package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.components.ApiTesterPane
import com.example.ui.components.BatchUploadOptionsDialog
import com.example.ui.components.ChangeSubdomainDialog
import com.example.ui.components.CloudflareInfraPanel
import com.example.ui.components.CodeEditorPane
import com.example.ui.components.CommandPaletteDialog
import com.example.ui.components.ConflictResolverModal
import com.example.ui.components.ConsoleView
import com.example.ui.components.DeployedWorkerPreviewPane
import com.example.ui.components.DiffReviewIntent
import com.example.ui.components.DiffReviewModal
import com.example.ui.components.EdgeMenu
import com.example.ui.components.EdgeMenuDivider
import com.example.ui.components.EdgeMenuHeader
import com.example.ui.components.EdgeMenuItem
import com.example.ui.components.EdgeMenuQuickAction
import com.example.ui.components.EdgeMenuQuickActionRow
import com.example.ui.components.EdgeMenuSection
import com.example.ui.components.EdgeMenuTone
import com.example.ui.components.EdgeMenuUiLayer
import com.example.ui.components.EdgeMenuUiLayersPanel
import com.example.ui.components.EdgeBottomDock
import com.example.ui.components.EdgeBottomDockItem
import com.example.ui.components.GitHubAndDiffPanel
import com.example.ui.components.LinuxTerminalPane
import com.example.ui.components.LiveTailView
import com.example.ui.components.NewDirectoryDialog
import com.example.ui.components.NewModuleFileDialog
import com.example.ui.components.NewWorkerDialog
import com.example.ui.components.PaletteCommand
import com.example.ui.components.ProblemsView
import com.example.ui.components.ProjectFilesSidebar
import com.example.ui.components.RenameFileDialog
import com.example.ui.components.RenameWorkerDialog
import com.example.ui.components.WorkerTestWindow
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.edgeGlassDock
import com.example.ui.theme.edgeGlassSurface
import com.example.ui.theme.rememberGlassBorderStroke
import com.example.ui.viewmodel.BottomPanelTab
import com.example.ui.viewmodel.IdeUiState
import com.example.ui.viewmodel.IdeViewModel
import com.example.ui.viewmodel.ProjectMode
import kotlinx.coroutines.launch

