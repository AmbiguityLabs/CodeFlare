package com.example.editor.engine

/**
 * One changed stretch of a file against its deployed Cloudflare baseline:
 * lines `[newStart, newEnd)` of the buffer replace lines `[oldStart, oldEnd)` of the base.
 */
data class LineChange(
  val newStart: Int,
  val newEnd: Int,
  val oldStart: Int,
  val oldEnd: Int,
) {
  enum class Kind {
    ADDED,
    MODIFIED,
    DELETED,
  }

  val kind: Kind
    get() =
      when {
        oldEnd == oldStart -> Kind.ADDED
        newEnd == newStart -> Kind.DELETED
        else -> Kind.MODIFIED
      }
}

/**
 * Myers O(ND) line diff after trimming common head and tail.
 * Falls back cleanly to a single modified stretch past [maxEdits].
 */
fun lineChanges(base: String, current: String, maxEdits: Int = 4000): List<LineChange> {
  if (base == current) return emptyList()
  val a = EditorDocument.normalizeNewlines(base).split('\n')
  val b = EditorDocument.normalizeNewlines(current).split('\n')
  var head = 0
  while (head < a.size && head < b.size && a[head] == b[head]) head++
  var tail = 0
  while (tail < a.size - head && tail < b.size - head && a[a.size - 1 - tail] == b[b.size - 1 - tail]) tail++
  val aMid = a.subList(head, a.size - tail)
  val bMid = b.subList(head, b.size - tail)
  if (aMid.isEmpty() && bMid.isEmpty()) return emptyList()
  if (aMid.isEmpty() || bMid.isEmpty()) {
    return listOf(LineChange(head, head + bMid.size, head, head + aMid.size))
  }
  val script =
    myers(aMid, bMid, maxEdits)
      ?: return listOf(LineChange(head, head + bMid.size, head, head + aMid.size))
  val out = ArrayList<LineChange>()
  var i = 0
  var j = 0
  var k = 0
  while (k < script.size) {
    if (script[k] == KEEP) {
      i++
      j++
      k++
      continue
    }
    val oldStart = i
    val newStart = j
    while (k < script.size && script[k] != KEEP) {
      if (script[k] == DELETE) i++ else j++
      k++
    }
    out += LineChange(head + newStart, head + j, head + oldStart, head + i)
  }
  return out
}

private const val KEEP: Byte = 0
private const val DELETE: Byte = 1
private const val INSERT: Byte = 2

private fun myers(a: List<String>, b: List<String>, maxEdits: Int): ByteArray? {
  val n = a.size
  val m = b.size
  val ah = IntArray(n) { a[it].hashCode() }
  val bh = IntArray(m) { b[it].hashCode() }
  fun same(x: Int, y: Int) = ah[x] == bh[y] && a[x] == b[y]
  val max = minOf(n + m, maxEdits)
  val offset = max + 1
  var v = IntArray(2 * max + 3)
  val trace = ArrayList<IntArray>()
  var found = -1
  for (d in 0..max) {
    trace += v.copyOf()
    val next = v.copyOf()
    var k = -d
    while (k <= d) {
      var x =
        if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) v[offset + k + 1]
        else v[offset + k - 1] + 1
      var y = x - k
      while (x < n && y < m && same(x, y)) {
        x++
        y++
      }
      next[offset + k] = x
      if (x >= n && y >= m) {
        found = d
        break
      }
      k += 2
    }
    v = next
    if (found >= 0) {
      trace += v
      break
    }
  }
  if (found < 0) return null
  val steps = ArrayList<Byte>(n + m)
  var x = n
  var y = m
  for (d in found downTo 1) {
    val vd = trace[d]
    val k = x - y
    val prevK =
      if (k == -d || (k != d && vd[offset + k - 1] < vd[offset + k + 1])) k + 1 else k - 1
    val prevX = vd[offset + prevK]
    val prevY = prevX - prevK
    while (x > prevX && y > prevY) {
      steps += KEEP
      x--
      y--
    }
    if (x == prevX) {
      steps += INSERT
      y--
    } else {
      steps += DELETE
      x--
    }
  }
  while (x > 0 && y > 0) {
    steps += KEEP
    x--
    y--
  }
  return ByteArray(steps.size) { steps[steps.size - 1 - it] }
}

fun baseLineFor(changes: List<LineChange>, line: Int): Int? {
  var delta = 0
  for (c in changes) {
    if (line < c.newStart) break
    if (line < c.newEnd) return null
    delta += (c.oldEnd - c.oldStart) - (c.newEnd - c.newStart)
  }
  return line + delta
}

/**
 * Returns `(startOffset, endOffset, replacement)` that reverts [change] in [doc] back to [base].
 */
fun revertEdit(doc: EditorDocument, base: String, change: LineChange): Triple<Int, Int, String> {
  val baseLines = EditorDocument.normalizeNewlines(base).split('\n')
  val safeOldStart = change.oldStart.coerceIn(0, baseLines.size)
  val safeOldEnd = change.oldEnd.coerceIn(safeOldStart, baseLines.size)
  val old = baseLines.subList(safeOldStart, safeOldEnd)
  val count = doc.lineCount
  return when {
    change.newStart >= count ->
      Triple(doc.length, doc.length, old.joinToString("") { "\n" + it })
    change.newEnd < count ->
      Triple(
        doc.lineStart(change.newStart),
        doc.lineStart(change.newEnd),
        old.joinToString("") { it + "\n" },
      )
    else ->
      Triple(doc.lineStart(change.newStart), doc.length, old.joinToString("\n"))
  }
}
