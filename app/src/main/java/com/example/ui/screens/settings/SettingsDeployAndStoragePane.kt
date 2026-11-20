package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.EditorSettings
import com.example.ui.components.IconRevealButton
import com.example.ui.theme.Ca
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.IdeUiState

@Composable
fun SettingsDeployPane(
  settings: EditorSettings,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  var compatDateDraft by rememberSaveable(settings.defaultCompatibilityDate) {
    mutableStateOf(settings.defaultCompatibilityDate)
  }

  Column(
    modifier = modifier.fillMaxWidth().testTag("settings_deploy_defaults_section"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SettingsCard(title = "Compatibility Date") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = compatDateDraft,
          onValueChange = { compatDateDraft = it },
          label = { Text("Date") },
          singleLine = true,
          textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp),
          shape = RoundedCornerShape(Ca.radius.control),
          modifier = Modifier.weight(1f).testTag("settings_compat_date_input"),
        )
        Button(
          onClick = {
            val clean = compatDateDraft.trim().ifBlank { "2024-11-01" }
            compatDateDraft = clean
            onUpdateSettings { it.copy(defaultCompatibilityDate = clean) }
            onNotify("Saved: $clean")
          },
          enabled = compatDateDraft.trim().isNotEmpty() && compatDateDraft.trim() != settings.defaultCompatibilityDate,
          colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
          shape = RoundedCornerShape(Ca.radius.control),
          modifier = Modifier.height(50.dp),
        ) {
          Text("Save", fontWeight = FontWeight.SemiBold)
        }
      }

      SettingsChoiceRow(
        title = "Runtime presets",
        description = "Select a standard Cloudflare Workers compatibility date",
        selected = settings.defaultCompatibilityDate,
        options = listOf("2024-11-01" to "2024-11-01", "2025-05-01" to "2025-05-01", "2026-01-01" to "2026-01-01"),
        onSelect = { preset ->
          compatDateDraft = preset
          onUpdateSettings { it.copy(defaultCompatibilityDate = preset) }
        },
      )
    }

    SettingsCard(title = "Worker Defaults") {
      SettingsToggleRow(
        title = "nodejs_compat",
        description = "Enable Node.js compatibility APIs in new Workers",
        checked = settings.defaultNodejsCompat,
        horizontalPadding = 0.dp,
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(defaultNodejsCompat = enabled) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "workers.dev Subdomain",
        description = "Expose new Workers on your workers.dev subdomain by default",
        checked = settings.defaultWorkersDevEnabled,
        horizontalPadding = 0.dp,
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(defaultWorkersDevEnabled = enabled) } },
      )
      SettingsDivider()
      SettingsToggleRow(
        title = "Auto-Open Preview",
        description = "Launch the deployed endpoint preview automatically after deployment",
        checked = settings.autoOpenPreviewOnDeploy,
        horizontalPadding = 0.dp,
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(autoOpenPreviewOnDeploy = enabled) } },
      )
    }
  }
}

