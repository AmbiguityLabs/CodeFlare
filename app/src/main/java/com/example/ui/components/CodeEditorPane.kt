package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.editor.drag.edgeTextDropTarget
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.remote.DiagnosticItem
import com.example.data.remote.DiagnosticSeverity
import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace
import com.example.editor.CodeSymbol
import com.example.editor.EditorCursorTelemetry
import com.example.editor.MergeConflictResolver
import com.example.editor.WranglerSyncParser
import com.example.editor.canvas.EdgeCodeEditorSurface
import com.example.editor.canvas.EdgeEditorController
import com.example.editor.canvas.EdgeGlassCompletionBar
import com.example.idecore.completion.EdgeCompletionItem
import com.example.idecore.completion.EdgeCompletionKind
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.UiStyleVersion
import com.example.ui.theme.rememberGlassBorderStroke
import com.example.ui.viewmodel.IdeUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Stable
private class EditorLiveHudHolder(initialZoomPercent: Int) {
  var telemetry by mutableStateOf(EditorCursorTelemetry())
  var liveZoomPercent by mutableIntStateOf(initialZoomPercent.coerceIn(60, 220))
  var isPinching by mutableStateOf(false)
}

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
  onFindPrevious: () -> Unit = {},
  onReplaceCurrent: () -> Unit = {},
  onToggleFindMatchCase: () -> Unit = {},
  onFindScopeChange: (Int) -> Unit = {},
  onOpenProblems: () -> Unit,
  onOpenExplorer: () -> Unit,
  onShowNewWorkerDialog: () -> Unit,
  onDismissDraftBanner: () -> Unit,
  onDiscardRestoredDraft: () -> Unit,
  onJumpToLine: (String, Int) -> Unit,
  onSyncWranglerToWorkspace: () -> Unit,
  onZoomPercentChange: (Int) -> Unit = {},
  onJumpToLocation: (String, Int, Int) -> Unit = { path, line, _ -> onJumpToLine(path, line) },
  onHideEditorBottomBars: () -> Unit = {},
  onOpenConflictResolver: (() -> Unit)? = null,
  onRegisterFlushHook: ((() -> Unit)?) -> Unit = {},
  onComputeCompletions: (filePath: String, text: CharSequence, cursor: Int, language: WorkerLanguage) -> List<EdgeCompletionItem> = { _, _, _, _ -> emptyList() },
  onAcceptCompletion: (EdgeCompletionItem) -> Unit = {},
  onRequestSmartRename: (initialSymbol: String?) -> Unit = {},
  onOpenLocalHistory: () -> Unit = {},
  onOpenProjectSearch: () -> Unit = {},
  onRunLocalScript: () -> Unit = {},
  editorCommandRequest: Pair<Long, String>? = null,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val activeFile = uiState.activeFile

  val editorController = remember { EdgeEditorController() }
  val hudHolder = remember { EditorLiveHudHolder(uiState.editorSettings.zoomPercent) }
  val latestOnCodeChange by rememberUpdatedState(onCodeChange)

  LaunchedEffect(editorCommandRequest) {
    val cmd = editorCommandRequest?.second ?: return@LaunchedEffect
    when (cmd) {
      "fold_all" -> editorController.foldAll()
      "unfold_all" -> editorController.unfoldAll()
      "toggle_fold" -> editorController.toggleFoldAtCurrentLine()
      "toggle_bookmark" -> editorController.toggleBookmark()
      "next_bookmark" -> editorController.nextBookmark()
      "paste_history" -> editorController.openPasteHistory()
    }
  }

  fun flushThen(action: () -> Unit) {
    editorController.flushNow()
    action()
  }

  Column(modifier = modifier.fillMaxSize().background(palette.editorBg)) {
    AnimatedVisibility(
      visible = uiState.workspace != null && !uiState.isEditorTopChromeHidden,
      enter = slideInVertically(tween(170)) { -it } + fadeIn(tween(150)),
      exit = slideOutVertically(tween(140)) { -it } + fadeOut(tween(120)),
    ) {
      EditorFileTabBar(
        uiState = uiState,
        onSelectTab = { p -> flushThen { onSelectTab(p) } },
        onCloseTab = { p -> flushThen { onCloseTab(p) } },
        onSelectAdjacentTab = { next -> flushThen { onSelectAdjacentTab(next) } },
        onCloseOtherTabs = { p -> flushThen { onCloseOtherTabs(p) } },
        onCloseSavedTabs = { flushThen(onCloseSavedTabs) },
        onCloseAllTabs = { flushThen(onCloseAllTabs) },
        onSetEntryModule = onSetEntryModule,
        onRevertFile = onRevertFile,
        onCreateNewFile = { flushThen(onCreateNewFile) },
        onOpenExplorer = { flushThen(onOpenExplorer) },
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

    val activeConflicts =
      remember(activeFile?.path, activeFile?.content) {
        if (activeFile != null && activeFile.content.contains("<<<<<<<")) {
          MergeConflictResolver.parseConflictMarkers(activeFile.content)
        } else {
          emptyList()
        }
      }
    if (activeConflicts.isNotEmpty() && onOpenConflictResolver != null) {
      Surface(
        color = AmberWarn.copy(alpha = 0.16f),
        modifier = Modifier.fillMaxWidth().testTag("editor_conflict_banner"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "${activeConflicts.size} merge conflict(s) in ${activeFile?.fileName.orEmpty()}",
            style = MaterialTheme.typography.labelSmall,
            color = AmberWarn,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = onOpenConflictResolver) {
            Text("Resolve", color = AmberWarn, fontWeight = FontWeight.Bold)
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
          onRunLocalScript = onRunLocalScript,
        )
      } else {
        EmptyWorkspaceWelcome(
          hasWorkers = uiState.workers.isNotEmpty(),
          onOpenExplorer = onOpenExplorer,
          onShowNewWorkerDialog = onShowNewWorkerDialog,
          onOpenWorkerPreview = onOpenWorkerPreview,
          onCreateNewFile = onCreateNewFile,
          onRunLocalScript = onRunLocalScript,
        )
      }
      return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activeFile.path, lifecycleOwner) {
      onRegisterFlushHook { editorController.flushNow() }
      val observer =
        LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
            editorController.flushNow()
          }
        }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        onRegisterFlushHook(null)
        lifecycleOwner.lifecycle.removeObserver(observer)
        editorController.flushNow()
      }
    }

    val syncedTfv = uiState.activeTextFieldValue
    val isWranglerConfig =
      activeFile.path.equals("wrangler.jsonc", ignoreCase = true) ||
        activeFile.path.equals("wrangler.json", ignoreCase = true) ||
        activeFile.path.equals("wrangler.toml", ignoreCase = true)
    var showSymbolOutlineMenu by remember { mutableStateOf(false) }

    var symbols by remember(activeFile.path, activeFile.language) {
      mutableStateOf<List<CodeSymbol>>(emptyList())
    }
    LaunchedEffect(activeFile.path, activeFile.language, activeFile.content) {
      if (activeFile.language == WorkerLanguage.TEXT) {
        symbols = emptyList()
        return@LaunchedEffect
      }
      val snapshot = activeFile.content
      val lang = activeFile.language
      if (snapshot.length > 1_200) delay(900L)
      symbols =
        withContext(Dispatchers.Default) {
          WranglerSyncParser.extractCodeSymbols(snapshot, lang)
        }
    }

    AnimatedVisibility(
      visible = !uiState.isEditorTopChromeHidden,
      enter = slideInVertically(tween(170)) { -it } + fadeIn(tween(150)),
      exit = slideOutVertically(tween(140)) { -it } + fadeOut(tween(120)),
    ) {
      Column(modifier = Modifier.fillMaxWidth().background(palette.editorBg)) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 6.dp, vertical = 1.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          IconButton(
            onClick = { flushThen(onNavigateTabBack) },
            enabled = uiState.canNavigateTabBack,
            modifier = Modifier.size(26.dp).testTag("editor_nav_back"),
          ) {
            Icon(
              imageVector = Icons.Outlined.KeyboardArrowLeft,
              contentDescription = "Previous tab",
              tint = if (uiState.canNavigateTabBack) palette.textPrimary else palette.textMuted.copy(alpha = 0.45f),
              modifier = Modifier.size(16.dp),
            )
          }
          IconButton(
            onClick = { flushThen(onNavigateTabForward) },
            enabled = uiState.canNavigateTabForward,
            modifier = Modifier.size(26.dp).testTag("editor_nav_forward"),
          ) {
            Icon(
              imageVector = Icons.Outlined.KeyboardArrowRight,
              contentDescription = "Next tab",
              tint = if (uiState.canNavigateTabForward) palette.textPrimary else palette.textMuted.copy(alpha = 0.45f),
              modifier = Modifier.size(16.dp),
            )
          }

          val segments =
            remember(uiState.workspace?.scriptName, activeFile.path) {
              buildList {
                add(uiState.workspace?.scriptName ?: "worker")
                addAll(activeFile.path.split('/'))
              }
            }
          Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            segments.forEachIndexed { idx, seg ->
              Text(
                text = seg,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = if (idx == segments.lastIndex) palette.textPrimary else palette.textMuted,
                fontWeight = if (idx == segments.lastIndex) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
              )
              if (idx < segments.lastIndex) {
                Icon(
                  imageVector = Icons.Outlined.KeyboardArrowRight,
                  contentDescription = null,
                  tint = palette.textMuted.copy(alpha = 0.7f),
                  modifier = Modifier.size(12.dp),
                )
              }
            }
          }

          Row(
            modifier = Modifier.weight(1.25f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (uiState.diagnostics.isNotEmpty()) {
              val hasErr = uiState.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
              IconRevealButton(
                icon = Icons.Outlined.WarningAmber,
                revealedLabel = "${uiState.diagnostics.size}",
                minimal = true,
                onClick = { flushThen(onOpenProblems) },
                active = !hasErr,
                statusDotColor = if (hasErr) RoseError else AmberWarn,
                height = 24.dp,
                iconSize = 14.dp,
              )
            }

            if (isWranglerConfig) {
              IconRevealButton(
                icon = Icons.Outlined.Sync,
                revealedLabel = "Apply Config",
                minimal = true,
                active = true,
                onClick = { flushThen(onSyncWranglerToWorkspace) },
                tag = "apply_wrangler_config_button",
                height = 24.dp,
                iconSize = 15.dp,
              )
            } else if (!activeFile.isVirtualReadOnly) {
              IconRevealButton(
                icon = Icons.Outlined.PlayArrow,
                revealedLabel = "Run CLI",
                minimal = true,
                isBusy = uiState.isRunningLocalScript,
                onClick = { flushThen(onRunLocalScript) },
                tag = "editor_run_local_button",
                height = 24.dp,
                iconSize = 15.dp,
              )
            }

            if (symbols.isNotEmpty()) {
              Box {
                IconRevealButton(
                  icon = Icons.Outlined.ListAlt,
                  revealedLabel = "Outline",
                  minimal = true,
                  onClick = { showSymbolOutlineMenu = true },
                  height = 24.dp,
                  iconSize = 15.dp,
                  tag = "symbol_outline_button",
                )
                EdgeMenu(
                  expanded = showSymbolOutlineMenu,
                  onDismissRequest = { showSymbolOutlineMenu = false },
                  minWidth = 224.dp,
                  maxWidth = 296.dp,
                ) {
                  EdgeMenuHeader(
                    title = "Outline",
                    badgeText = "${symbols.size}",
                    leadingIcon = Icons.Outlined.ListAlt,
                  )
                  symbols.forEach { sym ->
                    EdgeMenuItem(
                      label = sym.name,
                      monospaced = true,
                      badgeText = ":${sym.line}",
                      leadingContent = {
                        Box(
                          modifier =
                            Modifier
                              .clip(RoundedCornerShape(5.dp))
                              .background(palette.activePillBg.copy(alpha = 0.85f))
                              .border(0.5.dp, palette.activePillFg.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
                              .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                          Text(
                            text = sym.kind,
                            fontFamily = JetBrainsMono,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.activePillFg,
                          )
                        }
                      },
                      onClick = {
                        showSymbolOutlineMenu = false
                        flushThen { onJumpToLine(activeFile.path, sym.line) }
                      },
                    )
                  }
                }
              }
            }

            IconRevealButton(
              icon = Icons.Outlined.History,
              revealedLabel = "History",
              minimal = true,
              onClick = { flushThen(onOpenLocalHistory) },
              height = 24.dp,
              iconSize = 15.dp,
              tag = "local_history_button",
            )
            if (!activeFile.isVirtualReadOnly &&
              activeFile.language in setOf(WorkerLanguage.JAVASCRIPT, WorkerLanguage.TYPESCRIPT)
            ) {
              IconRevealButton(
                icon = Icons.Outlined.DriveFileRenameOutline,
                revealedLabel = "Rename",
                minimal = true,
                onClick = {
                  val ident = editorController.currentIdentifierAtCursor()
                  flushThen { onRequestSmartRename(ident) }
                },
                height = 24.dp,
                iconSize = 15.dp,
                tag = "rename_symbol_button",
              )
            }

            IconRevealButton(
              icon = Icons.Outlined.Search,
              revealedLabel = "Find",
              minimal = true,
              active = uiState.showFindBar,
              onClick = { flushThen(onToggleFindBar) },
              height = 24.dp,
              iconSize = 15.dp,
              tag = "find_replace_toggle",
            )
            if (!activeFile.isVirtualReadOnly) {
              IconRevealButton(
                icon = Icons.AutoMirrored.Outlined.FormatIndentIncrease,
                revealedLabel = "Format",
                minimal = true,
                onClick = { flushThen(onFormat) },
                height = 24.dp,
                iconSize = 15.dp,
                tag = "format_code_button",
              )
            }
          }
        }
        HorizontalDivider(color = palette.border.copy(alpha = 0.25f), thickness = 0.5.dp)
      }
    }

    val activeFindMatches =
      remember(uiState.showFindBar, uiState.findQuery, uiState.findMatchCase, syncedTfv.text) {
        if (!uiState.showFindBar || uiState.findQuery.isEmpty()) {
          emptyList()
        } else {
          val q = uiState.findQuery
          val ignoreCase = !uiState.findMatchCase
          val hits = ArrayList<IntRange>(32)
          var pos = 0
          val hay = syncedTfv.text
          while (pos <= hay.length - q.length && hits.size < 500) {
            val found = hay.indexOf(q, pos, ignoreCase = ignoreCase)
            if (found < 0) break
            hits.add(found until (found + q.length))
            pos = found + q.length
          }
          hits
        }
      }
    val activeFindMatchIndex =
      remember(activeFindMatches, syncedTfv.selection) {
        if (activeFindMatches.isEmpty()) -1
        else {
          val selStart = syncedTfv.selection.min
          val selEnd = syncedTfv.selection.max
          activeFindMatches.indexOfFirst { it.first == selStart && (it.last + 1) == selEnd }
            .takeIf { it >= 0 }
            ?: activeFindMatches.indexOfFirst { selStart in it.first..(it.last + 1) }
        }
      }
    val scopedTotalMatches =
      remember(
        uiState.showFindBar,
        uiState.findQuery,
        uiState.findMatchCase,
        uiState.findScope,
        uiState.openTabPaths,
        uiState.workspace?.files,
        uiState.editorFieldValues,
        syncedTfv.text,
      ) {
        if (!uiState.showFindBar || uiState.findQuery.isEmpty() || uiState.findScope == 0) {
          activeFindMatches.size
        } else {
          val q = uiState.findQuery
          val ignoreCase = !uiState.findMatchCase
          val targetPaths =
            if (uiState.findScope == 1) uiState.openTabPaths
            else uiState.workspace?.files?.map { it.path }.orEmpty()
          var total = 0
          targetPaths.forEach { p ->
            val txt =
              if (p == activeFile.path) syncedTfv.text
              else uiState.editorFieldValues[p]?.text
                ?: uiState.workspace?.files?.firstOrNull { it.path == p }?.content
                ?: ""
            var i = 0
            while (i <= txt.length - q.length) {
              val hit = txt.indexOf(q, i, ignoreCase = ignoreCase)
              if (hit < 0) break
              total++
              i = hit + q.length
            }
          }
          total
        }
      }

    AnimatedVisibility(visible = uiState.showFindBar) {
      Surface(
        color = palette.chromeSurface,
        border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth().testTag("editor_find_replace_panel"),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier
                  .weight(1f)
                  .height(34.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(palette.editorBg)
                  .border(0.75.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                  .edgeTextDropTarget(
                    targetSurfaceId = "Find",
                    currentValue = uiState.findQuery,
                    onValueChange = onFindQueryChange,
                    singleLine = true,
                    replaceAll = true,
                    shape = RoundedCornerShape(8.dp),
                  )
                  .padding(horizontal = 9.dp),
              contentAlignment = Alignment.CenterStart,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                  if (uiState.findQuery.isEmpty()) {
                    Text(
                      text = "Find in ${when (uiState.findScope) { 1 -> "open tabs"; 2 -> "project"; else -> "tab" }}…",
                      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
                      color = palette.textMuted,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  BasicTextField(
                    value = uiState.findQuery,
                    onValueChange = onFindQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textPrimary),
                    cursorBrush = SolidColor(palette.activePillFg),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { flushThen(onFindNext) }),
                    modifier = Modifier.fillMaxWidth().testTag("editor_find_input"),
                  )
                }
                if (uiState.findQuery.isNotEmpty()) {
                  val countText =
                    if (activeFindMatches.isEmpty() && scopedTotalMatches == 0) {
                      "0/0"
                    } else if (uiState.findScope > 0 && scopedTotalMatches != activeFindMatches.size) {
                      "${if (activeFindMatchIndex >= 0) activeFindMatchIndex + 1 else 0}/${activeFindMatches.size} ($scopedTotalMatches)"
                    } else {
                      "${if (activeFindMatchIndex >= 0) activeFindMatchIndex + 1 else 0}/${activeFindMatches.size}"
                    }
                  Text(
                    text = countText,
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = if (scopedTotalMatches == 0) RoseError else palette.activePillFg,
                    maxLines = 1,
                  )
                }
              }
            }

            Surface(
              onClick = onToggleFindMatchCase,
              shape = RoundedCornerShape(7.dp),
              color = if (uiState.findMatchCase) palette.activePillBg else palette.chromePill,
              border =
                BorderStroke(
                  0.75.dp,
                  if (uiState.findMatchCase) palette.activePillFg.copy(alpha = 0.45f)
                  else palette.border.copy(alpha = 0.5f),
                ),
              modifier = Modifier.height(34.dp).testTag("editor_find_case_toggle"),
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                  text = "Aa",
                  style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                  color = if (uiState.findMatchCase) palette.activePillFg else palette.textSecondary,
                )
              }
            }

            Surface(
              onClick = { if (uiState.findQuery.isNotEmpty()) flushThen(onFindPrevious) },
              shape = RoundedCornerShape(7.dp),
              color = palette.chromePill,
              border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.5f)),
              modifier = Modifier.size(34.dp).testTag("editor_find_prev"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Outlined.KeyboardArrowUp,
                  contentDescription = "Previous match",
                  tint = if (uiState.findQuery.isNotEmpty()) palette.textPrimary else palette.textMuted.copy(alpha = 0.45f),
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            Surface(
              onClick = { if (uiState.findQuery.isNotEmpty()) flushThen(onFindNext) },
              shape = RoundedCornerShape(7.dp),
              color = palette.chromePill,
              border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.5f)),
              modifier = Modifier.size(34.dp).testTag("editor_find_next"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Outlined.KeyboardArrowDown,
                  contentDescription = "Next match",
                  tint = if (uiState.findQuery.isNotEmpty()) palette.textPrimary else palette.textMuted.copy(alpha = 0.45f),
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            IconButton(
              onClick = onToggleFindBar,
              modifier = Modifier.size(30.dp).testTag("editor_find_close"),
            ) {
              Icon(
                Icons.Outlined.Close,
                contentDescription = "Close find bar",
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp),
              )
            }
          }

          if (!activeFile.isVirtualReadOnly) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.editorBg)
                    .border(0.75.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .edgeTextDropTarget(
                      targetSurfaceId = "Replace",
                      currentValue = uiState.replaceQuery,
                      onValueChange = onReplaceQueryChange,
                      singleLine = true,
                      replaceAll = true,
                      shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.CenterStart,
              ) {
                if (uiState.replaceQuery.isEmpty()) {
                  Text(
                    text = "Replace with…",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
                    color = palette.textMuted,
                    maxLines = 1,
                  )
                }
                BasicTextField(
                  value = uiState.replaceQuery,
                  onValueChange = onReplaceQueryChange,
                  singleLine = true,
                  textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textPrimary),
                  cursorBrush = SolidColor(palette.activePillFg),
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                  keyboardActions = KeyboardActions(onDone = { if (uiState.findQuery.isNotEmpty()) flushThen(onReplaceCurrent) }),
                  modifier = Modifier.fillMaxWidth().testTag("editor_replace_input"),
                )
              }
              Surface(
                onClick = { if (uiState.findQuery.isNotEmpty()) flushThen(onReplaceCurrent) },
                shape = RoundedCornerShape(7.dp),
                color = palette.chromePill,
                border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
                modifier = Modifier.height(34.dp).testTag("editor_replace_one"),
              ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                  Text(
                    text = "Replace",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (uiState.findQuery.isNotEmpty()) palette.textPrimary else palette.textMuted,
                  )
                }
              }
              Surface(
                onClick = { if (uiState.findQuery.isNotEmpty()) flushThen(onReplaceAll) },
                shape = RoundedCornerShape(7.dp),
                color = palette.activePillBg,
                border = BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.35f)),
                modifier = Modifier.height(34.dp).testTag("editor_replace_all"),
              ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                  Text(
                    text = "All",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (uiState.findQuery.isNotEmpty()) palette.activePillFg else palette.textMuted,
                  )
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "SCOPE",
              style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, fontWeight = FontWeight.Bold),
              color = palette.textMuted,
            )
            val openCount = uiState.openTabPaths.size.coerceAtLeast(1)
            val allCount = uiState.workspace?.files?.size ?: 1
            listOf(
              0 to "This Tab",
              1 to "Open Tabs ($openCount)",
              2 to "All Files ($allCount)",
            ).forEach { (scopeIdx, label) ->
              val selected = uiState.findScope == scopeIdx
              Surface(
                onClick = { onFindScopeChange(scopeIdx) },
                shape = RoundedCornerShape(6.dp),
                color = if (selected) palette.activePillBg else palette.editorBg,
                border =
                  BorderStroke(
                    0.75.dp,
                    if (selected) palette.activePillFg.copy(alpha = 0.35f) else palette.border.copy(alpha = 0.45f),
                  ),
                modifier = Modifier.testTag("editor_find_scope_$scopeIdx"),
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                  color = if (selected) palette.activePillFg else palette.textSecondary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
          }
        }
      }
    }

    val fileDiagnostics =
      remember(uiState.diagnostics, activeFile.path) {
        uiState.diagnostics.filter { it.filePath == activeFile.path }
      }
    val actionableDiagnostics =
      remember(fileDiagnostics) {
        fileDiagnostics.filter { it.severity != DiagnosticSeverity.INFO }.ifEmpty { fileDiagnostics }
      }

    var committedZoomPercent by remember {
      mutableIntStateOf(uiState.editorSettings.zoomPercent.coerceIn(60, 220))
    }
    LaunchedEffect(uiState.editorSettings.zoomPercent) {
      if (!hudHolder.isPinching) {
        val next = uiState.editorSettings.zoomPercent.coerceIn(60, 220)
        committedZoomPercent = next
        hudHolder.liveZoomPercent = next
      }
    }

    val latestOnZoomPercentChange by rememberUpdatedState(onZoomPercentChange)
    var showZoomHud by remember { mutableStateOf(false) }
    var zoomHudBump by remember { mutableIntStateOf(0) }
    var zoomPresetMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(zoomHudBump) {
      if (zoomHudBump > 0) {
        showZoomHud = true
        delay(1000L)
        showZoomHud = false
      }
    }

    val hasCustomBg = rememberHasActiveEditorBackground(uiState.editorSettings)

    Box(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .clipToBounds(),
    ) {
      if (hasCustomBg) {
        EditorBackgroundLayer(
          settings = uiState.editorSettings,
          modifier = Modifier.matchParentSize(),
        )
      }

      EdgeCodeEditorSurface(
        filePath = activeFile.path,
        externalTfv = syncedTfv,
        editorRevision = uiState.editorRevision,
        originalContent = activeFile.originalContent,
        language = activeFile.language,
        editorSettings = uiState.editorSettings,
        zoomPercent = committedZoomPercent,
        hasCustomBg = hasCustomBg,
        readOnly = activeFile.isVirtualReadOnly,
        diagnostics = fileDiagnostics,
        symbols = symbols,
        findMatches = activeFindMatches,
        activeFindMatchIndex = activeFindMatchIndex,
        controller = editorController,
        onDebouncedSync = { synced -> latestOnCodeChange(synced) },
        onTelemetryUpdate = { telemetry -> hudHolder.telemetry = telemetry },
        onPinchZoomChanged = { zoom, pinching ->
          hudHolder.liveZoomPercent = zoom
          hudHolder.isPinching = pinching
        },
        onPinchZoomCommitted = { finalZoom ->
          hudHolder.liveZoomPercent = finalZoom
          hudHolder.isPinching = false
          val prev = committedZoomPercent
          committedZoomPercent = finalZoom
          zoomHudBump++
          if (finalZoom != prev) {
            latestOnZoomPercentChange(finalZoom)
          }
        },
        onRequestSmartRename = { ident -> flushThen { onRequestSmartRename(ident) } },
        onToggleFindBar = { flushThen(onToggleFindBar) },
        modifier = Modifier.fillMaxSize(),
      )

      FloatingSyntaxDiagnosticPill(
        visible = actionableDiagnostics.isNotEmpty() && !uiState.isEditorTopChromeHidden,
        filePath = activeFile.path,
        diagnostics = actionableDiagnostics,
        hudHolder = hudHolder,
        onJumpToLocation = { path, line, col ->
          flushThen { onJumpToLocation(path, line, col) }
        },
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .padding(top = if (uiState.editorSettings.stickyHeaders) 28.dp else 6.dp, end = 8.dp),
      )

      EditorZoomHud(
        visible = hudHolder.isPinching || showZoomHud,
        zoomPercentProvider = { hudHolder.liveZoomPercent },
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
      )

      if (!activeFile.isVirtualReadOnly &&
        !uiState.isEditorBottomBarsHidden &&
        activeFile.language in setOf(WorkerLanguage.JAVASCRIPT, WorkerLanguage.TYPESCRIPT, WorkerLanguage.JSON)
      ) {
        FloatingAutocompleteOverlay(
          filePath = activeFile.path,
          workspace = uiState.workspace,
          language = activeFile.language,
          hudHolder = hudHolder,
          editorController = editorController,
          onComputeCompletions = onComputeCompletions,
          onAcceptCompletion = onAcceptCompletion,
          modifier =
            Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
        )
      }
    }

    AnimatedVisibility(
      visible = !uiState.isEditorBottomBarsHidden,
      enter = slideInVertically(tween(180)) { it } + fadeIn(tween(160)),
      exit = slideOutVertically(tween(150)) { it } + fadeOut(tween(130)),
    ) {
      Column(modifier = Modifier.fillMaxWidth().background(palette.editorBg)) {
        HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.5.dp)
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(22.dp)
              .background(palette.editorBg)
              .padding(horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          EditorLiveStatusTelemetry(
            hudHolder = hudHolder,
            language = activeFile.language,
          )
          Spacer(Modifier.weight(1f))

          EditorZoomControlCluster(
            zoomPercentProvider = { hudHolder.liveZoomPercent },
            zoomPresetMenuOpen = zoomPresetMenuOpen,
            onZoomPresetMenuChange = { zoomPresetMenuOpen = it },
            onSetZoom = { next ->
              val clamped = next.coerceIn(60, 220)
              val prev = committedZoomPercent.toFloat().coerceAtLeast(1f)
              val ratio = clamped.toFloat() / prev
              committedZoomPercent = clamped
              hudHolder.liveZoomPercent = clamped
              onZoomPercentChange(clamped)
              zoomHudBump++
              editorController.scaleScrollBy(ratio)
            },
            onStepZoom = { delta ->
              val prev = committedZoomPercent.toFloat().coerceAtLeast(1f)
              val next = (committedZoomPercent + delta).coerceIn(60, 220)
              val ratio = next.toFloat() / prev
              committedZoomPercent = next
              hudHolder.liveZoomPercent = next
              onZoomPercentChange(next)
              zoomHudBump++
              editorController.scaleScrollBy(ratio)
            },
          )

          val errCount = uiState.diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
          val warnCount = uiState.diagnostics.size - errCount
          IconRevealButton(
            icon =
              if (uiState.diagnostics.isEmpty()) Icons.Outlined.CheckCircleOutline
              else Icons.Outlined.WarningAmber,
            revealedLabel =
              when {
                errCount > 0 -> "$errCount err"
                warnCount > 0 -> "$warnCount warn"
                else -> "Clean"
              },
            minimal = true,
            statusDotColor =
              when {
                errCount > 0 -> RoseError
                warnCount > 0 -> AmberWarn
                else -> EmeraldLive
              },
            onClick = { flushThen(onOpenProblems) },
            height = 20.dp,
            iconSize = 13.dp,
            tag = "editor_status_diagnostics_button",
          )

          IconButton(
            onClick = onHideEditorBottomBars,
            modifier = Modifier.size(20.dp).testTag("hide_editor_bottom_bars_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.KeyboardArrowDown,
              contentDescription = "Hide editor bottom bars",
              tint = palette.textMuted,
              modifier = Modifier.size(14.dp),
            )
          }
        }

        if (!activeFile.isVirtualReadOnly) {
          HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.5.dp)
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(palette.chromeBg)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
              IconButton(
                onClick = { if (!editorController.undo()) flushThen(onUndo) },
                modifier = Modifier.size(26.dp).testTag("editor_undo"),
              ) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
              }
              IconButton(
                onClick = { if (!editorController.redo()) flushThen(onRedo) },
                modifier = Modifier.size(26.dp).testTag("editor_redo"),
              ) {
                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
              }
            }

            VerticalDivider(
              color = palette.border.copy(alpha = 0.45f),
              modifier = Modifier.height(16.dp).padding(horizontal = 4.dp),
            )

            Row(
              modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AccessoryKey("TAB") { editorController.insertTab(uiState.editorSettings.tabSize) }
              AccessoryKey("//") { editorController.toggleLineComment() }
              AccessoryKey("↑") { editorController.moveLineUp() }
              AccessoryKey("↓") { editorController.moveLineDown() }
              AccessoryKey("⧉") { editorController.duplicateLine() }
              AccessoryKey("FOLD") { editorController.toggleFoldAtCurrentLine() }
              AccessoryKey("BM") { editorController.toggleBookmark() }
              AccessoryKey("CLIP") { editorController.openPasteHistory() }
              AccessoryKey("{ }") { editorController.insertSnippet("{}", 1) }
              AccessoryKey("( )") { editorController.insertSnippet("()", 1) }
              AccessoryKey("[ ]") { editorController.insertSnippet("[]", 1) }
              AccessoryKey("=>") { editorController.insertSnippet(" => ", 0) }
              AccessoryKey(";") { editorController.insertSnippet(";", 0) }
              AccessoryKey(":") { editorController.insertSnippet(": ", 0) }
              AccessoryKey("\"") { editorController.insertSnippet("\"\"", 1) }
              AccessoryKey("'") { editorController.insertSnippet("''", 1) }
              AccessoryKey("`") { editorController.insertSnippet("``", 1) }
              AccessoryKey("=") { editorController.insertSnippet(" = ", 0) }
              AccessoryKey(".") { editorController.insertSnippet(".", 0) }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EditorLiveStatusTelemetry(
  hudHolder: EditorLiveHudHolder,
  language: WorkerLanguage,
) {
  val palette = LocalIdePalette.current
  val t = hudHolder.telemetry
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = "Ln ${t.line}:${t.col}",
      fontFamily = JetBrainsMono,
      fontSize = 9.5.sp,
      color = palette.textSecondary,
      maxLines = 1,
    )
    Text("·", fontSize = 9.5.sp, color = palette.textMuted)
    Text(
      text =
        when (language) {
          WorkerLanguage.JAVASCRIPT -> "JS"
          WorkerLanguage.TYPESCRIPT -> "TS"
          WorkerLanguage.JSON -> "JSON"
          WorkerLanguage.TEXT -> "TXT"
        },
      fontFamily = JetBrainsMono,
      fontSize = 9.5.sp,
      fontWeight = FontWeight.SemiBold,
      color = palette.textMuted,
      maxLines = 1,
    )
    Text("·", fontSize = 9.5.sp, color = palette.textMuted)
    Text(
      text = "${t.lineCount}L",
      fontFamily = JetBrainsMono,
      fontSize = 9.5.sp,
      color = palette.textMuted,
      maxLines = 1,
    )
  }
}

