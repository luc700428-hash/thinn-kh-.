package com.kyant.backdrop.effects

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.BackdropEffectScope

/**
 * thinn-khì addition: the refraction model of Zaki Sheriff's Liquid Glass (MIT,
 * github.com/zakisheriff/Liquid-Glass, src/engine/refraction.ts), ported from its baked SVG
 * displacement maps to a per-pixel AGSL shader.
 *
 * Light crossing a convex squircle bevel h(t) = (1 − (1 − t)⁴)^¼ is bent by Snell's law
 * (index [ior]); the lateral shift at a distance d inside the rim is
 * tan(θi − θr) · thickness · (0.35 + 0.65 h). A "surface" (bars, buttons, cards) pulls the scene
 * inward at the rim; a "lens" (lifted selections, knobs) bends outward, magnifies by [zoomX] /
 * [zoomY] and splits red and blue apart ([dispersion]). [stretch] replaces the lens's outward pull
 * with the streaked, smeared rim of a thick droplet. All lengths are in pixels.
 */
fun BackdropEffectScope.liquidRefraction(
    lens: Boolean,
    bezel: Float,
    thickness: Float,
    ior: Float = 1.5f,
    zoomX: Float = 1f,
    zoomY: Float = zoomX,
    dispersion: Float = 0f,
    strength: Float = 1f,
    stretch: Float = 0f,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (bezel <= 0f || thickness <= 0f || size.minDimension < 2f) return
    val radii = liquidCornerRadii ?: return
    val shader = obtainRuntimeShader("LiquidGlassRefraction", LiquidGlassRefractionShader)
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("offset", -padding, -padding)
    shader.setFloatUniform("cornerRadii", radii)
    shader.setFloatUniform("bezel", bezel)
    shader.setFloatUniform("thickness", thickness)
    shader.setFloatUniform("ior", ior.coerceIn(1.01f, 3f))
    shader.setFloatUniform("kind", if (lens) 1f else 0f)
    shader.setFloatUniform("magnify", 1f - 1f / zoomX.coerceAtLeast(.5f), 1f - 1f / zoomY.coerceAtLeast(.5f))
    shader.setFloatUniform("dispersion", dispersion.coerceIn(0f, 1f))
    shader.setFloatUniform("strength", strength.coerceIn(0f, 3f))
    shader.setFloatUniform("stretch", stretch.coerceIn(0f, 1f))
    effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
}

private val BackdropEffectScope.liquidCornerRadii: FloatArray?
    get() {
        val shape = shape as? CornerBasedShape ?: return null
        val max = size.minDimension / 2f
        val ltr = layoutDirection == LayoutDirection.Ltr
        val tl = (if (ltr) shape.topStart else shape.topEnd).toPx(size, this)
        val tr = (if (ltr) shape.topEnd else shape.topStart).toPx(size, this)
        val br = (if (ltr) shape.bottomEnd else shape.bottomStart).toPx(size, this)
        val bl = (if (ltr) shape.bottomStart else shape.bottomEnd).toPx(size, this)
        return floatArrayOf(tl.fastCoerceAtMost(max), tr.fastCoerceAtMost(max), br.fastCoerceAtMost(max), bl.fastCoerceAtMost(max))
    }

internal const val LiquidGlassRefractionShader = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float bezel;
uniform float thickness;
uniform float ior;
uniform float kind;
uniform float2 magnify;
uniform float dispersion;
uniform float strength;
uniform float stretch;

float radiusAt(float2 p, float4 r) {
    if (p.x >= 0.0) { return p.y <= 0.0 ? r.y : r.z; }
    return p.y <= 0.0 ? r.x : r.w;
}
float sdRoundedRect(float2 p, float2 h, float r) {
    float2 q = abs(p) - (h - float2(r));
    return length(max(q, 0.0)) - r + min(max(q.x, q.y), 0.0);
}
float2 normalOf(float2 p, float2 h, float r) {
    float2 q = abs(p) - (h - float2(r));
    if (q.x >= 0.0 && q.y >= 0.0) return sign(p) * normalize(q);
    if (q.x > q.y) return float2(sign(p.x), 0.0);
    return float2(0.0, sign(p.y));
}
// Lateral shift of light leaving the squircle bevel at distance d inside the rim.
float shiftAt(float d) {
    if (d < 0.0 || d >= bezel) return 0.0;
    float t = max(0.002, d / bezel);
    float q = 1.0 - t;
    float q2 = q * q;
    float base = 1.0 - q2 * q2;
    float height = pow(base, 0.25);
    float slope = q2 * q / max(0.0001, pow(base, 0.75));
    float incident = atan(slope);
    float refracted = asin(sin(incident) / ior);
    return tan(incident - refracted) * thickness * (0.35 + 0.65 * height);
}

half4 main(float2 coord) {
    float2 h = size * 0.5;
    float2 p = coord + offset - h;
    float r = radiusAt(p, cornerRadii);
    float d = -sdRoundedRect(p, h, r);
    bool lens = kind > 0.5;
    bool zooms = lens && (abs(magnify.x) > 0.0001 || abs(magnify.y) > 0.0001);
    if (d < 0.0 || (d >= bezel && !zooms)) return content.eval(coord);
    float2 n = normalOf(p, h, r);
    float u = d / bezel;
    float s = 0.0;
    if (lens && stretch > 0.0) {
        // The rim samples ever more steeply inward, smearing what is under it out to the edge.
        s = u < 1.0 ? -bezel * stretch * 0.5 * (1.0 - u) * (1.0 - u) : 0.0;
    } else {
        s = shiftAt(d) * (lens ? 1.0 : -1.0);
    }
    float2 edge = n * s * strength;
    // Magnification runs first, then the rim bends what it magnified.
    float2 source = coord - (lens ? p * magnify : float2(0.0));
    if (lens && dispersion > 0.0 && abs(s) > 0.001) {
        float spread = (stretch > 0.0 ? 0.12 : 0.16) * dispersion;
        half4 g = content.eval(source + edge);
        half4 red = content.eval(source + edge * (1.0 + spread));
        half4 blue = content.eval(source + edge * (1.0 - spread));
        return half4(red.r, g.g, blue.b, g.a);
    }
    return content.eval(source + edge);
}
"""
