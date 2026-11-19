package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.WorkerTestRunRecord
import com.example.data.remote.ApiEnvVar
import com.example.data.remote.ApiImportResult
import com.example.data.remote.ApiTesterRequest
import com.example.data.remote.DiagnosticSeverity
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
  if (uiState.diagnostics.isEmpty()) {
    var showInfo by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    return
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    items(uiState.diagnostics) { diag ->
      val badgeColor =
        when (diag.severity) {
          DiagnosticSeverity.ERROR -> RoseError
          DiagnosticSeverity.WARNING -> AmberWarn
          DiagnosticSeverity.INFO -> MaterialTheme.colorScheme.primary
        }
      Surface(
        onClick = { onJumpToProblem(diag.filePath, diag.line) },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Surface(color = badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
            Text(
              text = diag.severity.name,
              style = MaterialTheme.typography.labelSmall,
              color = badgeColor,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
          Spacer(Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            SmartDraggableText(
              text = diag.message,
              surfaceId = "problem_msg_${diag.filePath}_${diag.line}",
              domain = SmartTextDomain.TERMINAL_OR_LOG,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
              onCopied = { onNotifyStatus("Copied diagnostic") },
            )
            SmartDraggableText(
              text = "${diag.filePath} • Ln ${diag.line}, Col ${diag.column}",
              surfaceId = "problem_loc_${diag.filePath}_${diag.line}",
              domain = SmartTextDomain.TERMINAL_OR_LOG,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              onCopied = { onNotifyStatus("Copied location") },
            )
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
  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    items(uiState.consoleLogs.reversed()) { log ->
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(log.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          log.level,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = if (log.level == "ERROR") RoseError else MaterialTheme.colorScheme.primary,
        )
        SmartDraggableText(
          text = log.message,
          surfaceId = "ide_console_${log.timestamp}_${log.message.hashCode()}",
          domain = SmartTextDomain.TERMINAL_OR_LOG,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          onCopied = { onNotifyStatus("Copied log snippet") },
          modifier = Modifier.weight(1f),
        )
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
  Column(
    modifier = Modifier.fillMaxSize().padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(
          color =
            if (uiState.isTailRunning) EmeraldLive.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surfaceContainerHighest,
          shape = RoundedCornerShape(999.dp),
        ) {
          Text(
            text = if (uiState.isTailRunning) "STREAMING" else "IDLE",
            style = MaterialTheme.typography.labelSmall,
            color = if (uiState.isTailRunning) EmeraldLive else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
        Text(
          text = uiState.workspace?.scriptName ?: "No Worker",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        height = 34.dp,
        tag = "toggle_live_tail_button",
      )
    }

    if (uiState.isLocalScratchpad || uiState.tailLogs.isEmpty()) {
      var showTailInfo by remember { mutableStateOf(false) }
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
          IconButton(onClick = { showTailInfo = !showTailInfo }) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = "Live tail info",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(uiState.tailLogs.reversed()) { event ->
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
          ) {
            Text(
              text = event.timestamp,
              style = MaterialTheme.typography.labelSmall,
              color = EmeraldLive,
            )
            SmartDraggableText(
              text = event.message,
              surfaceId = "live_tail_${event.timestamp}_${event.message.hashCode()}",
              domain = SmartTextDomain.TERMINAL_OR_LOG,
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
              color = MaterialTheme.colorScheme.onSurface,
              onCopied = { onNotifyStatus("Copied tail event") },
            )
          }
        }
      }
    }
  }
}



