package com.example.editor.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.IconLabelBehavior
import com.example.editor.CodeSymbol
import com.example.editor.drag.DragTransferMode
import com.example.editor.engine.ClipboardHistoryStore
import com.example.editor.engine.EdgeEditorSession
import com.example.editor.engine.LineChange
import com.example.editor.engine.LineRenderCache
import com.example.editor.selection.SelectionScope
import com.example.idecore.completion.EdgeCompletionItem
import com.example.idecore.completion.EdgeCompletionKind
import com.example.ui.components.IconRevealButton
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIconLabelBehavior
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- 1. Selection Loupe (1.5x GraphicsLayer Glass Magnifier) ----

@Composable
fun Modifier.edgeEditorLoupe(
  loupeSourceProvider: () -> Offset?,
  lineHeight: Float,
  background: Color,
  outline: Color,
  outlineProvider: () -> Color = { outline },
  extraLiftDpProvider: () -> Float = { 0f },
): Modifier {
  val layer = rememberGraphicsLayer()
  val appear = remember { Animatable(0f) }
  val lastSource = remember { arrayOf(Offset.Zero) }
  val active = loupeSourceProvider() != null

  LaunchedEffect(active) {
    appear.animateTo(if (active) 1f else 0f, tween(if (active) 140 else 110))
  }

  return drawWithContent {
    loupeSourceProvider()?.let { lastSource[0] = it }
    val shown = appear.value
    if (shown <= 0f) {
      drawContent()
      return@drawWithContent
    }
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)

    val source = lastSource[0]
    val w = 120.dp.toPx()
    val h = 46.dp.toPx()
    val edge = 4.dp.toPx()
    val extraLiftPx = extraLiftDpProvider().coerceAtLeast(0f).dp.toPx()
    val cx = source.x.coerceIn(w / 2f + edge, (size.width - w / 2f - edge).coerceAtLeast(w / 2f + edge))
    val rawCy = source.y - lineHeight / 2f - 12.dp.toPx() - extraLiftPx - h / 2f
    val minCy = h / 2f + 6.dp.toPx()
    val maxCy = (size.height - h / 2f - 6.dp.toPx()).coerceAtLeast(minCy)
    val cy = rawCy.coerceIn(minCy, maxCy)
    val rect = Rect(Offset(cx - w / 2f, cy - h / 2f), Size(w, h))
    val radius = CornerRadius(15.dp.toPx())
    val handleClearance = 2.dp.toPx()
    val grow = 0.75f + 0.25f * shown

    withTransform({ scale(grow, grow, pivot = Offset(cx, rect.bottom)) }) {
      drawIntoCanvas { it.saveLayer(rect.inflate(8.dp.toPx()), Paint().apply { alpha = shown }) }
      for (i in 1..2) {
        drawRoundRect(
          Color.Black.copy(alpha = 0.16f),
          rect.topLeft + Offset(0f, i * 1.5.dp.toPx()),
          rect.size,
          radius,
        )
      }
      clipPath(Path().apply { addRoundRect(RoundRect(rect, radius)) }) {
        drawRect(background, rect.topLeft, rect.size)
        withTransform({
          translate(rect.center.x, rect.center.y)
          scale(1.5f, 1.5f, pivot = Offset.Zero)
          translate(-source.x, -source.y)
          clipRect(top = source.y - lineHeight * 2f, bottom = source.y + lineHeight / 2f - handleClearance)
        }) {
          drawLayer(layer)
        }
      }
      drawRoundRect(outlineProvider(), rect.topLeft, rect.size, radius, style = Stroke(1.25.dp.toPx()))
      drawIntoCanvas { it.restore() }
    }
  }
}

// ---- 2. Three-Finger Sideways Swipe Undo / Redo ----

class EdgeUndoFeedback {
  enum class Kind {
    UNDO,
    REDO,
    NOTHING_TO_UNDO,
    NOTHING_TO_REDO,
  }

  var kind by mutableStateOf<Kind?>(null)
    private set
  var serial by mutableIntStateOf(0)
    private set

  fun show(next: Kind) {
    kind = next
    serial++
  }

  fun hide() {
    kind = null
  }
}

