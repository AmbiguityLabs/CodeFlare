package com.example.editor.engine

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import com.example.data.remote.WorkerLanguage
import com.example.editor.EditorCursorTelemetry
import kotlin.math.abs

/**
 * Contiguous edit span in pre-edit coordinates for partial IME `updateExtractedText` and
 * incremental line-style splicing.
 */
data class EditSpan(
  val start: Int,
  val removed: Int,
  val added: Int,
  val editStartLine: Int,
  val editOldEndLine: Int,
  val lineDelta: Int,
)

/**
 * Platform-independent snapshot matching Android's `ExtractedText` contract.
 */
data class ExtractedTextSnapshot(
  val text: String,
  val startOffset: Int,
  val selectionStart: Int,
  val selectionEnd: Int,
  val partialStartOffset: Int,
  val partialEndOffset: Int,
)

private sealed interface UndoRecord {
  data class SingleEdit(
    val start: Int,
    val removedText: String,
    val insertedText: String,
    val selectionBefore: TextRange,
    val selectionAfter: TextRange,
    val timestampMs: Long,
    val kind: EditKind,
  ) : UndoRecord

  /**
   * Atomic same-document move recorded in O(movedText.length) memory.
   * Forward removes [srcStart, srcStart + movedText.length) then inserts [movedText] at [adjustedDst].
   * Undo removes [adjustedDst, adjustedDst + movedText.length) then inserts [movedText] back at [srcStart].
   */
  data class MoveEdit(
    val srcStart: Int,
    val movedText: String,
    val adjustedDst: Int,
    val selectionBefore: TextRange,
    val selectionAfter: TextRange,
  ) : UndoRecord
}

private enum class EditKind {
  INSERT,
  DELETE,
  ATOMIC,
}

/**
 * Authoritative Compose snapshot state buffer for the CodeAssist-powered EdgeCode editor.
 *
 * - Stores text in an immutable [EditorDocument] (Rope + incremental lineStarts).
 * - Coalesces rapid typing / backspacing into crisp Undo/Redo entries (up to 150 steps).
 * - Handles smart Enter auto-indent, JS/TS/JSON bracket/quote auto-close, skip-over, and pair delete.
 * - Exposes full synchronous Android IME bridge operations (`imeCommitText`, `imeSetComposingText`,
 *   `imeDeleteSurrounding`, `imeReplaceText`, batching, partial `ExtractedTextSnapshot`).
 */
