package com.example.data.local

import android.net.Uri
import com.example.data.remote.CfCronSchedule
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.CloudflareNamingUtils
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.WorkerFile
import com.example.editor.WranglerSyncParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

enum class ZipImportTargetMode {
  NEW_PROJECT,
  MERGE_INTO_ACTIVE,
}

data class ZipArchiveReadResult(
  val archiveName: String,
  val rawAssets: List<RawPickedAsset>,
  val commonRootFolder: String?,
  val skippedIgnoredCount: Int,
  val totalArchiveBytes: Long,
)

data class ZipImportPlan(
  val archiveName: String,
  val suggestedProjectName: String,
  val entryModulePath: String,
  val compatibilityDate: String?,
  val compatibilityFlags: List<String>,
  val workersDev: Boolean?,
  val bindings: List<CfWorkerBinding>,
  val schedules: List<CfCronSchedule>,
  val files: List<WorkerFile>,
  val commonRootFolder: String?,
  val stripRootFolder: Boolean,
  val skippedBinaryFiles: List<String>,
  val skippedLargeFiles: List<String>,
  val totalArchiveBytes: Long,
  val edgeCodeBundleJson: String? = null,
)

data class ZipPreviewState(
  val uri: Uri,
  val targetMode: ZipImportTargetMode,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val stripRootFolder: Boolean = true,
  val archiveReadResult: ZipArchiveReadResult? = null,
  val plan: ZipImportPlan? = null,
)

object ZipWorkspaceImporter {
  const val DEFAULT_MAX_ARCHIVE_BYTES = 500L * 1024L * 1024L
  const val DEFAULT_MAX_FILE_BYTES = 500L * 1024L * 1024L
  private const val MAX_EDITOR_TEXT_FILE_BYTES = 2L * 1024L * 1024L
  private const val DEFAULT_MAX_ENTRIES = 20_000
  private const val STREAM_BUFFER_BYTES = 64 * 1024

  private val IGNORED_SEGMENTS = setOf(".git", "node_modules", ".wrangler", "__MACOSX", ".idea", ".gradle")
  private val IGNORED_FILES = setOf(".DS_Store", "Thumbs.db", "desktop.ini")

  private val BINARY_EXTENSIONS =
    setOf(
      "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "heic", "avif", "tiff",
      "woff", "woff2", "ttf", "otf", "eot",
      "pdf", "zip", "gz", "tar", "tgz", "bz2", "xz", "7z", "rar",
      "exe", "dll", "so", "dylib", "a", "o", "wasm", "class", "jar", "apk", "aab", "dex",
      "mp3", "mp4", "m4a", "wav", "ogg", "webm", "avi", "mov", "mkv", "flac",
      "db", "sqlite", "sqlite3", "bin", "dat", "lockb", "pyc", "pyo",
    )

  private val ENTRY_CANDIDATES =
    listOf(
      "src/index.ts",
      "src/index.js",
      "src/index.mjs",
      "src/worker.ts",
      "src/worker.js",
      "src/main.ts",
      "src/main.js",
      "index.ts",
      "index.js",
      "index.mjs",
      "worker.ts",
      "worker.js",
      "main.ts",
      "main.js",
      "index.html",
    )

  fun isZipArchiveHeader(firstBytes: ByteArray): Boolean {
    if (firstBytes.size < 4) return false
    val b0 = firstBytes[0].toInt() and 0xFF
    val b1 = firstBytes[1].toInt() and 0xFF
    val b2 = firstBytes[2].toInt() and 0xFF
    val b3 = firstBytes[3].toInt() and 0xFF
    if (b0 != 0x50 || b1 != 0x4B) return false
    return (b2 == 0x03 && b3 == 0x04) ||
      (b2 == 0x05 && b3 == 0x06) ||
      (b2 == 0x07 && b3 == 0x08)
  }

