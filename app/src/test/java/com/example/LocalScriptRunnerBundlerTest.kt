package com.example

import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace
import com.example.editor.LocalModuleBundler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalScriptRunnerBundlerTest {

  @Test
  fun resolveTargetEntryPathPrefersActiveExecutableOrHtmlFile() {
    val ws =
      WorkerWorkspace(
        scriptName = "demo-worker",
        entryModulePath = "src/index.js",
        files =
          listOf(
            WorkerFile(
              path = "src/index.js",
              content = "export default { fetch() { return new Response('ok'); } };",
              language = WorkerLanguage.JAVASCRIPT,
              isEntryPoint = true,
            ),
            WorkerFile(
              path = "src/helper.ts",
              content = "export const version = 2;",
              language = WorkerLanguage.TYPESCRIPT,
            ),
            WorkerFile(
              path = "public/index.html",
              content = "<h1>Hi</h1>",
              language = WorkerLanguage.TEXT,
            ),
            WorkerFile(
              path = "scripts/bench.js",
              content = "console.log('local script');",
              language = WorkerLanguage.JAVASCRIPT,
            ),
          ),
      )

    // Non-executable helper falls back to workspace entry module
    assertEquals("src/index.js", LocalModuleBundler.resolveTargetEntryPath(ws, "src/helper.ts"))
    // Previewable HTML resolves directly
    assertEquals("public/index.html", LocalModuleBundler.resolveTargetEntryPath(ws, "public/index.html"))
    // Standalone script with console.log resolves directly
    assertEquals("scripts/bench.js", LocalModuleBundler.resolveTargetEntryPath(ws, "scripts/bench.js"))
  }

  @Test
  fun stripBasicTypeScriptRemovesInterfacesTypesAndAnnotations() {
    val ts =
      """
      import type { Env } from './types';
      export interface Config {
        mode: string;
      }
      type Flags = Record<string, string>;
      export default {
        async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
          const id = 'edge' as const;
          return new Response(id);
        }
      };
      """.trimIndent()

    val stripped = LocalModuleBundler.stripBasicTypeScript(ts, "src/worker.ts")
    assertFalse(stripped.contains("import type"))
    assertFalse(stripped.contains("interface Config"))
    assertFalse(stripped.contains("type Flags"))
    assertFalse(stripped.contains(": Promise<Response>"))
    assertFalse(stripped.contains("as const"))
    assertTrue(stripped.contains("async fetch(request, env, ctx)"))
  }

  @Test
  fun transpileEsmToCjsRewritesImportsAndExports() {
    val esm =
      """
      import helper, { greet as sayHi } from './helper';
      import * as utils from './utils';
      export const answer = 42;
      export function runTask() {
        return sayHi(helper) + utils.tag;
      }
      export default {
        async fetch(req) {
          return new Response(String(answer));
        }
      };
      """.trimIndent()

    val cjs = LocalModuleBundler.transpileEsmToCjs(esm, "src/index.js")
    assertTrue(cjs.contains("__require(\"./helper\")"))
    assertTrue(cjs.contains("const utils = __require(\"./utils\")"))
    assertTrue(cjs.contains("exports.default ="))
    assertTrue(cjs.contains("exports.answer = answer"))
    assertTrue(cjs.contains("exports.runTask = runTask"))
  }

  @Test
  fun buildModuleMapJsonExcludesVirtualFilesAndTransformsJsonAndModules() {
    val files =
      listOf(
        WorkerFile(
          path = "src/index.ts",
          content = "import cfg from './cfg.json'; export default { fetch() { return Response.json(cfg); } };",
          language = WorkerLanguage.TYPESCRIPT,
          isEntryPoint = true,
        ),
        WorkerFile(
          path = "src/cfg.json",
          content = """{"ok":true}""",
          language = WorkerLanguage.JSON,
        ),
        WorkerFile(
          path = ".edgecode/bindings.json",
          content = "{}",
          language = WorkerLanguage.JSON,
          isVirtualReadOnly = true,
        ),
      )

    val map = LocalModuleBundler.buildModuleMapJson(files)
    assertTrue(map.has("src/index.ts"))
    assertTrue(map.has("src/cfg.json"))
    assertFalse(map.has(".edgecode/bindings.json"))
    assertTrue(map.getString("src/cfg.json").contains("exports.default = {\"ok\":true}"))
  }
}
