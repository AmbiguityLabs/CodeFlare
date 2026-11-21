package com.example.data.auth

import android.net.Uri
import android.util.Base64
import com.example.data.remote.CfOAuthTokenResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Implements RFC 8252 Native App OAuth 2.0 PKCE Authorization against Cloudflare's
 * official Wrangler-compatible OAuth2 endpoints with a local loopback callback listener.
 */
object CloudflareOAuthManager {
  const val CLIENT_ID = "54d11594-84e4-41aa-b438-e81b8fa78ee8"
  const val REDIRECT_PORT = 8976
  const val REDIRECT_URI = "http://localhost:$REDIRECT_PORT/oauth/callback"
  const val AUTH_URL = "https://dash.cloudflare.com/oauth2/auth"
  const val TOKEN_URL = "https://dash.cloudflare.com/oauth2/token"

  val OAUTH_SCOPES =
    listOf(
      "account:read",
      "user:read",
      "workers:write",
      "workers_kv:write",
      "workers_routes:write",
      "workers_scripts:write",
      "workers_tail:read",
      "offline_access",
    )

  data class PkceSession(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
    val authorizationUrl: String,
  )

  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val tokenAdapter = moshi.adapter(CfOAuthTokenResponse::class.java)
  private val httpClient = com.example.data.remote.SharedHttpClientProvider.newBuilder().build()

  fun createPkceSession(): PkceSession {
    val random = SecureRandom()
    val verifierBytes = ByteArray(48).also { random.nextBytes(it) }
    val verifier = Base64.encodeToString(verifierBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    val stateBytes = ByteArray(24).also { random.nextBytes(it) }
    val state = Base64.encodeToString(stateBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    val uri =
      Uri.parse(AUTH_URL)
        .buildUpon()
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("client_id", CLIENT_ID)
        .appendQueryParameter("redirect_uri", REDIRECT_URI)
        .appendQueryParameter("scope", OAUTH_SCOPES.joinToString(" "))
        .appendQueryParameter("state", state)
        .appendQueryParameter("code_challenge", challenge)
        .appendQueryParameter("code_challenge_method", "S256")
        .build()

    return PkceSession(
      codeVerifier = verifier,
      codeChallenge = challenge,
      state = state,
      authorizationUrl = uri.toString(),
    )
  }

  fun buildScopedTokenDashboardUrl(): String {
    val permissions =
      """[{"key":"workers_scripts","type":"edit"},{"key":"workers_kv_storage","type":"edit"},{"key":"workers_routes","type":"edit"},{"key":"account_settings","type":"read"}]"""
    return Uri.parse("https://dash.cloudflare.com/profile/api-tokens")
      .buildUpon()
      .appendQueryParameter("permissionGroupKeys", permissions)
      .appendQueryParameter("name", "CodeFlare Mobile IDE")
      .build()
      .toString()
  }

  /**
   * Listens on localhost:8976 for Cloudflare's OAuth callback redirect, validates state,
   * returns the authorization code, and writes a crisp HTML confirmation page to the browser.
   */
  suspend fun awaitCallbackAndExchange(session: PkceSession): Result<CfOAuthTokenResponse> =
    withContext(Dispatchers.IO) {
      var serverSocket: ServerSocket? = null
      try {
        serverSocket = ServerSocket(REDIRECT_PORT, 4, InetAddress.getByName("127.0.0.1"))
        serverSocket.soTimeout = 1500

        val deadline = System.currentTimeMillis() + 180_000L
        var authCode: String? = null
        var authError: String? = null

        while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
          val socket =
            try {
              serverSocket.accept()
            } catch (_: SocketTimeoutException) {
              continue
            }

          socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine().orEmpty()
            // Consume headers
            while (true) {
              val line = reader.readLine() ?: break
              if (line.isEmpty()) break
            }

            val pathAndQuery = requestLine.split(" ").getOrNull(1).orEmpty()
            if (pathAndQuery.startsWith("/oauth/callback")) {
              val query = pathAndQuery.substringAfter("?", "")
              val params = parseQueryParams(query)
              val returnedState = params["state"]
              val err = params["error"]
              val code = params["code"]

              val html: String
              if (err != null) {
                authError = params["error_description"] ?: err
                html = callbackHtml(success = false, message = "Authorization declined: $authError")
              } else if (returnedState != session.state) {
                authError = "OAuth state mismatch. Please retry sign-in."
                html = callbackHtml(success = false, message = authError!!)
              } else if (code.isNullOrBlank()) {
                authError = "Missing authorization code from Cloudflare."
                html = callbackHtml(success = false, message = authError!!)
              } else {
                authCode = code
                html =
                  callbackHtml(
                    success = true,
                    message = "Cloudflare account authorized! You can return to EdgeCode now.",
                  )
              }

              val responseBytes = html.toByteArray(Charsets.UTF_8)
              val headers =
                "HTTP/1.1 200 OK\r\n" +
                  "Content-Type: text/html; charset=utf-8\r\n" +
                  "Content-Length: ${responseBytes.size}\r\n" +
                  "Connection: close\r\n\r\n"
              client.getOutputStream().apply {
                write(headers.toByteArray(Charsets.UTF_8))
                write(responseBytes)
                flush()
              }
              break
            } else {
              val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
              client.getOutputStream().write(notFound.toByteArray(Charsets.UTF_8))
            }
          }
        }

        if (authError != null) {
          return@withContext Result.failure(IllegalStateException(authError))
        }
        val code =
          authCode
            ?: return@withContext Result.failure(
              IllegalStateException("Timed out waiting for Cloudflare OAuth callback.")
            )

        exchangeCodeForToken(code = code, codeVerifier = session.codeVerifier)
      } catch (e: Exception) {
        Result.failure(e)
      } finally {
        runCatching { serverSocket?.close() }
      }
    }

