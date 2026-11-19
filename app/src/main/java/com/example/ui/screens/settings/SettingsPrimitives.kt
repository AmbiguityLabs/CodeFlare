package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.UiStyleVersion

enum class SettingsCategory(
  val title: String,
  val jumpTag: String,
  val icon: ImageVector,
) {
  APPEARANCE("Appearance", "appearance", Icons.Outlined.Palette),
  EDITOR("Editor", "editor", Icons.Outlined.Code),
  CLOUDFLARE("Cloudflare", "credentials", Icons.Outlined.Key),
  DEPLOY("Deployment", "deploy", Icons.Outlined.RocketLaunch),
  STORAGE("Storage & Sync", "storage", Icons.Outlined.Folder),
}

enum class AppearanceSubPage(
  val title: String,
  val headerTitle: String,
  val tag: String,
  val icon: ImageVector,
) {
  HOME_PAGE("Home Page", "Appearance · Home Page", "appearance_home", Icons.Outlined.Home),
  EDITOR("Editor", "Appearance · Editor", "appearance_editor", Icons.Outlined.Code),
}

@Composable
fun SettingsGroup(
  modifier: Modifier = Modifier,
  label: String? = null,
  trailingValue: String? = null,
  contentPadding: Dp = 0.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(if (isV2) 10.dp else 6.dp),
  ) {
    if (label != null || trailingValue != null) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (isV2) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        if (label != null) {
          if (isV2) {
            Text(
              text = label,
              style = MaterialTheme.typography.bodyMedium,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Medium,
              color = palette.textSecondary,
            )
          } else {
            Text(
              text = label.uppercase(),
              fontFamily = JetBrainsMono,
              fontSize = 9.5.sp,
              fontWeight = FontWeight.SemiBold,
              letterSpacing = 0.9.sp,
              color = palette.textMuted,
            )
          }
        } else {
          Spacer(Modifier.width(1.dp))
        }
        if (trailingValue != null) {
          Text(
            text = trailingValue,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.activePillFg,
          )
        }
      }
    }
    Surface(
      color = palette.chromeSurface,
      shape = RoundedCornerShape(if (isV2) 24.dp else 13.dp),
      border = if (isV2) null else BorderStroke(0.75.dp, palette.border.copy(alpha = 0.6f)),
      modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(160)),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(contentPadding),
        content = content,
      )
    }
  }
}

@Composable
fun SettingsFoldableSection(
  title: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  summary: String? = null,
  initiallyExpanded: Boolean = false,
  contentPadding: Dp = 0.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
  val chevronRotation by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = tween(160),
    label = "fold_chevron_$title",
  )

  Surface(
    color = palette.chromeSurface,
    shape = RoundedCornerShape(if (isV2) 24.dp else 13.dp),
    border = if (isV2) null else BorderStroke(0.75.dp, palette.border.copy(alpha = 0.6f)),
    modifier = modifier.fillMaxWidth().animateContentSize(animationSpec = tween(160)),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = if (isV2) 18.dp else 14.dp, vertical = if (isV2) 15.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isV2) 14.dp else 10.dp),
      ) {
        if (isV2) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (expanded) palette.activePillFg else palette.textPrimary,
            modifier = Modifier.size(20.dp),
          )
        } else {
          Box(
            modifier =
              Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (expanded) palette.activePillBg else palette.editorBg)
                .border(0.75.dp, palette.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = if (expanded) palette.activePillFg else palette.textSecondary,
              modifier = Modifier.size(15.dp),
            )
          }
        }
        Text(
          text = title,
          style = MaterialTheme.typography.bodyMedium,
          fontSize = if (isV2) 15.5.sp else 14.sp,
          fontWeight = if (isV2) FontWeight.Normal else FontWeight.SemiBold,
          color = palette.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (!summary.isNullOrBlank()) {
          if (isV2) {
            Text(
              text = summary,
              style = MaterialTheme.typography.bodyMedium,
              fontSize = 13.5.sp,
              color = palette.textSecondary,
              maxLines = 1,
            )
          } else {
            Surface(
              color = palette.editorBg,
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.45f)),
            ) {
              Text(
                text = summary,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = palette.activePillFg,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
              )
            }
          }
        }
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowDown,
          contentDescription = if (expanded) "Collapse" else "Expand",
          tint = palette.textMuted,
          modifier = Modifier.size(18.dp).rotate(chevronRotation),
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.fillMaxWidth()) {
          SettingsHairlineDivider()
          Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            content = content,
          )
        }
      }
    }
  }
}

