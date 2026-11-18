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
  val resolvedWidth = width.coerceAtMost(0.75.dp)
  val lineColor = subtleColor ?: highlightColor ?: palette.glassBorderSubtle
  return remember(resolvedWidth, lineColor) { BorderStroke(resolvedWidth, lineColor) }
}

@Composable
fun Modifier.edgeGlassSurface(
  shape: Shape = RoundedCornerShape(14.dp),
  elevated: Boolean = false,
  borderWidth: Dp = 0.75.dp,
): Modifier {
  val palette = LocalIdePalette.current
  val fillColor = if (elevated) palette.glassElevated else palette.glassSurface
  val strokeWidth = borderWidth.coerceAtMost(0.75.dp)
  val borderColor = if (elevated) palette.glassBorderHighlight else palette.glassBorderSubtle
  return this
    .clip(shape)
    .background(fillColor, shape)
    .border(strokeWidth, borderColor, shape)
}

@Composable
fun Modifier.edgeGlassDock(
  shape: Shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
): Modifier {
  val palette = LocalIdePalette.current
  return this
    .clip(shape)
    .background(palette.glassDockBg, shape)
}
