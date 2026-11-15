package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.files.ApkInspectorInfo
import com.example.files.ExploreDParsers

@Composable
fun ApkActionDialog(
  info: ApkInspectorInfo,
  isSuActive: Boolean,
  onViewContents: () -> Unit,
  onInstall: () -> Unit,
  onRootInstall: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      tonalElevation = 8.dp,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("apk_action_dialog"),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = AppleSquircleShape(),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
            modifier = Modifier.size(44.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Android,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.padding(10.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = info.appLabel,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = info.packageName,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "File: ${info.entry.name} (${ExploreDParsers.formatBytes(info.entry.sizeBytes)})",
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "Version: ${info.versionName} (${info.versionCode})",
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (info.minSdk != null || info.targetSdk != null) {
              Text(
                text = "SDK: min ${info.minSdk ?: "—"} · target ${info.targetSdk ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onViewContents,
            modifier = Modifier
              .weight(1f)
              .testTag("apk_view_contents_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.FolderZip,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("View Contents")
          }
          Button(
            onClick = onInstall,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("apk_install_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.InstallMobile,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Install")
          }
        }

        if (isSuActive) {
          OutlinedButton(
            onClick = onRootInstall,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("apk_root_install_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Terminal,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Silent Root Install (pm)")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
        }
      }
    }
  }
}
