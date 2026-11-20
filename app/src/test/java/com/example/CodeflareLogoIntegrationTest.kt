package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.buildCodeflareLogoAssetUrl
import com.example.util.DeviceHardwareProfile
import com.example.util.HardwareSpeedProfiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CodeflareLogoIntegrationTest {

  @Test
  fun `low ram and power saver profiles stay at baseline 1x`() {
    val lowRamProfile =
      DeviceHardwareProfile(
        cpuCores = 8,
        totalRamBytes = 8L * 1024L * 1024L * 1024L,
        isLowRamDevice = true,
        refreshRateHz = 120f,
        is64Bit = true,
        mediaPerformanceClass = 33,
        isPowerSaveMode = false,
        animatorDurationScale = 1.0f,
      )
    val powerSaverProfile = lowRamProfile.copy(isLowRamDevice = false, isPowerSaveMode = true)

    assertEquals(1.0f, HardwareSpeedProfiler.computeSpeedMultiplier(lowRamProfile), 0.001f)
    assertEquals(1.0f, HardwareSpeedProfiler.computeSpeedMultiplier(powerSaverProfile), 0.001f)
  }

  @Test
  fun `speed multiplier scales monotonically across hardware tiers within bounds`() {
    val entry =
      DeviceHardwareProfile(
        cpuCores = 4,
        totalRamBytes = 3L * 1024L * 1024L * 1024L,
        isLowRamDevice = false,
        refreshRateHz = 60f,
        is64Bit = false,
        mediaPerformanceClass = 0,
        isPowerSaveMode = false,
        animatorDurationScale = 1.0f,
      )
    val mid =
      entry.copy(
        cpuCores = 8,
        totalRamBytes = 6L * 1024L * 1024L * 1024L,
        refreshRateHz = 90f,
        is64Bit = true,
        mediaPerformanceClass = 31,
      )
    val flagship =
      entry.copy(
        cpuCores = 8,
        totalRamBytes = 12L * 1024L * 1024L * 1024L,
        refreshRateHz = 120f,
        is64Bit = true,
        mediaPerformanceClass = 33,
      )

    val entrySpeed = HardwareSpeedProfiler.computeSpeedMultiplier(entry)
    val midSpeed = HardwareSpeedProfiler.computeSpeedMultiplier(mid)
    val flagshipSpeed = HardwareSpeedProfiler.computeSpeedMultiplier(flagship)

    assertTrue(entrySpeed in HardwareSpeedProfiler.MIN_SPEED_MULTIPLIER..HardwareSpeedProfiler.MAX_SPEED_MULTIPLIER)
    assertTrue(entrySpeed < midSpeed)
    assertTrue(midSpeed < flagshipSpeed)
    assertEquals(HardwareSpeedProfiler.MAX_SPEED_MULTIPLIER, flagshipSpeed, 0.001f)
  }

  @Test
  fun `sequence duration scales inversely with speed multiplier`() {
    assertEquals(7200L, HardwareSpeedProfiler.computeSequenceDurationMs(1.0f))
    assertEquals(4800L, HardwareSpeedProfiler.computeSequenceDurationMs(1.5f))
    assertEquals(3692L, HardwareSpeedProfiler.computeSequenceDurationMs(1.95f))
  }

  @Test
  fun `asset url encodes embed theme and clamped speed`() {
    val embedDark = buildCodeflareLogoAssetUrl(darkTheme = true, embedOnly = true, speedMultiplier = 1.65f)
    assertEquals(
      "file:///android_asset/codeflare_logo_flame_top_neon_heat.html?embed=1&theme=dark&speed=1.65",
      embedDark,
    )

    val showcaseLightClamped = buildCodeflareLogoAssetUrl(darkTheme = false, embedOnly = false, speedMultiplier = 9.0f)
    assertEquals(
      "file:///android_asset/codeflare_logo_flame_top_neon_heat.html?embed=0&theme=light&speed=3.00",
      showcaseLightClamped,
    )
  }

  @Test
  fun `isExternalFileLaunchIntent detects file actions only`() {
    assertTrue(isExternalFileLaunchIntent(Intent(Intent.ACTION_VIEW)))
    assertTrue(isExternalFileLaunchIntent(Intent(Intent.ACTION_EDIT)))
    assertTrue(isExternalFileLaunchIntent(Intent(Intent.ACTION_SEND)))
    assertTrue(isExternalFileLaunchIntent(Intent(Intent.ACTION_SEND_MULTIPLE)))
    assertFalse(isExternalFileLaunchIntent(Intent(Intent.ACTION_MAIN)))
    assertFalse(isExternalFileLaunchIntent(null))
  }

  @Test
  fun `isLauncherClickIntent detects launcher clicks only`() {
    val launcherIntent =
      Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
      }
    assertTrue(isLauncherClickIntent(launcherIntent))
    assertFalse(isLauncherClickIntent(Intent(Intent.ACTION_VIEW)))
    assertFalse(isLauncherClickIntent(Intent(Intent.ACTION_MAIN)))
    assertFalse(isLauncherClickIntent(null))
  }

  @Test
  fun `compressed static codeflare icons and boot emblem resources exist`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val res = context.resources
    assertTrue(res.getIdentifier("ic_launcher_foreground", "drawable", context.packageName) != 0)
    assertTrue(res.getIdentifier("ic_launcher_monochrome", "drawable", context.packageName) != 0)
    assertTrue(res.getIdentifier("codeflare_boot_emblem", "drawable", context.packageName) != 0)
    assertTrue(res.getIdentifier("img_empty_workspace", "drawable", context.packageName) != 0)
    assertTrue(res.getIdentifier("app_icon_foreground", "drawable", context.packageName) != 0)
    assertEquals(0xFF0B0D1E.toInt(), context.getColor(R.color.codeflare_boot_bg))
  }

  @Test
  fun `packaged html logo asset has single html root intact artwork and speed hooks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val html =
      context.assets.open("codeflare_logo_flame_top_neon_heat.html").bufferedReader().use {
        it.readText()
      }

    assertEquals(1, Regex("<!DOCTYPE html>", RegexOption.IGNORE_CASE).findAll(html).count())
    assertEquals(1, Regex("</html>", RegexOption.IGNORE_CASE).findAll(html).count())
    assertFalse(html.contains(":root{color-scheme:light;"))

    assertTrue(html.contains("Flame Top: Neon Heat"))
    assertTrue(html.contains("viewBox=\"0 0 1228 1281\""))
    assertTrue(html.contains("id=\"top\""))
    assertTrue(html.contains("id=\"rv\""))
    assertTrue(html.contains("id=\"fire\""))
    assertTrue(html.contains("transform-origin:601px 652px"))
    assertTrue(html.contains("data:image/jpeg;base64,"))
    assertTrue(html.contains("class=\"dg\""))
    assertTrue(html.contains("class=\"dt\""))

    assertTrue(html.contains("applyHardwareSpeed"))
    assertTrue(html.contains("window.setHardwareSpeed"))
    assertTrue(html.contains("window.setHostTheme"))
    assertTrue(html.contains("unpauseAnimations"))
    assertTrue(html.contains("setCurrentTime"))
  }
}
