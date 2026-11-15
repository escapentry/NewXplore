package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.window.DialogProperties
import com.example.files.ArchiveViewerState
import com.example.files.ExploreDParsers

@Composable
fun ArchiveViewerDialog(
  state: ArchiveViewerState,
  onInspectEntry: (com.example.files.ArchiveEntryItem) -> Unit = {},
  onExtractHere: () -> Unit,
  onExtractSubfolder: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.background,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp)
        .testTag("archive_viewer_dialog"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = AppleSquircleShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Archive,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(8.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = state.entry.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${state.formatLabel} · ${state.items.size} entries · ${ExploreDParsers.formatBytes(state.entry.sizeBytes)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(onClick = onDismiss) {
            Text("Close")
          }
        }

        if (state.isExtracting) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (!state.error.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
          ) {
            Text(
              text = state.error,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            )
          }
        }

        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(state.items) { item ->
            Surface(
              onClick = { if (!item.isDirectory) onInspectEntry(item) },
              enabled = !item.isDirectory,
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = if (item.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                  contentDescription = null,
                  tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = item.name,
                  fontFamily = FontFamily.Monospace,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                if (!item.isDirectory && item.uncompressedSize > 0L) {
                  Text(
                    text = ExploreDParsers.formatBytes(item.uncompressedSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            enabled = !state.isExtracting,
            onClick = onExtractHere,
            modifier = Modifier
              .weight(1f)
              .testTag("archive_extract_here"),
          ) {
            Text("Extract Here")
          }
          Button(
            enabled = !state.isExtracting,
            onClick = onExtractSubfolder,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("archive_extract_subfolder"),
          ) {
            Text("Extract Folder")
          }
        }
      }
    }
  }
}
