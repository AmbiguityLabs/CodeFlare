package com.example.editor.drag

import android.content.ClipDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.security.SmartSelectionHoldMode
import com.example.editor.selection.SelectionLadder
import com.example.editor.selection.SelectionLadderState
import com.example.editor.selection.SmartSelectionInitialDepth
import com.example.editor.selection.SmartTextDomain
import com.example.editor.selection.SmartTextSelectionEngine
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.LocalSelectionDragConfig
import com.example.ui.theme.UiStyleVersion
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pure formatting helper for dropping dragged text snippets into single-line or multi-line input fields.
 */
object TextDropFormatter {
  private val FULL_URL_REGEX = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

  fun normalizeSingleLine(raw: String): String =
    raw
      .replace("\r\n", " ")
      .replace('\n', ' ')
      .replace('\r', ' ')
      .replace('\t', ' ')
      .replace(Regex(" {2,}"), " ")
      .trim()

  fun resolveTextDropValue(
    currentValue: String,
    droppedText: String,
    singleLine: Boolean,
    replaceIfFullUrl: Boolean = true,
    replaceAll: Boolean = false,
  ): String {
    if (droppedText.isEmpty()) return currentValue
    if (singleLine) {
      val clean = normalizeSingleLine(droppedText)
      if (clean.isEmpty()) return currentValue
      if (replaceAll || currentValue.isBlank()) return clean
      if (replaceIfFullUrl && FULL_URL_REGEX.matches(clean)) return clean
      if (currentValue.endsWith("/") && clean.startsWith("/")) {
        return currentValue + clean.removePrefix("/")
      }
      return currentValue + clean
    }
    if (replaceAll || currentValue.isEmpty()) return droppedText
    return if (currentValue.endsWith("\n")) {
      currentValue + droppedText
    } else {
      "$currentValue\n$droppedText"
    }
  }
}

/**
 * Read-only text surface (CLI outputs, console logs, Live Tail, stack traces, API responses, headers)
 * with tap-and-hold semantic selection via [SmartTextSelectionEngine] and instant one-motion drag-and-drop.
 *
 * Always preserves vertical/horizontal parent scrolling by yielding immediately if finger moves beyond
 * touchSlop before [holdDelayMs] elapses.
 */
