package com.example.ui.components

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
    modifier = modifier.fillMaxWidth().background(palette.chromeSurface),
  ) {
    HorizontalDivider(color = palette.border.copy(alpha = 0.5f), thickness = 1.dp)
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(54.dp)
          .padding(horizontal = 8.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      items.forEach { item ->
        val interactionSource = remember(item.testTag) { MutableInteractionSource() }
        val active = item.selected
        val fg = if (active) palette.activePillFg else palette.textSecondary

        Surface(
          color = if (active) palette.activePillBg else Color.Transparent,
          shape = RoundedCornerShape(10.dp),
          border =
            if (active) BorderStroke(1.dp, palette.activePillFg.copy(alpha = 0.25f))
            else null,
          modifier =
            Modifier
              .weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(10.dp))
              .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = item.onClick,
              )
              .testTag(item.testTag),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = fg,
                modifier = Modifier.size(18.dp),
              )
              if (!item.badgeText.isNullOrBlank()) {
                Spacer(Modifier.width(4.dp))
                val bColor = item.badgeColor ?: palette.activePillFg
                Box(
                  modifier =
                    Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(bColor.copy(alpha = 0.18f))
                      .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                  Text(
                    text = item.badgeText,
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = bColor,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }
            }
            Spacer(Modifier.height(2.dp))
            Text(
              text = item.label,
              fontSize = 11.sp,
              fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
              color = fg,
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
