package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.IconLabelBehavior
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIconLabelBehavior
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.UiStyleVersion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun IconRevealButton(
  icon: ImageVector,
  revealedLabel: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tag: String? = null,
  active: Boolean = false,
  isBusy: Boolean = false,
  accentContainer: Boolean = false,
  minimal: Boolean = false,
  contentTint: Color? = null,
  statusDotColor: Color? = null,
  badgeCount: String? = null,
  height: Dp = 36.dp,
  iconSize: Dp = 17.dp,
  revealDurationMs: Long = 2200L,
  labelBehaviorOverride: IconLabelBehavior? = null,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val behavior = labelBehaviorOverride ?: LocalIconLabelBehavior.current
  var revealBump by remember { mutableIntStateOf(0) }
  var tapRevealed by remember { mutableStateOf(false) }

  LaunchedEffect(revealBump, behavior) {
    if (behavior != IconLabelBehavior.REVEAL_ON_TAP) {
      tapRevealed = false
      return@LaunchedEffect
    }
    if (revealBump > 0) {
      tapRevealed = true
      delay(revealDurationMs)
      tapRevealed = false
    }
  }

  val isRevealed =
    when (behavior) {
      IconLabelBehavior.ICON_ONLY -> false
      IconLabelBehavior.REVEAL_ON_TAP -> tapRevealed
      IconLabelBehavior.ALWAYS_SHOW -> true
    }

  val containerColor by animateColorAsState(
    targetValue =
      when {
        accentContainer -> palette.ctaBg
        active -> palette.activePillBg
        minimal && isRevealed -> palette.chromeSurface
        minimal -> Color.Transparent
        isV2 -> palette.chromeSurface
        else -> palette.glassElevated
      },
    animationSpec = tween(150),
    label = "icon_reveal_bg",
  )
  val contentColor by animateColorAsState(
    targetValue =
      when {
        accentContainer -> palette.ctaFg
        active -> palette.activePillFg
        contentTint != null -> contentTint
        minimal -> palette.textSecondary
        else -> palette.textPrimary
      },
    animationSpec = tween(150),
    label = "icon_reveal_fg",
  )

  val borderStroke =
    when {
      minimal && !active && !isRevealed -> null
      minimal && isRevealed && !isV2 -> BorderStroke(0.5.dp, palette.glassBorderSubtle)
      isV2 && !active -> null
      active -> BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.35f))
      isV2 -> null
      else -> BorderStroke(0.75.dp, palette.glassBorderSubtle)
    }

  Surface(
    onClick = {
      if (behavior == IconLabelBehavior.REVEAL_ON_TAP) {
        revealBump++
      }
      onClick()
    },
    shape = RoundedCornerShape(if (isV2) 999.dp else if (minimal) 8.dp else 10.dp),
    color = containerColor,
    border = borderStroke,
    modifier =
      modifier
        .height(height)
        .animateContentSize(animationSpec = tween(170))
        .then(if (tag != null) Modifier.testTag(tag) else Modifier),
  ) {
    Row(
      modifier =
        Modifier.padding(
          horizontal =
            when {
              isRevealed -> if (minimal) 8.dp else 11.dp
              minimal -> 6.dp
              else -> 9.dp
            },
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Box(contentAlignment = Alignment.TopEnd) {
        if (isBusy) {
          EdgeLoadingIndicator(
            size = iconSize - 1.dp,
            strokeWidth = 2.dp,
            color = contentColor,
          )
        } else {
          Icon(
            imageVector = icon,
            contentDescription = revealedLabel,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
          )
        }
        if (statusDotColor != null && !isBusy) {
          Box(
            modifier =
              Modifier
                .offset(x = 3.dp, y = (-2).dp)
                .size(5.5.dp)
                .clip(CircleShape)
                .background(statusDotColor),
          )
        }
      }

      AnimatedVisibility(
        visible = isRevealed,
        enter = fadeIn(tween(150)) + expandHorizontally(tween(170)),
        exit = fadeOut(tween(120)) + shrinkHorizontally(tween(150)),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Spacer(Modifier.width(5.dp))
          Text(
            text = revealedLabel,
            style = MaterialTheme.typography.labelMedium,
            fontSize = if (minimal) 10.5.sp else 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (!badgeCount.isNullOrBlank() && !isRevealed) {
        Spacer(Modifier.width(4.dp))
        Text(
          text = badgeCount,
          fontFamily = JetBrainsMono,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (active) palette.activePillFg else palette.textSecondary,
        )
      }
    }
  }
}

@Composable
fun IconSegmentTab(
  icon: ImageVector,
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tag: String? = null,
  badgeText: String? = null,
  badgeColor: Color? = null,
  labelBehaviorOverride: IconLabelBehavior? = null,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val behavior = labelBehaviorOverride ?: LocalIconLabelBehavior.current
  val showLabel =
    when (behavior) {
      IconLabelBehavior.ICON_ONLY -> false
      IconLabelBehavior.REVEAL_ON_TAP -> selected
      IconLabelBehavior.ALWAYS_SHOW -> true
    }

  val bg by animateColorAsState(
    targetValue =
      when {
        selected -> palette.activePillBg
        isV2 -> palette.chromeSurface
        else -> palette.glassSurface
      },
    animationSpec = tween(160),
    label = "seg_tab_bg",
  )
  val fg by animateColorAsState(
    targetValue = if (selected) palette.activePillFg else palette.textSecondary,
    animationSpec = tween(160),
    label = "seg_tab_fg",
  )

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(if (isV2) 999.dp else 9.dp),
    color = bg,
    border =
      when {
        isV2 -> null
        selected -> BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.35f))
        else -> BorderStroke(0.75.dp, palette.glassBorderSubtle)
      },
    modifier =
      modifier
        .height(32.dp)
        .animateContentSize(animationSpec = tween(170))
        .then(if (tag != null) Modifier.testTag(tag) else Modifier),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = if (showLabel) 11.dp else 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = fg,
        modifier = Modifier.size(16.dp),
      )
      AnimatedVisibility(
        visible = showLabel,
        enter = fadeIn(tween(150)) + expandHorizontally(tween(170)),
        exit = fadeOut(tween(110)) + shrinkHorizontally(tween(140)),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Spacer(Modifier.width(5.dp))
          Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (!badgeText.isNullOrBlank()) {
        Spacer(Modifier.width(4.dp))
        val bColor = badgeColor ?: palette.activePillFg
        Box(
          modifier =
            Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(bColor.copy(alpha = 0.16f))
              .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
          Text(
            text = badgeText,
            fontFamily = JetBrainsMono,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = bColor,
            maxLines = 1,
            softWrap = false,
          )
        }
      }
    }
  }
}

