package com.example.data.remote

import com.example.data.auth.CloudflareOAuthManager
import com.example.data.security.AuthMethod
import com.example.data.security.AuthPreferencesStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CloudflareClientFactory(private val prefsStore: AuthPreferencesStore) {
  companion object {
    private const val BASE_URL = "https://api.cloudflare.com/client/v4/"
  }

  val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

  val rawHttpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .build()

  private val authInterceptor = Interceptor { chain ->
    val creds = runBlocking { prefsStore.getCurrentCredentials() }
    val original = chain.request()
    val builder = original.newBuilder().header("Accept", "application/json")

    if (creds != null) {
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

    val response = chain.proceed(builder.build())

    // Transparently refresh OAuth token on 401 if refresh_token is available
    if (
      response.code == 401 &&
        creds?.method == AuthMethod.OAUTH_PKCE &&
        creds.refreshToken.isNotBlank()
    ) {
      val refreshed = CloudflareOAuthManager.refreshAccessToken(creds.refreshToken).getOrNull()
      if (refreshed != null && refreshed.accessToken.isNotBlank()) {
        response.close()
        val updatedCreds =
          creds.copy(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken ?: creds.refreshToken,
            expiresAtEpochMs = System.currentTimeMillis() + (refreshed.expiresIn * 1000L),
          )
        runBlocking { prefsStore.saveCredentials(updatedCreds) }
        val retryReq =
          original
            .newBuilder()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${updatedCreds.accessToken}")
            .build()
        return@Interceptor chain.proceed(retryReq)
      }
    }

    response
  }

  private val okHttpClient: OkHttpClient =
    OkHttpClient.Builder()
      .addInterceptor(authInterceptor)
      .addInterceptor(
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
      )
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(45, TimeUnit.SECONDS)
      .writeTimeout(45, TimeUnit.SECONDS)
      .build()

  val apiService: CloudflareApiService =
    Retrofit.Builder()
      .baseUrl(BASE_URL)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(CloudflareApiService::class.java)
}
