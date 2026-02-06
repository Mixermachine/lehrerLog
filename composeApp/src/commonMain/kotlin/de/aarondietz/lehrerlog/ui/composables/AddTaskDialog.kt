package de.aarondietz.lehrerlog.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import de.aarondietz.lehrerlog.ui.test.UiTestTags
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import de.aarondietz.lehrerlog.ui.util.PickedFile
import de.aarondietz.lehrerlog.ui.util.rememberFilePickerLauncher
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?, dueAt: String, file: PickedFile?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<PickedFile?>(null) }

    val filePicker = rememberFilePickerLauncher(
        mimeTypes = listOf("application/pdf"),
        onFilePicked = { file -> selectedFile = file },
        onCanceled = {}
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_task)) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.task_title)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.addTaskDialogTitleField)
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.task_description)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
                OutlinedTextField(
                    value = selectedDate?.toString() ?: "",
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.due_date)) },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = stringResource(Res.string.due_date)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))

                // File upload section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(Res.string.task_upload_assignment),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.xs))
                    OutlinedButton(
                        onClick = { filePicker() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(MaterialTheme.spacing.md)
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.xs))
                        Text(stringResource(Res.string.submission_upload_file))
                    }
                }

                // Show selected file
                selectedFile?.let { file ->
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.xs))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(MaterialTheme.spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { selectedFile = null },
                                modifier = Modifier.size(MaterialTheme.spacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(Res.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedDate?.let { date ->
                        onConfirm(
                            title.trim(),
                            description.trim().ifBlank { null },
                            date.toString(),
                            selectedFile
                        )
                    }
                },
                enabled = title.isNotBlank() && selectedDate != null
            ) {
                Text(stringResource(Res.string.action_add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(UiTestTags.addTaskDialogCancelButton)
            ) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate?.atStartOfDayIn(TimeZone.currentSystemDefault())?.toEpochMilliseconds()
                ?: Clock.System.now().toEpochMilliseconds()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                .date
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Preview
@Composable
private fun AddTaskDialogPreview() {
    LehrerLogTheme {
        AddTaskDialog(onDismiss = {}, onConfirm = { _, _, _, _ -> })
    }
}
