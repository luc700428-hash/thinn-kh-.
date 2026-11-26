package io.atmos.weather.life

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The national alert feed of the National Science and Technology Center for Disaster Reduction
 * (NCDR, alerts.ncdr.nat.gov.tw): one public Atom feed carrying every official CAP alert —
 * 停班停課 from the DGPA, 土石流 from the Agency of Rural Development and Soil and Water
 * Conservation, 淹水 / 河川高水位 / 水庫放流 from the Water Resources Agency, road closures and
 * the CWA's weather warnings.
 */
object NcdrFeed {
    const val URL = "https://alerts.ncdr.nat.gov.tw/RssAtomFeed.ashx"
    data class Alert(
        val id: String,
        val category: String,
        val author: String,
        val summary: String,
        val updated: Instant,
        val expires: Instant?,
        val link: String,
        val msgType: String,
    ) {
        fun activeAt(now: Instant): Boolean = msgType != "Cancel" && (expires?.isAfter(now) ?: (Duration.between(updated, now).toHours() < 24))
    }

    val closureCategories = setOf("停班停課")
    val waterCategories = setOf("淹水", "河川高水位", "水庫放流", "土石流", "大規模崩塌", "降雨", "颱風", "道路封閉", "水位警戒")

    /** Parses the feed with plain string scanning, so it also runs in background work without a DOM. */
    fun parse(xml: String): List<Alert> {
        val out = mutableListOf<Alert>()
        var at = 0
        while (true) {
            val start = xml.indexOf("<entry", at); if (start < 0) break
            val end = xml.indexOf("</entry>", start); if (end < 0) break
            val e = xml.substring(start, end)
            at = end + 8
            fun tag(name: String): String? {
                val open = e.indexOf("<$name"); if (open < 0) return null
                val gt = e.indexOf('>', open); if (gt < 0) return null
                if (e[gt - 1] == '/') return ""
                val close = e.indexOf("</$name>", gt); if (close < 0) return null
                return unescape(e.substring(gt + 1, close)).trim()
            }
            fun attr(tagName: String, attrName: String): String? {
                val open = e.indexOf("<$tagName"); if (open < 0) return null
                val gt = e.indexOf('>', open)
                val chunk = e.substring(open, if (gt < 0) e.length else gt)
                val key = "$attrName=\""; val k = chunk.indexOf(key); if (k < 0) return null
                return unescape(chunk.substring(k + key.length, chunk.indexOf('"', k + key.length)))
            }
            val updated = tag("updated")?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: continue
            val authorBlock = e.substringAfter("<author>", "").substringBefore("</author>")
            val author = authorBlock.substringAfter("<name>", "").substringBefore("</name>").trim()
            out += Alert(
                id = tag("id") ?: continue,
                category = attr("category", "term") ?: tag("title") ?: continue,
                author = unescape(author),
                summary = stripTags(tag("summary") ?: ""),
                updated = updated,
                expires = tag("cap:expires")?.let { capTime(it) },
                link = attr("link", "href") ?: "",
                msgType = tag("cap:msgType") ?: "Alert",
            )
        }
        return out.sortedByDescending { it.updated }
    }

    /** "2026/11/26 上午 04:00:00" (Taiwan time) → instant. */
    fun capTime(text: String): Instant? = runCatching {
        val t = text.trim()
        val pm = t.contains("下午"); val am = t.contains("上午")
        val clean = t.replace("上午", "").replace("下午", "").replace(Regex("\\s+"), " ").trim()
        val local = LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"))
        val hour = local.hour
        val fixed = when {
            pm && hour < 12 -> local.plusHours(12)
            am && hour == 12 -> local.minusHours(12)
            else -> local
        }
        fixed.atZone(ZoneId.of("Asia/Taipei")).toInstant()
    }.getOrNull()

    fun stripTags(s: String) = s.replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")

    /** Does the alert mention [county] (臺/台 tolerant)? */
    fun mentions(alert: Alert, county: String?): Boolean {
        if (county.isNullOrBlank()) return false
        val text = (alert.summary + alert.category).replace('台', '臺')
        val c = county.replace('台', '臺')
        return text.contains(c) || text.contains(c.removeSuffix("縣").removeSuffix("市"))
    }
}

