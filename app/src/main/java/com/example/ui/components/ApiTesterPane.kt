package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.data.remote.ApiAuthType
import com.example.data.remote.ApiBodyType
import com.example.data.remote.ApiEnvVar
import com.example.data.remote.ApiKeyValueRow
import com.example.data.remote.ApiRequestUtils
import com.example.data.remote.ApiTesterRequest
import com.example.terminal.JqEvaluator
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BadgeTs
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.IdeUiState

private val httpVerbs = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")

@Composable
fun ApiTesterPane(
  uiState: IdeUiState,
  onUpdateRequest: ((ApiTesterRequest) -> ApiTesterRequest) -> Unit,
  onUpdateEnvVars: (List<ApiEnvVar>) -> Unit,
  onRunRequest: () -> Unit,
  onSaveRequest: (String) -> Unit,
  onLoadRequest: (ApiTesterRequest) -> Unit,
  onDeleteCollectionItem: (String) -> Unit,
  onClearHistory: () -> Unit,
  onNotifyStatus: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val clipboard = LocalClipboardManager.current
  val req = uiState.apiRequest
  val workerBaseUrl = uiState.workspace?.workersDevUrl

  var methodMenuOpen by remember { mutableStateOf(false) }
  var requestSubTab by remember { mutableIntStateOf(0) } // 0=Params, 1=Auth, 2=Headers, 3=Body, 4=Env, 5=Saved
  var responseSubTab by remember { mutableIntStateOf(0) } // 0=Pretty, 1=Raw, 2=Headers, 3=Preview
  var showSaveDialog by remember { mutableStateOf(false) }

  if (showSaveDialog) {
    var saveName by remember(req.name) { mutableStateOf(req.name.ifBlank { "${req.method} ${req.url}" }) }
    AlertDialog(
      onDismissRequest = { showSaveDialog = false },
      title = { Text("Save Request to Collection") },
      text = {
        OutlinedTextField(
          value = saveName,
          onValueChange = { saveName = it },
          label = { Text("Request Name") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("api_save_name_input"),
        )
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onSaveRequest(saveName)
            showSaveDialog = false
          },
          enabled = saveName.isNotBlank(),
        ) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } },
    )
  }

  Column(
    modifier = modifier.fillMaxSize().background(palette.editorBg).verticalScroll(rememberScrollState()).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // 1. Postman-style Method + URL + Send Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val verbColor =
        when (req.method.uppercase()) {
          "GET", "HEAD" -> EmeraldLive
          "POST" -> AmberWarn
          "DELETE" -> RoseError
          else -> BadgeTs
        }
      Box {
        Surface(
          onClick = { methodMenuOpen = true },
          shape = RoundedCornerShape(10.dp),
          color = palette.chromePill,
          modifier = Modifier.height(48.dp).testTag("api_method_dropdown"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Text(
              text = req.method.uppercase(),
              style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, fontWeight = FontWeight.Bold),
              color = verbColor,
            )
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Select HTTP method", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
          }
        }
        DropdownMenu(expanded = methodMenuOpen, onDismissRequest = { methodMenuOpen = false }) {
          httpVerbs.forEach { verb ->
            DropdownMenuItem(
              text = { Text(verb, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold) },
              onClick = {
                onUpdateRequest { it.copy(method = verb) }
                methodMenuOpen = false
              },
            )
          }
        }
      }

      Box(
        modifier =
          Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.chromeSurface)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        if (req.url.isEmpty()) {
          Text(
            text = "{{baseUrl}}/api/health or https://...",
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp),
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        BasicTextField(
          value = req.url,
          onValueChange = { nextUrl -> onUpdateRequest { it.copy(url = nextUrl) } },
          singleLine = true,
          textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.5.sp, color = palette.textPrimary),
          cursorBrush = SolidColor(palette.activePillFg),
          modifier = Modifier.fillMaxWidth().testTag("api_url_input"),
        )
      }

      Surface(
        onClick = { if (!uiState.isRunningHttpTest) onRunRequest() },
        shape = RoundedCornerShape(10.dp),
        color = palette.activePillBg,
        modifier = Modifier.height(48.dp).testTag("api_send_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          if (uiState.isRunningHttpTest) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.activePillFg)
          } else {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send HTTP request", tint = palette.activePillFg, modifier = Modifier.size(16.dp))
            Text("Send", style = MaterialTheme.typography.labelLarge, color = palette.activePillFg, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // 2. Quick Actions & Presets
    val activeBase = workerBaseUrl?.trimEnd('/') ?: "{{baseUrl}}"
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!workerBaseUrl.isNullOrBlank()) {
        ApiActionChip("Worker URL", tag = "api_use_worker_url_chip") {
          onUpdateRequest { ApiRequestUtils.applyDeployedWorkerUrl(it, workerBaseUrl, forceReplace = true) }
          onNotifyStatus("API Tester URL set to $workerBaseUrl")
        }
      }
      ApiActionChip("Save", icon = Icons.Outlined.BookmarkAdd, tag = "api_save_button") {
        showSaveDialog = true
      }
      ApiActionChip("Copy cURL", icon = Icons.Outlined.ContentCopy, tag = "api_copy_curl_button") {
        val snippet = ApiRequestUtils.toCurlSnippet(req, uiState.apiEnvVars, workerBaseUrl)
        clipboard.setText(AnnotatedString(snippet))
        onNotifyStatus("Copied cURL snippet to clipboard")
      }
      ApiActionChip("Copy fetch()", icon = Icons.Outlined.Code, tag = "api_copy_fetch_button") {
        val snippet = ApiRequestUtils.toFetchSnippet(req, uiState.apiEnvVars, workerBaseUrl)
        clipboard.setText(AnnotatedString(snippet))
        onNotifyStatus("Copied JS fetch() snippet")
      }
      ApiActionChip("GET /") {
        onUpdateRequest { it.copy(method = "GET", url = "$activeBase/", bodyType = ApiBodyType.NONE) }
      }
      ApiActionChip("POST JSON") {
        onUpdateRequest {
          it.copy(
            method = "POST",
            url = "$activeBase/echo",
            bodyType = ApiBodyType.JSON,
            rawBody = "{\n  \"ok\": true,\n  \"source\": \"edgecode\"\n}",
          )
        }
      }
    }

    // 3. Request Builder Sub-Tabs
    val reqTabs =
      listOf(
        "Params (${req.params.count { it.enabled && it.key.isNotBlank() }})",
        "Auth",
        "Headers (${req.headers.count { it.enabled && it.key.isNotBlank() }})",
        "Body",
        "Env",
        "Saved (${uiState.apiCollections.size + uiState.apiHistory.size})",
      )
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      reqTabs.forEachIndexed { idx, label ->
        val active = requestSubTab == idx
        Surface(
          onClick = { requestSubTab = idx },
          shape = RoundedCornerShape(9.dp),
          color = if (active) palette.activePillBg else palette.chromePill,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("api_req_tab_$idx"),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              color = if (active) palette.activePillFg else palette.textSecondary,
              fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
              maxLines = 1,
            )
          }
        }
      }
    }

    Surface(
      color = palette.chromeSurface,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (requestSubTab) {
          0 ->
            KeyValueRowsEditor(
              rows = req.params,
              keyPlaceholder = "query param",
              valuePlaceholder = "value or {{var}}",
              onRowsChange = { next -> onUpdateRequest { it.copy(params = next) } },
            )
          1 ->
            ApiAuthEditor(
              req = req,
              onUpdateRequest = onUpdateRequest,
            )
          2 ->
            KeyValueRowsEditor(
              rows = req.headers,
              keyPlaceholder = "Header-Name",
              valuePlaceholder = "Header value",
              onRowsChange = { next -> onUpdateRequest { it.copy(headers = next) } },
            )
          3 ->
            ApiBodyEditor(
              req = req,
              onUpdateRequest = onUpdateRequest,
            )
          4 -> {
            Surface(color = palette.editorBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
              Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("{{baseUrl}}", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold), color = palette.activePillFg)
                Spacer(Modifier.width(8.dp))
                Text(
                  text = workerBaseUrl ?: "No deployed workers.dev URL (add override below)",
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.textSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            uiState.apiEnvVars.forEachIndexed { idx, envVar ->
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                  checked = envVar.enabled,
                  onCheckedChange = { checked ->
                    onUpdateEnvVars(uiState.apiEnvVars.mapIndexed { i, v -> if (i == idx) v.copy(enabled = checked) else v })
                  },
                )
                OutlinedTextField(
                  value = envVar.key,
                  onValueChange = { k ->
                    onUpdateEnvVars(uiState.apiEnvVars.mapIndexed { i, v -> if (i == idx) v.copy(key = k) else v })
                  },
                  placeholder = { Text("varName") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                  value = envVar.value,
                  onValueChange = { v ->
                    onUpdateEnvVars(uiState.apiEnvVars.mapIndexed { i, old -> if (i == idx) old.copy(value = v) else old })
                  },
                  placeholder = { Text("value") },
                  singleLine = true,
                  modifier = Modifier.weight(1.2f),
                )
                IconButton(
                  onClick = { onUpdateEnvVars(uiState.apiEnvVars.filterIndexed { i, _ -> i != idx }) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete variable", modifier = Modifier.size(16.dp))
                }
              }
            }
            TextButton(onClick = { onUpdateEnvVars(uiState.apiEnvVars + ApiEnvVar(key = "", value = "")) }) {
              Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Add Variable")
            }
          }
          5 -> {
            Text("SAVED COLLECTION (${uiState.apiCollections.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
            if (uiState.apiCollections.isEmpty()) {
              Text("Tap Save above to store reusable Worker endpoints.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            } else {
              uiState.apiCollections.forEach { saved ->
                Surface(
                  onClick = { onLoadRequest(saved) },
                  shape = RoundedCornerShape(8.dp),
                  color = palette.editorBg,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(saved.method, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold), color = palette.activePillFg)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(saved.name, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text(saved.url, style = MaterialTheme.typography.labelSmall, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onDeleteCollectionItem(saved.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                      Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete saved request", modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }

            HorizontalDivider(color = palette.treeLine)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
              Text("HISTORY (${uiState.apiHistory.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
              if (uiState.apiHistory.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                  Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Clear", fontSize = 11.sp)
                }
              }
            }
            if (uiState.apiHistory.isEmpty()) {
              Text("No API calls yet.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            } else {
              uiState.apiHistory.take(12).forEach { item ->
                val scColor =
                  when (item.statusCode) {
                    in 200..299 -> EmeraldLive
                    in 300..399 -> AmberWarn
                    else -> RoseError
                  }
                Surface(
                  onClick = { onLoadRequest(item.request) },
                  shape = RoundedCornerShape(8.dp),
                  color = palette.editorBg,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${item.statusCode}", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold), color = scColor)
                    Text(item.request.method, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp), color = palette.textPrimary)
                    Text(item.resolvedUrl, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${item.durationMs}ms", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                  }
                }
              }
            }
          }
        }
      }
    }

    // 4. Response Inspector
    uiState.httpTestResult?.let { res ->
      val statusColor =
        when {
          res.errorMessage != null || res.statusCode == 0 -> RoseError
          res.statusCode in 200..299 -> EmeraldLive
          res.statusCode in 300..399 -> AmberWarn
          else -> RoseError
        }
      Surface(
        color = palette.chromeSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("api_response_card"),
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = statusColor.copy(alpha = 0.16f), shape = RoundedCornerShape(999.dp)) {
              Text(
                text = if (res.errorMessage != null && res.statusCode == 0) "ERR" else "${res.statusCode} ${res.statusText}",
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
            Text("${res.durationMs} ms", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Text("${res.body.toByteArray().size} B", style = MaterialTheme.typography.labelSmall, color = palette.textMuted, modifier = Modifier.weight(1f))
            IconButton(
              onClick = {
                clipboard.setText(AnnotatedString(res.body))
                onNotifyStatus("Copied response body")
              },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy response body", tint = palette.activePillFg, modifier = Modifier.size(16.dp))
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Pretty", "Raw", "Headers (${res.headers.size})", "Preview").forEachIndexed { idx, label ->
              val active = responseSubTab == idx
              Surface(
                onClick = { responseSubTab = idx },
                shape = RoundedCornerShape(8.dp),
                color = if (active) palette.activePillBg else palette.chromePill,
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (active) palette.activePillFg else palette.textSecondary,
                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                )
              }
            }
          }

          SelectionContainer {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(palette.editorBg)
                  .padding(10.dp),
            ) {
              when (responseSubTab) {
                0 -> {
                  val pretty = remember(res.body, res.errorMessage) {
                    res.errorMessage ?: JqEvaluator.prettyPrintIfJson(res.body)
                  }
                  Text(pretty.ifBlank { "(empty body)" }, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp), color = palette.textPrimary)
                }
                1 -> {
                  Text((res.errorMessage ?: res.body).ifBlank { "(empty body)" }, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp), color = palette.textPrimary)
                }
                2 -> {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (res.headers.isEmpty()) {
                      Text("(no response headers)", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                    } else {
                      res.headers.forEach { (k, v) ->
                        Text("$k: $v", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp), color = palette.textPrimary)
                      }
                    }
                  }
                }
                3 -> {
                  val previewText =
                    remember(res.body) {
                      if (res.body.contains("<html", ignoreCase = true) || res.body.contains("<body", ignoreCase = true)) {
                        res.body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                      } else {
                        JqEvaluator.prettyPrintIfJson(res.body)
                      }
                    }
                  Text(previewText.ifBlank { "(no preview content)" }, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ApiAuthEditor(
  req: ApiTesterRequest,
  onUpdateRequest: ((ApiTesterRequest) -> ApiTesterRequest) -> Unit,
) {
  val palette = LocalIdePalette.current
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
    ApiAuthType.entries.forEach { type ->
      val active = req.authType == type
      Surface(
        onClick = { onUpdateRequest { it.copy(authType = type) } },
        shape = RoundedCornerShape(8.dp),
        color = if (active) palette.activePillBg else palette.chromePill,
      ) {
        Text(
          text = type.label,
          style = MaterialTheme.typography.labelSmall,
          color = if (active) palette.activePillFg else palette.textSecondary,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    }
  }
  when (req.authType) {
    ApiAuthType.NONE -> Text("No authentication header attached.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
    ApiAuthType.BEARER ->
      OutlinedTextField(
        value = req.bearerToken,
        onValueChange = { tok -> onUpdateRequest { it.copy(bearerToken = tok) } },
        label = { Text("Bearer Token (supports {{apiToken}})") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    ApiAuthType.BASIC ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = req.basicUser,
          onValueChange = { u -> onUpdateRequest { it.copy(basicUser = u) } },
          label = { Text("Username") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = req.basicPass,
          onValueChange = { p -> onUpdateRequest { it.copy(basicPass = p) } },
          label = { Text("Password") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }
    ApiAuthType.API_KEY ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = req.apiKeyName,
          onValueChange = { k -> onUpdateRequest { it.copy(apiKeyName = k) } },
          label = { Text("Header Name") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = req.apiKeyValue,
          onValueChange = { v -> onUpdateRequest { it.copy(apiKeyValue = v) } },
          label = { Text("Key Value") },
          singleLine = true,
          modifier = Modifier.weight(1.2f),
        )
      }
  }
}

@Composable
private fun ApiBodyEditor(
  req: ApiTesterRequest,
  onUpdateRequest: ((ApiTesterRequest) -> ApiTesterRequest) -> Unit,
) {
  val palette = LocalIdePalette.current
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
    ApiBodyType.entries.forEach { type ->
      val active = req.bodyType == type
      Surface(
        onClick = { onUpdateRequest { it.copy(bodyType = type) } },
        shape = RoundedCornerShape(8.dp),
        color = if (active) palette.activePillBg else palette.chromePill,
      ) {
        Text(
          text = type.label,
          style = MaterialTheme.typography.labelSmall,
          color = if (active) palette.activePillFg else palette.textSecondary,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    }
  }
  when (req.bodyType) {
    ApiBodyType.NONE -> Text("No request body sent.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
    ApiBodyType.FORM_URLENCODED ->
      KeyValueRowsEditor(
        rows = req.formRows,
        keyPlaceholder = "field",
        valuePlaceholder = "value",
        onRowsChange = { next -> onUpdateRequest { it.copy(formRows = next) } },
      )
    ApiBodyType.JSON, ApiBodyType.RAW -> {
      OutlinedTextField(
        value = req.rawBody,
        onValueChange = { body -> onUpdateRequest { it.copy(rawBody = body) } },
        label = { Text(if (req.bodyType == ApiBodyType.JSON) "JSON Payload" else "Raw Text Body") },
        maxLines = 8,
        textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp),
        modifier = Modifier.fillMaxWidth().testTag("api_body_input"),
      )
      if (req.bodyType == ApiBodyType.JSON) {
        TextButton(onClick = { onUpdateRequest { it.copy(rawBody = JqEvaluator.prettyPrintIfJson(it.rawBody)) } }) {
          Text("Format JSON")
        }
      }
    }
  }
}

@Composable
private fun KeyValueRowsEditor(
  rows: List<ApiKeyValueRow>,
  keyPlaceholder: String,
  valuePlaceholder: String,
  onRowsChange: (List<ApiKeyValueRow>) -> Unit,
) {
  rows.forEachIndexed { idx, row ->
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      Checkbox(
        checked = row.enabled,
        onCheckedChange = { c -> onRowsChange(rows.mapIndexed { i, r -> if (i == idx) r.copy(enabled = c) else r }) },
      )
      OutlinedTextField(
        value = row.key,
        onValueChange = { k -> onRowsChange(rows.mapIndexed { i, r -> if (i == idx) r.copy(key = k) else r }) },
        placeholder = { Text(keyPlaceholder) },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = row.value,
        onValueChange = { v -> onRowsChange(rows.mapIndexed { i, r -> if (i == idx) r.copy(value = v) else r }) },
        placeholder = { Text(valuePlaceholder) },
        singleLine = true,
        modifier = Modifier.weight(1.2f),
      )
      IconButton(
        onClick = { onRowsChange(rows.filterIndexed { i, _ -> i != idx }) },
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove row", modifier = Modifier.size(16.dp))
      }
    }
  }
  TextButton(onClick = { onRowsChange(rows + ApiKeyValueRow()) }) {
    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text("Add Row")
  }
}

@Composable
private fun ApiActionChip(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  tag: String? = null,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = palette.chromePill,
    modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(13.dp))
      }
      Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textPrimary, fontWeight = FontWeight.Medium)
    }
  }
}
