package com.example.editor

import android.content.Context
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
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
  val mode: String, // "WORKER" | "SCRIPT" | "STATIC"
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
  private const val LOCAL_SERVER_PORT = 8080
  private const val READY_PREFIX = "__EDGECODE_READY__:"
  private const val RUNNER_ASSET_NAME = "universal_runner.ts"
  private const val WORKSPACE_CACHE_DIR = "edgecode_deno_workspace"
  private const val DENO_CACHE_DIR = "deno_cache"

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()
  }

  private val processLock = Any()
  @Volatile private var activeServerProcess: Process? = null
  @Volatile private var activeServerFingerprint: String? = null
  @Volatile private var activeLogBuffer: MutableList<LocalRunLogEntry>? = null

  fun resolveDenoBinaryPath(nativeLibraryDir: String): String {
    val cleanDir = nativeLibraryDir.trimEnd('/', '\\')
    return "$cleanDir/libdeno.so"
  }

  fun resolveDenoBinary(context: Context): File =
    File(resolveDenoBinaryPath(context.applicationInfo.nativeLibraryDir))

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
        val looksExecutable =
          active.content.contains("export default") ||
            active.content.contains("fetch(") ||
            active.content.contains("console.") ||
            active.content.contains("Deno.") ||
            active.content.contains("addEventListener") ||
            active.path == workspace.entryModulePath
        if (looksExecutable) return active.path
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
          .put("text", b.text.orEmpty()),
      )
    }
    return arr.toString()
  }

  fun stageWorkspaceFiles(
    workspaceDir: File,
    files: List<WorkerFile>,
    runnerSource: String?,
  ): File? {
    if (workspaceDir.exists()) {
      workspaceDir.deleteRecursively()
    }
    workspaceDir.mkdirs()

    val rootCanonical = workspaceDir.canonicalFile
    files.filter { !it.isVirtualReadOnly }.forEach { wf ->
      val rel = wf.path.trimStart('/').replace('\\', '/')
      if (rel.isBlank() || rel.contains("..")) return@forEach
      val target = File(workspaceDir, rel)
      if (!target.canonicalPath.startsWith(rootCanonical.canonicalPath)) return@forEach
      target.parentFile?.mkdirs()
      target.writeText(wf.content, Charsets.UTF_8)
    }

    if (runnerSource != null) {
      val runnerFile = File(workspaceDir, ".edgecode_runner/$RUNNER_ASSET_NAME")
      runnerFile.parentFile?.mkdirs()
      runnerFile.writeText(runnerSource, Charsets.UTF_8)
      return runnerFile
    }
    return null
  }

  fun stopActiveServer() {
    synchronized(processLock) {
      runCatching { activeServerProcess?.destroyForcibly() }
      activeServerProcess = null
      activeServerFingerprint = null
      activeLogBuffer = null
    }
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
            ext == "md" || ext == "markdown" ->
              LocalWorkspacePreviewRenderer.renderMarkdownToHtml(file.path, file.content, isDarkTheme)
            else -> file.content
          }
        val mime =
          if (ext == "md" || ext == "markdown") "text/html"
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
        return@withContext LocalScriptRunResult(
          mode = "SCRIPT",
          entryPath = entryPath,
          statusCode = 500,
          statusText = "BINARY MISSING",
          durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
          logs = emptyList(),
          errorMessage =
            "Embedded Deno binary not found at ${denoBinary.absolutePath}. Place an Android arm64-v8a binary at app/src/main/jniLibs/arm64-v8a/libdeno.so.",
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
        put("EDGECODE_PORT", LOCAL_SERVER_PORT.toString())
        put("EDGECODE_HOST", "0.0.0.0")
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

    val request =
      Request.Builder()
        .url("http://127.0.0.1:$LOCAL_SERVER_PORT$cleanPath")
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
