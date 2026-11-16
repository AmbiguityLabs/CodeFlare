package com.example.runner

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

enum class LocalRunKind {
  WORKER_FETCH,
  JS_SCRIPT,
  SHELL_SCRIPT,
  HTML_PREVIEW,
  JSON_VALIDATE,
  ERROR,
}

data class LocalRunResult(
  val filePath: String,
  val kind: LocalRunKind,
  val statusCode: Int,
  val statusText: String,
  val headers: Map<String, String>,
  val body: String,
  val consoleLogs: List<String>,
  val durationMs: Long,
  val isError: Boolean = false,
  val isHtmlContent: Boolean = false,
)

class LocalScriptRunner(context: Context) {
  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private var webView: WebView? = null
  private val inMemoryKvStore = mutableMapOf<String, MutableMap<String, String>>()

  suspend fun runLocally(
    targetFile: WorkerFile,
    workspace: WorkerWorkspace?,
    method: String = "GET",
    requestPath: String = "/",
    headersJson: String = "{}",
    requestBody: String = "",
  ): LocalRunResult {
    val started = System.currentTimeMillis()
    val path = targetFile.path
    val lower = path.lowercase()

    if (lower.endsWith(".sh") || targetFile.content.trimStart().startsWith("#!/")) {
      return runShellScript(path, targetFile.content, started)
    }
    if (lower.endsWith(".json") || lower.endsWith(".jsonc")) {
      return runJsonCheck(path, targetFile.content, started)
    }
    if (lower.endsWith(".html") || lower.endsWith(".htm")) {
      return LocalRunResult(
        filePath = path,
        kind = LocalRunKind.HTML_PREVIEW,
        statusCode = 200,
        statusText = "OK (Local HTML Preview)",
        headers = mapOf("content-type" to "text/html; charset=utf-8"),
        body = targetFile.content,
        consoleLogs = listOf("Rendered HTML directly from unsaved local buffer (${targetFile.content.length} chars)"),
        durationMs = System.currentTimeMillis() - started,
        isHtmlContent = true,
      )
    }

    return runJavaScriptInV8(
      targetFile = targetFile,
      allFiles = workspace?.files.orEmpty(),
      bindings = workspace?.bindings.orEmpty(),
      method = method,
      requestPath = requestPath,
      headersJson = headersJson,
      requestBody = requestBody,
      startedMs = started,
    )
  }

  private suspend fun runShellScript(
    path: String,
    content: String,
    startedMs: Long,
  ): LocalRunResult =
    withContext(Dispatchers.IO) {
      runCatching {
        val workDir = File(appContext.filesDir, "edgecode_workspaces/_local_run").apply { mkdirs() }
        val tmpScript = File(workDir, "run_${System.nanoTime()}.sh")
        tmpScript.writeText(content)
        val proc =
          ProcessBuilder("/system/bin/sh", tmpScript.absolutePath)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        val finished = proc.waitFor(8, TimeUnit.SECONDS)
        if (!finished) proc.destroyForcibly()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        tmpScript.delete()
        val code = if (finished) proc.exitValue() else 124
        LocalRunResult(
          filePath = path,
          kind = LocalRunKind.SHELL_SCRIPT,
          statusCode = code,
          statusText = if (code == 0) "Exit 0 (Shell)" else "Exit $code",
          headers = mapOf("x-runner" to "/system/bin/sh"),
          body = out.ifBlank { "(no stdout)" },
          consoleLogs = listOf("Executed via Android /system/bin/sh (no deployment needed)"),
          durationMs = System.currentTimeMillis() - startedMs,
          isError = code != 0,
        )
      }.getOrElse { err ->
        LocalRunResult(
          filePath = path,
          kind = LocalRunKind.ERROR,
          statusCode = 1,
          statusText = "Shell Error",
          headers = emptyMap(),
          body = err.localizedMessage ?: "Failed to execute shell script",
          consoleLogs = emptyList(),
          durationMs = System.currentTimeMillis() - startedMs,
          isError = true,
        )
      }
    }

