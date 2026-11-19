package com.example

import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import com.example.editor.LocalScriptRunner
import java.io.File
import kotlin.io.path.createTempDirectory
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

    assertEquals("src/index.js", LocalScriptRunner.resolveTargetEntryPath(ws, "src/helper.ts"))
    assertEquals("public/index.html", LocalScriptRunner.resolveTargetEntryPath(ws, "public/index.html"))
    assertEquals("scripts/bench.js", LocalScriptRunner.resolveTargetEntryPath(ws, "scripts/bench.js"))
  }

  @Test
  fun resolveDenoBinaryPathPointsToNativeLibraryDirLibdenoSo() {
    assertEquals(
      "/data/app/edgecode/lib/arm64/libdeno.so",
      LocalScriptRunner.resolveDenoBinaryPath("/data/app/edgecode/lib/arm64"),
    )
    assertEquals(
      "/data/app/edgecode/lib/arm64/libdeno.so",
      LocalScriptRunner.resolveDenoBinaryPath("/data/app/edgecode/lib/arm64/"),
    )
  }

  @Test
  fun stageWorkspaceFilesWritesEditableModulesAndRunnerWithoutPathTraversal() {
    val tmpDir = createTempDirectory("deno-stage-test").toFile()
    try {
      val files =
        listOf(
          WorkerFile(
            path = "src/index.ts",
            content = "import { Readable } from 'node:stream'; export default { fetch() { return new Response(typeof Readable); } };",
            originalContent = "",
            contentType = "application/javascript+module",
            isEntryPoint = true,
          ),
          WorkerFile(
            path = "../../etc/passwd",
            content = "blocked",
            originalContent = "",
            contentType = "text/plain",
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

      val runner =
        LocalScriptRunner.stageWorkspaceFiles(
          workspaceDir = tmpDir,
          files = files,
          runnerSource = "// universal runner",
        )

      assertNotNull(runner)
      assertTrue(runner!!.exists())
      assertTrue(File(tmpDir, "src/index.ts").exists())
      assertFalse(File(tmpDir, ".edgecode/bindings.json").exists())
      assertFalse(File(tmpDir.parentFile, "etc/passwd").exists())
    } finally {
      tmpDir.deleteRecursively()
    }
  }

  @Test
  fun buildBindingsJsonSerializesWorkerBindingsCleanly() {
    val json =
      LocalScriptRunner.buildBindingsJson(
        listOf(
          CfWorkerBinding(name = "API_URL", type = "plain_text", text = "https://example.com"),
          CfWorkerBinding(name = "SESSION_KV", type = "kv_namespace"),
        ),
      )
    val parsed = org.json.JSONArray(json)
    assertEquals(2, parsed.length())
    val first = parsed.getJSONObject(0)
    assertEquals("API_URL", first.getString("name"))
    assertEquals("plain_text", first.getString("type"))
    assertEquals("https://example.com", first.getString("text"))
    val second = parsed.getJSONObject(1)
    assertEquals("SESSION_KV", second.getString("name"))
    assertEquals("kv_namespace", second.getString("type"))
  }
}
