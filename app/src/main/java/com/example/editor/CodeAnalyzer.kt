package com.example.editor

import com.example.data.remote.DiagnosticItem
import com.example.data.remote.DiagnosticSeverity
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerLanguage
import org.json.JSONArray
import org.json.JSONObject

object CodeAnalyzer {
  fun analyzeFile(file: WorkerFile): List<DiagnosticItem> {
    if (file.isVirtualReadOnly) return emptyList()
    return when (file.language) {
      WorkerLanguage.JSON -> analyzeJson(file)
      WorkerLanguage.JAVASCRIPT,
      WorkerLanguage.TYPESCRIPT -> analyzeJsWorker(file)
      WorkerLanguage.TEXT -> emptyList()
    }
  }

  private fun analyzeJson(file: WorkerFile): List<DiagnosticItem> {
    val text = file.content.trim()
    if (text.isEmpty()) return emptyList()
    return try {
      if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
      emptyList()
    } catch (e: Exception) {
      listOf(
        DiagnosticItem(
          filePath = file.path,
          line = 1,
          column = 1,
          message = e.message ?: "Invalid JSON syntax",
          severity = DiagnosticSeverity.ERROR,
        )
      )
    }
  }

  private fun analyzeJsWorker(file: WorkerFile): List<DiagnosticItem> {
    val diagnostics = mutableListOf<DiagnosticItem>()
    val code = file.content

    // Bracket / brace / paren matching ignoring strings and comments
    data class OpenBracket(val ch: Char, val line: Int, val col: Int)
    val stack = ArrayDeque<OpenBracket>()
    var line = 1
    var col = 1
    var i = 0
    val len = code.length

    while (i < len) {
      val c = code[i]
      if (c == '\n') {
        line++
        col = 1
        i++
        continue
      }
      if (c == '/' && i + 1 < len && code[i + 1] == '/') {
        while (i < len && code[i] != '\n') i++
        continue
      }
      if (c == '/' && i + 1 < len && code[i + 1] == '*') {
        i += 2
        while (i + 1 < len && !(code[i] == '*' && code[i + 1] == '/')) {
          if (code[i] == '\n') {
            line++
            col = 1
          }
          i++
        }
        i = minOf(len, i + 2)
        continue
      }
      if (c == '"' || c == '\'' || c == '`') {
        val q = c
        i++
        while (i < len) {
          if (code[i] == '\n') {
            line++
            col = 1
          }
          if (code[i] == '\\' && i + 1 < len) {
            i += 2
            continue
          }
          if (code[i] == q) {
            i++
            break
          }
          i++
        }
        continue
      }

      when (c) {
        '{', '(', '[' -> stack.addLast(OpenBracket(c, line, col))
        '}', ')', ']' -> {
          val expected =
            when (c) {
              '}' -> '{'
              ')' -> '('
              else -> '['
            }
          val top = stack.lastOrNull()
          if (top == null || top.ch != expected) {
            diagnostics.add(
              DiagnosticItem(
                filePath = file.path,
                line = line,
                column = col,
                message = "Unexpected '$c'",
                severity = DiagnosticSeverity.ERROR,
              )
            )
          } else {
            stack.removeLast()
          }
        }
      }

      i++
      col++
    }

    stack.lastOrNull()?.let { unclosed ->
      diagnostics.add(
        DiagnosticItem(
          filePath = file.path,
          line = unclosed.line,
          column = unclosed.col,
          message = "Unclosed '${unclosed.ch}'",
          severity = DiagnosticSeverity.ERROR,
        )
      )
    }

    // Cloudflare Workers runtime hints
    code.lines().forEachIndexed { idx, lineText ->
      val trimmed = lineText.trim()
      if (!trimmed.startsWith("//")) {
        if (lineText.contains("process.env")) {
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = idx + 1,
              column = lineText.indexOf("process.env") + 1,
              message = "Workers isolate has no Node process.env — use env.VAR_NAME parameter",
              severity = DiagnosticSeverity.WARNING,
            )
          )
        }
        if (Regex("""\b(window|document)\.""").containsMatchIn(lineText)) {
          diagnostics.add(
            DiagnosticItem(
              filePath = file.path,
              line = idx + 1,
              column = 1,
              message = "DOM globals (window/document) are unavailable in Cloudflare Workers",
              severity = DiagnosticSeverity.WARNING,
            )
          )
        }
      }
    }

    if (
      file.isEntryPoint &&
        !code.contains("export default") &&
        !code.contains("addEventListener")
    ) {
      diagnostics.add(
        DiagnosticItem(
          filePath = file.path,
          line = 1,
          column = 1,
          message = "Entry module should export default { async fetch(request, env, ctx) { ... } }",
          severity = DiagnosticSeverity.INFO,
        )
      )
    }

    return diagnostics
  }
}
