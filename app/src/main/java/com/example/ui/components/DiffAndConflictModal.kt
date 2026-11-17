package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.DiffType
import com.example.editor.CodeFormatter
import com.example.editor.ConflictChoice
import com.example.editor.ConflictHunk
import com.example.editor.DiffCellKind
import com.example.editor.DiffFileOrigin
import com.example.editor.DiffViewMode
import com.example.editor.FileDiffEntry
import com.example.editor.MergeConflictFile
import com.example.editor.MergeConflictResolver
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.rememberGlassBorderStroke

enum class DiffReviewIntent(
  val title: String,
  val subtitle: String,
  val primaryActionLabel: String?,
) {
  WORKSPACE(
    title = "Workspace Diff Inspector",
    subtitle = "Compare local edits against deployed Worker baseline",
    primaryActionLabel = null,
  ),
  PRE_DEPLOY(
    title = "Pre-Deploy Diff Review",
    subtitle = "Review changes before publishing live to Cloudflare",
    primaryActionLabel = "Deploy to Cloudflare",
  ),
  PRE_GIT_PUSH(
    title = "Pre-Push Diff Review",
    subtitle = "Review staged & working changes before pushing to GitHub",
    primaryActionLabel = "Proceed to Push",
  ),
}

@Composable
fun DiffReviewModal(
  intent: DiffReviewIntent,
  entries: List<FileDiffEntry>,
  onDismiss: () -> Unit,
  onRevertFile: (String) -> Unit,
  onPrimaryProceed: (() -> Unit)? = null,
  onOpenFileInEditor: (String) -> Unit,
) {
  val palette = LocalIdePalette.current
  var selectedPath by remember(entries) { mutableStateOf(entries.firstOrNull()?.path) }
  val activeEntry = entries.firstOrNull { it.path == selectedPath } ?: entries.firstOrNull()
  var viewMode by remember { mutableStateOf(DiffViewMode.UNIFIED) }
  var showUnchangedContext by remember { mutableStateOf(true) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = palette.chromeBg,
      shape = RoundedCornerShape(22.dp),
      border = rememberGlassBorderStroke(1.dp),
      modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 18.dp).testTag("diff_review_modal"),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            color = palette.activePillBg,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(36.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.Difference,
                contentDescription = null,
                tint = palette.activePillFg,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = intent.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${entries.size} changed file(s) • ${intent.subtitle}",
              style = MaterialTheme.typography.labelSmall,
              color = palette.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "Close diff review", tint = palette.textSecondary)
          }
        }

        HorizontalDivider(color = palette.border.copy(alpha = 0.45f))

        if (entries.isEmpty()) {
          Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(24.dp),
            ) {
              Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(40.dp))
              Text("Working Tree Clean", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
              Text(
                text = "No modified, added, or deleted files compared to the baseline.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
              )
            }
          }
        } else {
          // File selector chips
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .background(palette.chromeSurface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            entries.forEach { entry ->
              val isSelected = entry.path == activeEntry?.path
              val originColor =
                when (entry.origin) {
                  DiffFileOrigin.ADDED -> EmeraldLive
                  DiffFileOrigin.DELETED -> RoseError
                  DiffFileOrigin.CONFLICT -> AmberWarn
                  DiffFileOrigin.MODIFIED -> palette.activePillFg
                }
              Surface(
                onClick = { selectedPath = entry.path },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) palette.activePillBg else palette.chromePill,
                border =
                  if (isSelected) BorderStroke(1.dp, palette.activePillFg.copy(alpha = 0.4f))
                  else BorderStroke(1.dp, palette.border.copy(alpha = 0.35f)),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = entry.path.substringAfterLast('/'),
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) palette.activePillFg else palette.textPrimary,
                  )
                  Text(
                    text = "+${entry.addedCount} -${entry.removedCount}",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp),
                    color = originColor,
                  )
                }
              }
            }
          }

          if (activeEntry != null) {
            // Controls bar: Unified vs Side-by-Side + Context toggle + File path & Revert
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1.2f)) {
                  DiffViewMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                      selected = viewMode == mode,
                      onClick = { viewMode = mode },
                      shape = SegmentedButtonDefaults.itemShape(idx, DiffViewMode.entries.size),
                      modifier = Modifier.testTag("diff_mode_${mode.name.lowercase()}"),
                    ) {
                      Text(mode.label, fontSize = 11.sp, maxLines = 1)
                    }
                  }
                }

                Surface(
                  onClick = { showUnchangedContext = !showUnchangedContext },
                  shape = RoundedCornerShape(10.dp),
                  color = if (showUnchangedContext) palette.chromePill else palette.activePillBg,
                ) {
                  Text(
                    text = if (showUnchangedContext) "Full File" else "Changes Only",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (showUnchangedContext) palette.textSecondary else palette.activePillFg,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "${activeEntry.path} (${activeEntry.originalLabel} -> ${activeEntry.modifiedLabel})",
                  style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
                  color = palette.textSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                if (activeEntry.origin != DiffFileOrigin.ADDED) {
                  Surface(
                    onClick = { onRevertFile(activeEntry.path) },
                    shape = RoundedCornerShape(8.dp),
                    color = RoseError.copy(alpha = 0.14f),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, tint = RoseError, modifier = Modifier.size(13.dp))
                      Text("Revert", style = MaterialTheme.typography.labelSmall, color = RoseError, fontWeight = FontWeight.SemiBold)
                    }
                  }
                }
                Surface(
                  onClick = {
                    onOpenFileInEditor(activeEntry.path)
                    onDismiss()
                  },
                  shape = RoundedCornerShape(8.dp),
                  color = palette.chromePill,
                ) {
                  Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.35f))

            // Diff surface
            val diffLines =
              remember(activeEntry.originalContent, activeEntry.modifiedContent, showUnchangedContext) {
                val all = CodeFormatter.computeLineDiff(activeEntry.originalContent, activeEntry.modifiedContent)
                if (showUnchangedContext) all else all.filter { it.type != DiffType.UNCHANGED }
              }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(palette.editorBg)) {
              if (viewMode == DiffViewMode.UNIFIED) {
                UnifiedDiffList(diffLines = diffLines)
              } else {
                val sideRows = remember(diffLines) { MergeConflictResolver.buildSideBySideRows(diffLines) }
                SideBySideDiffTable(
                  rows = sideRows,
                  leftHeader = activeEntry.originalLabel,
                  rightHeader = activeEntry.modifiedLabel,
                )
              }
            }
          }
        }

        // Bottom CTA footer
        HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
        Row(
          modifier = Modifier.fillMaxWidth().background(palette.chromeSurface).padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text(if (intent.primaryActionLabel != null) "Cancel" else "Close")
          }
          val actionLabel = intent.primaryActionLabel
          if (actionLabel != null && onPrimaryProceed != null) {
            FilledTonalButton(
              onClick = {
                onDismiss()
                onPrimaryProceed()
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1.5f).testTag("diff_review_proceed_button"),
            ) {
              Icon(
                imageVector = if (intent == DiffReviewIntent.PRE_DEPLOY) Icons.Outlined.RocketLaunch else Icons.Outlined.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun UnifiedDiffList(diffLines: List<com.example.data.remote.DiffLine>) {
  val palette = LocalIdePalette.current
  val horizScroll = rememberScrollState()
  if (diffLines.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("No line differences.", style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
    }
    return
  }
  LazyColumn(modifier = Modifier.fillMaxSize().horizontalScroll(horizScroll).padding(vertical = 6.dp)) {
    items(diffLines) { line ->
      val bg =
        when (line.type) {
          DiffType.ADDED -> EmeraldLive.copy(alpha = 0.16f)
          DiffType.REMOVED -> RoseError.copy(alpha = 0.16f)
          DiffType.UNCHANGED -> Color.Transparent
        }
      val gutterColor =
        when (line.type) {
          DiffType.ADDED -> EmeraldLive
          DiffType.REMOVED -> RoseError
          DiffType.UNCHANGED -> palette.textMuted
        }
      val sign =
        when (line.type) {
          DiffType.ADDED -> "+"
          DiffType.REMOVED -> "-"
          DiffType.UNCHANGED -> " "
        }
      val oldNum = line.lineNumberOld?.toString()?.padStart(3) ?: "   "
      val newNum = line.lineNumberNew?.toString()?.padStart(3) ?: "   "
      Row(
        modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "$oldNum $newNum $sign ",
          style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
          color = gutterColor,
        )
        Text(
          text = line.text,
          style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
          color = palette.textPrimary,
          softWrap = false,
        )
      }
    }
  }
}

@Composable
private fun SideBySideDiffTable(
  rows: List<com.example.editor.SideBySideDiffRow>,
  leftHeader: String,
  rightHeader: String,
) {
  val palette = LocalIdePalette.current
  val horizScroll = rememberScrollState()
  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().background(palette.chromeSurface).padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = leftHeader,
        style = MaterialTheme.typography.labelSmall,
        color = RoseError,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = rightHeader,
        style = MaterialTheme.typography.labelSmall,
        color = EmeraldLive,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f),
      )
    }
    HorizontalDivider(color = palette.border.copy(alpha = 0.35f))

    if (rows.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No line differences.", style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
      }
      return
    }

    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().horizontalScroll(horizScroll).padding(vertical = 4.dp)) {
      items(rows) { row ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
          SideBySideCell(
            lineNumber = row.leftLineNumber,
            text = row.leftText,
            kind = row.leftKind,
            modifier = Modifier.width(260.dp),
          )
          VerticalDivider(color = palette.border.copy(alpha = 0.35f), modifier = Modifier.height(20.dp))
          SideBySideCell(
            lineNumber = row.rightLineNumber,
            text = row.rightText,
            kind = row.rightKind,
            modifier = Modifier.width(260.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun SideBySideCell(
  lineNumber: Int?,
  text: String,
  kind: DiffCellKind,
  modifier: Modifier = Modifier,
) {
  val palette = LocalIdePalette.current
  val bg =
    when (kind) {
      DiffCellKind.REMOVED -> RoseError.copy(alpha = 0.16f)
      DiffCellKind.ADDED -> EmeraldLive.copy(alpha = 0.16f)
      DiffCellKind.EMPTY_PAD -> palette.chromeSurface.copy(alpha = 0.35f)
      DiffCellKind.UNCHANGED -> Color.Transparent
    }
  val numColor =
    when (kind) {
      DiffCellKind.REMOVED -> RoseError
      DiffCellKind.ADDED -> EmeraldLive
      else -> palette.textMuted
    }
  val prefix =
    when (kind) {
      DiffCellKind.REMOVED -> "-"
      DiffCellKind.ADDED -> "+"
      else -> " "
    }
  Row(
    modifier = modifier.background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "${lineNumber?.toString()?.padStart(3) ?: "   "}$prefix",
      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.5.sp),
      color = numColor,
    )
    Spacer(Modifier.width(4.dp))
    Text(
      text = text,
      style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
      color = palette.textPrimary,
      softWrap = false,
    )
  }
}

@Composable
fun ConflictResolverModal(
  conflictFiles: List<MergeConflictFile>,
  onResolveHunk: (filePath: String, hunkIndex: Int, choice: ConflictChoice) -> Unit,
  onResolveAllInFile: (filePath: String, choice: ConflictChoice) -> Unit,
  onApplyResolvedToWorkspace: () -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  var selectedPath by remember(conflictFiles) { mutableStateOf(conflictFiles.firstOrNull()?.path) }
  val activeFile = conflictFiles.firstOrNull { it.path == selectedPath } ?: conflictFiles.firstOrNull()
  val totalRemaining = conflictFiles.sumOf { it.hunks.size }
  var showFullMergedPreview by remember { mutableStateOf(false) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = palette.chromeBg,
      shape = RoundedCornerShape(22.dp),
      border = rememberGlassBorderStroke(1.dp),
      modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 18.dp).testTag("conflict_resolver_modal"),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            color = if (totalRemaining > 0) AmberWarn.copy(alpha = 0.2f) else EmeraldLive.copy(alpha = 0.2f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(36.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (totalRemaining > 0) Icons.Outlined.MergeType else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (totalRemaining > 0) AmberWarn else EmeraldLive,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "3-Way Merge Conflict Resolver",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary,
            )
            Text(
              text =
                if (totalRemaining > 0) "$totalRemaining unresolved conflict(s) across ${conflictFiles.size} file(s)"
                else "All conflicts resolved — ready to apply cleanly",
              style = MaterialTheme.typography.labelSmall,
              color = if (totalRemaining > 0) AmberWarn else EmeraldLive,
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "Close conflict resolver", tint = palette.textSecondary)
          }
        }

        HorizontalDivider(color = palette.border.copy(alpha = 0.45f))

        // File tabs
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .background(palette.chromeSurface)
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          conflictFiles.forEach { file ->
            val selected = file.path == activeFile?.path
            val count = file.hunks.size
            Surface(
              onClick = { selectedPath = file.path },
              shape = RoundedCornerShape(10.dp),
              color = if (selected) palette.activePillBg else palette.chromePill,
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = file.path.substringAfterLast('/'),
                  style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                  color = if (selected) palette.activePillFg else palette.textPrimary,
                )
                Surface(
                  color = if (count > 0) AmberWarn.copy(alpha = 0.2f) else EmeraldLive.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(999.dp),
                ) {
                  Text(
                    text = if (count > 0) "$count" else "OK",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (count > 0) AmberWarn else EmeraldLive,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                  )
                }
              }
            }
          }
        }

        if (activeFile != null) {
          // Per-file bulk actions
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = activeFile.path,
              style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
              color = palette.textSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Surface(
              onClick = { showFullMergedPreview = !showFullMergedPreview },
              shape = RoundedCornerShape(8.dp),
              color = if (showFullMergedPreview) palette.activePillBg else palette.chromePill,
            ) {
              Text(
                text = if (showFullMergedPreview) "Hunks View" else "Preview File",
                style = MaterialTheme.typography.labelSmall,
                color = if (showFullMergedPreview) palette.activePillFg else palette.textPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
              )
            }
          }

          if (activeFile.hunks.isNotEmpty() && !showFullMergedPreview) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              ConflictChoice.entries.forEach { choice ->
                OutlinedButton(
                  onClick = { onResolveAllInFile(activeFile.path, choice) },
                  shape = RoundedCornerShape(9.dp),
                  modifier = Modifier.weight(1f).height(34.dp),
                ) {
                  Text(choice.label, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
            }
          }

          HorizontalDivider(color = palette.border.copy(alpha = 0.35f))

          Box(modifier = Modifier.weight(1f).fillMaxWidth().background(palette.editorBg)) {
            if (showFullMergedPreview || activeFile.hunks.isEmpty()) {
              Column(
                modifier =
                  Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (activeFile.hunks.isEmpty()) {
                  Surface(color = EmeraldLive.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(16.dp))
                      Text("No remaining conflicts in ${activeFile.path}", style = MaterialTheme.typography.labelSmall, color = EmeraldLive)
                    }
                  }
                }
                Text(
                  text = activeFile.mergedContent,
                  style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.5.sp),
                  color = palette.textPrimary,
                )
              }
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                items(activeFile.hunks, key = { "${activeFile.path}:${it.index}:${it.startLine}" }) { hunk ->
                  ConflictHunkCard(
                    hunk = hunk,
                    onChoose = { choice -> onResolveHunk(activeFile.path, hunk.index, choice) },
                  )
                }
              }
            }
          }
        }

        HorizontalDivider(color = palette.border.copy(alpha = 0.45f))
        Row(
          modifier = Modifier.fillMaxWidth().background(palette.chromeSurface).padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text("Keep Markers in Editor")
          }
          FilledTonalButton(
            onClick = onApplyResolvedToWorkspace,
            enabled = totalRemaining == 0,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1.3f).testTag("apply_resolved_merge_button"),
          ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              text = if (totalRemaining == 0) "Apply Merge" else "Resolve $totalRemaining Left",
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ConflictHunkCard(
  hunk: ConflictHunk,
  onChoose: (ConflictChoice) -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    color = palette.chromeSurface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, AmberWarn.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = AmberWarn, modifier = Modifier.size(16.dp))
        Text(
          text = "Conflict #${hunk.index + 1} (Lines ${hunk.startLine}–${hunk.endLine})",
          style = MaterialTheme.typography.labelMedium,
          color = AmberWarn,
          fontWeight = FontWeight.Bold,
        )
      }

      // Local block
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(EmeraldLive.copy(alpha = 0.12f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = "LOCAL (${hunk.localLabel})",
          style = MaterialTheme.typography.labelSmall,
          color = EmeraldLive,
          fontWeight = FontWeight.Bold,
        )
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
          Text(
            text = hunk.localLines.joinToString("\n").ifEmpty { "(empty)" },
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
            color = palette.textPrimary,
          )
        }
      }

      // Remote block
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RoseError.copy(alpha = 0.12f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = "REMOTE (${hunk.remoteLabel})",
          style = MaterialTheme.typography.labelSmall,
          color = RoseError,
          fontWeight = FontWeight.Bold,
        )
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
          Text(
            text = hunk.remoteLines.joinToString("\n").ifEmpty { "(empty)" },
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp),
            color = palette.textPrimary,
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
          onClick = { onChoose(ConflictChoice.KEEP_LOCAL) },
          shape = RoundedCornerShape(9.dp),
          modifier = Modifier.weight(1f).height(36.dp).testTag("conflict_keep_local_${hunk.index}"),
        ) {
          Text("Keep Local", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(
          onClick = { onChoose(ConflictChoice.USE_REMOTE) },
          shape = RoundedCornerShape(9.dp),
          modifier = Modifier.weight(1f).height(36.dp).testTag("conflict_use_remote_${hunk.index}"),
        ) {
          Text("Use Remote", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
          onClick = { onChoose(ConflictChoice.ACCEPT_BOTH) },
          shape = RoundedCornerShape(9.dp),
          modifier = Modifier.weight(1f).height(36.dp).testTag("conflict_accept_both_${hunk.index}"),
        ) {
          Text("Accept Both", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
