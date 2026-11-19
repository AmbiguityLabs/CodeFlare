package com.example.editor

import com.example.data.remote.DiagnosticItem
import com.example.data.remote.DiagnosticSeverity
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerLanguage
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class BracketMatch(
  val primaryOffset: Int,
  val partnerOffset: Int?,
  val isMismatch: Boolean = false,
)

object CodeAnalyzer {
  fun analyzeFile(file: WorkerFile): List<DiagnosticItem> {
    if (file.isVirtualReadOnly) return emptyList()
    return when (file.language) {
      WorkerLanguage.JSON -> analyzeJson(file)
      WorkerLanguage.JAVASCRIPT,
      WorkerLanguage.TYPESCRIPT -> analyzeJsWorker(file)
      WorkerLanguage.HTML,
      WorkerLanguage.CSS,
      WorkerLanguage.MARKDOWN,
      WorkerLanguage.CONFIG,
      WorkerLanguage.TEXT -> emptyList()
    }.sortedWith(compareBy<DiagnosticItem>({ it.severity.ordinal }, { it.line }, { it.column }))
  }

  private fun offsetToLineCol(code: String, targetOffset: Int): Pair<Int, Int> {
    val clamped = targetOffset.coerceIn(0, code.length)
    var line = 1
    var col = 1
    for (idx in 0 until clamped) {
      if (code[idx] == '\n') {
        line++
        col = 1
      } else {
        col++
      }
    }
    return line to col
  }

  private fun analyzeJson(file: WorkerFile): List<DiagnosticItem> {
    val code = file.content
    if (code.trim().isEmpty()) return emptyList()

    val isJsonc = file.path.endsWith(".jsonc", ignoreCase = true)
    val diagnostics = mutableListOf<DiagnosticItem>()

    // Scan JSON/JSONC preserving line/column coordinates into a strict-JSON buffer
    val sanitized = StringBuilder(code.length)
    var inString = false
    var escaped = false
    var line = 1
    var col = 1
    var strStartLine = 1
    var strStartCol = 1
    var i = 0
    val len = code.length

    while (i < len) {
      val c = code[i]
      val next = if (i + 1 < len) code[i + 1] else '\u0000'

      if (inString) {
        if (c == '\n') {
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = strStartLine,
              column = strStartCol,
              message = "Unterminated string literal in JSON",
              severity = DiagnosticSeverity.ERROR,
              length = 1,
            ),
          )
          inString = false
          escaped = false
          sanitized.append('\n')
          line++
          col = 1
          i++
          continue
        }
        sanitized.append(c)
        if (escaped) {
          escaped = false
        } else if (c == '\\') {
          escaped = true
        } else if (c == '"') {
          inString = false
        }
        i++
        col++
        continue
      }

      if (c == '"') {
        inString = true
        strStartLine = line
        strStartCol = col
        sanitized.append(c)
        i++
        col++
        continue
      }