fun Modifier.edgeUndoSwipe(
  session: EdgeEditorSession,
  feedback: EdgeUndoFeedback,
): Modifier =
  pointerInput(session) {
    awaitEachGesture {
      var claimed = false
      var travel = 0f
      val step = 72.dp.toPx()
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val touching = event.changes.filter { it.pressed && it.type == PointerType.Touch }
        if (touching.isEmpty()) break
        if (touching.size < 3) {
          if (claimed) event.changes.forEach { it.consume() }
          continue
        }
        val dx = touching.sumOf { (it.position.x - it.previousPosition.x).toDouble() }.toFloat() / touching.size
        val dy = touching.sumOf { (it.position.y - it.previousPosition.y).toDouble() }.toFloat() / touching.size
        if (!claimed && abs(dx) > abs(dy) && abs(travel + dx) > viewConfiguration.touchSlop) claimed = true
        travel += dx
        if (claimed) {
          event.changes.forEach { it.consume() }
          while (travel <= -step) {
            travel += step
            feedback.show(if (session.undo()) EdgeUndoFeedback.Kind.UNDO else EdgeUndoFeedback.Kind.NOTHING_TO_UNDO)
          }
          while (travel >= step) {
            travel -= step
            feedback.show(if (session.redo()) EdgeUndoFeedback.Kind.REDO else EdgeUndoFeedback.Kind.NOTHING_TO_REDO)
          }
        }
      }
    }
  }

@Composable
fun BoxScope.EdgeUndoGestureToast(feedback: EdgeUndoFeedback) {
  val palette = LocalIdePalette.current
  var shown by remember { mutableStateOf<EdgeUndoFeedback.Kind?>(null) }
  LaunchedEffect(feedback.serial) {
    val k = feedback.kind ?: return@LaunchedEffect
    shown = k
    delay(750L)
    shown = null
    feedback.hide()
  }
  AnimatedVisibility(
    visible = shown != null,
    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
    enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.88f),
    exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.92f),
  ) {
    val kind = shown ?: EdgeUndoFeedback.Kind.UNDO
    val (icon, label, dim) =
      when (kind) {
        EdgeUndoFeedback.Kind.UNDO -> Triple(Icons.AutoMirrored.Outlined.Undo, "Undo", false)
        EdgeUndoFeedback.Kind.REDO -> Triple(Icons.AutoMirrored.Outlined.Redo, "Redo", false)
        EdgeUndoFeedback.Kind.NOTHING_TO_UNDO -> Triple(Icons.AutoMirrored.Outlined.Undo, "No Undo", true)
        EdgeUndoFeedback.Kind.NOTHING_TO_REDO -> Triple(Icons.AutoMirrored.Outlined.Redo, "No Redo", true)
      }
    Surface(
      color = palette.glassElevated.copy(alpha = 0.95f),
      shape = RoundedCornerShape(999.dp),
      border = BorderStroke(0.75.dp, palette.glassBorderHighlight),
      shadowElevation = 4.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val ink = if (dim) palette.textMuted else palette.activePillFg
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
        Text(label, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink)
      }
    }
  }
}

// ---- 3. Sticky Scroll Header (Pinned Enclosing Scope) ----

@Composable
fun BoxScope.EdgeStickyScrollHeader(
  visible: Boolean,
  symbol: CodeSymbol?,
  lineText: String,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  AnimatedVisibility(
    visible = visible && symbol != null,
    enter = fadeIn(tween(110)),
    exit = fadeOut(tween(90)),
    modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
  ) {
    val sym = symbol ?: return@AnimatedVisibility
    Surface(
      color = palette.editorBg,
      shadowElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .background(palette.editorBg)
              .clickable(onClick = onClick)
              .padding(horizontal = 8.dp, vertical = 2.dp)
              .testTag("editor_sticky_header"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          Text(
            text = "${sym.line}",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = palette.textMuted,
            modifier = Modifier.widthIn(min = 22.dp),
          )
          Box(
            modifier =
              Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(palette.activePillBg)
                .padding(horizontal = 5.dp, vertical = 1.dp),
          ) {
            Text(
              text = sym.kind,
              fontFamily = JetBrainsMono,
              fontSize = 8.5.sp,
              fontWeight = FontWeight.Bold,
              color = palette.activePillFg,
            )
          }
          Text(
            text = lineText.trim().ifEmpty { sym.name },
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }
        HorizontalDivider(color = palette.border.copy(alpha = 0.65f), thickness = 0.5.dp)
      }
    }
  }
}

