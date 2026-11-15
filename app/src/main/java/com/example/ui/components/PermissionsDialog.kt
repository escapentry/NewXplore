package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.files.ExploreDParsers
import com.example.files.FileEntry
import com.example.files.FilePropertiesDetails
import com.example.files.PermissionBits

private val COMMON_UID_GIDS = listOf(
  "root" to "0",
  "system" to "1000",
  "radio" to "1001",
  "shell" to "2000",
  "media_rw" to "1023",
  "sdcard_rw" to "1015",
  "cache" to "2001",
  "nobody" to "9999",
)

private val COMMON_SELINUX_CONTEXTS = listOf(
  "u:object_r:system_file:s0",
  "u:object_r:system_data_file:s0",
  "u:object_r:apk_data_file:s0",
  "u:object_r:app_data_file:s0",
  "u:object_r:vendor_file:s0",
)

@Composable
fun PermissionsDialog(
  entry: FileEntry,
  isSuActive: Boolean,
  onApply: (octal: String, recursive: Boolean, ownerGroup: String?) -> Unit,
  onDismiss: () -> Unit,
  details: FilePropertiesDetails? = null,
  onApplyWithSeLinux: ((octal: String, recursive: Boolean, ownerGroup: String?, seLinuxContext: String?) -> Unit)? = null,
  selectedEntries: List<FileEntry> = listOf(entry),
  onApplyAdvanced: ((octal: String, recursive: Boolean, noExecOnChildFiles: Boolean, ownerGroup: String?, seLinuxContext: String?) -> Unit)? = null,
) {
  val targets = remember(entry, selectedEntries) {
    selectedEntries.ifEmpty { listOf(entry) }
  }
  val isMulti = targets.size > 1
  val hasDirectories = remember(targets) { targets.any { it.isDirectory } }
  val selectionKey = remember(targets) { targets.joinToString("|") { it.path } }

  val initialBits = remember(selectionKey) {
    val distinctOctals = targets.map { it.permissionsOctal }.distinct()
    when {
      distinctOctals.size == 1 ->
        PermissionBits.fromOctal(distinctOctals.first())
          ?: PermissionBits.fromSymbolic(targets.first().permissionsSymbolic)
      hasDirectories -> PermissionBits.fromOctal("0755")!!
      else ->
        PermissionBits.fromOctal(entry.permissionsOctal)
          ?: PermissionBits.fromSymbolic(entry.permissionsSymbolic)
    }
  }
  var bits by remember(selectionKey) { mutableStateOf(initialBits) }
  var octalText by remember(selectionKey) { mutableStateOf(initialBits.toOctalString()) }
  var recursive by remember(selectionKey) { mutableStateOf(false) }
  var noExecOnChildFiles by remember(selectionKey) { mutableStateOf(false) }

  val sharedOwner = remember(selectionKey) {
    val owners = targets.map { it.owner.trim() }.filter { it.isNotEmpty() }.distinct()
    if (owners.size == 1) owners.first() else ""
  }
  val sharedGroup = remember(selectionKey) {
    val groups = targets.map { it.group.trim() }.filter { it.isNotEmpty() }.distinct()
    if (groups.size == 1) groups.first() else ""
  }

  var ownerText by remember(selectionKey) { mutableStateOf(if (isMulti) sharedOwner else entry.owner) }
  var groupText by remember(selectionKey) { mutableStateOf(if (isMulti) sharedGroup else entry.group) }
  var ownerDropdownExpanded by remember { mutableStateOf(false) }
  var groupDropdownExpanded by remember { mutableStateOf(false) }

  var seLinuxText by remember(selectionKey) {
    mutableStateOf(if (isMulti) "" else (details?.seLinuxContext ?: entry.seLinuxContext))
  }
  LaunchedEffect(details?.seLinuxContext, isMulti) {
    if (!isMulti) {
      val fetched = details?.seLinuxContext
      if (!fetched.isNullOrBlank()) {
        seLinuxText = fetched
      }
    }
  }

  val isOctalValid = PermissionBits.fromOctal(octalText) != null

  fun updateBits(next: PermissionBits) {
    bits = next
    octalText = next.toOctalString()
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      tonalElevation = 8.dp,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("permissions_dialog"),
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Permissions",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = if (isMulti) "${targets.size} items selected" else entry.name,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("chmod_target_subtitle"),
        )

        Column(
          modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          // Properties & symlink info
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              if (isMulti) {
                val dirCount = targets.count { it.isDirectory }
                val fileCount = targets.size - dirCount
                val namesPreview = targets.take(4).joinToString(", ") { it.name } +
                  if (targets.size > 4) " (+${targets.size - 4} more)" else ""
                Text(
                  text = "Items: $namesPreview",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "$dirCount folder(s) · $fileCount file(s)",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              } else {
                Text(
                  text = "Path: ${entry.path}",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                val kindLabel = when {
                  entry.isSymlink -> "Symlink → ${details?.resolvedLinkTarget ?: entry.symlinkTarget ?: "?"}"
                  entry.isDirectory -> "Directory"
                  else -> "File · ${ExploreDParsers.formatBytes(entry.sizeBytes)}"
                }
                Text(
                  text = "$kindLabel${if (entry.modifiedDisplay.isNotBlank()) " · ${entry.modifiedDisplay}" else ""}",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }

          // Octal input + live symbolic readout
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OutlinedTextField(
              value = octalText,
              onValueChange = { raw ->
                val filtered = raw.filter { it in '0'..'7' }.take(4)
                octalText = filtered
                PermissionBits.fromOctal(filtered)?.let { parsed ->
                  bits = parsed
                }
              },
              label = { Text("Octal") },
              singleLine = true,
              isError = !isOctalValid,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier
                .weight(1f)
                .testTag("chmod_octal_input"),
            )

            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                  text = "Symbolic",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = bits.toSymbolicString(),
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.testTag("chmod_symbolic_preview"),
                )
              }
            }
          }

          // Quick octal presets
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf("0644", "0755", "0600", "0777", "4755").forEach { preset ->
              AssistChip(
                onClick = {
                  PermissionBits.fromOctal(preset)?.let { updateBits(it) }
                },
                label = { Text(preset, fontFamily = FontFamily.Monospace) },
                modifier = Modifier.testTag("chmod_preset_$preset"),
              )
            }
          }

          // 3x3 Checkboxes grid (Owner / Group / Others)
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Spacer(modifier = Modifier.weight(1.2f))
                Text("Read", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("Write", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("Exec", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
              }

              PermRow(
                label = "Owner",
                r = bits.ownerRead,
                w = bits.ownerWrite,
                x = bits.ownerExecute,
                tagPrefix = "owner",
                onR = { updateBits(bits.copy(ownerRead = it)) },
                onW = { updateBits(bits.copy(ownerWrite = it)) },
                onX = { updateBits(bits.copy(ownerExecute = it)) },
              )
              PermRow(
                label = "Group",
                r = bits.groupRead,
                w = bits.groupWrite,
                x = bits.groupExecute,
                tagPrefix = "group",
                onR = { updateBits(bits.copy(groupRead = it)) },
                onW = { updateBits(bits.copy(groupWrite = it)) },
                onX = { updateBits(bits.copy(groupExecute = it)) },
              )
              PermRow(
                label = "Others",
                r = bits.otherRead,
                w = bits.otherWrite,
                x = bits.otherExecute,
                tagPrefix = "other",
                onR = { updateBits(bits.copy(otherRead = it)) },
                onW = { updateBits(bits.copy(otherWrite = it)) },
                onX = { updateBits(bits.copy(otherExecute = it)) },
              )
            }
          }

          // Special bits (SetUID / SetGID / Sticky)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            SpecialBitChip(
              label = "SUID",
              checked = bits.setUid,
              tag = "chmod_checkbox_suid",
              onToggle = { updateBits(bits.copy(setUid = it)) },
            )
            SpecialBitChip(
              label = "SGID",
              checked = bits.setGid,
              tag = "chmod_checkbox_sgid",
              onToggle = { updateBits(bits.copy(setGid = it)) },
            )
            SpecialBitChip(
              label = "Sticky",
              checked = bits.sticky,
              tag = "chmod_checkbox_sticky",
              onToggle = { updateBits(bits.copy(sticky = it)) },
            )
          }

          if (hasDirectories) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      val next = !recursive
                      recursive = next
                      if (!next) noExecOnChildFiles = false
                    }
                    .padding(vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "Apply recursively (-R)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                  )
                  Switch(
                    checked = recursive,
                    onCheckedChange = { next ->
                      recursive = next
                      if (!next) noExecOnChildFiles = false
                    },
                    modifier = Modifier.testTag("chmod_recursive_switch"),
                  )
                }

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      val next = !noExecOnChildFiles
                      noExecOnChildFiles = next
                      if (next) recursive = true
                    }
                    .padding(vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Checkbox(
                    checked = noExecOnChildFiles,
                    onCheckedChange = { next ->
                      noExecOnChildFiles = next
                      if (next) recursive = true
                    },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("chmod_no_exec_files_checkbox"),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Never set execute on child files",
                      style = MaterialTheme.typography.bodyMedium,
                    )
                    val filePreviewBits = bits.withoutExecuteBits()
                    val subLabel = if (recursive && noExecOnChildFiles) {
                      "Dirs: ${bits.toOctalString()} (${bits.toSymbolicString()}) · Files: ${filePreviewBits.toOctalString()} (${filePreviewBits.toSymbolicString()})"
                    } else {
                      "Keeps dirs ${bits.toOctalString()} while stripping +x on files (${filePreviewBits.toOctalString()})"
                    }
                    Text(
                      text = subLabel,
                      style = MaterialTheme.typography.labelSmall,
                      fontFamily = FontFamily.Monospace,
                      color = if (recursive && noExecOnChildFiles) {
                        MaterialTheme.colorScheme.tertiary
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      },
                      modifier = Modifier.testTag("chmod_no_exec_preview"),
                    )
                  }
                }
              }
            }
          }

          if (isSuActive) {
            // Owner & Group (chown) with dropdowns + numeric UID/GID support
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                  value = ownerText,
                  onValueChange = { ownerText = it },
                  label = { Text("Owner / UID") },
                  placeholder = if (isMulti && ownerText.isEmpty()) {
                    { Text("unchanged") }
                  } else null,
                  singleLine = true,
                  trailingIcon = {
                    IconButton(onClick = { ownerDropdownExpanded = true }) {
                      Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pick owner")
                    }
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chmod_owner_input"),
                )
                DropdownMenu(
                  expanded = ownerDropdownExpanded,
                  onDismissRequest = { ownerDropdownExpanded = false },
                ) {
                  COMMON_UID_GIDS.forEach { (name, uid) ->
                    DropdownMenuItem(
                      text = { Text("$name ($uid)", fontFamily = FontFamily.Monospace) },
                      onClick = {
                        ownerText = name
                        ownerDropdownExpanded = false
                      },
                    )
                  }
                }
              }

              Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                  value = groupText,
                  onValueChange = { groupText = it },
                  label = { Text("Group / GID") },
                  placeholder = if (isMulti && groupText.isEmpty()) {
                    { Text("unchanged") }
                  } else null,
                  singleLine = true,
                  trailingIcon = {
                    IconButton(onClick = { groupDropdownExpanded = true }) {
                      Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Pick group")
                    }
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chmod_group_input"),
                )
                DropdownMenu(
                  expanded = groupDropdownExpanded,
                  onDismissRequest = { groupDropdownExpanded = false },
                ) {
                  COMMON_UID_GIDS.forEach { (name, gid) ->
                    DropdownMenuItem(
                      text = { Text("$name ($gid)", fontFamily = FontFamily.Monospace) },
                      onClick = {
                        groupText = name
                        groupDropdownExpanded = false
                      },
                    )
                  }
                }
              }
            }

            // SELinux security context (chcon)
            OutlinedTextField(
              value = seLinuxText,
              onValueChange = { seLinuxText = it },
              label = { Text("SELinux Context (chcon)") },
              placeholder = { Text("u:object_r:system_file:s0") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("chmod_selinux_input"),
            )
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              COMMON_SELINUX_CONTEXTS.forEach { ctx ->
                AssistChip(
                  onClick = { seLinuxText = ctx },
                  label = {
                    Text(
                      text = ctx.removePrefix("u:object_r:").removeSuffix(":s0"),
                      fontFamily = FontFamily.Monospace,
                    )
                  },
                )
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("chmod_cancel_button"),
          ) {
            Text("Cancel")
          }
          Spacer(modifier = Modifier.width(6.dp))
          Button(
            enabled = isOctalValid,
            onClick = {
              val finalOctal = if (octalText.length == 3) "0$octalText" else octalText
              val cleanOwner = ownerText.trim()
              val cleanGroup = groupText.trim()
              val combinedOwner = when {
                cleanOwner.isNotEmpty() && cleanGroup.isNotEmpty() -> "$cleanOwner:$cleanGroup"
                cleanOwner.isNotEmpty() -> cleanOwner
                cleanGroup.isNotEmpty() -> ":$cleanGroup"
                else -> null
              }
              val cleanCtx = seLinuxText.trim().takeIf { it.isNotEmpty() }
              val effectiveNoExec = hasDirectories && recursive && noExecOnChildFiles
              when {
                onApplyAdvanced != null ->
                  onApplyAdvanced(finalOctal, recursive, effectiveNoExec, combinedOwner, cleanCtx)
                onApplyWithSeLinux != null ->
                  onApplyWithSeLinux(finalOctal, recursive, combinedOwner, cleanCtx)
                else ->
                  onApply(finalOctal, recursive, combinedOwner)
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.testTag("chmod_apply_button"),
          ) {
            Text("Apply")
          }
        }
      }
    }
  }
}

@Composable
private fun PermRow(
  label: String,
  r: Boolean,
  w: Boolean,
  x: Boolean,
  tagPrefix: String,
  onR: (Boolean) -> Unit,
  onW: (Boolean) -> Unit,
  onX: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.weight(1.2f),
    )
    Checkbox(
      checked = r,
      onCheckedChange = onR,
      colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
      modifier = Modifier
        .weight(1f)
        .testTag("chmod_checkbox_${tagPrefix}_r"),
    )
    Checkbox(
      checked = w,
      onCheckedChange = onW,
      colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
      modifier = Modifier
        .weight(1f)
        .testTag("chmod_checkbox_${tagPrefix}_w"),
    )
    Checkbox(
      checked = x,
      onCheckedChange = onX,
      colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
      modifier = Modifier
        .weight(1f)
        .testTag("chmod_checkbox_${tagPrefix}_x"),
    )
  }
}

@Composable
private fun SpecialBitChip(
  label: String,
  checked: Boolean,
  tag: String,
  onToggle: (Boolean) -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.clickable { onToggle(!checked) },
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = onToggle,
      modifier = Modifier.testTag(tag),
    )
    Text(text = label, style = MaterialTheme.typography.labelSmall)
  }
}
