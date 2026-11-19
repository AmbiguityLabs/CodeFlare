package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.UiStyleVersion
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
  statusDotColor: Color? = null,
  badgeCount: String? = null,
  height: Dp = 36.dp,
  revealDurationMs: Long = 2200L,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  var revealBump by remember { mutableIntStateOf(0) }
  var isRevealed by remember { mutableStateOf(false) }

  LaunchedEffect(revealBump) {
    if (revealBump > 0) {
      isRevealed = true
      delay(revealDurationMs)
      isRevealed = false
    }
  }

  val containerColor by animateColorAsState(
    targetValue =
      when {
        accentContainer -> palette.ctaBg
        active -> palette.activePillBg
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
        else -> palette.textPrimary
      },
    animationSpec = tween(150),
    label = "icon_reveal_fg",
  )

  val borderStroke =
    when {
      isV2 && !active -> null
      active -> BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.35f))
      isV2 -> null
      else -> BorderStroke(0.75.dp, palette.glassBorderSubtle)
    }

  Surface(
    onClick = {
      revealBump++
      onClick()
    },
    shape = RoundedCornerShape(if (isV2) 999.dp else 10.dp),
    color = containerColor,
    border = borderStroke,
    modifier =
      modifier
        .height(height)
        .animateContentSize(animationSpec = tween(170))
        .then(if (tag != null) Modifier.testTag(tag) else Modifier),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = if (isRevealed) 11.dp else 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Box(contentAlignment = Alignment.TopEnd) {
        if (isBusy) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = contentColor,
          )
        } else {
          Icon(
            imageVector = icon,
            contentDescription = revealedLabel,
            tint = contentColor,
            modifier = Modifier.size(17.dp),
          )
        }
        if (statusDotColor != null && !isBusy) {
          Box(
            modifier =
              Modifier
                .size(6.dp)
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
          Spacer(Modifier.width(6.dp))
          Text(
            text = revealedLabel,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.5.sp,
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
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
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
      modifier = Modifier.padding(horizontal = if (selected) 11.dp else 9.dp),
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
        visible = selected,
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
) {
  val palette = LocalIdePalette.current
  var revealed by remember { mutableStateOf(false) }
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(999.dp))
        .clickable(role = Role.Button) { revealed = !revealed }
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
    AnimatedVisibility(visible = revealed) {
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
