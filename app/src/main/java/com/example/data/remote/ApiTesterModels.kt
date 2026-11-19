package com.example.data.remote

import android.util.Base64
import java.net.URLEncoder
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

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

enum class ApiAssertionTarget(val label: String) {
  STATUS_CODE("Status"),
  RESPONSE_TIME_MS("Time (ms)"),
  JSON_PATH("JSON Path"),
  BODY_TEXT("Body"),
  HEADER("Header"),
}

enum class ApiAssertionOperator(val label: String) {
  EQUALS("=="),
  NOT_EQUALS("!="),
  CONTAINS("contains"),
  NOT_CONTAINS("!contains"),
  LESS_THAN("<"),
  GREATER_THAN(">"),
  EXISTS("exists"),
  IS_2XX("is 2xx"),
}

data class ApiAssertionRule(
  val id: String = UUID.randomUUID().toString().take(8),
  val target: ApiAssertionTarget = ApiAssertionTarget.STATUS_CODE,
  val property: String = "",
  val operator: ApiAssertionOperator = ApiAssertionOperator.IS_2XX,
  val expected: String = "",
  val enabled: Boolean = true,
)

data class ApiAssertionResult(
  val rule: ApiAssertionRule,
  val passed: Boolean,
  val actualValue: String,
  val summary: String,
)

