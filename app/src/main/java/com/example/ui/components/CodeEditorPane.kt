package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.remote.DiagnosticSeverity
import com.example.data.remote.WorkerLanguage
import com.example.editor.CodeSyntaxVisualTransformation
import com.example.editor.WranglerSyncParser
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.viewmodel.IdeUiState

@Composable
fun CodeEditorPane(
  uiState: IdeUiState,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onSelectAdjacentTab: (Boolean) -> Unit,
  onCloseOtherTabs: (String) -> Unit,
  onCloseSavedTabs: () -> Unit,
  onCloseAllTabs: () -> Unit,
  onSetEntryModule: (String) -> Unit,
  onRevertFile: (String) -> Unit,
  onNavigateTabBack: () -> Unit,
  onNavigateTabForward: () -> Unit,
  onOpenWorkerPreview: () -> Unit,
  onCreateNewFile: () -> Unit,
  onCodeChange: (TextFieldValue) -> Unit,
  onInsertText: (String, Int) -> Unit,
  onInsertTab: () -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onFormat: () -> Unit,
  onToggleComment: () -> Unit,
  onToggleFindBar: () -> Unit,
  onFindQueryChange: (String) -> Unit,
  onReplaceQueryChange: (String) -> Unit,
  onFindNext: () -> Unit,
  onReplaceAll: () -> Unit,
  onOpenProblems: () -> Unit,
  onOpenExplorer: () -> Unit,
  onShowNewWorkerDialog: () -> Unit,
  onDismissDraftBanner: () -> Unit,
  onDiscardRestoredDraft: () -> Unit,
  onJumpToLine: (String, Int) -> Unit,
  onSyncWranglerToWorkspace: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val activeFile = uiState.activeFile

  Column(modifier = modifier.fillMaxSize().background(palette.editorBg)) {
    if (uiState.workspace != null) {
      EditorFileTabBar(
        uiState = uiState,
        onSelectTab = onSelectTab,
        onCloseTab = onCloseTab,
        onSelectAdjacentTab = onSelectAdjacentTab,
        onCloseOtherTabs = onCloseOtherTabs,
        onCloseSavedTabs = onCloseSavedTabs,
        onCloseAllTabs = onCloseAllTabs,
        onSetEntryModule = onSetEntryModule,
        onRevertFile = onRevertFile,
        onCreateNewFile = onCreateNewFile,
        onOpenExplorer = onOpenExplorer,
      )
    }

    uiState.restoredDraftBanner?.let { bannerMsg ->
      Surface(
        color = palette.activePillBg,
        modifier = Modifier.fillMaxWidth().testTag("restored_draft_banner"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = bannerMsg,
            style = MaterialTheme.typography.labelSmall,
            color = palette.activePillFg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = onDismissDraftBanner) {
            Text("Keep", color = palette.activePillFg, fontWeight = FontWeight.Bold)
          }
          TextButton(onClick = onDiscardRestoredDraft) {
            Text("Discard", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    }

    if (activeFile == null) {
      val workspaceFiles = uiState.workspace?.files.orEmpty()
      if (uiState.workspace != null && workspaceFiles.isNotEmpty()) {
        AllTabsClosedState(
          workerName = uiState.workspace.scriptName,
          files = workspaceFiles,
          onOpenFile = onSelectTab,
          onCreateNewFile = onCreateNewFile,
          onOpenExplorer = onOpenExplorer,
        )
      } else {
        EmptyWorkspaceWelcome(
          hasWorkers = uiState.workers.isNotEmpty(),
          onOpenExplorer = onOpenExplorer,
          onShowNewWorkerDialog = onShowNewWorkerDialog,
          onOpenWorkerPreview = onOpenWorkerPreview,
          onCreateNewFile = onCreateNewFile,
        )
      }
      return
    }

    val isWranglerConfig =
      activeFile.path.equals("wrangler.jsonc", ignoreCase = true) ||
        activeFile.path.equals("wrangler.json", ignoreCase = true) ||
        activeFile.path.equals("wrangler.toml", ignoreCase = true)
    var showSymbolOutlineMenu by remember { mutableStateOf(false) }
    val tfv = uiState.activeTextFieldValue
    val symbols =
      remember(tfv.text, activeFile.language) {
        WranglerSyncParser.extractCodeSymbols(tfv.text, activeFile.language)
      }

    // Tab-history navigation chevrons (< >) + Breadcrumb Strip (matching Screenshot 2)
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.editorBg)
          .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onNavigateTabBack,
        enabled = uiState.canNavigateTabBack,
        modifier = Modifier.size(30.dp).testTag("editor_nav_back"),
      ) {
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowLeft,
          contentDescription = "Previous tab",
          tint = if (uiState.canNavigateTabBack) palette.textPrimary else palette.textMuted.copy(alpha = 0.45f),
          modifier = Modifier.size(18.dp),
        )
      }
      IconButton(
        onClick = onNavigateTabForward,
        enabled = uiState.canNavigateTabForward,
        modifier = Modifier.size(30.dp).testTag("editor_nav_forward"),
      ) {
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowRight,
          contentDescription = "Next tab",
          tint = if (uiState.canNavigateTabForward) palette.textPrimary else palette.textMuted.copy(alpha = 0.45f),
          modifier = Modifier.size(18.dp),
        )
      }
      Spacer(Modifier.width(4.dp))

      val segments =
        buildList {
          add(uiState.workspace?.scriptName ?: "worker")
          addAll(activeFile.path.split('/'))
        }
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        segments.forEachIndexed { idx, seg ->
          Text(
            text = seg,
            style = MaterialTheme.typography.labelSmall,
            color = if (idx == segments.lastIndex) palette.textPrimary else palette.textSecondary,
            fontWeight = if (idx == segments.lastIndex) FontWeight.Medium else FontWeight.Normal,
          )
          if (idx < segments.lastIndex) {
            Icon(
              imageVector = Icons.Outlined.KeyboardArrowRight,
              contentDescription = null,
              tint = palette.textMuted,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      if (uiState.diagnostics.isNotEmpty()) {
        val hasErr = uiState.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
        Surface(
          onClick = onOpenProblems,
          shape = RoundedCornerShape(999.dp),
          color =
            if (hasErr) MaterialTheme.colorScheme.errorContainer
            else palette.activePillBg,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              Icons.Outlined.WarningAmber,
              contentDescription = "Diagnostics",
              modifier = Modifier.size(12.dp),
              tint =
                if (hasErr) MaterialTheme.colorScheme.onErrorContainer
                else palette.activePillFg,
            )
            Text(
              text = "${uiState.diagnostics.size}",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (hasErr) MaterialTheme.colorScheme.onErrorContainer
                else palette.activePillFg,
            )
          }
        }
      }

      if (isWranglerConfig) {
        Surface(
          onClick = onSyncWranglerToWorkspace,
          shape = RoundedCornerShape(999.dp),
          color = palette.activePillBg,
          modifier = Modifier.padding(end = 4.dp).testTag("apply_wrangler_config_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(Icons.Outlined.Sync, contentDescription = "Apply Wrangler Config", tint = palette.activePillFg, modifier = Modifier.size(13.dp))
            Text("Apply Config", style = MaterialTheme.typography.labelSmall, color = palette.activePillFg, fontWeight = FontWeight.SemiBold)
          }
        }
      }

      if (symbols.isNotEmpty()) {
        Box {
          IconButton(
            onClick = { showSymbolOutlineMenu = true },
            modifier = Modifier.size(32.dp).testTag("symbol_outline_button"),
          ) {
            Icon(
              Icons.Outlined.ListAlt,
              contentDescription = "Symbol Outline",
              tint = palette.textSecondary,
              modifier = Modifier.size(16.dp),
            )
          }
          DropdownMenu(expanded = showSymbolOutlineMenu, onDismissRequest = { showSymbolOutlineMenu = false }) {
            symbols.forEach { sym ->
              DropdownMenuItem(
                text = {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = palette.activePillBg, shape = RoundedCornerShape(4.dp)) {
                      Text(sym.kind, fontSize = 9.sp, color = palette.activePillFg, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                    Text(sym.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(":${sym.line}", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                  }
                },
                onClick = {
                  showSymbolOutlineMenu = false
                  onJumpToLine(activeFile.path, sym.line)
                },
              )
            }
          }
        }
      }

      IconButton(
        onClick = onToggleFindBar,
        modifier = Modifier.size(32.dp).testTag("find_replace_toggle"),
      ) {
        Icon(
          Icons.Outlined.Search,
          contentDescription = stringResource(R.string.cd_find_replace),
          tint = palette.textSecondary,
          modifier = Modifier.size(16.dp),
        )
      }
      if (!activeFile.isVirtualReadOnly) {
        IconButton(
          onClick = onFormat,
          modifier = Modifier.size(32.dp).testTag("format_code_button"),
        ) {
          Icon(
            Icons.AutoMirrored.Outlined.FormatIndentIncrease,
            contentDescription = stringResource(R.string.cd_format_code),
            tint = palette.textSecondary,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }

    // Collapsible Find & Replace Strip
    AnimatedVisibility(visible = uiState.showFindBar) {
      Surface(
        color = palette.chromeSurface,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
              value = uiState.findQuery,
              onValueChange = onFindQueryChange,
              placeholder = { Text("Find") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onFindNext, enabled = uiState.findQuery.isNotEmpty()) {
              Text("Next")
            }
          }
          if (!activeFile.isVirtualReadOnly) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              OutlinedTextField(
                value = uiState.replaceQuery,
                onValueChange = onReplaceQueryChange,
                placeholder = { Text("Replace") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = onReplaceAll, enabled = uiState.findQuery.isNotEmpty()) {
                Text("Replace All")
              }
            }
          }
        }
      }
    }

    // Code Editor with Gutter, Active Line Highlight, Fold Marker & Vertical Gutter Divider
    val fontSize = uiState.editorSettings.fontSizeSp.sp
    val lineHeight = (uiState.editorSettings.fontSizeSp + 7).sp
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val codeLines = remember(tfv.text) { tfv.text.lines() }
    val activeLine = uiState.cursorLineAndCol.first
    val visualTransformation =
      remember(activeFile.language, palette) {
        CodeSyntaxVisualTransformation(activeFile.language, palette)
      }

    Row(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(verticalScroll),
    ) {
      if (uiState.editorSettings.showLineNumbers) {
        Column(
          modifier =
            Modifier
              .background(palette.editorGutterBg)
              .padding(top = 8.dp, bottom = 12.dp),
          horizontalAlignment = Alignment.End,
        ) {
          codeLines.forEachIndexed { idx, lineText ->
            val lineNum = idx + 1
            val isCurrent = lineNum == activeLine
            val hasBlockOpen = lineText.trimEnd().endsWith("{")
            Row(
              modifier =
                Modifier
                  .background(if (isCurrent) palette.editorActiveLineBg else palette.editorGutterBg)
                  .padding(start = 10.dp, end = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = lineNum.toString(),
                style =
                  TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = if (isCurrent) palette.textPrimary else palette.textMuted,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                  ),
              )
              Text(
                text = if (hasBlockOpen) "▾" else " ",
                style =
                  TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    lineHeight = lineHeight,
                    color = palette.textMuted,
                  ),
              )
            }
          }
        }

        VerticalDivider(color = palette.border, thickness = 1.dp)
      }

      val editorModifier =
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .then(
            if (!uiState.editorSettings.wordWrap) Modifier.horizontalScroll(horizontalScroll)
            else Modifier
          )
          .padding(horizontal = 10.dp, vertical = 8.dp)
          .testTag("code_editor_input")

      BasicTextField(
        value = tfv,
        onValueChange = { if (!activeFile.isVirtualReadOnly) onCodeChange(it) },
        readOnly = activeFile.isVirtualReadOnly,
        textStyle =
          TextStyle(
            fontFamily = JetBrainsMono,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = palette.textPrimary,
          ),
        cursorBrush = SolidColor(palette.activePillFg),
        visualTransformation = visualTransformation,
        modifier = editorModifier,
      )
    }

    // Workers Autocomplete Strip + Compact Coding Symbol Accessory Bar
    if (!activeFile.isVirtualReadOnly) {
      if (activeFile.language in setOf(WorkerLanguage.JAVASCRIPT, WorkerLanguage.TYPESCRIPT, WorkerLanguage.JSON)) {
        val cursorPos = tfv.selection.start.coerceIn(0, tfv.text.length)
        val wordBeforeCursor =
          remember(tfv.text, cursorPos) {
            val prefixWindow = tfv.text.substring((cursorPos - 40).coerceAtLeast(0), cursorPos)
            prefixWindow.takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '.' || it == '$' }
          }
        val completions =
          remember(uiState.workspace?.bindings, activeFile.language, wordBeforeCursor) {
            WranglerSyncParser.buildWorkersCompletions(uiState.workspace, activeFile.language, wordBeforeCursor)
          }
        if (completions.isNotEmpty()) {
          HorizontalDivider(color = palette.border)
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .background(palette.chromeBg)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            completions.forEach { sug ->
              Surface(
                onClick = {
                  if (wordBeforeCursor.isNotEmpty() && sug.label.startsWith(wordBeforeCursor, ignoreCase = true)) {
                    val replaceStart = (cursorPos - wordBeforeCursor.length).coerceAtLeast(0)
                    val nextText = tfv.text.replaceRange(replaceStart, cursorPos, sug.insertText)
                    val nextPos = (replaceStart + sug.insertText.length - sug.cursorBackOffset).coerceIn(0, nextText.length)
                    onCodeChange(TextFieldValue(nextText, TextRange(nextPos)))
                  } else {
                    onInsertText(sug.insertText, sug.cursorBackOffset)
                  }
                },
                shape = RoundedCornerShape(8.dp),
                color = palette.chromePill,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Text(sug.label, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = palette.activePillFg)
                  Text(sug.detail, style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                }
              }
            }
          }
        }
      }

      HorizontalDivider(color = palette.border)
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(palette.chromeSurface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onUndo, modifier = Modifier.size(34.dp).testTag("editor_undo")) {
          Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo", tint = palette.textSecondary, modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onRedo, modifier = Modifier.size(34.dp).testTag("editor_redo")) {
          Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo", tint = palette.textSecondary, modifier = Modifier.size(17.dp))
        }
        AccessoryKey("TAB") { onInsertTab() }
        AccessoryKey("//") { onToggleComment() }
        AccessoryKey("{ }") { onInsertText("{}", 1) }
        AccessoryKey("( )") { onInsertText("()", 1) }
        AccessoryKey("[ ]") { onInsertText("[]", 1) }
        AccessoryKey("=>") { onInsertText(" => ", 0) }
        AccessoryKey(";") { onInsertText(";", 0) }
        AccessoryKey(":") { onInsertText(": ", 0) }
        AccessoryKey("\"") { onInsertText("\"\"", 1) }
        AccessoryKey("'") { onInsertText("''", 1) }
        AccessoryKey("`") { onInsertText("``", 1) }
        AccessoryKey("=") { onInsertText(" = ", 0) }
        AccessoryKey(".") { onInsertText(".", 0) }
      }
    }
  }
}

