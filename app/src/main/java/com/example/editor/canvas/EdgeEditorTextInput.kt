package com.example.editor.canvas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Matrix
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.SurroundingText
import android.view.inputmethod.TextAttribute
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireView
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import com.example.editor.engine.ClipboardHistoryStore
import com.example.editor.engine.EdgeEditorSession
import com.example.editor.engine.EditSpan
import com.example.editor.engine.ExtractedTextSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class EditorCaretRect(
  val horizontalPosition: Float,
  val top: Float,
  val baseline: Float,
  val bottom: Float,
)

@Immutable
data class EditorImeOptions(
  val softKeyboardSuggestions: Boolean = false,
  val readOnly: Boolean = false,
)

@Stable
class EditorImeHandle {
  internal var onShow: (() -> Unit)? = null
  internal var onHide: (() -> Unit)? = null
  var caretGeometryProvider: (() -> EditorCaretRect?)? = null

  fun showKeyboard() {
    onShow?.invoke()
  }

  fun hideKeyboard() {
    onHide?.invoke()
  }
}

fun Modifier.edgeEditorTextInput(
  session: EdgeEditorSession,
  ime: EditorImeHandle,
  options: EditorImeOptions,
): Modifier = this then EdgeEditorTextInputElement(session, ime, options)

fun textInputCodePoint(event: KeyEvent): Int {
  val native = event.nativeKeyEvent
  if (AndroidKeyEvent.isModifierKey(native.keyCode)) return -1
  if (native.isCtrlPressed || native.isMetaPressed) return -1
  val cp = native.unicodeChar
  return if (cp in 32..0x10FFFF && cp != 127) cp else -1
}

private data class EdgeEditorTextInputElement(
  val session: EdgeEditorSession,
  val ime: EditorImeHandle,
  val options: EditorImeOptions,
) : ModifierNodeElement<EdgeEditorTextInputNode>() {
  override fun create(): EdgeEditorTextInputNode = EdgeEditorTextInputNode(session, ime, options)

  override fun update(node: EdgeEditorTextInputNode) {
    node.setSession(session, ime, options)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "edgeEditorTextInput"
    properties["softKeyboardSuggestions"] = options.softKeyboardSuggestions
    properties["readOnly"] = options.readOnly
  }
}

private class EdgeEditorTextInputNode(
  private var session: EdgeEditorSession,
  private var ime: EditorImeHandle,
  private var options: EditorImeOptions,
) : Modifier.Node(), PlatformTextInputModifierNode, FocusEventModifierNode {

  private var job: Job? = null
  private var focused = false
  private var wantsKeyboard = false

  fun setSession(s: EdgeEditorSession, h: EditorImeHandle, opts: EditorImeOptions) {
    if (h !== ime) {
      ime.onShow = null
      ime.onHide = null
      ime = h
      registerHandle()
    }
    val optionsChanged = opts != options
    options = opts
    if (opts.readOnly) {
      wantsKeyboard = false
      stopSession()
      return
    }
    if (s !== session) {
      session = s
      wantsKeyboard = false
      stopSession()
      return
    }
    if (optionsChanged && job != null && isAttached) {
      restartInput()
    }
  }

  override fun onAttach() {
    registerHandle()
  }

  private fun registerHandle() {
    ime.onShow = {
      if (!options.readOnly) {
        wantsKeyboard = true
        if (focused) {
          if (job == null) startSession() else showSoftKeyboard()
        }
      }
    }
    ime.onHide = {
      wantsKeyboard = false
      stopSession()
    }
  }

  override fun onFocusEvent(focusState: FocusState) {
    val now = focusState.isFocused
    if (now == focused) return
    focused = now
    if (now) {
      if (wantsKeyboard && !options.readOnly && job == null) startSession()
    } else {
      wantsKeyboard = false
      stopSession()
    }
  }

  override fun onDetach() {
    ime.onShow = null
    ime.onHide = null
    stopSession()
  }

  private fun startSession() {
    job?.cancel()
    val s = session
    val h = ime
    job =
      coroutineScope.launch {
        establishTextInputSession {
          val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
          val bridge = EditorImeBridge(s, view, imm, h)
          s.imeListener = bridge
          try {
            startInputMethod(EditorImeRequest(s, view, bridge) { options })
          } finally {
            bridge.cancelPendingRestart()
            if (s.imeListener === bridge) s.imeListener = null
          }
        }
      }
  }

  private fun stopSession() {
    val hadSession = job != null
    job?.cancel()
    job = null
    session.imeFinishComposing()
    if (hadSession) hideSoftKeyboard()
  }

  private fun showSoftKeyboard() {
    if (!isAttached) return
    val view = requireView()
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(view, 0)
  }

  private fun hideSoftKeyboard() {
    if (!isAttached) return
    val view = requireView()
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
  }

  private fun restartInput() {
    if (!isAttached) return
    val view = requireView()
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.restartInput(view)
  }
}

