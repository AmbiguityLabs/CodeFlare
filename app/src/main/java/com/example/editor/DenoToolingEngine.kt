package com.example.editor

import android.content.Context
import com.example.data.remote.DiagnosticItem
import com.example.data.remote.DiagnosticSeverity
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerLanguage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class DenoFormatResult(
  val formatted: String,
  val usedDeno: Boolean,
  val note: String,
)

data class DenoCheckSummary(
  val diagnostics: List<DiagnosticItem>,
  val usedDeno: Boolean,
  val summaryMessage: String,
)

object DenoToolingEngine {
  private const val CHECK_CACHE_DIR = "edgecode_deno_check"
  private const val DENO_CACHE_DIR = "deno_cache"
  private const val COMMAND_TIMEOUT_SECONDS = 12L

  private val ANSI_REGEX = Regex("""\u001B\[[0-9;]*[A-Za-z]""")
  private val CHECK_ERROR_HEADER_REGEX = Regex("""^(TS\d+|error(?:\[[^\]]+\])?)\s*(?:\[(ERROR|WARNING)\])?:\s*(.+)$""", RegexOption.IGNORE_CASE)
  private val AT_LOCATION_REGEX = Regex("""^\s*at\s+(.+?):(\d+):(\d+)\s*$""")
  private val COLON_DIAG_REGEX = Regex("""^(.+?):(\d+):(\d+)\s*-\s*(error|warning|info)?\s*(?:TS\d+:\s*)?(.+)$""", RegexOption.IGNORE_CASE)

  fun stripAnsi(text: String): String = ANSI_REGEX.replace(text, "")

  fun normalizeWorkspacePath(
    rawPath: String,
    workspaceRootPrefix: String = "",
    validPaths: Set<String> = emptySet(),
  ): String {
    var clean = rawPath.trim().removePrefix("file://")
    // Normalize Windows/Unix slashes
    clean = clean.replace('\\', '/')
    val normRoot = workspaceRootPrefix.trim().removePrefix("file://").replace('\\', '/').trimEnd('/')
    if (normRoot.isNotEmpty() && clean.startsWith("$normRoot/")) {
      clean = clean.removePrefix("$normRoot/")
    }
    clean = clean.trimStart('/')
    if (clean in validPaths) return clean
    if (validPaths.isNotEmpty()) {
      validPaths.firstOrNull { candidate ->
        clean == candidate || clean.endsWith("/$candidate")
      }?.let { return it }
    }
    return clean
  }

