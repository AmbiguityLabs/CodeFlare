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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.outlined.Archive
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
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import com.example.data.local.ZipImportTargetMode
import com.example.data.remote.CfWorkerScript
import com.example.ui.components.ChangeSubdomainDialog
import com.example.ui.components.EdgeMenu
import com.example.ui.components.EdgeMenuCompactItem
import com.example.ui.components.EdgeMenuHeader
import com.example.ui.components.EdgeMenuItem
import com.example.ui.components.CloudflareInfraPanel
import com.example.ui.components.CloudflarePagesPanel
import com.example.ui.components.EdgeBottomDock
import com.example.ui.components.EdgeBottomDockItem
import com.example.ui.components.EdgeLoadingIndicator
import com.example.ui.components.EdgeSkeletonProjectList
import com.example.ui.components.GitHubAndDiffPanel
import com.example.ui.components.IconRevealButton
import com.example.ui.components.NewWorkerDialog
import com.example.ui.components.RenameWorkerDialog
import com.example.ui.components.UnsavedChangesWarningDialog
import com.example.ui.components.ZipImportPreviewDialog
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.UiStyleVersion
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

  val importZipProjectLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.inspectZipForImport(uri, ZipImportTargetMode.NEW_PROJECT)
      }
    }

  val launchZipPicker = {
    importZipProjectLauncher.launch(
      arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"),
    )
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
        viewModel.dismissZipImportPreview()
      },
      onCreateFromTemplate = { name, template ->
        showNewProjectDialog = false
        pickedNewWorkerUris = emptyList()
        viewModel.dismissZipImportPreview()
        viewModel.createNewWorker(name, template)
      },
      onCreateFromFiles = { name, uris, folderPrefix ->
        showNewProjectDialog = false
        pickedNewWorkerUris = emptyList()
        viewModel.dismissZipImportPreview()
        viewModel.createNewWorkerFromUris(name, uris, folderPrefix)
      },
      zipPreview = uiState.zipImportPreview,
      onPickZip = launchZipPicker,
      onToggleZipStripRoot = viewModel::toggleZipPreviewStripRoot,
      onClearPickedZip = viewModel::dismissZipImportPreview,
      onCreateFromZip = { name ->
        showNewProjectDialog = false
        pickedNewWorkerUris = emptyList()
        viewModel.confirmImportZipAsNewProject(name)
      },
    )
  }

  uiState.zipImportPreview?.let { preview ->
    if (!showNewProjectDialog) {
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

  uiState.pendingUnsavedWarning?.let { warning ->
    UnsavedChangesWarningDialog(
      warning = warning,
      onKeepDraft = viewModel::resolveUnsavedWarningKeepDraft,
      onDeployNow = viewModel::resolveUnsavedWarningDeployNow,
      onDiscard = viewModel::resolveUnsavedWarningDiscard,
      onDismiss = viewModel::dismissUnsavedWarning,
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
        onClearApiCache = viewModel::clearApiResponseCache,
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
              uiState.gitHubCreds?.login?.let { login ->
                Text(
                  text = "@$login",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textMuted,
                )
              }
            }
            if (uiState.isGitHubBusy) {
              EdgeLoadingIndicator(size = 20.dp, modifier = Modifier.padding(end = 4.dp))
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

  val isV2Home = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val homeBarStyle = uiState.editorSettings.homeBarStyle.normalized()
  val projectTotal =
    if (uiState.workers.isNotEmpty()) uiState.workers.size else uiState.ramWorkspaces.size
  val infraTotal =
    uiState.kvNamespaces.size + uiState.d1Databases.size + uiState.r2Buckets.size + uiState.queues.size
  val homeDockItems =
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
    )

  Scaffold(
    containerColor = palette.chromeBg,
    snackbarHost = {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier =
          Modifier.padding(
            bottom =
              if (isV2Home && !uiState.isBottomDockHidden) {
                (homeBarStyle.heightDp + homeBarStyle.bottomMarginDp + 14).dp
              } else {
                0.dp
              },
          ),
      )
    },
    topBar = {
      val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
      val activeStyle = UiStyleVersion.fromKey(uiState.editorSettings.uiStyleVersion)
      Column(modifier = Modifier.background(palette.chromeBg).statusBarsPadding()) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text =
              when (uiState.homeTab) {
                HomeTab.PROJECTS -> "EdgeCode"
                HomeTab.PAGES -> "Pages"
                HomeTab.INFRA -> "Infra"
              },
            fontSize = if (isV2) 22.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 84.dp),
          )

          Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (!uiState.isLocalScratchpad && uiState.accounts.isNotEmpty()) {
              Box {
                IconRevealButton(
                  icon = Icons.Outlined.CloudQueue,
                  revealedLabel = uiState.selectedAccount?.name?.let(::formatAccountDisplayName) ?: "Account",
                  statusDotColor = EmeraldLive,
                  onClick = { accountMenuOpen = true },
                  tag = "home_account_button",
                )
                EdgeMenu(
                  expanded = accountMenuOpen,
                  onDismissRequest = { accountMenuOpen = false },
                  minWidth = 224.dp,
                  maxWidth = 280.dp,
                ) {
                  EdgeMenuHeader(
                    title = "Cloudflare Accounts",
                    badgeText = "${uiState.accounts.size}",
                    leadingIcon = Icons.Outlined.CloudQueue,
                  )
                  uiState.accounts.forEach { acc ->
                    EdgeMenuCompactItem(
                      label = formatAccountDisplayName(acc.name),
                      icon = Icons.Outlined.CloudQueue,
                      selected = acc.id == uiState.selectedAccount?.id,
                      onClick = {
                        accountMenuOpen = false
                        viewModel.selectAccount(acc)
                      },
                    )
                  }
                }
              }

              IconRevealButton(
                icon = Icons.Outlined.Language,
                revealedLabel =
                  if (uiState.accountSubdomain.isNotBlank()) "${uiState.accountSubdomain}.workers.dev"
                  else "Subdomain",
                active = uiState.accountSubdomain.isNotBlank(),
                onClick = { showChangeSubdomainDialog = true },
                tag = "home_subdomain_chip",
              )
            } else {
              IconRevealButton(
                icon = Icons.Outlined.CloudQueue,
                revealedLabel = "Connect",
                statusDotColor = AmberWarn,
                onClick = viewModel::exitScratchpadToAuth,
                tag = "home_connect_cloudflare_button",
              )
            }

            IconRevealButton(
              icon = Icons.Outlined.Style,
              revealedLabel = activeStyle.label,
              active = isV2,
              onClick = {
                val next =
                  if (activeStyle == UiStyleVersion.V1_DEFAULT) UiStyleVersion.V2_MINIMAL_OLED
                  else UiStyleVersion.V1_DEFAULT
                viewModel.updateSettings { it.copy(uiStyleVersion = next.key) }
              },
              tag = "home_style_toggle_button",
            )

            IconRevealButton(
              icon = Icons.Outlined.AccountTree,
              revealedLabel = uiState.gitHubCreds?.login?.let { "@$it" } ?: "GitHub",
              active = uiState.gitHubCreds != null,
              statusDotColor = if (uiState.gitHubCreds != null) EmeraldLive else null,
              onClick = { viewModel.setShowHomeGitHubHub(true) },
              tag = "home_github_hub_button",
            )

            IconRevealButton(
              icon = Icons.Outlined.Settings,
              revealedLabel = "Settings",
              onClick = { viewModel.setShowHomeSettings(true) },
              tag = "home_settings_button",
            )
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
      if (!isV2Home) {
        AnimatedVisibility(
          visible = !uiState.isBottomDockHidden,
          enter = slideInVertically(tween(200)) { it } + fadeIn(tween(180)),
          exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(140)),
        ) {
          EdgeBottomDock(
            modifier = Modifier.navigationBarsPadding(),
            barStyle = homeBarStyle,
            onHideDock = { viewModel.setBottomDockHidden(true) },
            hideDockTestTag = "hide_home_bottom_dock_button",
            items = homeDockItems,
          )
        }
      }
    },
  ) { innerPadding ->
    val floatingBottomClearance =
      if (isV2Home && !uiState.isBottomDockHidden) {
        (homeBarStyle.heightDp + homeBarStyle.bottomMarginDp + 28).dp
      } else {
        16.dp
      }
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
              bottomContentPadding = floatingBottomClearance,
              searchQuery = projectSearch,
              onSearchChange = { projectSearch = it },
              onNewProject = { showNewProjectDialog = true },
              onImportZip = launchZipPicker,
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
            Box(
              modifier =
                Modifier
                  .fillMaxSize()
                  .padding(bottom = if (isV2Home && !uiState.isBottomDockHidden) (homeBarStyle.heightDp + homeBarStyle.bottomMarginDp + 12).dp else 0.dp),
            ) {
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
            }

          HomeTab.INFRA ->
            Box(
              modifier =
                Modifier
                  .fillMaxSize()
                  .padding(bottom = if (isV2Home && !uiState.isBottomDockHidden) (homeBarStyle.heightDp + homeBarStyle.bottomMarginDp + 12).dp else 0.dp),
            ) {
              CloudflareInfraPanel(
                uiState = uiState,
                onRefreshInfra = { viewModel.loadCloudflareInfra(forceRefresh = true) },
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
      }

      if (isV2Home) {
        AnimatedVisibility(
          visible = !uiState.isBottomDockHidden,
          enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
          exit = slideOutVertically(tween(160)) { it } + fadeOut(tween(140)),
          modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          EdgeBottomDock(
            barStyle = homeBarStyle,
            onHideDock = { viewModel.setBottomDockHidden(true) },
            hideDockTestTag = "hide_home_bottom_dock_button",
            items = homeDockItems,
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
            .padding(end = 16.dp, bottom = 14.dp),
      ) {
        Surface(
          onClick = { viewModel.setBottomDockHidden(false) },
          shape = CircleShape,
          color = palette.chromeSurface,
          border = if (palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED) null else rememberGlassBorderStroke(0.75.dp),
          modifier = Modifier.size(42.dp).testTag("show_home_bottom_dock_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.KeyboardArrowUp,
              contentDescription = "Show menu",
              tint = palette.activePillFg,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HomeProjectsTabContent(
  uiState: IdeUiState,
  bottomContentPadding: androidx.compose.ui.unit.Dp,
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  onNewProject: () -> Unit,
  onImportZip: () -> Unit,
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
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val q = searchQuery.trim()
  var showSearchBar by remember { mutableStateOf(false) }
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
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = bottomContentPadding),
    verticalArrangement = Arrangement.spacedBy(if (isV2) 16.dp else 12.dp),
  ) {
    item(key = "home_actions") {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          var importMenuOpen by remember { mutableStateOf(false) }
          Box {
            IconRevealButton(
              icon = Icons.Outlined.CloudDownload,
              revealedLabel = "Import",
              onClick = { importMenuOpen = true },
              height = 42.dp,
              tag = "home_import_bundle_button",
            )
            EdgeMenu(
              expanded = importMenuOpen,
              onDismissRequest = { importMenuOpen = false },
              minWidth = 216.dp,
              maxWidth = 264.dp,
            ) {
              EdgeMenuHeader(
                title = "Import Project",
                leadingIcon = Icons.Outlined.CloudDownload,
              )
              EdgeMenuItem(
                label = "Import .zip Archive",
                icon = Icons.Outlined.Archive,
                badgeText = ".zip",
                onClick = {
                  importMenuOpen = false
                  onImportZip()
                },
              )
              EdgeMenuItem(
                label = "Import .json Bundle",
                icon = Icons.Outlined.CloudDownload,
                badgeText = ".json",
                onClick = {
                  importMenuOpen = false
                  onImportBundle()
                },
              )
            }
          }

          IconRevealButton(
            icon = Icons.Outlined.Search,
            revealedLabel = "Search",
            active = showSearchBar || searchQuery.isNotEmpty(),
            onClick = {
              showSearchBar = !showSearchBar
              if (!showSearchBar && searchQuery.isNotEmpty()) onSearchChange("")
            },
            height = 42.dp,
            tag = "home_search_toggle_button",
          )

          IconRevealButton(
            icon = Icons.Outlined.Key,
            revealedLabel = "Vault",
            statusDotColor = if (uiState.credentials != null) EmeraldLive else AmberWarn,
            onClick = onOpenSettings,
            height = 42.dp,
            tag = "home_vault_shortcut",
          )

          IconRevealButton(
            icon = Icons.Outlined.AccountTree,
            revealedLabel = "GitHub",
            statusDotColor = if (uiState.gitHubCreds != null) EmeraldLive else null,
            onClick = onOpenGitHubHub,
            height = 42.dp,
            tag = "home_github_shortcut",
          )

          if (!uiState.projectStorageSummary.hasNativeStorageAccess) {
            IconRevealButton(
              icon = Icons.Outlined.Folder,
              revealedLabel = "Storage",
              statusDotColor = AmberWarn,
              onClick = onRequestStorageAccess,
              height = 42.dp,
              tag = "home_grant_storage_button",
            )
          }
        }

        Surface(
          onClick = onNewProject,
          color = palette.ctaBg,
          shape = RoundedCornerShape(if (isV2) 999.dp else 12.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("home_new_project_button"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.Add,
              contentDescription = "Create new project",
              tint = palette.ctaFg,
              modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Create new project",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.ctaFg,
              maxLines = 1,
            )
          }
        }
      }
    }

    if (showSearchBar || searchQuery.isNotEmpty()) {
      item(key = "home_search") {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchChange,
          leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
          },
          trailingIcon = {
            IconButton(
              onClick = {
                onSearchChange("")
                showSearchBar = false
              },
            ) {
              Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(if (isV2) 20.dp else 12.dp),
          modifier = Modifier.fillMaxWidth().testTag("home_project_search"),
        )
      }
    }

    if (latestProject != null && q.isEmpty()) {
      item(key = "resume_featured_card") {
        val ws = latestProject.workingWorkspace
        val fileCount = ws.files.count { !it.isVirtualReadOnly }
        Surface(
          onClick = { onOpenRamWorkspace(latestProject.key) },
          color = if (isV2) palette.chromeSurface else palette.featuredCardBg,
          shape = RoundedCornerShape(if (isV2) 24.dp else 16.dp),
          border = if (isV2) null else BorderStroke(0.75.dp, palette.featuredCardFg.copy(alpha = 0.14f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Bolt,
              contentDescription = null,
              tint = if (isV2) palette.activePillFg else palette.featuredCardFg,
              modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(
                text = "Pick up where you left off",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (isV2) palette.textSecondary else palette.featuredCardMuted,
                maxLines = 1,
              )
              Text(
                text = ws.scriptName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isV2) palette.textPrimary else palette.featuredCardFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "$fileCount files · ${ws.entryModulePath}",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = if (isV2) palette.textMuted else palette.featuredCardMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
              contentDescription = "Open",
              tint = if (isV2) palette.textSecondary else palette.featuredCardFg,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }

    item(key = "ram_header") {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Projects",
          style = MaterialTheme.typography.bodyMedium,
          fontSize = if (isV2) 13.5.sp else 13.sp,
          fontWeight = FontWeight.Medium,
          color = palette.textSecondary,
        )
        Text(
          text = "${ramWorkspaces.size}",
          fontFamily = JetBrainsMono,
          fontSize = 11.sp,
          color = palette.textMuted,
        )
      }
    }

    if (ramWorkspaces.isEmpty()) {
      item(key = "ram_empty") {
        Surface(
          onClick = onNewProject,
          color = palette.chromeSurface,
          shape = RoundedCornerShape(if (isV2) 24.dp else 14.dp),
          border = if (isV2) null else rememberGlassBorderStroke(0.75.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(18.dp))
            Text(
              text = "No local projects yet — tap Create new project",
              fontSize = 13.sp,
              color = palette.textSecondary,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    } else if (isV2) {
      item(key = "ram_v2_grouped_list") {
        Surface(
          color = palette.chromeSurface,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            ramWorkspaces.forEachIndexed { idx, rec ->
              if (idx > 0) {
                HorizontalDivider(
                  color = palette.border,
                  thickness = 0.5.dp,
                  modifier = Modifier.padding(start = 18.dp),
                )
              }
              RamProjectRowContent(
                record = rec,
                isV2 = true,
                onOpen = { onOpenRamWorkspace(rec.key) },
                onRename = { onRequestRenameRamWorkspace(rec) },
                onDelete = { onRequestDeleteRamWorkspace(rec) },
                modifier = Modifier.testTag("home_ram_card_${rec.workingWorkspace.scriptName}"),
              )
            }
          }
        }
      }
    } else {
      items(ramWorkspaces, key = { "ram_${it.key}" }) { rec ->
        val ws = rec.workingWorkspace
        Surface(
          color = palette.glassSurface,
          shape = RoundedCornerShape(14.dp),
          border =
            if (rec.hasPendingChanges) BorderStroke(0.75.dp, AmberWarn.copy(alpha = 0.55f))
            else rememberGlassBorderStroke(0.75.dp),
          modifier = Modifier.fillMaxWidth().animateItem().testTag("home_ram_card_${ws.scriptName}"),
        ) {
          RamProjectRowContent(
            record = rec,
            isV2 = false,
            onOpen = { onOpenRamWorkspace(rec.key) },
            onRename = { onRequestRenameRamWorkspace(rec) },
            onDelete = { onRequestDeleteRamWorkspace(rec) },
          )
        }
      }
    }

    if (!uiState.isLocalScratchpad && uiState.credentials != null) {
      item(key = "cf_header") {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Workers",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = if (isV2) 13.5.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textSecondary,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "${cloudWorkers.size}",
              fontFamily = JetBrainsMono,
              fontSize = 11.sp,
              color = palette.textMuted,
            )
            IconRevealButton(
              icon = Icons.Outlined.Refresh,
              revealedLabel = "Refresh",
              isBusy = uiState.isLoadingWorkers,
              onClick = { if (!uiState.isLoadingWorkers) onRefreshWorkers() },
              height = 32.dp,
              tag = "home_refresh_workers_button",
            )
          }
        }
      }

      if (cloudWorkers.isEmpty() && uiState.isLoadingWorkers) {
        item(key = "cf_loading") {
          EdgeSkeletonProjectList(count = 4)
        }
      } else if (cloudWorkers.isEmpty() && !uiState.isLoadingWorkers) {
        item(key = "cf_empty") {
          Surface(
            color = palette.chromeSurface,
            shape = RoundedCornerShape(if (isV2) 24.dp else 14.dp),
            border = if (isV2) null else rememberGlassBorderStroke(0.75.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(Icons.Outlined.CloudQueue, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(18.dp))
              Text(
                text = "No deployed Workers found",
                fontSize = 13.sp,
                color = palette.textSecondary,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      } else if (isV2) {
        item(key = "cf_v2_grouped_list") {
          Surface(
            color = palette.chromeSurface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              cloudWorkers.forEachIndexed { idx, script ->
                if (idx > 0) {
                  HorizontalDivider(
                    color = palette.border,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 18.dp),
                  )
                }
                val cached = ramByName[script.id]
                CloudWorkerRowContent(
                  script = script,
                  cachedRecord = cached,
                  isV2 = true,
                  onOpen = { onOpenWorker(script) },
                  onRename = { onRequestRenameWorker(script, cached) },
                  onDelete = { onRequestDeleteWorker(script) },
                  modifier = Modifier.testTag("home_worker_card_${script.id}"),
                )
              }
            }
          }
        }
      } else {
        items(cloudWorkers, key = { "cf_${it.id}" }) { script ->
          val cached = ramByName[script.id]
          Surface(
            color = palette.glassSurface,
            shape = RoundedCornerShape(14.dp),
            border = rememberGlassBorderStroke(0.75.dp),
            modifier = Modifier.fillMaxWidth().animateItem().testTag("home_worker_card_${script.id}"),
          ) {
            CloudWorkerRowContent(
              script = script,
              cachedRecord = cached,
              isV2 = false,
              onOpen = { onOpenWorker(script) },
              onRename = { onRequestRenameWorker(script, cached) },
              onDelete = { onRequestDeleteWorker(script) },
            )
          }
        }
      }
    }

    item(key = "home_bottom_spacer") { Spacer(Modifier.height(14.dp)) }
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
private fun RamProjectRowContent(
  record: RamWorkspaceRecord,
  isV2: Boolean,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val ws = record.workingWorkspace
  val fileCount = ws.files.count { !it.isVirtualReadOnly }
  val dirty = record.hasPendingChanges

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
        .padding(horizontal = if (isV2) 18.dp else 13.dp, vertical = if (isV2) 14.dp else 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(if (isV2) 14.dp else 10.dp),
  ) {
    if (isV2) {
      Icon(
        imageVector = Icons.Outlined.Folder,
        contentDescription = null,
        tint = if (dirty) AmberWarn else palette.textPrimary,
        modifier = Modifier.size(20.dp),
      )
    } else {
      Surface(
        color = if (dirty) AmberWarn.copy(alpha = 0.16f) else EmeraldLive.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.75.dp, (if (dirty) AmberWarn else EmeraldLive).copy(alpha = 0.35f)),
        modifier = Modifier.size(38.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (dirty) AmberWarn else EmeraldLive,
            modifier = Modifier.size(19.dp),
          )
        }
      }
    }

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = ws.scriptName,
        fontSize = 15.5.sp,
        fontWeight = if (isV2) FontWeight.Normal else FontWeight.SemiBold,
        color = palette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "$fileCount files · ${ws.entryModulePath}",
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        color = palette.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    IconButton(
      onClick = onRename,
      modifier = Modifier.size(30.dp).testTag("rename_ram_project_${ws.scriptName}"),
    ) {
      Icon(
        imageVector = Icons.Outlined.DriveFileRenameOutline,
        contentDescription = "Rename ${ws.scriptName}",
        tint = palette.textSecondary,
        modifier = Modifier.size(17.dp),
      )
    }

    IconButton(
      onClick = onDelete,
      modifier = Modifier.size(30.dp).testTag("delete_ram_project_${ws.scriptName}"),
    ) {
      Icon(
        imageVector = Icons.Outlined.DeleteOutline,
        contentDescription = "Remove ${ws.scriptName}",
        tint = palette.textMuted,
        modifier = Modifier.size(17.dp),
      )
    }

    Icon(
      imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
      contentDescription = "Open project",
      tint = palette.textSecondary,
      modifier = Modifier.size(19.dp),
    )
  }
}

@Composable
private fun CloudWorkerRowContent(
  script: CfWorkerScript,
  cachedRecord: RamWorkspaceRecord?,
  isV2: Boolean,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val hasRamEdits = cachedRecord?.hasPendingChanges == true
  val subtitle =
    cachedRecord?.let { "${it.workingWorkspace.files.count { f -> !f.isVirtualReadOnly }} files · ${it.workingWorkspace.entryModulePath}" }
      ?: script.modifiedOn?.take(10)
      ?: "Cloudflare Worker"

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
        .padding(horizontal = if (isV2) 18.dp else 13.dp, vertical = if (isV2) 14.dp else 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(if (isV2) 14.dp else 12.dp),
  ) {
    if (isV2) {
      Icon(
        imageVector = Icons.Outlined.CloudQueue,
        contentDescription = null,
        tint = if (hasRamEdits) AmberWarn else if (cachedRecord != null) EmeraldLive else palette.textPrimary,
        modifier = Modifier.size(20.dp),
      )
    } else {
      Surface(
        color = if (cachedRecord != null) palette.activePillBg else palette.chromeSurface,
        shape = RoundedCornerShape(10.dp),
        border = rememberGlassBorderStroke(0.75.dp),
        modifier = Modifier.size(38.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.CloudQueue,
            contentDescription = null,
            tint = if (cachedRecord != null) palette.activePillFg else palette.textSecondary,
            modifier = Modifier.size(19.dp),
          )
        }
      }
    }

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = script.id,
        fontSize = 15.5.sp,
        fontWeight = if (isV2) FontWeight.Normal else FontWeight.SemiBold,
        color = palette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        color = palette.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    IconButton(
      onClick = onRename,
      modifier = Modifier.size(30.dp).testTag("rename_worker_${script.id}"),
    ) {
      Icon(
        imageVector = Icons.Outlined.DriveFileRenameOutline,
        contentDescription = "Rename ${script.id}",
        tint = palette.textSecondary,
        modifier = Modifier.size(17.dp),
      )
    }

    IconButton(
      onClick = onDelete,
      modifier = Modifier.size(30.dp).testTag("delete_worker_${script.id}"),
    ) {
      Icon(
        imageVector = Icons.Outlined.DeleteOutline,
        contentDescription = "Delete ${script.id}",
        tint = palette.textMuted,
        modifier = Modifier.size(17.dp),
      )
    }

    Icon(
      imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
      contentDescription = "Open ${script.id}",
      tint = palette.textSecondary,
      modifier = Modifier.size(19.dp),
    )
  }
}