@Composable
private fun FloatingSyntaxDiagnosticPill(
  visible: Boolean,
  filePath: String,
  diagnostics: List<DiagnosticItem>,
  hudHolder: EditorLiveHudHolder,
  onJumpToLocation: (String, Int, Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  var selectedDiagIndex by remember(filePath) { mutableIntStateOf(0) }
  val activeLine = hudHolder.telemetry.line
  val activeLineDiagIdx =
    remember(diagnostics, activeLine) {
      diagnostics.indexOfFirst { it.line == activeLine }
    }
  LaunchedEffect(activeLineDiagIdx) {
    if (activeLineDiagIdx >= 0) {
      selectedDiagIndex = activeLineDiagIdx
    }
  }

  AnimatedVisibility(
    visible = visible && diagnostics.isNotEmpty(),
    enter = fadeIn(tween(140)),
    exit = fadeOut(tween(110)),
    modifier = modifier,
  ) {
    if (diagnostics.isEmpty()) return@AnimatedVisibility
    val safeIdx = selectedDiagIndex.coerceIn(0, diagnostics.lastIndex)
    val currentDiag = diagnostics[safeIdx]
    val isErr = currentDiag.severity == DiagnosticSeverity.ERROR
    val isWarn = currentDiag.severity == DiagnosticSeverity.WARNING
    val accent =
      when {
        isErr -> RoseError
        isWarn -> AmberWarn
        else -> palette.activePillFg
      }

    Surface(
      color = palette.chromeSurface,
      shape = RoundedCornerShape(8.dp),
      border = BorderStroke(0.75.dp, accent.copy(alpha = 0.55f)),
      shadowElevation = 2.dp,
      modifier =
        Modifier
          .widthIn(max = 310.dp)
          .clip(RoundedCornerShape(10.dp))
          .clickable { onJumpToLocation(currentDiag.filePath, currentDiag.line, currentDiag.column) }
          .testTag("editor_syntax_catcher_bar"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = if (isErr) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
          contentDescription = currentDiag.severity.name,
          tint = accent,
          modifier = Modifier.size(13.dp),
        )
        Text(
          text = "Ln ${currentDiag.line}:${currentDiag.column}",
          fontFamily = JetBrainsMono,
          fontSize = 9.5.sp,
          fontWeight = FontWeight.Bold,
          color = accent,
          maxLines = 1,
        )
        Text(
          text = currentDiag.message,
          fontFamily = JetBrainsMono,
          fontSize = 10.sp,
          color = palette.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (diagnostics.size > 1) {
          Text(
            text = "${safeIdx + 1}/${diagnostics.size}",
            fontFamily = JetBrainsMono,
            fontSize = 9.5.sp,
            color = palette.textMuted,
          )
          IconButton(
            onClick = {
              val prev = if (safeIdx <= 0) diagnostics.lastIndex else safeIdx - 1
              selectedDiagIndex = prev
              val d = diagnostics[prev]
              onJumpToLocation(d.filePath, d.line, d.column)
            },
            modifier = Modifier.size(22.dp).testTag("syntax_prev_button"),
          ) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous syntax issue", tint = accent, modifier = Modifier.size(14.dp))
          }
          IconButton(
            onClick = {
              val next = (safeIdx + 1) % diagnostics.size
              selectedDiagIndex = next
              val d = diagnostics[next]
              onJumpToLocation(d.filePath, d.line, d.column)
            },
            modifier = Modifier.size(22.dp).testTag("syntax_next_button"),
          ) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next syntax issue", tint = accent, modifier = Modifier.size(14.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun FloatingAutocompleteOverlay(
  filePath: String,
  workspace: WorkerWorkspace?,
  language: WorkerLanguage,
  hudHolder: EditorLiveHudHolder,
  editorController: EdgeEditorController,
  onComputeCompletions: (filePath: String, text: CharSequence, cursor: Int, language: WorkerLanguage) -> List<EdgeCompletionItem>,
  onAcceptCompletion: (EdgeCompletionItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val telemetry = hudHolder.telemetry
  val word = telemetry.wordBeforeCursor
  val cursorOffset = telemetry.cursorOffset
  val completions =
    remember(filePath, workspace?.bindings, language, word, cursorOffset) {
      if (word.isEmpty()) {
        emptyList()
      } else {
        val docText = editorController.currentCharSequence()
        val engineItems = onComputeCompletions(filePath, docText, cursorOffset, language)
        engineItems.ifEmpty {
          WranglerSyncParser.buildWorkersCompletions(workspace, language, word).map { s ->
            EdgeCompletionItem(
              label = s.label,
              insertText = s.insertText,
              kind = if (s.label.startsWith("env.")) EdgeCompletionKind.BINDING else EdgeCompletionKind.API,
              detail = s.detail,
              cursorBackOffset = s.cursorBackOffset,
            )
          }
        }
      }
    }

  AnimatedVisibility(
    visible = completions.isNotEmpty(),
    enter = fadeIn(tween(100)),
    exit = fadeOut(tween(80)),
    modifier = modifier,
  ) {
    EdgeGlassCompletionBar(
      items = completions,
      queryPrefix = word,
      onSelect = { item ->
        if (item.replaceStart >= 0 && item.replaceEnd >= item.replaceStart) {
          editorController.applyCompletionEdit(
            replaceStart = item.replaceStart,
            replaceEnd = item.replaceEnd,
            insertText = item.insertText,
            cursorBack = item.cursorBackOffset,
          )
        } else if (word.isNotEmpty() && item.label.startsWith(word, ignoreCase = true)) {
          editorController.replaceBeforeCursor(word.length, item.insertText, item.cursorBackOffset)
        } else {
          editorController.insertSnippet(item.insertText, item.cursorBackOffset)
        }
        onAcceptCompletion(item)
      },
    )
  }
}

@Composable
private fun AccessoryKey(label: String, onClick: () -> Unit) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(6.dp),
    color = palette.chromeSurface,
    border = if (isV2) null else BorderStroke(0.5.dp, palette.border.copy(alpha = 0.45f)),
  ) {
    Box(
      modifier = Modifier.height(24.dp).padding(horizontal = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = palette.textPrimary,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

@Composable
private fun EmptyWorkspaceWelcome(
  hasWorkers: Boolean,
  onOpenExplorer: () -> Unit,
  onShowNewWorkerDialog: () -> Unit,
  onOpenWorkerPreview: () -> Unit,
  onCreateNewFile: () -> Unit,
  onRunLocalScript: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 400.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_empty_workspace),
        contentDescription = stringResource(R.string.cd_empty_workspace),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(128.dp).clip(RoundedCornerShape(if (isV2) 30.dp else 28.dp)),
      )
      Text(
        text = "EdgeCode",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconRevealButton(
          icon = Icons.Outlined.PlayArrow,
          revealedLabel = "Run CLI",
          onClick = onRunLocalScript,
          height = 46.dp,
          tag = "welcome_local_run_button",
        )
        IconRevealButton(
          icon = Icons.Outlined.Visibility,
          revealedLabel = "Test Live Worker",
          onClick = onOpenWorkerPreview,
          height = 46.dp,
          tag = "welcome_worker_preview_button",
        )
        IconRevealButton(
          icon = Icons.Outlined.NoteAdd,
          revealedLabel = "New File",
          onClick = onCreateNewFile,
          height = 46.dp,
          tag = "welcome_new_file_button",
        )
        if (hasWorkers) {
          IconRevealButton(
            icon = Icons.Outlined.FolderOpen,
            revealedLabel = "Explorer",
            onClick = onOpenExplorer,
            height = 46.dp,
            tag = "welcome_explorer_button",
          )
        }
        IconRevealButton(
          icon = Icons.Outlined.Add,
          revealedLabel = "New Worker",
          accentContainer = true,
          onClick = onShowNewWorkerDialog,
          height = 46.dp,
          tag = "welcome_new_worker_button",
        )
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
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
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
      modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      IconRevealButton(
        icon = Icons.Outlined.FolderOpen,
        revealedLabel = "Files",
        minimal = true,
        onClick = onOpenExplorer,
        height = 26.dp,
        iconSize = 16.dp,
        tag = "editor_files_button",
      )
      if (uiState.openTabPaths.isEmpty()) {
        Spacer(Modifier.weight(1f))
      } else {
        LazyRow(
          state = tabListState,
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(3.dp),
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
                shape = RoundedCornerShape(6.dp),
                color = if (isActive) palette.editorBg else Color.Transparent,
                border =
                  if (isActive && !isV2) BorderStroke(0.5.dp, palette.border.copy(alpha = 0.55f))
                  else null,
                modifier =
                  Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(
                      onClick = { onSelectTab(path) },
                      onLongClick = { contextMenuPath = path },
                    )
                    .testTag("editor_tab_$path"),
              ) {
                Column {
                  Row(
                    modifier = Modifier.padding(start = 8.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    FileTypeBadge(path = path)
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      if (parentHint.isNotEmpty()) {
                        Text(
                          text = parentHint,
                          style = MaterialTheme.typography.labelSmall,
                          fontSize = 10.sp,
                          color = palette.textMuted,
                          maxLines = 1,
                        )
                      }
                      Text(
                        text = fileName,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.5.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) palette.textPrimary else palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      if (file?.isEntryPoint == true) {
                        Surface(color = palette.activePillBg, shape = RoundedCornerShape(3.dp)) {
                          Text(
                            text = "MAIN",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.activePillFg,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
                          )
                        }
                      }
                      if (file?.isDirty == true) {
                        Box(
                          modifier =
                            Modifier
                              .size(6.dp)
                              .clip(CircleShape)
                              .background(palette.activePillFg)
                              .testTag("dirty_dot_$path"),
                        )
                      }
                    }
                    IconButton(
                      onClick = { onCloseTab(path) },
                      modifier = Modifier.size(18.dp).testTag("close_tab_$path"),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "${stringResource(R.string.cd_close_tab)} $fileName",
                        tint = palette.textMuted,
                        modifier = Modifier.size(12.dp),
                      )
                    }
                  }
                  if (isActive) {
                    Box(
                      modifier =
                        Modifier
                          .fillMaxWidth()
                          .height(1.5.dp)
                          .background(palette.ctaBg),
                    )
                  }
                }
              }

              EdgeMenu(
                expanded = contextMenuPath == path,
                onDismissRequest = { contextMenuPath = null },
                minWidth = 224.dp,
                maxWidth = 276.dp,
              ) {
                EdgeMenuHeader(
                  title = file?.fileName ?: path.substringAfterLast('/'),
                  subtitle = path,
                  leadingContent = { FileTypeBadge(path = path) },
                )
                EdgeMenuItem(
                  label = "Close Tab",
                  icon = Icons.Outlined.Close,
                  onClick = {
                    contextMenuPath = null
                    onCloseTab(path)
                  },
                )
                if (uiState.openTabPaths.size > 1) {
                  EdgeMenuItem(
                    label = "Close Other Tabs",
                    icon = Icons.Outlined.DeleteSweep,
                    onClick = {
                      contextMenuPath = null
                      onCloseOtherTabs(path)
                    },
                  )
                }
                EdgeMenuItem(
                  label = "Close Saved Tabs",
                  icon = Icons.Outlined.CheckCircleOutline,
                  onClick = {
                    contextMenuPath = null
                    onCloseSavedTabs()
                  },
                )
                EdgeMenuItem(
                  label = "Close All Tabs",
                  icon = Icons.Outlined.DeleteSweep,
                  tone = EdgeMenuTone.DANGER,
                  onClick = {
                    contextMenuPath = null
                    onCloseAllTabs()
                  },
                )
                val canSetEntry =
                  file != null &&
                    !file.isVirtualReadOnly &&
                    !file.isEntryPoint &&
                    file.language in setOf(WorkerLanguage.JAVASCRIPT, WorkerLanguage.TYPESCRIPT)
                val canRevert = file != null && file.isDirty && !file.isVirtualReadOnly
                if (canSetEntry || canRevert) {
                  EdgeMenuDivider()
                }
                if (canSetEntry) {
                  EdgeMenuItem(
                    label = "Set as Entry Point",
                    icon = Icons.Outlined.StarOutline,
                    tone = EdgeMenuTone.ACCENT,
                    badgeText = "main",
                    onClick = {
                      contextMenuPath = null
                      onSetEntryModule(path)
                    },
                  )
                }
                if (canRevert) {
                  EdgeMenuItem(
                    label = "Revert to Baseline",
                    icon = Icons.Outlined.Restore,
                    tone = EdgeMenuTone.DANGER,
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

      if (uiState.openTabPaths.size > 1) {
        IconButton(
          onClick = { onSelectAdjacentTab(false) },
          modifier = Modifier.size(24.dp).testTag("tab_cycle_prev"),
        ) {
          Icon(Icons.Outlined.KeyboardArrowLeft, contentDescription = "Previous open tab", tint = palette.textSecondary, modifier = Modifier.size(15.dp))
        }
        IconButton(
          onClick = { onSelectAdjacentTab(true) },
          modifier = Modifier.size(24.dp).testTag("tab_cycle_next"),
        ) {
          Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "Next open tab", tint = palette.textSecondary, modifier = Modifier.size(15.dp))
        }
      }

      IconButton(
        onClick = onCreateNewFile,
        modifier = Modifier.size(24.dp).testTag("tab_bar_new_file"),
      ) {
        Icon(Icons.Outlined.Add, contentDescription = "New file tab", tint = palette.textSecondary, modifier = Modifier.size(15.dp))
      }

      Box {
        Surface(
          onClick = {
            switcherQuery = ""
            tabSwitcherOpen = true
          },
          shape = RoundedCornerShape(6.dp),
          color = palette.chromeSurface,
          modifier = Modifier.testTag("tab_switcher_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = "${uiState.openTabPaths.size}/${workspaceFiles.size}",
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Medium,
              color = palette.textPrimary,
            )
            Icon(
              Icons.Outlined.KeyboardArrowDown,
              contentDescription = "Open tabs and project file switcher",
              tint = palette.textSecondary,
              modifier = Modifier.size(13.dp),
            )
          }
        }

        EdgeMenu(
          expanded = tabSwitcherOpen,
          onDismissRequest = { tabSwitcherOpen = false },
          minWidth = 264.dp,
          maxWidth = 332.dp,
          modifier = Modifier.heightIn(max = 440.dp),
        ) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.editorBg)
                .border(0.75.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              Icons.Outlined.Search,
              contentDescription = null,
              tint = palette.textMuted,
              modifier = Modifier.size(14.dp),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
              if (switcherQuery.isEmpty()) {
                Text(
                  text = "Jump to file…",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.5.sp,
                  color = palette.textMuted,
                  maxLines = 1,
                )
              }
              BasicTextField(
                value = switcherQuery,
                onValueChange = { switcherQuery = it },
                singleLine = true,
                textStyle =
                  TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 11.5.sp,
                    color = palette.textPrimary,
                  ),
                cursorBrush = SolidColor(palette.activePillFg),
                modifier = Modifier.fillMaxWidth(),
              )
            }
            if (switcherQuery.isNotEmpty()) {
              Box(
                modifier =
                  Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { switcherQuery = "" },
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  Icons.Outlined.Close,
                  contentDescription = "Clear filter",
                  tint = palette.textMuted,
                  modifier = Modifier.size(12.dp),
                )
              }
            }
          }

          val openMatches =
            uiState.openTabPaths.filter { p ->
              switcherQuery.isBlank() || p.contains(switcherQuery, ignoreCase = true)
            }
          val closedProjectMatches =
            workspaceFiles
              .map { it.path }
              .filter { it !in uiState.openTabPaths && (switcherQuery.isBlank() || it.contains(switcherQuery, ignoreCase = true)) }

          if (openMatches.isNotEmpty()) {
            EdgeMenuSection("Open Tabs (${openMatches.size})")
            openMatches.forEach { path ->
              val isActive = path == uiState.activeFilePath
              val isDirty = workspaceFiles.firstOrNull { it.path == path }?.isDirty == true
              EdgeMenuItem(
                label = path,
                monospaced = true,
                selected = isActive,
                leadingContent = { FileTypeBadge(path = path) },
                trailingContent =
                  if (isDirty) {
                    {
                      Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AmberWarn))
                    }
                  } else null,
                onClick = {
                  tabSwitcherOpen = false
                  onSelectTab(path)
                },
              )
            }
          }

          if (closedProjectMatches.isNotEmpty()) {
            if (openMatches.isNotEmpty()) EdgeMenuDivider()
            EdgeMenuSection("Workspace Files (${closedProjectMatches.size})")
            closedProjectMatches.forEach { path ->
              EdgeMenuItem(
                label = path,
                monospaced = true,
                leadingContent = { FileTypeBadge(path = path) },
                onClick = {
                  tabSwitcherOpen = false
                  onSelectTab(path)
                },
              )
            }
          }

          if (uiState.openTabPaths.isNotEmpty()) {
            EdgeMenuDivider()
            EdgeMenuItem(
              label = "Close Saved Tabs",
              icon = Icons.Outlined.CheckCircleOutline,
              onClick = {
                tabSwitcherOpen = false
                onCloseSavedTabs()
              },
            )
            EdgeMenuItem(
              label = "Close All Tabs",
              icon = Icons.Outlined.DeleteSweep,
              tone = EdgeMenuTone.DANGER,
              onClick = {
                tabSwitcherOpen = false
                onCloseAllTabs()
              },
            )
          }
        }
      }
    }
    HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.5.dp)
  }
}

