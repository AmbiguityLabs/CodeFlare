package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.WorkerTestRunRecord
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.IdeUiState
import com.example.ui.viewmodel.WorkerPreviewConsoleEntry

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeployedWorkerPreviewPane(
  uiState: IdeUiState,
  onSelectModeTab: (Int) -> Unit,
  onUrlChange: (String) -> Unit,
  onReloadPreview: () -> Unit,
  onToggleConsoleExpanded: () -> Unit,
  onUpdateHttpRequest: (method: String, headersJson: String, body: String) -> Unit,
  onSendHttpTest: () -> Unit,
  onSelectHistoryRun: (WorkerTestRunRecord) -> Unit,
  onClearHistory: () -> Unit,
  onAppendConsoleEntry: (kind: String, methodOrLevel: String, message: String, statusCode: Int?, durationMs: Long?) -> Unit,
  onClearConsole: () -> Unit,
  onDeployActiveWorker: () -> Unit,
  onNotifyStatus: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current

  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var pageProgress by remember { mutableIntStateOf(0) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var loadedUrl by remember { mutableStateOf("") }
  var lastLoadedNonce by remember { mutableStateOf(-1L) }

  val latestPreviewUrl by rememberUpdatedState(uiState.previewUrl)
  val latestOnUrlChange by rememberUpdatedState(onUrlChange)
  val latestOnAppendConsole by rememberUpdatedState(onAppendConsoleEntry)

  fun normalizeWorkerUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
      return trimmed
    }
    val fallbackBase =
      uiState.workspace?.workersDevUrl
        ?: uiState.workspace?.domains?.firstOrNull()?.hostname?.takeIf { it.isNotBlank() }?.let { "https://$it" }
    return if (!fallbackBase.isNullOrBlank()) {
      fallbackBase.trimEnd('/') + if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    } else {
      "https://$trimmed"
    }
  }

  fun triggerWebViewLoad() {
    val target = normalizeWorkerUrl(uiState.previewUrl)
    if (target.isNotBlank()) {
      if (target != uiState.previewUrl) onUrlChange(target)
      loadedUrl = target
      webViewRef?.loadUrl(target)
    } else {
      onNotifyStatus("Deploy your Worker or enter a https:// URL to preview")
    }
  }

  LaunchedEffect(uiState.previewReloadNonce) {
    if (uiState.previewModeTab == 0 && uiState.previewReloadNonce != lastLoadedNonce) {
      lastLoadedNonce = uiState.previewReloadNonce
      val target = normalizeWorkerUrl(uiState.previewUrl)
      if (target.isNotBlank()) {
        loadedUrl = target
        webViewRef?.loadUrl(target)
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webViewRef?.destroy()
      webViewRef = null
    }
  }

  Column(
    modifier = modifier.fillMaxSize().background(palette.chromeBg),
  ) {
    // 1. Cloudflare-style Top Bar: Preview | HTTP + Visit + Deploy
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeSurface)
          .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(
        onClick = { onSelectModeTab(0) },
        color = if (uiState.previewModeTab == 0) palette.activePillBg else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("worker_preview_mode_web"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            Icons.Outlined.Visibility,
            contentDescription = "Preview mode",
            tint = if (uiState.previewModeTab == 0) palette.activePillFg else palette.textSecondary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "Preview",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (uiState.previewModeTab == 0) FontWeight.SemiBold else FontWeight.Normal,
            color = if (uiState.previewModeTab == 0) palette.activePillFg else palette.textPrimary,
          )
        }
      }

      Surface(
        onClick = { onSelectModeTab(1) },
        color = if (uiState.previewModeTab == 1) palette.activePillBg else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("worker_preview_mode_http"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            Icons.Outlined.Http,
            contentDescription = "HTTP request mode",
            tint = if (uiState.previewModeTab == 1) palette.activePillFg else palette.textSecondary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "HTTP",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (uiState.previewModeTab == 1) FontWeight.SemiBold else FontWeight.Normal,
            color = if (uiState.previewModeTab == 1) palette.activePillFg else palette.textPrimary,
          )
        }
      }

      Spacer(Modifier.weight(1f))

      if (uiState.hasUnsavedChanges && !uiState.isLocalScratchpad && uiState.workspace != null) {
        Surface(
          onClick = { if (!uiState.isDeploying) onDeployActiveWorker() },
          color = palette.activePillBg,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.testTag("preview_deploy_sync_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(Icons.Outlined.CloudUpload, contentDescription = "Deploy pending changes", tint = palette.activePillFg, modifier = Modifier.size(13.dp))
            Text("Deploy*", style = MaterialTheme.typography.labelSmall, color = palette.activePillFg, fontWeight = FontWeight.SemiBold)
          }
        }
      }

      if (uiState.previewUrl.isNotBlank()) {
        Surface(
          onClick = {
            val url = normalizeWorkerUrl(uiState.previewUrl)
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
              .onFailure { onNotifyStatus("No browser available for $url") }
          },
          color = palette.chromePill,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.testTag("worker_preview_visit_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text("Visit", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Visit live Worker URL", tint = palette.activePillFg, modifier = Modifier.size(13.dp))
          }
        }
      }
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.45f))

    // 2. WebView URL Bar (shown in Preview tab; WorkerTestWindow provides its own Method + URL bar in HTTP mode)
    if (uiState.previewModeTab == 0) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(palette.chromeBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconButton(
          onClick = { webViewRef?.goBack() },
          enabled = canGoBack,
          modifier = Modifier.size(34.dp).minimumInteractiveComponentSize().testTag("worker_preview_back"),
        ) {
          Icon(
            Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "WebView Back",
            tint = if (canGoBack) palette.textPrimary else palette.textMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp),
          )
        }
        IconButton(
          onClick = { webViewRef?.goForward() },
          enabled = canGoForward,
          modifier = Modifier.size(34.dp).minimumInteractiveComponentSize().testTag("worker_preview_forward"),
        ) {
          Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = "WebView Forward",
            tint = if (canGoForward) palette.textPrimary else palette.textMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp),
          )
        }

        Box(
          modifier =
            Modifier
              .weight(1f)
              .height(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(palette.editorBg)
              .border(1.dp, palette.border.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
              .padding(horizontal = 10.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (uiState.previewUrl.isEmpty()) {
            Text(
              text = "https://my-worker.subdomain.workers.dev/",
              style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textMuted),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BasicTextField(
            value = uiState.previewUrl,
            onValueChange = onUrlChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textPrimary),
            cursorBrush = SolidColor(palette.activePillFg),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions =
              KeyboardActions(
                onGo = {
                  onReloadPreview()
                  triggerWebViewLoad()
                },
              ),
            modifier = Modifier.fillMaxWidth().testTag("worker_preview_url_input"),
          )
        }

        Surface(
          onClick = {
            onReloadPreview()
            triggerWebViewLoad()
          },
          color = palette.activePillBg,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.height(36.dp).testTag("worker_preview_reload_button"),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(
              Icons.Outlined.Refresh,
              contentDescription = "Load or reload deployed Worker URL",
              tint = palette.activePillFg,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    if (uiState.previewModeTab == 0 && pageProgress in 1..99) {
      LinearProgressIndicator(
        progress = { pageProgress / 100f },
        modifier = Modifier.fillMaxWidth().height(2.dp),
      )
    }

    // 3. Main Testing Viewport (WebView Preview OR HTTP Request/Response Inspector)
    Box(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .background(palette.editorBg),
    ) {
      if (uiState.previewModeTab == 0) {
        val currentTarget = normalizeWorkerUrl(uiState.previewUrl)
        if (currentTarget.isBlank()) {
          EmptyPreviewUrlNotice(
            hasWorkspace = uiState.workspace != null && !uiState.isLocalScratchpad,
            onDeployWorker = onDeployActiveWorker,
          )
        } else {
          val initialUrl = remember(uiState.workspace?.scriptName) { normalizeWorkerUrl(uiState.previewUrl) }
          val activeWorkerHost =
            remember(currentTarget) {
              runCatching { Uri.parse(currentTarget).host }.getOrNull()
            }
          val latestWorkerHost by rememberUpdatedState(activeWorkerHost)

          AndroidView(
            factory = { ctx ->
              WebView(ctx).apply {
                settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = true
                  loadsImagesAutomatically = true
                  useWideViewPort = true
                  loadWithOverviewMode = true
                  mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                  cacheMode = WebSettings.LOAD_DEFAULT
                }
                webViewClient =
                  object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                      super.onPageStarted(view, url, favicon)
                      canGoBack = view?.canGoBack() == true
                      canGoForward = view?.canGoForward() == true
                      if (!url.isNullOrBlank()) {
                        loadedUrl = url
                        if (url != latestPreviewUrl) {
                          latestOnUrlChange(url)
                        }
                        latestOnAppendConsole("NAV", "GET", url, null, null)
                      }
                    }

                    override fun shouldInterceptRequest(
                      view: WebView?,
                      request: WebResourceRequest?,
                    ): WebResourceResponse? {
                      val reqUrl = request?.url?.toString().orEmpty()
                      if (request != null && !request.isForMainFrame && reqUrl.startsWith("http")) {
                        val workerHost = latestWorkerHost
                        val reqHost = request.url?.host
                        if (workerHost != null && workerHost == reqHost) {
                          val method = request.method ?: "GET"
                          view?.post {
                            latestOnAppendConsole("NET", method, reqUrl, null, null)
                          }
                        }
                      }
                      return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                      super.onPageFinished(view, url)
                      canGoBack = view?.canGoBack() == true
                      canGoForward = view?.canGoForward() == true
                      pageProgress = 100
                    }

                    override fun onReceivedHttpError(
                      view: WebView?,
                      request: WebResourceRequest?,
                      errorResponse: WebResourceResponse?,
                    ) {
                      super.onReceivedHttpError(view, request, errorResponse)
                      if (request?.isForMainFrame == true && errorResponse != null) {
                        latestOnAppendConsole(
                          "ERR",
                          request.method ?: "GET",
                          request.url?.toString().orEmpty(),
                          errorResponse.statusCode,
                          null,
                        )
                      }
                    }

                    override fun onReceivedError(
                      view: WebView?,
                      request: WebResourceRequest?,
                      error: WebResourceError?,
                    ) {
                      super.onReceivedError(view, request, error)
                      if (request?.isForMainFrame == true) {
                        val desc = error?.description?.toString() ?: "Load failed"
                        latestOnAppendConsole("ERR", request.method ?: "GET", "${request.url} • $desc", null, null)
                      }
                    }
                  }
                webChromeClient =
                  object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                      pageProgress = newProgress
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                      consoleMessage?.let { cm ->
                        val lvl = cm.messageLevel().name
                        val kind = if (lvl == "ERROR") "ERR" else "JS"
                        latestOnAppendConsole(kind, "JS:$lvl", "${cm.message()} (L${cm.lineNumber()})", null, null)
                      }
                      return true
                    }
                  }
                webViewRef = this
                if (initialUrl.isNotBlank()) {
                  loadedUrl = initialUrl
                  loadUrl(initialUrl)
                }
              }
            },
            update = { wv ->
              webViewRef = wv
              if (loadedUrl.isBlank() && currentTarget.isNotBlank()) {
                loadedUrl = currentTarget
                wv.loadUrl(currentTarget)
              }
            },
            modifier = Modifier.fillMaxSize().testTag("deployed_worker_webview"),
          )
        }
      } else {
        WorkerTestWindow(
          uiState = uiState,
          onUrlChange = onUrlChange,
          onMethodChange = { method ->
            onUpdateHttpRequest(method, uiState.previewHttpHeadersJson, uiState.previewHttpBody)
          },
          onHeadersJsonChange = { headersJson ->
            onUpdateHttpRequest(uiState.previewHttpMethod, headersJson, uiState.previewHttpBody)
          },
          onBodyChange = { body ->
            onUpdateHttpRequest(uiState.previewHttpMethod, uiState.previewHttpHeadersJson, body)
          },
          onSendRequest = onSendHttpTest,
          onSelectHistoryRun = onSelectHistoryRun,
          onClearHistory = onClearHistory,
          onNotifyStatus = onNotifyStatus,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }

    // 4. Rebuilt Bottom CONSOLE Drawer (crisp header bar + zero-jank log stream)
    HorizontalDivider(color = palette.border.copy(alpha = 0.55f))
    Column(
      modifier = Modifier.fillMaxWidth().background(palette.chromeSurface),
    ) {
      val entryCount = uiState.previewConsoleEntries.size
      val errorCount =
        remember(uiState.previewConsoleEntries) {
          uiState.previewConsoleEntries.count { it.kind == "ERR" || (it.statusCode != null && it.statusCode >= 400) }
        }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable { onToggleConsoleExpanded() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "CONSOLE",
          fontFamily = JetBrainsMono,
          fontSize = 10.5.sp,
          letterSpacing = 0.8.sp,
          fontWeight = FontWeight.Bold,
          color = palette.textSecondary,
        )
        if (entryCount > 0) {
          Spacer(Modifier.width(7.dp))
          Surface(
            color = palette.chromePill,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
          ) {
            Text(
              text = "$entryCount",
              fontFamily = JetBrainsMono,
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.activePillFg,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
          }
        }
        if (errorCount > 0) {
          Spacer(Modifier.width(5.dp))
          Surface(
            color = RoseError.copy(alpha = 0.14f),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, RoseError.copy(alpha = 0.35f)),
          ) {
            Text(
              text = "$errorCount ERR",
              fontFamily = JetBrainsMono,
              fontSize = 9.5.sp,
              fontWeight = FontWeight.Bold,
              color = RoseError,
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
          }
        }
        Spacer(Modifier.weight(1f))
        if (entryCount > 0) {
          Surface(
            onClick = onClearConsole,
            color = palette.chromePill,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
            modifier = Modifier.height(24.dp).testTag("worker_preview_console_clear"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear console", tint = palette.textSecondary, modifier = Modifier.size(12.dp))
              Text("Clear", style = MaterialTheme.typography.labelSmall, fontSize = 10.5.sp, color = palette.textSecondary)
            }
          }
          Spacer(Modifier.width(6.dp))
        }
        Icon(
          imageVector =
            if (uiState.isPreviewConsoleExpanded) Icons.Outlined.KeyboardArrowDown
            else Icons.Outlined.KeyboardArrowUp,
          contentDescription = if (uiState.isPreviewConsoleExpanded) "Collapse console" else "Expand console",
          tint = palette.textSecondary,
          modifier = Modifier.size(18.dp),
        )
      }

      if (uiState.isPreviewConsoleExpanded) {
        HorizontalDivider(color = palette.border.copy(alpha = 0.35f))
        Box(
          modifier = Modifier.fillMaxWidth().background(palette.editorBg),
        ) {
          if (uiState.previewConsoleEntries.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
              contentAlignment = Alignment.CenterStart,
            ) {
              Text(
                text = "Load a Worker URL or run an HTTP test to inspect live requests & console logs.",
                fontFamily = JetBrainsMono,
                fontSize = 10.5.sp,
                color = palette.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 148.dp).padding(horizontal = 6.dp, vertical = 4.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              items(uiState.previewConsoleEntries, key = { it.id }) { entry ->
                PreviewConsoleRow(
                  entry = entry,
                  onCopy = {
                    clipboard.setText(AnnotatedString("${entry.timestamp} ${entry.methodOrLevel} ${entry.message}"))
                    onNotifyStatus("Copied console entry")
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PreviewConsoleRow(
  entry: WorkerPreviewConsoleEntry,
  onCopy: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val accent =
    when {
      entry.kind == "ERR" || (entry.statusCode != null && entry.statusCode >= 400) -> RoseError
      entry.kind == "DEPLOY" -> EmeraldLive
      else -> palette.activePillFg
    }
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(5.dp))
        .clickable(onClick = onCopy)
        .padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier =
        Modifier
          .width(2.5.dp)
          .height(14.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(accent),
    )
    Text(
      text = entry.timestamp,
      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.5.sp),
      color = palette.textMuted,
    )
    Surface(
      color = palette.chromeSurface,
      shape = RoundedCornerShape(4.dp),
      border = BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
    ) {
      Text(
        text = entry.methodOrLevel,
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        color = if (entry.kind == "ERR") RoseError else palette.textPrimary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
      )
    }
    Text(
      text = entry.message,
      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
      color = palette.textSecondary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    entry.statusCode?.let { code ->
      Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
      ) {
        Text(
          text = "$code",
          style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Bold),
          color = accent,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
      }
    }
    entry.durationMs?.let { ms ->
      Text(
        text = "${ms}ms",
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
        color = palette.textMuted,
      )
    }
  }
}

@Composable
private fun EmptyPreviewUrlNotice(
  hasWorkspace: Boolean,
  onDeployWorker: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Box(
    modifier = Modifier.fillMaxSize().padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        Icons.Outlined.Visibility,
        contentDescription = null,
        tint = palette.activePillFg,
        modifier = Modifier.size(34.dp),
      )
      Text(
        text = "Deployed Worker Preview Window",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = palette.textPrimary,
      )
      Text(
        text =
          if (hasWorkspace) "Deploy your Worker or enter its live https://...workers.dev URL above to test UI interactions, HTTP requests, and console logs."
          else "Connect Cloudflare & open/deploy a Worker — or type any live https://...workers.dev URL above to inspect and test it here.",
        style = MaterialTheme.typography.bodySmall,
        color = palette.textSecondary,
        textAlign = TextAlign.Center,
      )
      if (hasWorkspace) {
        FilledTonalButton(
          onClick = onDeployWorker,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.testTag("empty_preview_deploy_button"),
        ) {
          Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Deploy Worker Now")
        }
      }
    }
  }
}
