package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.RoseError

val TerminalDarkBackground = Color(0xFF0B0E14)
val TerminalDarkSurface = Color(0xFF111621)
val TerminalDarkInputBg = Color(0xFF0E131C)
val TerminalDarkBorder = Color(0xFF232B3A)
val TerminalDarkText = Color(0xFFE6EDF3)
val TerminalDarkMuted = Color(0xFF7D8590)
val TerminalDarkPrompt = Color(0xFF7EE787)
val TerminalDarkCommand = Color(0xFF79C0FF)
val TerminalDarkError = Color(0xFFFF7B72)

data class TerminalOutputLine(
  val id: Long,
  val text: String,
  val isCommand: Boolean = false,
  val isError: Boolean = false,
)

@Stable
class TerminalCommandHistoryState(
  initialCommands: List<String> = emptyList(),
  private val maxEntries: Int = 200,
) {
  val commands = mutableStateListOf<String>().apply { addAll(initialCommands) }
  var cursorIndex by mutableIntStateOf(-1)
    private set
  private var draftInput by mutableStateOf("")

  fun record(command: String) {
    val normalized = command.trim()
    if (normalized.isEmpty()) return
    if (commands.lastOrNull() != normalized) {
      commands.add(normalized)
      while (commands.size > maxEntries) {
        commands.removeAt(0)
      }
    }
    cursorIndex = -1
    draftInput = ""
  }

  fun navigateUp(currentInput: String): String {
    if (commands.isEmpty()) return currentInput
    if (cursorIndex < 0) {
      draftInput = currentInput
      cursorIndex = commands.lastIndex
    } else {
      cursorIndex = (cursorIndex - 1).coerceAtLeast(0)
    }
    return commands[cursorIndex]
  }

  fun navigateDown(): String {
    if (cursorIndex < 0) return draftInput
    return if (cursorIndex >= commands.lastIndex) {
      cursorIndex = -1
      draftInput
    } else {
      cursorIndex += 1
      commands[cursorIndex]
    }
  }

  fun onUserEdited() {
    cursorIndex = -1
    draftInput = ""
  }

  fun clear() {
    commands.clear()
    cursorIndex = -1
    draftInput = ""
  }
}

@Composable
fun rememberTerminalCommandHistoryState(
  initialCommands: List<String> = emptyList(),
): TerminalCommandHistoryState = remember { TerminalCommandHistoryState(initialCommands) }

