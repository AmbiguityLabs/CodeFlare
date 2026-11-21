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

  @Test
  fun standalonePlainJsExecutesDirectlyWhileExportOnlyModuleDefersToEntry() {
    val ws =
      WorkerWorkspace(
        scriptName = "mixed-workspace",
        isEsModuleFormat = true,
        entryModulePath = "src/index.js",
        compatibilityDate = "2024-11-01",
        files =
          listOf(
            WorkerFile(
              path = "src/index.js",
              content = "export default { fetch() { return new Response('ok'); } };",
              originalContent = "",
              contentType = "application/javascript+module",
              isEntryPoint = true,
            ),
            WorkerFile(
              path = "src/consts.ts",
              content = "export const VERSION = '1.0.0';",
              originalContent = "",
              contentType = "application/javascript+module",
              isEntryPoint = false,
            ),
            WorkerFile(
              path = "scratch.js",
              content = "const a = 6 * 7; a;",
              originalContent = "",
              contentType = "application/javascript+module",
              isEntryPoint = false,
            ),
          ),
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = true,
        accountSubdomain = "demo",
      )

    assertTrue(LocalScriptRunner.isPureDeclarationLibraryModule("export const VERSION = '1.0.0';"))
    assertFalse(LocalScriptRunner.isPureDeclarationLibraryModule("const a = 6 * 7; a;"))
    assertEquals("src/index.js", LocalScriptRunner.resolveTargetEntryPath(ws, "src/consts.ts"))
    assertEquals("scratch.js", LocalScriptRunner.resolveTargetEntryPath(ws, "scratch.js"))
  }

  @Test
  fun sandboxWrapperAndJsonResultParserHandleLogsResultsAndErrors() {
    val exprScript = LocalScriptRunner.buildSandboxScriptWrapper("21 * 2", isExpression = true)
    assertTrue(exprScript.contains("return (21 * 2);"))

    val okJson = "\"{\\\"logs\\\":[{\\\"l\\\":\\\"LOG\\\",\\\"m\\\":\\\"hello\\\"}],\\\"result\\\":\\\"42\\\",\\\"error\\\":null}\""
    val okParsed = LocalScriptRunner.parseSandboxResultJson(okJson, "scratch.js", System.currentTimeMillis() - 5L, "12:00:00")
    assertEquals("JS SANDBOX", okParsed.mode)
    assertEquals(200, okParsed.statusCode)
    assertTrue(okParsed.body.contains("hello"))
    assertTrue(okParsed.body.contains("42"))
    assertTrue(okParsed.logs.any { it.level == "LOG" && it.message == "hello" })

    val errJson = "{\"logs\":[],\"result\":\"\",\"error\":\"ReferenceError: x is not defined\"}"
    val errParsed = LocalScriptRunner.parseSandboxResultJson(errJson, "scratch.js", System.currentTimeMillis(), "12:00:01")
    assertEquals(500, errParsed.statusCode)
    assertTrue(errParsed.errorMessage!!.contains("ReferenceError"))
  }

  @Test
  fun previewRendererResolvesRelativeSubpagesCssBinaryAssetsAndMarkdown() {
    val ws =
      WorkerWorkspace(
        scriptName = "web-site",
        isEsModuleFormat = false,
        entryModulePath = "index.html",
        compatibilityDate = "2024-11-01",
        files =
          listOf(
            WorkerFile("index.html", "<link href='css/app.css'>", "", "text/html", isEntryPoint = true),
            WorkerFile("css/app.css", "body { color: #fff; }", "", "text/css", isEntryPoint = false),
            WorkerFile("pages/about.html", "<h1>About</h1>", "", "text/html", isEntryPoint = false),
            WorkerFile("README.md", "# Hello EdgeCode", "", "text/markdown", isEntryPoint = false),
          ),
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = false,
        accountSubdomain = "",
      )
    val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    val binaryAssets = mapOf("assets/logo.png" to pngBytes)

    assertEquals(
      "pages/about.html",
      com.example.editor.LocalWorkspacePreviewRenderer.normalizeRequestPath("https://local.edgecode.app/css/../pages/about.html?v=1"),
    )

    val cssResp =
      com.example.editor.LocalWorkspacePreviewRenderer.resolveWebResource(
        workspace = ws,
        requestUrl = "https://local.edgecode.app/css/app.css",
        isDarkTheme = true,
        externalBinaryAssets = binaryAssets,
      )
    assertNotNull(cssResp)
    assertEquals("text/css", cssResp!!.mimeType)
    assertTrue(cssResp.data.readBytes().decodeToString().contains("#fff"))

    val relPageResp =
      com.example.editor.LocalWorkspacePreviewRenderer.resolveWebResource(
        workspace = ws,
        requestUrl = "https://local.edgecode.app/css/../pages/about.html",
        isDarkTheme = true,
        externalBinaryAssets = binaryAssets,
      )
    assertNotNull(relPageResp)
    assertEquals("text/html", relPageResp!!.mimeType)
    assertTrue(relPageResp.data.readBytes().decodeToString().contains("<h1>About</h1>"))

    val imgResp =
      com.example.editor.LocalWorkspacePreviewRenderer.resolveWebResource(
        workspace = ws,
        requestUrl = "https://local.edgecode.app/assets/logo.png",
        isDarkTheme = true,
        externalBinaryAssets = binaryAssets,
      )
    assertNotNull(imgResp)
    assertEquals("image/png", imgResp!!.mimeType)
    assertEquals(pngBytes.size, imgResp.data.readBytes().size)

    val mdResp =
      com.example.editor.LocalWorkspacePreviewRenderer.resolveWebResource(
        workspace = ws,
        requestUrl = "https://local.edgecode.app/README.md",
        isDarkTheme = true,
      )
    assertNotNull(mdResp)
    assertEquals("text/html", mdResp!!.mimeType)
    assertTrue(mdResp.data.readBytes().decodeToString().contains("<h1>Hello EdgeCode</h1>"))
  }

  @Test
  fun allocateFreeLocalPortAndParseReadyWorkerPortWorkReliably() {
    val port = LocalScriptRunner.allocateFreeLocalPort()
    assertTrue(port in 1..65535)
    assertEquals(43210, LocalScriptRunner.parseReadyWorkerPort("WORKER:43210"))
    assertEquals(null, LocalScriptRunner.parseReadyWorkerPort("SCRIPT:0"))
    assertEquals(null, LocalScriptRunner.parseReadyWorkerPort("WORKER:99999"))
    assertEquals(null, LocalScriptRunner.parseReadyWorkerPort("INVALID"))
  }

  @Test
  fun incrementalStagingUpdatesModifiedFilesAndRemovesDeletedOnes() {
    val tmpDir = createTempDirectory("deno-incremental-stage").toFile()
    try {
      val fileA =
        WorkerFile("src/index.ts", "export default { fetch: () => new Response('v1') };", "", "application/javascript+module", true)
      val fileB =
        WorkerFile("src/old.ts", "export const old = true;", "", "application/javascript+module", false)

      val runner1 = LocalScriptRunner.stageWorkspaceFiles(tmpDir, listOf(fileA, fileB), "// runner v1")
      assertNotNull(runner1)
      val stagedA = File(tmpDir, "src/index.ts")
      val stagedB = File(tmpDir, "src/old.ts")
      assertTrue(stagedA.exists())
      assertTrue(stagedB.exists())
      stagedA.setLastModified(1_000_000L)

      val fileACopy = fileA.copy()
      val fileC =
        WorkerFile("src/new.ts", "export const added = 1;", "", "application/javascript+module", false)
      val runner2 = LocalScriptRunner.stageWorkspaceFiles(tmpDir, listOf(fileACopy, fileC), "// runner v1")
      assertNotNull(runner2)
      assertEquals(1_000_000L, stagedA.lastModified())
      assertFalse(stagedB.exists())
      assertTrue(File(tmpDir, "src/new.ts").exists())
    } finally {
      tmpDir.deleteRecursively()
    }
  }

  @Test
  fun synthesizeDenoImportMapJsonBuildsNpmImportsFromPackageJson() {
    val pkgJson =
      """
      {
        "name": "worker-app",
        "dependencies": {
          "hono": "^4.6.0",
          "itty-router": "~5.0.0"
        },
        "devDependencies": {
          "local-pkg": "workspace:*"
        }
      }
      """.trimIndent()
    val pkgFile = WorkerFile("package.json", pkgJson, "", "application/json", false)
    val indexFile = WorkerFile("src/index.ts", "export default {}", "", "application/javascript+module", true)

    val generated = LocalScriptRunner.synthesizeDenoImportMapJson(listOf(pkgFile, indexFile))
    assertNotNull(generated)
    assertTrue(generated!!.contains("\"hono\": \"npm:hono@^4.6.0\""))
    assertTrue(generated.contains("\"hono/\": \"npm:hono@^4.6.0/\""))
    assertTrue(generated.contains("\"itty-router\": \"npm:itty-router@~5.0.0\""))
    assertFalse(generated.contains("local-pkg"))

    val explicitDeno = WorkerFile("deno.json", "{\"imports\":{}}", "", "application/json", false)
    assertEquals(null, LocalScriptRunner.synthesizeDenoImportMapJson(listOf(pkgFile, explicitDeno)))

    val tmpDir = createTempDirectory("deno-import-map").toFile()
    try {
      LocalScriptRunner.stageWorkspaceFiles(tmpDir, listOf(pkgFile, indexFile), "// runner")
      val stagedDenoJson = File(tmpDir, "deno.json")
      assertTrue(stagedDenoJson.exists())
      assertTrue(stagedDenoJson.readText().contains("\"hono\": \"npm:hono@^4.6.0\""))
    } finally {
      tmpDir.deleteRecursively()
    }
  }

  @Test
  fun buildBindingsJsonForwardsDurableObjectQueueAndServiceMetadata() {
    val json =
      LocalScriptRunner.buildBindingsJson(
        listOf(
          com.example.data.remote.CfWorkerBinding(
            name = "COUNTER",
            type = "durable_object_namespace",
            className = "CounterDO",
            scriptName = "self-worker",
          ),
          com.example.data.remote.CfWorkerBinding(
            name = "JOBS",
            type = "queue",
            queueName = "email-jobs",
          ),
          com.example.data.remote.CfWorkerBinding(
            name = "AUTH_SVC",
            type = "service",
            service = "auth-worker",
            environment = "production",
          ),
        ),
      )

    assertTrue(json.contains("\"className\":\"CounterDO\""))
    assertTrue(json.contains("\"queueName\":\"email-jobs\""))
    assertTrue(json.contains("\"service\":\"auth-worker\""))
    assertTrue(json.contains("\"environment\":\"production\""))
  }
}

