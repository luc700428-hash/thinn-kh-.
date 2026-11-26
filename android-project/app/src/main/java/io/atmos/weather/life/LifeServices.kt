package io.atmos.weather.life

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.atmos.weather.AppContainer
import io.atmos.weather.comfort.ComfortScheduler
import io.atmos.weather.comfort.ComfortStore
import io.atmos.weather.comfort.ComfortText
import io.atmos.weather.comfort.ComfortWorker
import io.atmos.weather.domain.model.GeoPoint
import io.atmos.weather.domain.model.WeatherCity
import io.atmos.weather.domain.model.WeatherSnapshot
import io.atmos.weather.domain.repository.WeatherLoadResult
import io.atmos.weather.notifications.ThinnNotificationChannels
import io.atmos.weather.notifications.XiaomiFocusBridge
import io.atmos.weather.permission.PermissionGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Local-only settings and lists for the personal and disaster features. */
object LifeStore {
    private fun p(ctx: Context) = ctx.getSharedPreferences("thinn_life_v1", Context.MODE_PRIVATE)
    private val revision = MutableStateFlow(0L)
    val changes: StateFlow<Long> = revision
    private fun bump() { revision.value++ }

    // 氣壓天氣痛
    fun pressureAlerts(ctx: Context) = p(ctx).getBoolean("pressure_on", false)
    fun setPressureAlerts(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("pressure_on", on).apply(); bump(); LifeScheduler.reschedule(ctx) }
    fun sensitivities(ctx: Context): Set<String> = p(ctx).getStringSet("pressure_kinds", setOf("偏頭痛")) ?: setOf("偏頭痛")
    fun setSensitivities(ctx: Context, s: Set<String>) { p(ctx).edit().putStringSet("pressure_kinds", s).apply(); bump() }

    // 體感校準
    fun comfortBias(ctx: Context) = p(ctx).getFloat("comfort_bias", 0f).toDouble()
    fun feel(ctx: Context, f: ComfortCalibration.Feeling) {
        val next = ComfortCalibration.next(comfortBias(ctx), f)
        p(ctx).edit().putFloat("comfort_bias", next.toFloat()).putLong("comfort_last", System.currentTimeMillis()).apply(); bump()
        io.atmos.weather.lifestyle.LifestylePreferenceStore.touch()
    }
    fun resetBias(ctx: Context) { p(ctx).edit().putFloat("comfort_bias", 0f).apply(); bump(); io.atmos.weather.lifestyle.LifestylePreferenceStore.touch() }
    fun askedFeelingToday(ctx: Context): Boolean {
        val last = p(ctx).getLong("comfort_last", 0L)
        return last > 0 && Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
    }

    // 家人天氣關懷
    fun family(ctx: Context): List<FamilyCare.Person> = runCatching {
        val a = JSONArray(p(ctx).getString("family", "[]")); (0 until a.length()).map { i -> a.getJSONObject(i).let {
            FamilyCare.Person(it.getString("id"), it.getString("relation"), it.optString("nick"), it.getString("place"), it.getDouble("lat"), it.getDouble("lon"))
        } }
    }.getOrDefault(emptyList())
    fun setFamily(ctx: Context, list: List<FamilyCare.Person>) {
        val a = JSONArray(); list.forEach { a.put(JSONObject().put("id", it.id).put("relation", it.relation).put("nick", it.nickname).put("place", it.place).put("lat", it.lat).put("lon", it.lon)) }
        p(ctx).edit().putString("family", a.toString()).apply(); bump(); LifeScheduler.reschedule(ctx)
    }
    /** Sends each concern once. */
    fun claim(ctx: Context, key: String): Boolean {
        val set = p(ctx).getStringSet("claimed", emptySet())!!.toMutableSet()
        if (key in set) return false
        if (set.size > 300) set.clear()
        set += key; p(ctx).edit().putStringSet("claimed", set).apply(); return true
    }

