package com.example.editor.canvas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.DiagnosticItem
import com.example.data.remote.WorkerLanguage
import com.example.data.security.EditorSettings
import com.example.editor.BracketMatch
import com.example.editor.CodeAnalyzer
import com.example.editor.CodeSymbol
import com.example.editor.EditorCursorTelemetry
import com.example.editor.engine.ClipboardHistoryStore
import com.example.editor.engine.EdgeEditorSession
import com.example.editor.engine.EdgeFoldDetector
import com.example.editor.engine.IncrementalLineStyles
import com.example.editor.engine.LineChange
import com.example.editor.engine.LineRenderCache
import com.example.editor.engine.lineChanges
import com.example.editor.engine.revertEdit
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Imperative controller exposed to [com.example.ui.components.CodeEditorPane] and the Command Palette.
 * Zero-cost in-place operations run directly against the Rope-backed [EdgeEditorSession].
 */
@Stable
class EdgeEditorController {
  internal var session: EdgeEditorSession? = null
  internal var geometry: EdgeEditorGeometry? = null
  internal var cache: LineRenderCache? = null
  internal var flushCallback: (() -> Unit)? = null
  internal var pasteHistoryToggleCallback: (() -> Unit)? = null

  fun currentCharSequence(): CharSequence = session?.doc?.chars ?: ""

  fun currentText(): String = session?.doc?.text.orEmpty()

  fun currentIdentifierAtCursor(): String? = session?.currentIdentifierAtCursor()

  fun flushNow() {
    flushCallback?.invoke()
  }

  fun insertSnippet(snippet: String, cursorBack: Int = 0) {
    session?.insertSnippet(snippet, cursorBack)
  }

  fun insertTab(tabSize: Int) {
    session?.let {
      it.tabSize = tabSize
      it.insertTab()
    }
  }

  fun toggleLineComment() {
    session?.toggleLineComment()
  }

  fun replaceBeforeCursor(replaceLen: Int, insertText: String, cursorBack: Int = 0) {
    session?.replaceBeforeCursor(replaceLen, insertText, cursorBack)
  }

  fun applyCompletionEdit(replaceStart: Int, replaceEnd: Int, insertText: String, cursorBack: Int = 0) {
    session?.applyCompletionEdit(replaceStart, replaceEnd, insertText, cursorBack)
  }

  fun moveLineUp() {
    session?.moveLineUp()
  }

  fun moveLineDown() {
    session?.moveLineDown()
  }

  fun duplicateLine() {
    session?.duplicateLine()
  }

  fun undo(): Boolean = session?.undo() == true

  fun redo(): Boolean = session?.redo() == true

  fun foldAll(): Int = session?.foldAll() ?: 0

  fun unfoldAll() {
    session?.unfoldAll()
  }

  fun toggleFoldAtCurrentLine(): Boolean {
    val s = session ?: return false
    val line = s.doc.lineForOffset(s.selection.end)
    return s.toggleFoldAtLine(line)
  }

  fun toggleBookmark(): Boolean = session?.toggleBookmark() == true

  fun nextBookmark(): Int? = session?.nextBookmarkLine()

  fun openPasteHistory() {
    pasteHistoryToggleCallback?.invoke()
  }

  fun scaleScrollBy(ratio: Float) {
    val s = session ?: return
    val g = geometry ?: return
    val c = cache ?: return
    g.scaleScrollBy(ratio, s, c)
  }
}

private enum class DraggedHandle {
  START,
  END,
}

