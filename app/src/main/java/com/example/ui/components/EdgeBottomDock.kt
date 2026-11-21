package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import com.example.ui.theme.LocalGlassBackdrop
import com.example.ui.theme.glassLens
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.FloatingBarStyleConfig
import com.example.data.security.IconLabelBehavior
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalBarStylePreferences
import com.example.ui.theme.LocalEditorSettings
import com.example.ui.theme.LocalIconLabelBehavior
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.UiStyleVersion
import com.example.ui.theme.LiquidGlassScrubLensOverlay
import com.example.ui.theme.liquidGlassScrubHost
import com.example.ui.theme.liquidGlassScrubItem
import com.example.ui.theme.liquidGlassSurface
import com.example.ui.theme.liquidGlassTouchGlow
import com.example.ui.theme.rememberLiquidGlassAdaptiveColors
import com.example.ui.theme.rememberLiquidGlassScrubState

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
  barStyle: FloatingBarStyleConfig? = null,
  forceStyleOverride: UiStyleVersion? = null,
  onHideDock: (() -> Unit)? = null,
  hideDockTestTag: String = "hide_bottom_dock_button",
) {
  val palette = LocalIdePalette.current
  val effectiveStyle = forceStyleOverride ?: palette.uiStyle
  val isV2 = effectiveStyle == UiStyleVersion.V2_MINIMAL_OLED
  val cfg = (barStyle ?: LocalBarStylePreferences.current.homeBar).normalized()

  if (isV2) {
    LiquidGlassFloatingDock(
      items = items,
      cfg = cfg,
      onHideDock = onHideDock,
      hideDockTestTag = hideDockTestTag,
      modifier = modifier,
    )
  } else {
    ClassicDockedBar(
      items = items,
      cfg = cfg,
      onHideDock = onHideDock,
      hideDockTestTag = hideDockTestTag,
      modifier = modifier,
    )
  }
}

