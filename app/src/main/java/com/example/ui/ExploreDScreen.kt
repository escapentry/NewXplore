package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.display.DisplayAutoScaler
import com.example.files.ClipboardMode
import com.example.files.ExploreDViewModel
import com.example.files.FileEntry
import com.example.system.StoragePermissionHelper
import com.example.ui.components.ApkActionDialog
import com.example.ui.components.ArchiveViewerDialog
import com.example.ui.components.BreadcrumbAndSearchBar
import com.example.ui.components.BrowserTabStrip
import com.example.ui.components.ClipboardPasteBar
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.CreateSymlinkDialog
import com.example.ui.components.ExploreDTopBar
import com.example.ui.components.FileBrowserPane
import com.example.ui.components.FileViewerEditorDialog
import com.example.ui.components.GoToPathDialog
import com.example.ui.components.NameInputDialog
import com.example.ui.components.PermissionsDialog
import com.example.ui.components.QuickJumpStrip
import com.example.ui.components.SelectionBottomBar
import com.example.ui.components.SideInspectorPane
import com.example.ui.components.SqliteViewerDialog
import com.example.ui.components.StoragePermissionBanner
import com.example.ui.components.SuPinDialog
import com.example.ui.components.SystemModifyWarningDialog
import com.example.ui.components.SystemMountBar
import com.example.ui.components.ToolsSheet
import kotlinx.coroutines.delay

