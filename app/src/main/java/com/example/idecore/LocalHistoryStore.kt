package com.example.idecore

import com.example.data.remote.WorkerFile
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class LocalHistorySnapshot(
  val id: Long,
  val timestampMs: Long,
  val sizeChars: Int,
  val lineCount: Int,
  val label: String = "",
)

data class HistoryDiffLine(
  val kind: DiffLineKind,
  val text: String,
)

enum class DiffLineKind { CONTEXT, ADDED, REMOVED }

/**
 * Per-file local version history stored independently of Git, adapted from CodeAssist LocalHistory.
 * Keeps up to [MAX_SNAPSHOTS] versions per file on a single priority-lowered background thread,
 * including the pre-first-edit baseline so edits can always be recovered after tab close or restart.
 */
class LocalHistoryStore(private val rootDir: File) {
  private val writer: ExecutorService = IdeBackgroundThreads.single("local-history")

  fun recordSnapshot(
    workspaceKey: String,
    filePath: String,
    before: String?,
    after: String,
    label: String = "",
  ) {
    if (workspaceKey.isBlank() || filePath.isBlank() || after.length > MAX_CHARS) return
    val dir = dirFor(workspaceKey, filePath)
    val now = System.currentTimeMillis()
    val safeLabel = label.replace('\n', ' ').take(48)
    writer.execute {
      runCatching {
        if (!dir.exists()) dir.mkdirs()
        val existingIds = snapshotIds(dir)
        if (existingIds.isEmpty() && before != null && before != after && before.length <= MAX_CHARS) {
          writeEntry(dir, now - 1L, before, "Initial")
        }
        val latest = snapshotIds(dir).maxOrNull()
        if (latest != null) {
          val latestText = runCatching { File(dir, "$latest.txt").readText(Charsets.UTF_8) }.getOrNull()
          if (latestText == after) return@runCatching
        }
        val stamp = if (latest != null && now <= latest) latest + 1L else now
        writeEntry(dir, stamp, after, safeLabel)
        prune(dir)
      }
    }
  }

  fun snapshots(workspaceKey: String, filePath: String): List<LocalHistorySnapshot> {
    if (workspaceKey.isBlank() || filePath.isBlank()) return emptyList()
    val dir = dirFor(workspaceKey, filePath)
    if (!dir.isDirectory) return emptyList()
    return snapshotIds(dir)
      .sortedDescending()
      .map { id ->
        val txtFile = File(dir, "$id.txt")
        val content = runCatching { txtFile.readText(Charsets.UTF_8) }.getOrDefault("")
        val metaLabel = runCatching { File(dir, "$id.meta").takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty() }.getOrDefault("")
        val lines = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1
        LocalHistorySnapshot(
          id = id,
          timestampMs = id,
          sizeChars = content.length,
          lineCount = lines,
          label = metaLabel.trim(),
        )
      }
  }

  fun readSnapshot(workspaceKey: String, filePath: String, id: Long): String? {
    val file = File(dirFor(workspaceKey, filePath), "$id.txt")
    return if (file.isFile) runCatching { file.readText(Charsets.UTF_8) }.getOrNull() else null
  }

  fun flush() {
    runCatching { writer.submit {}.get(5, TimeUnit.SECONDS) }
  }

  private fun writeEntry(dir: File, id: Long, text: String, label: String) {
    File(dir, "$id.txt").writeText(text, Charsets.UTF_8)
    if (label.isNotBlank()) {
      File(dir, "$id.meta").writeText(label, Charsets.UTF_8)
    }
  }

  private fun snapshotIds(dir: File): List<Long> {
    val files = dir.listFiles() ?: return emptyList()
    return files.mapNotNull { f ->
      if (f.isFile && f.name.endsWith(".txt")) {
        f.name.removeSuffix(".txt").toLongOrNull()
      } else {
        null
      }
    }
  }

  private fun prune(dir: File) {
    val ids = snapshotIds(dir).sorted()
    if (ids.size <= MAX_SNAPSHOTS) return
    ids.take(ids.size - MAX_SNAPSHOTS).forEach { id ->
      runCatching { File(dir, "$id.txt").delete() }
      runCatching { File(dir, "$id.meta").delete() }
    }
  }

  private fun dirFor(workspaceKey: String, filePath: String): File {
    val wsHash = sha1Hex(workspaceKey).take(12)
    val fileHash = sha1Hex(filePath.trimStart('/')).take(16)
    return File(File(rootDir, wsHash), fileHash)
  }

