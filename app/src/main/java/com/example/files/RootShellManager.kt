package com.example.files

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class DirectoryListingResult(
  val entries: List<FileEntry>,
  val accessDenied: Boolean = false,
  val errorMessage: String? = null,
)

data class ShellOpResult(
  val success: Boolean,
  val message: String = "",
)

class RootShellManager(
  private val scope: CoroutineScope,
) {
  companion object {
    private const val MARKER_PREFIX = "__EXP_MARK_"
    private const val SU_PROBE_PREFIX = "__EXP_SU_OK_"

    fun resolveShBinary(): String =
      listOf("/system/bin/sh", "/bin/sh")
        .firstOrNull { File(it).canExecute() }
        ?: "sh"

    fun resolveSuBinary(): String? =
      listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
      ).firstOrNull { File(it).canExecute() }
        ?: findExecutableInPath("su")

    fun resolveRarTool(): String? =
      listOf(
        "/system/bin/unrar",
        "/system/xbin/unrar",
        "/system/bin/7z",
        "/system/xbin/7z",
        "/system/bin/bsdtar",
        "/usr/bin/unrar",
        "/usr/bin/7z",
      ).firstOrNull { File(it).canExecute() }
        ?: findExecutableInPath("unrar")
        ?: findExecutableInPath("7z")
        ?: findExecutableInPath("7za")
        ?: findExecutableInPath("bsdtar")

    private fun findExecutableInPath(name: String): String? {
      val pathEnv = System.getenv("PATH").orEmpty()
      for (dir in pathEnv.split(':')) {
        if (dir.isBlank()) continue
        val candidate = File(dir, name)
        if (candidate.exists() && candidate.canExecute()) {
          return candidate.absolutePath
        }
      }
      return null
    }

    fun shellQuote(value: String): String =
      "'" + value.replace("'", "'\"'\"'") + "'"
  }

  private data class PendingCommand(
    val token: String,
    val output: StringBuilder,
    val errors: StringBuilder,
    val deferred: CompletableDeferred<Int>,
  )

  private class SuSession(
    val process: Process,
    val writer: BufferedWriter,
    val stdoutJob: Job,
    val stderrJob: Job,
    val monitorJob: Job,
  ) {
    val closed = AtomicBoolean(false)
  }

  private val mutex = Mutex()
  private var suSession: SuSession? = null

  @Volatile
  private var pendingSuProbe: Pair<String, CompletableDeferred<Boolean>>? = null

  @Volatile
  private var pendingCommand: PendingCommand? = null

  val isSuActive: Boolean
    get() = suSession?.let { isProcessAlive(it.process) } == true

  suspend fun enableSu(): ShellOpResult = withContext(Dispatchers.IO) {
    mutex.withLock {
      if (isSuActive) {
        return@withLock ShellOpResult(true, "SU already active")
      }
      val suBinary = resolveSuBinary()
        ?: return@withLock ShellOpResult(false, "su binary not found on this device")

      val process = runCatching {
        ProcessBuilder(suBinary).apply {
          directory(File("/").takeIf { it.exists() })
          redirectErrorStream(false)
        }.start()
      }.getOrElse { err ->
        return@withLock ShellOpResult(false, err.message ?: "Failed to launch su")
      }

      val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
      val probeToken = UUID.randomUUID().toString().replace("-", "").take(12)
      val probeDeferred = CompletableDeferred<Boolean>()
      pendingSuProbe = probeToken to probeDeferred

      val stderrBuffer = StringBuilder()
      val stdoutJob = scope.launch(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        runCatching {
          while (true) {
            val line = reader.readLine() ?: break
            handleSuStdout(line)
          }
        }
      }
      val stderrJob = scope.launch(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(process.errorStream))
        runCatching {
          while (true) {
            val line = reader.readLine() ?: break
            pendingCommand?.errors?.appendLine(line) ?: stderrBuffer.appendLine(line)
          }
        }
      }
      val monitorJob = scope.launch(Dispatchers.IO) {
        runCatching { process.waitFor() }
        pendingSuProbe?.second?.complete(false)
        pendingCommand?.deferred?.complete(1)
        if (suSession?.process === process) {
          suSession = null
        }
      }

      val session = SuSession(process, writer, stdoutJob, stderrJob, monitorJob)
      val sent = runCatching {
        writer.write("printf '${SU_PROBE_PREFIX}%s\\n' '$probeToken'\n")
        writer.flush()
      }.isSuccess

      if (!sent) {
        pendingSuProbe = null
        closeSuSession(session)
        return@withLock ShellOpResult(false, "SU permission denied")
      }

      val ok = withTimeoutOrNull(8_000L) { probeDeferred.await() } ?: false
      pendingSuProbe = null
      if (!ok || !isProcessAlive(process)) {
        closeSuSession(session)
        val detail = stderrBuffer.toString().trim().ifEmpty { "SU permission denied or timed out" }
        return@withLock ShellOpResult(false, detail)
      }

      suSession = session
      ShellOpResult(true, "SU shell elevated")
    }
  }

  suspend fun disableSu() = withContext(Dispatchers.IO) {
    mutex.withLock {
      suSession?.let { closeSuSession(it) }
      suSession = null
    }
  }

  fun close() {
    suSession?.let { closeSuSession(it) }
    suSession = null
  }

  suspend fun listDirectory(
    path: String,
    useSu: Boolean,
    showHidden: Boolean,
  ): DirectoryListingResult = withContext(Dispatchers.IO) {
    val cleanPath = if (path.isBlank()) "/" else path
    if (useSu && isSuActive) {
      val (code, out, err) = runSuCommand("ls -la ${shellQuote(cleanPath)}")
      val parsed = ExploreDParsers.parseLsOutput(out, cleanPath, showHidden)
      if (code == 0 || parsed.isNotEmpty()) {
        return@withContext DirectoryListingResult(entries = parsed)
      }
      return@withContext DirectoryListingResult(
        entries = emptyList(),
        accessDenied = err.contains("Permission denied", ignoreCase = true),
        errorMessage = err.trim().ifEmpty { "Unable to read $cleanPath" },
      )
    }

    val targetDir = File(cleanPath)
    val javaFiles = runCatching { targetDir.listFiles() }.getOrNull()
    val shResult = runNonRootSh("ls -la ${shellQuote(cleanPath)}")
    val shEntries = ExploreDParsers.parseLsOutput(shResult.second, cleanPath, showHidden)

    if (shEntries.isNotEmpty()) {
      val shByPath = shEntries.associateBy { it.path }.toMutableMap()
      javaFiles?.forEach { file ->
        if ((showHidden || !file.name.startsWith(".")) && !shByPath.containsKey(file.absolutePath)) {
          shByPath[file.absolutePath] = fileToEntry(file)
        }
      }
      return@withContext DirectoryListingResult(entries = shByPath.values.toList())
    }

    if (javaFiles != null) {
      val mapped = javaFiles
        .filter { showHidden || !it.name.startsWith(".") }
        .map { fileToEntry(it) }
      return@withContext DirectoryListingResult(entries = mapped)
    }

    val denied = shResult.third.contains("Permission denied", ignoreCase = true) ||
      shResult.third.contains("Operation not permitted", ignoreCase = true) ||
      !targetDir.canRead()

    DirectoryListingResult(
      entries = emptyList(),
      accessDenied = denied,
      errorMessage = shResult.third.trim().ifEmpty { "Permission denied for $cleanPath" },
    )
  }

  fun resolveMountForPath(path: String, mounts: List<MountPartition>): MountPartition? {
    if (mounts.isEmpty()) return null
    val normalized = path.trim().trimEnd('/').ifEmpty { "/" }
    return mounts
      .filter { mp ->
        mp.mountPoint == "/" ||
          normalized == mp.mountPoint ||
          normalized.startsWith(mp.mountPoint.trimEnd('/') + "/")
      }
      .maxByOrNull { it.mountPoint.length }
  }

  suspend fun ensureWritableMounts(
    paths: Collection<String>,
    mounts: List<MountPartition>,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    if (!isSuActive) {
      return@withContext ShellOpResult(false, "SU required to auto-mount partition")
    }
    val effectiveMounts = mounts.ifEmpty { readMounts(useSu = true) }
    val targetMountPoints = paths.mapNotNull { path ->
      resolveMountForPath(path, effectiveMounts)
    }.distinctBy { it.mountPoint }

    for (mount in targetMountPoints) {
      if (!mount.isReadWrite) {
        val remounted = remountPartitionInternal(mount.mountPoint, readWrite = true)
        if (!remounted.success && mount.mountPoint != "/") {
          val rootFallback = remountPartitionInternal("/", readWrite = true)
          if (!rootFallback.success) {
            return@withContext remounted
          }
        } else if (!remounted.success) {
          return@withContext remounted
        }
      }
    }
    ShellOpResult(true, "Mount ready")
  }

  suspend fun remountPartition(
    mountPoint: String,
    readWrite: Boolean,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    if (!isSuActive) {
      return@withContext ShellOpResult(false, "SU required to remount $mountPoint")
    }
    val direct = remountPartitionInternal(mountPoint, readWrite)
    if (direct.success) return@withContext direct
    if (mountPoint != "/") {
      val fallback = remountPartitionInternal("/", readWrite)
      if (fallback.success) return@withContext fallback
    }
    direct
  }

  private suspend fun remountPartitionInternal(
    mountPoint: String,
    readWrite: Boolean,
  ): ShellOpResult {
    val qMount = shellQuote(mountPoint)
    val flag = if (readWrite) "rw" else "ro"
    val label = if (readWrite) "R/W" else "R/O"
    val attempts = listOf(
      "mount -o $flag,remount $qMount",
      "mount -o remount,$flag $qMount",
      "toolbox mount -o remount,$flag $qMount",
    )
    var lastErr = ""
    for (cmd in attempts) {
      val (code, _, err) = runSuCommand(cmd)
      if (code == 0) {
        return ShellOpResult(true, "Mounted $mountPoint as $label")
      }
      lastErr = err.trim()
    }
    return ShellOpResult(false, lastErr.ifEmpty { "Remount $label failed for $mountPoint" })
  }

  suspend fun createFolder(parentDir: String, name: String, useSu: Boolean): ShellOpResult =
    withContext(Dispatchers.IO) {
      val target = File(parentDir, name)
      if (!useSu) {
        if (target.exists()) return@withContext ShellOpResult(false, "Already exists")
        if (runCatching { target.mkdirs() }.getOrDefault(false)) {
          return@withContext ShellOpResult(true, "Created folder $name")
        }
      }
      val cmd = "mkdir -p ${shellQuote(target.absolutePath)}"
      execWriteCommand(cmd, useSu, "Created folder $name")
    }

  suspend fun createFile(parentDir: String, name: String, useSu: Boolean): ShellOpResult =
    withContext(Dispatchers.IO) {
      val target = File(parentDir, name)
      if (!useSu) {
        if (target.exists()) return@withContext ShellOpResult(false, "Already exists")
        if (runCatching { target.createNewFile() }.getOrDefault(false)) {
          return@withContext ShellOpResult(true, "Created file $name")
        }
      }
      val cmd = "touch ${shellQuote(target.absolutePath)}"
      execWriteCommand(cmd, useSu, "Created file $name")
    }

  suspend fun renameEntry(oldPath: String, newName: String, useSu: Boolean): ShellOpResult =
    withContext(Dispatchers.IO) {
      val source = File(oldPath)
      val parent = source.parentFile ?: return@withContext ShellOpResult(false, "Invalid parent")
      val dest = File(parent, newName)
      if (dest.exists()) return@withContext ShellOpResult(false, "$newName already exists")
      if (!useSu) {
        if (runCatching { source.renameTo(dest) }.getOrDefault(false)) {
          return@withContext ShellOpResult(true, "Renamed to $newName")
        }
      }
      val cmd = "mv ${shellQuote(oldPath)} ${shellQuote(dest.absolutePath)}"
      execWriteCommand(cmd, useSu, "Renamed to $newName")
    }

  suspend fun copyEntries(
    sourcePaths: Collection<String>,
    targetDir: String,
    useSu: Boolean,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    if (sourcePaths.isEmpty()) return@withContext ShellOpResult(false, "Nothing selected")
    if (!useSu) {
      val allJavaOk = runCatching {
        for (srcPath in sourcePaths) {
          val src = File(srcPath)
          val dst = resolveUniqueCopyTarget(File(targetDir), src.name)
          src.copyRecursively(dst, overwrite = false)
        }
        true
      }.getOrDefault(false)
      if (allJavaOk) return@withContext ShellOpResult(true, "Copied ${sourcePaths.size} item(s)")
    }
    val quotedSources = sourcePaths.joinToString(" ") { shellQuote(it) }
    val qDest = shellQuote(targetDir.trimEnd('/') + "/")
    val cmd = "cp -af $quotedSources $qDest || cp -rfp $quotedSources $qDest || cp -rf $quotedSources $qDest"
    execWriteCommand(cmd, useSu, "Copied ${sourcePaths.size} item(s)")
  }

  suspend fun moveEntries(
    sourcePaths: Collection<String>,
    targetDir: String,
    useSu: Boolean,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    if (sourcePaths.isEmpty()) return@withContext ShellOpResult(false, "Nothing selected")
    if (!useSu) {
      val allJavaOk = runCatching {
        for (srcPath in sourcePaths) {
          val src = File(srcPath)
          val dst = File(targetDir, src.name)
          if (!src.renameTo(dst)) {
            src.copyRecursively(dst, overwrite = true)
            src.deleteRecursively()
          }
        }
        true
      }.getOrDefault(false)
      if (allJavaOk) return@withContext ShellOpResult(true, "Moved ${sourcePaths.size} item(s)")
    }
    val quotedSources = sourcePaths.joinToString(" ") { shellQuote(it) }
    val qDest = shellQuote(targetDir.trimEnd('/') + "/")
    val cmd = "(mv -f $quotedSources $qDest) || ((cp -af $quotedSources $qDest || cp -rfp $quotedSources $qDest) && rm -rf $quotedSources)"
    execWriteCommand(cmd, useSu, "Moved ${sourcePaths.size} item(s)")
  }

  suspend fun deleteEntries(paths: Collection<String>, useSu: Boolean): ShellOpResult =
    withContext(Dispatchers.IO) {
      if (paths.isEmpty()) return@withContext ShellOpResult(false, "Nothing selected")
      if (!useSu) {
        val allDeleted = runCatching {
          paths.all { path ->
            val file = File(path)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
          }
        }.getOrDefault(false)
        if (allDeleted) return@withContext ShellOpResult(true, "Deleted ${paths.size} item(s)")
      }
      val quotedPaths = paths.joinToString(" ") { shellQuote(it) }
      val cmd = "rm -rf $quotedPaths"
      execWriteCommand(cmd, useSu, "Deleted ${paths.size} item(s)")
    }

  suspend fun changePermissions(
    path: String,
    octal: String,
    recursive: Boolean,
    useSu: Boolean,
    noExecOnChildFiles: Boolean = false,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    val cleanOctal = octal.trim()
    val parsed = PermissionBits.fromOctal(cleanOctal)
      ?: return@withContext ShellOpResult(false, "Invalid octal mode: $octal")
    val qPath = shellQuote(path)
    if (recursive && noExecOnChildFiles) {
      val fileOctal = parsed.withoutExecuteBits().toOctalString()
      val cmd = """
        if [ -d $qPath ]; then
          chmod $cleanOctal $qPath && find $qPath -type d -exec chmod $cleanOctal {} \; && find $qPath -type f -exec chmod $fileOctal {} \;
        else
          chmod $fileOctal $qPath
        fi
      """.trimIndent()
      return@withContext execWriteCommand(cmd, useSu, "Dirs $cleanOctal · Files $fileOctal")
    }
    val flag = if (recursive) "-R " else ""
    val cmd = "chmod $flag$cleanOctal $qPath"
    execWriteCommand(cmd, useSu, "Permissions set to $cleanOctal")
  }

  suspend fun changeOwnership(
    path: String,
    ownerGroup: String,
    recursive: Boolean,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    if (!isSuActive) return@withContext ShellOpResult(false, "SU required for chown")
    val cleanOwner = ownerGroup.trim()
    if (cleanOwner.isEmpty()) return@withContext ShellOpResult(false, "Owner cannot be empty")
    val flag = if (recursive) "-R " else ""
    val cmd = "chown $flag${shellQuote(cleanOwner)} ${shellQuote(path)}"
    execWriteCommand(cmd, useSu = true, "Owner updated to $cleanOwner")
  }

  suspend fun getFileSecurityDetails(
    path: String,
    useSu: Boolean,
  ): Pair<String, String?> = withContext(Dispatchers.IO) {
    val qPath = shellQuote(path)
    val cmd = "stat -c '%C' $qPath 2>/dev/null || ls -Zd $qPath 2>/dev/null; printf '\\n__EXP_LINK__\\n'; readlink -f $qPath 2>/dev/null || readlink $qPath 2>/dev/null"
    val (_, out, _) = if (useSu && isSuActive) runSuCommand(cmd) else runNonRootSh(cmd)
    val parts = out.split("__EXP_LINK__")
    val rawCtx = parts.getOrNull(0).orEmpty().trim()
    val seContext = when {
      rawCtx.isBlank() || rawCtx == "?" -> ""
      rawCtx.contains("u:") -> Regex("""u:[^\s]+""").find(rawCtx)?.value ?: rawCtx.lineSequence().firstOrNull().orEmpty()
      else -> rawCtx.lineSequence().firstOrNull().orEmpty().takeIf { !it.startsWith("total ") }.orEmpty()
    }
    val resolvedLink = parts.getOrNull(1).orEmpty().trim().takeIf { it.isNotEmpty() && it != path }
    seContext to resolvedLink
  }

  suspend fun changeSeLinuxContext(
    path: String,
    context: String,
    recursive: Boolean,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    if (!isSuActive) return@withContext ShellOpResult(false, "SU required for chcon")
    val cleanCtx = context.trim()
    if (cleanCtx.isEmpty()) return@withContext ShellOpResult(false, "SELinux context cannot be empty")
    val flag = if (recursive) "-R " else ""
    val cmd = "chcon $flag${shellQuote(cleanCtx)} ${shellQuote(path)}"
    execWriteCommand(cmd, useSu = true, "SELinux context set to $cleanCtx")
  }

  suspend fun createSymlink(
    targetPath: String,
    linkPath: String,
    useSu: Boolean,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    val cleanTarget = targetPath.trim()
    val cleanLink = linkPath.trim()
    if (cleanTarget.isEmpty() || cleanLink.isEmpty()) {
      return@withContext ShellOpResult(false, "Target and link path are required")
    }
    val cmd = "ln -s ${shellQuote(cleanTarget)} ${shellQuote(cleanLink)}"
    execWriteCommand(cmd, useSu, "Created symlink ${File(cleanLink).name}")
  }

  suspend fun readFileBytes(
    path: String,
    useSu: Boolean,
    maxBytes: Int = 1_048_576,
  ): Result<ByteArray> = withContext(Dispatchers.IO) {
    val file = File(path)
    if (!useSu && file.canRead()) {
      return@withContext runCatching {
        file.inputStream().use { input ->
          val out = java.io.ByteArrayOutputStream()
          val buf = ByteArray(8192)
          var total = 0
          while (total < maxBytes) {
            val toRead = minOf(buf.size, maxBytes - total)
            val read = input.read(buf, 0, toRead)
            if (read <= 0) break
            out.write(buf, 0, read)
            total += read
          }
          out.toByteArray()
        }
      }
    }
    // Try base64 transfer first so binary bytes (like AXML/ABX) are 100% lossless
    val qPath = shellQuote(path)
    val b64Cmd = "head -c $maxBytes $qPath | base64"
    val (b64Code, b64Out, b64Err) = if (useSu && isSuActive) runSuCommand(b64Cmd) else runNonRootSh(b64Cmd)
    if (b64Code == 0 && b64Out.isNotBlank()) {
      val decoded = runCatching {
        android.util.Base64.decode(b64Out.replace("\n", "").replace("\r", ""), android.util.Base64.DEFAULT)
      }.getOrNull()
      if (decoded != null) return@withContext Result.success(decoded)
    }
    // Fallback to raw head if base64 binary unavailable
    val rawCmd = "head -c $maxBytes $qPath"
    val (code, out, err) = if (useSu && isSuActive) runSuCommand(rawCmd) else runNonRootSh(rawCmd)
    if (code == 0) {
      Result.success(out.toByteArray(Charsets.UTF_8))
    } else {
      Result.failure(IllegalStateException(err.trim().ifEmpty { b64Err.trim().ifEmpty { "Unable to read $path" } }))
    }
  }

  suspend fun readTextFile(path: String, useSu: Boolean): Result<String> =
    withContext(Dispatchers.IO) {
      val file = File(path)
      if (!useSu && file.canRead()) {
        return@withContext runCatching {
          val bytes = file.inputStream().use { input ->
            val buf = ByteArray(1_048_576)
            val read = input.read(buf)
            if (read <= 0) ByteArray(0) else buf.copyOf(read)
          }
          bytes.toString(Charsets.UTF_8)
        }
      }
      val cmd = "head -c 1048576 ${shellQuote(path)}"
      val (code, out, err) = if (useSu && isSuActive) runSuCommand(cmd) else runNonRootSh(cmd)
      if (code == 0) {
        Result.success(out)
      } else {
        Result.failure(IllegalStateException(err.trim().ifEmpty { "Unable to read $path" }))
      }
    }

  suspend fun writeTextFile(
    path: String,
    content: String,
    useSu: Boolean,
    createBackup: Boolean = false,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    val file = File(path)
    if (!useSu) {
      val written = runCatching {
        if (createBackup && file.exists()) {
          file.copyTo(File("${file.absolutePath}.bak"), overwrite = true)
        }
        val tmp = File(file.parentFile, ".${file.name}.tmp.${System.nanoTime()}")
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
          file.writeText(content, Charsets.UTF_8)
          tmp.delete()
        }
        true
      }.getOrDefault(false)
      if (written) {
        val msg = if (createBackup) "Saved ${file.name} (.bak created)" else "Saved ${file.name}"
        return@withContext ShellOpResult(true, msg)
      }
    }

    val qPath = shellQuote(path)
    val qBak = shellQuote("$path.bak")
    val tmpPath = "$path.tmp.${UUID.randomUUID().toString().replace("-", "").take(8)}"
    val qTmp = shellQuote(tmpPath)
    val b64 = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    val eofMarker = "__EXP_EOF_${UUID.randomUUID().toString().replace("-", "").take(10)}"

    val backupSnippet = if (createBackup) {
      "if [ -e $qPath ]; then cp -p $qPath $qBak || cp -f $qPath $qBak; fi\n"
    } else {
      ""
    }
    val metaCaptureSnippet = """
      __EXP_MODE=""
      __EXP_OWNER=""
      __EXP_CTX=""
      if [ -e $qPath ]; then
        __EXP_MODE=$(stat -c '%a' $qPath 2>/dev/null)
        __EXP_OWNER=$(stat -c '%u:%g' $qPath 2>/dev/null)
        __EXP_CTX=$(stat -c '%C' $qPath 2>/dev/null)
      fi
    """.trimIndent()

    val writeTmpSnippet = """
      if command -v base64 >/dev/null 2>&1; then
        printf '%s' '$b64' | base64 -d > $qTmp
      else
        cat << '$eofMarker' > $qTmp
      ${if (content.endsWith("\n")) content else "$content\n"}$eofMarker
      fi
    """.trimIndent()

    val restoreAndMoveSnippet = """
      if [ -n "${'$'}__EXP_MODE" ]; then chmod "${'$'}__EXP_MODE" $qTmp 2>/dev/null; fi
      if [ -n "${'$'}__EXP_OWNER" ]; then chown "${'$'}__EXP_OWNER" $qTmp 2>/dev/null; fi
      if [ -n "${'$'}__EXP_CTX" ] && [ "${'$'}__EXP_CTX" != "?" ]; then chcon "${'$'}__EXP_CTX" $qTmp 2>/dev/null; fi
      mv -f $qTmp $qPath
    """.trimIndent()

    val fullCmd = backupSnippet + metaCaptureSnippet + "\n" + writeTmpSnippet + "\n" + restoreAndMoveSnippet
    val msg = if (createBackup) "Saved ${file.name} (.bak backup created)" else "Saved ${file.name}"
    execWriteCommand(fullCmd, useSu, msg)
  }

  suspend fun writeFileFromLocalTemp(
    tempFile: File,
    destPath: String,
    useSu: Boolean,
    createBackup: Boolean = true,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    if (!tempFile.exists()) return@withContext ShellOpResult(false, "Temporary database file missing")
    val qSrc = shellQuote(tempFile.absolutePath)
    val qDest = shellQuote(destPath)
    val qBak = shellQuote("$destPath.bak")
    val tmpDest = "$destPath.tmp.${UUID.randomUUID().toString().replace("-", "").take(8)}"
    val qTmp = shellQuote(tmpDest)

    val backupSnippet = if (createBackup) {
      "if [ -e $qDest ]; then cp -p $qDest $qBak || cp -f $qDest $qBak; fi\n"
    } else {
      ""
    }
    val cmd = """
      ${backupSnippet}__EXP_MODE=""
      __EXP_OWNER=""
      __EXP_CTX=""
      if [ -e $qDest ]; then
        __EXP_MODE=$(stat -c '%a' $qDest 2>/dev/null)
        __EXP_OWNER=$(stat -c '%u:%g' $qDest 2>/dev/null)
        __EXP_CTX=$(stat -c '%C' $qDest 2>/dev/null)
      fi
      cp -f $qSrc $qTmp || exit 1
      if [ -n "${'$'}__EXP_MODE" ]; then chmod "${'$'}__EXP_MODE" $qTmp 2>/dev/null; fi
      if [ -n "${'$'}__EXP_OWNER" ]; then chown "${'$'}__EXP_OWNER" $qTmp 2>/dev/null; fi
      if [ -n "${'$'}__EXP_CTX" ] && [ "${'$'}__EXP_CTX" != "?" ]; then chcon "${'$'}__EXP_CTX" $qTmp 2>/dev/null; fi
      mv -f $qTmp $qDest && rm -f ${shellQuote("$destPath-wal")} ${shellQuote("$destPath-shm")} ${shellQuote("$destPath-journal")}
    """.trimIndent()
    execWriteCommand(cmd, useSu, "Committed changes to ${File(destPath).name}")
  }

  suspend fun installApkWithRoot(apkPath: String): ShellOpResult = withContext(Dispatchers.IO) {
    if (!isSuActive) return@withContext ShellOpResult(false, "SU required for silent root install")
    val (code, out, err) = runSuCommand("pm install -r ${shellQuote(apkPath)}")
    val msg = (out + "\n" + err).trim().ifEmpty { if (code == 0) "APK installed" else "pm install failed ($code)" }
    ShellOpResult(code == 0 && !out.contains("Failure", ignoreCase = true), msg)
  }

  suspend fun readMounts(useSu: Boolean): List<MountPartition> = withContext(Dispatchers.IO) {
    val direct = runCatching { File("/proc/mounts").takeIf { it.canRead() }?.readText() }.getOrNull()
    if (!direct.isNullOrBlank()) {
      return@withContext ExploreDParsers.parseProcMounts(direct)
    }
    val (_, out, _) = if (useSu && isSuActive) {
      runSuCommand("cat /proc/mounts")
    } else {
      runNonRootSh("cat /proc/mounts")
    }
    ExploreDParsers.parseProcMounts(out)
  }

  suspend fun copyReadableToTemp(
    sourcePath: String,
    tempFile: File,
    useSu: Boolean,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    tempFile.parentFile?.mkdirs()
    val src = File(sourcePath)
    val tempParent = tempFile.parentFile
    val sidecarSuffixes = listOf("-wal", "-shm", "-journal")
    if (!useSu && src.canRead()) {
      val ok = runCatching {
        src.copyTo(tempFile, overwrite = true)
        tempFile.setReadable(true, false)
        tempFile.setWritable(true, true)
        for (suffix in sidecarSuffixes) {
          val sidecarSrc = File("$sourcePath$suffix")
          val sidecarDst = File("${tempFile.absolutePath}$suffix")
          if (sidecarSrc.exists() && sidecarSrc.canRead()) {
            sidecarSrc.copyTo(sidecarDst, overwrite = true)
            sidecarDst.setReadable(true, false)
            sidecarDst.setWritable(true, true)
          } else if (sidecarDst.exists()) {
            sidecarDst.delete()
          }
        }
        true
      }.getOrDefault(false)
      if (ok) return@withContext ShellOpResult(true, "Ready")
    }

    runCatching {
      if (!tempFile.exists()) tempFile.createNewFile()
      tempFile.setReadable(true, true)
      tempFile.setWritable(true, true)
    }

    val qSrc = shellQuote(sourcePath)
    val qTmp = shellQuote(tempFile.absolutePath)
    val qParent = shellQuote(tempParent?.absolutePath ?: "/data/local/tmp")
    val sidecarSnippets = sidecarSuffixes.joinToString("\n") { suffix ->
      val qSideSrc = shellQuote("$sourcePath$suffix")
      val qSideTmp = shellQuote("${tempFile.absolutePath}$suffix")
      """
        if [ -e $qSideSrc ]; then
          cat $qSideSrc > $qSideTmp 2>/dev/null || cp -f $qSideSrc $qSideTmp
          if [ -n "${'$'}__EXP_APP_OWNER" ]; then chown "${'$'}__EXP_APP_OWNER" $qSideTmp 2>/dev/null; fi
          chmod 0666 $qSideTmp 2>/dev/null || chmod 0660 $qSideTmp 2>/dev/null || true
        else
          rm -f $qSideTmp 2>/dev/null || true
        fi
      """.trimIndent()
    }
    val cmd = """
      __EXP_APP_OWNER=$(stat -c '%u:%g' $qParent 2>/dev/null)
      cat $qSrc > $qTmp || cp -f $qSrc $qTmp || exit 1
      if [ -n "${'$'}__EXP_APP_OWNER" ]; then chown "${'$'}__EXP_APP_OWNER" $qTmp 2>/dev/null; fi
      chmod 0666 $qTmp 2>/dev/null || chmod 0660 $qTmp 2>/dev/null || exit 1
      $sidecarSnippets
    """.trimIndent()
    val res = execWriteCommand(cmd, useSu, "Prepared local copy")
    if (res.success) {
      runCatching {
        tempFile.setReadable(true, false)
        tempFile.setWritable(true, true)
      }
    }
    res
  }

  suspend fun copyDirContents(
    tempSrcDir: File,
    targetDir: String,
    useSu: Boolean,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    val children = tempSrcDir.listFiles()?.map { it.absolutePath }.orEmpty()
    if (children.isEmpty()) return@withContext ShellOpResult(true, "Extracted 0 items")
    copyEntries(children, targetDir, useSu)
  }

  suspend fun listRarEntries(archivePath: String, useSu: Boolean): Result<List<ArchiveEntryItem>> =
    withContext(Dispatchers.IO) {
      val tool = resolveRarTool()
        ?: return@withContext Result.failure(
          IllegalStateException("RAR inspection requires unrar, 7z, or bsdtar on device"),
        )
      val qTool = shellQuote(tool)
      val qArchive = shellQuote(archivePath)
      val cmd = when {
        tool.endsWith("unrar") -> "$qTool lb $qArchive"
        tool.endsWith("bsdtar") -> "$qTool -tf $qArchive"
        else -> "$qTool l -ba $qArchive"
      }
      val (code, out, err) = if (useSu && isSuActive) runSuCommand(cmd) else runNonRootSh(cmd)
      if (code != 0) {
        return@withContext Result.failure(IllegalStateException(err.trim().ifEmpty { "Failed to read RAR archive" }))
      }
      val entries = out.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
          val cleanName = if (tool.endsWith("7z") || tool.endsWith("7za")) {
            line.split(Regex("\\s+")).lastOrNull().orEmpty()
          } else {
            line
          }
          ArchiveEntryItem(
            name = cleanName,
            compressedSize = 0L,
            uncompressedSize = 0L,
            isDirectory = cleanName.endsWith("/"),
          )
        }
        .filter { it.name.isNotEmpty() }
        .toList()
      Result.success(entries)
    }

  suspend fun extractRarArchive(
    archivePath: String,
    targetDir: String,
    useSu: Boolean,
  ): ShellOpResult = withContext(Dispatchers.IO) {
    val tool = resolveRarTool()
      ?: return@withContext ShellOpResult(false, "RAR extraction requires unrar, 7z, or bsdtar binary on device")
    val qTool = shellQuote(tool)
    val qArchive = shellQuote(archivePath)
    val qDest = shellQuote(targetDir)
    val cmd = when {
      tool.endsWith("unrar") -> "mkdir -p $qDest && $qTool x -o+ $qArchive $qDest/"
      tool.endsWith("bsdtar") -> "mkdir -p $qDest && $qTool -xf $qArchive -C $qDest"
      else -> "mkdir -p $qDest && $qTool x -y -o$qDest $qArchive"
    }
    execWriteCommand(cmd, useSu, "Extracted RAR archive")
  }

  suspend fun getSeLinuxMode(): String = withContext(Dispatchers.IO) {
    val (code, out, _) = if (isSuActive) {
      runSuCommand("getenforce")
    } else {
      runNonRootSh("getenforce")
    }
    if (code == 0 && out.isNotBlank()) out.trim() else "Unknown"
  }

  suspend fun setSeLinuxPermissive(permissive: Boolean): ShellOpResult = withContext(Dispatchers.IO) {
    if (!isSuActive) return@withContext ShellOpResult(false, "SU required to toggle SELinux")
    val flag = if (permissive) "0" else "1"
    val label = if (permissive) "Permissive" else "Enforcing"
    val (code, _, err) = runSuCommand("setenforce $flag")
    if (code == 0) {
      ShellOpResult(true, "SELinux set to $label")
    } else {
      ShellOpResult(false, err.trim().ifEmpty { "Failed to change SELinux mode" })
    }
  }

  suspend fun runRootCommand(command: String): ShellOpResult = withContext(Dispatchers.IO) {
    val trimmed = command.trim()
    if (trimmed.isEmpty()) return@withContext ShellOpResult(false, "Enter a command")
    val (code, out, err) = if (isSuActive) {
      runSuCommand(trimmed)
    } else {
      runNonRootSh(trimmed)
    }
    val combined = buildString {
      if (out.isNotBlank()) append(out.trimEnd())
      if (err.isNotBlank()) {
        if (isNotEmpty()) append("\n")
        append(err.trimEnd())
      }
      if (isEmpty()) append("(exit $code)")
    }
    ShellOpResult(code == 0, combined)
  }

  suspend fun rebootDevice(target: String): ShellOpResult = withContext(Dispatchers.IO) {
    if (!isSuActive) return@withContext ShellOpResult(false, "SU required to reboot")
    val arg = target.trim()
    val cmd = if (arg.isEmpty()) "svc power reboot || reboot" else "svc power reboot $arg || reboot $arg"
    execWriteCommand(cmd, useSu = true, "Rebooting device…")
  }

  private suspend fun execWriteCommand(
    cmd: String,
    useSu: Boolean,
    successMessage: String,
  ): ShellOpResult {
    val (code, _, err) = if (useSu && isSuActive) {
      runSuCommand(cmd)
    } else {
      runNonRootSh(cmd)
    }
    return if (code == 0) {
      ShellOpResult(true, successMessage)
    } else {
      val cleanErr = err.trim().ifEmpty { "Operation failed (exit $code)" }
      ShellOpResult(false, cleanErr)
    }
  }

  private suspend fun runSuCommand(command: String): Triple<Int, String, String> =
    mutex.withLock {
      val session = suSession
      if (session == null || !isProcessAlive(session.process)) {
        suSession = null
        return@withLock Triple(1, "", "SU session inactive")
      }

      val token = UUID.randomUUID().toString().replace("-", "").take(12)
      val deferred = CompletableDeferred<Int>()
      val stdoutBuf = StringBuilder()
      val stderrBuf = StringBuilder()
      pendingCommand = PendingCommand(token, stdoutBuf, stderrBuf, deferred)

      val sent = runCatching {
        session.writer.write(command)
        session.writer.write("\n__EXP_EC=$?\n")
        session.writer.write("printf '${MARKER_PREFIX}%s__:%d\\n' '$token' \"\$__EXP_EC\"\n")
        session.writer.flush()
      }.isSuccess

      if (!sent) {
        pendingCommand = null
        return@withLock Triple(1, "", "SU write error")
      }

      val exitCode = withTimeoutOrNull(15_000L) { deferred.await() } ?: 124
      pendingCommand = null
      Triple(exitCode, stdoutBuf.toString(), stderrBuf.toString())
    }

  private fun runNonRootSh(command: String): Triple<Int, String, String> {
    return runCatching {
      val proc = ProcessBuilder(resolveShBinary(), "-c", command)
        .redirectErrorStream(false)
        .start()
      val out = proc.inputStream.bufferedReader().readText()
      val err = proc.errorStream.bufferedReader().readText()
      val code = proc.waitFor()
      Triple(code, out, err)
    }.getOrElse { err ->
      Triple(1, "", err.message ?: "sh execution failed")
    }
  }

  private fun handleSuStdout(line: String) {
    val probe = pendingSuProbe
    if (probe != null) {
      val tag = "${SU_PROBE_PREFIX}${probe.first}"
      if (line.contains(tag)) {
        probe.second.complete(true)
        return
      }
    }

    val pending = pendingCommand
    if (pending != null) {
      val tag = "${MARKER_PREFIX}${pending.token}__:"
      val idx = line.indexOf(tag)
      if (idx >= 0) {
        val before = line.substring(0, idx)
        if (before.isNotEmpty()) pending.output.appendLine(before)
        val code = line.substring(idx + tag.length).trim().toIntOrNull() ?: 0
        pending.deferred.complete(code)
        return
      }
      pending.output.appendLine(line)
    }
  }

  private fun fileToEntry(file: File): FileEntry {
    val isDir = file.isDirectory
    val perms = buildString {
      append(if (isDir) 'd' else '-')
      append(if (file.canRead()) 'r' else '-')
      append(if (file.canWrite()) 'w' else '-')
      append(if (file.canExecute()) 'x' else '-')
      append("------")
    }
    val octal = PermissionBits.fromSymbolic(perms).toOctalString()
    return FileEntry(
      name = file.name,
      path = file.absolutePath,
      isDirectory = isDir,
      isSymlink = false,
      sizeBytes = if (isDir) 0L else file.length(),
      modifiedDisplay = ExploreDParsers.formatTimestamp(file.lastModified()),
      modifiedEpochMillis = file.lastModified(),
      permissionsSymbolic = perms,
      permissionsOctal = octal,
    )
  }

  private fun resolveUniqueCopyTarget(targetDir: File, fileName: String): File {
    val initial = File(targetDir, fileName)
    if (!initial.exists()) return initial
    val dotIdx = fileName.lastIndexOf('.')
    val base = if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
    val ext = if (dotIdx > 0) fileName.substring(dotIdx) else ""
    var counter = 1
    while (true) {
      val candidate = File(targetDir, "${base}_copy$counter$ext")
      if (!candidate.exists()) return candidate
      counter++
    }
  }

  private fun closeSuSession(session: SuSession) {
    if (!session.closed.compareAndSet(false, true)) return
    runCatching { session.writer.close() }
    runCatching { session.process.destroy() }
    if (isProcessAlive(session.process)) {
      runCatching { session.process.destroyForcibly() }
    }
    session.stdoutJob.cancel()
    session.stderrJob.cancel()
    session.monitorJob.cancel()
  }

  private fun isProcessAlive(process: Process): Boolean =
    runCatching {
      process.exitValue()
      false
    }.getOrDefault(true)
}
