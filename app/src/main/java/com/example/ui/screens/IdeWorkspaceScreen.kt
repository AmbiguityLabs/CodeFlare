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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Dvr
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.data.local.ZipImportTargetMode
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
import com.example.ui.components.IconRevealButton
import com.example.ui.components.IconSegmentTab
import com.example.ui.components.IconToggleRevealText
import com.example.ui.components.IdeCoreSheetTab
import com.example.ui.components.IdeSearchAndHistorySheet
import com.example.ui.components.LiveTailView
import com.example.ui.components.LocalScriptRunPane
import com.example.ui.components.NewDirectoryDialog
import com.example.ui.components.NewModuleFileDialog
import com.example.ui.components.NewWorkerDialog
import com.example.ui.components.PaletteCommand
import com.example.ui.components.ProblemsView
import com.example.ui.components.ProjectFilesSidebar
import com.example.ui.components.RenameFileDialog
import com.example.ui.components.RenameWorkerDialog
import com.example.ui.components.SmartRenameSymbolDialog
import com.example.ui.components.WorkerTestWindow
import com.example.ui.components.ZipImportPreviewDialog
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.LocalPopupMenuStyle
import com.example.ui.theme.UiStyleVersion
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
  var activeIdeSheetTab by remember { mutableStateOf<IdeCoreSheetTab?>(null) }
  var showRenameSymbolDialog by remember { mutableStateOf(false) }
  var renameSymbolInitial by remember { mutableStateOf("") }
  var editorCommandRequest by remember { mutableStateOf<Pair<Long, String>?>(null) }

  fun dispatchEditorCommand(cmd: String) {
    viewModel.setProjectMode(ProjectMode.CODE)
    editorCommandRequest = System.nanoTime() to cmd
  }

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

  val importZipNewProjectLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.inspectZipForImport(uri, ZipImportTargetMode.NEW_PROJECT)
    }

  val importZipMergeLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.inspectZipForImport(uri, ZipImportTargetMode.MERGE_INTO_ACTIVE)
    }

  val linkExternalFolderLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) viewModel.linkExternalProjectFolder(uri)
    }

  val triggerSingleUpload = { singleFileUploadLauncher.launch(arrayOf("*/*")) }
  val triggerBatchUpload = { batchFileUploadLauncher.launch(arrayOf("*/*")) }
  val triggerImportZipAsNewProject = {
    importZipNewProjectLauncher.launch(
      arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"),
    )
  }
  val triggerImportZipIntoActive = {
    importZipMergeLauncher.launch(
      arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"),
    )
  }
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
    val showUndo =
      uiState.lastUndoableFileOpLabel != null &&
        (msg.startsWith("Removed '") || msg.startsWith("Renamed '"))
    val res =
      snackbarHostState.showSnackbar(
        message = msg,
        actionLabel = if (showUndo) "Undo" else null,
        duration = if (showUndo) SnackbarDuration.Long else SnackbarDuration.Short,
      )
    viewModel.clearStatusMessage()
    if (res == SnackbarResult.ActionPerformed && showUndo) {
      viewModel.undoLastFileOperation()
    }
  }

  LaunchedEffect(uiState.deployBannerMessage) {
    val msg = uiState.deployBannerMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.dismissDeployBanner()
  }

  BackHandler {
    when {
      activeIdeSheetTab != null -> activeIdeSheetTab = null
      showRenameSymbolDialog -> showRenameSymbolDialog = false
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
        viewModel.dismissZipImportPreview()
      },
      onCreateFromTemplate = { name, template ->
        showNewWorkerDialog = false
        pickedNewWorkerUris = emptyList()
        viewModel.dismissZipImportPreview()
        viewModel.createNewWorker(name, template)
      },
      onCreateFromFiles = { name, uris, folderPrefix ->
        showNewWorkerDialog = false
        pickedNewWorkerUris = emptyList()
        viewModel.dismissZipImportPreview()
        viewModel.createNewWorkerFromUris(name, uris, folderPrefix)
      },
      zipPreview = uiState.zipImportPreview,
      onPickZip = triggerImportZipAsNewProject,
      onToggleZipStripRoot = viewModel::toggleZipPreviewStripRoot,
      onClearPickedZip = viewModel::dismissZipImportPreview,
      onCreateFromZip = { name ->
        showNewWorkerDialog = false
        pickedNewWorkerUris = emptyList()
        viewModel.confirmImportZipAsNewProject(name)
      },
    )
  }

  uiState.zipImportPreview?.let { preview ->
    if (!showNewWorkerDialog) {
      ZipImportPreviewDialog(
        preview = preview,
        activeWorkerName = uiState.workspace?.scriptName,
        isLocalScratchpad = uiState.isLocalScratchpad || uiState.credentials == null,
        onToggleStripRoot = viewModel::toggleZipPreviewStripRoot,
        onDismiss = viewModel::dismissZipImportPreview,
        onConfirmNewProject = { name, deployNow ->
          viewModel.confirmImportZipAsNewProject(name, deployNow)
        },
        onConfirmMergeIntoActive = { prefix, deploy ->
          viewModel.confirmMergeZipIntoActiveWorkspace(prefix, deploy)
        },
      )
    }
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

  activeIdeSheetTab?.let { sheetTab ->
    IdeSearchAndHistorySheet(
      initialTab = sheetTab,
      activeFilePath = uiState.activeFile?.path,
      activeFileContent = uiState.activeTextFieldValue.text,
      onSearchSymbols = viewModel::searchProjectSymbols,
      onFindInFiles = viewModel::findInWorkspaceFiles,
      onLoadHistory = viewModel::loadActiveFileHistory,
      onReadSnapshot = viewModel::readActiveFileHistorySnapshot,
      onRestoreSnapshot = viewModel::restoreActiveFileFromHistory,
      onJumpToLine = { path, line, col -> viewModel.jumpToLine(path, line, col) },
      onDismiss = { activeIdeSheetTab = null },
    )
  }

  if (showRenameSymbolDialog) {
    SmartRenameSymbolDialog(
      initialSymbol = renameSymbolInitial,
      onPreview = viewModel::previewSymbolRename,
      onConfirmRename = viewModel::applySmartRenameSymbol,
      onDismiss = { showRenameSymbolDialog = false },
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
          title = file.path,
          subtitle = if (file.isEntryPoint) "MAIN" else "FILE",
        ) {
          viewModel.openFileTab(file.path, recordHistory = true)
          viewModel.setProjectMode(ProjectMode.CODE)
        }
      }
    val commands =
      listOf(
        PaletteCommand("Fold All", "EDITOR") { dispatchEditorCommand("fold_all") },
        PaletteCommand("Unfold All", "EDITOR") { dispatchEditorCommand("unfold_all") },
        PaletteCommand("Toggle Fold", "EDITOR") { dispatchEditorCommand("toggle_fold") },
        PaletteCommand("Toggle Bookmark", "EDITOR") { dispatchEditorCommand("toggle_bookmark") },
        PaletteCommand("Next Bookmark", "EDITOR") { dispatchEditorCommand("next_bookmark") },
        PaletteCommand("Clipboard History", "EDITOR") { dispatchEditorCommand("paste_history") },
        PaletteCommand("Go to Symbol", "NAV") {
          activeIdeSheetTab = IdeCoreSheetTab.SYMBOLS
        },
        PaletteCommand("Find in Files", "SEARCH") {
          activeIdeSheetTab = IdeCoreSheetTab.FIND_IN_FILES
        },
        PaletteCommand("Local History", "HIST") {
          activeIdeSheetTab = IdeCoreSheetTab.LOCAL_HISTORY
        },
        PaletteCommand("Rename Symbol", "REF") {
          renameSymbolInitial = ""
          showRenameSymbolDialog = true
        },
        PaletteCommand("Code Mode", "MODE") {
          viewModel.setProjectMode(ProjectMode.CODE)
        },
        PaletteCommand("Run in CLI", "CLI") {
          viewModel.runWorkspaceLocally(openTestWorkspace = true)
        },
        PaletteCommand("Worker Preview", "TEST") {
          viewModel.openDeployedWorkerPreview(maximizeWindow = true)
        },
        PaletteCommand("Preview Fullscreen", "VIEW") {
          if (uiState.projectMode != ProjectMode.TEST) {
            viewModel.openDeployedWorkerPreview(maximizeWindow = true)
            viewModel.setPreviewFullScreen(true)
          } else {
            viewModel.togglePreviewFullScreen()
          }
        },
        PaletteCommand("HTTP Tester", "TEST") {
          viewModel.openWorkerTestWindow(maximizeWindow = true)
        },
        PaletteCommand("API Tester", "TEST") {
          viewModel.toggleBottomPanel(BottomPanelTab.API_TESTER)
        },
        PaletteCommand("Project Config", "CONFIG") {
          viewModel.setProjectMode(ProjectMode.CONFIG)
        },
        PaletteCommand("Problems (${uiState.diagnostics.size})", "LINT") {
          viewModel.toggleBottomPanel(BottomPanelTab.PROBLEMS)
        },
        PaletteCommand("Zoom In (+15%)", "ZOOM") {
          viewModel.adjustEditorZoom(15)
        },
        PaletteCommand("Zoom Out (-15%)", "ZOOM") {
          viewModel.adjustEditorZoom(-15)
        },
        PaletteCommand("Reset Zoom (100%)", "ZOOM") {
          viewModel.resetEditorZoom()
        },
        PaletteCommand("Live Tail Logs", "LOGS") {
          viewModel.toggleLiveTail()
        },
        PaletteCommand("IDE Log", "LOGS") {
          viewModel.toggleBottomPanel(BottomPanelTab.CONSOLE)
        },
        PaletteCommand(
          title =
            if (
              uiState.isTopBarHidden &&
              uiState.isEditorTopChromeHidden &&
              uiState.isEditorBottomBarsHidden &&
              uiState.isBottomDockHidden
            ) "Show All UI (4/4)"
            else "Zen Mode",
          subtitle = "VIEW",
        ) {
          val allHidden =
            uiState.isTopBarHidden &&
              uiState.isEditorTopChromeHidden &&
              uiState.isEditorBottomBarsHidden &&
              uiState.isBottomDockHidden
          viewModel.toggleAllUiChrome(!allHidden)
        },
        PaletteCommand("Deploy Worker", "CLOUD") {
          viewModel.deployActiveWorker()
        },
        PaletteCommand("Rename Worker", "CLOUD") {
          if (uiState.workspace != null) showRenameWorkerDialog = true
        },
        PaletteCommand("Workers Subdomain", "CLOUD") {
          showChangeSubdomainDialog = true
        },
        PaletteCommand("Review Diff & Deploy", "DIFF") {
          viewModel.openDiffReview(DiffReviewIntent.PRE_DEPLOY)
        },
        PaletteCommand("Diff Viewer", "DIFF") {
          viewModel.openDiffReview(DiffReviewIntent.WORKSPACE)
        },
        PaletteCommand("Merge Conflicts", "GIT") {
          viewModel.openConflictResolver()
        },
        PaletteCommand("Re-Import Worker", "CLOUD") {
          viewModel.reimportActiveWorkerFromCloudflare()
        },
        PaletteCommand("Revert Workspace", "DIFF") {
          viewModel.revertWorkspaceToImportedBaseline()
        },
        PaletteCommand("Export Bundle (.json)", "IO") {
          if (uiState.workspace != null) triggerExportWorkspaceBundle()
        },
        PaletteCommand("Import Bundle (.json)", "IO") {
          triggerImportWorkspaceBundle()
        },
        PaletteCommand("Import ZIP Project", "IO") {
          triggerImportZipAsNewProject()
        },
        PaletteCommand("Import ZIP into Files", "IO") {
          if (uiState.workspace != null) triggerImportZipIntoActive()
        },
        PaletteCommand("Upload File", "IO") {
          if (uiState.workspace != null) triggerSingleUpload()
        },
        PaletteCommand("Batch Upload", "IO") {
          if (uiState.workspace != null) triggerBatchUpload()
        },
        PaletteCommand("Export Active File", "IO") {
          if (uiState.activeFile != null) triggerExportActiveFile()
        },
        PaletteCommand("Sync wrangler.jsonc", "CONFIG") {
          viewModel.syncWranglerFileToWorkspace()
        },
        PaletteCommand("Format Document", "EDITOR") {
          viewModel.formatActiveFile()
        },
        PaletteCommand("Git Sync", "GIT") {
          showProjectGitModal = true
        },
        PaletteCommand("Settings", "APP") {
          showProjectSettingsModal = true
        },
        PaletteCommand("All Projects", "HOME") {
          viewModel.closeWorkspaceToHome()
        },
      ) + fileCommands
    CommandPaletteDialog(commands = commands, onDismiss = { showCommandPalette = false })
  }

  val editorBarStyle = uiState.editorSettings.editorBarStyle.normalized()
  val isV2Workspace = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val dockClearanceBottom =
    if (isV2Workspace) (editorBarStyle.heightDp + editorBarStyle.bottomMarginDp + 14).dp
    else (editorBarStyle.heightDp + 10).dp

  CompositionLocalProvider(LocalPopupMenuStyle provides uiState.editorSettings.editorMenuStyle) {
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
        onImportZipIntoProject = triggerImportZipIntoActive,
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
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            IconButton(
              onClick = viewModel::closeWorkspaceToHome,
              modifier = Modifier.size(34.dp).testTag("back_to_home_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back to Home",
                tint = palette.textPrimary,
                modifier = Modifier.size(18.dp),
              )
            }

            Row(
              modifier =
                Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(6.dp))
                  .clickable { showCommandPalette = true }
                  .padding(horizontal = 4.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = uiState.workspace?.scriptName ?: "Project",
                style = MaterialTheme.typography.titleSmall,
                fontSize = 13.5.sp,
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
              val fileCount = uiState.workspace?.files?.count { !it.isVirtualReadOnly } ?: 0
              val dirtyCount = uiState.workspace?.files?.count { it.isDirty } ?: 0
              IconToggleRevealText(
                icon = Icons.Outlined.Info,
                text =
                  when {
                    dirtyCount > 0 -> "$fileCount files · $dirtyCount edited"
                    uiState.isLocalScratchpad -> "$fileCount files · CLI"
                    else -> "$fileCount files · Synced"
                  },
                tint = if (dirtyCount > 0) AmberWarn else palette.textMuted,
              )
            }

            if (uiState.workspace != null) {
              Row(
                modifier = Modifier.weight(1.1f, fill = false).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (!isExpanded) {
                  IconRevealButton(
                    icon = Icons.Outlined.FolderOpen,
                    revealedLabel = "Files",
                    minimal = true,
                    onClick = { scope.launch { drawerState.open() } },
                    height = 28.dp,
                    iconSize = 16.dp,
                    tag = "workspace_quick_files_button",
                  )
                }

                IconRevealButton(
                  icon = Icons.Outlined.PlayArrow,
                  revealedLabel = "Run CLI",
                  minimal = true,
                  isBusy = uiState.isRunningLocalScript,
                  active = uiState.projectMode == ProjectMode.TEST && uiState.activeBottomTab == BottomPanelTab.LOCAL_RUN,
                  onClick = { viewModel.runWorkspaceLocally(openTestWorkspace = true) },
                  height = 28.dp,
                  iconSize = 16.dp,
                  tag = "workspace_local_run_button",
                )

                if (uiState.isLocalScratchpad) {
                  IconRevealButton(
                    icon = Icons.Outlined.CloudOff,
                    revealedLabel = "Connect",
                    minimal = true,
                    active = true,
                    onClick = viewModel::exitScratchpadToAuth,
                    height = 28.dp,
                    iconSize = 16.dp,
                    tag = "connect_cloudflare_button",
                  )
                } else {
                  IconRevealButton(
                    icon = Icons.Outlined.CloudUpload,
                    revealedLabel = if (uiState.hasUnsavedChanges) "Deploy*" else "Deploy",
                    minimal = true,
                    active = uiState.hasUnsavedChanges,
                    isBusy = uiState.isDeploying,
                    onClick = { if (!uiState.isDeploying) viewModel.deployActiveWorker() },
                    height = 28.dp,
                    iconSize = 16.dp,
                    tag = "deploy_worker_button",
                  )
                }
              }
            }

            IconButton(
              onClick = { overflowMenuOpen = true },
              modifier = Modifier.size(34.dp).testTag("project_overflow_menu_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "Project actions",
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          if (uiState.isOpeningWorker || uiState.isDeploying) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
          }
          HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.5.dp)
          }
          }

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
                      onRegisterFlushHook = viewModel::registerEditorFlushHook,
                      onComputeCompletions = viewModel::completeAtCursor,
                      onAcceptCompletion = viewModel::noteCompletionAccepted,
                      onRequestSmartRename = { ident ->
                        renameSymbolInitial = ident.orEmpty()
                        showRenameSymbolDialog = true
                      },
                      onOpenLocalHistory = { activeIdeSheetTab = IdeCoreSheetTab.LOCAL_HISTORY },
                      onOpenProjectSearch = { activeIdeSheetTab = IdeCoreSheetTab.FIND_IN_FILES },
                      onRunLocalScript = { viewModel.runWorkspaceLocally(openTestWorkspace = true) },
                      editorCommandRequest = editorCommandRequest,
                      modifier = Modifier.fillMaxSize(),
                    )
                  }

                  ProjectMode.TEST -> {
                    ProjectTestModeWorkspace(
                      uiState = uiState,
                      viewModel = viewModel,
                      onLinkProjectFolder = { linkExternalFolderLauncher.launch(null) },
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

          AnimatedVisibility(
            visible = !isFullScreenPreview && !uiState.isBottomDockHidden,
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(140)),
          ) {
            EdgeBottomDock(
              barStyle = editorBarStyle,
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
                    icon = Icons.Outlined.Dvr,
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
              badgeText = if (activeWs != null) "$menuFileCount" else null,
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
                      icon = Icons.Outlined.Bolt,
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
                    subtitle = "",
                    visible = !uiState.isTopBarHidden,
                    testTag = "ui_layer_toggle_top_bar",
                    onToggle = viewModel::toggleTopBarHidden,
                  ),
                  EdgeMenuUiLayer(
                    code = "L2",
                    label = "Tabs & Path",
                    subtitle = "",
                    visible = !uiState.isEditorTopChromeHidden,
                    testTag = "ui_layer_toggle_editor_top",
                    onToggle = viewModel::toggleEditorTopChromeHidden,
                  ),
                  EdgeMenuUiLayer(
                    code = "L3",
                    label = "Editor Bar",
                    subtitle = "",
                    visible = !uiState.isEditorBottomBarsHidden,
                    testTag = "ui_layer_toggle_editor_bottom",
                    onToggle = viewModel::toggleEditorBottomBarsHidden,
                  ),
                  EdgeMenuUiLayer(
                    code = "L4",
                    label = "Bottom Dock",
                    subtitle = "",
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
                icon = Icons.Outlined.Bolt,
                badgeText = "⌘K",
                onClick = {
                  overflowMenuOpen = false
                  showCommandPalette = true
                },
              )
            }
            if (activeWs != null) {
              EdgeMenuItem(
                label = "Go to Symbol",
                icon = Icons.Outlined.Tag,
                badgeText = "SYM",
                onClick = {
                  overflowMenuOpen = false
                  activeIdeSheetTab = IdeCoreSheetTab.SYMBOLS
                },
              )
              EdgeMenuItem(
                label = "Find in Files",
                icon = Icons.Outlined.Search,
                badgeText = "FIND",
                onClick = {
                  overflowMenuOpen = false
                  activeIdeSheetTab = IdeCoreSheetTab.FIND_IN_FILES
                },
              )
              EdgeMenuItem(
                label = "Local History",
                icon = Icons.Outlined.History,
                badgeText = "HIST",
                onClick = {
                  overflowMenuOpen = false
                  activeIdeSheetTab = IdeCoreSheetTab.LOCAL_HISTORY
                },
              )
              EdgeMenuItem(
                label = "Rename Symbol",
                icon = Icons.Outlined.DriveFileRenameOutline,
                badgeText = "REF",
                onClick = {
                  overflowMenuOpen = false
                  renameSymbolInitial = ""
                  showRenameSymbolDialog = true
                },
              )
              if (uiState.activeFilePath != null && uiState.activeFilePath in uiState.externalFileUris) {
                EdgeMenuItem(
                  label = "Save to Original File",
                  icon = Icons.Outlined.Save,
                  tone = EdgeMenuTone.ACCENT,
                  badgeText = "URI",
                  onClick = {
                    overflowMenuOpen = false
                    viewModel.saveActiveExternalFileNow()
                  },
                )
              }
              uiState.lastUndoableFileOpLabel?.let { opLabel ->
                EdgeMenuItem(
                  label = "Undo $opLabel",
                  icon = Icons.AutoMirrored.Outlined.Undo,
                  tone = EdgeMenuTone.ACCENT,
                  badgeText = "UNDO",
                  onClick = {
                    overflowMenuOpen = false
                    viewModel.undoLastFileOperation()
                  },
                )
              }
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
                subtitle = effectiveSub.takeIf { it.isNotBlank() }?.let { "$it.workers.dev" },
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
              label = "Import ZIP Project",
              icon = Icons.Outlined.Archive,
              badgeText = ".zip",
              onClick = {
                overflowMenuOpen = false
                triggerImportZipAsNewProject()
              },
            )
            EdgeMenuItem(
              label = "Import ZIP into Files",
              icon = Icons.Outlined.Archive,
              badgeText = "ADD",
              enabled = activeWs != null,
              onClick = {
                overflowMenuOpen = false
                if (activeWs != null) triggerImportZipIntoActive()
              },
            )
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
                label = "Link External Folder",
                icon = Icons.Outlined.FolderOpen,
                badgeText = "TREE",
                enabled = activeWs != null,
                onClick = {
                  overflowMenuOpen = false
                  if (activeWs != null) linkExternalFolderLauncher.launch(null)
                },
              )
              EdgeMenuItem(
                label = "Git Sync",
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
                bottom = if (uiState.isBottomDockHidden) 14.dp else dockClearanceBottom,
              ),
        ) {
          Row(
            modifier =
              Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(palette.chromeSurface)
                .then(
                  if (palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED) Modifier
                  else Modifier.edgeGlassSurface(RoundedCornerShape(999.dp), elevated = true),
                ),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier
                  .clip(RoundedCornerShape(topStart = 999.dp, bottomStart = 999.dp))
                  .clickable { viewModel.toggleAllUiChrome(false) }
                  .padding(horizontal = 12.dp, vertical = 9.dp)
                  .testTag("show_workspace_bottom_menus_button"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = "Restore all UI layers ($visibleLayersCount/4)",
                tint = palette.activePillFg,
                modifier = Modifier.size(18.dp),
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
                  .padding(horizontal = 10.dp, vertical = 9.dp)
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
              .padding(bottom = if (uiState.isBottomDockHidden) 18.dp else dockClearanceBottom),
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
}

@Composable
private fun ProjectTestModeWorkspace(
  uiState: IdeUiState,
  viewModel: IdeViewModel,
  onLinkProjectFolder: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val activeTab =
    when (uiState.activeBottomTab) {
      BottomPanelTab.LOCAL_RUN, BottomPanelTab.WORKER_PREVIEW, BottomPanelTab.WORKER_TEST, BottomPanelTab.API_TESTER ->
        uiState.activeBottomTab
      else -> BottomPanelTab.LOCAL_RUN
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
        IconSegmentTab(
          icon = Icons.Outlined.Terminal,
          label = "CLI",
          selected = activeTab == BottomPanelTab.LOCAL_RUN,
          tag = "test_segment_local_run",
          onClick = { viewModel.toggleBottomPanel(BottomPanelTab.LOCAL_RUN) },
          modifier = Modifier.weight(1f),
        )
        IconSegmentTab(
          icon = Icons.Outlined.Visibility,
          label = "Preview",
          selected = activeTab == BottomPanelTab.WORKER_PREVIEW,
          tag = "test_segment_preview",
          onClick = { viewModel.openDeployedWorkerPreview(maximizeWindow = true) },
          modifier = Modifier.weight(1f),
        )
        IconSegmentTab(
          icon = Icons.Outlined.Http,
          label = "HTTP",
          selected = activeTab == BottomPanelTab.WORKER_TEST,
          tag = "test_segment_worker_http",
          onClick = { viewModel.openWorkerTestWindow(maximizeWindow = true) },
          modifier = Modifier.weight(1f),
        )
        IconSegmentTab(
          icon = Icons.Outlined.Api,
          label = "API",
          selected = activeTab == BottomPanelTab.API_TESTER,
          tag = "test_segment_api_tester",
          onClick = { viewModel.toggleBottomPanel(BottomPanelTab.API_TESTER) },
          modifier = Modifier.weight(1f),
        )
        IconButton(
          onClick = { viewModel.setPreviewFullScreen(true) },
          modifier = Modifier.size(32.dp).testTag("test_workspace_fullscreen_button"),
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text =
            when (activeTab) {
              BottomPanelTab.LOCAL_RUN -> "CLI Terminal"
              BottomPanelTab.WORKER_TEST -> "Worker HTTP"
              else -> "API Tester"
            },
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = palette.textPrimary,
        )
        IconRevealButton(
          icon = Icons.Outlined.FullscreenExit,
          revealedLabel = "Exit",
          active = true,
          onClick = { viewModel.setPreviewFullScreen(false) },
          height = 30.dp,
        )
      }
      HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
    }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (activeTab) {
        BottomPanelTab.LOCAL_RUN ->
          LocalScriptRunPane(
            uiState = uiState,
            onPathChange = { p -> viewModel.updateLocalRunRequest(path = p) },
            onMethodChange = { m -> viewModel.updateLocalRunRequest(method = m) },
            onBodyChange = { b -> viewModel.updateLocalRunRequest(body = b) },
            onRunLocally = { viewModel.runWorkspaceLocally(openTestWorkspace = true) },
            onNotifyStatus = viewModel::showStatus,
            onExecuteCliCommand = viewModel::executeCliInput,
            modifier = Modifier.fillMaxSize(),
          )

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
            onSwitchToCode = {
              viewModel.setProjectMode(ProjectMode.CODE)
              viewModel.setPreviewFullScreen(false)
            },
            onLinkProjectFolder = onLinkProjectFolder,
            onToggleViewport = viewModel::togglePreviewViewport,
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
      BottomPanelTab.TAIL_LOGS, BottomPanelTab.PROBLEMS, BottomPanelTab.CONSOLE ->
        uiState.activeBottomTab
      else -> BottomPanelTab.TAIL_LOGS
    }

  Column(modifier = Modifier.fillMaxSize().background(palette.chromeBg)) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .edgeGlassDock(shape = RectangleShape)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconSegmentTab(
        icon = Icons.Outlined.Sensors,
        label = "Live Tail",
        selected = activeTab == BottomPanelTab.TAIL_LOGS,
        tag = "console_segment_tail",
        badgeText = if (uiState.isTailRunning) "LIVE" else null,
        badgeColor = EmeraldLive,
        onClick = { viewModel.toggleBottomPanel(BottomPanelTab.TAIL_LOGS) },
        modifier = Modifier.weight(1f),
      )
      IconSegmentTab(
        icon = Icons.Outlined.WarningAmber,
        label = "Problems",
        selected = activeTab == BottomPanelTab.PROBLEMS,
        tag = "console_segment_problems",
        badgeText = uiState.diagnostics.size.takeIf { it > 0 }?.toString(),
        badgeColor = AmberWarn,
        onClick = { viewModel.toggleBottomPanel(BottomPanelTab.PROBLEMS) },
        modifier = Modifier.weight(1f),
      )
      IconSegmentTab(
        icon = Icons.Outlined.Dvr,
        label = "Log",
        selected = activeTab == BottomPanelTab.CONSOLE,
        tag = "console_segment_log",
        onClick = { viewModel.toggleBottomPanel(BottomPanelTab.CONSOLE) },
        modifier = Modifier.weight(1f),
      )
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.45f))

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (activeTab) {
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
