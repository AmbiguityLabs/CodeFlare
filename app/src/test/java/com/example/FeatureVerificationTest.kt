package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DraftPersistenceManager
import com.example.data.local.PersistedApiTesterState
import com.example.data.remote.ApiEnvVar
import com.example.data.remote.ApiTesterRequest
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.WorkerFile
import com.example.data.repository.CloudflareRepository
import com.example.idecore.LocalHistoryStore
import java.io.File
import okio.Buffer
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
class FeatureVerificationTest {

  @Test
  fun draftPersistenceAndLocalHistoryWriteAtomicallyAndRoundTripCleanly() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DraftPersistenceManager(context)
    val wsKey = "test_atomic_${System.nanoTime()}"

    manager.saveWorkspaceDraft(
      workspaceKey = wsKey,
      files =
        listOf(
          WorkerFile(
            path = "src/index.ts",
            content = "export default { fetch() { return new Response('draft'); } };",
            originalContent = "export default { fetch() { return new Response('orig'); } };",
            contentType = "application/typescript",
            isEntryPoint = true,
          ),
        ),
      openTabs = listOf("src/index.ts"),
      activeFilePath = "src/index.ts",
    )

    val restored = manager.loadWorkspaceDraft(wsKey)
    assertNotNull(restored)
    assertEquals(1, restored!!.files.size)
    assertEquals("src/index.ts", restored.activeFilePath)
    assertTrue(restored.files.first().content.contains("'draft'"))
    manager.clearWorkspaceDraft(wsKey)

    val apiState =
      PersistedApiTesterState(
        collections = listOf(ApiTesterRequest(id = "r1", name = "Ping", url = "https://edge.dev/ping")),
        envVars = listOf(ApiEnvVar(key = "apiToken", value = "tok_123")),
      )
    manager.saveApiTesterState(apiState)
    val loadedApi = manager.loadApiTesterState()
    assertNotNull(loadedApi)
    assertTrue(loadedApi!!.collections.any { it.name == "Ping" })

    val historyDir = File(context.cacheDir, "history_test_${System.nanoTime()}")
    val store = LocalHistoryStore(historyDir)
    store.recordSnapshot(wsKey, "src/index.ts", "v0", "v1", label = "AutoSave")
    store.flush()
    val snaps = store.snapshots(wsKey, "src/index.ts")
    assertEquals(2, snaps.size)
    assertEquals("AutoSave", snaps[0].label)
    assertEquals("Initial", snaps[1].label)
    assertEquals("v1", store.readSnapshot(wsKey, "src/index.ts", snaps[0].id))
    assertEquals("v0", store.readSnapshot(wsKey, "src/index.ts", snaps[1].id))
    assertFalse(historyDir.walkTopDown().any { it.name.endsWith(".tmp") })
    historyDir.deleteRecursively()
  }

  @Test
  fun multipartBundlerMapsWasmAndBinaryMimeTypesAndNormalizesRelativePaths() {
    assertEquals("application/wasm", MultipartWorkerParser.inferModuleMimeType("pkg/bg.wasm"))
    assertEquals("application/octet-stream", MultipartWorkerParser.inferModuleMimeType("assets/blob.bin"))
    assertEquals("application/octet-stream", MultipartWorkerParser.inferModuleMimeType("assets/data.dat"))

    val files =
      listOf(
        WorkerFile(
          path = "./src/index.js",
          content = "import mod from './lib/mod.wasm'; export default {};",
          originalContent = "",
          contentType = "application/javascript+module",
          isEntryPoint = true,
        ),
        WorkerFile(
          path = "/src/index.js",
          content = "duplicate",
          originalContent = "",
          contentType = "application/javascript+module",
          isEntryPoint = false,
        ),
        WorkerFile(
          path = "./lib/mod.wasm",
          content = "wasm-bytes",
          originalContent = "",
          contentType = "application/wasm",
          isEntryPoint = false,
        ),
        WorkerFile(
          path = "wrangler.jsonc",
          content = """{"name":"skip"}""",
          originalContent = "",
          contentType = "application/json",
          isEntryPoint = false,
        ),
      )

    val body =
      MultipartWorkerParser.buildUploadMultipartBody(
        files = files,
        entryModulePath = "./src/index.js",
        isEsModule = true,
        compatibilityDate = "2026-05-01",
      )
    val sink = Buffer()
    body.writeTo(sink)
    val serialized = sink.readUtf8()
    assertTrue(serialized.contains("\"main_module\":\"src/index.js\""))
    assertTrue(serialized.contains("application/wasm"))
    assertFalse(serialized.contains("wrangler.jsonc"))
    assertFalse(serialized.contains("duplicate"))
  }

  @Test
  fun formatTailTraceEventsParsesTraceV1JsonAndFallsBackForRawText() {
    val tracePayload =
      """
      {
        "outcome": "exception",
        "event": {
          "request": {
            "method": "POST",
            "url": "https://api.example.workers.dev/checkout"
          }
        },
        "logs": [
          { "level": "log", "message": ["cart validated", { "items": 2 }] }
        ],
        "exceptions": [
          { "name": "TypeError", "message": "Cannot read properties of undefined" }
        ]
      }
      """.trimIndent()

    val events = CloudflareRepository.formatTailTraceEvents(tracePayload)
    assertEquals(3, events.size)
    assertTrue(events[0].contains("[EXCEPTION]") && events[0].contains("POST https://api.example.workers.dev/checkout"))
    assertTrue(events[1].contains("[LOG]") && events[1].contains("cart validated"))
    assertTrue(events[2].contains("[ERR]") && events[2].contains("TypeError: Cannot read properties of undefined"))

    val plainEvents = CloudflareRepository.formatTailTraceEvents("plain heartbeat")
    assertEquals(listOf("plain heartbeat"), plainEvents)
  }
}
