package com.example

import com.example.data.local.WorkspaceZipExporter
import com.example.data.remote.WorkerFile
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceZipExporterTest {

  @Test
  fun sanitizeZipPathPreventsTraversalAndNormalizesSlashes() {
    assertEquals("src/index.ts", WorkspaceZipExporter.sanitizeZipPath("/src\\index.ts"))
    assertEquals("wrangler.jsonc", WorkspaceZipExporter.sanitizeZipPath("./wrangler.jsonc"))
    assertNull(WorkspaceZipExporter.sanitizeZipPath("../secret.txt"))
    assertNull(WorkspaceZipExporter.sanitizeZipPath("src/../../etc/passwd"))
    assertNull(WorkspaceZipExporter.sanitizeZipPath("   "))
  }

  @Test
  fun suggestZipFileNameNormalizesSpecialCharsAndAppendsExtension() {
    assertEquals("my-worker-app.zip", WorkspaceZipExporter.suggestZipFileName("My Worker App!"))
    assertEquals("edge.zip", WorkspaceZipExporter.suggestZipFileName("edge.zip"))
    assertEquals("codeflare-project.zip", WorkspaceZipExporter.suggestZipFileName("   "))
  }

  @Test
  fun exportToBytesWritesTextAndBinaryAssetsWhileSkippingVirtualFiles() {
    val files =
      listOf(
        WorkerFile(
          path = "src/index.ts",
          content = "export default { fetch() { return new Response('ok'); } };",
          originalContent = "export default { fetch() { return new Response('ok'); } };",
          contentType = "application/typescript",
          isEntryPoint = true,
        ),
        WorkerFile(
          path = "public/logo.png",
          content = "// virtual placeholder",
          originalContent = "// virtual placeholder",
          contentType = "text/plain",
          isEntryPoint = false,
          isVirtualReadOnly = true,
        ),
        WorkerFile(
          path = "wrangler.jsonc",
          content = "{\"name\":\"demo\"}",
          originalContent = "{\"name\":\"demo\"}",
          contentType = "application/json",
          isEntryPoint = false,
        ),
      )
    val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    val result =
      WorkspaceZipExporter.exportToBytes(
        files = files,
        binaryAssets = mapOf("public/logo.png" to pngBytes),
      )
    assertTrue(result.isSuccess)
    val (zipBytes, summary) = result.getOrThrow()
    assertEquals(3, summary.entryCount)
    assertEquals(2, summary.textFileCount)
    assertEquals(1, summary.binaryAssetCount)

    val unpacked = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        unpacked[entry.name] = zis.readBytes()
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }

    assertEquals(setOf("src/index.ts", "wrangler.jsonc", "public/logo.png"), unpacked.keys)
    assertEquals("{\"name\":\"demo\"}", unpacked.getValue("wrangler.jsonc").toString(Charsets.UTF_8))
    assertArrayEquals(pngBytes, unpacked.getValue("public/logo.png"))
  }

  @Test
  fun exportToBytesFailsWhenWorkspaceHasNoExportableEntries() {
    val virtualOnly =
      listOf(
        WorkerFile(
          path = "README.virtual",
          content = "virtual",
          originalContent = "virtual",
          contentType = "text/plain",
          isEntryPoint = false,
          isVirtualReadOnly = true,
        ),
      )
    val res = WorkspaceZipExporter.exportToBytes(virtualOnly)
    assertTrue(res.isFailure)
  }

  @Test
  fun exportToCacheFilePrunesOlderExportsBeyondLimit() {
    val tempCache = Files.createTempDirectory("zip_export_cache_test").toFile()
    try {
      val files =
        listOf(
          WorkerFile(
            path = "index.js",
            content = "console.log(1);",
            originalContent = "console.log(1);",
            contentType = "application/javascript",
            isEntryPoint = true,
          ),
        )
      for (i in 1..11) {
        val out = WorkspaceZipExporter.exportToCacheFile(tempCache, "proj-$i", files).getOrThrow()
        assertTrue(out.isFile)
        Thread.sleep(5)
      }
      val exportDir = File(tempCache, "exports")
      val zips = exportDir.listFiles()?.filter { it.extension == "zip" }.orEmpty()
      assertTrue("Expected at most 8 cached ZIP files, got ${zips.size}", zips.size <= 8)
    } finally {
      tempCache.deleteRecursively()
    }
  }
}