  fun parseDenoLintJson(
    rawJson: String,
    workspaceRootPrefix: String = "",
    validPaths: Set<String> = emptySet(),
  ): List<DiagnosticItem> {
    val trimmed = rawJson.trim()
    val jsonStart = trimmed.indexOf('{')
    val jsonEnd = trimmed.lastIndexOf('}')
    if (jsonStart < 0 || jsonEnd <= jsonStart) return emptyList()
    val payload = trimmed.substring(jsonStart, jsonEnd + 1)

    return runCatching {
      val obj = JSONObject(payload)
      val out = ArrayList<DiagnosticItem>()
      val diagnostics = obj.optJSONArray("diagnostics")
      if (diagnostics != null) {
        for (i in 0 until diagnostics.length()) {
          val item = diagnostics.optJSONObject(i) ?: continue
          val filename = item.optString("filename", "")
          val path = normalizeWorkspacePath(filename, workspaceRootPrefix, validPaths)
          if (path.isBlank()) continue
          val code = item.optString("code", "").trim()
          val msg = item.optString("message", "").trim()
          val hint = item.optString("hint", "").trim()
          val range = item.optJSONObject("range")
          val start = range?.optJSONObject("start")
          val end = range?.optJSONObject("end")
          val line = (start?.optInt("line", 1) ?: 1).coerceAtLeast(1)
          // Deno lint JSON uses 0-based col for start/end.
          val startColZero = (start?.optInt("col", 0) ?: 0).coerceAtLeast(0)
          val endLine = end?.optInt("line", line) ?: line
          val endColZero = end?.optInt("col", startColZero + 1) ?: (startColZero + 1)
          val spanLength = if (endLine == line) (endColZero - startColZero).coerceAtLeast(1) else 1
          val formattedMsg =
            buildString {
              if (code.isNotEmpty()) append("[$code] ")
              append(msg.ifBlank { "Lint warning" })
              if (hint.isNotEmpty()) append(" — ").append(hint)
            }
          out.add(
            DiagnosticItem(
              filePath = path,
              line = line,
              column = startColZero + 1,
              message = formattedMsg,
              severity = DiagnosticSeverity.WARNING,
              length = spanLength,
            ),
          )
        }
      }
      val errors = obj.optJSONArray("errors")
      if (errors != null) {
        for (i in 0 until errors.length()) {
          val errObj = errors.optJSONObject(i) ?: continue
          val filePath = normalizeWorkspacePath(errObj.optString("file_path", ""), workspaceRootPrefix, validPaths)
          val message = errObj.optString("message", "").trim()
          if (filePath.isNotBlank() && message.isNotBlank()) {
            out.add(
              DiagnosticItem(
                filePath = filePath,
                line = 1,
                column = 1,
                message = message,
                severity = DiagnosticSeverity.ERROR,
                length = 1,
              ),
            )
          }
        }
      }
      out.distinctBy { listOf(it.filePath, it.line, it.column, it.message) }
    }.getOrDefault(emptyList())
  }

