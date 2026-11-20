package com.example

import androidx.compose.ui.graphics.Color
import com.example.data.security.EditorBubbleColorStyle
import com.example.data.security.EditorBubbleConfig
import com.example.ui.components.EditorBubbleController
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorBubbleEngineTest {

  @Test
  fun disabledConfigKeepsZeroActiveBubblesAndRejectsPop() {
    val controller = EditorBubbleController(Random(42))
    controller.updateConfig(
      config = EditorBubbleConfig(enabled = false, count = 16),
      widthPx = 1080f,
      heightPx = 1920f,
      density = 2.0f,
    )
    assertEquals(0, controller.activeBubblesCount())
    assertFalse(controller.tryPopAt(540f, 960f))
    assertEquals(0L, controller.poppedTotalCount)
  }

  @Test
  fun bubblesSpawnWithinRandomSizeAndDurationBoundsAndRiseUpward() {
    val controller = EditorBubbleController(Random(1337))
    val density = 2.0f
    val config =
      EditorBubbleConfig(
        enabled = true,
        count = 18,
        minSizeDp = 12,
        maxSizeDp = 36,
        minDurationSec = 4.0f,
        maxDurationSec = 10.0f,
        opacity = 0.35f,
      )
    controller.updateConfig(config, widthPx = 1080f, heightPx = 1920f, density = density)
    assertEquals(18, controller.activeBubblesCount())

    val minRadiusPx = config.minSizeDp * 0.5f * density
    val maxRadiusPx = config.maxSizeDp * 0.5f * density
    val initialY = FloatArray(18)

    for (i in 0 until 18) {
      val b = controller.bubbles[i]
      assertTrue("Bubble $i should be active", b.active)
      assertTrue(
        "Radius ${b.radiusPx} out of [$minRadiusPx, $maxRadiusPx]",
        b.radiusPx in minRadiusPx..maxRadiusPx,
      )
      assertTrue(
        "Duration ${b.durationSec} out of [${config.minDurationSec}, ${config.maxDurationSec}]",
        b.durationSec in config.minDurationSec..config.maxDurationSec,
      )
      initialY[i] = b.yPx
    }

    // Advance several frames and verify bubbles move from below toward above (yPx decreases)
    repeat(10) {
      controller.advance(dtSec = 0.05f, widthPx = 1080f, heightPx = 1920f, density = density)
    }

    for (i in 0 until 18) {
      val b = controller.bubbles[i]
      if (b.yPx <= initialY[i]) {
        assertTrue("Bubble $i should move upward", b.yPx < initialY[i])
      } else {
        // Wrapped past top and respawned below viewport bottom
        assertTrue("Respawned bubble $i should start below viewport height", b.yPx > 1920f)
      }
    }
  }

  @Test
  fun bubbleRespawnsAtBottomAfterCrossingTopEdge() {
    val controller = EditorBubbleController(Random(7))
    val config =
      EditorBubbleConfig(
        enabled = true,
        count = 4,
        minSizeDp = 10,
        maxSizeDp = 16,
        minDurationSec = 2.5f,
        maxDurationSec = 3.5f,
      )
    controller.updateConfig(config, widthPx = 800f, heightPx = 1000f, density = 1.0f)

    // Move bubble 0 just above the top edge so the next frame triggers respawn below
    val b0 = controller.bubbles[0]
    b0.yPx = -b0.radiusPx * 1.4f
    controller.advance(dtSec = 0.016f, widthPx = 800f, heightPx = 1000f, density = 1.0f)

    assertTrue("Bubble should respawn below viewport height", b0.yPx > 1000f)
    assertTrue("Respawned bubble remains active", b0.active)
  }

  @Test
  fun tryPopAtPopsImmediatelySpawnsBurstAndRespawnsAtBottom() {
    val controller = EditorBubbleController(Random(99))
    val config =
      EditorBubbleConfig(
        enabled = true,
        count = 6,
        minSizeDp = 16,
        maxSizeDp = 28,
        tapToPop = true,
        popBurstEnabled = true,
      )
    controller.updateConfig(config, widthPx = 1000f, heightPx = 1600f, density = 2.0f)

    val target = controller.bubbles[2]
    target.yPx = 720f
    val tapX = controller.bubbleCenterX(target, 1000f)
    val tapY = target.yPx

    val popped = controller.tryPopAt(tapX, tapY)
    assertTrue("Tapping bubble center should pop immediately", popped)
    assertEquals(1L, controller.poppedTotalCount)
    assertTrue("Popped bubble respawns at bottom edge", target.yPx > 1600f)
    assertTrue("At least one shockwave burst should activate", controller.bursts.any { it.active })
    assertEquals(6, controller.droplets.count { it.active })

    // Miss check far away from any bubble (zero slop)
    val farMiss = controller.tryPopAt(-5000f, -5000f, extraTouchSlopPx = 0f)
    assertFalse(farMiss)
    assertEquals(1L, controller.poppedTotalCount)

    // Advance burst & droplets past completion duration (~0.40s) so they deactivate cleanly
    repeat(10) {
      controller.advance(dtSec = 0.05f, widthPx = 1000f, heightPx = 1600f, density = 2.0f)
    }
    assertTrue(controller.bursts.none { it.active })
    assertTrue(controller.droplets.none { it.active })
  }

  @Test
  fun tapToPopDisabledIgnoresTouchesEvenOnDirectHit() {
    val controller = EditorBubbleController(Random(11))
    val config =
      EditorBubbleConfig(
        enabled = true,
        count = 4,
        tapToPop = false,
      )
    controller.updateConfig(config, widthPx = 900f, heightPx = 1400f, density = 1.5f)
    val target = controller.bubbles[0]
    target.yPx = 500f
    val tapX = controller.bubbleCenterX(target, 900f)

    assertFalse(controller.tryPopAt(tapX, target.yPx))
    assertEquals(0L, controller.poppedTotalCount)
  }

  @Test
  fun resolveBubblePaletteProducesFourDistinctColorsPerStyle() {
    val accent = Color(0xFF8B5CF6)
    for (style in EditorBubbleColorStyle.entries) {
      val darkColors = EditorBubbleController.resolveBubblePalette(style, accent, isDark = true)
      val lightColors = EditorBubbleController.resolveBubblePalette(style, accent, isDark = false)
      assertEquals("Dark palette size for $style", 4, darkColors.size)
      assertEquals("Light palette size for $style", 4, lightColors.size)
    }
  }
}