@Composable
private fun LiquidGlassFloatingDock(
  items: List<EdgeBottomDockItem>,
  cfg: FloatingBarStyleConfig,
  onHideDock: (() -> Unit)?,
  hideDockTestTag: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val settings = LocalEditorSettings.current
  val isDark = palette.isDark
  val labelBehavior = LocalIconLabelBehavior.current
  val capsuleShape = RoundedCornerShape(cfg.cornerRadiusDp.dp)
  val hideOrbSize = (cfg.heightDp - 10).coerceIn(34, 44).dp
  val liquidEnabled = settings.liquidGlassEnabled
  val glassBackdrop = LocalGlassBackdrop.current
  val useRealGlass = liquidEnabled && glassBackdrop != null

  val adaptiveGlass =
    rememberLiquidGlassAdaptiveColors(
      palette = palette,
      tintFraction = settings.liquidGlassTint,
      thickness = 1.25f,
      surfaceOpacityMultiplier = (cfg.opacity / 0.65f).coerceIn(0.45f, 1.35f),
    )

  val topGlassTint =
    if (isDark) {
      lerp(Color(0xFF2B282A), palette.chromeSurface, 0.35f).copy(alpha = (cfg.opacity * 0.96f).coerceIn(0.12f, 1f))
    } else {
      lerp(Color(0xFFFAFAFC), palette.chromeSurface, 0.30f).copy(alpha = (cfg.opacity * 0.96f).coerceIn(0.18f, 1f))
    }
  val bottomGlassTint =
    if (isDark) {
      lerp(Color(0xFF151416), palette.chromeBg, 0.32f).copy(alpha = (cfg.opacity * 1.04f).coerceIn(0.15f, 1f))
    } else {
      lerp(Color(0xFFE7E9EF), palette.chromeSurface, 0.35f).copy(alpha = (cfg.opacity * 1.02f).coerceIn(0.22f, 1f))
    }
  val glassBodyBrush = Brush.verticalGradient(listOf(topGlassTint, bottomGlassTint))

  val rimBrush =
    if (cfg.borderOpacity > 0.01f) {
      if (isDark) {
        Brush.verticalGradient(
          colors =
            listOf(
              Color.White.copy(alpha = (cfg.borderOpacity * 1.25f).coerceAtMost(0.92f)),
              Color.White.copy(alpha = (cfg.borderOpacity * 0.36f).coerceAtMost(0.55f)),
              Color.White.copy(alpha = (cfg.borderOpacity * 0.72f).coerceAtMost(0.75f)),
            ),
        )
      } else {
        Brush.verticalGradient(
          colors =
            listOf(
              Color.White.copy(alpha = (cfg.borderOpacity * 1.35f).coerceAtMost(0.96f)),
              palette.border.copy(alpha = (cfg.borderOpacity * 0.55f).coerceAtMost(0.65f)),
              Color.Black.copy(alpha = (cfg.borderOpacity * 0.24f).coerceAtMost(0.32f)),
            ),
        )
      }
    } else {
      null
    }

  val sheenAlpha = if (isDark) (cfg.borderOpacity * 0.22f).coerceIn(0f, 0.14f) else (cfg.borderOpacity * 0.38f).coerceIn(0f, 0.24f)
  val elevationDp = (14f * cfg.opacity).coerceIn(2f, 16f).dp

  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = cfg.bottomMarginDp.dp),
    contentAlignment = Alignment.Center,
  ) {
    val reservedSide = if (onHideDock != null) hideOrbSize + 10.dp else 0.dp
    val maxCapsuleWidth = (maxWidth - reservedSide).coerceAtLeast(164.dp)
    val capsuleWidth = (maxWidth * (cfg.widthPercent / 100f)).coerceIn(164.dp.coerceAtMost(maxCapsuleWidth), maxCapsuleWidth)

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val dockScrubState = rememberLiquidGlassScrubState()
      val innerCorner = (cfg.cornerRadiusDp - 3).coerceAtLeast(10).dp
      Box(
        modifier =
          Modifier
            .width(capsuleWidth)
            .height(cfg.heightDp.dp)
            .then(
              if (useRealGlass && glassBackdrop != null) {
                Modifier
                  .shadow(
                    elevation = elevationDp,
                    shape = capsuleShape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.18f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.65f else 0.22f),
                  )
                  .glassLens(glassBackdrop, cfg.cornerRadiusDp.dp, isDark)
              } else if (liquidEnabled) {
                Modifier.liquidGlassSurface(
                  shape = capsuleShape,
                  cornerRadius = cfg.cornerRadiusDp.dp,
                  thickness = 1.25f,
                  surfaceOpacityMultiplier = (cfg.opacity / 0.65f).coerceIn(0.45f, 1.35f),
                  borderOpacityMultiplier = (cfg.borderOpacity / 0.35f).coerceIn(0f, 1.4f),
                  drawShadow = true,
                )
              } else {
                Modifier
                  .shadow(
                    elevation = elevationDp,
                    shape = capsuleShape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.65f else 0.25f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.75f else 0.30f),
                  )
                  .clip(capsuleShape)
                  .background(glassBodyBrush)
                  .then(
                    if (rimBrush != null) Modifier.border(width = 1.dp, brush = rimBrush, shape = capsuleShape)
                    else Modifier,
                  )
              },
            )
            .liquidGlassScrubHost(
              state = dockScrubState,
              itemCount = items.size,
              onCommitItem = { idx -> items.getOrNull(idx)?.onClick?.invoke() },
            ),
      ) {
        if (sheenAlpha > 0.005f) {
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .height((cfg.heightDp * 0.42f).dp)
                .background(
                  Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = sheenAlpha), Color.Transparent),
                  ),
                ),
          )
        }

        Box(
          modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
          contentAlignment = Alignment.Center,
        ) {
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(cfg.iconSpacingDp.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val itemHeight = (cfg.heightDp - 8).coerceAtLeast(36).dp
            val innerShape = RoundedCornerShape(innerCorner)

            items.forEachIndexed { index, item ->
              val interactionSource = remember(item.testTag) { MutableInteractionSource() }
              val scrubHovered = dockScrubState.isItemScrubHovered(index)
              val scrubProgress by animateFloatAsState(
                targetValue = if (scrubHovered) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 340f),
                label = "v2_dock_scrub",
              )
              val active = item.selected
              val activeBgTarget =
                if (active) {
                  palette.activePillBg.copy(alpha = (cfg.activePillOpacity + 0.56f).coerceIn(0.42f, 0.94f))
                } else {
                  Color.Transparent
                }
              val pillBg by animateColorAsState(
                targetValue = activeBgTarget,
                animationSpec = tween(durationMillis = 190),
                label = "v2_dock_pill_bg",
              )
              val iconTint by animateColorAsState(
                targetValue =
                  when {
                    scrubHovered || active -> palette.activePillFg
                    liquidEnabled -> adaptiveGlass.foregroundSecondary
                    isDark -> Color.White.copy(alpha = 0.76f)
                    else -> palette.textSecondary
                  },
                animationSpec = tween(durationMillis = 150),
                label = "v2_dock_icon_tint",
              )
              val iconScale by animateFloatAsState(
                targetValue =
                  when {
                    scrubHovered -> 1.16f
                    active -> 1.07f
                    else -> 1f
                  },
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
                label = "v2_dock_icon_scale",
              )

              val showLabel =
                when {
                  cfg.showLabels -> true
                  labelBehavior == IconLabelBehavior.ALWAYS_SHOW -> true
                  labelBehavior == IconLabelBehavior.ICON_ONLY -> false
                  else -> active
                }

              val horizontalPad =
                when {
                  showLabel && active -> 14.dp
                  showLabel -> 10.dp
                  active -> 16.dp
                  else -> 11.dp
                }

              Box(
                modifier =
                  Modifier
                    .liquidGlassScrubItem(dockScrubState, index)
                    .height(itemHeight)
                    .then(
                      if (useRealGlass && glassBackdrop != null) {
                        Modifier.glassLens(glassBackdrop, innerCorner, isDark, press = { scrubProgress }, bubble = true)
                      } else {
                        Modifier
                      },
                    )
                    .clip(innerShape)
                    .background(pillBg)
                    .then(
                      if (active && cfg.borderOpacity > 0.02f) {
                        Modifier.border(
                          width = 0.75.dp,
                          color = palette.activePillFg.copy(alpha = 0.34f),
                          shape = innerShape,
                        )
                      } else {
                        Modifier
                      },
                    )
                    .liquidGlassTouchGlow(glowColor = palette.activePillFg)
                    .clickable(
                      interactionSource = interactionSource,
                      indication = null,
                      role = Role.Tab,
                      onClick = item.onClick,
                    )
                    .animateContentSize(animationSpec = tween(180))
                    .testTag(item.testTag),
                contentAlignment = Alignment.Center,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = horizontalPad),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                ) {
                  Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = iconTint,
                    modifier =
                      Modifier
                        .size(cfg.iconSizeDp.dp)
                        .graphicsLayer {
                          scaleX = iconScale
                          scaleY = iconScale
                        },
                  )

                  AnimatedVisibility(
                    visible = showLabel,
                    enter = fadeIn(tween(150)) + expandHorizontally(tween(180)),
                    exit = fadeOut(tween(110)) + shrinkHorizontally(tween(140)),
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Spacer(Modifier.width(6.dp))
                      Text(
                        text = item.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iconTint,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }

                  if (cfg.showBadges && !item.badgeText.isNullOrBlank()) {
                    Spacer(Modifier.width(5.dp))
                    DockInlineCounterBadge(
                      text = item.badgeText,
                      customColor = item.badgeColor,
                      active = active || scrubHovered,
                      isV2Glass = true,
                    )
                  }
                }
              }
            }
          }
        }

        if (!useRealGlass) {
          LiquidGlassScrubLensOverlay(
            state = dockScrubState,
            cornerRadius = innerCorner,
            thickness = 1.45f,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      if (onHideDock != null) {
        Box(
          modifier =
            Modifier
              .size(hideOrbSize)
              .then(
                if (liquidEnabled) {
                  Modifier.liquidGlassSurface(
                    shape = CircleShape,
                    cornerRadius = hideOrbSize / 2,
                    thickness = 1.25f,
                    surfaceOpacityMultiplier = (cfg.opacity / 0.65f).coerceIn(0.45f, 1.35f),
                    borderOpacityMultiplier = (cfg.borderOpacity / 0.35f).coerceIn(0f, 1.4f),
                    drawShadow = true,
                  )
                } else {
                  Modifier
                    .shadow(
                      elevation = (elevationDp.value * 0.85f).dp,
                      shape = CircleShape,
                      ambientColor = Color.Black.copy(alpha = if (isDark) 0.6f else 0.22f),
                      spotColor = Color.Black.copy(alpha = if (isDark) 0.7f else 0.28f),
                    )
                    .clip(CircleShape)
                    .background(glassBodyBrush)
                    .then(
                      if (rimBrush != null) Modifier.border(width = 1.dp, brush = rimBrush, shape = CircleShape)
                      else Modifier,
                    )
                },
              )
              .liquidGlassTouchGlow(glowColor = palette.activePillFg)
              .clickable(role = Role.Button, onClick = onHideDock)
              .testTag(hideDockTestTag),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = "Hide bottom menu",
            tint = if (liquidEnabled) adaptiveGlass.foregroundPrimary else palette.textSecondary,
            modifier = Modifier.size(19.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ClassicDockedBar(
  items: List<EdgeBottomDockItem>,
  cfg: FloatingBarStyleConfig,
  onHideDock: (() -> Unit)?,
  hideDockTestTag: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val barHeight = cfg.heightDp.coerceIn(48, 76).dp
  val iconSize = cfg.iconSizeDp.coerceIn(16, 26).dp

  Column(
    modifier = modifier.fillMaxWidth().background(palette.glassDockBg.copy(alpha = cfg.opacity)),
  ) {
    HorizontalDivider(
      color = palette.border.copy(alpha = (cfg.borderOpacity + 0.22f).coerceIn(0f, 0.85f)),
      thickness = 0.75.dp,
    )
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(barHeight)
          .padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val classicScrubState = rememberLiquidGlassScrubState()
      Box(
        modifier =
          Modifier
            .weight(1f)
            .liquidGlassScrubHost(
              state = classicScrubState,
              itemCount = items.size,
              onCommitItem = { idx -> items.getOrNull(idx)?.onClick?.invoke() },
            ),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(cfg.iconSpacingDp.coerceIn(4, 34).dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val labelBehavior = LocalIconLabelBehavior.current
          items.forEachIndexed { index, item ->
            val interactionSource = remember(item.testTag) { MutableInteractionSource() }
            val scrubHovered = classicScrubState.isItemScrubHovered(index)
            val active = item.selected
            val showLabel =
              when {
                cfg.showLabels -> true
                labelBehavior == IconLabelBehavior.ALWAYS_SHOW -> true
                labelBehavior == IconLabelBehavior.ICON_ONLY -> false
                else -> active
              }

            val pillBg by animateColorAsState(
              targetValue = if (active) palette.activePillBg else Color.Transparent,
              animationSpec = tween(durationMillis = 180),
              label = "dock_pill_bg",
            )
            val iconTint by animateColorAsState(
              targetValue = if (scrubHovered || active) palette.activePillFg else palette.textSecondary,
              animationSpec = tween(durationMillis = 150),
              label = "dock_icon_tint",
            )
            val iconScale by animateFloatAsState(
              targetValue =
                when {
                  scrubHovered -> 1.14f
                  active -> 1.06f
                  else -> 1f
                },
              animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
              label = "dock_icon_scale",
            )

            Box(
              modifier =
                Modifier
                  .liquidGlassScrubItem(classicScrubState, index)
                  .height((cfg.heightDp - 12).coerceIn(36, 52).dp)
                  .clip(RoundedCornerShape(cfg.cornerRadiusDp.dp))
                  .liquidGlassTouchGlow(glowColor = palette.activePillFg)
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
                shape = RoundedCornerShape(cfg.cornerRadiusDp.dp),
                border =
                  if (active) BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.30f))
                  else null,
                modifier = Modifier.height((cfg.heightDp - 14).coerceIn(34, 48).dp).animateContentSize(animationSpec = tween(180)),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = if (showLabel) 13.dp else 11.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                ) {
                  Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = iconTint,
                    modifier =
                      Modifier
                        .size(iconSize)
                        .graphicsLayer {
                          scaleX = iconScale
                          scaleY = iconScale
                        },
                  )

                  AnimatedVisibility(
                    visible = showLabel,
                    enter = fadeIn(tween(150)) + expandHorizontally(tween(180)),
                    exit = fadeOut(tween(110)) + shrinkHorizontally(tween(140)),
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Spacer(Modifier.width(6.dp))
                      Text(
                        text = item.label,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = iconTint,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }

                  if (cfg.showBadges && !item.badgeText.isNullOrBlank()) {
                    Spacer(Modifier.width(5.dp))
                    DockInlineCounterBadge(
                      text = item.badgeText,
                      customColor = item.badgeColor,
                      active = active || scrubHovered,
                      isV2Glass = false,
                    )
                  }
                }
              }
            }
          }
        }
        LiquidGlassScrubLensOverlay(
          state = classicScrubState,
          cornerRadius = cfg.cornerRadiusDp.dp,
          thickness = 1.35f,
          modifier = Modifier.matchParentSize(),
        )
      }

      if (onHideDock != null) {
        Surface(
          onClick = onHideDock,
          shape = CircleShape,
          color = palette.chromeSurface,
          border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.7f)),
          modifier =
            Modifier
              .padding(start = 4.dp, end = 2.dp)
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

@Composable
private fun DockInlineCounterBadge(
  text: String,
  customColor: Color?,
  active: Boolean,
  isV2Glass: Boolean,
) {
  val palette = LocalIdePalette.current
  val badgeBg =
    when {
      customColor != null -> customColor
      isV2Glass -> palette.activePillFg.copy(alpha = if (active) 0.24f else 0.16f)
      active -> palette.ctaBg
      else -> palette.chromePill
    }
  val badgeFg =
    when {
      customColor != null -> Color(0xFF141806)
      isV2Glass -> palette.activePillFg
      active -> palette.ctaFg
      else -> palette.textSecondary
    }
  Box(
    modifier =
      Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(badgeBg)
        .padding(horizontal = 5.dp, vertical = 1.5.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      fontFamily = JetBrainsMono,
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = badgeFg,
      maxLines = 1,
      softWrap = false,
    )
  }
}
