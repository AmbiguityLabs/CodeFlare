package com.example.idecore.search

import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace
import com.example.editor.WranglerSyncParser

data class ProjectSymbolHit(
  val name: String,
  val kind: String,
  val filePath: String,
  val fileName: String,
  val line: Int,
  val score: Int,
)

data class ProjectSearchOptions(
  val caseSensitive: Boolean = false,
  val wholeWord: Boolean = false,
  val regex: Boolean = false,
)

data class ProjectTextMatch(
  val filePath: String,
  val fileName: String,
  val line: Int,
  val col: Int,
  val lineText: String,
  val matchStart: Int,
  val matchEnd: Int,
  val offset: Int,
)

data class RenamePreview(
  val oldName: String,
  val totalMatches: Int,
  val fileCounts: List<Pair<String, Int>>,
)

data class RenameApplyResult(
  val updatedContents: Map<String, String>,
  val totalReplacements: Int,
)

/**
 * Project-wide symbol search, regex/whole-word find-in-files, caret usage highlighter,
 * and lexical-safe multi-file symbol rename adapted from CodeAssist SearchService & RefactorService.
 */
object WorkspaceSearchAndRefactor {

  private const val MAX_SEARCH_FILE_CHARS = 1_500_000
  private val JS_KEYWORDS =
    setOf(
      "const", "let", "var", "function", "async", "await", "return", "export", "default",
      "import", "from", "if", "else", "for", "while", "switch", "case", "break", "continue",
      "try", "catch", "finally", "throw", "new", "class", "extends", "true", "false", "null",
      "undefined", "typeof", "instanceof", "interface", "type", "enum", "this", "super",
    )

  /** Fuzzy go-to-symbol across all workspace files and wrangler bindings. */
  fun searchSymbols(
    workspace: WorkerWorkspace?,
    liveFields: Map<String, TextFieldValue>,
    query: String,
    limit: Int = 80,
  ): List<ProjectSymbolHit> {
    if (workspace == null) return emptyList()
    val q = query.trim()
    val out = ArrayList<ProjectSymbolHit>(64)

    for (binding in workspace.bindings) {
      val s = fuzzyScore(binding.name, q)
      if (q.isEmpty() || s > 0) {
        out.add(
          ProjectSymbolHit(
            name = "env.${binding.name}",
            kind = "bind",
            filePath = "wrangler.jsonc",
            fileName = "wrangler.jsonc",
            line = 1,
            score = s + 5,
          ),
        )
      }
    }

    for (file in workspace.files) {
      if (file.language == WorkerLanguage.TEXT) continue
      val text = liveFields[file.path]?.text ?: file.content
      val syms = WranglerSyncParser.extractCodeSymbols(text, file.language)
      for (sym in syms) {
        val cleanName = sym.name.removeSuffix("()")
        val score = fuzzyScore(cleanName, q)
        if (q.isEmpty() || score > 0) {
          out.add(
            ProjectSymbolHit(
              name = cleanName,
              kind = sym.kind,
              filePath = file.path,
              fileName = file.fileName,
              line = sym.line,
              score = score,
            ),
          )
        }
      }
    }

    return out
      .sortedWith(compareByDescending<ProjectSymbolHit> { it.score }.thenBy { it.fileName }.thenBy { it.line })
      .take(limit)
  }

  /** Full-text Find in Files across the workspace, reading live unsaved editor buffers first. */
  fun findInFiles(
    workspace: WorkerWorkspace?,
    liveFields: Map<String, TextFieldValue>,
    query: String,
    options: ProjectSearchOptions,
    limit: Int = 250,
  ): List<ProjectTextMatch> {
    if (workspace == null || query.isBlank()) return emptyList()
    val regex = buildSearchRegex(query, options) ?: return emptyList()
    val out = ArrayList<ProjectTextMatch>(64)

    for (file in workspace.files) {
      val text = liveFields[file.path]?.text ?: file.content
      if (text.length > MAX_SEARCH_FILE_CHARS) continue
      var lineStart = 0
      var lineNo = 1
      var i = 0
      val n = text.length
      while (i <= n) {
        if (i == n || text[i] == '\n') {
          val line = text.substring(lineStart, i)
          for (m in regex.findAll(line)) {
            if (m.range.isEmpty()) continue
            out.add(
              ProjectTextMatch(
                filePath = file.path,
                fileName = file.fileName,
                line = lineNo,
                col = m.range.first + 1,
                lineText = line,
                matchStart = m.range.first,
                matchEnd = m.range.last + 1,
                offset = lineStart + m.range.first,
              ),
            )
            if (out.size >= limit) return out
          }
          lineStart = i + 1
          lineNo++
        }
        i++
      }
    }
    return out
  }

  /** Extracts the identifier under or touching [offset], or null if on whitespace/punctuation/keyword. */
  fun identifierAtOffset(text: CharSequence, offset: Int): String? {
    if (text.isEmpty()) return null
    var i = offset.coerceIn(0, text.length)
    if (i == text.length || !isIdentPart(text[i])) {
      if (i > 0 && isIdentPart(text[i - 1])) i-- else return null
    }
    var start = i
    while (start > 0 && isIdentPart(text[start - 1])) start--
    var end = i + 1
    while (end < text.length && isIdentPart(text[end])) end++
    if (start >= end || !isIdentStart(text[start])) return null
    val word = text.subSequence(start, end).toString()
    return word.takeIf { it.length >= 2 && it !in JS_KEYWORDS }
  }

