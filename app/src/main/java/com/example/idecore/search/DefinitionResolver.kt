package com.example.idecore.search

import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.remote.WorkerWorkspace

data class DefinitionTarget(
  val filePath: String,
  val line: Int,
  val column: Int,
  val symbolName: String,
  val kindLabel: String,
)

object DefinitionResolver {
  private val CODE_EXTENSIONS = listOf(".ts", ".tsx", ".mts", ".cts", ".js", ".mjs", ".jsx", ".cts", ".json", ".css", ".html")
  private val INDEX_SUFFIXES = listOf("/index.ts", "/index.tsx", "/index.mts", "/index.js", "/index.mjs", "/index.jsx")

  private val IMPORT_FROM_REGEX = Regex("""(?:import|export)\s+[^;\n]*?\bfrom\s+["']([^"']+)["']""")
  private val SIDE_EFFECT_IMPORT_REGEX = Regex("""import\s+["']([^"']+)["']""")
  private val DYNAMIC_IMPORT_REGEX = Regex("""(?:import|require)\s*\(\s*["']([^"']+)["']\s*\)""")
  private val HTML_LINK_REGEX = Regex("""(?:src|href)\s*=\s*["']([^"']+)["']""")
  private val CSS_URL_REGEX = Regex("""(?:@import\s+["']([^"']+)["']|url\(\s*["']?([^"')]+)["']?\s*\))""")

  fun resolveAt(
    workspace: WorkerWorkspace?,
    editorFieldValues: Map<String, TextFieldValue> = emptyMap(),
    activeFilePath: String,
    documentText: String,
    offset: Int,
  ): DefinitionTarget? {
    val wsFiles = workspace?.files.orEmpty().filterNot { it.isVirtualReadOnly }
    val fileContents = LinkedHashMap<String, String>()
    wsFiles.forEach { wf ->
      fileContents[wf.path] = editorFieldValues[wf.path]?.text ?: wf.content
    }
    fileContents[activeFilePath] = documentText
    return resolveInFiles(
      files = fileContents.entries.map { it.key to it.value },
      activeFilePath = activeFilePath,
      documentText = documentText,
      offset = offset,
    )
  }

  fun resolveInFiles(
    files: List<Pair<String, String>>,
    activeFilePath: String,
    documentText: String,
    offset: Int,
  ): DefinitionTarget? {
    if (documentText.isEmpty() || files.isEmpty()) return null
    val safeOffset = offset.coerceIn(0, documentText.length)
    val allPaths = files.map { it.first }
    val fileMap = files.toMap()

    val (lineStart, lineEnd) = lineBoundsAt(documentText, safeOffset)
    val lineText = documentText.substring(lineStart, lineEnd)
    val colInLine = safeOffset - lineStart

    // 1. String path / ES module specifier on the current line (import/export/require/src/href/url)
    extractPathSpecifierAtColumn(lineText, colInLine)?.let { spec ->
      resolveModulePath(activeFilePath, spec, allPaths)?.let { resolvedPath ->
        return DefinitionTarget(
          filePath = resolvedPath,
          line = 1,
          column = 1,
          symbolName = spec,
          kindLabel = "file",
        )
      }
    }

    // 2. Identifier or env.<BINDING> at cursor
    val ident = identifierAt(documentText, safeOffset) ?: return null
    val prevEnvPrefix = checkEnvBindingPrefix(documentText, safeOffset, ident)
    if (prevEnvPrefix != null) {
      findWranglerBinding(files, prevEnvPrefix)?.let { return it }
    }

    // 3. Check if identifier is declared inside an explicitly imported local module
    resolveImportedModuleForSymbol(lineText, documentText, activeFilePath, ident, allPaths, fileMap)?.let {
      return it
    }

    // 4. Search declarations in active file first (skipping cursor's own exact declaration line only if there's a better match)
    val currentLineNumber = lineNumberAt(documentText, safeOffset)
    val sameFileHits = findDeclarationsInText(activeFilePath, documentText, ident)
    val exactOtherLine = sameFileHits.firstOrNull { it.line != currentLineNumber }
    if (exactOtherLine != null) return exactOtherLine

    // 5. Search remaining workspace files (preferring imported modules, then entry/other files)
    val importedPaths = collectImportedFilePaths(documentText, activeFilePath, allPaths).toSet()
    val orderedFiles =
      files
        .filter { it.first != activeFilePath }
        .sortedBy { if (it.first in importedPaths) 0 else 1 }

    for ((path, text) in orderedFiles) {
      val hits = findDeclarationsInText(path, text, ident)
      if (hits.isNotEmpty()) return hits.first()
    }

    // 6. Fallback: if cursor is on declaration itself in active file, return it
    return sameFileHits.firstOrNull()
  }

