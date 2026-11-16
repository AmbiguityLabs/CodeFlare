package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.DiagnosticSeverity
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.BottomPanelTab
import com.example.ui.viewmodel.IdeUiState

@Composable
fun BottomPanel(
  uiState: IdeUiState,
  onSelectTab: (BottomPanelTab) -> Unit,
  onToggleExpand: () -> Unit,
  onClose: () -> Unit,
  onRunActiveFileLocally: () -> Unit,
  onImportAndRunFile: () -> Unit,
  onCreateAndRunFile: () -> Unit,
  onUpdateRunRequest: (String, String, String, String) -> Unit,
  onTerminalInputChange: (String) -> Unit,
  onRunTerminalCommand: (String) -> Unit,
  onPasteIntoTerminal: (String) -> Unit,
  onAutocompleteTerminal: () -> Unit,
  onRecallTerminalHistory: (Boolean) -> Unit,
  onClearTerminal: () -> Unit,
  onNotifyStatus: (String) -> Unit,
  onJumpToProblem: (String, Int) -> Unit,
  onToggleLiveTail: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 2.dp,
    modifier =
      modifier.fillMaxWidth().then(
        if (uiState.isBottomPanelExpanded) Modifier else Modifier.height(320.dp),
      ),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ScrollableTabRow(
          selectedTabIndex = uiState.activeBottomTab.ordinal,
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          edgePadding = 8.dp,
          modifier = Modifier.weight(1f),
        ) {
          Tab(
            selected = uiState.activeBottomTab == BottomPanelTab.RUN_PREVIEW,
            onClick = { onSelectTab(BottomPanelTab.RUN_PREVIEW) },
            text = { Text("Run & Result", maxLines = 1) },
          )
          Tab(
            selected = uiState.activeBottomTab == BottomPanelTab.TERMINAL,
            onClick = { onSelectTab(BottomPanelTab.TERMINAL) },
            text = { Text("Terminal", maxLines = 1) },
          )
          Tab(
            selected = uiState.activeBottomTab == BottomPanelTab.PROBLEMS,
            onClick = { onSelectTab(BottomPanelTab.PROBLEMS) },
            text = { Text("Problems (${uiState.diagnostics.size})", maxLines = 1) },
          )
          Tab(
            selected = uiState.activeBottomTab == BottomPanelTab.TAIL_LOGS,
            onClick = { onSelectTab(BottomPanelTab.TAIL_LOGS) },
            text = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                if (uiState.isTailRunning) {
                  Box(
                    modifier =
                      Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(EmeraldLive),
                  )
                }
                Text("Live Tail", maxLines = 1)
              }
            },
          )
          Tab(
            selected = uiState.activeBottomTab == BottomPanelTab.CONSOLE,
            onClick = { onSelectTab(BottomPanelTab.CONSOLE) },
            text = { Text("IDE Log", maxLines = 1) },
          )
        }
        IconButton(
          onClick = onToggleExpand,
          modifier = Modifier.size(38.dp).testTag("expand_bottom_panel"),
        ) {
          Icon(
            imageVector =
              if (uiState.isBottomPanelExpanded) Icons.Outlined.CloseFullscreen
              else Icons.Outlined.OpenInFull,
            contentDescription = if (uiState.isBottomPanelExpanded) "Restore panel size" else "Maximize terminal panel",
            modifier = Modifier.size(17.dp),
          )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(38.dp).testTag("close_bottom_panel")) {
          Icon(Icons.Outlined.Close, contentDescription = "Close bottom panel", modifier = Modifier.size(18.dp))
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      when (uiState.activeBottomTab) {
        BottomPanelTab.RUN_PREVIEW ->
          LocalRunPreviewPane(
            uiState = uiState,
            onRunActiveFile = onRunActiveFileLocally,
            onImportAndRunFile = onImportAndRunFile,
            onCreateAndRunFile = onCreateAndRunFile,
            onUpdateRequest = onUpdateRunRequest,
            onNotifyCopied = onNotifyStatus,
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
            onNotifyCopied = onNotifyStatus,
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
private fun ProblemsView(
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
private fun ConsoleView(uiState: IdeUiState) {
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
private fun LiveTailView(
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
