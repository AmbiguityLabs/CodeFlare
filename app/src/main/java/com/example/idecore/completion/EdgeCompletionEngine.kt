package com.example.idecore.completion

import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace
import com.example.editor.EmmetExpander
import com.example.editor.WranglerSyncParser
import com.example.idecore.snippets.CodeSnippetItem
import com.example.idecore.snippets.CodeSnippetRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class EdgeCompletionKind(val badge: String) {
  BINDING("env"),
  API("api"),
  SYMBOL("sym"),
  FUNCTION("fn"),
  KEYWORD("kw"),
  POSTFIX("pfx"),
  TEMPLATE("tpl"),
  WORD("txt"),
}

data class EdgeCompletionItem(
  val label: String,
  val insertText: String,
  val kind: EdgeCompletionKind,
  val detail: String = "",
  val cursorBackOffset: Int = 0,
  val replaceStart: Int = -1,
  val replaceEnd: Int = -1,
  val sortPriority: Int = 50,
)

/**
 * Per-project persisted completion acceptance stats adapted from CodeAssist CompletionStats.
 * Frequently accepted items float up in ranking across editing sessions.
 */
class CompletionStatsStore(private val file: File) {
  private val lock = Any()
  @Volatile private var counts: MutableMap<String, Int>? = null

  private fun loaded(): MutableMap<String, Int> {
    counts?.let { return it }
    synchronized(lock) {
      counts?.let { return it }
      val map = HashMap<String, Int>()
      if (file.isFile) {
        runCatching {
          val props = Properties()
          file.inputStream().use { props.load(it) }
          for ((k, v) in props) {
            map[k.toString()] = v.toString().toIntOrNull() ?: 0
          }
        }
      }
      counts = map
      return map
    }
  }

  fun countFor(key: String): Int = if (key.isEmpty()) 0 else loaded()[key] ?: 0

  fun noteAccepted(label: String) {
    val key = keyOf(label)
    if (key.isEmpty()) return
    synchronized(lock) {
      val map = loaded()
      map[key] = ((map[key] ?: 0) + 1).coerceAtMost(MAX_COUNT)
      runCatching {
        file.parentFile?.mkdirs()
        val props = Properties()
        for ((k, v) in map) props.setProperty(k, v.toString())
        file.outputStream().use { props.store(it, "EdgeCode completion acceptance stats") }
      }
    }
  }

  companion object {
    private const val MAX_COUNT = 1000

    fun keyOf(label: String): String =
      label.takeWhile { it.isLetterOrDigit() || it == '_' || it == '$' || it == '.' }
  }
}

/**
 * Lexical member-usage frequency index adapted from CodeAssist MemberUsageIndex.
 * Counts how often each member name follows `.` or `?.` in project JS/TS sources so real
 * codebase members float above unused suggestions.
 */
class MemberUsageIndexer {
  private val perFileCounts = ConcurrentHashMap<String, Map<String, Int>>()
  @Volatile private var aggregated: Map<String, Int> = emptyMap()

  fun indexWorkspace(workspace: WorkerWorkspace?) {
    perFileCounts.clear()
    if (workspace == null) {
      aggregated = emptyMap()
      return
    }
    for (f in workspace.files) {
      if (f.language == WorkerLanguage.JAVASCRIPT || f.language == WorkerLanguage.TYPESCRIPT) {
        perFileCounts[f.path] = countMemberNames(f.content)
      }
    }
    rebuildAggregate()
  }

  fun updateFile(path: String, text: String, language: WorkerLanguage) {
    if (language == WorkerLanguage.JAVASCRIPT || language == WorkerLanguage.TYPESCRIPT) {
      perFileCounts[path] = countMemberNames(text)
    } else {
      perFileCounts.remove(path)
    }
    rebuildAggregate()
  }

  fun removeFile(path: String) {
    if (perFileCounts.remove(path) != null) rebuildAggregate()
  }

  fun usageCount(name: String): Int = aggregated[name] ?: 0

  fun topMembers(prefix: String, limit: Int = 12): List<Pair<String, Int>> {
    if (prefix.isEmpty()) return emptyList()
    return aggregated.entries
      .asSequence()
      .filter { it.key.startsWith(prefix, ignoreCase = true) && it.key != prefix }
      .sortedByDescending { it.value }
      .take(limit)
      .map { it.key to it.value }
      .toList()
  }

