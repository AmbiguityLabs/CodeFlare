package com.example.editor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.remote.CfWorkerBinding
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
    // Strip inline `type` modifiers inside named import braces: import { type Foo, bar } from 'pkg'
    code =
      Regex("""(?m)^\s*import\s+\{([^}]+)\}\s+from\s+(["'][^"']+["']\s*;?\s*)$""")
        .replace(code) { m ->
          val cleaned =
            m.groupValues[1]
              .split(',')
              .mapNotNull { part ->
                val t = part.trim()
                when {
                  t.isEmpty() -> null
                  t.startsWith("type ") -> null
                  else -> t
                }
              }
              .joinToString(", ")
          if (cleaned.isBlank()) "" else "import { $cleaned } from ${m.groupValues[2]}"
        }

    // Re-exports: export * from 'mod' / export * as ns from 'mod' / export { a, b } from 'mod'
    code =
      Regex("""(?m)^\s*export\s+\*\s+as\s+([A-Za-z_$][\w$]*)\s+from\s+["']([^"']+)["']\s*;?\s*$""")
        .replace(code) { m ->
          "exports.${m.groupValues[1]} = __require(${JSONObject.quote(m.groupValues[2])});"
        }
    code =
      Regex("""(?m)^\s*export\s+\*\s+from\s+["']([^"']+)["']\s*;?\s*$""")
        .replace(code) { m ->
          "Object.assign(exports, __require(${JSONObject.quote(m.groupValues[1])}));"
        }
    code =
      Regex("""(?m)^\s*export\s+\{([^}]+)\}\s+from\s+["']([^"']+)["']\s*;?\s*$""")
        .replace(code) { m ->
          val spec = JSONObject.quote(m.groupValues[2])
          val assignments =
            m.groupValues[1]
              .split(',')
              .mapNotNull { raw ->
                val seg = raw.trim().removePrefix("type ").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                if (seg.contains(" as ")) {
                  val (orig, alias) = seg.split(" as ", limit = 2).map { it.trim() }
                  "exports.$alias = __reexp.$orig;"
                } else {
                  "exports.$seg = __reexp.$seg;"
                }
              }
              .joinToString(" ")
          "const __reexp = __require($spec); $assignments"
        }

    val importStarRe = Regex("""(?m)^\s*import\s+\*\s+as\s+([A-Za-z_$][\w$]*)\s+from\s+["']([^"']+)["']\s*;?\s*$""")
    code = importStarRe.replace(code) { m ->
      "const ${m.groupValues[1]} = __require(${JSONObject.quote(m.groupValues[2])});"
    }

    val importDefaultAndNamedRe = Regex("""(?m)^\s*import\s+([A-Za-z_$][\w$]*)\s*,\s*\{([^}]+)\}\s+from\s+["']([^"']+)["']\s*;?\s*$""")
    code = importDefaultAndNamedRe.replace(code) { m ->
      val defName = m.groupValues[1]
      val named = m.groupValues[2].replace(" as ", ": ")
      val spec = JSONObject.quote(m.groupValues[3])
      "const __imp_$defName = __require($spec); const $defName = (__imp_$defName && (typeof __imp_$defName === 'object' || typeof __imp_$defName === 'function') && 'default' in __imp_$defName) ? __imp_$defName.default : __imp_$defName; const {$named} = __imp_$defName;"
    }

    val importDefaultRe = Regex("""(?m)^\s*import\s+([A-Za-z_$][\w$]*)\s+from\s+["']([^"']+)["']\s*;?\s*$""")
    code = importDefaultRe.replace(code) { m ->
      val defName = m.groupValues[1]
      val spec = JSONObject.quote(m.groupValues[2])
      "const __imp_$defName = __require($spec); const $defName = (__imp_$defName && (typeof __imp_$defName === 'object' || typeof __imp_$defName === 'function') && 'default' in __imp_$defName) ? __imp_$defName.default : __imp_$defName;"
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
    val exportedNames = mutableListOf<String>()
    Regex("""(?m)^\s*export\s+(?:async\s+)?function\s+([A-Za-z_$][\w$]*)""").findAll(code).forEach {
      exportedNames += it.groupValues[1]
    }
    Regex("""(?m)^\s*export\s+(?:const|let|var|class)\s+([A-Za-z_$][\w$]*)""").findAll(code).forEach {
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
    workspace.bindings.forEach { b: CfWorkerBinding ->
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

  internal fun buildHarnessJs(payloadJson: String): String {
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

  const __builtins = {};
  (function __initNodeAndCloudflareBuiltins() {
    class EventEmitter {
      constructor() {
        this._events = new Map();
        this._maxListeners = 10;
      }
      setMaxListeners(n) { this._maxListeners = n; return this; }
      getMaxListeners() { return this._maxListeners; }
      on(evt, fn) {
        if (!this._events.has(evt)) this._events.set(evt, []);
        this._events.get(evt).push(fn);
        return this;
      }
      addListener(evt, fn) { return this.on(evt, fn); }
      prependListener(evt, fn) {
        if (!this._events.has(evt)) this._events.set(evt, []);
        this._events.get(evt).unshift(fn);
        return this;
      }
      once(evt, fn) {
        const wrapper = (...args) => {
          this.off(evt, wrapper);
          fn.apply(this, args);
        };
        wrapper._orig = fn;
        return this.on(evt, wrapper);
      }
      prependOnceListener(evt, fn) {
        const wrapper = (...args) => {
          this.off(evt, wrapper);
          fn.apply(this, args);
        };
        wrapper._orig = fn;
        return this.prependListener(evt, wrapper);
      }
      off(evt, fn) {
        const list = this._events.get(evt);
        if (!list) return this;
        const filtered = list.filter(item => item !== fn && item._orig !== fn);
        if (filtered.length) this._events.set(evt, filtered);
        else this._events.delete(evt);
        return this;
      }
      removeListener(evt, fn) { return this.off(evt, fn); }
      removeAllListeners(evt) {
        if (evt === undefined) this._events.clear();
        else this._events.delete(evt);
        return this;
      }
      listeners(evt) { return (this._events.get(evt) || []).slice(); }
      listenerCount(evt) { return (this._events.get(evt) || []).length; }
      eventNames() { return Array.from(this._events.keys()); }
      emit(evt, ...args) {
        const list = this._events.get(evt);
        if (!list || !list.length) return false;
        for (const fn of list.slice()) fn.apply(this, args);
        return true;
      }
    }
    EventEmitter.EventEmitter = EventEmitter;
    EventEmitter.default = EventEmitter;

    const __utf8Enc = new TextEncoder();
    const __utf8Dec = new TextDecoder('utf-8', { fatal: false });
    class NodeBuffer extends Uint8Array {
      static isBuffer(obj) { return obj instanceof NodeBuffer || obj instanceof Uint8Array; }
      static byteLength(input, enc) {
        if (typeof input === 'string') return NodeBuffer.from(input, enc).length;
        return input && typeof input.byteLength === 'number' ? input.byteLength : 0;
      }
      static alloc(size, fill = 0, enc = 'utf8') {
        const buf = new NodeBuffer(size);
        if (fill !== undefined && fill !== 0) buf.fill(typeof fill === 'string' ? fill.charCodeAt(0) : fill);
        return buf;
      }
      static allocUnsafe(size) { return new NodeBuffer(size); }
      static allocUnsafeSlow(size) { return new NodeBuffer(size); }
      static concat(list, totalLength) {
        const arrs = (list || []).map(x => NodeBuffer.from(x));
        const len = totalLength !== undefined ? totalLength : arrs.reduce((s, a) => s + a.length, 0);
        const out = new NodeBuffer(len);
        let offset = 0;
        for (const a of arrs) {
          if (offset >= len) break;
          const slice = offset + a.length > len ? a.subarray(0, len - offset) : a;
          out.set(slice, offset);
          offset += slice.length;
        }
        return out;
      }
      static compare(a, b) {
        const ba = NodeBuffer.from(a);
        const bb = NodeBuffer.from(b);
        const minLen = Math.min(ba.length, bb.length);
        for (let i = 0; i < minLen; i++) {
          if (ba[i] !== bb[i]) return ba[i] < bb[i] ? -1 : 1;
        }
        return ba.length === bb.length ? 0 : (ba.length < bb.length ? -1 : 1);
      }
      static from(value, enc = 'utf8', len) {
        if (value instanceof NodeBuffer) return new NodeBuffer(value);
        if (value instanceof ArrayBuffer) {
          const offset = typeof enc === 'number' ? enc : 0;
          const view = len !== undefined ? new Uint8Array(value, offset, len) : new Uint8Array(value, offset);
          return new NodeBuffer(view);
        }
        if (ArrayBuffer.isView(value)) {
          return new NodeBuffer(new Uint8Array(value.buffer, value.byteOffset, value.byteLength));
        }
        if (Array.isArray(value)) return new NodeBuffer(value);
        if (typeof value === 'string') {
          const e = String(enc || 'utf8').toLowerCase();
          if (e === 'hex') {
            const clean = value.replace(/[^0-9a-fA-F]/g, '');
            const out = new NodeBuffer(Math.floor(clean.length / 2));
            for (let i = 0; i < out.length; i++) out[i] = parseInt(clean.slice(i * 2, i * 2 + 2), 16);
            return out;
          }
          if (e === 'base64' || e === 'base64url') {
            let b64 = e === 'base64url' ? value.replace(/-/g, '+').replace(/_/g, '/') : value;
            while (b64.length % 4) b64 += '=';
            const bin = atob(b64);
            const out = new NodeBuffer(bin.length);
            for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
            return out;
          }
          if (e === 'latin1' || e === 'binary' || e === 'ascii') {
            const out = new NodeBuffer(value.length);
            for (let i = 0; i < value.length; i++) out[i] = value.charCodeAt(i) & 0xff;
            return out;
          }
          return new NodeBuffer(__utf8Enc.encode(value));
        }
        return new NodeBuffer(0);
      }
      toString(enc = 'utf8', start = 0, end = this.length) {
        const sub = this.subarray(start, end);
        const e = String(enc || 'utf8').toLowerCase();
        if (e === 'hex') return Array.from(sub).map(b => b.toString(16).padStart(2, '0')).join('');
        if (e === 'base64' || e === 'base64url') {
          let bin = '';
          for (let i = 0; i < sub.length; i++) bin += String.fromCharCode(sub[i]);
          const b64 = btoa(bin);
          return e === 'base64url' ? b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '') : b64;
        }
        if (e === 'latin1' || e === 'binary' || e === 'ascii') {
          let out = '';
          for (let i = 0; i < sub.length; i++) out += String.fromCharCode(sub[i]);
          return out;
        }
        return __utf8Dec.decode(sub);
      }
      toJSON() { return { type: 'Buffer', data: Array.from(this) }; }
      equals(other) { return NodeBuffer.compare(this, other) === 0; }
      compare(other) { return NodeBuffer.compare(this, other); }
      copy(target, targetStart = 0, sourceStart = 0, sourceEnd = this.length) {
        const slice = this.subarray(sourceStart, sourceEnd);
        target.set(slice, targetStart);
        return slice.length;
      }
      write(str, offset = 0, length, enc = 'utf8') {
        const src = NodeBuffer.from(str, enc);
        const n = Math.min(src.length, length !== undefined ? length : this.length - offset, this.length - offset);
        if (n > 0) this.set(src.subarray(0, n), offset);
        return n;
      }
      readUInt8(o = 0) { return this[o]; }
      readUInt16BE(o = 0) { return (this[o] << 8) | this[o + 1]; }
      readUInt16LE(o = 0) { return this[o] | (this[o + 1] << 8); }
      readUInt32BE(o = 0) { return ((this[o] * 0x1000000) + ((this[o + 1] << 16) | (this[o + 2] << 8) | this[o + 3])) >>> 0; }
      readUInt32LE(o = 0) { return ((this[o] | (this[o + 1] << 8) | (this[o + 2] << 16)) + (this[o + 3] * 0x1000000)) >>> 0; }
      writeUInt8(v, o = 0) { this[o] = v & 0xff; return o + 1; }
      writeUInt32BE(v, o = 0) { this[o] = (v >>> 24) & 0xff; this[o + 1] = (v >>> 16) & 0xff; this[o + 2] = (v >>> 8) & 0xff; this[o + 3] = v & 0xff; return o + 4; }
    }
    if (typeof globalThis.Buffer === 'undefined') globalThis.Buffer = NodeBuffer;
    if (typeof globalThis.global === 'undefined') globalThis.global = globalThis;

    class StreamBase extends EventEmitter {
      constructor(opts) { super(); this._opts = opts || {}; this.destroyed = false; }
      pipe(dest, opts) {
        this.on('data', (chunk) => {
          if (typeof dest.write === 'function') dest.write(chunk);
        });
        this.on('end', () => {
          if (!opts || opts.end !== false) {
            if (typeof dest.end === 'function') dest.end();
          }
        });
        this.on('error', (err) => {
          if (dest.listenerCount && dest.listenerCount('error') > 0) dest.emit('error', err);
        });
        if (dest && typeof dest.emit === 'function') dest.emit('pipe', this);
        return dest;
      }
      destroy(err) {
        if (this.destroyed) return this;
        this.destroyed = true;
        if (err) this.emit('error', err);
        this.emit('close');
        return this;
      }
    }
    class Readable extends StreamBase {
      constructor(opts) {
        super(opts);
        this.readable = true;
        this._queue = [];
        this._ended = false;
        this._flowing = false;
        this._readFn = opts && opts.read ? opts.read.bind(this) : null;
      }
      _read() { if (this._readFn) this._readFn(); }
      push(chunk) {
        if (chunk === null) {
          this._ended = true;
          this.emit('end');
          return false;
        }
        if (this.listenerCount('data') > 0) {
          this.emit('data', chunk);
        } else {
          this._queue.push(chunk);
        }
        return true;
      }
      on(evt, fn) {
        super.on(evt, fn);
        if (evt === 'data') {
          this._flowing = true;
          while (this._queue.length) this.emit('data', this._queue.shift());
          this._read();
        }
        return this;
      }
      read() {
        if (!this._queue.length) this._read();
        return this._queue.length ? this._queue.shift() : null;
      }
      pause() { this._flowing = false; return this; }
      resume() { this._flowing = true; while (this._queue.length) this.emit('data', this._queue.shift()); return this; }
      setEncoding() { return this; }
      static from(iterable) {
        const r = new Readable();
        Promise.resolve().then(async () => {
          try {
            if (iterable && typeof iterable[Symbol.asyncIterator] === 'function') {
              for await (const chunk of iterable) r.push(chunk);
            } else if (iterable && typeof iterable[Symbol.iterator] === 'function' && typeof iterable !== 'string') {
              for (const chunk of iterable) r.push(chunk);
            } else if (iterable !== undefined && iterable !== null) {
              r.push(iterable);
            }
            r.push(null);
          } catch (e) { r.destroy(e); }
        });
        return r;
      }
      static toWeb(readable) {
        return new ReadableStream({
          start(controller) {
            readable.on('data', (c) => controller.enqueue(c));
            readable.on('end', () => controller.close());
            readable.on('error', (e) => controller.error(e));
          }
        });
      }
      static fromWeb(webStream) {
        const r = new Readable();
        const reader = webStream.getReader();
        (async () => {
          try {
            while (true) {
              const { value, done } = await reader.read();
              if (done) { r.push(null); break; }
              r.push(value);
            }
          } catch (e) { r.destroy(e); }
        })();
        return r;
      }
      async *[Symbol.asyncIterator]() {
        const buf = [];
        let done = false;
        let err = null;
        let wake = null;
        const notify = () => { if (wake) { const w = wake; wake = null; w(); } };
        this.on('data', c => { buf.push(c); notify(); });
        this.on('end', () => { done = true; notify(); });
        this.on('error', e => { err = e; done = true; notify(); });
        while (!done || buf.length) {
          if (buf.length) yield buf.shift();
          else await new Promise(res => { wake = res; });
        }
        if (err) throw err;
      }
    }
    class Writable extends StreamBase {
      constructor(opts) {
        super(opts);
        this.writable = true;
        this.writableEnded = false;
        this._writeFn = opts && opts.write ? opts.write.bind(this) : null;
        this._finalFn = opts && opts.final ? opts.final.bind(this) : null;
      }
      write(chunk, encoding, cb) {
        if (typeof encoding === 'function') { cb = encoding; encoding = 'utf8'; }
        if (this._writeFn) {
          this._writeFn(chunk, encoding || 'utf8', (err) => {
            if (err) this.emit('error', err);
            if (cb) cb(err);
          });
        } else {
          if (cb) cb();
        }
        return true;
      }
      end(chunk, encoding, cb) {
        if (typeof chunk === 'function') { cb = chunk; chunk = undefined; }
        else if (typeof encoding === 'function') { cb = encoding; encoding = undefined; }
        if (chunk !== undefined) this.write(chunk, encoding);
        this.writableEnded = true;
        const done = (err) => {
          if (err) this.emit('error', err);
          this.emit('finish');
          if (cb) cb(err);
        };
        if (this._finalFn) this._finalFn(done);
        else done();
        return this;
      }
      static toWeb(writable) {
        return new WritableStream({
          write(c) { return new Promise((res, rej) => writable.write(c, e => e ? rej(e) : res())); },
          close() { return new Promise(res => writable.end(res)); }
        });
      }
    }
    class Duplex extends Readable {
      constructor(opts) {
        super(opts);
        this.writable = true;
        this.writableEnded = false;
        this._writeFn = opts && opts.write ? opts.write.bind(this) : null;
        this._finalFn = opts && opts.final ? opts.final.bind(this) : null;
      }
      write(chunk, encoding, cb) { return Writable.prototype.write.call(this, chunk, encoding, cb); }
      end(chunk, encoding, cb) { return Writable.prototype.end.call(this, chunk, encoding, cb); }
    }
    class Transform extends Duplex {
      constructor(opts) {
        super(opts);
        this._transformFn = opts && opts.transform ? opts.transform.bind(this) : null;
        this._flushFn = opts && opts.flush ? opts.flush.bind(this) : null;
      }
      write(chunk, encoding, cb) {
        if (typeof encoding === 'function') { cb = encoding; encoding = 'utf8'; }
        if (this._transformFn) {
          this._transformFn(chunk, encoding || 'utf8', (err, out) => {
            if (out !== undefined && out !== null) this.push(out);
            if (err) this.emit('error', err);
            if (cb) cb(err);
          });
        } else {
          this.push(chunk);
          if (cb) cb();
        }
        return true;
      }
      end(chunk, encoding, cb) {
        if (typeof chunk === 'function') { cb = chunk; chunk = undefined; }
        else if (typeof encoding === 'function') { cb = encoding; encoding = undefined; }
        if (chunk !== undefined) this.write(chunk, encoding);
        this.writableEnded = true;
        const finish = (err) => {
          if (err) this.emit('error', err);
          this.push(null);
          this.emit('finish');
          if (cb) cb(err);
        };
        if (this._flushFn) this._flushFn(finish);
        else finish();
        return this;
      }
    }
    class PassThrough extends Transform {}
    function pipeline(...streams) {
      const cb = typeof streams[streams.length - 1] === 'function' ? streams.pop() : null;
      try {
        let cur = streams[0];
        for (let i = 1; i < streams.length; i++) cur = cur.pipe(streams[i]);
        const last = streams[streams.length - 1];
        if (cb && last) {
          last.on('finish', () => cb(null));
          last.on('end', () => cb(null));
          last.on('error', (e) => cb(e));
        }
        return last;
      } catch (e) {
        if (cb) cb(e); else throw e;
      }
    }
    function finished(stream, opts, cb) {
      if (typeof opts === 'function') { cb = opts; }
      if (stream) {
        stream.on('end', () => cb && cb());
        stream.on('finish', () => cb && cb());
        stream.on('error', (e) => cb && cb(e));
      }
      return () => {};
    }
    StreamBase.Stream = StreamBase;
    StreamBase.Readable = Readable;
    StreamBase.Writable = Writable;
    StreamBase.Duplex = Duplex;
    StreamBase.Transform = Transform;
    StreamBase.PassThrough = PassThrough;
    StreamBase.EventEmitter = EventEmitter;
    StreamBase.pipeline = pipeline;
    StreamBase.finished = finished;
    StreamBase.promises = {
      pipeline: (...streams) => new Promise((res, rej) => pipeline(...streams, (e) => e ? rej(e) : res())),
      finished: (stream, opts) => new Promise((res, rej) => finished(stream, opts || {}, (e) => e ? rej(e) : res()))
    };
    StreamBase.default = StreamBase;
    const streamMod = StreamBase;
    async function __collectStream(s) {
      const chunks = [];
      for await (const c of s) chunks.push(NodeBuffer.from(c));
      return NodeBuffer.concat(chunks);
    }
    const streamConsumersMod = {
      buffer: __collectStream,
      arrayBuffer: async (s) => (await __collectStream(s)).buffer,
      text: async (s) => (await __collectStream(s)).toString('utf8'),
      json: async (s) => JSON.parse((await __collectStream(s)).toString('utf8')),
      blob: async (s) => new Blob([await __collectStream(s)])
    };
    const streamWebMod = {
      ReadableStream: globalThis.ReadableStream,
      WritableStream: globalThis.WritableStream,
      TransformStream: globalThis.TransformStream,
      ByteLengthQueuingStrategy: globalThis.ByteLengthQueuingStrategy,
      CountQueuingStrategy: globalThis.CountQueuingStrategy,
      TextEncoderStream: globalThis.TextEncoderStream,
      TextDecoderStream: globalThis.TextDecoderStream
    };

    const nodeProcess = {
      env: {},
      version: 'v20.11.0',
      versions: { node: '20.11.0', v8: '11.3.244', webview: '1.0' },
      platform: 'linux',
      arch: 'arm64',
      pid: 1,
      ppid: 0,
      argv: ['worker', __payload.entryPath],
      cwd: () => '/',
      chdir: () => {},
      nextTick: (fn, ...args) => Promise.resolve().then(() => fn(...args)),
      hrtime: Object.assign((prev) => {
        const ms = performance.now();
        const sec = Math.floor(ms / 1000);
        const ns = Math.floor((ms % 1000) * 1e6);
        return prev ? [sec - prev[0], ns - prev[1]] : [sec, ns];
      }, { bigint: () => BigInt(Math.floor(performance.now() * 1e6)) }),
      stdout: { write: (s) => { __pushLog('LOG', [String(s).trimEnd()]); return true; } },
      stderr: { write: (s) => { __pushLog('ERR', [String(s).trimEnd()]); return true; } },
      on: () => nodeProcess,
      once: () => nodeProcess,
      off: () => nodeProcess,
      emit: () => false,
      exit: () => {}
    };
    for (const b of (__payload.bindings || [])) {
      if (b.type === 'plain_text' || b.type === 'secret_text') {
        nodeProcess.env[b.name] = b.text || '';
      }
    }
    if (typeof globalThis.process === 'undefined') globalThis.process = nodeProcess;

    const pathMod = {
      sep: '/',
      delimiter: ':',
      normalize(p) {
        const isAbs = String(p).startsWith('/');
        const parts = [];
        for (const seg of String(p).split('/')) {
          if (!seg || seg === '.') continue;
          if (seg === '..') { if (parts.length && parts[parts.length - 1] !== '..') parts.pop(); else if (!isAbs) parts.push('..'); }
          else parts.push(seg);
        }
        return (isAbs ? '/' : '') + parts.join('/') || (isAbs ? '/' : '.');
      },
      join(...args) { return pathMod.normalize(args.filter(Boolean).join('/')); },
      resolve(...args) {
        let out = '';
        for (let i = args.length - 1; i >= 0; i--) {
          const p = String(args[i] || '');
          if (!p) continue;
          out = out ? (p + '/' + out) : p;
          if (p.startsWith('/')) break;
        }
        return pathMod.normalize(out.startsWith('/') ? out : ('/' + out));
      },
      isAbsolute: (p) => String(p).startsWith('/'),
      dirname(p) {
        const s = String(p).replace(/\/+$/, '');
        const idx = s.lastIndexOf('/');
        if (idx < 0) return '.';
        return idx === 0 ? '/' : s.slice(0, idx);
      },
      basename(p, ext) {
        const base = String(p).replace(/\/+$/, '').split('/').pop() || '';
        return ext && base.endsWith(ext) ? base.slice(0, base.length - ext.length) : base;
      },
      extname(p) {
        const b = pathMod.basename(p);
        const i = b.lastIndexOf('.');
        return i > 0 ? b.slice(i) : '';
      },
      parse(p) {
        const dir = pathMod.dirname(p);
        const base = pathMod.basename(p);
        const ext = pathMod.extname(p);
        return { root: String(p).startsWith('/') ? '/' : '', dir, base, ext, name: ext ? base.slice(0, -ext.length) : base };
      }
    };
    pathMod.posix = pathMod;
    pathMod.default = pathMod;

    const querystringMod = {
      parse(str) {
        const out = {};
        const p = new URLSearchParams(String(str || '').replace(/^\?/, ''));
        for (const [k, v] of p.entries()) {
          if (k in out) out[k] = Array.isArray(out[k]) ? out[k].concat(v) : [out[k], v];
          else out[k] = v;
        }
        return out;
      },
      stringify(obj) {
        const p = new URLSearchParams();
        for (const [k, v] of Object.entries(obj || {})) {
          if (Array.isArray(v)) v.forEach(item => p.append(k, String(item)));
          else if (v !== undefined) p.append(k, String(v));
        }
        return p.toString();
      },
      escape: encodeURIComponent,
      unescape: decodeURIComponent
    };

    const utilMod = {
      inherits(ctor, superCtor) {
        if (ctor && superCtor) {
          ctor.super_ = superCtor;
          Object.setPrototypeOf(ctor.prototype, superCtor.prototype);
        }
      },
      promisify(fn) {
        return function(...args) {
          return new Promise((res, rej) => fn.call(this, ...args, (err, val) => err ? rej(err) : res(val)));
        };
      },
      callbackify(fn) {
        return function(...args) {
          const cb = args.pop();
          Promise.resolve(fn.apply(this, args)).then(v => cb(null, v), e => cb(e));
        };
      },
      deprecate: (fn) => fn,
      inspect: (v) => __serialize(v),
      format: (...args) => args.map(__serialize).join(' '),
      TextEncoder: globalThis.TextEncoder,
      TextDecoder: globalThis.TextDecoder,
      types: {
        isUint8Array: (v) => v instanceof Uint8Array,
        isArrayBuffer: (v) => v instanceof ArrayBuffer,
        isDate: (v) => v instanceof Date,
        isRegExp: (v) => v instanceof RegExp,
        isPromise: (v) => v instanceof Promise,
        isMap: (v) => v instanceof Map,
        isSet: (v) => v instanceof Set
      }
    };

    const urlMod = {
      URL: globalThis.URL,
      URLSearchParams: globalThis.URLSearchParams,
      parse(u) {
        try {
          const parsed = new URL(u, 'https://local.edgecode.app');
          return { href: parsed.href, protocol: parsed.protocol, host: parsed.host, hostname: parsed.hostname, port: parsed.port, pathname: parsed.pathname, search: parsed.search, path: parsed.pathname + parsed.search, query: parsed.search.replace(/^\?/, ''), hash: parsed.hash };
        } catch (e) { return { href: u, pathname: u }; }
      },
      format: (obj) => (obj && obj.href) ? obj.href : String(obj || ''),
      resolve: (from, to) => new URL(to, from).href,
      fileURLToPath: (u) => String(u).replace(/^file:\/\//, ''),
      pathToFileURL: (p) => new URL('file://' + p)
    };

    const stringDecoderMod = {
      StringDecoder: class StringDecoder {
        constructor(enc = 'utf8') { this._dec = new TextDecoder(enc); }
        write(buf) { return this._dec.decode(NodeBuffer.from(buf), { stream: true }); }
        end(buf) { return buf ? this._dec.decode(NodeBuffer.from(buf)) : this._dec.decode(); }
      }
    };

    const webCrypto = globalThis.crypto;
    const cryptoMod = {
      webcrypto: webCrypto,
      subtle: webCrypto ? webCrypto.subtle : undefined,
      constants: {},
      getRandomValues: (arr) => webCrypto.getRandomValues(arr),
      randomUUID: () => webCrypto.randomUUID ? webCrypto.randomUUID() : '00000000-0000-4000-8000-000000000000',
      randomBytes(size, cb) {
        const buf = NodeBuffer.alloc(size);
        webCrypto.getRandomValues(buf);
        if (cb) { nodeProcess.nextTick(() => cb(null, buf)); return undefined; }
        return buf;
      },
      randomFillSync(buf, offset = 0, size) {
        const len = size !== undefined ? size : buf.byteLength - offset;
        const view = new Uint8Array(buf.buffer, buf.byteOffset + offset, len);
        webCrypto.getRandomValues(view);
        return buf;
      },
      randomInt(min, max) {
        if (max === undefined) { max = min; min = 0; }
        const arr = new Uint32Array(1);
        webCrypto.getRandomValues(arr);
        return min + (arr[0] % Math.max(1, max - min));
      },
      timingSafeEqual(a, b) {
        const ba = NodeBuffer.from(a);
        const bb = NodeBuffer.from(b);
        if (ba.length !== bb.length) throw new RangeError('Input buffers must have the same byte length');
        let diff = 0;
        for (let i = 0; i < ba.length; i++) diff |= ba[i] ^ bb[i];
        return diff === 0;
      },
      createHash() {
        const chunks = [];
        return {
          update(d) { chunks.push(NodeBuffer.from(d)); return this; },
          digest(enc) {
            const total = NodeBuffer.concat(chunks);
            // Deterministic 256-bit FNV/xorshift digest when sync hash is requested in browser WebView
            const out = NodeBuffer.alloc(32);
            let h1 = 0x811c9dc5 >>> 0;
            let h2 = 0x9e3779b9 >>> 0;
            for (let i = 0; i < total.length; i++) {
              h1 = Math.imul(h1 ^ total[i], 0x01000193) >>> 0;
              h2 = Math.imul(h2 ^ (total[i] + i), 0x85ebca6b) >>> 0;
            }
            for (let i = 0; i < 32; i++) {
              h1 = (h1 ^ (h1 << 13)) >>> 0; h1 = (h1 ^ (h1 >>> 17)) >>> 0; h1 = (h1 ^ (h1 << 5)) >>> 0;
              out[i] = (h1 ^ h2 ^ i) & 0xff;
            }
            return enc ? out.toString(enc) : out;
          }
        };
      },
      createHmac(alg, _key) { return cryptoMod.createHash(alg); }
    };
    cryptoMod.default = cryptoMod;

    class AsyncLocalStorage {
      constructor() { this._store = undefined; }
      getStore() { return this._store; }
      enterWith(store) { this._store = store; }
      disable() { this._store = undefined; }
      run(store, fn, ...args) {
        const prev = this._store;
        this._store = store;
        let isAsync = false;
        try {
          const out = fn(...args);
          if (out && typeof out.then === 'function') {
            isAsync = true;
            return Promise.resolve(out).finally(() => {
              this._store = prev;
            });
          }
          return out;
        } finally {
          if (!isAsync) this._store = prev;
        }
      }
      exit(fn, ...args) { return this.run(undefined, fn, ...args); }
    }
    const asyncHooksMod = {
      AsyncLocalStorage,
      AsyncResource: class AsyncResource {
        constructor(type) { this.type = type; }
        runInAsyncScope(fn, thisArg, ...args) { return fn.apply(thisArg, args); }
        emitDestroy() { return this; }
        static bind(fn) { return fn; }
      }
    };

    function assertFn(val, msg) {
      if (!val) throw new Error(msg || 'Assertion failed');
    }
    assertFn.ok = assertFn;
    assertFn.strictEqual = (a, b, msg) => { if (a !== b) throw new Error(msg || ('Expected ' + a + ' === ' + b)); };
    assertFn.notStrictEqual = (a, b, msg) => { if (a === b) throw new Error(msg || 'Expected values to differ'); };
    assertFn.equal = (a, b, msg) => { if (a != b) throw new Error(msg || ('Expected ' + a + ' == ' + b)); };
    assertFn.notEqual = (a, b, msg) => { if (a == b) throw new Error(msg || 'Expected values to differ'); };
    assertFn.deepStrictEqual = (a, b, msg) => { if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(msg || 'Deep equality failed'); };
    assertFn.fail = (msg) => { throw new Error(msg || 'Assertion failed'); };
    assertFn.strict = assertFn;

    const timersMod = {
      setTimeout: globalThis.setTimeout.bind(globalThis),
      clearTimeout: globalThis.clearTimeout.bind(globalThis),
      setInterval: globalThis.setInterval.bind(globalThis),
      clearInterval: globalThis.clearInterval.bind(globalThis),
      setImmediate: (fn, ...args) => globalThis.setTimeout(fn, 0, ...args),
      clearImmediate: globalThis.clearTimeout.bind(globalThis),
      promises: {
        setTimeout: (ms, val) => new Promise(res => globalThis.setTimeout(() => res(val), ms)),
        setImmediate: (val) => Promise.resolve(val)
      }
    };

    const noopStreamFactory = () => new PassThrough();
    const zlibMod = {
      constants: { Z_NO_FLUSH: 0, Z_FINISH: 4, Z_BEST_COMPRESSION: 9, Z_DEFAULT_COMPRESSION: -1 },
      createGzip: noopStreamFactory,
      createGunzip: noopStreamFactory,
      createDeflate: noopStreamFactory,
      createInflate: noopStreamFactory,
      createBrotliCompress: noopStreamFactory,
      createBrotliDecompress: noopStreamFactory,
      gzip: (d, cb) => cb && cb(null, NodeBuffer.from(d)),
      gunzip: (d, cb) => cb && cb(null, NodeBuffer.from(d)),
      deflate: (d, cb) => cb && cb(null, NodeBuffer.from(d)),
      inflate: (d, cb) => cb && cb(null, NodeBuffer.from(d)),
      gzipSync: (d) => NodeBuffer.from(d),
      gunzipSync: (d) => NodeBuffer.from(d),
      deflateSync: (d) => NodeBuffer.from(d),
      inflateSync: (d) => NodeBuffer.from(d)
    };

    const stubMod = (name) => new Proxy({}, {
      get: (_t, prop) => {
        if (prop === 'default') return stubMod(name);
        if (prop === '__esModule') return true;
        return () => { throw new Error(name + '.' + String(prop) + ' is not supported in on-device local runner'); };
      }
    });

    const fsMod = {
      existsSync: (p) => Object.prototype.hasOwnProperty.call(__payload.modules, String(p).replace(/^\//, '')),
      readFileSync: (p) => {
        const clean = String(p).replace(/^\//, '');
        if (Object.prototype.hasOwnProperty.call(__payload.modules, clean)) return __payload.modules[clean];
        throw new Error('ENOENT: no such file: ' + p);
      },
      promises: {
        readFile: async (p) => fsMod.readFileSync(p)
      }
    };

    const cfWorkersMod = {
      WorkerEntrypoint: class WorkerEntrypoint {
        constructor(ctx, env) { this.ctx = ctx; this.env = env; }
      },
      DurableObject: class DurableObject {
        constructor(ctx, env) { this.ctx = ctx; this.env = env; }
      },
      WorkflowEntrypoint: class WorkflowEntrypoint {
        constructor(ctx, env) { this.ctx = ctx; this.env = env; }
      },
      waitUntil: (p) => Promise.resolve(p),
      env: {}
    };

    const register = (key, mod) => {
      __builtins[key] = mod;
      if (!key.startsWith('node:') && !key.startsWith('cloudflare:')) {
        __builtins['node:' + key] = mod;
      }
    };
    register('events', EventEmitter);
    register('stream', streamMod);
    register('stream/promises', streamMod.promises);
    register('stream/consumers', streamConsumersMod);
    register('stream/web', streamWebMod);
    register('buffer', { Buffer: NodeBuffer, Blob: globalThis.Blob, default: { Buffer: NodeBuffer } });
    register('crypto', cryptoMod);
    register('util', utilMod);
    register('path', pathMod);
    register('path/posix', pathMod);
    register('url', urlMod);
    register('querystring', querystringMod);
    register('string_decoder', stringDecoderMod);
    register('process', nodeProcess);
    register('assert', assertFn);
    register('assert/strict', assertFn);
    register('async_hooks', asyncHooksMod);
    register('timers', timersMod);
    register('timers/promises', timersMod.promises);
    register('zlib', zlibMod);
    register('fs', fsMod);
    register('fs/promises', fsMod.promises);
    register('diagnostics_channel', { channel: () => ({ subscribe: () => {}, unsubscribe: () => {}, publish: () => {}, hasSubscribers: false }) });
    register('perf_hooks', { performance: globalThis.performance });
    register('os', { EOL: '\n', platform: () => 'linux', arch: () => 'arm64', cpus: () => [], totalmem: () => 0, freemem: () => 0, tmpdir: () => '/tmp', homedir: () => '/' });
    register('tty', { isatty: () => false });
    register('module', { createRequire: () => __loadModule, builtinModules: Object.keys(__builtins) });
    register('http', { STATUS_CODES: { 200: 'OK', 400: 'Bad Request', 404: 'Not Found', 500: 'Internal Server Error' }, METHODS: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'] });
    register('https', __builtins['http']);
    register('net', { isIP: (s) => /^\d+\.\d+\.\d+\.\d+$/.test(String(s)) ? 4 : 0, isIPv4: (s) => /^\d+\.\d+\.\d+\.\d+$/.test(String(s)), isIPv6: () => false, Socket: StreamBase, Server: EventEmitter });
    register('tls', { TLSSocket: StreamBase });
    register('dns', stubMod('node:dns'));
    register('child_process', stubMod('node:child_process'));
    register('vm', stubMod('node:vm'));
    register('v8', { setFlagsFromString: () => {}, getHeapStatistics: () => ({}) });
    register('readline', { createInterface: () => new EventEmitter() });
    __builtins['cloudflare:workers'] = cfWorkersMod;
    __builtins['cloudflare:sockets'] = { connect: () => { throw new Error('cloudflare:sockets connect() is unavailable offline'); } };
    __builtins['cloudflare:email'] = { EmailMessage: class EmailMessage { constructor(from, to, raw) { this.from = from; this.to = to; this.raw = raw; } } };
  })();

  const __cache = {};
  function __loadModule(path) {
    if (Object.prototype.hasOwnProperty.call(__builtins, path)) {
      return __builtins[path];
    }
    if (__cache[path]) return __cache[path].exports;
    const code = __payload.modules[path];
    if (typeof code !== 'string') {
      throw new Error('Module not found: ' + path);
    }
    const module = { exports: {} };
    __cache[path] = module;
    const localRequire = function(spec) {
      if (Object.prototype.hasOwnProperty.call(__builtins, spec)) {
        return __builtins[spec];
      }
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
