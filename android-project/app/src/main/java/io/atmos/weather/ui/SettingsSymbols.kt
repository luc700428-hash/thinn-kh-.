package io.atmos.weather.ui

/** Original white glyphs for the 29dp coloured settings tiles. */
object SettingsSymbols {
    val general get()=AppleNavSymbols.settings
    val alarm by lazy {appGlyph("alarm-clock") {glyphStroke {
        glyphCircle(12f,13f,7.5f)
        moveTo(12f,8.3f);lineTo(12f,13f);lineTo(15f,14.8f)
        moveTo(3.2f,5.3f);lineTo(6.3f,2.4f);moveTo(17.7f,2.4f);lineTo(20.8f,5.3f)
        moveTo(7f,19.1f);lineTo(5.4f,21.5f);moveTo(17f,19.1f);lineTo(18.6f,21.5f)
    }}}
    val accessibility by lazy {appGlyph("accessibility") {glyphStroke {
        glyphCircle(12f,12f,10f);glyphCircle(12f,6.6f,1.35f)
        moveTo(6.7f,10f);lineTo(12f,11f);lineTo(17.3f,10f)
        moveTo(12f,11f);lineTo(12f,14.5f);moveTo(12f,14.5f);lineTo(9.5f,19f)
        moveTo(12f,14.5f);lineTo(14.5f,19f)
    }}}
    val display by lazy {appGlyph("sun-display") {glyphStroke {
        glyphCircle(12f,12f,4.2f)
        for(i in 0 until 8) {
            val a=i*Math.PI/4;val x=kotlin.math.cos(a).toFloat();val y=kotlin.math.sin(a).toFloat()
            moveTo(12f+x*7.3f,12f+y*7.3f);lineTo(12f+x*10f,12f+y*10f)
        }
    }}}
    val voice by lazy {appGlyph("voice-wave") {glyphStroke {
        moveTo(3f,10f);lineTo(3f,14f);moveTo(7.5f,6f);lineTo(7.5f,18f)
        moveTo(12f,2.7f);lineTo(12f,21.3f);moveTo(16.5f,6f);lineTo(16.5f,18f)
        moveTo(21f,10f);lineTo(21f,14f)
    }}}
    val notification by lazy {appGlyph("notification-bell") {glyphStroke {
        moveTo(5f,16f);lineTo(6.5f,13.5f);lineTo(6.5f,9f)
        curveTo(6.5f,1.5f,17.5f,1.5f,17.5f,9f);lineTo(17.5f,13.5f)
        lineTo(19f,16f);close();moveTo(9.3f,19f);curveTo(10.4f,21.4f,13.6f,21.4f,14.7f,19f)
    }}}
    val weather get()=AppleNavSymbols.weather
    val cyclone by lazy {appGlyph("cyclone") {glyphStroke {
        glyphCircle(12f,12f,3.5f)
        moveTo(11f,8.6f);curveTo(6f,7f,9f,1.2f,16.5f,2.2f)
        moveTo(15.4f,11f);curveTo(17f,6f,22.8f,9f,21.8f,16.5f)
        moveTo(13f,15.4f);curveTo(18f,17f,15f,22.8f,7.5f,21.8f)
        moveTo(8.6f,13f);curveTo(7f,18f,1.2f,15f,2.2f,7.5f)
    }}}
    val controls by lazy {appGlyph("controls") {glyphStroke {
        for(y in listOf(7f,17f)) {
            moveTo(6f,y-3.5f);lineTo(18f,y-3.5f)
            curveTo(22.7f,y-3.5f,22.7f,y+3.5f,18f,y+3.5f)
            lineTo(6f,y+3.5f);curveTo(1.3f,y+3.5f,1.3f,y-3.5f,6f,y-3.5f);close()
        };glyphCircle(7f,7f,1.7f);glyphCircle(17f,17f,1.7f)
    }}}
    val wallpaper by lazy {appGlyph("wallpaper-flower") {glyphStroke {
        glyphCircle(12f,12f,2.2f)
        for(i in 0 until 6) {
            val a=i*Math.PI/3;glyphCircle(12f+kotlin.math.cos(a).toFloat()*5.6f,
                12f+kotlin.math.sin(a).toFloat()*5.6f,3.3f)
        }
    }}}
    val update by lazy {appGlyph("software-update") {glyphStroke {
        glyphCircle(12f,12f,9.5f);moveTo(12f,5.8f);lineTo(12f,17.5f)
        moveTo(7.8f,13.4f);lineTo(12f,17.5f);lineTo(16.2f,13.4f)
    }}}
    val lifestyle by lazy {appGlyph("clothing") {glyphStroke {
        moveTo(8f,3f);curveTo(9f,6f,15f,6f,16f,3f)
        lineTo(22f,7f);lineTo(19f,11f);lineTo(17f,9.7f);lineTo(17f,21f)
        lineTo(7f,21f);lineTo(7f,9.7f);lineTo(5f,11f);lineTo(2f,7f);close()
    }}}
}
