package com.example.data.local

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.remote.MultipartWorkerParser
import com.example.data.remote.WorkerFile
import com.example.data.remote.WorkerWorkspace
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.autoSaveDraftDataStore by preferencesDataStore(name = "edgecode_autosave_drafts")

data class AutoSaveSessionPointer(
  val workspaceKey: String,
  val scriptName: String,
  val isLocalScratchpad: Boolean,
  val accountId: String,
)

data class AutoSavedWorkspaceDraft(
  val workspaceKey: String,
  val scriptName: String,
  val isLocalScratchpad: Boolean,
  val accountId: String,
  val entryModulePath: String,
  val compatibilityDate: String,
  val openTabs: List<String>,
  val activeFilePath: String?,
  val cursorSelections: Map<String, Pair<Int, Int>>,
  val files: List<WorkerFile>,
  val savedAtEpochMs: Long,
  val contentDigest: Int,
) {
  fun toWorkspace(existingAccountSubdomain: String = ""): WorkerWorkspace {
    val resolvedEntry =
      files.firstOrNull { it.isEntryPoint }?.path
        ?: entryModulePath.takeIf { em -> files.any { it.path == em } }
        ?: files.firstOrNull()?.path
        ?: "index.js"
    return WorkerWorkspace(
      scriptName = scriptName,
      isEsModuleFormat = true,
      entryModulePath = resolvedEntry,
      compatibilityDate = compatibilityDate.ifBlank { "2024-11-01" },
      files = files.map { it.copy(isEntryPoint = it.path == resolvedEntry) },
      bindings = emptyList(),
      routes = emptyList(),
      workersDevEnabled = false,
      accountSubdomain = existingAccountSubdomain,
    )
  }

  fun toFieldValues(): Map<String, TextFieldValue> =
    files.associate { file ->
      val sel = cursorSelections[file.path]
      val range =
        if (sel != null) {
          TextRange(
            sel.first.coerceIn(0, file.content.length),
            sel.second.coerceIn(0, file.content.length),
          )
        } else {
          TextRange.Zero
        }
      file.path to TextFieldValue(file.content, range)
    }
}

/**
 * Jetpack DataStore-backed local persistent auto-save storage.
 *
 * Persists open editor buffers, file contents, tabs, cursor positions, and the last active
 * workspace pointer so code edits survive process death or sudden termination without data loss.
 */
class AutoSaveDraftStore(private val context: Context) {
  private object Keys {
    const val DRAFT_PREFIX = "autosave_draft_"
    val LAST_WORKSPACE_KEY = stringPreferencesKey("last_active_workspace_key")
    val LAST_SCRIPT_NAME = stringPreferencesKey("last_active_script_name")
    val LAST_IS_SCRATCHPAD = booleanPreferencesKey("last_active_is_scratchpad")
    val LAST_ACCOUNT_ID = stringPreferencesKey("last_active_account_id")

    fun draftKey(workspaceKey: String) = stringPreferencesKey("$DRAFT_PREFIX${sanitizeKey(workspaceKey)}")
  }

  suspend fun saveWorkspaceDraft(draft: AutoSavedWorkspaceDraft) {
    val editableFiles = draft.files.filterNot { it.isVirtualReadOnly }
    if (editableFiles.isEmpty()) return
    val serialized = serializeDraft(draft, editableFiles)
    context.autoSaveDraftDataStore.edit { prefs ->
      prefs[Keys.draftKey(draft.workspaceKey)] = serialized
    }
  }

  suspend fun loadWorkspaceDraft(workspaceKey: String): AutoSavedWorkspaceDraft? {
    val prefs = context.autoSaveDraftDataStore.data.first()
    val raw = prefs[Keys.draftKey(workspaceKey)] ?: return null
    return deserializeDraft(raw)
  }

  suspend fun loadAllDrafts(): List<AutoSavedWorkspaceDraft> {
    val prefs = context.autoSaveDraftDataStore.data.first()
    val results = mutableListOf<AutoSavedWorkspaceDraft>()
    prefs.asMap().forEach { (key, value) ->
      if (key.name.startsWith(Keys.DRAFT_PREFIX) && value is String) {
        deserializeDraft(value)?.let { results.add(it) }
      }
    }
    return results.sortedByDescending { it.savedAtEpochMs }
  }

  suspend fun clearWorkspaceDraft(workspaceKey: String) {
    context.autoSaveDraftDataStore.edit { prefs ->
      prefs.remove(Keys.draftKey(workspaceKey))
      if (prefs[Keys.LAST_WORKSPACE_KEY] == workspaceKey) {
        prefs.remove(Keys.LAST_WORKSPACE_KEY)
        prefs.remove(Keys.LAST_SCRIPT_NAME)
        prefs.remove(Keys.LAST_IS_SCRATCHPAD)
        prefs.remove(Keys.LAST_ACCOUNT_ID)
      }
    }
  }

