package com.example

import com.example.data.remote.ApiAuthType
import com.example.data.remote.ApiBodyType
import com.example.data.remote.ApiEnvVar
import com.example.data.remote.ApiKeyValueRow
import com.example.data.remote.ApiRequestUtils
import com.example.data.remote.ApiTesterRequest
import com.example.data.remote.GitIgnoreMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitIgnoreMatcherTest {

  @Test
  fun isIgnored_respectsFolderGlobsWildcardsCommentsAndNegations() {
    val patterns =
      GitIgnoreMatcher.parsePatterns(
        """
        # Build & dependencies
        node_modules/
        *.log
        dist/
        !dist/important.js
        .env
        .dev.vars
        """.trimIndent(),
      )

    assertTrue(GitIgnoreMatcher.isIgnored("node_modules/hono/index.js", patterns))
    assertTrue(GitIgnoreMatcher.isIgnored("logs/debug.log", patterns))
    assertTrue(GitIgnoreMatcher.isIgnored("dist/bundle.js", patterns))
    assertFalse(GitIgnoreMatcher.isIgnored("dist/important.js", patterns))
    assertTrue(GitIgnoreMatcher.isIgnored(".env", patterns))
    assertTrue(GitIgnoreMatcher.isIgnored(".dev.vars", patterns))
    assertFalse(GitIgnoreMatcher.isIgnored(".gitignore", patterns))
    assertFalse(GitIgnoreMatcher.isIgnored("src/index.js", patterns))
  }

  @Test
  fun apiRequestUtils_substitutesEnvVarsAndBuildsBearerAndCurl() {
    val env =
      listOf(
        ApiEnvVar(key = "token", value = "secret-123", enabled = true),
        ApiEnvVar(key = "userId", value = "u_42", enabled = true),
      )
    val req =
      ApiTesterRequest(
        name = "Get User",
        method = "POST",
        url = "{{baseUrl}}/api/users/{{userId}}",
        params = listOf(ApiKeyValueRow(key = "expand", value = "roles", enabled = true)),
        authType = ApiAuthType.BEARER,
        bearerToken = "{{token}}",
        bodyType = ApiBodyType.JSON,
        rawBody = """{"uid":"{{userId}}"}""",
      )

    val resolvedUrl =
      ApiRequestUtils.resolveUrl(req, env, "https://my-worker.demo.workers.dev").getOrThrow()
    assertEquals("https://my-worker.demo.workers.dev/api/users/u_42?expand=roles", resolvedUrl)

    val (headers, body) =
      ApiRequestUtils.buildHeadersAndBody(req, env, "https://my-worker.demo.workers.dev")
    assertEquals("Bearer secret-123", headers["Authorization"])
    assertEquals("application/json", headers["Content-Type"])
    assertEquals("""{"uid":"u_42"}""", body)

    val curl = ApiRequestUtils.toCurlSnippet(req, env, "https://my-worker.demo.workers.dev")
    assertTrue(curl.contains("curl -i -X POST"))
    assertTrue(curl.contains("Authorization: Bearer secret-123"))
  }
}
