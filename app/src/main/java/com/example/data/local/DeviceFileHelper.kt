package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedDeviceFile(
  val fileName: String,
  val content: String,
)

object DeviceFileHelper {
  suspend fun readFiles(context: Context, uris: List<Uri>): List<PickedDeviceFile> =
    withContext(Dispatchers.IO) {
      uris.mapNotNull { uri ->
        runCatching {
          val name = resolveDisplayName(context, uri)
          val bytes =
            context.contentResolver.openInputStream(uri)?.use { input ->
              val buffer = ByteArrayOutputStream()
              val chunk = ByteArray(8192)
              while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                buffer.write(chunk, 0, read)
              }
              buffer.toByteArray()
            } ?: return@runCatching null
          PickedDeviceFile(
            fileName = name,
            content = bytes.toString(Charsets.UTF_8),
          )
        }.getOrNull()
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

  private fun resolveDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) {
          val name = cursor.getString(idx)
          if (!name.isNullOrBlank()) return name
        }
      }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "module.js" } ?: "module.js"
  }
}