      if (c == '/' && next == '/') {
        if (!isJsonc) {
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = line,
              column = col,
              message = "Comments are not allowed in strict JSON (use .jsonc)",
              severity = DiagnosticSeverity.ERROR,
              length = 2,
            ),
          )
        }
        sanitized.append("  ")
        i += 2
        col += 2
        while (i < len && code[i] != '\n') {
          sanitized.append(' ')
          i++
          col++
        }
        continue
      }

      if (c == '/' && next == '*') {
        val openLine = line
        val openCol = col
        if (!isJsonc) {
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = openLine,
              column = openCol,
              message = "Block comments are not allowed in strict JSON (use .jsonc)",
              severity = DiagnosticSeverity.ERROR,
              length = 2,
            ),
          )
        }
        sanitized.append("  ")
        i += 2
        col += 2
        var closed = false
        while (i < len) {
          val ch = code[i]
          if (ch == '*' && i + 1 < len && code[i + 1] == '/') {
            sanitized.append("  ")
            i += 2
            col += 2
            closed = true
            break
          }
          if (ch == '\n') {
            sanitized.append('\n')
            line++
            col = 1
          } else {
            sanitized.append(' ')
            col++
          }
          i++
        }
        if (!closed) {
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = openLine,
              column = openCol,
              message = "Unclosed block comment '/*'",
              severity = DiagnosticSeverity.ERROR,
              length = 2,
            ),
          )
        }
        continue
      }

      if (c == '\n') {
        sanitized.append('\n')
        line++
        col = 1
        i++
        continue
      }

      sanitized.append(c)
      i++
      col++
    }

    if (inString) {
      diagnostics.add(
        DiagnosticItem(
          filePath = file.path,
          line = strStartLine,
          column = strStartCol,
          message = "Unterminated string literal in JSON",
          severity = DiagnosticSeverity.ERROR,
          length = 1,
        ),
      )
    }

    // Handle trailing commas outside strings
    val cleanedChars = sanitized.toString().toCharArray()
    var j = 0
    var inStr2 = false
    var esc2 = false
    while (j < cleanedChars.size) {
      val ch = cleanedChars[j]
      if (inStr2) {
        if (esc2) esc2 = false
        else if (ch == '\\') esc2 = true
        else if (ch == '"') inStr2 = false
        j++
        continue
      }
      if (ch == '"') {
        inStr2 = true
        j++
        continue
      }
      if (ch == ',') {
        var k = j + 1
        while (k < cleanedChars.size && cleanedChars[k].isWhitespace()) k++
        if (k < cleanedChars.size && (cleanedChars[k] == '}' || cleanedChars[k] == ']')) {
          if (!isJsonc) {
            val (tLine, tCol) = offsetToLineCol(code, j)
            diagnostics.add(
              DiagnosticItem(
                filePath = file.path,
                line = tLine,
                column = tCol,
                message = "Trailing comma is not valid in strict JSON",
                severity = DiagnosticSeverity.ERROR,
                length = 1,
              ),
            )
          }
          cleanedChars[j] = ' '
        }
      }
      j++
    }

    if (diagnostics.isNotEmpty()) {
      return diagnostics.distinctBy { Triple(it.line, it.column, it.message) }
    }

    val strictText = String(cleanedChars)
    val firstNonWs = strictText.indexOfFirst { !it.isWhitespace() }
    if (firstNonWs < 0) return emptyList()

    try {
      val tokener = JSONTokener(strictText)
      val root = tokener.nextValue()
      if (root !is JSONObject && root !is JSONArray) {
        val (rLine, rCol) = offsetToLineCol(code, firstNonWs)
        diagnostics.add(
          DiagnosticItem(
            filePath = file.path,
            line = rLine,
            column = rCol,
            message = "JSON root must be an object {...} or array [...]",
            severity = DiagnosticSeverity.ERROR,
            length = 1,
          ),
        )
      } else {
        // Check for unexpected trailing non-whitespace tokens after root JSON value
        var trailingIdx = -1
        val nextCh = tokener.nextClean()
        if (nextCh != '\u0000') {
          // Estimate location using remaining text
          val suffix = strictText.substring(firstNonWs)
          val approx = strictText.length - suffix.trimEnd().length
          trailingIdx = (approx - 1).coerceIn(0, code.length - 1)
          val (eLine, eCol) = offsetToLineCol(code, trailingIdx)
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = eLine,
              column = eCol,
              message = "Unexpected trailing character '$nextCh' after JSON root",
              severity = DiagnosticSeverity.ERROR,
              length = 1,
            ),
          )
        }
      }
    } catch (e: Exception) {
      val msg = e.message ?: "Invalid JSON syntax"
      val charMatch = Regex("""at character (\d+)""").find(msg)
      val errOffset = charMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, code.length)
      val (errLine, errCol) =
        if (errOffset != null) offsetToLineCol(code, (errOffset - 1).coerceAtLeast(0))
        else 1 to 1
      val cleanMsg = msg.replace(Regex("""\s+at character \d+ of .*"""), "").trim().ifBlank { "Invalid JSON syntax" }
      diagnostics.add(
        DiagnosticItem(
          filePath = file.path,
          line = errLine,
          column = errCol,
          message = cleanMsg,
          severity = DiagnosticSeverity.ERROR,
          length = 1,
        ),
      )
    }

    return diagnostics
  }

  private enum class LexContext {
    CODE,
    TEMPLATE_TEXT,
  }

  private fun isRegexStart(code: String, slashIdx: Int): Boolean {
    var k = slashIdx - 1
    while (k >= 0 && code[k].isWhitespace()) {
      if (code[k] == '\n') return true
      k--
    }
    if (k < 0) return true
    val prev = code[k]
    if (prev in "([,=;:!&|?{}~^+-*%<>") return true
    if (prev.isLetterOrDigit() || prev == '_' || prev == '$') {
      var start = k
      while (start > 0 && (code[start - 1].isLetterOrDigit() || code[start - 1] == '_' || code[start - 1] == '$')) {
        start--
      }
      val word = code.substring(start, k + 1)
      return word in setOf("return", "throw", "case", "typeof", "void", "delete", "await", "yield", "in", "of", "new", "else")
    }
    return false
  }

  private fun analyzeJsWorker(file: WorkerFile): List<DiagnosticItem> {
    val diagnostics = mutableListOf<DiagnosticItem>()
    val code = file.content
    val len = code.length

    data class OpenBracket(
      val ch: Char,
      val line: Int,
      val col: Int,
      val offset: Int,
      val isTemplateExpr: Boolean = false,
    )

    val stack = ArrayDeque<OpenBracket>()
    val templateStack = ArrayDeque<Pair<Int, Int>>() // (line, col) of opening `
    val sanitized = StringBuilder(len) // Code with strings/comments/regex replaced by spaces (newlines kept)

    var line = 1
    var col = 1
    var i = 0
    var context = LexContext.CODE

    fun appendMasked(ch: Char) {
      sanitized.append(if (ch == '\n') '\n' else ' ')
    }

    while (i < len) {
      val c = code[i]

      if (context == LexContext.TEMPLATE_TEXT) {
        if (c == '\\' && i + 1 < len) {
          appendMasked(c)
          i++
          col++
          val esc = code[i]
          appendMasked(esc)
          if (esc == '\n') {
            line++
            col = 1
          } else {
            col++
          }
          i++
          continue
        }
        if (c == '$' && i + 1 < len && code[i + 1] == '{') {
          stack.addLast(OpenBracket('{', line, col, i + 1, isTemplateExpr = true))
          sanitized.append("  ")
          i += 2
          col += 2
          context = LexContext.CODE
          continue
        }
        if (c == '`') {
          if (templateStack.isNotEmpty()) templateStack.removeLast()
          appendMasked(c)
          i++
          col++
          context = LexContext.CODE
          continue
        }
        appendMasked(c)
        if (c == '\n') {
          line++
          col = 1
        } else {
          col++
        }
        i++
        continue
      }

      if (c == '\n') {
        sanitized.append('\n')
        line++
        col = 1
        i++
        continue
      }

      // Line comment //
      if (c == '/' && i + 1 < len && code[i + 1] == '/') {
        sanitized.append("  ")
        i += 2
        col += 2
        while (i < len && code[i] != '\n') {
          sanitized.append(' ')
          i++
          col++
        }
        continue
      }

      // Block comment /* ... */
      if (c == '/' && i + 1 < len && code[i + 1] == '*') {
        val openLine = line
        val openCol = col
        sanitized.append("  ")
        i += 2
        col += 2
        var closed = false
        while (i < len) {
          val ch = code[i]
          if (ch == '*' && i + 1 < len && code[i + 1] == '/') {
            sanitized.append("  ")
            i += 2
            col += 2
            closed = true
            break
          }
          appendMasked(ch)
          if (ch == '\n') {
            line++
            col = 1
          } else {
            col++
          }
          i++
        }
        if (!closed) {
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = openLine,
              column = openCol,
              message = "Unclosed block comment '/*'",
              severity = DiagnosticSeverity.ERROR,
              length = 2,
            ),
          )
        }
        continue
      }

      // Regex literal /pattern/flags
      if (c == '/' && isRegexStart(code, i)) {
        val regexStartLine = line
        val regexStartCol = col
        appendMasked(c)
        i++
        col++
        var inCharClass = false
        var closed = false
        while (i < len) {
          val ch = code[i]
          if (ch == '\n') {
            break
          }
          appendMasked(ch)
          if (ch == '\\' && i + 1 < len && code[i + 1] != '\n') {
            i++
            col++
            appendMasked(code[i])
            i++
            col++
            continue
          }
          if (ch == '[') inCharClass = true
          else if (ch == ']' && inCharClass) inCharClass = false
          else if (ch == '/' && !inCharClass) {
            i++
            col++
            while (i < len && code[i].isLetter()) {
              appendMasked(code[i])
              i++
              col++
            }
            closed = true
            break
          }
          i++
          col++
        }
        if (!closed) {
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = regexStartLine,
              column = regexStartCol,
              message = "Unterminated regular expression literal",
              severity = DiagnosticSeverity.ERROR,
              length = 1,
            ),
          )
        }
        continue
      }

      // Template literal `
      if (c == '`') {
        templateStack.addLast(line to col)
        appendMasked(c)
        i++
        col++
        context = LexContext.TEMPLATE_TEXT
        continue
      }

      // Single or double-quoted strings
      if (c == '"' || c == '\'') {
        val q = c
        val strLine = line
        val strCol = col
        appendMasked(c)
        i++
        col++
        var closed = false
        while (i < len) {
          val ch = code[i]
          if (ch == '\n') {
            break
          }
          appendMasked(ch)
          if (ch == '\\' && i + 1 < len) {
            i++
            col++
            val esc = code[i]
            appendMasked(esc)
            if (esc == '\n') {
              line++
              col = 1
            } else {
              col++
            }
            i++
            continue
          }
          if (ch == q) {
            i++
            col++
            closed = true
            break
          }
          i++
          col++
        }
        if (!closed) {
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = strLine,
              column = strCol,
              message = "Unterminated string literal ($q)",
              severity = DiagnosticSeverity.ERROR,
              length = 1,
            ),
          )
        }
        continue
      }

      sanitized.append(c)
      when (c) {
        '{', '(', '[' -> stack.addLast(OpenBracket(c, line, col, i))
        '}', ')', ']' -> {
          val expectedOpen =
            when (c) {
              '}' -> '{'
              ')' -> '('
              else -> '['
            }
          val top = stack.lastOrNull()
          if (top == null) {
            diagnostics.add(
              DiagnosticItem(
                filePath = file.path,
                line = line,
                column = col,
                message = "Unexpected closing '$c'",
                severity = DiagnosticSeverity.ERROR,
                length = 1,
              ),
            )
          } else if (top.ch != expectedOpen) {
            val expectedClose =
              when (top.ch) {
                '{' -> '}'
                '(' -> ')'
                else -> ']'
              }
            diagnostics.add(
              DiagnosticItem(
                filePath = file.path,
                line = line,
                column = col,
                message = "Unexpected '$c' — expected '$expectedClose' for '${top.ch}' at Ln ${top.line}",
                severity = DiagnosticSeverity.ERROR,
                length = 1,
              ),
            )
          } else {
            val popped = stack.removeLast()
            if (popped.isTemplateExpr && c == '}') {
              context = LexContext.TEMPLATE_TEXT
            }
          }
        }
      }

      i++
      col++
    }

    templateStack.lastOrNull()?.let { (tLine, tCol) ->
      diagnostics.add(
        DiagnosticItem(
          filePath = file.path,
          line = tLine,
          column = tCol,
          message = "Unterminated template literal '`'",
          severity = DiagnosticSeverity.ERROR,
          length = 1,
        ),
      )
    }

    // Report unclosed brackets (up to 3 deepest/earliest so users see root causes)
    if (stack.isNotEmpty()) {
      stack.takeLast(3).reversed().forEach { unclosed ->
        diagnostics.add(
          DiagnosticItem(
            filePath = file.path,
            line = unclosed.line,
            column = unclosed.col,
            message = "Unclosed '${unclosed.ch}' (opened at Ln ${unclosed.line}, Col ${unclosed.col})",
            severity = DiagnosticSeverity.ERROR,
            length = 1,
          ),
        )
      }
    }

    // Statement / token-level syntax catching on comment/string-stripped code
    val maskedCode = sanitized.toString()
    analyzeMaskedJsSyntax(file, code, maskedCode, diagnostics)

    // Cloudflare Workers runtime hints
    maskedCode.lines().forEachIndexed { idx, lineText ->
      val lineNum = idx + 1
      val procIdx = lineText.indexOf("process.env")
      if (procIdx >= 0) {
        diagnostics.add(
          DiagnosticItem(
            filePath = file.path,
            line = lineNum,
            column = procIdx + 1,
            message = "Workers isolate has no Node process.env — use env.VAR_NAME parameter",
            severity = DiagnosticSeverity.WARNING,
            length = "process.env".length,
          ),
        )
      }
      Regex("""\b(window|document)\.""").find(lineText)?.let { m ->
        diagnostics.add(
          DiagnosticItem(
            filePath = file.path,
            line = lineNum,
            column = m.range.first + 1,
            message = "DOM globals (window/document) are unavailable in Cloudflare Workers",
            severity = DiagnosticSeverity.WARNING,
            length = m.value.length - 1,
          ),
        )
      }
      Regex("""\brequire\s*\(""").find(lineText)?.let { m ->
        diagnostics.add(
          DiagnosticItem(
            filePath = file.path,
            line = lineNum,
            column = m.range.first + 1,
            message = "CommonJS require() is discouraged in ESM Workers — prefer ES import",
            severity = DiagnosticSeverity.WARNING,
            length = "require".length,
          ),
        )
      }
    }

    if (
      file.isEntryPoint &&
        !maskedCode.contains("export default") &&
        !maskedCode.contains("addEventListener")
    ) {
      diagnostics.add(
        DiagnosticItem(
          filePath = file.path,
          line = 1,
          column = 1,
          message = "Entry module should export default { async fetch(request, env, ctx) { ... } }",
          severity = DiagnosticSeverity.INFO,
          length = 1,
        ),
      )
    }

    return diagnostics.distinctBy { Triple(it.line, it.column, it.message) }
  }

  private fun analyzeMaskedJsSyntax(
    file: WorkerFile,
    rawCode: String,
    maskedCode: String,
    diagnostics: MutableList<DiagnosticItem>,
  ) {
    // 1. Duplicate export default
    val exportDefaultMatches = Regex("""\bexport\s+default\b""").findAll(maskedCode).toList()
    if (exportDefaultMatches.size > 1) {
      exportDefaultMatches.drop(1).forEach { match ->
        val (l, c) = offsetToLineCol(maskedCode, match.range.first)
        diagnostics.add(
          DiagnosticItem(
            filePath = file.path,
            line = l,
            column = c,
            message = "Duplicate 'export default' in module",
            severity = DiagnosticSeverity.ERROR,
            length = match.value.length,
          ),
        )
      }
    }

    // 2. Empty condition parentheses in if(), while(), switch()
    Regex("""\b(if|while|switch)\s*\(\s*\)""").findAll(maskedCode).forEach { match ->
      val (l, c) = offsetToLineCol(maskedCode, match.range.first)
      diagnostics.add(
        DiagnosticItem(
          filePath = file.path,
          line = l,
          column = c,
          message = "Expected expression inside '${match.groupValues[1]} ()'",
          severity = DiagnosticSeverity.ERROR,
          length = match.value.length,
        ),
      )
    }

    // 3. Malformed operator sequences outside strings/comments
    Regex("""(===\s*=|!==\s*=|=>\s*=>)""").findAll(maskedCode).forEach { match ->
      val (l, c) = offsetToLineCol(maskedCode, match.range.first)
      diagnostics.add(
        DiagnosticItem(
          filePath = file.path,
          line = l,
          column = c,
          message = "Unexpected operator sequence '${match.value.replace(Regex("\\s+"), " ")}'",
          severity = DiagnosticSeverity.ERROR,
          length = match.value.length,
        ),
      )
    }

    // 4. Uninitialized const declarations & missing import specifier strings
    val maskedLines = maskedCode.lines()
    val rawLines = rawCode.lines()
    val importFromPattern = Regex("""^\s*import\b.*\bfrom\b\s*;?\s*$""")
    maskedLines.forEachIndexed { idx, lineText ->
      val lineNum = idx + 1
      val trimmed = lineText.trim()
      if (trimmed.startsWith("const ") && !trimmed.startsWith("declare ")) {
        val simpleConst = Regex("""^(?:export\s+)?const\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*(?::[^=;]+)?;\s*$""").find(trimmed)
        if (simpleConst != null) {
          val colIdx = lineText.indexOf("const") + 1
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = lineNum,
              column = colIdx,
              message = "'const ${simpleConst.groupValues[1]}' declarations must be initialized",
              severity = DiagnosticSeverity.ERROR,
              length = "const".length,
            ),
          )
        }
      }
      // Malformed import ... from ; (missing specifier string) — verify against unmasked raw source!
      val fromMatch = if (importFromPattern.containsMatchIn(lineText)) Regex("""\bfrom\b""").find(lineText) else null
      if (fromMatch != null) {
        val rawLine = rawLines.getOrElse(idx) { "" }
        val afterFromRaw =
          if (fromMatch.range.last + 1 <= rawLine.length) rawLine.substring(fromMatch.range.last + 1).trimStart()
          else ""
        val hasSameLineString = afterFromRaw.startsWith("\"") || afterFromRaw.startsWith("'")
        val hasNextLineString =
          if (!hasSameLineString && afterFromRaw.isEmpty()) {
            val nextNonBlank = rawLines.drop(idx + 1).firstOrNull { it.isNotBlank() }?.trimStart().orEmpty()
            nextNonBlank.startsWith("\"") || nextNonBlank.startsWith("'")
          } else {
            false
          }
        if (!hasSameLineString && !hasNextLineString) {
          val colIdx = fromMatch.range.first + 1
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = lineNum,
              column = colIdx.coerceAtLeast(1),
              message = "Expected module specifier string after 'from'",
              severity = DiagnosticSeverity.ERROR,
              length = 4,
            ),
          )
        }
      }
    }

    // 5. Detect 'await' inside non-async traditional function declarations/expressions
    val nonAsyncFnPattern = Regex("""\bfunction\s+[A-Za-z_$][A-Za-z0-9_$]*\s*\([^)]*\)\s*\{""")
    nonAsyncFnPattern.findAll(maskedCode).forEach { fnMatch ->
      val prefixStart = (fnMatch.range.first - 16).coerceAtLeast(0)
      val prefix = maskedCode.substring(prefixStart, fnMatch.range.first)
      if (!Regex("""\basync\s+$""").containsMatchIn(prefix)) {
        val openBraceIdx = fnMatch.range.last
        var depth = 1
        var p = openBraceIdx + 1
        while (p < maskedCode.length && depth > 0) {
          when (maskedCode[p]) {
            '{' -> depth++
            '}' -> depth--
          }
          p++
        }
        if (depth == 0) {
          val body = maskedCode.substring(openBraceIdx + 1, p - 1)
          // Only flag if there is a top-level await in this function body (not nested inside an inner async function/arrow)
          if (!body.contains("async ") && !body.contains("function")) {
            val awaitMatch = Regex("""\bawait\b""").find(body)
            if (awaitMatch != null) {
              val awaitOffset = openBraceIdx + 1 + awaitMatch.range.first
              val (l, c) = offsetToLineCol(maskedCode, awaitOffset)
              diagnostics.add(
                DiagnosticItem(
                  filePath = file.path,
                  line = l,
                  column = c,
                  message = "'await' is only valid inside an 'async' function",
                  severity = DiagnosticSeverity.ERROR,
                  length = 5,
                ),
              )
            }
          }
        }
      }
    }
  }

  /**
   * Finds the matching bracket partner for a bracket adjacent to [cursorOffset], ignoring strings & comments.
   */
  fun findMatchingBracket(
    code: String,
    cursorOffset: Int,
    language: WorkerLanguage,
  ): BracketMatch? {
    if (code.isEmpty() || language == WorkerLanguage.TEXT || code.length > 30_000) return null
    val candidateOffsets = listOf(cursorOffset, cursorOffset - 1)
    val targetOffset =
      candidateOffsets.firstOrNull { off ->
        off in code.indices && code[off] in charArrayOf('{', '}', '(', ')', '[', ']')
      } ?: return null

    // Build bracket pairs while skipping strings and comments
    data class TokenBracket(val ch: Char, val offset: Int)
    val stack = ArrayDeque<TokenBracket>()
    val pairs = HashMap<Int, Int>()
    val unmatched = HashSet<Int>()

    var i = 0
    val len = code.length
    while (i < len) {
      val c = code[i]
      if (c == '/' && i + 1 < len && code[i + 1] == '/') {
        while (i < len && code[i] != '\n') i++
        continue
      }
      if (c == '/' && i + 1 < len && code[i + 1] == '*') {
        i += 2
        while (i + 1 < len && !(code[i] == '*' && code[i + 1] == '/')) i++
        i = minOf(len, i + 2)
        continue
      }
      if (c == '"' || c == '\'' || c == '`') {
        val q = c
        i++
        while (i < len) {
          if (code[i] == '\\' && i + 1 < len) {
            i += 2
            continue
          }
          if (code[i] == q) {
            i++
            break
          }
          i++
        }
        continue
      }

      when (c) {
        '{', '(', '[' -> stack.addLast(TokenBracket(c, i))
        '}', ')', ']' -> {
          val expected =
            when (c) {
              '}' -> '{'
              ')' -> '('
              else -> '['
            }
          val top = stack.lastOrNull()
          if (top != null && top.ch == expected) {
            stack.removeLast()
            pairs[top.offset] = i
            pairs[i] = top.offset
          } else {
            unmatched.add(i)
          }
        }
      }
      i++
    }
    stack.forEach { unmatched.add(it.offset) }

    if (targetOffset in unmatched) {
      return BracketMatch(primaryOffset = targetOffset, partnerOffset = null, isMismatch = true)
    }
    val partner = pairs[targetOffset] ?: return null
    return BracketMatch(primaryOffset = targetOffset, partnerOffset = partner, isMismatch = false)
  }
}
