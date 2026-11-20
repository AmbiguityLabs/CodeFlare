package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.WorkerLanguage
import com.example.editor.SyntaxHighlighter
import com.example.editor.canvas.EdgeCodeEditorSurface
import com.example.editor.canvas.EdgeEditorController
import com.example.editor.drag.SmartDraggableText
import com.example.editor.selection.SmartTextDomain
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalEditorSettings
import com.example.ui.theme.LocalIdePalette

/**
 * Infers the best [WorkerLanguage] for secondary code, HTTP payload, KV, or SQL editors.
 */
fun detectSharedEditorLanguage(
  text: String,
  contentTypeHint: String? = null,
  fallback: WorkerLanguage = WorkerLanguage.TEXT,
): WorkerLanguage {
  val ct = contentTypeHint?.lowercase().orEmpty()
  when {
    ct.contains("json") -> return WorkerLanguage.JSON
    ct.contains("html") -> return WorkerLanguage.HTML
    ct.contains("css") -> return WorkerLanguage.CSS
    ct.contains("javascript") || ct.contains("ecmascript") -> return WorkerLanguage.JAVASCRIPT
    ct.contains("typescript") -> return WorkerLanguage.TYPESCRIPT
    ct.contains("markdown") -> return WorkerLanguage.MARKDOWN
    ct.contains("toml") || ct.contains("yaml") || ct.contains("sql") -> return WorkerLanguage.CONFIG
  }
  val trimmed = text.trimStart()
  if (trimmed.isEmpty()) return fallback
  return when {
    trimmed.startsWith("{") || trimmed.startsWith("[") -> WorkerLanguage.JSON
    trimmed.startsWith("<!doctype html", ignoreCase = true) ||
      trimmed.startsWith("<html", ignoreCase = true) ||
      trimmed.startsWith("<div", ignoreCase = true) ||
      trimmed.startsWith("<svg", ignoreCase = true) -> WorkerLanguage.HTML
    trimmed.startsWith("SELECT ", ignoreCase = true) ||
      trimmed.startsWith("INSERT ", ignoreCase = true) ||
      trimmed.startsWith("UPDATE ", ignoreCase = true) ||
      trimmed.startsWith("DELETE ", ignoreCase = true) ||
      trimmed.startsWith("CREATE ", ignoreCase = true) ||
      trimmed.startsWith("PRAGMA ", ignoreCase = true) -> WorkerLanguage.CONFIG
    else -> fallback
  }
}

/**
 * Shared embedded editor wrapper that reuses the exact unmodified [EdgeCodeEditorSurface]
 * across secondary text/code/JSON/SQL/response surfaces while respecting Settings.
 *
 * Never mutates the main IDE editor; passes a lightweight embedded settings copy with line
 * numbers / folding / sticky headers disabled by default unless explicitly requested.
 */