  suspend fun clearAllDrafts() {
    context.autoSaveDraftDataStore.edit { prefs ->
      val draftKeys = prefs.asMap().keys.filter { it.name.startsWith(Keys.DRAFT_PREFIX) }
      draftKeys.forEach { prefs.remove(it) }
      prefs.remove(Keys.LAST_WORKSPACE_KEY)
      prefs.remove(Keys.LAST_SCRIPT_NAME)
      prefs.remove(Keys.LAST_IS_SCRATCHPAD)
      prefs.remove(Keys.LAST_ACCOUNT_ID)
    }
  }

  suspend fun saveLastActiveSession(pointer: AutoSaveSessionPointer) {
    context.autoSaveDraftDataStore.edit { prefs ->
      prefs[Keys.LAST_WORKSPACE_KEY] = pointer.workspaceKey
      prefs[Keys.LAST_SCRIPT_NAME] = pointer.scriptName
      prefs[Keys.LAST_IS_SCRATCHPAD] = pointer.isLocalScratchpad
      prefs[Keys.LAST_ACCOUNT_ID] = pointer.accountId
    }
  }

  suspend fun loadLastSessionPointer(): AutoSaveSessionPointer? {
    val prefs = context.autoSaveDraftDataStore.data.first()
    val key = prefs[Keys.LAST_WORKSPACE_KEY]?.takeIf { it.isNotBlank() } ?: return null
    val scriptName = prefs[Keys.LAST_SCRIPT_NAME]?.takeIf { it.isNotBlank() } ?: return null
    return AutoSaveSessionPointer(
      workspaceKey = key,
      scriptName = scriptName,
      isLocalScratchpad = prefs[Keys.LAST_IS_SCRATCHPAD] ?: false,
      accountId = prefs[Keys.LAST_ACCOUNT_ID].orEmpty(),
    )
  }

  suspend fun clearLastActiveSession() {
    context.autoSaveDraftDataStore.edit { prefs ->
      prefs.remove(Keys.LAST_WORKSPACE_KEY)
      prefs.remove(Keys.LAST_SCRIPT_NAME)
      prefs.remove(Keys.LAST_IS_SCRATCHPAD)
      prefs.remove(Keys.LAST_ACCOUNT_ID)
    }
  }

  private fun serializeDraft(
    draft: AutoSavedWorkspaceDraft,
    editableFiles: List<WorkerFile>,
  ): String {
    val root = JSONObject()
    root.put("workspaceKey", draft.workspaceKey)
    root.put("scriptName", draft.scriptName)
    root.put("isLocalScratchpad", draft.isLocalScratchpad)
    root.put("accountId", draft.accountId)
    root.put("entryModulePath", draft.entryModulePath)
    root.put("compatibilityDate", draft.compatibilityDate)
    root.put("activeFilePath", draft.activeFilePath.orEmpty())
    root.put("savedAtEpochMs", draft.savedAtEpochMs)
    root.put("contentDigest", draft.contentDigest)
    root.put("openTabs", JSONArray(draft.openTabs))

    val selObj = JSONObject()
    draft.cursorSelections.forEach { (path, range) ->
      selObj.put(
        path,
        JSONArray().apply {
          put(range.first)
          put(range.second)
        },
      )
    }
    root.put("cursorSelections", selObj)

    val filesArr = JSONArray()
    editableFiles.forEach { wf ->
      val persistedContent =
        if (wf.content.length > MAX_PERSISTED_FILE_CHARS) wf.content.substring(0, MAX_PERSISTED_FILE_CHARS)
        else wf.content
      val persistedOriginal =
        if (wf.originalContent.length > MAX_PERSISTED_FILE_CHARS) wf.originalContent.substring(0, MAX_PERSISTED_FILE_CHARS)
        else wf.originalContent
      filesArr.put(
        JSONObject().apply {
          put("path", wf.path)
          put("content", persistedContent)
          put("originalContent", persistedOriginal)
          put("contentType", wf.contentType)
          put("isEntryPoint", wf.isEntryPoint)
        },
      )
    }
    root.put("files", filesArr)
    return root.toString()
  }

