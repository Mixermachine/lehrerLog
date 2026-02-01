package de.aarondietz.lehrerlog.ui.composables

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.aarondietz.lehrerlog.SharedTestFixtures

@Composable
fun EditTaskDialog(
    task: TaskDto,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?, dueAt: String) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description.orEmpty()) }
    var dueAt by remember { mutableStateOf(task.dueAt.substringBefore("T")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_task)) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.task_title)) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.task_description)) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = dueAt,
                    onValueChange = { dueAt = it },
                    label = { Text(stringResource(Res.string.due_date)) },
                    placeholder = { Text(stringResource(Res.string.due_date_hint)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), description.trim().ifBlank { null }, dueAt.trim()) },
                enabled = title.isNotBlank() && dueAt.isNotBlank()
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Preview
@Composable
private fun EditTaskDialogPreview() {
    LehrerLogTheme {
        EditTaskDialog(
            task = SharedTestFixtures.testTaskDto(SharedTestFixtures.testClassId),
            onDismiss = {},
            onConfirm = { _, _, _ -> }
        )
    }
}
