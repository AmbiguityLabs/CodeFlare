package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace
import com.example.editor.EdgeCodeEditorView
import com.example.idecore.DiffLineKind
import com.example.idecore.IdePerfSampler
import com.example.idecore.LocalHistoryStore
import com.example.idecore.WorkspaceEvent
import com.example.idecore.WorkspaceEventBus
import com.example.idecore.WorkspaceFileOpJournal
import com.example.idecore.completion.CompletionStatsStore
import com.example.idecore.completion.EdgeCompletionEngine
import com.example.idecore.completion.EdgeCompletionKind
import com.example.idecore.completion.EdgeMacroResolver
import com.example.idecore.completion.MemberUsageIndexer
import com.example.idecore.search.ProjectSearchOptions
import com.example.idecore.search.WorkspaceSearchAndRefactor
import com.example.ui.theme.DarkIdePalette
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IdeCoreEngineTest {

  private fun sampleWorkspace(): WorkerWorkspace {
    val indexJs =
      WorkerFile(
        path = "src/index.js",
        content =
          """
          import { computeToken } from "./auth.js";

          export default {
            async fetch(request, env, ctx) {
              const userToken = computeToken(request);
              // userToken in comment must stay untouched
              const label = "userToken";
              const cached = await env.SESSION_KV.get(userToken);
              return Response.json({ token: userToken, cached });
            }
          };
          """.trimIndent(),
        originalContent = "",
        contentType = "application/javascript+module",
        isEntryPoint = true,
      )
    val authJs =
      WorkerFile(
        path = "src/auth.js",
        content =
          """
          export function computeToken(req) {
            const userToken = req.headers.get("authorization") || "anon";
            return userToken.trim();
          }
          """.trimIndent(),
        originalContent = "",
        contentType = "application/javascript+module",
        isEntryPoint = false,
      )
    return WorkerWorkspace(
      scriptName = "edge-api",
      isEsModuleFormat = true,
      entryModulePath = "src/index.js",
      compatibilityDate = "2024-11-01",
      files = listOf(indexJs, authJs),
      bindings = listOf(CfWorkerBinding(name = "SESSION_KV", type = "kv_namespace")),
      routes = emptyList(),
      workersDevEnabled = true,
      accountSubdomain = "demo",
    )
  }

  @Test
  fun localHistoryStore_preservesBaselineDeduplicatesAndComputesDiff() {
    val dir = Files.createTempDirectory("edge_hist_test").toFile()
    try {
      val store = LocalHistoryStore(dir)
      store.recordSnapshot("ws-1", "src/index.js", before = "const a = 1;", after = "const a = 2;", label = "Edit 1")
      // Duplicate content should be skipped
      store.recordSnapshot("ws-1", "src/index.js", before = "const a = 2;", after = "const a = 2;")
      store.recordSnapshot("ws-1", "src/index.js", before = "const a = 2;", after = "const a = 3;\nconsole.log(a);", label = "Edit 2")
      store.flush()

      val snaps = store.snapshots("ws-1", "src/index.js")
      assertEquals(3, snaps.size) // Initial + Edit 1 + Edit 2
      assertEquals("Edit 2", snaps[0].label)
      assertEquals("Edit 1", snaps[1].label)
      assertEquals("Initial", snaps[2].label)

      val baseline = store.readSnapshot("ws-1", "src/index.js", snaps.last().id)
      assertEquals("const a = 1;", baseline)

      val diff = LocalHistoryStore.computeLineDiff("const a = 1;\nkeep();", "const a = 2;\nkeep();\nextra();")
      assertTrue(diff.any { it.kind == DiffLineKind.REMOVED && it.text == "const a = 1;" })
      assertTrue(diff.any { it.kind == DiffLineKind.ADDED && it.text == "const a = 2;" })
      assertTrue(diff.any { it.kind == DiffLineKind.CONTEXT && it.text == "keep();" })
      assertTrue(diff.any { it.kind == DiffLineKind.ADDED && it.text == "extra();" })
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun fileOpJournal_tracksLifoPerWorkspaceAndPopsCleanly() {
    val journal = WorkspaceFileOpJournal()
    val file = WorkerFile("src/tmp.js", "export const x = 1;", "", "application/javascript+module", false)

    journal.record(WorkspaceFileOpJournal.FileOp.Created("ws-a", "src/tmp.js"))
    journal.record(WorkspaceFileOpJournal.FileOp.Deleted("ws-b", file, wasOpenTab = true, wasEntryPoint = false))
    journal.record(WorkspaceFileOpJournal.FileOp.Renamed("ws-a", "src/tmp.js", "src/renamed.js"))

    assertEquals("Rename tmp.js", journal.latestForWorkspace("ws-a")?.op?.label)
    val firstA = journal.takeLatestForWorkspace("ws-a")
    assertTrue(firstA?.op is WorkspaceFileOpJournal.FileOp.Renamed)

    val secondA = journal.takeLatestForWorkspace("ws-a")
    assertTrue(secondA?.op is WorkspaceFileOpJournal.FileOp.Created)
    assertNull(journal.takeLatestForWorkspace("ws-a"))

    val onlyB = journal.takeLatestForWorkspace("ws-b")
    assertTrue(onlyB?.op is WorkspaceFileOpJournal.FileOp.Deleted)
  }

  @Test
  fun completionEngine_supportsPostfixTemplatesLiveMacrosAndStatsRanking() {
    val dir = Files.createTempDirectory("edge_comp_test").toFile()
    try {
      val stats = CompletionStatsStore(File(dir, "stats.properties"))
      val memberIdx = MemberUsageIndexer()
      val ws = sampleWorkspace()
      memberIdx.indexWorkspace(ws)
      val engine = EdgeCompletionEngine(stats, memberIdx)

      // 1. Postfix template with balanced function call receiver
      val postfixDoc = "async function run() {\n  const out = fetchData(req, env).aw\n}"
      val postfixCursor = postfixDoc.indexOf(".aw") + 3
      val postfixItems =
        engine.complete(ws, "src/index.js", postfixDoc, postfixCursor, WorkerLanguage.JAVASCRIPT)
      val awaitItem = postfixItems.firstOrNull { it.kind == EdgeCompletionKind.POSTFIX && it.label == ".await" }
      assertNotNull("Expected .await postfix completion", awaitItem)
      assertEquals("await fetchData(req, env)", awaitItem!!.insertText)
      assertEquals(postfixDoc.indexOf("fetchData"), awaitItem.replaceStart)
      assertEquals(postfixCursor, awaitItem.replaceEnd)

      // 2. Live template with MacroVariableResolver ($FILE$, $FUNCTION_NAME$, $END$)
      val macroDoc = "async function handleLogin(req) {\n  logf\n}"
      val macroCursor = macroDoc.indexOf("logf") + 4
      val macroItems = engine.complete(ws, "src/auth.js", macroDoc, macroCursor, WorkerLanguage.JAVASCRIPT)
      val logf = macroItems.firstOrNull { it.kind == EdgeCompletionKind.TEMPLATE && it.label == "logf" }
      assertNotNull("Expected logf live template", logf)
      assertTrue(logf!!.insertText.contains("[auth.js:handleLogin]"))
      assertTrue(logf.cursorBackOffset > 0)

      // Direct macro expansion check for $DATE$ and $UUID$
      val (expandedDate, _) = EdgeMacroResolver.expand("// \$DATE\$ \$UUID\$", "index.js", "", 0)
      assertTrue(Regex("""// \d{4}-\d{2}-\d{2} [0-9a-f-]{36}""").matches(expandedDate))

      // 3. Acceptance stats boost persists and promotes frequently accepted completion
      stats.noteAccepted("computeToken")
      stats.noteAccepted("computeToken")
      stats.noteAccepted("computeToken")
      val reloadedStats = CompletionStatsStore(File(dir, "stats.properties"))
      assertEquals(3, reloadedStats.countFor("computeToken"))
      assertTrue(memberIdx.usageCount("get") >= 2)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun searchAndRefactor_findsSymbolsMatchesAndRenamesSafelyAcrossFiles() {
    val ws = sampleWorkspace()

    // Fuzzy Go to Symbol across files + Wrangler bindings
    val symHits = WorkspaceSearchAndRefactor.searchSymbols(ws, emptyMap(), "ct")
    assertTrue(symHits.any { it.name == "computeToken" && it.filePath == "src/auth.js" })
    val bindHits = WorkspaceSearchAndRefactor.searchSymbols(ws, emptyMap(), "SESSION")
    assertTrue(bindHits.any { it.name == "env.SESSION_KV" && it.kind == "bind" })

    // Find in Files with whole word & regex
    val wordHits =
      WorkspaceSearchAndRefactor.findInFiles(
        ws,
        emptyMap(),
        "userToken",
        ProjectSearchOptions(caseSensitive = true, wholeWord = true),
      )
    assertTrue(wordHits.size >= 5)

    val regexHits =
      WorkspaceSearchAndRefactor.findInFiles(
        ws,
        emptyMap(),
        """Response\.json""",
        ProjectSearchOptions(regex = true),
      )
    assertEquals(1, regexHits.size)
    assertEquals("src/index.js", regexHits.first().filePath)

    // Lexical-safe rename skips comments and string literals
    val preview = WorkspaceSearchAndRefactor.previewRename(ws, emptyMap(), "userToken")
    assertEquals(2, preview.fileCounts.size)
    assertEquals(5, preview.totalMatches) // 3 code occurrences in index.js + 2 in auth.js (comment + string skipped!)

    val applied = WorkspaceSearchAndRefactor.applyRename(ws.files, emptyMap(), "userToken", "authJwt")
    assertEquals(5, applied.totalReplacements)
    val updatedIndex = applied.updatedContents["src/index.js"]!!
    assertTrue(updatedIndex.contains("const authJwt = computeToken(request);"))
    assertTrue(updatedIndex.contains("// userToken in comment must stay untouched"))
    assertTrue(updatedIndex.contains("""const label = "userToken";"""))
    val updatedAuth = applied.updatedContents["src/auth.js"]!!
    assertTrue(updatedAuth.contains("const authJwt ="))
    assertTrue(updatedAuth.contains("return authJwt.trim();"))
  }

  @Test
  fun editorView_lineMoveDuplicatePostfixApplyAndUsageHighlights() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val view = EdgeCodeEditorView(context)
    val initial = "const alpha = 1;\nconst beta = alpha + 2;\nreturn beta;"
    view.configureDocumentAndSettings(
      filePath = "src/index.js",
      externalTfv = TextFieldValue(initial, TextRange(6)), // on 'alpha'
      editorRevision = 1L,
      language = WorkerLanguage.JAVASCRIPT,
      palette = DarkIdePalette,
      hasCustomBg = false,
      showLineNumbers = true,
      wordWrap = false,
      autoCloseBrackets = true,
      tabSize = 2,
      fontSizeSp = 13,
      zoomPercent = 100,
      readOnly = false,
      diagnostics = emptyList(),
      findMatches = emptyList(),
      activeFindMatchIndex = -1,
    )

    // Caret symbol usage highlighting detects both 'alpha' tokens
    val usages = view.refreshUsageHighlightsNow()
    assertEquals(2, usages.size)
    assertEquals("alpha", view.currentIdentifierAtCursor())

    // Move line 1 down -> swaps line 1 and line 2
    view.moveCurrentLineDownInPlace()
    assertEquals("const beta = alpha + 2;\nconst alpha = 1;\nreturn beta;", view.text.toString())

    // Move line 2 back up -> restores original order
    view.moveCurrentLineUpInPlace()
    assertEquals(initial, view.text.toString())

    // Duplicate line 1
    view.duplicateCurrentLineInPlace()
    assertEquals("const alpha = 1;\nconst alpha = 1;\nconst beta = alpha + 2;\nreturn beta;", view.text.toString())

    // Postfix completion in-place rewrite
    view.configureDocumentAndSettings(
      filePath = "src/index.js",
      externalTfv = TextFieldValue("return payload.json", TextRange(19)),
      editorRevision = 2L,
      language = WorkerLanguage.JAVASCRIPT,
      palette = DarkIdePalette,
      hasCustomBg = false,
      showLineNumbers = true,
      wordWrap = false,
      autoCloseBrackets = true,
      tabSize = 2,
      fontSizeSp = 13,
      zoomPercent = 100,
      readOnly = false,
      diagnostics = emptyList(),
      findMatches = emptyList(),
      activeFindMatchIndex = -1,
    )
    view.applyCompletionEditInPlace(replaceStart = 7, replaceEnd = 19, insertText = "Response.json(payload)")
    assertEquals("return Response.json(payload)", view.text.toString())

    // Draw with usage highlights active to ensure zero Canvas issues
    val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
    view.measure(widthSpec, heightSpec)
    view.layout(0, 0, 1080, 1200)
    val bmp = Bitmap.createBitmap(1080, 1200, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bmp))
    bmp.recycle()
  }

  @Test
  fun perfSamplerAndEventBus_aggregateMetricsAndNotifySubscribers() {
    val sampler = IdePerfSampler(windowSize = 5)
    listOf(2L, 4L, 6L, 8L, 10L).forEach { sampler.record("completion", it) }
    val snap = sampler.snapshot().firstOrNull { it.name == "completion" }
    assertNotNull(snap)
    assertEquals(5, snap!!.count)
    assertEquals(10L, snap.maxMs)

    val bus = WorkspaceEventBus()
    val events = mutableListOf<WorkspaceEvent>()
    val unsub = bus.subscribe { events.add(it) }
    bus.publish(WorkspaceEvent.FileCreated("ws", "src/new.js"))
    unsub()
    bus.publish(WorkspaceEvent.FileDeleted("ws", "src/new.js"))
    assertEquals(1, events.size)
    assertEquals(2L, bus.generation.get())
  }
}
