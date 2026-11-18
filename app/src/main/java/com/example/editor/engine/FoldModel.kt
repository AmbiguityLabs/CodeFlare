package com.example.editor.engine

import androidx.compose.runtime.Immutable
import com.example.data.remote.WorkerLanguage

enum class FoldKind {
  BLOCK,
  IMPORTS,
  COMMENT,
}

@Immutable
data class FoldRegion(
  val startLine: Int,
  val endLine: Int,
  val placeholder: String = "…",
  val kind: FoldKind = FoldKind.BLOCK,
) {
  init {
    require(endLine > startLine) { "FoldRegion endLine ($endLine) must be > startLine ($startLine)" }
  }
}

@Immutable
data class FoldedLineInfo(
  val region: FoldRegion,
  val prefixText: String,
  val suffixText: String,
)

/**
 * Projection between 0-based document lines and 0-based visible (unfolded) lines.
 * Pure, immutable, O(log N) lookup.
 */
@Immutable
class FoldModel(
  val docLineCount: Int,
  val collapsed: List<FoldRegion>,
) {
  private val activeCollapsed: List<FoldRegion>
  private val visibleDocLines: IntArray
  private val collapsedStartMap: Map<Int, FoldRegion>

  init {
    val sorted =
      collapsed
        .filter { it.startLine in 0 until docLineCount && it.endLine in (it.startLine + 1) until docLineCount }
        .sortedWith(compareBy<FoldRegion>({ it.startLine }, { -it.endLine }))

    val nonOverlapping = ArrayList<FoldRegion>(sorted.size)
    var coveredThrough = -1
    for (r in sorted) {
      if (r.startLine > coveredThrough) {
        nonOverlapping.add(r)
        coveredThrough = r.endLine
      }
    }
    activeCollapsed = nonOverlapping
    collapsedStartMap = nonOverlapping.associateBy { it.startLine }

    val hiddenCount = nonOverlapping.sumOf { it.endLine - it.startLine }
    val vis = IntArray((docLineCount - hiddenCount).coerceAtLeast(1))
    var write = 0
    var foldIdx = 0
    var line = 0
    while (line < docLineCount && write < vis.size) {
      vis[write++] = line
      if (foldIdx < nonOverlapping.size && nonOverlapping[foldIdx].startLine == line) {
        line = nonOverlapping[foldIdx].endLine + 1
        foldIdx++
      } else {
        line++
      }
    }
    visibleDocLines = vis
  }

  val visualLineCount: Int get() = visibleDocLines.size

  fun docLineForVisual(visualLine: Int): Int {
    if (visibleDocLines.isEmpty()) return 0
    return visibleDocLines[visualLine.coerceIn(0, visibleDocLines.lastIndex)]
  }

  fun visualLineForDoc(docLine: Int): Int {
    if (visibleDocLines.isEmpty()) return 0
    val target = docLine.coerceIn(0, (docLineCount - 1).coerceAtLeast(0))
    var lo = 0
    var hi = visibleDocLines.lastIndex
    while (lo < hi) {
      val mid = (lo + hi + 1) ushr 1
      if (visibleDocLines[mid] <= target) lo = mid else hi = mid - 1
    }
    return lo
  }

  fun foldStartingAt(docLine: Int): FoldRegion? = collapsedStartMap[docLine]

  fun foldContaining(docLine: Int): FoldRegion? {
    for (r in activeCollapsed) {
      if (docLine > r.startLine && docLine <= r.endLine) return r
      if (r.startLine > docLine) break
    }
    return null
  }

  fun isHidden(docLine: Int): Boolean = foldContaining(docLine) != null

  fun sameProjection(other: FoldModel): Boolean =
    docLineCount == other.docLineCount && activeCollapsed == other.activeCollapsed

  fun buildFoldedLineInfo(doc: EditorDocument, docLine: Int): FoldedLineInfo? {
    val region = foldStartingAt(docLine) ?: return null
    val startText = doc.lineText(region.startLine).trimEnd()
    val endText = doc.lineText(region.endLine).trim()
    val suffix =
      when (region.kind) {
        FoldKind.IMPORTS -> ""
        FoldKind.COMMENT -> if (endText.endsWith("*/")) " */" else ""
        FoldKind.BLOCK -> {
          val closing = endText.takeWhile { it == '}' || it == ']' || it == ')' || it == ';' || it == ',' }
          if (closing.isNotEmpty()) " $closing" else ""
        }
      }
    return FoldedLineInfo(region = region, prefixText = startText, suffixText = suffix)
  }

  companion object {
    fun flat(docLineCount: Int): FoldModel = FoldModel(docLineCount.coerceAtLeast(1), emptyList())
  }
}

