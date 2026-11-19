package com.example.editor.selection

/**
 * Modular domain strategies for [SmartTextSelectionEngine]:
 * - [CodeStructureStrategy]: JS/TS/JSX identifiers, member chains, strings, templates, calls, statements, blocks.
 * - [JsonStructureStrategy]: JSON/JSONC values, `"key": value` pairs, arrays, objects.
 * - [MarkupStructureStrategy]: HTML/XML/JSX attributes, tags, matched elements, CSS declarations/rules.
 * - [TerminalSemanticStrategy]: URLs, stack-trace `file:line:col` paths, UUID/IP/email/tokens,
 *   inline JSON payloads, HTTP headers, CLI flags & commands.
 */
internal object SharedSemanticDetectors {
  private val URL_PREFIXES = listOf("https://", "http://", "wss://", "ws://")
  private val UUID_REGEX = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
  private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
  private val IPV4_PORT_REGEX = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?\b""")
  private val FILE_PATH_REGEX =
    Regex("""(?:\.{0,2}/)?(?:[A-Za-z0-9_.\-@]+/)+[A-Za-z0-9_.\-@]+(?::\d+(?::\d+)?)?|[A-Za-z0-9_\-]+\.(?:ts|mts|js|mjs|jsx|tsx|json|jsonc|kt|java|html|css|toml|yml|yaml|md|sql|sh|py|rs|go)(?::\d+(?::\d+)?)?""")
  private val OPAQUE_TOKEN_REGEX = Regex("""[A-Za-z0-9_\-]{12,}(?:\.[A-Za-z0-9_\-]{2,}){0,4}""")
  private val CLI_FLAG_REGEX = Regex("""--?[A-Za-z0-9][A-Za-z0-9_\-]*(?:=(?:"[^"]*"|'[^']*'|[^\s,;)>\]]+))?""")

  fun isIdentChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'

  fun findTokenAt(text: CharSequence, anchor: Int, allowHyphen: Boolean = false): SelectionScope? {
    if (text.isEmpty()) return null
    val pos = anchor.coerceIn(0, text.length)
    val isWord: (Char) -> Boolean = { ch ->
      isIdentChar(ch) || (allowHyphen && ch == '-')
    }
    val probe =
      when {
        pos < text.length && isWord(text[pos]) -> pos
        pos > 0 && isWord(text[pos - 1]) -> pos - 1
        else -> return null
      }
    var s = probe
    while (s > 0 && isWord(text[s - 1])) s--
    var e = probe + 1
    while (e < text.length && isWord(text[e])) e++
    if (e <= s) return null
    val label = if (text[s].isDigit()) "Number" else "Word"
    return SelectionScope(s, e, SelectionScopeKind.TOKEN, label)
  }

