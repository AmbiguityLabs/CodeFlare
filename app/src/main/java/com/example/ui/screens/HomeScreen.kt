package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RamWorkspaceRecord
import com.example.data.remote.CfWorkerScript
import com.example.ui.components.ChangeSubdomainDialog
import com.example.ui.components.CloudflareInfraPanel
import com.example.ui.components.CloudflarePagesPanel
import com.example.ui.components.EdgeBottomDock
import com.example.ui.components.EdgeBottomDockItem
import com.example.ui.components.GitHubAndDiffPanel
import com.example.ui.components.NewWorkerDialog
import com.example.ui.components.RenameWorkerDialog
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.edgeGlassSurface
import com.example.ui.theme.rememberGlassBorderStroke
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
  val requestStorageAccess =
    com.example.ui.components.rememberStorageAccessRequester {
      viewModel.refreshStoragePermissionAndReload()
    }

  var projectSearch by remember { mutableStateOf("") }
  var accountMenuOpen by remember { mutableStateOf(false) }
  var showNewProjectDialog by remember { mutableStateOf(false) }
  var pickedNewWorkerUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
  var pendingDeleteCloudWorker by remember { mutableStateOf<String?>(null) }
  var pendingDeleteLocalKey by remember { mutableStateOf<Pair<String, String>?>(null) }
  var pendingRenameTarget by remember { mutableStateOf<HomeRenameTarget?>(null) }
  var showChangeSubdomainDialog by remember { mutableStateOf(false) }

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
      title = { Text("Delete Project?") },
      text = { Text("Delete '$title' from your EdgeCode project directory? Any unpushed edits will be removed.") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteLocalRamWorkspace(key)
            pendingDeleteLocalKey = null
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteLocalKey = null }) { Text("Cancel") }
      },
    )
  }

  pendingRenameTarget?.let { target ->
    RenameWorkerDialog(
      currentName = target.currentName,
      subdomain = uiState.accountSubdomain,
      isRemoteWorker = target.isRemoteWorker,
      hasEncryptedSecrets = target.hasEncryptedSecrets,
      onDismiss = { pendingRenameTarget = null },
      onConfirm = { newName, deleteOld ->
        pendingRenameTarget = null
        viewModel.renameExistingWorker(
          newScriptNameRaw = newName,
          targetRamKey = target.ramKey,
          remoteScriptName = target.remoteScriptName,
          deleteOldRemote = deleteOld,
        )
      },
    )
  }

  if (showChangeSubdomainDialog) {
    ChangeSubdomainDialog(
      currentSubdomain = uiState.accountSubdomain,
      sampleWorkerName = uiState.workspace?.scriptName ?: uiState.workers.firstOrNull()?.id.orEmpty(),
      isLocalMode = uiState.isLocalScratchpad || uiState.credentials == null,
      onDismiss = { showChangeSubdomainDialog = false },
      onConfirm = { newSub ->
        showChangeSubdomainDialog = false
        viewModel.updateAccountWorkersSubdomain(newSub)
      },
    )
  }

  if (uiState.showHomeSettings) {
    Box(modifier = Modifier.fillMaxSize()) {
      SettingsScreen(
        uiState = uiState,
        onBack = { viewModel.setShowHomeSettings(false) },
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
          viewModel.setShowHomeSettings(false)
          if (uiState.isLocalScratchpad || uiState.isGitHubIdeOnlyMode) viewModel.exitScratchpadToAuth()
          else viewModel.signOut()
        },
        onOpenGitHubHub = {
          viewModel.setShowHomeSettings(false)
          viewModel.setShowHomeGitHubHub(true)
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
              .padding(horizontal = 20.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text =
                when (uiState.homeTab) {
                  HomeTab.PROJECTS -> "Your projects"
                  HomeTab.PAGES -> "Cloudflare Pages"
                  HomeTab.INFRA -> "Edge Infrastructure"
                },
              fontSize = 24.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = (-0.3).sp,
              color = palette.textPrimary,
            )
            if (!uiState.isLocalScratchpad && uiState.accounts.isNotEmpty()) {
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box {
                  Surface(
                    onClick = { accountMenuOpen = true },
                    shape = RoundedCornerShape(999.dp),
                    color = palette.glassSurface,
                    border = rememberGlassBorderStroke(0.75.dp),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                      Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(EmeraldLive))
                      Text(
                        text =
                          uiState.selectedAccount?.name?.let(::formatAccountDisplayName)
                            ?: "Account",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Switch account",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(15.dp),
                      )
                    }
                  }
                  DropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                    uiState.accounts.forEach { acc ->
                      DropdownMenuItem(
                        text = {
                          Text(
                            text = formatAccountDisplayName(acc.name),
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

                Surface(
                  onClick = { showChangeSubdomainDialog = true },
                  shape = RoundedCornerShape(999.dp),
                  color = palette.activePillBg.copy(alpha = 0.55f),
                  border = rememberGlassBorderStroke(0.75.dp),
                  modifier = Modifier.testTag("home_subdomain_chip"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      Icons.Outlined.Language,
                      contentDescription = null,
                      tint = palette.activePillFg,
                      modifier = Modifier.size(13.dp),
                    )
                    Text(
                      text =
                        if (uiState.accountSubdomain.isNotBlank()) "${uiState.accountSubdomain}.workers.dev"
                        else "Set *.workers.dev",
                      fontFamily = JetBrainsMono,
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = palette.activePillFg,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                      Icons.Outlined.Edit,
                      contentDescription = "Change subdomain",
                      tint = palette.activePillFg,
                      modifier = Modifier.size(11.dp),
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
                  text = "Local Dir",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textMuted,
                )
                Surface(
                  onClick = viewModel::exitScratchpadToAuth,
                  shape = RoundedCornerShape(999.dp),
                  color = palette.activePillBg.copy(alpha = 0.65f),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                  ) {
                    Text(
                      text = "Connect Cloudflare",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = palette.activePillFg,
                    )
                    Icon(
                      imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                      contentDescription = "Connect Cloudflare",
                      tint = palette.activePillFg,
                      modifier = Modifier.size(12.dp),
                    )
                  }
                }
              }
            }
          }

          // Slim flat header action buttons with hairline border
          Surface(
            onClick = { viewModel.setShowHomeGitHubHub(true) },
            shape = CircleShape,
            color = palette.chromeSurface,
            border = rememberGlassBorderStroke(0.75.dp),
            modifier = Modifier.size(40.dp).testTag("home_github_hub_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.AccountTree,
                contentDescription = "Optional GitHub Hub",
                tint = if (uiState.gitHubCreds != null) palette.activePillFg else palette.textPrimary,
                modifier = Modifier.size(19.dp),
              )
              if (uiState.gitHubCreds != null) {
                Box(
                  modifier =
                    Modifier
                      .align(Alignment.TopEnd)
                      .padding(7.dp)
                      .size(6.dp)
                      .clip(CircleShape)
                      .background(EmeraldLive),
                )
              }
            }
          }

          Surface(
            onClick = { viewModel.setShowHomeSettings(true) },
            shape = CircleShape,
            color = palette.chromeSurface,
            border = rememberGlassBorderStroke(0.75.dp),
            modifier = Modifier.size(40.dp).testTag("home_settings_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings and Credentials",
                tint = palette.textPrimary,
                modifier = Modifier.size(19.dp),
              )
            }
          }
        }

        if (uiState.isOpeningWorker || uiState.isLoadingWorkers) {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = palette.ctaBg,
            trackColor = palette.chromeSurface,
          )
        }
      }
    },
    bottomBar = {
      val projectTotal =
        if (uiState.workers.isNotEmpty()) uiState.workers.size else uiState.ramWorkspaces.size
      val infraTotal =
        uiState.kvNamespaces.size + uiState.d1Databases.size + uiState.r2Buckets.size + uiState.queues.size
      AnimatedVisibility(
        visible = !uiState.isBottomDockHidden,
        enter = slideInVertically(tween(200)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(140)),
      ) {
        EdgeBottomDock(
          modifier = Modifier.navigationBarsPadding(),
          onHideDock = { viewModel.setBottomDockHidden(true) },
          hideDockTestTag = "hide_home_bottom_dock_button",
          items =
            listOf(
              EdgeBottomDockItem(
                label = "Projects",
                icon = Icons.Outlined.Code,
                selected = uiState.homeTab == HomeTab.PROJECTS,
                testTag = "home_tab_projects",
                badgeText = projectTotal.takeIf { it > 0 }?.toString(),
                onClick = { viewModel.setHomeTab(HomeTab.PROJECTS) },
              ),
              EdgeBottomDockItem(
                label = "Pages",
                icon = Icons.Outlined.Web,
                selected = uiState.homeTab == HomeTab.PAGES,
                testTag = "home_tab_pages",
                badgeText = uiState.pagesProjects.size.takeIf { it > 0 }?.toString(),
                onClick = { viewModel.setHomeTab(HomeTab.PAGES) },
              ),
              EdgeBottomDockItem(
                label = "Infra",
                icon = Icons.Outlined.Storage,
                selected = uiState.homeTab == HomeTab.INFRA,
                testTag = "home_tab_infra",
                badgeText = infraTotal.takeIf { it > 0 }?.toString(),
                onClick = { viewModel.setHomeTab(HomeTab.INFRA) },
              ),
            ),
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(palette.chromeBg)
          .padding(innerPadding)
          .imePadding(),
    ) {
      AnimatedContent(
        targetState = uiState.homeTab,
        transitionSpec = {
          fadeIn(tween(180)) togetherWith fadeOut(tween(130))
        },
        modifier = Modifier.fillMaxSize(),
        label = "home_tab_content",
      ) { tab ->
        when (tab) {
          HomeTab.PROJECTS ->
            HomeProjectsTabContent(
              uiState = uiState,
              searchQuery = projectSearch,
              onSearchChange = { projectSearch = it },
              onNewProject = { showNewProjectDialog = true },
              onImportBundle = {
                importWorkspaceBundleLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
              },
              onOpenSettings = { viewModel.setShowHomeSettings(true) },
              onOpenGitHubHub = { viewModel.setShowHomeGitHubHub(true) },
              onOpenRamWorkspace = viewModel::openRamWorkspace,
              onRequestRenameRamWorkspace = { rec ->
                val isRemote = !rec.key.startsWith("local_") && !rec.isNewUnexportedWorker && !uiState.isLocalScratchpad
                pendingRenameTarget =
                  HomeRenameTarget(
                    currentName = rec.workingWorkspace.scriptName,
                    ramKey = rec.key,
                    remoteScriptName = null,
                    isRemoteWorker = isRemote,
                    hasEncryptedSecrets = rec.workingWorkspace.bindings.any { it.type == "secret_text" },
                  )
              },
              onRequestDeleteRamWorkspace = { rec ->
                pendingDeleteLocalKey = rec.key to rec.workingWorkspace.scriptName
              },
              onRequestStorageAccess = requestStorageAccess,
              onRefreshWorkers = viewModel::refreshWorkers,
              onOpenWorker = { script -> viewModel.openWorker(script) },
              onRequestRenameWorker = { script, cached ->
                pendingRenameTarget =
                  HomeRenameTarget(
                    currentName = script.id,
                    ramKey = cached?.key,
                    remoteScriptName = script.id,
                    isRemoteWorker = true,
                    hasEncryptedSecrets = cached?.workingWorkspace?.bindings?.any { it.type == "secret_text" } == true,
                  )
              },
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
              onRenameWorker = { newName, deleteOld ->
                viewModel.renameExistingWorker(newScriptNameRaw = newName, deleteOldRemote = deleteOld)
              },
              onUpdateSubdomain = viewModel::updateAccountWorkersSubdomain,
            )
        }
      }

      AnimatedVisibility(
        visible = uiState.isBottomDockHidden,
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 2 },
        modifier =
          Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .imePadding()
            .padding(end = 16.dp, bottom = 14.dp),
      ) {
        Row(
          modifier =
            Modifier
              .edgeGlassSurface(RoundedCornerShape(999.dp), elevated = true)
              .clickable { viewModel.setBottomDockHidden(false) }
              .padding(horizontal = 14.dp, vertical = 9.dp)
              .testTag("show_home_bottom_dock_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.KeyboardArrowUp,
            contentDescription = "Show bottom menu",
            tint = palette.activePillFg,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Menu",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
          )
        }
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
  onOpenSettings: () -> Unit,
  onOpenGitHubHub: () -> Unit,
  onOpenRamWorkspace: (String) -> Unit,
  onRequestRenameRamWorkspace: (RamWorkspaceRecord) -> Unit,
  onRequestDeleteRamWorkspace: (RamWorkspaceRecord) -> Unit,
  onRequestStorageAccess: () -> Unit,
  onRefreshWorkers: () -> Unit,
  onOpenWorker: (CfWorkerScript) -> Unit,
  onRequestRenameWorker: (CfWorkerScript, RamWorkspaceRecord?) -> Unit,
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
  val latestProject = remember(ramWorkspaces) { ramWorkspaces.firstOrNull() }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Clean Thin Primary Action Row (+ New project + Import .json square tile)
    item(key = "home_actions") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          onClick = onNewProject,
          shape = RoundedCornerShape(14.dp),
          color = palette.ctaBg,
          modifier = Modifier.weight(1f).height(50.dp).testTag("home_new_project_button"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.Add,
              contentDescription = null,
              tint = palette.ctaFg,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "New project",
              fontSize = 14.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.ctaFg,
            )
          }
        }

        Surface(
          onClick = onImportBundle,
          shape = RoundedCornerShape(14.dp),
          color = palette.glassElevated,
          border = rememberGlassBorderStroke(0.75.dp),
          modifier = Modifier.size(50.dp).testTag("home_import_bundle_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.CloudDownload,
              contentDescription = "Import workspace JSON",
              tint = palette.textPrimary,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }

    // Compact Quick-Access Chips (API Vault & GitHub) without bloated descriptions
    item(key = "home_quick_cards") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CompactQuickChip(
          title = "API Vault",
          status = if (uiState.credentials != null) "Active" else "Setup",
          accent = if (uiState.credentials != null) EmeraldLive else AmberWarn,
          icon = Icons.Outlined.Key,
          onClick = onOpenSettings,
          modifier = Modifier.weight(1f),
        )
        CompactQuickChip(
          title = "GitHub",
          status = uiState.gitHubCreds?.login?.let { "@$it" } ?: "Optional",
          accent = if (uiState.gitHubCreds != null) EmeraldLive else palette.textMuted,
          icon = Icons.Outlined.AccountTree,
          onClick = onOpenGitHubHub,
          modifier = Modifier.weight(1f),
        )
      }
    }

    // Search Filter
    item(key = "home_search") {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Search projects and Workers…", color = palette.textMuted) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchChange("") }) {
              Icon(Icons.Outlined.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("home_project_search"),
      )
    }

    // Native Phone Folder Permission Prompt (when device external storage access is not yet granted)
    if (!uiState.projectStorageSummary.hasNativeStorageAccess) {
      item(key = "home_storage_permission") {
        Surface(
          color = palette.glassElevated,
          shape = RoundedCornerShape(14.dp),
          border = rememberGlassBorderStroke(0.75.dp),
          modifier = Modifier.fillMaxWidth().testTag("home_storage_permission_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Surface(
              color = palette.activePillBg,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.size(38.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Outlined.Folder,
                  contentDescription = null,
                  tint = palette.activePillFg,
                  modifier = Modifier.size(19.dp),
                )
              }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = "Dedicated Phone Folder",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
              )
              Text(
                text = "Allow file access to store all projects in Phone Storage/EdgeCode/",
                fontSize = 11.5.sp,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Surface(
              onClick = onRequestStorageAccess,
              shape = RoundedCornerShape(12.dp),
              color = palette.ctaBg,
              modifier = Modifier.testTag("home_grant_storage_button"),
            ) {
              Text(
                text = "Allow",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.ctaFg,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              )
            }
          }
        }
      }
    }

    // Auto-managed Dedicated Project Directory Pill Header
    item(key = "ram_header") {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        SectionCountPill(
          title = "Projects",
          count = ramWorkspaces.size,
        )
        if (uiState.projectStorageSummary.totalDiskKb > 0 || uiState.ramDbStats.estimatedRamKb > 0) {
          val kb =
            uiState.projectStorageSummary.totalDiskKb.takeIf { it > 0 }
              ?: uiState.ramDbStats.estimatedRamKb
          val folderLabel = uiState.projectStorageSummary.rootDisplayPath.ifBlank { "EdgeCodeProjects" }
          Text(
            text = "$folderLabel · $kb KB",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    if (ramWorkspaces.isEmpty()) {
      item(key = "ram_empty") {
        Surface(
          color = palette.glassSurface,
          shape = RoundedCornerShape(14.dp),
          border = rememberGlassBorderStroke(0.75.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "No saved projects yet — tap + New project to create one.",
              style = MaterialTheme.typography.bodySmall,
              color = palette.textSecondary,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    } else {
      items(ramWorkspaces, key = { "ram_${it.key}" }) { rec ->
        RamProjectCard(
          record = rec,
          onOpen = { onOpenRamWorkspace(rec.key) },
          onRename = { onRequestRenameRamWorkspace(rec) },
          onDelete = { onRequestDeleteRamWorkspace(rec) },
          modifier = Modifier.animateItem(),
        )
      }
    }

    // Featured "Pick up where you left off" Resume Card
    if (latestProject != null && q.isEmpty()) {
      item(key = "resume_featured_card") {
        val ws = latestProject.workingWorkspace
        val fileCount = ws.files.count { !it.isVirtualReadOnly }
        Surface(
          color = palette.featuredCardBg,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(0.75.dp, palette.featuredCardFg.copy(alpha = 0.14f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = palette.featuredCardFg,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = "Pick up where you left off",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.featuredCardFg,
              )
            }
            Text(
              text = "${ws.scriptName} · $fileCount file(s) · entry ${ws.entryModulePath}",
              style = MaterialTheme.typography.bodySmall,
              color = palette.featuredCardMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Surface(
              onClick = { onOpenRamWorkspace(latestProject.key) },
              shape = RoundedCornerShape(999.dp),
              color = if (palette.isDark) Color(0xFFF3EAFF) else Color(0xFF4F346B),
            ) {
              Text(
                text = "Open ${ws.scriptName}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (palette.isDark) Color(0xFF31164F) else Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
              )
            }
          }
        }
      }
    }

    // Remote Cloudflare Workers section (when signed into Cloudflare)
    if (!uiState.isLocalScratchpad && uiState.credentials != null) {
      item(key = "cf_header") {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          SectionCountPill(
            title = "Cloudflare Workers",
            count = cloudWorkers.size,
          )
          Surface(
            onClick = { if (!uiState.isLoadingWorkers) onRefreshWorkers() },
            shape = CircleShape,
            color = palette.chromeSurface,
            border = rememberGlassBorderStroke(0.75.dp),
            modifier = Modifier.size(34.dp).testTag("home_refresh_workers_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                Icons.Outlined.Refresh,
                contentDescription = "Refresh Workers",
                tint = palette.textSecondary,
                modifier = Modifier.size(17.dp),
              )
            }
          }
        }
      }

      if (cloudWorkers.isEmpty() && !uiState.isLoadingWorkers) {
        item(key = "cf_empty") {
          Surface(
            color = palette.glassSurface,
            shape = RoundedCornerShape(14.dp),
            border = rememberGlassBorderStroke(0.75.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = if (q.isEmpty()) "No Cloudflare Workers found" else "No matching Workers",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
              )
              Text(
                text =
                  if (q.isEmpty()) "Tap New project above to create and deploy your first Worker."
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
            onRename = { onRequestRenameWorker(script, cached) },
            onDelete = { onRequestDeleteWorker(script) },
            modifier = Modifier.animateItem(),
          )
        }
      }
    }

    item(key = "home_bottom_spacer") { Spacer(Modifier.height(14.dp)) }
  }
}

@Composable
private fun SectionCountPill(
  title: String,
  count: Int,
) {
  val palette = LocalIdePalette.current
  Surface(
    color = palette.activePillBg.copy(alpha = 0.55f),
    shape = RoundedCornerShape(999.dp),
  ) {
    Row(
      modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.activePillFg,
      )
      Surface(
        color = palette.ctaBg,
        shape = CircleShape,
        modifier = Modifier.size(22.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = count.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = palette.ctaFg,
          )
        }
      }
    }
  }
}

@Composable
private fun CompactQuickChip(
  title: String,
  status: String,
  accent: Color,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    color = palette.glassSurface,
    shape = RoundedCornerShape(12.dp),
    border = rememberGlassBorderStroke(0.75.dp),
    modifier = modifier.height(44.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = palette.textPrimary,
        modifier = Modifier.size(17.dp),
      )
      Text(
        text = title,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
        Text(
          text = status,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = palette.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private fun formatAccountDisplayName(rawName: String): String =
  rawName
    .trim()
    .replace(Regex("'s\\s+Account$", RegexOption.IGNORE_CASE), "")
    .ifBlank { rawName.trim() }

private data class HomeRenameTarget(
  val currentName: String,
  val ramKey: String?,
  val remoteScriptName: String?,
  val isRemoteWorker: Boolean,
  val hasEncryptedSecrets: Boolean = false,
)

@Composable
private fun RamProjectCard(
  record: RamWorkspaceRecord,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val ws = record.workingWorkspace
  val fileCount = ws.files.count { !it.isVirtualReadOnly }
  val isLocal = record.key.startsWith("local_")
  val dirty = record.hasPendingChanges

  Surface(
    onClick = onOpen,
    color = palette.glassSurface,
    shape = RoundedCornerShape(14.dp),
    border =
      if (dirty) BorderStroke(0.75.dp, AmberWarn.copy(alpha = 0.55f))
      else rememberGlassBorderStroke(0.75.dp),
    modifier = modifier.fillMaxWidth().testTag("home_ram_card_${ws.scriptName}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        color = if (dirty) AmberWarn.copy(alpha = 0.16f) else EmeraldLive.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.75.dp, (if (dirty) AmberWarn else EmeraldLive).copy(alpha = 0.35f)),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (dirty) AmberWarn else EmeraldLive,
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
            fontSize = 15.5.sp,
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
                else -> "Saved"
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
          text = "$fileCount file(s) · ${ws.entryModulePath}",
          fontFamily = JetBrainsMono,
          fontSize = 11.5.sp,
          color = palette.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onRename,
        modifier = Modifier.size(34.dp).testTag("rename_ram_project_${ws.scriptName}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.DriveFileRenameOutline,
          contentDescription = "Rename ${ws.scriptName}",
          tint = palette.textSecondary,
          modifier = Modifier.size(18.dp),
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
  onRename: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val hasRamEdits = cachedRecord?.hasPendingChanges == true

  Surface(
    onClick = onOpen,
    color = palette.glassSurface,
    shape = RoundedCornerShape(14.dp),
    border = rememberGlassBorderStroke(0.75.dp),
    modifier = modifier.fillMaxWidth().testTag("home_worker_card_${script.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        color = if (cachedRecord != null) palette.activePillBg else palette.chromeSurface,
        shape = RoundedCornerShape(10.dp),
        border = rememberGlassBorderStroke(0.75.dp),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.CloudQueue,
            contentDescription = null,
            tint = if (cachedRecord != null) palette.activePillFg else palette.textSecondary,
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
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (hasRamEdits) {
            StatusTag(label = "Edited", color = AmberWarn)
          } else if (cachedRecord != null) {
            StatusTag(label = "Synced", color = EmeraldLive)
          }
        }
        Text(
          text =
            cachedRecord?.let { "${it.workingWorkspace.files.count { f -> !f.isVirtualReadOnly }} files saved" }
              ?: script.modifiedOn?.take(10)?.let { "Updated $it · Tap to open" }
              ?: "Cloudflare Worker · Tap to open",
          fontFamily = JetBrainsMono,
          fontSize = 11.5.sp,
          color = palette.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onRename,
        modifier = Modifier.size(34.dp).testTag("rename_worker_${script.id}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.DriveFileRenameOutline,
          contentDescription = "Rename ${script.id}",
          tint = palette.textSecondary,
          modifier = Modifier.size(18.dp),
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
    shape = RoundedCornerShape(999.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
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
