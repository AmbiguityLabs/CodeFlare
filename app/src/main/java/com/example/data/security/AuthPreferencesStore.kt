package com.example.data.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.edgeCodeDataStore by preferencesDataStore(name = "edgecode_secure_prefs")

enum class AuthMethod {
  OAUTH_PKCE,
  API_TOKEN,
  GLOBAL_KEY,
}

data class StoredCredentials(
  val method: AuthMethod,
  val accessToken: String,
  val refreshToken: String = "",
  val expiresAtEpochMs: Long = 0L,
  val email: String = "",
  val selectedAccountId: String = "",
  val selectedAccountName: String = "",
)

enum class GitHubAuthMethod {
  OAUTH_DEVICE,
  PAT,
}

data class StoredGitHubCredentials(
  val token: String,
  val login: String,
  val method: GitHubAuthMethod = GitHubAuthMethod.PAT,
  val scopes: String = "",
  val avatarUrl: String = "",
  val displayName: String = "",
)

data class EditorSettings(
  val darkTheme: Boolean = true,
  val dynamicColor: Boolean = false,
  val fontSizeSp: Int = 13,
  val tabSize: Int = 2,
  val wordWrap: Boolean = false,
  val showLineNumbers: Boolean = true,
  val autoCloseBrackets: Boolean = true,
)

class AuthPreferencesStore(private val context: Context) {
  private object Keys {
    val AUTH_METHOD = stringPreferencesKey("auth_method")
    val ENC_ACCESS_TOKEN = stringPreferencesKey("enc_access_token")
    val ENC_REFRESH_TOKEN = stringPreferencesKey("enc_refresh_token")
    val EXPIRES_AT = longPreferencesKey("expires_at_ms")
    val EMAIL = stringPreferencesKey("auth_email")
    val ACCOUNT_ID = stringPreferencesKey("selected_account_id")
    val ACCOUNT_NAME = stringPreferencesKey("selected_account_name")

    val ENC_GITHUB_TOKEN = stringPreferencesKey("enc_github_token")
    val GITHUB_LOGIN = stringPreferencesKey("github_login")
    val GITHUB_METHOD = stringPreferencesKey("github_method")
    val GITHUB_SCOPES = stringPreferencesKey("github_scopes")
    val GITHUB_AVATAR = stringPreferencesKey("github_avatar")
    val GITHUB_NAME = stringPreferencesKey("github_display_name")

    val DARK_THEME = booleanPreferencesKey("editor_dark_theme")
    val DYNAMIC_COLOR = booleanPreferencesKey("editor_dynamic_color")
    val FONT_SIZE_SP = intPreferencesKey("editor_font_size_sp")
    val TAB_SIZE = intPreferencesKey("editor_tab_size")
    val WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
    val SHOW_LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
    val AUTO_CLOSE_BRACKETS = booleanPreferencesKey("editor_auto_brackets")
  }

  val credentialsFlow: Flow<StoredCredentials?> =
    context.edgeCodeDataStore.data.map { prefs ->
      val methodStr = prefs[Keys.AUTH_METHOD] ?: return@map null
      val method = runCatching { AuthMethod.valueOf(methodStr) }.getOrNull() ?: return@map null
      val token = CryptoManager.decrypt(prefs[Keys.ENC_ACCESS_TOKEN].orEmpty())
      if (token.isEmpty()) return@map null
      StoredCredentials(
        method = method,
        accessToken = token,
        refreshToken = CryptoManager.decrypt(prefs[Keys.ENC_REFRESH_TOKEN].orEmpty()),
        expiresAtEpochMs = prefs[Keys.EXPIRES_AT] ?: 0L,
        email = prefs[Keys.EMAIL].orEmpty(),
        selectedAccountId = prefs[Keys.ACCOUNT_ID].orEmpty(),
        selectedAccountName = prefs[Keys.ACCOUNT_NAME].orEmpty(),
      )
    }

  val gitHubCredentialsFlow: Flow<StoredGitHubCredentials?> =
    context.edgeCodeDataStore.data.map { prefs ->
      val token = CryptoManager.decrypt(prefs[Keys.ENC_GITHUB_TOKEN].orEmpty())
      if (token.isEmpty()) return@map null
      val method =
        runCatching { GitHubAuthMethod.valueOf(prefs[Keys.GITHUB_METHOD] ?: "PAT") }
          .getOrDefault(GitHubAuthMethod.PAT)
      StoredGitHubCredentials(
        token = token,
        login = prefs[Keys.GITHUB_LOGIN].orEmpty(),
        method = method,
        scopes = prefs[Keys.GITHUB_SCOPES].orEmpty(),
        avatarUrl = prefs[Keys.GITHUB_AVATAR].orEmpty(),
        displayName = prefs[Keys.GITHUB_NAME].orEmpty(),
      )
    }

