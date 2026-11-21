package com.example.data.remote

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

object MultipartWorkerParser {
  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val mapType =
    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
  private val mapAdapter: JsonAdapter<Map<String, Any>> = moshi.adapter(mapType)

  data class ParsedWorkerPayload(
    val files: List<WorkerFile>,
    val isEsModule: Boolean,
    val entryModulePath: String,
  )

  fun inferModuleMimeType(path: String): String =
    when {
      path.endsWith(".json", ignoreCase = true) || path.endsWith(".jsonc", ignoreCase = true) -> "application/json"
      path.endsWith(".wasm", ignoreCase = true) -> "application/wasm"
      path.endsWith(".bin", ignoreCase = true) || path.endsWith(".dat", ignoreCase = true) -> "application/octet-stream"
      path.endsWith(".txt", ignoreCase = true) ||
        path.endsWith(".html", ignoreCase = true) ||
        path.endsWith(".htm", ignoreCase = true) ||
        path.endsWith(".css", ignoreCase = true) ||
        path.endsWith(".sql", ignoreCase = true) ||
        path.endsWith(".md", ignoreCase = true) ||
        path.endsWith(".svg", ignoreCase = true) -> "text/plain"
      else -> "application/javascript+module"
    }

  fun parseResponse(
    rawBytes: ByteArray,
    contentTypeHeader: String?,
    scriptName: String,
    settingsEntryHint: String? = null,
  ): ParsedWorkerPayload {
    val contentType = contentTypeHeader.orEmpty()
    if (contentType.contains("multipart/", ignoreCase = true)) {
      val boundary = extractBoundary(contentType)
      if (!boundary.isNullOrBlank()) {
        val parts = parseMultipartParts(rawBytes, boundary)
        if (parts.isNotEmpty()) {
          val moduleFiles = mutableListOf<WorkerFile>()
          var detectedEntry = settingsEntryHint ?: ""

          for (part in parts) {
            val partName = part.name.ifBlank { part.filename }
            if (
              partName == "metadata" ||
                (part.partContentType.contains("json", ignoreCase = true) && partName == "metadata.json")
            ) {
              runCatching {
                val meta = mapAdapter.fromJson(part.bodyText)
                val mainMod = meta?.get("main_module") as? String
                if (!mainMod.isNullOrBlank()) {
                  detectedEntry = mainMod
                }
              }
              continue
            }
            val cleanPath = (part.filename.ifBlank { partName }).ifBlank { "index.js" }
            val resolvedMime =
              part.partContentType.ifBlank { inferModuleMimeType(cleanPath) }
            moduleFiles.add(
              WorkerFile(
                path = cleanPath,
                content = part.bodyText,
                originalContent = part.bodyText,
                contentType = resolvedMime,
                isEntryPoint = false,
              )
            )
          }

          if (moduleFiles.isNotEmpty()) {
            val resolvedEntry =
              when {
                detectedEntry.isNotBlank() && moduleFiles.any { it.path == detectedEntry } ->
                  detectedEntry
                moduleFiles.any { it.path == "index.js" } -> "index.js"
                moduleFiles.any { it.path == "src/index.js" } -> "src/index.js"
                else -> moduleFiles.first().path
              }

            val finalFiles =
              moduleFiles
                .map { it.copy(isEntryPoint = it.path == resolvedEntry) }
                .sortedWith(compareByDescending<WorkerFile> { it.isEntryPoint }.thenBy { it.path })

            val esm =
              finalFiles.any {
                it.contentType.contains("module", ignoreCase = true) ||
                  it.content.contains("export default")
              }
            return ParsedWorkerPayload(
              files = finalFiles,
              isEsModule = esm,
              entryModulePath = resolvedEntry,
            )
          }
        }
      }
    }

    val text = rawBytes.toString(Charsets.UTF_8)
    val looksLikeEsm =
      contentType.contains("javascript+module", ignoreCase = true) ||
        text.contains("export default") ||
        text.contains("export {")
    val defaultPath = if (looksLikeEsm) "index.js" else "$scriptName.js"
    return ParsedWorkerPayload(
      files =
        listOf(
          WorkerFile(
            path = defaultPath,
            content = text,
            originalContent = text,
            contentType =
              if (looksLikeEsm) "application/javascript+module" else "application/javascript",
            isEntryPoint = true,
          )
        ),
      isEsModule = looksLikeEsm,
      entryModulePath = defaultPath,
    )
  }

