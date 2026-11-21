package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace
import com.example.data.security.AuthPreferencesStore
import com.example.data.security.EditorSettings
import com.example.editor.CodeFormatter
import com.example.idecore.completion.EdgeCompletionEngine
import com.example.idecore.completion.EdgeCompletionKind
import com.example.idecore.snippets.CodeSnippetItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExtendedFrameworkTest {

  @Test
  fun authPreferencesStorePersistsBackgroundAppearanceFormatOnSaveAndCustomSnippets() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AuthPreferencesStore(context)

    val customSnippet =
      CodeSnippetItem(
        id = "cust_1",
        abbrev = "mycf",
        title = "My Worker Response",
        tag = "CUSTOM",
        code = "return Response.json({ custom: true });",
        description = "Custom quick helper",
        isCustom = true,
      )
    val savedPaths = List(7) { idx -> "/data/bg_$idx.png" }

    store.updateEditorSettings { cur ->
      cur.copy(
        editorBgBlurDp = 14f,
        editorBgVignette = 0.45f,
        editorBgFitMode = EditorSettings.BG_FIT_FIT,
        editorBgHomeEnabled = true,
        editorBgSavedPaths = savedPaths,
        formatOnSave = true,
        customSnippets = listOf(customSnippet),
      )
    }

    val loaded = store.editorSettingsFlow.first()
    assertEquals(14f, loaded.editorBgBlurDp, 0.01f)
    assertEquals(0.45f, loaded.editorBgVignette, 0.01f)
    assertEquals(EditorSettings.BG_FIT_FIT, loaded.editorBgFitMode)
    assertTrue(loaded.editorBgHomeEnabled)
    assertEquals(EditorSettings.MAX_SAVED_BG_PATHS, loaded.editorBgSavedPaths.size)
    assertTrue(loaded.formatOnSave)
    assertEquals(1, loaded.customSnippets.size)
    assertEquals("mycf", loaded.customSnippets.first().abbrev)
    assertEquals("Custom quick helper", loaded.customSnippets.first().description)
  }

  @Test
  fun codeFormatterFormatsCssAndHtmlCleanly() {
    val rawCss = "body{color:#fff;\nmargin:0;\n}\n.card{\npadding:12px;\n}"
    val formattedCss = CodeFormatter.format(rawCss, WorkerLanguage.CSS, tabSize = 2)
    assertTrue(formattedCss.contains("  margin:0;"))
    assertTrue(formattedCss.contains(".card{"))
    assertTrue(formattedCss.contains("  padding:12px;"))

    val rawHtml = "<!DOCTYPE html>\n<html>\n<head>\n<title>Hi</title>\n</head>\n<body>\n<main>\n<p>Ok</p>\n</main>\n</body>\n</html>"
    val formattedHtml = CodeFormatter.format(rawHtml, WorkerLanguage.HTML, tabSize = 2)
    assertTrue(formattedHtml.contains("  <head>"))
    assertTrue(formattedHtml.contains("    <title>Hi</title>"))
    assertTrue(formattedHtml.contains("    <main>"))
  }

  @Test
  fun completionEngineIncludesCustomUserSnippets() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = EdgeCompletionEngine(context.cacheDir)
    val custom =
      CodeSnippetItem(
        id = "c_cfjson",
        abbrev = "cfjson",
        title = "Custom CF JSON",
        tag = "CUSTOM",
        code = "return Response.json({ status: 'ok' });",
        isCustom = true,
      )
    val doc = "export default {\n  fetch() {\n    cfjs\n  }\n};"
    val ws =
      WorkerWorkspace(
        scriptName = "demo",
        files = listOf(WorkerFile("src/index.ts", doc, WorkerLanguage.TYPESCRIPT, true)),
        entryModulePath = "src/index.ts",
      )
    val results =
      engine.complete(
        workspace = ws,
        filePath = "src/index.ts",
        documentText = doc,
        cursor = doc.indexOf("cfjs") + 4,
        language = WorkerLanguage.TYPESCRIPT,
        customSnippets = listOf(custom),
      )

    assertTrue(results.any { it.label == "cfjson" && it.kind == EdgeCompletionKind.TEMPLATE })
  }
}
