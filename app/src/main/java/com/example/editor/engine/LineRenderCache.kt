package com.example.editor.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

/**
 * CodeAssist-style per-line [TextLayoutResult] cache.
 * - Validates hits with O(1) string + style-revision comparison.
 * - Supports soft-wrap with smart continuation indent (`TextIndent(restLine = ...)`).
 * - Evicts LRU entries past [maxEntries] so memory stays flat on large files.
 * - Tracks [measuredMaxWidth] for exact horizontal scrolling without measuring off-screen lines.
 */
class LineRenderCache(
  private val textMeasurer: TextMeasurer,
  private val maxEntries: Int = 256,
) {
  private class Entry(
    val text: String,
    val styleRev: Int,
    val wrapWidthPx: Int,
    val indentPx: Float,
    val layout: TextLayoutResult,
  )

  private val cache = LinkedHashMap<Int, Entry>(maxEntries, 0.75f, true)
  private val foldCache = LinkedHashMap<Long, TextLayoutResult>(64, 0.75f, true)

  private var styleKey: TextStyle = TextStyle.Default
  private var wrapWidthPx: Int = 0
  private var tabSize: Int = 2
  private var charWidthPx: Float = 8f
  private var density: Density = Density(1f)

  var measuredMaxWidth: Float = 0f
    private set

  fun configure(
    textStyle: TextStyle,
    wrapWidthPx: Int,
    tabSize: Int,
    charWidthPx: Float,
    density: Density,
  ) {
    val safeWrap = wrapWidthPx.coerceAtLeast(0)
    if (this.styleKey != textStyle || this.wrapWidthPx != safeWrap || this.tabSize != tabSize || this.density != density) {
      this.styleKey = textStyle
      this.wrapWidthPx = safeWrap
      this.tabSize = tabSize
      this.charWidthPx = charWidthPx.coerceAtLeast(1f)
      this.density = density
      clear()
    } else {
      this.charWidthPx = charWidthPx.coerceAtLeast(1f)
    }
  }

  fun clear() {
    cache.clear()
    foldCache.clear()
    measuredMaxWidth = 0f
  }

  fun invalidateLine(line: Int) {
    cache.remove(line)
  }

  fun layoutForLine(
    doc: EditorDocument,
    styles: IncrementalLineStyles,
    line: Int,
  ): TextLayoutResult {
    val raw = doc.lineText(line)
    val styleRev = styles.lineRevision(line)
    val restIndentPx = if (wrapWidthPx > 0) computeContinuationIndentPx(raw, wrapWidthPx) else 0f

    val existing = cache[line]
    if (existing != null &&
      existing.text == raw &&
      existing.styleRev == styleRev &&
      existing.wrapWidthPx == wrapWidthPx &&
      existing.indentPx == restIndentPx
    ) {
      return existing.layout
    }

    val spans = styles.spansForLine(doc, line)
    val annotated = AnnotatedString(raw, spanStyles = spans)
    val effectiveStyle =
      if (restIndentPx > 0f) {
        styleKey.copy(textIndent = TextIndent(firstLine = TextUnit.Unspecified, restLine = with(density) { restIndentPx.toSp() }))
      } else {
        styleKey
      }

    val constraints =
      if (wrapWidthPx > 0) Constraints(maxWidth = wrapWidthPx)
      else Constraints()

    val layout =
      textMeasurer.measure(
        text = annotated,
        style = effectiveStyle,
        softWrap = wrapWidthPx > 0,
        maxLines = if (wrapWidthPx > 0) Int.MAX_VALUE else 1,
        constraints = constraints,
      )

    if (wrapWidthPx == 0) {
      val w = layout.size.width.toFloat()
      if (w > measuredMaxWidth) measuredMaxWidth = w
    }

    cache[line] = Entry(raw, styleRev, wrapWidthPx, restIndentPx, layout)
    if (cache.size > maxEntries) {
      val iter = cache.entries.iterator()
      if (iter.hasNext()) {
        iter.next()
        iter.remove()
      }
    }
    return layout
  }

  fun layoutForFoldedLine(
    doc: EditorDocument,
    styles: IncrementalLineStyles,
    info: FoldedLineInfo,
    badgeFg: Color,
    badgeBg: Color,
  ): TextLayoutResult {
    val key =
      (info.region.startLine.toLong() shl 32) xor
        (info.region.endLine.toLong() shl 16) xor
        styles.lineRevision(info.region.startLine).toLong()
    foldCache[key]?.let { return it }

    val prefixLen = info.prefixText.length
    val badgeText = " ${info.region.placeholder} "
    val combined = "${info.prefixText} $badgeText${info.suffixText}"
    val prefixSpans =
      styles.spansForLine(doc, info.region.startLine).mapNotNull { r ->
        val s = r.start.coerceAtMost(prefixLen)
        val e = r.end.coerceAtMost(prefixLen)
        if (s < e) AnnotatedString.Range(r.item, s, e) else null
      }
    val badgeStart = prefixLen + 1
    val badgeEnd = badgeStart + badgeText.length
    val allSpans =
      buildList(prefixSpans.size + 1) {
        addAll(prefixSpans)
        add(
          AnnotatedString.Range(
            SpanStyle(color = badgeFg, background = badgeBg),
            badgeStart,
            badgeEnd,
          ),
        )
      }
    val layout =
      textMeasurer.measure(
        text = AnnotatedString(combined, spanStyles = allSpans),
        style = styleKey,
        softWrap = false,
        maxLines = 1,
      )
    foldCache[key] = layout
    if (foldCache.size > 64) {
      val iter = foldCache.entries.iterator()
      if (iter.hasNext()) {
        iter.next()
        iter.remove()
      }
    }
    return layout
  }

  fun estimateWrapRows(lineLength: Int, wrapWidthPx: Int): Int {
    if (wrapWidthPx <= 0 || lineLength <= 0) return 1
    val colsPerRow = (wrapWidthPx / charWidthPx).toInt().coerceAtLeast(8)
    return ceil(lineLength.toFloat() / colsPerRow.toFloat()).toInt().coerceAtLeast(1)
  }

  private fun computeContinuationIndentPx(line: String, wrapWidthPx: Int): Float {
    var cols = 0
    for (i in line.indices) {
      when (line[i]) {
        ' ' -> cols++
        '\t' -> cols += tabSize
        else -> break
      }
    }
    if (cols == 0) return 0f
    val rawPx = (cols + tabSize) * charWidthPx
    // Cap continuation indent to 45% of wrap width so deeply indented lines still have room
    return rawPx.coerceAtMost(wrapWidthPx * 0.45f)
  }

  companion object {
    const val MEASURER_CACHE_ENTRIES = 64
  }
}
