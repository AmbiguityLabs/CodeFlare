package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.CloudflareClientFactory
import com.example.data.repository.CloudflareRepository
import com.example.data.security.AuthPreferencesStore
import com.example.data.security.DefaultEditorDragPolicy
import com.example.data.security.EditorSettings
import com.example.data.security.FloatingBarStyleConfig
import com.example.data.security.IconLabelBehavior
import com.example.data.security.PopupMenuStyleConfig
import com.example.data.security.SelectionDragConfig
import com.example.data.security.SmartSelectionHoldMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.remote.WorkerFile
import com.example.editor.canvas.computeSelectionToolbarTopPx
import com.example.editor.selection.SmartSelectionInitialDepth
import com.example.ui.screens.settings.parseHexColor
import com.example.ui.theme.CaAccent
import com.example.ui.theme.CodeStylePreset
import com.example.ui.theme.EditorThemePreset
import com.example.ui.theme.UiStyleVersion
import com.example.ui.theme.bridgedTo
import com.example.ui.theme.computeLiquidGlassParams
import com.example.ui.theme.expressiveColorScheme
import com.example.ui.theme.expressiveColorSchemeFromSeed
import com.example.ui.theme.paletteFor
import com.example.ui.viewmodel.hasAnyNonSpacingWorkspaceDiff
import com.example.ui.viewmodel.isSpacingOnlyChange
import com.example.ui.viewmodel.shouldAutoSaveEdit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsAndPreferencesTest {

  @Test
  fun editorSettingsDefaultsMatchExpectedDeploymentBaselines() {
    val defaults = EditorSettings()
    assertTrue(defaults.darkTheme)
    assertFalse(defaults.dynamicColor)
    assertEquals(13, defaults.fontSizeSp)
    assertEquals(2, defaults.tabSize)
    assertFalse(defaults.wordWrap)
    assertTrue(defaults.showLineNumbers)
    assertTrue(defaults.autoCloseBrackets)
    assertEquals("2024-11-01", defaults.defaultCompatibilityDate)
    assertFalse(defaults.defaultNodejsCompat)
    assertTrue(defaults.defaultWorkersDevEnabled)
    assertFalse(defaults.autoOpenPreviewOnDeploy)
    assertTrue(defaults.autoSaveEnabled)
    assertEquals(5, defaults.autoSaveIntervalSeconds)
    assertEquals("V1", defaults.uiStyleVersion)
    assertEquals(IconLabelBehavior.REVEAL_ON_TAP, defaults.iconLabelBehavior)
  }

  @Test
  fun updateEditorSettingsPersistsDeploymentDefaultsAndPreferences() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    store.updateEditorSettings {
      it.copy(
        darkTheme = false,
        dynamicColor = true,
        fontSizeSp = 16,
        tabSize = 4,
        wordWrap = true,
        showLineNumbers = false,
        autoCloseBrackets = false,
        defaultCompatibilityDate = "2026-01-01",
        defaultNodejsCompat = true,
        defaultWorkersDevEnabled = false,
        autoOpenPreviewOnDeploy = true,
        autoSaveEnabled = false,
        autoSaveIntervalSeconds = 15,
        uiStyleVersion = "V2",
      )
    }

    val loaded = store.editorSettingsFlow.first()
    assertFalse(loaded.darkTheme)
    assertTrue(loaded.dynamicColor)
    assertEquals(16, loaded.fontSizeSp)
    assertEquals(4, loaded.tabSize)
    assertTrue(loaded.wordWrap)
    assertFalse(loaded.showLineNumbers)
    assertFalse(loaded.autoCloseBrackets)
    assertEquals("2026-01-01", loaded.defaultCompatibilityDate)
    assertTrue(loaded.defaultNodejsCompat)
    assertFalse(loaded.defaultWorkersDevEnabled)
    assertTrue(loaded.autoOpenPreviewOnDeploy)
    assertFalse(loaded.autoSaveEnabled)
    assertEquals(15, loaded.autoSaveIntervalSeconds)
    assertEquals("V2", loaded.uiStyleVersion)

    // Blank compatibility date falls back safely to baseline and intervals clamp to 3..60s
    store.updateEditorSettings {
      it.copy(
        defaultCompatibilityDate = "   ",
        fontSizeSp = 30,
        tabSize = 3,
        autoSaveEnabled = true,
        autoSaveIntervalSeconds = 1,
      )
    }
    val normalizedLow = store.editorSettingsFlow.first()
    assertEquals("2024-11-01", normalizedLow.defaultCompatibilityDate)
    assertEquals(20, normalizedLow.fontSizeSp)
    assertEquals(2, normalizedLow.tabSize)
    assertTrue(normalizedLow.autoSaveEnabled)
    assertEquals(3, normalizedLow.autoSaveIntervalSeconds)

    store.updateEditorSettings { it.copy(autoSaveIntervalSeconds = 999) }
    val normalizedHigh = store.editorSettingsFlow.first()
    assertEquals(60, normalizedHigh.autoSaveIntervalSeconds)
  }

  @Test
  fun credentialVerificationAndUpdatesFailCleanlyOnEmptyInputOrMissingSession() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)
    val repository = CloudflareRepository(store, CloudflareClientFactory(store))

    val verifyResult = repository.verifyActiveSession()
    assertTrue(verifyResult.isFailure)
    assertEquals("No Cloudflare credentials configured", verifyResult.exceptionOrNull()?.message)

    val emptyTokenResult = repository.updateApiTokenCredentials("   ")
    assertTrue(emptyTokenResult.isFailure)
    assertEquals("Token cannot be empty", emptyTokenResult.exceptionOrNull()?.message)

    val emptyGlobalResult = repository.updateGlobalKeyCredentials("", "abc")
    assertTrue(emptyGlobalResult.isFailure)
    assertEquals("Email and Global API Key are required", emptyGlobalResult.exceptionOrNull()?.message)
  }

  @Test
  fun homeAndEditorBarAndMenuStylesPersistAndClampCleanly() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    val customHomeBar =
      FloatingBarStyleConfig(
        heightDp = 66,
        widthPercent = 74,
        iconSpacingDp = 28,
        iconSizeDp = 24,
        opacity = 0.62f,
        borderOpacity = 0.55f,
        cornerRadiusDp = 36,
        bottomMarginDp = 18,
        activePillOpacity = 0.34f,
        showLabels = true,
        showBadges = false,
      )
    val customEditorBar =
      FloatingBarStyleConfig(
        heightDp = 50,
        widthPercent = 92,
        iconSpacingDp = 10,
        iconSizeDp = 19,
        opacity = 0.88f,
        borderOpacity = 0.20f,
        cornerRadiusDp = 22,
        bottomMarginDp = 6,
        activePillOpacity = 0.18f,
        showLabels = false,
        showBadges = true,
      )
    val customHomeMenu =
      PopupMenuStyleConfig(
        widthDp = 284,
        itemHeightDp = 46,
        iconSpacingDp = 14,
        iconSizeDp = 18,
        opacity = 0.82f,
        borderOpacity = 0.48f,
        cornerRadiusDp = 20,
      )
    val customEditorMenu =
      PopupMenuStyleConfig(
        widthDp = 212,
        itemHeightDp = 36,
        iconSpacingDp = 8,
        iconSizeDp = 15,
        opacity = 0.90f,
        borderOpacity = 0.25f,
        cornerRadiusDp = 12,
      )

    store.updateEditorSettings {
      it.copy(
        homeBarStyle = customHomeBar,
        editorBarStyle = customEditorBar,
        homeMenuStyle = customHomeMenu,
        editorMenuStyle = customEditorMenu,
      )
    }

    val loaded = store.editorSettingsFlow.first()
    assertEquals(customHomeBar, loaded.homeBarStyle)
    assertEquals(customEditorBar, loaded.editorBarStyle)
    assertEquals(customHomeMenu, loaded.homeMenuStyle)
    assertEquals(customEditorMenu, loaded.editorMenuStyle)

    // Verify out-of-range values clamp safely on normalize & decode
    val clampedBar =
      FloatingBarStyleConfig(
        heightDp = 120,
        widthPercent = 10,
        iconSpacingDp = 99,
        iconSizeDp = 4,
        opacity = 2.0f,
        borderOpacity = -0.5f,
        cornerRadiusDp = 100,
        bottomMarginDp = -10,
        activePillOpacity = 1.5f,
      ).normalized()
    assertEquals(78, clampedBar.heightDp)
    assertEquals(52, clampedBar.widthPercent)
    assertEquals(40, clampedBar.iconSpacingDp)
    assertEquals(16, clampedBar.iconSizeDp)
    assertEquals(1.0f, clampedBar.opacity, 0.001f)
    assertEquals(0.0f, clampedBar.borderOpacity, 0.001f)
    assertEquals(40, clampedBar.cornerRadiusDp)
    assertEquals(0, clampedBar.bottomMarginDp)
    assertEquals(0.60f, clampedBar.activePillOpacity, 0.001f)

    val decodedFallback = FloatingBarStyleConfig.decode("corrupted", FloatingBarStyleConfig.HOME_DEFAULT)
    assertEquals(FloatingBarStyleConfig.HOME_DEFAULT, decodedFallback)

    val clampedMenu =
      PopupMenuStyleConfig(
        widthDp = 999,
        itemHeightDp = 5,
        iconSpacingDp = 99,
        iconSizeDp = 40,
        opacity = 0.01f,
        borderOpacity = 2.0f,
        cornerRadiusDp = 1,
      ).normalized()
    assertEquals(320, clampedMenu.widthDp)
    assertEquals(32, clampedMenu.itemHeightDp)
    assertEquals(20, clampedMenu.iconSpacingDp)
    assertEquals(22, clampedMenu.iconSizeDp)
    assertEquals(0.35f, clampedMenu.opacity, 0.001f)
    assertEquals(0.75f, clampedMenu.borderOpacity, 0.001f)
    assertEquals(8, clampedMenu.cornerRadiusDp)
  }

  @Test
  fun selectionDragConfigPersistsRoundTripClampsAndFallsBackCleanly() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    val custom =
      SelectionDragConfig(
        holdMode = SmartSelectionHoldMode.PROGRESSIVE_HOLD,
        initialDepth = SmartSelectionInitialDepth.BROAD,
        defaultEditorDragMode = DefaultEditorDragPolicy.ALWAYS_COPY,
        immediateSlideToDrag = false,
        holdDelayMs = 450,
        allowCrossAppDrag = false,
        showScopeBadge = false,
        showExpandShrink = true,
        showDragGrip = false,
        showModeToggle = true,
        showDropTargetLoupe = false,
        showQuickActions = true,
        showCodeActions = false,
      )

    store.updateEditorSettings { it.copy(selectionDragConfig = custom) }
    val loaded = store.editorSettingsFlow.first().selectionDragConfig
    assertEquals(custom, loaded)
    assertEquals(3, loaded.enabledOverlayCount)

    // Out-of-range holdDelayMs clamps cleanly to 200..600ms
    assertEquals(200, custom.copy(holdDelayMs = 50).normalized().holdDelayMs)
    assertEquals(600, custom.copy(holdDelayMs = 1800).normalized().holdDelayMs)

    // Corrupted/incomplete serialized payload falls back safely
    assertEquals(SelectionDragConfig.DEFAULT, SelectionDragConfig.decode("bad|payload"))
    val partialValidDecode = SelectionDragConfig.decode("CLASSIC_WORD|COMPACT|ALWAYS_MOVE|0|240|0|0|0|0|0|0|0|0")
    assertEquals(SmartSelectionHoldMode.CLASSIC_WORD, partialValidDecode.holdMode)
    assertEquals(SmartSelectionInitialDepth.COMPACT, partialValidDecode.initialDepth)
    assertEquals(DefaultEditorDragPolicy.ALWAYS_MOVE, partialValidDecode.defaultEditorDragMode)
    assertFalse(partialValidDecode.immediateSlideToDrag)
    assertEquals(240, partialValidDecode.holdDelayMs)
    assertFalse(partialValidDecode.allowCrossAppDrag)
    assertEquals(0, partialValidDecode.enabledOverlayCount)
  }

  @Test
  fun iconLabelBehaviorPersistsAndFallsBackCleanly() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    assertEquals(IconLabelBehavior.REVEAL_ON_TAP, EditorSettings().iconLabelBehavior)
    assertEquals(IconLabelBehavior.REVEAL_ON_TAP, IconLabelBehavior.fromKey(null))
    assertEquals(IconLabelBehavior.REVEAL_ON_TAP, IconLabelBehavior.fromKey(""))
    assertEquals(IconLabelBehavior.REVEAL_ON_TAP, IconLabelBehavior.fromKey("UNKNOWN_MODE"))
    assertEquals(IconLabelBehavior.ICON_ONLY, IconLabelBehavior.fromKey("icon_only"))
    assertEquals(IconLabelBehavior.ALWAYS_SHOW, IconLabelBehavior.fromKey("Always_Show"))
    assertEquals(IconLabelBehavior.REVEAL_ON_TAP, IconLabelBehavior.fromKey("reveal_on_tap"))

    for (mode in listOf(
      IconLabelBehavior.ICON_ONLY,
      IconLabelBehavior.ALWAYS_SHOW,
      IconLabelBehavior.REVEAL_ON_TAP,
    )) {
      store.updateEditorSettings { it.copy(iconLabelBehavior = mode) }
      val loaded = store.editorSettingsFlow.first()
      assertEquals(mode, loaded.iconLabelBehavior)
    }
  }

  @Test
  fun unifiedEditorApiCacheTtlAndUnsavedWarningPrefsPersistAndClampCleanly() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    val defaults = EditorSettings()
    assertTrue(defaults.useUnifiedEditorEverywhere)
    assertEquals(4, defaults.apiCacheTtlHours)
    assertTrue(defaults.warnOnCloseDirtyTab)
    assertTrue(defaults.warnOnLeaveDirtyWorkspace)
    assertTrue(defaults.warnOnCloudOverwrite)

    store.updateEditorSettings {
      it.copy(
        useUnifiedEditorEverywhere = false,
        apiCacheTtlHours = 0, // Manual Only
        warnOnCloseDirtyTab = false,
        warnOnLeaveDirtyWorkspace = true,
        warnOnCloudOverwrite = false,
      )
    }
    val manual = store.editorSettingsFlow.first()
    assertFalse(manual.useUnifiedEditorEverywhere)
    assertEquals(0, manual.apiCacheTtlHours)
    assertFalse(manual.warnOnCloseDirtyTab)
    assertTrue(manual.warnOnLeaveDirtyWorkspace)
    assertFalse(manual.warnOnCloudOverwrite)

    store.updateEditorSettings { it.copy(apiCacheTtlHours = 999) }
    val clampedHigh = store.editorSettingsFlow.first()
    assertEquals(24, clampedHigh.apiCacheTtlHours)

    store.updateEditorSettings { it.copy(apiCacheTtlHours = -5) }
    val clampedLow = store.editorSettingsFlow.first()
    assertEquals(0, clampedLow.apiCacheTtlHours)
  }

  @Test
  fun expressiveThemeModeAccentAndCustomSeedPersistAndSyncCleanly() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    val defaults = EditorSettings()
    assertEquals(EditorSettings.THEME_DARK, defaults.themeMode)
    assertEquals(EditorSettings.ACCENT_LIME, defaults.accent)
    assertEquals(EditorSettings.DEFAULT_ACCENT_COLOR, defaults.accentColor)
    assertTrue(defaults.resolveDarkTheme(systemIsDark = false))

    // System mode tracks system dark flag dynamically
    store.updateEditorSettings { it.copy(themeMode = EditorSettings.THEME_SYSTEM) }
    val systemSettings = store.editorSettingsFlow.first()
    assertEquals(EditorSettings.THEME_SYSTEM, systemSettings.normalizedThemeMode())
    assertFalse(systemSettings.resolveDarkTheme(systemIsDark = false))
    assertTrue(systemSettings.resolveDarkTheme(systemIsDark = true))

    // Expressive accent + custom hex seed round-trip & dynamic flag sync
    val customSeed = 0xFF3DDC84L
    store.updateEditorSettings {
      it.copy(
        themeMode = EditorSettings.THEME_LIGHT,
        accent = EditorSettings.ACCENT_CUSTOM,
        accentColor = customSeed,
      )
    }
    val customLoaded = store.editorSettingsFlow.first()
    assertEquals(EditorSettings.THEME_LIGHT, customLoaded.themeMode)
    assertFalse(customLoaded.darkTheme)
    assertFalse(customLoaded.resolveDarkTheme(systemIsDark = true))
    assertEquals(EditorSettings.ACCENT_CUSTOM, customLoaded.accent)
    assertFalse(customLoaded.dynamicColor)
    assertEquals(customSeed, customLoaded.accentColor)

    // Dynamic accent sets dynamicColor = true cleanly
    store.updateEditorSettings { it.copy(accent = EditorSettings.ACCENT_DYNAMIC) }
    val dynamicLoaded = store.editorSettingsFlow.first()
    assertEquals(EditorSettings.ACCENT_DYNAMIC, dynamicLoaded.accent)
    assertTrue(dynamicLoaded.dynamicColor)

    // Selecting expressive preset clears dynamic flag & persists accent
    for (presetAccent in CaAccent.entries) {
      store.updateEditorSettings { it.copy(accent = presetAccent.key) }
      val loaded = store.editorSettingsFlow.first()
      assertEquals(presetAccent.key, loaded.accent)
      assertFalse(loaded.dynamicColor)
    }
  }

  @Test
  fun expressiveSeedSchemeAndHexParserProduceValidDistinctSchemes() {
    val parsed6 = parseHexColor("#3DDC84")
    val parsedNoHash = parseHexColor("8b5cf6")
    val invalid = parseHexColor("not-a-color")
    assertNotNull(parsed6)
    assertNotNull(parsedNoHash)
    assertNull(invalid)
    assertEquals(Color(0xFF3DDC84L), parsed6)
    assertEquals(Color(0xFF8B5CF6L), parsedNoHash)

    val darkSeedScheme = expressiveColorSchemeFromSeed(parsed6!!, dark = true)
    val lightSeedScheme = expressiveColorSchemeFromSeed(parsed6, dark = false)
    assertNotEquals(darkSeedScheme.background, lightSeedScheme.background)
    assertNotEquals(darkSeedScheme.primary, lightSeedScheme.primary)
    assertNotEquals(darkSeedScheme.surfaceContainer, lightSeedScheme.surfaceContainer)

    for (accent in CaAccent.entries) {
      val darkPalette = expressiveColorScheme(accent, dark = true)
      val lightPalette = expressiveColorScheme(accent, dark = false)
      assertNotEquals(darkPalette.primary, lightPalette.primary)
      assertNotEquals(darkPalette.surface, lightPalette.surface)
    }
  }

  @Test
  fun codeAssistEditorCompletionAnalysisAndStartupSettingsPersistAndNormalizeCleanly() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    val defaults = EditorSettings()
    assertTrue(defaults.reopenLastProject)
    assertEquals(EditorSettings.CODE_FONT_JETBRAINS, defaults.codeFont)
    assertEquals(EditorSettings.CARET_LINE, defaults.caretStyle)
    assertTrue(defaults.fontLigatures)
    assertTrue(defaults.showHorizontalScrollbar)
    assertTrue(defaults.pinchZoomEnabled)
    assertTrue(defaults.showSymbolBar)
    assertTrue(defaults.autoPopupCompletion)
    assertEquals(110, defaults.completionDelayMs)
    assertEquals(50, defaults.completionMaxItems)
    assertTrue(defaults.analyzeOnTheFly)
    assertEquals(300, defaults.reparseDelayMs)
    assertEquals(EditorSettings.CLOSE_UNSAVED_ASK, defaults.closeUnsavedPolicy)

    store.updateEditorSettings {
      it.copy(
        reopenLastProject = false,
        codeFont = EditorSettings.CODE_FONT_MONOSPACE,
        caretStyle = EditorSettings.CARET_CANDLE,
        fontLigatures = false,
        showHorizontalScrollbar = false,
        pinchZoomEnabled = false,
        showSymbolBar = false,
        autoPopupCompletion = false,
        completionDelayMs = 240,
        completionMaxItems = 120,
        analyzeOnTheFly = false,
        reparseDelayMs = 650,
        closeUnsavedPolicy = EditorSettings.CLOSE_UNSAVED_DISCARD,
      )
    }

    val loaded = store.editorSettingsFlow.first()
    assertFalse(loaded.reopenLastProject)
    assertEquals(EditorSettings.CODE_FONT_MONOSPACE, loaded.codeFont)
    assertEquals(EditorSettings.CARET_CANDLE, loaded.caretStyle)
    assertFalse(loaded.fontLigatures)
    assertFalse(loaded.showHorizontalScrollbar)
    assertFalse(loaded.pinchZoomEnabled)
    assertFalse(loaded.showSymbolBar)
    assertFalse(loaded.autoPopupCompletion)
    assertEquals(240, loaded.completionDelayMs)
    assertEquals(120, loaded.completionMaxItems)
    assertFalse(loaded.analyzeOnTheFly)
    assertEquals(650, loaded.reparseDelayMs)
    assertEquals(EditorSettings.CLOSE_UNSAVED_DISCARD, loaded.closeUnsavedPolicy)
    // Switching policy to DISCARD keeps warnOnCloseDirtyTab synchronized to false
    assertFalse(loaded.warnOnCloseDirtyTab)

    // Out-of-bounds delays/max items clamp and unknown keys normalize safely
    store.updateEditorSettings {
      it.copy(
        codeFont = "comic_sans",
        caretStyle = "rainbow",
        completionDelayMs = 5000,
        completionMaxItems = 2,
        reparseDelayMs = -100,
        closeUnsavedPolicy = "unknown",
        warnOnCloseDirtyTab = true,
      )
    }
    val clamped = store.editorSettingsFlow.first()
    assertEquals(EditorSettings.CODE_FONT_JETBRAINS, clamped.normalizedCodeFont())
    assertEquals(EditorSettings.CARET_LINE, clamped.normalizedCaretStyle())
    assertEquals(EditorSettings.MAX_COMPLETION_DELAY_MS, clamped.completionDelayMs)
    assertEquals(EditorSettings.MIN_COMPLETION_MAX_ITEMS, clamped.completionMaxItems)
    assertEquals(EditorSettings.MIN_REPARSE_DELAY_MS, clamped.reparseDelayMs)
    assertEquals(EditorSettings.CLOSE_UNSAVED_ASK, clamped.normalizedCloseUnsavedPolicy())

    // Toggling warnOnCloseDirtyTab off syncs closeUnsavedPolicy to SAVE
    store.updateEditorSettings { it.copy(warnOnCloseDirtyTab = false) }
    val syncedSave = store.editorSettingsFlow.first()
    assertFalse(syncedSave.warnOnCloseDirtyTab)
    assertEquals(EditorSettings.CLOSE_UNSAVED_SAVE, syncedSave.closeUnsavedPolicy)

    // Toggling warnOnCloseDirtyTab back on syncs closeUnsavedPolicy to ASK
    store.updateEditorSettings { it.copy(warnOnCloseDirtyTab = true) }
    val syncedAsk = store.editorSettingsFlow.first()
    assertTrue(syncedAsk.warnOnCloseDirtyTab)
    assertEquals(EditorSettings.CLOSE_UNSAVED_ASK, syncedAsk.closeUnsavedPolicy)
  }

  @Test
  fun codeStylePresetAndMinimalSelectionDefaultsWorkAndPersistCleanly() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    // Minimal selection defaults keep clutter hidden while keeping quick actions + loupe
    val minDefault = SelectionDragConfig.DEFAULT
    assertEquals(2, minDefault.enabledOverlayCount)
    assertTrue(minDefault.showQuickActions)
    assertTrue(minDefault.showDropTargetLoupe)
    assertFalse(minDefault.showScopeBadge)
    assertFalse(minDefault.showExpandShrink)
    assertFalse(minDefault.showDragGrip)
    assertFalse(minDefault.showModeToggle)
    assertFalse(minDefault.showCodeActions)
    assertEquals(7, SelectionDragConfig.SMART_POWER.enabledOverlayCount)

    // Reference code style presets apply distinct syntax & canvas tones cleanly
    val neonPalette =
      paletteFor(
        darkTheme = true,
        preset = EditorThemePreset.OLIVE_OBSIDIAN,
        codeStyle = CodeStylePreset.NEON_QUERY,
        codeStyleOverrideCanvas = true,
      )
    assertEquals(CodeStylePreset.NEON_QUERY.canvasBg, neonPalette.editorBg)
    assertEquals(CodeStylePreset.NEON_QUERY.keyword, neonPalette.syntaxKeyword)
    assertEquals(CodeStylePreset.NEON_QUERY.function, neonPalette.syntaxFunction)

    val monokaiSyntaxOnly =
      paletteFor(
        darkTheme = true,
        preset = EditorThemePreset.OLIVE_OBSIDIAN,
        codeStyle = CodeStylePreset.STUDIO_MONOKAI,
        codeStyleOverrideCanvas = false,
      )
    assertNotEquals(CodeStylePreset.STUDIO_MONOKAI.canvasBg, monokaiSyntaxOnly.editorBg)
    assertEquals(CodeStylePreset.STUDIO_MONOKAI.property, monokaiSyntaxOnly.syntaxProperty)

    // Persistence + normalization
    store.updateEditorSettings {
      it.copy(
        codeStylePreset = CodeStylePreset.STUDIO_MONOKAI.key,
        codeStyleOverrideCanvas = false,
      )
    }
    val loaded = store.editorSettingsFlow.first()
    assertEquals(CodeStylePreset.STUDIO_MONOKAI.key, loaded.codeStylePreset)
    assertFalse(loaded.codeStyleOverrideCanvas)

    store.updateEditorSettings { it.copy(codeStylePreset = "NON_EXISTENT") }
    val normalized = store.editorSettingsFlow.first()
    assertEquals(EditorSettings.CODE_STYLE_DEFAULT, normalized.normalizedCodeStylePreset())
    assertEquals(CodeStylePreset.THEME_DEFAULT, CodeStylePreset.fromKey("non_existent"))
  }

  @Test
  fun autoSaveIgnoresSpacingOnlyChangesAndSavesNonSpacingChangesImmediately() {
    // Pure whitespace/newline/tab changes are ignored
    assertTrue(isSpacingOnlyChange("const a = 1;", "  const a = 1;\n\t"))
    assertTrue(isSpacingOnlyChange("let x=2;", "let   x=2;"))
    assertTrue(isSpacingOnlyChange("foo\n\nbar", "foo\r\nbar"))
    assertFalse(isSpacingOnlyChange("const a = 1;", "const a = 2;"))
    assertFalse(isSpacingOnlyChange("", "a"))
    assertTrue(isSpacingOnlyChange("  \n\t ", ""))

    // Baseline comparison catches transition from spacing edit back to non-spacing edit
    assertFalse(shouldAutoSaveEdit("const x = 1;", "const x = 1;", "const x = 1;\n  "))
    assertTrue(shouldAutoSaveEdit("const x = 1;", "const x = 1;\n  ", "const x = 1;\n  y"))
    assertFalse(shouldAutoSaveEdit("const x = 1;", "const x = 1;\n", "const x = 1;"))

    // Workspace diff filter checks only non-virtual files against saved baseline
    val files =
      listOf(
        WorkerFile(path = "src/index.js", content = "const x = 1;", originalContent = "const x = 1;"),
        WorkerFile(path = "wrangler.jsonc", content = "{}", originalContent = "{}"),
      )
    val spacingOnlyLive = mapOf("src/index.js" to TextFieldValue("const x = 1;\n   "))
    assertFalse(hasAnyNonSpacingWorkspaceDiff("ws1", files, spacingOnlyLive, emptyMap()))

    val realEditLive = mapOf("src/index.js" to TextFieldValue("const x = 42;"))
    assertTrue(hasAnyNonSpacingWorkspaceDiff("ws1", files, realEditLive, emptyMap()))
    // Once saved baseline matches live non-spacing text, further spacing edits do not re-trigger save
    assertFalse(
      hasAnyNonSpacingWorkspaceDiff(
        workspaceKey = "ws1",
        files = files,
        liveValues = mapOf("src/index.js" to TextFieldValue("const x = 42;\n\t")),
        lastSavedFileTexts = mapOf("ws1::src/index.js" to "const x = 42;"),
      ),
    )
  }

  @Test
  fun dynamicColorSchemeAppliesUniversallyAcrossV1AndV2Styles() {
    val limeScheme = expressiveColorScheme(CaAccent.LIME, dark = true)
    val violetScheme = expressiveColorScheme(CaAccent.VIOLET, dark = true)

    val baseV2Dark = paletteFor(darkTheme = true, preset = EditorThemePreset.MIDNIGHT_NOIR, uiStyle = UiStyleVersion.V2_MINIMAL_OLED)
    val v2Lime = baseV2Dark.bridgedTo(limeScheme, preserveEditorCanvas = true)
    val v2Violet = baseV2Dark.bridgedTo(violetScheme, preserveEditorCanvas = true)
    assertNotEquals(v2Lime.ctaBg, v2Violet.ctaBg)
    assertNotEquals(v2Lime.activePillFg, v2Violet.activePillFg)
    assertNotEquals(v2Lime.activePillBg, v2Violet.activePillBg)

    val baseV1Light = paletteFor(darkTheme = false, preset = EditorThemePreset.LIGHT_PAPER, uiStyle = UiStyleVersion.V1_DEFAULT)
    val v1Lime = baseV1Light.bridgedTo(expressiveColorScheme(CaAccent.LIME, dark = false), preserveEditorCanvas = false)
    val v1Violet = baseV1Light.bridgedTo(expressiveColorScheme(CaAccent.VIOLET, dark = false), preserveEditorCanvas = false)
    assertNotEquals(v1Lime.ctaBg, v1Violet.ctaBg)
    assertNotEquals(v1Lime.chromeSurface, v1Violet.chromeSurface)
  }

  @Test
  fun selectionToolbarPinsAtTopForSelectAllInsteadOfOverlappingBottomCode() {
    val viewportH = 1200f
    // Normal selection in mid-screen floats cleanly above selection start
    val midTop =
      computeSelectionToolbarTopPx(
        anchorY = 320f,
        anchorBottomY = 360f,
        viewportHeightPx = viewportH,
        toolbarAboveOffsetPx = 92f,
        minBelowAnchorPx = 60f,
        belowSelectionGapPx = 28f,
        minTopPx = 16f,
        toolbarReserveBottomPx = 112f,
        isFullDocumentSelected = false,
      )
    assertEquals(228, midTop)

    // Short first-line selection near top places bar safely right below line
    val firstLineBelow =
      computeSelectionToolbarTopPx(
        anchorY = 10f,
        anchorBottomY = 54f,
        viewportHeightPx = viewportH,
        toolbarAboveOffsetPx = 92f,
        minBelowAnchorPx = 60f,
        belowSelectionGapPx = 28f,
        minTopPx = 16f,
        toolbarReserveBottomPx = 112f,
        isFullDocumentSelected = false,
      )
    assertEquals(82, firstLineBelow)

    // Select All spanning full document stays pinned cleanly at minTopPx (never jumps down over bottom code)
    val selectAllTop =
      computeSelectionToolbarTopPx(
        anchorY = 8f,
        anchorBottomY = 1190f,
        viewportHeightPx = viewportH,
        toolbarAboveOffsetPx = 92f,
        minBelowAnchorPx = 60f,
        belowSelectionGapPx = 28f,
        minTopPx = 16f,
        toolbarReserveBottomPx = 112f,
        isFullDocumentSelected = true,
      )
    assertEquals(16, selectAllTop)

    // Even multi-screen partial selection exceeding bottom viewport stays at top instead of overlapping bottom text
    val tallPartialTop =
      computeSelectionToolbarTopPx(
        anchorY = 8f,
        anchorBottomY = 1160f,
        viewportHeightPx = viewportH,
        toolbarAboveOffsetPx = 92f,
        minBelowAnchorPx = 60f,
        belowSelectionGapPx = 28f,
        minTopPx = 16f,
        toolbarReserveBottomPx = 112f,
        isFullDocumentSelected = false,
      )
    assertEquals(16, tallPartialTop)
  }

  @Test
  fun liquidGlassSettingsPersistClampAndSolveOpticsAcrossClearTintedAndLuminance() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    val defaults = EditorSettings()
    assertTrue(defaults.liquidGlassEnabled)
    assertEquals(0.25f, defaults.liquidGlassTint, 0.001f)
    assertTrue(defaults.liquidGlassMotionHighlights)

    store.updateEditorSettings {
      it.copy(
        liquidGlassEnabled = false,
        liquidGlassTint = 2.5f,
        liquidGlassMotionHighlights = false,
      )
    }
    val clampedHigh = store.editorSettingsFlow.first()
    assertFalse(clampedHigh.liquidGlassEnabled)
    assertEquals(1f, clampedHigh.liquidGlassTint, 0.001f)
    assertFalse(clampedHigh.liquidGlassMotionHighlights)

    store.updateEditorSettings {
      it.copy(
        liquidGlassEnabled = true,
        liquidGlassTint = -0.4f,
        liquidGlassMotionHighlights = true,
      )
    }
    val clampedLow = store.editorSettingsFlow.first()
    assertTrue(clampedLow.liquidGlassEnabled)
    assertEquals(0f, clampedLow.liquidGlassTint, 0.001f)
    assertTrue(clampedLow.liquidGlassMotionHighlights)

    // Clear (tint=0) bends stronger than Tinted (tint=1); thick menus (1.65x) bend stronger than pills (1.0x)
    val (clearLens, clearDark) =
      computeLiquidGlassParams(
        tintFraction = 0f,
        thickness = 1.0f,
        backdropLuminance = 0.12f,
        contentDensity = 0.5f,
        paletteIsDark = true,
        emergenceProgress = 1f,
      )
    val (tintedLens, _) =
      computeLiquidGlassParams(
        tintFraction = 1.0f,
        thickness = 1.0f,
        backdropLuminance = 0.12f,
        contentDensity = 0.5f,
        paletteIsDark = true,
        emergenceProgress = 1f,
      )
    val (menuLens, brightPolarityDark) =
      computeLiquidGlassParams(
        tintFraction = 0f,
        thickness = 1.65f,
        backdropLuminance = 0.88f,
        contentDensity = 0.5f,
        paletteIsDark = true,
        emergenceProgress = 1f,
      )
    assertTrue(clearLens > tintedLens)
    assertTrue(menuLens > clearLens)
    assertTrue(clearDark)
    // Bright underlying backdrop flips glass polarity automatically to light glass even on dark theme
    assertFalse(brightPolarityDark)
  }
}
