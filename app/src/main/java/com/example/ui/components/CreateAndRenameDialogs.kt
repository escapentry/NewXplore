package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun NameInputDialog(
  title: String,
  label: String,
  initialValue: String = "",
  confirmLabel: String,
  tagPrefix: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var value by remember(title, initialValue) { mutableStateOf(initialValue) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("${tagPrefix}_dialog"),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
          value = value,
          onValueChange = { value = it },
          label = { Text(label) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
          Button(
            enabled = value.trim().isNotEmpty(),
            onClick = { onConfirm(value.trim()) },
            modifier = Modifier.testTag("${tagPrefix}_confirm"),
          ) {
            Text(confirmLabel)
          }
        }
      }
    }
  }
}

@Composable
fun ConfirmDeleteDialog(
  count: Int,
  sampleName: String?,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("delete_confirm_dialog"),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = if (count == 1 && sampleName != null) "Delete \"$sampleName\"?" else "Delete $count items?",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "This action permanently removes the selected item(s) from disk.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
          Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.testTag("delete_confirm_button"),
          ) {
            Text("Delete")
          }
        }
      }
    }
  }
}

@Composable
fun CreateSymlinkDialog(
  initialTarget: String = "",
  initialLinkName: String = "",
  onConfirm: (targetPath: String, linkName: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var targetPath by remember(initialTarget) { mutableStateOf(initialTarget) }
  var linkName by remember(initialLinkName) { mutableStateOf(initialLinkName) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("create_symlink_dialog"),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Create Symbolic Link (ln -s)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
          value = targetPath,
          onValueChange = { targetPath = it },
          label = { Text("Target path") },
          placeholder = { Text("/system/bin/sh") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("symlink_target_input"),
        )
        OutlinedTextField(
          value = linkName,
          onValueChange = { linkName = it },
          label = { Text("Link name (or full path)") },
          placeholder = { Text("my_link") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("symlink_name_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
          Button(
            enabled = targetPath.trim().isNotEmpty() && linkName.trim().isNotEmpty(),
            onClick = { onConfirm(targetPath.trim(), linkName.trim()) },
            modifier = Modifier.testTag("symlink_confirm_button"),
          ) {
            Text("Create Link")
          }
        }
      }
    }
  }
}

@Composable
fun GoToPathDialog(
  initialPath: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var path by remember(initialPath) { mutableStateOf(initialPath) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("goto_path_dialog"),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Go to Path",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
          value = path,
          onValueChange = { path = it },
          label = { Text("Directory path") },
          placeholder = { Text("/data/data") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("goto_path_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
          Button(
            enabled = path.trim().isNotEmpty(),
            onClick = { onConfirm(path.trim()) },
            modifier = Modifier.testTag("goto_path_confirm"),
          ) {
            Text("Open")
          }
        }
      }
    }
  }
}
