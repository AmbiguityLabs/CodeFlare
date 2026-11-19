package com.example.editor.engine

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.data.remote.WorkerLanguage
import com.example.ui.theme.IdePalette

enum class EdgeTokenKind {
  PLAIN,
  KEYWORD,
  CONTROL,
  STRING,
  NUMBER,
  COMMENT,
  FUNCTION,
  TYPE,
  PROPERTY,
  OPERATOR,
}

enum class LexState {
  CODE,
  BLOCK_COMMENT,
  TEMPLATE_STRING,
  HTML_COMMENT,
  MD_CODE_BLOCK,
}

/**
 * Compact per-line token run: [start, end) relative to line start.
 */
data class LineTokenRun(
  val start: Int,
  val end: Int,
  val kind: EdgeTokenKind,
)

class EdgeSyntaxTheme(palette: IdePalette) {
  private val styles: Array<SpanStyle?> =
    Array(EdgeTokenKind.entries.size) { kindIdx ->
      when (EdgeTokenKind.entries[kindIdx]) {
        EdgeTokenKind.PLAIN -> null
        EdgeTokenKind.KEYWORD -> SpanStyle(color = palette.syntaxKeyword, fontWeight = FontWeight.Medium)
        EdgeTokenKind.CONTROL -> SpanStyle(color = palette.syntaxControl, fontWeight = FontWeight.Medium)
        EdgeTokenKind.STRING -> SpanStyle(color = palette.syntaxString)
        EdgeTokenKind.NUMBER -> SpanStyle(color = palette.syntaxNumber)
        EdgeTokenKind.COMMENT -> SpanStyle(color = palette.syntaxComment, fontStyle = FontStyle.Italic)
        EdgeTokenKind.FUNCTION -> SpanStyle(color = palette.syntaxFunction)
        EdgeTokenKind.TYPE -> SpanStyle(color = palette.syntaxType)
        EdgeTokenKind.PROPERTY -> SpanStyle(color = palette.syntaxProperty)
        EdgeTokenKind.OPERATOR -> SpanStyle(color = palette.syntaxOperator)
      }
    }

  fun styleOf(kind: EdgeTokenKind): SpanStyle? = styles[kind.ordinal]
}

/**
 * CodeAssist-style lazy incremental per-line tokenizer.
 *
 * Key properties:
 * - Each line stores its exit [LexState] and cached [AnnotatedString.Range] list.
 * - Editing line `k` re-lexes line `k` and stops rippling downward the moment a subsequent line's
 *   exit [LexState] matches its previous exit state (for normal single-line edits, that's O(1) —
 *   literally 1 line!).
 * - Opening a large file lexes lazily up to the visible viewport instead of blocking on frame 1.
 */
