package de.aarondietz.lehrerlog.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.data.Student
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.action_add
import lehrerlog.composeapp.generated.resources.action_cancel
import lehrerlog.composeapp.generated.resources.add_student
import lehrerlog.composeapp.generated.resources.first_name
import lehrerlog.composeapp.generated.resources.last_name
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddStudentDialog(
    onDismiss: () -> Unit,
    onConfirm: (Student) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_student)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text(stringResource(Res.string.first_name)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text(stringResource(Res.string.last_name)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(Student(
                        firstName = firstName.trim(),
                        lastName = lastName.trim()
                    ))
                },
                enabled = firstName.isNotBlank() && lastName.isNotBlank()
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
