package com.example

import com.example.data.remote.ApiRequestUtils
import com.example.data.remote.ApiTesterRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiRequestUtilsTest {

  @Test
  fun populatesDefaultPlaceholderAndEmptyUrlsWithDeployedWorkerUrl() {
    val deployed = "https://my-worker.edgecode.workers.dev"

    val fromDefault =
      ApiRequestUtils.applyDeployedWorkerUrl(
        current = ApiTesterRequest(url = "{{baseUrl}}/"),
        deployedUrl = "$deployed/",
        forceReplace = false,
      )
    assertEquals(deployed, fromDefault.url)

    val fromEmpty =
      ApiRequestUtils.applyDeployedWorkerUrl(
        current = ApiTesterRequest(url = "   "),
        deployedUrl = deployed,
        forceReplace = false,
      )
    assertEquals(deployed, fromEmpty.url)

    val fromBaseVar =
      ApiRequestUtils.applyDeployedWorkerUrl(
        current = ApiTesterRequest(url = "{{baseUrl}}"),
        deployedUrl = deployed,
        forceReplace = false,
      )
    assertEquals(deployed, fromBaseVar.url)
  }

  @Test
  fun preservesPathSuffixForWorkersDevAndBaseUrlAndRespectsForceReplace() {
    val deployed = "https://billing-api.acme.workers.dev"

    val withTemplatePath =
      ApiRequestUtils.applyDeployedWorkerUrl(
        current = ApiTesterRequest(url = "{{baseUrl}}/v1/health"),
        deployedUrl = deployed,
        forceReplace = false,
      )
    assertEquals("$deployed/v1/health", withTemplatePath.url)

    val switchingWorkerPreservingPath =
      ApiRequestUtils.applyDeployedWorkerUrl(
        current = ApiTesterRequest(url = "https://old-worker.acme.workers.dev/echo?ping=1"),
        deployedUrl = deployed,
        forceReplace = false,
      )
    assertEquals("$deployed/echo?ping=1", switchingWorkerPreservingPath.url)

    val customExternalKeptOnSoftSync =
      ApiRequestUtils.applyDeployedWorkerUrl(
        current = ApiTesterRequest(url = "https://httpbin.org/get"),
        deployedUrl = deployed,
        forceReplace = false,
      )
    assertEquals("https://httpbin.org/get", customExternalKeptOnSoftSync.url)

    val customExternalReplacedOnNewDeploy =
      ApiRequestUtils.applyDeployedWorkerUrl(
        current = ApiTesterRequest(url = "https://httpbin.org/get"),
        deployedUrl = deployed,
        forceReplace = true,
      )
    assertEquals(deployed, customExternalReplacedOnNewDeploy.url)
  }
}
