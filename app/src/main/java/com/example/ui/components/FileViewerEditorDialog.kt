package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.files.TextEditorState

@Composable
fun FileViewerEditorDialog(
  state: TextEditorState,
  onSave: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var textValue by remember(state.entry.path) {
    mutableStateOf(TextFieldValue(text = state.content))
  }
  var showFindReplace by remember { mutableStateOf(false) }
  var findQuery by remember { mutableStateOf("") }
  var replaceText by remember { mutableStateOf("") }
  var matchIndex by remember { mutableIntStateOf(0) }

  val content = textValue.text
  val lines = remember(content) {
    if (content.isEmpty()) listOf("") else content.split('\n')
  }
  val lineNumberText = remember(lines.size) {
    (1..lines.size).joinToString("\n")
  }

  val matchIndices = remember(content, findQuery) {
    if (findQuery.isEmpty()) {
      emptyList()
    } else {
      val list = mutableListOf<Int>()
      var idx = content.indexOf(findQuery, ignoreCase = true)
      while (idx >= 0) {
        list += idx
        idx = content.indexOf(findQuery, startIndex = idx + maxOf(1, findQuery.length), ignoreCase = true)
      }
      list
    }
  }

  fun jumpToMatch(index: Int) {
    if (matchIndices.isEmpty()) return
    val wrapped = ((index % matchIndices.size) + matchIndices.size) % matchIndices.size
    matchIndex = wrapped
    val start = matchIndices[wrapped]
    textValue = textValue.copy(selection = TextRange(start, start + findQuery.length))
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.background,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp)
        .testTag("file_editor_dialog"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Header row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = state.entry.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${lines.size} lines · ${content.length} chars · ${state.entry.path}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            IconButton(
              onClick = { showFindReplace = !showFindReplace },
              modifier = Modifier.testTag("file_editor_find_toggle"),
            ) {
              Icon(
                imageVector = if (state.isReadOnly) Icons.Rounded.Search else Icons.Rounded.FindReplace,
                contentDescription = "Find and replace",
                tint = if (showFindReplace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            TextButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("file_editor_close"),
            ) {
              Text("Close")
            }
            if (!state.isReadOnly) {
              Button(
                enabled = !state.isSaving,
                onClick = { onSave(content) },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.testTag("file_editor_save"),
              ) {
                Text(if (state.isSaving) "Saving…" else "Save")
              }
            }
          }
        }

        // Status chips (Binary XML decoded / Auto .bak backup)
        if (state.isBinaryXml || state.isSystemFile || state.isReadOnly) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (state.isBinaryXml) {
              AssistChip(
                onClick = {},
                label = { Text("Decoded Binary XML (Read-Only)") },
              )
            } else if (state.isReadOnly) {
              AssistChip(
                onClick = {},
                label = { Text("Archive Preview (Read-Only)") },
              )
            }
            if (state.isSystemFile && !state.isReadOnly) {
              AssistChip(
                onClick = {},
                label = { Text("Auto .bak backup & atomic save") },
              )
            }
          }
        }

        // Find & Replace Bar
        AnimatedVisibility(visible = showFindReplace) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("file_editor_find_bar"),
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                OutlinedTextField(
                  value = findQuery,
                  onValueChange = {
                    findQuery = it
                    matchIndex = 0
                  },
                  label = { Text("Find") },
                  singleLine = true,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("file_editor_find_input"),
                )
                Text(
                  text = if (matchIndices.isEmpty()) "0/0" else "${(matchIndex % matchIndices.size) + 1}/${matchIndices.size}",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                  enabled = matchIndices.isNotEmpty(),
                  onClick = { jumpToMatch(matchIndex - 1) },
                ) {
                  Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(
                  enabled = matchIndices.isNotEmpty(),
                  onClick = { jumpToMatch(matchIndex + 1) },
                ) {
                  Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                }
              }

              if (!state.isReadOnly) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Replace with") },
                    singleLine = true,
                    modifier = Modifier
                      .weight(1f)
                      .testTag("file_editor_replace_input"),
                  )
                  OutlinedButton(
                    enabled = findQuery.isNotEmpty() && matchIndices.isNotEmpty(),
                    onClick = {
                      val pos = matchIndices.getOrNull(matchIndex % matchIndices.size) ?: return@OutlinedButton
                      val updated = content.replaceRange(pos, pos + findQuery.length, replaceText)
                      textValue = TextFieldValue(
                        text = updated,
                        selection = TextRange(pos + replaceText.length),
                      )
                    },
                    modifier = Modifier.testTag("file_editor_replace_one"),
                  ) {
                    Text("Replace")
                  }
                  Button(
                    enabled = findQuery.isNotEmpty() && matchIndices.isNotEmpty(),
                    onClick = {
                      val updated = content.replace(findQuery, replaceText, ignoreCase = true)
                      textValue = TextFieldValue(text = updated)
                    },
                    modifier = Modifier.testTag("file_editor_replace_all"),
                  ) {
                    Text("All")
                  }
                }
              }
            }
          }
        }

        // Line-numbered monospace editor / viewer
        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()
        val lineDigits = lines.size.toString().length.coerceAtLeast(2)
        val gutterWidth = (lineDigits * 9 + 16).dp

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(vScroll),
          ) {
            // Line number gutter
            Box(
              modifier = Modifier
                .width(gutterWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(vertical = 12.dp, horizontal = 6.dp),
            ) {
              Text(
                text = lineNumberText,
                style = TextStyle(
                  fontFamily = FontFamily.Monospace,
                  fontSize = 13.sp,
                  lineHeight = 20.sp,
                  textAlign = TextAlign.End,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("file_editor_line_numbers"),
              )
            }

            Spacer(modifier = Modifier.width(8.dp))

            BasicTextField(
              value = textValue,
              onValueChange = { if (!state.isReadOnly) textValue = it },
              readOnly = state.isReadOnly,
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
              ),
              modifier = Modifier
                .weight(1f)
                .horizontalScroll(hScroll)
                .padding(top = 12.dp, bottom = 12.dp, end = 12.dp)
                .testTag("file_editor_text"),
            )
          }
        }
      }
    }
  }
}
