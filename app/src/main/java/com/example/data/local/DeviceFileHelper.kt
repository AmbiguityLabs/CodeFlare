package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedDeviceFile(
  val fileName: String,
  val content: String,
)

data class RawPickedAsset(
  val relativePath: String,
  val bytes: ByteArray = ByteArray(0),
  val diskFile: File? = null,
  val byteSize: Long = diskFile?.length() ?: bytes.size.toLong(),
) {
  fun readBytes(): ByteArray =
    diskFile?.let { file -> if (file.exists()) file.readBytes() else bytes } ?: bytes

  fun readSampleBytes(maxLen: Int = 4096): ByteArray {
    val file = diskFile
    if (file != null && file.exists()) {
      val len = file.length().coerceAtMost(maxLen.toLong()).toInt()
      if (len <= 0) return ByteArray(0)
      val buf = ByteArray(len)
      FileInputStream(file).use { fis ->
        var offset = 0
        while (offset < len) {
          val n = fis.read(buf, offset, len - offset)
          if (n <= 0) break
          offset += n
        }
        return if (offset == len) buf else buf.copyOf(offset)
      }
    }
    return if (bytes.size <= maxLen) bytes else bytes.copyOf(maxLen)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RawPickedAsset) return false
    return relativePath == other.relativePath &&
      byteSize == other.byteSize &&
      diskFile?.absolutePath == other.diskFile?.absolutePath &&
      bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int {
    var result = relativePath.hashCode()
    result = 31 * result + byteSize.hashCode()
    result = 31 * result + (diskFile?.absolutePath?.hashCode() ?: 0)
    result = 31 * result + bytes.contentHashCode()
    return result
  }
}

object DeviceFileHelper {
  const val MAX_ZIP_ARCHIVE_BYTES = 500L * 1024L * 1024L
  const val CLOUDFLARE_PAGES_MAX_SINGLE_FILE_BYTES = 25L * 1024L * 1024L
  const val CLOUDFLARE_WORKER_MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L
  private const val MAX_PAGES_ASSET_COUNT = 20_000
  private const val STREAM_BUFFER_BYTES = 64 * 1024
  private val IGNORED_SEGMENTS = setOf(".git", "node_modules", ".wrangler", "__MACOSX")
  private val IGNORED_FILES = setOf(".DS_Store", "Thumbs.db", "desktop.ini")

  private fun prepareFreshStagingDir(context: Context, bucket: String): File {
    val parent = File(context.cacheDir, bucket)
    parent.mkdirs()
    // Clean up older staging snapshots so disk cache never accumulates unboundedly
    parent.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
    return File(parent, UUID.randomUUID().toString()).apply { mkdirs() }
  }

  suspend fun readFiles(context: Context, uris: List<Uri>): List<PickedDeviceFile> =
    withContext(Dispatchers.IO) {
      uris.mapNotNull { uri ->
        runCatching {
          val name = resolveDisplayName(context, uri)
          val bytes =
            context.contentResolver.openInputStream(uri)?.use { input -> readStreamBytes(input) }
              ?: return@runCatching null
          PickedDeviceFile(
            fileName = name,
            content = bytes.toString(Charsets.UTF_8),
          )
        }.getOrNull()
      }
    }