  fun readZipStream(
    input: InputStream,
    archiveName: String,
    maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    maxEntries: Int = DEFAULT_MAX_ENTRIES,
    maxTotalBytes: Long = DEFAULT_MAX_ARCHIVE_BYTES,
    stagingDir: File? = null,
  ): Result<ZipArchiveReadResult> =
    runCatching {
      val rawEntries = mutableListOf<RawPickedAsset>()
      var skippedIgnored = 0
      var totalBytes = 0L
      val canonicalRoot =
        stagingDir?.let { dir ->
          dir.mkdirs()
          dir.canonicalPath + File.separator
        }
      val chunk = ByteArray(STREAM_BUFFER_BYTES)

      ZipInputStream(input.buffered(STREAM_BUFFER_BYTES)).use { zis ->
        while (true) {
          val entry = zis.nextEntry ?: break
          try {
            if (!entry.isDirectory) {
              val cleanSegments =
                entry.name
                  .replace('\\', '/')
                  .split('/')
                  .filter { it.isNotBlank() && it != "." }
              val hasTraversal = cleanSegments.any { it == ".." }
              val hasIgnored =
                cleanSegments.any { it in IGNORED_SEGMENTS } ||
                  cleanSegments.lastOrNull() in IGNORED_FILES
              if (hasTraversal || hasIgnored || cleanSegments.isEmpty()) {
                skippedIgnored++
              } else {
                if (entry.size > maxFileBytes) {
                  error("ZIP entry '${entry.name}' exceeds ${maxFileBytes / (1024 * 1024)} MiB limit")
                }
                val relPath = cleanSegments.joinToString("/")
                if (stagingDir != null && canonicalRoot != null) {
                  val targetFile = File(stagingDir, relPath)
                  if (!targetFile.canonicalPath.startsWith(canonicalRoot)) {
                    skippedIgnored++
                  } else {
                    targetFile.parentFile?.mkdirs()
                    var entryBytes = 0L
                    FileOutputStream(targetFile).use { fos ->
                      while (true) {
                        val read = zis.read(chunk)
                        if (read <= 0) break
                        entryBytes += read
                        totalBytes += read
                        if (entryBytes > maxFileBytes) {
                          error("ZIP entry '${entry.name}' exceeds ${maxFileBytes / (1024 * 1024)} MiB limit")
                        }
                        if (totalBytes > maxTotalBytes) {
                          error("ZIP archive exceeds ${maxTotalBytes / (1024 * 1024)} MiB uncompressed limit")
                        }
                        fos.write(chunk, 0, read)
                      }
                    }
                    rawEntries.add(
                      RawPickedAsset(
                        relativePath = relPath,
                        diskFile = targetFile,
                        byteSize = entryBytes,
                      ),
                    )
                  }
                } else {
                  val (bytes, entryBytes) = readBoundedInMemoryOrSample(zis, maxFileBytes, maxTotalBytes - totalBytes)
                  totalBytes += entryBytes
                  rawEntries.add(
                    RawPickedAsset(
                      relativePath = relPath,
                      bytes = bytes,
                      byteSize = entryBytes,
                    ),
                  )
                }
                if (rawEntries.size > maxEntries) {
                  error("ZIP archive exceeds $maxEntries files limit")
                }
              }
            }
          } finally {
            zis.closeEntry()
          }
        }
      }
      require(rawEntries.isNotEmpty()) { "ZIP archive contains no importable files" }
      val sorted = rawEntries.sortedBy { it.relativePath.lowercase() }
      ZipArchiveReadResult(
        archiveName = archiveName,
        rawAssets = sorted,
        commonRootFolder = detectCommonWrapperFolder(sorted),
        skippedIgnoredCount = skippedIgnored,
        totalArchiveBytes = totalBytes,
      )
    }