@Composable
fun SettingsMenuRow(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  value: String? = null,
  selected: Boolean = false,
  statusDotColor: Color? = null,
  showChevron: Boolean = true,
  useUnfoldIndicator: Boolean = false,
  tag: String? = null,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val rowBg by animateColorAsState(
    targetValue = if (selected) palette.activePillBg.copy(alpha = if (isV2) 0.45f else 0.65f) else Color.Transparent,
    animationSpec = tween(140),
    label = "menu_row_bg",
  )
  val iconBg by animateColorAsState(
    targetValue = if (selected) palette.activePillBg else palette.editorBg,
    animationSpec = tween(140),
    label = "menu_row_icon_bg",
  )
  val iconTint by animateColorAsState(
    targetValue =
      when {
        selected -> palette.activePillFg
        isV2 -> palette.textPrimary
        else -> palette.textSecondary
      },
    animationSpec = tween(140),
    label = "menu_row_icon_tint",
  )

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = if (isV2) 56.dp else 52.dp)
        .background(rowBg)
        .clickable(onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .padding(horizontal = if (isV2) 18.dp else 14.dp, vertical = if (isV2) 13.dp else 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(if (isV2) 16.dp else 12.dp),
  ) {
    if (isV2) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(21.dp),
      )
    } else {
      Box(
        modifier =
          Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(iconBg)
            .border(
              0.75.dp,
              if (selected) palette.activePillFg.copy(alpha = 0.35f) else palette.border.copy(alpha = 0.5f),
              RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(16.dp),
        )
      }
    }

    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
      fontSize = if (isV2) 15.5.sp else 14.5.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color = palette.textPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )

    if (statusDotColor != null) {
      Box(
        modifier =
          Modifier
            .size(6.5.dp)
            .clip(CircleShape)
            .background(statusDotColor),
      )
    }

    if (!value.isNullOrBlank()) {
      Text(
        text = value,
        fontFamily = if (isV2) MaterialTheme.typography.bodyMedium.fontFamily else JetBrainsMono,
        fontSize = if (isV2) 14.sp else 11.sp,
        color = if (selected) palette.activePillFg else palette.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (useUnfoldIndicator) {
      Icon(
        imageVector = Icons.Outlined.UnfoldMore,
        contentDescription = null,
        tint = palette.textSecondary,
        modifier = Modifier.size(18.dp),
      )
    } else if (showChevron) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = palette.textMuted.copy(alpha = 0.75f),
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
fun SettingsHairlineDivider(
  modifier: Modifier = Modifier,
  startIndent: Dp = 0.dp,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  HorizontalDivider(
    color = if (isV2) palette.border else palette.border.copy(alpha = 0.4f),
    thickness = if (isV2) 0.5.dp else 0.75.dp,
    modifier = modifier.padding(start = startIndent),
  )
}

@Composable
fun SettingsToggleRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: ImageVector? = null,
  horizontalPadding: Dp = 14.dp,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = if (isV2) 54.dp else 50.dp)
        .clickable(role = Role.Switch) { onCheckedChange(!checked) }
        .padding(horizontal = if (isV2 && horizontalPadding == 14.dp) 18.dp else horizontalPadding, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(if (isV2) 16.dp else 12.dp),
  ) {
    if (leadingIcon != null) {
      val iconBg by animateColorAsState(
        targetValue = if (checked) palette.activePillBg.copy(alpha = 0.8f) else palette.editorBg,
        animationSpec = tween(140),
        label = "toggle_icon_bg",
      )
      val iconTint by animateColorAsState(
        targetValue = if (checked) palette.activePillFg else if (isV2) palette.textPrimary else palette.textMuted,
        animationSpec = tween(140),
        label = "toggle_icon_tint",
      )
      if (isV2) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(20.dp),
        )
      } else {
        Box(
          modifier =
            Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(iconBg)
              .border(0.75.dp, palette.border.copy(alpha = 0.45f), RoundedCornerShape(7.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(15.dp),
          )
        }
      }
    }

    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
      fontSize = if (isV2) 15.sp else 14.sp,
      fontWeight = if (isV2) FontWeight.Normal else FontWeight.Medium,
      color = palette.textPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )

    SettingsToggleSwitch(checked = checked)
  }
}

