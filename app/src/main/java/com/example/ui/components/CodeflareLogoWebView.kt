package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.liquidGlassSurface
import com.example.util.HardwareSpeedProfiler
import java.util.Locale

private const val CODEFLARE_LOGO_ASSET_BASE = "file:///android_asset/codeflare_logo_flame_top_neon_heat.html"

internal fun buildCodeflareLogoAssetUrl(
  darkTheme: Boolean,
  embedOnly: Boolean,
  speedMultiplier: Float,
): String {
  val clampedSpeed = speedMultiplier.coerceIn(0.5f, 3.0f)
  val formattedSpeed = String.format(Locale.US, "%.2f", clampedSpeed)
  val themeValue = if (darkTheme) "dark" else "light"
  val embedValue = if (embedOnly) "1" else "0"
  return Uri.parse(CODEFLARE_LOGO_ASSET_BASE)
    .buildUpon()
    .appendQueryParameter("embed", embedValue)
    .appendQueryParameter("theme", themeValue)
    .appendQueryParameter("speed", formattedSpeed)
    .build()
    .toString()
}

@Composable
fun rememberHardwareAnimationSpeed(): Float {
  val context = LocalContext.current
  return remember(context) {
    val profile = HardwareSpeedProfiler.inspectDevice(context)
    HardwareSpeedProfiler.computeSpeedMultiplier(profile)
  }
}

/**
 * High-performance native Compose emblem replacing the heavy Chromium WebView splash/logo.
 * Zero WebView initialization, zero 190 KB HTML/SVG/base64 decoding, and GPU-composited pulse only.
 */
@Composable
fun CodeflareAnimatedLogoView(
  modifier: Modifier = Modifier,
  embedOnly: Boolean = true,
  replayTrigger: Int = 0,
  tapToReplay: Boolean = false,
  speedMultiplier: Float = rememberHardwareAnimationSpeed(),
  onPageReady: () -> Unit = {},
  testTag: String = "codeflare_animated_logo",
) {
  val context = LocalContext.current
  val reducedMotion =
    remember(context) {
      HardwareSpeedProfiler.inspectDevice(context).isReducedMotionPreferred
    }
  var pulseTarget by remember { mutableFloatStateOf(1f) }
  val pulseScale by animateFloatAsState(
    targetValue = pulseTarget,
    animationSpec = tween(durationMillis = (180f / speedMultiplier.coerceIn(0.75f, 2.5f)).toInt()),
    finishedListener = { pulseTarget = 1f },
    label = "codeflare_logo_pulse",
  )

  LaunchedEffect(Unit) {
    onPageReady()
  }

  LaunchedEffect(replayTrigger, reducedMotion) {
    if (replayTrigger > 0 && !reducedMotion) {
      pulseTarget = 1.05f
    }
  }

  val clickModifier =
    if (embedOnly && tapToReplay) {
      Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
      ) {
        if (!reducedMotion) {
          pulseTarget = 1.05f
        }
      }
    } else {
      Modifier
    }

  Box(
    modifier = modifier.testTag(testTag).then(clickModifier),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.codeflare_boot_emblem),
      contentDescription = "CodeFlare emblem",
      contentScale = ContentScale.Fit,
      modifier =
        Modifier
          .fillMaxSize()
          .graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
          },
    )
  }
}

/**
 * Lightweight native CodeFlare header emblem. Zero WebView overhead and zero idle wake timers;
 * animates smoothly on the GPU only while actively syncing.
 */
@Composable
fun CodeflareAutoEmblem(
  modifier: Modifier = Modifier,
  actionNonce: Long = 0L,
  isSyncing: Boolean = false,
  minIntervalMs: Long = 9_000L,
  maxIntervalMs: Long = 22_000L,
  testTag: String = "codeflare_auto_emblem",
) {
  @Suppress("UNUSED_VARIABLE")
  val unusedParams = actionNonce + minIntervalMs + maxIntervalMs

  val context = LocalContext.current
  val reducedMotion =
    remember(context) {
      HardwareSpeedProfiler.inspectDevice(context).isReducedMotionPreferred
    }

  val syncAlpha =
    if (isSyncing && !reducedMotion) {
      val transition = rememberInfiniteTransition(label = "emblem_sync_pulse")
      val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.58f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "emblem_sync_alpha",
      )
      alpha
    } else {
      1f
    }

  Box(
    modifier = modifier.testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.codeflare_boot_emblem),
      contentDescription = "CodeFlare emblem",
      contentScale = ContentScale.Fit,
      modifier =
        Modifier
          .fillMaxSize()
          .graphicsLayer { alpha = syncAlpha },
    )
  }
}

@Composable
fun CodeflareEmblemDialog(
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  var replayCount by remember { mutableIntStateOf(0) }
  Dialog(onDismissRequest = onDismiss) {
    val shape = RoundedCornerShape(26.dp)
    Box(
      modifier =
        Modifier
          .liquidGlassSurface(
            shape = shape,
            cornerRadius = 26.dp,
            thickness = 1.65f,
            drawShadow = true,
          )
          .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CodeflareAnimatedLogoView(
          modifier =
            Modifier
              .size(164.dp)
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
              ) { replayCount++ },
          embedOnly = true,
          replayTrigger = replayCount,
          tapToReplay = true,
        )
        Text(
          text = "CodeFlare",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = palette.textPrimary,
        )
        Text(
          text = "Mobile Workers & Full-Stack Edge IDE",
          style = MaterialTheme.typography.bodySmall,
          color = palette.textSecondary,
        )
        TextButton(onClick = onDismiss) {
          Text("Close", color = palette.activePillFg, fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

