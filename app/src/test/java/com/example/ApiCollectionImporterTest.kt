package com.example

import com.example.data.remote.ApiAuthType
import com.example.data.remote.ApiBodyType
import com.example.data.remote.ApiCollectionImporter
import com.example.data.remote.ApiImportSourceFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApiCollectionImporterTest {

  @Test
  fun importsPostmanV21WithNestedFoldersAuthQueryParamsJsonBodyAndVars() {
    val postmanJson =
      """
      {
        "info": {
          "name": "Workers Edge API",
          "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
        },
        "auth": {
          "type": "bearer",
          "bearer": [
            { "key": "token", "value": "{{apiToken}}", "type": "string" }
          ]
        },
        "variable": [
          { "key": "baseUrl", "value": "https://edge.example.workers.dev" },
          { "key": "apiToken", "value": "secret-123" }
        ],
        "item": [
          {
            "name": "Users",
            "item": [
              {
                "name": "Create User",
                "request": {
                  "method": "POST",
                  "header": [
                    { "key": "X-Custom", "value": "v1" }
                  ],
                  "url": {
                    "raw": "{{baseUrl}}/users?role=admin",
                    "query": [
                      { "key": "role", "value": "admin" }
                    ]
                  },
                  "body": {
                    "mode": "raw",
                    "raw": "{\"name\":\"Ada\"}",
                    "options": { "raw": { "language": "json" } }
                  }
                }
              }
            ]
          }
        ]
      }
      """.trimIndent()

    val res = ApiCollectionImporter.parseImportJson(postmanJson).getOrThrow()
    assertEquals(ApiImportSourceFormat.POSTMAN_V2, res.sourceFormat)
    assertEquals("Workers Edge API", res.title)
    assertEquals(1, res.requests.size)
    assertEquals(2, res.extractedEnvVars.size)

    val req = res.requests.first()
    assertEquals("Users / Create User", req.name)
    assertEquals("POST", req.method)
    assertEquals("{{baseUrl}}/users", req.url)
    assertEquals("role", req.params.first().key)
    assertEquals("admin", req.params.first().value)
    assertEquals(ApiAuthType.BEARER, req.authType)
    assertEquals("{{apiToken}}", req.bearerToken)
    assertEquals(ApiBodyType.JSON, req.bodyType)
    assertTrue(req.rawBody.contains("Ada"))
    assertEquals(1, req.assertions.size)
  }

  @Test
  fun importsOpenApiV3WithServersParametersAuthAndSchemaBodySkeleton() {
    val openApiJson =
      """
      {
        "openapi": "3.0.3",
        "info": { "title": "Edge KV Service", "version": "1.0.0" },
        "servers": [{ "url": "https://kv-api.demo.workers.dev/" }],
        "components": {
          "securitySchemes": {
            "ApiKeyAuth": { "type": "apiKey", "in": "header", "name": "X-Edge-Key" }
          },
          "schemas": {
            "KvPayload": {
              "type": "object",
              "properties": {
                "value": { "type": "string", "example": "hello" },
                "ttl": { "type": "integer", "example": 3600 }
              }
            }
          }
        },
        "security": [{ "ApiKeyAuth": [] }],
        "paths": {
          "/v1/items": {
            "post": {
              "summary": "Upsert Item",
              "parameters": [
                { "name": "namespace", "in": "query", "schema": { "type": "string", "default": "prod" } }
              ],
              "requestBody": {
                "content": {
                  "application/json": {
                    "schema": { "${"$"}ref": "#/components/schemas/KvPayload" }
                  }
                }
              }
            }
          }
        }
      }
      """.trimIndent()

    val res = ApiCollectionImporter.parseImportJson(openApiJson).getOrThrow()
    assertEquals(ApiImportSourceFormat.OPENAPI_V3, res.sourceFormat)
    assertEquals("Edge KV Service", res.title)
    assertEquals("baseUrl", res.extractedEnvVars.first().key)
    assertEquals("https://kv-api.demo.workers.dev", res.extractedEnvVars.first().value)

    val req = res.requests.first()
    assertEquals("Upsert Item", req.name)
    assertEquals("POST", req.method)
    assertEquals("{{baseUrl}}/v1/items", req.url)
    assertEquals(ApiAuthType.API_KEY, req.authType)
    assertEquals("X-Edge-Key", req.apiKeyName)
    assertEquals("namespace", req.params.first().key)
    assertEquals("prod", req.params.first().value)
    assertEquals(ApiBodyType.JSON, req.bodyType)
    assertTrue(req.rawBody.contains("\"value\": \"hello\""))
    assertTrue(req.rawBody.contains("3600"))
  }

  @Test
  fun importsSwaggerV2EndpointsAndHostUrl() {
    val swaggerJson =
      """
      {
        "swagger": "2.0",
        "info": { "title": "Legacy Worker" },
        "host": "legacy.example.com",
        "basePath": "/api",
        "schemes": ["https"],
        "paths": {
          "/status": {
            "get": {
              "summary": "Check Status",
              "parameters": [
                { "name": "verbose", "in": "query", "default": "1" }
              ]
            }
          }
        }
      }
      """.trimIndent()

    val res = ApiCollectionImporter.parseImportJson(swaggerJson).getOrThrow()
    assertEquals(ApiImportSourceFormat.SWAGGER_V2, res.sourceFormat)
    assertEquals("https://legacy.example.com/api", res.extractedEnvVars.first().value)
    assertEquals("Check Status", res.requests.first().name)
    assertEquals("{{baseUrl}}/status", res.requests.first().url)
  }

  @Test
  fun rejectsUnknownJsonSpecificationsGracefully() {
    val bad = ApiCollectionImporter.parseImportJson("""{"hello": "world"}""")
    assertTrue(bad.isFailure)
  }
}
