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

data class CodeSyntaxVisualTransformation(
  val language: WorkerLanguage,
  val palette: IdePalette,
) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val highlighted = SyntaxHighlighter.highlight(text.text, language, palette)
    return TransformedText(highlighted, OffsetMapping.Identity)
  }
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

  private fun highlightRange(
    code: String,
    start: Int,
    endLimit: Int,
    language: WorkerLanguage,
    styles: PaletteSpanStyles,
    builder: AnnotatedString.Builder,
  ) {
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

  private fun isFollowedByColon(code: String, startIndex: Int, endLimit: Int): Boolean {
    var k = startIndex
    while (k < endLimit && code[k].isWhitespace()) k++
    return k < endLimit && code[k] == ':'
  }
}
