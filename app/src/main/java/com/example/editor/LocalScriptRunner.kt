package com.example.editor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import com.example.data.remote.SharedHttpClientProvider
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class LocalRunLogEntry(
  val level: String,
  val message: String,
  val timestamp: String,
)

data class LocalScriptRunResult(
  val mode: String, // "WORKER" | "SCRIPT" | "STATIC" | "JS SANDBOX"
  val entryPath: String,
  val statusCode: Int,
  val statusText: String,
  val durationMs: Long,
  val headers: Map<String, String> = emptyMap(),
  val body: String = "",
  val isHtmlBody: Boolean = false,
  val logs: List<LocalRunLogEntry> = emptyList(),
  val errorMessage: String? = null,
  val ranAt: String = "",
)

object LocalScriptRunner {
  private const val DEFAULT_LOCAL_SERVER_PORT = 8080
  private const val READY_PREFIX = "__EDGECODE_READY__:"
  private const val RUNNER_ASSET_NAME = "universal_runner.ts"
  private const val WORKSPACE_CACHE_DIR = "edgecode_deno_workspace"
  private const val DENO_CACHE_DIR = "deno_cache"
  private const val STATE_DIR_NAME = "edgecode_local_runtime_state"

  private val httpClient: OkHttpClient by lazy {
    SharedHttpClientProvider.newBuilder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()
  }

  private val processLock = Any()
  @Volatile private var activeServerProcess: Process? = null
  @Volatile private var activeServerFingerprint: String? = null
  @Volatile private var activeServerPort: Int = DEFAULT_LOCAL_SERVER_PORT
  @Volatile private var activeLogBuffer: MutableList<LocalRunLogEntry>? = null

  fun allocateFreeLocalPort(fallbackPort: Int = DEFAULT_LOCAL_SERVER_PORT): Int =
    runCatching {
      ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { socket ->
        socket.localPort.takeIf { it > 0 } ?: fallbackPort
      }
    }.getOrDefault(fallbackPort)

  fun parseReadyWorkerPort(readyPayload: String): Int? {
    if (!readyPayload.startsWith("WORKER:")) return null
    return readyPayload.removePrefix("WORKER:").trim().toIntOrNull()?.takeIf { it in 1..65535 }
  }

  fun resolveDenoBinaryPath(nativeLibraryDir: String): String {
    val cleanDir = nativeLibraryDir.trimEnd('/', '\\')
    return "$cleanDir/libdeno.so"
  }

  fun resolveDenoBinary(context: Context): File =
    File(resolveDenoBinaryPath(context.applicationInfo.nativeLibraryDir))