@Stable
class EdgeEditorSession(
  initialText: String = "",
  initialSelection: TextRange = TextRange.Zero,
) {
  interface ImeListener {
    fun onStateChanged()
    fun onTextChanged(span: EditSpan?)
    fun onRestartInput()
    fun isSyncingExtractedText(): Boolean
  }

  var doc: EditorDocument by mutableStateOf(EditorDocument.of(initialText))
    private set

  var selection: TextRange by mutableStateOf(clampRange(initialSelection, doc.length))
    private set

  var composing: TextRange? by mutableStateOf(null)
    private set

  var textRevision: Int by mutableIntStateOf(0)
    private set

  var editCount: Int by mutableIntStateOf(0)
    private set

  var maxLineChars: Int by mutableIntStateOf(computeMaxLineChars(doc))
    private set

  var foldRegions: List<FoldRegion> by mutableStateOf(emptyList())
    private set

  var collapsedFolds: Set<FoldRegion> by mutableStateOf(emptySet())
    private set

  var foldModel: FoldModel by mutableStateOf(FoldModel.flat(doc.lineCount))
    private set

  var bookmarks: Set<Int> by mutableStateOf(emptySet()) // 0-based doc lines
    private set

  var readOnly: Boolean = false
  var autoCloseBrackets: Boolean = true
  var tabSize: Int = 2
  var language: WorkerLanguage = WorkerLanguage.JAVASCRIPT

  var imeListener: ImeListener? = null
  var onSpliceStyles: ((doc: EditorDocument, startLine: Int, oldEndLine: Int, lineDelta: Int) -> Unit)? = null
  var onUserEdit: (() -> Unit)? = null

  private val undoStack = ArrayDeque<UndoRecord>()
  private val redoStack = ArrayDeque<UndoRecord>()
  private var batchDepth = 0
  private var batchHadTextChange = false
  private var batchHadStateChange = false
  private var batchSingleSpan: EditSpan? = null
  private var batchMultipleSpans = false
  private var lastEditTimeMs = 0L

  val canUndo: Boolean get() = undoStack.isNotEmpty()
  val canRedo: Boolean get() = redoStack.isNotEmpty()
  val hasSelection: Boolean get() = !selection.collapsed

  fun loadDocument(
    text: String,
    newSelection: TextRange = TextRange.Zero,
    resetHistory: Boolean = true,
  ) {
    val nextDoc = EditorDocument.of(text)
    doc = nextDoc
    selection = clampRange(newSelection, nextDoc.length)
    composing = null
    maxLineChars = computeMaxLineChars(nextDoc)
    collapsedFolds = emptySet()
    foldModel = FoldModel.flat(nextDoc.lineCount)
    if (resetHistory) {
      undoStack.clear()
      redoStack.clear()
      bookmarks = emptySet()
    }
    textRevision++
    imeListener?.onRestartInput()
  }

  fun updateFoldRegions(detected: List<FoldRegion>) {
    foldRegions = detected
    if (collapsedFolds.isNotEmpty()) {
      val validStarts = detected.associateBy { it.startLine }
      val nextCollapsed = collapsedFolds.mapNotNull { validStarts[it.startLine] }.toSet()
      if (nextCollapsed != collapsedFolds) {
        collapsedFolds = nextCollapsed
        foldModel = FoldModel(doc.lineCount, nextCollapsed.toList())
      }
    }
  }

  fun toggleFoldAtLine(docLine: Int): Boolean {
    val existingCollapsed = collapsedFolds.firstOrNull { it.startLine == docLine }
    if (existingCollapsed != null) {
      val next = collapsedFolds - existingCollapsed
      collapsedFolds = next
      foldModel = FoldModel(doc.lineCount, next.toList())
      return true
    }
    val candidate = foldRegions.firstOrNull { it.startLine == docLine } ?: return false
    val next = collapsedFolds + candidate
    collapsedFolds = next
    foldModel = FoldModel(doc.lineCount, next.toList())
    // Move caret out of newly hidden region onto fold header
    val caretLine = doc.lineForOffset(selection.end)
    if (foldModel.isHidden(caretLine)) {
      setSelection(TextRange(doc.lineEnd(docLine)))
    }
    return true
  }

  fun foldAll(): Int {
    if (foldRegions.isEmpty()) return 0
    val topLevel = ArrayList<FoldRegion>()
    var covered = -1
    for (r in foldRegions.sortedBy { it.startLine }) {
      if (r.startLine > covered) {
        topLevel.add(r)
        covered = r.endLine
      }
    }
    collapsedFolds = topLevel.toSet()
    foldModel = FoldModel(doc.lineCount, topLevel)
    val caretLine = doc.lineForOffset(selection.end)
    val hiddenIn = foldModel.foldContaining(caretLine)
    if (hiddenIn != null) setSelection(TextRange(doc.lineEnd(hiddenIn.startLine)))
    return topLevel.size
  }

  fun unfoldAll() {
    if (collapsedFolds.isEmpty()) return
    collapsedFolds = emptySet()
    foldModel = FoldModel.flat(doc.lineCount)
  }

  fun ensureLineUnfolded(docLine: Int) {
    val containing = foldModel.foldContaining(docLine) ?: return
    val next = collapsedFolds.filterNot { docLine > it.startLine && docLine <= it.endLine }.toSet()
    collapsedFolds = next
    foldModel = FoldModel(doc.lineCount, next.toList())
  }

  fun toggleBookmark(docLine: Int = doc.lineForOffset(selection.end)): Boolean {
    val safe = docLine.coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
    return if (safe in bookmarks) {
      bookmarks = bookmarks - safe
      false
    } else {
      bookmarks = bookmarks + safe
      true
    }
  }

  fun nextBookmarkLine(): Int? {
    if (bookmarks.isEmpty()) return null
    val cur = doc.lineForOffset(selection.end)
    val sorted = bookmarks.sorted()
    val target = sorted.firstOrNull { it > cur } ?: sorted.first()
    ensureLineUnfolded(target)
    setSelection(TextRange(doc.lineStart(target)))
    return target
  }

  fun selectedText(): String? {
    if (selection.collapsed) return null
    return doc.substring(selection.min, selection.max)
  }

  fun cutSelection(): String? {
    if (readOnly || selection.collapsed) return null
    val txt = doc.substring(selection.min, selection.max)
    ClipboardHistoryStore.record(txt)
    applyEdit(selection.min, selection.max, "", TextRange(selection.min), EditKind.ATOMIC, resyncIme = true)
    return txt
  }

  fun copySelection(): String? {
    val txt = selectedText() ?: return null
    ClipboardHistoryStore.record(txt)
    return txt
  }

  fun setSelection(range: TextRange, finishComposing: Boolean = true) {
    val clamped = clampRange(range, doc.length)
    val changed = clamped != selection || (finishComposing && composing != null)
    if (!changed) return
    selection = clamped
    if (finishComposing) composing = null
    ensureLineUnfolded(doc.lineForOffset(clamped.end))
    notifyStateChanged()
  }

  fun selectAll() {
    setSelection(TextRange(0, doc.length))
  }

  fun selectWordAt(offset: Int) {
    val pos = offset.coerceIn(0, doc.length)
    if (doc.length == 0) return
    val chars = doc.chars
    val probe = if (pos < doc.length && isIdentChar(chars[pos])) pos else (pos - 1).coerceAtLeast(0)
    if (!isIdentChar(chars[probe])) {
      val end = (probe + 1).coerceAtMost(doc.length)
      setSelection(TextRange(probe, end))
      return
    }
    var s = probe
    while (s > 0 && isIdentChar(chars[s - 1])) s--
    var e = probe + 1
    while (e < doc.length && isIdentChar(chars[e])) e++
    setSelection(TextRange(s, e))
  }

  fun selectLineAt(docLine: Int) {
    val safe = docLine.coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
    val start = doc.lineStart(safe)
    val end = if (safe + 1 < doc.lineCount) doc.lineStart(safe + 1) else doc.lineEnd(safe)
    setSelection(TextRange(start, end))
  }

  fun currentIdentifierAtCursor(): String? {
    if (doc.length == 0) return null
    val pos = selection.end.coerceIn(0, doc.length)
    val chars = doc.chars
    val probe =
      when {
        pos < doc.length && isIdentChar(chars[pos]) -> pos
        pos > 0 && isIdentChar(chars[pos - 1]) -> pos - 1
        else -> return null
      }
    var s = probe
    while (s > 0 && isIdentChar(chars[s - 1])) s--
    var e = probe + 1
    while (e < doc.length && isIdentChar(chars[e])) e++
    return if (e > s) doc.substring(s, e) else null
  }

  fun wordBeforeCursor(): String {
    val pos = selection.end.coerceIn(0, doc.length)
    if (pos == 0) return ""
    val chars = doc.chars
    var s = pos
    while (s > 0 && (isIdentChar(chars[s - 1]) || chars[s - 1] == '.')) s--
    return if (pos > s) doc.substring(s, pos) else ""
  }

  fun buildTelemetry(): EditorCursorTelemetry {
    val pos = selection.end.coerceIn(0, doc.length)
    val line0 = doc.lineForOffset(pos)
    val col0 = pos - doc.lineStart(line0)
    return EditorCursorTelemetry(
      line = line0 + 1,
      col = col0 + 1,
      lineCount = doc.lineCount,
      wordBeforeCursor = wordBeforeCursor(),
      cursorOffset = pos,
    )
  }

  // ---- High-level Editor Actions (Toolbar / Key Rail / Keyboard Shortcuts) ----

  fun commitText(text: String) {
    if (readOnly) return
    val normalized = EditorDocument.normalizeNewlines(text)
    if (normalized == "\n") {
      insertSmartNewline(resyncIme = true)
      return
    }
    if (autoCloseBrackets && normalized.length == 1 && selection.collapsed) {
      if (handleSingleCharAutoClose(normalized[0], resyncIme = true)) return
    }
    val start = selection.min
    val end = selection.max
    applyEdit(start, end, normalized, TextRange(start + normalized.length), EditKind.INSERT, resyncIme = true)
  }

  fun insertSnippet(snippet: String, cursorBack: Int = 0) {
    if (readOnly) return
    val start = selection.min
    val end = selection.max
    val normalized = EditorDocument.normalizeNewlines(snippet)
    val targetCaret = (start + normalized.length - cursorBack).coerceIn(start, start + normalized.length)
    applyEdit(start, end, normalized, TextRange(targetCaret), EditKind.ATOMIC, resyncIme = true)
  }

  fun insertTab() {
    if (readOnly) return
    val indent = " ".repeat(tabSize.coerceIn(1, 8))
    if (selection.collapsed) {
      insertSnippet(indent, 0)
      return
    }
    // Multi-line selection indent
    val startLine = doc.lineForOffset(selection.min)
    val endLine = doc.lineForOffset((selection.max - 1).coerceAtLeast(selection.min))
    val blockStart = doc.lineStart(startLine)
    val blockEnd = doc.lineEnd(endLine)
    val lines = doc.substring(blockStart, blockEnd).split('\n')
    val indented = lines.joinToString("\n") { indent + it }
    applyEdit(blockStart, blockEnd, indented, TextRange(blockStart, blockStart + indented.length), EditKind.ATOMIC, resyncIme = true)
  }

  fun outdentSelection() {
    if (readOnly) return
    val startLine = doc.lineForOffset(selection.min)
    val endLine = doc.lineForOffset(if (selection.collapsed) selection.min else (selection.max - 1).coerceAtLeast(selection.min))
    val blockStart = doc.lineStart(startLine)
    val blockEnd = doc.lineEnd(endLine)
    val lines = doc.substring(blockStart, blockEnd).split('\n')
    var removedFirst = 0
    val outdented =
      lines.mapIndexed { idx, l ->
        var strip = 0
        if (l.startsWith('\t')) {
          strip = 1
        } else {
          while (strip < tabSize && strip < l.length && l[strip] == ' ') strip++
        }
        if (idx == 0) removedFirst = strip
        l.substring(strip)
      }.joinToString("\n")
    val nextSel =
      if (selection.collapsed) {
        TextRange((selection.min - removedFirst).coerceAtLeast(blockStart))
      } else {
        TextRange(blockStart, blockStart + outdented.length)
      }
    applyEdit(blockStart, blockEnd, outdented, nextSel, EditKind.ATOMIC, resyncIme = true)
  }

  fun replaceBeforeCursor(replaceLen: Int, insertText: String, cursorBack: Int = 0) {
    if (readOnly) return
    val end = selection.max
    val start = (selection.min - replaceLen.coerceAtLeast(0)).coerceAtLeast(0)
    applyCompletionEdit(start, end, insertText, cursorBack)
  }

  fun applyCompletionEdit(replaceStart: Int, replaceEnd: Int, insertText: String, cursorBack: Int = 0) {
    if (readOnly) return
    val s = replaceStart.coerceIn(0, doc.length)
    val e = replaceEnd.coerceIn(s, doc.length)
    val normalized = EditorDocument.normalizeNewlines(insertText)
    val targetCaret = (s + normalized.length - cursorBack).coerceIn(s, s + normalized.length)
    applyEdit(s, e, normalized, TextRange(targetCaret), EditKind.ATOMIC, resyncIme = true)
  }

  fun replaceRange(start: Int, end: Int, replacement: String, newCaret: Int? = null) {
    if (readOnly) return
    val s = start.coerceIn(0, doc.length)
    val e = end.coerceIn(s, doc.length)
    val normalized = EditorDocument.normalizeNewlines(replacement)
    val caret = newCaret ?: (s + normalized.length)
    applyEdit(s, e, normalized, TextRange(caret.coerceIn(0, doc.length - (e - s) + normalized.length)), EditKind.ATOMIC, resyncIme = true)
  }

  /**
   * Inserts [text] at [offset] as a single atomic undo step, auto-unfolding the target line.
   * Returns true if any non-empty text was inserted.
   */
  fun insertAt(offset: Int, text: String, selectInserted: Boolean = true): Boolean {
    if (readOnly || text.isEmpty()) return false
    val normalized = EditorDocument.normalizeNewlines(text)
    if (normalized.isEmpty()) return false
    val pos = offset.coerceIn(0, doc.length)
    ensureLineUnfolded(doc.lineForOffset(pos))
    val nextSel =
      if (selectInserted) TextRange(pos, pos + normalized.length)
      else TextRange(pos + normalized.length)
    applyEdit(pos, pos, normalized, nextSel, EditKind.ATOMIC, resyncIme = true)
    return true
  }

  /**
   * Atomically moves `[sourceStart, sourceEnd)` to [destinationOffset] within the same document
   * as ONE compound undo/redo step.
   *
   * - Dropping inside or directly touching `[sourceStart, sourceEnd]` is a clean no-op (returns false).
   * - Automatically adjusts destination offset when moving forward past [sourceEnd].
   * - Unfolds target line if folded so moved text is immediately visible.
   */
  fun moveRange(sourceStart: Int, sourceEnd: Int, destinationOffset: Int): Boolean {
    if (readOnly) return false
    val s = sourceStart.coerceIn(0, doc.length)
    val e = sourceEnd.coerceIn(s, doc.length)
    val movedLen = e - s
    if (movedLen <= 0) return false
    val rawDst = destinationOffset.coerceIn(0, doc.length)
    if (rawDst in s..e) return false

    val movedText = doc.substring(s, e)
    val adjustedDst = if (rawDst > e) rawDst - movedLen else rawDst
    val selBefore = selection
    val selAfter = TextRange(adjustedDst, adjustedDst + movedLen)

    ensureLineUnfolded(doc.lineForOffset(rawDst))
    ensureLineUnfolded(doc.lineForOffset(s))

    undoStack.addLast(
      UndoRecord.MoveEdit(
        srcStart = s,
        movedText = movedText,
        adjustedDst = adjustedDst,
        selectionBefore = selBefore,
        selectionAfter = selAfter,
      ),
    )
    while (undoStack.size > MAX_UNDO_ENTRIES) {
      undoStack.removeFirst()
    }
    redoStack.clear()
    lastEditTimeMs = System.currentTimeMillis()

    applyRawMove(
      removeAt = s,
      insertAt = adjustedDst,
      movedText = movedText,
      nextSelection = selAfter,
    )
    return true
  }

  fun backspace(resyncIme: Boolean = false) {
    if (readOnly) return
    if (!selection.collapsed) {
      applyEdit(selection.min, selection.max, "", TextRange(selection.min), EditKind.DELETE, resyncIme = resyncIme)
      return
    }
    val pos = selection.min
    if (pos <= 0) return
    // Pair backspace: deleting '(' when immediately followed by ')' removes both
    if (autoCloseBrackets && pos < doc.length) {
      val prev = doc.charAt(pos - 1)
      val next = doc.charAt(pos)
      if (matchingCloser(prev) == next) {
        applyEdit(pos - 1, pos + 1, "", TextRange(pos - 1), EditKind.DELETE, resyncIme = true)
        return
      }
    }
    val step = if (pos >= 2 && doc.charAt(pos - 1).isLowSurrogate() && doc.charAt(pos - 2).isHighSurrogate()) 2 else 1
    applyEdit(pos - step, pos, "", TextRange(pos - step), EditKind.DELETE, resyncIme = resyncIme)
  }

  fun deleteForward() {
    if (readOnly) return
    if (!selection.collapsed) {
      applyEdit(selection.min, selection.max, "", TextRange(selection.min), EditKind.DELETE, resyncIme = true)
      return
    }
    val pos = selection.min
    if (pos >= doc.length) return
    val step = if (pos + 1 < doc.length && doc.charAt(pos).isHighSurrogate() && doc.charAt(pos + 1).isLowSurrogate()) 2 else 1
    applyEdit(pos, pos + step, "", TextRange(pos), EditKind.DELETE, resyncIme = true)
  }

  fun toggleLineComment() {
    if (readOnly || language == WorkerLanguage.JSON || language == WorkerLanguage.TEXT) return
    val startLine = doc.lineForOffset(selection.min)
    val endLine = doc.lineForOffset(if (selection.collapsed) selection.min else (selection.max - 1).coerceAtLeast(selection.min))
    val blockStart = doc.lineStart(startLine)
    val blockEnd = doc.lineEnd(endLine)
    val lines = doc.substring(blockStart, blockEnd).split('\n')
    val allCommented = lines.filter { it.isNotBlank() }.all { it.trimStart().startsWith("//") }
    val updated =
      lines.joinToString("\n") { line ->
        if (allCommented) {
          val idx = line.indexOf("//")
          if (idx >= 0) {
            val after = if (idx + 2 < line.length && line[idx + 2] == ' ') idx + 3 else idx + 2
            line.substring(0, idx) + line.substring(after)
          } else {
            line
          }
        } else {
          if (line.isBlank()) line
          else {
            val lead = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            line.substring(0, lead) + "// " + line.substring(lead)
          }
        }
      }
    val nextSel =
      if (selection.collapsed) TextRange(blockStart + updated.length)
      else TextRange(blockStart, blockStart + updated.length)
    applyEdit(blockStart, blockEnd, updated, nextSel, EditKind.ATOMIC, resyncIme = true)
  }

  fun moveLineUp() {
    if (readOnly) return
    val line = doc.lineForOffset(selection.end)
    if (line <= 0) return
    val col = selection.end - doc.lineStart(line)
    val prevStart = doc.lineStart(line - 1)
    val curEnd = doc.lineEnd(line)
    val prevText = doc.lineText(line - 1)
    val curText = doc.lineText(line)
    val swapped = "$curText\n$prevText"
    val newCaret = (prevStart + col.coerceAtMost(curText.length))
    applyEdit(prevStart, curEnd, swapped, TextRange(newCaret), EditKind.ATOMIC, resyncIme = true)
  }

  fun moveLineDown() {
    if (readOnly) return
    val line = doc.lineForOffset(selection.end)
    if (line >= doc.lineCount - 1) return
    val col = selection.end - doc.lineStart(line)
    val curStart = doc.lineStart(line)
    val nextEnd = doc.lineEnd(line + 1)
    val curText = doc.lineText(line)
    val nextText = doc.lineText(line + 1)
    val swapped = "$nextText\n$curText"
    val newCaret = (curStart + nextText.length + 1 + col.coerceAtMost(curText.length))
    applyEdit(curStart, nextEnd, swapped, TextRange(newCaret), EditKind.ATOMIC, resyncIme = true)
  }

  fun duplicateLine() {
    if (readOnly) return
    val line = doc.lineForOffset(selection.end)
    val lineStart = doc.lineStart(line)
    val lineEnd = doc.lineEnd(line)
    val lineTxt = doc.lineText(line)
    val col = (selection.end - lineStart).coerceIn(0, lineTxt.length)
    val insertion = "\n$lineTxt"
    val newCaret = lineEnd + 1 + col
    applyEdit(lineEnd, lineEnd, insertion, TextRange(newCaret), EditKind.ATOMIC, resyncIme = true)
  }

  fun undo(): Boolean {
    if (readOnly || undoStack.isEmpty()) return false
    val entry = undoStack.removeLast()
    redoStack.addLast(entry)
    when (entry) {
      is UndoRecord.SingleEdit ->
        applyRawEdit(
          start = entry.start,
          end = entry.start + entry.insertedText.length,
          replacement = entry.removedText,
          nextSelection = entry.selectionBefore,
          resyncIme = true,
        )
      is UndoRecord.MoveEdit ->
        applyRawMove(
          removeAt = entry.adjustedDst,
          insertAt = entry.srcStart,
          movedText = entry.movedText,
          nextSelection = entry.selectionBefore,
        )
    }
    return true
  }

  fun redo(): Boolean {
    if (readOnly || redoStack.isEmpty()) return false
    val entry = redoStack.removeLast()
    undoStack.addLast(entry)
    when (entry) {
      is UndoRecord.SingleEdit ->
        applyRawEdit(
          start = entry.start,
          end = entry.start + entry.removedText.length,
          replacement = entry.insertedText,
          nextSelection = entry.selectionAfter,
          resyncIme = true,
        )
      is UndoRecord.MoveEdit ->
        applyRawMove(
          removeAt = entry.srcStart,
          insertAt = entry.adjustedDst,
          movedText = entry.movedText,
          nextSelection = entry.selectionAfter,
        )
    }
    return true
  }

  private fun applyRawMove(
    removeAt: Int,
    insertAt: Int,
    movedText: String,
    nextSelection: TextRange,
  ) {
    val len = movedText.length
    beginBatch()
    try {
      val remStart = removeAt.coerceIn(0, doc.length)
      val remEnd = (remStart + len).coerceIn(remStart, doc.length)
      applyRawEdit(
        start = remStart,
        end = remEnd,
        replacement = "",
        nextSelection = TextRange(remStart),
        resyncIme = false,
      )
      val insPos = insertAt.coerceIn(0, doc.length)
      ensureLineUnfolded(doc.lineForOffset(insPos))
      applyRawEdit(
        start = insPos,
        end = insPos,
        replacement = movedText,
        nextSelection = nextSelection,
        resyncIme = true,
      )
    } finally {
      endBatch()
    }
  }

  // ---- Android IME Bridge Methods ----

  fun beginBatch() {
    batchDepth++
  }

  fun endBatch() {
    if (batchDepth <= 0) return
    batchDepth--
    if (batchDepth == 0) {
      val hadText = batchHadTextChange
      val hadState = batchHadStateChange
      val span = if (batchMultipleSpans) null else batchSingleSpan
      batchHadTextChange = false
      batchHadStateChange = false
      batchSingleSpan = null
      batchMultipleSpans = false
      if (hadText) imeListener?.onTextChanged(span)
      else if (hadState) imeListener?.onStateChanged()
    }
  }

  fun imeCommitText(text: String, newCursorPosition: Int) {
    if (readOnly) return
    val normalized = EditorDocument.normalizeNewlines(text)
    val range = composing ?: selection
    composing = null

    // Smart Enter & auto-close only for direct single-unit typing (not bulk paste/multi-char commits)
    if (range.collapsed && normalized == "\n") {
      insertSmartNewline(resyncIme = true)
      return
    }
    if (autoCloseBrackets && range.collapsed && normalized.length == 1) {
      if (handleSingleCharAutoClose(normalized[0], resyncIme = true)) return
    }

    val start = range.min.coerceIn(0, doc.length)
    val end = range.max.coerceIn(start, doc.length)
    val newCaret = computeImeCursor(start, normalized.length, newCursorPosition, doc.length - (end - start) + normalized.length)
    applyEdit(start, end, normalized, TextRange(newCaret), EditKind.INSERT, resyncIme = false)
  }

  fun imeSetComposingText(text: String, newCursorPosition: Int) {
    if (readOnly) return
    val normalized = EditorDocument.normalizeNewlines(text)
    val range = composing ?: selection
    val start = range.min.coerceIn(0, doc.length)
    val end = range.max.coerceIn(start, doc.length)
    val newDocLen = doc.length - (end - start) + normalized.length
    val newCaret = computeImeCursor(start, normalized.length, newCursorPosition, newDocLen)
    composing = if (normalized.isEmpty()) null else TextRange(start, start + normalized.length)
    applyEdit(start, end, normalized, TextRange(newCaret), EditKind.INSERT, resyncIme = false, keepComposing = true)
  }

  fun imeSetComposingRegion(start: Int, end: Int) {
    val s = minOf(start, end).coerceIn(0, doc.length)
    val e = maxOf(start, end).coerceIn(s, doc.length)
    composing = if (s == e) null else TextRange(s, e)
    notifyStateChanged()
  }

  fun imeFinishComposing() {
    if (composing != null) {
      composing = null
      notifyStateChanged()
    }
  }

  fun imeDeleteSurrounding(beforeLength: Int, afterLength: Int) {
    if (readOnly) return
    if (beforeLength == 0 && afterLength == 0) return

    val sel = selection
    val comp = composing

    // Standard single-char backspace shortcut when no composing & collapsed selection
    if (comp == null && sel.collapsed && beforeLength == 1 && afterLength == 0) {
      backspace(resyncIme = false)
      return
    }

    // Delete outside composing region per Android InputConnection contract
    val beforeAnchor = minOf(sel.min, comp?.min ?: sel.min)
    val afterAnchor = maxOf(sel.max, comp?.max ?: sel.max)
    val delBeforeStart = (beforeAnchor - beforeLength).coerceAtLeast(0)
    val delAfterEnd = (afterAnchor + afterLength).coerceAtMost(doc.length)

    beginBatch()
    try {
      if (delAfterEnd > afterAnchor) {
        applyEdit(afterAnchor, delAfterEnd, "", selection, EditKind.DELETE, resyncIme = false, keepComposing = true)
      }
      if (beforeAnchor > delBeforeStart) {
        val shift = beforeAnchor - delBeforeStart
        val nextSel = TextRange((selection.start - shift).coerceAtLeast(0), (selection.end - shift).coerceAtLeast(0))
        composing = composing?.let { TextRange((it.start - shift).coerceAtLeast(0), (it.end - shift).coerceAtLeast(0)) }
        applyEdit(delBeforeStart, beforeAnchor, "", nextSel, EditKind.DELETE, resyncIme = false, keepComposing = true)
      }
    } finally {
      endBatch()
    }
  }

  fun imeReplaceText(start: Int, end: Int, text: String, newCursorPosition: Int) {
    if (readOnly) return
    val s = minOf(start, end).coerceIn(0, doc.length)
    val e = maxOf(start, end).coerceIn(s, doc.length)
    val normalized = EditorDocument.normalizeNewlines(text)
    val newLen = doc.length - (e - s) + normalized.length
    val newCaret = computeImeCursor(s, normalized.length, newCursorPosition, newLen)
    composing = null
    applyEdit(s, e, normalized, TextRange(newCaret), EditKind.ATOMIC, resyncIme = false)
  }

  fun imeSetSelection(start: Int, end: Int) {
    val s = start.coerceIn(0, doc.length)
    val e = end.coerceIn(0, doc.length)
    setSelection(TextRange(s, e), finishComposing = false)
  }

  fun imeTextBeforeCursor(n: Int): String {
    if (n <= 0) return ""
    val end = selection.min.coerceIn(0, doc.length)
    val start = (end - n).coerceAtLeast(0)
    return doc.substring(start, end)
  }

  fun imeTextAfterCursor(n: Int): String {
    if (n <= 0) return ""
    val start = selection.max.coerceIn(0, doc.length)
    val end = (start + n).coerceAtMost(doc.length)
    return doc.substring(start, end)
  }

  fun imeRewriteRefused() {
    imeListener?.onRestartInput()
  }

  fun extractedTextSnapshot(maxChars: Int): ExtractedTextSnapshot {
    val len = doc.length
    val sel = selection
    val start: Int
    val end: Int
    if (len <= maxChars) {
      start = 0
      end = len
    } else {
      start = (sel.min - maxChars / 2).coerceIn(0, len - maxChars)
      end = (start + maxChars).coerceAtMost(len)
    }
    return ExtractedTextSnapshot(
      text = doc.substring(start, end),
      startOffset = start,
      selectionStart = (sel.min - start).coerceIn(0, end - start),
      selectionEnd = (sel.max - start).coerceIn(0, end - start),
      partialStartOffset = -1,
      partialEndOffset = -1,
    )
  }

  fun partialExtractedSnapshot(span: EditSpan): ExtractedTextSnapshot {
    val sel = selection
    return ExtractedTextSnapshot(
      text = doc.substring(span.start, (span.start + span.added).coerceAtMost(doc.length)),
      startOffset = 0,
      selectionStart = sel.min,
      selectionEnd = sel.max,
      partialStartOffset = span.start,
      partialEndOffset = span.start + span.removed,
    )
  }

  // ---- Smart Editing Internals ----

  private fun insertSmartNewline(resyncIme: Boolean) {
    val start = selection.min
    val end = selection.max
    val lineIdx = doc.lineForOffset(start)
    val lineText = doc.lineText(lineIdx)
    val col = (start - doc.lineStart(lineIdx)).coerceIn(0, lineText.length)
    val baseIndent = lineText.takeWhile { it == ' ' || it == '\t' }
    val beforeTrimmed = lineText.substring(0, col).trimEnd()
    val afterChar = if (end < doc.length) doc.charAt(end) else '\u0000'
    val step = " ".repeat(tabSize.coerceIn(1, 8))

    val opensBlock = beforeTrimmed.endsWith("{") || beforeTrimmed.endsWith("[") || beforeTrimmed.endsWith("(")
    val splitsPair =
      (beforeTrimmed.endsWith("{") && afterChar == '}') ||
        (beforeTrimmed.endsWith("[") && afterChar == ']') ||
        (beforeTrimmed.endsWith("(") && afterChar == ')')

    val (insertion, caretOffsetFromStart) =
      when {
        splitsPair -> {
          val inner = "\n$baseIndent$step\n$baseIndent"
          inner to (1 + baseIndent.length + step.length)
        }
        opensBlock -> {
          val inner = "\n$baseIndent$step"
          inner to inner.length
        }
        else -> {
          val inner = "\n$baseIndent"
          inner to inner.length
        }
      }
    applyEdit(start, end, insertion, TextRange(start + caretOffsetFromStart), EditKind.ATOMIC, resyncIme = resyncIme)
  }

  private fun handleSingleCharAutoClose(ch: Char, resyncIme: Boolean): Boolean {
    val pos = selection.min
    val next = if (pos < doc.length) doc.charAt(pos) else '\u0000'
    val prev = if (pos > 0) doc.charAt(pos - 1) else '\u0000'

    // Skip-over closing bracket/quote if already right ahead
    if (ch in CLOSERS && next == ch) {
      if (ch == '"' || ch == '\'' || ch == '`') {
        if (prev != '\\') {
          setSelection(TextRange(pos + 1))
          if (resyncIme) resyncImeIfNeeded()
          return true
        }
      } else {
        setSelection(TextRange(pos + 1))
        if (resyncIme) resyncImeIfNeeded()
        return true
      }
    }

    val closer = matchingCloser(ch) ?: return false
    // Don't auto-close quotes immediately inside words/identifiers
    if ((ch == '"' || ch == '\'' || ch == '`') && (prev.isLetterOrDigit() || next.isLetterOrDigit())) {
      return false
    }
    // Don't auto-close openers when followed immediately by an identifier char
    if (next.isLetterOrDigit() || next == '_' || next == '$') return false

    val pair = "$ch$closer"
    applyEdit(pos, pos, pair, TextRange(pos + 1), EditKind.ATOMIC, resyncIme = resyncIme)
    return true
  }

  private fun applyEdit(
    start: Int,
    end: Int,
    replacement: String,
    nextSelection: TextRange,
    kind: EditKind,
    resyncIme: Boolean,
    keepComposing: Boolean = false,
  ) {
    val s = start.coerceIn(0, doc.length)
    val e = end.coerceIn(s, doc.length)
    val removed = doc.substring(s, e)
    if (removed == replacement && nextSelection == selection) return

    val selBefore = selection
    val now = System.currentTimeMillis()
    pushUndo(s, removed, replacement, selBefore, nextSelection, now, kind)
    redoStack.clear()

    applyRawEdit(s, e, replacement, nextSelection, resyncIme, keepComposing)
  }

  private fun applyRawEdit(
    start: Int,
    end: Int,
    replacement: String,
    nextSelection: TextRange,
    resyncIme: Boolean,
    keepComposing: Boolean = false,
  ) {
    val oldDoc = doc
    val editStartLine = oldDoc.lineForOffset(start)
    val editOldEndLine = oldDoc.lineForOffset(end)

    val newDoc = oldDoc.replace(start, end, replacement)
    val lineDelta = newDoc.lineCount - oldDoc.lineCount
    val span =
      EditSpan(
        start = start,
        removed = end - start,
        added = replacement.length,
        editStartLine = editStartLine,
        editOldEndLine = editOldEndLine,
        lineDelta = lineDelta,
      )

    doc = newDoc
    selection = clampRange(nextSelection, newDoc.length)
    if (!keepComposing) composing = null

    // Maintain folds & bookmarks across line insertions/deletions
    if (lineDelta != 0 || editStartLine != editOldEndLine) {
      if (collapsedFolds.isNotEmpty()) {
        val shifted = shiftFoldRegions(collapsedFolds, editStartLine, editOldEndLine, lineDelta)
        collapsedFolds = shifted
        foldModel = FoldModel(newDoc.lineCount, shifted.toList())
      } else {
        foldModel = FoldModel.flat(newDoc.lineCount)
      }
      if (bookmarks.isNotEmpty()) {
        bookmarks =
          bookmarks.mapNotNull { bm ->
            when {
              bm < editStartLine -> bm
              bm > editOldEndLine -> (bm + lineDelta).takeIf { it in 0 until newDoc.lineCount }
              else -> editStartLine.takeIf { it in 0 until newDoc.lineCount }
            }
          }.toSet()
      }
    }

    // Update maxLineChars cheaply for edited lines
    val newEditEndLine = (editOldEndLine + lineDelta).coerceIn(editStartLine, newDoc.lineCount - 1)
    for (l in editStartLine..newEditEndLine) {
      val len = newDoc.lineLength(l)
      if (len > maxLineChars) maxLineChars = len
    }

    onSpliceStyles?.invoke(newDoc, editStartLine, editOldEndLine, lineDelta)
    textRevision++
    editCount++
    onUserEdit?.invoke()

    if (resyncIme) {
      resyncImeIfNeeded(span)
    } else {
      notifyTextChanged(span)
    }
  }

  private fun resyncImeIfNeeded(span: EditSpan? = null) {
    val listener = imeListener ?: return
    if (listener.isSyncingExtractedText()) {
      notifyTextChanged(span)
    } else {
      notifyTextChanged(span)
      listener.onRestartInput()
    }
  }

  private fun notifyStateChanged() {
    if (batchDepth > 0) {
      batchHadStateChange = true
      return
    }
    imeListener?.onStateChanged()
  }

  private fun notifyTextChanged(span: EditSpan?) {
    if (batchDepth > 0) {
      batchHadTextChange = true
      if (batchSingleSpan == null && !batchMultipleSpans && span != null) {
        batchSingleSpan = span
      } else {
        batchMultipleSpans = true
        batchSingleSpan = null
      }
      return
    }
    imeListener?.onTextChanged(span)
  }

  private fun pushUndo(
    start: Int,
    removed: String,
    inserted: String,
    selBefore: TextRange,
    selAfter: TextRange,
    nowMs: Long,
    kind: EditKind,
  ) {
    val prev = undoStack.lastOrNull() as? UndoRecord.SingleEdit
    val withinWindow = nowMs - lastEditTimeMs <= UNDO_COALESCE_MS
    lastEditTimeMs = nowMs

    if (prev != null && withinWindow && kind != EditKind.ATOMIC && prev.kind == kind) {
      if (kind == EditKind.INSERT &&
        removed.isEmpty() &&
        inserted.isNotEmpty() &&
        '\n' !in inserted &&
        start == prev.start + prev.insertedText.length &&
        prev.insertedText.length + inserted.length <= 64
      ) {
        undoStack.removeLast()
        undoStack.addLast(
          prev.copy(
            insertedText = prev.insertedText + inserted,
            selectionAfter = selAfter,
            timestampMs = nowMs,
          ),
        )
        return
      }
      if (kind == EditKind.DELETE &&
        inserted.isEmpty() &&
        removed.isNotEmpty() &&
        '\n' !in removed &&
        start + removed.length == prev.start &&
        prev.removedText.length + removed.length <= 64
      ) {
        undoStack.removeLast()
        undoStack.addLast(
          prev.copy(
            start = start,
            removedText = removed + prev.removedText,
            selectionAfter = selAfter,
            timestampMs = nowMs,
          ),
        )
        return
      }
    }

    undoStack.addLast(UndoRecord.SingleEdit(start, removed, inserted, selBefore, selAfter, nowMs, kind))
    while (undoStack.size > MAX_UNDO_ENTRIES) {
      undoStack.removeFirst()
    }
  }

  companion object {
    private const val MAX_UNDO_ENTRIES = 150
    private const val UNDO_COALESCE_MS = 700L
    private val CLOSERS = charArrayOf(')', ']', '}', '"', '\'', '`')

    private fun matchingCloser(open: Char): Char? =
      when (open) {
        '(' -> ')'
        '[' -> ']'
        '{' -> '}'
        '"' -> '"'
        '\'' -> '\''
        '`' -> '`'
        else -> null
      }

    private fun isIdentChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun clampRange(r: TextRange, maxLen: Int): TextRange =
      TextRange(r.start.coerceIn(0, maxLen), r.end.coerceIn(0, maxLen))

    private fun computeMaxLineChars(doc: EditorDocument): Int {
      var m = 0
      val cap = minOf(doc.lineCount, 4_000)
      for (i in 0 until cap) {
        val len = doc.lineLength(i)
        if (len > m) m = len
      }
      return m
    }

    private fun computeImeCursor(editStart: Int, insertedLen: Int, newCursorPosition: Int, newDocLen: Int): Int {
      val raw =
        if (newCursorPosition > 0) editStart + insertedLen + (newCursorPosition - 1)
        else editStart + newCursorPosition
      return raw.coerceIn(0, newDocLen)
    }
  }
}
