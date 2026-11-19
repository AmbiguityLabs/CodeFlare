package com.example.data.local

import com.example.data.remote.CfD1Database
import com.example.data.remote.CfDurableObjectNamespace
import com.example.data.remote.CfKvNamespace
import com.example.data.remote.CfPagesProject
import com.example.data.remote.CfQueue
import com.example.data.remote.CfR2Bucket
import com.example.data.remote.CfWorkerScript
import com.example.data.remote.CfZone
import com.example.data.remote.GitHubRepo
import com.example.data.remote.GitHubUser
import com.squareup.moshi.Moshi
import java.io.File

data class CachedWorkersEnvelope(
  val accountId: String,
  val fetchedAtEpochMs: Long,
  val workers: List<CfWorkerScript> = emptyList(),
  val subdomain: String = "",
)

data class CachedPagesEnvelope(
  val accountId: String,
  val fetchedAtEpochMs: Long,
  val projects: List<CfPagesProject> = emptyList(),
)

data class CachedInfraEnvelope(
  val accountId: String,
  val fetchedAtEpochMs: Long,
  val kvNamespaces: List<CfKvNamespace> = emptyList(),
  val zones: List<CfZone> = emptyList(),
  val d1Databases: List<CfD1Database> = emptyList(),
  val r2Buckets: List<CfR2Bucket> = emptyList(),
  val durableObjects: List<CfDurableObjectNamespace> = emptyList(),
  val queues: List<CfQueue> = emptyList(),
)

data class CachedGitHubReposEnvelope(
  val scopeKey: String,
  val fetchedAtEpochMs: Long,
  val repos: List<GitHubRepo> = emptyList(),
  val user: GitHubUser? = null,
  val rateLimitLabel: String? = null,
)

/**
 * Persistent disk-backed API response cache with multi-hour TTL.
 * Prevents redundant Cloudflare and GitHub network calls on every cold launch or tab switch,
 * while honoring explicit manual refresh and write-through invalidation on mutations.
 */
class ApiResponseCacheStore(
  private val rootDir: File,
  moshi: Moshi,
) {
  private val workersAdapter = moshi.adapter(CachedWorkersEnvelope::class.java)
  private val pagesAdapter = moshi.adapter(CachedPagesEnvelope::class.java)
  private val infraAdapter = moshi.adapter(CachedInfraEnvelope::class.java)
  private val githubAdapter = moshi.adapter(CachedGitHubReposEnvelope::class.java)

  init {
    rootDir.mkdirs()
  }

  private fun sanitizeKey(raw: String): String =
    raw.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "_").ifBlank { "default" }.take(80)

  private fun fileFor(domain: String, scopeKey: String): File {
    rootDir.mkdirs()
    return File(rootDir, "${domain}_${sanitizeKey(scopeKey)}.json")
  }

  private fun writeAtomic(target: File, json: String) {
    runCatching {
      target.parentFile?.mkdirs()
      val tmp = File(target.parentFile, "${target.name}.tmp")
      tmp.writeText(json, Charsets.UTF_8)
      if (!tmp.renameTo(target)) {
        target.writeText(json, Charsets.UTF_8)
        tmp.delete()
      }
    }
  }

  fun loadWorkers(accountId: String): CachedWorkersEnvelope? =
    runCatching {
      val f = fileFor("cf_workers", accountId)
      if (!f.exists()) return null
      workersAdapter.fromJson(f.readText(Charsets.UTF_8))
    }.getOrNull()

  fun saveWorkers(
    accountId: String,
    workers: List<CfWorkerScript>,
    subdomain: String,
    nowEpochMs: Long = System.currentTimeMillis(),
  ) {
    val envelope =
      CachedWorkersEnvelope(
        accountId = accountId,
        fetchedAtEpochMs = nowEpochMs,
        workers = workers,
        subdomain = subdomain,
      )
    writeAtomic(fileFor("cf_workers", accountId), workersAdapter.toJson(envelope))
  }

  fun loadPages(accountId: String): CachedPagesEnvelope? =
    runCatching {
      val f = fileFor("cf_pages", accountId)
      if (!f.exists()) return null
      pagesAdapter.fromJson(f.readText(Charsets.UTF_8))
    }.getOrNull()

  fun savePages(
    accountId: String,
    projects: List<CfPagesProject>,
    nowEpochMs: Long = System.currentTimeMillis(),
  ) {
    val envelope =
      CachedPagesEnvelope(
        accountId = accountId,
        fetchedAtEpochMs = nowEpochMs,
        projects = projects,
      )
    writeAtomic(fileFor("cf_pages", accountId), pagesAdapter.toJson(envelope))
  }

  fun loadInfra(accountId: String): CachedInfraEnvelope? =
    runCatching {
      val f = fileFor("cf_infra", accountId)
      if (!f.exists()) return null
      infraAdapter.fromJson(f.readText(Charsets.UTF_8))
    }.getOrNull()

  fun saveInfra(
    accountId: String,
    kvNamespaces: List<CfKvNamespace>,
    zones: List<CfZone>,
    d1Databases: List<CfD1Database>,
    r2Buckets: List<CfR2Bucket>,
    durableObjects: List<CfDurableObjectNamespace>,
    queues: List<CfQueue>,
    nowEpochMs: Long = System.currentTimeMillis(),
  ) {
    val envelope =
      CachedInfraEnvelope(
        accountId = accountId,
        fetchedAtEpochMs = nowEpochMs,
        kvNamespaces = kvNamespaces,
        zones = zones,
        d1Databases = d1Databases,
        r2Buckets = r2Buckets,
        durableObjects = durableObjects,
        queues = queues,
      )
    writeAtomic(fileFor("cf_infra", accountId), infraAdapter.toJson(envelope))
  }

  fun loadGitHubRepos(scopeKey: String): CachedGitHubReposEnvelope? =
    runCatching {
      val f = fileFor("gh_repos", scopeKey)
      if (!f.exists()) return null
      githubAdapter.fromJson(f.readText(Charsets.UTF_8))
    }.getOrNull()

  fun saveGitHubRepos(
    scopeKey: String,
    repos: List<GitHubRepo>,
    user: GitHubUser?,
    rateLimitLabel: String?,
    nowEpochMs: Long = System.currentTimeMillis(),
  ) {
    val envelope =
      CachedGitHubReposEnvelope(
        scopeKey = scopeKey,
        fetchedAtEpochMs = nowEpochMs,
        repos = repos,
        user = user,
        rateLimitLabel = rateLimitLabel,
      )
    writeAtomic(fileFor("gh_repos", scopeKey), githubAdapter.toJson(envelope))
  }

  fun invalidateGitHub(scopeKey: String) {
    runCatching { fileFor("gh_repos", scopeKey).delete() }
  }

  fun clearAll(): Int {
    var count = 0
    runCatching {
      rootDir.listFiles()?.forEach { child ->
        if (child.isFile && child.delete()) count++
      }
    }
    return count
  }

  companion object {
    const val DEFAULT_TTL_HOURS = 4

    /**
     * Evaluates whether a cached API response is still valid under the user's configured interval.
     * - [ttlHours] == 0 means "Manual Only": any previously cached snapshot stays fresh until
     *   explicitly refreshed by the user or invalidated by a mutation.
     */
    fun isFresh(
      fetchedAtEpochMs: Long,
      ttlHours: Int,
      nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
      if (fetchedAtEpochMs <= 0L) return false
      if (ttlHours <= 0) return true
      val ageMs = nowEpochMs - fetchedAtEpochMs
      if (ageMs < 0L) return false
      return ageMs < ttlHours.toLong() * 3_600_000L
    }
  }
}
