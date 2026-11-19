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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.example.editor.drag.SmartDraggableText
import com.example.editor.drag.edgeTextDropTarget
import com.example.editor.selection.SmartTextDomain
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
  var wrapResponse by remember { mutableStateOf(true) }
  var showEmptyHelp by remember { mutableStateOf(false) }
  var showMethodNote by remember { mutableStateOf(false) }
  var requestCardCollapsed by remember { mutableStateOf(false) }
  var responseExpanded by remember { mutableStateOf(false) }
  var showResponseSearch by remember { mutableStateOf(false) }
  var responseSearchQuery by remember { mutableStateOf("") }
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
            .edgeTextDropTarget(
              targetSurfaceId = "Worker URL",
              currentValue = uiState.previewUrl,
              onValueChange = onUrlChange,
              singleLine = true,
              shape = RoundedCornerShape(10.dp),
              onDropFeedback = onNotifyStatus,
            )
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
          EdgeLoadingIndicator(size = 16.dp, color = MaterialTheme.colorScheme.onPrimary)
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
            Row(
              modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              listOf(
                0 to "Headers (${headerRows.count { it.enabled && it.key.isNotBlank() }})",
                1 to "Body${if (uiState.previewHttpBody.isNotBlank()) " •" else ""}",
              ).forEach { (idx, label) ->
                val active = requestSectionTab == idx
                Surface(
                  onClick = {
                    requestSectionTab = idx
                    if (requestCardCollapsed) requestCardCollapsed = false
                  },
                  color = if (active) palette.activePillBg else palette.chromePill,
                  shape = RoundedCornerShape(8.dp),
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) palette.activePillFg else palette.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
            }
            if (!requestCardCollapsed) {
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
                    maxLines = 1,
                    softWrap = false,
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
                    Text("Format JSON", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, maxLines = 1, softWrap = false)
                  }
                }
              }
            }
            IconButton(
              onClick = { requestCardCollapsed = !requestCardCollapsed },
              modifier = Modifier.size(28.dp).testTag("worker_test_collapse_request"),
            ) {
              Icon(
                imageVector = if (requestCardCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = if (requestCardCollapsed) "Expand request section" else "Collapse request section",
                tint = palette.textSecondary,
                modifier = Modifier.size(17.dp),
              )
            }
          }

          AnimatedVisibility(visible = !requestCardCollapsed) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (requestSectionTab == 0) {
            if (rawHeadersMode) {
              EdgeSharedTextEditor(
                surfaceKey = "worker_test_headers_json",
                value = uiState.previewHttpHeadersJson,
                onValueChange = onHeadersJsonChange,
                label = "Headers JSON",
                placeholder = "{\"Content-Type\": \"application/json\"}",
                language = WorkerLanguage.JSON,
                height = 132.dp,
                testTagName = "worker_test_headers_json_input",
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
                      modifier =
                        Modifier
                          .weight(1f)
                          .edgeTextDropTarget(
                            targetSurfaceId = "Header Key",
                            currentValue = row.key,
                            onValueChange = { k ->
                              val next = headerRows.toMutableList()
                              next[idx] = row.copy(key = k)
                              commitHeaderRows(next)
                            },
                            singleLine = true,
                            replaceAll = true,
                            shape = RoundedCornerShape(8.dp),
                            onDropFeedback = onNotifyStatus,
                          ),
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
                      modifier =
                        Modifier
                          .weight(1.2f)
                          .edgeTextDropTarget(
                            targetSurfaceId = "Header Value",
                            currentValue = row.value,
                            onValueChange = { v ->
                              val next = headerRows.toMutableList()
                              next[idx] = row.copy(value = v)
                              commitHeaderRows(next)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            onDropFeedback = onNotifyStatus,
                          ),
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
            EdgeSharedTextEditor(
              surfaceKey = "worker_test_request_body",
              value = uiState.previewHttpBody,
              onValueChange = onBodyChange,
              placeholder = "{\"hello\": \"cloudflare\"}",
              language = detectSharedEditorLanguage(uiState.previewHttpBody, fallback = WorkerLanguage.JSON),
              height = 148.dp,
              testTagName = "worker_test_body_input",
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
                  maxLines = 1,
                  softWrap = false,
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
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }

              Spacer(Modifier.weight(1f))

              IconButton(
                onClick = { showResponseSearch = !showResponseSearch },
                modifier = Modifier.size(28.dp).testTag("worker_test_res_search_toggle"),
              ) {
                Icon(
                  Icons.Outlined.Search,
                  contentDescription = "Search response",
                  tint = if (showResponseSearch) palette.activePillFg else palette.textSecondary,
                  modifier = Modifier.size(15.dp),
                )
              }

              IconButton(
                onClick = { responseExpanded = !responseExpanded },
                modifier = Modifier.size(28.dp).testTag("worker_test_res_expand_toggle"),
              ) {
                Icon(
                  imageVector = if (responseExpanded) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                  contentDescription = if (responseExpanded) "Restore response height" else "Expand response",
                  tint = if (responseExpanded) palette.activePillFg else palette.textSecondary,
                  modifier = Modifier.size(15.dp),
                )
              }

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
                  Text("Copy", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary, maxLines = 1, softWrap = false)
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
            val contentTypeHint =
              remember(res.headers) {
                res.headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value
              }
            val formatBadge =
              remember(res.body, contentTypeHint, res.errorMessage) {
                detectWorkerResponseBadge(res.body, contentTypeHint, res.errorMessage)
              }
            val displayBody =
              remember(res.body, prettyJson, res.errorMessage) {
                if (res.errorMessage != null) res.errorMessage
                else if (res.body.isBlank()) "(empty response body)"
                else if (prettyJson) prettyFormatJsonOrKeep(res.body)
                else res.body
              }
            val activeSearchMatches =
              remember(showResponseSearch, responseSearchQuery, displayBody) {
                if (!showResponseSearch || responseSearchQuery.isBlank()) emptyList()
                else findTextOccurrences(displayBody, responseSearchQuery)
              }

            AnimatedVisibility(visible = showResponseSearch) {
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.editorBg)
                    .border(0.5.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(14.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                  if (responseSearchQuery.isEmpty()) {
                    Text(
                      text = "Find in response…",
                      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
                      color = palette.textMuted,
                      maxLines = 1,
                    )
                  }
                  BasicTextField(
                    value = responseSearchQuery,
                    onValueChange = { responseSearchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, color = palette.textPrimary),
                    cursorBrush = SolidColor(palette.activePillFg),
                    modifier = Modifier.fillMaxWidth().testTag("worker_test_res_search_input"),
                  )
                }
                if (responseSearchQuery.isNotEmpty()) {
                  Text(
                    text = "${activeSearchMatches.size} hits",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
                    color = if (activeSearchMatches.isNotEmpty()) palette.activePillFg else palette.textMuted,
                    maxLines = 1,
                    softWrap = false,
                  )
                  IconButton(onClick = { responseSearchQuery = "" }, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = palette.textMuted, modifier = Modifier.size(13.dp))
                  }
                }
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
              Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                tabs.forEach { (idx, label) ->
                  val active = responseOutputTab == idx
                  Surface(
                    onClick = { responseOutputTab = idx },
                    color = if (active) palette.activePillBg else palette.chromePill,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, if (active) palette.activePillFg.copy(alpha = 0.4f) else palette.border.copy(alpha = 0.35f)),
                  ) {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.labelSmall,
                      color = if (active) palette.activePillFg else palette.textSecondary,
                      maxLines = 1,
                      softWrap = false,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                  }
                }
              }
              if (responseOutputTab == 0) {
                Surface(
                  color = palette.chromePill,
                  shape = RoundedCornerShape(6.dp),
                  border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.35f)),
                ) {
                  Text(
                    text = formatBadge,
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = palette.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                  )
                }
                Surface(
                  onClick = { wrapResponse = !wrapResponse },
                  color = if (wrapResponse) palette.activePillBg else palette.chromePill,
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.35f)),
                ) {
                  Text(
                    text = if (wrapResponse) "Wrap" else "NoWrap",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (wrapResponse) palette.activePillFg else palette.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
              if (responseOutputTab == 0 && res.errorMessage == null) {
                Surface(
                  onClick = { prettyJson = !prettyJson },
                  color = palette.chromePill,
                  shape = RoundedCornerShape(8.dp),
                  border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.35f)),
                ) {
                  Text(
                    text = if (prettyJson) "Pretty" else "Raw",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.activePillFg,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
            }

            Surface(
              color = palette.editorBg,
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.55f)),
              modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = if (responseExpanded) 520.dp else 320.dp),
            ) {
              when (responseOutputTab) {
                1 -> {
                  val displayedHeaders =
                    remember(res.headers, showResponseSearch, responseSearchQuery) {
                      if (!showResponseSearch || responseSearchQuery.isBlank()) {
                        res.headers.entries.toList()
                      } else {
                        res.headers.entries.filter { (k, v) ->
                          k.contains(responseSearchQuery, ignoreCase = true) ||
                            v.contains(responseSearchQuery, ignoreCase = true)
                        }
                      }
                    }
                  Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    if (displayedHeaders.isEmpty()) {
                      Text("(no matching response headers)", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp), color = palette.textMuted)
                    } else {
                      displayedHeaders.forEach { (k, v) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                          Text("$k:", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = palette.activePillFg)
                          SmartDraggableText(
                            text = v,
                            surfaceId = "worker_res_header_$k",
                            domain = SmartTextDomain.TERMINAL_OR_LOG,
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
                            color = palette.textPrimary,
                            onCopied = { onNotifyStatus("Copied header value") },
                          )
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
                    modifier = Modifier.fillMaxWidth().height(if (responseExpanded) 420.dp else 250.dp),
                  )
                }
                else -> {
                  val detectedLang =
                    remember(displayBody, contentTypeHint, res.errorMessage) {
                      if (res.errorMessage != null) WorkerLanguage.TEXT
                      else detectSharedEditorLanguage(displayBody, contentTypeHint)
                    }
                  EdgeSharedTextEditor(
                    surfaceKey = "worker_res_body_${res.url.hashCode()}_${prettyJson}",
                    value = displayBody,
                    onValueChange = {},
                    language = detectedLang,
                    readOnly = true,
                    wordWrap = wrapResponse,
                    showLineNumbers = false,
                    height = if (responseExpanded) 460.dp else 280.dp,
                    findMatches = activeSearchMatches,
                    contentTypeHint = contentTypeHint,
                    isErrorText = res.errorMessage != null,
                    onCopiedFallback = { onNotifyStatus("Copied response snippet") },
                  )
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
      Spacer(Modifier.height(72.dp))
    }
  }
}