  fun findUrlAt(text: CharSequence, anchor: Int): SelectionScope? {
    if (text.isEmpty()) return null
    val lineStart = StructuralScanner.findLineStart(text, anchor)
    val lineEnd = StructuralScanner.findLineEnd(text, anchor)
    val line = text.subSequence(lineStart, lineEnd).toString()
    val relAnchor = (anchor - lineStart).coerceIn(0, line.length)

    var searchFrom = 0
    while (searchFrom < line.length) {
      var bestIdx = -1
      for (prefix in URL_PREFIXES) {
        val idx = line.indexOf(prefix, searchFrom)
        if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) bestIdx = idx
      }
      if (bestIdx < 0) break

      var end = bestIdx
      while (end < line.length && !line[end].isWhitespace() && line[end] !in "\"'`<>") {
        end++
      }
      // Trim trailing punctuation cleanly while preserving balanced parentheses in URLs
      end = trimUrlEnd(line, bestIdx, end)
      if (end > bestIdx && relAnchor in bestIdx..end) {
        return SelectionScope(lineStart + bestIdx, lineStart + end, SelectionScopeKind.URL, "URL")
      }
      searchFrom = end.coerceAtLeast(bestIdx + 4)
    }
    return null
  }

  private fun trimUrlEnd(line: String, start: Int, rawEnd: Int): Int {
    var end = rawEnd
    while (end > start) {
      val last = line[end - 1]
      when {
        last in ".,;:!?'\"`>]" -> end--
        last == ')' -> {
          val openCount = line.substring(start, end).count { it == '(' }
          val closeCount = line.substring(start, end).count { it == ')' }
          if (closeCount > openCount) end-- else break
        }
        last == '}' -> {
          val openCount = line.substring(start, end).count { it == '{' }
          val closeCount = line.substring(start, end).count { it == '}' }
          if (closeCount > openCount) end-- else break
        }
        else -> break
      }
    }
    return end
  }

  fun findSemanticLineEntities(text: CharSequence, anchor: Int, includeCliFlags: Boolean): List<SelectionScope> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<SelectionScope>(6)
    val lineStart = StructuralScanner.findLineStart(text, anchor)
    val lineEnd = StructuralScanner.findLineEnd(text, anchor)
    if (lineEnd <= lineStart) return out
    val line = text.subSequence(lineStart, lineEnd).toString()
    val relAnchor = (anchor - lineStart).coerceIn(0, line.length)

    findUrlAt(text, anchor)?.let { out.add(it) }

    fun collectRegex(regex: Regex, kind: SelectionScopeKind, label: String) {
      for (m in regex.findAll(line)) {
        val s = m.range.first
        val e = m.range.last + 1
        if (relAnchor in s..e) {
          out.add(SelectionScope(lineStart + s, lineStart + e, kind, label))
        }
      }
    }

    collectRegex(UUID_REGEX, SelectionScopeKind.UUID_OR_TOKEN, "UUID")
    collectRegex(EMAIL_REGEX, SelectionScopeKind.UUID_OR_TOKEN, "Email")
    collectRegex(IPV4_PORT_REGEX, SelectionScopeKind.UUID_OR_TOKEN, "Host")
    collectRegex(FILE_PATH_REGEX, SelectionScopeKind.FILE_PATH, "Path")
    collectRegex(OPAQUE_TOKEN_REGEX, SelectionScopeKind.UUID_OR_TOKEN, "Token")
    if (includeCliFlags) {
      collectRegex(CLI_FLAG_REGEX, SelectionScopeKind.KEY_VALUE_PAIR, "Flag")
    }
    return out
  }

  fun addLineAndDocumentScopes(text: CharSequence, anchor: Int, out: MutableList<SelectionScope>) {
    if (text.isEmpty()) return
    val lineStart = StructuralScanner.findLineStart(text, anchor)
    val lineEnd = StructuralScanner.findLineEnd(text, anchor)
    var trimStart = lineStart
    while (trimStart < lineEnd && text[trimStart].isWhitespace()) trimStart++
    var trimEnd = lineEnd
    while (trimEnd > trimStart && text[trimEnd - 1].isWhitespace()) trimEnd--
    if (trimEnd > trimStart) {
      out.add(SelectionScope(trimStart, trimEnd, SelectionScopeKind.LINE, "Line"))
    }
    val lineWithNewlineEnd = if (lineEnd < text.length && text[lineEnd] == '\n') lineEnd + 1 else lineEnd
    if (lineWithNewlineEnd > lineStart) {
      out.add(SelectionScope(lineStart, lineWithNewlineEnd, SelectionScopeKind.LINE, "Full Line"))
    }
    out.add(SelectionScope(0, text.length, SelectionScopeKind.DOCUMENT, "All"))
  }
}

internal object CodeStructureStrategy : SelectionGrammarStrategy {
  override fun collectScopes(
    text: CharSequence,
    anchorOffset: Int,
    windowStart: Int,
    windowEnd: Int,
  ): List<SelectionScope> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<SelectionScope>(16)
    val scan =
      StructuralScanner.scan(
        text = text,
        anchorOffset = anchorOffset,
        windowStart = windowStart,
        windowEnd = windowEnd,
        allowSingleQuotes = true,
        allowBackticks = true,
        allowLineComments = true,
        allowBlockComments = true,
      )

    // 1. Token + semantic URL/path inside strings/comments
    val tokenScope = SharedSemanticDetectors.findTokenAt(text, anchorOffset)?.also { out.add(it) }
    SharedSemanticDetectors.findUrlAt(text, anchorOffset)?.let { out.add(it) }

