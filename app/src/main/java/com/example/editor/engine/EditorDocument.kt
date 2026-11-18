package com.example.editor.engine

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Immutable line-indexed document backed by a [Rope] and an incremental [lineStarts] table.
 * Full-string materialization ([text]) is deferred until explicitly requested; per-line reads and
 * edits touch only O(log N + lineLen).
 */
@Immutable
class EditorDocument private constructor(
  private val rope: Rope,
  private val lineStarts: IntArray,
  initialText: String?,
) {
  @Volatile private var cachedText: String? = initialText

  val chars: CharSequence get() = rope

  val text: String
    get() = cachedText ?: rope.toString().also { cachedText = it }

  suspend fun awaitText(): String =
    cachedText ?: withContext(Dispatchers.Default) { rope.toString() }.also { cachedText = it }

  val length: Int get() = rope.length
  val lineCount: Int get() = lineStarts.size

  fun charAt(offset: Int): Char = rope[offset]

  fun substring(start: Int, end: Int): String = rope.substring(start, end)

  fun lineStart(line: Int): Int = lineStarts[line.coerceIn(0, lineCount - 1)]

  fun lineEnd(line: Int): Int {
    val idx = line.coerceIn(0, lineCount - 1)
    return if (idx + 1 < lineCount) lineStarts[idx + 1] - 1 else rope.length
  }

  fun lineLength(line: Int): Int = (lineEnd(line) - lineStart(line)).coerceAtLeast(0)

  fun lineText(line: Int): String = rope.substring(lineStart(line), lineEnd(line))

  fun lineForOffset(offset: Int): Int {
    val clamped = offset.coerceIn(0, rope.length)
    var lo = 0
    var hi = lineStarts.size - 1
    while (lo < hi) {
      val mid = (lo + hi + 1) ushr 1
      if (lineStarts[mid] <= clamped) lo = mid else hi = mid - 1
    }
    return lo
  }

  fun columnOf(offset: Int): Int {
    val clamped = offset.coerceIn(0, rope.length)
    val line = lineForOffset(clamped)
    return clamped - lineStarts[line]
  }

  fun offsetOf(line: Int, col: Int): Int {
    val idx = line.coerceIn(0, lineCount - 1)
    val start = lineStarts[idx]
    val end = lineEnd(idx)
    return (start + col.coerceAtLeast(0)).coerceAtMost(end)
  }

  fun replace(start: Int, end: Int, replacement: String): EditorDocument {
    val s = start.coerceIn(0, rope.length)
    val e = end.coerceIn(s, rope.length)
    if (s == e && replacement.isEmpty()) return this

    val normalized = normalizeNewlines(replacement)
    val nextRope = rope.replace(s, e, normalized)
    val delta = normalized.length - (e - s)

    val startLine = lineForOffset(s)
    val endLine = lineForOffset(e)

    // Count new newlines inside inserted text
    var addedNewlines = 0
    for (i in normalized.indices) {
      if (normalized[i] == '\n') addedNewlines++
    }

    val removedNewlines = endLine - startLine
    val newLineCount = (lineStarts.size - removedNewlines + addedNewlines).coerceAtLeast(1)
    val nextStarts = IntArray(newLineCount)

    // 1. Copy unchanged prefix lines [0 .. startLine]
    lineStarts.copyInto(nextStarts, destinationOffset = 0, startIndex = 0, endIndex = startLine + 1)

    // 2. Write line starts introduced inside normalized replacement
    var writeIdx = startLine + 1
    for (i in normalized.indices) {
      if (normalized[i] == '\n') {
        nextStarts[writeIdx++] = s + i + 1
      }
    }

    // 3. Copy & shift remaining lines after endLine
    for (oldIdx in (endLine + 1) until lineStarts.size) {
      nextStarts[writeIdx++] = lineStarts[oldIdx] + delta
    }

    return EditorDocument(nextRope, nextStarts, initialText = null)
  }

  companion object {
    val EMPTY = EditorDocument(Rope.EMPTY, intArrayOf(0), "")

    fun of(raw: String): EditorDocument {
      if (raw.isEmpty()) return EMPTY
      val text = normalizeNewlines(raw)
      val starts = computeLineStarts(text)
      return EditorDocument(Rope.of(text), starts, initialText = text)
    }

    fun normalizeNewlines(s: String): String {
      if ('\r' !in s) return s
      val sb = StringBuilder(s.length)
      var i = 0
      while (i < s.length) {
        val c = s[i]
        if (c == '\r') {
          sb.append('\n')
          if (i + 1 < s.length && s[i + 1] == '\n') i++
        } else {
          sb.append(c)
        }
        i++
      }
      return sb.toString()
    }

    private fun computeLineStarts(text: String): IntArray {
      var count = 1
      for (i in text.indices) {
        if (text[i] == '\n') count++
      }
      val starts = IntArray(count)
      var idx = 1
      for (i in text.indices) {
        if (text[i] == '\n') starts[idx++] = i + 1
      }
      return starts
    }
  }
}
