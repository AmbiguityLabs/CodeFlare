package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repository.WorkerTemplate
import com.example.ui.theme.LocalIdePalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewWorkerDialog(
  pickedUris: List<Uri>,
  onPickFiles: () -> Unit,
  onClearPickedFiles: () -> Unit,
  onDismiss: () -> Unit,
  onCreateFromTemplate: (name: String, template: WorkerTemplate) -> Unit,
  onCreateFromFiles: (name: String, uris: List<Uri>, folderPrefix: String) -> Unit,
) {
  val palette = LocalIdePalette.current
  val context = LocalContext.current
  var name by remember { mutableStateOf("") }
  var uploadMode by remember { mutableStateOf(pickedUris.isNotEmpty()) }
  var selectedTemplate by remember { mutableStateOf(WorkerTemplate.HELLO_FETCH) }
  var folderPrefix by remember { mutableStateOf("") }

  val pickedNames =
    remember(pickedUris) {
      pickedUris.map { resolveUriName(context, it) }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create Cloudflare Worker") },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Worker Script Name") },
          placeholder = { Text("my-edge-api") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("new_worker_name_input"),
        )

        // Source Mode Pill Switcher: Template vs Upload Files
        Surface(
          color = palette.chromePill,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Surface(
              onClick = { uploadMode = false },
              shape = RoundedCornerShape(9.dp),
              color = if (!uploadMode) palette.activePillBg else palette.chromePill,
              modifier = Modifier.weight(1f).testTag("new_worker_template_mode"),
            ) {
              Text(
                text = "Template",
                style = MaterialTheme.typography.labelLarge,
                color = if (!uploadMode) palette.activePillFg else palette.textSecondary,
                fontWeight = if (!uploadMode) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(vertical = 8.dp).padding(horizontal = 10.dp),
              )
            }
            Surface(
              onClick = { uploadMode = true },
              shape = RoundedCornerShape(9.dp),
              color = if (uploadMode) palette.activePillBg else palette.chromePill,
              modifier = Modifier.weight(1f).testTag("new_worker_upload_mode"),
            ) {
              Text(
                text = "Upload File(s)",
                style = MaterialTheme.typography.labelLarge,
                color = if (uploadMode) palette.activePillFg else palette.textSecondary,
                fontWeight = if (uploadMode) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(vertical = 8.dp).padding(horizontal = 10.dp),
              )
            }
          }
        }

        if (!uploadMode) {
          Text(
            text = "Starter Template",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          WorkerTemplate.entries.forEach { tpl ->
            Surface(
              onClick = { selectedTemplate = tpl },
              shape = RoundedCornerShape(12.dp),
              color =
                if (selectedTemplate == tpl) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(selected = selectedTemplate == tpl, onClick = { selectedTemplate = tpl })
                Column(modifier = Modifier.weight(1f)) {
                  Text(tpl.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                  Text(tpl.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        } else {
          OutlinedButton(
            onClick = onPickFiles,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("new_worker_pick_files_button"),
          ) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
              if (pickedNames.isEmpty()) "Select Script File(s) from Device"
              else "Selected ${pickedNames.size} File(s) — Change",
            )
          }

          if (pickedNames.isNotEmpty()) {
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
                    Text(fileName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }

            OutlinedTextField(
              value = folderPrefix,
              onValueChange = { folderPrefix = it },
              label = { Text("Subfolder prefix (optional, e.g. src)") },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            )

            TextButton(onClick = onClearPickedFiles) {
              Text("Clear selected files")
            }
          } else {
            Text(
              text = "Pick a single index.js or batch select multiple ES module files (.js, .mjs, .json, .html, .sql) to scaffold & deploy.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    },
    confirmButton = {
      val canConfirm =
        name.trim().isNotEmpty() && (!uploadMode || pickedUris.isNotEmpty())
      Button(
        onClick = {
          if (uploadMode) onCreateFromFiles(name, pickedUris, folderPrefix)
          else onCreateFromTemplate(name, selectedTemplate)
        },
        enabled = canConfirm,
        modifier = Modifier.testTag("confirm_create_worker_button"),
      ) {
        Text(if (uploadMode) "Upload & Create" else "Create & Deploy")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun NewModuleFileDialog(
  initialPrefix: String = "",
  defaultRunLocally: Boolean = false,
  onDismiss: () -> Unit,
  onCreate: (fileName: String, runLocally: Boolean) -> Unit,
) {
  val defaultName =
    if (initialPrefix.isBlank()) "src/script.js"
    else "${initialPrefix.trimEnd('/')}/script.js"
  var fileName by remember(initialPrefix) { mutableStateOf(defaultName) }
  var runLocally by remember(defaultRunLocally) { mutableStateOf(defaultRunLocally) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Script / File") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = fileName,
          onValueChange = { fileName = it },
          label = { Text("File path (.js, .ts, .html, .sh, .json)") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("new_module_filename_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Run locally right after create", style = MaterialTheme.typography.bodyMedium)
            Text(
              text = "Test in local V8/shell without deploying to Cloudflare",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = runLocally, onCheckedChange = { runLocally = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onCreate(fileName, runLocally) },
        enabled = fileName.trim().isNotEmpty(),
      ) {
        Text(if (runLocally) "Create & Run" else "Add File")
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
      ) {
        Text("Create")
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
        modifier = Modifier.testTag("confirm_rename_module_button"),
      ) {
        Text("Rename")
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
      FilledTonalButton(
        onClick = { onConfirmUpload(folderPrefix, autoDeploy) },
        modifier = Modifier.testTag("confirm_upload_files_button"),
      ) {
        Text(if (autoDeploy && !isLocalScratchpad) "Import & Deploy" else "Import to Worker")
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
    title = { Text("Command Palette") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Type a command…") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("command_palette_input"),
        )
        LazyColumn(
          modifier = Modifier.heightIn(max = 320.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(filtered) { cmd ->
            Surface(
              onClick = {
                onDismiss()
                cmd.action()
              },
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(cmd.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(cmd.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
