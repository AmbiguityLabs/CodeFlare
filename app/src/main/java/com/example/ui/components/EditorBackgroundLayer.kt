package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
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

@Composable
fun rememberHasActiveEditorBackdrop(settings: EditorSettings): Boolean =
  rememberHasActiveEditorBackground(settings) || settings.editorBubbles.enabled

/**
 * Renders the customizable code editor backdrop: either a user-selected custom photo
 * or a curated lightweight ambient backdrop, plus optional interactive bottom-to-top
 * floating bubbles rendered in a zero-allocation Canvas pass.
 */
@Composable
fun EditorBackgroundLayer(
  settings: EditorSettings,
  modifier: Modifier = Modifier,
  bubbleController: EditorBubbleController? = null,
) {
  val palette = LocalIdePalette.current
  val density = LocalDensity.current.density
  val preset = EditorBackgroundPreset.fromKey(settings.editorBgPreset)
  val customFile =
    remember(settings.editorBgImagePath) {
      settings.editorBgImagePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
    }
  val hasActiveBg =
    preset != EditorBackgroundPreset.NONE &&
      (preset != EditorBackgroundPreset.CUSTOM || customFile != null)
  val bubblesConfig = settings.editorBubbles.normalized()

  val zoom = settings.editorBgZoom.coerceIn(1f, 3f)
  val opacity = settings.editorBgOpacity.coerceIn(0.05f, 1f)
  val scrimAlpha = settings.editorBgScrim.coerceIn(0f, 0.85f)
  val offsetX = settings.editorBgOffsetX.coerceIn(-0.5f, 0.5f)
  val offsetY = settings.editorBgOffsetY.coerceIn(-0.5f, 0.5f)

  var canvasSize by remember { mutableStateOf(IntSize.Zero) }
  var bubbleFrameTick by remember { mutableLongStateOf(0L) }

  LaunchedEffect(bubbleController, bubblesConfig, canvasSize, density) {
    val ctrl = bubbleController ?: return@LaunchedEffect
    ctrl.updateConfig(
      config = bubblesConfig,
      widthPx = canvasSize.width.toFloat(),
      heightPx = canvasSize.height.toFloat(),
      density = density,
    )
    if (!bubblesConfig.enabled) return@LaunchedEffect

    var lastNanos = 0L
    while (true) {
      withFrameNanos { frameNanos ->
        if (lastNanos != 0L && canvasSize.width > 1 && canvasSize.height > 1) {
          val dtSec = ((frameNanos - lastNanos) / 1_000_000_000.0).toFloat()
          ctrl.advance(
            dtSec = dtSec,
            widthPx = canvasSize.width.toFloat(),
            heightPx = canvasSize.height.toFloat(),
            density = density,
          )
        }
        lastNanos = frameNanos
        bubbleFrameTick++
      }
    }
  }

  val bubbleColors =
    remember(bubblesConfig.colorStyle, palette.activePillFg, palette.isDark) {
      EditorBubbleController.resolveBubblePalette(
        style = bubblesConfig.colorStyle,
        accent = palette.activePillFg,
        isDark = palette.isDark,
      )
    }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .clipToBounds()
        .background(palette.editorBg)
        .onSizeChanged { canvasSize = it },
  ) {
    if (hasActiveBg) {
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

      // Readability tint scrim for high-contrast code legibility over custom/ambient backdrop
      if (scrimAlpha > 0.005f) {
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .background(palette.editorBg.copy(alpha = scrimAlpha)),
        )
      }
    }

    if (bubblesConfig.enabled && bubbleController != null) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        // Read frame tick inside DrawScope so only drawing invalidates — zero recomposition!
        @Suppress("UNUSED_VARIABLE")
        val tick = bubbleFrameTick
        val widthPx = size.width
        val baseOpacity = bubblesConfig.opacity
        val strokeWidth = 1.1.dp.toPx()
        val limit = bubbleController.activeBubblesCount().coerceIn(0, EditorBubbleController.MAX_BUBBLES)

        for (i in 0 until limit) {
          val b = bubbleController.bubbles[i]
          if (!b.active) continue
          val cx = bubbleController.bubbleCenterX(b, widthPx)
          val cy = b.yPx
          if (cy + b.radiusPx < -8f || cy - b.radiusPx > size.height + 8f) continue

          val baseColor = bubbleColors[b.paletteIndex % bubbleColors.size]
          val alpha = (baseOpacity * b.alphaMultiplier).coerceIn(0.04f, 0.88f)
          val center = Offset(cx, cy)

          drawCircle(
            brush =
              Brush.radialGradient(
                colors =
                  listOf(
                    baseColor.copy(alpha = alpha * 0.24f),
                    baseColor.copy(alpha = alpha * 0.62f),
                    baseColor.copy(alpha = alpha * 0.92f),
                  ),
                center = Offset(cx + b.radiusPx * 0.18f, cy + b.radiusPx * 0.18f),
                radius = b.radiusPx,
              ),
            radius = b.radiusPx,
            center = center,
          )

          drawCircle(
            color = baseColor.copy(alpha = (alpha * 1.15f).coerceAtMost(0.95f)),
            radius = b.radiusPx,
            center = center,
            style = Stroke(width = strokeWidth),
          )

          // Crisp glass glint in top-left rim
          val glintRadius = (b.radiusPx * 0.22f).coerceIn(1.5f, 5.5.dp.toPx())
          drawCircle(
            color = Color.White.copy(alpha = (alpha * 1.25f).coerceAtMost(0.85f)),
            radius = glintRadius,
            center = Offset(cx - b.radiusPx * 0.36f, cy - b.radiusPx * 0.36f),
          )
        }

        // Draw expanding pop bursts
        for (i in 0 until EditorBubbleController.MAX_BURSTS) {
          val burst = bubbleController.bursts[i]
          if (!burst.active) continue
          val p = burst.progress.coerceIn(0f, 1f)
          val burstColor = bubbleColors[burst.paletteIndex % bubbleColors.size]
          val ringRadius = burst.startRadiusPx * (1.0f + p * 1.15f)
          val ringAlpha = ((1f - p) * (baseOpacity * 1.6f).coerceIn(0.25f, 0.95f)).coerceIn(0f, 1f)
          drawCircle(
            color = burstColor.copy(alpha = ringAlpha),
            radius = ringRadius,
            center = Offset(burst.xPx, burst.yPx),
            style = Stroke(width = (2.2f * (1f - p * 0.65f)).dp.toPx()),
          )
        }

        // Draw pop micro-droplets
        for (i in 0 until EditorBubbleController.MAX_DROPLETS) {
          val d = bubbleController.droplets[i]
          if (!d.active) continue
          val p = d.progress.coerceIn(0f, 1f)
          val easeOut = 1f - (1f - p) * (1f - p)
          val dropletColor = bubbleColors[d.paletteIndex % bubbleColors.size]
          val dx = d.originX + d.vxPx * easeOut
          val dy = d.originY + d.vyPx * easeOut
          val alpha = ((1f - p) * (baseOpacity * 1.5f).coerceIn(0.3f, 0.95f)).coerceIn(0f, 1f)
          drawCircle(
            color = dropletColor.copy(alpha = alpha),
            radius = d.radiusPx * (1f - p * 0.55f),
            center = Offset(dx, dy),
          )
        }
      }
    }
  }
}
