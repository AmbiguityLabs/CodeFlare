package com.example.data.local

import android.content.Context
import android.os.Environment
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.remote.CfCronSchedule
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ProjectStorageSummary(
  val rootDisplayPath: String = "EdgeCodeProjects",
  val rootAbsolutePath: String = "",
  val hasNativeStorageAccess: Boolean = false,
  val isUsingNativeDeviceStorage: Boolean = false,
  val projectCount: Int = 0,
  val exportedFilesCount: Int = 0,
  val importedFilesCount: Int = 0,
  val totalDiskKb: Int = 0,
)

/**
 * Dedicated file-system storage for EdgeCode workspaces, imports, and exports.
 *
 * When native phone file management permission is granted, uses `/storage/emulated/0/EdgeCode/`
 * directly on device storage so every project lives reliably on disk outside app cache/RAM:
 *  - `EdgeCode/projects/<workspaceKey>/` -> active workspace source tree + `.edgecode/manifest.json`
 *  - `EdgeCode/imports/<projectName>/`  -> organized imported files & bundles
 *  - `EdgeCode/exports/<projectName>/`  -> organized exported files & bundles
 *
 * Falls back cleanly to app-scoped `EdgeCodeProjects/` until permission is granted.
 */
class ProjectDirectoryManager(private val context: Context) {

  @Volatile
  var rootDir: File = resolveDedicatedRootDir(context)
    private set

  val projectsDir: File
    get() = File(rootDir, "projects").apply { mkdirs() }

  val exportsDir: File
    get() = File(rootDir, "exports").apply { mkdirs() }

  val importsDir: File
    get() = File(rootDir, "imports").apply { mkdirs() }

  private val ioMutex = Mutex()
  private val writtenFileHashesByProject = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
  private val writtenManifestDigestByProject = ConcurrentHashMap<String, Int>()

  fun isUsingNativeDeviceStorage(): Boolean =
    StoragePermissionHelper.hasNativeFileAccess(context) && rootDir.name == NATIVE_FOLDER_NAME

  fun displayRootPath(): String {
    if (isUsingNativeDeviceStorage()) {
      return "Phone Storage/$NATIVE_FOLDER_NAME"
    }
    val baseName = rootDir.parentFile?.name?.takeIf { it.isNotBlank() }
    return if (baseName != null) "$baseName/${rootDir.name}" else rootDir.name
  }

  fun projectDirectoryFor(workspaceKey: String): File =
    File(projectsDir, sanitizeSegment(workspaceKey)).apply { mkdirs() }

  suspend fun saveSingleFile(
    workspaceKey: String,
    file: WorkerFile,
  ) =
    withContext(Dispatchers.IO) {
      if (file.isVirtualReadOnly) return@withContext
      ioMutex.withLock {
        val projDir = projectDirectoryFor(workspaceKey)
        val cleanPath = normalizeRelativePath(file.path, fallbackFolder = "")
        if (cleanPath.isBlank()) return@withLock
        val dest = File(projDir, cleanPath)
        val hash = file.content.hashCode()
        val fileHashes = writtenFileHashesByProject.getOrPut(workspaceKey) { ConcurrentHashMap() }
        if (fileHashes[cleanPath] != hash || !dest.exists()) {
          dest.parentFile?.mkdirs()
          dest.writeText(file.content, Charsets.UTF_8)
          fileHashes[cleanPath] = hash
        }
      }
    }

  suspend fun refreshRootDirectoryAndMigrate(): Boolean =
    withContext(Dispatchers.IO) {
      ioMutex.withLock {
        val targetRoot = resolveDedicatedRootDir(context)
        val oldRoot = rootDir
        if (targetRoot.absolutePath == oldRoot.absolutePath) {
          return@withLock false
        }
        listOf("projects", "imports", "exports").forEach { sub ->
          val srcDir = File(oldRoot, sub)
          val dstDir = File(targetRoot, sub).apply { mkdirs() }
          if (srcDir.exists() && srcDir.isDirectory) {
            copyDirectoryPreferNewer(srcDir, dstDir)
          }
        }
        rootDir = targetRoot
        writtenFileHashesByProject.clear()
        writtenManifestDigestByProject.clear()
        true
      }
    }

