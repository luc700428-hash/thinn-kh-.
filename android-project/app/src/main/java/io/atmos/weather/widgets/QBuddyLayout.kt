package io.atmos.weather.widgets

/**
 * r20 layout for the Q-version (buddy) widgets, from 2×2 up to 10×10 launcher cells.
 *
 * One rule set for every size: a header, a hero (the buddy with the main reading), then as many
 * detail blocks as the remaining height really holds, in the order that matters for the widget:
 *   7 天氣夥伴   – description, hours, metrics, days, outing advice
 *   8 夥伴時鐘   – description, next alarm, hours, days
 *   9 一週預報   – days first, then hours and metrics
 *  10 出門小幫手 – outing advice first, then metrics, hours, days
 * While a warning or a fresh quake report is on (see [WidgetEvent]), every family leads with it
 * and switches back by itself when it ends. The current condition (多雲, 晴…) is part of every hero.
 * A block is placed whole or not at all, so nothing is ever cut in half or overlaps, and wide
 * widgets split the blocks into two columns instead of leaving half the widget empty.
 */
internal object QBuddyLayout {
    private const val LINE = 1.65f
    private fun units(s: String) = s.sumOf { if (it.code >= 0x2e80) 1.0 else .64 }.toFloat().coerceAtLeast(1f)

    private fun dateRowFont0(p: WidgetLayoutPlan, width: Float, maxHeight: Float) =
        minOf(p.caption / p.textScale, width / (4.5f * WidgetTypography.MAX), maxHeight / (LINE * WidgetTypography.MAX)) * p.textScale

    /** A rectangle that blocks flow down inside. */
    private class Column(val x: Float, var y: Float, val w: Float, val bottom: Float)

