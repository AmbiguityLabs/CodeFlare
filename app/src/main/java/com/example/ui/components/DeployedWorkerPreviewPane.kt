package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.editor.LocalWorkspacePreviewRenderer
import com.example.editor.drag.SmartDraggableText
import com.example.editor.drag.edgeTextDropTarget
import com.example.editor.selection.SmartTextDomain
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
import com.example.data.remote.ApiRequestUtils
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.UiStyleVersion
import com.example.ui.viewmodel.IdeUiState
import com.example.ui.viewmodel.WorkerPreviewConsoleEntry
import java.io.File

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
  isFullScreen: Boolean = false,
  onToggleFullScreen: (() -> Unit)? = null,
  onSwitchToCode: (() -> Unit)? = null,
  onLinkProjectFolder: (() -> Unit)? = null,
  onToggleViewport: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current

  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var pageProgress by remember { mutableIntStateOf(0) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var lastAppliedLoadKey by remember { mutableStateOf<Pair<String, Long>?>(null) }
  var loadErrorMessage by remember { mutableStateOf<String?>(null) }

  val latestPreviewUrl by rememberUpdatedState(uiState.previewUrl)
  val latestOnUrlChange by rememberUpdatedState(onUrlChange)
  val latestOnAppendConsole by rememberUpdatedState(onAppendConsoleEntry)
  val latestWorkspace by rememberUpdatedState(uiState.workspace)
  val latestIsDarkTheme by rememberUpdatedState(uiState.editorSettings.darkTheme)
  val latestBinaryAssets by rememberUpdatedState(uiState.externalBinaryAssets)
  val latestExternalRootDir by rememberUpdatedState(
    uiState.externalProjectRootPath?.takeIf { !it.startsWith("content://") }?.let { File(it) },
  )

  fun normalizeWorkerUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val fallbackBase =
      uiState.workspace?.workersDevUrl
        ?: uiState.workspace?.domains?.firstOrNull()?.hostname?.takeIf { it.isNotBlank() }?.let { "https://$it" }
    return ApiRequestUtils.normalizeBareOrRelativeUrl(trimmed, fallbackBase).getOrElse {
      if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
      } else {
        "https://$trimmed"
      }
    }
  }

  fun requestPreviewReload() {
    val target = normalizeWorkerUrl(uiState.previewUrl)
    if (target.isBlank()) {
      onNotifyStatus("Deploy your Worker or enter a https:// URL to preview")
      return
    }
    if (target != uiState.previewUrl) {
      onUrlChange(target)
    }
    loadErrorMessage = null
    onReloadPreview()
  }

  fun disposeWebView() {
    val wv = webViewRef ?: return
    webViewRef = null
    lastAppliedLoadKey = null
    runCatching {
      wv.stopLoading()
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.destroy()
    }
  }

  LaunchedEffect(webViewRef, uiState.previewModeTab, uiState.previewReloadNonce) {
    val wv = webViewRef
    if (uiState.previewModeTab == 0 && wv != null) {
      val target = normalizeWorkerUrl(uiState.previewUrl)
      val loadKey = target to uiState.previewReloadNonce
      if (target.isNotBlank() && lastAppliedLoadKey != loadKey) {
        lastAppliedLoadKey = loadKey
        loadErrorMessage = null
        wv.loadUrl(target)
      }
    }
  }

  DisposableEffect(uiState.previewModeTab == 0) {
    onDispose {
      if (uiState.previewModeTab != 0) {
        disposeWebView()
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      disposeWebView()
    }
  }

  Column(
    modifier = modifier.fillMaxSize().background(palette.chromeBg),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeSurface)
          .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      IconSegmentTab(
        icon = Icons.Outlined.Visibility,
        label = "Preview",
        selected = uiState.previewModeTab == 0,
        onClick = { onSelectModeTab(0) },
        tag = "worker_preview_mode_web",
      )

      IconSegmentTab(
        icon = Icons.Outlined.Http,
        label = "HTTP",
        selected = uiState.previewModeTab == 1,
        onClick = { onSelectModeTab(1) },
        tag = "worker_preview_mode_http",
      )

      Spacer(Modifier.weight(1f))

      val isLocalPreview = LocalWorkspacePreviewRenderer.isLocalPreviewUrl(uiState.previewUrl)
      if (onSwitchToCode != null) {
        IconRevealButton(
          icon = Icons.Outlined.Code,
          revealedLabel = "Code",
          minimal = true,
          onClick = onSwitchToCode,
          height = 30.dp,
          iconSize = 15.dp,
          tag = "worker_preview_code_button",
        )
      }

      if (isLocalPreview && onLinkProjectFolder != null) {
        IconRevealButton(
          icon = Icons.Outlined.FolderOpen,
          revealedLabel = "Folder",
          minimal = true,
          active = uiState.externalProjectRootPath != null,
          onClick = onLinkProjectFolder,
          height = 30.dp,
          iconSize = 15.dp,
          tag = "worker_preview_link_folder_button",
        )
      }

      if (onToggleViewport != null) {
        IconRevealButton(
          icon = if (uiState.isPreviewDesktopViewport) Icons.Outlined.Computer else Icons.Outlined.Smartphone,
          revealedLabel = if (uiState.isPreviewDesktopViewport) "Desktop" else "Mobile",
          minimal = true,
          active = uiState.isPreviewDesktopViewport,
          onClick = onToggleViewport,
          height = 30.dp,
          iconSize = 15.dp,
          tag = "worker_preview_viewport_button",
        )
      }

      if (uiState.hasUnsavedChanges && !uiState.isLocalScratchpad && uiState.workspace != null) {
        IconRevealButton(
          icon = Icons.Outlined.CloudUpload,
          revealedLabel = "Deploy*",
          active = true,
          isBusy = uiState.isDeploying,
          onClick = { if (!uiState.isDeploying) onDeployActiveWorker() },
          height = 30.dp,
          tag = "preview_deploy_sync_button",
        )
      }

      if (uiState.previewUrl.isNotBlank() && !isLocalPreview) {
        IconRevealButton(
          icon = Icons.AutoMirrored.Outlined.OpenInNew,
          revealedLabel = "Visit",
          onClick = {
            val url = normalizeWorkerUrl(uiState.previewUrl)
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
              .onFailure { onNotifyStatus("No browser available for $url") }
          },
          height = 30.dp,
          tag = "worker_preview_visit_button",
        )
      }

      if (onToggleFullScreen != null) {
        IconRevealButton(
          icon = if (isFullScreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
          revealedLabel = if (isFullScreen) "Exit" else "Fullscreen",
          active = isFullScreen,
          onClick = onToggleFullScreen,
          height = 30.dp,
          tag = "worker_preview_fullscreen_button",
        )
      }
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.45f))

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
              .background(palette.chromeBg)
              .border(1.dp, palette.border.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
              .edgeTextDropTarget(
                targetSurfaceId = "Preview URL",
                currentValue = uiState.previewUrl,
                onValueChange = onUrlChange,
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                onDropFeedback = onNotifyStatus,
              )
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
                onGo = { requestPreviewReload() },
              ),
            modifier = Modifier.fillMaxWidth().testTag("worker_preview_url_input"),
          )
        }

        Surface(
          onClick = { requestPreviewReload() },
          color = palette.activePillBg,
          border = BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.35f)),
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

    Box(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .background(palette.chromeBg),
    ) {
      if (uiState.previewModeTab == 0) {
        val currentTarget = normalizeWorkerUrl(uiState.previewUrl)
        if (currentTarget.isBlank()) {
          EmptyPreviewUrlNotice(
            hasWorkspace = uiState.workspace != null && !uiState.isLocalScratchpad,
            onDeployWorker = onDeployActiveWorker,
          )
        } else {
          val activeWorkerHost =
            remember(currentTarget) {
              runCatching { Uri.parse(currentTarget).host }.getOrNull()
            }
          val latestWorkerHost by rememberUpdatedState(activeWorkerHost)

          Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
              factory = { ctx ->
                WebView(ctx).apply {
                  setBackgroundColor(android.graphics.Color.WHITE)
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
                        loadErrorMessage = null
                        canGoBack = view?.canGoBack() == true
                        canGoForward = view?.canGoForward() == true
                        if (!url.isNullOrBlank()) {
                          if (url != latestPreviewUrl && url != "about:blank") {
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
                        if (LocalWorkspacePreviewRenderer.isLocalPreviewUrl(reqUrl)) {
                          val localResp =
                            LocalWorkspacePreviewRenderer.resolveWebResource(
                              workspace = latestWorkspace,
                              requestUrl = reqUrl,
                              isDarkTheme = latestIsDarkTheme,
                              externalBinaryAssets = latestBinaryAssets,
                              externalRootDir = latestExternalRootDir,
                            )
                          if (localResp != null) {
                            if (request?.isForMainFrame == false) {
                              val code = localResp.statusCode
                              val method = request.method ?: "GET"
                              view?.post {
                                latestOnAppendConsole(
                                  if (code >= 400) "ERR" else "NET",
                                  method,
                                  reqUrl,
                                  code,
                                  null,
                                )
                              }
                            }
                            return localResp
                          }
                        }
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
                          val desc = error?.description?.toString()?.takeIf { it.isNotBlank() } ?: "Unable to load Worker endpoint"
                          loadErrorMessage = desc
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
                }
              },
              update = { wv ->
                if (webViewRef !== wv) {
                  webViewRef = wv
                }
                val desiredUa =
                  if (uiState.isPreviewDesktopViewport) {
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                  } else {
                    null
                  }
                if (wv.settings.userAgentString != desiredUa && (uiState.isPreviewDesktopViewport || desiredUa == null)) {
                  wv.settings.userAgentString = desiredUa
                  wv.settings.useWideViewPort = true
                  wv.settings.loadWithOverviewMode = uiState.isPreviewDesktopViewport
                }
              },
              modifier = Modifier.fillMaxSize().testTag("deployed_worker_webview"),
            )

            val errMsg = loadErrorMessage
            if (errMsg != null) {
              Box(
                modifier = Modifier.fillMaxSize().background(palette.chromeBg.copy(alpha = 0.96f)).padding(20.dp),
                contentAlignment = Alignment.Center,
              ) {
                Surface(
                  color = palette.chromeSurface,
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(0.75.dp, RoseError.copy(alpha = 0.45f)),
                  modifier = Modifier.fillMaxWidth().testTag("worker_preview_error_card"),
                ) {
                  Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Icon(
                      Icons.Outlined.ErrorOutline,
                      contentDescription = null,
                      tint = RoseError,
                      modifier = Modifier.size(28.dp),
                    )
                    Text(
                      text = "Endpoint Unreachable",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = palette.textPrimary,
                    )
                    Text(
                      text = "$errMsg\n$currentTarget",
                      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
                      color = palette.textSecondary,
                      textAlign = TextAlign.Center,
                    )
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      IconRevealButton(
                        icon = Icons.Outlined.Refresh,
                        revealedLabel = "Retry",
                        onClick = { requestPreviewReload() },
                        height = 36.dp,
                      )
                      if (uiState.workspace != null && !uiState.isLocalScratchpad) {
                        IconRevealButton(
                          icon = Icons.Outlined.CloudUpload,
                          revealedLabel = "Deploy",
                          onClick = onDeployActiveWorker,
                          height = 36.dp,
                        )
                      }
                      IconRevealButton(
                        icon = Icons.Outlined.Http,
                        revealedLabel = "HTTP",
                        onClick = { onSelectModeTab(1) },
                        height = 36.dp,
                      )
                    }
                  }
                }
              }
            }
          }
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
          IconRevealButton(
            icon = Icons.Outlined.DeleteSweep,
            revealedLabel = "Clear",
            onClick = onClearConsole,
            height = 26.dp,
            tag = "worker_preview_console_clear",
          )
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
          modifier = Modifier.fillMaxWidth().background(palette.chromeBg),
        ) {
          if (uiState.previewConsoleEntries.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
              contentAlignment = Alignment.CenterStart,
            ) {
              Text(
                text = "—",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = palette.textMuted,
              )
            }
          } else {
            val keyedConsole =
              remember(uiState.previewConsoleEntries.toList()) {
                val seen = HashSet<String>(uiState.previewConsoleEntries.size)
                uiState.previewConsoleEntries.mapIndexed { idx, entry ->
                  val key = if (seen.add(entry.id)) "c_${entry.id}" else "c_${entry.id}_$idx"
                  key to entry
                }
              }
            LazyColumn(
              modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 160.dp),
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              items(keyedConsole, key = { it.first }) { (_, entry) ->
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
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(6.dp))
        .clickable(onClick = onCopy)
        .padding(horizontal = 6.dp, vertical = 5.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier =
          Modifier
            .width(2.5.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(accent),
      )
      Surface(
        color = palette.chromeSurface,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.45f)),
      ) {
        Text(
          text = entry.methodOrLevel,
          style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
          color = if (entry.kind == "ERR") RoseError else palette.textPrimary,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
      }
      entry.statusCode?.let { code ->
        Surface(
          color = accent.copy(alpha = 0.14f),
          shape = RoundedCornerShape(4.dp),
          border = BorderStroke(0.75.dp, accent.copy(alpha = 0.35f)),
        ) {
          Text(
            text = "$code",
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
            color = accent,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
          )
        }
      }
      entry.durationMs?.let { ms ->
        Text(
          text = "${ms}ms",
          style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.5.sp),
          color = palette.textMuted,
          maxLines = 1,
          softWrap = false,
        )
      }
      Spacer(Modifier.weight(1f))
      Text(
        text = entry.timestamp,
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.5.sp),
        color = palette.textMuted,
        maxLines = 1,
        softWrap = false,
      )
    }
    SmartDraggableText(
      text = entry.message,
      surfaceId = "preview_console_${entry.timestamp}_${entry.message.hashCode()}",
      domain = SmartTextDomain.TERMINAL_OR_LOG,
      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 15.sp),
      color = palette.textSecondary,
      maxLines = 4,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
    )
  }
}

@Composable
private fun EmptyPreviewUrlNotice(
  hasWorkspace: Boolean,
  onDeployWorker: () -> Unit,
) {
  val palette = LocalIdePalette.current
  var showInfo by remember { mutableStateOf(false) }
  Box(
    modifier = Modifier.fillMaxSize().padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      IconButton(onClick = { showInfo = !showInfo }) {
        Icon(
          Icons.Outlined.Visibility,
          contentDescription = "Preview info",
          tint = palette.activePillFg,
          modifier = Modifier.size(32.dp),
        )
      }
      AnimatedVisibility(visible = showInfo) {
        Text(
          text =
            if (hasWorkspace) "Deploy Worker or enter its workers.dev URL above."
            else "Enter any live Worker URL above to inspect.",
          style = MaterialTheme.typography.bodySmall,
          color = palette.textSecondary,
          textAlign = TextAlign.Center,
        )
      }
      if (hasWorkspace) {
        IconRevealButton(
          icon = Icons.Outlined.CloudUpload,
          revealedLabel = "Deploy",
          accentContainer = true,
          onClick = onDeployWorker,
          height = 40.dp,
          tag = "empty_preview_deploy_button",
        )
      }
    }
  }
}