private class EditorImeRequest(
  private val session: EdgeEditorSession,
  private val view: View,
  private val bridge: EditorImeBridge,
  private val options: () -> EditorImeOptions,
) : PlatformTextInputMethodRequest {
  override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
    val rawMode = !options().softKeyboardSuggestions
    var inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    if (rawMode) {
      inputType = inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      if (imeIgnoresNoSuggestions(view.context)) {
        inputType = inputType or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
      }
    }
    outAttributes.inputType = inputType
    outAttributes.imeOptions =
      EditorInfo.IME_FLAG_NO_FULLSCREEN or
        EditorInfo.IME_FLAG_NO_EXTRACT_UI or
        EditorInfo.IME_ACTION_NONE
    outAttributes.initialSelStart = session.selection.min
    outAttributes.initialSelEnd = session.selection.max
    bridge.onNewConnection()
    return EditorInputConnection(session, view, bridge, rawMode)
  }
}

private const val MAX_EXTRACT_CHARS = 100_000
private const val NO_MONITOR = -1
private const val RESTART_QUIET_MS = 120L
private const val RESTART_MAX_DELAY_MS = 1_000L

private val NO_SUGGESTIONS_DEAF_IMES =
  arrayOf(
    "com.touchtype.swiftkey",
  )

private fun imeIgnoresNoSuggestions(context: Context): Boolean {
  val ime =
    runCatching {
      Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    }.getOrNull() ?: return false
  val pkg = ime.substringBefore('/')
  return NO_SUGGESTIONS_DEAF_IMES.any { pkg == it || pkg.startsWith("$it.") }
}

private class EditorImeBridge(
  private val session: EdgeEditorSession,
  private val view: View,
  private val imm: InputMethodManager,
  private val handle: EditorImeHandle,
) : EdgeEditorSession.ImeListener {

  var monitorToken = NO_MONITOR
  var cursorUpdateMode = 0
  var connectionGeneration = 0
  private var mirrorWindowed = false

  fun onNewConnection() {
    monitorToken = NO_MONITOR
    cursorUpdateMode = 0
    mirrorWindowed = false
    cancelPendingRestart()
  }

  private var restartRequestedAt = 0L
  private val restartRunnable =
    Runnable {
      val now = SystemClock.uptimeMillis()
      if (session.composing != null && now < restartRequestedAt + RESTART_MAX_DELAY_MS) {
        scheduleRestart()
        return@Runnable
      }
      restartRequestedAt = 0L
      if (view.isAttachedToWindow) imm.restartInput(view)
    }

  private var lastImeInputAt = 0L

  override fun onRestartInput() {
    val now = SystemClock.uptimeMillis()
    if (restartRequestedAt == 0L && now - lastImeInputAt >= RESTART_QUIET_MS) {
      if (view.isAttachedToWindow) imm.restartInput(view)
      return
    }
    if (restartRequestedAt == 0L) restartRequestedAt = now
    scheduleRestart()
  }

  fun onImeInput() {
    lastImeInputAt = SystemClock.uptimeMillis()
    if (restartRequestedAt != 0L) scheduleRestart()
  }

  fun cancelPendingRestart() {
    restartRequestedAt = 0L
    view.removeCallbacks(restartRunnable)
  }

  private fun scheduleRestart() {
    view.removeCallbacks(restartRunnable)
    val now = SystemClock.uptimeMillis()
    val at = minOf(now + RESTART_QUIET_MS, restartRequestedAt + RESTART_MAX_DELAY_MS)
    view.postDelayed(restartRunnable, (at - now).coerceAtLeast(0L))
  }

  override fun onStateChanged() {
    pushSelection()
    maybePushCursorAnchor()
  }

  override fun onTextChanged(span: EditSpan?) {
    if (monitorToken != NO_MONITOR) {
      val et =
        if (span != null && !mirrorWindowed && session.doc.length <= MAX_EXTRACT_CHARS) {
          session.partialExtractedSnapshot(span).toAndroid()
        } else {
          fullExtracted()
        }
      imm.updateExtractedText(view, monitorToken, et)
    }
    pushSelection()
    maybePushCursorAnchor()
  }

  fun fullExtracted(): ExtractedText {
    val et = session.extractedTextSnapshot(MAX_EXTRACT_CHARS).toAndroid()
    mirrorWindowed = et.startOffset != 0
    return et
  }

  override fun isSyncingExtractedText(): Boolean = monitorToken != NO_MONITOR

  private fun pushSelection() {
    val sel = session.selection
    val comp = session.composing
    imm.updateSelection(view, sel.min, sel.max, comp?.min ?: -1, comp?.max ?: -1)
  }

  private fun maybePushCursorAnchor() {
    if (cursorUpdateMode and InputConnection.CURSOR_UPDATE_MONITOR != 0) pushCursorAnchor()
  }

  fun pushCursorAnchor() {
    imm.updateCursorAnchorInfo(view, buildCursorAnchorInfo(session, view, handle))
  }
}

