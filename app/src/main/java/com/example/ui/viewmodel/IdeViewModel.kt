package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.CloudflareOAuthManager
import com.example.data.auth.GitHubDeviceCodeResponse
import com.example.data.auth.GitHubOAuthManager
import com.example.data.local.DeviceFileHelper
import com.example.data.local.PickedDeviceFile
import com.example.data.local.RawPickedAsset
import com.example.data.remote.CfAccount
import com.example.data.remote.CfD1Database
import com.example.data.remote.CfD1QueryResult
import com.example.data.remote.CfDurableObjectNamespace
import com.example.data.remote.CfKvKey
import com.example.data.remote.CfKvNamespace
import com.example.data.remote.CfPagesBuildLogsResult
import com.example.data.remote.CfPagesDeployment
import com.example.data.remote.CfPagesDomain
import com.example.data.remote.CfPagesEnvVarValue
import com.example.data.remote.CfPagesLogEntry
import com.example.data.remote.CfPagesProject
import com.example.data.remote.CfQueue
import com.example.data.remote.CfR2Bucket
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.CfWorkerScript
import com.example.data.remote.CfZone
import com.example.data.remote.CloudflareClientFactory
import com.example.data.remote.DiagnosticItem
import com.example.data.remote.DiffType
import com.example.data.remote.GitChangedFile
import com.example.data.remote.GitFileChangeStatus
import com.example.data.remote.GitHubBranch
import com.example.data.remote.GitHubCommitDetailResponse
import com.example.data.remote.GitHubCommitItem
import com.example.data.remote.GitHubDiffFile
import com.example.data.remote.GitHubIssue
import com.example.data.remote.GitHubPullRequest
import com.example.data.remote.GitHubRepo
import com.example.data.remote.GitHubTreeItem
import com.example.data.remote.GitHubUser
import com.example.data.remote.GitHubWorkflow
import com.example.data.remote.GitHubWorkflowRun
import com.example.data.remote.HttpTestResult
import com.example.data.remote.LinkedGitRepoState
import com.example.data.remote.LocalGitCommit
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.PagesDirectUploadProgress
import com.example.data.remote.PagesPreparedAsset
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import com.example.data.repository.ActiveTailSession
import com.example.data.repository.CloudflareRepository
import com.example.data.repository.GitHubRepository
import com.example.data.repository.WorkerTemplate
import com.example.data.security.AuthPreferencesStore
import com.example.data.security.EditorSettings
import com.example.data.security.GitHubAuthMethod
import com.example.data.security.StoredCredentials
import com.example.data.security.StoredGitHubCredentials
import com.example.editor.CodeAnalyzer
import com.example.editor.CodeFormatter
import com.example.runner.LocalRunResult
import com.example.runner.LocalScriptRunner
import com.example.terminal.LinuxTerminalEngine
import com.example.terminal.ShellExecutionResult
import com.example.terminal.TerminalEntry
import com.example.terminal.TerminalEntryKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SidebarSection {
  EXPLORER,
  SEARCH,
  BINDINGS,
  PAGES,
  GITHUB,
  SNIPPETS,
  SETTINGS,
}

enum class BottomPanelTab {
  RUN_PREVIEW,
  TERMINAL,
  PROBLEMS,
  TAIL_LOGS,
  CONSOLE,
}

data class ConsoleLogEntry(
  val timestamp: String,
  val level: String,
  val message: String,
)

data class SearchMatchItem(
  val filePath: String,
  val line: Int,
  val preview: String,
)

data class IdeUiState(
  val isBootstrapping: Boolean = true,
  val credentials: StoredCredentials? = null,
  val isLocalScratchpad: Boolean = false,
  val isGitHubIdeOnlyMode: Boolean = false,
  val isAuthenticating: Boolean = false,
  val isWaitingOAuthCallback: Boolean = false,
  val authError: String? = null,
  val accounts: List<CfAccount> = emptyList(),
  val selectedAccount: CfAccount? = null,
  val workers: List<CfWorkerScript> = emptyList(),
  val isLoadingWorkers: Boolean = false,
  val isOpeningWorker: Boolean = false,
  val workspace: WorkerWorkspace? = null,
  val openTabPaths: List<String> = emptyList(),
  val activeFilePath: String? = null,
  val tabNavHistory: List<String> = emptyList(),
  val tabNavIndex: Int = -1,
  val editorFieldValues: Map<String, TextFieldValue> = emptyMap(),
  val diagnostics: List<DiagnosticItem> = emptyList(),
  val isDeploying: Boolean = false,
  val deployBannerMessage: String? = null,
  val isDeployError: Boolean = false,
  val sidebarSection: SidebarSection = SidebarSection.EXPLORER,
  val showBottomPanel: Boolean = false,
  val isBottomPanelExpanded: Boolean = false,
  val activeBottomTab: BottomPanelTab = BottomPanelTab.RUN_PREVIEW,
  val showFindBar: Boolean = false,
  val findQuery: String = "",
  val replaceQuery: String = "",
  val searchQuery: String = "",
  val editorSettings: EditorSettings = EditorSettings(),
  // Local Sandbox Script Runner (run without deploying or saving)
  val isRunningLocalScript: Boolean = false,
  val localRunResult: LocalRunResult? = null,
  val runRequestMethod: String = "GET",
  val runRequestPath: String = "/",
  val runRequestHeadersJson: String = "{\"x-edge-test\": \"1\"}",
  val runRequestBody: String = "",
  // Cloudflare Workers Infra: KV, D1, R2, DO, Queues, Zones
  val kvNamespaces: List<CfKvNamespace> = emptyList(),
  val selectedKvNamespace: CfKvNamespace? = null,
  val kvKeys: List<CfKvKey> = emptyList(),
  val selectedKvKeyValue: Pair<String, String>? = null,
  val isKvBusy: Boolean = false,
  val zones: List<CfZone> = emptyList(),
  val d1Databases: List<CfD1Database> = emptyList(),
  val selectedD1Database: CfD1Database? = null,
  val d1QueryResults: List<CfD1QueryResult> = emptyList(),
  val r2Buckets: List<CfR2Bucket> = emptyList(),
  val durableObjects: List<CfDurableObjectNamespace> = emptyList(),
  val queues: List<CfQueue> = emptyList(),
  val isInfraBusy: Boolean = false,
  // Cloudflare Pages
  val pagesProjects: List<CfPagesProject> = emptyList(),
  val selectedPagesProject: CfPagesProject? = null,
  val pagesDeployments: List<CfPagesDeployment> = emptyList(),
  val pagesBuildLogs: List<CfPagesLogEntry> = emptyList(),
  val pagesDomains: List<CfPagesDomain> = emptyList(),
  val isPagesBusy: Boolean = false,
  val pagesDirectUploadSourceLabel: String? = null,
  val pagesIsZipSource: Boolean = false,
  val pagesStripZipRootFolder: Boolean = true,
  val pagesStagedUnstrippedZipAssets: List<RawPickedAsset> = emptyList(),
  val pagesStagedPreparedAssets: List<PagesPreparedAsset> = emptyList(),
  val pagesDirectUploadProgress: PagesDirectUploadProgress? = null,
  val isPagesDirectUploading: Boolean = false,
  val pagesLastDirectDeployment: CfPagesDeployment? = null,
  // GitHub & Git Integration
  val gitHubCreds: StoredGitHubCredentials? = null,
  val gitHubUser: GitHubUser? = null,
  val gitHubDeviceSession: GitHubDeviceCodeResponse? = null,
  val isWaitingGitHubOAuth: Boolean = false,
  val gitHubRateLimitLabel: String? = null,
  val gitHubRepos: List<GitHubRepo> = emptyList(),
  val selectedGitHubRepo: GitHubRepo? = null,
  val gitHubBranches: List<GitHubBranch> = emptyList(),
  val selectedGitHubBranch: String = "main",
  val gitHubTreeFiles: List<GitHubTreeItem> = emptyList(),
  val gitHubCommits: List<GitHubCommitItem> = emptyList(),
  val selectedCommitDetail: GitHubCommitDetailResponse? = null,
  val gitHubWorkflows: List<GitHubWorkflow> = emptyList(),
  val gitHubWorkflowRuns: List<GitHubWorkflowRun> = emptyList(),
  val gitHubPullRequests: List<GitHubPullRequest> = emptyList(),
  val selectedPrNumber: Int? = null,
  val selectedPrFiles: List<GitHubDiffFile> = emptyList(),
  val gitHubIssues: List<GitHubIssue> = emptyList(),
  val linkedGitRepo: LinkedGitRepoState? = null,
  val gitWorkingBaseline: Map<String, String> = emptyMap(),
  val gitUnstagedExcludedPaths: Set<String> = emptySet(),
  val localGitCommits: List<LocalGitCommit> = emptyList(),
  val gitCommitMessageInput: String = "",
  val gitPushStatusMessage: String? = null,
  val isGitHubBusy: Boolean = false,
  val gitHubError: String? = null,
  // API Test & Linux Terminal
  val isRunningHttpTest: Boolean = false,
  val httpTestResult: HttpTestResult? = null,
  val terminalEntries: List<TerminalEntry> = emptyList(),
  val terminalInput: String = "",
  val terminalPromptPath: String = "~/scratch",
  val isTerminalBusy: Boolean = false,
  val terminalHistory: List<String> = emptyList(),
  val terminalHistoryCursor: Int = -1,
  val isTailRunning: Boolean = false,
  val tailLogs: List<ConsoleLogEntry> = emptyList(),
  val consoleLogs: List<ConsoleLogEntry> = emptyList(),
  val statusMessage: String? = null,
) {
  val activeFile: WorkerFile?
    get() = workspace?.files?.firstOrNull { it.path == activeFilePath }

  val hasUnsavedChanges: Boolean
    get() =
      workspace?.let { ws ->
        ws.hasModuleSetChanged || ws.files.any { it.isDirty }
      } == true

  val canNavigateTabBack: Boolean
    get() = tabNavIndex > 0

  val canNavigateTabForward: Boolean
    get() = tabNavIndex >= 0 && tabNavIndex < tabNavHistory.lastIndex

  val activeTextFieldValue: TextFieldValue
    get() =
      activeFilePath?.let { path ->
        editorFieldValues[path]
          ?: activeFile?.let { TextFieldValue(it.content) }
      } ?: TextFieldValue("")

  val cursorLineAndCol: Pair<Int, Int>
    get() {
      val tfv = activeTextFieldValue
      val pos = tfv.selection.start.coerceIn(0, tfv.text.length)
      val before = tfv.text.substring(0, pos)
      val line = before.count { it == '\n' } + 1
      val col = pos - (before.lastIndexOf('\n') + 1) + 1
      return line to col
    }

  val gitChangedFiles: List<GitChangedFile>
    get() {
      val ws = workspace ?: return emptyList()
      val currentMap = ws.files.filterNot { it.isVirtualReadOnly }.associate { it.path to it.content }
      val baseline = gitWorkingBaseline
      val allPaths = (currentMap.keys + baseline.keys).toSortedSet()
      return allPaths.mapNotNull { path ->
        val cur = currentMap[path]
        val base = baseline[path]
        when {
          cur != null && base == null ->
            GitChangedFile(
              path = path,
              status = GitFileChangeStatus.ADDED,
              isStaged = path !in gitUnstagedExcludedPaths,
              currentContent = cur,
              baseContent = "",
            )
          cur == null && base != null ->
            GitChangedFile(
              path = path,
              status = GitFileChangeStatus.DELETED,
              isStaged = path !in gitUnstagedExcludedPaths,
              currentContent = "",
              baseContent = base,
            )
          cur != null && base != null && cur != base ->
            GitChangedFile(
              path = path,
              status = GitFileChangeStatus.MODIFIED,
              isStaged = path !in gitUnstagedExcludedPaths,
              currentContent = cur,
              baseContent = base,
            )
          else -> null
        }
      }
    }
}

class IdeViewModel(application: Application) : AndroidViewModel(application) {
  private val prefsStore = AuthPreferencesStore(application)
  private val clientFactory = CloudflareClientFactory(prefsStore)
  private val repository = CloudflareRepository(prefsStore, clientFactory)
  private val gitHubRepository = GitHubRepository(prefsStore, clientFactory.moshi)
  private val terminalEngine = LinuxTerminalEngine(application, repository)
  private val localScriptRunner = LocalScriptRunner(application)
  private val terminalIdGen = AtomicLong(1L)

  private val _uiState = MutableStateFlow(IdeUiState())
  val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

  private var oauthJob: Job? = null
  private var gitHubOAuthJob: Job? = null
  private var activeTailSession: ActiveTailSession? = null
  private val undoStacks = mutableMapOf<String, ArrayDeque<TextFieldValue>>()
  private val redoStacks = mutableMapOf<String, ArrayDeque<TextFieldValue>>()

