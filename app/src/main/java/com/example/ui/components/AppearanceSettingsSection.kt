package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.screens.settings.SettingsToggleRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.WorkerLanguage
import com.example.data.security.EditorSettings
import com.example.editor.SyntaxHighlighter
import com.example.ui.theme.EditorBackgroundPreset
import com.example.ui.theme.EditorThemePreset
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.paletteFor
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full Appearance & Custom Editor Background Studio for Settings.
 * Supports theme preset swatches, Dark/Light mode, Material You toggle, zero-permission
 * Android Photo Picker for custom editor backgrounds, built-in ambient backdrops,
 * live Opacity + Zoom + Readability Tint controls, and interactive drag-to-pan framing.
 */
@Composable
fun AppearanceStudioContent(
  settings: EditorSettings,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onPickEditorBackgroundImage: (Uri) -> Unit,
  onClearEditorBackgroundImage: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val activePreset = EditorThemePreset.fromKey(settings.themePreset)
  val activeBgPreset = EditorBackgroundPreset.fromKey(settings.editorBgPreset)
  val hasCustomPhotoFile =
    remember(settings.editorBgImagePath) {
      settings.editorBgImagePath.isNotBlank() && File(settings.editorBgImagePath).exists()
    }
  val hasActiveBg = rememberHasActiveEditorBackground(settings)

  // Zero-permission Android Photo Picker (Play Policy compliant)
  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        onPickEditorBackgroundImage(uri)
      }
    }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // 1. Dark / Light Mode Ultra-Thin Segmented Control
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(palette.editorBg)
          .border(0.75.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
          .padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      listOf(
        true to ("Dark" to Icons.Outlined.DarkMode),
        false to ("Light" to Icons.Outlined.LightMode),
      ).forEach { (isDarkChoice, pair) ->
        val (label, icon) = pair
        val selected = settings.darkTheme == isDarkChoice
        Surface(
          onClick = { onUpdateSettings { it.copy(darkTheme = isDarkChoice) } },
          shape = RoundedCornerShape(8.dp),
          color = if (selected) palette.activePillBg else Color.Transparent,
          border =
            if (selected) BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.35f))
            else null,
          modifier = Modifier.weight(1f).height(34.dp).testTag("settings_mode_${label.lowercase()}"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = if (selected) palette.activePillFg else palette.textSecondary,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = label,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
              color = if (selected) palette.activePillFg else palette.textSecondary,
            )
          }
        }
      }
    }

    // 2. Curated Theme Presets Grid (2x2 ultra-thin swatch cards)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = "THEME PALETTE",
        fontFamily = JetBrainsMono,
        fontSize = 9.5.sp,
        letterSpacing = 0.6.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textMuted,
      )
      val presets = EditorThemePreset.entries
      presets.chunked(2).forEach { rowPresets ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          rowPresets.forEach { preset ->
            val selected = preset == activePreset
            val previewPalette = remember(settings.darkTheme, preset) { paletteFor(settings.darkTheme, preset) }
            Surface(
              onClick = { onUpdateSettings { it.copy(themePreset = preset.name) } },
              shape = RoundedCornerShape(10.dp),
              color = if (selected) palette.activePillBg.copy(alpha = 0.55f) else palette.editorBg,
              border =
                BorderStroke(
                  width = if (selected) 1.dp else 0.75.dp,
                  color = if (selected) palette.activePillFg.copy(alpha = 0.65f) else palette.border.copy(alpha = 0.55f),
                ),
              modifier = Modifier.weight(1f).testTag("settings_theme_preset_${preset.name.lowercase()}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                // Mini 3-dot palette preview
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier =
                      Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(previewPalette.editorBg)
                        .border(0.75.dp, previewPalette.border, CircleShape),
                  )
                  Box(
                    modifier =
                      Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(previewPalette.chromeSurface)
                        .border(0.75.dp, previewPalette.border, CircleShape),
                  )
                  Box(
                    modifier =
                      Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(previewPalette.activePillFg),
                  )
                }
                Text(
                  text = preset.label,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                if (selected) {
                  Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected theme",
                    tint = palette.activePillFg,
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            }
          }
        }
      }
    }

    SettingsToggleRow(
      title = "Material You",
      checked = settings.dynamicColor,
      onCheckedChange = { dyn -> onUpdateSettings { it.copy(dynamicColor = dyn) } },
      horizontalPadding = 2.dp,
    )

    HorizontalDivider(color = palette.border.copy(alpha = 0.4f), thickness = 0.75.dp)

    // 3. Editor Background & Custom Photo Studio
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Wallpaper,
            contentDescription = null,
            tint = palette.activePillFg,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "EDITOR BACKGROUND",
            fontFamily = JetBrainsMono,
            fontSize = 9.5.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textMuted,
          )
        }
        Surface(
          color = palette.editorBg,
          shape = RoundedCornerShape(6.dp),
          border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.5f)),
        ) {
          Text(
            text =
              if (activeBgPreset == EditorBackgroundPreset.CUSTOM && hasCustomPhotoFile) "Custom Photo"
              else activeBgPreset.label,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = palette.activePillFg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          )
        }
      }

      // Live Interactive Code Editor Preview (supports drag-to-pan framing when zoomed > 1x)
      InteractiveEditorBackgroundPreview(
        settings = settings,
        hasActiveBg = hasActiveBg,
        onPanDelta = { dxNorm, dyNorm ->
          onUpdateSettings { cur ->
            val maxShift = ((cur.editorBgZoom - 1f) * 0.45f).coerceIn(0f, 0.5f)
            cur.copy(
              editorBgOffsetX = (cur.editorBgOffsetX + dxNorm).coerceIn(-maxShift, maxShift),
              editorBgOffsetY = (cur.editorBgOffsetY + dyNorm).coerceIn(-maxShift, maxShift),
            )
          }
        },
      )

      // Custom Photo Picker CTA + Remove Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
          },
          shape = RoundedCornerShape(10.dp),
          color = palette.activePillBg,
          border = BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.4f)),
          modifier = Modifier.weight(1f).height(40.dp).testTag("settings_pick_editor_bg_button"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.AddPhotoAlternate,
              contentDescription = "Choose custom background photo",
              tint = palette.activePillFg,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
              text = if (hasCustomPhotoFile) "Change Photo" else "Choose Photo",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = palette.activePillFg,
            )
          }
        }

        if (hasActiveBg || hasCustomPhotoFile) {
          OutlinedButton(
            onClick = onClearEditorBackgroundImage,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.7f)),
            modifier = Modifier.height(40.dp).testTag("settings_clear_editor_bg_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.DeleteOutline,
              contentDescription = "Remove editor background",
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text("Clear", fontSize = 12.sp)
          }
        }
      }

      // Built-in Backdrop Presets Strip
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val visiblePresets =
          buildList {
            add(EditorBackgroundPreset.NONE)
            if (hasCustomPhotoFile) add(EditorBackgroundPreset.CUSTOM)
            add(EditorBackgroundPreset.AURORA_MIST)
            add(EditorBackgroundPreset.DEEP_OCEAN)
            add(EditorBackgroundPreset.CARBON_GRID)
            add(EditorBackgroundPreset.DUSK_HORIZON)
          }
        visiblePresets.forEach { bg ->
          val selected = bg == activeBgPreset
          val dotColor =
            when (bg) {
              EditorBackgroundPreset.NONE -> palette.editorBg
              EditorBackgroundPreset.CUSTOM -> palette.activePillFg
              EditorBackgroundPreset.AURORA_MIST -> Color(0xFF2DD4BF)
              EditorBackgroundPreset.DEEP_OCEAN -> Color(0xFF38BDF8)
              EditorBackgroundPreset.CARBON_GRID -> Color(0xFF94A3B8)
              EditorBackgroundPreset.DUSK_HORIZON -> Color(0xFFF97316)
            }
          Surface(
            onClick = { onUpdateSettings { it.copy(editorBgPreset = bg.key) } },
            shape = RoundedCornerShape(8.dp),
            color = if (selected) palette.activePillBg else palette.editorBg,
            border =
              BorderStroke(
                0.75.dp,
                if (selected) palette.activePillFg.copy(alpha = 0.55f) else palette.border.copy(alpha = 0.55f),
              ),
            modifier = Modifier.testTag("settings_bg_preset_${bg.key.lowercase()}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(0.75.dp, palette.border, CircleShape),
              )
              Text(
                text = bg.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) palette.activePillFg else palette.textSecondary,
              )
            }
          }
        }
      }

      // Live Fine-Tuning Sliders: Opacity + Zoom + Readability Scrim (when any background is active)
      if (hasActiveBg) {
        Surface(
          color = palette.editorBg,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // Opacity Slider (5% - 100%)
            val opacityPct = (settings.editorBgOpacity.coerceIn(0.05f, 1f) * 100f).roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Background Opacity", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                Text(
                  text = "$opacityPct%",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Slider(
                value = settings.editorBgOpacity.coerceIn(0.05f, 1f),
                onValueChange = { v -> onUpdateSettings { it.copy(editorBgOpacity = v.coerceIn(0.05f, 1f)) } },
                valueRange = 0.05f..1.0f,
                modifier = Modifier.testTag("settings_bg_opacity_slider"),
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.18f to "18%", 0.35f to "35%", 0.60f to "60%", 0.85f to "85%").forEach { (valF, lbl) ->
                  val close = kotlin.math.abs(settings.editorBgOpacity - valF) < 0.04f
                  QuickPresetChip(
                    label = lbl,
                    selected = close,
                    onClick = { onUpdateSettings { it.copy(editorBgOpacity = valF) } },
                  )
                }
              }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

            // Zoom Slider (1.00x - 3.00x)
            val zoomVal = settings.editorBgZoom.coerceIn(1f, 3f)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Background Zoom", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                Text(
                  text = String.format(Locale.US, "%.2f×", zoomVal),
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Slider(
                value = zoomVal,
                onValueChange = { z ->
                  onUpdateSettings { cur ->
                    val clampedZ = z.coerceIn(1f, 3f)
                    val maxShift = ((clampedZ - 1f) * 0.45f).coerceIn(0f, 0.5f)
                    cur.copy(
                      editorBgZoom = clampedZ,
                      editorBgOffsetX = cur.editorBgOffsetX.coerceIn(-maxShift, maxShift),
                      editorBgOffsetY = cur.editorBgOffsetY.coerceIn(-maxShift, maxShift),
                    )
                  }
                },
                valueRange = 1.0f..3.0f,
                modifier = Modifier.testTag("settings_bg_zoom_slider"),
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                listOf(1.0f to "1.0×", 1.35f to "1.35×", 1.8f to "1.8×", 2.4f to "2.4×").forEach { (zF, lbl) ->
                  val close = kotlin.math.abs(zoomVal - zF) < 0.08f
                  QuickPresetChip(
                    label = lbl,
                    selected = close,
                    onClick = {
                      onUpdateSettings { cur ->
                        val maxShift = ((zF - 1f) * 0.45f).coerceIn(0f, 0.5f)
                        cur.copy(
                          editorBgZoom = zF,
                          editorBgOffsetX = cur.editorBgOffsetX.coerceIn(-maxShift, maxShift),
                          editorBgOffsetY = cur.editorBgOffsetY.coerceIn(-maxShift, maxShift),
                        )
                      }
                    },
                  )
                }
                Spacer(Modifier.weight(1f))
                if (zoomVal > 1.01f || settings.editorBgOffsetX != 0f || settings.editorBgOffsetY != 0f) {
                  TextButton(
                    onClick = {
                      onUpdateSettings {
                        it.copy(editorBgZoom = 1f, editorBgOffsetX = 0f, editorBgOffsetY = 0f)
                      }
                    },
                    modifier = Modifier.height(28.dp),
                  ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Reset", fontSize = 10.5.sp)
                  }
                }
              }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

            // Readability Tint / Scrim Slider (0% - 85%)
            val scrimPct = (settings.editorBgScrim.coerceIn(0f, 0.85f) * 100f).roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Code Contrast Scrim", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                Text(
                  text = "$scrimPct%",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Slider(
                value = settings.editorBgScrim.coerceIn(0f, 0.85f),
                onValueChange = { s -> onUpdateSettings { it.copy(editorBgScrim = s.coerceIn(0f, 0.85f)) } },
                valueRange = 0.0f..0.85f,
                modifier = Modifier.testTag("settings_bg_scrim_slider"),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun InteractiveEditorBackgroundPreview(
  settings: EditorSettings,
  hasActiveBg: Boolean,
  onPanDelta: (dxNorm: Float, dyNorm: Float) -> Unit,
) {
  val palette = LocalIdePalette.current
  val canPan = hasActiveBg && settings.editorBgZoom > 1.01f
  val latestOnPanDelta by rememberUpdatedState(onPanDelta)
  var previewSize by remember { mutableStateOf(IntSize(1, 1)) }

  val indent = " ".repeat(settings.tabSize.coerceIn(2, 4))
  val sampleCode =
    remember(indent) {
      """export default {
${indent}async fetch(req: Request, env: Env): Promise<Response> {
${indent}${indent}const colo = req.cf?.colo ?? "EDGE";
${indent}${indent}const data = await env.KV.get("status");
${indent}${indent}return Response.json({ ok: true, colo, data });
${indent}},
};"""
    }
  val highlighted =
    remember(sampleCode, palette) {
      SyntaxHighlighter.highlight(sampleCode, WorkerLanguage.TYPESCRIPT, palette)
    }

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(168.dp)
        .clip(RoundedCornerShape(12.dp))
        .border(0.75.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        .onSizeChanged { previewSize = it }
        .then(
          if (canPan) {
            Modifier.pointerInput(canPan) {
              detectDragGestures { change, dragAmount ->
                change.consume()
                val w = previewSize.width.coerceAtLeast(1).toFloat()
                val h = previewSize.height.coerceAtLeast(1).toFloat()
                latestOnPanDelta(dragAmount.x / w, dragAmount.y / h)
              }
            }
          } else {
            Modifier
          },
        )
        .testTag("settings_editor_bg_preview"),
  ) {
    EditorBackgroundLayer(
      settings = settings,
      modifier = Modifier.fillMaxSize(),
    )

    Row(modifier = Modifier.fillMaxSize()) {
      if (settings.showLineNumbers) {
        Column(
          modifier =
            Modifier
              .background(if (hasActiveBg) palette.editorGutterBg.copy(alpha = 0.62f) else palette.editorGutterBg)
              .padding(horizontal = 8.dp, vertical = 10.dp),
          horizontalAlignment = Alignment.End,
        ) {
          for (ln in 1..7) {
            Text(
              text = "$ln",
              fontFamily = JetBrainsMono,
              fontSize = settings.fontSizeSp.coerceIn(10, 15).sp,
              lineHeight = (settings.fontSizeSp.coerceIn(10, 15) * 1.48f).sp,
              color = palette.textMuted,
            )
          }
        }
      }

      Box(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 10.dp),
      ) {
        Text(
          text = highlighted,
          style =
            TextStyle(
              fontFamily = JetBrainsMono,
              fontSize = settings.fontSizeSp.coerceIn(10, 15).sp,
              lineHeight = (settings.fontSizeSp.coerceIn(10, 15) * 1.48f).sp,
            ),
          maxLines = 7,
          overflow = TextOverflow.Clip,
        )
      }
    }

    if (canPan) {
      Surface(
        color = palette.chromeSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.65f)),
        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
      ) {
        Text(
          text = String.format(Locale.US, "%.2f×", settings.editorBgZoom),
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          fontWeight = FontWeight.SemiBold,
          color = palette.activePillFg,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun QuickPresetChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(6.dp),
    color = if (selected) palette.activePillBg else palette.chromeSurface,
    border =
      BorderStroke(
        0.75.dp,
        if (selected) palette.activePillFg.copy(alpha = 0.4f) else palette.border.copy(alpha = 0.45f),
      ),
  ) {
    Text(
      text = label,
      fontFamily = JetBrainsMono,
      fontSize = 10.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color = if (selected) palette.activePillFg else palette.textSecondary,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
    )
  }
}
