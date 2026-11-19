package com.example.data.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.editor.selection.SmartSelectionInitialDepth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.edgeCodeDataStore by preferencesDataStore(name = "edgecode_secure_prefs")

enum class AuthMethod {
  OAUTH_PKCE,
  API_TOKEN,
  GLOBAL_KEY,
}

data class StoredCredentials(
  val method: AuthMethod,
  val accessToken: String,
  val refreshToken: String = "",
  val expiresAtEpochMs: Long = 0L,
  val email: String = "",
  val selectedAccountId: String = "",
  val selectedAccountName: String = "",
)

enum class GitHubAuthMethod {
  OAUTH_DEVICE,
  PAT,
}

data class StoredGitHubCredentials(
  val token: String,
  val login: String,
  val method: GitHubAuthMethod = GitHubAuthMethod.PAT,
  val scopes: String = "",
  val avatarUrl: String = "",
  val displayName: String = "",
)

data class FloatingBarStyleConfig(
  val heightDp: Int = 58,
  val widthPercent: Int = 82,
  val iconSpacingDp: Int = 18,
  val iconSizeDp: Int = 22,
  val opacity: Float = 0.78f,
  val borderOpacity: Float = 0.34f,
  val cornerRadiusDp: Int = 32,
  val bottomMarginDp: Int = 12,
  val activePillOpacity: Float = 0.24f,
  val showLabels: Boolean = false,
  val showBadges: Boolean = true,
) {
  fun normalized(): FloatingBarStyleConfig =
    copy(
      heightDp = heightDp.coerceIn(44, 78),
      widthPercent = widthPercent.coerceIn(52, 100),
      iconSpacingDp = iconSpacingDp.coerceIn(2, 40),
      iconSizeDp = iconSizeDp.coerceIn(16, 28),
      opacity = opacity.coerceIn(0.15f, 1.0f),
      borderOpacity = borderOpacity.coerceIn(0.0f, 0.75f),
      cornerRadiusDp = cornerRadiusDp.coerceIn(10, 40),
      bottomMarginDp = bottomMarginDp.coerceIn(0, 28),
      activePillOpacity = activePillOpacity.coerceIn(0.08f, 0.60f),
    )

  fun encode(): String {
    val n = normalized()
    return listOf(
      n.heightDp,
      n.widthPercent,
      n.iconSpacingDp,
      n.iconSizeDp,
      n.opacity,
      n.borderOpacity,
      n.cornerRadiusDp,
      n.bottomMarginDp,
      n.activePillOpacity,
      if (n.showLabels) 1 else 0,
      if (n.showBadges) 1 else 0,
    ).joinToString("|")
  }

  companion object {
    val HOME_DEFAULT =
      FloatingBarStyleConfig(
        heightDp = 58,
        widthPercent = 78,
        iconSpacingDp = 20,
        iconSizeDp = 22,
        opacity = 0.76f,
        borderOpacity = 0.34f,
        cornerRadiusDp = 32,
        bottomMarginDp = 12,
        activePillOpacity = 0.24f,
        showLabels = false,
        showBadges = true,
      )

    val EDITOR_DEFAULT =
      FloatingBarStyleConfig(
        heightDp = 56,
        widthPercent = 86,
        iconSpacingDp = 14,
        iconSizeDp = 21,
        opacity = 0.78f,
        borderOpacity = 0.34f,
        cornerRadiusDp = 32,
        bottomMarginDp = 10,
        activePillOpacity = 0.24f,
        showLabels = false,
        showBadges = true,
      )

    fun decode(raw: String?, fallback: FloatingBarStyleConfig): FloatingBarStyleConfig {
      if (raw.isNullOrBlank()) return fallback
      val parts = raw.split("|")
      if (parts.size < 11) return fallback
      return FloatingBarStyleConfig(
        heightDp = parts[0].toIntOrNull() ?: fallback.heightDp,
        widthPercent = parts[1].toIntOrNull() ?: fallback.widthPercent,
        iconSpacingDp = parts[2].toIntOrNull() ?: fallback.iconSpacingDp,
        iconSizeDp = parts[3].toIntOrNull() ?: fallback.iconSizeDp,
        opacity = parts[4].toFloatOrNull() ?: fallback.opacity,
        borderOpacity = parts[5].toFloatOrNull() ?: fallback.borderOpacity,
        cornerRadiusDp = parts[6].toIntOrNull() ?: fallback.cornerRadiusDp,
        bottomMarginDp = parts[7].toIntOrNull() ?: fallback.bottomMarginDp,
        activePillOpacity = parts[8].toFloatOrNull() ?: fallback.activePillOpacity,
        showLabels = parts[9] == "1",
        showBadges = parts[10] != "0",
      ).normalized()
    }
  }
}

