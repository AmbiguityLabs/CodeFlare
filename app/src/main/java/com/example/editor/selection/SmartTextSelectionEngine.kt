package com.example.editor.selection

/**
 * Pure-Kotlin Multi-Domain Structural & Semantic Text Selection Engine.
 *
 * Resolves a strictly monotonically expanding [SelectionLadder] around any character offset
 * across code (JS/TS/JSON/HTML/CSS) and terminal/CLI/log outputs in sub-millisecond time
 * without native JNI/NDK dependencies.
 */
object SmartTextSelectionEngine {

  fun resolveAt(
    text: CharSequence,
    offset: Int,
    domain: SmartTextDomain,
    initialDepth: SmartSelectionInitialDepth = SmartSelectionInitialDepth.SMART_AUTO,
    customStrategy: SelectionGrammarStrategy? = null,
  ): SelectionLadder {
    if (text.isEmpty()) {
      return SelectionLadder(scopes = emptyList(), initialIndex = 0, anchorOffset = 0)
    }
    val anchor = offset.coerceIn(0, text.length)
    val (winStart, winEnd) = StructuralScanner.computeWindow(text, anchor)
    val strategy = customStrategy ?: strategyFor(domain)
    val rawCandidates = strategy.collectScopes(text, anchor, winStart, winEnd)

    val ladderScopes = buildMonotonicLadder(text, anchor, rawCandidates)
    if (ladderScopes.isEmpty()) {
      val fallbackStart = (anchor - 1).coerceAtLeast(0)
      val fallbackEnd = (fallbackStart + 1).coerceAtMost(text.length)
      val single = SelectionScope(fallbackStart, fallbackEnd, SelectionScopeKind.TOKEN, "Char")
      return SelectionLadder(listOf(single), initialIndex = 0, anchorOffset = anchor)
    }

    val initialIdx = pickInitialIndex(text, anchor, domain, initialDepth, ladderScopes)
    return SelectionLadder(
      scopes = ladderScopes,
      initialIndex = initialIdx.coerceIn(0, ladderScopes.lastIndex),
      anchorOffset = anchor,
    )
  }

  private fun strategyFor(domain: SmartTextDomain): SelectionGrammarStrategy =
    when (domain) {
      SmartTextDomain.CODE_JS_TS -> CodeStructureStrategy
      SmartTextDomain.JSON -> JsonStructureStrategy
      SmartTextDomain.MARKUP_HTML_CSS -> MarkupStructureStrategy
      SmartTextDomain.TERMINAL_OR_LOG,
      SmartTextDomain.PLAIN_TEXT -> TerminalSemanticStrategy
    }

  /**
   * Deduplicates candidates by `(start, end)` (preferring higher-specificity semantic kinds)
   * and builds a strictly nested, monotonically expanding scope ladder anchored on the
   * tightest valid range enclosing [anchor].
   */
  private fun buildMonotonicLadder(
    text: CharSequence,
    anchor: Int,
    raw: List<SelectionScope>,
  ): List<SelectionScope> {
    val len = text.length
    val bestByRange = LinkedHashMap<Long, SelectionScope>()
    for (c in raw) {
      val s = c.start.coerceIn(0, len)
      val e = c.end.coerceIn(s, len)
      if (e <= s) continue
      if (anchor !in s..e) continue
      val clamped = if (s == c.start && e == c.end) c else c.copy(start = s, end = e)
      val key = (s.toLong() shl 32) or (e.toLong() and 0xffffffffL)
      val existing = bestByRange[key]
      if (existing == null || specificityRank(clamped.kind) > specificityRank(existing.kind)) {
        bestByRange[key] = clamped
      }
    }
    if (bestByRange.isEmpty()) return emptyList()

    val sorted =
      bestByRange.values.sortedWith(
        compareBy<SelectionScope>({ it.length }, { -specificityRank(it.kind) }, { it.start }),
      )

    // Pick innermost anchor-covering seed, then greedily chain strictly containing scopes
    // so Expand(+) ALWAYS grows monotonically and Shrink(-) ALWAYS reverses cleanly.
    val ladder = ArrayList<SelectionScope>(sorted.size)
    // Prefer a semantic entity (URL/Path/UUID/Token) over overlapping non-nested peers when starting
    val seed =
      sorted.firstOrNull {
        it.kind == SelectionScopeKind.TOKEN ||
          it.kind == SelectionScopeKind.URL ||
          it.kind == SelectionScopeKind.FILE_PATH ||
          it.kind == SelectionScopeKind.UUID_OR_TOKEN
      } ?: sorted.first()

    // If a high-value semantic unit (URL, File Path, UUID) is partially split by a tiny TOKEN inside it,
    // allow starting with TOKEN -> URL -> ... since URL strictly contains TOKEN!
    ladder.add(seed)
    var current = seed
    for (candidate in sorted) {
      if (candidate.strictlyContains(current)) {
        ladder.add(candidate)
        current = candidate
      }
    }
    return ladder
  }

