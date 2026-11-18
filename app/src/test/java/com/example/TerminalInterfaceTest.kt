package com.example

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.example.ui.components.TerminalCommandHistoryState
import com.example.ui.components.TerminalInterface
import com.example.ui.components.TerminalOutputLine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TerminalInterfaceTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun historyStateTracksCommandsAndPreservesDraftAcrossArrowNavigation() {
    val state = TerminalCommandHistoryState()
    state.record("pwd")
    state.record("ls -la")
    state.record("ls -la")
    state.record("git status")

    assertEquals(listOf("pwd", "ls -la", "git status"), state.commands.toList())
    assertEquals("git status", state.navigateUp("echo dr"))
    assertEquals("ls -la", state.navigateUp("git status"))
    assertEquals("pwd", state.navigateUp("ls -la"))
    assertEquals("pwd", state.navigateUp("pwd"))
    assertEquals("ls -la", state.navigateDown())
    assertEquals("git status", state.navigateDown())
    assertEquals("echo dr", state.navigateDown())
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun terminalInterfaceSubmitsOnEnterAndNavigatesHistoryWithArrowKeys() {
    val executed = mutableListOf<String>()
    val lines = mutableStateListOf(TerminalOutputLine(id = 1L, text = "ready"))
    val historyState = TerminalCommandHistoryState()

    composeRule.setContent {
      TerminalInterface(
        outputLines = lines,
        historyState = historyState,
        onExecuteCommand = { cmd ->
          executed.add(cmd)
          lines.add(TerminalOutputLine(id = lines.size + 1L, text = "$ $cmd", isCommand = true))
          lines.add(TerminalOutputLine(id = lines.size + 2L, text = "out:$cmd"))
        },
      )
    }

    composeRule.onNodeWithText("ready").assertIsDisplayed()

    val inputNode = composeRule.onNodeWithTag("terminal_interface_input")
    inputNode.performClick()
    inputNode.performTextInput("whoami")
    inputNode.performKeyInput { pressKey(Key.Enter) }

    assertEquals(listOf("whoami"), executed)
    assertEquals(listOf("whoami"), historyState.commands.toList())
    composeRule.onNodeWithText("out:whoami").assertIsDisplayed()

    inputNode.performTextInput("uname -a")
    inputNode.performImeAction()

    assertEquals(listOf("whoami", "uname -a"), executed)
    assertEquals(listOf("whoami", "uname -a"), historyState.commands.toList())
    composeRule.onNodeWithText("out:uname -a").assertIsDisplayed()

    inputNode.performKeyInput { pressKey(Key.DirectionUp) }
    inputNode.assertTextContains("uname -a")

    inputNode.performKeyInput { pressKey(Key.DirectionUp) }
    inputNode.assertTextContains("whoami")

    inputNode.performKeyInput { pressKey(Key.DirectionDown) }
    inputNode.assertTextContains("uname -a")

    inputNode.performKeyInput { pressKey(Key.DirectionDown) }
    inputNode.assertTextContains("")
  }
}
