package com.example.data.remote

import okio.ByteString.Companion.toByteString

data class PagesPreparedAsset(
  val normalizedPath: String, // Always starts with '/', e.g. "/index.html"
  val bytes: ByteArray,
  val base64Content: String,
  val contentType: String,
  val hash: String, // 32-char lowercase hex BLAKE3(base64 + ext)
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PagesPreparedAsset) return false
    return normalizedPath == other.normalizedPath && hash == other.hash
  }

  override fun hashCode(): Int = 31 * normalizedPath.hashCode() + hash.hashCode()
}

data class PagesDirectUploadProgress(
  val phase: String,
  val completedFiles: Int,
  val totalFiles: Int,
  val uploadedBytes: Long,
  val totalBytes: Long,
) {
  val fraction: Float
    get() =
      when {
        totalBytes > 0L -> (uploadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        totalFiles > 0 -> (completedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
        else -> 0f
      }
}

/**
 * Implements Cloudflare Pages Direct Upload asset hashing and MIME resolution
 * matching Wrangler's exact protocol:
 *   hash = blake3(base64(fileBytes) + extensionWithoutLeadingDot).hex().slice(0, 32)
 */
object PagesDirectUploadHasher {
  private val MIME_BY_EXTENSION =
    mapOf(
      "html" to "text/html; charset=utf-8",
      "htm" to "text/html; charset=utf-8",
      "css" to "text/css; charset=utf-8",
      "js" to "application/javascript; charset=utf-8",
      "mjs" to "application/javascript; charset=utf-8",
      "json" to "application/json; charset=utf-8",
      "map" to "application/json; charset=utf-8",
      "xml" to "application/xml; charset=utf-8",
      "txt" to "text/plain; charset=utf-8",
      "md" to "text/markdown; charset=utf-8",
      "csv" to "text/csv; charset=utf-8",
      "svg" to "image/svg+xml",
      "png" to "image/png",
      "jpg" to "image/jpeg",
      "jpeg" to "image/jpeg",
      "gif" to "image/gif",
      "webp" to "image/webp",
      "avif" to "image/avif",
      "ico" to "image/x-icon",
      "woff" to "font/woff",
      "woff2" to "font/woff2",
      "ttf" to "font/ttf",
      "otf" to "font/otf",
      "eot" to "application/vnd.ms-fontobject",
      "wasm" to "application/wasm",
      "pdf" to "application/pdf",
      "mp4" to "video/mp4",
      "webm" to "video/webm",
      "mp3" to "audio/mpeg",
      "ogg" to "audio/ogg",
      "webmanifest" to "application/manifest+json",
    )

  fun normalizeAssetPath(rawPath: String): String {
    val cleaned =
      rawPath
        .replace('\\', '/')
        .split('/')
        .filter { it.isNotBlank() && it != "." && it != ".." }
        .joinToString("/")
    return if (cleaned.isBlank()) "" else "/$cleaned"
  }

  /**
   * Matches Node.js `path.extname(filePath).slice(1)` semantics:
   * - "/index.html" -> "html"
   * - "/app.min.js" -> "js"
   * - "/_headers" -> ""
   * - "/.well-known" -> ""
   */
  fun extractExtensionWithoutDot(filePath: String): String {
    val fileName = filePath.substringAfterLast('/')
    val dotIdx = fileName.lastIndexOf('.')
    if (dotIdx <= 0 || dotIdx == fileName.lastIndex) return ""
    return fileName.substring(dotIdx + 1)
  }

  fun guessContentType(filePath: String): String {
    val fileName = filePath.substringAfterLast('/').lowercase()
    if (fileName == "_headers" || fileName == "_redirects" || fileName == "robots.txt") {
      return "text/plain; charset=utf-8"
    }
    val ext = extractExtensionWithoutDot(fileName).lowercase()
    return MIME_BY_EXTENSION[ext] ?: "application/octet-stream"
  }

  fun computeAssetHash(bytes: ByteArray, filePath: String): String {
    val base64Str = bytes.toByteString().base64()
    val ext = extractExtensionWithoutDot(filePath)
    val payload = (base64Str + ext).toByteArray(Charsets.UTF_8)
    return Blake3.hashHex(payload, outputBytes = 16)
  }

  fun prepareAsset(rawPath: String, bytes: ByteArray): PagesPreparedAsset? {
    val normalized = normalizeAssetPath(rawPath)
    if (normalized.isBlank()) return null
    val base64Str = bytes.toByteString().base64()
    val ext = extractExtensionWithoutDot(normalized)
    val hash = Blake3.hashHex((base64Str + ext).toByteArray(Charsets.UTF_8), outputBytes = 16)
    return PagesPreparedAsset(
      normalizedPath = normalized,
      bytes = bytes,
      base64Content = base64Str,
      contentType = guessContentType(normalized),
      hash = hash,
    )
  }
}

/**
 * Pure-Kotlin implementation of the official BLAKE3 cryptographic hash function (single-keyless mode).
 */
internal object Blake3 {
  private const val OUT_LEN = 32
  private const val BLOCK_LEN = 64
  private const val CHUNK_LEN = 1024

  private const val CHUNK_START = 1
  private const val CHUNK_END = 2
  private const val PARENT = 4
  private const val ROOT = 8

  private val IV =
    intArrayOf(
      0x6A09E667,
      0xBB67AE85.toInt(),
      0x3C6EF372,
      0xA54FF53A.toInt(),
      0x510E527F,
      0x9B05688C.toInt(),
      0x1F83D9AB,
      0x5BE0CD19,
    )

  private val MSG_PERMUTATION =
    intArrayOf(2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8)

  fun hashHex(input: ByteArray, outputBytes: Int = OUT_LEN): String {
    val digest = hash(input, outputBytes)
    val hexChars = "0123456789abcdef"
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
      val v = b.toInt() and 0xFF
      sb.append(hexChars[v ushr 4])
      sb.append(hexChars[v and 0x0F])
    }
    return sb.toString()
  }

  fun hash(input: ByteArray, outputBytes: Int = OUT_LEN): ByteArray {
    val hasher = Hasher()
    hasher.update(input)
    return hasher.finalize(outputBytes)
  }

  private fun g(state: IntArray, a: Int, b: Int, c: Int, d: Int, mx: Int, my: Int) {
    state[a] = state[a] + state[b] + mx
    state[d] = (state[d] xor state[a]).rotateRight(16)
    state[c] = state[c] + state[d]
    state[b] = (state[b] xor state[c]).rotateRight(12)
    state[a] = state[a] + state[b] + my
    state[d] = (state[d] xor state[a]).rotateRight(8)
    state[c] = state[c] + state[d]
    state[b] = (state[b] xor state[c]).rotateRight(7)
  }

  private fun round(state: IntArray, m: IntArray) {
    // Columns
    g(state, 0, 4, 8, 12, m[0], m[1])
    g(state, 1, 5, 9, 13, m[2], m[3])
    g(state, 2, 6, 10, 14, m[4], m[5])
    g(state, 3, 7, 11, 15, m[6], m[7])
    // Diagonals
    g(state, 0, 5, 10, 15, m[8], m[9])
    g(state, 1, 6, 11, 12, m[10], m[11])
    g(state, 2, 7, 8, 13, m[12], m[13])
    g(state, 3, 4, 9, 14, m[14], m[15])
  }

  private fun permute(m: IntArray) {
    val copy = m.copyOf()
    for (i in 0 until 16) {
      m[i] = copy[MSG_PERMUTATION[i]]
    }
  }

  private fun wordsFromLittleEndianBytes(bytes: ByteArray, offset: Int, len: Int): IntArray {
    val words = IntArray(16)
    for (i in 0 until len) {
      val wordIdx = i / 4
      val shift = (i % 4) * 8
      words[wordIdx] = words[wordIdx] or ((bytes[offset + i].toInt() and 0xFF) shl shift)
    }
    return words
  }

  private fun compress(
    chainingValue: IntArray,
    blockWords: IntArray,
    counter: Long,
    blockLen: Int,
    flags: Int,
  ): IntArray {
    val state =
      intArrayOf(
        chainingValue[0],
        chainingValue[1],
        chainingValue[2],
        chainingValue[3],
        chainingValue[4],
        chainingValue[5],
        chainingValue[6],
        chainingValue[7],
        IV[0],
        IV[1],
        IV[2],
        IV[3],
        counter.toInt(),
        (counter ushr 32).toInt(),
        blockLen,
        flags,
      )
    val m = blockWords.copyOf()
    round(state, m)
    permute(m)
    round(state, m)
    permute(m)
    round(state, m)
    permute(m)
    round(state, m)
    permute(m)
    round(state, m)
    permute(m)
    round(state, m)
    permute(m)
    round(state, m)

    for (i in 0 until 8) {
      state[i] = state[i] xor state[i + 8]
      state[i + 8] = state[i + 8] xor chainingValue[i]
    }
    return state
  }

  private data class Output(
    val inputChainingValue: IntArray,
    val blockWords: IntArray,
    val counter: Long,
    val blockLen: Int,
    val flags: Int,
  ) {
    fun chainingValue(): IntArray {
      return compress(inputChainingValue, blockWords, counter, blockLen, flags).copyOf(8)
    }

    fun rootOutputBytes(outLen: Int): ByteArray {
      val out = ByteArray(outLen)
      var outputBlockCounter = 0L
      var offset = 0
      while (offset < outLen) {
        val words = compress(inputChainingValue, blockWords, outputBlockCounter, blockLen, flags or ROOT)
        for (w in words) {
          for (b in 0 until 4) {
            if (offset >= outLen) break
            out[offset++] = ((w ushr (b * 8)) and 0xFF).toByte()
          }
          if (offset >= outLen) break
        }
        outputBlockCounter++
      }
      return out
    }
  }

  private class ChunkState(
    val keyWords: IntArray,
    val chunkCounter: Long,
    val flags: Int,
  ) {
    var chainingValue: IntArray = keyWords.copyOf()
    val block = ByteArray(BLOCK_LEN)
    var blockLen: Int = 0
    var blocksCompressed: Int = 0

    fun len(): Int = BLOCK_LEN * blocksCompressed + blockLen

    private fun startFlag(): Int = if (blocksCompressed == 0) CHUNK_START else 0

    fun update(input: ByteArray, startOffset: Int, length: Int) {
      var offset = startOffset
      var remaining = length
      while (remaining > 0) {
        if (blockLen == BLOCK_LEN) {
          val blockWords = wordsFromLittleEndianBytes(block, 0, BLOCK_LEN)
          chainingValue =
            compress(
              chainingValue,
              blockWords,
              chunkCounter,
              BLOCK_LEN,
              flags or startFlag(),
            ).copyOf(8)
          blocksCompressed++
          block.fill(0)
          blockLen = 0
        }
        val want = BLOCK_LEN - blockLen
        val take = minOf(want, remaining)
        System.arraycopy(input, offset, block, blockLen, take)
        blockLen += take
        offset += take
        remaining -= take
      }
    }

    fun output(): Output {
      val blockWords = wordsFromLittleEndianBytes(block, 0, blockLen)
      return Output(
        inputChainingValue = chainingValue,
        blockWords = blockWords,
        counter = chunkCounter,
        blockLen = blockLen,
        flags = flags or startFlag() or CHUNK_END,
      )
    }
  }

  private class Hasher {
    private val keyWords = IV.copyOf()
    private var chunkState = ChunkState(keyWords, 0L, 0)
    private val cvStack = Array(54) { IntArray(8) }
    private var cvStackLen = 0

    private fun pushStack(cv: IntArray) {
      cvStack[cvStackLen++] = cv
    }

    private fun popStack(): IntArray {
      return cvStack[--cvStackLen]
    }

    private fun parentOutput(leftChildCv: IntArray, rightChildCv: IntArray): Output {
      val blockWords = IntArray(16)
      System.arraycopy(leftChildCv, 0, blockWords, 0, 8)
      System.arraycopy(rightChildCv, 0, blockWords, 8, 8)
      return Output(
        inputChainingValue = keyWords,
        blockWords = blockWords,
        counter = 0L,
        blockLen = BLOCK_LEN,
        flags = PARENT,
      )
    }

    private fun parentCv(leftChildCv: IntArray, rightChildCv: IntArray): IntArray {
      return parentOutput(leftChildCv, rightChildCv).chainingValue()
    }

    private fun addChunkChainingValue(newCv: IntArray, totalChunksArg: Long) {
      var cv = newCv
      var totalChunks = totalChunksArg
      while ((totalChunks and 1L) == 0L) {
        cv = parentCv(popStack(), cv)
        totalChunks = totalChunks ushr 1
      }
      pushStack(cv)
    }

    fun update(input: ByteArray) {
      var offset = 0
      while (offset < input.size) {
        if (chunkState.len() == CHUNK_LEN) {
          val chunkCv = chunkState.output().chainingValue()
          val totalChunks = chunkState.chunkCounter + 1
          addChunkChainingValue(chunkCv, totalChunks)
          chunkState = ChunkState(keyWords, totalChunks, 0)
        }
        val want = CHUNK_LEN - chunkState.len()
        val take = minOf(want, input.size - offset)
        chunkState.update(input, offset, take)
        offset += take
      }
    }

    fun finalize(outLen: Int): ByteArray {
      var output = chunkState.output()
      var parentNodesRemaining = cvStackLen
      while (parentNodesRemaining > 0) {
        parentNodesRemaining--
        output = parentOutput(cvStack[parentNodesRemaining], output.chainingValue())
      }
      return output.rootOutputBytes(outLen)
    }
  }
}
