package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.FormatIndentIncrease
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.WorkerLanguage
import com.example.data.security.EditorSettings
import com.example.editor.SyntaxHighlighter
import com.example.ui.theme.EmeraldLive
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
      val highlightedLines =
        remember(sampleLines, palette) {
          sampleLines.map { line ->
            SyntaxHighlighter.highlight(
              code = line,
              language = WorkerLanguage.TYPESCRIPT,
              palette = palette,
            )
          }
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
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (settings.stickyHeaders) {
                  Text(
                    text = "STICKY",
                    fontFamily = JetBrainsMono,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.syntaxFunction,
                  )
                }
                if (settings.wordWrap) {
                  Text(
                    text = "WRAP",
                    fontFamily = JetBrainsMono,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.activePillFg,
                  )
                }
              }
            }
            SettingsHairlineDivider()
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .then(if (!settings.wordWrap) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                  .padding(vertical = 8.dp),
            ) {
              val guideColor = palette.border.copy(alpha = 0.5f)
              highlightedLines.forEachIndexed { idx, annotatedLine ->
                val isModifiedPreview = idx == 2
                val isFoldHeader = idx == 0 || idx == 1
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .background(if (idx == 2) palette.editorActiveLineBg else palette.editorBg)
                      .padding(horizontal = 10.dp, vertical = 1.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  if (settings.showLineNumbers) {
                    Text(
                      text = "${idx + 1}",
                      fontFamily = JetBrainsMono,
                      fontSize = (settings.fontSizeSp - 1).coerceAtLeast(9).sp,
                      lineHeight = (settings.fontSizeSp + 6).sp,
                      color = if (idx == 2) palette.activePillFg else palette.textMuted.copy(alpha = 0.6f),
                      modifier = Modifier.width(18.dp),
                    )
                    if (settings.codeFolding) {
                      Text(
                        text = if (isFoldHeader) "▾" else " ",
                        fontFamily = JetBrainsMono,
                        fontSize = 10.sp,
                        color = palette.textMuted.copy(alpha = 0.7f),
                        modifier = Modifier.width(12.dp),
                      )
                    }
                    if (settings.gutterDiffMarkers) {
                      Box(
                        modifier =
                          Modifier
                            .padding(end = 6.dp)
                            .width(2.5.dp)
                            .height((settings.fontSizeSp + 4).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isModifiedPreview) EmeraldLive else palette.editorBg),
                      )
                    } else {
                      Spacer(Modifier.width(6.dp))
                    }
                  }
                  Box(
                    modifier =
                      Modifier.drawBehind {
                        if (settings.indentGuides && idx in 1..3) {
                          drawLine(
                            color = guideColor,
                            start = Offset(1f, 0f),
                            end = Offset(1f, size.height),
                            strokeWidth = 1.dp.toPx(),
                          )
                          if (idx == 2) {
                            val secondX = (settings.tabSize * (settings.fontSizeSp * 0.6f)).dp.toPx()
                            drawLine(
                              color = guideColor,
                              start = Offset(secondX, 0f),
                              end = Offset(secondX, size.height),
                              strokeWidth = 1.dp.toPx(),
                            )
                          }
                        }
                      },
                  ) {
                    Text(
                      text = annotatedLine,
                      fontFamily = JetBrainsMono,
                      fontSize = settings.fontSizeSp.sp,
                      lineHeight = (settings.fontSizeSp + 6).sp,
                      softWrap = settings.wordWrap,
                      color = palette.textPrimary,
                    )
                  }
                }
              }
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

    SettingsGroup(label = "Editor") {
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
        title = "Code Folding",
        leadingIcon = Icons.Outlined.UnfoldLess,
        checked = settings.codeFolding,
        onCheckedChange = { fold -> onUpdateSettings { it.copy(codeFolding = fold) } },
      )
      SettingsHairlineDivider(startIndent = 54.dp)
      SettingsToggleRow(
        title = "Sticky Headers",
        leadingIcon = Icons.Outlined.PushPin,
        checked = settings.stickyHeaders,
        onCheckedChange = { sticky -> onUpdateSettings { it.copy(stickyHeaders = sticky) } },
      )
      SettingsHairlineDivider(startIndent = 54.dp)
      SettingsToggleRow(
        title = "Indent Guides",
        leadingIcon = Icons.Outlined.FormatIndentIncrease,
        checked = settings.indentGuides,
        onCheckedChange = { guides -> onUpdateSettings { it.copy(indentGuides = guides) } },
      )
      SettingsHairlineDivider(startIndent = 54.dp)
      SettingsToggleRow(
        title = "Gutter Diff Markers",
        leadingIcon = Icons.Outlined.Difference,
        checked = settings.gutterDiffMarkers,
        onCheckedChange = { diff -> onUpdateSettings { it.copy(gutterDiffMarkers = diff) } },
      )
      SettingsHairlineDivider(startIndent = 54.dp)
      SettingsToggleRow(
        title = "Auto-Close Brackets",
        leadingIcon = Icons.Outlined.DataObject,
        checked = settings.autoCloseBrackets,
        onCheckedChange = { auto -> onUpdateSettings { it.copy(autoCloseBrackets = auto) } },
      )
      SettingsHairlineDivider(startIndent = 54.dp)
      SettingsToggleRow(
        title = "Keyboard Suggestions",
        leadingIcon = Icons.Outlined.Keyboard,
        checked = settings.softKeyboardSuggestions,
        onCheckedChange = { sug -> onUpdateSettings { it.copy(softKeyboardSuggestions = sug) } },
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