  fun buildUploadMultipartBody(
    files: List<WorkerFile>,
    isEsModule: Boolean,
    entryModulePath: String,
    compatibilityDate: String,
    compatibilityFlags: List<String> = emptyList(),
  ): RequestBody {
    val editableFiles =
      files
        .filter { f ->
          val clean = normalizeModulePath(f.path)
          val name = clean.substringAfterLast('/').lowercase()
          !f.isVirtualReadOnly &&
            clean.isNotEmpty() &&
            !(name.startsWith("wrangler.") && (name.endsWith(".jsonc") || name.endsWith(".json") || name.endsWith(".toml")))
        }
        .map { it.copy(path = normalizeModulePath(it.path)) }
        .distinctBy { it.path }
    require(editableFiles.isNotEmpty()) { "Worker must contain at least one module script." }
    val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
    val normalizedEntry = normalizeModulePath(entryModulePath)

    if (isEsModule || editableFiles.size > 1) {
      val resolvedEntry =
        editableFiles.firstOrNull { it.path == normalizedEntry }?.path
          ?: editableFiles.firstOrNull { it.isEntryPoint }?.path
          ?: editableFiles.first().path

      val metadataJson =
        buildString {
          append("{")
          append("\"main_module\":\"${escapeJson(resolvedEntry)}\"")
          if (compatibilityDate.isNotBlank()) {
            append(",\"compatibility_date\":\"${escapeJson(compatibilityDate)}\"")
          }
          if (compatibilityFlags.isNotEmpty()) {
            val flagsArr = compatibilityFlags.joinToString(",") { "\"${escapeJson(it)}\"" }
            append(",\"compatibility_flags\":[$flagsArr]")
          }
          append("}")
        }
      builder.addFormDataPart(
        "metadata",
        "metadata.json",
        metadataJson.toRequestBody("application/json".toMediaType()),
      )

      for (file in editableFiles) {
        val mime = inferModuleMimeType(file.path)
        builder.addFormDataPart(
          file.path,
          file.path,
          file.content.toRequestBody(mime.toMediaType()),
        )
      }
    } else {
      val mainFile = editableFiles.first()
      val metadataJson =
        buildString {
          append("{\"body_part\":\"script\"")
          if (compatibilityDate.isNotBlank()) {
            append(",\"compatibility_date\":\"${escapeJson(compatibilityDate)}\"")
          }
          if (compatibilityFlags.isNotEmpty()) {
            val flagsArr = compatibilityFlags.joinToString(",") { "\"${escapeJson(it)}\"" }
            append(",\"compatibility_flags\":[$flagsArr]")
          }
          append("}")
        }
      builder.addFormDataPart(
        "metadata",
        "metadata.json",
        metadataJson.toRequestBody("application/json".toMediaType()),
      )
      builder.addFormDataPart(
        "script",
        mainFile.path,
        mainFile.content.toRequestBody("application/javascript".toMediaType()),
      )
    }

    return builder.build()
  }

  private fun normalizeModulePath(raw: String): String =
    raw.trim().replace('\\', '/').trimStart('/').removePrefix("./")

  private fun escapeJson(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

  private fun extractBoundary(contentType: String): String? {
    val regex = Regex("""boundary=(?:"([^"]+)"|([^;\s]+))""", RegexOption.IGNORE_CASE)
    val match = regex.find(contentType) ?: return null
    return match.groups[1]?.value ?: match.groups[2]?.value
  }

  private data class RawPart(
    val name: String,
    val filename: String,
    val partContentType: String,
    val bodyText: String,
  )

  private fun parseMultipartParts(bytes: ByteArray, boundary: String): List<RawPart> {
    val rawString = bytes.toString(Charsets.ISO_8859_1)
    val delimiter = "--$boundary"
    val chunks = rawString.split(delimiter)
    val results = mutableListOf<RawPart>()

    for (chunk in chunks) {
      if (chunk.isBlank() || chunk.startsWith("--")) continue
      val cleaned = chunk.removePrefix("\r\n").removePrefix("\n")
      if (cleaned.startsWith("--")) continue

      val headerEndCrLf = cleaned.indexOf("\r\n\r\n")
      val headerEndLf = cleaned.indexOf("\n\n")
      val (headerBlock, bodyIso) =
        when {
          headerEndCrLf >= 0 ->
            cleaned.substring(0, headerEndCrLf) to cleaned.substring(headerEndCrLf + 4)
          headerEndLf >= 0 ->
            cleaned.substring(0, headerEndLf) to cleaned.substring(headerEndLf + 2)
          else -> continue
        }

      val bodyTrimmedIso =
        bodyIso.removeSuffix("\r\n").removeSuffix("\n")
      val utf8Body =
        String(bodyTrimmedIso.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)

      var name = ""
      var filename = ""
      var partContentType = ""

      for (line in headerBlock.lines()) {
        val lower = line.lowercase()
        if (lower.startsWith("content-disposition:")) {
          name =
            Regex("""name="([^"]*)"""")
              .find(line)
              ?.groups?.get(1)
              ?.value
              .orEmpty()
          filename =
            Regex("""filename="([^"]*)"""")
              .find(line)
              ?.groups?.get(1)
              ?.value
              .orEmpty()
        } else if (lower.startsWith("content-type:")) {
          partContentType = line.substringAfter(":").trim()
        }
      }

      results.add(
        RawPart(
          name = name,
          filename = filename,
          partContentType = partContentType,
          bodyText = utf8Body,
        )
      )
    }
    return results
  }
}
