package com.example.data.auth

import android.net.Uri
import android.util.Base64
import com.example.data.remote.CfOAuthTokenResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

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
  private val activeServerSocket = AtomicReference<ServerSocket?>(null)

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

  fun openLoopbackListener(): ServerSocket {
    closeActiveListener()
    val server =
      ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(REDIRECT_PORT), 16)
        soTimeout = 1500
      }
    activeServerSocket.set(server)
    return server
  }

  fun closeActiveListener() {
    val existing = activeServerSocket.getAndSet(null) ?: return
    runCatching { existing.close() }
  }

  fun extractCallbackCode(rawInput: String, expectedState: String): Result<String> {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) {
      return Result.failure(IllegalArgumentException("Paste the OAuth callback URL or code."))
    }
    if (!trimmed.contains("://") && !trimmed.contains("?") && !trimmed.contains("=")) {
      return Result.success(trimmed)
    }
    val query =
      when {
        trimmed.contains("?") -> trimmed.substringAfter("?").substringBefore("#")
        else -> trimmed.substringBefore("#")
      }
    val params = parseQueryParams(query)
    val err = params["error"]
    if (!err.isNullOrBlank()) {
      return Result.failure(IllegalStateException(params["error_description"] ?: err))
    }
    val returnedState = params["state"]
    if (!returnedState.isNullOrBlank() && returnedState != expectedState) {
      return Result.failure(IllegalStateException("OAuth state mismatch. Please retry sign-in."))
    }
    val code = params["code"]?.trim()
    if (code.isNullOrBlank()) {
      return Result.failure(IllegalArgumentException("Callback URL is missing the 'code' parameter."))
    }
    return Result.success(code)
  }

  suspend fun completeFromManualRedirect(
    session: PkceSession,
    rawRedirectOrCode: String,
  ): Result<CfOAuthTokenResponse> =
    withContext(Dispatchers.IO) {
      closeActiveListener()
      val code = extractCallbackCode(rawRedirectOrCode, session.state).getOrElse {
        return@withContext Result.failure(it)
      }
      exchangeCodeForToken(code = code, codeVerifier = session.codeVerifier)
    }

  suspend fun awaitCallbackAndExchange(
    session: PkceSession,
    preboundSocket: ServerSocket? = null,
  ): Result<CfOAuthTokenResponse> =
    withContext(Dispatchers.IO) {
      var serverSocket: ServerSocket? = preboundSocket
      try {
        if (serverSocket == null || serverSocket.isClosed) {
          serverSocket = openLoopbackListener()
        } else {
          activeServerSocket.set(serverSocket)
          serverSocket.soTimeout = 1500
        }

        val deadline = System.currentTimeMillis() + 180_000L
        var authCode: String? = null
        var authError: String? = null

        while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline && !serverSocket.isClosed) {
          val socket =
            try {
              serverSocket.accept()
            } catch (_: SocketTimeoutException) {
              continue
            } catch (_: SocketException) {
              break
            }

          var handledCallback = false
          socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine().orEmpty()
            while (true) {
              val line = reader.readLine() ?: break
              if (line.isEmpty()) break
            }
            if (requestLine.isBlank()) return@use

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
                    message = "Cloudflare account authorized! Returning to EdgeCode…",
                  )
              }

              val responseBytes = html.toByteArray(Charsets.UTF_8)
              val headers =
                "HTTP/1.1 200 OK\r\n" +
                  "Content-Type: text/html; charset=utf-8\r\n" +
                  "Cache-Control: no-store\r\n" +
                  "Content-Length: ${responseBytes.size}\r\n" +
                  "Connection: close\r\n\r\n"
              client.getOutputStream().apply {
                write(headers.toByteArray(Charsets.UTF_8))
                write(responseBytes)
                flush()
              }
              runCatching { client.shutdownOutput() }
              runCatching { Thread.sleep(150L) }
              handledCallback = true
            } else if (pathAndQuery.startsWith("/favicon")) {
              val noContent = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
              client.getOutputStream().apply {
                write(noContent.toByteArray(Charsets.UTF_8))
                flush()
              }
            } else {
              val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
              client.getOutputStream().apply {
                write(notFound.toByteArray(Charsets.UTF_8))
                flush()
              }
            }
          }
          if (handledCallback) break
        }

        if (authError != null) {
          return@withContext Result.failure(IllegalStateException(authError))
        }
        val code =
          authCode
            ?: return@withContext Result.failure(
              IllegalStateException("Timed out waiting for Cloudflare OAuth callback."),
            )

        exchangeCodeForToken(code = code, codeVerifier = session.codeVerifier)
      } catch (e: Exception) {
        Result.failure(e)
      } finally {
        activeServerSocket.compareAndSet(serverSocket, null)
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
    val returnButton =
      if (success) {
        """<a class="btn" href="edgecode://oauth/return">Return to EdgeCode</a>"""
      } else {
        ""
      }
    val autoScript =
      if (success) {
        """<script>setTimeout(function(){ window.location.href = "edgecode://oauth/return"; }, 250);</script>"""
      } else {
        ""
      }
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
          .btn { display:inline-block; margin-top:16px; padding:10px 18px; border-radius:10px; background:#F6821F; color:#121318; font-weight:700; font-size:13px; text-decoration:none; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="pill">$badge</div>
          <h1>EdgeCode Workers IDE</h1>
          <p>$message</p>
          $returnButton
        </div>
        $autoScript
      </body>
      </html>
    """.trimIndent()
  }
}