data class PopupMenuStyleConfig(
  val widthDp: Int = 248,
  val itemHeightDp: Int = 40,
  val iconSpacingDp: Int = 10,
  val iconSizeDp: Int = 16,
  val opacity: Float = 0.94f,
  val borderOpacity: Float = 0.36f,
  val cornerRadiusDp: Int = 16,
) {
  fun normalized(): PopupMenuStyleConfig =
    copy(
      widthDp = widthDp.coerceIn(180, 320),
      itemHeightDp = itemHeightDp.coerceIn(32, 56),
      iconSpacingDp = iconSpacingDp.coerceIn(4, 20),
      iconSizeDp = iconSizeDp.coerceIn(13, 22),
      opacity = opacity.coerceIn(0.35f, 1.0f),
      borderOpacity = borderOpacity.coerceIn(0.0f, 0.75f),
      cornerRadiusDp = cornerRadiusDp.coerceIn(8, 28),
    )

  fun encode(): String {
    val n = normalized()
    return listOf(
      n.widthDp,
      n.itemHeightDp,
      n.iconSpacingDp,
      n.iconSizeDp,
      n.opacity,
      n.borderOpacity,
      n.cornerRadiusDp,
    ).joinToString("|")
  }

  companion object {
    val DEFAULT = PopupMenuStyleConfig()

    fun decode(raw: String?, fallback: PopupMenuStyleConfig = DEFAULT): PopupMenuStyleConfig {
      if (raw.isNullOrBlank()) return fallback
      val parts = raw.split("|")
      if (parts.size < 7) return fallback
      return PopupMenuStyleConfig(
        widthDp = parts[0].toIntOrNull() ?: fallback.widthDp,
        itemHeightDp = parts[1].toIntOrNull() ?: fallback.itemHeightDp,
        iconSpacingDp = parts[2].toIntOrNull() ?: fallback.iconSpacingDp,
        iconSizeDp = parts[3].toIntOrNull() ?: fallback.iconSizeDp,
        opacity = parts[4].toFloatOrNull() ?: fallback.opacity,
        borderOpacity = parts[5].toFloatOrNull() ?: fallback.borderOpacity,
        cornerRadiusDp = parts[6].toIntOrNull() ?: fallback.cornerRadiusDp,
      ).normalized()
    }
  }
}

enum class SmartSelectionHoldMode(val shortLabel: String) {
  SMART_HOLD_TOOLBAR("Smart Hold"),
  PROGRESSIVE_HOLD("Progressive"),
  CLASSIC_WORD("Classic Word"),
}

enum class DefaultEditorDragPolicy(val shortLabel: String) {
  SMART("Smart"),
  ALWAYS_COPY("Always Copy"),
  ALWAYS_MOVE("Always Move"),
}

