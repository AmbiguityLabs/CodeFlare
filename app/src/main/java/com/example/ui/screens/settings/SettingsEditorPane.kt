package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import com.example.ui.theme.Ca
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
  val scheme = MaterialTheme.colorScheme
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val resolvedFont =
    if (settings.normalizedCodeFont() == EditorSettings.CODE_FONT_MONOSPACE) {
      FontFamily.Monospace
    } else {
      JetBrainsMono
    }
  val resolvedLigatures = if (settings.fontLigatures) null else "liga 0, calt 0"
  val activeCaretStyle = settings.normalizedCaretStyle()

  Column(
    modifier = modifier.fillMaxWidth().testTag("settings_editor_section"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Typography & Caret
    SettingsCard(title = "Typography & Caret") {
      val sampleLines =
        remember(settings.tabSize) {
          val indent = " ".repeat(settings.tabSize)
          listOf(
            "export default {",
            "${indent}const ok = status !== 404 => true;",
            "${indent}return Response.json({ ok });",
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

      Surface(
        color = palette.editorBg,
        shape = RoundedCornerShape(if (isV2) Ca.radius.lg else Ca.radius.control),
        border = if (isV2) null else BorderStroke(0.75.dp, scheme.outlineVariant.copy(alpha = 0.65f)),
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
              fontFamily = resolvedFont,
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium,
              color = palette.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              if (settings.fontLigatures) {
                Text(
                  text = "LIGA",
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
              val isFoldHeader = idx == 0
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
                    fontFamily = resolvedFont,
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
                      if (settings.indentGuides && idx in 1..2) {
                        drawLine(
                          color = guideColor,
                          start = Offset(1f, 0f),
                          end = Offset(1f, size.height),
                          strokeWidth = 1.dp.toPx(),
                        )
                      }
                    },
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = annotatedLine,
                      fontFamily = resolvedFont,
                      fontSize = settings.fontSizeSp.sp,
                      lineHeight = (settings.fontSizeSp + 6).sp,
                      softWrap = settings.wordWrap,
                      color = palette.textPrimary,
                      style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = resolvedLigatures),
                    )
                    if (idx == 2) {
                      PreviewCaretIndicator(
                        caretStyle = activeCaretStyle,
                        caretColor = palette.activePillFg,
                        heightDp = (settings.fontSizeSp + 3).coerceIn(12, 22),
                      )
                    }
                  }
                }
              }
            }
          }
          if (!settings.wordWrap && settings.showHorizontalScrollbar) {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(start = if (settings.showLineNumbers) 36.dp else 10.dp, end = 10.dp, bottom = 5.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .fillMaxWidth(0.42f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(Ca.radius.pill))
                    .background(palette.activePillFg.copy(alpha = 0.45f)),
              )
            }
          }
        }
      }

      SettingsChoiceRow(
        title = "Code font",
        description = "Monospace typeface used in the code editor",
        selected = settings.normalizedCodeFont(),
        options =
          listOf(
            EditorSettings.CODE_FONT_JETBRAINS to "JetBrains Mono",
            EditorSettings.CODE_FONT_MONOSPACE to "System monospace",
          ),
        chipTestTagPrefix = "settings_code_font",
        onSelect = { font -> onUpdateSettings { it.copy(codeFont = font) } },
      )

      SettingsDivider()

      SettingsSliderRow(
        title = "Font size",
        description = "Editor code & gutter text size",
        value = settings.fontSizeSp,
        min = 10,
        max = 20,
        step = 1,
        unit = "sp",
        modifier = Modifier.testTag("settings_editor_font_size"),
        onChange = { sp -> onUpdateSettings { it.copy(fontSizeSp = sp) } },
      )

      SettingsDivider()

      SettingsChoiceRow(
        title = "Caret style",
        description = "Visual cursor style inside the editor",
        selected = activeCaretStyle,
        options =
          listOf(
            EditorSettings.CARET_LINE to "Line",
            EditorSettings.CARET_BLOCK to "Block",
            EditorSettings.CARET_UNDERLINE to "Underline",
            EditorSettings.CARET_CANDLE to "Candle",
          ),
        chipTestTagPrefix = "settings_caret",
        onSelect = { style -> onUpdateSettings { it.copy(caretStyle = style) } },
      )

      SettingsDivider()

      SettingsToggleRow(
        title = "Font ligatures",
        description = "Render multi-character operators like => and !== as single glyphs",
        checked = settings.fontLigatures,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_editor_ligatures"),
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(fontLigatures = enabled) } },
      )

      SettingsDivider()

      SettingsChoiceRow(
        title = "Tab size",
        description = "Spaces inserted per indent level",
        selected = settings.tabSize.toString(),
        options = listOf("2" to "2 Spaces", "4" to "4 Spaces"),
        chipTestTagPrefix = "settings_tab_size",
        onSelect = { choice ->
          val spaces = choice.toIntOrNull() ?: 2
          onUpdateSettings { it.copy(tabSize = spaces) }
        },
      )
    }

    // 2. Display & Code
    SettingsCard(title = "Display & Code") {
      SettingsToggleRow(
        title = "Word wrap",
        description = "Wrap long lines inside the viewport",
        checked = settings.wordWrap,
        horizontalPadding = 0.dp,
        onCheckedChange = { wrap -> onUpdateSettings { it.copy(wordWrap = wrap) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Horizontal scrollbar",
        description = "Show a draggable bottom scrollbar when word wrap is off",
        checked = settings.showHorizontalScrollbar,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_editor_hscrollbar"),
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(showHorizontalScrollbar = enabled) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Line numbers",
        description = "Show line numbers in the left gutter",
        checked = settings.showLineNumbers,
        horizontalPadding = 0.dp,
        onCheckedChange = { ln -> onUpdateSettings { it.copy(showLineNumbers = ln) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Code folding",
        description = "Show fold chevrons for blocks and functions",
        checked = settings.codeFolding,
        horizontalPadding = 0.dp,
        onCheckedChange = { fold -> onUpdateSettings { it.copy(codeFolding = fold) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Sticky headers",
        description = "Pin the current enclosing block at the top",
        checked = settings.stickyHeaders,
        horizontalPadding = 0.dp,
        onCheckedChange = { sticky -> onUpdateSettings { it.copy(stickyHeaders = sticky) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Indent guides",
        description = "Draw subtle vertical alignment lines per indent",
        checked = settings.indentGuides,
        horizontalPadding = 0.dp,
        onCheckedChange = { guides -> onUpdateSettings { it.copy(indentGuides = guides) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Gutter diff markers",
        description = "Highlight edited lines compared to saved state",
        checked = settings.gutterDiffMarkers,
        horizontalPadding = 0.dp,
        onCheckedChange = { diff -> onUpdateSettings { it.copy(gutterDiffMarkers = diff) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Auto-close brackets",
        description = "Automatically insert matching quotes and brackets",
        checked = settings.autoCloseBrackets,
        horizontalPadding = 0.dp,
        onCheckedChange = { auto -> onUpdateSettings { it.copy(autoCloseBrackets = auto) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Unified Editor Everywhere",
        description = "Use the Edge canvas editor across code, diff, and config views",
        checked = settings.useUnifiedEditorEverywhere,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_toggle_unified_editor"),
        onCheckedChange = { unified -> onUpdateSettings { it.copy(useUnifiedEditorEverywhere = unified) } },
      )
      SettingsDivider()
      SettingsChoiceRow(
        title = "Toolbar & icon labels",
        description = "How editor toolbar action labels appear",
        selected = settings.iconLabelBehavior.name,
        options = IconLabelBehavior.entries.map { it.name to it.shortLabel },
        chipTestTagPrefix = "settings_editor_label_behavior",
        onSelect = { key ->
          onUpdateSettings { it.copy(iconLabelBehavior = IconLabelBehavior.fromKey(key)) }
        },
      )
    }

    // 3. Gestures & Keyboard
    SettingsCard(title = "Gestures & Keyboard") {
      SettingsToggleRow(
        title = "Pinch to zoom",
        description = "Scale font size with two-finger pinch gesture",
        checked = settings.pinchZoomEnabled,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_editor_pinch_zoom"),
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(pinchZoomEnabled = enabled) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Keyboard suggestions",
        description = "Allow soft keyboard auto-suggestions and strip words",
        checked = settings.softKeyboardSuggestions,
        horizontalPadding = 0.dp,
        onCheckedChange = { sug -> onUpdateSettings { it.copy(softKeyboardSuggestions = sug) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Symbol bar above keyboard",
        description = "Show quick coding symbols row at the bottom of the editor",
        checked = settings.showSymbolBar,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_editor_symbol_bar"),
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(showSymbolBar = enabled) } },
      )
    }

    // 4. Tabs, Warnings & Auto-Save
    SettingsCard(
      title = "Tabs & Auto-Save",
      modifier = Modifier.testTag("settings_unsaved_warnings_section"),
    ) {
      SettingsChoiceRow(
        title = "Closing a tab with unsaved changes",
        description = "What happens when closing a modified file tab",
        selected = settings.normalizedCloseUnsavedPolicy(),
        options =
          listOf(
            EditorSettings.CLOSE_UNSAVED_ASK to "Ask",
            EditorSettings.CLOSE_UNSAVED_SAVE to "Save",
            EditorSettings.CLOSE_UNSAVED_DISCARD to "Don't save",
          ),
        chipTestTagPrefix = "settings_close_unsaved",
        onSelect = { policy ->
          onUpdateSettings {
            it.copy(
              closeUnsavedPolicy = policy,
              warnOnCloseDirtyTab = (policy == EditorSettings.CLOSE_UNSAVED_ASK),
            )
          }
        },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Closing Modified Tab",
        description = "Confirm before closing an unsaved editor tab",
        checked = settings.warnOnCloseDirtyTab,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_warn_close_dirty_tab"),
        onCheckedChange = { enabled ->
          onUpdateSettings {
            it.copy(
              warnOnCloseDirtyTab = enabled,
              closeUnsavedPolicy =
                if (enabled) EditorSettings.CLOSE_UNSAVED_ASK else EditorSettings.CLOSE_UNSAVED_SAVE,
            )
          }
        },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Leaving or Switching Project",
        description = "Warn before leaving a project with uncommitted edits",
        checked = settings.warnOnLeaveDirtyWorkspace,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_warn_leave_workspace"),
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(warnOnLeaveDirtyWorkspace = enabled) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Cloud / Git Overwrite",
        description = "Warn before remote sync overwrites local modifications",
        checked = settings.warnOnCloudOverwrite,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_warn_cloud_overwrite"),
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(warnOnCloudOverwrite = enabled) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Auto-Save",
        description = "Periodically save open project drafts",
        checked = settings.autoSaveEnabled,
        horizontalPadding = 0.dp,
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(autoSaveEnabled = enabled) } },
      )

      AnimatedVisibility(
        visible = settings.autoSaveEnabled,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          SettingsDivider()
          SettingsChoiceRow(
            title = "Auto-save interval",
            description = "How often drafts are flushed to disk",
            selected = settings.autoSaveIntervalSeconds.toString(),
            options = listOf("3" to "3s", "5" to "5s", "15" to "15s", "30" to "30s"),
            onSelect = { sec ->
              val v = sec.toIntOrNull() ?: 5
              onUpdateSettings { it.copy(autoSaveIntervalSeconds = v) }
            },
          )
        }
      }

      if (onClearAutoSavedDrafts != null) {
        SettingsDivider()
        SettingsActionRow(
          title = "Drafts",
          description = "Clear locally cached auto-saved recovery buffers",
          buttonLabel = "Clear Drafts",
          destructive = true,
          buttonTag = "settings_clear_drafts_button",
          onClick = onClearAutoSavedDrafts,
        )
      }
    }

    // 5. Selection & Drag
    val selConfig = settings.selectionDragConfig.normalized()
    SettingsFoldableSection(
      title = "Selection & Drag",
      icon = Icons.Outlined.TouchApp,
      summary = "${selConfig.holdMode.shortLabel} · ${selConfig.enabledOverlayCount}/7",
      initiallyExpanded = false,
      contentPadding = 16.dp,
      modifier = Modifier.testTag("settings_selection_drag_section"),
    ) {
      val previewCode = "const url = \"https://edge.dev/v1\";"
      val previewAnchorOffset = 22
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

      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Interactive Live Preview Card (uses code editor palette for authentic preview)
        Surface(
          color = palette.editorBg,
          shape = RoundedCornerShape(if (isV2) Ca.radius.lg else Ca.radius.control),
          border = if (isV2) null else BorderStroke(0.75.dp, scheme.outlineVariant.copy(alpha = 0.65f)),
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

            val previewBehavior = settings.iconLabelBehavior
            Surface(
              color = palette.glassElevated.copy(alpha = 0.96f),
              shape = RoundedCornerShape(999.dp),
              border = BorderStroke(1.dp, palette.ctaBg.copy(alpha = 0.42f)),
            ) {
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
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

        // Quick Overlay Mode Presets (Minimal vs Full Power)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Selection Toolbar Preset", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = scheme.onSurface)
          Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val isMinimal = selConfig.enabledOverlayCount <= 2 && !selConfig.showScopeBadge && !selConfig.showExpandShrink && !selConfig.showDragGrip && !selConfig.showModeToggle && !selConfig.showCodeActions
            SettingsPillChip(
              label = "Minimal",
              selected = isMinimal,
              tag = "sel_preset_minimal",
              onClick = {
                updateSelectionConfig {
                  it.copy(
                    showScopeBadge = false,
                    showExpandShrink = false,
                    showDragGrip = false,
                    showModeToggle = false,
                    showDropTargetLoupe = true,
                    showQuickActions = true,
                    showCodeActions = false,
                  )
                }
              },
            )
            SettingsPillChip(
              label = "Smart Power (All 7)",
              selected = selConfig.enabledOverlayCount == 7,
              tag = "sel_preset_power",
              onClick = {
                updateSelectionConfig {
                  it.copy(
                    showScopeBadge = true,
                    showExpandShrink = true,
                    showDragGrip = true,
                    showModeToggle = true,
                    showDropTargetLoupe = true,
                    showQuickActions = true,
                    showCodeActions = true,
                  )
                }
              },
            )
          }
        }

        SettingsDivider()

        // Hold Behavior
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Hold Behavior", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = scheme.onSurface)
          Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmartSelectionHoldMode.entries.forEach { mode ->
              SettingsPillChip(
                label = mode.shortLabel,
                selected = selConfig.holdMode == mode,
                tag = "sel_hold_mode_${mode.name}",
                onClick = { updateSelectionConfig { it.copy(holdMode = mode) } },
              )
            }
          }
        }

        SettingsDivider()

        // Initial Scope Depth
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Initial Scope Depth", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = scheme.onSurface)
          Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
              SmartSelectionInitialDepth.COMPACT to "Compact",
              SmartSelectionInitialDepth.SMART_AUTO to "Smart Auto",
              SmartSelectionInitialDepth.BROAD to "Broad",
            ).forEach { (depth, label) ->
              SettingsPillChip(
                label = label,
                selected = selConfig.initialDepth == depth,
                tag = "sel_depth_${depth.name}",
                onClick = { updateSelectionConfig { it.copy(initialDepth = depth) } },
              )
            }
          }
        }

        SettingsDivider()

        // Default Editor Drag Policy
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Default Editor Drag Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = scheme.onSurface)
          Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DefaultEditorDragPolicy.entries.forEach { policy ->
              SettingsPillChip(
                label = policy.shortLabel,
                selected = selConfig.defaultEditorDragMode == policy,
                tag = "sel_drag_policy_${policy.name}",
                onClick = { updateSelectionConfig { it.copy(defaultEditorDragMode = policy) } },
              )
            }
          }
        }

        SettingsDivider()

        // Hold Trigger Delay
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Hold Trigger Delay", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = scheme.onSurface)
            Box(
              modifier =
                Modifier
                  .background(scheme.primaryContainer, RoundedCornerShape(Ca.radius.pill))
                  .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Text(
                text = "${selConfig.holdDelayMs} ms",
                color = scheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
          Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(240, 320, 450).forEach { ms ->
              SettingsPillChip(
                label = "${ms}ms",
                selected = selConfig.holdDelayMs == ms,
                tag = "sel_hold_delay_$ms",
                onClick = { updateSelectionConfig { it.copy(holdDelayMs = ms) } },
              )
            }
          }
        }

        SettingsDivider()

        SettingsToggleRow(
          title = "Immediate Slide-to-Drag",
          leadingIcon = Icons.Outlined.Swipe,
          checked = selConfig.immediateSlideToDrag,
          onCheckedChange = { enabled -> updateSelectionConfig { it.copy(immediateSlideToDrag = enabled) } },
          horizontalPadding = 0.dp,
          modifier = Modifier.testTag("sel_toggle_immediate_slide"),
        )
        SettingsDivider()
        SettingsToggleRow(
          title = "Cross-App Global Drag",
          leadingIcon = Icons.Outlined.OpenWith,
          checked = selConfig.allowCrossAppDrag,
          onCheckedChange = { enabled -> updateSelectionConfig { it.copy(allowCrossAppDrag = enabled) } },
          horizontalPadding = 0.dp,
          modifier = Modifier.testTag("sel_toggle_cross_app"),
        )

        SettingsDivider()

        Text(
          text = "HOLD OVERLAY ELEMENTS (${selConfig.enabledOverlayCount}/7)",
          fontFamily = JetBrainsMono,
          fontSize = 9.5.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.7.sp,
          color = scheme.outline,
        )

        SettingsToggleRow(
          title = "Scope Badge",
          leadingIcon = Icons.Outlined.AutoAwesome,
          checked = selConfig.showScopeBadge,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showScopeBadge = v) } },
          horizontalPadding = 0.dp,
          modifier = Modifier.testTag("sel_toggle_scope_badge"),
        )
        SettingsDivider()
        SettingsToggleRow(
          title = "Expand / Shrink Buttons",
          leadingIcon = Icons.Outlined.Tune,
          checked = selConfig.showExpandShrink,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showExpandShrink = v) } },
          horizontalPadding = 0.dp,
          modifier = Modifier.testTag("sel_toggle_expand_shrink"),
        )
        SettingsDivider()
        SettingsToggleRow(
          title = "Drag Grip",
          leadingIcon = Icons.Outlined.DragIndicator,
          checked = selConfig.showDragGrip,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showDragGrip = v) } },
          horizontalPadding = 0.dp,
          modifier = Modifier.testTag("sel_toggle_drag_grip"),
        )
        SettingsDivider()
        SettingsToggleRow(
          title = "Move / Copy Switch",
          leadingIcon = Icons.Outlined.ContentCopy,
          checked = selConfig.showModeToggle,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showModeToggle = v) } },
          horizontalPadding = 0.dp,
          modifier = Modifier.testTag("sel_toggle_mode_switch"),
        )
        SettingsDivider()
        SettingsToggleRow(
          title = "Drop Target Loupe (1.5x)",
          leadingIcon = Icons.Outlined.ZoomIn,
          checked = selConfig.showDropTargetLoupe,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showDropTargetLoupe = v) } },
          horizontalPadding = 0.dp,
          modifier = Modifier.testTag("sel_toggle_drop_loupe"),
        )
        SettingsDivider()
        SettingsToggleRow(
          title = "Quick Actions (Copy, Cut, Paste, All)",
          leadingIcon = Icons.Outlined.Timer,
          checked = selConfig.showQuickActions,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showQuickActions = v) } },
          horizontalPadding = 0.dp,
          modifier = Modifier.testTag("sel_toggle_quick_actions"),
        )
        SettingsDivider()
        SettingsToggleRow(
          title = "Code Actions (//, Rename)",
          leadingIcon = Icons.Outlined.Code,
          checked = selConfig.showCodeActions,
          onCheckedChange = { v -> updateSelectionConfig { it.copy(showCodeActions = v) } },
          horizontalPadding = 0.dp,
          modifier = Modifier.testTag("sel_toggle_code_actions"),
        )

        if (selConfig != SelectionDragConfig.DEFAULT) {
          SettingsDivider()
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

@Composable
private fun PreviewCaretIndicator(
  caretStyle: String,
  caretColor: Color,
  heightDp: Int,
) {
  Canvas(modifier = Modifier.padding(start = 1.dp).width(9.dp).height(heightDp.dp)) {
    val w = size.width
    val h = size.height
    when (caretStyle) {
      EditorSettings.CARET_BLOCK -> {
        drawRoundRect(
          color = caretColor.copy(alpha = 0.36f),
          topLeft = Offset.Zero,
          size = Size(w * 0.85f, h),
          cornerRadius = CornerRadius(2f, 2f),
        )
      }
      EditorSettings.CARET_UNDERLINE -> {
        val barH = 2.2.dp.toPx()
        drawRect(
          color = caretColor,
          topLeft = Offset(0f, h - barH),
          size = Size(w * 0.85f, barH),
        )
      }
      EditorSettings.CARET_CANDLE -> {
        val cx = w * 0.35f
        val flameBottom = h * 0.36f
        val bodyW = 2.dp.toPx()
        drawRoundRect(
          color = Color(0xFFFFE4B5).copy(alpha = 0.85f),
          topLeft = Offset(cx - bodyW / 2f, flameBottom + 1f),
          size = Size(bodyW, (h - flameBottom - 1f).coerceAtLeast(2f)),
          cornerRadius = CornerRadius(1.5f, 1.5f),
        )
        val flameTop = (flameBottom - 5.dp.toPx()).coerceAtLeast(0f)
        val path =
          Path().apply {
            moveTo(cx, flameBottom)
            cubicTo(cx - 3.dp.toPx(), flameBottom - 1.dp.toPx(), cx - 2.dp.toPx(), flameTop + 1.dp.toPx(), cx, flameTop)
            cubicTo(cx + 2.dp.toPx(), flameTop + 1.dp.toPx(), cx + 3.dp.toPx(), flameBottom - 1.dp.toPx(), cx, flameBottom)
            close()
          }
        drawPath(path, Color(0xFFFF8C00))
      }
      else -> {
        drawRect(
          color = caretColor,
          topLeft = Offset.Zero,
          size = Size(2.dp.toPx(), h),
        )
      }
    }
  }
}
