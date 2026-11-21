package com.example

import com.example.idecore.search.DefinitionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DefinitionResolverTest {

  @Test
  fun resolvesRelativeTsModuleAndIndexDirectoryPaths() {
    val allPaths = listOf("src/index.ts", "src/utils/auth.ts", "src/routes/index.ts", "public/app.css")
    assertEquals(
      "src/utils/auth.ts",
      DefinitionResolver.resolveModulePath("src/index.ts", "./utils/auth", allPaths),
    )
    assertEquals(
      "src/routes/index.ts",
      DefinitionResolver.resolveModulePath("src/index.ts", "./routes", allPaths),
    )
    assertEquals(
      "public/app.css",
      DefinitionResolver.resolveModulePath("src/index.ts", "../public/app.css", allPaths),
    )
  }

  @Test
  fun resolvesImportedSymbolAcrossFilesAndWranglerBindings() {
    val indexCode =
      """
      import { verifyToken } from "./utils/auth";

      export default {
        async fetch(request: Request, env: Env) {
          const kvVal = await env.SESSION_KV.get("k");
          return verifyToken(request, kvVal);
        }
      };
      """.trimIndent()
    val authCode =
      """
      export interface AuthResult {
        ok: boolean;
      }

      export function verifyToken(req: Request, val: string | null): Response {
        return Response.json({ ok: Boolean(val) });
      }
      """.trimIndent()
    val wrangler =
      """
      {
        "name": "demo",
        "kv_namespaces": [
          { "binding": "SESSION_KV", "id": "abc123" }
        ]
      }
      """.trimIndent()

    val files =
      listOf(
        "src/index.ts" to indexCode,
        "src/utils/auth.ts" to authCode,
        "wrangler.jsonc" to wrangler,
      )

    val callOffset = indexCode.indexOf("verifyToken(request") + 3
    val symbolTarget = DefinitionResolver.resolveInFiles(files, "src/index.ts", indexCode, callOffset)
    assertNotNull(symbolTarget)
    assertEquals("src/utils/auth.ts", symbolTarget!!.filePath)
    assertEquals(5, symbolTarget.line)
    assertEquals("verifyToken", symbolTarget.symbolName)

    val envOffset = indexCode.indexOf("SESSION_KV") + 2
    val bindingTarget = DefinitionResolver.resolveInFiles(files, "src/index.ts", indexCode, envOffset)
    assertNotNull(bindingTarget)
    assertEquals("wrangler.jsonc", bindingTarget!!.filePath)
    assertEquals(4, bindingTarget.line)
    assertEquals("SESSION_KV", bindingTarget.symbolName)
  }

  @Test
  fun resolvesSameFileDeclarationAndHtmlHrefSpecifier() {
    val htmlCode =
      """
      <!DOCTYPE html>
      <html>
      <head>
        <link rel="stylesheet" href="./styles/main.css" />
      </head>
      </html>
      """.trimIndent()
    val files = listOf("public/index.html" to htmlCode, "public/styles/main.css" to "body { margin: 0; }")
    val hrefOffset = htmlCode.indexOf("./styles/main.css") + 4
    val target = DefinitionResolver.resolveInFiles(files, "public/index.html", htmlCode, hrefOffset)
    assertNotNull(target)
    assertEquals("public/styles/main.css", target!!.filePath)
    assertEquals(1, target.line)
  }
}