// ---- 4. Fast-Scroll Outline Scrubber with Symbol Ticks ----

@Composable
fun BoxScope.EdgeFastScrollLayer(
  geometry: EdgeEditorGeometry,
  session: EdgeEditorSession,
  symbols: List<CodeSymbol>,
  onScrubToFraction: (Float) -> Unit,
) {
  val palette = LocalIdePalette.current
  val maxV = geometry.maxVerticalScroll(session.foldModel)
  if (maxV <= geometry.lineHeightPx * 6f) return

  var dragging by remember { mutableStateOf(false) }
  var dragFraction by remember { mutableFloatStateOf(0f) }
  val scrollFraction = if (maxV > 0f) (geometry.vOffset / maxV).coerceIn(0f, 1f) else 0f
  val activeFraction = if (dragging) dragFraction else scrollFraction
  val lineCount = session.doc.lineCount.coerceAtLeast(1)
  val currentLine1 = ((activeFraction * (lineCount - 1)).roundToInt() + 1).coerceIn(1, lineCount)
  val nearestSymbol =
    remember(symbols, currentLine1, dragging) {
      if (!dragging || symbols.isEmpty()) null
      else symbols.lastOrNull { it.line <= currentLine1 } ?: symbols.firstOrNull()
    }

  Box(
    modifier =
      Modifier
        .align(Alignment.CenterEnd)
        .fillMaxHeight()
        .padding(vertical = 8.dp)
        .width(24.dp)
        .pointerInput(maxV) {
          detectVerticalDragGestures(
            onDragStart = { pos ->
              dragging = true
              val frac = (pos.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
              dragFraction = frac
              onScrubToFraction(frac)
            },
            onDragEnd = { dragging = false },
            onDragCancel = { dragging = false },
            onVerticalDrag = { change, _ ->
              change.consume()
              val frac = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
              dragFraction = frac
              onScrubToFraction(frac)
            },
          )
        }
        .testTag("editor_fast_scrubber"),
  ) {
    Canvas(modifier = Modifier.fillMaxHeight().width(24.dp)) {
      val trackX = size.width - 7.dp.toPx()
      val trackW = 4.dp.toPx()
      // Symbol tick marks along track
      for (sym in symbols) {
        val f = ((sym.line - 1).toFloat() / lineCount.toFloat()).coerceIn(0f, 1f)
        val ty = f * size.height
        drawRoundRect(
          color = palette.syntaxFunction.copy(alpha = 0.55f),
          topLeft = Offset(trackX - 3.dp.toPx(), ty),
          size = Size(trackW + 3.dp.toPx(), 2.dp.toPx()),
          cornerRadius = CornerRadius(1.dp.toPx()),
        )
      }
      // Thumb
      val thumbH = (size.height * 0.14f).coerceIn(28.dp.toPx(), 72.dp.toPx())
      val thumbY = activeFraction * (size.height - thumbH)
      drawRoundRect(
        color = if (dragging) palette.ctaBg else palette.textMuted.copy(alpha = 0.42f),
        topLeft = Offset(trackX, thumbY),
        size = Size(if (dragging) 5.dp.toPx() else trackW, thumbH),
        cornerRadius = CornerRadius(3.dp.toPx()),
      )
    }
  }

  AnimatedVisibility(
    visible = dragging,
    enter = fadeIn(tween(80)),
    exit = fadeOut(tween(120)),
    modifier = Modifier.align(Alignment.TopEnd).padding(end = 30.dp, top = 16.dp),
  ) {
    Surface(
      color = palette.glassElevated.copy(alpha = 0.96f),
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.4f)),
      shadowElevation = 4.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = "Ln $currentLine1",
          fontFamily = JetBrainsMono,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = palette.activePillFg,
        )
        nearestSymbol?.let { sym ->
          Text(
            text = sym.name,
            fontFamily = JetBrainsMono,
            fontSize = 10.5.sp,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
          )
        }
      }
    }
  }
}