  fun parseDenoCheckOutput(
    rawText: String,
    workspaceRootPrefix: String = "",
    validPaths: Set<String> = emptySet(),
  ): List<DiagnosticItem> {
    val cleaned = stripAnsi(rawText)
    if (cleaned.isBlank()) return emptyList()
    val lines = cleaned.lines()
    val out = ArrayList<DiagnosticItem>()

    var pendingMessage: String? = null
    var pendingSeverity = DiagnosticSeverity.ERROR

    for (rawLine in lines) {
      val line = rawLine.trimEnd()
      val trimmed = line.trim()
      if (trimmed.isEmpty()) continue

      val colonMatch = COLON_DIAG_REGEX.find(trimmed)
      if (colonMatch != null) {
        val rawFile = colonMatch.groupValues[1]
        val ln = colonMatch.groupValues[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val col = colonMatch.groupValues[3].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val sevStr = colonMatch.groupValues[4].lowercase()
        val msg = colonMatch.groupValues[5].trim()
        val sev =
          when (sevStr) {
            "warning" -> DiagnosticSeverity.WARNING
            "info" -> DiagnosticSeverity.INFO
            else -> DiagnosticSeverity.ERROR
          }
        val normalized = normalizeWorkspacePath(rawFile, workspaceRootPrefix, validPaths)
        if (normalized.isNotBlank() && msg.isNotBlank()) {
          out.add(DiagnosticItem(normalized, ln, col, msg, sev))
        }
        pendingMessage = null
        continue
      }

      val headerMatch = CHECK_ERROR_HEADER_REGEX.find(trimmed)
      if (headerMatch != null) {
        val codeOrTag = headerMatch.groupValues[1]
        val sevWord = headerMatch.groupValues[2].uppercase()
        val msg = headerMatch.groupValues[3].trim()
        pendingSeverity = if (sevWord == "WARNING") DiagnosticSeverity.WARNING else DiagnosticSeverity.ERROR
        pendingMessage = if (codeOrTag.startsWith("TS", ignoreCase = true)) "$codeOrTag: $msg" else msg
        continue
      }

      val atMatch = AT_LOCATION_REGEX.find(trimmed)
      if (atMatch != null && pendingMessage != null) {
        val rawFile = atMatch.groupValues[1]
        val ln = atMatch.groupValues[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val col = atMatch.groupValues[3].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val normalized = normalizeWorkspacePath(rawFile, workspaceRootPrefix, validPaths)
        if (normalized.isNotBlank()) {
          out.add(DiagnosticItem(normalized, ln, col, pendingMessage, pendingSeverity))
        }
        pendingMessage = null
      }
    }

    return out.distinctBy { listOf(it.filePath, it.line, it.column, it.message) }
  }

  suspend fun formatFileWithDenoOrFallback(
    context: Context,
    filePath: String,
    content: String,
    language: WorkerLanguage,
    tabSize: Int = 2,
  ): DenoFormatResult = withContext(Dispatchers.IO) {
    val fallback = CodeFormatter.format(content, language, tabSize)
    val denoSupported =
      language == WorkerLanguage.JAVASCRIPT ||
        language == WorkerLanguage.TYPESCRIPT ||
        language == WorkerLanguage.JSON ||
        language == WorkerLanguage.CSS ||
        language == WorkerLanguage.HTML ||
        language == WorkerLanguage.MARKDOWN
    if (!denoSupported) {
      return@withContext DenoFormatResult(fallback, usedDeno = false, note = "Formatted with built-in engine")
    }

    val denoBinary = LocalScriptRunner.resolveDenoBinary(context)
    if (!denoBinary.exists()) {
      return@withContext DenoFormatResult(fallback, usedDeno = false, note = "Formatted locally")
    }
    if (!denoBinary.canExecute()) {
      runCatching { denoBinary.setExecutable(true, false) }
    }

    val ext = filePath.substringAfterLast('.', "").lowercase().ifBlank {
      when (language) {
        WorkerLanguage.TYPESCRIPT -> "ts"
        WorkerLanguage.JAVASCRIPT -> "js"
        WorkerLanguage.JSON -> "json"
        WorkerLanguage.CSS -> "css"
        WorkerLanguage.HTML -> "html"
        WorkerLanguage.MARKDOWN -> "md"
        else -> "ts"
      }
    }

    val result =
      runCatching {
        val denoCacheDir = File(context.cacheDir, DENO_CACHE_DIR).apply { mkdirs() }
        val pb =
          ProcessBuilder(
            denoBinary.absolutePath,
            "fmt",
            "--ext=$ext",
            "--indent-width=${tabSize.coerceIn(2, 8)}",
            "-",
          )
        configureDenoEnv(pb, context, denoCacheDir)
        val process = pb.start()
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
          writer.write(content)
          writer.flush()
        }
        val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
          process.destroyForcibly()
          return@runCatching null
        }
        if (process.exitValue() == 0 && stdout.isNotBlank()) stdout else null
      }.getOrNull()

    if (result != null) {
      DenoFormatResult(result, usedDeno = true, note = "Formatted with Deno")
    } else {
      DenoFormatResult(fallback, usedDeno = false, note = "Formatted locally")
    }
  }