@Composable
fun SettingsToggleSwitch(
  checked: Boolean,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val trackBg by animateColorAsState(
    targetValue = if (checked) palette.activePillBg else palette.editorBg,
    animationSpec = tween(150),
    label = "settings_switch_track",
  )
  val borderColor by animateColorAsState(
    targetValue = if (checked) palette.activePillFg.copy(alpha = 0.55f) else palette.border.copy(alpha = 0.7f),
    animationSpec = tween(150),
    label = "settings_switch_border",
  )
  val thumbColor by animateColorAsState(
    targetValue = if (checked) palette.activePillFg else palette.textMuted,
    animationSpec = tween(150),
    label = "settings_switch_thumb",
  )
  val thumbOffset by animateDpAsState(
    targetValue = if (checked) 18.dp else 2.dp,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
    label = "settings_switch_offset",
  )

  Box(
    modifier =
      modifier
        .width(42.dp)
        .height(24.dp)
        .clip(CircleShape)
        .background(trackBg)
        .border(0.75.dp, borderColor, CircleShape)
        .padding(horizontal = 2.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      modifier =
        Modifier
          .offset(x = thumbOffset)
          .size(16.dp)
          .clip(CircleShape)
          .background(thumbColor),
    )
  }
}

@Composable
fun SettingsSegmentedControl(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val railShape = RoundedCornerShape(10.dp)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(railShape)
        .background(palette.editorBg)
        .border(0.75.dp, palette.border.copy(alpha = 0.6f), railShape)
        .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    options.forEachIndexed { idx, label ->
      val selected = idx == selectedIndex
      val bg by animateColorAsState(
        targetValue = if (selected) palette.activePillBg else Color.Transparent,
        animationSpec = tween(140),
        label = "seg_bg_$idx",
      )
      val fg by animateColorAsState(
        targetValue = if (selected) palette.activePillFg else palette.textSecondary,
        animationSpec = tween(140),
        label = "seg_fg_$idx",
      )
      Box(
        modifier =
          Modifier
            .weight(1f)
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .then(
              if (selected) Modifier.border(0.75.dp, palette.activePillFg.copy(alpha = 0.3f), RoundedCornerShape(7.dp))
              else Modifier,
            )
            .clickable { onSelect(idx) },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = label,
          fontSize = 11.5.sp,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
          color = fg,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun SettingsChoiceChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: ImageVector? = null,
  monospace: Boolean = false,
) {
  val palette = LocalIdePalette.current
  val bg by animateColorAsState(
    targetValue = if (selected) palette.activePillBg else palette.editorBg,
    animationSpec = tween(140),
    label = "choice_chip_bg",
  )
  val border by animateColorAsState(
    targetValue = if (selected) palette.activePillFg.copy(alpha = 0.38f) else palette.border.copy(alpha = 0.55f),
    animationSpec = tween(140),
    label = "choice_chip_border",
  )
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(9.dp),
    color = bg,
    border = BorderStroke(0.75.dp, border),
    modifier = modifier.heightIn(min = 38.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (leadingIcon != null) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = if (selected) palette.activePillFg else palette.textMuted,
          modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
      }
      Text(
        text = label,
        fontFamily = if (monospace) JetBrainsMono else MaterialTheme.typography.labelMedium.fontFamily,
        fontSize = if (monospace) 11.sp else 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) palette.activePillFg else palette.textPrimary,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun SettingsMetricTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  accentColor: Color? = null,
) {
  val palette = LocalIdePalette.current
  Surface(
    color = palette.editorBg,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = value,
        fontFamily = JetBrainsMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = accentColor ?: palette.textPrimary,
        maxLines = 1,
      )
      Text(
        text = label.uppercase(),
        fontFamily = JetBrainsMono,
        fontSize = 8.5.sp,
        letterSpacing = 0.6.sp,
        color = palette.textMuted,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun SettingsStatusBanner(
  message: String,
  isError: Boolean,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val tone = if (isError) RoseError else EmeraldLive
  Surface(
    color = tone.copy(alpha = 0.11f),
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(0.75.dp, tone.copy(alpha = 0.36f)),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Icon(
        imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircleOutline,
        contentDescription = null,
        tint = tone,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = palette.textPrimary,
        modifier = Modifier.weight(1f),
      )
    }
  }
}