  val editorSettingsFlow: Flow<EditorSettings> =
    context.edgeCodeDataStore.data.map { prefs ->
      EditorSettings(
        darkTheme = prefs[Keys.DARK_THEME] ?: true,
        dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
        fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 13).coerceIn(10, 20),
        tabSize = if ((prefs[Keys.TAB_SIZE] ?: 2) == 4) 4 else 2,
        wordWrap = prefs[Keys.WORD_WRAP] ?: false,
        showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
        autoCloseBrackets = prefs[Keys.AUTO_CLOSE_BRACKETS] ?: true,
      )
    }

  suspend fun getCurrentCredentials(): StoredCredentials? = credentialsFlow.first()

  suspend fun getCurrentGitHubCredentials(): StoredGitHubCredentials? = gitHubCredentialsFlow.first()

  suspend fun saveCredentials(credentials: StoredCredentials) {
    context.edgeCodeDataStore.edit { prefs ->
      prefs[Keys.AUTH_METHOD] = credentials.method.name
      prefs[Keys.ENC_ACCESS_TOKEN] = CryptoManager.encrypt(credentials.accessToken)
      prefs[Keys.ENC_REFRESH_TOKEN] = CryptoManager.encrypt(credentials.refreshToken)
      prefs[Keys.EXPIRES_AT] = credentials.expiresAtEpochMs
      prefs[Keys.EMAIL] = credentials.email
      if (credentials.selectedAccountId.isNotEmpty()) {
        prefs[Keys.ACCOUNT_ID] = credentials.selectedAccountId
      }
      if (credentials.selectedAccountName.isNotEmpty()) {
        prefs[Keys.ACCOUNT_NAME] = credentials.selectedAccountName
      }
    }
  }

  suspend fun saveGitHubCredentials(
    token: String,
    login: String,
    method: GitHubAuthMethod = GitHubAuthMethod.PAT,
    scopes: String = "",
    avatarUrl: String = "",
    displayName: String = "",
  ) {
    context.edgeCodeDataStore.edit { prefs ->
      prefs[Keys.ENC_GITHUB_TOKEN] = CryptoManager.encrypt(token)
      prefs[Keys.GITHUB_LOGIN] = login
      prefs[Keys.GITHUB_METHOD] = method.name
      prefs[Keys.GITHUB_SCOPES] = scopes
      prefs[Keys.GITHUB_AVATAR] = avatarUrl
      prefs[Keys.GITHUB_NAME] = displayName
    }
  }

  suspend fun clearGitHubCredentials() {
    context.edgeCodeDataStore.edit { prefs ->
      prefs.remove(Keys.ENC_GITHUB_TOKEN)
      prefs.remove(Keys.GITHUB_LOGIN)
      prefs.remove(Keys.GITHUB_METHOD)
      prefs.remove(Keys.GITHUB_SCOPES)
      prefs.remove(Keys.GITHUB_AVATAR)
      prefs.remove(Keys.GITHUB_NAME)
    }
  }

  suspend fun saveSelectedAccount(accountId: String, accountName: String) {
    context.edgeCodeDataStore.edit { prefs ->
      prefs[Keys.ACCOUNT_ID] = accountId
      prefs[Keys.ACCOUNT_NAME] = accountName
    }
  }

  suspend fun clearCredentials() {
    context.edgeCodeDataStore.edit { prefs ->
      prefs.remove(Keys.AUTH_METHOD)
      prefs.remove(Keys.ENC_ACCESS_TOKEN)
      prefs.remove(Keys.ENC_REFRESH_TOKEN)
      prefs.remove(Keys.EXPIRES_AT)
      prefs.remove(Keys.EMAIL)
      prefs.remove(Keys.ACCOUNT_ID)
      prefs.remove(Keys.ACCOUNT_NAME)
    }
  }

  suspend fun updateEditorSettings(transform: (EditorSettings) -> EditorSettings) {
    val current = editorSettingsFlow.first()
    val next = transform(current)
    context.edgeCodeDataStore.edit { prefs ->
      prefs[Keys.DARK_THEME] = next.darkTheme
      prefs[Keys.DYNAMIC_COLOR] = next.dynamicColor
      prefs[Keys.FONT_SIZE_SP] = next.fontSizeSp
      prefs[Keys.TAB_SIZE] = next.tabSize
      prefs[Keys.WORD_WRAP] = next.wordWrap
      prefs[Keys.SHOW_LINE_NUMBERS] = next.showLineNumbers
      prefs[Keys.AUTO_CLOSE_BRACKETS] = next.autoCloseBrackets
    }
  }
}
