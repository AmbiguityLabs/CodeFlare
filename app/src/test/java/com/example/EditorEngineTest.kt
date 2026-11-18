package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.DiagnosticItem
import com.example.data.remote.DiagnosticSeverity
import com.example.data.remote.WorkerLanguage
import com.example.editor.EdgeCodeEditorView
import com.example.editor.SyntaxHighlighter
import com.example.ui.theme.DarkIdePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditorEngineTest {

  @Test
  fun tokenizeVisibleRange_respectsLookbackForMultilineCommentsAndClampsBounds() {
    val code =
      """
      /* multi-line header
         comment continuing here
      */
      export default {
        async fetch(request) {
          return new Response("ok", { status: 200 });
        }
      };
      """.trimIndent()

    val emitStart = code.indexOf("comment continuing")
    val emitEnd = code.indexOf("return new Response") + 25
    val spans = mutableListOf<Triple<Int, Int, Int>>()

    SyntaxHighlighter.tokenizeVisibleRange(
      code = code,
      scanStart = 0,
      emitStart = emitStart,
      emitEnd = emitEnd,
      language = WorkerLanguage.TYPESCRIPT,
      palette = DarkIdePalette,
    ) { start, end, argb ->
      spans.add(Triple(start, end, argb))
    }

    assertTrue("Expected non-empty spans", spans.isNotEmpty())
    assertTrue("All spans must stay within [emitStart, emitEnd]", spans.all { it.first >= emitStart && it.second <= emitEnd && it.first < it.second })

    val commentColor = DarkIdePalette.syntaxComment.toArgb()
    assertTrue(
      "Multiline comment started before emitStart should still be colored inside emit window",
      spans.any { it.first == emitStart && it.third == commentColor },
    )

    val controlColor = DarkIdePalette.syntaxControl.toArgb()
    val exportIdx = code.indexOf("export")
    assertTrue(
      "Control keyword 'export' inside emit window should be highlighted",
      spans.any { it.first == exportIdx && it.second == exportIdx + 6 && it.third == controlColor },
    )

    val keywordColor = DarkIdePalette.syntaxKeyword.toArgb()
    val newIdx = code.indexOf("new")
    assertTrue(
      "Keyword 'new' inside emit window should be highlighted",
      spans.any { it.first == newIdx && it.second == newIdx + 3 && it.third == keywordColor },
    )
  }

  @Test
  fun tokenizeVisibleRange_largeWorkerFileViewportIsFast() {
    val bigWorker =
      buildString(180_000) {
        appendLine("export interface Env { KV: KVNamespace; }")
        for (i in 1..2500) {
          append("export async function handler_")
          append(i)
          appendLine("(req: Request, env: Env): Promise<Response> {")
          append("  const key = \"item_")
          append(i)
          appendLine("\"; // fast edge lookup")
          appendLine("  return new Response(JSON.stringify({ ok: true, id: $i }), { status: 200 });")
          appendLine("}")
        }
      }

    // Highlight ~60 lines in the middle of a 10,000-line file
    val midStart = bigWorker.length / 2
    val emitStart = bigWorker.indexOf('\n', midStart) + 1
    val emitEnd = (emitStart + 3_500).coerceAtMost(bigWorker.length)
    val scanStart = (emitStart - 1_500).coerceAtLeast(0)

    var tokenCount = 0
    val startNs = System.nanoTime()
    SyntaxHighlighter.tokenizeVisibleRange(
      code = bigWorker,
      scanStart = scanStart,
      emitStart = emitStart,
      emitEnd = emitEnd,
      language = WorkerLanguage.TYPESCRIPT,
      palette = DarkIdePalette,
    ) { s, e, _ ->
      assertTrue(s >= emitStart && e <= emitEnd && s < e)
      tokenCount++
    }
    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

    assertTrue("Expected rich token count in viewport slice", tokenCount > 80)
    assertTrue("Viewport tokenization should be fast (<150ms even cold on JVM), was ${elapsedMs}ms", elapsedMs < 150.0)
  }

  @Test
  fun nativeEditorView_setsCodeImeFlagsAndHandlesInPlaceEditsAndFlush() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val view = EdgeCodeEditorView(context)

    assertTrue((view.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0)
    assertTrue((view.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0)
    assertTrue((view.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0)

    val synced = mutableListOf<TextFieldValue>()
    view.onDebouncedSync = { synced.add(it) }

    view.configureDocumentAndSettings(
      filePath = "src/index.ts",
      externalTfv = TextFieldValue("const a = 1;", TextRange(12)),
      editorRevision = 1L,
      language = WorkerLanguage.TYPESCRIPT,
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

    assertEquals("const a = 1;", view.text.toString())

    // In-place snippet insertion
    view.insertSnippetInPlace("\nfunction run() {}", 1)
    assertEquals("const a = 1;\nfunction run() {}", view.text.toString())
    assertEquals(view.text.length - 1, view.selectionStart)

    // In-place line comment toggle
    view.toggleLineCommentInPlace()
    assertTrue(view.text.toString().contains("// function run() {}"))
    view.toggleLineCommentInPlace()
    assertFalse(view.text.toString().contains("// function run() {}"))

    // Synchronous flush delivers exact buffer and deduplicates repeated calls
    view.flushPendingSyncNow()
    assertEquals(1, synced.size)
    assertEquals("const a = 1;\nfunction run() {}", synced.last().text)
    view.flushPendingSyncNow()
    assertEquals(1, synced.size)
  }

  @Test
  fun nativeEditorView_smartAutoCloseSkipsDuringImeComposingAndRendersSafely() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val view = EdgeCodeEditorView(context)

    view.configureDocumentAndSettings(
      filePath = "src/worker.js",
      externalTfv = TextFieldValue("const x = ", TextRange(10)),
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
      diagnostics =
        listOf(
          DiagnosticItem(
            filePath = "src/worker.js",
            line = 1,
            column = 7,
            length = 1,
            severity = DiagnosticSeverity.WARNING,
            message = "Test diagnostic",
          ),
        ),
      findMatches = listOf(0..4),
      activeFindMatchIndex = 0,
    )

    // Typing '(' with NO active IME composing span auto-closes to '()'
    val editable = view.text!!
    editable.insert(10, "(")
    assertEquals("const x = ()", editable.toString())
    assertEquals(11, view.selectionStart)

    // When IME composing span IS active, smart edit must not fight the keyboard
    BaseInputConnection.setComposingSpans(editable)
    editable.insert(editable.length, "{")
    assertEquals("const x = (){", editable.toString())
    BaseInputConnection.removeComposingSpans(editable)

    // Measure, layout, and draw onto a software Canvas to verify zero exceptions in onDraw
    val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY)
    view.measure(widthSpec, heightSpec)
    view.layout(0, 0, 1080, 1600)
    val bmp = Bitmap.createBitmap(1080, 1600, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    view.draw(canvas)
    bmp.recycle()
  }
}
