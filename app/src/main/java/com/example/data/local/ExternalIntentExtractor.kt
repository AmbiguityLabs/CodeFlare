package com.example.data.local

import android.content.Intent
import android.net.Uri
import android.os.Build

enum class ExternalOpenTargetMode {
  PREVIEW_HTML,
  PREVIEW_MARKDOWN,
  PREVIEW_SVG,
  CODE_EDITOR_WITH_RUN,
  CODE_EDITOR,
  ZIP_IMPORT,
}

data class ExtractedExternalIntent(
  val action: String,
  val uris: List<Uri>,
  val sharedText: String?,
  val sharedSubject: String?,
)

object ExternalIntentExtractor {
  val BLOCKED_APP_AND_BINARY_EXTENSIONS: Set<String> =
    setOf(
      "apk",
      "apkm",
      "xapk",
      "apks",
      "aab",
      "dex",
      "odex",
      "vdex",
      "so",
      "dll",
      "exe",
      "msi",
      "dmg",
      "pkg",
      "deb",
      "rpm",
      "bin",
      "img",
      "iso",
      "class",
      "jar",
      "elf",
      "o",
      "a",
    )

  val BLOCKED_MIME_TYPES: Set<String> =
    setOf(
      "application/vnd.android.package-archive",
      "application/x-msdownload",
      "application/x-dosexec",
      "application/x-executable",
      "application/x-sharedlib",
      "application/java-archive",
      "application/x-debian-package",
      "application/x-rpm",
    )

  val WEB_BINARY_ASSET_EXTENSIONS: Set<String> =
    setOf(
      "png",
      "jpg",
      "jpeg",
      "gif",
      "webp",
      "ico",
      "bmp",
      "avif",
      "woff",
      "woff2",
      "ttf",
      "otf",
      "eot",
      "wasm",
      "mp3",
      "wav",
      "ogg",
      "mp4",
      "webm",
      "pdf",
    )

