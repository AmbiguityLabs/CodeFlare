package com.example

import com.example.data.remote.GitFileChangeStatus
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import com.example.data.repository.GitHubRepository
import com.example.data.security.AuthPreferencesStore
import com.example.ui.viewmodel.IdeUiState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitIntegrationTest {
  private fun makeRepo(): GitHubRepository {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val store = AuthPreferencesStore(ctx)
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    return GitHubRepository(store, moshi)
  }

  @Test
  fun parseRepoReference_supportsHttpsSshAndSlug() {
    val repo = makeRepo()

    val https = repo.parseRepoReference("https://github.com/cloudflare/workers-sdk.git")
    assertNotNull(https)
    assertEquals("cloudflare", https?.owner)
    assertEquals("workers-sdk", https?.repo)
    assertNull(https?.branch)

    val treeUrl = repo.parseRepoReference("https://github.com/acme/edge-api/tree/feat-kv")
    assertNotNull(treeUrl)
    assertEquals("acme", treeUrl?.owner)
    assertEquals("edge-api", treeUrl?.repo)
    assertEquals("feat-kv", treeUrl?.branch)

    val ssh = repo.parseRepoReference("git@github.com:acme/worker-proxy.git")
    assertNotNull(ssh)
    assertEquals("acme", ssh?.owner)
    assertEquals("worker-proxy", ssh?.repo)

    val slug = repo.parseRepoReference("acme/my-worker")
    assertNotNull(slug)
    assertEquals("acme", slug?.owner)
    assertEquals("my-worker", slug?.repo)

    val slashBranch = repo.parseRepoReference("https://github.com/acme/edge-api/tree/feat/kv-sync")
    assertNotNull(slashBranch)
    assertEquals("acme", slashBranch?.owner)
    assertEquals("edge-api", slashBranch?.repo)
    assertEquals("feat/kv-sync", slashBranch?.branch)
  }

  @Test
  fun githubOAuthManager_providesValidDeviceEndpointsAndScopes() {
    assertEquals("https://github.com/login/device/code", com.example.data.auth.GitHubOAuthManager.DEVICE_CODE_URL)
    assertEquals("https://github.com/login/oauth/access_token", com.example.data.auth.GitHubOAuthManager.ACCESS_TOKEN_URL)
    val patUrl = com.example.data.auth.GitHubOAuthManager.buildClassicPatCreationUrl()
    assertEquals(true, patUrl.contains("scopes=repo,workflow"))
  }

  @Test
  fun gitChangedFiles_detectsAddedModifiedDeletedAndStaged() {
    val state =
      IdeUiState(
        workspace =
          WorkerWorkspace(
            scriptName = "demo-worker",
            isEsModuleFormat = true,
            entryModulePath = "src/index.js",
            compatibilityDate = "2024-11-01",
            files =
              listOf(
                WorkerFile("src/index.js", "export default { fetch() { return new Response('v2'); } }", "old", "application/javascript+module", true),
                WorkerFile("src/router.js", "export const route = '/';", "", "application/javascript+module", false),
              ),
            bindings = emptyList(),
            routes = emptyList(),
            workersDevEnabled = false,
            accountSubdomain = "",
          ),
        gitWorkingBaseline =
          mapOf(
            "src/index.js" to "export default { fetch() { return new Response('v1'); } }",
            "src/legacy.js" to "// removed",
          ),
        gitUnstagedExcludedPaths = setOf("src/router.js"),
      )

    val changes = state.gitChangedFiles.associateBy { it.path }
    assertEquals(3, changes.size)
    assertEquals(GitFileChangeStatus.MODIFIED, changes["src/index.js"]?.status)
    assertEquals(true, changes["src/index.js"]?.isStaged)
    assertEquals(GitFileChangeStatus.ADDED, changes["src/router.js"]?.status)
    assertEquals(false, changes["src/router.js"]?.isStaged)
    assertEquals(GitFileChangeStatus.DELETED, changes["src/legacy.js"]?.status)
    assertEquals(true, changes["src/legacy.js"]?.isStaged)
  }
}
