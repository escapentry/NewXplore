package com.example.files

import android.app.Application
import android.content.Intent
import android.os.Environment
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PinPreferences
import com.example.system.StoragePermissionHelper
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TextEditorState(
  val entry: FileEntry,
  val content: String,
  val isSaving: Boolean = false,
  val isBinaryXml: Boolean = false,
  val isReadOnly: Boolean = false,
  val isSystemFile: Boolean = false,
)

@Immutable
data class ArchiveViewerState(
  val entry: FileEntry,
  val formatLabel: String,
  val items: List<ArchiveEntryItem>,
  val isExtracting: Boolean = false,
  val error: String? = null,
)

sealed interface PendingSystemAction {
  data class CreateFolder(val parentDir: String, val name: String) : PendingSystemAction
  data class CreateFile(val parentDir: String, val name: String) : PendingSystemAction
  data class CreateSymlink(val targetPath: String, val linkPath: String) : PendingSystemAction
  data class Rename(val entry: FileEntry, val newName: String) : PendingSystemAction
  data class Paste(val clip: ClipboardState, val targetDir: String) : PendingSystemAction
  data class Delete(val paths: List<String>) : PendingSystemAction
  data class ApplyPermissions(
    val entry: FileEntry,
    val octal: String,
    val recursive: Boolean,
    val ownerGroup: String?,
    val seLinuxContext: String?,
  ) : PendingSystemAction
  data class SaveText(val entry: FileEntry, val content: String) : PendingSystemAction
  data class ExtractArchive(val entry: FileEntry, val targetDir: String) : PendingSystemAction
  data class CommitSqlite(val info: SqliteDatabaseInfo) : PendingSystemAction
}

@Immutable
data class SystemModificationWarning(
  val actionLabel: String,
  val targetPath: String,
  val action: PendingSystemAction,
)

enum class PinDialogMode {
  CREATE,
  UNLOCK,
  CHANGE_CURRENT,
  CHANGE_NEW,
}

@Immutable
data class ExploreDUiState(
  val tabs: List<BrowserTabState> = emptyList(),
  val activeTabId: String = "",
  val currentPath: String = "/",
  val internalStoragePath: String = "/storage/emulated/0",
  val rawEntries: List<FileEntry> = emptyList(),
  val visibleEntries: List<FileEntry> = emptyList(),
  val searchQuery: String = "",
  val isLoading: Boolean = false,
  val accessDenied: Boolean = false,
  val isSuActive: Boolean = false,
  val suAvailable: Boolean = true,
  val hasPinConfigured: Boolean = false,
  val showHiddenFiles: Boolean = true,
  val sortOrder: SortOrder = SortOrder.NAME,
  val selectedPaths: Set<String> = emptySet(),
  val inspectedEntry: FileEntry? = null,
  val clipboard: ClipboardState? = null,
  val mounts: List<MountPartition> = emptyList(),
  val activeMount: MountPartition? = null,
  val hasStoragePermission: Boolean = true,
  val permissionBannerDismissed: Boolean = false,
  val pinDialogMode: PinDialogMode? = null,
  val pinError: String? = null,
  val permissionsTarget: FileEntry? = null,
  val permissionsDetails: FilePropertiesDetails? = null,
  val textEditor: TextEditorState? = null,
  val archiveViewer: ArchiveViewerState? = null,
  val sqliteViewer: SqliteDatabaseInfo? = null,
  val apkActionInfo: ApkInspectorInfo? = null,
  val systemWarning: SystemModificationWarning? = null,
  val showToolsSheet: Boolean = false,
  val selinuxMode: String = "Unknown",
  val quickToolOutput: String? = null,
  val statusMessage: String? = null,
  val statusIsError: Boolean = false,
) {
  val isSelectionMode: Boolean
    get() = selectedPaths.isNotEmpty()

  val isCurrentSystemPartition: Boolean
    get() = ExploreDParsers.isSystemPartitionPath(currentPath, internalStoragePath)
}

class ExploreDViewModel(application: Application) : AndroidViewModel(application) {

  private val pinPrefs = PinPreferences(application)
  private val shellManager = RootShellManager(viewModelScope)

  private val internalStorageRoot: String = runCatching {
    Environment.getExternalStorageDirectory().absolutePath
  }.getOrDefault("/storage/emulated/0")

  private val initialTab1Path: String = internalStorageRoot.takeIf { File(it).exists() } ?: "/"
  private val initialTabs = listOf(
    BrowserTabState(id = "tab_1", currentPath = initialTab1Path),
    BrowserTabState(id = "tab_2", currentPath = "/"),
  )

  private val _uiState = MutableStateFlow(
    ExploreDUiState(
      tabs = initialTabs,
      activeTabId = "tab_1",
      currentPath = initialTab1Path,
      internalStoragePath = internalStorageRoot,
      suAvailable = RootShellManager.resolveSuBinary() != null,
      hasStoragePermission = StoragePermissionHelper.hasFilesPermission(application),
    ),
  )
  val uiState: StateFlow<ExploreDUiState> = _uiState.asStateFlow()

  private var pendingPostSuAction: (suspend () -> Unit)? = null
  private var activeTempInspectorFile: File? = null
  private var activeArchiveSourceFile: File? = null

  init {
    viewModelScope.launch {
      pinPrefs.settingsFlow.collect { settings ->
        _uiState.update { state ->
          val updatedTabs = state.tabs.map { tab ->
            tab.copy(
              visibleEntries = filterAndSort(
                entries = tab.rawEntries,
                query = tab.searchQuery,
                showHidden = settings.showHiddenFiles,
                sortOrder = settings.sortOrder,
              ),
            )
          }
          syncActiveTab(
            state.copy(
              tabs = updatedTabs,
              hasPinConfigured = settings.hasPin,
              showHiddenFiles = settings.showHiddenFiles,
              sortOrder = settings.sortOrder,
            ),
          )
        }
      }
    }
    refreshAll()
  }

  override fun onCleared() {
    activeTempInspectorFile?.delete()
    activeArchiveSourceFile?.delete()
    shellManager.close()
    super.onCleared()
  }

