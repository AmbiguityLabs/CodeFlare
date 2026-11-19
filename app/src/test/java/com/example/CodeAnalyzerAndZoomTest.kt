package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.DiagnosticSeverity
import com.example.data.remote.WorkerFile
import com.example.data.security.AuthPreferencesStore
import com.example.editor.CodeAnalyzer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CodeAnalyzerAndZoomTest {

  private fun jsFile(content: String, path: String = "src/index.js", isEntry: Boolean = true) =
    WorkerFile(
      path = path,
      content = content,
      originalContent = content,
      contentType = "application/javascript+module",
      isEntryPoint = isEntry,
    )

  private fun jsonFile(content: String, path: String = "package.json") =
    WorkerFile(
      path = path,
      content = content,
      originalContent = content,
      contentType = "application/json",
      isEntryPoint = false,
    )

  @Test
  fun catchesUnterminatedStringAndBracketMismatchWithExactLines() {
    val code =
      """
      export default {
        async fetch(request) {
          const msg = "unclosed
          return new Response(msg);
        }
      """.trimIndent()
    val diags = CodeAnalyzer.analyzeFile(jsFile(code))
    assertTrue(diags.any { it.severity == DiagnosticSeverity.ERROR && it.line == 3 && it.message.contains("Unterminated string") })
    assertTrue(diags.any { it.severity == DiagnosticSeverity.ERROR && it.line == 1 && it.message.contains("Unclosed '{'") })
  }

  @Test
  fun handlesTemplateInterpolationsAndRegexLiteralsWithoutFalsePositives() {
    val code =
      """
      export default {
        async fetch(request) {
          const re = /foo\/[{}]()bar/gi;
          const label = `path=${'$'}{new URL(request.url).pathname}`;
          return Response.json({ ok: re.test(label), label });
        }
      };
      """.trimIndent()
    val diags = CodeAnalyzer.analyzeFile(jsFile(code))
    assertTrue("Expected no errors, got: $diags", diags.none { it.severity == DiagnosticSeverity.ERROR })
  }

  @Test
  fun catchesJsSemanticAndStatementErrors() {
    val code =
      """
      export default {
        async fetch(request) {
          if () { return new Response("bad"); }
          const missingInit;
          return new Response("ok");
        }
      };
      export default {};
      function helper() {
        return await fetch("https://example.com");
      }
      """.trimIndent()
    val diags = CodeAnalyzer.analyzeFile(jsFile(code))
    assertTrue(diags.any { it.line == 3 && it.message.contains("Expected expression inside 'if ()'") })
    assertTrue(diags.any { it.line == 4 && it.message.contains("must be initialized") })
    assertTrue(diags.any { it.line == 8 && it.message.contains("Duplicate 'export default'") })
    assertTrue(diags.any { it.line == 10 && it.message.contains("only valid inside an 'async' function") })
  }

  @Test
  fun acceptsValidEsmImportsIncludingNodeBuiltinsWithoutFalsePositive() {
    val validCode =
      """
      import { EventEmitter } from "node:events";
      import { Socket } from "node:net";
      import type { Request } from '@cloudflare/workers-types';
      export default {
        async fetch() {
          return new Response("ok");
        }
      };
      """.trimIndent()
    val validDiags = CodeAnalyzer.analyzeFile(jsFile(validCode))
    assertTrue("Expected no errors for valid imports, got: $validDiags", validDiags.none { it.severity == DiagnosticSeverity.ERROR })

    val brokenImport =
      """
      import { Socket } from ;
      export default { async fetch() { return new Response("ok"); } };
      """.trimIndent()
    val brokenDiags = CodeAnalyzer.analyzeFile(jsFile(brokenImport))
    assertTrue(brokenDiags.any { it.line == 1 && it.message.contains("Expected module specifier") })
  }

  @Test
  fun differentiatesStrictJsonFromJsoncAndPinpointsLine() {
    val jsonc =
      """
      {
        // wrangler comment
        "name": "edge-worker",
        "compatibility_date": "2024-11-01",
      }
      """.trimIndent()
    assertTrue(CodeAnalyzer.analyzeFile(jsonFile(jsonc, "wrangler.jsonc")).isEmpty())

    val strictJsonWithComment = CodeAnalyzer.analyzeFile(jsonFile(jsonc, "package.json"))
    assertTrue(strictJsonWithComment.any { it.severity == DiagnosticSeverity.ERROR && it.line == 2 })

    val brokenJson =
      """
      {
        "name": "app",
        "version" "1.0.0"
      }
      """.trimIndent()
    val brokenDiags = CodeAnalyzer.analyzeFile(jsonFile(brokenJson, "package.json"))
    assertTrue(brokenDiags.any { it.severity == DiagnosticSeverity.ERROR && it.line == 3 })
  }

  @Test
  fun findsMatchingAndMismatchedBracketsAtCursor() {
    val code = "function a() { return [1, 2]; }"
    val file = jsFile(code)
    val match = CodeAnalyzer.findMatchingBracket(code, code.indexOf('{'), file.language)
    assertNotNull(match)
    assertFalse(match!!.isMismatch)
    assertEquals(code.lastIndexOf('}'), match.partnerOffset)

    val broken = "function a() { return [1, 2; }"
    val mismatch = CodeAnalyzer.findMatchingBracket(broken, broken.indexOf('['), file.language)
    assertNotNull(mismatch)
    assertTrue(mismatch!!.isMismatch)
  }

  @Test
  fun persistsAndClampsEditorZoomPercent() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    store.updateEditorSettings { it.copy(zoomPercent = 150) }
    assertEquals(150, store.editorSettingsFlow.first().zoomPercent)

    store.updateEditorSettings { it.copy(zoomPercent = 500) }
    assertEquals(220, store.editorSettingsFlow.first().zoomPercent)

    store.updateEditorSettings { it.copy(zoomPercent = 10) }
    assertEquals(60, store.editorSettingsFlow.first().zoomPercent)
  }
}