  fun detectCommonWrapperFolder(assets: List<RawPickedAsset>): String? {
    if (assets.isEmpty()) return null
    val firstSegments =
      assets.mapNotNull { asset ->
        if (!asset.relativePath.contains('/')) null
        else asset.relativePath.substringBefore('/').takeIf { it.isNotBlank() }
      }
    if (firstSegments.size != assets.size) return null
    val candidate = firstSegments.first()
    return if (firstSegments.all { it == candidate }) candidate else null
  }

  fun isBinaryAsset(
    path: String,
    bytes: ByteArray,
  ): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    if (ext in BINARY_EXTENSIONS) return true
    val inspectLen = bytes.size.coerceAtMost(4096)
    for (i in 0 until inspectLen) {
      if (bytes[i].toInt() == 0) return true
    }
    return false
  }

  fun buildImportPlan(
    readResult: ZipArchiveReadResult,
    stripCommonRootFolder: Boolean = true,
    defaultCompatibilityDate: String = "2024-11-01",
  ): Result<ZipImportPlan> =
    runCatching {
      val rootPrefix =
        if (stripCommonRootFolder) readResult.commonRootFolder?.let { "$it/" }
        else null

      val skippedBinary = mutableListOf<String>()
      val skippedLarge = mutableListOf<String>()
      val candidateFiles = mutableListOf<WorkerFile>()

      for (asset in readResult.rawAssets) {
        val unstripped = asset.relativePath
        val stripped =
          if (rootPrefix != null && unstripped.startsWith(rootPrefix)) {
            unstripped.removePrefix(rootPrefix)
          } else {
            unstripped
          }
        val cleanPath = ProjectDirectoryManager.normalizeRelativePath(stripped, fallbackFolder = "")
        if (cleanPath.isBlank()) continue

        val sampleBytes = asset.readSampleBytes(4096)
        if (isBinaryAsset(cleanPath, sampleBytes)) {
          skippedBinary.add(cleanPath)
          continue
        }
        if (asset.byteSize > MAX_EDITOR_TEXT_FILE_BYTES) {
          skippedLarge.add(cleanPath)
          continue
        }

        val text = asset.readBytes().toString(Charsets.UTF_8)
        candidateFiles.add(
          WorkerFile(
            path = cleanPath,
            content = text,
            originalContent = text,
            contentType = MultipartWorkerParser.inferModuleMimeType(cleanPath),
            isEntryPoint = false,
          ),
        )
      }

      require(candidateFiles.isNotEmpty()) {
        if (skippedBinary.isNotEmpty()) {
          "ZIP archive contains only binary assets (${skippedBinary.size} skipped)"
        } else {
          "ZIP archive contains no readable source files"
        }
      }

      val singleBundleJson = detectSingleEdgeCodeBundleJson(candidateFiles)

      val wranglerFile =
        candidateFiles.firstOrNull { it.path.equals("wrangler.jsonc", ignoreCase = true) }
          ?: candidateFiles.firstOrNull { it.path.equals("wrangler.json", ignoreCase = true) }
          ?: candidateFiles.firstOrNull { it.path.equals("wrangler.toml", ignoreCase = true) }
      val parsedWrangler =
        wranglerFile?.let { WranglerSyncParser.parse(it.path, it.content).getOrNull() }

      val packageJsonFile = candidateFiles.firstOrNull { it.path.equals("package.json", ignoreCase = true) }
      val packageMeta = packageJsonFile?.let { parsePackageJson(it.content) }

      val rawSuggestedName =
        parsedWrangler?.scriptName?.takeIf { it.isNotBlank() }
          ?: packageMeta?.first?.takeIf { it.isNotBlank() }
          ?: readResult.commonRootFolder?.takeIf { it.isNotBlank() }
          ?: readResult.archiveName.substringBeforeLast('.').takeIf { it.isNotBlank() }
          ?: "zip-project"
      val sanitizedName =
        CloudflareNamingUtils.sanitizeWorkerName(rawSuggestedName, allowUnderscore = true)
          .ifBlank { "zip-project" }

      val pathLookup = candidateFiles.associateBy { it.path }
      val wranglerMain = parsedWrangler?.main?.removePrefix("./")?.trimStart('/')
      val pkgMain = packageMeta?.second?.removePrefix("./")?.trimStart('/')

      val resolvedEntry =
        wranglerMain?.takeIf { it in pathLookup }
          ?: pkgMain?.takeIf { it in pathLookup }
          ?: ENTRY_CANDIDATES.firstOrNull { it in pathLookup }
          ?: candidateFiles.firstOrNull {
            val lower = it.path.lowercase()
            lower.endsWith(".ts") ||
              lower.endsWith(".tsx") ||
              lower.endsWith(".js") ||
              lower.endsWith(".mjs") ||
              lower.endsWith(".jsx")
          }?.path
          ?: candidateFiles.first().path

      val finalFiles =
        ProjectDirectoryManager.organizeWorkspaceFiles(
          candidateFiles.map { wf -> wf.copy(isEntryPoint = wf.path == resolvedEntry) },
        )

      ZipImportPlan(
        archiveName = readResult.archiveName,
        suggestedProjectName = sanitizedName,
        entryModulePath = resolvedEntry,
        compatibilityDate = parsedWrangler?.compatibilityDate ?: defaultCompatibilityDate,
        compatibilityFlags = parsedWrangler?.compatibilityFlags.orEmpty(),
        workersDev = parsedWrangler?.workersDev,
        bindings = parsedWrangler?.bindings.orEmpty(),
        schedules = parsedWrangler?.crons.orEmpty().map { CfCronSchedule(cron = it) },
        files = finalFiles,
        commonRootFolder = readResult.commonRootFolder,
        stripRootFolder = stripCommonRootFolder && readResult.commonRootFolder != null,
        skippedBinaryFiles = skippedBinary,
        skippedLargeFiles = skippedLarge,
        totalArchiveBytes = readResult.totalArchiveBytes,
        edgeCodeBundleJson = singleBundleJson,
      )
    }

  private fun detectSingleEdgeCodeBundleJson(files: List<WorkerFile>): String? {
    if (files.size != 1) return null
    val only = files.first()
    if (!only.path.endsWith(".json", ignoreCase = true)) return null
    return runCatching {
      val obj = JSONObject(only.content)
      val hasScript = obj.optString("scriptName").isNotBlank()
      val hasFiles = obj.optJSONArray("files") != null
      if (hasScript && hasFiles) only.content else null
    }.getOrNull()
  }

  private fun parsePackageJson(raw: String): Pair<String?, String?>? =
    runCatching {
      val obj = JSONObject(raw)
      val name = obj.optString("name").trim().substringAfterLast('/').takeIf { it.isNotBlank() }
      val main = obj.optString("main").trim().takeIf { it.isNotBlank() }
      name to main
    }.getOrNull()

  private fun readBoundedInMemoryOrSample(
    input: InputStream,
    maxFileBytes: Long,
    remainingArchiveBytes: Long,
  ): Pair<ByteArray, Long> {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(STREAM_BUFFER_BYTES)
    var total = 0L
    var exceededEditorTextCap = false
    while (true) {
      val read = input.read(chunk)
      if (read <= 0) break
      total += read
      if (total > maxFileBytes) {
        error("ZIP entry exceeds ${maxFileBytes / (1024 * 1024)} MiB per-file limit")
      }
      if (total > remainingArchiveBytes) {
        error("ZIP archive exceeds 500 MiB uncompressed limit")
      }
      if (!exceededEditorTextCap) {
        if (total <= MAX_EDITOR_TEXT_FILE_BYTES) {
          buffer.write(chunk, 0, read)
        } else {
          // Retain only first 4 KB sample in memory for binary detection; avoid OOM for huge entries
          val sample = buffer.toByteArray().copyOf(buffer.size().coerceAtMost(4096))
          buffer.reset()
          buffer.write(sample)
          exceededEditorTextCap = true
        }
      }
    }
    return buffer.toByteArray() to total
  }
}
