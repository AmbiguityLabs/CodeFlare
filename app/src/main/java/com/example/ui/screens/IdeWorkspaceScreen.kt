package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.ActivitySidebar
import com.example.ui.components.BatchUploadOptionsDialog
import com.example.ui.components.BottomPanel
import com.example.ui.components.CodeEditorPane
import com.example.ui.components.CommandPaletteDialog
import com.example.ui.components.NewDirectoryDialog
import com.example.ui.components.NewModuleFileDialog
import com.example.ui.components.NewWorkerDialog
import com.example.ui.components.PaletteCommand
import com.example.ui.components.RenameFileDialog
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LocalIdePalette
import com.example.ui.viewmodel.BottomPanelTab
import com.example.ui.viewmodel.IdeUiState
import com.example.ui.viewmodel.IdeViewModel
import com.example.ui.viewmodel.SidebarSection
import kotlinx.coroutines.launch

@Composable
fun IdeWorkspaceScreen(
  uiState: IdeUiState,
  viewModel: IdeViewModel,
) {
  val palette = LocalIdePalette.current
  val context = LocalContext.current
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var showNewWorkerDialog by remember { mutableStateOf(false) }
  var pickedNewWorkerUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
  var newFileDialogPrefix by remember { mutableStateOf<String?>(null) }
  var newFileAutoRun by remember { mutableStateOf(false) }
  var newDirDialogBase by remember { mutableStateOf<String?>(null) }
  var showCommandPalette by remember { mutableStateOf(false) }
  var pendingUploadUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
  var pendingRenamePath by remember { mutableStateOf<String?>(null) }

  // Zero-permission Storage Access Framework (SAF) launchers
  val singleFileUploadLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        pendingUploadUris = listOf(uri)
      }
    }

  val batchFileUploadLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        pendingUploadUris = uris
      }
    }

  val importAndRunLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.importAndRunFilesDirectly(uris)
      }
    }

  val newWorkerFilesLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        pickedNewWorkerUris = uris
      }
    }

  val exportFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
      if (uri != null) {
        viewModel.exportActiveFileToUri(uri)
      }
    }

  val pagesFolderLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
      if (treeUri != null) {
        viewModel.stagePagesFolder(treeUri)
      }
    }

  val pagesZipLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { zipUri ->
      if (zipUri != null) {
        viewModel.stagePagesZip(zipUri)
      }
    }

  val pagesFilesLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.stagePagesFiles(uris)
      }
    }

  val triggerSingleUpload = { singleFileUploadLauncher.launch(arrayOf("*/*")) }
  val triggerBatchUpload = { batchFileUploadLauncher.launch(arrayOf("*/*")) }
  val triggerImportAndRun = { importAndRunLauncher.launch(arrayOf("*/*")) }
  val triggerCreateAndRun = {
    newFileAutoRun = true
    newFileDialogPrefix = ""
  }
  val triggerExportActiveFile = {
    val fileName = uiState.activeFile?.fileName ?: "index.js"
    exportFileLauncher.launch(fileName)
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

  BackHandler(enabled = drawerState.isOpen || uiState.showBottomPanel || uiState.showFindBar) {
    when {
      drawerState.isOpen -> scope.launch { drawerState.close() }
      uiState.showFindBar -> viewModel.toggleFindBar(false)
      uiState.showBottomPanel -> viewModel.hideBottomPanel()
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

  newFileDialogPrefix?.let { prefix ->
    NewModuleFileDialog(
      initialPrefix = prefix,
      defaultRunLocally = newFileAutoRun,
      onDismiss = {
        newFileDialogPrefix = null
        newFileAutoRun = false
      },
      onCreate = { path, runLocally ->
        newFileDialogPrefix = null
        newFileAutoRun = false
        viewModel.addModuleFile(path, autoRunLocally = runLocally)
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

  if (showCommandPalette) {
    val commands =
      listOf(
        PaletteCommand("Import File & Run Locally (No Deploy)", "Open a local script & execute immediately") {
          triggerImportAndRun()
        },
        PaletteCommand("Create & Run Script Locally", "Write a new script and preview result before deploying") {
          triggerCreateAndRun()
        },
        PaletteCommand("Run Active Script Locally", "Execute unsaved buffer in V8/shell right now") {
          viewModel.runActiveFileLocally()
        },
        PaletteCommand("Deploy Worker to Cloudflare", "Publish all module edits & deletions live") {
          viewModel.deployActiveWorker()
        },
        PaletteCommand("Create New Worker", "Scaffold from template or upload local files") {
          showNewWorkerDialog = true
        },
        PaletteCommand("Upload Single File to Worker", "Pick one local script/asset file via Android picker") {
          if (uiState.workspace != null) triggerSingleUpload() else triggerImportAndRun()
        },
        PaletteCommand("Batch Upload Multiple Files", "Import multiple module scripts at once into Worker") {
          if (uiState.workspace != null) triggerBatchUpload() else triggerImportAndRun()
        },
        PaletteCommand("Export Active File", "Save current script out to device storage") {
          if (uiState.activeFile != null) triggerExportActiveFile()
        },
        PaletteCommand("Open API Tester (Postman)", "Test HTTP endpoints, headers, auth presets & collections") {
          viewModel.toggleBottomPanel(BottomPanelTab.API_TESTER)
        },
        PaletteCommand("Open Linux API Terminal", "Run curl, jq, wrangler, and Linux commands") {
          viewModel.toggleBottomPanel(BottomPanelTab.TERMINAL)
        },
        PaletteCommand("Sync wrangler.jsonc to Cloudflare", "Apply wrangler.jsonc/.toml bindings, vars & crons to workspace") {
          viewModel.syncWranglerFileToWorkspace()
        },
        PaletteCommand("Generate GitHub Auto-Deploy Action", "Create .github/workflows/deploy-cloudflare.yml for auto-deploy") {
          viewModel.generateCloudflareGitHubActionWorkflow()
        },
        PaletteCommand("Workers Infra (KV, D1, R2, Domains)", "Manage Bindings, KV, D1 SQL, R2, Cron, Rollback") {
          viewModel.setSidebarSection(SidebarSection.BINDINGS)
          scope.launch { drawerState.open() }
        },
        PaletteCommand("Cloudflare Pages", "Manage Pages projects, deployments, logs & domains") {
          viewModel.setSidebarSection(SidebarSection.PAGES)
          scope.launch { drawerState.open() }
        },
        PaletteCommand("Git Source Control & Clone", "Clone GitHub repos, stage/commit changes, push & deploy Worker") {
          viewModel.setSidebarSection(SidebarSection.GITHUB)
          scope.launch { drawerState.open() }
        },
        PaletteCommand("Worker Snippets", "Insert reusable Workers JS snippets") {
          viewModel.setSidebarSection(SidebarSection.SNIPPETS)
          scope.launch { drawerState.open() }
        },
        PaletteCommand("Toggle Live Tail Logs", "Stream real-time console.log() via WebSocket") {
          viewModel.toggleLiveTail()
        },
        PaletteCommand("Format Document", "Auto-indent JavaScript or JSON") {
          viewModel.formatActiveFile()
        },
        PaletteCommand("Toggle Dark / Light Theme", "Switch Material You theme mode") {
          viewModel.updateSettings { it.copy(darkTheme = !it.darkTheme) }
        },
      )
    CommandPaletteDialog(commands = commands, onDismiss = { showCommandPalette = false })
  }

  BoxWithConstraints(
    modifier =
      Modifier
        .fillMaxSize()
        .background(palette.chromeBg),
  ) {
    val isExpanded = maxWidth >= 840.dp
    val drawerWidth = (maxWidth * 0.78f).coerceIn(270.dp, 326.dp)

    val sidebarContent: @Composable () -> Unit = {
      ActivitySidebar(
        uiState = uiState,
        onSelectSection = viewModel::setSidebarSection,
        onSelectAccount = viewModel::selectAccount,
        onRefreshWorkers = viewModel::refreshWorkers,
        onOpenWorker = viewModel::openWorker,
        onShowNewWorkerDialog = { showNewWorkerDialog = true },
        onDeleteWorker = viewModel::deleteWorker,
        onOpenFile = { path -> viewModel.openFileTab(path, recordHistory = true) },
        onRunFileLocally = { path ->
          viewModel.openFileTab(path, recordHistory = true)
          viewModel.runActiveFileLocally(path)
        },
        onShowNewFileDialog = { prefix ->
          newFileAutoRun = false
          newFileDialogPrefix = prefix
        },
        onShowNewDirectoryDialog = { base -> newDirDialogBase = base },
        onImportAndRunFile = triggerImportAndRun,
        onUploadSingleFile = triggerSingleUpload,
        onUploadBatchFiles = triggerBatchUpload,
        onExportActiveFile = triggerExportActiveFile,
        onDeleteModuleFile = viewModel::deleteModuleFile,
        onRequestRenameModuleFile = { path -> pendingRenamePath = path },
        onSetEntryModule = viewModel::setEntryModule,
        onDeployActiveWorker = viewModel::deployActiveWorker,
        onInsertSnippet = { snippet -> viewModel.insertTextAtCursor(snippet, 0) },
        onJumpToLine = viewModel::jumpToLine,
        onSearchQueryChange = viewModel::setSearchQuery,
        onReplaceInAllFiles = viewModel::replaceInAllFiles,
        onRevertFile = viewModel::revertFile,
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
        onRefreshPages = viewModel::loadPagesProjects,
        onSelectPagesProject = viewModel::selectPagesProject,
        onCreatePagesProject = viewModel::createPagesProject,
        onDeletePagesProject = viewModel::deletePagesProject,
        onTriggerPagesDeploy = viewModel::triggerPagesDeployment,
        onRetryPagesDeploy = viewModel::retryPagesDeployment,
        onRollbackPagesDeploy = viewModel::rollbackPagesDeployment,
        onFetchPagesLogs = viewModel::fetchPagesLogs,
        onSavePagesEnvVar = viewModel::savePagesEnvVar,
        onUpdatePagesBuildConfig = viewModel::updatePagesBuildConfig,
        onUpdatePagesBindings = viewModel::updatePagesBindings,
        onCreatePagesSpecialFile = viewModel::createPagesSpecialFile,
        onAddPagesDomain = viewModel::addPagesDomain,
        onDeletePagesDomain = viewModel::deletePagesDomain,
        onPickPagesFolder = { pagesFolderLauncher.launch(null) },
        onPickPagesZip = { pagesZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) },
        onPickPagesFiles = { pagesFilesLauncher.launch(arrayOf("*/*")) },
        onStageWorkspaceForPages = viewModel::stageCurrentWorkspaceForPages,
        onTogglePagesZipStripRoot = viewModel::togglePagesZipStripRoot,
        onClearStagedPagesAssets = viewModel::clearStagedPagesAssets,
        onDeployDirectPages = viewModel::deployStagedPagesAssets,
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
        onRefreshGitHubRepos = viewModel::refreshGitHubRepos,
        onSelectGitHubRepo = viewModel::selectGitHubRepo,
        onSelectGitHubBranch = viewModel::selectGitHubBranch,
        onCreateGitHubBranch = viewModel::createGitHubBranch,
        onCloneRepository = { url, branch, merge ->
          viewModel.cloneGitHubRepository(url, branch, merge)
        },
        onCreateGitHubRepo = viewModel::createGitHubRepoAndLink,
        onLinkSelectedGitHubRepo = viewModel::linkCurrentWorkerToSelectedGitHubRepo,
        onPullGitHubFiles = viewModel::pullGitHubPathsIntoWorker,
        onSyncPullLinkedGitRepo = viewModel::syncPullLinkedGitRepo,
        onSetGitCommitMessage = viewModel::setGitCommitMessage,
        onToggleGitFileStaged = viewModel::toggleGitFileStaged,
        onStageAllGitChanges = viewModel::stageAllGitChanges,
        onUnstageAllGitChanges = viewModel::unstageAllGitChanges,
        onDiscardGitFileChange = viewModel::discardGitFileChange,
        onCommitGitLocally = { viewModel.commitStagedChangesLocally() },
        onPushGitToGitHub = { viewModel.pushQueuedAndActiveChangesToGitHub() },
        onInspectGitHubCommit = viewModel::inspectGitHubCommit,
        onInspectPullRequest = viewModel::inspectPullRequest,
        onCreatePullRequest = viewModel::createPullRequest,
        onMergePullRequest = viewModel::mergePullRequest,
        onCreateGitHubIssue = viewModel::createGitHubIssue,
        onToggleGitHubIssueState = viewModel::toggleGitHubIssueState,
        onCommentOnGitHubIssue = viewModel::commentOnGitHubIssue,
        onDispatchGitHubWorkflow = viewModel::dispatchGitHubWorkflow,
        onRerunGitHubWorkflowRun = viewModel::rerunGitHubWorkflowRun,
        onInspectWorkflowRunJobs = viewModel::inspectWorkflowRunJobs,
        onSubmitPrReview = viewModel::submitPullRequestReview,
        onGenerateCfActionWorkflow = viewModel::generateCloudflareGitHubActionWorkflow,
        onRenameWorkerTarget = viewModel::renameActiveWorkerTarget,
        onUpdateSettings = viewModel::updateSettings,
        onSignOut = {
          if (uiState.isLocalScratchpad || uiState.isGitHubIdeOnlyMode) viewModel.exitScratchpadToAuth()
          else viewModel.signOut()
        },
        onCloseDrawerIfCompact = {
          if (!isExpanded) scope.launch { drawerState.close() }
        },
      )
    }

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
          // Screenshot-matched Olive-Sage Top Toolbar
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .background(palette.chromeBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (!isExpanded) {
              IconButton(
                onClick = {
                  scope.launch {
                    if (drawerState.isOpen) drawerState.close() else drawerState.open()
                  }
                },
                modifier = Modifier.size(38.dp).testTag("toggle_sidebar_button"),
              ) {
                Icon(
                  imageVector =
                    if (drawerState.isOpen) Icons.AutoMirrored.Outlined.ArrowBack
                    else Icons.Outlined.Menu,
                  contentDescription = stringResource(R.string.cd_toggle_sidebar),
                  tint = palette.textPrimary,
                )
              }
            }

            // Green-Olive Split Layout Pill (matching screenshot top-right icon!)
            Surface(
              onClick = { viewModel.toggleBottomPanel(null) },
              shape = RoundedCornerShape(10.dp),
              color = palette.activePillBg,
              modifier = Modifier.size(width = 40.dp, height = 32.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Outlined.VerticalSplit,
                  contentDescription = "Toggle Bottom Panel",
                  tint = palette.activePillFg,
                  modifier = Modifier.size(18.dp),
                )
              }
            }

            Spacer(Modifier.weight(1f))

            IconButton(
              onClick = { showCommandPalette = true },
              modifier = Modifier.size(38.dp).testTag("command_palette_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = stringResource(R.string.cd_command_palette),
                tint = palette.textSecondary,
                modifier = Modifier.size(19.dp),
              )
            }

            if (uiState.workspace != null) {
              IconButton(
                onClick = { viewModel.runActiveFileLocally() },
                modifier = Modifier.size(38.dp).testTag("top_run_script_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.PlayArrow,
                  contentDescription = "Run active script locally without deploying",
                  tint = palette.activePillFg,
                  modifier = Modifier.size(21.dp),
                )
              }

              if (uiState.isLocalScratchpad) {
                Surface(
                  onClick = viewModel::exitScratchpadToAuth,
                  shape = RoundedCornerShape(12.dp),
                  color = palette.activePillBg,
                  modifier = Modifier.testTag("connect_cloudflare_button"),
                ) {
                  Text(
                    text = "Connect",
                    color = palette.activePillFg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                  )
                }
              } else {
                Surface(
                  onClick = { if (!uiState.isDeploying) viewModel.deployActiveWorker() },
                  shape = RoundedCornerShape(12.dp),
                  color =
                    if (uiState.hasUnsavedChanges) palette.activePillBg
                    else palette.chromePill,
                  modifier = Modifier.testTag("deploy_worker_button"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
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
          }

          if (uiState.isOpeningWorker || uiState.isDeploying) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
          }

          // Main Split Row: Docked Sidebar (Expanded) + Editor + Bottom Panel
          Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isExpanded) {
              Box(modifier = Modifier.width(336.dp).fillMaxHeight()) {
                sidebarContent()
              }
              VerticalDivider(color = palette.border)
            }

            val terminalMaximized = uiState.showBottomPanel && uiState.isBottomPanelExpanded
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
              if (!terminalMaximized) {
                CodeEditorPane(
                  uiState = uiState,
                  onSelectTab = { path -> viewModel.openFileTab(path, recordHistory = true) },
                  onCloseTab = viewModel::closeFileTab,
                  onNavigateTabBack = viewModel::navigateTabBack,
                  onNavigateTabForward = viewModel::navigateTabForward,
                  onImportAndRunFile = triggerImportAndRun,
                  onCreateAndRunFile = triggerCreateAndRun,
                  onRunActiveFile = { viewModel.runActiveFileLocally() },
                  onCodeChange = viewModel::updateEditorValue,
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
                  onReplaceAll = viewModel::replaceAllMatches,
                  onOpenProblems = { viewModel.toggleBottomPanel(BottomPanelTab.PROBLEMS) },
                  onOpenExplorer = {
                    viewModel.setSidebarSection(SidebarSection.EXPLORER)
                    if (!isExpanded) scope.launch { drawerState.open() }
                  },
                  onShowNewWorkerDialog = { showNewWorkerDialog = true },
                  onDismissDraftBanner = viewModel::dismissDraftBanner,
                  onDiscardRestoredDraft = viewModel::discardRestoredDraft,
                  onJumpToLine = viewModel::jumpToLine,
                  onSyncWranglerToWorkspace = viewModel::syncWranglerFileToWorkspace,
                  modifier = Modifier.weight(1f),
                )
              }

              AnimatedVisibility(
                visible = uiState.showBottomPanel,
                modifier = if (terminalMaximized) Modifier.weight(1f) else Modifier,
              ) {
                BottomPanel(
                  uiState = uiState,
                  onSelectTab = { viewModel.toggleBottomPanel(it) },
                  onToggleExpand = viewModel::toggleBottomPanelExpanded,
                  onClose = viewModel::hideBottomPanel,
                  onRunActiveFileLocally = { viewModel.runActiveFileLocally() },
                  onImportAndRunFile = triggerImportAndRun,
                  onCreateAndRunFile = triggerCreateAndRun,
                  onUpdateRunRequest = viewModel::updateRunRequest,
                  onUpdateApiRequest = viewModel::updateApiRequest,
                  onUpdateApiEnvVars = viewModel::updateApiEnvVars,
                  onRunApiRequest = { viewModel.runApiTesterRequest() },
                  onSaveApiRequest = viewModel::saveCurrentApiRequestToCollection,
                  onLoadApiRequest = viewModel::loadApiRequest,
                  onDeleteApiCollectionItem = viewModel::deleteApiCollectionItem,
                  onClearApiHistory = viewModel::clearApiHistory,
                  onTerminalInputChange = viewModel::setTerminalInput,
                  onRunTerminalCommand = viewModel::runTerminalCommand,
                  onPasteIntoTerminal = viewModel::pasteIntoTerminal,
                  onAutocompleteTerminal = viewModel::autocompleteTerminal,
                  onRecallTerminalHistory = viewModel::recallTerminalHistory,
                  onClearTerminal = viewModel::clearTerminal,
                  onSaveTerminalOutputToFile = viewModel::saveTerminalOutputToWorkspaceFile,
                  onNotifyStatus = viewModel::showStatus,
                  onJumpToProblem = viewModel::jumpToLine,
                  onToggleLiveTail = viewModel::toggleLiveTail,
                  modifier = if (terminalMaximized) Modifier.fillMaxSize() else Modifier,
                )
              }
            }
          }

          // Bottom Action Bar (Run, API, Terminal, Tail, Search, Problems, Prefs)
          HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .background(palette.chromeBg)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            BottomBarToolButton(
              icon = Icons.Outlined.PlayArrow,
              label = "Run",
              active = uiState.showBottomPanel && uiState.activeBottomTab == BottomPanelTab.RUN_PREVIEW,
              onClick = {
                if (uiState.activeFile?.isVirtualReadOnly == false) {
                  viewModel.runActiveFileLocally()
                } else {
                  viewModel.toggleBottomPanel(BottomPanelTab.RUN_PREVIEW)
                }
              },
            )
            BottomBarToolButton(
              icon = Icons.AutoMirrored.Outlined.Send,
              label = "API",
              active = uiState.showBottomPanel && uiState.activeBottomTab == BottomPanelTab.API_TESTER,
              onClick = { viewModel.toggleBottomPanel(BottomPanelTab.API_TESTER) },
            )
            BottomBarToolButton(
              icon = Icons.Outlined.Terminal,
              label = "Terminal",
              active = uiState.showBottomPanel && uiState.activeBottomTab == BottomPanelTab.TERMINAL,
              onClick = { viewModel.toggleBottomPanel(BottomPanelTab.TERMINAL) },
            )
            BottomBarToolButton(
              icon = Icons.Outlined.Sensors,
              label = if (uiState.isTailRunning) "Tail •" else "Tail",
              active =
                uiState.isTailRunning ||
                  (uiState.showBottomPanel && uiState.activeBottomTab == BottomPanelTab.TAIL_LOGS),
              highlightLive = uiState.isTailRunning,
              onClick = { viewModel.toggleBottomPanel(BottomPanelTab.TAIL_LOGS) },
            )
            BottomBarToolButton(
              icon = Icons.Outlined.Search,
              label = "Search",
              active = uiState.showFindBar,
              onClick = { viewModel.toggleFindBar() },
            )
            BottomBarToolButton(
              icon = Icons.Outlined.WarningAmber,
              label = "Problems",
              active = uiState.showBottomPanel && uiState.activeBottomTab == BottomPanelTab.PROBLEMS,
              onClick = { viewModel.toggleBottomPanel(BottomPanelTab.PROBLEMS) },
            )
            BottomBarToolButton(
              icon = Icons.Outlined.Settings,
              label = "Prefs",
              active = uiState.sidebarSection == SidebarSection.SETTINGS && drawerState.isOpen,
              onClick = {
                viewModel.setSidebarSection(SidebarSection.SETTINGS)
                if (!isExpanded) scope.launch { drawerState.open() }
              },
            )
          }
        }

        SnackbarHost(
          hostState = snackbarHostState,
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 58.dp),
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
            sidebarContent()
          }
        },
        content = workspaceBody,
      )
    }
  }
}

@Composable
private fun BottomBarToolButton(
  icon: ImageVector,
  label: String,
  active: Boolean,
  highlightLive: Boolean = false,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val tint =
    when {
      highlightLive -> EmeraldLive
      active -> palette.activePillFg
      else -> palette.textSecondary
    }
  Column(
    modifier =
      Modifier
        .clip(RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = tint,
      modifier = Modifier.size(19.dp),
    )
    Text(
      text = label,
      fontSize = 10.sp,
      color = tint,
      maxLines = 1,
    )
  }
}
