package com.example.files

import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SortOrder(val label: String) {
  NAME("Name"),
  SIZE("Size"),
  DATE("Modified"),
  TYPE("Type"),
}

enum class ClipboardMode {
  COPY,
  MOVE,
}

@Immutable
data class ClipboardState(
  val mode: ClipboardMode,
  val paths: Set<String>,
  val sourceDir: String,
)

@Immutable
data class FileEntry(
  val name: String,
  val path: String,
  val isDirectory: Boolean,
  val isSymlink: Boolean = false,
  val symlinkTarget: String? = null,
  val sizeBytes: Long = 0L,
  val modifiedDisplay: String = "",
  val modifiedEpochMillis: Long = 0L,
  val permissionsSymbolic: String = "---------",
  val permissionsOctal: String = "0644",
  val owner: String = "",
  val group: String = "",
  val seLinuxContext: String = "",
) {
  val isHidden: Boolean
    get() = name.startsWith(".")

  val extension: String
    get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}

@Immutable
data class BrowserTabState(
  val id: String,
  val currentPath: String,
  val rawEntries: List<FileEntry> = emptyList(),
  val visibleEntries: List<FileEntry> = emptyList(),
  val searchQuery: String = "",
  val isLoading: Boolean = false,
  val accessDenied: Boolean = false,
  val selectedPaths: Set<String> = emptySet(),
  val inspectedEntry: FileEntry? = null,
) {
  val title: String
    get() = when {
      currentPath == "/" -> "/"
      currentPath == "/storage/emulated/0" -> "Internal"
      else -> currentPath.substringAfterLast('/').ifEmpty { currentPath }
    }
}

@Immutable
data class FilePropertiesDetails(
  val entry: FileEntry,
  val seLinuxContext: String = "",
  val resolvedLinkTarget: String? = null,
  val isLoading: Boolean = false,
)

@Immutable
data class ApkInspectorInfo(
  val entry: FileEntry,
  val appLabel: String,
  val packageName: String,
  val versionName: String,
  val versionCode: Long,
  val minSdk: Int?,
  val targetSdk: Int?,
)

@Immutable
data class MountPartition(
  val device: String,
  val mountPoint: String,
  val fsType: String,
  val isReadWrite: Boolean,
  val rawOptions: String,
)

@Immutable
data class ArchiveEntryItem(
  val name: String,
  val compressedSize: Long,
  val uncompressedSize: Long,
  val isDirectory: Boolean,
)

@Immutable
data class SqliteDatabaseInfo(
  val entry: FileEntry,
  val tempCopyPath: String?,
  val tables: List<String>,
  val selectedTable: String?,
  val columns: List<String>,
  val rows: List<List<String>>,
  val totalRows: Int,
  val error: String? = null,
  val rowIds: List<Long?> = emptyList(),
  val hasRowId: Boolean = false,
  val sqlQuery: String = "",
  val queryResultMessage: String? = null,
  val hasPendingChanges: Boolean = false,
  val isSavingBack: Boolean = false,
)

