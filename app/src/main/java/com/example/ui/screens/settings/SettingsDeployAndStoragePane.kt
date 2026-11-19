package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.viewmodel.IdeUiState

@Composable
fun SettingsDeployPane(
  settings: EditorSettings,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  var compatDateDraft by rememberSaveable(settings.defaultCompatibilityDate) {
    mutableStateOf(settings.defaultCompatibilityDate)
  }

  val enabledDefaults =
    listOf(
      settings.defaultNodejsCompat,
      settings.defaultWorkersDevEnabled,
      settings.autoOpenPreviewOnDeploy,
    ).count { it }

  Column(
    modifier = modifier.fillMaxWidth().testTag("settings_deploy_defaults_section"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SettingsFoldableSection(
      title = "Compatibility Date",
      icon = Icons.Outlined.Schedule,
      summary = settings.defaultCompatibilityDate,
      initiallyExpanded = false,
      contentPadding = 14.dp,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            shape = RoundedCornerShape(10.dp),
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
            colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(50.dp),
          ) {
            Text("Save", fontWeight = FontWeight.SemiBold)
          }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("2024-11-01", "2025-05-01", "2026-01-01").forEach { preset ->
            SettingsChoiceChip(
              label = preset,
              selected = settings.defaultCompatibilityDate == preset,
              onClick = {
                compatDateDraft = preset
                onUpdateSettings { it.copy(defaultCompatibilityDate = preset) }
              },
              monospace = true,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }

    SettingsFoldableSection(
      title = "Defaults",
      icon = Icons.Outlined.Tune,
      summary = "$enabledDefaults/3",
      initiallyExpanded = false,
    ) {
      SettingsToggleRow(
        title = "nodejs_compat",
        leadingIcon = Icons.Outlined.Memory,
        checked = settings.defaultNodejsCompat,
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(defaultNodejsCompat = enabled) } },
      )
      SettingsHairlineDivider(startIndent = 54.dp)
      SettingsToggleRow(
        title = "workers.dev Subdomain",
        leadingIcon = Icons.Outlined.Language,
        checked = settings.defaultWorkersDevEnabled,
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(defaultWorkersDevEnabled = enabled) } },
      )
      SettingsHairlineDivider(startIndent = 54.dp)
      SettingsToggleRow(
        title = "Auto-Open Preview",
        leadingIcon = Icons.Outlined.OpenInBrowser,
        checked = settings.autoOpenPreviewOnDeploy,
        onCheckedChange = { enabled -> onUpdateSettings { it.copy(autoOpenPreviewOnDeploy = enabled) } },
      )
    }
  }
}

@Composable
fun SettingsStorageAndSessionPane(
  uiState: IdeUiState,
  onOpenGitHubHub: (() -> Unit)?,
  onRequestStorageAccess: (() -> Unit)?,
  onRequestDisconnect: () -> Unit,
  onDirectAuthSetup: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val clipboard = LocalClipboardManager.current
  val storage = uiState.projectStorageSummary
  val creds = uiState.credentials
  val resolvedPath =
    storage.rootAbsolutePath.ifBlank {
      storage.rootDisplayPath.ifBlank { "EdgeCodeProjects/" }
    }

  Column(
    modifier = modifier.fillMaxWidth().testTag("settings_storage_section"),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SettingsGroup(
      label = "Directory",
      trailingValue = if (storage.hasNativeStorageAccess) "NATIVE" else "SCOPED",
      contentPadding = 14.dp,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
          color = palette.editorBg,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
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
              color = palette.activePillFg,
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
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }

        if (!storage.hasNativeStorageAccess && onRequestStorageAccess != null) {
          Button(
            onClick = onRequestStorageAccess,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("settings_grant_storage_button"),
          ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Grant Folder Access", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        SettingsFoldableSection(
          title = "Storage Stats",
          icon = Icons.Outlined.Storage,
          summary = "${storage.totalDiskKb} KB",
          initiallyExpanded = false,
          contentPadding = 10.dp,
        ) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsMetricTile(label = "Projects", value = storage.projectCount.toString(), modifier = Modifier.weight(1f))
            SettingsMetricTile(label = "Imports", value = storage.importedFilesCount.toString(), modifier = Modifier.weight(1f))
            SettingsMetricTile(label = "Exports", value = storage.exportedFilesCount.toString(), modifier = Modifier.weight(1f))
            SettingsMetricTile(label = "Disk", value = "${storage.totalDiskKb} KB", modifier = Modifier.weight(1f))
          }
        }
      }
    }

    SettingsGroup(label = "Sync & Session") {
      if (onOpenGitHubHub != null) {
        val gitHubLogin = uiState.gitHubCreds?.login
        SettingsMenuRow(
          title = "GitHub Sync",
          icon = Icons.Outlined.AccountTree,
          value = gitHubLogin?.let { "@$it" } ?: "Off",
          statusDotColor = if (gitHubLogin != null) EmeraldLive else null,
          onClick = onOpenGitHubHub,
        )
        SettingsHairlineDivider()
      }

      if (creds != null) {
        OutlinedButton(
          onClick = onRequestDisconnect,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().padding(12.dp).height(42.dp).testTag("sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(8.dp))
          Text("Disconnect Cloudflare", fontWeight = FontWeight.Medium)
        }
      } else {
        OutlinedButton(
          onClick = onDirectAuthSetup,
          border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.7f)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().padding(12.dp).height(42.dp).testTag("sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(8.dp))
          Text("Auth Setup", fontWeight = FontWeight.Medium)
        }
      }
    }
  }
}