  private fun runJsonCheck(
    path: String,
    content: String,
    startedMs: Long,
  ): LocalRunResult {
    return try {
      val trimmed = content.trim()
      val formatted =
        if (trimmed.startsWith("[")) JSONArray(trimmed).toString(2)
        else JSONObject(trimmed).toString(2)
      LocalRunResult(
        filePath = path,
        kind = LocalRunKind.JSON_VALIDATE,
        statusCode = 200,
        statusText = "Valid JSON",
        headers = mapOf("content-type" to "application/json"),
        body = formatted,
        consoleLogs = listOf("Parsed & validated JSON (${formatted.lines().size} lines)"),
        durationMs = System.currentTimeMillis() - startedMs,
      )
    } catch (e: Exception) {
      LocalRunResult(
        filePath = path,
        kind = LocalRunKind.ERROR,
        statusCode = 400,
        statusText = "JSON Syntax Error",
        headers = emptyMap(),
        body = e.localizedMessage ?: "Invalid JSON",
        consoleLogs = emptyList(),
        durationMs = System.currentTimeMillis() - startedMs,
        isError = true,
      )
    }
  }

  private suspend fun runJavaScriptInV8(
    targetFile: WorkerFile,
    allFiles: List<WorkerFile>,
    bindings: List<CfWorkerBinding>,
    method: String,
    requestPath: String,
    headersJson: String,
    requestBody: String,
    startedMs: Long,
  ): LocalRunResult {
    val deferred = CompletableDeferred<String>()
    val capturedWebConsole = mutableListOf<String>()

    val preparedBundle = buildExecutableJsPayload(targetFile, allFiles)
    val envJson = buildEnvSpecJson(bindings)
    val fullUrl =
      if (requestPath.startsWith("http://") || requestPath.startsWith("https://")) requestPath
      else "https://local.edgecode.dev/${requestPath.trimStart('/')}"

    val reqSpecJson =
      JSONObject()
        .put("url", fullUrl)
        .put("method", method.uppercase())
        .put("headersJson", headersJson.ifBlank { "{}" })
        .put("body", requestBody)
        .toString()

    val harnessScript = buildHarnessJs(preparedBundle, reqSpecJson, envJson)

    withContext(Dispatchers.Main) {
      val wv = ensureWebView(capturedWebConsole)
      wv.removeJavascriptInterface("EdgeRunnerBridge")
      wv.addJavascriptInterface(
        object {
          @JavascriptInterface
          fun onResult(jsonPayload: String) {
            if (!deferred.isCompleted) deferred.complete(jsonPayload)
          }

          @JavascriptInterface
          fun kvGet(nsName: String, key: String): String? =
            inMemoryKvStore[nsName]?.get(key)

          @JavascriptInterface
          fun kvPut(nsName: String, key: String, value: String) {
            inMemoryKvStore.getOrPut(nsName) { mutableMapOf() }[key] = value
          }

          @JavascriptInterface
          fun kvDelete(nsName: String, key: String) {
            inMemoryKvStore[nsName]?.remove(key)
          }

          @JavascriptInterface
          fun kvList(nsName: String): String {
            val arr = JSONArray()
            inMemoryKvStore[nsName]?.keys?.sorted()?.forEach { k ->
              arr.put(JSONObject().put("name", k))
            }
            return arr.toString()
          }
        },
        "EdgeRunnerBridge",
      )
      wv.evaluateJavascript(harnessScript, null)
    }

    val rawResult = withTimeoutOrNull(10_000L) { deferred.await() }
      ?: return LocalRunResult(
        filePath = targetFile.path,
        kind = LocalRunKind.ERROR,
        statusCode = 408,
        statusText = "Execution Timeout",
        headers = emptyMap(),
        body = "Local script execution timed out after 10s.",
        consoleLogs = capturedWebConsole,
        durationMs = System.currentTimeMillis() - startedMs,
        isError = true,
      )

    return parseRunnerJson(targetFile.path, rawResult, capturedWebConsole, System.currentTimeMillis() - startedMs)
  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun ensureWebView(capturedWebConsole: MutableList<String>): WebView {
    val existing = webView
    if (existing != null) {
      existing.webChromeClient =
        object : WebChromeClient() {
          override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            consoleMessage?.let {
              capturedWebConsole.add("[${it.messageLevel().name}] L${it.lineNumber()}: ${it.message()}")
            }
            return true
          }
        }
      return existing
    }
    val ready = CompletableDeferred<Unit>()
    val wv = WebView(appContext)
    wv.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      cacheMode = WebSettings.LOAD_NO_CACHE
    }
    wv.webViewClient =
      object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
          if (!ready.isCompleted) ready.complete(Unit)
        }
      }
    wv.webChromeClient =
      object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
          consoleMessage?.let {
            capturedWebConsole.add("[${it.messageLevel().name}] L${it.lineNumber()}: ${it.message()}")
          }
          return true
        }
      }
    wv.loadDataWithBaseURL("https://local.edgecode.dev/", "<!doctype html><html><body></body></html>", "text/html", "utf-8", null)
    withTimeoutOrNull(2_000L) { ready.await() }
    webView = wv
    return wv
  }

  private fun buildExecutableJsPayload(
    targetFile: WorkerFile,
    allFiles: List<WorkerFile>,
  ): String {
    val fileMap = allFiles.associateBy { it.path }
    val visited = mutableSetOf<String>()
    val preludeModules = StringBuilder()

    fun resolveRelative(basePath: String, rel: String): String {
      val baseDir = if (basePath.contains('/')) basePath.substringBeforeLast('/') else ""
      val combined =
        if (rel.startsWith("./")) {
          if (baseDir.isEmpty()) rel.removePrefix("./") else "$baseDir/${rel.removePrefix("./")}"
        } else if (rel.startsWith("../")) {
          val parts = (if (baseDir.isEmpty()) emptyList() else baseDir.split('/')).toMutableList()
          var rem = rel
          while (rem.startsWith("../")) {
            if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            rem = rem.removePrefix("../")
          }
          (parts + rem).joinToString("/")
        } else {
          rel.trimStart('/')
        }
      val candidates = listOf(combined, "$combined.js", "$combined.mjs", "$combined.ts", "$combined/index.js")
      return candidates.firstOrNull { fileMap.containsKey(it) } ?: combined
    }

    fun stripBasicTs(code: String, isTs: Boolean): String {
      if (!isTs) return code
      return code
        .replace(Regex(""":\s*(Request|Response|ExecutionContext|Headers|URL|any|string|number|boolean|void|unknown)\b(\[\])?"""), "")
        .replace(Regex("""export\s+interface\s+\w+\s*\{[^}]*\}"""), "")
        .replace(Regex("""interface\s+\w+\s*\{[^}]*\}"""), "")
        .replace(Regex("""export\s+type\s+\w+\s*=[^;]+;"""), "")
        .replace(Regex("""\s+as\s+(Response|Request|any|string|number)\b"""), "")
    }

    fun transformImportsAndExports(rawCode: String, currentPath: String, isEntry: Boolean): String {
      var code = stripBasicTs(rawCode, currentPath.endsWith(".ts", true) || currentPath.endsWith(".mts", true))
      val importRegex = Regex("""import\s+([^;]+?)\s+from\s+["']([^"']+)["']\s*;?""")
      code =
        importRegex.replace(code) { m ->
          val spec = m.groupValues[1].trim()
          val rel = m.groupValues[2].trim()
          val resolvedPath = resolveRelative(currentPath, rel)
          val depFile = fileMap[resolvedPath]
          if (depFile != null) {
            if (visited.add(resolvedPath)) {
              val depBody = transformImportsAndExports(depFile.content, resolvedPath, isEntry = false)
              preludeModules.appendLine("__modules[\"$resolvedPath\"] = (() => {")
              preludeModules.appendLine("  const __exports = {};")
              preludeModules.appendLine(depBody)
              preludeModules.appendLine("  return __exports;")
              preludeModules.appendLine("})();")
            }
            if (spec.startsWith("{") && spec.endsWith("}")) {
              "const $spec = (__modules[\"$resolvedPath\"] || {});"
            } else if (spec.startsWith("* as ")) {
              val alias = spec.removePrefix("* as ").trim()
              "const $alias = (__modules[\"$resolvedPath\"] || {});"
            } else {
              "const $spec = (__modules[\"$resolvedPath\"]?.default ?? __modules[\"$resolvedPath\"] ?? {});"
            }
          } else {
            "// unresolved import: $rel"
          }
        }

      if (isEntry) {
        code =
          code
            .replace(Regex("""export\s+default\s+async\s+function"""), "const __defaultExport = async function")
            .replace(Regex("""export\s+default\s+function"""), "const __defaultExport = function")
            .replace(Regex("""export\s+default\s+"""), "const __defaultExport = ")
            .replace(Regex("""\bexport\s+(?=(const|let|var|function|async\s+function|class)\b)"""), "")
      } else {
        val exportedNames = mutableListOf<String>()
        code =
          code
            .replace(Regex("""export\s+default\s+"""), "__exports.default = ")
            .replace(Regex("""\bexport\s+(?:const|let|var|async\s+function|function|class)\s+([a-zA-Z0-9_$]+)""")) { m ->
              exportedNames.add(m.groupValues[1])
              m.value.removePrefix("export").trimStart()
            }
        if (exportedNames.isNotEmpty()) {
          val exportsFooter =
            exportedNames.distinct().joinToString("\n") { n ->
              """try { __exports["$n"] = $n; } catch (_) {}"""
            }
          code = "$code\n$exportsFooter"
        }
      }
      return code
    }

    val transformedEntry = transformImportsAndExports(targetFile.content, targetFile.path, isEntry = true)
    return buildString {
      if (preludeModules.isNotEmpty()) {
        appendLine(preludeModules.toString())
      }
      appendLine(transformedEntry)
    }
  }

  private fun buildEnvSpecJson(bindings: List<CfWorkerBinding>): String {
    val arr = JSONArray()
    bindings.forEach { b ->
      arr.put(
        JSONObject()
          .put("name", b.name)
          .put("type", b.type)
          .put("text", b.text ?: ""),
      )
    }
    return arr.toString()
  }

  private fun buildHarnessJs(
    userBundleCode: String,
    reqSpecJson: String,
    envSpecJson: String,
  ): String {
    val reqLiteral = JSONObject.quote(reqSpecJson)
    val envLiteral = JSONObject.quote(envSpecJson)
    return """
(async () => {
  const __logs = [];
  const __fmt = (args) => args.map(a => {
    if (typeof a === 'string') return a;
    try { return JSON.stringify(a); } catch (e) { return String(a); }
  }).join(' ');
  const __origConsole = window.console;
  const console = {
    log: (...a) => { __logs.push('[LOG] ' + __fmt(a)); __origConsole?.log?.(...a); },
    info: (...a) => { __logs.push('[INFO] ' + __fmt(a)); __origConsole?.info?.(...a); },
    warn: (...a) => { __logs.push('[WARN] ' + __fmt(a)); __origConsole?.warn?.(...a); },
    error: (...a) => { __logs.push('[ERROR] ' + __fmt(a)); __origConsole?.error?.(...a); },
    debug: (...a) => { __logs.push('[DEBUG] ' + __fmt(a)); __origConsole?.debug?.(...a); },
  };
  const __modules = {};
  let __fetchListener = null;
  const addEventListener = (type, fn) => {
    if (type === 'fetch') __fetchListener = fn;
  };
  try {
    const reqSpec = JSON.parse($reqLiteral);
    const envSpecs = JSON.parse($envLiteral);
    const env = {};
    for (const item of envSpecs) {
      if (item.type === 'plain_text') {
        env[item.name] = item.text;
      } else if (item.type === 'secret_text') {
        env[item.name] = '[local-secret:' + item.name + ']';
      } else if (item.type === 'kv_namespace') {
        const nsName = item.name;
        env[nsName] = {
          get: async (k, opts) => {
            const val = EdgeRunnerBridge.kvGet(nsName, String(k));
            if (val === null || val === undefined) return null;
            if (opts === 'json' || opts?.type === 'json') return JSON.parse(val);
            return val;
          },
          put: async (k, v) => { EdgeRunnerBridge.kvPut(nsName, String(k), typeof v === 'string' ? v : JSON.stringify(v)); },
          delete: async (k) => { EdgeRunnerBridge.kvDelete(nsName, String(k)); },
          list: async () => ({ keys: JSON.parse(EdgeRunnerBridge.kvList(nsName)), list_complete: true }),
        };
      }
    }
    const ctx = {
      waitUntil: (p) => Promise.resolve(p).catch(e => __logs.push('[waitUntil error] ' + String(e))),
      passThroughOnException: () => {},
    };

    $userBundleCode

    let parsedHeaders = {};
    try { parsedHeaders = JSON.parse(reqSpec.headersJson || '{}'); } catch (_) {}
    const reqInit = { method: reqSpec.method, headers: parsedHeaders };
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(reqSpec.method) && reqSpec.body && reqSpec.body.length > 0) {
      reqInit.body = reqSpec.body;
    }
    const request = new Request(reqSpec.url, reqInit);
    Object.defineProperty(request, 'cf', {
      value: { colo: 'LOCAL', country: 'LOCAL', city: 'EdgeCode', tlsVersion: 'TLSv1.3', httpProtocol: 'HTTP/2' },
      enumerable: true,
    });

    if (typeof __defaultExport !== 'undefined' && __defaultExport && typeof __defaultExport.fetch === 'function') {
      const resp = await __defaultExport.fetch(request, env, ctx);
      const outHeaders = {};
      resp.headers.forEach((v, k) => { outHeaders[k] = v; });
      const text = await resp.text();
      EdgeRunnerBridge.onResult(JSON.stringify({
        kind: 'WORKER_FETCH',
        statusCode: resp.status,
        statusText: resp.statusText || 'OK',
        headers: outHeaders,
        body: text,
        logs: __logs,
        isError: resp.status >= 500
      }));
    } else if (typeof __fetchListener === 'function') {
      let responded = null;
      await __fetchListener({
        request,
        respondWith: (p) => { responded = Promise.resolve(p); },
        waitUntil: ctx.waitUntil,
      });
      const resp = responded ? await responded : new Response('No respondWith() called', { status: 500 });
      const outHeaders = {};
      resp.headers.forEach((v, k) => { outHeaders[k] = v; });
      const text = await resp.text();
      EdgeRunnerBridge.onResult(JSON.stringify({
        kind: 'WORKER_FETCH',
        statusCode: resp.status,
        statusText: resp.statusText || 'OK',
        headers: outHeaders,
        body: text,
        logs: __logs,
        isError: resp.status >= 500
      }));
    } else {
      const summary = __logs.length > 0
        ? __logs.join('\n')
        : (typeof __defaultExport !== 'undefined' ? JSON.stringify(__defaultExport, null, 2) : 'Script evaluated with 0 errors and no console output.');
      EdgeRunnerBridge.onResult(JSON.stringify({
        kind: 'JS_SCRIPT',
        statusCode: 200,
        statusText: 'Executed JS Locally',
        headers: { 'x-runtime': 'Android V8 WebView' },
        body: summary,
        logs: __logs,
        isError: false
      }));
    }
  } catch (err) {
    EdgeRunnerBridge.onResult(JSON.stringify({
      kind: 'ERROR',
      statusCode: 500,
      statusText: err?.name || 'RuntimeError',
      headers: {},
      body: (err?.stack || err?.message || String(err)),
      logs: __logs,
      isError: true
    }));
  }
})();
    """.trimIndent()
  }

  private fun parseRunnerJson(
    filePath: String,
    rawJson: String,
    webLogs: List<String>,
    durationMs: Long,
  ): LocalRunResult {
    return try {
      val obj = JSONObject(rawJson)
      val kindStr = obj.optString("kind", "WORKER_FETCH")
      val kind = runCatching { LocalRunKind.valueOf(kindStr) }.getOrDefault(LocalRunKind.WORKER_FETCH)
      val headersObj = obj.optJSONObject("headers") ?: JSONObject()
      val headers = linkedMapOf<String, String>()
      headersObj.keys().forEach { k -> headers[k] = headersObj.optString(k) }

      val logsArr = obj.optJSONArray("logs") ?: JSONArray()
      val logs = mutableListOf<String>()
      for (i in 0 until logsArr.length()) {
        logs.add(logsArr.optString(i))
      }
      webLogs.forEach { if (!logs.contains(it)) logs.add(it) }

      val body = obj.optString("body", "")
      val contentType = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value.orEmpty()
      val isHtml = contentType.contains("text/html", true) || body.trimStart().startsWith("<!doctype html", true) || body.trimStart().startsWith("<html", true)

      LocalRunResult(
        filePath = filePath,
        kind = kind,
        statusCode = obj.optInt("statusCode", 200),
        statusText = obj.optString("statusText", "OK"),
        headers = headers,
        body = body,
        consoleLogs = logs,
        durationMs = durationMs,
        isError = obj.optBoolean("isError", false),
        isHtmlContent = isHtml,
      )
    } catch (e: Exception) {
      LocalRunResult(
        filePath = filePath,
        kind = LocalRunKind.ERROR,
        statusCode = 500,
        statusText = "Runner Parse Error",
        headers = emptyMap(),
        body = "$rawJson\n${e.localizedMessage}",
        consoleLogs = webLogs,
        durationMs = durationMs,
        isError = true,
      )
    }
  }
}
