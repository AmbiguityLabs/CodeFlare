package com.example.editor

import com.example.data.remote.DiffLine
import com.example.data.remote.DiffType
import com.example.data.remote.WorkerLanguage
import org.json.JSONArray
import org.json.JSONObject

object CodeFormatter {
  fun format(code: String, language: WorkerLanguage, tabSize: Int = 2): String {
    if (language == WorkerLanguage.JSON) {
      val trimmed = code.trim()
      return runCatching {
          if (trimmed.startsWith("[")) JSONArray(trimmed).toString(tabSize)
          else JSONObject(trimmed).toString(tabSize)
        }
        .getOrDefault(code)
    }
    if (language != WorkerLanguage.JAVASCRIPT && language != WorkerLanguage.TYPESCRIPT) {
      return code
    }

    val indentUnit = " ".repeat(tabSize)
    var indent = 0
    val out = StringBuilder()
    val lines = code.lines()

    lines.forEachIndexed { idx, rawLine ->
      val trimmed = rawLine.trim()
      if (trimmed.isEmpty()) {
        if (idx < lines.lastIndex) out.append('\n')
        return@forEachIndexed
      }

      if (trimmed.startsWith("}") || trimmed.startsWith("]") || trimmed.startsWith(")")) {
        indent = (indent - 1).coerceAtLeast(0)
      }

      out.append(indentUnit.repeat(indent)).append(trimmed)
      if (idx < lines.lastIndex) out.append('\n')

      val opens = countCharOutsideStrings(trimmed, '{') + countCharOutsideStrings(trimmed, '[')
      val closes = countCharOutsideStrings(trimmed, '}') + countCharOutsideStrings(trimmed, ']')
      val delta = opens - closes
      if (delta > 0) {
        indent += delta
      } else if (delta < 0 && !trimmed.startsWith("}") && !trimmed.startsWith("]")) {
        indent = (indent + delta).coerceAtLeast(0)
      }
    }
    return out.toString()
  }

  private fun countCharOutsideStrings(line: String, target: Char): Int {
    var count = 0
    var inQuote: Char? = null
    var i = 0
    while (i < line.length) {
      val c = line[i]
      if (inQuote != null) {
        if (c == '\\') {
          i += 2
          continue
        }
        if (c == inQuote) inQuote = null
      } else {
        if (c == '/' && i + 1 < line.length && line[i + 1] == '/') break
        if (c == '"' || c == '\'' || c == '`') {
          inQuote = c
        } else if (c == target) {
          count++
        }
      }
      i++
    }
    return count
  }

  fun computeLineDiff(original: String, current: String): List<DiffLine> {
    val a = original.lines()
    val b = current.lines()
    val n = a.size
    val m = b.size
    // Standard LCS for compact Worker files
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
      for (j in m - 1 downTo 0) {
        dp[i][j] =
          if (a[i] == b[j]) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
      }
    }

    val result = mutableListOf<DiffLine>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
      when {
        a[i] == b[j] -> {
          result.add(DiffLine(DiffType.UNCHANGED, i + 1, j + 1, a[i]))
          i++
          j++
        }
        dp[i + 1][j] >= dp[i][j + 1] -> {
          result.add(DiffLine(DiffType.REMOVED, i + 1, null, a[i]))
          i++
        }
        else -> {
          result.add(DiffLine(DiffType.ADDED, null, j + 1, b[j]))
          j++
        }
      }
    }
    while (i < n) {
      result.add(DiffLine(DiffType.REMOVED, i + 1, null, a[i]))
      i++
    }
    while (j < m) {
      result.add(DiffLine(DiffType.ADDED, null, j + 1, b[j]))
      j++
    }
    return result
  }
}