@Composable
fun IdeWorkspaceScreen(
  uiState: IdeUiState,
  viewModel: IdeViewModel,
) {
  val palette = LocalIdePalette.current
  val context = LocalContext.current
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var showNewWorkerDialog by remember { mutableStateOf(false) }
  var pickedNewWorkerUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
  var newFileDialogPrefix by remember { mutableStateOf<String?>(null) }
  var newDirDialogBase by remember { mutableStateOf<String?>(null) }
  var showCommandPalette by remember { mutableStateOf(false) }
  var pendingUploadUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
  var pendingRenamePath by remember { mutableStateOf<String?>(null) }
  var overflowMenuOpen by remember { mutableStateOf(false) }
  var showProjectGitModal by remember { mutableStateOf(false) }
  var showProjectSettingsModal by remember { mutableStateOf(false) }
  var showRenameWorkerDialog by remember { mutableStateOf(false) }
  var showChangeSubdomainDialog by remember { mutableStateOf(false) }

  // Zero-permission Storage Access Framework (SAF) launchers
  val singleFileUploadLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) pendingUploadUris = listOf(uri)
    }

  val batchFileUploadLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) pendingUploadUris = uris
    }

  val newWorkerFilesLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) pickedNewWorkerUris = uris
    }

  val exportFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
      if (uri != null) viewModel.exportActiveFileToUri(uri)
    }

  val exportWorkspaceBundleLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri != null) viewModel.exportWorkspaceBundleToUri(uri)
    }

  val importWorkspaceBundleLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.importWorkspaceBundleFromUri(uri)
    }

  val triggerSingleUpload = { singleFileUploadLauncher.launch(arrayOf("*/*")) }
  val triggerBatchUpload = { batchFileUploadLauncher.launch(arrayOf("*/*")) }
  val triggerExportActiveFile = {
    val fileName = uiState.activeFile?.fileName ?: "index.js"
    exportFileLauncher.launch(fileName)
  }
  val triggerExportWorkspaceBundle = {
    val slug = (uiState.workspace?.scriptName ?: "worker").replace(Regex("[^a-zA-Z0-9._-]"), "-")
    exportWorkspaceBundleLauncher.launch("$slug-ram-bundle.json")
  }
  val triggerImportWorkspaceBundle = {
    importWorkspaceBundleLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
  }

  LaunchedEffect(uiState.statusMessage) {
    val msg = uiState.statusMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearStatusMessage()
  }

  LaunchedEffect(uiState.deployBannerMessage) {
    val msg = uiState.deployBannerMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.dismissDeployBanner()
  }

  BackHandler {
    when {
      showProjectGitModal -> showProjectGitModal = false
      showProjectSettingsModal -> showProjectSettingsModal = false
      drawerState.isOpen -> scope.launch { drawerState.close() }
      uiState.projectMode == ProjectMode.TEST && uiState.isPreviewFullScreen ->
        viewModel.setPreviewFullScreen(false)
      uiState.showFindBar -> viewModel.toggleFindBar(false)
      uiState.projectMode != ProjectMode.CODE -> viewModel.setProjectMode(ProjectMode.CODE)
      else -> viewModel.closeWorkspaceToHome()
    }
  }

  if (showNewWorkerDialog) {
    NewWorkerDialog(
      pickedUris = pickedNewWorkerUris,
      onPickFiles = { newWorkerFilesLauncher.launch(arrayOf("*/*")) },
      onClearPickedFiles = { pickedNewWorkerUris = emptyList() },
      onDismiss = {
        showNewWorkerDialog = false
        pickedNewWorkerUris = emptyList()
      },
      onCreateFromTemplate = { name, template ->
        showNewWorkerDialog = false
        pickedNewWorkerUris = emptyList()
        viewModel.createNewWorker(name, template)
      },
      onCreateFromFiles = { name, uris, folderPrefix ->
        showNewWorkerDialog = false
        pickedNewWorkerUris = emptyList()
        viewModel.createNewWorkerFromUris(name, uris, folderPrefix)
      },
    )
  }

  if (showRenameWorkerDialog) {
    uiState.workspace?.let { ws ->
      val effectiveSub = ws.accountSubdomain.ifBlank { uiState.accountSubdomain }
      RenameWorkerDialog(
        currentName = ws.scriptName,
        subdomain = effectiveSub,
        isRemoteWorker = !uiState.isLocalScratchpad && uiState.credentials != null,
        hasEncryptedSecrets = ws.bindings.any { it.type == "secret_text" },
        onDismiss = { showRenameWorkerDialog = false },
        onConfirm = { newName, deleteOld ->
          showRenameWorkerDialog = false
          viewModel.renameExistingWorker(newScriptNameRaw = newName, deleteOldRemote = deleteOld)
        },
      )
    }
  }

  if (showChangeSubdomainDialog) {
    val activeWs = uiState.workspace
    val effectiveSub = activeWs?.accountSubdomain?.takeIf { it.isNotBlank() } ?: uiState.accountSubdomain
    ChangeSubdomainDialog(
      currentSubdomain = effectiveSub,
      sampleWorkerName = activeWs?.scriptName.orEmpty(),
      isLocalMode = uiState.isLocalScratchpad || uiState.credentials == null,
      onDismiss = { showChangeSubdomainDialog = false },
      onConfirm = { newSub ->
        showChangeSubdomainDialog = false
        viewModel.updateAccountWorkersSubdomain(newSub)
      },
    )
  }

  newFileDialogPrefix?.let { prefix ->
    NewModuleFileDialog(
      initialPrefix = prefix,
      onDismiss = { newFileDialogPrefix = null },
      onCreate = { path ->
        newFileDialogPrefix = null
        viewModel.addModuleFile(path)
      },
    )
  }

  newDirDialogBase?.let { base ->
    NewDirectoryDialog(
      basePath = base,
      onDismiss = { newDirDialogBase = null },
      onCreate = { dirPath ->
        newDirDialogBase = null
        viewModel.createModuleDirectory(dirPath)
      },
    )
  }

  pendingRenamePath?.let { oldPath ->
    RenameFileDialog(
      currentPath = oldPath,
      onDismiss = { pendingRenamePath = null },
      onRename = { newPath ->
        pendingRenamePath = null
        viewModel.renameModuleFile(oldPath, newPath)
      },
    )
  }

  if (pendingUploadUris.isNotEmpty() && uiState.workspace != null) {
    BatchUploadOptionsDialog(
      workerName = uiState.workspace.scriptName,
      pickedUris = pendingUploadUris,
      isLocalScratchpad = uiState.isLocalScratchpad,
      onDismiss = { pendingUploadUris = emptyList() },
      onConfirmUpload = { folderPrefix, autoDeploy ->
        val uris = pendingUploadUris
        pendingUploadUris = emptyList()
        viewModel.uploadFilesToActiveWorker(uris, folderPrefix, autoDeploy)
      },
    )
  }

  if (uiState.showDiffReviewModal) {
    DiffReviewModal(
      intent = uiState.diffReviewIntent,
      entries = uiState.diffReviewEntries,
      onDismiss = viewModel::dismissDiffReview,
      onRevertFile = viewModel::revertFile,
      onPrimaryProceed =
        if (uiState.diffReviewIntent != DiffReviewIntent.WORKSPACE) {
          { viewModel.proceedFromDiffReview() }
        } else null,
      onOpenFileInEditor = { path ->
        viewModel.openFileTab(path, recordHistory = true)
        viewModel.setProjectMode(ProjectMode.CODE)
      },
    )
  }

  if (uiState.showConflictResolverModal && uiState.pendingMergeConflicts.isNotEmpty()) {
    ConflictResolverModal(
      conflictFiles = uiState.pendingMergeConflicts,
      onResolveHunk = viewModel::resolveConflictHunk,
      onResolveAllInFile = viewModel::resolveAllConflictsInFile,
      onApplyResolvedToWorkspace = viewModel::applyResolvedConflictsToWorkspace,
      onDismiss = viewModel::dismissConflictResolver,
    )
  }

  if (showProjectSettingsModal) {
    val requestStorageAccess =
      com.example.ui.components.rememberStorageAccessRequester {
        viewModel.refreshStoragePermissionAndReload()
      }
    Box(modifier = Modifier.fillMaxSize()) {
      SettingsScreen(
        uiState = uiState,
        onBack = { showProjectSettingsModal = false },
        onUpdateSettings = viewModel::updateSettings,
        onSelectAccount = viewModel::selectAccount,
        onVerifyCredentials = viewModel::verifyCloudflareCredentials,
        onUpdateApiToken = viewModel::updateCloudflareApiToken,
        onUpdateGlobalKey = viewModel::updateCloudflareGlobalKey,
        onStartOAuth = {
          viewModel.startOAuthSignIn { authUrl ->
            runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
            }
          }
        },
        onCancelOAuth = viewModel::cancelOAuthSignIn,
        onClearCredentialFeedback = viewModel::clearCloudflareCredentialTestMessage,
        onDisconnectCloudflare = {
          showProjectSettingsModal = false
          if (uiState.isLocalScratchpad || uiState.isGitHubIdeOnlyMode) viewModel.exitScratchpadToAuth()
          else viewModel.signOut()
        },
        onOpenGitHubHub = {
          showProjectSettingsModal = false
          showProjectGitModal = true
        },
        onClearAutoSavedDrafts = viewModel::clearAllAutoSavedDrafts,
        onRequestStorageAccess = requestStorageAccess,
        onPickEditorBackgroundImage = viewModel::setCustomEditorBackground,
        onClearEditorBackgroundImage = viewModel::clearCustomEditorBackground,
        onNotify = viewModel::showStatus,
      )
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(16.dp),
      )
    }
    return
  }

  if (showProjectGitModal) {
    Dialog(
      onDismissRequest = { showProjectGitModal = false },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(
        color = palette.chromeBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 18.dp),
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column {
              Text(
                text = "Git Sync (Optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
              )
              Text(
                text = "Project: ${uiState.workspace?.scriptName ?: "none"}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
              )
            }
            IconButton(onClick = { showProjectGitModal = false }) {
              Icon(Icons.Outlined.Close, contentDescription = "Close Git Sync", tint = palette.textSecondary)
            }
          }
          HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
          Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            GitHubAndDiffPanel(
              uiState = uiState,
              onStartGitHubOAuth = { clientId ->
                viewModel.startGitHubDeviceOAuth(clientIdOverride = clientId, enterIdeOnSuccess = false) { verificationUrl ->
                  runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verificationUrl)))
                  }
                }
              },
              onCancelGitHubOAuth = viewModel::cancelGitHubDeviceOAuth,
              onConnectGitHubToken = { pat -> viewModel.connectGitHubToken(pat, enterIdeOnSuccess = false) },
              onDisconnectGitHub = viewModel::disconnectGitHub,
              onRefreshRepos = viewModel::refreshGitHubRepos,
              onSelectRepo = viewModel::selectGitHubRepo,
              onSelectBranch = viewModel::selectGitHubBranch,
              onCreateBranch = viewModel::createGitHubBranch,
              onCloneRepository = { url, branch, merge ->
                viewModel.cloneGitHubRepository(url, branch, merge)
              },
              onCreateGitHubRepo = viewModel::createGitHubRepoAndLink,
              onLinkSelectedRepo = viewModel::linkCurrentWorkerToSelectedGitHubRepo,
              onPullFilesToWorker = viewModel::pullGitHubPathsIntoWorker,
              onSyncPullLinkedRepo = viewModel::syncPullLinkedGitRepo,
              onSetCommitMessage = viewModel::setGitCommitMessage,
              onToggleStageFile = viewModel::toggleGitFileStaged,
              onStageAll = viewModel::stageAllGitChanges,
              onUnstageAll = viewModel::unstageAllGitChanges,
              onDiscardFileChange = viewModel::discardGitFileChange,
              onCommitLocally = { viewModel.commitStagedChangesLocally() },
              onPushToGitHub = { viewModel.pushQueuedAndActiveChangesToGitHub() },
              onInspectCommit = viewModel::inspectGitHubCommit,
              onInspectPullRequest = viewModel::inspectPullRequest,
              onCreatePullRequest = viewModel::createPullRequest,
              onMergePullRequest = viewModel::mergePullRequest,
              onCreateIssue = viewModel::createGitHubIssue,
              onToggleIssueState = viewModel::toggleGitHubIssueState,
              onCommentOnIssue = viewModel::commentOnGitHubIssue,
              onDispatchWorkflow = viewModel::dispatchGitHubWorkflow,
              onRerunWorkflowRun = viewModel::rerunGitHubWorkflowRun,
              onInspectWorkflowRunJobs = viewModel::inspectWorkflowRunJobs,
              onSubmitPrReview = viewModel::submitPullRequestReview,
              onGenerateCfActionWorkflow = viewModel::generateCloudflareGitHubActionWorkflow,
              onOpenFileInEditor = { path ->
                viewModel.openFileTab(path, recordHistory = true)
                viewModel.setProjectMode(ProjectMode.CODE)
                showProjectGitModal = false
              },
              onRenameWorkerTarget = viewModel::renameActiveWorkerTarget,
              onDeployActiveWorker = viewModel::deployActiveWorker,
              onRevertFile = viewModel::revertFile,
              onCloseDrawerIfCompact = { showProjectGitModal = false },
              onOpenDiffReview = { viewModel.openDiffReview(DiffReviewIntent.WORKSPACE) },
              onOpenPrePushDiffReview = { viewModel.openDiffReview(DiffReviewIntent.PRE_GIT_PUSH) },
              onOpenConflictResolver = viewModel::openConflictResolver,
            )
          }
        }
      }
    }
  }

  if (showCommandPalette) {
    val fileCommands =
      uiState.workspace?.files.orEmpty().map { file ->
        PaletteCommand(
          title = "Open ${file.path}",
          subtitle = if (file.isEntryPoint) "Worker entry module" else "Workspace file",
        ) {
          viewModel.openFileTab(file.path, recordHistory = true)
          viewModel.setProjectMode(ProjectMode.CODE)
        }
      }
    val commands =
      listOf(
        PaletteCommand("Switch to Code Mode", "Open full-screen editor for current project") {
          viewModel.setProjectMode(ProjectMode.CODE)
        },
        PaletteCommand("Open Deployed Worker Preview", "Live WebView & HTTP preview for this Worker") {
          viewModel.openDeployedWorkerPreview(maximizeWindow = true)
        },
        PaletteCommand("Toggle Preview Fullscreen", "Expand or restore the live Worker preview window") {
          if (uiState.projectMode != ProjectMode.TEST) {
            viewModel.openDeployedWorkerPreview(maximizeWindow = true)
            viewModel.setPreviewFullScreen(true)
          } else {
            viewModel.togglePreviewFullScreen()
          }
        },
        PaletteCommand("Open Worker HTTP Tester", "Test Worker routes with custom methods, headers & body") {
          viewModel.openWorkerTestWindow(maximizeWindow = true)
        },
        PaletteCommand("Open API Tester", "Postman-style API tester with collections & env vars") {
          viewModel.toggleBottomPanel(BottomPanelTab.API_TESTER)
        },
        PaletteCommand("Open Project Config", "Bindings, Env Vars, Secrets, Routes, Cron & Rollback") {
          viewModel.setProjectMode(ProjectMode.CONFIG)
        },
        PaletteCommand("Open Project Terminal", "Run curl, jq, wrangler, and Linux commands") {
          viewModel.toggleBottomPanel(BottomPanelTab.TERMINAL)
        },
        PaletteCommand("Open Problems (${uiState.diagnostics.size})", "Inspect syntax & Worker diagnostics") {
          viewModel.toggleBottomPanel(BottomPanelTab.PROBLEMS)
        },
        PaletteCommand("Zoom In Editor", "Increase editor code magnification (+15%)") {
          viewModel.adjustEditorZoom(15)
        },
        PaletteCommand("Zoom Out Editor", "Decrease editor code magnification (-15%)") {
          viewModel.adjustEditorZoom(-15)
        },
        PaletteCommand("Reset Editor Zoom (100%)", "Restore default editor magnification") {
          viewModel.resetEditorZoom()
        },
        PaletteCommand("Toggle Live Tail Logs", "Stream real-time console.log() via WebSocket") {
          viewModel.toggleLiveTail()
        },
        PaletteCommand("Open IDE Log", "Inspect internal IDE deployment & API event log") {
          viewModel.toggleBottomPanel(BottomPanelTab.CONSOLE)
        },
        PaletteCommand(
          title =
            if (
              uiState.isTopBarHidden &&
              uiState.isEditorTopChromeHidden &&
              uiState.isEditorBottomBarsHidden &&
              uiState.isBottomDockHidden
            ) "Show All UI Layers (4/4)"
            else "Hide All UI Layers (Zen Mode)",
          subtitle = "Collapse or restore Top Bar, Tabs & Path, Editor Bar, and Bottom Dock",
        ) {
          val allHidden =
            uiState.isTopBarHidden &&
              uiState.isEditorTopChromeHidden &&
              uiState.isEditorBottomBarsHidden &&
              uiState.isBottomDockHidden
          viewModel.toggleAllUiChrome(!allHidden)
        },
        PaletteCommand("Export / Deploy Worker to Cloudflare", "Publish all local edits, bindings & deletions live") {
          viewModel.deployActiveWorker()
        },
        PaletteCommand("Rename Worker", "Rename this Worker script, wrangler.jsonc & live URL") {
          if (uiState.workspace != null) showRenameWorkerDialog = true
        },
        PaletteCommand("Change workers.dev Subdomain", "Update your *.workers.dev subdomain endpoint") {
          showChangeSubdomainDialog = true
        },
        PaletteCommand("Review Diff Before Deploy", "Inspect Unified or Side-by-Side line changes before deploying") {
          viewModel.openDiffReview(DiffReviewIntent.PRE_DEPLOY)
        },
        PaletteCommand("Open Workspace Diff Viewer", "Unified and Side-by-Side diff inspector for changed files") {
          viewModel.openDiffReview(DiffReviewIntent.WORKSPACE)
        },
        PaletteCommand("Resolve Merge Conflicts", "Open interactive 3-way merge conflict resolver") {
          viewModel.openConflictResolver()
        },
        PaletteCommand("Re-Import Worker from Cloudflare", "Discard local edits and pull fresh Worker code") {
          viewModel.reimportActiveWorkerFromCloudflare()
        },
        PaletteCommand("Revert Workspace to Baseline", "Restore workspace files back to imported baseline") {
          viewModel.revertWorkspaceToImportedBaseline()
        },
        PaletteCommand("Export Workspace Bundle (.json)", "Save organized workspace files and metadata as JSON") {
          if (uiState.workspace != null) triggerExportWorkspaceBundle()
        },
        PaletteCommand("Import Workspace Bundle (.json)", "Load saved workspace JSON bundle into Project Dir") {
          triggerImportWorkspaceBundle()
        },
        PaletteCommand("Upload Single File", "Pick one local script/asset file into this project") {
          if (uiState.workspace != null) triggerSingleUpload()
        },
        PaletteCommand("Batch Upload Files", "Import multiple module scripts into this project") {
          if (uiState.workspace != null) triggerBatchUpload()
        },
        PaletteCommand("Export Active File", "Save current script out to device storage") {
          if (uiState.activeFile != null) triggerExportActiveFile()
        },
        PaletteCommand("Sync wrangler.jsonc", "Apply wrangler.jsonc/.toml bindings, vars & crons to workspace") {
          viewModel.syncWranglerFileToWorkspace()
        },
        PaletteCommand("Format Document", "Auto-indent JavaScript or JSON") {
          viewModel.formatActiveFile()
        },
        PaletteCommand("Git Sync (Optional)", "Stage, commit & push project changes to GitHub") {
          showProjectGitModal = true
        },
        PaletteCommand("Settings & Cloudflare Credentials", "API tokens, deployment defaults, theme & editor preferences") {
          showProjectSettingsModal = true
        },
        PaletteCommand("Back to All Projects (Home)", "Return to the Home Projects Hub") {
          viewModel.closeWorkspaceToHome()
        },
      ) + fileCommands
    CommandPaletteDialog(commands = commands, onDismiss = { showCommandPalette = false })
  }

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize().background(palette.chromeBg),
  ) {
    val isExpanded = maxWidth >= 840.dp
    val drawerWidth = (maxWidth * 0.78f).coerceIn(270.dp, 326.dp)

    val projectSidebarContent: @Composable () -> Unit = {
      ProjectFilesSidebar(
        uiState = uiState,
        onOpenFile = { path ->
          viewModel.openFileTab(path, recordHistory = true)
          viewModel.setProjectMode(ProjectMode.CODE)
        },
        onShowNewFileDialog = { prefix -> newFileDialogPrefix = prefix },
        onShowNewDirectoryDialog = { base -> newDirDialogBase = base },
        onUploadSingleFile = triggerSingleUpload,
        onUploadBatchFiles = triggerBatchUpload,
        onExportActiveFile = triggerExportActiveFile,
        onDeleteModuleFile = viewModel::deleteModuleFile,
        onRequestRenameModuleFile = { path -> pendingRenamePath = path },
        onSetEntryModule = viewModel::setEntryModule,
        onRevertWorkspaceToImported = viewModel::revertWorkspaceToImportedBaseline,
        onSearchQueryChange = viewModel::setSearchQuery,
        onReplaceInAllFiles = viewModel::replaceInAllFiles,
        onJumpToLine = viewModel::jumpToLine,
        onInsertSnippet = { snippet ->
          viewModel.insertTextAtCursor(snippet, 0)
          viewModel.setProjectMode(ProjectMode.CODE)
        },
        onCloseDrawerIfCompact = {
          if (!isExpanded) scope.launch { drawerState.close() }
        },
        showCloseButton = !isExpanded,
      )
    }

    val isFullScreenPreview = uiState.projectMode == ProjectMode.TEST && uiState.isPreviewFullScreen

    val workspaceBody: @Composable () -> Unit = {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(palette.chromeBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          // Layer 1: Single-Project Top Header Bar (hideable via Hide UI or UI Layers L1)
          AnimatedVisibility(
            visible = !isFullScreenPreview && !uiState.isTopBarHidden,
            enter = slideInVertically(tween(180)) { -it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(150)) { -it } + fadeOut(tween(130)),
          ) {
          Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .background(palette.chromeBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            IconButton(
              onClick = viewModel::closeWorkspaceToHome,
              modifier = Modifier.size(38.dp).testTag("back_to_home_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back to Home",
                tint = palette.textPrimary,
                modifier = Modifier.size(20.dp),
              )
            }

            Column(
              modifier =
                Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { showCommandPalette = true }
                  .padding(horizontal = 4.dp, vertical = 2.dp),
              verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = uiState.workspace?.scriptName ?: "Project",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = "Open Command Palette",
                  tint = palette.textMuted,
                  modifier = Modifier.size(13.dp),
                )
              }
              val fileCount = uiState.workspace?.files?.count { !it.isVirtualReadOnly } ?: 0
              val dirtyCount = uiState.workspace?.files?.count { it.isDirty } ?: 0
              Text(
                text =
                  when {
                    dirtyCount > 0 -> "$fileCount files · $dirtyCount edited"
                    uiState.isLocalScratchpad -> "$fileCount files · Local Dir"
                    else -> "$fileCount files · Synced"
                  },
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = if (dirtyCount > 0) AmberWarn else palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            if (!isExpanded && uiState.workspace != null) {
              Surface(
                onClick = { scope.launch { drawerState.open() } },
                shape = CircleShape,
                color = palette.chromeSurface,
                border = rememberGlassBorderStroke(0.75.dp),
                modifier = Modifier.size(34.dp).testTag("workspace_quick_files_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = "Open Project Files",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(17.dp),
                  )
                }
              }
            }

            if (uiState.workspace != null) {
              if (uiState.isLocalScratchpad) {
                Surface(
                  onClick = viewModel::exitScratchpadToAuth,
                  shape = RoundedCornerShape(10.dp),
                  color = palette.activePillBg,
                  border = rememberGlassBorderStroke(0.75.dp),
                  modifier = Modifier.testTag("connect_cloudflare_button"),
                ) {
                  Text(
                    text = "Connect",
                    color = palette.activePillFg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                  )
                }
              } else {
                Surface(
                  onClick = { if (!uiState.isDeploying) viewModel.deployActiveWorker() },
                  shape = RoundedCornerShape(10.dp),
                  color = if (uiState.hasUnsavedChanges) palette.activePillBg else palette.chromeSurface,
                  border =
                    if (uiState.hasUnsavedChanges) BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.35f))
                    else rememberGlassBorderStroke(0.75.dp),
                  modifier = Modifier.testTag("deploy_worker_button"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                  ) {
                    if (uiState.isDeploying) {
                      CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                      Icon(
                        imageVector = Icons.Outlined.CloudUpload,
                        contentDescription = stringResource(R.string.cd_deploy_worker),
                        tint = if (uiState.hasUnsavedChanges) palette.activePillFg else palette.textPrimary,
                        modifier = Modifier.size(16.dp),
                      )
                      Text(
                        text = if (uiState.hasUnsavedChanges) "Deploy*" else "Deploy",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (uiState.hasUnsavedChanges) palette.activePillFg else palette.textPrimary,
                      )
                    }
                  }
                }
              }
            }

            IconButton(
              onClick = { overflowMenuOpen = true },
              modifier = Modifier.size(38.dp).testTag("project_overflow_menu_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "Project actions",
                tint = palette.textSecondary,
                modifier = Modifier.size(20.dp),
              )
            }
          }

          if (uiState.isOpeningWorker || uiState.isDeploying) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
          }
          HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
          }
          }

          // Main Content Area: Strictly 1 Clean Mode at a Time
          Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isExpanded && uiState.projectMode == ProjectMode.CODE) {
              Box(modifier = Modifier.width(300.dp).fillMaxHeight()) {
                projectSidebarContent()
              }
              VerticalDivider(color = palette.border.copy(alpha = 0.5f))
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
              AnimatedContent(
                targetState = uiState.projectMode,
                transitionSpec = {
                  fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                },
                modifier = Modifier.fillMaxSize(),
                label = "workspace_mode_anim",
              ) { mode ->
                when (mode) {
                  ProjectMode.CODE -> {
                    CodeEditorPane(
                      uiState = uiState,
                      onSelectTab = { path -> viewModel.openFileTab(path, recordHistory = true) },
                      onCloseTab = viewModel::closeFileTab,
                      onSelectAdjacentTab = viewModel::selectAdjacentTab,
                      onCloseOtherTabs = viewModel::closeOtherTabs,
                      onCloseSavedTabs = viewModel::closeSavedTabs,
                      onCloseAllTabs = viewModel::closeAllTabs,
                      onSetEntryModule = viewModel::setEntryModule,
                      onRevertFile = viewModel::revertFile,
                      onNavigateTabBack = viewModel::navigateTabBack,
                      onNavigateTabForward = viewModel::navigateTabForward,
                      onOpenWorkerPreview = { viewModel.openDeployedWorkerPreview(maximizeWindow = true) },
                      onCreateNewFile = { newFileDialogPrefix = "" },
                      onCodeChange = viewModel::syncTypedEditorValue,
                      onInsertText = viewModel::insertTextAtCursor,
                      onInsertTab = viewModel::insertTab,
                      onUndo = viewModel::undo,
                      onRedo = viewModel::redo,
                      onFormat = viewModel::formatActiveFile,
                      onToggleComment = viewModel::toggleLineComment,
                      onToggleFindBar = { viewModel.toggleFindBar() },
                      onFindQueryChange = viewModel::setFindQuery,
                      onReplaceQueryChange = viewModel::setReplaceQuery,
                      onFindNext = viewModel::findNextMatch,
                      onFindPrevious = viewModel::findPreviousMatch,
                      onReplaceCurrent = viewModel::replaceCurrentMatch,
                      onReplaceAll = viewModel::replaceAllMatches,
                      onToggleFindMatchCase = viewModel::toggleFindMatchCase,
                      onFindScopeChange = viewModel::setFindScope,
                      onOpenProblems = { viewModel.toggleBottomPanel(BottomPanelTab.PROBLEMS) },
                      onOpenExplorer = {
                        if (!isExpanded) scope.launch { drawerState.open() }
                      },
                      onShowNewWorkerDialog = { showNewWorkerDialog = true },
                      onDismissDraftBanner = viewModel::dismissDraftBanner,
                      onDiscardRestoredDraft = viewModel::discardRestoredDraft,
                      onJumpToLine = viewModel::jumpToLine,
                      onSyncWranglerToWorkspace = viewModel::syncWranglerFileToWorkspace,
                      onZoomPercentChange = viewModel::setEditorZoomPercent,
                      onJumpToLocation = viewModel::jumpToLine,
                      onHideEditorBottomBars = { viewModel.setEditorBottomBarsHidden(true) },
                      onOpenConflictResolver = viewModel::openConflictResolver,
                      modifier = Modifier.fillMaxSize(),
                    )
                  }

                  ProjectMode.TEST -> {
                    ProjectTestModeWorkspace(
                      uiState = uiState,
                      viewModel = viewModel,
                    )
                  }

                  ProjectMode.CONFIG -> {
                    CloudflareInfraPanel(
                      uiState = uiState,
                      onRefreshInfra = viewModel::loadCloudflareInfra,
                      onToggleWorkersDev = viewModel::toggleWorkersDev,
                      onUpdateCompat = viewModel::updateCompatibilitySettings,
                      onAddCronSchedule = viewModel::addCronSchedule,
                      onRemoveCronSchedule = viewModel::removeCronSchedule,
                      onAttachZoneRoute = viewModel::attachZoneRoute,
                      onDeleteZoneRoute = viewModel::deleteZoneRoute,
                      onAttachWorkerDomain = viewModel::attachWorkerCustomDomain,
                      onRemoveWorkerDomain = viewModel::removeWorkerCustomDomain,
                      onRollbackVersion = viewModel::rollbackToVersion,
                      onRefreshWorkerVersions = viewModel::loadWorkerVersions,
                      onDeployGradualSplit = viewModel::deployGradualWorkerSplit,
                      onAddEnvVar = viewModel::addEnvVar,
                      onAddSecret = viewModel::addSecret,
                      onBindResource = viewModel::bindResourceToWorker,
                      onDeleteBinding = viewModel::deleteBindingOrSecret,
                      onCreateKvNamespace = viewModel::createKvNamespace,
                      onDeleteKvNamespace = viewModel::deleteKvNamespace,
                      onSelectKvNamespace = viewModel::selectKvNamespace,
                      onInspectKvKey = viewModel::inspectKvKey,
                      onPutKvEntry = viewModel::putKvEntry,
                      onBulkImportKv = viewModel::bulkImportKvJson,
                      onExportKvJson = viewModel::exportSelectedKvJson,
                      onDeleteKvKey = viewModel::deleteKvKey,
                      onCreateD1Database = viewModel::createD1Database,
                      onDeleteD1Database = viewModel::deleteD1Database,
                      onSelectD1Database = viewModel::selectD1Database,
                      onRunD1Sql = viewModel::runD1SqlQuery,
                      onCreateR2Bucket = viewModel::createR2Bucket,
                      onDeleteR2Bucket = viewModel::deleteR2Bucket,
                      onCreateQueue = viewModel::createQueue,
                      onDeleteQueue = viewModel::deleteQueue,
                      onNotifyStatus = viewModel::showStatus,
                      onRenameWorker = { newName, deleteOld ->
                        viewModel.renameExistingWorker(newScriptNameRaw = newName, deleteOldRemote = deleteOld)
                      },
                      onUpdateSubdomain = viewModel::updateAccountWorkersSubdomain,
                    )
                  }

                  ProjectMode.CONSOLE -> {
                    ProjectConsoleModeWorkspace(
                      uiState = uiState,
                      viewModel = viewModel,
                    )
                  }
                }
              }
            }
          }

          // Sleek Hideable 4-Destination Bottom Dock (hidden in Fullscreen Preview or when collapsed)
          AnimatedVisibility(
            visible = !isFullScreenPreview && !uiState.isBottomDockHidden,
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(140)),
          ) {
            EdgeBottomDock(
              onHideDock = { viewModel.setBottomDockHidden(true) },
              hideDockTestTag = "hide_workspace_bottom_dock_button",
              items =
                listOf(
                  EdgeBottomDockItem(
                    label = "Code",
                    icon = Icons.Outlined.Code,
                    selected = uiState.projectMode == ProjectMode.CODE,
                    testTag = "project_nav_code",
                    onClick = { viewModel.setProjectMode(ProjectMode.CODE) },
                  ),
                  EdgeBottomDockItem(
                    label = "Test",
                    icon = Icons.Outlined.PlayArrow,
                    selected = uiState.projectMode == ProjectMode.TEST,
                    testTag = "project_nav_test",
                    onClick = { viewModel.setProjectMode(ProjectMode.TEST) },
                  ),
                  EdgeBottomDockItem(
                    label = "Config",
                    icon = Icons.Outlined.Tune,
                    selected = uiState.projectMode == ProjectMode.CONFIG,
                    testTag = "project_nav_config",
                    onClick = { viewModel.setProjectMode(ProjectMode.CONFIG) },
                  ),
                  EdgeBottomDockItem(
                    label = "Console",
                    icon = Icons.Outlined.Terminal,
                    selected = uiState.projectMode == ProjectMode.CONSOLE,
                    testTag = "project_nav_console",
                    badgeText =
                      when {
                        uiState.isTailRunning -> "LIVE"
                        uiState.diagnostics.isNotEmpty() -> "${uiState.diagnostics.size}"
                        else -> null
                      },
                    badgeColor = if (uiState.isTailRunning) EmeraldLive else AmberWarn,
                    onClick = { viewModel.setProjectMode(ProjectMode.CONSOLE) },
                  ),
                ),
            )
          }
        }

        // Persistent Top-End Anchor for the Overflow & 4-Layer UI Customization Menu
        // Kept outside AnimatedVisibility so toggling Layer 1 (Top Bar) live never closes the menu.
        Box(
          modifier =
            Modifier
              .align(Alignment.TopEnd)
              .padding(top = if (uiState.isTopBarHidden) 8.dp else 44.dp, end = 8.dp),
        ) {
          EdgeMenu(
            expanded = overflowMenuOpen,
            onDismissRequest = { overflowMenuOpen = false },
            minWidth = 264.dp,
            maxWidth = 304.dp,
          ) {
            val activeWs = uiState.workspace
            val menuFileCount = activeWs?.files?.count { !it.isVirtualReadOnly } ?: 0
            val menuDirtyCount = activeWs?.files?.count { it.isDirty } ?: 0
            val allHidden =
              uiState.isTopBarHidden &&
                uiState.isEditorTopChromeHidden &&
                uiState.isEditorBottomBarsHidden &&
                uiState.isBottomDockHidden
            val effectiveSub = activeWs?.accountSubdomain?.takeIf { it.isNotBlank() } ?: uiState.accountSubdomain

            EdgeMenuHeader(
              title = activeWs?.scriptName ?: "Project",
              subtitle =
                when {
                  menuDirtyCount > 0 -> "$menuDirtyCount edited · unsaved"
                  uiState.isLocalScratchpad -> "Local workspace"
                  else -> "Synced with Cloudflare"
                },
              badgeText = if (activeWs != null) "$menuFileCount files" else null,
              leadingIcon = Icons.Outlined.Code,
            )

            val quickActions =
              buildList {
                if (!isExpanded) {
                  add(
                    EdgeMenuQuickAction(
                      label = "Files",
                      icon = Icons.Outlined.FolderOpen,
                      onClick = {
                        overflowMenuOpen = false
                        scope.launch { drawerState.open() }
                      },
                    ),
                  )
                } else {
                  add(
                    EdgeMenuQuickAction(
                      label = "Palette",
                      icon = Icons.Outlined.Terminal,
                      onClick = {
                        overflowMenuOpen = false
                        showCommandPalette = true
                      },
                    ),
                  )
                }
                add(
                  EdgeMenuQuickAction(
                    label = if (allHidden) "Show UI" else "Hide UI",
                    icon = if (allHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    active = allHidden,
                    onClick = {
                      overflowMenuOpen = false
                      viewModel.toggleAllUiChrome(!allHidden)
                    },
                  ),
                )
                add(
                  EdgeMenuQuickAction(
                    label = "Settings",
                    icon = Icons.Outlined.Settings,
                    onClick = {
                      overflowMenuOpen = false
                      showProjectSettingsModal = true
                    },
                  ),
                )
              }
            EdgeMenuQuickActionRow(actions = quickActions)

            EdgeMenuUiLayersPanel(
              layers =
                listOf(
                  EdgeMenuUiLayer(
                    code = "L1",
                    label = "Top Bar",
                    subtitle = "Project header & deploy",
                    visible = !uiState.isTopBarHidden,
                    testTag = "ui_layer_toggle_top_bar",
                    onToggle = viewModel::toggleTopBarHidden,
                  ),
                  EdgeMenuUiLayer(
                    code = "L2",
                    label = "Tabs & Path",
                    subtitle = "File tabs, path & lint",
                    visible = !uiState.isEditorTopChromeHidden,
                    testTag = "ui_layer_toggle_editor_top",
                    onToggle = viewModel::toggleEditorTopChromeHidden,
                  ),
                  EdgeMenuUiLayer(
                    code = "L3",
                    label = "Editor Bar",
                    subtitle = "Status, zoom & key rail",
                    visible = !uiState.isEditorBottomBarsHidden,
                    testTag = "ui_layer_toggle_editor_bottom",
                    onToggle = viewModel::toggleEditorBottomBarsHidden,
                  ),
                  EdgeMenuUiLayer(
                    code = "L4",
                    label = "Bottom Dock",
                    subtitle = "Code · Test · Config · Console",
                    visible = !uiState.isBottomDockHidden,
                    testTag = "ui_layer_toggle_bottom_dock",
                    onToggle = viewModel::toggleBottomDockHidden,
                  ),
                ),
              onToggleAll = { hideAll -> viewModel.toggleAllUiChrome(hideAll) },
            )

            EdgeMenuSection("Workspace")
            if (!isExpanded) {
              EdgeMenuItem(
                label = "Command Palette",
                icon = Icons.Outlined.Terminal,
                badgeText = "⌘K",
                onClick = {
                  overflowMenuOpen = false
                  showCommandPalette = true
                },
              )
            }
            EdgeMenuItem(
              label = "Sync wrangler.jsonc",
              icon = Icons.Outlined.Sync,
              badgeText = "CONFIG",
              onClick = {
                overflowMenuOpen = false
                viewModel.syncWranglerFileToWorkspace()
              },
            )
            if (uiState.hasUnsavedChanges) {
              EdgeMenuItem(
                label = if (uiState.isLocalScratchpad) "Review Workspace Diff" else "Review Diff & Deploy",
                icon = Icons.Outlined.Difference,
                tone = EdgeMenuTone.ACCENT,
                badgeText = "DIFF",
                onClick = {
                  overflowMenuOpen = false
                  viewModel.openDiffReview(
                    if (uiState.isLocalScratchpad) DiffReviewIntent.WORKSPACE else DiffReviewIntent.PRE_DEPLOY,
                  )
                },
              )
              EdgeMenuItem(
                label = "Revert Workspace",
                icon = Icons.Outlined.Restore,
                tone = EdgeMenuTone.DANGER,
                onClick = {
                  overflowMenuOpen = false
                  viewModel.revertWorkspaceToImportedBaseline()
                },
              )
            } else if (activeWs != null) {
              EdgeMenuItem(
                label = "Diff Viewer",
                icon = Icons.Outlined.Difference,
                badgeText = "DIFF",
                onClick = {
                  overflowMenuOpen = false
                  viewModel.openDiffReview(DiffReviewIntent.WORKSPACE)
                },
              )
            }
            if (uiState.pendingMergeConflicts.isNotEmpty()) {
              EdgeMenuItem(
                label = "Merge Conflicts",
                icon = Icons.Outlined.MergeType,
                tone = EdgeMenuTone.WARNING,
                badgeText = "${uiState.pendingMergeConflicts.size}",
                onClick = {
                  overflowMenuOpen = false
                  viewModel.openConflictResolver()
                },
              )
            }

            if (activeWs != null) {
              EdgeMenuDivider()
              EdgeMenuSection("Worker & Cloud")
              EdgeMenuItem(
                label = "Rename Worker",
                icon = Icons.Outlined.DriveFileRenameOutline,
                onClick = {
                  overflowMenuOpen = false
                  showRenameWorkerDialog = true
                },
              )
              EdgeMenuItem(
                label = "Workers Subdomain",
                subtitle =
                  if (effectiveSub.isNotBlank()) "$effectiveSub.workers.dev"
                  else "Configure *.workers.dev",
                icon = Icons.Outlined.Language,
                badgeText = "*.dev",
                onClick = {
                  overflowMenuOpen = false
                  showChangeSubdomainDialog = true
                },
              )
              if (!uiState.isLocalScratchpad) {
                EdgeMenuItem(
                  label = "Re-Import from Cloudflare",
                  icon = Icons.Outlined.Refresh,
                  onClick = {
                    overflowMenuOpen = false
                    viewModel.reimportActiveWorkerFromCloudflare()
                  },
                )
              }
            }

            EdgeMenuDivider()
            EdgeMenuSection("Transfer & Git")
            EdgeMenuItem(
              label = "Export Workspace",
              icon = Icons.Outlined.UploadFile,
              badgeText = ".json",
              enabled = activeWs != null,
              onClick = {
                overflowMenuOpen = false
                if (activeWs != null) triggerExportWorkspaceBundle()
              },
            )
            EdgeMenuItem(
              label = "Import Workspace",
              icon = Icons.Outlined.FolderOpen,
              badgeText = ".json",
              onClick = {
                overflowMenuOpen = false
                triggerImportWorkspaceBundle()
              },
            )
            EdgeMenuItem(
              label = "Git Sync",
              subtitle = "Optional repository sync",
              icon = Icons.Outlined.AccountTree,
              badgeText = "GIT",
              onClick = {
                overflowMenuOpen = false
                showProjectGitModal = true
              },
            )
          }
        }

        val anyUiHidden =
          uiState.isTopBarHidden ||
            uiState.isBottomDockHidden ||
            (uiState.projectMode == ProjectMode.CODE &&
              (uiState.isEditorTopChromeHidden || uiState.isEditorBottomBarsHidden))
        val visibleLayersCount =
          listOf(
            !uiState.isTopBarHidden,
            !uiState.isEditorTopChromeHidden,
            !uiState.isEditorBottomBarsHidden,
            !uiState.isBottomDockHidden,
          ).count { it }

        AnimatedVisibility(
          visible = !isFullScreenPreview && anyUiHidden,
          enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
          exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 2 },
          modifier =
            Modifier
              .align(Alignment.BottomEnd)
              .padding(
                end = 14.dp,
                bottom = if (uiState.isBottomDockHidden) 14.dp else 64.dp,
              ),
        ) {
          Row(
            modifier = Modifier.edgeGlassSurface(RoundedCornerShape(999.dp), elevated = true),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier =
                Modifier
                  .clip(RoundedCornerShape(topStart = 999.dp, bottomStart = 999.dp))
                  .clickable { viewModel.toggleAllUiChrome(false) }
                  .padding(start = 11.dp, end = 9.dp, top = 8.dp, bottom = 8.dp)
                  .testTag("show_workspace_bottom_menus_button"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = "Restore all UI layers",
                tint = palette.activePillFg,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = if (visibleLayersCount == 0) "Show UI" else "$visibleLayersCount/4",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
              )
            }
            Box(
              modifier =
                Modifier
                  .width(0.75.dp)
                  .height(16.dp)
                  .background(palette.border.copy(alpha = 0.65f)),
            )
            Box(
              modifier =
                Modifier
                  .clip(RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp))
                  .clickable { overflowMenuOpen = true }
                  .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
                  .testTag("zen_overflow_menu_button"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "Open UI layers & project menu",
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }

        SnackbarHost(
          hostState = snackbarHostState,
          modifier =
            Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = if (uiState.isBottomDockHidden) 18.dp else 64.dp),
        )
      }
    }

    if (isExpanded) {
      workspaceBody()
    } else {
      ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
          ModalDrawerSheet(
            drawerContainerColor = palette.chromeBg,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.width(drawerWidth).statusBarsPadding().navigationBarsPadding(),
          ) {
            projectSidebarContent()
          }
        },
        content = workspaceBody,
      )
    }
  }
}

