package com.example.data.local

import android.content.Context
import com.example.data.remote.ApiAuthType
import com.example.data.remote.ApiBodyType
import com.example.data.remote.ApiEnvVar
import com.example.data.remote.ApiKeyValueRow
import com.example.data.remote.ApiTesterHistoryItem
import com.example.data.remote.ApiTesterRequest
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.WorkerFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class RestoredWorkspaceDraft(
  val workspaceKey: String,
  val files: List<WorkerFile>,
  val openTabs: List<String>,
  val activeFilePath: String?,
  val savedAtEpochMs: Long,
)

data class PersistedApiTesterState(
  val collections: List<ApiTesterRequest> = emptyList(),
  val history: List<ApiTesterHistoryItem> = emptyList(),
  val envVars: List<ApiEnvVar> = listOf(ApiEnvVar(key = "apiToken", value = "")),
)

class DraftPersistenceManager(context: Context) {
  private val stateDir = File(context.filesDir, "edgecode_state").apply { mkdirs() }
  private val draftsDir = File(stateDir, "drafts").apply { mkdirs() }
  private val apiTesterFile = File(stateDir, "api_tester.json")

  private fun draftFileForKey(workspaceKey: String): File {
    val safe = workspaceKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return File(draftsDir, "$safe.json")
  }

  fun saveWorkspaceDraft(
    workspaceKey: String,
    files: List<WorkerFile>,
    openTabs: List<String>,
    activeFilePath: String?,
  ) {
    runCatching {
      val dirtyOrAll = files.filterNot { it.isVirtualReadOnly }
      if (dirtyOrAll.isEmpty()) return
      val root = JSONObject()
      root.put("workspaceKey", workspaceKey)
      root.put("savedAt", System.currentTimeMillis())
      root.put("activeFilePath", activeFilePath ?: "")
      root.put("openTabs", JSONArray(openTabs))

      val filesArr = JSONArray()
      dirtyOrAll.forEach { wf ->
        val obj = JSONObject()
        obj.put("path", wf.path)
        obj.put("content", wf.content)
        obj.put("originalContent", wf.originalContent)
        obj.put("isEntryPoint", wf.isEntryPoint)
        filesArr.put(obj)
      }
      root.put("files", filesArr)
      draftFileForKey(workspaceKey).writeText(root.toString(), Charsets.UTF_8)
    }
  }

  fun loadWorkspaceDraft(workspaceKey: String): RestoredWorkspaceDraft? {
    return runCatching {
      val file = draftFileForKey(workspaceKey)
      if (!file.exists()) return null
      val root = JSONObject(file.readText(Charsets.UTF_8))
      val savedAt = root.optLong("savedAt", 0L)
      val activePath = root.optString("activeFilePath").takeIf { it.isNotBlank() }
      val tabsArr = root.optJSONArray("openTabs")
      val tabs = mutableListOf<String>()
      if (tabsArr != null) {
        for (i in 0 until tabsArr.length()) {
          tabsArr.optString(i).takeIf { it.isNotBlank() }?.let { tabs.add(it) }
        }
      }
      val filesArr = root.optJSONArray("files") ?: return null
      val files = mutableListOf<WorkerFile>()
      for (i in 0 until filesArr.length()) {
        val obj = filesArr.optJSONObject(i) ?: continue
        val path = obj.optString("path").takeIf { it.isNotBlank() } ?: continue
        val content = obj.optString("content")
        val original = obj.optString("originalContent")
        val isEntry = obj.optBoolean("isEntryPoint", false)
        files.add(
          WorkerFile(
            path = path,
            content = content,
            originalContent = original,
            contentType = MultipartWorkerParser.inferModuleMimeType(path),
            isEntryPoint = isEntry,
          ),
        )
      }
      if (files.isEmpty()) null
      else RestoredWorkspaceDraft(workspaceKey, files, tabs, activePath, savedAt)
    }.getOrNull()
  }

  fun clearWorkspaceDraft(workspaceKey: String) {
    runCatching {
      val f = draftFileForKey(workspaceKey)
      if (f.exists()) f.delete()
    }
  }

  fun saveApiTesterState(state: PersistedApiTesterState) {
    runCatching {
      val root = JSONObject()
      val colArr = JSONArray()
      state.collections.forEach { colArr.put(serializeRequest(it)) }
      root.put("collections", colArr)

      val histArr = JSONArray()
      state.history.take(30).forEach { h ->
        val obj = JSONObject()
        obj.put("id", h.id)
        obj.put("timestamp", h.timestamp)
        obj.put("resolvedUrl", h.resolvedUrl)
        obj.put("statusCode", h.statusCode)
        obj.put("durationMs", h.durationMs)
        obj.put("request", serializeRequest(h.request))
        histArr.put(obj)
      }
      root.put("history", histArr)

      val envArr = JSONArray()
      state.envVars.forEach { ev ->
        envArr.put(
          JSONObject().apply {
            put("key", ev.key)
            put("value", ev.value)
            put("enabled", ev.enabled)
          },
        )
      }
      root.put("envVars", envArr)
      apiTesterFile.writeText(root.toString(), Charsets.UTF_8)
    }
  }

