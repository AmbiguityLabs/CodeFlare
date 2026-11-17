package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.terminal.JqEvaluator
import com.example.terminal.TerminalEntry
import com.example.terminal.TerminalEntryKind
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.IdeUiState

@Composable
fun LinuxTerminalPane(
  uiState: IdeUiState,
  onInputChange: (String) -> Unit,
  onRunCommand: (String) -> Unit,
  onPasteText: (String) -> Unit,
  onAutocomplete: () -> Unit,
  onRecallHistory: (older: Boolean) -> Unit,
  onClearTerminal: () -> Unit,
  onSaveEntryToFile: (fileName: String, content: String) -> Unit,
  onNotifyCopied: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current
  val listState = rememberLazyListState()
  var inspectedEntry by remember { mutableStateOf<TerminalEntry?>(null) }
  var pendingSaveEntry by remember { mutableStateOf<TerminalEntry?>(null) }

  LaunchedEffect(uiState.terminalEntries.size) {
    if (uiState.terminalEntries.isNotEmpty()) {
      listState.animateScrollToItem(uiState.terminalEntries.lastIndex)
    }
  }

  val copyText: (label: String, content: String) -> Unit = { label, content ->
    clipboard.setText(AnnotatedString(content))
    onNotifyCopied("Copied $label to clipboard")
  }

  inspectedEntry?.let { entry ->
    TerminalCopyInspectorDialog(
      entry = entry,
      onCopy = copyText,
      onDismiss = { inspectedEntry = null },
    )
  }

  pendingSaveEntry?.let { entry ->
    var targetFileName by remember(entry.id) { mutableStateOf("output-${entry.id}.txt") }
    AlertDialog(
      onDismissRequest = { pendingSaveEntry = null },
      title = { Text("Save Terminal Output to Workspace") },
      text = {
        OutlinedTextField(
          value = targetFileName,
          onValueChange = { targetFileName = it },
          label = { Text("Workspace File Path") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("terminal_save_filename_input"),
        )
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onSaveEntryToFile(targetFileName, entry.httpMeta?.bodyText?.ifBlank { entry.output } ?: entry.output)
            pendingSaveEntry = null
          },
          enabled = targetFileName.isNotBlank(),
        ) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { pendingSaveEntry = null }) { Text("Cancel") } },
    )
  }

  Column(
    modifier = modifier.fillMaxSize().background(palette.editorBg),
  ) {
    // Scrollable Terminal Output Feed (Native Selection + Per-Block 1-Tap Copy)
    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (uiState.terminalEntries.isEmpty()) {
        item {
          Surface(
            color = palette.chromeSurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Linux + Cloudflare Edge Terminal Ready",
                style = MaterialTheme.typography.titleSmall,
                color = palette.activePillFg,
              )
              Text(
                text = "Run curl -i / , POST /echo '{\"ok\":true}', wrangler deploy, ls -la, cat src/index.js, jq ., or uname -a. Long-press any text or tap Copy chips below any output.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
              )
            }
          }
        }
      } else {
        items(uiState.terminalEntries, key = { it.id }) { entry ->
          TerminalEntryCard(
            entry = entry,
            onCopy = copyText,
            onRerun = { onRunCommand(entry.command) },
            onSave = { pendingSaveEntry = entry },
            onShare = {
              val shareBody = buildString {
                appendLine("$ ${entry.command}")
                append(entry.output)
              }
              val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, shareBody)
                }
              runCatching {
                context.startActivity(Intent.createChooser(shareIntent, "Share terminal output"))
              }
            },
            onOpenSelectDialog = { inspectedEntry = entry },
          )
        }
      }
    }

    HorizontalDivider(color = palette.border)

    // Quick Copy/Paste & Shortcut Bar (solves touch terminal copy/paste friction)
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeSurface)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 8.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        onClick = {
          val clip = clipboard.getText()?.text
          if (!clip.isNullOrEmpty()) {
            onPasteText(clip)
          } else {
            onNotifyCopied("Clipboard is empty")
          }
        },
        shape = RoundedCornerShape(8.dp),
        color = palette.activePillBg,
        modifier = Modifier.testTag("terminal_paste_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            Icons.Outlined.ContentPaste,
            contentDescription = "Paste clipboard to terminal",
            tint = palette.activePillFg,
            modifier = Modifier.size(14.dp),
          )
          Text(
            "Paste",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.activePillFg,
          )
        }
      }

      val lastOut = uiState.terminalEntries.lastOrNull()?.let { it.httpMeta?.bodyText ?: it.output }
      if (!lastOut.isNullOrBlank()) {
        TerminalQuickChip(
          label = "Copy Last",
          icon = Icons.Outlined.ContentCopy,
          onClick = { copyText("last output", lastOut) },
        )
      }

      TerminalQuickChip(
        label = "Prev",
        icon = Icons.Outlined.KeyboardArrowUp,
        onClick = { onRecallHistory(true) },
      )
      TerminalQuickChip(
        label = "Next",
        icon = Icons.Outlined.KeyboardArrowDown,
        onClick = { onRecallHistory(false) },
      )
      TerminalQuickChip(label = "Tab", onClick = onAutocomplete)
      TerminalQuickChip(label = "curl -i /", onClick = { onInputChange("curl -i /") })
      TerminalQuickChip(
        label = "POST /",
        onClick = { onInputChange("curl -i -X POST -H \"Content-Type: application/json\" -d '{\"ping\":\"edge\"}' /") },
      )
      TerminalQuickChip(label = "jq .", onClick = { onInputChange("curl -s / | jq .") })
      TerminalQuickChip(label = "wrangler dev", onClick = { onRunCommand("wrangler dev") })
      TerminalQuickChip(label = "wrangler deploy", onClick = { onRunCommand("wrangler deploy") })
      TerminalQuickChip(label = "wrangler versions", onClick = { onRunCommand("wrangler versions") })
      TerminalQuickChip(label = "wrangler d1 list", onClick = { onRunCommand("wrangler d1 list") })
      TerminalQuickChip(label = "wrangler r2 bucket list", onClick = { onRunCommand("wrangler r2 bucket list") })
      TerminalQuickChip(label = "wrangler pages project list", onClick = { onRunCommand("wrangler pages project list") })
      TerminalQuickChip(label = "wrangler tail", onClick = { onRunCommand("wrangler tail") })
      TerminalQuickChip(label = "ls -la", onClick = { onRunCommand("ls -la") })
      TerminalQuickChip(label = "uname -a", onClick = { onRunCommand("uname -a") })
      TerminalQuickChip(
        label = "Clear",
        icon = Icons.Outlined.DeleteSweep,
        onClick = onClearTerminal,
      )
    }

    // Active Linux Prompt Input Row
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeBg)
          .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = "${uiState.terminalPromptPath}$",
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        color = palette.activePillFg,
        maxLines = 1,
      )

      Box(
        modifier =
          Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.editorBg)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
      ) {
        if (uiState.terminalInput.isEmpty()) {
          Text(
            text = "curl -i /health  |  ls -la  |  wrangler deploy  |  paste command…",
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp),
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        BasicTextField(
          value = uiState.terminalInput,
          onValueChange = onInputChange,
          textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.5.sp, color = palette.textPrimary),
          cursorBrush = SolidColor(palette.activePillFg),
          maxLines = 4,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = { onRunCommand(uiState.terminalInput) }),
          modifier = Modifier.fillMaxWidth().testTag("terminal_command_input"),
        )
      }

      IconButton(
        onClick = { onRunCommand(uiState.terminalInput) },
        enabled = !uiState.isTerminalBusy && uiState.terminalInput.isNotBlank(),
        modifier = Modifier.size(40.dp).testTag("terminal_run_button"),
      ) {
        if (uiState.isTerminalBusy) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Send,
            contentDescription = "Execute terminal command",
            tint = if (uiState.terminalInput.isNotBlank()) palette.activePillFg else palette.textMuted,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun TerminalEntryCard(
  entry: TerminalEntry,
  onCopy: (label: String, content: String) -> Unit,
  onRerun: () -> Unit,
  onSave: () -> Unit,
  onShare: () -> Unit,
  onOpenSelectDialog: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val borderColor =
    when {
      entry.isError || entry.kind == TerminalEntryKind.ERROR -> RoseError.copy(alpha = 0.45f)
      entry.kind == TerminalEntryKind.HTTP -> palette.activePillBg
      else -> palette.border.copy(alpha = 0.7f)
    }

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(palette.chromeSurface)
        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // Prompt & Command Header + Copy/Re-run
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SelectionContainer(modifier = Modifier.weight(1f)) {
        Text(
          text = "${entry.promptPrefix} ${entry.command}",
          style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
          color = palette.activePillFg,
        )
      }
      IconButton(
        onClick = { onCopy("command", entry.command) },
        modifier = Modifier.size(28.dp),
      ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy command", tint = palette.textSecondary, modifier = Modifier.size(14.dp))
      }
      IconButton(
        onClick = onRerun,
        modifier = Modifier.size(28.dp),
      ) {
        Icon(Icons.Outlined.PlayArrow, contentDescription = "Re-run command", tint = palette.textSecondary, modifier = Modifier.size(15.dp))
      }
    }

    // Real HTTP Status + DNS/TLS/TTFB Trace Badge
    entry.httpMeta?.let { meta ->
      val statusColor =
        when {
          meta.statusCode in 200..299 -> EmeraldLive
          meta.statusCode in 300..399 -> AmberWarn
          else -> RoseError
        }
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(color = statusColor.copy(alpha = 0.16f), shape = RoundedCornerShape(999.dp)) {
          Text(
            text = "${meta.protocol} ${meta.statusCode} ${meta.statusText}",
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }
        Text(
          text = meta.timingSummary,
          style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
          color = palette.textSecondary,
        )
      }
    }

    // Native Drag-Selectable Terminal Output
    if (entry.output.isNotBlank()) {
      SelectionContainer {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(palette.editorBg)
              .horizontalScroll(rememberScrollState())
              .padding(8.dp),
        ) {
          Text(
            text = entry.output,
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, lineHeight = 16.5.sp),
            color = if (entry.isError) RoseError else palette.textPrimary,
          )
        }
      }
    }

    // Effortless 1-Tap Copy Strip
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TerminalMiniAction(
        label = "Copy Output",
        icon = Icons.Outlined.ContentCopy,
        onClick = { onCopy("output", entry.httpMeta?.bodyText?.ifBlank { entry.output } ?: entry.output) },
      )
      entry.httpMeta?.let { meta ->
        if (meta.headersText.isNotBlank()) {
          TerminalMiniAction(
            label = "Copy Headers",
            onClick = { onCopy("headers", meta.headersText) },
          )
        }
        TerminalMiniAction(
          label = "Copy cURL",
          onClick = { onCopy("cURL", meta.curlEquivalent) },
        )
      }
      TerminalMiniAction(
        label = "Select / Extract",
        icon = Icons.Outlined.SelectAll,
        onClick = onOpenSelectDialog,
      )
      TerminalMiniAction(
        label = "Save",
        icon = Icons.Outlined.Save,
        onClick = onSave,
      )
      TerminalMiniAction(
        label = "Share",
        icon = Icons.Outlined.Share,
        onClick = onShare,
      )
    }
  }
}

