package com.example

import com.example.data.remote.WorkerLanguage
import com.example.editor.selection.SelectionLadderState
import com.example.editor.selection.SelectionScopeKind
import com.example.editor.selection.SmartSelectionInitialDepth
import com.example.editor.selection.SmartTextDomain
import com.example.editor.selection.SmartTextSelectionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTextSelectionEngineTest {

  private fun selectedText(source: String, ladderState: SelectionLadderState): String {
    val scope = ladderState.currentScope ?: return ""
    return source.substring(scope.start, scope.end)
  }

  @Test
  fun monotonicInvariantsHoldForEveryLadder() {
    val samples =
      listOf(
        """export default { async fetch(req, env) { const url = "https://api.cloudflare.com/v4"; return Response.json({ ok: true, url }); } };""" to SmartTextDomain.CODE_JS_TS,
        """{"compatibility_date": "2024-11-01", "vars": {"API": "https://edge.dev/v1"}}""" to SmartTextDomain.JSON,
        """<button class="cta-btn" data-id="run">Deploy Now</button>""" to SmartTextDomain.MARKUP_HTML_CSS,
        """[ERR] at async fetch (src/worker.ts:42:13) payload={"ok":false,"url":"https://api.dev/a?x=1"}""" to SmartTextDomain.TERMINAL_OR_LOG,
      )

    for ((src, domain) in samples) {
      for (off in 0..src.length step 3) {
        val ladder = SmartTextSelectionEngine.resolveAt(src, off, domain)
        assertFalse("Ladder must not be empty at offset $off", ladder.isEmpty)
        for (i in 0 until ladder.scopes.lastIndex) {
          val cur = ladder.scopes[i]
          val next = ladder.scopes[i + 1]
          assertTrue(
            "Scope ${i + 1} ($next) must strictly contain scope $i ($cur) at offset $off",
            next.strictlyContains(cur),
          )
        }
        var state = SelectionLadderState(ladder)
        val initialIdx = state.activeIndex
        while (state.canExpand) {
          val prevLen = state.currentScope!!.length
          state = state.expand()
          assertTrue(state.currentScope!!.length > prevLen)
        }
        while (state.canShrink) {
          val prevLen = state.currentScope!!.length
          state = state.shrink()
          assertTrue(state.currentScope!!.length < prevLen)
        }
        assertEquals(0, state.activeIndex)
        assertTrue(initialIdx in ladder.scopes.indices)
      }
    }
  }

  @Test
  fun jsEscapedQuotesAndTemplateLiteralsWithNestedExpressions() {
    val code = """const msg = `Hello ${'$'}{user.getName("a\"b")}!`;"""
    val innerQuoteAnchor = code.indexOf("a\\\"b") + 1
    val ladder = SmartTextSelectionEngine.resolveAt(code, innerQuoteAnchor, SmartTextDomain.CODE_JS_TS)
    val texts = ladder.scopes.map { code.substring(it.start, it.end) }

    assertTrue("Should capture unescaped inner string content: $texts", texts.contains("""a\"b"""))
    assertTrue("Should capture full quoted string literal: $texts", texts.contains(""""a\"b""""))
    assertTrue("Should capture call expression: $texts", texts.contains("""user.getName("a\"b")"""))
    assertTrue("Should capture template expression: $texts", texts.contains("""${'$'}{user.getName("a\"b")}"""))
    assertTrue("Should capture full template literal: $texts", texts.contains("""`Hello ${'$'}{user.getName("a\"b")}!`"""))
    assertTrue("Should capture full statement: $texts", texts.contains(code))
  }

  @Test
  fun jsIgnoresFakeBracketsInsideStringsAndComments() {
    val code =
      """
      const s = "}"; // fake brace }
      if (ok) {
        return runTask(42);
      }
      """.trimIndent()
    val anchor = code.indexOf("runTask") + 2
    val ladder = SmartTextSelectionEngine.resolveAt(code, anchor, SmartTextDomain.CODE_JS_TS)
    val texts = ladder.scopes.map { code.substring(it.start, it.end) }

    assertEquals("runTask", selectedText(code, SelectionLadderState(ladder)))
    assertTrue("Should include call runTask(42): $texts", texts.contains("runTask(42)"))
    assertTrue("Should include statement: $texts", texts.contains("return runTask(42);"))
    assertTrue("Should include enclosing if-block cleanly: $texts", texts.any { it.startsWith("if (ok) {") && it.endsWith("}") })
  }

  @Test
  fun jsBrokenSyntaxNeverCrashesAndStillSelectsUrlOrToken() {
    val broken = """const res = await fetch("https://api.example.com/v1/items?limit=10"""
    val urlAnchor = broken.indexOf("example")
    val ladder = SmartTextSelectionEngine.resolveAt(broken, urlAnchor, SmartTextDomain.CODE_JS_TS)
    val state = SelectionLadderState(ladder)
    assertEquals("https://api.example.com/v1/items?limit=10", selectedText(broken, state))
    assertEquals(SelectionScopeKind.URL, state.currentScope?.kind)
  }

  @Test
  fun jsMemberChainSelectedWhenHoldingDotOrExpandingFromIdentifier() {
    val code = """const stmt = env.DB.prepare("SELECT 1");"""
    val dotAnchor = code.indexOf(".DB")
    val dotLadder = SmartTextSelectionEngine.resolveAt(code, dotAnchor, SmartTextDomain.CODE_JS_TS)
    assertEquals("env.DB.prepare", selectedText(code, SelectionLadderState(dotLadder)))

    val idAnchor = code.indexOf("DB")
    val idLadder = SmartTextSelectionEngine.resolveAt(code, idAnchor, SmartTextDomain.CODE_JS_TS)
    val idState = SelectionLadderState(idLadder)
    assertEquals("DB", selectedText(code, idState))
    assertEquals("env.DB.prepare", selectedText(code, idState.expand()))
  }

  @Test
  fun jsonKeyValuePairsAndNestedObjectsExpandCleanly() {
    val json =
      """
      {
        "name": "edge-worker",
        "compatibility_date": "2024-11-01",
        "vars": {
          "ENDPOINT": "https://api.cloudflare.com/client/v4"
        }
      }
      """.trimIndent()

    val dateAnchor = json.indexOf("2024")
    val ladder = SmartTextSelectionEngine.resolveAt(json, dateAnchor, SmartTextDomain.JSON)
    var state = SelectionLadderState(ladder)
    assertEquals("2024-11-01", selectedText(json, state))

    state = state.expand()
    assertEquals(""""2024-11-01"""", selectedText(json, state))

    state = state.expand()
    assertEquals(""""compatibility_date": "2024-11-01"""", selectedText(json, state))
    assertEquals(SelectionScopeKind.KEY_VALUE_PAIR, state.currentScope?.kind)

    // Holding directly on URL inside JSON picks URL automatically
    val urlAnchor = json.indexOf("cloudflare")
    val urlLadder = SmartTextSelectionEngine.resolveAt(json, urlAnchor, SmartTextDomain.JSON)
    assertEquals("https://api.cloudflare.com/client/v4", selectedText(json, SelectionLadderState(urlLadder)))
  }

  @Test
  fun markupAttributesTagsAndPairedElements() {
    val html = """<button class="btn-primary" id="deploy">Run Worker</button>"""
    val anchor = html.indexOf("btn-primary") + 4
    val ladder = SmartTextSelectionEngine.resolveAt(html, anchor, SmartTextDomain.MARKUP_HTML_CSS)
    val texts = ladder.scopes.map { html.substring(it.start, it.end) }

    assertEquals("btn-primary", selectedText(html, SelectionLadderState(ladder)))
    assertTrue("Should include quoted attribute value: $texts", texts.contains(""""btn-primary""""))
    assertTrue("Should include attribute pair: $texts", texts.contains("""class="btn-primary""""))
    assertTrue("Should include opening tag: $texts", texts.contains("""<button class="btn-primary" id="deploy">"""))
    assertTrue("Should include full paired element: $texts", texts.contains(html))
  }

  @Test
  fun terminalUrlTrimsTrailingPunctuationButKeepsBalancedParens() {
    val log1 = """[INFO] Fetching (https://api.cloudflare.com/client/v4/zones?status=active#top), done."""
    val a1 = log1.indexOf("cloudflare")
    val s1 = SelectionLadderState(SmartTextSelectionEngine.resolveAt(log1, a1, SmartTextDomain.TERMINAL_OR_LOG))
    assertEquals("https://api.cloudflare.com/client/v4/zones?status=active#top", selectedText(log1, s1))

    val log2 = """Docs: https://en.wikipedia.org/wiki/Edge_(computing). See above."""
    val a2 = log2.indexOf("wikipedia")
    val s2 = SelectionLadderState(SmartTextSelectionEngine.resolveAt(log2, a2, SmartTextDomain.TERMINAL_OR_LOG))
    assertEquals("https://en.wikipedia.org/wiki/Edge_(computing)", selectedText(log2, s2))
  }

  @Test
  fun terminalStackTraceFilePathInlineJsonHeadersUuidAndCliFlags() {
    // 1. Stack trace file:line:col
    val stack = """    at async handleRequest (src/routes/worker.ts:42:13)"""
    val pathAnchor = stack.indexOf("worker.ts")
    val pathState = SelectionLadderState(SmartTextSelectionEngine.resolveAt(stack, pathAnchor, SmartTextDomain.TERMINAL_OR_LOG))
    assertEquals("src/routes/worker.ts:42:13", selectedText(stack, pathState))
    assertEquals(SelectionScopeKind.FILE_PATH, pathState.currentScope?.kind)

    // 2. Inline JSON inside log line
    val jsonLog = """[INFO] payload={"ok":true,"items":[1,2]}"""
    val braceAnchor = jsonLog.indexOf('{')
    val jsonState = SelectionLadderState(SmartTextSelectionEngine.resolveAt(jsonLog, braceAnchor, SmartTextDomain.TERMINAL_OR_LOG))
    assertEquals("""{"ok":true,"items":[1,2]}""", selectedText(jsonLog, jsonState))
    val expandedTexts = jsonState.ladder.scopes.map { jsonLog.substring(it.start, it.end) }
    assertTrue("Should expand to payload={...}: $expandedTexts", expandedTexts.contains("""payload={"ok":true,"items":[1,2]}"""))

    // 3. HTTP Header with Bearer token
    val header = """Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig"""
    val tokAnchor = header.indexOf("eyJhb") + 5
    val tokState = SelectionLadderState(SmartTextSelectionEngine.resolveAt(header, tokAnchor, SmartTextDomain.TERMINAL_OR_LOG))
    assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig", selectedText(header, tokState))
    val hdrTexts = tokState.ladder.scopes.map { header.substring(it.start, it.end) }
    assertTrue("Should include header value: $hdrTexts", hdrTexts.contains("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig"))
    assertTrue("Should include full header pair: $hdrTexts", hdrTexts.contains(header))

    // 4. UUID
    val uuidLine = """request_id=550e8400-e29b-41d4-a716-446655440000 status=200"""
    val uuidAnchor = uuidLine.indexOf("41d4")
    val uuidState = SelectionLadderState(SmartTextSelectionEngine.resolveAt(uuidLine, uuidAnchor, SmartTextDomain.TERMINAL_OR_LOG))
    assertEquals("550e8400-e29b-41d4-a716-446655440000", selectedText(uuidLine, uuidState))

    // 5. CLI flag and command
    val cmd = """$ wrangler deploy --compatibility-date=2024-11-01"""
    val flagAnchor = cmd.indexOf("compatibility")
    val flagLadder = SmartTextSelectionEngine.resolveAt(cmd, flagAnchor, SmartTextDomain.TERMINAL_OR_LOG)
    val cmdTexts = flagLadder.scopes.map { cmd.substring(it.start, it.end) }
    assertTrue("Should include CLI flag: $cmdTexts", cmdTexts.contains("--compatibility-date=2024-11-01"))
    assertTrue("Should include CLI command: $cmdTexts", cmdTexts.contains("wrangler deploy --compatibility-date=2024-11-01"))
  }

  @Test
  fun initialDepthModesCompactSmartAutoAndBroadDifferPredictably() {
    val line = """const url = "https://edge.dev/v1";"""
    val anchor = line.indexOf("edge")
    val compact = SmartTextSelectionEngine.resolveAt(line, anchor, SmartTextDomain.CODE_JS_TS, SmartSelectionInitialDepth.COMPACT)
    val smart = SmartTextSelectionEngine.resolveAt(line, anchor, SmartTextDomain.CODE_JS_TS, SmartSelectionInitialDepth.SMART_AUTO)
    val broad = SmartTextSelectionEngine.resolveAt(line, anchor, SmartTextDomain.CODE_JS_TS, SmartSelectionInitialDepth.BROAD)

    assertNotNull(compact.initialScope)
    assertEquals("edge", line.substring(compact.initialScope!!.start, compact.initialScope!!.end))
    assertEquals("https://edge.dev/v1", line.substring(smart.initialScope!!.start, smart.initialScope!!.end))
    assertEquals(line, line.substring(broad.initialScope!!.start, broad.initialScope!!.end))
  }

  @Test
  fun domainMappingResolvesMarkupJsonCodeAndText() {
    assertEquals(SmartTextDomain.MARKUP_HTML_CSS, SmartTextDomain.from(WorkerLanguage.TEXT, "public/index.html"))
    assertEquals(SmartTextDomain.JSON, SmartTextDomain.from(WorkerLanguage.JSON, "wrangler.jsonc"))
    assertEquals(SmartTextDomain.CODE_JS_TS, SmartTextDomain.from(WorkerLanguage.TYPESCRIPT, "src/index.ts"))
    assertEquals(SmartTextDomain.PLAIN_TEXT, SmartTextDomain.from(WorkerLanguage.TEXT, "README.txt"))
  }
}
