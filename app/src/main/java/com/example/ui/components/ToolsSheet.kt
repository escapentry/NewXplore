package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.files.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsSheet(
  isSuActive: Boolean,
  hasPinConfigured: Boolean,
  showHiddenFiles: Boolean,
  sortOrder: SortOrder,
  selinuxMode: String,
  quickToolOutput: String?,
  onToggleSu: () -> Unit,
  onChangePin: () -> Unit,
  onToggleSeLinux: () -> Unit,
  onRunRootCommand: (String) -> Unit,
  onReboot: (String) -> Unit,
  onToggleShowHidden: (Boolean) -> Unit,
  onChangeSortOrder: (SortOrder) -> Unit,
  onDismiss: () -> Unit,
) {
  var commandInput by remember { mutableStateOf("id") }
  var pendingRebootTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = Modifier.testTag("tools_sheet"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 26.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Tools",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      // Superuser & PIN Tool Card (No clear PIN option once configured)
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = if (isSuActive) Icons.Rounded.Security else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (isSuActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
              )
              Text(
                text = if (isSuActive) "Superuser Active" else "Superuser Locked",
                style = MaterialTheme.typography.titleMedium,
              )
            }
            Button(
              onClick = {
                onDismiss()
                onToggleSu()
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isSuActive) {
                  MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                } else {
                  MaterialTheme.colorScheme.primary
                },
                contentColor = if (isSuActive) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.onPrimary
                },
              ),
              modifier = Modifier.testTag("tools_su_toggle_button"),
            ) {
              Text(if (isSuActive) "Lock SU" else "Unlock SU")
            }
          }

          OutlinedButton(
            onClick = onChangePin,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("tools_change_pin_button"),
          ) {
            Text(if (hasPinConfigured) "Change SU PIN" else "Set SU PIN")
          }
        }
      }

      // Simple Root Utilities
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column {
                Text("SELinux Mode", style = MaterialTheme.typography.bodyLarge)
                Text(
                  text = selinuxMode,
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.tertiary,
                )
              }
            }
            OutlinedButton(
              enabled = isSuActive,
              onClick = onToggleSeLinux,
              modifier = Modifier.testTag("tools_selinux_toggle"),
            ) {
              Text(if (selinuxMode.equals("Enforcing", ignoreCase = true)) "Set Permissive" else "Set Enforcing")
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = commandInput,
              onValueChange = { commandInput = it },
              label = { Text(if (isSuActive) "Root command (#)" else "Shell command ($)") },
              singleLine = true,
              modifier = Modifier
                .weight(1f)
                .testTag("tools_quick_cmd_input"),
            )
            IconButton(
              onClick = { onRunRootCommand(commandInput) },
              modifier = Modifier.testTag("tools_quick_cmd_run"),
            ) {
              Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Run command",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf("id", "df -h", "uname -a", "getenforce").forEach { preset ->
              AssistChip(
                onClick = {
                  commandInput = preset
                  onRunRootCommand(preset)
                },
                label = { Text(preset, fontFamily = FontFamily.Monospace) },
              )
            }
          }

          if (!quickToolOutput.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.background,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Text(
                text = quickToolOutput,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(max = 120.dp)
                  .verticalScroll(rememberScrollState())
                  .padding(10.dp),
              )
            }
          }

          if (isSuActive) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              AssistChip(
                onClick = { pendingRebootTarget = "Reboot" to "" },
                leadingIcon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
                label = { Text("Reboot") },
              )
              AssistChip(
                onClick = { pendingRebootTarget = "Reboot Recovery" to "recovery" },
                label = { Text("Recovery") },
              )
              AssistChip(
                onClick = { pendingRebootTarget = "Reboot Bootloader" to "bootloader" },
                label = { Text("Bootloader") },
              )
            }
          }
        }
      }

      // Hidden files toggle
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Show hidden files (.*)",
            style = MaterialTheme.typography.bodyLarge,
          )
          Switch(
            checked = showHiddenFiles,
            onCheckedChange = onToggleShowHidden,
            modifier = Modifier.testTag("tools_hidden_switch"),
          )
        }
      }

      // Sort order
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Sort order",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SortOrder.entries.forEach { order ->
            FilterChip(
              selected = sortOrder == order,
              onClick = { onChangeSortOrder(order) },
              label = { Text(order.label) },
              modifier = Modifier.testTag("tools_sort_${order.name.lowercase()}"),
            )
          }
        }
      }
    }
  }

  pendingRebootTarget?.let { (label, target) ->
    AlertDialog(
      onDismissRequest = { pendingRebootTarget = null },
      title = { Text(label) },
      text = { Text("Restart device now?") },
      confirmButton = {
        Button(
          onClick = {
            pendingRebootTarget = null
            onReboot(target)
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        ) {
          Text("Reboot")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingRebootTarget = null }) {
          Text("Cancel")
        }
      },
    )
  }
}
