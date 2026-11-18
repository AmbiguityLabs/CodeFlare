package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.idecore.DiffLineKind
import com.example.idecore.HistoryDiffLine
import com.example.idecore.LocalHistorySnapshot
import com.example.idecore.LocalHistoryStore
import com.example.idecore.search.ProjectSearchOptions
import com.example.idecore.search.ProjectSymbolHit
import com.example.idecore.search.ProjectTextMatch
import com.example.idecore.search.RenamePreview
import com.example.idecore.search.WorkspaceSearchAndRefactor
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class IdeCoreSheetTab(val label: String) {
  SYMBOLS("Symbols"),
  FIND_IN_FILES("Find"),
  LOCAL_HISTORY("History"),
}

@Composable
fun IdeSearchAndHistorySheet(
  initialTab: IdeCoreSheetTab,
  activeFilePath: String?,
  activeFileContent: String,
  onSearchSymbols: (String) -> List<ProjectSymbolHit>,
  onFindInFiles: (String, ProjectSearchOptions) -> List<ProjectTextMatch>,
  onLoadHistory: () -> List<LocalHistorySnapshot>,
  onReadSnapshot: (Long) -> String?,
  onRestoreSnapshot: (Long) -> Unit,
  onJumpToLine: (String, Int, Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = palette.chromeBg,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.65f)),
      modifier =
        Modifier
          .fillMaxSize()
          .padding(horizontal = 10.dp, vertical = 18.dp)
          .testTag("ide_core_sheet"),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Segmented top header
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier =
              Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(palette.chromeSurface)
                .border(0.75.dp, palette.border.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            IdeCoreSheetTab.entries.forEach { tab ->
              val isSelected = selectedTab == tab
              val icon =
                when (tab) {
                  IdeCoreSheetTab.SYMBOLS -> Icons.Outlined.Tag
                  IdeCoreSheetTab.FIND_IN_FILES -> Icons.Outlined.Search
                  IdeCoreSheetTab.LOCAL_HISTORY -> Icons.Outlined.History
                }
              Surface(
                onClick = { selectedTab = tab },
                shape = RoundedCornerShape(7.dp),
                color = if (isSelected) palette.activePillBg else palette.chromeSurface,
                modifier = Modifier.weight(1f).testTag("ide_sheet_tab_${tab.name.lowercase()}"),
              ) {
                Row(
                  modifier = Modifier.padding(vertical = 7.dp, horizontal = 6.dp),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = if (isSelected) palette.activePillFg else palette.textMuted,
                    modifier = Modifier.size(14.dp),
                  )
                  Spacer(Modifier.width(5.dp))
                  Text(
                    text = tab.label,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) palette.activePillFg else palette.textSecondary,
                    maxLines = 1,
                  )
                }
              }
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(34.dp).testTag("ide_core_sheet_close"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Close",
              tint = palette.textSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        HorizontalDivider(color = palette.border.copy(alpha = 0.45f))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          when (selectedTab) {
            IdeCoreSheetTab.SYMBOLS ->
              ProjectSymbolsPane(
                onSearchSymbols = onSearchSymbols,
                onSelect = { hit ->
                  onJumpToLine(hit.filePath, hit.line, 1)
                  onDismiss()
                },
              )

            IdeCoreSheetTab.FIND_IN_FILES ->
              FindInFilesPane(
                onFindInFiles = onFindInFiles,
                onSelect = { match ->
                  onJumpToLine(match.filePath, match.line, match.col)
                  onDismiss()
                },
              )

            IdeCoreSheetTab.LOCAL_HISTORY ->
              LocalHistoryPane(
                activeFilePath = activeFilePath,
                activeFileContent = activeFileContent,
                onLoadHistory = onLoadHistory,
                onReadSnapshot = onReadSnapshot,
                onRestore = { id ->
                  onRestoreSnapshot(id)
                  onDismiss()
                },
              )
          }
        }
      }
    }
  }
}

