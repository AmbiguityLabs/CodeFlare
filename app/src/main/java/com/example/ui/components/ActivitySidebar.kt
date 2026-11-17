package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.remote.CfAccount
import com.example.data.remote.CfD1Database
import com.example.data.remote.CfKvNamespace
import com.example.data.remote.CfPagesProject
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.CfWorkerScript
import com.example.data.remote.GitHubIssue
import com.example.data.remote.GitHubRepo
import com.example.data.remote.WorkerFile
import com.example.data.security.EditorSettings
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BadgeJs
import com.example.ui.theme.BadgeJson
import com.example.ui.theme.BadgeTs
import com.example.ui.theme.BadgeWrangler
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.FolderGreen
import com.example.ui.theme.FolderYellow
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.viewmodel.IdeUiState
import com.example.ui.viewmodel.SearchMatchItem
import com.example.ui.viewmodel.SidebarSection

private sealed class FileTreeEntry {
  abstract val id: String
  abstract val depth: Int
  abstract val label: String

  data class FolderNode(
    override val id: String,
    override val depth: Int,
    override val label: String,
    val folderPath: String,
    val isRoot: Boolean = false,
  ) : FileTreeEntry()

  data class FileNode(
    override val id: String,
    override val depth: Int,
    override val label: String,
    val file: WorkerFile,
  ) : FileTreeEntry()
}

private data class WorkerSnippet(
  val title: String,
  val tag: String,
  val code: String,
)

private val workerSnippets =
  listOf(
    WorkerSnippet(
      title = "JSON Response Helper",
      tag = "HTTP",
      code =
        """function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: { "content-type": "application/json;charset=UTF-8" }
  });
}
""",
    ),
    WorkerSnippet(
      title = "KV Read & Write",
      tag = "KV",
      code =
        """const cached = await env.MY_KV.get("session:key", { type: "json" });
if (!cached) {
  await env.MY_KV.put("session:key", JSON.stringify({ ok: true }), { expirationTtl: 300 });
}
""",
    ),
    WorkerSnippet(
      title = "D1 SQL Query",
      tag = "D1",
      code =
        """const { results } = await env.DB.prepare("SELECT * FROM items LIMIT 20").all();
return Response.json({ results });
""",
    ),
    WorkerSnippet(
      title = "Scheduled Cron Handler",
      tag = "CRON",
      code =
        """async scheduled(controller, env, ctx) {
  ctx.waitUntil(
    console.log("Cron fired at", new Date(controller.scheduledTime).toISOString())
  );
},
""",
    ),
    WorkerSnippet(
      title = "CORS Preflight Guard",
      tag = "CORS",
      code =
        """if (request.method === "OPTIONS") {
  return new Response(null, {
    status: 204,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization"
    }
  });
}
""",
    ),
  )

