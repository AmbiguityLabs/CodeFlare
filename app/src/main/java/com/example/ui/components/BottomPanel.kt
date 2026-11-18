package com.example.ui.components

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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
  onTerminalInputChange: (String) -> Unit,
  onRunTerminalCommand: (String) -> Unit,
  onPasteIntoTerminal: (String) -> Unit,
  onAutocompleteTerminal: () -> Unit,
  onRecallTerminalHistory: (Boolean) -> Unit,
  onClearTerminal: () -> Unit,
  onSaveTerminalOutputToFile: (String, String) -> Unit,
  onNotifyStatus: (String) -> Unit,
  onJumpToProblem: (String, Int) -> Unit,
  onToggleLiveTail: () -> Unit,
  onSelectApiEnvProfile: (String) -> Unit = {},
  onCreateApiEnvProfile: (String) -> Unit = {},
  onDeleteApiEnvProfile: (String) -> Unit = {},
  onImportApiCollection: (String, Boolean, Boolean) -> Result<ApiImportResult> = { _, _, _ ->
    Result.failure(UnsupportedOperationException("Collection import not wired"))
  },
  onInsertTerminalToken: (String) -> Unit = { token -> onTerminalInputChange(uiState.terminalInput + token) },
  onCreateTerminalSession: () -> Unit = {},
  onSwitchTerminalSession: (String) -> Unit = {},
  onCloseTerminalSession: (String) -> Unit = {},
  onInterruptTerminalCommand: () -> Unit = {},
  onSendTerminalStdin: (String) -> Unit = {},
  onSaveTerminalQuickEdit: (String, String) -> Unit = { _, _ -> },
  onDismissTerminalQuickEdit: () -> Unit = {},
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
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          BottomPanelTabChip(
            label = "Preview",
            selected = uiState.activeBottomTab == BottomPanelTab.WORKER_PREVIEW,
            onClick = { onSelectTab(BottomPanelTab.WORKER_PREVIEW) },
            modifier = Modifier.testTag("tab_worker_preview"),
          )
          BottomPanelTabChip(
            label = "Worker Test",
            selected = uiState.activeBottomTab == BottomPanelTab.WORKER_TEST,
            onClick = { onSelectTab(BottomPanelTab.WORKER_TEST) },
            modifier = Modifier.testTag("tab_worker_test"),
          )
          BottomPanelTabChip(
            label = "API Tester",
            selected = uiState.activeBottomTab == BottomPanelTab.API_TESTER,
            onClick = { onSelectTab(BottomPanelTab.API_TESTER) },
            modifier = Modifier.testTag("tab_api_tester"),
          )
          BottomPanelTabChip(
            label = "Terminal",
            selected = uiState.activeBottomTab == BottomPanelTab.TERMINAL,
            onClick = { onSelectTab(BottomPanelTab.TERMINAL) },
          )
          BottomPanelTabChip(
            label = "Problems",
            selected = uiState.activeBottomTab == BottomPanelTab.PROBLEMS,
            badgeText = uiState.diagnostics.size.takeIf { it > 0 }?.toString(),
            badgeColor = AmberWarn,
            onClick = { onSelectTab(BottomPanelTab.PROBLEMS) },
          )
          BottomPanelTabChip(
            label = "Live Tail",
            selected = uiState.activeBottomTab == BottomPanelTab.TAIL_LOGS,
            badgeText = if (uiState.isTailRunning) "LIVE" else null,
            badgeColor = EmeraldLive,
            onClick = { onSelectTab(BottomPanelTab.TAIL_LOGS) },
          )
          BottomPanelTabChip(
            label = "IDE Log",
            selected = uiState.activeBottomTab == BottomPanelTab.CONSOLE,
            onClick = { onSelectTab(BottomPanelTab.CONSOLE) },
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
            contentDescription = if (uiState.isBottomPanelExpanded) "Restore panel size" else "Maximize terminal panel",
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
        BottomPanelTab.TERMINAL ->
          LinuxTerminalPane(
            uiState = uiState,
            onInputChange = onTerminalInputChange,
            onRunCommand = onRunTerminalCommand,
            onPasteText = onPasteIntoTerminal,
            onAutocomplete = onAutocompleteTerminal,
            onRecallHistory = onRecallTerminalHistory,
            onClearTerminal = onClearTerminal,
            onSaveEntryToFile = onSaveTerminalOutputToFile,
            onNotifyCopied = onNotifyStatus,
            onInsertToken = onInsertTerminalToken,
            onCreateSession = onCreateTerminalSession,
            onSwitchSession = onSwitchTerminalSession,
            onCloseSession = onCloseTerminalSession,
            onInterruptCommand = onInterruptTerminalCommand,
            onSendStdin = onSendTerminalStdin,
            onSaveQuickEdit = onSaveTerminalQuickEdit,
            onDismissQuickEdit = onDismissTerminalQuickEdit,
            modifier = Modifier.weight(1f),
          )
        BottomPanelTab.PROBLEMS ->
          ProblemsView(uiState = uiState, onJumpToProblem = onJumpToProblem)
        BottomPanelTab.TAIL_LOGS ->
          LiveTailView(uiState = uiState, onToggleLiveTail = onToggleLiveTail)
        BottomPanelTab.CONSOLE ->
          ConsoleView(uiState = uiState)
      }
    }
  }
}

@Composable
fun ProblemsView(
  uiState: IdeUiState,
  onJumpToProblem: (String, Int) -> Unit,
) {
  if (uiState.diagnostics.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("No syntax or Worker diagnostics detected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(diag.message, style = MaterialTheme.typography.bodySmall)
            Text(
              "${diag.filePath} • Ln ${diag.line}, Col ${diag.column}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
fun ConsoleView(uiState: IdeUiState) {
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
        Text(
          log.message,
          style = MaterialTheme.typography.labelSmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
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

      FilledTonalButton(
        onClick = onToggleLiveTail,
        enabled = !uiState.isLocalScratchpad && uiState.workspace != null,
        modifier = Modifier.testTag("toggle_live_tail_button"),
      ) {
        Icon(
          imageVector = if (uiState.isTailRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
          contentDescription = if (uiState.isTailRunning) "Stop Live Tail" else "Start Live Tail",
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(if (uiState.isTailRunning) "Stop Tail" else "Start Tail")
      }
    }

    if (uiState.isLocalScratchpad) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text = "Connect your Cloudflare account to stream real-time edge Worker WebSocket tail logs.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else if (uiState.tailLogs.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text =
            if (uiState.isTailRunning) "Listening for incoming requests & console.log events…"
            else "Tap Start Tail to stream real-time console.log() & exceptions from Cloudflare Edge.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            Text(
              text = event.message,
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BottomPanelTabChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badgeText: String? = null,
  badgeColor: Color? = null,
) {
  val palette = LocalIdePalette.current
  val fg = if (selected) palette.activePillFg else palette.textSecondary

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(7.dp),
    color = if (selected) palette.activePillBg else Color.Transparent,
    border =
      if (selected) BorderStroke(1.dp, palette.activePillFg.copy(alpha = 0.25f))
      else null,
    modifier = modifier.height(28.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = fg,
        maxLines = 1,
        softWrap = false,
      )
      if (!badgeText.isNullOrBlank()) {
        val bColor = badgeColor ?: palette.activePillFg
        Box(
          modifier =
            Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(bColor.copy(alpha = 0.16f))
              .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
          Text(
            text = badgeText,
            fontFamily = JetBrainsMono,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = bColor,
            maxLines = 1,
            softWrap = false,
          )
        }
      }
    }
  }
}

