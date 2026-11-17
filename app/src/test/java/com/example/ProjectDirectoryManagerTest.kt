package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.local.ProjectDirectoryManager
import com.example.data.local.RamWorkspaceDatabase
import com.example.data.remote.CfCronSchedule
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectDirectoryManagerTest {

  private lateinit var manager: ProjectDirectoryManager

  @Before
  fun setup() {
    val context = RuntimeEnvironment.getApplication()
    manager = ProjectDirectoryManager(context)
    manager.rootDir.deleteRecursively()
  }

  private fun buildSampleWorkspace(name: String = "edge-api"): WorkerWorkspace =
    WorkerWorkspace(
      scriptName = name,
      isEsModuleFormat = true,
      entryModulePath = "src/index.js",
      compatibilityDate = "2025-05-01",
      compatibilityFlags = listOf("nodejs_compat"),
      files =
        listOf(
          WorkerFile(
            path = "wrangler.jsonc",
            content = "{\"name\":\"$name\"}",
            originalContent = "{\"name\":\"$name\"}",
            contentType = "application/json",
            isEntryPoint = false,
          ),
          WorkerFile(
            path = "src/utils/cors.js",
            content = "export const corsHeaders = {};",
            originalContent = "export const corsHeaders = {};",
            contentType = "application/javascript+module",
            isEntryPoint = false,
          ),
          WorkerFile(
            path = "src/index.js",
            content = "export default { fetch() { return new Response('ok'); } };",
            originalContent = "export default { fetch() { return new Response('base'); } };",
            contentType = "application/javascript+module",
            isEntryPoint = true,
          ),
        ),
      directories = setOf("src", "src/utils", "assets"),
      bindings = listOf(CfWorkerBinding(name = "ENV_MODE", type = "plain_text", text = "prod")),
      routes = emptyList(),
      schedules = listOf(CfCronSchedule(cron = "0 * * * *")),
      workersDevEnabled = true,
      accountSubdomain = "edgecode",
    )

  @Test
  fun autoCreatesDedicatedStructureAndRoundTripsWorkspaces() = runBlocking {
    assertEquals("EdgeCodeProjects", manager.rootDir.name)
    assertTrue(manager.projectsDir.isDirectory)
    assertTrue(manager.importsDir.isDirectory)
    assertTrue(manager.exportsDir.isDirectory)

    val db = RamWorkspaceDatabase()
    val key = db.workspaceKey("local", "edge-api")
    val ws = buildSampleWorkspace()
    val record =
      db.importWorkspace(
        key = key,
        workspace = ws,
        defaultTabs = listOf("src/index.js", "src/utils/cors.js"),
        defaultActivePath = "src/utils/cors.js",
      ).copy(
        fieldValues =
          mapOf(
            "src/index.js" to TextFieldValue(ws.files.first { it.path == "src/index.js" }.content, TextRange(12, 12)),
          ),
      )

    manager.saveWorkspaceRecord(record)

    val projDir = manager.projectDirectoryFor(key)
    assertTrue(File(projDir, "src/index.js").exists())
    assertTrue(File(projDir, "src/utils/cors.js").exists())
    assertTrue(File(projDir, "assets").isDirectory)
    assertTrue(File(projDir, ".edgecode/manifest.json").exists())

    val loadedList = manager.loadAllWorkspaces()
    assertEquals(1, loadedList.size)
    val loaded = loadedList.first()
    assertEquals("edge-api", loaded.workingWorkspace.scriptName)
    assertEquals("src/utils/cors.js", loaded.activeFilePath)
    assertEquals(listOf("src/index.js", "src/utils/cors.js"), loaded.openTabPaths)
    assertEquals(TextRange(12, 12), loaded.fieldValues["src/index.js"]?.selection)
    assertEquals("ENV_MODE", loaded.workingWorkspace.bindings.first().name)
    assertEquals("0 * * * *", loaded.workingWorkspace.schedules.first().cron)
    assertTrue(loaded.workingWorkspace.files.first { it.path == "src/index.js" }.isDirty)

    // Deleting file from workspace removes stale file on next save
    val trimmedWs =
      loaded.workingWorkspace.copy(
        files = loaded.workingWorkspace.files.filterNot { it.path == "src/utils/cors.js" },
      )
    manager.saveWorkspaceRecord(loaded.copy(workingWorkspace = trimmedWs))
    assertFalse(File(projDir, "src/utils/cors.js").exists())

    manager.deleteWorkspaceDirectory(key)
    assertFalse(projDir.exists())
    assertTrue(manager.loadAllWorkspaces().isEmpty())
  }

  @Test
  fun normalizeAndOrganizeKeepImportsAndExportsStructured() = runBlocking {
    assertEquals(
      "src/helpers/auth.js",
      ProjectDirectoryManager.normalizeRelativePath("../..\\src\\.\\helpers\\auth.js", fallbackFolder = "src"),
    )
    assertEquals(
      "src/routes/users.js",
      ProjectDirectoryManager.normalizeRelativePath("users.js", fallbackFolder = "/src/routes/"),
    )
    assertEquals(
      "nested/item.json",
      ProjectDirectoryManager.normalizeRelativePath("nested/item.json", fallbackFolder = "src"),
    )

    val messy =
      listOf(
        WorkerFile(path = "wrangler.jsonc", content = "{}", originalContent = "{}", contentType = "application/json", isEntryPoint = false),
        WorkerFile(path = "src\\b.js", content = "b", originalContent = "b", contentType = "", isEntryPoint = false),
        WorkerFile(path = "../src/a.js", content = "a1", originalContent = "a1", contentType = "", isEntryPoint = false),
        WorkerFile(path = "src/a.js", content = "a2", originalContent = "a2", contentType = "", isEntryPoint = false),
        WorkerFile(path = "src/index.js", content = "main", originalContent = "main", contentType = "", isEntryPoint = true),
      )
    val organized = ProjectDirectoryManager.organizeWorkspaceFiles(messy)
    assertEquals(
      listOf("src/index.js", "src/a.js", "src/b.js", "wrangler.jsonc"),
      organized.map { it.path },
    )
    assertEquals("a2", organized.first { it.path == "src/a.js" }.content)

    manager.recordOrganizedImport("my-worker", organized, bundleJson = "{\"scriptName\":\"my-worker\"}")
    manager.recordOrganizedExportFile("my-worker", "../src/index.js", "export default {};")
    manager.recordOrganizedExportBundle("my-worker", "{\"bundle\":true}")

    val summary = manager.computeStorageSummary()
    assertEquals(5, summary.importedFilesCount) // 4 source files + bundle json
    assertEquals(2, summary.exportedFilesCount) // src/index.js + bundle json
    assertTrue(summary.totalDiskKb >= 1)
    assertTrue(summary.rootAbsolutePath.isNotBlank())
    assertFalse(summary.hasNativeStorageAccess)
    assertFalse(summary.isUsingNativeDeviceStorage)
  }

  @Test
  fun savesSingleFileDirectlyAndRefreshRootMigrationIsSafe() = runBlocking {
    val db = RamWorkspaceDatabase()
    val key = db.workspaceKey("local", "fast-save")
    val ws = buildSampleWorkspace("fast-save")
    val record = db.importWorkspace(key = key, workspace = ws)
    manager.saveWorkspaceRecord(record)

    val updatedFile =
      WorkerFile(
        path = "src/index.js",
        content = "export default { fetch: () => new Response('saved') };",
        originalContent = "export default { fetch() { return new Response('base'); } };",
        contentType = "application/javascript+module",
        isEntryPoint = true,
      )
    manager.saveSingleFile(key, updatedFile)

    val onDisk = File(manager.projectDirectoryFor(key), "src/index.js").readText()
    assertEquals("export default { fetch: () => new Response('saved') };", onDisk)
    assertFalse(manager.refreshRootDirectoryAndMigrate())
  }
}