@Immutable
data class PermissionBits(
  val ownerRead: Boolean = true,
  val ownerWrite: Boolean = true,
  val ownerExecute: Boolean = false,
  val groupRead: Boolean = true,
  val groupWrite: Boolean = false,
  val groupExecute: Boolean = false,
  val otherRead: Boolean = true,
  val otherWrite: Boolean = false,
  val otherExecute: Boolean = false,
  val setUid: Boolean = false,
  val setGid: Boolean = false,
  val sticky: Boolean = false,
) {
  fun withoutExecuteBits(): PermissionBits =
    copy(ownerExecute = false, groupExecute = false, otherExecute = false)

  fun toOctalString(alwaysFourDigits: Boolean = true): String {
    val special = (if (setUid) 4 else 0) + (if (setGid) 2 else 0) + (if (sticky) 1 else 0)
    val owner = (if (ownerRead) 4 else 0) + (if (ownerWrite) 2 else 0) + (if (ownerExecute) 1 else 0)
    val group = (if (groupRead) 4 else 0) + (if (groupWrite) 2 else 0) + (if (groupExecute) 1 else 0)
    val other = (if (otherRead) 4 else 0) + (if (otherWrite) 2 else 0) + (if (otherExecute) 1 else 0)
    return if (alwaysFourDigits || special > 0) {
      "$special$owner$group$other"
    } else {
      "$owner$group$other"
    }
  }

  fun toSymbolicString(): String {
    val oR = if (ownerRead) 'r' else '-'
    val oW = if (ownerWrite) 'w' else '-'
    val oX = when {
      setUid && ownerExecute -> 's'
      setUid -> 'S'
      ownerExecute -> 'x'
      else -> '-'
    }
    val gR = if (groupRead) 'r' else '-'
    val gW = if (groupWrite) 'w' else '-'
    val gX = when {
      setGid && groupExecute -> 's'
      setGid -> 'S'
      groupExecute -> 'x'
      else -> '-'
    }
    val tR = if (otherRead) 'r' else '-'
    val tW = if (otherWrite) 'w' else '-'
    val tX = when {
      sticky && otherExecute -> 't'
      sticky -> 'T'
      otherExecute -> 'x'
      else -> '-'
    }
    return charArrayOf(oR, oW, oX, gR, gW, gX, tR, tW, tX).concatToString()
  }

  companion object {
    fun fromOctal(raw: String): PermissionBits? {
      val trimmed = raw.trim()
      if (trimmed.length !in 3..4) return null
      if (!trimmed.all { it in '0'..'7' }) return null
      val padded = if (trimmed.length == 3) "0$trimmed" else trimmed
      val s = padded[0] - '0'
      val o = padded[1] - '0'
      val g = padded[2] - '0'
      val t = padded[3] - '0'
      return PermissionBits(
        ownerRead = (o and 4) != 0,
        ownerWrite = (o and 2) != 0,
        ownerExecute = (o and 1) != 0,
        groupRead = (g and 4) != 0,
        groupWrite = (g and 2) != 0,
        groupExecute = (g and 1) != 0,
        otherRead = (t and 4) != 0,
        otherWrite = (t and 2) != 0,
        otherExecute = (t and 1) != 0,
        setUid = (s and 4) != 0,
        setGid = (s and 2) != 0,
        sticky = (s and 1) != 0,
      )
    }

    fun fromSymbolic(raw: String): PermissionBits {
      val mode = when {
        raw.length >= 10 -> raw.substring(1, 10)
        raw.length == 9 -> raw
        else -> "rw-r--r--"
      }
      val oXChar = mode[2]
      val gXChar = mode[5]
      val tXChar = mode[8]
      return PermissionBits(
        ownerRead = mode[0] == 'r',
        ownerWrite = mode[1] == 'w',
        ownerExecute = oXChar == 'x' || oXChar == 's',
        groupRead = mode[3] == 'r',
        groupWrite = mode[4] == 'w',
        groupExecute = gXChar == 'x' || gXChar == 's',
        otherRead = mode[6] == 'r',
        otherWrite = mode[7] == 'w',
        otherExecute = tXChar == 'x' || tXChar == 't',
        setUid = oXChar == 's' || oXChar == 'S',
        setGid = gXChar == 's' || gXChar == 'S',
        sticky = tXChar == 't' || tXChar == 'T',
      )
    }
  }
}

object ExploreDParsers {
  private val ISO_LINE_REGEX = Regex(
    """^([\-dlcbps])([rwxstST\-]{9})[.+@]?\s+\d+\s+(\S+)\s+(\S+)\s+(?:(\d+),\s*(\d+)|(\d+))\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}(?::\d{2})?)\s+(.+)$""",
  )
  private val BSD_LINE_REGEX = Regex(
    """^([\-dlcbps])([rwxstST\-]{9})[.+@]?\s+\d+\s+(\S+)\s+(\S+)\s+(?:(\d+),\s*(\d+)|(\d+))\s+([A-Z][a-z]{2}\s+\d+\s+(?:\d{2}:\d{2}|\d{4}))\s+(.+)$""",
  )

  private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

  fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return synchronized(dateFormatter) {
      dateFormatter.format(Date(epochMillis))
    }
  }

  fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
  }

  /**
   * True for system/root/app-private Android partitions where POSIX permissions apply and
   * system modifications should prompt a safety warning + auto-remount RW.
   * False for shared user storage (/storage, /sdcard, /data/media, /mnt/media_rw, temp).
   */
  fun isSystemPartitionPath(path: String, internalStoragePath: String): Boolean {
    val clean = path.trim().trimEnd('/').ifEmpty { "/" }
    val cleanInternal = internalStoragePath.trim().trimEnd('/').ifEmpty { "/storage/emulated/0" }
    if (
      clean == cleanInternal ||
      clean.startsWith("$cleanInternal/") ||
      clean == "/storage" ||
      clean.startsWith("/storage/") ||
      clean == "/sdcard" ||
      clean.startsWith("/sdcard/") ||
      clean == "/data/media" ||
      clean.startsWith("/data/media/") ||
      clean.startsWith("/mnt/media_rw") ||
      clean.startsWith("/mnt/user") ||
      clean.startsWith("/mnt/pass_through")
    ) {
      return false
    }
    return true
  }

  fun isZipOrApkFile(entry: FileEntry): Boolean =
    !entry.isDirectory && entry.extension in setOf("zip", "apk", "jar", "xapk")

  fun isTarFile(entry: FileEntry): Boolean {
    if (entry.isDirectory) return false
    val lower = entry.name.lowercase(Locale.ROOT)
    return lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
  }

  fun isRarFile(entry: FileEntry): Boolean =
    !entry.isDirectory && entry.extension == "rar"

  fun isArchiveFile(entry: FileEntry): Boolean =
    isZipOrApkFile(entry) || isTarFile(entry) || isRarFile(entry)

  fun isSqliteFile(entry: FileEntry): Boolean =
    !entry.isDirectory && entry.extension in setOf("db", "sqlite", "sqlite3", "db3")

  fun isTextInspectable(entry: FileEntry): Boolean {
    if (entry.isDirectory) return false
    if (isArchiveFile(entry) || isSqliteFile(entry)) return false
    val ext = entry.extension
    if (ext in TEXT_EXTENSIONS) return true
    val lower = entry.name.lowercase(Locale.ROOT)
    return lower in TEXT_FILENAMES || (ext.isEmpty() && entry.sizeBytes in 0L..262_144L)
  }

  fun parseLsOutput(
    raw: String,
    parentDir: String,
    showHidden: Boolean,
  ): List<FileEntry> {
    val cleanParent = if (parentDir == "/") "/" else parentDir.trimEnd('/')
    val results = mutableListOf<FileEntry>()
    for (rawLine in raw.lineSequence()) {
      val line = rawLine.trimEnd('\r')
      if (line.isBlank() || line.startsWith("total ")) continue
      val match = ISO_LINE_REGEX.matchEntire(line) ?: BSD_LINE_REGEX.matchEntire(line) ?: continue
      val typeChar = match.groupValues[1][0]
      val perms9 = match.groupValues[2]
      val owner = match.groupValues[3]
      val group = match.groupValues[4]
      val size = match.groupValues[7].toLongOrNull() ?: 0L
      val timestamp = match.groupValues[8]
      val nameRaw = match.groupValues[9]

      val isSymlink = typeChar == 'l'
      val (name, symlinkTarget) = if (isSymlink && nameRaw.contains(" -> ")) {
        val idx = nameRaw.indexOf(" -> ")
        nameRaw.substring(0, idx) to nameRaw.substring(idx + 4)
      } else {
        nameRaw to null
      }

      if (name == "." || name == "..") continue
      if (!showHidden && name.startsWith(".")) continue

      val isDirectory = typeChar == 'd' || (isSymlink && symlinkTarget != null && !symlinkTarget.contains('.'))
      val fullPath = if (cleanParent == "/") "/$name" else "$cleanParent/$name"
      val octal = PermissionBits.fromSymbolic(perms9).toOctalString()

      results += FileEntry(
        name = name,
        path = fullPath,
        isDirectory = isDirectory,
        isSymlink = isSymlink,
        symlinkTarget = symlinkTarget,
        sizeBytes = size,
        modifiedDisplay = timestamp,
        permissionsSymbolic = "$typeChar$perms9",
        permissionsOctal = octal,
        owner = owner,
        group = group,
      )
    }
    return results
  }

  fun parseProcMounts(raw: String): List<MountPartition> {
    val seenMountPoints = linkedMapOf<String, MountPartition>()
    for (rawLine in raw.lineSequence()) {
      val line = rawLine.trim()
      if (line.isEmpty()) continue
      val parts = line.split(Regex("\\s+"))
      if (parts.size < 4) continue
      val device = parts[0]
      val mountPoint = parts[1].replace("\\040", " ")
      val fsType = parts[2]
      val options = parts[3]
      val optList = options.split(',')
      val isRw = "rw" in optList && "ro" !in optList
      seenMountPoints[mountPoint] = MountPartition(
        device = device,
        mountPoint = mountPoint,
        fsType = fsType,
        isReadWrite = isRw,
        rawOptions = options,
      )
    }
    return seenMountPoints.values.toList()
  }

  private val TEXT_EXTENSIONS = setOf(
    "txt", "conf", "cfg", "prop", "sh", "xml", "json", "yaml", "yml",
    "ini", "rc", "log", "md", "kt", "java", "py", "env", "properties",
    "hosts", "fstab", "policy", "rules", "list", "toml",
  )

  private val TEXT_FILENAMES = setOf(
    "build.prop", "default.prop", "hosts", "fstab", "resolv.conf",
    "init.rc", "ueventd.rc", "mounts", "cpuinfo", "meminfo", "version",
  )
}
