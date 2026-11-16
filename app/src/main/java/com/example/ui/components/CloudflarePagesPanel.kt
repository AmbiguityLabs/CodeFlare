package com.example.ui.components

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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
  onAddDomain: (String, String) -> Unit,
  onDeleteDomain: (String, String) -> Unit,
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
    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("CLOUDFLARE PAGES", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
      Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Connect your Cloudflare API Token or Global Key to manage Pages sites, deployments, build logs, rollbacks, preview/production env vars, and custom domains.",
          style = MaterialTheme.typography.bodySmall,
          color = palette.textSecondary,
          modifier = Modifier.padding(14.dp),
        )
      }
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

      if (uiState.isPagesBusy) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
      } else if (uiState.pagesProjects.isEmpty()) {
        Text("No Pages projects found. Tap + to create one.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
      } else {
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
      onAddDomain = { d -> onAddDomain(selected.name, d) },
      onDeleteDomain = { d -> onDeleteDomain(selected.name, d) },
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
  onAddDomain: (String) -> Unit,
  onDeleteDomain: (String) -> Unit,
) {
  val palette = LocalIdePalette.current
  var envMode by remember { mutableStateOf("production") }
  var envKey by remember { mutableStateOf("") }
  var envVal by remember { mutableStateOf("") }
  var isSecret by remember { mutableStateOf(false) }
  var domainInput by remember { mutableStateOf("") }

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

    FilledTonalButton(onClick = onTriggerDeploy, modifier = Modifier.fillMaxWidth()) {
      Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(6.dp))
      Text("Trigger Production Deployment")
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