@Composable
fun TerminalInterface(
  outputLines: List<TerminalOutputLine>,
  onExecuteCommand: (String) -> Unit,
  modifier: Modifier = Modifier,
  prompt: String = "dev@edgecode:~$",
  title: String = "Linux Terminal",
  historyState: TerminalCommandHistoryState = rememberTerminalCommandHistoryState(),
  placeholder: String = "Enter command...",
  isBusy: Boolean = false,
  onClearOutput: (() -> Unit)? = null,
  externalInput: String? = null,
  onExternalInputChange: ((String) -> Unit)? = null,
  onExternalHistoryUp: (() -> Unit)? = null,
  onExternalHistoryDown: (() -> Unit)? = null,
  onAutocomplete: (() -> Unit)? = null,
  showHeader: Boolean = true,
) {
  val listState = rememberLazyListState()
  val isControlled = externalInput != null && onExternalInputChange != null
  var internalFieldValue by remember { mutableStateOf(TextFieldValue("")) }
  var controlledFieldValue by remember {
    mutableStateOf(TextFieldValue(externalInput.orEmpty(), TextRange(externalInput.orEmpty().length)))
  }

  LaunchedEffect(externalInput) {
    if (externalInput != null && externalInput != controlledFieldValue.text) {
      controlledFieldValue = TextFieldValue(externalInput, TextRange(externalInput.length))
    }
  }

  LaunchedEffect(outputLines.size) {
    if (outputLines.isNotEmpty()) {
      listState.animateScrollToItem(outputLines.lastIndex)
    }
  }

  val activeFieldValue = if (isControlled) controlledFieldValue else internalFieldValue
  val currentText = activeFieldValue.text

  val submitCommand = {
    val trimmed = currentText.trim()
    if (trimmed.isNotEmpty() && !isBusy) {
      if (!isControlled) {
        historyState.record(trimmed)
        internalFieldValue = TextFieldValue("")
      }
      onExecuteCommand(trimmed)
    }
  }

  val navigateHistoryUp = {
    if (onExternalHistoryUp != null) {
      onExternalHistoryUp()
    } else if (!isControlled) {
      val recalled = historyState.navigateUp(internalFieldValue.text)
      internalFieldValue = TextFieldValue(recalled, TextRange(recalled.length))
    }
  }

  val navigateHistoryDown = {
    if (onExternalHistoryDown != null) {
      onExternalHistoryDown()
    } else if (!isControlled) {
      val recalled = historyState.navigateDown()
      internalFieldValue = TextFieldValue(recalled, TextRange(recalled.length))
    }
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(TerminalDarkBackground)
        .testTag("terminal_interface_root"),
  ) {
    if (showHeader) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(TerminalDarkSurface)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(TerminalDarkError))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AmberWarn))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(TerminalDarkPrompt))
        Text(
          text = title,
          fontFamily = JetBrainsMono,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = TerminalDarkText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        if (!isControlled) {
          Text(
            text = "hist:${historyState.commands.size}",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = TerminalDarkMuted,
          )
        }
        TerminalHeaderIconButton(
          icon = Icons.Outlined.KeyboardArrowUp,
          contentDescription = "Previous command",
          onClick = navigateHistoryUp,
          tag = "terminal_interface_history_up",
        )
        TerminalHeaderIconButton(
          icon = Icons.Outlined.KeyboardArrowDown,
          contentDescription = "Next command",
          onClick = navigateHistoryDown,
          tag = "terminal_interface_history_down",
        )
        if (onClearOutput != null) {
          TerminalHeaderIconButton(
            icon = Icons.Outlined.DeleteSweep,
            contentDescription = "Clear terminal output",
            onClick = onClearOutput,
            tag = "terminal_interface_clear",
          )
        }
      }
      HorizontalDivider(color = TerminalDarkBorder)
    }

    SelectionContainer(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .background(TerminalDarkBackground),
    ) {
      val keyedLines =
        remember(outputLines.toList()) {
          val seen = HashSet<Long>(outputLines.size)
          outputLines.mapIndexed { idx, line ->
            val uniqueKey = if (seen.add(line.id)) "id_${line.id}" else "id_${line.id}_$idx"
            uniqueKey to line
          }
        }
      LazyColumn(
        state = listState,
        modifier =
          Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("terminal_interface_output_list"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        if (outputLines.isEmpty()) {
          item(key = "empty_prompt") {
            Text(
              text = "~ $",
              fontFamily = JetBrainsMono,
              fontSize = 12.sp,
              lineHeight = 17.sp,
              color = TerminalDarkMuted,
            )
          }
        } else {
          items(keyedLines, key = { it.first }) { (_, line) ->
            val lineColor =
              when {
                line.isError -> TerminalDarkError
                line.isCommand -> TerminalDarkCommand
                else -> TerminalDarkText
              }
            val rendered =
              remember(line.text, lineColor) {
                parseTerminalAnsi(line.text, lineColor)
              }
            Text(
              text = rendered,
              style =
                TextStyle(
                  fontFamily = JetBrainsMono,
                  fontSize = 12.sp,
                  lineHeight = 17.sp,
                  fontWeight = if (line.isCommand) FontWeight.SemiBold else FontWeight.Normal,
                ),
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }

    HorizontalDivider(color = TerminalDarkBorder)

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(TerminalDarkSurface)
          .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = prompt,
        fontFamily = JetBrainsMono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TerminalDarkPrompt,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Box(
        modifier =
          Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(TerminalDarkInputBg)
            .border(1.dp, TerminalDarkBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
      ) {
        if (currentText.isEmpty()) {
          Text(
            text = placeholder,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            color = TerminalDarkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        BasicTextField(
          value = activeFieldValue,
          onValueChange = { updated ->
            if (isControlled) {
              controlledFieldValue = updated
              onExternalInputChange?.invoke(updated.text)
            } else {
              if (updated.text != internalFieldValue.text) {
                historyState.onUserEdited()
              }
              internalFieldValue = updated
            }
          },
          singleLine = true,
          textStyle =
            TextStyle(
              fontFamily = JetBrainsMono,
              fontSize = 12.sp,
              color = TerminalDarkText,
            ),
          cursorBrush = SolidColor(TerminalDarkPrompt),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions =
            KeyboardActions(
              onSend = { submitCommand() },
              onDone = { submitCommand() },
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
                        submitCommand()
                        true
                      } else {
                        false
                      }
                    }
                    Key.DirectionUp -> {
                      navigateHistoryUp()
                      true
                    }
                    Key.DirectionDown -> {
                      navigateHistoryDown()
                      true
                    }
                    Key.Tab -> {
                      if (onAutocomplete != null) {
                        onAutocomplete()
                        true
                      } else {
                        false
                      }
                    }
                    else -> false
                  }
                }
              }
              .testTag("terminal_interface_input"),
        )
      }

      val canSubmit = currentText.isNotBlank() && !isBusy
      Surface(
        onClick = { submitCommand() },
        enabled = canSubmit,
        shape = RoundedCornerShape(6.dp),
        color = if (canSubmit) TerminalDarkPrompt.copy(alpha = 0.16f) else TerminalDarkInputBg,
        border = BorderStroke(1.dp, if (canSubmit) TerminalDarkPrompt.copy(alpha = 0.5f) else TerminalDarkBorder),
        modifier = Modifier.size(32.dp).testTag("terminal_interface_submit"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Send,
            contentDescription = "Execute command",
            tint = if (canSubmit) TerminalDarkPrompt else TerminalDarkMuted,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun TerminalHeaderIconButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  tag: String,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(5.dp),
    color = TerminalDarkInputBg,
    border = BorderStroke(1.dp, TerminalDarkBorder),
    modifier = Modifier.size(24.dp).testTag(tag),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = TerminalDarkText,
        modifier = Modifier.size(14.dp),
      )
    }
  }
}

private val TERMINAL_ANSI_REGEX = Regex("\u001B\\[([0-9;]*)([A-Za-z])")

internal fun stripTerminalAnsi(raw: String): String = TERMINAL_ANSI_REGEX.replace(raw, "")

internal fun parseTerminalAnsi(
  raw: String,
  defaultColor: Color,
): AnnotatedString =
  buildAnnotatedString {
    var currentColor = defaultColor
    var isBold = false
    var cursor = 0

    for (match in TERMINAL_ANSI_REGEX.findAll(raw)) {
      if (match.range.first > cursor) {
        val segment = raw.substring(cursor, match.range.first)
        pushStyle(
          SpanStyle(
            color = currentColor,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
          ),
        )
        append(segment)
        pop()
      }
      if (match.groupValues[2] == "m") {
        val codes =
          match.groupValues[1]
            .split(';')
            .mapNotNull { it.toIntOrNull() }
            .ifEmpty { listOf(0) }
        for (code in codes) {
          when (code) {
            0 -> {
              currentColor = defaultColor
              isBold = false
            }
            1 -> isBold = true
            22 -> isBold = false
            30, 90 -> currentColor = Color(0xFF94A3B8)
            31, 91 -> currentColor = RoseError
            32, 92 -> currentColor = EmeraldLive
            33, 93 -> currentColor = AmberWarn
            34, 94 -> currentColor = Color(0xFF60A5FA)
            35, 95 -> currentColor = Color(0xFFC084FC)
            36, 96 -> currentColor = Color(0xFF38BDF8)
            37, 97 -> currentColor = defaultColor
            39 -> currentColor = defaultColor
          }
        }
      }
      cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
      pushStyle(
        SpanStyle(
          color = currentColor,
          fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        ),
      )
      append(raw.substring(cursor))
      pop()
    }
  }
