package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
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
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.terminal.JqEvaluator
import com.example.terminal.TerminalEntry
import com.example.terminal.TerminalEntryKind
import com.example.terminal.TerminalQuickEditTarget
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
  onInsertToken: (String) -> Unit = { token -> onInputChange(uiState.terminalInput + token) },
  onCreateSession: () -> Unit = {},
  onSwitchSession: (String) -> Unit = {},
  onCloseSession: (String) -> Unit = {},
  onInterruptCommand: () -> Unit = {},
  onSendStdin: (String) -> Unit = {},
  onSaveQuickEdit: (filePath: String, content: String) -> Unit = { _, _ -> },
  onDismissQuickEdit: () -> Unit = {},
) {
  val palette = LocalIdePalette.current
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current
  val listState = rememberLazyListState()
  var inspectedEntry by remember { mutableStateOf<TerminalEntry?>(null) }
  var pendingSaveEntry by remember { mutableStateOf<TerminalEntry?>(null) }
  var showMoreCommandsMenu by remember { mutableStateOf(false) }
  var stdinDraft by remember { mutableStateOf("") }
  var classicStreamMode by remember { mutableStateOf(false) }
  var inputFieldValue by remember {
    mutableStateOf(TextFieldValue(uiState.terminalInput, TextRange(uiState.terminalInput.length)))
  }

  LaunchedEffect(uiState.terminalInput) {
    if (uiState.terminalInput != inputFieldValue.text) {
      inputFieldValue = TextFieldValue(uiState.terminalInput, TextRange(uiState.terminalInput.length))
    }
  }

  LaunchedEffect(uiState.terminalEntries.size, uiState.isTerminalBusy, uiState.terminalLiveOutput) {
    val totalItems = uiState.terminalEntries.size + if (uiState.isTerminalBusy) 1 else 0
    if (totalItems > 0) {
      listState.animateScrollToItem(totalItems - 1)
    }
  }

  val copyText: (label: String, content: String) -> Unit = { label, content ->
    clipboard.setText(AnnotatedString(content))
    onNotifyCopied("Copied $label to clipboard")
  }

  uiState.terminalQuickEdit?.let { target ->
    TerminalQuickEditDialog(
      target = target,
      onSave = { updated -> onSaveQuickEdit(target.filePath, updated) },
      onDismiss = onDismissQuickEdit,
    )
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
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(34.dp)
          .background(palette.chromeSurface)
          .padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        uiState.terminalSessions.forEach { tab ->
          val selected = tab.id == uiState.activeTerminalSessionId
          Surface(
            onClick = { onSwitchSession(tab.id) },
            shape = RoundedCornerShape(6.dp),
            color = if (selected) palette.activePillBg else palette.chromePill,
            border =
              BorderStroke(
                1.dp,
                if (selected) palette.activePillFg.copy(alpha = 0.3f) else palette.border.copy(alpha = 0.45f),
              ),
            modifier = Modifier.height(24.dp).testTag("terminal_tab_${tab.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (tab.isBusy) AmberWarn else if (selected) EmeraldLive else palette.textMuted),
              )
              Text(
                text = tab.title,
                fontFamily = JetBrainsMono,
                fontSize = 10.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) palette.activePillFg else palette.textSecondary,
                maxLines = 1,
              )
              if (uiState.terminalSessions.size > 1) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = "Close ${tab.title}",
                  tint = palette.textMuted,
                  modifier =
                    Modifier
                      .size(12.dp)
                      .clickable { onCloseSession(tab.id) },
                )
              }
            }
          }
        }

        Surface(
          onClick = onCreateSession,
          shape = RoundedCornerShape(6.dp),
          color = palette.chromePill,
          border = BorderStroke(1.dp, palette.border.copy(alpha = 0.45f)),
          modifier = Modifier.size(24.dp).testTag("terminal_new_session_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.Add,
              contentDescription = "New shell session",
              tint = palette.textPrimary,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      TerminalQuickChip(
        label = if (classicStreamMode) "Cards" else "Stream",
        onClick = { classicStreamMode = !classicStreamMode },
        modifier = Modifier.testTag("terminal_view_mode_toggle"),
      )
      TerminalQuickChip(label = "sysinfo", onClick = { onRunCommand("sysinfo") })
      TerminalQuickChip(label = "pkg", onClick = { onRunCommand("pkg") })
    }
    HorizontalDivider(color = palette.border.copy(alpha = 0.45f))

    if (classicStreamMode) {
      val streamLines =
        remember(uiState.terminalEntries, uiState.isTerminalBusy, uiState.terminalLiveOutput, uiState.terminalRunningCommand) {
          buildList {
            var lineId = 1L
            uiState.terminalEntries.forEach { entry ->
              add(
                TerminalOutputLine(
                  id = lineId++,
                  text = "${entry.promptPrefix} ${entry.command}",
                  isCommand = true,
                ),
              )
              if (entry.output.isNotEmpty()) {
                entry.output.split('\n').forEach { outLine ->
                  add(
                    TerminalOutputLine(
                      id = lineId++,
                      text = outLine,
                      isError = entry.isError || entry.kind == TerminalEntryKind.ERROR,
                    ),
                  )
                }
              }
            }
            if (uiState.isTerminalBusy) {
              uiState.terminalRunningCommand?.let { running ->
                add(TerminalOutputLine(id = lineId++, text = "$ $running", isCommand = true))
              }
              if (uiState.terminalLiveOutput.isNotEmpty()) {
                uiState.terminalLiveOutput.split('\n').forEach { liveLine ->
                  add(TerminalOutputLine(id = lineId++, text = liveLine))
                }
              }
            }
          }
        }
      TerminalInterface(
        outputLines = streamLines,
        onExecuteCommand = onRunCommand,
        prompt = "${uiState.terminalPromptPath}$",
        isBusy = uiState.isTerminalBusy,
        externalInput = uiState.terminalInput,
        onExternalInputChange = onInputChange,
        onExternalHistoryUp = { onRecallHistory(true) },
        onExternalHistoryDown = { onRecallHistory(false) },
        onAutocomplete = onAutocomplete,
        showHeader = false,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )
    } else {
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (uiState.terminalEntries.isEmpty() && !uiState.isTerminalBusy) {
          item {
            Surface(
              color = palette.chromeSurface,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = "EdgeCode Linux Terminal Ready",
                  style = MaterialTheme.typography.titleSmall,
                  color = palette.activePillFg,
                )
                Text(
                  text = "Run any Linux command (ls, tree, grep, zip, unzip, wget, curl, jq, sha256sum, nano, pkg, sysinfo, wrangler, git).",
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
                  append(stripTerminalAnsi(entry.output))
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

      if (uiState.isTerminalBusy) {
        item(key = "live_running_card") {
          Surface(
            color = palette.chromeSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AmberWarn.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AmberWarn)
                Text(
                  text = uiState.terminalRunningCommand ?: "Running…",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Surface(
                  onClick = onInterruptCommand,
                  shape = RoundedCornerShape(6.dp),
                  color = RoseError.copy(alpha = 0.18f),
                  border = BorderStroke(1.dp, RoseError.copy(alpha = 0.45f)),
                  modifier = Modifier.testTag("terminal_interrupt_button"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Interrupt process", tint = RoseError, modifier = Modifier.size(13.dp))
                    Text("^C Stop", fontFamily = JetBrainsMono, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = RoseError)
                  }
                }
              }

              if (uiState.terminalLiveOutput.isNotBlank()) {
                val styledLive = parseTerminalAnsi(uiState.terminalLiveOutput, palette.textPrimary)
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
                      text = styledLive,
                      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, lineHeight = 16.5.sp),
                    )
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Box(
                  modifier =
                    Modifier
                      .weight(1f)
                      .clip(RoundedCornerShape(7.dp))
                      .background(palette.editorBg)
                      .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                      .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                  if (stdinDraft.isEmpty()) {
                    Text(
                      text = "Send stdin line to running process…",
                      fontFamily = JetBrainsMono,
                      fontSize = 10.5.sp,
                      color = palette.textMuted,
                    )
                  }
                  BasicTextField(
                    value = stdinDraft,
                    onValueChange = { stdinDraft = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.textPrimary),
                    cursorBrush = SolidColor(palette.activePillFg),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions =
                      KeyboardActions(
                        onSend = {
                          onSendStdin(stdinDraft)
                          stdinDraft = ""
                        },
                      ),
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
                Surface(
                  onClick = {
                    onSendStdin(stdinDraft)
                    stdinDraft = ""
                  },
                  shape = RoundedCornerShape(7.dp),
                  color = palette.activePillBg,
                ) {
                  Text(
                    text = "Stdin",
                    fontFamily = JetBrainsMono,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.activePillFg,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.55f))
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(34.dp)
          .background(palette.chromeBg)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      TerminalExtraKeyChip(label = "TAB", onClick = onAutocomplete)
      if (uiState.isTerminalBusy) {
        Surface(
          onClick = onInterruptCommand,
          shape = RoundedCornerShape(5.dp),
          color = RoseError.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, RoseError.copy(alpha = 0.4f)),
          modifier = Modifier.height(24.dp),
        ) {
          Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text("^C", fontFamily = JetBrainsMono, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = RoseError)
          }
        }
      }
      listOf("|", "&&", ";", ">", ">>", "~/", "/", "-", "$", "\"", "'", "↑", "↓").forEach { key ->
        TerminalExtraKeyChip(
          label = key,
          onClick = {
            when (key) {
              "↑" -> onRecallHistory(true)
              "↓" -> onRecallHistory(false)
              else -> onInsertToken(key)
            }
          },
        )
      }
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.4f))
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(38.dp)
          .background(palette.chromeSurface)
          .padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
          shape = RoundedCornerShape(6.dp),
          color = palette.activePillBg,
          border = BorderStroke(1.dp, palette.activePillFg.copy(alpha = 0.25f)),
          modifier = Modifier.height(26.dp).testTag("terminal_paste_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              Icons.Outlined.ContentPaste,
              contentDescription = "Paste clipboard to terminal",
              tint = palette.activePillFg,
              modifier = Modifier.size(12.dp),
            )
            Text(
              "Paste",
              fontFamily = JetBrainsMono,
              fontSize = 10.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.activePillFg,
            )
          }
        }

        val lastOut = uiState.terminalEntries.lastOrNull()?.let { it.httpMeta?.bodyText ?: stripTerminalAnsi(it.output) }
        TerminalDockIconBtn(
          icon = Icons.Outlined.ContentCopy,
          contentDescription = "Copy last output",
          enabled = !lastOut.isNullOrBlank(),
          onClick = { if (!lastOut.isNullOrBlank()) copyText("last output", lastOut) },
        )
        TerminalDockIconBtn(
          icon = Icons.Outlined.KeyboardArrowUp,
          contentDescription = "Previous command",
          onClick = { onRecallHistory(true) },
        )
        TerminalDockIconBtn(
          icon = Icons.Outlined.KeyboardArrowDown,
          contentDescription = "Next command",
          onClick = { onRecallHistory(false) },
        )
        TerminalDockIconBtn(
          icon = Icons.Outlined.DeleteSweep,
          contentDescription = "Clear terminal",
          enabled = uiState.terminalEntries.isNotEmpty(),
          onClick = onClearTerminal,
        )
      }

      VerticalDivider(
        color = palette.border.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
      )

      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TerminalQuickChip(label = "ls -la", onClick = { onRunCommand("ls -la") })
        TerminalQuickChip(label = "tree", onClick = { onRunCommand("tree -L 2") })
        TerminalQuickChip(label = "curl /", onClick = { onInputChange("curl -i /") })
        TerminalQuickChip(label = "wget", onClick = { onInputChange("wget ") })
        TerminalQuickChip(label = "unzip", onClick = { onInputChange("unzip ") })
        TerminalQuickChip(label = "deploy", onClick = { onRunCommand("wrangler deploy") })
      }

      Spacer(Modifier.width(4.dp))

      Box {
        Surface(
          onClick = { showMoreCommandsMenu = true },
          shape = RoundedCornerShape(6.dp),
          color = palette.chromePill,
          border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
          modifier = Modifier.height(26.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = "Cmds",
              fontFamily = JetBrainsMono,
              fontSize = 10.5.sp,
              fontWeight = FontWeight.Medium,
              color = palette.textPrimary,
            )
            Icon(
              Icons.Outlined.KeyboardArrowDown,
              contentDescription = "More commands",
              tint = palette.textSecondary,
              modifier = Modifier.size(13.dp),
            )
          }
        }
        EdgeMenu(
          expanded = showMoreCommandsMenu,
          onDismissRequest = { showMoreCommandsMenu = false },
          minWidth = 248.dp,
          maxWidth = 296.dp,
        ) {
          EdgeMenuHeader(
            title = "Linux & Edge CLI",
            badgeText = "SHELL",
            leadingIcon = Icons.Outlined.Terminal,
          )
          listOf(
            "sysinfo" to "INFO",
            "pkg" to "TOOLS",
            "tree -L 3" to "FILES",
            "projects" to "IDE",
            "uname -a && id" to "SYS",
            "wrangler dev" to "DEV",
            "wrangler deploy" to "DEPLOY",
            "wrangler tail" to "LOGS",
            "wrangler d1 list" to "D1",
            "wrangler r2 bucket list" to "R2",
            "git status" to "GIT",
            "help" to "HELP",
          ).forEach { (cmd, tag) ->
            EdgeMenuItem(
              label = cmd,
              icon = Icons.Outlined.Terminal,
              badgeText = tag,
              monospaced = true,
              onClick = {
                showMoreCommandsMenu = false
                onRunCommand(cmd)
              },
            )
          }
        }
      }
    }

    if (!classicStreamMode) {
      HorizontalDivider(color = palette.border.copy(alpha = 0.4f))

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(palette.chromeBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(
          color = palette.chromeSurface,
          shape = RoundedCornerShape(6.dp),
          border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
        ) {
          Text(
            text = "${uiState.terminalPromptPath}$",
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = palette.activePillFg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
          )
        }

        Box(
          modifier =
            Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .background(palette.editorBg)
              .border(1.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
              .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
          if (uiState.terminalInput.isEmpty()) {
            Text(
              text = "ls -la | zip | curl | wrangler | nano | help…",
              style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
              color = palette.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BasicTextField(
            value = inputFieldValue,
            onValueChange = { next ->
              inputFieldValue = next
              onInputChange(next.text)
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textPrimary),
            cursorBrush = SolidColor(palette.activePillFg),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions =
              KeyboardActions(
                onSend = { onRunCommand(uiState.terminalInput) },
                onDone = { onRunCommand(uiState.terminalInput) },
              ),
            modifier =
              Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { keyEvent ->
                  if (keyEvent.type != KeyEventType.KeyDown) {
                    false
                  } else {
                    when (keyEvent.key) {
                      Key.Enter, Key.NumPadEnter -> {
                        if (!keyEvent.isShiftPressed) {
                          onRunCommand(uiState.terminalInput)
                          true
                        } else {
                          false
                        }
                      }
                      Key.DirectionUp -> {
                        onRecallHistory(true)
                        true
                      }
                      Key.DirectionDown -> {
                        onRecallHistory(false)
                        true
                      }
                      Key.Tab -> {
                        onAutocomplete()
                        true
                      }
                      else -> false
                    }
                  }
                }
                .testTag("terminal_command_input"),
          )
        }

        val canRun = !uiState.isTerminalBusy && uiState.terminalInput.isNotBlank()
        Surface(
          onClick = {
            if (uiState.isTerminalBusy) onInterruptCommand()
            else if (canRun) onRunCommand(uiState.terminalInput)
          },
          enabled = uiState.isTerminalBusy || canRun,
          shape = RoundedCornerShape(8.dp),
          color =
            when {
              uiState.isTerminalBusy -> RoseError.copy(alpha = 0.16f)
              canRun -> palette.activePillBg
              else -> palette.chromeSurface
            },
          border =
            BorderStroke(
              1.dp,
              when {
                uiState.isTerminalBusy -> RoseError.copy(alpha = 0.45f)
                canRun -> palette.activePillFg.copy(alpha = 0.3f)
                else -> palette.border.copy(alpha = 0.45f)
              },
            ),
          modifier = Modifier.size(34.dp).testTag("terminal_run_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (uiState.isTerminalBusy) {
              Icon(
                imageVector = Icons.Outlined.Stop,
                contentDescription = "Stop running command",
                tint = RoseError,
                modifier = Modifier.size(17.dp),
              )
            } else {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Execute terminal command",
                tint = if (canRun) palette.activePillFg else palette.textMuted,
                modifier = Modifier.size(17.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TerminalExtraKeyChip(
  label: String,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(5.dp),
    color = palette.chromePill,
    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.4f)),
    modifier = Modifier.height(24.dp),
  ) {
    Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
      Text(
        text = label,
        fontFamily = JetBrainsMono,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textPrimary,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun TerminalQuickEditDialog(
  target: TerminalQuickEditTarget,
  onSave: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  var text by remember(target.filePath) { mutableStateOf(target.content) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = "nano • ${target.displayName}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
          text = target.filePath,
          fontFamily = JetBrainsMono,
          fontSize = 10.sp,
          color = palette.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    text = {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 340.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.editorBg)
            .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
      ) {
        BasicTextField(
          value = text,
          onValueChange = { text = it },
          textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textPrimary, lineHeight = 17.sp),
          cursorBrush = SolidColor(palette.activePillFg),
          modifier = Modifier.fillMaxWidth().testTag("terminal_quick_edit_input"),
        )
      }
    },
    confirmButton = {
      FilledTonalButton(onClick = { onSave(text) }) {
        Text("Save File")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun TerminalDockIconBtn(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = { if (enabled) onClick() },
    enabled = enabled,
    shape = RoundedCornerShape(6.dp),
    color = palette.chromePill,
    border = BorderStroke(1.dp, palette.border.copy(alpha = if (enabled) 0.5f else 0.2f)),
    modifier = Modifier.size(26.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (enabled) palette.textSecondary else palette.textMuted.copy(alpha = 0.4f),
        modifier = Modifier.size(14.dp),
      )
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
  val defaultColor = if (entry.isError) RoseError else palette.textPrimary
  val annotatedOutput = remember(entry.output, defaultColor) { parseTerminalAnsi(entry.output, defaultColor) }

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(palette.chromeSurface)
        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "${entry.promptPrefix} ${entry.command}",
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
        color = palette.activePillFg,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
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
        Surface(
          color = statusColor.copy(alpha = 0.14f),
          shape = RoundedCornerShape(5.dp),
          border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        ) {
          Text(
            text = "${meta.protocol} ${meta.statusCode} ${meta.statusText}",
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
          )
        }
        Text(
          text = meta.timingSummary,
          style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
          color = palette.textSecondary,
        )
      }
    }

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
            text = annotatedOutput,
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, lineHeight = 16.5.sp),
          )
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TerminalMiniAction(
        label = "Copy Output",
        icon = Icons.Outlined.ContentCopy,
        onClick = { onCopy("output", entry.httpMeta?.bodyText?.ifBlank { stripTerminalAnsi(entry.output) } ?: stripTerminalAnsi(entry.output)) },
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
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(6.dp),
    color = palette.chromeBg,
    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
    modifier = modifier.height(26.dp),
  ) {
    Box(
      modifier = Modifier.padding(horizontal = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
        color = palette.textPrimary,
        maxLines = 1,
        softWrap = false,
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
  val rawText = remember(entry.output) { stripTerminalAnsi(entry.output) }
  val bodyForJson = entry.httpMeta?.bodyText ?: rawText
  val jsonFields = remember(bodyForJson) { JqEvaluator.extractCopyableFields(bodyForJson) }
  val lines = remember(rawText) { rawText.lines().filter { it.isNotEmpty() } }
  var mode by remember { mutableIntStateOf(0) }

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