@Composable
private fun TerminalQuickChip(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = palette.chromePill,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(13.dp))
      }
      Text(
        text = label,
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
        color = palette.textPrimary,
      )
    }
  }
}

@Composable
private fun TerminalMiniAction(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(7.dp),
    color = palette.chromePill,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(12.dp))
      }
      Text(
        text = label,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        color = palette.textSecondary,
      )
    }
  }
}

@Composable
private fun TerminalCopyInspectorDialog(
  entry: TerminalEntry,
  onCopy: (label: String, content: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val rawText = entry.output
  val bodyForJson = entry.httpMeta?.bodyText ?: entry.output
  val jsonFields = remember(bodyForJson) { JqEvaluator.extractCopyableFields(bodyForJson) }
  val lines = remember(rawText) { rawText.lines().filter { it.isNotEmpty() } }
  var mode by remember { mutableIntStateOf(0) } // 0 = Free Select, 1 = Line Tap, 2 = JSON Fields

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select & Copy Terminal Text") },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          InspectorTabPill("Drag Select", selected = mode == 0, onClick = { mode = 0 }, modifier = Modifier.weight(1f))
          InspectorTabPill("Tap Line", selected = mode == 1, onClick = { mode = 1 }, modifier = Modifier.weight(1f))
          if (jsonFields.isNotEmpty()) {
            InspectorTabPill("JSON Fields", selected = mode == 2, onClick = { mode = 2 }, modifier = Modifier.weight(1f))
          }
        }

        when (mode) {
          0 -> {
            Text(
              text = "Touch & drag anywhere below to select any exact substring:",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .weight(1f, fill = false)
                  .heightIn(min = 140.dp, max = 280.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(palette.editorBg)
                  .verticalScroll(rememberScrollState())
                  .padding(10.dp),
            ) {
              BasicTextField(
                value = rawText,
                onValueChange = {},
                readOnly = true,
                textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textPrimary),
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
          1 -> {
            Text(
              text = "Tap any line to copy that exact line:",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
              modifier = Modifier.heightIn(max = 280.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              items(lines) { line ->
                Surface(
                  onClick = { onCopy("line", line) },
                  shape = RoundedCornerShape(8.dp),
                  color = palette.chromeSurface,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = line,
                      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
                      color = palette.textPrimary,
                      maxLines = 3,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy line", tint = palette.activePillFg, modifier = Modifier.size(14.dp))
                  }
                }
              }
            }
          }
          2 -> {
            Text(
              text = "Tap any JSON field to copy its exact value:",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
              modifier = Modifier.heightIn(max = 280.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              items(jsonFields) { (k, v) ->
                Surface(
                  onClick = { onCopy(k, v) },
                  shape = RoundedCornerShape(8.dp),
                  color = palette.chromeSurface,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(k, style = MaterialTheme.typography.labelSmall, color = palette.activePillFg)
                      Text(
                        v,
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy $k", tint = palette.activePillFg, modifier = Modifier.size(14.dp))
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      FilledTonalButton(onClick = { onCopy("full block", rawText) }) {
        Text("Copy All")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Done") }
    },
  )
}

@Composable
private fun InspectorTabPill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(9.dp),
    color = if (selected) palette.activePillBg else palette.chromePill,
    modifier = modifier,
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color = if (selected) palette.activePillFg else palette.textSecondary,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
    )
  }
}