data class SelectionDragConfig(
  val holdMode: SmartSelectionHoldMode = SmartSelectionHoldMode.SMART_HOLD_TOOLBAR,
  val initialDepth: SmartSelectionInitialDepth = SmartSelectionInitialDepth.SMART_AUTO,
  val defaultEditorDragMode: DefaultEditorDragPolicy = DefaultEditorDragPolicy.SMART,
  val immediateSlideToDrag: Boolean = true,
  val holdDelayMs: Int = 320,
  val allowCrossAppDrag: Boolean = true,
  val showScopeBadge: Boolean = true,
  val showExpandShrink: Boolean = true,
  val showDragGrip: Boolean = true,
  val showModeToggle: Boolean = true,
  val showDropTargetLoupe: Boolean = true,
  val showQuickActions: Boolean = true,
  val showCodeActions: Boolean = true,
) {
  fun normalized(): SelectionDragConfig =
    copy(
      holdDelayMs = holdDelayMs.coerceIn(200, 600),
    )

  fun encode(): String {
    val n = normalized()
    return listOf(
      n.holdMode.name,
      n.initialDepth.name,
      n.defaultEditorDragMode.name,
      if (n.immediateSlideToDrag) 1 else 0,
      n.holdDelayMs,
      if (n.allowCrossAppDrag) 1 else 0,
      if (n.showScopeBadge) 1 else 0,
      if (n.showExpandShrink) 1 else 0,
      if (n.showDragGrip) 1 else 0,
      if (n.showModeToggle) 1 else 0,
      if (n.showDropTargetLoupe) 1 else 0,
      if (n.showQuickActions) 1 else 0,
      if (n.showCodeActions) 1 else 0,
    ).joinToString("|")
  }

  val enabledOverlayCount: Int
    get() =
      listOf(
        showScopeBadge,
        showExpandShrink,
        showDragGrip,
        showModeToggle,
        showDropTargetLoupe,
        showQuickActions,
        showCodeActions,
      ).count { it }

  companion object {
    val DEFAULT = SelectionDragConfig()

    fun decode(raw: String?, fallback: SelectionDragConfig = DEFAULT): SelectionDragConfig {
      if (raw.isNullOrBlank()) return fallback
      val parts = raw.split("|")
      if (parts.size < 13) return fallback
      return SelectionDragConfig(
        holdMode = runCatching { SmartSelectionHoldMode.valueOf(parts[0]) }.getOrDefault(fallback.holdMode),
        initialDepth = runCatching { SmartSelectionInitialDepth.valueOf(parts[1]) }.getOrDefault(fallback.initialDepth),
        defaultEditorDragMode = runCatching { DefaultEditorDragPolicy.valueOf(parts[2]) }.getOrDefault(fallback.defaultEditorDragMode),
        immediateSlideToDrag = parts[3] != "0",
        holdDelayMs = parts[4].toIntOrNull() ?: fallback.holdDelayMs,
        allowCrossAppDrag = parts[5] != "0",
        showScopeBadge = parts[6] != "0",
        showExpandShrink = parts[7] != "0",
        showDragGrip = parts[8] != "0",
        showModeToggle = parts[9] != "0",
        showDropTargetLoupe = parts[10] != "0",
        showQuickActions = parts[11] != "0",
        showCodeActions = parts[12] != "0",
      ).normalized()
    }
  }
}

data class EditorSettings(
  val darkTheme: Boolean = true,
  val dynamicColor: Boolean = false,
  val themePreset: String = "OLIVE_OBSIDIAN",
  val uiStyleVersion: String = "V1",
  val editorBgPreset: String = "NONE",
  val editorBgImagePath: String = "",
  val editorBgOpacity: Float = 0.28f,
  val editorBgZoom: Float = 1.0f,
  val editorBgScrim: Float = 0.35f,
  val editorBgOffsetX: Float = 0f,
  val editorBgOffsetY: Float = 0f,
  val fontSizeSp: Int = 13,
  val zoomPercent: Int = 100,
  val tabSize: Int = 2,
  val wordWrap: Boolean = false,
  val showLineNumbers: Boolean = true,
  val autoCloseBrackets: Boolean = true,
  val stickyHeaders: Boolean = true,
  val indentGuides: Boolean = true,
  val codeFolding: Boolean = true,
  val gutterDiffMarkers: Boolean = true,
  val softKeyboardSuggestions: Boolean = false,
  val autoSaveEnabled: Boolean = true,
  val autoSaveIntervalSeconds: Int = 5,
  val defaultCompatibilityDate: String = "2024-11-01",
  val defaultNodejsCompat: Boolean = false,
  val defaultWorkersDevEnabled: Boolean = true,
  val autoOpenPreviewOnDeploy: Boolean = false,
  val homeBarStyle: FloatingBarStyleConfig = FloatingBarStyleConfig.HOME_DEFAULT,
  val editorBarStyle: FloatingBarStyleConfig = FloatingBarStyleConfig.EDITOR_DEFAULT,
  val homeMenuStyle: PopupMenuStyleConfig = PopupMenuStyleConfig.DEFAULT,
  val editorMenuStyle: PopupMenuStyleConfig = PopupMenuStyleConfig.DEFAULT,
  val selectionDragConfig: SelectionDragConfig = SelectionDragConfig.DEFAULT,
)