    // 自訂規則
    fun rules(ctx: Context): List<WeatherRules.Rule> = runCatching {
        val a = JSONArray(p(ctx).getString("rules", "[]")); (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            val conds = o.getJSONArray("conds").let { c -> (0 until c.length()).map { j -> c.getJSONObject(j).let {
                WeatherRules.Condition(WeatherRules.Metric.valueOf(it.getString("m")), WeatherRules.Op.valueOf(it.getString("o")), it.getDouble("v")) } } }
            WeatherRules.Rule(o.getString("id"), o.getString("name"), o.getInt("days"), o.getInt("h"), o.getInt("min"),
                WeatherRules.Period.valueOf(o.getString("period")), conds, o.optString("msg"), o.optBoolean("on", true))
        } }
    }.getOrDefault(emptyList())
    fun setRules(ctx: Context, rules: List<WeatherRules.Rule>) {
        val a = JSONArray()
        rules.forEach { r -> a.put(JSONObject().put("id", r.id).put("name", r.name).put("days", r.weekdays).put("h", r.hour).put("min", r.minute)
            .put("period", r.period.name).put("msg", r.message).put("on", r.enabled)
            .put("conds", JSONArray().apply { r.conditions.forEach { put(JSONObject().put("m", it.metric.name).put("o", it.op.name).put("v", it.value)) } })) }
        p(ctx).edit().putString("rules", a.toString()).apply(); bump(); LifeScheduler.reschedule(ctx)
    }

    // 防災包
    fun kit(ctx: Context): List<DisasterKit.Item> {
        val raw = p(ctx).getString("kit", null) ?: return DisasterKit.defaults()
        return runCatching { val a = JSONArray(raw); (0 until a.length()).map { i -> a.getJSONObject(i).let {
            DisasterKit.Item(it.getString("id"), it.getString("name"), it.getString("group"), it.optBoolean("packed"),
                it.optString("exp").takeIf { s -> s.isNotBlank() }?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull() })
        } } }.getOrDefault(DisasterKit.defaults())
    }
    fun setKit(ctx: Context, items: List<DisasterKit.Item>) {
        val a = JSONArray(); items.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("group", it.group).put("packed", it.packed).put("exp", it.expires?.toString() ?: "")) }
        p(ctx).edit().putString("kit", a.toString()).apply(); bump(); LifeScheduler.reschedule(ctx)
    }

    // 停班停課與水情通知
    fun hazardAlerts(ctx: Context) = p(ctx).getBoolean("hazard_on", true)
    fun setHazardAlerts(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("hazard_on", on).apply(); bump(); LifeScheduler.reschedule(ctx) }

    // 災難省電模式
    fun disasterMode(ctx: Context) = p(ctx).getBoolean("disaster_mode", false)
    fun setDisasterMode(ctx: Context, on: Boolean) {
        val settings = ctx.getSharedPreferences("thinn_settings", Context.MODE_PRIVATE)
        val e = settings.edit()
        if (on) {
            // Remember the person's own choices, then turn every animation and translucent layer off.
            p(ctx).edit().putBoolean("saved_reduce_motion", settings.getBoolean("reduce_motion", false))
                .putBoolean("saved_reduce_transparency", settings.getBoolean("reduce_transparency", false)).apply()
            e.putBoolean("reduce_motion", true).putBoolean("reduce_transparency", true)
        } else {
            e.putBoolean("reduce_motion", p(ctx).getBoolean("saved_reduce_motion", false))
                .putBoolean("reduce_transparency", p(ctx).getBoolean("saved_reduce_transparency", false))
        }
        e.apply()
        p(ctx).edit().putBoolean("disaster_mode", on).apply(); bump()
    }

    // 駕車模式
    fun drivingCard(ctx: Context) = p(ctx).getBoolean("driving_on", true)
    fun setDrivingCard(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("driving_on", on).apply(); bump() }
}

