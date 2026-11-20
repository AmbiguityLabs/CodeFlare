package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.security.EditorSettings

@Composable
fun SettingsCompletionPane(
  settings: EditorSettings,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().testTag("settings_completion_section"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SettingsCard(title = "Code Completion") {
      SettingsToggleRow(
        title = "Auto-show suggestions",
        description = "Pop suggestions up automatically while typing",
        checked = settings.autoPopupCompletion,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_completion_auto_popup"),
        onCheckedChange = { enabled ->
          onUpdateSettings { it.copy(autoPopupCompletion = enabled) }
        },
      )

      SettingsDivider()

      AdvancedGroup {
        SettingsSliderRow(
          title = "Auto-popup delay",
          description = "Delay after a keystroke before suggestions appear",
          value = settings.completionDelayMs,
          min = EditorSettings.MIN_COMPLETION_DELAY_MS,
          max = EditorSettings.MAX_COMPLETION_DELAY_MS,
          step = 10,
          unit = "ms",
          modifier = Modifier.testTag("settings_completion_delay"),
          onChange = { delayMs ->
            onUpdateSettings { it.copy(completionDelayMs = delayMs) }
          },
        )

        SettingsDivider()

        SettingsSliderRow(
          title = "Maximum suggestions",
          description = "Cap on items shown in the completion bar",
          value = settings.completionMaxItems,
          min = EditorSettings.MIN_COMPLETION_MAX_ITEMS,
          max = EditorSettings.MAX_COMPLETION_MAX_ITEMS,
          step = 10,
          unit = null,
          modifier = Modifier.testTag("settings_completion_max_items"),
          onChange = { maxItems ->
            onUpdateSettings { it.copy(completionMaxItems = maxItems) }
          },
        )
      }
    }
  }
}

@Composable
fun SettingsAnalysisPane(
  settings: EditorSettings,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().testTag("settings_analysis_section"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SettingsCard(title = "Analysis & Diagnostics") {
      SettingsToggleRow(
        title = "Analyze on the fly",
        description = "Run Workers syntax and type diagnostics as you type",
        checked = settings.analyzeOnTheFly,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_analysis_on_the_fly"),
        onCheckedChange = { enabled ->
          onUpdateSettings { it.copy(analyzeOnTheFly = enabled) }
        },
      )

      SettingsDivider()

      AdvancedGroup {
        SettingsSliderRow(
          title = "Reparse delay",
          description = "Quiet period after typing before diagnostics re-run",
          value = settings.reparseDelayMs,
          min = EditorSettings.MIN_REPARSE_DELAY_MS,
          max = EditorSettings.MAX_REPARSE_DELAY_MS,
          step = 50,
          unit = "ms",
          modifier = Modifier.testTag("settings_analysis_reparse_delay"),
          onChange = { delayMs ->
            onUpdateSettings { it.copy(reparseDelayMs = delayMs) }
          },
        )
      }
    }
  }
}
