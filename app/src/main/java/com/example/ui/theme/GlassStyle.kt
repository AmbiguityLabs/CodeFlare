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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern, ultra-thin flat surface & hairline border primitives.
 *
 * Replaces heavy 3D-style translucent glass gradients and glossy specular rims with crisp,
 * low-profile matte tonal surfaces and razor-thin hairline strokes.
 */
@Composable
fun rememberGlassBorderBrush(
  highlightColor: Color? = null,
  subtleColor: Color? = null,
): Brush {
  val palette = LocalIdePalette.current
  val lineColor = subtleColor ?: highlightColor ?: palette.glassBorderSubtle
  return remember(lineColor) { SolidColor(lineColor) }
}

@Composable
fun rememberGlassBorderStroke(
  width: Dp = 0.75.dp,
  highlightColor: Color? = null,
  subtleColor: Color? = null,
): BorderStroke {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val resolvedWidth = if (isV2 && highlightColor == null && subtleColor == null) 0.5.dp else width.coerceAtMost(0.75.dp)
  val lineColor =
    subtleColor
      ?: highlightColor
      ?: if (isV2) palette.glassBorderSubtle.copy(alpha = 0.45f) else palette.glassBorderSubtle
  return remember(resolvedWidth, lineColor) { BorderStroke(resolvedWidth, lineColor) }
}

@Composable
fun Modifier.edgeGlassSurface(
  shape: Shape = RoundedCornerShape(14.dp),
  elevated: Boolean = false,
  borderWidth: Dp = 0.75.dp,
): Modifier {
  val palette = LocalIdePalette.current
  val settings = LocalEditorSettings.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val cornerDp = if (isV2) 22.dp else 14.dp
  val resolvedShape = if (isV2 && shape == RoundedCornerShape(14.dp)) RoundedCornerShape(cornerDp) else shape
  if (settings.liquidGlassEnabled) {
    return this.liquidGlassSurface(
      shape = resolvedShape,
      cornerRadius = cornerDp,
      thickness = if (elevated) 1.25f else 1.0f,
      surfaceOpacityMultiplier = if (elevated) 1.05f else 0.96f,
      borderOpacityMultiplier = if (elevated) 1.1f else 0.85f,
      drawShadow = elevated,
    )
  }
  val fillColor = if (elevated) palette.glassElevated else palette.glassSurface
  if (isV2 && !elevated) {
    return this
      .clip(resolvedShape)
      .background(fillColor, resolvedShape)
  }
  val strokeWidth = borderWidth.coerceAtMost(0.75.dp)
  val borderColor = if (elevated) palette.glassBorderHighlight else palette.glassBorderSubtle
  return this
    .clip(resolvedShape)
    .background(fillColor, resolvedShape)
    .border(strokeWidth, borderColor, resolvedShape)
}

@Composable
fun Modifier.edgeGlassDock(
  shape: Shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
): Modifier {
  val palette = LocalIdePalette.current
  val settings = LocalEditorSettings.current
  if (settings.liquidGlassEnabled) {
    return this.liquidGlassSurface(
      shape = shape,
      cornerRadius = 14.dp,
      thickness = 1.12f,
      surfaceOpacityMultiplier = 0.98f,
      borderOpacityMultiplier = 0.7f,
      drawShadow = false,
    )
  }
  return this
    .clip(shape)
    .background(palette.glassDockBg, shape)
}
