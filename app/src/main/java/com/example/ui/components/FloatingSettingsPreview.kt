package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.theme.CaMotion
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import kotlin.math.roundToInt

@Stable
data class FloatingPreviewEntry(
  val id: String,
  val title: String,
  val shortLabel: String,
  val order: Int,
  val content: @Composable () -> Unit,
)

@Stable
class FloatingPreviewController {
  private val entries = mutableStateMapOf<String, FloatingPreviewEntry>()

  var activeId by mutableStateOf<String?>(null)
    private set

  var isFloating by mutableStateOf(true)
    private set

  var isMinimized by mutableStateOf(false)
    private set

  var offsetX by mutableFloatStateOf(0f)
    private set

  var offsetY by mutableFloatStateOf(0f)
    private set

  var hasCustomPosition by mutableStateOf(false)
    private set

  fun upsert(
    entry: FloatingPreviewEntry,
    autoActivateIfMissing: Boolean = true,
  ) {
    val existing = entries[entry.id]
    entries[entry.id] = entry
    if (autoActivateIfMissing && (activeId == null || activeId !in entries || existing == null)) {
      if (activeId == null || activeId !in entries) {
        activeId = entry.id
      }
    }
  }

  fun remove(id: String) {
    entries.remove(id)
    if (activeId == id) {
      activeId = entries.values.minByOrNull { it.order }?.id
    }
  }

  fun select(
    id: String,
    unminimize: Boolean = true,
  ) {
    if (id in entries) {
      activeId = id
      isFloating = true
      if (unminimize) isMinimized = false
    } else {
      activeId = id
      isFloating = true
      if (unminimize) isMinimized = false
    }
  }

  fun activateOnControlTouch(id: String) {
    if (id in entries && isFloating && activeId != id) {
      activeId = id
      isMinimized = false
    }
  }

  fun dockInline() {
    isFloating = false
  }

  fun floatPreview(id: String) {
    activeId = id
    isFloating = true
    isMinimized = false
  }

  fun toggleMinimized() {
    isMinimized = !isMinimized
  }

  fun clearAll() {
    entries.clear()
    activeId = null
  }

  fun isShownFloating(id: String): Boolean = isFloating && activeId == id && id in entries

  fun hasAnyFloatingPreview(): Boolean = isFloating && activeEntry() != null

  fun activeEntry(): FloatingPreviewEntry? {
    val current = activeId?.let { entries[it] }
    return current ?: entries.values.minByOrNull { it.order }
  }

  fun sortedEntries(): List<FloatingPreviewEntry> = entries.values.sortedBy { it.order }

  fun updatePositionForContainer(
    containerWidthPx: Float,
    containerHeightPx: Float,
    cardWidthPx: Float,
    cardHeightPx: Float,
    marginPx: Float,
  ) {
    if (containerWidthPx <= 0f || containerHeightPx <= 0f || cardWidthPx <= 0f || cardHeightPx <= 0f) return
    val maxX = (containerWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx)
    val maxY = (containerHeightPx - cardHeightPx - marginPx).coerceAtLeast(marginPx)
    if (!hasCustomPosition) {
      offsetX = ((containerWidthPx - cardWidthPx) / 2f).coerceIn(marginPx, maxX)
      offsetY = maxY
    } else {
      offsetX = offsetX.coerceIn(marginPx, maxX)
      offsetY = offsetY.coerceIn(marginPx, maxY)
    }
  }

  fun dragBy(
    dx: Float,
    dy: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    cardWidthPx: Float,
    cardHeightPx: Float,
    marginPx: Float,
  ) {
    hasCustomPosition = true
    val maxX = (containerWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx)
    val maxY = (containerHeightPx - cardHeightPx - marginPx).coerceAtLeast(marginPx)
    offsetX = (offsetX + dx).coerceIn(marginPx, maxX)
    offsetY = (offsetY + dy).coerceIn(marginPx, maxY)
  }
}

val LocalFloatingPreviewController = staticCompositionLocalOf<FloatingPreviewController?> { null }

