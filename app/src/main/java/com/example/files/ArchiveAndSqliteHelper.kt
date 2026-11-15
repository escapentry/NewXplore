package com.example.files

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

object ArchiveAndSqliteHelper {

  fun listZipOrApkEntries(file: File): Result<List<ArchiveEntryItem>> = runCatching {
    ZipFile(file).use { zip ->
      val list = mutableListOf<ArchiveEntryItem>()
      val entries = zip.entries()
      while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        list += ArchiveEntryItem(
          name = entry.name,
          compressedSize = entry.compressedSize.coerceAtLeast(0L),
          uncompressedSize = entry.size.coerceAtLeast(0L),
          isDirectory = entry.isDirectory,
        )
      }
      list.sortedWith(compareBy<ArchiveEntryItem> { !it.isDirectory }.thenBy { it.name })
    }
  }

  fun extractZipOrApk(archiveFile: File, destDir: File): Result<Int> = runCatching {
    destDir.mkdirs()
    val canonicalDest = destDir.canonicalPath
    var count = 0
    ZipFile(archiveFile).use { zip ->
      val entries = zip.entries()
      while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        val outFile = File(destDir, entry.name)
        if (!outFile.canonicalPath.startsWith(canonicalDest + File.separator) &&
          outFile.canonicalPath != canonicalDest
        ) {
          throw SecurityException("Blocked unsafe path in archive: ${entry.name}")
        }
        if (entry.isDirectory) {
          outFile.mkdirs()
        } else {
          outFile.parentFile?.mkdirs()
          zip.getInputStream(entry).use { input ->
            FileOutputStream(outFile).use { output ->
              input.copyTo(output)
            }
          }
          count++
        }
      }
    }
    count
  }

  fun listTarEntries(file: File): Result<List<ArchiveEntryItem>> = runCatching {
    openTarStream(file).use { stream ->
      val items = mutableListOf<ArchiveEntryItem>()
      val header = ByteArray(512)
      while (true) {
        val read = readFully(stream, header)
        if (read < 512 || header.all { it == 0.toByte() }) break
        val name = extractNullTerminatedString(header, 0, 100)
        if (name.isEmpty()) break
        val prefix = extractNullTerminatedString(header, 345, 155)
        val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name
        val size = parseTarOctal(header, 124, 12)
        val typeFlag = header[156].toInt().toChar()
        val isDir = typeFlag == '5' || fullName.endsWith("/")
        if (typeFlag != 'L' && typeFlag != 'x' && typeFlag != 'g') {
          items += ArchiveEntryItem(
            name = fullName,
            compressedSize = size,
            uncompressedSize = size,
            isDirectory = isDir,
          )
        }
        val padded = ((size + 511L) / 512L) * 512L
        skipExactly(stream, padded)
      }
      items.sortedWith(compareBy<ArchiveEntryItem> { !it.isDirectory }.thenBy { it.name })
    }
  }

  fun extractTar(archiveFile: File, destDir: File): Result<Int> = runCatching {
    destDir.mkdirs()
    val canonicalDest = destDir.canonicalPath
    var count = 0
    openTarStream(archiveFile).use { stream ->
      val header = ByteArray(512)
      while (true) {
        val read = readFully(stream, header)
        if (read < 512 || header.all { it == 0.toByte() }) break
        val name = extractNullTerminatedString(header, 0, 100)
        if (name.isEmpty()) break
        val prefix = extractNullTerminatedString(header, 345, 155)
        val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name
        val size = parseTarOctal(header, 124, 12)
        val typeFlag = header[156].toInt().toChar()
        val isDir = typeFlag == '5' || fullName.endsWith("/")

        val outFile = File(destDir, fullName)
        if (!outFile.canonicalPath.startsWith(canonicalDest + File.separator) &&
          outFile.canonicalPath != canonicalDest
        ) {
          throw SecurityException("Blocked unsafe path in tar: $fullName")
        }

        if (isDir) {
          outFile.mkdirs()
          val padded = ((size + 511L) / 512L) * 512L
          skipExactly(stream, padded)
        } else if (typeFlag == '0' || typeFlag == '\u0000') {
          outFile.parentFile?.mkdirs()
          FileOutputStream(outFile).use { output ->
            copyExactBytes(stream, output, size)
          }
          val remainder = (512L - (size % 512L)) % 512L
          skipExactly(stream, remainder)
          count++
        } else {
          val padded = ((size + 511L) / 512L) * 512L
          skipExactly(stream, padded)
        }
      }
    }
    count
  }

  fun readZipEntryBytes(
    archiveFile: File,
    entryName: String,
    maxBytes: Int = 1_048_576,
  ): Result<ByteArray> = runCatching {
    ZipFile(archiveFile).use { zip ->
      val entry = zip.getEntry(entryName)
        ?: throw IllegalArgumentException("Entry not found: $entryName")
      zip.getInputStream(entry).use { input ->
        val out = ByteArrayOutputStream()
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

  @Suppress("DEPRECATION")
  fun inspectApkMetadata(
    context: Context,
    apkFile: File,
    entry: FileEntry,
  ): ApkInspectorInfo {
    val pm = context.packageManager
    val pkgInfo = runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0L))
      } else {
        pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
      }
    }.getOrNull()

    val appInfo = pkgInfo?.applicationInfo?.apply {
      sourceDir = apkFile.absolutePath
      publicSourceDir = apkFile.absolutePath
    }
    val label = runCatching {
      appInfo?.let { pm.getApplicationLabel(it).toString() }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: entry.name.removeSuffix(".apk")

    val versionCode = if (pkgInfo != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode else pkgInfo.versionCode.toLong()
    } else {
      0L
    }

    return ApkInspectorInfo(
      entry = entry,
      appLabel = label,
      packageName = pkgInfo?.packageName ?: "Unknown package",
      versionName = pkgInfo?.versionName ?: "—",
      versionCode = versionCode,
      minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo?.minSdkVersion else null,
      targetSdk = appInfo?.targetSdkVersion,
    )
  }

  fun checkpointSqliteDatabase(readableFile: File) {
    if (!readableFile.exists() || !readableFile.canWrite()) return
    runCatching {
      SQLiteDatabase.openDatabase(
        readableFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE,
      ).use { db ->
        checkpointOpenDb(db)
      }
    }
  }

  private fun openWritableDatabase(readableFile: File): SQLiteDatabase {
    runCatching {
      readableFile.setReadable(true, false)
      readableFile.setWritable(true, true)
    }
    return SQLiteDatabase.openDatabase(
      readableFile.absolutePath,
      null,
      SQLiteDatabase.OPEN_READWRITE,
    )
  }

  private fun checkpointOpenDb(db: SQLiteDatabase) {
    runCatching {
      db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
        cursor.moveToFirst()
      }
    }
  }

  fun inspectSqliteDatabase(
    entry: FileEntry,
    readableFile: File,
    tempCopyPath: String?,
    requestedTable: String? = null,
    hasPendingChanges: Boolean = false,
  ): SqliteDatabaseInfo {
    return runCatching {
      val openMode = if (readableFile.canWrite() || tempCopyPath != null) {
        runCatching { readableFile.setWritable(true, true) }
        if (readableFile.canWrite()) SQLiteDatabase.OPEN_READWRITE else SQLiteDatabase.OPEN_READONLY
      } else {
        SQLiteDatabase.OPEN_READONLY
      }
      SQLiteDatabase.openDatabase(
        readableFile.absolutePath,
        null,
        openMode,
      ).use { db ->
        if (openMode == SQLiteDatabase.OPEN_READWRITE) {
          checkpointOpenDb(db)
        }
        val tables = mutableListOf<String>()
        db.rawQuery(
          "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
          null,
        ).use { cursor ->
          while (cursor.moveToNext()) {
            tables += cursor.getString(0)
          }
        }
        val activeTable = requestedTable?.takeIf { it in tables }
          ?: tables.firstOrNull { it != "android_metadata" && it != "sqlite_sequence" }
          ?: tables.firstOrNull()

        if (activeTable == null) {
          return@use SqliteDatabaseInfo(
            entry = entry,
            tempCopyPath = tempCopyPath,
            tables = emptyList(),
            selectedTable = null,
            columns = emptyList(),
            rows = emptyList(),
            totalRows = 0,
            hasPendingChanges = hasPendingChanges,
          )
        }

        val escapedTable = activeTable.replace("\"", "\"\"")
        var totalRows = 0
        db.rawQuery("SELECT COUNT(*) FROM \"$escapedTable\"", null).use { c ->
          if (c.moveToFirst()) totalRows = c.getInt(0)
        }

        val columns = mutableListOf<String>()
        val rows = mutableListOf<List<String>>()
        val rowIds = mutableListOf<Long?>()
        var hasRowId = false

        val rowIdQueryWorked = runCatching {
          db.rawQuery("SELECT rowid AS _exp_rowid_, * FROM \"$escapedTable\" LIMIT 200", null).use { cursor ->
            val allCols = cursor.columnNames
            if (allCols.isNotEmpty() && allCols[0] == "_exp_rowid_") {
              hasRowId = true
              columns.addAll(allCols.drop(1))
              while (cursor.moveToNext()) {
                rowIds += if (cursor.isNull(0)) null else cursor.getLong(0)
                val row = ArrayList<String>(columns.size)
                for (i in 1 until allCols.size) {
                  row += formatCursorCell(cursor, i)
                }
                rows += row
              }
            }
          }
          true
        }.getOrDefault(false)

        if (!rowIdQueryWorked) {
          columns.clear()
          rows.clear()
          rowIds.clear()
          db.rawQuery("SELECT * FROM \"$escapedTable\" LIMIT 200", null).use { cursor ->
            columns.addAll(cursor.columnNames)
            while (cursor.moveToNext()) {
              rowIds += null
              val row = ArrayList<String>(columns.size)
              for (i in columns.indices) {
                row += formatCursorCell(cursor, i)
              }
              rows += row
            }
          }
        }

        SqliteDatabaseInfo(
          entry = entry,
          tempCopyPath = tempCopyPath,
          tables = tables,
          selectedTable = activeTable,
          columns = columns,
          rows = rows,
          totalRows = totalRows,
          rowIds = rowIds,
          hasRowId = hasRowId,
          sqlQuery = "SELECT * FROM \"$escapedTable\" LIMIT 200;",
          hasPendingChanges = hasPendingChanges,
        )
      }
    }.getOrElse { err ->
      SqliteDatabaseInfo(
        entry = entry,
        tempCopyPath = tempCopyPath,
        tables = emptyList(),
        selectedTable = null,
        columns = emptyList(),
        rows = emptyList(),
        totalRows = 0,
        error = err.message ?: "Unable to read SQLite database",
        hasPendingChanges = hasPendingChanges,
      )
    }
  }

  fun executeSqliteQuery(
    current: SqliteDatabaseInfo,
    readableFile: File,
    sql: String,
  ): SqliteDatabaseInfo {
    val trimmed = sql.trim()
    if (trimmed.isEmpty()) return current.copy(error = "Enter a SQL statement")
    val upper = trimmed.uppercase(Locale.ROOT)
    val isQuery = upper.startsWith("SELECT") ||
      upper.startsWith("PRAGMA") ||
      upper.startsWith("EXPLAIN") ||
      upper.startsWith("WITH")

    return runCatching {
      val db = if (isQuery) {
        SQLiteDatabase.openDatabase(readableFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
      } else {
        openWritableDatabase(readableFile)
      }
      db.use { openedDb ->
        if (isQuery) {
          val columns = mutableListOf<String>()
          val rows = mutableListOf<List<String>>()
          openedDb.rawQuery(trimmed, null).use { cursor ->
            columns.addAll(cursor.columnNames)
            var count = 0
            while (cursor.moveToNext() && count < 300) {
              val row = ArrayList<String>(columns.size)
              for (i in columns.indices) {
                row += formatCursorCell(cursor, i)
              }
              rows += row
              count++
            }
          }
          current.copy(
            columns = columns,
            rows = rows,
            rowIds = List(rows.size) { null },
            hasRowId = false,
            totalRows = rows.size,
            sqlQuery = sql,
            error = null,
            queryResultMessage = "Query returned ${rows.size} row(s)",
          )
        } else {
          openedDb.execSQL(trimmed)
          checkpointOpenDb(openedDb)
          val refreshed = inspectSqliteDatabase(
            entry = current.entry,
            readableFile = readableFile,
            tempCopyPath = current.tempCopyPath,
            requestedTable = current.selectedTable,
            hasPendingChanges = current.tempCopyPath != null || current.hasPendingChanges,
          )
          refreshed.copy(
            sqlQuery = sql,
            error = null,
            queryResultMessage = "SQL executed successfully",
          )
        }
      }
    }.getOrElse { err ->
      current.copy(
        sqlQuery = sql,
        error = err.message ?: "SQL execution failed",
        queryResultMessage = null,
      )
    }
  }

  fun updateSqliteCell(
    current: SqliteDatabaseInfo,
    readableFile: File,
    rowIndex: Int,
    columnIndex: Int,
    newValue: String?,
  ): SqliteDatabaseInfo {
    val table = current.selectedTable ?: return current.copy(error = "No active table")
    val colName = current.columns.getOrNull(columnIndex) ?: return current.copy(error = "Invalid column")
    val escapedTable = table.replace("\"", "\"\"")
    val escapedCol = colName.replace("\"", "\"\"")
    val rowId = current.rowIds.getOrNull(rowIndex)

    return runCatching {
      openWritableDatabase(readableFile).use { db ->
        if (current.hasRowId && rowId != null) {
          if (newValue == null) {
            db.execSQL("UPDATE \"$escapedTable\" SET \"$escapedCol\" = NULL WHERE rowid = ?", arrayOf<Any>(rowId))
          } else {
            db.execSQL("UPDATE \"$escapedTable\" SET \"$escapedCol\" = ? WHERE rowid = ?", arrayOf<Any>(newValue, rowId))
          }
        } else {
          val rowValues = current.rows.getOrNull(rowIndex) ?: error("Row missing")
          val whereParts = mutableListOf<String>()
          val args = mutableListOf<Any>()
          if (newValue != null) args += newValue
          current.columns.forEachIndexed { idx, col ->
            val escC = col.replace("\"", "\"\"")
            val v = rowValues.getOrNull(idx)
            if (v == null || v == "NULL") {
              whereParts += "\"$escC\" IS NULL"
            } else if (!v.startsWith("<BLOB ")) {
              whereParts += "\"$escC\" = ?"
              args += v
            }
          }
          val setClause = if (newValue == null) "\"$escapedCol\" = NULL" else "\"$escapedCol\" = ?"
          val whereClause = whereParts.joinToString(" AND ").ifEmpty { error("Cannot identify row without rowid") }
          db.execSQL("UPDATE \"$escapedTable\" SET $setClause WHERE $whereClause", args.toTypedArray())
        }
        checkpointOpenDb(db)
      }
      inspectSqliteDatabase(
        entry = current.entry,
        readableFile = readableFile,
        tempCopyPath = current.tempCopyPath,
        requestedTable = table,
        hasPendingChanges = current.tempCopyPath != null,
      ).copy(
        queryResultMessage = "Updated $colName",
        error = null,
      )
    }.getOrElse { err ->
      current.copy(error = err.message ?: "Failed to update cell")
    }
  }

  fun deleteSqliteRow(
    current: SqliteDatabaseInfo,
    readableFile: File,
    rowIndex: Int,
  ): SqliteDatabaseInfo {
    val table = current.selectedTable ?: return current.copy(error = "No active table")
    val escapedTable = table.replace("\"", "\"\"")
    val rowId = current.rowIds.getOrNull(rowIndex)

    return runCatching {
      openWritableDatabase(readableFile).use { db ->
        if (current.hasRowId && rowId != null) {
          db.execSQL("DELETE FROM \"$escapedTable\" WHERE rowid = ?", arrayOf<Any>(rowId))
        } else {
          val rowValues = current.rows.getOrNull(rowIndex) ?: error("Row missing")
          val whereParts = mutableListOf<String>()
          val args = mutableListOf<Any>()
          current.columns.forEachIndexed { idx, col ->
            val escC = col.replace("\"", "\"\"")
            val v = rowValues.getOrNull(idx)
            if (v == null || v == "NULL") {
              whereParts += "\"$escC\" IS NULL"
            } else if (!v.startsWith("<BLOB ")) {
              whereParts += "\"$escC\" = ?"
              args += v
            }
          }
          val whereClause = whereParts.joinToString(" AND ").ifEmpty { error("Cannot delete row without rowid") }
          db.execSQL("DELETE FROM \"$escapedTable\" WHERE $whereClause", args.toTypedArray())
        }
        checkpointOpenDb(db)
      }
      inspectSqliteDatabase(
        entry = current.entry,
        readableFile = readableFile,
        tempCopyPath = current.tempCopyPath,
        requestedTable = table,
        hasPendingChanges = current.tempCopyPath != null,
      ).copy(
        queryResultMessage = "Deleted row",
        error = null,
      )
    }.getOrElse { err ->
      current.copy(error = err.message ?: "Failed to delete row")
    }
  }

  private fun formatCursorCell(cursor: Cursor, colIndex: Int): String {
    return when (cursor.getType(colIndex)) {
      Cursor.FIELD_TYPE_NULL -> "NULL"
      Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(colIndex).toString()
      Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(colIndex).toString()
      Cursor.FIELD_TYPE_BLOB -> "<BLOB ${cursor.getBlob(colIndex).size} B>"
      else -> cursor.getString(colIndex).orEmpty()
    }
  }

  private fun openTarStream(file: File): InputStream {
    val lower = file.name.lowercase(Locale.ROOT)
    val buffered = BufferedInputStream(FileInputStream(file))
    return if (lower.endsWith(".gz") || lower.endsWith(".tgz")) {
      GZIPInputStream(buffered)
    } else {
      buffered
    }
  }

  private fun readFully(stream: InputStream, buffer: ByteArray): Int {
    var total = 0
    while (total < buffer.size) {
      val read = stream.read(buffer, total, buffer.size - total)
      if (read < 0) break
      total += read
    }
    return total
  }

  private fun skipExactly(stream: InputStream, bytes: Long) {
    var remaining = bytes
    val scratch = ByteArray(4096)
    while (remaining > 0L) {
      val toRead = minOf(remaining, scratch.size.toLong()).toInt()
      val read = stream.read(scratch, 0, toRead)
      if (read <= 0) break
      remaining -= read.toLong()
    }
  }

  private fun copyExactBytes(stream: InputStream, out: FileOutputStream, bytes: Long) {
    var remaining = bytes
    val buffer = ByteArray(8192)
    while (remaining > 0L) {
      val toRead = minOf(remaining, buffer.size.toLong()).toInt()
      val read = stream.read(buffer, 0, toRead)
      if (read <= 0) break
      out.write(buffer, 0, read)
      remaining -= read.toLong()
    }
  }

  private fun extractNullTerminatedString(buf: ByteArray, offset: Int, length: Int): String {
    var end = offset
    val max = minOf(buf.size, offset + length)
    while (end < max && buf[end] != 0.toByte()) end++
    return String(buf, offset, end - offset, Charsets.UTF_8).trim()
  }

  private fun parseTarOctal(buf: ByteArray, offset: Int, length: Int): Long {
    val raw = extractNullTerminatedString(buf, offset, length).trim()
    if (raw.isEmpty()) return 0L
    return raw.toLongOrNull(8) ?: 0L
  }
}
