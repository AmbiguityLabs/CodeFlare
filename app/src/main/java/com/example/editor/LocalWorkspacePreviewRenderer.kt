package com.example.editor

import android.webkit.WebResourceResponse
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import java.io.ByteArrayInputStream

object LocalWorkspacePreviewRenderer {
  const val LOCAL_PREVIEW_HOST = "local.edgecode.app"
  const val LOCAL_PREVIEW_ORIGIN = "https://local.edgecode.app"

  private val PREVIEWABLE_EXTENSIONS = setOf("html", "htm", "md", "markdown", "svg")

  fun isLocalPreviewUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return url.trim().startsWith(LOCAL_PREVIEW_ORIGIN, ignoreCase = true)
  }

  fun isPreviewableFile(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in PREVIEWABLE_EXTENSIONS
  }

  fun localUrlForPath(path: String): String {
    val clean = path.trim().trimStart('/')
    return if (clean.isBlank()) "$LOCAL_PREVIEW_ORIGIN/index.html" else "$LOCAL_PREVIEW_ORIGIN/$clean"
  }

  fun findDefaultPreviewPath(
    workspace: WorkerWorkspace?,
    activeFilePath: String? = null,
  ): String? {
    val files = workspace?.files.orEmpty().filter { !it.isVirtualReadOnly }
    if (files.isEmpty()) return null
    if (activeFilePath != null &&
      isPreviewableFile(activeFilePath) &&
      files.any { it.path == activeFilePath }
    ) {
      return activeFilePath
    }
    val preferred =
      listOf(
        "index.html",
        "www/index.html",
        "public/index.html",
        "src/index.html",
        "README.md",
        "readme.md",
      )
    preferred.forEach { candidate ->
      files.firstOrNull { it.path.equals(candidate, ignoreCase = true) }?.let { return it.path }
    }
    return files.firstOrNull { isPreviewableFile(it.path) }?.path
  }

  fun resolveWebResource(
    workspace: WorkerWorkspace?,
    requestUrl: String,
    isDarkTheme: Boolean,
  ): WebResourceResponse? {
    if (!isLocalPreviewUrl(requestUrl) || workspace == null) return null
    val rawPath =
      requestUrl
        .removePrefix(LOCAL_PREVIEW_ORIGIN)
        .substringBefore('?')
        .substringBefore('#')
        .trimStart('/')
    val file = findWorkspaceFileForRequest(workspace.files, rawPath) ?: return buildNotFoundResponse(rawPath, isDarkTheme)
    val ext = file.path.substringAfterLast('.', "").lowercase()
    return when {
      ext == "md" || ext == "markdown" -> {
        val html = renderMarkdownToHtml(file.path, file.content, isDarkTheme)
        WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
      }
      else -> {
        val mime = inferBrowserMimeType(file.path)
        WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(file.content.toByteArray(Charsets.UTF_8)))
      }
    }
  }

  fun findWorkspaceFileForRequest(
    files: List<WorkerFile>,
    rawPath: String,
  ): WorkerFile? {
    val editable = files.filter { !it.isVirtualReadOnly }
    val normalized = rawPath.trim('/')
    val candidates =
      if (normalized.isBlank()) {
        listOf("index.html", "www/index.html", "public/index.html", "README.md")
      } else {
        listOf(normalized, "$normalized.html", "$normalized/index.html")
      }
    candidates.forEach { cand ->
      editable.firstOrNull { it.path.equals(cand, ignoreCase = true) }?.let { return it }
    }
    return null
  }

  fun inferBrowserMimeType(path: String): String {
    return when (path.substringAfterLast('.', "").lowercase()) {
      "html", "htm" -> "text/html"
      "css" -> "text/css"
      "js", "mjs", "jsx" -> "application/javascript"
      "json", "jsonc" -> "application/json"
      "svg" -> "image/svg+xml"
      "xml" -> "application/xml"
      "txt", "toml", "yaml", "yml" -> "text/plain"
      else -> "text/plain"
    }
  }

  fun renderMarkdownToHtml(
    title: String,
    markdown: String,
    isDark: Boolean,
  ): String {
    val bg = if (isDark) "#0D1117" else "#FFFFFF"
    val surface = if (isDark) "#161B22" else "#F6F8FA"
    val border = if (isDark) "#30363D" else "#D0D7DE"
    val fg = if (isDark) "#E6EDF3" else "#1F2328"
    val muted = if (isDark) "#8B949E" else "#656D76"
    val accent = if (isDark) "#58A6FF" else "#0969DA"
    val bodyHtml = convertMarkdownBody(markdown)

    return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${escapeHtml(title)}</title>
<style>
  :root { color-scheme: ${if (isDark) "dark" else "light"}; }
  body {
    margin: 0;
    padding: 20px 18px 40px;
    background: $bg;
    color: $fg;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    font-size: 15px;
    line-height: 1.6;
    word-wrap: break-word;
  }
  .md-header {
    font-family: monospace;
    font-size: 12px;
    color: $muted;
    border-bottom: 1px solid $border;
    padding-bottom: 8px;
    margin-bottom: 18px;
  }
  h1, h2, h3, h4 {
    margin: 1.2em 0 0.5em;
    font-weight: 600;
    line-height: 1.25;
  }
  h1 { font-size: 1.75em; border-bottom: 1px solid $border; padding-bottom: 0.3em; }
  h2 { font-size: 1.35em; border-bottom: 1px solid $border; padding-bottom: 0.25em; }
  h3 { font-size: 1.12em; }
  a { color: $accent; text-decoration: none; }
  a:hover { text-decoration: underline; }
  code {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 0.88em;
    background: $surface;
    padding: 0.15em 0.4em;
    border-radius: 5px;
  }
  pre {
    background: $surface;
    border: 1px solid $border;
    border-radius: 8px;
    padding: 12px 14px;
    overflow-x: auto;
  }
  pre code { background: transparent; padding: 0; font-size: 12.5px; line-height: 1.5; }
  blockquote {
    margin: 0.9em 0;
    padding: 0.2em 1em;
    border-left: 3px solid $accent;
    color: $muted;
  }
  ul, ol { padding-left: 1.5em; }
  li { margin: 0.25em 0; }
  hr { border: 0; border-top: 1px solid $border; margin: 1.5em 0; }
  table { border-collapse: collapse; width: 100%; margin: 1em 0; overflow-x: auto; display: block; }
  th, td { border: 1px solid $border; padding: 6px 10px; text-align: left; }
  th { background: $surface; font-weight: 600; }
  img { max-width: 100%; border-radius: 8px; }
</style>
</head>
<body>
  <div class="md-header">${escapeHtml(title)}</div>
  $bodyHtml
</body>
</html>
    """.trimIndent()
  }

  private fun convertMarkdownBody(markdown: String): String {
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val out = StringBuilder()
    var i = 0
    var inListType: String? = null

    fun closeListIfOpen() {
      if (inListType != null) {
        out.append("</").append(inListType).append(">\n")
        inListType = null
      }
    }

    while (i < lines.size) {
      val line = lines[i]
      val trimmed = line.trim()

      if (trimmed.startsWith("```")) {
        closeListIfOpen()
        val lang = trimmed.removePrefix("```").trim()
        val codeLines = mutableListOf<String>()
        i++
        while (i < lines.size && !lines[i].trim().startsWith("```")) {
          codeLines.add(lines[i])
          i++
        }
        val langAttr = if (lang.isNotEmpty()) " class=\"language-${escapeHtml(lang)}\"" else ""
        out.append("<pre><code$langAttr>${escapeHtml(codeLines.joinToString("\n"))}</code></pre>\n")
        i++
        continue
      }

      if (trimmed.isEmpty()) {
        closeListIfOpen()
        i++
        continue
      }

      if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
        closeListIfOpen()
        out.append("<hr>\n")
        i++
        continue
      }

      val heading = Regex("""^(#{1,6})\s+(.+)$""").matchEntire(trimmed)
      if (heading != null) {
        closeListIfOpen()
        val level = heading.groupValues[1].length
        out.append("<h$level>${formatInlineMarkdown(heading.groupValues[2])}</h$level>\n")
        i++
        continue
      }

      if (trimmed.startsWith(">")) {
        closeListIfOpen()
        val quoteLines = mutableListOf<String>()
        while (i < lines.size && lines[i].trim().startsWith(">")) {
          quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
          i++
        }
        out.append("<blockquote><p>${quoteLines.joinToString("<br>") { formatInlineMarkdown(it) }}</p></blockquote>\n")
        continue
      }

      val unordered = Regex("""^[-*+]\s+(.+)$""").matchEntire(trimmed)
      if (unordered != null) {
        if (inListType != "ul") {
          closeListIfOpen()
          out.append("<ul>\n")
          inListType = "ul"
        }
        val itemText = unordered.groupValues[1]
        val rendered =
          when {
            itemText.startsWith("[x] ", ignoreCase = true) ->
              "<input type=\"checkbox\" checked disabled> ${formatInlineMarkdown(itemText.drop(4))}"
            itemText.startsWith("[ ] ") ->
              "<input type=\"checkbox\" disabled> ${formatInlineMarkdown(itemText.drop(4))}"
            else -> formatInlineMarkdown(itemText)
          }
        out.append("<li>$rendered</li>\n")
        i++
        continue
      }

      val ordered = Regex("""^\d+\.\s+(.+)$""").matchEntire(trimmed)
      if (ordered != null) {
        if (inListType != "ol") {
          closeListIfOpen()
          out.append("<ol>\n")
          inListType = "ol"
        }
        out.append("<li>${formatInlineMarkdown(ordered.groupValues[1])}</li>\n")
        i++
        continue
      }

      if (trimmed.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("""^\|[\s:|-]+\|$"""))) {
        closeListIfOpen()
        val headers = splitTableCells(trimmed)
        i += 2
        out.append("<table><thead><tr>")
        headers.forEach { h -> out.append("<th>${formatInlineMarkdown(h)}</th>") }
        out.append("</tr></thead><tbody>\n")
        while (i < lines.size && lines[i].trim().startsWith("|")) {
          val cells = splitTableCells(lines[i].trim())
          out.append("<tr>")
          cells.forEach { c -> out.append("<td>${formatInlineMarkdown(c)}</td>") }
          out.append("</tr>\n")
          i++
        }
        out.append("</tbody></table>\n")
        continue
      }

      closeListIfOpen()
      val paraLines = mutableListOf(trimmed)
      while (i + 1 < lines.size) {
        val next = lines[i + 1].trim()
        if (next.isEmpty() || next.startsWith("#") || next.startsWith("```") || next.startsWith(">") ||
          next.matches(Regex("""^[-*+]\s+.*""")) || next.matches(Regex("""^\d+\.\s+.*"""))
        ) {
          break
        }
        paraLines.add(next)
        i++
      }
      out.append("<p>${paraLines.joinToString(" ") { formatInlineMarkdown(it) }}</p>\n")
      i++
    }
    closeListIfOpen()
    return out.toString()
  }

  private fun splitTableCells(row: String): List<String> =
    row.trim().trim('|').split('|').map { it.trim() }

  private fun formatInlineMarkdown(raw: String): String {
    var text = escapeHtml(raw)
    text =
      Regex("""`([^`]+)`""").replace(text) { m ->
        "<code>${m.groupValues[1]}</code>"
      }
    text =
      Regex("""!\[([^\]]*)\]\(([^)]+)\)""").replace(text) { m ->
        "<img alt=\"${m.groupValues[1]}\" src=\"${m.groupValues[2]}\">"
      }
    text =
      Regex("""\[([^\]]+)\]\(([^)]+)\)""").replace(text) { m ->
        "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>"
      }
    text = Regex("""\*\*([^*]+)\*\*""").replace(text) { "<strong>${it.groupValues[1]}</strong>" }
    text = Regex("""__([^_]+)__""").replace(text) { "<strong>${it.groupValues[1]}</strong>" }
    text = Regex("""\*([^*]+)\*""").replace(text) { "<em>${it.groupValues[1]}</em>" }
    text = Regex("""~~([^~]+)~~""").replace(text) { "<del>${it.groupValues[1]}</del>" }
    return text
  }

  private fun buildNotFoundResponse(path: String, isDark: Boolean): WebResourceResponse {
    val bg = if (isDark) "#0D1117" else "#FFFFFF"
    val fg = if (isDark) "#E6EDF3" else "#1F2328"
    val html =
      """
      <!DOCTYPE html>
      <html><body style="background:$bg;color:$fg;font-family:sans-serif;padding:24px;">
      <h3>Local file not found in workspace</h3>
      <p><code>${escapeHtml(path)}</code></p>
      </body></html>
      """.trimIndent()
    return WebResourceResponse("text/html", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
  }

  private fun escapeHtml(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
}
