package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.LocalIdePalette
import com.example.util.HardwareSpeedProfiler
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val CODEFLARE_LOGO_ASSET_BASE = "file:///android_asset/codeflare_logo_flame_top_neon_heat.html"

internal fun buildCodeflareLogoAssetUrl(
  darkTheme: Boolean,
  embedOnly: Boolean,
  speedMultiplier: Float,
): String {
  val clampedSpeed = speedMultiplier.coerceIn(0.5f, 3.0f)
  val formattedSpeed = String.format(Locale.US, "%.2f", clampedSpeed)
  val themeValue = if (darkTheme) "dark" else "light"
  val embedValue = if (embedOnly) "1" else "0"
  return Uri.parse(CODEFLARE_LOGO_ASSET_BASE)
    .buildUpon()
    .appendQueryParameter("embed", embedValue)
    .appendQueryParameter("theme", themeValue)
    .appendQueryParameter("speed", formattedSpeed)
    .build()
    .toString()
}

@Composable
fun rememberHardwareAnimationSpeed(): Float {
  val context = LocalContext.current
  return remember(context) {
    val profile = HardwareSpeedProfiler.inspectDevice(context)
    HardwareSpeedProfiler.computeSpeedMultiplier(profile)
  }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun CodeflareAnimatedLogoView(
  modifier: Modifier = Modifier,
  embedOnly: Boolean = true,
  replayTrigger: Int = 0,
  tapToReplay: Boolean = false,
  speedMultiplier: Float = rememberHardwareAnimationSpeed(),
  onPageReady: () -> Unit = {},
  testTag: String = "codeflare_animated_logo",
) {
  val palette = LocalIdePalette.current
  val isInspection = LocalInspectionMode.current
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var isPageReady by remember { mutableStateOf(false) }

  val initialUrl =
    remember(embedOnly) {
      buildCodeflareLogoAssetUrl(
        darkTheme = palette.isDark,
        embedOnly = embedOnly,
        speedMultiplier = speedMultiplier,
      )
    }

  DisposableEffect(Unit) {
    onDispose {
      val wv = webViewRef ?: return@onDispose
      webViewRef = null
      runCatching {
        wv.stopLoading()
        wv.loadUrl("about:blank")
        wv.clearHistory()
        wv.removeAllViews()
        wv.destroy()
      }
    }
  }

  LaunchedEffect(palette.isDark, isPageReady) {
    val wv = webViewRef ?: return@LaunchedEffect
    if (isPageReady) {
      wv.evaluateJavascript("window.setHostTheme && window.setHostTheme(${palette.isDark});", null)
    }
  }

  LaunchedEffect(speedMultiplier, isPageReady) {
    val wv = webViewRef ?: return@LaunchedEffect
    if (isPageReady) {
      val formatted = String.format(Locale.US, "%.2f", speedMultiplier.coerceIn(0.5f, 3.0f))
      wv.evaluateJavascript("window.setHardwareSpeed && window.setHardwareSpeed($formatted, false);", null)
    }
  }

  LaunchedEffect(replayTrigger) {
    if (replayTrigger > 0 && isPageReady) {
      webViewRef?.evaluateJavascript("window.play && window.play();", null)
    }
  }

  Box(
    modifier = modifier.testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    if (isInspection) {
      Box(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        contentAlignment = Alignment.Center,
      ) {
        EdgeLoadingIndicator(size = 36.dp)
      }
    } else {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
          val created =
            runCatching {
              WebView(ctx).apply {
                layoutParams =
                  ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                  )
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setOnLongClickListener { true }
                isLongClickable = false
                if (embedOnly && !tapToReplay) {
                  isClickable = false
                  isFocusable = false
                  isFocusableInTouchMode = false
                }

                settings.apply {
                  javaScriptEnabled = true
                  allowFileAccess = true
                  domStorageEnabled = false
                  cacheMode = WebSettings.LOAD_NO_CACHE
                  useWideViewPort = false
                  loadWithOverviewMode = false
                  setSupportZoom(false)
                  builtInZoomControls = false
                  displayZoomControls = false
                  mediaPlaybackRequiresUserGesture = false
                }

                if (embedOnly) {
                  setOnTouchListener { v, event ->
                    if (tapToReplay && event.action == MotionEvent.ACTION_UP) {
                      (v as? WebView)?.evaluateJavascript("window.play && window.play();", null)
                      v.performClick()
                    }
                    tapToReplay
                  }
                }

                webViewClient =
                  object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                      view: WebView?,
                      request: WebResourceRequest?,
                    ): Boolean {
                      val url = request?.url?.toString().orEmpty()
                      return !url.startsWith("file:///android_asset/")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                      super.onPageFinished(view, url)
                      isPageReady = true
                      onPageReady()
                      val formatted = String.format(Locale.US, "%.2f", speedMultiplier.coerceIn(0.5f, 3.0f))
                      view?.evaluateJavascript(
                        "window.setHostTheme && window.setHostTheme(${palette.isDark}); window.setHardwareSpeed && window.setHardwareSpeed($formatted, false);",
                        null,
                      )
                    }
                  }

                loadUrl(initialUrl)
                webViewRef = this
              }
            }.getOrNull()

          created ?: View(ctx)
        },
        update = { view ->
          val wv = view as? WebView ?: return@AndroidView
          if (webViewRef !== wv) webViewRef = wv
        },
      )
    }
  }
}

