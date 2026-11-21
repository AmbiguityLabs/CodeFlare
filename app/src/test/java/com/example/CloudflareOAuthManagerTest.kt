package com.example

import com.example.data.auth.CloudflareOAuthManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudflareOAuthManagerTest {

  @After
  fun tearDown() {
    CloudflareOAuthManager.closeActiveListener()
  }

  @Test
  fun createPkceSessionUsesOfficialLoopbackRedirectAndChallenge() {
    val session = CloudflareOAuthManager.createPkceSession()
    assertTrue(session.authorizationUrl.contains("client_id=${CloudflareOAuthManager.CLIENT_ID}"))
    assertTrue(session.authorizationUrl.contains("code_challenge_method=S256"))
    assertTrue(session.authorizationUrl.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8976%2Foauth%2Fcallback"))
    assertTrue(session.state.isNotBlank())
    assertTrue(session.codeVerifier.isNotBlank())
  }

  @Test
  fun extractCallbackCodeHandlesRedirectUrlsErrorsStateAndRawCodes() {
    val valid =
      CloudflareOAuthManager.extractCallbackCode(
        rawInput = "http://localhost:8976/oauth/callback?code=auth_code_123&state=st_ok",
        expectedState = "st_ok",
      )
    assertTrue(valid.isSuccess)
    assertEquals("auth_code_123", valid.getOrNull())

    val mismatch =
      CloudflareOAuthManager.extractCallbackCode(
        rawInput = "http://localhost:8976/oauth/callback?code=auth_code_123&state=wrong_state",
        expectedState = "st_ok",
      )
    assertTrue(mismatch.isFailure)
    assertTrue(mismatch.exceptionOrNull()?.message.orEmpty().contains("state mismatch"))

    val denied =
      CloudflareOAuthManager.extractCallbackCode(
        rawInput = "http://localhost:8976/oauth/callback?error=access_denied&error_description=User+denied",
        expectedState = "st_ok",
      )
    assertTrue(denied.isFailure)
    assertEquals("User denied", denied.exceptionOrNull()?.message)

    val missingCode =
      CloudflareOAuthManager.extractCallbackCode(
        rawInput = "http://localhost:8976/oauth/callback?state=st_ok",
        expectedState = "st_ok",
      )
    assertTrue(missingCode.isFailure)

    val rawCode =
      CloudflareOAuthManager.extractCallbackCode(
        rawInput = "  cf_raw_code_789  ",
        expectedState = "st_ok",
      )
    assertTrue(rawCode.isSuccess)
    assertEquals("cf_raw_code_789", rawCode.getOrNull())

    val blank =
      CloudflareOAuthManager.extractCallbackCode(
        rawInput = "   ",
        expectedState = "st_ok",
      )
    assertTrue(blank.isFailure)
  }

  @Test
  fun openLoopbackListenerBindsReuseAddressAndReleasesOnClose() {
    val first = CloudflareOAuthManager.openLoopbackListener()
    try {
      assertTrue(first.isBound)
      assertFalse(first.isClosed)
      assertTrue(first.reuseAddress)
      assertEquals(CloudflareOAuthManager.REDIRECT_PORT, first.localPort)
    } finally {
      CloudflareOAuthManager.closeActiveListener()
    }
    assertTrue(first.isClosed)

    val second = CloudflareOAuthManager.openLoopbackListener()
    try {
      assertTrue(second.isBound)
      assertFalse(second.isClosed)
    } finally {
      CloudflareOAuthManager.closeActiveListener()
    }
  }

  @Test
  fun loopbackListenerAnswersFaviconAndCallbackHtmlBeforeClosing() = runBlocking {
    val session = CloudflareOAuthManager.createPkceSession()
    val prebound = CloudflareOAuthManager.openLoopbackListener()

    val awaitDeferred =
      async {
        CloudflareOAuthManager.awaitCallbackAndExchange(session = session, preboundSocket = prebound)
      }

    Socket("127.0.0.1", CloudflareOAuthManager.REDIRECT_PORT).use { client ->
      client.getOutputStream().write(
        "GET /favicon.ico HTTP/1.1\r\nHost: localhost:8976\r\n\r\n".toByteArray(Charsets.UTF_8),
      )
      client.getOutputStream().flush()
      val statusLine = BufferedReader(InputStreamReader(client.getInputStream())).readLine()
      assertTrue(statusLine.orEmpty().contains("204 No Content"))
    }

    Socket("127.0.0.1", CloudflareOAuthManager.REDIRECT_PORT).use { client ->
      client.getOutputStream().write(
        "GET /oauth/callback?error=access_denied&error_description=Cancelled HTTP/1.1\r\nHost: localhost:8976\r\n\r\n"
          .toByteArray(Charsets.UTF_8),
      )
      client.getOutputStream().flush()
      val responseText = client.getInputStream().bufferedReader().readText()
      assertTrue(responseText.contains("HTTP/1.1 200 OK"))
      assertTrue(responseText.contains("Cache-Control: no-store"))
      assertTrue(responseText.contains("Authorization declined: Cancelled"))
    }

    val outcome = awaitDeferred.await()
    assertTrue(outcome.isFailure)
    assertEquals("Cancelled", outcome.exceptionOrNull()?.message)
    assertTrue(prebound.isClosed)
  }
}
