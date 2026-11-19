package com.example.editor.selection

/**
 * Bounded-window lexical & delimiter scanner supporting JS/TS/JSON/HTML/CSS/Log text.
 *
 * Operates strictly within `[windowStart, windowEnd)` (default ±32 KB around the anchor offset)
 * so selection resolution remains sub-millisecond even in 100k-line files, while handling
 * escaped quotes, nested JS template `${...}` expressions, comments, and unclosed/broken syntax.
 */
internal data class DelimiterSpan(
  val openChar: Char,
  val closeChar: Char,
  val openOffset: Int,
  val closeOffset: Int, // index of closing char; full range is [openOffset, closeOffset + 1)
  val isTemplateExpr: Boolean = false,
) {
  val innerStart: Int get() = if (isTemplateExpr) openOffset + 1 else openOffset + 1
  val innerEnd: Int get() = closeOffset
  val fullStart: Int get() = if (isTemplateExpr && openOffset > 0) openOffset - 1 else openOffset
  val fullEnd: Int get() = closeOffset + 1
}

internal data class QuotedSpan(
  val quote: Char,
  val startOffset: Int, // index of opening quote
  val endOffset: Int, // exclusive end (after closing quote, or line/window end if unclosed)
  val closed: Boolean,
) {
  val innerStart: Int get() = (startOffset + 1).coerceAtMost(endOffset)
  val innerEnd: Int get() = if (closed) (endOffset - 1).coerceAtLeast(innerStart) else endOffset
}

internal data class StructuralScanResult(
  val windowStart: Int,
  val windowEnd: Int,
  val enclosingQuotes: List<QuotedSpan>,
  val enclosingBrackets: List<DelimiterSpan>,
  /**
   * Length == (windowEnd - windowStart). Characters inside strings and comments are masked to
   * spaces (preserving '\n') so structural boundary checks never false-match inside literals.
   */
  val masked: CharArray,
)

internal object StructuralScanner {
  const val WINDOW_RADIUS = 32_768

  fun computeWindow(text: CharSequence, anchorOffset: Int): Pair<Int, Int> {
    val len = text.length
    if (len <= WINDOW_RADIUS * 2) return 0 to len
    var start = (anchorOffset - WINDOW_RADIUS).coerceAtLeast(0)
    while (start > 0 && text[start - 1] != '\n') start--
    var end = (anchorOffset + WINDOW_RADIUS).coerceAtMost(len)
    while (end < len && text[end] != '\n') end++
    return start to end
  }

