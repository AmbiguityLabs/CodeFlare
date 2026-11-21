package com.example

import com.example.data.remote.CfWorkerBinding
import com.example.data.remote.WorkerLanguage
import com.example.data.remote.WorkerWorkspace
import com.example.editor.WranglerSyncParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WranglerSyncParserTest {

  @Test
  fun parseJsonc_stripsCommentsAndTrailingCommasAndExtractsBindings() {
    val jsonc =
      """
      {
        // Worker identity
        "name": "edge-api-worker",
        "main": "src/index.js",
        "compatibility_date": "2026-04-01",
        "compatibility_flags": ["nodejs_compat",],
        "workers_dev": true,
        /* Environment bindings */
        "vars": {
          "ENV_NAME": "staging",
        },
        "kv_namespaces": [
          { "binding": "CACHE_KV", "id": "kv_123" },
        ],
        "d1_databases": [
          { "binding": "PRIMARY_DB", "database_id": "d1_456" },
        ],
        "r2_buckets": [
          { "binding": "ASSETS", "bucket_name": "edge-assets" },
        ],
        "ai": { "binding": "AI" },
        "triggers": {
          "crons": ["*/15 * * * *",],
        },
      }
      """.trimIndent()

    val parsed = WranglerSyncParser.parse("wrangler.jsonc", jsonc).getOrThrow()
    assertEquals("edge-api-worker", parsed.scriptName)
    assertEquals("src/index.js", parsed.main)
    assertEquals("2026-04-01", parsed.compatibilityDate)
    assertEquals(listOf("nodejs_compat"), parsed.compatibilityFlags)
    assertEquals(true, parsed.workersDev)
    assertEquals(listOf("*/15 * * * *"), parsed.crons)

    val byName = parsed.bindings.associateBy { it.name }
    assertEquals("plain_text", byName["ENV_NAME"]?.type)
    assertEquals("staging", byName["ENV_NAME"]?.text)
    assertEquals("kv_namespace", byName["CACHE_KV"]?.type)
    assertEquals("kv_123", byName["CACHE_KV"]?.namespaceId)
    assertEquals("d1", byName["PRIMARY_DB"]?.type)
    assertEquals("d1_456", byName["PRIMARY_DB"]?.databaseId)
    assertEquals("r2_bucket", byName["ASSETS"]?.type)
    assertEquals("edge-assets", byName["ASSETS"]?.bucketName)
    assertEquals("ai", byName["AI"]?.type)
  }

  @Test
  fun parseToml_extractsTopLevelVarsCronsAndResourceBindings() {
    val toml =
      """
      # Wrangler TOML config
      name = "toml-worker"
      main = "./worker.ts"
      compatibility_date = "2026-03-10"
      workers_dev = false
      compatibility_flags = ["nodejs_compat"]

      [vars]
      API_URL = "https://api.example.com"

      [[kv_namespaces]]
      binding = "SESSIONS"
      id = "ns_999"

      [[d1_databases]]
      binding = "DB"
      database_id = "db_888"

      [ai]
      binding = "AI"

      [triggers]
      crons = ["0 * * * *"]
      """.trimIndent()

    val parsed = WranglerSyncParser.parse("wrangler.toml", toml).getOrThrow()
    assertEquals("toml-worker", parsed.scriptName)
    assertEquals("worker.ts", parsed.main)
    assertEquals("2026-03-10", parsed.compatibilityDate)
    assertEquals(false, parsed.workersDev)
    assertEquals(listOf("nodejs_compat"), parsed.compatibilityFlags)
    assertEquals(listOf("0 * * * *"), parsed.crons)
    assertTrue(parsed.bindings.any { it.name == "API_URL" && it.text == "https://api.example.com" })
    assertTrue(parsed.bindings.any { it.name == "SESSIONS" && it.namespaceId == "ns_999" })
    assertTrue(parsed.bindings.any { it.name == "DB" && it.databaseId == "db_888" })
    assertTrue(parsed.bindings.any { it.name == "AI" && it.type == "ai" })
  }

  @Test
  fun extractCodeSymbols_detectsHonoRoutesFunctionsAndHandlers() {
    val code =
      """
      import { Hono } from 'hono';
      const app = new Hono();
      app.get('/health', (c) => c.json({ ok: true }));
      app.post('/users', async (c) => c.json({ created: true }));
      export async function verifySignature(req) { return true; }
      export default {
        async fetch(request, env, ctx) {
          return app.fetch(request, env, ctx);
        },
        async scheduled(controller, env, ctx) {}
      };
      """.trimIndent()

    val symbols = WranglerSyncParser.extractCodeSymbols(code, WorkerLanguage.JAVASCRIPT)
    val names = symbols.map { it.name }
    assertTrue(names.contains("GET /health"))
    assertTrue(names.contains("POST /users"))
    assertTrue(names.contains("verifySignature()"))
    assertTrue(names.contains("export default"))
    assertTrue(names.contains("fetch()"))
    assertTrue(names.contains("scheduled()"))
  }

  @Test
  fun buildWorkersCompletions_includesEnvBindingsFromWorkspace() {
    val workspace =
      WorkerWorkspace(
        scriptName = "demo",
        isEsModuleFormat = true,
        entryModulePath = "index.js",
        compatibilityDate = "2026-04-01",
        files = emptyList(),
        bindings =
          listOf(
            CfWorkerBinding(name = "MY_KV", type = "kv_namespace", namespaceId = "123"),
            CfWorkerBinding(name = "AI", type = "ai"),
          ),
        routes = emptyList(),
        workersDevEnabled = true,
        accountSubdomain = "example",
      )

    val suggestions = WranglerSyncParser.buildWorkersCompletions(workspace, WorkerLanguage.JAVASCRIPT, "MY_KV")
    assertFalse(suggestions.isEmpty())
    assertEquals("env.MY_KV.get()", suggestions.first().label)
    assertTrue(suggestions.any { it.label == "env.AI.run()" })
  }

  @Test
  fun parseJsonc_mergesNamedEnvironmentOverrides() {
    val raw =
      """
      {
        "name": "api-worker",
        "main": "src/index.ts",
        "compatibility_date": "2026-04-01",
        "vars": {
          "STAGE": "dev",
          "SHARED": "keep"
        },
        "kv_namespaces": [
          { "binding": "CACHE", "id": "kv_base" }
        ],
        "env": {
          "staging": {
            "name": "api-worker-staging",
            "vars": {
              "STAGE": "staging"
            },
            "kv_namespaces": [
              { "binding": "CACHE", "id": "kv_staging" }
            ],
            "queues": {
              "producers": [
                { "binding": "EVENTS", "queue": "staging-events" }
              ]
            }
          }
        }
      }
      """.trimIndent()

    val base = WranglerSyncParser.parse("wrangler.jsonc", raw)
    assertEquals("api-worker", base.scriptName)
    assertTrue(base.environments.containsKey("staging"))
    assertEquals("dev", base.bindings.first { it.name == "STAGE" }.text)

    val staging = WranglerSyncParser.parse("wrangler.jsonc", raw, environment = "staging")
    assertEquals("api-worker-staging", staging.scriptName)
    assertEquals("src/index.ts", staging.main)
    assertEquals("staging", staging.bindings.first { it.name == "STAGE" }.text)
    assertEquals("keep", staging.bindings.first { it.name == "SHARED" }.text)
    assertEquals("kv_staging", staging.bindings.first { it.name == "CACHE" }.namespaceId)
    assertEquals("staging-events", staging.bindings.first { it.name == "EVENTS" }.queueName)
  }

  @Test
  fun parseToml_mergesNamedEnvironmentOverrides() {
    val raw =
      """
      name = "toml-api"
      main = "src/index.ts"
      compatibility_date = "2026-04-01"

      [vars]
      ENV = "default"
      APP_NAME = "edge"

      [[d1_databases]]
      binding = "DB"
      database_id = "db_default"

      [env.production]
      name = "toml-api-prod"

      [env.production.vars]
      ENV = "production"

      [[env.production.d1_databases]]
      binding = "DB"
      database_id = "db_prod"
      """.trimIndent()

    val prod = WranglerSyncParser.parse("wrangler.toml", raw, environment = "production")
    assertEquals("toml-api-prod", prod.scriptName)
    assertEquals("production", prod.bindings.first { it.name == "ENV" }.text)
    assertEquals("edge", prod.bindings.first { it.name == "APP_NAME" }.text)
    assertEquals("db_prod", prod.bindings.first { it.name == "DB" }.databaseId)
  }
}

