package de.aarondietz.lehrerlog.ui.screens.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.data.SchoolClass
import de.aarondietz.lehrerlog.data.Student
import de.aarondietz.lehrerlog.ui.composables.AddClassDialog
import de.aarondietz.lehrerlog.ui.composables.AddStudentDialog
import de.aarondietz.lehrerlog.ui.composables.ExpandableClassCard
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.add_class
import lehrerlog.composeapp.generated.resources.no_classes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StudentsScreen(
    viewModel: StudentsViewModel = koinViewModel()
) {
    val expandedClasses = remember { mutableStateMapOf<String, Boolean>() }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var selectedClassForStudent by remember { mutableStateOf<SchoolClass?>(null) }

    LaunchedEffect(true) {
        repeat(10) {
            val myClass = SchoolClass(name = "Test")
            repeat(50) {
                myClass.students.add(Student(
                    firstName = "SLe",
                    lastName = "sda",
                ))
            }

            viewModel.classes.add(myClass)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddClassDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_class))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (viewModel.classes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(Res.string.no_classes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    items = viewModel.classes,
                    key = { it.id }
                ) { schoolClass ->
                    ExpandableClassCard(
                        schoolClass = schoolClass,
                        isExpanded = expandedClasses[schoolClass.id] ?: false,
                        onExpandClick = {
                            expandedClasses[schoolClass.id] = !(expandedClasses[schoolClass.id] ?: false)
                        },
                        onAddStudent = {
                            selectedClassForStudent = schoolClass
                        },
                        onDeleteClass = {
                            viewModel.classes.remove(schoolClass)
                            expandedClasses.remove(schoolClass.id)
                        },
                        onDeleteStudent = { student ->
                            schoolClass.students.remove(student)
                        }
                    )
                }
            }
        }
    }

    if (showAddClassDialog) {
        AddClassDialog(
            onDismiss = { showAddClassDialog = false },
            onConfirm = { className ->
                viewModel.classes.add(SchoolClass(name = className))
                showAddClassDialog = false
            }
        )
    }

    selectedClassForStudent?.let { schoolClass ->
        AddStudentDialog(
            onDismiss = { selectedClassForStudent = null },
            onConfirm = { student ->
                schoolClass.students.add(student)
                selectedClassForStudent = null
            }
        )
    }
}

@Preview
@Composable
fun StudentsScreenPreview() {
    StudentsScreen(StudentsViewModel())
}