    // 2. Qualified member chain (e.g., env.DB.prepare or req?.headers.get)
    val chainScope = findMemberChain(text, anchorOffset)?.also { out.add(it) }

    // 2b. Trailing call/index when anchor sits directly on the callee name before '(' or '['
    val calleeBase = chainScope ?: tokenScope
    if (calleeBase != null) {
      findTrailingCallOrIndex(text, calleeBase.start, calleeBase.end, scan)?.let { out.add(it) }
    }

    // 3. Enclosing quoted strings / template literals
    for (q in scan.enclosingQuotes) {
      if (q.innerEnd > q.innerStart) {
        out.add(SelectionScope(q.innerStart, q.innerEnd, SelectionScopeKind.STRING_CONTENT, "Text"))
      }
      if (q.endOffset > q.startOffset) {
        out.add(SelectionScope(q.startOffset, q.endOffset, SelectionScopeKind.STRING_LITERAL, "String"))
      }
    }

    // 4. Enclosing brackets + prefixed calls / index expressions / control & function blocks
    for (b in scan.enclosingBrackets) {
      if (b.innerEnd > b.innerStart) {
        val innerTrimmed = trimRange(text, b.innerStart, b.innerEnd)
        if (innerTrimmed.second > innerTrimmed.first) {
          out.add(
            SelectionScope(
              innerTrimmed.first,
              innerTrimmed.second,
              SelectionScopeKind.CALL_OR_BRACKET_INNER,
              if (b.isTemplateExpr) "Expr" else "Inner",
            ),
          )
        }
      }
      val bracketLabel =
        when {
          b.isTemplateExpr -> "Template"
          b.openChar == '{' -> "Block"
          b.openChar == '[' -> "Array"
          else -> "Parens"
        }
      val bracketKind = if (b.openChar == '{' && !b.isTemplateExpr) SelectionScopeKind.BLOCK else SelectionScopeKind.CALL_OR_BRACKET
      out.add(SelectionScope(b.fullStart, b.fullEnd, bracketKind, bracketLabel))

      // Attach leading callee/index prefix: e.g. Response.json(...) or arr[i]
      if (!b.isTemplateExpr && (b.openChar == '(' || b.openChar == '[')) {
        val prefixStart = scanCalleePrefixStart(text, b.openOffset)
        if (prefixStart < b.openOffset) {
          val kindLabel = if (b.openChar == '(') "Call" else "Index"
          out.add(SelectionScope(prefixStart, b.fullEnd, SelectionScopeKind.CALL_OR_BRACKET, kindLabel))
        }
      }

      // Attach function/method/control header before `{ ... }`
      if (!b.isTemplateExpr && b.openChar == '{') {
        val headerStart = scanBlockHeaderStart(text, b.openOffset, windowStart)
        if (headerStart < b.openOffset) {
          out.add(SelectionScope(headerStart, b.fullEnd, SelectionScopeKind.BLOCK, "Block"))
        }
      }
    }

    // 5. Key-value property on same line (e.g. `ok: true` or `"k": "v"`)
    findKeyValueOnLine(text, anchorOffset, scan)?.let { out.add(it) }

    // 6. Full statement around anchor (respecting balanced brackets inside window)
    findStatementAt(text, anchorOffset, scan)?.let { out.add(it) }

