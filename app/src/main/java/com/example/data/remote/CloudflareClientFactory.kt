package com.example.data.remote

import com.example.data.auth.CloudflareOAuthManager
import com.example.data.security.AuthMethod
import com.example.data.security.AuthPreferencesStore
import com.example.data.security.StoredCredentials
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CloudflareClientFactory(
  private val prefsStore: AuthPreferencesStore,
  baseUrl: String = BASE_URL,
  private val tokenRefresher: (String) -> Result<CfOAuthTokenResponse> = CloudflareOAuthManager::refreshAccessToken,
  private val transportInterceptor: Interceptor? = null,
) {
  companion object {
    private const val BASE_URL = "https://api.cloudflare.com/client/v4/"
  }

  private val refreshLock = Any()

  val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

  val rawHttpClient: OkHttpClient =
    SharedHttpClientProvider.newBuilder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .build()

  private val authInterceptor = Interceptor { chain ->
    val creds = runBlocking { prefsStore.getCachedOrCurrentCredentials() }
    val original = chain.request()
    val hasExplicitAuth = original.header("Authorization") != null
    val builder = original.newBuilder().header("Accept", "application/json")

    if (!hasExplicitAuth && creds != null) {
      applyAuthHeaders(builder, creds)
    }

    val response = chain.proceed(builder.build())

    // Transparently refresh OAuth token on 401 with lock + double-check so concurrent 401s refresh once
    if (
      !hasExplicitAuth &&
        response.code == 401 &&
        creds?.method == AuthMethod.OAUTH_PKCE &&
        creds.refreshToken.isNotBlank()
    ) {
      val retryCreds: StoredCredentials? =
        synchronized(refreshLock) {
          val latestCreds = runBlocking { prefsStore.getCurrentCredentials() }
          if (
            latestCreds != null &&
              latestCreds.method == AuthMethod.OAUTH_PKCE &&
              latestCreds.accessToken.isNotBlank() &&
              latestCreds.accessToken != creds.accessToken
          ) {
            // Another concurrent request already refreshed the access token
            latestCreds
          } else {
            val refreshSource = latestCreds?.refreshToken?.takeIf { it.isNotBlank() } ?: creds.refreshToken
            val refreshed = tokenRefresher(refreshSource).getOrNull()
            if (refreshed != null && refreshed.accessToken.isNotBlank()) {
              val baseCreds = latestCreds ?: creds
              val updatedCreds =
                baseCreds.copy(
                  accessToken = refreshed.accessToken,
                  refreshToken = refreshed.refreshToken ?: baseCreds.refreshToken,
                  expiresAtEpochMs = System.currentTimeMillis() + (refreshed.expiresIn * 1000L),
                )
              runBlocking { prefsStore.saveCredentials(updatedCreds) }
              updatedCreds
            } else {
              null
            }
          }
        }

      if (retryCreds != null) {
        response.close()
        val retryReqBuilder =
          original
            .newBuilder()
            .header("Accept", "application/json")
        applyAuthHeaders(retryReqBuilder, retryCreds)
        return@Interceptor chain.proceed(retryReqBuilder.build())
      }
    }

    response
  }

  private fun applyAuthHeaders(builder: okhttp3.Request.Builder, creds: StoredCredentials) {
    when (creds.method) {
      AuthMethod.OAUTH_PKCE,
      AuthMethod.API_TOKEN -> {
        builder.header("Authorization", "Bearer ${creds.accessToken}")
      }
      AuthMethod.GLOBAL_KEY -> {
        builder.header("X-Auth-Email", creds.email)
        builder.header("X-Auth-Key", creds.accessToken)
      }
    }
  }

  private val okHttpClient: OkHttpClient =
    SharedHttpClientProvider.newBuilder()
      .addInterceptor(authInterceptor)
      .addInterceptor(
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
      )
      .apply {
        if (transportInterceptor != null) {
          addInterceptor(transportInterceptor)
        }
      }
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(45, TimeUnit.SECONDS)
      .writeTimeout(45, TimeUnit.SECONDS)
      .build()

  val apiService: CloudflareApiService =
    Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(CloudflareApiService::class.java)
}
