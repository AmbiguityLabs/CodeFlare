package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.FloatingBarStyleConfig
import com.example.data.security.PopupMenuStyleConfig
import com.example.ui.screens.settings.SettingsFoldableSection
import com.example.ui.screens.settings.SettingsToggleRow
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.LocalPopupMenuStyle
import com.example.ui.theme.UiStyleVersion
import kotlin.math.roundToInt

@Composable
fun FloatingBarCustomizerSection(
  title: String,
  tagPrefix: String,
  config: FloatingBarStyleConfig,
  defaultConfig: FloatingBarStyleConfig,
  previewItems: List<Triple<String, ImageVector, String?>>,
  onChange: ((FloatingBarStyleConfig) -> FloatingBarStyleConfig) -> Unit,
  onReset: () -> Unit,
  initiallyExpanded: Boolean = true,
) {
  val palette = LocalIdePalette.current
  val cfg = config.normalized()
  var selectedPreviewIndex by remember { mutableIntStateOf(0) }
  val summaryText = "${cfg.heightDp}dp · ${cfg.widthPercent}% · ${(cfg.opacity * 100f).roundToInt()}%"

  SettingsFoldableSection(
    title = title,
    icon = Icons.Outlined.Tune,
    summary = summaryText,
    initiallyExpanded = initiallyExpanded,
    contentPadding = 12.dp,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height((cfg.heightDp + cfg.bottomMarginDp + 56).coerceAtLeast(126).dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
              Brush.linearGradient(
                colors =
                  if (palette.isDark) {
                    listOf(Color(0xFF151923), Color(0xFF282034), Color(0xFF13222B))
                  } else {
                    listOf(Color(0xFFDCE6F4), Color(0xFFEBDFF2), Color(0xFFD8EBE8))
                  },
              ),
            )
            .border(0.75.dp, palette.border.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .testTag("${tagPrefix}_live_preview"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          repeat(3) { idx ->
            Box(
              modifier =
                Modifier
                  .weight(1f)
                  .height(44.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(
                    when (idx) {
                      0 -> palette.activePillFg.copy(alpha = 0.24f)
                      1 -> Color(0xFF38BDF8).copy(alpha = 0.22f)
                      else -> Color(0xFFF59E0B).copy(alpha = 0.22f)
                    },
                  ),
            )
          }
        }

        val dockPreviewItems =
          previewItems.mapIndexed { idx, (label, icon, badge) ->
            EdgeBottomDockItem(
              label = label,
              icon = icon,
              selected = idx == selectedPreviewIndex,
              testTag = "${tagPrefix}_preview_item_$idx",
              badgeText = badge,
              onClick = { selectedPreviewIndex = idx },
            )
          }

        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
          EdgeBottomDock(
            items = dockPreviewItems,
            barStyle = cfg,
          )
        }
      }

      Surface(
        color = palette.editorBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          CustomizerIntSlider(
            label = "Height",
            value = cfg.heightDp,
            range = 44..78,
            unit = "dp",
            testTag = "${tagPrefix}_height_slider",
            onValueChange = { v -> onChange { it.copy(heightDp = v).normalized() } },
          )
          CustomizerIntSlider(
            label = "Width",
            value = cfg.widthPercent,
            range = 52..100,
            unit = "%",
            testTag = "${tagPrefix}_width_slider",
            onValueChange = { v -> onChange { it.copy(widthPercent = v).normalized() } },
          )
          CustomizerIntSlider(
            label = "Icon Distance",
            value = cfg.iconSpacingDp,
            range = 2..40,
            unit = "dp",
            testTag = "${tagPrefix}_spacing_slider",
            onValueChange = { v -> onChange { it.copy(iconSpacingDp = v).normalized() } },
          )
          CustomizerIntSlider(
            label = "Icon Size",
            value = cfg.iconSizeDp,
            range = 16..28,
            unit = "dp",
            testTag = "${tagPrefix}_icon_size_slider",
            onValueChange = { v -> onChange { it.copy(iconSizeDp = v).normalized() } },
          )
          CustomizerPercentSlider(
            label = "Opacity",
            value = cfg.opacity,
            range = 0.15f..1.0f,
            testTag = "${tagPrefix}_opacity_slider",
            onValueChange = { v -> onChange { it.copy(opacity = v).normalized() } },
          )
          CustomizerPercentSlider(
            label = "Glass Rim",
            value = cfg.borderOpacity,
            range = 0.0f..0.75f,
            testTag = "${tagPrefix}_border_slider",
            onValueChange = { v -> onChange { it.copy(borderOpacity = v).normalized() } },
          )
          CustomizerPercentSlider(
            label = "Active Pill",
            value = cfg.activePillOpacity,
            range = 0.08f..0.60f,
            testTag = "${tagPrefix}_active_pill_slider",
            onValueChange = { v -> onChange { it.copy(activePillOpacity = v).normalized() } },
          )
          CustomizerIntSlider(
            label = "Corner Radius",
            value = cfg.cornerRadiusDp,
            range = 10..40,
            unit = "dp",
            testTag = "${tagPrefix}_radius_slider",
            onValueChange = { v -> onChange { it.copy(cornerRadiusDp = v).normalized() } },
          )
          CustomizerIntSlider(
            label = "Bottom Float",
            value = cfg.bottomMarginDp,
            range = 0..28,
            unit = "dp",
            testTag = "${tagPrefix}_margin_slider",
            onValueChange = { v -> onChange { it.copy(bottomMarginDp = v).normalized() } },
          )

          HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

          SettingsToggleRow(
            title = "Show Labels",
            checked = cfg.showLabels,
            onCheckedChange = { show -> onChange { it.copy(showLabels = show).normalized() } },
            horizontalPadding = 2.dp,
            modifier = Modifier.testTag("${tagPrefix}_labels_toggle"),
          )
          SettingsToggleRow(
            title = "Counter Badges",
            checked = cfg.showBadges,
            onCheckedChange = { show -> onChange { it.copy(showBadges = show).normalized() } },
            horizontalPadding = 2.dp,
            modifier = Modifier.testTag("${tagPrefix}_badges_toggle"),
          )
        }
      }

      if (cfg != defaultConfig.normalized()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(
            onClick = onReset,
            modifier = Modifier.testTag("${tagPrefix}_reset_button"),
          ) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("Reset Bar", fontSize = 12.sp)
          }
        }
      }
    }
  }
}

