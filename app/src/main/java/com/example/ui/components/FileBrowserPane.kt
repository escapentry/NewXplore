package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.display.ExploreDDisplayProfile
import com.example.files.ExploreDParsers
import com.example.files.ExploreDUiState
import com.example.files.FileEntry

@Composable
fun FileBrowserPane(
  state: ExploreDUiState,
  profile: ExploreDDisplayProfile,
  modifier: Modifier = Modifier,
  onEntryClick: (FileEntry) -> Unit,
  onEntryLongClick: (FileEntry) -> Unit,
  onUnlockSu: () -> Unit,
  onInspectText: (FileEntry) -> Unit,
  onOpenArchive: (FileEntry) -> Unit,
  onExtractArchive: (FileEntry) -> Unit,
  onOpenSqlite: (FileEntry) -> Unit,
  onPermissions: (FileEntry) -> Unit,
  onRename: (FileEntry) -> Unit,
  onCopySingle: (FileEntry) -> Unit,
  onMoveSingle: (FileEntry) -> Unit,
  onDeleteSingle: (FileEntry) -> Unit,
  onInstallApk: (FileEntry) -> Unit = {},
  onCreateSymlinkFromEntry: (FileEntry) -> Unit = {},
) {
  val isSystemPartition = state.isCurrentSystemPartition

  when {
    state.isLoading && state.visibleEntries.isEmpty() -> {
      Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      }
    }

    state.accessDenied && state.visibleEntries.isEmpty() -> {
      Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Surface(
          shape = RoundedCornerShape(22.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(36.dp),
            )
            Text(
              text = "Protected System Path",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "${state.currentPath} requires Superuser (su) privileges.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.isSuActive) {
              Button(
                onClick = onUnlockSu,
                modifier = Modifier.testTag("unlock_su_empty_button"),
              ) {
                Text("Unlock SU with PIN")
              }
            }
          }
        }
      }
    }

    state.visibleEntries.isEmpty() -> {
      Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
          )
          Text(
            text = if (state.searchQuery.isNotBlank()) "No matching files" else "Folder is empty",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    else -> {
      LazyColumn(
        modifier = modifier.testTag("file_list"),
        contentPadding = PaddingValues(
          start = profile.horizontalPaddingDp,
          end = profile.horizontalPaddingDp,
          top = 4.dp,
          bottom = 76.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(state.visibleEntries, key = { it.path }) { entry ->
          val allowPermissions = isSystemPartition &&
            ExploreDParsers.isSystemPartitionPath(entry.path, state.internalStoragePath)
          FileEntryRow(
            entry = entry,
            isSelected = entry.path in state.selectedPaths,
            allowPermissions = allowPermissions,
            profile = profile,
            onClick = { onEntryClick(entry) },
            onLongClick = { onEntryLongClick(entry) },
            onInspectText = { onInspectText(entry) },
            onOpenArchive = { onOpenArchive(entry) },
            onExtractArchive = { onExtractArchive(entry) },
            onOpenSqlite = { onOpenSqlite(entry) },
            onPermissions = { onPermissions(entry) },
            onRename = { onRename(entry) },
            onCopy = { onCopySingle(entry) },
            onMove = { onMoveSingle(entry) },
            onDelete = { onDeleteSingle(entry) },
            onInstallApk = { onInstallApk(entry) },
            onCreateSymlink = { onCreateSymlinkFromEntry(entry) },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileEntryRow(
  entry: FileEntry,
  isSelected: Boolean,
  allowPermissions: Boolean,
  profile: ExploreDDisplayProfile,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onInspectText: () -> Unit,
  onOpenArchive: () -> Unit,
  onExtractArchive: () -> Unit,
  onOpenSqlite: () -> Unit,
  onPermissions: () -> Unit,
  onRename: () -> Unit,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onDelete: () -> Unit,
  onInstallApk: () -> Unit,
  onCreateSymlink: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  val isArchive = ExploreDParsers.isArchiveFile(entry)
  val isSqlite = ExploreDParsers.isSqliteFile(entry)

  val iconTint = when {
    entry.isDirectory -> MaterialTheme.colorScheme.primary
    entry.isSymlink -> MaterialTheme.colorScheme.secondary
    isArchive || isSqlite || entry.extension in setOf("sh", "bin", "so") -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (isSelected) {
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
      MaterialTheme.colorScheme.surface
    },
    border = BorderStroke(
      width = 1.dp,
      color = if (isSelected) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.outline
      },
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
      )
      .testTag("file_row_${entry.name}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        shape = AppleSquircleShape(),
        color = iconTint.copy(alpha = 0.14f),
        modifier = Modifier.size(profile.iconBoxSizeDp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = when {
              isSelected -> Icons.Rounded.CheckCircle
              entry.isSymlink -> Icons.Rounded.Link
              entry.isDirectory -> Icons.Rounded.Folder
              isArchive -> Icons.Rounded.FolderZip
              isSqlite -> Icons.Rounded.TableChart
              entry.extension == "sh" -> Icons.Rounded.Terminal
              else -> Icons.Rounded.Description
            },
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (entry.symlinkTarget != null) "${entry.name} → ${entry.symlinkTarget}" else entry.name,
          fontSize = profile.rowTitleFontSp,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        val metaParts = buildList {
          add("${entry.permissionsOctal} ${entry.permissionsSymbolic}")
          if (entry.owner.isNotBlank()) add("${entry.owner}:${entry.group}")
          if (!entry.isDirectory) add(ExploreDParsers.formatBytes(entry.sizeBytes))
          if (entry.modifiedDisplay.isNotBlank()) add(entry.modifiedDisplay)
        }
        Text(
          text = metaParts.joinToString(" · "),
          fontSize = profile.rowMetaFontSp,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Box {
        IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier.testTag("file_menu_${entry.name}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = "File actions",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false },
        ) {
          if (isArchive) {
            if (entry.extension == "apk") {
              DropdownMenuItem(
                text = { Text("Install APK") },
                leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                onClick = {
                  menuExpanded = false
                  onInstallApk()
                },
              )
            }
            DropdownMenuItem(
              text = { Text(if (entry.extension == "apk") "View Contents" else "Inspect Archive") },
              leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onOpenArchive()
              },
            )
            DropdownMenuItem(
              text = { Text("Extract Archive") },
              leadingIcon = { Icon(Icons.Rounded.FolderZip, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onExtractArchive()
              },
            )
          } else if (isSqlite) {
            DropdownMenuItem(
              text = { Text("Open SQLite Viewer") },
              leadingIcon = { Icon(Icons.Rounded.TableChart, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onOpenSqlite()
              },
            )
          } else if (!entry.isDirectory) {
            DropdownMenuItem(
              text = { Text("Edit Text") },
              leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onInspectText()
              },
            )
          }

          if (allowPermissions) {
            DropdownMenuItem(
              text = { Text("Permissions") },
              leadingIcon = { Icon(Icons.Rounded.Security, contentDescription = null) },
              onClick = {
                menuExpanded = false
                onPermissions()
              },
              modifier = Modifier.testTag("action_permissions_${entry.name}"),
            )
          }
          DropdownMenuItem(
            text = { Text("Create Symlink") },
            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onCreateSymlink()
            },
          )
          DropdownMenuItem(
            text = { Text("Copy") },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onCopy()
            },
          )
          DropdownMenuItem(
            text = { Text("Move") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onMove()
            },
          )
          DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onRename()
            },
          )
          HorizontalDivider()
          DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
              Icon(
                Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
              )
            },
            onClick = {
              menuExpanded = false
              onDelete()
            },
          )
        }
      }
    }
  }
}

@Composable
fun SideInspectorPane(
  entry: FileEntry?,
  allowPermissions: Boolean,
  modifier: Modifier = Modifier,
  onPermissions: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Inspector",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      if (entry != null) {
        Text(
          text = entry.name,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "Path: ${entry.path}\nMode: ${entry.permissionsOctal} (${entry.permissionsSymbolic})\nOwner: ${entry.owner}:${entry.group}\nSize: ${ExploreDParsers.formatBytes(entry.sizeBytes)}",
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (allowPermissions) {
          Button(onClick = onPermissions, modifier = Modifier.fillMaxWidth()) {
            Text("Permissions")
          }
        }
      } else {
        Text(
          text = "Long-press an item to inspect its details or select multiple files.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