@Composable
private fun ProjectSymbolsPane(
  onSearchSymbols: (String) -> List<ProjectSymbolHit>,
  onSelect: (ProjectSymbolHit) -> Unit,
) {
  val palette = LocalIdePalette.current
  var query by remember { mutableStateOf("") }
  val hits = remember(query) { onSearchSymbols(query) }

  Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    HairlineMonoSearchInput(
      value = query,
      placeholder = "Symbol…",
      trailingBadge = "${hits.size}",
      tag = "ide_symbols_input",
      onValueChange = { query = it },
    )

    if (hits.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text = "No symbols",
          fontFamily = JetBrainsMono,
          fontSize = 12.sp,
          color = palette.textMuted,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(hits, key = { "${it.filePath}:${it.line}:${it.kind}:${it.name}" }) { hit ->
          Surface(
            onClick = { onSelect(hit) },
            shape = RoundedCornerShape(9.dp),
            color = palette.chromeSurface,
            border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              MonoKindChip(label = hit.kind)
              Text(
                text = hit.name,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              Text(
                text = "${hit.fileName}:${hit.line}",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = palette.textMuted,
                maxLines = 1,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FindInFilesPane(
  onFindInFiles: (String, ProjectSearchOptions) -> List<ProjectTextMatch>,
  onSelect: (ProjectTextMatch) -> Unit,
) {
  val palette = LocalIdePalette.current
  var query by remember { mutableStateOf("") }
  var caseSensitive by remember { mutableStateOf(false) }
  var wholeWord by remember { mutableStateOf(false) }
  var regex by remember { mutableStateOf(false) }

  val options = remember(caseSensitive, wholeWord, regex) {
    ProjectSearchOptions(caseSensitive = caseSensitive, wholeWord = wholeWord, regex = regex)
  }
  val matches = remember(query, options) {
    if (query.isBlank()) emptyList() else onFindInFiles(query, options)
  }

  Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Box(modifier = Modifier.weight(1f)) {
        HairlineMonoSearchInput(
          value = query,
          placeholder = "Search workspace…",
          trailingBadge = if (query.isBlank()) null else "${matches.size}",
          tag = "ide_find_files_input",
          onValueChange = { query = it },
        )
      }
      SearchOptionPill("Aa", caseSensitive, "ide_find_opt_case") { caseSensitive = !caseSensitive }
      SearchOptionPill("W", wholeWord, "ide_find_opt_word") { wholeWord = !wholeWord }
      SearchOptionPill(".*", regex, "ide_find_opt_regex") { regex = !regex }
    }

    if (query.isBlank() || matches.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text = if (query.isBlank()) "Type to search" else "0 matches",
          fontFamily = JetBrainsMono,
          fontSize = 12.sp,
          color = palette.textMuted,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        items(matches, key = { "${it.filePath}:${it.offset}:${it.matchEnd}" }) { match ->
          Surface(
            onClick = { onSelect(match) },
            shape = RoundedCornerShape(9.dp),
            color = palette.chromeSurface,
            border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = match.filePath,
                  fontFamily = JetBrainsMono,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = "${match.line}:${match.col}",
                  fontFamily = JetBrainsMono,
                  fontSize = 9.5.sp,
                  color = palette.textMuted,
                )
              }
              val trimmedStart = match.lineText.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
              val previewText = match.lineText.substring(trimmedStart)
              val hlStart = (match.matchStart - trimmedStart).coerceIn(0, previewText.length)
              val hlEnd = (match.matchEnd - trimmedStart).coerceIn(hlStart, previewText.length)
              val annotated =
                buildAnnotatedString {
                  append(previewText.substring(0, hlStart))
                  withStyle(
                    SpanStyle(
                      background = palette.activePillBg,
                      color = palette.activePillFg,
                      fontWeight = FontWeight.Bold,
                    ),
                  ) {
                    append(previewText.substring(hlStart, hlEnd))
                  }
                  append(previewText.substring(hlEnd))
                }
              Text(
                text = annotated,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LocalHistoryPane(
  activeFilePath: String?,
  activeFileContent: String,
  onLoadHistory: () -> List<LocalHistorySnapshot>,
  onReadSnapshot: (Long) -> String?,
  onRestore: (Long) -> Unit,
) {
  val palette = LocalIdePalette.current
  val snapshots = remember(activeFilePath) { onLoadHistory() }
  var selectedId by remember(activeFilePath, snapshots) {
    mutableStateOf(snapshots.firstOrNull()?.id)
  }
  val selectedSnap = snapshots.firstOrNull { it.id == selectedId }
  val diffLines: List<HistoryDiffLine> =
    remember(selectedId, activeFileContent) {
      val id = selectedId ?: return@remember emptyList()
      val snapText = onReadSnapshot(id) ?: return@remember emptyList()
      LocalHistoryStore.computeLineDiff(snapText, activeFileContent)
    }

  val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
  val dateFmt = remember { SimpleDateFormat("MMM d", Locale.US) }

  Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = activeFilePath ?: "No file",
        fontFamily = JetBrainsMono,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      MonoKindChip(label = "${snapshots.size}")
    }

    if (snapshots.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
          text = "No snapshots",
          fontFamily = JetBrainsMono,
          fontSize = 12.sp,
          color = palette.textMuted,
        )
      }
    } else {
      LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(snapshots, key = { it.id }) { snap ->
          val active = snap.id == selectedId
          val dt = Date(snap.timestampMs)
          Surface(
            onClick = { selectedId = snap.id },
            shape = RoundedCornerShape(9.dp),
            color = if (active) palette.activePillBg else palette.chromeSurface,
            border =
              BorderStroke(
                0.75.dp,
                if (active) palette.activePillFg.copy(alpha = 0.45f) else palette.border.copy(alpha = 0.5f),
              ),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Text(
                  text = timeFmt.format(dt),
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (active) palette.activePillFg else palette.textPrimary,
                )
                if (snap.label.isNotBlank()) {
                  Text(
                    text = snap.label,
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) palette.activePillFg else AmberWarn,
                  )
                }
              }
              Text(
                text = "${dateFmt.format(dt)} · ${snap.lineCount} ln",
                fontFamily = JetBrainsMono,
                fontSize = 9.5.sp,
                color = palette.textMuted,
              )
            }
          }
        }
      }

      // Unified monospace diff viewport
      Surface(
        color = palette.editorBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) {
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
        ) {
          items(diffLines) { dl ->
            val bg =
              when (dl.kind) {
                DiffLineKind.ADDED -> EmeraldLive.copy(alpha = 0.14f)
                DiffLineKind.REMOVED -> RoseError.copy(alpha = 0.14f)
                DiffLineKind.CONTEXT -> palette.editorBg
              }
            val fg =
              when (dl.kind) {
                DiffLineKind.ADDED -> EmeraldLive
                DiffLineKind.REMOVED -> RoseError
                DiffLineKind.CONTEXT -> palette.textSecondary
              }
            val prefix =
              when (dl.kind) {
                DiffLineKind.ADDED -> "+"
                DiffLineKind.REMOVED -> "-"
                DiffLineKind.CONTEXT -> " "
              }
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(bg)
                  .horizontalScroll(rememberScrollState())
                  .padding(horizontal = 8.dp, vertical = 1.5.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = prefix,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
              )
              Text(
                text = dl.text,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = if (dl.kind == DiffLineKind.CONTEXT) palette.textSecondary else palette.textPrimary,
                maxLines = 1,
                softWrap = false,
              )
            }
          }
        }
      }

      if (selectedSnap != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = { onRestore(selectedSnap.id) },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(42.dp).testTag("ide_history_restore_button"),
          ) {
            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Restore", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }
}

@Composable
fun SmartRenameSymbolDialog(
  initialSymbol: String,
  onPreview: (String) -> RenamePreview,
  onConfirmRename: (oldName: String, newName: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  var oldName by remember(initialSymbol) { mutableStateOf(initialSymbol.trim()) }
  var newName by remember(initialSymbol) { mutableStateOf(initialSymbol.trim()) }

  val preview = remember(oldName) { onPreview(oldName.trim()) }
  val validOld = WorkspaceSearchAndRefactor.isValidIdentifier(oldName.trim())
  val validNew = WorkspaceSearchAndRefactor.isValidIdentifier(newName.trim())
  val canApply = validOld && validNew && oldName.trim() != newName.trim() && preview.totalMatches > 0

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = palette.chromeBg,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.65f)),
      modifier = Modifier.fillMaxWidth().testTag("smart_rename_dialog"),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.DriveFileRenameOutline,
            contentDescription = null,
            tint = palette.activePillFg,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Rename Symbol",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f),
          )
          if (preview.totalMatches > 0) {
            MonoKindChip(label = "${preview.totalMatches} · ${preview.fileCounts.size}f")
          }
        }

        HairlineMonoSearchInput(
          value = oldName,
          placeholder = "Current identifier",
          trailingBadge = if (oldName.isNotBlank() && !validOld) "!" else null,
          tag = "rename_old_input",
          onValueChange = { oldName = it },
        )

        HairlineMonoSearchInput(
          value = newName,
          placeholder = "New identifier",
          trailingBadge = if (newName.isNotBlank() && !validNew) "!" else null,
          tag = "rename_new_input",
          onValueChange = { newName = it },
        )

        if (preview.fileCounts.isNotEmpty()) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items(preview.fileCounts) { (path, count) ->
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = palette.chromeSurface,
                border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.45f)),
              ) {
                Text(
                  text = "${path.substringAfterLast('/')} ($count)",
                  fontFamily = JetBrainsMono,
                  fontSize = 10.sp,
                  color = palette.textSecondary,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel", color = palette.textSecondary)
          }
          Spacer(Modifier.width(6.dp))
          Button(
            enabled = canApply,
            onClick = {
              onConfirmRename(oldName.trim(), newName.trim())
              onDismiss()
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(40.dp).testTag("rename_confirm_button"),
          ) {
            Text("Rename", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }
}

@Composable
private fun HairlineMonoSearchInput(
  value: String,
  placeholder: String,
  trailingBadge: String?,
  tag: String,
  onValueChange: (String) -> Unit,
) {
  val palette = LocalIdePalette.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(38.dp)
        .clip(RoundedCornerShape(9.dp))
        .background(palette.editorBg)
        .border(0.75.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
        .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (value.isEmpty()) {
        Text(
          text = placeholder,
          fontFamily = JetBrainsMono,
          fontSize = 12.sp,
          color = palette.textMuted,
          maxLines = 1,
        )
      }
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = palette.textPrimary),
        cursorBrush = SolidColor(palette.activePillFg),
        modifier = Modifier.fillMaxWidth().testTag(tag),
      )
    }
    if (trailingBadge != null) {
      Text(
        text = trailingBadge,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (trailingBadge == "!") RoseError else palette.activePillFg,
      )
    }
  }
}

@Composable
private fun SearchOptionPill(
  label: String,
  active: Boolean,
  tag: String,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = if (active) palette.activePillBg else palette.chromeSurface,
    border =
      BorderStroke(
        0.75.dp,
        if (active) palette.activePillFg.copy(alpha = 0.45f) else palette.border.copy(alpha = 0.55f),
      ),
    modifier = Modifier.height(38.dp).testTag(tag),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 9.dp)) {
      Text(
        text = label,
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (active) palette.activePillFg else palette.textSecondary,
      )
    }
  }
}

@Composable
private fun MonoKindChip(label: String) {
  val palette = LocalIdePalette.current
  Box(
    modifier =
      Modifier
        .clip(RoundedCornerShape(5.dp))
        .background(palette.activePillBg.copy(alpha = 0.85f))
        .border(0.5.dp, palette.activePillFg.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
        .padding(horizontal = 5.dp, vertical = 2.dp),
  ) {
    Text(
      text = label,
      fontFamily = JetBrainsMono,
      fontSize = 9.sp,
      fontWeight = FontWeight.SemiBold,
      color = palette.activePillFg,
      maxLines = 1,
    )
  }
}
