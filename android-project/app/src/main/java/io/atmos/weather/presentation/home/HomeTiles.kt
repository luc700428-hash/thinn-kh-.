package io.atmos.weather.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.atmos.weather.ui.AppText as Text
import io.atmos.weather.ui.WeatherCardPalettes

/**
 * One tile shape for the whole weather home, so every square lines up: same corner, padding and
 * type, every line single and ellipsised, and rows built with [HomeTileRow] share one height.
 * Colours come from the card palette, so text stays readable in both light and dark appearance.
 */
internal object HomeInk {
    val primary: Color get() = WeatherCardPalettes.current.ink
    val secondary: Color get() = WeatherCardPalettes.current.secondary
    /** A faint fill that darkens light cards and lightens dark ones. */
    val tile: Color get() = WeatherCardPalettes.current.ink.copy(alpha = .065f)
    val hairline: Color get() = WeatherCardPalettes.current.ink.copy(alpha = .12f)
}

@Composable
internal fun HomeTileRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
internal fun HomeTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = HomeInk.secondary,
    valueColor: Color = HomeInk.primary,
    bottom: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxHeight().heightIn(min = if (bottom != null) 128.dp else 96.dp)
        .clip(RoundedCornerShape(18.dp)).background(HomeInk.tile).padding(horizontal = 12.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(label, color = HomeInk.secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = TextStyle(fontFeatureSettings = "tnum"))
        Text(caption.ifBlank { " " }, color = HomeInk.secondary, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (bottom != null) {
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            bottom()
        }
    }
}

/**
 * iOS-weather style scale: a thin gradient track with a ringed dot where the value sits.
 * [fraction] outside 0..1 is clamped; null draws the track alone.
 */
@Composable
internal fun HomeScaleBar(fraction: Float?, colors: List<Color>, modifier: Modifier = Modifier) {
    val ring = WeatherCardPalettes.current.fill
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val h = 4.dp.toPx(); val y = size.height / 2
        drawRoundRect(Brush.horizontalGradient(colors.map { it.copy(alpha = .85f) }), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        if (fraction != null && fraction.isFinite()) {
            val x = (h / 2 + (size.width - h) * fraction.coerceIn(0f, 1f))
            drawCircle(ring.copy(alpha = 1f), 5.dp.toPx(), Offset(x, y))
            drawCircle(Color.White, 3.6.dp.toPx(), Offset(x, y))
        }
    }
}

internal object HomeScales {
    val uv = listOf(Color(0xFF6FD08C), Color(0xFFF2D35B), Color(0xFFF59E4B), Color(0xFFE5554F), Color(0xFFB06BD8))
    val aqi = listOf(Color(0xFF6FD08C), Color(0xFFF2D35B), Color(0xFFF59E4B), Color(0xFFE5554F), Color(0xFF9B5DD0))
    val warmth = listOf(Color(0xFF7FB7FF), Color(0xFF8AD9C4), Color(0xFFF2D35B), Color(0xFFF59E4B), Color(0xFFE5554F))
    val water = listOf(Color(0xFFBFD9F2), Color(0xFF6FB3EE), Color(0xFF3F7FD8))
    val wind = listOf(Color(0xFF9EE0C8), Color(0xFF5CC2A0), Color(0xFFF2B35B), Color(0xFFE5554F))
    val sky = listOf(Color(0xFF8E97A8), Color(0xFFB5D8E8), Color(0xFF7FD0A0))
    val pressure = listOf(Color(0xFF8FA6D8), Color(0xFFB9C6E0), Color(0xFF8AD9C4))
    val sun = listOf(Color(0xFF4E5D86), Color(0xFFF7C66B), Color(0xFFF59E4B), Color(0xFF4E5D86))
}