  fun scan(
    text: CharSequence,
    anchorOffset: Int,
    windowStart: Int,
    windowEnd: Int,
    allowSingleQuotes: Boolean = true,
    allowBackticks: Boolean = true,
    allowLineComments: Boolean = true,
    allowBlockComments: Boolean = true,
    allowAngleBrackets: Boolean = false,
  ): StructuralScanResult {
    val spanLen = (windowEnd - windowStart).coerceAtLeast(0)
    val masked = CharArray(spanLen) { ' ' }
    val enclosingQuotes = ArrayList<QuotedSpan>(4)
    val enclosingBrackets = ArrayList<DelimiterSpan>(8)

    data class OpenDelim(val ch: Char, val offset: Int, val isTemplateExpr: Boolean = false)
    data class OpenTemplate(val startOffset: Int)

    val delimStack = ArrayDeque<OpenDelim>()
    val templateStack = ArrayDeque<OpenTemplate>()
    var inTemplateText = false

    fun putMask(absIdx: Int, ch: Char) {
      val rel = absIdx - windowStart
      if (rel in 0 until spanLen) masked[rel] = ch
    }

    fun maskIfNewline(absIdx: Int) {
      if (text[absIdx] == '\n') putMask(absIdx, '\n')
    }

    var i = windowStart
    while (i < windowEnd) {
      val c = text[i]
      val next = if (i + 1 < windowEnd) text[i + 1] else '\u0000'

      if (inTemplateText) {
        if (c == '\\' && i + 1 < windowEnd) {
          maskIfNewline(i)
          maskIfNewline(i + 1)
          i += 2
          continue
        }
        if (c == '$' && next == '{') {
          delimStack.addLast(OpenDelim('{', i + 1, isTemplateExpr = true))
          i += 2
          inTemplateText = false
          continue
        }
        if (c == '`') {
          val opened = if (templateStack.isNotEmpty()) templateStack.removeLast() else null
          if (opened != null) {
            val qSpan = QuotedSpan('`', opened.startOffset, i + 1, closed = true)
            if (anchorOffset in qSpan.startOffset..qSpan.endOffset) {
              enclosingQuotes.add(qSpan)
            }
          }
          i++
          inTemplateText = false
          continue
        }
        maskIfNewline(i)
        i++
        continue
      }

      // Line comment //
      if (allowLineComments && c == '/' && next == '/') {
        i += 2
        while (i < windowEnd && text[i] != '\n') i++
        if (i < windowEnd && text[i] == '\n') {
          putMask(i, '\n')
          i++
        }
        continue
      }

      // Block comment /* ... */
      if (allowBlockComments && c == '/' && next == '*') {
        i += 2
        while (i < windowEnd) {
          if (text[i] == '*' && i + 1 < windowEnd && text[i + 1] == '/') {
            i += 2
            break
          }
          maskIfNewline(i)
          i++
        }
        continue
      }

      // Template literal start `
      if (allowBackticks && c == '`') {
        templateStack.addLast(OpenTemplate(i))
        i++
        inTemplateText = true
        continue
      }

      // Single or double-quoted string
      if (c == '"' || (allowSingleQuotes && c == '\'')) {
        val q = c
        val strStart = i
        i++
        var closed = false
        while (i < windowEnd) {
          val ch = text[i]
          if (ch == '\n') break
          if (ch == '\\' && i + 1 < windowEnd) {
            maskIfNewline(i + 1)
            i += 2
            continue
          }
          if (ch == q) {
            i++
            closed = true
            break
          }
          i++
        }
        val qSpan = QuotedSpan(q, strStart, i, closed = closed)
        if (anchorOffset in qSpan.startOffset..qSpan.endOffset) {
          enclosingQuotes.add(qSpan)
        }
        continue
      }

      putMask(i, c)

      when (c) {
        '(', '[', '{' -> delimStack.addLast(OpenDelim(c, i))
        '<' -> if (allowAngleBrackets) delimStack.addLast(OpenDelim('<', i))
        ')', ']', '}', '>' -> {
          if (c == '>' && !allowAngleBrackets) {
            i++
            continue
          }
          val expectedOpen =
            when (c) {
              ')' -> '('
              ']' -> '['
              '}' -> '{'
              else -> '<'
            }
          // Error-tolerant pop: unwind to matching opener if present on stack
          var matchIdx = -1
          for (k in delimStack.size - 1 downTo 0) {
            if (delimStack[k].ch == expectedOpen) {
              matchIdx = k
              break
            }
          }
          if (matchIdx >= 0) {
            while (delimStack.size - 1 > matchIdx) {
              delimStack.removeLast()
            }
            val open = delimStack.removeLast()
            val span = DelimiterSpan(open.ch, c, open.offset, i, open.isTemplateExpr)
            if (anchorOffset in span.fullStart..span.fullEnd) {
              enclosingBrackets.add(span)
            }
            if (open.isTemplateExpr && c == '}') {
              inTemplateText = true
            }
          }
        }
      }
      i++
    }

    // Handle unclosed template literal covering anchor (broken code tolerance)
    while (templateStack.isNotEmpty()) {
      val unclosed = templateStack.removeLast()
      val lineEnd = findLineEnd(text, anchorOffset.coerceAtLeast(unclosed.startOffset), windowEnd)
      if (anchorOffset in unclosed.startOffset..lineEnd) {
        enclosingQuotes.add(QuotedSpan('`', unclosed.startOffset, lineEnd, closed = false))
      }
    }

    enclosingQuotes.sortBy { it.endOffset - it.startOffset }
    enclosingBrackets.sortBy { it.fullEnd - it.fullStart }

    return StructuralScanResult(
      windowStart = windowStart,
      windowEnd = windowEnd,
      enclosingQuotes = enclosingQuotes,
      enclosingBrackets = enclosingBrackets,
      masked = masked,
    )
  }

  fun findLineStart(text: CharSequence, offset: Int): Int {
    var i = offset.coerceIn(0, text.length)
    while (i > 0 && text[i - 1] != '\n') i--
    return i
  }

  fun findLineEnd(text: CharSequence, offset: Int, limit: Int = text.length): Int {
    var i = offset.coerceIn(0, limit.coerceAtMost(text.length))
    val max = limit.coerceAtMost(text.length)
    while (i < max && text[i] != '\n') i++
    return i
  }
}