  private fun rebuildAggregate() {
    val total = HashMap<String, Int>()
    for (counts in perFileCounts.values) {
      for ((k, v) in counts) {
        total[k] = (total[k] ?: 0) + v
      }
    }
    aggregated = total
  }

  companion object {
    fun countMemberNames(text: CharSequence): Map<String, Int> {
      val counts = HashMap<String, Int>()
      val n = text.length
      var i = 0
      var lineStart = true
      while (i < n) {
        val c = text[i]
        if (lineStart) {
          var j = i
          while (j < n && (text[j] == ' ' || text[j] == '\t')) j++
          if (startsWithAt(text, j, "import ")) {
            i = skipLine(text, j)
            continue
          }
          lineStart = false
        }
        when {
          c == '\n' -> {
            lineStart = true
            i++
          }
          c == '/' && i + 1 < n && text[i + 1] == '/' -> i = skipLine(text, i)
          c == '/' && i + 1 < n && text[i + 1] == '*' -> {
            val end = indexOfSeq(text, "*/", i + 2)
            i = if (end < 0) n else end + 2
          }
          c == '"' || c == '\'' || c == '`' -> i = skipQuoted(text, i, c)
          c == '.' -> {
            var j = i + 1
            while (j < n && (text[j] == ' ' || text[j] == '\t')) j++
            if (j < n && (text[j].isLetter() || text[j] == '_' || text[j] == '$')) {
              val start = j
              while (j < n && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '$')) j++
              val name = text.subSequence(start, j).toString()
              counts[name] = (counts[name] ?: 0) + 1
              i = j
            } else {
              i++
            }
          }
          else -> i++
        }
      }
      return counts
    }

    private fun startsWithAt(text: CharSequence, offset: Int, prefix: String): Boolean {
      if (offset + prefix.length > text.length) return false
      for (k in prefix.indices) if (text[offset + k] != prefix[k]) return false
      return true
    }

    private fun indexOfSeq(text: CharSequence, needle: String, from: Int): Int {
      val limit = text.length - needle.length
      var i = from
      while (i <= limit) {
        if (startsWithAt(text, i, needle)) return i
        i++
      }
      return -1
    }

    private fun skipLine(text: CharSequence, from: Int): Int {
      var i = from
      while (i < text.length && text[i] != '\n') i++
      return i
    }

    private fun skipQuoted(text: CharSequence, from: Int, quote: Char): Int {
      var i = from + 1
      while (i < text.length) {
        val c = text[i]
        if (c == '\\') {
          i += 2
          continue
        }
        if (c == quote) return i + 1
        if (quote != '`' && c == '\n') return i
        i++
      }
      return i
    }
  }
}

/**
 * Resolves live-template macro variables adapted from CodeAssist MacroVariableResolver:
 * `$FILE$`, `$FILE_BASE$`, `$FUNCTION_NAME$`, `$LINE$`, `$DATE$`, `$TIME$`, `$UUID$`, `$INDENT$`, `$END$`.
 */
object EdgeMacroResolver {
  private val CONTROL_WORDS = setOf("if", "for", "while", "switch", "catch", "try")

  fun expand(
    template: String,
    fileName: String,
    documentText: CharSequence,
    offset: Int,
  ): Pair<String, Int> {
    val safeOffset = offset.coerceIn(0, documentText.length)
    val indent = leadingIndent(documentText, safeOffset)
    val indentedTemplate = template.replace("\n", "\n$indent")
    val now = Date()

    val resolved =
      Regex("""\$([A-Z_]+)\$""").replace(indentedTemplate) { m ->
        when (m.groupValues[1]) {
          "END" -> "\u0000"
          "FILE", "FILENAME" -> fileName
          "FILE_BASE", "CLASS" -> fileName.substringBeforeLast('.')
          "FUNCTION_NAME", "METHOD_NAME" -> enclosingFunctionName(documentText, safeOffset).ifBlank { "handler" }
          "LINE" -> (documentText.subSequence(0, safeOffset).count { it == '\n' } + 1).toString()
          "INDENT" -> indent
          "DATE" -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
          "TIME" -> SimpleDateFormat("HH:mm:ss", Locale.US).format(now)
          "UUID" -> UUID.randomUUID().toString()
          else -> m.value
        }
      }

    val caretMarker = resolved.indexOf('\u0000')
    return if (caretMarker >= 0) {
      val clean = resolved.removeRange(caretMarker, caretMarker + 1)
      clean to (clean.length - caretMarker)
    } else {
      resolved to 0
    }
  }