@Composable
private fun AccessoryKey(label: String, onClick: () -> Unit) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = palette.chromePill,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = palette.textPrimary,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
  }
}

@Composable
private fun EmptyWorkspaceWelcome(
  hasWorkers: Boolean,
  onOpenExplorer: () -> Unit,
  onShowNewWorkerDialog: () -> Unit,
  onOpenWorkerPreview: () -> Unit,
  onCreateNewFile: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 400.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_empty_workspace),
        contentDescription = stringResource(R.string.cd_empty_workspace),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(132.dp).clip(RoundedCornerShape(28.dp)),
      )
      Text(
        text = "EdgeCode Workspace",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary,
      )
      Text(
        text =
          if (hasWorkers) "Open a Cloudflare Worker or Pages project, deploy edits live, and inspect responses in the Worker Preview window."
          else "Create a Cloudflare Worker, deploy to your workers.dev URL, and test it in the live Worker Preview window.",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.textSecondary,
        textAlign = TextAlign.Center,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
          onClick = onOpenWorkerPreview,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(48.dp).testTag("welcome_worker_preview_button"),
        ) {
          Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Test Live Worker")
        }
        OutlinedButton(
          onClick = onCreateNewFile,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(48.dp).testTag("welcome_new_file_button"),
        ) {
          Icon(Icons.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("New File")
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasWorkers) {
          OutlinedButton(
            onClick = onOpenExplorer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(48.dp),
          ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("Explorer")
          }
        }
        Button(
          onClick = onShowNewWorkerDialog,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(48.dp).testTag("welcome_new_worker_button"),
        ) {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(6.dp))
          Text("New Worker")
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorFileTabBar(
  uiState: IdeUiState,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onSelectAdjacentTab: (Boolean) -> Unit,
  onCloseOtherTabs: (String) -> Unit,
  onCloseSavedTabs: () -> Unit,
  onCloseAllTabs: () -> Unit,
  onSetEntryModule: (String) -> Unit,
  onRevertFile: (String) -> Unit,
  onCreateNewFile: () -> Unit,
  onOpenExplorer: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val tabListState = rememberLazyListState()
  var tabSwitcherOpen by remember { mutableStateOf(false) }
  var switcherQuery by remember { mutableStateOf("") }
  var contextMenuPath by remember { mutableStateOf<String?>(null) }

  val workspaceFiles = uiState.workspace?.files.orEmpty()
  val duplicateNames =
    remember(uiState.openTabPaths) {
      uiState.openTabPaths
        .groupingBy { it.substringAfterLast('/') }
        .eachCount()
        .filterValues { it > 1 }
        .keys
    }

  LaunchedEffect(uiState.activeFilePath, uiState.openTabPaths) {
    val idx = uiState.openTabPaths.indexOf(uiState.activeFilePath)
    if (idx >= 0) {
      tabListState.animateScrollToItem(idx)
    }
  }

  Column(modifier = Modifier.fillMaxWidth().background(palette.tabBarBg)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Surface(
        onClick = onOpenExplorer,
        shape = RoundedCornerShape(9.dp),
        color = palette.chromePill,
        modifier = Modifier.testTag("editor_files_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            Icons.Outlined.FolderOpen,
            contentDescription = "Open Project Files",
            tint = palette.activePillFg,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "Files",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
          )
        }
      }
      if (uiState.openTabPaths.isEmpty()) {
        Text(
          text = "${workspaceFiles.size} project files",
          style = MaterialTheme.typography.labelSmall,
          color = palette.textMuted,
          modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
      } else {
        LazyRow(
          state = tabListState,
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          items(uiState.openTabPaths, key = { it }) { path ->
            val file = workspaceFiles.firstOrNull { it.path == path }
            val isActive = path == uiState.activeFilePath
            val fileName = path.substringAfterLast('/')
            val parentHint =
              if (fileName in duplicateNames && path.contains('/')) {
                path.substringBeforeLast('/').substringAfterLast('/') + "/"
              } else {
                ""
              }

            Box {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isActive) palette.tabActiveBg else palette.chromeSurface,
                border =
                  if (isActive) BorderStroke(1.dp, palette.activePillFg.copy(alpha = 0.45f))
                  else BorderStroke(1.dp, palette.border.copy(alpha = 0.25f)),
                modifier =
                  Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                      onClick = { onSelectTab(path) },
                      onLongClick = { contextMenuPath = path },
                    )
                    .testTag("editor_tab_$path"),
              ) {
                Column {
                  Row(
                    modifier = Modifier.padding(start = 9.dp, end = 4.dp, top = 5.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                  ) {
                    FileTypeBadge(path = path)
                    Column {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        if (parentHint.isNotEmpty()) {
                          Text(
                            text = parentHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted,
                            maxLines = 1,
                          )
                        }
                        Text(
                          text = fileName,
                          style = MaterialTheme.typography.labelMedium,
                          fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                          color = if (isActive) palette.textPrimary else palette.textSecondary,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        if (file?.isEntryPoint == true) {
                          Surface(color = palette.activePillBg, shape = RoundedCornerShape(4.dp)) {
                            Text(
                              text = "MAIN",
                              fontSize = 8.sp,
                              fontWeight = FontWeight.Bold,
                              color = palette.activePillFg,
                              modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                          }
                        }
                        if (file?.isDirty == true) {
                          Box(
                            modifier =
                              Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(palette.activePillFg)
                                .testTag("dirty_dot_$path"),
                          )
                        }
                      }
                    }
                    IconButton(
                      onClick = { onCloseTab(path) },
                      modifier = Modifier.size(22.dp).testTag("close_tab_$path"),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "${stringResource(R.string.cd_close_tab)} $fileName",
                        tint = palette.textMuted,
                        modifier = Modifier.size(13.dp),
                      )
                    }
                  }
                  if (isActive) {
                    Box(
                      modifier =
                        Modifier
                          .fillMaxWidth()
                          .height(2.dp)
                          .background(palette.activePillFg),
                    )
                  }
                }
              }

              DropdownMenu(
                expanded = contextMenuPath == path,
                onDismissRequest = { contextMenuPath = null },
              ) {
                DropdownMenuItem(
                  text = { Text("Close Tab") },
                  onClick = {
                    contextMenuPath = null
                    onCloseTab(path)
                  },
                )
                if (uiState.openTabPaths.size > 1) {
                  DropdownMenuItem(
                    text = { Text("Close Other Tabs") },
                    onClick = {
                      contextMenuPath = null
                      onCloseOtherTabs(path)
                    },
                  )
                }
                DropdownMenuItem(
                  text = { Text("Close Saved Tabs") },
                  onClick = {
                    contextMenuPath = null
                    onCloseSavedTabs()
                  },
                )
                DropdownMenuItem(
                  text = { Text("Close All Tabs") },
                  onClick = {
                    contextMenuPath = null
                    onCloseAllTabs()
                  },
                )
                if (file != null && !file.isVirtualReadOnly && !file.isEntryPoint && file.language in setOf(WorkerLanguage.JAVASCRIPT, WorkerLanguage.TYPESCRIPT)) {
                  DropdownMenuItem(
                    text = { Text("Set as Worker Entry Point") },
                    onClick = {
                      contextMenuPath = null
                      onSetEntryModule(path)
                    },
                  )
                }
                if (file != null && file.isDirty && !file.isVirtualReadOnly) {
                  DropdownMenuItem(
                    text = { Text("Revert to Imported Baseline", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                      contextMenuPath = null
                      onRevertFile(path)
                    },
                  )
                }
              }
            }
          }
        }
      }

      // Quick Prev/Next tab cycling
      if (uiState.openTabPaths.size > 1) {
        IconButton(
          onClick = { onSelectAdjacentTab(false) },
          modifier = Modifier.size(26.dp).testTag("tab_cycle_prev"),
        ) {
          Icon(Icons.Outlined.KeyboardArrowLeft, contentDescription = "Previous open tab", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
        }
        IconButton(
          onClick = { onSelectAdjacentTab(true) },
          modifier = Modifier.size(26.dp).testTag("tab_cycle_next"),
        ) {
          Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "Next open tab", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
        }
      }

      // Quick New File button
      IconButton(
        onClick = onCreateNewFile,
        modifier = Modifier.size(28.dp).testTag("tab_bar_new_file"),
      ) {
        Icon(Icons.Outlined.Add, contentDescription = "New file tab", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
      }

      // Searchable Open Tabs & Workspace Files Switcher
      Box {
        Surface(
          onClick = {
            switcherQuery = ""
            tabSwitcherOpen = true
          },
          shape = RoundedCornerShape(8.dp),
          color = palette.chromePill,
          modifier = Modifier.testTag("tab_switcher_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = "${uiState.openTabPaths.size}/${workspaceFiles.size}",
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium,
              color = palette.textPrimary,
            )
            Icon(
              Icons.Outlined.KeyboardArrowDown,
              contentDescription = "Open tabs and project file switcher",
              tint = palette.textSecondary,
              modifier = Modifier.size(14.dp),
            )
          }
        }

        DropdownMenu(
          expanded = tabSwitcherOpen,
          onDismissRequest = { tabSwitcherOpen = false },
          modifier = Modifier.widthIn(min = 260.dp, max = 340.dp).heightIn(max = 420.dp),
        ) {
          OutlinedTextField(
            value = switcherQuery,
            onValueChange = { switcherQuery = it },
            placeholder = { Text("Jump to file…", fontSize = 12.sp) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
          )

          val openMatches =
            uiState.openTabPaths.filter { p ->
              switcherQuery.isBlank() || p.contains(switcherQuery, ignoreCase = true)
            }
          val closedProjectMatches =
            workspaceFiles
              .map { it.path }
              .filter { it !in uiState.openTabPaths && (switcherQuery.isBlank() || it.contains(switcherQuery, ignoreCase = true)) }

          if (openMatches.isNotEmpty()) {
            Text(
              text = "OPEN TABS (${openMatches.size})",
              style = MaterialTheme.typography.labelSmall,
              color = palette.textMuted,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            openMatches.forEach { path ->
              val isActive = path == uiState.activeFilePath
              val isDirty = workspaceFiles.firstOrNull { it.path == path }?.isDirty == true
              DropdownMenuItem(
                text = {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileTypeBadge(path = path)
                    Text(
                      text = path,
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                      color = if (isActive) palette.activePillFg else palette.textPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    if (isDirty) {
                      Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AmberWarn))
                    }
                  }
                },
                onClick = {
                  tabSwitcherOpen = false
                  onSelectTab(path)
                },
              )
            }
          }

          if (closedProjectMatches.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
              text = "WORKSPACE FILES (${closedProjectMatches.size})",
              style = MaterialTheme.typography.labelSmall,
              color = palette.textMuted,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            closedProjectMatches.forEach { path ->
              DropdownMenuItem(
                text = {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileTypeBadge(path = path)
                    Text(
                      text = path,
                      style = MaterialTheme.typography.bodySmall,
                      color = palette.textSecondary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                  }
                },
                onClick = {
                  tabSwitcherOpen = false
                  onSelectTab(path)
                },
              )
            }
          }

          if (uiState.openTabPaths.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
              text = { Text("Close Saved Tabs") },
              onClick = {
                tabSwitcherOpen = false
                onCloseSavedTabs()
              },
            )
            DropdownMenuItem(
              text = { Text("Close All Tabs") },
              onClick = {
                tabSwitcherOpen = false
                onCloseAllTabs()
              },
            )
          }
        }
      }
    }
    HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
  }
}

@Composable
private fun AllTabsClosedState(
  workerName: String,
  files: List<com.example.data.remote.WorkerFile>,
  onOpenFile: (String) -> Unit,
  onCreateNewFile: () -> Unit,
  onOpenExplorer: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 400.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = workerName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary,
      )
      Text(
        text = "All editor tabs are closed. Pick a file from RAM or create a new module to continue editing on-device.",
        style = MaterialTheme.typography.bodySmall,
        color = palette.textSecondary,
        textAlign = TextAlign.Center,
      )
      files.take(4).forEach { file ->
        FilledTonalButton(
          onClick = { onOpenFile(file.path) },
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
          FileTypeBadge(path = file.path)
          Spacer(Modifier.width(8.dp))
          Text(file.path, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onOpenExplorer, modifier = Modifier.height(48.dp)) {
          Text("Explorer")
        }
        Button(onClick = onCreateNewFile, modifier = Modifier.height(48.dp)) {
          Text("New File")
        }
      }
    }
  }
}