    // 7. Line + Document
    SharedSemanticDetectors.addLineAndDocumentScopes(text, anchorOffset, out)
    return out
  }

  private fun findMemberChain(text: CharSequence, anchor: Int): SelectionScope? {
    val len = text.length
    val pos = anchor.coerceIn(0, len)
    val isChainChar: (Char) -> Boolean = { c ->
      SharedSemanticDetectors.isIdentChar(c) || c == '.' || c == '?'
    }
    val probe =
      when {
        pos < len && isChainChar(text[pos]) -> pos
        pos > 0 && isChainChar(text[pos - 1]) -> pos - 1
        else -> return null
      }
    var s = probe
    while (s > 0 && isChainChar(text[s - 1])) {
      if (text[s - 1] == '?' && (s >= len || text[s] != '.')) break
      s--
    }
    var e = probe + 1
    while (e < len && isChainChar(text[e])) {
      if (text[e] == '?' && (e + 1 >= len || text[e + 1] != '.')) break
      e++
    }
    while (s < e && (text[s] == '.' || text[s] == '?')) s++
    while (e > s && (text[e - 1] == '.' || text[e - 1] == '?')) e--
    val slice = text.subSequence(s, e)
    if ('.' !in slice || e <= s) return null
    return SelectionScope(s, e, SelectionScopeKind.MEMBER_CHAIN, "Chain")
  }

  private fun findTrailingCallOrIndex(
    text: CharSequence,
    calleeStart: Int,
    calleeEnd: Int,
    scan: StructuralScanResult,
  ): SelectionScope? {
    val calleeText = text.subSequence(calleeStart, calleeEnd).toString()
    if (calleeText in setOf("if", "for", "while", "switch", "catch", "return", "await", "new", "typeof", "throw")) {
      return null
    }
    var i = calleeEnd
    while (i < scan.windowEnd && (text[i] == ' ' || text[i] == '\t')) i++
    if (i >= scan.windowEnd || i < scan.windowStart) return null
    val open = scan.masked[i - scan.windowStart]
    if (open != '(' && open != '[') return null
    val close = if (open == '(') ')' else ']'
    var depth = 0
    var p = i
    while (p < scan.windowEnd) {
      val m = scan.masked[p - scan.windowStart]
      if (m == open) depth++
      else if (m == close) {
        depth--
        if (depth == 0) {
          val label = if (open == '(') "Call" else "Index"
          return SelectionScope(calleeStart, p + 1, SelectionScopeKind.CALL_OR_BRACKET, label)
        }
      }
      p++
    }
    return null
  }

  private fun scanCalleePrefixStart(text: CharSequence, openBracketOffset: Int): Int {
    var i = openBracketOffset
    while (i > 0 && (text[i - 1] == ' ' || text[i - 1] == '\t')) i--
    val endOfName = i
    while (i > 0) {
      val ch = text[i - 1]
      if (SharedSemanticDetectors.isIdentChar(ch) || ch == '.' || ch == '?') {
        i--
      } else {
        break
      }
    }
    if (i == endOfName) return openBracketOffset
    // Don't swallow standalone keywords like `if (` or `return (` as a function call
    val word = text.subSequence(i, endOfName).toString()
    if (word in setOf("if", "for", "while", "switch", "catch", "return", "await", "new", "typeof", "throw")) {
      return openBracketOffset
    }
    return i
  }

  private fun scanBlockHeaderStart(text: CharSequence, openBraceOffset: Int, windowStart: Int): Int {
    val lineStart = StructuralScanner.findLineStart(text, openBraceOffset).coerceAtLeast(windowStart)
    var s = lineStart
    while (s < openBraceOffset && (text[s] == ' ' || text[s] == '\t')) s++
    if (s >= openBraceOffset) return openBraceOffset
    val prefix = text.subSequence(s, openBraceOffset).toString().trimEnd()
    if (prefix.isEmpty() || prefix.endsWith(":") || prefix.endsWith(",") || prefix.endsWith("(")) {
      return openBraceOffset
    }
    return s
  }

  private fun findKeyValueOnLine(
    text: CharSequence,
    anchor: Int,
    scan: StructuralScanResult,
  ): SelectionScope? {
    val lineStart = StructuralScanner.findLineStart(text, anchor).coerceAtLeast(scan.windowStart)
    val lineEnd = StructuralScanner.findLineEnd(text, anchor, scan.windowEnd)
    var colonIdx = -1
    var depth = 0
    for (i in lineStart until lineEnd) {
      val m = scan.masked[i - scan.windowStart]
      when (m) {
        '(', '[', '{' -> depth++
        ')', ']', '}' -> if (depth > 0) depth--
        ':' -> if (depth == 0) {
          colonIdx = i
          break
        }
      }
    }
    if (colonIdx < 0) return null
    var start = colonIdx - 1
    while (start >= lineStart && text[start].isWhitespace()) start--
    // Scan key backwards to separator
    var kStart = start
    while (kStart > lineStart) {
      val m = scan.masked[kStart - 1 - scan.windowStart]
      if (m == ',' || m == '{' || m == ';') break
      kStart--
    }
    while (kStart < colonIdx && text[kStart].isWhitespace()) kStart++

    var vEnd = colonIdx + 1
    var vDepth = 0
    while (vEnd < lineEnd) {
      val m = scan.masked[vEnd - scan.windowStart]
      when (m) {
        '(', '[', '{' -> vDepth++
        ')', ']', '}' -> {
          if (vDepth == 0) break
          vDepth--
        }
        ',', ';' -> if (vDepth == 0) break
      }
      vEnd++
    }
    while (vEnd > colonIdx + 1 && text[vEnd - 1].isWhitespace()) vEnd--
    if (kStart < colonIdx && vEnd > colonIdx + 1 && anchor in kStart..vEnd) {
      return SelectionScope(kStart, vEnd, SelectionScopeKind.KEY_VALUE_PAIR, "Pair")
    }
    return null
  }

  private fun findStatementAt(
    text: CharSequence,
    anchor: Int,
    scan: StructuralScanResult,
  ): SelectionScope? {
    val lineStart = StructuralScanner.findLineStart(text, anchor).coerceAtLeast(scan.windowStart)
    val lineEnd = StructuralScanner.findLineEnd(text, anchor, scan.windowEnd)
    val trimmed = trimRange(text, lineStart, lineEnd)
    if (trimmed.second <= trimmed.first) return null
    // If statement ends with ';' on this line, capture clean trimmed statement
    val lineMaskedStart = trimmed.first - scan.windowStart
    val lineMaskedEnd = trimmed.second - scan.windowStart
    if (lineMaskedStart >= 0 && lineMaskedEnd <= scan.masked.size) {
      val lastMasked = scan.masked[lineMaskedEnd - 1]
      if (lastMasked == ';') {
        return SelectionScope(trimmed.first, trimmed.second, SelectionScopeKind.STATEMENT, "Statement")
      }
    }
    return null
  }

  private fun trimRange(text: CharSequence, start: Int, end: Int): Pair<Int, Int> {
    var s = start
    var e = end
    while (s < e && text[s].isWhitespace()) s++
    while (e > s && text[e - 1].isWhitespace()) e--
    return s to e
  }
}

