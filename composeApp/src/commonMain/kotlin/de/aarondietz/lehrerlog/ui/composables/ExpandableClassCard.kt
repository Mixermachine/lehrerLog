package de.aarondietz.lehrerlog.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.data.SchoolClass
import de.aarondietz.lehrerlog.data.Student
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExpandableClassCard(
    schoolClass: SchoolClass,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onAddStudent: () -> Unit,
    onDeleteClass: () -> Unit,
    onDeleteStudent: (Student) -> Unit
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Class Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandClick() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(if (isExpanded) Res.string.collapse else Res.string.expand),
                        modifier = Modifier.rotate(rotationState)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = schoolClass.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(Res.string.students_count, schoolClass.students.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDeleteClass) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.delete_class)
                    )
                }
            }

            // Expanded Content - Students
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(8.dp))

                    if (schoolClass.students.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.no_students_in_class),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        schoolClass.students.forEach { student ->
                            StudentListItem(
                                student = student,
                                onDelete = { onDeleteStudent(student) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onAddStudent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.add_student))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ExpandableClassCardPreview() {
    LehrerLogTheme {
        ExpandableClassCard(
            schoolClass = SharedTestFixtures.testSchoolClass(),
            isExpanded = true,
            onExpandClick = {},
            onAddStudent = {},
            onDeleteClass = {},
            onDeleteStudent = {}
        )
    }
}