@Composable
fun PopupMenuCustomizerSection(
  title: String,
  tagPrefix: String,
  config: PopupMenuStyleConfig,
  defaultConfig: PopupMenuStyleConfig,
  previewHeader: String,
  previewRows: List<Triple<String, ImageVector, String?>>,
  onChange: ((PopupMenuStyleConfig) -> PopupMenuStyleConfig) -> Unit,
  onReset: () -> Unit,
  initiallyExpanded: Boolean = false,
) {
  val palette = LocalIdePalette.current
  val cfg = config.normalized()
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  var selectedRowIdx by remember { mutableIntStateOf(0) }
  val summaryText = "${cfg.widthDp}dp · ${cfg.itemHeightDp}dp · ${(cfg.opacity * 100f).roundToInt()}%"

  SettingsFoldableSection(
    title = title,
    icon = Icons.AutoMirrored.Outlined.MenuOpen,
    summary = summaryText,
    initiallyExpanded = initiallyExpanded,
    contentPadding = 12.dp,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      BoxWithConstraints(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
              Brush.linearGradient(
                colors =
                  if (palette.isDark) {
                    listOf(Color(0xFF141821), Color(0xFF231D2E), Color(0xFF121F24))
                  } else {
                    listOf(Color(0xFFDDE7F4), Color(0xFFECE2F4), Color(0xFFDAEBE6))
                  },
              ),
            )
            .border(0.75.dp, palette.border.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 10.dp)
            .testTag("${tagPrefix}_live_preview"),
        contentAlignment = Alignment.Center,
      ) {
        val previewMaxWidth = (maxWidth - 4.dp).coerceAtLeast(164.dp)
        val previewWidth = cfg.widthDp.dp.coerceAtMost(previewMaxWidth)
        val borderColor =
          if (isV2 && palette.isDark) Color.White.copy(alpha = cfg.borderOpacity)
          else palette.glassBorderHighlight.copy(alpha = cfg.borderOpacity)

        CompositionLocalProvider(LocalPopupMenuStyle provides cfg) {
          Surface(
            color = palette.chromeSurface.copy(alpha = cfg.opacity),
            shape = RoundedCornerShape(cfg.cornerRadiusDp.dp),
            shadowElevation = if (cfg.opacity > 0.45f) 8.dp else 3.dp,
            border = if (cfg.borderOpacity > 0.01f) BorderStroke(if (isV2) 0.9.dp else 0.75.dp, borderColor) else null,
            modifier = Modifier.widthIn(max = previewWidth).width(previewWidth),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp),
              verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
              EdgeMenuHeader(
                title = previewHeader,
                badgeText = "${previewRows.size}",
              )
              previewRows.forEachIndexed { idx, (label, icon, badge) ->
                EdgeMenuItem(
                  label = label,
                  icon = icon,
                  badgeText = badge,
                  selected = idx == selectedRowIdx,
                  onClick = { selectedRowIdx = idx },
                )
              }
            }
          }
        }
      }

      Surface(
        color = palette.editorBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          CustomizerIntSlider(
            label = "Width",
            value = cfg.widthDp,
            range = 180..320,
            unit = "dp",
            testTag = "${tagPrefix}_width_slider",
            onValueChange = { v -> onChange { it.copy(widthDp = v).normalized() } },
          )
          CustomizerIntSlider(
            label = "Row Height",
            value = cfg.itemHeightDp,
            range = 32..56,
            unit = "dp",
            testTag = "${tagPrefix}_height_slider",
            onValueChange = { v -> onChange { it.copy(itemHeightDp = v).normalized() } },
          )
          CustomizerIntSlider(
            label = "Icon Distance",
            value = cfg.iconSpacingDp,
            range = 4..20,
            unit = "dp",
            testTag = "${tagPrefix}_spacing_slider",
            onValueChange = { v -> onChange { it.copy(iconSpacingDp = v).normalized() } },
          )
          CustomizerIntSlider(
            label = "Icon Size",
            value = cfg.iconSizeDp,
            range = 13..22,
            unit = "dp",
            testTag = "${tagPrefix}_icon_size_slider",
            onValueChange = { v -> onChange { it.copy(iconSizeDp = v).normalized() } },
          )
          CustomizerPercentSlider(
            label = "Opacity",
            value = cfg.opacity,
            range = 0.35f..1.0f,
            testTag = "${tagPrefix}_opacity_slider",
            onValueChange = { v -> onChange { it.copy(opacity = v).normalized() } },
          )
          CustomizerPercentSlider(
            label = "Glass Rim",
            value = cfg.borderOpacity,
            range = 0.0f..0.75f,
            testTag = "${tagPrefix}_border_slider",
            onValueChange = { v -> onChange { it.copy(borderOpacity = v).normalized() } },
          )
          CustomizerIntSlider(
            label = "Corner Radius",
            value = cfg.cornerRadiusDp,
            range = 8..28,
            unit = "dp",
            testTag = "${tagPrefix}_radius_slider",
            onValueChange = { v -> onChange { it.copy(cornerRadiusDp = v).normalized() } },
          )
        }
      }

      if (cfg != defaultConfig.normalized()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(
            onClick = onReset,
            modifier = Modifier.testTag("${tagPrefix}_reset_button"),
          ) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("Reset Menu", fontSize = 12.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun CustomizerIntSlider(
  label: String,
  value: Int,
  range: IntRange,
  unit: String,
  testTag: String,
  onValueChange: (Int) -> Unit,
) {
  val palette = LocalIdePalette.current
  Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = palette.textPrimary,
      )
      Text(
        text = "$value$unit",
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.activePillFg,
      )
    }
    Slider(
      value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
      onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
      valueRange = range.first.toFloat()..range.last.toFloat(),
      modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
  }
}

@Composable
private fun CustomizerPercentSlider(
  label: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  testTag: String,
  onValueChange: (Float) -> Unit,
) {
  val palette = LocalIdePalette.current
  val pct = (value.coerceIn(range.start, range.endInclusive) * 100f).roundToInt()
  Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = palette.textPrimary,
      )
      Text(
        text = "$pct%",
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.activePillFg,
      )
    }
    Slider(
      value = value.coerceIn(range.start, range.endInclusive),
      onValueChange = { onValueChange(it.coerceIn(range.start, range.endInclusive)) },
      valueRange = range,
      modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
  }
}