  private fun syncActiveTab(state: ExploreDUiState): ExploreDUiState {
    val active = state.tabs.firstOrNull { it.id == state.activeTabId } ?: state.tabs.firstOrNull()
    val path = active?.currentPath ?: "/"
    val activeMount = shellManager.resolveMountForPath(path, state.mounts)
    return state.copy(
      activeTabId = active?.id ?: "",
      currentPath = path,
      rawEntries = active?.rawEntries ?: emptyList(),
      visibleEntries = active?.visibleEntries ?: emptyList(),
      searchQuery = active?.searchQuery ?: "",
      isLoading = active?.isLoading ?: false,
      accessDenied = active?.accessDenied ?: false,
      selectedPaths = active?.selectedPaths ?: emptySet(),
      inspectedEntry = active?.inspectedEntry,
      activeMount = activeMount,
    )
  }

  private fun updateTab(
    state: ExploreDUiState,
    tabId: String = state.activeTabId,
    transform: (BrowserTabState) -> BrowserTabState,
  ): ExploreDUiState {
    val nextTabs = state.tabs.map { tab ->
      if (tab.id == tabId) transform(tab) else tab
    }
    return syncActiveTab(state.copy(tabs = nextTabs))
  }

  // --- Multi-Tab Management ---

  fun addNewTab(initialPath: String? = null) {
    val targetPath = normalizePath(initialPath ?: _uiState.value.currentPath)
    val newId = "tab_${UUID.randomUUID().toString().take(8)}"
    val newTab = BrowserTabState(id = newId, currentPath = targetPath)
    _uiState.update { state ->
      syncActiveTab(
        state.copy(
          tabs = state.tabs + newTab,
          activeTabId = newId,
        ),
      )
    }
    refreshDirectory(targetPath, newId)
  }

  fun selectTab(tabId: String) {
    val currentState = _uiState.value
    if (currentState.activeTabId == tabId) return
    val targetTab = currentState.tabs.firstOrNull { it.id == tabId } ?: return
    _uiState.update { state ->
      syncActiveTab(state.copy(activeTabId = tabId))
    }
    if (targetTab.rawEntries.isEmpty() && !targetTab.isLoading) {
      refreshDirectory(targetTab.currentPath, tabId)
    }
  }

  fun closeTab(tabId: String) {
    _uiState.update { state ->
      if (state.tabs.size <= 1) return@update state
      val idx = state.tabs.indexOfFirst { it.id == tabId }
      if (idx < 0) return@update state
      val nextTabs = state.tabs.filterNot { it.id == tabId }
      val nextActiveId = if (state.activeTabId == tabId) {
        nextTabs[minOf(idx, nextTabs.lastIndex)].id
      } else {
        state.activeTabId
      }
      syncActiveTab(state.copy(tabs = nextTabs, activeTabId = nextActiveId))
    }
  }

  fun refreshStoragePermissionState() {
    val granted = StoragePermissionHelper.hasFilesPermission(getApplication())
    _uiState.update { it.copy(hasStoragePermission = granted) }
    if (granted) refreshDirectory()
  }