@Composable
fun EdgeSharedTextEditor(
  surfaceKey: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  placeholder: String = "",
  language: WorkerLanguage = WorkerLanguage.TEXT,
  readOnly: Boolean = false,
  wordWrap: Boolean = true,
  showLineNumbers: Boolean = false,
  height: Dp? = 148.dp,
  findMatches: List<IntRange> = emptyList(),
  activeFindMatchIndex: Int = 0,
  controller: EdgeEditorController = remember(surfaceKey) { EdgeEditorController() },
  testTagName: String? = null,
  contentTypeHint: String? = null,
  isErrorText: Boolean = false,
  onCopiedFallback: ((String) -> Unit)? = null,
) {
  val palette = LocalIdePalette.current
  val baseSettings = LocalEditorSettings.current
  val latestOnValueChange by rememberUpdatedState(onValueChange)

  val embeddedSettings =
    remember(baseSettings, wordWrap, showLineNumbers) {
      baseSettings.copy(
        fontSizeSp = (baseSettings.fontSizeSp - 1).coerceIn(10, 16),
        wordWrap = wordWrap,
        showLineNumbers = showLineNumbers,
        codeFolding = false,
        stickyHeaders = false,
        gutterDiffMarkers = false,
        indentGuides = false,
      )
    }

  var liveZoomPercent by remember(surfaceKey) { mutableIntStateOf(100) }
  var externalRev by remember(surfaceKey) { mutableLongStateOf(0L) }
  var lastSyncedText by remember(surfaceKey) { mutableStateOf(value) }
  var isEmptyVisual by remember(surfaceKey) { mutableStateOf(value.isEmpty()) }

  // Sync externally-driven text mutations (Format JSON, Clear, HTTP Response, KV Load) without echoing own edits
  LaunchedEffect(value) {
    if (value != lastSyncedText) {
      lastSyncedText = value
      isEmptyVisual = value.isEmpty()
      externalRev += 1L
    }
  }

  Column(modifier = modifier.fillMaxWidth()) {
    if (!label.isNullOrBlank()) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = palette.textMuted,
        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
      )
    }

    if (!baseSettings.useUnifiedEditorEverywhere) {
      if (readOnly) {
        val annotated =
          remember(value, contentTypeHint, palette, isErrorText) {
            if (isErrorText || value.isBlank()) null
            else SyntaxHighlighter.highlightResponse(value, contentTypeHint, palette)
          }
        Surface(
          color = palette.editorBg,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.55f)),
          modifier =
            Modifier
              .fillMaxWidth()
              .let { if (height != null) it.height(height) else it.fillMaxSize() }
              .let { if (testTagName != null) it.testTag(testTagName) else it },
        ) {
          val scrollMod =
            Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .let { if (wordWrap) it else it.horizontalScroll(rememberScrollState()) }
              .padding(10.dp)
          Box(modifier = scrollMod) {
            SmartDraggableText(
              text = value.ifEmpty { placeholder },
              annotatedText = annotated,
              surfaceId = surfaceKey,
              domain = if (language == WorkerLanguage.JSON) SmartTextDomain.JSON else SmartTextDomain.TERMINAL_OR_LOG,
              style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, lineHeight = 16.sp),
              color = if (isErrorText) Color(0xFFF87171) else if (value.isEmpty()) palette.editorTextMuted else palette.editorTextPrimary,
              onCopied = onCopiedFallback,
            )
          }
        }
      } else {
        OutlinedTextField(
          value = value,
          onValueChange = onValueChange,
          placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, fontFamily = JetBrainsMono, fontSize = 12.sp) }) else null,
          textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp),
          minLines = 3,
          maxLines = 8,
          shape = RoundedCornerShape(10.dp),
          modifier =
            Modifier
              .fillMaxWidth()
              .let { if (testTagName != null) it.testTag(testTagName) else it },
        )
      }
      return@Column
    }

    val shape = RoundedCornerShape(10.dp)
    Surface(
      color = palette.editorBg,
      shape = shape,
      border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.60f)),
      modifier =
        Modifier
          .fillMaxWidth()
          .let { if (height != null) it.height(height) else it.fillMaxSize() }
          .clip(shape)
          .let { if (testTagName != null) it.testTag(testTagName) else it },
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(palette.editorBg)
            .padding(horizontal = 4.dp, vertical = 2.dp),
      ) {
        EdgeCodeEditorSurface(
          filePath = "embedded://$surfaceKey",
          externalTfv = TextFieldValue(text = value, selection = TextRange(value.length)),
          editorRevision = externalRev,
          originalContent = value,
          language = language,
          editorSettings = embeddedSettings,
          zoomPercent = liveZoomPercent,
          hasCustomBg = false,
          readOnly = readOnly,
          diagnostics = emptyList(),
          symbols = emptyList(),
          findMatches = findMatches,
          activeFindMatchIndex = activeFindMatchIndex,
          controller = controller,
          onDebouncedSync = { tfv ->
            isEmptyVisual = tfv.text.isEmpty()
            if (!readOnly && tfv.text != lastSyncedText) {
              lastSyncedText = tfv.text
              latestOnValueChange(tfv.text)
            }
          },
          onTelemetryUpdate = {
            val liveChars = controller.currentCharSequence()
            isEmptyVisual = liveChars.isEmpty()
            // Keep interactive secondary fields synchronized immediately so fast Submit/Run clicks never miss recent edits
            if (!readOnly && liveChars.length <= 64_000) {
              val liveText = liveChars.toString()
              if (liveText != lastSyncedText) {
                lastSyncedText = liveText
                latestOnValueChange(liveText)
              }
            }
          },
          onPinchZoomChanged = { z, _ -> liveZoomPercent = z.coerceIn(70, 180) },
          onPinchZoomCommitted = { z -> liveZoomPercent = z.coerceIn(70, 180) },
          onRequestSmartRename = {},
          onToggleFindBar = {},
          modifier = Modifier.fillMaxSize(),
        )

        if (isEmptyVisual && placeholder.isNotEmpty()) {
          Text(
            text = placeholder,
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, lineHeight = 16.sp),
            color = palette.textMuted.copy(alpha = 0.65f),
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp),
          )
        }
      }
    }
  }
}