@Composable
private fun AllTabsClosedState(
  workerName: String,
  files: List<com.example.data.remote.WorkerFile>,
  onOpenFile: (String) -> Unit,
  onCreateNewFile: () -> Unit,
  onOpenExplorer: () -> Unit,
  onRunLocalScript: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 360.dp),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = workerName,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = palette.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          IconRevealButton(
            icon = Icons.Outlined.PlayArrow,
            revealedLabel = "Run CLI",
            accentContainer = true,
            onClick = onRunLocalScript,
            height = 36.dp,
            tag = "all_tabs_closed_run_local",
          )
          IconRevealButton(
            icon = Icons.Outlined.Add,
            revealedLabel = "New File",
            onClick = onCreateNewFile,
            height = 36.dp,
          )
          IconRevealButton(
            icon = Icons.Outlined.FolderOpen,
            revealedLabel = "Files",
            onClick = onOpenExplorer,
            height = 36.dp,
          )
        }
      }
      Surface(
        color = palette.chromeSurface,
        shape = RoundedCornerShape(if (isV2) 24.dp else 18.dp),
        border = if (isV2) null else BorderStroke(1.dp, palette.border.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
          files.take(5).forEachIndexed { idx, file ->
            if (idx > 0) {
              HorizontalDivider(
                color = if (isV2) palette.border else palette.border.copy(alpha = 0.35f),
                thickness = if (isV2) 0.5.dp else 0.75.dp,
              )
            }
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { onOpenFile(file.path) }
                  .padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              FileTypeBadge(path = file.path)
              Text(
                text = file.path,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EditorZoomHud(
  visible: Boolean,
  zoomPercentProvider: () -> Int,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = modifier,
  ) {
    val zoomPercent = zoomPercentProvider()
    Surface(
      color = palette.glassElevated,
      shape = RoundedCornerShape(10.dp),
      border = rememberGlassBorderStroke(0.75.dp),
      shadowElevation = 1.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(Icons.Outlined.ZoomIn, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(14.dp))
        Text(
          text = "${zoomPercent}%",
          fontFamily = JetBrainsMono,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Bold,
          color = palette.textPrimary,
        )
      }
    }
  }
}

@Composable
private fun EditorZoomControlCluster(
  zoomPercentProvider: () -> Int,
  zoomPresetMenuOpen: Boolean,
  onZoomPresetMenuChange: (Boolean) -> Unit,
  onSetZoom: (Int) -> Unit,
  onStepZoom: (Int) -> Unit,
) {
  val palette = LocalIdePalette.current
  val displayZoomPercent = zoomPercentProvider()
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(1.dp),
  ) {
    IconButton(
      onClick = { onStepZoom(-10) },
      enabled = displayZoomPercent > 60,
      modifier = Modifier.size(20.dp).testTag("editor_zoom_out"),
    ) {
      Icon(
        Icons.Outlined.Remove,
        contentDescription = "Zoom out editor",
        tint = if (displayZoomPercent > 60) palette.textSecondary else palette.textMuted.copy(alpha = 0.4f),
        modifier = Modifier.size(12.dp),
      )
    }
    Box {
      Text(
        text = "${displayZoomPercent}%",
        fontFamily = JetBrainsMono,
        fontSize = 9.5.sp,
        fontWeight = if (displayZoomPercent != 100) FontWeight.Bold else FontWeight.Medium,
        color = if (displayZoomPercent != 100) palette.activePillFg else palette.textSecondary,
        modifier =
          Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onZoomPresetMenuChange(true) }
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .testTag("editor_zoom_label"),
      )
      EdgeMenu(
        expanded = zoomPresetMenuOpen,
        onDismissRequest = { onZoomPresetMenuChange(false) },
        minWidth = 152.dp,
        maxWidth = 186.dp,
      ) {
        EdgeMenuSection("Editor Zoom")
        listOf(75, 100, 125, 150, 175, 200).forEach { preset ->
          EdgeMenuCompactItem(
            label = "$preset%",
            badgeText = if (preset == 100) "Default" else null,
            selected = preset == displayZoomPercent,
            monospaced = true,
            onClick = {
              onZoomPresetMenuChange(false)
              onSetZoom(preset)
            },
          )
        }
      }
    }
    IconButton(
      onClick = { onStepZoom(10) },
      enabled = displayZoomPercent < 220,
      modifier = Modifier.size(20.dp).testTag("editor_zoom_in"),
    ) {
      Icon(
        Icons.Outlined.Add,
        contentDescription = "Zoom in editor",
        tint = if (displayZoomPercent < 220) palette.textSecondary else palette.textMuted.copy(alpha = 0.4f),
        modifier = Modifier.size(12.dp),
      )
    }
  }
}