@Composable
fun BoxScope.EdgeHorizontalScrollbarLayer(
  geometry: EdgeEditorGeometry,
  session: EdgeEditorSession,
  cache: LineRenderCache,
) {
  if (geometry.wordWrap) return
  val maxH = geometry.maxHorizontalScroll(session, cache)
  if (maxH <= 0.5f) return

  val palette = LocalIdePalette.current
  val density = LocalDensity.current
  val gutterDp = with(density) { geometry.effectiveGutterPx.toDp() }
  val minThumbPx = with(density) { 36.dp.toPx() }
  val trackWidthPx = remember { mutableFloatStateOf(0f) }

  var dragging by remember { mutableStateOf(false) }
  val alpha by animateFloatAsState(
    targetValue = if (dragging) 0.85f else 0.42f,
    animationSpec = tween(140),
    label = "editorHScrollbar",
  )

  fun computeThumbWidth(trackW: Float, curMaxH: Float): Float {
    val vpW = (geometry.viewport.width - geometry.textOriginX).coerceAtLeast(1f)
    val contentW = vpW + curMaxH
    if (contentW <= 0f || trackW <= 0f) return minThumbPx
    return (trackW * (vpW / contentW)).coerceIn(minThumbPx, trackW)
  }

  val dragState =
    rememberDraggableState { delta ->
      val track = trackWidthPx.floatValue
      val curMaxH = geometry.maxHorizontalScroll(session, cache)
      val thumb = computeThumbWidth(track, curMaxH)
      val availTrack = (track - thumb).coerceAtLeast(1f)
      val contentDelta = (delta / availTrack) * curMaxH
      if (contentDelta != 0f) {
        geometry.scrollBy(contentDelta, 0f, session, cache)
      }
    }

  Box(
    modifier =
      Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .padding(start = gutterDp, end = 10.dp, bottom = 3.dp)
        .height(14.dp)
        .onSizeChanged { trackWidthPx.floatValue = it.width.toFloat() }
        .draggable(
          state = dragState,
          orientation = Orientation.Horizontal,
          onDragStarted = { dragging = true },
          onDragStopped = { dragging = false },
        )
        .drawBehind {
          val curMaxH = geometry.maxHorizontalScroll(session, cache)
          if (curMaxH <= 0.5f) return@drawBehind
          val thumbW = computeThumbWidth(size.width, curMaxH)
          val frac = (geometry.hOffset / curMaxH).coerceIn(0f, 1f)
          val x = frac * (size.width - thumbW).coerceAtLeast(0f)
          val h = if (dragging) 5.dp.toPx() else 3.5.dp.toPx()
          val y = (size.height - h) / 2f
          val radius = CornerRadius(h / 2f, h / 2f)
          drawRoundRect(
            color = palette.border.copy(alpha = 0.25f * alpha),
            topLeft = Offset(0f, y),
            size = Size(size.width, h),
            cornerRadius = radius,
          )
          drawRoundRect(
            color = (if (dragging) palette.ctaBg else palette.textMuted).copy(alpha = alpha),
            topLeft = Offset(x, y),
            size = Size(thumbW, h),
            cornerRadius = radius,
          )
        }
        .testTag("editor_horizontal_scrollbar"),
  )
}

// ---- 5. Gutter Diff Hunk Preview + One-Tap Revert ----

