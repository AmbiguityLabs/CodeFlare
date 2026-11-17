package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AutoSaveDraftStore
import com.example.data.local.AutoSaveSessionPointer
import com.example.data.local.AutoSavedWorkspaceDraft
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoSaveDraftStoreTest {

  private fun sampleFiles(): List<WorkerFile> =
    listOf(
      WorkerFile(
        path = "src/index.js",
        content = "export default { fetch() { return new Response('edited'); } };",
        originalContent = "export default { fetch() { return new Response('v1'); } };",
        contentType = "application/javascript+module",
        isEntryPoint = true,
      ),
      WorkerFile(
        path = "src/helper.js",
        content = "export const flag = true;",
        originalContent = "export const flag = true;",
        contentType = "application/javascript+module",
        isEntryPoint = false,
      ),
      WorkerFile(
        path = ".readonly/info.txt",
        content = "virtual",
        originalContent = "virtual",
        contentType = "text/plain",
        isEntryPoint = false,
        isVirtualReadOnly = true,
      ),
    )

  @Test
  fun saveLoadAndClearWorkspaceDraftRoundTripsViaDataStore() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = AutoSaveDraftStore(context)
    store.clearAllDrafts()

    val files = sampleFiles()
    val tabs = listOf("src/index.js", "src/helper.js")
    val digest =
      AutoSaveDraftStore.computeContentDigest(
        files = files,
        openTabs = tabs,
        activeFilePath = "src/index.js",
        entryModulePath = "src/index.js",
      )
    val draft =
      AutoSavedWorkspaceDraft(
        workspaceKey = "acct_1::api-worker",
        scriptName = "api-worker",
        isLocalScratchpad = false,
        accountId = "acct_1",
        entryModulePath = "src/index.js",
        compatibilityDate = "2024-11-01",
        openTabs = tabs,
        activeFilePath = "src/index.js",
        cursorSelections = mapOf("src/index.js" to (12 to 18)),
        files = files,
        savedAtEpochMs = 1_700_000_000_000L,
        contentDigest = digest,
      )

    store.saveWorkspaceDraft(draft)
    val loaded = store.loadWorkspaceDraft("acct_1::api-worker")
    assertNotNull(loaded)
    assertEquals("api-worker", loaded!!.scriptName)
    assertEquals(tabs, loaded.openTabs)
    assertEquals("src/index.js", loaded.activeFilePath)
    assertEquals(12 to 18, loaded.cursorSelections["src/index.js"])
    assertEquals(digest, loaded.contentDigest)
    // Virtual read-only files are excluded from persisted drafts
    assertEquals(2, loaded.files.size)
    assertTrue(loaded.files.none { it.isVirtualReadOnly })

    val fieldValues = loaded.toFieldValues()
    assertEquals(12, fieldValues["src/index.js"]?.selection?.start)
    assertEquals(18, fieldValues["src/index.js"]?.selection?.end)

    store.saveLastActiveSession(
      AutoSaveSessionPointer(
        workspaceKey = "acct_1::api-worker",
        scriptName = "api-worker",
        isLocalScratchpad = false,
        accountId = "acct_1",
      ),
    )
    val pointer = store.loadLastSessionPointer()
    assertNotNull(pointer)
    assertEquals("acct_1::api-worker", pointer!!.workspaceKey)
    assertEquals("acct_1", pointer.accountId)

    // Clearing the matching workspace draft also clears the active pointer
    store.clearWorkspaceDraft("acct_1::api-worker")
    assertNull(store.loadWorkspaceDraft("acct_1::api-worker"))
    assertNull(store.loadLastSessionPointer())
  }

  @Test
  fun mergeDraftIntoWorkspaceRestoresUnsavedEditsAndAddedFilesWhilePreservingBaseline() {
    val imported =
      WorkerWorkspace(
        scriptName = "edge-app",
        isEsModuleFormat = true,
        entryModulePath = "src/index.js",
        compatibilityDate = "2024-11-01",
        files =
          listOf(
            WorkerFile(
              path = "src/index.js",
              content = "export default { fetch() { return new Response('cloud'); } };",
              originalContent = "export default { fetch() { return new Response('cloud'); } };",
              contentType = "application/javascript+module",
              isEntryPoint = true,
            ),
          ),
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = true,
        accountSubdomain = "demo",
      )

    val matchingDraft =
      AutoSavedWorkspaceDraft(
        workspaceKey = "acct::edge-app",
        scriptName = "edge-app",
        isLocalScratchpad = false,
        accountId = "acct",
        entryModulePath = "src/index.js",
        compatibilityDate = "2024-11-01",
        openTabs = listOf("src/index.js"),
        activeFilePath = "src/index.js",
        cursorSelections = emptyMap(),
        files = imported.files,
        savedAtEpochMs = 100L,
        contentDigest = 1,
      )
    val (unchangedWs, hasUnchangedDiff) = AutoSaveDraftStore.mergeDraftIntoWorkspace(imported, matchingDraft)
    assertFalse(hasUnchangedDiff)
    assertEquals(imported.files, unchangedWs.files)

    val editedDraft =
      matchingDraft.copy(
        files =
          listOf(
            WorkerFile(
              path = "src/index.js",
              content = "export default { fetch() { return new Response('local-unsaved'); } };",
              originalContent = "stale",
              contentType = "application/javascript+module",
              isEntryPoint = true,
            ),
            WorkerFile(
              path = "src/new-module.js",
              content = "export const extra = 42;",
              originalContent = "",
              contentType = "application/javascript+module",
              isEntryPoint = false,
            ),
          ),
      )

    val (mergedWs, hasDiff) = AutoSaveDraftStore.mergeDraftIntoWorkspace(imported, editedDraft)
    assertTrue(hasDiff)
    assertTrue(mergedWs.hasModuleSetChanged)
    assertEquals(2, mergedWs.files.size)

    val mergedIndex = mergedWs.files.first { it.path == "src/index.js" }
    assertEquals("export default { fetch() { return new Response('local-unsaved'); } };", mergedIndex.content)
    // Cloud baseline originalContent is preserved for accurate diff/revert
    assertEquals("export default { fetch() { return new Response('cloud'); } };", mergedIndex.originalContent)
    assertTrue(mergedIndex.isDirty)

    val mergedExtra = mergedWs.files.first { it.path == "src/new-module.js" }
    assertEquals("export const extra = 42;", mergedExtra.content)
    assertEquals("", mergedExtra.originalContent)
  }

  @Test
  fun contentDigestChangesWhenCodeOrActiveTabChanges() {
    val filesA = sampleFiles()
    val filesB = filesA.map { if (it.path == "src/index.js") it.copy(content = "changed") else it }
    val tabs = listOf("src/index.js", "src/helper.js")

    val d1 = AutoSaveDraftStore.computeContentDigest(filesA, tabs, "src/index.js", "src/index.js")
    val d2 = AutoSaveDraftStore.computeContentDigest(filesA, tabs, "src/index.js", "src/index.js")
    val d3 = AutoSaveDraftStore.computeContentDigest(filesB, tabs, "src/index.js", "src/index.js")
    val d4 = AutoSaveDraftStore.computeContentDigest(filesA, tabs, "src/helper.js", "src/index.js")

    assertEquals(d1, d2)
    assertNotEquals(d1, d3)
    assertNotEquals(d1, d4)
  }
}
