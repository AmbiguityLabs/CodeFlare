package com.example.terminal

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.util.Base64
import com.example.data.remote.CfAccount
import com.example.data.remote.CfKvBulkItem
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import com.example.data.repository.CloudflareRepository
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.pow
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
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class ShellExecutionResult(
  val output: String,
  val isError: Boolean = false,
  val exitCode: Int = 0,
  val kind: TerminalEntryKind = TerminalEntryKind.SHELL,
  val httpMeta: HttpTraceMeta? = null,
  val clearedScreen: Boolean = false,
  val updatedWorkspaceFiles: List<WorkerFile>? = null,
  val openTabRequest: String? = null,
  val editFileRequest: File? = null,
  val openProjectRequest: String? = null,
  val importZipPathRequest: String? = null,
)

private class TerminalSessionState(
  val id: String,
  val title: String,
  var currentDir: File,
  var previousDir: File? = null,
  val sessionEnv: MutableMap<String, String>,
  val aliases: MutableMap<String, String> =
    mutableMapOf(
      "ll" to "ls -la",
      "la" to "ls -la",
      "l" to "ls -CF",
      ".." to "cd ..",
      "..." to "cd ../..",
      "cls" to "clear",
    ),
  @Volatile var activeProcess: Process? = null,
  @Volatile var activeStdinWriter: BufferedWriter? = null,
  @Volatile var interruptedByUser: Boolean = false,
)