@Composable
fun BoxScope.EdgeDiffHunkPreviewCard(
  change: LineChange?,
  baseText: String,
  readOnly: Boolean,
  onRevertHunk: (LineChange) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  AnimatedVisibility(
    visible = change != null,
    enter = fadeIn(tween(110)) + scaleIn(tween(110), initialScale = 0.95f),
    exit = fadeOut(tween(90)),
    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 12.dp),
  ) {
    val active = change ?: return@AnimatedVisibility
    val oldLines =
      remember(active, baseText) {
        val split = baseText.replace("\r\n", "\n").split('\n')
        val s = active.oldStart.coerceIn(0, split.size)
        val e = active.oldEnd.coerceIn(s, split.size)
        split.subList(s, e)
      }
    val badgeColor =
      when (active.kind) {
        LineChange.Kind.ADDED -> EmeraldLive
        LineChange.Kind.MODIFIED -> palette.syntaxFunction
        LineChange.Kind.DELETED -> RoseError
      }
    val badgeLabel =
      when (active.kind) {
        LineChange.Kind.ADDED -> "ADDED"
        LineChange.Kind.MODIFIED -> "MODIFIED"
        LineChange.Kind.DELETED -> "DELETED"
      }

    Surface(
      color = palette.glassElevated.copy(alpha = 0.97f),
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(0.75.dp, badgeColor.copy(alpha = 0.45f)),
      shadowElevation = 6.dp,
      modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).testTag("editor_diff_hunk_card"),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier =
              Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
          ) {
            Text(badgeLabel, fontFamily = JetBrainsMono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = badgeColor)
          }
          Text(
            text = "Ln ${active.newStart + 1}–${maxOf(active.newStart + 1, active.newEnd)}",
            fontFamily = JetBrainsMono,
            fontSize = 10.5.sp,
            color = palette.textSecondary,
            modifier = Modifier.weight(1f),
          )
          if (!readOnly) {
            Surface(
              onClick = { onRevertHunk(active) },
              shape = RoundedCornerShape(7.dp),
              color = RoseError.copy(alpha = 0.16f),
              modifier = Modifier.testTag("editor_revert_hunk_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Outlined.Restore, contentDescription = "Revert", tint = RoseError, modifier = Modifier.size(13.dp))
                Text("Revert", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = RoseError)
              }
            }
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = palette.textMuted, modifier = Modifier.size(15.dp))
          }
        }
        if (oldLines.isNotEmpty()) {
          HorizontalDivider(color = palette.border.copy(alpha = 0.5f), thickness = 0.75.dp)
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(max = 128.dp)
                .background(RoseError.copy(alpha = 0.08f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 7.dp),
          ) {
            Text(
              text = oldLines.joinToString("\n") { "- $it" },
              fontFamily = JetBrainsMono,
              fontSize = 11.sp,
              color = palette.textPrimary,
            )
          }
        }
      }
    }
  }
}

// ---- 6. Frosted Floating Selection Action Toolbar + Live Hold Scope Pill ----

@Composable
fun BoxScope.EdgeSmartHoldScopePill(
  scope: SelectionScope?,
  anchorY: Float,
  dragHintVisible: Boolean,
  showScopeBadge: Boolean = false,
) {
  if (!showScopeBadge) return
  val palette = LocalIdePalette.current
  val density = LocalDensity.current
  val topOffsetPx =
    with(density) {
      val desired = anchorY - 44.dp.toPx()
      if (desired < 8.dp.toPx()) (anchorY + 34.dp.toPx()).roundToInt() else desired.roundToInt()
    }

  AnimatedVisibility(
    visible = scope != null,
    enter = fadeIn(tween(90)) + scaleIn(tween(110), initialScale = 0.92f),
    exit = fadeOut(tween(80)),
    modifier =
      Modifier
        .align(Alignment.TopCenter)
        .offset { IntOffset(0, topOffsetPx.coerceAtLeast(8)) },
  ) {
    val active = scope ?: return@AnimatedVisibility
    Surface(
      color = palette.glassElevated.copy(alpha = 0.96f),
      shape = RoundedCornerShape(999.dp),
      border = BorderStroke(1.dp, palette.ctaBg.copy(alpha = 0.45f)),
      shadowElevation = 4.dp,
      modifier = Modifier.testTag("editor_hold_scope_pill"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(
          text = active.label,
          fontFamily = JetBrainsMono,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          color = palette.activePillFg,
        )
        Text(
          text = "${active.length}ch",
          fontFamily = JetBrainsMono,
          fontSize = 9.5.sp,
          color = palette.textMuted,
        )
      }
    }
  }
}

