package com.example.data.remote

import android.util.Base64
import java.net.URLEncoder
import java.util.UUID

enum class ApiAuthType(val label: String) {
  NONE("None"),
  BEARER("Bearer"),
  BASIC("Basic"),
  API_KEY("API Key"),
}

enum class ApiBodyType(val label: String) {
  NONE("None"),
  JSON("JSON"),
  FORM_URLENCODED("Form"),
  RAW("Raw"),
}

data class ApiKeyValueRow(
  val id: String = UUID.randomUUID().toString().take(8),
  val key: String = "",
  val value: String = "",
  val enabled: Boolean = true,
)

data class ApiEnvVar(
  val key: String = "",
  val value: String = "",
  val enabled: Boolean = true,
)

data class ApiTesterRequest(
  val id: String = "req_active",
  val name: String = "Edge API Request",
  val method: String = "GET",
  val url: String = "{{baseUrl}}/",
  val params: List<ApiKeyValueRow> = emptyList(),
  val headers: List<ApiKeyValueRow> = listOf(ApiKeyValueRow(id = "h_accept", key = "Accept", value = "application/json")),
  val authType: ApiAuthType = ApiAuthType.NONE,
  val bearerToken: String = "",
  val basicUser: String = "",
  val basicPass: String = "",
  val apiKeyName: String = "X-API-Key",
  val apiKeyValue: String = "",
  val bodyType: ApiBodyType = ApiBodyType.NONE,
  val rawBody: String = "{\n  \"ping\": true\n}",
  val formRows: List<ApiKeyValueRow> = emptyList(),
)

data class ApiTesterHistoryItem(
  val id: String,
  val timestamp: String,
  val request: ApiTesterRequest,
  val resolvedUrl: String,
  val statusCode: Int,
  val durationMs: Long,
)

object ApiRequestUtils {
  private val varPattern = Regex("""\{\{([A-Za-z_][A-Za-z0-9_]*)\}\}""")

  fun substituteVariables(
    input: String,
    envVars: List<ApiEnvVar>,
    workerBaseUrl: String?,
  ): String {
    if (!input.contains("{{")) return input
    val activeMap = linkedMapOf<String, String>()
    if (!workerBaseUrl.isNullOrBlank()) {
      activeMap["baseUrl"] = workerBaseUrl.trimEnd('/')
    }
    envVars.filter { it.enabled && it.key.isNotBlank() }.forEach {
      activeMap[it.key.trim()] = it.value
    }
    return varPattern.replace(input) { match ->
      val key = match.groupValues[1]
      activeMap[key] ?: match.value
    }
  }

  fun resolveUrl(
    request: ApiTesterRequest,
    envVars: List<ApiEnvVar>,
    workerBaseUrl: String?,
  ): Result<String> {
    var expanded = substituteVariables(request.url.trim(), envVars, workerBaseUrl)
    if (expanded.isEmpty()) expanded = "/"
    if (!expanded.startsWith("http://", ignoreCase = true) && !expanded.startsWith("https://", ignoreCase = true)) {
      val base =
        envVars.firstOrNull { it.enabled && it.key.equals("baseUrl", ignoreCase = true) && it.value.isNotBlank() }?.value
          ?: workerBaseUrl
      if (base.isNullOrBlank()) {
        return Result.failure(
          IllegalArgumentException("Relative URL '$expanded' requires an active Worker URL or {{baseUrl}} variable."),
        )
      }
      val cleanBase = base.trimEnd('/')
      val cleanPath = if (expanded.startsWith("/")) expanded else "/$expanded"
      expanded = "$cleanBase$cleanPath"
    }

    val enabledParams = request.params.filter { it.enabled && it.key.isNotBlank() }
    if (enabledParams.isEmpty()) return Result.success(expanded)

    val queryString =
      enabledParams.joinToString("&") { p ->
        val k = URLEncoder.encode(substituteVariables(p.key.trim(), envVars, workerBaseUrl), "UTF-8")
        val v = URLEncoder.encode(substituteVariables(p.value, envVars, workerBaseUrl), "UTF-8")
        "$k=$v"
      }
    val sep = if (expanded.contains("?")) "&" else "?"
    return Result.success("$expanded$sep$queryString")
  }

