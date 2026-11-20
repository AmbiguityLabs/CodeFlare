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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LocalIdePalette
import com.example.util.HardwareSpeedProfiler
import java.util.Locale

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

@Composable
fun CodeflareEmblemDialog(
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val speed = rememberHardwareAnimationSpeed()
  var replayNonce by remember { mutableStateOf(0) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = if (palette.isDark) Color(0xFF0B0D1E) else Color(0xFFF4F5FB),
      tonalElevation = 6.dp,
      modifier =
        Modifier
          .fillMaxWidth(0.94f)
          .widthIn(max = 540.dp)
          .padding(vertical = 16.dp),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "EdgeCode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (palette.isDark) Color(0xFFEEF0FF) else Color(0xFF10122E),
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = { replayNonce += 1 },
              modifier = Modifier.testTag("emblem_dialog_replay"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Replay animation",
                tint = if (palette.isDark) Color(0xFFEEF0FF) else Color(0xFF10122E),
              )
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("emblem_dialog_close"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close",
                tint = if (palette.isDark) Color(0xFFA3A8D0) else Color(0xFF5A5F86),
              )
            }
          }
        }
        CodeflareAnimatedLogoView(
          embedOnly = false,
          replayTrigger = replayNonce,
          tapToReplay = false,
          speedMultiplier = speed,
          modifier = Modifier.fillMaxWidth().height(540.dp),
          testTag = "codeflare_emblem_showcase",
        )
      }
    }
  }
}