  suspend fun runWorkspaceCheckAndLint(
    context: Context,
    files: List<WorkerFile>,
  ): DenoCheckSummary = withContext(Dispatchers.IO) {
    val editableFiles = files.filterNot { it.isVirtualReadOnly }
    val staticDiagnostics = CodeAnalyzer.analyzeWorkspace(editableFiles)
    val denoBinary = LocalScriptRunner.resolveDenoBinary(context)
    if (!denoBinary.exists()) {
      return@withContext DenoCheckSummary(
        diagnostics = staticDiagnostics,
        usedDeno = false,
        summaryMessage = buildSummaryLabel(staticDiagnostics, usedDeno = false),
      )
    }
    if (!denoBinary.canExecute()) {
      runCatching { denoBinary.setExecutable(true, false) }
    }

    val stageDir = File(context.cacheDir, CHECK_CACHE_DIR)
    LocalScriptRunner.stageWorkspaceFiles(stageDir, editableFiles, runnerSource = null)
    val denoCacheDir = File(context.cacheDir, DENO_CACHE_DIR).apply { mkdirs() }
    val validPaths = editableFiles.map { it.path }.toSet()
    val rootPrefix = stageDir.canonicalPath

    val scriptTargets =
      editableFiles
        .filter {
          it.language == WorkerLanguage.TYPESCRIPT || it.language == WorkerLanguage.JAVASCRIPT
        }
        .map { it.path.trimStart('/') }
        .filter { File(stageDir, it).isFile }

    if (scriptTargets.isEmpty()) {
      return@withContext DenoCheckSummary(
        diagnostics = staticDiagnostics,
        usedDeno = false,
        summaryMessage = buildSummaryLabel(staticDiagnostics, usedDeno = false),
      )
    }

    var ranDeno = false
    val denoDiagnostics = ArrayList<DiagnosticItem>()

    // 1. Run `deno lint --json`
    runCatching {
      val cmd = mutableListOf(denoBinary.absolutePath, "lint", "--json")
      cmd.addAll(scriptTargets)
      val pb = ProcessBuilder(cmd).directory(stageDir)
      configureDenoEnv(pb, context, denoCacheDir)
      val process = pb.start()
      val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
      val stderr = process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
      val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      if (!finished) {
        process.destroyForcibly()
      } else {
        ranDeno = true
        denoDiagnostics.addAll(parseDenoLintJson(stdout.ifBlank { stderr }, rootPrefix, validPaths))
      }
    }

    // 2. Run `deno check --no-lock` on TS/JS targets
    val tsTargets = scriptTargets.filter { it.endsWith(".ts", true) || it.endsWith(".tsx", true) || it.endsWith(".mts", true) }
    val checkTargets = tsTargets.ifEmpty { scriptTargets.take(4) }
    runCatching {
      val cmd = mutableListOf(denoBinary.absolutePath, "check", "--no-lock")
      cmd.addAll(checkTargets)
      val pb = ProcessBuilder(cmd).directory(stageDir)
      configureDenoEnv(pb, context, denoCacheDir)
      val process = pb.start()
      val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
      val stderr = process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
      val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      if (!finished) {
        process.destroyForcibly()
      } else {
        ranDeno = true
        val combined = "$stderr\n$stdout"
        denoDiagnostics.addAll(parseDenoCheckOutput(combined, rootPrefix, validPaths))
      }
    }

    val merged =
      (denoDiagnostics + staticDiagnostics)
        .distinctBy { listOf(it.filePath, it.line, it.column, it.message) }
        .sortedWith(compareBy<DiagnosticItem>({ it.severity.ordinal }, { it.filePath }, { it.line }, { it.column }))

    DenoCheckSummary(
      diagnostics = merged,
      usedDeno = ranDeno,
      summaryMessage = buildSummaryLabel(merged, usedDeno = ranDeno),
    )
  }

  private fun configureDenoEnv(
    pb: ProcessBuilder,
    context: Context,
    denoCacheDir: File,
  ) {
    val env = pb.environment()
    env["DENO_DIR"] = denoCacheDir.absolutePath
    env["HOME"] = context.filesDir.absolutePath
    env["NO_COLOR"] = "1"
    env["DENO_NO_UPDATE_CHECK"] = "1"
    val nativeDir = context.applicationInfo.nativeLibraryDir
    val existingLd = env["LD_LIBRARY_PATH"].orEmpty()
    env["LD_LIBRARY_PATH"] = if (existingLd.isBlank()) nativeDir else "$nativeDir:$existingLd"
  }

  private fun buildSummaryLabel(diagnostics: List<DiagnosticItem>, usedDeno: Boolean): String {
    val errors = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warnings = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }
    val engine = if (usedDeno) "Deno Check & Lint" else "Workspace Analysis"
    return when {
      errors == 0 && warnings == 0 -> "$engine passed — 0 issues"
      else -> "$engine: $errors error${if (errors == 1) "" else "s"}, $warnings warning${if (warnings == 1) "" else "s"}"
    }
  }
}
