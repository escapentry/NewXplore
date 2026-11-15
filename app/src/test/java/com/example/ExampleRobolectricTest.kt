package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.PinPreferences
import com.example.display.DisplayAutoScaler
import com.example.files.ApkInspectorInfo
import com.example.files.ArchiveAndSqliteHelper
import com.example.files.BinaryXmlParser
import com.example.files.BrowserTabState
import com.example.files.ExploreDParsers
import com.example.files.ExploreDUiState
import com.example.files.FileEntry
import com.example.files.MountPartition
import com.example.files.PermissionBits
import com.example.files.RootShellManager
import com.example.files.SortOrder
import com.example.ui.ExploreDScreen
import com.example.ui.components.ApkActionDialog
import com.example.ui.components.BrowserTabStrip
import com.example.ui.components.FileBrowserPane
import com.example.ui.components.OrbitLoadingScreen
import com.example.ui.components.PermissionsDialog
import com.example.ui.components.SelectionBottomBar
import com.example.ui.components.SystemMountBar
import com.example.ui.components.ToolsSheet
import com.example.ui.theme.ExploreDTheme
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `app name matches ExploreD`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("ExploreD", context.getString(R.string.app_name))
  }

  @Test
  fun `system partition check distinguishes system paths from shared internal storage`() {
    val internal = "/storage/emulated/0"
    assertFalse(ExploreDParsers.isSystemPartitionPath(internal, internal))
    assertFalse(ExploreDParsers.isSystemPartitionPath("$internal/Download/file.zip", internal))
    assertFalse(ExploreDParsers.isSystemPartitionPath("/sdcard/Documents", internal))
    assertFalse(ExploreDParsers.isSystemPartitionPath("/data/media/0", internal))

    assertTrue(ExploreDParsers.isSystemPartitionPath("/", internal))
    assertTrue(ExploreDParsers.isSystemPartitionPath("/system/build.prop", internal))
    assertTrue(ExploreDParsers.isSystemPartitionPath("/vendor/etc", internal))
    assertTrue(ExploreDParsers.isSystemPartitionPath("/data/local/tmp", internal))
  }

  @Test
  fun `permission bits convert bidirectionally between octal symbolic and flags`() {
    val p755 = PermissionBits.fromOctal("755")
    assertNotNull(p755)
    assertEquals("0755", p755!!.toOctalString())
    assertEquals("rwxr-xr-x", p755.toSymbolicString())
    assertTrue(p755.ownerWrite)
    assertFalse(p755.groupWrite)
    assertTrue(p755.otherExecute)

    val p4755 = PermissionBits.fromOctal("4755")!!
    assertTrue(p4755.setUid)
    assertEquals("rwsr-xr-x", p4755.toSymbolicString())

    val fromSym = PermissionBits.fromSymbolic("drwxr-x--t")
    assertEquals("1751", fromSym.toOctalString())
    assertTrue(fromSym.sticky)

    assertNull(PermissionBits.fromOctal("999"))
    assertNull(PermissionBits.fromOctal("12"))
  }

  @Test
  fun `parsers handle ls -la output and proc mounts accurately`() {
    val lsRaw = """
      total 48
      drwxr-xr-x 22 root root 4096 2024-05-12 08:14 system
      lrw-r--r--  1 root root   11 2024-05-12 08:14 bin -> /system/bin
      -rw-r--r--  1 root root 2048 2024-05-12 08:14 my config.prop
      -rw-------  1 root root   64 2024-05-12 08:14 .secret
    """.trimIndent()

    val visibleOnly = ExploreDParsers.parseLsOutput(lsRaw, "/", showHidden = false)
    assertEquals(3, visibleOnly.size)
    assertEquals("system", visibleOnly[0].name)
    assertTrue(visibleOnly[0].isDirectory)
    assertEquals("/system/bin", visibleOnly[1].symlinkTarget)
    assertEquals("my config.prop", visibleOnly[2].name)
    assertEquals("0644", visibleOnly[2].permissionsOctal)

    val withHidden = ExploreDParsers.parseLsOutput(lsRaw, "/", showHidden = true)
    assertEquals(4, withHidden.size)

    val mountsRaw = """
      /dev/block/dm-0 / ext4 ro,seclabel,relatime 0 0
      /dev/block/dm-1 /vendor ext4 ro,seclabel,relatime 0 0
      /dev/block/dm-5 /data f2fs rw,lazytime,seclabel 0 0
    """.trimIndent()
    val mounts = ExploreDParsers.parseProcMounts(mountsRaw)
    assertEquals(3, mounts.size)
    val rootMount = mounts.first { it.mountPoint == "/" }
    val dataMount = mounts.first { it.mountPoint == "/data" }
    assertFalse(rootMount.isReadWrite)
    assertTrue(dataMount.isReadWrite)
  }

  @Test
  fun `pin preferences hash and verify SU pin securely and update without clearing`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PinPreferences(context)
    prefs.setPin("2580")
    assertTrue(prefs.hasPin())
    assertTrue(prefs.verifyPin("2580"))
    assertFalse(prefs.verifyPin("0000"))

    prefs.setPin("9012")
    assertTrue(prefs.verifyPin("9012"))
    assertFalse(prefs.verifyPin("2580"))
  }

  @Test
  fun `zip tar and sqlite helpers inspect and extract real files`() {
    val tempRoot = File(System.getProperty("java.tmpdir") ?: System.getProperty("java.io.tmpdir"), "explored_arch_${System.nanoTime()}")
    tempRoot.mkdirs()
    try {
      // 1. ZIP viewer & extractor
      val zipFile = File(tempRoot, "bundle.zip")
      ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
        zos.putNextEntry(ZipEntry("docs/readme.txt"))
        zos.write("hello zip".toByteArray())
        zos.closeEntry()
      }
      val zipEntries = ArchiveAndSqliteHelper.listZipOrApkEntries(zipFile).getOrThrow()
      assertEquals(1, zipEntries.size)
      assertEquals("docs/readme.txt", zipEntries[0].name)

      val zipOut = File(tempRoot, "zip_out")
      assertEquals(1, ArchiveAndSqliteHelper.extractZipOrApk(zipFile, zipOut).getOrThrow())
      assertEquals("hello zip", File(zipOut, "docs/readme.txt").readText())

      // 2. TAR viewer & extractor
      val tarFile = File(tempRoot, "archive.tar")
      tarFile.writeBytes(buildSingleEntryTar("notes.txt", "tar_ok".toByteArray()))
      val tarEntries = ArchiveAndSqliteHelper.listTarEntries(tarFile).getOrThrow()
      assertEquals(1, tarEntries.size)
      assertEquals("notes.txt", tarEntries[0].name)

      val tarOut = File(tempRoot, "tar_out")
      assertEquals(1, ArchiveAndSqliteHelper.extractTar(tarFile, tarOut).getOrThrow())
      assertEquals("tar_ok", File(tarOut, "notes.txt").readText())

      // 3. SQLite viewer
      val dbFile = File(tempRoot, "settings.db")
      SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
        db.execSQL("CREATE TABLE prefs (id INTEGER PRIMARY KEY, key TEXT, val TEXT)")
        db.execSQL("INSERT INTO prefs (id, key, val) VALUES (1, 'theme', 'obsidian')")
      }
      val dbInfo = ArchiveAndSqliteHelper.inspectSqliteDatabase(
        entry = FileEntry("settings.db", dbFile.absolutePath, isDirectory = false),
        readableFile = dbFile,
        tempCopyPath = null,
      )
      assertTrue("prefs" in dbInfo.tables)
      assertEquals("prefs", dbInfo.selectedTable)
      assertEquals(1, dbInfo.totalRows)
      assertEquals(listOf("1", "theme", "obsidian"), dbInfo.rows.first())
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  @Test
  fun `root shell manager handles non-root file operations and root listing`() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val manager = RootShellManager(scope)
    val tempRoot = File(System.getProperty("java.io.tmpdir"), "explored_test_${System.nanoTime()}")
    tempRoot.mkdirs()

    try {
      val rootList = manager.listDirectory("/", useSu = false, showHidden = true)
      assertTrue(rootList.entries.isNotEmpty())

      assertTrue(manager.createFolder(tempRoot.absolutePath, "alpha", useSu = false).success)
      assertTrue(manager.createFile(tempRoot.absolutePath, "hello.txt", useSu = false).success)

      val filePath = File(tempRoot, "hello.txt").absolutePath
      assertTrue(manager.writeTextFile(filePath, "explored_ok", useSu = false).success)
      assertEquals("explored_ok", manager.readTextFile(filePath, useSu = false).getOrNull())

      val destDir = File(tempRoot, "alpha").absolutePath
      assertTrue(manager.copyEntries(listOf(filePath), destDir, useSu = false).success)
      assertTrue(File(destDir, "hello.txt").exists())

      assertTrue(manager.renameEntry(filePath, "renamed.txt", useSu = false).success)
      assertTrue(File(tempRoot, "renamed.txt").exists())

      assertTrue(manager.deleteEntries(listOf(File(tempRoot, "renamed.txt").absolutePath), useSu = false).success)
      assertFalse(File(tempRoot, "renamed.txt").exists())
    } finally {
      manager.close()
      scope.cancel()
      tempRoot.deleteRecursively()
    }
  }

  @Test
  fun `permissions dialog says Permissions without chmod and syncs checkboxes and octal`() {
    var appliedOctal = ""
    val sample = FileEntry(
      name = "init.rc",
      path = "/system/etc/init.rc",
      isDirectory = false,
      permissionsSymbolic = "-rwxr-xr-x",
      permissionsOctal = "0755",
      owner = "root",
      group = "root",
    )

    composeTestRule.setContent {
      ExploreDTheme {
        PermissionsDialog(
          entry = sample,
          isSuActive = false,
          onApply = { octal, _, _ -> appliedOctal = octal },
          onDismiss = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Permissions").assertIsDisplayed()
    composeTestRule.onNodeWithTag("chmod_octal_input").assertTextContains("0755")
    composeTestRule.onNodeWithTag("chmod_checkbox_other_w").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("chmod_octal_input").performScrollTo().assertTextContains("0757")

    composeTestRule.onNodeWithTag("chmod_octal_input").performScrollTo().performTextReplacement("0600")
    composeTestRule.onNodeWithTag("chmod_symbolic_preview").performScrollTo().assertTextContains("rw-------")

    composeTestRule.onNodeWithTag("chmod_apply_button").performClick()
    assertEquals("0600", appliedOctal)
  }

  @Test
  fun `non-system partitions hide permissions button and system partitions show Permissions menu`() {
    val nonSystemState = ExploreDUiState(
      currentPath = "/storage/emulated/0/Download",
      internalStoragePath = "/storage/emulated/0",
      visibleEntries = listOf(
        FileEntry("photo.jpg", "/storage/emulated/0/Download/photo.jpg", false),
      ),
      selectedPaths = setOf("/storage/emulated/0/Download/photo.jpg"),
    )
    val profile = DisplayAutoScaler.computeProfile(400f, 800f, 2f, 1f)

    composeTestRule.setContent {
      ExploreDTheme {
        SelectionBottomBar(
          selectedCount = 1,
          singleSelectedEntry = nonSystemState.visibleEntries.first(),
          allowPermissions = nonSystemState.isCurrentSystemPartition,
          onCopy = {},
          onMove = {},
          onPermissions = {},
          onRename = {},
          onDelete = {},
          onSelectAll = {},
          onClear = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("sel_permissions_button").assertDoesNotExist()
  }

  @Test
  fun `system partition row menu shows Permissions without chmod`() {
    val systemState = ExploreDUiState(
      currentPath = "/system/etc",
      internalStoragePath = "/storage/emulated/0",
      visibleEntries = listOf(
        FileEntry("hosts", "/system/etc/hosts", false),
      ),
    )
    val profile = DisplayAutoScaler.computeProfile(400f, 800f, 2f, 1f)

    composeTestRule.setContent {
      ExploreDTheme {
        FileBrowserPane(
          state = systemState,
          profile = profile,
          onEntryClick = {},
          onEntryLongClick = {},
          onUnlockSu = {},
          onInspectText = {},
          onOpenArchive = {},
          onExtractArchive = {},
          onOpenSqlite = {},
          onPermissions = {},
          onRename = {},
          onCopySingle = {},
          onMoveSingle = {},
          onDeleteSingle = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("file_menu_hosts").performClick()
    composeTestRule.onNodeWithTag("action_permissions_hosts").assertIsDisplayed()
    composeTestRule.onNodeWithText("Permissions").assertIsDisplayed()
  }

  @Test
  fun `tools sheet hides clear pin and mount controls while exposing root tools`() {
    composeTestRule.setContent {
      ExploreDTheme {
        ToolsSheet(
          isSuActive = true,
          hasPinConfigured = true,
          showHiddenFiles = true,
          sortOrder = SortOrder.NAME,
          selinuxMode = "Enforcing",
          quickToolOutput = null,
          onToggleSu = {},
          onChangePin = {},
          onToggleSeLinux = {},
          onRunRootCommand = {},
          onReboot = {},
          onToggleShowHidden = {},
          onChangeSortOrder = {},
          onDismiss = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Clear PIN").assertDoesNotExist()
    composeTestRule.onNodeWithText("Partition Mounts (RO / RW)").assertDoesNotExist()
    composeTestRule.onNodeWithTag("tools_change_pin_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("tools_selinux_toggle").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("tools_quick_cmd_run").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `explored screen displays header quick jumps without mount chip and opens PIN dialog`() {
    composeTestRule.setContent {
      ExploreDTheme {
        ExploreDScreen()
      }
    }

    composeTestRule.onNodeWithTag("su_toggle_chip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mount_status_chip").assertDoesNotExist()
    composeTestRule.onNodeWithTag("tools_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("quick_jump_/").assertIsDisplayed()
    composeTestRule.onNodeWithTag("su_toggle_chip").performClick()
    composeTestRule.onNodeWithTag("pin_dialog").assertIsDisplayed()
  }

  @Test
  fun `orbit loading screen displays ExploreD branding`() {
    composeTestRule.setContent {
      ExploreDTheme {
        OrbitLoadingScreen()
      }
    }
    composeTestRule.onNodeWithTag("orbit_loading_screen").assertIsDisplayed()
    composeTestRule.onNodeWithText("ExploreD").assertIsDisplayed()
  }

  @Test
  fun `binary xml parser decodes ABX XML into readable markup`() {
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)
    dos.write(byteArrayOf('A'.code.toByte(), 'B'.code.toByte(), 'X'.code.toByte(), 0))
    dos.writeByte(0x02) // START_TAG
    dos.writeShort(-1)
    dos.writeUTF("map")
    dos.writeByte(0x2F) // ATTRIBUTE + STRING (0x20 | 0x0F)
    dos.writeShort(-1)
    dos.writeUTF("version")
    dos.writeUTF("1")
    dos.writeByte(0x03) // END_TAG
    dos.writeShort(0) // interned "map"
    dos.flush()

    val bytes = baos.toByteArray()
    assertTrue(BinaryXmlParser.isBinaryXml(bytes))
    val xml = BinaryXmlParser.decodeToXmlString(bytes).getOrThrow()
    assertTrue(xml.contains("<map version=\"1\" />"))
  }

  @Test
  fun `sqlite helper supports cell update row deletion and custom sql queries`() {
    val tempRoot = File(System.getProperty("java.tmpdir"), "explored_sql_${System.nanoTime()}")
    tempRoot.mkdirs()
    try {
      val dbFile = File(tempRoot, "app.db")
      SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
        db.execSQL("CREATE TABLE kv (k TEXT, v TEXT)")
        db.execSQL("INSERT INTO kv (k, v) VALUES ('mode', 'ro')")
        db.execSQL("INSERT INTO kv (k, v) VALUES ('user', 'root')")
      }
      val entry = FileEntry("app.db", dbFile.absolutePath, false)
      var info = ArchiveAndSqliteHelper.inspectSqliteDatabase(entry, dbFile, tempCopyPath = dbFile.absolutePath)
      assertEquals(2, info.rows.size)

      info = ArchiveAndSqliteHelper.updateSqliteCell(info, dbFile, rowIndex = 0, columnIndex = 1, newValue = "rw")
      assertTrue(info.hasPendingChanges)
      assertEquals("rw", info.rows[0][1])

      info = ArchiveAndSqliteHelper.executeSqliteQuery(info, dbFile, "SELECT v FROM kv WHERE k='mode';")
      assertEquals(listOf(listOf("rw")), info.rows)

      info = ArchiveAndSqliteHelper.inspectSqliteDatabase(entry, dbFile, tempCopyPath = dbFile.absolutePath)
      info = ArchiveAndSqliteHelper.deleteSqliteRow(info, dbFile, rowIndex = 1)
      assertEquals(1, info.totalRows)
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  @Test
  fun `writeTextFile creates bak backup when requested`() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val manager = RootShellManager(scope)
    val tempRoot = File(System.getProperty("java.tmpdir"), "explored_bak_${System.nanoTime()}")
    tempRoot.mkdirs()
    try {
      val cfg = File(tempRoot, "build.prop")
      cfg.writeText("ro.debuggable=0\n")
      val res = manager.writeTextFile(cfg.absolutePath, "ro.debuggable=1\n", useSu = false, createBackup = true)
      assertTrue(res.success)
      assertEquals("ro.debuggable=1\n", cfg.readText())
      assertEquals("ro.debuggable=0\n", File(tempRoot, "build.prop.bak").readText())
    } finally {
      manager.close()
      scope.cancel()
      tempRoot.deleteRecursively()
    }
  }

  @Test
  fun `system mount bar tab strip and apk action dialog render expected controls`() {
    val profile = DisplayAutoScaler.computeProfile(400f, 800f, 2f, 1f)
    val state = ExploreDUiState(
      tabs = listOf(
        BrowserTabState("t1", "/storage/emulated/0"),
        BrowserTabState("t2", "/system"),
      ),
      activeTabId = "t2",
      currentPath = "/system",
      activeMount = MountPartition("/dev/block/dm-0", "/system", "ext4", isReadWrite = false, "ro"),
    )
    val apkInfo = ApkInspectorInfo(
      entry = FileEntry("App.apk", "/sdcard/App.apk", false, sizeBytes = 1024L),
      appLabel = "DemoApp",
      packageName = "com.example.demo",
      versionName = "2.1",
      versionCode = 21L,
      minSdk = 24,
      targetSdk = 36,
    )

    composeTestRule.setContent {
      ExploreDTheme {
        androidx.compose.foundation.layout.Column {
          BrowserTabStrip(
            tabs = state.tabs,
            activeTabId = state.activeTabId,
            profile = profile,
            onSelectTab = {},
            onCloseTab = {},
            onAddTab = {},
          )
          SystemMountBar(state = state, profile = profile, onToggleMount = {})
          ApkActionDialog(
            info = apkInfo,
            isSuActive = true,
            onViewContents = {},
            onInstall = {},
            onRootInstall = {},
            onDismiss = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("browser_tab_strip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("mount_rw_ro_toggle").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mount R/W").assertIsDisplayed()
    composeTestRule.onNodeWithTag("apk_view_contents_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("apk_install_button").assertIsDisplayed()
  }

  private fun buildSingleEntryTar(fileName: String, content: ByteArray): ByteArray {
    val header = ByteArray(512)
    val nameBytes = fileName.toByteArray(Charsets.UTF_8)
    System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 100))
    writeOctal(header, 100, 8, 0b110_100_100L) // 0644
    writeOctal(header, 108, 8, 0L)
    writeOctal(header, 116, 8, 0L)
    writeOctal(header, 124, 12, content.size.toLong())
    writeOctal(header, 136, 12, 1700000000L)
    for (i in 148 until 156) header[i] = ' '.code.toByte()
    header[156] = '0'.code.toByte()
    val checksum = header.sumOf { it.toInt() and 0xFF }
    writeOctal(header, 148, 7, checksum.toLong())
    header[155] = 0

    val out = ByteArrayOutputStream()
    out.write(header)
    out.write(content)
    val pad = (512 - (content.size % 512)) % 512
    if (pad > 0) out.write(ByteArray(pad))
    out.write(ByteArray(1024)) // EOF blocks
    return out.toByteArray()
  }

  private fun writeOctal(buf: ByteArray, offset: Int, len: Int, value: Long) {
    val oct = value.toString(8).padStart(len - 1, '0')
    for (i in 0 until len - 1) {
      buf[offset + i] = oct[i].code.toByte()
    }
    buf[offset + len - 1] = 0
  }
}
