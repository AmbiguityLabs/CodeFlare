package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApiService {
  @GET("user")
  suspend fun getCurrentUser(
    @Header("Authorization") authHeader: String,
  ): Response<GitHubUser>

  @GET("user/repos")
  suspend fun listUserRepos(
    @Header("Authorization") authHeader: String,
    @Query("sort") sort: String = "updated",
    @Query("per_page") perPage: Int = 60,
    @Query("affiliation") affiliation: String = "owner,collaborator,organization_member",
  ): Response<List<GitHubRepo>>

  @POST("user/repos")
  suspend fun createRepo(
    @Header("Authorization") authHeader: String,
    @Body body: GitHubCreateRepoRequest,
  ): Response<GitHubRepo>

  @GET("repos/{owner}/{repo}")
  suspend fun getRepo(
    @Header("Authorization") authHeader: String?,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
  ): Response<GitHubRepo>

  @GET("repos/{owner}/{repo}/branches")
  suspend fun listBranches(
    @Header("Authorization") authHeader: String?,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("per_page") perPage: Int = 50,
  ): Response<List<GitHubBranch>>

  @GET("repos/{owner}/{repo}/git/trees/{branch}")
  suspend fun getRecursiveTree(
    @Header("Authorization") authHeader: String?,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("branch") branch: String,
    @Query("recursive") recursive: Int = 1,
  ): Response<GitHubTreeResponse>

  @GET("repos/{owner}/{repo}/contents/{filePath}")
  suspend fun getFileContents(
    @Header("Authorization") authHeader: String?,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path(value = "filePath", encoded = true) filePath: String,
    @Query("ref") ref: String,
  ): Response<GitHubFileContent>

  @PUT("repos/{owner}/{repo}/contents/{filePath}")
  suspend fun putFileContents(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path(value = "filePath", encoded = true) filePath: String,
    @Body body: GitHubPutFileRequest,
  ): Response<Map<String, @JvmSuppressWildcards Any?>>

  @POST("repos/{owner}/{repo}/git/refs")
  suspend fun createBranchRef(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: GitHubCreateBranchRequest,
  ): Response<Map<String, @JvmSuppressWildcards Any?>>

  @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
  suspend fun getBranchRef(
    @Header("Authorization") authHeader: String?,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("branch") branch: String,
  ): Response<GitHubGitRefResponse>

  @GET("repos/{owner}/{repo}/git/commits/{commitSha}")
  suspend fun getGitCommit(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("commitSha") commitSha: String,
  ): Response<GitHubGitCommitResponse>

  @POST("repos/{owner}/{repo}/git/blobs")
  suspend fun createBlob(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: GitHubCreateBlobRequest,
  ): Response<GitHubBlobResponse>

  @POST("repos/{owner}/{repo}/git/trees")
  suspend fun createTree(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: GitHubCreateTreeRequest,
  ): Response<GitHubTreeResponse>

  @POST("repos/{owner}/{repo}/git/commits")
  suspend fun createGitCommit(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: GitHubCreateGitCommitRequest,
  ): Response<GitHubCommitRef>

  @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
  suspend fun updateBranchRef(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("branch") branch: String,
    @Body body: GitHubUpdateRefRequest,
  ): Response<GitHubGitRefResponse>

  @GET("repos/{owner}/{repo}/commits")
  suspend fun listCommits(
    @Header("Authorization") authHeader: String?,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("sha") branch: String,
    @Query("per_page") perPage: Int = 20,
  ): Response<List<GitHubCommitItem>>

  @GET("repos/{owner}/{repo}/actions/runs")
  suspend fun listWorkflowRuns(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("per_page") perPage: Int = 10,
  ): Response<GitHubWorkflowRunsResponse>

  @GET("repos/{owner}/{repo}/actions/workflows")
  suspend fun listWorkflows(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
  ): Response<GitHubWorkflowsResponse>

  @POST("repos/{owner}/{repo}/actions/workflows/{workflowId}/dispatches")
  suspend fun dispatchWorkflow(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("workflowId") workflowId: Long,
    @Body body: GitHubWorkflowDispatchRequest,
  ): Response<Unit>

  @POST("repos/{owner}/{repo}/actions/runs/{runId}/rerun")
  suspend fun rerunWorkflow(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("runId") runId: Long,
  ): Response<Unit>

  @GET("repos/{owner}/{repo}/commits/{commitSha}")
  suspend fun getCommitDetail(
    @Header("Authorization") authHeader: String?,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("commitSha") commitSha: String,
  ): Response<GitHubCommitDetailResponse>

  @GET("repos/{owner}/{repo}/pulls")
  suspend fun listPullRequests(
    @Header("Authorization") authHeader: String?,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("state") state: String = "open",
    @Query("per_page") perPage: Int = 25,
  ): Response<List<GitHubPullRequest>>

  @POST("repos/{owner}/{repo}/pulls")
  suspend fun createPullRequest(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: GitHubCreatePullRequest,
  ): Response<GitHubPullRequest>

  @PUT("repos/{owner}/{repo}/pulls/{pullNumber}/merge")
  suspend fun mergePullRequest(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("pullNumber") pullNumber: Int,
    @Body body: GitHubMergePrRequest,
  ): Response<Map<String, @JvmSuppressWildcards Any?>>

  @GET("repos/{owner}/{repo}/pulls/{pullNumber}/files")
  suspend fun listPullRequestFiles(
    @Header("Authorization") authHeader: String?,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("pullNumber") pullNumber: Int,
  ): Response<List<GitHubDiffFile>>

  @GET("repos/{owner}/{repo}/issues")
  suspend fun listIssues(
    @Header("Authorization") authHeader: String?,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("state") state: String = "open",
    @Query("per_page") perPage: Int = 30,
  ): Response<List<GitHubIssue>>

  @POST("repos/{owner}/{repo}/issues")
  suspend fun createIssue(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body body: GitHubCreateIssueRequest,
  ): Response<GitHubIssue>

  @PATCH("repos/{owner}/{repo}/issues/{issueNumber}")
  suspend fun updateIssue(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("issueNumber") issueNumber: Int,
    @Body body: GitHubUpdateIssueRequest,
  ): Response<GitHubIssue>

  @POST("repos/{owner}/{repo}/issues/{issueNumber}/comments")
  suspend fun commentOnIssue(
    @Header("Authorization") authHeader: String,
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("issueNumber") issueNumber: Int,
    @Body body: GitHubIssueCommentRequest,
  ): Response<Map<String, @JvmSuppressWildcards Any?>>
}

