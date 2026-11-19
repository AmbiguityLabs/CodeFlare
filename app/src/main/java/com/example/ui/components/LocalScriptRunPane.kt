package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Dvr
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.remote.WorkerLanguage
import com.example.editor.LocalWorkspacePreviewRenderer
import com.example.editor.SyntaxHighlighter
import com.example.editor.drag.SmartDraggableText
import com.example.editor.drag.edgeTextDropTarget
import com.example.editor.selection.SmartTextDomain
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.UiStyleVersion
import com.example.ui.viewmodel.IdeUiState
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LocalScriptRunPane(
  uiState: IdeUiState,
  onPathChange: (String) -> Unit,
  onMethodChange: (String) -> Unit,
  onBodyChange: (String) -> Unit,
  onRunLocally: () -> Unit,
  onNotifyStatus: (String) -> Unit,
  onExecuteCliCommand: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val clipboard = LocalClipboardManager.current
  var showHttpBar by remember { mutableStateOf(false) }
  var showBodyEditor by remember { mutableStateOf(false) }
  var resultViewTab by remember { mutableIntStateOf(0) } // 0 = Output/Preview, 1 = Console, 2 = Headers, 3 = Unified
  var methodMenuOpen by remember { mutableStateOf(false) }
  var cliInput by remember { mutableStateOf("") }
  var wrapOutput by remember { mutableStateOf(true) }
  var showOutputSearch by remember { mutableStateOf(false) }
  var outputSearchQuery by remember { mutableStateOf("") }
  var logFilter by remember { mutableStateOf("ALL") } // ALL, LOG, ERR, INFO

  val latestWorkspace by rememberUpdatedState(uiState.workspace)
  val latestIsDark by rememberUpdatedState(uiState.editorSettings.darkTheme)
  val latestBinaryAssets by rememberUpdatedState(uiState.externalBinaryAssets)
  val latestExternalDir by rememberUpdatedState(
    uiState.externalProjectRootPath?.takeIf { !it.startsWith("content://") }?.let { File(it) },
  )

  val result = uiState.localScriptResult
  val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH")

  fun submitCliCommand() {
    val cmd = cliInput.trim()
    if (cmd.isEmpty()) return
    cliInput = ""
    onExecuteCliCommand(cmd)
  }

  Column(
    modifier = modifier.fillMaxSize().background(palette.chromeBg),
  ) {
    // Interactive CLI & JS/TS REPL Prompt Bar
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeSurface)
          .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val promptShape = RoundedCornerShape(8.dp)
      Row(
        modifier =
          Modifier
            .weight(1f)
            .height(34.dp)
            .clip(promptShape)
            .background(palette.editorBg)
            .border(0.5.dp, palette.border.copy(alpha = 0.65f), promptShape)
            .edgeTextDropTarget(
              targetSurfaceId = "CLI Prompt",
              currentValue = cliInput,
              onValueChange = { cliInput = it },
              singleLine = true,
              shape = promptShape,
              onDropFeedback = onNotifyStatus,
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = ">",
          fontFamily = JetBrainsMono,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = EmeraldLive,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
          if (cliInput.isEmpty()) {
            Text(
              text = "run, files, env, or JS expression…",
              fontFamily = JetBrainsMono,
              fontSize = 11.5.sp,
              color = palette.textMuted,
              maxLines = 1,
            )
          }
          BasicTextField(
            value = cliInput,
            onValueChange = { cliInput = it },
            singleLine = true,
            textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, color = palette.textPrimary),
            cursorBrush = SolidColor(palette.activePillFg),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submitCliCommand() }),
            modifier = Modifier.fillMaxWidth().testTag("cli_prompt_input"),
          )
        }
        if (cliInput.isNotBlank()) {
          Box(
            modifier =
              Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { submitCliCommand() }
                .testTag("cli_prompt_send"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.Send,
              contentDescription = "Execute command",
              tint = EmeraldLive,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      IconRevealButton(
        icon = Icons.Outlined.Http,
        revealedLabel = "Route",
        minimal = true,
        active = showHttpBar,
        onClick = { showHttpBar = !showHttpBar },
        tag = "local_run_http_toggle",
        height = 34.dp,
        iconSize = 16.dp,
      )

      IconRevealButton(
        icon = Icons.Outlined.RocketLaunch,
        revealedLabel = if (uiState.isRunningLocalScript) "Running" else "Run",
        onClick = { if (!uiState.isRunningLocalScript) onRunLocally() },
        isBusy = uiState.isRunningLocalScript,
        accentContainer = true,
        tag = "local_run_execute_button",
        height = 34.dp,
      )
    }

    // Collapsible Worker Route & HTTP Request Strip
    AnimatedVisibility(visible = showHttpBar) {
      Column(modifier = Modifier.fillMaxWidth().background(palette.chromeSurface)) {
        HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.5.dp)
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box {
            Surface(
              onClick = { methodMenuOpen = true },
              shape = RoundedCornerShape(7.dp),
              color = palette.activePillBg,
              modifier = Modifier.height(32.dp).testTag("local_run_method_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = uiState.localRunMethod,
                  fontFamily = JetBrainsMono,
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = palette.activePillFg,
                )
              }
            }
            EdgeMenu(
              expanded = methodMenuOpen,
              onDismissRequest = { methodMenuOpen = false },
              minWidth = 120.dp,
              maxWidth = 150.dp,
            ) {
              methods.forEach { m ->
                EdgeMenuItem(
                  label = m,
                  monospaced = true,
                  selected = uiState.localRunMethod == m,
                  onClick = {
                    methodMenuOpen = false
                    onMethodChange(m)
                  },
                )
              }
            }
          }

          val pathShape = RoundedCornerShape(7.dp)
          Box(
            modifier =
              Modifier
                .weight(1f)
                .height(32.dp)
                .clip(pathShape)
                .background(palette.editorBg)
                .border(0.5.dp, palette.border.copy(alpha = 0.6f), pathShape)
                .edgeTextDropTarget(
                  targetSurfaceId = "CLI Path",
                  currentValue = uiState.localRunUrlPath,
                  onValueChange = onPathChange,
                  singleLine = true,
                  shape = pathShape,
                  onDropFeedback = onNotifyStatus,
                )
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (uiState.localRunUrlPath.isEmpty()) {
              Text("/", fontFamily = JetBrainsMono, fontSize = 11.5.sp, color = palette.textMuted)
            }
            BasicTextField(
              value = uiState.localRunUrlPath,
              onValueChange = onPathChange,
              singleLine = true,
              textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, color = palette.textPrimary),
              cursorBrush = SolidColor(palette.activePillFg),
              modifier = Modifier.fillMaxWidth().testTag("local_run_path_input"),
            )
          }

          IconRevealButton(
            icon = Icons.Outlined.DataObject,
            revealedLabel = "Body",
            minimal = true,
            active = showBodyEditor,
            onClick = { showBodyEditor = !showBodyEditor },
            tag = "local_run_body_toggle",
            height = 32.dp,
          )
        }

        AnimatedVisibility(visible = showBodyEditor) {
          EdgeSharedTextEditor(
            surfaceKey = "local_cli_request_body",
            value = uiState.localRunBody,
            onValueChange = onBodyChange,
            label = "Request Body",
            placeholder = "{\"hello\": \"local\"}",
            language = detectSharedEditorLanguage(uiState.localRunBody, fallback = WorkerLanguage.JSON),
            height = 116.dp,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
          )
        }
      }
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.45f), thickness = 0.5.dp)

    if (result == null) {
      Column(
        modifier = Modifier.weight(1f).fillMaxWidth().background(palette.editorBg).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        IconRevealButton(
          icon = Icons.Outlined.RocketLaunch,
          revealedLabel = uiState.activeFile?.fileName ?: uiState.workspace?.entryModulePath ?: "Run",
          onClick = onRunLocally,
          isBusy = uiState.isRunningLocalScript,
          accentContainer = true,
          height = 42.dp,
          tag = "local_run_empty_trigger",
        )
        Spacer(Modifier.height(14.dp))
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf("run", "files", "env", "help").forEach { quickCmd ->
            Surface(
              onClick = { onExecuteCliCommand(quickCmd) },
              color = palette.chromeSurface,
              shape = RoundedCornerShape(7.dp),
              border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.6f)),
            ) {
              Text(
                text = "> $quickCmd",
                fontFamily = JetBrainsMono,
                fontSize = 10.5.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
              )
            }
          }
        }
      }
      return
    }

    val statusColor =
      when {
        result.errorMessage != null || result.statusCode >= 500 -> RoseError
        result.statusCode >= 400 -> AmberWarn
        else -> EmeraldLive
      }

    val displayOutputBody = remember(result.body, result.errorMessage) {
      result.errorMessage ?: result.body.ifEmpty { "(empty)" }
    }
    val outputSearchMatches =
      remember(showOutputSearch, outputSearchQuery, displayOutputBody) {
        if (!showOutputSearch || outputSearchQuery.isBlank()) emptyList()
        else findTextOccurrences(displayOutputBody, outputSearchQuery)
      }

    Column(
      modifier = Modifier.fillMaxWidth().background(palette.chromeSurface),
    ) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(
          color = statusColor.copy(alpha = 0.14f),
          shape = RoundedCornerShape(6.dp),
          border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.35f)),
        ) {
          Text(
            text = "${result.mode} • ${result.statusCode}",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          )
        }

        Text(
          text = "${result.durationMs}ms",
          fontFamily = JetBrainsMono,
          fontSize = 10.sp,
          color = palette.textMuted,
          maxLines = 1,
          softWrap = false,
        )

        Spacer(Modifier.weight(1f))

        IconButton(
          onClick = { showOutputSearch = !showOutputSearch },
          modifier = Modifier.size(28.dp).testTag("local_run_search_toggle"),
        ) {
          Icon(
            Icons.Outlined.Search,
            contentDescription = "Search output",
            tint = if (showOutputSearch) palette.activePillFg else palette.textSecondary,
            modifier = Modifier.size(15.dp),
          )
        }
        if ((resultViewTab == 0 || resultViewTab == 3) && !result.isHtmlBody) {
          IconButton(
            onClick = { wrapOutput = !wrapOutput },
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              Icons.AutoMirrored.Outlined.WrapText,
              contentDescription = "Toggle wrap",
              tint = if (wrapOutput) palette.activePillFg else palette.textMuted,
              modifier = Modifier.size(15.dp),
            )
          }
        }
        IconButton(
          onClick = {
            clipboard.setText(AnnotatedString(result.errorMessage ?: result.body))
            onNotifyStatus("Copied output")
          },
          modifier = Modifier.size(28.dp),
        ) {
          Icon(
            Icons.Outlined.ContentCopy,
            contentDescription = "Copy output",
            tint = palette.textSecondary,
            modifier = Modifier.size(15.dp),
          )
        }
        IconButton(
          onClick = { onExecuteCliCommand("clear") },
          modifier = Modifier.size(28.dp),
        ) {
          Icon(
            Icons.Outlined.DeleteSweep,
            contentDescription = "Clear CLI output",
            tint = palette.textMuted,
            modifier = Modifier.size(15.dp),
          )
        }
      }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 8.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconSegmentTab(
          icon = if (result.isHtmlBody) Icons.Outlined.Visibility else Icons.Outlined.Code,
          label = if (result.isHtmlBody) "Preview" else "Output",
          selected = resultViewTab == 0,
          onClick = { resultViewTab = 0 },
          tag = "local_run_tab_output",
        )
        IconSegmentTab(
          icon = Icons.Outlined.Terminal,
          label = "Logs",
          selected = resultViewTab == 1,
          badgeText = result.logs.size.takeIf { it > 0 }?.toString(),
          onClick = { resultViewTab = 1 },
          tag = "local_run_tab_logs",
        )
        IconSegmentTab(
          icon = Icons.Outlined.DataObject,
          label = "Headers",
          selected = resultViewTab == 2,
          badgeText = result.headers.size.takeIf { it > 0 }?.toString(),
          onClick = { resultViewTab = 2 },
          tag = "local_run_tab_headers",
        )
        IconSegmentTab(
          icon = Icons.Outlined.Dvr,
          label = "Unified",
          selected = resultViewTab == 3,
          onClick = { resultViewTab = 3 },
          tag = "local_run_tab_unified",
        )
      }

      AnimatedVisibility(visible = showOutputSearch) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(palette.editorBg)
              .border(0.5.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
              .padding(horizontal = 8.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(14.dp))
          Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (outputSearchQuery.isEmpty()) {
              Text(
                text = "Filter logs or find in output…",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = palette.textMuted,
                maxLines = 1,
              )
            }
            BasicTextField(
              value = outputSearchQuery,
              onValueChange = { outputSearchQuery = it },
              singleLine = true,
              textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, color = palette.textPrimary),
              cursorBrush = SolidColor(palette.activePillFg),
              modifier = Modifier.fillMaxWidth().testTag("local_run_search_input"),
            )
          }
          if (outputSearchQuery.isNotEmpty()) {
            Text(
              text = "${outputSearchMatches.size} hits",
              fontFamily = JetBrainsMono,
              fontSize = 10.sp,
              color = if (outputSearchMatches.isNotEmpty()) palette.activePillFg else palette.textMuted,
              maxLines = 1,
              softWrap = false,
            )
            IconButton(onClick = { outputSearchQuery = "" }, modifier = Modifier.size(22.dp)) {
              Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = palette.textMuted, modifier = Modifier.size(13.dp))
            }
          }
        }
      }
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.45f), thickness = 0.5.dp)

    Box(modifier = Modifier.weight(1f).fillMaxWidth().background(palette.editorBg)) {
      when (resultViewTab) {
        0 -> {
          if (result.errorMessage != null) {
            EdgeSharedTextEditor(
              surfaceKey = "cli_error_output_${result.entryPath.hashCode()}",
              value = result.errorMessage,
              onValueChange = {},
              language = WorkerLanguage.TEXT,
              readOnly = true,
              wordWrap = wrapOutput,
              height = null,
              findMatches = outputSearchMatches,
              isErrorText = true,
              onCopiedFallback = { onNotifyStatus("Copied error snippet") },
              modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
            )
          } else if (result.isHtmlBody) {
            val baseEntryUrl =
              remember(result.entryPath) {
                LocalWorkspacePreviewRenderer.localUrlForPath(result.entryPath)
              }
            AndroidView(
              factory = { ctx ->
                WebView(ctx).apply {
                  settings.javaScriptEnabled = true
                  settings.domStorageEnabled = true
                  webViewClient =
                    object : WebViewClient() {
                      override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                      ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString().orEmpty()
                        if (LocalWorkspacePreviewRenderer.isLocalPreviewUrl(reqUrl)) {
                          return LocalWorkspacePreviewRenderer.resolveWebResource(
                            workspace = latestWorkspace,
                            requestUrl = reqUrl,
                            isDarkTheme = latestIsDark,
                            externalBinaryAssets = latestBinaryAssets,
                            externalRootDir = latestExternalDir,
                          )
                        }
                        return super.shouldInterceptRequest(view, request)
                      }
                    }
                }
              },
              update = { wv ->
                wv.loadDataWithBaseURL(
                  baseEntryUrl,
                  result.body,
                  "text/html",
                  "UTF-8",
                  null,
                )
              },
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            val displayText = result.body.ifEmpty { "(empty)" }
            val ctype = result.headers["content-type"]
            val detectedLang = remember(displayText, ctype) { detectSharedEditorLanguage(displayText, ctype) }
            EdgeSharedTextEditor(
              surfaceKey = "cli_body_output_${result.entryPath.hashCode()}_${result.statusCode}",
              value = displayText,
              onValueChange = {},
              language = detectedLang,
              readOnly = true,
              wordWrap = wrapOutput,
              height = null,
              findMatches = outputSearchMatches,
              contentTypeHint = ctype,
              onCopiedFallback = { onNotifyStatus("Copied output snippet") },
              modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
            )
          }
        }

        1 -> {
          Column(modifier = Modifier.fillMaxSize()) {
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(palette.chromeSurface)
                  .padding(horizontal = 8.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              listOf("ALL", "LOG", "ERR", "INFO").forEach { lvl ->
                val selected = logFilter == lvl
                Surface(
                  onClick = { logFilter = lvl },
                  color = if (selected) palette.activePillBg else palette.editorBg,
                  shape = RoundedCornerShape(6.dp),
                  border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.55f)),
                ) {
                  Text(
                    text = lvl,
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) palette.activePillFg else palette.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
              }
            }
            HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.5.dp)

            val filteredLogs =
              remember(result.logs, logFilter, showOutputSearch, outputSearchQuery) {
                val byLevel = if (logFilter == "ALL") result.logs else result.logs.filter { it.level == logFilter }
                if (!showOutputSearch || outputSearchQuery.isBlank()) byLevel
                else byLevel.filter {
                  it.message.contains(outputSearchQuery, ignoreCase = true) ||
                    it.level.contains(outputSearchQuery, ignoreCase = true)
                }
              }
            if (filteredLogs.isEmpty()) {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IconToggleRevealText(icon = Icons.Outlined.Terminal, text = "0 logs")
              }
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                items(filteredLogs) { log ->
                  val tone =
                    when (log.level) {
                      "ERR" -> RoseError
                      "INFO" -> AmberWarn
                      else -> palette.activePillFg
                    }
                  val logAnnotated =
                    remember(log.message, palette) {
                      SyntaxHighlighter.highlightResponse(log.message, null, palette)
                    }
                  val logSearchAnnotated =
                    remember(logAnnotated, log.message, showOutputSearch, outputSearchQuery, palette) {
                      if (!showOutputSearch || outputSearchQuery.isBlank()) logAnnotated
                      else applySearchMatchHighlight(
                        base = logAnnotated,
                        plainText = log.message,
                        matches = findTextOccurrences(log.message, outputSearchQuery),
                        matchBg = palette.searchMatchBg,
                        matchFg = palette.textPrimary,
                      )
                    }
                  Column(
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(if (isV2) 10.dp else 7.dp))
                        .background(palette.chromeSurface)
                        .border(0.5.dp, palette.border.copy(alpha = 0.35f), RoundedCornerShape(if (isV2) 10.dp else 7.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Text(log.level, fontFamily = JetBrainsMono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = tone)
                      Text(log.timestamp, fontFamily = JetBrainsMono, fontSize = 9.5.sp, color = palette.textMuted)
                    }
                    SmartDraggableText(
                      text = log.message,
                      annotatedText = logSearchAnnotated,
                      surfaceId = "cli_log_${log.timestamp}_${log.message.hashCode()}",
                      domain = SmartTextDomain.TERMINAL_OR_LOG,
                      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, lineHeight = 16.sp),
                      color = palette.textPrimary,
                      onCopied = { onNotifyStatus("Copied log snippet") },
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }
                }
              }
            }
          }
        }

        2 -> {
          val displayedHeaders =
            remember(result.headers, showOutputSearch, outputSearchQuery) {
              if (!showOutputSearch || outputSearchQuery.isBlank()) result.headers.entries.toList()
              else result.headers.entries.filter { (k, v) ->
                k.contains(outputSearchQuery, ignoreCase = true) || v.contains(outputSearchQuery, ignoreCase = true)
              }
            }
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(displayedHeaders) { (k, v) ->
              Column(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(palette.chromeSurface)
                    .border(0.5.dp, palette.border.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(k, fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.activePillFg)
                SmartDraggableText(
                  text = v,
                  surfaceId = "cli_header_$k",
                  domain = SmartTextDomain.TERMINAL_OR_LOG,
                  style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
                  color = palette.textSecondary,
                  onCopied = { onNotifyStatus("Copied header value") },
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }
        }

        else -> {
          // 3 = Unified Output + Console Logs + Headers Summary
          val ctype = result.headers["content-type"]
          val annotatedBody =
            remember(displayOutputBody, ctype, palette, result.errorMessage) {
              SyntaxHighlighter.highlightResponse(displayOutputBody, if (result.errorMessage != null) null else ctype, palette)
            }
          val bodyWithSearch =
            remember(annotatedBody, displayOutputBody, outputSearchMatches, palette) {
              applySearchMatchHighlight(
                base = annotatedBody,
                plainText = displayOutputBody,
                matches = outputSearchMatches,
                matchBg = palette.searchMatchBg,
                matchFg = palette.textPrimary,
              )
            }
          Column(
            modifier =
              Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .let { if (wrapOutput) it else it.horizontalScroll(rememberScrollState()) }
                .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (result.logs.isNotEmpty()) {
              Text(
                text = "CONSOLE LOGS (${result.logs.size})",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textMuted,
              )
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                result.logs.forEach { log ->
                  val tone =
                    when (log.level) {
                      "ERR" -> RoseError
                      "INFO" -> AmberWarn
                      else -> palette.activePillFg
                    }
                  val logAnnotated =
                    remember(log.message, palette) {
                      SyntaxHighlighter.highlightResponse(log.message, null, palette)
                    }
                  Column(
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.chromeSurface)
                        .border(0.5.dp, palette.border.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                  ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                      Text(log.level, fontFamily = JetBrainsMono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = tone)
                      Text(log.timestamp, fontFamily = JetBrainsMono, fontSize = 9.5.sp, color = palette.textMuted)
                    }
                    SmartDraggableText(
                      text = log.message,
                      annotatedText = logAnnotated,
                      surfaceId = "cli_unified_log_${log.timestamp}_${log.message.hashCode()}",
                      domain = SmartTextDomain.TERMINAL_OR_LOG,
                      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
                      color = palette.textPrimary,
                      onCopied = { onNotifyStatus("Copied log snippet") },
                    )
                  }
                }
              }
            }

            Text(
              text = if (result.errorMessage != null) "ERROR OUTPUT" else "RESPONSE OUTPUT",
              fontFamily = JetBrainsMono,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (result.errorMessage != null) RoseError else palette.textMuted,
            )
            Surface(
              color = palette.chromeSurface,
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.45f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Box(modifier = Modifier.padding(10.dp)) {
                SmartDraggableText(
                  text = displayOutputBody,
                  annotatedText = bodyWithSearch,
                  surfaceId = "cli_unified_body",
                  domain = if (result.errorMessage == null && (displayOutputBody.trimStart().startsWith("{") || displayOutputBody.trimStart().startsWith("["))) SmartTextDomain.JSON else SmartTextDomain.TERMINAL_OR_LOG,
                  style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, lineHeight = 17.sp),
                  color = if (result.errorMessage != null) RoseError else palette.textPrimary,
                  onCopied = { onNotifyStatus("Copied output snippet") },
                )
              }
            }

            if (result.headers.isNotEmpty()) {
              Text(
                text = "HEADERS (${result.headers.size})",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textMuted,
              )
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                result.headers.forEach { (k, v) ->
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$k:", fontFamily = JetBrainsMono, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = palette.activePillFg)
                    SmartDraggableText(
                      text = v,
                      surfaceId = "cli_unified_header_$k",
                      domain = SmartTextDomain.TERMINAL_OR_LOG,
                      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.5.sp),
                      color = palette.textSecondary,
                      onCopied = { onNotifyStatus("Copied header value") },
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
}
