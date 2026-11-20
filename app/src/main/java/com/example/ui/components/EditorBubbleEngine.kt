package com.example.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.example.data.security.EditorBubbleColorStyle
import com.example.data.security.EditorBubbleConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zero-GC pooled particle simulation for interactive background bubbles inside the code editor.
 *
 * Bubbles spawn at the bottom edge with randomized size and rise duration, drift smoothly upward
 * with subtle horizontal sway, and can be popped immediately via [tryPopAt] with radial burst
 * ripples before seamlessly respawning from below.
 */
@Stable
class EditorBubbleController(
  private val random: Random = Random.Default,
) {
  internal data class BubbleParticle(
    var active: Boolean = false,
    var xNorm: Float = 0.5f,
    var yPx: Float = 0f,
    var radiusPx: Float = 18f,
    var durationSec: Float = 8f,
    var swayAmpPx: Float = 8f,
    var swayFreqRad: Float = 1.2f,
    var swayPhase: Float = 0f,
    var alphaMultiplier: Float = 0.9f,
    var paletteIndex: Int = 0,
    var ageSec: Float = 0f,
  )

  internal data class PopBurst(
    var active: Boolean = false,
    var xPx: Float = 0f,
    var yPx: Float = 0f,
    var startRadiusPx: Float = 16f,
    var progress: Float = 0f,
    var paletteIndex: Int = 0,
  )

  internal data class PopDroplet(
    var active: Boolean = false,
    var originX: Float = 0f,
    var originY: Float = 0f,
    var vxPx: Float = 0f,
    var vyPx: Float = 0f,
    var radiusPx: Float = 3f,
    var progress: Float = 0f,
    var paletteIndex: Int = 0,
  )

  internal val bubbles = Array(MAX_BUBBLES) { BubbleParticle() }
  internal val bursts = Array(MAX_BURSTS) { PopBurst() }
  internal val droplets = Array(MAX_DROPLETS) { PopDroplet() }

  private var lastConfig: EditorBubbleConfig = EditorBubbleConfig.DEFAULT
  private var lastWidthPx: Float = 0f
  private var lastHeightPx: Float = 0f
  private var lastDensity: Float = 1f
  private var activeCount: Int = 0

  var poppedTotalCount: Long = 0L
    private set

  fun updateConfig(
    config: EditorBubbleConfig,
    widthPx: Float,
    heightPx: Float,
    density: Float,
  ) {
    val norm = config.normalized()
    val safeDensity = density.coerceAtLeast(0.5f)
    val countChanged = norm.count != lastConfig.count
    val sizeRangeChanged =
      norm.minSizeDp != lastConfig.minSizeDp || norm.maxSizeDp != lastConfig.maxSizeDp
    val durationChanged =
      norm.minDurationSec != lastConfig.minDurationSec || norm.maxDurationSec != lastConfig.maxDurationSec
    val enabledToggledOn = norm.enabled && !lastConfig.enabled
    val viewportChanged =
      (widthPx > 1f && heightPx > 1f) &&
        (lastWidthPx <= 1f || lastHeightPx <= 1f)

    lastConfig = norm
    lastDensity = safeDensity
    if (widthPx > 1f) lastWidthPx = widthPx
    if (heightPx > 1f) lastHeightPx = heightPx

    if (!norm.enabled) {
      activeCount = 0
      return
    }

    if (lastWidthPx <= 1f || lastHeightPx <= 1f) return

    val target = norm.count.coerceIn(0, MAX_BUBBLES)
    if (enabledToggledOn || viewportChanged) {
      for (i in 0 until target) {
        spawnBubble(
          slot = bubbles[i],
          config = norm,
          heightPx = lastHeightPx,
          density = safeDensity,
          scatterVertically = true,
        )
      }
      for (i in target until MAX_BUBBLES) {
        bubbles[i].active = false
      }
      activeCount = target
      return
    }

    if (countChanged || activeCount != target) {
      if (target > activeCount) {
        for (i in activeCount until target) {
          spawnBubble(
            slot = bubbles[i],
            config = norm,
            heightPx = lastHeightPx,
            density = safeDensity,
            scatterVertically = activeCount == 0,
          )
        }
      } else {
        for (i in target until MAX_BUBBLES) {
          bubbles[i].active = false
        }
      }
      activeCount = target
    }

    if (sizeRangeChanged) {
      val minR = norm.minSizeDp * 0.5f * safeDensity
      val maxR = norm.maxSizeDp * 0.5f * safeDensity
      for (i in 0 until target) {
        val b = bubbles[i]
        if (b.active && (b.radiusPx < minR || b.radiusPx > maxR)) {
          b.radiusPx = randomFloat(minR, maxR)
        }
      }
    }

    if (durationChanged) {
      for (i in 0 until target) {
        val b = bubbles[i]
        if (b.active && (b.durationSec < norm.minDurationSec || b.durationSec > norm.maxDurationSec)) {
          b.durationSec = randomFloat(norm.minDurationSec, norm.maxDurationSec)
        }
      }
    }
  }

  fun advance(
    dtSec: Float,
    widthPx: Float,
    heightPx: Float,
    density: Float,
  ) {
    val cfg = lastConfig
    if (!cfg.enabled) return
    if (widthPx <= 1f || heightPx <= 1f) return

    if (lastWidthPx <= 1f || lastHeightPx <= 1f || activeCount != cfg.count) {
      updateConfig(cfg, widthPx, heightPx, density)
    } else {
      lastWidthPx = widthPx
      lastHeightPx = heightPx
      lastDensity = density.coerceAtLeast(0.5f)
    }

    val clampedDt = dtSec.coerceIn(0f, 0.08f)
    if (clampedDt <= 0f) return

    val limit = activeCount.coerceIn(0, MAX_BUBBLES)
    for (i in 0 until limit) {
      val b = bubbles[i]
      if (!b.active) continue
      val travelSpan = heightPx + b.radiusPx * 2.4f
      val vy = travelSpan / b.durationSec.coerceAtLeast(0.8f)
      b.yPx -= vy * clampedDt
      b.ageSec += clampedDt
      if (b.yPx < -b.radiusPx * 1.35f) {
        spawnBubble(
          slot = b,
          config = cfg,
          heightPx = heightPx,
          density = lastDensity,
          scatterVertically = false,
        )
      }
    }

    val burstDt = clampedDt / BURST_DURATION_SEC
    for (i in 0 until MAX_BURSTS) {
      val burst = bursts[i]
      if (!burst.active) continue
      burst.progress += burstDt
      if (burst.progress >= 1f) {
        burst.active = false
      }
    }

    val dropletDt = clampedDt / DROPLET_DURATION_SEC
    for (i in 0 until MAX_DROPLETS) {
      val d = droplets[i]
      if (!d.active) continue
      d.progress += dropletDt
      if (d.progress >= 1f) {
        d.active = false
      }
    }
  }

  /**
   * Checks if `(tapX, tapY)` hits any visible bubble (with generous tap slop for easy popping).
   * Pops the topmost matching bubble immediately, spawns burst feedback, and respawns from bottom.
   */
  fun tryPopAt(
    tapX: Float,
    tapY: Float,
    extraTouchSlopPx: Float = 12f * lastDensity,
  ): Boolean {
    val cfg = lastConfig
    if (!cfg.enabled || !cfg.tapToPop || lastWidthPx <= 1f || lastHeightPx <= 1f) return false

    val limit = activeCount.coerceIn(0, MAX_BUBBLES)
    for (i in limit - 1 downTo 0) {
      val b = bubbles[i]
      if (!b.active) continue
      val cx = bubbleCenterX(b, lastWidthPx)
      val cy = b.yPx
      val dx = tapX - cx
      val dy = tapY - cy
      val hitRadius = b.radiusPx + extraTouchSlopPx
      if (dx * dx + dy * dy <= hitRadius * hitRadius) {
        if (cfg.popBurstEnabled) {
          spawnPopBurst(cx, cy, b.radiusPx, b.paletteIndex)
        }
        poppedTotalCount += 1L
        spawnBubble(
          slot = b,
          config = cfg,
          heightPx = lastHeightPx,
          density = lastDensity,
          scatterVertically = false,
        )
        return true
      }
    }
    return false
  }

  fun activeBubblesCount(): Int = activeCount

  internal fun bubbleCenterX(
    b: BubbleParticle,
    widthPx: Float,
  ): Float {
    val base = b.xNorm * widthPx
    val sway = sin(b.ageSec * b.swayFreqRad + b.swayPhase) * b.swayAmpPx
    return (base + sway).coerceIn(b.radiusPx * 0.5f, (widthPx - b.radiusPx * 0.5f).coerceAtLeast(b.radiusPx * 0.5f))
  }

  private fun spawnBubble(
    slot: BubbleParticle,
    config: EditorBubbleConfig,
    heightPx: Float,
    density: Float,
    scatterVertically: Boolean,
  ) {
    val minR = config.minSizeDp * 0.5f * density
    val maxR = (config.maxSizeDp * 0.5f * density).coerceAtLeast(minR + 1f)
    val radius = randomFloat(minR, maxR)
    val duration = randomFloat(config.minDurationSec, config.maxDurationSec)
    val bottomStart = heightPx + radius * randomFloat(1.1f, 2.2f)
    slot.active = true
    slot.xNorm = randomFloat(0.05f, 0.95f)
    slot.radiusPx = radius
    slot.durationSec = duration
    slot.yPx =
      if (scatterVertically) randomFloat(-radius * 0.4f, bottomStart)
      else bottomStart
    slot.swayAmpPx = randomFloat(4f, 18f) * density
    slot.swayFreqRad = randomFloat(0.75f, 2.0f)
    slot.swayPhase = randomFloat(0f, (2.0 * PI).toFloat())
    slot.alphaMultiplier = randomFloat(0.72f, 1.0f)
    slot.paletteIndex = random.nextInt(4)
    slot.ageSec = randomFloat(0f, 10f)
  }

  private fun spawnPopBurst(
    xPx: Float,
    yPx: Float,
    radiusPx: Float,
    paletteIndex: Int,
  ) {
    for (i in 0 until MAX_BURSTS) {
      if (!bursts[i].active) {
        val b = bursts[i]
        b.active = true
        b.xPx = xPx
        b.yPx = yPx
        b.startRadiusPx = radiusPx
        b.progress = 0f
        b.paletteIndex = paletteIndex
        break
      }
    }

    var spawned = 0
    val targetDroplets = 6
    val baseAngle = randomFloat(0f, 1.0f)
    for (i in 0 until MAX_DROPLETS) {
      if (!droplets[i].active) {
        val angle = baseAngle + (spawned.toFloat() / targetDroplets) * (2.0 * PI).toFloat() + randomFloat(-0.22f, 0.22f)
        val distance = radiusPx * randomFloat(1.45f, 2.5f)
        val d = droplets[i]
        d.active = true
        d.originX = xPx
        d.originY = yPx
        d.vxPx = cos(angle) * distance
        d.vyPx = sin(angle) * distance
        d.radiusPx = (radiusPx * randomFloat(0.14f, 0.24f)).coerceIn(2f, 6f * lastDensity)
        d.progress = 0f
        d.paletteIndex = paletteIndex
        spawned++
        if (spawned >= targetDroplets) break
      }
    }
  }

  private fun randomFloat(min: Float, max: Float): Float {
    if (max <= min) return min
    return min + random.nextFloat() * (max - min)
  }

  companion object {
    const val MAX_BUBBLES = 32
    const val MAX_BURSTS = 12
    const val MAX_DROPLETS = 48
    private const val BURST_DURATION_SEC = 0.28f
    private const val DROPLET_DURATION_SEC = 0.34f

    fun resolveBubblePalette(
      style: EditorBubbleColorStyle,
      accent: Color,
      isDark: Boolean,
    ): List<Color> =
      when (style) {
        EditorBubbleColorStyle.FLARE_HEAT ->
          if (isDark) {
            listOf(Color(0xFFFF6B2B), Color(0xFFFF9E2C), Color(0xFFFF3D71), Color(0xFFA855F7))
          } else {
            listOf(Color(0xFFEA580C), Color(0xFFF59E0B), Color(0xFFE11D48), Color(0xFF9333EA))
          }

        EditorBubbleColorStyle.THEME_ACCENT ->
          listOf(
            accent,
            accent.copy(
              red = (accent.red * 0.78f + 0.22f).coerceIn(0f, 1f),
              green = (accent.green * 0.9f + 0.1f).coerceIn(0f, 1f),
            ),
            accent.copy(
              blue = (accent.blue * 0.8f + 0.2f).coerceIn(0f, 1f),
            ),
            accent,
          )

        EditorBubbleColorStyle.AURORA ->
          if (isDark) {
            listOf(Color(0xFF2DD4BF), Color(0xFF38BDF8), Color(0xFF34D399), Color(0xFF818CF8))
          } else {
            listOf(Color(0xFF0D9488), Color(0xFF0284C7), Color(0xFF059669), Color(0xFF4F46E5))
          }

        EditorBubbleColorStyle.MONO_GLASS ->
          if (isDark) {
            listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1), Color(0xFFF8FAFC), Color(0xFF94A3B8))
          } else {
            listOf(Color(0xFF475569), Color(0xFF64748B), Color(0xFF334155), Color(0xFF94A3B8))
          }

        EditorBubbleColorStyle.PASTEL ->
          if (isDark) {
            listOf(Color(0xFFF9A8D4), Color(0xFFA5B4FC), Color(0xFF7DD3FC), Color(0xFF86EFAC))
          } else {
            listOf(Color(0xFFF472B6), Color(0xFF818CF8), Color(0xFF38BDF8), Color(0xFF34D399))
          }
      }
  }
}
