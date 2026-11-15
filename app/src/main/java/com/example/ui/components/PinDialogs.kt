package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.files.PinDialogMode

@Composable
fun SuPinDialog(
  mode: PinDialogMode,
  errorText: String?,
  onCreatePin: (pin: String, confirmPin: String) -> Unit,
  onVerifyPin: (pin: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var pin by remember(mode) { mutableStateOf("") }
  var confirmPin by remember(mode) { mutableStateOf("") }

  val isCreateFlow = mode == PinDialogMode.CREATE || mode == PinDialogMode.CHANGE_NEW
  val title = when (mode) {
    PinDialogMode.CREATE -> "Set Superuser PIN"
    PinDialogMode.UNLOCK -> "Unlock Superuser (su)"
    PinDialogMode.CHANGE_CURRENT -> "Verify Current PIN"
    PinDialogMode.CHANGE_NEW -> "Enter New SU PIN"
  }
  val subtitle = when (mode) {
    PinDialogMode.CREATE -> "Protect root su elevation, chmod, and partition remounting with a 4–8 digit PIN."
    PinDialogMode.UNLOCK -> "Enter your PIN to elevate to a persistent su root session."
    PinDialogMode.CHANGE_CURRENT -> "Confirm your existing SU PIN before setting a new one."
    PinDialogMode.CHANGE_NEW -> "Choose a new 4–8 digit PIN for Superuser mode."
  }

  val submitAction = {
    if (isCreateFlow) onCreatePin(pin, confirmPin) else onVerifyPin(pin)
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      tonalElevation = 8.dp,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("pin_dialog"),
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
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = if (isCreateFlow) Icons.Rounded.Security else Icons.Rounded.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(8.dp),
            )
          }
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = pin,
          onValueChange = { candidate ->
            if (candidate.length <= 8 && candidate.all { it.isDigit() }) {
              pin = candidate
            }
          },
          label = { Text("4–8 Digit PIN") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = if (isCreateFlow) ImeAction.Next else ImeAction.Done,
          ),
          keyboardActions = KeyboardActions(
            onDone = { if (!isCreateFlow) submitAction() },
          ),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pin_dialog_input"),
        )

        if (isCreateFlow) {
          OutlinedTextField(
            value = confirmPin,
            onValueChange = { candidate ->
              if (candidate.length <= 8 && candidate.all { it.isDigit() }) {
                confirmPin = candidate
              }
            },
            label = { Text("Confirm PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.NumberPassword,
              imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submitAction() }),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("pin_dialog_confirm_input"),
          )
        }

        if (!errorText.isNullOrBlank()) {
          Text(
            text = errorText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("pin_dialog_error"),
          )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("pin_dialog_cancel"),
          ) {
            Text("Cancel")
          }
          Button(
            onClick = submitAction,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.testTag("pin_dialog_submit"),
          ) {
            Text(if (isCreateFlow) "Save PIN" else "Unlock")
          }
        }
      }
    }
  }
}
