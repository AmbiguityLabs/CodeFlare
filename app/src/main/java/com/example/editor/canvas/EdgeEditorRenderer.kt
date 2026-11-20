package com.example.editor.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.DiagnosticItem
import com.example.data.remote.DiagnosticSeverity
import com.example.data.security.EditorSettings
import com.example.editor.BracketMatch
import com.example.editor.drag.DragTransferMode
import com.example.editor.drag.EditorDropHoverState
import com.example.editor.engine.EdgeEditorSession
import com.example.editor.engine.EditorDocument
import com.example.editor.engine.IncrementalLineStyles
import com.example.editor.engine.LineChange
import com.example.editor.engine.LineRenderCache
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.IdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.selectionColorsFor

data class EdgeEditorDrawParams(
  val session: EdgeEditorSession,
  val geometry: EdgeEditorGeometry,
  val styles: IncrementalLineStyles,
  val cache: LineRenderCache,
  val gutterMeasurer: TextMeasurer,
  val gutterStyle: TextStyle,
  val palette: IdePalette,
  val hasCustomBg: Boolean,
  val indentGuides: Boolean,
  val gutterDiffMarkers: Boolean,
  val tabSize: Int,
  val diagnostics: List<DiagnosticItem>,
  val findMatches: List<IntRange>,
  val activeFindMatchIndex: Int,
  val bracketMatch: BracketMatch?,
  val lineChanges: List<LineChange>,
  val caretSymbolWord: String?,
  val caretAnimatedX: Float,
  val caretAnimatedY: Float,
  val caretAlpha: Float,
  val showCaret: Boolean,
  val showTouchHandles: Boolean,
  val caretStyle: String = EditorSettings.CARET_LINE,
  val flameTime: Float = 0f,
  val dropHoverState: EditorDropHoverState? = null,
  val isSelfDragMoveActive: Boolean = false,
)

/**
 * Pure draw-phase CodeAssist canvas renderer.
 * Reads scroll offsets, layouts, and syntax spans exclusively inside [DrawScope], incurring
 * ZERO Compose recompositions while scrolling, flinging, pinching, or dragging handles.
 */
