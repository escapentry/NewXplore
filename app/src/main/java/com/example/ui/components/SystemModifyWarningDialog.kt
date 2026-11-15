package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.files.SystemModificationWarning

@Composable
fun SystemModifyWarningDialog(
  warning: SystemModificationWarning,
  isSuActive: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("system_warning_dialog"),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = AppleSquircleShape(),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.WarningAmber,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(8.dp),
            )
          }
          Text(
            text = "System Partition Modification",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }

        Text(
          text = "You are about to ${warning.actionLabel.lowercase()} inside a protected system path:",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Text(
            text = warning.targetPath,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }

        Text(
          text = if (isSuActive) {
            "Modifying system files can affect device stability. The partition will be mounted read-write automatically for this operation."
          } else {
            "Modifying system files requires Superuser access and will automatically mount the target partition read-write after PIN verification."
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("system_warning_cancel"),
          ) {
            Text("Cancel")
          }
          Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.testTag("system_warning_confirm"),
          ) {
            Text(if (isSuActive) "Proceed" else "Unlock SU & Proceed")
          }
        }
      }
    }
  }
}
