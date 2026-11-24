package io.atmos.weather.visual

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-drawn landmark silhouettes for the weather page. Each one is drawn in a 100-unit-tall frame
 * standing on the ground (y grows upward), centred on x = 0, so the scene can place and scale it.
 * Silhouettes take the colour of the sky and haze they stand in, with a few lighter details
 * (clock faces, windows that light up at night) so they read as part of the weather picture.
 */
class LandmarkInk(val body: Color, val detail: Color, val light: Color, val night: Boolean)

class LandmarkSpec(val id: String, val name: String, val width: Float, val draw: LandmarkPen.() -> Unit)

/** Drawing helpers in landmark units. */
class LandmarkPen(val scope: DrawScope, val x: Float, val ground: Float, val unit: Float, val ink: LandmarkInk) {
    fun px(u: Float) = x + u * unit
    fun py(v: Float) = ground - v * unit
    fun path(evenOdd: Boolean = false, block: Path.() -> Unit) = Path().apply { if (evenOdd) fillType = PathFillType.EvenOdd; block() }
    fun Path.poly(vararg p: Float) {
        moveTo(px(p[0]), py(p[1]))
        var i = 2
        while (i + 1 < p.size) { lineTo(px(p[i]), py(p[i + 1])); i += 2 }
        close()
    }
    fun Path.box(l: Float, b: Float, r: Float, t: Float) = poly(l, b, r, b, r, t, l, t)
    /** Semi-ellipse arch opening from (cx-w/2, b) up to height h. */
    fun Path.arch(cx: Float, b: Float, w: Float, h: Float, steps: Int = 14) {
        moveTo(px(cx - w / 2), py(b))
        for (i in 0..steps) { val a = PI * i / steps; lineTo(px(cx - (w / 2) * cos(a).toFloat()), py(b + (h) * sin(a).toFloat())) }
        close()
    }
    fun Path.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
        addOval(Rect(px(cx - rx), py(cy + ry), px(cx + rx), py(cy - ry)))
    /** A Chinese hip roof: eaves curl up at both ends. */
    fun Path.roof(cx: Float, b: Float, half: Float, h: Float, curl: Float = 2.2f) {
        moveTo(px(cx - half - curl * .6f), py(b + curl))
        quadraticTo(px(cx - half * .7f), py(b - h * .05f), px(cx - half * .45f), py(b + h * .35f))
        lineTo(px(cx - half * .25f), py(b + h)); lineTo(px(cx + half * .25f), py(b + h))
        lineTo(px(cx + half * .45f), py(b + h * .35f))
        quadraticTo(px(cx + half * .7f), py(b - h * .05f), px(cx + half + curl * .6f), py(b + curl))
        lineTo(px(cx + half * .8f), py(b)); lineTo(px(cx - half * .8f), py(b)); close()
    }
    fun fill(p: Path, c: Color = ink.body) = scope.drawPath(p, c)
    fun body(evenOdd: Boolean = false, block: Path.() -> Unit) = fill(path(evenOdd, block))
    fun detail(block: Path.() -> Unit) = fill(path(block = block), ink.detail)
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = .5f, c: Color = ink.body) =
        scope.drawLine(c, Offset(px(x0), py(y0)), Offset(px(x1), py(y1)), w * unit)
    fun curve(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, w: Float = .5f, c: Color = ink.body) =
        scope.drawPath(Path().apply { moveTo(px(x0), py(y0)); quadraticTo(px(cx), py(cy), px(x1), py(y1)) }, c, style = Stroke(w * unit))
    /** Windows that glow at night; by day a faint lighter tint. */
    fun windows(l: Float, b: Float, r: Float, t: Float, cols: Int, rows: Int, seed: Int = 7) {
        val c = if (ink.night) ink.light else ink.detail.copy(alpha = ink.detail.alpha * .5f)
        val cw = (r - l) / cols; val rh = (t - b) / rows
        for (i in 0 until cols) for (j in 0 until rows) {
            if (ink.night && ((i * 31 + j * 17 + seed) % 5 == 0)) continue
            val x0 = l + i * cw + cw * .28f; val y0 = b + j * rh + rh * .3f
            scope.drawRect(c, Offset(px(x0), py(y0 + rh * .42f)), androidx.compose.ui.geometry.Size(cw * .44f * unit, rh * .42f * unit))
        }
    }
    fun glow(cx: Float, cy: Float, r: Float) {
        if (!ink.night) return
        scope.drawCircle(ink.light.copy(alpha = .35f), r * unit * 2.2f, Offset(px(cx), py(cy)))
        scope.drawCircle(ink.light, r * unit, Offset(px(cx), py(cy)))
    }
    fun water(l: Float, r: Float, depth: Float = 3f) {
        scope.drawRect(ink.body.copy(alpha = ink.body.alpha * .55f), Offset(px(l), py(0f)), androidx.compose.ui.geometry.Size((r - l) * unit, depth * unit))
        for (k in 0 until 6) line(l + (r - l) * (k * .17f + .03f), -1f - (k % 3) * .8f, l + (r - l) * (k * .17f + .1f), -1f - (k % 3) * .8f, .35f, ink.detail)
    }
    fun hill(l: Float, r: Float, h: Float, c: Color = ink.body) = fill(path {
        moveTo(px(l), py(0f)); cubicTo(px(l + (r - l) * .25f), py(h * .9f), px(l + (r - l) * .45f), py(h), px((l + r) / 2), py(h))
        cubicTo(px(l + (r - l) * .6f), py(h), px(l + (r - l) * .78f), py(h * .8f), px(r), py(0f)); close()
    }, c)
}

object LandmarkArt {
    private fun spec(id: String, name: String, width: Float, draw: LandmarkPen.() -> Unit) = LandmarkSpec(id, name, width, draw)