@Composable
fun BoxScope.EdgeSelectionToolbar(
  visible: Boolean,
  anchorY: Float,
  anchorBottomY: Float = anchorY,
  viewportHeightPx: Float = 0f,
  readOnly: Boolean,
  hasClipboardHistory: Boolean,
  scopeLabel: String? = null,
  canExpandScope: Boolean = false,
  canShrinkScope: Boolean = false,
  dragMode: DragTransferMode = DragTransferMode.MOVE,
  showScopeBadge: Boolean = false,
  showExpandShrink: Boolean = false,
  showDragGrip: Boolean = false,
  showModeToggle: Boolean = false,
  showQuickActions: Boolean = true,
  showCodeActions: Boolean = false,
  onExpandScope: () -> Unit = {},
  onShrinkScope: () -> Unit = {},
  onToggleDragMode: () -> Unit = {},
  onStartToolbarDrag: () -> Unit = {},
  onCopy: () -> Unit,
  onCut: () -> Unit,
  onPaste: () -> Unit,
  onOpenPasteHistory: () -> Unit,
  onSelectAll: () -> Unit,
  onComment: () -> Unit,
  onRename: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val labelBehavior = LocalIconLabelBehavior.current
  val density = LocalDensity.current
  val topOffsetPx =
    with(density) {
      val desired = anchorY - 46.dp.toPx()
      if (desired >= 8.dp.toPx()) {
        desired.roundToInt()
      } else {
        val belowSel = maxOf(anchorY + 30.dp.toPx(), anchorBottomY + 22.dp.toPx())
        val maxY =
          if (viewportHeightPx > 0f) (viewportHeightPx - 48.dp.toPx()).coerceAtLeast(8.dp.toPx())
          else belowSel
        belowSel.coerceAtMost(maxY).roundToInt()
      }
    }

  val hasSmartTools =
    showDragGrip ||
      (showScopeBadge && !scopeLabel.isNullOrBlank()) ||
      (showExpandShrink && (canExpandScope || canShrinkScope)) ||
      (showModeToggle && !readOnly)
  val hasCodeTools = showCodeActions && !readOnly
  val hasAnyControl = hasSmartTools || showQuickActions || hasCodeTools

  AnimatedVisibility(
    visible = visible && hasAnyControl,
    enter = fadeIn(tween(100)) + scaleIn(tween(100), initialScale = 0.94f),
    exit = fadeOut(tween(80)),
    modifier =
      Modifier
        .align(Alignment.TopCenter)
        .padding(horizontal = 12.dp)
        .offset { IntOffset(0, topOffsetPx.coerceAtLeast(8)) },
  ) {
    Surface(
      color = palette.glassElevated.copy(alpha = 0.96f),
      shape = RoundedCornerShape(999.dp),
      border = BorderStroke(1.dp, palette.ctaBg.copy(alpha = 0.42f)),
      shadowElevation = 6.dp,
      modifier = Modifier.testTag("editor_selection_toolbar"),
    ) {
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        if (showDragGrip) {
          Box(
            modifier =
              Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.activePillBg.copy(alpha = 0.65f))
                .pointerInput(onStartToolbarDrag) {
                  awaitEachGesture {
                    val down = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.pressed } ?: return@awaitEachGesture
                    down.consume()
                    onStartToolbarDrag()
                  }
                }
                .padding(horizontal = 6.dp)
                .testTag("sel_drag_grip"),
            contentAlignment = Alignment.Center,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.DragIndicator,
                contentDescription = "Drag selected text",
                tint = palette.activePillFg,
                modifier = Modifier.size(14.dp),
              )
              if (labelBehavior == IconLabelBehavior.ALWAYS_SHOW) {
                Text(
                  text = "Drag",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }
        }

        if (showScopeBadge && !scopeLabel.isNullOrBlank()) {
          Text(
            text = scopeLabel,
            fontFamily = JetBrainsMono,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.activePillFg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 5.dp).testTag("sel_scope_badge"),
          )
        }

        if (showExpandShrink && (canExpandScope || canShrinkScope)) {
          IconButton(
            onClick = onShrinkScope,
            enabled = canShrinkScope,
            modifier = Modifier.size(26.dp).testTag("sel_shrink_scope"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Remove,
              contentDescription = "Shrink selection scope",
              tint = if (canShrinkScope) palette.textPrimary else palette.textMuted.copy(alpha = 0.35f),
              modifier = Modifier.size(14.dp),
            )
          }
          IconButton(
            onClick = onExpandScope,
            enabled = canExpandScope,
            modifier = Modifier.size(26.dp).testTag("sel_expand_scope"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Add,
              contentDescription = "Expand selection scope",
              tint = if (canExpandScope) palette.activePillFg else palette.textMuted.copy(alpha = 0.35f),
              modifier = Modifier.size(14.dp),
            )
          }
        }

        if (showModeToggle && !readOnly) {
          val isMove = dragMode == DragTransferMode.MOVE
          val modeTint = if (isMove) palette.ctaBg else EmeraldLive
          IconRevealButton(
            icon = if (isMove) Icons.Outlined.OpenWith else Icons.Outlined.ContentCopy,
            revealedLabel = if (isMove) "Move" else "Copy",
            minimal = true,
            active = true,
            contentTint = modeTint,
            height = 26.dp,
            iconSize = 14.dp,
            onClick = onToggleDragMode,
            tag = "sel_drag_mode_toggle",
          )
        }

        if (hasSmartTools && showQuickActions) {
          Box(
            modifier =
              Modifier
                .padding(horizontal = 2.dp)
                .width(1.dp)
                .height(14.dp)
                .background(palette.border.copy(alpha = 0.65f)),
          )
        }

        if (showQuickActions) {
          IconRevealButton(
            icon = Icons.Outlined.ContentCopy,
            revealedLabel = "Copy",
            minimal = true,
            contentTint = palette.textPrimary,
            height = 28.dp,
            iconSize = 15.dp,
            onClick = onCopy,
            tag = "sel_copy",
          )
          if (!readOnly) {
            IconRevealButton(
              icon = Icons.Outlined.ContentCut,
              revealedLabel = "Cut",
              minimal = true,
              contentTint = palette.textPrimary,
              height = 28.dp,
              iconSize = 15.dp,
              onClick = onCut,
              tag = "sel_cut",
            )
            IconRevealButton(
              icon = Icons.Outlined.ContentPaste,
              revealedLabel = "Paste",
              minimal = true,
              contentTint = palette.textPrimary,
              height = 28.dp,
              iconSize = 15.dp,
              onClick = onPaste,
              tag = "sel_paste",
            )
            if (hasClipboardHistory) {
              IconRevealButton(
                icon = Icons.Outlined.History,
                revealedLabel = "History",
                minimal = true,
                contentTint = palette.activePillFg,
                height = 28.dp,
                iconSize = 15.dp,
                onClick = onOpenPasteHistory,
                tag = "sel_paste_history",
              )
            }
          }
          IconRevealButton(
            icon = Icons.Outlined.SelectAll,
            revealedLabel = "All",
            minimal = true,
            contentTint = palette.textPrimary,
            height = 28.dp,
            iconSize = 15.dp,
            onClick = onSelectAll,
            tag = "sel_all",
          )
        }

        if (hasCodeTools) {
          if (hasSmartTools || showQuickActions) {
            Box(
              modifier =
                Modifier
                  .padding(horizontal = 2.dp)
                  .width(1.dp)
                  .height(14.dp)
                  .background(palette.border.copy(alpha = 0.65f)),
            )
          }
          ToolbarSlashButton(onClick = onComment, tag = "sel_comment")
          IconRevealButton(
            icon = Icons.Outlined.DriveFileRenameOutline,
            revealedLabel = "Rename",
            minimal = true,
            contentTint = palette.textPrimary,
            height = 28.dp,
            iconSize = 15.dp,
            onClick = onRename,
            tag = "sel_rename",
          )
        }
      }
    }
  }
}

