package com.example.editor.engine

/**
 * Immutable, structurally-shared Boehm–Atkinson–Plass balanced Rope.
 * Edits rebuild only O(log N) path nodes plus at most one leaf (≤512 chars),
 * keeping keystroke cost O(log N + 512) independent of file size.
 */
sealed class Rope : CharSequence {
  abstract override val length: Int
  internal abstract val depth: Int

  abstract override fun get(index: Int): Char

  internal abstract fun appendTo(sb: StringBuilder, start: Int, end: Int)

  internal abstract fun doSub(start: Int, end: Int): Rope

  fun sub(start: Int, end: Int): Rope {
    val s = start.coerceIn(0, length)
    val e = end.coerceIn(s, length)
    if (s == 0 && e == length) return this
    if (s == e) return EMPTY
    return doSub(s, e)
  }

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = sub(startIndex, endIndex)

  fun substring(start: Int, end: Int): String {
    val s = start.coerceIn(0, length)
    val e = end.coerceIn(s, length)
    if (s == e) return ""
    return StringBuilder(e - s).also { appendTo(it, s, e) }.toString()
  }

  fun replace(start: Int, end: Int, insertion: Rope): Rope {
    val s = start.coerceIn(0, length)
    val e = end.coerceIn(s, length)
    return concat(concat(sub(0, s), insertion), sub(e, length))
  }

  fun replace(start: Int, end: Int, insertion: String): Rope = replace(start, end, of(insertion))

  final override fun toString(): String =
    if (isEmpty()) "" else StringBuilder(length).also { appendTo(it, 0, length) }.toString()

  internal val isBalanced: Boolean
    get() {
      val d = depth
      return d + 2 >= FIB.size || length >= FIB[d + 2]
    }

  companion object {
    internal const val MAX_LEAF = 512

    val EMPTY: Rope = Leaf("")

    fun of(text: String): Rope {
      if (text.isEmpty()) return EMPTY
      if (text.length <= MAX_LEAF) return Leaf(text)
      val leaves = ArrayList<Leaf>((text.length + MAX_LEAF - 1) / MAX_LEAF)
      var i = 0
      while (i < text.length) {
        val j = minOf(i + MAX_LEAF, text.length)
        leaves.add(Leaf(text.substring(i, j)))
        i = j
      }
      return buildBalanced(leaves, 0, leaves.size)
    }

    fun concat(left: Rope, right: Rope): Rope {
      if (left.length == 0) return right
      if (right.length == 0) return left
      if (left is Leaf && right is Leaf && left.length + right.length <= MAX_LEAF) {
        return Leaf(left.text + right.text)
      }
      if (left is Branch && right is Leaf) {
        val lr = left.right
        if (lr is Leaf && lr.length + right.length <= MAX_LEAF) {
          return concat(left.left, Leaf(lr.text + right.text))
        }
      }
      if (right is Branch && left is Leaf) {
        val rl = right.left
        if (rl is Leaf && rl.length + left.length <= MAX_LEAF) {
          return concat(Leaf(left.text + rl.text), right.right)
        }
      }
      val node = Branch(left, right)
      return if (node.isBalanced) node else rebalance(node)
    }

    private val FIB: LongArray =
      LongArray(91).also { f ->
        f[1] = 1
        for (i in 2 until f.size) f[i] = f[i - 1] + f[i - 2]
      }

    private fun rebalance(rope: Rope): Rope {
      val leaves = ArrayList<Leaf>()
      collectLeaves(rope, leaves)
      return if (leaves.isEmpty()) EMPTY else buildBalanced(leaves, 0, leaves.size)
    }

    private fun collectLeaves(rope: Rope, out: ArrayList<Leaf>) {
      when (rope) {
        is Leaf -> {
          if (rope.text.isEmpty()) return
          val last = if (out.isEmpty()) null else out[out.size - 1]
          if (last != null && last.text.length + rope.text.length <= MAX_LEAF) {
            out[out.size - 1] = Leaf(last.text + rope.text)
          } else {
            out.add(rope)
          }
        }
        is Branch -> {
          collectLeaves(rope.left, out)
          collectLeaves(rope.right, out)
        }
      }
    }

    private fun buildBalanced(leaves: List<Leaf>, lo: Int, hi: Int): Rope =
      when (val n = hi - lo) {
        0 -> EMPTY
        1 -> leaves[lo]
        else -> {
          val mid = lo + n / 2
          Branch(buildBalanced(leaves, lo, mid), buildBalanced(leaves, mid, hi))
        }
      }
  }
}

private class Leaf(val text: String) : Rope() {
  override val length: Int get() = text.length
  override val depth: Int get() = 0

  override fun get(index: Int): Char = text[index]

  override fun appendTo(sb: StringBuilder, start: Int, end: Int) {
    sb.append(text, start, end)
  }

  override fun doSub(start: Int, end: Int): Rope =
    if (start == 0 && end == text.length) this else Leaf(text.substring(start, end))
}

private class Branch(val left: Rope, val right: Rope) : Rope() {
  override val length: Int = left.length + right.length
  override val depth: Int = 1 + maxOf(left.depth, right.depth)

  override fun get(index: Int): Char {
    val ll = left.length
    return if (index < ll) left[index] else right[index - ll]
  }

  override fun appendTo(sb: StringBuilder, start: Int, end: Int) {
    val ll = left.length
    if (start < ll) left.appendTo(sb, start, if (end < ll) end else ll)
    if (end > ll) right.appendTo(sb, if (start > ll) start - ll else 0, end - ll)
  }

  override fun doSub(start: Int, end: Int): Rope {
    val ll = left.length
    return when {
      end <= ll -> left.sub(start, end)
      start >= ll -> right.sub(start - ll, end - ll)
      else -> Rope.concat(left.sub(start, ll), right.sub(0, end - ll))
    }
  }
}

private fun Rope.leafAt(index: Int, startOut: IntArray): String {
  var node: Rope = this
  var i = index
  var offset = 0
  while (true) {
    when (node) {
      is Leaf -> {
        startOut[0] = offset
        return node.text
      }
      is Branch -> {
        val ll = node.left.length
        if (i < ll) {
          node = node.left
        } else {
          i -= ll
          offset += ll
          node = node.right
        }
      }
    }
  }
}

fun interface CharVisitor {
  fun visit(index: Int, c: Char): Boolean
}

fun CharSequence.scanForward(start: Int, end: Int, visitor: CharVisitor): Int {
  var i = start.coerceAtLeast(0)
  val stop = end.coerceAtMost(length)
  if (this !is Rope) {
    while (i < stop) {
      if (visitor.visit(i, this[i])) return i
      i++
    }
    return -1
  }
  val leafStart = IntArray(1)
  while (i < stop) {
    val leaf = leafAt(i, leafStart)
    val base = leafStart[0]
    val leafStop = minOf(stop, base + leaf.length)
    while (i < leafStop) {
      if (visitor.visit(i, leaf[i - base])) return i
      i++
    }
  }
  return -1
}

fun CharSequence.scanBackward(from: Int, downTo: Int, visitor: CharVisitor): Int {
  var i = from.coerceAtMost(length - 1)
  val stop = downTo.coerceAtLeast(0)
  if (this !is Rope) {
    while (i >= stop) {
      if (visitor.visit(i, this[i])) return i
      i--
    }
    return -1
  }
  val leafStart = IntArray(1)
  while (i >= stop) {
    val leaf = leafAt(i, leafStart)
    val base = leafStart[0]
    val leafStop = maxOf(stop, base)
    while (i >= leafStop) {
      if (visitor.visit(i, leaf[i - base])) return i
      i--
    }
  }
  return -1
}