internal object JsonStructureStrategy : SelectionGrammarStrategy {
  override fun collectScopes(
    text: CharSequence,
    anchorOffset: Int,
    windowStart: Int,
    windowEnd: Int,
  ): List<SelectionScope> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<SelectionScope>(16)
    val scan =
      StructuralScanner.scan(
        text = text,
        anchorOffset = anchorOffset,
        windowStart = windowStart,
        windowEnd = windowEnd,
        allowSingleQuotes = false,
        allowBackticks = false,
        allowLineComments = true,
        allowBlockComments = true,
      )

    SharedSemanticDetectors.findTokenAt(text, anchorOffset, allowHyphen = true)?.let { out.add(it) }
    SharedSemanticDetectors.findUrlAt(text, anchorOffset)?.let { out.add(it) }

    for (q in scan.enclosingQuotes) {
      if (q.innerEnd > q.innerStart) {
        out.add(SelectionScope(q.innerStart, q.innerEnd, SelectionScopeKind.STRING_CONTENT, "Text"))
      }
      if (q.endOffset > q.startOffset) {
        out.add(SelectionScope(q.startOffset, q.endOffset, SelectionScopeKind.STRING_LITERAL, "String"))
      }
    }

    // Detect JSON `"key": value` property containing anchor (supports multi-line object/array values!)
    findJsonPropertyAt(text, anchorOffset, scan)?.let { out.add(it) }

    for (b in scan.enclosingBrackets) {
      if (b.innerEnd > b.innerStart) {
        var s = b.innerStart
        var e = b.innerEnd
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (e > s) {
          out.add(SelectionScope(s, e, SelectionScopeKind.CALL_OR_BRACKET_INNER, "Inner"))
        }
      }
      val label = if (b.openChar == '{') "Object" else "Array"
      val kind = if (b.openChar == '{') SelectionScopeKind.BLOCK else SelectionScopeKind.CALL_OR_BRACKET
      out.add(SelectionScope(b.fullStart, b.fullEnd, kind, label))
    }

