package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.WorkerLanguage
import com.example.data.security.EditorSettings
import com.example.editor.SyntaxHighlighter
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette

@Composable
fun SettingsEditorPane(
  settings: EditorSettings,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onClearAutoSavedDrafts: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current

  Column(
    modifier = modifier.fillMaxWidth().testTag("settings_editor_section"),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SettingsGroup(
      label = "Typography",
      trailingValue = "${settings.fontSizeSp}sp · ${settings.tabSize}sp",
      contentPadding = 14.dp,
    ) {
      val sampleLines =
        remember(settings.tabSize) {
          val indent = " ".repeat(settings.tabSize)
          listOf(
            "export default {",
            "${indent}async fetch(req: Request): Promise<Response> {",
            "${indent}${indent}return Response.json({ ok: true, edge: \"cf\" });",
            "${indent}}",
            "};",
          )
        }
      val highlightedCode =
        remember(sampleLines, palette) {
          SyntaxHighlighter.highlight(
            code = sampleLines.joinToString("\n"),
            language = WorkerLanguage.TYPESCRIPT,
            palette = palette,
          )
        }

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
          color = palette.editorBg,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(palette.tabBarBg)
                  .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "worker.ts",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textSecondary,
              )
              if (settings.wordWrap) {
                Text(
                  text = "WRAP",
                  fontFamily = JetBrainsMono,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = palette.activePillFg,
                )
              }
            }
            SettingsHairlineDivider()
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .then(if (!settings.wordWrap) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                  .padding(horizontal = 10.dp, vertical = 9.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (settings.showLineNumbers) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                  sampleLines.indices.forEach { idx ->
                    Text(
                      text = "${idx + 1}",
                      fontFamily = JetBrainsMono,
                      fontSize = settings.fontSizeSp.sp,
                      lineHeight = (settings.fontSizeSp + 6).sp,
                      color = palette.textMuted.copy(alpha = 0.6f),
                    )
                  }
                }
              }
              Text(
                text = highlightedCode,
                fontFamily = JetBrainsMono,
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp + 6).sp,
                softWrap = settings.wordWrap,
                color = palette.textPrimary,
              )
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Font Size", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
            Text(
              text = "${settings.fontSizeSp}sp",
              fontFamily = JetBrainsMono,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.activePillFg,
            )
          }
          Slider(
            value = settings.fontSizeSp.toFloat(),
            onValueChange = { value -> onUpdateSettings { it.copy(fontSizeSp = value.toInt()) } },
            valueRange = 10f..18f,
            steps = 7,
          )
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(11, 13, 15, 17).forEach { sp ->
              SettingsChoiceChip(
                label = "${sp}sp",
                selected = settings.fontSizeSp == sp,
                onClick = { onUpdateSettings { it.copy(fontSizeSp = sp) } },
                monospace = true,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        SettingsHairlineDivider()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Tab Size", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 4).forEach { spaces ->
              val selected = settings.tabSize == spaces
              SettingsChoiceChip(
                label = "$spaces Spaces",
                selected = selected,
                leadingIcon = if (selected) Icons.Outlined.Check else null,
                onClick = { onUpdateSettings { it.copy(tabSize = spaces) } },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }

    SettingsGroup(label = "Behavior") {
      SettingsToggleRow(
        title = "Word Wrap",
        leadingIcon = Icons.AutoMirrored.Outlined.WrapText,
        checked = settings.wordWrap,
        onCheckedChange = { wrap -> onUpdateSettings { it.copy(wordWrap = wrap) } },
      )
      SettingsHairlineDivider(startIndent = 54.dp)
      SettingsToggleRow(
        title = "Line Numbers",
        leadingIcon = Icons.Outlined.FormatListNumbered,
        checked = settings.showLineNumbers,
        onCheckedChange = { ln -> onUpdateSettings { it.copy(showLineNumbers = ln) } },
      )
      SettingsHairlineDivider(startIndent = 54.dp)
      SettingsToggleRow(
        title = "Auto-Close Brackets",
        leadingIcon = Icons.Outlined.DataObject,
        checked = settings.autoCloseBrackets,
        onCheckedChange = { auto -> onUpdateSettings { it.copy(autoCloseBrackets = auto) } },
      )
    }

    SettingsGroup(
      label = "Auto-Save",
      trailingValue = if (settings.autoSaveEnabled) "${settings.autoSaveIntervalSeconds}s" else "Off",
    ) {
      SettingsToggleRow(
        title = "Auto-Save",
        leadingIcon = Icons.Outlined.Save,
        checked = settings.autoSaveEnabled,
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(autoSaveEnabled = enabled) } },
      )

      AnimatedVisibility(
        visible = settings.autoSaveEnabled,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        Column {
          SettingsHairlineDivider()
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(3, 5, 15, 30).forEach { seconds ->
              SettingsChoiceChip(
                label = "${seconds}s",
                selected = settings.autoSaveIntervalSeconds == seconds,
                onClick = { onUpdateSettings { it.copy(autoSaveIntervalSeconds = seconds) } },
                monospace = true,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }

      if (onClearAutoSavedDrafts != null) {
        SettingsHairlineDivider()
        OutlinedButton(
          onClick = onClearAutoSavedDrafts,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.7f)),
          modifier = Modifier.fillMaxWidth().padding(12.dp).height(42.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteSweep,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(7.dp))
          Text("Clear Drafts", fontSize = 12.5.sp)
        }
      }
    }
  }
}
