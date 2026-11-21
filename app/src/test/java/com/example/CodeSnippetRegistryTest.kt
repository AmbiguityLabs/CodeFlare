package com.example

import com.example.idecore.snippets.CodeSnippetItem
import com.example.idecore.snippets.CodeSnippetRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSnippetRegistryTest {

  @Test
  fun expandSnippetHandlesEndCursorMarkerAndIndentation() {
    val doc = "async function run() {\n    "
    val expanded =
      CodeSnippetRegistry.expandSnippet(
        rawCode = "try {\n  \$END\$\n} catch (e) {}",
        fileName = "worker.ts",
        documentText = doc,
        offset = doc.length,
      )
    assertEquals("try {\n      \n    } catch (e) {}", expanded.text)
    assertEquals("\n    } catch (e) {}".length, expanded.cursorBackOffset)
  }

  @Test
  fun encodeAndDecodeCustomSnippetsRoundTripsMultilineAndControlCharsCleanly() {
    val custom =
      listOf(
        CodeSnippetItem(
          id = "c1",
          abbrev = "authgw",
          title = "Bearer Auth Guard",
          tag = "SEC",
          description = "Rejects unauthorized requests",
          code = "if (!request.headers.get(\"Authorization\")) {\n  return new Response(\"401\", { status: 401 });\n}\$END\$",
          isCustom = true,
        ),
        CodeSnippetItem(
          id = "c2",
          abbrev = "ping",
          title = "Health Ping",
          tag = "HTTP",
          description = "",
          code = "return Response.json({ pong: true });",
          isCustom = true,
        ),
      )

    val encoded = CodeSnippetRegistry.encodeCustomSnippets(custom)
    val decoded = CodeSnippetRegistry.decodeCustomSnippets(encoded)

    assertEquals(2, decoded.size)
    assertEquals("authgw", decoded[0].abbrev)
    assertEquals("Bearer Auth Guard", decoded[0].title)
    assertEquals("SEC", decoded[0].tag)
    assertEquals(custom[0].code, decoded[0].code)
    assertTrue(decoded[0].isCustom)
    assertEquals("ping", decoded[1].abbrev)
  }

  @Test
  fun allSnippetsOverridesBuiltinsByAbbrevAndFiltersByQueryAndTag() {
    val overrideFetch =
      CodeSnippetItem(
        id = "c_fetch",
        abbrev = "fetch",
        title = "Custom Hono Fetch",
        tag = "CUSTOM",
        code = "export default app;",
        isCustom = true,
      )
    val combined = CodeSnippetRegistry.allSnippets(listOf(overrideFetch))
    val fetchMatches = combined.filter { it.abbrev == "fetch" }
    assertEquals(1, fetchMatches.size)
    assertTrue(fetchMatches.first().isCustom)
    assertEquals("Custom Hono Fetch", fetchMatches.first().title)

    val kvOnly = CodeSnippetRegistry.filterSnippets(combined, query = "", tagFilter = "KV")
    assertTrue(kvOnly.isNotEmpty())
    assertTrue(kvOnly.all { it.tag == "KV" })
    assertFalse(kvOnly.any { it.abbrev == "fetch" })
  }
}