@Composable
fun IconToggleRevealText(
  icon: ImageVector,
  text: String,
  tint: Color? = null,
  modifier: Modifier = Modifier,
  onFallbackClick: (() -> Unit)? = null,
) {
  val palette = LocalIdePalette.current
  val behavior = LocalIconLabelBehavior.current
  var revealed by remember { mutableStateOf(false) }
  val isTextShown =
    when (behavior) {
      IconLabelBehavior.ICON_ONLY -> false
      IconLabelBehavior.REVEAL_ON_TAP -> revealed
      IconLabelBehavior.ALWAYS_SHOW -> true
    }

  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(999.dp))
        .clickable(role = Role.Button) {
          if (behavior == IconLabelBehavior.REVEAL_ON_TAP) {
            revealed = !revealed
          } else {
            onFallbackClick?.invoke()
          }
        }
        .padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = text,
      tint = tint ?: palette.textMuted,
      modifier = Modifier.size(15.dp),
    )
    AnimatedVisibility(visible = isTextShown) {
      Text(
        text = text,
        fontFamily = JetBrainsMono,
        fontSize = 10.5.sp,
        color = palette.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private data class ThinkingDotSeed(
  val gridX: Float, // -1f, 0f, +1f
  val gridY: Float, // -1f, 0f, +1f
  val pulsePhaseA: Float,
  val pulsePhaseB: Float,
  val driftPhaseX: Float,
  val driftPhaseY: Float,
  val freqPulse: Int,
  val freqDrift: Int,
)

// Nine organically de-phased seeds (3x3 field) whose integer harmonic multipliers
// guarantee a 100% seamless infinite loop while moving & shimmering unpredictably.
private val THINKING_DOT_SEEDS: List<ThinkingDotSeed> =
  listOf(
    ThinkingDotSeed(-1f, -1f, 0.25f, 1.90f, 0.70f, 2.80f, 2, 1),
    ThinkingDotSeed(0f, -1f, 2.40f, 0.65f, 3.10f, 1.15f, 3, 2),
    ThinkingDotSeed(1f, -1f, 4.15f, 3.30f, 5.20f, 4.40f, 2, 1),
    ThinkingDotSeed(-1f, 0f, 5.10f, 2.15f, 1.85f, 5.60f, 3, 2),
    ThinkingDotSeed(0f, 0f, 1.35f, 4.85f, 4.25f, 0.45f, 2, 1),
    ThinkingDotSeed(1f, 0f, 3.60f, 5.75f, 2.40f, 3.95f, 3, 1),
    ThinkingDotSeed(-1f, 1f, 2.95f, 0.40f, 4.90f, 2.20f, 2, 2),
    ThinkingDotSeed(0f, 1f, 0.80f, 3.70f, 0.15f, 5.05f, 3, 1),
    ThinkingDotSeed(1f, 1f, 4.70f, 1.55f, 3.65f, 1.60f, 2, 2),
  )

/**
 * Dual-style loading indicator bound to the active [UiStyleVersion]:
 * - [UiStyleVersion.V1_DEFAULT]: Crisp round circular sweeping arc spinner.
 * - [UiStyleVersion.V2_MINIMAL_OLED]: Fluid organic 3x3 thinking-dots constellation
 *   with staggered pseudo-random pulsing, breathing radius, and gentle harmonic drift.
 */
@Composable
fun EdgeLoadingIndicator(
  modifier: Modifier = Modifier,
  size: Dp = 28.dp,
  color: Color? = null,
  strokeWidth: Dp = (size * 0.115f).coerceIn(2.dp, 3.5.dp),
  styleOverride: UiStyleVersion? = null,
) {
  val palette = LocalIdePalette.current
  val resolvedStyle = styleOverride ?: palette.uiStyle
  val resolvedColor = color ?: palette.activePillFg

  when (resolvedStyle) {
    UiStyleVersion.V1_DEFAULT -> {
      RoundArcLoadingIndicator(
        size = size,
        color = resolvedColor,
        strokeWidth = strokeWidth,
        modifier = modifier,
      )
    }

    UiStyleVersion.V2_MINIMAL_OLED -> {
      val secondaryTint = color ?: palette.ctaBg
      ThinkingDotsLoadingIndicator(
        size = size,
        baseColor = color ?: palette.textPrimary,
        accentColor = secondaryTint,
        modifier = modifier,
      )
    }
  }
}

@Composable
private fun RoundArcLoadingIndicator(
  size: Dp,
  color: Color,
  strokeWidth: Dp,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "edge_v1_round_loader")
  val rotation by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1050, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "round_rotation",
    )
  val sweepBreath by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "round_sweep",
    )

  Canvas(
    modifier = modifier.size(size).testTag("edge_loader_v1_round"),
  ) {
    val strokePx = strokeWidth.toPx().coerceAtMost(this.size.minDimension * 0.28f)
    val inset = strokePx / 2f
    val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
    val topLeft = Offset(inset, inset)
    val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

    drawArc(
      color = color.copy(alpha = 0.16f),
      startAngle = 0f,
      sweepAngle = 360f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = stroke,
    )

    val sweepAngle = 52f + (sweepBreath * 196f)
    val startAngle = (rotation + sweepBreath * 72f) % 360f
    drawArc(
      color = color,
      startAngle = startAngle,
      sweepAngle = sweepAngle,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = stroke,
    )
  }
}

