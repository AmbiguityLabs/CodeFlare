package com.example.data.repository

import com.example.data.local.RawPickedAsset
import com.example.data.remote.CfAccount
import com.example.data.remote.CfCronSchedule
import com.example.data.remote.CfCronSchedulePut
import com.example.data.remote.CfD1CreateRequest
import com.example.data.remote.CfD1Database
import com.example.data.remote.CfD1QueryRequest
import com.example.data.remote.CfD1QueryResult
import com.example.data.remote.CfDeployment
import com.example.data.remote.CfDeploymentCreateRequest
import com.example.data.remote.CfDeploymentVersion
import com.example.data.remote.CfDurableObjectNamespace
import com.example.data.remote.CfKvBulkItem
import com.example.data.remote.CfKvKey
import com.example.data.remote.CfKvNamespace
import com.example.data.remote.CfKvNamespaceRequest
import com.example.data.remote.CfOAuthTokenResponse
import com.example.data.remote.CfPagesAssetMetadata
import com.example.data.remote.CfPagesBuildLogsResult
import com.example.data.remote.CfPagesCheckMissingRequest
import com.example.data.remote.CfPagesDeployment
import com.example.data.remote.CfPagesDomain
import com.example.data.remote.CfPagesDomainCreateRequest
import com.example.data.remote.CfPagesEnvVarValue
import com.example.data.remote.CfPagesProject
import com.example.data.remote.CfPagesProjectCreateRequest
import com.example.data.remote.CfPagesUploadPayloadItem
import com.example.data.remote.CfPagesUpsertHashesRequest
import com.example.data.remote.CfQueue
import com.example.data.remote.CfQueueCreateRequest
import com.example.data.remote.CfR2Bucket
import com.example.data.remote.CfR2CreateRequest
import com.example.data.remote.CfResponse
import com.example.data.remote.CfScriptSubdomainRequest
import com.example.data.remote.CfSubdomainPutRequest
import com.example.data.remote.CfTailResult
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.CfWorkerDomainPutRequest
import com.example.data.remote.CfWorkerDomainRecord
import com.example.data.remote.CfWorkerRoute
import com.example.data.remote.CfWorkerScript
import com.example.data.remote.CfWorkerSecretPut
import com.example.data.remote.CfWorkerSettings
import com.example.data.remote.CfWorkerVersion
import com.example.data.remote.CfZone
import com.example.data.remote.CfZoneRouteCreate
import com.example.data.remote.CloudflareApiService
import com.example.data.remote.CloudflareClientFactory
import com.example.data.remote.CloudflareNamingUtils
import com.example.data.remote.D1AppliedMigrationRecord
import com.example.data.remote.D1SchemaGraph
import com.example.data.remote.D1TableInfo
import com.example.data.remote.D1WorkspaceMigration
import com.example.data.remote.HttpTestResult
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.PagesDirectUploadHasher
import com.example.data.remote.PagesDirectUploadProgress
import com.example.data.remote.PagesPreparedAsset
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerRenameOutcome
import com.example.data.remote.WorkerWorkspace
import com.example.data.security.AuthMethod
import com.example.data.security.AuthPreferencesStore
import com.example.data.security.StoredCredentials
import com.example.editor.D1RelationalEngine
import com.squareup.moshi.Types
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response as OkHttpResponse
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import retrofit2.Response

enum class WorkerTemplate(val id: String, val title: String, val subtitle: String, val code: String) {
  HELLO_FETCH(
    id = "hello_fetch",
    title = "ES Module Fetch Worker",
    subtitle = "Minimal edge HTTP handler with request metadata",
    code =
      """export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    return new Response(
      JSON.stringify({
        ok: true,
        worker: "edgecode-worker",
        path: url.pathname,
        method: request.method,
        colo: request.cf?.colo ?? "EDGE",
        timestamp: new Date().toISOString()
      }, null, 2),
      {
        headers: {
          "content-type": "application/json;charset=UTF-8",
          "x-powered-by": "Cloudflare-Workers"
        }
      }
    );
  }
};
""",
  ),
  JSON_ROUTER(
    id = "json_router",
    title = "REST API Router",
    subtitle = "Method + path routing with JSON errors and CORS headers",
    code =
      """const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization"
};

export default {
  async fetch(request, env, ctx) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    if (url.pathname === "/health") {
      return json({ status: "healthy", edge: request.cf?.colo ?? "local" });
    }

    if (url.pathname === "/echo" && request.method === "POST") {
      const payload = await request.json().catch(() => ({}));
      return json({ received: payload, at: Date.now() }, 201);
    }

    return json({ error: "Route not found", path: url.pathname }, 404);
  }
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: { "content-type": "application/json;charset=UTF-8", ...CORS_HEADERS }
  });
}
""",
  ),
  CORS_PROXY(
    id = "cors_proxy",
    title = "Edge Proxy & Cache",
    subtitle = "Upstreams fetch with edge caching and header injection",
    code =
      """export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const target = url.searchParams.get("url");

    if (!target) {
      return new Response("Usage: ?url=https://httpbin.org/json", { status: 400 });
    }

    const upstream = await fetch(target, {
      cf: { cacheTtl: 60, cacheEverything: true }
    });

    const headers = new Headers(upstream.headers);
    headers.set("Access-Control-Allow-Origin", "*");
    headers.set("X-Edge-Proxy", "EdgeCode");

    return new Response(upstream.body, {
      status: upstream.status,
      headers
    });
  }
};
""",
  ),
  CRON_WORKER(
    id = "cron_worker",
    title = "Scheduled Cron + Fetch",
    subtitle = "Handles both scheduled() triggers and HTTP inspection",
    code =
      """export default {
  async fetch(request, env, ctx) {
    return new Response(
      JSON.stringify({ service: "cron-worker", ready: true }, null, 2),
      { headers: { "content-type": "application/json" } }
    );
  },

  async scheduled(controller, env, ctx) {
    console.log("Cron triggered at:", new Date(controller.scheduledTime).toISOString());
    // ctx.waitUntil(doAsyncWork(env));
  }
};
""",
  ),
  HONO_ROUTER(
    id = "hono_router",
    title = "Edge Micro-Router (Hono-style)",
    subtitle = "Lightweight route matcher with params, middleware & JSON helpers",
    code =
      """const routes = [
  { method: "GET", pattern: /^\/api\/health$/, handler: (req, env) => Response.json({ ok: true, env: env.ENVIRONMENT ?? "prod" }) },
  { method: "GET", pattern: /^\/api\/items\/([^/]+)$/, handler: async (req, env, m) => {
      const id = m[1];
      const cached = env.MY_KV ? await env.MY_KV.get(`item:${'$'}{id}`) : null;
      return Response.json({ id, cached });
    }
  }
];

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    for (const r of routes) {
      if (r.method === request.method) {
        const match = url.pathname.match(r.pattern);
        if (match) return r.handler(request, env, match, ctx);
      }
    }
    return Response.json({ error: "Not Found", path: url.pathname }, { status: 404 });
  }
};
""",
  ),
  BEARER_AUTH_WORKER(
    id = "bearer_auth_worker",
    title = "Bearer Auth & KV Guard",
    subtitle = "Validates Authorization header against env.API_SECRET & KV quota",
    code =
      """export default {
  async fetch(request, env, ctx) {
    const auth = request.headers.get("Authorization") || "";
    const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
    const expected = env.API_SECRET || "dev-secret";

    if (!token || token !== expected) {
      return Response.json(
        { error: "Unauthorized", hint: "Provide Authorization: Bearer <API_SECRET>" },
        { status: 401 }
      );
    }

    const url = new URL(request.url);
    return Response.json({
      authenticated: true,
      path: url.pathname,
      colo: request.cf?.colo ?? "EDGE"
    });
  }
};
""",
  ),
  PAGES_FUNCTIONS_API(
    id = "pages_functions_api",
    title = "Pages Function Handler",
    subtitle = "Full-stack Cloudflare Pages onRequest(context) JSON endpoint",
    code =
      """export async function onRequest(context) {
  const { request, env, params, waitUntil } = context;
  const url = new URL(request.url);

  if (request.method === "POST") {
    const body = await request.json().catch(() => ({}));
    return Response.json({ received: body, path: url.pathname }, { status: 201 });
  }

  return Response.json({
    service: "cloudflare-pages-function",
    method: request.method,
    path: url.pathname,
    hasEnvKeys: Object.keys(env || {})
  });
}
""",
  ),
}

data class ActiveTailSession(
  val tailId: String,
  val webSocket: WebSocket,
)