  fun leadingIndent(text: CharSequence, offset: Int): String {
    var lineStart = offset.coerceIn(0, text.length)
    while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
    val sb = StringBuilder()
    var i = lineStart
    while (i < text.length && (text[i] == ' ' || text[i] == '\t')) {
      sb.append(text[i])
      i++
    }
    return sb.toString()
  }

  private fun enclosingFunctionName(text: CharSequence, offset: Int): String {
    var depth = 0
    var i = offset - 1
    val limit = maxOf(0, offset - 32_000)
    while (i >= limit) {
      when (text[i]) {
        '}' -> depth++
        '{' ->
          if (depth > 0) depth--
          else extractDeclaredFn(text, i)?.let { return it }
      }
      i--
    }
    return ""
  }

  private fun extractDeclaredFn(text: CharSequence, braceIdx: Int): String? {
    var start = braceIdx
    while (start > 0 && text[start - 1] != ';' && text[start - 1] != '{' && text[start - 1] != '}') start--
    val header = text.subSequence(start, braceIdx).toString()
    val open = header.indexOf('(')
    if (open <= 0) return null
    val tokens = header.substring(0, open).trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    val name = tokens.lastOrNull()?.substringAfterLast('.') ?: return null
    if (name in CONTROL_WORDS) return null
    val valid = name.all { it.isLetterOrDigit() || it == '_' || it == '$' }
    return name.takeIf { valid && ("function" in tokens || "async" in tokens || tokens.size >= 1) }
  }
}

/**
 * Postfix templates contributor adapted directly from CodeAssist PostfixContributor.
 * Scans backwards over a balanced receiver expression before `.` so `expr.log`, `expr.await`,
 * `expr.json`, `expr.return`, `expr.if`, `expr.not`, `expr.const`, `expr.try`, `expr.str` rewrite in-place.
 */
object EdgePostfixContributor {
  private data class PostfixDef(
    val key: String,
    val example: String,
    val expand: (expr: String, indent: String) -> Pair<String, Int>,
  )

  private val templates =
    listOf(
      PostfixDef("log", "console.log(expr)") { e, _ -> "console.log($e);" to 0 },
      PostfixDef("await", "await expr") { e, _ -> "await $e" to 0 },
      PostfixDef("return", "return expr;") { e, _ -> "return $e;" to 0 },
      PostfixDef("json", "Response.json(expr)") { e, _ -> "Response.json($e)" to 0 },
      PostfixDef("str", "JSON.stringify(expr)") { e, _ -> "JSON.stringify($e)" to 0 },
      PostfixDef("not", "!expr") { e, _ -> "!$e" to 0 },
      PostfixDef("const", "const v = expr;") { e, _ -> "const value = $e;" to (e.length + 4) },
      PostfixDef("if", "if (expr) { }") { e, indent ->
        val out = "if ($e) {\n$indent  \n$indent}"
        out to (indent.length + 2)
      },
      PostfixDef("try", "try { expr } catch") { e, indent ->
        val out = "try {\n$indent  $e\n$indent} catch (err) {\n$indent  console.error(err);\n$indent}"
        out to 0
      },
      PostfixDef("len", "expr.length") { e, _ -> "$e.length" to 0 },
    )

  fun contribute(
    text: CharSequence,
    cursor: Int,
    language: WorkerLanguage,
  ): List<EdgeCompletionItem> {
    if (language != WorkerLanguage.JAVASCRIPT && language != WorkerLanguage.TYPESCRIPT) return emptyList()
    val safeCursor = cursor.coerceIn(0, text.length)
    var keyStart = safeCursor
    while (keyStart > 0 && (text[keyStart - 1].isLetterOrDigit() || text[keyStart - 1] == '_')) keyStart--
    val prefix = text.subSequence(keyStart, safeCursor).toString()
    if (prefix.isEmpty()) return emptyList()

    val dot = dotBefore(text, keyStart) ?: return emptyList()
    val recvStart = receiverStart(text, dot)
    if (recvStart >= dot) return emptyList()
    val receiver = text.subSequence(recvStart, dot).toString().trim()
    if (receiver.isEmpty() || receiver == "env" || receiver == "console" || receiver == "JSON" || receiver == "Math") {
      return emptyList()
    }

    val indent = EdgeMacroResolver.leadingIndent(text, recvStart)
    val out = ArrayList<EdgeCompletionItem>(4)
    for (t in templates) {
      if (!t.key.startsWith(prefix, ignoreCase = true)) continue
      val (expanded, cursorBack) = t.expand(receiver, indent)
      out.add(
        EdgeCompletionItem(
          label = ".${t.key}",
          insertText = expanded,
          kind = EdgeCompletionKind.POSTFIX,
          detail = t.example,
          cursorBackOffset = cursorBack,
          replaceStart = recvStart,
          replaceEnd = safeCursor,
          sortPriority = if (t.key.equals(prefix, ignoreCase = true)) -40 else 65,
        ),
      )
    }
    return out
  }

