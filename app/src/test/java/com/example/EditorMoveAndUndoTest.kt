package com.example

import androidx.compose.ui.text.TextRange
import com.example.data.remote.WorkerLanguage
import com.example.editor.drag.DragTransferMode
import com.example.editor.drag.EdgeDragPayload
import com.example.editor.drag.EdgeTextDragCoordinator
import com.example.editor.engine.EdgeEditorSession
import com.example.editor.engine.EdgeFoldDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorMoveAndUndoTest {

  @Test
  fun moveRangeForwardAdjustsOffsetAndUndoesRedoesAtomically() {
    val session = EdgeEditorSession("abcdef", TextRange(1, 3)) // "bc" selected
    val moved = session.moveRange(sourceStart = 1, sourceEnd = 3, destinationOffset = 5)
    assertTrue(moved)
    assertEquals("adebcf", session.doc.text)
    assertEquals(TextRange(3, 5), session.selection)

    // Single undo restores BOTH original text and original selection
    assertTrue(session.undo())
    assertEquals("abcdef", session.doc.text)
    assertEquals(TextRange(1, 3), session.selection)
    assertFalse(session.canUndo)

    // Single redo reapplies the exact move
    assertTrue(session.redo())
    assertEquals("adebcf", session.doc.text)
    assertEquals(TextRange(3, 5), session.selection)
  }

  @Test
  fun moveRangeBackwardAndBoundaryEndpointsUndoCleanly() {
    val session = EdgeEditorSession("abcdef", TextRange(3, 5)) // "de"
    assertTrue(session.moveRange(3, 5, 1))
    assertEquals("adebcf", session.doc.text)
    assertEquals(TextRange(1, 3), session.selection)

    // Move to very end of doc
    assertTrue(session.moveRange(0, 2, 6)) // move "ad" to end
    assertEquals("ebcfad", session.doc.text)
    assertEquals(TextRange(4, 6), session.selection)

    // Move to very start of doc (0)
    assertTrue(session.moveRange(4, 6, 0)) // move "ad" back to 0
    assertEquals("adebcf", session.doc.text)
    assertEquals(TextRange(0, 2), session.selection)

    // Step all 3 moves back in exact order
    assertTrue(session.undo())
    assertEquals("ebcfad", session.doc.text)
    assertTrue(session.undo())
    assertEquals("adebcf", session.doc.text)
    assertTrue(session.undo())
    assertEquals("abcdef", session.doc.text)
    assertEquals(TextRange(3, 5), session.selection)
    assertFalse(session.canUndo)
  }

  @Test
  fun droppingInsideOrTouchingOwnSelectionIsCleanNoOp() {
    val session = EdgeEditorSession("const x = 42;", TextRange(6, 7))
    assertFalse(session.moveRange(6, 7, 6))
    assertFalse(session.moveRange(6, 7, 7))
    assertFalse(session.moveRange(0, 5, 3))
    assertEquals("const x = 42;", session.doc.text)
    assertFalse("No undo entry should be pushed on self-range no-op", session.canUndo)
  }

  @Test
  fun multiLineMoveUnfoldsCollapsedRegionAndPreservesAtomicUndo() {
    val src =
      """
      function a() {
        const x = 1;
        return x;
      }
      const tail = 99;
      """.trimIndent()
    val session = EdgeEditorSession(src)
    session.language = WorkerLanguage.JAVASCRIPT
    val regions = EdgeFoldDetector.detect(session.doc, WorkerLanguage.JAVASCRIPT)
    session.updateFoldRegions(regions)
    assertTrue(session.toggleFoldAtLine(0))
    assertTrue(session.foldModel.isHidden(1))

    // Move `const tail = 99;` into the hidden body line inside function a()
    val tailStart = src.indexOf("const tail = 99;")
    val tailEnd = tailStart + "const tail = 99;".length
    val insideBodyOffset = src.indexOf("return x;")
    assertTrue(session.moveRange(tailStart, tailEnd, insideBodyOffset))

    assertFalse("Target line should auto-unfold on drop", session.foldModel.isHidden(1))
    assertTrue(session.doc.text.contains("const tail = 99;return x;"))

    // Atomic single undo restores original document identically
    assertTrue(session.undo())
    assertEquals(src, session.doc.text)
    assertTrue(session.redo())
    assertTrue(session.doc.text.contains("const tail = 99;return x;"))
  }

  @Test
  fun insertAtSelectsInsertedSpanAndSupportsReadOnlyGuard() {
    val session = EdgeEditorSession("hello world")
    assertTrue(session.insertAt(6, "edge "))
    assertEquals("hello edge world", session.doc.text)
    assertEquals(TextRange(6, 11), session.selection)

    assertTrue(session.undo())
    assertEquals("hello world", session.doc.text)

    session.readOnly = true
    assertFalse(session.insertAt(0, "blocked"))
    assertFalse(session.moveRange(0, 5, 11))
    assertEquals("hello world", session.doc.text)
  }

  @Test
  fun dragCoordinatorResolvesMoveVsCopySafelyAcrossSurfaces() {
    val editablePayload =
      EdgeDragPayload(
        dragId = 1L,
        sourceSurfaceId = "worker.js",
        sourceEditable = true,
        sourceRange = 0..4,
        text = "hello",
        preferredMode = DragTransferMode.MOVE,
        scopeLabel = "Word",
      )
    assertEquals(DragTransferMode.MOVE, EdgeTextDragCoordinator.resolveEffectiveMode(editablePayload, "worker.js"))
    assertEquals(DragTransferMode.COPY, EdgeTextDragCoordinator.resolveEffectiveMode(editablePayload, "cli_path"))

    val copyPreferred = editablePayload.copy(preferredMode = DragTransferMode.COPY)
    assertEquals(DragTransferMode.COPY, EdgeTextDragCoordinator.resolveEffectiveMode(copyPreferred, "worker.js"))

    val readOnlyPayload = editablePayload.copy(sourceEditable = false)
    assertEquals(DragTransferMode.COPY, EdgeTextDragCoordinator.resolveEffectiveMode(readOnlyPayload, "worker.js"))
  }
}