    // ---------------- Taiwan ----------------
    val taipei101 = spec("taipei101", "臺北 101", 34f) {
        body {
            poly(-13f, 0f, 13f, 0f, 9.5f, 24f, -9.5f, 24f)
            for (k in 0 until 8) { val b = 24f + k * 7.6f; poly(-7f, b, 7f, b, 8.9f, b + 7.6f, -8.9f, b + 7.6f) }
            box(-5.5f, 84.8f, 5.5f, 88f); box(-4f, 88f, 4f, 91f); box(-.7f, 91f, .7f, 100f)
        }
        for (k in 0 until 8) windows(-6.5f, 24f + k * 7.6f, 6.5f, 30.6f + k * 7.6f, 4, 2, k)
        detail { for (k in 0 until 8) { val b = 24f + k * 7.6f; box(-8.9f, b + 7.2f, 8.9f, b + 7.6f) } }
        glow(0f, 99f, .8f)
    }
    val grandHotel = spec("grand_hotel", "圓山大飯店", 62f) {
        hill(-31f, 31f, 18f)
        body { box(-22f, 14f, 22f, 30f); roof(0f, 30f, 25f, 9f); box(-12f, 39f, 12f, 42f); roof(0f, 42f, 14f, 6f) }
        windows(-20f, 15f, 20f, 29f, 14, 3)
        detail { box(-22f, 30f, 22f, 30.8f) }
    }
    val tamkangBridge = spec("tamkang_bridge", "淡江大橋", 90f) {
        water(-45f, 45f)
        body { box(-45f, 6f, 45f, 8f); poly(-2.5f, 0f, 2.5f, 0f, 1.6f, 70f, -1.6f, 70f) }
        for (k in 1..9) { line(0f, 68f - k * 4f, -6f - k * 4.2f, 8f, .25f); line(0f, 68f - k * 4f, 6f + k * 4.2f, 8f, .25f) }
        glow(0f, 70f, .6f)
    }
    val loversBridge = spec("lovers_bridge", "淡水情人橋", 64f) {
        water(-32f, 32f)
        body { poly(-30f, 5f, 30f, 5f, 30f, 7f, -30f, 7f); poly(4f, 7f, 7f, 7f, -3f, 44f, -5f, 43f) }
        for (k in 0 until 8) { line(-3.5f + k * .9f, 42f - k * 4.6f, -26f + k * 7.5f, 7f, .25f) }
    }
    val keelungSign = spec("keelung_sign", "基隆 KEELUNG 地標", 80f) {
        water(-40f, 40f)
        hill(-40f, 40f, 34f)
        val letters = "KEELUNG"
        val c = if (ink.night) ink.light else Color.White.copy(alpha = .82f)
        letters.forEachIndexed { i, ch -> letterBlock(this, -27f + i * 9f, 20f, ch, c) }
        body { box(26f, 0f, 28f, 30f); poly(18f, 30f, 38f, 30f, 38f, 32f, 18f, 32f); box(36f, 22f, 37f, 30f) }
    }
    val daxiBridge = spec("daxi_bridge", "大溪橋", 86f) {
        water(-43f, 43f)
        body {
            box(-43f, 6f, 43f, 8f)
            for (s in listOf(-26f, 26f)) {
                box(s - 5f, 0f, s + 5f, 30f); arch(s, 30f, 10f, 6f); box(s - .8f, 36f, s + .8f, 40f)
            }
        }
        detail { for (s in listOf(-26f, 26f)) arch(s, 8f, 5f, 10f) }
        curve(-26f, 34f, 0f, 12f, 26f, 34f, .5f); curve(-26f, 34f, -36f, 20f, -43f, 9f, .5f); curve(26f, 34f, 36f, 20f, 43f, 9f, .5f)
        for (k in -5..5) if (k != 0) { val xx = k * 4.3f; line(xx, 8f, xx, 12f + (xx * xx) / 30f, .2f) }
    }
    val airportTower = spec("airport_tower", "桃園機場塔台", 30f) {
        body { poly(-6f, 0f, 6f, 0f, 3f, 62f, -3f, 62f); poly(-8f, 62f, 8f, 62f, 9f, 70f, -9f, 70f); box(-6f, 70f, 6f, 73f); box(-.4f, 73f, .4f, 84f) }
        detail { box(-8.5f, 64f, 8.5f, 68.5f) }
        glow(0f, 84f, .7f)
    }
    val hsinchuGate = spec("hsinchu_gate", "新竹東門城（迎曦門）", 56f) {
        body {
            poly(-28f, 0f, 28f, 0f, 25f, 22f, -25f, 22f)
            box(-16f, 22f, 16f, 32f); roof(0f, 32f, 19f, 6f); box(-10f, 38f, 10f, 42f); roof(0f, 42f, 13f, 6f)
        }
        detail { arch(0f, 0f, 10f, 14f) }
        windows(-14f, 23f, 14f, 31f, 6, 1)
    }
    val neiwanBridge = spec("neiwan_bridge", "內灣吊橋", 84f) {
        hill(-42f, -6f, 40f); hill(8f, 42f, 34f)
        water(-42f, 42f)
        body { for (s in listOf(-24f, 24f)) { box(s - 2f, 0f, s + 2f, 26f) } }
        val red = Color(0xFFC6463F).copy(alpha = ink.body.alpha)
        curve(-24f, 25f, 0f, 4f, 24f, 25f, .7f, red); curve(-24f, 18f, 0f, 6f, 24f, 18f, .5f, red)
        for (k in -5..5) { val xx = k * 4f; line(xx, 7f + xx * xx / 70f, xx, 13f + xx * xx / 40f, .2f, red) }
    }
    val longtengBridge = spec("longteng_bridge", "龍騰斷橋", 80f) {
        hill(-40f, 40f, 22f)
        body(evenOdd = true) {
            poly(-40f, 0f, -10f, 0f, -10f, 40f, -40f, 40f)
            for (k in 0 until 3) arch(-35f + k * 10f + 5f, 0f, 7.5f, 28f)
            poly(2f, 0f, 14f, 0f, 14f, 36f, 2f, 36f); arch(8f, 0f, 7.5f, 26f)
            poly(26f, 0f, 34f, 0f, 34f, 30f, 26f, 26f)
        }
        detail { for (k in 0 until 4) box(-40f + k * 10f, 39f, -38f + k * 10f, 40f) }
    }
    val taichungOpera = spec("taichung_opera", "臺中國家歌劇院", 70f) {
        body(evenOdd = true) {
            poly(-35f, 0f, 35f, 0f, 35f, 30f, -35f, 30f)
            for (k in 0 until 5) { val cx = -28f + k * 14f; ellipse(cx, 15f, 5.4f, 9f + (k % 2) * 3f) }
        }
        detail { box(-35f, 29f, 35f, 30f) }
        if (ink.night) for (k in 0 until 5) glow(-28f + k * 14f, 12f, 1.3f)
    }
    val taichungStation = spec("taichung_station", "臺中車站", 64f) {
        body { box(-30f, 0f, 30f, 18f); poly(-30f, 18f, 30f, 18f, 27f, 22f, -27f, 22f); box(-6f, 18f, 6f, 34f); poly(-7f, 34f, 7f, 34f, 4f, 40f, -4f, 40f); box(-.4f, 40f, .4f, 45f) }
        windows(-28f, 2f, 28f, 16f, 12, 2)
        detail { ellipse(0f, 28f, 3.2f, 3.2f) }
    }
    val baguaBuddha = spec("bagua_buddha", "八卦山大佛", 50f) {
        hill(-25f, 25f, 10f)
        body {
            poly(-20f, 6f, 20f, 6f, 17f, 12f, -17f, 12f)
            moveTo(px(-17f), py(12f)); cubicTo(px(-19f), py(26f), px(-15f), py(38f), px(-8f), py(42f))
            lineTo(px(8f), py(42f)); cubicTo(px(15f), py(38f), px(19f), py(26f), px(17f), py(12f)); close()
            ellipse(0f, 49f, 6f, 7.5f); ellipse(0f, 57f, 2.6f, 2.6f)
            poly(-2f, 41f, 2f, 41f, 2f, 43f, -2f, 43f)
        }
        detail { ellipse(0f, 20f, 7f, 2.5f) }
    }
    val sunMoonLake = spec("sun_moon_lake", "日月潭慈恩塔", 86f) {
        hill(-43f, 6f, 36f); hill(-4f, 43f, 26f)
        water(-43f, 43f, 5f)
        body {
            var b = 36f
            for (k in 0 until 9) { val w = 3.4f - k * .22f; box(-w, b, w, b + 3.2f); poly(-w - 1.3f, b + 3.2f, w + 1.3f, b + 3.2f, w, b + 4f, -w, b + 4f); b += 4f }
            box(-.3f, b, .3f, b + 4f)
            ellipse(24f, 1.6f, 4f, 1.4f)
        }
    }
    val beigangTemple = spec("beigang_temple", "北港朝天宮", 64f) {
        body {
            box(-30f, 0f, 30f, 14f); roof(0f, 14f, 32f, 9f, 3.2f)
            box(-12f, 23f, 12f, 27f); roof(0f, 27f, 15f, 8f, 3f)
            for (s in listOf(-21f, 21f)) { box(s - 6f, 14f, s + 6f, 18f); roof(s, 18f, 8f, 5f, 2.4f) }
        }
        detail { box(-4f, 0f, 4f, 9f); for (s in listOf(-14f, 14f)) box(s - 3f, 0f, s + 3f, 8f) }
        if (ink.night) for (k in -3..3) glow(k * 8f, 12f, .9f)
    }
    val xiluoBridge = spec("xiluo_bridge", "西螺大橋", 96f) {
        water(-48f, 48f)
        val red = Color(0xFFC73A33).copy(alpha = ink.body.alpha)
        body { box(-48f, 5f, 48f, 7f) }
        for (k in 0 until 6) {
            val l = -48f + k * 16f
            line(l, 7f, l + 8f, 17f, .6f, red); line(l + 8f, 17f, l + 16f, 7f, .6f, red); line(l, 17f, l + 16f, 17f, .7f, red)
            line(l + 8f, 7f, l + 8f, 17f, .3f, red)
        }
        line(-48f, 7f, -48f, 17f, .6f, red); line(48f, 7f, 48f, 17f, .6f, red)
    }
    val chiayiFountain = spec("chiayi_fountain", "嘉義中央噴水池", 52f) {
        body { ellipse(0f, 3f, 24f, 3f); box(-20f, 0f, 20f, 4f); box(-3f, 4f, 3f, 16f); ellipse(0f, 17f, 8f, 1.8f); box(-1.5f, 18f, 1.5f, 26f); ellipse(0f, 28f, 3f, 3.2f) }
        val w = if (ink.night) ink.light else Color.White.copy(alpha = .75f)
        for (s in listOf(-1f, 1f)) { curve(0f, 24f, s * 10f, 34f, s * 18f, 6f, .45f, w); curve(0f, 20f, s * 7f, 26f, s * 12f, 5f, .35f, w) }
    }
    val alishan = spec("alishan_railway", "阿里山小火車與神木", 92f) {
        hill(-46f, 10f, 46f); hill(-8f, 46f, 34f)
        body {
            // The sacred red cypress: a thick trunk with a broad crown.
            poly(-36f, 0f, -28f, 0f, -29.5f, 36f, -34.5f, 36f)
            ellipse(-32f, 44f, 11f, 9f); ellipse(-38f, 38f, 6f, 5f); ellipse(-25f, 39f, 6f, 5f)
        }
        val red = Color(0xFFC3352D).copy(alpha = ink.body.alpha)
        // The red diesel train on its narrow-gauge track.
        line(-20f, 1f, 46f, 1f, .5f)
        fill(path { box(-16f, 2f, 0f, 11f); box(-14f, 11f, -6f, 14f) }, red)
        for (k in 0 until 3) fill(path { box(2f + k * 14f, 2f, 14.5f + k * 14f, 10.5f) }, red)
        detail { for (k in 0 until 3) box(4f + k * 14f, 6f, 12.5f + k * 14f, 8.5f); box(-13f, 7f, -3f, 9.5f) }
        if (ink.night) for (k in 0 until 3) glow(8f + k * 14f, 7f, .8f)
    }
    val chihkan = spec("chihkan_tower", "赤崁樓", 70f) {
        body {
            poly(-34f, 0f, 34f, 0f, 32f, 10f, -32f, 10f)
            for (s in listOf(-15f, 15f)) {
                box(s - 9f, 10f, s + 9f, 20f); roof(s, 20f, 12f, 6f); box(s - 6f, 26f, s + 6f, 30f); roof(s, 30f, 8.5f, 6f, 2.6f)
            }
        }
        detail { for (s in listOf(-15f, 15f)) box(s - 7f, 12f, s + 7f, 18f) }
        if (ink.night) for (s in listOf(-15f, 15f)) glow(s, 15f, 1.2f)
    }
    val anpingFort = spec("anping_fort", "安平古堡", 44f) {
        body { poly(-22f, 0f, 22f, 0f, 20f, 12f, -20f, 12f); box(-6f, 12f, 6f, 26f); poly(-7.5f, 26f, 7.5f, 26f, 0f, 33f) ; box(-.3f, 33f, .3f, 38f) }
        windows(-5f, 14f, 5f, 24f, 2, 2)
    }
    val lotusPagodas = spec("dragon_tiger_pagodas", "蓮池潭龍虎塔", 80f) {
        water(-40f, 40f, 4f)
        for (s in listOf(-12f, 12f)) body {
            var b = 2f
            for (k in 0 until 7) { val w = 5f - k * .35f; box(s - w, b, s + w, b + 5f); roof(s, b + 5f, w + 2f, 2.2f, 1.6f); b += 7f }
            box(s - .3f, b, s + .3f, b + 4f)
        }
        val zig = path { moveTo(px(-40f), py(3f)); lineTo(px(-24f), py(4.5f)); lineTo(px(-18f), py(3f)); lineTo(px(18f), py(3f)); lineTo(px(24f), py(4.5f)); lineTo(px(40f), py(3f)); lineTo(px(40f), py(2f)); lineTo(px(-40f), py(2f)); close() }
        fill(zig)
        if (ink.night) for (s in listOf(-12f, 12f)) for (k in 0 until 7) glow(s, 5f + k * 7f, .5f)
    }
    val kaohsiung85 = spec("kaohsiung85", "高雄 85 大樓", 40f) {
        body(evenOdd = true) {
            poly(-16f, 0f, 16f, 0f, 16f, 52f, 11f, 60f, 8f, 78f, 4f, 86f, -4f, 86f, -8f, 78f, -11f, 60f, -16f, 52f)
            poly(-6f, 0f, 6f, 0f, 6f, 34f, -6f, 34f)
            box(-.6f, 86f, .6f, 100f)
        }
        windows(-15f, 36f, -7f, 58f, 3, 6, 3); windows(7f, 36f, 15f, 58f, 3, 6, 5); windows(-7f, 60f, 7f, 84f, 4, 6, 9)
        glow(0f, 100f, .8f)
    }
    val eluanbiLighthouse = spec("eluanbi_lighthouse", "鵝鑾鼻燈塔", 70f) {
        water(-35f, 35f)
        hill(-35f, 30f, 14f)
        val white = if (ink.night) ink.body else Color.White.copy(alpha = .9f)
        fill(path { poly(-4.5f, 12f, 4.5f, 12f, 3.3f, 44f, -3.3f, 44f); box(-4.5f, 44f, 4.5f, 46f) }, white)
        body { box(-2.8f, 46f, 2.8f, 51f); poly(-3.2f, 51f, 3.2f, 51f, 0f, 55f) }
        if (ink.night) { glow(0f, 48.5f, 1.4f); scope.drawPath(path { moveTo(px(0f), py(48.5f)); lineTo(px(-35f), py(56f)); lineTo(px(-35f), py(44f)); close() }, ink.light.copy(alpha = .18f)) }
        else detail { box(-2.8f, 46.5f, 2.8f, 50.5f) }
    }
    val turtleIsland = spec("turtle_island", "龜山島", 84f) {
        water(-42f, 42f, 5f)
        body {
            moveTo(px(-30f), py(0f)); cubicTo(px(-26f), py(16f), px(-14f), py(26f), px(-2f), py(27f))
            cubicTo(px(10f), py(26f), px(20f), py(14f), px(24f), py(4f))
            cubicTo(px(28f), py(9f), px(33f), py(9f), px(36f), py(4f)); lineTo(px(38f), py(0f)); close()
        }
    }
    val lanyangMuseum = spec("lanyang_museum", "蘭陽博物館", 60f) {
        water(-30f, 30f, 3f)
        body { poly(-28f, 0f, 24f, 0f, 24f, 8f, -10f, 34f, -28f, 22f) }
        detail { for (k in 0 until 6) line(-24f + k * 7f, 2f, -24f + k * 7f, 18f - k * 2f + (if (k < 3) 10f else 0f), .25f, ink.detail) }
    }
    val qingshuiCliff = spec("qingshui_cliff", "清水斷崖", 92f) {
        water(-46f, 46f, 6f)
        body {
            moveTo(px(-46f), py(0f)); lineTo(px(-46f), py(60f)); lineTo(px(-36f), py(64f)); lineTo(px(-24f), py(48f)); lineTo(px(-14f), py(52f))
            lineTo(px(-6f), py(30f)); lineTo(px(4f), py(34f)); lineTo(px(12f), py(16f)); lineTo(px(22f), py(18f)); lineTo(px(30f), py(4f)); lineTo(px(34f), py(0f)); close()
        }
        detail { line(-46f, 20f, 14f, 9f, .5f, ink.detail) }
    }
    val sanxiantai = spec("sanxiantai", "三仙台八拱橋", 96f) {
        water(-48f, 48f, 5f)
        hill(26f, 48f, 16f)
        val path = path {
            moveTo(px(-48f), py(2f))
            for (k in 0 until 8) { val l = -40f + k * 8f; quadraticTo(px(l + 4f), py(13f), px(l + 8f), py(2f)) }
            lineTo(px(26f), py(2f)); lineTo(px(26f), py(1f)); lineTo(px(-48f), py(1f)); close()
        }
        scope.drawPath(path, Color(0xFFD9443F).copy(alpha = ink.body.alpha), style = Stroke(.9f * unit))
    }
    val penghuBridge = spec("penghu_bridge", "澎湖跨海大橋", 96f) {
        water(-48f, 48f, 5f)
        body { box(-48f, 5f, 48f, 7f); for (k in 0 until 12) box(-46f + k * 8.2f, 0f, -45f + k * 8.2f, 5f); arch(0f, 7f, 14f, 6f) }
        detail { arch(0f, 7f, 11f, 4.6f) }
    }
    val twinHearts = spec("twin_hearts", "七美雙心石滬", 80f) {
        water(-40f, 40f, 12f)
        hill(18f, 40f, 12f)
        val stone = Color(0xFF8B7D6B).copy(alpha = ink.body.alpha)
        for ((cx, s) in listOf(-14f to 1f, -2f to .86f)) {
            val p = Path().apply {
                moveTo(px(cx), py(1f))
                cubicTo(px(cx - 14f * s), py(4f), px(cx - 12f * s), py(11f), px(cx - 5f * s), py(10f))
                quadraticTo(px(cx - 1f * s), py(9.6f), px(cx), py(7.6f))
                quadraticTo(px(cx + 1f * s), py(9.6f), px(cx + 5f * s), py(10f))
                cubicTo(px(cx + 12f * s), py(11f), px(cx + 14f * s), py(4f), px(cx), py(1f)); close()
            }
            scope.drawPath(p, stone, style = Stroke(.8f * unit))
        }
    }
    val juguangTower = spec("juguang_tower", "金門莒光樓", 48f) {
        body {
            poly(-24f, 0f, 24f, 0f, 22f, 6f, -22f, 6f)
            box(-15f, 6f, 15f, 17f); roof(0f, 17f, 18f, 5f)
            box(-11f, 22f, 11f, 30f); roof(0f, 30f, 14f, 5f)
            box(-7f, 35f, 7f, 41f); roof(0f, 41f, 10f, 7f, 2.4f)
        }
        windows(-13f, 7f, 13f, 16f, 6, 1); windows(-9f, 23f, 9f, 29f, 4, 1)
    }
    val windLion = spec("wind_lion", "金門風獅爺", 26f) {
        body {
            box(-6f, 0f, 6f, 6f)
            moveTo(px(-5f), py(6f)); cubicTo(px(-7f), py(16f), px(-6f), py(26f), px(-2f), py(30f))
            lineTo(px(4f), py(30f)); cubicTo(px(7f), py(24f), px(6f), py(14f), px(5f), py(6f)); close()
            ellipse(0f, 32f, 6.5f, 5.5f)
        }
        detail { ellipse(-2.4f, 33f, 1f, 1f); ellipse(2.4f, 33f, 1f, 1f); box(-3f, 29.5f, 3f, 30.6f) }
    }
    val qinbi = spec("qinbi_village", "馬祖芹壁聚落", 80f) {
        water(-40f, 40f, 4f)
        hill(-40f, 40f, 30f)
        for (k in 0 until 7) {
            val cx = -28f + k * 9f; val b = 4f + (k % 3) * 6f + (if (k in 2..4) 6f else 0f)
            fill(path { box(cx - 3.6f, b, cx + 3.6f, b + 5.5f); poly(cx - 4.2f, b + 5.5f, cx + 4.2f, b + 5.5f, cx, b + 9f) },
                Color(0xFF9C9488).copy(alpha = ink.body.alpha))
            windows(cx - 3f, b + 1f, cx + 3f, b + 4.5f, 2, 1, k)
        }
    }
    val dongyinLighthouse = spec("dongyin_lighthouse", "東引燈塔", 60f) {
        water(-30f, 30f)
        body { moveTo(px(-30f), py(0f)); lineTo(px(-30f), py(26f)); lineTo(px(4f), py(30f)); lineTo(px(16f), py(22f)); lineTo(px(26f), py(0f)); close() }
        val white = if (ink.night) ink.body else Color.White.copy(alpha = .9f)
        fill(path { box(-10f, 28f, 0f, 34f); poly(-7.5f, 34f, -2.5f, 34f, -3f, 46f, -7f, 46f) }, white)
        body { box(-7.5f, 46f, -2.5f, 50f); poly(-8f, 50f, -2f, 50f, -5f, 53f) }
        glow(-5f, 48f, 1.2f)
    }