    SharedSemanticDetectors.addLineAndDocumentScopes(text, anchorOffset, out)
    return out
  }

  private fun findJsonPropertyAt(
    text: CharSequence,
    anchor: Int,
    scan: StructuralScanResult,
  ): SelectionScope? {
    // Search enclosing object bracket (innermost `{ ... }`) or whole window
    val obj = scan.enclosingBrackets.firstOrNull { it.openChar == '{' }
    val searchStart = obj?.innerStart ?: scan.windowStart
    val searchEnd = obj?.innerEnd ?: scan.windowEnd

    // Walk top-level properties inside this object
    var i = searchStart
    while (i < searchEnd) {
      while (i < searchEnd && (text[i].isWhitespace() || scan.masked[i - scan.windowStart] == ',')) i++
      if (i >= searchEnd) break
      if (text[i] != '"') {
        i++
        continue
      }
      val keyStart = i
      i++
      while (i < searchEnd) {
        if (text[i] == '\\' && i + 1 < searchEnd) {
          i += 2
          continue
        }
        if (text[i] == '"') {
          i++
          break
        }
        i++
      }
      while (i < searchEnd && text[i].isWhitespace()) i++
      if (i >= searchEnd || scan.masked[i - scan.windowStart] != ':') continue
      i++ // skip ':'
      while (i < searchEnd && text[i].isWhitespace()) i++
      if (i >= searchEnd) break

      val valEnd = scanJsonValueEnd(text, i, searchEnd, scan)
      if (anchor in keyStart..valEnd && valEnd > keyStart) {
        return SelectionScope(keyStart, valEnd, SelectionScopeKind.KEY_VALUE_PAIR, "JSON Pair")
      }
      i = valEnd
    }
    return null
  }

  private fun scanJsonValueEnd(
    text: CharSequence,
    start: Int,
    limit: Int,
    scan: StructuralScanResult,
  ): Int {
    var i = start
    if (i >= limit) return limit
    val first = text[i]
    if (first == '"') {
      i++
      while (i < limit) {
        if (text[i] == '\\' && i + 1 < limit) {
          i += 2
          continue
        }
        if (text[i] == '"') return i + 1
        i++
      }
      return limit
    }
    if (first == '{' || first == '[') {
      var depth = 0
      while (i < limit) {
        val m = scan.masked[i - scan.windowStart]
        if (m == '{' || m == '[') depth++
        else if (m == '}' || m == ']') {
          depth--
          if (depth == 0) return i + 1
        }
        i++
      }
      return limit
    }
    while (i < limit) {
      val m = scan.masked[i - scan.windowStart]
      if (m == ',' || m == '}' || m == ']' || text[i] == '\n') break
      i++
    }
    while (i > start && text[i - 1].isWhitespace()) i--
    return i
  }
}

internal object MarkupStructureStrategy : SelectionGrammarStrategy {
  private val ATTR_REGEX = Regex("""[A-Za-z_:][A-Za-z0-9_.:-]*\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""")
  private val TAG_NAME_REGEX = Regex("""^<\s*/?\s*([A-Za-z][A-Za-z0-9:-]*)""")

  override fun collectScopes(
    text: CharSequence,
    anchorOffset: Int,
    windowStart: Int,
    windowEnd: Int,
  ): List<SelectionScope> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<SelectionScope>(14)
    val scan =
      StructuralScanner.scan(
        text = text,
        anchorOffset = anchorOffset,
        windowStart = windowStart,
        windowEnd = windowEnd,
        allowSingleQuotes = true,
        allowBackticks = false,
        allowLineComments = false,
        allowBlockComments = true,
        allowAngleBrackets = true,
      )

    SharedSemanticDetectors.findTokenAt(text, anchorOffset, allowHyphen = true)?.let { out.add(it) }
    SharedSemanticDetectors.findUrlAt(text, anchorOffset)?.let { out.add(it) }