class LinuxTerminalEngine(
  private val context: Context,
  private val repository: CloudflareRepository,
  rootDirOverride: File? = null,
) {
  @Volatile
  private var rootWorkspacesDir: File =
    (rootDirOverride ?: File(context.filesDir, "edgecode_workspaces")).apply { mkdirs() }
  private var lastSyncedScript: String? = null
  private val sessionCounter = AtomicInteger(1)
  private val sessions = ConcurrentHashMap<String, TerminalSessionState>()
  private val sessionOrder = mutableListOf<String>()

  @Volatile
  private var activeSessionId: String = "sh-1"

  init {
    val first = createSessionState(id = "sh-1", title = "sh-1", initialDir = rootWorkspacesDir)
    sessions[first.id] = first
    sessionOrder.add(first.id)
  }

  val sessionEnv: MutableMap<String, String>
    get() = activeSession().sessionEnv

  private fun createSessionState(
    id: String,
    title: String,
    initialDir: File,
  ): TerminalSessionState {
    val existingPath = System.getenv("PATH").orEmpty()
    val mergedPath =
      listOf("/system/bin", "/system/xbin", "/vendor/bin", existingPath)
        .filter { it.isNotBlank() }
        .joinToString(":")
    return TerminalSessionState(
      id = id,
      title = title,
      currentDir = initialDir,
      sessionEnv =
        mutableMapOf(
          "USER" to "dev",
          "TERM" to "xterm-256color",
          "SHELL" to "/system/bin/sh",
          "HOME" to rootWorkspacesDir.absolutePath,
          "TMPDIR" to context.cacheDir.absolutePath,
          "PATH" to mergedPath,
        ),
    )
  }

  private fun activeSession(): TerminalSessionState =
    sessions[activeSessionId] ?: sessions.values.first()

  private fun sessionFor(sessionId: String?): TerminalSessionState =
    if (sessionId != null) sessions[sessionId] ?: activeSession() else activeSession()

  fun getActiveSessionId(): String = activeSessionId

  @Synchronized
  fun listSessionTabs(workspace: WorkerWorkspace?): List<TerminalSessionTab> =
    sessionOrder.mapNotNull { id ->
      sessions[id]?.let { state ->
        TerminalSessionTab(
          id = state.id,
          title = state.title,
          promptPath = promptPathFor(state.currentDir, workspace),
          isBusy = state.activeProcess != null,
        )
      }
    }

  @Synchronized
  fun createSession(workspace: WorkerWorkspace?): TerminalSessionTab {
    val idx = sessionCounter.incrementAndGet()
    val id = "sh-$idx"
    val wsDir = workspace?.let { File(rootWorkspacesDir, it.scriptName).apply { mkdirs() } } ?: rootWorkspacesDir
    val state = createSessionState(id = id, title = id, initialDir = wsDir)
    sessions[id] = state
    sessionOrder.add(id)
    activeSessionId = id
    return TerminalSessionTab(
      id = state.id,
      title = state.title,
      promptPath = promptPathFor(state.currentDir, workspace),
      isBusy = false,
    )
  }

  @Synchronized
  fun switchSession(sessionId: String): Boolean {
    if (!sessions.containsKey(sessionId)) return false
    activeSessionId = sessionId
    return true
  }

  @Synchronized
  fun closeSession(sessionId: String): String {
    if (sessionOrder.size <= 1) return activeSessionId
    sessions[sessionId]?.let { st ->
      runCatching { st.activeProcess?.destroyForcibly() }
    }
    sessions.remove(sessionId)
    sessionOrder.remove(sessionId)
    if (activeSessionId == sessionId) {
      activeSessionId = sessionOrder.lastOrNull() ?: "sh-1"
    }
    return activeSessionId
  }

  fun interruptSession(sessionId: String? = null): Boolean {
    val session = sessionFor(sessionId)
    val proc = session.activeProcess ?: return false
    session.interruptedByUser = true
    runCatching { session.activeStdinWriter?.close() }
    return runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        proc.destroyForcibly()
      } else {
        proc.destroy()
      }
      true
    }.getOrDefault(false)
  }

  fun sendStdin(line: String, sessionId: String? = null): Boolean {
    val session = sessionFor(sessionId)
    val writer = session.activeStdinWriter ?: return false
    return runCatching {
      writer.write(line)
      writer.newLine()
      writer.flush()
      true
    }.getOrDefault(false)
  }

  fun updateRootWorkspacesDir(newRoot: File) {
    val prepared = newRoot.apply { mkdirs() }
    if (prepared.absolutePath != rootWorkspacesDir.absolutePath) {
      rootWorkspacesDir = prepared
      lastSyncedScript = null
      sessions.values.forEach { st ->
        st.currentDir = prepared
        st.sessionEnv["HOME"] = prepared.absolutePath
      }
    }
  }

  fun currentDirPath(sessionId: String? = null): String = sessionFor(sessionId).currentDir.absolutePath

  fun currentPromptPath(workspace: WorkerWorkspace?, sessionId: String? = null): String =
    promptPathFor(sessionFor(sessionId).currentDir, workspace)

  private fun promptPathFor(dir: File, workspace: WorkerWorkspace?): String {
    val wsName = workspace?.scriptName ?: "scratch"
    val wsDir = File(rootWorkspacesDir, wsName)
    val homePath = rootWorkspacesDir.absolutePath
    return when {
      workspace != null && dir.absolutePath == wsDir.absolutePath -> "~/$wsName"
      workspace != null && dir.absolutePath.startsWith(wsDir.absolutePath + "/") ->
        "~/$wsName/${dir.relativeTo(wsDir).path}"
      dir.absolutePath == homePath -> "~"
      dir.absolutePath.startsWith("$homePath/") -> "~/${dir.relativeTo(rootWorkspacesDir).path}"
      else -> dir.absolutePath.ifBlank { "/" }
    }
  }

  fun syncWorkspaceToDisk(workspace: WorkerWorkspace?) {
    val ws = workspace ?: return
    val safeScriptName = ws.scriptName.ifBlank { "scratch" }.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    val wsDir = File(rootWorkspacesDir, safeScriptName)
    if (wsDir.exists() && wsDir.isFile) {
      wsDir.delete()
    }
    wsDir.mkdirs()
    if (lastSyncedScript != safeScriptName) {
      activeSession().currentDir = wsDir
      lastSyncedScript = safeScriptName
    }
    sessions.values.forEach { st ->
      st.sessionEnv["WORKER"] = safeScriptName
      st.sessionEnv["COMPAT_DATE"] = ws.compatibilityDate
      ws.workersDevUrl?.let { st.sessionEnv["BASE_URL"] = it } ?: st.sessionEnv.remove("BASE_URL")
    }

    val activePaths = ws.files.filterNot { it.isVirtualReadOnly }.map { it.path }.toSet()
    runCatching {
      wsDir.walkTopDown().filter { it.isFile }.forEach { f ->
        val rel = f.relativeTo(wsDir).path.replace('\\', '/')
        if (!rel.startsWith(".git/") && !rel.startsWith(".edgecode/") && rel !in activePaths) {
          f.delete()
        }
      }
    }
    for (wf in ws.files) {
      if (wf.path.isBlank()) continue
      val dest = File(wsDir, wf.path)
      if (dest.isDirectory) {
        dest.deleteRecursively()
      }
      dest.parentFile?.let { parent ->
        if (parent.isFile) parent.delete()
        parent.mkdirs()
      }
      runCatching {
        val current = if (dest.exists() && dest.isFile) dest.readText(Charsets.UTF_8) else null
        if (current != wf.content) {
          dest.writeText(wf.content, Charsets.UTF_8)
        }
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
          if (rel.startsWith(".git/") || rel.startsWith(".edgecode/")) return@mapNotNull null
          if (file.length() > 2L * 1024L * 1024L) return@mapNotNull null
          val sample = ByteArray(file.length().coerceAtMost(2048).toInt())
          val isBinary =
            runCatching {
              FileInputStream(file).use { fis ->
                val n = fis.read(sample)
                (0 until n).any { sample[it].toInt() == 0 }
              }
            }.getOrDefault(false)
          if (isBinary) return@mapNotNull null
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
    sessionId: String? = null,
    onLiveOutput: (String) -> Unit = {},
  ): ShellExecutionResult {
    withContext(Dispatchers.IO) {
      syncWorkspaceToDisk(workspace)
    }
    val session = sessionFor(sessionId)
    val normalized = ShellTokenizer.normalizeMultilineCommand(rawInput)
    if (normalized.isBlank()) {
      return ShellExecutionResult(output = "")
    }

    val chainedCommands = ShellTokenizer.splitCommandList(normalized)
    if (chainedCommands.isEmpty()) {
      return ShellExecutionResult(output = "")
    }

    val combinedOutputs = mutableListOf<String>()
    var lastResult = ShellExecutionResult(output = "")

    for (item in chainedCommands) {
      val shouldRun =
        when (item.conditionFromPrevious) {
          ShellChainOp.ALWAYS -> true
          ShellChainOp.AND_ON_SUCCESS -> !lastResult.isError && lastResult.exitCode == 0
          ShellChainOp.OR_ON_FAILURE -> lastResult.isError || lastResult.exitCode != 0
        }
      if (!shouldRun) continue

      val res =
        executePipelineCommand(
          rawCommand = item.command,
          session = session,
          workspace = workspace,
          account = account,
          history = history,
          onWranglerDeploy = onWranglerDeploy,
          onWranglerTailStart = onWranglerTailStart,
          onWranglerTailStop = onWranglerTailStop,
          onWranglerRollback = onWranglerRollback,
          onGitCommand = onGitCommand,
          onLiveOutput = onLiveOutput,
        )
      lastResult = res
      if (res.clearedScreen) {
        return res
      }
      if (res.output.isNotBlank()) {
        combinedOutputs.add(res.output)
      }
      if (res.editFileRequest != null || res.openTabRequest != null || res.openProjectRequest != null || res.importZipPathRequest != null) {
        break
      }
    }

    val syncedBack = withContext(Dispatchers.IO) { readUpdatedFilesFromDisk(workspace) }
    val finalOutput = combinedOutputs.joinToString("\n")
    return lastResult.copy(
      output = finalOutput.ifBlank { lastResult.output },
      updatedWorkspaceFiles = syncedBack ?: lastResult.updatedWorkspaceFiles,
    )
  }

  private suspend fun executePipelineCommand(
    rawCommand: String,
    session: TerminalSessionState,
    workspace: WorkerWorkspace?,
    account: CfAccount?,
    history: List<String>,
    onWranglerDeploy: suspend () -> String,
    onWranglerTailStart: suspend () -> String,
    onWranglerTailStop: suspend () -> String,
    onWranglerRollback: suspend (String) -> String,
    onGitCommand: (suspend (List<String>) -> ShellExecutionResult)?,
    onLiveOutput: (String) -> Unit,
  ): ShellExecutionResult {
    val aliasExpanded = expandAliasIfPresent(rawCommand, session.aliases)
    val expanded = ShellTokenizer.expandEnvVars(aliasExpanded, session.sessionEnv)
    val pipeStages = ShellTokenizer.splitUnquotedPipe(expanded)
    if (pipeStages.isEmpty()) return ShellExecutionResult(output = "")

    var pipedInput: String? = null
    var lastResult = ShellExecutionResult(output = "")
    var redirectTarget: Pair<String, Boolean>? = null

    for ((stageIndex, rawStageCmd) in pipeStages.withIndex()) {
      var stageToRun = rawStageCmd
      if (stageIndex == 0) {
        val (withoutInput, inputFilePath) = parseInputRedirection(stageToRun)
        if (inputFilePath != null) {
          val resolvedIn = resolvePath(session.currentDir, inputFilePath, session)
          if (!resolvedIn.exists() || !resolvedIn.isFile) {
            return ShellExecutionResult(
              output = "sh: $inputFilePath: No such file",
              isError = true,
              exitCode = 1,
              kind = TerminalEntryKind.ERROR,
            )
          }
          pipedInput = resolvedIn.readText(Charsets.UTF_8)
          stageToRun = withoutInput
        }
      }

      val (stageCmd, targetPath, appendMode) =
        if (stageIndex == pipeStages.lastIndex) parseRedirection(stageToRun)
        else Triple(stageToRun, null, false)
      if (targetPath != null) {
        redirectTarget = targetPath to appendMode
      }

      val res =
        executeSingleStage(
          stageCmd = stageCmd,
          stdin = pipedInput,
          session = session,
          workspace = workspace,
          account = account,
          history = history,
          onWranglerDeploy = onWranglerDeploy,
          onWranglerTailStart = onWranglerTailStart,
          onWranglerTailStop = onWranglerTailStop,
          onWranglerRollback = onWranglerRollback,
          onGitCommand = onGitCommand,
          onLiveOutput = onLiveOutput,
        )
      if (res.clearedScreen) return res
      lastResult = res
      if (res.isError && stageIndex < pipeStages.lastIndex) {
        return res
      }
      pipedInput =
        if (stageIndex < pipeStages.lastIndex && res.httpMeta != null) {
          res.httpMeta.bodyText
        } else {
          res.output
        }
    }

    if (redirectTarget != null && !lastResult.isError) {
      val (targetPath, appendMode) = redirectTarget
      runCatching {
        val outFile = resolvePath(session.currentDir, targetPath, session)
        outFile.parentFile?.mkdirs()
        val textToWrite = if (lastResult.output.endsWith("\n")) lastResult.output else "${lastResult.output}\n"
        if (appendMode) outFile.appendText(textToWrite, Charsets.UTF_8)
        else outFile.writeText(textToWrite, Charsets.UTF_8)
        lastResult = lastResult.copy(output = "")
      }.onFailure { err ->
        lastResult =
          lastResult.copy(
            output = "sh: $targetPath: ${err.localizedMessage ?: "write failed"}",
            isError = true,
            exitCode = 1,
            kind = TerminalEntryKind.ERROR,
          )
      }
    }

    return lastResult
  }

  private fun expandAliasIfPresent(
    command: String,
    aliases: Map<String, String>,
  ): String {
    val trimmed = command.trimStart()
    if (trimmed.isEmpty()) return command
    val firstSpace = trimmed.indexOfFirst { it.isWhitespace() }
    val firstWord = if (firstSpace < 0) trimmed else trimmed.substring(0, firstSpace)
    val replacement = aliases[firstWord] ?: return command
    val rest = if (firstSpace < 0) "" else trimmed.substring(firstSpace)
    return "$replacement$rest"
  }

  private fun resolvePath(baseDir: File, pathArg: String, session: TerminalSessionState): File {
    val home = File(session.sessionEnv["HOME"] ?: rootWorkspacesDir.absolutePath)
    val raw =
      when {
        pathArg == "~" -> home
        pathArg.startsWith("~/") -> File(home, pathArg.removePrefix("~/"))
        pathArg.startsWith("/") -> File(pathArg)
        else -> File(baseDir, pathArg)
      }
    return runCatching { raw.canonicalFile }.getOrElse { raw.absoluteFile }
  }

  private fun parseInputRedirection(stageCmd: String): Pair<String, String?> {
    var inSingle = false
    var inDouble = false
    var escaped = false
    var idx = -1

    for (i in stageCmd.indices) {
      val ch = stageCmd[i]
      if (escaped) {
        escaped = false
        continue
      }
      if (ch == '\\' && !inSingle) {
        escaped = true
        continue
      }
      if (ch == '\'' && !inDouble) {
        inSingle = !inSingle
        continue
      }
      if (ch == '"' && !inSingle) {
        inDouble = !inDouble
        continue
      }
      if (!inSingle && !inDouble && ch == '<') {
        idx = i
        break
      }
    }
    if (idx < 0) return stageCmd to null
    val left = stageCmd.substring(0, idx).trim()
    val rightToken = ShellTokenizer.tokenize(stageCmd.substring(idx + 1).trim()).firstOrNull()
    return if (rightToken.isNullOrBlank()) stageCmd to null else left to rightToken
  }

  private fun parseRedirection(stageCmd: String): Triple<String, String?, Boolean> {
    var inSingle = false
    var inDouble = false
    var escaped = false
    var redirIdx = -1
    var isAppend = false

    var i = 0
    while (i < stageCmd.length) {
      val ch = stageCmd[i]
      if (escaped) {
        escaped = false
        i++
        continue
      }
      if (ch == '\\' && !inSingle) {
        escaped = true
        i++
        continue
      }
      if (ch == '\'' && !inDouble) {
        inSingle = !inSingle
        i++
        continue
      }
      if (ch == '"' && !inSingle) {
        inDouble = !inDouble
        i++
        continue
      }
      if (!inSingle && !inDouble && ch == '>') {
        val prev = stageCmd.getOrNull(i - 1)
        if (prev != null && prev.isDigit()) {
          i++
          continue
        }
        redirIdx = i
        if (stageCmd.getOrNull(i + 1) == '>') {
          isAppend = true
        }
        break
      }
      i++
    }

    if (redirIdx < 0) return Triple(stageCmd, null, false)
    val left = stageCmd.substring(0, redirIdx).trim()
    val afterArrow = stageCmd.substring(redirIdx + if (isAppend) 2 else 1).trim()
    val targetToken = ShellTokenizer.tokenize(afterArrow).firstOrNull()
    return if (targetToken.isNullOrBlank()) Triple(stageCmd, null, false)
    else Triple(left, targetToken, isAppend)
  }

  private suspend fun executeSingleStage(
    stageCmd: String,
    stdin: String?,
    session: TerminalSessionState,
    workspace: WorkerWorkspace?,
    account: CfAccount?,
    history: List<String>,
    onWranglerDeploy: suspend () -> String,
    onWranglerTailStart: suspend () -> String,
    onWranglerTailStop: suspend () -> String,
    onWranglerRollback: suspend (String) -> String,
    onGitCommand: (suspend (List<String>) -> ShellExecutionResult)?,
    onLiveOutput: (String) -> Unit,
  ): ShellExecutionResult {
    val tokens = ShellTokenizer.tokenize(stageCmd)
    if (tokens.isEmpty()) return ShellExecutionResult(output = "")
    val cmd = tokens[0]

    if (tokens.size == 1 && cmd.contains('=') && !cmd.startsWith("=")) {
      val idx = cmd.indexOf('=')
      val key = cmd.substring(0, idx).trim()
      val value = cmd.substring(idx + 1)
      if (key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
        session.sessionEnv[key] = value
        return ShellExecutionResult(output = "")
      }
    }

    when (cmd) {
      "clear", "cls" -> return ShellExecutionResult(output = "", clearedScreen = true)
      "true" -> return ShellExecutionResult(output = "", exitCode = 0)
      "false" -> return ShellExecutionResult(output = "", isError = true, exitCode = 1)
      "whoami" -> return ShellExecutionResult(output = session.sessionEnv["USER"] ?: "dev")
      "hostname" -> return ShellExecutionResult(output = "edgecode-linux")
      "id" -> return ShellExecutionResult(output = "uid=1000(dev) gid=1000(dev) groups=1000(dev),3003(inet)")
      "pwd" -> return ShellExecutionResult(output = session.currentDir.absolutePath)
      "uuid", "uuidgen" -> return ShellExecutionResult(output = UUID.randomUUID().toString())
      "help" -> return ShellExecutionResult(output = buildHelpText(), kind = TerminalEntryKind.SYSTEM)
      "pkg", "toolbox", "applets" -> return ShellExecutionResult(output = buildToolboxInventory(), kind = TerminalEntryKind.SYSTEM)
      "sysinfo", "neofetch" -> return ShellExecutionResult(output = buildSysInfoReport(session, workspace), kind = TerminalEntryKind.SYSTEM)
      "projects" -> return ShellExecutionResult(output = listWorkspaceProjects(workspace), kind = TerminalEntryKind.SYSTEM)
    }

    if (cmd == "ide") {
      val name = tokens.getOrNull(1)
      return if (name.isNullOrBlank()) {
        ShellExecutionResult("Usage: ide <project-name>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      } else {
        ShellExecutionResult(output = "Opening project '$name' in EdgeCode IDE…", openProjectRequest = name, kind = TerminalEntryKind.SYSTEM)
      }
    }

    if (cmd == "zipimport") {
      val arg = tokens.getOrNull(1)
      if (arg.isNullOrBlank()) {
        return ShellExecutionResult("Usage: zipimport <archive.zip>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
      val resolved = resolvePath(session.currentDir, arg, session)
      return if (!resolved.exists() || !resolved.isFile) {
        ShellExecutionResult("zipimport: $arg: File not found", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      } else {
        ShellExecutionResult(output = "Inspecting '${resolved.name}' for project import…", importZipPathRequest = resolved.absolutePath, kind = TerminalEntryKind.SYSTEM)
      }
    }

    if (cmd == "export") {
      val assignment = tokens.getOrNull(1)
      if (assignment != null && assignment.contains('=')) {
        val idx = assignment.indexOf('=')
        val k = assignment.substring(0, idx).trim()
        val v = assignment.substring(idx + 1)
        session.sessionEnv[k] = v
        return ShellExecutionResult(output = "")
      }
      return ShellExecutionResult(
        output = session.sessionEnv.entries.sortedBy { it.key }.joinToString("\n") { "export ${it.key}=${it.value}" },
      )
    }

    if (cmd == "unset") {
      tokens.drop(1).forEach { session.sessionEnv.remove(it) }
      return ShellExecutionResult(output = "")
    }

    if (cmd == "env" || cmd == "printenv") {
      val combined = System.getenv() + session.sessionEnv
      val out = combined.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }
      return ShellExecutionResult(output = out)
    }

    if (cmd == "alias") {
      val arg = stageCmd.trimStart().removePrefix("alias").trim()
      if (arg.isEmpty()) {
        val list = session.aliases.entries.sortedBy { it.key }.joinToString("\n") { "alias ${it.key}='${it.value}'" }
        return ShellExecutionResult(output = list.ifBlank { "(no aliases)" })
      }
      val eq = arg.indexOf('=')
      if (eq > 0) {
        val key = arg.substring(0, eq).trim()
        val rawVal = arg.substring(eq + 1).trim().removeSurrounding("'").removeSurrounding("\"")
        session.aliases[key] = rawVal
        return ShellExecutionResult(output = "alias $key='$rawVal'")
      }
      val match = session.aliases[arg]
      return if (match != null) {
        ShellExecutionResult(output = "alias $arg='$match'")
      } else {
        ShellExecutionResult("sh: alias: $arg: not found", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
    }

    if (cmd == "unalias") {
      tokens.drop(1).forEach { session.aliases.remove(it) }
      return ShellExecutionResult(output = "")
    }

    if (cmd == "cd") {
      val targetArg = tokens.getOrNull(1)
      val wsDir = workspace?.let { File(rootWorkspacesDir, it.scriptName) } ?: rootWorkspacesDir
      val nextDir =
        when (targetArg) {
          null, "~" -> if (workspace != null) wsDir else File(session.sessionEnv["HOME"] ?: rootWorkspacesDir.absolutePath)
          "-" -> session.previousDir ?: wsDir
          else -> resolvePath(session.currentDir, targetArg, session)
        }
      return if (nextDir.exists() && nextDir.isDirectory) {
        session.previousDir = session.currentDir
        session.currentDir = nextDir
        session.sessionEnv["PWD"] = nextDir.absolutePath
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

    if (cmd == "which" || cmd == "type" || cmd == "command") {
      val query = if (cmd == "command") tokens.drop(1).lastOrNull { !it.startsWith("-") } else tokens.getOrNull(1)
      if (query.isNullOrBlank()) {
        return ShellExecutionResult("Usage: $cmd <command>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
      val resolved = resolveCommandLocation(query, session)
      return if (resolved != null) {
        ShellExecutionResult(output = resolved)
      } else {
        ShellExecutionResult("$query not found", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
    }

    if (cmd == "echo") {
      var noNewline = false
      var interpretEscapes = false
      var start = 1
      while (start < tokens.size && tokens[start].startsWith("-") && tokens[start].length > 1 && tokens[start].drop(1).all { it == 'n' || it == 'e' || it == 'E' }) {
        for (c in tokens[start].drop(1)) {
          if (c == 'n') noNewline = true
          if (c == 'e') interpretEscapes = true
          if (c == 'E') interpretEscapes = false
        }
        start++
      }
      val rawOut = tokens.drop(start).joinToString(" ")
      val processed =
        if (interpretEscapes) {
          rawOut
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\\\", "\\")
        } else rawOut
      return ShellExecutionResult(output = if (noNewline) processed else processed)
    }

    if (cmd == "printf") {
      val fmt = tokens.getOrNull(1).orEmpty()
      val decoded =
        fmt
          .replace("\\n", "\n")
          .replace("\\t", "\t")
          .replace("\\\\", "\\")
      val args = tokens.drop(2)
      var idx = 0
      val out =
        Regex("%[sd]").replace(decoded) { m ->
          val v = args.getOrNull(idx++) ?: ""
          if (m.value == "%d") (v.toLongOrNull() ?: 0L).toString() else v
        }
      return ShellExecutionResult(output = out)
    }

    if (cmd == "history") {
      val formatted = history.mapIndexed { idx, line -> "${idx + 1}  $line" }.joinToString("\n")
      return ShellExecutionResult(output = formatted.ifBlank { "(no history yet)" })
    }

    if (cmd == "sleep") {
      val secs = tokens.getOrNull(1)?.toDoubleOrNull()
      if (secs == null || secs < 0) {
        return ShellExecutionResult("Usage: sleep <seconds>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
      withContext(Dispatchers.IO) {
        Thread.sleep((secs * 1000.0).toLong().coerceAtMost(60_000L))
      }
      return ShellExecutionResult(output = "")
    }

    if (cmd == "open" || cmd == "code" || cmd == "nano" || cmd == "vi" || cmd == "vim" || cmd == "edit") {
      val target = tokens.getOrNull(1)
      if (target.isNullOrBlank()) {
        return ShellExecutionResult("Usage: $cmd <file-path>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
      val resolved = resolvePath(session.currentDir, target, session)
      val wsDir = workspace?.let { File(rootWorkspacesDir, it.scriptName).canonicalFile }
      val openTab =
        if (wsDir != null && resolved.absolutePath.startsWith(wsDir.absolutePath + "/")) {
          resolved.relativeTo(wsDir).path.replace('\\', '/')
        } else null
      val preferInlineEdit = cmd in setOf("nano", "vi", "vim", "edit") || openTab == null
      return ShellExecutionResult(
        output = if (preferInlineEdit) "Opening editor for ${resolved.name}…" else "Opening $openTab in IDE editor…",
        openTabRequest = if (!preferInlineEdit) openTab else null,
        editFileRequest = if (preferInlineEdit) resolved else null,
      )
    }

    if (cmd == "tree") {
      return executeTree(tokens.drop(1), session)
    }

    if (cmd == "zip") {
      return executeZip(tokens.drop(1), session)
    }

    if (cmd == "unzip") {
      return executeUnzip(tokens.drop(1), session)
    }

    if (cmd == "base64") {
      return executeBase64(tokens.drop(1), stdin, session)
    }

    if (cmd == "sha256sum" || cmd == "sha1sum" || cmd == "md5sum" || cmd == "sha512sum") {
      val algo =
        when (cmd) {
          "md5sum" -> "MD5"
          "sha1sum" -> "SHA-1"
          "sha512sum" -> "SHA-512"
          else -> "SHA-256"
        }
      return executeDigest(tokens.drop(1), stdin, session, algo)
    }

    if (cmd == "calc") {
      val exprText = tokens.drop(1).joinToString(" ")
      if (exprText.isBlank()) {
        return ShellExecutionResult("Usage: calc <math-expression>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
      return evaluateMathExpression(exprText).fold(
        onSuccess = { ShellExecutionResult(output = it) },
        onFailure = { ShellExecutionResult("calc: ${it.localizedMessage}", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
      )
    }

    if (cmd == "jq") {
      val flags = tokens.drop(1).filter { it.startsWith("-") }
      val rawOut = flags.any { it.contains('r') }
      val compactOut = flags.any { it.contains('c') }
      val positional = tokens.drop(1).filter { !it.startsWith("-") }
      val expr = positional.firstOrNull() ?: "."
      val fileArg = positional.getOrNull(1)
      val inputSource =
        when {
          stdin != null -> stdin
          fileArg != null -> {
            val f = resolvePath(session.currentDir, fileArg, session)
            if (!f.exists()) {
              return ShellExecutionResult("jq: error: Could not open file $fileArg", isError = true, exitCode = 2, kind = TerminalEntryKind.ERROR)
            }
            f.readText(Charsets.UTF_8)
          }
          else -> session.sessionEnv["LAST_BODY"] ?: ""
        }
      return JqEvaluator.evaluate(inputSource, expr, rawOutput = rawOut, compactOutput = compactOut).fold(
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

    if (cmd == "wget") {
      return executeWget(tokens.drop(1), session)
    }

    if (cmd == "nslookup" || cmd == "dig" || cmd == "dns") {
      return executeDnsLookup(tokens.drop(1))
    }

    if (cmd == "tcping") {
      val host = tokens.getOrNull(1) ?: return ShellExecutionResult("Usage: tcping <host> [port]", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      val port = tokens.getOrNull(2)?.toIntOrNull() ?: 443
      return executeTcpPing(host, port, count = 4)
    }

    if (cmd == "ping") {
      val nativePing = executeRealLinuxShell(stageCmd, stdin, session, onLiveOutput)
      if (!nativePing.isError) return nativePing
      val host = tokens.drop(1).lastOrNull { !it.startsWith("-") } ?: ""
      if (host.isNotBlank() && (nativePing.output.contains("permitted", true) || nativePing.exitCode == 127 || nativePing.output.contains("not found", true))) {
        val countIdx = tokens.indexOf("-c")
        val count = if (countIdx >= 0) tokens.getOrNull(countIdx + 1)?.toIntOrNull()?.coerceIn(1, 10) ?: 4 else 4
        return executeTcpPing(host, port = 443, count = count)
      }
      return nativePing
    }

    if (CurlCommandParser.isHttpTestCommand(tokens)) {
      return executeHttpCurl(tokens, workspace, session)
    }

    if (cmd == "wrangler" || (cmd == "npx" && tokens.getOrNull(1) == "wrangler")) {
      val args = if (cmd == "npx") tokens.drop(2) else tokens.drop(1)
      return executeWranglerCommand(
        args = args,
        workspace = workspace,
        account = account,
        session = session,
        onWranglerDeploy = onWranglerDeploy,
        onWranglerTailStart = onWranglerTailStart,
        onWranglerTailStop = onWranglerTailStop,
        onWranglerRollback = onWranglerRollback,
      )
    }

    if (cmd == "git") {
      return onGitCommand?.invoke(tokens.drop(1))
        ?: ShellExecutionResult(
          output = "git: GitHub integration not initialized.",
          isError = true,
          exitCode = 1,
          kind = TerminalEntryKind.ERROR,
        )
    }

    if (CodeRunnerEngine.isCodeRunnerCommand(cmd, tokens, session.currentDir)) {
      return CodeRunnerEngine.execute(
        context = context,
        tokens = tokens,
        stdin = stdin,
        currentDir = session.currentDir,
        workspace = workspace,
        env = session.sessionEnv,
        onLiveOutput = onLiveOutput,
        onRunShellCommand = { subCmd ->
          executePipelineCommand(
            rawCommand = subCmd,
            session = session,
            workspace = workspace,
            account = account,
            history = history,
            onWranglerDeploy = onWranglerDeploy,
            onWranglerTailStart = onWranglerTailStart,
            onWranglerTailStop = onWranglerTailStop,
            onWranglerRollback = onWranglerRollback,
            onGitCommand = onGitCommand,
            onLiveOutput = onLiveOutput,
          )
        },
      )
    }

    return executeRealLinuxShell(stageCmd, stdin, session, onLiveOutput)
  }

  private fun resolveCommandLocation(query: String, session: TerminalSessionState): String? {
    val builtins =
      setOf(
        "cd", "pwd", "export", "unset", "env", "printenv", "alias", "unalias", "echo", "printf",
        "which", "type", "command", "history", "clear", "cls", "sleep", "true", "false", "whoami",
        "id", "hostname", "uname", "date", "help", "pkg", "toolbox", "applets", "sysinfo", "neofetch",
        "projects", "ide", "zipimport", "open", "code", "nano", "vi", "vim", "edit",
        "tree", "zip", "unzip", "base64", "sha256sum", "sha1sum", "md5sum", "sha512sum",
        "uuid", "uuidgen", "calc", "jq", "curl", "http", "fetch", "wget", "nslookup", "dig", "dns",
        "ping", "tcping", "wrangler", "git",
        "run", "node", "nodejs", "js", "ts", "tsx", "ts-node", "npm", "npx",
      )
    session.aliases[query]?.let { return "$query: aliased to '$it'" }
    if (query in builtins) return "/usr/local/bin/$query (EdgeCode builtin)"
    val paths = (session.sessionEnv["PATH"] ?: "/system/bin:/system/xbin").split(':')
    for (dir in paths) {
      if (dir.isBlank()) continue
      val f = File(dir, query)
      if (f.exists() && f.canExecute()) return f.absolutePath
    }
    return null
  }

  private fun executeTree(args: List<String>, session: TerminalSessionState): ShellExecutionResult {
    var showHidden = false
    var maxDepth = 4
    var targetPath: String? = null
    var i = 0
    while (i < args.size) {
      when (args[i]) {
        "-a" -> showHidden = true
        "-L" -> {
          maxDepth = args.getOrNull(i + 1)?.toIntOrNull()?.coerceIn(1, 12) ?: 4
          i++
        }
        else -> if (!args[i].startsWith("-") && targetPath == null) targetPath = args[i]
      }
      i++
    }
    val root = if (targetPath != null) resolvePath(session.currentDir, targetPath, session) else session.currentDir
    if (!root.exists()) {
      return ShellExecutionResult("tree: ${targetPath ?: "."}: No such file or directory", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
    }
    var dirCount = 0
    var fileCount = 0
    val sb = StringBuilder()
    sb.appendLine("\u001B[1;36m${targetPath ?: "."}\u001B[0m")

    fun walk(dir: File, prefix: String, depth: Int) {
      if (depth > maxDepth) return
      val children =
        dir.listFiles()
          ?.filter { showHidden || !it.name.startsWith(".") }
          ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
          .orEmpty()
      children.forEachIndexed { idx, child ->
        val isLast = idx == children.lastIndex
        val branch = if (isLast) "└── " else "├── "
        val nextPrefix = prefix + if (isLast) "    " else "│   "
        if (child.isDirectory) {
          dirCount++
          sb.appendLine("$prefix$branch\u001B[1;34m${child.name}/\u001B[0m")
          walk(child, nextPrefix, depth + 1)
        } else {
          fileCount++
          sb.appendLine("$prefix$branch${child.name}")
        }
      }
    }
    if (root.isDirectory) walk(root, "", 1)
    sb.append("$dirCount directories, $fileCount files")
    return ShellExecutionResult(output = sb.toString())
  }

  private suspend fun executeZip(args: List<String>, session: TerminalSessionState): ShellExecutionResult =
    withContext(Dispatchers.IO) {
      val positional = args.filter { !it.startsWith("-") }
      if (positional.size < 2) {
        return@withContext ShellExecutionResult(
          "Usage: zip [-r] <archive.zip> <file_or_dir...>",
          isError = true,
          exitCode = 1,
          kind = TerminalEntryKind.ERROR,
        )
      }
      val outFile = resolvePath(session.currentDir, positional[0], session)
      val inputs = positional.drop(1).map { resolvePath(session.currentDir, it, session) }
      val missing = inputs.firstOrNull { !it.exists() }
      if (missing != null) {
        return@withContext ShellExecutionResult("zip error: '${missing.name}' not found", isError = true, exitCode = 12, kind = TerminalEntryKind.ERROR)
      }

      runCatching {
        outFile.parentFile?.mkdirs()
        var entryCount = 0
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
          for (src in inputs) {
            if (src.isDirectory) {
              val base = src.parentFile ?: src
              src.walkTopDown().filter { it.isFile && it.canonicalPath != outFile.canonicalPath }.forEach { f ->
                val rel = f.relativeTo(base).path.replace('\\', '/')
                zos.putNextEntry(ZipEntry(rel))
                FileInputStream(f).use { it.copyTo(zos) }
                zos.closeEntry()
                entryCount++
              }
            } else if (src.canonicalPath != outFile.canonicalPath) {
              zos.putNextEntry(ZipEntry(src.name))
              FileInputStream(src).use { it.copyTo(zos) }
              zos.closeEntry()
              entryCount++
            }
          }
        }
        ShellExecutionResult(
          output = "\u001B[32mCreated ${outFile.name}\u001B[0m ($entryCount file(s), ${outFile.length()} bytes)",
        )
      }.getOrElse { err ->
        ShellExecutionResult("zip: ${err.localizedMessage ?: "failed"}", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
    }

  private suspend fun executeUnzip(args: List<String>, session: TerminalSessionState): ShellExecutionResult =
    withContext(Dispatchers.IO) {
      var listOnly = false
      var destDirArg: String? = null
      var archiveArg: String? = null
      var i = 0
      while (i < args.size) {
        when (args[i]) {
          "-l" -> listOnly = true
          "-d" -> {
            destDirArg = args.getOrNull(i + 1)
            i++
          }
          else -> if (!args[i].startsWith("-") && archiveArg == null) archiveArg = args[i]
        }
        i++
      }
      if (archiveArg.isNullOrBlank()) {
        return@withContext ShellExecutionResult(
          "Usage: unzip [-l] <archive.zip> [-d dest_dir]",
          isError = true,
          exitCode = 1,
          kind = TerminalEntryKind.ERROR,
        )
      }
      val zipFile = resolvePath(session.currentDir, archiveArg, session)
      if (!zipFile.exists() || !zipFile.isFile) {
        return@withContext ShellExecutionResult("unzip: cannot find $archiveArg", isError = true, exitCode = 9, kind = TerminalEntryKind.ERROR)
      }
      val destRoot =
        if (destDirArg != null) resolvePath(session.currentDir, destDirArg, session)
        else session.currentDir

      runCatching {
        val sb = StringBuilder()
        var extracted = 0
        var listed = 0
        ZipInputStream(FileInputStream(zipFile).buffered()).use { zis ->
          while (true) {
            val entry = zis.nextEntry ?: break
            try {
              val clean = entry.name.replace('\\', '/').trimStart('/')
              if (clean.split('/').any { it == ".." }) continue
              if (listOnly) {
                listed++
                sb.appendLine("${entry.size.coerceAtLeast(0).toString().padStart(9)}  $clean")
              } else if (entry.isDirectory) {
                File(destRoot, clean).mkdirs()
              } else {
                val target = File(destRoot, clean).canonicalFile
                if (!target.path.startsWith(destRoot.canonicalPath)) continue
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { out -> zis.copyTo(out) }
                extracted++
                sb.appendLine("  inflating: $clean")
              }
            } finally {
              zis.closeEntry()
            }
          }
        }
        if (listOnly) {
          sb.append("$listed item(s) in ${zipFile.name}")
        } else {
          sb.append("\u001B[32mExtracted $extracted file(s) into ${destRoot.name}\u001B[0m")
        }
        ShellExecutionResult(output = sb.toString().trimEnd())
      }.getOrElse { err ->
        ShellExecutionResult("unzip: ${err.localizedMessage ?: "failed"}", isError = true, exitCode = 2, kind = TerminalEntryKind.ERROR)
      }
    }

  private fun executeBase64(args: List<String>, stdin: String?, session: TerminalSessionState): ShellExecutionResult {
    val decode = args.any { it == "-d" || it == "--decode" }
    val fileArg = args.firstOrNull { !it.startsWith("-") && it != "-" }
    val bytes =
      when {
        fileArg != null -> {
          val f = resolvePath(session.currentDir, fileArg, session)
          if (!f.exists()) return ShellExecutionResult("base64: $fileArg: No such file", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
          f.readBytes()
        }
        stdin != null -> stdin.toByteArray(Charsets.UTF_8)
        else -> return ShellExecutionResult("Usage: base64 [-d] <file> (or pipe stdin)", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
    return runCatching {
      val out =
        if (decode) {
          val cleaned = bytes.toString(Charsets.UTF_8).replace(Regex("\\s+"), "")
          Base64.decode(cleaned, Base64.DEFAULT).toString(Charsets.UTF_8)
        } else {
          Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
      ShellExecutionResult(output = out)
    }.getOrElse {
      ShellExecutionResult("base64: invalid input", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
    }
  }

  private fun executeDigest(
    args: List<String>,
    stdin: String?,
    session: TerminalSessionState,
    algo: String,
  ): ShellExecutionResult {
    val targets = args.filter { !it.startsWith("-") }
    if (targets.isEmpty() && stdin != null) {
      val digest = MessageDigest.getInstance(algo).digest(stdin.toByteArray(Charsets.UTF_8))
      val hex = digest.joinToString("") { "%02x".format(it) }
      return ShellExecutionResult(output = "$hex  -")
    }
    if (targets.isEmpty()) {
      return ShellExecutionResult("Usage: $algo <file...> (or pipe stdin)", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
    }
    val lines = mutableListOf<String>()
    var hasErr = false
    for (t in targets) {
      val f = resolvePath(session.currentDir, t, session)
      if (!f.exists() || !f.isFile) {
        lines.add("$algo: $t: No such file")
        hasErr = true
      } else {
        val md = MessageDigest.getInstance(algo)
        FileInputStream(f).use { fis ->
          val buf = ByteArray(8192)
          while (true) {
            val n = fis.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
          }
        }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        lines.add("$hex  $t")
      }
    }
    return ShellExecutionResult(
      output = lines.joinToString("\n"),
      isError = hasErr,
      exitCode = if (hasErr) 1 else 0,
      kind = if (hasErr) TerminalEntryKind.ERROR else TerminalEntryKind.SHELL,
    )
  }

  private fun evaluateMathExpression(expr: String): Result<String> =
    runCatching {
      val cleaned = expr.replace(Regex("\\s+"), "")
      var pos = -1
      var ch = 0

      fun nextChar() {
        pos++
        ch = if (pos < cleaned.length) cleaned[pos].code else -1
      }

      fun eat(expected: Int): Boolean {
        if (ch == expected) {
          nextChar()
          return true
        }
        return false
      }

      lateinit var parseExpression: () -> Double

      fun parseFactor(): Double {
        if (eat('+'.code)) return parseFactor()
        if (eat('-'.code)) return -parseFactor()
        var x: Double
        val startPos = pos
        if (eat('('.code)) {
          x = parseExpression()
          if (!eat(')'.code)) error("Missing closing parenthesis")
        } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
          while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
          x = cleaned.substring(startPos, pos).toDouble()
        } else {
          error("Unexpected token '${if (ch == -1) "EOF" else ch.toChar()}'")
        }
        if (eat('^'.code)) x = x.pow(parseFactor())
        return x
      }

      fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
          when {
            eat('*'.code) -> x *= parseFactor()
            eat('/'.code) -> {
              val d = parseFactor()
              if (d == 0.0) error("Division by zero")
              x /= d
            }
            eat('%'.code) -> {
              val d = parseFactor()
              if (d == 0.0) error("Modulo by zero")
              x %= d
            }
            else -> return x
          }
        }
      }

      parseExpression = {
        var x = parseTerm()
        while (true) {
          if (eat('+'.code)) x += parseTerm()
          else if (eat('-'.code)) x -= parseTerm()
          else break
        }
        x
      }

      nextChar()
      val value = parseExpression()
      if (pos < cleaned.length) error("Unexpected trailing '${cleaned.substring(pos)}'")
      if (value % 1.0 == 0.0 && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
        value.toLong().toString()
      } else {
        value.toString()
      }
    }

  private suspend fun executeWget(args: List<String>, session: TerminalSessionState): ShellExecutionResult =
    withContext(Dispatchers.IO) {
      var outName: String? = null
      var quiet = false
      var url: String? = null
      var i = 0
      while (i < args.size) {
        when (args[i]) {
          "-O", "--output-document" -> {
            outName = args.getOrNull(i + 1)
            i++
          }
          "-q", "--quiet" -> quiet = true
          else -> if (!args[i].startsWith("-") && url == null) url = args[i]
        }
        i++
      }
      val resolvedUrl = url ?: return@withContext ShellExecutionResult(
        "Usage: wget <https://url> [-O filename] [-q]",
        isError = true,
        exitCode = 1,
        kind = TerminalEntryKind.ERROR,
      )
      val fileName = outName ?: resolvedUrl.substringBefore('?').substringAfterLast('/').ifBlank { "index.html" }
      val destFile = resolvePath(session.currentDir, fileName, session)
      val startMs = System.currentTimeMillis()
      val client =
        OkHttpClient.Builder()
          .followRedirects(true)
          .followSslRedirects(true)
          .connectTimeout(20, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .build()
      try {
        val req = Request.Builder().url(resolvedUrl).get().build()
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            return@withContext ShellExecutionResult(
              "wget: HTTP ${resp.code} ${resp.message} for $resolvedUrl",
              isError = true,
              exitCode = 8,
              kind = TerminalEntryKind.ERROR,
            )
          }
          val bytes = resp.body?.bytes() ?: ByteArray(0)
          destFile.parentFile?.mkdirs()
          destFile.writeBytes(bytes)
          val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1)
          val msg =
            if (quiet) ""
            else "\u001B[32mSaved '$fileName'\u001B[0m (${bytes.size} bytes in ${elapsed}ms)"
          ShellExecutionResult(output = msg)
        }
      } catch (t: Throwable) {
        ShellExecutionResult(
          "wget: ${t.localizedMessage ?: "download failed"}",
          isError = true,
          exitCode = 4,
          kind = TerminalEntryKind.ERROR,
        )
      }
    }

  private suspend fun executeDnsLookup(args: List<String>): ShellExecutionResult =
    withContext(Dispatchers.IO) {
      val host = args.lastOrNull { !it.startsWith("-") && !it.startsWith("@") }
      if (host.isNullOrBlank()) {
        return@withContext ShellExecutionResult("Usage: nslookup <hostname>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
      }
      runCatching {
        val start = System.currentTimeMillis()
        val addrs = InetAddress.getAllByName(host)
        val ms = (System.currentTimeMillis() - start).coerceAtLeast(1)
        val lines = buildString {
          appendLine("\u001B[36mName:\u001B[0m $host (${ms}ms)")
          addrs.forEach { a -> appendLine("Address: ${a.hostAddress}") }
        }.trimEnd()
        ShellExecutionResult(output = lines)
      }.getOrElse { err ->
        ShellExecutionResult(
          "DNS lookup failed for '$host': ${err.localizedMessage ?: "unknown host"}",
          isError = true,
          exitCode = 1,
          kind = TerminalEntryKind.ERROR,
        )
      }
    }

  private suspend fun executeTcpPing(host: String, port: Int, count: Int): ShellExecutionResult =
    withContext(Dispatchers.IO) {
      val lines = mutableListOf("\u001B[36mTCPING $host:$port ($count probes)\u001B[0m")
      val rtts = mutableListOf<Long>()
      repeat(count) { seq ->
        val start = System.nanoTime()
        val ok =
          runCatching {
            Socket().use { sock ->
              sock.connect(InetSocketAddress(host, port), 3000)
            }
            true
          }.getOrDefault(false)
        val ms = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1)
        if (ok) {
          rtts.add(ms)
          lines.add("seq=${seq + 1} host=$host port=$port \u001B[32mtime=${ms}ms\u001B[0m")
        } else {
          lines.add("seq=${seq + 1} host=$host port=$port \u001B[31mtimeout/refused\u001B[0m")
        }
      }
      val avg = if (rtts.isNotEmpty()) rtts.average().toLong() else 0L
      lines.add("--- $host:$port: ${rtts.size}/$count succeeded, avg=${avg}ms ---")
      ShellExecutionResult(
        output = lines.joinToString("\n"),
        isError = rtts.isEmpty(),
        exitCode = if (rtts.isEmpty()) 1 else 0,
        kind = if (rtts.isEmpty()) TerminalEntryKind.ERROR else TerminalEntryKind.SHELL,
      )
    }

  private suspend fun executeHttpCurl(
    tokens: List<String>,
    workspace: WorkerWorkspace?,
    session: TerminalSessionState,
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
      var tlsStart = 0L
      var tlsEnd = 0L
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

          override fun secureConnectStart(call: Call) {
            tlsStart = System.nanoTime()
          }

          override fun secureConnectEnd(call: Call, handshake: Handshake?) {
            tlsEnd = System.nanoTime()
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
          .readTimeout(30, TimeUnit.SECONDS)
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
          val rawBytes = if (spec.headOnly) ByteArray(0) else resp.body?.bytes() ?: ByteArray(0)

          if (spec.outputFile != null) {
            val outDest = resolvePath(session.currentDir, spec.outputFile, session)
            outDest.parentFile?.mkdirs()
            outDest.writeBytes(rawBytes)
            val traceSummary = "DNS ${dnsMs}ms • TLS ${tlsMs}ms • TTFB ${ttfbMs}ms • Total ${totalMs}ms"
            return@withContext ShellExecutionResult(
              output = "\u001B[32mSaved ${rawBytes.size} bytes to ${spec.outputFile}\u001B[0m",
              isError = resp.code >= 500,
              exitCode = 0,
              kind = TerminalEntryKind.HTTP,
              httpMeta =
                HttpTraceMeta(
                  method = spec.method,
                  url = resp.request.url.toString(),
                  protocol = resp.protocol.toString(),
                  statusCode = resp.code,
                  statusText = resp.message.ifBlank { "OK" },
                  durationMs = totalMs,
                  timingSummary = traceSummary,
                  headersText = "",
                  bodyText = "",
                  curlEquivalent = CurlCommandParser.toReproducibleCurl(spec),
                ),
            )
          }

          val rawBody = rawBytes.toString(Charsets.UTF_8)
          val formattedBody = JqEvaluator.prettyPrintIfJson(rawBody)
          val headersText =
            resp.headers.names().joinToString("\n") { name ->
              "$name: ${resp.headers.values(name).joinToString(", ")}"
            }

          session.sessionEnv["LAST_STATUS"] = resp.code.toString()
          session.sessionEnv["LAST_URL"] = resp.request.url.toString()
          session.sessionEnv["LAST_BODY"] = rawBody

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
    session: TerminalSessionState,
    onWranglerDeploy: suspend () -> String,
    onWranglerTailStart: suspend () -> String,
    onWranglerTailStop: suspend () -> String,
    onWranglerRollback: suspend (String) -> String,
  ): ShellExecutionResult {
    if (args.isEmpty()) {
      return ShellExecutionResult(
        output = "Usage: wrangler <whoami|list|deploy|dev|tail|deployments|versions|rollback|routes|secret|kv:namespace|kv:key|kv:bulk|d1|r2|pages|queues|durable-objects>",
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
      "dev" -> {
        val ws = workspace ?: return ShellExecutionResult("No Worker open.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        val info = buildString {
          appendLine("Wrangler Dev Preview (${ws.scriptName})")
          appendLine("Entrypoint : ${ws.entryModulePath} (${if (ws.isEsModuleFormat) "ESM" else "Service Worker"})")
          appendLine("Compat Date: ${ws.compatibilityDate}")
          appendLine("Bindings   : ${ws.bindings.size} configured (${ws.bindings.joinToString(", ") { it.name }.ifBlank { "none" }})")
          ws.workersDevUrl?.let { appendLine("Live Edge  : $it") }
          appendLine("Tip: Use 'curl -i /' or open the Run / API Tester tab in the bottom panel to execute HTTP requests.")
        }.trimEnd()
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
                val summary = d.versions.joinToString(", ") { "${it.versionId.take(8)}:${it.percentage.toInt()}%" }.ifBlank { d.id.take(12) }
                val tag = if (idx == 0) "[LIVE]" else "      "
                "$tag $summary  ${d.createdOn.orEmpty()}  ${d.source.orEmpty()}"
              }.joinToString("\n")
            ShellExecutionResult(lines, kind = TerminalEntryKind.WRANGLER)
          },
          onFailure = {
            ShellExecutionResult(it.localizedMessage ?: "Failed to list deployments", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
          },
        )
      }
      "versions" -> {
        val ws = workspace ?: return ShellExecutionResult("No Worker open.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        if (accountId == null) return ShellExecutionResult("Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        repository.listWorkerVersions(accountId, ws.scriptName).fold(
          onSuccess = { versions ->
            val lines =
              if (versions.isEmpty()) "(no Worker versions found)"
              else versions.joinToString("\n") { v ->
                "v${v.number ?: "?"}  ${v.id}  ${v.metadata?.source.orEmpty()}  ${v.metadata?.createdOn.orEmpty()}"
              }
            ShellExecutionResult(lines, kind = TerminalEntryKind.WRANGLER)
          },
          onFailure = {
            ShellExecutionResult(it.localizedMessage ?: "Failed to list versions", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
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
        val ttlFlagIdx = args.indexOfFirst { it == "--ttl" }
        val ttlVal = if (ttlFlagIdx >= 0) args.getOrNull(ttlFlagIdx + 1)?.toLongOrNull() else null
        val positional =
          args.drop(2).filterIndexed { i, tok ->
            val origIdx = i + 2
            tok != "--namespace-id" && tok != "-n" && origIdx != nsFlagIdx + 1 &&
              tok != "--ttl" && origIdx != ttlFlagIdx + 1
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
            if (key == null || valStr == null) return ShellExecutionResult("Usage: wrangler kv:key put <KEY> <VAL> [--ttl 3600]", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            repository.writeKvValue(accountId, nsId, key, valStr, expirationTtl = ttlVal).fold(
              onSuccess = {
                val ttlNote = ttlVal?.takeIf { it >= 60L }?.let { " (ttl=${it}s)" }.orEmpty()
                ShellExecutionResult("Wrote KV key '$key'$ttlNote", kind = TerminalEntryKind.WRANGLER)
              },
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
      "kv:bulk" -> {
        if (accountId == null) return ShellExecutionResult("Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        val action = args.getOrNull(1)?.lowercase()
        if (action != "put") {
          return ShellExecutionResult("Usage: wrangler kv:bulk put <entries.json> [--namespace-id ID]", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        val nsFlagIdx = args.indexOfFirst { it == "--namespace-id" || it == "-n" }
        val explicitNs = if (nsFlagIdx >= 0) args.getOrNull(nsFlagIdx + 1) else null
        val filePath =
          args.drop(2).filterIndexed { i, tok ->
            val origIdx = i + 2
            tok != "--namespace-id" && tok != "-n" && origIdx != nsFlagIdx + 1
          }.firstOrNull() ?: return ShellExecutionResult("Usage: wrangler kv:bulk put <entries.json> [--namespace-id ID]", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)

        val nsId =
          explicitNs
            ?: workspace?.bindings?.firstOrNull { it.type == "kv_namespace" && !it.namespaceId.isNullOrBlank() }?.namespaceId
            ?: repository.listKvNamespaces(accountId).getOrNull()?.firstOrNull()?.id
            ?: return ShellExecutionResult("No KV namespace ID found. Pass --namespace-id <ID>.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)

        val diskFile = resolvePath(session.currentDir, filePath, session)
        val rawJson =
          if (diskFile.exists()) diskFile.readText(Charsets.UTF_8)
          else workspace?.files?.firstOrNull { it.path == filePath }?.content
            ?: return ShellExecutionResult("File not found: $filePath", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)

        val itemsResult =
          runCatching {
            val parsed = JSONTokener(rawJson.trim()).nextValue()
            when (parsed) {
              is JSONArray ->
                (0 until parsed.length()).map { idx ->
                  val obj = parsed.getJSONObject(idx)
                  CfKvBulkItem(
                    key = obj.getString("key"),
                    value = obj.optString("value", ""),
                    expirationTtl = obj.optLong("expiration_ttl", 0L).takeIf { it >= 60L },
                  )
                }
              is JSONObject ->
                parsed.keys().asSequence().map { k ->
                  CfKvBulkItem(key = k, value = parsed.optString(k, ""))
                }.toList()
              else -> error("Expected JSON array or object")
            }
          }
        val items =
          itemsResult.getOrElse {
            return ShellExecutionResult("Invalid KV bulk JSON: ${it.localizedMessage}", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
          }
        repository.bulkPutKvEntries(accountId, nsId, items).fold(
          onSuccess = { ShellExecutionResult("Bulk imported ${items.size} keys into KV namespace ${nsId.take(8)}", kind = TerminalEntryKind.WRANGLER) },
          onFailure = { ShellExecutionResult(it.localizedMessage ?: "kv:bulk put failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
        )
      }
      "d1" -> {
        if (accountId == null) return ShellExecutionResult("Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        when (args.getOrNull(1)?.lowercase()) {
          "list" ->
            repository.listD1Databases(accountId).fold(
              onSuccess = { dbs ->
                val out = if (dbs.isEmpty()) "(no D1 databases)" else dbs.joinToString("\n") { "${it.uuid}  ${it.name}  tables=${it.numTables ?: "?"}" }
                ShellExecutionResult(out, kind = TerminalEntryKind.WRANGLER)
              },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "d1 list failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          "execute" -> {
            val dbNameOrId = args.getOrNull(2) ?: return ShellExecutionResult("Usage: wrangler d1 execute <DB_NAME|UUID> <SQL>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            val cmdFlagIdx = args.indexOfFirst { it == "--command" || it == "-c" }
            val sql =
              if (cmdFlagIdx >= 0) args.getOrNull(cmdFlagIdx + 1).orEmpty()
              else args.drop(3).joinToString(" ")
            if (sql.isBlank()) return ShellExecutionResult("Provide SQL query after database name or via --command", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            val dbs = repository.listD1Databases(accountId).getOrDefault(emptyList())
            val resolvedId = dbs.firstOrNull { it.uuid == dbNameOrId || it.name.equals(dbNameOrId, true) }?.uuid ?: dbNameOrId
            repository.queryD1Database(accountId, resolvedId, sql).fold(
              onSuccess = { results ->
                val arr = JSONArray()
                results.forEach { res ->
                  res.results.forEach { row -> arr.put(JSONObject(row)) }
                }
                val meta = results.firstOrNull()?.meta
                val summary = if (arr.length() == 0) "Query OK (changes=${meta?.changes ?: 0}, duration=${meta?.duration ?: 0.0}ms)" else arr.toString(2)
                ShellExecutionResult(summary, kind = TerminalEntryKind.WRANGLER)
              },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "d1 execute failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          }
          else -> ShellExecutionResult("Usage: wrangler d1 <list | execute <DB> <SQL>>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
      }
      "r2" -> {
        if (accountId == null) return ShellExecutionResult("Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        val noun = args.getOrNull(1)?.lowercase()
        val action = args.getOrNull(2)?.lowercase()
        if (noun != "bucket") {
          return ShellExecutionResult("Usage: wrangler r2 bucket <list|create|delete> [name]", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        when (action) {
          "list" ->
            repository.listR2Buckets(accountId).fold(
              onSuccess = { buckets ->
                ShellExecutionResult(if (buckets.isEmpty()) "(no R2 buckets)" else buckets.joinToString("\n") { "${it.name}  (${it.location ?: "auto"})" }, kind = TerminalEntryKind.WRANGLER)
              },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "r2 list failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          "create" -> {
            val name = args.getOrNull(3) ?: return ShellExecutionResult("Usage: wrangler r2 bucket create <NAME>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            repository.createR2Bucket(accountId, name).fold(
              onSuccess = { ShellExecutionResult("Created R2 bucket '${it.name}'", kind = TerminalEntryKind.WRANGLER) },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "r2 create failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          }
          "delete" -> {
            val name = args.getOrNull(3) ?: return ShellExecutionResult("Usage: wrangler r2 bucket delete <NAME>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            repository.deleteR2Bucket(accountId, name).fold(
              onSuccess = { ShellExecutionResult("Deleted R2 bucket '$name'", kind = TerminalEntryKind.WRANGLER) },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "r2 delete failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          }
          else -> ShellExecutionResult("Usage: wrangler r2 bucket <list|create|delete> [name]", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
      }
      "pages" -> {
        if (accountId == null) return ShellExecutionResult("Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        val noun = args.getOrNull(1)?.lowercase()
        val action = args.getOrNull(2)?.lowercase()
        when {
          noun == "project" && action == "list" ->
            repository.listPagesProjects(accountId).fold(
              onSuccess = { list -> ShellExecutionResult(if (list.isEmpty()) "(no Pages projects)" else list.joinToString("\n") { "${it.name}  https://${it.subdomain}" }, kind = TerminalEntryKind.WRANGLER) },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "pages project list failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          noun == "deployment" && action == "list" -> {
            val proj = args.getOrNull(3) ?: return ShellExecutionResult("Usage: wrangler pages deployment list <project>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            repository.listPagesDeployments(accountId, proj).fold(
              onSuccess = { deps -> ShellExecutionResult(if (deps.isEmpty()) "(no deployments)" else deps.joinToString("\n") { "${it.id.take(10)}  ${it.environment}  ${it.latestStage?.status ?: "?"}  ${it.url.orEmpty()}" }, kind = TerminalEntryKind.WRANGLER) },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "pages deployment list failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          }
          else -> ShellExecutionResult("Usage: wrangler pages <project list | deployment list <project>>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
      }
      "queues" -> {
        if (accountId == null) return ShellExecutionResult("Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        when (args.getOrNull(1)?.lowercase()) {
          "list" ->
            repository.listQueues(accountId).fold(
              onSuccess = { qs ->
                ShellExecutionResult(if (qs.isEmpty()) "(no Queues)" else qs.joinToString("\n") { "${it.queueId}  ${it.queueName}" }, kind = TerminalEntryKind.WRANGLER)
              },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "queues list failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          "create" -> {
            val name = args.getOrNull(2) ?: return ShellExecutionResult("Usage: wrangler queues create <NAME>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            repository.createQueue(accountId, name).fold(
              onSuccess = { ShellExecutionResult("Created Queue '${it.queueName}' (${it.queueId})", kind = TerminalEntryKind.WRANGLER) },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "queues create failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          }
          "delete" -> {
            val qId = args.getOrNull(2) ?: return ShellExecutionResult("Usage: wrangler queues delete <QUEUE_ID>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
            repository.deleteQueue(accountId, qId).fold(
              onSuccess = { ShellExecutionResult("Deleted Queue '$qId'", kind = TerminalEntryKind.WRANGLER) },
              onFailure = { ShellExecutionResult(it.localizedMessage ?: "queues delete failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
            )
          }
          else -> ShellExecutionResult("Usage: wrangler queues <list|create|delete>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
      }
      "durable-objects" -> {
        if (accountId == null) return ShellExecutionResult("Cloudflare account not connected.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        if (args.getOrNull(1)?.lowercase() != "list") {
          return ShellExecutionResult("Usage: wrangler durable-objects list", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        repository.listDurableObjects(accountId).fold(
          onSuccess = { dos ->
            ShellExecutionResult(if (dos.isEmpty()) "(no Durable Object namespaces)" else dos.joinToString("\n") { "${it.id}  ${it.name}  class=${it.className ?: "?"}  script=${it.script ?: "?"}" }, kind = TerminalEntryKind.WRANGLER)
          },
          onFailure = { ShellExecutionResult(it.localizedMessage ?: "durable-objects list failed", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR) },
        )
      }
      else -> ShellExecutionResult("Unknown wrangler subcommand '$sub'. Run 'help' for supported commands.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
    }
  }

  private suspend fun executeRealLinuxShell(
    command: String,
    stdin: String?,
    session: TerminalSessionState,
    onLiveOutput: (String) -> Unit,
  ): ShellExecutionResult =
    withContext(Dispatchers.IO) {
      session.interruptedByUser = false
      var process: Process? = null
      try {
        val shellBinary =
          listOf("/system/bin/sh", "/bin/sh", "sh").firstOrNull { candidate ->
            candidate == "sh" || File(candidate).exists()
          } ?: "sh"
        val pb = ProcessBuilder(shellBinary, "-c", command)
        pb.directory(session.currentDir)
        pb.redirectErrorStream(true)
        pb.environment().putAll(session.sessionEnv)

        process = pb.start()
        session.activeProcess = process
        val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
        session.activeStdinWriter = writer

        if (stdin != null) {
          writer.write(stdin)
          writer.flush()
          writer.close()
          session.activeStdinWriter = null
        }

        val maxOutputChars = 512 * 1024
        val collected = StringBuilder()
        var truncated = false
        val readerThread =
          Thread {
            runCatching {
              val reader = process.inputStream.bufferedReader(Charsets.UTF_8)
              val buf = CharArray(2048)
              var lastEmit = 0L
              while (true) {
                val n = reader.read(buf)
                if (n <= 0) break
                val remaining = maxOutputChars - collected.length
                if (remaining > 0) {
                  collected.appendRange(buf, 0, minOf(n, remaining))
                } else if (!truncated) {
                  truncated = true
                  collected.append("\n[output truncated]")
                }
                val now = System.currentTimeMillis()
                if (now - lastEmit >= 75L) {
                  lastEmit = now
                  onLiveOutput(collected.toString())
                }
              }
            }
          }
        readerThread.start()

        val completed =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.waitFor(120, TimeUnit.SECONDS)
          } else {
            val deadline = System.currentTimeMillis() + 120_000L
            var finished = false
            while (System.currentTimeMillis() < deadline) {
              try {
                process.exitValue()
                finished = true
                break
              } catch (_: IllegalThreadStateException) {
                Thread.sleep(50)
              }
            }
            finished
          }

        if (!completed) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.destroyForcibly()
          } else {
            process.destroy()
          }
          readerThread.join(1000)
          val partial = collected.toString().trimEnd()
          return@withContext ShellExecutionResult(
            output = (if (partial.isNotBlank()) "$partial\n" else "") + "Process timed out after 120s",
            isError = true,
            exitCode = 124,
            kind = TerminalEntryKind.ERROR,
          )
        }

        readerThread.join(1500)
        val text = collected.toString().trimEnd()
        val code = runCatching { process.exitValue() }.getOrDefault(if (session.interruptedByUser) 130 else 1)
        if (session.interruptedByUser) {
          return@withContext ShellExecutionResult(
            output = (if (text.isNotBlank()) "$text\n" else "") + "^C",
            isError = true,
            exitCode = 130,
            kind = TerminalEntryKind.ERROR,
          )
        }
        ShellExecutionResult(
          output = text.ifEmpty { if (code == 0) "" else "(exit $code)" },
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
      } finally {
        runCatching { session.activeStdinWriter?.close() }
        session.activeStdinWriter = null
        session.activeProcess = null
      }
    }

  private fun listWorkspaceProjects(workspace: WorkerWorkspace?): String {
    val dirs =
      rootWorkspacesDir.listFiles()
        ?.filter { it.isDirectory && !it.name.startsWith(".") }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()
    if (dirs.isEmpty()) return "(no projects in ${rootWorkspacesDir.absolutePath})"
    return dirs.joinToString("\n") { d ->
      val activeMark = if (workspace?.scriptName == d.name) "\u001B[32m*\u001B[0m " else "  "
      val files = d.walkTopDown().count { it.isFile }
      "$activeMark\u001B[1;36m${d.name}\u001B[0m  ($files files)  ${d.absolutePath}"
    }
  }

  private fun buildSysInfoReport(session: TerminalSessionState, workspace: WorkerWorkspace?): String {
    val runtime = Runtime.getRuntime()
    val cores = runtime.availableProcessors()
    val jvmUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val jvmMaxMb = runtime.maxMemory() / (1024 * 1024)
    val arch = System.getProperty("os.arch") ?: "arm64"
    val kernel = System.getProperty("os.version") ?: "linux"
    val stat = runCatching { StatFs(rootWorkspacesDir.absolutePath) }.getOrNull()
    val freeMb = stat?.let { (it.availableBlocksLong * it.blockSizeLong) / (1024L * 1024L) }
    val totalMb = stat?.let { (it.blockCountLong * it.blockSizeLong) / (1024L * 1024L) }
    val memInfo = readProcMemInfoMb()
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    return buildString {
      appendLine("\u001B[1;36m  ___    _          ___         _\u001B[0m")
      appendLine("\u001B[1;36m | __|__| |__ _ ___/ __|___  __| |___\u001B[0m   \u001B[1;32m${session.sessionEnv["USER"]}@edgecode-linux\u001B[0m")
      appendLine("\u001B[1;36m | _|/ _` / _` / -_) (__/ _ \\/ _` / -_)\u001B[0m  ─────────────────────")
      appendLine("\u001B[1;36m |___\\__,_\\__, \\___|\\___\\___/\\__,_\\___|\u001B[0m  \u001B[33mOS:\u001B[0m Android ${Build.VERSION.RELEASE ?: "Linux"} (API ${Build.VERSION.SDK_INT})")
      appendLine("          |___/                           \u001B[33mKernel:\u001B[0m $kernel ($arch)")
      appendLine("  \u001B[33mShell:\u001B[0m EdgeCode sh + Toybox             \u001B[33mCPU:\u001B[0m $cores cores")
      if (memInfo != null) {
        appendLine("  \u001B[33mRAM:\u001B[0m ${memInfo.first} MB avail / ${memInfo.second} MB total (App ${jvmUsedMb}/${jvmMaxMb} MB)")
      } else {
        appendLine("  \u001B[33mHeap:\u001B[0m ${jvmUsedMb} MB / ${jvmMaxMb} MB")
      }
      if (freeMb != null && totalMb != null) {
        appendLine("  \u001B[33mDisk:\u001B[0m ${freeMb} MB free / ${totalMb} MB total")
      }
      appendLine("  \u001B[33mWorkspace:\u001B[0m ${workspace?.scriptName ?: "(standalone)"} @ ${session.currentDir.absolutePath}")
      append("  \u001B[33mTime:\u001B[0m $now")
    }
  }

  private fun readProcMemInfoMb(): Pair<Long, Long>? =
    runCatching {
      val f = File("/proc/meminfo")
      if (!f.exists() || !f.canRead()) return null
      var totalKb = 0L
      var availKb = 0L
      f.forEachLine { line ->
        when {
          line.startsWith("MemTotal:") -> totalKb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
          line.startsWith("MemAvailable:") -> availKb = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
        }
      }
      if (totalKb > 0L) (availKb / 1024L) to (totalKb / 1024L) else null
    }.getOrNull()

  private fun buildToolboxInventory(): String {
    val systemBins =
      File("/system/bin")
        .listFiles()
        ?.filter { it.canExecute() }
        ?.map { it.name }
        ?.sorted()
        .orEmpty()
    val sampleSys = systemBins.take(64).joinToString(" ")
    val moreCount = (systemBins.size - 64).coerceAtLeast(0)
    return buildString {
      appendLine("\u001B[1;36mEdgeCode Built-in Linux Utilities:\u001B[0m")
      appendLine("  Shell    : cd, pwd, export, unset, env, alias, unalias, echo, printf, which, sleep, history, clear")
      appendLine("  Files    : ls, cat, head, tail, touch, mkdir, rm, cp, mv, chmod, find, grep, wc, sort, uniq, cut, sed, awk, tree")
      appendLine("  Archives : zip, unzip, tar, gzip, gunzip, base64, sha256sum, sha1sum, md5sum, sha512sum, uuidgen")
      appendLine("  Network  : curl (-o/-O/-i/-v), wget, nslookup, dig, ping, tcping, nc")
      appendLine("  JSON/Math: jq (-r/-c), calc")
      appendLine("  Code Run : run [file], node/js [-e code|file], ts/tsx [file], npm <run|test|start>, sh <file.sh>")
      appendLine("  Cloud/Git: wrangler, git")
      appendLine("  IDE      : nano/vi/edit/open <file>, projects, ide <project>, zipimport <file.zip>, sysinfo")
      if (systemBins.isNotEmpty()) {
        appendLine()
        appendLine("\u001B[1;32mSystem Binaries (/system/bin — ${systemBins.size} tools):\u001B[0m")
        append("  $sampleSys")
        if (moreCount > 0) append(" ... (+$moreCount more)")
      }
    }.trimEnd()
  }

  private fun buildHelpText(): String =
    """
    EdgeCode Terminal
    -----------------
    Code Runner:
      run [file] [--fetch]                Run entry/active file or script
      node -e "console.log(1+1)" | file.js
      ts file.ts | npm run <script> | npm test | sh script.sh

    Shell & Files:
      cd, pwd, ls -la, cat, grep -rn, find, nano <file>, zip, unzip
      curl -i <url> | wget <url> | jq -r . | calc 2^10

    Cloudflare & GitHub:
      wrangler whoami | list | deploy | tail | d1 | r2 | kv:key | pages
      git status | diff | log | add | commit | push | pull | clone
    """.trimIndent()

  fun autocomplete(input: String, workspace: WorkerWorkspace?, sessionId: String? = null): String {
    val session = sessionFor(sessionId)
    val trimmed = input.trimStart()
    val parts = trimmed.split(Regex("\\s+"))
    val last = parts.lastOrNull().orEmpty()
    val candidates = buildList {
      addAll(
        listOf(
          "run ",
          "node ",
          "node -e \"",
          "ts ",
          "npm run ",
          "npm test",
          "sh ",
          "sysinfo",
          "pkg",
          "tree -L 2",
          "ls -la",
          "cd ",
          "cat ",
          "nano ",
          "edit ",
          "grep -rn ",
          "find . -maxdepth 3",
          "zip -r archive.zip ",
          "unzip ",
          "zipimport ",
          "projects",
          "ide ",
          "wget ",
          "curl -i ",
          "curl -v ",
          "nslookup ",
          "tcping ",
          "sha256sum ",
          "base64 ",
          "jq -r .",
          "calc ",
          "alias ",
          "export ",
          "wrangler deploy",
          "wrangler dev",
          "wrangler tail",
          "wrangler list",
          "wrangler versions",
          "wrangler whoami",
          "wrangler deployments",
          "wrangler secret list",
          "wrangler kv:namespace list",
          "wrangler d1 list",
          "wrangler r2 bucket list",
          "wrangler pages project list",
          "wrangler queues list",
          "wrangler durable-objects list",
          "git status",
          "git diff",
          "git add .",
          "git commit -m \"",
          "git push",
          "git pull",
          "git log",
          "git branch",
          "git clone ",
          "uname -a",
          "clear",
          "help",
        ),
      )
      session.currentDir.listFiles()
        ?.sortedBy { it.name.lowercase() }
        ?.take(50)
        ?.forEach { f ->
          add(if (f.isDirectory) "${f.name}/" else f.name)
        }
      workspace?.files?.forEach { add(it.path) }
    }
    val match = candidates.firstOrNull { it.startsWith(last, ignoreCase = true) && it != last }
    return if (match != null && parts.isNotEmpty()) {
      val prefix = input.dropLast(last.length)
      "$prefix$match"
    } else input
  }
}