/** Network and file sources, each cached on disk so the disaster pages work offline. */
object LifeSources {
    private fun get(url: String): String? = runCatching {
        AppContainer.httpClient.newCall(Request.Builder().url(url).header("User-Agent", "thinn-khi").build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.string()
        }
    }.getOrNull()
    private fun cache(ctx: Context, name: String) = File(ctx.filesDir, "life-cache").apply { mkdirs() }.let { File(it, name) }
    private fun cached(ctx: Context, name: String, maxAgeMs: Long, url: String): String? {
        val f = cache(ctx, name)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < maxAgeMs) return runCatching { f.readText() }.getOrNull()
        val fresh = get(url)
        if (fresh != null && fresh.isNotBlank()) { runCatching { f.writeText(fresh) }; return fresh }
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }
    fun cachedAt(ctx: Context, name: String): Instant? = cache(ctx, name).takeIf { it.exists() }?.let { Instant.ofEpochMilli(it.lastModified()) }

    /** Hourly surface pressure from 1 day back to 2 days ahead (Open-Meteo). */
    suspend fun pressure(lat: Double, lon: Double): List<PressurePain.Point>? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&hourly=surface_pressure&past_days=1&forecast_days=2&timezone=GMT"
            .format(java.util.Locale.US, lat, lon)
        val text = get(url) ?: return@withContext null
        runCatching {
            val h = JSONObject(text).getJSONObject("hourly")
            val t = h.getJSONArray("time"); val v = h.getJSONArray("surface_pressure")
            (0 until t.length()).mapNotNull { i ->
                val hpa = v.optDouble(i, Double.NaN); if (hpa.isNaN()) null
                else PressurePain.Point(java.time.LocalDateTime.parse(t.getString(i)).toInstant(java.time.ZoneOffset.UTC), hpa)
            }
        }.getOrNull()
    }

    suspend fun alerts(ctx: Context, maxAgeMs: Long = 10 * 60_000L): List<NcdrFeed.Alert> = withContext(Dispatchers.IO) {
        cached(ctx, "ncdr.xml", maxAgeMs, NcdrFeed.URL)?.let { runCatching { NcdrFeed.parse(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun reservoirs(ctx: Context): List<Reservoirs.Level> = withContext(Dispatchers.IO) {
        val infoText = cached(ctx, "reservoir-capacity.json", 7 * 24 * 3600_000L, Reservoirs.CAPACITY_URL)
        val storageText = cached(ctx, "reservoir-storage.json", 50 * 60_000L, Reservoirs.STORAGE_URL)
        if (infoText == null || storageText == null) return@withContext emptyList()
        runCatching {
            val infos = JSONArray(infoText).let { a -> (0 until a.length()).mapNotNull { i -> a.getJSONObject(i).let { o ->
                val cap = Reservoirs.number(o.optString("目前有效容量")) ?: return@mapNotNull null
                Reservoirs.Info(o.opt("水庫代碼").toString(), o.optString("水庫名稱"), o.optString("地區別"), cap)
            } } }
            val storage = JSONArray(storageText).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
                Triple(o.optString("reservoiridentifier"), o.optString("effectivewaterstoragecapacity"), o.optString("observationtime"))
            } } }
            Reservoirs.join(infos, storage)
        }.getOrDefault(emptyList())
    }

    @Volatile private var shelterCache: List<Shelters.Shelter>? = null
    /** The list if it is already in memory (opens instantly the second time). */
    fun sheltersIfLoaded(): List<Shelters.Shelter>? = shelterCache
    /** Bundled list (offline from install), replaced by a fresher download when one exists. */
    fun shelters(ctx: Context): List<Shelters.Shelter> {
        shelterCache?.let { return it }
        val downloaded = cache(ctx, "shelters.csv").takeIf { it.exists() }?.let { runCatching { Shelters.parse(it.readText()) }.getOrNull() }
        val list = downloaded?.takeIf { it.size > 1000 }
            ?: runCatching { ctx.assets.open("shelters/shelters.csv").bufferedReader().use { Shelters.parse(it.readText()) } }.getOrDefault(emptyList())
        shelterCache = list
        return list
    }
    suspend fun refreshShelters(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val text = get(Shelters.URL) ?: return@withContext false
        val parsed = runCatching { Shelters.parse(text) }.getOrDefault(emptyList())
        if (parsed.size < 1000) return@withContext false
        // Keep only what the app needs: never the managers' names and phone numbers.
        val slim = buildString {
            appendLine("縣市及鄉鎮市區,村里,避難收容處所地址,經度,緯度,避難收容處所名稱,預計收容人數,適用災害類別,室內,室外,適合避難弱者安置")
            parsed.forEach { s -> appendLine(listOf(s.area, "", s.address, "%.5f".format(java.util.Locale.US, s.lon), "%.5f".format(java.util.Locale.US, s.lat),
                s.name, s.capacity?.toString() ?: "", s.hazards, if (s.indoor) "是" else "否", "", if (s.vulnerable) "是" else "否")
                .joinToString(",") { f -> if (f.contains(',') || f.contains('"')) "\"" + f.replace("\"", "\"\"") + "\"" else f }) }
        }
        runCatching { cache(ctx, "shelters.csv").writeText(slim) }
        shelterCache = parsed
        true
    }

    suspend fun weather(ctx: Context, name: String, lat: Double, lon: Double): WeatherSnapshot? =
        when (val r = AppContainer.repository(ctx).load(WeatherCity("life-%.3f-%.3f".format(java.util.Locale.US, lat, lon), name, GeoPoint(lat, lon)), false)) {
            is WeatherLoadResult.Success -> r.value
            is WeatherLoadResult.Failure -> null
        }

    /** The county of a place name such as "臺北市大安區" or "新竹縣竹北市". */
    fun county(name: String?): String? {
        val n = name?.replace('台', '臺') ?: return null
        return WorkSchoolClosure.counties.firstOrNull { n.contains(it) } ?: WorkSchoolClosure.counties.firstOrNull { n.contains(it.dropLast(1)) }
    }
}