class AuthPreferencesStore(private val context: Context) {
  private object Keys {
    val AUTH_METHOD = stringPreferencesKey("auth_method")
    val ENC_ACCESS_TOKEN = stringPreferencesKey("enc_access_token")
    val ENC_REFRESH_TOKEN = stringPreferencesKey("enc_refresh_token")
    val EXPIRES_AT = longPreferencesKey("expires_at_ms")
    val EMAIL = stringPreferencesKey("auth_email")
    val ACCOUNT_ID = stringPreferencesKey("selected_account_id")
    val ACCOUNT_NAME = stringPreferencesKey("selected_account_name")

    val ENC_GITHUB_TOKEN = stringPreferencesKey("enc_github_token")
    val GITHUB_LOGIN = stringPreferencesKey("github_login")
    val GITHUB_METHOD = stringPreferencesKey("github_method")
    val GITHUB_SCOPES = stringPreferencesKey("github_scopes")
    val GITHUB_AVATAR = stringPreferencesKey("github_avatar")
    val GITHUB_NAME = stringPreferencesKey("github_display_name")

    val DARK_THEME = booleanPreferencesKey("editor_dark_theme")
    val DYNAMIC_COLOR = booleanPreferencesKey("editor_dynamic_color")
    val THEME_PRESET = stringPreferencesKey("editor_theme_preset")
    val UI_STYLE_VERSION = stringPreferencesKey("editor_ui_style_version")
    val EDITOR_BG_PRESET = stringPreferencesKey("editor_bg_preset")
    val EDITOR_BG_IMAGE_PATH = stringPreferencesKey("editor_bg_image_path")
    val EDITOR_BG_OPACITY = floatPreferencesKey("editor_bg_opacity")
    val EDITOR_BG_ZOOM = floatPreferencesKey("editor_bg_zoom")
    val EDITOR_BG_SCRIM = floatPreferencesKey("editor_bg_scrim")
    val EDITOR_BG_OFFSET_X = floatPreferencesKey("editor_bg_offset_x")
    val EDITOR_BG_OFFSET_Y = floatPreferencesKey("editor_bg_offset_y")
    val FONT_SIZE_SP = intPreferencesKey("editor_font_size_sp")
    val ZOOM_PERCENT = intPreferencesKey("editor_zoom_percent")
    val TAB_SIZE = intPreferencesKey("editor_tab_size")
    val WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
    val SHOW_LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
    val AUTO_CLOSE_BRACKETS = booleanPreferencesKey("editor_auto_brackets")
    val STICKY_HEADERS = booleanPreferencesKey("editor_sticky_headers")
    val INDENT_GUIDES = booleanPreferencesKey("editor_indent_guides")
    val CODE_FOLDING = booleanPreferencesKey("editor_code_folding")
    val GUTTER_DIFF_MARKERS = booleanPreferencesKey("editor_gutter_diff_markers")
    val SOFT_KEYBOARD_SUGGESTIONS = booleanPreferencesKey("editor_soft_keyboard_suggestions")
    val AUTO_SAVE_ENABLED = booleanPreferencesKey("editor_auto_save_enabled")
    val AUTO_SAVE_INTERVAL_SEC = intPreferencesKey("editor_auto_save_interval_sec")
    val DEFAULT_COMPAT_DATE = stringPreferencesKey("deploy_default_compat_date")
    val DEFAULT_NODEJS_COMPAT = booleanPreferencesKey("deploy_default_nodejs_compat")
    val DEFAULT_WORKERS_DEV = booleanPreferencesKey("deploy_default_workers_dev")
    val AUTO_OPEN_PREVIEW_ON_DEPLOY = booleanPreferencesKey("deploy_auto_open_preview")
    val HOME_BAR_STYLE = stringPreferencesKey("ui_home_bar_style")
    val EDITOR_BAR_STYLE = stringPreferencesKey("ui_editor_bar_style")
    val HOME_MENU_STYLE = stringPreferencesKey("ui_home_menu_style")
    val EDITOR_MENU_STYLE = stringPreferencesKey("ui_editor_menu_style")
    val SELECTION_DRAG_CONFIG = stringPreferencesKey("editor_selection_drag_config")
  }

