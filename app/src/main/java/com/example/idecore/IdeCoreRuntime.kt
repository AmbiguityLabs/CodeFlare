package com.example.idecore

import android.os.Process
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Priority-lowered Android background threads for work nobody is waiting on this frame:
 * indexing, local history snapshots, and member-usage sweeps.
 *
 * Uses a moderate Android nice offset (THREAD_PRIORITY_LESS_FAVORABLE = +1/+2) so the UI thread
 * and GC always win contention without pushing background threads onto starved little-core cpusets.
 */
object IdeBackgroundThreads {
  @Volatile
  var lowerCurrentThread: () -> Unit = {
    runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_LESS_FAVORABLE) }
  }

  fun factory(name: String, numbered: Boolean = true): ThreadFactory {
    val n = AtomicInteger()
    return ThreadFactory { runnable ->
      Thread(
        {
          runCatching { lowerCurrentThread() }
          runnable.run()
        },
        if (numbered) "$name-${n.incrementAndGet()}" else name,
      ).apply { isDaemon = true }
    }
  }

  fun pool(name: String, threads: Int, keepAliveSeconds: Long = 30): ExecutorService =
    ThreadPoolExecutor(
      threads,
      threads,
      keepAliveSeconds,
      TimeUnit.SECONDS,
      LinkedBlockingQueue(),
      factory(name),
    ).apply { allowCoreThreadTimeOut(true) }

  fun single(name: String): ExecutorService =
    ThreadPoolExecutor(
      1,
      1,
      30,
      TimeUnit.SECONDS,
      LinkedBlockingQueue(),
      factory(name, numbered = false),
    ).apply { allowCoreThreadTimeOut(true) }
}

private const val MB_BYTES = 1024L * 1024L

data class IdeMemSample(
  val usedMb: Long,
  val maxMb: Long,
  val headroomMb: Long,
  val samples: Int = 1,
) {
  companion object {
    fun now(): IdeMemSample {
      val rt = Runtime.getRuntime()
      val max = rt.maxMemory()
      val used = rt.totalMemory() - rt.freeMemory()
      return IdeMemSample(
        usedMb = used / MB_BYTES,
        maxMb = max / MB_BYTES,
        headroomMb = (max - used).coerceAtLeast(0L) / MB_BYTES,
      )
    }
  }
}

class IdePeakHeap {
  private val lock = Any()
  private var maxUsed = 0L
  private var minHeadroom = Long.MAX_VALUE
  private var maxSeen = 0L
  private var count = 0

  fun record() {
    val rt = Runtime.getRuntime()
    val max = rt.maxMemory()
    val used = rt.totalMemory() - rt.freeMemory()
    val headroom = (max - used).coerceAtLeast(0L)
    synchronized(lock) {
      if (used > maxUsed) maxUsed = used
      if (headroom < minHeadroom) minHeadroom = headroom
      if (max > maxSeen) maxSeen = max
      count++
    }
  }

  fun peak(): IdeMemSample =
    synchronized(lock) {
      IdeMemSample(
        usedMb = maxUsed / MB_BYTES,
        maxMb = maxSeen / MB_BYTES,
        headroomMb = if (minHeadroom == Long.MAX_VALUE) 0L else minHeadroom / MB_BYTES,
        samples = count.coerceAtLeast(1),
      )
    }
}

data class IdePerfMetricSummary(
  val name: String,
  val count: Int,
  val p50Ms: Long,
  val p95Ms: Long,
  val maxMs: Long,
  val peakHeap: IdeMemSample,
)

/**
 * Aggregates high-frequency IDE latency samples (completion, search, refactor) into rolling
 * summaries with zero heap churn on hot paths.
 */
class IdePerfSampler(private val windowSize: Int = 40) {
  private val lock = Any()
  private val windows = HashMap<String, MetricWindow>()
  private val lastSummaries = HashMap<String, IdePerfMetricSummary>()

  private class MetricWindow(capacity: Int) {
    val samples = ArrayList<Long>(capacity)
    val heap = IdePeakHeap()
  }

  fun record(name: String, elapsedMs: Long) {
    synchronized(lock) {
      val w = windows.getOrPut(name) { MetricWindow(windowSize) }
      w.samples.add(elapsedMs.coerceAtLeast(0L))
      w.heap.record()
      if (w.samples.size >= windowSize) {
        lastSummaries[name] = summarize(name, w)
        windows.remove(name)
      }
    }
  }

  fun snapshot(): List<IdePerfMetricSummary> =
    synchronized(lock) {
      val out = LinkedHashMap<String, IdePerfMetricSummary>(lastSummaries)
      for ((name, w) in windows) {
        if (w.samples.isNotEmpty()) {
          out[name] = summarize(name, w)
        }
      }
      out.values.toList()
    }

  private fun summarize(name: String, w: MetricWindow): IdePerfMetricSummary {
    val sorted = w.samples.sorted()
    fun pct(p: Double): Long = sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)]
    return IdePerfMetricSummary(
      name = name,
      count = sorted.size,
      p50Ms = pct(0.50),
      p95Ms = pct(0.95),
      maxMs = sorted.last(),
      peakHeap = w.heap.peak(),
    )
  }
}

sealed interface WorkspaceEvent {
  data class FileSaved(val workspaceKey: String, val path: String, val before: String?, val after: String) : WorkspaceEvent
  data class FilesRefactored(val workspaceKey: String, val editedPaths: List<String>) : WorkspaceEvent
  data class FileCreated(val workspaceKey: String, val path: String) : WorkspaceEvent
  data class FileDeleted(val workspaceKey: String, val path: String) : WorkspaceEvent
  data class FileRenamed(val workspaceKey: String, val oldPath: String, val newPath: String) : WorkspaceEvent
}

/**
 * Coalesced workspace event spine routing saves, refactors, and tree mutations to background
 * subscribers (LocalHistory, MemberUsageIndex, symbol caches).
 */
class WorkspaceEventBus {
  private val listeners = CopyOnWriteArrayList<(WorkspaceEvent) -> Unit>()
  val generation = AtomicLong(0L)

  fun subscribe(listener: (WorkspaceEvent) -> Unit): () -> Unit {
    listeners.add(listener)
    return { listeners.remove(listener) }
  }

  fun publish(event: WorkspaceEvent) {
    generation.incrementAndGet()
    for (listener in listeners) {
      runCatching { listener(event) }
    }
  }
}
