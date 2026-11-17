package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.RamWorkspaceRecord
import com.example.data.remote.CfWorkerScript
import com.example.ui.components.CloudflareInfraPanel
import com.example.ui.components.CloudflarePagesPanel
import com.example.ui.components.EdgeBottomDock
import com.example.ui.components.EdgeBottomDockItem
import com.example.ui.components.GitHubAndDiffPanel
import com.example.ui.components.NewWorkerDialog
import com.example.ui.components.SettingsPanel
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.viewmodel.HomeTab
import com.example.ui.viewmodel.IdeUiState
import com.example.ui.viewmodel.IdeViewModel

@Composable
fun HomeScreen(
  uiState: IdeUiState,
  viewModel: IdeViewModel,
) {
  val palette = LocalIdePalette.current
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  var projectSearch by remember { mutableStateOf("") }
  var accountMenuOpen by remember { mutableStateOf(false) }
  var showNewProjectDialog by remember { mutableStateOf(false) }
  var pickedNewWorkerUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
  var pendingDeleteCloudWorker by remember { mutableStateOf<String?>(null) }
  var pendingDeleteLocalKey by remember { mutableStateOf<Pair<String, String>?>(null) }

  val newWorkerFilesLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) {
        pickedNewWorkerUris = uris
      }
    }

  val importWorkspaceBundleLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.importWorkspaceBundleFromUri(uri)
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

  val showingGitHubOverlay = uiState.showHomeGitHubHub || (uiState.isGitHubIdeOnlyMode && uiState.workspace == null)

  BackHandler(enabled = showingGitHubOverlay || uiState.showHomeSettings) {
    when {
      uiState.showHomeSettings -> viewModel.setShowHomeSettings(false)
      uiState.isGitHubIdeOnlyMode && uiState.credentials == null -> viewModel.exitScratchpadToAuth()
      showingGitHubOverlay -> viewModel.setShowHomeGitHubHub(false)
    }
  }

  if (showNewProjectDialog) {
    NewWorkerDialog(
      pickedUris = pickedNewWorkerUris,
      onPickFiles = { newWorkerFilesLauncher.launch(arrayOf("*/*")) },
      onClearPickedFiles = { pickedNewWorkerUris = emptyList() },
      onDismiss = {
        showNewProjectDialog = false
        pickedNewWorkerUris = emptyList()
      },
      onCreateFromTemplate = { name, template ->
        showNewProjectDialog = false
        pickedNewWorkerUris = emptyList()
        viewModel.createNewWorker(name, template)
      },
      onCreateFromFiles = { name, uris, folderPrefix ->
        showNewProjectDialog = false
        pickedNewWorkerUris = emptyList()
        viewModel.createNewWorkerFromUris(name, uris, folderPrefix)
      },
    )
  }

  pendingDeleteCloudWorker?.let { scriptName ->
    AlertDialog(
      onDismissRequest = { pendingDeleteCloudWorker = null },
      title = { Text("Delete Worker?") },
      text = {
        Text("Permanently remove '$scriptName' from Cloudflare? This cannot be undone.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteWorker(scriptName)
            pendingDeleteCloudWorker = null
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteCloudWorker = null }) { Text("Cancel") }
      },
    )
  }

  pendingDeleteLocalKey?.let { (key, title) ->
    AlertDialog(
      onDismissRequest = { pendingDeleteLocalKey = null },
      title = { Text("Remove Project from RAM?") },
      text = { Text("Remove '$title' from on-device RAM? Any unexported edits will be discarded.") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteLocalRamWorkspace(key)
            pendingDeleteLocalKey = null
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Remove")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteLocalKey = null }) { Text("Cancel") }
      },
    )
  }

  if (uiState.showHomeSettings) {
    Dialog(
      onDismissRequest = { viewModel.setShowHomeSettings(false) },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(
        color = palette.chromeBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 28.dp),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "IDE Preferences",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = palette.textPrimary,
            )
            IconButton(onClick = { viewModel.setShowHomeSettings(false) }) {
              Icon(Icons.Outlined.Close, contentDescription = "Close preferences", tint = palette.textSecondary)
            }
          }
          HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
          Box(modifier = Modifier.height(480.dp)) {
            SettingsPanel(
              uiState = uiState,
              onUpdateSettings = viewModel::updateSettings,
              onSignOut = {
                viewModel.setShowHomeSettings(false)
                if (uiState.isLocalScratchpad || uiState.isGitHubIdeOnlyMode) viewModel.exitScratchpadToAuth()
                else viewModel.signOut()
              },
            )
          }
        }
      }
    }
  }

  // Optional Full-Screen GitHub Hub Overlay (kept completely separate from primary Projects & Editor)
  if (showingGitHubOverlay) {
    Scaffold(
      containerColor = palette.chromeBg,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        Column(modifier = Modifier.background(palette.chromeBg).statusBarsPadding()) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            IconButton(
              onClick = {
                if (uiState.isGitHubIdeOnlyMode && uiState.credentials == null) {
                  viewModel.exitScratchpadToAuth()
                } else {
                  viewModel.setShowHomeGitHubHub(false)
                }
              },
              modifier = Modifier.testTag("github_hub_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back to Home",
                tint = palette.textPrimary,
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "GitHub Hub",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
              )
              Text(
                text =
                  uiState.gitHubCreds?.login?.let { "@$it · Optional Git integration" }
                    ?: "Optional source control, clone & pull requests",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
              )
            }
            if (uiState.isGitHubBusy) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            }
          }
          HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
        }
      },
    ) { innerPadding ->
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .imePadding(),
      ) {
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
          onOpenFileInEditor = { path -> viewModel.openFileTab(path, recordHistory = true) },
          onRenameWorkerTarget = viewModel::renameActiveWorkerTarget,
          onDeployActiveWorker = viewModel::deployActiveWorker,
          onRevertFile = viewModel::revertFile,
          onCloseDrawerIfCompact = {},
        )
      }
    }
    return
  }

  Scaffold(
    containerColor = palette.chromeBg,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      Column(modifier = Modifier.background(palette.chromeBg).statusBarsPadding()) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "EdgeCode",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary,
            )
            if (!uiState.isLocalScratchpad && uiState.accounts.isNotEmpty()) {
              Box {
                Row(
                  modifier =
                    Modifier
                      .clip(RoundedCornerShape(8.dp))
                      .clickable { accountMenuOpen = true }
                      .padding(vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = uiState.selectedAccount?.name ?: "Select Account",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.activePillFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Switch account",
                    tint = palette.activePillFg,
                    modifier = Modifier.size(16.dp),
                  )
                }
                DropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                  uiState.accounts.forEach { acc ->
                    DropdownMenuItem(
                      text = {
                        Text(
                          text = acc.name,
                          fontWeight = if (acc.id == uiState.selectedAccount?.id) FontWeight.Bold else FontWeight.Normal,
                        )
                      },
                      onClick = {
                        accountMenuOpen = false
                        viewModel.selectAccount(acc)
                      },
                    )
                  }
                }
              }
            } else {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "Local Mode (On-device RAM)",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textMuted,
                )
                Text(
                  text = "Connect →",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                  modifier =
                    Modifier
                      .clip(RoundedCornerShape(6.dp))
                      .clickable(onClick = viewModel::exitScratchpadToAuth)
                      .padding(horizontal = 4.dp, vertical = 1.dp),
                )
              }
            }
          }

          // Optional GitHub Hub trigger (distinct & unobtrusive)
          Surface(
            onClick = { viewModel.setShowHomeGitHubHub(true) },
            shape = RoundedCornerShape(10.dp),
            color = palette.chromeSurface,
            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
            modifier = Modifier.testTag("home_github_hub_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.AccountTree,
                contentDescription = "Optional GitHub Hub",
                tint = if (uiState.gitHubCreds != null) EmeraldLive else palette.textSecondary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "GitHub",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
              )
            }
          }

          IconButton(
            onClick = { viewModel.setShowHomeSettings(true) },
            modifier = Modifier.size(38.dp).testTag("home_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Settings,
              contentDescription = "IDE Preferences",
              tint = palette.textSecondary,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        if (uiState.isOpeningWorker || uiState.isLoadingWorkers) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
      }
    },
    bottomBar = {
      EdgeBottomDock(
        modifier = Modifier.background(palette.chromeSurface).navigationBarsPadding(),
        items =
          listOf(
            EdgeBottomDockItem(
              label = "Projects",
              icon = Icons.Outlined.Code,
              selected = uiState.homeTab == HomeTab.PROJECTS,
              testTag = "home_tab_projects",
              onClick = { viewModel.setHomeTab(HomeTab.PROJECTS) },
            ),
            EdgeBottomDockItem(
              label = "Pages",
              icon = Icons.Outlined.Web,
              selected = uiState.homeTab == HomeTab.PAGES,
              testTag = "home_tab_pages",
              onClick = { viewModel.setHomeTab(HomeTab.PAGES) },
            ),
            EdgeBottomDockItem(
              label = "Infra",
              icon = Icons.Outlined.Storage,
              selected = uiState.homeTab == HomeTab.INFRA,
              testTag = "home_tab_infra",
              onClick = { viewModel.setHomeTab(HomeTab.INFRA) },
            ),
          ),
      )
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .imePadding(),
    ) {
      when (uiState.homeTab) {
        HomeTab.PROJECTS ->
          HomeProjectsTabContent(
            uiState = uiState,
            searchQuery = projectSearch,
            onSearchChange = { projectSearch = it },
            onNewProject = { showNewProjectDialog = true },
            onImportBundle = {
              importWorkspaceBundleLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onOpenRamWorkspace = viewModel::openRamWorkspace,
            onRequestDeleteRamWorkspace = { rec ->
              pendingDeleteLocalKey = rec.key to rec.workingWorkspace.scriptName
            },
            onRefreshWorkers = viewModel::refreshWorkers,
            onOpenWorker = { script -> viewModel.openWorker(script) },
            onRequestDeleteWorker = { script -> pendingDeleteCloudWorker = script.id },
          )

        HomeTab.PAGES ->
          CloudflarePagesPanel(
            uiState = uiState,
            onRefreshProjects = viewModel::loadPagesProjects,
            onSelectProject = viewModel::selectPagesProject,
            onCreateProject = viewModel::createPagesProject,
            onDeleteProject = viewModel::deletePagesProject,
            onTriggerDeploy = viewModel::triggerPagesDeployment,
            onRetryDeploy = viewModel::retryPagesDeployment,
            onRollbackDeploy = viewModel::rollbackPagesDeployment,
            onFetchLogs = viewModel::fetchPagesLogs,
            onSaveEnvVar = viewModel::savePagesEnvVar,
            onUpdateBuildConfig = viewModel::updatePagesBuildConfig,
            onUpdateBindings = viewModel::updatePagesBindings,
            onCreatePagesSpecialFile = viewModel::createPagesSpecialFile,
            onAddDomain = viewModel::addPagesDomain,
            onDeleteDomain = viewModel::deletePagesDomain,
            onPickPagesFolder = { pagesFolderLauncher.launch(null) },
            onPickPagesZip = {
              pagesZipLauncher.launch(
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"),
              )
            },
            onPickPagesFiles = { pagesFilesLauncher.launch(arrayOf("*/*")) },
            onStageWorkspaceForPages = viewModel::stageCurrentWorkspaceForPages,
            onTogglePagesZipStripRoot = viewModel::togglePagesZipStripRoot,
            onClearStagedPagesAssets = viewModel::clearStagedPagesAssets,
            onDeployDirectPages = viewModel::deployStagedPagesAssets,
          )

        HomeTab.INFRA ->
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
          )
      }
    }
  }
}

