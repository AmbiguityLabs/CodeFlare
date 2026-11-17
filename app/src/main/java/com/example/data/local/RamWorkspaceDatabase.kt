package com.example.data.local

import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.remote.CfCronSchedule
import com.example.data.remote.CfD1Database
import com.example.data.remote.CfDurableObjectNamespace
import com.example.data.remote.CfKvNamespace
import com.example.data.remote.CfQueue
import com.example.data.remote.CfR2Bucket
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.CfWorkerScript
import com.example.data.remote.CfZone
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class RamDatabaseStats(
  val workspacesInRam: Int = 0,
  val totalFilesInRam: Int = 0,
  val dirtyFilesCount: Int = 0,
  val pendingConfigChanges: Int = 0,
  val estimatedRamKb: Int = 0,
  val activeWorkspaceKey: String? = null,
  val lastImportEpochMs: Long? = null,
  val lastExportEpochMs: Long? = null,
)

data class RamWorkspaceRecord(
  val key: String,
  val importedSnapshot: WorkerWorkspace,
  val workingWorkspace: WorkerWorkspace,
  val openTabPaths: List<String>,
  val activeFilePath: String?,
  val tabNavHistory: List<String>,
  val tabNavIndex: Int,
  val fieldValues: Map<String, TextFieldValue>,
  val stagedSecrets: Map<String, String> = emptyMap(),
  val deletedSecrets: Set<String> = emptySet(),
  val hasPendingConfigSync: Boolean = false,
  val isNewUnexportedWorker: Boolean = false,
  val importedAtEpochMs: Long = System.currentTimeMillis(),
  val lastExportedAtEpochMs: Long? = null,
) {
  val hasPendingChanges: Boolean
    get() =
      isNewUnexportedWorker ||
        hasPendingConfigSync ||
        stagedSecrets.isNotEmpty() ||
        deletedSecrets.isNotEmpty() ||
        workingWorkspace.hasModuleSetChanged ||
        workingWorkspace.files.any { it.isDirty }
}

data class RamAccountInfraCache(
  val accountId: String,
  val subdomain: String = "",
  val workers: List<CfWorkerScript> = emptyList(),
  val kvNamespaces: List<CfKvNamespace> = emptyList(),
  val zones: List<CfZone> = emptyList(),
  val d1Databases: List<CfD1Database> = emptyList(),
  val r2Buckets: List<CfR2Bucket> = emptyList(),
  val durableObjects: List<CfDurableObjectNamespace> = emptyList(),
  val queues: List<CfQueue> = emptyList(),
  val fetchedAtEpochMs: Long = System.currentTimeMillis(),
)

data class WorkerTestRunRecord(
  val id: String,
  val timestamp: String,
  val url: String,
  val method: String,
  val statusCode: Int,
  val statusText: String,
  val durationMs: Long,
  val responseBytes: Int,
  val headers: Map<String, String>,
  val body: String,
  val errorMessage: String? = null,
)

/**
 * Pure on-device in-memory (RAM) workspace database.
 *
 * Guarantees that remote Cloudflare fetches happen ONLY during Import (or explicit Re-Import)
 * and remote writes happen ONLY during Export / Deploy. Every editing, tab-switching, formatting,
 * wrangler config processing, binding setup, and live testing operation runs strictly on-device in RAM.
 */
class RamWorkspaceDatabase {
  private val workspaces = ConcurrentHashMap<String, RamWorkspaceRecord>()
  private val infraCaches = ConcurrentHashMap<String, RamAccountInfraCache>()
  private val undoStacksByWorkspace = ConcurrentHashMap<String, MutableMap<String, ArrayDeque<TextFieldValue>>>()
  private val redoStacksByWorkspace = ConcurrentHashMap<String, MutableMap<String, ArrayDeque<TextFieldValue>>>()
  private val workerTestHistory = mutableListOf<WorkerTestRunRecord>()
  private var activeKey: String? = null

  private val _stats = MutableStateFlow(RamDatabaseStats())
  val stats: StateFlow<RamDatabaseStats> = _stats.asStateFlow()

