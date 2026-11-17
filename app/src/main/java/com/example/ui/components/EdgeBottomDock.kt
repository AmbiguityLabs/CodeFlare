package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette

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
) {
  val palette = LocalIdePalette.current

  Column(
    modifier = modifier.fillMaxWidth().background(palette.chromeBg),
  ) {
    HorizontalDivider(color = palette.border.copy(alpha = 0.45f), thickness = 1.dp)
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
        val iconTint = if (active) palette.activePillFg else palette.textSecondary
        val labelColor = if (active) palette.textPrimary else palette.textSecondary

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
                  .width(60.dp)
                  .height(30.dp)
                  .clip(RoundedCornerShape(999.dp))
                  .background(if (active) palette.activePillBg else Color.Transparent),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier.size(19.dp),
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
    }
  }
}
