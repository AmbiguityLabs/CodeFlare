package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.CfAccount
import com.example.data.security.AuthMethod
import com.example.data.security.EditorSettings
import com.example.ui.components.AppearanceHubContent
import com.example.ui.components.EditorAppearanceStudioContent
import com.example.ui.components.HomeAppearanceStudioContent
import com.example.ui.components.IconRevealButton
import com.example.ui.screens.settings.AppearanceSubPage
import com.example.ui.screens.settings.SettingsAnalysisPane
import com.example.ui.screens.settings.SettingsCategory
import com.example.ui.screens.settings.SettingsCloudflarePane
import com.example.ui.screens.settings.SettingsCompletionPane
import com.example.ui.screens.settings.SettingsDeployPane
import com.example.ui.screens.settings.SettingsEditorPane
import com.example.ui.screens.settings.SettingsHubRow
import com.example.ui.screens.settings.SettingsHubSearchField
import com.example.ui.screens.settings.SettingsHubSectionHeader
import com.example.ui.screens.settings.SettingsHubSidebarItem
import com.example.ui.screens.settings.SettingsStorageAndSessionPane
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CaMotion
import com.example.ui.theme.CodeStylePreset
import com.example.ui.theme.EmeraldLive
import com.example.ui.viewmodel.IdeUiState

private data class SettingsSectionGroup(
  val title: String,
  val items: List<SettingsCategory>,
)

private val SETTINGS_GROUPS =
  listOf(
    SettingsSectionGroup("Preferences", listOf(SettingsCategory.APPEARANCE, SettingsCategory.EDITOR)),
    SettingsSectionGroup("Code", listOf(SettingsCategory.COMPLETION, SettingsCategory.ANALYSIS)),
    SettingsSectionGroup("Cloudflare", listOf(SettingsCategory.CLOUDFLARE, SettingsCategory.DEPLOY)),
    SettingsSectionGroup("System", listOf(SettingsCategory.STORAGE)),
  )

