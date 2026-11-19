package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.EdgeLoadingIndicator
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IdeWorkspaceScreen
import com.example.ui.theme.BarStylePreferences
import com.example.ui.theme.EdgeCodeTheme
import com.example.ui.viewmodel.IdeViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val ideViewModel: IdeViewModel = viewModel()
      val uiState by ideViewModel.uiState.collectAsStateWithLifecycle()
      val lifecycleOwner = LocalLifecycleOwner.current

      DisposableEffect(lifecycleOwner, ideViewModel) {
        val observer =
          LifecycleEventObserver { _, event ->
            when (event) {
              Lifecycle.Event.ON_RESUME -> ideViewModel.refreshStoragePermissionAndReload()
              Lifecycle.Event.ON_STOP -> ideViewModel.flushAutoSaveNow(clearSessionPointer = false)
              else -> Unit
            }
          }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }

      EdgeCodeTheme(
        darkTheme = uiState.editorSettings.darkTheme,
        dynamicColor = uiState.editorSettings.dynamicColor,
        themePreset = com.example.ui.theme.EditorThemePreset.fromKey(uiState.editorSettings.themePreset),
        uiStyle = com.example.ui.theme.UiStyleVersion.fromKey(uiState.editorSettings.uiStyleVersion),
        barPreferences =
          BarStylePreferences(
            homeBar = uiState.editorSettings.homeBarStyle,
            editorBar = uiState.editorSettings.editorBarStyle,
            homeMenu = uiState.editorSettings.homeMenuStyle,
            editorMenu = uiState.editorSettings.editorMenuStyle,
          ),
      ) {
        when {
          uiState.isBootstrapping -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              EdgeLoadingIndicator(size = 44.dp)
            }
          }
          uiState.credentials == null && !uiState.isLocalScratchpad && !uiState.isGitHubIdeOnlyMode -> {
            AuthScreen(
              uiState = uiState,
              onLaunchUrl = { url -> openExternalUrl(url) },
              onStartOAuth = {
                ideViewModel.startOAuthSignIn { authUrl -> openExternalUrl(authUrl) }
              },
              onCancelOAuth = ideViewModel::cancelOAuthSignIn,
              onAuthorizeToken = ideViewModel::authorizeWithToken,
              onAuthorizeGlobalKey = ideViewModel::authorizeWithGlobalKey,
              onStartGitHubOAuth = { clientIdOverride ->
                ideViewModel.startGitHubDeviceOAuth(
                  clientIdOverride = clientIdOverride,
                  enterIdeOnSuccess = true,
                ) { verificationUrl -> openExternalUrl(verificationUrl) }
              },
              onCancelGitHubOAuth = ideViewModel::cancelGitHubDeviceOAuth,
              onAuthorizeGitHubPat = { pat ->
                ideViewModel.connectGitHubToken(pat, enterIdeOnSuccess = true)
              },
              onDisconnectGitHub = ideViewModel::disconnectGitHub,
              onEnterGitHubIde = ideViewModel::enterGitHubIdeMode,
              onOpenLocalScratchpad = { ideViewModel.enterLocalHome() },
            )
          }
          uiState.workspace == null -> {
            HomeScreen(
              uiState = uiState,
              viewModel = ideViewModel,
            )
          }
          else -> {
            IdeWorkspaceScreen(
              uiState = uiState,
              viewModel = ideViewModel,
            )
          }
        }
      }
    }
  }

  private fun openExternalUrl(url: String) {
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
      startActivity(intent)
    }
  }
}
