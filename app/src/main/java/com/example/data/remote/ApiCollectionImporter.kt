package com.example.data.remote

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

enum class ApiImportSourceFormat(val label: String) {
  POSTMAN_V2("Postman Collection"),
  OPENAPI_V3("OpenAPI 3.x"),
  SWAGGER_V2("Swagger 2.0"),
}

data class ApiImportResult(
  val sourceFormat: ApiImportSourceFormat,
  val title: String,
  val requests: List<ApiTesterRequest>,
  val extractedEnvVars: List<ApiEnvVar>,
)

object ApiCollectionImporter {
  fun parseImportJson(rawJson: String): Result<ApiImportResult> {
    val trimmed = rawJson.trim()
    if (trimmed.isEmpty()) {
      return Result.failure(IllegalArgumentException("Paste or choose a Postman or OpenAPI JSON specification."))
    }
    val root =
      runCatching { JSONObject(trimmed) }.getOrElse { err ->
        return Result.failure(IllegalArgumentException("Invalid JSON specification: ${err.localizedMessage}"))
      }

    return when {
      root.has("item") && (root.has("info") || root.has("variable")) -> parsePostmanCollection(root)
      root.has("openapi") && root.has("paths") -> parseOpenApiV3(root)
      root.has("swagger") && root.has("paths") -> parseSwaggerV2(root)
      else ->
        Result.failure(
          IllegalArgumentException(
            "Unrecognized JSON format. Supported: Postman Collection v2.0/v2.1, OpenAPI 3.x JSON, and Swagger 2.0 JSON.",
          ),
        )
    }
  }

  private fun parsePostmanCollection(root: JSONObject): Result<ApiImportResult> {
    val info = root.optJSONObject("info")
    val title = info?.optString("name")?.takeIf { it.isNotBlank() } ?: "Postman Collection"
    val collectionAuth = root.optJSONObject("auth")
    val requests = mutableListOf<ApiTesterRequest>()

    fun walkItems(arr: JSONArray?, folderPrefix: String) {
      if (arr == null) return
      for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val itemName = item.optString("name").ifBlank { "Request ${requests.size + 1}" }
        val displayName = if (folderPrefix.isBlank()) itemName else "$folderPrefix / $itemName"
        val nested = item.optJSONArray("item")
        if (nested != null) {
          walkItems(nested, displayName)
          continue
        }
        val reqObj = item.optJSONObject("request") ?: continue
        requests.add(parsePostmanRequest(displayName, reqObj, collectionAuth))
      }
    }

    walkItems(root.optJSONArray("item"), "")

    val envVars = mutableListOf<ApiEnvVar>()
    root.optJSONArray("variable")?.let { varsArr ->
      for (i in 0 until varsArr.length()) {
        val v = varsArr.optJSONObject(i) ?: continue
        val key = v.optString("key").trim()
        if (key.isBlank()) continue
        val enabled = !v.optBoolean("disabled", false)
        envVars.add(ApiEnvVar(key = key, value = v.optString("value"), enabled = enabled))
      }
    }

