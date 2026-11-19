package com.example.editor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.remote.WorkerBinding
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

object LocalModuleBundler {
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
      if (ext in setOf("js", "mjs", "ts", "tsx", "jsx")) {
        val looksExecutable =
          active.content.contains("export default") ||
            active.content.contains("fetch(") ||
            active.content.contains("console.") ||
            active.path == workspace.entryModulePath
        if (looksExecutable) return active.path
      }
    }
    return workspace.entryModulePath
      .takeIf { p -> editable.any { it.path == p } }
      ?: editable.firstOrNull { it.isEntryPoint }?.path
      ?: editable.firstOrNull()?.path
      ?: "index.js"
  }

  fun stripBasicTypeScript(source: String, filePath: String): String {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    if (ext != "ts" && ext != "tsx") return source

    var code = source
    code = code.replace(Regex("""(?m)^\s*import\s+type\s+[^;]+;\s*$"""), "")
    code = code.replace(Regex("""(?m)^\s*export\s+(?:declare\s+)?interface\s+\w+(?:<[^>]+>)?(?:\s+extends\s+[^{]+)?\s*\{[\s\S]*?^\s*\}"""), "")
    code = code.replace(Regex("""(?m)^\s*interface\s+\w+(?:<[^>]+>)?(?:\s+extends\s+[^{]+)?\s*\{[\s\S]*?^\s*\}"""), "")
    code = code.replace(Regex("""(?m)^\s*(?:export\s+)?type\s+\w+(?:<[^>]+>)?\s*=\s*[^;]+;\s*$"""), "")
    code = code.replace(Regex("""(?m)^(\s*(?:export\s+)?(?:async\s+)?function\s+\w+\s*(?:<[^>]+>)?\([^)]*\))\s*:\s*[A-Za-z0-9_<>\[\]|\s&.,{}?]+\s*(\{)""")) {
      "${it.groupValues[1]} ${it.groupValues[2]}"
    }
    code = code.replace(Regex("""\b(fetch|scheduled)\s*\(([^)]+)\)\s*:\s*Promise<[^>]+>""")) {
      "${it.groupValues[1]}(${it.groupValues[2]})"
    }
    code = code.replace(Regex("""(\b[A-Za-z_$][\w$]*)\s*:\s*(?:Request|Response|ExecutionContext|Env|ExportedHandler|KVNamespace|D1Database|R2Bucket|ScheduledController|MessageBatch|string|number|boolean|any|unknown|void|never|Record<[^>{}]+>|Promise<[^>{}]+>)(?=\s*[,)=;])""")) {
      it.groupValues[1]
    }
    code = code.replace(Regex("""\s+as\s+(?:const|any|unknown|string|number|boolean|Response|Request|Record<[^>;]+>)\b"""), "")
    code = code.replace(Regex("""\bsatisfies\s+[A-Za-z0-9_<>\[\]|&\s]+\b"""), "")
    return code
  }

  fun transpileEsmToCjs(source: String, filePath: String): String {
    var code = stripBasicTypeScript(source, filePath)
    val importStarRe = Regex("""(?m)^\s*import\s+\*\s+as\s+([A-Za-z_$][\w$]*)\s+from\s+["']([^"']+)["']\s*;?\s*$""")
    code = importStarRe.replace(code) { m ->
      "const ${m.groupValues[1]} = __require(${JSONObject.quote(m.groupValues[2])});"
    }

    val importDefaultAndNamedRe = Regex("""(?m)^\s*import\s+([A-Za-z_$][\w$]*)\s*,\s*\{([^}]+)\}\s+from\s+["']([^"']+)["']\s*;?\s*$""")
    code = importDefaultAndNamedRe.replace(code) { m ->
      val defName = m.groupValues[1]
      val named = m.groupValues[2].replace(" as ", ": ")
      val spec = JSONObject.quote(m.groupValues[3])
      "const __imp_$defName = __require($spec); const $defName = __imp_$defName.default ?? __imp_$defName; const {$named} = __imp_$defName;"
    }

    val importDefaultRe = Regex("""(?m)^\s*import\s+([A-Za-z_$][\w$]*)\s+from\s+["']([^"']+)["']\s*;?\s*$""")
    code = importDefaultRe.replace(code) { m ->
      val defName = m.groupValues[1]
      val spec = JSONObject.quote(m.groupValues[2])
      "const __imp_$defName = __require($spec); const $defName = (__imp_$defName && 'default' in __imp_$defName) ? __imp_$defName.default : __imp_$defName;"
    }

    val importNamedRe = Regex("""(?m)^\s*import\s+\{([^}]+)\}\s+from\s+["']([^"']+)["']\s*;?\s*$""")
    code = importNamedRe.replace(code) { m ->
      val named = m.groupValues[1].replace(" as ", ": ")
      val spec = JSONObject.quote(m.groupValues[2])
      "const {$named} = __require($spec);"
    }

    val sideEffectImportRe = Regex("""(?m)^\s*import\s+["']([^"']+)["']\s*;?\s*$""")
    code = sideEffectImportRe.replace(code) { m ->
      "__require(${JSONObject.quote(m.groupValues[1])});"
    }

    code = code.replace(Regex("""(?m)^\s*export\s+default\s+"""), "exports.default = ")
    code = code.replace(Regex("""(?m)^\s*export\s+async\s+function\s+([A-Za-z_$][\w$]*)""")) { m ->
      "async function ${m.groupValues[1]}"
    }
    val exportedNames = mutableListOf<String>()
    Regex("""(?m)^\s*export\s+(?:async\s+)?function\s+([A-Za-z_$][\w$]*)""").findAll(code).forEach {
      exportedNames += it.groupValues[1]
    }
    Regex("""(?m)^\s*export\s+(?:const|let|var)\s+([A-Za-z_$][\w$]*)""").findAll(code).forEach {
      exportedNames += it.groupValues[1]
    }
    code = code.replace(Regex("""(?m)^\s*export\s+(const|let|var|function|async\s+function|class)\s+""")) { m ->
      "${m.groupValues[1]} "
    }
    code = code.replace(Regex("""(?m)^\s*export\s+\{([^}]+)\}\s*;?\s*$""")) { m ->
      val parts =
        m.groupValues[1]
          .split(',')
          .mapNotNull { raw ->
            val seg = raw.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (seg.contains(" as ")) {
              val (local, alias) = seg.split(" as ", limit = 2).map { it.trim() }
              "exports.$alias = $local;"
            } else {
              "exports.$seg = $seg;"
            }
          }
      parts.joinToString(" ")
    }
    if (exportedNames.isNotEmpty()) {
      code += "\n" + exportedNames.distinct().joinToString(" ") { "if (typeof $it !== 'undefined') exports.$it = $it;" }
    }
    return code
  }

  fun buildModuleMapJson(files: List<WorkerFile>): JSONObject {
    val map = JSONObject()
    files.filter { !it.isVirtualReadOnly }.forEach { f ->
      val ext = f.path.substringAfterLast('.', "").lowercase()
      val transformed =
        when (ext) {
          "js", "mjs", "ts", "tsx", "jsx" -> transpileEsmToCjs(f.content, f.path)
          "json", "jsonc" -> "exports.default = ${f.content}; module.exports = exports.default;"
          else -> "exports.default = ${JSONObject.quote(f.content)}; module.exports = exports.default;"
        }
      map.put(f.path, transformed)
    }
    return map
  }
}