@Composable
private fun ProjectTestModeWorkspace(
  uiState: IdeUiState,
  viewModel: IdeViewModel,
) {
  val palette = LocalIdePalette.current
  val activeTab =
    when (uiState.activeBottomTab) {
      BottomPanelTab.WORKER_PREVIEW, BottomPanelTab.WORKER_TEST, BottomPanelTab.API_TESTER ->
        uiState.activeBottomTab
      else -> BottomPanelTab.WORKER_PREVIEW
    }

  Column(modifier = Modifier.fillMaxSize().background(palette.chromeBg)) {
    if (!uiState.isPreviewFullScreen) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .edgeGlassDock(shape = RectangleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ModeSegmentChip(
          label = "Preview",
          selected = activeTab == BottomPanelTab.WORKER_PREVIEW,
          tag = "test_segment_preview",
          onClick = { viewModel.openDeployedWorkerPreview(maximizeWindow = true) },
          modifier = Modifier.weight(1f),
        )
        ModeSegmentChip(
          label = "Worker HTTP",
          selected = activeTab == BottomPanelTab.WORKER_TEST,
          tag = "test_segment_worker_http",
          onClick = { viewModel.openWorkerTestWindow(maximizeWindow = true) },
          modifier = Modifier.weight(1f),
        )
        ModeSegmentChip(
          label = "API Tester",
          selected = activeTab == BottomPanelTab.API_TESTER,
          tag = "test_segment_api_tester",
          onClick = { viewModel.toggleBottomPanel(BottomPanelTab.API_TESTER) },
          modifier = Modifier.weight(1f),
        )
        IconButton(
          onClick = { viewModel.setPreviewFullScreen(true) },
          modifier = Modifier.size(30.dp).testTag("test_workspace_fullscreen_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Fullscreen,
            contentDescription = "Fullscreen Preview",
            tint = palette.textSecondary,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
    } else if (activeTab != BottomPanelTab.WORKER_PREVIEW) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(palette.chromeSurface)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = if (activeTab == BottomPanelTab.WORKER_TEST) "Worker HTTP Tester" else "API Tester",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = palette.textPrimary,
        )
        Surface(
          onClick = { viewModel.setPreviewFullScreen(false) },
          color = palette.activePillBg,
          shape = RoundedCornerShape(8.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(Icons.Outlined.FullscreenExit, contentDescription = "Exit Fullscreen", tint = palette.activePillFg, modifier = Modifier.size(15.dp))
            Text("Exit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = palette.activePillFg)
          }
        }
      }
      HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
    }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (activeTab) {
        BottomPanelTab.WORKER_PREVIEW ->
          DeployedWorkerPreviewPane(
            uiState = uiState,
            onSelectModeTab = viewModel::setPreviewModeTab,
            onUrlChange = viewModel::setPreviewUrl,
            onReloadPreview = viewModel::reloadDeployedWorkerPreview,
            onToggleConsoleExpanded = viewModel::togglePreviewConsoleExpanded,
            onUpdateHttpRequest = viewModel::updatePreviewHttpRequest,
            onSendHttpTest = { viewModel.sendPreviewHttpTest() },
            onSelectHistoryRun = viewModel::selectWorkerTestHistoryRun,
            onClearHistory = viewModel::clearWorkerTestHistory,
            onAppendConsoleEntry = viewModel::appendPreviewConsoleEntry,
            onClearConsole = viewModel::clearPreviewConsole,
            onDeployActiveWorker = viewModel::deployActiveWorker,
            onNotifyStatus = viewModel::showStatus,
            isFullScreen = uiState.isPreviewFullScreen,
            onToggleFullScreen = viewModel::togglePreviewFullScreen,
            modifier = Modifier.fillMaxSize(),
          )

        BottomPanelTab.WORKER_TEST ->
          WorkerTestWindow(
            uiState = uiState,
            onUrlChange = viewModel::setPreviewUrl,
            onMethodChange = { method ->
              viewModel.updatePreviewHttpRequest(method, uiState.previewHttpHeadersJson, uiState.previewHttpBody)
            },
            onHeadersJsonChange = { headersJson ->
              viewModel.updatePreviewHttpRequest(uiState.previewHttpMethod, headersJson, uiState.previewHttpBody)
            },
            onBodyChange = { body ->
              viewModel.updatePreviewHttpRequest(uiState.previewHttpMethod, uiState.previewHttpHeadersJson, body)
            },
            onSendRequest = { viewModel.sendPreviewHttpTest() },
            onSelectHistoryRun = viewModel::selectWorkerTestHistoryRun,
            onClearHistory = viewModel::clearWorkerTestHistory,
            onNotifyStatus = viewModel::showStatus,
            modifier = Modifier.fillMaxSize(),
          )

        else ->
          ApiTesterPane(
            uiState = uiState,
            onUpdateRequest = viewModel::updateApiRequest,
            onUpdateEnvVars = viewModel::updateApiEnvVars,
            onRunRequest = { viewModel.runApiTesterRequest() },
            onSaveRequest = viewModel::saveCurrentApiRequestToCollection,
            onLoadRequest = viewModel::loadApiRequest,
            onDeleteCollectionItem = viewModel::deleteApiCollectionItem,
            onClearHistory = viewModel::clearApiHistory,
            onNotifyStatus = viewModel::showStatus,
            onSelectEnvProfile = viewModel::selectApiEnvProfile,
            onCreateEnvProfile = viewModel::createApiEnvProfile,
            onDeleteEnvProfile = viewModel::deleteApiEnvProfile,
            onImportCollection = viewModel::importApiCollection,
            modifier = Modifier.fillMaxSize(),
          )
      }
    }
  }
}

