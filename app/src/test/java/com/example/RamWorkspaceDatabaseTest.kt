package com.example

import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.local.RamAccountInfraCache
import com.example.data.local.RamWorkspaceDatabase
import com.example.data.local.WorkerTestRunRecord
import com.example.data.remote.CfCronSchedule
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.CfWorkerScript
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
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
class RamWorkspaceDatabaseTest {

  private fun sampleWorkspace(name: String = "api-worker"): WorkerWorkspace {
    val index =
      WorkerFile(
        path = "src/index.js",
        content = "export default { fetch() { return new Response('v1'); } };",
        originalContent = "export default { fetch() { return new Response('v1'); } };",
        contentType = "application/javascript+module",
        isEntryPoint = true,
      )
    val utils =
      WorkerFile(
        path = "src/utils.js",
        content = "export const version = 1;",
        originalContent = "export const version = 1;",
        contentType = "application/javascript+module",
        isEntryPoint = false,
      )
    return WorkerWorkspace(
      scriptName = name,
      isEsModuleFormat = true,
      entryModulePath = "src/index.js",
      compatibilityDate = "2024-11-01",
      compatibilityFlags = listOf("nodejs_compat"),
      files = listOf(index, utils),
      directories = setOf("src"),
      bindings = listOf(CfWorkerBinding(name = "MY_VAR", type = "plain_text", text = "dev")),
      routes = emptyList(),
      schedules = listOf(CfCronSchedule(cron = "*/5 * * * *")),
      workersDevEnabled = true,
      accountSubdomain = "demo",
    )
  }

  @Test
  fun importAndTabPersistencePreservesTabsAcrossEdits() {
    val db = RamWorkspaceDatabase()
    val key = db.workspaceKey("acct-1", "api-worker")
    val ws = sampleWorkspace()

    val record =
      db.importWorkspace(
        key = key,
        workspace = ws,
        defaultTabs = listOf("src/index.js", "src/utils.js"),
        defaultActivePath = "src/utils.js",
      )

    assertEquals(listOf("src/index.js", "src/utils.js"), record.openTabPaths)
    assertEquals("src/utils.js", record.activeFilePath)
    assertEquals(1, db.stats.value.workspacesInRam)
    assertEquals(2, db.stats.value.totalFilesInRam)
    assertEquals(0, db.stats.value.dirtyFilesCount)

    db.saveTabAndEditorState(
      key = key,
      openTabs = listOf("src/utils.js", "src/index.js"),
      activeFilePath = "src/index.js",
      tabNavHistory = listOf("src/utils.js", "src/index.js"),
      tabNavIndex = 1,
      fieldValues = mapOf("src/index.js" to TextFieldValue("edited")),
    )

    val loaded = db.getWorkspace(key)
    assertNotNull(loaded)
    assertEquals(listOf("src/utils.js", "src/index.js"), loaded!!.openTabPaths)
    assertEquals("src/index.js", loaded.activeFilePath)
    assertEquals("edited", loaded.fieldValues["src/index.js"]?.text)
  }

  @Test
  fun undoRedoStacksAreIsolatedAndAccurate() {
    val db = RamWorkspaceDatabase()
    val keyA = db.workspaceKey("acct-1", "worker-a")
    val keyB = db.workspaceKey("acct-1", "worker-b")
    db.importWorkspace(keyA, sampleWorkspace("worker-a"))
    db.importWorkspace(keyB, sampleWorkspace("worker-b"))

    db.recordUndoSnapshot(keyA, "src/index.js", TextFieldValue("step-0"))
    db.recordUndoSnapshot(keyA, "src/index.js", TextFieldValue("step-1"))

    val undone = db.undo(keyA, "src/index.js", TextFieldValue("step-2"))
    assertEquals("step-1", undone?.text)
    assertNull(db.undo(keyB, "src/index.js", TextFieldValue("other")))

    val redone = db.redo(keyA, "src/index.js", TextFieldValue("step-1"))
    assertEquals("step-2", redone?.text)
  }