@Composable
fun SmartDraggableText(
  text: String,
  surfaceId: String,
  modifier: Modifier = Modifier,
  annotatedText: AnnotatedString? = null,
  domain: SmartTextDomain = SmartTextDomain.TERMINAL_OR_LOG,
  initialDepth: SmartSelectionInitialDepth = LocalSelectionDragConfig.current.initialDepth,
  holdDelayMs: Long = LocalSelectionDragConfig.current.holdDelayMs.toLong(),
  holdToDragImmediately: Boolean = LocalSelectionDragConfig.current.immediateSlideToDrag,
  progressiveHold: Boolean = LocalSelectionDragConfig.current.holdMode == SmartSelectionHoldMode.PROGRESSIVE_HOLD,
  classicWordOnly: Boolean = LocalSelectionDragConfig.current.holdMode == SmartSelectionHoldMode.CLASSIC_WORD,
  allowGlobalDrag: Boolean = LocalSelectionDragConfig.current.allowCrossAppDrag,
  showScopeBadge: Boolean = LocalSelectionDragConfig.current.showScopeBadge,
  showExpandShrink: Boolean = LocalSelectionDragConfig.current.showExpandShrink,
  showDragGrip: Boolean = LocalSelectionDragConfig.current.showDragGrip,
  showQuickActions: Boolean = LocalSelectionDragConfig.current.showQuickActions,
  style: TextStyle = TextStyle.Default,
  color: Color = Color.Unspecified,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  onCopied: ((String) -> Unit)? = null,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val view = LocalView.current
  val haptic = LocalHapticFeedback.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()

  var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
  var ladderState by remember(text) { mutableStateOf<SelectionLadderState?>(null) }
  var popupAnchorPx by remember { mutableStateOf(IntOffset.Zero) }
  var showSelectionPopup by remember(text) { mutableStateOf(false) }

  val latestText by rememberUpdatedState(text)
  val latestDomain by rememberUpdatedState(domain)
  val latestInitialDepth by rememberUpdatedState(initialDepth)
  val latestHoldDelayMs by rememberUpdatedState(holdDelayMs)
  val latestHoldToDrag by rememberUpdatedState(holdToDragImmediately)
  val latestProgressive by rememberUpdatedState(progressiveHold)
  val latestClassicWord by rememberUpdatedState(classicWordOnly)
  val latestAllowGlobal by rememberUpdatedState(allowGlobalDrag)
  val latestPalette by rememberUpdatedState(palette)

  fun launchReadOnlyDrag(state: SelectionLadderState): Boolean {
    val activeScope = state.currentScope ?: return false
    val snippet =
      latestText.substring(
        activeScope.start.coerceIn(0, latestText.length),
        activeScope.end.coerceIn(0, latestText.length),
      )
    if (snippet.isEmpty()) return false
    val payload =
      EdgeDragPayload(
        dragId = EdgeTextDragCoordinator.nextDragId(),
        sourceSurfaceId = surfaceId,
        sourceEditable = false,
        sourceRange = null,
        text = snippet,
        preferredMode = DragTransferMode.COPY,
        scopeLabel = activeScope.label,
        allowCrossPaneMove = false,
      )
    val started =
      EdgeTextDragCoordinator.startTextDrag(
        view = view,
        payload = payload,
        palette = latestPalette,
        allowGlobal = latestAllowGlobal,
        isV2 = isV2,
      )
    if (started) {
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      showSelectionPopup = false
    }
    return started
  }

  val activeRange = ladderState?.currentScope?.let { it.start to it.end }
  val renderedText =
    remember(text, annotatedText, activeRange, palette.activePillBg, palette.activePillFg) {
      val base = annotatedText ?: AnnotatedString(text)
      if (activeRange == null) {
        base
      } else {
        val safeStart = activeRange.first.coerceIn(0, base.length)
        val safeEnd = activeRange.second.coerceIn(safeStart, base.length)
        buildAnnotatedString {
          append(base)
          if (safeEnd > safeStart) {
            addStyle(
              style =
                SpanStyle(
                  background = palette.activePillBg.copy(alpha = 0.72f),
                  color = palette.activePillFg,
                  fontWeight = FontWeight.SemiBold,
                ),
              start = safeStart,
              end = safeEnd,
            )
          }
        }
      }
    }

  Box(modifier = modifier) {
    Text(
      text = renderedText,
      style = style,
      color = color,
      maxLines = maxLines,
      overflow = overflow,
      onTextLayout = { layoutResult = it },
      modifier =
        Modifier.pointerInput(surfaceId) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downPos = down.position
            val touchSlop = viewConfiguration.touchSlop
            var holdTriggered = false
            var dragLaunched = false
            var movedPastSlopBeforeHold = false
            var progressiveJob: Job? = null

            val holdJob =
              scope.launch {
                delay(latestHoldDelayMs.coerceIn(150L, 900L))
                if (movedPastSlopBeforeHold || latestText.isEmpty()) return@launch
                val layout = layoutResult ?: return@launch
                val anchorOffset = layout.getOffsetForPosition(downPos).coerceIn(0, latestText.length)
                val rawLadder =
                  SmartTextSelectionEngine.resolveAt(
                    text = latestText,
                    offset = anchorOffset,
                    domain = latestDomain,
                    initialDepth = if (latestClassicWord) SmartSelectionInitialDepth.COMPACT else latestInitialDepth,
                  )
                val ladder =
                  if (latestClassicWord) {
                    val wordScope = rawLadder.scopes.firstOrNull()?.copy(label = "Word")
                    if (wordScope != null) SelectionLadder(listOf(wordScope), 0, anchorOffset) else rawLadder
                  } else {
                    rawLadder
                  }
                val initialState = SelectionLadderState(ladder = ladder, activeIndex = ladder.initialIndex)
                ladderState = initialState
                popupAnchorPx = IntOffset(downPos.x.roundToInt(), (downPos.y - 48f).roundToInt())
                holdTriggered = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                if (latestProgressive && !latestClassicWord) {
                  progressiveJob =
                    launch {
                      while (true) {
                        delay(450L)
                        val cur = ladderState ?: break
                        if (!cur.canExpand) break
                        ladderState = cur.expand()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      }
                    }
                }
              }

            try {
              while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                  if (holdTriggered) {
                    change.consume()
                  } else if (ladderState != null) {
                    ladderState = null
                    showSelectionPopup = false
                  }
                  break
                }
                val dist = hypot(change.position.x - downPos.x, change.position.y - downPos.y)
                if (!holdTriggered) {
                  if (dist > touchSlop) {
                    movedPastSlopBeforeHold = true
                    holdJob.cancel()
                    // Yield immediately without consuming so parent LazyColumn / scroll container scrolls naturally
                    break
                  }
                } else {
                  change.consume()
                  if (latestHoldToDrag && !dragLaunched && dist > touchSlop) {
                    progressiveJob?.cancel()
                    val currentState = ladderState
                    if (currentState != null && launchReadOnlyDrag(currentState)) {
                      dragLaunched = true
                      break
                    }
                  }
                }
              }
            } finally {
              holdJob.cancel()
              progressiveJob?.cancel()
            }

            if (holdTriggered && !dragLaunched && ladderState != null) {
              showSelectionPopup = true
            }
          }
        },
    )

    val currentState = ladderState
    val currentScope = currentState?.currentScope
    if (showSelectionPopup && currentState != null && currentScope != null) {
      val selectedSnippet =
        text.substring(
          currentScope.start.coerceIn(0, text.length),
          currentScope.end.coerceIn(0, text.length),
        )
      Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(popupAnchorPx.x.coerceAtLeast(0), popupAnchorPx.y.coerceAtLeast(-52)),
        onDismissRequest = {
          showSelectionPopup = false
          ladderState = null
        },
        properties = PopupProperties(focusable = false),
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = palette.chromeSurface,
          border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.75f)),
          shadowElevation = 6.dp,
          modifier = Modifier.testTag("smart_text_popup_$surfaceId"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            if (showDragGrip) {
              Box(
                modifier =
                  Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(EmeraldLive.copy(alpha = 0.14f))
                    .border(0.5.dp, EmeraldLive.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                    .pointerInput(currentState) {
                      awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        launchReadOnlyDrag(currentState)
                      }
                    }
                    .padding(horizontal = 7.dp)
                    .testTag("smart_text_drag_grip_$surfaceId"),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Outlined.DragIndicator,
                  contentDescription = "Drag selected text",
                  tint = EmeraldLive,
                  modifier = Modifier.size(15.dp),
                )
              }
            }

            if (showScopeBadge) {
              Box(
                modifier =
                  Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.activePillBg)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
              ) {
                Text(
                  text = currentScope.label,
                  fontFamily = JetBrainsMono,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
            }

            if (showExpandShrink) {
              Box(
                modifier =
                  Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(enabled = currentState.canShrink) {
                      ladderState = currentState.shrink()
                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    .testTag("smart_text_shrink_$surfaceId"),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Remove,
                  contentDescription = "Shrink selection",
                  tint = if (currentState.canShrink) palette.textPrimary else palette.textMuted.copy(alpha = 0.35f),
                  modifier = Modifier.size(15.dp),
                )
              }

              Box(
                modifier =
                  Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(enabled = currentState.canExpand) {
                      ladderState = currentState.expand()
                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    .testTag("smart_text_expand_$surfaceId"),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Add,
                  contentDescription = "Expand selection",
                  tint = if (currentState.canExpand) palette.textPrimary else palette.textMuted.copy(alpha = 0.35f),
                  modifier = Modifier.size(15.dp),
                )
              }
            }

            if (showQuickActions) {
              Box(
                modifier =
                  Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable {
                      clipboard.setText(AnnotatedString(selectedSnippet))
                      onCopied?.invoke(selectedSnippet)
                      showSelectionPopup = false
                      ladderState = null
                    }
                    .testTag("smart_text_copy_$surfaceId"),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Outlined.ContentCopy,
                  contentDescription = "Copy selection",
                  tint = palette.textPrimary,
                  modifier = Modifier.size(14.dp),
                )
              }
            }

            Box(
              modifier =
                Modifier
                  .size(26.dp)
                  .clip(RoundedCornerShape(7.dp))
                  .clickable {
                    showSelectionPopup = false
                    ladderState = null
                  },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Dismiss selection",
                tint = palette.textMuted,
                modifier = Modifier.size(14.dp),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Reusable Compose drop-target modifier for single-line and multi-line text inputs across EdgeCode.
 * Highlights the field with an emerald drop ring while hovering and formats dropped text cleanly.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.edgeTextDropTarget(
  targetSurfaceId: String,
  currentValue: String,
  onValueChange: (String) -> Unit,
  singleLine: Boolean = true,
  replaceIfFullUrl: Boolean = true,
  replaceAll: Boolean = false,
  shape: Shape = RoundedCornerShape(10.dp),
  onDropFeedback: ((String) -> Unit)? = null,
): Modifier =
  composed {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isHovering by remember(targetSurfaceId) { mutableStateOf(false) }

    val latestValue by rememberUpdatedState(currentValue)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestFeedback by rememberUpdatedState(onDropFeedback)

    val callback =
      remember(targetSurfaceId, singleLine, replaceIfFullUrl, replaceAll) {
        object : DragAndDropTarget {
          override fun onEntered(event: DragAndDropEvent) {
            isHovering = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          }

          override fun onExited(event: DragAndDropEvent) {
            isHovering = false
          }

          override fun onEnded(event: DragAndDropEvent) {
            isHovering = false
          }

          override fun onDrop(event: DragAndDropEvent): Boolean {
            isHovering = false
            val androidEvent = event.toAndroidDragEvent()
            val dropped =
              EdgeTextDragCoordinator.extractDroppedTextWithPermissions(androidEvent, context)
                ?: return false
            val updated =
              TextDropFormatter.resolveTextDropValue(
                currentValue = latestValue,
                droppedText = dropped,
                singleLine = singleLine,
                replaceIfFullUrl = replaceIfFullUrl,
                replaceAll = replaceAll,
              )
            latestOnValueChange(updated)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            latestFeedback?.invoke("Dropped into $targetSurfaceId")
            return true
          }
        }
      }

    this
      .dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
          val desc = event.toAndroidDragEvent().clipDescription
          EdgeTextDragCoordinator.hasSupportedTextMime(desc) ||
            event.mimeTypes().any { it.startsWith("text/") }
        },
        target = callback,
      )
      .then(
        if (isHovering) {
          Modifier
            .clip(shape)
            .background(EmeraldLive.copy(alpha = 0.08f), shape)
            .border(1.75.dp, EmeraldLive, shape)
        } else {
          Modifier
        },
      )
  }
