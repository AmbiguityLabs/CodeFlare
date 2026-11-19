package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Dvr
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.WorkerTestRunRecord
import com.example.data.remote.ApiEnvVar
import com.example.data.remote.ApiImportResult
import com.example.data.remote.ApiTesterRequest
import com.example.data.remote.DiagnosticSeverity
import com.example.editor.SyntaxHighlighter
import com.example.editor.drag.SmartDraggableText
import com.example.editor.selection.SmartTextDomain
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.BottomPanelTab
import com.example.ui.viewmodel.IdeUiState

@Composable
fun BottomPanel(
  uiState: IdeUiState,
  onSelectTab: (BottomPanelTab) -> Unit,
  onToggleExpand: () -> Unit,
  onClose: () -> Unit,
  onPreviewSelectModeTab: (Int) -> Unit,
  onPreviewUrlChange: (String) -> Unit,
  onReloadPreview: () -> Unit,
  onTogglePreviewConsoleExpanded: () -> Unit,
  onUpdatePreviewHttp: (String, String, String) -> Unit,
  onSendPreviewHttp: () -> Unit,
  onSelectWorkerTestHistoryRun: (WorkerTestRunRecord) -> Unit,
  onClearWorkerTestHistory: () -> Unit,
  onAppendPreviewConsole: (String, String, String, Int?, Long?) -> Unit,
  onClearPreviewConsole: () -> Unit,
  onDeployActiveWorker: () -> Unit,
  onUpdateApiRequest: ((ApiTesterRequest) -> ApiTesterRequest) -> Unit,
  onUpdateApiEnvVars: (List<ApiEnvVar>) -> Unit,
  onRunApiRequest: () -> Unit,
  onSaveApiRequest: (String) -> Unit,
  onLoadApiRequest: (ApiTesterRequest) -> Unit,
  onDeleteApiCollectionItem: (String) -> Unit,
  onClearApiHistory: () -> Unit,
  onNotifyStatus: (String) -> Unit,
  onJumpToProblem: (String, Int) -> Unit,
  onToggleLiveTail: () -> Unit,
  onSelectApiEnvProfile: (String) -> Unit = {},
  onCreateApiEnvProfile: (String) -> Unit = {},
  onDeleteApiEnvProfile: (String) -> Unit = {},
  onImportApiCollection: (String, Boolean, Boolean) -> Result<ApiImportResult> = { _, _, _ ->
    Result.failure(UnsupportedOperationException("Collection import not wired"))
  },
  onUpdateLocalRunRequest: (method: String, path: String, body: String) -> Unit = { _, _, _ -> },
  onRunWorkspaceLocally: () -> Unit = {},
  onExecuteCliCommand: (String) -> Unit = {},
  onSwitchToCode: (() -> Unit)? = null,
  onLinkProjectFolder: (() -> Unit)? = null,
  onToggleViewport: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current

  Surface(
    color = palette.chromeBg,
    modifier =
      modifier.fillMaxWidth().then(
        if (uiState.isBottomPanelExpanded) Modifier else Modifier.height(320.dp),
      ),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      HorizontalDivider(color = palette.border.copy(alpha = 0.55f))
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(palette.chromeSurface)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconSegmentTab(
            icon = Icons.Outlined.Terminal,
            label = "CLI",
            selected = uiState.activeBottomTab == BottomPanelTab.LOCAL_RUN,
            onClick = { onSelectTab(BottomPanelTab.LOCAL_RUN) },
            tag = "tab_local_run",
          )
          IconSegmentTab(
            icon = Icons.Outlined.Visibility,
            label = "Preview",
            selected = uiState.activeBottomTab == BottomPanelTab.WORKER_PREVIEW,
            onClick = { onSelectTab(BottomPanelTab.WORKER_PREVIEW) },
            tag = "tab_worker_preview",
          )
          IconSegmentTab(
            icon = Icons.Outlined.Http,
            label = "Worker Test",
            selected = uiState.activeBottomTab == BottomPanelTab.WORKER_TEST,
            onClick = { onSelectTab(BottomPanelTab.WORKER_TEST) },
            tag = "tab_worker_test",
          )
          IconSegmentTab(
            icon = Icons.Outlined.Api,
            label = "API Tester",
            selected = uiState.activeBottomTab == BottomPanelTab.API_TESTER,
            onClick = { onSelectTab(BottomPanelTab.API_TESTER) },
            tag = "tab_api_tester",
          )
          IconSegmentTab(
            icon = Icons.Outlined.WarningAmber,
            label = "Problems",
            selected = uiState.activeBottomTab == BottomPanelTab.PROBLEMS,
            badgeText = uiState.diagnostics.size.takeIf { it > 0 }?.toString(),
            badgeColor = AmberWarn,
            onClick = { onSelectTab(BottomPanelTab.PROBLEMS) },
            tag = "tab_problems",
          )
          IconSegmentTab(
            icon = Icons.Outlined.Sensors,
            label = "Live Tail",
            selected = uiState.activeBottomTab == BottomPanelTab.TAIL_LOGS,
            badgeText = if (uiState.isTailRunning) "LIVE" else null,
            badgeColor = EmeraldLive,
            onClick = { onSelectTab(BottomPanelTab.TAIL_LOGS) },
            tag = "tab_live_tail",
          )
          IconSegmentTab(
            icon = Icons.Outlined.Dvr,
            label = "IDE Log",
            selected = uiState.activeBottomTab == BottomPanelTab.CONSOLE,
            onClick = { onSelectTab(BottomPanelTab.CONSOLE) },
            tag = "tab_ide_log",
          )
        }

        IconButton(
          onClick = onToggleExpand,
          modifier = Modifier.size(34.dp).testTag("expand_bottom_panel"),
        ) {
          Icon(
            imageVector =
              if (uiState.isBottomPanelExpanded) Icons.Outlined.CloseFullscreen
              else Icons.Outlined.OpenInFull,
            contentDescription = if (uiState.isBottomPanelExpanded) "Restore panel size" else "Maximize panel",
            tint = palette.textSecondary,
            modifier = Modifier.size(16.dp),
          )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(34.dp).testTag("close_bottom_panel")) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = "Close bottom panel",
            tint = palette.textSecondary,
            modifier = Modifier.size(17.dp),
          )
        }
      }
      HorizontalDivider(color = palette.border.copy(alpha = 0.45f))

      when (uiState.activeBottomTab) {
        BottomPanelTab.LOCAL_RUN ->
          LocalScriptRunPane(
            uiState = uiState,
            onPathChange = { p -> onUpdateLocalRunRequest(uiState.localRunMethod, p, uiState.localRunBody) },
            onMethodChange = { m -> onUpdateLocalRunRequest(m, uiState.localRunUrlPath, uiState.localRunBody) },
            onBodyChange = { b -> onUpdateLocalRunRequest(uiState.localRunMethod, uiState.localRunUrlPath, b) },
            onRunLocally = onRunWorkspaceLocally,
            onNotifyStatus = onNotifyStatus,
            onExecuteCliCommand = onExecuteCliCommand,
            modifier = Modifier.weight(1f),
          )
        BottomPanelTab.WORKER_PREVIEW ->
          DeployedWorkerPreviewPane(
            uiState = uiState,
            onSelectModeTab = onPreviewSelectModeTab,
            onUrlChange = onPreviewUrlChange,
            onReloadPreview = onReloadPreview,
            onToggleConsoleExpanded = onTogglePreviewConsoleExpanded,
            onUpdateHttpRequest = onUpdatePreviewHttp,
            onSendHttpTest = onSendPreviewHttp,
            onSelectHistoryRun = onSelectWorkerTestHistoryRun,
            onClearHistory = onClearWorkerTestHistory,
            onAppendConsoleEntry = onAppendPreviewConsole,
            onClearConsole = onClearPreviewConsole,
            onDeployActiveWorker = onDeployActiveWorker,
            onNotifyStatus = onNotifyStatus,
            isFullScreen = uiState.isBottomPanelExpanded,
            onToggleFullScreen = onToggleExpand,
            onSwitchToCode = onSwitchToCode,
            onLinkProjectFolder = onLinkProjectFolder,
            onToggleViewport = onToggleViewport,
            modifier = Modifier.weight(1f),
          )
        BottomPanelTab.WORKER_TEST ->
          WorkerTestWindow(
            uiState = uiState,
            onUrlChange = onPreviewUrlChange,
            onMethodChange = { method ->
              onUpdatePreviewHttp(method, uiState.previewHttpHeadersJson, uiState.previewHttpBody)
            },
            onHeadersJsonChange = { headersJson ->
              onUpdatePreviewHttp(uiState.previewHttpMethod, headersJson, uiState.previewHttpBody)
            },
            onBodyChange = { body ->
              onUpdatePreviewHttp(uiState.previewHttpMethod, uiState.previewHttpHeadersJson, body)
            },
            onSendRequest = onSendPreviewHttp,
            onSelectHistoryRun = onSelectWorkerTestHistoryRun,
            onClearHistory = onClearWorkerTestHistory,
            onNotifyStatus = onNotifyStatus,
            modifier = Modifier.weight(1f),
          )
        BottomPanelTab.API_TESTER ->
          ApiTesterPane(
            uiState = uiState,
            onUpdateRequest = onUpdateApiRequest,
            onUpdateEnvVars = onUpdateApiEnvVars,
            onRunRequest = onRunApiRequest,
            onSaveRequest = onSaveApiRequest,
            onLoadRequest = onLoadApiRequest,
            onDeleteCollectionItem = onDeleteApiCollectionItem,
            onClearHistory = onClearApiHistory,
            onNotifyStatus = onNotifyStatus,
            onSelectEnvProfile = onSelectApiEnvProfile,
            onCreateEnvProfile = onCreateApiEnvProfile,
            onDeleteEnvProfile = onDeleteApiEnvProfile,
            onImportCollection = onImportApiCollection,
            modifier = Modifier.weight(1f),
          )
        BottomPanelTab.PROBLEMS ->
          ProblemsView(uiState = uiState, onJumpToProblem = onJumpToProblem, onNotifyStatus = onNotifyStatus)
        BottomPanelTab.TAIL_LOGS ->
          LiveTailView(uiState = uiState, onToggleLiveTail = onToggleLiveTail, onNotifyStatus = onNotifyStatus)
        BottomPanelTab.CONSOLE ->
          ConsoleView(uiState = uiState, onNotifyStatus = onNotifyStatus)
      }
    }
  }
}

