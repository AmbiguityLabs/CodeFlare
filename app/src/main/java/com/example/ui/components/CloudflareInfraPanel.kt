package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.data.remote.CfD1Database
import com.example.data.remote.CfKvNamespace
import com.example.data.remote.CfWorkerBinding
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.rememberGlassBorderStroke
import com.example.ui.viewmodel.IdeUiState

private enum class InfraSubTab(val label: String) {
  BINDINGS("Bindings"),
  KV("KV"),
  D1("D1 SQL"),
  R2_DO("R2 / DO / Queues"),
  ROUTES("Domains & Cron"),
  DEPLOYS("Versions & Split"),
}

@Composable
fun CloudflareInfraPanel(
  uiState: IdeUiState,
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
  onRenameWorker: (String, Boolean) -> Unit = { _, _ -> },
  onUpdateSubdomain: (String) -> Unit = {},
) {
  val palette = LocalIdePalette.current
  var subTab by remember { mutableStateOf(InfraSubTab.BINDINGS) }
  var showRenameWorkerDialog by remember { mutableStateOf(false) }
  var showChangeSubdomainDialog by remember { mutableStateOf(false) }

  val activeWs = uiState.workspace
  val effectiveSubdomain =
    activeWs?.accountSubdomain?.takeIf { it.isNotBlank() }
      ?: uiState.accountSubdomain

  if (showRenameWorkerDialog && activeWs != null) {
    RenameWorkerDialog(
      currentName = activeWs.scriptName,
      subdomain = effectiveSubdomain,
      isRemoteWorker = !uiState.isLocalScratchpad && uiState.credentials != null,
      hasEncryptedSecrets = activeWs.bindings.any { it.type == "secret_text" },
      onDismiss = { showRenameWorkerDialog = false },
      onConfirm = { newName, deleteOld ->
        showRenameWorkerDialog = false
        onRenameWorker(newName, deleteOld)
      },
    )
  }

  if (showChangeSubdomainDialog) {
    ChangeSubdomainDialog(
      currentSubdomain = effectiveSubdomain,
      sampleWorkerName = activeWs?.scriptName ?: uiState.workers.firstOrNull()?.id.orEmpty(),
      isLocalMode = uiState.isLocalScratchpad || uiState.credentials == null,
      onDismiss = { showChangeSubdomainDialog = false },
      onConfirm = { newSubdomain ->
        showChangeSubdomainDialog = false
        onUpdateSubdomain(newSubdomain)
      },
    )
  }

  if (uiState.isLocalScratchpad) {
    var showInfraInfo by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (activeWs != null) {
        WorkerIdentityAndSubdomainCard(
          scriptName = activeWs.scriptName,
          subdomain = effectiveSubdomain,
          canRenameWorker = true,
          onRequestRenameWorker = { showRenameWorkerDialog = true },
          onRequestChangeSubdomain = { showChangeSubdomainDialog = true },
        )
      }
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("INFRA", style = MaterialTheme.typography.labelSmall, color = palette.textMuted, modifier = Modifier.weight(1f))
        IconButton(onClick = { showInfraInfo = !showInfraInfo }, modifier = Modifier.size(28.dp)) {
          Icon(Icons.Outlined.Info, contentDescription = "Infra info", tint = palette.textMuted, modifier = Modifier.size(16.dp))
        }
      }
      AnimatedVisibility(visible = showInfraInfo) {
        Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Connect Cloudflare to manage Bindings, KV, D1, R2, Routes & Cron.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(12.dp),
          )
        }
      }
    }
    return
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "WORKERS INFRASTRUCTURE",
        style = MaterialTheme.typography.labelSmall,
        color = palette.textMuted,
        modifier = Modifier.weight(1f),
      )
      if (uiState.isInfraBusy) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
      } else {
        IconButton(onClick = onRefreshInfra, modifier = Modifier.size(28.dp)) {
          Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Cloudflare Infra", modifier = Modifier.size(16.dp))
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (activeWs != null) {
        Surface(
          onClick = { showRenameWorkerDialog = true },
          shape = RoundedCornerShape(12.dp),
          color = palette.glassElevated,
          border = rememberGlassBorderStroke(1.dp),
          modifier = Modifier.weight(1f).testTag("infra_quick_rename_worker"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(15.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("Worker Name", fontSize = 9.5.sp, color = palette.textMuted)
              Text(
                text = activeWs.scriptName,
                fontFamily = JetBrainsMono,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Icon(Icons.Outlined.Edit, contentDescription = "Rename Worker", tint = palette.textSecondary, modifier = Modifier.size(14.dp))
          }
        }
      }

      Surface(
        onClick = { showChangeSubdomainDialog = true },
        shape = RoundedCornerShape(12.dp),
        color = palette.glassElevated,
        border = rememberGlassBorderStroke(1.dp),
        modifier = Modifier.weight(1f).testTag("infra_quick_change_subdomain"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Outlined.Language, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(15.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("workers.dev Subdomain", fontSize = 9.5.sp, color = palette.textMuted)
            Text(
              text = if (effectiveSubdomain.isNotBlank()) "$effectiveSubdomain.workers.dev" else "Set subdomain",
              fontFamily = JetBrainsMono,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (effectiveSubdomain.isNotBlank()) palette.textPrimary else palette.activePillFg,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Icon(Icons.Outlined.Edit, contentDescription = "Change subdomain", tint = palette.textSecondary, modifier = Modifier.size(14.dp))
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      InfraSubTab.entries.forEach { tab ->
        val active = subTab == tab
        Surface(
          onClick = { subTab = tab },
          shape = RoundedCornerShape(10.dp),
          color = if (active) palette.activePillBg else palette.chromePill,
          modifier = Modifier.testTag("infra_tab_${tab.name.lowercase()}"),
        ) {
          Text(
            text = tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) palette.activePillFg else palette.textSecondary,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
    }

    Spacer(Modifier.height(8.dp))

    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      when (subTab) {
        InfraSubTab.BINDINGS ->
          BindingsSection(
            uiState = uiState,
            onAddEnvVar = onAddEnvVar,
            onAddSecret = onAddSecret,
            onBindResource = onBindResource,
            onDeleteBinding = onDeleteBinding,
          )
        InfraSubTab.KV ->
          KvSection(
            uiState = uiState,
            onCreateKvNamespace = onCreateKvNamespace,
            onDeleteKvNamespace = onDeleteKvNamespace,
            onSelectKvNamespace = onSelectKvNamespace,
            onInspectKvKey = onInspectKvKey,
            onPutKvEntry = onPutKvEntry,
            onBulkImportKv = onBulkImportKv,
            onExportKvJson = onExportKvJson,
            onDeleteKvKey = onDeleteKvKey,
            onBindKv = { varName, ns ->
              onBindResource(CfWorkerBinding(name = varName, type = "kv_namespace", namespaceId = ns.id))
            },
            onNotifyStatus = onNotifyStatus,
          )
        InfraSubTab.D1 ->
          D1Section(
            uiState = uiState,
            onCreateD1 = onCreateD1Database,
            onDeleteD1 = onDeleteD1Database,
            onSelectD1 = onSelectD1Database,
            onRunSql = onRunD1Sql,
            onBindD1 = { varName, db ->
              onBindResource(CfWorkerBinding(name = varName, type = "d1", databaseId = db.uuid))
            },
          )
        InfraSubTab.R2_DO ->
          R2DoQueueSection(
            uiState = uiState,
            onCreateR2 = onCreateR2Bucket,
            onDeleteR2 = onDeleteR2Bucket,
            onBindR2 = { varName, bucketName ->
              onBindResource(CfWorkerBinding(name = varName, type = "r2_bucket", bucketName = bucketName))
            },
            onCreateQueue = onCreateQueue,
            onDeleteQueue = onDeleteQueue,
            onBindQueue = { varName, queueName ->
              onBindResource(CfWorkerBinding(name = varName, type = "queue", queueName = queueName))
            },
          )
        InfraSubTab.ROUTES ->
          RoutesDomainsCronSection(
            uiState = uiState,
            effectiveSubdomain = effectiveSubdomain,
            onRequestRenameWorker = { showRenameWorkerDialog = true },
            onRequestChangeSubdomain = { showChangeSubdomainDialog = true },
            onToggleWorkersDev = onToggleWorkersDev,
            onUpdateCompat = onUpdateCompat,
            onAddCronSchedule = onAddCronSchedule,
            onRemoveCronSchedule = onRemoveCronSchedule,
            onAttachZoneRoute = onAttachZoneRoute,
            onDeleteZoneRoute = onDeleteZoneRoute,
            onAttachWorkerDomain = onAttachWorkerDomain,
            onRemoveWorkerDomain = onRemoveWorkerDomain,
          )
        InfraSubTab.DEPLOYS ->
          DeploymentsRollbackSection(
            uiState = uiState,
            onRollbackVersion = onRollbackVersion,
            onRefreshWorkerVersions = onRefreshWorkerVersions,
            onDeployGradualSplit = onDeployGradualSplit,
          )
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun BindingsSection(
  uiState: IdeUiState,
  onAddEnvVar: (String, String) -> Unit,
  onAddSecret: (String, String) -> Unit,
  onBindResource: (CfWorkerBinding) -> Unit,
  onDeleteBinding: (CfWorkerBinding) -> Unit,
) {
  val palette = LocalIdePalette.current
  val ws = uiState.workspace
  if (ws == null) {
    Text("Open a Worker to inspect & configure its env bindings.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
    return
  }

  var varName by remember { mutableStateOf("") }
  var varValue by remember { mutableStateOf("") }
  var isSecret by remember { mutableStateOf(false) }
  var svcBindName by remember { mutableStateOf("") }
  var svcTarget by remember { mutableStateOf("") }
  var aiBindName by remember { mutableStateOf("AI") }

  Text("ACTIVE WORKER BINDINGS (${ws.bindings.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
  ws.bindings.forEach { b ->
    Surface(color = palette.chromePill, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector =
            when (b.type) {
              "secret_text" -> Icons.Outlined.Lock
              "ai" -> Icons.Outlined.AutoAwesome
              else -> Icons.Outlined.Key
            },
          contentDescription = null,
          tint = palette.activePillFg,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(b.name, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, fontWeight = FontWeight.Medium)
          Text(
            text =
              when (b.type) {
                "secret_text" -> "secret_text (encrypted)"
                "plain_text" -> b.text.orEmpty()
                "kv_namespace" -> "KV • ${b.namespaceId.orEmpty().take(10)}…"
                "d1" -> "D1 • ${b.databaseId.orEmpty().take(10)}…"
                "r2_bucket" -> "R2 • ${b.bucketName.orEmpty()}"
                "service" -> "Service -> ${b.service.orEmpty()}"
                "ai" -> "Workers AI Inference"
                "queue" -> "Queue -> ${b.queueName.orEmpty()}"
                "durable_object_namespace" -> "DO -> ${b.className.orEmpty()}"
                else -> b.type
              },
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(onClick = { onDeleteBinding(b) }, modifier = Modifier.size(34.dp)) {
          Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove binding", modifier = Modifier.size(16.dp))
        }
      }
    }
  }

  HorizontalDivider(color = palette.treeLine)
  Text("ADD ENV VAR / SECRET", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
  OutlinedTextField(
    value = varName,
    onValueChange = { varName = it },
    label = { Text("Variable Name (e.g. API_BASE)") },
    singleLine = true,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    value = varValue,
    onValueChange = { varValue = it },
    label = { Text(if (isSecret) "Encrypted Secret Value" else "Plain Text Value") },
    singleLine = true,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  )
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(checked = isSecret, onCheckedChange = { isSecret = it })
      Spacer(Modifier.width(6.dp))
      Text("Secret", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
    }
    FilledTonalButton(
      onClick = {
        if (isSecret) onAddSecret(varName, varValue) else onAddEnvVar(varName, varValue)
        varName = ""
        varValue = ""
      },
      enabled = varName.isNotBlank() && varValue.isNotBlank(),
    ) {
      Text("Save Binding")
    }
  }

  HorizontalDivider(color = palette.treeLine)
  Text("BIND WORKER SERVICE", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
  OutlinedTextField(
    value = svcBindName,
    onValueChange = { svcBindName = it },
    label = { Text("Binding Name (e.g. AUTH_SERVICE)") },
    singleLine = true,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  )
  OutlinedTextField(
    value = svcTarget,
    onValueChange = { svcTarget = it },
    label = { Text("Target Worker Script Name") },
    singleLine = true,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  )
  FilledTonalButton(
    onClick = {
      onBindResource(CfWorkerBinding(name = svcBindName.trim(), type = "service", service = svcTarget.trim(), environment = "production"))
      svcBindName = ""
      svcTarget = ""
    },
    enabled = svcBindName.isNotBlank() && svcTarget.isNotBlank(),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text("Bind Service")
  }

  HorizontalDivider(color = palette.treeLine)
  Text("BIND WORKERS AI", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
      value = aiBindName,
      onValueChange = { aiBindName = it },
      label = { Text("AI Binding Name") },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.weight(1f),
    )
    FilledTonalButton(
      onClick = { onBindResource(CfWorkerBinding(name = aiBindName.trim(), type = "ai")) },
      enabled = aiBindName.isNotBlank(),
      modifier = Modifier.testTag("bind_workers_ai_button"),
    ) {
      Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
      Spacer(Modifier.width(4.dp))
      Text("Bind AI")
    }
  }
}

@Composable
private fun KvSection(
  uiState: IdeUiState,
  onCreateKvNamespace: (String) -> Unit,
  onDeleteKvNamespace: (String) -> Unit,
  onSelectKvNamespace: (CfKvNamespace) -> Unit,
  onInspectKvKey: (String) -> Unit,
  onPutKvEntry: (String, String, Long?) -> Unit,
  onBulkImportKv: (String) -> Unit,
  onExportKvJson: () -> String,
  onDeleteKvKey: (String) -> Unit,
  onBindKv: (String, CfKvNamespace) -> Unit,
  onNotifyStatus: (String) -> Unit,
) {
  val palette = LocalIdePalette.current
  val clipboard = LocalClipboardManager.current
  var newNsTitle by remember { mutableStateOf("") }
  var bindName by remember { mutableStateOf("MY_KV") }
  var kvKey by remember { mutableStateOf("") }
  var kvVal by remember { mutableStateOf("") }
  var kvTtlInput by remember { mutableStateOf("") }
  var showBulkImportDialog by remember { mutableStateOf(false) }

  if (showBulkImportDialog) {
    var bulkJson by remember {
      mutableStateOf(
        """[
  {"key": "config:mode", "value": "production", "expiration_ttl": 3600},
  {"key": "feature:v2", "value": "enabled"}
]""",
      )
    }
    AlertDialog(
      onDismissRequest = { showBulkImportDialog = false },
      title = { Text("Bulk Import KV Entries (JSON)") },
      text = {
        OutlinedTextField(
          value = bulkJson,
          onValueChange = { bulkJson = it },
          label = { Text("JSON Array or Key-Value Object") },
          maxLines = 8,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("kv_bulk_json_input"),
        )
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onBulkImportKv(bulkJson)
            showBulkImportDialog = false
          },
          enabled = bulkJson.isNotBlank(),
        ) { Text("Import") }
      },
      dismissButton = { TextButton(onClick = { showBulkImportDialog = false }) { Text("Cancel") } },
    )
  }

  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
      value = newNsTitle,
      onValueChange = { newNsTitle = it },
      label = { Text("New KV Namespace") },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.weight(1f),
    )
    FilledTonalButton(
      onClick = {
        onCreateKvNamespace(newNsTitle)
        newNsTitle = ""
      },
      enabled = newNsTitle.isNotBlank(),
    ) {
      Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
    }
  }

  uiState.kvNamespaces.forEach { ns ->
    val selected = uiState.selectedKvNamespace?.id == ns.id
    Surface(
      onClick = { onSelectKvNamespace(ns) },
      shape = RoundedCornerShape(12.dp),
      color = if (selected) palette.selectedRowBg else palette.chromePill,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Storage, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(ns.title, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(ns.id.take(12) + "…", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
        }
        IconButton(onClick = { onDeleteKvNamespace(ns.id) }, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete namespace", modifier = Modifier.size(15.dp))
        }
      }
    }
  }

  uiState.selectedKvNamespace?.let { ns ->
    HorizontalDivider(color = palette.treeLine)
    if (uiState.workspace != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = bindName,
          onValueChange = { bindName = it },
          label = { Text("Bind as env var") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
          onClick = { onBindKv(bindName.trim(), ns) },
          enabled = bindName.isNotBlank(),
        ) {
          Text("Bind")
        }
      }
    }

    Text("KEYS (${uiState.kvKeys.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
    uiState.kvKeys.take(25).forEach { key ->
      Surface(
        onClick = { onInspectKvKey(key.name) },
        shape = RoundedCornerShape(8.dp),
        color = palette.chromeSurface,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(key.name, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          IconButton(onClick = { onDeleteKvKey(key.name) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete key", modifier = Modifier.size(15.dp))
          }
        }
      }
    }

    uiState.selectedKvKeyValue?.let { (k, v) ->
      Surface(color = palette.activePillBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(k, style = MaterialTheme.typography.labelMedium, color = palette.activePillFg, fontWeight = FontWeight.Bold)
            Text(v, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
          }
        }
      }
    }

    OutlinedTextField(value = kvKey, onValueChange = { kvKey = it }, label = { Text("Put KV Key") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = kvVal, onValueChange = { kvVal = it }, label = { Text("Value") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = kvTtlInput, onValueChange = { kvTtlInput = it.filter { ch -> ch.isDigit() } }, label = { Text("Optional TTL (seconds >= 60)") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
    FilledTonalButton(
      onClick = {
        val ttl = kvTtlInput.toLongOrNull()?.takeIf { it >= 60L }
        onPutKvEntry(kvKey, kvVal, ttl)
        kvKey = ""
        kvVal = ""
        kvTtlInput = ""
      },
      enabled = kvKey.isNotBlank(),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Write KV Key")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      OutlinedButton(
        onClick = { showBulkImportDialog = true },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.weight(1f).testTag("kv_bulk_import_button"),
      ) {
        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("Bulk Import", fontSize = 11.sp)
      }
      OutlinedButton(
        onClick = {
          clipboard.setText(AnnotatedString(onExportKvJson()))
          onNotifyStatus("Copied KV keys JSON to clipboard")
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.weight(1f).testTag("kv_export_json_button"),
      ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("Export JSON", fontSize = 11.sp)
      }
    }
  }
}

@Composable
private fun D1Section(
  uiState: IdeUiState,
  onCreateD1: (String) -> Unit,
  onDeleteD1: (String) -> Unit,
  onSelectD1: (CfD1Database) -> Unit,
  onRunSql: (String, String) -> Unit,
  onBindD1: (String, CfD1Database) -> Unit,
) {
  val palette = LocalIdePalette.current
  var newDbName by remember { mutableStateOf("") }
  var d1BindVar by remember { mutableStateOf("DB") }
  var sqlInput by remember { mutableStateOf("SELECT name FROM sqlite_master WHERE type='table';") }

  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
      value = newDbName,
      onValueChange = { newDbName = it },
      label = { Text("New D1 Database") },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.weight(1f),
    )
    FilledTonalButton(
      onClick = {
        onCreateD1(newDbName)
        newDbName = ""
      },
      enabled = newDbName.isNotBlank(),
    ) {
      Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
    }
  }

  uiState.d1Databases.forEach { db ->
    val selected = uiState.selectedD1Database?.uuid == db.uuid
    Surface(
      onClick = { onSelectD1(db) },
      shape = RoundedCornerShape(12.dp),
      color = if (selected) palette.selectedRowBg else palette.chromePill,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Storage, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(db.name, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
          Text("${db.numTables ?: 0} tables • ${db.uuid.take(8)}…", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
        }
        IconButton(onClick = { onDeleteD1(db.uuid) }, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete D1", modifier = Modifier.size(15.dp))
        }
      }
    }
  }

  uiState.selectedD1Database?.let { db ->
    HorizontalDivider(color = palette.treeLine)
    if (uiState.workspace != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = d1BindVar, onValueChange = { d1BindVar = it }, label = { Text("Bind var") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = { onBindD1(d1BindVar.trim(), db) }, enabled = d1BindVar.isNotBlank()) { Text("Bind") }
      }
    }
    OutlinedTextField(
      value = sqlInput,
      onValueChange = { sqlInput = it },
      label = { Text("SQL Console (${db.name})") },
      maxLines = 4,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth(),
    )
    FilledTonalButton(onClick = { onRunSql(db.uuid, sqlInput) }, enabled = sqlInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
      Text("Execute SQL Query")
    }
    if (uiState.d1QueryResults.isNotEmpty()) {
      SelectionContainer {
        Surface(color = palette.editorBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            uiState.d1QueryResults.forEach { res ->
              Text(
                text = "Rows: ${res.results.size} • ${res.meta?.duration?.let { "%.2fms".format(it) } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.activePillFg,
              )
              res.results.take(25).forEach { row ->
                Text(
                  text = row.toString(),
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                  color = palette.textPrimary,
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
private fun R2DoQueueSection(
  uiState: IdeUiState,
  onCreateR2: (String) -> Unit,
  onDeleteR2: (String) -> Unit,
  onBindR2: (String, String) -> Unit,
  onCreateQueue: (String) -> Unit,
  onDeleteQueue: (String) -> Unit,
  onBindQueue: (String, String) -> Unit,
) {
  val palette = LocalIdePalette.current
  var newBucket by remember { mutableStateOf("") }
  var r2BindVar by remember { mutableStateOf("BUCKET") }
  var newQueueName by remember { mutableStateOf("") }
  var queueBindVar by remember { mutableStateOf("MY_QUEUE") }

  Text("R2 OBJECT STORAGE BUCKETS", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(value = newBucket, onValueChange = { newBucket = it }, label = { Text("New R2 Bucket") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
    FilledTonalButton(
      onClick = {
        onCreateR2(newBucket)
        newBucket = ""
      },
      enabled = newBucket.isNotBlank(),
    ) {
      Icon(Icons.Outlined.Add, contentDescription = "Create R2 bucket", modifier = Modifier.size(16.dp))
    }
  }

  uiState.r2Buckets.forEach { b ->
    Surface(color = palette.chromePill, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
      Row(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(b.name, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
          Text(b.location ?: "auto region", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
        }
        if (uiState.workspace != null) {
          TextButton(onClick = { onBindR2(r2BindVar.ifBlank { "BUCKET" }, b.name) }) {
            Text("Bind")
          }
        }
        IconButton(onClick = { onDeleteR2(b.name) }, modifier = Modifier.size(30.dp)) {
          Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete bucket", modifier = Modifier.size(15.dp))
        }
      }
    }
  }

  if (uiState.r2Buckets.isNotEmpty() && uiState.workspace != null) {
    OutlinedTextField(value = r2BindVar, onValueChange = { r2BindVar = it }, label = { Text("Binding Name for R2") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
  }

  HorizontalDivider(color = palette.treeLine)
  Text("DURABLE OBJECTS (${uiState.durableObjects.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
  if (uiState.durableObjects.isEmpty()) {
    Text("No Durable Object namespaces found.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
  } else {
    uiState.durableObjects.forEach { d ->
      Surface(color = palette.chromePill, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
          Text(d.name.ifBlank { d.className.orEmpty() }, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
          Text("${d.script ?: "-"} • class ${d.className ?: "-"}", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
        }
      }
    }
  }

  HorizontalDivider(color = palette.treeLine)
  Text("CLOUDFLARE QUEUES (${uiState.queues.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
      value = newQueueName,
      onValueChange = { newQueueName = it },
      label = { Text("New Queue Name") },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.weight(1f).testTag("create_queue_input"),
    )
    FilledTonalButton(
      onClick = {
        onCreateQueue(newQueueName)
        newQueueName = ""
      },
      enabled = newQueueName.isNotBlank(),
      modifier = Modifier.testTag("create_queue_button"),
    ) {
      Icon(Icons.Outlined.Add, contentDescription = "Create Queue", modifier = Modifier.size(16.dp))
    }
  }
  if (uiState.queues.isEmpty()) {
    Text("No Queues provisioned.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
  } else {
    if (uiState.workspace != null) {
      OutlinedTextField(value = queueBindVar, onValueChange = { queueBindVar = it }, label = { Text("Binding Name for Queue") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
    }
    uiState.queues.forEach { q ->
      Surface(color = palette.chromePill, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text(q.queueName, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
            Text(q.queueId.take(12) + "…", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
          }
          if (uiState.workspace != null) {
            TextButton(onClick = { onBindQueue(queueBindVar.ifBlank { "MY_QUEUE" }, q.queueName) }) {
              Text("Bind")
            }
          }
          IconButton(onClick = { onDeleteQueue(q.queueId) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete queue", modifier = Modifier.size(15.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun WorkerIdentityAndSubdomainCard(
  scriptName: String?,
  subdomain: String,
  canRenameWorker: Boolean,
  onRequestRenameWorker: () -> Unit,
  onRequestChangeSubdomain: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    color = palette.glassElevated,
    shape = RoundedCornerShape(14.dp),
    border = rememberGlassBorderStroke(1.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("WORKER IDENTITY & SUBDOMAIN", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)

      if (scriptName != null && canRenameWorker) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Worker Script Name", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
            Text(
              text = scriptName,
              fontFamily = JetBrainsMono,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          FilledTonalButton(
            onClick = onRequestRenameWorker,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("infra_rename_worker_button"),
          ) {
            Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("Rename", fontSize = 12.sp)
          }
        }
        HorizontalDivider(color = palette.treeLine)
      }

      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Account *.workers.dev Subdomain", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
          Text(
            text = if (subdomain.isNotBlank()) "*.$subdomain.workers.dev" else "Not configured yet",
            fontFamily = JetBrainsMono,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (subdomain.isNotBlank()) EmeraldLive else palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        FilledTonalButton(
          onClick = onRequestChangeSubdomain,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.testTag("infra_change_subdomain_button"),
        ) {
          Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(5.dp))
          Text(if (subdomain.isNotBlank()) "Change" else "Set", fontSize = 12.sp)
        }
      }
    }
  }
}

@Composable
private fun RoutesDomainsCronSection(
  uiState: IdeUiState,
  effectiveSubdomain: String,
  onRequestRenameWorker: () -> Unit,
  onRequestChangeSubdomain: () -> Unit,
  onToggleWorkersDev: (Boolean) -> Unit,
  onUpdateCompat: (String, Boolean) -> Unit,
  onAddCronSchedule: (String) -> Unit,
  onRemoveCronSchedule: (String) -> Unit,
  onAttachZoneRoute: (String, String) -> Unit,
  onDeleteZoneRoute: (String, String) -> Unit,
  onAttachWorkerDomain: (String, String) -> Unit,
  onRemoveWorkerDomain: (String) -> Unit,
) {
  val palette = LocalIdePalette.current
  val ws = uiState.workspace

  WorkerIdentityAndSubdomainCard(
    scriptName = ws?.scriptName,
    subdomain = effectiveSubdomain,
    canRenameWorker = ws != null,
    onRequestRenameWorker = onRequestRenameWorker,
    onRequestChangeSubdomain = onRequestChangeSubdomain,
  )

  if (ws == null) {
    Text("Open a Worker to configure its custom domains, routes & cron triggers.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
    return
  }

  var compatDate by remember(ws.scriptName, ws.compatibilityDate) { mutableStateOf(ws.compatibilityDate) }
  var nodejsCompat by remember(ws.scriptName, ws.compatibilityFlags) { mutableStateOf(ws.compatibilityFlags.contains("nodejs_compat")) }
  var cronInput by remember { mutableStateOf("*/15 * * * *") }
  var zoneDropdownExpanded by remember { mutableStateOf(false) }
  var selectedZoneId by remember(uiState.zones) { mutableStateOf(uiState.zones.firstOrNull()?.id ?: "") }
  var routePatternInput by remember { mutableStateOf("") }
  var customHostname by remember { mutableStateOf("") }

  Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text("workers.dev Route", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
        Text(
          text =
            ws.workersDevUrl
              ?: effectiveSubdomain.takeIf { it.isNotBlank() }?.let { "https://${ws.scriptName}.$it.workers.dev" }
              ?: "No subdomain set",
          style = MaterialTheme.typography.labelSmall,
          color = palette.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Switch(checked = ws.workersDevEnabled, onCheckedChange = onToggleWorkersDev)
    }
  }

  Surface(color = palette.chromePill, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(value = compatDate, onValueChange = { compatDate = it }, label = { Text("compatibility_date") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("nodejs_compat flag", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
        Switch(checked = nodejsCompat, onCheckedChange = { nodejsCompat = it })
      }
      FilledTonalButton(onClick = { onUpdateCompat(compatDate, nodejsCompat) }, modifier = Modifier.fillMaxWidth()) {
        Text("Save Runtime Config")
      }
    }
  }

  Text("CUSTOM WORKER DOMAINS (${ws.domains.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
  ws.domains.forEach { d ->
    Surface(color = palette.chromePill, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
      Row(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Language, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(d.hostname, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = { onRemoveWorkerDomain(d.id) }, modifier = Modifier.size(30.dp)) {
          Icon(Icons.Outlined.DeleteOutline, contentDescription = "Detach domain", modifier = Modifier.size(15.dp))
        }
      }
    }
  }

  if (uiState.zones.isNotEmpty()) {
    val activeZone = uiState.zones.firstOrNull { it.id == selectedZoneId } ?: uiState.zones.first()
    Box {
      OutlinedButton(onClick = { zoneDropdownExpanded = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Zone: ${activeZone.name}")
      }
      EdgeMenu(
        expanded = zoneDropdownExpanded,
        onDismissRequest = { zoneDropdownExpanded = false },
        minWidth = 210.dp,
        maxWidth = 272.dp,
      ) {
        EdgeMenuHeader(
          title = "Cloudflare Zone",
          badgeText = "${uiState.zones.size}",
          leadingIcon = Icons.Outlined.Language,
        )
        uiState.zones.forEach { zone ->
          EdgeMenuCompactItem(
            label = zone.name,
            icon = Icons.Outlined.Language,
            selected = zone.id == activeZone.id,
            monospaced = true,
            onClick = {
              selectedZoneId = zone.id
              if (customHostname.isBlank()) customHostname = "api.${zone.name}"
              if (routePatternInput.isBlank()) routePatternInput = "${zone.name}/*"
              zoneDropdownExpanded = false
            },
          )
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(value = customHostname, onValueChange = { customHostname = it }, label = { Text("Domain (e.g. api.${activeZone.name})") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
      FilledTonalButton(
        onClick = {
          onAttachWorkerDomain(activeZone.id, customHostname)
          customHostname = ""
        },
        enabled = customHostname.isNotBlank(),
      ) { Text("Bind") }
    }

    Text("ZONE ROUTES (${ws.routes.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
    ws.routes.forEach { route ->
      Surface(color = palette.chromePill, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(route.pattern, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary, modifier = Modifier.weight(1f))
          IconButton(onClick = { onDeleteZoneRoute(activeZone.id, route.id) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove route", modifier = Modifier.size(15.dp))
          }
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(value = routePatternInput, onValueChange = { routePatternInput = it }, label = { Text("Route (${activeZone.name}/*)") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
      FilledTonalButton(
        onClick = {
          onAttachZoneRoute(activeZone.id, routePatternInput)
          routePatternInput = ""
        },
        enabled = routePatternInput.isNotBlank(),
      ) { Text("Add") }
    }
  }

  HorizontalDivider(color = palette.treeLine)
  Text("CRON TRIGGERS (${ws.schedules.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
  ws.schedules.forEach { s ->
    Surface(color = palette.chromePill, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
      Row(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(s.cron, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = { onRemoveCronSchedule(s.cron) }, modifier = Modifier.size(30.dp)) {
          Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete cron", modifier = Modifier.size(15.dp))
        }
      }
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(value = cronInput, onValueChange = { cronInput = it }, label = { Text("Cron expr") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
    FilledTonalButton(onClick = { onAddCronSchedule(cronInput) }, enabled = cronInput.isNotBlank()) { Text("Add") }
  }
}

@Composable
private fun DeploymentsRollbackSection(
  uiState: IdeUiState,
  onRollbackVersion: (String) -> Unit,
  onRefreshWorkerVersions: () -> Unit,
  onDeployGradualSplit: (String, Int, String, Int, String) -> Unit,
) {
  val palette = LocalIdePalette.current
  val ws = uiState.workspace
  var pendingVersion by remember { mutableStateOf<String?>(null) }
  var showGradualSplitDialog by remember { mutableStateOf(false) }

  pendingVersion?.let { versionId ->
    AlertDialog(
      onDismissRequest = { pendingVersion = null },
      title = { Text("Rollback Deployment?") },
      text = { Text("Promote Worker version ${versionId.take(8)}… to 100% edge traffic?") },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onRollbackVersion(versionId)
            pendingVersion = null
          },
        ) { Text("Rollback") }
      },
      dismissButton = { TextButton(onClick = { pendingVersion = null }) { Text("Cancel") } },
    )
  }

  val availableVersionIds =
    remember(uiState.workerVersions, ws?.deployments) {
      val vIds = uiState.workerVersions.mapNotNull { it.id.takeIf(String::isNotBlank) }
      if (vIds.isNotEmpty()) vIds
      else ws?.deployments.orEmpty().flatMap { it.versions }.map { it.versionId }.distinct()
    }

  if (showGradualSplitDialog && availableVersionIds.size >= 2) {
    var primaryVer by remember { mutableStateOf(availableVersionIds[0]) }
    var secondaryVer by remember { mutableStateOf(availableVersionIds[1]) }
    var primaryPct by remember { mutableFloatStateOf(80f) }
    var splitNote by remember { mutableStateOf("Canary rollout via EdgeCode") }

    AlertDialog(
      onDismissRequest = { showGradualSplitDialog = false },
      title = { Text("Gradual Traffic Split") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Primary: ${primaryVer.take(8)} (${primaryPct.toInt()}%)", style = MaterialTheme.typography.labelMedium)
          Text("Secondary: ${secondaryVer.take(8)} (${100 - primaryPct.toInt()}%)", style = MaterialTheme.typography.labelMedium)
          Slider(
            value = primaryPct,
            onValueChange = { primaryPct = it },
            valueRange = 5f..95f,
            steps = 17,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            availableVersionIds.take(4).forEachIndexed { idx, vId ->
              Surface(
                onClick = { if (idx % 2 == 0) primaryVer = vId else secondaryVer = vId },
                shape = RoundedCornerShape(8.dp),
                color = if (vId == primaryVer || vId == secondaryVer) palette.activePillBg else palette.chromePill,
              ) {
                Text(vId.take(6), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
              }
            }
          }
          OutlinedTextField(
            value = splitNote,
            onValueChange = { splitNote = it },
            label = { Text("Deployment Annotation") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            val p1 = primaryPct.toInt()
            onDeployGradualSplit(primaryVer, p1, secondaryVer, 100 - p1, splitNote)
            showGradualSplitDialog = false
          },
        ) { Text("Apply Split") }
      },
      dismissButton = { TextButton(onClick = { showGradualSplitDialog = false }) { Text("Cancel") } },
    )
  }

  if (ws == null) {
    Text("Open a Worker first to inspect versions and gradual traffic splits.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
    return
  }

  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("VERSIONS (${uiState.workerVersions.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted, modifier = Modifier.weight(1f))
    if (availableVersionIds.size >= 2) {
      OutlinedButton(
        onClick = { showGradualSplitDialog = true },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("gradual_split_button"),
      ) {
        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("Traffic Split", fontSize = 11.sp)
      }
      Spacer(Modifier.width(6.dp))
    }
    IconButton(onClick = onRefreshWorkerVersions, modifier = Modifier.size(30.dp)) {
      Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Worker Versions", modifier = Modifier.size(16.dp))
    }
  }

  if (uiState.workerVersions.isEmpty()) {
    Text("Tap Refresh to load Worker versions for gradual canary rollouts.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
  } else {
    uiState.workerVersions.take(6).forEach { ver ->
      Surface(color = palette.chromePill, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "v${ver.number ?: "?"} • ${ver.id.take(10)}…",
              style = MaterialTheme.typography.labelMedium,
              color = palette.textPrimary,
            )
            Text(
              text = "${ver.metadata?.source ?: "upload"} • ${ver.metadata?.createdOn?.take(19)?.replace('T', ' ').orEmpty()}",
              style = MaterialTheme.typography.labelSmall,
              color = palette.textMuted,
              maxLines = 1,
            )
          }
          TextButton(onClick = { pendingVersion = ver.id }) { Text("Promote") }
        }
      }
    }
  }

  HorizontalDivider(color = palette.treeLine)
  Text("DEPLOYMENTS & ROLLBACK", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
  if (ws.deployments.isEmpty()) {
    Text("No deployment history available.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
  } else {
    ws.deployments.take(10).forEachIndexed { idx, dep ->
      val versionSummary = dep.versions.joinToString(" / ") { "${it.versionId.take(6)} (${it.percentage.toInt()}%)" }
      val firstVersionId = dep.versions.firstOrNull()?.versionId.orEmpty()
      Surface(color = palette.chromePill, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.History, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (idx == 0) "Active • $versionSummary" else versionSummary.ifBlank { "Deployment" },
              style = MaterialTheme.typography.labelMedium,
              color = if (idx == 0) palette.activePillFg else palette.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = dep.createdOn?.take(19)?.replace('T', ' ') ?: dep.source.orEmpty(),
              style = MaterialTheme.typography.labelSmall,
              color = palette.textMuted,
              maxLines = 1,
            )
          }
          if (idx > 0 && firstVersionId.isNotBlank()) {
            TextButton(onClick = { pendingVersion = firstVersionId }) { Text("Rollback") }
          }
        }
      }
    }
  }
}