internal fun findTextOccurrences(text: String, query: String): List<IntRange> {
  if (query.isEmpty() || text.isEmpty()) return emptyList()
  val hits = mutableListOf<IntRange>()
  var start = 0
  while (start < text.length && hits.size < 250) {
    val idx = text.indexOf(query, startIndex = start, ignoreCase = true)
    if (idx < 0) break
    hits.add(idx until (idx + query.length))
    start = idx + query.length.coerceAtLeast(1)
  }
  return hits
}

internal fun applySearchMatchHighlight(
  base: AnnotatedString?,
  plainText: String,
  matches: List<IntRange>,
  matchBg: Color,
  matchFg: Color,
): AnnotatedString? {
  if (matches.isEmpty()) return base
  val source = base ?: AnnotatedString(plainText)
  return buildAnnotatedString {
    append(source)
    for (range in matches) {
      val s = range.first.coerceIn(0, length)
      val e = (range.last + 1).coerceIn(s, length)
      if (e > s) {
        addStyle(
          SpanStyle(background = matchBg, color = matchFg, fontWeight = FontWeight.Bold),
          s,
          e,
        )
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

private fun detectWorkerResponseBadge(
  body: String,
  contentTypeHint: String?,
  errorMessage: String?,
): String {
  if (errorMessage != null) return "ERR"
  val hint = contentTypeHint.orEmpty().lowercase()
  val trimmed = body.trimStart()
  return when {
    hint.contains("json") || trimmed.startsWith("{") || trimmed.startsWith("[") -> "JSON"
    hint.contains("html") || trimmed.startsWith("<!doctype", true) || trimmed.startsWith("<html", true) -> "HTML"
    hint.contains("svg") || trimmed.startsWith("<svg", true) -> "SVG"
    hint.contains("xml") || trimmed.startsWith("<?xml", true) -> "XML"
    hint.contains("css") -> "CSS"
    hint.contains("javascript") || hint.contains("typescript") -> "JS"
    hint.contains("markdown") -> "MD"
    else -> "TXT"
  }
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
