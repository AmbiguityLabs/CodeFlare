package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.security.EditorSettings
import com.example.ui.theme.EditorBackgroundPreset
import com.example.ui.theme.LocalIdePalette
import java.io.File

@Composable
fun rememberHasActiveEditorBackground(settings: EditorSettings): Boolean {
  val preset = EditorBackgroundPreset.fromKey(settings.editorBgPreset)
  return remember(preset, settings.editorBgImagePath) {
    when (preset) {
      EditorBackgroundPreset.NONE -> false
      EditorBackgroundPreset.CUSTOM ->
        settings.editorBgImagePath.isNotBlank() && File(settings.editorBgImagePath).exists()
      else -> true
    }
  }
}

/**
 * Renders the customizable code editor backdrop: either a user-selected custom photo
 * or a curated lightweight ambient backdrop, with live Opacity, Zoom, Pan framing,
 * and a readability tint scrim so syntax-highlighted code always stays razor-sharp.
 */
@Composable
fun EditorBackgroundLayer(
  settings: EditorSettings,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val preset = EditorBackgroundPreset.fromKey(settings.editorBgPreset)
  val customFile =
    remember(settings.editorBgImagePath) {
      settings.editorBgImagePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
    }
  val hasActive =
    preset != EditorBackgroundPreset.NONE &&
      (preset != EditorBackgroundPreset.CUSTOM || customFile != null)

  val zoom = settings.editorBgZoom.coerceIn(1f, 3f)
  val opacity = settings.editorBgOpacity.coerceIn(0.05f, 1f)
  val scrimAlpha = settings.editorBgScrim.coerceIn(0f, 0.85f)
  val offsetX = settings.editorBgOffsetX.coerceIn(-0.5f, 0.5f)
  val offsetY = settings.editorBgOffsetY.coerceIn(-0.5f, 0.5f)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .clipToBounds()
        .background(palette.editorBg),
  ) {
    if (!hasActive) return@Box

    if (preset == EditorBackgroundPreset.CUSTOM && customFile != null) {
      AsyncImage(
        model = customFile,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier
            .fillMaxSize()
            .graphicsLayer {
              scaleX = zoom
              scaleY = zoom
              translationX = offsetX * size.width
              translationY = offsetY * size.height
              alpha = opacity
            },
      )
    } else {
      val isDark = palette.isDark
      val accent = palette.activePillFg
      Canvas(
        modifier =
          Modifier
            .fillMaxSize()
            .graphicsLayer {
              scaleX = zoom
              scaleY = zoom
              translationX = offsetX * size.width
              translationY = offsetY * size.height
              alpha = opacity
            },
      ) {
        when (preset) {
          EditorBackgroundPreset.AURORA_MIST -> {
            drawRect(
              brush =
                Brush.radialGradient(
                  colors =
                    if (isDark) listOf(Color(0xFF1F4E46), Color(0xFF142824), Color.Transparent)
                    else listOf(Color(0xFFA7F3D0), Color(0xFFD1FAE5), Color.Transparent),
                  center = Offset(size.width * 0.22f, size.height * 0.18f),
                  radius = size.maxDimension * 0.72f,
                ),
            )
            drawRect(
              brush =
                Brush.radialGradient(
                  colors =
                    if (isDark) listOf(accent.copy(alpha = 0.55f), Color(0xFF1E2D1A), Color.Transparent)
                    else listOf(Color(0xFFBFDBFE), Color(0xFFE0F2FE), Color.Transparent),
                  center = Offset(size.width * 0.84f, size.height * 0.76f),
                  radius = size.maxDimension * 0.68f,
                ),
            )
          }

          EditorBackgroundPreset.DEEP_OCEAN -> {
            drawRect(
              brush =
                Brush.linearGradient(
                  colors =
                    if (isDark) listOf(Color(0xFF0A1D37), Color(0xFF0F2942), Color(0xFF0C1424))
                    else listOf(Color(0xFFDBEAFE), Color(0xFFE0F2FE), Color(0xFFF0F9FF)),
                  start = Offset.Zero,
                  end = Offset(size.width, size.height),
                ),
            )
            drawRect(
              brush =
                Brush.radialGradient(
                  colors =
                    if (isDark) listOf(Color(0xFF1D4ED8).copy(alpha = 0.48f), Color.Transparent)
                    else listOf(Color(0xFF38BDF8).copy(alpha = 0.35f), Color.Transparent),
                  center = Offset(size.width * 0.78f, size.height * 0.24f),
                  radius = size.maxDimension * 0.62f,
                ),
            )
          }

          EditorBackgroundPreset.CARBON_GRID -> {
            val stepPx = 24.dp.toPx()
            val lineColor =
              if (isDark) accent.copy(alpha = 0.20f)
              else Color(0xFF334155).copy(alpha = 0.16f)
            val strokePx = 0.7.dp.toPx()
            var x = 0f
            while (x <= size.width) {
              drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokePx)
              x += stepPx
            }
            var y = 0f
            while (y <= size.height) {
              drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = strokePx)
              y += stepPx
            }
            val dotColor =
              if (isDark) accent.copy(alpha = 0.42f)
              else Color(0xFF1E293B).copy(alpha = 0.28f)
            var gx = stepPx
            while (gx < size.width) {
              var gy = stepPx
              while (gy < size.height) {
                drawCircle(dotColor, radius = 1.15.dp.toPx(), center = Offset(gx, gy))
                gy += stepPx * 2f
              }
              gx += stepPx * 2f
            }
          }

          EditorBackgroundPreset.DUSK_HORIZON -> {
            drawRect(
              brush =
                Brush.linearGradient(
                  colors =
                    if (isDark) listOf(Color(0xFF2B1914), Color(0xFF1C141E), Color(0xFF111216))
                    else listOf(Color(0xFFFFEDD5), Color(0xFFFEE2E2), Color(0xFFFDF8F6)),
                  start = Offset(0f, 0f),
                  end = Offset(size.width * 0.6f, size.height),
                ),
            )
            drawRect(
              brush =
                Brush.radialGradient(
                  colors =
                    if (isDark) listOf(Color(0xFFF97316).copy(alpha = 0.45f), Color(0xFFFB7185).copy(alpha = 0.18f), Color.Transparent)
                    else listOf(Color(0xFFFB923C).copy(alpha = 0.35f), Color.Transparent),
                  center = Offset(size.width * 0.2f, size.height * 0.85f),
                  radius = size.maxDimension * 0.7f,
                ),
            )
          }

          EditorBackgroundPreset.NONE,
          EditorBackgroundPreset.CUSTOM,
          -> Unit
        }
      }
    }

    // Readability tint scrim for high-contrast code legibility
    if (scrimAlpha > 0.005f) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(palette.editorBg.copy(alpha = scrimAlpha)),
      )
    }
  }
}