/**
 * Compact inline CodeFlare emblem that renders the HTML logo animation directly
 * inside a small header slot (no click-to-popup dialog), replaying automatically
 * at randomized idle intervals as well as on save, sync, and user actions.
 */
@Composable
fun CodeflareAutoEmblem(
  modifier: Modifier = Modifier,
  actionNonce: Long = 0L,
  isSyncing: Boolean = false,
  minIntervalMs: Long = 9_000L,
  maxIntervalMs: Long = 22_000L,
  testTag: String = "codeflare_auto_emblem",
) {
  val context = LocalContext.current
  val speed = rememberHardwareAnimationSpeed()
  val reducedMotion =
    remember(context) {
      HardwareSpeedProfiler.inspectDevice(context).isReducedMotionPreferred
    }
  val sequenceDurationMs = remember(speed) { HardwareSpeedProfiler.computeSequenceDurationMs(speed) }

  var replayNonce by remember { mutableIntStateOf(0) }
  var isPageReady by remember { mutableStateOf(false) }
  var lastPlayStartMs by remember { mutableLongStateOf(0L) }
  var hasInitializedObserver by remember { mutableStateOf(false) }
  var prevActionNonce by remember { mutableLongStateOf(actionNonce) }
  var prevSyncing by remember { mutableStateOf(isSyncing) }

  // Random-interval idle auto-playback (resets whenever replayNonce changes)
  LaunchedEffect(replayNonce, isPageReady, reducedMotion, minIntervalMs, maxIntervalMs) {
    if (!isPageReady || reducedMotion) return@LaunchedEffect
    if (lastPlayStartMs == 0L) {
      lastPlayStartMs = System.currentTimeMillis()
    }
    val safeMin = minIntervalMs.coerceAtLeast(3_000L)
    val safeMax = maxIntervalMs.coerceAtLeast(safeMin + 1_000L)
    val randomGap = Random.nextLong(safeMin, safeMax + 1L)
    delay(sequenceDurationMs + randomGap)
    lastPlayStartMs = System.currentTimeMillis()
    replayNonce += 1
  }

  // Automatic small inline playback on save, sync, or workspace/app actions
  LaunchedEffect(actionNonce, isSyncing, isPageReady, reducedMotion) {
    if (!hasInitializedObserver) {
      hasInitializedObserver = true
      prevActionNonce = actionNonce
      prevSyncing = isSyncing
      return@LaunchedEffect
    }
    val actionChanged = actionNonce != prevActionNonce
    val syncToggled = isSyncing != prevSyncing
    prevActionNonce = actionNonce
    prevSyncing = isSyncing

    if (!isPageReady || reducedMotion) return@LaunchedEffect
    if (actionChanged || syncToggled) {
      val now = System.currentTimeMillis()
      if (now - lastPlayStartMs >= 2_500L) {
        lastPlayStartMs = now
        replayNonce += 1
      }
    }
  }

  Box(
    modifier = modifier.testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    CodeflareAnimatedLogoView(
      embedOnly = true,
      replayTrigger = replayNonce,
      tapToReplay = false,
      speedMultiplier = speed,
      onPageReady = {
        isPageReady = true
        if (lastPlayStartMs == 0L) {
          lastPlayStartMs = System.currentTimeMillis()
        }
      },
      modifier = Modifier.fillMaxSize(),
      testTag = "${testTag}_webview",
    )
    if (!isPageReady) {
      Image(
        painter = painterResource(id = R.drawable.codeflare_boot_emblem),
        contentDescription = "CodeFlare emblem",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}