  val credentialsFlow: Flow<StoredCredentials?> =
    context.edgeCodeDataStore.data.map { prefs ->
      val methodStr = prefs[Keys.AUTH_METHOD] ?: return@map null
      val method = runCatching { AuthMethod.valueOf(methodStr) }.getOrNull() ?: return@map null
      val token = CryptoManager.decrypt(prefs[Keys.ENC_ACCESS_TOKEN].orEmpty())
      if (token.isEmpty()) return@map null
      StoredCredentials(
        method = method,
        accessToken = token,
        refreshToken = CryptoManager.decrypt(prefs[Keys.ENC_REFRESH_TOKEN].orEmpty()),
        expiresAtEpochMs = prefs[Keys.EXPIRES_AT] ?: 0L,
        email = prefs[Keys.EMAIL].orEmpty(),
        selectedAccountId = prefs[Keys.ACCOUNT_ID].orEmpty(),
        selectedAccountName = prefs[Keys.ACCOUNT_NAME].orEmpty(),
      )
    }

  val gitHubCredentialsFlow: Flow<StoredGitHubCredentials?> =
    context.edgeCodeDataStore.data.map { prefs ->
      val token = CryptoManager.decrypt(prefs[Keys.ENC_GITHUB_TOKEN].orEmpty())
      if (token.isEmpty()) return@map null
      val method =
        runCatching { GitHubAuthMethod.valueOf(prefs[Keys.GITHUB_METHOD] ?: "PAT") }
          .getOrDefault(GitHubAuthMethod.PAT)
      StoredGitHubCredentials(
        token = token,
        login = prefs[Keys.GITHUB_LOGIN].orEmpty(),
        method = method,
        scopes = prefs[Keys.GITHUB_SCOPES].orEmpty(),
        avatarUrl = prefs[Keys.GITHUB_AVATAR].orEmpty(),
        displayName = prefs[Keys.GITHUB_NAME].orEmpty(),
      )
    }

