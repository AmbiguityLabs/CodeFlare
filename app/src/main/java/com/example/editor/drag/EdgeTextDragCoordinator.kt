package com.example.editor.drag

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.DragAndDropPermissions
import android.view.DragEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.IdePalette
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

enum class DragTransferMode {
  MOVE,
  COPY,
}

/**
 * In-process metadata attached to Android `startDragAndDrop`'s `localState` alongside standard
 * plain-text [ClipData], allowing zero-latency same-file Move vs cross-pane Copy arbitration.
 */
data class EdgeDragPayload(
  val dragId: Long,
  val sourceSurfaceId: String,
  val sourceEditable: Boolean,
  val sourceRange: IntRange?,
  val text: String,
  val preferredMode: DragTransferMode,
  val scopeLabel: String,
  val allowCrossPaneMove: Boolean = false,
)

/**
 * Live draw-phase hover state while a drag passes over the custom canvas Code Editor.
 */
data class EditorDropHoverState(
  val localOffset: Offset,
  val dropDocOffset: Int,
  val isSelfSource: Boolean,
  val effectiveMode: DragTransferMode,
  val previewSnippet: String,
)

object EdgeTextDragCoordinator {
  private val idGen = AtomicLong(1L)

  fun nextDragId(): Long = idGen.getAndIncrement()

  fun startTextDrag(
    view: View,
    payload: EdgeDragPayload,
    palette: IdePalette,
    allowGlobal: Boolean,
    isV2: Boolean,
  ): Boolean {
    if (payload.text.isEmpty()) return false
    val clip = ClipData.newPlainText(payload.scopeLabel.ifBlank { "EdgeCode Text" }, payload.text)
    val shadow = FloatingPillDragShadowBuilder(view.context, payload, palette, isV2)
    val flags = if (allowGlobal) View.DRAG_FLAG_GLOBAL else 0
    return runCatching {
      view.startDragAndDrop(clip, shadow, payload, flags)
    }.getOrDefault(false)
  }

  fun inAppPayload(event: DragEvent): EdgeDragPayload? =
    event.localState as? EdgeDragPayload

  fun resolveEffectiveMode(
    payload: EdgeDragPayload?,
    targetSurfaceId: String,
  ): DragTransferMode {
    if (payload == null) return DragTransferMode.COPY
    if (!payload.sourceEditable || payload.sourceRange == null) return DragTransferMode.COPY
    if (payload.preferredMode == DragTransferMode.COPY) return DragTransferMode.COPY
    if (payload.sourceSurfaceId == targetSurfaceId) return DragTransferMode.MOVE
    return if (payload.allowCrossPaneMove) DragTransferMode.MOVE else DragTransferMode.COPY
  }

  fun hasSupportedTextMime(description: ClipDescription?): Boolean {
    if (description == null) return false
    return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
      description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) ||
      description.hasMimeType("text/*")
  }

  fun previewSnippet(text: String): String {
    val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
    val extraLines = text.count { it == '\n' }
    val clipped = if (firstLine.length > 28) firstLine.take(27) + "…" else firstLine.ifEmpty { "whitespace" }
    return if (extraLines > 0) "$clipped  +${extraLines}ln" else clipped
  }

  /**
   * Unwraps an [Activity] from a Compose or themed [Context] wrapper chain.
   */
  tailrec fun findActivity(context: Context?): Activity? =
    when (context) {
      null -> null
      is Activity -> context
      is ContextWrapper -> findActivity(context.baseContext)
      else -> null
    }

  /**
   * Extracts dropped text from an Android [DragEvent], cleanly requesting and releasing
   * [DragAndDropPermissions] for cross-app drops originating outside EdgeCode.
   */
  fun extractDroppedTextWithPermissions(
    event: DragEvent,
    context: Context,
  ): String? {
    val inApp = inAppPayload(event)
    if (inApp != null && inApp.text.isNotEmpty()) return inApp.text

    val activity = findActivity(context)
    val permissions: DragAndDropPermissions? =
      runCatching { activity?.requestDragAndDropPermissions(event) }.getOrNull()
    return try {
      extractTextFromClipData(event.clipData, context)
    } finally {
      runCatching { permissions?.release() }
    }
  }

  /**
   * Extracts plain text from an Android [ClipData] (used by in-app & external cross-app drops).
   */
  fun extractTextFromClipData(clipData: ClipData?, context: Context? = null): String? {
    if (clipData == null || clipData.itemCount <= 0) return null
    val isHtmlClip = clipData.description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true
    val builder = StringBuilder()
    for (i in 0 until clipData.itemCount) {
      val item = clipData.getItemAt(i) ?: continue
      val piece =
        item.text?.toString()?.takeIf { it.isNotEmpty() }
          ?: item.htmlText?.let(::stripHtmlTags)?.takeIf { it.isNotEmpty() }
          ?: context?.let { item.coerceToText(it)?.toString() }?.let { raw ->
            if (isHtmlClip && raw.contains('<') && raw.contains('>')) stripHtmlTags(raw) else raw
          }?.takeIf { it.isNotEmpty() }
      if (!piece.isNullOrEmpty()) {
        if (builder.isNotEmpty()) builder.append('\n')
        builder.append(piece)
      }
    }
    return builder.toString().takeIf { it.isNotEmpty() }
  }

  internal fun stripHtmlTags(rawHtml: String): String =
    rawHtml
      .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
      .replace(Regex("</(p|div|li|tr)\\s*>", RegexOption.IGNORE_CASE), "\n")
      .replace(Regex("<[^>]+>"), "")
      .replace("&nbsp;", " ")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace(Regex("\n{3,}"), "\n\n")
      .trim('\n')
}

