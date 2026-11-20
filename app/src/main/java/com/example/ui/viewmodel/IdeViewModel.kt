package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileOutputStream
import com.example.data.auth.CloudflareOAuthManager
import com.example.data.auth.GitHubDeviceCodeResponse
import com.example.data.auth.GitHubOAuthManager
import com.example.data.local.ApiResponseCacheStore
import com.example.data.local.AutoSaveDraftStore
import com.example.data.local.AutoSaveSessionPointer
import com.example.data.local.AutoSavedWorkspaceDraft
import com.example.data.local.DeviceFileHelper
import com.example.data.local.DraftPersistenceManager
import com.example.data.local.ExternalIntentExtractor
import com.example.data.local.ExternalOpenTargetMode
import com.example.data.local.PersistedApiTesterState
import com.example.data.local.PickedDeviceFile
import com.example.data.local.ProjectDirectoryManager
import com.example.data.local.ProjectStorageSummary
import com.example.data.local.RamAccountInfraCache
import com.example.data.local.RamDatabaseStats
import com.example.data.local.RamWorkspaceDatabase
import com.example.data.local.RamWorkspaceRecord
import com.example.data.local.RawPickedAsset
import com.example.data.local.WorkerTestRunRecord
import com.example.data.local.ZipImportPlan
import com.example.data.local.ZipImportTargetMode
import com.example.data.local.ZipPreviewState
import com.example.data.local.ZipWorkspaceImporter
import com.example.data.remote.ApiAssertionResult
import com.example.data.remote.ApiCollectionImporter
import com.example.data.remote.ApiEnvVar
import com.example.data.remote.ApiEnvironmentProfile
import com.example.data.remote.ApiImportResult
import com.example.data.remote.ApiRequestUtils
import com.example.data.remote.ApiTesterHistoryItem
import com.example.data.remote.ApiTesterRequest
import com.example.data.remote.CfAccount
import com.example.data.remote.CfD1Database
import com.example.data.remote.CfD1QueryResult
import com.example.data.remote.CfDeploymentVersion
import com.example.data.remote.CfDurableObjectNamespace
import com.example.data.remote.CfKvBulkItem
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
import com.example.data.remote.CfWorkerVersion
import com.example.data.remote.CfZone
import com.example.data.remote.CloudflareClientFactory
import com.example.data.remote.CloudflareNamingUtils
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
import com.example.data.remote.GitHubWorkflowJob
import com.example.data.remote.GitHubWorkflowRun
import com.example.data.remote.GitIgnoreMatcher
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
import com.example.editor.CodeEditHelper
import com.example.editor.CodeFormatter
import com.example.editor.ConflictChoice
import com.example.editor.DiffFileOrigin
import com.example.editor.FileDiffEntry
import com.example.editor.LocalRunLogEntry
import com.example.editor.LocalScriptRunResult
import com.example.editor.LocalScriptRunner
import com.example.editor.LocalWorkspacePreviewRenderer
import com.example.editor.MergeConflictFile
import com.example.editor.MergeConflictResolver
import com.example.editor.WranglerSyncParser
import com.example.idecore.IdeBackgroundThreads
import com.example.idecore.IdePerfSampler
import com.example.idecore.LocalHistorySnapshot
import com.example.idecore.LocalHistoryStore
import com.example.idecore.WorkspaceEvent
import com.example.idecore.WorkspaceEventBus
import com.example.idecore.WorkspaceFileOpJournal
import com.example.idecore.completion.CompletionStatsStore
import com.example.idecore.completion.EdgeCompletionEngine
import com.example.idecore.completion.EdgeCompletionItem
import com.example.idecore.completion.MemberUsageIndexer
import com.example.idecore.search.ProjectSearchOptions
import com.example.idecore.search.ProjectSymbolHit
import com.example.idecore.search.ProjectTextMatch
import com.example.idecore.search.RenamePreview
import com.example.idecore.search.WorkspaceSearchAndRefactor
import com.example.ui.components.DiffReviewIntent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class HomeTab {
  PROJECTS,
  PAGES,
  INFRA,
}

enum class ProjectMode {
  CODE,
  TEST,
  CONFIG,
  CONSOLE,
}

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
  LOCAL_RUN,
  WORKER_PREVIEW,
  WORKER_TEST,
  API_TESTER,
  PROBLEMS,
  TAIL_LOGS,
  CONSOLE,
}

data class WorkerPreviewConsoleEntry(
  val id: String,
  val timestamp: String,
  val kind: String, // "NAV", "NET", "HTTP", "JS", "DEPLOY", "ERR"
  val methodOrLevel: String,
  val message: String,
  val statusCode: Int? = null,
  val durationMs: Long? = null,
)

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

sealed interface PendingUnsavedAction {
  data class CloseTab(val path: String) : PendingUnsavedAction
  data object LeaveWorkspace : PendingUnsavedAction
  data class SwitchWorker(val script: CfWorkerScript, val forceRefreshFromCloudflare: Boolean) : PendingUnsavedAction
  data class SwitchRamWorkspace(val key: String) : PendingUnsavedAction
  data class SwitchAccount(val account: CfAccount) : PendingUnsavedAction
  data object SignOut : PendingUnsavedAction
  data object ReimportFromCloudflare : PendingUnsavedAction
  data object PullLinkedGitRepo : PendingUnsavedAction
  data class PullGitHubPaths(val paths: List<String>) : PendingUnsavedAction
}