@Composable
private fun HomeProjectsTabContent(
  uiState: IdeUiState,
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  onNewProject: () -> Unit,
  onImportBundle: () -> Unit,
  onOpenRamWorkspace: (String) -> Unit,
  onRequestDeleteRamWorkspace: (RamWorkspaceRecord) -> Unit,
  onRefreshWorkers: () -> Unit,
  onOpenWorker: (CfWorkerScript) -> Unit,
  onRequestDeleteWorker: (CfWorkerScript) -> Unit,
) {
  val palette = LocalIdePalette.current
  val q = searchQuery.trim()
  val ramWorkspaces =
    remember(uiState.ramWorkspaces, q) {
      val list =
        if (q.isEmpty()) uiState.ramWorkspaces
        else uiState.ramWorkspaces.filter { it.workingWorkspace.scriptName.contains(q, ignoreCase = true) }
      list.sortedByDescending { it.importedAtEpochMs }
    }
  val cloudWorkers =
    remember(uiState.workers, q) {
      if (q.isEmpty()) uiState.workers
      else uiState.workers.filter { it.id.contains(q, ignoreCase = true) }
    }
  val ramByName = remember(uiState.ramWorkspaces) { uiState.ramWorkspaces.associateBy { it.workingWorkspace.scriptName } }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Search & Create Toolbar
    item(key = "home_actions") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchChange,
          placeholder = { Text("Search projects…") },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchChange("") }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("home_project_search"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = onNewProject,
            shape = RoundedCornerShape(12.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = palette.activePillBg,
                contentColor = palette.activePillFg,
              ),
            modifier = Modifier.weight(1f).height(44.dp).testTag("home_new_project_button"),
          ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("New Project", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
          }

          OutlinedButton(
            onClick = onImportBundle,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.65f)),
            modifier = Modifier.height(44.dp).testTag("home_import_bundle_button"),
          ) {
            Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import .json", fontSize = 13.sp)
          }
        }
      }
    }

    // On-device RAM / Local Projects section
    item(key = "ram_header") {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "ON-DEVICE PROJECTS (${ramWorkspaces.size})",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = palette.textMuted,
        )
        if (uiState.ramDbStats.estimatedRamKb > 0) {
          Text(
            text = "${uiState.ramDbStats.estimatedRamKb} KB in RAM",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = palette.textMuted,
          )
        }
      }
    }

    if (ramWorkspaces.isEmpty()) {
      item(key = "ram_empty") {
        Surface(
          color = palette.chromeSurface,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "No projects opened yet",
              style = MaterialTheme.typography.titleSmall,
              color = palette.textPrimary,
            )
            Text(
              text = "Create a new project, import a .json workspace bundle, or select a Worker below to start editing.",
              style = MaterialTheme.typography.bodySmall,
              color = palette.textSecondary,
            )
          }
        }
      }
    } else {
      items(ramWorkspaces, key = { "ram_${it.key}" }) { rec ->
        RamProjectCard(
          record = rec,
          onOpen = { onOpenRamWorkspace(rec.key) },
          onDelete = { onRequestDeleteRamWorkspace(rec) },
        )
      }
    }

    // Remote Cloudflare Workers section (when signed into Cloudflare)
    if (!uiState.isLocalScratchpad && uiState.credentials != null) {
      item(key = "cf_header") {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "CLOUDFLARE WORKERS (${cloudWorkers.size})",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.textMuted,
          )
          IconButton(
            onClick = onRefreshWorkers,
            enabled = !uiState.isLoadingWorkers,
            modifier = Modifier.size(32.dp).testTag("home_refresh_workers_button"),
          ) {
            Icon(
              Icons.Outlined.Refresh,
              contentDescription = "Refresh Workers",
              tint = palette.textSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      if (cloudWorkers.isEmpty() && !uiState.isLoadingWorkers) {
        item(key = "cf_empty") {
          Surface(
            color = palette.chromeSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = if (q.isEmpty()) "No Cloudflare Workers found" else "No matching Workers",
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
              )
              Text(
                text =
                  if (q.isEmpty()) "Tap 'New Project' above to create and deploy your first Worker."
                  else "Try clearing the search filter or refreshing your account Workers.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
              )
            }
          }
        }
      } else {
        items(cloudWorkers, key = { "cf_${it.id}" }) { script ->
          val cached = ramByName[script.id]
          CloudWorkerCard(
            script = script,
            cachedRecord = cached,
            onOpen = { onOpenWorker(script) },
            onDelete = { onRequestDeleteWorker(script) },
          )
        }
      }
    }

    item(key = "home_bottom_spacer") { Spacer(Modifier.height(12.dp)) }
  }
}