  @Test
  fun revertFileAndWorkspaceRestoresImportedBaseline() {
    val db = RamWorkspaceDatabase()
    val key = db.workspaceKey("acct-1", "api-worker")
    val imported = sampleWorkspace()
    db.importWorkspace(key, imported)

    db.updateWorkspace(key) { rec ->
      val editedFiles =
        rec.workingWorkspace.files.map { f ->
          if (f.path == "src/index.js") f.copy(content = "dirty edit") else f
        }
      rec.copy(
        workingWorkspace = rec.workingWorkspace.copy(files = editedFiles),
        hasPendingConfigSync = true,
        stagedSecrets = mapOf("API_SECRET" to "12345"),
      )
    }

    assertEquals(1, db.stats.value.dirtyFilesCount)
    assertTrue(db.getWorkspace(key)!!.hasPendingChanges)

    val afterFileRevert = db.revertFileToImportedBaseline(key, "src/index.js")!!
    assertFalse(afterFileRevert.workingWorkspace.files.first { it.path == "src/index.js" }.isDirty)
    assertTrue(afterFileRevert.hasPendingConfigSync)

    val afterWsRevert = db.revertWorkspaceToImportedBaseline(key)!!
    assertFalse(afterWsRevert.hasPendingChanges)
    assertEquals(0, db.stats.value.dirtyFilesCount)
    assertTrue(afterWsRevert.stagedSecrets.isEmpty())
  }

  @Test
  fun markExportedClearsPendingStateAndPromotesBaseline() {
    val db = RamWorkspaceDatabase()
    val key = db.workspaceKey("acct-1", "api-worker")
    val imported = sampleWorkspace()
    db.importWorkspace(key, imported, isNewUnexported = true)

    val editedWs =
      imported.copy(
        files =
          imported.files.map { f ->
            if (f.path == "src/index.js") f.copy(content = "export default { fetch() { return new Response('v2'); } };")
            else f
          },
        hasModuleSetChanged = true,
      )
    db.updateWorkspace(key) { it.copy(workingWorkspace = editedWs, hasPendingConfigSync = true) }

    val exported = db.markExported(key, editedWs)!!
    assertFalse(exported.hasPendingChanges)
    assertFalse(exported.isNewUnexportedWorker)
    assertFalse(exported.workingWorkspace.files.first { it.path == "src/index.js" }.isDirty)
    assertNotNull(exported.lastExportedAtEpochMs)
    assertEquals(0, db.stats.value.dirtyFilesCount)
    assertEquals(0, db.stats.value.pendingConfigChanges)
  }

  @Test
  fun bundleExportAndImportRoundTripsWorkspace() {
    val db = RamWorkspaceDatabase()
    val key = db.workspaceKey("acct-1", "bundle-worker")
    val ws = sampleWorkspace("bundle-worker")
    db.importWorkspace(key, ws)

    val json = db.exportWorkspaceBundleJson(key).getOrThrow()
    val imported = db.importWorkspaceBundleJson(json).getOrThrow()

    assertEquals("bundle-worker", imported.scriptName)
    assertEquals("src/index.js", imported.entryModulePath)
    assertEquals("2024-11-01", imported.compatibilityDate)
    assertEquals(listOf("nodejs_compat"), imported.compatibilityFlags)
    assertEquals(2, imported.files.size)
    assertEquals("MY_VAR", imported.bindings.first().name)
    assertEquals("*/5 * * * *", imported.schedules.first().cron)
  }

  @Test
  fun workerTestHistoryAndInfraCacheOperateStrictlyInRam() {
    val db = RamWorkspaceDatabase()
    for (i in 1..45) {
      db.recordWorkerTestRun(
        WorkerTestRunRecord(
          id = "run-$i",
          timestamp = "12:00:$i",
          url = "https://worker.workers.dev/$i",
          method = "GET",
          statusCode = 200,
          statusText = "OK",
          durationMs = 15L + i,
          responseBytes = 64,
          headers = mapOf("content-type" to "application/json"),
          body = "{\"n\":$i}",
        ),
      )
    }

    val history = db.getWorkerTestHistory()
    assertEquals(40, history.size)
    assertEquals("run-45", history.first().id)

    db.clearWorkerTestHistory()
    assertTrue(db.getWorkerTestHistory().isEmpty())

    db.putInfraCache(
      RamAccountInfraCache(
        accountId = "acct-1",
        workers = listOf(CfWorkerScript(id = "edge-api")),
      ),
    )
    assertEquals("edge-api", db.getInfraCache("acct-1")?.workers?.firstOrNull()?.id)
  }
}
