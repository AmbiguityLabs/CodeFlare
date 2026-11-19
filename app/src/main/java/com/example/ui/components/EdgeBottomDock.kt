package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.UiStyleVersion
import com.example.ui.theme.edgeGlassDock

data class EdgeBottomDockItem(
  val label: String,
  val icon: ImageVector,
  val selected: Boolean,
  val testTag: String,
  val badgeText: String? = null,
  val badgeColor: Color? = null,
  val onClick: () -> Unit,
)

@Composable
fun EdgeBottomDock(
  items: List<EdgeBottomDockItem>,
  modifier: Modifier = Modifier,
  onHideDock: (() -> Unit)? = null,
  hideDockTestTag: String = "hide_bottom_dock_button",
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED

  Column(
    modifier = modifier.fillMaxWidth().edgeGlassDock(shape = RectangleShape),
  ) {
    HorizontalDivider(
      color = if (isV2) palette.border else palette.border.copy(alpha = 0.55f),
      thickness = if (isV2) 0.5.dp else 0.75.dp,
    )
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(54.dp)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      items.forEach { item ->
        val interactionSource = remember(item.testTag) { MutableInteractionSource() }
        val active = item.selected

        val pillBg by animateColorAsState(
          targetValue = if (active) palette.activePillBg else Color.Transparent,
          animationSpec = tween(durationMillis = 180),
          label = "dock_pill_bg",
        )
        val iconTint by animateColorAsState(
          targetValue = if (active) palette.activePillFg else palette.textSecondary,
          animationSpec = tween(durationMillis = 160),
          label = "dock_icon_tint",
        )
        val iconScale by animateFloatAsState(
          targetValue = if (active) 1.06f else 1f,
          animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
          label = "dock_icon_scale",
        )

        Box(
          modifier =
            Modifier
              .height(38.dp)
              .clip(RoundedCornerShape(999.dp))
              .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = item.onClick,
              )
              .testTag(item.testTag),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            color = pillBg,
            shape = RoundedCornerShape(999.dp),
            border =
              if (active && !isV2) BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.30f))
              else null,
            modifier = Modifier.height(36.dp).animateContentSize(animationSpec = tween(180)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = if (active) 14.dp else 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                  imageVector = item.icon,
                  contentDescription = item.label,
                  tint = iconTint,
                  modifier =
                    Modifier
                      .size(19.dp)
                      .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                      },
                )
                if (!item.badgeText.isNullOrBlank()) {
                  val bColor = item.badgeColor ?: palette.ctaBg
                  Box(
                    modifier =
                      Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(bColor)
                        .padding(horizontal = 3.5.dp, vertical = 0.5.dp),
                  ) {
                    Text(
                      text = item.badgeText,
                      fontFamily = JetBrainsMono,
                      fontSize = 7.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF141806),
                      maxLines = 1,
                      softWrap = false,
                    )
                  }
                }
              }

              AnimatedVisibility(
                visible = active,
                enter = fadeIn(tween(150)) + expandHorizontally(tween(180)),
                exit = fadeOut(tween(110)) + shrinkHorizontally(tween(140)),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = item.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.activePillFg,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }

      if (onHideDock != null) {
        Surface(
          onClick = onHideDock,
          shape = CircleShape,
          color = palette.chromeSurface,
          border = if (isV2) null else BorderStroke(0.75.dp, palette.border.copy(alpha = 0.7f)),
          modifier =
            Modifier
              .padding(start = 2.dp, end = 4.dp)
              .size(34.dp)
              .testTag(hideDockTestTag),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.KeyboardArrowDown,
              contentDescription = "Hide bottom menu",
              tint = palette.textSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}
