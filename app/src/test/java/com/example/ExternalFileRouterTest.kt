package com.example

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import com.example.data.local.ExternalIntentExtractor
import com.example.data.local.ExternalOpenTargetMode
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
class ExternalFileRouterTest {

  @Test
  fun extractFromIntentSupportsViewEditSendAndSendMultiple() {
    val viewUri = Uri.parse("content://com.example.files/doc/index.html")
    val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(viewUri, "text/html")
    val viewExtracted = ExternalIntentExtractor.extractFromIntent(viewIntent)
    assertNotNull(viewExtracted)
    assertEquals(Intent.ACTION_VIEW, viewExtracted!!.action)
    assertEquals(listOf(viewUri), viewExtracted.uris)

    val editUri = Uri.parse("file:///storage/emulated/0/web/app.js")
    val editIntent = Intent(Intent.ACTION_EDIT).apply {
      clipData = ClipData.newRawUri("code", editUri)
    }
    val editExtracted = ExternalIntentExtractor.extractFromIntent(editIntent)
    assertNotNull(editExtracted)
    assertEquals(listOf(editUri), editExtracted!!.uris)

    val sendSnippetIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, "Quick Check")
      putExtra(Intent.EXTRA_TEXT, "console.log('hello');")
    }
    val sendSnippetExtracted = ExternalIntentExtractor.extractFromIntent(sendSnippetIntent)
    assertNotNull(sendSnippetExtracted)
    assertEquals("Quick Check", sendSnippetExtracted!!.sharedSubject)
    assertEquals("console.log('hello');", sendSnippetExtracted.sharedText)
    assertTrue(sendSnippetExtracted.uris.isEmpty())

    val streamUri1 = Uri.parse("content://docs/1.css")
    val streamUri2 = Uri.parse("content://docs/2.js")
    val multiIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
      type = "*/*"
      putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(streamUri1, streamUri2))
    }
    val multiExtracted = ExternalIntentExtractor.extractFromIntent(multiIntent)
    assertNotNull(multiExtracted)
    assertEquals(listOf(streamUri1, streamUri2), multiExtracted!!.uris)

    assertNull(ExternalIntentExtractor.extractFromIntent(Intent(Intent.ACTION_MAIN)))
  }

  @Test
  fun blockedAppAndExecutableExtensionsAndMimesAreRejected() {
    listOf("app.apk", "bundle.aab", "classes.dex", "libdeno.so", "setup.exe", "firmware.bin", "payload.jar").forEach {
      assertTrue("Expected $it to be blocked", ExternalIntentExtractor.isBlockedAppOrExecutable(it, null))
    }
    assertTrue(ExternalIntentExtractor.isBlockedAppOrExecutable("download", "application/vnd.android.package-archive"))
    assertTrue(ExternalIntentExtractor.isBlockedAppOrExecutable("native", "application/x-sharedlib"))

    listOf("notes.foo", "script.weirdext", "Makefile", "index.html", "bundle.zip", "app.js", "icon.svg").forEach {
      assertFalse("Expected $it to be allowed", ExternalIntentExtractor.isBlockedAppOrExecutable(it, "text/plain"))
    }
  }

  @Test
  fun binaryBytesCheckRejectsNulBytesButAllowsBanglaUtf8Text() {
    val nulPayload = byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0x00, 0x01)
    assertTrue(ExternalIntentExtractor.looksLikeBinaryBytes(nulPayload))

    val banglaText = "আমার সোনার বাংলা\nconsole.log('EdgeCode');\n".toByteArray(Charsets.UTF_8)
    assertFalse(ExternalIntentExtractor.looksLikeBinaryBytes(banglaText))
  }

  @Test
  fun determineOpenModeRoutesFilesToExpectedModes() {
    assertEquals(ExternalOpenTargetMode.PREVIEW_HTML, ExternalIntentExtractor.determineOpenMode("site/index.html"))
    assertEquals(ExternalOpenTargetMode.PREVIEW_HTML, ExternalIntentExtractor.determineOpenMode("page.HTM"))
    assertEquals(ExternalOpenTargetMode.PREVIEW_MARKDOWN, ExternalIntentExtractor.determineOpenMode("docs/README.md"))
    assertEquals(ExternalOpenTargetMode.PREVIEW_SVG, ExternalIntentExtractor.determineOpenMode("assets/logo.svg"))
    assertEquals(ExternalOpenTargetMode.CODE_EDITOR_WITH_RUN, ExternalIntentExtractor.determineOpenMode("scripts/run.mjs"))
    assertEquals(ExternalOpenTargetMode.CODE_EDITOR_WITH_RUN, ExternalIntentExtractor.determineOpenMode("src/main.ts"))
    assertEquals(ExternalOpenTargetMode.ZIP_IMPORT, ExternalIntentExtractor.determineOpenMode("project.zip"))
    assertEquals(ExternalOpenTargetMode.CODE_EDITOR, ExternalIntentExtractor.determineOpenMode("styles/main.css"))
    assertEquals(ExternalOpenTargetMode.CODE_EDITOR, ExternalIntentExtractor.determineOpenMode("notes.unknownext"))
  }

  @Test
  fun sanitizeSharedSnippetFilenameInfersExtensionAndCleansSubject() {
    assertEquals("shared-snippet.html", ExternalIntentExtractor.sanitizeSharedSnippetFilename(null, "<!DOCTYPE html><html></html>"))
    assertEquals("shared-snippet.svg", ExternalIntentExtractor.sanitizeSharedSnippetFilename(null, "<svg viewBox='0 0 10 10'></svg>"))
    assertEquals("shared-snippet.json", ExternalIntentExtractor.sanitizeSharedSnippetFilename(null, "{\"ok\":true}"))
    assertEquals("shared-snippet.js", ExternalIntentExtractor.sanitizeSharedSnippetFilename(null, "console.log(42)"))
    assertEquals("shared-snippet.md", ExternalIntentExtractor.sanitizeSharedSnippetFilename(null, "# Heading\nBody"))
    assertEquals("My_Snippet.js", ExternalIntentExtractor.sanitizeSharedSnippetFilename("My Snippet!", "console.log(1)"))
    assertEquals("demo.css", ExternalIntentExtractor.sanitizeSharedSnippetFilename("demo.css", "body { color: red; }"))
  }
}
