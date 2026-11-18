package com.example.editor

data class EmmetExpansion(
  val abbreviation: String,
  val expandedText: String,
  val cursorBackOffset: Int,
  val replaceLength: Int,
)

object EmmetExpander {
  private val VOID_TAGS =
    setOf(
      "area", "base", "br", "col", "embed", "hr", "img", "input",
      "link", "meta", "param", "source", "track", "wbr",
    )

  private val CSS_SHORTHANDS =
    mapOf(
      "df" to "display: flex;",
      "dg" to "display: grid;",
      "db" to "display: block;",
      "dib" to "display: inline-block;",
      "dn" to "display: none;",
      "fdc" to "flex-direction: column;",
      "fdr" to "flex-direction: row;",
      "fw" to "flex-wrap: wrap;",
      "jcc" to "justify-content: center;",
      "jcsb" to "justify-content: space-between;",
      "jcfe" to "justify-content: flex-end;",
      "aic" to "align-items: center;",
      "aifs" to "align-items: flex-start;",
      "posr" to "position: relative;",
      "posa" to "position: absolute;",
      "posf" to "position: fixed;",
      "poss" to "position: sticky;",
      "w100p" to "width: 100%;",
      "h100p" to "height: 100%;",
      "h100vh" to "height: 100vh;",
      "m0" to "margin: 0;",
      "ma" to "margin: 0 auto;",
      "p0" to "padding: 0;",
      "bg" to "background: #;",
      "bgc" to "background-color: #;",
      "br8" to "border-radius: 8px;",
      "br12" to "border-radius: 12px;",
      "tac" to "text-align: center;",
      "tdn" to "text-decoration: none;",
      "curp" to "cursor: pointer;",
      "ovh" to "overflow: hidden;",
      "ova" to "overflow: auto;",
      "fwb" to "font-weight: bold;",
      "bsbb" to "box-sizing: border-box;",
    )

  private val HTML_PRESETS =
    mapOf(
      "!" to
        ("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n  <title>Document</title>\n</head>\n<body>\n  \n</body>\n</html>" to 16),
      "html:5" to
        ("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n  <title>Document</title>\n</head>\n<body>\n  \n</body>\n</html>" to 16),
      "link:css" to ("<link rel=\"stylesheet\" href=\"style.css\">" to 2),
      "script:src" to ("<script src=\"app.js\"></script>" to 11),
      "meta:vp" to ("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" to 0),
      "a:mail" to ("<a href=\"mailto:\"></a>" to 6),
      "a:link" to ("<a href=\"https://\"></a>" to 6),
      "btn" to ("<button type=\"button\"></button>" to 9),
      "input:text" to ("<input type=\"text\" name=\"\" id=\"\">" to 2),
      "input:email" to ("<input type=\"email\" name=\"\" id=\"\">" to 2),
      "input:password" to ("<input type=\"password\" name=\"\" id=\"\">" to 2),
      "input:submit" to ("<input type=\"submit\" value=\"Submit\">" to 0),
      "form:post" to ("<form action=\"\" method=\"post\">\n  \n</form>" to 8),
      "form:get" to ("<form action=\"\" method=\"get\">\n  \n</form>" to 8),
    )

