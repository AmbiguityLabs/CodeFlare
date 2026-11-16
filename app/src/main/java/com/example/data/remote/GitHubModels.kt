package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUser(
  val login: String = "",
  val id: Long = 0L,
  val name: String? = null,
  val email: String? = null,
  @Json(name = "avatar_url") val avatarUrl: String? = null,
  @Json(name = "html_url") val htmlUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubOwner(
  val login: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubRepo(
  val id: Long = 0L,
  val name: String = "",
  @Json(name = "full_name") val fullName: String = "",
  val private: Boolean = false,
  val owner: GitHubOwner = GitHubOwner(),
  val description: String? = null,
  @Json(name = "default_branch") val defaultBranch: String = "main",
  @Json(name = "html_url") val htmlUrl: String = "",
  @Json(name = "updated_at") val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubCommitRef(
  val sha: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubBranch(
  val name: String = "",
  val commit: GitHubCommitRef = GitHubCommitRef(),
)

@JsonClass(generateAdapter = true)
data class GitHubTreeItem(
  val path: String = "",
  val type: String = "", // "blob" or "tree"
  val sha: String = "",
  val size: Long? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubTreeResponse(
  val sha: String = "",
  val tree: List<GitHubTreeItem> = emptyList(),
  val truncated: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class GitHubFileContent(
  val name: String = "",
  val path: String = "",
  val sha: String = "",
  val content: String? = null,
  val encoding: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubPutFileRequest(
  val message: String,
  val content: String, // Base64 encoded
  val branch: String,
  val sha: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubCommitAuthor(
  val name: String = "",
  val date: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubCommitDetails(
  val message: String = "",
  val author: GitHubCommitAuthor? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubCommitItem(
  val sha: String = "",
  val commit: GitHubCommitDetails = GitHubCommitDetails(),
  @Json(name = "html_url") val htmlUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRun(
  val id: Long = 0L,
  val name: String? = null,
  @Json(name = "head_branch") val headBranch: String? = null,
  val status: String? = null,
  val conclusion: String? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "html_url") val htmlUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRunsResponse(
  @Json(name = "workflow_runs") val workflowRuns: List<GitHubWorkflowRun> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GitHubCreateBranchRequest(
  val ref: String, // "refs/heads/new-branch"
  val sha: String,
)

@JsonClass(generateAdapter = true)
data class GitHubCreateRepoRequest(
  val name: String,
  val description: String = "Cloudflare Worker managed in EdgeCode",
  val private: Boolean = true,
  @Json(name = "auto_init") val autoInit: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class GitHubGitRefResponse(
  val ref: String = "",
  @Json(name = "object") val obj: GitHubCommitRef = GitHubCommitRef(),
)

@JsonClass(generateAdapter = true)
data class GitHubGitCommitResponse(
  val sha: String = "",
  val tree: GitHubCommitRef = GitHubCommitRef(),
)

@JsonClass(generateAdapter = true)
data class GitHubCreateBlobRequest(
  val content: String,
  val encoding: String = "base64",
)

@JsonClass(generateAdapter = true)
data class GitHubBlobResponse(
  val sha: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubTreeEntryInput(
  val path: String,
  val mode: String = "100644",
  val type: String = "blob",
  val sha: String?,
)

@JsonClass(generateAdapter = true)
data class GitHubCreateTreeRequest(
  @Json(name = "base_tree") val baseTree: String,
  val tree: List<GitHubTreeEntryInput>,
)

@JsonClass(generateAdapter = true)
data class GitHubCreateGitCommitRequest(
  val message: String,
  val tree: String,
  val parents: List<String>,
)

@JsonClass(generateAdapter = true)
data class GitHubUpdateRefRequest(
  val sha: String,
  val force: Boolean = false,
)

enum class GitFileChangeStatus {
  ADDED,
  MODIFIED,
  DELETED,
}

data class GitChangedFile(
  val path: String,
  val status: GitFileChangeStatus,
  val isStaged: Boolean,
  val currentContent: String,
  val baseContent: String,
)

data class LocalGitCommit(
  val id: String,
  val message: String,
  val timestamp: String,
  val author: String,
  val updatedFiles: Map<String, String>,
  val deletedPaths: Set<String>,
  val changedCount: Int,
)

data class LinkedGitRepoState(
  val owner: String,
  val repoName: String,
  val fullName: String,
  val branch: String,
  val headCommitSha: String,
  val baselineFiles: Map<String, String>,
  val clonedAtLabel: String,
)

data class ClonedRepositoryBundle(
  val repo: GitHubRepo,
  val branch: String,
  val headCommitSha: String,
  val files: List<WorkerFile>,
  val detectedWorkerName: String,
  val detectedEntryPath: String,
  val detectedCompatibilityDate: String,
)

data class GitHubAuthSessionInfo(
  val user: GitHubUser,
  val scopes: String,
  val rateLimitRemaining: Int?,
  val rateLimitLimit: Int?,
)

@JsonClass(generateAdapter = true)
data class GitHubPrBranchRef(
  val ref: String = "",
  val sha: String = "",
  val label: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubPullRequest(
  val id: Long = 0L,
  val number: Int = 0,
  val title: String = "",
  val state: String = "open",
  val body: String? = null,
  val draft: Boolean = false,
  val user: GitHubOwner = GitHubOwner(),
  val head: GitHubPrBranchRef = GitHubPrBranchRef(),
  val base: GitHubPrBranchRef = GitHubPrBranchRef(),
  @Json(name = "merged_at") val mergedAt: String? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "html_url") val htmlUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubCreatePullRequest(
  val title: String,
  val body: String = "",
  val head: String,
  val base: String,
  val draft: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class GitHubMergePrRequest(
  @Json(name = "commit_title") val commitTitle: String? = null,
  @Json(name = "merge_method") val mergeMethod: String = "squash",
)

@JsonClass(generateAdapter = true)
data class GitHubDiffFile(
  val filename: String = "",
  val status: String = "modified",
  val additions: Int = 0,
  val deletions: Int = 0,
  val changes: Int = 0,
  val patch: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubCommitDetailResponse(
  val sha: String = "",
  val commit: GitHubCommitDetails = GitHubCommitDetails(),
  val files: List<GitHubDiffFile> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GitHubLabel(
  val name: String = "",
  val color: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubIssue(
  val id: Long = 0L,
  val number: Int = 0,
  val title: String = "",
  val state: String = "open",
  val body: String? = null,
  val user: GitHubOwner = GitHubOwner(),
  val comments: Int = 0,
  val labels: List<GitHubLabel> = emptyList(),
  @Json(name = "pull_request") val pullRequest: Map<String, @JvmSuppressWildcards Any?>? = null,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "html_url") val htmlUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubCreateIssueRequest(
  val title: String,
  val body: String = "",
)

@JsonClass(generateAdapter = true)
data class GitHubUpdateIssueRequest(
  val state: String,
)

@JsonClass(generateAdapter = true)
data class GitHubIssueCommentRequest(
  val body: String,
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflow(
  val id: Long = 0L,
  val name: String = "",
  val path: String = "",
  val state: String = "active",
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowsResponse(
  val workflows: List<GitHubWorkflow> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowDispatchRequest(
  val ref: String,
)


