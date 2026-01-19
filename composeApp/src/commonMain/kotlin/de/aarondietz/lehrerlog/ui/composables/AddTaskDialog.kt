package de.aarondietz.lehrerlog.ui.composables

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.action_add
import lehrerlog.composeapp.generated.resources.action_cancel
import lehrerlog.composeapp.generated.resources.add_task
import lehrerlog.composeapp.generated.resources.due_date
import lehrerlog.composeapp.generated.resources.due_date_hint
import lehrerlog.composeapp.generated.resources.task_description
import lehrerlog.composeapp.generated.resources.task_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?, dueAt: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueAt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_task)) },
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
                Text(stringResource(Res.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