  fun dismissPermissionBanner() {
    _uiState.update { it.copy(permissionBannerDismissed = true) }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null, statusIsError = false) }
  }

  fun refreshAll() {
    refreshDirectory()
    refreshMounts()
  }

  fun navigateTo(path: String) {
    val clean = normalizePath(path)
    val tabId = _uiState.value.activeTabId
    _uiState.update { state ->
      updateTab(state, tabId) { tab ->
        tab.copy(
          currentPath = clean,
          selectedPaths = emptySet(),
          inspectedEntry = null,
          searchQuery = "",
        )
      }
    }
    refreshDirectory(clean, tabId)
  }

  fun navigateUp(): Boolean {
    val current = _uiState.value.currentPath
    if (current == "/" || current.isEmpty()) return false
    val parent = File(current).parent ?: "/"
    navigateTo(parent)
    return true
  }

  fun updateSearchQuery(query: String) {
    _uiState.update { state ->
      updateTab(state) { tab ->
        tab.copy(
          searchQuery = query,
          visibleEntries = filterAndSort(
            entries = tab.rawEntries,
            query = query,
            showHidden = state.showHiddenFiles,
            sortOrder = state.sortOrder,
          ),
        )
      }
    }
  }

  fun refreshDirectory(
    targetPath: String = _uiState.value.currentPath,
    tabId: String = _uiState.value.activeTabId,
  ) {
    viewModelScope.launch {
      _uiState.update { state ->
        updateTab(state, tabId) { it.copy(isLoading = true) }
      }
      val stateSnapshot = _uiState.value
      val result = shellManager.listDirectory(
        path = targetPath,
        useSu = stateSnapshot.isSuActive,
        showHidden = stateSnapshot.showHiddenFiles,
      )
      _uiState.update { latest ->
        val tab = latest.tabs.firstOrNull { it.id == tabId } ?: return@update latest
        if (tab.currentPath != targetPath) {
          return@update updateTab(latest, tabId) { it.copy(isLoading = false) }
        }
        val filtered = filterAndSort(
          entries = result.entries,
          query = tab.searchQuery,
          showHidden = latest.showHiddenFiles,
          sortOrder = latest.sortOrder,
        )
        updateTab(latest, tabId) {
          it.copy(
            rawEntries = result.entries,
            visibleEntries = filtered,
            isLoading = false,
            accessDenied = result.accessDenied,
          )
        }
      }
    }
  }

  private fun refreshMounts() {
    viewModelScope.launch {
      val mounts = shellManager.readMounts(_uiState.value.isSuActive)
      _uiState.update { state ->
        syncActiveTab(state.copy(mounts = mounts))
      }
    }
  }

  fun toggleCurrentPartitionMount() {
    val state = _uiState.value
    val mount = state.activeMount ?: MountPartition("", "/", "", isReadWrite = false, rawOptions = "")
    val targetRw = !mount.isReadWrite
    if (!state.isSuActive) {
      requestEnableSu {
        performRemount(mount.mountPoint, targetRw)
      }
      return
    }
    viewModelScope.launch {
      performRemount(mount.mountPoint, targetRw)
    }
  }

  private suspend fun performRemount(mountPoint: String, readWrite: Boolean) {
    val res = shellManager.remountPartition(mountPoint, readWrite)
    val refreshedMounts = shellManager.readMounts(useSu = true)
    _uiState.update { state ->
      syncActiveTab(
        state.copy(
          mounts = refreshedMounts,
          statusMessage = res.message,
          statusIsError = !res.success,
        ),
      )
    }
  }

  fun onEntryClicked(entry: FileEntry) {
    val current = _uiState.value
    if (current.isSelectionMode) {
      toggleSelection(entry.path)
      return
    }
    when {
      entry.isDirectory -> navigateTo(entry.path)
      entry.extension == "apk" -> openApkActionDialog(entry)
      ExploreDParsers.isArchiveFile(entry) -> openArchiveViewer(entry)
      ExploreDParsers.isSqliteFile(entry) -> openSqliteViewer(entry)
      ExploreDParsers.isTextInspectable(entry) -> openTextViewer(entry)
      else -> _uiState.update { state ->
        updateTab(state) { it.copy(inspectedEntry = entry) }
      }
    }
  }

  fun onEntryLongPressed(entry: FileEntry) {
    toggleSelection(entry.path)
    _uiState.update { state ->
      updateTab(state) { it.copy(inspectedEntry = entry) }
    }
  }

  fun toggleSelection(path: String) {
    _uiState.update { state ->
      updateTab(state) { tab ->
        val next = tab.selectedPaths.toMutableSet()
        if (!next.add(path)) next.remove(path)
        tab.copy(selectedPaths = next)
      }
    }
  }

  fun clearSelection() {
    _uiState.update { state ->
      updateTab(state) { it.copy(selectedPaths = emptySet()) }
    }
  }

  fun selectAllVisible() {
    _uiState.update { state ->
      updateTab(state) { tab ->
        tab.copy(selectedPaths = tab.visibleEntries.map { it.path }.toSet())
      }
    }
  }

  // --- PIN-Protected SU Flow (no clearing once set) ---

  fun onSuToggleClicked() {
    if (_uiState.value.isSuActive) lockSu() else requestEnableSu()
  }

  fun requestEnableSu(afterUnlock: (suspend () -> Unit)? = null) {
    pendingPostSuAction = afterUnlock
    viewModelScope.launch {
      val hasPin = pinPrefs.hasPin()
      _uiState.update {
        it.copy(
          hasPinConfigured = hasPin,
          pinError = null,
          pinDialogMode = if (hasPin) PinDialogMode.UNLOCK else PinDialogMode.CREATE,
        )
      }
    }
  }

  fun dismissPinDialog() {
    pendingPostSuAction = null
    _uiState.update { it.copy(pinDialogMode = null, pinError = null) }
  }

  fun submitCreatePin(pin: String, confirmPin: String) {
    val clean = pin.trim()
    if (clean.length !in 4..8 || !clean.all { it.isDigit() }) {
      _uiState.update { it.copy(pinError = "PIN must be 4–8 digits") }
      return
    }
    if (clean != confirmPin.trim()) {
      _uiState.update { it.copy(pinError = "PINs do not match") }
      return
    }
    viewModelScope.launch {
      pinPrefs.setPin(clean)
      val wasChangeFlow = _uiState.value.pinDialogMode == PinDialogMode.CHANGE_NEW
      _uiState.update {
        it.copy(
          hasPinConfigured = true,
          pinDialogMode = null,
          pinError = null,
        )
      }
      if (wasChangeFlow) {
        setStatus("SU PIN updated", isError = false)
      } else {
        performSuElevation()
      }
    }
  }

  fun submitUnlockPin(pin: String) {
    val clean = pin.trim()
    if (clean.isEmpty()) {
      _uiState.update { it.copy(pinError = "Enter your PIN") }
      return
    }
    viewModelScope.launch {
      val valid = pinPrefs.verifyPin(clean)
      if (!valid) {
        _uiState.update { it.copy(pinError = "Incorrect PIN") }
        return@launch
      }
      val mode = _uiState.value.pinDialogMode
      if (mode == PinDialogMode.CHANGE_CURRENT) {
        _uiState.update {
          it.copy(
            pinDialogMode = PinDialogMode.CHANGE_NEW,
            pinError = null,
          )
        }
        return@launch
      }
      _uiState.update { it.copy(pinDialogMode = null, pinError = null) }
      performSuElevation()
    }
  }

  private suspend fun performSuElevation() {
    val result = shellManager.enableSu()
    if (!result.success) {
      pendingPostSuAction = null
      _uiState.update {
        it.copy(
          isSuActive = false,
          statusMessage = result.message,
          statusIsError = true,
        )
      }
      return
    }
    _uiState.update {
      it.copy(
        isSuActive = true,
        statusMessage = "Superuser unlocked",
        statusIsError = false,
      )
    }
    refreshAll()
    val post = pendingPostSuAction
    pendingPostSuAction = null
    post?.invoke()
  }

  fun lockSu() {
    viewModelScope.launch {
      shellManager.disableSu()
      _uiState.update {
        it.copy(
          isSuActive = false,
          statusMessage = "SU locked",
          statusIsError = false,
        )
      }
      refreshAll()
    }
  }

  fun openChangePinFlow() {
    viewModelScope.launch {
      val hasPin = pinPrefs.hasPin()
      _uiState.update {
        it.copy(
          showToolsSheet = false,
          pinError = null,
          pinDialogMode = if (hasPin) PinDialogMode.CHANGE_CURRENT else PinDialogMode.CREATE,
        )
      }
    }
  }

  // --- Protected System Modification Gate + Auto-Mount ---

  private fun isPathProtected(path: String): Boolean =
    ExploreDParsers.isSystemPartitionPath(path, internalStorageRoot)

  private fun gateOrExecute(
    actionLabel: String,
    affectedPaths: List<String>,
    action: PendingSystemAction,
  ) {
    val protectedTarget = affectedPaths.firstOrNull { isPathProtected(it) }
    if (protectedTarget != null) {
      _uiState.update {
        it.copy(
          systemWarning = SystemModificationWarning(
            actionLabel = actionLabel,
            targetPath = protectedTarget,
            action = action,
          ),
        )
      }
      return
    }
    viewModelScope.launch {
      executeAction(action, autoMountPaths = emptyList())
    }
  }

  fun dismissSystemWarning() {
    _uiState.update { it.copy(systemWarning = null) }
  }

  fun confirmSystemModification() {
    val warning = _uiState.value.systemWarning ?: return
    _uiState.update { it.copy(systemWarning = null) }
    val affected = collectAffectedPaths(warning.action)
    if (!_uiState.value.isSuActive) {
      requestEnableSu {
        executeAction(warning.action, autoMountPaths = affected)
      }
      return
    }
    viewModelScope.launch {
      executeAction(warning.action, autoMountPaths = affected)
    }
  }

  private fun collectAffectedPaths(action: PendingSystemAction): List<String> = when (action) {
    is PendingSystemAction.CreateFolder -> listOf(action.parentDir)
    is PendingSystemAction.CreateFile -> listOf(action.parentDir)
    is PendingSystemAction.CreateSymlink -> listOf(File(action.linkPath).parent ?: action.linkPath)
    is PendingSystemAction.Rename -> listOf(action.entry.path)
    is PendingSystemAction.Paste -> {
      if (action.clip.mode == ClipboardMode.MOVE) {
        listOf(action.targetDir) + action.clip.paths
      } else {
        listOf(action.targetDir)
      }
    }
    is PendingSystemAction.Delete -> action.paths
    is PendingSystemAction.ApplyPermissions -> listOf(action.entry.path)
    is PendingSystemAction.SaveText -> listOf(action.entry.path)
    is PendingSystemAction.ExtractArchive -> listOf(action.targetDir)
    is PendingSystemAction.CommitSqlite -> listOf(action.info.entry.path)
  }

  private suspend fun executeAction(
    action: PendingSystemAction,
    autoMountPaths: List<String>,
  ) {
    val useSu = _uiState.value.isSuActive
    if (useSu && autoMountPaths.isNotEmpty()) {
      val mountRes = shellManager.ensureWritableMounts(autoMountPaths, _uiState.value.mounts)
      if (!mountRes.success) {
        setStatus(mountRes.message, isError = true)
        return
      }
      refreshMounts()
    }

    when (action) {
      is PendingSystemAction.CreateFolder -> {
        val res = shellManager.createFolder(action.parentDir, action.name, useSu)
        setStatus(res.message, isError = !res.success)
        if (res.success) refreshDirectory()
      }

      is PendingSystemAction.CreateFile -> {
        val res = shellManager.createFile(action.parentDir, action.name, useSu)
        setStatus(res.message, isError = !res.success)
        if (res.success) refreshDirectory()
      }

      is PendingSystemAction.CreateSymlink -> {
        val res = shellManager.createSymlink(action.targetPath, action.linkPath, useSu)
        setStatus(res.message, isError = !res.success)
        if (res.success) refreshDirectory()
      }

      is PendingSystemAction.Rename -> {
        val res = shellManager.renameEntry(action.entry.path, action.newName, useSu)
        setStatus(res.message, isError = !res.success)
        if (res.success) {
          clearSelection()
          refreshDirectory()
        }
      }

      is PendingSystemAction.Paste -> {
        val res = when (action.clip.mode) {
          ClipboardMode.COPY -> shellManager.copyEntries(action.clip.paths, action.targetDir, useSu)
          ClipboardMode.MOVE -> shellManager.moveEntries(action.clip.paths, action.targetDir, useSu)
        }
        setStatus(res.message, isError = !res.success)
        if (res.success) {
          if (action.clip.mode == ClipboardMode.MOVE) {
            _uiState.update { it.copy(clipboard = null) }
          }
          refreshDirectory()
        }
      }

      is PendingSystemAction.Delete -> {
        val res = shellManager.deleteEntries(action.paths, useSu)
        setStatus(res.message, isError = !res.success)
        if (res.success) {
          _uiState.update { state ->
            updateTab(state) { it.copy(selectedPaths = emptySet(), inspectedEntry = null) }
          }
          refreshDirectory()
        }
      }

      is PendingSystemAction.ApplyPermissions -> {
        val res = shellManager.changePermissions(action.entry.path, action.octal, action.recursive, useSu)
        val notes = mutableListOf<String>()
        var isErr = !res.success
        if (res.success) {
          notes += "Mode ${action.octal}"
          if (!action.ownerGroup.isNullOrBlank() && useSu &&
            action.ownerGroup != "${action.entry.owner}:${action.entry.group}"
          ) {
            val chownRes = shellManager.changeOwnership(action.entry.path, action.ownerGroup, action.recursive)
            if (!chownRes.success) {
              notes += "chown failed: ${chownRes.message}"
              isErr = true
            } else {
              notes += "Owner ${action.ownerGroup}"
            }
          }
          val initialCtx = _uiState.value.permissionsDetails?.seLinuxContext.orEmpty()
          if (!action.seLinuxContext.isNullOrBlank() && useSu && action.seLinuxContext != initialCtx) {
            val chconRes = shellManager.changeSeLinuxContext(action.entry.path, action.seLinuxContext, action.recursive)
            if (!chconRes.success) {
              notes += "chcon failed: ${chconRes.message}"
              isErr = true
            } else {
              notes += "SELinux updated"
            }
          }
        }
        val finalMsg = if (notes.isEmpty()) res.message else notes.joinToString(" · ")
        _uiState.update { it.copy(permissionsTarget = null, permissionsDetails = null) }
        setStatus(finalMsg, isError = isErr)
        refreshDirectory()
      }

      is PendingSystemAction.SaveText -> {
        _uiState.update { it.copy(textEditor = it.textEditor?.copy(isSaving = true)) }
        val backupNeeded = isPathProtected(action.entry.path)
        val res = shellManager.writeTextFile(
          path = action.entry.path,
          content = action.content,
          useSu = useSu,
          createBackup = backupNeeded,
        )
        _uiState.update {
          it.copy(textEditor = if (res.success) null else it.textEditor?.copy(isSaving = false))
        }
        setStatus(res.message, isError = !res.success)
        if (res.success) refreshDirectory()
      }

      is PendingSystemAction.ExtractArchive -> {
        performArchiveExtraction(action.entry, action.targetDir, useSu)
      }

      is PendingSystemAction.CommitSqlite -> {
        val tempPath = action.info.tempCopyPath
        if (tempPath == null) {
          setStatus("Database already saved directly", isError = false)
          return
        }
        _uiState.update { it.copy(sqliteViewer = it.sqliteViewer?.copy(isSavingBack = true)) }
        val res = shellManager.writeFileFromLocalTemp(
          tempFile = File(tempPath),
          destPath = action.info.entry.path,
          useSu = useSu,
          createBackup = true,
        )
        _uiState.update {
          it.copy(
            sqliteViewer = it.sqliteViewer?.copy(
              isSavingBack = false,
              hasPendingChanges = if (res.success) false else it.sqliteViewer?.hasPendingChanges ?: false,
              queryResultMessage = if (res.success) res.message else null,
              error = if (res.success) null else res.message,
            ),
          )
        }
        setStatus(res.message, isError = !res.success)
        if (res.success) refreshDirectory()
      }
    }
  }

  // --- File Operations ---

  fun createFolder(name: String) {
    val clean = name.trim()
    if (clean.isEmpty() || clean.contains('/')) {
      setStatus("Invalid folder name", isError = true)
      return
    }
    val parent = _uiState.value.currentPath
    gateOrExecute("Create folder \"$clean\"", listOf(parent), PendingSystemAction.CreateFolder(parent, clean))
  }

  fun createFile(name: String) {
    val clean = name.trim()
    if (clean.isEmpty() || clean.contains('/')) {
      setStatus("Invalid file name", isError = true)
      return
    }
    val parent = _uiState.value.currentPath
    gateOrExecute("Create file \"$clean\"", listOf(parent), PendingSystemAction.CreateFile(parent, clean))
  }

  fun createSymlink(targetPath: String, linkName: String) {
    val cleanTarget = targetPath.trim()
    val cleanName = linkName.trim()
    if (cleanTarget.isEmpty() || cleanName.isEmpty()) {
      setStatus("Target and link name are required", isError = true)
      return
    }
    val linkPath = if (cleanName.startsWith("/")) {
      normalizePath(cleanName)
    } else {
      File(_uiState.value.currentPath, cleanName).absolutePath
    }
    val parentDir = File(linkPath).parent ?: _uiState.value.currentPath
    gateOrExecute(
      "Create symlink \"${File(linkPath).name}\"",
      listOf(parentDir),
      PendingSystemAction.CreateSymlink(cleanTarget, linkPath),
    )
  }

  fun renameEntry(entry: FileEntry, newName: String) {
    val clean = newName.trim()
    if (clean.isEmpty() || clean.contains('/')) {
      setStatus("Invalid name", isError = true)
      return
    }
    gateOrExecute("Rename \"${entry.name}\"", listOf(entry.path), PendingSystemAction.Rename(entry, clean))
  }

  fun stageClipboard(mode: ClipboardMode, paths: Set<String> = _uiState.value.selectedPaths) {
    if (paths.isEmpty()) return
    val currentDir = _uiState.value.currentPath
    _uiState.update { state ->
      val cleared = updateTab(state) { it.copy(selectedPaths = emptySet()) }
      cleared.copy(
        clipboard = ClipboardState(mode = mode, paths = paths, sourceDir = currentDir),
        statusMessage = if (mode == ClipboardMode.COPY) {
          "${paths.size} item(s) ready to copy"
        } else {
          "${paths.size} item(s) ready to move"
        },
        statusIsError = false,
      )
    }
  }

  fun clearClipboard() {
    _uiState.update { it.copy(clipboard = null) }
  }

  fun pasteClipboardHere() {
    val clip = _uiState.value.clipboard ?: return
    val targetDir = _uiState.value.currentPath
    val pathsToCheck = if (clip.mode == ClipboardMode.MOVE) {
      listOf(targetDir) + clip.paths
    } else {
      listOf(targetDir)
    }
    val label = if (clip.mode == ClipboardMode.COPY) "Copy ${clip.paths.size} item(s)" else "Move ${clip.paths.size} item(s)"
    gateOrExecute(label, pathsToCheck, PendingSystemAction.Paste(clip, targetDir))
  }

  fun deletePaths(paths: Collection<String>) {
    val list = paths.toList()
    if (list.isEmpty()) return
    gateOrExecute("Delete ${list.size} item(s)", list, PendingSystemAction.Delete(list))
  }

  // --- Permissions, Ownership, SELinux ---

  fun openPermissionsEditor(entry: FileEntry) {
    if (!ExploreDParsers.isSystemPartitionPath(entry.path, internalStorageRoot)) {
      setStatus("Permissions editor is only enabled on system partitions", isError = false)
      return
    }
    _uiState.update {
      it.copy(
        permissionsTarget = entry,
        permissionsDetails = FilePropertiesDetails(entry = entry, isLoading = true),
      )
    }
    viewModelScope.launch {
      val (seCtx, resolvedLink) = shellManager.getFileSecurityDetails(entry.path, _uiState.value.isSuActive)
      _uiState.update { state ->
        if (state.permissionsTarget?.path != entry.path) return@update state
        state.copy(
          permissionsDetails = FilePropertiesDetails(
            entry = entry,
            seLinuxContext = seCtx,
            resolvedLinkTarget = resolvedLink,
            isLoading = false,
          ),
        )
      }
    }
  }

  fun closePermissionsEditor() {
    _uiState.update { it.copy(permissionsTarget = null, permissionsDetails = null) }
  }

  fun applyPermissions(
    entry: FileEntry,
    octal: String,
    recursive: Boolean,
    ownerGroup: String?,
    seLinuxContext: String? = null,
  ) {
    gateOrExecute(
      "Change permissions to $octal",
      listOf(entry.path),
      PendingSystemAction.ApplyPermissions(entry, octal, recursive, ownerGroup, seLinuxContext),
    )
  }

  // --- Text & Binary XML Editor ---

  fun openTextViewer(entry: FileEntry) {
    viewModelScope.launch {
      val res = shellManager.readFileBytes(entry.path, _uiState.value.isSuActive)
      res.onSuccess { bytes ->
        val isBinXml = BinaryXmlParser.isBinaryXml(bytes)
        val content = if (isBinXml) {
          BinaryXmlParser.decodeToXmlString(bytes).getOrElse { bytes.toString(Charsets.UTF_8) }
        } else {
          bytes.toString(Charsets.UTF_8)
        }
        _uiState.update {
          it.copy(
            textEditor = TextEditorState(
              entry = entry,
              content = content,
              isBinaryXml = isBinXml,
              isReadOnly = isBinXml,
              isSystemFile = isPathProtected(entry.path),
            ),
          )
        }
      }.onFailure { err ->
        setStatus(err.message ?: "Unable to open ${entry.name}", isError = true)
      }
    }
  }

  fun closeTextViewer() {
    _uiState.update { it.copy(textEditor = null) }
  }

  fun saveTextFile(entry: FileEntry, newContent: String) {
    gateOrExecute(
      "Modify file \"${entry.name}\"",
      listOf(entry.path),
      PendingSystemAction.SaveText(entry, newContent),
    )
  }

  // --- APK Action Dialog & Installer ---

  fun openApkActionDialog(entry: FileEntry) {
    viewModelScope.launch {
      val localFile = resolveReadableLocalFile(entry, _uiState.value.isSuActive)
      val info = if (localFile != null) {
        ArchiveAndSqliteHelper.inspectApkMetadata(getApplication(), localFile, entry)
      } else {
        ApkInspectorInfo(entry, entry.name.removeSuffix(".apk"), "Protected APK (unlock SU to inspect)", "—", 0L, null, null)
      }
      _uiState.update { it.copy(apkActionInfo = info) }
    }
  }

  fun closeApkActionDialog() {
    _uiState.update { it.copy(apkActionInfo = null) }
  }

  fun exploreApkFromDialog(entry: FileEntry) {
    _uiState.update { it.copy(apkActionInfo = null) }
    openArchiveViewer(entry)
  }

  fun launchApkInstallerIntent(entry: FileEntry) {
    viewModelScope.launch {
      val app = getApplication<Application>()
      val readable = resolveReadableLocalFile(entry, _uiState.value.isSuActive)
      if (readable == null) {
        setStatus("Unable to read APK; unlock SU to install", isError = true)
        return@launch
      }
      val uriResult = runCatching {
        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", readable)
      }
      uriResult.onSuccess { uri ->
        val intent = Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, "application/vnd.android.package-archive")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
          app.startActivity(intent)
          _uiState.update { it.copy(apkActionInfo = null) }
        }.onFailure { err ->
          setStatus(err.message ?: "No package installer available", isError = true)
        }
      }.onFailure { err ->
        setStatus(err.message ?: "Failed to prepare APK URI", isError = true)
      }
    }
  }

  fun installApkWithRoot(entry: FileEntry) {
    viewModelScope.launch {
      val res = shellManager.installApkWithRoot(entry.path)
      if (res.success) _uiState.update { it.copy(apkActionInfo = null) }
      setStatus(res.message, isError = !res.success)
    }
  }

  // --- Archive Viewer & Extractor (ZIP / APK / TAR / RAR) ---

  fun openArchiveViewer(entry: FileEntry) {
    viewModelScope.launch {
      val useSu = _uiState.value.isSuActive
      val formatLabel = when {
        entry.extension == "apk" -> "APK Package"
        ExploreDParsers.isZipOrApkFile(entry) -> "ZIP Archive"
        ExploreDParsers.isTarFile(entry) -> "TAR Archive"
        ExploreDParsers.isRarFile(entry) -> "RAR Archive"
        else -> "Archive"
      }

      if (ExploreDParsers.isRarFile(entry)) {
        val rarRes = shellManager.listRarEntries(entry.path, useSu)
        rarRes.onSuccess { items ->
          _uiState.update {
            it.copy(archiveViewer = ArchiveViewerState(entry, formatLabel, items))
          }
        }.onFailure { err ->
          _uiState.update {
            it.copy(archiveViewer = ArchiveViewerState(entry, formatLabel, emptyList(), error = err.message))
          }
        }
        return@launch
      }

      val localFile = resolveReadableLocalFile(entry, useSu)
      if (localFile == null) {
        setStatus("Unable to read ${entry.name}", isError = true)
        return@launch
      }
      activeArchiveSourceFile = localFile

      val res = if (ExploreDParsers.isZipOrApkFile(entry)) {
        ArchiveAndSqliteHelper.listZipOrApkEntries(localFile)
      } else {
        ArchiveAndSqliteHelper.listTarEntries(localFile)
      }

      res.onSuccess { items ->
        _uiState.update {
          it.copy(archiveViewer = ArchiveViewerState(entry, formatLabel, items))
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(archiveViewer = ArchiveViewerState(entry, formatLabel, emptyList(), error = err.message))
        }
      }
    }
  }

  fun inspectArchiveEntry(item: ArchiveEntryItem) {
    if (item.isDirectory) return
    val archiveState = _uiState.value.archiveViewer ?: return
    if (!ExploreDParsers.isZipOrApkFile(archiveState.entry)) {
      setStatus("In-memory preview is supported for ZIP/APK entries", isError = false)
      return
    }
    viewModelScope.launch {
      val archiveFile = activeArchiveSourceFile ?: resolveReadableLocalFile(archiveState.entry, _uiState.value.isSuActive)
      if (archiveFile == null) {
        setStatus("Archive source unavailable", isError = true)
        return@launch
      }
      val bytesRes = ArchiveAndSqliteHelper.readZipEntryBytes(archiveFile, item.name)
      bytesRes.onSuccess { bytes ->
        val isBinXml = BinaryXmlParser.isBinaryXml(bytes)
        val text = if (isBinXml) {
          BinaryXmlParser.decodeToXmlString(bytes).getOrElse { bytes.toString(Charsets.UTF_8) }
        } else {
          bytes.toString(Charsets.UTF_8)
        }
        val syntheticEntry = FileEntry(
          name = item.name.substringAfterLast('/'),
          path = "${archiveState.entry.path}!/${item.name}",
          isDirectory = false,
          sizeBytes = item.uncompressedSize,
        )
        _uiState.update {
          it.copy(
            textEditor = TextEditorState(
              entry = syntheticEntry,
              content = text,
              isBinaryXml = isBinXml,
              isReadOnly = true,
              isSystemFile = false,
            ),
          )
        }
      }.onFailure { err ->
        setStatus(err.message ?: "Unable to read ${item.name}", isError = true)
      }
    }
  }

  fun closeArchiveViewer() {
    activeTempInspectorFile?.delete()
    activeTempInspectorFile = null
    activeArchiveSourceFile = null
    _uiState.update { it.copy(archiveViewer = null) }
  }

  fun extractArchive(entry: FileEntry, intoSubfolder: Boolean) {
    val parentDir = File(entry.path).parent ?: _uiState.value.currentPath
    val folderName = entry.name
      .removeSuffix(".tar.gz")
      .removeSuffix(".tgz")
      .substringBeforeLast('.', entry.name + "_extracted")
    val targetDir = if (intoSubfolder) File(parentDir, folderName).absolutePath else parentDir
    gateOrExecute(
      "Extract \"${entry.name}\"",
      listOf(targetDir),
      PendingSystemAction.ExtractArchive(entry, targetDir),
    )
  }

  private suspend fun performArchiveExtraction(
    entry: FileEntry,
    targetDir: String,
    useSu: Boolean,
  ) {
    _uiState.update { it.copy(archiveViewer = it.archiveViewer?.copy(isExtracting = true)) }

    if (ExploreDParsers.isRarFile(entry)) {
      val res = shellManager.extractRarArchive(entry.path, targetDir, useSu)
      _uiState.update { it.copy(archiveViewer = null) }
      setStatus(res.message, isError = !res.success)
      if (res.success) refreshDirectory()
      return
    }

    val readableArchive = resolveReadableLocalFile(entry, useSu)
    if (readableArchive == null) {
      _uiState.update { it.copy(archiveViewer = it.archiveViewer?.copy(isExtracting = false)) }
      setStatus("Unable to read ${entry.name}", isError = true)
      return
    }

    val directTarget = File(targetDir)
    val canWriteDirectly = !useSu && (directTarget.canWrite() || directTarget.parentFile?.canWrite() == true)
    if (canWriteDirectly) {
      val extractRes = if (ExploreDParsers.isZipOrApkFile(entry)) {
        ArchiveAndSqliteHelper.extractZipOrApk(readableArchive, directTarget)
      } else {
        ArchiveAndSqliteHelper.extractTar(readableArchive, directTarget)
      }
      _uiState.update { it.copy(archiveViewer = null) }
      extractRes.onSuccess { count ->
        setStatus("Extracted $count item(s)", isError = false)
        refreshDirectory()
      }.onFailure { err ->
        setStatus(err.message ?: "Extraction failed", isError = true)
      }
      return
    }

    val cacheStage = File(getApplication<Application>().cacheDir, "explored_extract_${System.nanoTime()}")
    val extractRes = if (ExploreDParsers.isZipOrApkFile(entry)) {
      ArchiveAndSqliteHelper.extractZipOrApk(readableArchive, cacheStage)
    } else {
      ArchiveAndSqliteHelper.extractTar(readableArchive, cacheStage)
    }
    extractRes.onSuccess { count ->
      shellManager.createFolder(File(targetDir).parent ?: "/", File(targetDir).name, useSu)
      val copyRes = shellManager.copyDirContents(cacheStage, targetDir, useSu)
      cacheStage.deleteRecursively()
      _uiState.update { it.copy(archiveViewer = null) }
      setStatus(
        if (copyRes.success) "Extracted $count item(s)" else copyRes.message,
        isError = !copyRes.success,
      )
      if (copyRes.success) refreshDirectory()
    }.onFailure { err ->
      cacheStage.deleteRecursively()
      _uiState.update { it.copy(archiveViewer = it.archiveViewer?.copy(isExtracting = false)) }
      setStatus(err.message ?: "Extraction failed", isError = true)
    }
  }

  // --- SQLite Viewer & Editor ---

  fun openSqliteViewer(entry: FileEntry, tableName: String? = null) {
    viewModelScope.launch {
      val useSu = _uiState.value.isSuActive
      val localFile = resolveReadableLocalFile(entry, useSu, forceTempWhenSystem = true)
      if (localFile == null) {
        setStatus("Unable to open ${entry.name}", isError = true)
        return@launch
      }
      val info = ArchiveAndSqliteHelper.inspectSqliteDatabase(
        entry = entry,
        readableFile = localFile,
        tempCopyPath = if (localFile.absolutePath != entry.path) localFile.absolutePath else null,
        requestedTable = tableName,
      )
      _uiState.update { it.copy(sqliteViewer = info) }
    }
  }

  fun selectSqliteTable(tableName: String) {
    val current = _uiState.value.sqliteViewer ?: return
    val readable = current.tempCopyPath?.let { File(it) } ?: File(current.entry.path)
    viewModelScope.launch {
      val updated = ArchiveAndSqliteHelper.inspectSqliteDatabase(
        entry = current.entry,
        readableFile = readable,
        tempCopyPath = current.tempCopyPath,
        requestedTable = tableName,
        hasPendingChanges = current.hasPendingChanges,
      )
      _uiState.update { it.copy(sqliteViewer = updated) }
    }
  }

  fun runSqliteQuery(sql: String) {
    val current = _uiState.value.sqliteViewer ?: return
    val readable = current.tempCopyPath?.let { File(it) } ?: File(current.entry.path)
    viewModelScope.launch {
      val updated = ArchiveAndSqliteHelper.executeSqliteQuery(current, readable, sql)
      _uiState.update { it.copy(sqliteViewer = updated) }
    }
  }

  fun updateSqliteCell(rowIndex: Int, colIndex: Int, newValue: String?) {
    val current = _uiState.value.sqliteViewer ?: return
    val readable = current.tempCopyPath?.let { File(it) } ?: File(current.entry.path)
    viewModelScope.launch {
      val updated = ArchiveAndSqliteHelper.updateSqliteCell(current, readable, rowIndex, colIndex, newValue)
      _uiState.update { it.copy(sqliteViewer = updated) }
    }
  }

  fun deleteSqliteRow(rowIndex: Int) {
    val current = _uiState.value.sqliteViewer ?: return
    val readable = current.tempCopyPath?.let { File(it) } ?: File(current.entry.path)
    viewModelScope.launch {
      val updated = ArchiveAndSqliteHelper.deleteSqliteRow(current, readable, rowIndex)
      _uiState.update { it.copy(sqliteViewer = updated) }
    }
  }

  fun commitSqliteChanges() {
    val current = _uiState.value.sqliteViewer ?: return
    if (current.tempCopyPath == null) {
      setStatus("Changes already saved directly to database", isError = false)
      return
    }
    gateOrExecute(
      "Write SQLite database \"${current.entry.name}\"",
      listOf(current.entry.path),
      PendingSystemAction.CommitSqlite(current),
    )
  }

  fun closeSqliteViewer() {
    activeTempInspectorFile?.delete()
    activeTempInspectorFile = null
    _uiState.update { it.copy(sqliteViewer = null) }
  }

  private suspend fun resolveReadableLocalFile(
    entry: FileEntry,
    useSu: Boolean,
    forceTempWhenSystem: Boolean = false,
  ): File? {
    val direct = File(entry.path)
    val shouldUseDirect = direct.canRead() && (!forceTempWhenSystem || !isPathProtected(entry.path) || direct.canWrite())
    if (shouldUseDirect) return direct
    val cacheFile = File(getApplication<Application>().cacheDir, "inspect_${System.nanoTime()}_${entry.name}")
    val res = shellManager.copyReadableToTemp(entry.path, cacheFile, useSu)
    if (!res.success || !cacheFile.exists()) {
      cacheFile.delete()
      return null
    }
    activeTempInspectorFile?.delete()
    activeTempInspectorFile = cacheFile
    return cacheFile
  }

  // --- Root Tools & Preferences ---

  fun openToolsSheet() {
    _uiState.update { it.copy(showToolsSheet = true) }
    viewModelScope.launch {
      val mode = shellManager.getSeLinuxMode()
      _uiState.update { it.copy(selinuxMode = mode) }
    }
  }

  fun closeToolsSheet() {
    _uiState.update { it.copy(showToolsSheet = false) }
  }

  fun toggleSeLinuxMode() {
    val currentlyEnforcing = _uiState.value.selinuxMode.equals("Enforcing", ignoreCase = true)
    viewModelScope.launch {
      val res = shellManager.setSeLinuxPermissive(currentlyEnforcing)
      val refreshed = shellManager.getSeLinuxMode()
      _uiState.update { it.copy(selinuxMode = refreshed) }
      setStatus(res.message, isError = !res.success)
    }
  }

  fun runQuickRootCommand(cmd: String) {
    viewModelScope.launch {
      val res = shellManager.runRootCommand(cmd)
      _uiState.update { it.copy(quickToolOutput = res.message) }
    }
  }

  fun triggerReboot(target: String) {
    viewModelScope.launch {
      val res = shellManager.rebootDevice(target)
      setStatus(res.message, isError = !res.success)
    }
  }

  fun setShowHiddenFiles(show: Boolean) {
    viewModelScope.launch {
      pinPrefs.setShowHiddenFiles(show)
      refreshDirectory()
    }
  }

  fun setSortOrder(order: SortOrder) {
    viewModelScope.launch {
      pinPrefs.setSortOrder(order)
    }
  }

  private fun setStatus(msg: String, isError: Boolean) {
    _uiState.update { it.copy(statusMessage = msg, statusIsError = isError) }
  }

  private fun normalizePath(path: String): String {
    val trimmed = path.trim()
    if (trimmed.isEmpty() || trimmed == "/") return "/"
    return trimmed.trimEnd('/')
  }

  private fun filterAndSort(
    entries: List<FileEntry>,
    query: String,
    showHidden: Boolean,
    sortOrder: SortOrder,
  ): List<FileEntry> {
    val cleanQuery = query.trim().lowercase(Locale.ROOT)
    val filtered = entries.filter { entry ->
      (showHidden || !entry.isHidden) &&
        (cleanQuery.isEmpty() || entry.name.lowercase(Locale.ROOT).contains(cleanQuery))
    }

    return filtered.sortedWith(
      compareBy<FileEntry> { !it.isDirectory }
        .thenBy { entry ->
          when (sortOrder) {
            SortOrder.NAME -> entry.name.lowercase(Locale.ROOT)
            SortOrder.TYPE -> entry.extension + "|" + entry.name.lowercase(Locale.ROOT)
            SortOrder.SIZE -> String.format(Locale.US, "%020d", Long.MAX_VALUE - entry.sizeBytes)
            SortOrder.DATE -> entry.modifiedDisplay
          }
        }
        .let { baseComparator ->
          if (sortOrder == SortOrder.DATE) {
            compareBy<FileEntry> { !it.isDirectory }
              .thenByDescending { it.modifiedDisplay }
              .thenBy { it.name.lowercase(Locale.ROOT) }
          } else {
            baseComparator
          }
        },
    )
  }
}
