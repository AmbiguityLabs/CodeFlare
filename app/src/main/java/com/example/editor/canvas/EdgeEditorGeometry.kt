package com.example.editor.canvas

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.example.editor.engine.EdgeEditorSession
import com.example.editor.engine.EditorDocument
import com.example.editor.engine.FoldModel
import com.example.editor.engine.IncrementalLineStyles
import com.example.editor.engine.LineRenderCache
import com.example.editor.engine.WrapModel
import kotlin.math.abs
import kotlin.math.floor

/**
 * Pure selection-handle drag model ported from CodeAssist's SelectionHandleDrag:
 * - Relative finger delta (no jump when grabbing off-center)
 * - Fixed far end recorded at grab time (crossing handles never collapses selection)
 * - Dead zone & X/Y boundary resistance so shaky fingers never flicker between chars/lines
 */
class SelectionHandleDrag(
  private val fixedEnd: Int?,
  start: Int,
  private val grab: Offset,
  hotspot: Offset,
  private val lineHeight: Float,
  private val deadZone: Float,
  private val resistX: Float,
  private val resistY: Float,
) {
  private val fingerToHotspot = hotspot - grab

  var moving: Int = start
    private set

  var started: Boolean = false
    private set

  val selection: Pair<Int, Int> get() = (fixedEnd ?: moving) to moving

  fun moveTo(
    finger: Offset,
    offsetAt: (Offset) -> Int,
    caretAt: (Int) -> CaretPoint,
  ): Boolean {
    if (!started) {
      if ((finger - grab).getDistance() <= deadZone) return false
      started = true
    }
    val target = finger + fingerToHotspot
    val candidate = offsetAt(target)
    if (candidate == moving) return false
    val now = caretAt(moving)
    val next = caretAt(candidate)
    val accept =
      if (next.rowTop != now.rowTop) {
        if (next.rowTop > now.rowTop) target.y >= next.rowTop + resistY
        else target.y <= next.rowTop + lineHeight - resistY
      } else {
        abs(target.x - next.x) + resistX <= abs(target.x - now.x)
      }
    if (!accept) return false
    moving = candidate
    return true
  }

  data class CaretPoint(val x: Float, val rowTop: Float)
}

/**
 * Coordinates, scrolling, wrap/fold projection, and hit-testing for the pure Compose Canvas editor.
 * Scroll offsets ([vOffset], [hOffset]) are read ONLY during draw/pointer passes, producing zero
 * recompositions during fling, scroll, or handle drag.
 */
