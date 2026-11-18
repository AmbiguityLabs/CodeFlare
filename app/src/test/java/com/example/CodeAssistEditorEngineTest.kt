package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import com.example.data.remote.WorkerLanguage
import com.example.editor.canvas.SelectionHandleDrag
import com.example.editor.engine.ClipboardHistoryStore
import com.example.editor.engine.EdgeEditorSession
import com.example.editor.engine.EdgeFoldDetector
import com.example.editor.engine.EdgeTokenKind
import com.example.editor.engine.EditSpan
import com.example.editor.engine.EditorDocument
import com.example.editor.engine.FoldKind
import com.example.editor.engine.FoldModel
import com.example.editor.engine.FoldRegion
import com.example.editor.engine.IncrementalLineStyles
import com.example.editor.engine.LineChange
import com.example.editor.engine.Rope
import com.example.editor.engine.WrapModel
import com.example.editor.engine.lineChanges
import com.example.editor.engine.revertEdit
import com.example.editor.engine.scanBackward
import com.example.editor.engine.scanForward
import com.example.editor.engine.shiftFoldRegions
import com.example.ui.theme.DarkIdePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CodeAssistEditorEngineTest {

  @Before
  fun resetClipboard() {
    ClipboardHistoryStore.clear()
  }

  @Test
  fun ropeSplitConcatRebalanceAndScanWorkOverLargeText() {
    val chunk = "export default { async fetch(req, env) { return new Response('ok'); } };\n"
    val initial = chunk.repeat(40) // > 2800 chars across multiple leaves
    var rope = Rope.of(initial)
    assertEquals(initial.length, rope.length)
    assertEquals(initial, rope.toString())

    // Perform 50 edits to trigger concatenation & rebalancing
    val expected = StringBuilder(initial)
    for (i in 0 until 50) {
      val pos = (i * 37) % expected.length
      val ins = "/*$i*/"
      rope = rope.replace(pos, pos, ins)
      expected.insert(pos, ins)
    }
    assertEquals(expected.toString(), rope.toString())

    // Delete a cross-leaf range
    val delStart = 450
    val delEnd = 1250
    rope = rope.replace(delStart, delEnd, "REPLACED")
    expected.replace(delStart, delEnd, "REPLACED")
    assertEquals(expected.toString(), rope.toString())

    // Scan forward & backward without materializing (visitor returns true to stop)
    var fwdCount = 0
    rope.scanForward(0, rope.length) { _, ch ->
      if (ch == '\n') fwdCount++
      false
    }
    assertEquals(expected.count { it == '\n' }, fwdCount)

    val revChars = StringBuilder()
    rope.scanBackward(9, 0) { _, ch ->
      revChars.append(ch)
      false
    }
    assertEquals(expected.substring(0, 10).reversed(), revChars.toString())
  }

  @Test
  fun editorDocumentIncrementalLineStartsAndCrlfNormalization() {
    val raw = "line0\r\nline1\r\nline2"
    var doc = EditorDocument.of(raw)
    assertEquals(3, doc.lineCount)
    assertEquals("line0", doc.lineText(0))
    assertEquals("line1", doc.lineText(1))
    assertEquals("line2", doc.lineText(2))
    assertEquals(1, doc.lineForOffset(6))
    assertEquals(0, doc.columnOf(6))
    assertEquals(8, doc.offsetOf(1, 2))

    // Multi-line insert in the middle of line 1
    doc = doc.replace(8, 10, "A\nB\nC")
    assertEquals(5, doc.lineCount)
    assertEquals("liA", doc.lineText(1))
    assertEquals("B", doc.lineText(2))
    assertEquals("C1", doc.lineText(3))
    assertEquals("line2", doc.lineText(4))

    // Delete across multiple lines
    val s = doc.lineStart(1)
    val e = doc.lineStart(4)
    doc = doc.replace(s, e, "")
    assertEquals(2, doc.lineCount)
    assertEquals("line0\nline2", doc.text)
  }

  @Test
  fun foldDetectorAndProjectionHandleJsImportsCommentsAndBlocks() {
    val source =
      """
      import { a } from './a';
      import { b } from './b';
      import { c } from './c';
      /*
       * Worker handler
       */
      export default {
        async fetch(req: Request) {
          return new Response('hi');
        }
      };
      """.trimIndent()
    val doc = EditorDocument.of(source)
    val regions = EdgeFoldDetector.detect(doc, WorkerLanguage.TYPESCRIPT)

    val importFold = regions.firstOrNull { it.kind == FoldKind.IMPORTS }
    assertNotNull("Expected multi-line import fold", importFold)
    assertEquals(0, importFold!!.startLine)
    assertEquals(2, importFold.endLine)

    val commentFold = regions.firstOrNull { it.kind == FoldKind.COMMENT }
    assertNotNull("Expected block comment fold", commentFold)

    val outerBlock = regions.firstOrNull { it.kind == FoldKind.BLOCK && it.startLine == 6 }
    assertNotNull("Expected export default block fold", outerBlock)
    assertEquals(10, outerBlock!!.endLine)

    // Collapsing outerBlock hides lines 7..10 (4 lines hidden)
    val model = FoldModel(doc.lineCount, listOf(importFold, outerBlock))
    assertEquals(doc.lineCount - 2 - 4, model.visualLineCount)
    assertTrue(model.isHidden(1))
    assertTrue(model.isHidden(8))
    assertFalse(model.isHidden(6))
    assertEquals(6, model.docLineForVisual(model.visualLineForDoc(6)))

    val foldedInfo = model.buildFoldedLineInfo(doc, 6)
    assertNotNull(foldedInfo)
    assertTrue(foldedInfo!!.suffixText.contains("}"))

    // Shifting fold regions when inserting 2 lines at line 3
    val shifted = shiftFoldRegions(setOf(importFold, outerBlock), editStartLine = 3, editOldEndLine = 3, lineDelta = 2)
    assertTrue(shifted.contains(importFold))
    assertTrue(shifted.any { it.startLine == 8 && it.endLine == 12 })
  }

  @Test
  fun wrapModelFenwickTreeMaintainsLogNVisualRowsWithFolds() {
    val wrap = WrapModel()
    wrap.resize(6)
    val fold = FoldModel(6, listOf(FoldRegion(2, 4)))
    wrap.ensure(fold)
    // Lines 0, 1, 2 (collapsed header), 5 are visible -> 4 total rows initially
    assertEquals(4, wrap.totalRows)

    // Line 1 wraps to 3 visual rows
    wrap.setRows(1, 3)
    assertEquals(6, wrap.totalRows)
    assertEquals(0, wrap.topRow(0))
    assertEquals(1, wrap.topRow(1))
    assertEquals(4, wrap.topRow(2))
    assertEquals(5, wrap.topRow(5))
    assertEquals(1, wrap.docLineForRow(2))
    assertEquals(1, wrap.docLineForRow(3))
    assertEquals(2, wrap.docLineForRow(4))
    assertEquals(5, wrap.docLineForRow(5))

    // Hidden line 3 changing wrap height must NOT alter totalRows
    wrap.setRows(3, 5)
    assertEquals(6, wrap.totalRows)
  }

  @Test
  fun myersLineDiffAndRevertEditRoundTripExactBaseline() {
    val base =
      """
      const a = 1;
      const b = 2;
      const c = 3;
      const d = 4;
      const e = 5;
      """.trimIndent()
    val edited =
      """
      const a = 1;
      const b = 200;
      const inserted = true;
      const c = 3;
      const e = 5;
      """.trimIndent()

    val changes = lineChanges(base, edited)
    assertEquals(2, changes.size)
    assertEquals(LineChange.Kind.MODIFIED, changes[0].kind)
    assertEquals(LineChange.Kind.DELETED, changes[1].kind)

    // Reverting changes from bottom to top restores exact baseline text
    var doc = EditorDocument.of(edited)
    for (chg in changes.reversed()) {
      val (s, e, repl) = revertEdit(doc, base, chg)
      doc = doc.replace(s, e, repl)
    }
    assertEquals(base, doc.text)
  }

  @Test
  fun incrementalLineStylesRipplesBlockCommentAndStopsWhenExitStateMatches() {
    val initial =
      """
      const a = 1;
      const b = 2;
      const c = 3;
      const d = 4;
      """.trimIndent()
    var doc = EditorDocument.of(initial)
    val styles = IncrementalLineStyles(WorkerLanguage.TYPESCRIPT, DarkIdePalette)
    styles.ensureUpTo(doc, 3)

    val revLine3Before = styles.lineRevision(3)
    assertTrue(styles.rawTokensForLine(doc, 1).any { it.kind == EdgeTokenKind.KEYWORD })

    // Open a block comment on line 0 -> ripples through lines 1..3 turning them COMMENT
    doc = doc.replace(0, 0, "/* ")
    styles.splice(doc, editStartLine = 0, editOldEndLine = 0, lineDelta = 0)
    assertTrue(styles.rawTokensForLine(doc, 2).all { it.kind == EdgeTokenKind.COMMENT })
    assertTrue(styles.lineRevision(3) > revLine3Before)

    // Close block comment at end of line 0 -> lines 1..3 recover keyword tokens
    val line0End = doc.lineEnd(0)
    doc = doc.replace(line0End, line0End, " */")
    styles.splice(doc, editStartLine = 0, editOldEndLine = 0, lineDelta = 0)
    assertTrue(styles.rawTokensForLine(doc, 1).any { it.kind == EdgeTokenKind.KEYWORD })

    // Single-line edit on line 1 that does NOT alter exit state leaves line 3 untouched (O(1) stop rule)
    val revLine3Stable = styles.lineRevision(3)
    val line1End = doc.lineEnd(1)
    doc = doc.replace(line1End, line1End, " // hi")
    styles.splice(doc, editStartLine = 1, editOldEndLine = 1, lineDelta = 0)
    assertEquals(revLine3Stable, styles.lineRevision(3))
  }

  @Test
  fun sessionSmartEditingUndoRedoBookmarksAndImeBridge() {
    val session = EdgeEditorSession("function run() {}")
    session.tabSize = 2
    session.autoCloseBrackets = true
    session.language = WorkerLanguage.TYPESCRIPT

    // Put caret between '{' and '}' and press Enter -> splits pair with inner indent
    val braceIdx = session.doc.text.indexOf('{') + 1
    session.setSelection(TextRange(braceIdx))
    session.commitText("\n")
    assertEquals("function run() {\n  \n}", session.doc.text)
    assertEquals(TextRange(braceIdx + 3), session.selection)

    // Auto-close '(' and skip-over ')' and pair-backspace
    session.commitText("(")
    assertEquals("function run() {\n  ()\n}", session.doc.text)
    assertEquals(TextRange(braceIdx + 4), session.selection)
    session.backspace()
    assertEquals("function run() {\n  \n}", session.doc.text)

    // Undo restores all 3 edits cleanly (pair-backspace -> auto-close -> smart newline)
    assertTrue(session.undo())
    assertEquals("function run() {\n  ()\n}", session.doc.text)
    assertTrue(session.undo())
    assertEquals("function run() {\n  \n}", session.doc.text)
    assertTrue(session.undo())
    assertEquals("function run() {}", session.doc.text)
    assertTrue(session.redo())
    assertEquals("function run() {\n  \n}", session.doc.text)

    // Line duplicate, move, comment toggle, bookmark cycle
    session.duplicateLine()
    assertEquals(4, session.doc.lineCount)
    assertTrue(session.toggleBookmark(0))
    assertTrue(session.toggleBookmark(2))
    session.setSelection(TextRange(0))
    assertEquals(2, session.nextBookmarkLine())
    assertEquals(0, session.nextBookmarkLine())

    // IME composing -> commit -> partial extracted text
    var lastSpan: EditSpan? = null
    session.imeListener =
      object : EdgeEditorSession.ImeListener {
        override fun onStateChanged() {}
        override fun onTextChanged(span: EditSpan?) {
          lastSpan = span
        }
        override fun onRestartInput() {}
        override fun isSyncingExtractedText(): Boolean = true
      }

    session.setSelection(TextRange(session.doc.lineStart(1)))
    session.imeSetComposingText("ret", 1)
    assertNotNull(session.composing)
    session.imeCommitText("return 42;", 1)
    assertNull(session.composing)
    assertTrue(session.doc.lineText(1).contains("return 42;"))
    assertNotNull(lastSpan)

    val partial = session.partialExtractedSnapshot(lastSpan!!)
    assertEquals(lastSpan!!.start, partial.partialStartOffset)
    assertEquals(lastSpan!!.start + lastSpan!!.removed, partial.partialEndOffset)

    val full = session.extractedTextSnapshot(maxChars = 1024)
    assertEquals(session.doc.text, full.text)
    assertEquals(session.selection.min, full.selectionStart)
  }

  @Test
  fun selectionHandleDragEnforcesDeadZoneFixedFarEndAndAxisResistance() {
    // 10 chars per line, 20px char width, 40px line height
    val lineH = 40f
    val charW = 20f
    val drag =
      SelectionHandleDrag(
        fixedEnd = 5,
        start = 10,
        grab = Offset(200f, 30f),
        hotspot = Offset(200f, 20f),
        lineHeight = lineH,
        deadZone = 6f,
        resistX = 4f,
        resistY = 6f,
      )

    fun offsetAt(p: Offset): Int {
      val row = (p.y / lineH).toInt().coerceIn(0, 2)
      val col = (p.x / charW).toInt().coerceIn(0, 10)
      return row * 10 + col
    }

    fun caretAt(off: Int): SelectionHandleDrag.CaretPoint {
      val row = off / 10
      val col = off % 10
      return SelectionHandleDrag.CaretPoint(col * charW, row * lineH)
    }

    // Small tremor inside dead zone is ignored
    assertFalse(drag.moveTo(Offset(203f, 32f), ::offsetAt, ::caretAt))
    assertFalse(drag.started)
    assertEquals(5 to 10, drag.selection)

    // Drag across fixedEnd=5 to offset 2 -> fixedEnd stays 5, moving becomes 2
    assertTrue(drag.moveTo(Offset(40f, 30f), ::offsetAt, ::caretAt))
    assertTrue(drag.started)
    assertEquals(5 to 2, drag.selection)

    // Crossing downward into row 1 within resistY (target.y = 42 < 40 + 6) is rejected
    assertFalse(drag.moveTo(Offset(40f, 52f), ::offsetAt, ::caretAt))
    assertEquals(2, drag.moving)

    // Pushing cleanly past resistY (target.y = 50 >= 46) advances to row 1
    assertTrue(drag.moveTo(Offset(40f, 60f), ::offsetAt, ::caretAt))
    assertEquals(12, drag.moving)
  }

  @Test
  fun clipboardHistoryDeduplicatesCapsAtTwentyAndClipsBinderSize() {
    for (i in 1..25) {
      ClipboardHistoryStore.record("clip-$i")
    }
    assertEquals(ClipboardHistoryStore.MAX_ENTRIES, ClipboardHistoryStore.entries.size)
    assertEquals("clip-25", ClipboardHistoryStore.entries.first())

    // Re-recording an existing entry promotes it to top without duplicate
    ClipboardHistoryStore.record("clip-10")
    assertEquals(ClipboardHistoryStore.MAX_ENTRIES, ClipboardHistoryStore.entries.size)
    assertEquals("clip-10", ClipboardHistoryStore.entries.first())
    assertEquals(1, ClipboardHistoryStore.entries.count { it == "clip-10" })

    // Binder safety cap at 512 KB
    val huge = "x".repeat(ClipboardHistoryStore.MAX_CLIPBOARD_CHARS + 1000)
    assertEquals(ClipboardHistoryStore.MAX_CLIPBOARD_CHARS, ClipboardHistoryStore.clipForClipboard(huge).length)

    // Simple HTML strip
    assertEquals("hello\nworld", ClipboardHistoryStore.stripSimpleHtml("<p>hello<br/>world</p>"))
  }
}
