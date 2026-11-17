package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SnippetFolder
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.remote.WorkerFile
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.FolderGreen
import com.example.ui.theme.FolderYellow
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.viewmodel.IdeUiState

private sealed class ProjectTreeEntry {
  abstract val id: String
  abstract val depth: Int
  abstract val label: String

  data class FolderNode(
    override val id: String,
    override val depth: Int,
    override val label: String,
    val folderPath: String,
    val isRoot: Boolean = false,
  ) : ProjectTreeEntry()

  data class FileNode(
    override val id: String,
    override val depth: Int,
    override val label: String,
    val file: WorkerFile,
  ) : ProjectTreeEntry()
}

@Composable
fun ProjectFilesSidebar(
  uiState: IdeUiState,
  onOpenFile: (String) -> Unit,
  onShowNewFileDialog: (String) -> Unit,
  onShowNewDirectoryDialog: (String) -> Unit,
  onUploadSingleFile: () -> Unit,
  onUploadBatchFiles: () -> Unit,
  onExportActiveFile: () -> Unit,
  onDeleteModuleFile: (String) -> Unit,
  onRequestRenameModuleFile: (String) -> Unit,
  onSetEntryModule: (String) -> Unit,
  onRevertWorkspaceToImported: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onReplaceInAllFiles: (String, String) -> Unit,
  onJumpToLine: (String, Int) -> Unit,
  onInsertSnippet: (String) -> Unit,
  onCloseDrawerIfCompact: () -> Unit,
  showCloseButton: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Files, 1 = Search, 2 = Snippets
  var pendingDeleteModule by remember { mutableStateOf<String?>(null) }
  var uploadMenuOpen by remember { mutableStateOf(false) }
  val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

  pendingDeleteModule?.let { moduleToDelete ->
    AlertDialog(
      onDismissRequest = { pendingDeleteModule = null },
      title = { Text("Delete Script File?") },
      text = {
        Text(
          "Remove '$moduleToDelete' from ${uiState.workspace?.scriptName ?: "this project"}? Deploy to push this deletion live.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteModuleFile(moduleToDelete)
            pendingDeleteModule = null
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete")
        }
      },
      dismissButton = { TextButton(onClick = { pendingDeleteModule = null }) { Text("Cancel") } },
    )
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(palette.chromeBg),
  ) {
    // Clean Project Sidebar Header
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = uiState.workspace?.scriptName ?: "Project",
          style = MaterialTheme.typography.titleMedium,
          color = palette.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val editableCount = uiState.workspace?.files?.count { !it.isVirtualReadOnly } ?: 0
        val dirtyCount = uiState.workspace?.files?.count { it.isDirty } ?: 0
        Text(
          text =
            if (dirtyCount > 0) "$editableCount files · $dirtyCount edited"
            else "$editableCount files · Synced in RAM",
          style = MaterialTheme.typography.labelSmall,
          color = if (dirtyCount > 0) AmberWarn else palette.textMuted,
          maxLines = 1,
        )
      }

      if (showCloseButton) {
        IconButton(
          onClick = onCloseDrawerIfCompact,
          modifier = Modifier.size(36.dp).testTag("collapse_sidebar_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Close files drawer",
            tint = palette.textSecondary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }

    // Focused 3-Tab Segmented Switcher (Files / Search / Snippets)
    Surface(
      color = palette.chromePill,
      shape = RoundedCornerShape(12.dp),
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 4.dp)
          .height(38.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxSize().padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        SidebarSegmentChip(
          selected = activeSubTab == 0,
          icon = Icons.Outlined.Description,
          label = "Files",
          tag = "rail_explorer",
          onClick = { activeSubTab = 0 },
          modifier = Modifier.weight(1f),
        )
        SidebarSegmentChip(
          selected = activeSubTab == 1,
          icon = Icons.Outlined.Search,
          label = "Search",
          tag = "rail_search",
          onClick = { activeSubTab = 1 },
          modifier = Modifier.weight(1f),
        )
        SidebarSegmentChip(
          selected = activeSubTab == 2,
          icon = Icons.Outlined.SnippetFolder,
          label = "Snippets",
          tag = "rail_snippets",
          onClick = { activeSubTab = 2 },
          modifier = Modifier.weight(1f),
        )
      }
    }

    Spacer(Modifier.height(4.dp))

    when (activeSubTab) {
      0 -> {
        // Project File Actions Bar
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          QuickFileActionButton(
            icon = Icons.Outlined.NoteAdd,
            label = "File",
            tag = "add_module_file_button",
            onClick = { onShowNewFileDialog("") },
            modifier = Modifier.weight(1f),
          )
          QuickFileActionButton(
            icon = Icons.Outlined.CreateNewFolder,
            label = "Folder",
            tag = "add_module_dir_button",
            onClick = { onShowNewDirectoryDialog("") },
            modifier = Modifier.weight(1f),
          )
          Box(modifier = Modifier.weight(1f)) {
            QuickFileActionButton(
              icon = Icons.Outlined.UploadFile,
              label = "Upload",
              tag = "upload_files_menu_button",
              onClick = { uploadMenuOpen = true },
              modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = uploadMenuOpen, onDismissRequest = { uploadMenuOpen = false }) {
              DropdownMenuItem(
                text = { Text("Upload Single File") },
                onClick = {
                  uploadMenuOpen = false
                  onUploadSingleFile()
                },
              )
              DropdownMenuItem(
                text = { Text("Batch Upload Files") },
                onClick = {
                  uploadMenuOpen = false
                  onUploadBatchFiles()
                },
              )
              uiState.activeFile?.let { active ->
                DropdownMenuItem(
                  text = { Text("Export ${active.fileName}") },
                  onClick = {
                    uploadMenuOpen = false
                    onExportActiveFile()
                  },
                )
              }
            }
          }
        }

        HorizontalDivider(
          color = palette.border.copy(alpha = 0.3f),
          modifier = Modifier.padding(top = 4.dp),
        )

        val ws = uiState.workspace
        val treeRows =
          remember(ws, expandedFolders.toMap()) {
            ws?.let {
              buildProjectTreeEntries(it.scriptName, it.files, it.directories, expandedFolders)
            }.orEmpty()
          }
        val editableFileCount = remember(ws) { ws?.files?.count { !it.isVirtualReadOnly } ?: 0 }

        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          if (ws != null) {
            items(treeRows, key = { it.id }) { entry ->
              when (entry) {
                is ProjectTreeEntry.FolderNode -> {
                  val isExpanded = expandedFolders[entry.id] ?: true
                  ProjectTreeRowItem(
                    depth = entry.depth,
                    isExpanded = isExpanded,
                    isFolder = true,
                    isRootFolder = entry.isRoot,
                    isSelected = false,
                    canDeleteFile = false,
                    label = entry.label,
                    contextTitle =
                      if (entry.folderPath.isBlank()) "New in ${ws.scriptName}"
                      else "New in ${entry.folderPath}",
                    file = null,
                    onClick = { expandedFolders[entry.id] = !isExpanded },
                    onNewFileInFolder = { onShowNewFileDialog(entry.folderPath) },
                    onNewDirInFolder = { onShowNewDirectoryDialog(entry.folderPath) },
                    onRenameClick = {},
                    onSetEntryClick = {},
                    onExportClick = {},
                    onDeleteFileClick = {},
                  )
                }
                is ProjectTreeEntry.FileNode -> {
                  val active = entry.file.path == uiState.activeFilePath
                  val parentFolder =
                    if (entry.file.path.contains('/')) entry.file.path.substringBeforeLast('/')
                    else ""
                  ProjectTreeRowItem(
                    depth = entry.depth,
                    isExpanded = false,
                    isFolder = false,
                    isRootFolder = false,
                    isSelected = active,
                    canDeleteFile = editableFileCount > 1 && !entry.file.isVirtualReadOnly,
                    label = entry.label,
                    contextTitle = entry.file.fileName,
                    file = entry.file,
                    onClick = {
                      onOpenFile(entry.file.path)
                      onCloseDrawerIfCompact()
                    },
                    onNewFileInFolder = { onShowNewFileDialog(parentFolder) },
                    onNewDirInFolder = { onShowNewDirectoryDialog(parentFolder) },
                    onRenameClick = { onRequestRenameModuleFile(entry.file.path) },
                    onSetEntryClick = { onSetEntryModule(entry.file.path) },
                    onExportClick = {
                      onOpenFile(entry.file.path)
                      onExportActiveFile()
                    },
                    onDeleteFileClick = { pendingDeleteModule = entry.file.path },
                  )
                }
              }
            }
          }
        }

        if (uiState.hasUnsavedChanges) {
          HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "${uiState.ramDbStats.dirtyFilesCount} unsaved edit(s) in RAM",
              style = MaterialTheme.typography.labelSmall,
              color = AmberWarn,
            )
            TextButton(onClick = onRevertWorkspaceToImported) {
              Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(Modifier.width(4.dp))
              Text("Revert", fontSize = 12.sp)
            }
          }
        }
      }

      1 -> {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          GlobalSearchPanel(
            uiState = uiState,
            onQueryChange = onSearchQueryChange,
            onReplaceAllInWorkspace = onReplaceInAllFiles,
            onJump = { path, line ->
              onJumpToLine(path, line)
              onCloseDrawerIfCompact()
            },
          )
        }
      }

      2 -> {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          SnippetsPanel(
            hasActiveFile = uiState.activeFile?.isVirtualReadOnly == false,
            onInsertSnippet = { snippet ->
              onInsertSnippet(snippet)
              onCloseDrawerIfCompact()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun SidebarSegmentChip(
  selected: Boolean,
  icon: ImageVector,
  label: String,
  tag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  Row(
    modifier =
      modifier
        .fillMaxHeight()
        .clip(RoundedCornerShape(9.dp))
        .background(if (selected) palette.activePillBg else Color.Transparent)
        .clickable(onClick = onClick)
        .testTag(tag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = if (selected) palette.activePillFg else palette.textSecondary,
      modifier = Modifier.size(15.dp),
    )
    Spacer(Modifier.width(4.dp))
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
      color = if (selected) palette.activePillFg else palette.textSecondary,
      maxLines = 1,
    )
  }
}

@Composable
private fun QuickFileActionButton(
  icon: ImageVector,
  label: String,
  tag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = palette.chromeSurface,
    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.35f)),
    modifier = modifier.testTag(tag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(icon, contentDescription = label, tint = palette.textPrimary, modifier = Modifier.size(15.dp))
      Spacer(Modifier.width(5.dp))
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = palette.textPrimary,
        maxLines = 1,
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectTreeRowItem(
  depth: Int,
  isExpanded: Boolean,
  isFolder: Boolean,
  isRootFolder: Boolean,
  isSelected: Boolean,
  canDeleteFile: Boolean,
  label: String,
  contextTitle: String,
  file: WorkerFile?,
  onClick: () -> Unit,
  onNewFileInFolder: () -> Unit,
  onNewDirInFolder: () -> Unit,
  onRenameClick: () -> Unit,
  onSetEntryClick: () -> Unit,
  onExportClick: () -> Unit,
  onDeleteFileClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val rowBg = if (isSelected) palette.selectedRowBg else Color.Transparent
  var showContextPopup by remember { mutableStateOf(false) }

  Box {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(40.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(rowBg)
          .combinedClickable(
            onClick = onClick,
            onLongClick = { showContextPopup = true },
          )
          .padding(end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (depth > 0) {
        Box(modifier = Modifier.width((depth * 14).dp).fillMaxHeight()) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            for (level in 0 until depth) {
              val x = ((level * 14) + 7).dp.toPx()
              drawLine(
                color = palette.treeLine,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx(),
              )
            }
          }
        }
      } else {
        Spacer(Modifier.width(6.dp))
      }

      if (isFolder) {
        Icon(
          imageVector =
            if (isExpanded) Icons.Outlined.KeyboardArrowDown
            else Icons.Outlined.KeyboardArrowRight,
          contentDescription = null,
          tint = palette.textSecondary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box {
          Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (isRootFolder) palette.textPrimary else FolderYellow,
            modifier = Modifier.size(18.dp),
          )
          if (isRootFolder) {
            Box(
              modifier =
                Modifier
                  .size(6.dp)
                  .align(Alignment.BottomEnd)
                  .clip(CircleShape)
                  .background(FolderGreen),
            )
          }
        }
      } else {
        Spacer(Modifier.width(8.dp))
        FileTypeBadge(path = file?.path ?: label)
      }

      Spacer(Modifier.width(8.dp))

      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isSelected || isRootFolder) FontWeight.SemiBold else FontWeight.Normal,
        color = palette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )

      if (file?.isEntryPoint == true) {
        Text(
          text = "MAIN",
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = palette.activePillFg,
          modifier = Modifier.padding(horizontal = 4.dp),
        )
      }

      if (file?.isDirty == true) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AmberWarn))
        Spacer(Modifier.width(4.dp))
      }

      IconButton(
        onClick = { showContextPopup = true },
        modifier = Modifier.size(30.dp).testTag("tree_context_${file?.path ?: label}"),
      ) {
        Icon(
          Icons.Outlined.MoreVert,
          contentDescription = "File options for $label",
          tint = palette.textMuted,
          modifier = Modifier.size(16.dp),
        )
      }
    }

    if (showContextPopup) {
      Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = { showContextPopup = false },
        properties = PopupProperties(focusable = true),
      ) {
        Surface(
          color = palette.chromePill,
          shape = RoundedCornerShape(16.dp),
          shadowElevation = 8.dp,
          border = BorderStroke(1.dp, palette.border.copy(alpha = 0.55f)),
          modifier = Modifier.widthIn(min = 200.dp, max = 250.dp),
        ) {
          Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
              text = contextTitle,
              style = MaterialTheme.typography.labelMedium,
              color = palette.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (isFolder) {
              ProjectPopupMenuRow(Icons.Outlined.Add, "New File") {
                showContextPopup = false
                onNewFileInFolder()
              }
              ProjectPopupMenuRow(Icons.Outlined.Folder, "New Folder") {
                showContextPopup = false
                onNewDirInFolder()
              }
            } else if (file != null) {
              ProjectPopupMenuRow(Icons.Outlined.Description, "Open in Editor") {
                showContextPopup = false
                onClick()
              }
              ProjectPopupMenuRow(Icons.Outlined.Add, "New Sibling File") {
                showContextPopup = false
                onNewFileInFolder()
              }
              if (!file.isVirtualReadOnly && !file.isEntryPoint) {
                ProjectPopupMenuRow(Icons.Outlined.StarOutline, "Set Entry Point") {
                  showContextPopup = false
                  onSetEntryClick()
                }
              }
              if (!file.isVirtualReadOnly) {
                ProjectPopupMenuRow(Icons.Outlined.DriveFileRenameOutline, "Rename / Move") {
                  showContextPopup = false
                  onRenameClick()
                }
                ProjectPopupMenuRow(Icons.Outlined.Download, "Export File") {
                  showContextPopup = false
                  onExportClick()
                }
              }
              if (canDeleteFile) {
                ProjectPopupMenuRow(
                  icon = Icons.Outlined.DeleteOutline,
                  label = "Delete File",
                  tint = MaterialTheme.colorScheme.error,
                ) {
                  showContextPopup = false
                  onDeleteFileClick()
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
private fun ProjectPopupMenuRow(
  icon: ImageVector,
  label: String,
  tint: Color? = null,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tint ?: palette.textSecondary, modifier = Modifier.size(18.dp))
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint ?: palette.textPrimary)
  }
}

private fun buildProjectTreeEntries(
  rootName: String,
  files: List<WorkerFile>,
  extraDirectories: Set<String>,
  expandedMap: Map<String, Boolean>,
): List<ProjectTreeEntry> {
  val result = mutableListOf<ProjectTreeEntry>()
  val rootId = "folder_root_$rootName"
  result.add(ProjectTreeEntry.FolderNode(id = rootId, depth = 0, label = rootName, folderPath = "", isRoot = true))
  if (expandedMap[rootId] == false) return result

  val emittedFolders = mutableSetOf<String>()
  val sortedFiles = files.sortedWith(compareBy<WorkerFile> { !it.path.contains('/') }.thenBy { it.path })

  for (file in sortedFiles) {
    val parts = file.path.split('/')
    var parentCollapsed = false
    for (i in 1 until parts.size) {
      val folderPath = parts.subList(0, i).joinToString("/")
      val folderId = "folder_$folderPath"
      if (!parentCollapsed && emittedFolders.add(folderPath)) {
        result.add(
          ProjectTreeEntry.FolderNode(
            id = folderId,
            depth = i,
            label = parts[i - 1],
            folderPath = folderPath,
          ),
        )
      }
      if (expandedMap[folderId] == false) parentCollapsed = true
    }
    if (!parentCollapsed) {
      result.add(
        ProjectTreeEntry.FileNode(
          id = "file_${file.path}",
          depth = parts.size,
          label = parts.last(),
          file = file,
        ),
      )
    }
  }

  for (dir in extraDirectories.sorted()) {
    val parts = dir.split('/')
    var parentCollapsed = false
    for (i in 1..parts.size) {
      val folderPath = parts.subList(0, i).joinToString("/")
      val folderId = "folder_$folderPath"
      if (!parentCollapsed && emittedFolders.add(folderPath)) {
        result.add(
          ProjectTreeEntry.FolderNode(
            id = folderId,
            depth = i,
            label = parts[i - 1],
            folderPath = folderPath,
          ),
        )
      }
      if (expandedMap[folderId] == false) parentCollapsed = true
    }
  }
  return result
}
