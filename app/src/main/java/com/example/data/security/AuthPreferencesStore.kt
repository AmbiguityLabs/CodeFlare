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
import com.example.idecore.snippets.CodeSnippetItem
import com.example.idecore.snippets.CodeSnippetRegistry
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
        heightDp = 48,
        widthPercent = 78,
        iconSpacingDp = 14,
        iconSizeDp = 19,
        opacity = 0.80f,
        borderOpacity = 0.30f,
        cornerRadiusDp = 28,
        bottomMarginDp = 6,
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
  val showScopeBadge: Boolean = false,
  val showExpandShrink: Boolean = false,
  val showDragGrip: Boolean = false,
  val showModeToggle: Boolean = false,
  val showDropTargetLoupe: Boolean = true,
  val showQuickActions: Boolean = true,
  val showCodeActions: Boolean = false,
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
    val MINIMAL = DEFAULT
    val SMART_POWER =
      SelectionDragConfig(
        showScopeBadge = true,
        showExpandShrink = true,
        showDragGrip = true,
        showModeToggle = true,
        showDropTargetLoupe = true,
        showQuickActions = true,
        showCodeActions = true,
      )

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

enum class IconLabelBehavior(
  val label: String,
  val shortLabel: String,
  val description: String,
) {
  REVEAL_ON_TAP(
    label = "Reveal on Tap",
    shortLabel = "On Tap",
    description = "Show icon first; reveal text when tapped",
  ),
  ICON_ONLY(
    label = "Icon Only",
    shortLabel = "Icons",
    description = "Keep controls minimal with icons only",
  ),
  ALWAYS_SHOW(
    label = "Always Show",
    shortLabel = "Always",
    description = "Always show both icon and text label",
  ),
  ;

  companion object {
    fun fromKey(raw: String?): IconLabelBehavior =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: REVEAL_ON_TAP
  }
}

enum class EditorBubbleColorStyle(val key: String, val label: String) {
  FLARE_HEAT("FLARE_HEAT", "CodeFlare Heat"),
  THEME_ACCENT("THEME_ACCENT", "Theme Accent"),
  AURORA("AURORA", "Aurora"),
  MONO_GLASS("MONO_GLASS", "Crystal Glass"),
  PASTEL("PASTEL", "Pastel"),
  ;

