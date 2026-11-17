package com.example.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.remote.WorkerLanguage

object CodeEditHelper {
  fun computeLineCount(text: String): Int {
    var count = 1
    for (i in 0 until text.length) {
      if (text[i] == '\n') count++
    }
    return count
  }

  fun computeLineStarts(text: String): IntArray {
    val lineCount = computeLineCount(text)
    val starts = IntArray(lineCount)
    starts[0] = 0
    var idx = 1
    for (i in 0 until text.length) {
      if (text[i] == '\n' && idx < lineCount) {
        starts[idx++] = i + 1
      }
    }
    return starts
  }

  fun computeCursorLineAndCol(text: String, cursorOffset: Int): Pair<Int, Int> {
    val pos = cursorOffset.coerceIn(0, text.length)
    var line = 1
    var lastNl = -1
    for (i in 0 until pos) {
      if (text[i] == '\n') {
        line++
        lastNl = i
      }
    }
    return line to (pos - lastNl)
  }

  /**
   * Fast line-local smart editing for Enter auto-indent and bracket/quote pairing.
   * Strictly skips IME composing regions and multi-character pastes so keyboard input never stutters.
   */
  fun applySmartCodeEdit(
    prev: TextFieldValue,
    next: TextFieldValue,
    language: WorkerLanguage,
    tabSize: Int,
    autoCloseBrackets: Boolean,
  ): TextFieldValue {
    if (language == WorkerLanguage.TEXT) return next
    if (next.composition != null || !next.selection.collapsed) return next
    val prevText = prev.text
    val nextText = next.text
    val cursor = next.selection.start

    // 1. Smart backspace inside empty bracket/quote pair: () {} [] "" '' ``
    if (autoCloseBrackets &&
      prevText.length == nextText.length + 1 &&
      prev.selection.collapsed &&
      cursor in 0 until nextText.length
    ) {
      val deletedPos = prev.selection.start - 1
      if (deletedPos == cursor && deletedPos in prevText.indices && deletedPos + 1 < prevText.length) {
        val left = prevText[deletedPos]
        val right = prevText[deletedPos + 1]
        if (isMatchingPair(left, right)) {
          val updated = nextText.removeRange(cursor, cursor + 1)
          return next.copy(text = updated, selection = TextRange(cursor))
        }
      }
      return next
    }

    // Only apply insertion helpers for single-character non-composing insertion
    if (nextText.length != prevText.length + 1 || cursor <= 0 || cursor > nextText.length) {
      return next
    }

    val inserted = nextText[cursor - 1]

    // 2. Smart Enter auto-indent (and block-splitting between { | } or [ | ])
    if (inserted == '\n') {
      val prevLineEnd = cursor - 1
      val prevLineStart = nextText.lastIndexOf('\n', (prevLineEnd - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
      var indentEnd = prevLineStart
      while (indentEnd < prevLineEnd && (nextText[indentEnd] == ' ' || nextText[indentEnd] == '\t')) {
        indentEnd++
      }
      val baseIndent = nextText.substring(prevLineStart, indentEnd)

      var trimEnd = prevLineEnd - 1
      while (trimEnd >= prevLineStart && nextText[trimEnd].isWhitespace()) trimEnd--
      val lastCharBeforeNewline = if (trimEnd >= prevLineStart) nextText[trimEnd] else '\u0000'

      var nextNonWs = cursor
      while (nextNonWs < nextText.length && (nextText[nextNonWs] == ' ' || nextText[nextNonWs] == '\t')) {
        nextNonWs++
      }
      val nextCharAfterNewline = if (nextNonWs < nextText.length) nextText[nextNonWs] else '\u0000'

      val opensBlock = lastCharBeforeNewline == '{' || lastCharBeforeNewline == '[' || lastCharBeforeNewline == '('
      val extraIndent = if (opensBlock) " ".repeat(tabSize.coerceIn(1, 8)) else ""
      val innerIndent = baseIndent + extraIndent

      val splitsEmptyBlock =
        (lastCharBeforeNewline == '{' && nextCharAfterNewline == '}') ||
          (lastCharBeforeNewline == '[' && nextCharAfterNewline == ']') ||
          (lastCharBeforeNewline == '(' && nextCharAfterNewline == ')')

      if (splitsEmptyBlock) {
        val insertion = "$innerIndent\n$baseIndent"
        val updated = nextText.replaceRange(cursor, nextNonWs, insertion)
        val newCursor = cursor + innerIndent.length
        return next.copy(text = updated, selection = TextRange(newCursor))
      }

      if (innerIndent.isNotEmpty()) {
        val updated = nextText.substring(0, cursor) + innerIndent + nextText.substring(cursor)
        return next.copy(text = updated, selection = TextRange(cursor + innerIndent.length))
      }
      return next
    }

    if (!autoCloseBrackets) return next

    val charAfter = if (cursor < nextText.length) nextText[cursor] else '\u0000'

    // 3. Skip over already-present closing bracket/quote
    if ((inserted == ')' || inserted == '}' || inserted == ']' || inserted == '"' || inserted == '\'' || inserted == '`') &&
      charAfter == inserted
    ) {
      val updated = nextText.removeRange(cursor - 1, cursor)
      return next.copy(text = updated, selection = TextRange(cursor))
    }

    // 4. Auto-close opening brackets/quotes when followed by whitespace, newline, or delimiter
    val closing =
      when (inserted) {
        '(' -> ')'
        '{' -> '}'
        '[' -> ']'
        '"' -> '"'
        '\'' -> '\''
        '`' -> '`'
        else -> null
      } ?: return next

    val canAutoCloseAfter =
      charAfter == '\u0000' ||
        charAfter.isWhitespace() ||
        charAfter in charArrayOf(')', '}', ']', ',', ';', ':')

    if (!canAutoCloseAfter) return next

    // Avoid auto-closing quote right after word/number/same quote (e.g., apostrophe in comments/text)
    if (inserted == '"' || inserted == '\'' || inserted == '`') {
      val charBefore = if (cursor >= 2) nextText[cursor - 2] else '\u0000'
      if (charBefore.isLetterOrDigit() || charBefore == inserted || charBefore == '\\') {
        return next
      }
    }

    val updated = nextText.substring(0, cursor) + closing + nextText.substring(cursor)
    return next.copy(text = updated, selection = TextRange(cursor))
  }

  private fun isMatchingPair(open: Char, close: Char): Boolean {
    return (open == '(' && close == ')') ||
      (open == '{' && close == '}') ||
      (open == '[' && close == ']') ||
      (open == '"' && close == '"') ||
      (open == '\'' && close == '\'') ||
      (open == '`' && close == '`')
  }
}