  private fun dotBefore(text: CharSequence, keyStart: Int): Int? {
    var i = keyStart - 1
    while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
    return if (i >= 0 && text[i] == '.') i else null
  }

  fun receiverStart(text: CharSequence, dot: Int): Int {
    var i = dot - 1
    while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
    var depth = 0
    while (i >= 0) {
      val c = text[i]
      when {
        c == ')' || c == ']' -> {
          depth++
          i--
        }
        c == '(' || c == '[' -> {
          if (depth == 0) return i + 1
          depth--
          i--
        }
        depth > 0 -> i--
        c == '"' || c == '\'' || c == '`' -> {
          val q = c
          i--
          while (i >= 0 && !(text[i] == q && (i == 0 || text[i - 1] != '\\'))) i--
          i--
        }
        c.isLetterOrDigit() || c == '_' || c == '$' || c == '.' -> i--
        else -> return i + 1
      }
    }
    return i + 1
  }
}

/**
 * Unified multi-contributor, multi-weigher completion engine modeled directly on CodeAssist CompletionEngine.
 */
class EdgeCompletionEngine(
  private val statsStore: CompletionStatsStore,
  private val memberUsageIndexer: MemberUsageIndexer,
) {
  private data class LiveTemplateDef(
    val abbrev: String,
    val detail: String,
    val template: String,
  )

  private val builtInTemplates =
    listOf(
      LiveTemplateDef(
        "fetch",
        "Worker fetch handler",
        "export default {\n  async fetch(request, env, ctx) {\n    \$END\$\n    return Response.json({ ok: true });\n  },\n};",
      ),
      LiveTemplateDef("rjson", "Response.json()", "return Response.json({ \$END\$ }, { status: 200 });"),
      LiveTemplateDef(
        "cors",
        "CORS headers",
        "const corsHeaders = {\n  \"Access-Control-Allow-Origin\": \"*\",\n  \"Access-Control-Allow-Methods\": \"GET, POST, OPTIONS\",\n  \"Access-Control-Allow-Headers\": \"Content-Type, Authorization\",\n};\$END\$",
      ),
      LiveTemplateDef("kvget", "KV get", "const value = await env.KV.get(\"\$END\$\");"),
      LiveTemplateDef("kvput", "KV put", "await env.KV.put(\"\$END\$\", JSON.stringify(data));"),
      LiveTemplateDef("d1q", "D1 query", "const { results } = await env.DB.prepare(\"SELECT * FROM \$END\$\").all();"),
      LiveTemplateDef("r2get", "R2 get", "const obj = await env.BUCKET.get(\"\$END\$\");"),
      LiveTemplateDef("tryc", "try / catch", "try {\n  \$END\$\n} catch (err) {\n  return Response.json({ error: String(err) }, { status: 500 });\n}"),
      LiveTemplateDef("afn", "async function", "async function \$END\$() {\n  \n}"),
      LiveTemplateDef("logf", "log file:fn", "console.log(\"[\$FILE\$:\$FUNCTION_NAME\$]\", \$END\$);"),
      LiveTemplateDef("todo", "TODO + date", "// TODO (\$DATE\$): \$END\$"),
      LiveTemplateDef("uuid", "random UUID", "\"\$UUID\$\"\$END\$"),
    )

  fun complete(
    workspace: WorkerWorkspace?,
    filePath: String,
    documentText: CharSequence,
    cursor: Int,
    language: WorkerLanguage,
    maxItems: Int = 18,
    customSnippets: List<CodeSnippetItem> = emptyList(),
  ): List<EdgeCompletionItem> {
    val isEmmetPath = EmmetExpander.isSupportedPath(filePath)
    if ((language == WorkerLanguage.TEXT && !isEmmetPath) || documentText.isEmpty()) return emptyList()
    val safeCursor = cursor.coerceIn(0, documentText.length)
    val wordBounds = extractWordBounds(documentText, safeCursor)
    val fullToken = documentText.subSequence(wordBounds.first, safeCursor).toString()
    val emmetAbbrev = if (isEmmetPath) EmmetExpander.extractAbbreviationAtCursor(documentText, safeCursor) else ""
    if (fullToken.isEmpty() && emmetAbbrev.isEmpty()) return emptyList()

    val isMemberAccess = fullToken.contains('.')
    val memberPrefix = if (isMemberAccess) fullToken.substringAfterLast('.') else fullToken
    val replaceStart = if (isMemberAccess) safeCursor - memberPrefix.length else wordBounds.first

    val raw = ArrayList<EdgeCompletionItem>(36)

    // 1. Workers APIs, env.* bindings & language keywords (order 0)
    WranglerSyncParser.buildWorkersCompletions(workspace, language, fullToken).forEachIndexed { idx, sug ->
      val isEnvBinding = sug.label.startsWith("env.")
      val itemReplaceStart =
        if (sug.label.startsWith(fullToken, ignoreCase = true)) wordBounds.first
        else replaceStart
      raw.add(
        EdgeCompletionItem(
          label = sug.label,
          insertText = sug.insertText,
          kind = if (isEnvBinding) EdgeCompletionKind.BINDING else EdgeCompletionKind.API,
          detail = sug.detail,
          cursorBackOffset = sug.cursorBackOffset,
          replaceStart = itemReplaceStart,
          replaceEnd = safeCursor,
          sortPriority = 10 + idx,
        ),
      )
    }

    // 2. Project-wide workspace symbols & member usage index (order 1000)
    if (language == WorkerLanguage.JAVASCRIPT || language == WorkerLanguage.TYPESCRIPT) {
      if (isMemberAccess && memberPrefix.isNotEmpty()) {
        memberUsageIndexer.topMembers(memberPrefix, limit = 10).forEach { (member, count) ->
          raw.add(
            EdgeCompletionItem(
              label = member,
              insertText = member,
              kind = EdgeCompletionKind.SYMBOL,
              detail = "${count}x",
              replaceStart = replaceStart,
              replaceEnd = safeCursor,
              sortPriority = 22,
            ),
          )
        }
      } else if (!isMemberAccess && workspace != null) {
        for (f in workspace.files) {
          if (f.language != WorkerLanguage.JAVASCRIPT && f.language != WorkerLanguage.TYPESCRIPT) continue
          val syms = WranglerSyncParser.extractCodeSymbols(f.content, f.language)
          for (s in syms) {
            val cleanName = s.name.removeSuffix("()")
            if (cleanName.contains(' ')) continue
            if (matchesQuery(cleanName, fullToken) && cleanName != fullToken) {
              raw.add(
                EdgeCompletionItem(
                  label = cleanName,
                  insertText = cleanName,
                  kind = if (s.kind == "fn" || s.kind == "handler") EdgeCompletionKind.FUNCTION else EdgeCompletionKind.SYMBOL,
                  detail = f.fileName,
                  replaceStart = wordBounds.first,
                  replaceEnd = safeCursor,
                  sortPriority = 18,
                ),
              )
            }
          }
        }
      }
    }

    // 3. Postfix templates (order 5000) — e.g. data.json, resp.await, err.log
    raw.addAll(EdgePostfixContributor.contribute(documentText, safeCursor, language))

    // 4. Built-in & Custom Snippets + Live Templates with MacroVariableResolver (order 6000)
    if (!isMemberAccess) {
      val fileName = filePath.substringAfterLast('/')
      val supportsRegistrySnippets =
        language == WorkerLanguage.JAVASCRIPT ||
          language == WorkerLanguage.TYPESCRIPT ||
          language == WorkerLanguage.HTML ||
          language == WorkerLanguage.CSS
      if (supportsRegistrySnippets) {
        CodeSnippetRegistry.allSnippets(customSnippets).forEach { snip ->
          val tagCompatible =
            when (language) {
              WorkerLanguage.HTML -> snip.isCustom || snip.tag.equals("HTML", ignoreCase = true)
              WorkerLanguage.CSS -> snip.isCustom || snip.tag.equals("CSS", ignoreCase = true)
              else -> snip.isCustom || (!snip.tag.equals("HTML", ignoreCase = true) && !snip.tag.equals("CSS", ignoreCase = true))
            }
          if (!tagCompatible) return@forEach
          if (!snip.abbrev.startsWith(fullToken, ignoreCase = true)) return@forEach
          val expanded =
            CodeSnippetRegistry.expandSnippet(
              rawCode = snip.code,
              fileName = fileName,
              documentText = documentText,
              offset = wordBounds.first,
            )
          raw.add(
            EdgeCompletionItem(
              label = snip.abbrev,
              insertText = expanded.text,
              kind = EdgeCompletionKind.TEMPLATE,
              detail = if (snip.isCustom) "${snip.title} (custom)" else snip.title,
              cursorBackOffset = expanded.cursorBackOffset,
              replaceStart = wordBounds.first,
              replaceEnd = safeCursor,
              sortPriority =
                when {
                  snip.abbrev.equals(fullToken, ignoreCase = true) && snip.isCustom -> -42
                  snip.abbrev.equals(fullToken, ignoreCase = true) -> -35
                  snip.isCustom -> 58
                  else -> 64
                },
            ),
          )
        }
      }
      if (language == WorkerLanguage.JAVASCRIPT || language == WorkerLanguage.TYPESCRIPT) {
        for (tpl in builtInTemplates) {
          if (!tpl.abbrev.startsWith(fullToken, ignoreCase = true)) continue
          val (expanded, cursorBack) = EdgeMacroResolver.expand(tpl.template, fileName, documentText, wordBounds.first)
          raw.add(
            EdgeCompletionItem(
              label = tpl.abbrev,
              insertText = expanded,
              kind = EdgeCompletionKind.TEMPLATE,
              detail = tpl.detail,
              cursorBackOffset = cursorBack,
              replaceStart = wordBounds.first,
              replaceEnd = safeCursor,
              sortPriority = if (tpl.abbrev.equals(fullToken, ignoreCase = true)) -35 else 64,
            ),
          )
        }
      }
    }

    // 5. Local buffer words (order 10000) — hippie completion from current file
    if (memberPrefix.length >= 2) {
      collectBufferWords(documentText, memberPrefix, safeCursor, limit = 10).forEach { w ->
        raw.add(
          EdgeCompletionItem(
            label = w,
            insertText = w,
            kind = EdgeCompletionKind.WORD,
            detail = "local",
            replaceStart = replaceStart,
            replaceEnd = safeCursor,
            sortPriority = 90,
          ),
        )
      }
    }

    if (isEmmetPath && emmetAbbrev.isNotEmpty()) {
      val lineIndent = EdgeMacroResolver.leadingIndent(documentText, safeCursor)
      EmmetExpander.expand(emmetAbbrev, filePath, lineIndent)?.let { em ->
        raw.add(
          EdgeCompletionItem(
            label = em.abbreviation,
            insertText = em.expandedText,
            kind = EdgeCompletionKind.TEMPLATE,
            detail = "Emmet",
            cursorBackOffset = em.cursorBackOffset,
            replaceStart = (safeCursor - em.replaceLength).coerceAtLeast(0),
            replaceEnd = safeCursor,
            sortPriority = -45,
          ),
        )
      }
      if (EmmetExpander.isCssLikePath(filePath) && !isMemberAccess) {
        EmmetExpander.suggestCssCompletions(emmetAbbrev).take(8).forEach { (abbr, css) ->
          if (!abbr.equals(emmetAbbrev, ignoreCase = true)) {
            raw.add(
              EdgeCompletionItem(
                label = abbr,
                insertText = css,
                kind = EdgeCompletionKind.TEMPLATE,
                detail = css,
                cursorBackOffset = if (css.endsWith("#;")) 1 else 0,
                replaceStart = (safeCursor - emmetAbbrev.length).coerceAtLeast(0),
                replaceEnd = safeCursor,
                sortPriority = 40,
              ),
            )
          }
        }
      }
    }

    if (raw.isEmpty()) return emptyList()

    val queryForRank =
      if (isEmmetPath && emmetAbbrev.isNotEmpty() && fullToken.isEmpty()) emmetAbbrev
      else if (isMemberAccess) memberPrefix
      else fullToken
    return rankItems(raw, queryForRank)
      .distinctBy { "${it.kind}:${it.label}:${it.insertText}" }
      .take(maxItems)
  }

  fun noteAccepted(item: EdgeCompletionItem) {
    statsStore.noteAccepted(item.label)
  }

  /**
   * Multi-weigher ranking mirroring CodeAssist CompletionEngine:
   * 1. ItemTierWeigher (tier 2: real symbols/bindings/exact postfix & macros; tier 1: fallback snippets/keywords; tier 0: buffer words)
   * 2. MatchGradeWeigher (exact > case-exact prefix > prefix > camelCase > substring)
   * 3. CompletionStats (persisted acceptance frequency)
   * 4. MemberUsageIndex (project-wide lexical member call count)
   * 5. SortPriority tiebreaker
   */
  private fun rankItems(items: List<EdgeCompletionItem>, query: String): List<EdgeCompletionItem> {
    val n = items.size
    val scores = DoubleArray(n)
    for (i in 0 until n) {
      val item = items[i]
      val tier =
        when {
          item.kind == EdgeCompletionKind.WORD -> 0.0
          (item.kind == EdgeCompletionKind.POSTFIX || item.kind == EdgeCompletionKind.TEMPLATE || item.kind == EdgeCompletionKind.KEYWORD) &&
            item.sortPriority > 0 -> 1.0
          else -> 2.0
        }
      val matchGrade = matchScore(item.label.removePrefix("."), query)
      val statsBoost = statsStore.countFor(CompletionStatsStore.keyOf(item.label)).coerceAtMost(50) * 1.5
      val usageBoost = memberUsageIndexer.usageCount(item.label).coerceAtMost(40) * 0.4
      scores[i] = tier * 10_000.0 + matchGrade * 1_000.0 + statsBoost + usageBoost - item.sortPriority * 0.1
    }
    return (0 until n).sortedByDescending { scores[it] }.map { items[it] }
  }

  private fun matchScore(candidate: String, query: String): Double {
    if (query.isEmpty()) return 0.0
    return when {
      candidate == query -> 5.0
      candidate.equals(query, ignoreCase = true) -> 4.5
      candidate.startsWith(query) -> 4.0
      candidate.startsWith(query, ignoreCase = true) -> 3.0
      matchesCamelCase(candidate, query) -> 2.2
      candidate.contains(query, ignoreCase = true) -> 1.0
      else -> 0.0
    }
  }

  private fun matchesQuery(candidate: String, query: String): Boolean {
    return candidate.startsWith(query, ignoreCase = true) ||
      matchesCamelCase(candidate, query) ||
      (query.length >= 2 && candidate.contains(query, ignoreCase = true))
  }

  private fun matchesCamelCase(candidate: String, query: String): Boolean {
    if (query.length < 2) return false
    var qi = 0
    for (i in candidate.indices) {
      val c = candidate[i]
      val isBoundary = i == 0 || c.isUpperCase() || candidate[i - 1] == '_' || candidate[i - 1] == '.'
      if (isBoundary && c.equals(query[qi], ignoreCase = true)) {
        qi++
        if (qi == query.length) return true
      }
    }
    return false
  }

  private fun extractWordBounds(text: CharSequence, cursor: Int): Pair<Int, Int> {
    val limit = maxOf(0, cursor - 48)
    var i = cursor
    while (i > limit) {
      val c = text[i - 1]
      if (c.isLetterOrDigit() || c == '_' || c == '.' || c == '$') i--
      else break
    }
    return i to cursor
  }

  private fun collectBufferWords(
    text: CharSequence,
    prefix: String,
    cursor: Int,
    limit: Int,
  ): List<String> {
    val seen = LinkedHashSet<String>(limit * 2)
    val maxScan = minOf(text.length, 32_000)
    var i = 0
    while (i < maxScan && seen.size < limit) {
      val c = text[i]
      if (c.isLetter() || c == '_' || c == '$') {
        val start = i
        i++
        while (i < maxScan && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '$')) i++
        val len = i - start
        if (len > prefix.length && !(cursor in start..i)) {
          val word = text.subSequence(start, i).toString()
          if (word.startsWith(prefix, ignoreCase = true)) {
            seen.add(word)
          }
        }
      } else {
        i++
      }
    }
    return seen.toList()
  }
}
