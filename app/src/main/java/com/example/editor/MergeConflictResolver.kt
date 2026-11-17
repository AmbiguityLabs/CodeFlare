package com.example.editor

import com.example.data.remote.DiffLine
import com.example.data.remote.DiffType

enum class DiffViewMode(val label: String) {
  UNIFIED("Unified"),
  SIDE_BY_SIDE("Side-by-Side"),
}

enum class DiffCellKind {
  UNCHANGED,
  REMOVED,
  ADDED,
  EMPTY_PAD,
}

data class SideBySideDiffRow(
  val leftLineNumber: Int?,
  val leftText: String,
  val leftKind: DiffCellKind,
  val rightLineNumber: Int?,
  val rightText: String,
  val rightKind: DiffCellKind,
)

enum class DiffFileOrigin(val label: String) {
  MODIFIED("Modified"),
  ADDED("Added"),
  DELETED("Deleted"),
  CONFLICT("Conflict"),
}

data class FileDiffEntry(
  val path: String,
  val origin: DiffFileOrigin,
  val originalLabel: String,
  val modifiedLabel: String,
  val originalContent: String,
  val modifiedContent: String,
  val addedCount: Int,
  val removedCount: Int,
  val hasConflicts: Boolean = false,
)

enum class ConflictChoice(val label: String) {
  KEEP_LOCAL("Keep Local"),
  USE_REMOTE("Use Remote"),
  ACCEPT_BOTH("Accept Both"),
}

data class ConflictHunk(
  val index: Int,
  val startLine: Int,
  val endLine: Int,
  val localLabel: String,
  val remoteLabel: String,
  val localLines: List<String>,
  val remoteLines: List<String>,
)

data class MergeConflictFile(
  val path: String,
  val baseContent: String,
  val localContent: String,
  val remoteContent: String,
  val mergedContent: String,
  val hunks: List<ConflictHunk>,
) {
  val remainingConflictCount: Int
    get() = hunks.size
}

data class ThreeWayMergeResult(
  val path: String,
  val mergedContent: String,
  val hasConflicts: Boolean,
  val hunks: List<ConflictHunk>,
)

object MergeConflictResolver {
  private const val MARKER_START = "<<<<<<<"
  private const val MARKER_MID = "======="
  private const val MARKER_END = ">>>>>>>"

  fun buildSideBySideRows(diffLines: List<DiffLine>): List<SideBySideDiffRow> {
    val rows = mutableListOf<SideBySideDiffRow>()
    var i = 0
    while (i < diffLines.size) {
      val line = diffLines[i]
      if (line.type == DiffType.UNCHANGED) {
        rows.add(
          SideBySideDiffRow(
            leftLineNumber = line.lineNumberOld,
            leftText = line.text,
            leftKind = DiffCellKind.UNCHANGED,
            rightLineNumber = line.lineNumberNew,
            rightText = line.text,
            rightKind = DiffCellKind.UNCHANGED,
          ),
        )
        i++
        continue
      }

      val removedRun = mutableListOf<DiffLine>()
      val addedRun = mutableListOf<DiffLine>()
      while (i < diffLines.size && diffLines[i].type != DiffType.UNCHANGED) {
        if (diffLines[i].type == DiffType.REMOVED) removedRun.add(diffLines[i])
        else if (diffLines[i].type == DiffType.ADDED) addedRun.add(diffLines[i])
        i++
      }

      val maxPairs = maxOf(removedRun.size, addedRun.size)
      for (k in 0 until maxPairs) {
        val left = removedRun.getOrNull(k)
        val right = addedRun.getOrNull(k)
        rows.add(
          SideBySideDiffRow(
            leftLineNumber = left?.lineNumberOld,
            leftText = left?.text.orEmpty(),
            leftKind = if (left != null) DiffCellKind.REMOVED else DiffCellKind.EMPTY_PAD,
            rightLineNumber = right?.lineNumberNew,
            rightText = right?.text.orEmpty(),
            rightKind = if (right != null) DiffCellKind.ADDED else DiffCellKind.EMPTY_PAD,
          ),
        )
      }
    }
    return rows
  }

  fun hasConflictMarkers(content: String): Boolean {
    return parseConflictMarkers(content).isNotEmpty()
  }