  fun exchangeCodeForToken(code: String, codeVerifier: String): Result<CfOAuthTokenResponse> {
    val formBody =
      FormBody.Builder()
        .add("grant_type", "authorization_code")
        .add("client_id", CLIENT_ID)
        .add("code", code)
        .add("redirect_uri", REDIRECT_URI)
        .add("code_verifier", codeVerifier)
        .build()

    val request =
      Request.Builder()
        .url(TOKEN_URL)
        .post(formBody)
        .header("Accept", "application/json")
        .build()

    return runCatching {
      httpClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          error("Cloudflare OAuth token exchange failed (${response.code}): $raw")
        }
        tokenAdapter.fromJson(raw)
          ?: error("Invalid token response from Cloudflare OAuth server")
      }
    }
  }

  fun refreshAccessToken(refreshToken: String): Result<CfOAuthTokenResponse> {
    val formBody =
      FormBody.Builder()
        .add("grant_type", "refresh_token")
        .add("client_id", CLIENT_ID)
        .add("refresh_token", refreshToken)
        .build()

    val request =
      Request.Builder()
        .url(TOKEN_URL)
        .post(formBody)
        .header("Accept", "application/json")
        .build()

    return runCatching {
      httpClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          error("OAuth refresh failed (${response.code}): $raw")
        }
        tokenAdapter.fromJson(raw)
          ?: error("Invalid refresh token response from Cloudflare")
      }
    }
  }

  private fun parseQueryParams(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    return query.split("&").mapNotNull { pair ->
      val idx = pair.indexOf('=')
      if (idx <= 0) null
      else {
        val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
        val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        k to v
      }
    }.toMap()
  }

  private fun callbackHtml(success: Boolean, message: String): String {
    val accent = if (success) "#F6821F" else "#F43F5E"
    val badge = if (success) "AUTHORIZED" else "FAILED"
    return """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>EdgeCode Cloudflare Auth</title>
        <style>
          body { margin:0; font-family: -apple-system, BlinkMacSystemFont, sans-serif; background:#121318; color:#E6EDF3; display:flex; align-items:center; justify-content:center; min-height:100vh; }
          .card { max-width:420px; margin:24px; padding:28px; border-radius:16px; background:#1C1C23; border:1px solid #2A2C38; text-align:center; }
          .pill { display:inline-block; padding:4px 12px; border-radius:999px; font-size:11px; font-weight:700; letter-spacing:1px; background:${accent}22; color:$accent; border:1px solid ${accent}55; }
          h1 { font-size:20px; margin:16px 0 8px; }
          p { font-size:14px; color:#A0A8B8; line-height:1.5; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="pill">$badge</div>
          <h1>EdgeCode Workers IDE</h1>
          <p>$message</p>
        </div>
      </body>
      </html>
    """.trimIndent()
  }
}