  fun workspaceKey(accountId: String?, scriptName: String): String {
    val scope = accountId?.takeIf { it.isNotBlank() } ?: "local"
    return "${scope}_${scriptName}"
  }

  fun importWorkspace(
    key: String,
    workspace: WorkerWorkspace,
    defaultTabs: List<String> = listOf(workspace.entryModulePath),
    defaultActivePath: String? = workspace.entryModulePath,
    isNewUnexported: Boolean = false,
  ): RamWorkspaceRecord {
    val validTabs = defaultTabs.filter { tp -> workspace.files.any { it.path == tp } }.ifEmpty {
      listOfNotNull(workspace.entryModulePath.takeIf { em -> workspace.files.any { it.path == em } } ?: workspace.files.firstOrNull()?.path)
    }
    val validActive =
      defaultActivePath?.takeIf { ap -> workspace.files.any { it.path == ap } }
        ?: validTabs.firstOrNull()
    val fields = workspace.files.associate { it.path to TextFieldValue(it.content) }
    val record =
      RamWorkspaceRecord(
        key = key,
        importedSnapshot = workspace,
        workingWorkspace = workspace,
        openTabPaths = validTabs,
        activeFilePath = validActive,
        tabNavHistory = validActive?.let { listOf(it) } ?: emptyList(),
        tabNavIndex = if (validActive != null) 0 else -1,
        fieldValues = fields,
        isNewUnexportedWorker = isNewUnexported,
        importedAtEpochMs = System.currentTimeMillis(),
      )
    workspaces[key] = record
    undoStacksByWorkspace.remove(key)
    redoStacksByWorkspace.remove(key)
    activeKey = key
    publishStats()
    return record
  }

  fun getWorkspace(key: String): RamWorkspaceRecord? = workspaces[key]

  fun hasWorkspace(key: String): Boolean = workspaces.containsKey(key)

  fun listWorkspaces(): List<RamWorkspaceRecord> = workspaces.values.sortedByDescending { it.importedAtEpochMs }

  fun setActiveWorkspaceKey(key: String?) {
    activeKey = key
    publishStats()
  }

  fun updateWorkspace(
    key: String,
    transform: (RamWorkspaceRecord) -> RamWorkspaceRecord,
  ): RamWorkspaceRecord? {
    val current = workspaces[key] ?: return null
    val updated = transform(current)
    workspaces[key] = updated
    activeKey = key
    publishStats()
    return updated
  }

  fun saveTabAndEditorState(
    key: String,
    openTabs: List<String>,
    activeFilePath: String?,
    tabNavHistory: List<String>,
    tabNavIndex: Int,
    fieldValues: Map<String, TextFieldValue>,
  ) {
    val current = workspaces[key] ?: return
    workspaces[key] =
      current.copy(
        openTabPaths = openTabs,
        activeFilePath = activeFilePath,
        tabNavHistory = tabNavHistory,
        tabNavIndex = tabNavIndex,
        fieldValues = fieldValues,
      )
    publishStats()
  }

  fun revertFileToImportedBaseline(key: String, filePath: String): RamWorkspaceRecord? {
    val record = workspaces[key] ?: return null
    val baselineFile = record.importedSnapshot.files.firstOrNull { it.path == filePath }
    val currentWs = record.workingWorkspace
    val nextFiles =
      if (baselineFile != null) {
        currentWs.files.map { f ->
          if (f.path == filePath) baselineFile.copy() else f
        }
      } else {
        // File was originally added locally; revert content to originalContent
        currentWs.files.map { f ->
          if (f.path == filePath) f.copy(content = f.originalContent) else f
        }
      }
    val restoredText = nextFiles.firstOrNull { it.path == filePath }?.content.orEmpty()
    val updated =
      record.copy(
        workingWorkspace = currentWs.copy(files = nextFiles),
        fieldValues = record.fieldValues + (filePath to TextFieldValue(restoredText)),
      )
    workspaces[key] = updated
    publishStats()
    return updated
  }