/** 停班停課: what each county has announced, from the DGPA's alerts in the NCDR feed. */
object WorkSchoolClosure {
    val counties = listOf("基隆市", "臺北市", "新北市", "桃園市", "新竹市", "新竹縣", "苗栗縣", "臺中市", "彰化縣", "南投縣",
        "雲林縣", "嘉義市", "嘉義縣", "臺南市", "高雄市", "屏東縣", "宜蘭縣", "花蓮縣", "臺東縣", "澎湖縣", "金門縣", "連江縣")
    data class Status(val county: String, val text: String, val updated: Instant?, val closed: Boolean)

    fun statuses(alerts: List<NcdrFeed.Alert>, now: Instant = Instant.now()): List<Status> {
        val recent = alerts.filter { it.category in NcdrFeed.closureCategories && Duration.between(it.updated, now).toHours() < 36 }
            .sortedByDescending { it.updated }
        return counties.map { c ->
            val hit = recent.firstOrNull { NcdrFeed.mentions(it, c) }
            if (hit == null) Status(c, "尚未發布停班停課訊息", null, false)
            else {
                val sentence = sentenceFor(hit.summary, c)
                Status(c, sentence, hit.updated, sentence.contains("停止上班") || sentence.contains("停止上課") || sentence.contains("停班") || sentence.contains("停課"))
            }
        }
    }

    /** The part of a multi-county announcement about [county]. */
    fun sentenceFor(summary: String, county: String): String {
        val c = county.replace('台', '臺')
        val text = summary.replace('台', '臺')
        val parts = text.split('。', '；', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.firstOrNull { it.contains(c) || it.contains(c.removeSuffix("縣").removeSuffix("市")) } ?: text.take(80)
    }
}

/** 水庫蓄水: hourly storage joined to each reservoir's current effective capacity (WRA open data). */
object Reservoirs {
    const val CAPACITY_URL = "https://opendata.wra.gov.tw/api/v2/708a43b0-24dc-40b7-9ed2-fca6a291e7ae?format=JSON"
    const val STORAGE_URL = "https://opendata.wra.gov.tw/api/v2/2be9044c-6e44-4856-aad5-dd108c2e6679?format=JSON"
    data class Info(val id: String, val name: String, val region: String, val capacity: Double)
    data class Level(val info: Info, val storage: Double, val percent: Double, val observed: LocalDateTime?)

    fun number(s: String?): Double? = s?.replace(",", "")?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    /** Storage % = effective storage ÷ effective capacity; weirs without real storage are left out. */
    fun join(infos: List<Info>, storage: List<Triple<String, String?, String?>>): List<Level> {
        val latest = HashMap<String, Pair<Double, LocalDateTime?>>()
        storage.forEach { (id, volume, time) ->
            val v = number(volume) ?: return@forEach
            val t = time?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val prev = latest[id]
            if (prev == null || (t != null && (prev.second == null || t.isAfter(prev.second)))) latest[id] = v to t
        }
        return infos.filter { it.capacity >= 100 && !it.name.contains("堰") && !it.name.endsWith("壩") }.mapNotNull { info ->
            val (v, t) = latest[info.id] ?: return@mapNotNull null
            val pct = v / info.capacity * 100
            if (pct.isNaN() || pct < 0 || pct > 130) null else Level(info, v, pct, t)
        }.sortedByDescending { it.info.capacity }
    }

    fun word(pct: Double) = when {
        pct >= 80 -> "充足"; pct >= 60 -> "正常"; pct >= 40 -> "偏低"; pct >= 25 -> "吃緊"; else -> "嚴重不足"
    }
}

/** 避難收容處所: the national list (MOI Fire Agency open data), bundled for offline use. */
object Shelters {
    const val URL = "https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/ED6CF735-6C03-4573-A882-72C1BEC799CB/resource/54550E2F-4567-4C8F-BD2E-E54E9D0386B8/download"
    data class Shelter(val name: String, val area: String, val address: String, val lat: Double, val lon: Double,
                       val capacity: Int?, val hazards: String, val indoor: Boolean, val vulnerable: Boolean)
    data class Near(val shelter: Shelter, val km: Double, val bearing: String)