    if (requests.isEmpty()) {
      return Result.failure(IllegalArgumentException("No executable requests found in Postman collection '$title'."))
    }
    return Result.success(
      ApiImportResult(
        sourceFormat = ApiImportSourceFormat.POSTMAN_V2,
        title = title,
        requests = requests,
        extractedEnvVars = envVars,
      ),
    )
  }

  private fun parsePostmanRequest(
    name: String,
    reqObj: JSONObject,
    collectionAuth: JSONObject?,
  ): ApiTesterRequest {
    val method = reqObj.optString("method", "GET").uppercase().ifBlank { "GET" }

    // URL + query params
    val params = mutableListOf<ApiKeyValueRow>()
    val urlRaw: String =
      when (val urlVal = reqObj.opt("url")) {
        is String -> urlVal
        is JSONObject -> {
          val raw = urlVal.optString("raw")
          urlVal.optJSONArray("query")?.let { qArr ->
            for (i in 0 until qArr.length()) {
              val q = qArr.optJSONObject(i) ?: continue
              val k = q.optString("key")
              if (k.isNotBlank()) {
                params.add(
                  ApiKeyValueRow(
                    key = k,
                    value = q.optString("value"),
                    enabled = !q.optBoolean("disabled", false),
                  ),
                )
              }
            }
          }
          if (raw.isNotBlank()) {
            // Strip query portion from raw if query params were parsed cleanly so we don't duplicate
            if (params.isNotEmpty() && raw.contains("?")) raw.substringBefore("?") else raw
          } else {
            val protocol = urlVal.optString("protocol").takeIf { it.isNotBlank() }?.let { "$it://" }.orEmpty()
            val host = joinJsonStringArray(urlVal.opt("host"), ".")
            val path = joinJsonStringArray(urlVal.opt("path"), "/").let {
              if (it.isBlank()) "" else if (it.startsWith("/")) it else "/$it"
            }
            val base = "$protocol$host$path".ifBlank { "{{baseUrl}}/" }
            base
          }
        }
        else -> "{{baseUrl}}/"
      }

    // Headers
    val headers = mutableListOf<ApiKeyValueRow>()
    reqObj.optJSONArray("header")?.let { hArr ->
      for (i in 0 until hArr.length()) {
        val h = hArr.optJSONObject(i) ?: continue
        val k = h.optString("key")
        if (k.isNotBlank()) {
          headers.add(
            ApiKeyValueRow(
              key = k,
              value = h.optString("value"),
              enabled = !h.optBoolean("disabled", false),
            ),
          )
        }
      }
    }

    // Auth (request-level overrides collection-level unless null/noauth)
    val authObj = reqObj.optJSONObject("auth") ?: collectionAuth
    var authType = ApiAuthType.NONE
    var bearerToken = ""
    var basicUser = ""
    var basicPass = ""
    var apiKeyName = "X-API-Key"
    var apiKeyValue = ""

    when (authObj?.optString("type")?.lowercase()) {
      "bearer" -> {
        authType = ApiAuthType.BEARER
        bearerToken = findPostmanAuthAttr(authObj.opt("bearer"), "token")
      }
      "basic" -> {
        authType = ApiAuthType.BASIC
        val b = authObj.opt("basic")
        basicUser = findPostmanAuthAttr(b, "username")
        basicPass = findPostmanAuthAttr(b, "password")
      }
      "apikey" -> {
        authType = ApiAuthType.API_KEY
        val a = authObj.opt("apikey")
        apiKeyName = findPostmanAuthAttr(a, "key").ifBlank { "X-API-Key" }
        apiKeyValue = findPostmanAuthAttr(a, "value")
      }
    }

    // Body
    var bodyType = ApiBodyType.NONE
    var rawBody = ""
    val formRows = mutableListOf<ApiKeyValueRow>()
    reqObj.optJSONObject("body")?.let { bObj ->
      when (bObj.optString("mode").lowercase()) {
        "raw" -> {
          rawBody = bObj.optString("raw")
          val lang = bObj.optJSONObject("options")?.optJSONObject("raw")?.optString("language").orEmpty()
          val hasJsonHeader =
            headers.any { it.key.equals("Content-Type", true) && it.value.contains("json", true) }
          bodyType =
            if (lang.equals("json", true) || hasJsonHeader || rawBody.trimStart().startsWith("{") || rawBody.trimStart().startsWith("[")) {
              ApiBodyType.JSON
            } else {
              ApiBodyType.RAW
            }
        }
        "urlencoded" -> {
          bodyType = ApiBodyType.FORM_URLENCODED
          bObj.optJSONArray("urlencoded")?.let { uArr ->
            for (i in 0 until uArr.length()) {
              val f = uArr.optJSONObject(i) ?: continue
              val k = f.optString("key")
              if (k.isNotBlank()) {
                formRows.add(
                  ApiKeyValueRow(
                    key = k,
                    value = f.optString("value"),
                    enabled = !f.optBoolean("disabled", false),
                  ),
                )
              }
            }
          }
        }
        "formdata" -> {
          bodyType = ApiBodyType.FORM_URLENCODED
          bObj.optJSONArray("formdata")?.let { fArr ->
            for (i in 0 until fArr.length()) {
              val f = fArr.optJSONObject(i) ?: continue
              val k = f.optString("key")
              if (k.isNotBlank()) {
                formRows.add(
                  ApiKeyValueRow(
                    key = k,
                    value = f.optString("value"),
                    enabled = !f.optBoolean("disabled", false),
                  ),
                )
              }
            }
          }
        }
      }
    }

    return ApiTesterRequest(
      id = UUID.randomUUID().toString().take(8),
      name = name,
      method = method,
      url = urlRaw.ifBlank { "{{baseUrl}}/" },
      params = params,
      headers = headers.ifEmpty { listOf(ApiKeyValueRow(key = "Accept", value = "application/json")) },
      authType = authType,
      bearerToken = bearerToken,
      basicUser = basicUser,
      basicPass = basicPass,
      apiKeyName = apiKeyName,
      apiKeyValue = apiKeyValue,
      bodyType = bodyType,
      rawBody = rawBody,
      formRows = formRows,
      assertions = listOf(ApiAssertionRule(target = ApiAssertionTarget.STATUS_CODE, operator = ApiAssertionOperator.IS_2XX)),
    )
  }

  private fun joinJsonStringArray(value: Any?, sep: String): String {
    return when (value) {
      is String -> value
      is JSONArray -> {
        val parts = mutableListOf<String>()
        for (i in 0 until value.length()) {
          val s = value.optString(i)
          if (s.isNotBlank()) parts.add(s)
        }
        parts.joinToString(sep)
      }
      else -> ""
    }
  }

  private fun findPostmanAuthAttr(container: Any?, targetKey: String): String {
    return when (container) {
      is JSONObject -> container.optString(targetKey)
      is JSONArray -> {
        for (i in 0 until container.length()) {
          val obj = container.optJSONObject(i) ?: continue
          if (obj.optString("key").equals(targetKey, ignoreCase = true)) {
            return obj.optString("value")
          }
        }
        ""
      }
      else -> ""
    }
  }

  private fun parseOpenApiV3(root: JSONObject): Result<ApiImportResult> {
    val info = root.optJSONObject("info")
    val title = info?.optString("title")?.takeIf { it.isNotBlank() } ?: "OpenAPI Specification"
    val servers = root.optJSONArray("servers")
    val firstServerUrl = servers?.optJSONObject(0)?.optString("url")?.trim()?.trimEnd('/').orEmpty()
    val envVars = mutableListOf<ApiEnvVar>()
    if (firstServerUrl.isNotBlank() && !firstServerUrl.startsWith("/")) {
      envVars.add(ApiEnvVar(key = "baseUrl", value = firstServerUrl, enabled = true))
    }

    val schemas = root.optJSONObject("components")?.optJSONObject("schemas")
    val securitySchemes = root.optJSONObject("components")?.optJSONObject("securitySchemes")
    val pathsObj = root.optJSONObject("paths") ?: JSONObject()
    val requests = mutableListOf<ApiTesterRequest>()
    val httpMethods = listOf("get", "post", "put", "patch", "delete", "head")

    val pathKeys = pathsObj.keys()
    while (pathKeys.hasNext()) {
      val path = pathKeys.next()
      val pathItem = pathsObj.optJSONObject(path) ?: continue
      val sharedParams = pathItem.optJSONArray("parameters")
      for (methodKey in httpMethods) {
        val op = pathItem.optJSONObject(methodKey) ?: continue
        val method = methodKey.uppercase()
        val summary = op.optString("summary").ifBlank { op.optString("operationId") }.ifBlank { "$method $path" }

        val queryParams = mutableListOf<ApiKeyValueRow>()
        val headers = mutableListOf(ApiKeyValueRow(key = "Accept", value = "application/json"))
        collectOpenApiParameters(sharedParams, queryParams, headers)
        collectOpenApiParameters(op.optJSONArray("parameters"), queryParams, headers)

        var bodyType = ApiBodyType.NONE
        var rawBody = ""
        val formRows = mutableListOf<ApiKeyValueRow>()
        val content = op.optJSONObject("requestBody")?.optJSONObject("content")
        if (content != null) {
          val jsonMedia = content.optJSONObject("application/json")
          val formMedia = content.optJSONObject("application/x-www-form-urlencoded")
          val textMedia = content.optJSONObject("text/plain")
          when {
            jsonMedia != null -> {
              bodyType = ApiBodyType.JSON
              rawBody = extractExampleOrSkeleton(jsonMedia, schemas)
            }
            formMedia != null -> {
              bodyType = ApiBodyType.FORM_URLENCODED
              val props = resolveSchemaObject(formMedia.optJSONObject("schema"), schemas)?.optJSONObject("properties")
              if (props != null) {
                val pKeys = props.keys()
                while (pKeys.hasNext()) {
                  val pk = pKeys.next()
                  formRows.add(ApiKeyValueRow(key = pk, value = samplePrimitive(props.optJSONObject(pk))))
                }
              }
            }
            textMedia != null -> {
              bodyType = ApiBodyType.RAW
              rawBody = textMedia.optString("example", "sample payload")
            }
          }
        }

        val (authType, apiKeyName) = detectOpenApiAuth(op.optJSONArray("security") ?: root.optJSONArray("security"), securitySchemes)

        val normPath = if (path.startsWith("/")) path else "/$path"
        val cleanUrl = "{{baseUrl}}$normPath"
        requests.add(
          ApiTesterRequest(
            id = UUID.randomUUID().toString().take(8),
            name = summary,
            method = method,
            url = cleanUrl,
            params = queryParams,
            headers = headers,
            authType = authType,
            bearerToken = if (authType == ApiAuthType.BEARER) "{{apiToken}}" else "",
            apiKeyName = apiKeyName,
            apiKeyValue = if (authType == ApiAuthType.API_KEY) "{{apiToken}}" else "",
            bodyType = bodyType,
            rawBody = rawBody,
            formRows = formRows,
            assertions = listOf(ApiAssertionRule(target = ApiAssertionTarget.STATUS_CODE, operator = ApiAssertionOperator.IS_2XX)),
          ),
        )
      }
    }

    if (requests.isEmpty()) {
      return Result.failure(IllegalArgumentException("No HTTP operations found in OpenAPI spec '$title'."))
    }
    return Result.success(
      ApiImportResult(
        sourceFormat = ApiImportSourceFormat.OPENAPI_V3,
        title = title,
        requests = requests,
        extractedEnvVars = envVars,
      ),
    )
  }

  private fun parseSwaggerV2(root: JSONObject): Result<ApiImportResult> {
    val info = root.optJSONObject("info")
    val title = info?.optString("title")?.takeIf { it.isNotBlank() } ?: "Swagger 2.0 API"
    val host = root.optString("host").trim()
    val basePath = root.optString("basePath").trim().trimEnd('/')
    val scheme = root.optJSONArray("schemes")?.optString(0)?.takeIf { it.isNotBlank() } ?: "https"
    val envVars = mutableListOf<ApiEnvVar>()
    if (host.isNotBlank()) {
      envVars.add(ApiEnvVar(key = "baseUrl", value = "$scheme://$host$basePath", enabled = true))
    }

    val definitions = root.optJSONObject("definitions")
    val securityDefs = root.optJSONObject("securityDefinitions")
    val pathsObj = root.optJSONObject("paths") ?: JSONObject()
    val requests = mutableListOf<ApiTesterRequest>()
    val httpMethods = listOf("get", "post", "put", "patch", "delete", "head")

    val pathKeys = pathsObj.keys()
    while (pathKeys.hasNext()) {
      val path = pathKeys.next()
      val pathItem = pathsObj.optJSONObject(path) ?: continue
      for (methodKey in httpMethods) {
        val op = pathItem.optJSONObject(methodKey) ?: continue
        val method = methodKey.uppercase()
        val summary = op.optString("summary").ifBlank { op.optString("operationId") }.ifBlank { "$method $path" }
        val queryParams = mutableListOf<ApiKeyValueRow>()
        val headers = mutableListOf(ApiKeyValueRow(key = "Accept", value = "application/json"))
        var bodyType = ApiBodyType.NONE
        var rawBody = ""
        val formRows = mutableListOf<ApiKeyValueRow>()

        op.optJSONArray("parameters")?.let { pArr ->
          for (i in 0 until pArr.length()) {
            val p = pArr.optJSONObject(i) ?: continue
            val name = p.optString("name")
            if (name.isBlank()) continue
            when (p.optString("in").lowercase()) {
              "query" -> queryParams.add(ApiKeyValueRow(key = name, value = p.optString("default")))
              "header" -> headers.add(ApiKeyValueRow(key = name, value = p.optString("default")))
              "formdata" -> {
                bodyType = ApiBodyType.FORM_URLENCODED
                formRows.add(ApiKeyValueRow(key = name, value = p.optString("default")))
              }
              "body" -> {
                bodyType = ApiBodyType.JSON
                rawBody = generateSchemaJson(p.optJSONObject("schema"), definitions, depth = 0)?.let { formatAnyJson(it) } ?: "{}"
              }
            }
          }
        }

        val (authType, apiKeyName) = detectOpenApiAuth(op.optJSONArray("security") ?: root.optJSONArray("security"), securityDefs)
        val normPath = if (path.startsWith("/")) path else "/$path"
        requests.add(
          ApiTesterRequest(
            id = UUID.randomUUID().toString().take(8),
            name = summary,
            method = method,
            url = "{{baseUrl}}$normPath",
            params = queryParams,
            headers = headers,
            authType = authType,
            bearerToken = if (authType == ApiAuthType.BEARER) "{{apiToken}}" else "",
            apiKeyName = apiKeyName,
            apiKeyValue = if (authType == ApiAuthType.API_KEY) "{{apiToken}}" else "",
            bodyType = bodyType,
            rawBody = rawBody,
            formRows = formRows,
            assertions = listOf(ApiAssertionRule(target = ApiAssertionTarget.STATUS_CODE, operator = ApiAssertionOperator.IS_2XX)),
          ),
        )
      }
    }

    if (requests.isEmpty()) {
      return Result.failure(IllegalArgumentException("No HTTP operations found in Swagger 2.0 spec '$title'."))
    }
    return Result.success(
      ApiImportResult(
        sourceFormat = ApiImportSourceFormat.SWAGGER_V2,
        title = title,
        requests = requests,
        extractedEnvVars = envVars,
      ),
    )
  }

  private fun collectOpenApiParameters(
    arr: JSONArray?,
    queryOut: MutableList<ApiKeyValueRow>,
    headersOut: MutableList<ApiKeyValueRow>,
  ) {
    if (arr == null) return
    for (i in 0 until arr.length()) {
      val p = arr.optJSONObject(i) ?: continue
      val name = p.optString("name")
      if (name.isBlank()) continue
      val exampleVal =
        p.opt("example")?.toString()
          ?: p.optJSONObject("schema")?.opt("example")?.toString()
          ?: p.optJSONObject("schema")?.opt("default")?.toString()
          ?: ""
      when (p.optString("in").lowercase()) {
        "query" -> {
          if (queryOut.none { it.key == name }) {
            queryOut.add(ApiKeyValueRow(key = name, value = exampleVal))
          }
        }
        "header" -> {
          if (headersOut.none { it.key.equals(name, ignoreCase = true) }) {
            headersOut.add(ApiKeyValueRow(key = name, value = exampleVal))
          }
        }
      }
    }
  }

  private fun detectOpenApiAuth(
    securityArr: JSONArray?,
    schemes: JSONObject?,
  ): Pair<ApiAuthType, String> {
    if (securityArr == null || schemes == null || securityArr.length() == 0) {
      return ApiAuthType.NONE to "X-API-Key"
    }
    val firstReq = securityArr.optJSONObject(0) ?: return ApiAuthType.NONE to "X-API-Key"
    val key = firstReq.keys().asSequence().firstOrNull() ?: return ApiAuthType.NONE to "X-API-Key"
    val schemeObj = schemes.optJSONObject(key) ?: return ApiAuthType.NONE to "X-API-Key"
    val type = schemeObj.optString("type").lowercase()
    return when {
      type == "http" && schemeObj.optString("scheme").equals("bearer", true) -> ApiAuthType.BEARER to "X-API-Key"
      type == "http" && schemeObj.optString("scheme").equals("basic", true) -> ApiAuthType.BASIC to "X-API-Key"
      type == "apikey" -> ApiAuthType.API_KEY to schemeObj.optString("name").ifBlank { "X-API-Key" }
      type == "oauth2" -> ApiAuthType.BEARER to "X-API-Key"
      else -> ApiAuthType.NONE to "X-API-Key"
    }
  }

  private fun extractExampleOrSkeleton(
    mediaObj: JSONObject,
    schemas: JSONObject?,
  ): String {
    mediaObj.opt("example")?.let { ex -> return formatAnyJson(ex) }
    mediaObj.optJSONObject("examples")?.let { examplesObj ->
      val firstKey = examplesObj.keys().asSequence().firstOrNull()
      if (firstKey != null) {
        examplesObj.optJSONObject(firstKey)?.opt("value")?.let { v -> return formatAnyJson(v) }
      }
    }
    val generated = generateSchemaJson(mediaObj.optJSONObject("schema"), schemas, depth = 0)
    return formatAnyJson(generated ?: JSONObject())
  }

  private fun resolveSchemaObject(
    schema: JSONObject?,
    schemas: JSONObject?,
  ): JSONObject? {
    if (schema == null) return null
    val ref = schema.optString("\$ref")
    if (ref.isNotBlank() && schemas != null) {
      val refName = ref.substringAfterLast('/')
      return schemas.optJSONObject(refName) ?: schema
    }
    return schema
  }

  private fun generateSchemaJson(
    rawSchema: JSONObject?,
    schemas: JSONObject?,
    depth: Int,
  ): Any? {
    if (rawSchema == null || depth > 4) return null
    val schema = resolveSchemaObject(rawSchema, schemas) ?: return null
    schema.opt("example")?.let { return it }

    return when (schema.optString("type").lowercase()) {
      "array" -> {
        val arr = JSONArray()
        val itemSample = generateSchemaJson(schema.optJSONObject("items"), schemas, depth + 1)
        if (itemSample != null) arr.put(itemSample)
        arr
      }
      "integer", "number" -> 0
      "boolean" -> true
      "string" -> schema.optString("default").ifBlank { schema.optJSONArray("enum")?.optString(0).orEmpty().ifBlank { "string" } }
      else -> {
        val obj = JSONObject()
        val props = schema.optJSONObject("properties")
        if (props != null) {
          val keys = props.keys()
          while (keys.hasNext()) {
            val k = keys.next()
            val propObj = props.optJSONObject(k)
            obj.put(k, generateSchemaJson(propObj, schemas, depth + 1) ?: samplePrimitive(propObj))
          }
        }
        obj
      }
    }
  }

  private fun samplePrimitive(schema: JSONObject?): String {
    if (schema == null) return ""
    schema.opt("example")?.let { return it.toString() }
    schema.opt("default")?.let { return it.toString() }
    return when (schema.optString("type").lowercase()) {
      "integer", "number" -> "0"
      "boolean" -> "true"
      else -> "string"
    }
  }

  private fun formatAnyJson(value: Any): String {
    return when (value) {
      is JSONObject -> value.toString(2)
      is JSONArray -> value.toString(2)
      is String -> {
        val t = value.trim()
        runCatching {
          val parsed = JSONTokener(t).nextValue()
          when (parsed) {
            is JSONObject -> parsed.toString(2)
            is JSONArray -> parsed.toString(2)
            else -> value
          }
        }.getOrDefault(value)
      }
      else -> value.toString()
    }
  }
}