@Composable
fun ActivitySidebar(
  uiState: IdeUiState,
  onSelectSection: (SidebarSection) -> Unit,
  onSelectAccount: (CfAccount) -> Unit,
  onRefreshWorkers: () -> Unit,
  onOpenWorker: (CfWorkerScript) -> Unit,
  onShowNewWorkerDialog: () -> Unit,
  onDeleteWorker: (String) -> Unit,
  onOpenFile: (String) -> Unit,
  onOpenWorkerPreview: () -> Unit,
  onShowNewFileDialog: (String) -> Unit,
  onShowNewDirectoryDialog: (String) -> Unit,
  onUploadSingleFile: () -> Unit,
  onUploadBatchFiles: () -> Unit,
  onExportActiveFile: () -> Unit,
  onDeleteModuleFile: (String) -> Unit,
  onRequestRenameModuleFile: (String) -> Unit,
  onSetEntryModule: (String) -> Unit,
  onDeployActiveWorker: () -> Unit,
  onReimportActiveWorker: () -> Unit,
  onRevertWorkspaceToImported: () -> Unit,
  onExportWorkspaceBundle: () -> Unit,
  onImportWorkspaceBundle: () -> Unit,
  onInsertSnippet: (String) -> Unit,
  onJumpToLine: (String, Int) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onReplaceInAllFiles: (String, String) -> Unit,
  onRevertFile: (String) -> Unit,
  // Workers Infra callbacks
  onRefreshInfra: () -> Unit,
  onToggleWorkersDev: (Boolean) -> Unit,
  onUpdateCompat: (String, Boolean) -> Unit,
  onAddCronSchedule: (String) -> Unit,
  onRemoveCronSchedule: (String) -> Unit,
  onAttachZoneRoute: (String, String) -> Unit,
  onDeleteZoneRoute: (String, String) -> Unit,
  onAttachWorkerDomain: (String, String) -> Unit,
  onRemoveWorkerDomain: (String) -> Unit,
  onRollbackVersion: (String) -> Unit,
  onRefreshWorkerVersions: () -> Unit,
  onDeployGradualSplit: (String, Int, String, Int, String) -> Unit,
  onAddEnvVar: (String, String) -> Unit,
  onAddSecret: (String, String) -> Unit,
  onBindResource: (CfWorkerBinding) -> Unit,
  onDeleteBinding: (CfWorkerBinding) -> Unit,
  onCreateKvNamespace: (String) -> Unit,
  onDeleteKvNamespace: (String) -> Unit,
  onSelectKvNamespace: (CfKvNamespace) -> Unit,
  onInspectKvKey: (String) -> Unit,
  onPutKvEntry: (String, String, Long?) -> Unit,
  onBulkImportKv: (String) -> Unit,
  onExportKvJson: () -> String,
  onDeleteKvKey: (String) -> Unit,
  onCreateD1Database: (String) -> Unit,
  onDeleteD1Database: (String) -> Unit,
  onSelectD1Database: (CfD1Database) -> Unit,
  onRunD1Sql: (String, String) -> Unit,
  onCreateR2Bucket: (String) -> Unit,
  onDeleteR2Bucket: (String) -> Unit,
  onCreateQueue: (String) -> Unit,
  onDeleteQueue: (String) -> Unit,
  onNotifyStatus: (String) -> Unit,
  // Pages callbacks
  onRefreshPages: () -> Unit,
  onSelectPagesProject: (CfPagesProject?) -> Unit,
  onCreatePagesProject: (String, String) -> Unit,
  onDeletePagesProject: (String) -> Unit,
  onTriggerPagesDeploy: (String) -> Unit,
  onRetryPagesDeploy: (String, String) -> Unit,
  onRollbackPagesDeploy: (String, String) -> Unit,
  onFetchPagesLogs: (String, String) -> Unit,
  onSavePagesEnvVar: (String, String, String, String, Boolean) -> Unit,
  onUpdatePagesBuildConfig: (String, String, String, String, String) -> Unit,
  onUpdatePagesBindings: (String, String, Map<String, String>, Map<String, String>, Map<String, String>) -> Unit,
  onCreatePagesSpecialFile: (String) -> Unit,
  onAddPagesDomain: (String, String) -> Unit,
  onDeletePagesDomain: (String, String) -> Unit,
  onPickPagesFolder: () -> Unit,
  onPickPagesZip: () -> Unit,
  onPickPagesFiles: () -> Unit,
  onStageWorkspaceForPages: () -> Unit,
  onTogglePagesZipStripRoot: (Boolean) -> Unit,
  onClearStagedPagesAssets: () -> Unit,
  onDeployDirectPages: (String, String?, String?) -> Unit,
  // Git & GitHub callbacks
  onStartGitHubOAuth: (String) -> Unit,
  onCancelGitHubOAuth: () -> Unit,
  onConnectGitHubToken: (String) -> Unit,
  onDisconnectGitHub: () -> Unit,
  onRefreshGitHubRepos: () -> Unit,
  onSelectGitHubRepo: (GitHubRepo?) -> Unit,
  onSelectGitHubBranch: (String) -> Unit,
  onCreateGitHubBranch: (String) -> Unit,
  onCloneRepository: (String, String?, Boolean) -> Unit,
  onCreateGitHubRepo: (String, String, Boolean) -> Unit,
  onLinkSelectedGitHubRepo: () -> Unit,
  onPullGitHubFiles: (List<String>) -> Unit,
  onSyncPullLinkedGitRepo: () -> Unit,
  onSetGitCommitMessage: (String) -> Unit,
  onToggleGitFileStaged: (String) -> Unit,
  onStageAllGitChanges: () -> Unit,
  onUnstageAllGitChanges: () -> Unit,
  onDiscardGitFileChange: (String) -> Unit,
  onCommitGitLocally: () -> Unit,
  onPushGitToGitHub: () -> Unit,
  onInspectGitHubCommit: (String) -> Unit,
  onInspectPullRequest: (Int?) -> Unit,
  onCreatePullRequest: (String, String, String, String) -> Unit,
  onMergePullRequest: (Int, String) -> Unit,
  onCreateGitHubIssue: (String, String) -> Unit,
  onToggleGitHubIssueState: (GitHubIssue) -> Unit,
  onCommentOnGitHubIssue: (Int, String) -> Unit,
  onDispatchGitHubWorkflow: (Long) -> Unit,
  onRerunGitHubWorkflowRun: (Long) -> Unit,
  onInspectWorkflowRunJobs: (Long) -> Unit,
  onSubmitPrReview: (Int, String, String) -> Unit,
  onGenerateCfActionWorkflow: () -> Unit,
  onRenameWorkerTarget: (String) -> Unit,
  // Settings & Navigation
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onSignOut: () -> Unit,
  onCloseDrawerIfCompact: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(palette.chromeBg),
  ) {
    // Top 5-Tab Segmented Pill Bar + Collapse "-" button (exact screenshot match)
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(
        color = palette.chromePill,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.weight(1f).height(44.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(3.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SidebarPillTab(
            selected = uiState.sidebarSection == SidebarSection.EXPLORER,
            icon = Icons.Outlined.Description,
            description = "Files Explorer",
            tag = "rail_explorer",
            onClick = { onSelectSection(SidebarSection.EXPLORER) },
            modifier = Modifier.weight(1f),
          )
          SidebarPillTab(
            selected = uiState.sidebarSection == SidebarSection.SEARCH,
            icon = Icons.Outlined.Search,
            description = "Search Workspace",
            tag = "rail_search",
            onClick = { onSelectSection(SidebarSection.SEARCH) },
            modifier = Modifier.weight(1f),
          )
          SidebarPillTab(
            selected = uiState.sidebarSection == SidebarSection.BINDINGS,
            icon = Icons.Outlined.Code,
            description = "Worker Infra, KV, D1, R2 & Routes",
            tag = "rail_bindings",
            onClick = { onSelectSection(SidebarSection.BINDINGS) },
            modifier = Modifier.weight(1f),
          )
          SidebarPillTab(
            selected = uiState.sidebarSection == SidebarSection.PAGES,
            icon = Icons.Outlined.PushPin,
            description = "Cloudflare Pages",
            tag = "rail_pages",
            onClick = { onSelectSection(SidebarSection.PAGES) },
            modifier = Modifier.weight(1f),
          )
          SidebarPillTab(
            selected = uiState.sidebarSection == SidebarSection.GITHUB,
            icon = Icons.Outlined.AccountTree,
            description = "GitHub Sync & Worker Diff",
            tag = "rail_github",
            onClick = { onSelectSection(SidebarSection.GITHUB) },
            modifier = Modifier.weight(1f),
          )
        }
      }

      IconButton(
        onClick = onCloseDrawerIfCompact,
        modifier = Modifier.size(38.dp).testTag("collapse_sidebar_button"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Remove,
          contentDescription = "Collapse sidebar",
          tint = palette.textSecondary,
        )
      }
    }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (uiState.sidebarSection) {
        SidebarSection.EXPLORER ->
          ExplorerPanel(
            uiState = uiState,
            onSelectAccount = onSelectAccount,
            onRefreshWorkers = onRefreshWorkers,
            onOpenWorker = {
              onOpenWorker(it)
              onCloseDrawerIfCompact()
            },
            onShowNewWorkerDialog = onShowNewWorkerDialog,
            onDeleteWorker = onDeleteWorker,
            onOpenFile = {
              onOpenFile(it)
              onCloseDrawerIfCompact()
            },
            onOpenWorkerPreview = {
              onOpenWorkerPreview()
              onCloseDrawerIfCompact()
            },
            onShowNewFileDialog = onShowNewFileDialog,
            onShowNewDirectoryDialog = onShowNewDirectoryDialog,
            onUploadSingleFile = onUploadSingleFile,
            onUploadBatchFiles = onUploadBatchFiles,
            onExportActiveFile = onExportActiveFile,
            onDeleteModuleFile = onDeleteModuleFile,
            onRequestRenameModuleFile = onRequestRenameModuleFile,
            onSetEntryModule = onSetEntryModule,
            onDeployActiveWorker = onDeployActiveWorker,
            onReimportActiveWorker = onReimportActiveWorker,
            onRevertWorkspaceToImported = onRevertWorkspaceToImported,
            onExportWorkspaceBundle = onExportWorkspaceBundle,
            onImportWorkspaceBundle = onImportWorkspaceBundle,
            onOpenSnippets = { onSelectSection(SidebarSection.SNIPPETS) },
            onOpenSettings = { onSelectSection(SidebarSection.SETTINGS) },
          )
        SidebarSection.SEARCH ->
          GlobalSearchPanel(
            uiState = uiState,
            onQueryChange = onSearchQueryChange,
            onReplaceAllInWorkspace = onReplaceInAllFiles,
            onJump = { path, line ->
              onJumpToLine(path, line)
              onCloseDrawerIfCompact()
            },
          )
        SidebarSection.BINDINGS ->
          CloudflareInfraPanel(
            uiState = uiState,
            onRefreshInfra = onRefreshInfra,
            onToggleWorkersDev = onToggleWorkersDev,
            onUpdateCompat = onUpdateCompat,
            onAddCronSchedule = onAddCronSchedule,
            onRemoveCronSchedule = onRemoveCronSchedule,
            onAttachZoneRoute = onAttachZoneRoute,
            onDeleteZoneRoute = onDeleteZoneRoute,
            onAttachWorkerDomain = onAttachWorkerDomain,
            onRemoveWorkerDomain = onRemoveWorkerDomain,
            onRollbackVersion = onRollbackVersion,
            onRefreshWorkerVersions = onRefreshWorkerVersions,
            onDeployGradualSplit = onDeployGradualSplit,
            onAddEnvVar = onAddEnvVar,
            onAddSecret = onAddSecret,
            onBindResource = onBindResource,
            onDeleteBinding = onDeleteBinding,
            onCreateKvNamespace = onCreateKvNamespace,
            onDeleteKvNamespace = onDeleteKvNamespace,
            onSelectKvNamespace = onSelectKvNamespace,
            onInspectKvKey = onInspectKvKey,
            onPutKvEntry = onPutKvEntry,
            onBulkImportKv = onBulkImportKv,
            onExportKvJson = onExportKvJson,
            onDeleteKvKey = onDeleteKvKey,
            onCreateD1Database = onCreateD1Database,
            onDeleteD1Database = onDeleteD1Database,
            onSelectD1Database = onSelectD1Database,
            onRunD1Sql = onRunD1Sql,
            onCreateR2Bucket = onCreateR2Bucket,
            onDeleteR2Bucket = onDeleteR2Bucket,
            onCreateQueue = onCreateQueue,
            onDeleteQueue = onDeleteQueue,
            onNotifyStatus = onNotifyStatus,
          )
        SidebarSection.PAGES ->
          CloudflarePagesPanel(
            uiState = uiState,
            onRefreshProjects = onRefreshPages,
            onSelectProject = onSelectPagesProject,
            onCreateProject = onCreatePagesProject,
            onDeleteProject = onDeletePagesProject,
            onTriggerDeploy = onTriggerPagesDeploy,
            onRetryDeploy = onRetryPagesDeploy,
            onRollbackDeploy = onRollbackPagesDeploy,
            onFetchLogs = onFetchPagesLogs,
            onSaveEnvVar = onSavePagesEnvVar,
            onUpdateBuildConfig = onUpdatePagesBuildConfig,
            onUpdateBindings = onUpdatePagesBindings,
            onCreatePagesSpecialFile = { kind ->
              onCreatePagesSpecialFile(kind)
              onCloseDrawerIfCompact()
            },
            onAddDomain = onAddPagesDomain,
            onDeleteDomain = onDeletePagesDomain,
            onPickPagesFolder = onPickPagesFolder,
            onPickPagesZip = onPickPagesZip,
            onPickPagesFiles = onPickPagesFiles,
            onStageWorkspaceForPages = onStageWorkspaceForPages,
            onTogglePagesZipStripRoot = onTogglePagesZipStripRoot,
            onClearStagedPagesAssets = onClearStagedPagesAssets,
            onDeployDirectPages = onDeployDirectPages,
          )
        SidebarSection.GITHUB ->
          GitHubAndDiffPanel(
            uiState = uiState,
            onStartGitHubOAuth = onStartGitHubOAuth,
            onCancelGitHubOAuth = onCancelGitHubOAuth,
            onConnectGitHubToken = onConnectGitHubToken,
            onDisconnectGitHub = onDisconnectGitHub,
            onRefreshRepos = onRefreshGitHubRepos,
            onSelectRepo = onSelectGitHubRepo,
            onSelectBranch = onSelectGitHubBranch,
            onCreateBranch = onCreateGitHubBranch,
            onCloneRepository = onCloneRepository,
            onCreateGitHubRepo = onCreateGitHubRepo,
            onLinkSelectedRepo = onLinkSelectedGitHubRepo,
            onPullFilesToWorker = onPullGitHubFiles,
            onSyncPullLinkedRepo = onSyncPullLinkedGitRepo,
            onSetCommitMessage = onSetGitCommitMessage,
            onToggleStageFile = onToggleGitFileStaged,
            onStageAll = onStageAllGitChanges,
            onUnstageAll = onUnstageAllGitChanges,
            onDiscardFileChange = onDiscardGitFileChange,
            onCommitLocally = onCommitGitLocally,
            onPushToGitHub = onPushGitToGitHub,
            onInspectCommit = onInspectGitHubCommit,
            onInspectPullRequest = onInspectPullRequest,
            onCreatePullRequest = onCreatePullRequest,
            onMergePullRequest = onMergePullRequest,
            onCreateIssue = onCreateGitHubIssue,
            onToggleIssueState = onToggleGitHubIssueState,
            onCommentOnIssue = onCommentOnGitHubIssue,
            onDispatchWorkflow = onDispatchGitHubWorkflow,
            onRerunWorkflowRun = onRerunGitHubWorkflowRun,
            onInspectWorkflowRunJobs = onInspectWorkflowRunJobs,
            onSubmitPrReview = onSubmitPrReview,
            onGenerateCfActionWorkflow = onGenerateCfActionWorkflow,
            onOpenFileInEditor = onOpenFile,
            onRenameWorkerTarget = onRenameWorkerTarget,
            onDeployActiveWorker = onDeployActiveWorker,
            onRevertFile = onRevertFile,
            onCloseDrawerIfCompact = onCloseDrawerIfCompact,
          )
        SidebarSection.SNIPPETS ->
          SnippetsPanel(
            hasActiveFile = uiState.activeFile?.isVirtualReadOnly == false,
            onInsertSnippet = { snippetCode ->
              onInsertSnippet(snippetCode)
              onCloseDrawerIfCompact()
            },
          )
        SidebarSection.SETTINGS ->
          SettingsPanel(
            uiState = uiState,
            onUpdateSettings = onUpdateSettings,
            onSignOut = onSignOut,
          )
      }
    }
  }
}