@Composable
private fun ThinkingDotsLoadingIndicator(
  size: Dp,
  baseColor: Color,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "edge_v2_thinking_dots")
  val phase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 3600, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "thinking_phase",
    )

  val twoPi = (2.0 * PI).toFloat()
  val drawSoftHalo = size >= 26.dp

  Canvas(
    modifier = modifier.size(size).testTag("edge_loader_v2_dots"),
  ) {
    val minDim = this.size.minDimension
    val centerX = this.size.width / 2f
    val centerY = this.size.height / 2f
    val gridStride = minDim * 0.295f
    val maxDotRadius = minDim * 0.118f
    val driftAmplitude = gridStride * 0.21f

    for (seed in THINKING_DOT_SEEDS) {
      val waveA = sin(twoPi * phase * seed.freqPulse + seed.pulsePhaseA)
      val waveB = cos(twoPi * phase * (seed.freqPulse + 1) + seed.pulsePhaseB)
      val intensity = (0.50f + 0.34f * waveA + 0.16f * waveB).coerceIn(0f, 1f)

      val driftX =
        driftAmplitude *
          (0.68f * sin(twoPi * phase * seed.freqDrift + seed.driftPhaseX) +
            0.32f * cos(twoPi * phase * 2 + seed.driftPhaseY))
      val driftY =
        driftAmplitude *
          (0.68f * cos(twoPi * phase * seed.freqDrift + seed.driftPhaseY) +
            0.32f * sin(twoPi * phase * 3 + seed.driftPhaseX))

      val cx = centerX + seed.gridX * gridStride + driftX
      val cy = centerY + seed.gridY * gridStride + driftY

      val radius = maxDotRadius * (0.38f + 0.64f * intensity)
      val alpha = 0.20f + 0.80f * intensity
      val dotColor = lerp(baseColor, accentColor, (intensity * 0.55f).coerceIn(0f, 1f))

      if (drawSoftHalo && intensity > 0.58f) {
        val haloStrength = ((intensity - 0.58f) / 0.42f).coerceIn(0f, 1f)
        drawCircle(
          color = dotColor.copy(alpha = 0.20f * haloStrength),
          radius = radius * 1.85f,
          center = Offset(cx, cy),
        )
      }

      drawCircle(
        color = dotColor.copy(alpha = alpha),
        radius = radius,
        center = Offset(cx, cy),
      )
    }
  }
}

/**
 * Centered pane overlay displaying [EdgeLoadingIndicator] cleanly above loading content.
 */
@Composable
fun EdgeLoadingOverlay(
  visible: Boolean,
  modifier: Modifier = Modifier,
  indicatorSize: Dp = 44.dp,
  scrimAlpha: Float = 0.90f,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(tween(140)),
    exit = fadeOut(tween(220)),
    modifier = modifier.fillMaxSize(),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(palette.editorBg.copy(alpha = scrimAlpha))
          .testTag("edge_loading_overlay"),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier =
          Modifier
            .clip(RoundedCornerShape(if (isV2) 24.dp else 18.dp))
            .background(palette.chromeSurface.copy(alpha = 0.92f))
            .size(indicatorSize + 36.dp),
        contentAlignment = Alignment.Center,
      ) {
        EdgeLoadingIndicator(size = indicatorSize)
      }
    }
  }
}