data class PendingUnsavedWarning(
  val action: PendingUnsavedAction,
  val title: String,
  val message: String,
  val dirtyFilePaths: List<String>,
  val allowKeepLocalDraft: Boolean,
  val allowDeployNow: Boolean,
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
  val accountSubdomain: String = "",
  val workers: List<CfWorkerScript> = emptyList(),
  val isLoadingWorkers: Boolean = false,
  val isOpeningWorker: Boolean = false,
  val workspace: WorkerWorkspace? = null,
  val openTabPaths: List<String> = emptyList(),
  val activeFilePath: String? = null,
  val tabNavHistory: List<String> = emptyList(),
  val tabNavIndex: Int = -1,
  val editorFieldValues: Map<String, TextFieldValue> = emptyMap(),
  val editorRevision: Long = 0L,
  val diagnostics: List<DiagnosticItem> = emptyList(),
  val isDeploying: Boolean = false,
  val deployBannerMessage: String? = null,
  val isDeployError: Boolean = false,
  val sidebarSection: SidebarSection = SidebarSection.EXPLORER,
  val showBottomPanel: Boolean = false,
  val isBottomPanelExpanded: Boolean = false,
  val activeBottomTab: BottomPanelTab = BottomPanelTab.WORKER_PREVIEW,
  val showFindBar: Boolean = false,
  val findQuery: String = "",
  val replaceQuery: String = "",
  val findMatchCase: Boolean = false,
  val findScope: Int = 0, // 0 = Current Tab, 1 = Open Tabs, 2 = All Files
  val searchQuery: String = "",
  val editorSettings: EditorSettings = EditorSettings(),
  // On-Device Local Pre-Deploy Script Runner
  val isRunningLocalScript: Boolean = false,
  val localScriptResult: LocalScriptRunResult? = null,
  val localRunUrlPath: String = "/",
  val localRunMethod: String = "GET",
  val localRunHeadersJson: String = "{}",
  val localRunBody: String = "",
  val cliHistory: List<String> = emptyList(),
  // External Open With / Share Sheet & Multi-File Web Project Context
  val externalFileUris: Map<String, String> = emptyMap(),
  val externalBinaryAssets: Map<String, ByteArray> = emptyMap(),
  val externalProjectRootPath: String? = null,
  val isPreviewDesktopViewport: Boolean = false,
  // Deployed Worker Live Preview & Testing Window
  val previewUrl: String = "",
  val previewModeTab: Int = 0, // 0 = Preview, 1 = HTTP
  val previewHttpMethod: String = "GET",
  val previewHttpHeadersJson: String = "{\"accept\": \"application/json\"}",
  val previewHttpBody: String = "",
  val previewHttpResult: HttpTestResult? = null,
  val isRunningPreviewHttp: Boolean = false,
  val previewConsoleEntries: List<WorkerPreviewConsoleEntry> = emptyList(),
  val previewReloadNonce: Long = 0L,
  val isPreviewConsoleExpanded: Boolean = true,
  val isPreviewFullScreen: Boolean = false,
  val workerTestHistory: List<WorkerTestRunRecord> = emptyList(),
  val ramDbStats: RamDatabaseStats = RamDatabaseStats(),
  val ramWorkspaces: List<RamWorkspaceRecord> = emptyList(),
  val projectStorageSummary: ProjectStorageSummary = ProjectStorageSummary(),
  val homeTab: HomeTab = HomeTab.PROJECTS,
  val projectMode: ProjectMode = ProjectMode.CODE,
  val isTopBarHidden: Boolean = false,
  val isEditorTopChromeHidden: Boolean = false,
  val isBottomDockHidden: Boolean = false,
  val isEditorBottomBarsHidden: Boolean = false,
  val showHomeGitHubHub: Boolean = false,
  val showHomeSettings: Boolean = false,
  val isTestingCfCredentials: Boolean = false,
  val cfCredentialTestMessage: String? = null,
  val isCfCredentialTestError: Boolean = false,
  val activeWorkspaceHasPendingConfig: Boolean = false,
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
  // API Test Pro, Diff Review & 3-Way Merge Conflict Resolver
  val isRunningHttpTest: Boolean = false,
  val httpTestResult: HttpTestResult? = null,
  val apiRequest: ApiTesterRequest = ApiTesterRequest(),
  val apiCollections: List<ApiTesterRequest> = emptyList(),
  val apiHistory: List<ApiTesterHistoryItem> = emptyList(),
  val apiEnvVars: List<ApiEnvVar> = listOf(ApiEnvVar(key = "apiToken", value = "")),
  val apiEnvProfiles: List<ApiEnvironmentProfile> = ApiRequestUtils.defaultEnvironmentProfiles(),
  val activeApiEnvProfileId: String = "env_dev",
  val apiAssertionResults: List<ApiAssertionResult> = emptyList(),
  val showDiffReviewModal: Boolean = false,
  val diffReviewIntent: DiffReviewIntent = DiffReviewIntent.WORKSPACE,
  val diffReviewEntries: List<FileDiffEntry> = emptyList(),
  val showConflictResolverModal: Boolean = false,
  val pendingMergeConflicts: List<MergeConflictFile> = emptyList(),
  val restoredDraftBanner: String? = null,
  val lastAutoSavedEpochMs: Long? = null,
  val workerVersions: List<CfWorkerVersion> = emptyList(),
  val selectedWorkflowRunId: Long? = null,
  val selectedWorkflowJobs: List<GitHubWorkflowJob> = emptyList(),
  val isTailRunning: Boolean = false,
  val tailLogs: List<ConsoleLogEntry> = emptyList(),
  val consoleLogs: List<ConsoleLogEntry> = emptyList(),
  val lastUndoableFileOpLabel: String? = null,
  val zipImportPreview: ZipPreviewState? = null,
  val pendingUnsavedWarning: PendingUnsavedWarning? = null,
  val statusMessage: String? = null,
) {
  val activeFile: WorkerFile?
    get() = workspace?.files?.firstOrNull { it.path == activeFilePath }

  val hasUnsavedChanges: Boolean
    get() =
      activeWorkspaceHasPendingConfig ||
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
      return CodeEditHelper.computeCursorLineAndCol(tfv.text, tfv.selection.start)
    }

  val gitChangedFiles: List<GitChangedFile>
    get() {
      val ws = workspace ?: return emptyList()
      val gitignoreContent = ws.files.firstOrNull { it.path == ".gitignore" }?.content
      val ignorePatterns = GitIgnoreMatcher.parsePatterns(gitignoreContent)
      val currentMap =
        ws.files
          .filterNot { it.isVirtualReadOnly || it.path == "wrangler.jsonc" }
          .filterNot { GitIgnoreMatcher.isIgnored(it.path, ignorePatterns) }
          .associate { it.path to it.content }
      val baseline = gitWorkingBaseline.filterKeys { !GitIgnoreMatcher.isIgnored(it, ignorePatterns) }
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
  private val projectDirManager = ProjectDirectoryManager(application)
  private val draftManager = DraftPersistenceManager(application)
  private val autoSaveStore = AutoSaveDraftStore(application)
  private val apiCacheStore =
    ApiResponseCacheStore(File(application.filesDir, "api_response_cache"), clientFactory.moshi)
  private val autoSaveIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val ramDb = RamWorkspaceDatabase()

  private val ideCoreIndexExecutor = IdeBackgroundThreads.single("ide-core-index")
  private val localHistoryStore = LocalHistoryStore(File(application.filesDir, "local_history"))
  private val fileOpJournal = WorkspaceFileOpJournal()
  private val completionStatsStore =
    CompletionStatsStore(File(application.filesDir, "ide_meta/completion_stats.properties"))
  private val memberUsageIndexer = MemberUsageIndexer()
  private val completionEngine = EdgeCompletionEngine(completionStatsStore, memberUsageIndexer)
  val idePerfSampler = IdePerfSampler()
  val workspaceEventBus = WorkspaceEventBus()
  private val lastHistoryRecordMs = ConcurrentHashMap<String, Long>()

  private val _uiState = MutableStateFlow(IdeUiState())
  val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

  private var oauthJob: Job? = null
  private var gitHubOAuthJob: Job? = null
  private var draftSaveJob: Job? = null
  private var periodicAutoSaveJob: Job? = null
  private var diagnosticsJob: Job? = null
  private var ramStatsJob: Job? = null
  private var zoomPersistJob: Job? = null
  private var lastUndoSnapshotMs: Long = 0L
  private var lastUndoSnapshotPath: String? = null
  private var activeTailSession: ActiveTailSession? = null
  private val undoStacks = mutableMapOf<String, ArrayDeque<TextFieldValue>>()
  private val redoStacks = mutableMapOf<String, ArrayDeque<TextFieldValue>>()
  private val fileDiagnosticsCache = ConcurrentHashMap<String, List<DiagnosticItem>>()
  private val autoSaveWriteMutex = Mutex()
  private val lastPersistedDraftDigests = ConcurrentHashMap<String, Int>()
  private val lastPersistedNonSpacingDigests = ConcurrentHashMap<String, Int>()
  private val lastLocallyAutoSavedNonSpacing = ConcurrentHashMap<String, String>()
  private val pendingCloudDrafts = ConcurrentHashMap<String, AutoSavedWorkspaceDraft>()
  private var pendingRestoreSessionPointer: AutoSaveSessionPointer? = null
  private var hasAttemptedAutoReopen = false

  private fun clearLocalAutoSaveTrackingForWorkspace(workspaceKey: String) {
    lastPersistedDraftDigests.remove(workspaceKey)
    lastPersistedNonSpacingDigests.remove(workspaceKey)
    val prefix = "$workspaceKey::"
    lastLocallyAutoSavedNonSpacing.keys.removeAll { it.startsWith(prefix) }
  }

  private fun seedLocalAutoSaveBaseline(workspaceKey: String, record: RamWorkspaceRecord) {
    val editable = record.workingWorkspace.files.filterNot { it.isVirtualReadOnly }
    editable.forEach { file ->
      val liveText = record.fieldValues[file.path]?.text ?: file.content
      lastLocallyAutoSavedNonSpacing["$workspaceKey::${file.path}"] = AutoSaveDraftStore.stripSpacing(liveText)
    }
    lastPersistedNonSpacingDigests[workspaceKey] =
      AutoSaveDraftStore.computeNonSpacingContentDigest(
        files = editable,
        openTabs = record.openTabPaths,
        activeFilePath = record.activeFilePath,
        entryModulePath = record.workingWorkspace.entryModulePath,
      )
  }

  private fun activeWorkspaceKey(state: IdeUiState = _uiState.value): String? {
    val ws = state.workspace ?: return null
    val accId = if (state.isLocalScratchpad) null else state.selectedAccount?.id
    return ramDb.workspaceKey(accId, ws.scriptName)
  }

  @Volatile
  private var activeEditorFlushHook: (() -> Unit)? = null

  fun registerEditorFlushHook(hook: (() -> Unit)?) {
    activeEditorFlushHook = hook
  }

  fun flushActiveEditorBuffer() {
    activeEditorFlushHook?.invoke()
  }

  private fun syncActiveStateToRamDb() {
    val state = _uiState.value
    val key = activeWorkspaceKey(state) ?: return
    ramDb.saveTabAndEditorState(
      key = key,
      openTabs = state.openTabPaths,
      activeFilePath = state.activeFilePath,
      tabNavHistory = state.tabNavHistory,
      tabNavIndex = state.tabNavIndex,
      fieldValues = state.editorFieldValues,
    )
    val stats = ramDb.computeStats(key)
    _uiState.update { if (it.ramDbStats != stats) it.copy(ramDbStats = stats) else it }
  }

  init {
    workspaceEventBus.subscribe { event ->
      when (event) {
        is WorkspaceEvent.FileSaved -> {
          val lang = _uiState.value.workspace?.files?.firstOrNull { it.path == event.path }?.language
          if (lang != null) {
            ideCoreIndexExecutor.execute {
              memberUsageIndexer.updateFile(event.path, event.after, lang)
            }
          }
        }
        is WorkspaceEvent.FileDeleted -> {
          ideCoreIndexExecutor.execute { memberUsageIndexer.removeFile(event.path) }
        }
        is WorkspaceEvent.FilesRefactored -> {
          val ws = _uiState.value.workspace
          ideCoreIndexExecutor.execute { memberUsageIndexer.indexWorkspace(ws) }
        }
        else -> Unit
      }
    }

    viewModelScope.launch {
      _uiState
        .map { it.workspace?.scriptName }
        .distinctUntilChanged()
        .collect { _ ->
          val state = _uiState.value
          val ws = state.workspace
          ideCoreIndexExecutor.execute { memberUsageIndexer.indexWorkspace(ws) }
          val key = activeWorkspaceKey(state)
          val latestLabel = key?.let { fileOpJournal.latestForWorkspace(it)?.op?.label }
          _uiState.update {
            if (it.lastUndoableFileOpLabel != latestLabel) it.copy(lastUndoableFileOpLabel = latestLabel)
            else it
          }
        }
    }

    viewModelScope.launch {
      val persistedApi = draftManager.loadApiTesterState()
      _uiState.update {
        it.copy(
          apiCollections = persistedApi.collections,
          apiHistory = persistedApi.history,
          apiEnvVars = persistedApi.envVars,
          apiEnvProfiles = persistedApi.environmentProfiles,
          activeApiEnvProfileId = persistedApi.activeEnvProfileId,
        )
      }
    }

    viewModelScope.launch {
      var prevAutoSaveEnabled: Boolean? = null
      var prevAutoSaveIntervalSec: Int? = null
      prefsStore.editorSettingsFlow.collect { settings ->
        _uiState.update { state ->
          val merged =
            if (zoomPersistJob?.isActive == true) {
              settings.copy(zoomPercent = state.editorSettings.zoomPercent)
            } else {
              settings
            }
          state.copy(editorSettings = merged)
        }
        if (prevAutoSaveEnabled != settings.autoSaveEnabled || prevAutoSaveIntervalSec != settings.autoSaveIntervalSeconds) {
          prevAutoSaveEnabled = settings.autoSaveEnabled
          prevAutoSaveIntervalSec = settings.autoSaveIntervalSeconds
          syncPeriodicAutoSaveLoop()
        }
      }
    }

    viewModelScope.launch {
      ramDb.stats.collect {
        _uiState.update { state -> state.copy(ramWorkspaces = ramDb.listWorkspaces()) }
      }
    }

    viewModelScope.launch {
      prefsStore.gitHubCredentialsFlow.collect { ghCreds ->
        _uiState.update { it.copy(gitHubCreds = ghCreds) }
        if (ghCreds != null && _uiState.value.gitHubRepos.isEmpty()) {
          refreshGitHubReposInternal(ghCreds.token, forceRefresh = false)
        }
      }
    }

    viewModelScope.launch {
      val (diskWorkspaces, initialSummary, restoredDrafts, lastPointer) =
        withContext(Dispatchers.IO) {
          projectDirManager.refreshRootDirectoryAndMigrate()
          val diskList = projectDirManager.loadAllWorkspaces()
          val drafts = autoSaveStore.loadAllDrafts()
          val pointer = autoSaveStore.loadLastSessionPointer()
          val summary = projectDirManager.computeStorageSummary()
          Quadruple(diskList, summary, drafts, pointer)
        }
      // Phone directory files on disk are the primary source of truth
      diskWorkspaces.forEach { rec ->
        ramDb.restoreRecord(rec, setActive = false)
        seedLocalAutoSaveBaseline(rec.key, rec)
      }
      pendingRestoreSessionPointer = lastPointer
      restoredDrafts.forEach { draft ->
        lastPersistedDraftDigests[draft.workspaceKey] = draft.contentDigest
        lastPersistedNonSpacingDigests[draft.workspaceKey] =
          AutoSaveDraftStore.computeNonSpacingContentDigest(
            files = draft.files,
            openTabs = draft.openTabs,
            activeFilePath = draft.activeFilePath,
            entryModulePath = draft.entryModulePath,
          )
        if (draft.isLocalScratchpad || draft.workspaceKey.startsWith("local_")) {
          // Only materialize from AutoSaveDraftStore if no folder exists on disk yet
          if (!ramDb.hasWorkspace(draft.workspaceKey)) {
            val ws = draft.toWorkspace()
            val record =
              ramDb.importWorkspace(
                key = draft.workspaceKey,
                workspace = ws,
                defaultTabs = draft.openTabs,
                defaultActivePath = draft.activeFilePath,
                isNewUnexported = false,
              )
            val selections = draft.toFieldValues()
            val finalRecord =
              if (selections.isNotEmpty()) {
                ramDb.updateWorkspace(draft.workspaceKey) { rec ->
                  rec.copy(fieldValues = selections)
                } ?: record
              } else {
                record
              }
            seedLocalAutoSaveBaseline(draft.workspaceKey, finalRecord)
            autoSaveIoScope.launch {
              autoSaveWriteMutex.withLock { projectDirManager.saveWorkspaceRecord(finalRecord) }
            }
          }
        } else {
          pendingCloudDrafts[draft.workspaceKey] = draft
        }
      }
      _uiState.update {
        it.copy(
          ramWorkspaces = ramDb.listWorkspaces(),
          ramDbStats = ramDb.computeStats(null),
          projectStorageSummary = initialSummary,
        )
      }

      val shouldReopenLast = runCatching { prefsStore.editorSettingsFlow.first().reopenLastProject }.getOrDefault(true)
      val creds = prefsStore.credentialsFlow.first()
      if (creds != null) {
        _uiState.update { it.copy(credentials = creds, isBootstrapping = false) }
        appendLog("INFO", "Restored encrypted Cloudflare session (${creds.method.name})")
        if (shouldReopenLast && lastPointer != null && lastPointer.isLocalScratchpad && !hasAttemptedAutoReopen && _uiState.value.workspace == null) {
          hasAttemptedAutoReopen = true
          val localDraft = restoredDrafts.firstOrNull { it.workspaceKey == lastPointer.workspaceKey }
          if (localDraft != null && ramDb.hasWorkspace(localDraft.workspaceKey)) {
            restoreLocalDraftIntoEditor(localDraft)
          } else if (ramDb.hasWorkspace(lastPointer.workspaceKey)) {
            openRamWorkspace(lastPointer.workspaceKey)
          }
        }
        bootstrapAccountAndWorkers(creds)
      } else {
        _uiState.update { it.copy(isBootstrapping = false) }
        if (shouldReopenLast && lastPointer != null && lastPointer.isLocalScratchpad && !hasAttemptedAutoReopen && _uiState.value.workspace == null) {
          hasAttemptedAutoReopen = true
          val localDraft = restoredDrafts.firstOrNull { it.workspaceKey == lastPointer.workspaceKey }
          if (localDraft != null && ramDb.hasWorkspace(localDraft.workspaceKey)) {
            restoreLocalDraftIntoEditor(localDraft)
          } else if (ramDb.hasWorkspace(lastPointer.workspaceKey)) {
            openRamWorkspace(lastPointer.workspaceKey)
          }
        }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    LocalScriptRunner.stopActiveServer()
    flushAutoSaveNow(clearSessionPointer = false)
    val accountId = _uiState.value.selectedAccount?.id
    val scriptName = _uiState.value.workspace?.scriptName
    val tail = activeTailSession
    if (accountId != null && scriptName != null && tail != null) {
      viewModelScope.launch { repository.stopLiveTail(accountId, scriptName, tail) }
    }
  }

  private fun bootstrapAccountAndWorkers(creds: StoredCredentials) {
    viewModelScope.launch {
      val ttlHours = _uiState.value.editorSettings.apiCacheTtlHours
      val savedAccountId = creds.selectedAccountId
      if (savedAccountId.isNotBlank()) {
        val cachedWorkers = withContext(Dispatchers.IO) { apiCacheStore.loadWorkers(savedAccountId) }
        if (cachedWorkers != null) {
          val cachedAccount =
            CfAccount(
              id = savedAccountId,
              name = creds.selectedAccountName.ifBlank { "Cloudflare Account" },
            )
          ramDb.updateInfraCache(savedAccountId) {
            it.copy(
              workers = cachedWorkers.workers,
              subdomain = cachedWorkers.subdomain.ifBlank { it.subdomain },
            )
          }
          _uiState.update { state ->
            state.copy(
              accounts = state.accounts.ifEmpty { listOf(cachedAccount) },
              selectedAccount = state.selectedAccount ?: cachedAccount,
              workers = cachedWorkers.workers,
              accountSubdomain = cachedWorkers.subdomain.ifBlank { state.accountSubdomain },
              isLoadingWorkers = false,
            )
          }
          if (ApiResponseCacheStore.isFresh(cachedWorkers.fetchedAtEpochMs, ttlHours)) {
            maybeAutoReopenPendingCloudDraft(savedAccountId, cachedWorkers.workers)
            return@launch
          }
        }
      }

      _uiState.update { it.copy(isLoadingWorkers = true) }
      repository
        .loadAccounts()
        .onSuccess { accounts ->
          val chosen =
            accounts.firstOrNull { it.id == creds.selectedAccountId } ?: accounts.firstOrNull()
          _uiState.update { it.copy(accounts = accounts, selectedAccount = chosen) }
          if (chosen != null) {
            prefsStore.saveSelectedAccount(chosen.id, chosen.name)
            refreshWorkersInternal(chosen.id, forceRefresh = false)
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
    flushActiveEditorBuffer()
    val state = _uiState.value
    if (state.workspace != null && state.editorSettings.warnOnLeaveDirtyWorkspace && state.hasUnsavedChanges) {
      showUnsavedWarning(
        action = PendingUnsavedAction.SignOut,
        title = "Unsaved Changes Before Sign Out",
        message = "Signing out clears local unsaved drafts for '${state.workspace.scriptName}'. Deploy first or discard changes to sign out.",
        allowKeepLocalDraft = false,
      )
      return
    }
    signOutInternal()
  }

  private fun signOutInternal() {
    oauthJob?.cancel()
    stopTailIfRunning()
    viewModelScope.launch {
      prefsStore.clearCredentials()
      autoSaveStore.clearAllDrafts()
      withContext(Dispatchers.IO) { apiCacheStore.clearAll() }
      lastPersistedDraftDigests.clear()
      lastPersistedNonSpacingDigests.clear()
      lastLocallyAutoSavedNonSpacing.clear()
      pendingCloudDrafts.clear()
      periodicAutoSaveJob?.cancel()
      undoStacks.clear()
      redoStacks.clear()
      ramDb.clearAll()
      _uiState.value =
        IdeUiState(
          isBootstrapping = false,
          editorSettings = _uiState.value.editorSettings,
          gitHubCreds = _uiState.value.gitHubCreds,
          gitHubUser = _uiState.value.gitHubUser,
          gitHubRateLimitLabel = _uiState.value.gitHubRateLimitLabel,
          gitHubRepos = _uiState.value.gitHubRepos,
          apiCollections = _uiState.value.apiCollections,
          apiHistory = _uiState.value.apiHistory,
          apiEnvVars = _uiState.value.apiEnvVars,
          apiEnvProfiles = _uiState.value.apiEnvProfiles,
          activeApiEnvProfileId = _uiState.value.activeApiEnvProfileId,
        )
    }
  }

  fun enterLocalScratchpad(template: WorkerTemplate = WorkerTemplate.HELLO_FETCH) {
    flushAutoSaveNow()
    syncActiveStateToRamDb()
    undoStacks.clear()
    redoStacks.clear()
    val ramKey = ramDb.workspaceKey(null, "local-worker")
    val existingRecord = ramDb.getWorkspace(ramKey)
    if (existingRecord != null) {
      ramDb.setActiveWorkspaceKey(ramKey)
      val ws = existingRecord.workingWorkspace
      val diags = rebuildDiagnosticsFull(ws.files)
      _uiState.update {
        it.copy(
          isLocalScratchpad = true,
          authError = null,
          workspace = ws,
          openTabPaths = existingRecord.openTabPaths,
          activeFilePath = existingRecord.activeFilePath,
          tabNavHistory = existingRecord.tabNavHistory,
          tabNavIndex = existingRecord.tabNavIndex,
          editorFieldValues = existingRecord.fieldValues,
          diagnostics = diags,
          restoredDraftBanner = null,
          activeWorkspaceHasPendingConfig = existingRecord.hasPendingChanges,
          ramDbStats = ramDb.computeStats(ramKey),
        )
      }
      appendLog("INFO", "Restored Local Project from '${projectDirManager.displayRootPath()}'")
      recordActiveSessionPointer()
      syncPeriodicAutoSaveLoop()
      return
    }
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
        isVirtualReadOnly = false,
      )
    val baseFiles = listOf(indexFile, corsFile, wranglerFile)
    val ws =
      WorkerWorkspace(
        scriptName = "local-worker",
        isEsModuleFormat = true,
        entryModulePath = "src/index.js",
        compatibilityDate = "2024-11-01",
        files = baseFiles,
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = false,
        accountSubdomain = "",
      )
    val record =
      ramDb.importWorkspace(
        key = ramKey,
        workspace = ws,
        defaultTabs = listOf("src/index.js", "src/utils/cors.js"),
        defaultActivePath = "src/index.js",
        isNewUnexported = false,
      )
    val diags = rebuildDiagnosticsFull(ws.files)
    persistWorkspaceToProjectDirAsync(ramKey)
    _uiState.update {
      it.copy(
        isLocalScratchpad = true,
        authError = null,
        workspace = ws,
        openTabPaths = record.openTabPaths,
        activeFilePath = record.activeFilePath,
        tabNavHistory = record.tabNavHistory,
        tabNavIndex = record.tabNavIndex,
        editorFieldValues = record.fieldValues,
        diagnostics = diags,
        restoredDraftBanner = null,
        activeWorkspaceHasPendingConfig = false,
        ramDbStats = ramDb.computeStats(ramKey),
      )
    }
    appendLog("INFO", "Initialized project in '${projectDirManager.displayRootPath()}'")
    recordActiveSessionPointer()
    syncPeriodicAutoSaveLoop()
  }

  fun enterLocalHome() {
    val ramKey = ramDb.workspaceKey(null, "local-worker")
    if (ramDb.getWorkspace(ramKey) == null) {
      seedDefaultLocalScratchpad(ramKey, WorkerTemplate.HELLO_FETCH)
    }
    _uiState.update {
      it.copy(
        isLocalScratchpad = true,
        isGitHubIdeOnlyMode = false,
        showHomeGitHubHub = false,
        homeTab = HomeTab.PROJECTS,
        workspace = null,
        openTabPaths = emptyList(),
        activeFilePath = null,
        authError = null,
        ramWorkspaces = ramDb.listWorkspaces(),
        ramDbStats = ramDb.computeStats(null),
      )
    }
    appendLog("INFO", "Opened Local Projects Home")
  }

  private fun seedDefaultLocalScratchpad(ramKey: String, template: WorkerTemplate): RamWorkspaceRecord {
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
        isVirtualReadOnly = false,
      )
    val baseFiles = listOf(indexFile, corsFile, wranglerFile)
    val ws =
      WorkerWorkspace(
        scriptName = "local-worker",
        isEsModuleFormat = true,
        entryModulePath = "src/index.js",
        compatibilityDate = "2024-11-01",
        files = baseFiles,
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = false,
        accountSubdomain = "",
      )
    val record =
      ramDb.importWorkspace(
        key = ramKey,
        workspace = ws,
        defaultTabs = listOf("src/index.js", "src/utils/cors.js"),
        defaultActivePath = "src/index.js",
        isNewUnexported = false,
      )
    persistWorkspaceToProjectDirAsync(ramKey)
    return record
  }

  fun refreshStoragePermissionAndReload() {
    viewModelScope.launch {
      val migrated =
        withContext(Dispatchers.IO) {
          projectDirManager.refreshRootDirectoryAndMigrate()
        }
      if (migrated) {
        val activeKey = activeWorkspaceKey()
        syncActiveStateToRamDb()
        val diskList = withContext(Dispatchers.IO) { projectDirManager.loadAllWorkspaces() }
        diskList.forEach { rec ->
          if (rec.key != activeKey) {
            ramDb.restoreRecord(rec, setActive = false)
          }
        }
        if (activeKey != null) {
          ramDb.getWorkspace(activeKey)?.let { activeRec ->
            withContext(Dispatchers.IO) { projectDirManager.saveWorkspaceRecord(activeRec) }
          }
        }
      }
      val summary = withContext(Dispatchers.IO) { projectDirManager.computeStorageSummary() }
      _uiState.update { state ->
        state.copy(
          projectStorageSummary = summary,
          ramWorkspaces = ramDb.listWorkspaces(),
          ramDbStats = ramDb.computeStats(activeWorkspaceKey(state)),
          statusMessage =
            if (migrated && summary.isUsingNativeDeviceStorage) {
              "Dedicated folder active: ${summary.rootAbsolutePath}"
            } else {
              state.statusMessage
            },
        )
      }
    }
  }

  fun openRamWorkspace(key: String) {
    flushActiveEditorBuffer()
    val state = _uiState.value
    val activeKey = activeWorkspaceKey(state)
    if (
      state.workspace != null &&
        activeKey != null &&
        activeKey != key &&
        state.editorSettings.warnOnLeaveDirtyWorkspace &&
        state.hasUnsavedChanges
    ) {
      showUnsavedWarning(
        action = PendingUnsavedAction.SwitchRamWorkspace(key),
        title = "Unsaved Changes in ${state.workspace.scriptName}",
        message = "You have modified files before switching projects. Keep a local draft, deploy, or discard edits.",
        allowKeepLocalDraft = true,
      )
      return
    }
    openRamWorkspaceInternal(key)
  }

  private fun openRamWorkspaceInternal(key: String) {
    val record = ramDb.getWorkspace(key) ?: return
    stopTailIfRunning()
    flushAutoSaveNow()
    syncActiveStateToRamDb()
    ramDb.setActiveWorkspaceKey(key)
    val cachedSub =
      _uiState.value.selectedAccount?.id?.let { ramDb.getInfraCache(it)?.subdomain }.orEmpty()
    val rawWs = record.workingWorkspace
    val ws =
      if (rawWs.accountSubdomain.isBlank() && cachedSub.isNotBlank()) {
        rawWs.copy(accountSubdomain = cachedSub)
      } else {
        rawWs
      }
    val diags = rebuildDiagnosticsFull(ws.files)
    val isLocal = key.startsWith("local_")
    val baselineMap =
      record.importedSnapshot.files
        .filterNot { it.isVirtualReadOnly || it.path == "wrangler.jsonc" }
        .associate { it.path to it.content }
    val resolvedPreview = resolveDefaultPreviewUrl(ws)
    _uiState.update { state ->
      state.copy(
        isLocalScratchpad = isLocal || state.credentials == null,
        isGitHubIdeOnlyMode = false,
        showHomeGitHubHub = false,
        projectMode = ProjectMode.CODE,
        sidebarSection = SidebarSection.EXPLORER,
        workspace = ws,
        openTabPaths = record.openTabPaths,
        activeFilePath = record.activeFilePath,
        tabNavHistory = record.tabNavHistory,
        tabNavIndex = record.tabNavIndex,
        editorFieldValues = record.fieldValues,
        editorRevision = state.editorRevision + 1L,
        diagnostics = diags,
        restoredDraftBanner = null,
        activeWorkspaceHasPendingConfig = record.hasPendingChanges,
        ramDbStats = ramDb.computeStats(key),
        ramWorkspaces = ramDb.listWorkspaces(),
        previewUrl = resolvedPreview,
        previewHttpResult = null,
        apiRequest =
          if (resolvedPreview.isNotBlank()) {
            ApiRequestUtils.applyDeployedWorkerUrl(state.apiRequest, resolvedPreview, forceReplace = false)
          } else {
            state.apiRequest
          },
        gitWorkingBaseline = baselineMap,
      )
    }
    appendLog("INFO", "Opened '${ws.scriptName}' from ${projectDirManager.displayRootPath()}")
    recordActiveSessionPointer()
    syncPeriodicAutoSaveLoop()
  }

  fun deleteLocalRamWorkspace(key: String) {
    val activeKey = activeWorkspaceKey()
    ramDb.removeWorkspace(key)
    clearLocalAutoSaveTrackingForWorkspace(key)
    pendingCloudDrafts.remove(key)
    autoSaveIoScope.launch {
      autoSaveStore.clearWorkspaceDraft(key)
      projectDirManager.deleteWorkspaceDirectory(key)
      val summary = projectDirManager.computeStorageSummary()
      _uiState.update { it.copy(projectStorageSummary = summary) }
    }
    _uiState.update { state ->
      val wasActive = activeKey == key
      state.copy(
        workspace = if (wasActive) null else state.workspace,
        openTabPaths = if (wasActive) emptyList() else state.openTabPaths,
        activeFilePath = if (wasActive) null else state.activeFilePath,
        ramDbStats = ramDb.computeStats(if (wasActive) null else activeKey),
        ramWorkspaces = ramDb.listWorkspaces(),
        statusMessage = "Removed project from directory",
      )
    }
  }

  fun closeWorkspaceToHome() {
    flushActiveEditorBuffer()
    val state = _uiState.value
    if (state.workspace != null && state.editorSettings.warnOnLeaveDirtyWorkspace && state.hasUnsavedChanges) {
      showUnsavedWarning(
        action = PendingUnsavedAction.LeaveWorkspace,
        title = "Unsaved Changes in ${state.workspace.scriptName}",
        message = "Modified files have not been deployed or reverted. Keep a local draft or discard changes before leaving.",
        allowKeepLocalDraft = true,
      )
      return
    }
    closeWorkspaceToHomeInternal()
  }

  private fun closeWorkspaceToHomeInternal() {
    flushActiveEditorBuffer()
    stopTailIfRunning()
    flushAutoSaveNow(clearSessionPointer = true)
    periodicAutoSaveJob?.cancel()
    val closingKey = activeWorkspaceKey()
    syncActiveStateToRamDb()
    closingKey?.let { persistWorkspaceToProjectDirAsync(it, refreshSummary = true) }
    ramDb.setActiveWorkspaceKey(null)
    _uiState.update {
      it.copy(
        workspace = null,
        openTabPaths = emptyList(),
        activeFilePath = null,
        showFindBar = false,
        showBottomPanel = false,
        isBottomPanelExpanded = false,
        isPreviewFullScreen = false,
        isGitHubIdeOnlyMode = false,
        externalFileUris = emptyMap(),
        externalBinaryAssets = emptyMap(),
        externalProjectRootPath = null,
        projectMode = ProjectMode.CODE,
        sidebarSection = SidebarSection.EXPLORER,
        ramWorkspaces = ramDb.listWorkspaces(),
        ramDbStats = ramDb.computeStats(null),
      )
    }
  }

  fun setHomeTab(tab: HomeTab) {
    _uiState.update { it.copy(homeTab = tab, showHomeGitHubHub = false) }
    if (!_uiState.value.isLocalScratchpad && _uiState.value.credentials != null) {
      when (tab) {
        HomeTab.INFRA -> loadCloudflareInfra(forceRefresh = false)
        HomeTab.PAGES -> loadPagesProjectsInternal(forceRefresh = false)
        HomeTab.PROJECTS -> Unit
      }
    }
  }

  fun setProjectMode(mode: ProjectMode) {
    _uiState.update { state ->
      val nextBottomTab =
        when (mode) {
          ProjectMode.TEST ->
            if (state.activeBottomTab in setOf(BottomPanelTab.LOCAL_RUN, BottomPanelTab.WORKER_PREVIEW, BottomPanelTab.WORKER_TEST, BottomPanelTab.API_TESTER)) {
              state.activeBottomTab
            } else {
              BottomPanelTab.LOCAL_RUN
            }
          ProjectMode.CONSOLE ->
            if (state.activeBottomTab in setOf(BottomPanelTab.TAIL_LOGS, BottomPanelTab.PROBLEMS, BottomPanelTab.CONSOLE)) {
              state.activeBottomTab
            } else {
              BottomPanelTab.TAIL_LOGS
            }
          else -> state.activeBottomTab
        }
      state.copy(
        projectMode = mode,
        activeBottomTab = nextBottomTab,
        isPreviewFullScreen = if (mode == ProjectMode.TEST) state.isPreviewFullScreen else false,
      )
    }
    if (mode == ProjectMode.CONFIG && !_uiState.value.isLocalScratchpad && _uiState.value.credentials != null) {
      loadCloudflareInfra(forceRefresh = false)
    }
  }

  fun setShowHomeGitHubHub(show: Boolean) {
    _uiState.update { it.copy(showHomeGitHubHub = show) }
  }

  fun setShowHomeSettings(show: Boolean) {
    _uiState.update { it.copy(showHomeSettings = show) }
  }

  fun exitScratchpadToAuth() {
    stopTailIfRunning()
    flushAutoSaveNow(clearSessionPointer = true)
    periodicAutoSaveJob?.cancel()
    syncActiveStateToRamDb()
    _uiState.update {
      it.copy(
        isLocalScratchpad = false,
        isGitHubIdeOnlyMode = false,
        showHomeGitHubHub = false,
        workspace = null,
        openTabPaths = emptyList(),
        activeFilePath = null,
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
    flushActiveEditorBuffer()
    val state = _uiState.value
    if (
      state.workspace != null &&
        account.id != state.selectedAccount?.id &&
        state.editorSettings.warnOnLeaveDirtyWorkspace &&
        state.hasUnsavedChanges
    ) {
      showUnsavedWarning(
        action = PendingUnsavedAction.SwitchAccount(account),
        title = "Unsaved Changes Before Account Switch",
        message = "Switching to '${account.name}' will close '${state.workspace.scriptName}'. Keep a local draft or discard changes.",
        allowKeepLocalDraft = true,
      )
      return
    }
    selectAccountInternal(account)
  }

  private fun selectAccountInternal(account: CfAccount) {
    stopTailIfRunning()
    viewModelScope.launch {
      prefsStore.saveSelectedAccount(account.id, account.name)
      _uiState.update {
        it.copy(
          selectedAccount = account,
          accountSubdomain = ramDb.getInfraCache(account.id)?.subdomain.orEmpty(),
          workspace = null,
          openTabPaths = emptyList(),
          activeFilePath = null,
          kvNamespaces = emptyList(),
          selectedKvNamespace = null,
          kvKeys = emptyList(),
          zones = emptyList(),
        )
      }
      refreshWorkersInternal(account.id, forceRefresh = false)
    }
  }

  fun refreshWorkers() {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      if (_uiState.value.accounts.size <= 1) {
        repository.loadAccounts().onSuccess { list ->
          if (list.isNotEmpty()) {
            _uiState.update { st ->
              st.copy(
                accounts = list,
                selectedAccount = list.firstOrNull { it.id == accountId } ?: st.selectedAccount,
              )
            }
          }
        }
      }
      refreshWorkersInternal(accountId, forceRefresh = true)
    }
  }

  fun clearApiResponseCache() {
    viewModelScope.launch {
      val removed = withContext(Dispatchers.IO) { apiCacheStore.clearAll() }
      _uiState.value.selectedAccount?.id?.let { accId ->
        ramDb.updateInfraCache(accId) { prev -> prev.copy(fetchedAtEpochMs = 0L) }
      }
      _uiState.update {
        it.copy(
          statusMessage =
            if (removed > 0) "Cleared $removed cached API snapshot(s)"
            else "API cache is already clean",
        )
      }
    }
  }

  private fun maybeAutoReopenPendingCloudDraft(accountId: String, list: List<CfWorkerScript>) {
    val pointer = pendingRestoreSessionPointer
    if (!hasAttemptedAutoReopen &&
      _uiState.value.workspace == null &&
      pointer != null &&
      !pointer.isLocalScratchpad &&
      pointer.accountId == accountId &&
      pendingCloudDrafts.containsKey(pointer.workspaceKey)
    ) {
      hasAttemptedAutoReopen = true
      pendingRestoreSessionPointer = null
      val targetScript = list.firstOrNull { it.id == pointer.scriptName }
      if (targetScript != null) {
        openWorkerInternal(targetScript, forceRefreshFromCloudflare = false)
      }
    }
  }

  private suspend fun refreshWorkersInternal(
    accountId: String,
    forceRefresh: Boolean = true,
  ) {
    val ttlHours = _uiState.value.editorSettings.apiCacheTtlHours
    if (!forceRefresh) {
      val cached = withContext(Dispatchers.IO) { apiCacheStore.loadWorkers(accountId) }
      if (cached != null) {
        ramDb.updateInfraCache(accountId) {
          it.copy(
            workers = cached.workers,
            subdomain = cached.subdomain.ifBlank { it.subdomain },
          )
        }
        _uiState.update {
          it.copy(
            workers = cached.workers,
            accountSubdomain = cached.subdomain.ifBlank { it.accountSubdomain },
            isLoadingWorkers = false,
          )
        }
        if (ApiResponseCacheStore.isFresh(cached.fetchedAtEpochMs, ttlHours)) {
          maybeAutoReopenPendingCloudDraft(accountId, cached.workers)
          return
        }
      }
    }

    _uiState.update { it.copy(isLoadingWorkers = true) }
    repository
      .loadWorkers(accountId)
      .onSuccess { list ->
        var cachedSub = ramDb.getInfraCache(accountId)?.subdomain.orEmpty()
        if (cachedSub.isBlank()) {
          cachedSub = repository.getAccountSubdomain(accountId)
        }
        ramDb.updateInfraCache(accountId) {
          it.copy(
            workers = list,
            subdomain = cachedSub.ifBlank { it.subdomain },
          )
        }
        withContext(Dispatchers.IO) {
          apiCacheStore.saveWorkers(accountId, list, cachedSub)
        }
        _uiState.update {
          it.copy(
            workers = list,
            accountSubdomain = cachedSub.ifBlank { it.accountSubdomain },
            isLoadingWorkers = false,
          )
        }
        appendLog("INFO", "Loaded ${list.size} Worker(s) from Cloudflare")
        maybeAutoReopenPendingCloudDraft(accountId, list)
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

  fun openWorker(
    script: CfWorkerScript,
    forceRefreshFromCloudflare: Boolean = false,
  ) {
    flushActiveEditorBuffer()
    val state = _uiState.value
    val activeWs = state.workspace
    if (activeWs != null && state.hasUnsavedChanges) {
      val sameWorkerForceReimport = forceRefreshFromCloudflare && activeWs.scriptName == script.id
      if (sameWorkerForceReimport && state.editorSettings.warnOnCloudOverwrite) {
        showUnsavedWarning(
          action = PendingUnsavedAction.SwitchWorker(script, forceRefreshFromCloudflare = true),
          title = "Overwrite Local Changes?",
          message = "Re-importing '${script.id}' from Cloudflare will overwrite unsaved edits in this workspace.",
          allowKeepLocalDraft = false,
        )
        return
      } else if (activeWs.scriptName != script.id && state.editorSettings.warnOnLeaveDirtyWorkspace) {
        showUnsavedWarning(
          action = PendingUnsavedAction.SwitchWorker(script, forceRefreshFromCloudflare = forceRefreshFromCloudflare),
          title = "Unsaved Changes in ${activeWs.scriptName}",
          message = "You have unsaved edits before opening '${script.id}'. Keep a local draft, deploy, or discard edits.",
          allowKeepLocalDraft = true,
        )
        return
      }
    }
    openWorkerInternal(script, forceRefreshFromCloudflare)
  }

  private fun openWorkerInternal(
    script: CfWorkerScript,
    forceRefreshFromCloudflare: Boolean = false,
  ) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    stopTailIfRunning()
    flushAutoSaveNow()
    syncActiveStateToRamDb()

    val ramKey = ramDb.workspaceKey(accountId, script.id)
    val cachedRecord = ramDb.getWorkspace(ramKey)
    if (!forceRefreshFromCloudflare && cachedRecord != null) {
      ramDb.setActiveWorkspaceKey(ramKey)
      val cachedSub = ramDb.getInfraCache(accountId)?.subdomain.orEmpty()
      val rawWs = cachedRecord.workingWorkspace
      val ws =
        if (rawWs.accountSubdomain.isBlank() && cachedSub.isNotBlank()) {
          rawWs.copy(accountSubdomain = cachedSub)
        } else {
          rawWs
        }
      val diags = rebuildDiagnosticsFull(ws.files)
      val baselineMap =
        cachedRecord.importedSnapshot.files
          .filterNot { it.isVirtualReadOnly || it.path == "wrangler.jsonc" }
          .associate { it.path to it.content }
      val resolvedPreview = resolveDefaultPreviewUrl(ws)
      _uiState.update { state ->
        state.copy(
          isLocalScratchpad = false,
          showHomeGitHubHub = false,
          projectMode = ProjectMode.CODE,
          sidebarSection = SidebarSection.EXPLORER,
          accountSubdomain = ws.accountSubdomain.ifBlank { state.accountSubdomain },
          workspace = ws,
          openTabPaths = cachedRecord.openTabPaths,
          activeFilePath = cachedRecord.activeFilePath,
          tabNavHistory = cachedRecord.tabNavHistory,
          tabNavIndex = cachedRecord.tabNavIndex,
          editorFieldValues = cachedRecord.fieldValues,
          editorRevision = state.editorRevision + 1L,
          diagnostics = diags,
          isOpeningWorker = false,
          statusMessage = "Opened '${ws.scriptName}' from local project folder",
          restoredDraftBanner = null,
          activeWorkspaceHasPendingConfig = cachedRecord.hasPendingChanges,
          ramDbStats = ramDb.computeStats(ramKey),
          previewUrl = resolvedPreview,
          previewHttpResult = null,
          previewReloadNonce = state.previewReloadNonce + 1L,
          apiRequest =
            if (resolvedPreview.isNotBlank()) {
              ApiRequestUtils.applyDeployedWorkerUrl(state.apiRequest, resolvedPreview, forceReplace = false)
            } else {
              state.apiRequest
            },
          gitWorkingBaseline = baselineMap,
        )
      }
      seedLocalAutoSaveBaseline(ramKey, cachedRecord)
      appendLog("INFO", "Switched to '${ws.scriptName}' from local project directory")
      recordActiveSessionPointer()
      syncPeriodicAutoSaveLoop()
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isOpeningWorker = true, statusMessage = "Syncing ${script.id} to project directory…") }
      repository
        .openWorkerWorkspace(accountId, script)
        .onSuccess { fetchedWs ->
          if (fetchedWs.accountSubdomain.isNotBlank()) {
            ramDb.updateInfraCache(accountId) { it.copy(subdomain = fetchedWs.accountSubdomain) }
          }
          val savedDraft =
            if (!forceRefreshFromCloudflare) {
              pendingCloudDrafts.remove(ramKey) ?: withContext(Dispatchers.IO) { autoSaveStore.loadWorkspaceDraft(ramKey) }
            } else {
              pendingCloudDrafts.remove(ramKey)
              withContext(Dispatchers.IO) { autoSaveStore.clearWorkspaceDraft(ramKey) }
              lastPersistedDraftDigests.remove(ramKey)
              null
            }

          val (mergedWs, hasDraftDiff) =
            if (savedDraft != null) AutoSaveDraftStore.mergeDraftIntoWorkspace(fetchedWs, savedDraft)
            else fetchedWs to false

          val defaultTabs =
            if (hasDraftDiff && savedDraft != null && savedDraft.openTabs.isNotEmpty()) savedDraft.openTabs
            else listOf(mergedWs.entryModulePath)
          val defaultActive =
            if (hasDraftDiff && savedDraft?.activeFilePath != null) savedDraft.activeFilePath
            else mergedWs.entryModulePath

          val record =
            ramDb.importWorkspace(
              key = ramKey,
              workspace = fetchedWs,
              defaultTabs = defaultTabs,
              defaultActivePath = defaultActive,
              isNewUnexported = false,
            )
          val activeRecord =
            if (hasDraftDiff && savedDraft != null) {
              val restoredSelections =
                mergedWs.files.associate { wf ->
                  val sel = savedDraft.cursorSelections[wf.path]
                  val range =
                    if (sel != null) TextRange(sel.first.coerceIn(0, wf.content.length), sel.second.coerceIn(0, wf.content.length))
                    else TextRange.Zero
                  wf.path to TextFieldValue(wf.content, range)
                }
              ramDb.updateWorkspace(ramKey) { rec ->
                rec.copy(
                  workingWorkspace = mergedWs,
                  openTabPaths = record.openTabPaths,
                  activeFilePath = record.activeFilePath,
                  fieldValues = restoredSelections,
                )
              } ?: record
            } else {
              record
            }

          val ws = activeRecord.workingWorkspace
          val diags = rebuildDiagnosticsFull(ws.files)
          persistWorkspaceToProjectDirAsync(ramKey)
          val baselineMap =
            fetchedWs.files
              .filterNot { it.isVirtualReadOnly || it.path == "wrangler.jsonc" }
              .associate { it.path to it.content }
          val banner =
            if (hasDraftDiff && savedDraft != null) {
              val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(savedDraft.savedAtEpochMs))
              "Restored auto-saved draft from $timeStr (${ws.files.count { it.isDirty }} edited)"
            } else null
          val resolvedPreview = resolveDefaultPreviewUrl(ws)
          _uiState.update { state ->
            state.copy(
              isLocalScratchpad = false,
              showHomeGitHubHub = false,
              projectMode = ProjectMode.CODE,
              sidebarSection = SidebarSection.EXPLORER,
              accountSubdomain = ws.accountSubdomain.ifBlank { state.accountSubdomain },
              workspace = ws,
              openTabPaths = activeRecord.openTabPaths,
              activeFilePath = activeRecord.activeFilePath,
              tabNavHistory = activeRecord.tabNavHistory,
              tabNavIndex = activeRecord.tabNavIndex,
              editorFieldValues = activeRecord.fieldValues,
              editorRevision = state.editorRevision + 1L,
              diagnostics = diags,
              isOpeningWorker = false,
              statusMessage = "Synced '${ws.scriptName}' (${ws.files.size} files) to project folder",
              restoredDraftBanner = banner,
              activeWorkspaceHasPendingConfig = activeRecord.hasPendingChanges,
              ramDbStats = ramDb.computeStats(ramKey),
              workerVersions = emptyList(),
              previewUrl = resolvedPreview,
              previewHttpResult = null,
              previewConsoleEntries = emptyList(),
              previewReloadNonce = state.previewReloadNonce + 1L,
              apiRequest =
                if (resolvedPreview.isNotBlank()) {
                  ApiRequestUtils.applyDeployedWorkerUrl(state.apiRequest, resolvedPreview, forceReplace = false)
                } else {
                  state.apiRequest
                },
              httpTestResult = null,
              tailLogs = emptyList(),
              linkedGitRepo = null,
              gitWorkingBaseline = baselineMap,
              gitUnstagedExcludedPaths = emptySet(),
              localGitCommits = emptyList(),
              gitPushStatusMessage = null,
            )
          }
          seedLocalAutoSaveBaseline(ramKey, activeRecord)
          appendLog("INFO", "Synced Worker '${ws.scriptName}' (${ws.files.size} files) into project folder")
          recordActiveSessionPointer()
          syncPeriodicAutoSaveLoop()
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isOpeningWorker = false,
              statusMessage = err.localizedMessage ?: "Could not import Worker",
            )
          }
          appendLog("ERROR", err.localizedMessage ?: "Failed to import Worker '${script.id}'")
        }
    }
  }

  fun reimportActiveWorkerFromCloudflare() {
    flushActiveEditorBuffer()
    val state = _uiState.value
    val ws = state.workspace ?: return
    if (state.isLocalScratchpad) return
    if (state.editorSettings.warnOnCloudOverwrite && state.hasUnsavedChanges) {
      showUnsavedWarning(
        action = PendingUnsavedAction.ReimportFromCloudflare,
        title = "Overwrite Local Changes?",
        message = "Re-importing '${ws.scriptName}' from Cloudflare will discard unsaved local edits.",
        allowKeepLocalDraft = false,
      )
      return
    }
    reimportActiveWorkerInternal()
  }

  private fun reimportActiveWorkerInternal() {
    val ws = _uiState.value.workspace ?: return
    if (_uiState.value.isLocalScratchpad) return
    val script = _uiState.value.workers.firstOrNull { it.id == ws.scriptName } ?: CfWorkerScript(id = ws.scriptName)
    openWorkerInternal(script, forceRefreshFromCloudflare = true)
  }

  fun revertWorkspaceToImportedBaseline() {
    val key = activeWorkspaceKey() ?: return
    val reverted = ramDb.revertWorkspaceToImportedBaseline(key) ?: return
    val ws = reverted.workingWorkspace
    persistWorkspaceToProjectDirAsync(key)
    clearLocalAutoSaveTrackingForWorkspace(key)
    seedLocalAutoSaveBaseline(key, reverted)
    autoSaveIoScope.launch { autoSaveWriteMutex.withLock { autoSaveStore.clearWorkspaceDraft(key) } }
    val diags = rebuildDiagnosticsFull(ws.files)
    _uiState.update { state ->
      state.copy(
        workspace = ws,
        openTabPaths = reverted.openTabPaths,
        activeFilePath = reverted.activeFilePath,
        tabNavHistory = reverted.tabNavHistory,
        tabNavIndex = reverted.tabNavIndex,
        editorFieldValues = reverted.fieldValues,
        editorRevision = state.editorRevision + 1L,
        diagnostics = diags,
        activeWorkspaceHasPendingConfig = reverted.hasPendingChanges,
        ramDbStats = ramDb.computeStats(key),
        statusMessage = "Reverted '${ws.scriptName}' to clean baseline",
      )
    }
    appendLog("INFO", "Reverted '${ws.scriptName}' to clean baseline")
  }

  fun createNewWorker(name: String, template: WorkerTemplate) {
    flushAutoSaveNow()
    syncActiveStateToRamDb()
    val isScratchpad = _uiState.value.isLocalScratchpad || _uiState.value.selectedAccount == null
    val accountId = if (isScratchpad) null else _uiState.value.selectedAccount?.id
    val cleanName =
      name.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-")
        .ifBlank { if (isScratchpad) "local-worker" else "my-edge-worker" }
    val indexFile =
      WorkerFile(
        path = "index.js",
        content = template.code,
        originalContent = template.code,
        contentType = "application/javascript+module",
        isEntryPoint = true,
      )
    val settings = _uiState.value.editorSettings
    val baseWs =
      WorkerWorkspace(
        scriptName = cleanName,
        isEsModuleFormat = true,
        entryModulePath = "index.js",
        compatibilityDate = settings.defaultCompatibilityDate.ifBlank { "2024-11-01" },
        compatibilityFlags = if (settings.defaultNodejsCompat) listOf("nodejs_compat") else emptyList(),
        files = listOf(indexFile),
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = !isScratchpad && settings.defaultWorkersDevEnabled,
        accountSubdomain = accountId?.let { ramDb.getInfraCache(it)?.subdomain }.orEmpty(),
        hasModuleSetChanged = !isScratchpad,
      )
    val ws =
      regenerateWranglerFileInWorkspace(baseWs).let { built ->
        built.copy(files = ProjectDirectoryManager.organizeWorkspaceFiles(built.files))
      }
    val key = ramDb.workspaceKey(accountId, cleanName)
    val record =
      ramDb.importWorkspace(
        key = key,
        workspace = ws,
        defaultTabs = listOf("index.js", "wrangler.jsonc"),
        defaultActivePath = "index.js",
        isNewUnexported = !isScratchpad,
      )
    val diags = rebuildDiagnosticsFull(ws.files)
    val initialPreview = resolveDefaultPreviewUrl(ws)
    _uiState.update { state ->
      val updatedWorkers =
        if (!isScratchpad && state.workers.none { it.id == cleanName }) {
          listOf(CfWorkerScript(id = cleanName)) + state.workers
        } else {
          state.workers
        }
      state.copy(
        workers = updatedWorkers,
        workspace = ws,
        projectMode = ProjectMode.CODE,
        sidebarSection = SidebarSection.EXPLORER,
        showHomeGitHubHub = false,
        openTabPaths = record.openTabPaths,
        activeFilePath = record.activeFilePath,
        tabNavHistory = record.tabNavHistory,
        tabNavIndex = record.tabNavIndex,
        editorFieldValues = record.fieldValues,
        diagnostics = diags,
        previewUrl = initialPreview,
        apiRequest =
          if (initialPreview.isNotBlank()) {
            ApiRequestUtils.applyDeployedWorkerUrl(state.apiRequest, initialPreview, forceReplace = true)
          } else {
            state.apiRequest
          },
        activeWorkspaceHasPendingConfig = record.hasPendingChanges,
        ramDbStats = ramDb.computeStats(key),
        statusMessage =
          if (isScratchpad) "Created '$cleanName' in ${projectDirManager.displayRootPath()}"
          else "Created '$cleanName' — deploying to Cloudflare…",
      )
    }
    appendLog("INFO", "Created '$cleanName' in ${projectDirManager.displayRootPath()}")
    recordActiveSessionPointer()
    syncPeriodicAutoSaveLoop()

    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        projectDirManager.saveWorkspaceRecord(record)
        val summary = projectDirManager.computeStorageSummary()
        _uiState.update { it.copy(projectStorageSummary = summary) }
      }
      if (!isScratchpad && accountId != null) {
        deployWorkspaceInternal(accountId = accountId, workspaceKey = key, isNewWorker = true)
      }
    }
  }

  fun createNewWorkerFromUris(
    name: String,
    uris: List<Uri>,
    folderPrefix: String = "",
  ) {
    if (uris.isEmpty()) return
    flushAutoSaveNow()
    syncActiveStateToRamDb()
    viewModelScope.launch {
      val picked = DeviceFileHelper.readFiles(getApplication(), uris)
      if (picked.isEmpty()) {
        _uiState.update { it.copy(statusMessage = "Could not read selected file(s)") }
        return@launch
      }
      val workerFiles =
        picked.mapIndexed { idx, f ->
          val path = ProjectDirectoryManager.normalizeRelativePath(f.fileName, fallbackFolder = folderPrefix)
            .ifBlank { f.fileName }
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
      val finalFiles =
        ProjectDirectoryManager.organizeWorkspaceFiles(
          workerFiles.map { it.copy(isEntryPoint = it.path == resolvedEntry) },
        )
      val isScratchpad = _uiState.value.isLocalScratchpad || _uiState.value.selectedAccount == null
      val accountId = if (isScratchpad) null else _uiState.value.selectedAccount?.id
      val cleanName = name.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-").ifBlank { "uploaded-worker" }

      val settings = _uiState.value.editorSettings
      val baseWs =
        WorkerWorkspace(
          scriptName = cleanName,
          isEsModuleFormat = true,
          entryModulePath = resolvedEntry,
          compatibilityDate = settings.defaultCompatibilityDate.ifBlank { "2024-11-01" },
          compatibilityFlags = if (settings.defaultNodejsCompat) listOf("nodejs_compat") else emptyList(),
          files = finalFiles,
          bindings = emptyList(),
          routes = emptyList(),
          workersDevEnabled = !isScratchpad && settings.defaultWorkersDevEnabled,
          accountSubdomain = accountId?.let { ramDb.getInfraCache(it)?.subdomain }.orEmpty(),
          hasModuleSetChanged = !isScratchpad,
        )
      val ws =
        regenerateWranglerFileInWorkspace(baseWs).let { built ->
          built.copy(files = ProjectDirectoryManager.organizeWorkspaceFiles(built.files))
        }
      val key = ramDb.workspaceKey(accountId, cleanName)
      val record =
        ramDb.importWorkspace(
          key = key,
          workspace = ws,
          defaultTabs = finalFiles.map { it.path },
          defaultActivePath = resolvedEntry,
          isNewUnexported = !isScratchpad,
        )
      withContext(Dispatchers.IO) {
        projectDirManager.recordOrganizedImport(cleanName, finalFiles)
        projectDirManager.saveWorkspaceRecord(record)
        val summary = projectDirManager.computeStorageSummary()
        _uiState.update { it.copy(projectStorageSummary = summary) }
      }
      val diags = rebuildDiagnosticsFull(ws.files)
      val initialPreview = resolveDefaultPreviewUrl(ws)
      _uiState.update { state ->
        val updatedWorkers =
          if (!isScratchpad && state.workers.none { it.id == cleanName }) {
            listOf(CfWorkerScript(id = cleanName)) + state.workers
          } else {
            state.workers
          }
        state.copy(
          workers = updatedWorkers,
          workspace = ws,
          projectMode = ProjectMode.CODE,
          sidebarSection = SidebarSection.EXPLORER,
          showHomeGitHubHub = false,
          openTabPaths = record.openTabPaths,
          activeFilePath = record.activeFilePath,
          tabNavHistory = record.tabNavHistory,
          tabNavIndex = record.tabNavIndex,
          editorFieldValues = record.fieldValues,
          diagnostics = diags,
          previewUrl = initialPreview,
          apiRequest =
            if (initialPreview.isNotBlank()) {
              ApiRequestUtils.applyDeployedWorkerUrl(state.apiRequest, initialPreview, forceReplace = true)
            } else {
              state.apiRequest
            },
          activeWorkspaceHasPendingConfig = record.hasPendingChanges,
          ramDbStats = ramDb.computeStats(key),
          statusMessage =
            if (isScratchpad) "Imported ${finalFiles.size} file(s) into '$cleanName'"
            else "Imported ${finalFiles.size} file(s) — deploying '$cleanName'…",
        )
      }
      appendLog("INFO", "Organized & imported ${finalFiles.size} file(s) into '$cleanName'")
      recordActiveSessionPointer()
      syncPeriodicAutoSaveLoop()

      if (!isScratchpad && accountId != null) {
        deployWorkspaceInternal(accountId = accountId, workspaceKey = key, isNewWorker = true)
      }
    }
  }

  /**
   * Direct single or batch upload from device storage into the currently open Worker!
   * Normalizes paths, organizes files cleanly inside the project directory, and optionally deploys.
   */
  fun uploadFilesToActiveWorker(
    uris: List<Uri>,
    folderPrefix: String = "",
    autoDeploy: Boolean = false,
  ) {
    if (uris.isEmpty()) return
    val ws = _uiState.value.workspace ?: return
    viewModelScope.launch {
      if (uris.size == 1 && DeviceFileHelper.isZipUri(getApplication(), uris.first())) {
        val readResult = DeviceFileHelper.readZipForWorkspace(getApplication(), uris.first())
        val archive =
          readResult.getOrElse { err ->
            _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Could not read ZIP archive") }
            return@launch
          }
        val plan =
          ZipWorkspaceImporter.buildImportPlan(
            readResult = archive,
            stripCommonRootFolder = true,
            defaultCompatibilityDate = ws.compatibilityDate,
          ).getOrElse { err ->
            _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "No readable files in ZIP") }
            return@launch
          }
        mergeWorkerFilesIntoActiveWorkspace(
          incomingFiles = plan.files,
          folderPrefix = folderPrefix,
          autoDeploy = autoDeploy,
          sourceLabel = "ZIP '${plan.archiveName}'",
        )
        return@launch
      }

      val picked: List<PickedDeviceFile> = DeviceFileHelper.readFiles(getApplication(), uris)
      if (picked.isEmpty()) {
        _uiState.update { it.copy(statusMessage = "No readable text/script files selected") }
        return@launch
      }

      val incoming =
        picked.map { item ->
          val targetPath =
            ProjectDirectoryManager.normalizeRelativePath(item.fileName, fallbackFolder = folderPrefix)
              .ifBlank { item.fileName }
          WorkerFile(
            path = targetPath,
            content = item.content,
            originalContent = "",
            contentType = MultipartWorkerParser.inferModuleMimeType(targetPath),
            isEntryPoint = false,
          )
        }
      mergeWorkerFilesIntoActiveWorkspace(
        incomingFiles = incoming,
        folderPrefix = "",
        autoDeploy = autoDeploy,
        sourceLabel = "${picked.size} file(s)",
      )
    }
  }

  private fun mergeWorkerFilesIntoActiveWorkspace(
    incomingFiles: List<WorkerFile>,
    folderPrefix: String,
    autoDeploy: Boolean,
    sourceLabel: String,
  ) {
    val ws = _uiState.value.workspace ?: return
    if (incomingFiles.isEmpty()) {
      _uiState.update { it.copy(statusMessage = "No importable files found") }
      return
    }
    val currentEditable = ws.files.filterNot { it.isVirtualReadOnly }.toMutableList()
    val virtualFiles = ws.files.filter { it.isVirtualReadOnly }
    val addedOrUpdatedPaths = mutableListOf<String>()
    val importedBatchFiles = mutableListOf<WorkerFile>()

    for (item in incomingFiles) {
      val combinedPath =
        if (folderPrefix.isBlank()) item.path
        else "${folderPrefix.trim('/').replace('\\', '/')}/${item.path.trimStart('/')}"
      val targetPath =
        ProjectDirectoryManager.normalizeRelativePath(combinedPath, fallbackFolder = "")
          .ifBlank { item.path }
      addedOrUpdatedPaths.add(targetPath)
      val existingIdx = currentEditable.indexOfFirst { it.path == targetPath }
      val wf =
        if (existingIdx >= 0) {
          currentEditable[existingIdx].copy(content = item.content)
        } else {
          WorkerFile(
            path = targetPath,
            content = item.content,
            originalContent = "",
            contentType = MultipartWorkerParser.inferModuleMimeType(targetPath),
            isEntryPoint = false,
          )
        }
      if (existingIdx >= 0) currentEditable[existingIdx] = wf else currentEditable.add(wf)
      importedBatchFiles.add(wf)
    }

    val nextFiles = ProjectDirectoryManager.organizeWorkspaceFiles(currentEditable + virtualFiles)
    val nextFieldValues =
      _uiState.value.editorFieldValues.toMutableMap().apply {
        importedBatchFiles.forEach { wf ->
          put(wf.path, TextFieldValue(wf.content))
        }
      }
    val tabsToAdd = addedOrUpdatedPaths.take(8)
    val nextTabs = (_uiState.value.openTabPaths + tabsToAdd).distinct()
    val nextActive = addedOrUpdatedPaths.firstOrNull() ?: _uiState.value.activeFilePath
    val diags = rebuildDiagnosticsFull(nextFiles)
    val updatedWs =
      ws.copy(
        files = nextFiles,
        isEsModuleFormat = true,
        hasModuleSetChanged = true,
      )

    _uiState.update { state ->
      state.copy(
        workspace = updatedWs,
        openTabPaths = nextTabs,
        activeFilePath = nextActive,
        editorFieldValues = nextFieldValues,
        editorRevision = state.editorRevision + 1L,
        diagnostics = diags,
        zipImportPreview = null,
        statusMessage =
          if (autoDeploy) "Imported ${importedBatchFiles.size} file(s) from $sourceLabel, deploying…"
          else "Imported ${importedBatchFiles.size} file(s) from $sourceLabel",
      )
    }
    activeWorkspaceKey()?.let { key ->
      val latest = _uiState.value
      val updatedRecord =
        ramDb.updateWorkspace(key) { rec ->
          rec.copy(
            workingWorkspace = updatedWs,
            openTabPaths = latest.openTabPaths,
            activeFilePath = latest.activeFilePath,
            fieldValues = latest.editorFieldValues,
          )
        }
      _uiState.update {
        it.copy(
          ramDbStats = ramDb.computeStats(key),
          ramWorkspaces = ramDb.listWorkspaces(),
        )
      }
      autoSaveIoScope.launch {
        projectDirManager.recordOrganizedImport(updatedWs.scriptName, importedBatchFiles)
        if (updatedRecord != null) projectDirManager.saveWorkspaceRecord(updatedRecord)
        val summary = projectDirManager.computeStorageSummary()
        _uiState.update { it.copy(projectStorageSummary = summary) }
      }
    }
    appendLog("INFO", "Imported & organized ${importedBatchFiles.size} file(s) from $sourceLabel")
    scheduleDraftSave()

    if (autoDeploy && !_uiState.value.isLocalScratchpad) {
      deployActiveWorker()
    }
  }

  fun exportActiveFileToUri(uri: Uri) {
    val active = _uiState.value.activeFile ?: return
    val projectName = _uiState.value.workspace?.scriptName ?: "worker"
    viewModelScope.launch {
      DeviceFileHelper.writeTextToUri(getApplication(), uri, active.content)
        .onSuccess {
          autoSaveIoScope.launch {
            projectDirManager.recordOrganizedExportFile(projectName, active.path, active.content)
            val summary = projectDirManager.computeStorageSummary()
            _uiState.update { it.copy(projectStorageSummary = summary) }
          }
          _uiState.update { it.copy(statusMessage = "Exported ${active.fileName} (saved in exports/$projectName)") }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Export failed") }
        }
    }
  }

  fun exportWorkspaceBundleToUri(uri: Uri) {
    val key = activeWorkspaceKey()
    if (key == null) {
      _uiState.update { it.copy(statusMessage = "Open a workspace first") }
      return
    }
    ramDb.exportWorkspaceBundleJson(key)
      .onSuccess { json ->
        viewModelScope.launch {
          DeviceFileHelper.writeTextToUri(getApplication(), uri, json)
            .onSuccess {
              val name = _uiState.value.workspace?.scriptName ?: "worker"
              autoSaveIoScope.launch {
                projectDirManager.recordOrganizedExportBundle(name, json)
                val summary = projectDirManager.computeStorageSummary()
                _uiState.update { it.copy(projectStorageSummary = summary) }
              }
              _uiState.update { it.copy(statusMessage = "Exported '$name' bundle to storage & exports/") }
              appendLog("INFO", "Exported organized bundle JSON for '$name'")
            }
            .onFailure { err ->
              _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Bundle export failed") }
            }
        }
      }
      .onFailure { err ->
        _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Bundle serialize failed") }
      }
  }

  fun importWorkspaceBundleFromUri(uri: Uri) {
    syncActiveStateToRamDb()
    viewModelScope.launch {
      if (DeviceFileHelper.isZipUri(getApplication(), uri)) {
        inspectZipForImport(uri, ZipImportTargetMode.NEW_PROJECT)
        return@launch
      }
      val picked = DeviceFileHelper.readFiles(getApplication(), listOf(uri))
      val first = picked.firstOrNull()
      if (first == null) {
        _uiState.update { it.copy(statusMessage = "Could not read workspace bundle file") }
        return@launch
      }
      importEdgeCodeBundleJson(first.content)
    }
  }

  private suspend fun importEdgeCodeBundleJson(
    rawJson: String,
    projectNameOverride: String? = null,
    deployNow: Boolean = false,
  ) {
    ramDb.importWorkspaceBundleJson(rawJson)
      .onSuccess { importedWs ->
        val isScratchpad = _uiState.value.isLocalScratchpad || _uiState.value.selectedAccount == null
        val accId = if (isScratchpad) null else _uiState.value.selectedAccount?.id
        val resolvedName =
          projectNameOverride
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { CloudflareNamingUtils.sanitizeWorkerName(it, allowUnderscore = isScratchpad) }
            ?.ifBlank { importedWs.scriptName }
            ?: importedWs.scriptName
        val ws =
          regenerateWranglerFileInWorkspace(importedWs.copy(scriptName = resolvedName)).let { built ->
            built.copy(files = ProjectDirectoryManager.organizeWorkspaceFiles(built.files))
          }
        val key = ramDb.workspaceKey(accId, ws.scriptName)
        val record =
          ramDb.importWorkspace(
            key = key,
            workspace = ws,
            defaultTabs = ws.files.filterNot { it.isVirtualReadOnly }.take(4).map { it.path },
            defaultActivePath = ws.entryModulePath,
            isNewUnexported = true,
          )
        withContext(Dispatchers.IO) {
          projectDirManager.recordOrganizedImport(ws.scriptName, ws.files, bundleJson = rawJson)
          projectDirManager.saveWorkspaceRecord(record)
          val summary = projectDirManager.computeStorageSummary()
          _uiState.update { it.copy(projectStorageSummary = summary) }
        }
        val diags = rebuildDiagnosticsFull(ws.files)
        val initialPreview = resolveDefaultPreviewUrl(ws)
        _uiState.update { state ->
          val updatedWorkers =
            if (!isScratchpad && state.workers.none { it.id == ws.scriptName }) {
              listOf(CfWorkerScript(id = ws.scriptName)) + state.workers
            } else {
              state.workers
            }
          state.copy(
            workers = updatedWorkers,
            workspace = ws,
            projectMode = ProjectMode.CODE,
            sidebarSection = SidebarSection.EXPLORER,
            showHomeGitHubHub = false,
            openTabPaths = record.openTabPaths,
            activeFilePath = record.activeFilePath,
            tabNavHistory = record.tabNavHistory,
            tabNavIndex = record.tabNavIndex,
            editorFieldValues = record.fieldValues,
            editorRevision = state.editorRevision + 1L,
            diagnostics = diags,
            previewUrl = initialPreview,
            apiRequest =
              if (initialPreview.isNotBlank()) {
                ApiRequestUtils.applyDeployedWorkerUrl(state.apiRequest, initialPreview, forceReplace = true)
              } else {
                state.apiRequest
              },
            activeWorkspaceHasPendingConfig = record.hasPendingChanges,
            ramDbStats = ramDb.computeStats(key),
            ramWorkspaces = ramDb.listWorkspaces(),
            zipImportPreview = null,
            statusMessage = "Imported & organized '${ws.scriptName}' (${ws.files.size} files)",
          )
        }
        appendLog("INFO", "Imported & organized bundle '${ws.scriptName}' into project directory")
        recordActiveSessionPointer()
        syncPeriodicAutoSaveLoop()
        if (deployNow && !isScratchpad && accId != null) {
          deployWorkspaceInternal(accountId = accId, workspaceKey = key, isNewWorker = true)
        }
      }
      .onFailure { err ->
        _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Invalid EdgeCode bundle JSON") }
      }
  }

  fun inspectZipForImport(
    uri: Uri,
    targetMode: ZipImportTargetMode,
  ) {
    _uiState.update {
      it.copy(
        zipImportPreview =
          ZipPreviewState(
            uri = uri,
            targetMode = targetMode,
            isLoading = true,
            stripRootFolder = true,
          ),
      )
    }
    viewModelScope.launch {
      val defaultCompat = _uiState.value.editorSettings.defaultCompatibilityDate.ifBlank { "2024-11-01" }
      val readResult = DeviceFileHelper.readZipForWorkspace(getApplication(), uri)
      readResult
        .onSuccess { archive ->
          ZipWorkspaceImporter.buildImportPlan(
            readResult = archive,
            stripCommonRootFolder = true,
            defaultCompatibilityDate = defaultCompat,
          )
            .onSuccess { plan ->
              _uiState.update { state ->
                val current = state.zipImportPreview
                if (current?.uri == uri) {
                  state.copy(
                    zipImportPreview =
                      current.copy(
                        isLoading = false,
                        errorMessage = null,
                        stripRootFolder = true,
                        archiveReadResult = archive,
                        plan = plan,
                      ),
                  )
                } else state
              }
            }
            .onFailure { err ->
              _uiState.update { state ->
                val current = state.zipImportPreview
                if (current?.uri == uri) {
                  state.copy(
                    zipImportPreview =
                      current.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Failed to parse ZIP contents",
                        archiveReadResult = archive,
                        plan = null,
                      ),
                  )
                } else state
              }
            }
        }
        .onFailure { err ->
          _uiState.update { state ->
            val current = state.zipImportPreview
            if (current?.uri == uri) {
              state.copy(
                zipImportPreview =
                  current.copy(
                    isLoading = false,
                    errorMessage = err.localizedMessage ?: "Could not open ZIP archive",
                    plan = null,
                  ),
              )
            } else state
          }
        }
    }
  }

  fun toggleZipPreviewStripRoot(stripRoot: Boolean) {
    val current = _uiState.value.zipImportPreview ?: return
    val archive = current.archiveReadResult ?: return
    val defaultCompat = _uiState.value.editorSettings.defaultCompatibilityDate.ifBlank { "2024-11-01" }
    val rebuilt =
      ZipWorkspaceImporter.buildImportPlan(
        readResult = archive,
        stripCommonRootFolder = stripRoot,
        defaultCompatibilityDate = defaultCompat,
      )
    _uiState.update { state ->
      state.copy(
        zipImportPreview =
          rebuilt.fold(
            onSuccess = { plan ->
              current.copy(stripRootFolder = stripRoot, errorMessage = null, plan = plan)
            },
            onFailure = { err ->
              current.copy(stripRootFolder = stripRoot, errorMessage = err.localizedMessage, plan = null)
            },
          ),
      )
    }
  }

  fun dismissZipImportPreview() {
    _uiState.update { it.copy(zipImportPreview = null) }
  }

  fun confirmImportZipAsNewProject(
    projectNameRaw: String,
    deployNow: Boolean = false,
  ) {
    val preview = _uiState.value.zipImportPreview ?: return
    val plan = preview.plan ?: return
    flushAutoSaveNow()
    syncActiveStateToRamDb()
    viewModelScope.launch {
      if (plan.edgeCodeBundleJson != null) {
        importEdgeCodeBundleJson(
          rawJson = plan.edgeCodeBundleJson,
          projectNameOverride = projectNameRaw,
          deployNow = deployNow,
        )
        return@launch
      }

      val isScratchpad = _uiState.value.isLocalScratchpad || _uiState.value.selectedAccount == null
      val accountId = if (isScratchpad) null else _uiState.value.selectedAccount?.id
      val cleanName =
        CloudflareNamingUtils.sanitizeWorkerName(
          projectNameRaw.ifBlank { plan.suggestedProjectName },
          allowUnderscore = isScratchpad,
        ).ifBlank { "zip-project" }

      val settings = _uiState.value.editorSettings
      val compatDate =
        plan.compatibilityDate?.takeIf { it.isNotBlank() }
          ?: settings.defaultCompatibilityDate.ifBlank { "2024-11-01" }
      val compatFlags =
        if (plan.compatibilityFlags.isNotEmpty()) plan.compatibilityFlags
        else if (settings.defaultNodejsCompat) listOf("nodejs_compat") else emptyList()
      val workersDev = plan.workersDev ?: (!isScratchpad && settings.defaultWorkersDevEnabled)

      val baseWs =
        WorkerWorkspace(
          scriptName = cleanName,
          isEsModuleFormat = true,
          entryModulePath = plan.entryModulePath,
          compatibilityDate = compatDate,
          compatibilityFlags = compatFlags,
          files = plan.files,
          bindings = plan.bindings,
          routes = emptyList(),
          schedules = plan.schedules,
          workersDevEnabled = workersDev,
          accountSubdomain = accountId?.let { ramDb.getInfraCache(it)?.subdomain }.orEmpty(),
          hasModuleSetChanged = !isScratchpad,
        )
      val ws =
        regenerateWranglerFileInWorkspace(baseWs).let { built ->
          built.copy(files = ProjectDirectoryManager.organizeWorkspaceFiles(built.files))
        }
      val key = ramDb.workspaceKey(accountId, cleanName)
      val defaultTabs =
        listOfNotNull(
          plan.entryModulePath,
          ws.files.firstOrNull { it.path == "wrangler.jsonc" }?.path,
          ws.files.firstOrNull { it.path == "package.json" }?.path,
        ).ifEmpty {
          ws.files.filterNot { it.isVirtualReadOnly }.take(4).map { it.path }
        }.distinct()
      val record =
        ramDb.importWorkspace(
          key = key,
          workspace = ws,
          defaultTabs = defaultTabs,
          defaultActivePath = plan.entryModulePath,
          isNewUnexported = !isScratchpad,
        )
      withContext(Dispatchers.IO) {
        projectDirManager.recordOrganizedImport(cleanName, ws.files)
        projectDirManager.saveWorkspaceRecord(record)
        val summary = projectDirManager.computeStorageSummary()
        _uiState.update { it.copy(projectStorageSummary = summary) }
      }
      val diags = rebuildDiagnosticsFull(ws.files)
      val initialPreview = resolveDefaultPreviewUrl(ws)
      val skippedNote =
        if (plan.skippedBinaryFiles.isNotEmpty()) " (${plan.skippedBinaryFiles.size} binary skipped)"
        else ""
      _uiState.update { state ->
        val updatedWorkers =
          if (!isScratchpad && state.workers.none { it.id == cleanName }) {
            listOf(CfWorkerScript(id = cleanName)) + state.workers
          } else {
            state.workers
          }
        state.copy(
          workers = updatedWorkers,
          workspace = ws,
          projectMode = ProjectMode.CODE,
          sidebarSection = SidebarSection.EXPLORER,
          showHomeGitHubHub = false,
          openTabPaths = record.openTabPaths,
          activeFilePath = record.activeFilePath,
          tabNavHistory = record.tabNavHistory,
          tabNavIndex = record.tabNavIndex,
          editorFieldValues = record.fieldValues,
          editorRevision = state.editorRevision + 1L,
          diagnostics = diags,
          previewUrl = initialPreview,
          apiRequest =
            if (initialPreview.isNotBlank()) {
              ApiRequestUtils.applyDeployedWorkerUrl(state.apiRequest, initialPreview, forceReplace = true)
            } else {
              state.apiRequest
            },
          activeWorkspaceHasPendingConfig = record.hasPendingChanges,
          ramDbStats = ramDb.computeStats(key),
          ramWorkspaces = ramDb.listWorkspaces(),
          zipImportPreview = null,
          statusMessage =
            if (deployNow && !isScratchpad) {
              "Imported ${plan.files.size} file(s) from ZIP$skippedNote — deploying '$cleanName'…"
            } else {
              "Imported ZIP '$cleanName' (${plan.files.size} file(s)$skippedNote)"
            },
        )
      }
      appendLog("INFO", "Imported ZIP archive '${plan.archiveName}' into '$cleanName' (${plan.files.size} files)")
      recordActiveSessionPointer()
      syncPeriodicAutoSaveLoop()

      if (deployNow && !isScratchpad && accountId != null) {
        deployWorkspaceInternal(accountId = accountId, workspaceKey = key, isNewWorker = true)
      }
    }
  }

  fun confirmMergeZipIntoActiveWorkspace(
    folderPrefix: String = "",
    autoDeploy: Boolean = false,
  ) {
    val preview = _uiState.value.zipImportPreview ?: return
    val plan = preview.plan ?: return
    viewModelScope.launch {
      mergeWorkerFilesIntoActiveWorkspace(
        incomingFiles = plan.files,
        folderPrefix = folderPrefix,
        autoDeploy = autoDeploy,
        sourceLabel = "ZIP '${plan.archiveName}'",
      )
    }
  }

  fun deleteWorker(scriptName: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ramKey = ramDb.workspaceKey(accountId, scriptName)
    viewModelScope.launch {
      repository
        .deleteWorker(accountId, scriptName)
        .onSuccess {
          ramDb.removeWorkspace(ramKey)
          autoSaveIoScope.launch {
            projectDirManager.deleteWorkspaceDirectory(ramKey)
            val summary = projectDirManager.computeStorageSummary()
            _uiState.update { it.copy(projectStorageSummary = summary) }
          }
          appendLog("INFO", "Deleted Worker '$scriptName'")
          _uiState.update { state ->
            val cleared = state.workspace?.scriptName == scriptName
            state.copy(
              workspace = if (cleared) null else state.workspace,
              openTabPaths = if (cleared) emptyList() else state.openTabPaths,
              activeFilePath = if (cleared) null else state.activeFilePath,
              ramDbStats = ramDb.computeStats(if (cleared) null else activeWorkspaceKey(state)),
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
    syncActiveStateToRamDb()
    scheduleDraftSave()
  }

  fun selectAdjacentTab(next: Boolean) {
    val state = _uiState.value
    val tabs = state.openTabPaths
    if (tabs.size <= 1) return
    val idx = tabs.indexOf(state.activeFilePath).takeIf { it >= 0 } ?: 0
    val targetIdx = if (next) (idx + 1) % tabs.size else (idx - 1 + tabs.size) % tabs.size
    openFileTab(tabs[targetIdx], recordHistory = true)
  }

  fun closeOtherTabs(keepPath: String) {
    _uiState.update { state ->
      val keep = state.openTabPaths.filter { it == keepPath }
      state.copy(openTabPaths = keep, activeFilePath = keep.firstOrNull())
    }
    syncActiveStateToRamDb()
    scheduleDraftSave()
  }

  fun closeSavedTabs() {
    _uiState.update { state ->
      val ws = state.workspace
      val dirtySet = ws?.files?.filter { it.isDirty }?.map { it.path }?.toSet().orEmpty()
      val remaining = state.openTabPaths.filter { it in dirtySet }
      val nextActive =
        if (state.activeFilePath in remaining) state.activeFilePath
        else remaining.lastOrNull()
      state.copy(openTabPaths = remaining, activeFilePath = nextActive)
    }
    syncActiveStateToRamDb()
    scheduleDraftSave()
  }

  fun closeAllTabs() {
    _uiState.update { it.copy(openTabPaths = emptyList(), activeFilePath = null) }
    syncActiveStateToRamDb()
    scheduleDraftSave()
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
    flushActiveEditorBuffer()
    val state = _uiState.value
    val targetDirty = state.workspace?.files?.firstOrNull { it.path == path }?.isDirty == true
    val policy = state.editorSettings.normalizedCloseUnsavedPolicy()
    if (targetDirty) {
      when {
        policy == com.example.data.security.EditorSettings.CLOSE_UNSAVED_SAVE -> {
          closeFileTabInternal(path)
          return
        }
        policy == com.example.data.security.EditorSettings.CLOSE_UNSAVED_DISCARD -> {
          revertSingleFileToOriginal(path)
          closeFileTabInternal(path)
          return
        }
        state.editorSettings.warnOnCloseDirtyTab -> {
          showUnsavedWarning(
            action = PendingUnsavedAction.CloseTab(path),
            title = "Unsaved File: ${path.substringAfterLast('/')}",
            message = "This file has unsaved modifications. Keep changes in workspace draft or discard edits to this file.",
            dirtyPathsOverride = listOf(path),
            allowKeepLocalDraft = true,
          )
          return
        }
      }
    }
    closeFileTabInternal(path)
  }

  private fun closeFileTabInternal(path: String) {
    _uiState.update { state ->
      val nextTabs = state.openTabPaths.filterNot { it == path }
      val nextActive =
        if (state.activeFilePath == path) nextTabs.lastOrNull() else state.activeFilePath
      state.copy(openTabPaths = nextTabs, activeFilePath = nextActive)
    }
    syncActiveStateToRamDb()
    scheduleDraftSave()
  }

  private fun revertSingleFileToOriginal(path: String) {
    val state = _uiState.value
    val ws = state.workspace ?: return
    val target = ws.files.firstOrNull { it.path == path } ?: return
    if (!target.isDirty) return
    val updatedFiles = ws.files.map { f -> if (f.path == path) f.copy(content = f.originalContent) else f }
    val updatedWs = ws.copy(files = updatedFiles)
    val nextFields = state.editorFieldValues.toMutableMap().apply {
      put(path, TextFieldValue(target.originalContent))
    }
    val key = activeWorkspaceKey(state)
    if (key != null) {
      ramDb.updateWorkspace(key) { rec ->
        rec.copy(workingWorkspace = updatedWs, fieldValues = nextFields)
      }
      persistWorkspaceToProjectDirAsync(key)
    }
    _uiState.update { cur ->
      cur.copy(
        workspace = updatedWs,
        editorFieldValues = nextFields,
        editorRevision = cur.editorRevision + 1L,
        diagnostics = rebuildDiagnosticsFull(updatedFiles),
        ramDbStats = if (key != null) ramDb.computeStats(key) else cur.ramDbStats,
      )
    }
  }

  private fun showUnsavedWarning(
    action: PendingUnsavedAction,
    title: String,
    message: String,
    dirtyPathsOverride: List<String>? = null,
    allowKeepLocalDraft: Boolean,
  ) {
    val state = _uiState.value
    val dirtyPaths =
      dirtyPathsOverride
        ?: state.workspace?.files?.filter { it.isDirty }?.map { it.path }.orEmpty()
    val canDeploy = !state.isLocalScratchpad && state.credentials != null && state.workspace != null
    _uiState.update {
      it.copy(
        pendingUnsavedWarning =
          PendingUnsavedWarning(
            action = action,
            title = title,
            message = message,
            dirtyFilePaths = dirtyPaths,
            allowKeepLocalDraft = allowKeepLocalDraft,
            allowDeployNow = canDeploy,
          ),
      )
    }
  }

  fun dismissUnsavedWarning() {
    _uiState.update { it.copy(pendingUnsavedWarning = null) }
  }

  fun resolveUnsavedWarningDeployNow() {
    _uiState.update { it.copy(pendingUnsavedWarning = null) }
    deployActiveWorker()
  }

  fun resolveUnsavedWarningKeepDraft() {
    val warning = _uiState.value.pendingUnsavedWarning ?: return
    _uiState.update { it.copy(pendingUnsavedWarning = null) }
    flushActiveEditorBuffer()
    flushAutoSaveNow(clearSessionPointer = false)
    executePendingUnsavedAction(warning.action)
  }

  fun resolveUnsavedWarningDiscard() {
    val warning = _uiState.value.pendingUnsavedWarning ?: return
    _uiState.update { it.copy(pendingUnsavedWarning = null) }
    when (val action = warning.action) {
      is PendingUnsavedAction.CloseTab -> revertSingleFileToOriginal(action.path)
      else -> revertWorkspaceToImportedBaseline()
    }
    executePendingUnsavedAction(warning.action)
  }

  private fun executePendingUnsavedAction(action: PendingUnsavedAction) {
    when (action) {
      is PendingUnsavedAction.CloseTab -> closeFileTabInternal(action.path)
      PendingUnsavedAction.LeaveWorkspace -> closeWorkspaceToHomeInternal()
      is PendingUnsavedAction.SwitchWorker -> openWorkerInternal(action.script, action.forceRefreshFromCloudflare)
      is PendingUnsavedAction.SwitchRamWorkspace -> openRamWorkspaceInternal(action.key)
      is PendingUnsavedAction.SwitchAccount -> selectAccountInternal(action.account)
      PendingUnsavedAction.SignOut -> signOutInternal()
      PendingUnsavedAction.ReimportFromCloudflare -> reimportActiveWorkerInternal()
      PendingUnsavedAction.PullLinkedGitRepo -> syncPullLinkedGitRepoInternal()
      is PendingUnsavedAction.PullGitHubPaths -> pullGitHubPathsIntoWorkerInternal(action.paths)
    }
  }

  fun syncTypedEditorValue(newValue: TextFieldValue) {
    updateEditorValue(newValue, bumpRevision = false)
  }

  fun updateEditorValue(
    newValue: TextFieldValue,
    bumpRevision: Boolean = true,
  ) {
    val currentState = _uiState.value
    val path = currentState.activeFilePath ?: return
    val currentFile = currentState.activeFile ?: return
    if (currentFile.isVirtualReadOnly) return

    val prevValue = currentState.editorFieldValues[path] ?: TextFieldValue(currentFile.content)
    val textChanged = prevValue.text != newValue.text
    if (!textChanged && prevValue.selection == newValue.selection && prevValue.composition == newValue.composition) {
      return
    }

    val ramKey = activeWorkspaceKey(currentState)

    if (textChanged) {
      val now = System.currentTimeMillis()
      val isBurstTyping =
        !bumpRevision &&
          lastUndoSnapshotPath == path &&
          now - lastUndoSnapshotMs < 650L &&
          kotlin.math.abs(newValue.text.length - prevValue.text.length) <= 2 &&
          !newValue.text.endsWith('\n')
      if (!isBurstTyping) {
        lastUndoSnapshotMs = now
        lastUndoSnapshotPath = path
        if (ramKey != null) {
          ramDb.recordUndoSnapshot(ramKey, path, prevValue)
        } else {
          val undo = undoStacks.getOrPut(path) { ArrayDeque() }
          if (undo.lastOrNull()?.text != prevValue.text) {
            undo.addLast(prevValue)
            if (undo.size > 60) undo.removeFirst()
          }
          redoStacks[path]?.clear()
        }
      }
    }

    var latestFilesForAnalysis: List<com.example.data.remote.WorkerFile>? = null
    _uiState.update { state ->
      val ws = state.workspace ?: return@update state
      val updatedFiles =
        if (textChanged) {
          ws.files.map { f -> if (f.path == path) f.copy(content = newValue.text) else f }
        } else ws.files
      if (textChanged) latestFilesForAnalysis = updatedFiles
      val nextWs = if (textChanged) ws.copy(files = updatedFiles) else ws
      val nextFields = state.editorFieldValues + (path to newValue)
      if (textChanged && ramKey != null) {
        ramDb.updateWorkspace(ramKey, notifyStats = false) { rec ->
          rec.copy(
            workingWorkspace = nextWs,
            fieldValues = nextFields,
          )
        }
      }
      state.copy(
        workspace = nextWs,
        editorFieldValues = nextFields,
        editorRevision = if (bumpRevision) state.editorRevision + 1L else state.editorRevision,
      )
    }

    if (textChanged) {
      val filesSnapshot = latestFilesForAnalysis
      val editorCfg = _uiState.value.editorSettings
      if (filesSnapshot != null && editorCfg.analyzeOnTheFly) {
        val delayMs = editorCfg.reparseDelayMs.toLong().coerceIn(0L, 2000L)
        diagnosticsJob?.cancel()
        diagnosticsJob =
          viewModelScope.launch {
            if (delayMs > 0L) delay(delayMs)
            val editedFile = filesSnapshot.firstOrNull { it.path == path }
            val diags =
              withContext(Dispatchers.Default) {
                if (editedFile != null) reanalyzeSingleFile(editedFile, filesSnapshot)
                else rebuildDiagnosticsFull(filesSnapshot)
              }
            _uiState.update { state ->
              if (state.workspace?.files == filesSnapshot && state.diagnostics != diags) {
                state.copy(diagnostics = diags)
              } else {
                state
              }
            }
          }
      }
      if (ramKey != null) {
        ramStatsJob?.cancel()
        ramStatsJob =
          viewModelScope.launch {
            delay(1200L)
            val stats = withContext(Dispatchers.Default) { ramDb.computeStats(ramKey) }
            _uiState.update { if (it.ramDbStats != stats) it.copy(ramDbStats = stats) else it }
          }
      }
      val wsKey = ramKey ?: "default"
      val histKey = "$wsKey::$path"
      val nowMs = System.currentTimeMillis()
      val prevHistMs = lastHistoryRecordMs[histKey] ?: 0L
      if (bumpRevision || nowMs - prevHistMs >= 4_000L) {
        lastHistoryRecordMs[histKey] = nowMs
        localHistoryStore.recordSnapshot(
          workspaceKey = wsKey,
          filePath = path,
          before = prevValue.text,
          after = newValue.text,
        )
      }
      workspaceEventBus.publish(WorkspaceEvent.FileSaved(wsKey, path, prevValue.text, newValue.text))
      val fileKey = "$wsKey::$path"
      val prevSavedNonSpacing =
        lastLocallyAutoSavedNonSpacing[fileKey]
          ?: AutoSaveDraftStore.stripSpacing(prevValue.text)
      val currentNonSpacing = AutoSaveDraftStore.stripSpacing(newValue.text)
      if (currentNonSpacing != prevSavedNonSpacing) {
        lastLocallyAutoSavedNonSpacing[fileKey] = currentNonSpacing
        draftSaveJob?.cancel()
        triggerImmediateLocalAutoSave(_uiState.value, modifiedExternalPath = path)
      }
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
    val current = _uiState.value.activeTextFieldValue
    val ramKey = activeWorkspaceKey()
    val previous =
      if (ramKey != null) {
        ramDb.undo(ramKey, path, current)
      } else {
        val undo = undoStacks[path] ?: return
        if (undo.isEmpty()) return
        redoStacks.getOrPut(path) { ArrayDeque() }.addLast(current)
        undo.removeLast()
      } ?: return
    applyDirectTextValue(path, previous)
  }

  fun redo() {
    val path = _uiState.value.activeFilePath ?: return
    val current = _uiState.value.activeTextFieldValue
    val ramKey = activeWorkspaceKey()
    val next =
      if (ramKey != null) {
        ramDb.redo(ramKey, path, current)
      } else {
        val redo = redoStacks[path] ?: return
        if (redo.isEmpty()) return
        undoStacks.getOrPut(path) { ArrayDeque() }.addLast(current)
        redo.removeLast()
      } ?: return
    applyDirectTextValue(path, next)
  }

  private fun applyDirectTextValue(path: String, value: TextFieldValue) {
    val prevState = _uiState.value
    val prevText = prevState.editorFieldValues[path]?.text
      ?: prevState.workspace?.files?.firstOrNull { it.path == path }?.content.orEmpty()
    val ramKey = activeWorkspaceKey(prevState)
    var filesSnapshot: List<WorkerFile>? = null
    _uiState.update { state ->
      val ws = state.workspace ?: return@update state
      val updatedFiles = ws.files.map { if (it.path == path) it.copy(content = value.text) else it }
      filesSnapshot = updatedFiles
      val nextWs = ws.copy(files = updatedFiles)
      val nextFields = state.editorFieldValues + (path to value)
      if (ramKey != null) {
        ramDb.updateWorkspace(ramKey, notifyStats = false) { rec ->
          rec.copy(workingWorkspace = nextWs, fieldValues = nextFields)
        }
      }
      state.copy(
        workspace = nextWs,
        editorFieldValues = nextFields,
        editorRevision = state.editorRevision + 1L,
      )
    }
    val snap = filesSnapshot
    val editorCfg = _uiState.value.editorSettings
    if (snap != null && editorCfg.analyzeOnTheFly) {
      diagnosticsJob?.cancel()
      diagnosticsJob =
        viewModelScope.launch {
          val edited = snap.firstOrNull { it.path == path }
          val diags =
            withContext(Dispatchers.Default) {
              if (edited != null) reanalyzeSingleFile(edited, snap) else rebuildDiagnosticsFull(snap)
            }
          val stats = if (ramKey != null) withContext(Dispatchers.Default) { ramDb.computeStats(ramKey) } else null
          _uiState.update { state ->
            if (state.workspace?.files == snap) {
              state.copy(diagnostics = diags, ramDbStats = stats ?: state.ramDbStats)
            } else state
          }
        }
    }
    val wsKey = ramKey ?: "default"
    val fileKey = "$wsKey::$path"
    val prevSavedNonSpacing =
      lastLocallyAutoSavedNonSpacing[fileKey]
        ?: AutoSaveDraftStore.stripSpacing(prevText)
    val currentNonSpacing = AutoSaveDraftStore.stripSpacing(value.text)
    if (currentNonSpacing != prevSavedNonSpacing) {
      lastLocallyAutoSavedNonSpacing[fileKey] = currentNonSpacing
      draftSaveJob?.cancel()
      triggerImmediateLocalAutoSave(_uiState.value, modifiedExternalPath = path)
    }
  }

  fun formatActiveFile() {
    flushActiveEditorBuffer()
    val file = _uiState.value.activeFile ?: return
    if (file.isVirtualReadOnly) return
    val formatted = CodeFormatter.format(file.content, file.language, _uiState.value.editorSettings.tabSize)
    if (formatted != file.content) {
      val wsKey = activeWorkspaceKey() ?: "default"
      localHistoryStore.recordSnapshot(wsKey, file.path, file.content, formatted, label = "Format")
      val cursor = formatted.length.coerceAtMost(_uiState.value.activeTextFieldValue.selection.start)
      updateEditorValue(TextFieldValue(formatted, TextRange(cursor)), bumpRevision = true)
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
    updateEditorValue(TextFieldValue(nextText, TextRange(nextCursor)), bumpRevision = true)
  }

  fun jumpToLine(filePath: String, line: Int, column: Int = 1) {
    openFileTab(filePath)
    val file = _uiState.value.workspace?.files?.firstOrNull { it.path == filePath } ?: return
    var offset = 0
    val lines = file.content.lines()
    val targetIdx = (line - 1).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    for (i in 0 until targetIdx) {
      offset += lines[i].length + 1
    }
    val colOffset = (column - 1).coerceIn(0, lines.getOrNull(targetIdx)?.length ?: 0)
    val safeOffset = (offset + colOffset).coerceIn(0, file.content.length)
    _uiState.update { state ->
      state.copy(
        projectMode = ProjectMode.CODE,
        isPreviewFullScreen = false,
        editorRevision = state.editorRevision + 1L,
        editorFieldValues =
          state.editorFieldValues +
            (filePath to TextFieldValue(file.content, TextRange(safeOffset)))
      )
    }
  }

  fun createModuleDirectory(dirPathRaw: String) {
    val ws = _uiState.value.workspace ?: return
    val clean = ProjectDirectoryManager.normalizeRelativePath(dirPathRaw)
    if (clean.isBlank()) return
    val ramKey = activeWorkspaceKey()
    val nextWs = ws.copy(directories = ws.directories + clean)
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec -> rec.copy(workingWorkspace = nextWs) }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = nextWs,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Created directory '$clean'",
      )
    }
  }

  fun addModuleFile(fileName: String) {
    val clean = ProjectDirectoryManager.normalizeRelativePath(fileName)
    if (clean.isBlank()) return
    val starter =
      when {
        clean.endsWith(".json", ignoreCase = true) -> "{\n  \"ok\": true,\n  \"service\": \"edgecode\"\n}\n"
        clean.endsWith(".html", ignoreCase = true) -> "<!doctype html>\n<html>\n  <body style=\"font-family:sans-serif;padding:16px;\">\n    <h2>EdgeCode Worker Page</h2>\n    <p>Deploy your Worker to preview this page live.</p>\n  </body>\n</html>\n"
        clean.endsWith(".sh", ignoreCase = true) -> "#!/bin/sh\necho \"Hello from EdgeCode\"\n"
        else ->
          """export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
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
          scriptName = "unsaved-worker",
          isEsModuleFormat = true,
          entryModulePath = clean,
          compatibilityDate = "2024-11-01",
          files = listOf(file),
          bindings = emptyList(),
          routes = emptyList(),
          workersDevEnabled = false,
          accountSubdomain = "",
        )
      val isScratch = _uiState.value.credentials == null
      val key = ramDb.workspaceKey(if (isScratch) null else _uiState.value.selectedAccount?.id, ws.scriptName)
      val record = ramDb.importWorkspace(key = key, workspace = ws, defaultTabs = listOf(clean), defaultActivePath = clean, isNewUnexported = true)
      persistWorkspaceToProjectDirAsync(key)
      val diags = rebuildDiagnosticsFull(ws.files)
      _uiState.update {
        it.copy(
          isLocalScratchpad = isScratch,
          workspace = ws,
          openTabPaths = record.openTabPaths,
          activeFilePath = record.activeFilePath,
          tabNavHistory = record.tabNavHistory,
          tabNavIndex = record.tabNavIndex,
          editorFieldValues = record.fieldValues,
          diagnostics = diags,
          activeWorkspaceHasPendingConfig = record.hasPendingChanges,
          ramDbStats = ramDb.computeStats(key),
          statusMessage = "Created '$clean'",
        )
      }
      return
    }

    if (existingWs.files.any { it.path == clean }) {
      openFileTab(clean)
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
      ProjectDirectoryManager.organizeWorkspaceFiles(
        existingWs.files.filterNot { it.isVirtualReadOnly } + newFile + existingWs.files.filter { it.isVirtualReadOnly },
      )
    val nextWs =
      existingWs.copy(
        files = updatedFiles,
        directories = nextDirs,
        isEsModuleFormat = true,
        hasModuleSetChanged = true,
      )
    val ramKey = activeWorkspaceKey()
    val wsKey = ramKey ?: "default"
    val opEntry = fileOpJournal.record(WorkspaceFileOpJournal.FileOp.Created(wsKey, clean))
    val diags = reanalyzeSingleFile(newFile, updatedFiles)
    _uiState.update { state ->
      val nextFields = state.editorFieldValues + (clean to TextFieldValue(starter))
      if (ramKey != null) {
        ramDb.updateWorkspace(ramKey) { rec ->
          rec.copy(workingWorkspace = nextWs, fieldValues = nextFields)
        }
      }
      state.copy(
        workspace = nextWs,
        openTabPaths = state.openTabPaths + clean,
        activeFilePath = clean,
        editorFieldValues = nextFields,
        diagnostics = diags,
        lastUndoableFileOpLabel = opEntry.op.label,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
      )
    }
    workspaceEventBus.publish(WorkspaceEvent.FileCreated(wsKey, clean))
    if (ramKey != null) persistWorkspaceToProjectDirAsync(ramKey)
    openFileTab(clean, recordHistory = true)
  }

  private fun resolveDefaultPreviewUrl(ws: WorkerWorkspace?, activeFilePath: String? = null): String {
    if (ws == null) return ""
    val remote =
      ws.workersDevUrl
        ?: ws.domains.firstOrNull()?.hostname?.takeIf { it.isNotBlank() }?.let { "https://$it" }
    if (!remote.isNullOrBlank()) return remote
    val localPath = LocalWorkspacePreviewRenderer.findDefaultPreviewPath(ws, activeFilePath)
    return if (localPath != null) LocalWorkspacePreviewRenderer.localUrlForPath(localPath) else ""
  }

  fun openDeployedWorkerPreview(
    targetUrl: String? = null,
    maximizeWindow: Boolean = true,
  ) {
    flushActiveEditorBuffer()
    _uiState.update { state ->
      val activeLocalPreview =
        state.activeFilePath
          ?.takeIf { LocalWorkspacePreviewRenderer.isPreviewableFile(it) }
          ?.let { LocalWorkspacePreviewRenderer.localUrlForPath(it) }
      val resolved =
        targetUrl?.trim()?.takeIf { it.isNotEmpty() }
          ?: activeLocalPreview
          ?: state.previewUrl.ifBlank { resolveDefaultPreviewUrl(state.workspace, state.activeFilePath) }
      state.copy(
        previewUrl = resolved,
        previewModeTab = 0,
        projectMode = ProjectMode.TEST,
        showBottomPanel = true,
        isBottomPanelExpanded = maximizeWindow,
        activeBottomTab = BottomPanelTab.WORKER_PREVIEW,
        previewReloadNonce = state.previewReloadNonce + 1L,
      )
    }
  }

  fun togglePreviewViewport() {
    _uiState.update {
      it.copy(
        isPreviewDesktopViewport = !it.isPreviewDesktopViewport,
        previewReloadNonce = it.previewReloadNonce + 1L,
      )
    }
  }

  fun openWorkerTestWindow(
    targetUrl: String? = null,
    maximizeWindow: Boolean = true,
  ) {
    _uiState.update { state ->
      val resolved =
        targetUrl?.trim()?.takeIf { it.isNotEmpty() }
          ?: state.previewUrl.ifBlank { resolveDefaultPreviewUrl(state.workspace) }
      state.copy(
        previewUrl = resolved,
        previewModeTab = 1,
        projectMode = ProjectMode.TEST,
        showBottomPanel = true,
        isBottomPanelExpanded = maximizeWindow,
        activeBottomTab = BottomPanelTab.WORKER_TEST,
      )
    }
  }

  fun setPreviewModeTab(tab: Int) {
    _uiState.update { it.copy(previewModeTab = tab.coerceIn(0, 1)) }
  }

  fun setPreviewUrl(url: String) {
    _uiState.update { it.copy(previewUrl = url) }
  }

  fun reloadDeployedWorkerPreview() {
    _uiState.update { state ->
      val url = state.previewUrl.ifBlank { resolveDefaultPreviewUrl(state.workspace) }
      state.copy(
        previewUrl = url,
        previewReloadNonce = state.previewReloadNonce + 1L,
      )
    }
  }

  fun togglePreviewConsoleExpanded() {
    _uiState.update { it.copy(isPreviewConsoleExpanded = !it.isPreviewConsoleExpanded) }
  }

  fun setPreviewFullScreen(fullScreen: Boolean) {
    _uiState.update { state ->
      state.copy(
        isPreviewFullScreen = fullScreen,
        isPreviewConsoleExpanded = if (fullScreen) false else state.isPreviewConsoleExpanded,
      )
    }
  }

  fun togglePreviewFullScreen() {
    val next = !_uiState.value.isPreviewFullScreen
    setPreviewFullScreen(next)
  }

  fun updatePreviewHttpRequest(
    method: String = _uiState.value.previewHttpMethod,
    headersJson: String = _uiState.value.previewHttpHeadersJson,
    body: String = _uiState.value.previewHttpBody,
  ) {
    _uiState.update {
      it.copy(
        previewHttpMethod = method,
        previewHttpHeadersJson = headersJson,
        previewHttpBody = body,
      )
    }
  }

  fun sendPreviewHttpTest() {
    val state = _uiState.value
    val baseFallback = resolveDefaultPreviewUrl(state.workspace)
    val rawCandidate = state.previewUrl.trim().ifEmpty { baseFallback }
    val method = state.previewHttpMethod.uppercase()
    val bodyText = state.previewHttpBody

    if (rawCandidate.isEmpty()) {
      val msg = "Deploy your Worker or enter a https://...workers.dev URL to test."
      _uiState.update {
        it.copy(
          statusMessage = msg,
          previewHttpResult =
            HttpTestResult(
              url = "",
              method = method,
              statusCode = 0,
              statusText = "Missing URL",
              durationMs = 0L,
              headers = emptyMap(),
              body = "",
              errorMessage = msg,
            ),
        )
      }
      return
    }

    val rawUrl =
      ApiRequestUtils.normalizeBareOrRelativeUrl(rawCandidate, baseFallback).getOrElse { err ->
        val msg = err.localizedMessage ?: "Invalid Worker URL"
        _uiState.update {
          it.copy(
            statusMessage = msg,
            previewHttpResult =
              HttpTestResult(
                url = rawCandidate,
                method = method,
                statusCode = 0,
                statusText = "Invalid URL",
                durationMs = 0L,
                headers = emptyMap(),
                body = "",
                errorMessage = msg,
              ),
          )
        }
        return
      }

    val headersMap = linkedMapOf<String, String>()
    val headersRaw = state.previewHttpHeadersJson.trim()
    if (headersRaw.isNotEmpty() && headersRaw != "{}") {
      val parsedHeaders =
        runCatching {
          val obj = JSONObject(headersRaw)
          obj.keys().forEach { k ->
            val cleanKey = k.trim()
            if (cleanKey.isNotEmpty()) headersMap[cleanKey] = obj.optString(k)
          }
        }
      if (parsedHeaders.isFailure) {
        val msg = "Headers JSON is malformed: ${parsedHeaders.exceptionOrNull()?.localizedMessage ?: "expected valid JSON object"}"
        _uiState.update {
          it.copy(
            statusMessage = msg,
            previewHttpResult =
              HttpTestResult(
                url = rawUrl,
                method = method,
                statusCode = 0,
                statusText = "Invalid Headers",
                durationMs = 0L,
                headers = emptyMap(),
                body = "",
                errorMessage = msg,
              ),
          )
        }
        return
      }
    }

    _uiState.update {
      it.copy(
        previewUrl = rawUrl,
        isRunningPreviewHttp = true,
      )
    }

    viewModelScope.launch {
      val result = repository.executeLiveHttpTest(rawUrl, method, headersMap, bodyText)
      val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
      ramDb.recordWorkerTestRun(
        WorkerTestRunRecord(
          id = UUID.randomUUID().toString().take(8),
          timestamp = ts,
          url = rawUrl,
          method = method,
          statusCode = result.statusCode,
          statusText = result.statusText,
          durationMs = result.durationMs,
          responseBytes = result.body.toByteArray(Charsets.UTF_8).size,
          headers = result.headers,
          body = result.body,
          errorMessage = result.errorMessage,
        ),
      )
      _uiState.update { s ->
        s.copy(
          isRunningPreviewHttp = false,
          previewHttpResult = result,
          workerTestHistory = ramDb.getWorkerTestHistory(),
        )
      }
      appendPreviewConsoleEntry(
        kind = if (result.errorMessage != null || result.statusCode >= 400) "ERR" else "HTTP",
        methodOrLevel = method,
        message = result.errorMessage?.let { "$rawUrl • $it" } ?: rawUrl,
        statusCode = result.statusCode.takeIf { it > 0 },
        durationMs = result.durationMs,
      )
    }
  }

  fun selectWorkerTestHistoryRun(run: WorkerTestRunRecord) {
    _uiState.update {
      it.copy(
        previewUrl = run.url,
        previewHttpMethod = run.method,
        previewHttpResult =
          HttpTestResult(
            url = run.url,
            method = run.method,
            statusCode = run.statusCode,
            statusText = run.statusText,
            durationMs = run.durationMs,
            headers = run.headers,
            body = run.body,
            errorMessage = run.errorMessage,
          ),
      )
    }
  }

  fun clearWorkerTestHistory() {
    ramDb.clearWorkerTestHistory()
    _uiState.update { it.copy(workerTestHistory = emptyList()) }
  }

  fun appendPreviewConsoleEntry(
    kind: String,
    methodOrLevel: String,
    message: String,
    statusCode: Int? = null,
    durationMs: Long? = null,
  ) {
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    val entry =
      WorkerPreviewConsoleEntry(
        id = UUID.randomUUID().toString().take(8),
        timestamp = ts,
        kind = kind,
        methodOrLevel = methodOrLevel,
        message = message,
        statusCode = statusCode,
        durationMs = durationMs,
      )
    _uiState.update { state ->
      state.copy(previewConsoleEntries = (listOf(entry) + state.previewConsoleEntries).take(80))
    }
  }

  fun clearPreviewConsole() {
    _uiState.update { it.copy(previewConsoleEntries = emptyList()) }
  }

  fun updateLocalRunRequest(
    path: String = _uiState.value.localRunUrlPath,
    method: String = _uiState.value.localRunMethod,
    headersJson: String = _uiState.value.localRunHeadersJson,
    body: String = _uiState.value.localRunBody,
  ) {
    _uiState.update {
      it.copy(
        localRunUrlPath = path,
        localRunMethod = method,
        localRunHeadersJson = headersJson,
        localRunBody = body,
      )
    }
  }

  fun runWorkspaceLocally(openTestWorkspace: Boolean = true) {
    flushActiveEditorBuffer()
    val state = _uiState.value
    val ws = state.workspace ?: return
    if (state.isRunningLocalScript) return

    _uiState.update { cur ->
      cur.copy(
        isRunningLocalScript = true,
        projectMode = if (openTestWorkspace) ProjectMode.TEST else cur.projectMode,
        activeBottomTab = if (openTestWorkspace) BottomPanelTab.LOCAL_RUN else cur.activeBottomTab,
        showBottomPanel = if (openTestWorkspace) true else cur.showBottomPanel,
      )
    }

    viewModelScope.launch {
      val result =
        runCatching {
          LocalScriptRunner.runWorkspace(
            context = getApplication(),
            workspace = ws,
            activeFilePath = state.activeFilePath,
            requestPath = state.localRunUrlPath,
            method = state.localRunMethod,
            headersJson = state.localRunHeadersJson,
            body = state.localRunBody,
            isDarkTheme = state.editorSettings.darkTheme,
          )
        }.getOrElse { err ->
          LocalScriptRunResult(
            mode = "SCRIPT",
            entryPath = state.activeFilePath ?: ws.entryModulePath,
            statusCode = 500,
            statusText = "ERROR",
            durationMs = 0L,
            errorMessage = err.localizedMessage ?: "Failed to execute locally",
            ranAt = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
          )
        }

      _uiState.update { cur ->
        cur.copy(
          isRunningLocalScript = false,
          localScriptResult = result,
        )
      }
      result.logs.forEach { log ->
        appendLog(log.level, "[Local ${result.entryPath}] ${log.message}")
      }
      if (result.errorMessage != null) {
        appendLog("ERROR", "[Local ${result.entryPath}] ${result.errorMessage}")
      }
      appendPreviewConsoleEntry(
        kind = if (result.errorMessage != null || result.statusCode >= 400) "ERR" else "JS",
        methodOrLevel = "${result.mode} ${state.localRunMethod}",
        message = result.errorMessage ?: "${result.entryPath} (${state.localRunUrlPath})",
        statusCode = result.statusCode,
        durationMs = result.durationMs,
      )
    }
  }

  fun executeCliInput(rawInput: String) {
    val cmd = rawInput.trim()
    if (cmd.isEmpty()) return
    flushActiveEditorBuffer()
    val state = _uiState.value
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    _uiState.update { cur ->
      cur.copy(cliHistory = (cur.cliHistory + cmd).takeLast(30))
    }

    when (cmd.lowercase()) {
      "clear", "cls" -> {
        _uiState.update { it.copy(localScriptResult = null) }
        return
      }
      "help", "?" -> {
        val helpText =
          buildString {
            appendLine("EdgeCode CLI Commands:")
            appendLine("  run            Execute active JS/TS file or Worker")
            appendLine("  files / ls     List workspace files")
            appendLine("  env            Show Worker bindings & runtime mode")
            appendLine("  clear          Clear terminal output")
            appendLine("  <js expr>      Evaluate JS/TS directly (e.g. 2 + 2, JSON.stringify({ok:true}))")
          }.trimEnd()
        _uiState.update {
          it.copy(
            localScriptResult =
              LocalScriptRunResult(
                mode = "CLI",
                entryPath = "cli",
                statusCode = 200,
                statusText = "HELP",
                durationMs = 1L,
                body = helpText,
                logs = listOf(LocalRunLogEntry("INFO", "> $cmd", ts)),
                ranAt = ts,
              ),
          )
        }
        return
      }
      "files", "ls" -> {
        val listText =
          state.workspace?.files?.filterNot { it.isVirtualReadOnly }
            ?.joinToString("\n") { wf ->
              val marker = if (wf.path == state.activeFilePath) "*" else " "
              "$marker ${wf.path} (${wf.content.length} chars)"
            }
            ?: "(no workspace open)"
        _uiState.update {
          it.copy(
            localScriptResult =
              LocalScriptRunResult(
                mode = "CLI",
                entryPath = "ls",
                statusCode = 200,
                statusText = "OK",
                durationMs = 1L,
                body = listText,
                logs = listOf(LocalRunLogEntry("INFO", "> $cmd", ts)),
                ranAt = ts,
              ),
          )
        }
        return
      }
      "env" -> {
        val denoPresent = LocalScriptRunner.resolveDenoBinary(getApplication()).exists()
        val envText =
          buildString {
            appendLine("Runtime: ${if (denoPresent) "Deno arm64 (libdeno.so active)" else "WebView JS Sandbox (libdeno.so not bundled locally)"}")
            appendLine("Project: ${state.workspace?.scriptName ?: "(none)"}")
            appendLine("Root: ${state.externalProjectRootPath ?: projectDirManager.displayRootPath()}")
            val bindings = state.workspace?.bindings.orEmpty()
            appendLine("Bindings: ${if (bindings.isEmpty()) "none" else bindings.joinToString { "${it.name}:${it.type}" }}")
          }.trimEnd()
        _uiState.update {
          it.copy(
            localScriptResult =
              LocalScriptRunResult(
                mode = "CLI",
                entryPath = "env",
                statusCode = 200,
                statusText = "OK",
                durationMs = 1L,
                body = envText,
                logs = listOf(LocalRunLogEntry("INFO", "> $cmd", ts)),
                ranAt = ts,
              ),
          )
        }
        return
      }
      "run" -> {
        runWorkspaceLocally(openTestWorkspace = true)
        return
      }
    }

    _uiState.update { it.copy(isRunningLocalScript = true) }
    viewModelScope.launch {
      val evalResult =
        runCatching {
          LocalScriptRunner.evalSnippet(
            context = getApplication(),
            workspace = _uiState.value.workspace,
            snippet = cmd,
          )
        }.getOrElse { err ->
          LocalScriptRunResult(
            mode = "SCRIPT",
            entryPath = "repl",
            statusCode = 500,
            statusText = "ERROR",
            durationMs = 1L,
            errorMessage = err.localizedMessage ?: "Failed to evaluate expression",
            ranAt = ts,
          )
        }
      _uiState.update {
        it.copy(
          isRunningLocalScript = false,
          localScriptResult = evalResult,
        )
      }
    }
  }

  fun linkExternalProjectFolder(treeUri: Uri) {
    val context = getApplication<Application>()
    runCatching {
      context.contentResolver.takePersistableUriPermission(
        treeUri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    }
    viewModelScope.launch {
      val scan =
        DeviceFileHelper.scanTreeUriForExternalProject(context, treeUri).getOrElse { err ->
          _uiState.update {
            it.copy(statusMessage = err.localizedMessage ?: "Could not read selected folder")
          }
          return@launch
        }
      val state = _uiState.value
      val ws = state.workspace ?: return@launch
      val currentByPath = ws.files.filterNot { it.isVirtualReadOnly }.associateBy { it.path }
      val mergedEditable = linkedMapOf<String, WorkerFile>()
      scan.textFiles.forEach { picked ->
        val cleanPath = ProjectDirectoryManager.normalizeRelativePath(picked.fileName).ifBlank { picked.fileName }
        val existing = currentByPath[cleanPath]
        mergedEditable[cleanPath] =
          existing ?: WorkerFile(
            path = cleanPath,
            content = picked.content,
            originalContent = picked.content,
            contentType = MultipartWorkerParser.inferModuleMimeType(cleanPath),
            isEntryPoint = false,
          )
      }
      currentByPath.forEach { (p, wf) ->
        if (!mergedEditable.containsKey(p)) mergedEditable[p] = wf
      }
      val nextFiles = ProjectDirectoryManager.organizeWorkspaceFiles(mergedEditable.values.toList())
      val nextWs = ws.copy(files = nextFiles)
      val nextFields =
        state.editorFieldValues.toMutableMap().apply {
          nextFiles.forEach { wf ->
            if (!containsKey(wf.path)) put(wf.path, TextFieldValue(wf.content))
          }
        }
      activeWorkspaceKey(state)?.let { ramKey ->
        ramDb.updateWorkspace(ramKey) { rec ->
          rec.copy(workingWorkspace = nextWs, fieldValues = nextFields)
        }
      }
      val resolvedPreviewUrl =
        state.previewUrl.ifBlank {
          LocalWorkspacePreviewRenderer.findDefaultPreviewPath(nextWs, state.activeFilePath)
            ?.let { LocalWorkspacePreviewRenderer.localUrlForPath(it) }
            .orEmpty()
        }
      _uiState.update { cur ->
        cur.copy(
          workspace = nextWs,
          editorFieldValues = nextFields,
          editorRevision = cur.editorRevision + 1L,
          externalBinaryAssets = scan.binaryAssets,
          externalProjectRootPath = scan.rootDirPath,
          previewUrl = resolvedPreviewUrl,
          previewReloadNonce = cur.previewReloadNonce + 1L,
          diagnostics = rebuildDiagnosticsFull(nextFiles),
          statusMessage = "Linked project folder (${scan.textFiles.size} code files, ${scan.binaryAssets.size} assets)",
        )
      }
    }
  }

  fun saveActiveExternalFileNow() {
    flushActiveEditorBuffer()
    val state = _uiState.value
    val active = state.activeFile ?: return
    val uriStr = state.externalFileUris[active.path] ?: return
    val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return
    val content = state.editorFieldValues[active.path]?.text ?: active.content
    viewModelScope.launch {
      DeviceFileHelper.writeTextToUri(getApplication(), uri, content)
        .onSuccess {
          _uiState.update { cur ->
            val ws = cur.workspace ?: return@update cur
            val updated = ws.files.map { f -> if (f.path == active.path) f.copy(originalContent = content, content = content) else f }
            cur.copy(
              workspace = ws.copy(files = updated),
              statusMessage = "Saved '${active.fileName}' back to original file",
            )
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(statusMessage = "Could not overwrite original file: ${err.localizedMessage ?: "read-only provider"}")
          }
        }
    }
  }

  fun handleExternalIntent(intent: Intent?) {
    val extracted = ExternalIntentExtractor.extractFromIntent(intent) ?: return
    hasAttemptedAutoReopen = true
    val context = getApplication<Application>()

    extracted.uris.forEach { uri ->
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      }
    }

    // Bypass AuthScreen immediately so external Open With / Share opens straight into IDE
    _uiState.update {
      it.copy(
        isLocalScratchpad = true,
        isBootstrapping = false,
        authError = null,
      )
    }

    viewModelScope.launch {
      if (extracted.uris.isEmpty() && extracted.sharedText != null) {
        val fileName =
          ExternalIntentExtractor.sanitizeSharedSnippetFilename(
            extracted.sharedSubject,
            extracted.sharedText,
          )
        openExternalFilesInWorkspace(
          pickedFiles = listOf(PickedDeviceFile(fileName, extracted.sharedText)),
          uriMap = emptyMap(),
          binaryAssets = emptyMap(),
          rootDirPath = null,
          primaryPath = fileName,
          sourceBanner = "Opened shared snippet '$fileName'",
        )
        return@launch
      }

      if (extracted.uris.size == 1 && DeviceFileHelper.isZipUri(context, extracted.uris.first())) {
        val zipUri = extracted.uris.first()
        inspectZipForImport(zipUri, ZipImportTargetMode.NEW_PROJECT)
        _uiState.update { it.copy(statusMessage = "Ready to import external ZIP archive") }
        return@launch
      }

      val batch = DeviceFileHelper.readExternalTextFiles(context, extracted.uris)
      if (batch.files.isEmpty()) {
        val reason = batch.rejectedReasons.firstOrNull() ?: "Unsupported or unreadable external file"
        _uiState.update { it.copy(statusMessage = reason) }
        return@launch
      }

      val primary = batch.files.first()
      val uriMap = linkedMapOf<String, String>()
      batch.files.forEach { item ->
        val norm = ProjectDirectoryManager.normalizeRelativePath(item.fileName).ifBlank { item.fileName }
        uriMap[norm] = item.uri.toString()
      }

      var pickedForWs = batch.files.map { PickedDeviceFile(it.fileName, it.content) }
      var binaryAssets = emptyMap<String, ByteArray>()
      var resolvedRootPath: String? = null
      var primaryRelPath = ProjectDirectoryManager.normalizeRelativePath(primary.fileName).ifBlank { primary.fileName }

      if (batch.files.size == 1) {
        val parentDir = DeviceFileHelper.tryResolveParentDirectory(context, primary.uri)
        if (parentDir != null) {
          val scan = DeviceFileHelper.scanDirectoryForExternalProject(parentDir)
          if (scan.textFiles.isNotEmpty()) {
            resolvedRootPath = scan.rootDirPath
            binaryAssets = scan.binaryAssets
            val mergedMap = linkedMapOf<String, PickedDeviceFile>()
            scan.textFiles.forEach { sf ->
              val rel = ProjectDirectoryManager.normalizeRelativePath(sf.fileName).ifBlank { sf.fileName }
              mergedMap[rel] = PickedDeviceFile(rel, sf.content)
              if (sf.fileName.substringAfterLast('/') == primary.fileName.substringAfterLast('/')) {
                primaryRelPath = rel
              }
            }
            mergedMap[primaryRelPath] = PickedDeviceFile(primaryRelPath, primary.content)
            uriMap.clear()
            uriMap[primaryRelPath] = primary.uri.toString()
            pickedForWs = mergedMap.values.toList()
          }
        }
      }

      val rejectedNote =
        if (batch.rejectedReasons.isNotEmpty()) " (${batch.rejectedReasons.size} skipped)"
        else ""
      openExternalFilesInWorkspace(
        pickedFiles = pickedForWs,
        uriMap = uriMap,
        binaryAssets = binaryAssets,
        rootDirPath = resolvedRootPath,
        primaryPath = primaryRelPath,
        sourceBanner = "Opened '${primary.fileName}'$rejectedNote",
      )
    }
  }

  private fun openExternalFilesInWorkspace(
    pickedFiles: List<PickedDeviceFile>,
    uriMap: Map<String, String>,
    binaryAssets: Map<String, ByteArray>,
    rootDirPath: String?,
    primaryPath: String,
    sourceBanner: String,
  ) {
    val slug =
      CloudflareNamingUtils.sanitizeWorkerName(
        primaryPath.substringBeforeLast('.').ifBlank { "external-file" },
        allowUnderscore = true,
      ).ifBlank { "external-file" }
    val ramKey = ramDb.workspaceKey(null, "ext-$slug")
    val workerFiles =
      pickedFiles.map { item ->
        val rel = ProjectDirectoryManager.normalizeRelativePath(item.fileName).ifBlank { item.fileName }
        WorkerFile(
          path = rel,
          content = item.content,
          originalContent = item.content,
          contentType = MultipartWorkerParser.inferModuleMimeType(rel),
          isEntryPoint = rel == primaryPath,
        )
      }.let { ProjectDirectoryManager.organizeWorkspaceFiles(it) }

    val ws =
      WorkerWorkspace(
        scriptName = "ext-$slug",
        isEsModuleFormat = true,
        entryModulePath = primaryPath,
        compatibilityDate = _uiState.value.editorSettings.defaultCompatibilityDate.ifBlank { "2024-11-01" },
        files = workerFiles,
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = false,
        accountSubdomain = "",
      )
    val defaultTabs =
      (listOf(primaryPath) + workerFiles.filterNot { it.isVirtualReadOnly }.take(5).map { it.path }).distinct()
    val record =
      ramDb.importWorkspace(
        key = ramKey,
        workspace = ws,
        defaultTabs = defaultTabs,
        defaultActivePath = primaryPath,
        isNewUnexported = false,
      )
    val openMode = ExternalIntentExtractor.determineOpenMode(primaryPath)
    val openInPreview =
      openMode == ExternalOpenTargetMode.PREVIEW_HTML ||
        openMode == ExternalOpenTargetMode.PREVIEW_MARKDOWN ||
        openMode == ExternalOpenTargetMode.PREVIEW_SVG
    val previewTarget =
      if (openInPreview || LocalWorkspacePreviewRenderer.findDefaultPreviewPath(ws, primaryPath) != null) {
        LocalWorkspacePreviewRenderer.localUrlForPath(
          LocalWorkspacePreviewRenderer.findDefaultPreviewPath(ws, primaryPath) ?: primaryPath,
        )
      } else {
        ""
      }

    _uiState.update { state ->
      state.copy(
        isLocalScratchpad = true,
        isBootstrapping = false,
        authError = null,
        workspace = ws,
        openTabPaths = record.openTabPaths,
        activeFilePath = primaryPath,
        tabNavHistory = record.tabNavHistory,
        tabNavIndex = record.tabNavIndex,
        editorFieldValues = record.fieldValues,
        editorRevision = state.editorRevision + 1L,
        diagnostics = rebuildDiagnosticsFull(ws.files),
        externalFileUris = uriMap,
        externalBinaryAssets = binaryAssets,
        externalProjectRootPath = rootDirPath,
        projectMode = if (openInPreview) ProjectMode.TEST else ProjectMode.CODE,
        activeBottomTab = if (openInPreview) BottomPanelTab.WORKER_PREVIEW else state.activeBottomTab,
        showBottomPanel = openInPreview,
        isBottomPanelExpanded = openInPreview,
        previewModeTab = 0,
        previewUrl = previewTarget,
        previewReloadNonce = state.previewReloadNonce + 1L,
        ramDbStats = ramDb.computeStats(ramKey),
        ramWorkspaces = ramDb.listWorkspaces(),
        statusMessage = sourceBanner,
      )
    }
    persistWorkspaceToProjectDirAsync(ramKey)
    recordActiveSessionPointer()
    syncPeriodicAutoSaveLoop()
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
    val nextWs =
      ws.copy(
        files = updatedFiles,
        entryModulePath = nextEntry,
        hasModuleSetChanged = true,
      )
    val ramKey = activeWorkspaceKey()
    val wsKey = ramKey ?: "default"
    val wasOpenTab = filePath in _uiState.value.openTabPaths
    val wasEntryPoint = ws.entryModulePath == filePath
    localHistoryStore.recordSnapshot(wsKey, filePath, target.content, target.content, label = "Deleted")
    val opEntry =
      fileOpJournal.record(
        WorkspaceFileOpJournal.FileOp.Deleted(
          workspaceKey = wsKey,
          file = target,
          wasOpenTab = wasOpenTab,
          wasEntryPoint = wasEntryPoint,
        ),
      )
    fileDiagnosticsCache.remove(filePath)
    val diags = rebuildDiagnosticsFull(updatedFiles)

    _uiState.update { state ->
      val nextFields = state.editorFieldValues - filePath
      if (ramKey != null) {
        ramDb.updateWorkspace(ramKey) { rec ->
          rec.copy(
            workingWorkspace = nextWs,
            openTabPaths = nextTabs,
            activeFilePath = nextActive,
            fieldValues = nextFields,
          )
        }
      }
      state.copy(
        workspace = nextWs,
        openTabPaths = nextTabs,
        activeFilePath = nextActive,
        editorFieldValues = nextFields,
        diagnostics = diags,
        lastUndoableFileOpLabel = opEntry.op.label,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Removed '$filePath'",
      )
    }
    workspaceEventBus.publish(WorkspaceEvent.FileDeleted(wsKey, filePath))
    if (ramKey != null) persistWorkspaceToProjectDirAsync(ramKey)
    appendLog("INFO", "Removed module '$filePath' from '${ws.scriptName}'")
    scheduleDraftSave()
  }

  fun renameModuleFile(oldPath: String, newPathRaw: String) {
    val ws = _uiState.value.workspace ?: return
    val newPath = ProjectDirectoryManager.normalizeRelativePath(newPathRaw)
    if (newPath.isBlank() || newPath == oldPath || ws.files.any { it.path == newPath }) return
    val target = ws.files.firstOrNull { it.path == oldPath } ?: return
    if (target.isVirtualReadOnly) return

    val updatedFiles =
      ProjectDirectoryManager.organizeWorkspaceFiles(
        ws.files.map { f ->
          if (f.path == oldPath) {
            f.copy(path = newPath, contentType = MultipartWorkerParser.inferModuleMimeType(newPath))
          } else f
        },
      )
    val nextEntry = if (ws.entryModulePath == oldPath) newPath else ws.entryModulePath
    val nextTabs = _uiState.value.openTabPaths.map { if (it == oldPath) newPath else it }
    val nextActive = if (_uiState.value.activeFilePath == oldPath) newPath else _uiState.value.activeFilePath
    val oldFieldVal = _uiState.value.editorFieldValues[oldPath] ?: TextFieldValue(target.content)
    val nextFieldValues = (_uiState.value.editorFieldValues - oldPath) + (newPath to oldFieldVal)
    val nextWs = ws.copy(files = updatedFiles, entryModulePath = nextEntry, hasModuleSetChanged = true)
    val ramKey = activeWorkspaceKey()
    val wsKey = ramKey ?: "default"
    val opEntry = fileOpJournal.record(WorkspaceFileOpJournal.FileOp.Renamed(wsKey, oldPath, newPath))
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(
          workingWorkspace = nextWs,
          openTabPaths = nextTabs,
          activeFilePath = nextActive,
          fieldValues = nextFieldValues,
        )
      }
      persistWorkspaceToProjectDirAsync(ramKey)
    }

    val diags = rebuildDiagnosticsFull(updatedFiles)
    _uiState.update { state ->
      state.copy(
        workspace = nextWs,
        openTabPaths = nextTabs,
        activeFilePath = nextActive,
        editorFieldValues = nextFieldValues,
        diagnostics = diags,
        lastUndoableFileOpLabel = opEntry.op.label,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Renamed '$oldPath' -> '$newPath'",
      )
    }
    workspaceEventBus.publish(WorkspaceEvent.FileRenamed(wsKey, oldPath, newPath))
    scheduleDraftSave()
  }

  fun setEntryModule(filePath: String) {
    val ws = _uiState.value.workspace ?: return
    val target = ws.files.firstOrNull { it.path == filePath } ?: return
    if (target.isVirtualReadOnly) return
    val updatedFiles = ProjectDirectoryManager.organizeWorkspaceFiles(ws.files.map { it.copy(isEntryPoint = it.path == filePath) })
    val nextWs =
      ws.copy(
        files = updatedFiles,
        entryModulePath = filePath,
        isEsModuleFormat = true,
        hasModuleSetChanged = true,
      ).let { regenerateWranglerFileInWorkspace(it) }
    val ramKey = activeWorkspaceKey()
    _uiState.update { state ->
      val nextFields = syncWranglerFieldMap(state.editorFieldValues, nextWs)
      if (ramKey != null) {
        ramDb.updateWorkspace(ramKey) { rec ->
          rec.copy(
            workingWorkspace = nextWs,
            fieldValues = nextFields,
            hasPendingConfigSync = true,
          )
        }
      }
      state.copy(
        workspace = nextWs,
        editorFieldValues = nextFields,
        editorRevision = state.editorRevision + 1L,
        activeWorkspaceHasPendingConfig = true,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Entry point set to '$filePath'",
      )
    }
    if (ramKey != null) persistWorkspaceToProjectDirAsync(ramKey)
  }

  fun revertFile(filePath: String) {
    flushActiveEditorBuffer()
    val key = activeWorkspaceKey()
    val preRevertContent = _uiState.value.workspace?.files?.firstOrNull { it.path == filePath }?.content
    if (key != null) {
      val updated = ramDb.revertFileToImportedBaseline(key, filePath) ?: return
      val ws = updated.workingWorkspace
      val afterContent = ws.files.firstOrNull { it.path == filePath }?.content
      if (preRevertContent != null && afterContent != null && preRevertContent != afterContent) {
        localHistoryStore.recordSnapshot(key, filePath, preRevertContent, afterContent, label = "Revert")
      }
      persistWorkspaceToProjectDirAsync(key)
      val diags = rebuildDiagnosticsFull(ws.files)
      _uiState.update { state ->
        state.copy(
          workspace = ws,
          editorFieldValues = updated.fieldValues,
          editorRevision = state.editorRevision + 1L,
          diagnostics = diags,
          activeWorkspaceHasPendingConfig = updated.hasPendingChanges,
          ramDbStats = ramDb.computeStats(key),
          statusMessage = "Reverted $filePath to imported baseline",
        )
      }
      scheduleDraftSave()
      return
    }
    val ws = _uiState.value.workspace ?: return
    val target = ws.files.firstOrNull { it.path == filePath } ?: return
    if (target.isVirtualReadOnly) return
    val restored = target.originalContent
    applyDirectTextValue(filePath, TextFieldValue(restored))
    _uiState.update { state ->
      val refreshedDiffs =
        if (state.showDiffReviewModal) buildDiffEntries(state.diffReviewIntent, state)
        else state.diffReviewEntries
      state.copy(
        diffReviewEntries = refreshedDiffs,
        statusMessage = "Reverted $filePath",
      )
    }
  }

  fun deployActiveWorker() {
    flushActiveEditorBuffer()
    if (_uiState.value.isLocalScratchpad) {
      _uiState.update {
        it.copy(
          deployBannerMessage = "Local mode — connect Cloudflare to deploy live and obtain a workers.dev URL.",
          isDeployError = false,
        )
      }
      return
    }
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    val ramKey = ramDb.workspaceKey(accountId, ws.scriptName)
    val ramRecord = ramDb.getWorkspace(ramKey)
    val isNew = ramRecord?.isNewUnexportedWorker == true
    viewModelScope.launch {
      deployWorkspaceInternal(accountId = accountId, workspaceKey = ramKey, isNewWorker = isNew)
    }
  }

  private suspend fun deployWorkspaceInternal(
    accountId: String,
    workspaceKey: String,
    isNewWorker: Boolean,
  ) {
    val stateSnapshot = _uiState.value
    val ramRecord = ramDb.getWorkspace(workspaceKey)
    val ws =
      if (activeWorkspaceKey(stateSnapshot) == workspaceKey) {
        stateSnapshot.workspace ?: ramRecord?.workingWorkspace
      } else {
        ramRecord?.workingWorkspace
      } ?: return

    _uiState.update {
      it.copy(
        isDeploying = true,
        deployBannerMessage = null,
        statusMessage = "Deploying '${ws.scriptName}' to Cloudflare…",
      )
    }

    repository
      .deployWorkerWorkspace(accountId, ws)
      .onSuccess {
        val pendingErrors = mutableListOf<String>()
        val shouldSyncConfig =
          isNewWorker ||
            ramRecord?.hasPendingConfigSync == true ||
            ramRecord?.isNewUnexportedWorker == true ||
            ws.hasModuleSetChanged
        if (shouldSyncConfig) {
          repository.saveBindingsAndCompat(
            accountId = accountId,
            scriptName = ws.scriptName,
            compatibilityDate = ws.compatibilityDate,
            compatibilityFlags = ws.compatibilityFlags,
            bindings = ws.bindings.filterNot { it.type == "secret_text" },
          ).onFailure { err -> pendingErrors.add("settings: ${err.localizedMessage}") }
        }

        val targetWorkersDev =
          if (isNewWorker) _uiState.value.editorSettings.defaultWorkersDevEnabled || ws.workersDevEnabled
          else ws.workersDevEnabled
        var confirmedWorkersDev = targetWorkersDev
        if (shouldSyncConfig || targetWorkersDev) {
          repository.toggleWorkersDev(accountId, ws.scriptName, targetWorkersDev)
            .onSuccess { enabled -> confirmedWorkersDev = enabled }
            .onFailure { err -> pendingErrors.add("workers.dev: ${err.localizedMessage}") }
        }

        if (shouldSyncConfig && (ws.schedules.isNotEmpty() || ramRecord?.hasPendingConfigSync == true)) {
          repository.updateCronSchedules(accountId, ws.scriptName, ws.schedules.map { it.cron })
            .onFailure { err -> pendingErrors.add("cron: ${err.localizedMessage}") }
        }

        ramRecord?.deletedSecrets?.forEach { secretName ->
          repository.deleteSecret(accountId, ws.scriptName, secretName)
            .onFailure { err -> pendingErrors.add("delete secret $secretName: ${err.localizedMessage}") }
        }
        ramRecord?.stagedSecrets?.forEach { (secretName, secretVal) ->
          repository.putSecret(accountId, ws.scriptName, secretName, secretVal)
            .onFailure { err -> pendingErrors.add("put secret $secretName: ${err.localizedMessage}") }
        }

        val cachedSubdomain = ramDb.getInfraCache(accountId)?.subdomain.orEmpty()
        val resolvedSubdomain =
          ws.accountSubdomain.ifBlank { cachedSubdomain }.ifBlank {
            repository.getAccountSubdomain(accountId)
          }
        if (resolvedSubdomain.isNotBlank()) {
          ramDb.updateInfraCache(accountId) { it.copy(subdomain = resolvedSubdomain) }
        } else if (confirmedWorkersDev) {
          pendingErrors.add("workers.dev subdomain is not yet enabled on your Cloudflare account")
        }

        val cleanFiles = ws.files.map { it.copy(originalContent = it.content) }
        val refreshedDeployments =
          repository.listDeployments(accountId, ws.scriptName).getOrDefault(ws.deployments)
        val baseUpdatedWs =
          ws.copy(
            files = cleanFiles,
            deployments = refreshedDeployments,
            workersDevEnabled = confirmedWorkersDev,
            accountSubdomain = resolvedSubdomain,
            hasModuleSetChanged = false,
          )
        val updatedWs =
          regenerateWranglerFileInWorkspace(baseUpdatedWs).let { built ->
            built.copy(
              files =
                ProjectDirectoryManager.organizeWorkspaceFiles(
                  built.files.map { f -> f.copy(originalContent = f.content) },
                ),
            )
          }

        ramDb.markExported(workspaceKey, updatedWs)
        val updatedRec = ramDb.getWorkspace(workspaceKey)
        withContext(Dispatchers.IO) {
          if (updatedRec != null) {
            projectDirManager.saveWorkspaceRecord(updatedRec)
          }
          autoSaveStore.clearWorkspaceDraft(workspaceKey)
        }
        lastPersistedDraftDigests.remove(workspaceKey)
        pendingCloudDrafts.remove(workspaceKey)
        val summary = withContext(Dispatchers.IO) { projectDirManager.computeStorageSummary() }
        val liveUrl = resolveDefaultPreviewUrl(updatedWs)

        _uiState.update { state ->
          val resolvedPreview = liveUrl.ifBlank { state.previewUrl }
          val updatedApiRequest =
            if (liveUrl.isNotBlank()) {
              ApiRequestUtils.applyDeployedWorkerUrl(
                current = state.apiRequest,
                deployedUrl = liveUrl,
                forceReplace = isNewWorker,
              )
            } else {
              state.apiRequest
            }
          val banner =
            when {
              pendingErrors.isEmpty() && liveUrl.isNotBlank() ->
                "Deployed '${ws.scriptName}' live: $liveUrl"
              pendingErrors.isEmpty() ->
                "Deployed '${ws.scriptName}' to Cloudflare Edge"
              else ->
                "Deployed '${ws.scriptName}' with warnings: ${pendingErrors.joinToString("; ")}"
            }
          state.copy(
            accountSubdomain = resolvedSubdomain.ifBlank { state.accountSubdomain },
            workspace = if (activeWorkspaceKey(state) == workspaceKey) updatedWs else state.workspace,
            restoredDraftBanner = null,
            isDeploying = false,
            isDeployError = pendingErrors.isNotEmpty(),
            activeWorkspaceHasPendingConfig = false,
            ramDbStats = ramDb.computeStats(workspaceKey),
            ramWorkspaces = ramDb.listWorkspaces(),
            projectStorageSummary = summary,
            deployBannerMessage = banner,
            statusMessage = null,
            previewUrl = resolvedPreview,
            previewReloadNonce = state.previewReloadNonce + 1L,
            apiRequest = updatedApiRequest,
            projectMode =
              if (pendingErrors.isEmpty() && state.editorSettings.autoOpenPreviewOnDeploy) ProjectMode.TEST
              else state.projectMode,
            activeBottomTab =
              if (pendingErrors.isEmpty() && state.editorSettings.autoOpenPreviewOnDeploy) BottomPanelTab.WORKER_PREVIEW
              else state.activeBottomTab,
          )
        }
        appendLog(
          "DEPLOY",
          "Deployed '${ws.scriptName}' (${cleanFiles.count { !it.isVirtualReadOnly }} files)" +
            if (liveUrl.isNotBlank()) " -> $liveUrl" else "",
        )
        appendPreviewConsoleEntry("DEPLOY", "LIVE", "Deployed '${ws.scriptName}' -> ${liveUrl.ifBlank { "Cloudflare Edge" }}")
        refreshWorkersInternal(accountId)
      }
      .onFailure { err ->
        _uiState.update {
          it.copy(
            isDeploying = false,
            isDeployError = true,
            statusMessage = null,
            deployBannerMessage = err.localizedMessage ?: "Deploy failed",
          )
        }
        appendLog("ERROR", err.localizedMessage ?: "Deploy failed")
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

  // Find & Replace (Multi-file tab aware)
  fun toggleFindBar(show: Boolean? = null) {
    _uiState.update { it.copy(showFindBar = show ?: !it.showFindBar) }
  }

  fun setFindQuery(q: String) {
    _uiState.update { it.copy(findQuery = q) }
  }

  fun setReplaceQuery(q: String) {
    _uiState.update { it.copy(replaceQuery = q) }
  }

  fun toggleFindMatchCase() {
    _uiState.update { it.copy(findMatchCase = !it.findMatchCase) }
  }

  fun setFindScope(scope: Int) {
    _uiState.update { it.copy(findScope = scope.coerceIn(0, 2)) }
  }

  private fun findSearchablePaths(state: IdeUiState): List<String> {
    val ws = state.workspace ?: return emptyList()
    return when (state.findScope) {
      0 -> listOfNotNull(state.activeFilePath)
      1 -> state.openTabPaths.ifEmpty { listOfNotNull(state.activeFilePath) }
      else -> ws.files.map { it.path }
    }
  }

  private fun fileContentForPath(state: IdeUiState, path: String): String {
    return state.editorFieldValues[path]?.text
      ?: state.workspace?.files?.firstOrNull { it.path == path }?.content
      ?: ""
  }

  fun findNextMatch() {
    val state = _uiState.value
    val q = state.findQuery
    if (q.isEmpty()) return
    val ignoreCase = !state.findMatchCase
    val activePath = state.activeFilePath ?: return
    val tfv = state.activeTextFieldValue
    val from = tfv.selection.end.coerceIn(0, tfv.text.length)
    var idx = tfv.text.indexOf(q, from, ignoreCase = ignoreCase)
    if (idx < 0 && state.findScope == 0 && from > 0) {
      idx = tfv.text.indexOf(q, 0, ignoreCase = ignoreCase)
    }
    if (idx >= 0) {
      _uiState.update { s ->
        s.copy(
          editorRevision = s.editorRevision + 1L,
          editorFieldValues = s.editorFieldValues + (activePath to tfv.copy(selection = TextRange(idx, idx + q.length))),
        )
      }
      return
    }

    // Multi-tab / project wrap-around search
    if (state.findScope > 0) {
      val paths = findSearchablePaths(state)
      val curPos = paths.indexOf(activePath).coerceAtLeast(0)
      for (offset in 1..paths.size) {
        val candidatePath = paths[(curPos + offset) % paths.size]
        val content = fileContentForPath(state, candidatePath)
        val hit = content.indexOf(q, 0, ignoreCase = ignoreCase)
        if (hit >= 0) {
          openFileTab(candidatePath, recordHistory = true)
          _uiState.update { s ->
            val nextTfv = s.editorFieldValues[candidatePath] ?: TextFieldValue(content)
            s.copy(
              editorRevision = s.editorRevision + 1L,
              editorFieldValues = s.editorFieldValues + (candidatePath to nextTfv.copy(selection = TextRange(hit, hit + q.length))),
            )
          }
          return
        }
      }
    }
    _uiState.update { it.copy(statusMessage = "No matches for '$q'") }
  }

  fun findPreviousMatch() {
    val state = _uiState.value
    val q = state.findQuery
    if (q.isEmpty()) return
    val ignoreCase = !state.findMatchCase
    val activePath = state.activeFilePath ?: return
    val tfv = state.activeTextFieldValue
    val before = (tfv.selection.start - 1).coerceAtLeast(-1)
    var idx = if (before >= 0) tfv.text.lastIndexOf(q, before, ignoreCase = ignoreCase) else -1
    if (idx < 0 && state.findScope == 0) {
      idx = tfv.text.lastIndexOf(q, tfv.text.length, ignoreCase = ignoreCase)
    }
    if (idx >= 0) {
      _uiState.update { s ->
        s.copy(
          editorRevision = s.editorRevision + 1L,
          editorFieldValues = s.editorFieldValues + (activePath to tfv.copy(selection = TextRange(idx, idx + q.length))),
        )
      }
      return
    }

    if (state.findScope > 0) {
      val paths = findSearchablePaths(state)
      val curPos = paths.indexOf(activePath).coerceAtLeast(0)
      for (offset in 1..paths.size) {
        val candidatePath = paths[(curPos - offset + paths.size) % paths.size]
        val content = fileContentForPath(state, candidatePath)
        val hit = content.lastIndexOf(q, content.length, ignoreCase = ignoreCase)
        if (hit >= 0) {
          openFileTab(candidatePath, recordHistory = true)
          _uiState.update { s ->
            val nextTfv = s.editorFieldValues[candidatePath] ?: TextFieldValue(content)
            s.copy(
              editorRevision = s.editorRevision + 1L,
              editorFieldValues = s.editorFieldValues + (candidatePath to nextTfv.copy(selection = TextRange(hit, hit + q.length))),
            )
          }
          return
        }
      }
    }
    _uiState.update { it.copy(statusMessage = "No matches for '$q'") }
  }

  fun replaceCurrentMatch() {
    val state = _uiState.value
    val q = state.findQuery
    if (q.isEmpty()) return
    val ignoreCase = !state.findMatchCase
    val activeFile = state.activeFile
    if (activeFile?.isVirtualReadOnly == true) {
      _uiState.update { it.copy(statusMessage = "Active file is read-only") }
      return
    }
    val tfv = state.activeTextFieldValue
    val selStart = tfv.selection.min.coerceIn(0, tfv.text.length)
    val selEnd = tfv.selection.max.coerceIn(0, tfv.text.length)
    val selectedText = if (selEnd > selStart) tfv.text.substring(selStart, selEnd) else ""
    if (selectedText.equals(q, ignoreCase = ignoreCase)) {
      val replacement = state.replaceQuery
      val updated = tfv.text.substring(0, selStart) + replacement + tfv.text.substring(selEnd)
      val newCursor = selStart + replacement.length
      updateEditorValue(TextFieldValue(updated, TextRange(newCursor, newCursor)))
      findNextMatch()
    } else {
      findNextMatch()
    }
  }

  fun replaceAllMatches() {
    val state = _uiState.value
    val q = state.findQuery
    if (q.isEmpty()) return
    val ignoreCase = !state.findMatchCase
    val replacement = state.replaceQuery
    when (state.findScope) {
      0 -> {
        val activeFile = state.activeFile
        if (activeFile?.isVirtualReadOnly == true) {
          _uiState.update { it.copy(statusMessage = "Active file is read-only") }
          return
        }
        val tfv = state.activeTextFieldValue
        val count = countOccurrences(tfv.text, q, ignoreCase)
        if (count == 0) {
          _uiState.update { it.copy(statusMessage = "No matches for '$q' in this tab") }
          return
        }
        val updated = tfv.text.replace(q, replacement, ignoreCase = ignoreCase)
        updateEditorValue(TextFieldValue(updated, TextRange(0)))
        _uiState.update { it.copy(statusMessage = "Replaced $count match(es) in ${activeFile?.path ?: "tab"}") }
      }
      1 -> {
        val openSet = state.openTabPaths.toSet()
        replaceInScopedFiles(q, replacement, ignoreCase = ignoreCase, allowedPaths = openSet, scopeLabel = "open tab(s)")
      }
      else -> {
        replaceInScopedFiles(q, replacement, ignoreCase = ignoreCase, allowedPaths = null, scopeLabel = "file(s)")
      }
    }
  }

  private fun countOccurrences(text: String, query: String, ignoreCase: Boolean): Int {
    if (query.isEmpty()) return 0
    var count = 0
    var idx = 0
    while (idx <= text.length - query.length) {
      val found = text.indexOf(query, idx, ignoreCase = ignoreCase)
      if (found < 0) break
      count++
      idx = found + query.length
    }
    return count
  }

  private fun replaceInScopedFiles(
    findQuery: String,
    replaceQuery: String,
    ignoreCase: Boolean,
    allowedPaths: Set<String>?,
    scopeLabel: String,
  ) {
    val ws = _uiState.value.workspace ?: return
    if (findQuery.isEmpty()) return
    var fileHits = 0
    var totalMatches = 0
    val nextFields = _uiState.value.editorFieldValues.toMutableMap()
    val updatedFiles =
      ws.files.map { f ->
        val inScope = allowedPaths == null || f.path in allowedPaths
        val currentText = nextFields[f.path]?.text ?: f.content
        if (inScope && !f.isVirtualReadOnly && currentText.contains(findQuery, ignoreCase = ignoreCase)) {
          val count = countOccurrences(currentText, findQuery, ignoreCase)
          fileHits++
          totalMatches += count
          val nextText = currentText.replace(findQuery, replaceQuery, ignoreCase = ignoreCase)
          nextFields[f.path] = TextFieldValue(nextText)
          f.copy(content = nextText)
        } else {
          f
        }
      }
    if (fileHits == 0) {
      _uiState.update { it.copy(statusMessage = "No matches found for '$findQuery'") }
      return
    }
    val nextWs = ws.copy(files = updatedFiles)
    val ramKey = activeWorkspaceKey()
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec -> rec.copy(workingWorkspace = nextWs, fieldValues = nextFields) }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = nextWs,
        editorFieldValues = nextFields,
        editorRevision = state.editorRevision + 1L,
        diagnostics = updatedFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Replaced $totalMatches match(es) across $fileHits $scopeLabel",
      )
    }
  }

  fun replaceInAllFiles(findQuery: String, replaceQuery: String) {
    val ws = _uiState.value.workspace ?: return
    if (findQuery.isBlank()) return
    var fileHits = 0
    val nextFields = _uiState.value.editorFieldValues.toMutableMap()
    val updatedFiles =
      ws.files.map { f ->
        if (!f.isVirtualReadOnly && f.content.contains(findQuery, ignoreCase = true)) {
          fileHits++
          val nextText = f.content.replace(findQuery, replaceQuery, ignoreCase = true)
          nextFields[f.path] = TextFieldValue(nextText)
          f.copy(content = nextText)
        } else {
          f
        }
      }
    if (fileHits == 0) {
      _uiState.update { it.copy(statusMessage = "No matches found for '$findQuery'") }
      return
    }
    val nextWs = ws.copy(files = updatedFiles)
    val ramKey = activeWorkspaceKey()
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec -> rec.copy(workingWorkspace = nextWs, fieldValues = nextFields) }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = nextWs,
        editorFieldValues = nextFields,
        editorRevision = state.editorRevision + 1L,
        diagnostics = updatedFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Replaced matches across $fileHits file(s) in RAM",
      )
    }
  }

  fun setSidebarSection(section: SidebarSection) {
    _uiState.update { it.copy(sidebarSection = section) }
    if (!_uiState.value.isLocalScratchpad) {
      when (section) {
        SidebarSection.BINDINGS -> loadCloudflareInfra(forceRefresh = false)
        SidebarSection.PAGES -> loadPagesProjectsInternal(forceRefresh = false)
        else -> Unit
      }
    }
  }

  fun setSearchQuery(q: String) {
    _uiState.update { it.copy(searchQuery = q) }
  }

  fun toggleBottomPanel(tab: BottomPanelTab? = null) {
    _uiState.update { state ->
      if (tab == null) {
        val nextMode =
          if (state.projectMode == ProjectMode.CODE) ProjectMode.CONSOLE else ProjectMode.CODE
        state.copy(projectMode = nextMode, showBottomPanel = !state.showBottomPanel)
      } else {
        val nextMode =
          when (tab) {
            BottomPanelTab.LOCAL_RUN, BottomPanelTab.WORKER_PREVIEW, BottomPanelTab.WORKER_TEST, BottomPanelTab.API_TESTER -> ProjectMode.TEST
            BottomPanelTab.TAIL_LOGS, BottomPanelTab.PROBLEMS, BottomPanelTab.CONSOLE -> ProjectMode.CONSOLE
          }
        state.copy(projectMode = nextMode, showBottomPanel = true, activeBottomTab = tab)
      }
    }
  }

  fun hideBottomPanel() {
    _uiState.update { it.copy(showBottomPanel = false, isBottomPanelExpanded = false) }
  }

  fun toggleBottomPanelExpanded() {
    _uiState.update { it.copy(isBottomPanelExpanded = !it.isBottomPanelExpanded) }
  }

  // Bindings, Compatibility, Cron Schedules, Zones, & Deployments
  // All Worker edits run on-device in RAM and sync to Cloudflare ONLY on Export / Deploy.
  fun toggleWorkersDev(enabled: Boolean) {
    val ws = _uiState.value.workspace ?: return
    val ramKey = activeWorkspaceKey()
    val updatedWs = regenerateWranglerFileInWorkspace(ws.copy(workersDevEnabled = enabled))
    val nextFields = syncWranglerFieldMap(_uiState.value.editorFieldValues, updatedWs)
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(workingWorkspace = updatedWs, fieldValues = nextFields, hasPendingConfigSync = true)
      }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = updatedWs,
        editorFieldValues = nextFields,
        activeWorkspaceHasPendingConfig = true,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "workers.dev ${if (enabled) "enabled" else "disabled"} in RAM (Export/Deploy to apply)",
      )
    }
    appendLog("INFO", "Staged workers.dev=$enabled in RAM for ${ws.scriptName}")
  }

  fun updateAccountWorkersSubdomain(newSubdomainRaw: String) {
    val cleanSub = CloudflareNamingUtils.sanitizeSubdomain(newSubdomainRaw)
    if (!CloudflareNamingUtils.isValidSubdomain(cleanSub)) {
      _uiState.update {
        it.copy(
          statusMessage = "Subdomain must use lowercase letters, numbers, and hyphens (1-63 chars)",
        )
      }
      return
    }
    val stateNow = _uiState.value
    val isScratchpad = stateNow.isLocalScratchpad || stateNow.selectedAccount == null
    if (isScratchpad) {
      val updatedRecords = ramDb.updateAllSubdomainsForAccount(null, cleanSub)
      val activeKey = activeWorkspaceKey()
      val updatedActiveWs =
        stateNow.workspace?.copy(accountSubdomain = cleanSub)
      val liveUrl = resolveDefaultPreviewUrl(updatedActiveWs)
      autoSaveIoScope.launch {
        updatedRecords.forEach { rec -> projectDirManager.saveWorkspaceRecord(rec) }
      }
      _uiState.update { s ->
        s.copy(
          accountSubdomain = cleanSub,
          workspace = updatedActiveWs,
          ramWorkspaces = ramDb.listWorkspaces(),
          ramDbStats = ramDb.computeStats(activeKey),
          previewUrl = liveUrl.ifBlank { s.previewUrl },
          apiRequest =
            if (liveUrl.isNotBlank()) ApiRequestUtils.applyDeployedWorkerUrl(s.apiRequest, liveUrl, forceReplace = true)
            else s.apiRequest,
          statusMessage = "Preview subdomain updated to *.$cleanSub.workers.dev",
        )
      }
      appendLog("INFO", "Updated local preview subdomain to *.$cleanSub.workers.dev")
      return
    }

    val accountId = stateNow.selectedAccount?.id ?: return
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isInfraBusy = true,
          statusMessage = "Updating Cloudflare subdomain to *.$cleanSub.workers.dev…",
        )
      }
      repository
        .updateAccountSubdomain(accountId, cleanSub)
        .onSuccess { confirmedSub ->
          ramDb.updateInfraCache(accountId) { it.copy(subdomain = confirmedSub) }
          val updatedRecords = ramDb.updateAllSubdomainsForAccount(accountId, confirmedSub)
          withContext(Dispatchers.IO) {
            updatedRecords.forEach { rec -> projectDirManager.saveWorkspaceRecord(rec) }
          }
          val activeKey = activeWorkspaceKey()
          val updatedActiveWs =
            _uiState.value.workspace?.let { cur ->
              cur.copy(accountSubdomain = confirmedSub)
            }
          val liveUrl = resolveDefaultPreviewUrl(updatedActiveWs)
          _uiState.update { s ->
            s.copy(
              isInfraBusy = false,
              accountSubdomain = confirmedSub,
              workspace = updatedActiveWs,
              ramWorkspaces = ramDb.listWorkspaces(),
              ramDbStats = ramDb.computeStats(activeKey),
              previewUrl = liveUrl.ifBlank { s.previewUrl },
              previewReloadNonce = s.previewReloadNonce + 1L,
              apiRequest =
                if (liveUrl.isNotBlank()) {
                  ApiRequestUtils.applyDeployedWorkerUrl(s.apiRequest, liveUrl, forceReplace = true)
                } else {
                  s.apiRequest
                },
              deployBannerMessage = "workers.dev subdomain updated to *.$confirmedSub.workers.dev",
              isDeployError = false,
              statusMessage = null,
            )
          }
          appendLog("DEPLOY", "Updated Cloudflare account subdomain -> *.$confirmedSub.workers.dev")
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isInfraBusy = false,
              isDeployError = true,
              statusMessage = err.localizedMessage ?: "Could not update workers.dev subdomain",
            )
          }
          appendLog("ERROR", err.localizedMessage ?: "Failed to update workers.dev subdomain")
        }
    }
  }

  fun renameExistingWorker(
    newScriptNameRaw: String,
    targetRamKey: String? = null,
    remoteScriptName: String? = null,
    deleteOldRemote: Boolean = true,
  ) {
    val activeKey = activeWorkspaceKey()
    val stateNow = _uiState.value
    val isScratchpad = stateNow.isLocalScratchpad || stateNow.selectedAccount == null
    val resolvedLocalOnly =
      (targetRamKey?.startsWith("local_") == true) || (targetRamKey == null && remoteScriptName == null && isScratchpad)
    val cleanName = CloudflareNamingUtils.sanitizeWorkerName(newScriptNameRaw, allowUnderscore = resolvedLocalOnly)
    if (cleanName.isBlank() || (!resolvedLocalOnly && !CloudflareNamingUtils.isValidWorkerName(cleanName))) {
      _uiState.update {
        it.copy(
          statusMessage = "Worker name must use lowercase letters, numbers, and hyphens (1-63 chars)",
        )
      }
      return
    }

    // Determine which worker is being renamed: active workspace, cached Home card, or remote-only Home Worker card.
    val resolvedRamKey =
      when {
        targetRamKey != null -> targetRamKey
        remoteScriptName != null && stateNow.selectedAccount != null -> {
          val candidateKey = ramDb.workspaceKey(stateNow.selectedAccount.id, remoteScriptName)
          candidateKey.takeIf { ramDb.hasWorkspace(it) }
        }
        else -> activeKey
      }

    // Case 1 & 2: Workspace is in RAM/ProjectDirectory (active or unopened Home project card)
    if (resolvedRamKey != null) {
      val isTargetActive = resolvedRamKey == activeKey
      if (isTargetActive) {
        flushAutoSaveNow()
        syncActiveStateToRamDb()
      }
      val rec = ramDb.getWorkspace(resolvedRamKey)
      if (rec != null) {
        val oldWs = rec.workingWorkspace
        val oldName = oldWs.scriptName
        if (oldName == cleanName) {
          _uiState.update { it.copy(statusMessage = "Worker is already named '$cleanName'") }
          return
        }
        val oldWasLocal = resolvedRamKey.startsWith("local_")
        val accountIdForKey = if (oldWasLocal || isScratchpad) null else stateNow.selectedAccount?.id
        val newKey = ramDb.workspaceKey(accountIdForKey, cleanName)
        if (newKey != resolvedRamKey && ramDb.hasWorkspace(newKey)) {
          _uiState.update { it.copy(statusMessage = "A project named '$cleanName' already exists") }
          return
        }

        val withUpdatedTomlFiles = updateWranglerTomlNameInFiles(oldWs.files, cleanName)
        val updatedBase =
          oldWs.copy(
            scriptName = cleanName,
            files = withUpdatedTomlFiles,
          )
        val updatedWs =
          regenerateWranglerFileInWorkspace(updatedBase).let { built ->
            built.copy(files = ProjectDirectoryManager.organizeWorkspaceFiles(built.files))
          }
        val nextFields = syncWranglerFieldMap(rec.fieldValues, updatedWs)

        val shouldDeployRemote = !oldWasLocal && !isScratchpad && stateNow.selectedAccount != null && !rec.isNewUnexportedWorker
        if (!shouldDeployRemote || accountIdForKey == null) {
          val renamedRec =
            ramDb.renameWorkspaceKey(
              oldKey = resolvedRamKey,
              newKey = newKey,
              updatedWorkspace = updatedWs,
              updatedFieldValues = nextFields,
              markCleanBaseline = false,
            ) ?: return
          autoSaveIoScope.launch {
            autoSaveStore.clearWorkspaceDraft(resolvedRamKey)
            projectDirManager.deleteWorkspaceDirectory(resolvedRamKey)
            projectDirManager.saveWorkspaceRecord(renamedRec)
            val summary = projectDirManager.computeStorageSummary()
            _uiState.update { it.copy(projectStorageSummary = summary) }
          }
          if (isTargetActive) {
            val diags = rebuildDiagnosticsFull(updatedWs.files)
            val liveUrl = resolveDefaultPreviewUrl(updatedWs)
            _uiState.update { s ->
              val updatedWorkersList =
                s.workers.map { w -> if (w.id == oldName) w.copy(id = cleanName) else w }
              s.copy(
                workers = updatedWorkersList,
                workspace = updatedWs,
                editorFieldValues = nextFields,
                editorRevision = s.editorRevision + 1L,
                diagnostics = diags,
                ramWorkspaces = ramDb.listWorkspaces(),
                ramDbStats = ramDb.computeStats(newKey),
                previewUrl = liveUrl.ifBlank { s.previewUrl },
                apiRequest =
                  if (liveUrl.isNotBlank()) ApiRequestUtils.applyDeployedWorkerUrl(s.apiRequest, liveUrl, forceReplace = true)
                  else s.apiRequest,
                statusMessage = "Renamed Worker '$oldName' to '$cleanName'",
              )
            }
            recordActiveSessionPointer()
          } else {
            _uiState.update { s ->
              s.copy(
                ramWorkspaces = ramDb.listWorkspaces(),
                statusMessage = "Renamed project '$oldName' to '$cleanName'",
              )
            }
          }
          appendLog("INFO", "Renamed local project '$oldName' -> '$cleanName'")
          return
        }

        // Connected Cloudflare Worker in RAM: deploy under new name, sync settings, optionally delete old remote, migrate disk & RAM!
        viewModelScope.launch {
          _uiState.update {
            it.copy(
              isDeploying = true,
              deployBannerMessage = null,
              statusMessage = "Renaming Worker '$oldName' -> '$cleanName' on Cloudflare…",
            )
          }
          repository
            .renameRemoteWorker(
              accountId = accountIdForKey,
              oldScriptName = oldName,
              renamedWorkspace = updatedWs,
              deleteOldScript = deleteOldRemote,
              stagedSecrets = rec.stagedSecrets,
            )
            .onSuccess { outcome ->
              val finalWs =
                updatedWs.copy(
                  deployments = repository.listDeployments(accountIdForKey, cleanName).getOrDefault(updatedWs.deployments),
                  hasModuleSetChanged = false,
                )
              val renamedRec =
                ramDb.renameWorkspaceKey(
                  oldKey = resolvedRamKey,
                  newKey = newKey,
                  updatedWorkspace = finalWs,
                  updatedFieldValues = nextFields,
                  markCleanBaseline = true,
                )
              withContext(Dispatchers.IO) {
                autoSaveStore.clearWorkspaceDraft(resolvedRamKey)
                projectDirManager.deleteWorkspaceDirectory(resolvedRamKey)
                if (renamedRec != null) {
                  projectDirManager.saveWorkspaceRecord(renamedRec)
                }
              }
              lastPersistedDraftDigests.remove(resolvedRamKey)
              pendingCloudDrafts.remove(resolvedRamKey)
              val summary = withContext(Dispatchers.IO) { projectDirManager.computeStorageSummary() }
              val liveUrl = resolveDefaultPreviewUrl(finalWs)
              val banner =
                buildString {
                  append("Renamed '$oldName' -> '$cleanName'")
                  if (liveUrl.isNotBlank()) append(" ($liveUrl)")
                  outcome.warningMessage?.let { append(" · Note: $it") }
                }
              if (isTargetActive) {
                val diags = rebuildDiagnosticsFull(finalWs.files)
                _uiState.update { s ->
                  s.copy(
                    isDeploying = false,
                    isDeployError = false,
                    workspace = finalWs,
                    editorFieldValues = nextFields,
                    editorRevision = s.editorRevision + 1L,
                    diagnostics = diags,
                    activeWorkspaceHasPendingConfig = false,
                    ramWorkspaces = ramDb.listWorkspaces(),
                    ramDbStats = ramDb.computeStats(newKey),
                    projectStorageSummary = summary,
                    previewUrl = liveUrl.ifBlank { s.previewUrl },
                    previewReloadNonce = s.previewReloadNonce + 1L,
                    apiRequest =
                      if (liveUrl.isNotBlank()) ApiRequestUtils.applyDeployedWorkerUrl(s.apiRequest, liveUrl, forceReplace = true)
                      else s.apiRequest,
                    deployBannerMessage = banner,
                    statusMessage = null,
                  )
                }
                recordActiveSessionPointer()
              } else {
                _uiState.update { s ->
                  s.copy(
                    isDeploying = false,
                    isDeployError = false,
                    ramWorkspaces = ramDb.listWorkspaces(),
                    projectStorageSummary = summary,
                    deployBannerMessage = banner,
                    statusMessage = null,
                  )
                }
              }
              appendLog("DEPLOY", banner)
              refreshWorkersInternal(accountIdForKey)
            }
            .onFailure { err ->
              _uiState.update {
                it.copy(
                  isDeploying = false,
                  isDeployError = true,
                  deployBannerMessage = err.localizedMessage ?: "Failed to rename Worker '$oldName'",
                  statusMessage = null,
                )
              }
              appendLog("ERROR", err.localizedMessage ?: "Failed to rename Worker '$oldName'")
            }
        }
        return
      }
    }

    // Case 3: Remote-only Worker from HomeScreen (not cached locally yet)
    val oldRemote = remoteScriptName?.takeIf { it.isNotBlank() } ?: return
    val accountId = stateNow.selectedAccount?.id ?: return
    if (oldRemote == cleanName) {
      _uiState.update { it.copy(statusMessage = "Worker is already named '$cleanName'") }
      return
    }
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isDeploying = true,
          deployBannerMessage = null,
          statusMessage = "Renaming Worker '$oldRemote' -> '$cleanName' on Cloudflare…",
        )
      }
      val fetched = repository.openWorkerWorkspace(accountId, CfWorkerScript(id = oldRemote)).getOrElse { err ->
        _uiState.update {
          it.copy(
            isDeploying = false,
            isDeployError = true,
            statusMessage = err.localizedMessage ?: "Could not load '$oldRemote' for rename",
          )
        }
        return@launch
      }
      val updatedFiles = updateWranglerTomlNameInFiles(fetched.files, cleanName)
      val renamedWs =
        regenerateWranglerFileInWorkspace(fetched.copy(scriptName = cleanName, files = updatedFiles))
      repository
        .renameRemoteWorker(
          accountId = accountId,
          oldScriptName = oldRemote,
          renamedWorkspace = renamedWs,
          deleteOldScript = deleteOldRemote,
        )
        .onSuccess { outcome ->
          val liveUrl = resolveDefaultPreviewUrl(renamedWs)
          val banner =
            buildString {
              append("Renamed '$oldRemote' -> '$cleanName'")
              if (liveUrl.isNotBlank()) append(" ($liveUrl)")
              outcome.warningMessage?.let { append(" · Note: $it") }
            }
          _uiState.update {
            it.copy(
              isDeploying = false,
              isDeployError = false,
              deployBannerMessage = banner,
              statusMessage = null,
            )
          }
          appendLog("DEPLOY", banner)
          refreshWorkersInternal(accountId)
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(
              isDeploying = false,
              isDeployError = true,
              deployBannerMessage = err.localizedMessage ?: "Failed to rename Worker '$oldRemote'",
              statusMessage = null,
            )
          }
        }
    }
  }

  private fun updateWranglerTomlNameInFiles(
    files: List<WorkerFile>,
    newScriptName: String,
  ): List<WorkerFile> =
    files.map { wf ->
      if (wf.path.equals("wrangler.toml", ignoreCase = true)) {
        val replaced =
          if (Regex("""(?m)^\s*name\s*=\s*["'][^"']*["']""").containsMatchIn(wf.content)) {
            wf.content.replaceFirst(
              Regex("""(?m)^(\s*name\s*=\s*)["'][^"']*["']"""),
              "$1\"$newScriptName\"",
            )
          } else {
            "name = \"$newScriptName\"\n${wf.content}"
          }
        wf.copy(content = replaced)
      } else {
        wf
      }
    }

  fun updateCompatibilitySettings(compatDate: String, nodejsCompat: Boolean) {
    val ws = _uiState.value.workspace ?: return
    val nextFlags =
      if (nodejsCompat) (ws.compatibilityFlags + "nodejs_compat").distinct()
      else ws.compatibilityFlags.filterNot { it == "nodejs_compat" }
    val updatedWs =
      regenerateWranglerFileInWorkspace(
        ws.copy(compatibilityDate = compatDate.trim(), compatibilityFlags = nextFlags),
      )
    val nextFields = syncWranglerFieldMap(_uiState.value.editorFieldValues, updatedWs)
    val ramKey = activeWorkspaceKey()
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(workingWorkspace = updatedWs, fieldValues = nextFields, hasPendingConfigSync = true)
      }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = updatedWs,
        editorFieldValues = nextFields,
        activeWorkspaceHasPendingConfig = true,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Saved compatibility settings in RAM",
      )
    }
    appendLog("INFO", "Staged compatibility_date=$compatDate, nodejs_compat=$nodejsCompat in RAM")
  }

  fun addEnvVar(name: String, value: String) {
    val ws = _uiState.value.workspace ?: return
    val cleanName = name.trim()
    if (cleanName.isBlank()) return
    val updated =
      ws.bindings.filterNot { it.name == cleanName } +
        CfWorkerBinding(name = cleanName, type = "plain_text", text = value)
    val updatedWs = regenerateWranglerFileInWorkspace(ws.copy(bindings = updated))
    val nextFields = syncWranglerFieldMap(_uiState.value.editorFieldValues, updatedWs)
    val ramKey = activeWorkspaceKey()
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(workingWorkspace = updatedWs, fieldValues = nextFields, hasPendingConfigSync = true)
      }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = updatedWs,
        editorFieldValues = nextFields,
        activeWorkspaceHasPendingConfig = true,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Staged env var '$cleanName' in RAM",
      )
    }
    appendLog("INFO", "Staged env var '$cleanName' in RAM for ${ws.scriptName}")
  }

  fun addSecret(name: String, value: String) {
    val ws = _uiState.value.workspace ?: return
    val cleanName = name.trim()
    if (cleanName.isBlank() || value.isBlank()) return
    val updated =
      ws.bindings.filterNot { it.name == cleanName } +
        CfWorkerBinding(name = cleanName, type = "secret_text")
    val updatedWs = regenerateWranglerFileInWorkspace(ws.copy(bindings = updated))
    val nextFields = syncWranglerFieldMap(_uiState.value.editorFieldValues, updatedWs)
    val ramKey = activeWorkspaceKey()
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(
          workingWorkspace = updatedWs,
          fieldValues = nextFields,
          stagedSecrets = rec.stagedSecrets + (cleanName to value),
          deletedSecrets = rec.deletedSecrets - cleanName,
          hasPendingConfigSync = true,
        )
      }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = updatedWs,
        editorFieldValues = nextFields,
        activeWorkspaceHasPendingConfig = true,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Staged secret '$cleanName' in RAM (encrypted on Export/Deploy)",
      )
    }
    appendLog("INFO", "Staged secret '$cleanName' in RAM DB")
  }

  fun deleteBindingOrSecret(binding: CfWorkerBinding) {
    val ws = _uiState.value.workspace ?: return
    val next = ws.bindings.filterNot { it.name == binding.name }
    val updatedWs = regenerateWranglerFileInWorkspace(ws.copy(bindings = next))
    val nextFields = syncWranglerFieldMap(_uiState.value.editorFieldValues, updatedWs)
    val ramKey = activeWorkspaceKey()
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        val nextStaged = rec.stagedSecrets - binding.name
        val nextDeleted =
          if (binding.type == "secret_text" && rec.importedSnapshot.bindings.any { it.name == binding.name }) {
            rec.deletedSecrets + binding.name
          } else rec.deletedSecrets
        rec.copy(
          workingWorkspace = updatedWs,
          fieldValues = nextFields,
          stagedSecrets = nextStaged,
          deletedSecrets = nextDeleted,
          hasPendingConfigSync = true,
        )
      }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = updatedWs,
        editorFieldValues = nextFields,
        activeWorkspaceHasPendingConfig = true,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Removed '${binding.name}' in RAM",
      )
    }
  }

  fun addCronSchedule(cronExpr: String) {
    val ws = _uiState.value.workspace ?: return
    val clean = cronExpr.trim()
    if (clean.isBlank()) return
    val nextSchedules = (ws.schedules.map { it.cron } + clean).distinct().map { com.example.data.remote.CfCronSchedule(cron = it) }
    val updatedWs = regenerateWranglerFileInWorkspace(ws.copy(schedules = nextSchedules))
    val nextFields = syncWranglerFieldMap(_uiState.value.editorFieldValues, updatedWs)
    val ramKey = activeWorkspaceKey()
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(workingWorkspace = updatedWs, fieldValues = nextFields, hasPendingConfigSync = true)
      }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = updatedWs,
        editorFieldValues = nextFields,
        activeWorkspaceHasPendingConfig = true,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Added Cron '$clean' in RAM",
      )
    }
    appendLog("INFO", "Staged Cron triggers in RAM: ${nextSchedules.joinToString { it.cron }}")
  }

  fun removeCronSchedule(cronExpr: String) {
    val ws = _uiState.value.workspace ?: return
    val nextSchedules = ws.schedules.filterNot { it.cron == cronExpr }
    val updatedWs = regenerateWranglerFileInWorkspace(ws.copy(schedules = nextSchedules))
    val nextFields = syncWranglerFieldMap(_uiState.value.editorFieldValues, updatedWs)
    val ramKey = activeWorkspaceKey()
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(workingWorkspace = updatedWs, fieldValues = nextFields, hasPendingConfigSync = true)
      }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = updatedWs,
        editorFieldValues = nextFields,
        activeWorkspaceHasPendingConfig = true,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
      )
    }
  }

  fun loadWorkerVersions() {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    viewModelScope.launch {
      repository.listWorkerVersions(accountId, ws.scriptName).onSuccess { versions ->
        _uiState.update { it.copy(workerVersions = versions) }
      }
    }
  }

  fun deployGradualWorkerSplit(
    primaryVersionId: String,
    primaryPercent: Int,
    secondaryVersionId: String,
    secondaryPercent: Int,
    message: String,
  ) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ws = _uiState.value.workspace ?: return
    val allocations = buildList {
      if (primaryPercent > 0) add(CfDeploymentVersion(versionId = primaryVersionId, percentage = primaryPercent.toDouble()))
      if (secondaryPercent > 0 && secondaryVersionId != primaryVersionId) {
        add(CfDeploymentVersion(versionId = secondaryVersionId, percentage = secondaryPercent.toDouble()))
      }
    }
    viewModelScope.launch {
      repository
        .deployGradualVersions(accountId, ws.scriptName, allocations, message)
        .onSuccess {
          val refreshedDeps = repository.listDeployments(accountId, ws.scriptName).getOrDefault(ws.deployments)
          _uiState.update { state ->
            state.copy(
              workspace = state.workspace?.copy(deployments = refreshedDeps),
              statusMessage = "Deployed gradual split (${allocations.joinToString { "${it.percentage.toInt()}%" }})",
            )
          }
          appendLog("DEPLOY", "Gradual traffic split active on '${ws.scriptName}'")
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Gradual deploy failed") }
        }
    }
  }

  fun syncWranglerFileToWorkspace() {
    val ws = _uiState.value.workspace ?: return
    val cfgFile =
      ws.files.firstOrNull { it.path == "wrangler.jsonc" || it.path == "wrangler.json" || it.path == "wrangler.toml" }
        ?: return
    val parsed =
      WranglerSyncParser.parse(cfgFile.path, cfgFile.content).getOrElse { err ->
        _uiState.update { it.copy(statusMessage = "Wrangler parse error: ${err.localizedMessage}") }
        return
      }

    // Preserve encrypted secrets that aren't serialized verbatim into wrangler.jsonc
    val existingSecrets = ws.bindings.filter { it.type == "secret_text" }
    val mergedBindings = (parsed.bindings + existingSecrets).distinctBy { it.name }
    val nextCompatDate = parsed.compatibilityDate ?: ws.compatibilityDate
    val nextFlags = if (parsed.compatibilityFlags.isNotEmpty()) parsed.compatibilityFlags else ws.compatibilityFlags
    val nextWorkersDev = parsed.workersDev ?: ws.workersDevEnabled
    val nextEntry = parsed.main?.takeIf { m -> ws.files.any { it.path == m } } ?: ws.entryModulePath
    val nextSchedules =
      if (parsed.crons.isNotEmpty()) parsed.crons.map { com.example.data.remote.CfCronSchedule(cron = it) }
      else ws.schedules
    val updatedFiles =
      ws.files.map { f ->
        f.copy(isEntryPoint = f.path == nextEntry)
      }
    val nextWs =
      ws.copy(
        entryModulePath = nextEntry,
        compatibilityDate = nextCompatDate,
        compatibilityFlags = nextFlags,
        workersDevEnabled = nextWorkersDev,
        bindings = mergedBindings,
        schedules = nextSchedules,
        files = updatedFiles,
      )
    val ramKey = activeWorkspaceKey()
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(workingWorkspace = nextWs, hasPendingConfigSync = true)
      }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { state ->
      state.copy(
        workspace = nextWs,
        activeWorkspaceHasPendingConfig = true,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Applied ${cfgFile.fileName} to RAM workspace (${mergedBindings.size} binding(s))",
      )
    }
    appendLog("INFO", "Applied ${cfgFile.fileName} settings in RAM DB (Export/Deploy to push live)")
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
          projectMode = ProjectMode.CONSOLE,
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
              state.copy(tailLogs = next)
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
  fun loadCloudflareInfra(forceRefresh: Boolean = false) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ttlHours = _uiState.value.editorSettings.apiCacheTtlHours
    if (!forceRefresh) {
      val cached = ramDb.getInfraCache(accountId)
      val hasAny =
        cached != null &&
          cached.fetchedAtEpochMs > 0L &&
          (cached.kvNamespaces.isNotEmpty() || cached.zones.isNotEmpty() || cached.d1Databases.isNotEmpty() || cached.r2Buckets.isNotEmpty())
      if (hasAny && cached != null && ApiResponseCacheStore.isFresh(cached.fetchedAtEpochMs, ttlHours)) {
        _uiState.update {
          it.copy(
            kvNamespaces = cached.kvNamespaces,
            zones = cached.zones,
            d1Databases = cached.d1Databases,
            r2Buckets = cached.r2Buckets,
            durableObjects = cached.durableObjects,
            queues = cached.queues,
            isInfraBusy = false,
          )
        }
        return
      }
    }

    viewModelScope.launch {
      if (!forceRefresh) {
        val diskCached = withContext(Dispatchers.IO) { apiCacheStore.loadInfra(accountId) }
        if (diskCached != null) {
          ramDb.updateInfraCache(accountId) { prev ->
            prev.copy(
              kvNamespaces = diskCached.kvNamespaces,
              zones = diskCached.zones,
              d1Databases = diskCached.d1Databases,
              r2Buckets = diskCached.r2Buckets,
              durableObjects = diskCached.durableObjects,
              queues = diskCached.queues,
              fetchedAtEpochMs = diskCached.fetchedAtEpochMs,
            )
          }
          _uiState.update {
            it.copy(
              kvNamespaces = diskCached.kvNamespaces,
              zones = diskCached.zones,
              d1Databases = diskCached.d1Databases,
              r2Buckets = diskCached.r2Buckets,
              durableObjects = diskCached.durableObjects,
              queues = diskCached.queues,
              isKvBusy = false,
              isInfraBusy = false,
            )
          }
          if (ApiResponseCacheStore.isFresh(diskCached.fetchedAtEpochMs, ttlHours)) {
            return@launch
          }
        }
      }

      _uiState.update { it.copy(isInfraBusy = true, isKvBusy = true) }
      val kvList = repository.listKvNamespaces(accountId).getOrDefault(_uiState.value.kvNamespaces)
      val zoneList = repository.listZones(accountId).getOrDefault(_uiState.value.zones)
      val d1List = repository.listD1Databases(accountId).getOrDefault(_uiState.value.d1Databases)
      val r2List = repository.listR2Buckets(accountId).getOrDefault(_uiState.value.r2Buckets)
      val doList = repository.listDurableObjects(accountId).getOrDefault(_uiState.value.durableObjects)
      val qList = repository.listQueues(accountId).getOrDefault(_uiState.value.queues)
      val nowMs = System.currentTimeMillis()

      ramDb.updateInfraCache(accountId) { prev ->
        prev.copy(
          kvNamespaces = kvList,
          zones = zoneList,
          d1Databases = d1List,
          r2Buckets = r2List,
          durableObjects = doList,
          queues = qList,
          fetchedAtEpochMs = nowMs,
        )
      }
      withContext(Dispatchers.IO) {
        apiCacheStore.saveInfra(
          accountId = accountId,
          kvNamespaces = kvList,
          zones = zoneList,
          d1Databases = d1List,
          r2Buckets = r2List,
          durableObjects = doList,
          queues = qList,
          nowEpochMs = nowMs,
        )
      }

      _uiState.update {
        it.copy(
          kvNamespaces = kvList,
          zones = zoneList,
          d1Databases = d1List,
          r2Buckets = r2List,
          durableObjects = doList,
          queues = qList,
          isKvBusy = false,
          isInfraBusy = false,
        )
      }
    }
  }

  fun bindResourceToWorker(binding: CfWorkerBinding) {
    val ws = _uiState.value.workspace ?: return
    if (binding.name.isBlank()) return
    val next = ws.bindings.filterNot { it.name == binding.name } + binding
    val updatedWs = regenerateWranglerFileInWorkspace(ws.copy(bindings = next))
    val nextFields = syncWranglerFieldMap(_uiState.value.editorFieldValues, updatedWs)
    val ramKey = activeWorkspaceKey()
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(workingWorkspace = updatedWs, fieldValues = nextFields, hasPendingConfigSync = true)
      }
    }
    _uiState.update { state ->
      state.copy(
        workspace = updatedWs,
        editorFieldValues = nextFields,
        activeWorkspaceHasPendingConfig = true,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else state.ramDbStats,
        statusMessage = "Bound ${binding.name} (${binding.type}) in RAM",
      )
    }
    appendLog("INFO", "Staged binding '${binding.name}' (${binding.type}) in RAM DB")
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

  fun putKvEntry(keyName: String, value: String, ttlSeconds: Long? = null) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ns = _uiState.value.selectedKvNamespace ?: return
    viewModelScope.launch {
      repository
        .writeKvValue(accountId, ns.id, keyName.trim(), value, expirationTtl = ttlSeconds)
        .onSuccess {
          selectKvNamespace(ns)
          _uiState.update {
            it.copy(
              selectedKvKeyValue = keyName.trim() to value,
              statusMessage = "Saved KV key '${keyName.trim()}'",
            )
          }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "KV write failed") }
        }
    }
  }

  fun bulkImportKvJson(rawJson: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ns = _uiState.value.selectedKvNamespace ?: return
    val itemsResult = runCatching {
      val parsed = JSONTokener(rawJson.trim()).nextValue()
      when (parsed) {
        is JSONArray -> (0 until parsed.length()).map { idx ->
          val obj = parsed.getJSONObject(idx)
          CfKvBulkItem(
            key = obj.getString("key"),
            value = obj.optString("value", ""),
            expirationTtl = obj.optLong("expiration_ttl", 0L).takeIf { it >= 60L },
          )
        }
        is JSONObject -> parsed.keys().asSequence().map { k ->
          CfKvBulkItem(key = k, value = parsed.optString(k, ""))
        }.toList()
        else -> error("Provide a JSON array of {key, value} or a flat JSON object")
      }
    }
    val items = itemsResult.getOrElse { err ->
      _uiState.update { it.copy(statusMessage = "Invalid bulk JSON: ${err.localizedMessage}") }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isKvBusy = true) }
      repository
        .bulkPutKvEntries(accountId, ns.id, items)
        .onSuccess {
          _uiState.update { it.copy(isKvBusy = false, statusMessage = "Imported ${items.size} KV key(s)") }
          selectKvNamespace(ns)
        }
        .onFailure { err ->
          _uiState.update { it.copy(isKvBusy = false, statusMessage = err.localizedMessage ?: "KV bulk import failed") }
        }
    }
  }

  fun exportSelectedKvJson(): String {
    val arr = JSONArray()
    _uiState.value.kvKeys.forEach { k ->
      arr.put(
        JSONObject().apply {
          put("name", k.name)
          if (k.expiration != null) put("expiration", k.expiration)
        },
      )
    }
    return arr.toString(2)
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

  fun createQueue(name: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    if (name.isBlank()) return
    viewModelScope.launch {
      repository
        .createQueue(accountId, name)
        .onSuccess { q ->
          _uiState.update { it.copy(statusMessage = "Created Queue '${q.queueName}'") }
          loadCloudflareInfra(forceRefresh = true)
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Create queue failed") }
        }
    }
  }

  fun deleteQueue(queueId: String) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      repository
        .deleteQueue(accountId, queueId)
        .onSuccess {
          _uiState.update { it.copy(statusMessage = "Deleted Queue") }
          loadCloudflareInfra(forceRefresh = true)
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Delete queue failed") }
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
          loadCloudflareInfra(forceRefresh = true)
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
          loadCloudflareInfra(forceRefresh = true)
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
          loadCloudflareInfra(forceRefresh = true)
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
          loadCloudflareInfra(forceRefresh = true)
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "R2 delete failed") }
        }
    }
  }

  // Cloudflare Pages Management
  fun loadPagesProjects() {
    loadPagesProjectsInternal(forceRefresh = true)
  }

  private fun loadPagesProjectsInternal(forceRefresh: Boolean) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    val ttlHours = _uiState.value.editorSettings.apiCacheTtlHours
    viewModelScope.launch {
      if (!forceRefresh) {
        val cached = withContext(Dispatchers.IO) { apiCacheStore.loadPages(accountId) }
        if (cached != null) {
          _uiState.update { it.copy(pagesProjects = cached.projects, isPagesBusy = false) }
          if (ApiResponseCacheStore.isFresh(cached.fetchedAtEpochMs, ttlHours)) {
            return@launch
          }
        }
      }

      _uiState.update { it.copy(isPagesBusy = true) }
      repository
        .listPagesProjects(accountId)
        .onSuccess { list ->
          withContext(Dispatchers.IO) { apiCacheStore.savePages(accountId, list) }
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

  fun updatePagesBuildConfig(
    projectName: String,
    productionBranch: String,
    buildCommand: String,
    destinationDir: String,
    rootDir: String,
  ) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isPagesBusy = true) }
      repository
        .updatePagesBuildConfig(accountId, projectName, productionBranch, buildCommand, destinationDir, rootDir)
        .onSuccess { updatedProject ->
          _uiState.update {
            it.copy(
              isPagesBusy = false,
              selectedPagesProject = updatedProject,
              pagesProjects = it.pagesProjects.map { p -> if (p.name == updatedProject.name) updatedProject else p },
              statusMessage = "Updated Pages build config for '$projectName'",
            )
          }
        }
        .onFailure { err ->
          _uiState.update { it.copy(isPagesBusy = false, statusMessage = err.localizedMessage ?: "Pages build config failed") }
        }
    }
  }

  fun updatePagesBindings(
    projectName: String,
    environment: String,
    kvBindings: Map<String, String>,
    d1Bindings: Map<String, String>,
    r2Bindings: Map<String, String>,
  ) {
    val accountId = _uiState.value.selectedAccount?.id ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isPagesBusy = true) }
      repository
        .updatePagesBindings(accountId, projectName, environment, kvBindings, d1Bindings, r2Bindings)
        .onSuccess { updatedProject ->
          _uiState.update {
            it.copy(
              isPagesBusy = false,
              selectedPagesProject = updatedProject,
              statusMessage = "Saved Pages $environment bindings",
            )
          }
        }
        .onFailure { err ->
          _uiState.update { it.copy(isPagesBusy = false, statusMessage = err.localizedMessage ?: "Pages bindings failed") }
        }
    }
  }

  fun createPagesSpecialFile(kind: String) {
    val (path, starterContent) =
      when (kind) {
        "_headers" ->
          "_headers" to
            """/*
  X-Frame-Options: DENY
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin

/assets/*
  Cache-Control: public, max-age=31536000, immutable
"""
        "_redirects" ->
          "_redirects" to
            """# Cloudflare Pages redirects
/old-path /new-path 301
/api/* https://api.example.com/:splat 200
"""
        "_routes.json" ->
          "_routes.json" to
            """{
  "version": 1,
  "include": ["/api/*", "/auth/*"],
  "exclude": ["/assets/*", "/*.css", "/*.js"]
}
"""
        "functions/api/hello.js" ->
          "functions/api/hello.js" to WorkerTemplate.PAGES_FUNCTIONS_API.code
        else -> return
      }

    val existingWs = _uiState.value.workspace
    if (existingWs == null) {
      addModuleFile(path)
    } else if (existingWs.files.any { it.path == path }) {
      openFileTab(path)
    } else {
      val newFile =
        WorkerFile(
          path = path,
          content = starterContent,
          originalContent = "",
          contentType = MultipartWorkerParser.inferModuleMimeType(path),
          isEntryPoint = false,
        )
      val nextFiles = existingWs.files + newFile
      val parentDir = if (path.contains('/')) path.substringBeforeLast('/') else ""
      val nextDirs = if (parentDir.isNotBlank()) existingWs.directories + parentDir else existingWs.directories
      _uiState.update { state ->
        state.copy(
          workspace = existingWs.copy(files = nextFiles, directories = nextDirs, hasModuleSetChanged = true),
          openTabPaths = state.openTabPaths + path,
          activeFilePath = path,
          editorFieldValues = state.editorFieldValues + (path to TextFieldValue(starterContent)),
          diagnostics = nextFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
          statusMessage = "Created Pages file '$path'",
        )
      }
      scheduleDraftSave()
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
    val scopeKey = _uiState.value.gitHubCreds?.login?.ifBlank { "default" } ?: "default"
    viewModelScope.launch {
      gitHubRepository.signOut()
      withContext(Dispatchers.IO) { apiCacheStore.invalidateGitHub(scopeKey) }
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
    viewModelScope.launch { refreshGitHubReposInternal(token, forceRefresh = true) }
  }

  private suspend fun refreshGitHubReposInternal(
    token: String,
    forceRefresh: Boolean = true,
  ) {
    val scopeKey = _uiState.value.gitHubCreds?.login?.ifBlank { "default" } ?: "default"
    val ttlHours = _uiState.value.editorSettings.apiCacheTtlHours
    if (!forceRefresh) {
      val cached = withContext(Dispatchers.IO) { apiCacheStore.loadGitHubRepos(scopeKey) }
      if (cached != null) {
        _uiState.update { state ->
          state.copy(
            gitHubRepos = cached.repos,
            gitHubUser = cached.user ?: state.gitHubUser,
            gitHubRateLimitLabel = cached.rateLimitLabel ?: state.gitHubRateLimitLabel,
            isGitHubBusy = false,
          )
        }
        if (ApiResponseCacheStore.isFresh(cached.fetchedAtEpochMs, ttlHours)) {
          return
        }
      }
    }

    _uiState.update { it.copy(isGitHubBusy = true, gitHubError = null) }
    var fetchedUser = _uiState.value.gitHubUser
    var fetchedRateLabel = _uiState.value.gitHubRateLimitLabel
    gitHubRepository.fetchSessionInfo(token).onSuccess { info ->
      val rateLabel =
        if (info.rateLimitRemaining != null && info.rateLimitLimit != null) {
          "${info.rateLimitRemaining}/${info.rateLimitLimit} req"
        } else null
      fetchedUser = info.user
      fetchedRateLabel = rateLabel
      _uiState.update { it.copy(gitHubUser = info.user, gitHubRateLimitLabel = rateLabel) }
    }
    gitHubRepository
      .listRepos(token)
      .onSuccess { repos ->
        val cacheScope = fetchedUser?.login?.ifBlank { scopeKey } ?: scopeKey
        withContext(Dispatchers.IO) {
          apiCacheStore.saveGitHubRepos(
            scopeKey = cacheScope,
            repos = repos,
            user = fetchedUser,
            rateLimitLabel = fetchedRateLabel,
          )
        }
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

  fun inspectWorkflowRunJobs(runId: Long) {
    if (_uiState.value.selectedWorkflowRunId == runId) {
      _uiState.update { it.copy(selectedWorkflowRunId = null, selectedWorkflowJobs = emptyList()) }
      return
    }
    val token = _uiState.value.gitHubCreds?.token ?: return
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    _uiState.update { it.copy(selectedWorkflowRunId = runId, selectedWorkflowJobs = emptyList(), isGitHubBusy = true) }
    viewModelScope.launch {
      gitHubRepository
        .listWorkflowRunJobs(token, owner, repoName, runId)
        .onSuccess { jobs -> _uiState.update { it.copy(selectedWorkflowJobs = jobs, isGitHubBusy = false) } }
        .onFailure { err -> _uiState.update { it.copy(isGitHubBusy = false, statusMessage = err.localizedMessage ?: "Could not fetch workflow jobs") } }
    }
  }

  fun submitPullRequestReview(prNumber: Int, event: String, body: String) {
    val token = _uiState.value.gitHubCreds?.token ?: return
    val linked = _uiState.value.linkedGitRepo
    val repo = _uiState.value.selectedGitHubRepo
    val owner = linked?.owner ?: repo?.owner?.login ?: return
    val repoName = linked?.repoName ?: repo?.name ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isGitHubBusy = true) }
      gitHubRepository
        .submitPullRequestReview(token, owner, repoName, prNumber, event, body)
        .onSuccess {
          _uiState.update { it.copy(isGitHubBusy = false, statusMessage = "Submitted PR #$prNumber review ($event)") }
          appendLog("INFO", "Submitted PR #$prNumber review: $event")
        }
        .onFailure { err ->
          _uiState.update { it.copy(isGitHubBusy = false, statusMessage = err.localizedMessage ?: "PR review failed") }
        }
    }
  }

  fun generateCloudflareGitHubActionWorkflow() {
    val ws = _uiState.value.workspace
    val branch = _uiState.value.linkedGitRepo?.branch ?: _uiState.value.selectedGitHubBranch.ifBlank { "main" }
    val path = ".github/workflows/deploy-cloudflare.yml"
    val yaml =
      """name: Deploy Cloudflare Worker

on:
  push:
    branches:
      - $branch
  workflow_dispatch:

jobs:
  deploy:
    runs-on: ubuntu-latest
    name: Deploy ${ws?.scriptName ?: "worker"}
    steps:
      - uses: actions/checkout@v4
      - name: Deploy with Wrangler
        uses: cloudflare/wrangler-action@v3
        with:
          apiToken: ${'$'}{{ secrets.CLOUDFLARE_API_TOKEN }}
          accountId: ${'$'}{{ secrets.CLOUDFLARE_ACCOUNT_ID }}
"""
    if (ws == null) {
      addModuleFile(path)
      return
    }
    val existingIdx = ws.files.indexOfFirst { it.path == path }
    val updatedFiles =
      if (existingIdx >= 0) {
        ws.files.mapIndexed { idx, f -> if (idx == existingIdx) f.copy(content = yaml) else f }
      } else {
        ws.files +
          WorkerFile(
            path = path,
            content = yaml,
            originalContent = "",
            contentType = "text/yaml",
            isEntryPoint = false,
          )
      }
    _uiState.update { state ->
      state.copy(
        workspace = ws.copy(files = updatedFiles, directories = ws.directories + ".github/workflows", hasModuleSetChanged = true),
        openTabPaths = (state.openTabPaths + path).distinct(),
        activeFilePath = path,
        editorFieldValues = state.editorFieldValues + (path to TextFieldValue(yaml)),
        statusMessage = "Generated Auto-Deploy Action ($path) — commit & push to enable!",
      )
    }
    scheduleDraftSave()
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
        editorRevision = state.editorRevision + 1L,
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
            val mergeOutput =
              performThreeWayMergeIntoWorkspace(
                existingWs = existingWs,
                remoteFiles = bundle.files,
                oldBaseline = _uiState.value.gitWorkingBaseline,
                remoteLabel = "${bundle.repo.name}/${bundle.branch}",
              )
            val nextFiles = mergeOutput.mergedEditable + mergeOutput.virtualFiles
            val firstCloned = bundle.detectedEntryPath
            val conflicts = mergeOutput.conflicts
            val updatedBaseline =
              if (conflicts.isEmpty()) baselineMap
              else {
                // Keep remote baseline for clean files, hold previous baseline for conflicted paths until resolved
                val conflictedPaths = conflicts.map { it.path }.toSet()
                baselineMap.filterKeys { it !in conflictedPaths } +
                  _uiState.value.gitWorkingBaseline.filterKeys { it in conflictedPaths }
              }
            _uiState.update { state ->
              state.copy(
                isGitHubBusy = false,
                selectedGitHubRepo = bundle.repo,
                selectedGitHubBranch = bundle.branch,
                linkedGitRepo = linkedState,
                gitWorkingBaseline = updatedBaseline,
                gitUnstagedExcludedPaths = emptySet(),
                localGitCommits = emptyList(),
                workspace = existingWs.copy(files = nextFiles, isEsModuleFormat = true, hasModuleSetChanged = true),
                openTabPaths = (state.openTabPaths + firstCloned + conflicts.map { it.path }).distinct(),
                activeFilePath = conflicts.firstOrNull()?.path ?: firstCloned,
                editorFieldValues =
                  state.editorFieldValues + mergeOutput.mergedEditable.associate { it.path to TextFieldValue(it.content) },
                editorRevision = state.editorRevision + 1L,
                diagnostics = nextFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
                pendingMergeConflicts = conflicts,
                showConflictResolverModal = conflicts.isNotEmpty(),
                statusMessage =
                  if (conflicts.isNotEmpty()) {
                    "Pull merged with ${conflicts.size} conflict file(s) — resolve in Merge Conflict Resolver"
                  } else {
                    "Merged ${bundle.files.size} files cleanly from ${bundle.repo.fullName}@${bundle.branch}"
                  },
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
    renameExistingWorker(
      newScriptNameRaw = newScriptName,
      targetRamKey = activeWorkspaceKey(),
      deleteOldRemote = false,
    )
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
      // Fallback: if no baseline diff tracked yet, push all non-ignored editable files cleanly
      val ws = state.workspace ?: return
      val msg =
        (commitMessageOverride ?: state.gitCommitMessageInput)
          .trim()
          .ifBlank { "Sync Worker '${ws.scriptName}' from EdgeCode" }
      val ignorePatterns = GitIgnoreMatcher.parsePatterns(ws.files.firstOrNull { it.path == ".gitignore" }?.content)
      val allEditable =
        ws.files
          .filterNot { it.isVirtualReadOnly || it.path == "wrangler.jsonc" }
          .filterNot { GitIgnoreMatcher.isIgnored(it.path, ignorePatterns) }
          .associate { it.path to it.content }
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
    flushActiveEditorBuffer()
    val state = _uiState.value
    if (state.linkedGitRepo == null) return
    if (state.workspace != null && state.editorSettings.warnOnCloudOverwrite && state.hasUnsavedChanges) {
      showUnsavedWarning(
        action = PendingUnsavedAction.PullLinkedGitRepo,
        title = "Pull Over Unsaved Changes?",
        message = "Pulling '${state.linkedGitRepo.fullName}' may overwrite local unsaved modifications.",
        allowKeepLocalDraft = false,
      )
      return
    }
    syncPullLinkedGitRepoInternal()
  }

  private fun syncPullLinkedGitRepoInternal() {
    val linked = _uiState.value.linkedGitRepo ?: return
    cloneGitHubRepository(
      repoUrlOrSlug = linked.fullName,
      branchOverride = linked.branch,
      mergeIntoCurrentWorker = true,
    )
  }

  fun pullGitHubPathsIntoWorker(paths: List<String>) {
    flushActiveEditorBuffer()
    val state = _uiState.value
    if (state.selectedGitHubRepo == null || paths.isEmpty()) return
    if (state.workspace != null && state.editorSettings.warnOnCloudOverwrite && state.hasUnsavedChanges) {
      showUnsavedWarning(
        action = PendingUnsavedAction.PullGitHubPaths(paths),
        title = "Pull Over Unsaved Changes?",
        message = "Pulling ${paths.size} file(s) from GitHub may overwrite unsaved local edits.",
        allowKeepLocalDraft = false,
      )
      return
    }
    pullGitHubPathsIntoWorkerInternal(paths)
  }

  private fun pullGitHubPathsIntoWorkerInternal(paths: List<String>) {
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
            val mergeOutput =
              performThreeWayMergeIntoWorkspace(
                existingWs = existingWs,
                remoteFiles = pulledFiles,
                oldBaseline = _uiState.value.gitWorkingBaseline,
                remoteLabel = "${repo.name}/${_uiState.value.selectedGitHubBranch}",
              )
            val nextFiles = mergeOutput.mergedEditable + mergeOutput.virtualFiles
            val firstPulled = mergeOutput.conflicts.firstOrNull()?.path ?: pulledFiles.first().path
            val conflictedPaths = mergeOutput.conflicts.map { it.path }.toSet()
            val updatedBaseline = _uiState.value.gitWorkingBaseline.toMutableMap().apply {
              pulledFiles.filter { it.path !in conflictedPaths }.forEach { put(it.path, it.content) }
            }
            _uiState.update { state ->
              state.copy(
                isGitHubBusy = false,
                gitWorkingBaseline = updatedBaseline,
                workspace = existingWs.copy(files = nextFiles, isEsModuleFormat = true, hasModuleSetChanged = true),
                openTabPaths = (state.openTabPaths + pulledFiles.map { it.path }).distinct(),
                activeFilePath = firstPulled,
                editorFieldValues =
                  state.editorFieldValues + mergeOutput.mergedEditable.associate { it.path to TextFieldValue(it.content) },
                editorRevision = state.editorRevision + 1L,
                diagnostics = nextFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
                pendingMergeConflicts = mergeOutput.conflicts,
                showConflictResolverModal = mergeOutput.conflicts.isNotEmpty(),
                statusMessage =
                  if (mergeOutput.conflicts.isNotEmpty()) {
                    "Pulled ${pulledFiles.size} file(s) with ${mergeOutput.conflicts.size} conflict(s) to resolve"
                  } else {
                    "Merged ${pulledFiles.size} file(s) cleanly into ${existingWs.scriptName}"
                  },
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

  private data class WorkspaceMergeOutput(
    val mergedEditable: List<WorkerFile>,
    val virtualFiles: List<WorkerFile>,
    val conflicts: List<MergeConflictFile>,
  )

  private fun performThreeWayMergeIntoWorkspace(
    existingWs: WorkerWorkspace,
    remoteFiles: List<WorkerFile>,
    oldBaseline: Map<String, String>,
    remoteLabel: String,
  ): WorkspaceMergeOutput {
    val editable = existingWs.files.filterNot { it.isVirtualReadOnly }.toMutableList()
    val virtual = existingWs.files.filter { it.isVirtualReadOnly }
    val conflicts = mutableListOf<MergeConflictFile>()

    remoteFiles.forEach { rf ->
      val idx = editable.indexOfFirst { it.path == rf.path }
      if (idx < 0) {
        editable.add(rf.copy(isEntryPoint = false))
      } else {
        val localFile = editable[idx]
        if (localFile.content == rf.content) {
          editable[idx] = localFile.copy(originalContent = rf.content)
        } else {
          val baseText = oldBaseline[rf.path].orEmpty()
          val res =
            MergeConflictResolver.threeWayMerge(
              path = rf.path,
              baseContent = baseText,
              localContent = localFile.content,
              remoteContent = rf.content,
              localLabel = existingWs.scriptName,
              remoteLabel = remoteLabel,
            )
          editable[idx] =
            localFile.copy(
              content = res.mergedContent,
              originalContent = rf.content,
            )
          if (res.hasConflicts) {
            conflicts.add(
              MergeConflictFile(
                path = rf.path,
                baseContent = baseText,
                localContent = localFile.content,
                remoteContent = rf.content,
                mergedContent = res.mergedContent,
                hunks = res.hunks,
              ),
            )
          }
        }
      }
    }
    return WorkspaceMergeOutput(editable, virtual, conflicts)
  }

  private fun buildDiffEntries(
    intent: DiffReviewIntent,
    state: IdeUiState = _uiState.value,
  ): List<FileDiffEntry> {
    val ws = state.workspace ?: return emptyList()
    if (intent == DiffReviewIntent.PRE_GIT_PUSH) {
      return state.gitChangedFiles.map { change ->
        val diff = CodeFormatter.computeLineDiff(change.baseContent, change.currentContent)
        val added = diff.count { it.type == DiffType.ADDED }
        val removed = diff.count { it.type == DiffType.REMOVED }
        val hasConflict = MergeConflictResolver.hasConflictMarkers(change.currentContent)
        val origin =
          when {
            hasConflict -> DiffFileOrigin.CONFLICT
            change.status == GitFileChangeStatus.ADDED -> DiffFileOrigin.ADDED
            change.status == GitFileChangeStatus.DELETED -> DiffFileOrigin.DELETED
            else -> DiffFileOrigin.MODIFIED
          }
        FileDiffEntry(
          path = change.path,
          origin = origin,
          originalLabel = "Git Baseline",
          modifiedLabel = if (change.isStaged) "Staged Local" else "Working Tree",
          originalContent = change.baseContent,
          modifiedContent = change.currentContent,
          addedCount = added,
          removedCount = removed,
          hasConflicts = hasConflict,
        )
      }
    }

    return ws.files
      .filterNot { it.isVirtualReadOnly }
      .filter { it.isDirty || MergeConflictResolver.hasConflictMarkers(it.content) }
      .map { wf ->
        val diff = CodeFormatter.computeLineDiff(wf.originalContent, wf.content)
        val added = diff.count { it.type == DiffType.ADDED }
        val removed = diff.count { it.type == DiffType.REMOVED }
        val hasConflict = MergeConflictResolver.hasConflictMarkers(wf.content)
        val origin =
          when {
            hasConflict -> DiffFileOrigin.CONFLICT
            wf.originalContent.isEmpty() -> DiffFileOrigin.ADDED
            else -> DiffFileOrigin.MODIFIED
          }
        FileDiffEntry(
          path = wf.path,
          origin = origin,
          originalLabel = if (state.isLocalScratchpad) "Disk Baseline" else "Deployed CF",
          modifiedLabel = "Local Edits",
          originalContent = wf.originalContent,
          modifiedContent = wf.content,
          addedCount = added,
          removedCount = removed,
          hasConflicts = hasConflict,
        )
      }
  }

  fun openDiffReview(intent: DiffReviewIntent) {
    val state = _uiState.value
    val entries = buildDiffEntries(intent, state)
    _uiState.update {
      it.copy(
        showDiffReviewModal = true,
        diffReviewIntent = intent,
        diffReviewEntries = entries,
      )
    }
  }

  fun dismissDiffReview() {
    _uiState.update { it.copy(showDiffReviewModal = false) }
  }

  fun proceedFromDiffReview() {
    val intent = _uiState.value.diffReviewIntent
    _uiState.update { it.copy(showDiffReviewModal = false) }
    when (intent) {
      DiffReviewIntent.PRE_DEPLOY -> deployActiveWorker()
      DiffReviewIntent.PRE_GIT_PUSH -> pushQueuedAndActiveChangesToGitHub()
      DiffReviewIntent.WORKSPACE -> Unit
    }
  }

  fun openConflictResolver() {
    val state = _uiState.value
    val existing = state.pendingMergeConflicts
    if (existing.isNotEmpty()) {
      _uiState.update { it.copy(showConflictResolverModal = true) }
      return
    }
    val ws = state.workspace ?: return
    val detected =
      ws.files
        .filterNot { it.isVirtualReadOnly }
        .mapNotNull { wf ->
          val hunks = MergeConflictResolver.parseConflictMarkers(wf.content)
          if (hunks.isEmpty()) null
          else
            MergeConflictFile(
              path = wf.path,
              baseContent = wf.originalContent,
              localContent = wf.content,
              remoteContent = wf.originalContent,
              mergedContent = wf.content,
              hunks = hunks,
            )
        }
    if (detected.isEmpty()) {
      _uiState.update { it.copy(statusMessage = "No merge conflicts detected in workspace.") }
      return
    }
    _uiState.update {
      it.copy(
        pendingMergeConflicts = detected,
        showConflictResolverModal = true,
      )
    }
  }

  fun dismissConflictResolver() {
    _uiState.update { it.copy(showConflictResolverModal = false) }
  }

  fun resolveConflictHunk(
    filePath: String,
    hunkIndex: Int,
    choice: ConflictChoice,
  ) {
    _uiState.update { state ->
      val updatedConflicts =
        state.pendingMergeConflicts.map { cf ->
          if (cf.path != filePath) cf
          else {
            val nextContent = MergeConflictResolver.resolveConflictHunk(cf.mergedContent, hunkIndex, choice)
            cf.copy(
              mergedContent = nextContent,
              hunks = MergeConflictResolver.parseConflictMarkers(nextContent),
            )
          }
        }
      state.copy(pendingMergeConflicts = updatedConflicts)
    }
  }

  fun resolveAllConflictsInFile(
    filePath: String,
    choice: ConflictChoice,
  ) {
    _uiState.update { state ->
      val updatedConflicts =
        state.pendingMergeConflicts.map { cf ->
          if (cf.path != filePath) cf
          else {
            val nextContent = MergeConflictResolver.resolveAllConflicts(cf.mergedContent, choice)
            cf.copy(
              mergedContent = nextContent,
              hunks = emptyList(),
            )
          }
        }
      state.copy(pendingMergeConflicts = updatedConflicts)
    }
  }

  fun applyResolvedConflictsToWorkspace() {
    val state = _uiState.value
    val ws = state.workspace ?: return
    val conflicts = state.pendingMergeConflicts
    if (conflicts.any { it.hunks.isNotEmpty() }) {
      _uiState.update { it.copy(statusMessage = "Resolve all remaining conflict hunks first.") }
      return
    }
    val resolvedMap = conflicts.associate { it.path to it.mergedContent }
    val nextFiles =
      ws.files.map { wf ->
        val resolved = resolvedMap[wf.path]
        if (resolved != null) wf.copy(content = resolved) else wf
      }
    val nextWs = ws.copy(files = nextFiles, hasModuleSetChanged = true)
    val nextFields =
      state.editorFieldValues + resolvedMap.mapValues { (_, text) -> TextFieldValue(text) }
    val nextBaseline =
      state.gitWorkingBaseline.toMutableMap().apply {
        conflicts.forEach { put(it.path, it.remoteContent) }
      }
    val ramKey = activeWorkspaceKey(state)
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(workingWorkspace = nextWs, fieldValues = nextFields)
      }
    }
    _uiState.update { s ->
      s.copy(
        workspace = nextWs,
        editorFieldValues = nextFields,
        editorRevision = s.editorRevision + 1L,
        gitWorkingBaseline = nextBaseline,
        diagnostics = nextFiles.flatMap { CodeAnalyzer.analyzeFile(it) },
        pendingMergeConflicts = emptyList(),
        showConflictResolverModal = false,
        statusMessage = "Applied merged changes across ${conflicts.size} resolved file(s)",
      )
    }
    appendLog("INFO", "Resolved merge conflicts for ${conflicts.joinToString { it.path }}")
  }

  // API Tester Pro (Environment Profiles, Importer, Assertions & Collections)
  fun updateApiRequest(transform: (ApiTesterRequest) -> ApiTesterRequest) {
    _uiState.update { it.copy(apiRequest = transform(it.apiRequest)) }
  }

  fun updateApiEnvVars(vars: List<ApiEnvVar>) {
    _uiState.update { state ->
      val updatedProfiles =
        state.apiEnvProfiles.map { profile ->
          if (profile.id == state.activeApiEnvProfileId) profile.copy(vars = vars) else profile
        }
      state.copy(
        apiEnvVars = vars,
        apiEnvProfiles = updatedProfiles,
      )
    }
    persistApiTester()
  }

  fun selectApiEnvProfile(profileId: String) {
    _uiState.update { state ->
      val target = state.apiEnvProfiles.firstOrNull { it.id == profileId } ?: return@update state
      state.copy(
        activeApiEnvProfileId = target.id,
        apiEnvVars = target.vars,
        statusMessage = "API Environment: ${target.name}",
      )
    }
    persistApiTester()
  }

  fun createApiEnvProfile(name: String) {
    val clean = name.trim()
    if (clean.isBlank()) return
    val newId = "env_${UUID.randomUUID().toString().take(8)}"
    val newProfile =
      ApiEnvironmentProfile(
        id = newId,
        name = clean,
        vars = listOf(ApiEnvVar(key = "baseUrl", value = ""), ApiEnvVar(key = "apiToken", value = "")),
      )
    _uiState.update { state ->
      state.copy(
        apiEnvProfiles = state.apiEnvProfiles + newProfile,
        activeApiEnvProfileId = newId,
        apiEnvVars = newProfile.vars,
        statusMessage = "Created environment '$clean'",
      )
    }
    persistApiTester()
  }

  fun renameApiEnvProfile(profileId: String, newName: String) {
    val clean = newName.trim()
    if (clean.isBlank()) return
    _uiState.update { state ->
      state.copy(
        apiEnvProfiles = state.apiEnvProfiles.map { if (it.id == profileId) it.copy(name = clean) else it },
        statusMessage = "Renamed environment to '$clean'",
      )
    }
    persistApiTester()
  }

  fun deleteApiEnvProfile(profileId: String) {
    _uiState.update { state ->
      if (state.apiEnvProfiles.size <= 1) {
        return@update state.copy(statusMessage = "At least one environment profile is required.")
      }
      val remaining = state.apiEnvProfiles.filterNot { it.id == profileId }
      val nextActive =
        if (state.activeApiEnvProfileId == profileId) remaining.first()
        else remaining.firstOrNull { it.id == state.activeApiEnvProfileId } ?: remaining.first()
      state.copy(
        apiEnvProfiles = remaining,
        activeApiEnvProfileId = nextActive.id,
        apiEnvVars = nextActive.vars,
        statusMessage = "Switched to environment '${nextActive.name}'",
      )
    }
    persistApiTester()
  }

  fun importApiCollection(
    rawJson: String,
    importEnvVars: Boolean = true,
    loadFirstRequest: Boolean = true,
  ): Result<ApiImportResult> {
    val parsedResult = ApiCollectionImporter.parseImportJson(rawJson)
    val parsed = parsedResult.getOrElse { err ->
      _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Import failed") }
      return Result.failure(err)
    }
    _uiState.update { state ->
      val nextCollections = parsed.requests + state.apiCollections
      val nextEnvVars =
        if (importEnvVars && parsed.extractedEnvVars.isNotEmpty()) {
          val existingKeys = state.apiEnvVars.map { it.key.lowercase() }.toSet()
          val merged = state.apiEnvVars.toMutableList()
          parsed.extractedEnvVars.forEach { ev ->
            if (ev.key.lowercase() !in existingKeys) {
              merged.add(ev)
            }
          }
          merged
        } else {
          state.apiEnvVars
        }
      val nextProfiles =
        state.apiEnvProfiles.map { profile ->
          if (profile.id == state.activeApiEnvProfileId) profile.copy(vars = nextEnvVars) else profile
        }
      state.copy(
        apiCollections = nextCollections,
        apiEnvVars = nextEnvVars,
        apiEnvProfiles = nextProfiles,
        apiRequest = if (loadFirstRequest) parsed.requests.first() else state.apiRequest,
        apiAssertionResults = if (loadFirstRequest) emptyList() else state.apiAssertionResults,
        statusMessage = "Imported ${parsed.requests.size} request(s) from ${parsed.sourceFormat.label} '${parsed.title}'",
      )
    }
    persistApiTester()
    appendLog("INFO", "Imported ${parsed.requests.size} API requests from ${parsed.title}")
    return Result.success(parsed)
  }

  fun runApiTesterRequest() {
    val state = _uiState.value
    val req = state.apiRequest
    val workerBaseUrl = state.workspace?.workersDevUrl
    val resolvedUrlResult = ApiRequestUtils.resolveUrl(req, state.apiEnvVars, workerBaseUrl)
    val url = resolvedUrlResult.getOrElse { err ->
      val msg = err.localizedMessage ?: "Invalid request URL"
      _uiState.update {
        it.copy(
          isRunningHttpTest = false,
          statusMessage = msg,
          httpTestResult =
            HttpTestResult(
              url = req.url,
              method = req.method,
              statusCode = 0,
              statusText = "Invalid URL",
              durationMs = 0L,
              headers = emptyMap(),
              body = "",
              errorMessage = msg,
            ),
          apiAssertionResults = emptyList(),
        )
      }
      return
    }
    val (headersMap, bodyText) = ApiRequestUtils.buildHeadersAndBody(req, state.apiEnvVars, workerBaseUrl)

    _uiState.update {
      it.copy(
        isRunningHttpTest = true,
        projectMode = ProjectMode.TEST,
        showBottomPanel = true,
        activeBottomTab = BottomPanelTab.API_TESTER,
      )
    }

    viewModelScope.launch {
      val result = repository.executeLiveHttpTest(url, req.method, headersMap, bodyText)
      val assertionsEval = ApiRequestUtils.evaluateAssertions(req.assertions, result)
      val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
      val historyItem =
        ApiTesterHistoryItem(
          id = UUID.randomUUID().toString().take(8),
          timestamp = ts,
          request = req,
          resolvedUrl = url,
          statusCode = result.statusCode,
          durationMs = result.durationMs,
        )
      _uiState.update { s ->
        s.copy(
          isRunningHttpTest = false,
          httpTestResult = result,
          apiAssertionResults = assertionsEval,
          apiHistory = (listOf(historyItem) + s.apiHistory).take(25),
        )
      }
      persistApiTester()
      val assertNote =
        if (assertionsEval.isEmpty()) ""
        else " • ${assertionsEval.count { it.passed }}/${assertionsEval.size} assertions passed"
      appendLog(
        if (result.statusCode in 200..399) "HTTP" else "WARN",
        "${result.method} ${result.url} -> ${result.statusCode} (${result.durationMs}ms)$assertNote",
      )
    }
  }

  fun saveCurrentApiRequestToCollection(customName: String) {
    val cleanName = customName.trim().ifBlank { _uiState.value.apiRequest.url.take(28) }
    val item =
      _uiState.value.apiRequest.copy(
        id = UUID.randomUUID().toString().take(8),
        name = cleanName,
      )
    _uiState.update { s ->
      s.copy(
        apiCollections = listOf(item) + s.apiCollections,
        statusMessage = "Saved '$cleanName' to API collection",
      )
    }
    persistApiTester()
  }

  fun loadApiRequest(request: ApiTesterRequest) {
    _uiState.update { it.copy(apiRequest = request, apiAssertionResults = emptyList()) }
  }

  fun deleteApiCollectionItem(id: String) {
    _uiState.update { s -> s.copy(apiCollections = s.apiCollections.filterNot { it.id == id }) }
    persistApiTester()
  }

  fun clearApiHistory() {
    _uiState.update { it.copy(apiHistory = emptyList()) }
    persistApiTester()
  }

  private fun persistApiTester() {
    val s = _uiState.value
    viewModelScope.launch {
      draftManager.saveApiTesterState(
        PersistedApiTesterState(
          collections = s.apiCollections,
          history = s.apiHistory,
          envVars = s.apiEnvVars,
          environmentProfiles = s.apiEnvProfiles,
          activeEnvProfileId = s.activeApiEnvProfileId,
        ),
      )
    }
  }

  fun dismissDraftBanner() {
    _uiState.update { it.copy(restoredDraftBanner = null) }
  }

  fun discardRestoredDraft() {
    val key = activeWorkspaceKey()
    revertWorkspaceToImportedBaseline()
    if (key != null) {
      clearLocalAutoSaveTrackingForWorkspace(key)
      pendingCloudDrafts.remove(key)
      autoSaveIoScope.launch { autoSaveWriteMutex.withLock { autoSaveStore.clearWorkspaceDraft(key) } }
    }
    _uiState.update { it.copy(restoredDraftBanner = null) }
  }

  fun clearAllAutoSavedDrafts() {
    lastPersistedDraftDigests.clear()
    lastPersistedNonSpacingDigests.clear()
    lastLocallyAutoSavedNonSpacing.clear()
    pendingCloudDrafts.clear()
    autoSaveIoScope.launch {
      autoSaveWriteMutex.withLock {
        autoSaveStore.clearAllDrafts()
      }
    }
    _uiState.update {
      it.copy(
        lastAutoSavedEpochMs = null,
        statusMessage = "Cleared all locally auto-saved drafts",
      )
    }
  }

  private fun triggerImmediateLocalAutoSave(
    stateSnapshot: IdeUiState,
    modifiedExternalPath: String? = null,
  ) {
    val activeKey = activeWorkspaceKey(stateSnapshot) ?: return
    val ramRecord = ramDb.getWorkspace(activeKey)
    val snapshot = buildActiveAutoSaveSnapshot(stateSnapshot)
    autoSaveIoScope.launch {
      autoSaveWriteMutex.withLock {
        if (ramRecord != null) {
          projectDirManager.saveWorkspaceRecord(ramRecord)
        }
        if (snapshot != null) {
          val (draft, pointer) = snapshot
          val nonSpacingDigest =
            AutoSaveDraftStore.computeNonSpacingContentDigest(
              files = draft.files,
              openTabs = draft.openTabs,
              activeFilePath = draft.activeFilePath,
              entryModulePath = draft.entryModulePath,
            )
          autoSaveStore.saveWorkspaceDraft(draft)
          autoSaveStore.saveLastActiveSession(pointer)
          lastPersistedDraftDigests[draft.workspaceKey] = draft.contentDigest
          lastPersistedNonSpacingDigests[draft.workspaceKey] = nonSpacingDigest
        }
        if (modifiedExternalPath != null) {
          val uriStr = stateSnapshot.externalFileUris[modifiedExternalPath]
          val liveContent =
            stateSnapshot.editorFieldValues[modifiedExternalPath]?.text
              ?: stateSnapshot.workspace?.files?.firstOrNull { it.path == modifiedExternalPath }?.content
          if (uriStr != null && liveContent != null) {
            runCatching { Uri.parse(uriStr) }.getOrNull()?.let { uri ->
              DeviceFileHelper.writeTextToUri(getApplication(), uri, liveContent)
            }
          }
        }
        val savedAt = System.currentTimeMillis()
        _uiState.update { it.copy(lastAutoSavedEpochMs = savedAt) }
      }
    }
  }

  fun flushAutoSaveNow(clearSessionPointer: Boolean = false) {
    flushActiveEditorBuffer()
    val state = _uiState.value
    val activeKey = activeWorkspaceKey(state)
    val snapshot = buildActiveAutoSaveSnapshot(state)
    autoSaveIoScope.launch {
      autoSaveWriteMutex.withLock {
        if (snapshot != null) {
          val (draft, pointer) = snapshot
          val prevDigest = lastPersistedDraftDigests[draft.workspaceKey]
          if (prevDigest != draft.contentDigest) {
            autoSaveStore.saveWorkspaceDraft(draft)
            lastPersistedDraftDigests[draft.workspaceKey] = draft.contentDigest
            lastPersistedNonSpacingDigests[draft.workspaceKey] =
              AutoSaveDraftStore.computeNonSpacingContentDigest(
                files = draft.files,
                openTabs = draft.openTabs,
                activeFilePath = draft.activeFilePath,
                entryModulePath = draft.entryModulePath,
              )
            draft.files.forEach { f ->
              lastLocallyAutoSavedNonSpacing["${draft.workspaceKey}::${f.path}"] =
                AutoSaveDraftStore.stripSpacing(f.content)
            }
          }
          if (activeKey != null) {
            ramDb.getWorkspace(activeKey)?.let { projectDirManager.saveWorkspaceRecord(it) }
            val summary = projectDirManager.computeStorageSummary()
            _uiState.update {
              if (it.projectStorageSummary != summary) it.copy(projectStorageSummary = summary) else it
            }
          }
          // Best-effort save-back to opened external file URIs when auto-saving/backgrounding
          state.externalFileUris.forEach { (path, uriStr) ->
            val wsFile = state.workspace?.files?.firstOrNull { it.path == path }
            val liveContent = state.editorFieldValues[path]?.text ?: wsFile?.content
            if (wsFile != null && liveContent != null && liveContent != wsFile.originalContent) {
              runCatching { Uri.parse(uriStr) }.getOrNull()?.let { uri ->
                DeviceFileHelper.writeTextToUri(getApplication(), uri, liveContent)
              }
            }
          }
          if (clearSessionPointer) {
            autoSaveStore.clearLastActiveSession()
          } else {
            autoSaveStore.saveLastActiveSession(pointer)
          }
        } else if (clearSessionPointer) {
          autoSaveStore.clearLastActiveSession()
        }
      }
    }
  }

  private fun scheduleDraftSave(syncRamDb: Boolean = true) {
    if (syncRamDb) {
      syncActiveStateToRamDb()
    }
    val settings = _uiState.value.editorSettings
    draftSaveJob?.cancel()
    draftSaveJob =
      viewModelScope.launch {
        delay(1800L)
        val state = _uiState.value
        val activeKey = activeWorkspaceKey(state)
        if (state.workspace == null) return@launch
        val ramRecord = activeKey?.let { ramDb.getWorkspace(it) }
        val snapshot =
          if (settings.autoSaveEnabled) {
            withContext(Dispatchers.Default) { buildActiveAutoSaveSnapshot(state) }
          } else null
        withContext(Dispatchers.IO) {
          autoSaveWriteMutex.withLock {
            if (ramRecord != null) {
              projectDirManager.saveWorkspaceRecord(ramRecord)
            }
            if (snapshot != null) {
              persistSnapshotIfChangedLocked(snapshot.first, snapshot.second, forceSessionPointer = false)
            }
          }
        }
      }
  }

  private fun syncPeriodicAutoSaveLoop() {
    periodicAutoSaveJob?.cancel()
    val settings = _uiState.value.editorSettings
    if (!settings.autoSaveEnabled || _uiState.value.workspace == null) return
    val intervalMs = settings.autoSaveIntervalSeconds.coerceIn(3, 60) * 1_000L
    periodicAutoSaveJob =
      viewModelScope.launch {
        while (isActive) {
          delay(intervalMs)
          val state = _uiState.value
          if (!state.editorSettings.autoSaveEnabled) break
          if (state.workspace == null) continue
          val snapshot = withContext(Dispatchers.Default) { buildActiveAutoSaveSnapshot(state) } ?: continue
          val (draft, pointer) = snapshot
          val nonSpacingDigest =
            AutoSaveDraftStore.computeNonSpacingContentDigest(
              files = draft.files,
              openTabs = draft.openTabs,
              activeFilePath = draft.activeFilePath,
              entryModulePath = draft.entryModulePath,
            )
          val prevNonSpacingDigest = lastPersistedNonSpacingDigests[draft.workspaceKey]
          if (prevNonSpacingDigest != nonSpacingDigest) {
            withContext(Dispatchers.IO) {
              autoSaveWriteMutex.withLock {
                persistSnapshotIfChangedLocked(draft, pointer, forceSessionPointer = false)
              }
            }
          }
        }
      }
  }

  private fun recordActiveSessionPointer() {
    val snapshot = buildActiveAutoSaveSnapshot(_uiState.value) ?: return
    autoSaveIoScope.launch {
      autoSaveWriteMutex.withLock {
        autoSaveStore.saveLastActiveSession(snapshot.second)
        val (draft, _) = snapshot
        if (lastPersistedDraftDigests[draft.workspaceKey] != draft.contentDigest) {
          autoSaveStore.saveWorkspaceDraft(draft)
          lastPersistedDraftDigests[draft.workspaceKey] = draft.contentDigest
          lastPersistedNonSpacingDigests[draft.workspaceKey] =
            AutoSaveDraftStore.computeNonSpacingContentDigest(
              files = draft.files,
              openTabs = draft.openTabs,
              activeFilePath = draft.activeFilePath,
              entryModulePath = draft.entryModulePath,
            )
        }
      }
    }
  }

  private suspend fun persistSnapshotIfChangedLocked(
    draft: AutoSavedWorkspaceDraft,
    pointer: AutoSaveSessionPointer,
    forceSessionPointer: Boolean,
  ) {
    val prevDigest = lastPersistedDraftDigests[draft.workspaceKey]
    if (prevDigest != draft.contentDigest) {
      autoSaveStore.saveWorkspaceDraft(draft)
      autoSaveStore.saveLastActiveSession(pointer)
      lastPersistedDraftDigests[draft.workspaceKey] = draft.contentDigest
      lastPersistedNonSpacingDigests[draft.workspaceKey] =
        AutoSaveDraftStore.computeNonSpacingContentDigest(
          files = draft.files,
          openTabs = draft.openTabs,
          activeFilePath = draft.activeFilePath,
          entryModulePath = draft.entryModulePath,
        )
    } else if (forceSessionPointer) {
      autoSaveStore.saveLastActiveSession(pointer)
    }
  }

  private fun buildActiveAutoSaveSnapshot(state: IdeUiState): Pair<AutoSavedWorkspaceDraft, AutoSaveSessionPointer>? {
    val ws = state.workspace ?: return null
    val key = activeWorkspaceKey(state) ?: return null
    val isScratchpad = state.isLocalScratchpad || state.selectedAccount == null
    val accountId = if (isScratchpad) "" else state.selectedAccount?.id.orEmpty()
    val syncedFiles =
      ws.files.filterNot { it.isVirtualReadOnly }.map { f ->
        val fieldContent = state.editorFieldValues[f.path]?.text
        if (fieldContent != null && fieldContent != f.content) f.copy(content = fieldContent) else f
      }
    if (syncedFiles.isEmpty()) return null
    val selections =
      state.editorFieldValues.mapValues { (_, tfv) ->
        tfv.selection.start to tfv.selection.end
      }
    val digest =
      AutoSaveDraftStore.computeContentDigest(
        files = syncedFiles,
        openTabs = state.openTabPaths,
        activeFilePath = state.activeFilePath,
        entryModulePath = ws.entryModulePath,
      )
    val now = System.currentTimeMillis()
    val draft =
      AutoSavedWorkspaceDraft(
        workspaceKey = key,
        scriptName = ws.scriptName,
        isLocalScratchpad = isScratchpad,
        accountId = accountId,
        entryModulePath = ws.entryModulePath,
        compatibilityDate = ws.compatibilityDate,
        openTabs = state.openTabPaths,
        activeFilePath = state.activeFilePath,
        cursorSelections = selections,
        files = syncedFiles,
        savedAtEpochMs = now,
        contentDigest = digest,
      )
    val pointer =
      AutoSaveSessionPointer(
        workspaceKey = key,
        scriptName = ws.scriptName,
        isLocalScratchpad = isScratchpad,
        accountId = accountId,
      )
    return draft to pointer
  }

  private fun restoreLocalDraftIntoEditor(draft: AutoSavedWorkspaceDraft) {
    val record = ramDb.getWorkspace(draft.workspaceKey) ?: return
    ramDb.setActiveWorkspaceKey(draft.workspaceKey)
    val ws = record.workingWorkspace
    val diags = rebuildDiagnosticsFull(ws.files)
    val hasDirty = ws.files.any { it.isDirty }
    val banner =
      if (hasDirty && draft.savedAtEpochMs > 0L) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(draft.savedAtEpochMs))
        "Restored auto-saved draft from $timeStr"
      } else null
    _uiState.update {
      it.copy(
        isLocalScratchpad = true,
        projectMode = ProjectMode.CODE,
        sidebarSection = SidebarSection.EXPLORER,
        workspace = ws,
        openTabPaths = record.openTabPaths,
        activeFilePath = record.activeFilePath,
        tabNavHistory = record.tabNavHistory,
        tabNavIndex = record.tabNavIndex,
        editorFieldValues = record.fieldValues,
        editorRevision = it.editorRevision + 1L,
        diagnostics = diags,
        restoredDraftBanner = banner,
        lastAutoSavedEpochMs = draft.savedAtEpochMs.takeIf { ts -> ts > 0L },
        activeWorkspaceHasPendingConfig = record.hasPendingChanges,
        ramDbStats = ramDb.computeStats(draft.workspaceKey),
        ramWorkspaces = ramDb.listWorkspaces(),
      )
    }
    appendLog("INFO", "Restored '${ws.scriptName}' from project directory & auto-save")
    syncPeriodicAutoSaveLoop()
  }

  private fun regenerateWranglerFileInWorkspace(ws: WorkerWorkspace): WorkerWorkspace {
    val wranglerContent =
      repository.buildWranglerPreviewJson(
        scriptName = ws.scriptName,
        entryModule = ws.entryModulePath,
        compatDate = ws.compatibilityDate,
        compatFlags = ws.compatibilityFlags,
        bindings = ws.bindings,
        schedules = ws.schedules,
        workersDev = ws.workersDevEnabled,
      )
    val hasFile = ws.files.any { it.path == "wrangler.jsonc" }
    val updatedFiles =
      if (hasFile) {
        ws.files.map {
          if (it.path == "wrangler.jsonc") {
            it.copy(content = wranglerContent, originalContent = wranglerContent, isVirtualReadOnly = false)
          } else it
        }
      } else {
        ws.files +
          WorkerFile(
            path = "wrangler.jsonc",
            content = wranglerContent,
            originalContent = wranglerContent,
            contentType = "application/json",
            isEntryPoint = false,
            isVirtualReadOnly = false,
          )
      }
    return ws.copy(files = updatedFiles)
  }

  private fun syncWranglerFieldMap(
    current: Map<String, TextFieldValue>,
    ws: WorkerWorkspace?,
  ): Map<String, TextFieldValue> {
    val wf = ws?.files?.firstOrNull { it.path == "wrangler.jsonc" } ?: return current
    return current + ("wrangler.jsonc" to TextFieldValue(wf.content))
  }

  private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

  // Settings & Cloudflare Credentials
  fun updateSettings(transform: (EditorSettings) -> EditorSettings) {
    viewModelScope.launch { prefsStore.updateEditorSettings(transform) }
  }

  fun setEditorZoomPercent(zoomPercent: Int) {
    val clamped = zoomPercent.coerceIn(60, 220)
    if (_uiState.value.editorSettings.zoomPercent != clamped) {
      _uiState.update { state ->
        state.copy(editorSettings = state.editorSettings.copy(zoomPercent = clamped))
      }
    }
    zoomPersistJob?.cancel()
    zoomPersistJob =
      viewModelScope.launch {
        delay(220L)
        prefsStore.updateEditorSettings { it.copy(zoomPercent = clamped) }
      }
  }

  fun adjustEditorZoom(deltaPercent: Int) {
    setEditorZoomPercent(_uiState.value.editorSettings.zoomPercent + deltaPercent)
  }

  fun resetEditorZoom() {
    setEditorZoomPercent(100)
  }

  fun setCustomEditorBackground(uri: Uri) {
    viewModelScope.launch {
      val savedResult =
        withContext(Dispatchers.IO) {
          runCatching {
            val resolver = getApplication<Application>().contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            val rawW = boundsOpts.outWidth.coerceAtLeast(1)
            val rawH = boundsOpts.outHeight.coerceAtLeast(1)
            val maxDim = 2048
            var sampleSize = 1
            while ((rawW / sampleSize) > maxDim || (rawH / sampleSize) > maxDim) {
              sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded =
              resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                ?: error("Unable to read selected image")

            val bgDir = File(getApplication<Application>().filesDir, "editor_backgrounds").apply { mkdirs() }
            bgDir.listFiles()?.forEach { runCatching { it.delete() } }
            val outFile = File(bgDir, "editor_bg_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
              decoded.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            decoded.recycle()
            outFile.absolutePath
          }
        }
      savedResult
        .onSuccess { path ->
          prefsStore.updateEditorSettings {
            it.copy(
              editorBgPreset = "CUSTOM",
              editorBgImagePath = path,
              editorBgOffsetX = 0f,
              editorBgOffsetY = 0f,
            )
          }
          _uiState.update { it.copy(statusMessage = "Custom editor background updated") }
        }
        .onFailure { err ->
          _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Failed to load background image") }
        }
    }
  }

  fun clearCustomEditorBackground() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        val bgDir = File(getApplication<Application>().filesDir, "editor_backgrounds")
        bgDir.listFiles()?.forEach { runCatching { it.delete() } }
      }
      prefsStore.updateEditorSettings {
        it.copy(
          editorBgPreset = "NONE",
          editorBgImagePath = "",
          editorBgOffsetX = 0f,
          editorBgOffsetY = 0f,
        )
      }
      _uiState.update { it.copy(statusMessage = "Editor background reset to solid") }
    }
  }

  fun clearCloudflareCredentialTestMessage() {
    _uiState.update { it.copy(cfCredentialTestMessage = null, isCfCredentialTestError = false) }
  }

  fun verifyCloudflareCredentials() {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isTestingCfCredentials = true,
          cfCredentialTestMessage = null,
          isCfCredentialTestError = false,
        )
      }
      repository
        .verifyActiveSession()
        .onSuccess { accounts ->
          val currentId = _uiState.value.selectedAccount?.id
          val chosen = accounts.firstOrNull { it.id == currentId } ?: accounts.firstOrNull()
          _uiState.update {
            it.copy(
              isTestingCfCredentials = false,
              accounts = accounts,
              selectedAccount = chosen,
              cfCredentialTestMessage = "Verified: ${accounts.size} Cloudflare account(s) ready for deployment",
              isCfCredentialTestError = false,
            )
          }
          appendLog("INFO", "Verified active Cloudflare credentials (${accounts.size} account(s))")
        }
        .onFailure { err ->
          val msg = err.localizedMessage ?: "Credential verification failed"
          _uiState.update {
            it.copy(
              isTestingCfCredentials = false,
              cfCredentialTestMessage = msg,
              isCfCredentialTestError = true,
            )
          }
          appendLog("ERROR", msg)
        }
    }
  }

  fun updateCloudflareApiToken(token: String) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isTestingCfCredentials = true,
          cfCredentialTestMessage = null,
          isCfCredentialTestError = false,
        )
      }
      repository
        .updateApiTokenCredentials(token)
        .onSuccess { accounts ->
          val creds = prefsStore.getCurrentCredentials()
          val currentId = _uiState.value.selectedAccount?.id
          val chosen = accounts.firstOrNull { it.id == currentId } ?: accounts.firstOrNull()
          _uiState.update {
            it.copy(
              credentials = creds,
              isLocalScratchpad = false,
              isGitHubIdeOnlyMode = false,
              accounts = accounts,
              selectedAccount = chosen,
              isTestingCfCredentials = false,
              cfCredentialTestMessage = "Saved API Token (${accounts.size} account(s) linked)",
              isCfCredentialTestError = false,
            )
          }
          appendLog("INFO", "Updated Cloudflare API Token from Settings")
          chosen?.let { refreshWorkersInternal(it.id) }
        }
        .onFailure { err ->
          val msg = err.localizedMessage ?: "Failed to verify Cloudflare API Token"
          _uiState.update {
            it.copy(
              isTestingCfCredentials = false,
              cfCredentialTestMessage = msg,
              isCfCredentialTestError = true,
            )
          }
          appendLog("ERROR", msg)
        }
    }
  }

  fun updateCloudflareGlobalKey(email: String, apiKey: String) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isTestingCfCredentials = true,
          cfCredentialTestMessage = null,
          isCfCredentialTestError = false,
        )
      }
      repository
        .updateGlobalKeyCredentials(email, apiKey)
        .onSuccess { accounts ->
          val creds = prefsStore.getCurrentCredentials()
          val currentId = _uiState.value.selectedAccount?.id
          val chosen = accounts.firstOrNull { it.id == currentId } ?: accounts.firstOrNull()
          _uiState.update {
            it.copy(
              credentials = creds,
              isLocalScratchpad = false,
              isGitHubIdeOnlyMode = false,
              accounts = accounts,
              selectedAccount = chosen,
              isTestingCfCredentials = false,
              cfCredentialTestMessage = "Saved Global API Key (${accounts.size} account(s) linked)",
              isCfCredentialTestError = false,
            )
          }
          appendLog("INFO", "Updated Cloudflare Global API Key from Settings")
          chosen?.let { refreshWorkersInternal(it.id) }
        }
        .onFailure { err ->
          val msg = err.localizedMessage ?: "Failed to verify Global API Key"
          _uiState.update {
            it.copy(
              isTestingCfCredentials = false,
              cfCredentialTestMessage = msg,
              isCfCredentialTestError = true,
            )
          }
          appendLog("ERROR", msg)
        }
    }
  }

  fun setTopBarHidden(hidden: Boolean) {
    _uiState.update { it.copy(isTopBarHidden = hidden) }
  }

  fun toggleTopBarHidden() {
    _uiState.update { it.copy(isTopBarHidden = !it.isTopBarHidden) }
  }

  fun setEditorTopChromeHidden(hidden: Boolean) {
    _uiState.update { it.copy(isEditorTopChromeHidden = hidden) }
  }

  fun toggleEditorTopChromeHidden() {
    _uiState.update { it.copy(isEditorTopChromeHidden = !it.isEditorTopChromeHidden) }
  }

  fun setBottomDockHidden(hidden: Boolean) {
    _uiState.update { it.copy(isBottomDockHidden = hidden) }
  }

  fun toggleBottomDockHidden() {
    _uiState.update { it.copy(isBottomDockHidden = !it.isBottomDockHidden) }
  }

  fun setEditorBottomBarsHidden(hidden: Boolean) {
    _uiState.update { it.copy(isEditorBottomBarsHidden = hidden) }
  }

  fun toggleEditorBottomBarsHidden() {
    _uiState.update { it.copy(isEditorBottomBarsHidden = !it.isEditorBottomBarsHidden) }
  }

  fun toggleAllUiChrome(hide: Boolean) {
    _uiState.update {
      it.copy(
        isTopBarHidden = hide,
        isEditorTopChromeHidden = hide,
        isEditorBottomBarsHidden = hide,
        isBottomDockHidden = hide,
      )
    }
  }

  fun toggleAllBottomMenus(hide: Boolean) {
    toggleAllUiChrome(hide)
  }

  private fun rebuildDiagnosticsFull(files: List<WorkerFile>): List<DiagnosticItem> {
    fileDiagnosticsCache.clear()
    val result = ArrayList<DiagnosticItem>()
    for (file in files) {
      val list = CodeAnalyzer.analyzeFile(file)
      fileDiagnosticsCache[file.path] = list
      result.addAll(list)
    }
    return result
  }

  private fun reanalyzeSingleFile(
    editedFile: WorkerFile,
    allFiles: List<WorkerFile>,
  ): List<DiagnosticItem> {
    fileDiagnosticsCache[editedFile.path] = CodeAnalyzer.analyzeFile(editedFile)
    val result = ArrayList<DiagnosticItem>()
    for (file in allFiles) {
      val cached = fileDiagnosticsCache[file.path] ?: CodeAnalyzer.analyzeFile(file).also {
        fileDiagnosticsCache[file.path] = it
      }
      result.addAll(cached)
    }
    return result
  }

  private fun persistWorkspaceToProjectDirAsync(
    workspaceKey: String,
    refreshSummary: Boolean = true,
  ) {
    val snapshot = ramDb.getWorkspace(workspaceKey) ?: return
    autoSaveIoScope.launch {
      projectDirManager.saveWorkspaceRecord(snapshot)
      if (refreshSummary) {
        val summary = projectDirManager.computeStorageSummary()
        _uiState.update { it.copy(projectStorageSummary = summary) }
      }
    }
  }

  private fun appendLog(level: String, message: String) {
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    _uiState.update { state ->
      val next = (state.consoleLogs + ConsoleLogEntry(ts, level, message)).takeLast(120)
      state.copy(consoleLogs = next)
    }
  }

  // --- IDE Core Engines (Completion, Symbols, Find in Files, Local History, Smart Rename, FileOp Undo) ---

  fun completeAtCursor(
    filePath: String,
    documentText: CharSequence,
    cursor: Int,
    language: com.example.data.remote.WorkerLanguage,
  ): List<EdgeCompletionItem> {
    val t0 = System.nanoTime()
    val items =
      completionEngine.complete(
        workspace = _uiState.value.workspace,
        filePath = filePath,
        documentText = documentText,
        cursor = cursor,
        language = language,
      )
    idePerfSampler.record("completion", (System.nanoTime() - t0) / 1_000_000L)
    return items
  }

  fun noteCompletionAccepted(item: EdgeCompletionItem) {
    ideCoreIndexExecutor.execute { completionEngine.noteAccepted(item) }
  }

  fun searchProjectSymbols(query: String): List<ProjectSymbolHit> {
    flushActiveEditorBuffer()
    val t0 = System.nanoTime()
    val state = _uiState.value
    val hits = WorkspaceSearchAndRefactor.searchSymbols(state.workspace, state.editorFieldValues, query)
    idePerfSampler.record("symbolSearch", (System.nanoTime() - t0) / 1_000_000L)
    return hits
  }

  fun findInWorkspaceFiles(
    query: String,
    options: ProjectSearchOptions,
  ): List<ProjectTextMatch> {
    flushActiveEditorBuffer()
    val t0 = System.nanoTime()
    val state = _uiState.value
    val matches = WorkspaceSearchAndRefactor.findInFiles(state.workspace, state.editorFieldValues, query, options)
    idePerfSampler.record("findInFiles", (System.nanoTime() - t0) / 1_000_000L)
    return matches
  }

  fun loadActiveFileHistory(): List<LocalHistorySnapshot> {
    flushActiveEditorBuffer()
    localHistoryStore.flush()
    val state = _uiState.value
    val path = state.activeFilePath ?: return emptyList()
    val key = activeWorkspaceKey(state) ?: "default"
    return localHistoryStore.snapshots(key, path)
  }

  fun readActiveFileHistorySnapshot(snapshotId: Long): String? {
    val state = _uiState.value
    val path = state.activeFilePath ?: return null
    val key = activeWorkspaceKey(state) ?: "default"
    return localHistoryStore.readSnapshot(key, path, snapshotId)
  }

  fun restoreActiveFileFromHistory(snapshotId: Long) {
    flushActiveEditorBuffer()
    val state = _uiState.value
    val file = state.activeFile ?: return
    if (file.isVirtualReadOnly) return
    val key = activeWorkspaceKey(state) ?: "default"
    val restored = localHistoryStore.readSnapshot(key, file.path, snapshotId) ?: return
    if (restored == file.content) return
    localHistoryStore.recordSnapshot(key, file.path, file.content, restored, label = "Restore")
    applyDirectTextValue(file.path, TextFieldValue(restored))
    _uiState.update { it.copy(statusMessage = "Restored ${file.fileName}") }
  }

  fun previewSymbolRename(oldName: String): RenamePreview {
    flushActiveEditorBuffer()
    val state = _uiState.value
    return WorkspaceSearchAndRefactor.previewRename(state.workspace, state.editorFieldValues, oldName)
  }

  fun applySmartRenameSymbol(oldName: String, newName: String) {
    flushActiveEditorBuffer()
    val state = _uiState.value
    val ws = state.workspace ?: return
    val t0 = System.nanoTime()
    val result = WorkspaceSearchAndRefactor.applyRename(ws.files, state.editorFieldValues, oldName, newName)
    idePerfSampler.record("rename", (System.nanoTime() - t0) / 1_000_000L)
    if (result.totalReplacements == 0 || result.updatedContents.isEmpty()) {
      _uiState.update { it.copy(statusMessage = "No matches for '$oldName'") }
      return
    }
    val ramKey = activeWorkspaceKey(state)
    val wsKey = ramKey ?: "default"

    val updatedFiles =
      ws.files.map { f ->
        val nextText = result.updatedContents[f.path]
        if (nextText != null) {
          localHistoryStore.recordSnapshot(wsKey, f.path, f.content, nextText, label = "Rename")
          f.copy(content = nextText)
        } else {
          f
        }
      }
    val nextWs = ws.copy(files = updatedFiles)
    val nextFields = state.editorFieldValues.toMutableMap()
    result.updatedContents.forEach { (path, content) ->
      val prevSel = state.editorFieldValues[path]?.selection?.start ?: 0
      nextFields[path] = TextFieldValue(content, TextRange(prevSel.coerceIn(0, content.length)))
    }
    val diags = rebuildDiagnosticsFull(updatedFiles)
    if (ramKey != null) {
      ramDb.updateWorkspace(ramKey) { rec ->
        rec.copy(workingWorkspace = nextWs, fieldValues = nextFields)
      }
      persistWorkspaceToProjectDirAsync(ramKey)
    }
    _uiState.update { cur ->
      cur.copy(
        workspace = nextWs,
        editorFieldValues = nextFields,
        editorRevision = cur.editorRevision + 1L,
        diagnostics = diags,
        ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else cur.ramDbStats,
        statusMessage = "Renamed $oldName → $newName (${result.totalReplacements} in ${result.updatedContents.size}f)",
      )
    }
    workspaceEventBus.publish(WorkspaceEvent.FilesRefactored(wsKey, result.updatedContents.keys.toList()))
    scheduleDraftSave()
  }

  fun undoLastFileOperation() {
    flushActiveEditorBuffer()
    val state = _uiState.value
    val ws = state.workspace ?: return
    val ramKey = activeWorkspaceKey(state)
    val wsKey = ramKey ?: "default"
    val entry = fileOpJournal.takeLatestForWorkspace(wsKey) ?: return
    val nextLabel = fileOpJournal.latestForWorkspace(wsKey)?.op?.label

    when (val op = entry.op) {
      is WorkspaceFileOpJournal.FileOp.Deleted -> {
        if (ws.files.any { it.path == op.file.path }) {
          _uiState.update { it.copy(lastUndoableFileOpLabel = nextLabel) }
          return
        }
        val nextEntry = if (op.wasEntryPoint) op.file.path else ws.entryModulePath
        val restoredFiles =
          ProjectDirectoryManager.organizeWorkspaceFiles(
            (ws.files.filterNot { it.isVirtualReadOnly } + op.file).map { it.copy(isEntryPoint = it.path == nextEntry) } +
              ws.files.filter { it.isVirtualReadOnly },
          )
        val nextWs = ws.copy(files = restoredFiles, entryModulePath = nextEntry, hasModuleSetChanged = true)
        val nextTabs =
          if (op.file.path in state.openTabPaths) state.openTabPaths
          else state.openTabPaths + op.file.path
        val nextFields = state.editorFieldValues + (op.file.path to TextFieldValue(op.file.content))
        val diags = rebuildDiagnosticsFull(restoredFiles)
        if (ramKey != null) {
          ramDb.updateWorkspace(ramKey) { rec ->
            rec.copy(
              workingWorkspace = nextWs,
              openTabPaths = nextTabs,
              activeFilePath = op.file.path,
              fieldValues = nextFields,
            )
          }
          persistWorkspaceToProjectDirAsync(ramKey)
        }
        _uiState.update { cur ->
          cur.copy(
            workspace = nextWs,
            openTabPaths = nextTabs,
            activeFilePath = op.file.path,
            editorFieldValues = nextFields,
            editorRevision = cur.editorRevision + 1L,
            diagnostics = diags,
            lastUndoableFileOpLabel = nextLabel,
            ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else cur.ramDbStats,
            statusMessage = "Restored ${op.file.fileName}",
          )
        }
        workspaceEventBus.publish(WorkspaceEvent.FileCreated(wsKey, op.file.path))
        scheduleDraftSave()
      }

      is WorkspaceFileOpJournal.FileOp.Renamed -> {
        val target = ws.files.firstOrNull { it.path == op.newPath }
        if (target == null || ws.files.any { it.path == op.oldPath }) {
          _uiState.update { it.copy(lastUndoableFileOpLabel = nextLabel) }
          return
        }
        val restoredFiles =
          ProjectDirectoryManager.organizeWorkspaceFiles(
            ws.files.map { f ->
              if (f.path == op.newPath) {
                f.copy(path = op.oldPath, contentType = MultipartWorkerParser.inferModuleMimeType(op.oldPath))
              } else f
            },
          )
        val nextEntry = if (ws.entryModulePath == op.newPath) op.oldPath else ws.entryModulePath
        val nextTabs = state.openTabPaths.map { if (it == op.newPath) op.oldPath else it }
        val nextActive = if (state.activeFilePath == op.newPath) op.oldPath else state.activeFilePath
        val prevField = state.editorFieldValues[op.newPath] ?: TextFieldValue(target.content)
        val nextFields = (state.editorFieldValues - op.newPath) + (op.oldPath to prevField)
        val nextWs = ws.copy(files = restoredFiles, entryModulePath = nextEntry, hasModuleSetChanged = true)
        val diags = rebuildDiagnosticsFull(restoredFiles)
        if (ramKey != null) {
          ramDb.updateWorkspace(ramKey) { rec ->
            rec.copy(
              workingWorkspace = nextWs,
              openTabPaths = nextTabs,
              activeFilePath = nextActive,
              fieldValues = nextFields,
            )
          }
          persistWorkspaceToProjectDirAsync(ramKey)
        }
        _uiState.update { cur ->
          cur.copy(
            workspace = nextWs,
            openTabPaths = nextTabs,
            activeFilePath = nextActive,
            editorFieldValues = nextFields,
            editorRevision = cur.editorRevision + 1L,
            diagnostics = diags,
            lastUndoableFileOpLabel = nextLabel,
            ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else cur.ramDbStats,
            statusMessage = "Restored ${op.oldPath}",
          )
        }
        workspaceEventBus.publish(WorkspaceEvent.FileRenamed(wsKey, op.newPath, op.oldPath))
        scheduleDraftSave()
      }

      is WorkspaceFileOpJournal.FileOp.Created -> {
        val editableRemaining = ws.files.filter { !it.isVirtualReadOnly && it.path != op.path }
        if (editableRemaining.isEmpty() || ws.files.none { it.path == op.path }) {
          _uiState.update { it.copy(lastUndoableFileOpLabel = nextLabel) }
          return
        }
        val nextEntry =
          if (ws.entryModulePath == op.path) editableRemaining.first().path else ws.entryModulePath
        val updatedFiles =
          editableRemaining.map { it.copy(isEntryPoint = it.path == nextEntry) } +
            ws.files.filter { it.isVirtualReadOnly }
        val nextTabs = state.openTabPaths.filterNot { it == op.path }
        val nextActive =
          if (state.activeFilePath == op.path) nextTabs.lastOrNull() ?: nextEntry else state.activeFilePath
        val nextFields = state.editorFieldValues - op.path
        val nextWs = ws.copy(files = updatedFiles, entryModulePath = nextEntry, hasModuleSetChanged = true)
        val diags = rebuildDiagnosticsFull(updatedFiles)
        if (ramKey != null) {
          ramDb.updateWorkspace(ramKey) { rec ->
            rec.copy(
              workingWorkspace = nextWs,
              openTabPaths = nextTabs,
              activeFilePath = nextActive,
              fieldValues = nextFields,
            )
          }
          persistWorkspaceToProjectDirAsync(ramKey)
        }
        _uiState.update { cur ->
          cur.copy(
            workspace = nextWs,
            openTabPaths = nextTabs,
            activeFilePath = nextActive,
            editorFieldValues = nextFields,
            editorRevision = cur.editorRevision + 1L,
            diagnostics = diags,
            lastUndoableFileOpLabel = nextLabel,
            ramDbStats = if (ramKey != null) ramDb.computeStats(ramKey) else cur.ramDbStats,
            statusMessage = "Undid create ${op.path.substringAfterLast('/')}",
          )
        }
        workspaceEventBus.publish(WorkspaceEvent.FileDeleted(wsKey, op.path))
        scheduleDraftSave()
      }
    }
  }
}
