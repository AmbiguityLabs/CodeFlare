package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.CloudflareClientFactory
import com.example.data.repository.CloudflareRepository
import com.example.data.security.AuthPreferencesStore
import com.example.data.security.EditorSettings
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

    // Blank compatibility date falls back safely to baseline
    store.updateEditorSettings { it.copy(defaultCompatibilityDate = "   ", fontSizeSp = 30, tabSize = 3) }
    val normalized = store.editorSettingsFlow.first()
    assertEquals("2024-11-01", normalized.defaultCompatibilityDate)
    assertEquals(20, normalized.fontSizeSp)
    assertEquals(2, normalized.tabSize)
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
}