@Composable
private fun SidebarPillTab(
  selected: Boolean,
  icon: ImageVector,
  description: String,
  tag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  Box(
    modifier =
      modifier
        .fillMaxHeight()
        .clip(RoundedCornerShape(11.dp))
        .background(if (selected) palette.activePillBg else Color.Transparent)
        .clickable(onClick = onClick)
        .testTag(tag),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = description,
      tint = if (selected) palette.activePillFg else palette.textSecondary,
      modifier = Modifier.size(19.dp),
    )
  }
}

@Composable
private fun ExplorerPanel(
  uiState: IdeUiState,
  onSelectAccount: (CfAccount) -> Unit,
  onRefreshWorkers: () -> Unit,
  onOpenWorker: (CfWorkerScript) -> Unit,
  onShowNewWorkerDialog: () -> Unit,
  onDeleteWorker: (String) -> Unit,
  onOpenFile: (String) -> Unit,
  onOpenWorkerPreview: () -> Unit,
  onShowNewFileDialog: (String) -> Unit,
  onShowNewDirectoryDialog: (String) -> Unit,
  onUploadSingleFile: () -> Unit,
  onUploadBatchFiles: () -> Unit,
  onExportActiveFile: () -> Unit,
  onDeleteModuleFile: (String) -> Unit,
  onRequestRenameModuleFile: (String) -> Unit,
  onSetEntryModule: (String) -> Unit,
  onDeployActiveWorker: () -> Unit,
  onReimportActiveWorker: () -> Unit,
  onRevertWorkspaceToImported: () -> Unit,
  onExportWorkspaceBundle: () -> Unit,
  onImportWorkspaceBundle: () -> Unit,
  onOpenSnippets: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val palette = LocalIdePalette.current
  var projectMenuOpen by remember { mutableStateOf(false) }
  var uploadMenuOpen by remember { mutableStateOf(false) }
  var moreMenuOpen by remember { mutableStateOf(false) }
  var pendingDeleteWorker by remember { mutableStateOf<String?>(null) }
  var pendingDeleteModule by remember { mutableStateOf<String?>(null) }
  val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

  pendingDeleteWorker?.let { nameToDelete ->
    AlertDialog(
      onDismissRequest = { pendingDeleteWorker = null },
      title = { Text("Delete Worker?") },
      text = { Text("Remove '$nameToDelete' permanently from Cloudflare Workers?") },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteWorker(nameToDelete)
            pendingDeleteWorker = null
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete")
        }
      },
      dismissButton = { TextButton(onClick = { pendingDeleteWorker = null }) { Text("Cancel") } },
    )
  }

  pendingDeleteModule?.let { moduleToDelete ->
    AlertDialog(
      onDismissRequest = { pendingDeleteModule = null },
      title = { Text("Delete Script File?") },
      text = {
        Text("Remove '$moduleToDelete' from ${uiState.workspace?.scriptName ?: "this Worker"}? Tap Deploy to push this deletion live.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteModuleFile(moduleToDelete)
            pendingDeleteModule = null
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete Script")
        }
      },
      dismissButton = { TextButton(onClick = { pendingDeleteModule = null }) { Text("Cancel") } },
    )
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box {
        Surface(
          onClick = { projectMenuOpen = true },
          shape = RoundedCornerShape(12.dp),
          color = palette.chromePill,
          modifier = Modifier.testTag("project_dropdown_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text =
                when {
                  uiState.isLocalScratchpad -> "Scratchpad"
                  uiState.selectedAccount != null -> uiState.selectedAccount.name
                  else -> "Project"
                },
              style = MaterialTheme.typography.titleSmall,
              color = palette.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Switch Project", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
          }
        }
        DropdownMenu(expanded = projectMenuOpen, onDismissRequest = { projectMenuOpen = false }) {
          if (uiState.accounts.isNotEmpty()) {
            uiState.accounts.forEach { acc ->
              DropdownMenuItem(
                text = { Text("Account: ${acc.name}") },
                onClick = {
                  projectMenuOpen = false
                  onSelectAccount(acc)
                },
              )
            }
          }
          DropdownMenuItem(
            text = { Text("Open Deployed Worker Preview") },
            onClick = {
              projectMenuOpen = false
              onOpenWorkerPreview()
            },
          )
          DropdownMenuItem(
            text = { Text("+ New File in Workspace") },
            onClick = {
              projectMenuOpen = false
              onShowNewFileDialog("")
            },
          )
          DropdownMenuItem(
            text = { Text("+ New Cloudflare Worker") },
            onClick = {
              projectMenuOpen = false
              onShowNewWorkerDialog()
            },
          )
        }
      }

      Spacer(Modifier.weight(1f))

      Box {
        IconButton(
          onClick = { uploadMenuOpen = true },
          modifier = Modifier.size(36.dp).testTag("upload_files_menu_button"),
        ) {
          Icon(Icons.Outlined.UploadFile, contentDescription = "Upload or Export Files", tint = palette.activePillFg, modifier = Modifier.size(19.dp))
        }
        DropdownMenu(expanded = uploadMenuOpen, onDismissRequest = { uploadMenuOpen = false }) {
          if (uiState.workspace != null) {
            DropdownMenuItem(
              text = { Text("Upload Single File to Worker") },
              onClick = {
                uploadMenuOpen = false
                onUploadSingleFile()
              },
            )
            DropdownMenuItem(
              text = { Text("Batch Upload Multiple Files") },
              onClick = {
                uploadMenuOpen = false
                onUploadBatchFiles()
              },
            )
            uiState.activeFile?.let { active ->
              DropdownMenuItem(
                text = { Text("Export ${active.fileName}") },
                onClick = {
                  uploadMenuOpen = false
                  onExportActiveFile()
                },
              )
            }
          } else {
            DropdownMenuItem(
              text = { Text("Create New Worker from Files") },
              onClick = {
                uploadMenuOpen = false
                onShowNewWorkerDialog()
              },
            )
          }
        }
      }

      IconButton(
        onClick = { onShowNewFileDialog("") },
        modifier = Modifier.size(36.dp).testTag("add_module_file_button"),
      ) {
        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New File", tint = palette.textSecondary, modifier = Modifier.size(19.dp))
      }

      IconButton(
        onClick = onRefreshWorkers,
        modifier = Modifier.size(36.dp).testTag("refresh_workers_button"),
      ) {
        Icon(Icons.Outlined.Download, contentDescription = "Pull Workers", tint = palette.textSecondary, modifier = Modifier.size(19.dp))
      }

      Box {
        IconButton(
          onClick = { moreMenuOpen = true },
          modifier = Modifier.size(36.dp).testTag("explorer_more_button"),
        ) {
          Icon(Icons.Outlined.MoreHoriz, contentDescription = "More Explorer Actions", tint = palette.textSecondary, modifier = Modifier.size(19.dp))
        }
        DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
          DropdownMenuItem(
            text = { Text("New Worker (Template or Upload)") },
            onClick = {
              moreMenuOpen = false
              onShowNewWorkerDialog()
            },
          )
          if (uiState.workspace != null) {
            DropdownMenuItem(
              text = { Text("New Directory") },
              onClick = {
                moreMenuOpen = false
                onShowNewDirectoryDialog("")
              },
            )
          }
          DropdownMenuItem(
            text = { Text("Worker Code Snippets") },
            onClick = {
              moreMenuOpen = false
              onOpenSnippets()
            },
          )
          if (uiState.workspace != null && !uiState.isLocalScratchpad) {
            DropdownMenuItem(
              text = { Text("Delete '${uiState.workspace.scriptName}'") },
              onClick = {
                moreMenuOpen = false
                pendingDeleteWorker = uiState.workspace.scriptName
              },
            )
          }
          DropdownMenuItem(
            text = { Text("IDE Preferences") },
            onClick = {
              moreMenuOpen = false
              onOpenSettings()
            },
          )
        }
      }
    }

    Spacer(Modifier.height(4.dp))

    val treeRows =
      remember(uiState.workspace, expandedFolders.toMap()) {
        uiState.workspace?.let { ws ->
          buildVisibleTreeEntries(ws.scriptName, ws.files, ws.directories, expandedFolders)
        }.orEmpty()
      }
    val editableFileCount =
      remember(uiState.workspace) {
        uiState.workspace?.files?.count { !it.isVirtualReadOnly } ?: 0
      }

    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      item(key = "ram_db_status_card") {
        RamWorkspaceStatusCard(
          uiState = uiState,
          onDeployExport = onDeployActiveWorker,
          onReimport = onReimportActiveWorker,
          onRevertWorkspace = onRevertWorkspaceToImported,
          onExportBundle = onExportWorkspaceBundle,
          onImportBundle = onImportWorkspaceBundle,
        )
        Spacer(Modifier.height(6.dp))
      }

      if (uiState.workspace != null) {
        items(treeRows, key = { it.id }) { entry ->
          when (entry) {
            is FileTreeEntry.FolderNode -> {
              val isExpanded = expandedFolders[entry.id] ?: true
              TreeRowItem(
                depth = entry.depth,
                isExpanded = isExpanded,
                isFolder = true,
                isRootFolder = entry.isRoot,
                isSelected = false,
                canDeleteFile = false,
                label = entry.label,
                contextTitle = if (entry.folderPath.isBlank()) "New in ${uiState.workspace.scriptName}" else "New in ${entry.folderPath}",
                file = null,
                onClick = { expandedFolders[entry.id] = !isExpanded },
                onNewFileInFolder = { onShowNewFileDialog(entry.folderPath) },
                onNewDirInFolder = { onShowNewDirectoryDialog(entry.folderPath) },
                onOpenPreviewClick = onOpenWorkerPreview,
                onDeployClick = {},
                onRenameClick = {},
                onSetEntryClick = {},
                onExportClick = {},
                onDeleteFileClick = {},
              )
            }
            is FileTreeEntry.FileNode -> {
              val active = entry.file.path == uiState.activeFilePath
              val parentFolder =
                if (entry.file.path.contains('/')) entry.file.path.substringBeforeLast('/') else ""
              TreeRowItem(
                depth = entry.depth,
                isExpanded = false,
                isFolder = false,
                isRootFolder = false,
                isSelected = active,
                canDeleteFile = editableFileCount > 1 && !entry.file.isVirtualReadOnly,
                label = entry.label,
                contextTitle = entry.file.fileName,
                file = entry.file,
                onClick = { onOpenFile(entry.file.path) },
                onNewFileInFolder = { onShowNewFileDialog(parentFolder) },
                onNewDirInFolder = { onShowNewDirectoryDialog(parentFolder) },
                onOpenPreviewClick = onOpenWorkerPreview,
                onDeployClick = onDeployActiveWorker,
                onRenameClick = { onRequestRenameModuleFile(entry.file.path) },
                onSetEntryClick = { onSetEntryModule(entry.file.path) },
                onExportClick = {
                  onOpenFile(entry.file.path)
                  onExportActiveFile()
                },
                onDeleteFileClick = { pendingDeleteModule = entry.file.path },
              )
            }
          }
        }
      }

      if (!uiState.isLocalScratchpad) {
        item {
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "CLOUDFLARE WORKERS (${uiState.workers.size})",
              style = MaterialTheme.typography.labelSmall,
              color = palette.textMuted,
              modifier = Modifier.weight(1f),
            )
            IconButton(
              onClick = onShowNewWorkerDialog,
              modifier = Modifier.size(28.dp).testTag("new_worker_button"),
            ) {
              Icon(Icons.Outlined.Add, contentDescription = "New Worker", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
            }
          }
        }

        if (uiState.isLoadingWorkers) {
          item {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
          }
        } else {
          items(uiState.workers, key = { "cf_${it.id}" }) { script ->
            val isOpen = uiState.workspace?.scriptName == script.id
            Surface(
              onClick = { onOpenWorker(script) },
              shape = RoundedCornerShape(12.dp),
              color = if (isOpen) palette.chromePill else Color.Transparent,
              modifier = Modifier.fillMaxWidth().testTag("worker_item_${script.id}"),
            ) {
              Row(
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.FolderOpen,
                  contentDescription = null,
                  tint = if (isOpen) palette.activePillFg else FolderYellow,
                  modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = script.id,
                  style = MaterialTheme.typography.bodyMedium,
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { pendingDeleteWorker = script.id }, modifier = Modifier.size(30.dp)) {
                  Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${script.id}", tint = palette.textMuted, modifier = Modifier.size(15.dp))
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRowItem(
  depth: Int,
  isExpanded: Boolean,
  isFolder: Boolean,
  isRootFolder: Boolean,
  isSelected: Boolean,
  canDeleteFile: Boolean,
  label: String,
  contextTitle: String,
  file: WorkerFile?,
  onClick: () -> Unit,
  onNewFileInFolder: () -> Unit,
  onNewDirInFolder: () -> Unit,
  onOpenPreviewClick: () -> Unit,
  onDeployClick: () -> Unit,
  onRenameClick: () -> Unit,
  onSetEntryClick: () -> Unit,
  onExportClick: () -> Unit,
  onDeleteFileClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val rowBg = if (isSelected) palette.selectedRowBg else Color.Transparent
  var showContextPopup by remember { mutableStateOf(false) }

  Box {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(40.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(rowBg)
          .combinedClickable(
            onClick = onClick,
            onLongClick = { showContextPopup = true },
          )
          .padding(end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (depth > 0) {
        Box(modifier = Modifier.width((depth * 16).dp).fillMaxHeight()) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            for (level in 0 until depth) {
              val x = ((level * 16) + 8).dp.toPx()
              drawLine(
                color = palette.treeLine,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx(),
              )
            }
          }
        }
      } else {
        Spacer(Modifier.width(4.dp))
      }

      if (isFolder) {
        Icon(
          imageVector =
            if (isExpanded) Icons.Outlined.KeyboardArrowDown
            else Icons.Outlined.KeyboardArrowRight,
          contentDescription = null,
          tint = palette.textSecondary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box {
          Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (isRootFolder) palette.textPrimary else FolderYellow,
            modifier = Modifier.size(18.dp),
          )
          if (isRootFolder) {
            Box(
              modifier =
                Modifier
                  .size(7.dp)
                  .align(Alignment.BottomEnd)
                  .clip(CircleShape)
                  .background(FolderGreen),
            )
          }
        }
      } else {
        Spacer(Modifier.width(10.dp))
        FileTypeBadge(path = file?.path ?: label)
      }

      Spacer(Modifier.width(8.dp))

      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isSelected || isRootFolder) FontWeight.SemiBold else FontWeight.Normal,
        color = palette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )

      if (file?.isEntryPoint == true) {
        Surface(color = palette.activePillBg, shape = RoundedCornerShape(6.dp)) {
          Text(
            text = "MAIN",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = palette.activePillFg,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
          )
        }
        Spacer(Modifier.width(4.dp))
      }

      if (file?.isDirty == true) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(palette.activePillFg))
        Spacer(Modifier.width(4.dp))
      }

      if (isSelected && file != null && !file.isVirtualReadOnly) {
        IconButton(
          onClick = onDeployClick,
          modifier = Modifier.size(30.dp).testTag("tree_deploy_inline_button"),
        ) {
          Icon(Icons.Outlined.FileUpload, contentDescription = "Deploy Worker", tint = palette.textPrimary, modifier = Modifier.size(17.dp))
        }
      }

      IconButton(
        onClick = { showContextPopup = true },
        modifier = Modifier.size(30.dp).testTag("tree_context_${file?.path ?: label}"),
      ) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "Context menu for $label", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
      }
    }

    // Screenshot-matched Floating Olive-Charcoal Context Popup Menu ("New in <path>" -> "+ File", "Directory", ...)
    if (showContextPopup) {
      Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = { showContextPopup = false },
        properties = PopupProperties(focusable = true),
      ) {
        Surface(
          color = palette.chromePill,
          shape = RoundedCornerShape(18.dp),
          shadowElevation = 10.dp,
          border = BorderStroke(1.dp, palette.border.copy(alpha = 0.55f)),
          modifier = Modifier.widthIn(min = 210.dp, max = 260.dp),
        ) {
          Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
              text = contextTitle,
              style = MaterialTheme.typography.labelMedium,
              color = palette.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (isFolder) {
              FloatingMenuRow(
                icon = Icons.Outlined.Add,
                label = "File",
                onClick = {
                  showContextPopup = false
                  onNewFileInFolder()
                },
              )
              FloatingMenuRow(
                icon = Icons.Outlined.Folder,
                label = "Directory",
                onClick = {
                  showContextPopup = false
                  onNewDirInFolder()
                },
              )
            } else if (file != null) {
              FloatingMenuRow(
                icon = Icons.Outlined.Visibility,
                label = "Preview Deployed Worker",
                tint = palette.activePillFg,
                onClick = {
                  showContextPopup = false
                  onOpenPreviewClick()
                },
              )
              FloatingMenuRow(
                icon = Icons.Outlined.Description,
                label = "Open in Tab",
                onClick = {
                  showContextPopup = false
                  onClick()
                },
              )
              FloatingMenuRow(
                icon = Icons.Outlined.Add,
                label = "New Sibling File",
                onClick = {
                  showContextPopup = false
                  onNewFileInFolder()
                },
              )
              if (!file.isVirtualReadOnly && !file.isEntryPoint) {
                FloatingMenuRow(
                  icon = Icons.Outlined.StarOutline,
                  label = "Set Entry Point (main)",
                  onClick = {
                    showContextPopup = false
                    onSetEntryClick()
                  },
                )
              }
              if (!file.isVirtualReadOnly) {
                FloatingMenuRow(
                  icon = Icons.Outlined.DriveFileRenameOutline,
                  label = "Rename / Move",
                  onClick = {
                    showContextPopup = false
                    onRenameClick()
                  },
                )
                FloatingMenuRow(
                  icon = Icons.Outlined.Download,
                  label = "Export File",
                  onClick = {
                    showContextPopup = false
                    onExportClick()
                  },
                )
              }
              if (canDeleteFile) {
                FloatingMenuRow(
                  icon = Icons.Outlined.DeleteOutline,
                  label = "Delete Script",
                  tint = MaterialTheme.colorScheme.error,
                  onClick = {
                    showContextPopup = false
                    onDeleteFileClick()
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FloatingMenuRow(
  icon: ImageVector,
  label: String,
  tint: Color? = null,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val fg = tint ?: palette.textPrimary
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tint ?: palette.textSecondary, modifier = Modifier.size(19.dp))
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = fg)
  }
}

@Composable
fun FileTypeBadge(path: String) {
  val (code, bgColor) =
    when {
      path.equals("wrangler.jsonc", ignoreCase = true) -> "W" to BadgeWrangler
      path.endsWith(".ts", ignoreCase = true) || path.endsWith(".mts", ignoreCase = true) -> "TS" to BadgeTs
      path.endsWith(".json", ignoreCase = true) || path.endsWith(".jsonc", ignoreCase = true) -> "{}" to BadgeJson
      path.endsWith(".html", ignoreCase = true) || path.endsWith(".htm", ignoreCase = true) -> "<>" to BadgeWrangler
      path.endsWith(".css", ignoreCase = true) -> "#" to BadgeTs
      path.endsWith(".sh", ignoreCase = true) -> "SH" to BadgeJson
      path.endsWith(".sql", ignoreCase = true) -> "DB" to BadgeJson
      else -> "JS" to BadgeJs
    }
  Box(
    modifier =
      Modifier
        .size(20.dp)
        .clip(RoundedCornerShape(5.dp))
        .background(bgColor.copy(alpha = 0.22f)),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = code, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = bgColor)
  }
}

private fun buildVisibleTreeEntries(
  rootName: String,
  files: List<WorkerFile>,
  extraDirectories: Set<String>,
  expandedMap: Map<String, Boolean>,
): List<FileTreeEntry> {
  val result = mutableListOf<FileTreeEntry>()
  val rootId = "folder_root_$rootName"
  result.add(FileTreeEntry.FolderNode(id = rootId, depth = 0, label = rootName, folderPath = "", isRoot = true))
  if (expandedMap[rootId] == false) return result

  val emittedFolders = mutableSetOf<String>()
  val sortedFiles = files.sortedWith(compareBy<WorkerFile> { !it.path.contains('/') }.thenBy { it.path })

  for (file in sortedFiles) {
    val parts = file.path.split('/')
    var parentCollapsed = false
    for (i in 1 until parts.size) {
      val folderPath = parts.subList(0, i).joinToString("/")
      val folderId = "folder_$folderPath"
      if (!parentCollapsed && emittedFolders.add(folderPath)) {
        result.add(
          FileTreeEntry.FolderNode(
            id = folderId,
            depth = i,
            label = parts[i - 1],
            folderPath = folderPath,
          ),
        )
      }
      if (expandedMap[folderId] == false) parentCollapsed = true
    }
    if (!parentCollapsed) {
      result.add(
        FileTreeEntry.FileNode(
          id = "file_${file.path}",
          depth = parts.size,
          label = parts.last(),
          file = file,
        ),
      )
    }
  }

  for (dir in extraDirectories.sorted()) {
    val parts = dir.split('/')
    var parentCollapsed = false
    for (i in 1..parts.size) {
      val folderPath = parts.subList(0, i).joinToString("/")
      val folderId = "folder_$folderPath"
      if (!parentCollapsed && emittedFolders.add(folderPath)) {
        result.add(
          FileTreeEntry.FolderNode(
            id = folderId,
            depth = i,
            label = parts[i - 1],
            folderPath = folderPath,
          ),
        )
      }
      if (expandedMap[folderId] == false) parentCollapsed = true
    }
  }
  return result
}

@Composable
fun SnippetsPanel(
  hasActiveFile: Boolean,
  onInsertSnippet: (String) -> Unit,
) {
  val palette = LocalIdePalette.current
  Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("WORKERS EDGE SNIPPETS", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      items(workerSnippets) { snippet ->
        Surface(
          onClick = { if (hasActiveFile) onInsertSnippet(snippet.code) },
          shape = RoundedCornerShape(14.dp),
          color = palette.chromePill,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = palette.activePillBg, shape = RoundedCornerShape(8.dp)) {
              Text(snippet.tag, style = MaterialTheme.typography.labelSmall, color = palette.activePillFg, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(snippet.title, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
              Text(
                text = if (hasActiveFile) "Tap to insert at cursor" else "Open an editable JS file",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun GlobalSearchPanel(
  uiState: IdeUiState,
  onQueryChange: (String) -> Unit,
  onReplaceAllInWorkspace: (String, String) -> Unit,
  onJump: (String, Int) -> Unit,
) {
  val palette = LocalIdePalette.current
  var replaceQuery by remember { mutableStateOf("") }
  val matches =
    remember(uiState.searchQuery, uiState.workspace) {
      val q = uiState.searchQuery.trim()
      val files = uiState.workspace?.files.orEmpty()
      if (q.isEmpty() || files.isEmpty()) emptyList()
      else {
        files.flatMap { f ->
          f.content.lines().mapIndexedNotNull { idx, line ->
            if (line.contains(q, ignoreCase = true)) SearchMatchItem(f.path, idx + 1, line.trim()) else null
          }
        }.take(60)
      }
    }

  Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text("SEARCH & REPLACE WORKSPACE", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
      if (uiState.searchQuery.isNotBlank()) {
        Surface(color = palette.chromePill, shape = RoundedCornerShape(6.dp)) {
          Text(
            text = "${matches.size}${if (matches.size >= 60) "+" else ""} match(es)",
            style = MaterialTheme.typography.labelSmall,
            color = palette.activePillFg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
      }
    }
    OutlinedTextField(
      value = uiState.searchQuery,
      onValueChange = onQueryChange,
      placeholder = { Text("Search across all project files…") },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().testTag("global_search_input"),
    )
    if (uiState.workspace != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OutlinedTextField(
          value = replaceQuery,
          onValueChange = { replaceQuery = it },
          placeholder = { Text("Replace with…") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).testTag("global_replace_input"),
        )
        FilledTonalButton(
          onClick = { onReplaceAllInWorkspace(uiState.searchQuery, replaceQuery) },
          enabled = uiState.searchQuery.isNotBlank() && matches.isNotEmpty(),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(48.dp).testTag("global_replace_all_button"),
        ) {
          Text("Replace All", fontSize = 11.sp)
        }
      }
    }
    if (uiState.workspace == null) {
      Text("Open a Worker first to search its files.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(matches) { match ->
          Surface(
            onClick = { onJump(match.filePath, match.line) },
            shape = RoundedCornerShape(12.dp),
            color = palette.chromePill,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text("${match.filePath}:${match.line}", style = MaterialTheme.typography.labelSmall, color = palette.activePillFg)
              Text(match.preview, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    }
  }
}

@Composable
fun SettingsPanel(
  uiState: IdeUiState,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onSignOut: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val settings = uiState.editorSettings
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("IDE PREFERENCES", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)

    Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text =
            when {
              uiState.credentials != null -> "Auth Mode: ${uiState.credentials.method.name}"
              uiState.isGitHubIdeOnlyMode -> "Mode: GitHub IDE (@${uiState.gitHubCreds?.login ?: "connected"})"
              else -> "Mode: Local Scratchpad (Offline)"
            },
          style = MaterialTheme.typography.labelMedium,
          color = palette.textPrimary,
        )
        if (!uiState.credentials?.email.isNullOrBlank()) {
          Text(uiState.credentials?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        }
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Dark Olive IDE Theme", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
      Switch(checked = settings.darkTheme, onCheckedChange = { dark -> onUpdateSettings { it.copy(darkTheme = dark) } })
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Text("System Dynamic Color", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
      Switch(checked = settings.dynamicColor, onCheckedChange = { dyn -> onUpdateSettings { it.copy(dynamicColor = dyn) } })
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Word Wrap", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
      Switch(checked = settings.wordWrap, onCheckedChange = { wrap -> onUpdateSettings { it.copy(wordWrap = wrap) } })
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Text("Line Numbers", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
      Switch(checked = settings.showLineNumbers, onCheckedChange = { ln -> onUpdateSettings { it.copy(showLineNumbers = ln) } })
    }

    Column {
      Text("Code Font Size (${settings.fontSizeSp} sp)", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
      Slider(
        value = settings.fontSizeSp.toFloat(),
        onValueChange = { v -> onUpdateSettings { it.copy(fontSizeSp = v.toInt()) } },
        valueRange = 10f..18f,
        steps = 7,
      )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(2, 4).forEach { spaces ->
        FilledTonalButton(
          onClick = { onUpdateSettings { it.copy(tabSize = spaces) } },
          modifier = Modifier.weight(1f),
        ) {
          Text("$spaces Spaces" + if (settings.tabSize == spaces) " ✓" else "")
        }
      }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(
      onClick = onSignOut,
      colors =
        if (uiState.isLocalScratchpad || uiState.isGitHubIdeOnlyMode) ButtonDefaults.outlinedButtonColors()
        else ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().testTag("sign_out_button"),
    ) {
      Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text(if (uiState.credentials == null) "Connect Cloudflare Account" else "Disconnect Cloudflare")
    }
  }
}

@Composable
private fun RamWorkspaceStatusCard(
  uiState: IdeUiState,
  onDeployExport: () -> Unit,
  onReimport: () -> Unit,
  onRevertWorkspace: () -> Unit,
  onExportBundle: () -> Unit,
  onImportBundle: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val stats = uiState.ramDbStats
  val dirtyOrPending = uiState.hasUnsavedChanges || stats.dirtyFilesCount > 0 || uiState.activeWorkspaceHasPendingConfig

  Surface(
    color = palette.chromePill.copy(alpha = 0.7f),
    shape = RoundedCornerShape(12.dp),
    border =
      BorderStroke(
        width = 1.dp,
        color = if (dirtyOrPending) AmberWarn.copy(alpha = 0.45f) else palette.border.copy(alpha = 0.4f),
      ),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).testTag("ram_db_card"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          Icons.Outlined.Storage,
          contentDescription = "On-device RAM database",
          tint = if (dirtyOrPending) AmberWarn else EmeraldLive,
          modifier = Modifier.size(15.dp),
        )
        Text(
          text = "ON-DEVICE RAM DB",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = palette.textPrimary,
          modifier = Modifier.weight(1f),
        )
        Surface(
          color = palette.activePillBg,
          shape = RoundedCornerShape(6.dp),
        ) {
          Text(
            text = "${stats.estimatedRamKb} KB",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.activePillFg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
      }

      Text(
        text =
          buildString {
            append("${stats.workspacesInRam} ws • ${stats.totalFilesInRam} files • ${stats.dirtyFilesCount} edited")
            if (uiState.activeWorkspaceHasPendingConfig) append(" • config*")
          },
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        color = palette.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (uiState.workspace != null && !uiState.isLocalScratchpad) {
          RamActionChip(
            icon = Icons.Outlined.CloudUpload,
            label = if (dirtyOrPending) "Export / Deploy*" else "Export / Deploy",
            accent = if (dirtyOrPending) palette.activePillFg else palette.textPrimary,
            bg = if (dirtyOrPending) palette.activePillBg else palette.chromeSurface,
            tag = "ram_export_deploy_button",
            enabled = !uiState.isDeploying,
            onClick = onDeployExport,
          )
          RamActionChip(
            icon = Icons.Outlined.Refresh,
            label = "Re-Import",
            accent = palette.textPrimary,
            bg = palette.chromeSurface,
            tag = "ram_reimport_button",
            enabled = !uiState.isOpeningWorker,
            onClick = onReimport,
          )
        }
        if (uiState.workspace != null && dirtyOrPending) {
          RamActionChip(
            icon = Icons.Outlined.Restore,
            label = "Revert",
            accent = MaterialTheme.colorScheme.error,
            bg = palette.chromeSurface,
            tag = "ram_revert_button",
            enabled = true,
            onClick = onRevertWorkspace,
          )
        }
        if (uiState.workspace != null) {
          RamActionChip(
            icon = Icons.Outlined.FileUpload,
            label = "Export .json",
            accent = palette.textSecondary,
            bg = palette.chromeSurface,
            tag = "ram_export_bundle_button",
            enabled = true,
            onClick = onExportBundle,
          )
        }
        RamActionChip(
          icon = Icons.Outlined.CloudDownload,
          label = "Import .json",
          accent = palette.textSecondary,
          bg = palette.chromeSurface,
          tag = "ram_import_bundle_button",
          enabled = true,
          onClick = onImportBundle,
        )
      }
    }
  }
}

@Composable
private fun RamActionChip(
  icon: ImageVector,
  label: String,
  accent: Color,
  bg: Color,
  tag: String,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    color = bg,
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.testTag(tag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(13.dp))
      Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = accent, maxLines = 1)
    }
  }
}

