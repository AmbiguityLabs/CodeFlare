package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CfError(
  val code: Int = 0,
  val message: String = "",
)

@JsonClass(generateAdapter = true)
data class CfResponse<T>(
  val success: Boolean = false,
  val errors: List<CfError> = emptyList(),
  val messages: List<CfError> = emptyList(),
  val result: T? = null,
)

@JsonClass(generateAdapter = true)
data class CfTokenVerifyResult(
  val id: String = "",
  val status: String = "",
)

@JsonClass(generateAdapter = true)
data class CfUserResult(
  val id: String = "",
  val email: String = "",
  val username: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfAccount(
  val id: String = "",
  val name: String = "",
  val type: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfWorkerScript(
  val id: String = "",
  val etag: String? = null,
  @Json(name = "created_on") val createdOn: String? = null,
  @Json(name = "modified_on") val modifiedOn: String? = null,
  @Json(name = "usage_model") val usageModel: String? = null,
  @Json(name = "compatibility_date") val compatibilityDate: String? = null,
  @Json(name = "compatibility_flags") val compatibilityFlags: List<String>? = null,
  val handlers: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class CfSubdomainResult(
  val subdomain: String = "",
)

@JsonClass(generateAdapter = true)
data class CfScriptSubdomainStatus(
  val enabled: Boolean = false,
  @Json(name = "previews_enabled") val previewsEnabled: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class CfScriptSubdomainRequest(
  val enabled: Boolean = true,
  @Json(name = "previews_enabled") val previewsEnabled: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class CfWorkerBinding(
  val name: String = "",
  val type: String = "plain_text",
  val text: String? = null,
  @Json(name = "namespace_id") val namespaceId: String? = null,
  @Json(name = "bucket_name") val bucketName: String? = null,
  @Json(name = "database_id") val databaseId: String? = null,
  val service: String? = null,
  val environment: String? = null,
  @Json(name = "class_name") val className: String? = null,
  @Json(name = "script_name") val scriptName: String? = null,
  @Json(name = "queue_name") val queueName: String? = null,
  @Json(name = "index_name") val indexName: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfWorkerSettings(
  @Json(name = "compatibility_date") val compatibilityDate: String? = null,
  @Json(name = "compatibility_flags") val compatibilityFlags: List<String>? = null,
  val bindings: List<CfWorkerBinding>? = null,
  @Json(name = "usage_model") val usageModel: String? = null,
  @Json(name = "logpush") val logpush: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class CfWorkerSecretPut(
  val name: String,
  val text: String,
  val type: String = "secret_text",
)

@JsonClass(generateAdapter = true)
data class CfWorkerRoute(
  val id: String = "",
  val pattern: String = "",
  val script: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfZone(
  val id: String = "",
  val name: String = "",
  val status: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfZoneRouteCreate(
  val pattern: String,
  val script: String,
)

@JsonClass(generateAdapter = true)
data class CfWorkerDomainRecord(
  val id: String = "",
  val hostname: String = "",
  val service: String = "",
  val environment: String = "production",
  @Json(name = "zone_id") val zoneId: String = "",
  @Json(name = "zone_name") val zoneName: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfWorkerDomainPutRequest(
  val hostname: String,
  val service: String,
  @Json(name = "zone_id") val zoneId: String,
  val environment: String = "production",
)

@JsonClass(generateAdapter = true)
data class CfCronSchedule(
  val cron: String = "",
  @Json(name = "created_on") val createdOn: String? = null,
  @Json(name = "modified_on") val modifiedOn: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfCronSchedulePut(
  val cron: String,
)

@JsonClass(generateAdapter = true)
data class CfSchedulesResult(
  val schedules: List<CfCronSchedule> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CfDeploymentVersion(
  @Json(name = "version_id") val versionId: String = "",
  val percentage: Double = 100.0,
)

@JsonClass(generateAdapter = true)
data class CfDeployment(
  val id: String = "",
  @Json(name = "created_on") val createdOn: String? = null,
  val source: String? = null,
  val strategy: String? = null,
  @Json(name = "author_email") val authorEmail: String? = null,
  val versions: List<CfDeploymentVersion> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CfDeploymentsResult(
  val deployments: List<CfDeployment> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CfDeploymentCreateRequest(
  val strategy: String = "percentage",
  val versions: List<CfDeploymentVersion>,
  val annotations: Map<String, String>? = null,
)

@JsonClass(generateAdapter = true)
data class CfTailResult(
  val id: String = "",
  val url: String = "",
  @Json(name = "expires_at") val expiresAt: String? = null,
)

// KV Storage Models
@JsonClass(generateAdapter = true)
data class CfKvNamespace(
  val id: String = "",
  val title: String = "",
)

@JsonClass(generateAdapter = true)
data class CfKvNamespaceRequest(
  val title: String,
)

@JsonClass(generateAdapter = true)
data class CfKvKey(
  val name: String = "",
  val expiration: Long? = null,
)

// D1 Database Models
@JsonClass(generateAdapter = true)
data class CfD1Database(
  val uuid: String = "",
  val name: String = "",
  val version: String? = null,
  @Json(name = "num_tables") val numTables: Int? = null,
  @Json(name = "file_size") val fileSize: Long? = null,
  @Json(name = "created_at") val createdAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfD1CreateRequest(
  val name: String,
)

@JsonClass(generateAdapter = true)
data class CfD1QueryRequest(
  val sql: String,
)

@JsonClass(generateAdapter = true)
data class CfD1QueryMeta(
  val duration: Double? = null,
  val changes: Int? = null,
  @Json(name = "rows_read") val rowsRead: Int? = null,
  @Json(name = "rows_written") val rowsWritten: Int? = null,
)

@JsonClass(generateAdapter = true)
data class CfD1QueryResult(
  val success: Boolean = false,
  val results: List<Map<String, @JvmSuppressWildcards Any?>> = emptyList(),
  val meta: CfD1QueryMeta? = null,
)

// R2 Bucket Models
@JsonClass(generateAdapter = true)
data class CfR2Bucket(
  val name: String = "",
  @Json(name = "creation_date") val creationDate: String? = null,
  val location: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfR2BucketsResult(
  val buckets: List<CfR2Bucket> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CfR2CreateRequest(
  val name: String,
)

// Durable Objects & Queues
@JsonClass(generateAdapter = true)
data class CfDurableObjectNamespace(
  val id: String = "",
  val name: String = "",
  val script: String? = null,
  @Json(name = "class") val className: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfQueue(
  @Json(name = "queue_id") val queueId: String = "",
  @Json(name = "queue_name") val queueName: String = "",
  @Json(name = "created_on") val createdOn: String? = null,
)

// Cloudflare Pages Models
@JsonClass(generateAdapter = true)
data class CfPagesStage(
  val name: String = "",
  val status: String = "",
  @Json(name = "started_on") val startedOn: String? = null,
  @Json(name = "ended_on") val endedOn: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesDeploymentTriggerMeta(
  val branch: String? = null,
  @Json(name = "commit_hash") val commitHash: String? = null,
  @Json(name = "commit_message") val commitMessage: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesDeploymentTrigger(
  val type: String? = null,
  val metadata: CfPagesDeploymentTriggerMeta? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesDeployment(
  val id: String = "",
  @Json(name = "short_id") val shortId: String? = null,
  val url: String? = null,
  val environment: String = "production",
  @Json(name = "created_on") val createdOn: String? = null,
  @Json(name = "latest_stage") val latestStage: CfPagesStage? = null,
  @Json(name = "deployment_trigger") val deploymentTrigger: CfPagesDeploymentTrigger? = null,
  val aliases: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesSourceConfig(
  val owner: String? = null,
  @Json(name = "repo_name") val repoName: String? = null,
  @Json(name = "production_branch") val productionBranch: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesSource(
  val type: String? = null,
  val config: CfPagesSourceConfig? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesEnvVarValue(
  val type: String = "plain_text",
  val value: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesEnvConfig(
  @Json(name = "env_vars") val envVars: Map<String, CfPagesEnvVarValue>? = null,
  @Json(name = "compatibility_date") val compatibilityDate: String? = null,
  @Json(name = "compatibility_flags") val compatibilityFlags: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesDeploymentConfigs(
  val production: CfPagesEnvConfig? = null,
  val preview: CfPagesEnvConfig? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesBuildConfig(
  @Json(name = "build_command") val buildCommand: String? = null,
  @Json(name = "destination_dir") val destinationDir: String? = null,
  @Json(name = "root_dir") val rootDir: String? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesProject(
  val id: String = "",
  val name: String = "",
  val subdomain: String? = null,
  val domains: List<String> = emptyList(),
  @Json(name = "production_branch") val productionBranch: String = "main",
  @Json(name = "created_on") val createdOn: String? = null,
  val source: CfPagesSource? = null,
  @Json(name = "build_config") val buildConfig: CfPagesBuildConfig? = null,
  @Json(name = "deployment_configs") val deploymentConfigs: CfPagesDeploymentConfigs? = null,
  @Json(name = "latest_deployment") val latestDeployment: CfPagesDeployment? = null,
  @Json(name = "canonical_deployment") val canonicalDeployment: CfPagesDeployment? = null,
)

@JsonClass(generateAdapter = true)
data class CfPagesProjectCreateRequest(
  val name: String,
  @Json(name = "production_branch") val productionBranch: String = "main",
)

@JsonClass(generateAdapter = true)
data class CfPagesDomain(
  val id: String = "",
  val name: String = "",
  val status: String = "",
)

@JsonClass(generateAdapter = true)
data class CfPagesDomainCreateRequest(
  val name: String,
)

@JsonClass(generateAdapter = true)
data class CfPagesLogEntry(
  val ts: String? = null,
  val line: String = "",
)

@JsonClass(generateAdapter = true)
data class CfPagesBuildLogsResult(
  val total: Int = 0,
  val data: List<CfPagesLogEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CfOAuthTokenResponse(
  @Json(name = "access_token") val accessToken: String = "",
  @Json(name = "refresh_token") val refreshToken: String? = null,
  @Json(name = "expires_in") val expiresIn: Long = 3600L,
  @Json(name = "token_type") val tokenType: String = "Bearer",
  val scope: String? = null,
)

// Domain models used across the IDE workspace
enum class WorkerLanguage {
  JAVASCRIPT,
  TYPESCRIPT,
  JSON,
  TEXT,
}

data class WorkerFile(
  val path: String,
  val content: String,
  val originalContent: String,
  val contentType: String,
  val isEntryPoint: Boolean,
  val isVirtualReadOnly: Boolean = false,
) {
  val isDirty: Boolean
    get() = !isVirtualReadOnly && content != originalContent

  val language: WorkerLanguage
    get() =
      when {
        path.endsWith(".ts", ignoreCase = true) || path.endsWith(".mts", ignoreCase = true) ->
          WorkerLanguage.TYPESCRIPT
        path.endsWith(".json", ignoreCase = true) || path.endsWith(".jsonc", ignoreCase = true) ->
          WorkerLanguage.JSON
        path.endsWith(".js", ignoreCase = true) || path.endsWith(".mjs", ignoreCase = true) ->
          WorkerLanguage.JAVASCRIPT
        else -> WorkerLanguage.TEXT
      }

  val fileName: String
    get() = path.substringAfterLast('/')
}

data class WorkerWorkspace(
  val scriptName: String,
  val isEsModuleFormat: Boolean,
  val entryModulePath: String,
  val compatibilityDate: String,
  val compatibilityFlags: List<String> = emptyList(),
  val files: List<WorkerFile>,
  val directories: Set<String> = emptySet(),
  val bindings: List<CfWorkerBinding>,
  val routes: List<CfWorkerRoute>,
  val schedules: List<CfCronSchedule> = emptyList(),
  val deployments: List<CfDeployment> = emptyList(),
  val domains: List<CfWorkerDomainRecord> = emptyList(),
  val workersDevEnabled: Boolean,
  val accountSubdomain: String,
  val hasModuleSetChanged: Boolean = false,
) {
  val workersDevUrl: String?
    get() =
      if (accountSubdomain.isNotBlank()) {
        "https://$scriptName.$accountSubdomain.workers.dev"
      } else null
}

enum class DiagnosticSeverity {
  ERROR,
  WARNING,
  INFO,
}

data class DiagnosticItem(
  val filePath: String,
  val line: Int,
  val column: Int,
  val message: String,
  val severity: DiagnosticSeverity,
)

enum class DiffType {
  UNCHANGED,
  ADDED,
  REMOVED,
}

data class DiffLine(
  val type: DiffType,
  val lineNumberOld: Int?,
  val lineNumberNew: Int?,
  val text: String,
)

data class HttpTestResult(
  val url: String,
  val method: String,
  val statusCode: Int,
  val statusText: String,
  val durationMs: Long,
  val headers: Map<String, String>,
  val body: String,
  val errorMessage: String? = null,
)
