package com.example

import com.example.data.remote.WorkerFile
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
        isEsModuleFormat = true,
        entryModulePath = "src/index.js",
        compatibilityDate = "2024-11-01",
        files =
          listOf(
            WorkerFile(
              path = "src/index.js",
              content = "export default { fetch() { return new Response('ok'); } };",
              originalContent = "export default { fetch() { return new Response('ok'); } };",
              contentType = "application/javascript+module",
              isEntryPoint = true,
            ),
            WorkerFile(
              path = "src/helper.ts",
              content = "export const version = 2;",
              originalContent = "export const version = 2;",
              contentType = "application/javascript+module",
              isEntryPoint = false,
            ),
            WorkerFile(
              path = "public/index.html",
              content = "<h1>Hi</h1>",
              originalContent = "<h1>Hi</h1>",
              contentType = "text/html",
              isEntryPoint = false,
            ),
            WorkerFile(
              path = "scripts/bench.js",
              content = "console.log('local script');",
              originalContent = "console.log('local script');",
              contentType = "application/javascript+module",
              isEntryPoint = false,
            ),
          ),
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = true,
        accountSubdomain = "demo",
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
          originalContent = "import cfg from './cfg.json'; export default { fetch() { return Response.json(cfg); } };",
          contentType = "application/javascript+module",
          isEntryPoint = true,
        ),
        WorkerFile(
          path = "src/cfg.json",
          content = """{"ok":true}""",
          originalContent = """{"ok":true}""",
          contentType = "application/json",
          isEntryPoint = false,
        ),
        WorkerFile(
          path = ".edgecode/bindings.json",
          content = "{}",
          originalContent = "{}",
          contentType = "application/json",
          isEntryPoint = false,
          isVirtualReadOnly = true,
        ),
      )

    val map = LocalModuleBundler.buildModuleMapJson(files)
    assertTrue(map.has("src/index.ts"))
    assertTrue(map.has("src/cfg.json"))
    assertFalse(map.has(".edgecode/bindings.json"))
    assertTrue(map.getString("src/cfg.json").contains("exports.default = {\"ok\":true}"))
  }

  @Test
  fun transpileEsmToCjsHandlesNodeImportsAndReExports() {
    val src =
      """
      import { Readable, Transform } from 'node:stream';
      import { type ExecutionContext } from '@cloudflare/workers-types';
      export { Readable as StreamReader } from 'node:stream';
      export * from './shared';
      export default {
        fetch() { return new Response(typeof Readable); }
      };
      """.trimIndent()

    val cjs = LocalModuleBundler.transpileEsmToCjs(src, "src/index.ts")
    assertTrue(cjs.contains("""__require("node:stream")"""))
    assertTrue(cjs.contains("exports.StreamReader = __reexp.Readable;"))
    assertTrue(cjs.contains("""Object.assign(exports, __require("./shared"));"""))
  }

  @Test
  fun buildHarnessJsIncludesNodeStreamAndCloudflareBuiltins() {
    val harness = com.example.editor.LocalScriptRunner.buildHarnessJs("""{"entryPath":"index.js","modules":{},"bindings":[],"request":{"url":"https://local.edgecode.app/","method":"GET","headers":{},"body":""}}""")
    assertTrue(harness.contains("register('stream', streamMod)"))
    assertTrue(harness.contains("register('stream/web', streamWebMod)"))
    assertTrue(harness.contains("register('stream/consumers', streamConsumersMod)"))
    assertTrue(harness.contains("register('buffer'"))
    assertTrue(harness.contains("register('crypto', cryptoMod)"))
    assertTrue(harness.contains("__builtins['cloudflare:workers']"))
  }
}
