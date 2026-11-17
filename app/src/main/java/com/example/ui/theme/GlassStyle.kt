package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight, GPU-friendly moderate glassmorphism helpers.
 *
 * Uses translucent tonal gradients paired with subtle specular hairline borders instead of
 * expensive per-frame RenderEffect blur passes over scrolling text/code, preserving locked
 * 60-120fps responsiveness across all Android API levels.
 */
@Composable
fun rememberGlassBorderBrush(
  highlightColor: Color? = null,
  subtleColor: Color? = null,
): Brush {
  val palette = LocalIdePalette.current
  val top = highlightColor ?: palette.glassBorderHighlight
  val bottom = subtleColor ?: palette.glassBorderSubtle
  return remember(top, bottom) {
    Brush.linearGradient(colors = listOf(top, bottom))
  }
}

@Composable
fun rememberGlassBorderStroke(
  width: Dp = 1.dp,
  highlightColor: Color? = null,
  subtleColor: Color? = null,
): BorderStroke {
  val brush = rememberGlassBorderBrush(highlightColor, subtleColor)
  return remember(width, brush) { BorderStroke(width, brush) }
}

@Composable
fun Modifier.edgeGlassSurface(
  shape: Shape = RoundedCornerShape(20.dp),
  elevated: Boolean = false,
  borderWidth: Dp = 1.dp,
): Modifier {
  val palette = LocalIdePalette.current
  val baseColor = if (elevated) palette.glassElevated else palette.glassSurface
  val topSheen = if (palette.isDark) Color(0x16FFFFFF) else Color(0x45FFFFFF)
  val bgBrush =
    remember(baseColor, topSheen) {
      Brush.verticalGradient(
        colors = listOf(baseColor.compositeOverSafe(topSheen), baseColor),
      )
    }
  val borderBrush = rememberGlassBorderBrush()
  return this
    .clip(shape)
    .background(bgBrush, shape)
    .border(borderWidth, borderBrush, shape)
}

@Composable
fun Modifier.edgeGlassDock(
  shape: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
): Modifier {
  val palette = LocalIdePalette.current
  val top = if (palette.isDark) palette.glassDockBg.copy(alpha = 0.92f) else palette.glassDockBg.copy(alpha = 0.95f)
  val bottom = palette.chromeBg.copy(alpha = 0.96f)
  val bgBrush =
    remember(top, bottom) {
      Brush.verticalGradient(colors = listOf(top, bottom))
    }
  return this
    .clip(shape)
    .background(bgBrush, shape)
}

private fun Color.compositeOverSafe(tint: Color): Color {
  val a = tint.alpha
  val inv = 1f - a
  return Color(
    red = tint.red * a + this.red * inv,
    green = tint.green * a + this.green * inv,
    blue = tint.blue * a + this.blue * inv,
    alpha = this.alpha,
  )
}