  private fun copyDirectoryPreferNewer(srcDir: File, dstDir: File) {
    srcDir.walkTopDown().forEach { src ->
      val rel = src.relativeTo(srcDir).path
      if (rel.isEmpty()) return@forEach
      val dst = File(dstDir, rel)
      if (src.isDirectory) {
        dst.mkdirs()
      } else if (src.isFile) {
        dst.parentFile?.mkdirs()
        if (!dst.exists() || src.lastModified() > dst.lastModified()) {
          runCatching { src.copyTo(dst, overwrite = true) }
        }
      }
    }
  }

  suspend fun saveWorkspaceRecord(record: RamWorkspaceRecord) =
    withContext(Dispatchers.IO) {
      ioMutex.withLock {
        saveWorkspaceRecordLocked(record)
      }
    }

  private fun saveWorkspaceRecordLocked(record: RamWorkspaceRecord) {
    val key = record.key
    val projDir = projectDirectoryFor(key)
    val working = record.workingWorkspace
    val editableFiles = organizeWorkspaceFiles(working.files.filterNot { it.isVirtualReadOnly })
    val fileHashes = writtenFileHashesByProject.getOrPut(key) { ConcurrentHashMap() }

    val activeRelativePaths = editableFiles.map { it.path }.toHashSet()
    // Remove deleted module files cleanly while preserving .edgecode/ and .git/
    projDir.walkTopDown().filter { it.isFile }.forEach { existing ->
      val rel = existing.relativeTo(projDir).path.replace('\\', '/')
      if (!rel.startsWith(".edgecode/") && !rel.startsWith(".git/") && rel !in activeRelativePaths) {
        existing.delete()
        fileHashes.remove(rel)
      }
    }

    // Incrementally write only changed files
    for (wf in editableFiles) {
      val dest = File(projDir, wf.path)
      val hash = wf.content.hashCode()
      if (fileHashes[wf.path] != hash || !dest.exists()) {
        dest.parentFile?.mkdirs()
        dest.writeText(wf.content, Charsets.UTF_8)
        fileHashes[wf.path] = hash
      }
    }

    // Ensure declared empty directories also exist cleanly on disk
    working.directories.forEach { dir ->
      val cleanDir = normalizeRelativePath(dir, fallbackFolder = "")
      if (cleanDir.isNotBlank()) {
        File(projDir, cleanDir).mkdirs()
      }
    }

    val manifestJson = serializeManifest(record, editableFiles)
    val manifestDigest = manifestJson.hashCode()
    val metaDir = File(projDir, ".edgecode").apply { mkdirs() }
    val manifestFile = File(metaDir, "manifest.json")
    if (writtenManifestDigestByProject[key] != manifestDigest || !manifestFile.exists()) {
      val tmpFile = File(metaDir, "manifest.json.tmp")
      tmpFile.writeText(manifestJson, Charsets.UTF_8)
      if (!tmpFile.renameTo(manifestFile)) {
        manifestFile.writeText(manifestJson, Charsets.UTF_8)
        tmpFile.delete()
      }
      writtenManifestDigestByProject[key] = manifestDigest
    }
  }

  suspend fun loadAllWorkspaces(): List<RamWorkspaceRecord> =
    withContext(Dispatchers.IO) {
      ioMutex.withLock {
        val folders = projectsDir.listFiles { f -> f.isDirectory } ?: return@withContext emptyList()
        val loaded = mutableListOf<RamWorkspaceRecord>()
        for (projDir in folders) {
          val manifestFile = File(projDir, ".edgecode/manifest.json")
          if (!manifestFile.exists()) continue
          runCatching {
            val record = deserializeManifest(projDir, manifestFile.readText(Charsets.UTF_8))
            if (record != null) {
              loaded.add(record)
              val map = ConcurrentHashMap<String, Int>()
              record.workingWorkspace.files
                .filterNot { it.isVirtualReadOnly }
                .forEach { f -> map[f.path] = f.content.hashCode() }
              writtenFileHashesByProject[record.key] = map
            }
          }
        }
        loaded.sortedByDescending { it.importedAtEpochMs }
      }
    }

