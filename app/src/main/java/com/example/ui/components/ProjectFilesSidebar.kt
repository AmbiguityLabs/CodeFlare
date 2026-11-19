package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SnippetFolder
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import com.example.data.remote.WorkerFile
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.FolderGreen
import com.example.ui.theme.FolderYellow
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.UiStyleVersion
import com.example.ui.theme.edgeGlassSurface
import com.example.ui.theme.rememberGlassBorderStroke
import com.example.ui.viewmodel.IdeUiState

private enum class TreeSortMode { NAME, TYPE }

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
  onImportZipIntoProject: () -> Unit = {},
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
  var treeOverflowMenuOpen by remember { mutableStateOf(false) }
  var sortMode by remember { mutableStateOf(TreeSortMode.NAME) }
  val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
  var folderTreeVersion by remember { mutableIntStateOf(0) }

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
    // Top Segmented Pill Switcher + Collapse Button
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
      Surface(
        color = palette.chromeSurface,
        shape = RoundedCornerShape(if (isV2) 20.dp else 10.dp),
        border = if (isV2) null else rememberGlassBorderStroke(0.75.dp),
        modifier = Modifier.weight(1f).height(38.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(3.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconSegmentTab(
            selected = activeSubTab == 0,
            icon = Icons.Outlined.Description,
            label = "Files",
            tag = "rail_explorer",
            onClick = { activeSubTab = 0 },
            modifier = Modifier.weight(1f),
          )
          IconSegmentTab(
            selected = activeSubTab == 1,
            icon = Icons.Outlined.Search,
            label = "Search",
            tag = "rail_search",
            onClick = { activeSubTab = 1 },
            modifier = Modifier.weight(1f),
          )
          IconSegmentTab(
            selected = activeSubTab == 2,
            icon = Icons.Outlined.SnippetFolder,
            label = "Snippets",
            tag = "rail_snippets",
            onClick = { activeSubTab = 2 },
            modifier = Modifier.weight(1f),
          )
        }
      }

      if (showCloseButton) {
        IconButton(
          onClick = onCloseDrawerIfCompact,
          modifier = Modifier.size(38.dp).testTag("collapse_sidebar_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Remove,
            contentDescription = "Close files drawer",
            tint = palette.textSecondary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }

    AnimatedContent(
      targetState = activeSubTab,
      transitionSpec = {
        fadeIn(tween(150)) togetherWith fadeOut(tween(120))
      },
      modifier = Modifier.weight(1f).fillMaxWidth(),
      label = "sidebar_tab_anim",
    ) { subTab ->
      when (subTab) {
      0 -> Column(modifier = Modifier.fillMaxSize()) {
        val ws = uiState.workspace
        val treeRows =
          remember(ws?.scriptName, ws?.files, ws?.directories, folderTreeVersion, sortMode) {
            ws?.let {
              buildProjectTreeEntries(it.scriptName, it.files, it.directories, expandedFolders, sortMode)
            }.orEmpty()
          }
        val editableFileCount = remember(ws?.files) { ws?.files?.count { !it.isVirtualReadOnly } ?: 0 }

        // Project Pill + Compact Icon Actions Row
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Surface(
            color = palette.chromeSurface,
            shape = RoundedCornerShape(10.dp),
            border = rememberGlassBorderStroke(0.75.dp),
            modifier = Modifier.weight(1f, fill = false),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = ws?.scriptName ?: "Project",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp),
              )
            }
          }

          Spacer(Modifier.weight(1f))

          IconButton(
            onClick = { onShowNewFileDialog("") },
            modifier = Modifier.size(34.dp).testTag("add_module_file_button"),
          ) {
            Icon(Icons.Outlined.NoteAdd, contentDescription = "New File", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
          }

          IconButton(
            onClick = { onShowNewDirectoryDialog("") },
            modifier = Modifier.size(34.dp).testTag("add_module_dir_button"),
          ) {
            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New Folder", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
          }

          Box {
            IconButton(
              onClick = { uploadMenuOpen = true },
              modifier = Modifier.size(34.dp).testTag("upload_files_menu_button"),
            ) {
              Icon(Icons.Outlined.UploadFile, contentDescription = "Upload or Export", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            }
            EdgeMenu(
              expanded = uploadMenuOpen,
              onDismissRequest = { uploadMenuOpen = false },
              minWidth = 216.dp,
              maxWidth = 264.dp,
            ) {
              EdgeMenuHeader(
                title = "Transfer Files",
                leadingIcon = Icons.Outlined.UploadFile,
              )
              EdgeMenuItem(
                label = "Upload Single File",
                icon = Icons.Outlined.UploadFile,
                onClick = {
                  uploadMenuOpen = false
                  onUploadSingleFile()
                },
              )
              EdgeMenuItem(
                label = "Batch Upload Files",
                icon = Icons.Outlined.SnippetFolder,
                badgeText = "MULTI",
                onClick = {
                  uploadMenuOpen = false
                  onUploadBatchFiles()
                },
              )
              EdgeMenuItem(
                label = "Import ZIP Archive",
                icon = Icons.Outlined.Archive,
                badgeText = ".zip",
                onClick = {
                  uploadMenuOpen = false
                  onImportZipIntoProject()
                },
              )
              uiState.activeFile?.let { active ->
                EdgeMenuDivider()
                EdgeMenuItem(
                  label = "Export ${active.fileName}",
                  icon = Icons.Outlined.Download,
                  onClick = {
                    uploadMenuOpen = false
                    onExportActiveFile()
                  },
                )
              }
            }
          }

          Box {
            IconButton(
              onClick = { treeOverflowMenuOpen = true },
              modifier = Modifier.size(34.dp).testTag("explorer_overflow_button"),
            ) {
              Icon(Icons.Outlined.MoreHoriz, contentDescription = "Explorer menu", tint = palette.textSecondary, modifier = Modifier.size(19.dp))
            }

            EdgeFloatingMenuPopup(
              expanded = treeOverflowMenuOpen,
              onDismissRequest = { treeOverflowMenuOpen = false },
              alignment = Alignment.TopEnd,
              minWidth = 208.dp,
              maxWidth = 248.dp,
            ) {
              EdgeMenuHeader(
                title = "Explorer",
                leadingIcon = Icons.Outlined.Folder,
              )
              EdgeMenuItem(
                label = "New File",
                icon = Icons.Outlined.Add,
                onClick = {
                  treeOverflowMenuOpen = false
                  onShowNewFileDialog("")
                },
              )
              EdgeMenuItem(
                label = "New Folder",
                icon = Icons.Outlined.CreateNewFolder,
                onClick = {
                  treeOverflowMenuOpen = false
                  onShowNewDirectoryDialog("")
                },
              )
              EdgeMenuItem(
                label = "Expand All",
                icon = Icons.Outlined.UnfoldMore,
                onClick = {
                  treeOverflowMenuOpen = false
                  expandedFolders.keys.toList().forEach { k -> expandedFolders[k] = true }
                  folderTreeVersion++
                },
              )
              EdgeMenuItem(
                label = "Collapse All",
                icon = Icons.Outlined.UnfoldLess,
                onClick = {
                  treeOverflowMenuOpen = false
                  ws?.files.orEmpty().forEach { f ->
                    val parts = f.path.split('/')
                    for (i in 1 until parts.size) {
                      expandedFolders["folder_${parts.subList(0, i).joinToString("/")}"] = false
                    }
                  }
                  ws?.directories.orEmpty().forEach { d ->
                    expandedFolders["folder_$d"] = false
                  }
                  folderTreeVersion++
                },
              )
              EdgeMenuDivider()
              EdgeMenuSection("Sort By")
              EdgeMenuCompactItem(
                label = "Name",
                selected = sortMode == TreeSortMode.NAME,
                onClick = {
                  sortMode = TreeSortMode.NAME
                  treeOverflowMenuOpen = false
                },
              )
              EdgeMenuCompactItem(
                label = "Type",
                selected = sortMode == TreeSortMode.TYPE,
                onClick = {
                  sortMode = TreeSortMode.TYPE
                  treeOverflowMenuOpen = false
                },
              )
            }
          }
        }

        HorizontalDivider(color = palette.border.copy(alpha = 0.35f))

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
                    onClick = {
                      expandedFolders[entry.id] = !isExpanded
                      folderTreeVersion++
                    },
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
            IconToggleRevealText(
              icon = Icons.Outlined.Description,
              text = "${uiState.ramDbStats.dirtyFilesCount} edited",
              tint = AmberWarn,
            )
            IconRevealButton(
              icon = Icons.Outlined.Restore,
              revealedLabel = "Revert",
              onClick = onRevertWorkspaceToImported,
              height = 32.dp,
            )
          }
        }
      }

      1 -> {
        Box(modifier = Modifier.fillMaxSize()) {
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
        Box(modifier = Modifier.fillMaxSize()) {
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
  val rowBg by animateColorAsState(
    targetValue = if (isSelected) palette.selectedRowBg else Color.Transparent,
    animationSpec = tween(140),
    label = "tree_row_bg",
  )
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

    EdgeFloatingMenuPopup(
      expanded = showContextPopup,
      onDismissRequest = { showContextPopup = false },
      alignment = Alignment.TopCenter,
      minWidth = 204.dp,
      maxWidth = 252.dp,
    ) {
      EdgeMenuHeader(
        title = contextTitle,
        leadingIcon = if (isFolder) Icons.Outlined.Folder else Icons.Outlined.Description,
      )
      if (isFolder) {
        EdgeMenuItem(
          label = "New File",
          icon = Icons.Outlined.Add,
          onClick = {
            showContextPopup = false
            onNewFileInFolder()
          },
        )
        EdgeMenuItem(
          label = "New Folder",
          icon = Icons.Outlined.CreateNewFolder,
          onClick = {
            showContextPopup = false
            onNewDirInFolder()
          },
        )
      } else if (file != null) {
        EdgeMenuItem(
          label = "Open in Editor",
          icon = Icons.Outlined.Description,
          onClick = {
            showContextPopup = false
            onClick()
          },
        )
        EdgeMenuItem(
          label = "New Sibling File",
          icon = Icons.Outlined.Add,
          onClick = {
            showContextPopup = false
            onNewFileInFolder()
          },
        )
        if (!file.isVirtualReadOnly && !file.isEntryPoint) {
          EdgeMenuItem(
            label = "Set Entry Point",
            icon = Icons.Outlined.StarOutline,
            tone = EdgeMenuTone.ACCENT,
            badgeText = "main",
            onClick = {
              showContextPopup = false
              onSetEntryClick()
            },
          )
        }
        if (!file.isVirtualReadOnly) {
          EdgeMenuItem(
            label = "Rename / Move",
            icon = Icons.Outlined.DriveFileRenameOutline,
            onClick = {
              showContextPopup = false
              onRenameClick()
            },
          )
          EdgeMenuItem(
            label = "Export File",
            icon = Icons.Outlined.Download,
            onClick = {
              showContextPopup = false
              onExportClick()
            },
          )
        }
        if (canDeleteFile) {
          EdgeMenuDivider()
          EdgeMenuItem(
            label = "Delete File",
            icon = Icons.Outlined.DeleteOutline,
            tone = EdgeMenuTone.DANGER,
            onClick = {
              showContextPopup = false
              onDeleteFileClick()
            },
          )
        }
      }
    }
  }
}

private fun buildProjectTreeEntries(
  rootName: String,
  files: List<WorkerFile>,
  extraDirectories: Set<String>,
  expandedMap: Map<String, Boolean>,
  sortMode: TreeSortMode = TreeSortMode.NAME,
): List<ProjectTreeEntry> {
  val result = mutableListOf<ProjectTreeEntry>()
  val rootId = "folder_root_$rootName"
  result.add(ProjectTreeEntry.FolderNode(id = rootId, depth = 0, label = rootName, folderPath = "", isRoot = true))
  if (expandedMap[rootId] == false) return result

  val emittedFolders = mutableSetOf<String>()
  val sortedFiles =
    when (sortMode) {
      TreeSortMode.NAME -> files.sortedWith(compareBy<WorkerFile> { !it.path.contains('/') }.thenBy { it.path.lowercase() })
      TreeSortMode.TYPE ->
        files.sortedWith(
          compareBy<WorkerFile> { !it.path.contains('/') }
            .thenBy { it.path.substringAfterLast('.', "").lowercase() }
            .thenBy { it.path.lowercase() },
        )
    }

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