class CloudflareRepository(
  private val prefsStore: AuthPreferencesStore,
  private val clientFactory: CloudflareClientFactory,
  apiServiceOverride: CloudflareApiService? = null,
) {
  private val api = apiServiceOverride ?: clientFactory.apiService

  suspend fun authorizeWithApiToken(token: String): Result<List<CfAccount>> =
    withContext(Dispatchers.IO) {
      val trimmed = token.trim()
      if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Token cannot be empty"))

      val tempCreds = StoredCredentials(method = AuthMethod.API_TOKEN, accessToken = trimmed)
      prefsStore.saveCredentials(tempCreds)

      val verifyResp = runCatching { api.verifyToken() }.getOrNull()
      if (verifyResp != null && !verifyResp.isSuccessful) {
        val accAttempt = api.listAccounts()
        if (!accAttempt.isSuccessful || accAttempt.body()?.success != true) {
          prefsStore.clearCredentials()
          return@withContext Result.failure(
            IllegalStateException(extractCfError(verifyResp) ?: "Invalid Cloudflare API Token")
          )
        }
        val accounts = accAttempt.body()?.result.orEmpty()
        if (accounts.isEmpty()) {
          prefsStore.clearCredentials()
          return@withContext Result.failure(IllegalStateException("No Cloudflare accounts accessible with this token."))
        }
        prefsStore.saveSelectedAccount(accounts.first().id, accounts.first().name)
        return@withContext Result.success(accounts)
      }

      val accountsResp =
        runCatching { api.listAccounts() }
          .getOrElse {
            prefsStore.clearCredentials()
            return@withContext Result.failure(it)
          }

      if (!accountsResp.isSuccessful || accountsResp.body()?.success != true) {
        prefsStore.clearCredentials()
        return@withContext Result.failure(
          IllegalStateException(extractCfError(accountsResp) ?: "Failed to fetch Cloudflare accounts")
        )
      }

      val accounts = accountsResp.body()?.result.orEmpty()
      if (accounts.isEmpty()) {
        prefsStore.clearCredentials()
        return@withContext Result.failure(IllegalStateException("No Cloudflare accounts found for this token."))
      }

      val userEmail = runCatching { api.getCurrentUser().body()?.result?.email.orEmpty() }.getOrDefault("")
      prefsStore.saveCredentials(
        tempCreds.copy(
          email = userEmail,
          selectedAccountId = accounts.first().id,
          selectedAccountName = accounts.first().name,
        )
      )
      Result.success(accounts)
    }

  suspend fun authorizeWithGlobalKey(email: String, apiKey: String): Result<List<CfAccount>> =
    withContext(Dispatchers.IO) {
      val cleanEmail = email.trim()
      val cleanKey = apiKey.trim()
      if (cleanEmail.isBlank() || cleanKey.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("Email and Global API Key are required"))
      }

      val creds = StoredCredentials(method = AuthMethod.GLOBAL_KEY, accessToken = cleanKey, email = cleanEmail)
      prefsStore.saveCredentials(creds)

      val accountsResp =
        runCatching { api.listAccounts() }
          .getOrElse {
            prefsStore.clearCredentials()
            return@withContext Result.failure(it)
          }

      if (!accountsResp.isSuccessful || accountsResp.body()?.success != true) {
        prefsStore.clearCredentials()
        return@withContext Result.failure(
          IllegalStateException(extractCfError(accountsResp) ?: "Cloudflare rejected Global API Key credentials")
        )
      }

      val accounts = accountsResp.body()?.result.orEmpty()
      if (accounts.isEmpty()) {
        prefsStore.clearCredentials()
        return@withContext Result.failure(IllegalStateException("No Cloudflare accounts found."))
      }
      prefsStore.saveSelectedAccount(accounts.first().id, accounts.first().name)
      Result.success(accounts)
    }

  suspend fun verifyActiveSession(): Result<List<CfAccount>> =
    withContext(Dispatchers.IO) {
      val creds =
        prefsStore.getCurrentCredentials()
          ?: return@withContext Result.failure(IllegalStateException("No Cloudflare credentials configured"))
      if (creds.method == AuthMethod.API_TOKEN) {
        val verifyResp = runCatching { api.verifyToken() }.getOrNull()
        if (verifyResp != null && !verifyResp.isSuccessful) {
          val msg = extractCfError(verifyResp)
          if (msg != null) {
            // Fall through to listAccounts for account-scoped tokens that cannot read /user/tokens/verify
          }
        }
      }
      val accountsResp =
        runCatching { api.listAccounts() }
          .getOrElse { return@withContext Result.failure(it) }
      if (!accountsResp.isSuccessful || accountsResp.body()?.success != true) {
        return@withContext Result.failure(
          IllegalStateException(extractCfError(accountsResp) ?: "Cloudflare rejected current credentials")
        )
      }
      val accounts = accountsResp.body()?.result.orEmpty()
      if (accounts.isEmpty()) {
        return@withContext Result.failure(IllegalStateException("Credentials valid, but zero Cloudflare accounts accessible"))
      }
      Result.success(accounts)
    }

  suspend fun updateApiTokenCredentials(token: String): Result<List<CfAccount>> =
    withContext(Dispatchers.IO) {
      val previous = prefsStore.getCurrentCredentials()
      val result = authorizeWithApiToken(token)
      if (result.isFailure && previous != null) {
        prefsStore.saveCredentials(previous)
      }
      result
    }

  suspend fun updateGlobalKeyCredentials(email: String, apiKey: String): Result<List<CfAccount>> =
    withContext(Dispatchers.IO) {
      val previous = prefsStore.getCurrentCredentials()
      val result = authorizeWithGlobalKey(email, apiKey)
      if (result.isFailure && previous != null) {
        prefsStore.saveCredentials(previous)
      }
      result
    }

  suspend fun completeOAuthSession(
    session: com.example.data.auth.CloudflareOAuthManager.PkceSession,
    preboundSocket: java.net.ServerSocket? = null,
  ): Result<List<CfAccount>> =
    withContext(Dispatchers.IO) {
      val tokenResult =
        com.example.data.auth.CloudflareOAuthManager.awaitCallbackAndExchange(session, preboundSocket)
      val tokenResp = tokenResult.getOrElse { return@withContext Result.failure(it) }
      finalizeOAuthToken(tokenResp)
    }

  suspend fun completeOAuthWithRedirect(
    session: com.example.data.auth.CloudflareOAuthManager.PkceSession,
    rawRedirectOrCode: String,
  ): Result<List<CfAccount>> =
    withContext(Dispatchers.IO) {
      val tokenResult =
        com.example.data.auth.CloudflareOAuthManager.completeFromManualRedirect(session, rawRedirectOrCode)
      val tokenResp = tokenResult.getOrElse { return@withContext Result.failure(it) }
      finalizeOAuthToken(tokenResp)
    }

  private suspend fun finalizeOAuthToken(tokenResp: CfOAuthTokenResponse): Result<List<CfAccount>> {
    val creds =
      StoredCredentials(
        method = AuthMethod.OAUTH_PKCE,
        accessToken = tokenResp.accessToken,
        refreshToken = tokenResp.refreshToken.orEmpty(),
        expiresAtEpochMs = System.currentTimeMillis() + (tokenResp.expiresIn * 1000L),
      )
    prefsStore.saveCredentials(creds)

    val accountsResp =
      runCatching { api.listAccounts() }
        .getOrElse {
          prefsStore.clearCredentials()
          return Result.failure(it)
        }

    if (!accountsResp.isSuccessful || accountsResp.body()?.success != true) {
      prefsStore.clearCredentials()
      return Result.failure(
        IllegalStateException(extractCfError(accountsResp) ?: "Failed to load Cloudflare accounts after OAuth"),
      )
    }

    val accounts = accountsResp.body()?.result.orEmpty()
    val userEmail = runCatching { api.getCurrentUser().body()?.result?.email.orEmpty() }.getOrDefault("")
    val firstAcc = accounts.firstOrNull()
    prefsStore.saveCredentials(
      creds.copy(
        email = userEmail,
        selectedAccountId = firstAcc?.id.orEmpty(),
        selectedAccountName = firstAcc?.name.orEmpty(),
      ),
    )
    return Result.success(accounts)
  }

  suspend fun loadAccounts(): Result<List<CfAccount>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listAccounts()
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Unable to load Cloudflare accounts")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun loadWorkers(accountId: String): Result<List<CfWorkerScript>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listWorkers(accountId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to list Workers scripts")
        }
        resp.body()?.result.orEmpty().sortedByDescending { it.modifiedOn ?: "" }
      }
    }

  suspend fun getAccountSubdomain(accountId: String): String =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.getWorkersSubdomain(accountId)
        if (resp.isSuccessful && resp.body()?.success == true) {
          resp.body()?.result?.subdomain.orEmpty()
        } else ""
      }.getOrDefault("")
    }

  suspend fun updateAccountSubdomain(
    accountId: String,
    newSubdomain: String,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val clean = CloudflareNamingUtils.sanitizeSubdomain(newSubdomain)
        require(CloudflareNamingUtils.isValidSubdomain(clean)) {
          "Subdomain must use lowercase letters, numbers, and hyphens (1-63 chars)"
        }
        val resp = api.setWorkersSubdomain(accountId, CfSubdomainPutRequest(clean))
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to update workers.dev subdomain to '$clean'")
        }
        resp.body()?.result?.subdomain?.takeIf { it.isNotBlank() } ?: clean
      }
    }

  suspend fun openWorkerWorkspace(accountId: String, script: CfWorkerScript): Result<WorkerWorkspace> =
    withContext(Dispatchers.IO) {
      runCatching {
        val contentResp = api.getWorkerContent(accountId, script.id)
        if (!contentResp.isSuccessful) {
          error("Failed to download script '${script.id}' (HTTP ${contentResp.code()})")
        }
        val rawBytes = contentResp.body()?.bytes() ?: ByteArray(0)
        val contentType = contentResp.headers()["Content-Type"]
        val parsed = MultipartWorkerParser.parseResponse(rawBytes, contentType, script.id)

        val settingsResp = runCatching { api.getWorkerSettings(accountId, script.id) }.getOrNull()
        val settings = settingsResp?.body()?.result
        val bindings = settings?.bindings.orEmpty()
        val compatDate = settings?.compatibilityDate ?: script.compatibilityDate ?: "2024-11-01"
        val compatFlags = settings?.compatibilityFlags ?: script.compatibilityFlags.orEmpty()

        val subdomain = getAccountSubdomain(accountId)
        val scriptSubdomain =
          runCatching { api.getScriptSubdomain(accountId, script.id).body()?.result?.enabled ?: false }
            .getOrDefault(false)
        val routes =
          runCatching { api.listWorkerRoutes(accountId, script.id).body()?.result.orEmpty() }
            .getOrDefault(emptyList())
        val schedules =
          runCatching { api.getWorkerSchedules(accountId, script.id).body()?.result?.schedules.orEmpty() }
            .getOrDefault(emptyList())
        val deployments =
          runCatching { api.listWorkerDeployments(accountId, script.id).body()?.result?.deployments.orEmpty() }
            .getOrDefault(emptyList())
        val domains =
          runCatching { api.listWorkerDomains(accountId, script.id).body()?.result.orEmpty() }
            .getOrDefault(emptyList())

        val wranglerVirtual =
          buildWranglerPreviewJson(
            scriptName = script.id,
            entryModule = parsed.entryModulePath,
            compatDate = compatDate,
            compatFlags = compatFlags,
            bindings = bindings,
            schedules = schedules,
            workersDev = scriptSubdomain,
          )
        val allFiles =
          parsed.files +
            WorkerFile(
              path = "wrangler.jsonc",
              content = wranglerVirtual,
              originalContent = wranglerVirtual,
              contentType = "application/json",
              isEntryPoint = false,
              isVirtualReadOnly = false,
            )

        WorkerWorkspace(
          scriptName = script.id,
          isEsModuleFormat = parsed.isEsModule,
          entryModulePath = parsed.entryModulePath,
          compatibilityDate = compatDate,
          compatibilityFlags = compatFlags,
          files = allFiles,
          bindings = bindings,
          routes = routes,
          schedules = schedules,
          deployments = deployments,
          domains = domains,
          workersDevEnabled = scriptSubdomain,
          accountSubdomain = subdomain,
        )
      }
    }

  suspend fun deployWorkerWorkspace(
    accountId: String,
    workspace: WorkerWorkspace,
  ): Result<CfWorkerScript> =
    withContext(Dispatchers.IO) {
      runCatching {
        val uncompressedBytes =
          workspace.files
            .filterNot { it.isVirtualReadOnly }
            .sumOf { it.content.toByteArray(Charsets.UTF_8).size.toLong() }
        if (uncompressedBytes > com.example.data.local.DeviceFileHelper.CLOUDFLARE_WORKER_MAX_UNCOMPRESSED_BYTES) {
          val sizeMb = String.format("%.1f", uncompressedBytes / (1024.0 * 1024.0))
          error("Worker bundle ($sizeMb MiB) exceeds Cloudflare Workers 64 MiB uncompressed upload limit")
        }
        val body =
          MultipartWorkerParser.buildUploadMultipartBody(
            files = workspace.files,
            isEsModule = workspace.isEsModuleFormat,
            entryModulePath = workspace.entryModulePath,
            compatibilityDate = workspace.compatibilityDate,
            compatibilityFlags = workspace.compatibilityFlags,
          )
        val resp = api.uploadWorkerScript(accountId, workspace.scriptName, body)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Worker deploy failed (HTTP ${resp.code()})")
        }
        resp.body()?.result ?: CfWorkerScript(id = workspace.scriptName)
      }
    }

  suspend fun createNewWorker(
    accountId: String,
    scriptName: String,
    template: WorkerTemplate,
    enableWorkersDev: Boolean = true,
  ): Result<CfWorkerScript> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sanitizedName = scriptName.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-")
        require(sanitizedName.isNotBlank()) { "Worker name is required" }

        val initialFile =
          WorkerFile(
            path = "index.js",
            content = template.code,
            originalContent = template.code,
            contentType = "application/javascript+module",
            isEntryPoint = true,
          )
        val body =
          MultipartWorkerParser.buildUploadMultipartBody(
            files = listOf(initialFile),
            isEsModule = true,
            entryModulePath = "index.js",
            compatibilityDate = "2024-11-01",
          )
        val resp = api.uploadWorkerScript(accountId, sanitizedName, body)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Could not create Worker '$sanitizedName'")
        }
        if (enableWorkersDev) {
          runCatching {
            api.setScriptSubdomain(accountId, sanitizedName, CfScriptSubdomainRequest(enabled = true))
          }
        }
        resp.body()?.result ?: CfWorkerScript(id = sanitizedName)
      }
    }

  suspend fun createWorkerFromUploadedFiles(
    accountId: String,
    scriptName: String,
    uploadedFiles: List<WorkerFile>,
    entryModulePath: String,
    enableWorkersDev: Boolean = true,
  ): Result<CfWorkerScript> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sanitizedName = scriptName.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-")
        require(sanitizedName.isNotBlank()) { "Worker name is required" }
        require(uploadedFiles.isNotEmpty()) { "Select at least one script file to upload" }

        val body =
          MultipartWorkerParser.buildUploadMultipartBody(
            files = uploadedFiles,
            isEsModule = true,
            entryModulePath = entryModulePath,
            compatibilityDate = "2024-11-01",
          )
        val resp = api.uploadWorkerScript(accountId, sanitizedName, body)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Could not upload & create Worker '$sanitizedName'")
        }
        if (enableWorkersDev) {
          runCatching {
            api.setScriptSubdomain(accountId, sanitizedName, CfScriptSubdomainRequest(enabled = true))
          }
        }
        resp.body()?.result ?: CfWorkerScript(id = sanitizedName)
      }
    }

  suspend fun deleteWorker(accountId: String, scriptName: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deleteWorkerScript(accountId, scriptName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to delete Worker '$scriptName'")
        }
      }
    }

  suspend fun renameRemoteWorker(
    accountId: String,
    oldScriptName: String,
    renamedWorkspace: WorkerWorkspace,
    deleteOldScript: Boolean = true,
    stagedSecrets: Map<String, String> = emptyMap(),
  ): Result<WorkerRenameOutcome> =
    withContext(Dispatchers.IO) {
      runCatching {
        val newScriptName = CloudflareNamingUtils.sanitizeWorkerName(renamedWorkspace.scriptName)
        require(CloudflareNamingUtils.isValidWorkerName(newScriptName)) {
          "Worker name must use lowercase letters, numbers, and hyphens (1-63 chars)"
        }
        val normalizedWs = renamedWorkspace.copy(scriptName = newScriptName)
        val deployedScript = deployWorkerWorkspace(accountId, normalizedWs).getOrThrow()

        val warnings = mutableListOf<String>()
        saveBindingsAndCompat(
          accountId = accountId,
          scriptName = newScriptName,
          compatibilityDate = normalizedWs.compatibilityDate,
          compatibilityFlags = normalizedWs.compatibilityFlags,
          bindings = normalizedWs.bindings.filterNot { it.type == "secret_text" },
        ).onFailure { warnings.add("settings: ${it.localizedMessage}") }

        toggleWorkersDev(accountId, newScriptName, normalizedWs.workersDevEnabled)
          .onFailure { warnings.add("workers.dev: ${it.localizedMessage}") }

        if (normalizedWs.schedules.isNotEmpty()) {
          updateCronSchedules(accountId, newScriptName, normalizedWs.schedules.map { it.cron })
            .onFailure { warnings.add("cron: ${it.localizedMessage}") }
        }

        stagedSecrets.forEach { (secretName, secretVal) ->
          putSecret(accountId, newScriptName, secretName, secretVal)
            .onFailure { warnings.add("secret $secretName: ${it.localizedMessage}") }
        }

        val uncopiedSecrets =
          normalizedWs.bindings
            .filter { it.type == "secret_text" && !stagedSecrets.containsKey(it.name) }
            .map { it.name }
        if (uncopiedSecrets.isNotEmpty()) {
          warnings.add("re-enter encrypted secret(s): ${uncopiedSecrets.joinToString(", ")}")
        }

        var deletedOld = false
        if (deleteOldScript && oldScriptName.isNotBlank() && oldScriptName != newScriptName) {
          deleteWorker(accountId, oldScriptName)
            .onSuccess { deletedOld = true }
            .onFailure { err ->
              warnings.add("old script '$oldScriptName' kept: ${err.localizedMessage}")
            }
        }

        WorkerRenameOutcome(
          script = deployedScript,
          deletedOldRemote = deletedOld,
          warningMessage = warnings.takeIf { it.isNotEmpty() }?.joinToString("; "),
        )
      }
    }

  suspend fun toggleWorkersDev(accountId: String, scriptName: String, enabled: Boolean): Result<Boolean> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.setScriptSubdomain(accountId, scriptName, CfScriptSubdomainRequest(enabled = enabled))
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to toggle workers.dev route")
        }
        resp.body()?.result?.enabled ?: enabled
      }
    }

  suspend fun getScriptSubdomainEnabled(accountId: String, scriptName: String): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.getScriptSubdomain(accountId, scriptName)
        resp.isSuccessful && resp.body()?.success == true && (resp.body()?.result?.enabled == true)
      }.getOrDefault(false)
    }

  suspend fun putSecret(accountId: String, scriptName: String, name: String, value: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.putWorkerSecret(accountId, scriptName, CfWorkerSecretPut(name = name.trim(), text = value))
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to set secret '$name'")
        }
      }
    }

  suspend fun deleteSecret(accountId: String, scriptName: String, secretName: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deleteWorkerSecret(accountId, scriptName, secretName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to delete secret '$secretName'")
        }
      }
    }

  suspend fun saveBindingsAndCompat(
    accountId: String,
    scriptName: String,
    compatibilityDate: String,
    compatibilityFlags: List<String>,
    bindings: List<CfWorkerBinding>,
  ): Result<CfWorkerSettings> =
    withContext(Dispatchers.IO) {
      runCatching {
        val adapter =
          clientFactory.moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
          )
        val settingsPayload =
          mapOf(
            "compatibility_date" to compatibilityDate,
            "compatibility_flags" to compatibilityFlags,
            "bindings" to
              bindings.map { b ->
                buildMap<String, Any?> {
                  put("name", b.name)
                  put("type", b.type)
                  if (b.text != null) put("text", b.text)
                  if (b.namespaceId != null) put("namespace_id", b.namespaceId)
                  if (b.databaseId != null) put("database_id", b.databaseId)
                  if (b.bucketName != null) put("bucket_name", b.bucketName)
                  if (b.service != null) put("service", b.service)
                  if (b.environment != null) put("environment", b.environment)
                  if (b.className != null) put("class_name", b.className)
                  if (b.scriptName != null) put("script_name", b.scriptName)
                  if (b.queueName != null) put("queue_name", b.queueName)
                  if (b.indexName != null) put("index_name", b.indexName)
                }
              },
          )
        val json = adapter.toJson(settingsPayload)
        val multipart =
          okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("settings", null, json.toRequestBody("application/json".toMediaType()))
            .build()
        val resp = api.patchWorkerSettings(accountId, scriptName, multipart)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to update Worker settings")
        }
        resp.body()?.result ?: CfWorkerSettings(
          compatibilityDate = compatibilityDate,
          compatibilityFlags = compatibilityFlags,
          bindings = bindings,
        )
      }
    }

  suspend fun updateCronSchedules(
    accountId: String,
    scriptName: String,
    crons: List<String>,
  ): Result<List<CfCronSchedule>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val payload = crons.map { CfCronSchedulePut(it.trim()) }.filter { it.cron.isNotBlank() }
        val resp = api.putWorkerSchedules(accountId, scriptName, payload)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to update Cron triggers")
        }
        resp.body()?.result?.schedules.orEmpty()
      }
    }

  suspend fun listDeployments(accountId: String, scriptName: String): Result<List<CfDeployment>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listWorkerDeployments(accountId, scriptName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to load Worker deployments")
        }
        resp.body()?.result?.deployments.orEmpty()
      }
    }

  suspend fun listWorkerVersions(accountId: String, scriptName: String): Result<List<CfWorkerVersion>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listWorkerVersions(accountId, scriptName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to load Worker versions")
        }
        resp.body()?.result?.items.orEmpty()
      }
    }

  suspend fun deployGradualVersions(
    accountId: String,
    scriptName: String,
    allocations: List<CfDeploymentVersion>,
    message: String? = null,
  ): Result<CfDeployment> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(allocations.isNotEmpty()) { "At least one version allocation is required" }
        val totalPct = allocations.sumOf { it.percentage }
        require(kotlin.math.abs(totalPct - 100.0) <= 0.1) { "Traffic percentages must sum to 100%" }
        val req =
          CfDeploymentCreateRequest(
            strategy = "percentage",
            versions = allocations,
            annotations = mapOf("workers/message" to (message?.takeIf { it.isNotBlank() } ?: "Gradual deploy via EdgeCode")),
          )
        val resp = api.createWorkerDeployment(accountId, scriptName, req)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Gradual deployment failed")
        }
        resp.body()?.result ?: CfDeployment()
      }
    }

  suspend fun rollbackToVersion(
    accountId: String,
    scriptName: String,
    versionId: String,
  ): Result<CfDeployment> =
    withContext(Dispatchers.IO) {
      runCatching {
        val req =
          CfDeploymentCreateRequest(
            strategy = "percentage",
            versions = listOf(CfDeploymentVersion(versionId = versionId, percentage = 100.0)),
            annotations = mapOf("workers/message" to "Rollback via EdgeCode IDE"),
          )
        val resp = api.createWorkerDeployment(accountId, scriptName, req)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Rollback failed")
        }
        resp.body()?.result ?: CfDeployment()
      }
    }

  suspend fun listZones(accountId: String): Result<List<CfZone>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listZones(accountId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to load Cloudflare Zones")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun createZoneRoute(zoneId: String, pattern: String, scriptName: String): Result<CfWorkerRoute> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.createZoneRoute(zoneId, CfZoneRouteCreate(pattern = pattern.trim(), script = scriptName))
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to attach route '$pattern'")
        }
        resp.body()?.result ?: CfWorkerRoute(pattern = pattern, script = scriptName)
      }
    }

  suspend fun deleteZoneRoute(zoneId: String, routeId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deleteZoneRoute(zoneId, routeId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to remove route")
        }
      }
    }

  suspend fun startLiveTail(
    accountId: String,
    scriptName: String,
    onTailEvent: (String) -> Unit,
    onClosed: (String?) -> Unit,
  ): Result<ActiveTailSession> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.startWorkerTail(accountId, scriptName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to start Live Tail")
        }
        val tail: CfTailResult = resp.body()?.result ?: error("Missing tail session URL")
        val req =
          Request.Builder()
            .url(tail.url)
            .header("Sec-WebSocket-Protocol", "trace-v1")
            .build()
        val ws =
          clientFactory.rawHttpClient.newWebSocket(
            req,
            object : WebSocketListener() {
              override fun onOpen(webSocket: WebSocket, response: OkHttpResponse) {
                webSocket.send("""{"filters":[]}""")
                onTailEvent("LIVE TAIL CONNECTED ($scriptName)")
              }

              override fun onMessage(webSocket: WebSocket, text: String) {
                formatTailTracePayload(text).forEach(onTailEvent)
              }

              override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val decoded = bytes.utf8()
                formatTailTracePayload(decoded).forEach(onTailEvent)
              }

              override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkHttpResponse?) {
                onClosed(t.localizedMessage ?: "Tail connection closed")
              }

              override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onClosed(reason.ifBlank { null })
              }
            },
          )
        ActiveTailSession(tailId = tail.id, webSocket = ws)
      }
    }

  internal fun formatTailTracePayload(rawPayload: String): List<String> =
    formatTailTraceEvents(rawPayload)

  companion object {
    fun formatTailTraceEvents(rawPayload: String): List<String> {
      val trimmed = rawPayload.trim()
      if (trimmed.isEmpty()) return emptyList()
      if (!trimmed.startsWith("{")) return listOf(trimmed)
      val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return listOf(trimmed)
      val lines = mutableListOf<String>()
      val outcome = json.optString("outcome").trim()
      val reqObj = json.optJSONObject("event")?.optJSONObject("request")
      val reqMethod = reqObj?.optString("method")?.trim().orEmpty()
      val reqUrl = reqObj?.optString("url")?.trim().orEmpty()
      if (reqMethod.isNotEmpty() || reqUrl.isNotEmpty() || outcome.isNotEmpty()) {
        val statusTag = if (outcome.isNotEmpty()) outcome.uppercase() else "EVENT"
        val reqLabel = listOf(reqMethod, reqUrl).filter { it.isNotEmpty() }.joinToString(" ")
        lines.add(if (reqLabel.isNotEmpty()) "[$statusTag] $reqLabel" else "[$statusTag]")
      }
      val logsArr = json.optJSONArray("logs")
      if (logsArr != null) {
        for (i in 0 until logsArr.length()) {
          val entry = logsArr.optJSONObject(i) ?: continue
          val lvl = entry.optString("level", "log").uppercase()
          val msgArr = entry.optJSONArray("message")
          val msgText =
            if (msgArr != null) {
              (0 until msgArr.length()).joinToString(" ") { idx -> msgArr.opt(idx)?.toString().orEmpty() }.trim()
            } else {
              entry.optString("message").trim()
            }
          if (msgText.isNotEmpty()) {
            lines.add("[$lvl] $msgText")
          }
        }
      }
      val exArr = json.optJSONArray("exceptions")
      if (exArr != null) {
        for (i in 0 until exArr.length()) {
          val ex = exArr.optJSONObject(i) ?: continue
          val name = ex.optString("name", "Error").trim()
          val message = ex.optString("message").trim()
          lines.add(if (message.isNotEmpty()) "[ERR] $name: $message" else "[ERR] $name")
        }
      }
      return if (lines.isNotEmpty()) lines else listOf(trimmed)
    }
  }

  suspend fun stopLiveTail(accountId: String, scriptName: String, session: ActiveTailSession) {
    withContext(Dispatchers.IO) {
      runCatching { session.webSocket.close(1000, "Stopped by user") }
      runCatching { api.stopWorkerTail(accountId, scriptName, session.tailId) }
    }
  }

  suspend fun listKvNamespaces(accountId: String): Result<List<CfKvNamespace>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listKvNamespaces(accountId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to load KV namespaces")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun listKvKeys(accountId: String, namespaceId: String, prefix: String? = null): Result<List<CfKvKey>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listKvKeys(accountId, namespaceId, prefix = prefix?.takeIf { it.isNotBlank() })
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to list KV keys")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun readKvValue(accountId: String, namespaceId: String, keyName: String): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.getKvValue(accountId, namespaceId, keyName)
        if (!resp.isSuccessful) {
          error("Failed to read KV key '$keyName' (HTTP ${resp.code()})")
        }
        resp.body()?.string().orEmpty()
      }
    }

  suspend fun writeKvValue(
    accountId: String,
    namespaceId: String,
    keyName: String,
    value: String,
    expirationTtl: Long? = null,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val validTtl = expirationTtl?.takeIf { it >= 60L }
        val resp =
          api.putKvValue(
            accountId = accountId,
            namespaceId = namespaceId,
            keyName = keyName,
            value = value.toRequestBody("text/plain".toMediaType()),
            expirationTtl = validTtl,
          )
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to write KV key '$keyName'")
        }
      }
    }

  suspend fun bulkPutKvEntries(
    accountId: String,
    namespaceId: String,
    items: List<CfKvBulkItem>,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(items.isNotEmpty()) { "No KV entries provided for bulk import" }
        val resp = api.putKvBulk(accountId, namespaceId, items)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "KV bulk import failed")
        }
      }
    }

  suspend fun deleteKvKey(accountId: String, namespaceId: String, keyName: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deleteKvValue(accountId, namespaceId, keyName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to delete KV key '$keyName'")
        }
      }
    }

  suspend fun createKvNamespace(accountId: String, title: String): Result<CfKvNamespace> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.createKvNamespace(accountId, CfKvNamespaceRequest(title.trim()))
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to create KV namespace")
        }
        resp.body()?.result ?: error("Empty KV create response")
      }
    }

  suspend fun deleteKvNamespace(accountId: String, namespaceId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deleteKvNamespace(accountId, namespaceId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to delete KV namespace")
        }
      }
    }

  // Worker Custom Domains
  suspend fun attachWorkerDomain(
    accountId: String,
    hostname: String,
    serviceName: String,
    zoneId: String,
  ): Result<CfWorkerDomainRecord> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp =
          api.attachWorkerDomain(
            accountId,
            CfWorkerDomainPutRequest(hostname = hostname.trim(), service = serviceName, zoneId = zoneId),
          )
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to attach Worker domain")
        }
        resp.body()?.result ?: error("Empty domain response")
      }
    }

  suspend fun removeWorkerDomain(accountId: String, domainId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deleteWorkerDomain(accountId, domainId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to remove Worker domain")
        }
      }
    }

  // D1 Databases & SQL Query Runner
  suspend fun listD1Databases(accountId: String): Result<List<CfD1Database>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listD1Databases(accountId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to list D1 databases")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun createD1Database(accountId: String, name: String): Result<CfD1Database> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.createD1Database(accountId, CfD1CreateRequest(name.trim()))
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to create D1 database")
        }
        resp.body()?.result ?: error("Empty D1 create response")
      }
    }

  suspend fun deleteD1Database(accountId: String, databaseId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deleteD1Database(accountId, databaseId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to delete D1 database")
        }
      }
    }

  suspend fun queryD1Database(accountId: String, databaseId: String, sql: String): Result<List<CfD1QueryResult>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.queryD1Database(accountId, databaseId, CfD1QueryRequest(sql))
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "D1 SQL query failed")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun introspectD1Schema(
    accountId: String,
    databaseId: String,
    databaseName: String,
  ): Result<D1SchemaGraph> =
    withContext(Dispatchers.IO) {
      runCatching {
        val masterResp = api.queryD1Database(accountId, databaseId, CfD1QueryRequest(D1RelationalEngine.buildIntrospectionMasterSql()))
        if (!masterResp.isSuccessful || masterResp.body()?.success != true) {
          error(extractCfError(masterResp) ?: "Failed to inspect sqlite_master")
        }
        val tablesWithDdl = D1RelationalEngine.parseMasterTables(masterResp.body()?.result?.firstOrNull())
        val allTables = mutableListOf<D1TableInfo>()
        tablesWithDdl.chunked(6).forEach { chunk ->
          val batchSql = D1RelationalEngine.buildTableInspectionSqlChunk(chunk.map { it.first })
          val chunkResp = api.queryD1Database(accountId, databaseId, CfD1QueryRequest(batchSql))
          if (!chunkResp.isSuccessful || chunkResp.body()?.success != true) {
            error(extractCfError(chunkResp) ?: "Failed to inspect D1 table metadata")
          }
          allTables.addAll(
            D1RelationalEngine.parseTableDetailsChunk(
              databaseId = databaseId,
              databaseName = databaseName,
              tablesWithDdl = chunk,
              chunkResults = chunkResp.body()?.result.orEmpty(),
            ),
          )
        }
        D1SchemaGraph(
          databaseId = databaseId,
          databaseName = databaseName,
          tables = allTables,
          introspectedAtEpochMs = System.currentTimeMillis(),
        )
      }
    }

  suspend fun loadD1AppliedMigrations(
    accountId: String,
    databaseId: String,
  ): Result<Pair<String, List<D1AppliedMigrationRecord>>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val detectSql = "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('d1_migrations', '_edgecode_migrations');"
        val detectResp = api.queryD1Database(accountId, databaseId, CfD1QueryRequest(detectSql))
        if (!detectResp.isSuccessful || detectResp.body()?.success != true) {
          error(extractCfError(detectResp) ?: "Failed checking migration ledger")
        }
        val existingTables =
          detectResp.body()?.result?.firstOrNull()?.results
            ?.mapNotNull { it["name"]?.toString() }
            .orEmpty()
        val ledgerTable =
          when {
            "d1_migrations" in existingTables -> "d1_migrations"
            "_edgecode_migrations" in existingTables -> "_edgecode_migrations"
            else -> "_edgecode_migrations"
          }
        if (ledgerTable !in existingTables) {
          return@runCatching ledgerTable to emptyList()
        }
        val readSql = "SELECT * FROM ${D1RelationalEngine.quoteIdent(ledgerTable)} ORDER BY id ASC;"
        val readResp = api.queryD1Database(accountId, databaseId, CfD1QueryRequest(readSql))
        if (!readResp.isSuccessful || readResp.body()?.success != true) {
          return@runCatching ledgerTable to emptyList()
        }
        val records =
          readResp.body()?.result?.firstOrNull()?.results.orEmpty().mapNotNull { row ->
            val name = row["name"]?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val appliedAt = row["applied_at"]?.toString().orEmpty()
            val checksum = row["checksum"]?.toString()
            D1AppliedMigrationRecord(name = name, appliedAt = appliedAt, checksum = checksum)
          }
        ledgerTable to records
      }
    }

  suspend fun applyD1Migration(
    accountId: String,
    databaseId: String,
    ledgerTable: String,
    migration: D1WorkspaceMigration,
  ): Result<List<CfD1QueryResult>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val qLedger = D1RelationalEngine.quoteIdent(ledgerTable)
        val createLedgerSql =
          if (ledgerTable == "d1_migrations") {
            "CREATE TABLE IF NOT EXISTS \"d1_migrations\" (\"id\" INTEGER PRIMARY KEY AUTOINCREMENT, \"name\" TEXT UNIQUE, \"applied_at\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL);"
          } else {
            "CREATE TABLE IF NOT EXISTS \"_edgecode_migrations\" (\"id\" INTEGER PRIMARY KEY AUTOINCREMENT, \"name\" TEXT UNIQUE NOT NULL, \"checksum\" TEXT, \"applied_at\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL);"
          }
        val createResp = api.queryD1Database(accountId, databaseId, CfD1QueryRequest(createLedgerSql))
        if (!createResp.isSuccessful || createResp.body()?.success != true) {
          error(extractCfError(createResp) ?: "Failed initializing migration ledger '$ledgerTable'")
        }
        val stmts = D1RelationalEngine.splitSqlStatements(migration.sql)
        require(stmts.isNotEmpty()) { "Migration '${migration.fileName}' contains no SQL statements" }
        val migrationSql = stmts.joinToString("\n")
        val execResp = api.queryD1Database(accountId, databaseId, CfD1QueryRequest(migrationSql))
        if (!execResp.isSuccessful || execResp.body()?.success != true) {
          error(extractCfError(execResp) ?: "Migration '${migration.fileName}' failed")
        }
        val escName = migration.fileName.replace("'", "''")
        val escHash = migration.checksum.replace("'", "''")
        val recordSql =
          if (ledgerTable == "d1_migrations") {
            "INSERT OR REPLACE INTO $qLedger (\"name\") VALUES ('$escName');"
          } else {
            "INSERT OR REPLACE INTO $qLedger (\"name\", \"checksum\") VALUES ('$escName', '$escHash');"
          }
        val recordResp = api.queryD1Database(accountId, databaseId, CfD1QueryRequest(recordSql))
        if (!recordResp.isSuccessful || recordResp.body()?.success != true) {
          error(extractCfError(recordResp) ?: "Executed SQL but failed recording ledger entry")
        }
        execResp.body()?.result.orEmpty()
      }
    }

  // R2 Buckets
  suspend fun listR2Buckets(accountId: String): Result<List<CfR2Bucket>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listR2Buckets(accountId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to list R2 buckets")
        }
        resp.body()?.result?.buckets.orEmpty()
      }
    }

  suspend fun createR2Bucket(accountId: String, name: String): Result<CfR2Bucket> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.createR2Bucket(accountId, CfR2CreateRequest(name.trim()))
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to create R2 bucket")
        }
        resp.body()?.result ?: CfR2Bucket(name = name.trim())
      }
    }

  suspend fun deleteR2Bucket(accountId: String, bucketName: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deleteR2Bucket(accountId, bucketName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to delete R2 bucket")
        }
      }
    }

  // Durable Objects & Queues
  suspend fun listDurableObjects(accountId: String): Result<List<CfDurableObjectNamespace>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listDurableObjects(accountId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to list Durable Objects")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun listQueues(accountId: String): Result<List<CfQueue>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listQueues(accountId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to list Queues")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun createQueue(accountId: String, queueName: String): Result<CfQueue> =
    withContext(Dispatchers.IO) {
      runCatching {
        val clean = queueName.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-")
        require(clean.isNotBlank()) { "Queue name cannot be empty" }
        val resp = api.createQueue(accountId, CfQueueCreateRequest(clean))
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to create queue '$clean'")
        }
        resp.body()?.result ?: CfQueue(queueName = clean)
      }
    }

  suspend fun deleteQueue(accountId: String, queueId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deleteQueue(accountId, queueId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to delete queue")
        }
      }
    }

  // Cloudflare Pages Management
  suspend fun listPagesProjects(accountId: String): Result<List<CfPagesProject>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listPagesProjects(accountId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to load Pages projects")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun createPagesProject(
    accountId: String,
    name: String,
    productionBranch: String = "main",
  ): Result<CfPagesProject> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanName = name.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-")
        val resp =
          api.createPagesProject(
            accountId,
            CfPagesProjectCreateRequest(name = cleanName, productionBranch = productionBranch.ifBlank { "main" }),
          )
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to create Pages project '$cleanName'")
        }
        resp.body()?.result ?: CfPagesProject(name = cleanName)
      }
    }

  suspend fun deletePagesProject(accountId: String, projectName: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deletePagesProject(accountId, projectName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to delete Pages project '$projectName'")
        }
      }
    }

  suspend fun listPagesDeployments(accountId: String, projectName: String): Result<List<CfPagesDeployment>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listPagesDeployments(accountId, projectName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to list Pages deployments")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun triggerPagesDeployment(accountId: String, projectName: String): Result<CfPagesDeployment> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.triggerPagesDeployment(accountId, projectName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to trigger Pages deployment")
        }
        resp.body()?.result ?: error("Empty deployment response")
      }
    }

  suspend fun retryPagesDeployment(accountId: String, projectName: String, deploymentId: String): Result<CfPagesDeployment> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.retryPagesDeployment(accountId, projectName, deploymentId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Retry deployment failed")
        }
        resp.body()?.result ?: error("Empty retry response")
      }
    }

  suspend fun rollbackPagesDeployment(accountId: String, projectName: String, deploymentId: String): Result<CfPagesDeployment> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.rollbackPagesDeployment(accountId, projectName, deploymentId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Pages rollback failed")
        }
        resp.body()?.result ?: error("Empty rollback response")
      }
    }

  suspend fun getPagesDeploymentLogs(accountId: String, projectName: String, deploymentId: String): Result<CfPagesBuildLogsResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.getPagesDeploymentLogs(accountId, projectName, deploymentId)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Could not fetch Pages build logs")
        }
        resp.body()?.result ?: CfPagesBuildLogsResult()
      }
    }

  suspend fun updatePagesEnvVars(
    accountId: String,
    projectName: String,
    environment: String, // "production" or "preview"
    envVars: Map<String, CfPagesEnvVarValue>,
  ): Result<CfPagesProject> =
    withContext(Dispatchers.IO) {
      runCatching {
        val adapter =
          clientFactory.moshi.adapter<Map<String, Any>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
          )
        val envBlock =
          mapOf(
            "env_vars" to
              envVars.mapValues { (_, v) ->
                if (v.value == null) mapOf("type" to v.type)
                else mapOf("type" to v.type, "value" to v.value)
              },
          )
        val payload = mapOf("deployment_configs" to mapOf(environment to envBlock))
        val body = adapter.toJson(payload).toRequestBody("application/json".toMediaType())
        val resp = api.patchPagesProject(accountId, projectName, body)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to update Pages environment variables")
        }
        resp.body()?.result ?: error("Empty Pages patch response")
      }
    }

  suspend fun updatePagesBuildConfig(
    accountId: String,
    projectName: String,
    productionBranch: String,
    buildCommand: String,
    destinationDir: String,
    rootDir: String,
  ): Result<CfPagesProject> =
    withContext(Dispatchers.IO) {
      runCatching {
        val adapter =
          clientFactory.moshi.adapter<Map<String, Any>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
          )
        val payload =
          mapOf(
            "production_branch" to productionBranch.trim().ifBlank { "main" },
            "build_config" to
              mapOf(
                "build_command" to buildCommand.trim(),
                "destination_dir" to destinationDir.trim(),
                "root_dir" to rootDir.trim(),
              ),
          )
        val body = adapter.toJson(payload).toRequestBody("application/json".toMediaType())
        val resp = api.patchPagesProject(accountId, projectName, body)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to update Pages build configuration")
        }
        resp.body()?.result ?: error("Empty Pages patch response")
      }
    }

  suspend fun updatePagesBindings(
    accountId: String,
    projectName: String,
    environment: String,
    kvBindings: Map<String, String>,
    d1Bindings: Map<String, String>,
    r2Bindings: Map<String, String>,
  ): Result<CfPagesProject> =
    withContext(Dispatchers.IO) {
      runCatching {
        val adapter =
          clientFactory.moshi.adapter<Map<String, Any>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
          )
        val envBlock =
          buildMap<String, Any> {
            put("kv_namespaces", kvBindings.mapValues { (_, id) -> mapOf("namespace_id" to id) })
            put("d1_databases", d1Bindings.mapValues { (_, id) -> mapOf("id" to id) })
            put("r2_buckets", r2Bindings.mapValues { (_, bucket) -> mapOf("name" to bucket) })
          }
        val payload = mapOf("deployment_configs" to mapOf(environment to envBlock))
        val body = adapter.toJson(payload).toRequestBody("application/json".toMediaType())
        val resp = api.patchPagesProject(accountId, projectName, body)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to update Pages bindings")
        }
        resp.body()?.result ?: error("Empty Pages patch response")
      }
    }

  suspend fun listPagesDomains(accountId: String, projectName: String): Result<List<CfPagesDomain>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.listPagesDomains(accountId, projectName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to load Pages domains")
        }
        resp.body()?.result.orEmpty()
      }
    }

  suspend fun addPagesDomain(accountId: String, projectName: String, domainName: String): Result<CfPagesDomain> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.addPagesDomain(accountId, projectName, CfPagesDomainCreateRequest(domainName.trim()))
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to attach Pages domain")
        }
        resp.body()?.result ?: error("Empty domain response")
      }
    }

  suspend fun deletePagesDomain(accountId: String, projectName: String, domainName: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      runCatching {
        val resp = api.deletePagesDomain(accountId, projectName, domainName)
        if (!resp.isSuccessful || resp.body()?.success != true) {
          error(extractCfError(resp) ?: "Failed to remove Pages domain")
        }
      }
    }

  suspend fun preparePagesAssets(rawAssets: List<RawPickedAsset>): List<PagesPreparedAsset> =
    withContext(Dispatchers.IO) {
      val deduped = linkedMapOf<String, PagesPreparedAsset>()
      for (raw in rawAssets) {
        val prepared =
          PagesDirectUploadHasher.prepareAssetLazy(
            rawPath = raw.relativePath,
            diskFile = raw.diskFile,
            inMemoryBytes = raw.bytes,
            byteSize = raw.byteSize,
            maxCloudflareSingleFileBytes = com.example.data.local.DeviceFileHelper.CLOUDFLARE_PAGES_MAX_SINGLE_FILE_BYTES,
          ) ?: continue
        deduped[prepared.normalizedPath] = prepared
      }
      deduped.values.toList()
    }

  suspend fun directUploadPagesDeployment(
    accountId: String,
    projectName: String,
    preparedAssets: List<PagesPreparedAsset>,
    branch: String?,
    commitMessage: String?,
    onProgress: (PagesDirectUploadProgress) -> Unit = {},
  ): Result<CfPagesDeployment> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(preparedAssets.isNotEmpty()) { "Select at least one file or folder to deploy" }

        val cfLimit = com.example.data.local.DeviceFileHelper.CLOUDFLARE_PAGES_MAX_SINGLE_FILE_BYTES
        val oversized = preparedAssets.firstOrNull { it.actualByteSize > cfLimit }
        if (oversized != null) {
          val mb = String.format("%.1f", oversized.actualByteSize / (1024.0 * 1024.0))
          error(
            "File '${oversized.normalizedPath}' ($mb MiB) exceeds Cloudflare Pages' 25 MiB per-file deploy limit. Remove or compress it before deploying.",
          )
        }

        val workerEntry = preparedAssets.firstOrNull { it.normalizedPath == "/_worker.js" }
        val staticAssets = preparedAssets.filterNot { it.normalizedPath == "/_worker.js" }
        val totalAssetCount = staticAssets.size
        val totalAssetBytes = staticAssets.sumOf { it.actualByteSize }

        onProgress(
          PagesDirectUploadProgress(
            phase = "Requesting upload token...",
            completedFiles = 0,
            totalFiles = totalAssetCount,
            uploadedBytes = 0L,
            totalBytes = totalAssetBytes,
          )
        )

        val tokenResp = api.getPagesUploadToken(accountId, projectName)
        if (!tokenResp.isSuccessful || tokenResp.body()?.success != true) {
          error(extractCfError(tokenResp) ?: "Failed to obtain Pages upload token (HTTP ${tokenResp.code()})")
        }
        val jwt = tokenResp.body()?.result?.jwt?.takeIf { it.isNotBlank() }
          ?: error("Cloudflare returned empty Pages upload token")
        val bearerJwt = "Bearer $jwt"

        if (staticAssets.isNotEmpty()) {
          val uniqueByHash = linkedMapOf<String, PagesPreparedAsset>()
          staticAssets.forEach { asset -> uniqueByHash.putIfAbsent(asset.hash, asset) }
          val allHashes = uniqueByHash.keys.toList()

          onProgress(
            PagesDirectUploadProgress(
              phase = "Checking Cloudflare edge cache (${allHashes.size} assets)...",
              completedFiles = 0,
              totalFiles = totalAssetCount,
              uploadedBytes = 0L,
              totalBytes = totalAssetBytes,
            )
          )

          val missingResp = api.checkMissingPagesAssets(bearerJwt, CfPagesCheckMissingRequest(allHashes))
          if (!missingResp.isSuccessful || missingResp.body()?.success != true) {
            error(extractCfError(missingResp) ?: "Failed to check missing Pages assets")
          }
          val missingSet = missingResp.body()?.result.orEmpty().toSet()
          val toUpload = uniqueByHash.values.filter { it.hash in missingSet }

          val cachedBytes =
            staticAssets.filter { it.hash !in missingSet }.sumOf { it.actualByteSize }
          var uploadedBytesSoFar = cachedBytes
          var uploadedFilesSoFar = staticAssets.count { it.hash !in missingSet }

          if (toUpload.isNotEmpty()) {
            // Chunk into <= 12 MiB base64 payload and <= 150 files per batch
            val maxBatchBase64Bytes = 12L * 1024L * 1024L
            val maxBatchItems = 150
            val batches = mutableListOf<List<PagesPreparedAsset>>()
            var currentBatch = mutableListOf<PagesPreparedAsset>()
            var currentBatchBytes = 0L

            for (asset in toUpload) {
              val estBase64Size = ((asset.actualByteSize + 2L) / 3L) * 4L
              if (
                currentBatch.isNotEmpty() &&
                  (currentBatchBytes + estBase64Size > maxBatchBase64Bytes || currentBatch.size >= maxBatchItems)
              ) {
                batches.add(currentBatch)
                currentBatch = mutableListOf()
                currentBatchBytes = 0L
              }
              currentBatch.add(asset)
              currentBatchBytes += estBase64Size
            }
            if (currentBatch.isNotEmpty()) {
              batches.add(currentBatch)
            }

            batches.forEachIndexed { idx, batch ->
              onProgress(
                PagesDirectUploadProgress(
                  phase = "Uploading batch ${idx + 1}/${batches.size} (${batch.size} files)...",
                  completedFiles = uploadedFilesSoFar,
                  totalFiles = totalAssetCount,
                  uploadedBytes = uploadedBytesSoFar,
                  totalBytes = totalAssetBytes,
                )
              )
              val payload =
                batch.map { asset ->
                  CfPagesUploadPayloadItem(
                    key = asset.hash,
                    value = asset.resolveBase64Content(),
                    metadata = CfPagesAssetMetadata(contentType = asset.contentType),
                    base64 = true,
                  )
                }
              val uploadResp = api.uploadPagesAssets(bearerJwt, payload)
              if (!uploadResp.isSuccessful || uploadResp.body()?.success != true) {
                error(extractCfError(uploadResp) ?: "Pages asset batch upload failed (HTTP ${uploadResp.code()})")
              }
              val batchHashSet = batch.mapTo(hashSetOf()) { it.hash }
              uploadedBytesSoFar += staticAssets.filter { it.hash in batchHashSet }.sumOf { it.actualByteSize }
              uploadedFilesSoFar += staticAssets.count { it.hash in batchHashSet }
            }
          }

          onProgress(
            PagesDirectUploadProgress(
              phase = "Registering asset hashes...",
              completedFiles = totalAssetCount,
              totalFiles = totalAssetCount,
              uploadedBytes = totalAssetBytes,
              totalBytes = totalAssetBytes,
            )
          )

          val upsertResp = api.upsertPagesHashes(bearerJwt, CfPagesUpsertHashesRequest(allHashes))
          if (!upsertResp.isSuccessful || upsertResp.body()?.success != true) {
            error(extractCfError(upsertResp) ?: "Failed to upsert Pages asset hashes")
          }
        }

        onProgress(
          PagesDirectUploadProgress(
            phase = "Finalizing deployment manifest...",
            completedFiles = totalAssetCount,
            totalFiles = totalAssetCount,
            uploadedBytes = totalAssetBytes,
            totalBytes = totalAssetBytes,
          )
        )

        val manifestMap = linkedMapOf<String, String>()
        staticAssets.forEach { asset ->
          manifestMap[asset.normalizedPath] = asset.hash
        }
        val manifestAdapter =
          clientFactory.moshi.adapter<Map<String, String>>(
            Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
          )
        val manifestJson = manifestAdapter.toJson(manifestMap)

        val multipartBuilder =
          okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("manifest", manifestJson)

        val cleanBranch = branch?.trim().orEmpty()
        if (cleanBranch.isNotBlank()) {
          multipartBuilder.addFormDataPart("branch", cleanBranch)
        }
        val cleanMsg = commitMessage?.trim().orEmpty()
        if (cleanMsg.isNotBlank()) {
          multipartBuilder.addFormDataPart("commit_message", cleanMsg)
        }
        if (workerEntry != null) {
          multipartBuilder.addFormDataPart(
            "_worker.js",
            "_worker.js",
            workerEntry.resolveBytes().toRequestBody("application/javascript+module".toMediaType()),
          )
        }

        val deployResp =
          api.createPagesDirectDeployment(accountId, projectName, multipartBuilder.build())
        if (!deployResp.isSuccessful || deployResp.body()?.success != true) {
          error(extractCfError(deployResp) ?: "Pages Direct Upload deployment failed (HTTP ${deployResp.code()})")
        }
        deployResp.body()?.result ?: error("Empty Pages deployment result")
      }
    }

  suspend fun executeLiveHttpTest(
    url: String,
    method: String,
    headersMap: Map<String, String>,
    bodyText: String,
  ): HttpTestResult =
    withContext(Dispatchers.IO) {
      val started = System.currentTimeMillis()
      val upperMethod = method.trim().uppercase().ifBlank { "GET" }
      try {
        val parsedUrl = url.trim().toHttpUrlOrNull()
          ?: return@withContext HttpTestResult(
            url = url,
            method = upperMethod,
            statusCode = 0,
            statusText = "Invalid URL",
            durationMs = 0L,
            headers = emptyMap(),
            body = "",
            errorMessage = "Malformed URL '$url'. Use a valid http:// or https:// address.",
          )

        val reqBuilder = Request.Builder().url(parsedUrl)
        headersMap.forEach { (rawKey, rawVal) ->
          val cleanKey = rawKey.replace("\r", "").replace("\n", "").trim()
          val cleanVal = rawVal.replace("\r", "").replace("\n", "").trim()
          if (cleanKey.isNotEmpty()) {
            try {
              reqBuilder.header(cleanKey, cleanVal)
            } catch (headerErr: IllegalArgumentException) {
              return@withContext HttpTestResult(
                url = parsedUrl.toString(),
                method = upperMethod,
                statusCode = 0,
                statusText = "Invalid Header",
                durationMs = System.currentTimeMillis() - started,
                headers = emptyMap(),
                body = "",
                errorMessage = "Invalid header '$cleanKey': ${headerErr.localizedMessage ?: "illegal characters"}",
              )
            }
          }
        }

        val reqBody =
          if (upperMethod in listOf("POST", "PUT", "PATCH") || (upperMethod == "DELETE" && bodyText.isNotEmpty())) {
            val contentTypeHeader =
              headersMap.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value?.trim()
            val media =
              contentTypeHeader?.toMediaTypeOrNull()
                ?: "application/json; charset=utf-8".toMediaType()
            bodyText.toRequestBody(media)
          } else null

        reqBuilder.method(upperMethod, reqBody)
        clientFactory.rawHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
          val duration = System.currentTimeMillis() - started
          val respHeaders = linkedMapOf<String, String>()
          for (i in 0 until resp.headers.size) {
            val name = resp.headers.name(i)
            val value = resp.headers.value(i)
            val existing = respHeaders[name]
            respHeaders[name] =
              if (existing == null) {
                value
              } else if (name.equals("Set-Cookie", ignoreCase = true)) {
                "$existing\n$value"
              } else {
                "$existing, $value"
              }
          }

          val contentType = resp.header("Content-Type").orEmpty().lowercase()
          val isBinary =
            contentType.startsWith("image/") ||
              contentType.startsWith("audio/") ||
              contentType.startsWith("video/") ||
              contentType.startsWith("font/") ||
              contentType.contains("application/octet-stream") ||
              contentType.contains("application/pdf") ||
              contentType.contains("application/zip") ||
              contentType.contains("application/wasm")

          val maxReadBytes = 1_048_576L // 1 MB safety cap to keep UI smooth
          val respBody =
            if (upperMethod == "HEAD") {
              ""
            } else if (isBinary) {
              val len = resp.body?.contentLength()?.takeIf { it >= 0 }
              val sizeLabel = len?.let { "$it bytes" } ?: "streaming binary"
              "[Binary response: ${contentType.ifBlank { "application/octet-stream" }} • $sizeLabel]"
            } else {
              val peeked = runCatching { resp.peekBody(maxReadBytes).bytes() }.getOrNull()
              if (peeked == null) {
                ""
              } else {
                val charset = resp.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                val decoded = String(peeked, charset)
                val fullLen = resp.body?.contentLength() ?: -1L
                if (peeked.size >= maxReadBytes.toInt() && (fullLen < 0 || fullLen > maxReadBytes)) {
                  "$decoded\n\n/* … Response truncated at 1 MB for responsive display */"
                } else {
                  decoded
                }
              }
            }

          val statusText = resp.message.ifBlank { httpReasonPhrase(resp.code) }
          HttpTestResult(
            url = parsedUrl.toString(),
            method = upperMethod,
            statusCode = resp.code,
            statusText = statusText,
            durationMs = duration,
            headers = respHeaders,
            body = respBody,
          )
        }
      } catch (e: UnknownHostException) {
        HttpTestResult(
          url = url,
          method = upperMethod,
          statusCode = 0,
          statusText = "DNS Error",
          durationMs = System.currentTimeMillis() - started,
          headers = emptyMap(),
          body = "",
          errorMessage = "Could not resolve host (${e.localizedMessage ?: "DNS lookup failed"}). Check the domain or deploy your Worker first.",
        )
      } catch (e: SocketTimeoutException) {
        HttpTestResult(
          url = url,
          method = upperMethod,
          statusCode = 0,
          statusText = "Timeout",
          durationMs = System.currentTimeMillis() - started,
          headers = emptyMap(),
          body = "",
          errorMessage = "Request timed out while waiting for a response from the endpoint.",
        )
      } catch (e: SSLHandshakeException) {
        HttpTestResult(
          url = url,
          method = upperMethod,
          statusCode = 0,
          statusText = "TLS Error",
          durationMs = System.currentTimeMillis() - started,
          headers = emptyMap(),
          body = "",
          errorMessage = "TLS/SSL handshake failed: ${e.localizedMessage ?: "certificate verification error"}.",
        )
      } catch (e: ConnectException) {
        HttpTestResult(
          url = url,
          method = upperMethod,
          statusCode = 0,
          statusText = "Connection Refused",
          durationMs = System.currentTimeMillis() - started,
          headers = emptyMap(),
          body = "",
          errorMessage = "Connection refused: ${e.localizedMessage ?: "target server is unreachable"}.",
        )
      } catch (e: Exception) {
        HttpTestResult(
          url = url,
          method = upperMethod,
          statusCode = 0,
          statusText = "Network Error",
          durationMs = System.currentTimeMillis() - started,
          headers = emptyMap(),
          body = "",
          errorMessage = e.localizedMessage ?: "Failed to connect to Worker endpoint",
        )
      }
    }

  private fun httpReasonPhrase(code: Int): String =
    when (code) {
      100 -> "Continue"
      101 -> "Switching Protocols"
      200 -> "OK"
      201 -> "Created"
      202 -> "Accepted"
      204 -> "No Content"
      206 -> "Partial Content"
      301 -> "Moved Permanently"
      302 -> "Found"
      304 -> "Not Modified"
      307 -> "Temporary Redirect"
      308 -> "Permanent Redirect"
      400 -> "Bad Request"
      401 -> "Unauthorized"
      403 -> "Forbidden"
      404 -> "Not Found"
      405 -> "Method Not Allowed"
      408 -> "Request Timeout"
      409 -> "Conflict"
      410 -> "Gone"
      413 -> "Payload Too Large"
      415 -> "Unsupported Media Type"
      422 -> "Unprocessable Entity"
      429 -> "Too Many Requests"
      500 -> "Internal Server Error"
      502 -> "Bad Gateway"
      503 -> "Service Unavailable"
      504 -> "Gateway Timeout"
      520 -> "Web Server Returned Unknown Error"
      522 -> "Connection Timed Out"
      524 -> "A Timeout Occurred"
      525 -> "SSL Handshake Failed"
      else -> "HTTP $code"
    }

  fun buildWranglerPreviewJson(
    scriptName: String,
    entryModule: String,
    compatDate: String,
    compatFlags: List<String> = emptyList(),
    bindings: List<CfWorkerBinding>,
    schedules: List<CfCronSchedule> = emptyList(),
    workersDev: Boolean,
  ): String =
    buildString {
      appendLine("// EdgeCode Wrangler configuration (editable & two-way synced)")
      appendLine("{")
      appendLine("  \"name\": ${JSONObject.quote(scriptName)},")
      appendLine("  \"main\": ${JSONObject.quote(entryModule)},")
      appendLine("  \"compatibility_date\": ${JSONObject.quote(compatDate)},")
      if (compatFlags.isNotEmpty()) {
        val flags = compatFlags.joinToString(", ") { JSONObject.quote(it) }
        appendLine("  \"compatibility_flags\": [$flags],")
      }
      appendLine("  \"workers_dev\": $workersDev,")
      val vars = bindings.filter { it.type == "plain_text" }
      if (vars.isNotEmpty()) {
        appendLine("  \"vars\": {")
        vars.forEachIndexed { idx, v ->
          val comma = if (idx < vars.lastIndex) "," else ""
          appendLine("    ${JSONObject.quote(v.name)}: ${JSONObject.quote(v.text.orEmpty())}$comma")
        }
        appendLine("  },")
      }
      val kvs = bindings.filter { it.type == "kv_namespace" }
      if (kvs.isNotEmpty()) {
        appendLine("  \"kv_namespaces\": [")
        kvs.forEachIndexed { idx, kv ->
          val comma = if (idx < kvs.lastIndex) "," else ""
          appendLine("    { \"binding\": ${JSONObject.quote(kv.name)}, \"id\": ${JSONObject.quote(kv.namespaceId.orEmpty())} }$comma")
        }
        appendLine("  ],")
      }
      val d1s = bindings.filter { it.type == "d1" }
      if (d1s.isNotEmpty()) {
        appendLine("  \"d1_databases\": [")
        d1s.forEachIndexed { idx, d1 ->
          val comma = if (idx < d1s.lastIndex) "," else ""
          appendLine("    { \"binding\": ${JSONObject.quote(d1.name)}, \"database_id\": ${JSONObject.quote(d1.databaseId.orEmpty())} }$comma")
        }
        appendLine("  ],")
      }
      val r2s = bindings.filter { it.type == "r2_bucket" }
      if (r2s.isNotEmpty()) {
        appendLine("  \"r2_buckets\": [")
        r2s.forEachIndexed { idx, r2 ->
          val comma = if (idx < r2s.lastIndex) "," else ""
          appendLine("    { \"binding\": ${JSONObject.quote(r2.name)}, \"bucket_name\": ${JSONObject.quote(r2.bucketName.orEmpty())} }$comma")
        }
        appendLine("  ],")
      }
      val services = bindings.filter { it.type == "service" }
      if (services.isNotEmpty()) {
        appendLine("  \"services\": [")
        services.forEachIndexed { idx, svc ->
          val comma = if (idx < services.lastIndex) "," else ""
          val envPart = svc.environment?.let { ", \"environment\": ${JSONObject.quote(it)}" }.orEmpty()
          appendLine("    { \"binding\": ${JSONObject.quote(svc.name)}, \"service\": ${JSONObject.quote(svc.service.orEmpty())}$envPart }$comma")
        }
        appendLine("  ],")
      }
      val ai = bindings.firstOrNull { it.type == "ai" }
      if (ai != null) {
        appendLine("  \"ai\": { \"binding\": ${JSONObject.quote(ai.name)} },")
      }
      val dos = bindings.filter { it.type == "durable_object_namespace" }
      if (dos.isNotEmpty()) {
        appendLine("  \"durable_objects\": {")
        appendLine("    \"bindings\": [")
        dos.forEachIndexed { idx, d ->
          val comma = if (idx < dos.lastIndex) "," else ""
          val scriptPart = d.scriptName?.let { ", \"script_name\": ${JSONObject.quote(it)}" }.orEmpty()
          appendLine("      { \"name\": ${JSONObject.quote(d.name)}, \"class_name\": ${JSONObject.quote(d.className.orEmpty())}$scriptPart }$comma")
        }
        appendLine("    ]")
        appendLine("  },")
      }
      val queues = bindings.filter { it.type == "queue" }
      if (queues.isNotEmpty()) {
        appendLine("  \"queues\": {")
        appendLine("    \"producers\": [")
        queues.forEachIndexed { idx, q ->
          val comma = if (idx < queues.lastIndex) "," else ""
          appendLine("      { \"binding\": ${JSONObject.quote(q.name)}, \"queue\": ${JSONObject.quote(q.queueName.orEmpty())} }$comma")
        }
        appendLine("    ]")
        appendLine("  },")
      }
      if (schedules.isNotEmpty()) {
        val crons = schedules.joinToString(", ") { JSONObject.quote(it.cron) }
        appendLine("  \"triggers\": { \"crons\": [$crons] },")
      }
      appendLine("  \"secrets_count\": ${bindings.count { it.type == "secret_text" }}")
      append("}")
    }

  private fun <T> extractCfError(response: Response<CfResponse<T>>): String? {
    val bodyErrors = response.body()?.errors.orEmpty()
    if (bodyErrors.isNotEmpty()) {
      return bodyErrors.joinToString("; ") { "${it.code}: ${it.message}" }
    }
    val errRaw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
    if (errRaw.isNotBlank()) {
      val regex = Regex(""""message"\s*:\s*"([^"]+)"""")
      val msg = regex.find(errRaw)?.groups?.get(1)?.value
      if (!msg.isNullOrBlank()) return msg
      return "HTTP ${response.code()}: ${errRaw.take(180)}"
    }
    return null
  }
}
