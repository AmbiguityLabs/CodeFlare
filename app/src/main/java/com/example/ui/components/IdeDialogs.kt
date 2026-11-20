package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ZipImportTargetMode
import com.example.data.local.ZipPreviewState
import com.example.data.remote.CloudflareNamingUtils
import com.example.data.repository.WorkerTemplate
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.PendingUnsavedWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewWorkerDialog(
  pickedUris: List<Uri>,
  onPickFiles: () -> Unit,
  onClearPickedFiles: () -> Unit,
  onDismiss: () -> Unit,
  onCreateFromTemplate: (name: String, template: WorkerTemplate) -> Unit,
  onCreateFromFiles: (name: String, uris: List<Uri>, folderPrefix: String) -> Unit,
  zipPreview: ZipPreviewState? = null,
  onPickZip: () -> Unit = {},
  onToggleZipStripRoot: (Boolean) -> Unit = {},
  onClearPickedZip: () -> Unit = {},
  onCreateFromZip: (name: String) -> Unit = {},
) {
  val palette = LocalIdePalette.current
  val context = LocalContext.current
  var name by remember { mutableStateOf("my-edge-worker") }
  var sourceMode by remember {
    mutableIntStateOf(
      when {
        zipPreview != null -> 2
        pickedUris.isNotEmpty() -> 1
        else -> 0
      },
    )
  }
  var selectedTemplate by remember { mutableStateOf(WorkerTemplate.HELLO_FETCH) }
  var folderPrefix by remember { mutableStateOf("") }
  var showMissingFilesHint by remember { mutableStateOf(false) }
  var showMissingZipHint by remember { mutableStateOf(false) }

  LaunchedEffect(zipPreview?.plan?.suggestedProjectName) {
    val suggested = zipPreview?.plan?.suggestedProjectName
    if (!suggested.isNullOrBlank()) {
      sourceMode = 2
      if (name == "my-edge-worker" || name.isBlank()) {
        name = suggested
      }
    }
  }

  LaunchedEffect(pickedUris.size) {
    if (pickedUris.isNotEmpty()) {
      sourceMode = 1
    }
  }

  val pickedNames =
    remember(pickedUris) {
      pickedUris.map { resolveUriName(context, it) }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Worker") },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Worker Script Name") },
          placeholder = { Text("my-edge-worker") },
          trailingIcon = {
            if (name.isNotEmpty()) {
              IconButton(onClick = { name = "" }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear name", modifier = Modifier.size(16.dp))
              }
            }
          },
          supportingText =
            if (name.isBlank()) {
              { Text("Defaults to my-edge-worker", fontSize = 11.sp) }
            } else {
              null
            },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("new_worker_name_input"),
        )

        Surface(
          color = palette.chromePill,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            listOf(
              Triple(0, "Template", "new_worker_template_mode"),
              Triple(1, "Files", "new_worker_upload_mode"),
              Triple(2, "ZIP", "new_worker_zip_mode"),
            ).forEach { (idx, label, tag) ->
              val selected = sourceMode == idx
              Surface(
                onClick = {
                  sourceMode = idx
                  showMissingFilesHint = false
                  showMissingZipHint = false
                },
                shape = RoundedCornerShape(9.dp),
                color = if (selected) palette.activePillBg else palette.chromePill,
                modifier = Modifier.weight(1f).testTag(tag),
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelLarge,
                  color = if (selected) palette.activePillFg else palette.textSecondary,
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp),
                )
              }
            }
          }
        }

        when (sourceMode) {
          0 -> {
            Text(
              text = "Starter Template",
              style = MaterialTheme.typography.labelMedium,
              color = palette.textSecondary,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              WorkerTemplate.entries.forEach { tpl ->
                val isSelected = selectedTemplate == tpl
                val shortTag =
                  when (tpl) {
                    WorkerTemplate.HELLO_FETCH -> "HTTP"
                    WorkerTemplate.JSON_ROUTER -> "Router"
                    WorkerTemplate.CORS_PROXY -> "Proxy"
                    WorkerTemplate.CRON_WORKER -> "Cron"
                    WorkerTemplate.HONO_ROUTER -> "Hono"
                    WorkerTemplate.BEARER_AUTH_WORKER -> "Auth"
                    WorkerTemplate.PAGES_FUNCTIONS_API -> "Pages"
                  }
                Surface(
                  onClick = { selectedTemplate = tpl },
                  shape = RoundedCornerShape(12.dp),
                  color =
                    if (isSelected) palette.activePillBg.copy(alpha = 0.72f)
                    else palette.chromePill,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    RadioButton(selected = isSelected, onClick = { selectedTemplate = tpl })
                    Text(
                      text = tpl.title,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                      color = if (isSelected) palette.activePillFg else palette.textPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    Surface(
                      color = if (isSelected) palette.ctaBg else palette.chromeSurface,
                      shape = RoundedCornerShape(6.dp),
                    ) {
                      Text(
                        text = shortTag,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) palette.ctaFg else palette.textMuted,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                      )
                    }
                  }
                }
              }
            }
          }

          1 -> {
            OutlinedButton(
              onClick = {
                showMissingFilesHint = false
                onPickFiles()
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("new_worker_pick_files_button"),
            ) {
              Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(
                if (pickedNames.isEmpty()) "Select Script File(s)"
                else "${pickedNames.size} File(s) Selected — Change",
              )
            }

            if (showMissingFilesHint && pickedNames.isEmpty()) {
              Text(
                text = "Select at least one script file from your device first.",
                style = MaterialTheme.typography.labelSmall,
                color = RoseError,
              )
            }

            if (pickedNames.isNotEmpty()) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                pickedNames.forEach { fileName ->
                  Surface(
                    color = palette.chromePill,
                    shape = RoundedCornerShape(8.dp),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                      Text(fileName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                  }
                }
              }

              OutlinedTextField(
                value = folderPrefix,
                onValueChange = { folderPrefix = it },
                label = { Text("Subfolder prefix (optional)") },
                placeholder = { Text("src") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              )

              TextButton(onClick = onClearPickedFiles) {
                Text("Clear selected files")
              }
            } else {
              Text(
                text = "Supports .js, .mjs, .ts, .json, .html, .sql",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
              )
            }
          }

          else -> {
            OutlinedButton(
              onClick = {
                showMissingZipHint = false
                onPickZip()
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("new_worker_pick_zip_button"),
            ) {
              Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(
                zipPreview?.plan?.archiveName?.let { "$it — Change" }
                  ?: "Select .zip Archive",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            if (showMissingZipHint && zipPreview?.plan == null) {
              Text(
                text = "Select a valid .zip archive first.",
                style = MaterialTheme.typography.labelSmall,
                color = RoseError,
              )
            }

            when {
              zipPreview?.isLoading == true -> {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  EdgeLoadingIndicator(size = 18.dp, strokeWidth = 2.dp)
                  Text(
                    text = "Inspecting ZIP archive…",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                  )
                }
              }

              zipPreview?.errorMessage != null -> {
                Surface(
                  color = RoseError.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = zipPreview.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = RoseError,
                    modifier = Modifier.padding(10.dp),
                  )
                }
              }

              zipPreview?.plan != null -> {
                val plan = zipPreview.plan
                ZipPlanSummarySection(
                  plan = plan,
                  commonRootFolder = zipPreview.archiveReadResult?.commonRootFolder,
                  stripRootFolder = zipPreview.stripRootFolder,
                  onToggleStripRoot = onToggleZipStripRoot,
                )
                TextButton(onClick = onClearPickedZip) {
                  Text("Clear ZIP selection")
                }
              }

              else -> {
                Text(
                  text = "Extracts multi-file Workers, Wrangler configs, and web projects directly from .zip archives.",
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.textSecondary,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      val resolvedName = name.trim().ifBlank { "my-edge-worker" }
      Button(
        onClick = {
          when (sourceMode) {
            1 -> {
              if (pickedUris.isEmpty()) {
                showMissingFilesHint = true
              } else {
                onCreateFromFiles(resolvedName, pickedUris, folderPrefix)
              }
            }
            2 -> {
              if (zipPreview?.plan == null) {
                showMissingZipHint = true
              } else {
                onCreateFromZip(resolvedName)
              }
            }
            else -> onCreateFromTemplate(resolvedName, selectedTemplate)
          }
        },
        enabled = sourceMode != 2 || zipPreview?.isLoading != true,
        colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
        modifier = Modifier.testTag("confirm_create_worker_button"),
      ) {
        Text(
          text =
            when (sourceMode) {
              1 -> "Import & Create"
              2 -> "Import ZIP"
              else -> "Create Project"
            },
          fontWeight = FontWeight.SemiBold,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZipImportPreviewDialog(
  preview: ZipPreviewState,
  activeWorkerName: String? = null,
  isLocalScratchpad: Boolean = true,
  onToggleStripRoot: (Boolean) -> Unit,
  onDismiss: () -> Unit,
  onConfirmNewProject: (projectName: String, deployNow: Boolean) -> Unit,
  onConfirmMergeIntoActive: (folderPrefix: String, autoDeploy: Boolean) -> Unit,
) {
  val palette = LocalIdePalette.current
  val plan = preview.plan
  val isMergeMode = preview.targetMode == ZipImportTargetMode.MERGE_INTO_ACTIVE
  var projectName by remember(plan?.suggestedProjectName) {
    mutableStateOf(plan?.suggestedProjectName.orEmpty())
  }
  var folderPrefix by remember { mutableStateOf("") }
  var deployNow by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Outlined.Archive,
        contentDescription = null,
        tint = palette.activePillFg,
      )
    },
    title = {
      Text(
        text =
          if (isMergeMode) "Import ZIP into ${activeWorkerName ?: "Project"}"
          else "Import ZIP Project",
      )
    },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        when {
          preview.isLoading -> {
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              EdgeLoadingIndicator(size = 22.dp, strokeWidth = 2.5.dp)
              Text(
                text = "Extracting & analyzing ZIP archive…",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
              )
            }
          }

          preview.errorMessage != null -> {
            Surface(
              color = RoseError.copy(alpha = 0.14f),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = preview.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = RoseError,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          plan != null -> {
            Surface(
              color = palette.chromePill,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = plan.archiveName,
                  fontFamily = JetBrainsMono,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text =
                    if (plan.totalArchiveBytes >= 1024L * 1024L) {
                      String.format("%.1f MB", plan.totalArchiveBytes / (1024.0 * 1024.0))
                    } else {
                      "${((plan.totalArchiveBytes + 1023L) / 1024L).coerceAtLeast(1L)} KB"
                    },
                  fontFamily = JetBrainsMono,
                  fontSize = 10.5.sp,
                  color = palette.textMuted,
                )
              }
            }

            if (!isMergeMode) {
              OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text("Project Name") },
                placeholder = { Text(plan.suggestedProjectName) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("zip_import_project_name_input"),
              )
            } else {
              OutlinedTextField(
                value = folderPrefix,
                onValueChange = { folderPrefix = it },
                label = { Text("Target folder prefix (optional)") },
                placeholder = { Text("src/imported") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("zip_import_folder_prefix_input"),
              )
            }

            ZipPlanSummarySection(
              plan = plan,
              commonRootFolder = preview.archiveReadResult?.commonRootFolder,
              stripRootFolder = preview.stripRootFolder,
              onToggleStripRoot = onToggleStripRoot,
            )

            if (!isLocalScratchpad) {
              Surface(
                color = palette.chromePill,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Deploy to Cloudflare",
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Medium,
                      color = palette.textPrimary,
                    )
                    Text(
                      text = "Publish modules right after import",
                      style = MaterialTheme.typography.labelSmall,
                      color = palette.textMuted,
                    )
                  }
                  Switch(
                    checked = deployNow,
                    onCheckedChange = { deployNow = it },
                    modifier = Modifier.testTag("zip_import_deploy_switch"),
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (plan != null && !preview.isLoading) {
        Button(
          onClick = {
            if (isMergeMode) {
              onConfirmMergeIntoActive(folderPrefix, deployNow)
            } else {
              onConfirmNewProject(projectName.ifBlank { plan.suggestedProjectName }, deployNow)
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
          modifier = Modifier.testTag("confirm_zip_import_button"),
        ) {
          Text(
            text =
              when {
                deployNow && !isLocalScratchpad -> "Import & Deploy"
                isMergeMode -> "Import Files"
                else -> "Import Project"
              },
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZipPlanSummarySection(
  plan: com.example.data.local.ZipImportPlan,
  commonRootFolder: String?,
  stripRootFolder: Boolean,
  onToggleStripRoot: (Boolean) -> Unit,
) {
  val palette = LocalIdePalette.current
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(color = palette.activePillBg.copy(alpha = 0.7f), shape = RoundedCornerShape(7.dp)) {
        Text(
          text = "${plan.files.size} file(s)",
          fontFamily = JetBrainsMono,
          fontSize = 10.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = palette.activePillFg,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
      Surface(color = EmeraldLive.copy(alpha = 0.16f), shape = RoundedCornerShape(7.dp)) {
        Text(
          text = "main: ${plan.entryModulePath}",
          fontFamily = JetBrainsMono,
          fontSize = 10.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = EmeraldLive,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
      if (plan.bindings.isNotEmpty()) {
        Surface(color = palette.chromePill, shape = RoundedCornerShape(7.dp)) {
          Text(
            text = "${plan.bindings.size} binding(s)",
            fontFamily = JetBrainsMono,
            fontSize = 10.5.sp,
            color = palette.textPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
      if (plan.skippedBinaryFiles.isNotEmpty()) {
        Surface(color = AmberWarn.copy(alpha = 0.16f), shape = RoundedCornerShape(7.dp)) {
          Text(
            text = "${plan.skippedBinaryFiles.size} binary skipped",
            fontFamily = JetBrainsMono,
            fontSize = 10.5.sp,
            color = AmberWarn,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
    }

    if (!commonRootFolder.isNullOrBlank()) {
      Surface(
        color = palette.chromePill,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Strip wrapper folder",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
              color = palette.textPrimary,
            )
            Text(
              text = "$commonRootFolder/ → root",
              fontFamily = JetBrainsMono,
              fontSize = 10.5.sp,
              color = palette.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Switch(
            checked = stripRootFolder,
            onCheckedChange = onToggleStripRoot,
            modifier = Modifier.testTag("zip_strip_root_switch"),
          )
        }
      }
    }

    val previewLimit = 24
    val shownFiles = plan.files.take(previewLimit)
    Surface(
      color = palette.chromeBg,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        shownFiles.forEach { wf ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = if (wf.path.contains('/')) Icons.Outlined.Folder else Icons.Outlined.Description,
              contentDescription = null,
              tint = if (wf.isEntryPoint) EmeraldLive else palette.textMuted,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = wf.path,
              fontFamily = JetBrainsMono,
              fontSize = 11.sp,
              color = if (wf.isEntryPoint) EmeraldLive else palette.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
          }
        }
        if (plan.files.size > previewLimit) {
          Text(
            text = "+ ${plan.files.size - previewLimit} more file(s)",
            fontFamily = JetBrainsMono,
            fontSize = 10.5.sp,
            color = palette.textMuted,
            modifier = Modifier.padding(top = 2.dp),
          )
        }
      }
    }
  }
}

@Composable
fun NewModuleFileDialog(
  initialPrefix: String = "",
  onDismiss: () -> Unit,
  onCreate: (fileName: String) -> Unit,
) {
  val palette = LocalIdePalette.current
  val defaultName =
    if (initialPrefix.isBlank()) "src/script.js"
    else "${initialPrefix.trimEnd('/')}/script.js"
  var fileName by remember(initialPrefix) { mutableStateOf(defaultName) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New File") },
    text = {
      OutlinedTextField(
        value = fileName,
        onValueChange = { fileName = it },
        label = { Text("File path (.js, .ts, .html, .json, .toml)") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("new_module_filename_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = { onCreate(fileName) },
        enabled = fileName.trim().isNotEmpty(),
        colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
      ) {
        Text("Add File", fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun NewDirectoryDialog(
  basePath: String = "",
  onDismiss: () -> Unit,
  onCreate: (dirPath: String) -> Unit,
) {
  val palette = LocalIdePalette.current
  val initial = if (basePath.isBlank()) "src/routes" else "${basePath.trimEnd('/')}/new-folder"
  var dirPath by remember(basePath) { mutableStateOf(initial) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Directory") },
    text = {
      OutlinedTextField(
        value = dirPath,
        onValueChange = { dirPath = it },
        label = { Text("Directory path") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("new_directory_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = { onCreate(dirPath) },
        enabled = dirPath.trim().isNotEmpty(),
        colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
      ) {
        Text("Create", fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun RenameFileDialog(
  currentPath: String,
  onDismiss: () -> Unit,
  onRename: (newPath: String) -> Unit,
) {
  val palette = LocalIdePalette.current
  var nextPath by remember(currentPath) { mutableStateOf(currentPath) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename or Move Module") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Change file name or move into a folder path (e.g. src/lib/auth.js).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = nextPath,
          onValueChange = { nextPath = it },
          label = { Text("Module path") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("rename_module_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onRename(nextPath) },
        enabled = nextPath.trim().isNotEmpty() && nextPath.trim() != currentPath,
        colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
        modifier = Modifier.testTag("confirm_rename_module_button"),
      ) {
        Text("Rename", fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchUploadOptionsDialog(
  workerName: String,
  pickedUris: List<Uri>,
  isLocalScratchpad: Boolean,
  onDismiss: () -> Unit,
  onConfirmUpload: (folderPrefix: String, autoDeploy: Boolean) -> Unit,
) {
  val context = LocalContext.current
  val pickedNames = remember(pickedUris) { pickedUris.map { resolveUriName(context, it) } }
  var folderPrefix by remember { mutableStateOf("") }
  var autoDeploy by remember { mutableStateOf(!isLocalScratchpad) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        if (pickedNames.size == 1) "Upload File to $workerName"
        else "Batch Upload ${pickedNames.size} Files",
      )
    },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          pickedNames.forEach { fileName ->
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shape = RoundedCornerShape(8.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(fileName, style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }

        OutlinedTextField(
          value = folderPrefix,
          onValueChange = { folderPrefix = it },
          label = { Text("Target folder prefix (optional, e.g. src/routes)") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("upload_folder_prefix_input"),
        )

        if (!isLocalScratchpad) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Deploy immediately to Cloudflare", style = MaterialTheme.typography.bodyMedium)
              Text(
                text = "Upload all modules & publish live right away",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = autoDeploy,
              onCheckedChange = { autoDeploy = it },
              modifier = Modifier.testTag("upload_auto_deploy_switch"),
            )
          }
        }
      }
    },
    confirmButton = {
      val palette = LocalIdePalette.current
      Button(
        onClick = { onConfirmUpload(folderPrefix, autoDeploy) },
        colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
        modifier = Modifier.testTag("confirm_upload_files_button"),
      ) {
        Text(
          text = if (autoDeploy && !isLocalScratchpad) "Import & Deploy" else "Import to Worker",
          fontWeight = FontWeight.SemiBold,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

data class PaletteCommand(
  val title: String,
  val subtitle: String,
  val action: () -> Unit,
)

@Composable
fun CommandPaletteDialog(
  commands: List<PaletteCommand>,
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  var query by remember { mutableStateOf("") }
  val filtered =
    remember(query, commands) {
      if (query.isBlank()) commands
      else commands.filter {
        it.title.contains(query, ignoreCase = true) ||
          it.subtitle.contains(query, ignoreCase = true)
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = palette.chromeSurface,
    title = {
      Text(
        text = "Command Palette",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = palette.textPrimary,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Command or file…", fontFamily = JetBrainsMono, fontSize = 12.sp) },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontSize = 12.5.sp),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("command_palette_input"),
        )
        LazyColumn(
          modifier = Modifier.heightIn(max = 340.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(filtered) { cmd ->
            Surface(
              onClick = {
                onDismiss()
                cmd.action()
              },
              shape = RoundedCornerShape(9.dp),
              color = palette.chromeBg,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = cmd.title,
                  style = MaterialTheme.typography.bodyMedium,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                if (cmd.subtitle.isNotBlank()) {
                  Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = palette.chromePill,
                  ) {
                    Text(
                      text = cmd.subtitle,
                      fontFamily = JetBrainsMono,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = palette.textMuted,
                      maxLines = 1,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}

@Composable
fun RenameWorkerDialog(
  currentName: String,
  subdomain: String,
  isRemoteWorker: Boolean,
  hasEncryptedSecrets: Boolean = false,
  onDismiss: () -> Unit,
  onConfirm: (newName: String, deleteOldRemote: Boolean) -> Unit,
) {
  val palette = LocalIdePalette.current
  var rawInput by remember(currentName) { mutableStateOf(currentName) }
  var deleteOldRemote by remember { mutableStateOf(true) }
  val sanitized =
    remember(rawInput, isRemoteWorker) {
      CloudflareNamingUtils.sanitizeWorkerName(rawInput, allowUnderscore = !isRemoteWorker)
    }
  val isValid =
    remember(sanitized, isRemoteWorker) {
      if (isRemoteWorker) CloudflareNamingUtils.isValidWorkerName(sanitized)
      else sanitized.isNotBlank()
    }
  val changed = sanitized.isNotBlank() && sanitized != currentName

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Outlined.DriveFileRenameOutline,
        contentDescription = null,
        tint = palette.activePillFg,
      )
    },
    title = { Text("Rename Worker") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = palette.chromePill, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("Current Name", fontSize = 11.sp, color = palette.textMuted)
            Text(
              text = currentName,
              fontFamily = JetBrainsMono,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.textPrimary,
            )
          }
        }

        OutlinedTextField(
          value = rawInput,
          onValueChange = { rawInput = it },
          label = { Text("New Worker Name") },
          placeholder = { Text("my-renamed-worker") },
          singleLine = true,
          isError = rawInput.isNotBlank() && !isValid,
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
          trailingIcon = {
            if (rawInput.isNotEmpty()) {
              IconButton(onClick = { rawInput = "" }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
              }
            }
          },
          supportingText = {
            Text(
              text =
                if (!isValid && rawInput.isNotBlank()) {
                  "Use 1–63 lowercase letters, numbers, or hyphens (no leading/trailing hyphen)"
                } else if (sanitized.isNotBlank() && sanitized != rawInput.trim()) {
                  "Normalized to: $sanitized"
                } else {
                  "Updates wrangler.jsonc, local directory${if (isRemoteWorker) " & Cloudflare deployment" else ""}"
                },
              fontSize = 11.sp,
            )
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("rename_worker_input"),
        )

        if (sanitized.isNotBlank()) {
          val subDisplay = subdomain.ifBlank { "<subdomain>" }
          Surface(
            color = palette.activePillBg.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("NEW WORKERS.DEV ENDPOINT", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.activePillFg)
              Text(
                text = "https://$sanitized.$subDisplay.workers.dev",
                fontFamily = JetBrainsMono,
                fontSize = 11.5.sp,
                color = EmeraldLive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        if (isRemoteWorker) {
          Surface(color = palette.chromePill, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Delete old Cloudflare script", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                Text(
                  text = "Deploys '$sanitized' then removes '$currentName'",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textMuted,
                )
              }
              Switch(checked = deleteOldRemote, onCheckedChange = { deleteOldRemote = it })
            }
          }
          if (hasEncryptedSecrets) {
            Surface(color = AmberWarn.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
              Text(
                text = "Note: Cloudflare does not expose existing encrypted secret values via API; re-add secrets on '$sanitized' after renaming.",
                fontSize = 11.sp,
                color = AmberWarn,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      FilledTonalButton(
        onClick = { onConfirm(sanitized, deleteOldRemote) },
        enabled = isValid && changed,
        modifier = Modifier.testTag("confirm_rename_worker_button"),
      ) {
        Text(if (isRemoteWorker) "Rename & Deploy" else "Rename")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun ChangeSubdomainDialog(
  currentSubdomain: String,
  sampleWorkerName: String,
  isLocalMode: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (newSubdomain: String) -> Unit,
) {
  val palette = LocalIdePalette.current
  var rawInput by remember(currentSubdomain) { mutableStateOf(currentSubdomain) }
  val sanitized = remember(rawInput) { CloudflareNamingUtils.sanitizeSubdomain(rawInput) }
  val isValid = remember(sanitized) { CloudflareNamingUtils.isValidSubdomain(sanitized) }
  val workerPreview = sampleWorkerName.ifBlank { "my-worker" }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Outlined.Language,
        contentDescription = null,
        tint = palette.activePillFg,
      )
    },
    title = { Text("Change workers.dev Subdomain") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = rawInput,
          onValueChange = { rawInput = it },
          label = { Text("Account Subdomain") },
          placeholder = { Text("my-edge-team") },
          suffix = { Text(".workers.dev", fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textMuted) },
          singleLine = true,
          isError = rawInput.isNotBlank() && !isValid,
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
          supportingText = {
            Text(
              text =
                if (!isValid && rawInput.isNotBlank()) {
                  "Must be 1–63 lowercase letters, numbers, or hyphens"
                } else {
                  "You can type just the prefix (e.g. my-team) or full my-team.workers.dev"
                },
              fontSize = 11.sp,
            )
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("change_subdomain_input"),
        )

        if (sanitized.isNotBlank()) {
          Surface(
            color = palette.activePillBg.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("LIVE PREVIEW URL", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = palette.activePillFg)
              Text(
                text = "https://$workerPreview.$sanitized.workers.dev",
                fontFamily = JetBrainsMono,
                fontSize = 11.5.sp,
                color = EmeraldLive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Surface(color = palette.chromePill, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
          Text(
            text =
              if (isLocalMode) {
                "Updates the local preview & API tester domain prefix for your offline projects."
              } else {
                "Updates your Cloudflare account's *.workers.dev subdomain via Cloudflare API. All Workers on this account will serve on *.$sanitized.workers.dev."
              },
            fontSize = 11.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          )
        }
      }
    },
    confirmButton = {
      FilledTonalButton(
        onClick = { onConfirm(sanitized) },
        enabled = isValid && sanitized != currentSubdomain,
        modifier = Modifier.testTag("confirm_change_subdomain_button"),
      ) {
        Text("Save Subdomain")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun UnsavedChangesWarningDialog(
  warning: PendingUnsavedWarning,
  onKeepDraft: () -> Unit,
  onDeployNow: () -> Unit,
  onDiscard: () -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Outlined.WarningAmber,
        contentDescription = null,
        tint = AmberWarn,
      )
    },
    title = {
      Text(
        text = warning.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("unsaved_changes_warning_dialog"),
      ) {
        Text(
          text = warning.message,
          style = MaterialTheme.typography.bodySmall,
          color = palette.textSecondary,
        )
        if (warning.dirtyFilePaths.isNotEmpty()) {
          Surface(
            color = palette.chromePill,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Text(
                text = "Modified files (${warning.dirtyFilePaths.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.textMuted,
              )
              warning.dirtyFilePaths.take(6).forEach { path ->
                Text(
                  text = "• $path",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (warning.dirtyFilePaths.size > 6) {
                Text(
                  text = "+ ${warning.dirtyFilePaths.size - 6} more file(s)",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textMuted,
                )
              }
            }
          }
        }
        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (warning.allowKeepLocalDraft) {
            FilledTonalButton(
              onClick = onKeepDraft,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().testTag("unsaved_keep_draft_button"),
            ) {
              Text("Keep Local Draft")
            }
          }
          if (warning.allowDeployNow) {
            OutlinedButton(
              onClick = onDeployNow,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().testTag("unsaved_deploy_now_button"),
            ) {
              Text("Deploy Now")
            }
          }
          OutlinedButton(
            onClick = onDiscard,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
            modifier = Modifier.fillMaxWidth().testTag("unsaved_discard_button"),
          ) {
            Text(if (warning.allowKeepLocalDraft) "Discard Changes" else "Overwrite / Discard")
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun GoToLineDialog(
  fileName: String,
  totalLines: Int,
  currentLine: Int,
  currentColumn: Int,
  lineSnippetProvider: (Int) -> String,
  onDismiss: () -> Unit,
  onJumpTo: (line: Int, col: Int) -> Unit,
) {
  val palette = LocalIdePalette.current
  val safeTotal = totalLines.coerceAtLeast(1)
  var query by remember(fileName, currentLine, currentColumn) {
    mutableStateOf("$currentLine:$currentColumn")
  }

  val normalized = query.trim().removePrefix(":")
  val parts = normalized.split(':')
  val parsedLine = parts.getOrNull(0)?.trim()?.toIntOrNull()
  val parsedCol = (parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1).coerceAtLeast(1)
  val targetLine = parsedLine?.coerceIn(1, safeTotal)
  val isOutOfRange = parsedLine != null && parsedLine !in 1..safeTotal
  val previewSnippet = remember(targetLine, fileName) {
    targetLine?.let { lineSnippetProvider(it).trim() }.orEmpty()
  }

  fun submitIfValid() {
    if (targetLine != null) {
      onJumpTo(targetLine, parsedCol)
      onDismiss()
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Go to Line / Column")
        Text(
          text = "$fileName · $safeTotal lines (now Ln $currentLine:$currentColumn)",
          fontFamily = JetBrainsMono,
          fontSize = 11.sp,
          color = palette.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = query,
          onValueChange = { next ->
            if (next.length <= 24) query = next
          },
          label = { Text("Line or line:column (e.g. 42 or 42:8)") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
          keyboardActions = KeyboardActions(onGo = { submitIfValid() }),
          modifier = Modifier.fillMaxWidth().testTag("goto_line_dialog_input"),
        )
        Surface(
          color = palette.chromeBg,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            if (targetLine != null) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = "Target Ln $targetLine:$parsedCol",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (isOutOfRange) AmberWarn else palette.ctaBg,
                )
                if (isOutOfRange) {
                  Text(
                    text = "(clamped to 1..$safeTotal)",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarn,
                  )
                }
              }
              Text(
                text = previewSnippet.ifEmpty { "<empty line>" },
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = if (previewSnippet.isEmpty()) palette.textMuted else palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            } else {
              Text(
                text = "Enter a line number between 1 and $safeTotal",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { submitIfValid() },
        enabled = targetLine != null,
        colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
        modifier = Modifier.testTag("goto_line_confirm_button"),
      ) {
        Text("Jump", fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

private fun resolveUriName(context: Context, uri: Uri): String {
  context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) {
      val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (idx >= 0) {
        val name = cursor.getString(idx)
        if (!name.isNullOrBlank()) return name
      }
    }
  }
  return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "script.js" } ?: "script.js"
}