  fun revertWorkspaceToImportedBaseline(key: String): RamWorkspaceRecord? {
    val record = workspaces[key] ?: return null
    val baseline = record.importedSnapshot
    val validTabs =
      record.openTabPaths.filter { tp -> baseline.files.any { it.path == tp } }
        .ifEmpty { listOf(baseline.entryModulePath) }
    val validActive =
      record.activeFilePath?.takeIf { ap -> baseline.files.any { it.path == ap } }
        ?: validTabs.firstOrNull()
    val updated =
      record.copy(
        workingWorkspace = baseline,
        openTabPaths = validTabs,
        activeFilePath = validActive,
        tabNavHistory = validActive?.let { listOf(it) } ?: emptyList(),
        tabNavIndex = if (validActive != null) 0 else -1,
        fieldValues = baseline.files.associate { it.path to TextFieldValue(it.content) },
        stagedSecrets = emptyMap(),
        deletedSecrets = emptySet(),
        hasPendingConfigSync = false,
      )
    workspaces[key] = updated
    undoStacksByWorkspace.remove(key)
    redoStacksByWorkspace.remove(key)
    publishStats()
    return updated
  }

  fun markExported(key: String, exportedWorkspace: WorkerWorkspace): RamWorkspaceRecord? {
    val cleanFiles = exportedWorkspace.files.map { it.copy(originalContent = it.content) }
    val cleanWs = exportedWorkspace.copy(files = cleanFiles, hasModuleSetChanged = false)
    val current = workspaces[key]
    val now = System.currentTimeMillis()
    val updated =
      (current ?: RamWorkspaceRecord(
        key = key,
        importedSnapshot = cleanWs,
        workingWorkspace = cleanWs,
        openTabPaths = listOf(cleanWs.entryModulePath),
        activeFilePath = cleanWs.entryModulePath,
        tabNavHistory = listOf(cleanWs.entryModulePath),
        tabNavIndex = 0,
        fieldValues = cleanFiles.associate { it.path to TextFieldValue(it.content) },
      )).copy(
        importedSnapshot = cleanWs,
        workingWorkspace = cleanWs,
        stagedSecrets = emptyMap(),
        deletedSecrets = emptySet(),
        hasPendingConfigSync = false,
        isNewUnexportedWorker = false,
        lastExportedAtEpochMs = now,
      )
    workspaces[key] = updated
    publishStats()
    return updated
  }

  fun removeWorkspace(key: String) {
    workspaces.remove(key)
    undoStacksByWorkspace.remove(key)
    redoStacksByWorkspace.remove(key)
    if (activeKey == key) activeKey = null
    publishStats()
  }

  // Per-workspace in-RAM undo/redo stacks
  fun recordUndoSnapshot(key: String, filePath: String, previousValue: TextFieldValue) {
    val map = undoStacksByWorkspace.getOrPut(key) { mutableMapOf() }
    val stack = map.getOrPut(filePath) { ArrayDeque() }
    if (stack.lastOrNull()?.text != previousValue.text) {
      stack.addLast(previousValue)
      if (stack.size > 60) stack.removeFirst()
    }
    redoStacksByWorkspace[key]?.get(filePath)?.clear()
  }

  fun undo(key: String, filePath: String, currentValue: TextFieldValue): TextFieldValue? {
    val stack = undoStacksByWorkspace[key]?.get(filePath) ?: return null
    if (stack.isEmpty()) return null
    val redoMap = redoStacksByWorkspace.getOrPut(key) { mutableMapOf() }
    redoMap.getOrPut(filePath) { ArrayDeque() }.addLast(currentValue)
    return stack.removeLast()
  }

  fun redo(key: String, filePath: String, currentValue: TextFieldValue): TextFieldValue? {
    val stack = redoStacksByWorkspace[key]?.get(filePath) ?: return null
    if (stack.isEmpty()) return null
    val undoMap = undoStacksByWorkspace.getOrPut(key) { mutableMapOf() }
    undoMap.getOrPut(filePath) { ArrayDeque() }.addLast(currentValue)
    return stack.removeLast()
  }

  // In-RAM account infrastructure snapshot
  fun putInfraCache(cache: RamAccountInfraCache) {
    infraCaches[cache.accountId] = cache
  }

  fun getInfraCache(accountId: String): RamAccountInfraCache? = infraCaches[accountId]