@Stable
class EdgeEditorGeometry(
  val wrapModel: WrapModel = WrapModel(),
) {
  var vOffset: Float by mutableFloatStateOf(0f)
  var hOffset: Float by mutableFloatStateOf(0f)
  var viewport: IntSize by mutableStateOf(IntSize.Zero)

  var lineHeightPx: Float = 42f
  var baselineOffsetPx: Float = 32f
  var charWidthPx: Float = 20f
  var gutterWidthPx: Float = 96f
  var textPaddingStartPx: Float = 16f
  var topPaddingPx: Float = 12f
  var bottomPaddingPx: Float = 160f
  var showLineNumbers: Boolean = true
  var wordWrap: Boolean = false
  var codeFolding: Boolean = true
  var gutterDiffWidthPx: Float = 8f

  val effectiveGutterPx: Float
    get() = if (showLineNumbers) gutterWidthPx else 12f

  val textOriginX: Float
    get() = effectiveGutterPx + textPaddingStartPx

  val wrapWidthPx: Int
    get() {
      if (!wordWrap || viewport.width <= 0) return 0
      return (viewport.width - textOriginX - textPaddingStartPx).toInt().coerceAtLeast(120)
    }

  fun syncWrapLayout(doc: EditorDocument, fold: FoldModel, cache: LineRenderCache) {
    if (!wordWrap) return
    val w = wrapWidthPx
    if (wrapModel.totalRows == 0 || doc.lineCount != lastWrapLineCount || w != lastWrapWidth) {
      wrapModel.resize(doc.lineCount)
      for (line in 0 until doc.lineCount) {
        wrapModel.setRows(line, cache.estimateWrapRows(doc.lineLength(line), w))
      }
      lastWrapLineCount = doc.lineCount
      lastWrapWidth = w
    }
    wrapModel.ensure(fold)
  }

  private var lastWrapLineCount = -1
  private var lastWrapWidth = -1

  fun invalidateWrapEstimate() {
    lastWrapLineCount = -1
  }

  fun totalVisualRows(fold: FoldModel): Int =
    if (wordWrap) wrapModel.totalRows.coerceAtLeast(1) else fold.visualLineCount.coerceAtLeast(1)

  fun maxVerticalScroll(fold: FoldModel): Float {
    val contentH = topPaddingPx + totalVisualRows(fold) * lineHeightPx + bottomPaddingPx
    return (contentH - viewport.height).coerceAtLeast(0f)
  }

  fun maxHorizontalScroll(session: EdgeEditorSession, cache: LineRenderCache): Float {
    if (wordWrap) return 0f
    val estWidth = (session.maxLineChars + 8) * charWidthPx
    val contentW = maxOf(estWidth, cache.measuredMaxWidth + charWidthPx * 6f)
    val avail = (viewport.width - textOriginX - textPaddingStartPx).coerceAtLeast(1f)
    return (contentW - avail).coerceAtLeast(0f)
  }

  fun clampScroll(session: EdgeEditorSession, cache: LineRenderCache) {
    val maxV = maxVerticalScroll(session.foldModel)
    vOffset = vOffset.coerceIn(0f, maxV)
    val maxH = maxHorizontalScroll(session, cache)
    hOffset = if (wordWrap) 0f else hOffset.coerceIn(0f, maxH)
  }

  fun scrollBy(dx: Float, dy: Float, session: EdgeEditorSession, cache: LineRenderCache): Offset {
    val prevH = hOffset
    val prevV = vOffset
    val maxV = maxVerticalScroll(session.foldModel)
    val maxH = maxHorizontalScroll(session, cache)
    vOffset = (vOffset + dy).coerceIn(0f, maxV)
    hOffset = if (wordWrap) 0f else (hOffset + dx).coerceIn(0f, maxH)
    return Offset(hOffset - prevH, vOffset - prevV)
  }

  fun scaleScrollBy(ratio: Float, session: EdgeEditorSession, cache: LineRenderCache) {
    if (ratio <= 0f || !ratio.isFinite()) return
    vOffset = (vOffset * ratio).coerceIn(0f, maxVerticalScroll(session.foldModel))
    if (!wordWrap) {
      hOffset = (hOffset * ratio).coerceIn(0f, maxHorizontalScroll(session, cache))
    }
  }

  fun lineTopViewportY(docLine: Int, fold: FoldModel): Float {
    val row =
      if (wordWrap) wrapModel.topRow(docLine)
      else fold.visualLineForDoc(docLine)
    return topPaddingPx + row * lineHeightPx - vOffset
  }

  fun rowsForLine(docLine: Int, fold: FoldModel): Int =
    if (wordWrap) wrapModel.rowsOf(docLine).coerceAtLeast(1)
    else if (fold.isHidden(docLine)) 0 else 1

  fun firstVisibleRow(): Int =
    floor(((vOffset - topPaddingPx) / lineHeightPx).coerceAtLeast(0f)).toInt()

  fun lastVisibleRow(fold: FoldModel): Int {
    val total = totalVisualRows(fold)
    if (viewport.height <= 0) return minOf(40, total - 1).coerceAtLeast(0)
    val bottomRow = floor(((vOffset + viewport.height - topPaddingPx) / lineHeightPx)).toInt() + 1
    return bottomRow.coerceIn(0, (total - 1).coerceAtLeast(0))
  }

  fun docLineAtViewportY(y: Float, fold: FoldModel): Int {
    val row = floor(((y + vOffset - topPaddingPx) / lineHeightPx)).toInt().coerceAtLeast(0)
    return if (wordWrap) wrapModel.docLineForRow(row)
    else fold.docLineForVisual(row)
  }

  fun caretGeometry(
    offset: Int,
    doc: EditorDocument,
    fold: FoldModel,
    styles: IncrementalLineStyles,
    cache: LineRenderCache,
  ): EditorCaretRect {
    val safe = offset.coerceIn(0, doc.length)
    val rawLine = doc.lineForOffset(safe)
    val hiddenIn = fold.foldContaining(rawLine)
    val docLine = hiddenIn?.startLine ?: rawLine
    val lineTop = lineTopViewportY(docLine, fold)
    val col = (safe - doc.lineStart(docLine)).coerceIn(0, doc.lineLength(docLine))

    if (hiddenIn != null || fold.foldStartingAt(docLine) != null) {
      val layout = cache.layoutForLine(doc, styles, docLine)
      val x = textOriginX - hOffset + layout.getHorizontalPosition(col.coerceIn(0, layout.layoutInput.text.length), true)
      return EditorCaretRect(x, lineTop, lineTop + baselineOffsetPx, lineTop + lineHeightPx)
    }

    val layout = cache.layoutForLine(doc, styles, docLine)
    if (wordWrap) wrapModel.setRows(docLine, layout.lineCount)
    val safeCol = col.coerceIn(0, layout.layoutInput.text.length)
    val box = layout.getCursorRect(safeCol)
    val wrapLine = layout.getLineForOffset(safeCol)
    val rowTop = lineTop + wrapLine * lineHeightPx
    val x = textOriginX - hOffset + box.left
    return EditorCaretRect(
      horizontalPosition = x,
      top = rowTop,
      baseline = rowTop + baselineOffsetPx,
      bottom = rowTop + lineHeightPx,
    )
  }

  fun offsetAt(
    point: Offset,
    doc: EditorDocument,
    fold: FoldModel,
    styles: IncrementalLineStyles,
    cache: LineRenderCache,
  ): Int {
    val docLine = docLineAtViewportY(point.y, fold).coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
    val lineTop = lineTopViewportY(docLine, fold)
    val localX = (point.x - textOriginX + hOffset).coerceAtLeast(0f)
    val localY = (point.y - lineTop).coerceAtLeast(0f)
    val layout = cache.layoutForLine(doc, styles, docLine)
    if (wordWrap) wrapModel.setRows(docLine, layout.lineCount)
    val col = layout.getOffsetForPosition(Offset(localX, localY)).coerceIn(0, doc.lineLength(docLine))
    return doc.lineStart(docLine) + col
  }

  /**
   * Returns 0-based docLine if tap landed on a fold chevron inside the gutter, or -1.
   */
  fun foldActionAt(point: Offset, session: EdgeEditorSession): Int {
    if (!codeFolding || !showLineNumbers) return -1
    val chevronLeft = effectiveGutterPx - 28f * (charWidthPx / 20f).coerceIn(0.8f, 1.8f)
    if (point.x < chevronLeft || point.x > effectiveGutterPx + 10f) return -1
    val docLine = docLineAtViewportY(point.y, session.foldModel)
    val isFoldable =
      session.foldRegions.any { it.startLine == docLine } ||
        session.foldModel.foldStartingAt(docLine) != null
    return if (isFoldable) docLine else -1
  }

  /**
   * Returns 0-based docLine if tap landed on the line-number column (bookmark toggle), or -1.
   */
  fun bookmarkActionAt(point: Offset, session: EdgeEditorSession): Int {
    if (!showLineNumbers) return -1
    if (point.x < 0f || point.x >= effectiveGutterPx) return -1
    val docLine = docLineAtViewportY(point.y, session.foldModel)
    return if (docLine in 0 until session.doc.lineCount) docLine else -1
  }

  fun ensureCaretVisible(
    session: EdgeEditorSession,
    styles: IncrementalLineStyles,
    cache: LineRenderCache,
    topClearancePx: Float = 0f,
  ) {
    if (viewport.width <= 0 || viewport.height <= 0) return
    val caret = caretGeometry(session.selection.end, session.doc, session.foldModel, styles, cache)
    val padY = lineHeightPx * 1.4f
    val minY = topClearancePx + padY * 0.5f
    val maxY = (viewport.height - padY).coerceAtLeast(minY + lineHeightPx)

    if (caret.top < minY) {
      vOffset = (vOffset - (minY - caret.top)).coerceIn(0f, maxVerticalScroll(session.foldModel))
    } else if (caret.bottom > maxY) {
      vOffset = (vOffset + (caret.bottom - maxY)).coerceIn(0f, maxVerticalScroll(session.foldModel))
    }

    if (!wordWrap) {
      val minX = textOriginX + charWidthPx * 2f
      val maxX = (viewport.width - charWidthPx * 4f).coerceAtLeast(minX + charWidthPx)
      if (caret.horizontalPosition < minX) {
        hOffset = (hOffset - (minX - caret.horizontalPosition)).coerceAtLeast(0f)
      } else if (caret.horizontalPosition > maxX) {
        hOffset = (hOffset + (caret.horizontalPosition - maxX)).coerceIn(0f, maxHorizontalScroll(session, cache))
      }
    }
  }
}