private fun ExtractedTextSnapshot.toAndroid(): ExtractedText {
  val et = ExtractedText()
  et.text = text
  et.startOffset = startOffset
  et.selectionStart = selectionStart
  et.selectionEnd = selectionEnd
  et.partialStartOffset = partialStartOffset
  et.partialEndOffset = partialEndOffset
  et.flags = if (selectionStart != selectionEnd) ExtractedText.FLAG_SELECTING else 0
  return et
}

private fun buildCursorAnchorInfo(
  session: EdgeEditorSession,
  view: View,
  handle: EditorImeHandle,
): CursorAnchorInfo {
  val sel = session.selection
  val comp = session.composing
  val b = CursorAnchorInfo.Builder()
  b.setSelectionRange(sel.min, sel.max)
  if (comp != null && comp.min < comp.max) {
    b.setComposingText(comp.min, session.doc.substring(comp.min, comp.max))
  }
  val loc = IntArray(2)
  view.getLocationOnScreen(loc)
  b.setMatrix(Matrix().apply { setTranslate(loc[0].toFloat(), loc[1].toFloat()) })
  handle.caretGeometryProvider?.invoke()?.let { c ->
    b.setInsertionMarkerLocation(
      c.horizontalPosition,
      c.top,
      c.baseline,
      c.bottom,
      CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION,
    )
  }
  return b.build()
}