  fun updateInfraCache(accountId: String, transform: (RamAccountInfraCache) -> RamAccountInfraCache) {
    val current = infraCaches[accountId] ?: RamAccountInfraCache(accountId = accountId)
    infraCaches[accountId] = transform(current)
  }

  // In-RAM Worker test run history
  @Synchronized
  fun recordWorkerTestRun(record: WorkerTestRunRecord) {
    workerTestHistory.add(0, record)
    if (workerTestHistory.size > 40) {
      workerTestHistory.subList(40, workerTestHistory.size).clear()
    }
  }

  @Synchronized
  fun getWorkerTestHistory(): List<WorkerTestRunRecord> = workerTestHistory.toList()

  @Synchronized
  fun clearWorkerTestHistory() {
    workerTestHistory.clear()
  }

  // Portable Workspace Bundle Export / Import (JSON)
  fun exportWorkspaceBundleJson(key: String): Result<String> =
    runCatching {
      val record = workspaces[key] ?: error("No active workspace in RAM database")
      val ws = record.workingWorkspace
      val root = JSONObject()
      root.put("format", "edgecode-ram-workspace-v1")
      root.put("exportedAtEpochMs", System.currentTimeMillis())
      root.put("scriptName", ws.scriptName)
      root.put("isEsModuleFormat", ws.isEsModuleFormat)
      root.put("entryModulePath", ws.entryModulePath)
      root.put("compatibilityDate", ws.compatibilityDate)
      root.put("compatibilityFlags", JSONArray(ws.compatibilityFlags))
      root.put("workersDevEnabled", ws.workersDevEnabled)
      root.put("directories", JSONArray(ws.directories.toList()))
      root.put("schedules", JSONArray(ws.schedules.map { it.cron }))

      val bindingsArr = JSONArray()
      ws.bindings.forEach { b ->
        bindingsArr.put(
          JSONObject().apply {
            put("name", b.name)
            put("type", b.type)
            b.text?.let { put("text", it) }
            b.namespaceId?.let { put("namespace_id", it) }
            b.bucketName?.let { put("bucket_name", it) }
            b.databaseId?.let { put("database_id", it) }
            b.service?.let { put("service", it) }
            b.environment?.let { put("environment", it) }
            b.className?.let { put("class_name", it) }
            b.queueName?.let { put("queue_name", it) }
          },
        )
      }
      root.put("bindings", bindingsArr)

      val filesArr = JSONArray()
      ws.files.filterNot { it.isVirtualReadOnly }.forEach { f ->
        filesArr.put(
          JSONObject().apply {
            put("path", f.path)
            put("content", f.content)
            put("contentType", f.contentType)
            put("isEntryPoint", f.isEntryPoint)
          },
        )
      }
      root.put("files", filesArr)
      root.toString(2)
    }

