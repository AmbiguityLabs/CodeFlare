package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.StoragePermissionHelper

/**
 * Returns a lambda that prompts the Android native storage access flow:
 * - Android 11+ (API 30+): opens "All files access" system settings for EdgeCode
 * - Android 10 & below: requests runtime READ/WRITE_EXTERNAL_STORAGE permissions
 */
@Composable
fun rememberStorageAccessRequester(
  onPermissionFlowReturned: () -> Unit,
): () -> Unit {
  val context = LocalContext.current
  val allFilesLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      onPermissionFlowReturned()
    }
  val legacyPermissionsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      onPermissionFlowReturned()
    }

  return remember(context, allFilesLauncher, legacyPermissionsLauncher, onPermissionFlowReturned) {
    {
      if (StoragePermissionHelper.hasNativeFileAccess(context)) {
        onPermissionFlowReturned()
      } else if (StoragePermissionHelper.needsAllFilesAccessSettings()) {
        runCatching {
          allFilesLauncher.launch(StoragePermissionHelper.buildManageAllFilesIntent(context))
        }.onFailure {
          onPermissionFlowReturned()
        }
      } else {
        legacyPermissionsLauncher.launch(StoragePermissionHelper.requiredLegacyPermissions())
      }
    }
  }
}