  suspend fun deleteWorkspaceDirectory(workspaceKey: String) =
    withContext(Dispatchers.IO) {
      ioMutex.withLock {
        writtenFileHashesByProject.remove(workspaceKey)
        writtenManifestDigestByProject.remove(workspaceKey)
        val dir = File(projectsDir, sanitizeSegment(workspaceKey))
        if (dir.exists()) {
          dir.deleteRecursively()
        }
      }
    }

  suspend fun recordOrganizedImport(
    projectName: String,
    files: List<WorkerFile>,
    bundleJson: String? = null,
  ): File =
    withContext(Dispatchers.IO) {
      ioMutex.withLock {
        val dir = File(importsDir, sanitizeSegment(projectName)).apply { mkdirs() }
        files.filterNot { it.isVirtualReadOnly }.forEach { wf ->
          val cleanPath = normalizeRelativePath(wf.path, fallbackFolder = "")
          if (cleanPath.isNotBlank()) {
            val dest = File(dir, cleanPath)
            dest.parentFile?.mkdirs()
            dest.writeText(wf.content, Charsets.UTF_8)
          }
        }
        if (!bundleJson.isNullOrBlank()) {
          val bundleFile = File(dir, "${sanitizeSegment(projectName)}-bundle.json")
          bundleFile.writeText(bundleJson, Charsets.UTF_8)
        }
        dir
      }
    }

  suspend fun recordOrganizedExportFile(
    projectName: String,
    relativePath: String,
    content: String,
  ): File =
    withContext(Dispatchers.IO) {
      ioMutex.withLock {
        val projectExportDir = File(exportsDir, sanitizeSegment(projectName)).apply { mkdirs() }
        val cleanPath = normalizeRelativePath(relativePath, fallbackFolder = "")
        val dest = File(projectExportDir, cleanPath.ifBlank { "module.js" })
        dest.parentFile?.mkdirs()
        dest.writeText(content, Charsets.UTF_8)
        dest
      }
    }

  suspend fun recordOrganizedExportBundle(
    projectName: String,
    bundleJson: String,
  ): File =
    withContext(Dispatchers.IO) {
      ioMutex.withLock {
        val projectExportDir = File(exportsDir, sanitizeSegment(projectName)).apply { mkdirs() }
        val dest = File(projectExportDir, "${sanitizeSegment(projectName)}-bundle.json")
        dest.writeText(bundleJson, Charsets.UTF_8)
        dest
      }
    }

  suspend fun computeStorageSummary(): ProjectStorageSummary =
    withContext(Dispatchers.IO) {
      val projCount = projectsDir.listFiles { f -> f.isDirectory && File(f, ".edgecode/manifest.json").exists() }?.size ?: 0
      val expCount = countFiles(exportsDir)
      val impCount = countFiles(importsDir)
      val totalBytes = sumFileBytes(rootDir)
      ProjectStorageSummary(
        rootDisplayPath = displayRootPath(),
        rootAbsolutePath = rootDir.absolutePath,
        hasNativeStorageAccess = StoragePermissionHelper.hasNativeFileAccess(context),
        isUsingNativeDeviceStorage = isUsingNativeDeviceStorage(),
        projectCount = projCount,
        exportedFilesCount = expCount,
        importedFilesCount = impCount,
        totalDiskKb = ((totalBytes + 1023L) / 1024L).toInt(),
      )
    }

  private fun countFiles(dir: File): Int {
    if (!dir.exists()) return 0
    return dir.walkTopDown().count { it.isFile && !it.name.endsWith(".tmp") }
  }

  private fun sumFileBytes(dir: File): Long {
    if (!dir.exists()) return 0L
    var sum = 0L
    dir.walkTopDown().forEach { f ->
      if (f.isFile) sum += f.length()
    }
    return sum
  }

