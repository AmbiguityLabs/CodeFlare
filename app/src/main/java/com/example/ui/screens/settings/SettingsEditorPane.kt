package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.FormatIndentIncrease
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.WorkerLanguage
import com.example.data.security.DefaultEditorDragPolicy
import com.example.data.security.EditorSettings
import com.example.data.security.IconLabelBehavior
import com.example.data.security.SelectionDragConfig
import com.example.data.security.SmartSelectionHoldMode
import com.example.editor.SyntaxHighlighter
import com.example.editor.selection.SelectionLadderState
import com.example.editor.selection.SmartSelectionInitialDepth
import com.example.editor.selection.SmartTextDomain
import com.example.editor.selection.SmartTextSelectionEngine
import com.example.ui.components.IconRevealButton
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.UiStyleVersion

@Composable
fun SettingsEditorPane(
  settings: EditorSettings,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onClearAutoSavedDrafts: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED

  val enabledEditorFlags =
    listOf(
      settings.wordWrap,
      settings.showLineNumbers,
      settings.codeFolding,
      settings.stickyHeaders,
      settings.indentGuides,
      settings.gutterDiffMarkers,
      settings.autoCloseBrackets,
      settings.softKeyboardSuggestions,
    ).count { it }

  Column(
    modifier = modifier.fillMaxWidth().testTag("settings_editor_section"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SettingsFoldableSection(
      title = "Typography",
      icon = Icons.Outlined.TextFields,
      summary = "${settings.fontSizeSp}sp · ${settings.tabSize}sp",
      initiallyExpanded = false,
      contentPadding = 14.dp,
    ) {
      val sampleLines =
        remember(settings.tabSize) {
          val indent = " ".repeat(settings.tabSize)
          listOf(
            "export default {",
            "${indent}async fetch(req) {",
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
              language = WorkerLanguage.JAVASCRIPT,
              palette = palette,
            )
          }
        }

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
          color = palette.editorBg,
          shape = RoundedCornerShape(if (isV2) 16.dp else 10.dp),
          border = if (isV2) null else BorderStroke(0.75.dp, palette.border.copy(alpha = 0.6f)),
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
                text = "worker.js",
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

    SettingsFoldableSection(
      title = "Editor",
      icon = Icons.Outlined.Tune,
      summary = "$enabledEditorFlags/8",
      initiallyExpanded = false,
    ) {
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
      SettingsHairlineDivider(startIndent = 54.dp)
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Toolbar & Icon Labels",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textPrimary,
          )
          Text(
            text = settings.iconLabelBehavior.shortLabel,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.activePillFg,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          IconLabelBehavior.entries.forEach { behavior ->
            SettingsChoiceChip(
              label = behavior.shortLabel,
              selected = settings.iconLabelBehavior == behavior,
              onClick = { onUpdateSettings { it.copy(iconLabelBehavior = behavior) } },
              modifier = Modifier.weight(1f).testTag("settings_editor_label_behavior_${behavior.name.lowercase()}"),
            )
          }
        }
      }
    }

    SettingsFoldableSection(
      title = "Auto-Save",
      icon = Icons.Outlined.Save,
      summary = if (settings.autoSaveEnabled) "${settings.autoSaveIntervalSeconds}s" else "Off",
      initiallyExpanded = false,
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
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Drafts", fontSize = 14.sp, color = palette.textPrimary)
          IconRevealButton(
            icon = Icons.Outlined.DeleteSweep,
            revealedLabel = "Clear Drafts",
            onClick = onClearAutoSavedDrafts,
            height = 36.dp,
            tag = "settings_clear_drafts_button",
          )
        }
      }
    }

    val selConfig = settings.selectionDragConfig.normalized()
    SettingsFoldableSection(
      title = "Selection & Drag",
      icon = Icons.Outlined.TouchApp,
      summary = "${selConfig.holdMode.shortLabel} · ${selConfig.enabledOverlayCount}/7",
      initiallyExpanded = false,
      contentPadding = 14.dp,
      modifier = Modifier.testTag("settings_selection_drag_section"),
    ) {
      val previewCode = "const url = \"https://edge.dev/v1\";"
      val previewAnchorOffset = 22 // inside https://edge.dev/v1
      val previewLadder =
        remember(selConfig.initialDepth, selConfig.holdMode) {
          SmartTextSelectionEngine.resolveAt(
            text = previewCode,
            offset = previewAnchorOffset,
            domain = SmartTextDomain.CODE_JS_TS,
            initialDepth =
              if (selConfig.holdMode == SmartSelectionHoldMode.CLASSIC_WORD) SmartSelectionInitialDepth.COMPACT
              else selConfig.initialDepth,
          )
        }
      var previewStepOffset by remember(selConfig.initialDepth, selConfig.holdMode) { mutableIntStateOf(0) }
      val effectiveIndex =
        if (selConfig.holdMode == SmartSelectionHoldMode.CLASSIC_WORD) 0
        else (previewLadder.initialIndex + previewStepOffset).coerceIn(0, (previewLadder.scopes.lastIndex).coerceAtLeast(0))
      val previewState = SelectionLadderState(previewLadder, effectiveIndex)
      val previewScope = previewState.currentScope
      val previewHighlightStart = if (selConfig.holdMode == SmartSelectionHoldMode.CLASSIC_WORD) 21 else (previewScope?.start ?: 21)
      val previewHighlightEnd = if (selConfig.holdMode == SmartSelectionHoldMode.CLASSIC_WORD) 25 else (previewScope?.end ?: 25)
      val previewLabel = if (selConfig.holdMode == SmartSelectionHoldMode.CLASSIC_WORD) "Word" else (previewScope?.label ?: "Token")

      val baseAnnotated =
        remember(previewCode, palette) {
          SyntaxHighlighter.highlight(previewCode, WorkerLanguage.JAVASCRIPT, palette)
        }
      val highlightedPreview =
        remember(baseAnnotated, previewHighlightStart, previewHighlightEnd, palette.activePillBg, palette.activePillFg) {
          buildAnnotatedString {
            append(baseAnnotated)
            addStyle(
              style =
                SpanStyle(
                  background = palette.activePillBg.copy(alpha = 0.8f),
                  color = palette.activePillFg,
                  fontWeight = FontWeight.Bold,
                ),
              start = previewHighlightStart.coerceIn(0, previewCode.length),
              end = previewHighlightEnd.coerceIn(0, previewCode.length),
            )
          }
        }

      fun updateSelectionConfig(block: (SelectionDragConfig) -> SelectionDragConfig) {
        onUpdateSettings { cur ->
          cur.copy(selectionDragConfig = block(cur.selectionDragConfig).normalized())
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Interactive Live Preview Card
        Surface(
          color = palette.editorBg,
          shape = RoundedCornerShape(if (isV2) 16.dp else 10.dp),
          border = if (isV2) null else BorderStroke(0.75.dp, palette.border.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth().testTag("settings_selection_drag_preview"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "LIVE SELECTION PREVIEW",
                fontFamily = JetBrainsMono,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                color = palette.textMuted,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selConfig.showDropTargetLoupe) {
                  Text(
                    text = "1.5x LOUPE",
                    fontFamily = JetBrainsMono,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldLive,
                  )
                }
                Text(
                  text = "${selConfig.holdDelayMs}ms",
                  fontFamily = JetBrainsMono,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
            }

            // Simulated minimal opaque icon-first Selection Toolbar reacting to switches
            val previewBehavior = settings.iconLabelBehavior
            Surface(
              color = palette.chromeSurface,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.75f)),
            ) {
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                if (selConfig.showDragGrip) {
                  Row(
                    modifier =
                      Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.activePillBg.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                  ) {
                    Icon(Icons.Outlined.DragIndicator, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(13.dp))
                    if (previewBehavior == IconLabelBehavior.ALWAYS_SHOW) {
                      Text("Drag", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = palette.activePillFg)
                    }
                  }
                }
                if (selConfig.showScopeBadge) {
                  Text(
                    text = previewLabel,
                    fontFamily = JetBrainsMono,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.activePillFg,
                    modifier =
                      Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.editorBg)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                  )
                }
                if (selConfig.showExpandShrink && selConfig.holdMode != SmartSelectionHoldMode.CLASSIC_WORD) {
                  Surface(
                    onClick = { if (previewState.canShrink) previewStepOffset-- },
                    enabled = previewState.canShrink,
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                  ) {
                    Icon(
                      Icons.Outlined.Remove,
                      contentDescription = "Shrink preview scope",
                      tint = if (previewState.canShrink) palette.textPrimary else palette.textMuted.copy(alpha = 0.35f),
                      modifier = Modifier.padding(3.dp).size(14.dp),
                    )
                  }
                  Surface(
                    onClick = { if (previewState.canExpand) previewStepOffset++ },
                    enabled = previewState.canExpand,
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                  ) {
                    Icon(
                      Icons.Outlined.Add,
                      contentDescription = "Expand preview scope",
                      tint = if (previewState.canExpand) palette.activePillFg else palette.textMuted.copy(alpha = 0.35f),
                      modifier = Modifier.padding(3.dp).size(14.dp),
                    )
                  }
                }
                if (selConfig.showModeToggle) {
                  val isMove = selConfig.defaultEditorDragMode != DefaultEditorDragPolicy.ALWAYS_COPY
                  val modeTint = if (isMove) palette.ctaBg else EmeraldLive
                  Row(
                    modifier =
                      Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(modeTint.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Icon(
                      imageVector = if (isMove) Icons.Outlined.OpenWith else Icons.Outlined.ContentCopy,
                      contentDescription = null,
                      tint = modeTint,
                      modifier = Modifier.size(12.dp),
                    )
                    if (previewBehavior == IconLabelBehavior.ALWAYS_SHOW) {
                      Text(
                        text = if (isMove) "Move" else "Copy",
                        fontFamily = JetBrainsMono,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = modeTint,
                      )
                    }
                  }
                }
                if (selConfig.showQuickActions) {
                  Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.padding(horizontal = 3.dp).size(13.dp))
                  Icon(Icons.Outlined.ContentCut, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.padding(horizontal = 3.dp).size(13.dp))
                  Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.padding(horizontal = 3.dp).size(13.dp))
                }
                if (selConfig.showCodeActions) {
                  Text("//", fontFamily = JetBrainsMono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, modifier = Modifier.padding(horizontal = 3.dp))
                }
                if (selConfig.enabledOverlayCount == 0) {
                  Text("Overlay hidden", fontFamily = JetBrainsMono, fontSize = 9.5.sp, color = palette.textMuted)
                }
              }
            }

            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(palette.editorActiveLineBg)
                  .horizontalScroll(rememberScrollState())
                  .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
              Text(
                text = highlightedPreview,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                color = palette.textPrimary,
                maxLines = 1,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  if (selConfig.immediateSlideToDrag) "Hold -> slide launches drag immediately"
                  else "Hold selects · use Drag grip or re-hold to drag",
                fontSize = 11.sp,
                color = palette.textSecondary,
              )
              if (selConfig.allowCrossAppDrag) {
                Text(
                  text = "GLOBAL",
                  fontFamily = JetBrainsMono,
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = palette.activePillFg,
                )
              }
            }
          }
        }

        // Hold Mode
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Hold Behavior", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmartSelectionHoldMode.entries.forEach { mode ->
              val selected = selConfig.holdMode == mode
              SettingsChoiceChip(
                label = mode.shortLabel,
                selected = selected,
                onClick = { updateSelectionConfig { it.copy(holdMode = mode) } },
                modifier = Modifier.weight(1f).testTag("sel_hold_mode_${mode.name}"),
              )
            }
          }
        }

        // Initial Depth
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Initial Scope Depth", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
              SmartSelectionInitialDepth.COMPACT to "Compact",
              SmartSelectionInitialDepth.SMART_AUTO to "Smart Auto",
              SmartSelectionInitialDepth.BROAD to "Broad",
            ).forEach { (depth, label) ->
              SettingsChoiceChip(
                label = label,
                selected = selConfig.initialDepth == depth,
                onClick = { updateSelectionConfig { it.copy(initialDepth = depth) } },
                modifier = Modifier.weight(1f).testTag("sel_depth_${depth.name}"),
              )
            }
          }
        }

        // Default Editor Drag Policy
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Default Editor Drag Mode", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DefaultEditorDragPolicy.entries.forEach { policy ->
              SettingsChoiceChip(
                label = policy.shortLabel,
                selected = selConfig.defaultEditorDragMode == policy,
                onClick = { updateSelectionConfig { it.copy(defaultEditorDragMode = policy) } },
                modifier = Modifier.weight(1f).testTag("sel_drag_policy_${policy.name}"),
              )
            }
          }
        }

        // Hold Trigger Delay
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Hold Trigger Delay", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
            Text(
              text = "${selConfig.holdDelayMs}ms",
              fontFamily = JetBrainsMono,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.activePillFg,
            )
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(240, 320, 450).forEach { ms ->
              SettingsChoiceChip(
                label = "${ms}ms",
                selected = selConfig.holdDelayMs == ms,
                onClick = { updateSelectionConfig { it.copy(holdDelayMs = ms) } },
                monospace = true,
                modifier = Modifier.weight(1f).testTag("sel_hold_delay_$ms"),
              )
            }
          }
        }

        SettingsHairlineDivider()

        // Drag Behavior Switches
        SettingsToggleRow(
          title = "Immediate Slide-to-Drag",
          leadingIcon = Icons.Outlined.Swipe,
          checked = selConfig.immediateSlideToDrag,
          onCheckedChange = { enabled -> updateSelectionConfig { it.copy(immediateSlideToDrag = enabled) } },
          horizontalPadding = 2.dp,
          modifier = Modifier.testTag("sel_toggle_immediate_slide"),
        )
        SettingsHairlineDivider()
        SettingsToggleRow(
          title = "Cross-App Global Drag",
          leadingIcon = Icons.Outlined.OpenWith,
          checked = selConfig.allowCrossAppDrag,
          onCheckedChange = { enabled -> updateSelectionConfig { it.copy(allowCrossAppDrag = enabled) } },
          horizontalPadding = 2.dp,
          modifier = Modifier.testTag("sel_toggle_cross_app"),
        )

        SettingsHairlineDivider()

        Text(
          text = "HOLD OVERLAY ELEMENTS (${selConfig.enabledOverlayCount}/7)",
          fontFamily = JetBrainsMono,
          fontSize = 9.5.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.7.sp,
          color = palette.textMuted,
        )

        SettingsToggleRow(
          title = "Scope Badge",
          leadingIcon = Icons.Outlined.AutoAwesome,
          checked = selConfig.showScopeBadge,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showScopeBadge = v) } },
          horizontalPadding = 2.dp,
          modifier = Modifier.testTag("sel_toggle_scope_badge"),
        )
        SettingsHairlineDivider()
        SettingsToggleRow(
          title = "Expand / Shrink Buttons",
          leadingIcon = Icons.Outlined.Tune,
          checked = selConfig.showExpandShrink,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showExpandShrink = v) } },
          horizontalPadding = 2.dp,
          modifier = Modifier.testTag("sel_toggle_expand_shrink"),
        )
        SettingsHairlineDivider()
        SettingsToggleRow(
          title = "Drag Grip",
          leadingIcon = Icons.Outlined.DragIndicator,
          checked = selConfig.showDragGrip,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showDragGrip = v) } },
          horizontalPadding = 2.dp,
          modifier = Modifier.testTag("sel_toggle_drag_grip"),
        )
        SettingsHairlineDivider()
        SettingsToggleRow(
          title = "Move / Copy Switch",
          leadingIcon = Icons.Outlined.ContentCopy,
          checked = selConfig.showModeToggle,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showModeToggle = v) } },
          horizontalPadding = 2.dp,
          modifier = Modifier.testTag("sel_toggle_mode_switch"),
        )
        SettingsHairlineDivider()
        SettingsToggleRow(
          title = "Drop Target Loupe (1.5x)",
          leadingIcon = Icons.Outlined.ZoomIn,
          checked = selConfig.showDropTargetLoupe,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showDropTargetLoupe = v) } },
          horizontalPadding = 2.dp,
          modifier = Modifier.testTag("sel_toggle_drop_loupe"),
        )
        SettingsHairlineDivider()
        SettingsToggleRow(
          title = "Quick Actions (Copy, Cut, Paste, All)",
          leadingIcon = Icons.Outlined.Timer,
          checked = selConfig.showQuickActions,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showQuickActions = v) } },
          horizontalPadding = 2.dp,
          modifier = Modifier.testTag("sel_toggle_quick_actions"),
        )
        SettingsHairlineDivider()
        SettingsToggleRow(
          title = "Code Actions (//, Rename)",
          leadingIcon = Icons.Outlined.Code,
          checked = selConfig.showCodeActions,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showCodeActions = v) } },
          horizontalPadding = 2.dp,
          modifier = Modifier.testTag("sel_toggle_code_actions"),
        )

        if (selConfig != SelectionDragConfig.DEFAULT) {
          SettingsHairlineDivider()
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            OutlinedButton(
              onClick = { updateSelectionConfig { SelectionDragConfig.DEFAULT } },
              modifier = Modifier.testTag("sel_reset_defaults"),
            ) {
              Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Reset Defaults", fontSize = 12.sp)
            }
          }
        }
      }
    }
  }
}
