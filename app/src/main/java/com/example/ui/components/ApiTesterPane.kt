package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.example.editor.drag.SmartDraggableText
import com.example.editor.drag.edgeTextDropTarget
import com.example.editor.selection.SmartTextDomain
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.remote.ApiAssertionOperator
import com.example.data.remote.ApiAssertionRule
import com.example.data.remote.ApiAssertionTarget
import com.example.data.remote.ApiAuthType
import com.example.data.remote.ApiBodyType
import com.example.data.remote.ApiCollectionImporter
import com.example.data.remote.ApiEnvVar
import com.example.data.remote.ApiImportResult
import com.example.data.remote.ApiKeyValueRow
import com.example.data.remote.ApiRequestUtils
import com.example.data.remote.ApiTesterRequest
import com.example.data.remote.WorkerLanguage
import com.example.editor.SyntaxHighlighter
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BadgeTs
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.viewmodel.IdeUiState

private val httpVerbs = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

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
  onSelectEnvProfile: (String) -> Unit = {},
  onCreateEnvProfile: (String) -> Unit = {},
  onDeleteEnvProfile: (String) -> Unit = {},
  onImportCollection: (rawJson: String, importEnvVars: Boolean, loadFirst: Boolean) -> Result<ApiImportResult> = { rawJson, _, _ ->
    ApiCollectionImporter.parseImportJson(rawJson)
  },
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current
  val req = uiState.apiRequest
  val workerBaseUrl = uiState.workspace?.workersDevUrl
  val activeProfile =
    uiState.apiEnvProfiles.firstOrNull { it.id == uiState.activeApiEnvProfileId }
      ?: uiState.apiEnvProfiles.firstOrNull()

  var methodMenuOpen by remember { mutableStateOf(false) }
  // 0=Params, 1=Auth, 2=Headers, 3=Body, 4=Assertions, 5=Env, 6=Saved
  var requestSubTab by remember { mutableIntStateOf(0) }
  // 0=Pretty, 1=Raw, 2=Headers, 3=Preview, 4=Assertions
  var responseSubTab by remember { mutableIntStateOf(0) }
  var wrapResponse by remember { mutableStateOf(true) }
  var requestCardCollapsed by remember { mutableStateOf(false) }
  var responseExpanded by remember { mutableStateOf(false) }
  var showResponseSearch by remember { mutableStateOf(false) }
  var responseSearchQuery by remember { mutableStateOf("") }
  var showSaveDialog by remember { mutableStateOf(false) }
  var showNewEnvDialog by remember { mutableStateOf(false) }
  var showImportDialog by remember { mutableStateOf(false) }
  var importJsonDraft by remember { mutableStateOf("") }
  val mainScrollState = rememberScrollState()

  // Reset response sub-tab if assertions became empty
  LaunchedEffect(uiState.apiAssertionResults.size) {
    if (responseSubTab == 4 && uiState.apiAssertionResults.isEmpty()) {
      responseSubTab = 0
    }
  }

  // Smoothly scroll response card into view whenever a new result arrives
  LaunchedEffect(uiState.httpTestResult, uiState.isRunningHttpTest) {
    if (!uiState.isRunningHttpTest && uiState.httpTestResult != null) {
      mainScrollState.animateScrollTo(mainScrollState.maxValue)
    }
  }

  val importFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        }.onSuccess { text ->
          if (text.isNotBlank()) {
            importJsonDraft = text
            showImportDialog = true
          } else {
            onNotifyStatus("Selected file was empty")
          }
        }.onFailure { err ->
          onNotifyStatus("Failed reading file: ${err.localizedMessage}")
        }
      }
    }

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

  if (showNewEnvDialog) {
    var envName by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showNewEnvDialog = false },
      title = { Text("New Environment Profile") },
      text = {
        OutlinedTextField(
          value = envName,
          onValueChange = { envName = it },
          label = { Text("Profile Name") },
          placeholder = { Text("e.g. QA, Preview, Edge-EU") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("api_new_env_name_input"),
        )
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onCreateEnvProfile(envName)
            showNewEnvDialog = false
          },
          enabled = envName.isNotBlank(),
        ) { Text("Create") }
      },
      dismissButton = { TextButton(onClick = { showNewEnvDialog = false }) { Text("Cancel") } },
    )
  }

  if (showImportDialog) {
    var mergeEnvVars by remember { mutableStateOf(true) }
    var loadFirstReq by remember { mutableStateOf(true) }
    val previewParse = remember(importJsonDraft) {
      if (importJsonDraft.isBlank()) null else ApiCollectionImporter.parseImportJson(importJsonDraft)
    }

    AlertDialog(
      onDismissRequest = { showImportDialog = false },
      title = { Text("Import Postman / OpenAPI") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Paste Postman Collection v2.0/v2.1 or OpenAPI 3.x / Swagger 2.0 JSON, or pick a .json file.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
              onClick = { importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
              shape = RoundedCornerShape(9.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(Modifier.width(4.dp))
              Text("Pick File", fontSize = 11.sp)
            }
            OutlinedButton(
              onClick = { importJsonDraft = clipboard.getText()?.text.orEmpty() },
              shape = RoundedCornerShape(9.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(Modifier.width(4.dp))
              Text("Paste", fontSize = 11.sp)
            }
          }
          OutlinedTextField(
            value = importJsonDraft,
            onValueChange = { importJsonDraft = it },
            label = { Text("Collection / Schema JSON") },
            minLines = 4,
            maxLines = 7,
            textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().testTag("api_import_json_input"),
          )

          previewParse?.fold(
            onSuccess = { parsed ->
              Surface(color = EmeraldLive.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = "${parsed.sourceFormat.label}: ${parsed.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldLive,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "${parsed.requests.size} endpoint(s) • ${parsed.extractedEnvVars.size} env variable(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textPrimary,
                  )
                }
              }
            },
            onFailure = { err ->
              Surface(color = RoseError.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                  text = err.localizedMessage ?: "Invalid specification",
                  style = MaterialTheme.typography.labelSmall,
                  color = RoseError,
                  modifier = Modifier.padding(8.dp),
                )
              }
            },
          )

          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = mergeEnvVars, onCheckedChange = { mergeEnvVars = it })
            Text("Merge extracted environment vars", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = loadFirstReq, onCheckedChange = { loadFirstReq = it })
            Text("Load first endpoint immediately", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
          }
        }
      },
      confirmButton = {
        FilledTonalButton(
          onClick = {
            onImportCollection(importJsonDraft, mergeEnvVars, loadFirstReq)
              .onSuccess {
                showImportDialog = false
                importJsonDraft = ""
                requestSubTab = 6 // Show Saved Collection if not loaded or allow browsing all imported endpoints
              }
          },
          enabled = previewParse?.isSuccess == true,
          modifier = Modifier.testTag("api_import_confirm_button"),
        ) {
          Text("Import (${previewParse?.getOrNull()?.requests?.size ?: 0})")
        }
      },
      dismissButton = {
        TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
      },
    )
  }

  Column(
    modifier = modifier.fillMaxSize().background(palette.editorBg).verticalScroll(mainScrollState).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // 0. Environment Switcher Bar (Dev / Staging / Prod / Custom) + Import CTA
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "ENV:",
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        color = palette.textMuted,
      )
      uiState.apiEnvProfiles.forEach { profile ->
        val selected = profile.id == uiState.activeApiEnvProfileId
        Surface(
          onClick = { onSelectEnvProfile(profile.id) },
          shape = RoundedCornerShape(999.dp),
          color = if (selected) palette.activePillBg else palette.chromePill,
          border =
            if (selected) BorderStroke(1.dp, palette.activePillFg.copy(alpha = 0.4f))
            else null,
          modifier = Modifier.testTag("api_env_pill_${profile.name.lowercase()}"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = profile.name,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) palette.activePillFg else palette.textSecondary,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
          }
        }
      }
      Surface(
        onClick = { showNewEnvDialog = true },
        shape = RoundedCornerShape(999.dp),
        color = palette.chromeSurface,
        border = BorderStroke(1.dp, palette.border),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Icon(Icons.Outlined.Add, contentDescription = "Add environment", tint = palette.textSecondary, modifier = Modifier.size(13.dp))
          Text("Env", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
      }
      Spacer(Modifier.width(4.dp))
      ApiActionChip("Import Spec", icon = Icons.Outlined.UploadFile, tag = "api_import_spec_button") {
        showImportDialog = true
      }
    }

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
        EdgeMenu(
          expanded = methodMenuOpen,
          onDismissRequest = { methodMenuOpen = false },
          minWidth = 152.dp,
          maxWidth = 180.dp,
        ) {
          EdgeMenuSection("HTTP Method")
          httpVerbs.forEach { verb ->
            val tint = edgeHttpVerbColor(verb, palette.textSecondary)
            val isSelected = verb.equals(req.method, ignoreCase = true)
            EdgeMenuCompactItem(
              label = verb,
              leadingColorDot = tint,
              selected = isSelected,
              monospaced = true,
              accentColor = if (isSelected) tint else null,
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
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.chromeSurface)
            .border(0.75.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .edgeTextDropTarget(
              targetSurfaceId = "API URL",
              currentValue = req.url,
              onValueChange = { nextUrl -> onUpdateRequest { it.copy(url = nextUrl) } },
              singleLine = true,
              shape = RoundedCornerShape(10.dp),
              onDropFeedback = onNotifyStatus,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
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
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = { if (!uiState.isRunningHttpTest) onRunRequest() }),
          modifier = Modifier.fillMaxWidth().testTag("api_url_input"),
        )
      }

      Surface(
        onClick = { if (!uiState.isRunningHttpTest) onRunRequest() },
        shape = RoundedCornerShape(10.dp),
        color = palette.activePillBg,
        border = BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.35f)),
        modifier = Modifier.height(44.dp).testTag("api_send_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          if (uiState.isRunningHttpTest) {
            EdgeLoadingIndicator(size = 16.dp, color = palette.activePillFg)
          } else {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send HTTP request", tint = palette.activePillFg, modifier = Modifier.size(15.dp))
            Text("Send", style = MaterialTheme.typography.labelLarge, color = palette.activePillFg, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // 1b. Thin Resolved Endpoint Preview Bar
    val resolvedPreview =
      remember(req.url, req.params, uiState.apiEnvVars, workerBaseUrl) {
        ApiRequestUtils.resolveUrl(req, uiState.apiEnvVars, workerBaseUrl)
      }
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(palette.chromeSurface.copy(alpha = 0.7f))
          .border(0.75.dp, palette.border.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
          .padding(horizontal = 9.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val ok = resolvedPreview.isSuccess
      Text(
        text = if (ok) "TARGET" else "CHECK",
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, fontWeight = FontWeight.Bold),
        color = if (ok) palette.activePillFg else AmberWarn,
      )
      Text(
        text = resolvedPreview.fold(
          onSuccess = { it },
          onFailure = { it.localizedMessage ?: "Enter a valid URL" },
        ),
        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.5.sp),
        color = if (ok) palette.textSecondary else AmberWarn,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
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
            rawBody = "{\n  \"ok\": true,\n  \"source\": \"codeflare\"\n}",
          )
        }
      }
    }

    // 3. Request Builder Sub-Tabs
    val enabledAssertCount = req.assertions.count { it.enabled }
    val reqTabs =
      listOf(
        "Params (${req.params.count { it.enabled && it.key.isNotBlank() }})",
        "Auth",
        "Headers (${req.headers.count { it.enabled && it.key.isNotBlank() }})",
        "Body",
        "Assert ($enabledAssertCount)",
        "Env (${activeProfile?.name ?: "Dev"})",
        "Saved (${uiState.apiCollections.size + uiState.apiHistory.size})",
      )
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        reqTabs.forEachIndexed { idx, label ->
          val active = requestSubTab == idx
          Surface(
            onClick = {
              requestSubTab = idx
              if (requestCardCollapsed) requestCardCollapsed = false
            },
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
                softWrap = false,
              )
            }
          }
        }
      }
      IconButton(
        onClick = { requestCardCollapsed = !requestCardCollapsed },
        modifier = Modifier.size(30.dp).testTag("api_collapse_request"),
      ) {
        Icon(
          imageVector = if (requestCardCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
          contentDescription = if (requestCardCollapsed) "Expand request builder" else "Collapse request builder",
          tint = palette.textSecondary,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    AnimatedVisibility(visible = !requestCardCollapsed) {
    Surface(
      color = palette.chromeSurface,
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
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
          4 ->
            ApiAssertionsEditor(
              rules = req.assertions,
              onRulesChange = { next -> onUpdateRequest { it.copy(assertions = next) } },
            )
          5 -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "PROFILE: ${activeProfile?.name ?: "Dev"}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.activePillFg,
                fontWeight = FontWeight.Bold,
              )
              if (activeProfile != null && uiState.apiEnvProfiles.size > 1 && activeProfile.id !in setOf("env_dev", "env_staging", "env_prod")) {
                TextButton(onClick = { onDeleteEnvProfile(activeProfile.id) }) {
                  Text("Delete Profile", fontSize = 11.sp, color = RoseError)
                }
              }
            }
            Surface(color = palette.editorBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
              Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("{{baseUrl}}", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold), color = palette.activePillFg)
                Spacer(Modifier.width(8.dp))
                Text(
                  text =
                    uiState.apiEnvVars.firstOrNull { it.enabled && it.key.equals("baseUrl", true) && it.value.isNotBlank() }?.value
                      ?: workerBaseUrl
                      ?: "Set baseUrl override below",
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
          6 -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("SAVED COLLECTION (${uiState.apiCollections.size})", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
              TextButton(onClick = { showImportDialog = true }) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import", fontSize = 11.sp)
              }
            }
            if (uiState.apiCollections.isEmpty()) {
              Text("Tap Save or Import Spec to build reusable endpoint collections.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
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
                      Text(
                        text = buildString {
                          append(saved.url)
                          if (saved.assertions.isNotEmpty()) append(" • ${saved.assertions.size} assertion(s)")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
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
    }

    // 4. Response Inspector + Automated Assertion Summary
    uiState.httpTestResult?.let { res ->
      val statusColor =
        when {
          res.errorMessage != null || res.statusCode == 0 -> RoseError
          res.statusCode in 200..299 -> EmeraldLive
          res.statusCode in 300..399 -> AmberWarn
          else -> RoseError
        }
      val assertions = uiState.apiAssertionResults
      val passedAssertions = assertions.count { it.passed }
      val allAssertionsPass = assertions.isNotEmpty() && passedAssertions == assertions.size

      val contentTypeHint =
        remember(res.headers) {
          res.headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value
        }
      val formatBadge =
        remember(res.body, contentTypeHint, res.errorMessage) {
          detectApiResponseBadge(res.body, contentTypeHint, res.errorMessage)
        }

      Surface(
        color = palette.chromeSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().testTag("api_response_card"),
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(color = statusColor.copy(alpha = 0.16f), shape = RoundedCornerShape(999.dp)) {
              Text(
                text = if (res.errorMessage != null && res.statusCode == 0) "ERR" else "${res.statusCode} ${res.statusText}",
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
            Text("${res.durationMs} ms", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, maxLines = 1, softWrap = false)
            Text("${res.body.toByteArray().size} B", style = MaterialTheme.typography.labelSmall, color = palette.textMuted, maxLines = 1, softWrap = false)
            Surface(
              color = palette.chromePill,
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.4f)),
            ) {
              Text(
                text = formatBadge,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                color = palette.textSecondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
            Spacer(Modifier.weight(1f))
            if (assertions.isNotEmpty()) {
              val assertColor = if (allAssertionsPass) EmeraldLive else RoseError
              Surface(
                onClick = { responseSubTab = 4 },
                color = assertColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("api_assertion_summary_badge"),
              ) {
                Text(
                  text = "$passedAssertions/${assertions.size} ${if (allAssertionsPass) "PASS" else "FAIL"}",
                  style = MaterialTheme.typography.labelSmall,
                  color = assertColor,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  softWrap = false,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
            IconButton(
              onClick = { showResponseSearch = !showResponseSearch },
              modifier = Modifier.size(30.dp).testTag("api_res_search_toggle"),
            ) {
              Icon(
                Icons.Outlined.Search,
                contentDescription = "Search response",
                tint = if (showResponseSearch) palette.activePillFg else palette.textSecondary,
                modifier = Modifier.size(16.dp),
              )
            }
            IconButton(
              onClick = { responseExpanded = !responseExpanded },
              modifier = Modifier.size(30.dp).testTag("api_res_expand_toggle"),
            ) {
              Icon(
                imageVector = if (responseExpanded) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                contentDescription = if (responseExpanded) "Restore response height" else "Expand response",
                tint = if (responseExpanded) palette.activePillFg else palette.textSecondary,
                modifier = Modifier.size(15.dp),
              )
            }
            IconButton(
              onClick = {
                clipboard.setText(AnnotatedString(res.body))
                onNotifyStatus("Copied response body")
              },
              modifier = Modifier.size(30.dp),
            ) {
              Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy response body", tint = palette.activePillFg, modifier = Modifier.size(16.dp))
            }
          }

          val prettyOrRawTextForSearch =
            remember(responseSubTab, res.body, res.errorMessage) {
              when (responseSubTab) {
                0, 3 -> (res.errorMessage ?: ApiRequestUtils.prettyPrintIfJson(res.body)).ifBlank { "(empty body)" }
                1 -> (res.errorMessage ?: res.body).ifBlank { "(empty body)" }
                else -> ""
              }
            }
          val activeSearchMatches =
            remember(showResponseSearch, responseSearchQuery, prettyOrRawTextForSearch) {
              if (!showResponseSearch || responseSearchQuery.isBlank() || prettyOrRawTextForSearch.isEmpty()) emptyList()
              else findTextOccurrences(prettyOrRawTextForSearch, responseSearchQuery)
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
                  modifier = Modifier.fillMaxWidth().testTag("api_res_search_input"),
                )
              }
              if (responseSearchQuery.isNotEmpty()) {
                if (responseSubTab != 2 && responseSubTab != 4) {
                  Text(
                    text = "${activeSearchMatches.size} hits",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
                    color = if (activeSearchMatches.isNotEmpty()) palette.activePillFg else palette.textMuted,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
                IconButton(onClick = { responseSearchQuery = "" }, modifier = Modifier.size(22.dp)) {
                  Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = palette.textMuted, modifier = Modifier.size(13.dp))
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              val respTabs = buildList {
                add("Pretty")
                add("Raw")
                add("Headers (${res.headers.size})")
                add("Preview")
                if (assertions.isNotEmpty()) add("Assertions ($passedAssertions/${assertions.size})")
              }
              respTabs.forEachIndexed { idx, label ->
                val active = responseSubTab == idx
                Surface(
                  onClick = { responseSubTab = idx },
                  shape = RoundedCornerShape(8.dp),
                  color = if (active) palette.activePillBg else palette.chromePill,
                  border = BorderStroke(0.5.dp, if (active) palette.activePillFg.copy(alpha = 0.4f) else palette.border.copy(alpha = 0.35f)),
                ) {
                  Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) palette.activePillFg else palette.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                  )
                }
              }
            }
            if (responseSubTab == 0 || responseSubTab == 1) {
              Surface(
                onClick = { wrapResponse = !wrapResponse },
                shape = RoundedCornerShape(8.dp),
                color = if (wrapResponse) palette.activePillBg else palette.chromePill,
                border = BorderStroke(0.5.dp, palette.border.copy(alpha = 0.35f)),
              ) {
                Text(
                  text = if (wrapResponse) "Wrap" else "NoWrap",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (wrapResponse) palette.activePillFg else palette.textSecondary,
                  maxLines = 1,
                  softWrap = false,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
              }
            }
          }

          if (responseSubTab == 4 && assertions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              assertions.forEach { ar ->
                val badgeColor = if (ar.passed) EmeraldLive else RoseError
                Surface(
                  color = badgeColor.copy(alpha = 0.12f),
                  shape = RoundedCornerShape(9.dp),
                  border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.4f)),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = if (ar.passed) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                      contentDescription = null,
                      tint = badgeColor,
                      modifier = Modifier.size(16.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = ar.summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                      )
                      Text(
                        text = if (ar.passed) "PASSED" else "FAILED • Actual: ${ar.actualValue}",
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
                        color = badgeColor,
                      )
                    }
                  }
                }
              }
            }
          } else if (responseSubTab == 3 && res.errorMessage == null && isLikelyHtmlResponse(res.headers, res.body)) {
            SandboxedHtmlPreviewBox(
              baseUrl = res.url,
              html = res.body,
              modifier = Modifier.fillMaxWidth().height(if (responseExpanded) 440.dp else 260.dp).clip(RoundedCornerShape(8.dp)),
            )
          } else if (responseSubTab == 2) {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .heightIn(min = 120.dp, max = if (responseExpanded) 540.dp else 360.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(palette.editorBg)
                  .border(0.5.dp, palette.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                  .verticalScroll(rememberScrollState())
                  .padding(10.dp),
            ) {
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
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (displayedHeaders.isEmpty()) {
                  Text("(no matching response headers)", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                } else {
                  displayedHeaders.forEach { (k, v) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Text("$k:", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = palette.activePillFg)
                      SmartDraggableText(
                        text = v,
                        surfaceId = "api_res_header_$k",
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
          } else {
            val resViewerHeight = if (responseExpanded) 480.dp else 300.dp
            val (viewerKey, viewerText, viewerLang) =
              when (responseSubTab) {
                0 -> {
                  val pretty =
                    remember(res.body, res.errorMessage) {
                      res.errorMessage ?: ApiRequestUtils.prettyPrintIfJson(res.body)
                    }
                  val effectiveText = pretty.ifBlank { "(empty body)" }
                  Triple(
                    "api_res_pretty_${res.url.hashCode()}",
                    effectiveText,
                    if (res.errorMessage != null) WorkerLanguage.TEXT else detectSharedEditorLanguage(effectiveText, contentTypeHint),
                  )
                }
                1 -> {
                  val rawText = (res.errorMessage ?: res.body).ifBlank { "(empty body)" }
                  Triple(
                    "api_res_raw_${res.url.hashCode()}",
                    rawText,
                    if (res.errorMessage != null) WorkerLanguage.TEXT else detectSharedEditorLanguage(rawText, contentTypeHint),
                  )
                }
                else -> {
                  val previewText =
                    remember(res.body, res.errorMessage) {
                      res.errorMessage ?: ApiRequestUtils.prettyPrintIfJson(res.body)
                    }
                  val effectivePreview = previewText.ifBlank { "(no preview content)" }
                  Triple(
                    "api_res_preview_${res.url.hashCode()}",
                    effectivePreview,
                    if (res.errorMessage != null) WorkerLanguage.TEXT else detectSharedEditorLanguage(effectivePreview, contentTypeHint),
                  )
                }
              }
            EdgeSharedTextEditor(
              surfaceKey = viewerKey,
              value = viewerText,
              onValueChange = {},
              language = viewerLang,
              readOnly = true,
              wordWrap = wrapResponse,
              height = resViewerHeight,
              findMatches = activeSearchMatches,
              contentTypeHint = contentTypeHint,
              isErrorText = res.errorMessage != null,
              onCopiedFallback = { onNotifyStatus("Copied response snippet") },
            )
          }
        }
      }
    }
    Spacer(Modifier.height(72.dp))
  }
}

@Composable
private fun ApiAssertionsEditor(
  rules: List<ApiAssertionRule>,
  onRulesChange: (List<ApiAssertionRule>) -> Unit,
) {
  val palette = LocalIdePalette.current
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    ApiActionChip("+ Status 2xx") {
      onRulesChange(rules + ApiAssertionRule(target = ApiAssertionTarget.STATUS_CODE, operator = ApiAssertionOperator.IS_2XX))
    }
    ApiActionChip("+ Latency < 500ms") {
      onRulesChange(rules + ApiAssertionRule(target = ApiAssertionTarget.RESPONSE_TIME_MS, operator = ApiAssertionOperator.LESS_THAN, expected = "500"))
    }
    ApiActionChip("+ JSON ok == true") {
      onRulesChange(rules + ApiAssertionRule(target = ApiAssertionTarget.JSON_PATH, property = "ok", operator = ApiAssertionOperator.EQUALS, expected = "true"))
    }
    ApiActionChip("+ Header Content-Type") {
      onRulesChange(rules + ApiAssertionRule(target = ApiAssertionTarget.HEADER, property = "Content-Type", operator = ApiAssertionOperator.CONTAINS, expected = "application/json"))
    }
  }

  if (rules.isEmpty()) {
    Text(
      text = "Add automated assertions to validate status codes, response time, headers, or JSON fields on every Send.",
      style = MaterialTheme.typography.bodySmall,
      color = palette.textSecondary,
    )
  }

  rules.forEachIndexed { idx, rule ->
    var targetMenuOpen by remember(rule.id) { mutableStateOf(false) }
    var opMenuOpen by remember(rule.id) { mutableStateOf(false) }

    Surface(
      color = palette.editorBg,
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Checkbox(
            checked = rule.enabled,
            onCheckedChange = { c -> onRulesChange(rules.mapIndexed { i, r -> if (i == idx) r.copy(enabled = c) else r }) },
          )
          Box {
            Surface(onClick = { targetMenuOpen = true }, color = palette.chromePill, shape = RoundedCornerShape(8.dp)) {
              Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rule.target.label, style = MaterialTheme.typography.labelSmall, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = palette.textSecondary)
              }
            }
            EdgeMenu(
              expanded = targetMenuOpen,
              onDismissRequest = { targetMenuOpen = false },
              minWidth = 186.dp,
              maxWidth = 232.dp,
            ) {
              EdgeMenuSection("Assertion Target")
              ApiAssertionTarget.entries.forEach { t ->
                EdgeMenuCompactItem(
                  label = t.label,
                  selected = t == rule.target,
                  onClick = {
                    val defaultOp =
                      when (t) {
                        ApiAssertionTarget.STATUS_CODE -> ApiAssertionOperator.IS_2XX
                        ApiAssertionTarget.RESPONSE_TIME_MS -> ApiAssertionOperator.LESS_THAN
                        ApiAssertionTarget.JSON_PATH -> ApiAssertionOperator.EQUALS
                        ApiAssertionTarget.BODY_TEXT -> ApiAssertionOperator.CONTAINS
                        ApiAssertionTarget.HEADER -> ApiAssertionOperator.CONTAINS
                      }
                    onRulesChange(rules.mapIndexed { i, r -> if (i == idx) r.copy(target = t, operator = defaultOp) else r })
                    targetMenuOpen = false
                  },
                )
              }
            }
          }

          Box {
            Surface(onClick = { opMenuOpen = true }, color = palette.chromePill, shape = RoundedCornerShape(8.dp)) {
              Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rule.operator.label, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp), color = palette.activePillFg, fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = palette.textSecondary)
              }
            }
            EdgeMenu(
              expanded = opMenuOpen,
              onDismissRequest = { opMenuOpen = false },
              minWidth = 178.dp,
              maxWidth = 228.dp,
            ) {
              EdgeMenuSection("Operator")
              ApiAssertionOperator.entries.forEach { op ->
                EdgeMenuCompactItem(
                  label = op.label,
                  selected = op == rule.operator,
                  monospaced = true,
                  onClick = {
                    onRulesChange(rules.mapIndexed { i, r -> if (i == idx) r.copy(operator = op) else r })
                    opMenuOpen = false
                  },
                )
              }
            }
          }

          Spacer(Modifier.weight(1f))
          IconButton(
            onClick = { onRulesChange(rules.filterIndexed { i, _ -> i != idx }) },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete assertion", modifier = Modifier.size(16.dp), tint = RoseError)
          }
        }

        val needsProperty = rule.target == ApiAssertionTarget.JSON_PATH || rule.target == ApiAssertionTarget.HEADER
        val needsExpected = rule.operator != ApiAssertionOperator.IS_2XX && rule.operator != ApiAssertionOperator.EXISTS
        if (needsProperty || needsExpected) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (needsProperty) {
              OutlinedTextField(
                value = rule.property,
                onValueChange = { prop -> onRulesChange(rules.mapIndexed { i, r -> if (i == idx) r.copy(property = prop) else r }) },
                label = { Text(if (rule.target == ApiAssertionTarget.JSON_PATH) "JSON Path (e.g. data.id)" else "Header Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
            if (needsExpected) {
              OutlinedTextField(
                value = rule.expected,
                onValueChange = { exp -> onRulesChange(rules.mapIndexed { i, r -> if (i == idx) r.copy(expected = exp) else r }) },
                label = { Text("Expected Value") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }
  }

  TextButton(onClick = { onRulesChange(rules + ApiAssertionRule()) }) {
    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text("Add Assertion Rule")
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
        modifier =
          Modifier
            .fillMaxWidth()
            .edgeTextDropTarget(
              targetSurfaceId = "Bearer Token",
              currentValue = req.bearerToken,
              onValueChange = { tok -> onUpdateRequest { it.copy(bearerToken = tok) } },
              singleLine = true,
              replaceAll = true,
            ),
      )
    ApiAuthType.BASIC ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = req.basicUser,
          onValueChange = { u -> onUpdateRequest { it.copy(basicUser = u) } },
          label = { Text("Username") },
          singleLine = true,
          modifier =
            Modifier
              .weight(1f)
              .edgeTextDropTarget(
                targetSurfaceId = "Basic User",
                currentValue = req.basicUser,
                onValueChange = { u -> onUpdateRequest { it.copy(basicUser = u) } },
                singleLine = true,
                replaceAll = true,
              ),
        )
        OutlinedTextField(
          value = req.basicPass,
          onValueChange = { p -> onUpdateRequest { it.copy(basicPass = p) } },
          label = { Text("Password") },
          singleLine = true,
          modifier =
            Modifier
              .weight(1f)
              .edgeTextDropTarget(
                targetSurfaceId = "Basic Pass",
                currentValue = req.basicPass,
                onValueChange = { p -> onUpdateRequest { it.copy(basicPass = p) } },
                singleLine = true,
                replaceAll = true,
              ),
        )
      }
    ApiAuthType.API_KEY ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = req.apiKeyName,
          onValueChange = { k -> onUpdateRequest { it.copy(apiKeyName = k) } },
          label = { Text("Header Name") },
          singleLine = true,
          modifier =
            Modifier
              .weight(1f)
              .edgeTextDropTarget(
                targetSurfaceId = "API Key Header",
                currentValue = req.apiKeyName,
                onValueChange = { k -> onUpdateRequest { it.copy(apiKeyName = k) } },
                singleLine = true,
                replaceAll = true,
              ),
        )
        OutlinedTextField(
          value = req.apiKeyValue,
          onValueChange = { v -> onUpdateRequest { it.copy(apiKeyValue = v) } },
          label = { Text("Key Value") },
          singleLine = true,
          modifier =
            Modifier
              .weight(1.2f)
              .edgeTextDropTarget(
                targetSurfaceId = "API Key Value",
                currentValue = req.apiKeyValue,
                onValueChange = { v -> onUpdateRequest { it.copy(apiKeyValue = v) } },
                singleLine = true,
                replaceAll = true,
              ),
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
      EdgeSharedTextEditor(
        surfaceKey = "api_tester_body_${req.id}_${req.bodyType.name}",
        value = req.rawBody,
        onValueChange = { body -> onUpdateRequest { it.copy(rawBody = body) } },
        label = if (req.bodyType == ApiBodyType.JSON) "JSON Payload" else "Raw Text Body",
        placeholder = if (req.bodyType == ApiBodyType.JSON) "{\n  \"key\": \"value\"\n}" else "Raw request payload...",
        language = if (req.bodyType == ApiBodyType.JSON) WorkerLanguage.JSON else WorkerLanguage.TEXT,
        height = 168.dp,
        testTagName = "api_body_input",
      )
      if (req.bodyType == ApiBodyType.JSON) {
        TextButton(onClick = { onUpdateRequest { it.copy(rawBody = ApiRequestUtils.prettyPrintIfJson(it.rawBody)) } }) {
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
        modifier =
          Modifier
            .weight(1f)
            .edgeTextDropTarget(
              targetSurfaceId = "Row Key",
              currentValue = row.key,
              onValueChange = { k -> onRowsChange(rows.mapIndexed { i, r -> if (i == idx) r.copy(key = k) else r }) },
              singleLine = true,
              replaceAll = true,
            ),
      )
      OutlinedTextField(
        value = row.value,
        onValueChange = { v -> onRowsChange(rows.mapIndexed { i, r -> if (i == idx) r.copy(value = v) else r }) },
        placeholder = { Text(valuePlaceholder) },
        singleLine = true,
        modifier =
          Modifier
            .weight(1.2f)
            .edgeTextDropTarget(
              targetSurfaceId = "Row Value",
              currentValue = row.value,
              onValueChange = { v -> onRowsChange(rows.mapIndexed { i, r -> if (i == idx) r.copy(value = v) else r }) },
              singleLine = true,
            ),
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
    border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.5f)),
    modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
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

private fun isLikelyHtmlResponse(headers: Map<String, String>, body: String): Boolean {
  val ct = headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value.orEmpty()
  if (ct.contains("text/html", ignoreCase = true) || ct.contains("application/xhtml", ignoreCase = true)) return true
  val lead = body.trimStart()
  return lead.startsWith("<!doctype html", ignoreCase = true) ||
    lead.startsWith("<html", ignoreCase = true) ||
    lead.startsWith("<body", ignoreCase = true)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxedHtmlPreviewBox(
  baseUrl: String,
  html: String,
  modifier: Modifier = Modifier,
) {
  val renderKey = remember(baseUrl, html) { baseUrl to html }
  AndroidView(
    factory = { ctx ->
      WebView(ctx).apply {
        setBackgroundColor(android.graphics.Color.WHITE)
        settings.javaScriptEnabled = false
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        webViewClient = WebViewClient()
        tag = renderKey
        loadDataWithBaseURL(baseUrl.ifBlank { "https://localhost/" }, html, "text/html", "utf-8", null)
      }
    },
    update = { wv ->
      if (wv.tag != renderKey) {
        wv.tag = renderKey
        wv.loadDataWithBaseURL(baseUrl.ifBlank { "https://localhost/" }, html, "text/html", "utf-8", null)
      }
    },
    modifier = modifier,
  )
}

private fun detectApiResponseBadge(
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