    for (q in scan.enclosingQuotes) {
      if (q.innerEnd > q.innerStart) {
        out.add(SelectionScope(q.innerStart, q.innerEnd, SelectionScopeKind.STRING_CONTENT, "Text"))
      }
      if (q.endOffset > q.startOffset) {
        out.add(SelectionScope(q.startOffset, q.endOffset, SelectionScopeKind.STRING_LITERAL, "Value"))
      }
    }

    // HTML/XML attributes on enclosing line or tag
    val lineStart = StructuralScanner.findLineStart(text, anchorOffset).coerceAtLeast(windowStart)
    val lineEnd = StructuralScanner.findLineEnd(text, anchorOffset, windowEnd)
    val line = text.subSequence(lineStart, lineEnd).toString()
    val relAnchor = (anchorOffset - lineStart).coerceIn(0, line.length)
    for (m in ATTR_REGEX.findAll(line)) {
      if (relAnchor in m.range.first..(m.range.last + 1)) {
        out.add(
          SelectionScope(
            lineStart + m.range.first,
            lineStart + m.range.last + 1,
            SelectionScopeKind.KEY_VALUE_PAIR,
            "Attr",
          ),
        )
      }
    }

    // Enclosing `<...>` tag and paired `<tag>...</tag>` element
    for (b in scan.enclosingBrackets) {
      if (b.openChar == '<') {
        out.add(SelectionScope(b.fullStart, b.fullEnd, SelectionScopeKind.CALL_OR_BRACKET, "Tag"))
        findPairedHtmlElement(text, b.fullStart, b.fullEnd, windowEnd)?.let { out.add(it) }
      } else {
        val label = if (b.openChar == '{') "Block" else "Expr"
        val kind = if (b.openChar == '{') SelectionScopeKind.BLOCK else SelectionScopeKind.CALL_OR_BRACKET
        out.add(SelectionScope(b.fullStart, b.fullEnd, kind, label))
      }
    }

    SharedSemanticDetectors.addLineAndDocumentScopes(text, anchorOffset, out)
    return out
  }

  private fun findPairedHtmlElement(
    text: CharSequence,
    tagStart: Int,
    tagEnd: Int,
    windowEnd: Int,
  ): SelectionScope? {
    val openTag = text.subSequence(tagStart, tagEnd).toString()
    if (openTag.startsWith("</") || openTag.endsWith("/>")) return null
    val tagName = TAG_NAME_REGEX.find(openTag)?.groupValues?.getOrNull(1) ?: return null
    val closeNeedle = "</$tagName>"
    val searchSlice = text.subSequence(tagEnd, windowEnd).toString()
    val closeRel = searchSlice.indexOf(closeNeedle, ignoreCase = true)
    if (closeRel < 0) return null
    val elementEnd = tagEnd + closeRel + closeNeedle.length
    return SelectionScope(tagStart, elementEnd, SelectionScopeKind.MARKUP_ELEMENT, "Element")
  }
}

internal object TerminalSemanticStrategy : SelectionGrammarStrategy {
  private val HEADER_OR_KV_REGEX = Regex("""^\s*([A-Za-z][A-Za-z0-9_\-.]{1,48})\s*[:=]\s*(.+?)\s*$""")
  private val INLINE_KV_REGEX = Regex("""([A-Za-z_][A-Za-z0-9_\-.]*)=(\{[^\n]*\}|\[[^\n]*\]|"[^"]*"|'[^']*'|[^\s,;]+)""")

  override fun collectScopes(
    text: CharSequence,
    anchorOffset: Int,
    windowStart: Int,
    windowEnd: Int,
  ): List<SelectionScope> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<SelectionScope>(16)

    // 1. Word token
    SharedSemanticDetectors.findTokenAt(text, anchorOffset, allowHyphen = true)?.let { out.add(it) }

    // 2. Semantic line entities: URL, Path:line:col, UUID, Email, IP:port, Opaque Token, CLI Flag
    out.addAll(SharedSemanticDetectors.findSemanticLineEntities(text, anchorOffset, includeCliFlags = true))

