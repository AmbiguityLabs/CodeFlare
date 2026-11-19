package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.editor.LocalWorkspacePreviewRenderer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.UiStyleVersion
import com.example.ui.viewmodel.IdeUiState

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LocalScriptRunPane(
  uiState: IdeUiState,
  onPathChange: (String) -> Unit,
  onMethodChange: (String) -> Unit,
  onBodyChange: (String) -> Unit,
  onRunLocally: () -> Unit,
  onNotifyStatus: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val clipboard = LocalClipboardManager.current
  var showBodyEditor by remember { mutableStateOf(false) }
  var resultViewTab by remember { mutableIntStateOf(0) } // 0 = Output/Preview, 1 = Console, 2 = Headers
  var methodMenuOpen by remember { mutableStateOf(false) }

  val result = uiState.localScriptResult
  val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH")

  Column(
    modifier = modifier.fillMaxSize().background(palette.chromeBg),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeSurface)
          .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box {
        Surface(
          onClick = { methodMenuOpen = true },
          shape = RoundedCornerShape(if (isV2) 12.dp else 8.dp),
          color = palette.activePillBg,
          modifier = Modifier.height(36.dp).testTag("local_run_method_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Http,
              contentDescription = "Method",
              tint = palette.activePillFg,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = uiState.localRunMethod,
              fontFamily = JetBrainsMono,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = palette.activePillFg,
            )
          }
        }
        EdgeMenu(
          expanded = methodMenuOpen,
          onDismissRequest = { methodMenuOpen = false },
          minWidth = 120.dp,
          maxWidth = 150.dp,
        ) {
          methods.forEach { m ->
            EdgeMenuItem(
              label = m,
              monospaced = true,
              selected = uiState.localRunMethod == m,
              onClick = {
                methodMenuOpen = false
                onMethodChange(m)
              },
            )
          }
        }
      }

      Box(
        modifier =
          Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(if (isV2) 12.dp else 8.dp))
            .background(palette.editorBg)
            .then(
              if (isV2) Modifier else Modifier.border(0.75.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        if (uiState.localRunUrlPath.isEmpty()) {
          Text(
            text = "/",
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            color = palette.textMuted,
          )
        }
        BasicTextField(
          value = uiState.localRunUrlPath,
          onValueChange = onPathChange,
          singleLine = true,
          textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textPrimary),
          cursorBrush = SolidColor(palette.activePillFg),
          modifier = Modifier.fillMaxWidth().testTag("local_run_path_input"),
        )
      }

      IconRevealButton(
        icon = Icons.Outlined.DataObject,
        revealedLabel = "Body",
        active = showBodyEditor,
        onClick = { showBodyEditor = !showBodyEditor },
        tag = "local_run_body_toggle",
        height = 36.dp,
      )

      IconRevealButton(
        icon = Icons.Outlined.PlayArrow,
        revealedLabel = if (uiState.isRunningLocalScript) "Running" else "Run Local",
        onClick = { if (!uiState.isRunningLocalScript) onRunLocally() },
        isBusy = uiState.isRunningLocalScript,
        accentContainer = true,
        tag = "local_run_execute_button",
        height = 36.dp,
      )
    }

    AnimatedVisibility(visible = showBodyEditor) {
      OutlinedTextField(
        value = uiState.localRunBody,
        onValueChange = onBodyChange,
        label = { Text("JSON / Body") },
        textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp),
        minLines = 3,
        maxLines = 6,
        shape = RoundedCornerShape(if (isV2) 16.dp else 10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
      )
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.45f), thickness = 0.75.dp)

    if (result == null) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        IconRevealButton(
          icon = Icons.Outlined.PlayArrow,
          revealedLabel = uiState.activeFile?.fileName ?: uiState.workspace?.entryModulePath ?: "Run",
          onClick = onRunLocally,
          isBusy = uiState.isRunningLocalScript,
          accentContainer = true,
          height = 46.dp,
          tag = "local_run_empty_trigger",
        )
      }
      return
    }

    val statusColor =
      when {
        result.errorMessage != null || result.statusCode >= 500 -> RoseError
        result.statusCode >= 400 -> AmberWarn
        else -> EmeraldLive
      }

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeSurface)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(
        color = statusColor.copy(alpha = 0.16f),
        shape = RoundedCornerShape(999.dp),
      ) {
        Text(
          text = "${result.statusCode} ${result.statusText}",
          fontFamily = JetBrainsMono,
          fontSize = 10.5.sp,
          fontWeight = FontWeight.Bold,
          color = statusColor,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }

      Text(
        text = "${result.durationMs}ms",
        fontFamily = JetBrainsMono,
        fontSize = 10.5.sp,
        color = palette.textMuted,
      )

      Spacer(Modifier.weight(1f))

      IconSegmentTab(
        icon = if (result.isHtmlBody) Icons.Outlined.Visibility else Icons.Outlined.Code,
        label = if (result.isHtmlBody) "Preview" else "Output",
        selected = resultViewTab == 0,
        onClick = { resultViewTab = 0 },
        tag = "local_run_tab_output",
      )
      IconSegmentTab(
        icon = Icons.Outlined.Terminal,
        label = "Logs",
        selected = resultViewTab == 1,
        badgeText = result.logs.size.takeIf { it > 0 }?.toString(),
        onClick = { resultViewTab = 1 },
        tag = "local_run_tab_logs",
      )
      IconSegmentTab(
        icon = Icons.Outlined.DataObject,
        label = "Headers",
        selected = resultViewTab == 2,
        badgeText = result.headers.size.takeIf { it > 0 }?.toString(),
        onClick = { resultViewTab = 2 },
        tag = "local_run_tab_headers",
      )
      IconButton(
        onClick = {
          clipboard.setText(AnnotatedString(result.errorMessage ?: result.body))
          onNotifyStatus("Copied")
        },
        modifier = Modifier.size(30.dp),
      ) {
        Icon(
          Icons.Outlined.ContentCopy,
          contentDescription = "Copy output",
          tint = palette.textSecondary,
          modifier = Modifier.size(16.dp),
        )
      }
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.45f), thickness = 0.75.dp)

    Box(modifier = Modifier.weight(1f).fillMaxWidth().background(palette.editorBg)) {
      when (resultViewTab) {
        0 -> {
          if (result.errorMessage != null) {
            SelectionContainer(
              modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            ) {
              Text(
                text = result.errorMessage,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                color = RoseError,
              )
            }
          } else if (result.isHtmlBody) {
            AndroidView(
              factory = { ctx ->
                WebView(ctx).apply {
                  settings.javaScriptEnabled = true
                  webViewClient = WebViewClient()
                }
              },
              update = { wv ->
                wv.loadDataWithBaseURL(
                  LocalWorkspacePreviewRenderer.LOCAL_PREVIEW_ORIGIN,
                  result.body,
                  "text/html",
                  "UTF-8",
                  null,
                )
              },
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            SelectionContainer(
              modifier =
                Modifier
                  .fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .horizontalScroll(rememberScrollState())
                  .padding(14.dp),
            ) {
              Text(
                text = result.body.ifEmpty { "(empty)" },
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = palette.textPrimary,
              )
            }
          }
        }

        1 -> {
          if (result.logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              IconToggleRevealText(icon = Icons.Outlined.Terminal, text = "0 logs")
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize().padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              items(result.logs) { log ->
                val tone = if (log.level == "ERR") RoseError else palette.activePillFg
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(if (isV2) 12.dp else 8.dp))
                      .background(palette.chromeSurface)
                      .padding(horizontal = 10.dp, vertical = 7.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.Top,
                ) {
                  Text(log.timestamp, fontFamily = JetBrainsMono, fontSize = 10.sp, color = palette.textMuted)
                  Text(log.level, fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tone)
                  Text(log.message, fontFamily = JetBrainsMono, fontSize = 11.5.sp, color = palette.textPrimary, modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }

        else -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            items(result.headers.entries.toList()) { (k, v) ->
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(if (isV2) 12.dp else 8.dp))
                    .background(palette.chromeSurface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(k, fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.activePillFg)
                Text(v, fontFamily = JetBrainsMono, fontSize = 11.sp, color = palette.textSecondary, modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }
    }
  }
}
