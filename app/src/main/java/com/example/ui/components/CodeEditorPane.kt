package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.remote.DiagnosticSeverity
import com.example.editor.CodeSyntaxVisualTransformation
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.viewmodel.IdeUiState

@Composable
fun CodeEditorPane(
  uiState: IdeUiState,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onNavigateTabBack: () -> Unit,
  onNavigateTabForward: () -> Unit,
  onImportAndRunFile: () -> Unit,
  onCreateAndRunFile: () -> Unit,
  onRunActiveFile: () -> Unit,
  onCodeChange: (TextFieldValue) -> Unit,
  onInsertText: (String, Int) -> Unit,
  onInsertTab: () -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onFormat: () -> Unit,
  onToggleComment: () -> Unit,
  onToggleFindBar: () -> Unit,
  onFindQueryChange: (String) -> Unit,
  onReplaceQueryChange: (String) -> Unit,
  onFindNext: () -> Unit,
  onReplaceAll: () -> Unit,
  onOpenProblems: () -> Unit,
  onOpenExplorer: () -> Unit,
  onShowNewWorkerDialog: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val activeFile = uiState.activeFile

  Column(modifier = modifier.fillMaxSize().background(palette.editorBg)) {
    // Rounded Capsule Tabs Strip (matching screenshot)
    if (uiState.openTabPaths.isNotEmpty()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(palette.tabBarBg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        uiState.openTabPaths.forEach { path ->
          val file = uiState.workspace?.files?.firstOrNull { it.path == path }
          val isActive = path == uiState.activeFilePath
          Surface(
            onClick = { onSelectTab(path) },
            shape = RoundedCornerShape(14.dp),
            color = if (isActive) palette.tabActiveBg else palette.chromeSurface,
            modifier = Modifier.testTag("editor_tab_$path"),
          ) {
            Row(
              modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              FileTypeBadge(path = path)
              Text(
                text = path.substringAfterLast('/'),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) palette.textPrimary else palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (file?.isDirty == true) {
                Box(
                  modifier =
                    Modifier
                      .size(7.dp)
                      .clip(CircleShape)
                      .background(palette.activePillFg),
                )
              }
              IconButton(
                onClick = { onCloseTab(path) },
                modifier = Modifier.size(22.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = stringResource(R.string.cd_close_tab),
                  tint = palette.textMuted,
                  modifier = Modifier.size(13.dp),
                )
              }
            }
          }
        }
      }
    }

    if (activeFile == null) {
      EmptyWorkspaceWelcome(
        hasWorkers = uiState.workers.isNotEmpty(),
        onOpenExplorer = onOpenExplorer,
        onShowNewWorkerDialog = onShowNewWorkerDialog,
        onImportAndRunFile = onImportAndRunFile,
        onCreateAndRunFile = onCreateAndRunFile,
      )
      return
    }

    // Tab-history navigation chevrons (< >) + Breadcrumb Strip (matching Screenshot 2)
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(palette.editorBg)
          .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onNavigateTabBack,
        enabled = uiState.canNavigateTabBack,
        modifier = Modifier.size(30.dp).testTag("editor_nav_back"),
      ) {
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowLeft,
          contentDescription = "Previous tab",
          tint = if (uiState.canNavigateTabBack) palette.textPrimary else palette.textMuted.copy(alpha = 0.45f),
          modifier = Modifier.size(18.dp),
        )
      }
      IconButton(
        onClick = onNavigateTabForward,
        enabled = uiState.canNavigateTabForward,
        modifier = Modifier.size(30.dp).testTag("editor_nav_forward"),
      ) {
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowRight,
          contentDescription = "Next tab",
          tint = if (uiState.canNavigateTabForward) palette.textPrimary else palette.textMuted.copy(alpha = 0.45f),
          modifier = Modifier.size(18.dp),
        )
      }
      Spacer(Modifier.width(4.dp))

      val segments =
        buildList {
          add(uiState.workspace?.scriptName ?: "worker")
          addAll(activeFile.path.split('/'))
        }
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        segments.forEachIndexed { idx, seg ->
          Text(
            text = seg,
            style = MaterialTheme.typography.labelSmall,
            color = if (idx == segments.lastIndex) palette.textPrimary else palette.textSecondary,
            fontWeight = if (idx == segments.lastIndex) FontWeight.Medium else FontWeight.Normal,
          )
          if (idx < segments.lastIndex) {
            Icon(
              imageVector = Icons.Outlined.KeyboardArrowRight,
              contentDescription = null,
              tint = palette.textMuted,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      if (uiState.diagnostics.isNotEmpty()) {
        val hasErr = uiState.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
        Surface(
          onClick = onOpenProblems,
          shape = RoundedCornerShape(999.dp),
          color =
            if (hasErr) MaterialTheme.colorScheme.errorContainer
            else palette.activePillBg,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              Icons.Outlined.WarningAmber,
              contentDescription = "Diagnostics",
              modifier = Modifier.size(12.dp),
              tint =
                if (hasErr) MaterialTheme.colorScheme.onErrorContainer
                else palette.activePillFg,
            )
            Text(
              text = "${uiState.diagnostics.size}",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (hasErr) MaterialTheme.colorScheme.onErrorContainer
                else palette.activePillFg,
            )
          }
        }
      }

      IconButton(
        onClick = onToggleFindBar,
        modifier = Modifier.size(32.dp).testTag("find_replace_toggle"),
      ) {
        Icon(
          Icons.Outlined.Search,
          contentDescription = stringResource(R.string.cd_find_replace),
          tint = palette.textSecondary,
          modifier = Modifier.size(16.dp),
        )
      }
      if (!activeFile.isVirtualReadOnly) {
        IconButton(
          onClick = onRunActiveFile,
          modifier = Modifier.size(32.dp).testTag("editor_run_script_button"),
        ) {
          Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Run script locally without deploying",
            tint = palette.activePillFg,
            modifier = Modifier.size(18.dp),
          )
        }
        IconButton(
          onClick = onFormat,
          modifier = Modifier.size(32.dp).testTag("format_code_button"),
        ) {
          Icon(
            Icons.AutoMirrored.Outlined.FormatIndentIncrease,
            contentDescription = stringResource(R.string.cd_format_code),
            tint = palette.textSecondary,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }

    // Collapsible Find & Replace Strip
    AnimatedVisibility(visible = uiState.showFindBar) {
      Surface(
        color = palette.chromeSurface,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
              value = uiState.findQuery,
              onValueChange = onFindQueryChange,
              placeholder = { Text("Find") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onFindNext, enabled = uiState.findQuery.isNotEmpty()) {
              Text("Next")
            }
          }
          if (!activeFile.isVirtualReadOnly) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              OutlinedTextField(
                value = uiState.replaceQuery,
                onValueChange = onReplaceQueryChange,
                placeholder = { Text("Replace") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              TextButton(onClick = onReplaceAll, enabled = uiState.findQuery.isNotEmpty()) {
                Text("Replace All")
              }
            }
          }
        }
      }
    }

    // Code Editor with Gutter, Active Line Highlight, Fold Marker & Vertical Gutter Divider
    val tfv = uiState.activeTextFieldValue
    val fontSize = uiState.editorSettings.fontSizeSp.sp
    val lineHeight = (uiState.editorSettings.fontSizeSp + 7).sp
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val codeLines = remember(tfv.text) { tfv.text.lines() }
    val activeLine = uiState.cursorLineAndCol.first
    val visualTransformation =
      remember(activeFile.language, palette) {
        CodeSyntaxVisualTransformation(activeFile.language, palette)
      }

    Row(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(verticalScroll),
    ) {
      if (uiState.editorSettings.showLineNumbers) {
        Column(
          modifier =
            Modifier
              .background(palette.editorGutterBg)
              .padding(top = 8.dp, bottom = 12.dp),
          horizontalAlignment = Alignment.End,
        ) {
          codeLines.forEachIndexed { idx, lineText ->
            val lineNum = idx + 1
            val isCurrent = lineNum == activeLine
            val hasBlockOpen = lineText.trimEnd().endsWith("{")
            Row(
              modifier =
                Modifier
                  .background(if (isCurrent) palette.editorActiveLineBg else palette.editorGutterBg)
                  .padding(start = 10.dp, end = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = lineNum.toString(),
                style =
                  TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = if (isCurrent) palette.textPrimary else palette.textMuted,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                  ),
              )
              Text(
                text = if (hasBlockOpen) "▾" else " ",
                style =
                  TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    lineHeight = lineHeight,
                    color = palette.textMuted,
                  ),
              )
            }
          }
        }

        VerticalDivider(color = palette.border, thickness = 1.dp)
      }

      val editorModifier =
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .then(
            if (!uiState.editorSettings.wordWrap) Modifier.horizontalScroll(horizontalScroll)
            else Modifier
          )
          .padding(horizontal = 10.dp, vertical = 8.dp)
          .testTag("code_editor_input")

      BasicTextField(
        value = tfv,
        onValueChange = { if (!activeFile.isVirtualReadOnly) onCodeChange(it) },
        readOnly = activeFile.isVirtualReadOnly,
        textStyle =
          TextStyle(
            fontFamily = JetBrainsMono,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = palette.textPrimary,
          ),
        cursorBrush = SolidColor(palette.activePillFg),
        visualTransformation = visualTransformation,
        modifier = editorModifier,
      )
    }

    // Compact Coding Symbol Accessory Bar
    if (!activeFile.isVirtualReadOnly) {
      HorizontalDivider(color = palette.border)
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(palette.chromeSurface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onUndo, modifier = Modifier.size(34.dp).testTag("editor_undo")) {
          Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo", tint = palette.textSecondary, modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onRedo, modifier = Modifier.size(34.dp).testTag("editor_redo")) {
          Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo", tint = palette.textSecondary, modifier = Modifier.size(17.dp))
        }
        AccessoryKey("TAB") { onInsertTab() }
        AccessoryKey("//") { onToggleComment() }
        AccessoryKey("{ }") { onInsertText("{}", 1) }
        AccessoryKey("( )") { onInsertText("()", 1) }
        AccessoryKey("[ ]") { onInsertText("[]", 1) }
        AccessoryKey("=>") { onInsertText(" => ", 0) }
        AccessoryKey(";") { onInsertText(";", 0) }
        AccessoryKey(":") { onInsertText(": ", 0) }
        AccessoryKey("\"") { onInsertText("\"\"", 1) }
        AccessoryKey("'") { onInsertText("''", 1) }
        AccessoryKey("`") { onInsertText("``", 1) }
        AccessoryKey("=") { onInsertText(" = ", 0) }
        AccessoryKey(".") { onInsertText(".", 0) }
      }
    }
  }
}