@Composable
private fun ToolbarSlashButton(onClick: () -> Unit, tag: String) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(7.dp),
    color = Color.Transparent,
    modifier = Modifier.height(26.dp).testTag(tag),
  ) {
    Box(
      modifier = Modifier.padding(horizontal = 7.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "//",
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textSecondary,
      )
    }
  }
}

// ---- 7. Paste History Picker Popup ----

@Composable
fun BoxScope.EdgePasteHistoryPopup(
  visible: Boolean,
  onSelectClip: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val entries = ClipboardHistoryStore.entries
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(tween(110)) + scaleIn(tween(110), initialScale = 0.95f),
    exit = fadeOut(tween(90)),
    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Surface(
      color = palette.chromeSurface,
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.8f)),
      shadowElevation = 8.dp,
      modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).testTag("editor_paste_history_popup"),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth().height(34.dp).padding(start = 10.dp, end = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(14.dp))
          Text(
            text = "Clipboard",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = "${entries.size}",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = palette.textMuted,
          )
          IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = palette.textMuted, modifier = Modifier.size(14.dp))
          }
        }
        HorizontalDivider(color = palette.border.copy(alpha = 0.55f), thickness = 0.5.dp)
        if (entries.isEmpty()) {
          Box(modifier = Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
            Text("Empty", fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.textMuted)
          }
        } else {
          LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
            itemsIndexed(entries) { idx, clip ->
              val preview = clip.replace('\n', '↵').take(120)
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      onSelectClip(clip)
                      onDismiss()
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("paste_history_item_$idx"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "${idx + 1}",
                  fontFamily = JetBrainsMono,
                  fontSize = 9.5.sp,
                  color = palette.textMuted,
                  modifier = Modifier.width(16.dp),
                )
                Text(
                  text = preview,
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  color = palette.textPrimary,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
              }
              if (idx < entries.lastIndex) {
                HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.5.dp)
              }
            }
          }
        }
      }
    }
  }
}