    // 3. Structural scan for quoted strings & inline JSON `{...}` / `[...]` / `(...)`
    val scan =
      StructuralScanner.scan(
        text = text,
        anchorOffset = anchorOffset,
        windowStart = windowStart,
        windowEnd = windowEnd,
        allowSingleQuotes = true,
        allowBackticks = true,
        allowLineComments = false,
        allowBlockComments = false,
      )
    for (q in scan.enclosingQuotes) {
      if (q.innerEnd > q.innerStart) {
        out.add(SelectionScope(q.innerStart, q.innerEnd, SelectionScopeKind.STRING_CONTENT, "Text"))
      }
      if (q.endOffset > q.startOffset) {
        out.add(SelectionScope(q.startOffset, q.endOffset, SelectionScopeKind.STRING_LITERAL, "String"))
      }
    }
    for (b in scan.enclosingBrackets) {
      if (b.innerEnd > b.innerStart) {
        out.add(SelectionScope(b.innerStart, b.innerEnd, SelectionScopeKind.CALL_OR_BRACKET_INNER, "Inner"))
      }
      val label =
        when (b.openChar) {
          '{' -> "JSON"
          '[' -> "List"
          else -> "Group"
        }
      out.add(SelectionScope(b.fullStart, b.fullEnd, SelectionScopeKind.CALL_OR_BRACKET, label))
    }

    // 4. Inline key=value (e.g. `payload={"ok":true}`) & HTTP Header (`Authorization: Bearer ...`)
    val lineStart = StructuralScanner.findLineStart(text, anchorOffset)
    val lineEnd = StructuralScanner.findLineEnd(text, anchorOffset)
    val line = text.subSequence(lineStart, lineEnd).toString()
    val relAnchor = (anchorOffset - lineStart).coerceIn(0, line.length)

    for (m in INLINE_KV_REGEX.findAll(line)) {
      val s = m.range.first
      val e = m.range.last + 1
      if (relAnchor in s..e) {
        val valGroup = m.groups[2]
        if (valGroup != null && relAnchor in valGroup.range.first..(valGroup.range.last + 1)) {
          out.add(
            SelectionScope(
              lineStart + valGroup.range.first,
              lineStart + valGroup.range.last + 1,
              SelectionScopeKind.STRING_CONTENT,
              "Value",
            ),
          )
        }
        out.add(SelectionScope(lineStart + s, lineStart + e, SelectionScopeKind.KEY_VALUE_PAIR, "Pair"))
      }
    }

    HEADER_OR_KV_REGEX.matchEntire(line)?.let { m ->
      val valGroup = m.groups[2]
      val keyGroup = m.groups[1]
      if (valGroup != null && relAnchor in valGroup.range.first..(valGroup.range.last + 1)) {
        out.add(
          SelectionScope(
            lineStart + valGroup.range.first,
            lineStart + valGroup.range.last + 1,
            SelectionScopeKind.STRING_CONTENT,
            "Value",
          ),
        )
      }
      if (keyGroup != null && valGroup != null) {
        out.add(
          SelectionScope(
            lineStart + keyGroup.range.first,
            lineStart + valGroup.range.last + 1,
            SelectionScopeKind.KEY_VALUE_PAIR,
            "Header",
          ),
        )
      }
    }

    // 5. CLI command segment after `$ ` / `> ` prompt if present
    val trimmedLead = line.indexOfFirst { !it.isWhitespace() }
    if (trimmedLead >= 0 && trimmedLead + 2 < line.length && (line[trimmedLead] == '$' || line[trimmedLead] == '>') && line[trimmedLead + 1] == ' ') {
      val cmdStart = lineStart + trimmedLead + 2
      var cmdEnd = lineEnd
      while (cmdEnd > cmdStart && text[cmdEnd - 1].isWhitespace()) cmdEnd--
      if (cmdEnd > cmdStart && anchorOffset in cmdStart..cmdEnd) {
        out.add(SelectionScope(cmdStart, cmdEnd, SelectionScopeKind.STATEMENT, "Command"))
      }
    }

    // 6. Line + Document
    SharedSemanticDetectors.addLineAndDocumentScopes(text, anchorOffset, out)
    return out
  }
}