  init {
    viewModelScope.launch {
      prefsStore.editorSettingsFlow.collect { settings ->
        _uiState.update { it.copy(editorSettings = settings) }
      }
    }

    viewModelScope.launch {
      prefsStore.gitHubCredentialsFlow.collect { ghCreds ->
        _uiState.update { it.copy(gitHubCreds = ghCreds) }
        if (ghCreds != null && _uiState.value.gitHubRepos.isEmpty()) {
          refreshGitHubReposInternal(ghCreds.token)
        }
      }
    }

    viewModelScope.launch {
      val creds = prefsStore.credentialsFlow.first()
      if (creds != null) {
        _uiState.update { it.copy(credentials = creds, isBootstrapping = false) }
        appendLog("INFO", "Restored encrypted Cloudflare session (${creds.method.name})")
        bootstrapAccountAndWorkers(creds)
      } else {
        _uiState.update { it.copy(isBootstrapping = false) }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    val accountId = _uiState.value.selectedAccount?.id
    val scriptName = _uiState.value.workspace?.scriptName
    val tail = activeTailSession
    if (accountId != null && scriptName != null && tail != null) {
      viewModelScope.launch { repository.stopLiveTail(accountId, scriptName, tail) }
    }
  }

  private fun bootstrapAccountAndWorkers(creds: StoredCredentials) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingWorkers = true) }
      repository
        .loadAccounts()
        .onSuccess { accounts ->
          val chosen =
            accounts.firstOrNull { it.id == creds.selectedAccountId } ?: accounts.firstOrNull()
          _uiState.update { it.copy(accounts = accounts, selectedAccount = chosen) }
          if (chosen != null) {
            prefsStore.saveSelectedAccount(chosen.id, chosen.name)
            refreshWorkersInternal(chosen.id)
          } else {
            _uiState.update { it.copy(isLoadingWorkers = false) }
          }
        }
        .onFailure { err ->
          appendLog("ERROR", err.localizedMessage ?: "Failed to verify saved Cloudflare session")
          _uiState.update {
            it.copy(
              isLoadingWorkers = false,
              statusMessage = err.localizedMessage ?: "Session check failed",
            )
          }
        }
    }
  }

  fun startOAuthSignIn(onLaunchBrowser: (String) -> Unit) {
    oauthJob?.cancel()
    val session = CloudflareOAuthManager.createPkceSession()
    _uiState.update {
      it.copy(
        isAuthenticating = true,
        isWaitingOAuthCallback = true,
        authError = null,
      )
    }
    appendLog("INFO", "Starting Cloudflare OAuth2 PKCE flow on localhost:${CloudflareOAuthManager.REDIRECT_PORT}")
    onLaunchBrowser(session.authorizationUrl)

    oauthJob =
      viewModelScope.launch {
        repository
          .completeOAuthSession(session)
          .onSuccess { accounts ->
            val creds = prefsStore.getCurrentCredentials()
            val chosen = accounts.firstOrNull()
            _uiState.update {
              it.copy(
                credentials = creds,
                isLocalScratchpad = false,
                accounts = accounts,
                selectedAccount = chosen,
                isAuthenticating = false,
                isWaitingOAuthCallback = false,
                authError = null,
              )
            }
            appendLog("INFO", "Cloudflare OAuth2 authorized (${accounts.size} account(s))")
            chosen?.let { refreshWorkersInternal(it.id) }
          }
          .onFailure { err ->
            _uiState.update {
              it.copy(
                isAuthenticating = false,
                isWaitingOAuthCallback = false,
                authError = err.localizedMessage ?: "Cloudflare OAuth authorization failed",
              )
            }
            appendLog("ERROR", err.localizedMessage ?: "OAuth failed")
          }
      }
  }

  fun cancelOAuthSignIn() {
    oauthJob?.cancel()
    oauthJob = null
    _uiState.update {
      it.copy(isAuthenticating = false, isWaitingOAuthCallback = false)
    }
  }

  fun authorizeWithToken(token: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isAuthenticating = true, authError = null) }
      repository
        .authorizeWithApiToken(token)
        .onSuccess { accounts ->
          val creds = prefsStore.getCurrentCredentials()
          val chosen = accounts.firstOrNull()
          _uiState.update {
            it.copy(
              credentials = creds,
              isLocalScratchpad = false,
              accounts = accounts,
              selectedAccount = chosen,
              isAuthenticating = false,
              authError = null,
            )
          }
          appendLog("INFO", "Authorized via Cloudflare Scoped API Token")
          chosen?.let { refreshWorkersInternal(it.id) }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isAuthenticating = false,
              authError = err.localizedMessage ?: "Token verification failed",
            )
          }
        }
    }
  }

  fun authorizeWithGlobalKey(email: String, apiKey: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isAuthenticating = true, authError = null) }
      repository
        .authorizeWithGlobalKey(email, apiKey)
        .onSuccess { accounts ->
          val creds = prefsStore.getCurrentCredentials()
          val chosen = accounts.firstOrNull()
          _uiState.update {
            it.copy(
              credentials = creds,
              isLocalScratchpad = false,
              accounts = accounts,
              selectedAccount = chosen,
              isAuthenticating = false,
              authError = null,
            )
          }
          appendLog("INFO", "Authorized via Cloudflare Global API Key")
          chosen?.let { refreshWorkersInternal(it.id) }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isAuthenticating = false,
              authError = err.localizedMessage ?: "Global Key authorization failed",
            )
          }
        }
    }
  }

  fun signOut() {
    oauthJob?.cancel()
    stopTailIfRunning()
    viewModelScope.launch {
      prefsStore.clearCredentials()
      undoStacks.clear()
      redoStacks.clear()
      _uiState.value =
        IdeUiState(
          isBootstrapping = false,
          editorSettings = _uiState.value.editorSettings,
          gitHubCreds = _uiState.value.gitHubCreds,
          gitHubUser = _uiState.value.gitHubUser,
          gitHubRateLimitLabel = _uiState.value.gitHubRateLimitLabel,
          gitHubRepos = _uiState.value.gitHubRepos,
        )
    }
  }

  fun enterLocalScratchpad(template: WorkerTemplate = WorkerTemplate.HELLO_FETCH) {
    undoStacks.clear()
    redoStacks.clear()
    val corsCode =
      """export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization"
};
"""
    val indexCode =
      if (template == WorkerTemplate.HELLO_FETCH) {
        """import { corsHeaders } from "./utils/cors.js";

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    return new Response(
      JSON.stringify({
        ok: true,
        worker: "local-worker",
        path: url.pathname,
        colo: request.cf?.colo ?? "EDGE"
      }, null, 2),
      {
        headers: {
          "content-type": "application/json;charset=UTF-8",
          ...corsHeaders
        }
      }
    );
  }
};
"""
      } else {
        template.code
      }
    val wranglerJson =
      """{
  "name": "local-worker",
  "main": "src/index.js",
  "compatibility_date": "2024-11-01",
  "workers_dev": true
}
"""
    val indexFile =
      WorkerFile(
        path = "src/index.js",
        content = indexCode,
        originalContent = indexCode,
        contentType = "application/javascript+module",
        isEntryPoint = true,
      )
    val corsFile =
      WorkerFile(
        path = "src/utils/cors.js",
        content = corsCode,
        originalContent = corsCode,
        contentType = "application/javascript+module",
        isEntryPoint = false,
      )
    val wranglerFile =
      WorkerFile(
        path = "wrangler.jsonc",
        content = wranglerJson,
        originalContent = wranglerJson,
        contentType = "application/json",
        isEntryPoint = false,
        isVirtualReadOnly = true,
      )
    val files = listOf(indexFile, corsFile, wranglerFile)
    val ws =
      WorkerWorkspace(
        scriptName = "local-worker",
        isEsModuleFormat = true,
        entryModulePath = "src/index.js",
        compatibilityDate = "2024-11-01",
        files = files,
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = false,
        accountSubdomain = "",
      )
    val diags = files.flatMap { CodeAnalyzer.analyzeFile(it) }
    terminalEngine.syncWorkspaceToDisk(ws)
    _uiState.update {
      it.copy(
        isLocalScratchpad = true,
        authError = null,
        workspace = ws,
        openTabPaths = listOf("src/index.js", "src/utils/cors.js"),
        activeFilePath = "src/index.js",
        tabNavHistory = listOf("src/index.js"),
        tabNavIndex = 0,
        editorFieldValues =
          mapOf(
            "src/index.js" to TextFieldValue(indexCode),
            "src/utils/cors.js" to TextFieldValue(corsCode),
            "wrangler.jsonc" to TextFieldValue(wranglerJson),
          ),
        diagnostics = diags,
        terminalPromptPath = terminalEngine.currentPromptPath(ws),
      )
    }
    appendTerminalWelcome(ws)
    appendLog("INFO", "Opened Local Worker Scratchpad (no Cloudflare sign-in)")
  }

  fun exitScratchpadToAuth() {
    _uiState.update {
      it.copy(
        isLocalScratchpad = false,
        isGitHubIdeOnlyMode = false,
        authError = null,
      )
    }
  }

  fun enterGitHubIdeMode() {
    _uiState.update { state ->
      state.copy(
        isGitHubIdeOnlyMode = true,
        isLocalScratchpad = state.credentials == null,
        sidebarSection = SidebarSection.GITHUB,
        authError = null,
      )
    }
    appendLog("INFO", "Entered GitHub IDE workspace")
  }

  fun selectAccount(account: CfAccount) {
    stopTailIfRunning()
    viewModelScope.launch {
      prefsStore.saveSelectedAccount(account.id, account.name)
      _uiState.update {
        it.copy(
          selectedAccount = account,
          workspace = null,
          openTabPaths = emptyList(),
          activeFilePath = null,
          kvNamespaces = emptyList(),
          selectedKvNamespace = null,
          kvKeys = emptyList(),
          zones = emptyList(),
        )
      }
      refreshWorkersInternal(account.id)
    }
  }

  fun refreshWorkers() {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch { refreshWorkersInternal(accountId) }
  }

  private suspend fun refreshWorkersInternal(accountId: String) {
    _uiState.update { it.copy(isLoadingWorkers = true) }
    repository
      .loadWorkers(accountId)
      .onSuccess { list ->
        _uiState.update { it.copy(workers = list, isLoadingWorkers = false) }
        appendLog("INFO", "Loaded ${list.size} Worker(s) from Cloudflare")
      }
      .onFailure { err ->
        _uiState.update {
          it.copy(
            isLoadingWorkers = false,
            statusMessage = err.localizedMessage ?: "Failed to load Workers",
          )
        }
        appendLog("ERROR", err.localizedMessage ?: "Failed to load Workers")
      }
  }

  fun openWorker(script: CfWorkerScript) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    stopTailIfRunning()
    viewModelScope.launch {
      _uiState.update { it.copy(isOpeningWorker = true, statusMessage = "Fetching ${script.id}…") }
      repository
        .openWorkerWorkspace(accountId, script)
        .onSuccess { ws ->
          undoStacks.clear()
          redoStacks.clear()
          val initialFields = ws.files.associate { it.path to TextFieldValue(it.content) }
          val entryPath = ws.entryModulePath
          val diags = ws.files.flatMap { CodeAnalyzer.analyzeFile(it) }
          terminalEngine.syncWorkspaceToDisk(ws)
          val baselineMap = ws.files.filterNot { it.isVirtualReadOnly }.associate { it.path to it.content }
          _uiState.update {
            it.copy(
              workspace = ws,
              openTabPaths = listOf(entryPath),
              activeFilePath = entryPath,
              tabNavHistory = listOf(entryPath),
              tabNavIndex = 0,
              editorFieldValues = initialFields,
              diagnostics = diags,
              isOpeningWorker = false,
              statusMessage = null,
              localRunResult = null,
              httpTestResult = null,
              tailLogs = emptyList(),
              linkedGitRepo = null,
              gitWorkingBaseline = baselineMap,
              gitUnstagedExcludedPaths = emptySet(),
              localGitCommits = emptyList(),
              gitPushStatusMessage = null,
              terminalPromptPath = terminalEngine.currentPromptPath(ws),
            )
          }
          appendTerminalWelcome(ws)
          appendLog("INFO", "Opened Worker '${ws.scriptName}' (${ws.files.size - 1} module file(s))")
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isOpeningWorker = false,
              statusMessage = err.localizedMessage ?: "Could not open Worker",
            )
          }
          appendLog("ERROR", err.localizedMessage ?: "Failed to open Worker '${script.id}'")
        }
    }
  }

  fun createNewWorker(name: String, template: WorkerTemplate) {
    if (_uiState.value.isLocalScratchpad) {
      val cleanName = name.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-").ifBlank { "local-worker" }
      val indexFile =
        WorkerFile(
          path = "index.js",
          content = template.code,
          originalContent = template.code,
          contentType = "application/javascript+module",
          isEntryPoint = true,
        )
      val ws =
        WorkerWorkspace(
          scriptName = cleanName,
          isEsModuleFormat = true,
          entryModulePath = "index.js",
          compatibilityDate = "2024-11-01",
          files = listOf(indexFile),
          bindings = emptyList(),
          routes = emptyList(),
          workersDevEnabled = false,
          accountSubdomain = "",
        )
      _uiState.update {
        it.copy(
          workspace = ws,
          openTabPaths = listOf("index.js"),
          activeFilePath = "index.js",
          editorFieldValues = mapOf("index.js" to TextFieldValue(template.code)),
          diagnostics = CodeAnalyzer.analyzeFile(indexFile),
          statusMessage = "Created local scratchpad '$cleanName'",
        )
      }
      return
    }
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isDeploying = true, statusMessage = "Creating Worker '$name'…") }
      repository
        .createNewWorker(accountId, name, template)
        .onSuccess { created ->
          appendLog("DEPLOY", "Created and published Worker '${created.id}'")
          refreshWorkersInternal(accountId)
          _uiState.update { it.copy(isDeploying = false, statusMessage = null) }
          openWorker(created)
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isDeploying = false,
              deployBannerMessage = err.localizedMessage ?: "Create Worker failed",
              isDeployError = true,
            )
          }
          appendLog("ERROR", err.localizedMessage ?: "Create Worker failed")
        }
    }
  }

  fun createNewWorkerFromUris(
    name: String,
    uris: List<Uri>,
    folderPrefix: String = "",
  ) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      val picked = DeviceFileHelper.readFiles(getApplication(), uris)
      if (picked.isEmpty()) {
        _uiState.update { it.copy(statusMessage = "Could not read selected file(s)") }
        return@launch
      }
      val normalizedPrefix = folderPrefix.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }
      val workerFiles =
        picked.mapIndexed { idx, f ->
          val path = "$normalizedPrefix${f.fileName}"
          val isEntry =
            idx == 0 && (path.endsWith(".js", true) || path.endsWith(".mjs", true)) ||
              f.fileName.equals("index.js", true) ||
              f.fileName.equals("index.mjs", true)
          WorkerFile(
            path = path,
            content = f.content,
            originalContent = f.content,
            contentType = MultipartWorkerParser.inferModuleMimeType(path),
            isEntryPoint = isEntry,
          )
        }
      val resolvedEntry =
        workerFiles.firstOrNull { it.isEntryPoint }?.path ?: workerFiles.first().path
      val finalFiles = workerFiles.map { it.copy(isEntryPoint = it.path == resolvedEntry) }

      if (_uiState.value.isLocalScratchpad) {
        val cleanName = name.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-").ifBlank { "uploaded-worker" }
        val ws =
          WorkerWorkspace(
            scriptName = cleanName,
            isEsModuleFormat = true,
            entryModulePath = resolvedEntry,
            compatibilityDate = "2024-11-01",
            files = finalFiles,
            bindings = emptyList(),
            routes = emptyList(),
            workersDevEnabled = false,
            accountSubdomain = "",
          )
        _uiState.update {
          it.copy(
            workspace = ws,
            openTabPaths = finalFiles.map { f -> f.path },
            activeFilePath = resolvedEntry,
            editorFieldValues = finalFiles.associate { f -> f.path to TextFieldValue(f.content) },
            diagnostics = finalFiles.flatMap { f -> CodeAnalyzer.analyzeFile(f) },
            statusMessage = "Imported ${finalFiles.size} file(s) into '$cleanName'",
          )
        }
        return@launch
      }

      val accountId = _uiState.value.selectedAccount?.id ?: return@launch
      _uiState.update { it.copy(isDeploying = true, statusMessage = "Uploading ${finalFiles.size} file(s) to '$name'…") }
      repository
        .createWorkerFromUploadedFiles(accountId, name, finalFiles, resolvedEntry)
        .onSuccess { created ->
          appendLog("DEPLOY", "Uploaded ${finalFiles.size} module(s) & created Worker '${created.id}'")
          refreshWorkersInternal(accountId)
          _uiState.update { it.copy(isDeploying = false, statusMessage = null) }
          openWorker(created)
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isDeploying = false,
              deployBannerMessage = err.localizedMessage ?: "Upload Worker failed",
              isDeployError = true,
            )
          }
          appendLog("ERROR", err.localizedMessage ?: "Upload Worker failed")
        }
    }
  }

  /**
   * Direct single or batch upload from device storage into the currently open Worker!
   * Merges new files or overwrites matching paths, optionally deploys immediately to Cloudflare.
   */
  fun uploadFilesToActiveWorker(
    uris: List<Uri>,
    folderPrefix: String = "",
    autoDeploy: Boolean = false,
  ) {
    if (uris.isEmpty()) return
    val ws = _uiState.value.workspace ?: return
    viewModelScope.launch {
      val picked: List<PickedDeviceFile> = DeviceFileHelper.readFiles(getApplication(), uris)
      if (picked.isEmpty()) {
        _uiState.update { it.copy(statusMessage = "No readable text/script files selected") }
        return@launch
      }

      val prefix = folderPrefix.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }
      val currentEditable = ws.files.filterNot { it.isVirtualReadOnly }.toMutableList()
      val virtualFiles = ws.files.filter { it.isVirtualReadOnly }
      val addedOrUpdatedPaths = mutableListOf<String>()

      for (item in picked) {
        val targetPath = "$prefix${item.fileName}"
        addedOrUpdatedPaths.add(targetPath)
        val existingIdx = currentEditable.indexOfFirst { it.path == targetPath }
        if (existingIdx >= 0) {
          val old = currentEditable[existingIdx]
          currentEditable[existingIdx] = old.copy(content = item.content)
        } else {
          currentEditable.add(
            WorkerFile(
              path = targetPath,
              content = item.content,
              originalContent = "",
              contentType = MultipartWorkerParser.inferModuleMimeType(targetPath),
              isEntryPoint = false,
            )
          )
        }
      }

      val nextFiles = currentEditable + virtualFiles
      val nextFieldValues =
        _uiState.value.editorFieldValues.toMutableMap().apply {
          picked.forEach { item ->
            val p = "$prefix${item.fileName}"
            put(p, TextFieldValue(item.content))
          }
        }
      val nextTabs = (_uiState.value.openTabPaths + addedOrUpdatedPaths).distinct()
      val nextActive = addedOrUpdatedPaths.firstOrNull() ?: _uiState.value.activeFilePath

      _uiState.update { state ->
        state.copy(
          workspace =
            ws.copy(
              files = nextFiles,
              isEsModuleFormat = true,
              hasModuleSetChanged = true,
            ),
          openTabPaths = nextTabs,
          activeFilePath = nextActive,
          editorFieldValues = nextFieldValues,
          diagnostics = nextFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
          statusMessage =
            if (autoDeploy) "Uploaded ${picked.size} file(s), deploying…"
            else "Imported ${picked.size} file(s) into ${ws.scriptName}",
        )
      }
      appendLog("INFO", "Imported ${picked.size} file(s) from device: ${addedOrUpdatedPaths.joinToString(", ")}")

      if (autoDeploy && !_uiState.value.isLocalScratchpad) {
        deployActiveWorker()
      }
    }
  }

  fun exportActiveFileToUri(uri: Uri) {
    val active = _uiState.value.activeFile ?: return
    viewModelScope.launch {
      DeviceFileHelper.writeTextToUri(getApplication(), uri, active.content)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Exported ${active.fileName}") }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Export failed") }
        }
    }
  }

  fun deleteWorker(scriptName: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .deleteWorker(accountId, scriptName)
        .onSuccess {
          appendLog("INFO", "Deleted Worker '$scriptName'")
          _uiState.update { state ->
            val cleared = state.workspace?.scriptName == scriptName
            state.copy(
              workspace = if (cleared) null else state.workspace,
              openTabPaths = if (cleared) emptyList() else state.openTabPaths,
              activeFilePath = if (cleared) null else state.activeFilePath,
              statusMessage = "Deleted '$scriptName'",
            )
          }
          refreshWorkersInternal(accountId)
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(statusMessage = err.localizedMessage ?: "Delete failed")
          }
        }
    }
  }

  fun openFileTab(path: String, recordHistory: Boolean = true) {
    _uiState.update { state ->
      val tabs =
        if (path in state.openTabPaths) state.openTabPaths else state.openTabPaths + path
      var nextHistory = state.tabNavHistory
      var nextIdx = state.tabNavIndex
      if (recordHistory && state.activeFilePath != path) {
        val truncated =
          if (nextIdx >= 0 && nextIdx < nextHistory.size) nextHistory.subList(0, nextIdx + 1)
          else nextHistory
        nextHistory = (truncated + path).takeLast(40)
        nextIdx = nextHistory.lastIndex
      }
      state.copy(
        openTabPaths = tabs,
        activeFilePath = path,
        tabNavHistory = nextHistory,
        tabNavIndex = nextIdx,
      )
    }
  }

  fun navigateTabBack() {
    val state = _uiState.value
    if (!state.canNavigateTabBack) return
    val targetIdx = state.tabNavIndex - 1
    val targetPath = state.tabNavHistory.getOrNull(targetIdx) ?: return
    val exists = state.workspace?.files?.any { it.path == targetPath } == true
    if (exists) {
      _uiState.update { it.copy(tabNavIndex = targetIdx) }
      openFileTab(targetPath, recordHistory = false)
    }
  }

  fun navigateTabForward() {
    val state = _uiState.value
    if (!state.canNavigateTabForward) return
    val targetIdx = state.tabNavIndex + 1
    val targetPath = state.tabNavHistory.getOrNull(targetIdx) ?: return
    val exists = state.workspace?.files?.any { it.path == targetPath } == true
    if (exists) {
      _uiState.update { it.copy(tabNavIndex = targetIdx) }
      openFileTab(targetPath, recordHistory = false)
    }
  }

  fun closeFileTab(path: String) {
    _uiState.update { state ->
      val nextTabs = state.openTabPaths.filterNot { it == path }
      val nextActive =
        if (state.activeFilePath == path) nextTabs.lastOrNull() else state.activeFilePath
      state.copy(openTabPaths = nextTabs, activeFilePath = nextActive)
    }
  }

  fun updateEditorValue(newValue: TextFieldValue) {
    val path = _uiState.value.activeFilePath ?: return
    val currentFile = _uiState.value.activeFile ?: return
    if (currentFile.isVirtualReadOnly) return

    val prevValue = _uiState.value.editorFieldValues[path] ?: TextFieldValue(currentFile.content)
    val textChanged = prevValue.text != newValue.text

    if (textChanged) {
      val undo = undoStacks.getOrPut(path) { ArrayDeque() }
      if (undo.lastOrNull()?.text != prevValue.text) {
        undo.addLast(prevValue)
        if (undo.size > 60) undo.removeFirst()
      }
      redoStacks[path]?.clear()
    }

    _uiState.update { state ->
      val ws = state.workspace ?: return@update state
      val updatedFiles =
        if (textChanged) {
          ws.files.map { f -> if (f.path == path) f.copy(content = newValue.text) else f }
        } else ws.files
      val diags =
        if (textChanged) updatedFiles.flatMap { CodeAnalyzer.analyzeFile(it) }
        else state.diagnostics
      state.copy(
        workspace = ws.copy(files = updatedFiles),
        editorFieldValues = state.editorFieldValues + (path to newValue),
        diagnostics = diags,
      )
    }
  }

  fun insertTextAtCursor(snippet: String, cursorOffsetBack: Int = 0) {
    val tfv = _uiState.value.activeTextFieldValue
    val start = tfv.selection.start.coerceIn(0, tfv.text.length)
    val end = tfv.selection.end.coerceIn(0, tfv.text.length)
    val replaced = tfv.text.replaceRange(minOf(start, end), maxOf(start, end), snippet)
    val nextCursor = (minOf(start, end) + snippet.length - cursorOffsetBack).coerceIn(0, replaced.length)
    updateEditorValue(TextFieldValue(replaced, TextRange(nextCursor)))
  }

  fun insertTab() {
    val spaces = " ".repeat(_uiState.value.editorSettings.tabSize)
    insertTextAtCursor(spaces)
  }

  fun undo() {
    val path = _uiState.value.activeFilePath ?: return
    val undo = undoStacks[path] ?: return
    if (undo.isEmpty()) return
    val current = _uiState.value.activeTextFieldValue
    redoStacks.getOrPut(path) { ArrayDeque() }.addLast(current)
    val previous = undo.removeLast()
    applyDirectTextValue(path, previous)
  }

  fun redo() {
    val path = _uiState.value.activeFilePath ?: return
    val redo = redoStacks[path] ?: return
    if (redo.isEmpty()) return
    val current = _uiState.value.activeTextFieldValue
    undoStacks.getOrPut(path) { ArrayDeque() }.addLast(current)
    val next = redo.removeLast()
    applyDirectTextValue(path, next)
  }

  private fun applyDirectTextValue(path: String, value: TextFieldValue) {
    _uiState.update { state ->
      val ws = state.workspace ?: return@update state
      val updatedFiles = ws.files.map { if (it.path == path) it.copy(content = value.text) else it }
      state.copy(
        workspace = ws.copy(files = updatedFiles),
        editorFieldValues = state.editorFieldValues + (path to value),
        diagnostics = updatedFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
      )
    }
  }

  fun formatActiveFile() {
    val file = _uiState.value.activeFile ?: return
    if (file.isVirtualReadOnly) return
    val formatted = CodeFormatter.format(file.content, file.language, _uiState.value.editorSettings.tabSize)
    if (formatted != file.content) {
      val cursor = formatted.length.coerceAtMost(_uiState.value.activeTextFieldValue.selection.start)
      updateEditorValue(TextFieldValue(formatted, TextRange(cursor)))
      _uiState.update { it.copy(statusMessage = "Formatted ${file.fileName}") }
    }
  }

  fun toggleLineComment() {
    val tfv = _uiState.value.activeTextFieldValue
    val file = _uiState.value.activeFile ?: return
    if (file.isVirtualReadOnly) return
    val pos = tfv.selection.start.coerceIn(0, tfv.text.length)
    val lineStart = tfv.text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = tfv.text.indexOf('\n', pos).let { if (it < 0) tfv.text.length else it }
    val lineText = tfv.text.substring(lineStart, lineEnd)
    val trimmed = lineText.trimStart()
    val updatedLine =
      if (trimmed.startsWith("// ")) {
        lineText.replaceFirst("// ", "")
      } else if (trimmed.startsWith("//")) {
        lineText.replaceFirst("//", "")
      } else {
        val indent = lineText.takeWhile { it == ' ' || it == '\t' }
        "$indent// ${lineText.drop(indent.length)}"
      }
    val nextText = tfv.text.replaceRange(lineStart, lineEnd, updatedLine)
    val nextCursor = (lineStart + updatedLine.length).coerceIn(0, nextText.length)
    updateEditorValue(TextFieldValue(nextText, TextRange(nextCursor)))
  }

  fun jumpToLine(filePath: String, line: Int) {
    openFileTab(filePath)
    val file = _uiState.value.workspace?.files?.firstOrNull { it.path == filePath } ?: return
    var offset = 0
    val lines = file.content.lines()
    val targetIdx = (line - 1).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    for (i in 0 until targetIdx) {
      offset += lines[i].length + 1
    }
    val safeOffset = offset.coerceIn(0, file.content.length)
    _uiState.update { state ->
      state.copy(
        editorFieldValues =
          state.editorFieldValues +
            (filePath to TextFieldValue(file.content, TextRange(safeOffset)))
      )
    }
  }

  fun createModuleDirectory(dirPathRaw: String) {
    val ws = _uiState.value.workspace ?: return
    val clean = dirPathRaw.trim().trim('/')
    if (clean.isBlank()) return
    _uiState.update { state ->
      state.copy(
        workspace = ws.copy(directories = ws.directories + clean),
        statusMessage = "Created directory '$clean'",
      )
    }
  }

  fun addModuleFile(fileName: String, autoRunLocally: Boolean = false) {
    val clean = fileName.trim().removePrefix("/")
    if (clean.isBlank()) return
    val starter =
      when {
        clean.endsWith(".json", ignoreCase = true) -> "{\n  \"ok\": true,\n  \"service\": \"edgecode\"\n}\n"
        clean.endsWith(".html", ignoreCase = true) -> "<!doctype html>\n<html>\n  <body style=\"font-family:sans-serif;padding:16px;\">\n    <h2>EdgeCode Live Preview</h2>\n    <p>Rendered directly without deploying.</p>\n  </body>\n</html>\n"
        clean.endsWith(".sh", ignoreCase = true) -> "#!/system/bin/sh\necho \"Hello from EdgeCode shell\"\n"
        else ->
          """export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    console.log("Dry-run request:", request.method, url.pathname);
    return new Response(
      JSON.stringify({ ok: true, file: "$clean", path: url.pathname }, null, 2),
      { headers: { "content-type": "application/json" } }
    );
  }
};
"""
      }

    val existingWs = _uiState.value.workspace
    if (existingWs == null) {
      val file =
        WorkerFile(
          path = clean,
          content = starter,
          originalContent = starter,
          contentType = MultipartWorkerParser.inferModuleMimeType(clean),
          isEntryPoint = true,
        )
      val ws =
        WorkerWorkspace(
          scriptName = "unsaved-sandbox",
          isEsModuleFormat = true,
          entryModulePath = clean,
          compatibilityDate = "2024-11-01",
          files = listOf(file),
          bindings = emptyList(),
          routes = emptyList(),
          workersDevEnabled = false,
          accountSubdomain = "",
        )
      _uiState.update {
        it.copy(
          isLocalScratchpad = it.credentials == null,
          workspace = ws,
          openTabPaths = listOf(clean),
          activeFilePath = clean,
          tabNavHistory = listOf(clean),
          tabNavIndex = 0,
          editorFieldValues = mapOf(clean to TextFieldValue(starter)),
          diagnostics = CodeAnalyzer.analyzeFile(file),
          statusMessage = "Created '$clean' in memory (not deployed)",
        )
      }
      if (autoRunLocally) runActiveFileLocally()
      return
    }

    if (existingWs.files.any { it.path == clean }) {
      openFileTab(clean)
      if (autoRunLocally) runActiveFileLocally()
      return
    }

    val newFile =
      WorkerFile(
        path = clean,
        content = starter,
        originalContent = "",
        contentType = MultipartWorkerParser.inferModuleMimeType(clean),
        isEntryPoint = false,
      )
    val parentDir = if (clean.contains('/')) clean.substringBeforeLast('/') else ""
    val nextDirs = if (parentDir.isNotBlank()) existingWs.directories + parentDir else existingWs.directories
    val updatedFiles =
      (existingWs.files.filterNot { it.isVirtualReadOnly } + newFile + existingWs.files.filter { it.isVirtualReadOnly })
    _uiState.update { state ->
      state.copy(
        workspace =
          existingWs.copy(
            files = updatedFiles,
            directories = nextDirs,
            isEsModuleFormat = true,
            hasModuleSetChanged = true,
          ),
        openTabPaths = state.openTabPaths + clean,
        activeFilePath = clean,
        editorFieldValues = state.editorFieldValues + (clean to TextFieldValue(starter)),
        diagnostics = updatedFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
      )
    }
    openFileTab(clean, recordHistory = true)
    if (autoRunLocally) runActiveFileLocally()
  }

  /**
   * Directly imports one or more files from device storage into an in-memory tab WITHOUT
   * saving or deploying to Cloudflare, and immediately runs the first script to display its result.
   */
  fun importAndRunFilesDirectly(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      val picked = DeviceFileHelper.readFiles(getApplication(), uris)
      if (picked.isEmpty()) {
        _uiState.update { it.copy(statusMessage = "Could not read selected file") }
        return@launch
      }

      val existingWs = _uiState.value.workspace
      if (existingWs == null) {
        val importedFiles =
          picked.mapIndexed { idx, p ->
            WorkerFile(
              path = p.fileName,
              content = p.content,
              originalContent = p.content,
              contentType = MultipartWorkerParser.inferModuleMimeType(p.fileName),
              isEntryPoint = idx == 0,
            )
          }
        val primary = importedFiles.first().path
        val ws =
          WorkerWorkspace(
            scriptName = "imported-sandbox",
            isEsModuleFormat = true,
            entryModulePath = primary,
            compatibilityDate = "2024-11-01",
            files = importedFiles,
            bindings = emptyList(),
            routes = emptyList(),
            workersDevEnabled = false,
            accountSubdomain = "",
          )
        terminalEngine.syncWorkspaceToDisk(ws)
        _uiState.update {
          it.copy(
            isLocalScratchpad = it.credentials == null,
            workspace = ws,
            openTabPaths = importedFiles.map { f -> f.path },
            activeFilePath = primary,
            tabNavHistory = listOf(primary),
            tabNavIndex = 0,
            editorFieldValues = importedFiles.associate { f -> f.path to TextFieldValue(f.content) },
            diagnostics = importedFiles.flatMap { f -> CodeAnalyzer.analyzeFile(f) },
            statusMessage = "Imported ${importedFiles.size} file(s) & running locally (not deployed)",
          )
        }
        runActiveFileLocally()
      } else {
        val currentEditable = existingWs.files.filterNot { it.isVirtualReadOnly }.toMutableList()
        val virtual = existingWs.files.filter { it.isVirtualReadOnly }
        val importedPaths = mutableListOf<String>()
        picked.forEach { item ->
          importedPaths.add(item.fileName)
          val idx = currentEditable.indexOfFirst { it.path == item.fileName }
          if (idx >= 0) {
            currentEditable[idx] = currentEditable[idx].copy(content = item.content)
          } else {
            currentEditable.add(
              WorkerFile(
                path = item.fileName,
                content = item.content,
                originalContent = "",
                contentType = MultipartWorkerParser.inferModuleMimeType(item.fileName),
                isEntryPoint = false,
              ),
            )
          }
        }
        val nextFiles = currentEditable + virtual
        val firstPath = importedPaths.first()
        _uiState.update { state ->
          state.copy(
            workspace = existingWs.copy(files = nextFiles, isEsModuleFormat = true, hasModuleSetChanged = true),
            openTabPaths = (state.openTabPaths + importedPaths).distinct(),
            activeFilePath = firstPath,
            editorFieldValues =
              state.editorFieldValues + picked.associate { it.fileName to TextFieldValue(it.content) },
            diagnostics = nextFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
            statusMessage = "Loaded '$firstPath' & running locally (not deployed)",
          )
        }
        openFileTab(firstPath, recordHistory = true)
        runActiveFileLocally()
      }
    }
  }

  fun updateRunRequest(
    method: String = _uiState.value.runRequestMethod,
    path: String = _uiState.value.runRequestPath,
    headersJson: String = _uiState.value.runRequestHeadersJson,
    body: String = _uiState.value.runRequestBody,
  ) {
    _uiState.update {
      it.copy(
        runRequestMethod = method,
        runRequestPath = path,
        runRequestHeadersJson = headersJson,
        runRequestBody = body,
      )
    }
  }

  /**
   * Executes the currently active script (or specified file path) locally via Android V8 WebView / /system/bin/sh
   * using the unsaved in-memory editor buffers — NO save or Cloudflare deploy required!
   */
  fun runActiveFileLocally(targetPath: String? = null) {
    val state = _uiState.value
    val targetFile =
      (targetPath?.let { p -> state.workspace?.files?.firstOrNull { it.path == p } } ?: state.activeFile)
        ?: return
    if (targetFile.isVirtualReadOnly) {
      _uiState.update { it.copy(statusMessage = "Select an editable script file (.js, .ts, .html, .sh) to run") }
      return
    }
    _uiState.update {
      it.copy(
        isRunningLocalScript = true,
        showBottomPanel = true,
        activeBottomTab = BottomPanelTab.RUN_PREVIEW,
      )
    }
    viewModelScope.launch {
      val result =
        localScriptRunner.runLocally(
          targetFile = targetFile,
          workspace = _uiState.value.workspace,
          method = _uiState.value.runRequestMethod,
          requestPath = _uiState.value.runRequestPath,
          headersJson = _uiState.value.runRequestHeadersJson,
          requestBody = _uiState.value.runRequestBody,
        )
      _uiState.update {
        it.copy(
          isRunningLocalScript = false,
          localRunResult = result,
        )
      }
      appendLog(
        if (result.isError) "ERROR" else "RUN",
        "Local run '${targetFile.fileName}' -> ${result.statusCode} ${result.statusText} (${result.durationMs}ms)",
      )
    }
  }

  fun deleteModuleFile(filePath: String) {
    val ws = _uiState.value.workspace ?: return
    val target = ws.files.firstOrNull { it.path == filePath } ?: return
    if (target.isVirtualReadOnly) return
    val editableRemaining = ws.files.filter { !it.isVirtualReadOnly && it.path != filePath }
    if (editableRemaining.isEmpty()) {
      _uiState.update { it.copy(statusMessage = "A Worker needs at least one script file") }
      return
    }
    val nextEntry =
      if (ws.entryModulePath == filePath) {
        editableRemaining.firstOrNull { it.path.endsWith(".js", true) || it.path.endsWith(".mjs", true) }?.path
          ?: editableRemaining.first().path
      } else {
        ws.entryModulePath
      }
    val updatedFiles =
      (editableRemaining.map { it.copy(isEntryPoint = it.path == nextEntry) } +
        ws.files.filter { it.isVirtualReadOnly })

    val nextTabs = _uiState.value.openTabPaths.filterNot { it == filePath }
    val nextActive =
      if (_uiState.value.activeFilePath == filePath) nextTabs.lastOrNull() ?: nextEntry
      else _uiState.value.activeFilePath

    _uiState.update { state ->
      state.copy(
        workspace =
          ws.copy(
            files = updatedFiles,
            entryModulePath = nextEntry,
            hasModuleSetChanged = true,
          ),
        openTabPaths = nextTabs,
        activeFilePath = nextActive,
        editorFieldValues = state.editorFieldValues - filePath,
        diagnostics = updatedFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
        statusMessage = "Removed '$filePath' (tap Deploy to sync to Cloudflare)",
      )
    }
    appendLog("INFO", "Removed module '$filePath' from '${ws.scriptName}'")
  }

  fun renameModuleFile(oldPath: String, newPathRaw: String) {
    val ws = _uiState.value.workspace ?: return
    val newPath = newPathRaw.trim().removePrefix("/")
    if (newPath.isBlank() || newPath == oldPath || ws.files.any { it.path == newPath }) return
    val target = ws.files.firstOrNull { it.path == oldPath } ?: return
    if (target.isVirtualReadOnly) return

    val updatedFiles =
      ws.files.map { f ->
        if (f.path == oldPath) {
          f.copy(path = newPath, contentType = MultipartWorkerParser.inferModuleMimeType(newPath))
        } else f
      }
    val nextEntry = if (ws.entryModulePath == oldPath) newPath else ws.entryModulePath
    val nextTabs = _uiState.value.openTabPaths.map { if (it == oldPath) newPath else it }
    val nextActive = if (_uiState.value.activeFilePath == oldPath) newPath else _uiState.value.activeFilePath
    val oldFieldVal = _uiState.value.editorFieldValues[oldPath] ?: TextFieldValue(target.content)
    val nextFieldValues = (_uiState.value.editorFieldValues - oldPath) + (newPath to oldFieldVal)

    _uiState.update { state ->
      state.copy(
        workspace = ws.copy(files = updatedFiles, entryModulePath = nextEntry, hasModuleSetChanged = true),
        openTabPaths = nextTabs,
        activeFilePath = nextActive,
        editorFieldValues = nextFieldValues,
        diagnostics = updatedFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
        statusMessage = "Renamed '$oldPath' -> '$newPath'",
      )
    }
  }

  fun setEntryModule(filePath: String) {
    val ws = _uiState.value.workspace ?: return
    val target = ws.files.firstOrNull { it.path == filePath } ?: return
    if (target.isVirtualReadOnly) return
    val updatedFiles = ws.files.map { it.copy(isEntryPoint = it.path == filePath) }
    _uiState.update { state ->
      state.copy(
        workspace =
          ws.copy(
            files = updatedFiles,
            entryModulePath = filePath,
            isEsModuleFormat = true,
            hasModuleSetChanged = true,
          ),
        statusMessage = "Entry point set to '$filePath'",
      )
    }
  }

  fun revertFile(filePath: String) {
    val ws = _uiState.value.workspace ?: return
    val target = ws.files.firstOrNull { it.path == filePath } ?: return
    if (target.isVirtualReadOnly) return
    val restored = target.originalContent
    applyDirectTextValue(filePath, TextFieldValue(restored))
    _uiState.update { it.copy(statusMessage = "Reverted $filePath") }
  }

  fun deployActiveWorker() {
    if (_uiState.value.isLocalScratchpad) {
      _uiState.update {
        it.copy(
          deployBannerMessage = "Local Scratchpad mode — tap Connect to authorize Cloudflare & deploy live.",
          isDeployError = false,
        )
      }
      return
    }
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isDeploying = true, deployBannerMessage = null) }
      repository
        .deployWorkerWorkspace(accountId, ws)
        .onSuccess {
          val cleanFiles = ws.files.map { it.copy(originalContent = it.content) }
          val refreshedDeployments =
            repository.listDeployments(accountId, ws.scriptName).getOrDefault(ws.deployments)
          _uiState.update { state ->
            state.copy(
              workspace =
                ws.copy(
                  files = cleanFiles,
                  deployments = refreshedDeployments,
                  hasModuleSetChanged = false,
                ),
              isDeploying = false,
              isDeployError = false,
              deployBannerMessage = "Deployed '${ws.scriptName}' to Cloudflare Edge",
            )
          }
          appendLog("DEPLOY", "Published '${ws.scriptName}' (${cleanFiles.count { !it.isVirtualReadOnly }} module(s)) live to Cloudflare Edge")
          refreshWorkersInternal(accountId)
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isDeploying = false,
              isDeployError = true,
              deployBannerMessage = err.localizedMessage ?: "Deploy failed",
            )
          }
          appendLog("ERROR", err.localizedMessage ?: "Deploy failed")
        }
    }
  }

  fun dismissDeployBanner() {
    _uiState.update { it.copy(deployBannerMessage = null) }
  }

  fun showStatus(message: String) {
    _uiState.update { it.copy(statusMessage = message) }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }

  // Find & Replace
  fun toggleFindBar(show: Boolean? = null) {
    _uiState.update { it.copy(showFindBar = show ?: !it.showFindBar) }
  }

  fun setFindQuery(q: String) {
    _uiState.update { it.copy(findQuery = q) }
  }

  fun setReplaceQuery(q: String) {
    _uiState.update { it.copy(replaceQuery = q) }
  }

  fun findNextMatch() {
    val q = _uiState.value.findQuery
    if (q.isEmpty()) return
    val tfv = _uiState.value.activeTextFieldValue
    val from = tfv.selection.end.coerceIn(0, tfv.text.length)
    var idx = tfv.text.indexOf(q, from, ignoreCase = true)
    if (idx < 0 && from > 0) idx = tfv.text.indexOf(q, 0, ignoreCase = true)
    if (idx >= 0) {
      val path = _uiState.value.activeFilePath ?: return
      _uiState.update { state ->
        state.copy(
          editorFieldValues =
            state.editorFieldValues +
              (path to tfv.copy(selection = TextRange(idx, idx + q.length)))
        )
      }
    }
  }

  fun replaceAllMatches() {
    val q = _uiState.value.findQuery
    if (q.isEmpty()) return
    val tfv = _uiState.value.activeTextFieldValue
    val updated = tfv.text.replace(q, _uiState.value.replaceQuery, ignoreCase = true)
    if (updated != tfv.text) {
      updateEditorValue(TextFieldValue(updated, TextRange(0)))
    }
  }

  fun setSidebarSection(section: SidebarSection) {
    _uiState.update { it.copy(sidebarSection = section) }
    if (!_uiState.value.isLocalScratchpad) {
      when (section) {
        SidebarSection.BINDINGS -> loadCloudflareInfra()
        SidebarSection.PAGES -> if (_uiState.value.pagesProjects.isEmpty()) loadPagesProjects()
        else -> Unit
      }
    }
  }

  fun setSearchQuery(q: String) {
    _uiState.update { it.copy(searchQuery = q) }
  }

  fun toggleBottomPanel(tab: BottomPanelTab? = null) {
    _uiState.update { state ->
      if (tab == null) state.copy(showBottomPanel = !state.showBottomPanel)
      else state.copy(showBottomPanel = true, activeBottomTab = tab)
    }
  }

  fun hideBottomPanel() {
    _uiState.update { it.copy(showBottomPanel = false, isBottomPanelExpanded = false) }
  }

  fun toggleBottomPanelExpanded() {
    _uiState.update { it.copy(isBottomPanelExpanded = !it.isBottomPanelExpanded) }
  }

  // Bindings, Compatibility, Cron Schedules, Zones, & Deployments
  fun toggleWorkersDev(enabled: Boolean) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    viewModelScope.launch {
      repository
        .toggleWorkersDev(accountId, ws.scriptName, enabled)
        .onSuccess { actual ->
          _uiState.update { state ->
            state.copy(workspace = state.workspace?.copy(workersDevEnabled = actual))
          }
          appendLog("INFO", "workers.dev route ${if (actual) "enabled" else "disabled"} for ${ws.scriptName}")
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Route toggle failed") }
        }
    }
  }

  fun updateCompatibilitySettings(compatDate: String, nodejsCompat: Boolean) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    val nextFlags =
      if (nodejsCompat) (ws.compatibilityFlags + "nodejs_compat").distinct()
      else ws.compatibilityFlags.filterNot { it == "nodejs_compat" }
    viewModelScope.launch {
      repository
        .saveBindingsAndCompat(accountId, ws.scriptName, compatDate.trim(), nextFlags, ws.bindings)
        .onSuccess { settings ->
          _uiState.update { state ->
            state.copy(
              workspace =
                state.workspace?.copy(
                  compatibilityDate = settings.compatibilityDate ?: compatDate,
                  compatibilityFlags = settings.compatibilityFlags ?: nextFlags,
                ),
              statusMessage = "Saved compatibility settings",
            )
          }
          appendLog("INFO", "Updated compatibility_date=$compatDate, nodejs_compat=$nodejsCompat")
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Update failed") }
        }
    }
  }

  fun addEnvVar(name: String, value: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    val cleanName = name.trim()
    if (cleanName.isBlank()) return
    viewModelScope.launch {
      val updated =
        ws.bindings.filterNot { it.name == cleanName } +
          CfWorkerBinding(name = cleanName, type = "plain_text", text = value)
      repository
        .saveBindingsAndCompat(
          accountId,
          ws.scriptName,
          ws.compatibilityDate,
          ws.compatibilityFlags,
          updated,
        )
        .onSuccess { settings ->
          _uiState.update { state ->
            state.copy(
              workspace =
                state.workspace?.copy(bindings = settings.bindings ?: updated),
              statusMessage = "Saved variable $cleanName",
            )
          }
          appendLog("INFO", "Saved env var '$cleanName' on ${ws.scriptName}")
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Save failed") }
        }
    }
  }

  fun addSecret(name: String, value: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    val cleanName = name.trim()
    if (cleanName.isBlank() || value.isBlank()) return
    viewModelScope.launch {
      repository
        .putSecret(accountId, ws.scriptName, cleanName, value)
        .onSuccess {
          val updated =
            ws.bindings.filterNot { it.name == cleanName } +
              CfWorkerBinding(name = cleanName, type = "secret_text")
          _uiState.update { state ->
            state.copy(
              workspace = state.workspace?.copy(bindings = updated),
              statusMessage = "Encrypted secret '$cleanName' uploaded",
            )
          }
          appendLog("INFO", "Uploaded secret '$cleanName' to Cloudflare")
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Secret upload failed") }
        }
    }
  }

  fun deleteBindingOrSecret(binding: CfWorkerBinding) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    viewModelScope.launch {
      if (binding.type == "secret_text") {
        repository
          .deleteSecret(accountId, ws.scriptName, binding.name)
          .onSuccess {
            val next = ws.bindings.filterNot { it.name == binding.name }
            _uiState.update { it.copy(workspace = ws.copy(bindings = next)) }
          }
      } else {
        val next = ws.bindings.filterNot { it.name == binding.name }
        repository
          .saveBindingsAndCompat(
            accountId,
            ws.scriptName,
            ws.compatibilityDate,
            ws.compatibilityFlags,
            next,
          )
          .onSuccess {
            _uiState.update { it.copy(workspace = ws.copy(bindings = next)) }
          }
      }
    }
  }

  fun addCronSchedule(cronExpr: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    val clean = cronExpr.trim()
    if (clean.isBlank()) return
    val updated = (ws.schedules.map { it.cron } + clean).distinct()
    viewModelScope.launch {
      repository
        .updateCronSchedules(accountId, ws.scriptName, updated)
        .onSuccess { schedules ->
          _uiState.update { state ->
            state.copy(
              workspace = state.workspace?.copy(schedules = schedules),
              statusMessage = "Added Cron '$clean'",
            )
          }
          appendLog("INFO", "Updated Cron triggers: ${schedules.joinToString { it.cron }}")
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Cron update failed") }
        }
    }
  }

  fun removeCronSchedule(cronExpr: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    val updated = ws.schedules.map { it.cron }.filterNot { it == cronExpr }
    viewModelScope.launch {
      repository
        .updateCronSchedules(accountId, ws.scriptName, updated)
        .onSuccess { schedules ->
          _uiState.update { state ->
            state.copy(workspace = state.workspace?.copy(schedules = schedules))
          }
        }
    }
  }

  fun rollbackToVersion(versionId: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    viewModelScope.launch {
      repository
        .rollbackToVersion(accountId, ws.scriptName, versionId)
        .onSuccess {
          appendLog("DEPLOY", "Rolled back '${ws.scriptName}' to version ${versionId.take(8)}")
          val script = _uiState.value.workers.firstOrNull { it.id == ws.scriptName } ?: CfWorkerScript(id = ws.scriptName)
          openWorker(script)
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Rollback failed") }
        }
    }
  }

  fun loadZones() {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository.listZones(accountId).onSuccess { list ->
        _uiState.update { it.copy(zones = list) }
      }
    }
  }

  fun attachZoneRoute(zoneId: String, pattern: String) {
    val ws = _uiState.value.workspace ?: return
    if (pattern.isBlank()) return
    viewModelScope.launch {
      repository
        .createZoneRoute(zoneId, pattern, ws.scriptName)
        .onSuccess { route ->
          _uiState.update { state ->
            state.copy(
              workspace = state.workspace?.copy(routes = state.workspace.routes + route),
              statusMessage = "Attached route '${route.pattern}'",
            )
          }
          appendLog("INFO", "Attached route '${route.pattern}' -> ${ws.scriptName}")
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Route failed") }
        }
    }
  }

  // Live Tail WebSocket Logs
  fun toggleLiveTail() {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    if (_uiState.value.isTailRunning) {
      stopTailIfRunning()
      return
    }
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          showBottomPanel = true,
          activeBottomTab = BottomPanelTab.TAIL_LOGS,
        )
      }
      repository
        .startLiveTail(
          accountId = accountId,
          scriptName = ws.scriptName,
          onTailEvent = { raw ->
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            _uiState.update { state ->
              val next = (state.tailLogs + ConsoleLogEntry(ts, "TAIL", raw)).takeLast(120)
              val nextTerm =
                if (state.activeBottomTab == BottomPanelTab.TERMINAL) {
                  (state.terminalEntries +
                    TerminalEntry(
                      id = terminalIdGen.getAndIncrement(),
                      timestamp = ts,
                      promptPrefix = "tail",
                      command = "edge event",
                      output = raw,
                      kind = TerminalEntryKind.WRANGLER,
                    )).takeLast(120)
                } else state.terminalEntries
              state.copy(tailLogs = next, terminalEntries = nextTerm)
            }
          },
          onClosed = { reason ->
            activeTailSession = null
            _uiState.update { it.copy(isTailRunning = false) }
            if (!reason.isNullOrBlank()) appendLog("TAIL", "Tail closed: $reason")
          },
        )
        .onSuccess { session ->
          activeTailSession = session
          _uiState.update { it.copy(isTailRunning = true) }
          appendLog("TAIL", "Started Live Tail for '${ws.scriptName}'")
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isTailRunning = false,
              statusMessage = err.localizedMessage ?: "Could not start Live Tail",
            )
          }
        }
    }
  }

  private fun stopTailIfRunning() {
    val session = activeTailSession ?: return
    activeTailSession = null
    _uiState.update { it.copy(isTailRunning = false) }
    val accountId = _uiState.value.selectedAccount?.id
    val scriptName = _uiState.value.workspace?.scriptName
    if (accountId != null && scriptName != null) {
      viewModelScope.launch { repository.stopLiveTail(accountId, scriptName, session) }
    } else {
      runCatching { session.webSocket.close(1000, "Stopped") }
    }
  }

  // Comprehensive Cloudflare Workers Infra: Bindings (KV/D1/R2/Service), Custom Domains, D1, R2, DO, Queues
  fun loadCloudflareInfra() {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isInfraBusy = true, isKvBusy = true) }
      repository.listKvNamespaces(accountId).onSuccess { list ->
        _uiState.update { it.copy(kvNamespaces = list, isKvBusy = false) }
      }.onFailure { _uiState.update { it.copy(isKvBusy = false) } }

      repository.listZones(accountId).onSuccess { z -> _uiState.update { it.copy(zones = z) } }
      repository.listD1Databases(accountId).onSuccess { d1 -> _uiState.update { it.copy(d1Databases = d1) } }
      repository.listR2Buckets(accountId).onSuccess { r2 -> _uiState.update { it.copy(r2Buckets = r2) } }
      repository.listDurableObjects(accountId).onSuccess { doList -> _uiState.update { it.copy(durableObjects = doList) } }
      repository.listQueues(accountId).onSuccess { q -> _uiState.update { it.copy(queues = q) } }
      _uiState.update { it.copy(isInfraBusy = false) }
    }
  }

  fun bindResourceToWorker(binding: CfWorkerBinding) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    if (binding.name.isBlank()) return
    viewModelScope.launch {
      val next = ws.bindings.filterNot { it.name == binding.name } + binding
      repository
        .saveBindingsAndCompat(accountId, ws.scriptName, ws.compatibilityDate, ws.compatibilityFlags, next)
        .onSuccess { settings ->
          _uiState.update { state ->
            state.copy(
              workspace = state.workspace?.copy(bindings = settings.bindings ?: next),
              statusMessage = "Bound ${binding.name} (${binding.type}) to ${ws.scriptName}",
            )
          }
          appendLog("INFO", "Bound '${binding.name}' (${binding.type}) on ${ws.scriptName}")
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Bind failed") }
        }
    }
  }

  fun deleteZoneRoute(zoneId: String, routeId: String) {
    viewModelScope.launch {
      repository
        .deleteZoneRoute(zoneId, routeId)
        .onSuccess {
          _uiState.update { state ->
            state.copy(
              workspace = state.workspace?.copy(routes = state.workspace.routes.filterNot { it.id == routeId }),
              statusMessage = "Removed zone route",
            )
          }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Delete route failed") }
        }
    }
  }

  fun attachWorkerCustomDomain(zoneId: String, hostname: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    if (hostname.isBlank()) return
    viewModelScope.launch {
      repository
        .attachWorkerDomain(accountId, hostname, ws.scriptName, zoneId)
        .onSuccess { record ->
          _uiState.update { state ->
            state.copy(
              workspace = state.workspace?.copy(domains = state.workspace.domains + record),
              statusMessage = "Attached custom domain ${record.hostname}",
            )
          }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Domain attach failed") }
        }
    }
  }

  fun removeWorkerCustomDomain(domainId: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .removeWorkerDomain(accountId, domainId)
        .onSuccess {
          _uiState.update { state ->
            state.copy(
              workspace = state.workspace?.copy(domains = state.workspace.domains.filterNot { it.id == domainId }),
              statusMessage = "Removed custom domain",
            )
          }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Remove domain failed") }
        }
    }
  }

  // KV Browser & Namespace CRUD
  fun loadKvNamespaces() {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isKvBusy = true) }
      repository
        .listKvNamespaces(accountId)
        .onSuccess { list ->
          _uiState.update { it.copy(kvNamespaces = list, isKvBusy = false) }
        }
        .onFailure {
          _uiState.update { it.copy(isKvBusy = false) }
        }
    }
  }

  fun createKvNamespace(title: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    if (title.isBlank()) return
    viewModelScope.launch {
      repository
        .createKvNamespace(accountId, title)
        .onSuccess { ns ->
          _uiState.update { it.copy(statusMessage = "Created KV '${ns.title}'") }
          loadKvNamespaces()
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "KV create failed") }
        }
    }
  }

  fun deleteKvNamespace(namespaceId: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .deleteKvNamespace(accountId, namespaceId)
        .onSuccess {
          _uiState.update {
            it.copy(
              selectedKvNamespace = if (it.selectedKvNamespace?.id == namespaceId) null else it.selectedKvNamespace,
              kvKeys = if (it.selectedKvNamespace?.id == namespaceId) emptyList() else it.kvKeys,
              statusMessage = "Deleted KV namespace",
            )
          }
          loadKvNamespaces()
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "KV delete failed") }
        }
    }
  }

  fun selectKvNamespace(ns: CfKvNamespace) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      _uiState.update {
        it.copy(selectedKvNamespace = ns, isKvBusy = true, selectedKvKeyValue = null)
      }
      repository
        .listKvKeys(accountId, ns.id)
        .onSuccess { keys ->
          _uiState.update { it.copy(kvKeys = keys, isKvBusy = false) }
        }
        .onFailure {
          _uiState.update { it.copy(isKvBusy = false) }
        }
    }
  }

  fun inspectKvKey(keyName: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val nsId = _uiState.value.selectedKvNamespace?.id ?: return
    viewModelScope.launch {
      repository.readKvValue(accountId, nsId, keyName).onSuccess { value ->
        _uiState.update { it.copy(selectedKvKeyValue = keyName to value) }
      }
    }
  }

  fun putKvEntry(keyName: String, value: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ns = _uiState.value.selectedKvNamespace ?: return
    viewModelScope.launch {
      repository.writeKvValue(accountId, ns.id, keyName.trim(), value).onSuccess {
        selectKvNamespace(ns)
        _uiState.update { it.copy(selectedKvKeyValue = keyName.trim() to value) }
      }
    }
  }

  fun deleteKvKey(keyName: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ns = _uiState.value.selectedKvNamespace ?: return
    viewModelScope.launch {
      repository.deleteKvKey(accountId, ns.id, keyName).onSuccess {
        selectKvNamespace(ns)
        _uiState.update { it.copy(selectedKvKeyValue = null, statusMessage = "Deleted KV key '$keyName'") }
      }
    }
  }

  // D1 Databases & SQL Runner
  fun selectD1Database(db: CfD1Database) {
    _uiState.update { it.copy(selectedD1Database = db, d1QueryResults = emptyList()) }
  }

  fun createD1Database(name: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    if (name.isBlank()) return
    viewModelScope.launch {
      repository
        .createD1Database(accountId, name)
        .onSuccess { db ->
          _uiState.update { it.copy(statusMessage = "Created D1 '${db.name}'") }
          loadCloudflareInfra()
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "D1 create failed") }
        }
    }
  }

  fun deleteD1Database(databaseId: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .deleteD1Database(accountId, databaseId)
        .onSuccess {
          _uiState.update {
            it.copy(
              selectedD1Database = if (it.selectedD1Database?.uuid == databaseId) null else it.selectedD1Database,
              statusMessage = "Deleted D1 database",
            )
          }
          loadCloudflareInfra()
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "D1 delete failed") }
        }
    }
  }

  fun runD1SqlQuery(databaseId: String, sql: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    if (sql.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isInfraBusy = true) }
      repository
        .queryD1Database(accountId, databaseId, sql)
        .onSuccess { results ->
          _uiState.update { it.copy(d1QueryResults = results, isInfraBusy = false) }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isInfraBusy = false,
              statusMessage = err.localizedMessage ?: "SQL query error",
            )
          }
        }
    }
  }

  // R2 Buckets
  fun createR2Bucket(name: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    if (name.isBlank()) return
    viewModelScope.launch {
      repository
        .createR2Bucket(accountId, name)
        .onSuccess { b ->
          _uiState.update { it.copy(statusMessage = "Created R2 bucket '${b.name}'") }
          loadCloudflareInfra()
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "R2 create failed") }
        }
    }
  }

  fun deleteR2Bucket(bucketName: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .deleteR2Bucket(accountId, bucketName)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Deleted R2 bucket '$bucketName'") }
          loadCloudflareInfra()
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "R2 delete failed") }
        }
    }
  }

  // Cloudflare Pages Management
  fun loadPagesProjects() {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isPagesBusy = true) }
      repository
        .listPagesProjects(accountId)
        .onSuccess { list ->
          _uiState.update { it.copy(pagesProjects = list, isPagesBusy = false) }
        }
        .onFailure { err ->
          _uiState.update { it.copy(isPagesBusy = false, statusMessage = err.localizedMessage) }
        }
    }
  }

  fun selectPagesProject(project: CfPagesProject?) {
    if (project == null) {
      _uiState.update {
        it.copy(selectedPagesProject = null, pagesDeployments = emptyList(), pagesBuildLogs = emptyList(), pagesDomains = emptyList())
      }
      return
    }
    val accountId = _uiState.value.selectedAccount?.id ?: return
    _uiState.update { it.copy(selectedPagesProject = project, isPagesBusy = true, pagesBuildLogs = emptyList()) }
    viewModelScope.launch {
      val deps = repository.listPagesDeployments(accountId, project.name).getOrDefault(emptyList())
      val domains = repository.listPagesDomains(accountId, project.name).getOrDefault(emptyList())
      _uiState.update { it.copy(pagesDeployments = deps, pagesDomains = domains, isPagesBusy = false) }
    }
  }

  fun createPagesProject(name: String, productionBranch: String = "main") {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    if (name.isBlank()) return
    viewModelScope.launch {
      repository
        .createPagesProject(accountId, name, productionBranch)
        .onSuccess { proj ->
          _uiState.update { it.copy(statusMessage = "Created Pages project '${proj.name}'") }
          loadPagesProjects()
          selectPagesProject(proj)
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Create Pages failed") }
        }
    }
  }

  fun deletePagesProject(projectName: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .deletePagesProject(accountId, projectName)
        .onSuccess {
          _uiState.update { it.copy(selectedPagesProject = null, statusMessage = "Deleted Pages '$projectName'") }
          loadPagesProjects()
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Delete Pages failed") }
        }
    }
  }

  fun triggerPagesDeployment(projectName: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .triggerPagesDeployment(accountId, projectName)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Triggered Pages build for '$projectName'") }
          _uiState.value.selectedPagesProject?.let { selectPagesProject(it) }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Trigger failed") }
        }
    }
  }

  fun retryPagesDeployment(projectName: String, deploymentId: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .retryPagesDeployment(accountId, projectName, deploymentId)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Retrying deployment ${deploymentId.take(8)}") }
          _uiState.value.selectedPagesProject?.let { selectPagesProject(it) }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Retry failed") }
        }
    }
  }

  fun rollbackPagesDeployment(projectName: String, deploymentId: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .rollbackPagesDeployment(accountId, projectName, deploymentId)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Rolled back Pages to ${deploymentId.take(8)}") }
          _uiState.value.selectedPagesProject?.let { selectPagesProject(it) }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Rollback failed") }
        }
    }
  }

  fun fetchPagesLogs(projectName: String, deploymentId: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .getPagesDeploymentLogs(accountId, projectName, deploymentId)
        .onSuccess { res ->
          _uiState.update { it.copy(pagesBuildLogs = res.data) }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Logs unavailable") }
        }
    }
  }

  fun savePagesEnvVar(
    projectName: String,
    environment: String,
    key: String,
    value: String,
    isSecret: Boolean,
  ) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val proj = _uiState.value.selectedPagesProject ?: return
    if (key.isBlank()) return
    val existingVars =
      (if (environment == "production") proj.deploymentConfigs?.production?.envVars
      else proj.deploymentConfigs?.preview?.envVars).orEmpty()
    val updatedVars =
      existingVars +
        (key.trim() to
          CfPagesEnvVarValue(
            type = if (isSecret) "secret_text" else "plain_text",
            value = value,
          ))
    viewModelScope.launch {
      repository
        .updatePagesEnvVars(accountId, projectName, environment, updatedVars)
        .onSuccess { updatedProject ->
          _uiState.update {
            it.copy(selectedPagesProject = updatedProject, statusMessage = "Saved Pages $environment var '$key'")
          }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Pages env save failed") }
        }
    }
  }

  fun addPagesDomain(projectName: String, domain: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    if (domain.isBlank()) return
    viewModelScope.launch {
      repository
        .addPagesDomain(accountId, projectName, domain)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Added Pages domain $domain") }
          _uiState.value.selectedPagesProject?.let { selectPagesProject(it) }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Add domain failed") }
        }
    }
  }

  fun deletePagesDomain(projectName: String, domain: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .deletePagesDomain(accountId, projectName, domain)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Removed Pages domain $domain") }
          _uiState.value.selectedPagesProject?.let { selectPagesProject(it) }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Remove domain failed") }
        }
    }
  }

  fun stagePagesFolder(treeUri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isPagesBusy = true, pagesLastDirectDeployment = null) }
      val appContext = getApplication<Application>()
      DeviceFileHelper.readDirectoryTree(appContext, treeUri)
        .onSuccess { rawAssets ->
          val prepared = repository.preparePagesAssets(rawAssets)
          val folderName = DeviceFileHelper.resolveDisplayName(appContext, treeUri)
          _uiState.update {
            it.copy(
              isPagesBusy = false,
              pagesDirectUploadSourceLabel = "Folder: $folderName",
              pagesIsZipSource = false,
              pagesStagedUnstrippedZipAssets = emptyList(),
              pagesStagedPreparedAssets = prepared,
              pagesDirectUploadProgress = null,
              statusMessage = "Staged ${prepared.size} files from folder '$folderName'",
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isPagesBusy = false,
              statusMessage = err.localizedMessage ?: "Failed to read selected folder",
            )
          }
        }
    }
  }

  fun stagePagesZip(zipUri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isPagesBusy = true, pagesLastDirectDeployment = null) }
      val appContext = getApplication<Application>()
      DeviceFileHelper.readZipArchive(appContext, zipUri, stripCommonRootFolder = false)
        .onSuccess { rawUnstripped ->
          val effective =
            if (_uiState.value.pagesStripZipRootFolder) {
              DeviceFileHelper.stripCommonWrapperFolder(rawUnstripped)
            } else {
              rawUnstripped
            }
          val prepared = repository.preparePagesAssets(effective)
          val zipName = DeviceFileHelper.resolveDisplayName(appContext, zipUri)
          _uiState.update {
            it.copy(
              isPagesBusy = false,
              pagesDirectUploadSourceLabel = "ZIP: $zipName",
              pagesIsZipSource = true,
              pagesStagedUnstrippedZipAssets = rawUnstripped,
              pagesStagedPreparedAssets = prepared,
              pagesDirectUploadProgress = null,
              statusMessage = "Extracted ${prepared.size} files from '$zipName'",
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isPagesBusy = false,
              statusMessage = err.localizedMessage ?: "Failed to read ZIP archive",
            )
          }
        }
    }
  }

  fun stagePagesFiles(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isPagesBusy = true, pagesLastDirectDeployment = null) }
      val appContext = getApplication<Application>()
      DeviceFileHelper.readBinaryFiles(appContext, uris)
        .onSuccess { rawAssets ->
          val prepared = repository.preparePagesAssets(rawAssets)
          _uiState.update {
            it.copy(
              isPagesBusy = false,
              pagesDirectUploadSourceLabel = "${prepared.size} Selected File(s)",
              pagesIsZipSource = false,
              pagesStagedUnstrippedZipAssets = emptyList(),
              pagesStagedPreparedAssets = prepared,
              pagesDirectUploadProgress = null,
              statusMessage = "Staged ${prepared.size} files for Pages Direct Upload",
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isPagesBusy = false,
              statusMessage = err.localizedMessage ?: "Failed to read selected files",
            )
          }
        }
    }
  }

  fun stageCurrentWorkspaceForPages() {
    val ws = _uiState.value.workspace ?: return
    val rawAssets =
      ws.files
        .filterNot { it.isVirtualReadOnly }
        .map { RawPickedAsset(relativePath = it.path, bytes = it.content.toByteArray(Charsets.UTF_8)) }
    if (rawAssets.isEmpty()) {
      _uiState.update { it.copy(statusMessage = "No deployable files in current workspace") }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isPagesBusy = true, pagesLastDirectDeployment = null) }
      val prepared = repository.preparePagesAssets(rawAssets)
      _uiState.update {
        it.copy(
          isPagesBusy = false,
          pagesDirectUploadSourceLabel = "Workspace: ${ws.scriptName}",
          pagesIsZipSource = false,
          pagesStagedUnstrippedZipAssets = emptyList(),
          pagesStagedPreparedAssets = prepared,
          pagesDirectUploadProgress = null,
          statusMessage = "Staged ${prepared.size} workspace files for Pages Direct Upload",
        )
      }
    }
  }

  fun togglePagesZipStripRoot(strip: Boolean) {
    val unstripped = _uiState.value.pagesStagedUnstrippedZipAssets
    if (unstripped.isEmpty()) {
      _uiState.update { it.copy(pagesStripZipRootFolder = strip) }
      return
    }
    viewModelScope.launch {
      val effective =
        if (strip) DeviceFileHelper.stripCommonWrapperFolder(unstripped) else unstripped
      val prepared = repository.preparePagesAssets(effective)
      _uiState.update {
        it.copy(
          pagesStripZipRootFolder = strip,
          pagesStagedPreparedAssets = prepared,
        )
      }
    }
  }

  fun clearStagedPagesAssets() {
    _uiState.update {
      it.copy(
        pagesDirectUploadSourceLabel = null,
        pagesIsZipSource = false,
        pagesStagedUnstrippedZipAssets = emptyList(),
        pagesStagedPreparedAssets = emptyList(),
        pagesDirectUploadProgress = null,
        pagesLastDirectDeployment = null,
      )
    }
  }

  fun deployStagedPagesAssets(
    projectName: String,
    branch: String?,
    commitMessage: String?,
  ) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val prepared = _uiState.value.pagesStagedPreparedAssets
    if (prepared.isEmpty() || _uiState.value.isPagesDirectUploading) return
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isPagesDirectUploading = true,
          pagesLastDirectDeployment = null,
          pagesDirectUploadProgress =
            PagesDirectUploadProgress(
              phase = "Preparing manifest...",
              completedFiles = 0,
              totalFiles = prepared.size,
              uploadedBytes = 0L,
              totalBytes = prepared.sumOf { a -> a.bytes.size.toLong() },
            ),
        )
      }
      repository
        .directUploadPagesDeployment(
          accountId = accountId,
          projectName = projectName,
          preparedAssets = prepared,
          branch = branch,
          commitMessage = commitMessage,
          onProgress = { progress ->
            _uiState.update { it.copy(pagesDirectUploadProgress = progress) }
          },
        )
        .onSuccess { deployment ->
          _uiState.update {
            it.copy(
              isPagesDirectUploading = false,
              pagesDirectUploadProgress = null,
              pagesLastDirectDeployment = deployment,
              statusMessage = "Deployed ${prepared.size} assets to ${deployment.url ?: projectName}",
            )
          }
          _uiState.value.selectedPagesProject?.let { selectPagesProject(it) }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isPagesDirectUploading = false,
              pagesDirectUploadProgress = null,
              statusMessage = err.localizedMessage ?: "Pages Direct Upload failed",
            )
          }
        }
    }
  }

  // GitHub Authorization & Full IDE Integration
  fun startGitHubDeviceOAuth(
    clientIdOverride: String = "",
    enterIdeOnSuccess: Boolean = false,
    onVerificationUrlReady: (String) -> Unit,
  ) {
    gitHubOAuthJob?.cancel()
    _uiState.update {
      it.copy(
        isGitHubBusy = true,
        isWaitingGitHubOAuth = true,
        gitHubDeviceSession = null,
        gitHubError = null,
        authError = null,
      )
    }
    appendLog("INFO", "Initiating GitHub OAuth 2.0 Device Flow (RFC 8628)…")

    gitHubOAuthJob =
      viewModelScope.launch {
        val deviceSessionResult = GitHubOAuthManager.requestDeviceCode(clientIdOverride)
        val session =
          deviceSessionResult.getOrElse { err ->
            _uiState.update {
              it.copy(
                isGitHubBusy = false,
                isWaitingGitHubOAuth = false,
                gitHubDeviceSession = null,
                gitHubError = err.localizedMessage ?: "Failed to start GitHub OAuth Device Flow",
              )
            }
            return@launch
          }

        _uiState.update { it.copy(gitHubDeviceSession = session) }
        onVerificationUrlReady(session.verificationUri)

        GitHubOAuthManager.pollForAccessToken(session, clientIdOverride)
          .onSuccess { tokenResp ->
            val token = tokenResp.accessToken.orEmpty()
            gitHubRepository
              .verifyAndSaveSession(
                rawToken = token,
                method = GitHubAuthMethod.OAUTH_DEVICE,
                scopesOverride = tokenResp.scope.orEmpty(),
              )
              .onSuccess { info ->
                val rateLabel =
                  if (info.rateLimitRemaining != null && info.rateLimitLimit != null) {
                    "${info.rateLimitRemaining}/${info.rateLimitLimit} req"
                  } else null
                _uiState.update { state ->
                  state.copy(
                    gitHubUser = info.user,
                    gitHubRateLimitLabel = rateLabel,
                    isGitHubBusy = false,
                    isWaitingGitHubOAuth = false,
                    gitHubDeviceSession = null,
                    gitHubError = null,
                    isGitHubIdeOnlyMode = if (enterIdeOnSuccess && state.credentials == null) true else state.isGitHubIdeOnlyMode,
                    isLocalScratchpad = if (enterIdeOnSuccess && state.credentials == null) true else state.isLocalScratchpad,
                    sidebarSection = if (enterIdeOnSuccess) SidebarSection.GITHUB else state.sidebarSection,
                    statusMessage = "Authorized GitHub @${info.user.login} via OAuth 2.0",
                  )
                }
                appendLog("INFO", "Authorized GitHub via OAuth 2.0 (@${info.user.login}, scopes: ${info.scopes})")
                refreshGitHubReposInternal(token)
              }
              .onFailure { err ->
                _uiState.update {
                  it.copy(
                    isGitHubBusy = false,
                    isWaitingGitHubOAuth = false,
                    gitHubDeviceSession = null,
                    gitHubError = err.localizedMessage ?: "GitHub user verification failed",
                  )
                }
              }
          }
          .onFailure { err ->
            _uiState.update {
              it.copy(
                isGitHubBusy = false,
                isWaitingGitHubOAuth = false,
                gitHubDeviceSession = null,
                gitHubError = err.localizedMessage ?: "GitHub OAuth Device authorization failed",
              )
            }
          }
      }
  }

  fun cancelGitHubDeviceOAuth() {
    gitHubOAuthJob?.cancel()
    gitHubOAuthJob = null
    _uiState.update {
      it.copy(
        isGitHubBusy = false,
        isWaitingGitHubOAuth = false,
        gitHubDeviceSession = null,
      )
    }
  }

  fun connectGitHubToken(token: String, enterIdeOnSuccess: Boolean = false) {
    viewModelScope.launch {
      _uiState.update { it.copy(isGitHubBusy = true, gitHubError = null) }
      gitHubRepository
        .verifyAndSaveSession(token, GitHubAuthMethod.PAT)
        .onSuccess { info ->
          val rateLabel =
            if (info.rateLimitRemaining != null && info.rateLimitLimit != null) {
              "${info.rateLimitRemaining}/${info.rateLimitLimit} req"
            } else null
          _uiState.update { state ->
            state.copy(
              gitHubUser = info.user,
              gitHubRateLimitLabel = rateLabel,
              isGitHubBusy = false,
              isGitHubIdeOnlyMode = if (enterIdeOnSuccess && state.credentials == null) true else state.isGitHubIdeOnlyMode,
              isLocalScratchpad = if (enterIdeOnSuccess && state.credentials == null) true else state.isLocalScratchpad,
              sidebarSection = if (enterIdeOnSuccess) SidebarSection.GITHUB else state.sidebarSection,
              statusMessage = "Connected GitHub @${info.user.login}",
            )
          }
          appendLog("INFO", "Connected GitHub account @${info.user.login} (${info.scopes})")
          refreshGitHubReposInternal(token.trim())
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isGitHubBusy = false,
              gitHubError = err.localizedMessage ?: "GitHub token verification failed",
            )
          }
        }
    }
  }

  fun disconnectGitHub() {
    gitHubOAuthJob?.cancel()
    viewModelScope.launch {
      gitHubRepository.signOut()
      _uiState.update {
        it.copy(
          gitHubCreds = null,
          gitHubUser = null,
          gitHubRateLimitLabel = null,
          gitHubDeviceSession = null,
          isWaitingGitHubOAuth = false,
          gitHubRepos = emptyList(),
          selectedGitHubRepo = null,
          gitHubBranches = emptyList(),
          gitHubTreeFiles = emptyList(),
          gitHubCommits = emptyList(),
          selectedCommitDetail = null,
          gitHubWorkflows = emptyList(),
          gitHubWorkflowRuns = emptyList(),
          gitHubPullRequests = emptyList(),
          selectedPrNumber = null,
          selectedPrFiles = emptyList(),
          gitHubIssues = emptyList(),
          gitHubError = null,
        )
      }
    }
  }

  fun refreshGitHubRepos() {
    val token = _uiState.value.gitHubCreds?.token ?: return
    viewModelScope.launch { refreshGitHubReposInternal(token) }
  }

  private suspend fun refreshGitHubReposInternal(token: String) {
    _uiState.update { it.copy(isGitHubBusy = true, gitHubError = null) }
    gitHubRepository.fetchSessionInfo(token).onSuccess { info ->
      val rateLabel =
        if (info.rateLimitRemaining != null && info.rateLimitLimit != null) {
          "${info.rateLimitRemaining}/${info.rateLimitLimit} req"
        } else null
      _uiState.update { it.copy(gitHubUser = info.user, gitHubRateLimitLabel = rateLabel) }
    }
    gitHubRepository
      .listRepos(token)
      .onSuccess { repos ->
        _uiState.update { it.copy(gitHubRepos = repos, isGitHubBusy = false) }
      }
      .onFailure { err ->
        _uiState.update {
          it.copy(
            isGitHubBusy = false,
            gitHubError = err.localizedMessage ?: "Failed to load GitHub repositories",
          )
        }
      }
  }

  fun selectGitHubRepo(repo: GitHubRepo?) {
    if (repo == null) {
      _uiState.update {
        it.copy(
          selectedGitHubRepo = null,
          gitHubBranches = emptyList(),
          gitHubTreeFiles = emptyList(),
          gitHubCommits = emptyList(),
          selectedCommitDetail = null,
          gitHubWorkflows = emptyList(),
          gitHubWorkflowRuns = emptyList(),
          gitHubPullRequests = emptyList(),
          selectedPrNumber = null,
          selectedPrFiles = emptyList(),
          gitHubIssues = emptyList(),
        )
      }
      return
    }
    val token = _uiState.value.gitHubCreds?.token
    val defaultBranch = repo.defaultBranch.ifBlank { "main" }
    _uiState.update {
      it.copy(
        selectedGitHubRepo = repo,
        selectedGitHubBranch = defaultBranch,
        selectedCommitDetail = null,
        selectedPrNumber = null,
        selectedPrFiles = emptyList(),
        isGitHubBusy = true,
      )
    }
    viewModelScope.launch {
      val owner = repo.owner.login
      val branches = gitHubRepository.listBranches(token, owner, repo.name).getOrDefault(emptyList())
      val runs = if (!token.isNullOrBlank()) gitHubRepository.listWorkflowRuns(token, owner, repo.name).getOrDefault(emptyList()) else emptyList()
      val workflows = if (!token.isNullOrBlank()) gitHubRepository.listWorkflows(token, owner, repo.name).getOrDefault(emptyList()) else emptyList()
      val prs = gitHubRepository.listPullRequests(token, owner, repo.name, "open").getOrDefault(emptyList())
      val issues = gitHubRepository.listIssues(token, owner, repo.name, "open").getOrDefault(emptyList())
      _uiState.update {
        it.copy(
          gitHubBranches = branches,
          gitHubWorkflowRuns = runs,
          gitHubWorkflows = workflows,
          gitHubPullRequests = prs,
          gitHubIssues = issues,
        )
      }
      loadGitHubBranchContents(token, owner, repo.name, defaultBranch)
    }
  }

  fun selectGitHubBranch(branch: String) {
    val token = _uiState.value.gitHubCreds?.token
    val repo = _uiState.value.selectedGitHubRepo ?: return
    _uiState.update { it.copy(selectedGitHubBranch = branch, isGitHubBusy = true) }
    viewModelScope.launch {
      loadGitHubBranchContents(token, repo.owner.login, repo.name, branch)
    }
  }

  private suspend fun loadGitHubBranchContents(
    token: String?,
    owner: String,
    repoName: String,
    branch: String,
  ) {
    val tree = gitHubRepository.listRepoFiles(token, owner, repoName, branch).getOrDefault(emptyList())
    val commits = gitHubRepository.listCommits(token, owner, repoName, branch).getOrDefault(emptyList())
    val prs = gitHubRepository.listPullRequests(token, owner, repoName, "open").getOrDefault(_uiState.value.gitHubPullRequests)
    val issues = gitHubRepository.listIssues(token, owner, repoName, "open").getOrDefault(_uiState.value.gitHubIssues)
    val runs = if (!token.isNullOrBlank()) gitHubRepository.listWorkflowRuns(token, owner, repoName).getOrDefault(_uiState.value.gitHubWorkflowRuns) else _uiState.value.gitHubWorkflowRuns
    val workflows = if (!token.isNullOrBlank()) gitHubRepository.listWorkflows(token, owner, repoName).getOrDefault(_uiState.value.gitHubWorkflows) else _uiState.value.gitHubWorkflows
    _uiState.update {
      it.copy(
        gitHubTreeFiles = tree,
        gitHubCommits = commits,
        gitHubPullRequests = prs,
        gitHubIssues = issues,
        gitHubWorkflowRuns = runs,
        gitHubWorkflows = workflows,
        isGitHubBusy = false,
      )
    }
  }

  fun inspectGitHubCommit(sha: String) {
    if (_uiState.value.selectedCommitDetail?.sha == sha) {
      _uiState.update { it.copy(selectedCommitDetail = null) }
      return
    }
    val token = _uiState.value.gitHubCreds?.token
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    viewModelScope.launch {
      gitHubRepository.getCommitDetail(token, owner, repoName, sha)
        .onSuccess { detail -> _uiState.update { it.copy(selectedCommitDetail = detail) } }
        .onFailure { err -> _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Failed to inspect commit") } }
    }
  }

  fun inspectPullRequest(prNumber: Int?) {
    if (prNumber == null || _uiState.value.selectedPrNumber == prNumber) {
      _uiState.update { it.copy(selectedPrNumber = null, selectedPrFiles = emptyList()) }
      return
    }
    val token = _uiState.value.gitHubCreds?.token
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    _uiState.update { it.copy(selectedPrNumber = prNumber, selectedPrFiles = emptyList(), isGitHubBusy = true) }
    viewModelScope.launch {
      gitHubRepository.listPullRequestFiles(token, owner, repoName, prNumber)
        .onSuccess { files -> _uiState.update { it.copy(selectedPrFiles = files, isGitHubBusy = false) } }
        .onFailure { err -> _uiState.update { it.copy(isGitHubBusy = false, statusMessage = err.localizedMessage ?: "Failed to load PR diff") } }
    }
  }

  fun createPullRequest(
    title: String,
    body: String,
    headBranch: String,
    baseBranch: String,
  ) {
    val token = _uiState.value.gitHubCreds?.token ?: return
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    if (title.isBlank() || headBranch.isBlank() || baseBranch.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isGitHubBusy = true, gitHubError = null) }
      gitHubRepository.createPullRequest(token, owner, repoName, title, body, headBranch, baseBranch)
        .onSuccess { pr ->
          val updated = listOf(pr) + _uiState.value.gitHubPullRequests
          _uiState.update {
            it.copy(
              isGitHubBusy = false,
              gitHubPullRequests = updated,
              statusMessage = "Opened PR #${pr.number}: ${pr.title}",
            )
          }
          appendLog("INFO", "Created GitHub PR #${pr.number} ($headBranch -> $baseBranch)")
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isGitHubBusy = false,
              gitHubError = err.localizedMessage ?: "Create PR failed",
            )
          }
        }
    }
  }

  fun mergePullRequest(prNumber: Int, mergeMethod: String = "squash") {
    val token = _uiState.value.gitHubCreds?.token ?: return
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isGitHubBusy = true, gitHubError = null) }
      gitHubRepository.mergePullRequest(token, owner, repoName, prNumber, mergeMethod)
        .onSuccess {
          val remaining = _uiState.value.gitHubPullRequests.filterNot { it.number == prNumber }
          _uiState.update {
            it.copy(
              isGitHubBusy = false,
              gitHubPullRequests = remaining,
              selectedPrNumber = null,
              selectedPrFiles = emptyList(),
              statusMessage = "Merged PR #$prNumber ($mergeMethod)",
            )
          }
          appendLog("DEPLOY", "Merged Pull Request #$prNumber via $mergeMethod")
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(isGitHubBusy = false, gitHubError = err.localizedMessage ?: "Merge PR failed")
          }
        }
    }
  }

  fun createGitHubIssue(title: String, body: String) {
    val token = _uiState.value.gitHubCreds?.token ?: return
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    if (title.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isGitHubBusy = true, gitHubError = null) }
      gitHubRepository.createIssue(token, owner, repoName, title, body)
        .onSuccess { issue ->
          _uiState.update {
            it.copy(
              isGitHubBusy = false,
              gitHubIssues = listOf(issue) + it.gitHubIssues,
              statusMessage = "Created Issue #${issue.number}",
            )
          }
          appendLog("INFO", "Created Issue #${issue.number}: ${issue.title}")
        }
        .onFailure { err ->
          _uiState.update { it.copy(isGitHubBusy = false, gitHubError = err.localizedMessage ?: "Create issue failed") }
        }
    }
  }

  fun toggleGitHubIssueState(issue: GitHubIssue) {
    val token = _uiState.value.gitHubCreds?.token ?: return
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    val nextState = if (issue.state == "open") "closed" else "open"
    viewModelScope.launch {
      gitHubRepository.updateIssueState(token, owner, repoName, issue.number, nextState)
        .onSuccess { updated ->
          _uiState.update { state ->
            state.copy(
              gitHubIssues = state.gitHubIssues.map { if (it.number == issue.number) updated else it },
              statusMessage = "Issue #${issue.number} marked $nextState",
            )
          }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Failed updating issue") }
        }
    }
  }

  fun commentOnGitHubIssue(issueNumber: Int, comment: String) {
    val token = _uiState.value.gitHubCreds?.token ?: return
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    if (comment.isBlank()) return
    viewModelScope.launch {
      gitHubRepository.commentOnIssue(token, owner, repoName, issueNumber, comment)
        .onSuccess {
          _uiState.update { state ->
            state.copy(
              gitHubIssues =
                state.gitHubIssues.map {
                  if (it.number == issueNumber) it.copy(comments = it.comments + 1) else it
                },
              statusMessage = "Commented on #$issueNumber",
            )
          }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Comment failed") }
        }
    }
  }

  fun dispatchGitHubWorkflow(workflowId: Long) {
    val token = _uiState.value.gitHubCreds?.token ?: return
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    val branch = linked?.branch ?: _uiState.value.selectedGitHubBranch
    viewModelScope.launch {
      gitHubRepository.dispatchWorkflow(token, owner, repoName, workflowId, branch)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Dispatched workflow on $branch") }
          appendLog("INFO", "Triggered GitHub Actions workflow #$workflowId on $branch")
          val runs = gitHubRepository.listWorkflowRuns(token, owner, repoName).getOrDefault(_uiState.value.gitHubWorkflowRuns)
          _uiState.update { it.copy(gitHubWorkflowRuns = runs) }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Dispatch failed (ensure workflow_dispatch trigger)") }
        }
    }
  }

  fun rerunGitHubWorkflowRun(runId: Long) {
    val token = _uiState.value.gitHubCreds?.token ?: return
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    viewModelScope.launch {
      gitHubRepository.rerunWorkflow(token, owner, repoName, runId)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Re-running workflow #$runId") }
          val runs = gitHubRepository.listWorkflowRuns(token, owner, repoName).getOrDefault(_uiState.value.gitHubWorkflowRuns)
          _uiState.update { it.copy(gitHubWorkflowRuns = runs) }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Re-run failed") }
        }
    }
  }

  fun createGitHubBranch(newBranchName: String) {
    val token = _uiState.value.gitHubCreds?.token ?: return
    val repo = _uiState.value.selectedGitHubRepo ?: return
    val baseBranch = _uiState.value.gitHubBranches.firstOrNull { it.name == _uiState.value.selectedGitHubBranch }
      ?: _uiState.value.gitHubBranches.firstOrNull()
      ?: return
    if (newBranchName.isBlank()) return
    viewModelScope.launch {
      gitHubRepository
        .createBranch(token, repo.owner.login, repo.name, newBranchName, baseBranch.commit.sha)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Created branch '$newBranchName'") }
          selectGitHubRepo(repo)
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Create branch failed") }
        }
    }
  }

  fun setGitCommitMessage(msg: String) {
    _uiState.update { it.copy(gitCommitMessageInput = msg) }
  }

  fun toggleGitFileStaged(path: String) {
    _uiState.update { state ->
      val nextExcluded =
        if (path in state.gitUnstagedExcludedPaths) state.gitUnstagedExcludedPaths - path
        else state.gitUnstagedExcludedPaths + path
      state.copy(gitUnstagedExcludedPaths = nextExcluded)
    }
  }

  fun stageAllGitChanges() {
    _uiState.update { it.copy(gitUnstagedExcludedPaths = emptySet()) }
  }

  fun unstageAllGitChanges() {
    val allPaths = _uiState.value.gitChangedFiles.map { it.path }.toSet()
    _uiState.update { it.copy(gitUnstagedExcludedPaths = allPaths) }
  }

  fun discardGitFileChange(path: String) {
    val ws = _uiState.value.workspace ?: return
    val baseContent = _uiState.value.gitWorkingBaseline[path]
    val editable = ws.files.filterNot { it.isVirtualReadOnly }.toMutableList()
    val virtual = ws.files.filter { it.isVirtualReadOnly }
    if (baseContent == null) {
      // Newly added file -> remove from workspace
      editable.removeAll { it.path == path }
    } else {
      val idx = editable.indexOfFirst { it.path == path }
      if (idx >= 0) {
        editable[idx] = editable[idx].copy(content = baseContent)
      } else {
        editable.add(
          WorkerFile(
            path = path,
            content = baseContent,
            originalContent = baseContent,
            contentType = MultipartWorkerParser.inferModuleMimeType(path),
            isEntryPoint = path == ws.entryModulePath,
          ),
        )
      }
    }
    val nextFiles = editable + virtual
    _uiState.update { state ->
      val nextTabs = if (baseContent == null) state.openTabPaths.filterNot { it == path } else state.openTabPaths
      val nextActive =
        if (state.activeFilePath == path && baseContent == null) nextTabs.lastOrNull() else state.activeFilePath
      state.copy(
        workspace = ws.copy(files = nextFiles, hasModuleSetChanged = true),
        openTabPaths = nextTabs,
        activeFilePath = nextActive,
        editorFieldValues =
          if (baseContent == null) state.editorFieldValues - path
          else state.editorFieldValues + (path to TextFieldValue(baseContent)),
        gitUnstagedExcludedPaths = state.gitUnstagedExcludedPaths - path,
        diagnostics = nextFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
        statusMessage = "Discarded changes in $path",
      )
    }
  }

  fun cloneGitHubRepository(
    repoUrlOrSlug: String,
    branchOverride: String? = null,
    mergeIntoCurrentWorker: Boolean = false,
    targetWorkerName: String? = null,
  ) {
    val parsed = gitHubRepository.parseRepoReference(repoUrlOrSlug)
    if (parsed == null) {
      _uiState.update {
        it.copy(gitHubError = "Enter a valid GitHub URL or owner/repo (e.g. cloudflare/workers-sdk)")
      }
      return
    }
    val token = _uiState.value.gitHubCreds?.token
    val effectiveBranch = branchOverride?.trim()?.takeIf { it.isNotEmpty() } ?: parsed.branch

    viewModelScope.launch {
      _uiState.update { it.copy(isGitHubBusy = true, gitHubError = null, gitPushStatusMessage = null) }
      gitHubRepository
        .cloneRepository(token, parsed.owner, parsed.repo, effectiveBranch)
        .onSuccess { bundle ->
          val baselineMap = bundle.files.associate { it.path to it.content }
          val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
          val linkedState =
            LinkedGitRepoState(
              owner = bundle.repo.owner.login,
              repoName = bundle.repo.name,
              fullName = bundle.repo.fullName,
              branch = bundle.branch,
              headCommitSha = bundle.headCommitSha,
              baselineFiles = baselineMap,
              clonedAtLabel = ts,
            )

          val existingWs = _uiState.value.workspace
          if (mergeIntoCurrentWorker && existingWs != null) {
            val editable = existingWs.files.filterNot { it.isVirtualReadOnly }.toMutableList()
            val virtual = existingWs.files.filter { it.isVirtualReadOnly }
            bundle.files.forEach { cf ->
              val idx = editable.indexOfFirst { it.path == cf.path }
              if (idx >= 0) editable[idx] = cf.copy(isEntryPoint = editable[idx].isEntryPoint)
              else editable.add(cf.copy(isEntryPoint = false))
            }
            val nextFiles = editable + virtual
            val firstCloned = bundle.detectedEntryPath
            _uiState.update { state ->
              state.copy(
                isGitHubBusy = false,
                selectedGitHubRepo = bundle.repo,
                selectedGitHubBranch = bundle.branch,
                linkedGitRepo = linkedState,
                gitWorkingBaseline = baselineMap,
                gitUnstagedExcludedPaths = emptySet(),
                localGitCommits = emptyList(),
                workspace = existingWs.copy(files = nextFiles, isEsModuleFormat = true, hasModuleSetChanged = true),
                openTabPaths = (state.openTabPaths + firstCloned).distinct(),
                activeFilePath = firstCloned,
                editorFieldValues =
                  state.editorFieldValues + bundle.files.associate { it.path to TextFieldValue(it.content) },
                diagnostics = nextFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
                statusMessage = "Cloned ${bundle.files.size} files from ${bundle.repo.fullName}@${bundle.branch} into ${existingWs.scriptName}",
              )
            }
          } else {
            val workerName =
              targetWorkerName?.trim()?.takeIf { it.isNotBlank() } ?: bundle.detectedWorkerName
            val nextWs =
              WorkerWorkspace(
                scriptName = workerName,
                isEsModuleFormat = true,
                entryModulePath = bundle.detectedEntryPath,
                compatibilityDate = bundle.detectedCompatibilityDate,
                files = bundle.files,
                bindings = emptyList(),
                routes = emptyList(),
                workersDevEnabled = false,
                accountSubdomain = _uiState.value.workspace?.accountSubdomain.orEmpty(),
              )
            val initialTabs =
              listOfNotNull(
                bundle.detectedEntryPath,
                bundle.files.firstOrNull { it.path.equals("wrangler.toml", true) }?.path,
              ).distinct()

            _uiState.update { state ->
              state.copy(
                isGitHubBusy = false,
                isLocalScratchpad = state.credentials == null,
                selectedGitHubRepo = bundle.repo,
                selectedGitHubBranch = bundle.branch,
                linkedGitRepo = linkedState,
                gitWorkingBaseline = baselineMap,
                gitUnstagedExcludedPaths = emptySet(),
                localGitCommits = emptyList(),
                workspace = nextWs,
                openTabPaths = initialTabs,
                activeFilePath = bundle.detectedEntryPath,
                tabNavHistory = listOf(bundle.detectedEntryPath),
                tabNavIndex = 0,
                editorFieldValues = bundle.files.associate { it.path to TextFieldValue(it.content) },
                diagnostics = bundle.files.flatMap { CodeAnalyzer.analyzeFile(it) },
                statusMessage = "Cloned ${bundle.repo.fullName} (${bundle.files.size} files) as Worker '$workerName'",
              )
            }
          }

          val branches = gitHubRepository.listBranches(token, bundle.repo.owner.login, bundle.repo.name).getOrDefault(emptyList())
          val commits = gitHubRepository.listCommits(token, bundle.repo.owner.login, bundle.repo.name, bundle.branch).getOrDefault(emptyList())
          val tree = gitHubRepository.listRepoFiles(token, bundle.repo.owner.login, bundle.repo.name, bundle.branch).getOrDefault(emptyList())
          val prs = gitHubRepository.listPullRequests(token, bundle.repo.owner.login, bundle.repo.name, "open").getOrDefault(emptyList())
          val issues = gitHubRepository.listIssues(token, bundle.repo.owner.login, bundle.repo.name, "open").getOrDefault(emptyList())
          val runs =
            if (!token.isNullOrBlank()) {
              gitHubRepository.listWorkflowRuns(token, bundle.repo.owner.login, bundle.repo.name).getOrDefault(emptyList())
            } else emptyList()
          val workflows =
            if (!token.isNullOrBlank()) {
              gitHubRepository.listWorkflows(token, bundle.repo.owner.login, bundle.repo.name).getOrDefault(emptyList())
            } else emptyList()

          _uiState.update { state ->
            state.copy(
              gitHubBranches = branches,
              gitHubCommits = commits,
              gitHubTreeFiles = tree,
              gitHubPullRequests = prs,
              gitHubIssues = issues,
              gitHubWorkflowRuns = runs,
              gitHubWorkflows = workflows,
              gitPushStatusMessage = "Cloned ${bundle.repo.fullName}@${bundle.branch}",
            )
          }
          appendLog("INFO", "Cloned ${bundle.repo.fullName}@${bundle.branch} (${bundle.files.size} files)")
          terminalEngine.syncWorkspaceToDisk(_uiState.value.workspace)
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isGitHubBusy = false,
              gitHubError = err.localizedMessage ?: "Failed to clone repository",
              statusMessage = err.localizedMessage ?: "Clone failed",
            )
          }
        }
    }
  }

  fun createGitHubRepoAndLink(
    repoName: String,
    description: String,
    isPrivate: Boolean,
  ) {
    val token = _uiState.value.gitHubCreds?.token ?: return
    val cleanName = repoName.trim()
    if (cleanName.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isGitHubBusy = true, gitHubError = null) }
      gitHubRepository
        .createRepository(token, cleanName, description, isPrivate, autoInit = true)
        .onSuccess { created ->
          val branch = created.defaultBranch.ifBlank { "main" }
          val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
          val linked =
            LinkedGitRepoState(
              owner = created.owner.login,
              repoName = created.name,
              fullName = created.fullName,
              branch = branch,
              headCommitSha = "",
              baselineFiles = emptyMap(),
              clonedAtLabel = ts,
            )
          _uiState.update { state ->
            state.copy(
              isGitHubBusy = false,
              gitHubRepos = listOf(created) + state.gitHubRepos,
              selectedGitHubRepo = created,
              selectedGitHubBranch = branch,
              linkedGitRepo = linked,
              gitWorkingBaseline = emptyMap(),
              gitUnstagedExcludedPaths = emptySet(),
              localGitCommits = emptyList(),
              statusMessage = "Created & linked GitHub repo ${created.fullName}",
            )
          }
          appendLog("INFO", "Created GitHub repo ${created.fullName}")
          selectGitHubRepo(created)
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isGitHubBusy = false,
              gitHubError = err.localizedMessage ?: "Failed creating GitHub repository",
            )
          }
        }
    }
  }

  fun linkCurrentWorkerToSelectedGitHubRepo() {
    val repo = _uiState.value.selectedGitHubRepo ?: return
    val branch = _uiState.value.selectedGitHubBranch
    val headSha = _uiState.value.gitHubBranches.firstOrNull { it.name == branch }?.commit?.sha.orEmpty()
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    _uiState.update { state ->
      state.copy(
        linkedGitRepo =
          LinkedGitRepoState(
            owner = repo.owner.login,
            repoName = repo.name,
            fullName = repo.fullName,
            branch = branch,
            headCommitSha = headSha,
            baselineFiles = state.gitWorkingBaseline,
            clonedAtLabel = ts,
          ),
        statusMessage = "Linked '${state.workspace?.scriptName ?: "workspace"}' to ${repo.fullName}@$branch",
      )
    }
  }

  fun renameActiveWorkerTarget(newScriptName: String) {
    val ws = _uiState.value.workspace ?: return
    val clean = newScriptName.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "-")
    if (clean.isBlank()) return
    _uiState.update { state ->
      val nextWs = ws.copy(scriptName = clean)
      terminalEngine.syncWorkspaceToDisk(nextWs)
      state.copy(
        workspace = nextWs,
        statusMessage = "Worker target set to '$clean'",
      )
    }
  }

  fun commitStagedChangesLocally(commitMessageOverride: String? = null) {
    val state = _uiState.value
    val changed = state.gitChangedFiles.filter { it.isStaged }
    if (changed.isEmpty()) {
      _uiState.update { it.copy(statusMessage = "No staged changes to commit.") }
      return
    }
    val msg =
      (commitMessageOverride ?: state.gitCommitMessageInput)
        .trim()
        .ifBlank { "Update ${changed.size} file(s) via EdgeCode" }

    val updatedMap =
      changed
        .filter { it.status == GitFileChangeStatus.ADDED || it.status == GitFileChangeStatus.MODIFIED }
        .associate { it.path to it.currentContent }
    val deletedSet =
      changed
        .filter { it.status == GitFileChangeStatus.DELETED }
        .map { it.path }
        .toSet()

    val nextBaseline = state.gitWorkingBaseline.toMutableMap()
    updatedMap.forEach { (k, v) -> nextBaseline[k] = v }
    deletedSet.forEach { k -> nextBaseline.remove(k) }

    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    val author = state.gitHubCreds?.login ?: state.gitHubUser?.login ?: "edgecode-dev"
    val localCommit =
      LocalGitCommit(
        id = "local-${System.currentTimeMillis().toString().takeLast(6)}",
        message = msg,
        timestamp = ts,
        author = author,
        updatedFiles = updatedMap,
        deletedPaths = deletedSet,
        changedCount = changed.size,
      )

    _uiState.update { s ->
      s.copy(
        gitWorkingBaseline = nextBaseline,
        localGitCommits = s.localGitCommits + localCommit,
        gitCommitMessageInput = "",
        gitPushStatusMessage = "Committed locally (${localCommit.id}): $msg",
        statusMessage = "Created local commit '${msg.take(36)}' (${changed.size} file(s))",
      )
    }
    appendLog("INFO", "Git local commit [${localCommit.id}]: $msg (${changed.size} files)")
  }

  fun pushQueuedAndActiveChangesToGitHub(commitMessageOverride: String? = null) {
    val state = _uiState.value
    val token = state.gitHubCreds?.token
    if (token.isNullOrBlank()) {
      _uiState.update { it.copy(gitHubError = "Connect your GitHub Personal Access Token first to push.") }
      return
    }
    val linked = state.linkedGitRepo
    val selectedRepo = state.selectedGitHubRepo
    val owner = linked?.owner ?: selectedRepo?.owner?.login ?: return
    val repoName = linked?.repoName ?: selectedRepo?.name ?: return
    val fullName = linked?.fullName ?: selectedRepo?.fullName ?: "$owner/$repoName"
    val branch = linked?.branch ?: state.selectedGitHubBranch

    val queuedCommits = state.localGitCommits.toMutableList()
    val stagedNow = state.gitChangedFiles.filter { it.isStaged }

    if (stagedNow.isNotEmpty()) {
      val msg =
        (commitMessageOverride ?: state.gitCommitMessageInput)
          .trim()
          .ifBlank { "Update Worker via EdgeCode" }
      val updatedMap =
        stagedNow
          .filter { it.status == GitFileChangeStatus.ADDED || it.status == GitFileChangeStatus.MODIFIED }
          .associate { it.path to it.currentContent }
      val deletedSet =
        stagedNow
          .filter { it.status == GitFileChangeStatus.DELETED }
          .map { it.path }
          .toSet()
      val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
      queuedCommits.add(
        LocalGitCommit(
          id = "staged",
          message = msg,
          timestamp = ts,
          author = state.gitHubCreds.login,
          updatedFiles = updatedMap,
          deletedPaths = deletedSet,
          changedCount = stagedNow.size,
        ),
      )
    } else if (queuedCommits.isEmpty()) {
      // Fallback: if no baseline diff tracked yet, push all editable files cleanly
      val ws = state.workspace ?: return
      val msg =
        (commitMessageOverride ?: state.gitCommitMessageInput)
          .trim()
          .ifBlank { "Sync Worker '${ws.scriptName}' from EdgeCode" }
      val allEditable = ws.files.filterNot { it.isVirtualReadOnly }.associate { it.path to it.content }
      if (allEditable.isEmpty()) {
        _uiState.update { it.copy(statusMessage = "No editable files to push.") }
        return
      }
      queuedCommits.add(
        LocalGitCommit(
          id = "sync",
          message = msg,
          timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
          author = state.gitHubCreds.login,
          updatedFiles = allEditable,
          deletedPaths = emptySet(),
          changedCount = allEditable.size,
        ),
      )
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isGitHubBusy = true, gitHubError = null) }
      var lastCommitSha = linked?.headCommitSha.orEmpty()
      var pushedCommitsCount = 0
      var pushedFilesTotal = 0

      for (commit in queuedCommits) {
        val res =
          gitHubRepository.pushAtomicChanges(
            token = token,
            owner = owner,
            repo = repoName,
            branch = branch,
            commitMessage = commit.message,
            updatedFiles = commit.updatedFiles,
            deletedPaths = commit.deletedPaths,
          )
        if (res.isFailure) {
          val errMsg = res.exceptionOrNull()?.localizedMessage ?: "GitHub push failed"
          _uiState.update {
            it.copy(
              isGitHubBusy = false,
              gitHubError = errMsg,
              statusMessage = errMsg,
            )
          }
          return@launch
        }
        lastCommitSha = res.getOrDefault(lastCommitSha)
        pushedCommitsCount++
        pushedFilesTotal += commit.changedCount
      }

      val nextBaseline =
        _uiState.value.workspace?.files?.filterNot { it.isVirtualReadOnly }?.associate { it.path to it.content }
          ?: _uiState.value.gitWorkingBaseline

      _uiState.update { s ->
        s.copy(
          isGitHubBusy = false,
          gitWorkingBaseline = nextBaseline,
          gitUnstagedExcludedPaths = emptySet(),
          localGitCommits = emptyList(),
          gitCommitMessageInput = "",
          linkedGitRepo =
            (s.linkedGitRepo ?: LinkedGitRepoState(
              owner = owner,
              repoName = repoName,
              fullName = fullName,
              branch = branch,
              headCommitSha = lastCommitSha,
              baselineFiles = nextBaseline,
              clonedAtLabel = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
            )).copy(headCommitSha = lastCommitSha, baselineFiles = nextBaseline),
          gitPushStatusMessage = "Pushed $pushedCommitsCount commit(s) ($pushedFilesTotal file(s)) -> ${lastCommitSha.take(7)}",
          statusMessage = "Pushed $pushedCommitsCount commit(s) to $fullName ($branch)",
        )
      }
      appendLog("DEPLOY", "Git pushed $pushedCommitsCount commit(s) to $fullName:$branch (${lastCommitSha.take(7)})")
      loadGitHubBranchContents(token, owner, repoName, branch)
    }
  }

  fun syncPullLinkedGitRepo() {
    val linked = _uiState.value.linkedGitRepo ?: return
    cloneGitHubRepository(
      repoUrlOrSlug = linked.fullName,
      branchOverride = linked.branch,
      mergeIntoCurrentWorker = true,
    )
  }

  fun pullGitHubPathsIntoWorker(paths: List<String>) {
    val token = _uiState.value.gitHubCreds?.token
    val repo = _uiState.value.selectedGitHubRepo ?: return
    if (paths.isEmpty()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isGitHubBusy = true) }
      gitHubRepository
        .pullSelectedOrWorkerFiles(token, repo.owner.login, repo.name, _uiState.value.selectedGitHubBranch, paths)
        .onSuccess { pulledFiles ->
          if (pulledFiles.isEmpty()) {
            _uiState.update { it.copy(isGitHubBusy = false, statusMessage = "No readable files found") }
            return@onSuccess
          }
          val existingWs = _uiState.value.workspace
          if (existingWs == null) {
            val entry = pulledFiles.firstOrNull { it.isEntryPoint }?.path ?: pulledFiles.first().path
            val finalFiles = pulledFiles.map { it.copy(isEntryPoint = it.path == entry) }
            val baselineMap = finalFiles.associate { it.path to it.content }
            val ws =
              WorkerWorkspace(
                scriptName = repo.name,
                isEsModuleFormat = true,
                entryModulePath = entry,
                compatibilityDate = "2024-11-01",
                files = finalFiles,
                bindings = emptyList(),
                routes = emptyList(),
                workersDevEnabled = false,
                accountSubdomain = "",
              )
            _uiState.update {
              it.copy(
                isGitHubBusy = false,
                isLocalScratchpad = it.credentials == null,
                workspace = ws,
                gitWorkingBaseline = baselineMap,
                linkedGitRepo =
                  LinkedGitRepoState(
                    owner = repo.owner.login,
                    repoName = repo.name,
                    fullName = repo.fullName,
                    branch = it.selectedGitHubBranch,
                    headCommitSha = it.gitHubBranches.firstOrNull { b -> b.name == it.selectedGitHubBranch }?.commit?.sha.orEmpty(),
                    baselineFiles = baselineMap,
                    clonedAtLabel = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                  ),
                openTabPaths = finalFiles.map { f -> f.path },
                activeFilePath = entry,
                tabNavHistory = listOf(entry),
                tabNavIndex = 0,
                editorFieldValues = finalFiles.associate { f -> f.path to TextFieldValue(f.content) },
                diagnostics = finalFiles.flatMap { f -> CodeAnalyzer.analyzeFile(f) },
                statusMessage = "Pulled ${finalFiles.size} file(s) from ${repo.fullName}",
              )
            }
          } else {
            val currentEditable = existingWs.files.filterNot { it.isVirtualReadOnly }.toMutableList()
            val virtual = existingWs.files.filter { it.isVirtualReadOnly }
            pulledFiles.forEach { pf ->
              val idx = currentEditable.indexOfFirst { it.path == pf.path }
              if (idx >= 0) currentEditable[idx] = pf.copy(isEntryPoint = currentEditable[idx].isEntryPoint)
              else currentEditable.add(pf.copy(isEntryPoint = false))
            }
            val nextFiles = currentEditable + virtual
            val firstPulled = pulledFiles.first().path
            val updatedBaseline = _uiState.value.gitWorkingBaseline.toMutableMap().apply {
              pulledFiles.forEach { put(it.path, it.content) }
            }
            _uiState.update { state ->
              state.copy(
                isGitHubBusy = false,
                gitWorkingBaseline = updatedBaseline,
                workspace = existingWs.copy(files = nextFiles, isEsModuleFormat = true, hasModuleSetChanged = true),
                openTabPaths = (state.openTabPaths + pulledFiles.map { it.path }).distinct(),
                activeFilePath = firstPulled,
                editorFieldValues =
                  state.editorFieldValues + pulledFiles.associate { it.path to TextFieldValue(it.content) },
                diagnostics = nextFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
                statusMessage = "Pulled ${pulledFiles.size} file(s) into ${existingWs.scriptName}",
              )
            }
          }
          appendLog("INFO", "Pulled ${pulledFiles.size} file(s) from GitHub ${repo.fullName}@${_uiState.value.selectedGitHubBranch}")
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(isGitHubBusy = false, statusMessage = err.localizedMessage ?: "GitHub pull failed")
          }
        }
    }
  }

  fun pushActiveWorkerToGitHub(commitMessage: String) {
    pushQueuedAndActiveChangesToGitHub(commitMessageOverride = commitMessage)
  }

  // Linux Terminal & Enhanced API Testing Environment
  fun setTerminalInput(input: String) {
    _uiState.update { it.copy(terminalInput = input, terminalHistoryCursor = -1) }
  }

  fun pasteIntoTerminal(clipboardText: String) {
    val clean = clipboardText.replace("\r\n", "\n")
    _uiState.update { state ->
      val current = state.terminalInput
      val next = if (current.isEmpty()) clean else "$current$clean"
      state.copy(terminalInput = next, terminalHistoryCursor = -1)
    }
  }

  fun autocompleteTerminal() {
    val state = _uiState.value
    val completed = terminalEngine.autocomplete(state.terminalInput, state.workspace)
    _uiState.update { it.copy(terminalInput = completed) }
  }

  fun recallTerminalHistory(older: Boolean) {
    val state = _uiState.value
    val history = state.terminalHistory
    if (history.isEmpty()) return
    val nextCursor =
      if (older) {
        if (state.terminalHistoryCursor < 0) history.lastIndex
        else (state.terminalHistoryCursor - 1).coerceAtLeast(0)
      } else {
        if (state.terminalHistoryCursor < 0) -1
        else if (state.terminalHistoryCursor >= history.lastIndex) -1
        else state.terminalHistoryCursor + 1
      }
    val nextText = if (nextCursor in history.indices) history[nextCursor] else ""
    _uiState.update { it.copy(terminalInput = nextText, terminalHistoryCursor = nextCursor) }
  }

  fun clearTerminal() {
    _uiState.update { it.copy(terminalEntries = emptyList()) }
  }

  fun runTerminalCommand(rawCommand: String = _uiState.value.terminalInput) {
    val cmd = rawCommand.trim()
    if (cmd.isEmpty()) return
    val currentState = _uiState.value
    val promptSnapshot = "dev@edgecode:${terminalEngine.currentPromptPath(currentState.workspace)}$"
    val nextHistory =
      if (currentState.terminalHistory.lastOrNull() == cmd) currentState.terminalHistory
      else (currentState.terminalHistory + cmd).takeLast(50)

    _uiState.update {
      it.copy(
        terminalInput = "",
        terminalHistory = nextHistory,
        terminalHistoryCursor = -1,
        isTerminalBusy = true,
        showBottomPanel = true,
        activeBottomTab = BottomPanelTab.TERMINAL,
      )
    }

    viewModelScope.launch {
      val result =
        terminalEngine.execute(
          rawInput = cmd,
          workspace = _uiState.value.workspace,
          account = _uiState.value.selectedAccount,
          history = nextHistory,
          onWranglerDeploy = {
            if (_uiState.value.isLocalScratchpad) {
              "Local Scratchpad mode — connect Cloudflare to deploy live."
            } else {
              val acc = _uiState.value.selectedAccount?.id
              val ws = _uiState.value.workspace
              if (acc == null || ws == null) "No active Cloudflare account or Worker."
              else {
                repository.deployWorkerWorkspace(acc, ws).fold(
                  onSuccess = {
                    val cleanFiles = ws.files.map { f -> f.copy(originalContent = f.content) }
                    _uiState.update { s ->
                      s.copy(workspace = ws.copy(files = cleanFiles, hasModuleSetChanged = false))
                    }
                    "Deployed '${ws.scriptName}' live to ${ws.workersDevUrl ?: "Cloudflare Edge"}"
                  },
                  onFailure = { err -> "Deploy failed: ${err.localizedMessage}" },
                )
              }
            }
          },
          onWranglerTailStart = {
            if (_uiState.value.isLocalScratchpad) {
              "Connect Cloudflare account first to stream edge tail logs."
            } else if (_uiState.value.isTailRunning) {
              "Live Tail already streaming below."
            } else {
              val acc = _uiState.value.selectedAccount?.id
              val ws = _uiState.value.workspace
              if (acc == null || ws == null) "Open a Worker first."
              else {
                repository.startLiveTail(
                  accountId = acc,
                  scriptName = ws.scriptName,
                  onTailEvent = { raw ->
                    val evTs = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                    _uiState.update { s ->
                      val nextTail = (s.tailLogs + ConsoleLogEntry(evTs, "TAIL", raw)).takeLast(120)
                      val nextTerm =
                        (s.terminalEntries +
                          TerminalEntry(
                            id = terminalIdGen.getAndIncrement(),
                            timestamp = evTs,
                            promptPrefix = "tail",
                            command = "edge event",
                            output = raw,
                            kind = TerminalEntryKind.WRANGLER,
                          )).takeLast(120)
                      s.copy(tailLogs = nextTail, terminalEntries = nextTerm)
                    }
                  },
                  onClosed = {
                    activeTailSession = null
                    _uiState.update { s -> s.copy(isTailRunning = false) }
                  },
                ).fold(
                  onSuccess = { session ->
                    activeTailSession = session
                    _uiState.update { s -> s.copy(isTailRunning = true) }
                    "Connected WebSocket Live Tail for '${ws.scriptName}' (run 'wrangler tail stop' to close)"
                  },
                  onFailure = { err -> "Live Tail failed: ${err.localizedMessage}" },
                )
              }
            }
          },
          onWranglerTailStop = {
            stopTailIfRunning()
            "Stopped Live Tail session."
          },
          onWranglerRollback = { versionId ->
            val acc = _uiState.value.selectedAccount?.id
            val ws = _uiState.value.workspace
            if (acc == null || ws == null) "Connect Cloudflare and open a Worker first."
            else {
              repository.rollbackToVersion(acc, ws.scriptName, versionId).fold(
                onSuccess = { "Rolled back '${ws.scriptName}' to version $versionId" },
                onFailure = { err -> "Rollback failed: ${err.localizedMessage}" },
              )
            }
          },
          onGitCommand = { args -> executeTerminalGitCommand(args) },
        )

      val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
      _uiState.update { state ->
        var nextWs = state.workspace
        var nextFields = state.editorFieldValues
        if (result.updatedWorkspaceFiles != null && nextWs != null) {
          nextWs =
            nextWs.copy(
              files = result.updatedWorkspaceFiles,
              hasModuleSetChanged = true,
            )
          nextFields =
            result.updatedWorkspaceFiles.associate { f ->
              f.path to (nextFields[f.path]?.takeIf { it.text == f.content } ?: TextFieldValue(f.content))
            }
        }
        val entries =
          if (result.clearedScreen) emptyList()
          else {
            val newEntry =
              TerminalEntry(
                id = terminalIdGen.getAndIncrement(),
                timestamp = ts,
                promptPrefix = promptSnapshot,
                command = cmd,
                output = result.output,
                isError = result.isError,
                exitCode = result.exitCode,
                kind = result.kind,
                httpMeta = result.httpMeta,
              )
            (state.terminalEntries + newEntry).takeLast(120)
          }
        state.copy(
          workspace = nextWs,
          editorFieldValues = nextFields,
          diagnostics = nextWs?.files?.flatMap { CodeAnalyzer.analyzeFile(it) } ?: state.diagnostics,
          terminalEntries = entries,
          terminalPromptPath = terminalEngine.currentPromptPath(nextWs),
          isTerminalBusy = false,
        )
      }
      if (!result.openTabRequest.isNullOrBlank()) {
        val target = result.openTabRequest
        val match = _uiState.value.workspace?.files?.firstOrNull { it.path == target || it.fileName == target }
        if (match != null) openFileTab(match.path)
      }
      if (result.httpMeta != null) {
        appendLog(
          if (result.httpMeta.statusCode in 200..399) "HTTP" else "WARN",
          "${result.httpMeta.method} ${result.httpMeta.url} -> ${result.httpMeta.statusCode} (${result.httpMeta.durationMs}ms)",
        )
      }
    }
  }

  private fun appendTerminalWelcome(ws: WorkerWorkspace) {
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    val urlHint = ws.workersDevUrl ?: "(Scratchpad offline — pass any https:// URL to curl)"
    val welcome =
      TerminalEntry(
        id = terminalIdGen.getAndIncrement(),
        timestamp = ts,
        promptPrefix = "dev@edgecode:~/${ws.scriptName}$",
        command = "echo \$BASE_URL && help --quick",
        output =
          "Worker: ${ws.scriptName}\nBASE_URL: $urlHint\nShell: Android Linux /system/bin/sh + curl + jq + wrangler\nTip: Long-press any text, or use Copy Out / Copy Headers / Select / Paste buttons.",
        kind = TerminalEntryKind.SYSTEM,
      )
    _uiState.update { state ->
      if (state.terminalEntries.isEmpty()) state.copy(terminalEntries = listOf(welcome))
      else state
    }
  }

  private suspend fun executeTerminalGitCommand(args: List<String>): ShellExecutionResult {
    if (args.isEmpty()) {
      return ShellExecutionResult(
        output = "Usage: git <status|branch|checkout|add|reset|diff|commit|push|pull|log|clone|whoami>",
        kind = TerminalEntryKind.SYSTEM,
      )
    }
    val sub = args[0].lowercase()
    val state = _uiState.value
    val linked = state.linkedGitRepo
    val selected = state.selectedGitHubRepo
    val branch = linked?.branch ?: state.selectedGitHubBranch

    return when (sub) {
      "whoami" -> {
        val creds = state.gitHubCreds
        if (creds == null) {
          ShellExecutionResult("Not signed into GitHub. Open Auth or Source Control -> Connect GitHub.", kind = TerminalEntryKind.SYSTEM)
        } else {
          ShellExecutionResult(
            "User: @${creds.login}\nAuth: ${creds.method.name}\nScopes: ${creds.scopes.ifBlank { "(default)" }}\nRate Limit: ${state.gitHubRateLimitLabel ?: "unknown"}",
            kind = TerminalEntryKind.SYSTEM,
          )
        }
      }
      "status" -> {
        val header =
          if (linked != null) "On repo ${linked.fullName} (branch $branch, HEAD ${linked.headCommitSha.take(7).ifBlank { "unborn" }})"
          else "No remote GitHub repo linked yet (use 'git clone owner/repo' or Source Control)."
        val changes = state.gitChangedFiles
        val staged = changes.filter { it.isStaged }
        val unstaged = changes.filterNot { it.isStaged }
        val out = buildString {
          appendLine(header)
          if (state.localGitCommits.isNotEmpty()) {
            appendLine("Your branch is ahead of 'origin/$branch' by ${state.localGitCommits.size} commit(s).")
          }
          if (changes.isEmpty()) {
            append("nothing to commit, working tree clean")
          } else {
            if (staged.isNotEmpty()) {
              appendLine("Changes to be committed:")
              staged.forEach { appendLine("  [${it.status.name.first()}] ${it.path}") }
            }
            if (unstaged.isNotEmpty()) {
              appendLine("Changes not staged for commit:")
              unstaged.forEach { appendLine("  [${it.status.name.first()}] ${it.path}") }
            }
          }
        }.trimEnd()
        ShellExecutionResult(out, kind = TerminalEntryKind.SYSTEM)
      }
      "branch" -> {
        val flag = args.getOrNull(1)
        if (flag == "-b" || flag == "--create") {
          val newName = args.getOrNull(2).orEmpty().trim()
          if (newName.isBlank()) {
            return ShellExecutionResult("Usage: git branch -b <new-branch>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
          }
          createGitHubBranch(newName)
          ShellExecutionResult("Creating branch '$newName' from '$branch'…", kind = TerminalEntryKind.SYSTEM)
        } else if (!flag.isNullOrBlank() && !flag.startsWith("-")) {
          createGitHubBranch(flag)
          ShellExecutionResult("Creating branch '$flag' from '$branch'…", kind = TerminalEntryKind.SYSTEM)
        } else {
          val names = state.gitHubBranches.map { it.name }.ifEmpty { listOf(branch) }
          val out = names.joinToString("\n") { if (it == branch) "* $it" else "  $it" }
          ShellExecutionResult(out, kind = TerminalEntryKind.SYSTEM)
        }
      }
      "checkout", "switch" -> {
        val targetBranch = args.getOrNull(1).orEmpty().trim()
        if (targetBranch.isBlank()) {
          ShellExecutionResult("Usage: git $sub <branch>", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        } else if (selected == null) {
          ShellExecutionResult("Select or clone a repository first.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        } else {
          selectGitHubBranch(targetBranch)
          ShellExecutionResult("Switched branch view to '$targetBranch' on ${selected.fullName}", kind = TerminalEntryKind.SYSTEM)
        }
      }
      "add" -> {
        val target = args.getOrNull(1) ?: "."
        if (target == "." || target == "-A" || target == "--all") {
          stageAllGitChanges()
          ShellExecutionResult("Staged all modified files.", kind = TerminalEntryKind.SYSTEM)
        } else {
          _uiState.update { s -> s.copy(gitUnstagedExcludedPaths = s.gitUnstagedExcludedPaths - target) }
          ShellExecutionResult("Staged '$target'.", kind = TerminalEntryKind.SYSTEM)
        }
      }
      "reset" -> {
        val target = args.getOrNull(1) ?: "."
        if (target == "." || target == "--all") {
          unstageAllGitChanges()
          ShellExecutionResult("Unstaged all working changes.", kind = TerminalEntryKind.SYSTEM)
        } else {
          _uiState.update { s -> s.copy(gitUnstagedExcludedPaths = s.gitUnstagedExcludedPaths + target) }
          ShellExecutionResult("Unstaged '$target'.", kind = TerminalEntryKind.SYSTEM)
        }
      }
      "diff" -> {
        val changes = state.gitChangedFiles
        if (changes.isEmpty()) {
          ShellExecutionResult("(no working tree diff)", kind = TerminalEntryKind.SYSTEM)
        } else {
          val diffText = buildString {
            changes.take(8).forEach { ch ->
              appendLine("diff --git a/${ch.path} b/${ch.path}")
              val hunks = CodeFormatter.computeLineDiff(ch.baseContent, ch.currentContent).filter { it.type != DiffType.UNCHANGED }
              hunks.take(25).forEach { line ->
                val sign = if (line.type == DiffType.ADDED) "+" else "-"
                appendLine("$sign ${line.text}")
              }
            }
          }.trimEnd()
          ShellExecutionResult(diffText, kind = TerminalEntryKind.SYSTEM)
        }
      }
      "commit" -> {
        val mIndex = args.indexOfFirst { it == "-m" || it == "--message" }
        val msg =
          if (mIndex >= 0 && mIndex + 1 < args.size) args.drop(mIndex + 1).joinToString(" ").trim()
          else args.drop(1).joinToString(" ").trim()
        if (msg.isBlank()) {
          return ShellExecutionResult("Usage: git commit -m \"your commit message\"", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        val stagedCount = state.gitChangedFiles.count { it.isStaged }
        if (stagedCount == 0) {
          return ShellExecutionResult("No staged changes to commit. Run 'git add .' first.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        commitStagedChangesLocally(msg)
        ShellExecutionResult("[$branch] $msg ($stagedCount file(s) committed locally; run 'git push' to sync)", kind = TerminalEntryKind.SYSTEM)
      }
      "push" -> {
        if (state.gitHubCreds == null) {
          return ShellExecutionResult("git push: authenticate GitHub (OAuth or PAT) first.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        val owner = linked?.owner ?: selected?.owner?.login
        val repoName = linked?.repoName ?: selected?.name
        if (owner == null || repoName == null) {
          return ShellExecutionResult("git push: no linked remote repository.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        pushQueuedAndActiveChangesToGitHub()
        ShellExecutionResult("Pushing commits & staged edits to $owner/$repoName ($branch)…", kind = TerminalEntryKind.SYSTEM)
      }
      "pull" -> {
        if (linked == null && selected == null) {
          return ShellExecutionResult("git pull: link or clone a repository first.", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        val targetSlug = linked?.fullName ?: selected?.fullName.orEmpty()
        cloneGitHubRepository(targetSlug, branch, mergeIntoCurrentWorker = true)
        ShellExecutionResult("Pulling latest files from $targetSlug@$branch…", kind = TerminalEntryKind.SYSTEM)
      }
      "clone" -> {
        val urlArg = args.getOrNull(1)
        if (urlArg.isNullOrBlank()) {
          return ShellExecutionResult("Usage: git clone <github-url|owner/repo> [branch]", isError = true, exitCode = 1, kind = TerminalEntryKind.ERROR)
        }
        val branchArg = args.getOrNull(2)
        cloneGitHubRepository(urlArg, branchArg, mergeIntoCurrentWorker = false)
        ShellExecutionResult("Cloning $urlArg${branchArg?.let { "@$it" }.orEmpty()} into workspace…", kind = TerminalEntryKind.SYSTEM)
      }
      "log" -> {
        val out = buildString {
          state.localGitCommits.reversed().forEach { lc ->
            appendLine("commit ${lc.id} (LOCAL / UNPUSHED)")
            appendLine("Author: ${lc.author}  Time: ${lc.timestamp}")
            appendLine("    ${lc.message}\n")
          }
          state.gitHubCommits.take(8).forEach { rc ->
            appendLine("commit ${rc.sha.take(7)} (origin/$branch)")
            appendLine("Author: ${rc.commit.author?.name.orEmpty()}")
            appendLine("    ${rc.commit.message.lineSequence().firstOrNull().orEmpty()}\n")
          }
        }.trimEnd().ifBlank { "(no commits fetched yet — open or clone a repo)" }
        ShellExecutionResult(out, kind = TerminalEntryKind.SYSTEM)
      }
      else ->
        ShellExecutionResult(
          output = "git: '$sub' is not supported. Run 'git' with no arguments for usage.",
          isError = true,
          exitCode = 1,
          kind = TerminalEntryKind.ERROR,
        )
    }
  }

  // Settings
  fun updateSettings(transform: (EditorSettings) -> EditorSettings) {
    viewModelScope.launch { prefsStore.updateEditorSettings(transform) }
  }

  private fun appendLog(level: String, message: String) {
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    _uiState.update { state ->
      val next = (state.consoleLogs + ConsoleLogEntry(ts, level, message)).takeLast(120)
      state.copy(consoleLogs = next)
    }
  }
}
