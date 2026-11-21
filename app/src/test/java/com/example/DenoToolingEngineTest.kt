package com.example

import com.example.data.remote.DiagnosticSeverity
import com.example.editor.DenoToolingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DenoToolingEngineTest {

  @Test
  fun stripAnsiRemovesColorEscapeCodesCleanly() {
    val colored = "\u001B[0m\u001B[31merror\u001B[0m: TS2304 Cannot find name 'foo'."
    assertEquals("error: TS2304 Cannot find name 'foo'.", DenoToolingEngine.stripAnsi(colored))
  }

  @Test
  fun parseDenoLintJsonConvertsZeroBasedColumnsAndStripsRootPrefix() {
    val json =
      """
      {
        "version": 1,
        "diagnostics": [
          {
            "range": {
              "start": { "line": 4, "col": 6, "bytePos": 55 },
              "end": { "line": 4, "col": 12, "bytePos": 61 }
            },
            "filename": "file:///data/user/0/app/cache/edgecode_deno_check/src/index.ts",
            "message": "`unused` is never used",
            "code": "no-unused-vars",
            "hint": "Prefix with `_` to ignore"
          }
        ],
        "errors": []
      }
      """.trimIndent()

    val parsed =
      DenoToolingEngine.parseDenoLintJson(
        rawJson = json,
        workspaceRootPrefix = "/data/user/0/app/cache/edgecode_deno_check",
        validPaths = setOf("src/index.ts", "wrangler.jsonc"),
      )

    assertEquals(1, parsed.size)
    val diag = parsed.first()
    assertEquals("src/index.ts", diag.filePath)
    assertEquals(4, diag.line)
    assertEquals(7, diag.column)
    assertEquals(6, diag.length)
    assertEquals(DiagnosticSeverity.WARNING, diag.severity)
    assertTrue(diag.message.contains("[no-unused-vars]"))
    assertTrue(diag.message.contains("Prefix with `_`"))
  }

  @Test
  fun parseDenoCheckOutputParsesMultiLineAndInlineFormats() {
    val rawOutput =
      """
      ${"\u001B[31m"}TS2322 [ERROR]: Type 'number' is not assignable to type 'string'.
          at file:///tmp/ws/src/routes.ts:12:9${"\u001B[0m"}
      src/index.ts:3:15 - error TS2304: Cannot find name 'envBinding'.
      """.trimIndent()

    val diagnostics =
      DenoToolingEngine.parseDenoCheckOutput(
        rawText = rawOutput,
        workspaceRootPrefix = "/tmp/ws",
        validPaths = setOf("src/index.ts", "src/routes.ts"),
      )

    assertEquals(2, diagnostics.size)
    val first = diagnostics[0]
    assertEquals("src/routes.ts", first.filePath)
    assertEquals(12, first.line)
    assertEquals(9, first.column)
    assertEquals(DiagnosticSeverity.ERROR, first.severity)
    assertTrue(first.message.contains("TS2322"))

    val second = diagnostics[1]
    assertEquals("src/index.ts", second.filePath)
    assertEquals(3, second.line)
    assertEquals(15, second.column)
    assertTrue(second.message.contains("envBinding"))
  }
}
