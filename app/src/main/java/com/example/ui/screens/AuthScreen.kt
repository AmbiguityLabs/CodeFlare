package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.auth.CloudflareOAuthManager
import com.example.data.auth.GitHubOAuthManager
import com.example.ui.theme.JetBrainsMono
import com.example.ui.viewmodel.IdeUiState

@Composable
fun AuthScreen(
  uiState: IdeUiState,
  onLaunchUrl: (String) -> Unit,
  onStartOAuth: () -> Unit,
  onCancelOAuth: () -> Unit,
  onAuthorizeToken: (String) -> Unit,
  onAuthorizeGlobalKey: (String, String) -> Unit,
  onStartGitHubOAuth: (String) -> Unit,
  onCancelGitHubOAuth: () -> Unit,
  onAuthorizeGitHubPat: (String) -> Unit,
  onDisconnectGitHub: () -> Unit,
  onEnterGitHubIde: () -> Unit,
  onOpenLocalScratchpad: () -> Unit,
) {
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var apiTokenInput by rememberSaveable { mutableStateOf("") }
  var showToken by rememberSaveable { mutableStateOf(false) }
  var emailInput by rememberSaveable { mutableStateOf("") }
  var globalKeyInput by rememberSaveable { mutableStateOf("") }
  var showGlobalKey by rememberSaveable { mutableStateOf(false) }

  var ghAuthMode by rememberSaveable { mutableIntStateOf(0) }
  var ghPatInput by rememberSaveable { mutableStateOf("") }
  var showGhPat by rememberSaveable { mutableStateOf(false) }
  var ghCustomClientId by rememberSaveable { mutableStateOf("") }
  var showGhClientIdField by rememberSaveable { mutableStateOf(false) }
  var showAuthInfo by rememberSaveable { mutableStateOf(false) }
  var showGhInfo by rememberSaveable { mutableStateOf(false) }

  val clipboard = LocalClipboardManager.current
  val globalKeyFocusRequester = remember { FocusRequester() }

  val tabs =
    listOf(
      stringResource(R.string.auth_tab_oauth),
      stringResource(R.string.auth_tab_token),
      stringResource(R.string.auth_tab_global),
    )

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .imePadding(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier
          .widthIn(max = 520.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(24.dp)),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_auth_hero),
          contentDescription = stringResource(R.string.cd_auth_hero),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .background(
                Brush.verticalGradient(
                  listOf(Color(0x550E1015), Color(0xDD0E1015))
                )
              )
              .padding(18.dp),
          contentAlignment = Alignment.BottomStart,
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
              shape = RoundedCornerShape(999.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.CloudDone,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(13.dp),
                )
                Text(
                  text = stringResource(R.string.app_name),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
            Text(
              text = stringResource(R.string.auth_title),
              style = MaterialTheme.typography.headlineMedium,
              color = Color.White,
            )
          }
        }
      }

      ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors =
          CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
              tabs.forEachIndexed { index, title ->
                SegmentedButton(
                  selected = selectedTab == index,
                  onClick = { selectedTab = index },
                  shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                  modifier = Modifier.testTag("auth_tab_$index"),
                ) {
                  Text(title, maxLines = 1)
                }
              }
            }
            IconButton(
              onClick = { showAuthInfo = !showAuthInfo },
              modifier = Modifier.size(34.dp).testTag("auth_info_toggle"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Auth help",
                tint = if (showAuthInfo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          AnimatedVisibility(visible = showAuthInfo) {
            Text(
              text = stringResource(R.string.auth_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          when (selectedTab) {
            0 -> {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(visible = showAuthInfo) {
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                  ) {
                    Row(
                      modifier = Modifier.padding(12.dp),
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text =
                          "OAuth 2.0 PKCE via localhost:8976.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }

                if (uiState.isWaitingOAuthCallback) {
                  Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(
                      modifier = Modifier.padding(14.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                      ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                          text = stringResource(R.string.auth_oauth_waiting),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                      }
                      TextButton(
                        onClick = onCancelOAuth,
                        modifier = Modifier.align(Alignment.End).testTag("cancel_oauth_button"),
                      ) {
                        Text(stringResource(R.string.auth_cancel))
                      }
                    }
                  }
                } else {
                  Button(
                    onClick = onStartOAuth,
                    enabled = !uiState.isAuthenticating,
                    shape = RoundedCornerShape(14.dp),
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("oauth_signin_button"),
                  ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                      text = stringResource(R.string.auth_oauth_btn),
                      fontWeight = FontWeight.SemiBold,
                    )
                  }
                }
              }
            }

            1 -> {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                  onClick = { onLaunchUrl(CloudflareOAuthManager.buildScopedTokenDashboardUrl()) },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().height(48.dp).testTag("create_scoped_token_link"),
                ) {
                  Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text(stringResource(R.string.auth_generate_token))
                }

                OutlinedTextField(
                  value = apiTokenInput,
                  onValueChange = { apiTokenInput = it },
                  label = { Text(stringResource(R.string.auth_token_label)) },
                  placeholder = { Text(stringResource(R.string.auth_token_hint)) },
                  singleLine = true,
                  leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                  trailingIcon = {
                    Row {
                      IconButton(
                        onClick = {
                          clipboard.getText()?.text?.let { pasted ->
                            apiTokenInput = pasted.trim()
                          }
                        },
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.ContentPaste,
                          contentDescription = "Paste API Token",
                        )
                      }
                      IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                          imageVector = if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                          contentDescription = "Toggle token visibility",
                        )
                      }
                    }
                  },
                  visualTransformation =
                    if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                  keyboardOptions =
                    KeyboardOptions(
                      keyboardType = KeyboardType.Ascii,
                      imeAction = ImeAction.Done,
                    ),
                  keyboardActions =
                    KeyboardActions(
                      onDone = {
                        if (apiTokenInput.isNotBlank() && !uiState.isAuthenticating) {
                          onAuthorizeToken(apiTokenInput)
                        }
                      },
                    ),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("api_token_input"),
                )

                Button(
                  onClick = { onAuthorizeToken(apiTokenInput) },
                  enabled = !uiState.isAuthenticating && apiTokenInput.isNotBlank(),
                  shape = RoundedCornerShape(14.dp),
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .height(50.dp)
                      .testTag("verify_token_button"),
                ) {
                  if (uiState.isAuthenticating) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(20.dp),
                      strokeWidth = 2.dp,
                      color = MaterialTheme.colorScheme.onPrimary,
                    )
                  } else {
                    Text(stringResource(R.string.auth_verify_connect), fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }

            2 -> {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                  value = emailInput,
                  onValueChange = { emailInput = it },
                  label = { Text(stringResource(R.string.auth_email_label)) },
                  placeholder = { Text("you@gmail.com") },
                  singleLine = true,
                  keyboardOptions =
                    KeyboardOptions(
                      keyboardType = KeyboardType.Email,
                      imeAction = ImeAction.Next,
                    ),
                  keyboardActions =
                    KeyboardActions(
                      onNext = { globalKeyFocusRequester.requestFocus() },
                    ),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("global_email_input"),
                )

                OutlinedTextField(
                  value = globalKeyInput,
                  onValueChange = { globalKeyInput = it },
                  label = { Text(stringResource(R.string.auth_global_key_label)) },
                  placeholder = { Text("Paste or type 37-char Global API Key") },
                  singleLine = true,
                  leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                  trailingIcon = {
                    Row {
                      IconButton(
                        onClick = {
                          clipboard.getText()?.text?.let { pasted ->
                            globalKeyInput = pasted.trim()
                          }
                        },
                        modifier = Modifier.testTag("paste_global_key_button"),
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.ContentPaste,
                          contentDescription = "Paste Global API Key",
                        )
                      }
                      IconButton(onClick = { showGlobalKey = !showGlobalKey }) {
                        Icon(
                          imageVector = if (showGlobalKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                          contentDescription = "Toggle key visibility",
                        )
                      }
                    }
                  },
                  visualTransformation =
                    if (showGlobalKey) VisualTransformation.None else PasswordVisualTransformation(),
                  keyboardOptions =
                    KeyboardOptions(
                      keyboardType = KeyboardType.Ascii,
                      imeAction = ImeAction.Done,
                    ),
                  keyboardActions =
                    KeyboardActions(
                      onDone = {
                        if (
                          emailInput.isNotBlank() &&
                            globalKeyInput.isNotBlank() &&
                            !uiState.isAuthenticating
                        ) {
                          onAuthorizeGlobalKey(emailInput, globalKeyInput)
                        }
                      },
                    ),
                  shape = RoundedCornerShape(14.dp),
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .focusRequester(globalKeyFocusRequester)
                      .testTag("global_key_input"),
                )

                TextButton(
                  onClick = { onLaunchUrl("https://dash.cloudflare.com/profile/api-tokens") },
                  modifier = Modifier.align(Alignment.End),
                ) {
                  Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("View Global API Key in Cloudflare", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                  onClick = { onAuthorizeGlobalKey(emailInput, globalKeyInput) },
                  enabled =
                    !uiState.isAuthenticating &&
                      emailInput.isNotBlank() &&
                      globalKeyInput.isNotBlank(),
                  shape = RoundedCornerShape(14.dp),
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .height(50.dp)
                      .testTag("verify_global_key_button"),
                ) {
                  if (uiState.isAuthenticating) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(20.dp),
                      strokeWidth = 2.dp,
                      color = MaterialTheme.colorScheme.onPrimary,
                    )
                  } else {
                    Text(stringResource(R.string.auth_verify_connect), fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }
          }

          AnimatedVisibility(visible = !uiState.authError.isNullOrBlank()) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = uiState.authError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
              )
            }
          }
        }
      }

      OutlinedButton(
        onClick = onOpenLocalScratchpad,
        shape = RoundedCornerShape(16.dp),
        modifier =
          Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("open_local_scratchpad_button"),
      ) {
        Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Local Projects", fontWeight = FontWeight.Medium)
      }

      var showOptionalGitHub by rememberSaveable { mutableStateOf(false) }
      TextButton(
        onClick = { showOptionalGitHub = !showOptionalGitHub },
        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("toggle_optional_github_auth"),
      ) {
        Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          text =
            if (uiState.gitHubCreds != null) "@${uiState.gitHubCreds.login}"
            else "GitHub",
          style = MaterialTheme.typography.labelMedium,
        )
      }

      AnimatedVisibility(visible = showOptionalGitHub) {
        ElevatedCard(
          shape = RoundedCornerShape(24.dp),
          colors =
            CardDefaults.elevatedCardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
          elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = "GitHub",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = { showGhInfo = !showGhInfo },
                modifier = Modifier.size(30.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Info,
                  contentDescription = "GitHub info",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
            AnimatedVisibility(visible = showGhInfo) {
              Text(
                text = "Repos, branches, commits & PRs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            if (uiState.gitHubCreds != null) {
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  val avatarUrl = uiState.gitHubUser?.avatarUrl ?: uiState.gitHubCreds.avatarUrl
                  if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                      model = avatarUrl,
                      contentDescription = "GitHub avatar for @${uiState.gitHubCreds.login}",
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Outlined.VerifiedUser,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "@${uiState.gitHubCreds.login}",
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = "${uiState.gitHubCreds.method.name} · ${uiState.gitHubCreds.scopes.ifBlank { "repo, workflow" }}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                  }
                  TextButton(onClick = onDisconnectGitHub) {
                    Text("Sign Out")
                  }
                }
              }

              Button(
                onClick = onEnterGitHubIde,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("open_github_ide_button"),
              ) {
                Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open GitHub Hub (@${uiState.gitHubCreds.login})", fontWeight = FontWeight.SemiBold)
              }
            } else {
              val ghModes = listOf("OAuth Device", "Access Token")
              SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ghModes.forEachIndexed { idx, label ->
                  SegmentedButton(
                    selected = ghAuthMode == idx,
                    onClick = { ghAuthMode = idx },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = ghModes.size),
                    modifier = Modifier.testTag("github_auth_mode_$idx"),
                  ) {
                    Text(label, maxLines = 1)
                  }
                }
              }

              if (ghAuthMode == 0) {
                val deviceSession = uiState.gitHubDeviceSession
                if (uiState.isWaitingGitHubOAuth && deviceSession != null) {
                  Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(
                      modifier = Modifier.padding(14.dp),
                      verticalArrangement = Arrangement.spacedBy(10.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                      Text(
                        text = "Enter this verification code on GitHub:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                      )
                      Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                      ) {
                        Text(
                          text = deviceSession.userCode,
                          style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetBrainsMono,
                            letterSpacing = 3.sp,
                          ),
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                      }
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        FilledTonalButton(
                          onClick = {
                            clipboard.setText(AnnotatedString(deviceSession.userCode))
                            onLaunchUrl(deviceSession.verificationUri)
                          },
                          shape = RoundedCornerShape(12.dp),
                          modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                          Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(Modifier.width(6.dp))
                          Text("Copy & Open GitHub")
                        }
                        TextButton(
                          onClick = onCancelGitHubOAuth,
                          modifier = Modifier.height(48.dp),
                        ) {
                          Text("Cancel")
                        }
                      }
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                          text = "Waiting for GitHub browser authorization…",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                      }
                    }
                  }
                } else {
                  TextButton(
                    onClick = { showGhClientIdField = !showGhClientIdField },
                    modifier = Modifier.align(Alignment.End),
                  ) {
                    Text(if (showGhClientIdField) "Hide Custom Client ID" else "Custom GitHub OAuth Client ID")
                  }
                  AnimatedVisibility(visible = showGhClientIdField) {
                    OutlinedTextField(
                      value = ghCustomClientId,
                      onValueChange = { ghCustomClientId = it },
                      label = { Text("GitHub OAuth App Client ID (optional)") },
                      placeholder = { Text(GitHubOAuthManager.DEFAULT_CLIENT_ID) },
                      singleLine = true,
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }
                  FilledTonalButton(
                    onClick = { onStartGitHubOAuth(ghCustomClientId) },
                    enabled = !uiState.isGitHubBusy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("github_device_oauth_button"),
                  ) {
                    if (uiState.isWaitingGitHubOAuth) {
                      CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                      Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(8.dp))
                      Text("Authorize with GitHub OAuth", fontWeight = FontWeight.SemiBold)
                    }
                  }
                }
              } else {
                FilledTonalButton(
                  onClick = { onLaunchUrl(GitHubOAuthManager.buildClassicPatCreationUrl()) },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                  Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Generate GitHub Token (repo + workflow)")
                }
                OutlinedTextField(
                  value = ghPatInput,
                  onValueChange = { ghPatInput = it },
                  label = { Text("GitHub PAT (ghp_ or github_pat_)") },
                  placeholder = { Text("ghp_xxxxxxxxxxxxxxxxxxxx") },
                  singleLine = true,
                  leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                  trailingIcon = {
                    Row {
                      IconButton(onClick = { clipboard.getText()?.text?.let { ghPatInput = it.trim() } }) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste GitHub token")
                      }
                      IconButton(onClick = { showGhPat = !showGhPat }) {
                        Icon(
                          imageVector = if (showGhPat) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                          contentDescription = "Toggle GitHub token visibility",
                        )
                      }
                    }
                  },
                  visualTransformation = if (showGhPat) VisualTransformation.None else PasswordVisualTransformation(),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("auth_github_pat_input"),
                )
                Button(
                  onClick = { onAuthorizeGitHubPat(ghPatInput) },
                  enabled = ghPatInput.isNotBlank() && !uiState.isGitHubBusy,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().height(50.dp).testTag("auth_github_pat_submit"),
                ) {
                  if (uiState.isGitHubBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                  } else {
                    Text("Authorize & Open GitHub Hub", fontWeight = FontWeight.SemiBold)
                  }
                }
              }

              AnimatedVisibility(visible = !uiState.gitHubError.isNullOrBlank()) {
                Surface(
                  color = MaterialTheme.colorScheme.errorContainer,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = uiState.gitHubError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