  fun isHtmlLikePath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".html") ||
      lower.endsWith(".htm") ||
      lower.endsWith(".xml") ||
      lower.endsWith(".svg") ||
      lower.endsWith(".jsx") ||
      lower.endsWith(".tsx")
  }

  fun isCssLikePath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".css") || lower.endsWith(".scss") || lower.endsWith(".less")
  }

  fun isSupportedPath(path: String): Boolean = isHtmlLikePath(path) || isCssLikePath(path)

  fun extractAbbreviationAtCursor(
    text: CharSequence,
    cursor: Int,
  ): String {
    val safeCursor = cursor.coerceIn(0, text.length)
    if (safeCursor == 0) return ""
    var i = safeCursor
    var braceDepth = 0
    var bracketDepth = 0
    while (i > 0) {
      val ch = text[i - 1]
      when {
        ch == '}' -> braceDepth++
        ch == '{' -> {
          if (braceDepth == 0) break
          braceDepth--
        }
        ch == ']' -> bracketDepth++
        ch == '[' -> {
          if (bracketDepth == 0) break
          bracketDepth--
        }
        braceDepth > 0 || bracketDepth > 0 -> Unit
        ch.isWhitespace() -> break
        ch in listOf('<', '=', ';', '(', ')', ',', '"', '\'', '`') -> break
        else -> Unit
      }
      i--
    }
    if (braceDepth != 0 || bracketDepth != 0 || i >= safeCursor) return ""
    return text.subSequence(i, safeCursor).toString()
  }

  fun expandAtCursor(
    text: CharSequence,
    cursor: Int,
    filePath: String,
    lineIndent: String = "",
  ): EmmetExpansion? {
    val abbrev = extractAbbreviationAtCursor(text, cursor)
    if (abbrev.isBlank()) return null
    return expand(abbrev, filePath, lineIndent)
  }

  fun expand(
    abbreviation: String,
    filePath: String,
    lineIndent: String = "",
  ): EmmetExpansion? {
    val trimmed = abbreviation.trim()
    if (trimmed.isEmpty()) return null

    if (isCssLikePath(filePath)) {
      val css = expandCss(trimmed) ?: return null
      val back = if (css.endsWith("#;")) 1 else 0
      return EmmetExpansion(
        abbreviation = trimmed,
        expandedText = css,
        cursorBackOffset = back,
        replaceLength = trimmed.length,
      )
    }

    if (!isHtmlLikePath(filePath)) return null

    val isJsx =
      filePath.endsWith(".jsx", ignoreCase = true) ||
        filePath.endsWith(".tsx", ignoreCase = true)

    HTML_PRESETS[trimmed]?.let { (tpl, back) ->
      val adjusted = if (isJsx) tpl.replace("class=\"", "className=\"") else tpl
      val indented = applyLineIndent(adjusted, lineIndent)
      return EmmetExpansion(
        abbreviation = trimmed,
        expandedText = indented,
        cursorBackOffset = back,
        replaceLength = trimmed.length,
      )
    }

    val parsed = parseHtmlTree(trimmed) ?: return null
    val rendered = renderHtmlNodes(parsed, depth = 0, isJsx = isJsx)
    if (rendered.isBlank()) return null
    val indented = applyLineIndent(rendered, lineIndent)
    val cursorBack = computeCursorBackOffset(indented)
    return EmmetExpansion(
      abbreviation = trimmed,
      expandedText = indented,
      cursorBackOffset = cursorBack,
      replaceLength = trimmed.length,
    )
  }

  fun suggestCssCompletions(prefix: String): List<Pair<String, String>> {
    val q = prefix.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return CSS_SHORTHANDS.entries
      .filter { it.key.startsWith(q) }
      .sortedBy { it.key.length }
      .map { it.key to it.value }
  }

  private fun expandCss(abbr: String): String? {
    CSS_SHORTHANDS[abbr.lowercase()]?.let { return it }
    val numUnit = Regex("""^([mp])([trblxy]?)(-?\d+)(p|rem|em|vh|vw|px)?$""").matchEntire(abbr.lowercase())
    if (numUnit != null) {
      val prop = if (numUnit.groupValues[1] == "m") "margin" else "padding"
      val dir =
        when (numUnit.groupValues[2]) {
          "t" -> "-top"
          "r" -> "-right"
          "b" -> "-bottom"
          "l" -> "-left"
          "x" -> "-inline"
          "y" -> "-block"
          else -> ""
        }
      val value = numUnit.groupValues[3]
      val unit = numUnit.groupValues[4].let { if (it == "p") "%" else it.ifEmpty { if (value == "0") "" else "px" } }
      return "$prop$dir: $value$unit;"
    }
    val sizeUnit = Regex("""^([wh])(\d+)(p|rem|em|vh|vw|px)?$""").matchEntire(abbr.lowercase())
    if (sizeUnit != null) {
      val prop = if (sizeUnit.groupValues[1] == "w") "width" else "height"
      val value = sizeUnit.groupValues[2]
      val unit = sizeUnit.groupValues[3].let { if (it == "p") "%" else it.ifEmpty { if (value == "0") "" else "px" } }
      return "$prop: $value$unit;"
    }
    return null
  }

  private data class HtmlNode(
    val tag: String,
    val id: String? = null,
    val classes: List<String> = emptyList(),
    val attrs: List<Pair<String, String?>> = emptyList(),
    val text: String? = null,
    val count: Int = 1,
    val children: MutableList<HtmlNode> = mutableListOf(),
  )

  private enum class Op { CHILD, SIBLING, CLIMB }

  private fun parseHtmlTree(abbr: String): List<HtmlNode>? {
    val tokens = tokenizeExpression(abbr) ?: return null
    if (tokens.isEmpty()) return null

    val roots = mutableListOf<HtmlNode>()
    val stack = mutableListOf<HtmlNode>()
    var pendingOp = Op.SIBLING

    for (token in tokens) {
      when (token) {
        ">" -> pendingOp = Op.CHILD
        "+" -> pendingOp = Op.SIBLING
        "^" -> {
          if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
          pendingOp = Op.SIBLING
        }
        else -> {
          val node = parseSingleSelector(token) ?: return null
          when (pendingOp) {
            Op.CHILD -> {
              val parent = stack.lastOrNull()
              if (parent != null) parent.children.add(node) else roots.add(node)
              stack.add(node)
            }
            Op.SIBLING -> {
              if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
              val parent = stack.lastOrNull()
              if (parent != null) parent.children.add(node) else roots.add(node)
              stack.add(node)
            }
            Op.CLIMB -> {
              val parent = stack.lastOrNull()
              if (parent != null) parent.children.add(node) else roots.add(node)
              stack.add(node)
            }
          }
          pendingOp = Op.SIBLING
        }
      }
    }
    return roots
  }

  private fun tokenizeExpression(input: String): List<String>? {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var braceDepth = 0
    var bracketDepth = 0
    var i = 0
    while (i < input.length) {
      val ch = input[i]
      when {
        ch == '{' -> {
          braceDepth++
          current.append(ch)
        }
        ch == '}' -> {
          braceDepth--
          if (braceDepth < 0) return null
          current.append(ch)
        }
        ch == '[' -> {
          bracketDepth++
          current.append(ch)
        }
        ch == ']' -> {
          bracketDepth--
          if (bracketDepth < 0) return null
          current.append(ch)
        }
        braceDepth == 0 && bracketDepth == 0 && (ch == '>' || ch == '+' || ch == '^') -> {
          if (current.isNotEmpty()) {
            result.add(current.toString())
            current.clear()
          }
          result.add(ch.toString())
        }
        else -> current.append(ch)
      }
      i++
    }
    if (braceDepth != 0 || bracketDepth != 0) return null
    if (current.isNotEmpty()) {
      result.add(current.toString())
    }
    return result
  }

  private fun parseSingleSelector(raw: String): HtmlNode? {
    if (raw.isBlank()) return null
    var s = raw
    var count = 1
    val multMatch = Regex("""^(.*)\*(\d+)$""").matchEntire(s)
    if (multMatch != null) {
      s = multMatch.groupValues[1]
      count = multMatch.groupValues[2].toIntOrNull()?.coerceIn(1, 50) ?: 1
    }

    var text: String? = null
    val braceIdx = findTopLevelChar(s, '{')
    if (braceIdx >= 0) {
      val closeBrace = s.lastIndexOf('}')
      if (closeBrace <= braceIdx) return null
      text = s.substring(braceIdx + 1, closeBrace)
      s = s.substring(0, braceIdx) + s.substring(closeBrace + 1)
    }

    val attrs = mutableListOf<Pair<String, String?>>()
    while (true) {
      val bStart = s.indexOf('[')
      if (bStart < 0) break
      val bEnd = s.indexOf(']', bStart + 1)
      if (bEnd < 0) return null
      val inside = s.substring(bStart + 1, bEnd).trim()
      parseAttributes(inside, attrs)
      s = s.substring(0, bStart) + s.substring(bEnd + 1)
    }

    var id: String? = null
    val classes = mutableListOf<String>()
    val tagBuilder = StringBuilder()
    var i = 0
    while (i < s.length) {
      val ch = s[i]
      if (ch == '#' || ch == '.') {
        i++
        val start = i
        while (i < s.length && s[i] != '#' && s[i] != '.') i++
        val token = s.substring(start, i)
        if (token.isNotBlank()) {
          if (ch == '#') id = token else classes.add(token)
        }
      } else {
        tagBuilder.append(ch)
        i++
      }
    }

    val rawTag = tagBuilder.toString().trim()
    val tag =
      when {
        rawTag.isNotEmpty() -> {
          if (!rawTag.matches(Regex("""^[A-Za-z][A-Za-z0-9:-]*$"""))) return null
          rawTag
        }
        id != null || classes.isNotEmpty() -> "div"
        else -> return null
      }

    return HtmlNode(
      tag = tag,
      id = id,
      classes = classes,
      attrs = attrs,
      text = text,
      count = count,
    )
  }

  private fun findTopLevelChar(s: String, target: Char): Int {
    var inBracket = false
    for (i in s.indices) {
      val ch = s[i]
      if (ch == '[') inBracket = true
      else if (ch == ']') inBracket = false
      else if (!inBracket && ch == target) return i
    }
    return -1
  }

  private fun parseAttributes(
    raw: String,
    out: MutableList<Pair<String, String?>>,
  ) {
    val regex = Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s]+)))?""")
    regex.findAll(raw).forEach { m ->
      val key = m.groupValues[1]
      val valStr =
        m.groupValues[2].takeIf { it.isNotEmpty() || m.value.contains("=\"\"") }
          ?: m.groupValues[3].takeIf { it.isNotEmpty() || m.value.contains("=''") }
          ?: m.groupValues[4].takeIf { it.isNotEmpty() }
      out.add(key to valStr)
    }
  }

  private fun renderHtmlNodes(
    nodes: List<HtmlNode>,
    depth: Int,
    isJsx: Boolean,
  ): String {
    val lines = mutableListOf<String>()
    nodes.forEach { node ->
      for (idx in 1..node.count) {
        lines.add(renderSingleHtmlNode(node, index = idx, total = node.count, depth = depth, isJsx = isJsx))
      }
    }
    return lines.joinToString("\n")
  }

  private fun renderSingleHtmlNode(
    node: HtmlNode,
    index: Int,
    total: Int,
    depth: Int,
    isJsx: Boolean,
  ): String {
    val indent = "  ".repeat(depth)
    val attrParts = mutableListOf<String>()
    node.id?.let { attrParts.add("id=\"${applyCounter(it, index, total)}\"") }
    if (node.classes.isNotEmpty()) {
      val classKey = if (isJsx) "className" else "class"
      val joined = node.classes.joinToString(" ") { applyCounter(it, index, total) }
      attrParts.add("$classKey=\"$joined\"")
    }
    node.attrs.forEach { (k, v) ->
      if (v == null) attrParts.add(k)
      else attrParts.add("$k=\"${applyCounter(v, index, total)}\"")
    }
    val defaultAttrs = defaultAttributesForTag(node.tag, attrParts)
    val allAttrs = (attrParts + defaultAttrs).joinToString(" ")
    val openTag = if (allAttrs.isEmpty()) "<${node.tag}>" else "<${node.tag} $allAttrs>"

    if (node.tag.lowercase() in VOID_TAGS && node.children.isEmpty()) {
      return if (isJsx) "$indent${openTag.dropLast(1)} />" else "$indent$openTag"
    }

    val textValue = node.text?.let { applyCounter(it, index, total) }.orEmpty()
    if (node.children.isEmpty()) {
      return "$indent$openTag$textValue</${node.tag}>"
    }

    val childrenRendered = renderHtmlNodes(node.children, depth + 1, isJsx)
    return buildString {
      append(indent).append(openTag).append('\n')
      if (textValue.isNotEmpty()) {
        append("  ".repeat(depth + 1)).append(textValue).append('\n')
      }
      append(childrenRendered).append('\n')
      append(indent).append("</").append(node.tag).append('>')
    }
  }

  private fun defaultAttributesForTag(tag: String, existing: List<String>): List<String> {
    val keys = existing.map { it.substringBefore('=') }.toSet()
    return when (tag.lowercase()) {
      "a" -> if ("href" !in keys) listOf("href=\"\"") else emptyList()
      "img" -> {
        val out = mutableListOf<String>()
        if ("src" !in keys) out.add("src=\"\"")
        if ("alt" !in keys) out.add("alt=\"\"")
        out
      }
      "link" -> if ("rel" !in keys && "href" !in keys) listOf("rel=\"stylesheet\"", "href=\"\"") else emptyList()
      "input" -> if ("type" !in keys) listOf("type=\"text\"") else emptyList()
      else -> emptyList()
    }
  }

  private fun applyCounter(value: String, index: Int, total: Int): String {
    if (!value.contains('$')) return value
    return Regex("""\$+""").replace(value) { m ->
      index.toString().padStart(m.value.length, '0')
    }
  }

  private fun applyLineIndent(rendered: String, lineIndent: String): String {
    if (lineIndent.isEmpty()) return rendered
    val parts = rendered.split('\n')
    if (parts.size <= 1) return rendered
    return parts.mapIndexed { idx, line -> if (idx == 0) line else "$lineIndent$line" }.joinToString("\n")
  }

  private fun computeCursorBackOffset(expanded: String): Int {
    val emptyQuotes = expanded.indexOf("\"\"")
    if (emptyQuotes >= 0) {
      return expanded.length - (emptyQuotes + 1)
    }
    val emptyTag = expanded.indexOf("></")
    if (emptyTag >= 0) {
      return expanded.length - (emptyTag + 1)
    }
    return 0
  }
}