  fun extractFromIntent(intent: Intent?): ExtractedExternalIntent? {
    if (intent == null) return null
    val action = intent.action ?: return null
    if (
      action != Intent.ACTION_VIEW &&
        action != Intent.ACTION_EDIT &&
        action != Intent.ACTION_SEND &&
        action != Intent.ACTION_SEND_MULTIPLE
    ) {
      return null
    }

    val collectedUris = linkedSetOf<Uri>()
    when (action) {
      Intent.ACTION_VIEW,
      Intent.ACTION_EDIT -> {
        intent.data?.let { uri ->
          if (uri.scheme.equals("content", true) || uri.scheme.equals("file", true)) {
            collectedUris.add(uri)
          }
        }
        extractClipDataUris(intent, collectedUris)
      }
      Intent.ACTION_SEND -> {
        getParcelableUriExtra(intent, Intent.EXTRA_STREAM)?.let { collectedUris.add(it) }
        intent.data?.let { uri ->
          if (uri.scheme.equals("content", true) || uri.scheme.equals("file", true)) {
            collectedUris.add(uri)
          }
        }
        extractClipDataUris(intent, collectedUris)
      }
      Intent.ACTION_SEND_MULTIPLE -> {
        getParcelableUriArrayListExtra(intent, Intent.EXTRA_STREAM).forEach { collectedUris.add(it) }
        extractClipDataUris(intent, collectedUris)
      }
    }

    val sharedText =
      if (action == Intent.ACTION_SEND) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotEmpty() }
      } else {
        null
      }
    val sharedSubject =
      intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
        ?: intent.getStringExtra(Intent.EXTRA_TITLE)?.takeIf { it.isNotBlank() }

    if (collectedUris.isEmpty() && sharedText == null) {
      return null
    }

    return ExtractedExternalIntent(
      action = action,
      uris = collectedUris.toList(),
      sharedText = sharedText,
      sharedSubject = sharedSubject,
    )
  }

  private fun extractClipDataUris(intent: Intent, out: MutableSet<Uri>) {
    val clip = intent.clipData ?: return
    for (i in 0 until clip.itemCount) {
      val itemUri = clip.getItemAt(i)?.uri ?: continue
      if (itemUri.scheme.equals("content", true) || itemUri.scheme.equals("file", true)) {
        out.add(itemUri)
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun getParcelableUriExtra(intent: Intent, name: String): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(name, Uri::class.java)
    } else {
      intent.getParcelableExtra(name)
    }
  }

  @Suppress("DEPRECATION")
  private fun getParcelableUriArrayListExtra(intent: Intent, name: String): List<Uri> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra(name, Uri::class.java).orEmpty()
    } else {
      intent.getParcelableArrayListExtra<Uri>(name).orEmpty()
    }
  }

  fun isBlockedAppOrExecutable(fileName: String?, mimeType: String?): Boolean {
    val cleanExt = fileName?.trim()?.substringAfterLast('.', "")?.lowercase().orEmpty()
    if (cleanExt.isNotBlank() && cleanExt in BLOCKED_APP_AND_BINARY_EXTENSIONS) {
      return true
    }
    val cleanMime = mimeType?.trim()?.substringBefore(';')?.lowercase().orEmpty()
    if (cleanMime in BLOCKED_MIME_TYPES) {
      return true
    }
    return false
  }

  fun isKnownWebBinaryAsset(fileName: String?): Boolean {
    val cleanExt = fileName?.trim()?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return cleanExt in WEB_BINARY_ASSET_EXTENSIONS
  }

  fun looksLikeBinaryBytes(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    val sampleSize = minOf(bytes.size, 8192)
    var controlCount = 0
    for (i in 0 until sampleSize) {
      val b = bytes[i].toInt() and 0xFF
      if (b == 0) return true
      if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) {
        controlCount++
      }
    }
    return controlCount.toDouble() / sampleSize > 0.10
  }

  fun determineOpenMode(path: String): ExternalOpenTargetMode {
    val ext = path.trim().substringAfterLast('.', "").lowercase()
    return when {
      ext == "zip" -> ExternalOpenTargetMode.ZIP_IMPORT
      ext in setOf("html", "htm", "xhtml") -> ExternalOpenTargetMode.PREVIEW_HTML
      ext in setOf("md", "markdown", "mdx") -> ExternalOpenTargetMode.PREVIEW_MARKDOWN
      ext == "svg" -> ExternalOpenTargetMode.PREVIEW_SVG
      ext in setOf("js", "mjs", "cjs", "ts", "mts", "cts", "tsx", "jsx") ->
        ExternalOpenTargetMode.CODE_EDITOR_WITH_RUN
      else -> ExternalOpenTargetMode.CODE_EDITOR
    }
  }

  fun sanitizeSharedSnippetFilename(subject: String?, text: String): String {
    val trimmedText = text.trimStart()
    val inferredExt =
      when {
        trimmedText.startsWith("<!DOCTYPE html", ignoreCase = true) ||
          trimmedText.startsWith("<html", ignoreCase = true) -> "html"
        trimmedText.startsWith("<svg", ignoreCase = true) -> "svg"
        trimmedText.startsWith("{") || trimmedText.startsWith("[") -> "json"
        trimmedText.contains("export default") ||
          trimmedText.contains("console.log(") ||
          trimmedText.contains("function ") -> "js"
        trimmedText.startsWith("# ") || trimmedText.contains("\n## ") -> "md"
        else -> "txt"
      }
    val rawSubject =
      subject
        ?.trim()
        ?.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        ?.trim('_', '.')
        ?.take(36)
        .orEmpty()
    if (rawSubject.isBlank()) {
      return "shared-snippet.$inferredExt"
    }
    return if ('.' in rawSubject) rawSubject else "$rawSubject.$inferredExt"
  }
}