  fun isPureDeclarationLibraryModule(content: String): Boolean {
    val stripped = content.trim()
    if (stripped.isEmpty()) return true
    if (stripped.contains("export default") || stripped.contains("console.") || stripped.contains("Deno.")) {
      return false
    }
    val nonCommentLines =
      stripped
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("/*") && !it.startsWith("*") }
        .toList()
    if (nonCommentLines.isEmpty()) return true
    return nonCommentLines.all { line ->
      line.startsWith("import ") ||
        line.startsWith("export const ") ||
        line.startsWith("export let ") ||
        line.startsWith("export type ") ||
        line.startsWith("export interface ") ||
        line.startsWith("export enum ") ||
        line.startsWith("export {") ||
        line.startsWith("export *")
    }
  }

  fun resolveTargetEntryPath(
    workspace: WorkerWorkspace,
    activeFilePath: String?,
  ): String {
    val editable = workspace.files.filter { !it.isVirtualReadOnly }
    val active = activeFilePath?.let { p -> editable.firstOrNull { it.path == p } }
    if (active != null) {
      val ext = active.path.substringAfterLast('.', "").lowercase()
      if (LocalWorkspacePreviewRenderer.isPreviewableFile(active.path)) {
        return active.path
      }
      if (ext in setOf("js", "mjs", "cjs", "ts", "mts", "cts", "tsx", "jsx")) {
        if (active.path == workspace.entryModulePath || !isPureDeclarationLibraryModule(active.content)) {
          return active.path
        }
      }
    }
    return workspace.entryModulePath
      .takeIf { p -> editable.any { it.path == p } }
      ?: editable.firstOrNull { it.isEntryPoint }?.path
      ?: editable.firstOrNull()?.path
      ?: "index.ts"
  }

  fun buildBindingsJson(bindings: List<CfWorkerBinding>): String {
    val arr = JSONArray()
    bindings.forEach { b ->
      arr.put(
        JSONObject()
          .put("name", b.name)
          .put("type", b.type)
          .put("text", b.text.orEmpty())
          .apply {
            b.className?.takeIf { it.isNotBlank() }?.let { put("className", it) }
            b.scriptName?.takeIf { it.isNotBlank() }?.let { put("scriptName", it) }
            b.queueName?.takeIf { it.isNotBlank() }?.let { put("queueName", it) }
            b.service?.takeIf { it.isNotBlank() }?.let { put("service", it) }
            b.environment?.takeIf { it.isNotBlank() }?.let { put("environment", it) }
          },
      )
    }
    return arr.toString()
  }

  fun synthesizeDenoImportMapJson(files: List<WorkerFile>): String? {
    val editable = files.filter { !it.isVirtualReadOnly }
    val hasExplicitDenoConfig =
      editable.any {
        val norm = it.path.trimStart('/').replace('\\', '/')
        norm == "deno.json" || norm == "deno.jsonc"
      }
    if (hasExplicitDenoConfig) return null

    val pkgFile =
      editable.firstOrNull { it.path.trimStart('/').replace('\\', '/') == "package.json" }
        ?: return null
    val pkgJson = runCatching { JSONObject(pkgFile.content) }.getOrNull() ?: return null
    val importsObj = JSONObject()
    listOf("dependencies", "devDependencies").forEach { section ->
      val deps = pkgJson.optJSONObject(section) ?: return@forEach
      val keys = deps.keys()
      while (keys.hasNext()) {
        val pkgName = keys.next().trim()
        val version = deps.optString(pkgName).trim()
        if (pkgName.isEmpty() || version.isEmpty() || version.startsWith("workspace:") || version.startsWith("file:")) {
          continue
        }
        val cleanVer = version.removePrefix("npm:").trim()
        val spec = if (cleanVer.isNotEmpty() && cleanVer != "*") "npm:$pkgName@$cleanVer" else "npm:$pkgName"
        if (!importsObj.has(pkgName)) {
          importsObj.put(pkgName, spec)
          importsObj.put("$pkgName/", "$spec/")
        }
      }
    }
    if (importsObj.length() == 0) return null
    return JSONObject().put("imports", importsObj).toString(2)
  }

  fun stageWorkspaceFiles(
    workspaceDir: File,
    files: List<WorkerFile>,
    runnerSource: String?,
  ): File? {
    if (!workspaceDir.exists()) {
      workspaceDir.mkdirs()
    }

    val rootCanonical = workspaceDir.canonicalFile
    val rootBoundary = rootCanonical.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
    val desiredFiles = linkedMapOf<File, String>()

    files.filter { !it.isVirtualReadOnly }.forEach { wf ->
      val rel = wf.path.trimStart('/').replace('\\', '/')
      if (rel.isBlank() || rel.contains("..")) return@forEach
      val target = File(workspaceDir, rel)
      val targetCanonical = runCatching { target.canonicalFile }.getOrNull() ?: return@forEach
      if (!targetCanonical.path.startsWith(rootBoundary)) return@forEach
      desiredFiles[targetCanonical] = wf.content
    }

    synthesizeDenoImportMapJson(files)?.let { importMapContent ->
      val denoCfg = File(workspaceDir, "deno.json").canonicalFile
      if (denoCfg.path.startsWith(rootBoundary) && denoCfg !in desiredFiles) {
        desiredFiles[denoCfg] = importMapContent
      }
    }

    // Remove stale workspace files from earlier runs (keep .edgecode_runner intact for incremental sync)
    val runnerDirCanonical = File(rootCanonical, ".edgecode_runner").canonicalFile
    workspaceDir.walkBottomUp().forEach { existing ->
      if (existing == workspaceDir) return@forEach
      val existingCanonical = runCatching { existing.canonicalFile }.getOrNull() ?: return@forEach
      if (existingCanonical == runnerDirCanonical || existingCanonical.path.startsWith(runnerDirCanonical.path + File.separator)) {
        return@forEach
      }
      if (existing.isFile && existingCanonical !in desiredFiles) {
        existing.delete()
      } else if (existing.isDirectory && existing.listFiles().isNullOrEmpty()) {
        existing.delete()
      }
    }

    // Write only new or modified files to minimize disk I/O between local runs
    desiredFiles.forEach { (target, content) ->
      target.parentFile?.mkdirs()
      val existingText = if (target.exists() && target.length() == content.toByteArray(Charsets.UTF_8).size.toLong()) {
        runCatching { target.readText(Charsets.UTF_8) }.getOrNull()
      } else {
        null
      }
      if (existingText != content) {
        target.writeText(content, Charsets.UTF_8)
      }
    }

    if (runnerSource != null) {
      val runnerFile = File(workspaceDir, ".edgecode_runner/$RUNNER_ASSET_NAME")
      runnerFile.parentFile?.mkdirs()
      val prevRunner = if (runnerFile.exists()) runCatching { runnerFile.readText(Charsets.UTF_8) }.getOrNull() else null
      if (prevRunner != runnerSource) {
        runnerFile.writeText(runnerSource, Charsets.UTF_8)
      }
      return runnerFile
    }
    return null
  }

  fun stopActiveServer() {
    synchronized(processLock) {
      runCatching { activeServerProcess?.destroyForcibly() }
      activeServerProcess = null
      activeServerFingerprint = null
      activeServerPort = DEFAULT_LOCAL_SERVER_PORT
      activeLogBuffer = null
    }
  }

  suspend fun evalSnippet(
    context: Context,
    workspace: WorkerWorkspace?,
    snippet: String,
    bindings: List<CfWorkerBinding> = workspace?.bindings.orEmpty(),
  ): LocalScriptRunResult =
    withContext(Dispatchers.IO) {
      val startedAtMs = System.currentTimeMillis()
      val ranAt = nowTime()
      val cleanSnippet = snippet.trim()
      if (cleanSnippet.isEmpty()) {
        return@withContext LocalScriptRunResult(
          mode = "SCRIPT",
          entryPath = "repl",
          statusCode = 400,
          statusText = "EMPTY",
          durationMs = 1L,
          errorMessage = "Enter a JS/TS expression or command.",
          ranAt = ranAt,
        )
      }

      val denoBinary = resolveDenoBinary(context)
      if (!denoBinary.exists()) {
        return@withContext executeInWebViewSandbox(
          context = context,
          entryPath = "repl",
          code = cleanSnippet,
          isExpression = !cleanSnippet.contains(';') && !cleanSnippet.contains('\n'),
          startedAtMs = startedAtMs,
          ranAt = ranAt,
        )
      }
      if (!denoBinary.canExecute()) {
        runCatching { denoBinary.setExecutable(true, false) }
      }

      val runnerSource =
        runCatching {
          context.assets.open(RUNNER_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { err ->
          return@withContext LocalScriptRunResult(
            mode = "SCRIPT",
            entryPath = "repl",
            statusCode = 500,
            statusText = "ASSET ERROR",
            durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
            errorMessage = "Missing $RUNNER_ASSET_NAME: ${err.localizedMessage}",
            ranAt = ranAt,
          )
        }

      val workspaceDir = File(context.cacheDir, WORKSPACE_CACHE_DIR)
      val runnerFile = stageWorkspaceFiles(workspaceDir, workspace?.files.orEmpty(), runnerSource)
      val denoCacheDir = File(context.cacheDir, DENO_CACHE_DIR).apply { mkdirs() }
      if (runnerFile == null) {
        return@withContext LocalScriptRunResult(
          mode = "SCRIPT",
          entryPath = "repl",
          statusCode = 500,
          statusText = "STAGE ERROR",
          durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
          errorMessage = "Could not stage universal runner",
          ranAt = ranAt,
        )
      }

      val pb =
        ProcessBuilder(
          denoBinary.absolutePath,
          "run",
          "--allow-all",
          "--no-prompt",
          runnerFile.absolutePath,
        )
      val stateDir = File(context.filesDir, STATE_DIR_NAME).apply { mkdirs() }
      pb.directory(workspaceDir)
      pb.environment().apply {
        put("EDGECODE_EVAL_SNIPPET", cleanSnippet)
        put("EDGECODE_BINDINGS_JSON", buildBindingsJson(bindings))
        put("EDGECODE_STATE_DIR", stateDir.absolutePath)
        put("DENO_DIR", denoCacheDir.absolutePath)
        put("HOME", context.cacheDir.absolutePath)
        put("NO_COLOR", "1")
        put("DENO_NO_UPDATE_CHECK", "1")
        val existingLd = get("LD_LIBRARY_PATH").orEmpty()
        val nativeDir = context.applicationInfo.nativeLibraryDir
        put("LD_LIBRARY_PATH", if (existingLd.isBlank()) nativeDir else "$nativeDir:$existingLd")
      }

      val process =
        runCatching { pb.start() }.getOrElse { err ->
          return@withContext LocalScriptRunResult(
            mode = "SCRIPT",
            entryPath = "repl",
            statusCode = 500,
            statusText = "EXEC ERROR",
            durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
            errorMessage = "Failed to start libdeno.so: ${err.localizedMessage}",
            ranAt = ranAt,
          )
        }

      val logBuffer = Collections.synchronizedList(mutableListOf<LocalRunLogEntry>())
      val stdoutLines = Collections.synchronizedList(mutableListOf<String>())
      val stderrLines = Collections.synchronizedList(mutableListOf<String>())

      val outThread =
        Thread {
          runCatching {
            BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { r ->
              while (true) {
                val line = r.readLine() ?: break
                if (!line.startsWith(READY_PREFIX)) {
                  stdoutLines.add(line)
                  logBuffer.add(LocalRunLogEntry("LOG", line, nowTime()))
                }
              }
            }
          }
        }.apply { isDaemon = true; start() }

      val errThread =
        Thread {
          runCatching {
            BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)).use { r ->
              while (true) {
                val line = r.readLine() ?: break
                stderrLines.add(line)
                logBuffer.add(LocalRunLogEntry("ERR", line, nowTime()))
              }
            }
          }
        }.apply { isDaemon = true; start() }

      val finished = process.waitFor(10, TimeUnit.SECONDS)
      if (!finished) process.destroyForcibly()
      runCatching { outThread.join(300) }
      runCatching { errThread.join(300) }

      val exitCode = if (finished) runCatching { process.exitValue() }.getOrDefault(1) else 124
      val outText = stdoutLines.joinToString("\n")
      val errText = stderrLines.joinToString("\n")

      LocalScriptRunResult(
        mode = "SCRIPT",
        entryPath = "repl",
        statusCode = if (exitCode == 0) 200 else 500,
        statusText = if (exitCode == 0) "EVAL OK" else "EXIT $exitCode",
        durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
        headers = mapOf("content-type" to "text/plain; charset=utf-8"),
        body = outText.ifBlank { errText },
        logs = logBuffer.toList(),
        errorMessage = if (exitCode != 0) errText.ifBlank { outText.ifBlank { "Eval exited $exitCode" } } else null,
        ranAt = ranAt,
      )
    }

  suspend fun runWorkspace(
    context: Context,
    workspace: WorkerWorkspace,
    activeFilePath: String?,
    requestPath: String = "/",
    method: String = "GET",
    headersJson: String = "{}",
    body: String = "",
    isDarkTheme: Boolean = true,
  ): LocalScriptRunResult =
    withContext(Dispatchers.IO) {
      val startedAtMs = System.currentTimeMillis()
      val ranAt = nowTime()
      val entryPath = resolveTargetEntryPath(workspace, activeFilePath)

      if (LocalWorkspacePreviewRenderer.isPreviewableFile(entryPath)) {
        val file = workspace.files.firstOrNull { it.path == entryPath }
        val ext = entryPath.substringAfterLast('.', "").lowercase()
        val content =
          when {
            file == null -> ""
            ext in setOf("md", "markdown", "mdx") ->
              LocalWorkspacePreviewRenderer.renderMarkdownToHtml(file.path, file.content, isDarkTheme)
            else -> file.content
          }
        val mime =
          if (ext in setOf("md", "markdown", "mdx")) "text/html"
          else LocalWorkspacePreviewRenderer.inferBrowserMimeType(entryPath)
        val dur = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
        return@withContext LocalScriptRunResult(
          mode = "STATIC",
          entryPath = entryPath,
          statusCode = 200,
          statusText = "OK",
          durationMs = dur,
          headers = mapOf("content-type" to mime),
          body = content,
          isHtmlBody = mime.contains("html", ignoreCase = true) || ext == "svg",
          logs = listOf(LocalRunLogEntry("INFO", entryPath, ranAt)),
          ranAt = ranAt,
        )
      }

      val denoBinary = resolveDenoBinary(context)
      if (!denoBinary.exists()) {
        val activeCode = workspace.files.firstOrNull { it.path == entryPath }?.content.orEmpty()
        val ext = entryPath.substringAfterLast('.', "").lowercase()
        if (ext in setOf("js", "mjs", "cjs")) {
          return@withContext executeInWebViewSandbox(
            context = context,
            entryPath = entryPath,
            code = activeCode,
            isExpression = false,
            startedAtMs = startedAtMs,
            ranAt = ranAt,
          )
        }
        return@withContext LocalScriptRunResult(
          mode = "SCRIPT",
          entryPath = entryPath,
          statusCode = 500,
          statusText = "DENO NEEDED",
          durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
          logs = listOf(
            LocalRunLogEntry("INFO", "libdeno.so not present in local build; needed for TS/Deno execution.", ranAt),
          ),
          errorMessage =
            "Embedded Deno runtime (libdeno.so) is not packaged in this local build. Pure .js files can run via WebView JS Sandbox; CI release APKs automatically bundle arm64-v8a libdeno.so.",
          ranAt = ranAt,
        )
      }
      if (!denoBinary.canExecute()) {
        runCatching { denoBinary.setExecutable(true, false) }
      }

      val bindingsJson = buildBindingsJson(workspace.bindings)
      val fingerprint = computeFingerprint(entryPath, workspace.files, bindingsJson)
      val cleanPath = requestPath.trim().let { if (it.startsWith("/")) it else "/$it" }

      // Reuse already-running Deno Worker HTTP server if code and bindings are unchanged
      val existingProc = synchronized(processLock) {
        if (activeServerFingerprint == fingerprint && activeServerProcess?.isAlive == true) {
          activeServerProcess
        } else null
      }
      if (existingProc != null) {
        val logSnap = synchronized(processLock) { activeLogBuffer?.toList().orEmpty() }
        val forwardResult =
          forwardHttpRequestToLocalServer(
            entryPath = entryPath,
            cleanPath = cleanPath,
            method = method,
            headersJson = headersJson,
            body = body,
            logs = logSnap,
            startedAtMs = startedAtMs,
            ranAt = ranAt,
          )
        if (forwardResult != null) return@withContext forwardResult
      }

      stopActiveServer()

      val runnerSource =
        runCatching {
          context.assets.open(RUNNER_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { err ->
          return@withContext LocalScriptRunResult(
            mode = "SCRIPT",
            entryPath = entryPath,
            statusCode = 500,
            statusText = "ASSET ERROR",
            durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
            errorMessage = "Missing $RUNNER_ASSET_NAME in assets: ${err.localizedMessage}",
            ranAt = ranAt,
          )
        }

      val workspaceDir = File(context.cacheDir, WORKSPACE_CACHE_DIR)
      val runnerFile = stageWorkspaceFiles(workspaceDir, workspace.files, runnerSource)
      val entryFile = File(workspaceDir, entryPath.trimStart('/'))
      val denoCacheDir = File(context.cacheDir, DENO_CACHE_DIR).apply { mkdirs() }

      if (runnerFile == null || !entryFile.exists()) {
        return@withContext LocalScriptRunResult(
          mode = "SCRIPT",
          entryPath = entryPath,
          statusCode = 404,
          statusText = "NOT FOUND",
          durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
          errorMessage = "Entry script not found: $entryPath",
          ranAt = ranAt,
        )
      }

      val targetPort = allocateFreeLocalPort()
      val stateDir = File(context.filesDir, STATE_DIR_NAME).apply { mkdirs() }
      val pb =
        ProcessBuilder(
          denoBinary.absolutePath,
          "run",
          "--allow-all",
          "--no-prompt",
          runnerFile.absolutePath,
        )
      pb.directory(workspaceDir)
      pb.environment().apply {
        put("EDGECODE_ENTRY_FILE", entryFile.absolutePath)
        put("EDGECODE_BINDINGS_JSON", bindingsJson)
        put("EDGECODE_STATE_DIR", stateDir.absolutePath)
        put("EDGECODE_PORT", targetPort.toString())
        put("EDGECODE_HOST", "127.0.0.1")
        put("DENO_DIR", denoCacheDir.absolutePath)
        put("HOME", context.cacheDir.absolutePath)
        put("NO_COLOR", "1")
        put("DENO_NO_UPDATE_CHECK", "1")
        val existingLd = get("LD_LIBRARY_PATH").orEmpty()
        val nativeDir = context.applicationInfo.nativeLibraryDir
        put("LD_LIBRARY_PATH", if (existingLd.isBlank()) nativeDir else "$nativeDir:$existingLd")
      }

      val process =
        runCatching { pb.start() }.getOrElse { err ->
          return@withContext LocalScriptRunResult(
            mode = "SCRIPT",
            entryPath = entryPath,
            statusCode = 500,
            statusText = "EXEC ERROR",
            durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
            errorMessage = "Failed to start libdeno.so: ${err.localizedMessage}",
            ranAt = ranAt,
          )
        }

      val logBuffer = Collections.synchronizedList(mutableListOf<LocalRunLogEntry>())
      val stdoutLines = Collections.synchronizedList(mutableListOf<String>())
      val stderrLines = Collections.synchronizedList(mutableListOf<String>())
      val readyLatch = CountDownLatch(1)
      val detectedMode = AtomicReference("SCRIPT")

      val stdoutThread =
        Thread(
          {
            runCatching {
              BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                  val line = reader.readLine() ?: break
                  if (line.startsWith(READY_PREFIX)) {
                    val payload = line.removePrefix(READY_PREFIX)
                    detectedMode.set(if (payload.startsWith("WORKER")) "WORKER" else "SCRIPT")
                    parseReadyWorkerPort(payload)?.let { activeServerPort = it }
                    readyLatch.countDown()
                  } else {
                    stdoutLines.add(line)
                    logBuffer.add(LocalRunLogEntry("LOG", line, nowTime()))
                  }
                }
              }
            }
            readyLatch.countDown()
          },
          "edgecode-deno-stdout",
        ).apply {
          isDaemon = true
          start()
        }

      val stderrThread =
        Thread(
          {
            runCatching {
              BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                  val line = reader.readLine() ?: break
                  stderrLines.add(line)
                  logBuffer.add(LocalRunLogEntry("ERR", line, nowTime()))
                }
              }
            }
          },
          "edgecode-deno-stderr",
        ).apply {
          isDaemon = true
          start()
        }

      val signaled = readyLatch.await(15, TimeUnit.SECONDS)

      if (detectedMode.get() == "WORKER" && process.isAlive) {
        synchronized(processLock) {
          activeServerProcess = process
          activeServerFingerprint = fingerprint
          activeLogBuffer = logBuffer
        }
        val forwardResult =
          forwardHttpRequestToLocalServer(
            entryPath = entryPath,
            cleanPath = cleanPath,
            method = method,
            headersJson = headersJson,
            body = body,
            logs = logBuffer.toList(),
            startedAtMs = startedAtMs,
            ranAt = ranAt,
          )
        if (forwardResult != null) return@withContext forwardResult
      }

      if (signaled && process.isAlive) {
        process.waitFor(5, TimeUnit.SECONDS)
      } else if (!signaled && process.isAlive) {
        process.destroyForcibly()
        return@withContext LocalScriptRunResult(
          mode = "SCRIPT",
          entryPath = entryPath,
          statusCode = 408,
          statusText = "TIMEOUT",
          durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
          logs = logBuffer.toList(),
          errorMessage = "Deno execution timed out (15s)",
          ranAt = ranAt,
        )
      }

      runCatching { stdoutThread.join(400) }
      runCatching { stderrThread.join(400) }

      val exitCode = runCatching { process.exitValue() }.getOrDefault(if (process.isAlive) 1 else 0)
      val outText = stdoutLines.joinToString("\n")
      val errText = stderrLines.joinToString("\n")

      LocalScriptRunResult(
        mode = "SCRIPT",
        entryPath = entryPath,
        statusCode = if (exitCode == 0) 200 else 500,
        statusText = if (exitCode == 0) "OK" else "EXIT $exitCode",
        durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
        headers = mapOf("content-type" to "text/plain; charset=utf-8"),
        body = outText.ifBlank { errText },
        isHtmlBody = false,
        logs = logBuffer.toList(),
        errorMessage = if (exitCode != 0) errText.ifBlank { outText.ifBlank { "Deno exited with code $exitCode" } } else null,
        ranAt = ranAt,
      )
    }

  private fun forwardHttpRequestToLocalServer(
    entryPath: String,
    cleanPath: String,
    method: String,
    headersJson: String,
    body: String,
    logs: List<LocalRunLogEntry>,
    startedAtMs: Long,
    ranAt: String,
  ): LocalScriptRunResult? {
    val upperMethod = method.uppercase().ifBlank { "GET" }
    val headersBuilder = Headers.Builder()
    runCatching { JSONObject(headersJson.ifBlank { "{}" }) }.getOrNull()?.let { obj ->
      obj.keys().forEach { k -> headersBuilder.add(k, obj.optString(k)) }
    }
    val reqBody =
      if (upperMethod in setOf("GET", "HEAD")) {
        null
      } else {
        val ctype = headersBuilder.get("Content-Type") ?: "application/json; charset=utf-8"
        body.toRequestBody(ctype.toMediaTypeOrNull())
      }

    val targetPort = activeServerPort.takeIf { it in 1..65535 } ?: DEFAULT_LOCAL_SERVER_PORT
    val request =
      Request.Builder()
        .url("http://127.0.0.1:$targetPort$cleanPath")
        .headers(headersBuilder.build())
        .method(upperMethod, reqBody)
        .build()

    return runCatching {
      httpClient.newCall(request).execute().use { resp ->
        val outHeaders = linkedMapOf<String, String>()
        resp.headers.names().forEach { name ->
          outHeaders[name.lowercase()] = resp.headers.values(name).joinToString(", ")
        }
        val outBody = resp.body?.string().orEmpty()
        val ctype = outHeaders["content-type"].orEmpty()
        val isHtml =
          ctype.contains("text/html", ignoreCase = true) ||
            outBody.trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) ||
            outBody.trimStart().startsWith("<html", ignoreCase = true)
        LocalScriptRunResult(
          mode = "WORKER",
          entryPath = entryPath,
          statusCode = resp.code,
          statusText = resp.message.ifBlank { "OK" },
          durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
          headers = outHeaders,
          body = outBody,
          isHtmlBody = isHtml,
          logs = synchronized(processLock) { activeLogBuffer?.toList() } ?: logs,
          errorMessage = if (resp.code >= 500 && outBody.isNotBlank()) outBody else null,
          ranAt = ranAt,
        )
      }
    }.getOrNull()
  }

  fun buildSandboxScriptWrapper(code: String, isExpression: Boolean): String {
    val bodyCode =
      if (isExpression) {
        "return ($code);"
      } else {
        code
      }
    val quotedCode = JSONObject.quote(bodyCode)
    return """
      (function() {
        var __logs = [];
        var __origConsole = {
          log: console.log,
          info: console.info,
          warn: console.warn,
          error: console.error
        };
        function __fmt(args) {
          return Array.prototype.slice.call(args).map(function(a) {
            if (typeof a === 'string') return a;
            try { return JSON.stringify(a); } catch (_e) { return String(a); }
          }).join(' ');
        }
        console.log = function() { __logs.push({ l: 'LOG', m: __fmt(arguments) }); };
        console.info = function() { __logs.push({ l: 'INFO', m: __fmt(arguments) }); };
        console.warn = function() { __logs.push({ l: 'INFO', m: '[WARN] ' + __fmt(arguments) }); };
        console.error = function() { __logs.push({ l: 'ERR', m: __fmt(arguments) }); };
        var __ret = undefined;
        var __err = null;
        try {
          var __fn = new Function($quotedCode);
          __ret = __fn();
        } catch (e) {
          __err = (e && e.stack) ? String(e.stack) : String(e);
        } finally {
          console.log = __origConsole.log;
          console.info = __origConsole.info;
          console.warn = __origConsole.warn;
          console.error = __origConsole.error;
        }
        var __retStr = '';
        if (__ret !== undefined) {
          if (typeof __ret === 'string') __retStr = __ret;
          else { try { __retStr = JSON.stringify(__ret, null, 2); } catch (_e) { __retStr = String(__ret); } }
        }
        return JSON.stringify({ logs: __logs, result: __retStr, error: __err });
      })()
    """.trimIndent()
  }

  fun parseSandboxResultJson(
    rawEvalReturn: String?,
    entryPath: String,
    startedAtMs: Long,
    ranAt: String,
  ): LocalScriptRunResult {
    val dur = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
    if (rawEvalReturn.isNullOrBlank() || rawEvalReturn == "null") {
      return LocalScriptRunResult(
        mode = "JS SANDBOX",
        entryPath = entryPath,
        statusCode = 500,
        statusText = "SANDBOX EMPTY",
        durationMs = dur,
        errorMessage = "WebView JS Sandbox returned empty result.",
        ranAt = ranAt,
      )
    }
    val jsonText =
      runCatching {
        val trimmed = rawEvalReturn.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
          JSONArray("[$trimmed]").getString(0)
        } else {
          trimmed
        }
      }.getOrDefault(rawEvalReturn)

    return runCatching {
      val obj = JSONObject(jsonText)
      val logsArr = obj.optJSONArray("logs") ?: JSONArray()
      val logs =
        buildList {
          add(LocalRunLogEntry("INFO", "Executed in WebView JS Sandbox (libdeno.so not bundled locally)", ranAt))
          for (i in 0 until logsArr.length()) {
            val item = logsArr.optJSONObject(i) ?: continue
            add(
              LocalRunLogEntry(
                level = item.optString("l", "LOG"),
                message = item.optString("m", ""),
                timestamp = ranAt,
              ),
            )
          }
        }
      val errorMsg = obj.optString("error").takeIf { it.isNotBlank() && it != "null" }
      val retValue = obj.optString("result").takeIf { it.isNotBlank() && it != "null" }
      val logOutput = logs.filter { it.level != "INFO" }.joinToString("\n") { it.message }
      val combinedBody =
        listOfNotNull(logOutput.takeIf { it.isNotBlank() }, retValue).joinToString("\n")
      LocalScriptRunResult(
        mode = "JS SANDBOX",
        entryPath = entryPath,
        statusCode = if (errorMsg == null) 200 else 500,
        statusText = if (errorMsg == null) "SANDBOX OK" else "JS ERROR",
        durationMs = dur,
        headers = mapOf("content-type" to "text/plain; charset=utf-8"),
        body = combinedBody,
        logs = logs,
        errorMessage = errorMsg,
        ranAt = ranAt,
      )
    }.getOrElse { err ->
      LocalScriptRunResult(
        mode = "JS SANDBOX",
        entryPath = entryPath,
        statusCode = 500,
        statusText = "PARSE ERROR",
        durationMs = dur,
        errorMessage = err.localizedMessage ?: rawEvalReturn,
        ranAt = ranAt,
      )
    }
  }

  private fun executeInWebViewSandbox(
    context: Context,
    entryPath: String,
    code: String,
    isExpression: Boolean,
    startedAtMs: Long,
    ranAt: String,
  ): LocalScriptRunResult {
    val latch = CountDownLatch(1)
    val resultRef = AtomicReference<String?>(null)
    val script = buildSandboxScriptWrapper(code, isExpression)

    Handler(Looper.getMainLooper()).post {
      runCatching {
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.webViewClient =
          object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
              view?.evaluateJavascript(script) { value ->
                resultRef.set(value)
                latch.countDown()
                runCatching { view.destroy() }
              }
            }
          }
        wv.loadDataWithBaseURL(LocalWorkspacePreviewRenderer.LOCAL_PREVIEW_ORIGIN, "<!DOCTYPE html><html><body></body></html>", "text/html", "UTF-8", null)
      }.onFailure {
        resultRef.set(null)
        latch.countDown()
      }
    }

    latch.await(6, TimeUnit.SECONDS)
    return parseSandboxResultJson(resultRef.get(), entryPath, startedAtMs, ranAt)
  }

  private fun computeFingerprint(
    entryPath: String,
    files: List<WorkerFile>,
    bindingsJson: String,
  ): String {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(entryPath.toByteArray(Charsets.UTF_8))
    md.update(bindingsJson.toByteArray(Charsets.UTF_8))
    files.filter { !it.isVirtualReadOnly }.sortedBy { it.path }.forEach { f ->
      md.update(f.path.toByteArray(Charsets.UTF_8))
      md.update(f.content.toByteArray(Charsets.UTF_8))
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }

  private fun nowTime(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
}
