package de.aarondietz.lehrerlog.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.data.Student
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.delete_student
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun StudentListItem(
    student: Student,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${student.firstName} ${student.lastName}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(Res.string.delete_student),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Preview
@Composable
private fun StudentListItemPreview() {
    LehrerLogTheme {
        StudentListItem(student = SharedTestFixtures.testStudent(), onDelete = {})
    }
}
