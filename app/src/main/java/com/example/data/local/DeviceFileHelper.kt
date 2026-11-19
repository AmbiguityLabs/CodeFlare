package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedDeviceFile(
  val fileName: String,
  val content: String,
)

data class RawPickedAsset(
  val relativePath: String,
  val bytes: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RawPickedAsset) return false
    return relativePath == other.relativePath && bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int = 31 * relativePath.hashCode() + bytes.contentHashCode()
}

object DeviceFileHelper {
  private const val MAX_PAGES_FILE_BYTES = 25L * 1024L * 1024L
  private const val MAX_PAGES_ASSET_COUNT = 2000
  private val IGNORED_SEGMENTS = setOf(".git", "node_modules", ".wrangler", "__MACOSX")
  private val IGNORED_FILES = setOf(".DS_Store", "Thumbs.db", "desktop.ini")

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
            context.contentResolver.openInputStream(uri)?.use { input -> readStreamBytes(input, MAX_PAGES_FILE_BYTES) }
              ?: continue
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
                if (reportedSize > MAX_PAGES_FILE_BYTES) {
                  error("File '$relPath' exceeds Cloudflare Pages 25 MiB per-file limit")
                }
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val bytes =
                  resolver.openInputStream(docUri)?.use { input -> readStreamBytes(input, MAX_PAGES_FILE_BYTES) }
                    ?: continue
                collected.add(RawPickedAsset(relPath, bytes))
                if (collected.size >= MAX_PAGES_ASSET_COUNT) {
                  error("Folder exceeds $MAX_PAGES_ASSET_COUNT files limit for mobile upload")
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
        val rawEntries = mutableListOf<RawPickedAsset>()
        context.contentResolver.openInputStream(zipUri)?.use { baseStream ->
          ZipInputStream(baseStream.buffered()).use { zis ->
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
                    if (entry.size > MAX_PAGES_FILE_BYTES) {
                      error("ZIP entry '${entry.name}' exceeds Cloudflare Pages 25 MiB limit")
                    }
                    val bytes = readStreamBytes(zis, MAX_PAGES_FILE_BYTES)
                    rawEntries.add(RawPickedAsset(cleanSegments.joinToString("/"), bytes))
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
        context.contentResolver.openInputStream(zipUri)?.use { stream ->
          ZipWorkspaceImporter.readZipStream(stream, archiveName).getOrThrow()
        } ?: error("Unable to open ZIP archive")
      }
    }

  suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
          out.write(content.toByteArray(Charsets.UTF_8))
          out.flush()
        } ?: error("Could not open destination stream")
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
        error("File exceeds 25 MiB Cloudflare Pages per-file limit")
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
