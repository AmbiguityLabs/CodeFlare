package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.CodeflareAnimatedLogoView
import com.example.ui.components.EdgeLoadingIndicator
import com.example.ui.components.rememberHardwareAnimationSpeed
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IdeWorkspaceScreen
import com.example.ui.theme.BarStylePreferences
import com.example.ui.theme.EdgeCodeTheme
import com.example.ui.theme.LocalIdePalette
import com.example.ui.viewmodel.IdeViewModel
import com.example.util.HardwareSpeedProfiler
import kotlinx.coroutines.delay

internal fun isExternalFileLaunchIntent(intent: Intent?): Boolean {
  val action = intent?.action ?: return false
  return action == Intent.ACTION_VIEW ||
    action == Intent.ACTION_EDIT ||
    action == Intent.ACTION_SEND ||
    action == Intent.ACTION_SEND_MULTIPLE
}

internal fun isLauncherClickIntent(intent: Intent?): Boolean {
  if (intent == null) return false
  return intent.action == Intent.ACTION_MAIN &&
    intent.hasCategory(Intent.CATEGORY_LAUNCHER)
}

class MainActivity : ComponentActivity() {
  private val incomingIntentState = mutableStateOf<Intent?>(null)
  private val launchReplayTriggerState = mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    incomingIntentState.value = intent
    setContent {
      val ideViewModel: IdeViewModel = viewModel()
      val uiState by ideViewModel.uiState.collectAsStateWithLifecycle()
      val lifecycleOwner = LocalLifecycleOwner.current
      val context = LocalContext.current
      val pendingIntent = incomingIntentState.value

      val hardwareSpeed = rememberHardwareAnimationSpeed()
      val reducedMotion =
        remember(context) {
          HardwareSpeedProfiler.inspectDevice(context).isReducedMotionPreferred
        }
      var showLaunchShowcase by
        rememberSaveable {
          mutableStateOf(!isExternalFileLaunchIntent(intent) && !reducedMotion)
        }

      LaunchedEffect(pendingIntent, ideViewModel) {
        if (pendingIntent != null) {
          if (isExternalFileLaunchIntent(pendingIntent)) {
            showLaunchShowcase = false
          }
          ideViewModel.handleExternalIntent(pendingIntent)
          incomingIntentState.value = null
        }
      }

      var minDurationElapsed by rememberSaveable { mutableStateOf(false) }

      val replayNonce = launchReplayTriggerState.intValue
      LaunchedEffect(replayNonce, reducedMotion) {
        if (replayNonce > 0 && !reducedMotion) {
          minDurationElapsed = false
          showLaunchShowcase = true
        }
      }

      LaunchedEffect(showLaunchShowcase, replayNonce, hardwareSpeed) {
        if (showLaunchShowcase) {
          minDurationElapsed = false
          delay(HardwareSpeedProfiler.computeSequenceDurationMs(hardwareSpeed) + 320L)
          minDurationElapsed = true
        }
      }

      LaunchedEffect(minDurationElapsed, uiState.isBootstrapping) {
        if (minDurationElapsed && !uiState.isBootstrapping) {
          showLaunchShowcase = false
        }
      }

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

      val systemDark = isSystemInDarkTheme()
      val effectiveDarkTheme = uiState.editorSettings.resolveDarkTheme(systemDark)
      LaunchedEffect(effectiveDarkTheme) {
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
          statusBarStyle =
            if (effectiveDarkTheme) SystemBarStyle.dark(transparent)
            else SystemBarStyle.light(transparent, transparent),
          navigationBarStyle =
            if (effectiveDarkTheme) SystemBarStyle.dark(transparent)
            else SystemBarStyle.light(transparent, transparent),
        )
      }
      EdgeCodeTheme(
        darkTheme = effectiveDarkTheme,
        dynamicColor = uiState.editorSettings.dynamicColor,
        accent = uiState.editorSettings.accent,
        accentColor = uiState.editorSettings.accentColor,
        themePreset = com.example.ui.theme.EditorThemePreset.fromKey(uiState.editorSettings.themePreset),
        uiStyle = com.example.ui.theme.UiStyleVersion.fromKey(uiState.editorSettings.uiStyleVersion),
        barPreferences =
          BarStylePreferences(
            homeBar = uiState.editorSettings.homeBarStyle,
            editorBar = uiState.editorSettings.editorBarStyle,
            homeMenu = uiState.editorSettings.homeMenuStyle,
            editorMenu = uiState.editorSettings.editorMenuStyle,
          ),
        selectionDragConfig = uiState.editorSettings.selectionDragConfig,
        iconLabelBehavior = uiState.editorSettings.iconLabelBehavior,
        editorSettings = uiState.editorSettings,
      ) {
        val palette = LocalIdePalette.current
        Box(modifier = Modifier.fillMaxSize().background(palette.chromeBg)) {
          when {
            uiState.isBootstrapping -> {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!showLaunchShowcase) {
                  EdgeLoadingIndicator(size = 44.dp)
                }
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

          AnimatedVisibility(
            visible = showLaunchShowcase,
            enter = EnterTransition.None,
            exit = fadeOut(tween(320)),
          ) {
            Box(
              modifier =
                Modifier
                  .fillMaxSize()
                  .background(if (palette.isDark) Color(0xFF000000) else Color(0xFFFFFFFF))
                  .clickable(
                    enabled = !uiState.isBootstrapping,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                  ) {
                    showLaunchShowcase = false
                  }
                  .testTag("cold_launch_showcase"),
              contentAlignment = Alignment.Center,
            ) {
              CodeflareAnimatedLogoView(
                embedOnly = true,
                replayTrigger = replayNonce,
                speedMultiplier = hardwareSpeed,
                modifier =
                  Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .widthIn(max = 360.dp),
                testTag = "launch_animated_logo",
              )
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    incomingIntentState.value = intent
    if (isLauncherClickIntent(intent)) {
      launchReplayTriggerState.intValue += 1
    }
  }

  private fun openExternalUrl(url: String) {
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
      startActivity(intent)
    }
  }
}