  fun loadApiTesterState(): PersistedApiTesterState {
    return runCatching {
      if (!apiTesterFile.exists()) return PersistedApiTesterState()
      val root = JSONObject(apiTesterFile.readText(Charsets.UTF_8))

      val collections = mutableListOf<ApiTesterRequest>()
      root.optJSONArray("collections")?.let { arr ->
        for (i in 0 until arr.length()) {
          arr.optJSONObject(i)?.let { collections.add(deserializeRequest(it)) }
        }
      }

      val history = mutableListOf<ApiTesterHistoryItem>()
      root.optJSONArray("history")?.let { arr ->
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val reqObj = obj.optJSONObject("request") ?: continue
          history.add(
            ApiTesterHistoryItem(
              id = obj.optString("id"),
              timestamp = obj.optString("timestamp"),
              request = deserializeRequest(reqObj),
              resolvedUrl = obj.optString("resolvedUrl"),
              statusCode = obj.optInt("statusCode", 0),
              durationMs = obj.optLong("durationMs", 0L),
            ),
          )
        }
      }

      val envVars = mutableListOf<ApiEnvVar>()
      root.optJSONArray("envVars")?.let { arr ->
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          envVars.add(
            ApiEnvVar(
              key = obj.optString("key"),
              value = obj.optString("value"),
              enabled = obj.optBoolean("enabled", true),
            ),
          )
        }
      }

      PersistedApiTesterState(
        collections = collections,
        history = history,
        envVars = envVars.ifEmpty { listOf(ApiEnvVar(key = "apiToken", value = "")) },
      )
    }.getOrDefault(PersistedApiTesterState())
  }

  private fun serializeRequest(req: ApiTesterRequest): JSONObject {
    return JSONObject().apply {
      put("id", req.id)
      put("name", req.name)
      put("method", req.method)
      put("url", req.url)
      put("params", serializeRows(req.params))
      put("headers", serializeRows(req.headers))
      put("authType", req.authType.name)
      put("bearerToken", req.bearerToken)
      put("basicUser", req.basicUser)
      put("basicPass", req.basicPass)
      put("apiKeyName", req.apiKeyName)
      put("apiKeyValue", req.apiKeyValue)
      put("bodyType", req.bodyType.name)
      put("rawBody", req.rawBody)
      put("formRows", serializeRows(req.formRows))
    }
  }

  private fun deserializeRequest(obj: JSONObject): ApiTesterRequest {
    return ApiTesterRequest(
      id = obj.optString("id", "req"),
      name = obj.optString("name", "Saved Request"),
      method = obj.optString("method", "GET"),
      url = obj.optString("url", "/"),
      params = deserializeRows(obj.optJSONArray("params")),
      headers = deserializeRows(obj.optJSONArray("headers")),
      authType = runCatching { ApiAuthType.valueOf(obj.optString("authType", "NONE")) }.getOrDefault(ApiAuthType.NONE),
      bearerToken = obj.optString("bearerToken"),
      basicUser = obj.optString("basicUser"),
      basicPass = obj.optString("basicPass"),
      apiKeyName = obj.optString("apiKeyName", "X-API-Key"),
      apiKeyValue = obj.optString("apiKeyValue"),
      bodyType = runCatching { ApiBodyType.valueOf(obj.optString("bodyType", "NONE")) }.getOrDefault(ApiBodyType.NONE),
      rawBody = obj.optString("rawBody"),
      formRows = deserializeRows(obj.optJSONArray("formRows")),
    )
  }

  private fun serializeRows(rows: List<ApiKeyValueRow>): JSONArray {
    return JSONArray().apply {
      rows.forEach { r ->
        put(
          JSONObject().apply {
            put("id", r.id)
            put("key", r.key)
            put("value", r.value)
            put("enabled", r.enabled)
          },
        )
      }
    }
  }

  private fun deserializeRows(arr: JSONArray?): List<ApiKeyValueRow> {
    if (arr == null) return emptyList()
    val list = mutableListOf<ApiKeyValueRow>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      list.add(
        ApiKeyValueRow(
          id = obj.optString("id", "r_$i"),
          key = obj.optString("key"),
          value = obj.optString("value"),
          enabled = obj.optBoolean("enabled", true),
        ),
      )
    }
    return list
  }
}
