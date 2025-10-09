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

@Composable
fun AddClassDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var className by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Klasse hinzufügen") },
        text = {
            OutlinedTextField(
                value = className,
                onValueChange = { className = it },
                label = { Text("Klassenname") },
                placeholder = { Text("z.B. 5a, 10b") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(className.trim()) },
                enabled = className.isNotBlank()
            ) {
                Text("Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
