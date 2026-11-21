package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.idecore.snippets.CodeSnippetItem
import com.example.idecore.snippets.CodeSnippetRegistry
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette

@Composable
fun SnippetsLibraryDialog(
  customSnippets: List<CodeSnippetItem>,
  hasEditableActiveFile: Boolean,
  activeFileName: String?,
  onInsertSnippet: (CodeSnippetItem) -> Unit,
  onAddCustomSnippet: (abbrev: String, title: String, tag: String, code: String, description: String) -> Unit,
  onDeleteCustomSnippet: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalIdePalette.current
  var query by remember { mutableStateOf("") }
  var selectedTag by remember { mutableStateOf("ALL") }
  var showCreateForm by remember { mutableStateOf(false) }

  var newAbbrev by remember { mutableStateOf("") }
  var newTitle by remember { mutableStateOf("") }
  var newTag by remember { mutableStateOf("CUSTOM") }
  var newDesc by remember { mutableStateOf("") }
  var newCode by remember { mutableStateOf("export async function handler() {\n  \$END\$\n}") }

  val allItems =
    remember(customSnippets) {
      CodeSnippetRegistry.allSnippets(customSnippets)
    }
  val availableTags =
    remember(allItems) {
      val dynamic = allItems.map { it.tag.uppercase() }.distinct()
      listOf("ALL") + (if (customSnippets.isNotEmpty()) listOf("CUSTOM") else emptyList()) + dynamic.filterNot { it == "CUSTOM" }
    }
  val filtered =
    remember(allItems, query, selectedTag, customSnippets) {
      val base =
        if (selectedTag == "CUSTOM") {
          allItems.filter { it.isCustom }
        } else {
          allItems
        }
      CodeSnippetRegistry.filterSnippets(
        items = base,
        query = query,
        tagFilter = if (selectedTag == "CUSTOM") null else selectedTag,
      )
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = palette.chromeSurface,
      border = BorderStroke(1.dp, palette.border.copy(alpha = 0.7f)),
      shadowElevation = 12.dp,
      modifier =
        Modifier
          .fillMaxWidth(0.94f)
          .heightIn(min = 360.dp, max = 660.dp)
          .testTag("snippets_library_dialog"),
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Code, contentDescription = null, tint = palette.activePillFg, modifier = Modifier.size(18.dp))
            Column {
              Text(
                text = "Code Snippets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
              )
              Text(
                text = if (hasEditableActiveFile && !activeFileName.isNullOrBlank()) "Insert into $activeFileName · or type prefix in editor" else "Type snippet prefix in any editable file",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted,
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
              onClick = { showCreateForm = !showCreateForm },
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.45f)),
              modifier = Modifier.height(34.dp).testTag("snippets_new_button"),
            ) {
              Icon(
                imageVector = if (showCreateForm) Icons.Outlined.Close else Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = palette.activePillFg,
              )
              Spacer(Modifier.width(4.dp))
              Text(if (showCreateForm) "Cancel" else "New", fontSize = 11.5.sp, color = palette.activePillFg)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Outlined.Close, contentDescription = "Close", tint = palette.textMuted, modifier = Modifier.size(18.dp))
            }
          }
        }

        AnimatedVisibility(visible = showCreateForm) {
          Surface(
            color = palette.editorBg,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Create Custom Snippet", style = MaterialTheme.typography.labelLarge, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = newAbbrev,
                  onValueChange = { newAbbrev = it },
                  label = { Text("Trigger (e.g. api)", fontSize = 11.sp) },
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("snippet_create_abbrev"),
                )
                OutlinedTextField(
                  value = newTag,
                  onValueChange = { newTag = it.uppercase().take(10) },
                  label = { Text("Tag", fontSize = 11.sp) },
                  singleLine = true,
                  modifier = Modifier.width(100.dp),
                )
              }
              OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text("Title", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("snippet_create_title"),
              )
              OutlinedTextField(
                value = newDesc,
                onValueChange = { newDesc = it },
                label = { Text("Description (optional)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
              OutlinedTextField(
                value = newCode,
                onValueChange = { newCode = it },
                label = { Text("Template Code (use \$END\$ for cursor)", fontSize = 11.sp) },
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                modifier = Modifier.fillMaxWidth().testTag("snippet_create_code"),
              )
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                  onClick = {
                    val ab = newAbbrev.trim()
                    if (ab.isNotEmpty() && newCode.isNotBlank()) {
                      onAddCustomSnippet(ab, newTitle.ifBlank { ab }, newTag.ifBlank { "CUSTOM" }, newCode, newDesc)
                      newAbbrev = ""
                      newTitle = ""
                      newDesc = ""
                      showCreateForm = false
                    }
                  },
                  enabled = newAbbrev.isNotBlank() && newCode.isNotBlank(),
                  colors = ButtonDefaults.buttonColors(containerColor = palette.ctaBg, contentColor = palette.ctaFg),
                  modifier = Modifier.testTag("snippet_save_button"),
                ) {
                  Text("Save Snippet", fontSize = 12.sp)
                }
              }
            }
          }
        }

        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search prefix, title, tag…", fontSize = 12.sp) },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("snippets_search_field"),
        )

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          availableTags.forEach { tag ->
            val selected = selectedTag == tag
            Surface(
              onClick = { selectedTag = tag },
              shape = RoundedCornerShape(8.dp),
              color = if (selected) palette.activePillBg else palette.chromePill,
              border = BorderStroke(0.75.dp, if (selected) palette.activePillFg.copy(alpha = 0.5f) else palette.border.copy(alpha = 0.5f)),
            ) {
              Text(
                text = tag,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) palette.activePillFg else palette.textSecondary,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
              )
            }
          }
        }

        HorizontalDivider(color = palette.border.copy(alpha = 0.45f), thickness = 0.75.dp)

        if (filtered.isEmpty()) {
          Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
            Text("No matching snippets", style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(filtered, key = { it.id }) { item ->
              Surface(
                color = palette.editorBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Surface(color = palette.activePillBg, shape = RoundedCornerShape(6.dp)) {
                      Text(
                        text = item.abbrev,
                        fontFamily = JetBrainsMono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.activePillFg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      )
                    }
                    Surface(color = palette.chromePill, shape = RoundedCornerShape(6.dp)) {
                      Text(
                        text = if (item.isCustom) "${item.tag} · CUSTOM" else item.tag,
                        fontFamily = JetBrainsMono,
                        fontSize = 9.sp,
                        color = palette.textMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      )
                    }
                    Text(
                      text = item.title,
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.SemiBold,
                      color = palette.textPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    if (item.isCustom) {
                      IconButton(
                        onClick = { onDeleteCustomSnippet(item.id) },
                        modifier = Modifier.size(28.dp).testTag("snippet_delete_${item.id}"),
                      ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete snippet", tint = palette.textMuted, modifier = Modifier.size(15.dp))
                      }
                    }
                    TextButton(
                      onClick = {
                        onInsertSnippet(item)
                        onDismiss()
                      },
                      enabled = hasEditableActiveFile,
                      modifier = Modifier.height(30.dp).testTag("snippet_insert_${item.abbrev}"),
                    ) {
                      Text(if (hasEditableActiveFile) "Insert" else "Read-only", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                  }
                  if (item.description.isNotBlank()) {
                    Text(
                      text = item.description,
                      style = MaterialTheme.typography.bodySmall,
                      color = palette.textSecondary,
                    )
                  }
                  Text(
                    text = item.code.replace("\$END\$", "│").lines().take(4).joinToString("\n"),
                    fontFamily = JetBrainsMono,
                    fontSize = 10.5.sp,
                    color = palette.textMuted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .background(palette.chromeSurface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
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
