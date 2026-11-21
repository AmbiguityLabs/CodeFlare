package com.example.data.local

import com.example.data.remote.WorkerFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ZipExportSummary(
  val entryCount: Int,
  val textFileCount: Int,
  val binaryAssetCount: Int,
  val byteCount: Long,
)

object WorkspaceZipExporter {
  private const val CACHE_EXPORTS_DIR = "exports"
  private const val MAX_CACHED_ZIPS = 8

  fun sanitizeZipPath(rawPath: String): String? {
    val normalized = rawPath.replace('\\', '/').trim().trimStart('/')
    if (normalized.isBlank()) return null
    val segments = normalized.split('/').filter { it.isNotBlank() && it != "." }
    if (segments.isEmpty() || segments.any { it == ".." }) return null
    return segments.joinToString("/")
  }

  fun suggestZipFileName(projectName: String): String {
    val cleanBase =
      projectName
        .trim()
        .lowercase()
        .replace(Regex("""[^a-z0-9._-]+"""), "-")
        .trim('-', '.')
        .ifBlank { "codeflare-project" }
    return if (cleanBase.endsWith(".zip", ignoreCase = true)) cleanBase else "$cleanBase.zip"
  }

  fun exportToStream(
    files: List<WorkerFile>,
    outputStream: OutputStream,
    binaryAssets: Map<String, ByteArray> = emptyMap(),
    includeVirtualReadOnly: Boolean = false,
  ): Result<ZipExportSummary> = runCatching {
    val candidates =
      if (includeVirtualReadOnly) files else files.filterNot { it.isVirtualReadOnly }
    val seenPaths = LinkedHashSet<String>()
    var textCount = 0
    var binaryCount = 0
    var totalBytes = 0L

    ZipOutputStream(outputStream).use { zos ->
      candidates
        .mapNotNull { file ->
          val safePath = sanitizeZipPath(file.path) ?: return@mapNotNull null
          safePath to file
        }
        .sortedBy { it.first }
        .forEach { (safePath, file) ->
          if (!seenPaths.add(safePath)) return@forEach
          val bytes = file.content.toByteArray(Charsets.UTF_8)
          zos.putNextEntry(ZipEntry(safePath))
          zos.write(bytes)
          zos.closeEntry()
          textCount++
          totalBytes += bytes.size
        }

      binaryAssets.entries
        .mapNotNull { (rawPath, bytes) ->
          val safePath = sanitizeZipPath(rawPath) ?: return@mapNotNull null
          safePath to bytes
        }
        .sortedBy { it.first }
        .forEach { (safePath, bytes) ->
          if (!seenPaths.add(safePath)) return@forEach
          zos.putNextEntry(ZipEntry(safePath))
          zos.write(bytes)
          zos.closeEntry()
          binaryCount++
          totalBytes += bytes.size
        }
    }

    require(seenPaths.isNotEmpty()) { "Workspace has no exportable files" }
    ZipExportSummary(
      entryCount = seenPaths.size,
      textFileCount = textCount,
      binaryAssetCount = binaryCount,
      byteCount = totalBytes,
    )
  }

  fun exportToBytes(
    files: List<WorkerFile>,
    binaryAssets: Map<String, ByteArray> = emptyMap(),
    includeVirtualReadOnly: Boolean = false,
  ): Result<Pair<ByteArray, ZipExportSummary>> = runCatching {
    val bos = ByteArrayOutputStream()
    val summary = exportToStream(files, bos, binaryAssets, includeVirtualReadOnly).getOrThrow()
    bos.toByteArray() to summary
  }

  fun exportToCacheFile(
    cacheDir: File,
    projectName: String,
    files: List<WorkerFile>,
    binaryAssets: Map<String, ByteArray> = emptyMap(),
    includeVirtualReadOnly: Boolean = false,
  ): Result<File> = runCatching {
    val exportDir = File(cacheDir, CACHE_EXPORTS_DIR).apply { mkdirs() }
    val existing =
      exportDir.listFiles()?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()
    existing.drop(MAX_CACHED_ZIPS - 1).forEach { runCatching { it.delete() } }

    val fileName = suggestZipFileName(projectName)
    val outFile = File(exportDir, fileName)
    FileOutputStream(outFile).use { fos ->
      exportToStream(files, fos, binaryAssets, includeVirtualReadOnly).getOrThrow()
    }
    outFile
  }
}