@Composable
fun ExploreDScreen(
  viewModel: ExploreDViewModel = viewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var showCreateMenu by remember { mutableStateOf(false) }
  var showCreateFolderDialog by remember { mutableStateOf(false) }
  var showCreateFileDialog by remember { mutableStateOf(false) }
  var symlinkTargetPrefill by remember { mutableStateOf<String?>(null) }
  var showGoToPathDialog by remember { mutableStateOf(false) }
  var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
  var deleteTargets by remember { mutableStateOf<List<String>>(emptyList()) }

  val allFilesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) {
    viewModel.refreshStoragePermissionState()
  }

  val legacyStorageLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    viewModel.refreshStoragePermissionState()
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshStoragePermissionState()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(state.statusMessage) {
    if (state.statusMessage != null) {
      delay(2800L)
      viewModel.clearStatusMessage()
    }
  }

  BackHandler(enabled = state.isSelectionMode || state.currentPath != "/") {
    if (state.isSelectionMode) {
      viewModel.clearSelection()
    } else {
      viewModel.navigateUp()
    }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    val density = LocalDensity.current
    val profile = remember(maxWidth, maxHeight, density.density, density.fontScale) {
      DisplayAutoScaler.computeProfile(
        availableWidthDp = maxWidth.value,
        availableHeightDp = maxHeight.value,
        density = density.density,
        systemFontScale = density.fontScale,
      )
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
              Color.Transparent,
              MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
            ),
          ),
        ),
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
      ExploreDTopBar(
        state = state,
        profile = profile,
        showCreateMenu = showCreateMenu,
        onToggleCreateMenu = { showCreateMenu = !showCreateMenu },
        onDismissCreateMenu = { showCreateMenu = false },
        onNewFolder = {
          showCreateMenu = false
          showCreateFolderDialog = true
        },
        onNewFile = {
          showCreateMenu = false
          showCreateFileDialog = true
        },
        onNewSymlink = {
          showCreateMenu = false
          symlinkTargetPrefill = ""
        },
        onSuToggle = viewModel::onSuToggleClicked,
        onRefresh = viewModel::refreshAll,
        onOpenTools = viewModel::openToolsSheet,
      )

      BrowserTabStrip(
        tabs = state.tabs,
        activeTabId = state.activeTabId,
        profile = profile,
        onSelectTab = viewModel::selectTab,
        onCloseTab = viewModel::closeTab,
        onAddTab = { viewModel.addNewTab() },
      )

      if (state.isLoading) {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.primary,
          trackColor = Color.Transparent,
        )
      }

      QuickJumpStrip(
        state = state,
        profile = profile,
        onJump = viewModel::navigateTo,
      )

      AnimatedVisibility(visible = state.isCurrentSystemPartition) {
        SystemMountBar(
          state = state,
          profile = profile,
          onToggleMount = viewModel::toggleCurrentPartitionMount,
        )
      }

      BreadcrumbAndSearchBar(
        state = state,
        profile = profile,
        onNavigateUp = { viewModel.navigateUp() },
        onJumpToPath = viewModel::navigateTo,
        onOpenGoToPath = { showGoToPathDialog = true },
        onSearchChanged = viewModel::updateSearchQuery,
      )

      AnimatedVisibility(
        visible = !state.hasStoragePermission && !state.permissionBannerDismissed,
      ) {
        StoragePermissionBanner(
          profile = profile,
          onGrant = {
            if (StoragePermissionHelper.requiresAllFilesSettingsFlow()) {
              runCatching {
                allFilesLauncher.launch(StoragePermissionHelper.createManageAllFilesIntent(context))
              }.onFailure {
                runCatching {
                  allFilesLauncher.launch(StoragePermissionHelper.createFallbackAllFilesIntent())
                }.onFailure {
                  context.startActivity(
                    StoragePermissionHelper.createFallbackAllFilesIntent()
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                  )
                }
              }
            } else {
              legacyStorageLauncher.launch(StoragePermissionHelper.legacyStoragePermissions)
            }
          },
          onDismiss = viewModel::dismissPermissionBanner,
        )
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        Row(modifier = Modifier.fillMaxSize()) {
          FileBrowserPane(
            state = state,
            profile = profile,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            onEntryClick = viewModel::onEntryClicked,
            onEntryLongClick = viewModel::onEntryLongPressed,
            onUnlockSu = { viewModel.requestEnableSu() },
            onInspectText = viewModel::openTextViewer,
            onOpenArchive = viewModel::openArchiveViewer,
            onExtractArchive = { entry -> viewModel.extractArchive(entry, intoSubfolder = true) },
            onOpenSqlite = { entry -> viewModel.openSqliteViewer(entry) },
            onPermissions = viewModel::openPermissionsEditor,
            onRename = { renameTarget = it },
            onCopySingle = { viewModel.stageClipboard(ClipboardMode.COPY, setOf(it.path)) },
            onMoveSingle = { viewModel.stageClipboard(ClipboardMode.MOVE, setOf(it.path)) },
            onDeleteSingle = { deleteTargets = listOf(it.path) },
            onInstallApk = viewModel::launchApkInstallerIntent,
            onCreateSymlinkFromEntry = { symlinkTargetPrefill = it.path },
          )

          if (profile.showSideInspector) {
            SideInspectorPane(
              entry = state.inspectedEntry,
              allowPermissions = state.isCurrentSystemPartition,
              modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .padding(end = profile.horizontalPaddingDp, bottom = 12.dp),
              onPermissions = { state.inspectedEntry?.let(viewModel::openPermissionsEditor) },
            )
          }
        }

        androidx.compose.animation.AnimatedVisibility(
          visible = state.statusMessage != null,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
          val msg = state.statusMessage.orEmpty()
          val tone = if (state.statusIsError) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.tertiary
          }
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, tone.copy(alpha = 0.65f)),
            shadowElevation = 6.dp,
            modifier = Modifier.testTag("status_banner"),
          ) {
            Text(
              text = msg,
              color = MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
          }
        }
      }

      if (state.isSelectionMode) {
        SelectionBottomBar(
          selectedCount = state.selectedPaths.size,
          singleSelectedEntry = if (state.selectedPaths.size == 1) {
            state.visibleEntries.firstOrNull { it.path in state.selectedPaths }
              ?: state.rawEntries.firstOrNull { it.path in state.selectedPaths }
          } else {
            null
          },
          allowPermissions = state.isCurrentSystemPartition,
          onCopy = { viewModel.stageClipboard(ClipboardMode.COPY) },
          onMove = { viewModel.stageClipboard(ClipboardMode.MOVE) },
          onPermissions = { entry -> viewModel.openPermissionsEditor(entry) },
          onPermissionsSelected = viewModel::openPermissionsForSelection,
          onRename = { entry -> renameTarget = entry },
          onDelete = { deleteTargets = state.selectedPaths.toList() },
          onSelectAll = viewModel::selectAllVisible,
          onClear = viewModel::clearSelection,
        )
      } else if (state.clipboard != null) {
        ClipboardPasteBar(
          mode = state.clipboard!!.mode,
          count = state.clipboard!!.paths.size,
          onPaste = viewModel::pasteClipboardHere,
          onCancel = viewModel::clearClipboard,
        )
      }
    }
  }

  state.systemWarning?.let { warning ->
    SystemModifyWarningDialog(
      warning = warning,
      isSuActive = state.isSuActive,
      onConfirm = viewModel::confirmSystemModification,
      onDismiss = viewModel::dismissSystemWarning,
    )
  }

  state.pinDialogMode?.let { mode ->
    SuPinDialog(
      mode = mode,
      errorText = state.pinError,
      onCreatePin = viewModel::submitCreatePin,
      onVerifyPin = viewModel::submitUnlockPin,
      onDismiss = viewModel::dismissPinDialog,
    )
  }

  state.permissionsTarget?.let { target ->
    val targets = state.permissionsTargets.ifEmpty { listOf(target) }
    PermissionsDialog(
      entry = target,
      isSuActive = state.isSuActive,
      details = state.permissionsDetails,
      selectedEntries = targets,
      onApply = { octal, recursive, ownerGroup ->
        viewModel.applyPermissionsBatch(targets, octal, recursive, false, ownerGroup, null)
      },
      onApplyWithSeLinux = { octal, recursive, ownerGroup, seCtx ->
        viewModel.applyPermissionsBatch(targets, octal, recursive, false, ownerGroup, seCtx)
      },
      onApplyAdvanced = { octal, recursive, noExecChildFiles, ownerGroup, seCtx ->
        viewModel.applyPermissionsBatch(targets, octal, recursive, noExecChildFiles, ownerGroup, seCtx)
      },
      onDismiss = viewModel::closePermissionsEditor,
    )
  }

  state.textEditor?.let { editor ->
    FileViewerEditorDialog(
      state = editor,
      onSave = { newContent -> viewModel.saveTextFile(editor.entry, newContent) },
      onDismiss = viewModel::closeTextViewer,
    )
  }

  state.apkActionInfo?.let { apkInfo ->
    ApkActionDialog(
      info = apkInfo,
      isSuActive = state.isSuActive,
      onViewContents = { viewModel.exploreApkFromDialog(apkInfo.entry) },
      onInstall = { viewModel.launchApkInstallerIntent(apkInfo.entry) },
      onRootInstall = { viewModel.installApkWithRoot(apkInfo.entry) },
      onDismiss = viewModel::closeApkActionDialog,
    )
  }

  state.archiveViewer?.let { archive ->
    ArchiveViewerDialog(
      state = archive,
      onInspectEntry = viewModel::inspectArchiveEntry,
      onExtractHere = { viewModel.extractArchive(archive.entry, intoSubfolder = false) },
      onExtractSubfolder = { viewModel.extractArchive(archive.entry, intoSubfolder = true) },
      onDismiss = viewModel::closeArchiveViewer,
    )
  }

  state.sqliteViewer?.let { sqlite ->
    SqliteViewerDialog(
      info = sqlite,
      onSelectTable = viewModel::selectSqliteTable,
      onRunSql = viewModel::runSqliteQuery,
      onUpdateCell = viewModel::updateSqliteCell,
      onDeleteRow = viewModel::deleteSqliteRow,
      onCommitToDevice = viewModel::commitSqliteChanges,
      onDismiss = viewModel::closeSqliteViewer,
    )
  }

  if (state.showToolsSheet) {
    ToolsSheet(
      isSuActive = state.isSuActive,
      hasPinConfigured = state.hasPinConfigured,
      showHiddenFiles = state.showHiddenFiles,
      sortOrder = state.sortOrder,
      selinuxMode = state.selinuxMode,
      quickToolOutput = state.quickToolOutput,
      onToggleSu = viewModel::onSuToggleClicked,
      onChangePin = viewModel::openChangePinFlow,
      onToggleSeLinux = viewModel::toggleSeLinuxMode,
      onRunRootCommand = viewModel::runQuickRootCommand,
      onReboot = viewModel::triggerReboot,
      onToggleShowHidden = viewModel::setShowHiddenFiles,
      onChangeSortOrder = viewModel::setSortOrder,
      onDismiss = viewModel::closeToolsSheet,
    )
  }

  if (showCreateFolderDialog) {
    NameInputDialog(
      title = "New Folder",
      label = "Folder name",
      confirmLabel = "Create",
      tagPrefix = "create_folder",
      onConfirm = { name ->
        showCreateFolderDialog = false
        viewModel.createFolder(name)
      },
      onDismiss = { showCreateFolderDialog = false },
    )
  }

  if (showCreateFileDialog) {
    NameInputDialog(
      title = "New File",
      label = "File name",
      confirmLabel = "Create",
      tagPrefix = "create_file",
      onConfirm = { name ->
        showCreateFileDialog = false
        viewModel.createFile(name)
      },
      onDismiss = { showCreateFileDialog = false },
    )
  }

  symlinkTargetPrefill?.let { initialTarget ->
    val suggestedName = initialTarget.substringAfterLast('/', "").let {
      if (it.isNotEmpty()) "${it}_link" else ""
    }
    CreateSymlinkDialog(
      initialTarget = initialTarget,
      initialLinkName = suggestedName,
      onConfirm = { targetPath, linkName ->
        symlinkTargetPrefill = null
        viewModel.createSymlink(targetPath, linkName)
      },
      onDismiss = { symlinkTargetPrefill = null },
    )
  }

  if (showGoToPathDialog) {
    GoToPathDialog(
      initialPath = state.currentPath,
      onConfirm = { path ->
        showGoToPathDialog = false
        viewModel.navigateTo(path)
      },
      onDismiss = { showGoToPathDialog = false },
    )
  }

  renameTarget?.let { target ->
    NameInputDialog(
      title = "Rename",
      label = "New name",
      initialValue = target.name,
      confirmLabel = "Rename",
      tagPrefix = "rename_entry",
      onConfirm = { newName ->
        renameTarget = null
        viewModel.renameEntry(target, newName)
      },
      onDismiss = { renameTarget = null },
    )
  }

  if (deleteTargets.isNotEmpty()) {
    val sample = if (deleteTargets.size == 1) {
      deleteTargets.first().substringAfterLast('/')
    } else {
      null
    }
    ConfirmDeleteDialog(
      count = deleteTargets.size,
      sampleName = sample,
      onConfirm = {
        val toDelete = deleteTargets
        deleteTargets = emptyList()
        viewModel.deletePaths(toDelete)
      },
      onDismiss = { deleteTargets = emptyList() },
    )
  }
}