private class EditorInputConnection(
  private val session: EdgeEditorSession,
  private val view: View,
  private val bridge: EditorImeBridge,
  private val rawMode: Boolean,
) : BaseInputConnection(view, true) {

  private val generation = ++bridge.connectionGeneration
  private var batchDepth = 0

  override fun beginBatchEdit(): Boolean {
    batchDepth++
    session.beginBatch()
    return true
  }

  override fun endBatchEdit(): Boolean {
    if (batchDepth > 0) {
      batchDepth--
      session.endBatch()
    }
    return batchDepth > 0
  }

  override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
    bridge.onImeInput()
    session.imeCommitText(text?.toString() ?: "", newCursorPosition)
    return true
  }

  override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
    bridge.onImeInput()
    val s = text?.toString() ?: ""
    if ('\n' in s) return false
    session.imeSetComposingText(s, newCursorPosition)
    return true
  }

  override fun setComposingRegion(start: Int, end: Int): Boolean {
    if (rawMode) return false
    session.imeSetComposingRegion(start, end)
    return true
  }

  override fun finishComposingText(): Boolean {
    bridge.onImeInput()
    session.imeFinishComposing()
    return true
  }

  override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
    bridge.onImeInput()
    if (beforeLength < 0 || afterLength < 0) return false
    session.imeDeleteSurrounding(beforeLength, afterLength)
    return true
  }

  override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
    if (beforeLength < 0 || afterLength < 0) return false
    val text = session.doc.chars
    val sel = session.selection
    val comp = session.composing
    var before = 0
    var i = minOf(sel.min, comp?.min ?: sel.min)
    var remaining = beforeLength
    while (remaining > 0 && i > 0) {
      val step = if (i >= 2 && text[i - 1].isLowSurrogate() && text[i - 2].isHighSurrogate()) 2 else 1
      i -= step
      before += step
      remaining--
    }
    var after = 0
    var j = maxOf(sel.max, comp?.max ?: sel.max)
    remaining = afterLength
    while (remaining > 0 && j < text.length) {
      val step = if (j + 1 < text.length && text[j].isHighSurrogate() && text[j + 1].isLowSurrogate()) 2 else 1
      j += step
      after += step
      remaining--
    }
    session.imeDeleteSurrounding(before, after)
    return true
  }

  override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence =
    if (rawMode) "" else session.imeTextBeforeCursor(n)

  override fun getTextAfterCursor(n: Int, flags: Int): CharSequence =
    if (rawMode) "" else session.imeTextAfterCursor(n)

  override fun getSelectedText(flags: Int): CharSequence? =
    if (rawMode) null else session.selectedText()

  @RequiresApi(Build.VERSION_CODES.S)
  override fun getSurroundingText(beforeLength: Int, afterLength: Int, flags: Int): SurroundingText {
    if (rawMode || beforeLength < 0 || afterLength < 0) return SurroundingText("", 0, 0, -1)
    val before = session.imeTextBeforeCursor(beforeLength)
    val selected = session.selectedText() ?: ""
    val after = session.imeTextAfterCursor(afterLength)
    return SurroundingText(
      before + selected + after,
      before.length,
      before.length + selected.length,
      session.selection.min - before.length,
    )
  }

  override fun setSelection(start: Int, end: Int): Boolean {
    if (rawMode) return false
    session.imeSetSelection(start, end)
    return true
  }

  @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  override fun replaceText(
    start: Int,
    end: Int,
    text: CharSequence,
    newCursorPosition: Int,
    textAttribute: TextAttribute?,
  ): Boolean {
    if (start < 0 || end < 0) return false
    if (rawMode) {
      session.imeRewriteRefused()
      return false
    }
    session.imeReplaceText(start, end, text.toString(), newCursorPosition)
    return true
  }

  override fun getCursorCapsMode(reqModes: Int): Int = 0

  override fun sendKeyEvent(event: AndroidKeyEvent?): Boolean {
    bridge.onImeInput()
    return super.sendKeyEvent(event)
  }

  override fun performEditorAction(actionCode: Int): Boolean {
    session.commitText("\n")
    return true
  }

  override fun performContextMenuAction(id: Int): Boolean {
    when (id) {
      android.R.id.selectAll -> session.selectAll()
      android.R.id.cut -> session.cutSelection()?.let(::setClipboardText)
      android.R.id.copy -> session.copySelection()?.let(::setClipboardText)
      android.R.id.paste, android.R.id.pasteAsPlainText -> clipboardText()?.let(session::commitText)
      android.R.id.undo -> session.undo()
      android.R.id.redo -> session.redo()
      else -> return super.performContextMenuAction(id)
    }
    return true
  }

  private fun setClipboardText(text: String) {
    val cm = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(null, ClipboardHistoryStore.clipForClipboard(text)))
  }

  private fun clipboardText(): String? {
    val cm = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val item = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
    val plain = item.text?.toString()?.takeIf { it.isNotEmpty() }
    if (plain != null) return plain
    val coerced = item.coerceToText(view.context)?.toString()?.takeIf { it.isNotEmpty() } ?: return null
    return ClipboardHistoryStore.stripSimpleHtml(coerced)
  }

  override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
    if (rawMode) return null
    if (request != null && flags and InputConnection.GET_EXTRACTED_TEXT_MONITOR != 0) {
      bridge.monitorToken = request.token
    }
    return bridge.fullExtracted()
  }

  override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean {
    bridge.cursorUpdateMode = cursorUpdateMode
    if (cursorUpdateMode and InputConnection.CURSOR_UPDATE_IMMEDIATE != 0) bridge.pushCursorAnchor()
    return true
  }

  override fun closeConnection() {
    while (batchDepth > 0) {
      batchDepth--
      session.endBatch()
    }
    if (bridge.connectionGeneration == generation) {
      bridge.monitorToken = NO_MONITOR
      bridge.cursorUpdateMode = 0
      session.imeFinishComposing()
    }
    super.closeConnection()
  }
}
