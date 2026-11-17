package com.example.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface CloudflareApiService {
  @GET("user/tokens/verify")
  suspend fun verifyToken(): Response<CfResponse<CfTokenVerifyResult>>

  @GET("user")
  suspend fun getCurrentUser(): Response<CfResponse<CfUserResult>>

  @GET("accounts")
  suspend fun listAccounts(
    @Query("per_page") perPage: Int = 50,
  ): Response<CfResponse<List<CfAccount>>>

  // Cloudflare Workers Scripts & Settings
  @GET("accounts/{accountId}/workers/scripts")
  suspend fun listWorkers(
    @Path("accountId") accountId: String,
  ): Response<CfResponse<List<CfWorkerScript>>>

  @GET("accounts/{accountId}/workers/scripts/{scriptName}")
  suspend fun getWorkerContent(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
  ): Response<ResponseBody>

  @PUT("accounts/{accountId}/workers/scripts/{scriptName}")
  suspend fun uploadWorkerScript(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
    @Body body: RequestBody,
  ): Response<CfResponse<CfWorkerScript>>

  @DELETE("accounts/{accountId}/workers/scripts/{scriptName}")
  suspend fun deleteWorkerScript(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
  ): Response<CfResponse<Any?>>

  @GET("accounts/{accountId}/workers/scripts/{scriptName}/settings")
  suspend fun getWorkerSettings(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
  ): Response<CfResponse<CfWorkerSettings>>

  @PATCH("accounts/{accountId}/workers/scripts/{scriptName}/settings")
  suspend fun patchWorkerSettings(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
    @Body body: RequestBody,
  ): Response<CfResponse<CfWorkerSettings>>

  @PUT("accounts/{accountId}/workers/scripts/{scriptName}/secrets")
  suspend fun putWorkerSecret(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
    @Body secret: CfWorkerSecretPut,
  ): Response<CfResponse<CfWorkerBinding>>

  @DELETE("accounts/{accountId}/workers/scripts/{scriptName}/secrets/{secretName}")
  suspend fun deleteWorkerSecret(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
    @Path("secretName") secretName: String,
  ): Response<CfResponse<Any?>>

  @GET("accounts/{accountId}/workers/subdomain")
  suspend fun getWorkersSubdomain(
    @Path("accountId") accountId: String,
  ): Response<CfResponse<CfSubdomainResult>>

  @GET("accounts/{accountId}/workers/scripts/{scriptName}/subdomain")
  suspend fun getScriptSubdomain(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
  ): Response<CfResponse<CfScriptSubdomainStatus>>

  @POST("accounts/{accountId}/workers/scripts/{scriptName}/subdomain")
  suspend fun setScriptSubdomain(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
    @Body request: CfScriptSubdomainRequest,
  ): Response<CfResponse<CfScriptSubdomainStatus>>

  @GET("accounts/{accountId}/workers/scripts/{scriptName}/routes")
  suspend fun listWorkerRoutes(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
  ): Response<CfResponse<List<CfWorkerRoute>>>

  @GET("accounts/{accountId}/workers/domains/records")
  suspend fun listWorkerDomains(
    @Path("accountId") accountId: String,
    @Query("service") service: String? = null,
  ): Response<CfResponse<List<CfWorkerDomainRecord>>>

  @PUT("accounts/{accountId}/workers/domains/records")
  suspend fun attachWorkerDomain(
    @Path("accountId") accountId: String,
    @Body request: CfWorkerDomainPutRequest,
  ): Response<CfResponse<CfWorkerDomainRecord>>

  @DELETE("accounts/{accountId}/workers/domains/records/{domainId}")
  suspend fun deleteWorkerDomain(
    @Path("accountId") accountId: String,
    @Path("domainId") domainId: String,
  ): Response<CfResponse<Any?>>

  @GET("accounts/{accountId}/workers/scripts/{scriptName}/schedules")
  suspend fun getWorkerSchedules(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
  ): Response<CfResponse<CfSchedulesResult>>

  @PUT("accounts/{accountId}/workers/scripts/{scriptName}/schedules")
  suspend fun putWorkerSchedules(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
    @Body schedules: List<CfCronSchedulePut>,
  ): Response<CfResponse<CfSchedulesResult>>

  @GET("accounts/{accountId}/workers/scripts/{scriptName}/deployments")
  suspend fun listWorkerDeployments(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
  ): Response<CfResponse<CfDeploymentsResult>>

  @POST("accounts/{accountId}/workers/scripts/{scriptName}/deployments")
  suspend fun createWorkerDeployment(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
    @Body request: CfDeploymentCreateRequest,
  ): Response<CfResponse<CfDeployment>>

  @GET("accounts/{accountId}/workers/scripts/{scriptName}/versions")
  suspend fun listWorkerVersions(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
    @Query("per_page") perPage: Int = 20,
  ): Response<CfResponse<CfWorkerVersionsResult>>

  @POST("accounts/{accountId}/workers/scripts/{scriptName}/tails")
  suspend fun startWorkerTail(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
    @Body body: Map<String, @JvmSuppressWildcards Any> = emptyMap(),
  ): Response<CfResponse<CfTailResult>>

  @DELETE("accounts/{accountId}/workers/scripts/{scriptName}/tails/{tailId}")
  suspend fun stopWorkerTail(
    @Path("accountId") accountId: String,
    @Path("scriptName") scriptName: String,
    @Path("tailId") tailId: String,
  ): Response<CfResponse<Any?>>

  // Zones & Custom Routes
  @GET("zones")
  suspend fun listZones(
    @Query("account.id") accountId: String,
    @Query("per_page") perPage: Int = 50,
  ): Response<CfResponse<List<CfZone>>>

  @GET("zones/{zoneId}/workers/routes")
  suspend fun listZoneRoutes(
    @Path("zoneId") zoneId: String,
  ): Response<CfResponse<List<CfWorkerRoute>>>

  @POST("zones/{zoneId}/workers/routes")
  suspend fun createZoneRoute(
    @Path("zoneId") zoneId: String,
    @Body route: CfZoneRouteCreate,
  ): Response<CfResponse<CfWorkerRoute>>

  @DELETE("zones/{zoneId}/workers/routes/{routeId}")
  suspend fun deleteZoneRoute(
    @Path("zoneId") zoneId: String,
    @Path("routeId") routeId: String,
  ): Response<CfResponse<Any?>>

  // KV Storage Namespaces & Keys
  @GET("accounts/{accountId}/storage/kv/namespaces")
  suspend fun listKvNamespaces(
    @Path("accountId") accountId: String,
    @Query("per_page") perPage: Int = 50,
  ): Response<CfResponse<List<CfKvNamespace>>>

  @POST("accounts/{accountId}/storage/kv/namespaces")
  suspend fun createKvNamespace(
    @Path("accountId") accountId: String,
    @Body body: CfKvNamespaceRequest,
  ): Response<CfResponse<CfKvNamespace>>

  @PUT("accounts/{accountId}/storage/kv/namespaces/{namespaceId}")
  suspend fun renameKvNamespace(
    @Path("accountId") accountId: String,
    @Path("namespaceId") namespaceId: String,
    @Body body: CfKvNamespaceRequest,
  ): Response<CfResponse<Any?>>

  @DELETE("accounts/{accountId}/storage/kv/namespaces/{namespaceId}")
  suspend fun deleteKvNamespace(
    @Path("accountId") accountId: String,
    @Path("namespaceId") namespaceId: String,
  ): Response<CfResponse<Any?>>

  @GET("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/keys")
  suspend fun listKvKeys(
    @Path("accountId") accountId: String,
    @Path("namespaceId") namespaceId: String,
    @Query("limit") limit: Int = 100,
    @Query("prefix") prefix: String? = null,
  ): Response<CfResponse<List<CfKvKey>>>

  @GET("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{keyName}")
  suspend fun getKvValue(
    @Path("accountId") accountId: String,
    @Path("namespaceId") namespaceId: String,
    @Path("keyName") keyName: String,
  ): Response<ResponseBody>

  @PUT("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{keyName}")
  suspend fun putKvValue(
    @Path("accountId") accountId: String,
    @Path("namespaceId") namespaceId: String,
    @Path("keyName") keyName: String,
    @Body value: RequestBody,
    @Query("expiration_ttl") expirationTtl: Long? = null,
  ): Response<CfResponse<Any?>>

  @PUT("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/bulk")
  suspend fun putKvBulk(
    @Path("accountId") accountId: String,
    @Path("namespaceId") namespaceId: String,
    @Body items: List<CfKvBulkItem>,
  ): Response<CfResponse<Any?>>

  @DELETE("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{keyName}")
  suspend fun deleteKvValue(
    @Path("accountId") accountId: String,
    @Path("namespaceId") namespaceId: String,
    @Path("keyName") keyName: String,
  ): Response<CfResponse<Any?>>

  // D1 Databases
  @GET("accounts/{accountId}/d1/database")
  suspend fun listD1Databases(
    @Path("accountId") accountId: String,
    @Query("per_page") perPage: Int = 50,
  ): Response<CfResponse<List<CfD1Database>>>

  @POST("accounts/{accountId}/d1/database")
  suspend fun createD1Database(
    @Path("accountId") accountId: String,
    @Body body: CfD1CreateRequest,
  ): Response<CfResponse<CfD1Database>>

  @DELETE("accounts/{accountId}/d1/database/{databaseId}")
  suspend fun deleteD1Database(
    @Path("accountId") accountId: String,
    @Path("databaseId") databaseId: String,
  ): Response<CfResponse<Any?>>

  @POST("accounts/{accountId}/d1/database/{databaseId}/query")
  suspend fun queryD1Database(
    @Path("accountId") accountId: String,
    @Path("databaseId") databaseId: String,
    @Body body: CfD1QueryRequest,
  ): Response<CfResponse<List<CfD1QueryResult>>>

  // R2 Buckets
  @GET("accounts/{accountId}/r2/buckets")
  suspend fun listR2Buckets(
    @Path("accountId") accountId: String,
  ): Response<CfResponse<CfR2BucketsResult>>

  @POST("accounts/{accountId}/r2/buckets")
  suspend fun createR2Bucket(
    @Path("accountId") accountId: String,
    @Body body: CfR2CreateRequest,
  ): Response<CfResponse<CfR2Bucket>>

  @DELETE("accounts/{accountId}/r2/buckets/{bucketName}")
  suspend fun deleteR2Bucket(
    @Path("accountId") accountId: String,
    @Path("bucketName") bucketName: String,
  ): Response<CfResponse<Any?>>

  // Durable Objects & Queues
  @GET("accounts/{accountId}/workers/durable_objects/namespaces")
  suspend fun listDurableObjects(
    @Path("accountId") accountId: String,
  ): Response<CfResponse<List<CfDurableObjectNamespace>>>

  @GET("accounts/{accountId}/queues")
  suspend fun listQueues(
    @Path("accountId") accountId: String,
  ): Response<CfResponse<List<CfQueue>>>

  @POST("accounts/{accountId}/queues")
  suspend fun createQueue(
    @Path("accountId") accountId: String,
    @Body body: CfQueueCreateRequest,
  ): Response<CfResponse<CfQueue>>

  @DELETE("accounts/{accountId}/queues/{queueId}")
  suspend fun deleteQueue(
    @Path("accountId") accountId: String,
    @Path("queueId") queueId: String,
  ): Response<CfResponse<Any?>>

  // Cloudflare Pages Projects, Deployments, Logs & Domains
  @GET("accounts/{accountId}/pages/projects")
  suspend fun listPagesProjects(
    @Path("accountId") accountId: String,
  ): Response<CfResponse<List<CfPagesProject>>>

  @GET("accounts/{accountId}/pages/projects/{projectName}")
  suspend fun getPagesProject(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
  ): Response<CfResponse<CfPagesProject>>

  @POST("accounts/{accountId}/pages/projects")
  suspend fun createPagesProject(
    @Path("accountId") accountId: String,
    @Body body: CfPagesProjectCreateRequest,
  ): Response<CfResponse<CfPagesProject>>

  @PATCH("accounts/{accountId}/pages/projects/{projectName}")
  suspend fun patchPagesProject(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
    @Body body: RequestBody,
  ): Response<CfResponse<CfPagesProject>>

  @DELETE("accounts/{accountId}/pages/projects/{projectName}")
  suspend fun deletePagesProject(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
  ): Response<CfResponse<Any?>>

  @GET("accounts/{accountId}/pages/projects/{projectName}/deployments")
  suspend fun listPagesDeployments(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
  ): Response<CfResponse<List<CfPagesDeployment>>>

  @POST("accounts/{accountId}/pages/projects/{projectName}/deployments")
  suspend fun triggerPagesDeployment(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
    @Body body: Map<String, @JvmSuppressWildcards Any> = emptyMap(),
  ): Response<CfResponse<CfPagesDeployment>>

  @POST("accounts/{accountId}/pages/projects/{projectName}/deployments/{deploymentId}/retry")
  suspend fun retryPagesDeployment(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
    @Path("deploymentId") deploymentId: String,
  ): Response<CfResponse<CfPagesDeployment>>

  @POST("accounts/{accountId}/pages/projects/{projectName}/deployments/{deploymentId}/rollback")
  suspend fun rollbackPagesDeployment(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
    @Path("deploymentId") deploymentId: String,
  ): Response<CfResponse<CfPagesDeployment>>

  @GET("accounts/{accountId}/pages/projects/{projectName}/deployments/{deploymentId}/history/logs")
  suspend fun getPagesDeploymentLogs(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
    @Path("deploymentId") deploymentId: String,
  ): Response<CfResponse<CfPagesBuildLogsResult>>

  @GET("accounts/{accountId}/pages/projects/{projectName}/domains")
  suspend fun listPagesDomains(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
  ): Response<CfResponse<List<CfPagesDomain>>>

  @POST("accounts/{accountId}/pages/projects/{projectName}/domains")
  suspend fun addPagesDomain(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
    @Body body: CfPagesDomainCreateRequest,
  ): Response<CfResponse<CfPagesDomain>>

  @DELETE("accounts/{accountId}/pages/projects/{projectName}/domains/{domainName}")
  suspend fun deletePagesDomain(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
    @Path("domainName") domainName: String,
  ): Response<CfResponse<Any?>>

  // Cloudflare Pages Direct Upload
  @GET("accounts/{accountId}/pages/projects/{projectName}/upload-token")
  suspend fun getPagesUploadToken(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
  ): Response<CfResponse<CfPagesUploadTokenResult>>

  @POST("pages/assets/check-missing")
  suspend fun checkMissingPagesAssets(
    @Header("Authorization") bearerJwt: String,
    @Body body: CfPagesCheckMissingRequest,
  ): Response<CfResponse<List<String>>>

  @POST("pages/assets/upload")
  suspend fun uploadPagesAssets(
    @Header("Authorization") bearerJwt: String,
    @Body body: List<CfPagesUploadPayloadItem>,
  ): Response<CfResponse<Any?>>

  @POST("pages/assets/upsert-hashes")
  suspend fun upsertPagesHashes(
    @Header("Authorization") bearerJwt: String,
    @Body body: CfPagesUpsertHashesRequest,
  ): Response<CfResponse<Any?>>

  @POST("accounts/{accountId}/pages/projects/{projectName}/deployments")
  suspend fun createPagesDirectDeployment(
    @Path("accountId") accountId: String,
    @Path("projectName") projectName: String,
    @Body body: RequestBody,
  ): Response<CfResponse<CfPagesDeployment>>
}