    fun create(p: WidgetLayoutPlan, family: Int, d: OpticalWidgetData, hour24: Boolean, showUpdated: Boolean, showAgenda: Boolean): List<WidgetRegion> {
        val out = mutableListOf<WidgetRegion>()
        val w = p.innerWidth; val h = p.innerHeight
        val gap = (minOf(w, h) * .03f).coerceIn(3f, 12f)
        fun add(kind: WidgetRegion.Kind, x: Float, top: Float, width: Float, height: Float, text: String = "", font: Float = 0f,
                soft: Boolean = false, lines: Int = 1, bold: Boolean = false, index: Int = 0) {
            if (width >= 1 && height >= 1 && top >= 0 && top + height <= h + .1f && x >= 0 && x + width <= w + .1f)
                out += WidgetRegion(kind, x, top, width, height, text, font, soft, lines, bold, index)
        }
        fun fit(desired: Float, text: String, width: Float, maxHeight: Float) = minOf(desired / p.textScale,
            width / (units(text) * WidgetTypography.MAX), maxHeight / (LINE * WidgetTypography.MAX)) * p.textScale

        val clock = family == 8
        val ev = d.event
        // Under the temperature: the condition, or the warning's title while one is on.
        val condition = ev?.title ?: d.description()
        /** Whether the warning's full title is already on screen (else the event block repeats it). */
        var titleShown = false
        val city = io.atmos.weather.location.LocationLabels.microcity(d.city).ifBlank { d.city }
        val range = d.highLow.replace("最高 ", "高 ").replace("最低 ", "低 ")
        val timeSample = if (hour24) "00:00" else "00:00 PM"
        val footerH = if (showUpdated && h >= 190f) p.caption * LINE + gap else 0f
        val usable = h - footerH

        if (p.strip) {
            // One row: reading on the left, the buddy on the right, a little context when wide.
            val side = minOf(h, w * .28f)
            add(WidgetRegion.Kind.ART, w - side, (h - side) / 2f, side, side)
            val pw = w - side - gap
            val extra = pw > 330f                      // room for a third column of context
            val main = if (extra) pw * .46f else pw
            val lower = h * .34f                        // second line under the reading
            if (clock) {
                val timeFont = fit(p.clock * 1.2f, timeSample, main, h - lower - 2f)
                add(WidgetRegion.Kind.TIME, 0f, 0f, main, timeFont * LINE, font = timeFont)
                val dateW = main * .58f; val dateFont = dateRowFont0(p, dateW, lower)
                val tempFont = fit(p.body, d.temperature, main - dateW - gap, lower)
                add(WidgetRegion.Kind.DATE, 0f, h - lower, dateW, dateFont * LINE, font = dateFont, soft = true)
                add(WidgetRegion.Kind.TEMPERATURE, dateW + gap, h - lower, main - dateW - gap, tempFont * LINE, d.temperature, tempFont, bold = true)
            } else {
                val twoLines = h >= 64f
                val tempFont = fit(p.hero * 1.2f, d.temperature, main, if (twoLines) h - lower - 2f else h)
                // The condition sits right after the temperature on the same line.
                val tempTop = if (twoLines) 0f else (h - tempFont * LINE) / 2f
                val tempW = (units(d.temperature) + .3f) * tempFont * WidgetTypography.MAX
                val condFont = minOf(p.body, tempFont * .42f)
                val side2 = main - tempW - gap >= condFont * 2.2f * WidgetTypography.MAX
                add(WidgetRegion.Kind.TEMPERATURE, 0f, tempTop, if (side2) tempW else main, tempFont * LINE, d.temperature, tempFont, bold = true)
                if (side2) { add(WidgetRegion.Kind.TEXT, tempW + gap, tempTop + tempFont * LINE - condFont * LINE * 1.15f,
                    main - tempW - gap, condFont * LINE, ev?.title ?: condition, condFont, bold = ev != null); titleShown = true }
                val line = when (family) { 9 -> d.days.getOrNull(1)?.let { "${it.label} ${it.range}" } ?: d.description(); 10 -> d.bring.ifBlank { d.description() }
                    else -> d.description() }
                val font = minOf(p.caption, lower / LINE)
                if (twoLines && !extra) {
                    // Narrow strip: the second line is the time and date (the side column has them when wide).
                    val timeW = minOf(main * .45f, (units(timeSample) + .4f) * font * WidgetTypography.MAX)
                    add(WidgetRegion.Kind.TIME, 0f, h - lower, timeW, font * LINE, font = font)
                    add(WidgetRegion.Kind.DATE, timeW + gap, h - lower, main - timeW - gap, font * LINE, "M月d日 EEE", font, soft = true)
                } else if (twoLines && line.isNotBlank()) add(WidgetRegion.Kind.TEXT, 0f, h - lower, main, font * LINE, line, font, soft = true)
            }
            if (extra) {
                val x = main + gap * 2; val cw = pw - x
                val font = minOf(p.body, (h - gap) / 2f / LINE)
                val first = ev?.detail?.ifBlank { null } ?: when (family) { 10 -> d.outlook.ifBlank { city }; else -> city }
                val second = when (family) { 8 -> d.alarm; 9 -> d.days.getOrNull(2)?.let { "${it.label} ${it.range}" } ?: range; else -> range }
                if (clock) {
                    add(WidgetRegion.Kind.TEXT, x, 0f, cw, font * LINE, first, font, bold = true)
                    add(WidgetRegion.Kind.TEXT, x, h - font * LINE, cw, font * LINE, second, font, soft = true)
                } else {
                    // Time and date lead the side column; the city (or the widget's own line) follows.
                    val timeW = minOf(cw * .45f, (units(timeSample) + .4f) * font * WidgetTypography.MAX)
                    add(WidgetRegion.Kind.TIME, x, 0f, timeW, font * LINE, font = font)
                    add(WidgetRegion.Kind.DATE, x + timeW + gap, 0f, cw - timeW - gap, font * LINE, "M月d日 EEE", font, soft = true)
                    add(WidgetRegion.Kind.TEXT, x, h - font * LINE, cw, font * LINE, first, font, bold = true)
                }
            }
            return out
        }

        /** The condition line's text and size: a warning falls back to its short label when the title will not fit. */
        fun conditionLine(width: Float, height: Float): Pair<String, Float> {
            val full = fit(p.body, condition, width, height)
            val short = ev?.kind?.label ?: return condition to full
            if (full >= 8f || condition == short) { titleShown = true; return condition to full }
            return short to fit(p.body, short, width, height)
        }

        /** The clock's row: temperature, then the condition beside it when there is room. */
        fun reading(x: Float, top: Float, width: Float, rowH: Float) {
            // The temperature keeps the size it always had; the condition joins only when readable.
            val font = fit(p.body, d.temperature, width, rowH)
            val tempW = minOf(width, (units(d.temperature) + .3f) * font * WidgetTypography.MAX)
            val rest = width - tempW - gap
            val (text, cf) = if (rest > 0f) conditionLine(rest, rowH) else condition to 0f
            if (cf >= 8f) {
                add(WidgetRegion.Kind.TEMPERATURE, x, top, tempW, font * LINE, d.temperature, font, bold = true)
                add(WidgetRegion.Kind.TEXT, x + tempW + gap, top, rest, minOf(cf, font) * LINE, text, minOf(cf, font), soft = ev == null, bold = ev != null)
            } else {
                if (text == condition && ev != null) titleShown = false
                add(WidgetRegion.Kind.TEMPERATURE, x, top, width, font * LINE, d.temperature, font, bold = true)
            }
        }

        // ---------- header + hero ----------
        // Every height in this part depends on the widget's size only, never on the text-size
        // preferences: a larger preference enlarges the reading inside the same space, so it can
        // never squeeze the main reading or the buddy.
        var y = 0f
        val heroBeside = w >= 230f                       // buddy beside the reading, else above it
        val buddyShare = when (family) { 9 -> .26f; 8 -> .34f; else -> .40f }  // lists and the clock give the buddy less
        fun dateRowFont(width: Float, maxHeight: Float = Float.MAX_VALUE) =
            minOf(p.caption / p.textScale, width / (4.5f * WidgetTypography.MAX), maxHeight / (LINE * WidgetTypography.MAX)) * p.textScale
        val headerH = (usable * .13f).coerceIn(18f, 70f)
        val minimumHero = if (heroBeside) 44f else 24f + gap + 13f * LINE * WidgetTypography.MAX + (if (clock) gap + 13f * LINE * WidgetTypography.MAX else 0f)
        val cityLabel = if (ev != null) "${ev.kind.label} · $city" else city
        if (clock) {
            // The clock widget shows the time big in its hero; its header is the city.
            // A 2×2 clock spends its little height on the time and the buddy instead of the city line.
            // During a warning the header always shows, carrying the warning's title.
            if (ev != null || usable - headerH - gap >= minimumHero && !(!heroBeside && usable < 170f)) {
                val cityFont = minOf(p.city, headerH / LINE)
                // The clock's header names the condition first, then the place.
                add(WidgetRegion.Kind.TEXT, 0f, 0f, w, cityFont * LINE, ev?.title ?: "$condition · $city", cityFont, bold = true)
                if (ev != null) titleShown = true
                y = headerH + gap
            }
        } else {
            // Every other Q widget opens with the time and date (live TextClocks), then the city
            // when the row has room for it. Even a 2×2 keeps this line.
            val headerH = (usable * .155f).coerceIn(20f, 78f)
            val font = minOf(p.city, headerH / LINE)
            val timeUnits = units(timeSample) + .4f
            val timeFont = minOf(font * 1.25f, headerH / LINE, w * .46f / (timeUnits * WidgetTypography.MAX))
            val timeW = timeUnits * timeFont * WidgetTypography.MAX
            val dateFont = minOf(font * .94f, headerH / LINE)
            val longDate = "11月17日 週二"; val shortDate = "11月17日"
            val roomAfterTime = w - timeW - gap
            val dateText = if (units(longDate) * dateFont * WidgetTypography.MAX <= roomAfterTime) "M月d日 EEE" else "M月d日"
            val dateW = minOf(roomAfterTime, units(if (dateText.length > 5) longDate else shortDate) * dateFont * WidgetTypography.MAX + 2f)
            val top = (headerH - maxOf(timeFont, dateFont) * LINE).coerceAtLeast(0f) / 2f
            add(WidgetRegion.Kind.TIME, 0f, top, timeW, timeFont * LINE, font = timeFont)
            if (dateW >= dateFont * 2.5f) add(WidgetRegion.Kind.DATE, timeW + gap, top, dateW, dateFont * LINE, dateText, dateFont, soft = true)
            val cityX = timeW + gap + dateW + gap * 2
            val cityW = w - cityX
            if (cityW >= units(cityLabel).coerceAtMost(6f) * font * WidgetTypography.MAX)
                add(WidgetRegion.Kind.TEXT, cityX, top, cityW, font * LINE, cityLabel, font, bold = true)
            y = headerH + gap
        }
        if (heroBeside) {
            val heightShare = if (family == 9) .30f else if (usable - y > 300f) .42f else .78f
            val side = minOf(w * buddyShare, (usable - y) * heightShare, 340f).coerceAtLeast(40f).coerceAtMost(usable - y)
            val pw = w - side - gap
            if (clock) {
                // The clock gets its own height, so a modest buddy never limits the time's size.
                val heroH = maxOf(side, minOf((usable - y) * .44f, 260f))
                val rowReserve = heroH * .32f
                val dateW = pw * .56f; val dateFont = dateRowFont(dateW, rowReserve)
                val timeFont = fit(p.clock * 1.6f, timeSample, pw, (heroH - rowReserve - gap).coerceAtLeast(14f))
                add(WidgetRegion.Kind.TIME, 0f, y, pw, timeFont * LINE, font = timeFont)
                val rowTop = y + heroH - rowReserve
                add(WidgetRegion.Kind.DATE, 0f, rowTop, dateW, dateFont * LINE, font = dateFont, soft = true)
                reading(dateW + gap, rowTop, pw - dateW - gap, rowReserve)
                add(WidgetRegion.Kind.ART, w - side, y + (heroH - side) / 2f, side, side)
                y += heroH - side
            } else {
                // The reading: temperature with the condition under it, sized by the widget alone.
                // A small buddy never squeezes the condition: the reading gets a minimum height.
                val condH = maxOf(side * .24f, 9f * LINE * WidgetTypography.MAX)
                val heroH = maxOf(side, condH + 16f * LINE * WidgetTypography.MAX)
                val tempFont = fit(p.hero * 1.3f, d.temperature, pw, heroH - condH)
                val (condText, condFont) = conditionLine(pw, condH)
                val block = tempFont * LINE + condFont * LINE
                val top = y + (heroH - block).coerceAtLeast(0f) / 2f
                add(WidgetRegion.Kind.TEMPERATURE, 0f, top, pw, tempFont * LINE, d.temperature, tempFont, bold = true)
                add(WidgetRegion.Kind.TEXT, 0f, top + tempFont * LINE, pw, condFont * LINE, condText, condFont, soft = ev == null, bold = ev != null)
                add(WidgetRegion.Kind.ART, w - side, y + (heroH - side) / 2f, side, side)
                y += heroH - side
            }
            y += side + gap
        } else {
            // Narrow: buddy on top, reading under it.
            val rowReserve = 13f * LINE * WidgetTypography.MAX
            // Non-clock: the temperature plus the condition line under it (readable even at the smallest text size).
            val conditionMin = 9f * LINE * WidgetTypography.MAX
            val readingMin = if (clock) 13f * LINE * WidgetTypography.MAX + gap + rowReserve else 16f * LINE * WidgetTypography.MAX + conditionMin
            val clockTime = usable * .30f
            val side = (if (clock) minOf(w * .86f, usable - y - clockTime - minOf(rowReserve, usable * .20f) - gap * 3, 260f)
                else minOf(w * .86f, (usable - y - readingMin - gap) * (if (usable - y > 320f) .46f else 1f), 260f)).coerceAtLeast(0f)
            if (side >= 24f) { add(WidgetRegion.Kind.ART, (w - side) / 2f, y, side, side); y += side + gap }
            if (clock) {
                val row = minOf(rowReserve, usable * .20f)
                val timeReserve = minOf(usable - y - row - gap, clockTime).coerceAtLeast(12f)
                val timeFont = fit(p.clock * 1.3f, timeSample, w, timeReserve)
                add(WidgetRegion.Kind.TIME, 0f, y, w, timeFont * LINE, font = timeFont); y += timeReserve + gap
                val dateW = w * .58f; val dateFont = dateRowFont(dateW, row)
                add(WidgetRegion.Kind.DATE, 0f, y, dateW, dateFont * LINE, font = dateFont, soft = true)
                reading(dateW + gap, y, w - dateW - gap, row)
                y += row + gap
            } else {
                val reserve = minOf(usable - y, maxOf(usable * .34f, readingMin)).coerceAtLeast(14f)
                // Temperature, with the condition on the line under it.
                val condH = maxOf(reserve * .36f, conditionMin).coerceAtMost(reserve * .5f)
                val tempFont = fit(p.hero, d.temperature, w, reserve - condH)
                add(WidgetRegion.Kind.TEMPERATURE, 0f, y, w, tempFont * LINE, d.temperature, tempFont, bold = true)
                val (condText, condFont) = conditionLine(w, condH)
                add(WidgetRegion.Kind.TEXT, 0f, y + tempFont * LINE, w, condFont * LINE, condText, condFont, soft = ev == null, bold = ev != null)
                y += reserve + gap
            }
        }

        // ---------- detail blocks ----------
        fun Column.text(value: String, font: Float = p.body, soft: Boolean = false, bold: Boolean = false, lines: Int = 1): Boolean {
            val height = font * LINE * lines
            if (value.isBlank() || this.y + height > bottom + .1f) return false
            add(WidgetRegion.Kind.TEXT, x, this.y, this.w, height, value, font, soft, lines, bold); this.y += height + gap; return true
        }
        fun Column.description(): Boolean = text(range, p.caption, soft = true)
        fun Column.hours(): Boolean {
            if (d.hours.isEmpty()) return false
            val art = 26f * p.typeGrowth
            val height = (p.caption + p.body) * LINE + art + gap * 2
            if (this.y + height > bottom + .1f) return false
            val count = (this.w / (p.body * 3.4f)).toInt().coerceIn(1, 8); val hours = d.hours.take(count); val cw = this.w / hours.size
            hours.forEachIndexed { i, hour ->
                add(WidgetRegion.Kind.TEXT, x + i * cw, this.y, cw, p.caption * LINE, hour.time, p.caption, true)
                val a = minOf(art, cw * .7f)
                add(WidgetRegion.Kind.HOUR_ART, x + i * cw + (cw - a) / 2, this.y + p.caption * LINE + gap, a, a, index = i)
                add(WidgetRegion.Kind.TEXT, x + i * cw, this.y + p.caption * LINE + art + gap * 2, cw, p.body * LINE, hour.temperature, p.body, bold = true)
            }
            this.y += height + gap; return true
        }
        fun Column.metrics(items: List<Pair<String, String>>): Boolean {
            val cols = (this.w / (p.body * 5.2f)).toInt().coerceIn(1, 4)
            val cellH = (p.caption + p.body) * LINE + gap
            val rows = ((bottom - this.y + .1f) / cellH).toInt().coerceIn(0, (items.size + cols - 1) / cols)
            if (rows == 0) return false
            items.take(rows * cols).chunked(cols).forEach { row ->
                val cw = (this.w - gap * (cols - 1)) / cols
                row.forEachIndexed { i, (label, value) ->
                    add(WidgetRegion.Kind.TEXT, x + i * (cw + gap), this.y, cw, p.caption * LINE, label, p.caption, true)
                    add(WidgetRegion.Kind.TEXT, x + i * (cw + gap), this.y + p.caption * LINE, cw, p.body * LINE, value, p.body, bold = true)
                }
                this.y += cellH
            }
            return true
        }
        fun Column.days(): Boolean {
            if (d.days.isEmpty()) return false
            // A list reads best a little smaller and tighter than body text.
            val font = p.body * .94f; val lineH = font * LINE; val rowH = lineH + gap * .35f
            val rows = ((bottom - this.y + .1f) / rowH).toInt().coerceAtMost(d.days.size)
            if (rows < 2) return false
            val full = this.w >= font * 11f           // room for icon and rain chance
            d.days.take(rows).forEachIndexed { i, day ->
                val top = this.y + i * rowH
                val labelW = if (full) this.w * .24f else this.w * .44f
                add(WidgetRegion.Kind.TEXT, x, top, labelW, lineH, day.label, font)
                if (full) {
                    val art = minOf(lineH, this.w * .12f)
                    add(WidgetRegion.Kind.DAY_ART, x + labelW + gap, top + (lineH - art) / 2, art, art, index = i)
                    val popX = labelW + gap + art + gap; val popW = this.w * .20f
                    if (day.rain.isNotBlank()) add(WidgetRegion.Kind.TEXT, x + popX, top, popW, lineH, day.rain, font * .9f, true)
                    val rangeX = popX + popW + gap
                    add(WidgetRegion.Kind.TEXT, x + rangeX, top, this.w - rangeX, lineH, day.range, font, bold = true)
                } else add(WidgetRegion.Kind.TEXT, x + labelW + gap, top, this.w - labelW - gap, lineH, day.range, font, bold = true)
            }
            this.y += rows * rowH + gap; return true
        }
        fun Column.outing(): Boolean {
            val roomy = bottom - this.y >= p.body * LINE * 4
            val a = text(d.outlook, p.body, bold = true, lines = if (roomy && units(d.outlook) * p.body > this.w) 2 else 1)
            val b = text(d.bring, p.body, lines = if (roomy && units(d.bring) * p.body > this.w) 2 else 1)
            val c = text(d.tomorrow, p.caption, soft = true)
            return a || b || c
        }
        fun Column.alarm(): Boolean {
            if (this.y + (p.caption + p.body) * LINE + gap > bottom + .1f) return false
            text("下一個鬧鐘", p.caption, soft = true); text(d.alarm, p.body)
            if (d.alarmAt?.let { it - System.currentTimeMillis() in 1..86_400_000L } == true && this.y + p.body * LINE <= bottom + .1f) {
                add(WidgetRegion.Kind.COUNTDOWN, x, this.y, this.w, p.body * LINE, font = p.body); this.y += p.body * LINE + gap
            }
            if (showAgenda) text(d.task, p.caption, soft = true)
            return true
        }
        fun Column.event(): Boolean {
            // The details; the title too when the line under the temperature only had room for its label.
            val e = ev ?: return false
            val a = if (!titleShown) text(e.title, p.body, bold = true, lines = if (bottom - this.y >= p.body * LINE * 3 && units(e.title) * p.body > this.w) 2 else 1) else false
            val b = text(e.detail, if (titleShown) p.body else p.caption, bold = titleShown, soft = !titleShown,
                lines = if (bottom - this.y >= p.body * LINE * 3 && units(e.detail) * p.body > this.w) 2 else 1)
            return a || b
        }
        val weather = listOf("降雨" to d.rain, "風速" to d.wind, "濕度" to d.humidity, "空氣 AQI" to d.air)
        val outdoor = listOf("降雨" to d.rain, "紫外線" to d.uv, "空氣 AQI" to d.air, "風速" to d.wind)
        val usual: List<Column.() -> Boolean> = when (family) {
            8 -> listOf({ alarm() }, { description() }, { hours() }, { days() })
            9 -> listOf({ days() }, { hours() }, { metrics(weather) }, { description() })
            10 -> listOf({ outing() }, { metrics(outdoor) }, { hours() }, { days() })
            else -> listOf({ description() }, { hours() }, { metrics(weather) }, { days() }, { outing() })
        }
        // A warning or fresh quake report comes first in every widget while it lasts.
        val blocks: List<Column.() -> Boolean> = if (ev != null) listOf<Column.() -> Boolean>({ event() }) + usual else usual
        // Wide and tall enough: two columns, blocks dealt left / right by whichever is shorter.
        val twoColumns = w >= 520f && usable - y >= p.body * LINE * 5
        val columns = if (twoColumns) { val cw = (w - gap * 2) / 2f; listOf(Column(0f, y, cw, usable), Column(cw + gap * 2, y, cw, usable)) }
            else listOf(Column(0f, y, w, usable))
        blocks.forEach { block ->
            val target = columns.minBy { it.y }
            if (!target.block()) columns.filter { it !== target }.forEach { other -> if (other.block()) return@forEach }
        }

        if (footerH > 0f) {
            val stamp = if (d.stamp > 0) java.time.Instant.ofEpochMilli(d.stamp).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) else "待同步"
            val label = when (d.age) { WidgetPresentationRules.Freshness.CACHED -> "快取 · $stamp"; WidgetPresentationRules.Freshness.EXPIRED -> "資料已過期"
                WidgetPresentationRules.Freshness.MISSING -> "等待同步"; else -> "更新 $stamp" }
            add(WidgetRegion.Kind.TEXT, 0f, h - p.caption * LINE, w, p.caption * LINE, label, p.caption, true)
        }
        return out
    }
}
