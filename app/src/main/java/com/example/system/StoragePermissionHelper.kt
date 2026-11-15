package com.example.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

object StoragePermissionHelper {

  val legacyStoragePermissions: Array<String> = arrayOf(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
  )

  fun requiresAllFilesSettingsFlow(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

  fun hasFilesPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    } else {
      val read = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE,
      ) == PackageManager.PERMISSION_GRANTED
      val write = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
      ) == PackageManager.PERMISSION_GRANTED
      read && write
    }
  }

  fun createManageAllFilesIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.parse("package:${context.packageName}")
      }
    } else {
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
      }
    }
  }

  fun createFallbackAllFilesIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    } else {
      Intent(Settings.ACTION_SETTINGS)
    }
  }
}
