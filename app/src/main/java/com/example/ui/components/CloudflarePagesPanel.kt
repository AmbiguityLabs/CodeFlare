package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.CfPagesProject
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.IdeUiState

@Composable
fun CloudflarePagesPanel(
  uiState: IdeUiState,
  onRefreshProjects: () -> Unit,
  onSelectProject: (CfPagesProject?) -> Unit,
  onCreateProject: (String, String) -> Unit,
  onDeleteProject: (String) -> Unit,
  onTriggerDeploy: (String) -> Unit,
  onRetryDeploy: (String, String) -> Unit,
  onRollbackDeploy: (String, String) -> Unit,
  onFetchLogs: (String, String) -> Unit,
  onSaveEnvVar: (projectName: String, environment: String, key: String, value: String, isSecret: Boolean) -> Unit,
  onUpdateBuildConfig: (projectName: String, productionBranch: String, buildCommand: String, destinationDir: String, rootDir: String) -> Unit,
  onUpdateBindings: (projectName: String, environment: String, kvBindings: Map<String, String>, d1Bindings: Map<String, String>, r2Bindings: Map<String, String>) -> Unit,
  onCreatePagesSpecialFile: (String) -> Unit,
  onAddDomain: (String, String) -> Unit,
  onDeleteDomain: (String, String) -> Unit,
  onPickPagesFolder: () -> Unit,
  onPickPagesZip: () -> Unit,
  onPickPagesFiles: () -> Unit,
  onStageWorkspaceForPages: () -> Unit,
  onTogglePagesZipStripRoot: (Boolean) -> Unit,
  onClearStagedPagesAssets: () -> Unit,
  onDeployDirectPages: (projectName: String, branch: String?, commitMessage: String?) -> Unit,
) {
  val palette = LocalIdePalette.current
  var showCreateDialog by remember { mutableStateOf(false) }

  if (showCreateDialog) {
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text("Create Cloudflare Pages Project") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Project name") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
          OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Production branch") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onCreateProject(name, branch)
            showCreateDialog = false
          },
          enabled = name.isNotBlank(),
        ) { Text("Create") }
      },
      dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
    )
  }

  if (uiState.isLocalScratchpad) {
    var showPagesInfo by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("PAGES", style = MaterialTheme.typography.labelSmall, color = palette.textMuted, modifier = Modifier.weight(1f))
        IconButton(onClick = { showPagesInfo = !showPagesInfo }, modifier = Modifier.size(28.dp)) {
          Icon(Icons.Outlined.Info, contentDescription = "Pages info", tint = palette.textMuted, modifier = Modifier.size(16.dp))
        }
      }
      AnimatedVisibility(visible = showPagesInfo) {
        Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Connect Cloudflare to deploy Pages sites, builds & domains.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(12.dp),
          )
        }
      }
      PagesSpecialFilesCard(onCreatePagesSpecialFile = onCreatePagesSpecialFile)
    }
    return
  }

  val selected = uiState.selectedPagesProject
  if (selected == null) {
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("CLOUDFLARE PAGES (${uiState.pagesProjects.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted, modifier = Modifier.weight(1f))
        IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.size(30.dp).testTag("create_pages_button")) {
          Icon(Icons.Outlined.Add, contentDescription = "New Pages Project", modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onRefreshProjects, modifier = Modifier.size(30.dp)) {
          Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Pages", modifier = Modifier.size(17.dp))
        }
      }

      if (uiState.isPagesBusy && uiState.pagesProjects.isEmpty()) {
        EdgeSkeletonProjectList(count = 4)
      } else if (uiState.pagesProjects.isEmpty()) {
        Text("No Pages projects found. Tap + to create one.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
      } else {
        if (uiState.isPagesBusy) {
          EdgeLoadingIndicator(size = 20.dp)
        }
        uiState.pagesProjects.forEach { proj ->
          Surface(
            onClick = { onSelectProject(proj) },
            shape = RoundedCornerShape(14.dp),
            color = palette.chromePill,
            modifier = Modifier.fillMaxWidth().testTag("pages_project_${proj.name}"),
          ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Outlined.Web, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(proj.name, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                Text(
                  text = proj.subdomain ?: "${proj.name}.pages.dev",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                proj.source?.config?.let { cfg ->
                  if (!cfg.owner.isNullOrBlank()) {
                    Text(
                      text = "GitHub: ${cfg.owner}/${cfg.repoName} (${cfg.productionBranch ?: proj.productionBranch})",
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
    }
  } else {
    PagesProjectDetailView(
      project = selected,
      uiState = uiState,
      onBack = { onSelectProject(null) },
      onDeleteProject = { onDeleteProject(selected.name) },
      onTriggerDeploy = { onTriggerDeploy(selected.name) },
      onRetryDeploy = { depId -> onRetryDeploy(selected.name, depId) },
      onRollbackDeploy = { depId -> onRollbackDeploy(selected.name, depId) },
      onFetchLogs = { depId -> onFetchLogs(selected.name, depId) },
      onSaveEnvVar = { env, k, v, sec -> onSaveEnvVar(selected.name, env, k, v, sec) },
      onUpdateBuildConfig = { branch, cmd, dest, root -> onUpdateBuildConfig(selected.name, branch, cmd, dest, root) },
      onUpdateBindings = { env, kv, d1, r2 -> onUpdateBindings(selected.name, env, kv, d1, r2) },
      onCreatePagesSpecialFile = onCreatePagesSpecialFile,
      onAddDomain = { d -> onAddDomain(selected.name, d) },
      onDeleteDomain = { d -> onDeleteDomain(selected.name, d) },
      onPickPagesFolder = onPickPagesFolder,
      onPickPagesZip = onPickPagesZip,
      onPickPagesFiles = onPickPagesFiles,
      onStageWorkspaceForPages = onStageWorkspaceForPages,
      onTogglePagesZipStripRoot = onTogglePagesZipStripRoot,
      onClearStagedPagesAssets = onClearStagedPagesAssets,
      onDeployDirectPages = { branch, msg -> onDeployDirectPages(selected.name, branch, msg) },
    )
  }
}

@Composable
private fun PagesProjectDetailView(
  project: CfPagesProject,
  uiState: IdeUiState,
  onBack: () -> Unit,
  onDeleteProject: () -> Unit,
  onTriggerDeploy: () -> Unit,
  onRetryDeploy: (String) -> Unit,
  onRollbackDeploy: (String) -> Unit,
  onFetchLogs: (String) -> Unit,
  onSaveEnvVar: (environment: String, key: String, value: String, isSecret: Boolean) -> Unit,
  onUpdateBuildConfig: (productionBranch: String, buildCommand: String, destinationDir: String, rootDir: String) -> Unit,
  onUpdateBindings: (environment: String, kvBindings: Map<String, String>, d1Bindings: Map<String, String>, r2Bindings: Map<String, String>) -> Unit,
  onCreatePagesSpecialFile: (String) -> Unit,
  onAddDomain: (String) -> Unit,
  onDeleteDomain: (String) -> Unit,
  onPickPagesFolder: () -> Unit,
  onPickPagesZip: () -> Unit,
  onPickPagesFiles: () -> Unit,
  onStageWorkspaceForPages: () -> Unit,
  onTogglePagesZipStripRoot: (Boolean) -> Unit,
  onClearStagedPagesAssets: () -> Unit,
  onDeployDirectPages: (branch: String?, commitMessage: String?) -> Unit,
) {
  val palette = LocalIdePalette.current
  val clipboardManager = LocalClipboardManager.current
  var envMode by remember { mutableStateOf("production") }
  var envKey by remember { mutableStateOf("") }
  var envVal by remember { mutableStateOf("") }
  var isSecret by remember { mutableStateOf(false) }
  var domainInput by remember { mutableStateOf("") }

  var directTargetEnv by remember { mutableStateOf("production") }
  var previewBranchName by remember { mutableStateOf("preview") }
  var commitMessage by remember { mutableStateOf("") }
  var showAllStagedFiles by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to Pages list")
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(project.name, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
        Text(project.subdomain ?: "${project.name}.pages.dev", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
      }
      IconButton(onClick = onDeleteProject, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete Pages project", tint = RoseError, modifier = Modifier.size(16.dp))
      }
    }

    // Direct Upload (Folder / ZIP / Files / Workspace)
    Surface(
      color = palette.chromePill,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().testTag("pages_direct_upload_card"),
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "DIRECT UPLOAD",
            style = MaterialTheme.typography.labelSmall,
            color = palette.activePillFg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
          )
          Surface(color = palette.activePillBg, shape = RoundedCornerShape(6.dp)) {
            Text(
              text = "BLAKE3 MANIFEST",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = palette.activePillFg,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }

        Text(
          text = "Deploy static sites or _worker.js bundles directly from a folder, ZIP archive, files, or open workspace.",
          style = MaterialTheme.typography.labelSmall,
          color = palette.textSecondary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = onPickPagesFolder,
            enabled = !uiState.isPagesDirectUploading,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).testTag("pages_pick_folder_button"),
          ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("Folder", fontSize = 12.sp, maxLines = 1)
          }
          OutlinedButton(
            onClick = onPickPagesZip,
            enabled = !uiState.isPagesDirectUploading,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).testTag("pages_pick_zip_button"),
          ) {
            Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("ZIP", fontSize = 12.sp, maxLines = 1)
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = onPickPagesFiles,
            enabled = !uiState.isPagesDirectUploading,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).testTag("pages_pick_files_button"),
          ) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("Files", fontSize = 12.sp, maxLines = 1)
          }
          OutlinedButton(
            onClick = onStageWorkspaceForPages,
            enabled = uiState.workspace != null && !uiState.isPagesDirectUploading,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).testTag("pages_stage_workspace_button"),
          ) {
            Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("Workspace", fontSize = 12.sp, maxLines = 1)
          }
        }

        val stagedAssets = uiState.pagesStagedPreparedAssets
        if (stagedAssets.isNotEmpty()) {
          val totalBytes = stagedAssets.sumOf { it.actualByteSize }
          val oversizedCount =
            stagedAssets.count {
              it.actualByteSize > com.example.data.local.DeviceFileHelper.CLOUDFLARE_PAGES_MAX_SINGLE_FILE_BYTES
            }
          val hasIndexHtml = stagedAssets.any { it.normalizedPath.equals("/index.html", ignoreCase = true) }
          val hasWorkerBundle = stagedAssets.any { it.normalizedPath == "/_worker.js" }

          Surface(
            color = palette.editorBg,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = uiState.pagesDirectUploadSourceLabel ?: "Staged Bundle",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text =
                      if (oversizedCount > 0) {
                        "${stagedAssets.size} files • ${formatReadableBytes(totalBytes)} • $oversizedCount file(s) > 25 MiB Pages deploy limit"
                      } else {
                        "${stagedAssets.size} files • ${formatReadableBytes(totalBytes)}"
                      },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (oversizedCount > 0) AmberWarn else palette.textSecondary,
                  )
                }
                if (!uiState.isPagesDirectUploading) {
                  IconButton(onClick = onClearStagedPagesAssets, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear staged files", modifier = Modifier.size(15.dp))
                  }
                }
              }

              if (uiState.pagesIsZipSource) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "Strip single root folder from ZIP",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                  )
                  Switch(
                    checked = uiState.pagesStripZipRootFolder,
                    onCheckedChange = onTogglePagesZipStripRoot,
                    enabled = !uiState.isPagesDirectUploading,
                  )
                }
              }

              if (!hasIndexHtml || hasWorkerBundle) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                  if (hasWorkerBundle) {
                    Surface(color = palette.activePillBg, shape = RoundedCornerShape(6.dp)) {
                      Text(
                        text = "_worker.js DETECTED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.activePillFg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      )
                    }
                  }
                  if (!hasIndexHtml) {
                    Text(
                      text = "No root /index.html found",
                      style = MaterialTheme.typography.labelSmall,
                      color = AmberWarn,
                    )
                  }
                }
              }

              val previewLimit = if (showAllStagedFiles) stagedAssets.size else 5
              stagedAssets.take(previewLimit).forEach { asset ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = asset.normalizedPath,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  Text(
                    text = asset.hash.take(8),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = palette.activePillFg,
                  )
                  Text(
                    text = formatReadableBytes(asset.bytes.size.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                  )
                }
              }

              if (stagedAssets.size > 5) {
                TextButton(
                  onClick = { showAllStagedFiles = !showAllStagedFiles },
                  modifier = Modifier.align(Alignment.End),
                ) {
                  Text(
                    text = if (showAllStagedFiles) "Show fewer" else "Show all ${stagedAssets.size} files",
                    fontSize = 11.sp,
                  )
                }
              }
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("production" to "Production (${project.productionBranch})", "preview" to "Preview Branch").forEach { (mode, label) ->
              val active = directTargetEnv == mode
              Surface(
                onClick = { if (!uiState.isPagesDirectUploading) directTargetEnv = mode },
                shape = RoundedCornerShape(8.dp),
                color = if (active) palette.activePillBg else palette.editorBg,
                modifier = Modifier.weight(1f),
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (active) palette.activePillFg else palette.textSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(vertical = 7.dp, horizontal = 8.dp),
                )
              }
            }
          }

          if (directTargetEnv == "preview") {
            OutlinedTextField(
              value = previewBranchName,
              onValueChange = { previewBranchName = it },
              label = { Text("Preview branch name") },
              singleLine = true,
              enabled = !uiState.isPagesDirectUploading,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth(),
            )
          }

          OutlinedTextField(
            value = commitMessage,
            onValueChange = { commitMessage = it },
            label = { Text("Deploy note (optional)") },
            placeholder = { Text("Direct upload via EdgeCode") },
            singleLine = true,
            enabled = !uiState.isPagesDirectUploading,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          )

          uiState.pagesDirectUploadProgress?.let { progress ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
              )
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(progress.phase, style = MaterialTheme.typography.labelSmall, color = palette.activePillFg)
                Text(
                  "${progress.completedFiles}/${progress.totalFiles}",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textSecondary,
                )
              }
            }
          }

          FilledTonalButton(
            onClick = {
              val targetBranch =
                if (directTargetEnv == "production") project.productionBranch
                else previewBranchName.trim().ifBlank { "preview" }
              onDeployDirectPages(targetBranch, commitMessage.ifBlank { "Direct upload via EdgeCode" })
            },
            enabled = !uiState.isPagesDirectUploading,
            modifier = Modifier.fillMaxWidth().testTag("pages_direct_deploy_button"),
          ) {
            Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              text =
                if (uiState.isPagesDirectUploading) "Uploading Assets..."
                else "Deploy ${stagedAssets.size} Assets",
            )
          }
        }

        uiState.pagesLastDirectDeployment?.let { lastDep ->
          val liveUrl = lastDep.url ?: "https://${project.subdomain ?: "${project.name}.pages.dev"}"
          Surface(
            color = palette.editorBg,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Direct Upload Live (${lastDep.environment})",
                  style = MaterialTheme.typography.labelMedium,
                  color = EmeraldLive,
                )
                Text(
                  text = liveUrl,
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(liveUrl)) },
                modifier = Modifier.size(28.dp),
              ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy deployment URL", modifier = Modifier.size(15.dp))
              }
            }
          }
        }
      }
    }

    if (project.source != null) {
      FilledTonalButton(onClick = onTriggerDeploy, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Trigger Git Production Build")
      }
    }

    // Deployments
    Text("DEPLOYMENTS (${uiState.pagesDeployments.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
    uiState.pagesDeployments.take(6).forEach { dep ->
      val stageStatus = dep.latestStage?.status ?: "unknown"
      val statusColor =
        when (stageStatus) {
          "success" -> EmeraldLive
          "failure" -> RoseError
          else -> AmberWarn
        }
      Surface(color = palette.chromePill, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "${dep.environment.uppercase()} • ${dep.shortId ?: dep.id.take(8)}",
              style = MaterialTheme.typography.labelMedium,
              color = palette.textPrimary,
              modifier = Modifier.weight(1f),
            )
            Text(stageStatus, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
          }
          dep.url?.let { depUrl ->
            Text(
              text = depUrl,
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
              color = palette.activePillFg,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          dep.deploymentTrigger?.metadata?.commitMessage?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onFetchLogs(dep.id) }) { Text("Build Logs", fontSize = 11.sp) }
            TextButton(onClick = { onRetryDeploy(dep.id) }) {
              Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(13.dp))
              Spacer(Modifier.width(3.dp))
              Text("Retry", fontSize = 11.sp)
            }
            if (dep.environment == "production") {
              TextButton(onClick = { onRollbackDeploy(dep.id) }) { Text("Rollback", fontSize = 11.sp) }
            }
          }
        }
      }
    }

    if (uiState.pagesBuildLogs.isNotEmpty()) {
      Surface(color = palette.editorBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("BUILD LOGS", style = MaterialTheme.typography.labelSmall, color = palette.activePillFg)
            uiState.pagesBuildLogs.takeLast(35).forEach { entry ->
              Text(entry.line, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono), color = palette.textPrimary)
            }
          }
        }
      }
    }

    HorizontalDivider(color = palette.treeLine)

    // Env Vars (Production / Preview)
    Text("ENVIRONMENT VARIABLES", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf("production", "preview").forEach { env ->
        val active = envMode == env
        Surface(
          onClick = { envMode = env },
          shape = RoundedCornerShape(8.dp),
          color = if (active) palette.activePillBg else palette.chromePill,
          modifier = Modifier.weight(1f),
        ) {
          Text(
            text = env.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = if (active) palette.activePillFg else palette.textSecondary,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp),
          )
        }
      }
    }

    val currentVars =
      (if (envMode == "production") project.deploymentConfigs?.production?.envVars
      else project.deploymentConfigs?.preview?.envVars).orEmpty()
    currentVars.forEach { (k, v) ->
      Surface(color = palette.chromePill, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(k, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary, modifier = Modifier.weight(1f))
          Text(if (v.type == "secret_text") "••••••" else v.value.orEmpty(), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, maxLines = 1)
        }
      }
    }

    OutlinedTextField(value = envKey, onValueChange = { envKey = it }, label = { Text("Var Name") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = envVal, onValueChange = { envVal = it }, label = { Text("Value") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = isSecret, onCheckedChange = { isSecret = it })
        Spacer(Modifier.width(6.dp))
        Text("Secret", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
      }
      FilledTonalButton(
        onClick = {
          onSaveEnvVar(envMode, envKey, envVal, isSecret)
          envKey = ""
          envVal = ""
        },
        enabled = envKey.isNotBlank(),
      ) { Text("Save") }
    }

    HorizontalDivider(color = palette.treeLine)

    // Pages Special Files & Functions Quick-Create
    PagesSpecialFilesCard(onCreatePagesSpecialFile = onCreatePagesSpecialFile)

    HorizontalDivider(color = palette.treeLine)

    // Build Configuration Editor
    var buildBranch by remember(project.name, project.productionBranch) { mutableStateOf(project.productionBranch) }
    var buildCmd by remember(project.name, project.buildConfig?.buildCommand) { mutableStateOf(project.buildConfig?.buildCommand.orEmpty()) }
    var destDir by remember(project.name, project.buildConfig?.destinationDir) { mutableStateOf(project.buildConfig?.destinationDir.orEmpty()) }
    var rootDir by remember(project.name, project.buildConfig?.rootDir) { mutableStateOf(project.buildConfig?.rootDir.orEmpty()) }
    Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BUILD CONFIG", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
        OutlinedTextField(value = buildBranch, onValueChange = { buildBranch = it }, label = { Text("Production branch") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = buildCmd, onValueChange = { buildCmd = it }, label = { Text("Build command (e.g. npm run build)") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedTextField(value = destDir, onValueChange = { destDir = it }, label = { Text("Output dir") }, placeholder = { Text("dist") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f))
          OutlinedTextField(value = rootDir, onValueChange = { rootDir = it }, label = { Text("Root dir") }, placeholder = { Text("/") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f))
        }
        FilledTonalButton(
          onClick = { onUpdateBuildConfig(buildBranch, buildCmd, destDir, rootDir) },
          enabled = buildBranch.isNotBlank() && !uiState.isPagesBusy,
          modifier = Modifier.fillMaxWidth().testTag("pages_save_build_config_button"),
        ) {
          Text("Save Build Config")
        }
      }
    }

    HorizontalDivider(color = palette.treeLine)

    // Pages Bindings (Production / Preview: KV, D1, R2)
    val currentEnvCfg =
      if (envMode == "production") project.deploymentConfigs?.production
      else project.deploymentConfigs?.preview
    val existingKvMap = currentEnvCfg?.kvNamespaces.orEmpty().mapValues { it.value.namespaceId }
    val existingD1Map = currentEnvCfg?.d1Databases.orEmpty().mapValues { it.value.id }
    val existingR2Map = currentEnvCfg?.r2Buckets.orEmpty().mapValues { it.value.name }

    var bindKind by remember { mutableStateOf("KV") } // KV, D1, R2
    var bindVarName by remember { mutableStateOf("MY_KV") }
    var bindResourceId by remember(uiState.kvNamespaces, uiState.d1Databases, uiState.r2Buckets, bindKind) {
      mutableStateOf(
        when (bindKind) {
          "KV" -> uiState.kvNamespaces.firstOrNull()?.id.orEmpty()
          "D1" -> uiState.d1Databases.firstOrNull()?.uuid.orEmpty()
          else -> uiState.r2Buckets.firstOrNull()?.name.orEmpty()
        },
      )
    }
    var resourceMenuOpen by remember { mutableStateOf(false) }

    Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PAGES BINDINGS (${envMode.uppercase()})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
        existingKvMap.forEach { (k, v) ->
          PagesBindingRow("KV", k, v) {
            onUpdateBindings(envMode, existingKvMap - k, existingD1Map, existingR2Map)
          }
        }
        existingD1Map.forEach { (k, v) ->
          PagesBindingRow("D1", k, v) {
            onUpdateBindings(envMode, existingKvMap, existingD1Map - k, existingR2Map)
          }
        }
        existingR2Map.forEach { (k, v) ->
          PagesBindingRow("R2", k, v) {
            onUpdateBindings(envMode, existingKvMap, existingD1Map, existingR2Map - k)
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("KV", "D1", "R2").forEach { kind ->
            val active = bindKind == kind
            Surface(
              onClick = {
                bindKind = kind
                bindVarName =
                  when (kind) {
                    "KV" -> "MY_KV"
                    "D1" -> "DB"
                    else -> "BUCKET"
                  }
              },
              shape = RoundedCornerShape(8.dp),
              color = if (active) palette.activePillBg else palette.editorBg,
              modifier = Modifier.weight(1f),
            ) {
              Text(
                text = kind,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) palette.activePillFg else palette.textSecondary,
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp),
              )
            }
          }
        }

        OutlinedTextField(value = bindVarName, onValueChange = { bindVarName = it }, label = { Text("Binding Var Name") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = bindResourceId,
            onValueChange = { bindResourceId = it },
            label = { Text(if (bindKind == "R2") "Bucket Name" else "$bindKind ID / UUID") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
          )
          val hasChoices =
            (bindKind == "KV" && uiState.kvNamespaces.isNotEmpty()) ||
              (bindKind == "D1" && uiState.d1Databases.isNotEmpty()) ||
              (bindKind == "R2" && uiState.r2Buckets.isNotEmpty())
          if (hasChoices) {
            androidx.compose.foundation.layout.Box {
              OutlinedButton(onClick = { resourceMenuOpen = true }, shape = RoundedCornerShape(10.dp)) {
                Text("Pick", fontSize = 11.sp)
              }
              EdgeMenu(
                expanded = resourceMenuOpen,
                onDismissRequest = { resourceMenuOpen = false },
                minWidth = 216.dp,
                maxWidth = 280.dp,
              ) {
                EdgeMenuHeader(
                  title = "Select $bindKind Resource",
                  badgeText = bindKind,
                )
                when (bindKind) {
                  "KV" -> uiState.kvNamespaces.forEach { ns ->
                    EdgeMenuCompactItem(
                      label = ns.title,
                      subtitle = ns.id,
                      selected = bindResourceId == ns.id,
                      onClick = {
                        bindResourceId = ns.id
                        resourceMenuOpen = false
                      },
                    )
                  }
                  "D1" -> uiState.d1Databases.forEach { db ->
                    EdgeMenuCompactItem(
                      label = db.name,
                      subtitle = db.uuid,
                      selected = bindResourceId == db.uuid,
                      onClick = {
                        bindResourceId = db.uuid
                        resourceMenuOpen = false
                      },
                    )
                  }
                  "R2" -> uiState.r2Buckets.forEach { b ->
                    EdgeMenuCompactItem(
                      label = b.name,
                      selected = bindResourceId == b.name,
                      monospaced = true,
                      onClick = {
                        bindResourceId = b.name
                        resourceMenuOpen = false
                      },
                    )
                  }
                }
              }
            }
          }
        }

        FilledTonalButton(
          onClick = {
            val cleanVar = bindVarName.trim()
            val cleanTarget = bindResourceId.trim()
            if (cleanVar.isNotBlank() && cleanTarget.isNotBlank()) {
              when (bindKind) {
                "KV" -> onUpdateBindings(envMode, existingKvMap + (cleanVar to cleanTarget), existingD1Map, existingR2Map)
                "D1" -> onUpdateBindings(envMode, existingKvMap, existingD1Map + (cleanVar to cleanTarget), existingR2Map)
                "R2" -> onUpdateBindings(envMode, existingKvMap, existingD1Map, existingR2Map + (cleanVar to cleanTarget))
              }
            }
          },
          enabled = bindVarName.isNotBlank() && bindResourceId.isNotBlank() && !uiState.isPagesBusy,
          modifier = Modifier.fillMaxWidth().testTag("pages_save_binding_button"),
        ) {
          Text("Add $bindKind Binding to $envMode")
        }
      }
    }

    HorizontalDivider(color = palette.treeLine)

    // Custom Domains
    Text("CUSTOM DOMAINS (${uiState.pagesDomains.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
    uiState.pagesDomains.forEach { dom ->
      Surface(color = palette.chromePill, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.Language, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(dom.name, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
            Text(dom.status, style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
          }
          IconButton(onClick = { onDeleteDomain(dom.name) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove domain", modifier = Modifier.size(15.dp))
          }
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(value = domainInput, onValueChange = { domainInput = it }, label = { Text("Add domain (app.example.com)") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
      OutlinedButton(
        onClick = {
          onAddDomain(domainInput)
          domainInput = ""
        },
        enabled = domainInput.isNotBlank(),
      ) { Text("Add") }
    }
  }
}

@Composable
private fun PagesSpecialFilesCard(
  onCreatePagesSpecialFile: (String) -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("PAGES FUNCTIONS & ROUTING FILES", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
      Text(
        text = "1-tap create standard Pages config or edge function handlers in workspace:",
        style = MaterialTheme.typography.labelSmall,
        color = palette.textSecondary,
      )
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf("_headers", "_redirects", "_routes.json", "functions/api/hello.js").forEach { kind ->
          OutlinedButton(
            onClick = { onCreatePagesSpecialFile(kind) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag("pages_create_${kind.replace('/', '_')}"),
          ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(kind, fontSize = 11.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun PagesBindingRow(
  kind: String,
  varName: String,
  target: String,
  onDelete: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(color = palette.editorBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Surface(color = palette.activePillBg, shape = RoundedCornerShape(4.dp)) {
        Text(kind, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = palette.activePillFg, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
      }
      Spacer(Modifier.width(8.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(varName, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
        Text(target.take(16), style = MaterialTheme.typography.labelSmall, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove $varName binding", modifier = Modifier.size(14.dp))
      }
    }
  }
}

private fun formatReadableBytes(bytes: Long): String {
  return when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
  }
}