  fun parseConflictMarkers(content: String): List<ConflictHunk> {
    if (!content.contains(MARKER_START)) return emptyList()
    val lines = splitLinesSafe(content)
    val hunks = mutableListOf<ConflictHunk>()
    var i = 0
    var hunkIdx = 0
    while (i < lines.size) {
      val line = lines[i]
      if (line.startsWith(MARKER_START)) {
        val startLine = i + 1
        val localLabel = line.removePrefix(MARKER_START).trim().ifBlank { "LOCAL" }
        val localLines = mutableListOf<String>()
        val remoteLines = mutableListOf<String>()
        var j = i + 1
        var seenMid = false
        var remoteLabel = "REMOTE"
        var closed = false
        while (j < lines.size) {
          val inner = lines[j]
          if (!seenMid && inner.startsWith(MARKER_MID)) {
            seenMid = true
          } else if (seenMid && inner.startsWith(MARKER_END)) {
            remoteLabel = inner.removePrefix(MARKER_END).trim().ifBlank { "REMOTE" }
            closed = true
            break
          } else if (!seenMid) {
            localLines.add(inner)
          } else {
            remoteLines.add(inner)
          }
          j++
        }
        if (closed) {
          hunks.add(
            ConflictHunk(
              index = hunkIdx++,
              startLine = startLine,
              endLine = j + 1,
              localLabel = localLabel,
              remoteLabel = remoteLabel,
              localLines = localLines,
              remoteLines = remoteLines,
            ),
          )
          i = j + 1
          continue
        }
      }
      i++
    }
    return hunks
  }

  fun resolveConflictHunk(
    content: String,
    hunkIndex: Int,
    choice: ConflictChoice,
  ): String {
    val lines = splitLinesSafe(content)
    val hunks = parseConflictMarkers(content)
    val target = hunks.firstOrNull { it.index == hunkIndex } ?: return content
    val replacement =
      when (choice) {
        ConflictChoice.KEEP_LOCAL -> target.localLines
        ConflictChoice.USE_REMOTE -> target.remoteLines
        ConflictChoice.ACCEPT_BOTH -> target.localLines + target.remoteLines
      }
    val out = mutableListOf<String>()
    out.addAll(lines.subList(0, target.startLine - 1))
    out.addAll(replacement)
    if (target.endLine < lines.size) {
      out.addAll(lines.subList(target.endLine, lines.size))
    }
    return out.joinToString("\n")
  }

  fun resolveAllConflicts(
    content: String,
    choice: ConflictChoice,
  ): String {
    var current = content
    while (true) {
      val hunks = parseConflictMarkers(current)
      if (hunks.isEmpty()) break
      current = resolveConflictHunk(current, hunks.first().index, choice)
    }
    return current
  }

  fun threeWayMerge(
    path: String,
    baseContent: String,
    localContent: String,
    remoteContent: String,
    localLabel: String = "LOCAL",
    remoteLabel: String = "REMOTE",
  ): ThreeWayMergeResult {
    if (localContent == remoteContent) {
      return ThreeWayMergeResult(
        path = path,
        mergedContent = localContent,
        hasConflicts = false,
        hunks = emptyList(),
      )
    }
    if (localContent == baseContent) {
      return ThreeWayMergeResult(
        path = path,
        mergedContent = remoteContent,
        hasConflicts = false,
        hunks = emptyList(),
      )
    }
    if (remoteContent == baseContent) {
      return ThreeWayMergeResult(
        path = path,
        mergedContent = localContent,
        hasConflicts = false,
        hunks = emptyList(),
      )
    }

    val baseLines = splitLinesSafe(baseContent)
    val localLines = splitLinesSafe(localContent)
    val remoteLines = splitLinesSafe(remoteContent)

    val localHunks = extractEditHunks(baseLines, localLines)
    val remoteHunks = extractEditHunks(baseLines, remoteLines)

    val out = mutableListOf<String>()
    var baseCursor = 0
    var li = 0
    var ri = 0

    while (li < localHunks.size || ri < remoteHunks.size) {
      val lh = localHunks.getOrNull(li)
      val rh = remoteHunks.getOrNull(ri)

      when {
        lh != null && (rh == null || comesStrictlyBefore(lh, rh)) -> {
          if (baseCursor < lh.startBase) {
            out.addAll(baseLines.subList(baseCursor, lh.startBase))
          }
          out.addAll(lh.replacement)
          baseCursor = lh.endBase
          li++
        }
        rh != null && (lh == null || comesStrictlyBefore(rh, lh)) -> {
          if (baseCursor < rh.startBase) {
            out.addAll(baseLines.subList(baseCursor, rh.startBase))
          }
          out.addAll(rh.replacement)
          baseCursor = rh.endBase
          ri++
        }
        lh != null && rh != null -> {
          // Overlapping edit cluster across local and remote hunks
          val clusterStart = minOf(lh.startBase, rh.startBase)
          var clusterEnd = maxOf(lh.endBase, rh.endBase)
          val clusterLocal = mutableListOf(lh)
          val clusterRemote = mutableListOf(rh)
          li++
          ri++

          var expanded = true
          while (expanded) {
            expanded = false
            val nextL = localHunks.getOrNull(li)
            if (nextL != null && overlapsRange(nextL, clusterStart, clusterEnd)) {
              clusterLocal.add(nextL)
              clusterEnd = maxOf(clusterEnd, nextL.endBase)
              li++
              expanded = true
            }
            val nextR = remoteHunks.getOrNull(ri)
            if (nextR != null && overlapsRange(nextR, clusterStart, clusterEnd)) {
              clusterRemote.add(nextR)
              clusterEnd = maxOf(clusterEnd, nextR.endBase)
              ri++
              expanded = true
            }
          }

          if (baseCursor < clusterStart) {
            out.addAll(baseLines.subList(baseCursor, clusterStart))
          }
          val localRegion = applyHunksToSlice(baseLines, clusterStart, clusterEnd, clusterLocal)
          val remoteRegion = applyHunksToSlice(baseLines, clusterStart, clusterEnd, clusterRemote)

          if (localRegion == remoteRegion) {
            out.addAll(localRegion)
          } else {
            out.add("$MARKER_START $localLabel")
            out.addAll(localRegion)
            out.add(MARKER_MID)
            out.addAll(remoteRegion)
            out.add("$MARKER_END $remoteLabel")
          }
          baseCursor = clusterEnd
        }
      }
    }

    if (baseCursor < baseLines.size) {
      out.addAll(baseLines.subList(baseCursor, baseLines.size))
    }

    val merged = out.joinToString("\n")
    val parsedHunks = parseConflictMarkers(merged)
    return ThreeWayMergeResult(
      path = path,
      mergedContent = merged,
      hasConflicts = parsedHunks.isNotEmpty(),
      hunks = parsedHunks,
    )
  }

