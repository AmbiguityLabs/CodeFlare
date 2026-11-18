package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
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
import com.example.data.security.EditorSettings
import com.example.ui.components.AppearanceStudioContent
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.rememberGlassBorderStroke
import com.example.ui.viewmodel.IdeUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  uiState: IdeUiState,
  onBack: () -> Unit,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onSelectAccount: (CfAccount) -> Unit,
  onVerifyCredentials: () -> Unit,
  onUpdateApiToken: (String) -> Unit,
  onUpdateGlobalKey: (String, String) -> Unit,
  onStartOAuth: () -> Unit,
  onCancelOAuth: () -> Unit,
  onClearCredentialFeedback: () -> Unit,
  onDisconnectCloudflare: () -> Unit,
  onOpenGitHubHub: (() -> Unit)? = null,
  onClearAutoSavedDrafts: (() -> Unit)? = null,
  onRequestStorageAccess: (() -> Unit)? = null,
  onPickEditorBackgroundImage: (Uri) -> Unit = {},
  onClearEditorBackgroundImage: () -> Unit = {},
  onNotify: (String) -> Unit,
) {
  val palette = LocalIdePalette.current
  val clipboard = LocalClipboardManager.current
  val settings = uiState.editorSettings
  val creds = uiState.credentials

  BackHandler(onBack = onBack)

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
  var compatDateDraft by rememberSaveable(settings.defaultCompatibilityDate) {
    mutableStateOf(settings.defaultCompatibilityDate)
  }
  var confirmDisconnect by remember { mutableStateOf(false) }

  if (confirmDisconnect) {
    AlertDialog(
      onDismissRequest = { confirmDisconnect = false },
      title = { Text("Disconnect Cloudflare?") },
      text = {
        Text(
          "This clears your encrypted Cloudflare API credentials and active session cache on this device.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDisconnect = false
            onDisconnectCloudflare()
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Disconnect")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") }
      },
    )
  }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(palette.chromeBg)
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding(),
  ) {
    // Top Header Bar
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeBg)
          .padding(horizontal = 12.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        onClick = onBack,
        shape = CircleShape,
        color = palette.glassElevated,
        border = rememberGlassBorderStroke(1.dp),
        modifier = Modifier.size(40.dp).testTag("settings_back_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            tint = palette.textPrimary,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Settings",
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold,
          color = palette.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "Credentials & editor",
          style = MaterialTheme.typography.labelSmall,
          color = palette.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      val statusLabel =
        when {
          creds != null -> "CONNECTED"
          uiState.isGitHubIdeOnlyMode -> "GITHUB IDE"
          else -> "LOCAL DIR"
        }
      val statusColor = if (creds != null) EmeraldLive else AmberWarn
      Surface(
        color = statusColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
      ) {
        Text(
          text = statusLabel,
          fontFamily = JetBrainsMono,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = statusColor,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.45f), thickness = 0.75.dp)

    val mainScrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    var appearanceY by remember { mutableIntStateOf(0) }
    var editorY by remember { mutableIntStateOf(0) }
    var credentialsY by remember { mutableIntStateOf(0) }
    var deployY by remember { mutableIntStateOf(0) }
    var storageY by remember { mutableIntStateOf(0) }

    // Slim Quick-Jump Category Strip
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeBg)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      listOf(
        "Appearance" to appearanceY,
        "Editor" to editorY,
        "Credentials" to credentialsY,
        "Deploy" to deployY,
        "Storage" to storageY,
      ).forEachIndexed { idx, (label, targetY) ->
        Surface(
          onClick = { scrollScope.launch { mainScrollState.animateScrollTo(targetY.coerceAtLeast(0)) } },
          shape = RoundedCornerShape(999.dp),
          color = if (idx == 0) palette.activePillBg else palette.chromeSurface,
          border =
            BorderStroke(
              0.75.dp,
              if (idx == 0) palette.activePillFg.copy(alpha = 0.35f) else palette.border.copy(alpha = 0.55f),
            ),
          modifier = Modifier.testTag("settings_jump_${label.lowercase()}"),
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Medium,
            color = if (idx == 0) palette.activePillFg else palette.textSecondary,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
          )
        }
      }
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

    // Scrollable Settings Body
    Box(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(mainScrollState)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // 0. FLAGSHIP APPEARANCE & CUSTOM EDITOR BACKGROUND STUDIO
        SettingsSectionCard(
          title = "Appearance & Background",
          subtitle = "Themes, custom editor wallpaper, opacity & zoom",
          icon = Icons.Outlined.Palette,
          modifier =
            Modifier
              .onGloballyPositioned { appearanceY = it.positionInParent().y.roundToInt() }
              .testTag("settings_appearance_section"),
        ) {
          AppearanceStudioContent(
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onPickEditorBackgroundImage = onPickEditorBackgroundImage,
            onClearEditorBackgroundImage = onClearEditorBackgroundImage,
          )
        }

        // 1. CLOUDFLARE API CREDENTIALS FOR WORKER DEPLOYMENT
        SettingsSectionCard(
          title = "Cloudflare Credentials",
          subtitle = "Token, Global Key, or OAuth",
          icon = Icons.Outlined.Key,
          modifier =
            Modifier
              .onGloballyPositioned { credentialsY = it.positionInParent().y.roundToInt() }
              .testTag("settings_cloudflare_section"),
        ) {
          // Active session status banner
          Surface(
            color = palette.editorBg,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = if (creds != null) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                  contentDescription = null,
                  tint = if (creds != null) EmeraldLive else AmberWarn,
                  modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
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
                    color = palette.textPrimary,
                  )
                  Text(
                    text =
                      when {
                        creds == null -> "Add credentials below to deploy."
                        creds.email.isNotBlank() -> creds.email
                        uiState.selectedAccount != null ->
                          uiState.selectedAccount.name.replace(Regex("'s\\s+Account$", RegexOption.IGNORE_CASE), "")
                        else -> "Stored in Keystore"
                      },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }

              if (creds != null) {
                HorizontalDivider(color = palette.border.copy(alpha = 0.35f))
                val maskedToken =
                  remember(creds.accessToken, revealActiveSecret) {
                    if (revealActiveSecret) creds.accessToken
                    else {
                      val tail = creds.accessToken.takeLast(6)
                      "cf_••••••••••••$tail"
                    }
                  }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "ACTIVE CREDENTIAL",
                      fontFamily = JetBrainsMono,
                      fontSize = 9.5.sp,
                      color = palette.textMuted,
                    )
                    Text(
                      text = maskedToken,
                      fontFamily = JetBrainsMono,
                      fontSize = 11.5.sp,
                      color = palette.textPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  IconButton(
                    onClick = { revealActiveSecret = !revealActiveSecret },
                    modifier = Modifier.size(32.dp),
                  ) {
                    Icon(
                      imageVector = if (revealActiveSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                      contentDescription = if (revealActiveSecret) "Hide credential" else "Reveal credential",
                      tint = palette.textSecondary,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                  IconButton(
                    onClick = {
                      clipboard.setText(AnnotatedString(creds.accessToken))
                      onNotify("Copied credential to clipboard")
                    },
                    modifier = Modifier.size(32.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.ContentCopy,
                      contentDescription = "Copy credential",
                      tint = palette.textSecondary,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }

                uiState.selectedAccount?.let { acc ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "DEPLOY ACCOUNT ID",
                        fontFamily = JetBrainsMono,
                        fontSize = 9.5.sp,
                        color = palette.textMuted,
                      )
                      Text(
                        text = "${acc.name} (${acc.id})",
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = palette.activePillFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    IconButton(
                      onClick = {
                        clipboard.setText(AnnotatedString(acc.id))
                        onNotify("Copied Account ID")
                      },
                      modifier = Modifier.size(32.dp),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy Account ID",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }
                }
              }

              // Action Buttons for active credentials
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                if (creds != null) {
                  OutlinedButton(
                    onClick = onVerifyCredentials,
                    enabled = !uiState.isTestingCfCredentials,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("settings_test_cf_connection"),
                  ) {
                    if (uiState.isTestingCfCredentials) {
                      CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                      Spacer(Modifier.width(6.dp))
                    } else {
                      Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(15.dp))
                      Spacer(Modifier.width(5.dp))
                    }
                    Text("Verify", fontSize = 12.sp)
                  }
                }
                OutlinedButton(
                  onClick = {
                    onClearCredentialFeedback()
                    showCredentialEditor = !showCredentialEditor
                  },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f).height(38.dp).testTag("settings_toggle_credential_editor"),
                ) {
                  Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(Modifier.width(5.dp))
                  Text(if (creds == null) "Configure" else if (showCredentialEditor) "Hide Form" else "Update Key", fontSize = 12.sp)
                }
              }
            }
          }

          // Live Verification / Save Feedback Banner
          uiState.cfCredentialTestMessage?.let { msg ->
            val msgColor = if (uiState.isCfCredentialTestError) RoseError else EmeraldLive
            Surface(
              color = msgColor.copy(alpha = 0.12f),
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, msgColor.copy(alpha = 0.35f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector =
                    if (uiState.isCfCredentialTestError) Icons.Outlined.ErrorOutline
                    else Icons.Outlined.CheckCircleOutline,
                  contentDescription = null,
                  tint = msgColor,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodySmall,
                  color = palette.textPrimary,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }

          // Multi-Account Switcher (when 2+ accounts exist)
          if (uiState.accounts.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "ACTIVE DEPLOYMENT ACCOUNT",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = palette.textMuted,
              )
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                uiState.accounts.forEach { acc ->
                  val isSelected = acc.id == uiState.selectedAccount?.id
                  Surface(
                    onClick = { onSelectAccount(acc) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) palette.activePillBg else palette.editorBg,
                    border =
                      BorderStroke(
                        1.dp,
                        if (isSelected) palette.activePillFg.copy(alpha = 0.35f) else palette.border.copy(alpha = 0.5f),
                      ),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                      if (isSelected) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(13.dp))
                      }
                      Text(
                        text = acc.name,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) palette.activePillFg else palette.textPrimary,
                      )
                    }
                  }
                }
              }
            }
          }

          // Update / Configure Cloudflare Credential Editor
          if (showCredentialEditor) {
            Surface(
              color = palette.editorBg,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  listOf("API Token", "Global Key", "OAuth").forEachIndexed { idx, label ->
                    val selected = credentialTab == idx
                    Surface(
                      onClick = { credentialTab = idx },
                      shape = RoundedCornerShape(7.dp),
                      color = if (selected) palette.activePillBg else palette.chromeSurface,
                      border =
                        BorderStroke(
                          1.dp,
                          if (selected) palette.activePillFg.copy(alpha = 0.3f) else palette.border.copy(alpha = 0.45f),
                        ),
                      modifier = Modifier.weight(1f).height(32.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text(
                          text = label,
                          fontSize = 11.sp,
                          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                          color = if (selected) palette.activePillFg else palette.textSecondary,
                        )
                      }
                    }
                  }
                }

                when (credentialTab) {
                  0 -> {
                    Text(
                      text = "Recommended: Scoped Cloudflare API Token with Workers Scripts, KV, D1, R2, and Pages Edit permissions.",
                      style = MaterialTheme.typography.bodySmall,
                      color = palette.textSecondary,
                    )
                    OutlinedTextField(
                      value = apiTokenInput,
                      onValueChange = { apiTokenInput = it },
                      label = { Text("Cloudflare API Token") },
                      placeholder = { Text("Paste Bearer token…") },
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
                      keyboardActions = KeyboardActions(
                        onDone = {
                          if (apiTokenInput.isNotBlank() && !uiState.isTestingCfCredentials) {
                            onUpdateApiToken(apiTokenInput)
                          }
                        },
                      ),
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.fillMaxWidth().testTag("settings_cf_token_input"),
                    )
                    Button(
                      onClick = { onUpdateApiToken(apiTokenInput) },
                      enabled = apiTokenInput.isNotBlank() && !uiState.isTestingCfCredentials,
                      colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth().height(46.dp).testTag("settings_save_cf_token"),
                    ) {
                      if (uiState.isTestingCfCredentials) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.ctaFg)
                        Spacer(Modifier.width(8.dp))
                      }
                      Text("Verify & Save API Token", fontWeight = FontWeight.SemiBold)
                    }
                  }

                  1 -> {
                    Text(
                      text = "Account email and Global API Key from My Profile -> API Tokens in Cloudflare Dashboard.",
                      style = MaterialTheme.typography.bodySmall,
                      color = palette.textSecondary,
                    )
                    OutlinedTextField(
                      value = globalEmailInput,
                      onValueChange = { globalEmailInput = it },
                      label = { Text("Cloudflare Account Email") },
                      placeholder = { Text("dev@example.com") },
                      singleLine = true,
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.fillMaxWidth().testTag("settings_cf_email_input"),
                    )
                    OutlinedTextField(
                      value = globalKeyInput,
                      onValueChange = { globalKeyInput = it },
                      label = { Text("Global API Key") },
                      placeholder = { Text("37-character Global Key…") },
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
                      keyboardActions = KeyboardActions(
                        onDone = {
                          if (globalEmailInput.isNotBlank() && globalKeyInput.isNotBlank() && !uiState.isTestingCfCredentials) {
                            onUpdateGlobalKey(globalEmailInput, globalKeyInput)
                          }
                        },
                      ),
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.fillMaxWidth().testTag("settings_cf_global_key_input"),
                    )
                    Button(
                      onClick = { onUpdateGlobalKey(globalEmailInput, globalKeyInput) },
                      enabled = globalEmailInput.isNotBlank() && globalKeyInput.isNotBlank() && !uiState.isTestingCfCredentials,
                      colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
                      shape = RoundedCornerShape(14.dp),
                      modifier = Modifier.fillMaxWidth().height(46.dp).testTag("settings_save_cf_global_key"),
                    ) {
                      if (uiState.isTestingCfCredentials) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.ctaFg)
                        Spacer(Modifier.width(8.dp))
                      }
                      Text("Verify & Save Global Key", fontWeight = FontWeight.SemiBold)
                    }
                  }

                  else -> {
                    Text(
                      text = "Authorize via Cloudflare OAuth 2.0 PKCE in your browser with automatic token refresh.",
                      style = MaterialTheme.typography.bodySmall,
                      color = palette.textSecondary,
                    )
                    if (uiState.isWaitingOAuthCallback) {
                      OutlinedButton(
                        onClick = onCancelOAuth,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                      ) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Waiting for browser callback · Cancel")
                      }
                    } else {
                      Button(
                        onClick = onStartOAuth,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("settings_cf_oauth_button"),
                      ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                          text = if (creds == null) "Authorize with Cloudflare OAuth" else "Re-authorize OAuth Session",
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

        // 2. WORKER DEPLOYMENT DEFAULTS
        SettingsSectionCard(
          title = "Deployment Defaults",
          subtitle = "New Worker scaffold & publish",
          icon = Icons.Outlined.RocketLaunch,
          modifier =
            Modifier
              .onGloballyPositioned { deployY = it.positionInParent().y.roundToInt() }
              .testTag("settings_deploy_defaults_section"),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = compatDateDraft,
                onValueChange = { compatDateDraft = it },
                label = { Text("Compatibility Date") },
                placeholder = { Text("2024-11-01") },
                singleLine = true,
                textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).testTag("settings_compat_date_input"),
              )
              Button(
                onClick = {
                  val clean = compatDateDraft.trim().ifBlank { "2024-11-01" }
                  compatDateDraft = clean
                  onUpdateSettings { it.copy(defaultCompatibilityDate = clean) }
                  onNotify("Compatibility date saved: $clean")
                },
                enabled = compatDateDraft.trim().isNotEmpty() && compatDateDraft.trim() != settings.defaultCompatibilityDate,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(50.dp),
              ) {
                Text("Save")
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              listOf("2024-11-01", "2025-05-01", "2026-01-01").forEach { preset ->
                val isPreset = settings.defaultCompatibilityDate == preset
                Surface(
                  onClick = {
                    compatDateDraft = preset
                    onUpdateSettings { it.copy(defaultCompatibilityDate = preset) }
                  },
                  shape = RoundedCornerShape(6.dp),
                  color = if (isPreset) palette.activePillBg else palette.editorBg,
                  border =
                    BorderStroke(
                      1.dp,
                      if (isPreset) palette.activePillFg.copy(alpha = 0.3f) else palette.border.copy(alpha = 0.45f),
                    ),
                ) {
                  Text(
                    text = preset,
                    fontFamily = JetBrainsMono,
                    fontSize = 10.5.sp,
                    fontWeight = if (isPreset) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isPreset) palette.activePillFg else palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
            }
          }

          HorizontalDivider(color = palette.border.copy(alpha = 0.35f))

          SettingsSwitchRow(
            title = "Default nodejs_compat Flag",
            subtitle = "Node.js APIs on new Workers",
            checked = settings.defaultNodejsCompat,
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(defaultNodejsCompat = enabled) } },
          )

          SettingsSwitchRow(
            title = "workers.dev Subdomain",
            subtitle = "Expose new Workers by default",
            checked = settings.defaultWorkersDevEnabled,
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(defaultWorkersDevEnabled = enabled) } },
          )

          SettingsSwitchRow(
            title = "Auto-Open Preview",
            subtitle = "Jump to preview after deploy",
            checked = settings.autoOpenPreviewOnDeploy,
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(autoOpenPreviewOnDeploy = enabled) } },
          )
        }

        // 3. CODE EDITOR TYPOGRAPHY & BEHAVIOR
        SettingsSectionCard(
          title = "Editor Behavior & Typography",
          subtitle = "Font size, indentation, wrapping & auto-save",
          icon = Icons.Outlined.Code,
          modifier =
            Modifier
              .onGloballyPositioned { editorY = it.positionInParent().y.roundToInt() }
              .testTag("settings_editor_section"),
        ) {

          SettingsSwitchRow(
            title = "Word Wrap",
            subtitle = "Wrap long source lines",
            checked = settings.wordWrap,
            onCheckedChange = { wrap -> onUpdateSettings { it.copy(wordWrap = wrap) } },
          )
          SettingsSwitchRow(
            title = "Line Numbers",
            subtitle = "Show line numbers gutter",
            checked = settings.showLineNumbers,
            onCheckedChange = { ln -> onUpdateSettings { it.copy(showLineNumbers = ln) } },
          )
          SettingsSwitchRow(
            title = "Auto-Close Brackets",
            subtitle = "Insert matching pairs automatically",
            checked = settings.autoCloseBrackets,
            onCheckedChange = { auto -> onUpdateSettings { it.copy(autoCloseBrackets = auto) } },
          )

          HorizontalDivider(color = palette.border.copy(alpha = 0.35f))

          SettingsSwitchRow(
            title = "Periodic Auto-Save",
            subtitle = "Persist open editor buffers to DataStore",
            checked = settings.autoSaveEnabled,
            onCheckedChange = { enabled -> onUpdateSettings { it.copy(autoSaveEnabled = enabled) } },
          )

          if (settings.autoSaveEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Auto-Save Interval", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                Text(
                  text = "Every ${settings.autoSaveIntervalSeconds}s",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 15, 30).forEach { seconds ->
                  val selected = settings.autoSaveIntervalSeconds == seconds
                  Surface(
                    onClick = { onUpdateSettings { it.copy(autoSaveIntervalSeconds = seconds) } },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) palette.activePillBg else palette.editorBg,
                    border =
                      BorderStroke(
                        1.dp,
                        if (selected) palette.activePillFg.copy(alpha = 0.35f) else palette.border.copy(alpha = 0.5f),
                      ),
                    modifier = Modifier.weight(1f).height(36.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = "${seconds}s",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) palette.activePillFg else palette.textPrimary,
                      )
                    }
                  }
                }
              }
            }
          }

          if (onClearAutoSavedDrafts != null) {
            OutlinedButton(
              onClick = onClearAutoSavedDrafts,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Clear Saved Editor Drafts")
            }
          }

          HorizontalDivider(color = palette.border.copy(alpha = 0.35f))

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Code Font Size", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
              Surface(
                color = palette.activePillBg,
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(
                  text = "${settings.fontSizeSp} sp",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
            Slider(
              value = settings.fontSizeSp.toFloat(),
              onValueChange = { v -> onUpdateSettings { it.copy(fontSizeSp = v.toInt()) } },
              valueRange = 10f..18f,
              steps = 7,
            )
            // Live font & indentation preview
            Surface(
              color = palette.editorBg,
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              val indent = " ".repeat(settings.tabSize)
              Text(
                text = "export default {\n${indent}async fetch(req) {\n${indent}${indent}return Response.json({ ok: true });\n${indent}}\n};",
                fontFamily = JetBrainsMono,
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp + 6).sp,
                color = palette.textPrimary,
                modifier = Modifier.padding(10.dp),
              )
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tab Indentation Width", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(2, 4).forEach { spaces ->
                val selected = settings.tabSize == spaces
                Surface(
                  onClick = { onUpdateSettings { it.copy(tabSize = spaces) } },
                  shape = RoundedCornerShape(10.dp),
                  color = if (selected) palette.activePillBg else palette.editorBg,
                  border =
                    BorderStroke(
                      1.dp,
                      if (selected) palette.activePillFg.copy(alpha = 0.35f) else palette.border.copy(alpha = 0.5f),
                    ),
                  modifier = Modifier.weight(1f).height(40.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                  ) {
                    if (selected) {
                      Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = palette.activePillFg,
                        modifier = Modifier.size(15.dp),
                      )
                      Spacer(Modifier.width(5.dp))
                    }
                    Text(
                      text = "$spaces Spaces",
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                      color = if (selected) palette.activePillFg else palette.textPrimary,
                    )
                  }
                }
              }
            }
          }
        }

        // 4. DEDICATED PROJECT DIRECTORY & ORGANIZED STORAGE
        val storage = uiState.projectStorageSummary
        SettingsSectionCard(
          title = "Project Directory & Storage",
          subtitle =
            if (storage.isUsingNativeDeviceStorage) "Dedicated native phone folder active"
            else "Grant native folder access to store outside app cache",
          icon = Icons.Outlined.Folder,
          modifier =
            Modifier
              .onGloballyPositioned { storageY = it.positionInParent().y.roundToInt() }
              .testTag("settings_storage_section"),
        ) {
          Surface(
            color = palette.editorBg,
            shape = RoundedCornerShape(10.dp),
            border = rememberGlassBorderStroke(1.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = if (storage.isUsingNativeDeviceStorage) "DEDICATED DEVICE FOLDER" else "ACTIVE DIRECTORY",
                  fontFamily = JetBrainsMono,
                  fontSize = 9.5.sp,
                  color = palette.textMuted,
                )
                Surface(
                  color =
                    if (storage.hasNativeStorageAccess) EmeraldLive.copy(alpha = 0.16f)
                    else AmberWarn.copy(alpha = 0.16f),
                  shape = RoundedCornerShape(6.dp),
                ) {
                  Text(
                    text = if (storage.hasNativeStorageAccess) "Native Access" else "App Scoped",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (storage.hasNativeStorageAccess) EmeraldLive else AmberWarn,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                  )
                }
              }
              Text(
                text =
                  storage.rootAbsolutePath.ifBlank {
                    storage.rootDisplayPath.ifBlank { "EdgeCodeProjects/ (projects · imports · exports)" }
                  },
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = palette.activePillFg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              if (!storage.hasNativeStorageAccess && onRequestStorageAccess != null) {
                Button(
                  onClick = onRequestStorageAccess,
                  shape = RoundedCornerShape(10.dp),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = palette.ctaBg,
                      contentColor = palette.ctaFg,
                    ),
                  modifier = Modifier.fillMaxWidth().height(40.dp).testTag("settings_grant_storage_button"),
                ) {
                  Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(8.dp))
                  Text("Grant Folder Access (Phone Storage/EdgeCode)", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
              }
              HorizontalDivider(color = palette.border.copy(alpha = 0.35f))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                StorageStatBox(
                  label = "Projects",
                  value = storage.projectCount.toString(),
                  modifier = Modifier.weight(1f),
                )
                StorageStatBox(
                  label = "Imports",
                  value = storage.importedFilesCount.toString(),
                  modifier = Modifier.weight(1f),
                )
                StorageStatBox(
                  label = "Exports",
                  value = storage.exportedFilesCount.toString(),
                  modifier = Modifier.weight(1f),
                )
                StorageStatBox(
                  label = "Disk",
                  value = "${storage.totalDiskKb} KB",
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }

        // 5. OPTIONAL GITHUB & SESSION CONTROLS
        SettingsSectionCard(
          title = "Integrations & Session",
          subtitle = "GitHub sync & sign-out",
          icon = Icons.Outlined.Tune,
        ) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.editorBg)
                .then(if (onOpenGitHubHub != null) Modifier.clickable(onClick = onOpenGitHubHub) else Modifier)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.AccountTree,
              contentDescription = null,
              tint = if (uiState.gitHubCreds != null) EmeraldLive else palette.textSecondary,
              modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "GitHub Sync",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
              )
              Text(
                text =
                  uiState.gitHubCreds?.login?.let { "Connected as @$it" }
                    ?: "Not connected (optional)",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
              )
            }
            if (onOpenGitHubHub != null) {
              TextButton(onClick = onOpenGitHubHub) {
                Text("Manage")
              }
            }
          }

          if (creds != null) {
            OutlinedButton(
              onClick = { confirmDisconnect = true },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().height(44.dp).testTag("sign_out_button"),
            ) {
              Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(17.dp))
              Spacer(Modifier.width(8.dp))
              Text("Disconnect Cloudflare Credentials")
            }
          } else {
            OutlinedButton(
              onClick = onDisconnectCloudflare,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().height(44.dp).testTag("sign_out_button"),
            ) {
              Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(17.dp))
              Spacer(Modifier.width(8.dp))
              Text("Open Auth Setup Screen")
            }
          }
        }

        Spacer(Modifier.height(12.dp))
      }
    }
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    color = palette.chromeSurface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.65f)),
    modifier = modifier.fillMaxWidth().animateContentSize(animationSpec = tween(180)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          color = palette.activePillBg,
          shape = RoundedCornerShape(9.dp),
          border = BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.25f)),
          modifier = Modifier.size(34.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = palette.activePillFg,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
          )
        }
      }
      content()
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  val palette = LocalIdePalette.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        color = palette.textPrimary,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = palette.textMuted,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
    )
  }
}

@Composable
private fun StorageStatBox(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  Surface(
    color = palette.chromeSurface,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = value,
        fontFamily = JetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary,
        maxLines = 1,
      )
      Text(
        text = label,
        fontSize = 10.sp,
        color = palette.textMuted,
        maxLines = 1,
      )
    }
  }
}
