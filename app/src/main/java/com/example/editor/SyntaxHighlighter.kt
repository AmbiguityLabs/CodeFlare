package com.example.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.remote.WorkerLanguage
import com.example.ui.theme.IdePalette

class CodeSyntaxVisualTransformation(
  private val language: WorkerLanguage,
  private val palette: IdePalette,
) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val highlighted = SyntaxHighlighter.highlight(text.text, language, palette)
    return TransformedText(highlighted, OffsetMapping.Identity)
  }
}

object SyntaxHighlighter {
  private val jsKeywords =
    setOf(
      "const",
      "let",
      "var",
      "function",
      "class",
      "extends",
      "new",
      "this",
      "super",
      "typeof",
      "instanceof",
      "in",
      "of",
      "void",
      "delete",
      "true",
      "false",
      "null",
      "undefined",
      "interface",
      "type",
      "enum",
      "implements",
      "private",
      "public",
      "readonly",
    )

  private val controlKeywords =
    setOf(
      "export",
      "import",
      "from",
      "default",
      "as",
      "async",
      "await",
      "return",
      "if",
      "else",
      "for",
      "while",
      "do",
      "switch",
      "case",
      "break",
      "continue",
      "try",
      "catch",
      "finally",
      "throw",
      "yield",
    )

  private val cloudflareGlobals =
    setOf(
      "Request",
      "Response",
      "Headers",
      "URL",
      "URLSearchParams",
      "fetch",
      "caches",
      "crypto",
      "TextEncoder",
      "TextDecoder",
      "ReadableStream",
      "WritableStream",
      "TransformStream",
      "WebSocketPair",
      "HTMLRewriter",
      "JSON",
      "Math",
      "Date",
      "Promise",
      "Map",
      "Set",
      "Array",
      "Object",
      "String",
      "Number",
      "Error",
      "console",
      "env",
      "ctx",
    )

  fun highlight(code: String, language: WorkerLanguage, palette: IdePalette): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")
    if (language == WorkerLanguage.TEXT) return AnnotatedString(code)

    val builder = AnnotatedString.Builder(code)
    val len = code.length
    var i = 0

    while (i < len) {
      val c = code[i]

      // Line comment //
      if (c == '/' && i + 1 < len && code[i + 1] == '/') {
        val end = code.indexOf('\n', i).let { if (it == -1) len else it }
        builder.addStyle(
          SpanStyle(color = palette.syntaxComment, fontStyle = FontStyle.Italic),
          i,
          end,
        )
        i = end
        continue
      }

      // Block comment /* ... */
      if (c == '/' && i + 1 < len && code[i + 1] == '*') {
        val end = code.indexOf("*/", i + 2).let { if (it == -1) len else it + 2 }
        builder.addStyle(
          SpanStyle(color = palette.syntaxComment, fontStyle = FontStyle.Italic),
          i,
          end,
        )
        i = end
        continue
      }

      // Strings ', ", `
      if (c == '"' || c == '\'' || c == '`') {
        val quote = c
        var j = i + 1
        while (j < len) {
          if (code[j] == '\\' && j + 1 < len) {
            j += 2
            continue
          }
          if (code[j] == quote) {
            j++
            break
          }
          j++
        }
        val isJsonKey =
          language == WorkerLanguage.JSON && quote == '"' && isFollowedByColon(code, j)
        val style =
          if (isJsonKey) SpanStyle(color = palette.syntaxProperty, fontWeight = FontWeight.Medium)
          else SpanStyle(color = palette.syntaxString)
        builder.addStyle(style, i, j)
        i = j
        continue
      }

      // Numbers
      if (c.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit())) {
        var j = i + 1
        while (j < len && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) {
          j++
        }
        builder.addStyle(SpanStyle(color = palette.syntaxNumber), i, j)
        i = j
        continue
      }

      // Identifiers & Keywords
      if (c.isLetter() || c == '_' || c == '$') {
        var j = i + 1
        while (j < len && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '$')) {
          j++
        }
        val word = code.substring(i, j)
        val style =
          when {
            language == WorkerLanguage.JSON && (word == "true" || word == "false" || word == "null") ->
              SpanStyle(color = palette.syntaxKeyword, fontWeight = FontWeight.SemiBold)
            word in controlKeywords ->
              SpanStyle(color = palette.syntaxControl, fontWeight = FontWeight.Medium)
            word in jsKeywords ->
              SpanStyle(color = palette.syntaxKeyword, fontWeight = FontWeight.Medium)
            word in cloudflareGlobals -> SpanStyle(color = palette.syntaxType)
            j < len && code[j] == '(' -> SpanStyle(color = palette.syntaxFunction)
            i > 0 && code[i - 1] == '.' -> SpanStyle(color = palette.syntaxProperty)
            else -> null
          }
        if (style != null) {
          builder.addStyle(style, i, j)
        }
        i = j
        continue
      }

      // Brackets & operators
      if (c in "{}()[]=>+-*/!&|?:") {
        builder.addStyle(SpanStyle(color = palette.syntaxOperator), i, i + 1)
      }

      i++
    }

    return builder.toAnnotatedString()
  }

  private fun isFollowedByColon(code: String, startIndex: Int): Boolean {
    var k = startIndex
    while (k < code.length && code[k].isWhitespace()) k++
    return k < code.length && code[k] == ':'
  }
}
