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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.NoteAdd
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.remote.DiagnosticSeverity
import com.example.data.remote.WorkerLanguage
import com.example.editor.BracketMatch
import com.example.editor.CodeAnalyzer
import com.example.editor.CodeEditHelper
import com.example.editor.CodeSymbol
import com.example.editor.CodeSyntaxVisualTransformation
import com.example.editor.MergeConflictResolver
import com.example.editor.WranglerSyncParser
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.edgeGlassDock
import com.example.ui.theme.edgeGlassSurface
import com.example.ui.theme.rememberGlassBorderStroke
import com.example.ui.viewmodel.IdeUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val activeFile = uiState.activeFile

  var localTfv by remember(activeFile?.path) {
    mutableStateOf(uiState.activeTextFieldValue)
  }
  val latestLocalTfv by rememberUpdatedState(localTfv)
  val latestVmTfv by rememberUpdatedState(uiState.activeTextFieldValue)
  val latestOnCodeChange by rememberUpdatedState(onCodeChange)

  fun flushThen(action: () -> Unit) {
    if (activeFile != null && !activeFile.isVirtualReadOnly && localTfv != latestVmTfv) {
      onCodeChange(localTfv)
    }
    action()
  }

  Column(modifier = modifier.fillMaxSize().background(palette.editorBg)) {
    if (uiState.workspace != null) {
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
        activeFile?.let { MergeConflictResolver.parseConflictMarkers(it.content) }.orEmpty()
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

    // Keep local editor buffer in sync with external ViewModel mutations (Undo/Redo/Format/Jump/Revert)
    LaunchedEffect(activeFile.path, uiState.editorRevision) {
      val vmVal = latestVmTfv
      if (localTfv != vmVal) {
        localTfv = vmVal
      }
    }

    // Debounce typing sync back to ViewModel so rapid keystrokes never trigger full-app recomposition
    LaunchedEffect(activeFile.path, localTfv) {
      if (!activeFile.isVirtualReadOnly && localTfv != latestVmTfv) {
        delay(110L)
        latestOnCodeChange(latestLocalTfv)
      }
    }

    // Flush any pending typed text when switching tabs, pausing app, or leaving the editor
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activeFile.path, lifecycleOwner) {
      val observer =
        LifecycleEventObserver { _, event ->
          if ((event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) &&
            !activeFile.isVirtualReadOnly &&
            latestLocalTfv != latestVmTfv
          ) {
            latestOnCodeChange(latestLocalTfv)
          }
        }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
        if (!activeFile.isVirtualReadOnly && latestLocalTfv != latestVmTfv) {
          latestOnCodeChange(latestLocalTfv)
        }
      }
    }

    val tfv = localTfv
    val isWranglerConfig =
      activeFile.path.equals("wrangler.jsonc", ignoreCase = true) ||
        activeFile.path.equals("wrangler.json", ignoreCase = true) ||
        activeFile.path.equals("wrangler.toml", ignoreCase = true)
    var showSymbolOutlineMenu by remember { mutableStateOf(false) }

    var symbols by remember(activeFile.path, activeFile.language) {
      mutableStateOf<List<CodeSymbol>>(emptyList())
    }
    LaunchedEffect(activeFile.path, activeFile.language, tfv.text) {
      if (activeFile.language == WorkerLanguage.TEXT) {
        symbols = emptyList()
        return@LaunchedEffect
      }
      val snapshot = tfv.text
      val lang = activeFile.language
      if (snapshot.length > 2_000) delay(320L)
      symbols =
        withContext(Dispatchers.Default) {
          WranglerSyncParser.extractCodeSymbols(snapshot, lang)
        }
    }

    // Tab-history navigation chevrons (< >) + Breadcrumb Strip
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.editorBg)
          .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = { flushThen(onNavigateTabBack) },
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
        onClick = { flushThen(onNavigateTabForward) },
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
        remember(uiState.workspace?.scriptName, activeFile.path) {
          buildList {
            add(uiState.workspace?.scriptName ?: "worker")
            addAll(activeFile.path.split('/'))
          }
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
          onClick = { flushThen(onOpenProblems) },
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
          onClick = { flushThen(onSyncWranglerToWorkspace) },
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
          onClick = { flushThen(onFormat) },
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

    // Compute active tab find match ranges for counter & live editor canvas highlights
    val activeFindMatches =
      remember(uiState.showFindBar, uiState.findQuery, uiState.findMatchCase, tfv.text) {
        if (!uiState.showFindBar || uiState.findQuery.isEmpty()) {
          emptyList()
        } else {
          val q = uiState.findQuery
          val ignoreCase = !uiState.findMatchCase
          val hits = ArrayList<IntRange>(32)
          var pos = 0
          while (pos <= tfv.text.length - q.length && hits.size < 500) {
            val found = tfv.text.indexOf(q, pos, ignoreCase = ignoreCase)
            if (found < 0) break
            hits.add(found until (found + q.length))
            pos = found + q.length
          }
          hits
        }
      }
    val activeFindMatchIndex =
      remember(activeFindMatches, tfv.selection) {
        if (activeFindMatches.isEmpty()) -1
        else {
          val selStart = tfv.selection.min
          val selEnd = tfv.selection.max
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
        tfv.text,
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
              if (p == activeFile.path) tfv.text
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

    // Collapsible Multi-File Tab Find & Replace Bar (Ultra-thin hairline style)
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
          // Row 1: Find input + Match Count + Case Toggle + Prev/Next + Close
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

            // Case-sensitive (Aa) toggle
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

          // Row 2: Replace Input + Single Replace + Replace All (when writable)
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

          // Row 3: Multi-File Scope Selector (This Tab / Open Tabs / All Files)
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
    val (activeLine, activeCol) =
      remember(tfv.text, tfv.selection.start) {
        CodeEditHelper.computeCursorLineAndCol(tfv.text, tfv.selection.start)
      }
    var selectedDiagIndex by remember(activeFile.path) { mutableIntStateOf(0) }
    val activeLineDiagIdx =
      remember(actionableDiagnostics, activeLine) {
        actionableDiagnostics.indexOfFirst { it.line == activeLine }
      }
    LaunchedEffect(activeLineDiagIdx) {
      if (activeLineDiagIdx >= 0) {
        selectedDiagIndex = activeLineDiagIdx
      }
    }
    val currentDiag =
      if (actionableDiagnostics.isNotEmpty()) {
        actionableDiagnostics[selectedDiagIndex.coerceIn(0, actionableDiagnostics.lastIndex)]
      } else null

    // Inline Syntax Catching Bar (live error & warning navigator)
    if (currentDiag != null) {
      val isErr = currentDiag.severity == DiagnosticSeverity.ERROR
      val isWarn = currentDiag.severity == DiagnosticSeverity.WARNING
      val stripBg =
        when {
          isErr -> RoseError.copy(alpha = 0.13f)
          isWarn -> AmberWarn.copy(alpha = 0.13f)
          else -> palette.activePillBg.copy(alpha = 0.7f)
        }
      val stripFg =
        when {
          isErr -> RoseError
          isWarn -> AmberWarn
          else -> palette.activePillFg
        }
      Surface(
        color = stripBg,
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { flushThen { onJumpToLocation(currentDiag.filePath, currentDiag.line, currentDiag.column) } }
            .testTag("editor_syntax_catcher_bar"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = if (isErr) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
            contentDescription = currentDiag.severity.name,
            tint = stripFg,
            modifier = Modifier.size(14.dp),
          )
          Surface(
            color = stripFg.copy(alpha = 0.18f),
            shape = RoundedCornerShape(4.dp),
          ) {
            Text(
              text = "Ln ${currentDiag.line}:${currentDiag.column}",
              fontFamily = JetBrainsMono,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = stripFg,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
          }
          Text(
            text = currentDiag.message,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          if (actionableDiagnostics.size > 1) {
            Text(
              text = "${(selectedDiagIndex.coerceIn(0, actionableDiagnostics.lastIndex)) + 1}/${actionableDiagnostics.size}",
              fontFamily = JetBrainsMono,
              fontSize = 10.sp,
              color = palette.textMuted,
            )
            IconButton(
              onClick = {
                val prev =
                  if (selectedDiagIndex <= 0) actionableDiagnostics.lastIndex else selectedDiagIndex - 1
                selectedDiagIndex = prev
                val d = actionableDiagnostics[prev]
                flushThen { onJumpToLocation(d.filePath, d.line, d.column) }
              },
              modifier = Modifier.size(24.dp).testTag("syntax_prev_button"),
            ) {
              Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous syntax issue", tint = stripFg, modifier = Modifier.size(16.dp))
            }
            IconButton(
              onClick = {
                val next = (selectedDiagIndex + 1) % actionableDiagnostics.size
                selectedDiagIndex = next
                val d = actionableDiagnostics[next]
                flushThen { onJumpToLocation(d.filePath, d.line, d.column) }
              },
              modifier = Modifier.size(24.dp).testTag("syntax_next_button"),
            ) {
              Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next syntax issue", tint = stripFg, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
      HorizontalDivider(color = stripFg.copy(alpha = 0.35f))
    }

    // Real Vector Typographic Pinch-to-Zoom + Pure VisualTransformation + TextLayoutResult Overlays
    var committedZoomPercent by remember {
      mutableIntStateOf(uiState.editorSettings.zoomPercent.coerceIn(60, 220))
    }
    var liveZoomPercent by remember {
      mutableFloatStateOf(committedZoomPercent.toFloat())
    }
    var isPinching by remember { mutableStateOf(false) }
    var gutterWidthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(uiState.editorSettings.zoomPercent) {
      if (!isPinching) {
        val next = uiState.editorSettings.zoomPercent.coerceIn(60, 220)
        committedZoomPercent = next
        liveZoomPercent = next.toFloat()
      }
    }

    val activeZoomPercent = if (isPinching) liveZoomPercent.coerceIn(60f, 220f) else committedZoomPercent.toFloat()
    val effectiveFontSizeSp = (uiState.editorSettings.fontSizeSp * (activeZoomPercent / 100f)).coerceIn(8f, 34f)
    val effectiveLineHeightSp = effectiveFontSizeSp * 1.48f
    val fontSize = effectiveFontSizeSp.sp
    val lineHeight = effectiveLineHeightSp.sp
    val sharedLineHeightStyle =
      remember {
        LineHeightStyle(
          alignment = LineHeightStyle.Alignment.Center,
          trim = LineHeightStyle.Trim.None,
        )
      }
    val sharedPlatformStyle = remember { PlatformTextStyle(includeFontPadding = false) }
    val density = LocalDensity.current
    val lineHeightPx = with(density) { lineHeight.toPx() }
    val topPaddingPx = with(density) { 8.dp.toPx() }
    val hPadPx = with(density) { 10.dp.toPx() }

    val scope = rememberCoroutineScope()
    val latestCommittedZoom by rememberUpdatedState(committedZoomPercent)
    val latestOnZoomPercentChange by rememberUpdatedState(onZoomPercentChange)
    val latestGutterWidthPx by rememberUpdatedState(gutterWidthPx)
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

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val lineStarts = remember(tfv.text) { CodeEditHelper.computeLineStarts(tfv.text) }
    val lineCount = lineStarts.size

    // Pure VisualTransformation keyed ONLY on (language, palette) -> never invalidated on cursor moves, diagnostics, or zoom
    val visualTransformation =
      remember(activeFile.language, palette) {
        CodeSyntaxVisualTransformation(
          language = activeFile.language,
          palette = palette,
        )
      }

    var textLayoutResult by remember(activeFile.path) { mutableStateOf<TextLayoutResult?>(null) }

    var bracketMatch by remember(activeFile.path) { mutableStateOf<BracketMatch?>(null) }
    LaunchedEffect(activeFile.path, activeFile.language, tfv.text, tfv.selection.start) {
      val text = tfv.text
      val cursor = tfv.selection.start
      if (activeFile.language == WorkerLanguage.TEXT || text.isEmpty()) {
        bracketMatch = null
        return@LaunchedEffect
      }
      val atCursor = cursor in text.indices && text[cursor] in charArrayOf('{', '}', '(', ')', '[', ']')
      val beforeCursor = (cursor - 1) in text.indices && text[cursor - 1] in charArrayOf('{', '}', '(', ')', '[', ']')
      if (!atCursor && !beforeCursor) {
        bracketMatch = null
        return@LaunchedEffect
      }
      val lang = activeFile.language
      bracketMatch =
        if (text.length > 4_000) {
          delay(35L)
          withContext(Dispatchers.Default) { CodeAnalyzer.findMatchingBracket(text, cursor, lang) }
        } else {
          CodeAnalyzer.findMatchingBracket(text, cursor, lang)
        }
    }

    val lineSeverityMap =
      remember(fileDiagnostics) {
        val map = HashMap<Int, DiagnosticSeverity>()
        fileDiagnostics.forEach { d ->
          val existing = map[d.line]
          if (existing == null || d.severity.ordinal < existing.ordinal) {
            map[d.line] = d.severity
          }
        }
        map
      }

    val rawGutterText =
      remember(lineCount) {
        buildString(lineCount * 4) {
          for (n in 1..lineCount) {
            if (n > 1) append('\n')
            append(n)
          }
        }
      }

    BoxWithConstraints(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .clipToBounds()
          .pointerInput(Unit) {
            awaitEachGesture {
              awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
              var activePinch = false
              var workingZoom = latestCommittedZoom.toFloat().coerceIn(60f, 220f)
              var lastCentroid = Offset.Zero
              do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount >= 2) {
                  if (!activePinch) {
                    activePinch = true
                    isPinching = true
                    workingZoom = latestCommittedZoom.toFloat().coerceIn(60f, 220f)
                    liveZoomPercent = workingZoom
                    val initialCentroid = event.calculateCentroid(useCurrent = false)
                    lastCentroid = if (initialCentroid != Offset.Unspecified) initialCentroid else Offset.Zero
                  }
                  val zoomDelta = event.calculateZoom()
                  val centroid = event.calculateCentroid(useCurrent = true)
                  if (centroid != Offset.Unspecified) {
                    lastCentroid = centroid
                  }
                  if (zoomDelta != 1f && zoomDelta.isFinite()) {
                    val nextZoom = (workingZoom * zoomDelta).coerceIn(60f, 220f)
                    if (kotlin.math.abs(nextZoom - workingZoom) >= 0.05f) {
                      val stepRatio = nextZoom / workingZoom.coerceAtLeast(1f)
                      val focalX = ((lastCentroid.x - latestGutterWidthPx) - hPadPx).coerceAtLeast(0f)
                      val focalY = (lastCentroid.y - topPaddingPx).coerceAtLeast(0f)
                      val curScrollX = horizontalScroll.value
                      val curScrollY = verticalScroll.value
                      workingZoom = nextZoom
                      liveZoomPercent = nextZoom
                      scope.launch {
                        val targetY = ((curScrollY + focalY) * stepRatio - focalY).roundToInt().coerceAtLeast(0)
                        val targetX = ((curScrollX + focalX) * stepRatio - focalX).roundToInt().coerceAtLeast(0)
                        verticalScroll.scrollTo(targetY)
                        horizontalScroll.scrollTo(targetX)
                      }
                    }
                  }
                  event.changes.forEach { it.consume() }
                } else if (activePinch) {
                  val anchor = latestCommittedZoom
                  val finalZoom = workingZoom.roundToInt().coerceIn(60, 220)
                  isPinching = false
                  liveZoomPercent = finalZoom.toFloat()
                  activePinch = false
                  zoomHudBump++
                  if (finalZoom != anchor) {
                    committedZoomPercent = finalZoom
                    latestOnZoomPercentChange(finalZoom)
                  }
                }
              } while (event.changes.any { it.pressed })

              if (activePinch) {
                val anchor = latestCommittedZoom
                val finalZoom = workingZoom.roundToInt().coerceIn(60, 220)
                isPinching = false
                liveZoomPercent = finalZoom.toFloat()
                zoomHudBump++
                if (finalZoom != anchor) {
                  committedZoomPercent = finalZoom
                  latestOnZoomPercentChange(finalZoom)
                }
              }
            }
          },
    ) {
      val minEditorHeight = maxHeight
      val hasCustomBg = rememberHasActiveEditorBackground(uiState.editorSettings)

      // Custom Editor Background Image / Ambient Backdrop (with live Opacity, Zoom & Scrim)
      if (hasCustomBg) {
        EditorBackgroundLayer(
          settings = uiState.editorSettings,
          modifier = Modifier.matchParentSize(),
        )
      }

      Row(
        modifier =
          Modifier
            .fillMaxSize()
            .verticalScroll(verticalScroll),
      ) {
        if (uiState.editorSettings.showLineNumbers) {
          val scalePad = (activeZoomPercent / 100f).coerceIn(0.6f, 2.2f)
          val gutterMinWidth =
            (when {
              lineCount >= 1000 -> 46f
              lineCount >= 100 -> 38f
              else -> 32f
            } * scalePad).dp
          Box(
            modifier =
              Modifier
                .widthIn(min = gutterMinWidth)
                .heightIn(min = minEditorHeight)
                .onSizeChanged { gutterWidthPx = it.width.toFloat() }
                .background(if (hasCustomBg) palette.editorGutterBg.copy(alpha = 0.62f) else palette.editorGutterBg)
                .drawBehind {
                  val layout = textLayoutResult
                  val canUseLayout =
                    !uiState.editorSettings.wordWrap && layout != null && layout.lineCount >= lineCount
                  fun lineBounds(oneBasedLine: Int): Pair<Float, Float> {
                    val idx = (oneBasedLine - 1).coerceAtLeast(0)
                    return if (canUseLayout && idx < layout!!.lineCount) {
                      val top = topPaddingPx + layout.getLineTop(idx)
                      val h = (layout.getLineBottom(idx) - layout.getLineTop(idx)).coerceAtLeast(1f)
                      top to h
                    } else {
                      (topPaddingPx + idx * lineHeightPx) to lineHeightPx
                    }
                  }
                  lineSeverityMap.forEach { (lineNum, sev) ->
                    if (sev != DiagnosticSeverity.INFO) {
                      val (errY, errH) = lineBounds(lineNum)
                      if (errY < size.height) {
                        val c = if (sev == DiagnosticSeverity.ERROR) RoseError else AmberWarn
                        drawRect(
                          color = c.copy(alpha = 0.16f),
                          topLeft = Offset(0f, errY),
                          size = Size(size.width, errH),
                        )
                        drawRect(
                          color = c,
                          topLeft = Offset(0f, errY),
                          size = Size(3.dp.toPx(), errH),
                        )
                      }
                    }
                  }
                  val (activeY, activeH) = lineBounds(activeLine)
                  if (activeY < size.height) {
                    drawRect(
                      color = palette.editorActiveLineBg,
                      topLeft = Offset(0f, activeY),
                      size = Size(size.width, activeH),
                    )
                  }
                }
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
            contentAlignment = Alignment.TopEnd,
          ) {
            Text(
              text = rawGutterText,
              style =
                TextStyle(
                  fontFamily = JetBrainsMono,
                  fontSize = fontSize,
                  lineHeight = lineHeight,
                  fontFeatureSettings = "tnum",
                  platformStyle = sharedPlatformStyle,
                  lineHeightStyle = sharedLineHeightStyle,
                  color = palette.textMuted,
                  textAlign = TextAlign.End,
                ),
            )
          }

          VerticalDivider(color = palette.border.copy(alpha = 0.7f), thickness = 1.dp)
        }

        val editorModifier =
          Modifier
            .weight(1f)
            .heightIn(min = minEditorHeight)
            .then(
              if (!uiState.editorSettings.wordWrap) Modifier.horizontalScroll(horizontalScroll)
              else Modifier
            )
            .drawBehind {
              val layout = textLayoutResult
              if (layout != null && layout.layoutInput.text.length == tfv.text.length) {
                val docLen = layout.layoutInput.text.length
                val safeCursor = tfv.selection.start.coerceIn(0, docLen)
                val curVisualLine = layout.getLineForOffset(safeCursor)
                val curTop = topPaddingPx + layout.getLineTop(curVisualLine)
                val curBottom = topPaddingPx + layout.getLineBottom(curVisualLine)
                drawRect(
                  color = palette.editorActiveLineBg.copy(alpha = 0.55f),
                  topLeft = Offset(0f, curTop),
                  size = Size(size.width, (curBottom - curTop).coerceAtLeast(1f)),
                )

                for (diag in fileDiagnostics) {
                  if (diag.severity == DiagnosticSeverity.INFO) continue
                  val lineIdx = (diag.line - 1).coerceIn(0, lineStarts.lastIndex)
                  val lineStart = lineStarts[lineIdx]
                  val nextStart = if (lineIdx + 1 < lineStarts.size) lineStarts[lineIdx + 1] else docLen
                  val lineEnd =
                    if (nextStart > lineStart && tfv.text[nextStart - 1] == '\n') nextStart - 1
                    else nextStart
                  val isErr = diag.severity == DiagnosticSeverity.ERROR
                  val diagColor = if (isErr) RoseError else AmberWarn

                  val visualLine = layout.getLineForOffset(lineStart.coerceIn(0, docLen))
                  val lTop = topPaddingPx + layout.getLineTop(visualLine)
                  val lBottom = topPaddingPx + layout.getLineBottom(visualLine)
                  drawRect(
                    color = diagColor.copy(alpha = if (isErr) 0.08f else 0.06f),
                    topLeft = Offset(0f, lTop),
                    size = Size(size.width, (lBottom - lTop).coerceAtLeast(1f)),
                  )

                  if (lineStart < lineEnd && docLen > 0) {
                    val rawStart = (lineStart + (diag.column - 1).coerceAtLeast(0)).coerceIn(lineStart, lineEnd - 1)
                    val rawEnd = (rawStart + diag.length.coerceAtLeast(1)).coerceIn(rawStart + 1, lineEnd)
                    val startBox = layout.getBoundingBox(rawStart.coerceIn(0, docLen - 1))
                    val endBox = layout.getBoundingBox((rawEnd - 1).coerceIn(0, docLen - 1))
                    val uLeft = hPadPx + startBox.left
                    val uRight =
                      if (endBox.top == startBox.top) hPadPx + endBox.right
                      else hPadPx + startBox.right.coerceAtLeast(startBox.left + 8.dp.toPx())
                    val uWidth = (uRight - uLeft).coerceAtLeast(6.dp.toPx())
                    val uY = topPaddingPx + startBox.bottom - 1.5.dp.toPx()
                    drawRect(
                      color = diagColor.copy(alpha = 0.20f),
                      topLeft = Offset(uLeft, topPaddingPx + startBox.top),
                      size = Size(uWidth, (startBox.bottom - startBox.top).coerceAtLeast(1f)),
                    )
                    drawLine(
                      color = diagColor,
                      start = Offset(uLeft, uY),
                      end = Offset(uLeft + uWidth, uY),
                      strokeWidth = 1.6.dp.toPx(),
                    )
                  }
                }

                bracketMatch?.let { bm ->
                  if (docLen > 0) {
                    val boxColor = if (bm.isMismatch) RoseError else palette.activePillFg
                    val bgAlpha = if (bm.isMismatch) 0.24f else 0.18f
                    listOfNotNull(bm.primaryOffset, bm.partnerOffset).forEach { off ->
                      if (off in 0 until docLen) {
                        val box = layout.getBoundingBox(off)
                        val left = hPadPx + box.left
                        val top = topPaddingPx + box.top
                        val w = (box.right - box.left).coerceAtLeast(4.dp.toPx())
                        val h = (box.bottom - box.top).coerceAtLeast(1f)
                        drawRect(
                          color = boxColor.copy(alpha = bgAlpha),
                          topLeft = Offset(left, top),
                          size = Size(w, h),
                        )
                        drawLine(
                          color = boxColor,
                          start = Offset(left, top + h - 1.dp.toPx()),
                          end = Offset(left + w, top + h - 1.dp.toPx()),
                          strokeWidth = 1.5.dp.toPx(),
                        )
                      }
                    }
                  }
                }

                // Live Find & Replace match highlights on the editor canvas
                if (activeFindMatches.isNotEmpty() && docLen > 0) {
                  val maxRender = activeFindMatches.size.coerceAtMost(200)
                  for (mIdx in 0 until maxRender) {
                    val range = activeFindMatches[mIdx]
                    val startOff = range.first.coerceIn(0, docLen - 1)
                    val endOff = range.last.coerceIn(startOff, docLen - 1)
                    val isCurrent = mIdx == activeFindMatchIndex
                    val highlightColor = if (isCurrent) palette.searchActiveMatchBg else palette.searchMatchBg
                    val startBox = layout.getBoundingBox(startOff)
                    val endBox = layout.getBoundingBox(endOff)
                    if (startBox.top == endBox.top) {
                      drawRect(
                        color = highlightColor,
                        topLeft = Offset(hPadPx + startBox.left, topPaddingPx + startBox.top),
                        size = Size((endBox.right - startBox.left).coerceAtLeast(4.dp.toPx()), (startBox.bottom - startBox.top).coerceAtLeast(1f)),
                      )
                    } else {
                      val startLine = layout.getLineForOffset(startOff)
                      val endLine = layout.getLineForOffset(endOff)
                      for (ln in startLine..endLine) {
                        val segLeft = if (ln == startLine) startBox.left else layout.getLineLeft(ln)
                        val segRight = if (ln == endLine) endBox.right else layout.getLineRight(ln)
                        drawRect(
                          color = highlightColor,
                          topLeft = Offset(hPadPx + segLeft, topPaddingPx + layout.getLineTop(ln)),
                          size = Size((segRight - segLeft).coerceAtLeast(4.dp.toPx()), (layout.getLineBottom(ln) - layout.getLineTop(ln)).coerceAtLeast(1f)),
                        )
                      }
                    }
                  }
                }
              }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("code_editor_input")

        BasicTextField(
          value = tfv,
          onValueChange = { rawNext ->
            if (!activeFile.isVirtualReadOnly) {
              localTfv =
                CodeEditHelper.applySmartCodeEdit(
                  prev = localTfv,
                  next = rawNext,
                  language = activeFile.language,
                  tabSize = uiState.editorSettings.tabSize,
                  autoCloseBrackets = uiState.editorSettings.autoCloseBrackets,
                )
            }
          },
          readOnly = activeFile.isVirtualReadOnly,
          onTextLayout = { textLayoutResult = it },
          textStyle =
            TextStyle(
              fontFamily = JetBrainsMono,
              fontSize = fontSize,
              lineHeight = lineHeight,
              fontFeatureSettings = "tnum",
              platformStyle = sharedPlatformStyle,
              lineHeightStyle = sharedLineHeightStyle,
              color = palette.textPrimary,
            ),
          cursorBrush = SolidColor(palette.activePillFg),
          visualTransformation = visualTransformation,
          modifier = editorModifier,
        )
      }

      // Floating Pinch-Zoom Indicator HUD (reads live typographic zoom percentage)
      EditorZoomHud(
        visible = isPinching || showZoomHud,
        zoomPercentProvider = { activeZoomPercent.roundToInt().coerceIn(60, 220) },
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
      )
    }

    // Hideable Bottom Coding Dock: Status Line + Zoom Controls + Autocomplete + Ergonomic Key Rail
    AnimatedVisibility(
      visible = !uiState.isEditorBottomBarsHidden,
      enter = slideInVertically(tween(180)) { it } + fadeIn(tween(160)),
      exit = slideOutVertically(tween(150)) { it } + fadeOut(tween(130)),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(28.dp)
              .edgeGlassDock(shape = RectangleShape)
              .padding(horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "Ln $activeLine, Col $activeCol",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = palette.textSecondary,
            maxLines = 1,
          )
          Text("·", fontSize = 10.sp, color = palette.textMuted)
          Text(
            text =
              when (activeFile.language) {
                WorkerLanguage.JAVASCRIPT -> "JS"
                WorkerLanguage.TYPESCRIPT -> "TS"
                WorkerLanguage.JSON -> "JSON"
                WorkerLanguage.TEXT -> "TXT"
              },
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textMuted,
            maxLines = 1,
          )
          Text("·", fontSize = 10.sp, color = palette.textMuted)
          Text(
            text = "$lineCount lines",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = palette.textMuted,
            maxLines = 1,
          )
          Spacer(Modifier.weight(1f))

          // Compact Editor Zoom Controls (- | 100% | +) scaling crisp vector text
          EditorZoomControlCluster(
            zoomPercentProvider = { activeZoomPercent.roundToInt().coerceIn(60, 220) },
            zoomPresetMenuOpen = zoomPresetMenuOpen,
            onZoomPresetMenuChange = { zoomPresetMenuOpen = it },
            onSetZoom = { next ->
              val clamped = next.coerceIn(60, 220)
              val prev = committedZoomPercent.toFloat().coerceAtLeast(1f)
              val ratio = clamped.toFloat() / prev
              committedZoomPercent = clamped
              liveZoomPercent = clamped.toFloat()
              onZoomPercentChange(clamped)
              zoomHudBump++
              scope.launch {
                verticalScroll.scrollTo((verticalScroll.value * ratio).roundToInt().coerceAtLeast(0))
              }
            },
            onStepZoom = { delta ->
              val prev = committedZoomPercent.toFloat().coerceAtLeast(1f)
              val next = (committedZoomPercent + delta).coerceIn(60, 220)
              val ratio = next.toFloat() / prev
              committedZoomPercent = next
              liveZoomPercent = next.toFloat()
              onZoomPercentChange(next)
              zoomHudBump++
              scope.launch {
                verticalScroll.scrollTo((verticalScroll.value * ratio).roundToInt().coerceAtLeast(0))
              }
            },
          )

          val errCount = uiState.diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
          val warnCount = uiState.diagnostics.size - errCount
          Row(
            modifier =
              Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { flushThen(onOpenProblems) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector =
                if (uiState.diagnostics.isEmpty()) Icons.Outlined.CheckCircleOutline
                else Icons.Outlined.WarningAmber,
              contentDescription = "Diagnostics",
              tint =
                when {
                  errCount > 0 -> MaterialTheme.colorScheme.error
                  warnCount > 0 -> AmberWarn
                  else -> EmeraldLive
                },
              modifier = Modifier.size(12.dp),
            )
            Text(
              text =
                when {
                  errCount > 0 -> "$errCount err"
                  warnCount > 0 -> "$warnCount warn"
                  else -> "Clean"
                },
              fontFamily = JetBrainsMono,
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium,
              color =
                when {
                  errCount > 0 -> MaterialTheme.colorScheme.error
                  warnCount > 0 -> AmberWarn
                  else -> palette.textSecondary
                },
              maxLines = 1,
            )
          }

          IconButton(
            onClick = onHideEditorBottomBars,
            modifier = Modifier.size(24.dp).testTag("hide_editor_bottom_bars_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.KeyboardArrowDown,
              contentDescription = "Hide editor bottom bars",
              tint = palette.textMuted,
              modifier = Modifier.size(15.dp),
            )
          }
        }

        if (!activeFile.isVirtualReadOnly) {
          if (activeFile.language in setOf(WorkerLanguage.JAVASCRIPT, WorkerLanguage.TYPESCRIPT, WorkerLanguage.JSON)) {
            val cursorPos = tfv.selection.start.coerceIn(0, tfv.text.length)
            val wordBeforeCursor =
              remember(tfv.text, cursorPos) {
                val start = (cursorPos - 40).coerceAtLeast(0)
                var idx = cursorPos
                while (idx > start) {
                  val ch = tfv.text[idx - 1]
                  if (ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '$') idx--
                  else break
                }
                if (idx == cursorPos) "" else tfv.text.substring(idx, cursorPos)
              }
            val completions =
              remember(uiState.workspace?.bindings, activeFile.language, wordBeforeCursor) {
                WranglerSyncParser.buildWorkersCompletions(uiState.workspace, activeFile.language, wordBeforeCursor)
              }
            if (completions.isNotEmpty()) {
              HorizontalDivider(color = palette.glassBorderSubtle)
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(palette.glassSurface)
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
                        val nextVal = TextFieldValue(nextText, TextRange(nextPos))
                        localTfv = nextVal
                        onCodeChange(nextVal)
                      } else {
                        flushThen { onInsertText(sug.insertText, sug.cursorBackOffset) }
                      }
                    },
                    shape = RoundedCornerShape(7.dp),
                    color = palette.glassElevated,
                    border = rememberGlassBorderStroke(1.dp),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                      Text(
                        text = sug.label,
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = palette.activePillFg,
                        maxLines = 1,
                      )
                      Text(
                        text = sug.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted,
                        maxLines = 1,
                      )
                    }
                  }
                }
              }
            }
          }

          HorizontalDivider(color = palette.glassBorderSubtle)
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(palette.glassSurface)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // Sticky Undo / Redo group on the left
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              IconButton(onClick = { flushThen(onUndo) }, modifier = Modifier.size(34.dp).testTag("editor_undo")) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo", tint = palette.textSecondary, modifier = Modifier.size(17.dp))
              }
              IconButton(onClick = { flushThen(onRedo) }, modifier = Modifier.size(34.dp).testTag("editor_redo")) {
                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo", tint = palette.textSecondary, modifier = Modifier.size(17.dp))
              }
            }

            VerticalDivider(
              color = palette.glassBorderSubtle,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            )

            // Scrollable tactile coding key rail
            Row(
              modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AccessoryKey("TAB") { flushThen(onInsertTab) }
              AccessoryKey("//") { flushThen(onToggleComment) }
              AccessoryKey("{ }") { flushThen { onInsertText("{}", 1) } }
              AccessoryKey("( )") { flushThen { onInsertText("()", 1) } }
              AccessoryKey("[ ]") { flushThen { onInsertText("[]", 1) } }
              AccessoryKey("=>") { flushThen { onInsertText(" => ", 0) } }
              AccessoryKey(";") { flushThen { onInsertText(";", 0) } }
              AccessoryKey(":") { flushThen { onInsertText(": ", 0) } }
              AccessoryKey("\"") { flushThen { onInsertText("\"\"", 1) } }
              AccessoryKey("'") { flushThen { onInsertText("''", 1) } }
              AccessoryKey("`") { flushThen { onInsertText("``", 1) } }
              AccessoryKey("=") { flushThen { onInsertText(" = ", 0) } }
              AccessoryKey(".") { flushThen { onInsertText(".", 0) } }
            }
          }
        }
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
    color = palette.glassElevated,
    border = rememberGlassBorderStroke(1.dp),
  ) {
    Box(
      modifier = Modifier.height(30.dp).padding(horizontal = 10.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        fontFamily = JetBrainsMono,
        fontSize = 11.5.sp,
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
        shape = RoundedCornerShape(10.dp),
        color = palette.glassElevated,
        border = rememberGlassBorderStroke(1.dp),
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
                color = if (isActive) palette.tabActiveBg else palette.glassSurface,
                border =
                  if (isActive) BorderStroke(1.dp, palette.activePillFg.copy(alpha = 0.45f))
                  else rememberGlassBorderStroke(1.dp),
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
                          .height(2.5.dp)
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
      modifier = Modifier.widthIn(max = 360.dp),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = workerName,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary,
      )
      Text(
        text = "Select a file or jump straight into editing",
        style = MaterialTheme.typography.bodySmall,
        color = palette.textMuted,
      )
      Spacer(Modifier.height(4.dp))
      Surface(
        color = palette.chromeSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
          files.take(4).forEach { file ->
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { onOpenFile(file.path) }
                  .padding(horizontal = 16.dp, vertical = 11.dp),
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
          HorizontalDivider(color = palette.border.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable(onClick = onCreateNewFile)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(18.dp))
            Text("New File…", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
          }
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenExplorer)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            Text("Project Explorer", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
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
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    modifier =
      Modifier
        .edgeGlassSurface(RoundedCornerShape(8.dp), elevated = true)
        .padding(horizontal = 2.dp, vertical = 1.dp),
  ) {
    IconButton(
      onClick = { onStepZoom(-10) },
      enabled = displayZoomPercent > 60,
      modifier = Modifier.size(22.dp).testTag("editor_zoom_out"),
    ) {
      Icon(
        Icons.Outlined.Remove,
        contentDescription = "Zoom out editor",
        tint = if (displayZoomPercent > 60) palette.textSecondary else palette.textMuted.copy(alpha = 0.4f),
        modifier = Modifier.size(13.dp),
      )
    }
    Box {
      Text(
        text = "${displayZoomPercent}%",
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
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
      modifier = Modifier.size(22.dp).testTag("editor_zoom_in"),
    ) {
      Icon(
        Icons.Outlined.Add,
        contentDescription = "Zoom in editor",
        tint = if (displayZoomPercent < 220) palette.textSecondary else palette.textMuted.copy(alpha = 0.4f),
        modifier = Modifier.size(13.dp),
      )
    }
  }
}