  private fun sha1Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  companion object {
    const val MAX_SNAPSHOTS = 50
    const val MAX_CHARS = 1_000_000

    /** Compact LCS-backed unified line diff for Local History preview (capped for instant UI rendering). */
    fun computeLineDiff(snapshotText: String, currentText: String, maxLines: Int = 220): List<HistoryDiffLine> {
      val oldLines = snapshotText.lines()
      val newLines = currentText.lines()
      if (oldLines == newLines) {
        return newLines.take(maxLines).map { HistoryDiffLine(DiffLineKind.CONTEXT, it) }
      }
      if (oldLines.size > 400 || newLines.size > 400) {
        val out = ArrayList<HistoryDiffLine>(maxLines)
        val n = maxOf(oldLines.size, newLines.size)
        for (i in 0 until n) {
          if (out.size >= maxLines) break
          val o = oldLines.getOrNull(i)
          val c = newLines.getOrNull(i)
          when {
            o == c && o != null -> out.add(HistoryDiffLine(DiffLineKind.CONTEXT, o))
            else -> {
              if (o != null) out.add(HistoryDiffLine(DiffLineKind.REMOVED, o))
              if (c != null && out.size < maxLines) out.add(HistoryDiffLine(DiffLineKind.ADDED, c))
            }
          }
        }
        return out
      }

      val m = oldLines.size
      val n = newLines.size
      val dp = Array(m + 1) { IntArray(n + 1) }
      for (i in m - 1 downTo 0) {
        for (j in n - 1 downTo 0) {
          dp[i][j] =
            if (oldLines[i] == newLines[j]) dp[i + 1][j + 1] + 1
            else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
      }
      val out = ArrayList<HistoryDiffLine>(minOf(m + n, maxLines))
      var i = 0
      var j = 0
      while ((i < m || j < n) && out.size < maxLines) {
        when {
          i < m && j < n && oldLines[i] == newLines[j] -> {
            out.add(HistoryDiffLine(DiffLineKind.CONTEXT, oldLines[i]))
            i++; j++
          }
          j < n && (i == m || dp[i][j + 1] >= dp[i + 1][j]) -> {
            out.add(HistoryDiffLine(DiffLineKind.ADDED, newLines[j]))
            j++
          }
          else -> {
            out.add(HistoryDiffLine(DiffLineKind.REMOVED, oldLines[i]))
            i++
          }
        }
      }
      return out
    }
  }
}

/**
 * Reversible file-tree operations journal adapted from CodeAssist FileOpJournal.
 * Allows instant one-tap undo of file deletions, renames, and creations.
 */
class WorkspaceFileOpJournal {
  sealed interface FileOp {
    val label: String

    data class Deleted(
      val workspaceKey: String,
      val file: WorkerFile,
      val wasOpenTab: Boolean,
      val wasEntryPoint: Boolean,
    ) : FileOp {
      override val label: String get() = "Delete ${file.fileName}"
    }

    data class Renamed(
      val workspaceKey: String,
      val oldPath: String,
      val newPath: String,
    ) : FileOp {
      override val label: String get() = "Rename ${oldPath.substringAfterLast('/')}"
    }

    data class Created(
      val workspaceKey: String,
      val path: String,
    ) : FileOp {
      override val label: String get() = "Create ${path.substringAfterLast('/')}"
    }
  }

  data class Entry(
    val token: String,
    val op: FileOp,
  )

  private val entries = LinkedHashMap<String, FileOp>()
  private val counter = AtomicLong(0L)

  @Synchronized
  fun record(op: FileOp): Entry {
    val token = "fop-${System.currentTimeMillis()}-${counter.incrementAndGet()}"
    entries[token] = op
    while (entries.size > MAX_ENTRIES) {
      val oldest = entries.keys.first()
      entries.remove(oldest)
    }
    return Entry(token, op)
  }

  @Synchronized
  fun takeLatestForWorkspace(workspaceKey: String): Entry? {
    val key = entries.entries.lastOrNull { entryMatchesWorkspace(it.value, workspaceKey) }?.key ?: return null
    val op = entries.remove(key) ?: return null
    return Entry(key, op)
  }

  @Synchronized
  fun take(token: String): FileOp? = entries.remove(token)

  @Synchronized
  fun latestForWorkspace(workspaceKey: String): Entry? {
    val found = entries.entries.lastOrNull { entryMatchesWorkspace(it.value, workspaceKey) } ?: return null
    return Entry(found.key, found.value)
  }

  private fun entryMatchesWorkspace(op: FileOp, workspaceKey: String): Boolean =
    when (op) {
      is FileOp.Deleted -> op.workspaceKey == workspaceKey
      is FileOp.Renamed -> op.workspaceKey == workspaceKey
      is FileOp.Created -> op.workspaceKey == workspaceKey
    }

  private companion object {
    const val MAX_ENTRIES = 20
  }
}
