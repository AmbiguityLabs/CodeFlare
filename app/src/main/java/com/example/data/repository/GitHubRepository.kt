package com.example.data.repository

import android.util.Base64
import com.example.data.remote.ClonedRepositoryBundle
import com.example.data.remote.GitHubApiService
import com.example.data.remote.GitHubAuthSessionInfo
import com.example.data.remote.GitHubBranch
import com.example.data.remote.GitHubCommitDetailResponse
import com.example.data.remote.GitHubCommitItem
import com.example.data.remote.GitHubCreateBlobRequest
import com.example.data.remote.GitHubCreateBranchRequest
import com.example.data.remote.GitHubCreateGitCommitRequest
import com.example.data.remote.GitHubCreateIssueRequest
import com.example.data.remote.GitHubCreatePullRequest
import com.example.data.remote.GitHubCreateRepoRequest
import com.example.data.remote.GitHubCreateTreeRequest
import com.example.data.remote.GitHubDiffFile
import com.example.data.remote.GitHubIssue
import com.example.data.remote.GitHubIssueCommentRequest
import com.example.data.remote.GitHubMergePrRequest
import com.example.data.remote.GitHubPrReviewRequest
import com.example.data.remote.GitHubPullRequest
import com.example.data.remote.GitHubPutFileRequest
import com.example.data.remote.GitHubRepo
import com.example.data.remote.GitHubTreeEntryInput
import com.example.data.remote.GitHubTreeItem
import com.example.data.remote.GitHubUpdateIssueRequest
import com.example.data.remote.GitHubUpdateRefRequest
import com.example.data.remote.GitHubUser
import com.example.data.remote.GitHubWorkflow
import com.example.data.remote.GitHubWorkflowDispatchRequest
import com.example.data.remote.GitHubWorkflowJob
import com.example.data.remote.GitHubWorkflowRun
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.WorkerFile
import com.example.data.security.AuthPreferencesStore
import com.example.data.security.GitHubAuthMethod
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

data class ParsedRepoReference(
  val owner: String,
  val repo: String,
  val branch: String? = null,
)

