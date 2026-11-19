package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.auth.GitHubOAuthManager
import com.example.data.remote.DiffType
import com.example.data.remote.GitChangedFile
import com.example.data.remote.GitFileChangeStatus
import com.example.data.remote.GitHubDiffFile
import com.example.data.remote.GitHubIssue
import com.example.data.remote.GitHubRepo
import com.example.data.security.GitHubAuthMethod
import com.example.editor.CodeFormatter
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.IdeUiState

@Composable
fun GitHubAndDiffPanel(
  uiState: IdeUiState,
  onStartGitHubOAuth: (String) -> Unit,
  onCancelGitHubOAuth: () -> Unit,
  onConnectGitHubToken: (String) -> Unit,
  onDisconnectGitHub: () -> Unit,
  onRefreshRepos: () -> Unit,
  onSelectRepo: (GitHubRepo?) -> Unit,
  onSelectBranch: (String) -> Unit,
  onCreateBranch: (String) -> Unit,
  onCloneRepository: (repoUrlOrSlug: String, branchOverride: String?, mergeIntoCurrentWorker: Boolean) -> Unit,
  onCreateGitHubRepo: (name: String, description: String, isPrivate: Boolean) -> Unit,
  onLinkSelectedRepo: () -> Unit,
  onPullFilesToWorker: (List<String>) -> Unit,
  onSyncPullLinkedRepo: () -> Unit,
  onSetCommitMessage: (String) -> Unit,
  onToggleStageFile: (String) -> Unit,
  onStageAll: () -> Unit,
  onUnstageAll: () -> Unit,
  onDiscardFileChange: (String) -> Unit,
  onCommitLocally: () -> Unit,
  onPushToGitHub: () -> Unit,
  onInspectCommit: (String) -> Unit,
  onInspectPullRequest: (Int?) -> Unit,
  onCreatePullRequest: (title: String, body: String, head: String, base: String) -> Unit,
  onMergePullRequest: (prNumber: Int, method: String) -> Unit,
  onCreateIssue: (title: String, body: String) -> Unit,
  onToggleIssueState: (GitHubIssue) -> Unit,
  onCommentOnIssue: (issueNumber: Int, comment: String) -> Unit,
  onDispatchWorkflow: (Long) -> Unit,
  onRerunWorkflowRun: (Long) -> Unit,
  onInspectWorkflowRunJobs: (Long) -> Unit,
  onSubmitPrReview: (prNumber: Int, event: String, body: String) -> Unit,
  onGenerateCfActionWorkflow: () -> Unit,
  onOpenFileInEditor: (String) -> Unit,
  onRenameWorkerTarget: (String) -> Unit,
  onDeployActiveWorker: () -> Unit,
  onRevertFile: (String) -> Unit,
  onCloseDrawerIfCompact: () -> Unit,
  onOpenDiffReview: (() -> Unit)? = null,
  onOpenPrePushDiffReview: (() -> Unit)? = null,
  onOpenConflictResolver: (() -> Unit)? = null,
) {
  // 0 = Git, 1 = Repos, 2 = PRs & Issues, 3 = CF Diff
  var subTab by remember(uiState.linkedGitRepo != null) {
    mutableIntStateOf(
      if (uiState.linkedGitRepo != null || uiState.gitChangedFiles.isNotEmpty() || uiState.localGitCommits.isNotEmpty()) 0
      else 1,
    )
  }

  val totalChanges = uiState.gitChangedFiles.size + uiState.localGitCommits.size
  val prAndIssueCount = uiState.gitHubPullRequests.size + uiState.gitHubIssues.size

  Column(modifier = Modifier.fillMaxSize()) {
    if (uiState.gitHubCreds != null) {
      GitHubConnectedProfileHeader(
        uiState = uiState,
        onRefreshRepos = onRefreshRepos,
        onDisconnect = onDisconnectGitHub,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      GitPanelTabChip(
        label = if (totalChanges > 0) "Git ($totalChanges)" else "Git",
        selected = subTab == 0,
        tag = "source_control_tab",
        onClick = { subTab = 0 },
        modifier = Modifier.weight(0.95f),
      )
      GitPanelTabChip(
        label = "Repos",
        selected = subTab == 1,
        tag = "source_control_github_tab",
        onClick = { subTab = 1 },
        modifier = Modifier.weight(0.9f),
      )
      GitPanelTabChip(
        label = if (prAndIssueCount > 0) "PR/Issue ($prAndIssueCount)" else "PR/Issue",
        selected = subTab == 2,
        tag = "source_control_prs_tab",
        onClick = { subTab = 2 },
        modifier = Modifier.weight(1.25f),
      )
      GitPanelTabChip(
        label = "Diff",
        selected = subTab == 3,
        tag = "source_control_diff_tab",
        onClick = { subTab = 3 },
        modifier = Modifier.weight(0.75f),
      )
    }

    when (subTab) {
      0 ->
        GitSourceControlTab(
          uiState = uiState,
          onSwitchToCloneRepos = { subTab = 1 },
          onSelectBranch = onSelectBranch,
          onCreateBranch = onCreateBranch,
          onLinkSelectedRepo = onLinkSelectedRepo,
          onSyncPullLinkedRepo = onSyncPullLinkedRepo,
          onSetCommitMessage = onSetCommitMessage,
          onToggleStageFile = onToggleStageFile,
          onStageAll = onStageAll,
          onUnstageAll = onUnstageAll,
          onDiscardFileChange = onDiscardFileChange,
          onCommitLocally = onCommitLocally,
          onPushToGitHub = onPushToGitHub,
          onInspectCommit = onInspectCommit,
          onDispatchWorkflow = onDispatchWorkflow,
          onRerunWorkflowRun = onRerunWorkflowRun,
          onInspectWorkflowRunJobs = onInspectWorkflowRunJobs,
          onGenerateCfActionWorkflow = onGenerateCfActionWorkflow,
          onOpenFileInEditor = { path ->
            onOpenFileInEditor(path)
            onCloseDrawerIfCompact()
          },
          onRenameWorkerTarget = onRenameWorkerTarget,
          onDeployActiveWorker = onDeployActiveWorker,
          onOpenPrePushDiffReview = onOpenPrePushDiffReview,
          onOpenConflictResolver = onOpenConflictResolver,
        )
      1 ->
        GitCloneAndReposTab(
          uiState = uiState,
          onStartGitHubOAuth = onStartGitHubOAuth,
          onCancelGitHubOAuth = onCancelGitHubOAuth,
          onConnectGitHubToken = onConnectGitHubToken,
          onRefreshRepos = onRefreshRepos,
          onSelectRepo = onSelectRepo,
          onSelectBranch = onSelectBranch,
          onCreateBranch = onCreateBranch,
          onCloneRepository = { url, branch, merge ->
            onCloneRepository(url, branch, merge)
            if (!merge) {
              subTab = 0
              onCloseDrawerIfCompact()
            }
          },
          onCreateGitHubRepo = onCreateGitHubRepo,
          onLinkSelectedRepo = {
            onLinkSelectedRepo()
            subTab = 0
          },
          onPullFilesToWorker = {
            onPullFilesToWorker(it)
            onCloseDrawerIfCompact()
          },
        )
      2 ->
        GitHubPullsAndIssuesTab(
          uiState = uiState,
          onSwitchToRepos = { subTab = 1 },
          onInspectPullRequest = onInspectPullRequest,
          onCreatePullRequest = onCreatePullRequest,
          onMergePullRequest = onMergePullRequest,
          onSubmitPrReview = onSubmitPrReview,
          onCreateIssue = onCreateIssue,
          onToggleIssueState = onToggleIssueState,
          onCommentOnIssue = onCommentOnIssue,
        )
      else -> LocalWorkerDiffView(uiState = uiState, onRevertFile = onRevertFile, onOpenFullDiffReview = onOpenDiffReview)
    }
  }
}

@Composable
private fun GitHubConnectedProfileHeader(
  uiState: IdeUiState,
  onRefreshRepos: () -> Unit,
  onDisconnect: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val creds = uiState.gitHubCreds ?: return
  val avatarUrl = uiState.gitHubUser?.avatarUrl ?: creds.avatarUrl

  Surface(
    color = palette.chromePill,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (avatarUrl.isNotBlank()) {
        AsyncImage(
          model = avatarUrl,
          contentDescription = "Avatar for @${creds.login}",
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(30.dp).clip(CircleShape),
        )
      } else {
        Surface(color = palette.activePillBg, shape = CircleShape, modifier = Modifier.size(30.dp)) {
          Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(16.dp))
          }
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          Text(
            text = "@${creds.login}",
            style = MaterialTheme.typography.labelLarge,
            color = palette.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Surface(color = palette.activePillBg, shape = RoundedCornerShape(6.dp)) {
            Text(
              text = if (creds.method == GitHubAuthMethod.OAUTH_DEVICE) "OAuth" else "PAT",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = palette.activePillFg,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
          }
        }
        val metaLine =
          buildString {
            append(creds.scopes.ifBlank { "repo, workflow" })
            uiState.gitHubRateLimitLabel?.let { append(" • $it") }
          }
        Text(
          text = metaLine,
          style = MaterialTheme.typography.labelSmall,
          color = palette.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(onClick = onRefreshRepos, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh GitHub data", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
      }
      TextButton(onClick = onDisconnect, modifier = Modifier.height(32.dp)) {
        Text("Sign Out", fontSize = 11.sp, color = RoseError)
      }
    }
  }
}

@Composable
private fun GitPanelTabChip(
  label: String,
  selected: Boolean,
  tag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = if (selected) palette.activePillBg else palette.chromePill,
    modifier = modifier.testTag(tag),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) palette.activePillFg else palette.textSecondary,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun GitSourceControlTab(
  uiState: IdeUiState,
  onSwitchToCloneRepos: () -> Unit,
  onSelectBranch: (String) -> Unit,
  onCreateBranch: (String) -> Unit,
  onLinkSelectedRepo: () -> Unit,
  onSyncPullLinkedRepo: () -> Unit,
  onSetCommitMessage: (String) -> Unit,
  onToggleStageFile: (String) -> Unit,
  onStageAll: () -> Unit,
  onUnstageAll: () -> Unit,
  onDiscardFileChange: (String) -> Unit,
  onCommitLocally: () -> Unit,
  onPushToGitHub: () -> Unit,
  onInspectCommit: (String) -> Unit,
  onDispatchWorkflow: (Long) -> Unit,
  onRerunWorkflowRun: (Long) -> Unit,
  onInspectWorkflowRunJobs: (Long) -> Unit,
  onGenerateCfActionWorkflow: () -> Unit,
  onOpenFileInEditor: (String) -> Unit,
  onRenameWorkerTarget: (String) -> Unit,
  onDeployActiveWorker: () -> Unit,
  onOpenPrePushDiffReview: (() -> Unit)? = null,
  onOpenConflictResolver: (() -> Unit)? = null,
) {
  val palette = LocalIdePalette.current
  val linked = uiState.linkedGitRepo
  val selectedRepo = uiState.selectedGitHubRepo
  val activeBranch = linked?.branch ?: uiState.selectedGitHubBranch
  val changedFiles = uiState.gitChangedFiles
  val stagedCount = changedFiles.count { it.isStaged }
  val queuedCount = uiState.localGitCommits.size

  var showRenameWorkerDialog by remember { mutableStateOf(false) }
  var showCreateBranchDialog by remember { mutableStateOf(false) }
  var branchMenuExpanded by remember { mutableStateOf(false) }

  if (showRenameWorkerDialog && uiState.workspace != null) {
    var workerTargetName by remember { mutableStateOf(uiState.workspace.scriptName) }
    AlertDialog(
      onDismissRequest = { showRenameWorkerDialog = false },
      title = { Text("Cloudflare Worker Target") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Set the Cloudflare Worker script name used when deploying this Git project to Cloudflare Edge.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
          )
          OutlinedTextField(
            value = workerTargetName,
            onValueChange = { workerTargetName = it },
            label = { Text("Worker name") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onRenameWorkerTarget(workerTargetName)
            showRenameWorkerDialog = false
          },
          enabled = workerTargetName.isNotBlank(),
        ) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { showRenameWorkerDialog = false }) { Text("Cancel") } },
    )
  }

  if (showCreateBranchDialog && selectedRepo != null) {
    var newBranch by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreateBranchDialog = false },
      title = { Text("New Branch from $activeBranch") },
      text = {
        OutlinedTextField(
          value = newBranch,
          onValueChange = { newBranch = it },
          label = { Text("Branch name") },
          placeholder = { Text("feat/worker-route") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onCreateBranch(newBranch)
            showCreateBranchDialog = false
          },
          enabled = newBranch.isNotBlank(),
        ) { Text("Create") }
      },
      dismissButton = { TextButton(onClick = { showCreateBranchDialog = false }) { Text("Cancel") } },
    )
  }

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // 1. Repository Link & Worker Deployment Bridge Card
    Surface(
      color = palette.chromePill,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (linked != null) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = linked.fullName,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "branch: ${linked.branch}",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.activePillFg,
                )
                if (linked.headCommitSha.isNotBlank()) {
                  Text(
                    text = "• ${linked.headCommitSha.take(7)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = palette.textMuted,
                  )
                }
              }
            }
            if (queuedCount > 0) {
              Surface(color = palette.activePillBg, shape = RoundedCornerShape(8.dp)) {
                Text(
                  text = "↑ $queuedCount",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.activePillFg,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
          }

          if (uiState.gitHubBranches.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              BoxBranchPicker(
                currentBranch = activeBranch,
                branches = uiState.gitHubBranches.map { it.name },
                expanded = branchMenuExpanded,
                onOpenChange = { branchMenuExpanded = it },
                onSelect = onSelectBranch,
                modifier = Modifier.weight(1f),
              )
              if (uiState.gitHubCreds != null) {
                IconButton(onClick = { showCreateBranchDialog = true }, modifier = Modifier.size(40.dp)) {
                  Icon(Icons.Outlined.Add, contentDescription = "Create branch", modifier = Modifier.size(18.dp))
                }
              }
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
              onClick = onSyncPullLinkedRepo,
              enabled = !uiState.isGitHubBusy,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).height(40.dp).testTag("git_pull_button"),
            ) {
              Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(Modifier.width(5.dp))
              Text("Pull", fontSize = 12.sp)
            }
            if (uiState.workspace != null && !uiState.isLocalScratchpad) {
              FilledTonalButton(
                onClick = onDeployActiveWorker,
                enabled = !uiState.isDeploying,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1.2f).height(40.dp).testTag("git_deploy_worker_button"),
              ) {
                Icon(Icons.Outlined.RocketLaunch, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Deploy Worker", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }

          if (uiState.workspace != null) {
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showRenameWorkerDialog = true }.padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "CF Target: ${uiState.workspace.scriptName}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.weight(1f),
              )
              Icon(Icons.Outlined.Edit, contentDescription = "Change Worker target name", tint = palette.textMuted, modifier = Modifier.size(14.dp))
            }
          }
        } else {
          Text("NO REPOSITORY LINKED", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
          Text(
            text =
              if (uiState.workspace == null) "Clone a GitHub repository to open a Worker workspace, edit code, commit, and deploy."
              else "Link '${uiState.workspace.scriptName}' to a GitHub repo or clone an existing repo to track changes and push commits.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(
              onClick = onSwitchToCloneRepos,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).testTag("git_open_clone_repos_btn"),
            ) {
              Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Clone / Select Repo", fontSize = 12.sp)
            }
            if (selectedRepo != null && uiState.workspace != null) {
              OutlinedButton(
                onClick = onLinkSelectedRepo,
                shape = RoundedCornerShape(10.dp),
              ) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Link", fontSize = 12.sp)
              }
            }
          }
        }
        if (uiState.workspace != null) {
          OutlinedButton(
            onClick = {
              onGenerateCfActionWorkflow()
              onOpenFileInEditor(".github/workflows/deploy-cloudflare.yml")
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("generate_cf_action_button"),
          ) {
            Icon(Icons.Outlined.RocketLaunch, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Generate Auto-Deploy GitHub Action", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }

    uiState.gitHubError?.let { err ->
      Surface(color = RoseError.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(err, style = MaterialTheme.typography.labelSmall, color = RoseError, modifier = Modifier.padding(10.dp))
      }
    }

    uiState.gitPushStatusMessage?.let { status ->
      Surface(color = EmeraldLive.copy(alpha = 0.13f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(15.dp))
          Text(status, style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
        }
      }
    }

    if (uiState.pendingMergeConflicts.isNotEmpty() && onOpenConflictResolver != null) {
      Surface(
        onClick = onOpenConflictResolver,
        color = AmberWarn.copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("git_resolve_conflicts_banner"),
      ) {
        Row(
          modifier = Modifier.padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Outlined.MergeType, contentDescription = null, tint = AmberWarn, modifier = Modifier.size(18.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${uiState.pendingMergeConflicts.size} Conflicted File(s) Detected",
              style = MaterialTheme.typography.labelMedium,
              color = AmberWarn,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Tap to open 3-Way Merge Conflict Resolver",
              style = MaterialTheme.typography.labelSmall,
              color = palette.textPrimary,
            )
          }
        }
      }
    }

    if (uiState.isGitHubBusy) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EdgeLoadingIndicator(size = 18.dp)
        Text("Syncing with GitHub…", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
      }
    }

    // 2. Commit & Push Composer
    if (uiState.workspace != null) {
      Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("COMMIT & PUSH", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
          OutlinedTextField(
            value = uiState.gitCommitMessageInput,
            onValueChange = onSetCommitMessage,
            label = { Text("Commit message") },
            placeholder = { Text("feat: update worker handler") },
            maxLines = 3,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().testTag("git_commit_message_input"),
          )

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("feat: ", "fix: ", "chore: ").forEach { prefix ->
              Surface(
                onClick = {
                  if (!uiState.gitCommitMessageInput.startsWith(prefix)) {
                    onSetCommitMessage(prefix + uiState.gitCommitMessageInput)
                  }
                },
                shape = RoundedCornerShape(8.dp),
                color = palette.chromeSurface,
              ) {
                Text(
                  text = prefix.trim(),
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textSecondary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
              onClick = onCommitLocally,
              enabled = stagedCount > 0 && !uiState.isGitHubBusy,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).height(44.dp).testTag("git_commit_local_button"),
            ) {
              Text("Commit ($stagedCount)", fontSize = 12.sp, maxLines = 1)
            }
            FilledTonalButton(
              onClick = onPushToGitHub,
              enabled = !uiState.isGitHubBusy && (linked != null || selectedRepo != null) && uiState.gitHubCreds != null,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1.3f).height(44.dp).testTag("github_push_button"),
            ) {
              Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(5.dp))
              val pushLabel =
                when {
                  queuedCount > 0 && stagedCount > 0 -> "Commit & Push (${queuedCount + 1})"
                  queuedCount > 0 -> "Push $queuedCount Commit(s)"
                  stagedCount > 0 -> "Commit & Push ($stagedCount)"
                  else -> "Push Sync"
                }
              Text(pushLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }

          if (onOpenPrePushDiffReview != null && changedFiles.isNotEmpty()) {
            OutlinedButton(
              onClick = onOpenPrePushDiffReview,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().height(38.dp).testTag("git_review_diff_before_push"),
            ) {
              Icon(Icons.Outlined.Difference, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(Modifier.width(6.dp))
              Text("Review Unified / Side-by-Side Diff", fontSize = 11.5.sp)
            }
          }

          if (uiState.gitHubCreds == null) {
            Text(
              text = "Connect GitHub (OAuth or PAT) in Repos tab to push commits.",
              style = MaterialTheme.typography.labelSmall,
              color = AmberWarn,
            )
          }
        }
      }
    }

    // 3. Unpushed Local Commit Queue
    if (uiState.localGitCommits.isNotEmpty()) {
      Surface(color = palette.chromeSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "UNPUSHED COMMITS (${uiState.localGitCommits.size} AHEAD)",
            style = MaterialTheme.typography.labelSmall,
            color = palette.activePillFg,
          )
          uiState.localGitCommits.reversed().forEach { lc ->
            Surface(color = palette.chromePill, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(8.dp)) {
                Text(lc.message, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${lc.id} • ${lc.changedCount} file(s) • ${lc.timestamp}", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
              }
            }
          }
        }
      }
    }

    // 4. Working Tree Changes (Staged & Unstaged)
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "CHANGES (${changedFiles.size})",
        style = MaterialTheme.typography.labelSmall,
        color = palette.textMuted,
      )
      if (changedFiles.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(onClick = onStageAll, modifier = Modifier.height(30.dp)) { Text("Stage All", fontSize = 11.sp) }
          TextButton(onClick = onUnstageAll, modifier = Modifier.height(30.dp)) { Text("Unstage", fontSize = 11.sp) }
        }
      }
    }

    if (changedFiles.isEmpty()) {
      Surface(color = palette.chromeSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
          text = if (uiState.workspace == null) "Open or clone a repository to inspect changes." else "Working tree clean — edit any file to track changes.",
          style = MaterialTheme.typography.bodySmall,
          color = palette.textSecondary,
          modifier = Modifier.padding(12.dp),
        )
      }
    } else {
      changedFiles.forEach { change ->
        GitChangedFileCard(
          change = change,
          onToggleStage = { onToggleStageFile(change.path) },
          onOpenFile = { onOpenFileInEditor(change.path) },
          onDiscard = { onDiscardFileChange(change.path) },
        )
      }
    }

    // 5. Clickable Remote Commits + Expanded Patch Diff Inspector
    if (uiState.gitHubCommits.isNotEmpty()) {
      HorizontalDivider(color = palette.treeLine)
      Text("REMOTE COMMITS (TAP TO INSPECT DIFF)", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
      uiState.gitHubCommits.take(6).forEach { c ->
        val isExpanded = uiState.selectedCommitDetail?.sha == c.sha
        Surface(
          onClick = { onInspectCommit(c.sha) },
          color = if (isExpanded) palette.selectedRowBg else palette.chromePill,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = c.commit.message.lineSequence().firstOrNull().orEmpty(),
              style = MaterialTheme.typography.labelMedium,
              color = palette.textPrimary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${c.sha.take(7)} • ${c.commit.author?.name.orEmpty()}",
              style = MaterialTheme.typography.labelSmall,
              color = palette.textMuted,
            )
            AnimatedVisibility(visible = isExpanded && uiState.selectedCommitDetail != null) {
              val files = uiState.selectedCommitDetail?.files.orEmpty()
              Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                files.take(6).forEach { df ->
                  RemotePatchDiffCard(df)
                }
              }
            }
          }
        }
      }
    }

    // 6. GitHub Actions Workflows (Manual Dispatch) & CI Runs (Re-run)
    if (uiState.gitHubWorkflows.isNotEmpty() || uiState.gitHubWorkflowRuns.isNotEmpty()) {
      HorizontalDivider(color = palette.treeLine)
      Text("GITHUB ACTIONS CI", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
      uiState.gitHubWorkflows.take(4).forEach { wf ->
        Surface(color = palette.chromeSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text(wf.name, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(wf.path, style = MaterialTheme.typography.labelSmall, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (uiState.gitHubCreds != null) {
              FilledTonalButton(
                onClick = { onDispatchWorkflow(wf.id) },
                modifier = Modifier.height(34.dp),
              ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Dispatch workflow", modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Run", fontSize = 11.sp)
              }
            }
          }
        }
      }
      uiState.gitHubWorkflowRuns.take(5).forEach { run ->
        val runColor =
          when (run.conclusion) {
            "success" -> EmeraldLive
            "failure" -> RoseError
            else -> AmberWarn
          }
        val jobsExpanded = uiState.selectedWorkflowRunId == run.id
        Surface(
          onClick = { onInspectWorkflowRunJobs(run.id) },
          color = if (jobsExpanded) palette.selectedRowBg else palette.chromePill,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().testTag("workflow_run_${run.id}"),
        ) {
          Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                Text(run.name ?: "Workflow", style = MaterialTheme.typography.labelMedium, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${run.headBranch.orEmpty()} • ${run.conclusion ?: run.status ?: ""} • Tap for jobs", style = MaterialTheme.typography.labelSmall, color = runColor)
              }
              if (uiState.gitHubCreds != null) {
                IconButton(onClick = { onRerunWorkflowRun(run.id) }, modifier = Modifier.size(34.dp)) {
                  Icon(Icons.Outlined.Replay, contentDescription = "Re-run workflow", tint = palette.activePillFg, modifier = Modifier.size(16.dp))
                }
              }
              Icon(
                imageVector = if (jobsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (jobsExpanded) "Collapse jobs" else "Inspect jobs",
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp),
              )
            }

            AnimatedVisibility(visible = jobsExpanded) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (uiState.selectedWorkflowJobs.isEmpty()) {
                  Text(
                    text = if (uiState.isGitHubBusy) "Loading job logs & steps…" else "No jobs returned for this run.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                  )
                } else {
                  uiState.selectedWorkflowJobs.forEach { job ->
                    val jobColor =
                      when (job.conclusion) {
                        "success" -> EmeraldLive
                        "failure" -> RoseError
                        else -> AmberWarn
                      }
                    Surface(color = palette.chromeSurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                      Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                          Text(job.name, style = MaterialTheme.typography.labelSmall, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                          Text(job.conclusion ?: job.status, style = MaterialTheme.typography.labelSmall, color = jobColor)
                        }
                        job.steps.forEach { step ->
                          val stepColor =
                            when (step.conclusion) {
                              "success" -> EmeraldLive
                              "failure" -> RoseError
                              "skipped" -> palette.textMuted
                              else -> AmberWarn
                            }
                          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                              text = "${step.number}. ${step.name}",
                              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontSize = 10.sp),
                              color = palette.textSecondary,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis,
                              modifier = Modifier.weight(1f),
                            )
                            Text(
                              text = step.conclusion ?: step.status,
                              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                              color = stepColor,
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GitHubPullsAndIssuesTab(
  uiState: IdeUiState,
  onSwitchToRepos: () -> Unit,
  onInspectPullRequest: (Int?) -> Unit,
  onCreatePullRequest: (title: String, body: String, head: String, base: String) -> Unit,
  onMergePullRequest: (prNumber: Int, method: String) -> Unit,
  onSubmitPrReview: (prNumber: Int, event: String, body: String) -> Unit,
  onCreateIssue: (title: String, body: String) -> Unit,
  onToggleIssueState: (GitHubIssue) -> Unit,
  onCommentOnIssue: (issueNumber: Int, comment: String) -> Unit,
) {
  val palette = LocalIdePalette.current
  val linked = uiState.linkedGitRepo
  val selectedRepo = uiState.selectedGitHubRepo
  val activeFullName = linked?.fullName ?: selectedRepo?.fullName
  val defaultBase = selectedRepo?.defaultBranch ?: "main"
  val currentBranch = linked?.branch ?: uiState.selectedGitHubBranch

  var mode by remember { mutableIntStateOf(0) } // 0 = PRs, 1 = Issues
  var showCreatePrDialog by remember { mutableStateOf(false) }
  var showCreateIssueDialog by remember { mutableStateOf(false) }
  var reviewPrNumber by remember { mutableStateOf<Int?>(null) }

  reviewPrNumber?.let { prNum ->
    var reviewEvent by remember { mutableStateOf("APPROVE") }
    var reviewBody by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { reviewPrNumber = null },
      title = { Text("Review Pull Request #$prNum") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          val events = listOf("APPROVE" to "Approve", "COMMENT" to "Comment", "REQUEST_CHANGES" to "Changes")
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            events.forEachIndexed { idx, (key, label) ->
              SegmentedButton(
                selected = reviewEvent == key,
                onClick = { reviewEvent = key },
                shape = SegmentedButtonDefaults.itemShape(idx, events.size),
              ) {
                Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
          OutlinedTextField(
            value = reviewBody,
            onValueChange = { reviewBody = it },
            label = { Text("Review summary / comment") },
            placeholder = { Text("Looks clean — ready for Cloudflare deploy.") },
            maxLines = 4,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().testTag("pr_review_body_input"),
          )
        }
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onSubmitPrReview(prNum, reviewEvent, reviewBody)
            reviewPrNumber = null
          },
          enabled = reviewEvent == "APPROVE" || reviewBody.isNotBlank(),
          modifier = Modifier.testTag("pr_review_submit_button"),
        ) {
          Text("Submit Review")
        }
      },
      dismissButton = {
        TextButton(onClick = { reviewPrNumber = null }) { Text("Cancel") }
      },
    )
  }

  if (showCreatePrDialog) {
    var prTitle by remember { mutableStateOf("") }
    var prBody by remember { mutableStateOf("") }
    var prHead by remember { mutableStateOf(currentBranch) }
    var prBase by remember { mutableStateOf(defaultBase) }
    AlertDialog(
      onDismissRequest = { showCreatePrDialog = false },
      title = { Text("Create Pull Request") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = prTitle, onValueChange = { prTitle = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = prHead, onValueChange = { prHead = it }, label = { Text("Head") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = prBase, onValueChange = { prBase = it }, label = { Text("Base") }, singleLine = true, modifier = Modifier.weight(1f))
          }
          OutlinedTextField(value = prBody, onValueChange = { prBody = it }, label = { Text("Description") }, maxLines = 4, modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onCreatePullRequest(prTitle, prBody, prHead, prBase)
            showCreatePrDialog = false
          },
          enabled = prTitle.isNotBlank() && prHead.isNotBlank() && prBase.isNotBlank(),
        ) { Text("Create PR") }
      },
      dismissButton = { TextButton(onClick = { showCreatePrDialog = false }) { Text("Cancel") } },
    )
  }

  if (showCreateIssueDialog) {
    var issueTitle by remember { mutableStateOf("") }
    var issueBody by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreateIssueDialog = false },
      title = { Text("New GitHub Issue") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = issueTitle, onValueChange = { issueTitle = it }, label = { Text("Issue title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(value = issueBody, onValueChange = { issueBody = it }, label = { Text("Details") }, maxLines = 4, modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onCreateIssue(issueTitle, issueBody)
            showCreateIssueDialog = false
          },
          enabled = issueTitle.isNotBlank(),
        ) { Text("Submit Issue") }
      },
      dismissButton = { TextButton(onClick = { showCreateIssueDialog = false }) { Text("Cancel") } },
    )
  }

  if (activeFullName == null) {
    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("NO REPOSITORY SELECTED", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
          Text(
            "Select or clone a GitHub repository in Repos to inspect Pull Requests, review code diffs, merge PRs, and manage Issues.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
          )
          FilledTonalButton(onClick = onSwitchToRepos, shape = RoundedCornerShape(10.dp)) {
            Text("Open Repositories")
          }
        }
      }
    }
    return
  }

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = activeFullName,
      style = MaterialTheme.typography.labelSmall,
      color = palette.activePillFg,
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      SegmentedButton(
        selected = mode == 0,
        onClick = { mode = 0 },
        shape = SegmentedButtonDefaults.itemShape(0, 2),
      ) {
        Text("PRs (${uiState.gitHubPullRequests.size})", maxLines = 1)
      }
      SegmentedButton(
        selected = mode == 1,
        onClick = { mode = 1 },
        shape = SegmentedButtonDefaults.itemShape(1, 2),
      ) {
        Text("Issues (${uiState.gitHubIssues.size})", maxLines = 1)
      }
    }

    if (mode == 0) {
      if (uiState.gitHubCreds != null) {
        FilledTonalButton(
          onClick = { showCreatePrDialog = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(42.dp).testTag("create_pr_button"),
        ) {
          Icon(Icons.Outlined.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("New Pull Request")
        }
      }

      if (uiState.gitHubPullRequests.isEmpty()) {
        Surface(color = palette.chromeSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
          Text("No open pull requests in $activeFullName.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, modifier = Modifier.padding(14.dp))
        }
      } else {
        uiState.gitHubPullRequests.forEach { pr ->
          val expanded = uiState.selectedPrNumber == pr.number
          Surface(
            onClick = { onInspectPullRequest(pr.number) },
            color = if (expanded) palette.selectedRowBg else palette.chromePill,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = EmeraldLive.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                  Text("#${pr.number}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldLive, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(pr.title, style = MaterialTheme.typography.labelLarge, color = palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
              }
              Text(
                text = "${pr.head.ref} -> ${pr.base.ref} • @${pr.user.login}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
              )

              AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  if (uiState.selectedPrFiles.isEmpty()) {
                    Text(
                      text = if (uiState.isGitHubBusy) "Loading changed files…" else "Tap PR to review file patches.",
                      style = MaterialTheme.typography.labelSmall,
                      color = palette.textMuted,
                    )
                  } else {
                    uiState.selectedPrFiles.take(8).forEach { df ->
                      RemotePatchDiffCard(df)
                    }
                  }
                  if (uiState.gitHubCreds != null) {
                    OutlinedButton(
                      onClick = { reviewPrNumber = pr.number },
                      enabled = !uiState.isGitHubBusy,
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.fillMaxWidth().testTag("review_pr_button_${pr.number}"),
                    ) {
                      Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Submit PR Review #${pr.number}")
                    }
                    FilledTonalButton(
                      onClick = { onMergePullRequest(pr.number, "squash") },
                      enabled = !uiState.isGitHubBusy,
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Icon(Icons.Outlined.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Squash & Merge #${pr.number}")
                    }
                  }
                }
              }
            }
          }
        }
      }
    } else {
      if (uiState.gitHubCreds != null) {
        FilledTonalButton(
          onClick = { showCreateIssueDialog = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(42.dp).testTag("create_issue_button"),
        ) {
          Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("New Issue")
        }
      }

      if (uiState.gitHubIssues.isEmpty()) {
        Surface(color = palette.chromeSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
          Text("No open issues in $activeFullName.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, modifier = Modifier.padding(14.dp))
        }
      } else {
        uiState.gitHubIssues.forEach { issue ->
          var expanded by remember(issue.number) { mutableStateOf(false) }
          var commentText by remember(issue.number) { mutableStateOf("") }
          val isOpen = issue.state == "open"
          Surface(
            onClick = { expanded = !expanded },
            color = if (expanded) palette.selectedRowBg else palette.chromePill,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                  color = (if (isOpen) EmeraldLive else RoseError).copy(alpha = 0.2f),
                  shape = RoundedCornerShape(6.dp),
                ) {
                  Text(
                    text = "#${issue.number}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOpen) EmeraldLive else RoseError,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
                Text(issue.title, style = MaterialTheme.typography.labelLarge, color = palette.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
              }
              Text(
                text = "@${issue.user.login} • ${issue.comments} comment(s) • ${issue.state}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
              )
              AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  if (!issue.body.isNullOrBlank()) {
                    Text(issue.body, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                  }
                  if (uiState.gitHubCreds != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                      OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Add comment…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                      )
                      IconButton(
                        onClick = {
                          onCommentOnIssue(issue.number, commentText)
                          commentText = ""
                        },
                        enabled = commentText.isNotBlank(),
                      ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send comment", tint = palette.activePillFg)
                      }
                    }
                    OutlinedButton(
                      onClick = { onToggleIssueState(issue) },
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(if (isOpen) "Close Issue #${issue.number}" else "Reopen Issue #${issue.number}")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RemotePatchDiffCard(df: GitHubDiffFile) {
  val palette = LocalIdePalette.current
  Surface(color = palette.chromeSurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(df.filename, style = MaterialTheme.typography.labelSmall, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("+${df.additions} -${df.deletions}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono), color = EmeraldLive)
      }
      if (!df.patch.isNullOrBlank()) {
        df.patch.lines().take(12).forEach { line ->
          val lineColor =
            when {
              line.startsWith("+") -> EmeraldLive
              line.startsWith("-") -> RoseError
              else -> palette.textMuted
            }
          Text(
            text = line,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontSize = 10.sp),
            color = lineColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun GitChangedFileCard(
  change: GitChangedFile,
  onToggleStage: () -> Unit,
  onOpenFile: () -> Unit,
  onDiscard: () -> Unit,
) {
  val palette = LocalIdePalette.current
  var showInlineDiff by remember { mutableStateOf(false) }

  val (badgeChar, badgeColor) =
    when (change.status) {
      GitFileChangeStatus.ADDED -> "A" to EmeraldLive
      GitFileChangeStatus.MODIFIED -> "M" to AmberWarn
      GitFileChangeStatus.DELETED -> "D" to RoseError
    }

  Surface(
    color = if (change.isStaged) palette.selectedRowBg else palette.chromeSurface,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
          checked = change.isStaged,
          onCheckedChange = { onToggleStage() },
          modifier = Modifier.size(36.dp),
        )
        Surface(
          color = badgeColor.copy(alpha = 0.20f),
          shape = RoundedCornerShape(6.dp),
        ) {
          Text(
            text = badgeChar,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
            color = badgeColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
        Spacer(Modifier.width(6.dp))
        Text(
          text = change.path,
          style = MaterialTheme.typography.labelMedium,
          color = palette.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).clickable { showInlineDiff = !showInlineDiff },
        )
        IconButton(onClick = { showInlineDiff = !showInlineDiff }, modifier = Modifier.size(30.dp)) {
          Icon(
            imageVector = if (showInlineDiff) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.Difference,
            contentDescription = "Toggle diff",
            tint = palette.textSecondary,
            modifier = Modifier.size(16.dp),
          )
        }
        if (change.status != GitFileChangeStatus.DELETED) {
          IconButton(onClick = onOpenFile, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "Open in editor", tint = palette.textSecondary, modifier = Modifier.size(15.dp))
          }
        }
        IconButton(onClick = onDiscard, modifier = Modifier.size(30.dp)) {
          Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Discard change", tint = RoseError, modifier = Modifier.size(15.dp))
        }
      }

      AnimatedVisibility(visible = showInlineDiff) {
        val diffLines = remember(change.baseContent, change.currentContent) {
          CodeFormatter.computeLineDiff(change.baseContent, change.currentContent).filter { it.type != DiffType.UNCHANGED }
        }
        Column(
          modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          if (diffLines.isEmpty()) {
            Text("(whitespace or metadata diff)", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
          } else {
            diffLines.take(18).forEach { line ->
              val bg =
                if (line.type == DiffType.ADDED) EmeraldLive.copy(alpha = 0.15f)
                else RoseError.copy(alpha = 0.15f)
              val prefix = if (line.type == DiffType.ADDED) "+" else "-"
              Text(
                text = "$prefix ${line.text}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontSize = 10.sp),
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GitCloneAndReposTab(
  uiState: IdeUiState,
  onStartGitHubOAuth: (String) -> Unit,
  onCancelGitHubOAuth: () -> Unit,
  onConnectGitHubToken: (String) -> Unit,
  onRefreshRepos: () -> Unit,
  onSelectRepo: (GitHubRepo?) -> Unit,
  onSelectBranch: (String) -> Unit,
  onCreateBranch: (String) -> Unit,
  onCloneRepository: (repoUrlOrSlug: String, branchOverride: String?, mergeIntoCurrentWorker: Boolean) -> Unit,
  onCreateGitHubRepo: (name: String, description: String, isPrivate: Boolean) -> Unit,
  onLinkSelectedRepo: () -> Unit,
  onPullFilesToWorker: (List<String>) -> Unit,
) {
  val palette = LocalIdePalette.current
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current

  var cloneUrlInput by remember { mutableStateOf("") }
  var cloneBranchInput by remember { mutableStateOf("") }
  var mergeIntoCurrent by remember { mutableStateOf(false) }
  var showCreateRepoDialog by remember { mutableStateOf(false) }
  var ghAuthMode by remember { mutableIntStateOf(0) } // 0 = OAuth, 1 = PAT

  if (showCreateRepoDialog) {
    var repoName by remember { mutableStateOf(uiState.workspace?.scriptName ?: "my-cloudflare-worker") }
    var repoDesc by remember { mutableStateOf("Cloudflare Worker managed in EdgeCode") }
    var isPrivate by remember { mutableStateOf(true) }
    AlertDialog(
      onDismissRequest = { showCreateRepoDialog = false },
      title = { Text("Create GitHub Repository") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = repoName,
            onValueChange = { repoName = it },
            label = { Text("Repository name") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = repoDesc,
            onValueChange = { repoDesc = it },
            label = { Text("Description") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Private repository", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
            Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
          }
        }
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onCreateGitHubRepo(repoName, repoDesc, isPrivate)
            showCreateRepoDialog = false
          },
          enabled = repoName.isNotBlank(),
        ) { Text("Create & Link") }
      },
      dismissButton = { TextButton(onClick = { showCreateRepoDialog = false }) { Text("Cancel") } },
    )
  }

  val selectedRepo = uiState.selectedGitHubRepo
  if (selectedRepo != null && uiState.gitHubCreds != null) {
    var branchMenuExpanded by remember { mutableStateOf(false) }
    var showCreateBranchDialog by remember { mutableStateOf(false) }
    val checkedPaths = remember(selectedRepo.fullName, uiState.selectedGitHubBranch) { mutableStateListOf<String>() }

    if (showCreateBranchDialog) {
      var newBranch by remember { mutableStateOf("") }
      AlertDialog(
        onDismissRequest = { showCreateBranchDialog = false },
        title = { Text("Create Branch from ${uiState.selectedGitHubBranch}") },
        text = {
          OutlinedTextField(value = newBranch, onValueChange = { newBranch = it }, label = { Text("Branch name") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
          FilledTonalButton(
            onClick = {
              onCreateBranch(newBranch)
              showCreateBranchDialog = false
            },
            enabled = newBranch.isNotBlank(),
          ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { showCreateBranchDialog = false }) { Text("Cancel") } },
      )
    }

    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelectRepo(null) }, modifier = Modifier.size(34.dp)) {
          Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to repos", modifier = Modifier.size(18.dp))
        }
        Text(
          text = selectedRepo.fullName,
          style = MaterialTheme.typography.titleSmall,
          color = palette.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        BoxBranchPicker(
          currentBranch = uiState.selectedGitHubBranch,
          branches = uiState.gitHubBranches.map { it.name },
          expanded = branchMenuExpanded,
          onOpenChange = { branchMenuExpanded = it },
          onSelect = onSelectBranch,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { showCreateBranchDialog = true }, modifier = Modifier.size(40.dp)) {
          Icon(Icons.Outlined.Add, contentDescription = "New branch", modifier = Modifier.size(18.dp))
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalButton(
          onClick = { onCloneRepository(selectedRepo.fullName, uiState.selectedGitHubBranch, false) },
          enabled = !uiState.isGitHubBusy,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1.2f).testTag("github_clone_selected_repo_button"),
        ) {
          Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(5.dp))
          Text("Clone Repo", fontSize = 12.sp)
        }
        if (uiState.workspace != null) {
          OutlinedButton(
            onClick = onLinkSelectedRepo,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Link", fontSize = 12.sp)
          }
        }
      }

      HorizontalDivider(color = palette.treeLine)
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("SELECT FILES (${uiState.gitHubTreeFiles.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
        FilledTonalButton(
          onClick = { onPullFilesToWorker(checkedPaths.toList()) },
          enabled = checkedPaths.isNotEmpty() && !uiState.isGitHubBusy,
        ) {
          Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text("Pull (${checkedPaths.size})", fontSize = 11.sp)
        }
      }

      uiState.gitHubTreeFiles.take(50).forEach { item ->
        val isChecked = item.path in checkedPaths
        Surface(
          onClick = {
            if (isChecked) checkedPaths.remove(item.path) else checkedPaths.add(item.path)
          },
          shape = RoundedCornerShape(8.dp),
          color = if (isChecked) palette.selectedRowBg else palette.chromeSurface,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = isChecked,
              onCheckedChange = { c ->
                if (c) checkedPaths.add(item.path) else checkedPaths.remove(item.path)
              },
            )
            Text(item.path, style = MaterialTheme.typography.labelSmall, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
    return
  }

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("CLONE GITHUB REPOSITORY", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
        OutlinedTextField(
          value = cloneUrlInput,
          onValueChange = { cloneUrlInput = it },
          label = { Text("GitHub URL or owner/repo") },
          placeholder = { Text("https://github.com/owner/worker-repo") },
          singleLine = true,
          trailingIcon = {
            IconButton(onClick = { cloneUrlInput = clipboard.getText()?.text.orEmpty() }) {
              Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste repo URL")
            }
          },
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().testTag("git_clone_url_input"),
        )
        OutlinedTextField(
          value = cloneBranchInput,
          onValueChange = { cloneBranchInput = it },
          label = { Text("Branch (optional)") },
          placeholder = { Text("default branch") },
          singleLine = true,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.workspace != null) {
          Row(
            modifier = Modifier.fillMaxWidth().clickable { mergeIntoCurrent = !mergeIntoCurrent },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("Merge into '${uiState.workspace.scriptName}'", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Switch(checked = mergeIntoCurrent, onCheckedChange = { mergeIntoCurrent = it })
          }
        }
        FilledTonalButton(
          onClick = { onCloneRepository(cloneUrlInput, cloneBranchInput.takeIf { it.isNotBlank() }, mergeIntoCurrent) },
          enabled = cloneUrlInput.isNotBlank() && !uiState.isGitHubBusy,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().height(44.dp).testTag("git_clone_submit_button"),
        ) {
          if (uiState.isGitHubBusy && !uiState.isWaitingGitHubOAuth) {
            EdgeLoadingIndicator(size = 18.dp)
          } else {
            Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (mergeIntoCurrent && uiState.workspace != null) "Clone & Merge Files" else "Clone as Worker Project")
          }
        }
      }
    }

    uiState.gitHubError?.let { err ->
      Text(err, style = MaterialTheme.typography.labelSmall, color = RoseError)
    }

    HorizontalDivider(color = palette.treeLine)

    if (uiState.gitHubCreds == null) {
      Text("AUTHORIZE GITHUB ACCOUNT", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(selected = ghAuthMode == 0, onClick = { ghAuthMode = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
          Text("OAuth Device", maxLines = 1)
        }
        SegmentedButton(selected = ghAuthMode == 1, onClick = { ghAuthMode = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
          Text("PAT Token", maxLines = 1)
        }
      }

      if (ghAuthMode == 0) {
        val deviceSession = uiState.gitHubDeviceSession
        if (uiState.isWaitingGitHubOAuth && deviceSession != null) {
          Surface(color = palette.activePillBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Verification Code:", style = MaterialTheme.typography.labelSmall, color = palette.activePillFg)
              Text(
                text = deviceSession.userCode,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono, letterSpacing = 2.sp),
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                  onClick = {
                    clipboard.setText(AnnotatedString(deviceSession.userCode))
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deviceSession.verificationUri))) }
                  },
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Copy & Open", fontSize = 11.sp)
                }
                TextButton(onClick = onCancelGitHubOAuth) { Text("Cancel") }
              }
            }
          }
        } else {
          FilledTonalButton(
            onClick = { onStartGitHubOAuth("") },
            enabled = !uiState.isGitHubBusy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("panel_github_oauth_button"),
          ) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Authorize GitHub via OAuth 2.0")
          }
        }
      } else {
        var patInput by remember { mutableStateOf("") }
        OutlinedButton(
          onClick = {
            runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GitHubOAuthManager.buildClassicPatCreationUrl())))
            }
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Generate GitHub Token in Browser")
        }
        OutlinedTextField(
          value = patInput,
          onValueChange = { patInput = it },
          label = { Text("GitHub Personal Access Token") },
          placeholder = { Text("ghp_xxxxxxxxxxxx") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { patInput = clipboard.getText()?.text.orEmpty() }) {
              Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste token")
            }
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("github_pat_input"),
        )
        FilledTonalButton(
          onClick = { onConnectGitHubToken(patInput) },
          enabled = patInput.isNotBlank() && !uiState.isGitHubBusy,
          modifier = Modifier.fillMaxWidth().testTag("connect_github_button"),
        ) {
          Text("Connect GitHub Token")
        }
      }
    } else {
      var repoSearchQuery by remember { mutableStateOf("") }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "YOUR REPOSITORIES (${uiState.gitHubRepos.size})",
          style = MaterialTheme.typography.labelSmall,
          color = palette.textMuted,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { showCreateRepoDialog = true }, modifier = Modifier.size(34.dp)) {
          Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Create GitHub repo", tint = palette.activePillFg, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRefreshRepos, modifier = Modifier.size(34.dp)) {
          Icon(Icons.Outlined.Refresh, contentDescription = "Refresh repos", modifier = Modifier.size(17.dp))
        }
      }

      OutlinedTextField(
        value = repoSearchQuery,
        onValueChange = { repoSearchQuery = it },
        placeholder = { Text("Filter repositories…") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(17.dp)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      )

      val filteredRepos =
        remember(uiState.gitHubRepos, repoSearchQuery) {
          val q = repoSearchQuery.trim().lowercase()
          if (q.isEmpty()) uiState.gitHubRepos
          else uiState.gitHubRepos.filter { it.fullName.lowercase().contains(q) || it.description.orEmpty().lowercase().contains(q) }
        }

      if (uiState.isGitHubBusy && uiState.gitHubRepos.isEmpty()) {
        EdgeSkeletonRepoList(count = 5)
      } else {
        filteredRepos.forEach { repo ->
          Surface(
            onClick = { onSelectRepo(repo) },
            shape = RoundedCornerShape(12.dp),
            color = palette.chromePill,
            modifier = Modifier.fillMaxWidth().testTag("github_repo_${repo.name}"),
          ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(17.dp))
              Spacer(Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(repo.fullName, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                  if (repo.private) {
                    Icon(Icons.Outlined.Lock, contentDescription = "Private repo", tint = palette.textSecondary, modifier = Modifier.size(13.dp))
                  }
                }
                Text("branch: ${repo.defaultBranch}", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
              }
              FilledTonalButton(
                onClick = { onCloneRepository(repo.fullName, repo.defaultBranch, false) },
                enabled = !uiState.isGitHubBusy,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp),
              ) {
                Text("Clone", fontSize = 11.sp)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BoxBranchPicker(
  currentBranch: String,
  branches: List<String>,
  expanded: Boolean,
  onOpenChange: (Boolean) -> Unit,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    OutlinedButton(onClick = { onOpenChange(true) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(40.dp)) {
      Text("Branch: $currentBranch", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.width(4.dp))
      Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    EdgeMenu(
      expanded = expanded,
      onDismissRequest = { onOpenChange(false) },
      minWidth = 204.dp,
      maxWidth = 268.dp,
    ) {
      EdgeMenuHeader(
        title = "Switch Branch",
        badgeText = "${branches.size}",
        leadingIcon = Icons.Outlined.AccountTree,
      )
      branches.forEach { b ->
        EdgeMenuCompactItem(
          label = b,
          icon = Icons.Outlined.AccountTree,
          selected = b == currentBranch,
          monospaced = true,
          onClick = {
            onOpenChange(false)
            onSelect(b)
          },
        )
      }
    }
  }
}

@Composable
private fun LocalWorkerDiffView(
  uiState: IdeUiState,
  onRevertFile: (String) -> Unit,
  onOpenFullDiffReview: (() -> Unit)? = null,
) {
  val palette = LocalIdePalette.current
  val active = uiState.activeFile
  Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Text("LOCAL DIFF VS CLOUDFLARE", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
      if (onOpenFullDiffReview != null && uiState.hasUnsavedChanges) {
        TextButton(onClick = onOpenFullDiffReview, modifier = Modifier.height(30.dp)) {
          Text("Full Diff", fontSize = 11.sp)
        }
      }
    }
    if (active == null) {
      Text("Select a file to compare edits against deployed code.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
      return
    }
    if (!active.isDirty) {
      Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "${active.fileName} has no uncommitted Cloudflare changes.",
          style = MaterialTheme.typography.bodySmall,
          color = palette.textSecondary,
          modifier = Modifier.padding(14.dp),
        )
      }
      return
    }

    OutlinedButton(onClick = { onRevertFile(active.path) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
      Text("Revert ${active.fileName}")
    }

    val diffLines = remember(active.originalContent, active.content) {
      CodeFormatter.computeLineDiff(active.originalContent, active.content).filter { it.type != DiffType.UNCHANGED }
    }

    Column(
      modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      diffLines.forEach { line ->
        val bg =
          if (line.type == DiffType.ADDED) EmeraldLive.copy(alpha = 0.16f)
          else RoseError.copy(alpha = 0.16f)
        val prefix = if (line.type == DiffType.ADDED) "+" else "-"
        Row(
          modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Text(
            text = "$prefix ${line.text}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
