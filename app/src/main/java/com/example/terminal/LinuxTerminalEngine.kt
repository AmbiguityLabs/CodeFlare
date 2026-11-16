package com.example.terminal

import android.content.Context
import com.example.data.remote.CfAccount
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import com.example.data.repository.CloudflareRepository
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ShellExecutionResult(
  val output: String,
  val isError: Boolean = false,
  val exitCode: Int = 0,
  val kind: TerminalEntryKind = TerminalEntryKind.SHELL,
  val httpMeta: HttpTraceMeta? = null,
  val clearedScreen: Boolean = false,
  val updatedWorkspaceFiles: List<WorkerFile>? = null,
  val openTabRequest: String? = null,
)

class LinuxTerminalEngine(
  private val context: Context,
  private val repository: CloudflareRepository,
) {
  private val rootWorkspacesDir = File(context.filesDir, "edgecode_workspaces").apply { mkdirs() }
  private var currentDir: File = rootWorkspacesDir
  private var lastSyncedScript: String? = null

  val sessionEnv =
    mutableMapOf(
      "USER" to "dev",
      "TERM" to "xterm-256color",
      "SHELL" to "/system/bin/sh",
      "HOME" to rootWorkspacesDir.absolutePath,
    )

  fun currentPromptPath(workspace: WorkerWorkspace?): String {
    val wsName = workspace?.scriptName ?: "scratch"
    val wsDir = File(rootWorkspacesDir, wsName)
    return if (currentDir.absolutePath == wsDir.absolutePath) {
      "~/$wsName"
    } else if (currentDir.absolutePath.startsWith(wsDir.absolutePath + "/")) {
      "~/$wsName/${currentDir.relativeTo(wsDir).path}"
    } else {
      currentDir.name.ifBlank { "/" }
    }
  }

  fun syncWorkspaceToDisk(workspace: WorkerWorkspace?) {
    val ws = workspace ?: return
    val wsDir = File(rootWorkspacesDir, ws.scriptName).apply { mkdirs() }
    if (lastSyncedScript != ws.scriptName) {
      currentDir = wsDir
      lastSyncedScript = ws.scriptName
    }
    sessionEnv["WORKER"] = ws.scriptName
    sessionEnv["COMPAT_DATE"] = ws.compatibilityDate
    ws.workersDevUrl?.let { sessionEnv["BASE_URL"] = it } ?: sessionEnv.remove("BASE_URL")

    val activePaths = ws.files.filterNot { it.isVirtualReadOnly }.map { it.path }.toSet()
    // Remove stale non-virtual files removed via GUI (preserve wrangler.jsonc & .git/)
    wsDir.walkTopDown().filter { it.isFile }.forEach { f ->
      val rel = f.relativeTo(wsDir).path.replace('\\', '/')
      if (rel != "wrangler.jsonc" && !rel.startsWith(".git/") && rel !in activePaths) {
        f.delete()
      }
    }
    // Write current editor files cleanly
    for (wf in ws.files) {
      val dest = File(wsDir, wf.path)
      dest.parentFile?.mkdirs()
      if (!dest.exists() || dest.readText(Charsets.UTF_8) != wf.content) {
        dest.writeText(wf.content, Charsets.UTF_8)
      }
    }
  }

  private fun readUpdatedFilesFromDisk(workspace: WorkerWorkspace?): List<WorkerFile>? {
    val ws = workspace ?: return null
    val wsDir = File(rootWorkspacesDir, ws.scriptName)
    if (!wsDir.exists()) return null

    val existingByPath = ws.files.associateBy { it.path }
    val virtualFiles = ws.files.filter { it.isVirtualReadOnly }
    val diskEditable =
      wsDir
        .walkTopDown()
        .filter { it.isFile }
        .mapNotNull { file ->
          val rel = file.relativeTo(wsDir).path.replace('\\', '/')
          if (rel == "wrangler.jsonc" || rel.startsWith(".git/")) return@mapNotNull null
          val content = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@mapNotNull null
          val prev = existingByPath[rel]
          prev?.copy(content = content)
            ?: WorkerFile(
              path = rel,
              content = content,
              originalContent = "",
              contentType = MultipartWorkerParser.inferModuleMimeType(rel),
              isEntryPoint = false,
            )
        }
        .toList()

    if (diskEditable.isEmpty()) return null
    val currentEditable = ws.files.filterNot { it.isVirtualReadOnly }
    return if (diskEditable != currentEditable) diskEditable + virtualFiles else null
  }

  suspend fun execute(
    rawInput: String,
    workspace: WorkerWorkspace?,
    account: CfAccount?,
    history: List<String>,
    onWranglerDeploy: suspend () -> String,
    onWranglerTailStart: suspend () -> String,
    onWranglerTailStop: suspend () -> String,
    onWranglerRollback: suspend (String) -> String,
    onGitCommand: (suspend (List<String>) -> ShellExecutionResult)? = null,
  ): ShellExecutionResult {
    syncWorkspaceToDisk(workspace)
    val normalized = ShellTokenizer.normalizeMultilineCommand(rawInput)
    if (normalized.isBlank()) {
      return ShellExecutionResult(output = "")
    }

    val expanded = ShellTokenizer.expandEnvVars(normalized, sessionEnv)
    val pipeStages = ShellTokenizer.splitUnquotedPipe(expanded)
    if (pipeStages.isEmpty()) {
      return ShellExecutionResult(output = "")
    }

    var pipedInput: String? = null
    var lastResult = ShellExecutionResult(output = "")

    for ((stageIndex, stageCmd) in pipeStages.withIndex()) {
      val res =
        executeSingleStage(
          stageCmd = stageCmd,
          stdin = pipedInput,
          workspace = workspace,
          account = account,
          history = history,
          onWranglerDeploy = onWranglerDeploy,
          onWranglerTailStart = onWranglerTailStart,
          onWranglerTailStop = onWranglerTailStop,
          onWranglerRollback = onWranglerRollback,
          onGitCommand = onGitCommand,
        )
      if (res.clearedScreen) return res
      lastResult = res
      if (res.isError && stageIndex < pipeStages.lastIndex) {
        return res
      }
      // For pipelines after curl, pipe raw HTTP body into jq/grep/wc if body exists
      pipedInput =
        if (stageIndex < pipeStages.lastIndex && res.httpMeta != null) {
          res.httpMeta.bodyText
        } else {
          res.output
        }
    }

    val syncedBack = readUpdatedFilesFromDisk(workspace)
    return lastResult.copy(updatedWorkspaceFiles = syncedBack ?: lastResult.updatedWorkspaceFiles)
  }

  private suspend fun executeSingleStage(
    stageCmd: String,
    stdin: String?,
    workspace: WorkerWorkspace?,
    account: CfAccount?,
    history: List<String>,
    onWranglerDeploy: suspend () -> String,
    onWranglerTailStart: suspend () -> String,
    onWranglerTailStop: suspend () -> String,
    onWranglerRollback: suspend (String) -> String,
    onGitCommand: (suspend (List<String>) -> ShellExecutionResult)? = null,
  ): ShellExecutionResult {
    val tokens = ShellTokenizer.tokenize(stageCmd)
    if (tokens.isEmpty()) return ShellExecutionResult(output = "")
    val cmd = tokens[0]

    // Built-in env assignment syntax: FOO=bar
    if (tokens.size == 1 && cmd.contains('=') && !cmd.startsWith("=")) {
      val idx = cmd.indexOf('=')
      val key = cmd.substring(0, idx).trim()
      val value = cmd.substring(idx + 1)
      if (key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
        sessionEnv[key] = value
        return ShellExecutionResult(output = "$key=$value")
      }
    }

    if (cmd == "clear" || cmd == "cls") {
      return ShellExecutionResult(output = "", clearedScreen = true)
    }

    if (cmd == "help") {
      return ShellExecutionResult(
        output =
          """
          EdgeCode Linux & Cloudflare API Terminal
          ----------------------------------------
          API / HTTP Testing (Real OkHttp with TLS & Timing):
            curl -i /health                     Call relative path on active workers.dev
            curl -v -X POST -d '{"a":1}' /echo  Verbose headers + DNS/TLS/TTFB trace
            POST /api '{"ping":true}'           Fast HTTP verb shorthand
            curl https://example.com | jq .     Pipe JSON responses cleanly into jq
            Paste multi-line Chrome/Postman cURL snippets directly via Paste button

          Cloudflare Wrangler CLI:
            wrangler whoami | list | deploy | tail | tail stop
            wrangler deployments | rollback <version-id>
            wrangler secret list | put <NAME> <VAL> | delete <NAME>
            wrangler kv:namespace list | wrangler kv:key list/get/put/delete

          Git & GitHub CLI (Synced with Source Control):
            git whoami | status | diff | log
            git branch | git branch -b <name> | git checkout <branch>
            git add <.|file> | git reset <.|file> | git commit -m "<msg>"
            git push | git pull | git clone <url|owner/repo> [branch]

          Linux / Workspace Shell (/system/bin/sh):
            ls -la, cat src/index.js, grep -rn fetch ., touch file.js, rm, mv, find,
            uname -a, env, export TOKEN=abc, cd src, pwd, history, open src/index.js, clear
          """.trimIndent(),
        kind = TerminalEntryKind.SYSTEM,
      )
    }

    if (cmd == "export") {
      val assignment = tokens.getOrNull(1)
      if (assignment != null && assignment.contains('=')) {
        val idx = assignment.indexOf('=')
        val k = assignment.substring(0, idx).trim()
        val v = assignment.substring(idx + 1)
        sessionEnv[k] = v
        return ShellExecutionResult(output = "exported $k")
      }
      return ShellExecutionResult(output = sessionEnv.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }

    if (cmd == "unset") {
      tokens.drop(1).forEach { sessionEnv.remove(it) }
      return ShellExecutionResult(output = "unset ${tokens.drop(1).joinToString(" ")}")
    }

    if (cmd == "env" || cmd == "printenv") {
      val combined = System.getenv() + sessionEnv
      val out = combined.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }
      return ShellExecutionResult(output = out)
    }

    if (cmd == "pwd") {
      return ShellExecutionResult(output = currentDir.absolutePath)
    }

    if (cmd == "cd") {
      val targetArg = tokens.getOrNull(1)
      val wsDir = workspace?.let { File(rootWorkspacesDir, it.scriptName) } ?: rootWorkspacesDir
      val nextDir =
        when (targetArg) {
          null, "~" -> wsDir
          "-" -> wsDir
          else -> File(currentDir, targetArg).canonicalFile
        }
      return if (nextDir.exists() && nextDir.isDirectory) {
        currentDir = nextDir
        ShellExecutionResult(output = "")
      } else {
        ShellExecutionResult(
          output = "sh: cd: $targetArg: No such directory",
          isError = true,
          exitCode = 1,
          kind = TerminalEntryKind.ERROR,
        )
      }
    }

    if (cmd == "history") {
      val formatted = history.mapIndexed { idx, line -> "${idx + 1}  $line" }.joinToString("\n")
      return ShellExecutionResult(output = formatted.ifBlank { "(no history yet)" })
    }

    if (cmd == "open" || cmd == "code") {
      val target = tokens.getOrNull(1)
      return if (target.isNullOrBlank()) {
        ShellExecutionResult("Usage: $cmd <file-path>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      } else {
        ShellExecutionResult(output = "Opening $target in editor…", openTabRequest = target)
      }
    }

    if (cmd == "jq") {
      val expr = tokens.drop(1).firstOrNull { !it.startsWith("-") } ?: "."
      val fileArg = tokens.drop(1).filter { !it.startsWith("-") }.getOrNull(1)
      val inputSource =
        when {
          stdin != null -> stdin
          fileArg != null -> {
            val f = File(currentDir, fileArg)
            if (!f.exists()) {
              return ShellExecutionResult("jq: error: Could not open file $fileArg", isError = true, exitCode = 2, kind = TerminalEntryKind.ERROR)
            }
            f.readText(Charsets.UTF_8)
          }
          else -> sessionEnv["LAST_BODY"] ?: ""
        }
      return JqEvaluator.evaluate(inputSource, expr).fold(
        onSuccess = { ShellExecutionResult(output = it) },
        onFailure = {
          ShellExecutionResult(
            output = it.localizedMessage ?: "jq parse error",
            isError = true,
            exitCode = 4,
            kind = TerminalEntryKind.ERROR,
          )
        },
      )
    }

    // Real OkHttp cURL / HTTP execution
    if (CurlCommandParser.isHttpTestCommand(tokens)) {
      return executeHttpCurl(tokens, workspace)
    }

    // Cloudflare Wrangler CLI integration
    if (cmd == "wrangler" || cmd == "npx" && tokens.getOrNull(1) == "wrangler") {
      val args = if (cmd == "npx") tokens.drop(2) else tokens.drop(1)
      return executeWranglerCommand(
        args = args,
        workspace = workspace,
        account = account,
        onWranglerDeploy = onWranglerDeploy,
        onWranglerTailStart = onWranglerTailStart,
        onWranglerTailStop = onWranglerTailStop,
        onWranglerRollback = onWranglerRollback,
      )
    }

    // Real Git & GitHub CLI integration
    if (cmd == "git") {
      return onGitCommand?.invoke(tokens.drop(1))
        ?: ShellExecutionResult(
          output = "git: GitHub integration not initialized.",
          isError = true,
          exitCode = 1,
          kind = TerminalEntryKind.ERROR,
        )
    }

    // Real Android /system/bin/sh Process Execution
    return executeRealLinuxShell(stageCmd, stdin)
  }

  private suspend fun executeHttpCurl(
    tokens: List<String>,
    workspace: WorkerWorkspace?,
  ): ShellExecutionResult =
    withContext(Dispatchers.IO) {
      val parsed = CurlCommandParser.parse(tokens, workspace?.workersDevUrl)
      val spec =
        parsed.getOrElse { err ->
          return@withContext ShellExecutionResult(
            output = "curl: ${err.localizedMessage ?: "invalid arguments"}",
            isError = true,
            exitCode = 2,
            kind = TerminalEntryKind.ERROR,
          )
        }

      var dnsStart = 0L
      var dnsEnd = 0L
      var connStart = 0L
      var tlsStart = 0L
      var tlsEnd = 0L
      var reqEnd = 0L
      var respStart = 0L
      val callStart = System.nanoTime()

      val timingListener =
        object : EventListener() {
          override fun dnsStart(call: Call, domainName: String) {
            dnsStart = System.nanoTime()
          }

          override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
            dnsEnd = System.nanoTime()
          }

          override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            connStart = System.nanoTime()
          }

          override fun secureConnectStart(call: Call) {
            tlsStart = System.nanoTime()
          }

          override fun secureConnectEnd(call: Call, handshake: Handshake?) {
            tlsEnd = System.nanoTime()
          }

          override fun requestBodyEnd(call: Call, byteCount: Long) {
            reqEnd = System.nanoTime()
          }

          override fun requestHeadersEnd(call: Call, request: Request) {
            if (reqEnd == 0L) reqEnd = System.nanoTime()
          }

          override fun responseHeadersStart(call: Call) {
            respStart = System.nanoTime()
          }
        }

      val client =
        OkHttpClient.Builder()
          .followRedirects(spec.followRedirects)
          .followSslRedirects(spec.followRedirects)
          .connectTimeout(15, TimeUnit.SECONDS)
          .readTimeout(25, TimeUnit.SECONDS)
          .eventListener(timingListener)
          .build()

      val contentType = spec.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value ?: "application/json"
      val bodyMethods = setOf("POST", "PUT", "PATCH", "DELETE")
      val reqBody =
        when {
          spec.body != null -> spec.body.toRequestBody(contentType.toMediaTypeOrNull())
          spec.method in bodyMethods -> ByteArray(0).toRequestBody(null)
          else -> null
        }

      val reqBuilder =
        try {
          Request.Builder().url(spec.url).method(spec.method, reqBody)
        } catch (t: Throwable) {
          return@withContext ShellExecutionResult(
            output = "curl: invalid URL '${spec.url}' (${t.localizedMessage})",
            isError = true,
            exitCode = 3,
            kind = TerminalEntryKind.ERROR,
          )
        }

      spec.headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
      val request = reqBuilder.build()

      try {
        val response = client.newCall(request).execute()
        response.use { resp ->
          val totalMs = ((System.nanoTime() - callStart) / 1_000_000L).coerceAtLeast(1)
          val dnsMs = if (dnsEnd > dnsStart) (dnsEnd - dnsStart) / 1_000_000L else 0L
          val tlsMs = if (tlsEnd > tlsStart) (tlsEnd - tlsStart) / 1_000_000L else 0L
          val ttfbMs = if (respStart > callStart) (respStart - callStart) / 1_000_000L else totalMs
          val rawBody = if (spec.headOnly) "" else resp.body?.string().orEmpty()
          val formattedBody = JqEvaluator.prettyPrintIfJson(rawBody)
          val headersText =
            resp.headers.names().joinToString("\n") { name ->
              "$name: ${resp.headers.values(name).joinToString(", ")}"
            }

          sessionEnv["LAST_STATUS"] = resp.code.toString()
          sessionEnv["LAST_URL"] = resp.request.url.toString()
          sessionEnv["LAST_BODY"] = rawBody

          val protoLabel =
            when (resp.protocol) {
              Protocol.HTTP_2 -> "HTTP/2"
              Protocol.HTTP_1_1 -> "HTTP/1.1"
              Protocol.HTTP_1_0 -> "HTTP/1.0"
              else -> resp.protocol.toString()
            }

          val out = buildString {
            if (spec.verbose) {
              appendLine("> ${spec.method} ${resp.request.url.encodedPath} $protoLabel")
              appendLine("> Host: ${resp.request.url.host}")
              spec.headers.forEach { (k, v) -> appendLine("> $k: $v") }
              appendLine(">")
              appendLine("< $protoLabel ${resp.code} ${resp.message}")
              if (headersText.isNotBlank()) appendLine(headersText.lines().joinToString("\n") { "< $it" })
              appendLine("<")
            } else if (spec.includeHeaders || spec.headOnly) {
              appendLine("$protoLabel ${resp.code} ${resp.message}")
              if (headersText.isNotBlank()) appendLine(headersText)
              if (formattedBody.isNotBlank()) appendLine()
            }
            if (formattedBody.isNotBlank()) {
              append(formattedBody)
            } else if (spec.headOnly) {
              append("(HEAD response — headers only)")
            } else {
              append("(Empty body • HTTP ${resp.code} ${resp.message})")
            }
          }.trimEnd()

          val traceSummary = "DNS ${dnsMs}ms • TLS ${tlsMs}ms • TTFB ${ttfbMs}ms • Total ${totalMs}ms"

          ShellExecutionResult(
            output = out,
            isError = resp.code >= 500,
            exitCode = 0,
            kind = TerminalEntryKind.HTTP,
            httpMeta =
              HttpTraceMeta(
                method = spec.method,
                url = resp.request.url.toString(),
                protocol = protoLabel,
                statusCode = resp.code,
                statusText = resp.message.ifBlank { "OK" },
                durationMs = totalMs,
                timingSummary = traceSummary,
                headersText = headersText,
                bodyText = formattedBody,
                curlEquivalent = CurlCommandParser.toReproducibleCurl(spec),
              ),
          )
        }
      } catch (ioe: IOException) {
        ShellExecutionResult(
          output = "curl: (7) Failed to connect to ${spec.url}: ${ioe.localizedMessage ?: "network error"}",
          isError = true,
          exitCode = 7,
          kind = TerminalEntryKind.ERROR,
        )
      }
    }

  private suspend fun executeWranglerCommand(
    args: List<String>,
    workspace: WorkerWorkspace?,
    account: CfAccount?,
    onWranglerDeploy: suspend () -> String,
    onWranglerTailStart: suspend () -> String,
    onWranglerTailStop: suspend () -> String,
    onWranglerRollback: suspend (String) -> String,
  ): ShellExecutionResult {
    if (args.isEmpty()) {
      return ShellExecutionResult(
        output = "Usage: wrangler <whoami|list|deploy|tail|deployments|rollback|routes|secret|kv:namespace|kv:key>",
        kind = TerminalEntryKind.WRANGLER,
      )
    }
    val sub = args[0].lowercase()
    val accountId = account?.id

    return when (sub) {
      "whoami" -> {
        val info =
          if (account == null) "Not authenticated (Scratchpad mode). Connect Cloudflare to run cloud commands."
          else "Account: ${account.name}\nAccount ID: ${account.id}\nSubdomain: ${workspace?.accountSubdomain ?: "(unknown)"}"
        ShellExecutionResult(info, kind = TerminalEntryKind.WRANGLER)
      }
      "list", "workers" -> {
        if (accountId == null) {
          return ShellExecutionResult("Error: Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        repository.loadWorkers(accountId).fold(
          onSuccess = { scripts ->
            val lines =
              if (scripts.isEmpty()) "(no Workers found)"
              else scripts.joinToString("\n") { "${it.id}  (compat: ${it.compatibilityDate ?: "default"})" }
            ShellExecutionResult(lines, kind = TerminalEntryKind.WRANGLER)
          },
          onFailure = {
            ShellExecutionResult(it.localizedMessage ?: "wrangler list failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
          },
        )
      }
      "deploy", "publish" -> {
        val msg = onWranglerDeploy()
        ShellExecutionResult(msg, kind = TerminalEntryKind.WRANGLER)
      }
      "tail" -> {
        val msg =
          if (args.getOrNull(1)?.equals("stop", true) == true) onWranglerTailStop()
          else onWranglerTailStart()
        ShellExecutionResult(msg, kind = TerminalEntryKind.WRANGLER)
      }
      "deployments" -> {
        val ws = workspace ?: return ShellExecutionResult("No Worker open.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        if (accountId == null) return ShellExecutionResult("Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        repository.listDeployments(accountId, ws.scriptName).fold(
          onSuccess = { list ->
            val lines =
              if (list.isEmpty()) "(no deployment history)"
              else list.mapIndexed { idx, d ->
                val vid = d.versions.firstOrNull()?.versionId ?: d.id
                val tag = if (idx == 0) "[LIVE]" else "      "
                "$tag ${vid.take(12)}  ${d.createdOn.orEmpty()}  ${d.source.orEmpty()}"
              }.joinToString("\n")
            ShellExecutionResult(lines, kind = TerminalEntryKind.WRANGLER)
          },
          onFailure = {
            ShellExecutionResult(it.localizedMessage ?: "Failed to list deployments", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
          },
        )
      }
      "rollback" -> {
        val ver = args.getOrNull(1)
        if (ver.isNullOrBlank()) {
          ShellExecutionResult("Usage: wrangler rollback <version-id>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        } else {
          ShellExecutionResult(onWranglerRollback(ver), kind = TerminalEntryKind.WRANGLER)
        }
      }
      "routes" -> {
        val ws = workspace ?: return ShellExecutionResult("No Worker open.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        val lines = buildString {
          ws.workersDevUrl?.let { appendLine("workers.dev -> $it (${if (ws.workersDevEnabled) "enabled" else "disabled"})") }
          ws.routes.forEach { r -> appendLine("route       -> ${r.pattern}") }
        }.ifBlank { "(no routes)" }
        ShellExecutionResult(lines.trimEnd(), kind = TerminalEntryKind.WRANGLER)
      }
      "secret" -> {
        val ws = workspace ?: return ShellExecutionResult("No Worker open.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        val action = args.getOrNull(1)?.lowercase()
        when (action) {
          "list" -> {
            val secrets = ws.bindings.filter { it.type == "secret_text" || it.type == "plain_text" }
            val text =
              if (secrets.isEmpty()) "(no bindings or secrets)"
              else secrets.joinToString("\n") { "${it.name} [${it.type}]" }
            ShellExecutionResult(text, kind = TerminalEntryKind.WRANGLER)
          }
          "put" -> {
            val key = args.getOrNull(2)
            val value = args.getOrNull(3)
            if (accountId == null || key.isNullOrBlank() || value == null) {
              ShellExecutionResult("Usage: wrangler secret put <NAME> <VALUE>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            } else {
              repository.putSecret(accountId, ws.scriptName, key, value).fold(
                onSuccess = { ShellExecutionResult("Uploaded encrypted secret '$key' to ${ws.scriptName}", kind = TerminalEntryKind.WRANGLER) },
                onFailure = { ShellExecutionResult(it.localizedMessage ?: "secret put failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
              )
            }
          }
          "delete" -> {
            val key = args.getOrNull(2)
            if (accountId == null || key.isNullOrBlank()) {
              ShellExecutionResult("Usage: wrangler secret delete <NAME>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            } else {
              repository.deleteSecret(accountId, ws.scriptName, key).fold(
                onSuccess = { ShellExecutionResult("Deleted secret '$key'", kind = TerminalEntryKind.WRANGLER) },
                onFailure = { ShellExecutionResult(it.localizedMessage ?: "secret delete failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
              )
            }
          }
          else -> ShellExecutionResult("Usage: wrangler secret <list|put|delete>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
      }
      "kv:namespace" -> {
        if (accountId == null) return ShellExecutionResult("Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        repository.listKvNamespaces(accountId).fold(
          onSuccess = { list ->
            val out = if (list.isEmpty()) "(no KV namespaces)" else list.joinToString("\n") { "${it.id}  ${it.title}" }
            ShellExecutionResult(out, kind = TerminalEntryKind.WRANGLER)
          },
          onFailure = { ShellExecutionResult(it.localizedMessage ?: "KV list failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
        )
      }
      "kv:key" -> {
        if (accountId == null) return ShellExecutionResult("Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        val action = args.getOrNull(1)?.lowercase()
        val nsFlagIdx = args.indexOfFirst { it == "--namespace-id" || it == "-n" }
        val explicitNs = if (nsFlagIdx >= 0) args.getOrNull(nsFlagIdx + 1) else null
        val positional =
          args.drop(2).filterIndexed { i, tok ->
            val origIdx = i + 2
            tok != "--namespace-id" && tok != "-n" && origIdx != nsFlagIdx + 1
          }
        val nsId =
          explicitNs
            ?: workspace?.bindings?.firstOrNull { it.type == "kv_namespace" && !it.namespaceId.isNullOrBlank() }?.namespaceId
            ?: repository.listKvNamespaces(accountId).getOrNull()?.firstOrNull()?.id
            ?: return ShellExecutionResult("No KV namespace ID provided or bound. Pass --namespace-id <ID>.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)

        when (action) {
          "list" ->
            repository.listKvKeys(accountId, nsId).fold(
              onSuccess = { keys -> ShellExecutionResult(if (keys.isEmpty()) "(empty namespace)" else keys.joinToString("\n") { it.name }, kind = TerminalEntryKind.WRANGLER) },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "kv list failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          "get" -> {
            val key = positional.getOrNull(0) ?: return ShellExecutionResult("Usage: wrangler kv:key get <KEY> [--namespace-id ID]", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            repository.readKvValue(accountId, nsId, key).fold(
              onSuccess = { v -> ShellExecutionResult(JqEvaluator.prettyPrintIfJson(v), kind = TerminalEntryKind.WRANGLER) },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "kv get failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          }
          "put" -> {
            val key = positional.getOrNull(0)
            val valStr = positional.getOrNull(1)
            if (key == null || valStr == null) return ShellExecutionResult("Usage: wrangler kv:key put <KEY> <VAL>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            repository.writeKvValue(accountId, nsId, key, valStr).fold(
              onSuccess = { ShellExecutionResult("Wrote KV key '$key'", kind = TerminalEntryKind.WRANGLER) },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "kv put failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          }
          "delete" -> {
            val key = positional.getOrNull(0) ?: return ShellExecutionResult("Usage: wrangler kv:key delete <KEY>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            repository.deleteKvKey(accountId, nsId, key).fold(
              onSuccess = { ShellExecutionResult("Deleted KV key '$key'", kind = TerminalEntryKind.WRANGLER) },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "kv delete failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          }
          else -> ShellExecutionResult("Usage: wrangler kv:key <list|get|put|delete> ...", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
      }
      else -> ShellExecutionResult("Unknown wrangler subcommand '$sub'. Run 'help' for supported commands.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
    }
  }

  private suspend fun executeRealLinuxShell(
    command: String,
    stdin: String?,
  ): ShellExecutionResult =
    withContext(Dispatchers.IO) {
      try {
        val pb = ProcessBuilder("/system/bin/sh", "-c", command)
        pb.directory(currentDir)
        pb.redirectErrorStream(true)
        pb.environment().putAll(sessionEnv)

        val process = pb.start()
        if (stdin != null) {
          process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(stdin)
            writer.flush()
          }
        } else {
          process.outputStream.close()
        }

        val completed = process.waitFor(15, TimeUnit.SECONDS)
        if (!completed) {
          process.destroyForcibly()
          return@withContext ShellExecutionResult(
            output = "Process timed out after 15s",
            isError = true,
            exitCode = 124,
            kind = TerminalEntryKind.ERROR,
          )
        }

        val text = process.inputStream.bufferedReader(Charsets.UTF_8).readText().trimEnd()
        val code = process.exitValue()
        ShellExecutionResult(
          output = text.ifEmpty { if (code == 0) "(exit 0)" else "(exit $code)" },
          isError = code != 0,
          exitCode = code,
          kind = if (code == 0) TerminalEntryKind.SHELL else TerminalEntryKind.ERROR,
        )
      } catch (t: Throwable) {
        ShellExecutionResult(
          output = "sh: ${t.localizedMessage ?: "execution failed"}",
          isError = true,
          exitCode = 127,
          kind = TerminalEntryKind.ERROR,
        )
      }
    }

  fun autocomplete(input: String, workspace: WorkerWorkspace?): String {
    val trimmed = input.trimStart()
    val parts = trimmed.split(Regex("\\s+"))
    val last = parts.lastOrNull().orEmpty()
    val candidates = buildList {
      addAll(
        listOf(
          "curl -i ",
          "curl -v ",
          "wrangler deploy",
          "wrangler tail",
          "wrangler list",
          "wrangler whoami",
          "wrangler deployments",
          "wrangler secret list",
          "wrangler kv:namespace list",
          "git status",
          "git diff",
          "git add .",
          "git commit -m \"",
          "git push",
          "git pull",
          "git log",
          "git branch",
          "git clone ",
          "jq .",
          "ls -la",
          "cat ",
          "grep -rn ",
          "uname -a",
          "clear",
          "help",
        ),
      )
      workspace?.files?.forEach { add(it.path) }
    }
    val match = candidates.firstOrNull { it.startsWith(last, ignoreCase = true) && it != last }
    return if (match != null && parts.isNotEmpty()) {
      val prefix = input.dropLast(last.length)
      "$prefix$match"
    } else input
  }
}
