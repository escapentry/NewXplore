package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.files.SqliteDatabaseInfo

@Composable
fun SqliteViewerDialog(
  info: SqliteDatabaseInfo,
  onSelectTable: (String) -> Unit,
  onRunSql: (String) -> Unit = {},
  onUpdateCell: (rowIndex: Int, colIndex: Int, newValue: String?) -> Unit = { _, _, _ -> },
  onDeleteRow: (rowIndex: Int) -> Unit = {},
  onCommitToDevice: () -> Unit = {},
  onDismiss: () -> Unit,
) {
  var showSqlConsole by remember { mutableStateOf(false) }
  var sqlInput by remember(info.selectedTable) { mutableStateOf(info.sqlQuery) }
  LaunchedEffect(info.sqlQuery) {
    if (info.sqlQuery.isNotBlank()) sqlInput = info.sqlQuery
  }

  var editingCell by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.background,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp)
        .testTag("sqlite_viewer_dialog"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Surface(
            shape = AppleSquircleShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.TableChart,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(8.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = info.entry.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${info.tables.size} table(s) · ${info.totalRows} row(s)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = { showSqlConsole = !showSqlConsole },
            modifier = Modifier.testTag("sqlite_sql_toggle"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Code,
              contentDescription = "SQL console",
              tint = if (showSqlConsole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(onClick = onDismiss) {
            Text("Close")
          }
        }

        if (!info.error.isNullOrBlank()) {
          Text(
            text = info.error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        } else if (!info.queryResultMessage.isNullOrBlank()) {
          Text(
            text = info.queryResultMessage,
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.labelMedium,
          )
        }

        if (info.tables.isNotEmpty()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .testTag("sqlite_table_chips"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            info.tables.forEach { table ->
              FilterChip(
                selected = table == info.selectedTable,
                onClick = { onSelectTable(table) },
                label = { Text(table, fontFamily = FontFamily.Monospace) },
              )
            }
          }
        }

        AnimatedVisibility(visible = showSqlConsole) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("sqlite_sql_console"),
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedTextField(
                  value = sqlInput,
                  onValueChange = { sqlInput = it },
                  label = { Text("SQL Query / Statement") },
                  maxLines = 3,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("sqlite_sql_input"),
                )
                IconButton(
                  onClick = { onRunSql(sqlInput) },
                  modifier = Modifier.testTag("sqlite_sql_run"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Run SQL",
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
              info.selectedTable?.let { tbl ->
                val esc = tbl.replace("\"", "\"\"")
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  listOf(
                    "SELECT *" to "SELECT * FROM \"$esc\" LIMIT 200;",
                    "Schema" to "PRAGMA table_info(\"$esc\");",
                    "Count" to "SELECT COUNT(*) AS count FROM \"$esc\";",
                  ).forEach { (label, query) ->
                    AssistChip(
                      onClick = {
                        sqlInput = query
                        onRunSql(query)
                      },
                      label = { Text(label) },
                    )
                  }
                }
              }
            }
          }
        }

        val hScroll = rememberScrollState()
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        ) {
          if (info.columns.isEmpty()) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "No rows or tables to display",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            Column(modifier = Modifier.fillMaxSize()) {
              // Column header row
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(hScroll)
                  .padding(horizontal = 10.dp, vertical = 10.dp),
              ) {
                info.columns.forEach { col ->
                  Text(
                    text = col,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                      .width(140.dp)
                      .padding(end = 8.dp),
                  )
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outline)

              LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(info.rows) { rowIdx, row ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .horizontalScroll(hScroll)
                      .padding(horizontal = 10.dp, vertical = 4.dp),
                  ) {
                    row.forEachIndexed { colIdx, cell ->
                      Text(
                        text = cell,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                          .width(140.dp)
                          .clickable(enabled = info.selectedTable != null && !cell.startsWith("<BLOB ")) {
                            editingCell = Triple(rowIdx, colIdx, cell)
                          }
                          .padding(end = 8.dp, top = 6.dp, bottom = 6.dp),
                      )
                    }
                  }
                  if (rowIdx < info.rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                  }
                }
              }
            }
          }
        }

        if (info.hasPendingChanges && info.tempCopyPath != null) {
          Button(
            enabled = !info.isSavingBack,
            onClick = onCommitToDevice,
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("sqlite_commit_button"),
          ) {
            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (info.isSavingBack) "Writing back via root…" else "Commit Edits to Device (Root)")
          }
        } else {
          Text(
            text = "Tap any cell to edit value or delete row · Toggle </> for SQL queries",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  editingCell?.let { (rowIdx, colIdx, initialVal) ->
    val colName = info.columns.getOrNull(colIdx).orEmpty()
    var cellValue by remember(rowIdx, colIdx) { mutableStateOf(if (initialVal == "NULL") "" else initialVal) }
    var setNull by remember(rowIdx, colIdx) { mutableStateOf(initialVal == "NULL") }

    Dialog(onDismissRequest = { editingCell = null }) {
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("sqlite_cell_edit_dialog"),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Edit \"$colName\" (Row ${rowIdx + 1})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          OutlinedTextField(
            value = cellValue,
            onValueChange = {
              cellValue = it
              if (it.isNotEmpty()) setNull = false
            },
            enabled = !setNull,
            label = { Text("Value") },
            maxLines = 4,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("sqlite_cell_value_input"),
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { setNull = !setNull },
          ) {
            Checkbox(checked = setNull, onCheckedChange = { setNull = it })
            Text("Set value to NULL", style = MaterialTheme.typography.bodyMedium)
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            OutlinedButton(
              onClick = {
                editingCell = null
                onDeleteRow(rowIdx)
              },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.testTag("sqlite_delete_row_button"),
            ) {
              Text("Delete Row")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              TextButton(onClick = { editingCell = null }) {
                Text("Cancel")
              }
              Button(
                onClick = {
                  editingCell = null
                  onUpdateCell(rowIdx, colIdx, if (setNull) null else cellValue)
                },
                modifier = Modifier.testTag("sqlite_save_cell_button"),
              ) {
                Text("Update")
              }
            }
          }
        }
      }
    }
  }
}
