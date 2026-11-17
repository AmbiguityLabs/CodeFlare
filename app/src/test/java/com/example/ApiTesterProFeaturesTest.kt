package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DraftPersistenceManager
import com.example.data.local.PersistedApiTesterState
import com.example.data.remote.ApiAssertionOperator
import com.example.data.remote.ApiAssertionRule
import com.example.data.remote.ApiAssertionTarget
import com.example.data.remote.ApiEnvVar
import com.example.data.remote.ApiEnvironmentProfile
import com.example.data.remote.ApiRequestUtils
import com.example.data.remote.ApiTesterRequest
import com.example.data.remote.HttpTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApiTesterProFeaturesTest {

  @Test
  fun extractJsonPathSupportsNestedObjectsAndArrays() {
    val json =
      """
      {
        "ok": true,
        "meta": { "region": "iad", "count": 42 },
        "items": [
          { "id": "first", "tags": ["edge", "cf"] },
          { "id": "second", "nullable": null }
        ]
      }
      """.trimIndent()

    assertEquals("true", ApiRequestUtils.extractJsonPath(json, "ok"))
    assertEquals("iad", ApiRequestUtils.extractJsonPath(json, "$.meta.region"))
    assertEquals("42", ApiRequestUtils.extractJsonPath(json, "meta.count"))
    assertEquals("cf", ApiRequestUtils.extractJsonPath(json, "items[0].tags[1]"))
    assertEquals("null", ApiRequestUtils.extractJsonPath(json, "items[1].nullable"))
    assertNull(ApiRequestUtils.extractJsonPath(json, "items[99].id"))
    assertNull(ApiRequestUtils.extractJsonPath(json, "meta.missing"))
  }

  @Test
  fun evaluateAssertionsValidatesStatusLatencyJsonPathHeadersAndBody() {
    val httpResult =
      HttpTestResult(
        url = "https://worker.demo.workers.dev/health",
        method = "GET",
        statusCode = 200,
        statusText = "OK",
        durationMs = 145L,
        headers = mapOf("Content-Type" to "application/json; charset=utf-8", "X-Region" to "SIN"),
        body = """{"ok":true,"data":{"version":3,"status":"healthy"}}""",
      )

    val rules =
      listOf(
        ApiAssertionRule(id = "r1", target = ApiAssertionTarget.STATUS_CODE, operator = ApiAssertionOperator.IS_2XX),
        ApiAssertionRule(id = "r2", target = ApiAssertionTarget.RESPONSE_TIME_MS, operator = ApiAssertionOperator.LESS_THAN, expected = "300"),
        ApiAssertionRule(id = "r3", target = ApiAssertionTarget.JSON_PATH, property = "data.version", operator = ApiAssertionOperator.EQUALS, expected = "3"),
        ApiAssertionRule(id = "r4", target = ApiAssertionTarget.HEADER, property = "content-type", operator = ApiAssertionOperator.CONTAINS, expected = "application/json"),
        ApiAssertionRule(id = "r5", target = ApiAssertionTarget.BODY_TEXT, operator = ApiAssertionOperator.CONTAINS, expected = "healthy"),
        // Failing rule
        ApiAssertionRule(id = "r6", target = ApiAssertionTarget.JSON_PATH, property = "data.status", operator = ApiAssertionOperator.EQUALS, expected = "degraded"),
        // Disabled rule should be skipped
        ApiAssertionRule(id = "r7", target = ApiAssertionTarget.STATUS_CODE, operator = ApiAssertionOperator.EQUALS, expected = "500", enabled = false),
      )

    val evaluated = ApiRequestUtils.evaluateAssertions(rules, httpResult)
    assertEquals(6, evaluated.size)
    assertTrue(evaluated[0].passed)
    assertTrue(evaluated[1].passed)
    assertTrue(evaluated[2].passed)
    assertTrue(evaluated[3].passed)
    assertTrue(evaluated[4].passed)
    assertFalse(evaluated[5].passed)
    assertEquals("healthy", evaluated[5].actualValue)
  }

  @Test
  fun environmentProfilesAndRequestAssertionsPersistRoundTrip() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DraftPersistenceManager(context)

    val profiles =
      listOf(
        ApiEnvironmentProfile("env_dev", "Dev", listOf(ApiEnvVar("baseUrl", "https://dev.workers.dev"))),
        ApiEnvironmentProfile("env_staging", "Staging", listOf(ApiEnvVar("baseUrl", "https://staging.workers.dev"))),
        ApiEnvironmentProfile("env_prod", "Prod", listOf(ApiEnvVar("baseUrl", "https://api.example.com"))),
      )
    val savedRequest =
      ApiTesterRequest(
        id = "req_health",
        name = "Health Check",
        method = "GET",
        url = "{{baseUrl}}/health",
        assertions =
          listOf(
            ApiAssertionRule(
              id = "assert_1",
              target = ApiAssertionTarget.JSON_PATH,
              property = "ok",
              operator = ApiAssertionOperator.EQUALS,
              expected = "true",
            ),
          ),
      )

    manager.saveApiTesterState(
      PersistedApiTesterState(
        collections = listOf(savedRequest),
        history = emptyList(),
        envVars = profiles[2].vars,
        environmentProfiles = profiles,
        activeEnvProfileId = "env_prod",
      ),
    )

    val restored = manager.loadApiTesterState()
    assertEquals("env_prod", restored.activeEnvProfileId)
    assertEquals(3, restored.environmentProfiles.size)
    assertEquals("https://api.example.com", restored.envVars.first { it.key == "baseUrl" }.value)

    val restoredReq = restored.collections.first()
    assertEquals(1, restoredReq.assertions.size)
    assertEquals(ApiAssertionTarget.JSON_PATH, restoredReq.assertions.first().target)
    assertEquals("ok", restoredReq.assertions.first().property)
    assertEquals("true", restoredReq.assertions.first().expected)

    val resolvedUrl = ApiRequestUtils.resolveUrl(restoredReq, restored.envVars, workerBaseUrl = null).getOrThrow()
    assertEquals("https://api.example.com/health", resolvedUrl)
  }
}
