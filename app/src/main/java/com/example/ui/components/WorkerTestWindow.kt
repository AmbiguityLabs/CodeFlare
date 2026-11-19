package com.example.ui.components

import android.view.ViewGroup
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.WorkerTestRunRecord
import com.example.data.remote.WorkerLanguage
import com.example.editor.SyntaxHighlighter
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.IdeUiState
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private data class HeaderEntry(
  val id: String = UUID.randomUUID().toString().take(8),
  val key: String,
  val value: String,
  val enabled: Boolean = true,
)

@Composable
fun WorkerTestWindow(
  uiState: IdeUiState,
  onUrlChange: (String) -> Unit,
  onMethodChange: (String) -> Unit,
  onHeadersJsonChange: (String) -> Unit,
  onBodyChange: (String) -> Unit,
  onSendRequest: () -> Unit,
  onSelectHistoryRun: (WorkerTestRunRecord) -> Unit,
  onClearHistory: () -> Unit,
  onNotifyStatus: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val clipboard = LocalClipboardManager.current

  var methodMenuOpen by remember { mutableStateOf(false) }
  var requestSectionTab by remember { mutableIntStateOf(0) }
  var rawHeadersMode by remember { mutableStateOf(false) }
  var responseOutputTab by remember { mutableIntStateOf(0) }
  var prettyJson by remember { mutableStateOf(true) }
  var showEmptyHelp by remember { mutableStateOf(false) }
  var showMethodNote by remember { mutableStateOf(false) }
  var htmlWebViewRef by remember { mutableStateOf<WebView?>(null) }

  val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
  val activeMethod = uiState.previewHttpMethod.uppercase()
  val methodColor =
    when (activeMethod) {
      "GET", "HEAD" -> EmeraldLive
      "POST" -> AmberWarn
      "DELETE" -> RoseError
      else -> palette.activePillFg
    }

  var headerRows by remember { mutableStateOf(parseHeadersJsonToList(uiState.previewHttpHeadersJson)) }
  var lastSerializedJson by remember { mutableStateOf(serializeHeadersToJson(headerRows)) }

  LaunchedEffect(uiState.previewHttpHeadersJson) {
    if (uiState.previewHttpHeadersJson != lastSerializedJson) {
      val parsed = parseHeadersJsonToList(uiState.previewHttpHeadersJson)
      headerRows = parsed
      lastSerializedJson = uiState.previewHttpHeadersJson
    }
  }

  fun disposeHtmlWebView() {
    val wv = htmlWebViewRef ?: return
    htmlWebViewRef = null
    runCatching {
      wv.stopLoading()
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.destroy()
    }
  }

  DisposableEffect(responseOutputTab == 2) {
    onDispose {
      if (responseOutputTab != 2) {
        disposeHtmlWebView()
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose { disposeHtmlWebView() }
  }

  fun commitHeaderRows(rows: List<HeaderEntry>) {
    headerRows = rows
    val serialized = serializeHeadersToJson(rows)
    lastSerializedJson = serialized
    onHeadersJsonChange(serialized)
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(palette.chromeBg)
        .testTag("worker_test_window"),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.chromeSurface)
          .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box {
        Surface(
          onClick = { methodMenuOpen = true },
          color = palette.chromePill,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.height(40.dp).minimumInteractiveComponentSize().testTag("worker_test_method_selector"),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
              text = "$activeMethod ▾",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = methodColor,
            )
          }
        }
        EdgeMenu(
          expanded = methodMenuOpen,
          onDismissRequest = { methodMenuOpen = false },
          minWidth = 152.dp,
          maxWidth = 180.dp,
        ) {
          EdgeMenuSection("HTTP Method")
          methods.forEach { m ->
            val verbTint = edgeHttpVerbColor(m, palette.textSecondary)
            val isSelected = m == activeMethod
            EdgeMenuCompactItem(
              label = m,
              leadingColorDot = verbTint,
              selected = isSelected,
              monospaced = true,
              accentColor = if (isSelected) verbTint else null,
              onClick = {
                methodMenuOpen = false
                onMethodChange(m)
                if (m in listOf("POST", "PUT", "PATCH") && requestSectionTab == 0) {
                  requestSectionTab = 1
                }
              },
            )
          }
        }
      }

      Box(
        modifier =
          Modifier
            .weight(1f)
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.editorBg)
            .border(1.dp, palette.border.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (uiState.previewUrl.isEmpty()) {
              Text(
                text = "https://your-worker.subdomain.workers.dev/path",
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = uiState.previewUrl,
              onValueChange = onUrlChange,
              singleLine = true,
              textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textPrimary),
              cursorBrush = SolidColor(palette.activePillFg),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
              keyboardActions = KeyboardActions(onSend = { onSendRequest() }),
              modifier = Modifier.fillMaxWidth().testTag("worker_test_url_input"),
            )
          }
          if (uiState.previewUrl.isNotEmpty()) {
            IconButton(
              onClick = { onUrlChange("") },
              modifier = Modifier.size(28.dp),
            ) {
              Icon(Icons.Outlined.Close, contentDescription = "Clear URL", tint = palette.textMuted, modifier = Modifier.size(14.dp))
            }
          }
        }
      }

      Button(
        onClick = { if (!uiState.isRunningPreviewHttp) onSendRequest() },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.height(40.dp).minimumInteractiveComponentSize().testTag("worker_test_send_button"),
      ) {
        if (uiState.isRunningPreviewHttp) {
          CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
          Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send HTTP test", modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(5.dp))
          Text("Send", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
      }
    }

    val baseUrls =
      remember(uiState.workspace) {
        buildList {
          uiState.workspace?.workersDevUrl?.let { add(it) }
          uiState.workspace?.domains.orEmpty().mapNotNull { it.hostname.takeIf(String::isNotBlank) }.forEach {
            add("https://$it")
          }
        }.distinct()
      }
    if (baseUrls.isNotEmpty() || uiState.workerTestHistory.isNotEmpty()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        baseUrls.forEach { base ->
          Surface(
            onClick = { onUrlChange(base) },
            color = palette.chromePill,
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = base.removePrefix("https://"),
              style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
              color = palette.activePillFg,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
          listOf("/health", "/api").forEach { suffix ->
            Surface(
              onClick = { onUrlChange(base.trimEnd('/') + suffix) },
              color = palette.chromeSurface,
              shape = RoundedCornerShape(8.dp),
            ) {
              Text(
                text = suffix,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
              )
            }
          }
        }
        if (uiState.workerTestHistory.isNotEmpty()) {
          Surface(
            onClick = onClearHistory,
            color = palette.chromeSurface,
            shape = RoundedCornerShape(8.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history", tint = palette.textMuted, modifier = Modifier.size(12.dp))
              Text("Clear (${uiState.workerTestHistory.size})", fontSize = 10.sp, color = palette.textMuted)
            }
          }
        }
      }
    }

    HorizontalDivider(color = palette.border.copy(alpha = 0.45f))

    Column(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        color = palette.chromeSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            listOf(
              0 to "Headers (${headerRows.count { it.enabled && it.key.isNotBlank() }})",
              1 to "Body${if (uiState.previewHttpBody.isNotBlank()) " •" else ""}",
            ).forEach { (idx, label) ->
              val active = requestSectionTab == idx
              Surface(
                onClick = { requestSectionTab = idx },
                color = if (active) palette.activePillBg else palette.chromePill,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                  color = if (active) palette.activePillFg else palette.textSecondary,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
              }
            }
            Spacer(Modifier.weight(1f))
            if (requestSectionTab == 0) {
              Surface(
                onClick = { rawHeadersMode = !rawHeadersMode },
                color = palette.chromePill,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = if (rawHeadersMode) "Key-Value" else "Raw JSON",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textSecondary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }
            } else {
              Surface(
                onClick = {
                  val pretty = prettyFormatJsonOrKeep(uiState.previewHttpBody)
                  onBodyChange(pretty)
                },
                color = palette.chromePill,
                shape = RoundedCornerShape(8.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(12.dp))
                  Text("Format JSON", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
              }
            }
          }

          if (requestSectionTab == 0) {
            if (rawHeadersMode) {
              OutlinedTextField(
                value = uiState.previewHttpHeadersJson,
                onValueChange = onHeadersJsonChange,
                label = { Text("Headers JSON") },
                textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp),
                maxLines = 5,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("worker_test_headers_json_input"),
              )
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                headerRows.forEachIndexed { idx, row ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Checkbox(
                      checked = row.enabled,
                      onCheckedChange = { checked ->
                        val next = headerRows.toMutableList()
                        next[idx] = row.copy(enabled = checked)
                        commitHeaderRows(next)
                      },
                    )
                    OutlinedTextField(
                      value = row.key,
                      onValueChange = { k ->
                        val next = headerRows.toMutableList()
                        next[idx] = row.copy(key = k)
                        commitHeaderRows(next)
                      },
                      placeholder = { Text("Header") },
                      singleLine = true,
                      textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                      value = row.value,
                      onValueChange = { v ->
                        val next = headerRows.toMutableList()
                        next[idx] = row.copy(value = v)
                        commitHeaderRows(next)
                      },
                      placeholder = { Text("Value") },
                      singleLine = true,
                      textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.weight(1.2f),
                    )
                    IconButton(
                      onClick = {
                        val next = headerRows.toMutableList().apply { removeAt(idx) }
                        commitHeaderRows(next)
                      },
                      modifier = Modifier.size(32.dp),
                    ) {
                      Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove header", tint = palette.textMuted, modifier = Modifier.size(16.dp))
                    }
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Surface(
                    onClick = { commitHeaderRows(headerRows + HeaderEntry(key = "", value = "", enabled = true)) },
                    color = palette.activePillBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("worker_test_add_header"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(Icons.Outlined.Add, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(14.dp))
                      Text("Add Header", style = MaterialTheme.typography.labelSmall, color = palette.activePillFg, fontWeight = FontWeight.SemiBold)
                    }
                  }
                  listOf(
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer ",
                    "X-Custom-Debug" to "true",
                  ).forEach { (pk, pv) ->
                    Surface(
                      onClick = {
                        val filtered = headerRows.filterNot { it.key.equals(pk, ignoreCase = true) }
                        commitHeaderRows(filtered + HeaderEntry(key = pk, value = pv, enabled = true))
                      },
                      color = palette.chromePill,
                      shape = RoundedCornerShape(8.dp),
                    ) {
                      Text(
                        text = "+ $pk",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                      )
                    }
                  }
                }
              }
            }
          } else {
            OutlinedTextField(
              value = uiState.previewHttpBody,
              onValueChange = onBodyChange,
              placeholder = { Text("{\"hello\": \"cloudflare\"}") },
              textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp),
              minLines = 3,
              maxLines = 7,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().testTag("worker_test_body_input"),
            )
            if (activeMethod in listOf("GET", "HEAD")) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                IconButton(onClick = { showMethodNote = !showMethodNote }, modifier = Modifier.size(22.dp)) {
                  Icon(Icons.Outlined.Info, contentDescription = "Request body info", tint = palette.textMuted, modifier = Modifier.size(14.dp))
                }
                AnimatedVisibility(visible = showMethodNote) {
                  Text(
                    text = "$activeMethod usually omits body.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                  )
                }
              }
            }
          }
        }
      }

      val res = uiState.previewHttpResult
      if (res == null) {
        Surface(
          color = palette.chromeSurface,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            IconButton(onClick = { showEmptyHelp = !showEmptyHelp }, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Outlined.Info, contentDescription = "Test help", tint = palette.activePillFg, modifier = Modifier.size(20.dp))
            }
            AnimatedVisibility(visible = showEmptyHelp) {
              Text(
                text = "Enter Worker URL above and tap Send.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      } else {
        val statusColor =
          when {
            res.errorMessage != null || res.statusCode >= 500 -> RoseError
            res.statusCode in 200..299 -> EmeraldLive
            else -> AmberWarn
          }
        val sizeBytes = res.body.toByteArray(Charsets.UTF_8).size
        val sizeLabel = if (sizeBytes >= 1024) String.format("%.1f KB", sizeBytes / 1024.0) else "$sizeBytes B"

        Surface(
          color = palette.chromeSurface,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Surface(
                color = statusColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("worker_test_response_status"),
              ) {
                Text(
                  text = if (res.errorMessage != null) "ERR" else "${res.statusCode} ${res.statusText}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = statusColor,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
              }

              Surface(
                color = palette.chromePill,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("worker_test_response_timing"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(Icons.Outlined.Schedule, contentDescription = "Response timing", tint = palette.activePillFg, modifier = Modifier.size(12.dp))
                  Text(
                    text = "${res.durationMs} ms • $sizeLabel",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
                    color = palette.textPrimary,
                  )
                }
              }

              Spacer(Modifier.weight(1f))

              Surface(
                onClick = {
                  clipboard.setText(AnnotatedString(res.errorMessage ?: res.body))
                  onNotifyStatus("Copied Worker response")
                },
                color = palette.chromePill,
                shape = RoundedCornerShape(8.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy response body", tint = palette.textSecondary, modifier = Modifier.size(13.dp))
                  Text("Copy", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
                }
              }
            }

            val bodyIsHtml =
              res.headers.entries.any { it.key.equals("content-type", true) && it.value.contains("html", true) } ||
                res.body.trimStart().startsWith("<!doctype html", true) ||
                res.body.trimStart().startsWith("<html", true)
            LaunchedEffect(bodyIsHtml) {
              if (!bodyIsHtml && responseOutputTab == 2) {
                responseOutputTab = 0
              }
            }
            val tabs =
              buildList {
                add(0 to "Body")
                add(1 to "Headers (${res.headers.size})")
                if (bodyIsHtml) add(2 to "HTML Preview")
              }
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              tabs.forEach { (idx, label) ->
                val active = responseOutputTab == idx
                Surface(
                  onClick = { responseOutputTab = idx },
                  color = if (active) palette.activePillBg else palette.chromePill,
                  shape = RoundedCornerShape(8.dp),
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) palette.activePillFg else palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
              Spacer(Modifier.weight(1f))
              if (responseOutputTab == 0 && res.errorMessage == null) {
                Surface(
                  onClick = { prettyJson = !prettyJson },
                  color = palette.chromePill,
                  shape = RoundedCornerShape(8.dp),
                ) {
                  Text(
                    text = if (prettyJson) "Pretty" else "Raw",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.activePillFg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
            }

            Surface(
              color = palette.editorBg,
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.45f)),
              modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp),
            ) {
              when (responseOutputTab) {
                1 -> {
                  SelectionContainer {
                    Column(
                      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp),
                      verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                      if (res.headers.isEmpty()) {
                        Text("(no response headers)", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp), color = palette.textMuted)
                      } else {
                        res.headers.forEach { (k, v) ->
                          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("$k:", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = palette.activePillFg)
                            Text(v, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp), color = palette.textPrimary)
                          }
                        }
                      }
                    }
                  }
                }
                2 -> {
                  val htmlRenderKey = remember(res.url, res.body) { res.url to res.body }
                  AndroidView(
                    factory = { ctx ->
                      WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.WHITE)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        tag = htmlRenderKey
                        loadDataWithBaseURL(res.url.ifBlank { "https://localhost/" }, res.body, "text/html", "utf-8", null)
                        htmlWebViewRef = this
                      }
                    },
                    update = { wv ->
                      if (htmlWebViewRef !== wv) htmlWebViewRef = wv
                      if (wv.tag != htmlRenderKey) {
                        wv.tag = htmlRenderKey
                        wv.loadDataWithBaseURL(res.url.ifBlank { "https://localhost/" }, res.body, "text/html", "utf-8", null)
                      }
                    },
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                  )
                }
                else -> {
                  val displayBody =
                    remember(res.body, prettyJson, res.errorMessage) {
                      if (res.errorMessage != null) res.errorMessage
                      else if (res.body.isBlank()) "(empty response body)"
                      else if (prettyJson) prettyFormatJsonOrKeep(res.body)
                      else res.body
                    }
                  val isJson =
                    res.errorMessage == null &&
                      prettyJson &&
                      (displayBody.trimStart().startsWith("{") || displayBody.trimStart().startsWith("["))
                  SelectionContainer {
                    Box(
                      modifier =
                        Modifier
                          .fillMaxWidth()
                          .verticalScroll(rememberScrollState())
                          .horizontalScroll(rememberScrollState())
                          .padding(10.dp),
                    ) {
                      if (isJson) {
                        val highlighted = remember(displayBody, palette) {
                          SyntaxHighlighter.highlight(displayBody, WorkerLanguage.JSON, palette)
                        }
                        Text(
                          text = highlighted,
                          style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
                        )
                      } else {
                        Text(
                          text = displayBody,
                          style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
                          color = if (res.errorMessage != null) RoseError else palette.textPrimary,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      if (uiState.workerTestHistory.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "HISTORY (${uiState.workerTestHistory.size})",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
          )
          uiState.workerTestHistory.take(8).forEach { run ->
            val runAccent =
              when {
                run.errorMessage != null || run.statusCode >= 500 -> RoseError
                run.statusCode in 200..299 -> EmeraldLive
                else -> AmberWarn
              }
            Surface(
              onClick = { onSelectHistoryRun(run) },
              color = palette.chromeSurface,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = run.method,
                  style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                  color = palette.activePillFg,
                )
                Surface(color = runAccent.copy(alpha = 0.16f), shape = RoundedCornerShape(5.dp)) {
                  Text(
                    text = if (run.errorMessage != null) "ERR" else "${run.statusCode}",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = runAccent,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                  )
                }
                Text(
                  text = run.url,
                  style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = "${run.durationMs}ms",
                  style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
                  color = palette.textMuted,
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun parseHeadersJsonToList(rawJson: String): List<HeaderEntry> {
  return runCatching {
    val obj = JSONObject(rawJson.ifBlank { "{}" })
    val list = mutableListOf<HeaderEntry>()
    obj.keys().forEach { key ->
      list.add(HeaderEntry(key = key, value = obj.optString(key), enabled = true))
    }
    list
  }.getOrDefault(emptyList())
}

private fun serializeHeadersToJson(rows: List<HeaderEntry>): String {
  val obj = JSONObject()
  rows.forEach { r ->
    if (r.enabled && r.key.isNotBlank()) {
      obj.put(r.key.trim(), r.value)
    }
  }
  return obj.toString(2)
}

private fun prettyFormatJsonOrKeep(raw: String): String {
  val trimmed = raw.trim()
  if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return raw
  return runCatching {
    when (val parsed = JSONTokener(trimmed).nextValue()) {
      is JSONObject -> parsed.toString(2)
      is JSONArray -> parsed.toString(2)
      else -> raw
    }
  }.getOrDefault(raw)
}