fun shiftFoldRegions(
  regions: Set<FoldRegion>,
  editStartLine: Int,
  editOldEndLine: Int,
  lineDelta: Int,
): Set<FoldRegion> {
  if (regions.isEmpty()) return regions
  val out = LinkedHashSet<FoldRegion>(regions.size)
  for (r in regions) {
    when {
      r.endLine < editStartLine -> out.add(r)
      r.startLine > editOldEndLine -> {
        val ns = r.startLine + lineDelta
        val ne = r.endLine + lineDelta
        if (ns >= 0 && ne > ns) out.add(r.copy(startLine = ns, endLine = ne))
      }
      editStartLine > r.startLine && editOldEndLine <= r.endLine -> {
        val ne = r.endLine + lineDelta
        if (ne > r.startLine) out.add(r.copy(endLine = ne))
      }
      // Edit overlaps fold header: unfold cleanly
    }
  }
  return out
}

/**
 * Lightweight JS/TS/JSON fold detector covering:
 * - Multi-line `{ ... }` and `[ ... ]` blocks
 * - Multi-line `/* ... */` comments
 * - Consecutive `import ...` blocks (≥ 3 lines)
 */
object EdgeFoldDetector {
  fun detect(doc: EditorDocument, language: WorkerLanguage): List<FoldRegion> {
    if (language == WorkerLanguage.TEXT || doc.lineCount < 2) return emptyList()
    val regions = ArrayList<FoldRegion>(32)
    val braceStack = ArrayDeque<Pair<Char, Int>>() // opener -> 0-based line

    var inBlockComment = false
    var commentStartLine = -1
    var inTemplate = false
    var importStartLine = -1
    var importLastLine = -1

    fun flushImports() {
      if (importStartLine >= 0 && importLastLine - importStartLine >= 2) {
        regions.add(
          FoldRegion(
            startLine = importStartLine,
            endLine = importLastLine,
            placeholder = "${importLastLine - importStartLine + 1} imports",
            kind = FoldKind.IMPORTS,
          ),
        )
      }
      importStartLine = -1
      importLastLine = -1
    }

    val maxLines = minOf(doc.lineCount, 8_000)
    for (lineIdx in 0 until maxLines) {
      val line = doc.lineText(lineIdx)
      val trimmed = line.trimStart()

      if (language != WorkerLanguage.JSON && !inBlockComment && !inTemplate) {
        if (trimmed.startsWith("import ") || trimmed.startsWith("import{")) {
          if (importStartLine < 0) importStartLine = lineIdx
          importLastLine = lineIdx
        } else if (trimmed.isNotEmpty()) {
          flushImports()
        }
      }

      var i = 0
      val len = line.length
      while (i < len) {
        val c = line[i]
        val next = if (i + 1 < len) line[i + 1] else '\u0000'

        if (inBlockComment) {
          if (c == '*' && next == '/') {
            inBlockComment = false
            if (commentStartLine >= 0 && lineIdx > commentStartLine) {
              regions.add(FoldRegion(commentStartLine, lineIdx, "/*…*/", FoldKind.COMMENT))
            }
            commentStartLine = -1
            i += 2
          } else {
            i++
          }
          continue
        }

        if (inTemplate) {
          if (c == '\\') {
            i += 2
          } else if (c == '`') {
            inTemplate = false
            i++
          } else {
            i++
          }
          continue
        }

        if (c == '/' && next == '/') break
        if (c == '/' && next == '*') {
          inBlockComment = true
          commentStartLine = lineIdx
          i += 2
          continue
        }
        if (c == '"' || c == '\'') {
          val q = c
          i++
          while (i < len) {
            if (line[i] == '\\') i += 2
            else if (line[i] == q) {
              i++
              break
            } else {
              i++
            }
          }
          continue
        }
        if (c == '`' && language != WorkerLanguage.JSON) {
          inTemplate = true
          i++
          continue
        }

        if (c == '{' || c == '[') {
          braceStack.addLast(c to lineIdx)
        } else if (c == '}' || c == ']') {
          val expected = if (c == '}') '{' else '['
          if (braceStack.isNotEmpty() && braceStack.last().first == expected) {
            val openLine = braceStack.removeLast().second
            if (lineIdx > openLine) {
              regions.add(FoldRegion(openLine, lineIdx, "…", FoldKind.BLOCK))
            }
          }
        }
        i++
      }
    }
    flushImports()

    return regions
      .distinctBy { it.startLine }
      .sortedBy { it.startLine }
  }
}
