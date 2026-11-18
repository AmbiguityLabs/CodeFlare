package com.example.editor.engine

/**
 * Variable-height vertical layout for soft-wrap: maps document lines <-> visual rows using a
 * fold-aware Fenwick tree (BIT) so per-frame line-height updates are O(log N) instead of O(N).
 */
class WrapModel {
  private var rows = IntArray(0)
  private var contrib = IntArray(0)
  private var tree = IntArray(1)
  var totalRows: Int = 0
    private set
  private var built = false
  private var builtFold: FoldModel? = null

  fun resize(lineCount: Int) {
    if (rows.size == lineCount) return
    val next = IntArray(lineCount) { 1 }
    for (i in 0 until minOf(lineCount, rows.size)) next[i] = rows[i]
    rows = next
    built = false
  }

  fun setRows(line: Int, count: Int) {
    if (line !in rows.indices) return
    val r = if (count < 1) 1 else count
    if (rows[line] == r) return
    rows[line] = r
    if (!built) return
    val f = builtFold ?: return
    val newC = contribOf(line, f)
    val old = contrib[line]
    if (newC != old) {
      bitAdd(line, newC - old)
      contrib[line] = newC
      totalRows += newC - old
    }
  }

  fun rawRows(line: Int): Int = if (line in rows.indices) rows[line] else 1

  fun ensure(fold: FoldModel) {
    if (built) {
      val prev = builtFold
      if (prev === fold) return
      if (prev != null && prev.sameProjection(fold)) {
        builtFold = fold
        return
      }
    }
    val n = rows.size
    if (contrib.size != n) contrib = IntArray(n)
    if (tree.size != n + 1) tree = IntArray(n + 1) else tree.fill(0)
    var acc = 0
    for (i in 0 until n) {
      val c = contribOf(i, fold)
      contrib[i] = c
      tree[i + 1] = c
      acc += c
    }
    var i = 1
    while (i <= n) {
      val parent = i + (i and -i)
      if (parent <= n) tree[parent] += tree[i]
      i++
    }
    totalRows = acc
    built = true
    builtFold = fold
  }

  fun topRow(line: Int): Int {
    val n = rows.size
    if (n == 0) return 0
    return bitPrefix(line.coerceIn(0, n))
  }

  fun rowsOf(line: Int): Int {
    if (!built || line < 0 || line >= rows.size) return rawRows(line)
    return contrib[line]
  }

  fun docLineForRow(row: Int): Int {
    val n = rows.size
    if (n <= 0) return 0
    val r = row.coerceIn(0, (totalRows - 1).coerceAtLeast(0))
    var lo = 0
    var hi = n - 1
    while (lo < hi) {
      val mid = (lo + hi + 1) ushr 1
      if (bitPrefix(mid) <= r) lo = mid else hi = mid - 1
    }
    return lo
  }

  private fun contribOf(line: Int, fold: FoldModel): Int =
    when {
      fold.isHidden(line) -> 0
      fold.foldStartingAt(line) != null -> 1
      else -> rows[line]
    }

  private fun bitAdd(line: Int, delta: Int) {
    var i = line + 1
    val n = tree.size - 1
    while (i <= n) {
      tree[i] += delta
      i += i and -i
    }
  }

  private fun bitPrefix(len: Int): Int {
    var i = len
    var s = 0
    while (i > 0) {
      s += tree[i]
      i -= i and -i
    }
    return s
  }
}
