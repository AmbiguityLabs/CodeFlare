package com.example.terminal

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.remote.WorkerWorkspace
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object CodeRunnerEngine {
  private val DIRECT_COMMANDS =
    setOf("run", "node", "nodejs", "js", "ts", "tsx", "ts-node", "npm", "npx", "sh", "bash")

  fun isCodeRunnerCommand(
    cmd: String,
    tokens: List<String>,
    currentDir: File,
  ): Boolean {
    if (cmd in DIRECT_COMMANDS) {
      if (cmd == "npx" && tokens.getOrNull(1) == "wrangler") return false
      if ((cmd == "sh" || cmd == "bash") && tokens.getOrNull(1) in setOf("-c", "-lc")) return false
      if ((cmd == "sh" || cmd == "bash") && tokens.size == 1) return false
      return true
    }
    if (cmd.startsWith("./") || cmd.startsWith("../")) {
      val f = File(currentDir, cmd)
      return f.exists() && f.isFile
    }
    return false
  }

  suspend fun execute(
    context: Context,
    tokens: List<String>,
    stdin: String?,
    currentDir: File,
    workspace: WorkerWorkspace?,
    env: Map<String, String>,
    onLiveOutput: (String) -> Unit,
    onRunShellCommand: suspend (String) -> ShellExecutionResult,
  ): ShellExecutionResult {
    val cmd = tokens.firstOrNull().orEmpty()
    val args = tokens.drop(1)

    if (cmd.startsWith("./") || cmd.startsWith("../")) {
      val target = resolveFile(currentDir, cmd)
      if (!target.exists() || !target.isFile) {
        return ShellExecutionResult(
          output = "sh: $cmd: No such file or directory",
          isError = true,
          exitCode = 127,
          kind = TerminalEntryKind.ERROR,
        )
      }
      return runTargetFile(
        context = context,
        file = target,
        args = args,
        currentDir = currentDir,
        env = env,
        onLiveOutput = onLiveOutput,
        onRunShellCommand = onRunShellCommand,
      )
    }

    return when (cmd) {
      "npm" -> executeNpm(args, currentDir, workspace, onRunShellCommand)
      "npx" -> executeNpx(context, args, currentDir, workspace, env, onLiveOutput, onRunShellCommand)
      "sh", "bash" -> executeShellScript(args, currentDir, onRunShellCommand)
      "run" -> executeRun(context, args, stdin, currentDir, workspace, env, onLiveOutput, onRunShellCommand)
      "node", "nodejs", "js" -> executeJsOrTs(context, args, stdin, currentDir, workspace, env, forceTypeScript = false, onLiveOutput = onLiveOutput)
      "ts", "tsx", "ts-node" -> executeJsOrTs(context, args, stdin, currentDir, workspace, env, forceTypeScript = true, onLiveOutput = onLiveOutput)
      else -> ShellExecutionResult(output = "$cmd: unsupported runner command", isError = true, exitCode = 127, kind = TerminalEntryKind.ERROR)
    }
  }

  private suspend fun executeNpm(
    args: List<String>,
    currentDir: File,
    workspace: WorkerWorkspace?,
    onRunShellCommand: suspend (String) -> ShellExecutionResult,
  ): ShellExecutionResult {
    val sub = args.firstOrNull()?.lowercase().orEmpty()
    if (sub in setOf("-v", "--version", "version")) {
      return ShellExecutionResult(output = "10.8.2 (EdgeCode)")
    }
    if (sub.isBlank() || sub == "help" || sub == "--help") {
      return ShellExecutionResult(
        output = "Usage: npm <run <script>|test|start|dev|ls|-v>",
        kind = TerminalEntryKind.SYSTEM,
      )
    }
    val pkgFile = findPackageJson(currentDir)
    val pkgJson =
      pkgFile?.let { runCatching { JSONObject(it.readText(Charsets.UTF_8)) }.getOrNull() }

    if (sub == "ls" || sub == "list") {
      if (pkgJson == null) {
        return ShellExecutionResult("npm: no package.json in ${currentDir.name}", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
      val name = pkgJson.optString("name", workspace?.scriptName ?: "workspace")
      val ver = pkgJson.optString("version", "0.0.0")
      val sb = StringBuilder("$name@$ver")
      listOf("dependencies", "devDependencies").forEach { key ->
        val deps = pkgJson.optJSONObject(key)
        if (deps != null && deps.length() > 0) {
          deps.keys().forEach { dep ->
            sb.appendLine()
            sb.append("├── $dep@${deps.optString(dep)}")
          }
        }
      }
      return ShellExecutionResult(output = sb.toString())
    }

    if (sub == "run" || sub == "run-script") {
      val scriptName = args.getOrNull(1)
      val scripts = pkgJson?.optJSONObject("scripts")
      if (scriptName.isNullOrBlank()) {
        if (scripts == null || scripts.length() == 0) {
          return ShellExecutionResult("(no scripts in package.json)")
        }
        val lines = buildList {
          scripts.keys().forEach { k -> add("  $k: ${scripts.optString(k)}") }
        }.joinToString("\n")
        return ShellExecutionResult(output = "Scripts:\n$lines", kind = TerminalEntryKind.SYSTEM)
      }
      val scriptCmd = scripts?.optString(scriptName)?.takeIf { it.isNotBlank() }
        ?: return ShellExecutionResult("npm error: Missing script: \"$scriptName\"", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      val extraArgs = args.drop(2).filter { it != "--" }
      val fullCmd = if (extraArgs.isEmpty()) scriptCmd else "$scriptCmd ${extraArgs.joinToString(" ")}"
      return onRunShellCommand(fullCmd)
    }

    if (sub in setOf("test", "start", "dev", "build", "lint", "check", "t")) {
      val normalized = if (sub == "t") "test" else sub
      val scripts = pkgJson?.optJSONObject("scripts")
      val scriptCmd = scripts?.optString(normalized)?.takeIf { it.isNotBlank() }
      if (scriptCmd != null) {
        val extraArgs = args.drop(1).filter { it != "--" }
        val fullCmd = if (extraArgs.isEmpty()) scriptCmd else "$scriptCmd ${extraArgs.joinToString(" ")}"
        return onRunShellCommand(fullCmd)
      }
      if (normalized == "start" || normalized == "dev") {
        val mainEntry = findDefaultEntryFile(currentDir, workspace)
        if (mainEntry != null) {
          return onRunShellCommand("run ${mainEntry.name}")
        }
      }
      return ShellExecutionResult("npm error: Missing script: \"$normalized\"", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
    }

    return ShellExecutionResult(
      output = "npm: unsupported command '$sub'",
      isError = true,
      exitCode = 1,
      kind = TerminalEntryKind.ERROR,
    )
  }

  private suspend fun executeNpx(
    context: Context,
    args: List<String>,
    currentDir: File,
    workspace: WorkerWorkspace?,
    env: Map<String, String>,
    onLiveOutput: (String) -> Unit,
    onRunShellCommand: suspend (String) -> ShellExecutionResult,
  ): ShellExecutionResult {
    val tool = args.firstOrNull()?.takeIf { it.isNotBlank() }
      ?: return ShellExecutionResult("Usage: npx <command> [args...]", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
    val rest = args.drop(1)
    if (tool in setOf("tsx", "ts-node", "node", "js")) {
      return executeJsOrTs(context, rest, null, currentDir, workspace, env, forceTypeScript = tool != "node" && tool != "js", onLiveOutput = onLiveOutput)
    }
    val localBin = resolveFile(currentDir, tool)
    if (localBin.exists() && localBin.isFile) {
      return runTargetFile(
        context = context,
        file = localBin,
        args = rest,
        currentDir = currentDir,
        env = env,
        onLiveOutput = onLiveOutput,
        onRunShellCommand = onRunShellCommand,
      )
    }
    val pkgJson = findPackageJson(currentDir)?.let { runCatching { JSONObject(it.readText(Charsets.UTF_8)) }.getOrNull() }
    val scriptCmd = pkgJson?.optJSONObject("scripts")?.optString(tool)?.takeIf { it.isNotBlank() }
    if (scriptCmd != null) {
      val cmdLine = if (rest.isEmpty()) scriptCmd else "$scriptCmd ${rest.joinToString(" ")}"
      return onRunShellCommand(cmdLine)
    }
    return ShellExecutionResult("npx: '$tool' not found in workspace", isError = true, exitCode = 127, kind = TerminalEntryKind.ERROR)
  }

  private suspend fun executeShellScript(
    args: List<String>,
    currentDir: File,
    onRunShellCommand: suspend (String) -> ShellExecutionResult,
  ): ShellExecutionResult {
    val targetArg = args.firstOrNull { !it.startsWith("-") }
      ?: return ShellExecutionResult("Usage: sh <script.sh> [args...]", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
    val scriptFile = resolveFile(currentDir, targetArg)
    if (!scriptFile.exists() || !scriptFile.isFile) {
      return ShellExecutionResult("sh: $targetArg: No such file or directory", isError = true, exitCode = 127, kind = TerminalEntryKind.ERROR)
    }
    val scriptIdx = args.indexOf(targetArg)
    val scriptArgs = args.drop(scriptIdx + 1)
    return runShellFileLines(scriptFile, scriptArgs, onRunShellCommand)
  }

  private suspend fun runShellFileLines(
    scriptFile: File,
    scriptArgs: List<String>,
    onRunShellCommand: suspend (String) -> ShellExecutionResult,
  ): ShellExecutionResult {
    val rawLines = runCatching { scriptFile.readLines(Charsets.UTF_8) }.getOrNull()
      ?: return ShellExecutionResult("sh: unable to read ${scriptFile.name}", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
    val outputs = mutableListOf<String>()
    var stopOnError = false
    var lastExit = 0
    for (raw in rawLines) {
      val trimmed = raw.trim()
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
      if (trimmed == "set -e" || trimmed == "set -euo pipefail") {
        stopOnError = true
        continue
      }
      var line = trimmed
        .replace("$@", scriptArgs.joinToString(" "))
        .replace("$#", scriptArgs.size.toString())
      for (i in 1..9) {
        line = line.replace("$$i", scriptArgs.getOrNull(i - 1).orEmpty())
      }
      val res = onRunShellCommand(line)
      if (res.output.isNotBlank()) outputs.add(res.output)
      lastExit = res.exitCode
      if (stopOnError && res.isError) {
        return ShellExecutionResult(
          output = outputs.joinToString("\n"),
          isError = true,
          exitCode = res.exitCode.takeIf { it != 0 } ?: 1,
          kind = TerminalEntryKind.ERROR,
        )
      }
    }
    return ShellExecutionResult(
      output = outputs.joinToString("\n"),
      isError = lastExit != 0,
      exitCode = lastExit,
      kind = if (lastExit == 0) TerminalEntryKind.SHELL else TerminalEntryKind.ERROR,
    )
  }

  private suspend fun executeRun(
    context: Context,
    args: List<String>,
    stdin: String?,
    currentDir: File,
    workspace: WorkerWorkspace?,
    env: Map<String, String>,
    onLiveOutput: (String) -> Unit,
    onRunShellCommand: suspend (String) -> ShellExecutionResult,
  ): ShellExecutionResult {
    var fetchPath: String? = null
    val positional = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
      when {
        args[i] == "--fetch" -> {
          fetchPath = args.getOrNull(i + 1)?.takeIf { !it.startsWith("-") } ?: "/"
          if (args.getOrNull(i + 1)?.startsWith("-") == false) i++
        }
        args[i].startsWith("--fetch=") -> {
          fetchPath = args[i].substringAfter("=")
        }
        else -> positional.add(args[i])
      }
      i++
    }
    val targetArg = positional.firstOrNull()
    val targetFile =
      if (targetArg != null) resolveFile(currentDir, targetArg)
      else findDefaultEntryFile(currentDir, workspace)
    if (targetFile == null || !targetFile.exists() || !targetFile.isFile) {
      if (!stdin.isNullOrBlank()) {
        return runCodeString(
          context = context,
          source = stdin,
          fileLabel = "<stdin>.js",
          baseDir = currentDir,
          args = positional,
          env = env,
          isTypeScript = false,
          syntaxOnly = false,
          fetchPath = fetchPath,
          onLiveOutput = onLiveOutput,
        )
      }
      val missingLabel = targetArg ?: "entry file"
      return ShellExecutionResult("run: $missingLabel not found", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
    }
    return runTargetFile(
      context = context,
      file = targetFile,
      args = positional.drop(if (targetArg != null) 1 else 0),
      currentDir = currentDir,
      env = env,
      fetchPath = fetchPath,
      onLiveOutput = onLiveOutput,
      onRunShellCommand = onRunShellCommand,
    )
  }

  private suspend fun runTargetFile(
    context: Context,
    file: File,
    args: List<String>,
    currentDir: File,
    env: Map<String, String>,
    fetchPath: String? = null,
    onLiveOutput: (String) -> Unit,
    onRunShellCommand: suspend (String) -> ShellExecutionResult,
  ): ShellExecutionResult {
    val ext = file.extension.lowercase()
    return when (ext) {
      "sh", "bash" -> runShellFileLines(file, args, onRunShellCommand)
      "json", "jsonc" -> {
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrElse {
          return ShellExecutionResult("run: unable to read ${file.name}", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        runCatching {
          val trimmed = stripJsonComments(raw).trim()
          val pretty =
            if (trimmed.startsWith("[")) org.json.JSONArray(trimmed).toString(2)
            else JSONObject(trimmed).toString(2)
          ShellExecutionResult(output = pretty)
        }.getOrElse { err ->
          ShellExecutionResult("${file.name}: Invalid JSON (${err.localizedMessage})", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
      }
      "js", "mjs", "cjs", "jsx", "ts", "tsx" -> {
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrElse {
          return ShellExecutionResult("run: unable to read ${file.name}", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        runCodeString(
          context = context,
          source = raw,
          fileLabel = file.name,
          baseDir = file.parentFile ?: currentDir,
          args = args,
          env = env,
          isTypeScript = ext == "ts" || ext == "tsx",
          syntaxOnly = false,
          fetchPath = fetchPath,
          onLiveOutput = onLiveOutput,
        )
      }
      else -> {
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrElse { "" }
        if (raw.startsWith("#!")) {
          if (raw.lineSequence().first().contains("sh")) {
            return runShellFileLines(file, args, onRunShellCommand)
          }
          if (raw.lineSequence().first().contains("node") || raw.lineSequence().first().contains("ts")) {
            return runCodeString(
              context = context,
              source = raw.substringAfter('\n'),
              fileLabel = file.name,
              baseDir = file.parentFile ?: currentDir,
              args = args,
              env = env,
              isTypeScript = false,
              syntaxOnly = false,
              fetchPath = fetchPath,
              onLiveOutput = onLiveOutput,
            )
          }
        }
        ShellExecutionResult(output = raw)
      }
    }
  }

  private suspend fun executeJsOrTs(
    context: Context,
    args: List<String>,
    stdin: String?,
    currentDir: File,
    workspace: WorkerWorkspace?,
    env: Map<String, String>,
    forceTypeScript: Boolean,
    onLiveOutput: (String) -> Unit,
  ): ShellExecutionResult {
    var evalCode: String? = null
    var printExpr = false
    var syntaxOnly = false
    var fetchPath: String? = null
    val positional = mutableListOf<String>()

    var i = 0
    while (i < args.size) {
      when (val a = args[i]) {
        "-v", "--version" -> {
          return ShellExecutionResult(
            output = if (forceTypeScript) "ts-node v10.9.2 (EdgeCode)" else "v20.18.0 (EdgeCode)",
          )
        }
        "-e", "--eval" -> {
          evalCode = args.getOrNull(i + 1) ?: ""
          i++
        }
        "-p", "--print" -> {
          evalCode = args.getOrNull(i + 1) ?: ""
          printExpr = true
          i++
        }
        "-c", "--check" -> syntaxOnly = true
        "--fetch" -> {
          fetchPath = args.getOrNull(i + 1)?.takeIf { !it.startsWith("-") } ?: "/"
          if (args.getOrNull(i + 1)?.startsWith("-") == false) i++
        }
        else -> {
          if (a.startsWith("--fetch=")) {
            fetchPath = a.substringAfter("=")
          } else {
            positional.add(a)
          }
        }
      }
      i++
    }

    if (evalCode != null) {
      val code = if (printExpr) "console.log($evalCode);" else evalCode
      return runCodeString(
        context = context,
        source = code,
        fileLabel = if (forceTypeScript) "<eval>.ts" else "<eval>.js",
        baseDir = currentDir,
        args = positional,
        env = env,
        isTypeScript = forceTypeScript,
        syntaxOnly = syntaxOnly,
        fetchPath = fetchPath,
        onLiveOutput = onLiveOutput,
      )
    }

    val targetArg = positional.firstOrNull()
    val targetFile =
      if (targetArg != null) resolveFile(currentDir, targetArg)
      else if (stdin.isNullOrBlank()) findDefaultEntryFile(currentDir, workspace) else null

    if (targetArg != null && (targetFile == null || !targetFile.exists() || !targetFile.isFile)) {
      return ShellExecutionResult(
        output = "Cannot find module '$targetArg'",
        isError = true,
        exitCode = 1,
        kind = TerminalEntryKind.ERROR,
      )
    }

    if (targetFile != null && targetFile.exists() && targetFile.isFile) {
      val raw = runCatching { targetFile.readText(Charsets.UTF_8) }.getOrElse {
        return ShellExecutionResult("Error reading ${targetFile.name}", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
      val isTs = forceTypeScript || targetFile.extension.lowercase() in setOf("ts", "tsx")
      return runCodeString(
        context = context,
        source = raw,
        fileLabel = targetFile.name,
        baseDir = targetFile.parentFile ?: currentDir,
        args = positional.drop(1),
        env = env,
        isTypeScript = isTs,
        syntaxOnly = syntaxOnly,
        fetchPath = fetchPath,
        onLiveOutput = onLiveOutput,
      )
    }

    if (!stdin.isNullOrBlank()) {
      return runCodeString(
        context = context,
        source = stdin,
        fileLabel = if (forceTypeScript) "<stdin>.ts" else "<stdin>.js",
        baseDir = currentDir,
        args = positional,
        env = env,
        isTypeScript = forceTypeScript,
        syntaxOnly = syntaxOnly,
        fetchPath = fetchPath,
        onLiveOutput = onLiveOutput,
      )
    }

    return ShellExecutionResult(
      output = "Usage: ${if (forceTypeScript) "ts" else "node"} [-e <code> | -p <expr> | <file>]",
      isError = true,
      exitCode = 1,
      kind = TerminalEntryKind.ERROR,
    )
  }

  internal suspend fun runCodeString(
    context: Context,
    source: String,
    fileLabel: String,
    baseDir: File,
    args: List<String>,
    env: Map<String, String>,
    isTypeScript: Boolean,
    syntaxOnly: Boolean,
    fetchPath: String?,
    onLiveOutput: (String) -> Unit,
  ): ShellExecutionResult {
    val bundled = bundleLocalModules(source, baseDir, isTypeScript)
    val stripped = if (isTypeScript) stripTypeScriptSyntax(bundled) else bundled

    val balanceError = checkBalancedDelimiters(stripped)
    if (balanceError != null) {
      return ShellExecutionResult(
        output = "$fileLabel:$balanceError",
        isError = true,
        exitCode = 1,
        kind = TerminalEntryKind.ERROR,
      )
    }
    if (syntaxOnly) {
      return ShellExecutionResult(output = "$fileLabel: Syntax OK")
    }

    if (canUseAndroidWebView()) {
      val webResult =
        executeInWebView(
          context = context,
          jsCode = stripped,
          fileLabel = fileLabel,
          args = args,
          env = env,
          cwdPath = baseDir.absolutePath,
          fetchPath = fetchPath,
          onLiveOutput = onLiveOutput,
        )
      if (webResult != null) return webResult
    }

    return executeWithKotlinFallback(
      jsCode = stripped,
      fileLabel = fileLabel,
      args = args,
      env = env,
      fetchPath = fetchPath,
    )
  }

  private fun canUseAndroidWebView(): Boolean {
    if (Looper.getMainLooper() == null) return false
    val fp = Build.FINGERPRINT.orEmpty().lowercase()
    val prod = Build.PRODUCT.orEmpty().lowercase()
    if (fp.contains("robolectric") || prod.contains("robolectric")) return false
    return true
  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun executeInWebView(
    context: Context,
    jsCode: String,
    fileLabel: String,
    args: List<String>,
    env: Map<String, String>,
    cwdPath: String,
    fetchPath: String?,
    onLiveOutput: (String) -> Unit,
  ): ShellExecutionResult? {
    val prepared = transformModuleForWebView(jsCode, fetchPath)
    val deferred = CompletableDeferred<Pair<Int, String>>()
    val logs = mutableListOf<String>()
    var hasError = false
    var webView: WebView? = null

    val bridge =
      object {
        @JavascriptInterface
        fun log(level: String, message: String) {
          val formatted =
            when (level) {
              "error" -> {
                hasError = true
                "\u001B[31m$message\u001B[0m"
              }
              "warn" -> "\u001B[33m$message\u001B[0m"
              else -> message
            }
          synchronized(logs) {
            if (logs.size < 2000) logs.add(formatted)
          }
          onLiveOutput(synchronized(logs) { logs.joinToString("\n") })
        }

        @JavascriptInterface
        fun done(exitCode: Int, extra: String) {
          val text =
            synchronized(logs) {
              if (extra.isNotBlank()) (logs + extra).joinToString("\n")
              else logs.joinToString("\n")
            }
          deferred.complete(exitCode to text)
        }
      }

    return try {
      withContext(Dispatchers.Main) {
        val wv =
          WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(bridge, "__EdgeCodeBridge")
            webViewClient = WebViewClient()
          }
        webView = wv

        val argvJson = org.json.JSONArray(listOf("node", fileLabel) + args).toString()
        val envJson = JSONObject(env).toString()
        val cwdJson = JSONObject.quote(cwdPath)
        val fetchArgJson = JSONObject.quote(fetchPath ?: "")

        val runnerHtml =
          """
          <!doctype html><html><head><meta charset="utf-8"></head><body><script>
          (function(){
            const __fmt = (v) => {
              if (v === undefined) return "undefined";
              if (v === null) return "null";
              if (typeof v === "string") return v;
              if (v instanceof Error) return v.stack || (v.name + ": " + v.message);
              try { return JSON.stringify(v, null, 2); } catch (_) { return String(v); }
            };
            const __emit = (lvl, args) => {
              __EdgeCodeBridge.log(lvl, Array.from(args).map(__fmt).join(" "));
            };
            console.log = (...a) => __emit("log", a);
            console.info = (...a) => __emit("info", a);
            console.warn = (...a) => __emit("warn", a);
            console.error = (...a) => __emit("error", a);
            console.debug = (...a) => __emit("log", a);
            console.dir = (v) => __emit("log", [v]);
            console.assert = (cond, ...a) => { if (!cond) __emit("error", ["Assertion failed:", ...a]); };
            const __timers = {};
            console.time = (k = "default") => { __timers[k] = performance.now(); };
            console.timeEnd = (k = "default") => {
              const t = __timers[k];
              if (t !== undefined) {
                __emit("log", [k + ": " + (performance.now() - t).toFixed(2) + "ms"]);
                delete __timers[k];
              }
            };
            let __finished = false;
            const __finish = (code, msg) => {
              if (__finished) return;
              __finished = true;
              __EdgeCodeBridge.done(code, msg || "");
            };
            window.process = {
              argv: $argvJson,
              env: $envJson,
              cwd: () => $cwdJson,
              version: "v20.18.0",
              platform: "android",
              exit: (c = 0) => __finish(c, "")
            };
            window.__edgeDefault = undefined;
            window.exports = {};
            window.module = { exports: window.exports };
            const __fetchTarget = $fetchArgJson;
            (async () => {
              try {
                $prepared
                if (__fetchTarget && window.__edgeDefault && typeof window.__edgeDefault.fetch === "function") {
                  const reqUrl = __fetchTarget.startsWith("http") ? __fetchTarget : ("https://worker.local" + (__fetchTarget.startsWith("/") ? "" : "/") + __fetchTarget);
                  const resp = await window.__edgeDefault.fetch(new Request(reqUrl), window.process.env, { waitUntil: () => {}, passThroughOnException: () => {} });
                  if (resp && typeof resp.text === "function") {
                    const body = await resp.text();
                    console.log("HTTP " + resp.status + " " + (resp.statusText || ""));
                    if (body) console.log(body);
                  }
                }
                __finish(0, "");
              } catch (err) {
                console.error(err);
                __finish(1, "");
              }
            })();
          })();
          </script></body></html>
          """.trimIndent()

        wv.loadDataWithBaseURL("https://edgecode.local/", runnerHtml, "text/html", "utf-8", null)
      }

      val completed = withTimeoutOrNull(25_000L) { deferred.await() }
      if (completed == null) {
        ShellExecutionResult(
          output = (synchronized(logs) { logs.joinToString("\n") } + "\nTimed out after 25s").trim(),
          isError = true,
          exitCode = 124,
          kind = TerminalEntryKind.ERROR,
        )
      } else {
        val (code, text) = completed
        val failed = code != 0 || hasError
        ShellExecutionResult(
          output = text,
          isError = failed,
          exitCode = if (code != 0) code else if (hasError) 1 else 0,
          kind = if (failed) TerminalEntryKind.ERROR else TerminalEntryKind.SHELL,
        )
      }
    } catch (_: Throwable) {
      null
    } finally {
      withContext(Dispatchers.Main) {
        runCatching {
          webView?.stopLoading()
          webView?.destroy()
        }
      }
    }
  }

  private fun transformModuleForWebView(raw: String, fetchPath: String?): String {
    var out = raw.replace(Regex("^#!.*\\n"), "")
    out = out.replace(Regex("""^\s*import\s+[^;]*;?\s*$""", RegexOption.MULTILINE), "")
    out = out.replace(Regex("""^\s*export\s+default\s+""", RegexOption.MULTILINE), "window.__edgeDefault = ")
    out = out.replace(Regex("""^\s*export\s+(?=(async\s+)?function|const|let|var|class)\s*""", RegexOption.MULTILINE), "")
    if (!fetchPath.isNullOrBlank() && !out.contains("__edgeDefault")) {
      out += "\nif (typeof module !== 'undefined' && module.exports) window.__edgeDefault = module.exports;"
    }
    return out
  }

  internal fun bundleLocalModules(
    entrySource: String,
    baseDir: File,
    isTypeScript: Boolean,
    visited: MutableSet<String> = mutableSetOf(),
  ): String {
    val importRegex = Regex("""^\s*import\s+(?:[\s\S]*?\s+from\s+)?['"](\.[^'"]+)['"]\s*;?\s*$""", RegexOption.MULTILINE)
    val requireRegex = Regex("""require\(\s*['"](\.[^'"]+)['"]\s*\)""")
    val importedChunks = mutableListOf<String>()

    fun resolveModuleFile(relPath: String): File? {
      val candidates =
        listOf(
          relPath,
          "$relPath.ts",
          "$relPath.tsx",
          "$relPath.js",
          "$relPath.mjs",
          "$relPath/index.ts",
          "$relPath/index.js",
        )
      return candidates.map { File(baseDir, it) }.firstOrNull { it.exists() && it.isFile }
    }

    importRegex.findAll(entrySource).forEach { match ->
      val rel = match.groupValues[1]
      val target = resolveModuleFile(rel) ?: return@forEach
      val key = runCatching { target.canonicalPath }.getOrDefault(target.absolutePath)
      if (visited.add(key)) {
        val modRaw = runCatching { target.readText(Charsets.UTF_8) }.getOrDefault("")
        val subBundled = bundleLocalModules(modRaw, target.parentFile ?: baseDir, isTypeScript || target.extension.lowercase() in setOf("ts", "tsx"), visited)
        val modCleaned =
          subBundled
            .replace(Regex("""^\s*export\s+default\s+[\s\S]*$""", RegexOption.MULTILINE), "")
            .replace(Regex("""^\s*export\s+""", RegexOption.MULTILINE), "")
        importedChunks.add(modCleaned)
      }
    }

    var transformed = importRegex.replace(entrySource, "")
    transformed =
      requireRegex.replace(transformed) { m ->
        val rel = m.groupValues[1]
        val target = resolveModuleFile(rel)
        if (target == null) {
          m.value
        } else {
          val modRaw = runCatching { target.readText(Charsets.UTF_8) }.getOrDefault("")
          val cleaned = if (target.extension.lowercase() in setOf("ts", "tsx")) stripTypeScriptSyntax(modRaw) else modRaw
          "(function(){ const module = { exports: {} }; const exports = module.exports;\n$cleaned\nreturn module.exports; })()"
        }
      }

    return if (importedChunks.isEmpty()) transformed else (importedChunks.joinToString("\n") + "\n" + transformed)
  }

  internal fun stripTypeScriptSyntax(source: String): String {
    var s = source
    s = s.replace(Regex("""^\s*import\s+type\s+[^;]*;?\s*$""", RegexOption.MULTILINE), "")
    s = s.replace(Regex("""^\s*(export\s+)?interface\s+\w+(?:<[^>]*>)?(?:\s+extends\s+[^{]+)?\s*\{[^}]*\}\s*$""", RegexOption.MULTILINE), "")
    s = s.replace(Regex("""^\s*(export\s+)?type\s+\w+(?:<[^>]*>)?\s*=\s*[^;]+;\s*$""", RegexOption.MULTILINE), "")
    s = s.replace(Regex("""(\b(?:const|let|var)\s+[A-Za-z_$][A-Za-z0-9_$]*)\s*:\s*[A-Za-z_$][A-Za-z0-9_$<>\[\]|&.,\s]*?(?=\s*=)"""), "$1")
    s = s.replace(Regex("""(\([A-Za-z0-9_$,\s]*?)\s*:\s*[A-Za-z_$][A-Za-z0-9_$<>\[\]|&\s]*(?=[,)])"""), "$1")
    s = s.replace(Regex("""\)\s*:\s*[A-Za-z_$][A-Za-z0-9_$<>\[\]|&.\s]*(?=\s*(=>|\{))"""), ")")
    s = s.replace(Regex("""\s+as\s+[A-Za-z_$][A-Za-z0-9_$<>\[\]|&]*"""), "")
    return s
  }

  internal fun checkBalancedDelimiters(code: String): String? {
    val stack = ArrayDeque<Triple<Char, Int, Int>>()
    var line = 1
    var col = 0
    var inSingle = false
    var inDouble = false
    var inTemplate = false
    var inLineComment = false
    var inBlockComment = false
    var escaped = false

    var i = 0
    while (i < code.length) {
      val ch = code[i]
      if (ch == '\n') {
        line++
        col = 0
        inLineComment = false
        escaped = false
        i++
        continue
      }
      col++
      if (inLineComment) {
        i++
        continue
      }
      if (inBlockComment) {
        if (ch == '*' && code.getOrNull(i + 1) == '/') {
          inBlockComment = false
          i += 2
          col++
          continue
        }
        i++
        continue
      }
      if (escaped) {
        escaped = false
        i++
        continue
      }
      if (inSingle || inDouble || inTemplate) {
        if (ch == '\\') {
          escaped = true
        } else if (inSingle && ch == '\'') {
          inSingle = false
        } else if (inDouble && ch == '"') {
          inDouble = false
        } else if (inTemplate && ch == '`') {
          inTemplate = false
        }
        i++
        continue
      }
      if (ch == '/' && code.getOrNull(i + 1) == '/') {
        inLineComment = true
        i += 2
        col++
        continue
      }
      if (ch == '/' && code.getOrNull(i + 1) == '*') {
        inBlockComment = true
        i += 2
        col++
        continue
      }
      when (ch) {
        '\'' -> inSingle = true
        '"' -> inDouble = true
        '`' -> inTemplate = true
        '(', '{', '[' -> stack.addLast(Triple(ch, line, col))
        ')', '}', ']' -> {
          if (stack.isEmpty()) {
            return "$line:$col: SyntaxError: Unexpected token '$ch'"
          }
          val (open, _, _) = stack.removeLast()
          val expected =
            when (open) {
              '(' -> ')'
              '{' -> '}'
              else -> ']'
            }
          if (ch != expected) {
            return "$line:$col: SyntaxError: Expected '$expected' before '$ch'"
          }
        }
      }
      i++
    }
    if (inSingle || inDouble || inTemplate) {
      return "$line:$col: SyntaxError: Unterminated string literal"
    }
    if (stack.isNotEmpty()) {
      val (open, l, c) = stack.last()
      return "$l:$c: SyntaxError: Unclosed '$open'"
    }
    return null
  }

  internal fun executeWithKotlinFallback(
    jsCode: String,
    fileLabel: String,
    args: List<String>,
    env: Map<String, String>,
    fetchPath: String?,
  ): ShellExecutionResult {
    val vars = linkedMapOf<String, String>()
    env.forEach { (k, v) -> vars["process.env.$k"] = v }
    vars["process.argv"] = args.joinToString(" ")
    val logs = mutableListOf<String>()
    var hasError = false

    val statements = splitTopLevelStatements(jsCode)
    for (stmt in statements) {
      val trimmed = stmt.trim()
      if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("import ")) continue
      val declMatch = Regex("""^(?:const|let|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*([\s\S]+)$""").find(trimmed)
      if (declMatch != null) {
        val name = declMatch.groupValues[1]
        val expr = declMatch.groupValues[2].removeSuffix(";").trim()
        vars[name] = evaluateSimpleExpr(expr, vars)
        continue
      }
      val logMatch = Regex("""^console\.(log|info|warn|error|debug)\(([\s\S]*)\);?$""").find(trimmed)
      if (logMatch != null) {
        val level = logMatch.groupValues[1]
        val rawArgs = logMatch.groupValues[2]
        val argList = splitCallArgs(rawArgs).map { evaluateSimpleExpr(it, vars) }
        val line = argList.joinToString(" ")
        if (level == "error") {
          hasError = true
          logs.add("\u001B[31m$line\u001B[0m")
        } else if (level == "warn") {
          logs.add("\u001B[33m$line\u001B[0m")
        } else {
          logs.add(line)
        }
        continue
      }
    }

    if (!fetchPath.isNullOrBlank() && jsCode.contains("fetch")) {
      val respMatch = Regex("""new\s+Response\(\s*(['"`])([\s\S]*?)\1""").find(jsCode)
      val body = respMatch?.groupValues?.get(2)?.let { unescapeString(it) } ?: "Worker handler invoked ($fetchPath)"
      logs.add("HTTP 200 OK")
      logs.add(body)
    }

    return ShellExecutionResult(
      output = logs.joinToString("\n"),
      isError = hasError,
      exitCode = if (hasError) 1 else 0,
      kind = if (hasError) TerminalEntryKind.ERROR else TerminalEntryKind.SHELL,
    )
  }

  private fun splitTopLevelStatements(code: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var paren = 0
    var brace = 0
    var bracket = 0
    var inSingle = false
    var inDouble = false
    var inTemplate = false
    var escaped = false

    for (ch in code) {
      if (escaped) {
        current.append(ch)
        escaped = false
        continue
      }
      if ((inSingle || inDouble || inTemplate) && ch == '\\') {
        current.append(ch)
        escaped = true
        continue
      }
      if (!inDouble && !inTemplate && ch == '\'') inSingle = !inSingle
      else if (!inSingle && !inTemplate && ch == '"') inDouble = !inDouble
      else if (!inSingle && !inDouble && ch == '`') inTemplate = !inTemplate
      else if (!inSingle && !inDouble && !inTemplate) {
        when (ch) {
          '(' -> paren++
          ')' -> if (paren > 0) paren--
          '{' -> brace++
          '}' -> if (brace > 0) brace--
          '[' -> bracket++
          ']' -> if (bracket > 0) bracket--
        }
      }
      if ((ch == ';' || ch == '\n') && paren == 0 && brace == 0 && bracket == 0 && !inSingle && !inDouble && !inTemplate) {
        val s = current.toString().trim()
        if (s.isNotEmpty()) result.add(s)
        current.setLength(0)
      } else {
        current.append(ch)
      }
    }
    val tail = current.toString().trim()
    if (tail.isNotEmpty()) result.add(tail)
    return result
  }

  private fun splitCallArgs(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val args = mutableListOf<String>()
    val cur = StringBuilder()
    var depth = 0
    var inSingle = false
    var inDouble = false
    var inTemplate = false
    var escaped = false
    for (ch in raw) {
      if (escaped) {
        cur.append(ch)
        escaped = false
        continue
      }
      if ((inSingle || inDouble || inTemplate) && ch == '\\') {
        cur.append(ch)
        escaped = true
        continue
      }
      if (!inDouble && !inTemplate && ch == '\'') inSingle = !inSingle
      else if (!inSingle && !inTemplate && ch == '"') inDouble = !inDouble
      else if (!inSingle && !inDouble && ch == '`') inTemplate = !inTemplate
      else if (!inSingle && !inDouble && !inTemplate) {
        when (ch) {
          '(', '{', '[' -> depth++
          ')', '}', ']' -> if (depth > 0) depth--
        }
      }
      if (ch == ',' && depth == 0 && !inSingle && !inDouble && !inTemplate) {
        args.add(cur.toString().trim())
        cur.setLength(0)
      } else {
        cur.append(ch)
      }
    }
    val tail = cur.toString().trim()
    if (tail.isNotEmpty()) args.add(tail)
    return args
  }

  private fun evaluateSimpleExpr(raw: String, vars: Map<String, String>): String {
    val expr = raw.trim()
    if (expr.isEmpty()) return ""
    if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
      return unescapeString(expr.substring(1, expr.length - 1))
    }
    if (expr.startsWith("`") && expr.endsWith("`")) {
      val inner = expr.substring(1, expr.length - 1)
      return Regex("""\$\{([^}]+)\}""").replace(inner) { m ->
        evaluateSimpleExpr(m.groupValues[1], vars)
      }
    }
    if (expr.startsWith("JSON.stringify(") && expr.endsWith(")")) {
      val inner = expr.removePrefix("JSON.stringify(").dropLast(1).trim()
      return evaluateSimpleExpr(inner, vars)
    }
    vars[expr]?.let { return it }
    if (expr.contains('+') && !expr.startsWith("\"") && !expr.startsWith("'")) {
      val parts = splitPlusParts(expr)
      if (parts.size > 1) {
        val evalParts = parts.map { evaluateSimpleExpr(it, vars) }
        val nums = evalParts.map { it.toDoubleOrNull() }
        return if (nums.all { it != null }) {
          val sum = nums.filterNotNull().sum()
          if (sum % 1.0 == 0.0) sum.toLong().toString() else sum.toString()
        } else {
          evalParts.joinToString("")
        }
      }
    }
    val arithmetic = expr.toDoubleOrNull()
    if (arithmetic != null) {
      return if (arithmetic % 1.0 == 0.0) arithmetic.toLong().toString() else arithmetic.toString()
    }
    return evalBasicMath(expr) ?: expr
  }

  private fun splitPlusParts(expr: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var inSingle = false
    var inDouble = false
    var inTemplate = false
    var depth = 0
    for (ch in expr) {
      if (!inDouble && !inTemplate && ch == '\'') inSingle = !inSingle
      else if (!inSingle && !inTemplate && ch == '"') inDouble = !inDouble
      else if (!inSingle && !inDouble && ch == '`') inTemplate = !inTemplate
      else if (!inSingle && !inDouble && !inTemplate) {
        when (ch) {
          '(', '[', '{' -> depth++
          ')', ']', '}' -> if (depth > 0) depth--
        }
      }
      if (ch == '+' && depth == 0 && !inSingle && !inDouble && !inTemplate) {
        out.add(cur.toString().trim())
        cur.setLength(0)
      } else {
        cur.append(ch)
      }
    }
    if (cur.isNotEmpty()) out.add(cur.toString().trim())
    return out
  }

  private fun evalBasicMath(expr: String): String? {
    val m = Regex("""^(-?\d+(?:\.\d+)?)\s*([+\-*/%])\s*(-?\d+(?:\.\d+)?)$""").find(expr.trim()) ?: return null
    val a = m.groupValues[1].toDoubleOrNull() ?: return null
    val op = m.groupValues[2]
    val b = m.groupValues[3].toDoubleOrNull() ?: return null
    val res =
      when (op) {
        "+" -> a + b
        "-" -> a - b
        "*" -> a * b
        "/" -> if (b == 0.0) return "Infinity" else a / b
        "%" -> if (b == 0.0) return "NaN" else a % b
        else -> return null
      }
    return if (res % 1.0 == 0.0) res.toLong().toString() else res.toString()
  }

  private fun unescapeString(s: String): String =
    s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\")

  private fun stripJsonComments(raw: String): String =
    raw.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""^\s*//.*$""", RegexOption.MULTILINE), "")

  private fun resolveFile(currentDir: File, path: String): File {
    val raw = if (path.startsWith("/")) File(path) else File(currentDir, path)
    return runCatching { raw.canonicalFile }.getOrElse { raw.absoluteFile }
  }

  private fun findPackageJson(currentDir: File): File? {
    var dir: File? = currentDir
    repeat(5) {
      val f = dir?.let { File(it, "package.json") }
      if (f != null && f.exists() && f.isFile) return f
      dir = dir?.parentFile
    }
    return null
  }

  private fun findDefaultEntryFile(currentDir: File, workspace: WorkerWorkspace?): File? {
    workspace?.entryModulePath?.takeIf { it.isNotBlank() }?.let { rel ->
      val f = File(currentDir, rel)
      if (f.exists() && f.isFile) return f
    }
    findPackageJson(currentDir)?.let { pkg ->
      val main = runCatching { JSONObject(pkg.readText(Charsets.UTF_8)).optString("main") }.getOrNull()
      if (!main.isNullOrBlank()) {
        val f = File(pkg.parentFile, main)
        if (f.exists() && f.isFile) return f
      }
    }
    val candidates =
      listOf("index.js", "index.ts", "src/index.js", "src/index.ts", "main.js", "main.ts", "app.js", "app.ts", "worker.js", "run.sh")
    return candidates.map { File(currentDir, it) }.firstOrNull { it.exists() && it.isFile }
  }
}
