package com.example.ui.theme

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Real "liquid glass": the content BEHIND a glass element is captured into a GraphicsLayer
 * (glassSource) and re-drawn inside the glass with a lens warp (AGSL, API 33+) and blur (API 31+).
 * The glass element's own children (icons, text) are never distorted.
 */
class GlassBackdrop {
  internal var layer: GraphicsLayer? = null
  var origin by mutableStateOf(Offset.Zero)
  internal val tick = mutableIntStateOf(0)
  internal var raw = 0
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
  val b = remember { GlassBackdrop() }
  b.layer = rememberGraphicsLayer()
  return b
}

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Mark the content that glass elements should refract. Must NOT contain the glass elements. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier =
  this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
      val layer = backdrop.layer
      if (layer == null || size.width < 1f || size.height < 1f) {
        drawContent()
      } else {
        layer.record(this, layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
          this@drawWithContent.drawContent()
        }
        drawLayer(layer)
        // write-only (no read) so the source never invalidates itself
        backdrop.raw += 1
        backdrop.tick.intValue = backdrop.raw
      }
    }

private const val GLASS_LENS_AGSL =
  """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float radius;
uniform float refH;
uniform float amount;

half4 main(float2 coord) {
  float2 p = coord - offset - size * 0.5;
  float2 b = size * 0.5 - float2(radius);
  float2 q = abs(p) - b;
  float d = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
  float depth = clamp(-d / refH, 0.0, 1.0);
  float bend = (1.0 - depth) * (1.0 - depth);
  float2 n;
  if (q.x > 0.0 && q.y > 0.0) {
    n = normalize(q * sign(p) + float2(0.0001));
  } else if (q.x > q.y) {
    n = float2(sign(p.x), 0.0);
  } else {
    n = float2(0.0, sign(p.y));
  }
  float2 src = coord + n * bend * amount;
  return content.eval(src);
}
"""

/**
 * Draws backdrop-refracting glass behind this element. [press] (0..1) strengthens lens + light.
 * With [bubble] = true nothing is drawn until pressed (used for the finger-following dock bubble).
 */
@Composable
fun Modifier.glassLens(
  backdrop: GlassBackdrop,
  cornerRadius: Dp,
  isDark: Boolean,
  press: () -> Float = { 0f },
  bubble: Boolean = false,
): Modifier {
  val lens = rememberGraphicsLayer()
  var pos by remember { mutableStateOf(Offset.Zero) }
  val density = LocalDensity.current
  val cornerPx = with(density) { cornerRadius.toPx() }
  val refHPx = with(density) { 22.dp.toPx() }
  val baseAmountPx = with(density) { 20.dp.toPx() }
  val blurPx = with(density) { 3.dp.toPx() }
  val strokePx = with(density) { 1.dp.toPx() }
  val shader = remember { if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(GLASS_LENS_AGSL) }.getOrNull() else null }

  return this
    .onGloballyPositioned { pos = it.positionInRoot() }
    .drawBehind {
      val p = press().coerceIn(0f, 1f)
      if (bubble && p < 0.01f) return@drawBehind
      backdrop.tick.intValue // read -> redraw whenever the source changes
      val w = size.width
      val h = size.height
      if (w < 4f || h < 4f) return@drawBehind
      val r = cornerPx.coerceAtMost(minOf(w, h) / 2f)
      val clip = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(r, r))) }
      val amount = baseAmountPx * (1f + 0.6f * p)
      val margin = (amount + blurPx * 2f).toInt() + 4
      val srcLayer = backdrop.layer

      if (srcLayer != null && Build.VERSION.SDK_INT >= 31) {
        val ok =
          runCatching {
            lens.record(this, layoutDirection, IntSize(w.toInt() + 2 * margin, h.toInt() + 2 * margin)) {
              translate(margin - (pos.x - backdrop.origin.x), margin - (pos.y - backdrop.origin.y)) {
                drawLayer(srcLayer)
              }
            }
            val blur = AndroidRenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            val effect =
              if (shader != null) {
                shader.setFloatUniform("size", w, h)
                shader.setFloatUniform("offset", margin.toFloat(), margin.toFloat())
                shader.setFloatUniform("radius", r)
                shader.setFloatUniform("refH", refHPx)
                shader.setFloatUniform("amount", amount)
                AndroidRenderEffect.createChainEffect(
                  AndroidRenderEffect.createRuntimeShaderEffect(shader, "content"),
                  blur,
                )
              } else {
                blur
              }
            lens.renderEffect = effect.asComposeRenderEffect()
            clipPath(clip) {
              translate(-margin.toFloat(), -margin.toFloat()) { drawLayer(lens) }
            }
          }.isSuccess
        ok.hashCode()
      }

      // tint: clear glass, a touch brighter when pressed
      val tintTop = if (isDark) Color.White.copy(alpha = 0.10f + 0.10f * p) else Color.White.copy(alpha = 0.26f + 0.14f * p)
      val tintBottom = if (isDark) Color.White.copy(alpha = 0.04f + 0.06f * p) else Color.White.copy(alpha = 0.12f + 0.10f * p)
      clipPath(clip) {
        drawRect(brush = Brush.verticalGradient(listOf(tintTop, tintBottom)))
        // inner light from under the finger / top sheen
        drawRect(
          brush =
            Brush.verticalGradient(
              colors = listOf(Color.White.copy(alpha = 0.16f + 0.12f * p), Color.Transparent),
              endY = h * 0.5f,
            ),
        )
      }
      // specular rim: bright top-left, dim middle, soft bottom-right
      drawPath(
        path = clip,
        brush =
          Brush.linearGradient(
            colors =
              listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.45f),
              ),
            start = Offset.Zero,
            end = Offset(w, h),
          ),
        style = Stroke(width = strokePx * (1f + 0.5f * p)),
      )
    }
}