@Composable
private fun RamProjectCard(
  record: RamWorkspaceRecord,
  onOpen: () -> Unit,
  onDelete: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val ws = record.workingWorkspace
  val fileCount = ws.files.count { !it.isVirtualReadOnly }
  val isLocal = record.key.startsWith("local_")
  val dirty = record.hasPendingChanges

  Surface(
    onClick = onOpen,
    color = palette.chromeSurface,
    shape = RoundedCornerShape(14.dp),
    border =
      BorderStroke(
        width = 1.dp,
        color = if (dirty) AmberWarn.copy(alpha = 0.55f) else palette.border.copy(alpha = 0.45f),
      ),
    modifier = Modifier.fillMaxWidth().testTag("home_ram_card_${ws.scriptName}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        color = palette.activePillBg.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = palette.activePillFg,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = ws.scriptName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          StatusTag(
            label =
              when {
                dirty -> "Edited"
                isLocal -> "Local"
                else -> "RAM"
              },
            color =
              when {
                dirty -> AmberWarn
                isLocal -> palette.activePillFg
                else -> EmeraldLive
              },
          )
        }
        Text(
          text = "$fileCount file(s) · entry ${ws.entryModulePath}",
          fontFamily = JetBrainsMono,
          fontSize = 11.sp,
          color = palette.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(34.dp).testTag("delete_ram_project_${ws.scriptName}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.DeleteOutline,
          contentDescription = "Remove ${ws.scriptName}",
          tint = palette.textMuted,
          modifier = Modifier.size(18.dp),
        )
      }

      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = "Open project",
        tint = palette.textSecondary,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun CloudWorkerCard(
  script: CfWorkerScript,
  cachedRecord: RamWorkspaceRecord?,
  onOpen: () -> Unit,
  onDelete: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val hasRamEdits = cachedRecord?.hasPendingChanges == true

  Surface(
    onClick = onOpen,
    color = palette.chromeSurface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag("home_worker_card_${script.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        color = palette.chromePill,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.CloudQueue,
            contentDescription = null,
            tint = if (cachedRecord != null) EmeraldLive else palette.textSecondary,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = script.id,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (hasRamEdits) {
            StatusTag(label = "Edited", color = AmberWarn)
          } else if (cachedRecord != null) {
            StatusTag(label = "Cached", color = EmeraldLive)
          }
        }
        Text(
          text =
            cachedRecord?.let { "${it.workingWorkspace.files.count { f -> !f.isVirtualReadOnly }} files in RAM" }
              ?: script.modifiedOn?.take(10)?.let { "Updated $it · Tap to open" }
              ?: "Cloudflare Worker · Tap to open",
          fontFamily = JetBrainsMono,
          fontSize = 11.sp,
          color = palette.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(34.dp).testTag("delete_worker_${script.id}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.DeleteOutline,
          contentDescription = "Delete ${script.id}",
          tint = palette.textMuted,
          modifier = Modifier.size(18.dp),
        )
      }

      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = "Open ${script.id}",
        tint = palette.textSecondary,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun StatusTag(
  label: String,
  color: Color,
) {
  Surface(
    color = color.copy(alpha = 0.16f),
    shape = RoundedCornerShape(6.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color))
      Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
      )
    }
  }
}