  private fun specificityRank(kind: SelectionScopeKind): Int =
    when (kind) {
      SelectionScopeKind.URL -> 100
      SelectionScopeKind.FILE_PATH -> 95
      SelectionScopeKind.UUID_OR_TOKEN -> 90
      SelectionScopeKind.KEY_VALUE_PAIR -> 85
      SelectionScopeKind.MARKUP_ELEMENT -> 82
      SelectionScopeKind.STRING_LITERAL -> 80
      SelectionScopeKind.STRING_CONTENT -> 75
      SelectionScopeKind.MEMBER_CHAIN -> 70
      SelectionScopeKind.CALL_OR_BRACKET -> 65
      SelectionScopeKind.CALL_OR_BRACKET_INNER -> 60
      SelectionScopeKind.STATEMENT -> 55
      SelectionScopeKind.BLOCK -> 50
      SelectionScopeKind.TOKEN -> 40
      SelectionScopeKind.LINE -> 20
      SelectionScopeKind.DOCUMENT -> 10
    }

  private fun pickInitialIndex(
    text: CharSequence,
    anchor: Int,
    domain: SmartTextDomain,
    depth: SmartSelectionInitialDepth,
    ladder: List<SelectionScope>,
  ): Int {
    if (ladder.size == 1) return 0
    return when (depth) {
      SmartSelectionInitialDepth.COMPACT -> 0
      SmartSelectionInitialDepth.BROAD -> {
        val preferred =
          setOf(
            SelectionScopeKind.STATEMENT,
            SelectionScopeKind.KEY_VALUE_PAIR,
            SelectionScopeKind.CALL_OR_BRACKET,
            SelectionScopeKind.MARKUP_ELEMENT,
            SelectionScopeKind.LINE,
          )
        ladder.indexOfFirst { it.kind in preferred }.takeIf { it >= 0 } ?: (ladder.lastIndex - 1).coerceAtLeast(0)
      }
      SmartSelectionInitialDepth.SMART_AUTO -> pickSmartAutoIndex(text, anchor, domain, ladder)
    }
  }

  private fun pickSmartAutoIndex(
    text: CharSequence,
    anchor: Int,
    domain: SmartTextDomain,
    ladder: List<SelectionScope>,
  ): Int {
    val ch = if (anchor < text.length) text[anchor] else if (anchor > 0) text[anchor - 1] else ' '

    // 1. High-value atomic entities (URL, File Path, UUID/Email/IP/Long Token) ALWAYS win first!
    val urlIdx = ladder.indexOfFirst { it.kind == SelectionScopeKind.URL }
    if (urlIdx >= 0) return urlIdx

    if (domain == SmartTextDomain.TERMINAL_OR_LOG || domain == SmartTextDomain.PLAIN_TEXT) {
      val pathIdx = ladder.indexOfFirst { it.kind == SelectionScopeKind.FILE_PATH }
      if (pathIdx >= 0) return pathIdx
      val idIdx = ladder.indexOfFirst { it.kind == SelectionScopeKind.UUID_OR_TOKEN }
      if (idIdx >= 0) return idIdx
      val jsonIdx = ladder.indexOfFirst { it.kind == SelectionScopeKind.CALL_OR_BRACKET && it.label == "JSON" }
      if (jsonIdx >= 0 && (ch == '{' || ch == '}')) return jsonIdx
      val strInnerIdx = ladder.indexOfFirst { it.kind == SelectionScopeKind.STRING_CONTENT && it.length <= 160 }
      if (strInnerIdx >= 0 && text.subSequence(ladder[strInnerIdx].start, ladder[strInnerIdx].end).none { it == '\n' }) {
        return strInnerIdx
      }
      val kvIdx = ladder.indexOfFirst { it.kind == SelectionScopeKind.KEY_VALUE_PAIR && it.label == "Flag" }
      if (kvIdx >= 0) return kvIdx
      return 0
    }

    // 2. Holding directly on a delimiter bracket grabs that bracketed expression/block immediately
    if (ch in "()[]{}<>") {
      val bracketIdx =
        ladder.indexOfFirst {
          it.kind == SelectionScopeKind.CALL_OR_BRACKET || it.kind == SelectionScopeKind.BLOCK
        }
      if (bracketIdx >= 0) return bracketIdx
    }

    // 3. Holding directly on quote grabs full string literal
    if (ch == '"' || ch == '\'' || ch == '`') {
      val litIdx = ladder.indexOfFirst { it.kind == SelectionScopeKind.STRING_LITERAL }
      if (litIdx >= 0) return litIdx
    }

    // 4. Holding directly on ':' or '=' grabs key-value pair / attribute
    if (ch == ':' || ch == '=') {
      val kvIdx = ladder.indexOfFirst { it.kind == SelectionScopeKind.KEY_VALUE_PAIR }
      if (kvIdx >= 0) return kvIdx
    }

    // 5. Holding on '.' inside a member chain grabs the full member chain (e.g. env.DB.prepare)
    if (ch == '.') {
      val chainIdx = ladder.indexOfFirst { it.kind == SelectionScopeKind.MEMBER_CHAIN }
      if (chainIdx >= 0) return chainIdx
    }

    // 6. Inside a JSON or HTML/JS single-line short string value, grab clean string content!
    val strContentIdx = ladder.indexOfFirst { it.kind == SelectionScopeKind.STRING_CONTENT }
    if (strContentIdx >= 0) {
      val sc = ladder[strContentIdx]
      val isSingleLine = text.subSequence(sc.start, sc.end).none { it == '\n' }
      if (isSingleLine && sc.length in 1..120) {
        return strContentIdx
      }
    }

    // 7. Default for identifiers/numbers in code: grab the clean Token (expandable via toolbar/progressive hold)
    return 0
  }
}
