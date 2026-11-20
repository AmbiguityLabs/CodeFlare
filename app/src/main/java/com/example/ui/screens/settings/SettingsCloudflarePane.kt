package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.CfAccount
import com.example.data.security.AuthMethod
import com.example.ui.components.EdgeLoadingIndicator
import com.example.ui.components.IconRevealButton
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.Ca
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.viewmodel.IdeUiState

@Composable
fun SettingsCloudflarePane(
  uiState: IdeUiState,
  onSelectAccount: (CfAccount) -> Unit,
  onVerifyCredentials: () -> Unit,
  onUpdateApiToken: (String) -> Unit,
  onUpdateGlobalKey: (String, String) -> Unit,
  onStartOAuth: () -> Unit,
  onCancelOAuth: () -> Unit,
  onClearCredentialFeedback: () -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val clipboard = LocalClipboardManager.current
  val creds = uiState.credentials

  var revealActiveSecret by remember { mutableStateOf(false) }
  var showCredentialEditor by rememberSaveable { mutableStateOf(creds == null) }
  var credentialTab by rememberSaveable {
    mutableIntStateOf(
      when (creds?.method) {
        AuthMethod.GLOBAL_KEY -> 1
        AuthMethod.OAUTH_PKCE -> 2
        else -> 0
      },
    )
  }
  var apiTokenInput by rememberSaveable { mutableStateOf("") }
  var showTokenInput by rememberSaveable { mutableStateOf(false) }
  var globalEmailInput by rememberSaveable(creds?.email) { mutableStateOf(creds?.email.orEmpty()) }
  var globalKeyInput by rememberSaveable { mutableStateOf("") }
  var showGlobalKeyInput by rememberSaveable { mutableStateOf(false) }

  Column(
    modifier = modifier.fillMaxWidth().testTag("settings_cloudflare_section"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SettingsCard(title = "Session") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        val tone = if (creds != null) EmeraldLive else AmberWarn
        Surface(
          color = tone.copy(alpha = 0.12f),
          shape = CircleShape,
          border = BorderStroke(0.75.dp, tone.copy(alpha = 0.35f)),
          modifier = Modifier.size(38.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (creds != null) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
              contentDescription = null,
              tint = tone,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text =
              when (creds?.method) {
                AuthMethod.API_TOKEN -> "API Token"
                AuthMethod.GLOBAL_KEY -> "Global API Key"
                AuthMethod.OAUTH_PKCE -> "OAuth 2.0"
                null -> "Not Connected"
              },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
          )
          val identity =
            when {
              creds == null -> "Link Cloudflare credentials to deploy Workers"
              creds.email.isNotBlank() -> creds.email
              uiState.selectedAccount != null ->
                uiState.selectedAccount.name.replace(Regex("'s\\s+Account$", RegexOption.IGNORE_CASE), "")
              else -> "Active Cloudflare session"
            }
          Text(
            text = identity,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(tone))
      }

      if (creds != null) {
        Surface(
          color = scheme.surfaceContainerHigh,
          shape = RoundedCornerShape(Ca.radius.control),
          border = BorderStroke(0.75.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val maskedToken =
              remember(creds.accessToken, revealActiveSecret) {
                if (revealActiveSecret) creds.accessToken
                else "cf_••••••••••••${creds.accessToken.takeLast(6)}"
              }
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "TOKEN",
                  fontFamily = JetBrainsMono,
                  fontSize = 8.5.sp,
                  letterSpacing = 0.7.sp,
                  color = scheme.outline,
                )
                Text(
                  text = maskedToken,
                  fontFamily = JetBrainsMono,
                  fontSize = 11.5.sp,
                  color = scheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = { revealActiveSecret = !revealActiveSecret },
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = if (revealActiveSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                  contentDescription = if (revealActiveSecret) "Hide credential" else "Reveal credential",
                  tint = scheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
              }
              IconButton(
                onClick = {
                  clipboard.setText(AnnotatedString(creds.accessToken))
                  onNotify("Copied credential")
                },
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.ContentCopy,
                  contentDescription = "Copy credential",
                  tint = scheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
              }
            }

            uiState.selectedAccount?.let { acc ->
              SettingsDivider()
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "ACCOUNT ID",
                    fontFamily = JetBrainsMono,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.7.sp,
                    color = scheme.outline,
                  )
                  Text(
                    text = "${acc.name} · ${acc.id}",
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                IconButton(
                  onClick = {
                    clipboard.setText(AnnotatedString(acc.id))
                    onNotify("Copied Account ID")
                  },
                  modifier = Modifier.size(34.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy Account ID",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
      ) {
        if (creds != null) {
          IconRevealButton(
            icon = Icons.Outlined.VerifiedUser,
            revealedLabel = "Verify",
            isBusy = uiState.isTestingCfCredentials,
            onClick = { if (!uiState.isTestingCfCredentials) onVerifyCredentials() },
            height = 38.dp,
            tag = "settings_test_cf_connection",
          )
        }
        IconRevealButton(
          icon = Icons.Outlined.Key,
          revealedLabel = if (creds == null) "Connect" else if (showCredentialEditor) "Close" else "Update",
          active = showCredentialEditor,
          onClick = {
            onClearCredentialFeedback()
            showCredentialEditor = !showCredentialEditor
          },
          height = 38.dp,
          tag = "settings_toggle_credential_editor",
        )
      }

      uiState.cfCredentialTestMessage?.let { msg ->
        SettingsStatusBanner(
          message = msg,
          isError = uiState.isCfCredentialTestError,
        )
      }
    }

    if (uiState.accounts.size > 1) {
      SettingsFoldableSection(
        title = "Accounts",
        icon = Icons.Outlined.CloudDone,
        summary = "${uiState.accounts.size}",
        initiallyExpanded = false,
      ) {
        uiState.accounts.forEachIndexed { index, acc ->
          if (index > 0) SettingsHairlineDivider()
          val selected = acc.id == uiState.selectedAccount?.id
          Surface(
            onClick = { onSelectAccount(acc) },
            color = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                  text = acc.name,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                  color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = acc.id,
                  fontFamily = JetBrainsMono,
                  fontSize = 10.sp,
                  color = scheme.outline,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (selected) {
                Icon(
                  imageVector = Icons.Outlined.Check,
                  contentDescription = "Active account",
                  tint = scheme.primary,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = showCredentialEditor,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      SettingsCard(title = "Credentials") {
        SettingsSegmentedControl(
          options = listOf("API Token", "Global Key", "OAuth"),
          selectedIndex = credentialTab,
          onSelect = { credentialTab = it },
        )

        when (credentialTab) {
          0 -> {
            OutlinedTextField(
              value = apiTokenInput,
              onValueChange = { apiTokenInput = it },
              label = { Text("API Token") },
              singleLine = true,
              visualTransformation = if (showTokenInput) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                Row {
                  IconButton(
                    onClick = {
                      val clip = clipboard.getText()?.text
                      if (!clip.isNullOrBlank()) apiTokenInput = clip.trim()
                    },
                  ) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste token", modifier = Modifier.size(18.dp))
                  }
                  IconButton(onClick = { showTokenInput = !showTokenInput }) {
                    Icon(
                      imageVector = if (showTokenInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                      contentDescription = "Toggle token visibility",
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
              keyboardActions =
                KeyboardActions(
                  onDone = {
                    if (apiTokenInput.isNotBlank() && !uiState.isTestingCfCredentials) {
                      onUpdateApiToken(apiTokenInput)
                    }
                  },
                ),
              shape = RoundedCornerShape(Ca.radius.control),
              modifier = Modifier.fillMaxWidth().testTag("settings_cf_token_input"),
            )
            Button(
              onClick = { onUpdateApiToken(apiTokenInput) },
              enabled = apiTokenInput.isNotBlank() && !uiState.isTestingCfCredentials,
              colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
              shape = RoundedCornerShape(Ca.radius.control),
              modifier = Modifier.fillMaxWidth().height(44.dp).testTag("settings_save_cf_token"),
            ) {
              if (uiState.isTestingCfCredentials) {
                EdgeLoadingIndicator(size = 16.dp, color = scheme.onPrimary)
                Spacer(Modifier.width(8.dp))
              }
              Text("Save API Token", fontWeight = FontWeight.SemiBold)
            }
          }

          1 -> {
            OutlinedTextField(
              value = globalEmailInput,
              onValueChange = { globalEmailInput = it },
              label = { Text("Email") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
              shape = RoundedCornerShape(Ca.radius.control),
              modifier = Modifier.fillMaxWidth().testTag("settings_cf_email_input"),
            )
            OutlinedTextField(
              value = globalKeyInput,
              onValueChange = { globalKeyInput = it },
              label = { Text("Global API Key") },
              singleLine = true,
              visualTransformation = if (showGlobalKeyInput) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                Row {
                  IconButton(
                    onClick = {
                      val clip = clipboard.getText()?.text
                      if (!clip.isNullOrBlank()) globalKeyInput = clip.trim()
                    },
                  ) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste key", modifier = Modifier.size(18.dp))
                  }
                  IconButton(onClick = { showGlobalKeyInput = !showGlobalKeyInput }) {
                    Icon(
                      imageVector = if (showGlobalKeyInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                      contentDescription = "Toggle key visibility",
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
              keyboardActions =
                KeyboardActions(
                  onDone = {
                    if (globalEmailInput.isNotBlank() && globalKeyInput.isNotBlank() && !uiState.isTestingCfCredentials) {
                      onUpdateGlobalKey(globalEmailInput, globalKeyInput)
                    }
                  },
                ),
              shape = RoundedCornerShape(Ca.radius.control),
              modifier = Modifier.fillMaxWidth().testTag("settings_cf_global_key_input"),
            )
            Button(
              onClick = { onUpdateGlobalKey(globalEmailInput, globalKeyInput) },
              enabled = globalEmailInput.isNotBlank() && globalKeyInput.isNotBlank() && !uiState.isTestingCfCredentials,
              colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
              shape = RoundedCornerShape(Ca.radius.control),
              modifier = Modifier.fillMaxWidth().height(44.dp).testTag("settings_save_cf_global_key"),
            ) {
              if (uiState.isTestingCfCredentials) {
                EdgeLoadingIndicator(size = 16.dp, color = scheme.onPrimary)
                Spacer(Modifier.width(8.dp))
              }
              Text("Save Global Key", fontWeight = FontWeight.SemiBold)
            }
          }

          else -> {
            if (uiState.isWaitingOAuthCallback) {
              OutlinedButton(
                onClick = onCancelOAuth,
                shape = RoundedCornerShape(Ca.radius.control),
                modifier = Modifier.fillMaxWidth().height(44.dp),
              ) {
                EdgeLoadingIndicator(size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text("Cancel OAuth")
              }
            } else {
              Button(
                onClick = onStartOAuth,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                shape = RoundedCornerShape(Ca.radius.control),
                modifier = Modifier.fillMaxWidth().height(44.dp).testTag("settings_cf_oauth_button"),
              ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                  text = if (creds == null) "Authorize OAuth" else "Re-authorize OAuth",
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }
      }
    }
  }
}