    // ---------------- Abroad ----------------
    val tokyoTower = spec("tokyo_tower", "東京鐵塔", 40f) {
        val orange = Color(0xFFE2572B).copy(alpha = ink.body.alpha)
        fill(path(evenOdd = true) {
            poly(-19f, 0f, -6f, 30f, -4f, 50f, -2f, 80f, -.6f, 92f, .6f, 92f, 2f, 80f, 4f, 50f, 6f, 30f, 19f, 0f, 12f, 0f, 0f, 16f, -12f, 0f)
            box(-.4f, 92f, .4f, 100f)
        }, orange)
        body { box(-8f, 30f, 8f, 34f); box(-4.5f, 60f, 4.5f, 63f) }
        glow(0f, 32f, 1f)
    }
    val skytree = spec("skytree", "東京晴空塔", 26f) {
        body {
            poly(-10f, 0f, 10f, 0f, 4.2f, 30f, 3f, 54f, -3f, 54f, -4.2f, 30f)
            poly(-5.6f, 54f, 5.6f, 54f, 5f, 60f, -5f, 60f)
            poly(-2.4f, 60f, 2.4f, 60f, 2f, 72f, -2f, 72f); poly(-3.4f, 72f, 3.4f, 72f, 3f, 75f, -3f, 75f)
            poly(-1.4f, 75f, 1.4f, 75f, .5f, 100f, -.5f, 100f)
        }
        detail { box(-5.6f, 56f, 5.6f, 58f); box(-3.4f, 72.8f, 3.4f, 74f) }
        glow(0f, 100f, .7f)
    }
    val osakaCastle = spec("osaka_castle", "大阪城", 56f) {
        body {
            poly(-28f, 0f, 28f, 0f, 24f, 16f, -24f, 16f)
            var b = 16f
            for (k in 0 until 5) { val w = 15f - k * 2.4f; box(-w, b, w, b + 5f); roof(0f, b + 5f, w + 3f, 3.4f, 2f); b += 8.4f }
        }
        val gold = Color(0xFFD8B24A).copy(alpha = ink.body.alpha)
        fill(path { ellipse(-4f, 58f, 1f, 1f); ellipse(4f, 58f, 1f, 1f) }, gold)
        windows(-13f, 17f, 13f, 21f, 7, 1)
    }
    val kyotoPagoda = spec("kyoto_pagoda", "京都五重塔", 34f) {
        body {
            var b = 0f
            for (k in 0 until 5) { val w = 7f - k * .6f; box(-w, b, w, b + 6f); roof(0f, b + 6f, w + 6f, 3f, 2.6f); b += 10f }
            box(-.5f, b, .5f, b + 18f)
        }
        detail { for (k in 0 until 9) box(-1.2f, 52f + k * 2f, 1.2f, 52.6f + k * 2f) }
    }
    val namsanTower = spec("namsan_tower", "首爾 N 首爾塔", 60f) {
        hill(-30f, 30f, 26f)
        body { poly(-2.6f, 24f, 2.6f, 24f, 1.8f, 62f, -1.8f, 62f); poly(-6f, 62f, 6f, 62f, 5f, 70f, -5f, 70f); box(-.5f, 70f, .5f, 96f) }
        detail { box(-6f, 64f, 6f, 67f) }
        glow(0f, 66f, 1f)
    }
    val hongKong = spec("hong_kong", "香港維港天際線", 90f) {
        hill(-45f, 45f, 52f, ink.body.copy(alpha = ink.body.alpha * .6f))
        water(-45f, 45f, 4f)
        body {
            box(-40f, 0f, -32f, 30f); box(-30f, 0f, -24f, 38f)
            poly(-20f, 0f, -8f, 0f, -8f, 64f, -11f, 70f, -14f, 72f, -17f, 70f, -20f, 64f)
            poly(-4f, 0f, 8f, 0f, 8f, 44f, 2f, 60f, -4f, 52f); box(1.8f, 60f, 2.2f, 70f)
            box(12f, 0f, 20f, 40f); box(22f, 0f, 30f, 28f); box(32f, 0f, 42f, 34f)
        }
        windows(-20f, 2f, -8f, 62f, 3, 12, 2); windows(12f, 2f, 20f, 38f, 2, 7, 4); windows(32f, 2f, 42f, 32f, 3, 6, 8)
        detail { line(-4f, 0f, 8f, 44f, .3f, ink.detail); line(8f, 0f, -4f, 52f, .3f, ink.detail) }
    }
    val pearlTower = spec("oriental_pearl", "上海東方明珠", 40f) {
        body {
            poly(-17f, 0f, -14f, 0f, -2f, 28f, -4f, 28f); poly(17f, 0f, 14f, 0f, 2f, 28f, 4f, 28f)
            box(-3.5f, 0f, 3.5f, 70f); ellipse(0f, 28f, 9f, 9f); ellipse(0f, 62f, 6f, 6f); ellipse(0f, 78f, 2.6f, 2.6f)
            box(-1.6f, 70f, 1.6f, 76f); poly(-1f, 80f, 1f, 80f, .3f, 100f, -.3f, 100f)
        }
        val pink = Color(0xFFE07A9A).copy(alpha = ink.body.alpha * .9f)
        fill(path { ellipse(0f, 28f, 8f, 8f); ellipse(0f, 62f, 5.2f, 5.2f); ellipse(0f, 78f, 2.2f, 2.2f) }, pink)
        glow(0f, 62f, 1f)
    }
    val marinaBaySands = spec("marina_bay_sands", "新加坡濱海灣金沙", 80f) {
        water(-40f, 40f, 4f)
        body {
            for (k in 0 until 3) { val cx = -20f + k * 20f; poly(cx - 6f, 0f, cx + 6f, 0f, cx + 5f, 46f, cx - 5f, 46f) }
            moveTo(px(-34f), py(46f)); lineTo(px(32f), py(46f)); quadraticTo(px(38f), py(47f), px(40f), py(50f)); lineTo(px(-30f), py(50f)); close()
        }
        for (k in 0 until 3) windows(-25f + k * 20f, 2f, -15f + k * 20f, 44f, 3, 10, k)
    }
    val watArun = spec("wat_arun", "曼谷鄭王廟", 70f) {
        water(-35f, 35f, 3f)
        body {
            poly(-30f, 0f, 30f, 0f, 26f, 8f, -26f, 8f)
            moveTo(px(-10f), py(8f)); cubicTo(px(-9f), py(30f), px(-5f), py(46f), px(-1f), py(56f)); lineTo(px(0f), py(66f)); lineTo(px(1f), py(56f))
            cubicTo(px(5f), py(46f), px(9f), py(30f), px(10f), py(8f)); close()
            for (s in listOf(-21f, 21f)) { moveTo(px(s - 4f), py(8f)); cubicTo(px(s - 3.5f), py(20f), px(s - 1f), py(26f), px(s), py(32f)); cubicTo(px(s + 1f), py(26f), px(s + 3.5f), py(20f), px(s + 4f), py(8f)); close() }
        }
        detail { for (k in 0 until 6) box(-9f + k * .9f, 12f + k * 6f, 9f - k * .9f, 12.6f + k * 6f) }
    }
    val eiffel = spec("eiffel", "巴黎艾菲爾鐵塔", 44f) {
        body(evenOdd = true) {
            moveTo(px(-21f), py(0f)); cubicTo(px(-12f), py(18f), px(-6f), py(42f), px(-3f), py(70f)); lineTo(px(-1f), py(92f)); lineTo(px(1f), py(92f)); lineTo(px(3f), py(70f))
            cubicTo(px(6f), py(42f), px(12f), py(18f), px(21f), py(0f)); lineTo(px(14f), py(0f)); quadraticTo(px(0f), py(26f), px(-14f), py(0f)); close()
            box(-11f, 21f, 11f, 24f); box(-6.5f, 44f, 6.5f, 46.5f); box(-.4f, 92f, .4f, 100f)
        }
        glow(0f, 95f, .7f)
    }
    val bigBen = spec("big_ben", "倫敦大笨鐘", 30f) {
        body {
            box(-6f, 0f, 6f, 58f); box(-7f, 58f, 7f, 68f); poly(-7f, 68f, 7f, 68f, 5f, 74f, -5f, 74f)
            poly(-5f, 74f, 5f, 74f, 0f, 96f); box(-.3f, 96f, .3f, 100f)
            for (s in listOf(-6.5f, 6.5f)) poly(s - .8f, 68f, s + .8f, 68f, s, 73f)
        }
        val face = if (ink.night) ink.light else Color.White.copy(alpha = .8f)
        fill(path { ellipse(0f, 63f, 4.4f, 4.4f) }, face)
        windows(-4.5f, 4f, 4.5f, 54f, 2, 12)
    }
    val towerBridge = spec("tower_bridge", "倫敦塔橋", 90f) {
        water(-45f, 45f)
        body {
            box(-45f, 6f, 45f, 8f)
            for (s in listOf(-16f, 16f)) { box(s - 5f, 0f, s + 5f, 34f); poly(s - 5.5f, 34f, s + 5.5f, 34f, s, 42f); for (t in listOf(-4.5f, 4.5f)) poly(s + t - 1f, 34f, s + t + 1f, 34f, s + t, 38f) }
            box(-11f, 28f, 11f, 31f)
        }
        detail { for (s in listOf(-16f, 16f)) arch(s, 8f, 6f, 12f) }
        curve(-21f, 26f, -34f, 10f, -45f, 9f, .5f); curve(21f, 26f, 34f, 10f, 45f, 9f, .5f)
    }
    val liberty = spec("liberty", "紐約自由女神", 30f) {
        body {
            poly(-13f, 0f, 13f, 0f, 11.5f, 6f, -11.5f, 6f); poly(-7f, 6f, 7f, 6f, 5.6f, 30f, -5.6f, 30f)
            // Robed figure, wider at the hem.
            poly(-5.2f, 30f, 5.2f, 30f, 3.8f, 58f, 2.6f, 70f, -2.4f, 70f, -3.6f, 58f)
            ellipse(0f, 73.5f, 2.4f, 3f)
            for (k in -3..3) poly(k * 1.1f - .35f, 75.5f, k * 1.1f + .35f, 75.5f, k * 2.4f, 80f - kotlin.math.abs(k) * .5f)
            // Raised right arm and the torch.
            poly(-2.6f, 68f, -1.2f, 68.6f, -4.4f, 90f, -5.8f, 89.6f)
            poly(-6.8f, 89.6f, -3.6f, 89.6f, -4.2f, 93f, -6.2f, 93f)
            // The tablet held in the left arm.
            poly(2.6f, 52f, 5.4f, 53f, 5f, 61f, 2.4f, 60f)
        }
        val flame = Color(0xFFF2C14E).copy(alpha = ink.body.alpha)
        fill(path { moveTo(px(-6.6f), py(93f)); quadraticTo(px(-5.6f), py(98.5f), px(-5.2f), py(100f)); quadraticTo(px(-4f), py(96.5f), px(-3.8f), py(93f)); close() }, flame)
        glow(-5.2f, 96f, .9f)
    }
    val empireState = spec("empire_state", "紐約帝國大廈", 30f) {
        body {
            box(-12f, 0f, 12f, 18f); box(-9f, 18f, 9f, 64f); box(-6.5f, 64f, 6.5f, 72f); box(-4.5f, 72f, 4.5f, 78f)
            poly(-2.8f, 78f, 2.8f, 78f, 1.6f, 88f, -1.6f, 88f); box(-.4f, 88f, .4f, 100f)
        }
        windows(-8f, 20f, 8f, 62f, 4, 14, 3)
        glow(0f, 84f, 1f)
    }
    val goldenGate = spec("golden_gate", "舊金山金門大橋", 100f) {
        water(-50f, 50f)
        val red = Color(0xFFC0462C).copy(alpha = ink.body.alpha)
        fill(path {
            box(-50f, 7f, 50f, 9f)
            for (s in listOf(-24f, 24f)) { box(s - 2.6f, 0f, s - 1f, 44f); box(s + 1f, 0f, s + 2.6f, 44f); for (h in listOf(18f, 30f, 40f)) box(s - 2.6f, h, s + 2.6f, h + 1.6f) }
        }, red)
        curve(-24f, 43f, 0f, 8f, 24f, 43f, .55f, red); curve(-24f, 43f, -40f, 18f, -50f, 11f, .55f, red); curve(24f, 43f, 40f, 18f, 50f, 11f, .55f, red)
        for (k in -5..5) if (k != 0) { val xx = k * 4f; line(xx, 9f, xx, 10f + xx * xx / 17f, .15f, red) }
    }
    val operaHouse = spec("sydney_opera", "雪梨歌劇院", 84f) {
        water(-42f, 42f, 4f)
        body { box(-36f, 0f, 36f, 6f) }
        val shell = if (ink.night) ink.body.copy(alpha = ink.body.alpha) else Color.White.copy(alpha = .88f)
        val shells = listOf(Triple(-24f, 15f, 20f), Triple(-12f, 14f, 26f), Triple(0f, 12f, 30f), Triple(12f, 12f, 24f), Triple(24f, 10f, 18f))
        shells.forEach { (cx, w, h) ->
            fill(path { moveTo(px(cx - w / 2), py(6f)); quadraticTo(px(cx - w / 2), py(6f + h * .7f), px(cx + w / 2), py(6f + h)); quadraticTo(px(cx + w * .1f), py(6f + h * .4f), px(cx + w * .25f), py(6f)); close() }, shell)
            line(cx + w / 2, 6f + h, cx + w * .25f, 6f, .2f, ink.body)
        }
        if (ink.night) shells.forEach { glow(it.first, 9f, .8f) }
    }
    val burjKhalifa = spec("burj_khalifa", "杜拜哈里發塔", 26f) {
        body {
            poly(-12f, 0f, 12f, 0f, 12f, 10f, 9f, 10f, 9f, 24f, 7f, 24f, 7f, 38f, 5.4f, 38f, 5.4f, 52f, 4f, 52f, 4f, 64f, 2.8f, 64f, 2.6f, 76f,
                1.4f, 76f, .5f, 100f, -.5f, 100f, -1.6f, 80f, -2.6f, 80f, -3f, 68f, -4.4f, 68f, -4.6f, 56f, -6f, 56f, -6f, 42f, -8f, 42f, -8f, 28f, -10f, 28f, -10f, 12f, -12f, 12f)
        }
        windows(-7f, 12f, 7f, 60f, 3, 14, 6)
        glow(0f, 100f, .7f)
    }
    val colosseum = spec("colosseum", "羅馬競技場", 84f) {
        body(evenOdd = true) {
            moveTo(px(-42f), py(0f)); lineTo(px(-42f), py(30f)); lineTo(px(-10f), py(32f)); lineTo(px(4f), py(40f)); lineTo(px(16f), py(40f))
            lineTo(px(28f), py(26f)); lineTo(px(42f), py(22f)); lineTo(px(42f), py(0f)); close()
            for (row in 0 until 3) for (k in 0 until 10) {
                val cx = -38f + k * 8.4f; val b = 2f + row * 10f
                val top = when { cx > 26f -> 22f; cx > 16f -> 26f + (26f - cx); cx > -10f -> 32f; else -> 30f }
                if (b + 8f < top) arch(cx, b, 4.6f, 7f)
            }
        }
    }