  val editorSettingsFlow: Flow<EditorSettings> =
    context.edgeCodeDataStore.data.map { prefs ->
      EditorSettings(
        darkTheme = prefs[Keys.DARK_THEME] ?: true,
        dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
        themePreset = prefs[Keys.THEME_PRESET]?.takeIf { it.isNotBlank() } ?: "OLIVE_OBSIDIAN",
        uiStyleVersion = prefs[Keys.UI_STYLE_VERSION]?.takeIf { it.isNotBlank() } ?: "V1",
        editorBgPreset = prefs[Keys.EDITOR_BG_PRESET]?.takeIf { it.isNotBlank() } ?: "NONE",
        editorBgImagePath = prefs[Keys.EDITOR_BG_IMAGE_PATH].orEmpty(),
        editorBgOpacity = (prefs[Keys.EDITOR_BG_OPACITY] ?: 0.28f).coerceIn(0.05f, 1.0f),
        editorBgZoom = (prefs[Keys.EDITOR_BG_ZOOM] ?: 1.0f).coerceIn(1.0f, 3.0f),
        editorBgScrim = (prefs[Keys.EDITOR_BG_SCRIM] ?: 0.35f).coerceIn(0.0f, 0.85f),
        editorBgOffsetX = (prefs[Keys.EDITOR_BG_OFFSET_X] ?: 0f).coerceIn(-0.5f, 0.5f),
        editorBgOffsetY = (prefs[Keys.EDITOR_BG_OFFSET_Y] ?: 0f).coerceIn(-0.5f, 0.5f),
        fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 13).coerceIn(10, 20),
        zoomPercent = (prefs[Keys.ZOOM_PERCENT] ?: 100).coerceIn(60, 220),
        tabSize = if ((prefs[Keys.TAB_SIZE] ?: 2) == 4) 4 else 2,
        wordWrap = prefs[Keys.WORD_WRAP] ?: false,
        showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
        autoCloseBrackets = prefs[Keys.AUTO_CLOSE_BRACKETS] ?: true,
        stickyHeaders = prefs[Keys.STICKY_HEADERS] ?: true,
        indentGuides = prefs[Keys.INDENT_GUIDES] ?: true,
        codeFolding = prefs[Keys.CODE_FOLDING] ?: true,
        gutterDiffMarkers = prefs[Keys.GUTTER_DIFF_MARKERS] ?: true,
        softKeyboardSuggestions = prefs[Keys.SOFT_KEYBOARD_SUGGESTIONS] ?: false,
        autoSaveEnabled = prefs[Keys.AUTO_SAVE_ENABLED] ?: true,
        autoSaveIntervalSeconds = (prefs[Keys.AUTO_SAVE_INTERVAL_SEC] ?: 5).coerceIn(3, 60),
        defaultCompatibilityDate =
          prefs[Keys.DEFAULT_COMPAT_DATE]?.takeIf { it.isNotBlank() } ?: "2024-11-01",
        defaultNodejsCompat = prefs[Keys.DEFAULT_NODEJS_COMPAT] ?: false,
        defaultWorkersDevEnabled = prefs[Keys.DEFAULT_WORKERS_DEV] ?: true,
        autoOpenPreviewOnDeploy = prefs[Keys.AUTO_OPEN_PREVIEW_ON_DEPLOY] ?: false,
        homeBarStyle = FloatingBarStyleConfig.decode(prefs[Keys.HOME_BAR_STYLE], FloatingBarStyleConfig.HOME_DEFAULT),
        editorBarStyle = FloatingBarStyleConfig.decode(prefs[Keys.EDITOR_BAR_STYLE], FloatingBarStyleConfig.EDITOR_DEFAULT),
        homeMenuStyle = PopupMenuStyleConfig.decode(prefs[Keys.HOME_MENU_STYLE], PopupMenuStyleConfig.DEFAULT),
        editorMenuStyle = PopupMenuStyleConfig.decode(prefs[Keys.EDITOR_MENU_STYLE], PopupMenuStyleConfig.DEFAULT),
        selectionDragConfig = SelectionDragConfig.decode(prefs[Keys.SELECTION_DRAG_CONFIG], SelectionDragConfig.DEFAULT),
      )
    }

  suspend fun getCurrentCredentials(): StoredCredentials? = credentialsFlow.first()

  suspend fun getCurrentGitHubCredentials(): StoredGitHubCredentials? = gitHubCredentialsFlow.first()

  suspend fun saveCredentials(credentials: StoredCredentials) {
    context.edgeCodeDataStore.edit { prefs ->
      prefs[Keys.AUTH_METHOD] = credentials.method.name
      prefs[Keys.ENC_ACCESS_TOKEN] = CryptoManager.encrypt(credentials.accessToken)
      prefs[Keys.ENC_REFRESH_TOKEN] = CryptoManager.encrypt(credentials.refreshToken)
      prefs[Keys.EXPIRES_AT] = credentials.expiresAtEpochMs
      prefs[Keys.EMAIL] = credentials.email
      if (credentials.selectedAccountId.isNotEmpty()) {
        prefs[Keys.ACCOUNT_ID] = credentials.selectedAccountId
      }
      if (credentials.selectedAccountName.isNotEmpty()) {
        prefs[Keys.ACCOUNT_NAME] = credentials.selectedAccountName
      }
    }
  }

  suspend fun saveGitHubCredentials(
    token: String,
    login: String,
    method: GitHubAuthMethod = GitHubAuthMethod.PAT,
    scopes: String = "",
    avatarUrl: String = "",
    displayName: String = "",
  ) {
    context.edgeCodeDataStore.edit { prefs ->
      prefs[Keys.ENC_GITHUB_TOKEN] = CryptoManager.encrypt(token)
      prefs[Keys.GITHUB_LOGIN] = login
      prefs[Keys.GITHUB_METHOD] = method.name
      prefs[Keys.GITHUB_SCOPES] = scopes
      prefs[Keys.GITHUB_AVATAR] = avatarUrl
      prefs[Keys.GITHUB_NAME] = displayName
    }
  }

  suspend fun clearGitHubCredentials() {
    context.edgeCodeDataStore.edit { prefs ->
      prefs.remove(Keys.ENC_GITHUB_TOKEN)
      prefs.remove(Keys.GITHUB_LOGIN)
      prefs.remove(Keys.GITHUB_METHOD)
      prefs.remove(Keys.GITHUB_SCOPES)
      prefs.remove(Keys.GITHUB_AVATAR)
      prefs.remove(Keys.GITHUB_NAME)
    }
  }

  suspend fun saveSelectedAccount(accountId: String, accountName: String) {
    context.edgeCodeDataStore.edit { prefs ->
      prefs[Keys.ACCOUNT_ID] = accountId
      prefs[Keys.ACCOUNT_NAME] = accountName
    }
  }

  suspend fun clearCredentials() {
    context.edgeCodeDataStore.edit { prefs ->
      prefs.remove(Keys.AUTH_METHOD)
      prefs.remove(Keys.ENC_ACCESS_TOKEN)
      prefs.remove(Keys.ENC_REFRESH_TOKEN)
      prefs.remove(Keys.EXPIRES_AT)
      prefs.remove(Keys.EMAIL)
      prefs.remove(Keys.ACCOUNT_ID)
      prefs.remove(Keys.ACCOUNT_NAME)
    }
  }

  suspend fun updateEditorSettings(transform: (EditorSettings) -> EditorSettings) {
    val current = editorSettingsFlow.first()
    val next = transform(current)
    context.edgeCodeDataStore.edit { prefs ->
      prefs[Keys.DARK_THEME] = next.darkTheme
      prefs[Keys.DYNAMIC_COLOR] = next.dynamicColor
      prefs[Keys.THEME_PRESET] = next.themePreset.ifBlank { "OLIVE_OBSIDIAN" }
      prefs[Keys.UI_STYLE_VERSION] = next.uiStyleVersion.ifBlank { "V1" }
      prefs[Keys.EDITOR_BG_PRESET] = next.editorBgPreset.ifBlank { "NONE" }
      prefs[Keys.EDITOR_BG_IMAGE_PATH] = next.editorBgImagePath
      prefs[Keys.EDITOR_BG_OPACITY] = next.editorBgOpacity.coerceIn(0.05f, 1.0f)
      prefs[Keys.EDITOR_BG_ZOOM] = next.editorBgZoom.coerceIn(1.0f, 3.0f)
      prefs[Keys.EDITOR_BG_SCRIM] = next.editorBgScrim.coerceIn(0.0f, 0.85f)
      prefs[Keys.EDITOR_BG_OFFSET_X] = next.editorBgOffsetX.coerceIn(-0.5f, 0.5f)
      prefs[Keys.EDITOR_BG_OFFSET_Y] = next.editorBgOffsetY.coerceIn(-0.5f, 0.5f)
      prefs[Keys.FONT_SIZE_SP] = next.fontSizeSp.coerceIn(10, 20)
      prefs[Keys.ZOOM_PERCENT] = next.zoomPercent.coerceIn(60, 220)
      prefs[Keys.TAB_SIZE] = next.tabSize
      prefs[Keys.WORD_WRAP] = next.wordWrap
      prefs[Keys.SHOW_LINE_NUMBERS] = next.showLineNumbers
      prefs[Keys.AUTO_CLOSE_BRACKETS] = next.autoCloseBrackets
      prefs[Keys.STICKY_HEADERS] = next.stickyHeaders
      prefs[Keys.INDENT_GUIDES] = next.indentGuides
      prefs[Keys.CODE_FOLDING] = next.codeFolding
      prefs[Keys.GUTTER_DIFF_MARKERS] = next.gutterDiffMarkers
      prefs[Keys.SOFT_KEYBOARD_SUGGESTIONS] = next.softKeyboardSuggestions
      prefs[Keys.AUTO_SAVE_ENABLED] = next.autoSaveEnabled
      prefs[Keys.AUTO_SAVE_INTERVAL_SEC] = next.autoSaveIntervalSeconds.coerceIn(3, 60)
      prefs[Keys.DEFAULT_COMPAT_DATE] = next.defaultCompatibilityDate.trim().ifBlank { "2024-11-01" }
      prefs[Keys.DEFAULT_NODEJS_COMPAT] = next.defaultNodejsCompat
      prefs[Keys.DEFAULT_WORKERS_DEV] = next.defaultWorkersDevEnabled
      prefs[Keys.AUTO_OPEN_PREVIEW_ON_DEPLOY] = next.autoOpenPreviewOnDeploy
      prefs[Keys.HOME_BAR_STYLE] = next.homeBarStyle.encode()
      prefs[Keys.EDITOR_BAR_STYLE] = next.editorBarStyle.encode()
      prefs[Keys.HOME_MENU_STYLE] = next.homeMenuStyle.encode()
      prefs[Keys.EDITOR_MENU_STYLE] = next.editorMenuStyle.encode()
      prefs[Keys.SELECTION_DRAG_CONFIG] = next.selectionDragConfig.encode()
    }
  }
}
