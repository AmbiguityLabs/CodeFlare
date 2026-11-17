package com.example

import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.RamWorkspaceDatabase
import com.example.data.remote.CfCronSchedule
import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.CloudflareApiService
import com.example.data.remote.CloudflareClientFactory
import com.example.data.remote.CloudflareNamingUtils
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import com.example.data.repository.CloudflareRepository
import com.example.data.security.AuthPreferencesStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkerRenameAndSubdomainTest {

  private fun createRecordedRepo(
    recordedCalls: MutableList<Pair<String, String>>,
    subdomainHandler: (method: String, body: String) -> Pair<Int, String> = { _, _ ->
      200 to """{"success":true,"errors":[],"messages":[],"result":{"subdomain":"team-edge"}}"""
    },
  ): CloudflareRepository {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val prefs = AuthPreferencesStore(context)
    val factory = CloudflareClientFactory(prefs)
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val interceptor = Interceptor { chain ->
      val req = chain.request()
      val path = req.url.encodedPath
      val bodyStr =
        req.body?.let { rb ->
          val buf = Buffer()
          rb.writeTo(buf)
          buf.readUtf8()
        }.orEmpty()
      recordedCalls.add("${req.method} $path" to bodyStr)

      val (code, json) =
        when {
          path.endsWith("/workers/subdomain") -> subdomainHandler(req.method, bodyStr)
          path.contains("/workers/scripts/") && req.method == "PUT" && path.endsWith("/secrets") ->
            200 to """{"success":true,"errors":[],"messages":[],"result":{"name":"API_KEY","type":"secret_text"}}"""
          path.contains("/workers/scripts/") && req.method == "PUT" && path.endsWith("/schedules") ->
            200 to """{"success":true,"errors":[],"messages":[],"result":{"schedules":[{"cron":"*/10 * * * *"}]}}"""
          path.contains("/workers/scripts/") && req.method == "PUT" -> {
            val scriptId = path.substringAfter("/workers/scripts/").substringBefore("/")
            200 to """{"success":true,"errors":[],"messages":[],"result":{"id":"$scriptId"}}"""
          }
          path.endsWith("/settings") && req.method == "PATCH" ->
            200 to """{"success":true,"errors":[],"messages":[],"result":{"compatibility_date":"2024-11-01","compatibility_flags":[],"bindings":[]}}"""
          path.endsWith("/subdomain") && req.method == "POST" ->
            200 to """{"success":true,"errors":[],"messages":[],"result":{"enabled":true,"previews_enabled":true}}"""
          path.contains("/workers/scripts/") && req.method == "DELETE" ->
            200 to """{"success":true,"errors":[],"messages":[],"result":null}"""
          else ->
            404 to """{"success":false,"errors":[{"code":10000,"message":"Unexpected endpoint $path"}]}"""
        }

      Response.Builder()
        .request(req)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code in 200..299) "OK" else "Error")
        .body(json.toResponseBody("application/json".toMediaType()))
        .build()
    }

    val okHttp = OkHttpClient.Builder().addInterceptor(interceptor).build()
    val api =
      Retrofit.Builder()
        .baseUrl("https://api.cloudflare.com/client/v4/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(CloudflareApiService::class.java)

    return CloudflareRepository(prefs, factory, apiServiceOverride = api)
  }

  @Test
  fun namingUtilsSanitizesAndValidatesWorkerNamesAndSubdomains() {
    assertEquals("my-edge-worker", CloudflareNamingUtils.sanitizeWorkerName("  My_Edge Worker! "))
    assertEquals("api-v2", CloudflareNamingUtils.sanitizeWorkerName("--API--V2--"))
    assertTrue(CloudflareNamingUtils.isValidWorkerName("my-edge-worker"))
    assertFalse(CloudflareNamingUtils.isValidWorkerName(""))
    assertFalse(CloudflareNamingUtils.isValidWorkerName("invalid_underscore"))

    assertEquals("my-team", CloudflareNamingUtils.sanitizeSubdomain("https://My-Team.workers.dev/"))
    assertEquals("edge-dev", CloudflareNamingUtils.sanitizeSubdomain("edge-dev.workers.dev"))
    assertTrue(CloudflareNamingUtils.isValidSubdomain("edge-dev"))
    assertFalse(CloudflareNamingUtils.isValidSubdomain(""))
  }

  @Test
  fun updateAccountSubdomainCallsPutEndpointAndHandlesErrors() = runBlocking {
    val calls = mutableListOf<Pair<String, String>>()
    val repo = createRecordedRepo(calls)

    val result = repo.updateAccountSubdomain("acc-123", "https://Team-Edge.workers.dev")
    assertTrue(result.isSuccess)
    assertEquals("team-edge", result.getOrNull())
    val putCall = calls.firstOrNull { it.first == "PUT /client/v4/accounts/acc-123/workers/subdomain" }
    assertNotNull(putCall)
    assertTrue(putCall!!.second.contains(""""subdomain":"team-edge""""))

    val failingRepo =
      createRecordedRepo(mutableListOf()) { _, _ ->
        409 to """{"success":false,"errors":[{"code":10064,"message":"Subdomain is already taken"}]}"""
      }
    val failResult = failingRepo.updateAccountSubdomain("acc-123", "taken-sub")
    assertTrue(failResult.isFailure)
    assertTrue(failResult.exceptionOrNull()?.message?.contains("already taken") == true)
  }

  @Test
  fun renameRemoteWorkerDeploysNewScriptSyncsConfigAndDeletesOldScript() = runBlocking {
    val calls = mutableListOf<Pair<String, String>>()
    val repo = createRecordedRepo(calls)

    val ws =
      WorkerWorkspace(
        scriptName = "new-worker-name",
        isEsModuleFormat = true,
        entryModulePath = "src/index.js",
        compatibilityDate = "2024-11-01",
        compatibilityFlags = listOf("nodejs_compat"),
        files =
          listOf(
            WorkerFile(
              path = "src/index.js",
              content = "export default { fetch() { return new Response('ok'); } };",
              originalContent = "",
              contentType = "application/javascript+module",
              isEntryPoint = true,
            )
          ),
        bindings =
          listOf(
            CfWorkerBinding(name = "ENV_MODE", type = "plain_text", text = "prod"),
            CfWorkerBinding(name = "STAGED_KEY", type = "secret_text"),
            CfWorkerBinding(name = "OLD_SECRET", type = "secret_text"),
          ),
        routes = emptyList(),
        schedules = listOf(CfCronSchedule(cron = "*/10 * * * *")),
        workersDevEnabled = true,
        accountSubdomain = "team-edge",
      )

    val outcomeRes =
      repo.renameRemoteWorker(
        accountId = "acc-123",
        oldScriptName = "old-worker-name",
        renamedWorkspace = ws,
        deleteOldScript = true,
        stagedSecrets = mapOf("STAGED_KEY" to "super-secret-value"),
      )

    assertTrue(outcomeRes.isSuccess)
    val outcome = outcomeRes.getOrThrow()
    assertEquals("new-worker-name", outcome.script.id)
    assertTrue(outcome.deletedOldRemote)
    assertTrue(outcome.warningMessage?.contains("OLD_SECRET") == true)
    assertFalse(outcome.warningMessage?.contains("STAGED_KEY") == true)

    assertTrue(calls.any { it.first == "PUT /client/v4/accounts/acc-123/workers/scripts/new-worker-name" })
    assertTrue(calls.any { it.first == "PATCH /client/v4/accounts/acc-123/workers/scripts/new-worker-name/settings" })
    assertTrue(calls.any { it.first == "POST /client/v4/accounts/acc-123/workers/scripts/new-worker-name/subdomain" })
    assertTrue(calls.any { it.first == "PUT /client/v4/accounts/acc-123/workers/scripts/new-worker-name/schedules" })
    assertTrue(calls.any { it.first == "PUT /client/v4/accounts/acc-123/workers/scripts/new-worker-name/secrets" })
    assertTrue(calls.any { it.first == "DELETE /client/v4/accounts/acc-123/workers/scripts/old-worker-name" })
  }

  @Test
  fun renameRemoteWorkerKeepsOldScriptWhenDeleteFlagIsFalse() = runBlocking {
    val calls = mutableListOf<Pair<String, String>>()
    val repo = createRecordedRepo(calls)

    val ws =
      WorkerWorkspace(
        scriptName = "cloned-worker",
        isEsModuleFormat = true,
        entryModulePath = "index.js",
        compatibilityDate = "2024-11-01",
        files =
          listOf(
            WorkerFile(
              path = "index.js",
              content = "export default { fetch() { return new Response('v2'); } };",
              originalContent = "",
              contentType = "application/javascript+module",
              isEntryPoint = true,
            )
          ),
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = true,
        accountSubdomain = "team-edge",
      )

    val outcome =
      repo.renameRemoteWorker(
        accountId = "acc-123",
        oldScriptName = "source-worker",
        renamedWorkspace = ws,
        deleteOldScript = false,
      ).getOrThrow()

    assertFalse(outcome.deletedOldRemote)
    assertFalse(calls.any { it.first.startsWith("DELETE ") })
  }

  @Test
  fun ramDbRenamesWorkspaceKeyPreservingUndoAndUpdatesAccountSubdomains() {
    val db = RamWorkspaceDatabase()
    val oldKey = db.workspaceKey("acc-1", "old-api")
    val newKey = db.workspaceKey("acc-1", "new-api")

    val ws =
      WorkerWorkspace(
        scriptName = "old-api",
        isEsModuleFormat = true,
        entryModulePath = "index.js",
        compatibilityDate = "2024-11-01",
        files =
          listOf(
            WorkerFile(
              path = "index.js",
              content = "export default {}",
              originalContent = "export default {}",
              contentType = "application/javascript+module",
              isEntryPoint = true,
            )
          ),
        bindings = emptyList(),
        routes = emptyList(),
        workersDevEnabled = true,
        accountSubdomain = "initial-sub",
      )

    db.importWorkspace(oldKey, ws)
    db.recordUndoSnapshot(oldKey, "index.js", TextFieldValue("before"))

    val renamedWs = ws.copy(scriptName = "new-api")
    val renamedRec =
      db.renameWorkspaceKey(
        oldKey = oldKey,
        newKey = newKey,
        updatedWorkspace = renamedWs,
        updatedFieldValues = mapOf("index.js" to TextFieldValue("after")),
      )

    assertNotNull(renamedRec)
    assertNull(db.getWorkspace(oldKey))
    assertEquals("new-api", db.getWorkspace(newKey)?.workingWorkspace?.scriptName)
    assertEquals("before", db.undo(newKey, "index.js", TextFieldValue("after"))?.text)

    val updatedList = db.updateAllSubdomainsForAccount("acc-1", "custom-edge")
    assertEquals(1, updatedList.size)
    assertEquals("https://new-api.custom-edge.workers.dev", db.getWorkspace(newKey)?.workingWorkspace?.workersDevUrl)
  }
}