@Composable
private fun AccessoryKey(label: String, onClick: () -> Unit) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = palette.chromePill,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = palette.textPrimary,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
  }
}

@Composable
private fun EmptyWorkspaceWelcome(
  hasWorkers: Boolean,
  onOpenExplorer: () -> Unit,
  onShowNewWorkerDialog: () -> Unit,
  onImportAndRunFile: () -> Unit,
  onCreateAndRunFile: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 400.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_empty_workspace),
        contentDescription = stringResource(R.string.cd_empty_workspace),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(132.dp).clip(RoundedCornerShape(28.dp)),
      )
      Text(
        text = "EdgeCode Workspace",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary,
      )
      Text(
        text =
          if (hasWorkers) "Open a Worker/Pages project, or import/create any script to run & test locally without deploying."
          else "Import a file or create a script to test locally first, then deploy when ready.",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.textSecondary,
        textAlign = TextAlign.Center,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
          onClick = onImportAndRunFile,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(48.dp).testTag("welcome_import_run_button"),
        ) {
          Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Import & Run")
        }
        OutlinedButton(
          onClick = onCreateAndRunFile,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(48.dp).testTag("welcome_create_run_button"),
        ) {
          Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("New Script")
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasWorkers) {
          OutlinedButton(
            onClick = onOpenExplorer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(44.dp),
          ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("Explorer")
          }
        }
        Button(
          onClick = onShowNewWorkerDialog,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(44.dp).testTag("welcome_new_worker_button"),
        ) {
          Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(6.dp))
          Text("New Worker")
        }
      }
    }
  }
}