  companion object {
    fun fromKey(raw: String?): EditorBubbleColorStyle =
      entries.firstOrNull { it.key.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
        ?: FLARE_HEAT
  }
}

data class EditorBubbleConfig(
  val enabled: Boolean = false,
  val count: Int = 14,
  val minSizeDp: Int = 12,
  val maxSizeDp: Int = 34,
  val minDurationSec: Float = 6.0f,
  val maxDurationSec: Float = 13.0f,
  val opacity: Float = 0.30f,
  val colorStyle: EditorBubbleColorStyle = EditorBubbleColorStyle.FLARE_HEAT,
  val tapToPop: Boolean = true,
  val popBurstEnabled: Boolean = true,
) {
  fun normalized(): EditorBubbleConfig {
    val safeMinSize = minSizeDp.coerceIn(6, 40)
    val safeMaxSize = maxSizeDp.coerceIn((safeMinSize + 4).coerceAtMost(64), 64)
    val safeMinDur = minDurationSec.coerceIn(2.5f, 18.0f)
    val safeMaxDur = maxDurationSec.coerceIn((safeMinDur + 1.0f).coerceAtMost(26.0f), 26.0f)
    return copy(
      count = count.coerceIn(4, 32),
      minSizeDp = safeMinSize,
      maxSizeDp = safeMaxSize,
      minDurationSec = safeMinDur,
      maxDurationSec = safeMaxDur,
      opacity = opacity.coerceIn(0.08f, 0.80f),
    )
  }

  fun encode(): String {
    val n = normalized()
    return listOf(
      if (n.enabled) 1 else 0,
      n.count,
      n.minSizeDp,
      n.maxSizeDp,
      n.minDurationSec,
      n.maxDurationSec,
      n.opacity,
      n.colorStyle.key,
      if (n.tapToPop) 1 else 0,
      if (n.popBurstEnabled) 1 else 0,
    ).joinToString("|")
  }

  companion object {
    val DEFAULT = EditorBubbleConfig()

    fun decode(raw: String?, fallback: EditorBubbleConfig = DEFAULT): EditorBubbleConfig {
      if (raw.isNullOrBlank()) return fallback
      val parts = raw.split("|")
      if (parts.size < 10) return fallback
      return EditorBubbleConfig(
        enabled = parts[0] == "1",
        count = parts[1].toIntOrNull() ?: fallback.count,
        minSizeDp = parts[2].toIntOrNull() ?: fallback.minSizeDp,
        maxSizeDp = parts[3].toIntOrNull() ?: fallback.maxSizeDp,
        minDurationSec = parts[4].toFloatOrNull() ?: fallback.minDurationSec,
        maxDurationSec = parts[5].toFloatOrNull() ?: fallback.maxDurationSec,
        opacity = parts[6].toFloatOrNull() ?: fallback.opacity,
        colorStyle = EditorBubbleColorStyle.fromKey(parts[7]),
        tapToPop = parts[8] != "0",
        popBurstEnabled = parts[9] != "0",
      ).normalized()
    }
  }
}

data class EditorSettings(
  val darkTheme: Boolean = true,
  val themeMode: String = THEME_DARK,
  val dynamicColor: Boolean = false,
  val accent: String = ACCENT_LIME,
  val accentColor: Long = DEFAULT_ACCENT_COLOR,
  val themePreset: String = "OLIVE_OBSIDIAN",
  val codeStylePreset: String = CODE_STYLE_DEFAULT,
  val codeStyleOverrideCanvas: Boolean = true,
  val uiStyleVersion: String = "V1",
  val iconLabelBehavior: IconLabelBehavior = IconLabelBehavior.REVEAL_ON_TAP,
  val reopenLastProject: Boolean = true,
  val editorBgPreset: String = "NONE",
  val editorBgImagePath: String = "",
  val editorBgOpacity: Float = 0.28f,
  val editorBgZoom: Float = 1.0f,
  val editorBgScrim: Float = 0.35f,
  val editorBgOffsetX: Float = 0f,
  val editorBgOffsetY: Float = 0f,
  val editorBgBlurDp: Float = 0f,
  val editorBgVignette: Float = 0.15f,
  val editorBgFitMode: String = BG_FIT_CROP,
  val editorBgHomeEnabled: Boolean = false,
  val editorBgSavedPaths: List<String> = emptyList(),
  val editorBubbles: EditorBubbleConfig = EditorBubbleConfig.DEFAULT,
  val fontSizeSp: Int = 13,
  val zoomPercent: Int = 100,
  val tabSize: Int = 2,
  val codeFont: String = CODE_FONT_JETBRAINS,
  val caretStyle: String = CARET_LINE,
  val fontLigatures: Boolean = true,
  val wordWrap: Boolean = false,
  val showHorizontalScrollbar: Boolean = true,
  val showLineNumbers: Boolean = true,
  val autoCloseBrackets: Boolean = true,
  val stickyHeaders: Boolean = true,
  val indentGuides: Boolean = true,
  val codeFolding: Boolean = true,
  val gutterDiffMarkers: Boolean = true,
  val pinchZoomEnabled: Boolean = true,
  val softKeyboardSuggestions: Boolean = false,
  val showSymbolBar: Boolean = true,
  val autoPopupCompletion: Boolean = true,
  val completionDelayMs: Int = 110,
  val completionMaxItems: Int = 50,
  val analyzeOnTheFly: Boolean = true,
  val reparseDelayMs: Int = 300,
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
  val useUnifiedEditorEverywhere: Boolean = true,
  val apiCacheTtlHours: Int = 4,
  val warnOnCloseDirtyTab: Boolean = true,
  val closeUnsavedPolicy: String = CLOSE_UNSAVED_ASK,
  val warnOnLeaveDirtyWorkspace: Boolean = true,
  val warnOnCloudOverwrite: Boolean = true,
  val formatOnSave: Boolean = false,
  val customSnippets: List<CodeSnippetItem> = emptyList(),
) {
  fun resolveDarkTheme(systemIsDark: Boolean): Boolean =
    when (themeMode.lowercase()) {
      THEME_LIGHT -> false
      THEME_DARK -> true
      THEME_SYSTEM -> systemIsDark
      else -> darkTheme
    }

  fun normalizedThemeMode(): String =
    when (themeMode.lowercase()) {
      THEME_LIGHT -> THEME_LIGHT
      THEME_SYSTEM -> THEME_SYSTEM
      THEME_DARK -> THEME_DARK
      else -> if (darkTheme) THEME_DARK else THEME_LIGHT
    }

  fun normalizedAccent(): String =
    when (accent.lowercase()) {
      ACCENT_DYNAMIC,
      ACCENT_LIME,
      ACCENT_VIOLET,
      ACCENT_TEAL,
      ACCENT_ORANGE,
      ACCENT_PUMPKIN,
      ACCENT_CUSTOM -> accent.lowercase()
      else -> if (dynamicColor) ACCENT_DYNAMIC else accentForPreset(themePreset)
    }

  fun normalizedCodeFont(): String =
    when (codeFont.lowercase()) {
      CODE_FONT_MONOSPACE -> CODE_FONT_MONOSPACE
      else -> CODE_FONT_JETBRAINS
    }

  fun normalizedCaretStyle(): String =
    when (caretStyle.lowercase()) {
      CARET_BLOCK,
      CARET_UNDERLINE,
      CARET_CANDLE -> caretStyle.lowercase()
      else -> CARET_LINE
    }

  fun normalizedCloseUnsavedPolicy(): String =
    when (closeUnsavedPolicy.lowercase()) {
      CLOSE_UNSAVED_SAVE -> CLOSE_UNSAVED_SAVE
      CLOSE_UNSAVED_DISCARD -> CLOSE_UNSAVED_DISCARD
      CLOSE_UNSAVED_ASK -> CLOSE_UNSAVED_ASK
      else -> if (warnOnCloseDirtyTab) CLOSE_UNSAVED_ASK else CLOSE_UNSAVED_SAVE
    }

  fun normalizedCodeStylePreset(): String =
    when (codeStylePreset.uppercase()) {
      "NEON_QUERY",
      "STUDIO_MONOKAI",
      "TOKYO_NIGHT",
      "DRACULA_VIVID" -> codeStylePreset.uppercase()
      else -> CODE_STYLE_DEFAULT
    }

  fun normalizedBgFitMode(): String =
    when (editorBgFitMode.uppercase()) {
      BG_FIT_FIT -> BG_FIT_FIT
      BG_FIT_FILL -> BG_FIT_FILL
      else -> BG_FIT_CROP
    }

  companion object {
    const val CODE_STYLE_DEFAULT = "DEFAULT"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"

    const val ACCENT_DYNAMIC = "dynamic"
    const val ACCENT_LIME = "lime"
    const val ACCENT_VIOLET = "violet"
    const val ACCENT_TEAL = "teal"
    const val ACCENT_ORANGE = "orange"
    const val ACCENT_PUMPKIN = "pumpkin"
    const val ACCENT_CUSTOM = "custom"

    const val CODE_FONT_JETBRAINS = "jetbrains"
    const val CODE_FONT_MONOSPACE = "monospace"

    const val CARET_LINE = "line"
    const val CARET_BLOCK = "block"
    const val CARET_UNDERLINE = "underline"
    const val CARET_CANDLE = "candle"

    const val CLOSE_UNSAVED_ASK = "ask"
    const val CLOSE_UNSAVED_SAVE = "save"
    const val CLOSE_UNSAVED_DISCARD = "discard"

    const val BG_FIT_CROP = "CROP"
    const val BG_FIT_FIT = "FIT"
    const val BG_FIT_FILL = "FILL"
    const val MAX_SAVED_BG_PATHS = 5

    fun encodeSavedPaths(paths: List<String>): String =
      paths.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_SAVED_BG_PATHS).joinToString("|")

    fun decodeSavedPaths(raw: String?): List<String> =
      raw?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.take(MAX_SAVED_BG_PATHS).orEmpty()

    const val MIN_COMPLETION_DELAY_MS = 0
    const val MAX_COMPLETION_DELAY_MS = 1000
    const val MIN_COMPLETION_MAX_ITEMS = 10
    const val MAX_COMPLETION_MAX_ITEMS = 200
    const val MIN_REPARSE_DELAY_MS = 0
    const val MAX_REPARSE_DELAY_MS = 2000

    const val DEFAULT_ACCENT_COLOR = 0xFF8B5CF6L

    fun accentForPreset(preset: String): String =
      when (preset.uppercase()) {
        "SLATE_CARBON" -> ACCENT_TEAL
        "MIDNIGHT_NOIR" -> ACCENT_VIOLET
        "AMBER_DUSK" -> ACCENT_ORANGE
        else -> ACCENT_LIME
      }

    fun presetForAccent(accent: String, fallback: String = "OLIVE_OBSIDIAN"): String =
      when (accent.lowercase()) {
        ACCENT_LIME -> "OLIVE_OBSIDIAN"
        ACCENT_TEAL -> "SLATE_CARBON"
        ACCENT_VIOLET -> "MIDNIGHT_NOIR"
        ACCENT_ORANGE, ACCENT_PUMPKIN -> "AMBER_DUSK"
        else -> fallback
      }
  }
}

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
    val THEME_MODE = stringPreferencesKey("editor_theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("editor_dynamic_color")
    val ACCENT = stringPreferencesKey("editor_accent")
    val ACCENT_COLOR = longPreferencesKey("editor_accent_color")
    val THEME_PRESET = stringPreferencesKey("editor_theme_preset")
    val CODE_STYLE_PRESET = stringPreferencesKey("editor_code_style_preset")
    val CODE_STYLE_OVERRIDE_CANVAS = booleanPreferencesKey("editor_code_style_override_canvas")
    val UI_STYLE_VERSION = stringPreferencesKey("editor_ui_style_version")
    val ICON_LABEL_BEHAVIOR = stringPreferencesKey("ui_icon_label_behavior")
    val REOPEN_LAST_PROJECT = booleanPreferencesKey("app_reopen_last_project")
    val EDITOR_BG_PRESET = stringPreferencesKey("editor_bg_preset")
    val EDITOR_BG_IMAGE_PATH = stringPreferencesKey("editor_bg_image_path")
    val EDITOR_BG_OPACITY = floatPreferencesKey("editor_bg_opacity")
    val EDITOR_BG_ZOOM = floatPreferencesKey("editor_bg_zoom")
    val EDITOR_BG_SCRIM = floatPreferencesKey("editor_bg_scrim")
    val EDITOR_BG_OFFSET_X = floatPreferencesKey("editor_bg_offset_x")
    val EDITOR_BG_OFFSET_Y = floatPreferencesKey("editor_bg_offset_y")
    val EDITOR_BG_BLUR_DP = floatPreferencesKey("editor_bg_blur_dp")
    val EDITOR_BG_VIGNETTE = floatPreferencesKey("editor_bg_vignette")
    val EDITOR_BG_FIT_MODE = stringPreferencesKey("editor_bg_fit_mode")
    val EDITOR_BG_HOME_ENABLED = booleanPreferencesKey("editor_bg_home_enabled")
    val EDITOR_BG_SAVED_PATHS = stringPreferencesKey("editor_bg_saved_paths")
    val EDITOR_BUBBLES = stringPreferencesKey("editor_bubbles_config")
    val FONT_SIZE_SP = intPreferencesKey("editor_font_size_sp")
    val ZOOM_PERCENT = intPreferencesKey("editor_zoom_percent")
    val TAB_SIZE = intPreferencesKey("editor_tab_size")
    val CODE_FONT = stringPreferencesKey("editor_code_font")
    val CARET_STYLE = stringPreferencesKey("editor_caret_style")
    val FONT_LIGATURES = booleanPreferencesKey("editor_font_ligatures")
    val WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
    val SHOW_HORIZONTAL_SCROLLBAR = booleanPreferencesKey("editor_horizontal_scrollbar")
    val SHOW_LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
    val AUTO_CLOSE_BRACKETS = booleanPreferencesKey("editor_auto_brackets")
    val STICKY_HEADERS = booleanPreferencesKey("editor_sticky_headers")
    val INDENT_GUIDES = booleanPreferencesKey("editor_indent_guides")
    val CODE_FOLDING = booleanPreferencesKey("editor_code_folding")
    val GUTTER_DIFF_MARKERS = booleanPreferencesKey("editor_gutter_diff_markers")
    val PINCH_ZOOM_ENABLED = booleanPreferencesKey("editor_pinch_zoom")
    val SOFT_KEYBOARD_SUGGESTIONS = booleanPreferencesKey("editor_soft_keyboard_suggestions")
    val SHOW_SYMBOL_BAR = booleanPreferencesKey("editor_show_symbol_bar")
    val AUTO_POPUP_COMPLETION = booleanPreferencesKey("completion_auto_popup")
    val COMPLETION_DELAY_MS = intPreferencesKey("completion_delay_ms")
    val COMPLETION_MAX_ITEMS = intPreferencesKey("completion_max_items")
    val ANALYZE_ON_THE_FLY = booleanPreferencesKey("analysis_on_the_fly")
    val REPARSE_DELAY_MS = intPreferencesKey("analysis_reparse_delay_ms")
    val AUTO_SAVE_ENABLED = booleanPreferencesKey("editor_auto_save_enabled")
    val AUTO_SAVE_INTERVAL_SEC = intPreferencesKey("editor_auto_save_interval_sec")
    val FORMAT_ON_SAVE = booleanPreferencesKey("editor_format_on_save")
    val CUSTOM_SNIPPETS = stringPreferencesKey("editor_custom_snippets")
    val DEFAULT_COMPAT_DATE = stringPreferencesKey("deploy_default_compat_date")
    val DEFAULT_NODEJS_COMPAT = booleanPreferencesKey("deploy_default_nodejs_compat")
    val DEFAULT_WORKERS_DEV = booleanPreferencesKey("deploy_default_workers_dev")
    val AUTO_OPEN_PREVIEW_ON_DEPLOY = booleanPreferencesKey("deploy_auto_open_preview")
    val HOME_BAR_STYLE = stringPreferencesKey("ui_home_bar_style")
    val EDITOR_BAR_STYLE = stringPreferencesKey("ui_editor_bar_style")
    val HOME_MENU_STYLE = stringPreferencesKey("ui_home_menu_style")
    val EDITOR_MENU_STYLE = stringPreferencesKey("ui_editor_menu_style")
    val SELECTION_DRAG_CONFIG = stringPreferencesKey("editor_selection_drag_config")
    val USE_UNIFIED_EDITOR_EVERYWHERE = booleanPreferencesKey("editor_use_unified_everywhere")
    val API_CACHE_TTL_HOURS = intPreferencesKey("api_cache_ttl_hours")
    val WARN_CLOSE_DIRTY_TAB = booleanPreferencesKey("warn_on_close_dirty_tab")
    val CLOSE_UNSAVED_POLICY = stringPreferencesKey("editor_close_unsaved_policy")
    val WARN_LEAVE_DIRTY_WORKSPACE = booleanPreferencesKey("warn_on_leave_dirty_workspace")
    val WARN_CLOUD_OVERWRITE = booleanPreferencesKey("warn_on_cloud_overwrite")
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
      val legacyDark = prefs[Keys.DARK_THEME] ?: true
      val legacyDynamic = prefs[Keys.DYNAMIC_COLOR] ?: false
      val preset = prefs[Keys.THEME_PRESET]?.takeIf { it.isNotBlank() } ?: "OLIVE_OBSIDIAN"
      val storedThemeMode =
        prefs[Keys.THEME_MODE]?.takeIf { it.isNotBlank() }
          ?: if (legacyDark) EditorSettings.THEME_DARK else EditorSettings.THEME_LIGHT
      val storedAccent =
        prefs[Keys.ACCENT]?.takeIf { it.isNotBlank() }
          ?: if (legacyDynamic) EditorSettings.ACCENT_DYNAMIC else EditorSettings.accentForPreset(preset)
      val storedAccentColor = prefs[Keys.ACCENT_COLOR] ?: EditorSettings.DEFAULT_ACCENT_COLOR
      val legacyWarnClose = prefs[Keys.WARN_CLOSE_DIRTY_TAB] ?: true
      val storedClosePolicy =
        prefs[Keys.CLOSE_UNSAVED_POLICY]?.takeIf { it.isNotBlank() }
          ?: if (legacyWarnClose) EditorSettings.CLOSE_UNSAVED_ASK else EditorSettings.CLOSE_UNSAVED_SAVE
      EditorSettings(
        darkTheme =
          when (storedThemeMode.lowercase()) {
            EditorSettings.THEME_LIGHT -> false
            EditorSettings.THEME_DARK -> true
            else -> legacyDark
          },
        themeMode = storedThemeMode,
        dynamicColor = storedAccent == EditorSettings.ACCENT_DYNAMIC || legacyDynamic,
        accent = storedAccent,
        accentColor = storedAccentColor,
        themePreset = preset,
        codeStylePreset = prefs[Keys.CODE_STYLE_PRESET]?.takeIf { it.isNotBlank() }?.uppercase() ?: EditorSettings.CODE_STYLE_DEFAULT,
        codeStyleOverrideCanvas = prefs[Keys.CODE_STYLE_OVERRIDE_CANVAS] ?: true,
        uiStyleVersion = prefs[Keys.UI_STYLE_VERSION]?.takeIf { it.isNotBlank() } ?: "V1",
        iconLabelBehavior = IconLabelBehavior.fromKey(prefs[Keys.ICON_LABEL_BEHAVIOR]),
        reopenLastProject = prefs[Keys.REOPEN_LAST_PROJECT] ?: true,
        editorBgPreset = prefs[Keys.EDITOR_BG_PRESET]?.takeIf { it.isNotBlank() } ?: "NONE",
        editorBgImagePath = prefs[Keys.EDITOR_BG_IMAGE_PATH].orEmpty(),
        editorBgOpacity = (prefs[Keys.EDITOR_BG_OPACITY] ?: 0.28f).coerceIn(0.05f, 1.0f),
        editorBgZoom = (prefs[Keys.EDITOR_BG_ZOOM] ?: 1.0f).coerceIn(1.0f, 3.0f),
        editorBgScrim = (prefs[Keys.EDITOR_BG_SCRIM] ?: 0.35f).coerceIn(0.0f, 0.85f),
        editorBgOffsetX = (prefs[Keys.EDITOR_BG_OFFSET_X] ?: 0f).coerceIn(-0.5f, 0.5f),
        editorBgOffsetY = (prefs[Keys.EDITOR_BG_OFFSET_Y] ?: 0f).coerceIn(-0.5f, 0.5f),
        editorBgBlurDp = (prefs[Keys.EDITOR_BG_BLUR_DP] ?: 0f).coerceIn(0f, 25f),
        editorBgVignette = (prefs[Keys.EDITOR_BG_VIGNETTE] ?: 0.15f).coerceIn(0f, 0.75f),
        editorBgFitMode =
          when (prefs[Keys.EDITOR_BG_FIT_MODE]?.uppercase()) {
            EditorSettings.BG_FIT_FIT -> EditorSettings.BG_FIT_FIT
            EditorSettings.BG_FIT_FILL -> EditorSettings.BG_FIT_FILL
            else -> EditorSettings.BG_FIT_CROP
          },
        editorBgHomeEnabled = prefs[Keys.EDITOR_BG_HOME_ENABLED] ?: false,
        editorBgSavedPaths =
          EditorSettings.decodeSavedPaths(prefs[Keys.EDITOR_BG_SAVED_PATHS]).let { decoded ->
            val currentPath = prefs[Keys.EDITOR_BG_IMAGE_PATH].orEmpty().trim()
            if (currentPath.isNotEmpty() && currentPath !in decoded) {
              (listOf(currentPath) + decoded).take(EditorSettings.MAX_SAVED_BG_PATHS)
            } else decoded
          },
        editorBubbles = EditorBubbleConfig.decode(prefs[Keys.EDITOR_BUBBLES], EditorBubbleConfig.DEFAULT),
        fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 13).coerceIn(10, 20),
        zoomPercent = (prefs[Keys.ZOOM_PERCENT] ?: 100).coerceIn(60, 220),
        tabSize = if ((prefs[Keys.TAB_SIZE] ?: 2) == 4) 4 else 2,
        codeFont =
          when (prefs[Keys.CODE_FONT]?.lowercase()) {
            EditorSettings.CODE_FONT_MONOSPACE -> EditorSettings.CODE_FONT_MONOSPACE
            else -> EditorSettings.CODE_FONT_JETBRAINS
          },
        caretStyle =
          when (prefs[Keys.CARET_STYLE]?.lowercase()) {
            EditorSettings.CARET_BLOCK,
            EditorSettings.CARET_UNDERLINE,
            EditorSettings.CARET_CANDLE -> prefs[Keys.CARET_STYLE]!!.lowercase()
            else -> EditorSettings.CARET_LINE
          },
        fontLigatures = prefs[Keys.FONT_LIGATURES] ?: true,
        wordWrap = prefs[Keys.WORD_WRAP] ?: false,
        showHorizontalScrollbar = prefs[Keys.SHOW_HORIZONTAL_SCROLLBAR] ?: true,
        showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
        autoCloseBrackets = prefs[Keys.AUTO_CLOSE_BRACKETS] ?: true,
        stickyHeaders = prefs[Keys.STICKY_HEADERS] ?: true,
        indentGuides = prefs[Keys.INDENT_GUIDES] ?: true,
        codeFolding = prefs[Keys.CODE_FOLDING] ?: true,
        gutterDiffMarkers = prefs[Keys.GUTTER_DIFF_MARKERS] ?: true,
        pinchZoomEnabled = prefs[Keys.PINCH_ZOOM_ENABLED] ?: true,
        softKeyboardSuggestions = prefs[Keys.SOFT_KEYBOARD_SUGGESTIONS] ?: false,
        showSymbolBar = prefs[Keys.SHOW_SYMBOL_BAR] ?: true,
        autoPopupCompletion = prefs[Keys.AUTO_POPUP_COMPLETION] ?: true,
        completionDelayMs =
          (prefs[Keys.COMPLETION_DELAY_MS] ?: 110).coerceIn(
            EditorSettings.MIN_COMPLETION_DELAY_MS,
            EditorSettings.MAX_COMPLETION_DELAY_MS,
          ),
        completionMaxItems =
          (prefs[Keys.COMPLETION_MAX_ITEMS] ?: 50).coerceIn(
            EditorSettings.MIN_COMPLETION_MAX_ITEMS,
            EditorSettings.MAX_COMPLETION_MAX_ITEMS,
          ),
        analyzeOnTheFly = prefs[Keys.ANALYZE_ON_THE_FLY] ?: true,
        reparseDelayMs =
          (prefs[Keys.REPARSE_DELAY_MS] ?: 300).coerceIn(
            EditorSettings.MIN_REPARSE_DELAY_MS,
            EditorSettings.MAX_REPARSE_DELAY_MS,
          ),
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
        useUnifiedEditorEverywhere = prefs[Keys.USE_UNIFIED_EDITOR_EVERYWHERE] ?: true,
        apiCacheTtlHours = (prefs[Keys.API_CACHE_TTL_HOURS] ?: 4).coerceIn(0, 24),
        warnOnCloseDirtyTab =
          when (storedClosePolicy.lowercase()) {
            EditorSettings.CLOSE_UNSAVED_SAVE, EditorSettings.CLOSE_UNSAVED_DISCARD -> false
            else -> legacyWarnClose
          },
        closeUnsavedPolicy = storedClosePolicy,
        warnOnLeaveDirtyWorkspace = prefs[Keys.WARN_LEAVE_DIRTY_WORKSPACE] ?: true,
        warnOnCloudOverwrite = prefs[Keys.WARN_CLOUD_OVERWRITE] ?: true,
        formatOnSave = prefs[Keys.FORMAT_ON_SAVE] ?: false,
        customSnippets = CodeSnippetRegistry.decodeCustomSnippets(prefs[Keys.CUSTOM_SNIPPETS]),
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
    val rawNext = transform(current)
    val resolvedMode =
      when {
        rawNext.themeMode != current.themeMode -> rawNext.normalizedThemeMode()
        rawNext.darkTheme != current.darkTheme ->
          if (rawNext.darkTheme) EditorSettings.THEME_DARK else EditorSettings.THEME_LIGHT
        else -> rawNext.normalizedThemeMode()
      }
    val resolvedDark =
      when (resolvedMode) {
        EditorSettings.THEME_LIGHT -> false
        EditorSettings.THEME_DARK -> true
        else -> rawNext.darkTheme
      }
    val resolvedAccent =
      when {
        rawNext.accent != current.accent -> rawNext.normalizedAccent()
        rawNext.dynamicColor != current.dynamicColor ->
          if (rawNext.dynamicColor) EditorSettings.ACCENT_DYNAMIC
          else EditorSettings.accentForPreset(rawNext.themePreset)
        else -> rawNext.normalizedAccent()
      }
    val resolvedDynamic = resolvedAccent == EditorSettings.ACCENT_DYNAMIC
    val resolvedPreset =
      when {
        rawNext.themePreset != current.themePreset -> rawNext.themePreset.ifBlank { "OLIVE_OBSIDIAN" }
        rawNext.accent != current.accent -> EditorSettings.presetForAccent(resolvedAccent, rawNext.themePreset.ifBlank { "OLIVE_OBSIDIAN" })
        else -> rawNext.themePreset.ifBlank { "OLIVE_OBSIDIAN" }
      }
    val resolvedClosePolicy =
      when {
        rawNext.closeUnsavedPolicy != current.closeUnsavedPolicy -> rawNext.normalizedCloseUnsavedPolicy()
        rawNext.warnOnCloseDirtyTab != current.warnOnCloseDirtyTab ->
          if (rawNext.warnOnCloseDirtyTab) EditorSettings.CLOSE_UNSAVED_ASK else EditorSettings.CLOSE_UNSAVED_SAVE
        else -> rawNext.normalizedCloseUnsavedPolicy()
      }
    val resolvedWarnClose = resolvedClosePolicy == EditorSettings.CLOSE_UNSAVED_ASK
    val next =
      rawNext.copy(
        darkTheme = resolvedDark,
        themeMode = resolvedMode,
        dynamicColor = resolvedDynamic,
        accent = resolvedAccent,
        accentColor = rawNext.accentColor or 0xFF000000L,
        themePreset = resolvedPreset,
        codeStylePreset = rawNext.normalizedCodeStylePreset(),
        codeFont = rawNext.normalizedCodeFont(),
        caretStyle = rawNext.normalizedCaretStyle(),
        editorBubbles = rawNext.editorBubbles.normalized(),
        closeUnsavedPolicy = resolvedClosePolicy,
        warnOnCloseDirtyTab = resolvedWarnClose,
        completionDelayMs =
          rawNext.completionDelayMs.coerceIn(
            EditorSettings.MIN_COMPLETION_DELAY_MS,
            EditorSettings.MAX_COMPLETION_DELAY_MS,
          ),
        completionMaxItems =
          rawNext.completionMaxItems.coerceIn(
            EditorSettings.MIN_COMPLETION_MAX_ITEMS,
            EditorSettings.MAX_COMPLETION_MAX_ITEMS,
          ),
        reparseDelayMs =
          rawNext.reparseDelayMs.coerceIn(
            EditorSettings.MIN_REPARSE_DELAY_MS,
            EditorSettings.MAX_REPARSE_DELAY_MS,
          ),
        editorBgBlurDp = rawNext.editorBgBlurDp.coerceIn(0f, 25f),
        editorBgVignette = rawNext.editorBgVignette.coerceIn(0f, 0.75f),
        editorBgFitMode = rawNext.normalizedBgFitMode(),
        editorBgSavedPaths =
          rawNext.editorBgSavedPaths
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(EditorSettings.MAX_SAVED_BG_PATHS),
      )
    context.edgeCodeDataStore.edit { prefs ->
      prefs[Keys.DARK_THEME] = next.darkTheme
      prefs[Keys.THEME_MODE] = next.themeMode
      prefs[Keys.DYNAMIC_COLOR] = next.dynamicColor
      prefs[Keys.ACCENT] = next.accent
      prefs[Keys.ACCENT_COLOR] = next.accentColor
      prefs[Keys.THEME_PRESET] = next.themePreset
      prefs[Keys.CODE_STYLE_PRESET] = next.codeStylePreset
      prefs[Keys.CODE_STYLE_OVERRIDE_CANVAS] = next.codeStyleOverrideCanvas
      prefs[Keys.UI_STYLE_VERSION] = next.uiStyleVersion.ifBlank { "V1" }
      prefs[Keys.ICON_LABEL_BEHAVIOR] = next.iconLabelBehavior.name
      prefs[Keys.REOPEN_LAST_PROJECT] = next.reopenLastProject
      prefs[Keys.EDITOR_BG_PRESET] = next.editorBgPreset.ifBlank { "NONE" }
      prefs[Keys.EDITOR_BG_IMAGE_PATH] = next.editorBgImagePath
      prefs[Keys.EDITOR_BG_OPACITY] = next.editorBgOpacity.coerceIn(0.05f, 1.0f)
      prefs[Keys.EDITOR_BG_ZOOM] = next.editorBgZoom.coerceIn(1.0f, 3.0f)
      prefs[Keys.EDITOR_BG_SCRIM] = next.editorBgScrim.coerceIn(0.0f, 0.85f)
      prefs[Keys.EDITOR_BG_OFFSET_X] = next.editorBgOffsetX.coerceIn(-0.5f, 0.5f)
      prefs[Keys.EDITOR_BG_OFFSET_Y] = next.editorBgOffsetY.coerceIn(-0.5f, 0.5f)
      prefs[Keys.EDITOR_BG_BLUR_DP] = next.editorBgBlurDp.coerceIn(0f, 25f)
      prefs[Keys.EDITOR_BG_VIGNETTE] = next.editorBgVignette.coerceIn(0f, 0.75f)
      prefs[Keys.EDITOR_BG_FIT_MODE] = next.normalizedBgFitMode()
      prefs[Keys.EDITOR_BG_HOME_ENABLED] = next.editorBgHomeEnabled
      prefs[Keys.EDITOR_BG_SAVED_PATHS] = EditorSettings.encodeSavedPaths(next.editorBgSavedPaths)
      prefs[Keys.EDITOR_BUBBLES] = next.editorBubbles.encode()
      prefs[Keys.FONT_SIZE_SP] = next.fontSizeSp.coerceIn(10, 20)
      prefs[Keys.ZOOM_PERCENT] = next.zoomPercent.coerceIn(60, 220)
      prefs[Keys.TAB_SIZE] = next.tabSize
      prefs[Keys.CODE_FONT] = next.codeFont
      prefs[Keys.CARET_STYLE] = next.caretStyle
      prefs[Keys.FONT_LIGATURES] = next.fontLigatures
      prefs[Keys.WORD_WRAP] = next.wordWrap
      prefs[Keys.SHOW_HORIZONTAL_SCROLLBAR] = next.showHorizontalScrollbar
      prefs[Keys.SHOW_LINE_NUMBERS] = next.showLineNumbers
      prefs[Keys.AUTO_CLOSE_BRACKETS] = next.autoCloseBrackets
      prefs[Keys.STICKY_HEADERS] = next.stickyHeaders
      prefs[Keys.INDENT_GUIDES] = next.indentGuides
      prefs[Keys.CODE_FOLDING] = next.codeFolding
      prefs[Keys.GUTTER_DIFF_MARKERS] = next.gutterDiffMarkers
      prefs[Keys.PINCH_ZOOM_ENABLED] = next.pinchZoomEnabled
      prefs[Keys.SOFT_KEYBOARD_SUGGESTIONS] = next.softKeyboardSuggestions
      prefs[Keys.SHOW_SYMBOL_BAR] = next.showSymbolBar
      prefs[Keys.AUTO_POPUP_COMPLETION] = next.autoPopupCompletion
      prefs[Keys.COMPLETION_DELAY_MS] = next.completionDelayMs
      prefs[Keys.COMPLETION_MAX_ITEMS] = next.completionMaxItems
      prefs[Keys.ANALYZE_ON_THE_FLY] = next.analyzeOnTheFly
      prefs[Keys.REPARSE_DELAY_MS] = next.reparseDelayMs
      prefs[Keys.AUTO_SAVE_ENABLED] = next.autoSaveEnabled
      prefs[Keys.AUTO_SAVE_INTERVAL_SEC] = next.autoSaveIntervalSeconds.coerceIn(3, 60)
      prefs[Keys.FORMAT_ON_SAVE] = next.formatOnSave
      prefs[Keys.CUSTOM_SNIPPETS] = CodeSnippetRegistry.encodeCustomSnippets(next.customSnippets)
      prefs[Keys.DEFAULT_COMPAT_DATE] = next.defaultCompatibilityDate.trim().ifBlank { "2024-11-01" }
      prefs[Keys.DEFAULT_NODEJS_COMPAT] = next.defaultNodejsCompat
      prefs[Keys.DEFAULT_WORKERS_DEV] = next.defaultWorkersDevEnabled
      prefs[Keys.AUTO_OPEN_PREVIEW_ON_DEPLOY] = next.autoOpenPreviewOnDeploy
      prefs[Keys.HOME_BAR_STYLE] = next.homeBarStyle.encode()
      prefs[Keys.EDITOR_BAR_STYLE] = next.editorBarStyle.encode()
      prefs[Keys.HOME_MENU_STYLE] = next.homeMenuStyle.encode()
      prefs[Keys.EDITOR_MENU_STYLE] = next.editorMenuStyle.encode()
      prefs[Keys.SELECTION_DRAG_CONFIG] = next.selectionDragConfig.encode()
      prefs[Keys.USE_UNIFIED_EDITOR_EVERYWHERE] = next.useUnifiedEditorEverywhere
      prefs[Keys.API_CACHE_TTL_HOURS] = next.apiCacheTtlHours.coerceIn(0, 24)
      prefs[Keys.WARN_CLOSE_DIRTY_TAB] = next.warnOnCloseDirtyTab
      prefs[Keys.CLOSE_UNSAVED_POLICY] = next.closeUnsavedPolicy
      prefs[Keys.WARN_LEAVE_DIRTY_WORKSPACE] = next.warnOnLeaveDirtyWorkspace
      prefs[Keys.WARN_CLOUD_OVERWRITE] = next.warnOnCloudOverwrite
    }
  }
}
