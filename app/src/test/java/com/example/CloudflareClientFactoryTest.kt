package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.CfOAuthTokenResponse
import com.example.data.remote.CloudflareClientFactory
import com.example.data.security.AuthMethod
import com.example.data.security.AuthPreferencesStore
import com.example.data.security.StoredCredentials
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudflareClientFactoryTest {

  @Test
  fun concurrent401ResponsesTriggerSingleOAuthRefreshAndRetryAllRequests() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = AuthPreferencesStore(context)
    prefs.clearCredentials()
    prefs.saveCredentials(
      StoredCredentials(
        method = AuthMethod.OAUTH_PKCE,
        accessToken = "expired-token-1",
        refreshToken = "refresh-token-1",
        expiresAtEpochMs = System.currentTimeMillis() - 1_000L,
      ),
    )

    val refreshCallCount = AtomicInteger(0)
    val startBarrier = CountDownLatch(3)

    val transport = Interceptor { chain ->
      val req = chain.request()
      val auth = req.header("Authorization")
      if (auth == "Bearer expired-token-1") {
        startBarrier.countDown()
        startBarrier.await(2, TimeUnit.SECONDS)
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(401)
          .message("Unauthorized")
          .body("""{"success":false,"errors":[{"code":10000,"message":"Authentication error"}]}""".toResponseBody("application/json".toMediaType()))
          .build()
      } else {
        assertEquals("Bearer fresh-token-2", auth)
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body("""{"success":true,"errors":[],"messages":[],"result":{"id":"usr-1","email":"dev@edge.dev"}}""".toResponseBody("application/json".toMediaType()))
          .build()
      }
    }

    val factory =
      CloudflareClientFactory(
        prefsStore = prefs,
        tokenRefresher = { refreshTok ->
          assertEquals("refresh-token-1", refreshTok)
          refreshCallCount.incrementAndGet()
          Result.success(
            CfOAuthTokenResponse(
              accessToken = "fresh-token-2",
              refreshToken = "refresh-token-2",
              expiresIn = 3600L,
            ),
          )
        },
        transportInterceptor = transport,
      )

    val results =
      (1..3).map {
        async(Dispatchers.IO) {
          factory.apiService.getCurrentUser()
        }
      }.awaitAll()

    assertTrue(results.all { it.isSuccessful && it.body()?.success == true })
    assertEquals(1, refreshCallCount.get())
    val stored = prefs.getCachedOrCurrentCredentials()
    assertEquals("fresh-token-2", stored?.accessToken)
    assertEquals("refresh-token-2", stored?.refreshToken)
  }
}