data class ApiEnvironmentProfile(
  val id: String,
  val name: String,
  val vars: List<ApiEnvVar> = emptyList(),
)

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
  val assertions: List<ApiAssertionRule> = emptyList(),
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
  private val workersDevRootPattern = Regex("""^https?://[^/]+\.workers\.dev/?$""", RegexOption.IGNORE_CASE)
  private val workersDevFullPattern = Regex("""^(https?://[^/]+\.workers\.dev)(/.*)?$""", RegexOption.IGNORE_CASE)

  fun defaultEnvironmentProfiles(initialDevVars: List<ApiEnvVar>? = null): List<ApiEnvironmentProfile> {
    val devVars =
      initialDevVars?.takeIf { it.isNotEmpty() } ?: listOf(ApiEnvVar(key = "apiToken", value = ""))
    return listOf(
      ApiEnvironmentProfile(id = "env_dev", name = "Dev", vars = devVars),
      ApiEnvironmentProfile(
        id = "env_staging",
        name = "Staging",
        vars = listOf(ApiEnvVar(key = "baseUrl", value = ""), ApiEnvVar(key = "apiToken", value = "")),
      ),
      ApiEnvironmentProfile(
        id = "env_prod",
        name = "Prod",
        vars = listOf(ApiEnvVar(key = "baseUrl", value = ""), ApiEnvVar(key = "apiToken", value = "")),
      ),
    )
  }

  fun applyDeployedWorkerUrl(
    current: ApiTesterRequest,
    deployedUrl: String,
    forceReplace: Boolean = false,
  ): ApiTesterRequest {
    val cleanDeployed = deployedUrl.trim().trimEnd('/')
    if (cleanDeployed.isBlank()) return current

    val cur = current.url.trim()
    val shouldReplace =
      forceReplace ||
        cur.isBlank() ||
        cur == "{{baseUrl}}" ||
        cur == "{{baseUrl}}/" ||
        workersDevRootPattern.matches(cur)

    if (shouldReplace) {
      return current.copy(url = cleanDeployed)
    }
    if (cur.startsWith("{{baseUrl}}")) {
      val suffix = cur.removePrefix("{{baseUrl}}")
      val normalizedSuffix = if (suffix.startsWith("/")) suffix else "/$suffix"
      return current.copy(url = "$cleanDeployed$normalizedSuffix")
    }
    val workersDevMatch = workersDevFullPattern.matchEntire(cur)
    if (workersDevMatch != null) {
      val pathSuffix = workersDevMatch.groupValues[2]
      return current.copy(url = if (pathSuffix.isBlank() || pathSuffix == "/") cleanDeployed else "$cleanDeployed$pathSuffix")
    }
    return current
  }

  fun substituteVariables(
    input: String,
    envVars: List<ApiEnvVar>,
    workerBaseUrl: String?,
  ): String {
    if (!input.contains("{{")) return input
    val activeMap = linkedMapOf<String, String>()
    if (!workerBaseUrl.isNullOrBlank()) {
      activeMap["baseUrl"] = workerBaseUrl.trim().trimEnd('/')
    }
    envVars.filter { it.enabled && it.key.isNotBlank() }.forEach {
      val k = it.key.trim()
      val v = it.value.trim()
      // Never let an empty profile placeholder overwrite an active deployed workerBaseUrl
      if (v.isNotEmpty() || !activeMap.containsKey(k)) {
        activeMap[k] = v
      }
    }
    return varPattern.replace(input) { match ->
      val key = match.groupValues[1]
      activeMap[key] ?: match.value
    }
  }

  private fun looksLikeBareHost(candidate: String): Boolean {
    if (candidate.startsWith("/") || candidate.startsWith("?")) return false
    val firstSegment = candidate.substringBefore('/').substringBefore('?').substringBefore('#')
    if (firstSegment.isBlank()) return false
    val hostOnly = firstSegment.substringBefore(':')
    return hostOnly.equals("localhost", ignoreCase = true) ||
      firstSegment.contains('.') ||
      firstSegment.contains(':')
  }

  fun normalizeBareOrRelativeUrl(rawInput: String, fallbackBase: String?): Result<String> {
    var expanded = rawInput.trim().replace("\r", "").replace("\n", "")
    if (expanded.isEmpty()) expanded = "/"

    val unresolved = varPattern.find(expanded)
    if (unresolved != null) {
      val varName = unresolved.groupValues[1]
      val hint =
        if (varName.equals("baseUrl", ignoreCase = true)) {
          "Deploy your Worker first, enter a full https:// URL, or set '$varName' in the Env tab."
        } else {
          "Set '$varName' in the Env tab or replace {{$varName}} with a value."
        }
      return Result.failure(IllegalArgumentException("Unresolved variable '{{$varName}}'. $hint"))
    }

    if (!expanded.startsWith("http://", ignoreCase = true) && !expanded.startsWith("https://", ignoreCase = true)) {
      if (looksLikeBareHost(expanded)) {
        val lower = expanded.lowercase()
        val scheme =
          if (lower.startsWith("localhost") || lower.startsWith("127.0.0.1") || lower.startsWith("10.") || lower.startsWith("192.168.")) {
            "http://"
          } else {
            "https://"
          }
        expanded = "$scheme$expanded"
      } else {
        if (fallbackBase.isNullOrBlank()) {
          return Result.failure(
            IllegalArgumentException(
              "Relative path '$expanded' needs an active Worker URL, a full https:// URL, or a {{baseUrl}} Env variable.",
            ),
          )
        }
        val cleanBase = fallbackBase.trim().trimEnd('/')
        val cleanPath = if (expanded.startsWith("/")) expanded else "/$expanded"
        expanded = "$cleanBase$cleanPath"
      }
    }

    if (expanded.toHttpUrlOrNull() == null) {
      return Result.failure(
        IllegalArgumentException("Invalid HTTP URL: '$expanded'. Check for spaces or malformed host syntax."),
      )
    }
    return Result.success(expanded)
  }

  fun resolveUrl(
    request: ApiTesterRequest,
    envVars: List<ApiEnvVar>,
    workerBaseUrl: String?,
  ): Result<String> {
    val substituted = substituteVariables(request.url.trim(), envVars, workerBaseUrl)
    val base =
      envVars.firstOrNull { it.enabled && it.key.equals("baseUrl", ignoreCase = true) && it.value.isNotBlank() }?.value
        ?: workerBaseUrl
    val normalized = normalizeBareOrRelativeUrl(substituted, base).getOrElse { return Result.failure(it) }

    val enabledParams = request.params.filter { it.enabled && it.key.isNotBlank() }
    if (enabledParams.isEmpty()) return Result.success(normalized)

    val queryString =
      enabledParams.joinToString("&") { p ->
        val k = URLEncoder.encode(substituteVariables(p.key.trim(), envVars, workerBaseUrl), "UTF-8")
        val v = URLEncoder.encode(substituteVariables(p.value, envVars, workerBaseUrl), "UTF-8")
        "$k=$v"
      }
    val hashIdx = normalized.indexOf('#')
    val beforeHash = if (hashIdx >= 0) normalized.substring(0, hashIdx) else normalized
    val hashSuffix = if (hashIdx >= 0) normalized.substring(hashIdx) else ""
    val sep = if (beforeHash.contains("?")) "&" else "?"
    val finalUrl = "$beforeHash$sep$queryString$hashSuffix"
    if (finalUrl.toHttpUrlOrNull() == null) {
      return Result.failure(IllegalArgumentException("Malformed URL after query parameters: '$finalUrl'"))
    }
    return Result.success(finalUrl)
  }

  fun methodSupportsBody(method: String): Boolean =
    method.uppercase() in setOf("POST", "PUT", "PATCH", "DELETE")

  private fun sanitizeHeaderPart(raw: String): String =
    raw.replace("\r", "").replace("\n", "").trim()

  fun buildHeadersAndBody(
    request: ApiTesterRequest,
    envVars: List<ApiEnvVar>,
    workerBaseUrl: String?,
  ): Pair<Map<String, String>, String> {
    val headers = linkedMapOf<String, String>()
    request.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { h ->
      val key = sanitizeHeaderPart(h.key)
      val value = sanitizeHeaderPart(substituteVariables(h.value, envVars, workerBaseUrl))
      if (key.isNotEmpty()) {
        headers[key] = value
      }
    }

    when (request.authType) {
      ApiAuthType.NONE -> Unit
      ApiAuthType.BEARER -> {
        val tok = sanitizeHeaderPart(substituteVariables(request.bearerToken, envVars, workerBaseUrl))
        if (tok.isNotEmpty()) headers["Authorization"] = "Bearer $tok"
      }
      ApiAuthType.BASIC -> {
        val u = substituteVariables(request.basicUser, envVars, workerBaseUrl)
        val p = substituteVariables(request.basicPass, envVars, workerBaseUrl)
        val encoded = Base64.encodeToString("$u:$p".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        headers["Authorization"] = "Basic $encoded"
      }
      ApiAuthType.API_KEY -> {
        val k = sanitizeHeaderPart(request.apiKeyName)
        val v = sanitizeHeaderPart(substituteVariables(request.apiKeyValue, envVars, workerBaseUrl))
        if (k.isNotEmpty() && v.isNotEmpty()) headers[k] = v
      }
    }

    val allowBody = methodSupportsBody(request.method)
    val bodyText =
      if (!allowBody) {
        ""
      } else {
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

  private sealed interface JsonLookup {
    data object Missing : JsonLookup
    data class Found(val value: Any?) : JsonLookup
  }

  fun extractJsonPath(jsonText: String, rawPath: String): String? {
    return when (val res = lookupJsonPath(jsonText, rawPath)) {
      is JsonLookup.Missing -> null
      is JsonLookup.Found -> res.value?.takeIf { it !== org.json.JSONObject.NULL }?.toString() ?: "null"
    }
  }

  private fun lookupJsonPath(jsonText: String, rawPath: String): JsonLookup {
    val cleanPath = rawPath.trim().removePrefix("$").removePrefix(".")
    if (cleanPath.isBlank()) return JsonLookup.Missing
    val trimmedJson = jsonText.trim()
    if (trimmedJson.isEmpty()) return JsonLookup.Missing
    var current: Any? =
      runCatching { org.json.JSONTokener(trimmedJson).nextValue() }.getOrNull() ?: return JsonLookup.Missing

    val tokens = tokenizePath(cleanPath)
    for (token in tokens) {
      current =
        when (val node = current) {
          is org.json.JSONObject -> {
            if (!node.has(token)) return JsonLookup.Missing
            node.get(token)
          }
          is org.json.JSONArray -> {
            val idx = token.toIntOrNull() ?: return JsonLookup.Missing
            if (idx !in 0 until node.length()) return JsonLookup.Missing
            node.get(idx)
          }
          else -> return JsonLookup.Missing
        }
    }
    return JsonLookup.Found(current)
  }

  private fun tokenizePath(path: String): List<String> {
    val out = mutableListOf<String>()
    val regex = Regex("""([^.\[\]]+)|\[(\d+)\]""")
    regex.findAll(path).forEach { m ->
      val segment = m.groupValues[1].ifEmpty { m.groupValues[2] }
      if (segment.isNotBlank()) out.add(segment)
    }
    return out
  }

  fun evaluateAssertions(
    rules: List<ApiAssertionRule>,
    result: HttpTestResult,
  ): List<ApiAssertionResult> {
    return rules.filter { it.enabled }.map { rule ->
      if (result.errorMessage != null && result.statusCode == 0) {
        return@map ApiAssertionResult(
          rule = rule,
          passed = false,
          actualValue = result.errorMessage,
          summary = "${rule.target.label}: Failed (${result.errorMessage})",
        )
      }

      when (rule.target) {
        ApiAssertionTarget.STATUS_CODE -> {
          val code = result.statusCode
          val passed =
            when (rule.operator) {
              ApiAssertionOperator.IS_2XX -> code in 200..299
              ApiAssertionOperator.EQUALS -> code.toString() == rule.expected.trim()
              ApiAssertionOperator.NOT_EQUALS -> code.toString() != rule.expected.trim()
              ApiAssertionOperator.LESS_THAN -> {
                val targetNum = rule.expected.trim().toDoubleOrNull()
                targetNum != null && code < targetNum
              }
              ApiAssertionOperator.GREATER_THAN -> {
                val targetNum = rule.expected.trim().toDoubleOrNull()
                targetNum != null && code > targetNum
              }
              ApiAssertionOperator.CONTAINS -> code.toString().contains(rule.expected.trim())
              ApiAssertionOperator.NOT_CONTAINS -> !code.toString().contains(rule.expected.trim())
              ApiAssertionOperator.EXISTS -> code > 0
            }
          val desc =
            if (rule.operator == ApiAssertionOperator.IS_2XX) "Status is 2xx"
            else "Status ${rule.operator.label} ${rule.expected.trim()}"
          ApiAssertionResult(
            rule = rule,
            passed = passed,
            actualValue = code.toString(),
            summary = "$desc (actual: $code)",
          )
        }

        ApiAssertionTarget.RESPONSE_TIME_MS -> {
          val ms = result.durationMs
          val targetNum = rule.expected.trim().toDoubleOrNull()
          val passed =
            when (rule.operator) {
              ApiAssertionOperator.LESS_THAN -> targetNum != null && ms < targetNum
              ApiAssertionOperator.GREATER_THAN -> targetNum != null && ms > targetNum
              ApiAssertionOperator.EQUALS -> targetNum != null && ms.toDouble() == targetNum
              ApiAssertionOperator.NOT_EQUALS -> targetNum != null && ms.toDouble() != targetNum
              else -> targetNum != null && ms <= targetNum
            }
          ApiAssertionResult(
            rule = rule,
            passed = passed,
            actualValue = "${ms}ms",
            summary = "Latency ${rule.operator.label} ${rule.expected.trim()}ms (actual: ${ms}ms)",
          )
        }

        ApiAssertionTarget.JSON_PATH -> {
          val lookup = lookupJsonPath(result.body, rule.property)
          val exists = lookup is JsonLookup.Found
          val actualText =
            when (lookup) {
              is JsonLookup.Missing -> "<missing>"
              is JsonLookup.Found -> lookup.value?.takeIf { it !== org.json.JSONObject.NULL }?.toString() ?: "null"
            }
          val passed = evaluateTextOrNumericOperator(exists, actualText, rule.operator, rule.expected)
          val pathLabel = rule.property.trim().ifBlank { "$" }
          val opText =
            if (rule.operator == ApiAssertionOperator.EXISTS) "exists"
            else "${rule.operator.label} '${rule.expected}'"
          ApiAssertionResult(
            rule = rule,
            passed = passed,
            actualValue = actualText,
            summary = "JSON $pathLabel $opText (actual: $actualText)",
          )
        }

        ApiAssertionTarget.BODY_TEXT -> {
          val actual = result.body
          val passed =
            when (rule.operator) {
              ApiAssertionOperator.EXISTS -> actual.isNotBlank()
              ApiAssertionOperator.EQUALS -> actual.trim() == rule.expected.trim()
              ApiAssertionOperator.NOT_EQUALS -> actual.trim() != rule.expected.trim()
              ApiAssertionOperator.CONTAINS -> actual.contains(rule.expected, ignoreCase = false)
              ApiAssertionOperator.NOT_CONTAINS -> !actual.contains(rule.expected, ignoreCase = false)
              else -> actual.contains(rule.expected, ignoreCase = true)
            }
          val preview = actual.take(48).replace('\n', ' ')
          ApiAssertionResult(
            rule = rule,
            passed = passed,
            actualValue = preview.ifBlank { "(empty)" },
            summary = "Body ${rule.operator.label} '${rule.expected}'",
          )
        }

        ApiAssertionTarget.HEADER -> {
          val targetHeader = rule.property.trim()
          val matched =
            result.headers.entries.firstOrNull { it.key.equals(targetHeader, ignoreCase = true) }
          val exists = matched != null
          val actual = matched?.value ?: "<missing>"
          val passed = evaluateTextOrNumericOperator(exists, actual, rule.operator, rule.expected)
          ApiAssertionResult(
            rule = rule,
            passed = passed,
            actualValue = actual,
            summary = "Header '$targetHeader' ${rule.operator.label} '${rule.expected}' (actual: $actual)",
          )
        }
      }
    }
  }

  private fun evaluateTextOrNumericOperator(
    exists: Boolean,
    actualText: String,
    operator: ApiAssertionOperator,
    expectedRaw: String,
  ): Boolean {
    if (!exists) return false
    val expected = expectedRaw.trim()
    return when (operator) {
      ApiAssertionOperator.EXISTS -> true
      ApiAssertionOperator.EQUALS -> actualText == expected
      ApiAssertionOperator.NOT_EQUALS -> actualText != expected
      ApiAssertionOperator.CONTAINS -> actualText.contains(expected, ignoreCase = true)
      ApiAssertionOperator.NOT_CONTAINS -> !actualText.contains(expected, ignoreCase = true)
      ApiAssertionOperator.LESS_THAN -> {
        val a = actualText.toDoubleOrNull()
        val e = expected.toDoubleOrNull()
        a != null && e != null && a < e
      }
      ApiAssertionOperator.GREATER_THAN -> {
        val a = actualText.toDoubleOrNull()
        val e = expected.toDoubleOrNull()
        a != null && e != null && a > e
      }
      ApiAssertionOperator.IS_2XX -> {
        val code = actualText.toIntOrNull()
        code != null && code in 200..299
      }
    }
  }

  fun prettyPrintIfJson(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return raw
    return runCatching {
      val parsed = JSONTokener(trimmed).nextValue()
      when (parsed) {
        is JSONObject -> parsed.toString(2)
        is JSONArray -> parsed.toString(2)
        else -> raw
      }
    }.getOrDefault(raw)
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
