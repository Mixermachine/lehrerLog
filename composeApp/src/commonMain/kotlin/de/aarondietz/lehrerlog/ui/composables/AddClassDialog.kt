package de.aarondietz.lehrerlog.ui.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.action_add
import lehrerlog.composeapp.generated.resources.action_cancel
import lehrerlog.composeapp.generated.resources.add_class
import lehrerlog.composeapp.generated.resources.class_name
import lehrerlog.composeapp.generated.resources.class_name_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddClassDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var className by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_class)) },
        text = {
            OutlinedTextField(
                value = className,
                onValueChange = { className = it },
                label = { Text(stringResource(Res.string.class_name)) },
                placeholder = { Text(stringResource(Res.string.class_name_placeholder)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(className.trim()) },
                enabled = className.isNotBlank()
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
