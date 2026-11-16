package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.runner.LocalRunKind
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.IdeUiState

@Composable
fun LocalRunPreviewPane(
  uiState: IdeUiState,
  onRunActiveFile: () -> Unit,
  onImportAndRunFile: () -> Unit,
  onCreateAndRunFile: () -> Unit,
  onUpdateRequest: (method: String, path: String, headersJson: String, body: String) -> Unit,
  onNotifyCopied: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val clipboard = LocalClipboardManager.current
  var showRequestConfig by remember { mutableStateOf(false) }
  var methodMenuOpen by remember { mutableStateOf(false) }
  var outputSubTab by remember { mutableStateOf(0) } // 0 = Body/Result, 1 = Console, 2 = Headers, 3 = HTML Render

  val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
  val result = uiState.localRunResult

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Top Quick Actions: Import & Run | New File | Method + Path + RUN (no deploy required)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FilledTonalButton(
        onClick = onImportAndRunFile,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.height(36.dp).testTag("import_and_run_button"),
      ) {
        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text("Import & Run", fontSize = 12.sp)
      }

      OutlinedButton(
        onClick = onCreateAndRunFile,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.height(36.dp).testTag("create_quick_script_button"),
      ) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("New & Run", fontSize = 12.sp)
      }

      Box {
        Surface(
          onClick = { methodMenuOpen = true },
          color = palette.chromePill,
          shape = RoundedCornerShape(10.dp),
        ) {
          Text(
            text = "${uiState.runRequestMethod} ▾",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.activePillFg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          )
        }
        DropdownMenu(expanded = methodMenuOpen, onDismissRequest = { methodMenuOpen = false }) {
          methods.forEach { m ->
            DropdownMenuItem(
              text = { Text(m) },
              onClick = {
                methodMenuOpen = false
                onUpdateRequest(m, uiState.runRequestPath, uiState.runRequestHeadersJson, uiState.runRequestBody)
              },
            )
          }
        }
      }

      Surface(
        onClick = { showRequestConfig = !showRequestConfig },
        color = if (showRequestConfig) palette.activePillBg else palette.chromePill,
        shape = RoundedCornerShape(10.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            Icons.Outlined.Tune,
            contentDescription = "Configure Request",
            tint = if (showRequestConfig) palette.activePillFg else palette.textSecondary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = uiState.runRequestPath,
            style = MaterialTheme.typography.labelSmall,
            color = if (showRequestConfig) palette.activePillFg else palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Button(
        onClick = onRunActiveFile,
        enabled = uiState.activeFile?.isVirtualReadOnly == false && !uiState.isRunningLocalScript,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.height(36.dp).testTag("run_script_now_button"),
      ) {
        if (uiState.isRunningLocalScript) {
          CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
          Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Run", fontSize = 12.sp)
        }
      }
    }

    AnimatedVisibility(visible = showRequestConfig) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedTextField(
            value = uiState.runRequestPath,
            onValueChange = { p ->
              onUpdateRequest(uiState.runRequestMethod, p, uiState.runRequestHeadersJson, uiState.runRequestBody)
            },
            label = { Text("Request Path or URL (e.g. /api/hello?q=1)") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = uiState.runRequestHeadersJson,
            onValueChange = { h ->
              onUpdateRequest(uiState.runRequestMethod, uiState.runRequestPath, h, uiState.runRequestBody)
            },
            label = { Text("Headers JSON") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          if (uiState.runRequestMethod in listOf("POST", "PUT", "PATCH")) {
            OutlinedTextField(
              value = uiState.runRequestBody,
              onValueChange = { b ->
                onUpdateRequest(uiState.runRequestMethod, uiState.runRequestPath, uiState.runRequestHeadersJson, b)
              },
              label = { Text("Request Body") },
              maxLines = 3,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }

    if (result == null) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "Instant Local Sandbox Runner",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = palette.textPrimary,
            )
            Text(
              text = "Import any .js / .ts / .html / .sh / .json file or create one cleanly and tap Run — executes locally in V8/shell without saving or deploying to Cloudflare.",
              style = MaterialTheme.typography.bodySmall,
              color = palette.textSecondary,
            )
          }
        }
      }
    } else {
      val badgeColor =
        when {
          result.isError -> RoseError
          result.statusCode in 200..299 || result.kind == LocalRunKind.SHELL_SCRIPT && result.statusCode == 0 -> EmeraldLive
          else -> AmberWarn
        }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(color = badgeColor.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
          Text(
            text = "${result.statusCode} ${result.statusText}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        Text(
          text = "${result.durationMs}ms • ${result.filePath.substringAfterLast('/')}",
          style = MaterialTheme.typography.labelSmall,
          color = palette.textSecondary,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Surface(
          onClick = {
            clipboard.setText(AnnotatedString(result.body))
            onNotifyCopied("Copied execution output")
          },
          color = palette.chromePill,
          shape = RoundedCornerShape(8.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy result", modifier = Modifier.size(13.dp))
            Text("Copy", style = MaterialTheme.typography.labelSmall)
          }
        }
      }

      // Output view mode pills: Output | Console (count) | Headers | Render HTML
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val tabs =
          buildList {
            add(0 to "Output")
            add(1 to "Console (${result.consoleLogs.size})")
            add(2 to "Headers (${result.headers.size})")
            if (result.isHtmlContent) add(3 to "Render")
          }
        tabs.forEach { (idx, label) ->
          Surface(
            onClick = { outputSubTab = idx },
            shape = RoundedCornerShape(8.dp),
            color = if (outputSubTab == idx) palette.activePillBg else palette.chromePill,
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              color = if (outputSubTab == idx) palette.activePillFg else palette.textSecondary,
              fontWeight = if (outputSubTab == idx) FontWeight.SemiBold else FontWeight.Normal,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        }
      }

      HorizontalDivider(color = palette.border.copy(alpha = 0.4f))

      Surface(
        color = palette.editorBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) {
        when (outputSubTab) {
          3 -> {
            if (result.isHtmlContent) {
              LocalHtmlWebViewPreview(html = result.body, modifier = Modifier.fillMaxSize())
            }
          }
          1 -> {
            SelectionContainer {
              Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                if (result.consoleLogs.isEmpty()) {
                  Text("(no console.log output)", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                } else {
                  result.consoleLogs.forEach { line ->
                    Text(
                      text = line,
                      style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono, fontSize = 12.sp),
                      color = palette.textPrimary,
                    )
                  }
                }
              }
            }
          }
          2 -> {
            SelectionContainer {
              Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                if (result.headers.isEmpty()) {
                  Text("(no headers)", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                } else {
                  result.headers.forEach { (k, v) ->
                    Text(
                      text = "$k: $v",
                      style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono, fontSize = 12.sp),
                      color = palette.textPrimary,
                    )
                  }
                }
              }
            }
          }
          else -> {
            SelectionContainer {
              Box(
                modifier =
                  Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
              ) {
                Text(
                  text = result.body.ifBlank { "(empty body)" },
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono, fontSize = 12.sp),
                  color = if (result.isError) RoseError else palette.textPrimary,
                )
              }
            }
          }
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LocalHtmlWebViewPreview(
  html: String,
  modifier: Modifier = Modifier,
) {
  AndroidView(
    factory = { ctx ->
      WebView(ctx).apply {
        settings.javaScriptEnabled = true
        loadDataWithBaseURL("https://local.edgecode.dev/", html, "text/html", "utf-8", null)
      }
    },
    update = { wv ->
      wv.loadDataWithBaseURL("https://local.edgecode.dev/", html, "text/html", "utf-8", null)
    },
    modifier = modifier.heightIn(min = 120.dp),
  )
}
