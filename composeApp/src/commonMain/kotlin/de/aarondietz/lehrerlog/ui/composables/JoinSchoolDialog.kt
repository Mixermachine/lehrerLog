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
import lehrerlog.composeapp.generated.resources.action_cancel
import lehrerlog.composeapp.generated.resources.join_school_action
import lehrerlog.composeapp.generated.resources.join_school_title
import lehrerlog.composeapp.generated.resources.school_code
import org.jetbrains.compose.resources.stringResource

@Composable
fun JoinSchoolDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var schoolCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.join_school_title)) },
        text = {
            OutlinedTextField(
                value = schoolCode,
                onValueChange = { schoolCode = it },
                label = { Text(stringResource(Res.string.school_code)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(schoolCode.trim()) },
                enabled = schoolCode.isNotBlank()
            ) {
                Text(stringResource(Res.string.join_school_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
