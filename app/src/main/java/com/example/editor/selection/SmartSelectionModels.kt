package com.example.editor.selection

import androidx.compose.ui.text.TextRange
import com.example.data.remote.WorkerLanguage

enum class SelectionScopeKind(val shortLabel: String) {
  TOKEN("Token"),
  MEMBER_CHAIN("Chain"),
  URL("URL"),
  FILE_PATH("Path"),
  UUID_OR_TOKEN("ID"),
  STRING_CONTENT("Text"),
  STRING_LITERAL("String"),
  KEY_VALUE_PAIR("Pair"),
  CALL_OR_BRACKET_INNER("Inner"),
  CALL_OR_BRACKET("Expr"),
  MARKUP_ELEMENT("Tag"),
  STATEMENT("Statement"),
  BLOCK("Block"),
  LINE("Line"),
  DOCUMENT("All"),
}

enum class SmartTextDomain {
  CODE_JS_TS,
  JSON,
  MARKUP_HTML_CSS,
  TERMINAL_OR_LOG,
  PLAIN_TEXT;

  companion object {
    fun from(language: WorkerLanguage, filePath: String = ""): SmartTextDomain {
      val lower = filePath.lowercase()
      if (
        lower.endsWith(".html") ||
        lower.endsWith(".htm") ||
        lower.endsWith(".xml") ||
        lower.endsWith(".svg") ||
        lower.endsWith(".css") ||
        lower.endsWith(".scss")
      ) {
        return MARKUP_HTML_CSS
      }
      return when (language) {
        WorkerLanguage.JAVASCRIPT,
        WorkerLanguage.TYPESCRIPT -> CODE_JS_TS
        WorkerLanguage.JSON -> JSON
        WorkerLanguage.HTML,
        WorkerLanguage.CSS -> MARKUP_HTML_CSS
        WorkerLanguage.MARKDOWN,
        WorkerLanguage.CONFIG,
        WorkerLanguage.TEXT -> PLAIN_TEXT
      }
    }
  }
}

enum class SmartSelectionInitialDepth {
  COMPACT,
  SMART_AUTO,
  BROAD,
}

data class SelectionScope(
  val start: Int,
  val end: Int,
  val kind: SelectionScopeKind,
  val label: String = kind.shortLabel,
) {
  val length: Int get() = (end - start).coerceAtLeast(0)

  fun toTextRange(): TextRange = TextRange(start, end)

  fun containsRange(other: SelectionScope): Boolean =
    start <= other.start && end >= other.end

  fun strictlyContains(other: SelectionScope): Boolean =
    containsRange(other) && length > other.length
}

data class SelectionLadder(
  val scopes: List<SelectionScope>,
  val initialIndex: Int,
  val anchorOffset: Int,
) {
  val isEmpty: Boolean get() = scopes.isEmpty()

  val initialScope: SelectionScope?
    get() = scopes.getOrNull(initialIndex)
}

data class SelectionLadderState(
  val ladder: SelectionLadder,
  val activeIndex: Int = ladder.initialIndex,
) {
  val currentScope: SelectionScope?
    get() = ladder.scopes.getOrNull(activeIndex)

  val canExpand: Boolean
    get() = activeIndex + 1 < ladder.scopes.size

  val canShrink: Boolean
    get() = activeIndex > 0 && ladder.scopes.isNotEmpty()

  fun expand(): SelectionLadderState =
    if (canExpand) copy(activeIndex = activeIndex + 1) else this

  fun shrink(): SelectionLadderState =
    if (canShrink) copy(activeIndex = activeIndex - 1) else this
}

interface SelectionGrammarStrategy {
  fun collectScopes(
    text: CharSequence,
    anchorOffset: Int,
    windowStart: Int,
    windowEnd: Int,
  ): List<SelectionScope>
}
