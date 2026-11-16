package com.example

import com.example.data.local.DeviceFileHelper
import com.example.data.local.RawPickedAsset
import com.example.data.remote.Blake3
import com.example.data.remote.PagesDirectUploadHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PagesDirectUploadHasherTest {
  @Test
  fun blake3_matchesOfficialTestVectors() {
    // Official BLAKE3 test vector for empty input ""
    assertEquals(
      "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262",
      Blake3.hashHex(ByteArray(0)),
    )

    // Official BLAKE3 test vector for "abc"
    // Verify deterministic 32-byte digest and multi-chunk (>1024 bytes) consistency
    val abcHash = Blake3.hashHex("abc".toByteArray(Charsets.UTF_8))
    assertEquals(64, abcHash.length)
    assertEquals("6437b3ac38465133ffb63b75273a8db548c558465d79db03fd359c6cd5bd9d85", abcHash)

    // Multi-chunk input (2048 bytes: 0, 1, 2, ..., 250 repeating, per official BLAKE3 test_vectors.json)
    val multiChunkInput = ByteArray(2048) { (it % 251).toByte() }
    val multiHash = Blake3.hashHex(multiChunkInput)
    assertEquals(
      "e776b6028c7cd22a4d0ba182a8bf62205d2ef576467e838ed6f2529b85fba24a",
      multiHash,
    )
  }

  @Test
  fun pagesAssetHash_usesBase64PlusExtensionAndTruncatesTo32HexChars() {
    val htmlBytes = "<h1>EdgeCode</h1>".toByteArray(Charsets.UTF_8)
    val hashHtml = PagesDirectUploadHasher.computeAssetHash(htmlBytes, "/index.html")
    val hashJs = PagesDirectUploadHasher.computeAssetHash(htmlBytes, "/index.js")

    assertEquals(32, hashHtml.length)
    assertEquals(32, hashJs.length)
    // Extension participates in hash, so same bytes with different extension yield different hash
    assertNotEquals(hashHtml, hashJs)

    // Leading dot or no extension behaves like path.extname().slice(1) -> ""
    assertEquals("", PagesDirectUploadHasher.extractExtensionWithoutDot("/_headers"))
    assertEquals("", PagesDirectUploadHasher.extractExtensionWithoutDot("/.well-known"))
    assertEquals("js", PagesDirectUploadHasher.extractExtensionWithoutDot("/assets/app.min.js"))
  }

  @Test
  fun pathNormalizationAndMimeInference() {
    assertEquals("/assets/app.js", PagesDirectUploadHasher.normalizeAssetPath("assets\\app.js"))
    assertEquals("/index.html", PagesDirectUploadHasher.normalizeAssetPath("/./index.html"))
    assertEquals("text/html; charset=utf-8", PagesDirectUploadHasher.guessContentType("/index.html"))
    assertEquals("application/javascript; charset=utf-8", PagesDirectUploadHasher.guessContentType("/main.mjs"))
    assertEquals("image/svg+xml", PagesDirectUploadHasher.guessContentType("/logo.svg"))
    assertEquals("text/plain; charset=utf-8", PagesDirectUploadHasher.guessContentType("/_headers"))

    val prepared = PagesDirectUploadHasher.prepareAsset("dist/app.css", "body{}".toByteArray())
    assertNotNull(prepared)
    assertEquals("/dist/app.css", prepared?.normalizedPath)
  }

  @Test
  fun stripCommonWrapperFolder_removesSingleSharedTopDir() {
    val wrapped =
      listOf(
        RawPickedAsset("dist/index.html", "hi".toByteArray()),
        RawPickedAsset("dist/assets/app.js", "js".toByteArray()),
      )
    val stripped = DeviceFileHelper.stripCommonWrapperFolder(wrapped)
    assertEquals(listOf("index.html", "assets/app.js"), stripped.map { it.relativePath })

    val mixed =
      listOf(
        RawPickedAsset("index.html", "hi".toByteArray()),
        RawPickedAsset("assets/app.js", "js".toByteArray()),
      )
    assertEquals(mixed, DeviceFileHelper.stripCommonWrapperFolder(mixed))
  }
}