@Composable
fun SettingsPreviewSlot(
  id: String,
  title: String,
  shortLabel: String,
  order: Int,
  active: Boolean = true,
  content: @Composable () -> Unit,
) {
  val palette = LocalIdePalette.current
  val controller = LocalFloatingPreviewController.current
  val latestContent by rememberUpdatedState(content)
  val wrappedContent =
    remember(id) {
      @Composable { latestContent() }
    }

  if (controller != null && active) {
    SideEffect {
      controller.upsert(
        FloatingPreviewEntry(
          id = id,
          title = title,
          shortLabel = shortLabel,
          order = order,
          content = wrappedContent,
        ),
      )
    }
    DisposableEffect(controller, id) {
      onDispose { controller.remove(id) }
    }
  }

  if (controller != null && active && controller.isShownFloating(id)) {
    Surface(
      color = palette.chromeSurface,
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.38f)),
      modifier = Modifier.fillMaxWidth().testTag("floating_preview_placeholder_$id"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.PictureInPictureAlt,
          contentDescription = null,
          tint = palette.activePillFg,
          modifier = Modifier.size(15.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Preview floating · Drag anywhere",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "Stays visible while you scroll & tweak controls",
            fontSize = 10.sp,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Surface(
          onClick = { controller.dockInline() },
          shape = RoundedCornerShape(7.dp),
          color = palette.activePillBg,
          border = BorderStroke(0.5.dp, palette.activePillFg.copy(alpha = 0.35f)),
          modifier = Modifier.testTag("dock_inline_preview_$id"),
        ) {
          Text(
            text = "Dock",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = palette.activePillFg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }
    }
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      if (controller != null && active) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "INLINE PREVIEW",
            fontFamily = JetBrainsMono,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = palette.textMuted,
          )
          Surface(
            onClick = { controller.floatPreview(id) },
            shape = RoundedCornerShape(7.dp),
            color = palette.activePillBg,
            border = BorderStroke(0.5.dp, palette.activePillFg.copy(alpha = 0.35f)),
            modifier = Modifier.testTag("float_preview_btn_$id"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.PictureInPictureAlt,
                contentDescription = "Float preview",
                tint = palette.activePillFg,
                modifier = Modifier.size(12.dp),
              )
              Text(
                text = "Float",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.activePillFg,
              )
            }
          }
        }
      }
      latestContent()
    }
  }
}

fun Modifier.activatePreviewOnTouch(id: String): Modifier =
  composed {
    val controller = LocalFloatingPreviewController.current
    if (controller == null) {
      this
    } else {
      pointerInput(controller, id) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.changedToDownIgnoreConsumed() }) {
              controller.activateOnControlTouch(id)
            }
          }
        }
      }
    }
  }