private fun SettingsCategory.matchesQuery(q: String): Boolean {
  val trimmed = q.trim()
  if (trimmed.isEmpty()) return true
  return title.contains(trimmed, ignoreCase = true) ||
    subtitle.contains(trimmed, ignoreCase = true) ||
    keywords.any { it.contains(trimmed, ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
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
  val scheme = MaterialTheme.colorScheme
  val settings = uiState.editorSettings
  val creds = uiState.credentials

  var openCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
  var wideSelectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.APPEARANCE) }
  var appearanceSubPage by rememberSaveable { mutableStateOf<AppearanceSubPage?>(null) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
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
          colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
        ) {
          Text("Disconnect")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") }
      },
    )
  }

  val statusLabel =
    when {
      creds != null -> "Connected"
      uiState.isGitHubIdeOnlyMode -> "GitHub"
      else -> "Local"
    }
  val statusColor = if (creds != null) EmeraldLive else AmberWarn

  val statusButton: @Composable () -> Unit = {
    IconRevealButton(
      icon = if (creds != null) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
      revealedLabel = statusLabel,
      statusDotColor = statusColor,
      onClick = { onNotify(statusLabel) },
      tag = "settings_status_icon_button",
    )
  }

  BoxWithConstraints(
    modifier =
      Modifier
        .fillMaxSize()
        .background(scheme.background)
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

    val detailPane: @Composable (SettingsCategory, AppearanceSubPage?, PaddingValues) -> Unit =
      { category, subPage, innerPadding ->
        val paneScroll = rememberScrollState()
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.TopCenter,
        ) {
          Column(
            modifier =
              Modifier
                .widthIn(max = 660.dp)
                .fillMaxWidth()
                .verticalScroll(paneScroll)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            when (category) {
              SettingsCategory.APPEARANCE -> {
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

              SettingsCategory.COMPLETION -> {
                SettingsCompletionPane(
                  settings = settings,
                  onUpdateSettings = onUpdateSettings,
                )
              }

              SettingsCategory.ANALYSIS -> {
                SettingsAnalysisPane(
                  settings = settings,
                  onUpdateSettings = onUpdateSettings,
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
            Spacer(Modifier.height(16.dp))
          }
        }
      }

    val filteredGroups =
      remember(searchQuery) {
        SETTINGS_GROUPS.mapNotNull { group ->
          val matching = group.items.filter { it.matchesQuery(searchQuery) }
          if (matching.isEmpty()) null else group.copy(items = matching)
        }
      }

    if (isWide) {
      Row(
        modifier =
          Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
      ) {
        Surface(
          color = scheme.surfaceContainerLow,
          modifier = Modifier.width(280.dp).fillMaxHeight(),
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                  contentDescription = "Back",
                )
              }
              Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
              )
              statusButton()
            }

            SettingsHubSearchField(
              query = searchQuery,
              onQuery = { searchQuery = it },
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Column(
              modifier =
                Modifier
                  .weight(1f)
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 12.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              if (filteredGroups.isEmpty()) {
                Text(
                  text = "No settings match \"$searchQuery\"",
                  style = MaterialTheme.typography.bodyMedium,
                  color = scheme.outline,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
                )
              } else {
                filteredGroups.forEachIndexed { idx, group ->
                  if (idx > 0) Spacer(Modifier.height(12.dp))
                  SettingsHubSectionHeader(
                    title = group.title,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                  )
                  Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                  ) {
                    group.items.forEach { category ->
                      SettingsHubSidebarItem(
                        title = category.title,
                        subtitle = categoryCompactValue(category, uiState),
                        icon = category.icon,
                        statusDotColor =
                          if (category == SettingsCategory.CLOUDFLARE) statusColor else null,
                        selected = wideSelectedCategory == category,
                        tag = "settings_jump_${category.jumpTag}",
                        onClick = {
                          appearanceSubPage = null
                          wideSelectedCategory = category
                        },
                      )
                    }
                  }
                }
              }
            }
          }
        }

        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(scheme.outlineVariant))

        val activeWideSub =
          if (wideSelectedCategory == SettingsCategory.APPEARANCE) appearanceSubPage else null
        val wideTitle =
          if (activeWideSub != null) "${wideSelectedCategory.title} · ${activeWideSub.title}"
          else wideSelectedCategory.title

        Scaffold(
          containerColor = scheme.background,
          modifier = Modifier.weight(1f).fillMaxHeight(),
          topBar = {
            TopAppBar(
              title = {
                Text(
                  text = wideTitle,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              navigationIcon = {
                if (activeWideSub != null) {
                  IconButton(
                    onClick = { appearanceSubPage = null },
                    modifier = Modifier.testTag("settings_appearance_subpage_back"),
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                      contentDescription = "Back to Appearance",
                    )
                  }
                }
              },
              actions = {
                Box(Modifier.padding(end = 10.dp)) { statusButton() }
              },
              colors =
                TopAppBarDefaults.topAppBarColors(
                  containerColor = scheme.background,
                  scrolledContainerColor = scheme.surfaceContainer,
                ),
            )
          },
        ) { innerPadding ->
          Crossfade(
            targetState = wideSelectedCategory to activeWideSub,
            animationSpec = tween(CaMotion.FAST),
            modifier = Modifier.fillMaxSize(),
            label = "settings_wide_detail",
          ) { (category, subPage) ->
            detailPane(category, subPage, innerPadding)
          }
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
        targetState = openCategory to (if (openCategory == SettingsCategory.APPEARANCE) appearanceSubPage else null),
        transitionSpec = {
          if (navDepth(targetState) > navDepth(initialState)) {
            (slideInHorizontally(tween(190)) { it / 5 } + fadeIn(tween(160))) togetherWith
              (slideOutHorizontally(tween(150)) { -it / 8 } + fadeOut(tween(120)))
          } else {
            (slideInHorizontally(tween(190)) { -it / 5 } + fadeIn(tween(160))) togetherWith
              (slideOutHorizontally(tween(150)) { it / 5 } + fadeOut(tween(120)))
          }
        },
        modifier = Modifier.fillMaxSize(),
        label = "settings_compact_nav",
      ) { (category, subPage) ->
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val headerTitle =
          when {
            category == SettingsCategory.APPEARANCE && subPage != null -> subPage.headerTitle
            category != null -> category.title
            else -> "Settings"
          }
        Scaffold(
          containerColor = scheme.background,
          modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
          topBar = {
            LargeTopAppBar(
              title = {
                Text(
                  text = headerTitle,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              navigationIcon = {
                IconButton(
                  onClick = handleBack,
                  modifier = Modifier.testTag("settings_back_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                  )
                }
              },
              actions = {
                Box(Modifier.padding(end = 10.dp)) { statusButton() }
              },
              colors =
                TopAppBarDefaults.topAppBarColors(
                  containerColor = scheme.background,
                  scrolledContainerColor = scheme.surfaceContainer,
                ),
              scrollBehavior = scrollBehavior,
            )
          },
        ) { innerPadding ->
          if (category == null) {
            Box(
              modifier = Modifier.fillMaxSize().padding(innerPadding),
              contentAlignment = Alignment.TopCenter,
            ) {
              Column(
                modifier =
                  Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
              ) {
                SettingsHubSearchField(
                  query = searchQuery,
                  onQuery = { searchQuery = it },
                  modifier = Modifier.fillMaxWidth(),
                )
                if (filteredGroups.isEmpty()) {
                  Text(
                    text = "No settings match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                  )
                } else {
                  filteredGroups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      SettingsHubSectionHeader(
                        title = group.title,
                        modifier = Modifier.padding(start = 16.dp),
                      )
                      Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                          group.items.forEachIndexed { itemIdx, cat ->
                            if (itemIdx > 0) {
                              HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = scheme.outlineVariant.copy(alpha = 0.5f),
                              )
                            }
                            SettingsHubRow(
                              title = cat.title,
                              subtitle = "${cat.subtitle} · ${categoryCompactValue(cat, uiState)}",
                              icon = cat.icon,
                              statusDotColor =
                                if (cat == SettingsCategory.CLOUDFLARE) statusColor else null,
                              tag = "settings_jump_${cat.jumpTag}",
                              onClick = {
                                appearanceSubPage = null
                                openCategory = cat
                              },
                            )
                          }
                        }
                      }
                    }
                  }
                }
                Spacer(Modifier.height(16.dp))
              }
            }
          } else {
            detailPane(category, subPage, innerPadding)
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
      val modeLabel =
        when (settings.normalizedThemeMode()) {
          EditorSettings.THEME_LIGHT -> "Light"
          EditorSettings.THEME_SYSTEM -> "System"
          else -> "Dark"
        }
      val codeStyle = CodeStylePreset.fromKey(settings.codeStylePreset)
      if (codeStyle != CodeStylePreset.THEME_DEFAULT) {
        "$modeLabel · ${codeStyle.label}"
      } else {
        val accentLabel =
          settings.normalizedAccent().replaceFirstChar { it.uppercase() }
        "$modeLabel · $accentLabel"
      }
    }
    SettingsCategory.EDITOR -> "${settings.fontSizeSp}sp · ${settings.tabSize}sp"
    SettingsCategory.COMPLETION ->
      if (settings.autoPopupCompletion) "${settings.completionDelayMs}ms · max ${settings.completionMaxItems}"
      else "Manual"
    SettingsCategory.ANALYSIS ->
      if (settings.analyzeOnTheFly) "Live · ${settings.reparseDelayMs}ms"
      else "Manual"
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
