package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BadgeTs
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError

enum class EdgeMenuTone {
  DEFAULT,
  ACCENT,
  WARNING,
  DANGER,
}

@Immutable
data class EdgeMenuQuickAction(
  val label: String,
  val icon: ImageVector,
  val active: Boolean = false,
  val tone: EdgeMenuTone = EdgeMenuTone.DEFAULT,
  val testTag: String? = null,
  val onClick: () -> Unit,
)

@Immutable
data class EdgeMenuUiLayer(
  val code: String,
  val label: String,
  val subtitle: String,
  val visible: Boolean,
  val testTag: String? = null,
  val onToggle: () -> Unit,
)

/**
 * Shared HTTP verb indicator color for API & Worker test menus.
 */
fun edgeHttpVerbColor(method: String, fallback: Color): Color =
  when (method.uppercase()) {
    "GET" -> EmeraldLive
    "POST" -> AmberWarn
    "PUT" -> BadgeTs
    "PATCH" -> Color(0xFFC084FC)
    "DELETE" -> RoseError
    else -> fallback
  }

/**
 * Ultra-thin editorial floating command menu that replaces stock Material 3 DropdownMenu styling
 * consistently across EdgeCode. Preserves native M3 popup anchoring, auto-flipping, scrolling,
 * and back-dismissal while providing razor-thin hairline framing and precision IDE density.
 */
@Composable
fun EdgeMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  minWidth: Dp = 212.dp,
  maxWidth: Dp = 276.dp,
  offset: DpOffset = DpOffset(0.dp, 6.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val palette = LocalIdePalette.current
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    offset = offset,
    shape = RoundedCornerShape(14.dp),
    containerColor = palette.chromeSurface,
    tonalElevation = 0.dp,
    shadowElevation = 8.dp,
    border = BorderStroke(0.75.dp, palette.glassBorderHighlight),
    modifier = modifier.widthIn(min = minWidth, max = maxWidth),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp),
      verticalArrangement = Arrangement.spacedBy(1.dp),
      content = content,
    )
  }
}

/**
 * Matching floating Popup surface for tree-row and contextual overlays that anchor via Compose Popup.
 */
@Composable
fun EdgeFloatingMenuPopup(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  alignment: Alignment = Alignment.TopEnd,
  minWidth: Dp = 208.dp,
  maxWidth: Dp = 256.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (!expanded) return
  val palette = LocalIdePalette.current
  Popup(
    alignment = alignment,
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(focusable = true),
  ) {
    Surface(
      color = palette.chromeSurface,
      shape = RoundedCornerShape(14.dp),
      tonalElevation = 0.dp,
      shadowElevation = 8.dp,
      border = BorderStroke(0.75.dp, palette.glassBorderHighlight),
      modifier = Modifier.padding(top = 4.dp).widthIn(min = minWidth, max = maxWidth),
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 5.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        content = content,
      )
    }
  }
}

/**
 * Crisp top anchor header strip inside a menu (e.g. project name, file path, or picker title).
 */