/**
 * Custom frosted floating drag-pill shadow rendered ABOVE the fingertip touch point so the
 * user's thumb never blocks the live drop caret underneath.
 */
private class FloatingPillDragShadowBuilder(
  context: Context,
  private val payload: EdgeDragPayload,
  private val palette: IdePalette,
  private val isV2: Boolean,
) : View.DragShadowBuilder() {
  private val density = context.resources.displayMetrics.density
  private val snippet = EdgeTextDragCoordinator.previewSnippet(payload.text)
  private val badgeLabel = if (payload.preferredMode == DragTransferMode.MOVE && payload.sourceEditable) "MOVE" else "COPY"

  private val textPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = palette.textPrimary.toArgb()
      textSize = 12f * density
      typeface = Typeface.MONOSPACE
    }

  private val badgePaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color =
        if (badgeLabel == "MOVE") palette.ctaBg.toArgb()
        else EmeraldLive.toArgb()
      textSize = 9.5f * density
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

  private val bgPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = palette.glassElevated.copy(alpha = 0.96f).toArgb()
      style = Paint.Style.FILL
      setShadowLayer(8f * density, 0f, 3f * density, 0x55000000)
    }

  private val borderPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color =
        if (badgeLabel == "MOVE") palette.ctaBg.copy(alpha = 0.65f).toArgb()
        else EmeraldLive.copy(alpha = 0.65f).toArgb()
      style = Paint.Style.STROKE
      strokeWidth = 1.25f * density
    }

  private val badgeBgPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color =
        if (badgeLabel == "MOVE") palette.ctaBg.copy(alpha = 0.18f).toArgb()
        else EmeraldLive.copy(alpha = 0.18f).toArgb()
      style = Paint.Style.FILL
    }

  private val padH = 12f * density
  private val shadowPad = 10f * density
  private val pillHeight = 36f * density
  private val maxPillWidth = 232f * density
  private val badgeTextW = badgePaint.measureText(badgeLabel)
  private val badgePillW = badgeTextW + 12f * density
  private val gap = 8f * density
  private val maxSnippetW = (maxPillWidth - padH * 2f - badgePillW - gap).coerceAtLeast(48f * density)
  private val ellipsizedSnippet = TextUtils.ellipsize(snippet, textPaint, maxSnippetW, TextUtils.TruncateAt.END).toString()
  private val snippetW = textPaint.measureText(ellipsizedSnippet)
  private val pillWidth = (padH * 2f + badgePillW + gap + snippetW).coerceIn(112f * density, maxPillWidth)

  override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
    val totalW = (pillWidth + shadowPad * 2f).roundToInt()
    val fingerLiftPx = (30f * density).roundToInt()
    val totalH = (pillHeight + shadowPad * 2f + fingerLiftPx).roundToInt()
    outShadowSize.set(totalW, totalH)
    // Place touch anchor below the pill so the pill floats clearly above the fingertip
    outShadowTouchPoint.set(totalW / 2, totalH - (4f * density).roundToInt())
  }

  override fun onDrawShadow(canvas: Canvas) {
    val rect = RectF(shadowPad, shadowPad, shadowPad + pillWidth, shadowPad + pillHeight)
    val radius = if (isV2) pillHeight * 0.5f else 11f * density
    canvas.drawRoundRect(rect, radius, radius, bgPaint)
    canvas.drawRoundRect(rect, radius, radius, borderPaint)

    // Mode pill chip
    val badgeH = 20f * density
    val badgeTop = rect.top + (pillHeight - badgeH) * 0.5f
    val badgeRect = RectF(rect.left + padH * 0.75f, badgeTop, rect.left + padH * 0.75f + badgePillW, badgeTop + badgeH)
    canvas.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, badgeBgPaint)

    val badgeFm = badgePaint.fontMetrics
    val badgeBaseline = badgeRect.centerY() - (badgeFm.ascent + badgeFm.descent) * 0.5f
    canvas.drawText(badgeLabel, badgeRect.left + 6f * density, badgeBaseline, badgePaint)

    // Snippet text
    val textFm = textPaint.fontMetrics
    val textBaseline = rect.centerY() - (textFm.ascent + textFm.descent) * 0.5f
    canvas.drawText(ellipsizedSnippet, badgeRect.right + gap, textBaseline, textPaint)
  }
}
