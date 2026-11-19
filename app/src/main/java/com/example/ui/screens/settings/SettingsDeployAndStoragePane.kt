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
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
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
import com.example.ui.components.IconRevealButton
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.UiStyleVersion
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
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onClearApiCache: (() -> Unit)?,
  onOpenGitHubHub: (() -> Unit)?,
  onRequestStorageAccess: (() -> Unit)?,
  onRequestDisconnect: () -> Unit,
  onDirectAuthSetup: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
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
          shape = RoundedCornerShape(if (isV2) 16.dp else 10.dp),
          border = if (isV2) null else BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
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
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Folder Access", fontSize = 14.sp, color = palette.textPrimary)
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

    val cacheSummary =
      if (settings.apiCacheTtlHours <= 0) "Manual" else "${settings.apiCacheTtlHours}h"
    SettingsFoldableSection(
      title = "API Sync & Cache",
      icon = Icons.Outlined.Cached,
      summary = cacheSummary,
      initiallyExpanded = false,
      contentPadding = 14.dp,
      modifier = Modifier.testTag("settings_api_cache_section"),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Auto-Refresh Interval",
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          color = palette.textPrimary,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(1 to "1h", 2 to "2h", 4 to "4h").forEach { (hours, label) ->
            SettingsChoiceChip(
              label = label,
              selected = settings.apiCacheTtlHours == hours,
              onClick = { onUpdateSettings { it.copy(apiCacheTtlHours = hours) } },
              monospace = true,
              modifier = Modifier.weight(1f).testTag("settings_api_cache_ttl_$hours"),
            )
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(6 to "6h", 12 to "12h", 0 to "Manual").forEach { (hours, label) ->
            SettingsChoiceChip(
              label = label,
              selected = settings.apiCacheTtlHours == hours,
              onClick = { onUpdateSettings { it.copy(apiCacheTtlHours = hours) } },
              monospace = hours > 0,
              modifier = Modifier.weight(1f).testTag("settings_api_cache_ttl_$hours"),
            )
          }
        }
        if (onClearApiCache != null) {
          SettingsHairlineDivider()
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Cached API Data", fontSize = 14.sp, color = palette.textPrimary)
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
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Session", fontSize = 14.5.sp, color = RoseError)
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
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Session", fontSize = 14.5.sp, color = palette.textPrimary)
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
