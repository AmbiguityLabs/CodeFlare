package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.CloudflareClientFactory
import com.example.data.repository.CloudflareRepository
import com.example.data.security.AuthPreferencesStore
import com.example.data.security.DefaultEditorDragPolicy
import com.example.data.security.EditorSettings
import com.example.data.security.FloatingBarStyleConfig
import com.example.data.security.PopupMenuStyleConfig
import com.example.data.security.SelectionDragConfig
import com.example.data.security.SmartSelectionHoldMode
import com.example.editor.selection.SmartSelectionInitialDepth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