@Composable
fun ProblemsView(
  uiState: IdeUiState,
  onJumpToProblem: (String, Int) -> Unit,
  onNotifyStatus: (String) -> Unit = {},
) {
  val palette = LocalIdePalette.current
  var severityFilter by remember { mutableStateOf("ALL") }

  if (uiState.diagnostics.isEmpty()) {
    var showInfo by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(palette.editorBg), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(onClick = { showInfo = !showInfo }) {
          Icon(
            imageVector = Icons.Outlined.CheckCircleOutline,
            contentDescription = "No diagnostics",
            tint = EmeraldLive,
            modifier = Modifier.size(24.dp),
          )
        }
        AnimatedVisibility(visible = showInfo) {
          Text(
            text = "0 problems",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = palette.textMuted,
          )
        }
      }
    }
    return
  }

  val errorCount = remember(uiState.diagnostics) { uiState.diagnostics.count { it.severity == DiagnosticSeverity.ERROR } }
  val warnCount = remember(uiState.diagnostics) { uiState.diagnostics.count { it.severity == DiagnosticSeverity.WARNING } }
  val filtered =
    remember(uiState.diagnostics, severityFilter) {
      when (severityFilter) {
        "ERR" -> uiState.diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }
        "WARN" -> uiState.diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }
        else -> uiState.diagnostics
      }
    }

  Column(modifier = Modifier.fillMaxSize().background(palette.editorBg)) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeSurface)
          .padding(horizontal = 8.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      listOf(
        "ALL" to "All (${uiState.diagnostics.size})",
        "ERR" to "Errors ($errorCount)",
        "WARN" to "Warnings ($warnCount)",
      ).forEach { (key, label) ->
        val selected = severityFilter == key
        Surface(
          onClick = { severityFilter = key },
          color = if (selected) palette.activePillBg else palette.editorBg,
          shape = RoundedCornerShape(6.dp),
          border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.55f)),
        ) {
          Text(
            text = label,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) palette.activePillFg else palette.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
    }
    HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.5.dp)

    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      items(filtered) { diag ->
        val badgeColor =
          when (diag.severity) {
            DiagnosticSeverity.ERROR -> RoseError
            DiagnosticSeverity.WARNING -> AmberWarn
            DiagnosticSeverity.INFO -> palette.activePillFg
          }
        Surface(
          onClick = { onJumpToProblem(diag.filePath, diag.line) },
          shape = RoundedCornerShape(8.dp),
          color = palette.chromeSurface,
          border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(modifier = Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
              color = badgeColor.copy(alpha = 0.14f),
              shape = RoundedCornerShape(5.dp),
              border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.35f)),
            ) {
              Text(
                text = diag.severity.name,
                fontFamily = JetBrainsMono,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              SmartDraggableText(
                text = diag.message,
                surfaceId = "problem_msg_${diag.filePath}_${diag.line}",
                domain = SmartTextDomain.TERMINAL_OR_LOG,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
                color = palette.textPrimary,
                onCopied = { onNotifyStatus("Copied diagnostic") },
              )
              SmartDraggableText(
                text = "${diag.filePath} • Ln ${diag.line}, Col ${diag.column}",
                surfaceId = "problem_loc_${diag.filePath}_${diag.line}",
                domain = SmartTextDomain.TERMINAL_OR_LOG,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
                color = palette.textMuted,
                onCopied = { onNotifyStatus("Copied location") },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun ConsoleView(
  uiState: IdeUiState,
  onNotifyStatus: (String) -> Unit = {},
) {
  val palette = LocalIdePalette.current
  var levelFilter by remember { mutableStateOf("ALL") }
  val logs =
    remember(uiState.consoleLogs, levelFilter) {
      val rev = uiState.consoleLogs.reversed()
      when (levelFilter) {
        "ERR" -> rev.filter { it.level.equals("ERROR", true) || it.level.equals("ERR", true) }
        "INFO" -> rev.filter { it.level.equals("INFO", true) || it.level.equals("LOG", true) }
        else -> rev
      }
    }

  Column(modifier = Modifier.fillMaxSize().background(palette.editorBg)) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeSurface)
          .padding(horizontal = 8.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      listOf("ALL", "INFO", "ERR").forEach { key ->
        val selected = levelFilter == key
        Surface(
          onClick = { levelFilter = key },
          color = if (selected) palette.activePillBg else palette.editorBg,
          shape = RoundedCornerShape(6.dp),
          border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.55f)),
        ) {
          Text(
            text = key,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) palette.activePillFg else palette.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
      Spacer(Modifier.weight(1f))
      Text(
        text = "${logs.size}",
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        color = palette.textMuted,
      )
    }
    HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.5.dp)

    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      items(logs) { log ->
        val isErr = log.level.equals("ERROR", true) || log.level.equals("ERR", true)
        val levelColor = if (isErr) RoseError else palette.activePillFg
        val annotated =
          remember(log.message, palette) {
            SyntaxHighlighter.highlightResponse(log.message, null, palette)
          }
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(palette.chromeSurface)
              .border(0.5.dp, palette.border.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
              .padding(horizontal = 8.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(log.timestamp, fontFamily = JetBrainsMono, fontSize = 10.sp, color = palette.textMuted)
          Text(
            log.level,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = levelColor,
          )
          SmartDraggableText(
            text = log.message,
            annotatedText = annotated,
            surfaceId = "ide_console_${log.timestamp}_${log.message.hashCode()}",
            domain = SmartTextDomain.TERMINAL_OR_LOG,
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
            color = palette.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            onCopied = { onNotifyStatus("Copied log snippet") },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
fun LiveTailView(
  uiState: IdeUiState,
  onToggleLiveTail: () -> Unit,
  onNotifyStatus: (String) -> Unit = {},
) {
  val palette = LocalIdePalette.current
  Column(
    modifier = Modifier.fillMaxSize().background(palette.editorBg),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeSurface)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          color =
            if (uiState.isTailRunning) EmeraldLive.copy(alpha = 0.16f)
            else palette.editorBg,
          shape = RoundedCornerShape(6.dp),
          border =
            BorderStroke(
              0.5.dp,
              if (uiState.isTailRunning) EmeraldLive.copy(alpha = 0.4f) else palette.border.copy(alpha = 0.5f),
            ),
        ) {
          Text(
            text = if (uiState.isTailRunning) "STREAMING" else "IDLE",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = if (uiState.isTailRunning) EmeraldLive else palette.textSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
        Text(
          text = uiState.workspace?.scriptName ?: "No Worker",
          fontFamily = JetBrainsMono,
          fontSize = 11.sp,
          color = palette.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconRevealButton(
        icon = if (uiState.isTailRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
        revealedLabel = if (uiState.isTailRunning) "Stop Tail" else "Start Tail",
        active = uiState.isTailRunning,
        onClick = {
          if (!uiState.isLocalScratchpad && uiState.workspace != null) {
            onToggleLiveTail()
          }
        },
        height = 32.dp,
        tag = "toggle_live_tail_button",
      )
    }
    HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.5.dp)

    if (uiState.isLocalScratchpad || uiState.tailLogs.isEmpty()) {
      var showTailInfo by remember { mutableStateOf(false) }
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
          IconButton(onClick = { showTailInfo = !showTailInfo }) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = "Live tail info",
              tint = palette.textSecondary,
              modifier = Modifier.size(22.dp),
            )
          }
          AnimatedVisibility(visible = showTailInfo) {
            Text(
              text =
                when {
                  uiState.isLocalScratchpad -> "Sign in to Cloudflare to stream live logs."
                  uiState.isTailRunning -> "Listening for live events…"
                  else -> "Tap Start Tail to stream logs."
                },
              style = MaterialTheme.typography.bodySmall,
              color = palette.textSecondary,
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(uiState.tailLogs.reversed()) { event ->
          val annotated =
            remember(event.message, palette) {
              SyntaxHighlighter.highlightResponse(event.message, null, palette)
            }
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(palette.chromeSurface)
                .border(0.5.dp, palette.border.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = event.timestamp,
              fontFamily = JetBrainsMono,
              fontSize = 10.sp,
              color = EmeraldLive,
            )
            SmartDraggableText(
              text = event.message,
              annotatedText = annotated,
              surfaceId = "live_tail_${event.timestamp}_${event.message.hashCode()}",
              domain = SmartTextDomain.TERMINAL_OR_LOG,
              style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
              color = palette.textPrimary,
              onCopied = { onNotifyStatus("Copied tail event") },
            )
          }
        }
      }
    }
  }
}



