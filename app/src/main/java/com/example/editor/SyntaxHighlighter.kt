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

import androidx.compose.ui.graphics.toArgb

data class CodeSyntaxVisualTransformation(
  val language: WorkerLanguage,
  val palette: IdePalette,
) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val highlighted = SyntaxHighlighter.highlight(text.text, language, palette)
    return TransformedText(highlighted, OffsetMapping.Identity)
  }
}

private class PaletteIntColors(palette: IdePalette) {
  val comment = palette.syntaxComment.toArgb()
  val property = palette.syntaxProperty.toArgb()
  val string = palette.syntaxString.toArgb()
  val number = palette.syntaxNumber.toArgb()
  val keyword = palette.syntaxKeyword.toArgb()
  val control = palette.syntaxControl.toArgb()
  val type = palette.syntaxType.toArgb()
  val function = palette.syntaxFunction.toArgb()
}

private class PaletteSpanStyles(palette: IdePalette) {
  val comment = SpanStyle(color = palette.syntaxComment, fontStyle = FontStyle.Italic)
  val propertyBold = SpanStyle(color = palette.syntaxProperty, fontWeight = FontWeight.Medium)
  val property = SpanStyle(color = palette.syntaxProperty)
  val string = SpanStyle(color = palette.syntaxString)
  val number = SpanStyle(color = palette.syntaxNumber)
  val keywordBold = SpanStyle(color = palette.syntaxKeyword, fontWeight = FontWeight.SemiBold)
  val keyword = SpanStyle(color = palette.syntaxKeyword, fontWeight = FontWeight.Medium)
  val control = SpanStyle(color = palette.syntaxControl, fontWeight = FontWeight.Medium)
  val type = SpanStyle(color = palette.syntaxType)
  val function = SpanStyle(color = palette.syntaxFunction)
  val operator = SpanStyle(color = palette.syntaxOperator)
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
      "NaN",
      "Infinity",
      "interface",
      "type",
      "enum",
      "implements",
      "private",
      "public",
      "protected",
      "readonly",
      "static",
      "abstract",
      "declare",
      "namespace",
      "override",
      "satisfies",
      "keyof",
      "infer",
      "is",
      "asserts",
      "get",
      "set",
      "string",
      "number",
      "boolean",
      "bigint",
      "symbol",
      "any",
      "unknown",
      "never",
      "object",
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
      "debugger",
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
      "SubtleCrypto",
      "CryptoKey",
      "TextEncoder",
      "TextDecoder",
      "ReadableStream",
      "WritableStream",
      "TransformStream",
      "WebSocket",
      "WebSocketPair",
      "HTMLRewriter",
      "ExportedHandler",
      "ExecutionContext",
      "ScheduledController",
      "KVNamespace",
      "D1Database",
      "D1Result",
      "R2Bucket",
      "R2Object",
      "DurableObject",
      "DurableObjectState",
      "DurableObjectNamespace",
      "Fetcher",
      "MessageBatch",
      "Queue",
      "Ai",
      "VectorizeIndex",
      "Hyperdrive",
      "WorkflowEntrypoint",
      "WorkflowEvent",
      "WorkflowStep",
      "AbortController",
      "AbortSignal",
      "FormData",
      "Blob",
      "File",
      "Uint8Array",
      "ArrayBuffer",
      "JSON",
      "Math",
      "Date",
      "Promise",
      "Map",
      "Set",
      "WeakMap",
      "WeakSet",
      "Array",
      "Object",
      "String",
      "Number",
      "Boolean",
      "RegExp",
      "Error",
      "TypeError",
      "Record",
      "Partial",
      "Required",
      "Pick",
      "Omit",
      "console",
      "env",
      "ctx",
      "cf",
      "atob",
      "btoa",
      "structuredClone",
      "setTimeout",
      "clearTimeout",
    )

  private data class HighlightCacheKey(
    val textHash: Int,
    val length: Int,
    val language: WorkerLanguage,
    val paletteHash: Int,
  )

  private data class HighlightCacheEntry(
    val rawText: String,
    val highlighted: AnnotatedString,
  )

  private val cacheLock = Any()
  private val stylesCache = HashMap<IdePalette, PaletteSpanStyles>(8)
  private val intColorsCache = HashMap<IdePalette, PaletteIntColors>(8)
  private val highlightCache =
    object : LinkedHashMap<HighlightCacheKey, HighlightCacheEntry>(32, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HighlightCacheKey, HighlightCacheEntry>?): Boolean {
        return size > 24
      }
    }

  private fun stylesFor(palette: IdePalette): PaletteSpanStyles {
    synchronized(cacheLock) {
      return stylesCache.getOrPut(palette) { PaletteSpanStyles(palette) }
    }
  }

  private fun intColorsFor(palette: IdePalette): PaletteIntColors {
    synchronized(cacheLock) {
      return intColorsCache.getOrPut(palette) { PaletteIntColors(palette) }
    }
  }

  private const val MAX_HIGHLIGHT_CHARS = 35_000

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

    if (actionableDiags.isNotEmpty()) {
      val lineStarts = CodeEditHelper.computeLineStarts(code)
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

  fun highlight(code: String, language: WorkerLanguage, palette: IdePalette): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")
    if (language == WorkerLanguage.TEXT || code.length > MAX_HIGHLIGHT_CHARS) {
      return AnnotatedString(code)
    }

    val cacheKey = HighlightCacheKey(code.hashCode(), code.length, language, palette.hashCode())
    synchronized(cacheLock) {
      val cached = highlightCache[cacheKey]
      if (cached != null && cached.rawText == code) {
        return cached.highlighted
      }
    }

    val styles = stylesFor(palette)
    val builder = AnnotatedString.Builder(code)
    highlightRange(code, 0, code.length, language, styles, builder)

    val result = builder.toAnnotatedString()
    synchronized(cacheLock) {
      highlightCache[cacheKey] = HighlightCacheEntry(code, result)
    }
    return result
  }

  /**
   * Highlights HTTP responses, API payloads, stack traces, and CLI logs cleanly based on MIME hint or content.
   */
  fun highlightResponse(
    text: String,
    contentTypeHint: String? = null,
    palette: IdePalette,
  ): AnnotatedString {
    if (text.isEmpty() || text.length > MAX_HIGHLIGHT_CHARS) return AnnotatedString(text)
    val hint = contentTypeHint.orEmpty().lowercase()
    val trimmed = text.trimStart()
    val detectedLang =
      when {
        hint.contains("json") || trimmed.startsWith("{") || trimmed.startsWith("[") -> WorkerLanguage.JSON
        hint.contains("html") ||
          trimmed.startsWith("<!doctype", ignoreCase = true) ||
          trimmed.startsWith("<html", ignoreCase = true) ||
          trimmed.startsWith("<head", ignoreCase = true) ||
          trimmed.startsWith("<body", ignoreCase = true) -> WorkerLanguage.HTML
        hint.contains("svg") || hint.contains("xml") || trimmed.startsWith("<?xml", ignoreCase = true) ||
          trimmed.startsWith("<svg", ignoreCase = true) -> WorkerLanguage.CONFIG
        hint.contains("css") -> WorkerLanguage.CSS
        hint.contains("markdown") -> WorkerLanguage.MARKDOWN
        hint.contains("javascript") || hint.contains("typescript") -> WorkerLanguage.JAVASCRIPT
        else -> null
      }
    if (detectedLang != null) {
      return highlight(text, detectedLang, palette)
    }

    val styles = stylesFor(palette)
    val builder = AnnotatedString.Builder(text)
    highlightTerminalOrLogText(text, styles, palette, builder)
    return builder.toAnnotatedString()
  }

  private fun highlightTerminalOrLogText(
    text: String,
    styles: PaletteSpanStyles,
    palette: IdePalette,
    builder: AnnotatedString.Builder,
  ) {
    val errStyle = SpanStyle(color = RoseError, fontWeight = FontWeight.SemiBold)
    val warnStyle = SpanStyle(color = AmberWarn, fontWeight = FontWeight.SemiBold)
    val len = text.length
    var i = 0
    while (i < len) {
      val c = text[i]
      // Quoted strings
      if (c == '"' || c == '\'') {
        val quote = c
        var j = i + 1
        while (j < len && text[j] != '\n') {
          if (text[j] == '\\' && j + 1 < len) {
            j += 2
            continue
          }
          if (text[j] == quote) {
            j++
            break
          }
          j++
        }
        builder.addStyle(styles.string, i, j)
        i = j
        continue
      }

      // URLs
      if ((c == 'h' || c == 'H') &&
        (text.regionMatches(i, "https://", 0, 8, ignoreCase = true) ||
          text.regionMatches(i, "http://", 0, 7, ignoreCase = true))
      ) {
        var j = i + 7
        while (j < len && !text[j].isWhitespace() && text[j] !in charArrayOf('"', '\'', ')', ']', '}')) j++
        builder.addStyle(styles.string, i, j)
        i = j
        continue
      }

      // Line-leading key: value
      if ((i == 0 || text[i - 1] == '\n') && (c.isLetter() || c == '_')) {
        var j = i + 1
        while (j < len && (text[j].isLetterOrDigit() || text[j] == '-' || text[j] == '_')) j++
        if (j < len && text[j] == ':' && (j + 1 >= len || text[j + 1].isWhitespace())) {
          builder.addStyle(styles.propertyBold, i, j)
          i = j + 1
          continue
        }
      }

      // Numbers / timestamps / HTTP codes
      if (c.isDigit() && (i == 0 || !text[i - 1].isLetterOrDigit())) {
        var j = i + 1
        while (j < len && (text[j].isDigit() || text[j] == '.' || text[j] == ':' || text[j] == '-')) j++
        val token = text.substring(i, j)
        val statusVal = if (token.length == 3 && token.all { it.isDigit() }) token.toIntOrNull() else null
        val numStyle =
          when {
            statusVal != null && statusVal in 500..599 -> errStyle
            statusVal != null && statusVal in 400..499 -> warnStyle
            else -> styles.number
          }
        builder.addStyle(numStyle, i, j)
        i = j
        continue
      }

      // Words
      if (c.isLetter() || c == '_') {
        var j = i + 1
        while (j < len && (text[j].isLetterOrDigit() || text[j] == '_')) j++
        val word = text.substring(i, j)
        val style =
          when {
            word.equals("ERROR", ignoreCase = true) ||
              word.equals("ERR", ignoreCase = true) ||
              word.equals("FAIL", ignoreCase = true) ||
              word.equals("FAILED", ignoreCase = true) ||
              word.endsWith("Error") ||
              word.endsWith("Exception") -> errStyle
            word.equals("WARN", ignoreCase = true) ||
              word.equals("WARNING", ignoreCase = true) ||
              word.equals("TIMEOUT", ignoreCase = true) -> warnStyle
            word == "true" || word == "false" || word == "null" -> styles.keywordBold
            word in setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "OK", "PASS", "PASSED", "LOG", "INFO") ->
              styles.control
            else -> null
          }
        if (style != null) builder.addStyle(style, i, j)
        i = j
        continue
      }
      i++
    }
  }

  private fun highlightRange(
    code: String,
    start: Int,
    endLimit: Int,
    language: WorkerLanguage,
    styles: PaletteSpanStyles,
    builder: AnnotatedString.Builder,
  ) {
    when (language) {
      WorkerLanguage.HTML -> {
        highlightMarkupRange(code, start, endLimit, styles, builder)
        return
      }
      WorkerLanguage.CSS -> {
        highlightCssRange(code, start, endLimit, styles, builder)
        return
      }
      WorkerLanguage.MARKDOWN -> {
        highlightMarkdownRange(code, start, endLimit, styles, builder)
        return
      }
      WorkerLanguage.CONFIG -> {
        highlightConfigRange(code, start, endLimit, styles, builder)
        return
      }
      else -> Unit
    }

    var i = start
    while (i < endLimit) {
      val c = code[i]

      // Line comment //
      if (c == '/' && i + 1 < endLimit && code[i + 1] == '/') {
        val end = code.indexOf('\n', i).let { if (it == -1 || it > endLimit) endLimit else it }
        builder.addStyle(styles.comment, i, end)
        i = end
        continue
      }

      // Block comment /* ... */
      if (c == '/' && i + 1 < endLimit && code[i + 1] == '*') {
        val end = code.indexOf("*/", i + 2).let { if (it == -1 || it + 2 > endLimit) endLimit else it + 2 }
        builder.addStyle(styles.comment, i, end)
        i = end
        continue
      }

      // Template literal `...${expr}...` with interpolation highlighting
      if (c == '`' && language != WorkerLanguage.JSON) {
        var segStart = i
        var j = i + 1
        while (j < endLimit) {
          if (code[j] == '\\' && j + 1 < endLimit) {
            j += 2
            continue
          }
          if (code[j] == '$' && j + 1 < endLimit && code[j + 1] == '{') {
            if (j > segStart) builder.addStyle(styles.string, segStart, j)
            builder.addStyle(styles.control, j, j + 2)
            val exprStart = j + 2
            var depth = 1
            var k = exprStart
            while (k < endLimit && depth > 0) {
              when (code[k]) {
                '\\' -> k += 2
                '{' -> {
                  depth++
                  k++
                }
                '}' -> {
                  depth--
                  if (depth > 0) k++
                }
                else -> k++
              }
            }
            if (k > exprStart) {
              highlightRange(code, exprStart, k, language, styles, builder)
            }
            if (k < endLimit && code[k] == '}') {
              builder.addStyle(styles.control, k, k + 1)
              j = k + 1
              segStart = j
              continue
            } else {
              j = k
              segStart = j
              break
            }
          }
          if (code[j] == '`') {
            j++
            break
          }
          j++
        }
        if (j > segStart) builder.addStyle(styles.string, segStart, j)
        i = j
        continue
      }

      // Strings ', "
      if (c == '"' || c == '\'') {
        val quote = c
        var j = i + 1
        while (j < endLimit) {
          if (code[j] == '\n') break
          if (code[j] == '\\' && j + 1 < endLimit) {
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
          language == WorkerLanguage.JSON && quote == '"' && isFollowedByColon(code, j, endLimit)
        builder.addStyle(if (isJsonKey) styles.propertyBold else styles.string, i, j)
        i = j
        continue
      }

      // Numbers (decimal, hex, binary, scientific, bigint suffix)
      if (c.isDigit() && (i == start || !code[i - 1].isLetterOrDigit())) {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) {
          j++
        }
        builder.addStyle(styles.number, i, j)
        i = j
        continue
      }

      // Arrow operator => and common operators
      if (c == '=' && i + 1 < endLimit && code[i + 1] == '>') {
        builder.addStyle(styles.control, i, i + 2)
        i += 2
        continue
      }

      // Identifiers, Keywords, Cloudflare Types & Functions
      if (c.isLetter() || c == '_' || c == '$') {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '$')) {
          j++
        }
        val word = code.substring(i, j)
        val style =
          when {
            language == WorkerLanguage.JSON && (word == "true" || word == "false" || word == "null") ->
              styles.keywordBold
            word in controlKeywords -> styles.control
            word in jsKeywords -> styles.keyword
            word in cloudflareGlobals -> styles.type
            j < endLimit && code[j] == '(' -> styles.function
            i > start && code[i - 1] == '.' -> styles.property
            language == WorkerLanguage.TYPESCRIPT &&
              word.length > 1 &&
              word[0].isUpperCase() &&
              word.any { it.isLowerCase() } -> styles.type
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
  }

  private fun highlightMarkupRange(
    code: String,
    start: Int,
    endLimit: Int,
    styles: PaletteSpanStyles,
    builder: AnnotatedString.Builder,
  ) {
    var i = start
    while (i < endLimit) {
      val c = code[i]
      if (c == '<') {
        if (code.startsWith("<!--", i)) {
          val end = code.indexOf("-->", i + 4).let { if (it == -1 || it + 3 > endLimit) endLimit else it + 3 }
          builder.addStyle(styles.comment, i, end)
          i = end
          continue
        }
        if (code.startsWith("<!", i) || code.startsWith("<?", i)) {
          val gt = code.indexOf('>', i + 2).let { if (it == -1 || it + 1 > endLimit) endLimit else it + 1 }
          builder.addStyle(styles.keyword, i, gt)
          i = gt
          continue
        }
        var j = i + 1
        if (j < endLimit && code[j] == '/') j++
        val nameStart = j
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == '_' || code[j] == ':')) j++
        if (j > nameStart) {
          builder.addStyle(styles.operator, i, nameStart)
          builder.addStyle(styles.control, nameStart, j)
        }
        while (j < endLimit && code[j] != '>') {
          val ac = code[j]
          when {
            ac == '"' || ac == '\'' -> {
              val sEnd = findQuoteEnd(code, j, endLimit, ac)
              builder.addStyle(styles.string, j, sEnd)
              j = sEnd
            }
            ac.isLetter() || ac == ':' || ac == '@' || ac == '_' -> {
              val aStart = j
              while (j < endLimit && (code[j].isLetterOrDigit() || code[j] in charArrayOf('-', '_', ':', '.'))) j++
              builder.addStyle(styles.property, aStart, j)
            }
            ac == '/' || ac == '=' -> {
              builder.addStyle(styles.operator, j, j + 1)
              j++
            }
            else -> j++
          }
        }
        if (j < endLimit && code[j] == '>') {
          builder.addStyle(styles.operator, j, j + 1)
          j++
        }
        i = j
        continue
      }
      i++
    }
  }

  private fun highlightCssRange(
    code: String,
    start: Int,
    endLimit: Int,
    styles: PaletteSpanStyles,
    builder: AnnotatedString.Builder,
  ) {
    var i = start
    while (i < endLimit) {
      val c = code[i]
      if (c == '/' && i + 1 < endLimit && code[i + 1] == '*') {
        val end = code.indexOf("*/", i + 2).let { if (it == -1 || it + 2 > endLimit) endLimit else it + 2 }
        builder.addStyle(styles.comment, i, end)
        i = end
        continue
      }
      if (c == '"' || c == '\'') {
        val end = findQuoteEnd(code, i, endLimit, c)
        builder.addStyle(styles.string, i, end)
        i = end
        continue
      }
      if (c == '@' || c == '!') {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '-')) j++
        if (j > i + 1) builder.addStyle(styles.control, i, j)
        i = j
        continue
      }
      if (c == '#') {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == '_')) j++
        val token = code.substring(i + 1, j)
        val isHex = token.length in setOf(3, 4, 6, 8) && token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        builder.addStyle(if (isHex) styles.number else styles.type, i, j)
        i = j
        continue
      }
      if (c.isDigit() && (i == start || !code[i - 1].isLetterOrDigit())) {
        var j = i + 1
        while (j < endLimit && (code[j].isDigit() || code[j] == '.' || code[j].isLetter() || code[j] == '%')) j++
        builder.addStyle(styles.number, i, j)
        i = j
        continue
      }
      if (c == '.' && i + 1 < endLimit && (code[i + 1].isLetter() || code[i + 1] == '_')) {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == '_')) j++
        builder.addStyle(styles.type, i, j)
        i = j
        continue
      }
      if (c.isLetter() || c == '-') {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == '_')) j++
        val style =
          when {
            j < endLimit && code[j] == '(' -> styles.function
            isFollowedByColon(code, j, endLimit) -> styles.property
            else -> null
          }
        if (style != null) builder.addStyle(style, i, j)
        i = j
        continue
      }
      i++
    }
  }

  private fun highlightMarkdownRange(
    code: String,
    start: Int,
    endLimit: Int,
    styles: PaletteSpanStyles,
    builder: AnnotatedString.Builder,
  ) {
    var i = start
    var inFence = false
    while (i < endLimit) {
      val lineEnd = code.indexOf('\n', i).let { if (it == -1 || it > endLimit) endLimit else it }
      val trimmedStart = skipSpaces(code, i, lineEnd)
      if (trimmedStart + 2 < lineEnd &&
        code[trimmedStart] == '`' &&
        code[trimmedStart + 1] == '`' &&
        code[trimmedStart + 2] == '`'
      ) {
        builder.addStyle(styles.control, i, lineEnd)
        inFence = !inFence
        i = if (lineEnd < endLimit) lineEnd + 1 else endLimit
        continue
      }
      if (inFence) {
        builder.addStyle(styles.string, i, lineEnd)
        i = if (lineEnd < endLimit) lineEnd + 1 else endLimit
        continue
      }
      if (trimmedStart < lineEnd) {
        val ch = code[trimmedStart]
        when {
          ch == '#' -> builder.addStyle(styles.keywordBold, trimmedStart, lineEnd)
          ch == '>' -> builder.addStyle(styles.comment, trimmedStart, lineEnd)
          (ch == '-' || ch == '*' || ch == '+') && trimmedStart + 1 < lineEnd && code[trimmedStart + 1] == ' ' -> {
            builder.addStyle(styles.control, trimmedStart, trimmedStart + 1)
            highlightMarkdownInline(code, trimmedStart + 2, lineEnd, styles, builder)
          }
          else -> highlightMarkdownInline(code, i, lineEnd, styles, builder)
        }
      }
      i = if (lineEnd < endLimit) lineEnd + 1 else endLimit
    }
  }

  private fun highlightMarkdownInline(
    code: String,
    start: Int,
    lineEnd: Int,
    styles: PaletteSpanStyles,
    builder: AnnotatedString.Builder,
  ) {
    var k = start
    while (k < lineEnd) {
      val c = code[k]
      if (c == '`') {
        val close = code.indexOf('`', k + 1).let { if (it in (k + 1) until lineEnd) it + 1 else -1 }
        if (close > 0) {
          builder.addStyle(styles.string, k, close)
          k = close
          continue
        }
      }
      if (c == '*' && k + 1 < lineEnd && code[k + 1] == '*') {
        val close = code.indexOf("**", k + 2).let { if (it in (k + 2) until lineEnd) it + 2 else -1 }
        if (close > 0) {
          builder.addStyle(styles.keywordBold, k, close)
          k = close
          continue
        }
      }
      if (c == '[' || (c == '!' && k + 1 < lineEnd && code[k + 1] == '[')) {
        val bracketStart = if (c == '!') k + 1 else k
        val closeBracket = code.indexOf(']', bracketStart + 1)
        if (closeBracket in (bracketStart + 1) until lineEnd &&
          closeBracket + 1 < lineEnd &&
          code[closeBracket + 1] == '('
        ) {
          val closeParen = code.indexOf(')', closeBracket + 2)
          if (closeParen in (closeBracket + 2) until lineEnd) {
            builder.addStyle(styles.propertyBold, k, closeBracket + 1)
            builder.addStyle(styles.string, closeBracket + 1, closeParen + 1)
            k = closeParen + 1
            continue
          }
        }
      }
      k++
    }
  }

  private fun highlightConfigRange(
    code: String,
    start: Int,
    endLimit: Int,
    styles: PaletteSpanStyles,
    builder: AnnotatedString.Builder,
  ) {
    val trimmed = code.substring(start, minOf(endLimit, start + 200)).trimStart()
    if (trimmed.startsWith("<")) {
      highlightMarkupRange(code, start, endLimit, styles, builder)
      return
    }
    var i = start
    while (i < endLimit) {
      val c = code[i]
      if (c == '#' || (c == '-' && i + 1 < endLimit && code[i + 1] == '-') ||
        (c == '/' && i + 1 < endLimit && code[i + 1] == '/')
      ) {
        val end = code.indexOf('\n', i).let { if (it == -1 || it > endLimit) endLimit else it }
        builder.addStyle(styles.comment, i, end)
        i = end
        continue
      }
      if (c == '[' && (i == start || code[i - 1] == '\n')) {
        val close = code.indexOf(']', i + 1)
        val nl = code.indexOf('\n', i + 1).let { if (it < 0) endLimit else it }
        if (close in (i + 1) until nl) {
          builder.addStyle(styles.control, i, close + 1)
          i = close + 1
          continue
        }
      }
      if (c == '"' || c == '\'') {
        val end = findQuoteEnd(code, i, endLimit, c)
        builder.addStyle(styles.string, i, end)
        i = end
        continue
      }
      if (c.isDigit() && (i == start || !code[i - 1].isLetterOrDigit())) {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '.')) j++
        builder.addStyle(styles.number, i, j)
        i = j
        continue
      }
      if (c.isLetter() || c == '_') {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '-')) j++
        val word = code.substring(i, j)
        val upper = word.uppercase()
        val style =
          when {
            upper in setOf("TRUE", "FALSE", "NULL", "YES", "NO", "ON", "OFF") -> styles.keywordBold
            upper in setOf("SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "INDEX", "ALTER", "DROP", "JOIN", "LEFT", "INNER", "ON", "AND", "OR", "NOT", "ORDER", "BY", "GROUP", "LIMIT", "EXPORT", "ECHO", "IF", "THEN", "ELSE", "FI", "FOR", "DO", "DONE", "CASE", "ESAC", "LOCAL", "RETURN") ->
              styles.control
            j < endLimit && (code[j] == '=' || isFollowedByColon(code, j, endLimit)) -> styles.propertyBold
            else -> null
          }
        if (style != null) builder.addStyle(style, i, j)
        i = j
        continue
      }
      i++
    }
  }

  private fun findQuoteEnd(code: CharSequence, start: Int, endLimit: Int, quote: Char): Int {
    var j = start + 1
    while (j < endLimit) {
      val ch = code[j]
      if (ch == '\n') break
      if (ch == '\\' && j + 1 < endLimit) {
        j += 2
        continue
      }
      if (ch == quote) {
        j++
        break
      }
      j++
    }
    return j
  }

  private fun skipSpaces(code: CharSequence, start: Int, limit: Int): Int {
    var k = start
    while (k < limit && (code[k] == ' ' || code[k] == '\t')) k++
    return k
  }

  private fun isFollowedByColon(code: CharSequence, startIndex: Int, endLimit: Int): Boolean {
    var k = startIndex
    while (k < endLimit && code[k].isWhitespace()) k++
    return k < endLimit && code[k] == ':'
  }

  /**
   * High-speed visible-window tokenizer operating directly on a CharSequence (such as Android Editable).
   * Scans from [scanStart] through [emitEnd] to keep multi-line comments/templates accurate,
   * while only emitting spans that intersect [emitStart, emitEnd].
   */
  fun tokenizeVisibleRange(
    code: CharSequence,
    scanStart: Int,
    emitStart: Int,
    emitEnd: Int,
    language: WorkerLanguage,
    palette: IdePalette,
    onToken: (start: Int, end: Int, colorArgb: Int) -> Unit,
  ) {
    if (language == WorkerLanguage.TEXT || code.isEmpty()) return
    val safeEnd = emitEnd.coerceIn(0, code.length)
    val safeEmitStart = emitStart.coerceIn(0, safeEnd)
    val safeScanStart = scanStart.coerceIn(0, safeEmitStart)
    if (safeScanStart >= safeEnd) return

    val colors = intColorsFor(palette)
    tokenizeCharRange(code, safeScanStart, safeEnd, safeEmitStart, safeEnd, language, colors, onToken)
  }

  private inline fun emitClipped(
    start: Int,
    end: Int,
    emitStart: Int,
    emitEnd: Int,
    color: Int,
    onToken: (Int, Int, Int) -> Unit,
  ) {
    val cs = if (start < emitStart) emitStart else start
    val ce = if (end > emitEnd) emitEnd else end
    if (cs < ce) onToken(cs, ce, color)
  }

  private fun tokenizeCharRange(
    code: CharSequence,
    start: Int,
    endLimit: Int,
    emitStart: Int,
    emitEnd: Int,
    language: WorkerLanguage,
    colors: PaletteIntColors,
    onToken: (start: Int, end: Int, colorArgb: Int) -> Unit,
  ) {
    if (language == WorkerLanguage.HTML) {
      tokenizeMarkupCharRange(code, start, endLimit, emitStart, emitEnd, colors, onToken)
      return
    }
    if (language == WorkerLanguage.CSS) {
      tokenizeCssCharRange(code, start, endLimit, emitStart, emitEnd, colors, onToken)
      return
    }
    if (language == WorkerLanguage.MARKDOWN) {
      tokenizeMarkdownCharRange(code, start, endLimit, emitStart, emitEnd, colors, onToken)
      return
    }
    if (language == WorkerLanguage.CONFIG) {
      tokenizeConfigCharRange(code, start, endLimit, emitStart, emitEnd, colors, onToken)
      return
    }

    var i = start
    while (i < endLimit) {
      val c = code[i]

      if (c == '/' && i + 1 < endLimit && code[i + 1] == '/') {
        var end = i + 2
        while (end < endLimit && code[end] != '\n') end++
        emitClipped(i, end, emitStart, emitEnd, colors.comment, onToken)
        i = end
        continue
      }

      if (c == '/' && i + 1 < endLimit && code[i + 1] == '*') {
        var end = i + 2
        while (end + 1 < endLimit && !(code[end] == '*' && code[end + 1] == '/')) end++
        end = if (end + 1 < endLimit) end + 2 else endLimit
        emitClipped(i, end, emitStart, emitEnd, colors.comment, onToken)
        i = end
        continue
      }

      if (c == '`' && language != WorkerLanguage.JSON) {
        var segStart = i
        var j = i + 1
        while (j < endLimit) {
          if (code[j] == '\\' && j + 1 < endLimit) {
            j += 2
            continue
          }
          if (code[j] == '$' && j + 1 < endLimit && code[j + 1] == '{') {
            if (j > segStart) emitClipped(segStart, j, emitStart, emitEnd, colors.string, onToken)
            emitClipped(j, j + 2, emitStart, emitEnd, colors.control, onToken)
            val exprStart = j + 2
            var depth = 1
            var k = exprStart
            while (k < endLimit && depth > 0) {
              when (code[k]) {
                '\\' -> k += 2
                '{' -> {
                  depth++
                  k++
                }
                '}' -> {
                  depth--
                  if (depth > 0) k++
                }
                else -> k++
              }
            }
            if (k > exprStart) {
              tokenizeCharRange(code, exprStart, k, emitStart, emitEnd, language, colors, onToken)
            }
            if (k < endLimit && code[k] == '}') {
              emitClipped(k, k + 1, emitStart, emitEnd, colors.control, onToken)
              j = k + 1
              segStart = j
              continue
            } else {
              j = k
              segStart = j
              break
            }
          }
          if (code[j] == '`') {
            j++
            break
          }
          j++
        }
        if (j > segStart) emitClipped(segStart, j, emitStart, emitEnd, colors.string, onToken)
        i = j
        continue
      }

      if (c == '"' || c == '\'') {
        val quote = c
        val j = findQuoteEnd(code, i, endLimit, quote)
        if (j > emitStart && i < emitEnd) {
          val isJsonKey =
            language == WorkerLanguage.JSON && quote == '"' && isFollowedByColon(code, j, endLimit)
          emitClipped(i, j, emitStart, emitEnd, if (isJsonKey) colors.property else colors.string, onToken)
        }
        i = j
        continue
      }

      if (c in '0'..'9' && (i == start || !code[i - 1].isLetterOrDigit())) {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
        emitClipped(i, j, emitStart, emitEnd, colors.number, onToken)
        i = j
        continue
      }

      if (c == '=' && i + 1 < endLimit && code[i + 1] == '>') {
        emitClipped(i, i + 2, emitStart, emitEnd, colors.control, onToken)
        i += 2
        continue
      }

      if (c.isLetter() || c == '_' || c == '$') {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '$')) j++
        if (j > emitStart && i < emitEnd) {
          val word = code.subSequence(i, j).toString()
          val color =
            when {
              language == WorkerLanguage.JSON && (word == "true" || word == "false" || word == "null") ->
                colors.keyword
              word in controlKeywords -> colors.control
              word in jsKeywords -> colors.keyword
              word in cloudflareGlobals -> colors.type
              j < endLimit && code[j] == '(' -> colors.function
              i > start && code[i - 1] == '.' -> colors.property
              language == WorkerLanguage.TYPESCRIPT &&
                word.length > 1 &&
                word[0].isUpperCase() &&
                word.any { it.isLowerCase() } -> colors.type
              else -> 0
            }
          if (color != 0) {
            emitClipped(i, j, emitStart, emitEnd, color, onToken)
          }
        }
        i = j
        continue
      }

      i++
    }
  }

  private fun tokenizeMarkupCharRange(
    code: CharSequence,
    start: Int,
    endLimit: Int,
    emitStart: Int,
    emitEnd: Int,
    colors: PaletteIntColors,
    onToken: (Int, Int, Int) -> Unit,
  ) {
    var i = start
    while (i < endLimit) {
      if (code[i] == '<') {
        if (i + 3 < endLimit && code[i + 1] == '!' && code[i + 2] == '-' && code[i + 3] == '-') {
          var end = i + 4
          while (end + 2 < endLimit && !(code[end] == '-' && code[end + 1] == '-' && code[end + 2] == '>')) end++
          end = if (end + 2 < endLimit) end + 3 else endLimit
          emitClipped(i, end, emitStart, emitEnd, colors.comment, onToken)
          i = end
          continue
        }
        if (i + 1 < endLimit && (code[i + 1] == '!' || code[i + 1] == '?')) {
          var end = i + 2
          while (end < endLimit && code[end] != '>') end++
          if (end < endLimit) end++
          emitClipped(i, end, emitStart, emitEnd, colors.keyword, onToken)
          i = end
          continue
        }
        var j = i + 1
        if (j < endLimit && code[j] == '/') j++
        val nameStart = j
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == '_' || code[j] == ':')) j++
        if (j > nameStart) {
          emitClipped(nameStart, j, emitStart, emitEnd, colors.control, onToken)
        }
        while (j < endLimit && code[j] != '>') {
          val ac = code[j]
          when {
            ac == '"' || ac == '\'' -> {
              val sEnd = findQuoteEnd(code, j, endLimit, ac)
              emitClipped(j, sEnd, emitStart, emitEnd, colors.string, onToken)
              j = sEnd
            }
            ac.isLetter() || ac == ':' || ac == '@' || ac == '_' -> {
              val aStart = j
              while (j < endLimit && (code[j].isLetterOrDigit() || code[j] in charArrayOf('-', '_', ':', '.'))) j++
              emitClipped(aStart, j, emitStart, emitEnd, colors.property, onToken)
            }
            else -> j++
          }
        }
        if (j < endLimit && code[j] == '>') j++
        i = j
        continue
      }
      i++
    }
  }

  private fun tokenizeCssCharRange(
    code: CharSequence,
    start: Int,
    endLimit: Int,
    emitStart: Int,
    emitEnd: Int,
    colors: PaletteIntColors,
    onToken: (Int, Int, Int) -> Unit,
  ) {
    var i = start
    while (i < endLimit) {
      val c = code[i]
      if (c == '/' && i + 1 < endLimit && code[i + 1] == '*') {
        var end = i + 2
        while (end + 1 < endLimit && !(code[end] == '*' && code[end + 1] == '/')) end++
        end = if (end + 1 < endLimit) end + 2 else endLimit
        emitClipped(i, end, emitStart, emitEnd, colors.comment, onToken)
        i = end
        continue
      }
      if (c == '"' || c == '\'') {
        val end = findQuoteEnd(code, i, endLimit, c)
        emitClipped(i, end, emitStart, emitEnd, colors.string, onToken)
        i = end
        continue
      }
      if (c == '@' || c == '!') {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '-')) j++
        if (j > i + 1) emitClipped(i, j, emitStart, emitEnd, colors.control, onToken)
        i = j
        continue
      }
      if (c == '#') {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == '_')) j++
        val token = code.subSequence(i + 1, j).toString()
        val isHex = token.length in setOf(3, 4, 6, 8) && token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        emitClipped(i, j, emitStart, emitEnd, if (isHex) colors.number else colors.type, onToken)
        i = j
        continue
      }
      if (c in '0'..'9' && (i == start || !code[i - 1].isLetterOrDigit())) {
        var j = i + 1
        while (j < endLimit && (code[j] in '0'..'9' || code[j] == '.' || code[j].isLetter() || code[j] == '%')) j++
        emitClipped(i, j, emitStart, emitEnd, colors.number, onToken)
        i = j
        continue
      }
      if (c == '.' && i + 1 < endLimit && (code[i + 1].isLetter() || code[i + 1] == '_')) {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == '_')) j++
        emitClipped(i, j, emitStart, emitEnd, colors.type, onToken)
        i = j
        continue
      }
      if (c.isLetter() || c == '-') {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == '_')) j++
        val color =
          when {
            j < endLimit && code[j] == '(' -> colors.function
            isFollowedByColon(code, j, endLimit) -> colors.property
            else -> 0
          }
        if (color != 0) emitClipped(i, j, emitStart, emitEnd, color, onToken)
        i = j
        continue
      }
      i++
    }
  }

  private fun tokenizeMarkdownCharRange(
    code: CharSequence,
    start: Int,
    endLimit: Int,
    emitStart: Int,
    emitEnd: Int,
    colors: PaletteIntColors,
    onToken: (Int, Int, Int) -> Unit,
  ) {
    var i = start
    var inFence = false
    while (i < endLimit) {
      var lineEnd = i
      while (lineEnd < endLimit && code[lineEnd] != '\n') lineEnd++
      val trimmedStart = skipSpaces(code, i, lineEnd)
      if (trimmedStart + 2 < lineEnd &&
        code[trimmedStart] == '`' &&
        code[trimmedStart + 1] == '`' &&
        code[trimmedStart + 2] == '`'
      ) {
        emitClipped(i, lineEnd, emitStart, emitEnd, colors.control, onToken)
        inFence = !inFence
        i = if (lineEnd < endLimit) lineEnd + 1 else endLimit
        continue
      }
      if (inFence) {
        emitClipped(i, lineEnd, emitStart, emitEnd, colors.string, onToken)
        i = if (lineEnd < endLimit) lineEnd + 1 else endLimit
        continue
      }
      if (trimmedStart < lineEnd) {
        val ch = code[trimmedStart]
        when {
          ch == '#' -> emitClipped(trimmedStart, lineEnd, emitStart, emitEnd, colors.keyword, onToken)
          ch == '>' -> emitClipped(trimmedStart, lineEnd, emitStart, emitEnd, colors.comment, onToken)
          (ch == '-' || ch == '*' || ch == '+') && trimmedStart + 1 < lineEnd && code[trimmedStart + 1] == ' ' -> {
            emitClipped(trimmedStart, trimmedStart + 1, emitStart, emitEnd, colors.control, onToken)
          }
        }
        var k = i
        while (k < lineEnd) {
          if (code[k] == '`') {
            var close = k + 1
            while (close < lineEnd && code[close] != '`') close++
            if (close < lineEnd) {
              emitClipped(k, close + 1, emitStart, emitEnd, colors.string, onToken)
              k = close + 1
              continue
            }
          }
          k++
        }
      }
      i = if (lineEnd < endLimit) lineEnd + 1 else endLimit
    }
  }

  private fun tokenizeConfigCharRange(
    code: CharSequence,
    start: Int,
    endLimit: Int,
    emitStart: Int,
    emitEnd: Int,
    colors: PaletteIntColors,
    onToken: (Int, Int, Int) -> Unit,
  ) {
    val headStart = skipSpaces(code, start, minOf(endLimit, start + 120))
    if (headStart < endLimit && code[headStart] == '<') {
      tokenizeMarkupCharRange(code, start, endLimit, emitStart, emitEnd, colors, onToken)
      return
    }
    var i = start
    while (i < endLimit) {
      val c = code[i]
      if (c == '#' || (c == '-' && i + 1 < endLimit && code[i + 1] == '-') ||
        (c == '/' && i + 1 < endLimit && code[i + 1] == '/')
      ) {
        var end = i + 1
        while (end < endLimit && code[end] != '\n') end++
        emitClipped(i, end, emitStart, emitEnd, colors.comment, onToken)
        i = end
        continue
      }
      if (c == '"' || c == '\'') {
        val end = findQuoteEnd(code, i, endLimit, c)
        emitClipped(i, end, emitStart, emitEnd, colors.string, onToken)
        i = end
        continue
      }
      if (c in '0'..'9' && (i == start || !code[i - 1].isLetterOrDigit())) {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '.')) j++
        emitClipped(i, j, emitStart, emitEnd, colors.number, onToken)
        i = j
        continue
      }
      if (c.isLetter() || c == '_') {
        var j = i + 1
        while (j < endLimit && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '-')) j++
        val word = code.subSequence(i, j).toString().uppercase()
        val color =
          when {
            word in setOf("TRUE", "FALSE", "NULL", "YES", "NO", "ON", "OFF") -> colors.keyword
            word in setOf("SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "INDEX", "ALTER", "DROP", "JOIN", "EXPORT", "ECHO", "IF", "THEN", "ELSE", "FI", "FOR", "DO", "DONE") ->
              colors.control
            j < endLimit && (code[j] == '=' || isFollowedByColon(code, j, endLimit)) -> colors.property
            else -> 0
          }
        if (color != 0) emitClipped(i, j, emitStart, emitEnd, color, onToken)
        i = j
        continue
      }
      i++
    }
  }
}