@Composable
fun EdgeCodeEditorSurface(
  filePath: String,
  externalTfv: TextFieldValue,
  editorRevision: Long,
  originalContent: String,
  language: WorkerLanguage,
  editorSettings: EditorSettings,
  zoomPercent: Int,
  hasCustomBg: Boolean,
  readOnly: Boolean,
  diagnostics: List<DiagnosticItem>,
  symbols: List<CodeSymbol>,
  findMatches: List<IntRange>,
  activeFindMatchIndex: Int,
  controller: EdgeEditorController,
  onDebouncedSync: (TextFieldValue) -> Unit,
  onTelemetryUpdate: (EditorCursorTelemetry) -> Unit,
  onPinchZoomChanged: (liveZoom: Int, pinching: Boolean) -> Unit,
  onPinchZoomCommitted: (finalZoom: Int) -> Unit,
  onRequestSmartRename: (String?) -> Unit,
  onToggleFindBar: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val density = LocalDensity.current
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()

  val latestOnSync by rememberUpdatedState(onDebouncedSync)
  val latestOnTelemetry by rememberUpdatedState(onTelemetryUpdate)
  val latestOnZoomChanged by rememberUpdatedState(onPinchZoomChanged)
  val latestOnZoomCommitted by rememberUpdatedState(onPinchZoomCommitted)
  val latestOnRename by rememberUpdatedState(onRequestSmartRename)
  val latestOnFind by rememberUpdatedState(onToggleFindBar)

  // Session & rendering caches scoped per open file path
  val session = remember(filePath) { EdgeEditorSession(externalTfv.text, externalTfv.selection) }
  val geometry = remember(filePath) { EdgeEditorGeometry() }
  val lineStyles = remember(filePath) { IncrementalLineStyles(language, palette) }
  val textMeasurer = rememberTextMeasurer(cacheSize = LineRenderCache.MEASURER_CACHE_ENTRIES)
  val gutterMeasurer = rememberTextMeasurer(cacheSize = 64)
  val renderCache = remember(filePath, textMeasurer) { LineRenderCache(textMeasurer) }
  val imeHandle = remember(filePath) { EditorImeHandle() }
  val focusRequester = remember(filePath) { FocusRequester() }
  val undoFeedback = remember(filePath) { EdgeUndoFeedback() }

  var isFocused by remember { mutableStateOf(false) }
  var liveZoomPercent by remember { mutableIntStateOf(zoomPercent.coerceIn(60, 220)) }
  var isPinching by remember { mutableStateOf(false) }
  var loupeSource by remember { mutableStateOf<Offset?>(null) }
  var activeDiffHunk by remember { mutableStateOf<LineChange?>(null) }
  var showPasteHistory by remember { mutableStateOf(false) }
  var showTouchHandles by remember { mutableStateOf(false) }
  var activeHandleDrag by remember { mutableStateOf<DraggedHandle?>(null) }

  // Sync committed zoom from settings when not actively pinching
  LaunchedEffect(zoomPercent) {
    if (!isPinching) liveZoomPercent = zoomPercent.coerceIn(60, 220)
  }

  // Configure session flags
  session.readOnly = readOnly
  session.autoCloseBrackets = editorSettings.autoCloseBrackets
  session.tabSize = editorSettings.tabSize
  session.language = language

  // Keep styles synchronized with palette/language & incremental edits
  lineStyles.reconfigure(language, palette)
  session.onSpliceStyles = { doc, sLine, oEnd, delta ->
    lineStyles.splice(doc, sLine, oEnd, delta)
    if (geometry.wordWrap) {
      geometry.invalidateWrapEstimate()
    }
  }

  // Track last pushed text to avoid echoing our own debounced sync back into the session
  var lastSyncedRevision by remember(filePath) { mutableIntStateOf(-1) }
  var lastExternalRev by remember(filePath) { mutableStateOf(-1L) }

  fun flushSyncNow() {
    if (session.textRevision == lastSyncedRevision) return
    lastSyncedRevision = session.textRevision
    val snapshot = TextFieldValue(text = session.doc.text, selection = session.selection)
    latestOnSync(snapshot)
  }

  // External document updates (Format, Find/Replace, Revert, JumpToLine, Tab switch)
  LaunchedEffect(filePath, editorRevision, externalTfv.text, externalTfv.selection) {
    val isNewExternalRev = editorRevision != lastExternalRev
    lastExternalRev = editorRevision
    if (isNewExternalRev) {
      val textDiffers = externalTfv.text != session.doc.text
      if (textDiffers) {
        session.loadDocument(externalTfv.text, externalTfv.selection, resetHistory = false)
        lineStyles.reset(session.doc.lineCount)
        renderCache.clear()
        geometry.invalidateWrapEstimate()
        lastSyncedRevision = session.textRevision
      } else if (externalTfv.selection != session.selection) {
        session.setSelection(externalTfv.selection)
      }
      geometry.ensureCaretVisible(session, lineStyles, renderCache)
    }
  }

  // Attach controller & cleanup flush
  DisposableEffect(controller, session, geometry, renderCache) {
    controller.session = session
    controller.geometry = geometry
    controller.cache = renderCache
    controller.flushCallback = { flushSyncNow() }
    controller.pasteHistoryToggleCallback = { showPasteHistory = !showPasteHistory }
    onDispose {
      flushSyncNow()
      if (controller.session === session) {
        controller.session = null
        controller.geometry = null
        controller.cache = null
        controller.flushCallback = null
        controller.pasteHistoryToggleCallback = null
      }
    }
  }

  // Debounced sync back to IdeViewModel (220ms after last edit — zero per-keystroke VM thrash)
  LaunchedEffect(session.editCount) {
    if (session.editCount == 0) return@LaunchedEffect
    delay(220L)
    flushSyncNow()
  }

  // Publish live cursor telemetry to leaf HUD composables
  LaunchedEffect(session.textRevision, session.selection) {
    latestOnTelemetry(session.buildTelemetry())
  }

  // Background fold detection (debounced)
  LaunchedEffect(filePath, language, editorSettings.codeFolding, session.textRevision) {
    if (!editorSettings.codeFolding || language == WorkerLanguage.TEXT) {
      session.updateFoldRegions(emptyList())
      return@LaunchedEffect
    }
    if (session.editCount > 0) delay(350L)
    val docSnap = session.doc
    val regions = withContext(Dispatchers.Default) { EdgeFoldDetector.detect(docSnap, language) }
    session.updateFoldRegions(regions)
  }

  // Background Myers diff markers against deployed Cloudflare baseline
  var diffChanges by remember(filePath) { mutableStateOf<List<LineChange>>(emptyList()) }
  LaunchedEffect(filePath, originalContent, editorSettings.gutterDiffMarkers, session.textRevision) {
    if (!editorSettings.gutterDiffMarkers || originalContent.isEmpty()) {
      diffChanges = emptyList()
      return@LaunchedEffect
    }
    if (session.editCount > 0) delay(450L)
    val currentStr = session.doc.awaitText()
    diffChanges = withContext(Dispatchers.Default) { lineChanges(originalContent, currentStr) }
  }

  // Background matching bracket lookup (fast local check)
  var bracketMatch by remember(filePath) { mutableStateOf<BracketMatch?>(null) }
  LaunchedEffect(session.textRevision, session.selection, language) {
    val sel = session.selection
    if (!sel.collapsed || language == WorkerLanguage.TEXT) {
      bracketMatch = null
      return@LaunchedEffect
    }
    val docSnap = session.doc
    val pos = sel.end.coerceIn(0, docSnap.length)
    val nearBracket =
      (pos < docSnap.length && docSnap.charAt(pos) in "()[]{}") ||
        (pos > 0 && docSnap.charAt(pos - 1) in "()[]{}")
    if (!nearBracket) {
      bracketMatch = null
      return@LaunchedEffect
    }
    val textSnap = docSnap.awaitText()
    bracketMatch =
      withContext(Dispatchers.Default) {
        CodeAnalyzer.findMatchingBracket(textSnap, pos, language)
      }
  }

  // Font & geometry metrics scaled by live zoom
  val effectiveFontSp = (editorSettings.fontSizeSp * (liveZoomPercent / 100f)).coerceIn(8f, 36f)
  val codeTextStyle =
    remember(effectiveFontSp, palette.textPrimary) {
      TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = effectiveFontSp.sp,
        lineHeight = (effectiveFontSp * 1.46f).sp,
        color = palette.textPrimary,
      )
    }
  val gutterTextStyle =
    remember(effectiveFontSp) {
      TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = (effectiveFontSp * 0.84f).coerceIn(8f, 22f).sp,
      )
    }

  val sampleMetrics = remember(codeTextStyle, textMeasurer) { textMeasurer.measure("0", style = codeTextStyle, maxLines = 1) }
  val charW = sampleMetrics.size.width.toFloat().coerceAtLeast(6f)
  val lineH = (sampleMetrics.size.height.toFloat().coerceAtLeast(14f) * 1.06f)
  val baseline = sampleMetrics.firstBaseline

  val digits = session.doc.lineCount.coerceAtLeast(1).toString().length.coerceAtLeast(2)
  val gutterW =
    with(density) {
      val foldExtra = if (editorSettings.codeFolding) 18.dp.toPx() else 6.dp.toPx()
      (digits * charW * 0.88f + 22.dp.toPx() + foldExtra).coerceAtLeast(42.dp.toPx())
    }

  geometry.lineHeightPx = lineH
  geometry.baselineOffsetPx = baseline
  geometry.charWidthPx = charW
  geometry.gutterWidthPx = gutterW
  geometry.textPaddingStartPx = with(density) { 10.dp.toPx() }
  geometry.topPaddingPx = with(density) { 8.dp.toPx() }
  geometry.bottomPaddingPx = with(density) { 140.dp.toPx() }
  geometry.showLineNumbers = editorSettings.showLineNumbers
  geometry.wordWrap = editorSettings.wordWrap
  geometry.codeFolding = editorSettings.codeFolding

  renderCache.configure(
    textStyle = codeTextStyle,
    wrapWidthPx = geometry.wrapWidthPx,
    tabSize = editorSettings.tabSize,
    charWidthPx = charW,
    density = density,
  )
  geometry.syncWrapLayout(session.doc, session.foldModel, renderCache)

  // Caret geometry provider for Android IME CursorAnchorInfo
  imeHandle.caretGeometryProvider = {
    geometry.caretGeometry(session.selection.end, session.doc, session.foldModel, lineStyles, renderCache)
  }

  // Animated gliding caret (snaps instantly while typing, smoothly glides on tap/arrow navigation)
  val caretXAnim = remember(filePath) { Animatable(geometry.textOriginX) }
  val caretYAnim = remember(filePath) { Animatable(geometry.topPaddingPx) }
  val caretAlphaAnim = remember(filePath) { Animatable(1f) }
  var lastAnimEditCount by remember(filePath) { mutableIntStateOf(0) }

  LaunchedEffect(session.selection, session.textRevision, geometry.vOffset, geometry.hOffset, effectiveFontSp) {
    val target = geometry.caretGeometry(session.selection.end, session.doc, session.foldModel, lineStyles, renderCache)
    val wasTyping = session.editCount != lastAnimEditCount
    lastAnimEditCount = session.editCount
    if (wasTyping || abs(target.horizontalPosition - caretXAnim.value) > 400f || abs(target.top - caretYAnim.value) > 400f) {
      caretXAnim.snapTo(target.horizontalPosition)
      caretYAnim.snapTo(target.top)
    } else {
      launch { caretXAnim.animateTo(target.horizontalPosition, tween(55)) }
      launch { caretYAnim.animateTo(target.top, tween(55)) }
    }
  }

  // Keep caret scrolled into view on edits/navigation
  LaunchedEffect(session.editCount, session.selection) {
    if (isFocused && activeHandleDrag == null) {
      val topClearance = if (editorSettings.stickyHeaders) with(density) { 28.dp.toPx() } else 0f
      geometry.ensureCaretVisible(session, lineStyles, renderCache, topClearancePx = topClearance)
    }
  }

  // Caret blink loop (solid immediately after typing/tapping)
  LaunchedEffect(isFocused, session.selection, session.editCount, readOnly) {
    if (!isFocused || readOnly || !session.selection.collapsed) {
      caretAlphaAnim.snapTo(0f)
      return@LaunchedEffect
    }
    caretAlphaAnim.snapTo(1f)
    delay(450L)
    while (true) {
      caretAlphaAnim.animateTo(0f, tween(140))
      delay(360L)
      caretAlphaAnim.animateTo(1f, tween(120))
      delay(480L)
    }
  }

  // Sticky Scroll Header derived purely from top visible doc line
  val stickySymbol by
    remember(symbols, editorSettings.stickyHeaders) {
      derivedStateOf {
        if (!editorSettings.stickyHeaders || symbols.isEmpty()) {
          null
        } else {
          val topRow = geometry.firstVisibleRow()
          if (topRow <= 0) {
            null
          } else {
            val topDocLine1 = geometry.docLineAtViewportY(geometry.topPaddingPx + 4f, session.foldModel) + 1
            symbols.lastOrNull { it.line < topDocLine1 }
          }
        }
      }
    }

  val caretWord =
    remember(session.selection, session.textRevision) {
      if (!session.selection.collapsed) null
      else session.currentIdentifierAtCursor()?.takeIf { it.length >= 2 }
    }

  // Clipboard helpers
  fun copyToSystemClipboard(text: String) {
    ClipboardHistoryStore.record(text)
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(null, ClipboardHistoryStore.clipForClipboard(text)))
  }

  fun readSystemClipboard(): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val item = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
    val plain = item.text?.toString()?.takeIf { it.isNotEmpty() }
    if (plain != null) return plain
    val coerced = item.coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() } ?: return null
    return ClipboardHistoryStore.stripSimpleHtml(coerced)
  }

  // Fling coroutine handle
  var flingJob by remember { mutableStateOf<Job?>(null) }

  // Helper to hit-test selection teardrop handles
  fun hitTestSelectionHandle(point: Offset): DraggedHandle? {
    if (!showTouchHandles || session.selection.collapsed) return null
    val hitRadius = with(density) { 26.dp.toPx() }
    val sCaret = geometry.caretGeometry(session.selection.min, session.doc, session.foldModel, lineStyles, renderCache)
    val eCaret = geometry.caretGeometry(session.selection.max, session.doc, session.foldModel, lineStyles, renderCache)
    val sCenter = Offset(sCaret.horizontalPosition - with(density) { 6.dp.toPx() }, sCaret.bottom + with(density) { 9.dp.toPx() })
    val eCenter = Offset(eCaret.horizontalPosition + with(density) { 6.dp.toPx() }, eCaret.bottom + with(density) { 9.dp.toPx() })
    val dStart = (point - sCenter).getDistance()
    val dEnd = (point - eCenter).getDistance()
    return when {
      dStart <= hitRadius && dStart <= dEnd -> DraggedHandle.START
      dEnd <= hitRadius -> DraggedHandle.END
      else -> null
    }
  }

  // Multi-tap tracker
  val lastTapTime = remember { longArrayOf(0L) }
  val lastTapPos = remember { arrayOf(Offset.Zero) }
  val tapStreak = remember { intArrayOf(0) }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .clipToBounds()
        .edgeEditorLoupe(
          loupeSourceProvider = { loupeSource },
          lineHeight = lineH,
          background = palette.editorBg,
          outline = palette.activePillFg.copy(alpha = 0.65f),
        )
        .testTag("code_editor_input"),
  ) {
    // Pure Canvas Editing Surface
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .onSizeChanged { newSize ->
            geometry.viewport = newSize
            geometry.clampScroll(session, renderCache)
          }
          .focusRequester(focusRequester)
          .onFocusChanged { st ->
            isFocused = st.isFocused
            if (!st.isFocused) {
              showTouchHandles = false
              activeDiffHunk = null
            }
          }
          .edgeEditorTextInput(
            session = session,
            ime = imeHandle,
            options =
              EditorImeOptions(
                softKeyboardSuggestions = editorSettings.softKeyboardSuggestions,
                readOnly = readOnly,
              ),
          )
          .focusable()
          .onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val ctrl = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
            val shift = keyEvent.isShiftPressed

            if (ctrl) {
              when (keyEvent.key) {
                Key.Z -> {
                  if (shift) session.redo() else session.undo()
                  return@onPreviewKeyEvent true
                }
                Key.Y -> {
                  session.redo()
                  return@onPreviewKeyEvent true
                }
                Key.A -> {
                  session.selectAll()
                  showTouchHandles = true
                  return@onPreviewKeyEvent true
                }
                Key.C -> {
                  session.copySelection()?.let(::copyToSystemClipboard)
                  return@onPreviewKeyEvent true
                }
                Key.X -> {
                  session.cutSelection()?.let(::copyToSystemClipboard)
                  return@onPreviewKeyEvent true
                }
                Key.V -> {
                  readSystemClipboard()?.let { session.commitText(it) }
                  return@onPreviewKeyEvent true
                }
                Key.D -> {
                  session.duplicateLine()
                  return@onPreviewKeyEvent true
                }
                Key.F -> {
                  latestOnFind()
                  return@onPreviewKeyEvent true
                }
                Key.Slash -> {
                  session.toggleLineComment()
                  return@onPreviewKeyEvent true
                }
              }
            }

            when (keyEvent.key) {
              Key.Backspace -> {
                session.backspace()
                true
              }
              Key.Delete -> {
                session.deleteForward()
                true
              }
              Key.Enter, Key.NumPadEnter -> {
                session.commitText("\n")
                true
              }
              Key.Tab -> {
                if (shift) session.outdentSelection() else session.insertTab()
                true
              }
              Key.DirectionLeft -> {
                moveCaretHorizontal(session, delta = -1, extend = shift)
                true
              }
              Key.DirectionRight -> {
                moveCaretHorizontal(session, delta = 1, extend = shift)
                true
              }
              Key.DirectionUp -> {
                moveCaretVertical(session, deltaLines = -1, extend = shift)
                true
              }
              Key.DirectionDown -> {
                moveCaretVertical(session, deltaLines = 1, extend = shift)
                true
              }
              Key.MoveHome -> {
                val line = session.doc.lineForOffset(session.selection.end)
                val target = session.doc.lineStart(line)
                session.setSelection(if (shift) TextRange(session.selection.start, target) else TextRange(target))
                true
              }
              Key.MoveEnd -> {
                val line = session.doc.lineForOffset(session.selection.end)
                val target = session.doc.lineEnd(line)
                session.setSelection(if (shift) TextRange(session.selection.start, target) else TextRange(target))
                true
              }
              else -> {
                val cp = textInputCodePoint(keyEvent)
                if (cp >= 0) {
                  session.commitText(String(Character.toChars(cp)))
                  true
                } else {
                  false
                }
              }
            }
          }
          .edgeUndoSwipe(session, undoFeedback)
          .pointerInput(filePath, readOnly) {
            val touchSlop = viewConfiguration.touchSlop
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
            val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
            val deadZonePx = with(density) { 4.dp.toPx() }
            val resistXPx = with(density) { 3.dp.toPx() }
            val resistYPx = with(density) { 4.dp.toPx() }

            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              flingJob?.cancel()
              val downPos = down.position
              val downTime = down.uptimeMillis

              // 1. Check if grabbing a selection teardrop handle
              val grabbedHandle = hitTestSelectionHandle(downPos)
              if (grabbedHandle != null) {
                down.consume()
                activeHandleDrag = grabbedHandle
                val fixed = if (grabbedHandle == DraggedHandle.START) session.selection.max else session.selection.min
                val movingStart = if (grabbedHandle == DraggedHandle.START) session.selection.min else session.selection.max
                val hotspotCaret = geometry.caretGeometry(movingStart, session.doc, session.foldModel, lineStyles, renderCache)
                val hotspot = Offset(hotspotCaret.horizontalPosition, hotspotCaret.top + lineH * 0.5f)
                val drag =
                  SelectionHandleDrag(
                    fixedEnd = fixed,
                    start = movingStart,
                    grab = downPos,
                    hotspot = hotspot,
                    lineHeight = lineH,
                    deadZone = deadZonePx,
                    resistX = resistXPx,
                    resistY = resistYPx,
                  )
                loupeSource = hotspot

                while (true) {
                  val ev = awaitPointerEvent()
                  val pt = ev.changes.firstOrNull { it.id == down.id } ?: break
                  if (!pt.pressed) {
                    pt.consume()
                    break
                  }
                  if (pt.positionChanged()) {
                    pt.consume()
                    val changed =
                      drag.moveTo(
                        finger = pt.position,
                        offsetAt = { p -> geometry.offsetAt(p, session.doc, session.foldModel, lineStyles, renderCache) },
                        caretAt = { off ->
                          val c = geometry.caretGeometry(off, session.doc, session.foldModel, lineStyles, renderCache)
                          SelectionHandleDrag.CaretPoint(c.horizontalPosition, c.top)
                        },
                      )
                    if (changed) {
                      val (a, b) = drag.selection
                      session.setSelection(TextRange(minOf(a, b), maxOf(a, b)))
                    }
                    val curCaret = geometry.caretGeometry(drag.moving, session.doc, session.foldModel, lineStyles, renderCache)
                    loupeSource = Offset(curCaret.horizontalPosition, curCaret.top + lineH * 0.5f)
                  }
                }
                activeHandleDrag = null
                loupeSource = null
                return@awaitEachGesture
              }

              // 2. General touch: distinguish tap / double-tap / triple-tap / long-press / 2D scroll+fling / 2-finger pinch
              val velocityTracker = VelocityTracker()
              velocityTracker.addPosition(down.uptimeMillis, down.position)
              var draggingScroll = false
              var pinching = false
              var pinchStartZoom = liveZoomPercent
              var pinchScaleAccum = 1f
              var longPressFired = false

              val longPressJob =
                scope.launch {
                  delay(longPressTimeout)
                  if (!draggingScroll && !pinching) {
                    longPressFired = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val off = geometry.offsetAt(downPos, session.doc, session.foldModel, lineStyles, renderCache)
                    session.selectWordAt(off)
                    showTouchHandles = true
                    focusRequester.requestFocus()
                  }
                }

              try {
                while (true) {
                  val ev = awaitPointerEvent()
                  val pressed = ev.changes.filter { it.pressed }
                  if (pressed.isEmpty()) break

                  // Two-finger pinch-to-zoom
                  if (pressed.size == 2) {
                    val zoomFactor = ev.calculateZoom()
                    val span = ev.calculateCentroidSize(useCurrent = true)
                    if (!pinching && span > 20f && abs(zoomFactor - 1f) > 0.02f) {
                      pinching = true
                      isPinching = true
                      longPressJob.cancel()
                      pinchStartZoom = liveZoomPercent
                      pinchScaleAccum = 1f
                    }
                    if (pinching) {
                      ev.changes.forEach { it.consume() }
                      pinchScaleAccum *= zoomFactor
                      val nextZoom = (pinchStartZoom * pinchScaleAccum).roundToInt().coerceIn(60, 220)
                      if (nextZoom != liveZoomPercent) {
                        val ratio = nextZoom.toFloat() / liveZoomPercent.toFloat().coerceAtLeast(1f)
                        liveZoomPercent = nextZoom
                        geometry.scaleScrollBy(ratio, session, renderCache)
                        latestOnZoomChanged(nextZoom, true)
                      }
                    }
                    continue
                  }

                  if (pinching) {
                    ev.changes.forEach { it.consume() }
                    continue
                  }

                  val primary = ev.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                  if (primary.positionChanged()) {
                    val delta = primary.position - primary.previousPosition
                    if (!draggingScroll && !longPressFired && (primary.position - downPos).getDistance() > touchSlop) {
                      draggingScroll = true
                      longPressJob.cancel()
                    }
                    if (draggingScroll) {
                      primary.consume()
                      velocityTracker.addPosition(primary.uptimeMillis, primary.position)
                      geometry.scrollBy(-delta.x, -delta.y, session, renderCache)
                    }
                  }
                }
              } finally {
                longPressJob.cancel()
              }

              if (pinching) {
                isPinching = false
                latestOnZoomCommitted(liveZoomPercent)
                return@awaitEachGesture
              }

              if (draggingScroll) {
                val vel = velocityTracker.calculateVelocity()
                val decay = FloatExponentialDecaySpec(frictionMultiplier = 1.45f)
                flingJob =
                  scope.launch {
                    if (abs(vel.y) > 80f) {
                      launch {
                        var lastV = geometry.vOffset
                        animateDecay(initialValue = lastV, initialVelocity = -vel.y, animationSpec = decay) { v, _ ->
                          val dy = v - lastV
                          lastV = v
                          geometry.scrollBy(0f, dy, session, renderCache)
                        }
                      }
                    }
                    if (!geometry.wordWrap && abs(vel.x) > 80f) {
                      launch {
                        var lastH = geometry.hOffset
                        animateDecay(initialValue = lastH, initialVelocity = -vel.x, animationSpec = decay) { h, _ ->
                          val dx = h - lastH
                          lastH = h
                          geometry.scrollBy(dx, 0f, session, renderCache)
                        }
                      }
                    }
                  }
                return@awaitEachGesture
              }

              if (longPressFired) return@awaitEachGesture

              // 3. Clean Tap handling: Fold chevron -> Diff stripe -> Bookmark gutter -> Text caret / double-tap / triple-tap
              val foldLine = geometry.foldActionAt(downPos, session)
              if (foldLine >= 0) {
                session.toggleFoldAtLine(foldLine)
                return@awaitEachGesture
              }

              // Gutter diff stripe tap -> open hunk preview card
              if (editorSettings.gutterDiffMarkers &&
                editorSettings.showLineNumbers &&
                diffChanges.isNotEmpty() &&
                downPos.x in (geometry.effectiveGutterPx - with(density) { 14.dp.toPx() })..(geometry.effectiveGutterPx + with(density) { 4.dp.toPx() })
              ) {
                val tappedLine = geometry.docLineAtViewportY(downPos.y, session.foldModel)
                val hitChange =
                  diffChanges.firstOrNull { ch ->
                    if (ch.kind == LineChange.Kind.DELETED) abs(ch.newStart - tappedLine) <= 1
                    else tappedLine in ch.newStart until ch.newEnd
                  }
                if (hitChange != null) {
                  activeDiffHunk = if (activeDiffHunk == hitChange) null else hitChange
                  return@awaitEachGesture
                }
              }

              // Tap in line-number gutter toggles bookmark
              val bmLine = geometry.bookmarkActionAt(downPos, session)
              if (bmLine >= 0) {
                session.toggleBookmark(bmLine)
                return@awaitEachGesture
              }

              // Tap inside text area
              activeDiffHunk = null
              showPasteHistory = false

              val tappedDocLine = geometry.docLineAtViewportY(downPos.y, session.foldModel)
              // Tap on collapsed fold row unfolds it immediately
              if (session.foldModel.foldStartingAt(tappedDocLine) != null) {
                session.toggleFoldAtLine(tappedDocLine)
                return@awaitEachGesture
              }

              val dt = downTime - lastTapTime[0]
              val dist = (downPos - lastTapPos[0]).getDistance()
              if (dt <= doubleTapTimeout && dist <= with(density) { 36.dp.toPx() }) {
                tapStreak[0] = (tapStreak[0] + 1).coerceAtMost(3)
              } else {
                tapStreak[0] = 1
              }
              lastTapTime[0] = downTime
              lastTapPos[0] = downPos

              val offset = geometry.offsetAt(downPos, session.doc, session.foldModel, lineStyles, renderCache)
              focusRequester.requestFocus()
              when (tapStreak[0]) {
                2 -> {
                  session.selectWordAt(offset)
                  showTouchHandles = true
                }
                3 -> {
                  session.selectLineAt(tappedDocLine)
                  showTouchHandles = true
                }
                else -> {
                  session.setSelection(TextRange(offset))
                  showTouchHandles = false
                  if (!readOnly) imeHandle.showKeyboard()
                }
              }
            }
          }
          .drawBehind {
            drawEdgeEditor(
              EdgeEditorDrawParams(
                session = session,
                geometry = geometry,
                styles = lineStyles,
                cache = renderCache,
                gutterMeasurer = gutterMeasurer,
                gutterStyle = gutterTextStyle,
                palette = palette,
                hasCustomBg = hasCustomBg,
                indentGuides = editorSettings.indentGuides,
                gutterDiffMarkers = editorSettings.gutterDiffMarkers,
                tabSize = editorSettings.tabSize,
                diagnostics = diagnostics,
                findMatches = findMatches,
                activeFindMatchIndex = activeFindMatchIndex,
                bracketMatch = bracketMatch,
                lineChanges = diffChanges,
                caretSymbolWord = caretWord,
                caretAnimatedX = caretXAnim.value,
                caretAnimatedY = caretYAnim.value,
                caretAlpha = caretAlphaAnim.value,
                showCaret = isFocused && !readOnly,
                showTouchHandles = showTouchHandles,
              ),
            )
          },
    )

    // Overlay 1: Sticky Scroll Header
    EdgeStickyScrollHeader(
      visible = editorSettings.stickyHeaders && stickySymbol != null,
      symbol = stickySymbol,
      lineText = stickySymbol?.let { session.doc.lineText((it.line - 1).coerceIn(0, session.doc.lineCount - 1)) }.orEmpty(),
      onClick = {
        stickySymbol?.let { sym ->
          val targetLine = (sym.line - 1).coerceIn(0, (session.doc.lineCount - 1).coerceAtLeast(0))
          session.ensureLineUnfolded(targetLine)
          session.setSelection(TextRange(session.doc.lineStart(targetLine)))
          geometry.ensureCaretVisible(session, lineStyles, renderCache)
        }
      },
    )

    // Overlay 2: Fast-Scroll Outline Scrubber
    EdgeFastScrollLayer(
      geometry = geometry,
      session = session,
      symbols = symbols,
      onScrubToFraction = { frac ->
        val maxV = geometry.maxVerticalScroll(session.foldModel)
        geometry.vOffset = (frac * maxV).coerceIn(0f, maxV)
      },
    )

    // Overlay 3: Floating Selection Toolbar
    val selAnchorY =
      if (!session.selection.collapsed && activeHandleDrag == null) {
        geometry.caretGeometry(session.selection.min, session.doc, session.foldModel, lineStyles, renderCache).top
      } else {
        0f
      }
    EdgeSelectionToolbar(
      visible = !session.selection.collapsed && activeHandleDrag == null && isFocused,
      anchorY = selAnchorY,
      readOnly = readOnly,
      hasClipboardHistory = ClipboardHistoryStore.entries.isNotEmpty(),
      onCopy = {
        session.copySelection()?.let(::copyToSystemClipboard)
        showTouchHandles = false
      },
      onCut = {
        session.cutSelection()?.let(::copyToSystemClipboard)
        showTouchHandles = false
      },
      onPaste = {
        readSystemClipboard()?.let { session.commitText(it) }
        showTouchHandles = false
      },
      onOpenPasteHistory = { showPasteHistory = true },
      onSelectAll = {
        session.selectAll()
        showTouchHandles = true
      },
      onComment = {
        session.toggleLineComment()
        showTouchHandles = false
      },
      onRename = {
        val ident = session.selectedText()?.trim()?.takeIf { it.isNotEmpty() && !it.contains('\n') }
          ?: session.currentIdentifierAtCursor()
        flushSyncNow()
        latestOnRename(ident)
      },
    )

    // Overlay 4: Gutter Diff Hunk Preview & One-Tap Revert
    EdgeDiffHunkPreviewCard(
      change = activeDiffHunk,
      baseText = originalContent,
      readOnly = readOnly,
      onRevertHunk = { chg ->
        val (s, e, repl) = revertEdit(session.doc, originalContent, chg)
        session.replaceRange(s, e, repl, newCaret = s)
        activeDiffHunk = null
      },
      onDismiss = { activeDiffHunk = null },
    )

    // Overlay 5: Paste History Picker
    EdgePasteHistoryPopup(
      visible = showPasteHistory,
      onSelectClip = { clip -> session.commitText(clip) },
      onDismiss = { showPasteHistory = false },
    )

    // Overlay 6: Three-Finger Undo/Redo Toast
    EdgeUndoGestureToast(feedback = undoFeedback)
  }
}

private fun moveCaretHorizontal(session: EdgeEditorSession, delta: Int, extend: Boolean) {
  val cur = session.selection.end
  val next =
    if (!extend && !session.selection.collapsed) {
      if (delta < 0) session.selection.min else session.selection.max
    } else {
      (cur + delta).coerceIn(0, session.doc.length)
    }
  session.setSelection(if (extend) TextRange(session.selection.start, next) else TextRange(next))
}

private fun moveCaretVertical(session: EdgeEditorSession, deltaLines: Int, extend: Boolean) {
  val doc = session.doc
  val curLine = doc.lineForOffset(session.selection.end)
  val col = session.selection.end - doc.lineStart(curLine)
  val targetLine = (curLine + deltaLines).coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
  val next = doc.offsetOf(targetLine, col)
  session.setSelection(if (extend) TextRange(session.selection.start, next) else TextRange(next))
}