  /**
   * Finds all lexical whole-word occurrences of [identifier] in [text], skipping comments and
   * string literals (safe for live caret usage highlighting and cross-file symbol rename).
   */
  fun findLexicalIdentifierRanges(
    text: CharSequence,
    identifier: String,
    language: WorkerLanguage,
    maxHits: Int = 500,
  ): List<IntRange> {
    if (identifier.isEmpty() || text.length < identifier.length) return emptyList()
    val out = ArrayList<IntRange>(16)
    val n = text.length
    val idLen = identifier.length
    val firstChar = identifier[0]
    var i = 0

    while (i < n && out.size < maxHits) {
      val c = text[i]
      if (language != WorkerLanguage.TEXT) {
        if (c == '/' && i + 1 < n && text[i + 1] == '/') {
          while (i < n && text[i] != '\n') i++
          continue
        }
        if (c == '/' && i + 1 < n && text[i + 1] == '*') {
          i += 2
          while (i + 1 < n && !(text[i] == '*' && text[i + 1] == '/')) i++
          i = (i + 2).coerceAtMost(n)
          continue
        }
        if (c == '"' || c == '\'' || c == '`') {
          val q = c
          i++
          while (i < n) {
            val ch = text[i]
            if (ch == '\\') {
              i += 2
              continue
            }
            if (ch == q) {
              i++
              break
            }
            if (q != '`' && ch == '\n') break
            i++
          }
          continue
        }
      }

      if (c == firstChar && i + idLen <= n) {
        val leftBoundary = i == 0 || !isIdentPart(text[i - 1])
        val rightBoundary = i + idLen == n || !isIdentPart(text[i + idLen])
        if (leftBoundary && rightBoundary && matchesAt(text, i, identifier)) {
          out.add(i until (i + idLen))
          i += idLen
          continue
        }
      }
      i++
    }
    return out
  }

  /** Previews how many occurrences of [oldName] will be renamed across the workspace. */
  fun previewRename(
    workspace: WorkerWorkspace?,
    liveFields: Map<String, TextFieldValue>,
    oldName: String,
  ): RenamePreview {
    if (workspace == null || !isValidIdentifier(oldName)) {
      return RenamePreview(oldName, 0, emptyList())
    }
    val counts = ArrayList<Pair<String, Int>>()
    var total = 0
    for (file in workspace.files) {
      if (file.isVirtualReadOnly || file.language == WorkerLanguage.TEXT) continue
      val text = liveFields[file.path]?.text ?: file.content
      if (!text.contains(oldName)) continue
      val hits = findLexicalIdentifierRanges(text, oldName, file.language, maxHits = 2_000)
      if (hits.isNotEmpty()) {
        counts.add(file.path to hits.size)
        total += hits.size
      }
    }
    return RenamePreview(oldName, total, counts)
  }

  /** Applies whole-word lexical-safe rename across all editable workspace files. */
  fun applyRename(
    files: List<WorkerFile>,
    liveFields: Map<String, TextFieldValue>,
    oldName: String,
    newName: String,
  ): RenameApplyResult {
    if (!isValidIdentifier(oldName) || !isValidIdentifier(newName) || oldName == newName) {
      return RenameApplyResult(emptyMap(), 0)
    }
    val updated = LinkedHashMap<String, String>()
    var total = 0
    for (file in files) {
      if (file.isVirtualReadOnly || file.language == WorkerLanguage.TEXT) continue
      val text = liveFields[file.path]?.text ?: file.content
      if (!text.contains(oldName)) continue
      val hits = findLexicalIdentifierRanges(text, oldName, file.language, maxHits = 5_000)
      if (hits.isEmpty()) continue
      val sb = StringBuilder(text.length + hits.size * (newName.length - oldName.length).coerceAtLeast(0))
      var last = 0
      for (r in hits) {
        sb.append(text, last, r.first)
        sb.append(newName)
        last = r.last + 1
      }
      sb.append(text, last, text.length)
      updated[file.path] = sb.toString()
      total += hits.size
    }
    return RenameApplyResult(updated, total)
  }

  fun isValidIdentifier(name: String): Boolean {
    if (name.isEmpty() || !isIdentStart(name[0])) return false
    return name.all { isIdentPart(it) } && name !in JS_KEYWORDS
  }

  private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_' || c == '$'

  private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'

  private fun matchesAt(text: CharSequence, offset: Int, target: String): Boolean {
    for (k in target.indices) {
      if (text[offset + k] != target[k]) return false
    }
    return true
  }

  private fun buildSearchRegex(query: String, options: ProjectSearchOptions): Regex? {
    val core =
      when {
        options.regex -> query
        options.wholeWord -> "\\b" + Regex.escape(query) + "\\b"
        else -> Regex.escape(query)
      }
    val opts = if (options.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
    return runCatching { Regex(core, opts) }.getOrNull()
  }

  private fun fuzzyScore(candidate: String, query: String): Int {
    if (query.isEmpty()) return 1
    if (candidate.equals(query, ignoreCase = true)) return 100
    if (candidate.startsWith(query, ignoreCase = true)) return 80
    val idx = candidate.indexOf(query, ignoreCase = true)
    if (idx >= 0) return (60 - idx).coerceAtLeast(30)
    // Subsequence / camelCase match
    var qi = 0
    var score = 0
    for (i in candidate.indices) {
      if (candidate[i].equals(query[qi], ignoreCase = true)) {
        val boundary = i == 0 || candidate[i].isUpperCase() || candidate[i - 1] == '_' || candidate[i - 1] == '.'
        score += if (boundary) 8 else 3
        qi++
        if (qi == query.length) return score.coerceAtLeast(10)
      }
    }
    return 0
  }
}