fun DrawScope.drawEdgeEditor(p: EdgeEditorDrawParams) {
  val doc = p.session.doc
  val fold = p.session.foldModel
  val geom = p.geometry
  val palette = p.palette
  val lineH = geom.lineHeightPx
  val textX = geom.textOriginX - geom.hOffset
  val gutterW = geom.effectiveGutterPx

  if (!p.hasCustomBg) {
    drawRect(palette.editorBg)
  }

  val firstRow = geom.firstVisibleRow()
  val lastRow = geom.lastVisibleRow(fold)

  // Collect visible document lines once
  val visibleDocLines = ArrayList<Int>(48)
  if (geom.wordWrap) {
    var docLine = geom.wrapModel.docLineForRow(firstRow)
    while (docLine < doc.lineCount) {
      if (!fold.isHidden(docLine)) {
        val topY = geom.lineTopViewportY(docLine, fold)
        if (topY > size.height + lineH) break
        visibleDocLines.add(docLine)
      }
      docLine++
    }
  } else {
    var vRow = firstRow
    while (vRow <= lastRow && vRow < fold.visualLineCount) {
      visibleDocLines.add(fold.docLineForVisual(vRow))
      vRow++
    }
  }

  // Ensure incremental syntax tokenizer is caught up through the bottom visible line
  val maxVisDocLine = visibleDocLines.lastOrNull() ?: 0
  p.styles.ensureUpTo(doc, maxVisDocLine)

  // Pre-group diagnostics by 0-based doc line for O(1) draw lookup
  val diagsByLine =
    if (p.diagnostics.isEmpty()) emptyMap()
    else p.diagnostics.groupBy { (it.line - 1).coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0)) }

  val caretOffset = p.session.selection.end.coerceIn(0, doc.length)
  val activeDocLine = doc.lineForOffset(caretOffset)
  val sel = p.session.selection
  val comp = p.session.composing
  val foldedStarts = p.session.foldRegions.associateBy { it.startLine }

  // ---- Pass 1: Text-area clipped layers ----
  clipRect(left = gutterW, top = 0f, right = size.width, bottom = size.height) {
    // 1a. Active line band & merge conflict marker bands
    for (docLine in visibleDocLines) {
      val topY = geom.lineTopViewportY(docLine, fold)
      val rows = geom.rowsForLine(docLine, fold).coerceAtLeast(1)
      val bandH = rows * lineH
      val lineStr = doc.lineText(docLine)

      when {
        lineStr.startsWith("<<<<<<<") ->
          drawRect(EmeraldLive.copy(alpha = 0.16f), Offset(gutterW, topY), Size(size.width - gutterW, bandH))
        lineStr.startsWith("=======") ->
          drawRect(AmberWarn.copy(alpha = 0.16f), Offset(gutterW, topY), Size(size.width - gutterW, bandH))
        lineStr.startsWith(">>>>>>>") ->
          drawRect(RoseError.copy(alpha = 0.16f), Offset(gutterW, topY), Size(size.width - gutterW, bandH))
        docLine == activeDocLine && sel.collapsed -> {
          val alpha = if (p.hasCustomBg) 0.52f else 1f
          drawRect(
            palette.editorActiveLineBg.copy(alpha = alpha),
            Offset(gutterW, topY),
            Size(size.width - gutterW, bandH),
          )
          drawRect(
            palette.activePillFg.copy(alpha = 0.78f),
            Offset(gutterW, topY + 2f),
            Size(2.dp.toPx(), (bandH - 4f).coerceAtLeast(4f)),
          )
        }
      }
    }

    // 1b. Tab-aligned vertical indent guides bridged across blank lines
    if (p.indentGuides && visibleDocLines.isNotEmpty()) {
      val guideColor = palette.border.copy(alpha = if (palette.isDark) 0.45f else 0.55f)
      val stepPx = p.tabSize.coerceIn(1, 8) * geom.charWidthPx
      var prevIndentCols = 0
      for (idx in visibleDocLines.indices) {
        val docLine = visibleDocLines[idx]
        val text = doc.lineText(docLine)
        val indentCols =
          if (text.isBlank()) {
            val nextCols =
              if (idx + 1 < visibleDocLines.size) leadingIndentCols(doc.lineText(visibleDocLines[idx + 1]), p.tabSize)
              else 0
            minOf(prevIndentCols, nextCols)
          } else {
            leadingIndentCols(text, p.tabSize).also { prevIndentCols = it }
          }
        val levels = indentCols / p.tabSize.coerceAtLeast(1)
        if (levels > 0) {
          val topY = geom.lineTopViewportY(docLine, fold)
          val bandH = geom.rowsForLine(docLine, fold).coerceAtLeast(1) * lineH
          for (lvl in 1..levels) {
            val gx = textX + (lvl - 1) * stepPx + 1f
            if (gx >= gutterW && gx <= size.width) {
              drawLine(guideColor, Offset(gx, topY), Offset(gx, topY + bandH), strokeWidth = 1.dp.toPx())
            }
          }
        }
      }
    }

    // 1c. Per-line highlights, text, squiggles, brackets, composing underline
    val selPalette = selectionColorsFor(palette)
    val selBg = if (p.isSelfDragMoveActive) selPalette.canvasMoveFill else selPalette.canvasFill
    val selOutline = if (p.isSelfDragMoveActive) Color.Transparent else selPalette.canvasOutline
    val symBg = palette.activePillFg.copy(alpha = 0.14f)
    val bracketFill = palette.activePillBg.copy(alpha = 0.9f)
    val bracketStroke = palette.activePillFg.copy(alpha = 0.85f)
    val mismatchFill = RoseError.copy(alpha = 0.25f)
    val foldBadgeBg = palette.activePillBg
    val foldBadgeFg = palette.activePillFg

    for (docLine in visibleDocLines) {
      val topY = geom.lineTopViewportY(docLine, fold)
      val lineStart = doc.lineStart(docLine)
      val lineEnd = doc.lineEnd(docLine)
      val foldedInfo = fold.buildFoldedLineInfo(doc, docLine)

      val layout: TextLayoutResult =
        if (foldedInfo != null) {
          p.cache.layoutForFoldedLine(doc, p.styles, foldedInfo, foldBadgeFg, foldBadgeBg)
        } else {
          p.cache.layoutForLine(doc, p.styles, docLine).also {
            if (geom.wordWrap) geom.wrapModel.setRows(docLine, it.lineCount)
          }
        }
      val textLen = layout.layoutInput.text.length

      // Find/replace match highlights on this line
      if (p.findMatches.isNotEmpty() && foldedInfo == null) {
        p.findMatches.forEachIndexed { matchIdx, range ->
          val mStart = range.first
          val mEnd = range.last + 1
          if (mEnd > lineStart && mStart < lineEnd) {
            val cStart = (mStart - lineStart).coerceIn(0, textLen)
            val cEnd = (mEnd - lineStart).coerceIn(cStart, textLen)
            if (cEnd > cStart) {
              val bg = if (matchIdx == p.activeFindMatchIndex) palette.searchActiveMatchBg else palette.searchMatchBg
              drawTextRangeRects(layout, cStart, cEnd, textX, topY, lineH, bg, cornerRadiusPx = 3.dp.toPx())
            }
          }
        }
      }

      // Caret symbol occurrence highlights (lightweight, visible lines only)
      val symWord = p.caretSymbolWord
      if (!symWord.isNullOrEmpty() && sel.collapsed && foldedInfo == null) {
        val rawLine = doc.lineText(docLine)
        var pos = 0
        while (pos <= rawLine.length - symWord.length) {
          val hit = rawLine.indexOf(symWord, pos)
          if (hit < 0) break
          val beforeOk = hit == 0 || !isIdentChar(rawLine[hit - 1])
          val afterOk = hit + symWord.length >= rawLine.length || !isIdentChar(rawLine[hit + symWord.length])
          if (beforeOk && afterOk) {
            drawTextRangeRects(
              layout,
              hit.coerceIn(0, textLen),
              (hit + symWord.length).coerceIn(0, textLen),
              textX,
              topY,
              lineH,
              symBg,
              cornerRadiusPx = 3.dp.toPx(),
            )
          }
          pos = hit + symWord.length
        }
      }

      // Selection highlight rects (supports wrapped lines + full-line newline tail + crisp outline)
      if (!sel.collapsed && sel.max > lineStart && sel.min <= lineEnd) {
        val cStart = (sel.min - lineStart).coerceIn(0, textLen)
        val cEnd = (sel.max - lineStart).coerceIn(cStart, textLen)
        val includesNewline = sel.max > lineEnd
        if (cEnd > cStart) {
          drawTextRangeRects(
            layout = layout,
            startCol = cStart,
            endCol = cEnd,
            textX = textX,
            lineTopY = topY,
            lineH = lineH,
            color = selBg,
            cornerRadiusPx = 3.dp.toPx(),
            outlineColor = selOutline,
          )
        }
        if (includesNewline) {
          val lastRowIdx = (layout.lineCount - 1).coerceAtLeast(0)
          val tailX = textX + layout.getLineRight(lastRowIdx)
          drawRect(
            selBg,
            Offset(tailX, topY + lastRowIdx * lineH + 1f),
            Size(geom.charWidthPx * 0.7f, (lineH - 2f).coerceAtLeast(4f)),
          )
        }
      }

      // Matching bracket highlights
      p.bracketMatch?.let { bm ->
        if (foldedInfo == null) {
          val offsets = listOfNotNull(bm.primaryOffset, bm.partnerOffset)
          for (off in offsets) {
            if (off in lineStart until lineEnd) {
              val col = (off - lineStart).coerceIn(0, (textLen - 1).coerceAtLeast(0))
              val box = layout.getBoundingBox(col)
              val rx = textX + box.left
              val ry = topY + layout.getLineForOffset(col) * lineH
              val rw = (box.right - box.left).coerceAtLeast(geom.charWidthPx)
              val r = CornerRadius(3.dp.toPx())
              drawRoundRect(
                color = if (bm.isMismatch) mismatchFill else bracketFill,
                topLeft = Offset(rx, ry + 1f),
                size = Size(rw, lineH - 2f),
                cornerRadius = r,
              )
              drawRoundRect(
                color = if (bm.isMismatch) RoseError else bracketStroke,
                topLeft = Offset(rx, ry + 1f),
                size = Size(rw, lineH - 2f),
                cornerRadius = r,
                style = Stroke(width = 1.dp.toPx()),
              )
            }
          }
        }
      }

      // Draw cached syntax-highlighted line text
      drawText(
        textLayoutResult = layout,
        topLeft = Offset(textX, topY + (lineH - layout.size.height / layout.lineCount.coerceAtLeast(1)) * 0.5f),
      )

      // Active IME composing underline
      if (comp != null && !comp.collapsed && comp.max > lineStart && comp.min < lineEnd && foldedInfo == null) {
        val cStart = (comp.min - lineStart).coerceIn(0, textLen)
        val cEnd = (comp.max - lineStart).coerceIn(cStart, textLen)
        if (cEnd > cStart) {
          val sX = textX + layout.getHorizontalPosition(cStart, true)
          val eX = textX + layout.getHorizontalPosition(cEnd, true)
          val rowIdx = layout.getLineForOffset(cStart)
          val uy = topY + (rowIdx + 1) * lineH - 3.dp.toPx()
          drawLine(palette.activePillFg, Offset(sX, uy), Offset(eX, uy), strokeWidth = 1.5.dp.toPx())
        }
      }

      // Viewport-clipped wavy diagnostic squiggles
      val lineDiags = diagsByLine[docLine]
      if (!lineDiags.isNullOrEmpty() && foldedInfo == null) {
        for (diag in lineDiags) {
          val colStart = (diag.column - 1).coerceIn(0, textLen)
          val spanLen = diag.length.coerceAtLeast(1)
          val colEnd = (colStart + spanLen).coerceIn(colStart, textLen)
          val sX = textX + layout.getHorizontalPosition(colStart, true)
          val eX =
            if (colEnd > colStart) textX + layout.getHorizontalPosition(colEnd, true)
            else sX + geom.charWidthPx
          val rowIdx = layout.getLineForOffset(colStart)
          val squiggleY = topY + (rowIdx + 1) * lineH - 2.5.dp.toPx()
          val color = if (diag.severity == DiagnosticSeverity.ERROR) RoseError else AmberWarn
          drawWavyLine(
            startX = sX.coerceAtLeast(gutterW),
            endX = eX.coerceAtMost(size.width),
            baseY = squiggleY,
            color = color,
          )
        }
      }
    }

    // 1d. Animated gliding caret (supports Line, Block, Underline, and flickering Candle styles)
    if (p.showCaret && sel.collapsed && p.caretAlpha > 0.01f && p.dropHoverState == null) {
      val caretColor = palette.ctaBg.copy(alpha = p.caretAlpha)
      val cellW = geom.charWidthPx.coerceAtLeast(lineH * 0.52f)
      when (p.caretStyle.lowercase()) {
        EditorSettings.CARET_BLOCK -> {
          drawRoundRect(
            color = palette.ctaBg.copy(alpha = p.caretAlpha * 0.42f),
            topLeft = Offset(p.caretAnimatedX, p.caretAnimatedY + 1f),
            size = Size(cellW, (lineH - 2f).coerceAtLeast(8f)),
            cornerRadius = CornerRadius(2.dp.toPx()),
          )
        }
        EditorSettings.CARET_UNDERLINE -> {
          val h = 2.2.dp.toPx()
          drawRoundRect(
            color = caretColor,
            topLeft = Offset(p.caretAnimatedX, p.caretAnimatedY + lineH - h - 1f),
            size = Size(cellW, h),
            cornerRadius = CornerRadius(1.dp.toPx()),
          )
        }
        EditorSettings.CARET_CANDLE -> {
          drawCandleCaret(
            cx = p.caretAnimatedX,
            cy = p.caretAnimatedY,
            lineHeight = lineH,
            color = caretColor,
            t = p.flameTime,
          )
        }
        else -> {
          val caretW = 2.dp.toPx()
          drawRoundRect(
            color = caretColor,
            topLeft = Offset(p.caretAnimatedX - caretW * 0.5f, p.caretAnimatedY + 2f),
            size = Size(caretW, (lineH - 4f).coerceAtLeast(8f)),
            cornerRadius = CornerRadius(1.dp.toPx()),
          )
        }
      }
    }

    // 1e. Live Drag-and-Drop Caret Preview (high-contrast vertical bar + diamond cap + soft halo)
    p.dropHoverState?.let { drop ->
      val dropCaret = geom.caretGeometry(drop.dropDocOffset, doc, fold, p.styles, p.cache)
      val dropColor = if (drop.effectiveMode == DragTransferMode.MOVE) palette.ctaBg else EmeraldLive
      val barW = 2.5.dp.toPx()
      val cx = dropCaret.horizontalPosition.coerceIn(gutterW + 1f, size.width - 2f)
      // Soft halo behind drop bar
      drawRoundRect(
        color = dropColor.copy(alpha = 0.22f),
        topLeft = Offset(cx - 5.dp.toPx(), dropCaret.top),
        size = Size(10.dp.toPx(), lineH),
        cornerRadius = CornerRadius(4.dp.toPx()),
      )
      // Crisp drop bar
      drawRoundRect(
        color = dropColor,
        topLeft = Offset(cx - barW * 0.5f, dropCaret.top),
        size = Size(barW, lineH),
        cornerRadius = CornerRadius(1.25.dp.toPx()),
      )
      // Top diamond cap so user spots the drop column instantly
      val capR = 4.dp.toPx()
      val capY = (dropCaret.top - 1.dp.toPx()).coerceAtLeast(capR)
      val diamond =
        Path().apply {
          moveTo(cx, capY - capR)
          lineTo(cx + capR, capY)
          lineTo(cx, capY + capR)
          lineTo(cx - capR, capY)
          close()
        }
      drawPath(diamond, color = dropColor)
    }
  }

  // ---- Pass 2: Opaque sticky gutter ----
  if (geom.showLineNumbers) {
    val gutterBg = if (p.hasCustomBg) palette.editorGutterBg.copy(alpha = 0.86f) else palette.editorGutterBg
    drawRect(gutterBg, Offset.Zero, Size(gutterW, size.height))
    drawLine(
      color = palette.border.copy(alpha = 0.40f),
      start = Offset(gutterW - 0.5.dp.toPx(), 0f),
      end = Offset(gutterW - 0.5.dp.toPx(), size.height),
      strokeWidth = 0.5.dp.toPx(),
    )

    val foldZoneW = if (geom.codeFolding) 18.dp.toPx() else 4.dp.toPx()
    val numRightX = gutterW - foldZoneW - 4.dp.toPx()

    // Gutter diff change markers along right edge of gutter
    if (p.gutterDiffMarkers && p.lineChanges.isNotEmpty()) {
      val barW = 3.dp.toPx()
      val barX = gutterW - barW - 1.dp.toPx()
      for (chg in p.lineChanges) {
        val barColor =
          when (chg.kind) {
            LineChange.Kind.ADDED -> EmeraldLive
            LineChange.Kind.MODIFIED -> palette.syntaxFunction
            LineChange.Kind.DELETED -> RoseError
          }
        if (chg.kind == LineChange.Kind.DELETED) {
          val anchorLine = chg.newStart.coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
          if (!fold.isHidden(anchorLine)) {
            val y = geom.lineTopViewportY(anchorLine, fold)
            if (y in -lineH..(size.height + lineH)) {
              val tri =
                Path().apply {
                  moveTo(barX - 2.dp.toPx(), y - 3.5.dp.toPx())
                  lineTo(barX + barW + 1.dp.toPx(), y)
                  lineTo(barX - 2.dp.toPx(), y + 3.5.dp.toPx())
                  close()
                }
              drawPath(tri, barColor)
            }
          }
        } else {
          val firstL = chg.newStart.coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
          val lastL = (chg.newEnd - 1).coerceIn(firstL, (doc.lineCount - 1).coerceAtLeast(0))
          val y0 = geom.lineTopViewportY(firstL, fold)
          val y1 = geom.lineTopViewportY(lastL, fold) + geom.rowsForLine(lastL, fold).coerceAtLeast(1) * lineH
          if (y1 >= 0f && y0 <= size.height) {
            drawRoundRect(
              color = barColor,
              topLeft = Offset(barX, y0 + 1f),
              size = Size(barW, (y1 - y0 - 2f).coerceAtLeast(4f)),
              cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
          }
        }
      }
    }

    for (docLine in visibleDocLines) {
      val topY = geom.lineTopViewportY(docLine, fold)
      val isActive = docLine == activeDocLine
      val isBookmarked = docLine in p.session.bookmarks
      val lineDiags = diagsByLine[docLine]

      // Bookmark pill indicator on far left of gutter
      if (isBookmarked) {
        drawRoundRect(
          color = palette.ctaBg,
          topLeft = Offset(2.dp.toPx(), topY + 3.dp.toPx()),
          size = Size(3.5.dp.toPx(), (lineH - 6.dp.toPx()).coerceAtLeast(8f)),
          cornerRadius = CornerRadius(2.dp.toPx()),
        )
      }

      // Diagnostic severity dot
      if (!lineDiags.isNullOrEmpty()) {
        val hasErr = lineDiags.any { it.severity == DiagnosticSeverity.ERROR }
        drawCircle(
          color = if (hasErr) RoseError else AmberWarn,
          radius = 3.dp.toPx(),
          center = Offset(if (isBookmarked) 10.dp.toPx() else 7.dp.toPx(), topY + lineH * 0.5f),
        )
      }

      // Line number text (right-aligned)
      val numStr = (docLine + 1).toString()
      val style =
        p.gutterStyle.copy(
          color = if (isActive) palette.activePillFg else palette.textMuted.copy(alpha = 0.50f),
          fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        )
      val measured = p.gutterMeasurer.measure(numStr, style = style, maxLines = 1)
      drawText(
        textLayoutResult = measured,
        topLeft = Offset(
          x = (numRightX - measured.size.width).coerceAtLeast(12.dp.toPx()),
          y = topY + (lineH - measured.size.height) * 0.5f,
        ),
      )

      // Fold chevron (▾ expanded / ▸ collapsed)
      if (geom.codeFolding) {
        val isCollapsed = fold.foldStartingAt(docLine) != null
        val isFoldable = isCollapsed || foldedStarts.containsKey(docLine)
        if (isFoldable) {
          val cx = gutterW - foldZoneW * 0.55f
          val cy = topY + lineH * 0.5f
          val r = 3.2.dp.toPx()
          val chevron =
            Path().apply {
              if (isCollapsed) {
                moveTo(cx - r * 0.5f, cy - r)
                lineTo(cx + r * 0.8f, cy)
                lineTo(cx - r * 0.5f, cy + r)
              } else {
                moveTo(cx - r, cy - r * 0.45f)
                lineTo(cx, cy + r * 0.75f)
                lineTo(cx + r, cy - r * 0.45f)
              }
            }
          drawPath(
            path = chevron,
            color = if (isCollapsed) palette.activePillFg else palette.textMuted.copy(alpha = 0.75f),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        }
      }
    }
  }

  // ---- Pass 3: Touch selection teardrop handles ----
  if (p.showTouchHandles && !sel.collapsed && p.dropHoverState == null && !p.isSelfDragMoveActive) {
    val startCaret = geom.caretGeometry(sel.min, doc, fold, p.styles, p.cache)
    val endCaret = geom.caretGeometry(sel.max, doc, fold, p.styles, p.cache)
    val handleColor = selectionColorsFor(palette).handleFill
    val ringColor = palette.editorBg
    if (startCaret.horizontalPosition >= gutterW - 4f && startCaret.bottom in 0f..(size.height + 24f)) {
      drawSelectionTeardrop(Offset(startCaret.horizontalPosition, startCaret.bottom), isStart = true, color = handleColor, ringColor = ringColor)
    }
    if (endCaret.horizontalPosition >= gutterW - 4f && endCaret.bottom in 0f..(size.height + 24f)) {
      drawSelectionTeardrop(Offset(endCaret.horizontalPosition, endCaret.bottom), isStart = false, color = handleColor, ringColor = ringColor)
    }
  }

  // ---- Pass 4: Luminous Drop Zone Frame while dragging over canvas ----
  p.dropHoverState?.let { drop ->
    val frameColor = if (drop.effectiveMode == DragTransferMode.MOVE) palette.ctaBg else EmeraldLive
    val strokePx = 1.75.dp.toPx()
    drawRect(
      color = frameColor.copy(alpha = 0.55f),
      topLeft = Offset(strokePx * 0.5f, strokePx * 0.5f),
      size = Size((size.width - strokePx).coerceAtLeast(0f), (size.height - strokePx).coerceAtLeast(0f)),
      style = Stroke(width = strokePx),
    )
  }
}

private fun DrawScope.drawTextRangeRects(
  layout: TextLayoutResult,
  startCol: Int,
  endCol: Int,
  textX: Float,
  lineTopY: Float,
  lineH: Float,
  color: Color,
  cornerRadiusPx: Float,
  outlineColor: Color = Color.Transparent,
) {
  if (endCol <= startCol) return
  val startRow = layout.getLineForOffset(startCol)
  val endRow = layout.getLineForOffset((endCol - 1).coerceAtLeast(startCol))
  val cr = CornerRadius(cornerRadiusPx)
  for (row in startRow..endRow) {
    val rowStartOff = if (row == startRow) startCol else layout.getLineStart(row)
    val rowEndOff = if (row == endRow) endCol else layout.getLineEnd(row, visibleEnd = true)
    val x0 = textX + layout.getHorizontalPosition(rowStartOff, true)
    val x1 =
      if (row == endRow) textX + layout.getHorizontalPosition(rowEndOff, true)
      else textX + layout.getLineRight(row)
    val w = (x1 - x0).coerceAtLeast(3f)
    val rectTop = Offset(x0, lineTopY + row * lineH + 1f)
    val rectSize = Size(w, (lineH - 2f).coerceAtLeast(4f))
    drawRoundRect(
      color = color,
      topLeft = rectTop,
      size = rectSize,
      cornerRadius = cr,
    )
    if (outlineColor != Color.Transparent) {
      drawRoundRect(
        color = outlineColor,
        topLeft = rectTop,
        size = rectSize,
        cornerRadius = cr,
        style = Stroke(width = 0.9.dp.toPx()),
      )
    }
  }
}

private fun DrawScope.drawWavyLine(
  startX: Float,
  endX: Float,
  baseY: Float,
  color: Color,
) {
  if (endX <= startX) return
  val waveLen = 5.dp.toPx()
  val amp = 1.6.dp.toPx()
  val path = Path()
  var x = startX
  var up = true
  path.moveTo(x, baseY)
  while (x < endX) {
    val nx = (x + waveLen * 0.5f).coerceAtMost(endX)
    path.lineTo(nx, if (up) baseY - amp else baseY + amp)
    up = !up
    x = nx
  }
  drawPath(path, color = color, style = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawSelectionTeardrop(
  tip: Offset,
  isStart: Boolean,
  color: Color,
  ringColor: Color,
) {
  val r = 8.dp.toPx()
  val cx = if (isStart) tip.x - r * 0.72f else tip.x + r * 0.72f
  val cy = tip.y + r * 1.05f
  drawCircle(color = ringColor.copy(alpha = 0.85f), radius = r + 1.25.dp.toPx(), center = Offset(cx, cy))
  drawCircle(color = color, radius = r, center = Offset(cx, cy))
  val stem =
    Path().apply {
      moveTo(tip.x, tip.y - 1.dp.toPx())
      lineTo(if (isStart) tip.x - r * 1.15f else tip.x + r * 1.15f, tip.y + r * 0.9f)
      lineTo(tip.x, tip.y + r * 0.9f)
      close()
    }
  drawPath(stem, color = color)
}

private fun DrawScope.drawCandleCaret(
  cx: Float,
  cy: Float,
  lineHeight: Float,
  color: Color,
  t: Float,
) {
  val stickW = 3.dp.toPx()
  val stickTop = cy + lineHeight * 0.28f
  drawRoundRect(
    color = color,
    topLeft = Offset(cx - stickW / 2f, stickTop),
    size = Size(stickW, (cy + lineHeight - stickTop - 1f).coerceAtLeast(4f)),
    cornerRadius = CornerRadius(1.dp.toPx()),
  )
  val wickH = 1.5.dp.toPx()
  drawRect(Color(0xFF3A2A1E), Offset(cx - 0.5.dp.toPx(), stickTop - wickH), Size(1.dp.toPx(), wickH))
  val flicker = 1f + 0.09f * kotlin.math.sin(t * 11f) + 0.05f * kotlin.math.sin(t * 17.3f + 1f)
  val lean = 0.7.dp.toPx() * kotlin.math.sin(t * 7.1f)
  val base = stickTop - wickH * 0.4f
  val h = (lineHeight * 0.46f * flicker).coerceAtLeast(6f)
  val w = 4.0.dp.toPx()
  val glowCenter = Offset(cx + lean * 0.5f, base - h * 0.45f)
  drawCircle(
    brush = Brush.radialGradient(listOf(Color(0xFFFF9A2E).copy(alpha = 0.28f), Color.Transparent), glowCenter, h * 0.95f),
    radius = h * 0.95f,
    center = glowCenter,
  )
  drawPath(candleFlamePath(cx, base, w, h, lean), Color(0xFFFF8A1F))
  drawPath(candleFlamePath(cx, base, w * 0.5f, h * 0.6f, lean * 0.6f), Color(0xFFFFE27A))
}

private fun candleFlamePath(cx: Float, base: Float, w: Float, h: Float, lean: Float): Path =
  Path().apply {
    moveTo(cx, base)
    cubicTo(cx - w, base, cx - w * 0.7f, base - h * 0.55f, cx + lean, base - h)
    cubicTo(cx + w * 0.7f, base - h * 0.55f, cx + w, base, cx, base)
    close()
  }

private fun leadingIndentCols(line: String, tabSize: Int): Int {
  var cols = 0
  for (i in line.indices) {
    when (line[i]) {
      ' ' -> cols++
      '\t' -> cols += tabSize
      else -> return cols
    }
  }
  return cols
}

private fun isIdentChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'