  fun buildHeadersAndBody(
    request: ApiTesterRequest,
    envVars: List<ApiEnvVar>,
    workerBaseUrl: String?,
  ): Pair<Map<String, String>, String> {
    val headers = linkedMapOf<String, String>()
    request.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { h ->
      headers[h.key.trim()] = substituteVariables(h.value, envVars, workerBaseUrl)
    }

    when (request.authType) {
      ApiAuthType.NONE -> Unit
      ApiAuthType.BEARER -> {
        val tok = substituteVariables(request.bearerToken.trim(), envVars, workerBaseUrl)
        if (tok.isNotEmpty()) headers["Authorization"] = "Bearer $tok"
      }
      ApiAuthType.BASIC -> {
        val u = substituteVariables(request.basicUser, envVars, workerBaseUrl)
        val p = substituteVariables(request.basicPass, envVars, workerBaseUrl)
        val encoded = Base64.encodeToString("$u:$p".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        headers["Authorization"] = "Basic $encoded"
      }
      ApiAuthType.API_KEY -> {
        val k = request.apiKeyName.trim()
        val v = substituteVariables(request.apiKeyValue, envVars, workerBaseUrl)
        if (k.isNotEmpty() && v.isNotEmpty()) headers[k] = v
      }
    }

    val bodyText =
      when (request.bodyType) {
        ApiBodyType.NONE -> ""
        ApiBodyType.JSON -> {
          if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
            headers["Content-Type"] = "application/json"
          }
          substituteVariables(request.rawBody, envVars, workerBaseUrl)
        }
        ApiBodyType.FORM_URLENCODED -> {
          if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
            headers["Content-Type"] = "application/x-www-form-urlencoded"
          }
          request.formRows
            .filter { it.enabled && it.key.isNotBlank() }
            .joinToString("&") { row ->
              val k = URLEncoder.encode(substituteVariables(row.key.trim(), envVars, workerBaseUrl), "UTF-8")
              val v = URLEncoder.encode(substituteVariables(row.value, envVars, workerBaseUrl), "UTF-8")
              "$k=$v"
            }
        }
        ApiBodyType.RAW -> {
          if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
            headers["Content-Type"] = "text/plain; charset=utf-8"
          }
          substituteVariables(request.rawBody, envVars, workerBaseUrl)
        }
      }

    return headers to bodyText
  }

  fun toCurlSnippet(
    request: ApiTesterRequest,
    envVars: List<ApiEnvVar>,
    workerBaseUrl: String?,
  ): String {
    val url = resolveUrl(request, envVars, workerBaseUrl).getOrElse { request.url }
    val (headers, body) = buildHeadersAndBody(request, envVars, workerBaseUrl)
    return buildString {
      append("curl -i -X ${request.method.uppercase()}")
      headers.forEach { (k, v) ->
        append(" -H \"${k.replace("\"", "\\\"")}: ${v.replace("\"", "\\\"")}\"")
      }
      if (body.isNotEmpty() && request.method.uppercase() in setOf("POST", "PUT", "PATCH", "DELETE")) {
        append(" -d '${body.replace("'", "\\'")}'")
      }
      append(" \"$url\"")
    }
  }

  fun toFetchSnippet(
    request: ApiTesterRequest,
    envVars: List<ApiEnvVar>,
    workerBaseUrl: String?,
  ): String {
    val url = resolveUrl(request, envVars, workerBaseUrl).getOrElse { request.url }
    val (headers, body) = buildHeadersAndBody(request, envVars, workerBaseUrl)
    return buildString {
      appendLine("const response = await fetch(\"$url\", {")
      appendLine("  method: \"${request.method.uppercase()}\",")
      if (headers.isNotEmpty()) {
        appendLine("  headers: {")
        headers.entries.forEachIndexed { idx, (k, v) ->
          val comma = if (idx < headers.size - 1) "," else ""
          appendLine("    \"$k\": \"${v.replace("\"", "\\\"")}\"$comma")
        }
        appendLine("  },")
      }
      if (body.isNotEmpty() && request.method.uppercase() in setOf("POST", "PUT", "PATCH", "DELETE")) {
        appendLine("  body: ${org.json.JSONObject.quote(body)},")
      }
      appendLine("});")
      append("const text = await response.text();")
    }
  }
}

object GitIgnoreMatcher {
  fun parsePatterns(gitignoreContent: String?): List<String> {
    if (gitignoreContent.isNullOrBlank()) return emptyList()
    return gitignoreContent
      .lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") }
  }

  fun isIgnored(path: String, patterns: List<String>): Boolean {
    if (patterns.isEmpty()) return false
    val normPath = path.trim().trimStart('/').replace('\\', '/')
    if (normPath == ".gitignore") return false

    var ignored = false
    for (rawPattern in patterns) {
      val negated = rawPattern.startsWith("!")
      val patternBody = (if (negated) rawPattern.drop(1) else rawPattern).trim()
      if (patternBody.isEmpty()) continue
      if (matchesPattern(normPath, patternBody)) {
        ignored = !negated
      }
    }
    return ignored
  }

  private fun matchesPattern(path: String, rawPattern: String): Boolean {
    val dirOnly = rawPattern.endsWith("/")
    val anchored = rawPattern.startsWith("/")
    val cleanPat = rawPattern.trim('/')
    if (cleanPat.isEmpty()) return false

    if (dirOnly) {
      val segments = path.split('/')
      val parentSegments = if (segments.size > 1) segments.dropLast(1) else emptyList()
      if (anchored) {
        return path.startsWith("$cleanPat/")
      }
      val regex = globToRegex(cleanPat)
      return parentSegments.any { regex.matches(it) } || path.startsWith("$cleanPat/")
    }

    if (!cleanPat.contains('/')) {
      val regex = globToRegex(cleanPat)
      val fileName = path.substringAfterLast('/')
      return regex.matches(fileName) || (!anchored && path.split('/').any { regex.matches(it) })
    }

    val regex = globToRegex(cleanPat)
    return regex.matches(path)
  }

  private fun globToRegex(glob: String): Regex {
    val sb = StringBuilder("^")
    var i = 0
    while (i < glob.length) {
      val c = glob[i]
      when {
        c == '*' && glob.getOrNull(i + 1) == '*' -> {
          sb.append(".*")
          i += 2
          if (glob.getOrNull(i) == '/') i++
        }
        c == '*' -> {
          sb.append("[^/]*")
          i++
        }
        c == '?' -> {
          sb.append("[^/]")
          i++
        }
        c in ".+^$()[]{}|\\" -> {
          sb.append('\\').append(c)
          i++
        }
        else -> {
          sb.append(c)
          i++
        }
      }
    }
    sb.append('$')
    return Regex(sb.toString())
  }
}
