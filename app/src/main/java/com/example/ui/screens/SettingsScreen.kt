package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.CfAccount
import com.example.data.security.AuthMethod
import com.example.data.security.EditorSettings
import com.example.ui.components.AppearanceHubContent
import com.example.ui.components.EditorAppearanceStudioContent
import com.example.ui.components.HomeAppearanceStudioContent
import com.example.ui.components.IconRevealButton
import com.example.ui.screens.settings.AppearanceSubPage
import com.example.ui.screens.settings.SettingsCategory
import com.example.ui.screens.settings.SettingsCloudflarePane
import com.example.ui.screens.settings.SettingsDeployPane
import com.example.ui.screens.settings.SettingsEditorPane
import com.example.ui.screens.settings.SettingsGroup
import com.example.ui.screens.settings.SettingsHairlineDivider
import com.example.ui.screens.settings.SettingsMenuRow
import com.example.ui.screens.settings.SettingsStorageAndSessionPane
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.UiStyleVersion
import com.example.ui.viewmodel.IdeUiState

@Composable
fun SettingsScreen(
  uiState: IdeUiState,
  onBack: () -> Unit,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onSelectAccount: (CfAccount) -> Unit,
  onVerifyCredentials: () -> Unit,
  onUpdateApiToken: (String) -> Unit,
  onUpdateGlobalKey: (String, String) -> Unit,
  onStartOAuth: () -> Unit,
  onCancelOAuth: () -> Unit,
  onClearCredentialFeedback: () -> Unit,
  onDisconnectCloudflare: () -> Unit,
  onOpenGitHubHub: (() -> Unit)? = null,
  onClearAutoSavedDrafts: (() -> Unit)? = null,
  onClearApiCache: (() -> Unit)? = null,
  onRequestStorageAccess: (() -> Unit)? = null,
  onPickEditorBackgroundImage: (Uri) -> Unit = {},
  onClearEditorBackgroundImage: () -> Unit = {},
  onNotify: (String) -> Unit,
) {
  val palette = LocalIdePalette.current
  val settings = uiState.editorSettings
  val creds = uiState.credentials
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED

  var openCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
  var wideSelectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.APPEARANCE) }
  var appearanceSubPage by rememberSaveable { mutableStateOf<AppearanceSubPage?>(null) }
  var confirmDisconnect by remember { mutableStateOf(false) }

  if (confirmDisconnect) {
    AlertDialog(
      onDismissRequest = { confirmDisconnect = false },
      title = { Text("Disconnect Cloudflare?") },
      text = { Text("Clear saved Cloudflare credentials?") },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDisconnect = false
            onDisconnectCloudflare()
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Disconnect")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") }
      },
    )
  }

  BoxWithConstraints(
    modifier =
      Modifier
        .fillMaxSize()
        .background(palette.chromeBg)
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding(),
  ) {
    val isWide = maxWidth >= 720.dp
    val activeCategory = if (isWide) wideSelectedCategory else openCategory
    val handleBack: () -> Unit = {
      when {
        activeCategory == SettingsCategory.APPEARANCE && appearanceSubPage != null -> {
          appearanceSubPage = null
        }
        !isWide && openCategory != null -> {
          openCategory = null
        }
        else -> onBack()
      }
    }
    BackHandler(onBack = handleBack)

    val detailPane: @Composable (SettingsCategory, AppearanceSubPage?) -> Unit = { category, subPage ->
      val paneScroll = rememberScrollState()
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
          modifier =
            Modifier
              .widthIn(max = if (isWide) 660.dp else 620.dp)
              .fillMaxWidth()
              .verticalScroll(paneScroll)
              .padding(horizontal = if (isWide) 22.dp else 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          when (category) {
            SettingsCategory.APPEARANCE -> {
              if (isWide && subPage != null) {
                Surface(
                  color = palette.chromeSurface,
                  shape = RoundedCornerShape(if (isV2) 18.dp else 10.dp),
                  border = if (isV2) null else BorderStroke(0.75.dp, palette.border.copy(alpha = 0.6f)),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { appearanceSubPage = null }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("settings_appearance_subpage_back"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                      contentDescription = "Back to Appearance",
                      tint = palette.activePillFg,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = "Appearance",
                      fontSize = 13.5.sp,
                      color = palette.textSecondary,
                    )
                    Text(
                      text = "/",
                      fontSize = 13.5.sp,
                      color = palette.textMuted,
                    )
                    Text(
                      text = subPage.title,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = palette.textPrimary,
                    )
                  }
                }
              }

              when (subPage) {
                null -> {
                  Box(modifier = Modifier.fillMaxWidth().testTag("settings_appearance_section")) {
                    AppearanceHubContent(
                      settings = settings,
                      onUpdateSettings = onUpdateSettings,
                      onOpenSubPage = { nextSub -> appearanceSubPage = nextSub },
                    )
                  }
                }

                AppearanceSubPage.HOME_PAGE -> {
                  Box(modifier = Modifier.fillMaxWidth().testTag("settings_appearance_home_section")) {
                    HomeAppearanceStudioContent(
                      settings = settings,
                      onUpdateSettings = onUpdateSettings,
                    )
                  }
                }

                AppearanceSubPage.EDITOR -> {
                  Box(modifier = Modifier.fillMaxWidth().testTag("settings_appearance_editor_section")) {
                    EditorAppearanceStudioContent(
                      settings = settings,
                      onUpdateSettings = onUpdateSettings,
                      onPickEditorBackgroundImage = onPickEditorBackgroundImage,
                      onClearEditorBackgroundImage = onClearEditorBackgroundImage,
                    )
                  }
                }
              }
            }

            SettingsCategory.EDITOR -> {
              SettingsEditorPane(
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onClearAutoSavedDrafts = onClearAutoSavedDrafts,
              )
            }

            SettingsCategory.CLOUDFLARE -> {
              SettingsCloudflarePane(
                uiState = uiState,
                onSelectAccount = onSelectAccount,
                onVerifyCredentials = onVerifyCredentials,
                onUpdateApiToken = onUpdateApiToken,
                onUpdateGlobalKey = onUpdateGlobalKey,
                onStartOAuth = onStartOAuth,
                onCancelOAuth = onCancelOAuth,
                onClearCredentialFeedback = onClearCredentialFeedback,
                onNotify = onNotify,
              )
            }

            SettingsCategory.DEPLOY -> {
              SettingsDeployPane(
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onNotify = onNotify,
              )
            }

            SettingsCategory.STORAGE -> {
              SettingsStorageAndSessionPane(
                uiState = uiState,
                onUpdateSettings = onUpdateSettings,
                onClearApiCache = onClearApiCache,
                onOpenGitHubHub = onOpenGitHubHub,
                onRequestStorageAccess = onRequestStorageAccess,
                onRequestDisconnect = { confirmDisconnect = true },
                onDirectAuthSetup = onDisconnectCloudflare,
                onNotify = onNotify,
              )
            }
          }
          Spacer(Modifier.height(12.dp))
        }
      }
    }

    val linearMenuContent: @Composable (Boolean) -> Unit = { railMode ->
      val activeSelection = if (railMode) wideSelectedCategory else null
      val select: (SettingsCategory) -> Unit = { cat ->
        appearanceSubPage = null
        if (railMode) wideSelectedCategory = cat else openCategory = cat
      }
      val dividerIndent = if (isV2) 18.dp else 56.dp
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (railMode) 12.dp else 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (isV2) 18.dp else 14.dp),
      ) {
        SettingsGroup(label = "Preferences") {
          SettingsMenuRow(
            title = SettingsCategory.APPEARANCE.title,
            icon = SettingsCategory.APPEARANCE.icon,
            value = if (isV2) null else categoryCompactValue(SettingsCategory.APPEARANCE, uiState),
            selected = activeSelection == SettingsCategory.APPEARANCE,
            showChevron = !railMode,
            tag = "settings_jump_${SettingsCategory.APPEARANCE.jumpTag}",
            onClick = { select(SettingsCategory.APPEARANCE) },
          )
          SettingsHairlineDivider(startIndent = dividerIndent)
          SettingsMenuRow(
            title = SettingsCategory.EDITOR.title,
            icon = SettingsCategory.EDITOR.icon,
            value = if (isV2) null else categoryCompactValue(SettingsCategory.EDITOR, uiState),
            selected = activeSelection == SettingsCategory.EDITOR,
            showChevron = !railMode,
            tag = "settings_jump_${SettingsCategory.EDITOR.jumpTag}",
            onClick = { select(SettingsCategory.EDITOR) },
          )
        }

        SettingsGroup(label = "Cloudflare") {
          SettingsMenuRow(
            title = SettingsCategory.CLOUDFLARE.title,
            icon = SettingsCategory.CLOUDFLARE.icon,
            value = if (isV2) null else categoryCompactValue(SettingsCategory.CLOUDFLARE, uiState),
            statusDotColor = if (creds != null) EmeraldLive else AmberWarn,
            selected = activeSelection == SettingsCategory.CLOUDFLARE,
            showChevron = !railMode,
            tag = "settings_jump_${SettingsCategory.CLOUDFLARE.jumpTag}",
            onClick = { select(SettingsCategory.CLOUDFLARE) },
          )
          SettingsHairlineDivider(startIndent = dividerIndent)
          SettingsMenuRow(
            title = SettingsCategory.DEPLOY.title,
            icon = SettingsCategory.DEPLOY.icon,
            value = if (isV2) null else categoryCompactValue(SettingsCategory.DEPLOY, uiState),
            selected = activeSelection == SettingsCategory.DEPLOY,
            showChevron = !railMode,
            tag = "settings_jump_${SettingsCategory.DEPLOY.jumpTag}",
            onClick = { select(SettingsCategory.DEPLOY) },
          )
        }

        SettingsGroup(label = "System") {
          SettingsMenuRow(
            title = SettingsCategory.STORAGE.title,
            icon = SettingsCategory.STORAGE.icon,
            value = if (isV2) null else categoryCompactValue(SettingsCategory.STORAGE, uiState),
            selected = activeSelection == SettingsCategory.STORAGE,
            showChevron = !railMode,
            tag = "settings_jump_${SettingsCategory.STORAGE.jumpTag}",
            onClick = { select(SettingsCategory.STORAGE) },
          )
        }
      }
    }

    val headerTitle =
      when {
        isWide -> "Settings"
        openCategory == SettingsCategory.APPEARANCE && appearanceSubPage != null -> appearanceSubPage!!.headerTitle
        openCategory != null -> openCategory!!.title
        else -> "Settings"
      }

    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(palette.chromeBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (isV2) {
          IconButton(
            onClick = handleBack,
            modifier = Modifier.size(48.dp).testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = palette.textPrimary,
              modifier = Modifier.size(21.dp),
            )
          }
        } else {
          Surface(
            onClick = handleBack,
            shape = CircleShape,
            color = palette.chromeSurface,
            border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.7f)),
            modifier = Modifier.size(42.dp).testTag("settings_back_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = palette.textPrimary,
                modifier = Modifier.size(19.dp),
              )
            }
          }
        }

        Text(
          text = headerTitle,
          fontSize = if (isV2) 19.sp else 18.sp,
          fontWeight = FontWeight.Medium,
          color = palette.textPrimary,
          textAlign = if (isV2) TextAlign.Center else TextAlign.Start,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )

        val statusLabel =
          when {
            creds != null -> "Connected"
            uiState.isGitHubIdeOnlyMode -> "GitHub"
            else -> "Local"
          }
        val statusColor = if (creds != null) EmeraldLive else AmberWarn
        IconRevealButton(
          icon = if (creds != null) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
          revealedLabel = statusLabel,
          statusDotColor = statusColor,
          onClick = { onNotify(statusLabel) },
          tag = "settings_status_icon_button",
        )
      }

      if (!isV2) {
        HorizontalDivider(color = palette.border.copy(alpha = 0.45f), thickness = 0.75.dp)
      }

      if (isWide) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
          Box(
            modifier =
              Modifier
                .width(276.dp)
                .fillMaxHeight()
                .background(palette.chromeBg),
          ) {
            linearMenuContent(true)
          }
          VerticalDivider(color = palette.border.copy(alpha = 0.45f), thickness = 0.75.dp)
          AnimatedContent(
            targetState = wideSelectedCategory to if (wideSelectedCategory == SettingsCategory.APPEARANCE) appearanceSubPage else null,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(110)) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            label = "settings_wide_detail",
          ) { (category, subPage) ->
            detailPane(category, subPage)
          }
        }
      } else {
        val navDepth: (Pair<SettingsCategory?, AppearanceSubPage?>) -> Int = { (cat, sub) ->
          when {
            cat == null -> 0
            cat == SettingsCategory.APPEARANCE && sub != null -> 2
            else -> 1
          }
        }
        AnimatedContent(
          targetState = openCategory to if (openCategory == SettingsCategory.APPEARANCE) appearanceSubPage else null,
          transitionSpec = {
            if (navDepth(targetState) > navDepth(initialState)) {
              (slideInHorizontally(tween(190)) { it / 5 } + fadeIn(tween(160))) togetherWith
                (slideOutHorizontally(tween(150)) { -it / 8 } + fadeOut(tween(120)))
            } else {
              (slideInHorizontally(tween(190)) { -it / 5 } + fadeIn(tween(160))) togetherWith
                (slideOutHorizontally(tween(150)) { it / 5 } + fadeOut(tween(120)))
            }
          },
          modifier = Modifier.weight(1f).fillMaxWidth(),
          label = "settings_compact_nav",
        ) { (category, subPage) ->
          if (category == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
              Box(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
                linearMenuContent(false)
              }
            }
          } else {
            detailPane(category, subPage)
          }
        }
      }
    }
  }
}

private fun categoryCompactValue(
  category: SettingsCategory,
  uiState: IdeUiState,
): String {
  val settings = uiState.editorSettings
  return when (category) {
    SettingsCategory.APPEARANCE -> {
      val styleLabel = UiStyleVersion.fromKey(settings.uiStyleVersion).label
      val modeLabel = if (settings.darkTheme) "Dark" else "Light"
      "$styleLabel · $modeLabel"
    }
    SettingsCategory.EDITOR -> "${settings.fontSizeSp}sp · ${settings.tabSize}sp"
    SettingsCategory.CLOUDFLARE ->
      when (uiState.credentials?.method) {
        AuthMethod.API_TOKEN -> "Token"
        AuthMethod.GLOBAL_KEY -> "Global Key"
        AuthMethod.OAUTH_PKCE -> "OAuth"
        null -> "Unlinked"
      }
    SettingsCategory.DEPLOY -> settings.defaultCompatibilityDate
    SettingsCategory.STORAGE -> "${uiState.projectStorageSummary.totalDiskKb} KB"
  }
}