// ---- 8. CodeAssist-Style Minimal Completion Strip with Bolded Prefix Match ----

@Composable
fun EdgeGlassCompletionBar(
  items: List<EdgeCompletionItem>,
  queryPrefix: String,
  onSelect: (EdgeCompletionItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val needle = queryPrefix.substringAfterLast('.')
  Surface(
    color = palette.chromeSurface,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.75f)),
    shadowElevation = 4.dp,
    modifier = modifier.testTag("editor_completion_bar"),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 5.dp, vertical = 3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      items.forEachIndexed { idx, item ->
        val badgeColor =
          when (item.kind) {
            EdgeCompletionKind.POSTFIX -> AmberWarn
            EdgeCompletionKind.TEMPLATE -> EmeraldLive
            EdgeCompletionKind.BINDING, EdgeCompletionKind.API -> palette.activePillFg
            EdgeCompletionKind.FUNCTION, EdgeCompletionKind.SYMBOL -> palette.syntaxFunction
            else -> palette.syntaxProperty
          }
        val highlightedLabel =
          remember(item.label, needle, palette) {
            buildAnnotatedString {
              val matchIdx =
                if (needle.isNotEmpty()) item.label.indexOf(needle, ignoreCase = true) else -1
              if (matchIdx >= 0) {
                if (matchIdx > 0) append(item.label.substring(0, matchIdx))
                withStyle(SpanStyle(color = palette.activePillFg, fontWeight = FontWeight.ExtraBold)) {
                  append(item.label.substring(matchIdx, matchIdx + needle.length))
                }
                append(item.label.substring(matchIdx + needle.length))
              } else {
                append(item.label)
              }
            }
          }
        Surface(
          onClick = { onSelect(item) },
          shape = RoundedCornerShape(6.dp),
          color = if (idx == 0) palette.activePillBg.copy(alpha = 0.6f) else palette.editorBg,
          border =
            if (idx == 0) BorderStroke(0.5.dp, palette.activePillFg.copy(alpha = 0.45f))
            else null,
          modifier = Modifier.testTag("completion_item_$idx"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = item.kind.badge,
              style = TextStyle(fontFamily = JetBrainsMono, fontSize = 8.sp, fontWeight = FontWeight.Bold),
              color = badgeColor,
              modifier =
                Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(badgeColor.copy(alpha = 0.16f))
                  .padding(horizontal = 4.dp, vertical = 1.dp),
              maxLines = 1,
            )
            Text(
              text = highlightedLabel,
              style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Medium),
              color = palette.textPrimary,
              maxLines = 1,
            )
            if (item.detail.isNotBlank()) {
              Text(
                text = item.detail,
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 84.dp),
              )
            }
          }
        }
      }
    }
  }
}
