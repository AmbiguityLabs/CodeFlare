package com.example

import com.example.data.local.ZipWorkspaceImporter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZipWorkspaceImporterTest {

  private fun buildZipBytes(entries: Map<String, ByteArray>): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { zos ->
      entries.forEach { (name, bytes) ->
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
      }
    }
    return bos.toByteArray()
  }

  @Test
  fun detectsZipMagicHeader() {
    val zipBytes = buildZipBytes(mapOf("index.js" to "export default {};".toByteArray()))
    assertTrue(ZipWorkspaceImporter.isZipArchiveHeader(zipBytes))
    assertFalse(ZipWorkspaceImporter.isZipArchiveHeader("{\"hello\":1}".toByteArray()))
    assertFalse(ZipWorkspaceImporter.isZipArchiveHeader(byteArrayOf(0x50, 0x4B)))
  }

  @Test
  fun stripsWrapperFolderParsesWranglerAndFiltersIgnoredAndBinaryFiles() {
    val zipBytes =
      buildZipBytes(
        mapOf(
          "my-worker-main/wrangler.jsonc" to
            """
            {
              "name": "edge-api-v2",
              "main": "src/worker.ts",
              "compatibility_date": "2025-02-10",
              "compatibility_flags": ["nodejs_compat"],
              "vars": { "API_ENV": "prod" },
              "kv_namespaces": [{ "binding": "CACHE", "id": "kv_123" }]
            }
            """.trimIndent().toByteArray(),
          "my-worker-main/src/worker.ts" to "export default { async fetch() { return new Response('ok'); } };".toByteArray(),
          "my-worker-main/src/routes/users.ts" to "export const users = [];".toByteArray(),
          "my-worker-main/assets/logo.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00, 0x01),
          "my-worker-main/node_modules/pkg/index.js" to "module.exports = {};".toByteArray(),
          "my-worker-main/.git/HEAD" to "ref: refs/heads/main".toByteArray(),
          "__MACOSX/._worker.ts" to byteArrayOf(0x00, 0x05, 0x16),
          "../evil.js" to "alert(1)".toByteArray(),
        ),
      )

    val readResult =
      ZipWorkspaceImporter.readZipStream(ByteArrayInputStream(zipBytes), "my-worker-main.zip").getOrThrow()
    assertEquals("my-worker-main", readResult.commonRootFolder)
    assertEquals(4, readResult.rawAssets.size)
    assertEquals(4, readResult.skippedIgnoredCount)

    val strippedPlan = ZipWorkspaceImporter.buildImportPlan(readResult, stripCommonRootFolder = true).getOrThrow()
    assertEquals("edge-api-v2", strippedPlan.suggestedProjectName)
    assertEquals("src/worker.ts", strippedPlan.entryModulePath)
    assertEquals("2025-02-10", strippedPlan.compatibilityDate)
    assertEquals(listOf("nodejs_compat"), strippedPlan.compatibilityFlags)
    assertEquals(2, strippedPlan.bindings.size)
    assertEquals(3, strippedPlan.files.size)
    assertTrue(strippedPlan.files.any { it.path == "src/worker.ts" && it.isEntryPoint })
    assertTrue(strippedPlan.files.any { it.path == "src/routes/users.ts" })
    assertEquals(listOf("assets/logo.png"), strippedPlan.skippedBinaryFiles)

    val unstrippedPlan = ZipWorkspaceImporter.buildImportPlan(readResult, stripCommonRootFolder = false).getOrThrow()
    assertTrue(unstrippedPlan.files.any { it.path == "my-worker-main/src/worker.ts" })
    assertFalse(unstrippedPlan.stripRootFolder)
  }

  @Test
  fun fallsBackToPackageJsonAndDetectsBinaryNulContent() {
    val zipBytes =
      buildZipBytes(
        mapOf(
          "package.json" to """{"name":"@org/hello-edge","main":"dist/app.js"}""".toByteArray(),
          "dist/app.js" to "export default {};".toByteArray(),
          "data.custom" to byteArrayOf(0x41, 0x42, 0x00, 0x43),
        ),
      )

    val readResult =
      ZipWorkspaceImporter.readZipStream(ByteArrayInputStream(zipBytes), "hello.zip").getOrThrow()
    assertNull(readResult.commonRootFolder)

    val plan = ZipWorkspaceImporter.buildImportPlan(readResult, stripCommonRootFolder = true).getOrThrow()
    assertEquals("hello-edge", plan.suggestedProjectName)
    assertEquals("dist/app.js", plan.entryModulePath)
    assertEquals(2, plan.files.size)
    assertEquals(listOf("data.custom"), plan.skippedBinaryFiles)
  }

  @Test
  fun detectsWranglerTomlAndEdgeCodeBundleJsonInsideZip() {
    val tomlZip =
      buildZipBytes(
        mapOf(
          "wrangler.toml" to
            """
            name = "toml-worker"
            main = "index.mjs"
            compatibility_date = "2024-12-01"
            workers_dev = true
            """.trimIndent().toByteArray(),
          "index.mjs" to "export default { fetch() { return new Response('hi'); } };".toByteArray(),
        ),
      )
    val tomlRead = ZipWorkspaceImporter.readZipStream(ByteArrayInputStream(tomlZip), "archive.zip").getOrThrow()
    val tomlPlan = ZipWorkspaceImporter.buildImportPlan(tomlRead, stripCommonRootFolder = true).getOrThrow()
    assertEquals("toml-worker", tomlPlan.suggestedProjectName)
    assertEquals("index.mjs", tomlPlan.entryModulePath)
    assertEquals("2024-12-01", tomlPlan.compatibilityDate)
    assertEquals(true, tomlPlan.workersDev)

    val bundleZip =
      buildZipBytes(
        mapOf(
          "demo-ram-bundle.json" to """{"scriptName":"demo-worker","files":[{"path":"index.js","content":"export default {}"}]}""".toByteArray(),
        ),
      )
    val bundleRead = ZipWorkspaceImporter.readZipStream(ByteArrayInputStream(bundleZip), "bundle.zip").getOrThrow()
    val bundlePlan = ZipWorkspaceImporter.buildImportPlan(bundleRead, stripCommonRootFolder = true).getOrThrow()
    assertNotNull(bundlePlan.edgeCodeBundleJson)
  }

  @Test
  fun rejectsEmptyOrBinaryOnlyZipArchives() {
    val emptyZip = buildZipBytes(emptyMap())
    assertTrue(ZipWorkspaceImporter.readZipStream(ByteArrayInputStream(emptyZip), "empty.zip").isFailure)

    val binaryOnlyZip = buildZipBytes(mapOf("pic.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    val read = ZipWorkspaceImporter.readZipStream(ByteArrayInputStream(binaryOnlyZip), "pics.zip").getOrThrow()
    assertTrue(ZipWorkspaceImporter.buildImportPlan(read, stripCommonRootFolder = true).isFailure)
  }
}
