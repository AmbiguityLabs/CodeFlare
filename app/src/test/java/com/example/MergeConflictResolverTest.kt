package com.example

import com.example.editor.CodeFormatter
import com.example.editor.ConflictChoice
import com.example.editor.DiffCellKind
import com.example.editor.MergeConflictResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeConflictResolverTest {

  @Test
  fun threeWayMergeCombinesNonOverlappingEditsCleanly() {
    val base =
      """
      line 1
      line 2
      line 3
      line 4
      line 5
      """.trimIndent()
    val local =
      """
      line 1 (local header)
      line 2
      line 3
      line 4
      line 5
      """.trimIndent()
    val remote =
      """
      line 1
      line 2
      line 3
      line 4
      line 5 (remote footer)
      """.trimIndent()

    val res = MergeConflictResolver.threeWayMerge("index.js", base, local, remote)
    assertFalse(res.hasConflicts)
    assertTrue(res.hunks.isEmpty())
    assertEquals(
      """
      line 1 (local header)
      line 2
      line 3
      line 4
      line 5 (remote footer)
      """.trimIndent(),
      res.mergedContent,
    )
  }

  @Test
  fun threeWayMergeFastForwardsWhenLocalMatchesBase() {
    val base = "export default { fetch() { return new Response('v1'); } };"
    val remote = "export default { fetch() { return new Response('v2'); } };"

    val res = MergeConflictResolver.threeWayMerge("src/index.js", base, base, remote)
    assertFalse(res.hasConflicts)
    assertEquals(remote, res.mergedContent)
  }

  @Test
  fun threeWayMergeEmitsMarkersAndResolvesIndividualHunks() {
    val base =
      """
      const mode = "dev";
      const port = 3000;
      const retries = 1;
      """.trimIndent()
    val local =
      """
      const mode = "local-debug";
      const port = 3000;
      const retries = 3;
      """.trimIndent()
    val remote =
      """
      const mode = "production";
      const port = 3000;
      const retries = 5;
      """.trimIndent()

    val res =
      MergeConflictResolver.threeWayMerge(
        path = "config.js",
        baseContent = base,
        localContent = local,
        remoteContent = remote,
        localLabel = "worker",
        remoteLabel = "origin/main",
      )

    assertTrue(res.hasConflicts)
    assertEquals(2, res.hunks.size)
    assertTrue(MergeConflictResolver.hasConflictMarkers(res.mergedContent))

    // Resolve hunk 0 with KEEP_LOCAL
    val afterFirst =
      MergeConflictResolver.resolveConflictHunk(res.mergedContent, 0, ConflictChoice.KEEP_LOCAL)
    val remaining = MergeConflictResolver.parseConflictMarkers(afterFirst)
    assertEquals(1, remaining.size)
    assertTrue(afterFirst.contains("const mode = \"local-debug\";"))

    // Resolve remaining hunk (now index 0) with ACCEPT_BOTH
    val resolvedAll =
      MergeConflictResolver.resolveConflictHunk(afterFirst, 0, ConflictChoice.ACCEPT_BOTH)
    assertFalse(MergeConflictResolver.hasConflictMarkers(resolvedAll))
    assertTrue(resolvedAll.contains("const retries = 3;"))
    assertTrue(resolvedAll.contains("const retries = 5;"))
  }

  @Test
  fun resolveAllConflictsUseRemoteCleansAllMarkers() {
    val conflicted =
      """
      <<<<<<< LOCAL
      const a = 1;
      =======
      const a = 2;
      >>>>>>> REMOTE
      const b = 10;
      <<<<<<< LOCAL
      const c = 3;
      =======
      const c = 4;
      >>>>>>> REMOTE
      """.trimIndent()

    val resolved = MergeConflictResolver.resolveAllConflicts(conflicted, ConflictChoice.USE_REMOTE)
    assertFalse(MergeConflictResolver.hasConflictMarkers(resolved))
    assertEquals(
      """
      const a = 2;
      const b = 10;
      const c = 4;
      """.trimIndent(),
      resolved,
    )
  }

  @Test
  fun buildSideBySideRowsPairsAdjacentRemovedAndAddedLinesWithPadding() {
    val oldText =
      """
      a
      b1
      b2
      c
      """.trimIndent()
    val newText =
      """
      a
      b_new
      c
      d_extra
      """.trimIndent()

    val diff = CodeFormatter.computeLineDiff(oldText, newText)
    val rows = MergeConflictResolver.buildSideBySideRows(diff)

    // Row 0: 'a' unchanged
    assertEquals(DiffCellKind.UNCHANGED, rows[0].leftKind)
    assertEquals("a", rows[0].leftText)
    assertEquals("a", rows[0].rightText)

    // Row 1: b1 -> b_new
    assertEquals(DiffCellKind.REMOVED, rows[1].leftKind)
    assertEquals("b1", rows[1].leftText)
    assertEquals(DiffCellKind.ADDED, rows[1].rightKind)
    assertEquals("b_new", rows[1].rightText)

    // Row 2: b2 -> empty pad
    assertEquals(DiffCellKind.REMOVED, rows[2].leftKind)
    assertEquals("b2", rows[2].leftText)
    assertEquals(DiffCellKind.EMPTY_PAD, rows[2].rightKind)
    assertNull(rows[2].rightLineNumber)

    // Row 4: empty pad -> d_extra
    val last = rows.last()
    assertEquals(DiffCellKind.EMPTY_PAD, last.leftKind)
    assertEquals(DiffCellKind.ADDED, last.rightKind)
    assertEquals("d_extra", last.rightText)
  }

  @Test
  fun threeWayMergeNormalizesCrlfForFastForwardAndConflicts() {
    val baseLf = "line1\nline2"
    val localCrlf = "line1\r\nline2"
    val remoteCrlf = "line1\r\nline2 updated"

    val ff = MergeConflictResolver.threeWayMerge("index.ts", baseLf, localCrlf, remoteCrlf)
    assertFalse(ff.hasConflicts)
    assertFalse(ff.mergedContent.contains("\r"))
    assertEquals("line1\nline2 updated", ff.mergedContent)

    val conflicted =
      MergeConflictResolver.threeWayMerge(
        path = "config.ts",
        baseContent = "mode = 1\r\nok = true",
        localContent = "mode = 2\r\nok = true",
        remoteContent = "mode = 3\r\nok = true",
      )
    assertTrue(conflicted.hasConflicts)
    assertFalse(conflicted.mergedContent.contains("\r"))
    assertEquals(1, conflicted.hunks.size)
  }
}