  private fun lineBoundsAt(text: String, offset: Int): Pair<Int, Int> {
    var start = offset.coerceIn(0, text.length)
    while (start > 0 && text[start - 1] != '\n') start--
    var end = offset.coerceIn(0, text.length)
    while (end < text.length && text[end] != '\n') end++
    return start to end
  }

  private fun lineNumberAt(text: String, offset: Int): Int {
    val clamped = offset.coerceIn(0, text.length)
    var line = 1
    for (i in 0 until clamped) {
      if (text[i] == '\n') line++
    }
    return line
  }

  private fun extractPathSpecifierAtColumn(lineText: String, colInLine: Int): String? {
    val patterns = listOf(IMPORT_FROM_REGEX, SIDE_EFFECT_IMPORT_REGEX, DYNAMIC_IMPORT_REGEX, HTML_LINK_REGEX, CSS_URL_REGEX)
    for (regex in patterns) {
      for (match in regex.findAll(lineText)) {
        val group =
          (1 until match.groups.size)
            .asSequence()
            .mapNotNull { match.groups[it] }
            .firstOrNull()
            ?: continue
        // Match either if caret is inside the quoted specifier or anywhere on an import line
        val isImportLine = regex == IMPORT_FROM_REGEX || regex == SIDE_EFFECT_IMPORT_REGEX
        if (colInLine in match.range || (isImportLine && match.range.first <= 12)) {
          return group.value.trim()
        }
      }
    }
    return null
  }

  fun resolveModulePath(
    fromFilePath: String,
    specifier: String,
    allPaths: List<String>,
  ): String? {
    val cleanSpec = specifier.trim().substringBefore('?').substringBefore('#')
    if (cleanSpec.isBlank() || cleanSpec.startsWith("http://") || cleanSpec.startsWith("https://")) {
      return null
    }

    val normalizedAll = allPaths.map { it.trimStart('/') to it }.toMap()
    val candidateBase =
      if (cleanSpec.startsWith("./") || cleanSpec.startsWith("../")) {
        val dirParts = fromFilePath.trimStart('/').split('/').dropLast(1).toMutableList()
        for (seg in cleanSpec.replace('\\', '/').split('/')) {
          when {
            seg.isBlank() || seg == "." -> Unit
            seg == ".." -> if (dirParts.isNotEmpty()) dirParts.removeAt(dirParts.lastIndex)
            else -> dirParts.add(seg)
          }
        }
        dirParts.joinToString("/")
      } else {
        cleanSpec.trimStart('/')
      }

    val probes = LinkedHashSet<String>()
    probes.add(candidateBase)
    CODE_EXTENSIONS.forEach { ext -> probes.add("$candidateBase$ext") }
    INDEX_SUFFIXES.forEach { suffix -> probes.add("$candidateBase$suffix") }

    for (probe in probes) {
      normalizedAll[probe]?.let { return it }
    }
    return null
  }

  private fun identifierAt(text: String, offset: Int): String? {
    var probe = offset.coerceIn(0, text.length)
    if (probe == text.length || (probe > 0 && !isIdentChar(text[probe]) && isIdentChar(text[probe - 1]))) {
      probe--
    }
    if (probe < 0 || probe >= text.length || !isIdentChar(text[probe])) return null
    var start = probe
    while (start > 0 && isIdentChar(text[start - 1])) start--
    var end = probe + 1
    while (end < text.length && isIdentChar(text[end])) end++
    val candidate = text.substring(start, end)
    return candidate.takeIf { it.isNotEmpty() && !it[0].isDigit() }
  }

  private fun checkEnvBindingPrefix(text: String, offset: Int, ident: String): String? {
    val idx = text.lastIndexOf(ident, startIndex = offset.coerceAtMost(text.length - 1))
    if (idx < 4) return null
    val prefix = text.substring((idx - 4).coerceAtLeast(0), idx)
    return if (prefix == "env." || prefix == "?.$ident") ident else null
  }

  private fun isIdentChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'

