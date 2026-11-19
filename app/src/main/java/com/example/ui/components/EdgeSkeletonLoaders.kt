package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.UiStyleVersion

@Composable
private fun rememberSkeletonPulseColor(): Color {
  val palette = LocalIdePalette.current
  val transition = rememberInfiniteTransition(label = "edge_skeleton_pulse")
  val alpha by transition.animateFloat(
    initialValue = if (palette.isDark) 0.14f else 0.10f,
    targetValue = if (palette.isDark) 0.30f else 0.22f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
    label = "edge_skeleton_alpha",
  )
  return palette.textMuted.copy(alpha = alpha)
}

@Composable
private fun SkeletonBlock(
  color: Color,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 6.dp,
) {
  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(color),
  )
}

@Composable
fun EdgeSkeletonProjectList(
  count: Int = 4,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val pulse = rememberSkeletonPulseColor()
  val subtlePulse = pulse.copy(alpha = pulse.alpha * 0.65f)

  Column(
    modifier = modifier.fillMaxWidth().testTag("skeleton_project_list"),
    verticalArrangement = Arrangement.spacedBy(if (isV2) 10.dp else 8.dp),
  ) {
    repeat(count.coerceAtLeast(1)) { index ->
      val titleFraction =
        when (index % 3) {
          0 -> 0.56f
          1 -> 0.44f
          else -> 0.64f
        }
      val subFraction =
        when (index % 3) {
          0 -> 0.38f
          1 -> 0.48f
          else -> 0.34f
        }
      Surface(
        color = palette.chromeSurface,
        shape = RoundedCornerShape(if (isV2) 20.dp else 14.dp),
        border = if (isV2) null else BorderStroke(0.75.dp, palette.border.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SkeletonBlock(
            color = pulse,
            cornerRadius = if (isV2) 10.dp else 8.dp,
            modifier = Modifier.size(32.dp),
          )
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
          ) {
            SkeletonBlock(
              color = pulse,
              cornerRadius = 5.dp,
              modifier = Modifier.fillMaxWidth(titleFraction).height(13.dp),
            )
            SkeletonBlock(
              color = subtlePulse,
              cornerRadius = 4.dp,
              modifier = Modifier.fillMaxWidth(subFraction).height(10.dp),
            )
          }
          SkeletonBlock(
            color = subtlePulse,
            cornerRadius = 6.dp,
            modifier = Modifier.width(38.dp).height(18.dp),
          )
        }
      }
    }
  }
}

@Composable
fun EdgeSkeletonCompactRows(
  count: Int = 5,
  modifier: Modifier = Modifier,
) {
  val pulse = rememberSkeletonPulseColor()
  val subtlePulse = pulse.copy(alpha = pulse.alpha * 0.70f)

  Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).testTag("skeleton_compact_rows"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    repeat(count.coerceAtLeast(1)) { index ->
      val widthFraction = if (index % 2 == 0) 0.68f else 0.52f
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SkeletonBlock(
          color = subtlePulse,
          cornerRadius = 4.dp,
          modifier = Modifier.size(16.dp),
        )
        SkeletonBlock(
          color = pulse,
          cornerRadius = 5.dp,
          modifier = Modifier.fillMaxWidth(widthFraction).height(12.dp),
        )
      }
    }
  }
}

@Composable
fun EdgeSkeletonInfraCards(
  count: Int = 3,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val pulse = rememberSkeletonPulseColor()
  val subtlePulse = pulse.copy(alpha = pulse.alpha * 0.65f)

  Column(
    modifier = modifier.fillMaxWidth().testTag("skeleton_infra_cards"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    repeat(count.coerceAtLeast(1)) { index ->
      Surface(
        color = palette.chromeSurface,
        shape = RoundedCornerShape(if (isV2) 18.dp else 12.dp),
        border = if (isV2) null else BorderStroke(0.75.dp, palette.border.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            SkeletonBlock(
              color = pulse,
              cornerRadius = 5.dp,
              modifier = Modifier.fillMaxWidth(if (index % 2 == 0) 0.46f else 0.58f).height(13.dp),
            )
            SkeletonBlock(
              color = subtlePulse,
              cornerRadius = 6.dp,
              modifier = Modifier.width(52.dp).height(18.dp),
            )
          }
          SkeletonBlock(
            color = subtlePulse,
            cornerRadius = 4.dp,
            modifier = Modifier.fillMaxWidth(0.74f).height(10.dp),
          )
        }
      }
    }
  }
}

@Composable
fun EdgeSkeletonRepoList(
  count: Int = 5,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val pulse = rememberSkeletonPulseColor()
  val subtlePulse = pulse.copy(alpha = pulse.alpha * 0.65f)

  Column(
    modifier = modifier.fillMaxWidth().testTag("skeleton_repo_list"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    repeat(count.coerceAtLeast(1)) { index ->
      Surface(
        color = palette.chromePill,
        shape = RoundedCornerShape(if (isV2) 16.dp else 12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SkeletonBlock(
            color = subtlePulse,
            cornerRadius = 5.dp,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(10.dp))
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            SkeletonBlock(
              color = pulse,
              cornerRadius = 5.dp,
              modifier = Modifier.fillMaxWidth(if (index % 2 == 0) 0.62f else 0.50f).height(13.dp),
            )
            SkeletonBlock(
              color = subtlePulse,
              cornerRadius = 4.dp,
              modifier = Modifier.fillMaxWidth(0.32f).height(10.dp),
            )
          }
          Spacer(Modifier.width(10.dp))
          SkeletonBlock(
            color = subtlePulse,
            cornerRadius = 8.dp,
            modifier = Modifier.width(58.dp).height(30.dp),
          )
        }
      }
    }
  }
}