  suspend fun readBinaryFiles(context: Context, uris: List<Uri>): Result<List<RawPickedAsset>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val out = mutableListOf<RawPickedAsset>()
        val usedNames = hashSetOf<String>()
        for (uri in uris) {
          val rawName = resolveDisplayName(context, uri).trimStart('/')
          if (rawName.isBlank() || IGNORED_FILES.contains(rawName)) continue
          val bytes =
            context.contentResolver.openInputStream(uri)?.use { input ->
              readStreamBytes(input, MAX_ZIP_ARCHIVE_BYTES)
            } ?: continue
          val uniqueName =
            if (usedNames.add(rawName)) rawName
            else "${rawName.substringBeforeLast('.')}_${usedNames.size}.${rawName.substringAfterLast('.', "")}"
              .trimEnd('.')
          out.add(RawPickedAsset(uniqueName, bytes))
        }
        require(out.isNotEmpty()) { "No readable files selected" }
        out
      }
    }

  suspend fun readDirectoryTree(context: Context, treeUri: Uri): Result<List<RawPickedAsset>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resolver = context.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val collected = mutableListOf<RawPickedAsset>()

        fun walk(parentDocId: String, prefix: String) {
          if (collected.size >= MAX_PAGES_ASSET_COUNT) {
            error("Folder exceeds $MAX_PAGES_ASSET_COUNT files limit for mobile upload")
          }
          val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
          val projection =
            arrayOf(
              DocumentsContract.Document.COLUMN_DOCUMENT_ID,
              DocumentsContract.Document.COLUMN_DISPLAY_NAME,
              DocumentsContract.Document.COLUMN_MIME_TYPE,
              DocumentsContract.Document.COLUMN_SIZE,
            )
          resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
              val docId = cursor.getString(idIdx) ?: continue
              val name = cursor.getString(nameIdx)?.trim() ?: continue
              val mime = cursor.getString(mimeIdx).orEmpty()
              val reportedSize = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L

              if (name in IGNORED_SEGMENTS || name in IGNORED_FILES) continue
              val relPath = if (prefix.isBlank()) name else "$prefix/$name"

              if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                walk(docId, relPath)
              } else {
                if (reportedSize > MAX_ZIP_ARCHIVE_BYTES) {
                  error("File '$relPath' exceeds 500 MiB local staging limit")
                }
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val bytes =
                  resolver.openInputStream(docUri)?.use { input -> readStreamBytes(input, MAX_ZIP_ARCHIVE_BYTES) }
                    ?: continue
                collected.add(RawPickedAsset(relPath, bytes))
                if (collected.size >= MAX_PAGES_ASSET_COUNT) {
                  error("Folder exceeds $MAX_PAGES_ASSET_COUNT files limit for upload")
                }
              }
            }
          }
        }

        walk(rootDocId, "")
        require(collected.isNotEmpty()) { "Selected folder contains no deployable files" }
        collected.sortedBy { it.relativePath }
      }
    }

  suspend fun readZipArchive(
    context: Context,
    zipUri: Uri,
    stripCommonRootFolder: Boolean = true,
  ): Result<List<RawPickedAsset>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val stagingRoot = prepareFreshStagingDir(context, "pages_zip_staging")
        val canonicalRoot = stagingRoot.canonicalPath + File.separator
        val rawEntries = mutableListOf<RawPickedAsset>()
        var totalExtractedBytes = 0L
        val chunk = ByteArray(STREAM_BUFFER_BYTES)

        context.contentResolver.openInputStream(zipUri)?.use { baseStream ->
          ZipInputStream(baseStream.buffered(STREAM_BUFFER_BYTES)).use { zis ->
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
                  if (!hasTraversal && !hasIgnored && cleanSegments.isNotEmpty()) {
                    val relPath = cleanSegments.joinToString("/")
                    val targetFile = File(stagingRoot, relPath)
                    if (!targetFile.canonicalPath.startsWith(canonicalRoot)) {
                      continue
                    }
                    targetFile.parentFile?.mkdirs()
                    var entryBytes = 0L
                    FileOutputStream(targetFile).use { fos ->
                      while (true) {
                        val read = zis.read(chunk)
                        if (read <= 0) break
                        entryBytes += read
                        totalExtractedBytes += read
                        if (totalExtractedBytes > MAX_ZIP_ARCHIVE_BYTES) {
                          error("ZIP archive exceeds 500 MiB uncompressed limit")
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
                    if (rawEntries.size >= MAX_PAGES_ASSET_COUNT) {
                      error("ZIP archive exceeds $MAX_PAGES_ASSET_COUNT files limit")
                    }
                  }
                }
              } finally {
                zis.closeEntry()
              }
            }
          }
        } ?: error("Unable to open ZIP archive")

        require(rawEntries.isNotEmpty()) { "ZIP archive contains no deployable files" }
        val normalized =
          if (stripCommonRootFolder) stripCommonWrapperFolder(rawEntries) else rawEntries
        normalized.sortedBy { it.relativePath }
      }
    }

  fun stripCommonWrapperFolder(assets: List<RawPickedAsset>): List<RawPickedAsset> {
    if (assets.isEmpty()) return assets
    val firstSegments =
      assets.mapNotNull { asset ->
        if (!asset.relativePath.contains('/')) null
        else asset.relativePath.substringBefore('/').takeIf { it.isNotBlank() }
      }
    if (firstSegments.size != assets.size) return assets
    val candidate = firstSegments.first()
    if (firstSegments.all { it == candidate }) {
      val prefix = "$candidate/"
      return assets.map { it.copy(relativePath = it.relativePath.removePrefix(prefix)) }
    }
    return assets
  }

  suspend fun isZipUri(context: Context, uri: Uri): Boolean =
    withContext(Dispatchers.IO) {
      val name = resolveDisplayName(context, uri)
      if (name.endsWith(".zip", ignoreCase = true)) return@withContext true
      val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
      if (mime.equals("application/zip", ignoreCase = true) ||
        mime.equals("application/x-zip-compressed", ignoreCase = true)
      ) {
        return@withContext true
      }
      runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
          val header = ByteArray(4)
          val count = input.read(header)
          count >= 4 && ZipWorkspaceImporter.isZipArchiveHeader(header)
        } ?: false
      }.getOrDefault(false)
    }

  suspend fun readZipForWorkspace(
    context: Context,
    zipUri: Uri,
  ): Result<ZipArchiveReadResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val archiveName = resolveDisplayName(context, zipUri)
        val stagingRoot = prepareFreshStagingDir(context, "workspace_zip_staging")
        context.contentResolver.openInputStream(zipUri)?.use { stream ->
          ZipWorkspaceImporter.readZipStream(
            input = stream,
            archiveName = archiveName,
            maxFileBytes = MAX_ZIP_ARCHIVE_BYTES,
            maxTotalBytes = MAX_ZIP_ARCHIVE_BYTES,
            stagingDir = stagingRoot,
          ).getOrThrow()
        } ?: error("Unable to open ZIP archive")
      }
    }

  suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resolver = context.contentResolver
        val bytes = content.toByteArray(Charsets.UTF_8)
        val modes = listOf("rwt", "wt", null)
        var written = false
        var lastErr: Throwable? = null
        for (mode in modes) {
          val attempt =
            runCatching {
              val stream =
                if (mode != null) resolver.openOutputStream(uri, mode)
                else resolver.openOutputStream(uri)
              stream?.use { out ->
                out.write(bytes)
                out.flush()
                true
              } ?: false
            }
          if (attempt.getOrDefault(false)) {
            written = true
            break
          }
          lastErr = attempt.exceptionOrNull()
        }
        if (!written) {
          throw lastErr ?: IllegalStateException("Could not open destination stream")
        }
      }
    }

  data class ExternalTextRead(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val content: String,
  )

  data class ExternalReadBatch(
    val files: List<ExternalTextRead>,
    val rejectedReasons: List<String>,
  )

  data class ExternalProjectScan(
    val textFiles: List<PickedDeviceFile>,
    val binaryAssets: Map<String, ByteArray>,
    val rootDirPath: String? = null,
  )

  suspend fun readExternalTextFiles(
    context: Context,
    uris: List<Uri>,
    maxSingleFileBytes: Long = 5L * 1024L * 1024L,
  ): ExternalReadBatch =
    withContext(Dispatchers.IO) {
      val accepted = mutableListOf<ExternalTextRead>()
      val rejected = mutableListOf<String>()
      for (uri in uris) {
        val name = resolveDisplayName(context, uri)
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (ExternalIntentExtractor.isBlockedAppOrExecutable(name, mime)) {
          rejected.add("'$name': app installers and native binaries are blocked")
          continue
        }
        val bytesResult =
          runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
              readStreamBytes(input, maxSingleFileBytes)
            } ?: error("Cannot open stream")
          }
        val bytes =
          bytesResult.getOrElse { err ->
            rejected.add("'$name': ${err.localizedMessage ?: "unreadable"}")
            continue
          }
        if (ExternalIntentExtractor.looksLikeBinaryBytes(bytes)) {
          rejected.add("'$name': unsupported binary file")
          continue
        }
        accepted.add(
          ExternalTextRead(
            uri = uri,
            fileName = name,
            mimeType = mime,
            content = bytes.toString(Charsets.UTF_8),
          ),
        )
      }
      ExternalReadBatch(files = accepted, rejectedReasons = rejected)
    }

  fun tryResolveParentDirectory(context: Context, uri: Uri): java.io.File? {
    if (uri.scheme.equals("file", ignoreCase = true)) {
      val path = uri.path ?: return null
      val f = java.io.File(path)
      val parent = f.parentFile
      return if (parent != null && parent.isDirectory && parent.canRead()) parent else null
    }
    if (uri.scheme.equals("content", ignoreCase = true) &&
      uri.authority == "com.android.externalstorage.documents"
    ) {
      val docId =
        runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
          ?: uri.lastPathSegment
          ?: return null
      val colonIdx = docId.indexOf(':')
      if (colonIdx > 0) {
        val volume = docId.substring(0, colonIdx)
        val relPath = docId.substring(colonIdx + 1)
        if (volume.equals("primary", ignoreCase = true) && relPath.isNotBlank()) {
          @Suppress("DEPRECATION")
          val extRoot = android.os.Environment.getExternalStorageDirectory()
          val candidateFile = java.io.File(extRoot, relPath)
          val parent = candidateFile.parentFile
          if (parent != null && parent.isDirectory && parent.canRead()) {
            return parent
          }
        }
      }
    }
    return null
  }

  suspend fun scanDirectoryForExternalProject(
    dir: java.io.File,
    maxFiles: Int = 500,
    maxTotalBytes: Long = 35L * 1024L * 1024L,
  ): ExternalProjectScan =
    withContext(Dispatchers.IO) {
      val textFiles = mutableListOf<PickedDeviceFile>()
      val binaryAssets = linkedMapOf<String, ByteArray>()
      if (!dir.exists() || !dir.isDirectory || !dir.canRead()) {
        return@withContext ExternalProjectScan(textFiles, binaryAssets, null)
      }
      val canonicalRoot = dir.canonicalFile
      var count = 0
      var totalBytes = 0L

      fun walk(current: java.io.File, prefix: String) {
        if (count >= maxFiles || totalBytes >= maxTotalBytes) return
        val children = current.listFiles()?.sortedBy { it.name.lowercase() } ?: return
        for (child in children) {
          if (count >= maxFiles || totalBytes >= maxTotalBytes) break
          val name = child.name
          if (name in IGNORED_SEGMENTS || name in IGNORED_FILES || name.startsWith(".")) {
            if (name != ".env" && !name.startsWith(".env.") && name != ".htaccess") continue
          }
          val rel = if (prefix.isBlank()) name else "$prefix/$name"
          if (child.isDirectory) {
            if (!child.canonicalPath.startsWith(canonicalRoot.canonicalPath)) continue
            walk(child, rel)
          } else if (child.isFile && child.canRead()) {
            if (ExternalIntentExtractor.isBlockedAppOrExecutable(name, null)) continue
            val len = child.length()
            if (len < 0L || len > CLOUDFLARE_PAGES_MAX_SINGLE_FILE_BYTES) continue
            if (totalBytes + len > maxTotalBytes) continue
            val bytes = runCatching { child.readBytes() }.getOrNull() ?: continue
            totalBytes += bytes.size
            count++
            if (ExternalIntentExtractor.isKnownWebBinaryAsset(name) || ExternalIntentExtractor.looksLikeBinaryBytes(bytes)) {
              if (ExternalIntentExtractor.isKnownWebBinaryAsset(name)) {
                binaryAssets[rel] = bytes
              }
            } else {
              textFiles.add(PickedDeviceFile(fileName = rel, content = bytes.toString(Charsets.UTF_8)))
            }
          }
        }
      }

      walk(dir, "")
      ExternalProjectScan(
        textFiles = textFiles,
        binaryAssets = binaryAssets,
        rootDirPath = canonicalRoot.absolutePath,
      )
    }

  suspend fun scanTreeUriForExternalProject(
    context: Context,
    treeUri: Uri,
    maxFiles: Int = 500,
    maxTotalBytes: Long = 35L * 1024L * 1024L,
  ): Result<ExternalProjectScan> =
    withContext(Dispatchers.IO) {
      runCatching {
        val picked = readDirectoryTree(context, treeUri).getOrThrow()
        val textFiles = mutableListOf<PickedDeviceFile>()
        val binaryAssets = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        for (asset in picked.take(maxFiles)) {
          if (ExternalIntentExtractor.isBlockedAppOrExecutable(asset.relativePath, null)) continue
          if (totalBytes + asset.bytes.size > maxTotalBytes) break
          totalBytes += asset.bytes.size
          if (ExternalIntentExtractor.isKnownWebBinaryAsset(asset.relativePath)) {
            binaryAssets[asset.relativePath] = asset.bytes
          } else if (!ExternalIntentExtractor.looksLikeBinaryBytes(asset.bytes)) {
            textFiles.add(
              PickedDeviceFile(
                fileName = asset.relativePath,
                content = asset.bytes.toString(Charsets.UTF_8),
              ),
            )
          }
        }
        ExternalProjectScan(
          textFiles = textFiles,
          binaryAssets = binaryAssets,
          rootDirPath = treeUri.toString(),
        )
      }
    }

  private fun readStreamBytes(input: InputStream, maxBytes: Long = Long.MAX_VALUE): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0L
    while (true) {
      val read = input.read(chunk)
      if (read <= 0) break
      total += read
      if (total > maxBytes) {
        error("File exceeds size limit (${maxBytes / (1024 * 1024)} MiB)")
      }
      buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
  }

  fun resolveDisplayName(context: Context, uri: Uri): String {
    runCatching {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) {
            val name = cursor.getString(idx)
            if (!name.isNullOrBlank()) return name
          }
        }
      }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "module.js" } ?: "module.js"
  }
}