  private fun serializeManifest(
    record: RamWorkspaceRecord,
    editableFiles: List<WorkerFile>,
  ): String {
    val ws = record.workingWorkspace
    val base = record.importedSnapshot
    val root = JSONObject()
    root.put("version", 1)
    root.put("key", record.key)
    root.put("scriptName", ws.scriptName)
    root.put("isEsModuleFormat", ws.isEsModuleFormat)
    root.put("entryModulePath", ws.entryModulePath)
    root.put("compatibilityDate", ws.compatibilityDate)
    root.put("compatibilityFlags", JSONArray(ws.compatibilityFlags))
    root.put("directories", JSONArray(ws.directories.toList().sorted()))
    root.put("workersDevEnabled", ws.workersDevEnabled)
    root.put("accountSubdomain", ws.accountSubdomain)
    root.put("hasModuleSetChanged", ws.hasModuleSetChanged)
    root.put("hasPendingConfigSync", record.hasPendingConfigSync)
    root.put("isNewUnexportedWorker", record.isNewUnexportedWorker)
    root.put("importedAtEpochMs", record.importedAtEpochMs)
    record.lastExportedAtEpochMs?.let { root.put("lastExportedAtEpochMs", it) }
    root.put("openTabPaths", JSONArray(record.openTabPaths))
    root.put("activeFilePath", record.activeFilePath.orEmpty())
    root.put("tabNavHistory", JSONArray(record.tabNavHistory))
    root.put("tabNavIndex", record.tabNavIndex)
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
          b.indexName?.let { put("index_name", it) }
        },
      )
    }
    root.put("bindings", bindingsArr)

    val selectionsObj = JSONObject()
    record.fieldValues.forEach { (path, tfv) ->
      selectionsObj.put(
        path,
        JSONArray().apply {
          put(tfv.selection.start)
          put(tfv.selection.end)
        },
      )
    }
    root.put("cursorSelections", selectionsObj)

    val filesMetaArr = JSONArray()
    editableFiles.forEach { wf ->
      filesMetaArr.put(
        JSONObject().apply {
          put("path", wf.path)
          put("contentType", wf.contentType)
          put("isEntryPoint", wf.isEntryPoint)
          put("originalContent", wf.originalContent)
        },
      )
    }
    root.put("filesMeta", filesMetaArr)

    val baselineFilesArr = JSONArray()
    base.files.filterNot { it.isVirtualReadOnly }.forEach { bf ->
      baselineFilesArr.put(
        JSONObject().apply {
          put("path", bf.path)
          put("content", bf.content)
          put("contentType", bf.contentType)
          put("isEntryPoint", bf.isEntryPoint)
        },
      )
    }
    root.put("baselineFiles", baselineFilesArr)

    return root.toString()
  }

  private fun deserializeManifest(
    projDir: File,
    rawJson: String,
  ): RamWorkspaceRecord? {
    val root = JSONObject(rawJson)
    val key = root.optString("key").takeIf { it.isNotBlank() } ?: return null
    val scriptName = root.optString("scriptName").ifBlank { projDir.name }
    val entryModulePath = root.optString("entryModulePath").ifBlank { "src/index.js" }
    val compatibilityDate = root.optString("compatibilityDate").ifBlank { "2024-11-01" }
    val isEsModuleFormat = root.optBoolean("isEsModuleFormat", true)
    val workersDevEnabled = root.optBoolean("workersDevEnabled", false)
    val accountSubdomain = root.optString("accountSubdomain")
    val hasModuleSetChanged = root.optBoolean("hasModuleSetChanged", false)
    val hasPendingConfigSync = root.optBoolean("hasPendingConfigSync", false)
    val isNewUnexported = root.optBoolean("isNewUnexportedWorker", false)
    val importedAt = root.optLong("importedAtEpochMs", System.currentTimeMillis())
    val lastExported = if (root.has("lastExportedAtEpochMs")) root.optLong("lastExportedAtEpochMs") else null

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
            indexName = b.optString("index_name").takeIf { b.has("index_name") },
          ),
        )
      }
    }

    val files = mutableListOf<WorkerFile>()
    val filesMetaArr = root.optJSONArray("filesMeta")
    val metaPaths = hashSetOf<String>()
    if (filesMetaArr != null) {
      for (i in 0 until filesMetaArr.length()) {
        val obj = filesMetaArr.optJSONObject(i) ?: continue
        val path = obj.optString("path").takeIf { it.isNotBlank() } ?: continue
        metaPaths.add(path)
        val diskFile = File(projDir, path)
        val original = obj.optString("originalContent")
        val content =
          if (diskFile.exists() && diskFile.isFile) {
            runCatching { diskFile.readText(Charsets.UTF_8) }.getOrDefault(original)
          } else {
            original
          }
        val contentType = obj.optString("contentType").ifBlank { MultipartWorkerParser.inferModuleMimeType(path) }
        val isEntry = obj.optBoolean("isEntryPoint", path == entryModulePath)
        files.add(
          WorkerFile(
            path = path,
            content = content,
            originalContent = original,
            contentType = contentType,
            isEntryPoint = isEntry,
          ),
        )
      }
    }

    // Pick up any newly placed non-metadata files on disk inside project directory
    projDir.walkTopDown().filter { it.isFile }.forEach { diskFile ->
      val rel = diskFile.relativeTo(projDir).path.replace('\\', '/')
      if (!rel.startsWith(".edgecode/") && !rel.startsWith(".git/") && rel !in metaPaths) {
        val content = runCatching { diskFile.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach
        files.add(
          WorkerFile(
            path = rel,
            content = content,
            originalContent = "",
            contentType = MultipartWorkerParser.inferModuleMimeType(rel),
            isEntryPoint = false,
          ),
        )
      }
    }

    if (files.isEmpty()) return null
    val organizedFiles = organizeWorkspaceFiles(files)

    val baselineFiles = mutableListOf<WorkerFile>()
    root.optJSONArray("baselineFiles")?.let { arr ->
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val path = obj.optString("path").takeIf { it.isNotBlank() } ?: continue
        val content = obj.optString("content")
        baselineFiles.add(
          WorkerFile(
            path = path,
            content = content,
            originalContent = content,
            contentType = obj.optString("contentType").ifBlank { MultipartWorkerParser.inferModuleMimeType(path) },
            isEntryPoint = obj.optBoolean("isEntryPoint", path == entryModulePath),
          ),
        )
      }
    }

    val openTabs = mutableListOf<String>()
    root.optJSONArray("openTabPaths")?.let { arr ->
      for (i in 0 until arr.length()) {
        arr.optString(i).takeIf { p -> p.isNotBlank() && organizedFiles.any { it.path == p } }?.let { openTabs.add(it) }
      }
    }
    if (openTabs.isEmpty()) {
      openTabs.add(organizedFiles.firstOrNull { it.isEntryPoint }?.path ?: organizedFiles.first().path)
    }

    val activeFile =
      root.optString("activeFilePath").takeIf { ap -> ap.isNotBlank() && organizedFiles.any { it.path == ap } }
        ?: openTabs.firstOrNull()

    val navHistory = mutableListOf<String>()
    root.optJSONArray("tabNavHistory")?.let { arr ->
      for (i in 0 until arr.length()) {
        arr.optString(i).takeIf { p -> p.isNotBlank() && organizedFiles.any { it.path == p } }?.let { navHistory.add(it) }
      }
    }
    if (navHistory.isEmpty() && activeFile != null) navHistory.add(activeFile)
    val navIndex =
      root.optInt("tabNavIndex", navHistory.lastIndex).coerceIn(-1, (navHistory.size - 1).coerceAtLeast(-1))

    val selections = mutableMapOf<String, Pair<Int, Int>>()
    root.optJSONObject("cursorSelections")?.let { selObj ->
      val keys = selObj.keys()
      while (keys.hasNext()) {
        val p = keys.next()
        val arr = selObj.optJSONArray(p) ?: continue
        if (arr.length() >= 2) {
          selections[p] = arr.optInt(0, 0) to arr.optInt(1, 0)
        }
      }
    }

    val fieldValues =
      organizedFiles.associate { wf ->
        val sel = selections[wf.path]
        val range =
          if (sel != null) {
            TextRange(sel.first.coerceIn(0, wf.content.length), sel.second.coerceIn(0, wf.content.length))
          } else {
            TextRange.Zero
          }
        wf.path to TextFieldValue(wf.content, range)
      }

    val workingWs =
      WorkerWorkspace(
        scriptName = scriptName,
        isEsModuleFormat = isEsModuleFormat,
        entryModulePath = entryModulePath,
        compatibilityDate = compatibilityDate,
        compatibilityFlags = flags,
        files = organizedFiles,
        directories = dirs,
        bindings = bindings,
        routes = emptyList(),
        schedules = schedules,
        workersDevEnabled = workersDevEnabled,
        accountSubdomain = accountSubdomain,
        hasModuleSetChanged = hasModuleSetChanged,
      )

    val baselineWs =
      if (baselineFiles.isNotEmpty()) {
        workingWs.copy(files = organizeWorkspaceFiles(baselineFiles), hasModuleSetChanged = false)
      } else {
        workingWs
      }

    return RamWorkspaceRecord(
      key = key,
      importedSnapshot = baselineWs,
      workingWorkspace = workingWs,
      openTabPaths = openTabs,
      activeFilePath = activeFile,
      tabNavHistory = navHistory,
      tabNavIndex = navIndex,
      fieldValues = fieldValues,
      hasPendingConfigSync = hasPendingConfigSync,
      isNewUnexportedWorker = isNewUnexported,
      importedAtEpochMs = importedAt,
      lastExportedAtEpochMs = lastExported,
    )
  }

  companion object {
    const val NATIVE_FOLDER_NAME = "EdgeCode"

    fun resolveDedicatedRootDir(context: Context): File {
      if (StoragePermissionHelper.hasNativeFileAccess(context)) {
        val mounted =
          runCatching { Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED }.getOrDefault(false)
        if (mounted) {
          val nativeRoot =
            runCatching {
              @Suppress("DEPRECATION")
              val baseExt = Environment.getExternalStorageDirectory()
              File(baseExt, NATIVE_FOLDER_NAME)
            }.getOrNull()
          if (nativeRoot != null) {
            val writable =
              runCatching {
                nativeRoot.mkdirs()
                File(nativeRoot, "projects").mkdirs()
                val probe = File(nativeRoot, ".edgecode_write_probe")
                probe.writeText("ok")
                probe.delete()
                nativeRoot.exists() && nativeRoot.isDirectory && nativeRoot.canWrite()
              }.getOrDefault(false)
            if (writable) {
              return nativeRoot
            }
          }
        }
      }

      val externalAppDir =
        runCatching {
          val state = Environment.getExternalStorageState()
          if (state == Environment.MEDIA_MOUNTED) context.getExternalFilesDir(null) else null
        }.getOrNull()
      val base = externalAppDir ?: context.filesDir
      return File(base, "EdgeCodeProjects").apply { mkdirs() }
    }

    fun sanitizeSegment(raw: String): String =
      raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "workspace" }

    /**
     * Normalizes user-provided or imported relative file paths to prevent traversal (`..`),
     * collapse redundant separators, and keep project trees cleanly structured.
     */
    fun normalizeRelativePath(
      rawPath: String,
      fallbackFolder: String = "",
    ): String {
      val cleanedSegments =
        rawPath
          .replace('\\', '/')
          .split('/')
          .map { it.trim() }
          .filter { it.isNotBlank() && it != "." && it != ".." && !it.startsWith(".edgecode") }
      if (cleanedSegments.isEmpty()) return ""
      val normalized = cleanedSegments.joinToString("/")
      val prefix =
        fallbackFolder
          .replace('\\', '/')
          .trim('/')
      return if (prefix.isNotBlank() && !normalized.contains('/')) {
        "$prefix/$normalized"
      } else {
        normalized
      }
    }

    /**
     * Sorts and deduplicates workspace files cleanly:
     * 1) Entry module & nested source folders (`src/...`, `lib/...`, etc.) sorted cleanly
     * 2) Root config files (`wrangler.jsonc`, `package.json`, `.gitignore`) kept tidy at the root level.
     */
    fun organizeWorkspaceFiles(files: List<WorkerFile>): List<WorkerFile> {
      val deduped = linkedMapOf<String, WorkerFile>()
      for (f in files) {
        if (f.isVirtualReadOnly) {
          deduped[f.path] = f
          continue
        }
        val cleanPath = normalizeRelativePath(f.path, fallbackFolder = "")
        if (cleanPath.isBlank()) continue
        deduped[cleanPath] =
          f.copy(
            path = cleanPath,
            contentType = f.contentType.ifBlank { MultipartWorkerParser.inferModuleMimeType(cleanPath) },
          )
      }
      return deduped.values.sortedWith(
        compareBy<WorkerFile> { it.isVirtualReadOnly }
          .thenBy { !it.isEntryPoint }
          .thenBy { !it.path.contains('/') }
          .thenBy { it.path.lowercase() },
      )
    }
  }
}
