package com.example.data.local

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Helper for checking and requesting native external file management access so EdgeCode
 * can create and maintain its dedicated `EdgeCode/` workspace directory on phone storage.
 */
object StoragePermissionHelper {

  fun hasNativeFileAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      return runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    }
    val readGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED
    val writeGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED
    return readGranted && writeGranted
  }

  fun needsAllFilesAccessSettings(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

  fun requiredLegacyPermissions(): Array<String> =
    arrayOf(
      Manifest.permission.READ_EXTERNAL_STORAGE,
      Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

  fun buildManageAllFilesIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val appSpecific =
        Intent(
          Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
          Uri.parse("package:${context.packageName}"),
        )
      if (appSpecific.resolveActivity(context.packageManager) != null) {
        return appSpecific
      }
      return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }
    return Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.parse("package:${context.packageName}"),
    )
  }
}