    // ---------------- registry ----------------
    private fun letterBlock(pen: LandmarkPen, x0: Float, y0: Float, ch: Char, c: Color) = with(pen) {
        val p = path {
            when (ch) {
                'K' -> { box(x0, y0, x0 + 1.4f, y0 + 9f); poly(x0 + 1.4f, y0 + 4f, x0 + 6f, y0 + 9f, x0 + 7.6f, y0 + 9f, x0 + 2.6f, y0 + 4.4f, x0 + 7.6f, y0, x0 + 6f, y0, x0 + 1.4f, y0 + 4.6f) }
                'E' -> { box(x0, y0, x0 + 1.4f, y0 + 9f); box(x0, y0 + 7.6f, x0 + 6f, y0 + 9f); box(x0, y0 + 3.8f, x0 + 5f, y0 + 5.2f); box(x0, y0, x0 + 6f, y0 + 1.4f) }
                'L' -> { box(x0, y0, x0 + 1.4f, y0 + 9f); box(x0, y0, x0 + 6f, y0 + 1.4f) }
                'U' -> { box(x0, y0, x0 + 1.4f, y0 + 9f); box(x0 + 5f, y0, x0 + 6.4f, y0 + 9f); box(x0, y0, x0 + 6.4f, y0 + 1.4f) }
                'N' -> { box(x0, y0, x0 + 1.4f, y0 + 9f); box(x0 + 5f, y0, x0 + 6.4f, y0 + 9f); poly(x0 + 1.4f, y0 + 9f, x0 + 2.8f, y0 + 9f, x0 + 5f, y0, x0 + 3.6f, y0) }
                'G' -> { box(x0, y0, x0 + 1.4f, y0 + 9f); box(x0, y0 + 7.6f, x0 + 6.2f, y0 + 9f); box(x0, y0, x0 + 6.2f, y0 + 1.4f); box(x0 + 4.8f, y0, x0 + 6.2f, y0 + 4.6f); box(x0 + 3f, y0 + 3.4f, x0 + 6.2f, y0 + 4.6f) }
            }
        }
        scope.drawPath(p, c)
    }

    val all: List<LandmarkSpec> = listOf(taipei101, grandHotel, tamkangBridge, loversBridge, keelungSign, daxiBridge, airportTower,
        hsinchuGate, neiwanBridge, longtengBridge, taichungOpera, taichungStation, baguaBuddha, sunMoonLake, beigangTemple, xiluoBridge,
        chiayiFountain, alishan, chihkan, anpingFort, lotusPagodas, kaohsiung85, eluanbiLighthouse, turtleIsland, lanyangMuseum,
        qingshuiCliff, sanxiantai, penghuBridge, twinHearts, juguangTower, windLion, qinbi, dongyinLighthouse,
        tokyoTower, skytree, osakaCastle, kyotoPagoda, namsanTower, hongKong, pearlTower, marinaBaySands, watArun, eiffel, bigBen,
        towerBridge, liberty, empireState, goldenGate, operaHouse, burjKhalifa, colosseum)
}