@Composable
fun BoxScope.FloatingSettingsPreviewOverlay(
  controller: FloatingPreviewController,
  containerSizePx: IntSize,
) {
  val activeEntry = controller.activeEntry()
  if (!controller.isFloating || activeEntry == null || containerSizePx.width <= 0 || containerSizePx.height <= 0) {
    return
  }

  val palette = LocalIdePalette.current
  val density = LocalDensity.current
  val marginPx = with(density) { 10.dp.toPx() }
  var cardSizePx by remember { mutableStateOf(IntSize.Zero) }
  val allEntries = controller.sortedEntries()

  SideEffect {
    if (cardSizePx.width > 0 && cardSizePx.height > 0) {
      controller.updatePositionForContainer(
        containerWidthPx = containerSizePx.width.toFloat(),
        containerHeightPx = containerSizePx.height.toFloat(),
        cardWidthPx = cardSizePx.width.toFloat(),
        cardHeightPx = cardSizePx.height.toFloat(),
        marginPx = marginPx,
      )
    }
  }

  Box(
    modifier =
      Modifier
        .align(Alignment.TopStart)
        .zIndex(20f)
        .offset {
          IntOffset(
            x = controller.offsetX.roundToInt(),
            y = controller.offsetY.roundToInt(),
          )
        }
        .widthIn(max = 560.dp)
        .then(if (controller.isMinimized) Modifier else Modifier.fillMaxWidth())
        .padding(horizontal = 10.dp)
        .navigationBarsPadding()
        .onSizeChanged { newSize ->
          cardSizePx = newSize
          controller.updatePositionForContainer(
            containerWidthPx = containerSizePx.width.toFloat(),
            containerHeightPx = containerSizePx.height.toFloat(),
            cardWidthPx = newSize.width.toFloat(),
            cardHeightPx = newSize.height.toFloat(),
            marginPx = marginPx,
          )
        },
  ) {
    Surface(
      color = palette.chromeSurface.copy(alpha = 0.96f),
      shape = RoundedCornerShape(16.dp),
      shadowElevation = 12.dp,
      border = BorderStroke(1.dp, palette.activePillFg.copy(alpha = 0.48f)),
      modifier =
        Modifier
          .then(if (controller.isMinimized) Modifier else Modifier.fillMaxWidth())
          .animateContentSize(animationSpec = tween(CaMotion.FAST))
          .testTag("floating_settings_preview_window"),
    ) {
      Column(modifier = Modifier.then(if (controller.isMinimized) Modifier else Modifier.fillMaxWidth())) {
        // Movable Drag Header
        Row(
          modifier =
            Modifier
              .then(if (controller.isMinimized) Modifier else Modifier.fillMaxWidth())
              .background(palette.chromeBg.copy(alpha = 0.85f))
              .pointerInput(controller, containerSizePx, cardSizePx) {
                detectDragGestures { change, dragAmount ->
                  change.consume()
                  controller.dragBy(
                    dx = dragAmount.x,
                    dy = dragAmount.y,
                    containerWidthPx = containerSizePx.width.toFloat(),
                    containerHeightPx = containerSizePx.height.toFloat(),
                    cardWidthPx = cardSizePx.width.toFloat().coerceAtLeast(1f),
                    cardHeightPx = cardSizePx.height.toFloat().coerceAtLeast(1f),
                    marginPx = marginPx,
                  )
                }
              }
              .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
              .testTag("floating_settings_preview_drag_handle"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.DragIndicator,
            contentDescription = "Drag floating preview",
            tint = palette.activePillFg,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = activeEntry.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (controller.isMinimized) Modifier.padding(end = 6.dp) else Modifier.weight(1f),
          )
          if (!controller.isMinimized && allEntries.size > 1) {
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              allEntries.forEach { entry ->
                val selected = entry.id == activeEntry.id
                Surface(
                  onClick = { controller.select(entry.id) },
                  shape = RoundedCornerShape(999.dp),
                  color = if (selected) palette.activePillBg else palette.chromeSurface,
                  border =
                    BorderStroke(
                      0.6.dp,
                      if (selected) palette.activePillFg.copy(alpha = 0.5f) else palette.border.copy(alpha = 0.55f),
                    ),
                ) {
                  Text(
                    text = entry.shortLabel,
                    fontFamily = JetBrainsMono,
                    fontSize = 9.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) palette.activePillFg else palette.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                  )
                }
              }
            }
          }
          IconButton(
            onClick = { controller.toggleMinimized() },
            modifier = Modifier.size(28.dp).testTag("floating_settings_preview_minimize_btn"),
          ) {
            Icon(
              imageVector = if (controller.isMinimized) Icons.Outlined.OpenInFull else Icons.Outlined.CloseFullscreen,
              contentDescription = if (controller.isMinimized) "Expand preview" else "Minimize preview",
              tint = palette.textSecondary,
              modifier = Modifier.size(15.dp),
            )
          }
          IconButton(
            onClick = { controller.dockInline() },
            modifier = Modifier.size(28.dp).testTag("floating_settings_preview_dock_btn"),
          ) {
            Icon(
              imageVector = Icons.Outlined.VerticalAlignBottom,
              contentDescription = "Dock preview inline",
              tint = palette.textSecondary,
              modifier = Modifier.size(15.dp),
            )
          }
        }

        AnimatedVisibility(visible = !controller.isMinimized) {
          Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = palette.border.copy(alpha = 0.45f), thickness = 0.75.dp)
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(8.dp),
            ) {
              activeEntry.content()
            }
          }
        }
      }
    }
  }
}