class GitHubRepository(
  private val prefsStore: AuthPreferencesStore,
  moshi: Moshi,
) {
  private val client =
    com.example.data.remote.SharedHttpClientProvider.newBuilder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(35, TimeUnit.SECONDS)
      .build()

  private val api: GitHubApiService =
    Retrofit.Builder()
      .baseUrl("https://api.github.com/")
      .client(client)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(GitHubApiService::class.java)

  private fun authHeader(token: String) = "Bearer $token"

  private fun optionalAuthHeader(token: String?): String? =
    token?.trim()?.takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

  fun parseRepoReference(rawInput: String): ParsedRepoReference? {
    val trimmed = rawInput.trim().removeSuffix("/")
    if (trimmed.isEmpty()) return null

    // Handle git@github.com:owner/repo.git
    val sshRegex = Regex("""^git@github\.com:([^/]+)/([^/\s]+?)(?:\.git)?$""")
    sshRegex.matchEntire(trimmed)?.let { m ->
      return ParsedRepoReference(owner = m.groupValues[1], repo = m.groupValues[2])
    }

    // Handle https://github.com/owner/repo[/tree/branch]
    val urlRegex = Regex("""^https?://(?:www\.)?github\.com/([^/]+)/([^/\s]+?)(?:\.git)?(?:/tree/([^\s]+))?/?$""")
    urlRegex.matchEntire(trimmed)?.let { m ->
      val branch = m.groupValues[3].takeIf { it.isNotBlank() }
      return ParsedRepoReference(
        owner = m.groupValues[1],
        repo = m.groupValues[2].removeSuffix(".git"),
        branch = branch,
      )
    }

    // Handle owner/repo
    val shortRegex = Regex("""^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?$""")
    shortRegex.matchEntire(trimmed)?.let { m ->
      return ParsedRepoReference(owner = m.groupValues[1], repo = m.groupValues[2])
    }

    return null
  }

  suspend fun verifyAndSaveToken(rawToken: String): Result<GitHubUser> =
    verifyAndSaveSession(rawToken, GitHubAuthMethod.PAT).map { it.user }

  suspend fun verifyAndSaveSession(
    rawToken: String,
    method: GitHubAuthMethod,
    scopesOverride: String = "",
  ): Result<GitHubAuthSessionInfo> {
    val clean = rawToken.trim()
    if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Enter a valid GitHub access token"))
    return fetchSessionInfo(clean, scopesOverride).onSuccess { info ->
      prefsStore.saveGitHubCredentials(
        token = clean,
        login = info.user.login,
        method = method,
        scopes = info.scopes,
        avatarUrl = info.user.avatarUrl.orEmpty(),
        displayName = info.user.name.orEmpty(),
      )
    }
  }

  suspend fun fetchSessionInfo(
    token: String,
    scopesOverride: String = "",
  ): Result<GitHubAuthSessionInfo> {
    return try {
      val r = api.getCurrentUser(authHeader(token))
      if (r.isSuccessful && r.body() != null) {
        val user = r.body()!!
        val headerScopes = r.headers()["X-OAuth-Scopes"].orEmpty().trim()
        val effectiveScopes =
          when {
            headerScopes.isNotBlank() -> headerScopes
            scopesOverride.isNotBlank() -> scopesOverride
            token.startsWith("github_pat_") -> "fine-grained-pat"
            else -> "repo, workflow"
          }
        val remaining = r.headers()["X-RateLimit-Remaining"]?.toIntOrNull()
        val limit = r.headers()["X-RateLimit-Limit"]?.toIntOrNull()
        Result.success(
          GitHubAuthSessionInfo(
            user = user,
            scopes = effectiveScopes,
            rateLimitRemaining = remaining,
            rateLimitLimit = limit,
          ),
        )
      } else {
        val err = r.errorBody()?.string().orEmpty()
        Result.failure(IllegalStateException("GitHub API ${r.code()}: ${err.take(200)}"))
      }
    } catch (t: Throwable) {
      Result.failure(t)
    }
  }

  suspend fun signOut() {
    prefsStore.clearGitHubCredentials()
  }

  suspend fun getCurrentUser(token: String): Result<GitHubUser> =
    safeCall { api.getCurrentUser(authHeader(token)) }

  suspend fun listRepos(token: String): Result<List<GitHubRepo>> =
    safeCall { api.listUserRepos(authHeader(token)) }

  suspend fun createRepository(
    token: String,
    name: String,
    description: String,
    isPrivate: Boolean,
    autoInit: Boolean = true,
  ): Result<GitHubRepo> =
    safeCall {
      api.createRepo(
        authHeader = authHeader(token),
        body =
          GitHubCreateRepoRequest(
            name = name.trim(),
            description = description.ifBlank { "Cloudflare Worker managed in EdgeCode" },
            private = isPrivate,
            autoInit = autoInit,
          ),
      )
    }

  suspend fun getRepo(token: String?, owner: String, repo: String): Result<GitHubRepo> =
    safeCall { api.getRepo(optionalAuthHeader(token), owner, repo) }

  suspend fun listBranches(token: String?, owner: String, repo: String): Result<List<GitHubBranch>> =
    safeCall { api.listBranches(optionalAuthHeader(token), owner, repo) }

  suspend fun listRepoFiles(
    token: String?,
    owner: String,
    repo: String,
    branch: String,
  ): Result<List<GitHubTreeItem>> {
    return safeCall { api.getRecursiveTree(optionalAuthHeader(token), owner, repo, branch) }.map { tree ->
      tree.tree.filter { it.type == "blob" && shouldIncludeClonePath(it.path) }
    }
  }

  suspend fun fetchFileText(
    token: String?,
    owner: String,
    repo: String,
    branch: String,
    path: String,
  ): Result<String> {
    return safeCall { api.getFileContents(optionalAuthHeader(token), owner, repo, path, branch) }.map { item ->
      val rawB64 = item.content.orEmpty().replace("\n", "")
      if (rawB64.isEmpty()) ""
      else String(Base64.decode(rawB64, Base64.DEFAULT), Charsets.UTF_8)
    }
  }

  suspend fun cloneRepository(
    token: String?,
    owner: String,
    repoName: String,
    branchOverride: String? = null,
  ): Result<ClonedRepositoryBundle> =
    runCatching {
      val repoMeta = getRepo(token, owner, repoName).getOrThrow()
      val targetBranch = branchOverride?.takeIf { it.isNotBlank() } ?: repoMeta.defaultBranch.ifBlank { "main" }
      val optHeader = optionalAuthHeader(token)

      val headRef = runCatching { api.getBranchRef(optHeader, owner, repoName, targetBranch) }.getOrNull()
      val headSha =
        if (headRef?.isSuccessful == true) headRef.body()?.obj?.sha.orEmpty()
        else ""

      val treeItems = listRepoFiles(token, owner, repoName, targetBranch).getOrDefault(emptyList())
      val bounded = treeItems.take(80)
      val semaphore = Semaphore(6)
      val downloaded =
        coroutineScope {
          bounded
            .map { item ->
              async(Dispatchers.IO) {
                semaphore.withPermit {
                  val text = fetchFileText(token, owner, repoName, targetBranch, item.path).getOrNull()
                    ?: return@withPermit null
                  item.path to text
                }
              }
            }
            .awaitAll()
            .filterNotNull()
        }

      val wranglerToml = downloaded.firstOrNull { it.first.equals("wrangler.toml", true) }?.second
      val wranglerJson =
        downloaded.firstOrNull { it.first.equals("wrangler.jsonc", true) || it.first.equals("wrangler.json", true) }?.second

      val detectedWorkerName =
        extractWranglerTomlField(wranglerToml, "name")
          ?: extractJsonField(wranglerJson, "name")
          ?: repoMeta.name.lowercase().replace(Regex("[^a-z0-9-]"), "-")

      val detectedCompat =
        extractWranglerTomlField(wranglerToml, "compatibility_date")
          ?: extractJsonField(wranglerJson, "compatibility_date")
          ?: "2024-11-01"

      val rawMain =
        extractWranglerTomlField(wranglerToml, "main")
          ?: extractJsonField(wranglerJson, "main")

      val allPaths = downloaded.map { it.first }
      val detectedEntry =
        rawMain?.removePrefix("./")?.takeIf { p -> p in allPaths }
          ?: allPaths.firstOrNull { it in listOf("src/index.ts", "src/index.js", "src/worker.js", "src/worker.ts", "index.js", "index.ts", "worker.js") }
          ?: allPaths.firstOrNull { it.endsWith(".js") || it.endsWith(".ts") || it.endsWith(".mjs") }
          ?: allPaths.firstOrNull()
          ?: "src/index.js"

      val workerFiles =
        if (downloaded.isEmpty()) {
          listOf(
            WorkerFile(
              path = "src/index.js",
              content = "// Cloned empty repo ${repoMeta.fullName}\nexport default {\n  async fetch(request, env, ctx) {\n    return new Response(\"Hello from ${repoMeta.name}!\");\n  }\n};\n",
              originalContent = "",
              contentType = "application/javascript+module",
              isEntryPoint = true,
            ),
          )
        } else {
          downloaded.map { (path, content) ->
            WorkerFile(
              path = path,
              content = content,
              originalContent = content,
              contentType = MultipartWorkerParser.inferModuleMimeType(path),
              isEntryPoint = path == detectedEntry,
            )
          }
        }

      ClonedRepositoryBundle(
        repo = repoMeta,
        branch = targetBranch,
        headCommitSha = headSha,
        files = workerFiles,
        detectedWorkerName = detectedWorkerName,
        detectedEntryPath = detectedEntry,
        detectedCompatibilityDate = detectedCompat,
      )
    }

  suspend fun pullSelectedOrWorkerFiles(
    token: String?,
    owner: String,
    repo: String,
    branch: String,
    paths: List<String>,
  ): Result<List<WorkerFile>> =
    runCatching {
      val semaphore = Semaphore(6)
      coroutineScope {
        paths.take(50)
          .map { path ->
            async(Dispatchers.IO) {
              semaphore.withPermit {
                val text = fetchFileText(token, owner, repo, branch, path).getOrNull() ?: return@withPermit null
                WorkerFile(
                  path = path,
                  content = text,
                  originalContent = text,
                  contentType = MultipartWorkerParser.inferModuleMimeType(path),
                  isEntryPoint =
                    path == "src/index.js" ||
                      path == "index.js" ||
                      path == "src/index.ts" ||
                      path == "src/worker.js",
                )
              }
            }
          }
          .awaitAll()
          .filterNotNull()
      }
    }

  /**
   * Atomically pushes updated/added and deleted files as ONE Git commit via GitHub's Git Data API.
   * Falls back to Contents API when pushing the very first commit to an uninitialized repo.
   */
  suspend fun pushAtomicChanges(
    token: String,
    owner: String,
    repo: String,
    branch: String,
    commitMessage: String,
    updatedFiles: Map<String, String>,
    deletedPaths: Set<String> = emptySet(),
  ): Result<String> =
    runCatching {
      val header = authHeader(token)
      val cleanMsg = commitMessage.ifBlank { "Update Worker via EdgeCode" }

      val refResp = api.getBranchRef(header, owner, repo, branch)
      if (!refResp.isSuccessful || refResp.body()?.obj?.sha.isNullOrBlank()) {
        // Fallback for empty/unborn branch repository
        var pushed = 0
        for ((path, content) in updatedFiles) {
          val b64Content = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
          val putResp =
            api.putFileContents(
              authHeader = header,
              owner = owner,
              repo = repo,
              filePath = path,
              body =
                GitHubPutFileRequest(
                  message = cleanMsg,
                  content = b64Content,
                  branch = branch,
                  sha = null,
                ),
            )
          if (!putResp.isSuccessful) {
            error("Push failed for $path: HTTP ${putResp.code()} ${putResp.errorBody()?.string().orEmpty()}")
          }
          pushed++
        }
        return@runCatching "initial ($pushed files)"
      }

      val parentCommitSha = refResp.body()!!.obj.sha
      val commitResp = api.getGitCommit(header, owner, repo, parentCommitSha)
      if (!commitResp.isSuccessful) {
        error("Failed to fetch HEAD commit $parentCommitSha: HTTP ${commitResp.code()}")
      }
      val baseTreeSha = commitResp.body()?.tree?.sha.orEmpty()

      // Create blobs concurrently
      val semaphore = Semaphore(6)
      val treeEntries =
        coroutineScope {
          val updatedDeferred =
            updatedFiles.entries.map { (path, content) ->
              async(Dispatchers.IO) {
                semaphore.withPermit {
                  val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                  val blobResp = api.createBlob(header, owner, repo, GitHubCreateBlobRequest(content = b64))
                  if (!blobResp.isSuccessful || blobResp.body()?.sha.isNullOrBlank()) {
                    error("Failed creating blob for $path: HTTP ${blobResp.code()}")
                  }
                  GitHubTreeEntryInput(
                    path = path,
                    mode = "100644",
                    type = "blob",
                    sha = blobResp.body()!!.sha,
                  )
                }
              }
            }
          val deletedEntries =
            deletedPaths.map { delPath ->
              GitHubTreeEntryInput(
                path = delPath,
                mode = "100644",
                type = "blob",
                sha = null,
              )
            }
          updatedDeferred.awaitAll() + deletedEntries
        }

      if (treeEntries.isEmpty()) {
        error("No staged file changes to commit and push.")
      }

      val newTreeResp = api.createTree(header, owner, repo, GitHubCreateTreeRequest(baseTree = baseTreeSha, tree = treeEntries))
      if (!newTreeResp.isSuccessful || newTreeResp.body()?.sha.isNullOrBlank()) {
        error("Failed creating Git tree: HTTP ${newTreeResp.code()} ${newTreeResp.errorBody()?.string().orEmpty()}")
      }
      val newTreeSha = newTreeResp.body()!!.sha

      val newCommitResp =
        api.createGitCommit(
          header,
          owner,
          repo,
          GitHubCreateGitCommitRequest(
            message = cleanMsg,
            tree = newTreeSha,
            parents = listOf(parentCommitSha),
          ),
        )
      if (!newCommitResp.isSuccessful || newCommitResp.body()?.sha.isNullOrBlank()) {
        error("Failed creating Git commit: HTTP ${newCommitResp.code()} ${newCommitResp.errorBody()?.string().orEmpty()}")
      }
      val newCommitSha = newCommitResp.body()!!.sha

      val patchResp =
        api.updateBranchRef(
          header,
          owner,
          repo,
          branch,
          GitHubUpdateRefRequest(sha = newCommitSha, force = false),
        )
      if (!patchResp.isSuccessful) {
        error("Failed updating branch ref '$branch': HTTP ${patchResp.code()} ${patchResp.errorBody()?.string().orEmpty()}")
      }

      newCommitSha
    }

  suspend fun createBranch(
    token: String,
    owner: String,
    repo: String,
    newBranchName: String,
    fromSha: String,
  ): Result<Unit> =
    safeCall {
      api.createBranchRef(
        authHeader = authHeader(token),
        owner = owner,
        repo = repo,
        body = GitHubCreateBranchRequest(ref = "refs/heads/${newBranchName.trim()}", sha = fromSha),
      )
    }.map { Unit }

  suspend fun listCommits(
    token: String?,
    owner: String,
    repo: String,
    branch: String,
  ): Result<List<GitHubCommitItem>> =
    safeCall { api.listCommits(optionalAuthHeader(token), owner, repo, branch) }

  suspend fun listWorkflowRuns(
    token: String,
    owner: String,
    repo: String,
  ): Result<List<GitHubWorkflowRun>> =
    safeCall { api.listWorkflowRuns(authHeader(token), owner, repo) }.map { it.workflowRuns }

  suspend fun listWorkflows(
    token: String,
    owner: String,
    repo: String,
  ): Result<List<GitHubWorkflow>> =
    safeCall { api.listWorkflows(authHeader(token), owner, repo) }.map { it.workflows }

  suspend fun dispatchWorkflow(
    token: String,
    owner: String,
    repo: String,
    workflowId: Long,
    branch: String,
  ): Result<Unit> =
    safeUnitCall {
      api.dispatchWorkflow(
        authHeader(token),
        owner,
        repo,
        workflowId,
        GitHubWorkflowDispatchRequest(ref = branch),
      )
    }

  suspend fun rerunWorkflow(
    token: String,
    owner: String,
    repo: String,
    runId: Long,
  ): Result<Unit> =
    safeUnitCall { api.rerunWorkflow(authHeader(token), owner, repo, runId) }

  suspend fun listWorkflowRunJobs(
    token: String,
    owner: String,
    repo: String,
    runId: Long,
  ): Result<List<GitHubWorkflowJob>> =
    safeCall { api.listWorkflowRunJobs(authHeader(token), owner, repo, runId) }.map { it.jobs }

  suspend fun getCommitDetail(
    token: String?,
    owner: String,
    repo: String,
    commitSha: String,
  ): Result<GitHubCommitDetailResponse> =
    safeCall { api.getCommitDetail(optionalAuthHeader(token), owner, repo, commitSha) }

  suspend fun listPullRequests(
    token: String?,
    owner: String,
    repo: String,
    state: String = "open",
  ): Result<List<GitHubPullRequest>> =
    safeCall { api.listPullRequests(optionalAuthHeader(token), owner, repo, state) }

  suspend fun listPullRequestFiles(
    token: String?,
    owner: String,
    repo: String,
    pullNumber: Int,
  ): Result<List<GitHubDiffFile>> =
    safeCall { api.listPullRequestFiles(optionalAuthHeader(token), owner, repo, pullNumber) }

  suspend fun createPullRequest(
    token: String,
    owner: String,
    repo: String,
    title: String,
    body: String,
    headBranch: String,
    baseBranch: String,
  ): Result<GitHubPullRequest> =
    safeCall {
      api.createPullRequest(
        authHeader(token),
        owner,
        repo,
        GitHubCreatePullRequest(title = title.trim(), body = body.trim(), head = headBranch, base = baseBranch),
      )
    }

  suspend fun mergePullRequest(
    token: String,
    owner: String,
    repo: String,
    pullNumber: Int,
    mergeMethod: String = "squash",
  ): Result<Unit> =
    safeCall {
      api.mergePullRequest(
        authHeader(token),
        owner,
        repo,
        pullNumber,
        GitHubMergePrRequest(mergeMethod = mergeMethod),
      )
    }.map { Unit }

  suspend fun submitPullRequestReview(
    token: String,
    owner: String,
    repo: String,
    pullNumber: Int,
    event: String,
    body: String,
  ): Result<Unit> =
    safeCall {
      api.submitPullRequestReview(
        authHeader(token),
        owner,
        repo,
        pullNumber,
        GitHubPrReviewRequest(body = body.trim(), event = event),
      )
    }.map { Unit }

  suspend fun listIssues(
    token: String?,
    owner: String,
    repo: String,
    state: String = "open",
  ): Result<List<GitHubIssue>> =
    safeCall { api.listIssues(optionalAuthHeader(token), owner, repo, state) }
      .map { items -> items.filter { it.pullRequest == null } }

  suspend fun createIssue(
    token: String,
    owner: String,
    repo: String,
    title: String,
    body: String,
  ): Result<GitHubIssue> =
    safeCall {
      api.createIssue(authHeader(token), owner, repo, GitHubCreateIssueRequest(title = title.trim(), body = body.trim()))
    }

  suspend fun updateIssueState(
    token: String,
    owner: String,
    repo: String,
    issueNumber: Int,
    state: String,
  ): Result<GitHubIssue> =
    safeCall {
      api.updateIssue(authHeader(token), owner, repo, issueNumber, GitHubUpdateIssueRequest(state = state))
    }

  suspend fun commentOnIssue(
    token: String,
    owner: String,
    repo: String,
    issueNumber: Int,
    comment: String,
  ): Result<Unit> =
    safeCall {
      api.commentOnIssue(authHeader(token), owner, repo, issueNumber, GitHubIssueCommentRequest(body = comment.trim()))
    }.map { Unit }

  private suspend fun safeUnitCall(block: suspend () -> Response<Unit>): Result<Unit> {
    return try {
      val r = block()
      if (r.isSuccessful) Result.success(Unit)
      else Result.failure(IllegalStateException("GitHub API ${r.code()}: ${r.errorBody()?.string().orEmpty().take(200)}"))
    } catch (t: Throwable) {
      Result.failure(t)
    }
  }

  private fun extractWranglerTomlField(toml: String?, key: String): String? {
    if (toml.isNullOrBlank()) return null
    val regex = Regex("""(?m)^\s*${Regex.escape(key)}\s*=\s*["']([^"']+)["']""")
    return regex.find(toml)?.groupValues?.get(1)?.trim()
  }

  private fun extractJsonField(json: String?, key: String): String? {
    if (json.isNullOrBlank()) return null
    val regex = Regex(""""${Regex.escape(key)}"\s*:\s*"([^"]+)"""")
    return regex.find(json)?.groupValues?.get(1)?.trim()
  }

  private fun shouldIncludeClonePath(path: String): Boolean {
    val lower = path.lowercase()
    if (lower.startsWith("node_modules/") || lower.contains("/node_modules/") ||
      lower.startsWith(".git/") || lower.startsWith("dist/") || lower.startsWith(".wrangler/")
    ) {
      return false
    }
    return isEditableTextAsset(path)
  }

  private fun isEditableTextAsset(path: String): Boolean {
    val lower = path.lowercase()
    val name = lower.substringAfterLast('/')
    return lower.endsWith(".js") ||
      lower.endsWith(".mjs") ||
      lower.endsWith(".ts") ||
      lower.endsWith(".mts") ||
      lower.endsWith(".jsx") ||
      lower.endsWith(".tsx") ||
      lower.endsWith(".json") ||
      lower.endsWith(".jsonc") ||
      lower.endsWith(".toml") ||
      lower.endsWith(".html") ||
      lower.endsWith(".css") ||
      lower.endsWith(".svg") ||
      lower.endsWith(".sql") ||
      lower.endsWith(".md") ||
      lower.endsWith(".txt") ||
      lower.endsWith(".yml") ||
      lower.endsWith(".yaml") ||
      lower.endsWith(".sh") ||
      lower.endsWith(".env.example") ||
      name == ".gitignore" ||
      name == "_headers" ||
      name == "_redirects"
  }

  private suspend fun <T> safeCall(block: suspend () -> Response<T>): Result<T> {
    return try {
      val r = block()
      if (r.isSuccessful) {
        val body = r.body()
        if (body != null) Result.success(body)
        else Result.failure(IllegalStateException("Empty GitHub response"))
      } else {
        val err = r.errorBody()?.string().orEmpty()
        Result.failure(IllegalStateException("GitHub API ${r.code()}: ${err.take(200)}"))
      }
    } catch (t: Throwable) {
      Result.failure(t)
    }
  }
}