  private fun deserializeDraft(rawJson: String): AutoSavedWorkspaceDraft? {
    return runCatching {
      val root = JSONObject(rawJson)
      val workspaceKey = root.optString("workspaceKey").takeIf { it.isNotBlank() } ?: return null
      val scriptName = root.optString("scriptName").ifBlank { "worker" }
      val isLocalScratchpad = root.optBoolean("isLocalScratchpad", workspaceKey.startsWith("local_"))
      val accountId = root.optString("accountId")
      val entryModulePath = root.optString("entryModulePath").ifBlank { "src/index.js" }
      val compatibilityDate = root.optString("compatibilityDate").ifBlank { "2024-11-01" }
      val activeFilePath = root.optString("activeFilePath").takeIf { it.isNotBlank() }
      val savedAt = root.optLong("savedAtEpochMs", 0L)
      val digest = root.optInt("contentDigest", 0)

      val openTabs = mutableListOf<String>()
      root.optJSONArray("openTabs")?.let { arr ->
        for (i in 0 until arr.length()) {
          arr.optString(i).takeIf { it.isNotBlank() }?.let { openTabs.add(it) }
        }
      }

      val selections = mutableMapOf<String, Pair<Int, Int>>()
      root.optJSONObject("cursorSelections")?.let { selObj ->
        val keys = selObj.keys()
        while (keys.hasNext()) {
          val path = keys.next()
          val arr = selObj.optJSONArray(path) ?: continue
          if (arr.length() >= 2) {
            selections[path] = arr.optInt(0, 0) to arr.optInt(1, 0)
          }
        }
      }

      val filesArr = root.optJSONArray("files") ?: return null
      val files = mutableListOf<WorkerFile>()
      for (i in 0 until filesArr.length()) {
        val obj = filesArr.optJSONObject(i) ?: continue
        val path = obj.optString("path").takeIf { it.isNotBlank() } ?: continue
        val content = obj.optString("content")
        val original = obj.optString("originalContent")
        val contentType =
          obj.optString("contentType").ifBlank { MultipartWorkerParser.inferModuleMimeType(path) }
        val isEntry = obj.optBoolean("isEntryPoint", false)
        files.add(
          WorkerFile(
            path = path,
            content = content,
            originalContent = original,
            contentType = contentType,
            isEntryPoint = isEntry,
          ),
        )
      }
      if (files.isEmpty()) return null
      AutoSavedWorkspaceDraft(
        workspaceKey = workspaceKey,
        scriptName = scriptName,
        isLocalScratchpad = isLocalScratchpad,
        accountId = accountId,
        entryModulePath = entryModulePath,
        compatibilityDate = compatibilityDate,
        openTabs = openTabs,
        activeFilePath = activeFilePath,
        cursorSelections = selections,
        files = files,
        savedAtEpochMs = savedAt,
        contentDigest = digest,
      )
    }.getOrNull()
  }

  companion object {
    private const val MAX_PERSISTED_FILE_CHARS = 512_000

    private fun sanitizeKey(workspaceKey: String): String =
      workspaceKey.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun computeContentDigest(
      files: List<WorkerFile>,
      openTabs: List<String>,
      activeFilePath: String?,
      entryModulePath: String,
    ): Int {
      var hash = 17
      files.filterNot { it.isVirtualReadOnly }.forEach { f ->
        hash = 31 * hash + f.path.hashCode()
        hash = 31 * hash + f.content.hashCode()
        hash = 31 * hash + f.originalContent.hashCode()
        hash = 31 * hash + if (f.isEntryPoint) 1 else 0
      }
      hash = 31 * hash + openTabs.hashCode()
      hash = 31 * hash + (activeFilePath?.hashCode() ?: 0)
      hash = 31 * hash + entryModulePath.hashCode()
      return hash
    }

    /**
     * Merges an auto-saved DataStore draft onto a freshly imported workspace, keeping the fresh
     * baseline's `originalContent` intact while restoring edited file contents and locally added files.
     */
    fun mergeDraftIntoWorkspace(
      imported: WorkerWorkspace,
      draft: AutoSavedWorkspaceDraft,
    ): Pair<WorkerWorkspace, Boolean> {
      val draftByPath = draft.files.associateBy { it.path }
      val importedByPath = imported.files.associateBy { it.path }
      var hasDiff = false

      val mergedExisting =
        imported.files.map { impFile ->
          if (impFile.isVirtualReadOnly) {
            impFile
          } else {
            val draftFile = draftByPath[impFile.path]
            if (draftFile != null && draftFile.content != impFile.content) {
              hasDiff = true
              impFile.copy(content = draftFile.content)
            } else {
              impFile
            }
          }
        }

      val extraLocalFiles =
        draft.files
          .filter { df -> !df.isVirtualReadOnly && df.path !in importedByPath }
          .map { df ->
            hasDiff = true
            df.copy(originalContent = "", isEntryPoint = false)
          }

      if (!hasDiff) {
        return imported to false
      }

      val combined = mergedExisting + extraLocalFiles
      return imported.copy(
        files = combined,
        hasModuleSetChanged = imported.hasModuleSetChanged || extraLocalFiles.isNotEmpty(),
      ) to true
    }
  }
}
