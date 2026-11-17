package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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

  Column(
    modifier = modifier.fillMaxWidth().edgeGlassDock(shape = RectangleShape),
  ) {
    HorizontalDivider(color = palette.glassBorderHighlight, thickness = 1.dp)
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(66.dp)
          .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      items.forEach { item ->
        val interactionSource = remember(item.testTag) { MutableInteractionSource() }
        val active = item.selected

        val pillWidth by animateDpAsState(
          targetValue = if (active) 58.dp else 32.dp,
          animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
          label = "dock_pill_w",
        )
        val pillBg by animateColorAsState(
          targetValue = if (active) palette.activePillBg else Color.Transparent,
          animationSpec = tween(durationMillis = 200),
          label = "dock_pill_bg",
        )
        val iconTint by animateColorAsState(
          targetValue = if (active) palette.activePillFg else palette.textSecondary,
          animationSpec = tween(durationMillis = 180),
          label = "dock_icon_tint",
        )
        val labelColor by animateColorAsState(
          targetValue = if (active) palette.textPrimary else palette.textSecondary,
          animationSpec = tween(durationMillis = 180),
          label = "dock_label_color",
        )
        val iconScale by animateFloatAsState(
          targetValue = if (active) 1.07f else 1f,
          animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
          label = "dock_icon_scale",
        )

        Column(
          modifier =
            Modifier
              .weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(14.dp))
              .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = item.onClick,
              )
              .testTag(item.testTag),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Box(contentAlignment = Alignment.Center) {
            Box(
              modifier =
                Modifier
                  .width(pillWidth)
                  .height(30.dp)
                  .clip(RoundedCornerShape(999.dp))
                  .background(pillBg),
              contentAlignment = Alignment.Center,
            ) {
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
            }
            if (!item.badgeText.isNullOrBlank()) {
              val bColor = item.badgeColor ?: palette.ctaBg
              Box(
                modifier =
                  Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(bColor)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
              ) {
                Text(
                  text = item.badgeText,
                  fontFamily = JetBrainsMono,
                  fontSize = 8.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF141806),
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }
          Spacer(Modifier.height(4.dp))
          Text(
            text = item.label,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (onHideDock != null) {
        Surface(
          onClick = onHideDock,
          shape = CircleShape,
          color = palette.glassElevated,
          border = BorderStroke(1.dp, palette.glassBorderSubtle),
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