@Composable
fun EdgeMenuHeader(
  title: String,
  subtitle: String? = null,
  badgeText: String? = null,
  leadingIcon: ImageVector? = null,
  leadingContent: (@Composable () -> Unit)? = null,
) {
  val palette = LocalIdePalette.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    when {
      leadingContent != null -> leadingContent()
      leadingIcon != null -> {
        Box(
          modifier =
            Modifier
              .size(24.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(palette.activePillBg.copy(alpha = 0.7f))
              .border(0.75.dp, palette.activePillFg.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = palette.activePillFg,
            modifier = Modifier.size(13.dp),
          )
        }
      }
    }

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          fontFamily = JetBrainsMono,
          fontSize = 9.5.sp,
          color = palette.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    if (!badgeText.isNullOrBlank()) {
      Box(
        modifier =
          Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(palette.chromePill)
            .border(0.75.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
      ) {
        Text(
          text = badgeText,
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          fontWeight = FontWeight.SemiBold,
          color = palette.textSecondary,
          maxLines = 1,
        )
      }
    }
  }
  EdgeMenuDivider(verticalPadding = 3.dp)
}

/**
 * Compact horizontal row of quick-access tonal action tiles at the top of rich overflow menus.
 */
@Composable
fun EdgeMenuQuickActionRow(
  actions: List<EdgeMenuQuickAction>,
) {
  if (actions.isEmpty()) return
  val palette = LocalIdePalette.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    actions.forEach { action ->
      val (bg, borderColor, iconTint, labelColor) =
        when {
          action.active || action.tone == EdgeMenuTone.ACCENT ->
            QuadStyle(
              bg = palette.activePillBg.copy(alpha = 0.85f),
              border = palette.activePillFg.copy(alpha = 0.35f),
              icon = palette.activePillFg,
              label = palette.activePillFg,
            )
          action.tone == EdgeMenuTone.WARNING ->
            QuadStyle(
              bg = AmberWarn.copy(alpha = 0.12f),
              border = AmberWarn.copy(alpha = 0.30f),
              icon = AmberWarn,
              label = AmberWarn,
            )
          action.tone == EdgeMenuTone.DANGER ->
            QuadStyle(
              bg = RoseError.copy(alpha = 0.12f),
              border = RoseError.copy(alpha = 0.30f),
              icon = RoseError,
              label = RoseError,
            )
          else ->
            QuadStyle(
              bg = palette.chromePill.copy(alpha = 0.75f),
              border = palette.border.copy(alpha = 0.75f),
              icon = palette.textPrimary,
              label = palette.textSecondary,
            )
        }

      Column(
        modifier =
          Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(0.75.dp, borderColor, RoundedCornerShape(9.dp))
            .clickable(onClick = action.onClick)
            .then(if (action.testTag != null) Modifier.testTag(action.testTag) else Modifier)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
      ) {
        Icon(
          imageVector = action.icon,
          contentDescription = action.label,
          tint = iconTint,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = action.label,
          style = MaterialTheme.typography.labelSmall,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          color = labelColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Interactive 4-layer UI chrome customization card rendered inside the workspace overflow menu.
 * Lets users toggle each UI layer (Top Bar, Tabs & Path, Editor Bar, Bottom Dock) live one-by-one
 * without dismissing the menu, or toggle all layers at once.
 */
@Composable
fun EdgeMenuUiLayersPanel(
  layers: List<EdgeMenuUiLayer>,
  onToggleAll: (hideAll: Boolean) -> Unit,
) {
  if (layers.isEmpty()) return
  val palette = LocalIdePalette.current
  val visibleCount = layers.count { it.visible }
  val allHidden = visibleCount == 0
  val containerShape = RoundedCornerShape(10.dp)

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 4.dp)
        .clip(containerShape)
        .background(palette.editorBg.copy(alpha = 0.72f))
        .border(0.75.dp, palette.border.copy(alpha = 0.65f), containerShape)
        .padding(horizontal = 8.dp, vertical = 7.dp)
        .testTag("ui_layers_panel"),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Layers,
        contentDescription = null,
        tint = palette.activePillFg,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = "UI LAYERS",
        fontFamily = JetBrainsMono,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = palette.textMuted,
      )
      Box(
        modifier =
          Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (allHidden) AmberWarn.copy(alpha = 0.14f) else palette.activePillBg.copy(alpha = 0.75f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
      ) {
        Text(
          text = "$visibleCount/${layers.size}",
          fontFamily = JetBrainsMono,
          fontSize = 8.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = if (allHidden) AmberWarn else palette.activePillFg,
        )
      }
      Spacer(Modifier.weight(1f))
      Box(
        modifier =
          Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(palette.chromePill)
            .border(0.5.dp, palette.border.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
            .clickable { onToggleAll(!allHidden) }
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("ui_layers_toggle_all"),
      ) {
        Text(
          text = if (allHidden) "Show All" else "Hide All",
          fontFamily = JetBrainsMono,
          fontSize = 8.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = palette.textSecondary,
        )
      }
    }

    layers.forEach { layer ->
      val rowBg by animateColorAsState(
        targetValue = if (layer.visible) palette.chromeSurface else Color.Transparent,
        animationSpec = tween(140),
        label = "layer_row_bg",
      )
      val pillBg by animateColorAsState(
        targetValue = if (layer.visible) palette.activePillBg else palette.chromePill.copy(alpha = 0.7f),
        animationSpec = tween(140),
        label = "layer_pill_bg",
      )
      val pillFg by animateColorAsState(
        targetValue = if (layer.visible) palette.activePillFg else palette.textMuted,
        animationSpec = tween(140),
        label = "layer_pill_fg",
      )

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(rowBg)
            .border(
              width = 0.5.dp,
              color = if (layer.visible) palette.border.copy(alpha = 0.55f) else palette.border.copy(alpha = 0.25f),
              shape = RoundedCornerShape(7.dp),
            )
            .clickable(onClick = layer.onToggle)
            .then(if (layer.testTag != null) Modifier.testTag(layer.testTag) else Modifier)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Box(
          modifier =
            Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(pillBg)
              .padding(horizontal = 4.dp, vertical = 1.5.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = layer.code,
            fontFamily = JetBrainsMono,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = pillFg,
          )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
          Text(
            text = layer.label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.5.sp,
            fontWeight = if (layer.visible) FontWeight.SemiBold else FontWeight.Medium,
            color = if (layer.visible) palette.textPrimary else palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (layer.subtitle.isNotBlank()) {
            Text(
              text = layer.subtitle,
              fontFamily = JetBrainsMono,
              fontSize = 8.5.sp,
              color = palette.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(
          modifier =
            Modifier
              .height(20.dp)
              .clip(RoundedCornerShape(999.dp))
              .background(pillBg)
              .border(
                0.5.dp,
                if (layer.visible) palette.activePillFg.copy(alpha = 0.35f) else palette.border.copy(alpha = 0.5f),
                RoundedCornerShape(999.dp),
              )
              .padding(horizontal = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Icon(
            imageVector = if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            contentDescription = if (layer.visible) "Hide ${layer.label}" else "Show ${layer.label}",
            tint = pillFg,
            modifier = Modifier.size(10.5.dp),
          )
          Text(
            text = if (layer.visible) "ON" else "OFF",
            fontFamily = JetBrainsMono,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = pillFg,
          )
        }
      }
    }
  }
}

/**
 * Tracked uppercase micro-section label separating menu groups cleanly.
 */
@Composable
fun EdgeMenuSection(title: String) {
  val palette = LocalIdePalette.current
  Text(
    text = title.uppercase(),
    fontFamily = JetBrainsMono,
    fontSize = 9.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.9.sp,
    color = palette.textMuted,
    maxLines = 1,
    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 3.dp),
  )
}

/**
 * Inset razor-thin hairline separator between menu groups.
 */
@Composable
fun EdgeMenuDivider(verticalPadding: Dp = 4.dp) {
  val palette = LocalIdePalette.current
  HorizontalDivider(
    thickness = 0.75.dp,
    color = palette.border.copy(alpha = 0.55f),
    modifier = Modifier.padding(horizontal = 8.dp, vertical = verticalPadding),
  )
}

/**
 * Signature EdgeCode action row: rounded pill touch zone, 26.dp tonal icon micro-well,
 * crisp single-line label, optional subtitle, mono trailing badge, and active selection state.
 */
@Composable
fun EdgeMenuItem(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  leadingContent: (@Composable () -> Unit)? = null,
  subtitle: String? = null,
  badgeText: String? = null,
  tone: EdgeMenuTone = EdgeMenuTone.DEFAULT,
  selected: Boolean = false,
  monospaced: Boolean = false,
  enabled: Boolean = true,
  trailingContent: (@Composable () -> Unit)? = null,
  testTag: String? = null,
) {
  val palette = LocalIdePalette.current
  val effectiveAlpha = if (enabled) 1f else 0.45f

  val (rowBg, rowBorder, iconWellBg, iconTint, labelTint, badgeFg, badgeBg) =
    when {
      selected ->
        MenuRowPalette(
          rowBg = palette.activePillBg.copy(alpha = 0.65f),
          rowBorder = palette.activePillFg.copy(alpha = 0.28f),
          iconWellBg = palette.activePillFg.copy(alpha = 0.16f),
          iconTint = palette.activePillFg,
          labelTint = palette.activePillFg,
          badgeFg = palette.activePillFg,
          badgeBg = palette.activePillFg.copy(alpha = 0.14f),
        )
      tone == EdgeMenuTone.ACCENT ->
        MenuRowPalette(
          rowBg = Color.Transparent,
          rowBorder = Color.Transparent,
          iconWellBg = palette.activePillBg.copy(alpha = 0.85f),
          iconTint = palette.activePillFg,
          labelTint = palette.activePillFg,
          badgeFg = palette.activePillFg,
          badgeBg = palette.activePillBg.copy(alpha = 0.75f),
        )
      tone == EdgeMenuTone.WARNING ->
        MenuRowPalette(
          rowBg = Color.Transparent,
          rowBorder = Color.Transparent,
          iconWellBg = AmberWarn.copy(alpha = 0.14f),
          iconTint = AmberWarn,
          labelTint = AmberWarn,
          badgeFg = AmberWarn,
          badgeBg = AmberWarn.copy(alpha = 0.14f),
        )
      tone == EdgeMenuTone.DANGER ->
        MenuRowPalette(
          rowBg = Color.Transparent,
          rowBorder = Color.Transparent,
          iconWellBg = RoseError.copy(alpha = 0.13f),
          iconTint = RoseError,
          labelTint = RoseError,
          badgeFg = RoseError,
          badgeBg = RoseError.copy(alpha = 0.13f),
        )
      else ->
        MenuRowPalette(
          rowBg = Color.Transparent,
          rowBorder = Color.Transparent,
          iconWellBg = palette.chromePill.copy(alpha = 0.85f),
          iconTint = palette.textSecondary,
          labelTint = palette.textPrimary,
          badgeFg = palette.textMuted,
          badgeBg = palette.chromePill,
        )
    }

  val shape = RoundedCornerShape(9.dp)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = if (subtitle != null) 44.dp else 38.dp)
        .clip(shape)
        .background(rowBg)
        .then(
          if (rowBorder != Color.Transparent) Modifier.border(0.75.dp, rowBorder, shape)
          else Modifier
        )
        .clickable(enabled = enabled, onClick = onClick)
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .padding(horizontal = 8.dp, vertical = if (subtitle != null) 5.dp else 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(9.dp),
  ) {
    when {
      leadingContent != null -> leadingContent()
      icon != null -> {
        Box(
          modifier =
            Modifier
              .size(26.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(iconWellBg),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint.copy(alpha = effectiveAlpha),
            modifier = Modifier.size(14.5.dp),
          )
        }
      }
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      Text(
        text = label,
        fontFamily = if (monospaced) JetBrainsMono else MaterialTheme.typography.bodyMedium.fontFamily,
        fontSize = if (monospaced) 11.5.sp else 13.sp,
        fontWeight = if (selected || tone != EdgeMenuTone.DEFAULT) FontWeight.SemiBold else FontWeight.Medium,
        color = labelTint.copy(alpha = effectiveAlpha),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          fontFamily = JetBrainsMono,
          fontSize = 9.5.sp,
          color = palette.textMuted.copy(alpha = effectiveAlpha),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    if (!badgeText.isNullOrBlank()) {
      Box(
        modifier =
          Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(badgeBg)
            .border(0.5.dp, palette.border.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
      ) {
        Text(
          text = badgeText,
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          fontWeight = FontWeight.SemiBold,
          color = badgeFg,
          maxLines = 1,
        )
      }
    }

    if (trailingContent != null) {
      trailingContent()
    } else if (selected) {
      Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = "Selected",
        tint = palette.activePillFg,
        modifier = Modifier.size(15.dp),
      )
    }
  }
}

/**
 * Compact selector row for crisp dropdown pickers (HTTP methods, zoom levels, branches, zones,
 * accounts, sort modes, assertion targets/operators).
 */
@Composable
fun EdgeMenuCompactItem(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  leadingColorDot: Color? = null,
  subtitle: String? = null,
  badgeText: String? = null,
  selected: Boolean = false,
  monospaced: Boolean = false,
  accentColor: Color? = null,
  testTag: String? = null,
) {
  val palette = LocalIdePalette.current
  val shape = RoundedCornerShape(8.dp)
  val activeTint = accentColor ?: palette.activePillFg

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = if (subtitle != null) 40.dp else 36.dp)
        .clip(shape)
        .background(if (selected) palette.activePillBg.copy(alpha = 0.65f) else Color.Transparent)
        .then(
          if (selected) Modifier.border(0.75.dp, activeTint.copy(alpha = 0.28f), shape)
          else Modifier
        )
        .clickable(onClick = onClick)
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .padding(horizontal = 9.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (leadingColorDot != null) {
      Box(
        modifier =
          Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(leadingColorDot),
      )
    } else if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) activeTint else palette.textSecondary,
        modifier = Modifier.size(15.dp),
      )
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      Text(
        text = label,
        fontFamily = if (monospaced) JetBrainsMono else MaterialTheme.typography.bodyMedium.fontFamily,
        fontSize = if (monospaced) 11.5.sp else 12.5.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color =
          when {
            accentColor != null -> accentColor
            selected -> palette.activePillFg
            else -> palette.textPrimary
          },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          fontFamily = JetBrainsMono,
          fontSize = 9.5.sp,
          color = palette.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    if (!badgeText.isNullOrBlank()) {
      Box(
        modifier =
          Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(palette.chromePill)
            .padding(horizontal = 5.dp, vertical = 1.5.dp),
      ) {
        Text(
          text = badgeText,
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          fontWeight = FontWeight.Medium,
          color = palette.textMuted,
          maxLines = 1,
        )
      }
    }

    if (selected) {
      Spacer(Modifier.width(2.dp))
      Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = "Selected",
        tint = activeTint,
        modifier = Modifier.size(14.5.dp),
      )
    }
  }
}

private data class QuadStyle(
  val bg: Color,
  val border: Color,
  val icon: Color,
  val label: Color,
)

private data class MenuRowPalette(
  val rowBg: Color,
  val rowBorder: Color,
  val iconWellBg: Color,
  val iconTint: Color,
  val labelTint: Color,
  val badgeFg: Color,
  val badgeBg: Color,
)
