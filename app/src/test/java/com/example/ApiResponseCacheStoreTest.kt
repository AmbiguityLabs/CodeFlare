package com.example

import com.example.data.local.ApiResponseCacheStore
import com.example.data.remote.CfD1Database
import com.example.data.remote.CfKvNamespace
import com.example.data.remote.CfPagesProject
import com.example.data.remote.CfWorkerScript
import com.example.data.remote.GitHubRepo
import com.example.data.remote.GitHubUser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResponseCacheStoreTest {
  private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

  @Test
  fun ttlFreshnessRespectsHoursAndManualOnlyMode() {
    val now = 1_750_000_000_000L
    val twoHoursAgo = now - 2L * 3_600_000L
    val fiveHoursAgo = now - 5L * 3_600_000L

    assertTrue(ApiResponseCacheStore.isFresh(twoHoursAgo, ttlHours = 4, nowEpochMs = now))
    assertFalse(ApiResponseCacheStore.isFresh(fiveHoursAgo, ttlHours = 4, nowEpochMs = now))
    assertFalse(ApiResponseCacheStore.isFresh(0L, ttlHours = 4, nowEpochMs = now))
    // Manual-only (0h) keeps existing cache fresh indefinitely until manual refresh
    assertTrue(ApiResponseCacheStore.isFresh(fiveHoursAgo, ttlHours = 0, nowEpochMs = now))
    assertFalse(ApiResponseCacheStore.isFresh(0L, ttlHours = 0, nowEpochMs = now))
  }

  @Test
  fun persistsAndClearsWorkersPagesInfraAndGitHubSnapshots() {
    val tempDir = createTempDirectory("edgecode_api_cache_test").toFile()
    try {
      val store = ApiResponseCacheStore(tempDir, moshi)
      val t0 = 1_750_000_000_000L

      store.saveWorkers(
        accountId = "acc-1",
        workers = listOf(CfWorkerScript(id = "api-worker")),
        subdomain = "my-edge",
        nowEpochMs = t0,
      )
      store.savePages(
        accountId = "acc-1",
        projects = listOf(CfPagesProject(id = "p1", name = "docs-site")),
        nowEpochMs = t0,
      )
      store.saveInfra(
        accountId = "acc-1",
        kvNamespaces = listOf(CfKvNamespace(id = "kv1", title = "SESSIONS")),
        zones = emptyList(),
        d1Databases = listOf(CfD1Database(uuid = "d1-1", name = "app_db")),
        r2Buckets = emptyList(),
        durableObjects = emptyList(),
        queues = emptyList(),
        nowEpochMs = t0,
      )
      store.saveGitHubRepos(
        scopeKey = "octocat",
        repos = listOf(GitHubRepo(id = 42L, name = "edge-starter", fullName = "octocat/edge-starter")),
        user = GitHubUser(login = "octocat", name = "Octocat"),
        rateLimitLabel = "4990/5000 req",
        nowEpochMs = t0,
      )

      val workers = store.loadWorkers("acc-1")
      assertNotNull(workers)
      assertEquals("api-worker", workers?.workers?.single()?.id)
      assertEquals("my-edge", workers?.subdomain)

      val pages = store.loadPages("acc-1")
      assertEquals("docs-site", pages?.projects?.single()?.name)

      val infra = store.loadInfra("acc-1")
      assertEquals("SESSIONS", infra?.kvNamespaces?.single()?.title)
      assertEquals("app_db", infra?.d1Databases?.single()?.name)

      val gh = store.loadGitHubRepos("octocat")
      assertEquals("octocat/edge-starter", gh?.repos?.single()?.fullName)
      assertEquals("4990/5000 req", gh?.rateLimitLabel)

      val deletedCount = store.clearAll()
      assertEquals(4, deletedCount)
      assertNull(store.loadWorkers("acc-1"))
      assertNull(store.loadPages("acc-1"))
      assertNull(store.loadInfra("acc-1"))
      assertNull(store.loadGitHubRepos("octocat"))
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
