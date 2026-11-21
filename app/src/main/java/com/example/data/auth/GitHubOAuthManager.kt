package com.example.data.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

@JsonClass(generateAdapter = true)
data class GitHubDeviceCodeResponse(
  @Json(name = "device_code") val deviceCode: String = "",
  @Json(name = "user_code") val userCode: String = "",
  @Json(name = "verification_uri") val verificationUri: String = "https://github.com/login/device",
  @Json(name = "expires_in") val expiresIn: Int = 900,
  val interval: Int = 5,
)

@JsonClass(generateAdapter = true)
data class GitHubOAuthTokenResponse(
  @Json(name = "access_token") val accessToken: String? = null,
  @Json(name = "token_type") val tokenType: String? = null,
  val scope: String? = null,
  val error: String? = null,
  @Json(name = "error_description") val errorDescription: String? = null,
  val interval: Int? = null,
)

object GitHubOAuthManager {
  const val DEFAULT_CLIENT_ID = "178c6fc778ccc68e1d6a"
  const val DEVICE_CODE_URL = "https://github.com/login/device/code"
  const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
  const val DEFAULT_SCOPES = "repo read:org workflow gist user:email"

  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val deviceAdapter = moshi.adapter(GitHubDeviceCodeResponse::class.java)
  private val tokenAdapter = moshi.adapter(GitHubOAuthTokenResponse::class.java)
  private val httpClient =
    com.example.data.remote.SharedHttpClientProvider.newBuilder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .build()

  suspend fun requestDeviceCode(
    clientId: String = DEFAULT_CLIENT_ID,
    scopes: String = DEFAULT_SCOPES,
  ): Result<GitHubDeviceCodeResponse> =
    withContext(Dispatchers.IO) {
      runCatching {
        val effectiveClientId = clientId.trim().ifBlank { DEFAULT_CLIENT_ID }
        val form =
          FormBody.Builder()
            .add("client_id", effectiveClientId)
            .add("scope", scopes)
            .build()
        val req =
          Request.Builder()
            .url(DEVICE_CODE_URL)
            .post(form)
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(req).execute().use { resp ->
          val raw = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            error("GitHub device authorization start failed (HTTP ${resp.code}): $raw")
          }
          val parsed = deviceAdapter.fromJson(raw)
            ?: error("Invalid response from GitHub Device Flow endpoint")
          if (parsed.deviceCode.isBlank() || parsed.userCode.isBlank()) {
            error("GitHub did not return a device verification code")
          }
          parsed
        }
      }
    }

  suspend fun pollForAccessToken(
    session: GitHubDeviceCodeResponse,
    clientId: String = DEFAULT_CLIENT_ID,
  ): Result<GitHubOAuthTokenResponse> =
    withContext(Dispatchers.IO) {
      val effectiveClientId = clientId.trim().ifBlank { DEFAULT_CLIENT_ID }
      var intervalMs = (session.interval.coerceAtLeast(5)) * 1000L
      val deadline = System.currentTimeMillis() + (session.expiresIn.coerceAtLeast(120) * 1000L)

      while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
        delay(intervalMs)
        val form =
          FormBody.Builder()
            .add("client_id", effectiveClientId)
            .add("device_code", session.deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device-code")
            .build()
        val req =
          Request.Builder()
            .url(ACCESS_TOKEN_URL)
            .post(form)
            .header("Accept", "application/json")
            .build()

        val result =
          runCatching {
            httpClient.newCall(req).execute().use { resp ->
              val raw = resp.body?.string().orEmpty()
              tokenAdapter.fromJson(raw)
                ?: error("Unexpected GitHub OAuth response (HTTP ${resp.code})")
            }
          }

        val parsed = result.getOrElse { err -> return@withContext Result.failure(err) }
        val token = parsed.accessToken
        if (!token.isNullOrBlank()) {
          return@withContext Result.success(parsed)
        }

        when (parsed.error) {
          "authorization_pending" -> continue
          "slow_down" -> {
            intervalMs = ((parsed.interval ?: ((intervalMs / 1000L).toInt() + 5)) * 1000L).coerceAtLeast(intervalMs + 5000L)
          }
          "expired_token" -> {
            return@withContext Result.failure(IllegalStateException("Device code expired. Start GitHub OAuth again."))
          }
          "access_denied" -> {
            return@withContext Result.failure(IllegalStateException("GitHub authorization was canceled by the user."))
          }
          else -> {
            val desc = parsed.errorDescription ?: parsed.error ?: "GitHub OAuth failed"
            return@withContext Result.failure(IllegalStateException(desc))
          }
        }
      }

      Result.failure(IllegalStateException("GitHub OAuth timed out before authorization completed."))
    }

  fun buildClassicPatCreationUrl(): String =
    "https://github.com/settings/tokens/new?description=EdgeCode+Mobile+IDE&scopes=repo,workflow,read:org,gist,user:email"

  fun buildFineGrainedPatUrl(): String =
    "https://github.com/settings/personal-access-tokens/new"
}