@Composable
fun SettingsStorageAndSessionPane(
  uiState: IdeUiState,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onClearApiCache: (() -> Unit)?,
  onOpenGitHubHub: (() -> Unit)?,
  onRequestStorageAccess: (() -> Unit)?,
  onRequestDisconnect: () -> Unit,
  onDirectAuthSetup: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val clipboard = LocalClipboardManager.current
  val storage = uiState.projectStorageSummary
  val creds = uiState.credentials
  val settings = uiState.editorSettings
  val resolvedPath =
    storage.rootAbsolutePath.ifBlank {
      storage.rootDisplayPath.ifBlank { "EdgeCodeProjects/" }
    }

  Column(
    modifier = modifier.fillMaxWidth().testTag("settings_storage_section"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SettingsCard(title = "Directory") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = if (storage.hasNativeStorageAccess) "Native external directory" else "Scoped app directory",
          style = MaterialTheme.typography.bodySmall,
          color = scheme.onSurfaceVariant,
        )
        Text(
          text = if (storage.hasNativeStorageAccess) "NATIVE" else "SCOPED",
          fontFamily = JetBrainsMono,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          color = scheme.primary,
        )
      }

      Surface(
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Ca.radius.control),
        border = BorderStroke(0.75.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = resolvedPath,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = scheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = {
              clipboard.setText(AnnotatedString(resolvedPath))
              onNotify("Copied path")
            },
            modifier = Modifier.size(34.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.ContentCopy,
              contentDescription = "Copy folder path",
              tint = scheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      if (!storage.hasNativeStorageAccess && onRequestStorageAccess != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Folder Access", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
          IconRevealButton(
            icon = Icons.Outlined.Folder,
            revealedLabel = "Grant Folder Access",
            accentContainer = true,
            onClick = onRequestStorageAccess,
            height = 38.dp,
            tag = "settings_grant_storage_button",
          )
        }
      }

      SettingsFoldableSection(
        title = "Storage Stats",
        icon = Icons.Outlined.Storage,
        summary = "${storage.totalDiskKb} KB",
        initiallyExpanded = false,
        contentPadding = 12.dp,
      ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SettingsMetricTile(label = "Projects", value = storage.projectCount.toString(), modifier = Modifier.weight(1f))
          SettingsMetricTile(label = "Imports", value = storage.importedFilesCount.toString(), modifier = Modifier.weight(1f))
          SettingsMetricTile(label = "Exports", value = storage.exportedFilesCount.toString(), modifier = Modifier.weight(1f))
          SettingsMetricTile(label = "Disk", value = "${storage.totalDiskKb} KB", modifier = Modifier.weight(1f))
        }
      }
    }

    SettingsCard(
      title = "API Sync & Cache",
      modifier = Modifier.testTag("settings_api_cache_section"),
    ) {
      SettingsChoiceRow(
        title = "Auto-refresh interval",
        description = "How long API responses remain cached locally",
        selected = settings.apiCacheTtlHours.toString(),
        options =
          listOf(
            "1" to "1h",
            "2" to "2h",
            "4" to "4h",
            "6" to "6h",
            "12" to "12h",
            "0" to "Manual",
          ),
        chipTestTagPrefix = "settings_api_cache_ttl",
        onSelect = { hoursStr ->
          val hours = hoursStr.toIntOrNull() ?: 4
          onUpdateSettings { it.copy(apiCacheTtlHours = hours) }
        },
      )

      if (onClearApiCache != null) {
        SettingsDivider()
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Cached API Data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = scheme.onSurface)
            Text("Purge cached Cloudflare dashboards & schemas", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
          }
          IconRevealButton(
            icon = Icons.Outlined.DeleteSweep,
            revealedLabel = "Clear Cache",
            onClick = onClearApiCache,
            height = 36.dp,
            tag = "settings_clear_api_cache_button",
          )
        }
      }
    }

    SettingsCard(title = "Sync & Session") {
      if (onOpenGitHubHub != null) {
        val gitHubLogin = uiState.gitHubCreds?.login
        SettingsMenuRow(
          title = "GitHub Sync",
          icon = Icons.Outlined.AccountTree,
          value = gitHubLogin?.let { "@$it" } ?: "Off",
          statusDotColor = if (gitHubLogin != null) EmeraldLive else null,
          onClick = onOpenGitHubHub,
        )
        SettingsDivider()
      }

      if (creds != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Session", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = RoseError)
          IconRevealButton(
            icon = Icons.AutoMirrored.Outlined.Logout,
            revealedLabel = "Disconnect Cloudflare",
            statusDotColor = RoseError,
            onClick = onRequestDisconnect,
            height = 38.dp,
            tag = "sign_out_button",
          )
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Session", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = scheme.onSurface)
          IconRevealButton(
            icon = Icons.AutoMirrored.Outlined.Logout,
            revealedLabel = "Auth Setup",
            onClick = onDirectAuthSetup,
            height = 38.dp,
            tag = "sign_out_button",
          )
        }
      }
    }
  }
}
