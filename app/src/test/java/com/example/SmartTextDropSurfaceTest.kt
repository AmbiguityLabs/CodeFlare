package com.example

import com.example.editor.drag.DragTransferMode
import com.example.editor.drag.EdgeDragPayload
import com.example.editor.drag.EdgeTextDragCoordinator
import com.example.editor.drag.TextDropFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartTextDropSurfaceTest {

  @Test
  fun singleLineDropNormalizesMultilineWhitespaceAndTabs() {
    val result =
      TextDropFormatter.resolveTextDropValue(
        currentValue = "",
        droppedText = "  https://api.example.com/v1/users\n\t ",
        singleLine = true,
      )
    assertEquals("https://api.example.com/v1/users", result)
  }

  @Test
  fun singleLineUrlDropReplacesExistingUrlCleanly() {
    val result =
      TextDropFormatter.resolveTextDropValue(
        currentValue = "https://old-worker.dev/test",
        droppedText = "https://new-worker.dev/v2/health",
        singleLine = true,
        replaceIfFullUrl = true,
      )
    assertEquals("https://new-worker.dev/v2/health", result)
  }

  @Test
  fun singleLinePathDropAvoidsDoubleSlashWhenJoining() {
    val result =
      TextDropFormatter.resolveTextDropValue(
        currentValue = "/api/",
        droppedText = "/users/42",
        singleLine = true,
        replaceIfFullUrl = false,
      )
    assertEquals("/api/users/42", result)
  }

  @Test
  fun singleLineReplaceAllOverwritesSearchOrHeaderKey() {
    val result =
      TextDropFormatter.resolveTextDropValue(
        currentValue = "oldSearchToken",
        droppedText = "newSearchToken\nextra",
        singleLine = true,
        replaceAll = true,
      )
    assertEquals("newSearchToken extra", result)
  }

  @Test
  fun multiLineDropPreservesNewlinesAndSeparatesWithLineBreak() {
    val existing = "{\"id\": 1}"
    val dropped = "{\n  \"status\": \"ok\"\n}"
    val result =
      TextDropFormatter.resolveTextDropValue(
        currentValue = existing,
        droppedText = dropped,
        singleLine = false,
      )
    assertEquals("{\"id\": 1}\n{\n  \"status\": \"ok\"\n}", result)
  }

  @Test
  fun readOnlyCliPayloadAlwaysResolvesToCopyEvenWithMovePreference() {
    val payload =
      EdgeDragPayload(
        dragId = 10L,
        sourceSurfaceId = "cli_log_1",
        sourceEditable = false,
        sourceRange = null,
        text = "https://edge.workers.dev/health",
        preferredMode = DragTransferMode.MOVE,
        scopeLabel = "URL",
        allowCrossPaneMove = true,
      )
    assertEquals(
      DragTransferMode.COPY,
      EdgeTextDragCoordinator.resolveEffectiveMode(payload, targetSurfaceId = "editor_src/index.ts"),
    )
    assertEquals(
      DragTransferMode.COPY,
      EdgeTextDragCoordinator.resolveEffectiveMode(payload, targetSurfaceId = "cli_log_1"),
    )
  }
}