class IncrementalLineStyles(
  private var language: WorkerLanguage,
  private var palette: IdePalette,
) {
  private var theme = EdgeSyntaxTheme(palette)
  private var lineCount = 0
  private var exitStates = Array(0) { LexState.CODE }
  private var lineTokens = arrayOfNulls<List<LineTokenRun>>(0)
  private var lineRevs = IntArray(0)
  private var tokenizedThrough = -1
  var revision: Int = 1
    private set

  fun reconfigure(language: WorkerLanguage, palette: IdePalette): Boolean {
    if (this.language == language && this.palette == palette) return false
    val langChanged = this.language != language
    this.language = language
    this.palette = palette
    this.theme = EdgeSyntaxTheme(palette)
    if (langChanged) {
      reset(lineCount)
    } else {
      // Theme-only change: keep token runs, bump revision so LineRenderCache re-paints colors
      revision++
      for (i in lineRevs.indices) lineRevs[i] = revision
    }
    return true
  }

  fun reset(newLineCount: Int) {
    val count = newLineCount.coerceAtLeast(1)
    lineCount = count
    exitStates = Array(count) { LexState.CODE }
    lineTokens = arrayOfNulls(count)
    lineRevs = IntArray(count)
    tokenizedThrough = -1
    revision++
  }

  /**
   * Incrementally splice [doc] after an edit covering `[editStartLine..editOldEndLine]` with
   * [lineDelta] lines added/removed. Re-lexes edited lines and ripples ONLY while exit state changes.
   */
  fun splice(doc: EditorDocument, editStartLine: Int, editOldEndLine: Int, lineDelta: Int) {
    val newCount = doc.lineCount
    if (lineCount == 0 || lineTokens.size != (newCount - lineDelta).coerceAtLeast(0)) {
      reset(newCount)
      return
    }
    val safeStart = editStartLine.coerceIn(0, (newCount - 1).coerceAtLeast(0))
    val nextStates = Array(newCount) { LexState.CODE }
    val nextTokens = arrayOfNulls<List<LineTokenRun>>(newCount)
    val nextRevs = IntArray(newCount)

    // Copy prefix before edited line
    for (i in 0 until safeStart) {
      nextStates[i] = exitStates[i]
      nextTokens[i] = lineTokens[i]
      nextRevs[i] = lineRevs[i]
    }

    // Copy suffix after edited range
    val oldTailStart = (editOldEndLine + 1).coerceAtLeast(safeStart)
    val newTailStart = (oldTailStart + lineDelta).coerceIn(safeStart, newCount)
    val tailLen = minOf(exitStates.size - oldTailStart, newCount - newTailStart).coerceAtLeast(0)
    for (i in 0 until tailLen) {
      nextStates[newTailStart + i] = exitStates[oldTailStart + i]
      nextTokens[newTailStart + i] = lineTokens[oldTailStart + i]
      nextRevs[newTailStart + i] = lineRevs[oldTailStart + i]
    }

    lineCount = newCount
    exitStates = nextStates
    lineTokens = nextTokens
    lineRevs = nextRevs
    val oldTokenizedThrough = tokenizedThrough
    tokenizedThrough = minOf(tokenizedThrough, safeStart - 1)
    revision++

    // Ensure state up to editStartLine, then re-lex edited region + ripple until state stabilizes
    val newEditEnd = (editOldEndLine + lineDelta).coerceIn(safeStart, newCount - 1)
    val previouslyTokenizedLimit = (oldTokenizedThrough + lineDelta).coerceIn(newEditEnd, newCount - 1)
    if (safeStart > 0 && tokenizedThrough < safeStart - 1) {
      ensureUpTo(doc, safeStart - 1)
    }

    var state = if (safeStart > 0) exitStates[safeStart - 1] else LexState.CODE
    var line = safeStart
    while (line <= newEditEnd && line < newCount) {
      val (tokens, exit) = lexLine(doc.lineText(line), state, language)
      lineTokens[line] = tokens
      exitStates[line] = exit
      lineRevs[line] = revision
      state = exit
      line++
    }

    // Ripple past edited range ONLY while exit state differs from cached state
    while (line <= previouslyTokenizedLimit && line < newCount) {
      val prevExit = exitStates[line]
      val prevCached = lineTokens[line] != null
      val (tokens, exit) = lexLine(doc.lineText(line), state, language)
      lineTokens[line] = tokens
      exitStates[line] = exit
      lineRevs[line] = revision
      state = exit
      line++
      if (prevCached && exit == prevExit) break
    }
    tokenizedThrough = maxOf(tokenizedThrough, line - 1)
  }

  fun ensureUpTo(doc: EditorDocument, targetLine: Int) {
    if (doc.lineCount != lineCount) reset(doc.lineCount)
    val stop = targetLine.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
    if (tokenizedThrough >= stop) return
    var state = if (tokenizedThrough >= 0) exitStates[tokenizedThrough] else LexState.CODE
    var line = tokenizedThrough + 1
    while (line <= stop) {
      val (tokens, exit) = lexLine(doc.lineText(line), state, language)
      lineTokens[line] = tokens
      exitStates[line] = exit
      lineRevs[line] = revision
      state = exit
      line++
    }
    tokenizedThrough = stop
  }

  fun lineRevision(line: Int): Int = if (line in lineRevs.indices) lineRevs[line] else revision

  fun spansForLine(doc: EditorDocument, line: Int): List<AnnotatedString.Range<SpanStyle>> {
    if (language == WorkerLanguage.TEXT || line !in 0 until doc.lineCount) return emptyList()
    ensureUpTo(doc, line)
    val runs = lineTokens.getOrNull(line) ?: return emptyList()
    if (runs.isEmpty()) return emptyList()
    val lineLen = doc.lineLength(line)
    val out = ArrayList<AnnotatedString.Range<SpanStyle>>(runs.size)
    for (r in runs) {
      val style = theme.styleOf(r.kind) ?: continue
      val s = r.start.coerceIn(0, lineLen)
      val e = r.end.coerceIn(s, lineLen)
      if (s < e) out.add(AnnotatedString.Range(style, s, e))
    }
    return out
  }

  fun rawTokensForLine(doc: EditorDocument, line: Int): List<LineTokenRun> {
    if (line !in 0 until doc.lineCount) return emptyList()
    ensureUpTo(doc, line)
    return lineTokens.getOrNull(line) ?: emptyList()
  }

  companion object {
    private val JS_KEYWORDS =
      setOf(
        "const", "let", "var", "function", "class", "extends", "new", "this", "super",
        "typeof", "instanceof", "in", "of", "void", "delete", "true", "false", "null",
        "undefined", "NaN", "Infinity", "interface", "type", "enum", "implements",
        "private", "public", "protected", "readonly", "static", "abstract", "declare",
        "namespace", "override", "satisfies", "keyof", "infer", "is", "asserts",
        "get", "set", "string", "number", "boolean", "bigint", "symbol", "any",
        "unknown", "never", "object",
      )

    private val CONTROL_KEYWORDS =
      setOf(
        "export", "import", "from", "default", "as", "async", "await", "return",
        "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
        "try", "catch", "finally", "throw", "yield", "debugger",
      )

    private val CLOUDFLARE_TYPES =
      setOf(
        "Request", "Response", "Headers", "URL", "URLSearchParams", "fetch", "caches",
        "crypto", "SubtleCrypto", "CryptoKey", "TextEncoder", "TextDecoder",
        "ReadableStream", "WritableStream", "TransformStream", "WebSocket", "WebSocketPair",
        "HTMLRewriter", "ExportedHandler", "ExecutionContext", "ScheduledController",
        "KVNamespace", "D1Database", "D1Result", "R2Bucket", "R2Object", "DurableObject",
        "DurableObjectState", "DurableObjectNamespace", "Fetcher", "MessageBatch",
        "Queue", "Ai", "VectorizeIndex", "Hyperdrive", "WorkflowEntrypoint",
        "WorkflowEvent", "WorkflowStep", "AbortController", "AbortSignal", "FormData",
        "Blob", "File", "Uint8Array", "ArrayBuffer", "JSON", "Math", "Date", "Promise",
        "Map", "Set", "WeakMap", "WeakSet", "Array", "Object", "String", "Number",
        "Boolean", "RegExp", "Error", "TypeError", "Record", "Partial", "Required",
        "Pick", "Omit", "console", "env", "ctx", "cf", "atob", "btoa", "structuredClone",
        "setTimeout", "clearTimeout",
      )

    fun lexLine(
      line: String,
      entryState: LexState,
      language: WorkerLanguage,
    ): Pair<List<LineTokenRun>, LexState> {
      if (language == WorkerLanguage.TEXT) return emptyList<LineTokenRun>() to entryState
      if (language == WorkerLanguage.HTML) return lexMarkupLine(line, entryState)
      if (language == WorkerLanguage.CSS) return lexCssLine(line, entryState)
      if (language == WorkerLanguage.MARKDOWN) return lexMarkdownLine(line, entryState)
      if (language == WorkerLanguage.CONFIG) return lexConfigLine(line, entryState)
      if (line.isEmpty()) return emptyList<LineTokenRun>() to entryState
      val runs = ArrayList<LineTokenRun>(12)
      var state = entryState
      var i = 0
      val len = line.length

      while (i < len) {
        when (state) {
          LexState.BLOCK_COMMENT -> {
            val closeIdx = line.indexOf("*/", i)
            if (closeIdx < 0) {
              runs.add(LineTokenRun(i, len, EdgeTokenKind.COMMENT))
              return runs to LexState.BLOCK_COMMENT
            } else {
              runs.add(LineTokenRun(i, closeIdx + 2, EdgeTokenKind.COMMENT))
              i = closeIdx + 2
              state = LexState.CODE
            }
          }

          LexState.TEMPLATE_STRING -> {
            val segStart = i
            var j = i
            var exited = false
            while (j < len) {
              val ch = line[j]
              if (ch == '\\' && j + 1 < len) {
                j += 2
                continue
              }
              if (ch == '$' && j + 1 < len && line[j + 1] == '{') {
                if (j > segStart) runs.add(LineTokenRun(segStart, j, EdgeTokenKind.STRING))
                runs.add(LineTokenRun(j, j + 2, EdgeTokenKind.CONTROL))
                val exprStart = j + 2
                var depth = 1
                var k = exprStart
                while (k < len && depth > 0) {
                  when (line[k]) {
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
                  val (innerRuns, _) = lexLine(line.substring(exprStart, k), LexState.CODE, language)
                  for (ir in innerRuns) {
                    runs.add(LineTokenRun(exprStart + ir.start, exprStart + ir.end, ir.kind))
                  }
                }
                if (k < len && line[k] == '}') {
                  runs.add(LineTokenRun(k, k + 1, EdgeTokenKind.CONTROL))
                  i = k + 1
                  exited = true
                  break
                } else {
                  return runs to LexState.TEMPLATE_STRING
                }
              }
              if (ch == '`') {
                runs.add(LineTokenRun(segStart, j + 1, EdgeTokenKind.STRING))
                i = j + 1
                state = LexState.CODE
                exited = true
                break
              }
              j++
            }
            if (!exited) {
              if (len > segStart) runs.add(LineTokenRun(segStart, len, EdgeTokenKind.STRING))
              return runs to LexState.TEMPLATE_STRING
            }
          }

          LexState.CODE -> {
            val c = line[i]
            val next = if (i + 1 < len) line[i + 1] else '\u0000'

            if (c == '/' && next == '/') {
              runs.add(LineTokenRun(i, len, EdgeTokenKind.COMMENT))
              return runs to LexState.CODE
            }

            if (c == '/' && next == '*') {
              val closeIdx = line.indexOf("*/", i + 2)
              if (closeIdx < 0) {
                runs.add(LineTokenRun(i, len, EdgeTokenKind.COMMENT))
                return runs to LexState.BLOCK_COMMENT
              } else {
                runs.add(LineTokenRun(i, closeIdx + 2, EdgeTokenKind.COMMENT))
                i = closeIdx + 2
                continue
              }
            }

            if (c == '`' && language != WorkerLanguage.JSON) {
              runs.add(LineTokenRun(i, i + 1, EdgeTokenKind.STRING))
              i++
              state = LexState.TEMPLATE_STRING
              continue
            }

            if (c == '"' || c == '\'') {
              var j = i + 1
              while (j < len) {
                val ch = line[j]
                if (ch == '\\' && j + 1 < len) {
                  j += 2
                  continue
                }
                if (ch == c) {
                  j++
                  break
                }
                j++
              }
              val isJsonKey =
                language == WorkerLanguage.JSON && c == '"' && isFollowedByColon(line, j)
              runs.add(
                LineTokenRun(
                  i,
                  j,
                  if (isJsonKey) EdgeTokenKind.PROPERTY else EdgeTokenKind.STRING,
                ),
              )
              i = j
              continue
            }

            if (c in '0'..'9' && (i == 0 || !line[i - 1].isLetterOrDigit())) {
              var j = i + 1
              while (j < len && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) j++
              runs.add(LineTokenRun(i, j, EdgeTokenKind.NUMBER))
              i = j
              continue
            }

            if (c == '=' && next == '>') {
              runs.add(LineTokenRun(i, i + 2, EdgeTokenKind.CONTROL))
              i += 2
              continue
            }

            if (c.isLetter() || c == '_' || c == '$') {
              var j = i + 1
              while (j < len && (line[j].isLetterOrDigit() || line[j] == '_' || line[j] == '$')) j++
              val word = line.substring(i, j)
              val kind =
                when {
                  language == WorkerLanguage.JSON && (word == "true" || word == "false" || word == "null") ->
                    EdgeTokenKind.KEYWORD
                  word in CONTROL_KEYWORDS -> EdgeTokenKind.CONTROL
                  word in JS_KEYWORDS -> EdgeTokenKind.KEYWORD
                  word in CLOUDFLARE_TYPES -> EdgeTokenKind.TYPE
                  j < len && line[j] == '(' -> EdgeTokenKind.FUNCTION
                  i > 0 && line[i - 1] == '.' -> EdgeTokenKind.PROPERTY
                  language == WorkerLanguage.TYPESCRIPT &&
                    word.length > 1 &&
                    word[0].isUpperCase() &&
                    word.any { it.isLowerCase() } -> EdgeTokenKind.TYPE
                  else -> EdgeTokenKind.PLAIN
                }
              if (kind != EdgeTokenKind.PLAIN) {
                runs.add(LineTokenRun(i, j, kind))
              }
              i = j
              continue
            }

            i++
          }
          else -> {
            state = LexState.CODE
          }
        }
      }
      return runs to state
    }

    private fun lexMarkupLine(
      line: String,
      entryState: LexState,
    ): Pair<List<LineTokenRun>, LexState> {
      if (line.isEmpty()) return emptyList<LineTokenRun>() to entryState
      val runs = ArrayList<LineTokenRun>(12)
      var state = entryState
      var i = 0
      val len = line.length
      while (i < len) {
        if (state == LexState.HTML_COMMENT) {
          val close = line.indexOf("-->", i)
          if (close < 0) {
            runs.add(LineTokenRun(i, len, EdgeTokenKind.COMMENT))
            return runs to LexState.HTML_COMMENT
          }
          runs.add(LineTokenRun(i, close + 3, EdgeTokenKind.COMMENT))
          i = close + 3
          state = LexState.CODE
          continue
        }
        val c = line[i]
        if (c == '<') {
          if (line.startsWith("<!--", i)) {
            val close = line.indexOf("-->", i + 4)
            if (close < 0) {
              runs.add(LineTokenRun(i, len, EdgeTokenKind.COMMENT))
              return runs to LexState.HTML_COMMENT
            }
            runs.add(LineTokenRun(i, close + 3, EdgeTokenKind.COMMENT))
            i = close + 3
            continue
          }
          if (i + 1 < len && (line[i + 1] == '!' || line[i + 1] == '?')) {
            val gt = line.indexOf('>', i + 2).let { if (it < 0) len else it + 1 }
            runs.add(LineTokenRun(i, gt, EdgeTokenKind.KEYWORD))
            i = gt
            continue
          }
          var j = i + 1
          if (j < len && line[j] == '/') j++
          val nameStart = j
          while (j < len && (line[j].isLetterOrDigit() || line[j] == '-' || line[j] == '_' || line[j] == ':')) j++
          if (j > nameStart) {
            runs.add(LineTokenRun(i, nameStart, EdgeTokenKind.OPERATOR))
            runs.add(LineTokenRun(nameStart, j, EdgeTokenKind.CONTROL))
          }
          while (j < len && line[j] != '>') {
            val ac = line[j]
            when {
              ac == '"' || ac == '\'' -> {
                var sEnd = j + 1
                while (sEnd < len) {
                  if (line[sEnd] == '\\' && sEnd + 1 < len) sEnd += 2
                  else if (line[sEnd] == ac) {
                    sEnd++
                    break
                  } else sEnd++
                }
                runs.add(LineTokenRun(j, sEnd, EdgeTokenKind.STRING))
                j = sEnd
              }
              ac.isLetter() || ac == ':' || ac == '@' || ac == '_' -> {
                val aStart = j
                while (j < len && (line[j].isLetterOrDigit() || line[j] in charArrayOf('-', '_', ':', '.'))) j++
                runs.add(LineTokenRun(aStart, j, EdgeTokenKind.PROPERTY))
              }
              ac == '/' || ac == '=' -> {
                runs.add(LineTokenRun(j, j + 1, EdgeTokenKind.OPERATOR))
                j++
              }
              else -> j++
            }
          }
          if (j < len && line[j] == '>') {
            runs.add(LineTokenRun(j, j + 1, EdgeTokenKind.OPERATOR))
            j++
          }
          i = j
          continue
        }
        i++
      }
      return runs to state
    }

    private fun lexCssLine(
      line: String,
      entryState: LexState,
    ): Pair<List<LineTokenRun>, LexState> {
      if (line.isEmpty()) return emptyList<LineTokenRun>() to entryState
      val runs = ArrayList<LineTokenRun>(12)
      var state = entryState
      var i = 0
      val len = line.length
      while (i < len) {
        if (state == LexState.BLOCK_COMMENT) {
          val close = line.indexOf("*/", i)
          if (close < 0) {
            runs.add(LineTokenRun(i, len, EdgeTokenKind.COMMENT))
            return runs to LexState.BLOCK_COMMENT
          }
          runs.add(LineTokenRun(i, close + 2, EdgeTokenKind.COMMENT))
          i = close + 2
          state = LexState.CODE
          continue
        }
        val c = line[i]
        val next = if (i + 1 < len) line[i + 1] else '\u0000'
        if (c == '/' && next == '*') {
          val close = line.indexOf("*/", i + 2)
          if (close < 0) {
            runs.add(LineTokenRun(i, len, EdgeTokenKind.COMMENT))
            return runs to LexState.BLOCK_COMMENT
          }
          runs.add(LineTokenRun(i, close + 2, EdgeTokenKind.COMMENT))
          i = close + 2
          continue
        }
        if (c == '"' || c == '\'') {
          var j = i + 1
          while (j < len) {
            if (line[j] == '\\' && j + 1 < len) j += 2
            else if (line[j] == c) {
              j++
              break
            } else j++
          }
          runs.add(LineTokenRun(i, j, EdgeTokenKind.STRING))
          i = j
          continue
        }
        if (c == '@' || c == '!') {
          var j = i + 1
          while (j < len && (line[j].isLetterOrDigit() || line[j] == '-')) j++
          if (j > i + 1) runs.add(LineTokenRun(i, j, EdgeTokenKind.CONTROL))
          i = j
          continue
        }
        if (c == '#') {
          var j = i + 1
          while (j < len && (line[j].isLetterOrDigit() || line[j] == '-' || line[j] == '_')) j++
          val token = line.substring(i + 1, j)
          val isHex = token.length in setOf(3, 4, 6, 8) && token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
          runs.add(LineTokenRun(i, j, if (isHex) EdgeTokenKind.NUMBER else EdgeTokenKind.TYPE))
          i = j
          continue
        }
        if (c in '0'..'9' && (i == 0 || !line[i - 1].isLetterOrDigit())) {
          var j = i + 1
          while (j < len && (line[j] in '0'..'9' || line[j] == '.' || line[j].isLetter() || line[j] == '%')) j++
          runs.add(LineTokenRun(i, j, EdgeTokenKind.NUMBER))
          i = j
          continue
        }
        if (c == '.' && i + 1 < len && (line[i + 1].isLetter() || line[i + 1] == '_')) {
          var j = i + 1
          while (j < len && (line[j].isLetterOrDigit() || line[j] == '-' || line[j] == '_')) j++
          runs.add(LineTokenRun(i, j, EdgeTokenKind.TYPE))
          i = j
          continue
        }
        if (c.isLetter() || c == '-') {
          var j = i + 1
          while (j < len && (line[j].isLetterOrDigit() || line[j] == '-' || line[j] == '_')) j++
          val kind =
            when {
              j < len && line[j] == '(' -> EdgeTokenKind.FUNCTION
              isFollowedByColon(line, j) -> EdgeTokenKind.PROPERTY
              else -> EdgeTokenKind.PLAIN
            }
          if (kind != EdgeTokenKind.PLAIN) runs.add(LineTokenRun(i, j, kind))
          i = j
          continue
        }
        i++
      }
      return runs to state
    }

    private fun lexMarkdownLine(
      line: String,
      entryState: LexState,
    ): Pair<List<LineTokenRun>, LexState> {
      val trimmed = line.trimStart()
      if (trimmed.startsWith("```")) {
        val nextState = if (entryState == LexState.MD_CODE_BLOCK) LexState.CODE else LexState.MD_CODE_BLOCK
        return listOf(LineTokenRun(0, line.length, EdgeTokenKind.CONTROL)) to nextState
      }
      if (entryState == LexState.MD_CODE_BLOCK) {
        return (if (line.isNotEmpty()) listOf(LineTokenRun(0, line.length, EdgeTokenKind.STRING)) else emptyList()) to LexState.MD_CODE_BLOCK
      }
      if (line.isEmpty()) return emptyList<LineTokenRun>() to LexState.CODE
      val runs = ArrayList<LineTokenRun>(8)
      val lead = line.length - trimmed.length
      when {
        trimmed.startsWith("#") -> runs.add(LineTokenRun(lead, line.length, EdgeTokenKind.KEYWORD))
        trimmed.startsWith(">") -> runs.add(LineTokenRun(lead, line.length, EdgeTokenKind.COMMENT))
        (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) ->
          runs.add(LineTokenRun(lead, lead + 1, EdgeTokenKind.CONTROL))
      }
      if (!trimmed.startsWith("#")) {
        var i = 0
        while (i < line.length) {
          if (line[i] == '`') {
            val close = line.indexOf('`', i + 1)
            if (close > i) {
              runs.add(LineTokenRun(i, close + 1, EdgeTokenKind.STRING))
              i = close + 1
              continue
            }
          } else if (line[i] == '*' && i + 1 < line.length && line[i + 1] == '*') {
            val close = line.indexOf("**", i + 2)
            if (close > i + 1) {
              runs.add(LineTokenRun(i, close + 2, EdgeTokenKind.KEYWORD))
              i = close + 2
              continue
            }
          } else if (line[i] == '[' || (line[i] == '!' && i + 1 < line.length && line[i + 1] == '[')) {
            val bStart = if (line[i] == '!') i + 1 else i
            val closeB = line.indexOf(']', bStart + 1)
            if (closeB > bStart && closeB + 1 < line.length && line[closeB + 1] == '(') {
              val closeP = line.indexOf(')', closeB + 2)
              if (closeP > closeB + 1) {
                runs.add(LineTokenRun(i, closeB + 1, EdgeTokenKind.PROPERTY))
                runs.add(LineTokenRun(closeB + 1, closeP + 1, EdgeTokenKind.STRING))
                i = closeP + 1
                continue
              }
            }
          }
          i++
        }
      }
      return runs to LexState.CODE
    }

    private fun lexConfigLine(
      line: String,
      entryState: LexState,
    ): Pair<List<LineTokenRun>, LexState> {
      val trimmed = line.trimStart()
      if (entryState == LexState.HTML_COMMENT || trimmed.startsWith("<")) {
        return lexMarkupLine(line, entryState)
      }
      if (line.isEmpty()) return emptyList<LineTokenRun>() to LexState.CODE
      val runs = ArrayList<LineTokenRun>(10)
      var i = 0
      val len = line.length
      while (i < len) {
        val c = line[i]
        val next = if (i + 1 < len) line[i + 1] else '\u0000'
        if (c == '#' || (c == '-' && next == '-') || (c == '/' && next == '/')) {
          runs.add(LineTokenRun(i, len, EdgeTokenKind.COMMENT))
          return runs to LexState.CODE
        }
        if (i == 0 && c == '[') {
          val close = line.indexOf(']', 1)
          if (close > 0) {
            runs.add(LineTokenRun(0, close + 1, EdgeTokenKind.CONTROL))
            i = close + 1
            continue
          }
        }
        if (c == '"' || c == '\'') {
          var j = i + 1
          while (j < len) {
            if (line[j] == '\\' && j + 1 < len) j += 2
            else if (line[j] == c) {
              j++
              break
            } else j++
          }
          runs.add(LineTokenRun(i, j, EdgeTokenKind.STRING))
          i = j
          continue
        }
        if (c in '0'..'9' && (i == 0 || !line[i - 1].isLetterOrDigit())) {
          var j = i + 1
          while (j < len && (line[j].isLetterOrDigit() || line[j] == '.')) j++
          runs.add(LineTokenRun(i, j, EdgeTokenKind.NUMBER))
          i = j
          continue
        }
        if (c.isLetter() || c == '_') {
          var j = i + 1
          while (j < len && (line[j].isLetterOrDigit() || line[j] == '_' || line[j] == '-')) j++
          val word = line.substring(i, j).uppercase()
          val kind =
            when {
              word in setOf("TRUE", "FALSE", "NULL", "YES", "NO", "ON", "OFF") -> EdgeTokenKind.KEYWORD
              word in setOf("SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "INDEX", "ALTER", "DROP", "JOIN", "EXPORT", "ECHO", "IF", "THEN", "ELSE", "FI", "FOR", "DO", "DONE") ->
                EdgeTokenKind.CONTROL
              j < len && (line[j] == '=' || isFollowedByColon(line, j)) -> EdgeTokenKind.PROPERTY
              else -> EdgeTokenKind.PLAIN
            }
          if (kind != EdgeTokenKind.PLAIN) runs.add(LineTokenRun(i, j, kind))
          i = j
          continue
        }
        i++
      }
      return runs to LexState.CODE
    }

    private fun isFollowedByColon(line: String, startIndex: Int): Boolean {
      var k = startIndex
      while (k < line.length && line[k].isWhitespace()) k++
      return k < line.length && line[k] == ':'
    }
  }
}
