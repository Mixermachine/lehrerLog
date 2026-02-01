package de.aarondietz.lehrerlog.ui.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

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

@Preview
@Composable
private fun AddClassDialogPreview() {
    LehrerLogTheme {
        AddClassDialog(onDismiss = {}, onConfirm = {})
    }
}