  private fun collectImportedFilePaths(
    documentText: String,
    activeFilePath: String,
    allPaths: List<String>,
  ): List<String> {
    val resolved = ArrayList<String>()
    for (m in IMPORT_FROM_REGEX.findAll(documentText)) {
      resolveModulePath(activeFilePath, m.groupValues[1], allPaths)?.let { resolved.add(it) }
    }
    for (m in DYNAMIC_IMPORT_REGEX.findAll(documentText)) {
      resolveModulePath(activeFilePath, m.groupValues[1], allPaths)?.let { resolved.add(it) }
    }
    return resolved
  }

  private fun resolveImportedModuleForSymbol(
    lineText: String,
    documentText: String,
    activeFilePath: String,
    ident: String,
    allPaths: List<String>,
    fileMap: Map<String, String>,
  ): DefinitionTarget? {
    // If caret is on `import { Foo } from "./mod"` or anywhere Foo is imported
    val importSpecRegex = Regex("""import\s+(?:type\s+)?(?:\{[^}]*\b${Regex.escape(ident)}\b[^}]*\}|${Regex.escape(ident)})\s+from\s+["']([^"']+)["']""")
    val match = importSpecRegex.find(lineText) ?: importSpecRegex.find(documentText)
    if (match != null) {
      val resolvedPath = resolveModulePath(activeFilePath, match.groupValues[1], allPaths)
      if (resolvedPath != null) {
        val targetText = fileMap[resolvedPath].orEmpty()
        findDeclarationsInText(resolvedPath, targetText, ident).firstOrNull()?.let { return it }
        return DefinitionTarget(resolvedPath, line = 1, column = 1, symbolName = ident, kindLabel = "module")
      }
    }
    return null
  }

  private fun findWranglerBinding(
    files: List<Pair<String, String>>,
    bindingName: String,
  ): DefinitionTarget? {
    val wrangler =
      files.firstOrNull { (path, _) ->
        val name = path.substringAfterLast('/').lowercase()
        name == "wrangler.jsonc" || name == "wrangler.json" || name == "wrangler.toml"
      } ?: return null
    val escaped = Regex.escape(bindingName)
    val regex = Regex("""(?:binding\s*[:=]\s*["']$escaped["']|["']$escaped["']\s*:)""")
    val lines = wrangler.second.lines()
    lines.forEachIndexed { idx, line ->
      val m = regex.find(line)
      if (m != null) {
        return DefinitionTarget(
          filePath = wrangler.first,
          line = idx + 1,
          column = m.range.first + 1,
          symbolName = bindingName,
          kindLabel = "binding",
        )
      }
    }
    return null
  }

  fun findDeclarationsInText(
    filePath: String,
    text: String,
    ident: String,
  ): List<DefinitionTarget> {
    val esc = Regex.escape(ident)
    val patterns =
      listOf(
        "fn" to Regex("""^\s*(?:export\s+(?:default\s+)?)?(?:async\s+)?function\s*\*?\s*($esc)\b"""),
        "class" to Regex("""^\s*(?:export\s+(?:default\s+)?)?(?:abstract\s+)?class\s+($esc)\b"""),
        "interface" to Regex("""^\s*(?:export\s+)?interface\s+($esc)\b"""),
        "type" to Regex("""^\s*(?:export\s+)?type\s+($esc)\s*="""),
        "enum" to Regex("""^\s*(?:export\s+)?enum\s+($esc)\b"""),
        "var" to Regex("""^\s*(?:export\s+)?(?:const|let|var)\s+($esc)\b"""),
        "method" to Regex("""^\s*(?:async\s+)?($esc)\s*\([^)]*\)\s*(?::\s*[^\{]+)?\s*\{"""),
        "prop" to Regex("""^\s*($esc)\s*:\s*(?:async\s+)?(?:function\b|\(|[^\n]+)"""),
      )

    val hits = ArrayList<DefinitionTarget>()
    val lines = text.lines()
    lines.forEachIndexed { idx, line ->
      val trimmed = line.trimStart()
      if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
      for ((kind, regex) in patterns) {
        val m = regex.find(line)
        if (m != null) {
          val symGroup = m.groups[1]
          val col = (symGroup?.range?.first ?: m.range.first) + 1
          hits.add(
            DefinitionTarget(
              filePath = filePath,
              line = idx + 1,
              column = col,
              symbolName = ident,
              kindLabel = kind,
            ),
          )
          break
        }
      }
    }
    return hits
  }
}
