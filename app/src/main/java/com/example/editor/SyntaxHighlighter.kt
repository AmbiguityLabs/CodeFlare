package com.example.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.example.data.remote.DiagnosticItem
import com.example.data.remote.DiagnosticSeverity
import com.example.data.remote.WorkerLanguage
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.IdePalette
import com.example.ui.theme.RoseError

class CodeSyntaxVisualTransformation(
  private val language: WorkerLanguage,
  private val palette: IdePalette,
  private val diagnostics: List<DiagnosticItem> = emptyList(),
  private val cursorOffset: Int = -1,
) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val highlighted =
      SyntaxHighlighter.highlightWithDiagnostics(
        code = text.text,
        language = language,
        palette = palette,
        diagnostics = diagnostics,
        cursorOffset = cursorOffset,
      )
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

  private data class HighlightCacheKey(
    val textHash: Int,
    val length: Int,
    val language: WorkerLanguage,
    val isDark: Boolean,
  )

  private data class HighlightCacheEntry(
    val rawText: String,
    val highlighted: AnnotatedString,
  )

  private val cacheLock = Any()
  private val highlightCache =
    object : LinkedHashMap<HighlightCacheKey, HighlightCacheEntry>(32, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HighlightCacheKey, HighlightCacheEntry>?): Boolean {
        return size > 24
      }
    }

  private const val MAX_HIGHLIGHT_CHARS = 30_000

  fun highlightWithDiagnostics(
    code: String,
    language: WorkerLanguage,
    palette: IdePalette,
    diagnostics: List<DiagnosticItem> = emptyList(),
    cursorOffset: Int = -1,
  ): AnnotatedString {
    val base = highlight(code, language, palette)
    if (code.isEmpty() || code.length > MAX_HIGHLIGHT_CHARS) return base

    val bracketMatch =
      if (cursorOffset >= 0) CodeAnalyzer.findMatchingBracket(code, cursorOffset, language)
      else null

    val actionableDiags = diagnostics.filter { it.severity != DiagnosticSeverity.INFO }
    if (actionableDiags.isEmpty() && bracketMatch == null) {
      return base
    }

    val builder = AnnotatedString.Builder(base)

    // Precompute line start offsets once when diagnostics exist
    if (actionableDiags.isNotEmpty()) {
      val lineStarts = computeLineStarts(code)
      actionableDiags.forEach { diag ->
        val lineIdx = (diag.line - 1).coerceIn(0, lineStarts.lastIndex)
        val lineStart = lineStarts[lineIdx]
        val nextLineStart = if (lineIdx + 1 < lineStarts.size) lineStarts[lineIdx + 1] else code.length
        val lineEnd =
          if (nextLineStart > lineStart && code[nextLineStart - 1] == '\n') nextLineStart - 1
          else nextLineStart

        if (lineStart < lineEnd) {
          val rawStart = (lineStart + (diag.column - 1).coerceAtLeast(0)).coerceIn(lineStart, lineEnd - 1)
          val spanLen =
            if (diag.length > 1) {
              diag.length
            } else {
              // Expand single-char span to cover adjacent identifier/token if applicable, or keep 1 char
              var end = rawStart + 1
              if (code[rawStart].isLetterOrDigit() || code[rawStart] == '_' || code[rawStart] == '$') {
                while (end < lineEnd && (code[end].isLetterOrDigit() || code[end] == '_' || code[end] == '$')) {
                  end++
                }
              }
              (end - rawStart).coerceAtLeast(1)
            }
          val rawEnd = (rawStart + spanLen).coerceIn(rawStart + 1, lineEnd)
          val isErr = diag.severity == DiagnosticSeverity.ERROR
          val color = if (isErr) RoseError else AmberWarn
          builder.addStyle(
            SpanStyle(
              background = color.copy(alpha = if (isErr) 0.18f else 0.14f),
              textDecoration = TextDecoration.Underline,
            ),
            rawStart,
            rawEnd,
          )
        }
      }
    }

    // Active bracket pair / mismatch highlight
    bracketMatch?.let { match ->
      if (match.isMismatch) {
        if (match.primaryOffset in code.indices) {
          builder.addStyle(
            SpanStyle(
              color = RoseError,
              background = RoseError.copy(alpha = 0.25f),
              fontWeight = FontWeight.Bold,
              textDecoration = TextDecoration.Underline,
            ),
            match.primaryOffset,
            match.primaryOffset + 1,
          )
        }
      } else {
        val style =
          SpanStyle(
            color = palette.activePillFg,
            background = palette.activePillBg.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
          )
        if (match.primaryOffset in code.indices) {
          builder.addStyle(style, match.primaryOffset, match.primaryOffset + 1)
        }
        val partner = match.partnerOffset
        if (partner != null && partner in code.indices) {
          builder.addStyle(style, partner, partner + 1)
        }
      }
    }

    return builder.toAnnotatedString()
  }

  private fun computeLineStarts(code: String): IntArray {
    val starts = ArrayList<Int>(64)
    starts.add(0)
    for (i in code.indices) {
      if (code[i] == '\n') {
        starts.add(i + 1)
      }
    }
    return starts.toIntArray()
  }

  fun highlight(code: String, language: WorkerLanguage, palette: IdePalette): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")
    if (language == WorkerLanguage.TEXT || code.length > MAX_HIGHLIGHT_CHARS) {
      return AnnotatedString(code)
    }

    val cacheKey = HighlightCacheKey(code.hashCode(), code.length, language, palette.isDark)
    synchronized(cacheLock) {
      val cached = highlightCache[cacheKey]
      if (cached != null && cached.rawText == code) {
        return cached.highlighted
      }
    }

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
          if (quote != '`' && code[j] == '\n') {
            // Stop unclosed single/double quote highlighting at end of line so rest of file stays readable
            break
          }
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

      i++
    }

    val result = builder.toAnnotatedString()
    synchronized(cacheLock) {
      highlightCache[cacheKey] = HighlightCacheEntry(code, result)
    }
    return result
  }

  private fun isFollowedByColon(code: String, startIndex: Int): Boolean {
    var k = startIndex
    while (k < code.length && code[k].isWhitespace()) k++
    return k < code.length && code[k] == ':'
  }
}