  fun importWorkspaceBundleJson(rawJson: String): Result<WorkerWorkspace> =
    runCatching {
      val root = JSONObject(rawJson)
      val scriptName = root.optString("scriptName").ifBlank { "imported-worker" }
      val entryModule = root.optString("entryModulePath").ifBlank { "src/index.js" }
      val compatDate = root.optString("compatibilityDate").ifBlank { "2024-11-01" }
      val isEsModule = root.optBoolean("isEsModuleFormat", true)
      val workersDev = root.optBoolean("workersDevEnabled", true)

      val flags = mutableListOf<String>()
      root.optJSONArray("compatibilityFlags")?.let { arr ->
        for (i in 0 until arr.length()) {
          arr.optString(i).takeIf { it.isNotBlank() }?.let { flags.add(it) }
        }
      }

      val dirs = mutableSetOf<String>()
      root.optJSONArray("directories")?.let { arr ->
        for (i in 0 until arr.length()) {
          arr.optString(i).takeIf { it.isNotBlank() }?.let { dirs.add(it) }
        }
      }

      val schedules = mutableListOf<CfCronSchedule>()
      root.optJSONArray("schedules")?.let { arr ->
        for (i in 0 until arr.length()) {
          arr.optString(i).takeIf { it.isNotBlank() }?.let { schedules.add(CfCronSchedule(cron = it)) }
        }
      }

      val bindings = mutableListOf<CfWorkerBinding>()
      root.optJSONArray("bindings")?.let { arr ->
        for (i in 0 until arr.length()) {
          val b = arr.optJSONObject(i) ?: continue
          val name = b.optString("name").takeIf { it.isNotBlank() } ?: continue
          bindings.add(
            CfWorkerBinding(
              name = name,
              type = b.optString("type", "plain_text"),
              text = b.optString("text").takeIf { b.has("text") },
              namespaceId = b.optString("namespace_id").takeIf { b.has("namespace_id") },
              bucketName = b.optString("bucket_name").takeIf { b.has("bucket_name") },
              databaseId = b.optString("database_id").takeIf { b.has("database_id") },
              service = b.optString("service").takeIf { b.has("service") },
              environment = b.optString("environment").takeIf { b.has("environment") },
              className = b.optString("class_name").takeIf { b.has("class_name") },
              queueName = b.optString("queue_name").takeIf { b.has("queue_name") },
            ),
          )
        }
      }

      val files = mutableListOf<WorkerFile>()
      val filesArr = root.optJSONArray("files") ?: error("Bundle contains no files array")
      for (i in 0 until filesArr.length()) {
        val f = filesArr.optJSONObject(i) ?: continue
        val path = f.optString("path").takeIf { it.isNotBlank() } ?: continue
        val content = f.optString("content")
        val contentType = f.optString("contentType").ifBlank { MultipartWorkerParser.inferModuleMimeType(path) }
        val isEntry = f.optBoolean("isEntryPoint", path == entryModule)
        files.add(
          WorkerFile(
            path = path,
            content = content,
            originalContent = content,
            contentType = contentType,
            isEntryPoint = isEntry,
          ),
        )
      }
      require(files.isNotEmpty()) { "Imported workspace bundle contains no files" }
      val resolvedEntry = files.firstOrNull { it.isEntryPoint }?.path ?: files.first().path

      WorkerWorkspace(
        scriptName = scriptName,
        isEsModuleFormat = isEsModule,
        entryModulePath = resolvedEntry,
        compatibilityDate = compatDate,
        compatibilityFlags = flags,
        files = files.map { it.copy(isEntryPoint = it.path == resolvedEntry) },
        directories = dirs,
        bindings = bindings,
        routes = emptyList(),
        schedules = schedules,
        workersDevEnabled = workersDev,
        accountSubdomain = "",
        hasModuleSetChanged = true,
      )
    }

  fun computeStats(overrideActiveKey: String? = activeKey): RamDatabaseStats {
    val all = workspaces.values.toList()
    val totalFiles = all.sumOf { it.workingWorkspace.files.count { f -> !f.isVirtualReadOnly } }
    val dirtyFiles = all.sumOf { it.workingWorkspace.files.count { f -> f.isDirty } }
    val pendingConfigs =
      all.count {
        it.hasPendingConfigSync ||
          it.stagedSecrets.isNotEmpty() ||
          it.deletedSecrets.isNotEmpty() ||
          it.isNewUnexportedWorker ||
          it.workingWorkspace.hasModuleSetChanged
      }
    val totalBytes =
      all.sumOf { rec ->
        rec.workingWorkspace.files.sumOf { it.content.length * 2 } +
          rec.importedSnapshot.files.sumOf { it.content.length * 2 }
      }
    val activeRecord = overrideActiveKey?.let { workspaces[it] }
    return RamDatabaseStats(
      workspacesInRam = all.size,
      totalFilesInRam = totalFiles,
      dirtyFilesCount = dirtyFiles,
      pendingConfigChanges = pendingConfigs,
      estimatedRamKb = ((totalBytes + 1023) / 1024),
      activeWorkspaceKey = overrideActiveKey,
      lastImportEpochMs = activeRecord?.importedAtEpochMs,
      lastExportEpochMs = activeRecord?.lastExportedAtEpochMs,
    )
  }

  fun clearAll() {
    workspaces.clear()
    infraCaches.clear()
    undoStacksByWorkspace.clear()
    redoStacksByWorkspace.clear()
    workerTestHistory.clear()
    activeKey = null
    publishStats()
  }

  private fun publishStats() {
    _stats.value = computeStats(activeKey)
  }
}