/** Schedules the hourly background check and each rule at its own time. */
object LifeScheduler {
    private const val HOURLY = "thinn_life_hourly"
    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        runCatching {
            val wm = WorkManager.getInstance(app)
            val needsHourly = LifeStore.pressureAlerts(app) || LifeStore.family(app).isNotEmpty() || LifeStore.hazardAlerts(app) ||
                LifeStore.kit(app).any { it.expires != null }
            if (needsHourly) wm.enqueueUniquePeriodicWork(HOURLY, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LifeWorker>(1, TimeUnit.HOURS).setInputData(Data.Builder().putString("kind", "hourly").build()).build())
            else wm.cancelUniqueWork(HOURLY)
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val rules = LifeStore.rules(app)
            rules.forEach { r ->
                val name = "thinn_life_rule_${r.id}"
                val at = if (r.enabled) ComfortScheduler.next(now, r.hour, r.minute, r.weekdays) else null
                if (at == null) wm.cancelUniqueWork(name)
                else wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<LifeWorker>()
                    .setInitialDelay((at.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1000L), TimeUnit.MILLISECONDS)
                    .setInputData(Data.Builder().putString("kind", "rule").putString("rule", r.id).putLong("planned", at.toInstant().toEpochMilli()).build()).build())
            }
        }
    }
}

class LifeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val kind = inputData.getString("kind") ?: return Result.success()
        if (!PermissionGuide.canNotify(ctx)) { if (kind == "rule") LifeScheduler.reschedule(ctx); return Result.success() }
        val zone = ZoneId.systemDefault(); val now = Instant.now()
        try {
            when (kind) {
                "rule" -> rule(ctx, inputData.getString("rule"), inputData.getLong("planned", 0L), now, zone)
                "hourly" -> {
                    val (from, to) = ComfortStore.quiet(ctx)
                    val quiet = io.atmos.weather.comfort.ComfortMessages.quiet(now.atZone(zone).toLocalTime(), from, to)
                    if (LifeStore.hazardAlerts(ctx)) hazards(ctx, now)
                    if (quiet) return Result.success()
                    if (LifeStore.pressureAlerts(ctx)) pressure(ctx, now, zone)
                    family(ctx, now, zone)
                    kit(ctx, now, zone)
                }
            }
        } finally { if (kind == "rule") LifeScheduler.reschedule(ctx) }
        return Result.success()
    }

    private fun post(ctx: Context, id: Int, type: ThinnNotificationChannels.Type, title: String, summary: String, body: String) =
        ComfortWorker.post(ctx, id, type, ComfortText(title, summary, body), XiaomiFocusBridge.Kind.WEATHER_BRIEFING)

    private suspend fun rule(ctx: Context, id: String?, planned: Long, now: Instant, zone: ZoneId) {
        if (planned > 0 && System.currentTimeMillis() - planned > 90 * 60_000L) return
        val r = LifeStore.rules(ctx).firstOrNull { it.id == id && it.enabled } ?: return
        val city = ComfortStore.current(ctx) ?: return
        val w = LifeSources.weather(ctx, city.name, city.coordinate.latitude, city.coordinate.longitude) ?: return
        val v = WeatherRules.evaluate(r, w, now.atZone(zone).toLocalDate(), zone)
        if (v.fires) post(ctx, 39500 + (r.id.hashCode() and 0xff), ThinnNotificationChannels.Type.SMART, r.name, r.message.ifBlank { r.name }, "${r.message}\n${v.explanation}")
    }

    private suspend fun pressure(ctx: Context, now: Instant, zone: ZoneId) {
        val city = ComfortStore.current(ctx) ?: return
        val points = LifeSources.pressure(city.coordinate.latitude, city.coordinate.longitude) ?: return
        val o = PressurePain.assess(points, now) ?: return
        if (o.level < PressurePain.Level.ALERT) return
        val day = now.atZone(zone).toLocalDate()
        if (!LifeStore.claim(ctx, "pressure:$day:${o.level}")) return
        val at = o.worstAt?.atZone(zone)?.let { "${it.hour} 點前後" } ?: "接下來"
        val kinds = LifeStore.sensitivities(ctx).joinToString("、")
        post(ctx, 39401, ThinnNotificationChannels.Type.SMART, "天氣痛${o.level.label}：氣壓$at 急降",
            "3 小時最多降 ${WeatherRules.fmt(o.drop3h)} hPa，24 小時降 ${WeatherRules.fmt(o.drop24h)} hPa",
            "${o.level.advice}\n你設定的症狀：$kinds。")
    }

    private suspend fun family(ctx: Context, now: Instant, zone: ZoneId) {
        val people = LifeStore.family(ctx); if (people.isEmpty()) return
        val alerts = LifeSources.alerts(ctx).filter { it.activeAt(now) }
        people.forEachIndexed { i, p ->
            val w = LifeSources.weather(ctx, p.place, p.lat, p.lon) ?: return@forEachIndexed
            val county = LifeSources.county(p.place)
            val warnings = alerts.filter { it.author.contains("氣象署") && NcdrFeed.mentions(it, county) }.map { it.category + "特報" }
            FamilyCare.concerns(p, w, warnings, now, zone).firstOrNull { LifeStore.claim(ctx, "family:${p.id}:${it.key}") }?.let { c ->
                post(ctx, 39600 + i, ThinnNotificationChannels.Type.SMART, c.title, c.detail, c.detail + "\n打開 App 可一鍵傳送天氣關心小卡。")
            }
        }
    }

    private suspend fun hazards(ctx: Context, now: Instant) {
        val city = ComfortStore.current(ctx) ?: return
        val county = LifeSources.county(city.name) ?: return
        val alerts = LifeSources.alerts(ctx, 5 * 60_000L).filter { it.activeAt(now) && NcdrFeed.mentions(it, county) }
        alerts.filter { it.category in NcdrFeed.closureCategories || it.category in setOf("土石流", "淹水", "大規模崩塌", "河川高水位", "水庫放流") }
            .take(3).forEach { a ->
                if (!LifeStore.claim(ctx, "hazard:${a.id}")) return@forEach
                val closure = a.category in NcdrFeed.closureCategories
                val text = if (closure) WorkSchoolClosure.sentenceFor(a.summary, county) else a.summary
                post(ctx, 39700 + (a.id.hashCode() and 0xff), ThinnNotificationChannels.Type.WARNINGS,
                    if (closure) "$county 停班停課" else "${a.category}警戒 · $county", text.take(60), "$text\n來源：${a.author}")
            }
    }

    private fun kit(ctx: Context, now: Instant, zone: ZoneId) {
        val day = now.atZone(zone).toLocalDate()
        if (now.atZone(zone).hour !in 9..20) return
        val due = DisasterKit.expiring(LifeStore.kit(ctx), day)
        if (due.isEmpty() || !LifeStore.claim(ctx, "kit:${day.withDayOfMonth(1)}:${due.joinToString { it.id }}")) return
        post(ctx, 39801, ThinnNotificationChannels.Type.SMART, "防災包有東西快過期了", due.joinToString("、") { it.name.substringBefore('（') },
            due.joinToString("\n") { "${it.name.substringBefore('（')}：${it.expires}" } + "\n記得汰換，保持防災包隨時可用。")
    }
}
