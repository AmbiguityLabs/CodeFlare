package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import kotlin.math.roundToInt

/**
 * Snapshot of physical device hardware & OS motion settings used to calibrate
 * the Codeflare animated emblem playback speed proportionally without
 * altering choreography or relative phase timings.
 */
data class DeviceHardwareProfile(
  val cpuCores: Int,
  val totalRamBytes: Long,
  val isLowRamDevice: Boolean,
  val refreshRateHz: Float,
  val is64Bit: Boolean,
  val mediaPerformanceClass: Int,
  val isPowerSaveMode: Boolean,
  val animatorDurationScale: Float,
) {
  val totalRamGb: Double
    get() = totalRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

  val isReducedMotionPreferred: Boolean
    get() = animatorDurationScale <= 0f
}

object HardwareSpeedProfiler {
  const val DEFAULT_BASE_SEQUENCE_DURATION_MS = 7200L
  const val MIN_SPEED_MULTIPLIER = 1.0f
  const val MAX_SPEED_MULTIPLIER = 1.95f

  fun inspectDevice(context: Context): DeviceHardwareProfile {
    val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    val totalRamBytes =
      runCatching {
        activityManager?.getMemoryInfo(memInfo)
        memInfo.totalMem.coerceAtLeast(0L)
      }.getOrDefault(0L)
    val isLowRam = runCatching { activityManager?.isLowRamDevice == true }.getOrDefault(false)

    val refreshRateHz =
      runCatching {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        display?.refreshRate?.takeIf { it.isFinite() && it > 1f } ?: 60f
      }.getOrDefault(60f)

    val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    val perfClass =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.VERSION.MEDIA_PERFORMANCE_CLASS
      } else {
        0
      }

    val isPowerSaveMode =
      runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isPowerSaveMode == true
      }.getOrDefault(false)

    val animatorScale =
      runCatching {
        Settings.Global.getFloat(
          context.contentResolver,
          Settings.Global.ANIMATOR_DURATION_SCALE,
          1.0f,
        )
      }.getOrDefault(1.0f)

    return DeviceHardwareProfile(
      cpuCores = cpuCores,
      totalRamBytes = totalRamBytes,
      isLowRamDevice = isLowRam,
      refreshRateHz = refreshRateHz,
      is64Bit = is64Bit,
      mediaPerformanceClass = perfClass,
      isPowerSaveMode = isPowerSaveMode,
      animatorDurationScale = animatorScale,
    )
  }

  /**
   * Computes a deterministic hardware-calibrated speed multiplier in [1.0, 1.95].
   * Higher-tier CPUs, larger memory capacities, 64-bit ABIs, 90/120Hz+ displays,
   * and Android Media Performance Class devices play the complex SVG filter &
   * multi-dot choreography crisply faster while low-RAM or battery-saver devices
   * remain at 1.0x baseline speed.
   */
  fun computeSpeedMultiplier(profile: DeviceHardwareProfile): Float {
    if (profile.isLowRamDevice || profile.isPowerSaveMode) {
      return MIN_SPEED_MULTIPLIER
    }

    var bonus = 0f

    bonus +=
      when {
        profile.cpuCores >= 8 -> 0.28f
        profile.cpuCores >= 6 -> 0.16f
        profile.cpuCores >= 4 -> 0.05f
        else -> 0f
      }

    val ramGb = profile.totalRamGb
    bonus +=
      when {
        ramGb >= 11.0 -> 0.32f
        ramGb >= 7.5 -> 0.22f
        ramGb >= 5.5 -> 0.12f
        ramGb >= 3.5 -> 0.04f
        else -> 0f
      }

    bonus +=
      when {
        profile.refreshRateHz >= 115f -> 0.20f
        profile.refreshRateHz >= 85f -> 0.10f
        else -> 0f
      }

    if (profile.is64Bit) {
      bonus += 0.05f
    }

    bonus +=
      when {
        profile.mediaPerformanceClass >= 33 -> 0.10f
        profile.mediaPerformanceClass >= 31 -> 0.05f
        else -> 0f
      }

    val clamped = (MIN_SPEED_MULTIPLIER + bonus).coerceIn(MIN_SPEED_MULTIPLIER, MAX_SPEED_MULTIPLIER)
    return (clamped * 100f).roundToInt() / 100f
  }

  fun computeSequenceDurationMs(speedMultiplier: Float): Long {
    val safeSpeed = speedMultiplier.coerceIn(0.5f, 3.0f)
    return (DEFAULT_BASE_SEQUENCE_DURATION_MS / safeSpeed).roundToInt().toLong().coerceAtLeast(1200L)
  }
}