object LocalScriptRunner {
  suspend fun runWorkspace(
    context: Context,
    workspace: WorkerWorkspace,
    activeFilePath: String?,
    requestPath: String = "/",
    method: String = "GET",
    headersJson: String = "{}",
    body: String = "",
    isDarkTheme: Boolean = true,
  ): LocalScriptRunResult {
    val startedAtMs = System.currentTimeMillis()
    val ranAt = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(startedAtMs))
    val entryPath = LocalModuleBundler.resolveTargetEntryPath(workspace, activeFilePath)

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
      return LocalScriptRunResult(
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

    val moduleMap = LocalModuleBundler.buildModuleMapJson(workspace.files)
    val bindingsArr = JSONArray()
    workspace.bindings.forEach { b: WorkerBinding ->
      bindingsArr.put(
        JSONObject()
          .put("name", b.name)
          .put("type", b.type)
          .put("text", b.text.orEmpty()),
      )
    }
    val headersObj =
      runCatching { JSONObject(headersJson.ifBlank { "{}" }) }
        .getOrElse { JSONObject() }

    val cleanPath = requestPath.trim().let { if (it.startsWith("/")) it else "/$it" }
    val requestUrl = "https://local.edgecode.app$cleanPath"

    val payload =
      JSONObject()
        .put("entryPath", entryPath)
        .put("modules", moduleMap)
        .put("bindings", bindingsArr)
        .put(
          "request",
          JSONObject()
            .put("url", requestUrl)
            .put("method", method.uppercase())
            .put("headers", headersObj)
            .put("body", body),
        )

    return executeInHeadlessWebView(
      context = context,
      entryPath = entryPath,
      payloadJson = payload.toString(),
      ranAt = ranAt,
      startedAtMs = startedAtMs,
    )
  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun executeInHeadlessWebView(
    context: Context,
    entryPath: String,
    payloadJson: String,
    ranAt: String,
    startedAtMs: Long,
  ): LocalScriptRunResult =
    withContext(Dispatchers.Main) {
      suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        var completed = false
        var webView: WebView? = null

        fun finish(result: LocalScriptRunResult) {
          if (completed) return
          completed = true
          handler.removeCallbacksAndMessages(null)
          runCatching {
            webView?.stopLoading()
            webView?.removeJavascriptInterface("__EdgeRunBridge")
            webView?.destroy()
          }
          webView = null
          if (cont.isActive) cont.resume(result)
        }

        val timeoutRunnable =
          Runnable {
            finish(
              LocalScriptRunResult(
                mode = "SCRIPT",
                entryPath = entryPath,
                statusCode = 408,
                statusText = "TIMEOUT",
                durationMs = System.currentTimeMillis() - startedAtMs,
                errorMessage = "Timed out (8s)",
                ranAt = ranAt,
              ),
            )
          }
        handler.postDelayed(timeoutRunnable, 8_000L)

        val bridge =
          object {
            @JavascriptInterface
            fun onComplete(rawJson: String) {
              handler.post {
                val parsed =
                  runCatching {
                    val obj = JSONObject(rawJson)
                    val mode = obj.optString("mode", "SCRIPT")
                    val statusCode = obj.optInt("statusCode", 200)
                    val statusText = obj.optString("statusText", "OK")
                    val outBody = obj.optString("body", "")
                    val errorMsg = obj.optString("error").takeIf { it.isNotBlank() }
                    val headers = linkedMapOf<String, String>()
                    obj.optJSONObject("headers")?.let { hObj ->
                      hObj.keys().forEach { k -> headers[k] = hObj.optString(k) }
                    }
                    val logsList = mutableListOf<LocalRunLogEntry>()
                    obj.optJSONArray("logs")?.let { arr ->
                      for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        logsList +=
                          LocalRunLogEntry(
                            level = item.optString("level", "LOG"),
                            message = item.optString("message", ""),
                            timestamp = item.optString("timestamp", ranAt),
                          )
                      }
                    }
                    val ctype = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value.orEmpty()
                    val isHtml =
                      ctype.contains("text/html", true) ||
                        outBody.trimStart().startsWith("<!DOCTYPE html", true) ||
                        outBody.trimStart().startsWith("<html", true)
                    LocalScriptRunResult(
                      mode = mode,
                      entryPath = entryPath,
                      statusCode = statusCode,
                      statusText = statusText,
                      durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
                      headers = headers,
                      body = outBody,
                      isHtmlBody = isHtml,
                      logs = logsList,
                      errorMessage = errorMsg,
                      ranAt = ranAt,
                    )
                  }.getOrElse { err ->
                    LocalScriptRunResult(
                      mode = "SCRIPT",
                      entryPath = entryPath,
                      statusCode = 500,
                      statusText = "ERROR",
                      durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L),
                      errorMessage = err.localizedMessage ?: "Parse error",
                      ranAt = ranAt,
                    )
                  }
                finish(parsed)
              }
            }
          }