    /** One CSV line into fields, honouring quotes. */
    fun fields(line: String): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    /**
     * Reads the Fire Agency CSV (序號,縣市及鄉鎮市區,村里,地址,經度,緯度,名稱,預計收容村里,預計收容人數,
     * 適用災害類別,管理人姓名,管理人電話,室內,室外,適合避難弱者安置). The manager's name and phone are
     * personal data and are never kept.
     */
    fun parse(csv: String): List<Shelter> {
        val lines = csv.removePrefix("\uFEFF").lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val head = fields(lines.first())
        fun col(name: String) = head.indexOfFirst { it.trim().startsWith(name) }
        val cArea = col("縣市"); val cVillage = col("村里"); val cAddr = col("避難收容處所地址"); val cLon = col("經度"); val cLat = col("緯度")
        val cName = col("避難收容處所名稱"); val cCap = col("預計收容人數"); val cHaz = col("適用災害"); val cIn = col("室內"); val cWeak = col("適合避難弱者")
        if (listOf(cArea, cLon, cLat, cName).any { it < 0 }) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val f = fields(line)
            fun get(i: Int) = if (i in f.indices) f[i].trim() else ""
            val lat = get(cLat).toDoubleOrNull() ?: return@mapNotNull null
            val lon = get(cLon).toDoubleOrNull() ?: return@mapNotNull null
            if (lat !in 21.0..27.0 || lon !in 117.0..123.0) return@mapNotNull null
            Shelter(get(cName).ifBlank { return@mapNotNull null }, get(cArea) + get(cVillage),
                get(cAddr), lat, lon, get(cCap).toIntOrNull(), get(cHaz), get(cIn) == "是", get(cWeak) == "是")
        }
    }

    fun km(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = Math.toRadians(bLat - aLat); val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return 12742 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun bearingWord(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String {
        val y = sin(Math.toRadians(toLon - fromLon)) * cos(Math.toRadians(toLat))
        val x = cos(Math.toRadians(fromLat)) * sin(Math.toRadians(toLat)) - sin(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) * cos(Math.toRadians(toLon - fromLon))
        val deg = (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
        return listOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")[((deg + 22.5) / 45).toInt() % 8]
    }

    fun nearest(all: List<Shelter>, lat: Double, lon: Double, hazard: String? = null, limit: Int = 12): List<Near> =
        all.asSequence().filter { hazard == null || it.hazards.contains(hazard) }
            .map { Near(it, km(lat, lon, it.lat, it.lon), bearingWord(lat, lon, it.lat, it.lon)) }
            .sortedBy { it.km }.take(limit).toList()

    fun distance(km: Double) = if (km < 1) "${(km * 1000).roundToInt()} 公尺" else "%.1f 公里".format(java.util.Locale.US, km)
}

/** 防災包: the checklist, with expiry dates for water, food, batteries and medicine. */
object DisasterKit {
    data class Item(val id: String, val name: String, val group: String, val packed: Boolean = false, val expires: java.time.LocalDate? = null)
    fun defaults(): List<Item> = listOf(
        Item("water", "飲用水（每人每天 3 公升，至少 3 天）", "飲食"),
        Item("food", "乾糧與罐頭（免煮即食）", "飲食"),
        Item("opener", "開罐器、餐具", "飲食"),
        Item("light", "手電筒與備用電池", "照明通訊"),
        Item("radio", "收音機（可手搖或用電池）", "照明通訊"),
        Item("power", "行動電源（充飽電）", "照明通訊"),
        Item("whistle", "哨子", "照明通訊"),
        Item("medicine", "個人常用藥品與處方箋影本", "醫療衛生"),
        Item("firstaid", "急救包（紗布、優碘、OK 繃）", "醫療衛生"),
        Item("mask", "口罩、濕紙巾、衛生用品", "醫療衛生"),
        Item("docs", "身分證件影本、存摺與保險單影本", "重要物品"),
        Item("cash", "少量現金（含零錢）", "重要物品"),
        Item("keys", "備用鑰匙", "重要物品"),
        Item("clothes", "保暖衣物、雨衣", "衣物"),
        Item("blanket", "輕便睡袋或保暖毯", "衣物"),
        Item("gloves", "工作手套、雨鞋", "衣物"),
    )
    /** Items that expire within [days] (or already have). */
    fun expiring(items: List<Item>, today: java.time.LocalDate, days: Long = 30): List<Item> =
        items.filter { it.expires != null && !it.expires.isAfter(today.plusDays(days)) }
}
