package com.example.data.remote

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/**
 * Provides a single shared [OkHttpClient] connection pool and dispatcher across
 * Cloudflare API, GitHub API, OAuth managers, and the local Worker HTTP bridge.
 */
object SharedHttpClientProvider {
  private val sharedConnectionPool = ConnectionPool(8, 5, TimeUnit.MINUTES)
  private val sharedDispatcher =
    Dispatcher().apply {
      maxRequests = 48
      maxRequestsPerHost = 12
    }

  val baseClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectionPool(sharedConnectionPool)
      .dispatcher(sharedDispatcher)
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .build()

  fun newBuilder(): OkHttpClient.Builder = baseClient.newBuilder()
}