  private data class EditHunk(
    val startBase: Int, // 0-based inclusive
    val endBase: Int,   // 0-based exclusive
    val replacement: List<String>,
  )

  private fun comesStrictlyBefore(a: EditHunk, b: EditHunk): Boolean {
    return if (a.startBase == a.endBase && b.startBase == b.endBase) {
      a.endBase < b.startBase
    } else if (a.startBase == a.endBase || b.startBase == b.endBase) {
      a.endBase < b.startBase
    } else {
      a.endBase <= b.startBase
    }
  }

  private fun overlapsRange(hunk: EditHunk, start: Int, end: Int): Boolean {
    return if (start == end) {
      hunk.startBase <= end && hunk.endBase >= start
    } else if (hunk.startBase == hunk.endBase) {
      hunk.startBase in start..end
    } else {
      hunk.startBase < end && hunk.endBase > start
    }
  }

  private fun applyHunksToSlice(
    baseLines: List<String>,
    sliceStart: Int,
    sliceEnd: Int,
    hunks: List<EditHunk>,
  ): List<String> {
    val result = mutableListOf<String>()
    var cursor = sliceStart
    hunks.forEach { h ->
      if (cursor < h.startBase) {
        result.addAll(baseLines.subList(cursor, h.startBase))
      }
      result.addAll(h.replacement)
      cursor = maxOf(cursor, h.endBase)
    }
    if (cursor < sliceEnd) {
      result.addAll(baseLines.subList(cursor, sliceEnd))
    }
    return result
  }

  private fun extractEditHunks(baseLines: List<String>, targetLines: List<String>): List<EditHunk> {
    if (baseLines.isEmpty() && targetLines.isEmpty()) return emptyList()
    if (baseLines.isEmpty()) return listOf(EditHunk(0, 0, targetLines))
    if (targetLines.isEmpty()) return listOf(EditHunk(0, baseLines.size, emptyList()))

    val diff = CodeFormatter.computeLineDiff(baseLines.joinToString("\n"), targetLines.joinToString("\n"))
    val hunks = mutableListOf<EditHunk>()
    var i = 0
    var nextBaseIdx = 0
    while (i < diff.size) {
      val line = diff[i]
      if (line.type == DiffType.UNCHANGED) {
        nextBaseIdx = (line.lineNumberOld ?: (nextBaseIdx + 1))
        i++
        continue
      }
      val hunkStart = nextBaseIdx
      var hunkEnd = nextBaseIdx
      val replacement = mutableListOf<String>()
      while (i < diff.size && diff[i].type != DiffType.UNCHANGED) {
        val item = diff[i]
        when (item.type) {
          DiffType.REMOVED -> {
            val oldNum = item.lineNumberOld ?: (hunkEnd + 1)
            hunkEnd = maxOf(hunkEnd, oldNum)
            nextBaseIdx = hunkEnd
          }
          DiffType.ADDED -> replacement.add(item.text)
          DiffType.UNCHANGED -> Unit
        }
        i++
      }
      hunks.add(EditHunk(startBase = hunkStart, endBase = hunkEnd, replacement = replacement))
    }
    return hunks
  }

  private fun splitLinesSafe(text: String): List<String> {
    return if (text.isEmpty()) emptyList() else text.split('\n')
  }
}