        val wv =
          WebView(context.applicationContext).apply {
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = false
              allowFileAccess = false
              allowContentAccess = false
            }
            addJavascriptInterface(bridge, "__EdgeRunBridge")
            webViewClient =
              object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                  val jsHarness = buildHarnessJs(payloadJson)
                  view?.evaluateJavascript(jsHarness, null)
                }
              }
          }
        webView = wv

        cont.invokeOnCancellation {
          handler.post {
            completed = true
            handler.removeCallbacksAndMessages(null)
            runCatching {
              wv.stopLoading()
              wv.destroy()
            }
          }
        }

        wv.loadDataWithBaseURL(
          LocalWorkspacePreviewRenderer.LOCAL_PREVIEW_ORIGIN,
          "<!DOCTYPE html><html><head></head><body></body></html>",
          "text/html",
          "UTF-8",
          null,
        )
      }
    }

  private fun buildHarnessJs(payloadJson: String): String {
    val quotedPayload = JSONObject.quote(payloadJson)
    return """
(function() {
  const __payload = JSON.parse($quotedPayload);
  const __logs = [];
  const __fmtTime = () => {
    const d = new Date();
    return [d.getHours(), d.getMinutes(), d.getSeconds()]
      .map(n => String(n).padStart(2, '0')).join(':');
  };
  const __serialize = (v) => {
    if (v === undefined) return 'undefined';
    if (v === null) return 'null';
    if (typeof v === 'string') return v;
    if (v instanceof Error) return v.stack || (v.name + ': ' + v.message);
    try { return JSON.stringify(v); } catch (e) { return String(v); }
  };
  const __pushLog = (lvl, args) => {
    __logs.push({
      level: lvl,
      message: Array.prototype.slice.call(args).map(__serialize).join(' '),
      timestamp: __fmtTime()
    });
  };
  const origConsole = window.console;
  window.console = {
    log: function() { __pushLog('LOG', arguments); },
    info: function() { __pushLog('INFO', arguments); },
    warn: function() { __pushLog('WARN', arguments); },
    error: function() { __pushLog('ERR', arguments); },
    debug: function() { __pushLog('DBG', arguments); },
    table: function(x) { __pushLog('LOG', [x]); }
  };

  function __resolveModulePath(fromPath, spec) {
    if (!spec) return spec;
    const baseParts = fromPath.includes('/') ? fromPath.split('/').slice(0, -1) : [];
    const rawParts = spec.startsWith('./') || spec.startsWith('../')
      ? baseParts.concat(spec.split('/'))
      : spec.replace(/^\//, '').split('/');
    const resolved = [];
    for (const part of rawParts) {
      if (!part || part === '.') continue;
      if (part === '..') resolved.pop();
      else resolved.push(part);
    }
    const base = resolved.join('/');
    const candidates = [
      base,
      base + '.js',
      base + '.ts',
      base + '.mjs',
      base + '.json',
      base + '/index.js',
      base + '/index.ts'
    ];
    for (const c of candidates) {
      if (Object.prototype.hasOwnProperty.call(__payload.modules, c)) return c;
    }
    return base;
  }

  const __cache = {};
  function __loadModule(path) {
    if (__cache[path]) return __cache[path].exports;
    const code = __payload.modules[path];
    if (typeof code !== 'string') {
      throw new Error('Module not found: ' + path);
    }
    const module = { exports: {} };
    __cache[path] = module;
    const localRequire = function(spec) {
      const target = __resolveModulePath(path, spec);
      return __loadModule(target);
    };
    const fn = new Function('require', '__require', 'module', 'exports', code);
    fn(localRequire, localRequire, module, module.exports);
    return module.exports;
  }

  function __makeEnv(bindings) {
    const env = {};
    for (const b of bindings || []) {
      if (b.type === 'plain_text' || b.type === 'secret_text') {
        env[b.name] = b.text || ('[' + b.type + ']');
      } else if (b.type === 'kv_namespace') {
        const kvStore = new Map();
        env[b.name] = {
          get: async (k, opts) => {
            const v = kvStore.get(String(k));
            if (v === undefined) return null;
            return opts && (opts.type === 'json' || opts === 'json') ? JSON.parse(v) : v;
          },
          put: async (k, v) => { kvStore.set(String(k), typeof v === 'string' ? v : JSON.stringify(v)); },
          delete: async (k) => { kvStore.delete(String(k)); },
          list: async () => ({ keys: Array.from(kvStore.keys()).map(name => ({ name })), list_complete: true })
        };
      } else if (b.type === 'r2_bucket') {
        const r2Store = new Map();
        env[b.name] = {
          get: async (k) => r2Store.has(k) ? { key: k, text: async () => r2Store.get(k) } : null,
          put: async (k, v) => { r2Store.set(k, typeof v === 'string' ? v : JSON.stringify(v)); return { key: k }; },
          delete: async (k) => { r2Store.delete(k); },
          list: async () => ({ objects: Array.from(r2Store.keys()).map(key => ({ key })), truncated: false })
        };
      } else if (b.type === 'd1') {
        env[b.name] = {
          prepare: (sql) => ({
            bind: function() { return this; },
            all: async () => ({ results: [], success: true, meta: { sql } }),
            first: async () => null,
            run: async () => ({ success: true, meta: { sql } })
          })
        };
      } else {
        env[b.name] = {};
      }
    }
    return env;
  }

  (async () => {
    let fetchHandler = null;
    const __origAddEvent = self.addEventListener;
    self.addEventListener = function(type, cb) {
      if (type === 'fetch' && typeof cb === 'function') fetchHandler = cb;
      if (__origAddEvent) __origAddEvent.apply(self, arguments);
    };

    try {
      const mod = __loadModule(__payload.entryPath);
      const env = __makeEnv(__payload.bindings);
      const waitUntils = [];
      const ctx = {
        waitUntil: (p) => { if (p && typeof p.then === 'function') waitUntils.push(p); },
        passThroughOnException: () => {}
      };
      const reqInit = { method: __payload.request.method || 'GET', headers: __payload.request.headers || {} };
      if (reqInit.method !== 'GET' && reqInit.method !== 'HEAD' && __payload.request.body) {
        reqInit.body = __payload.request.body;
      }
      const req = new Request(__payload.request.url, reqInit);

      let response = null;
      if (mod && mod.default && typeof mod.default.fetch === 'function') {
        response = await mod.default.fetch(req, env, ctx);
      } else if (typeof mod.fetch === 'function') {
        response = await mod.fetch(req, env, ctx);
      } else if (typeof mod.default === 'function') {
        const ret = await mod.default(req, env, ctx);
        if (ret instanceof Response) response = ret;
        else if (ret !== undefined) __logs.push({ level: 'RET', message: __serialize(ret), timestamp: __fmtTime() });
      } else if (fetchHandler) {
        let responded = null;
        fetchHandler({
          request: req,
          respondWith: (p) => { responded = p; },
          waitUntil: ctx.waitUntil
        });
        if (responded) response = await responded;
      }

      if (waitUntils.length) await Promise.allSettled(waitUntils);

      if (response && typeof response.text === 'function') {
        const hMap = {};
        if (response.headers && response.headers.forEach) {
          response.headers.forEach((v, k) => { hMap[k] = v; });
        }
        const text = await response.text();
        __EdgeRunBridge.onComplete(JSON.stringify({
          mode: 'WORKER',
          statusCode: response.status || 200,
          statusText: response.statusText || 'OK',
          headers: hMap,
          body: text,
          logs: __logs
        }));
      } else {
        const logOut = __logs.map(l => '[' + l.level + '] ' + l.message).join('\n');
        __EdgeRunBridge.onComplete(JSON.stringify({
          mode: 'SCRIPT',
          statusCode: 200,
          statusText: 'OK',
          headers: { 'content-type': 'text/plain' },
          body: logOut || __serialize(mod && mod.default !== undefined ? mod.default : mod),
          logs: __logs
        }));
      }
    } catch (err) {
      __logs.push({ level: 'ERR', message: __serialize(err), timestamp: __fmtTime() });
      __EdgeRunBridge.onComplete(JSON.stringify({
        mode: 'SCRIPT',
        statusCode: 500,
        statusText: 'RUNTIME ERROR',
        headers: {},
        body: '',
        logs: __logs,
        error: (err && err.stack) ? err.stack : String(err)
      }));
    } finally {
      window.console = origConsole;
    }
  })();
})();
""".trimIndent()
  }
}