@Composable
private fun ProjectConsoleModeWorkspace(
  uiState: IdeUiState,
  viewModel: IdeViewModel,
) {
  val palette = LocalIdePalette.current
  val activeTab =
    when (uiState.activeBottomTab) {
      BottomPanelTab.TERMINAL, BottomPanelTab.TAIL_LOGS, BottomPanelTab.PROBLEMS, BottomPanelTab.CONSOLE ->
        uiState.activeBottomTab
      else -> BottomPanelTab.TERMINAL
    }

  Column(modifier = Modifier.fillMaxSize().background(palette.chromeBg)) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .edgeGlassDock(shape = RectangleShape)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ModeSegmentChip(
        label = "Terminal",
        selected = activeTab == BottomPanelTab.TERMINAL,
        tag = "console_segment_terminal",
        onClick = { viewModel.toggleBottomPanel(BottomPanelTab.TERMINAL) },
        modifier = Modifier.weight(1f),
      )
      ModeSegmentChip(
        label = "Live Tail",
        selected = activeTab == BottomPanelTab.TAIL_LOGS,
        tag = "console_segment_tail",
        badgeText = if (uiState.isTailRunning) "LIVE" else null,
        badgeColor = EmeraldLive,
        onClick = { viewModel.toggleBottomPanel(BottomPanelTab.TAIL_LOGS) },
        modifier = Modifier.weight(1.05f),
      )
      ModeSegmentChip(
        label = "Problems",
        selected = activeTab == BottomPanelTab.PROBLEMS,
        tag = "console_segment_problems",
        badgeText = uiState.diagnostics.size.takeIf { it > 0 }?.toString(),
        badgeColor = AmberWarn,
        onClick = { viewModel.toggleBottomPanel(BottomPanelTab.PROBLEMS) },
        modifier = Modifier.weight(1.05f),
      )
      ModeSegmentChip(
        label = "Log",
        selected = activeTab == BottomPanelTab.CONSOLE,
        tag = "console_segment_log",
        onClick = { viewModel.toggleBottomPanel(BottomPanelTab.CONSOLE) },
        modifier = Modifier.weight(0.7f),
      )
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.45f))

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (activeTab) {
        BottomPanelTab.TERMINAL ->
          LinuxTerminalPane(
            uiState = uiState,
            onInputChange = viewModel::setTerminalInput,
            onRunCommand = viewModel::runTerminalCommand,
            onPasteText = viewModel::pasteIntoTerminal,
            onAutocomplete = viewModel::autocompleteTerminal,
            onRecallHistory = viewModel::recallTerminalHistory,
            onClearTerminal = viewModel::clearTerminal,
            onSaveEntryToFile = viewModel::saveTerminalOutputToWorkspaceFile,
            onNotifyCopied = viewModel::showStatus,
            modifier = Modifier.fillMaxSize(),
          )

        BottomPanelTab.TAIL_LOGS ->
          LiveTailView(
            uiState = uiState,
            onToggleLiveTail = viewModel::toggleLiveTail,
          )

        BottomPanelTab.PROBLEMS ->
          ProblemsView(
            uiState = uiState,
            onJumpToProblem = viewModel::jumpToLine,
          )

        else ->
          ConsoleView(uiState = uiState)
      }
    }
  }
}

@Composable
private fun ModeSegmentChip(
  label: String,
  selected: Boolean,
  tag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badgeText: String? = null,
  badgeColor: Color? = null,
) {
  val palette = LocalIdePalette.current
  val fg by animateColorAsState(
    targetValue = if (selected) palette.activePillFg else palette.textSecondary,
    animationSpec = tween(160),
    label = "mode_chip_fg",
  )
  val bg by animateColorAsState(
    targetValue = if (selected) palette.activePillBg else palette.glassSurface,
    animationSpec = tween(160),
    label = "mode_chip_bg",
  )

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = bg,
    border =
      if (selected) BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.35f))
      else rememberGlassBorderStroke(0.75.dp),
    modifier = modifier.height(28.dp).testTag(tag),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = fg,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
      )
      if (!badgeText.isNullOrBlank()) {
        Spacer(Modifier.width(4.dp))
        val bColor = badgeColor ?: palette.activePillFg
        Box(
          modifier =
            Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(bColor.copy(alpha = 0.16f))
              .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
          Text(
            text = badgeText,
            fontFamily = JetBrainsMono,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = bColor,
            maxLines = 1,
            softWrap = false,
          )
        }
      }
    }
  }
}
