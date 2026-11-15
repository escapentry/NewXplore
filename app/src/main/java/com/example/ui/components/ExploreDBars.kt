package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.display.ExploreDDisplayProfile
import com.example.files.BrowserTabState
import com.example.files.ClipboardMode
import com.example.files.ExploreDUiState
import com.example.files.FileEntry

@Composable
fun ExploreDTopBar(
  state: ExploreDUiState,
  profile: ExploreDDisplayProfile,
  showCreateMenu: Boolean,
  onToggleCreateMenu: () -> Unit,
  onDismissCreateMenu: () -> Unit,
  onNewFolder: () -> Unit,
  onNewFile: () -> Unit,
  onNewSymlink: () -> Unit = {},
  onSuToggle: () -> Unit,
  onRefresh: () -> Unit,
  onOpenTools: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = profile.horizontalPaddingDp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = stringResource(R.string.app_name),
        fontSize = profile.titleFontSp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
      )

      val suColor = if (state.isSuActive) {
        MaterialTheme.colorScheme.error
      } else {
        MaterialTheme.colorScheme.primary
      }
      Surface(
        onClick = onSuToggle,
        shape = RoundedCornerShape(12.dp),
        color = suColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, suColor.copy(alpha = 0.45f)),
        modifier = Modifier.testTag("su_toggle_chip"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = if (state.isSuActive) Icons.Rounded.Security else Icons.Rounded.Lock,
            contentDescription = if (state.isSuActive) "Lock SU" else "Unlock SU",
            tint = suColor,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = if (state.isSuActive) "SU #" else "SU",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = suColor,
          )
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      Box {
        IconButton(
          onClick = onToggleCreateMenu,
          modifier = Modifier.testTag("create_menu_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Create folder, file, or symlink",
            tint = MaterialTheme.colorScheme.onBackground,
          )
        }
        DropdownMenu(
          expanded = showCreateMenu,
          onDismissRequest = onDismissCreateMenu,
        ) {
          DropdownMenuItem(
            text = { Text("New Folder") },
            leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
            onClick = onNewFolder,
            modifier = Modifier.testTag("menu_new_folder"),
          )
          DropdownMenuItem(
            text = { Text("New File") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null) },
            onClick = onNewFile,
            modifier = Modifier.testTag("menu_new_file"),
          )
          DropdownMenuItem(
            text = { Text("New Symlink (ln -s)") },
            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            onClick = onNewSymlink,
            modifier = Modifier.testTag("menu_new_symlink"),
          )
        }
      }

      IconButton(
        onClick = onRefresh,
        modifier = Modifier.testTag("refresh_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Refresh,
          contentDescription = "Refresh",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      IconButton(
        onClick = onOpenTools,
        modifier = Modifier.testTag("tools_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Tune,
          contentDescription = "Tools",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
fun BrowserTabStrip(
  tabs: List<BrowserTabState>,
  activeTabId: String,
  profile: ExploreDDisplayProfile,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onAddTab: () -> Unit,
) {
  if (tabs.isEmpty()) return

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = profile.horizontalPaddingDp, vertical = 6.dp)
      .testTag("browser_tab_strip"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    tabs.forEachIndexed { idx, tab ->
      val active = tab.id == activeTabId
      Surface(
        onClick = { onSelectTab(tab.id) },
        shape = RoundedCornerShape(12.dp),
        color = if (active) {
          MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
          MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
          width = 1.dp,
          color = if (active) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.outline
          },
        ),
        modifier = Modifier.testTag("browser_tab_$idx"),
      ) {
        Row(
          modifier = Modifier.padding(
            start = 10.dp,
            end = if (tabs.size > 1) 4.dp else 10.dp,
            top = 6.dp,
            bottom = 6.dp,
          ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Tab,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = tab.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (tabs.size > 1) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Close tab",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable { onCloseTab(tab.id) }
                .padding(2.dp),
            )
          }
        }
      }
    }

    Surface(
      onClick = onAddTab,
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.testTag("add_tab_button"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = "New tab",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "Tab",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
fun SystemMountBar(
  state: ExploreDUiState,
  profile: ExploreDDisplayProfile,
  onToggleMount: () -> Unit,
) {
  val mount = state.activeMount
  val isRw = mount?.isReadWrite == true
  val mountPoint = mount?.mountPoint ?: "/"
  val fsType = mount?.fsType?.takeIf { it.isNotBlank() } ?: "sys"
  val tone = if (isRw) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = tone.copy(alpha = 0.11f),
    border = BorderStroke(1.dp, tone.copy(alpha = 0.45f)),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = profile.horizontalPaddingDp, vertical = 4.dp)
      .testTag("system_mount_bar"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = if (isRw) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
          contentDescription = null,
          tint = tone,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "$mountPoint ($fsType) · ${if (isRw) "R/W" else "R/O"}",
          fontFamily = FontFamily.Monospace,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Button(
        onClick = onToggleMount,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = tone,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.testTag("mount_rw_ro_toggle"),
      ) {
        Text(
          text = if (isRw) "Mount R/O" else "Mount R/W",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
fun QuickJumpStrip(
  state: ExploreDUiState,
  profile: ExploreDDisplayProfile,
  onJump: (String) -> Unit,
) {
  val targets = remember(state.internalStoragePath) {
    listOf(
      "Internal" to state.internalStoragePath,
      "/" to "/",
      "/data" to "/data",
      "/system" to "/system",
      "/vendor" to "/vendor",
      "/etc" to "/etc",
    )
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = profile.horizontalPaddingDp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    targets.forEach { (label, path) ->
      val active = state.currentPath == path
      Surface(
        onClick = { onJump(path) },
        shape = AppleSquircleShape(0.32f),
        color = if (active) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
          width = 1.dp,
          color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
          } else {
            MaterialTheme.colorScheme.outline
          },
        ),
        modifier = Modifier.testTag("quick_jump_$label"),
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = if (label.startsWith("/")) FontFamily.Monospace else FontFamily.Default,
          fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
          color = if (active) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }
  }
}

@Composable
fun BreadcrumbAndSearchBar(
  state: ExploreDUiState,
  profile: ExploreDDisplayProfile,
  onNavigateUp: () -> Unit,
  onJumpToPath: (String) -> Unit,
  onOpenGoToPath: () -> Unit = {},
  onSearchChanged: (String) -> Unit,
) {
  val segments = remember(state.currentPath) {
    if (state.currentPath == "/") {
      listOf("/" to "/")
    } else {
      val parts = state.currentPath.trim('/').split('/')
      val acc = mutableListOf("/" to "/")
      var built = ""
      for (part in parts) {
        built += "/$part"
        acc += part to built
      }
      acc
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = profile.horizontalPaddingDp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      IconButton(
        enabled = state.currentPath != "/",
        onClick = onNavigateUp,
        modifier = Modifier
          .size(48.dp)
          .testTag("navigate_up_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = "Up directory",
          tint = if (state.currentPath == "/") {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
          } else {
            MaterialTheme.colorScheme.onBackground
          },
        )
      }

      Row(
        modifier = Modifier
          .weight(1f)
          .horizontalScroll(rememberScrollState())
          .testTag("breadcrumb_row"),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        segments.forEachIndexed { idx, (label, fullPath) ->
          val isLast = idx == segments.lastIndex
          Text(
            text = if (idx == 0) "/" else label,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
            color = if (isLast) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .clickable { onJumpToPath(fullPath) }
              .padding(horizontal = 6.dp, vertical = 6.dp),
          )
          if (!isLast && idx > 0) {
            Text(
              text = "/",
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              fontFamily = FontFamily.Monospace,
            )
          }
        }
      }

      IconButton(
        onClick = onOpenGoToPath,
        modifier = Modifier.testTag("goto_path_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.EditLocationAlt,
          contentDescription = "Go to path",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    OutlinedTextField(
      value = state.searchQuery,
      onValueChange = onSearchChanged,
      placeholder = {
        Text(
          text = stringResource(R.string.search_hint),
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Rounded.Search,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
      },
      trailingIcon = {
        if (state.searchQuery.isNotEmpty()) {
          IconButton(onClick = { onSearchChanged("") }) {
            Icon(Icons.Rounded.Close, contentDescription = "Clear filter")
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp)
        .testTag("search_filter_input"),
    )
  }
}

@Composable
fun StoragePermissionBanner(
  profile: ExploreDDisplayProfile,
  onGrant: () -> Unit,
  onDismiss: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = profile.horizontalPaddingDp, vertical = 4.dp)
      .testTag("storage_permission_banner"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.Storage,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = stringResource(R.string.files_permission_prompt),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.dismiss))
      }
      Button(
        onClick = onGrant,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.testTag("grant_storage_permission_button"),
      ) {
        Text(stringResource(R.string.grant_files_permission))
      }
    }
  }
}

@Composable
fun SelectionBottomBar(
  selectedCount: Int,
  singleSelectedEntry: FileEntry?,
  allowPermissions: Boolean,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onPermissions: (FileEntry) -> Unit,
  onRename: (FileEntry) -> Unit,
  onDelete: () -> Unit,
  onSelectAll: () -> Unit,
  onClear: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("selection_action_bar"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = "$selectedCount",
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 8.dp),
      )
      IconButton(onClick = onCopy, modifier = Modifier.testTag("sel_copy_button")) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
      }
      IconButton(onClick = onMove, modifier = Modifier.testTag("sel_move_button")) {
        Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = "Move")
      }
      if (singleSelectedEntry != null) {
        if (allowPermissions) {
          IconButton(
            onClick = { onPermissions(singleSelectedEntry) },
            modifier = Modifier.testTag("sel_permissions_button"),
          ) {
            Icon(Icons.Rounded.Security, contentDescription = "Permissions")
          }
        }
        IconButton(
          onClick = { onRename(singleSelectedEntry) },
          modifier = Modifier.testTag("sel_rename_button"),
        ) {
          Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Rename")
        }
      }
      IconButton(onClick = onDelete, modifier = Modifier.testTag("sel_delete_button")) {
        Icon(
          Icons.Rounded.Delete,
          contentDescription = "Delete",
          tint = MaterialTheme.colorScheme.error,
        )
      }
      IconButton(onClick = onSelectAll, modifier = Modifier.testTag("sel_all_button")) {
        Icon(Icons.Rounded.SelectAll, contentDescription = "Select all")
      }
      IconButton(onClick = onClear, modifier = Modifier.testTag("sel_clear_button")) {
        Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
      }
    }
  }
}

@Composable
fun ClipboardPasteBar(
  mode: ClipboardMode,
  count: Int,
  onPaste: () -> Unit,
  onCancel: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("clipboard_paste_bar"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.ContentPaste,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = "${if (mode == ClipboardMode.COPY) "Copy" else "Move"} $count item(s)",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onCancel) {
        Text("Cancel")
      }
      Button(
        onClick = onPaste,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.testTag("paste_here_button"),
      ) {
        Text("Paste here")
      }
    }
  }
}
