package com.example.editor.engine

import androidx.compose.runtime.mutableStateListOf

/**
 * In-memory deduplicated 20-entry clipboard ring buffer + Binder safety cap (512 KB)
 * ported from CodeAssist's PasteHistory.
 */
object ClipboardHistoryStore {
  const val MAX_ENTRIES = 20
  const val MAX_CLIPBOARD_CHARS = 512 * 1024

  private val _entries = mutableStateListOf<String>()
  val entries: List<String> get() = _entries

  fun record(text: String) {
    val safe = clipForClipboard(text)
    if (safe.isEmpty()) return
    _entries.remove(safe)
    _entries.add(0, safe)
    while (_entries.size > MAX_ENTRIES) {
      _entries.removeAt(_entries.lastIndex)
    }
  }

  fun clear() {
    _entries.clear()
  }

  fun clipForClipboard(text: String): String =
    if (text.length <= MAX_CLIPBOARD_CHARS) text else text.substring(0, MAX_CLIPBOARD_CHARS)

  fun stripSimpleHtml(raw: String): String {
    if ('<' !in raw) return raw
    return raw
      .replace(Regex("(?i)<br\\s*/?>"), "\n")
      .replace(Regex("(?i)</p\\s*>"), "\n")
      .replace(Regex("<[^>]+>"), "")
      .replace("&nbsp;", " ")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .trim()
      .takeIf { it.isNotEmpty() } ?: raw
  }
}
