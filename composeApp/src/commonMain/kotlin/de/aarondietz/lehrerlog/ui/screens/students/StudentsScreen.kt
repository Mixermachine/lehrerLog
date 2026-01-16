package de.aarondietz.lehrerlog.ui.screens.students

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.ui.composables.AddClassDialog
import de.aarondietz.lehrerlog.ui.composables.AddStudentDialog
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.add_class
import lehrerlog.composeapp.generated.resources.no_classes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StudentsScreen(
    viewModel: StudentsViewModel = koinViewModel()
) {
    val classes by viewModel.classes.collectAsState()
    val students by viewModel.students.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val expandedClasses = remember { mutableStateMapOf<String, Boolean>() }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var selectedClassForStudent by remember { mutableStateOf<SchoolClassDto?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddClassDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_class))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && classes.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (classes.isEmpty()) {
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
                            items = classes,
                            key = { it.id }
                        ) { schoolClass ->
                            // Get students for this class
                            val classStudents = students.filter { student ->
                                student.classIds.contains(schoolClass.id)
                            }

                            ClassCard(
                                schoolClass = schoolClass,
                                students = classStudents,
                                isExpanded = expandedClasses[schoolClass.id] ?: false,
                                onExpandClick = {
                                    expandedClasses[schoolClass.id] = !(expandedClasses[schoolClass.id] ?: false)
                                },
                                onAddStudent = {
                                    selectedClassForStudent = schoolClass
                                },
                                onDeleteClass = {
                                    viewModel.deleteClass(schoolClass.id)
                                },
                                onDeleteStudent = { student ->
                                    viewModel.deleteStudent(student.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Error snackbar
    error?.let { errorMessage ->
        LaunchedEffect(errorMessage) {
            // TODO: Show snackbar with error
            viewModel.clearError()
        }
    }

    if (showAddClassDialog) {
        AddClassDialog(
            onDismiss = { showAddClassDialog = false },
            onConfirm = { className ->
                viewModel.createClass(className)
                showAddClassDialog = false
            }
        )
    }

    selectedClassForStudent?.let { schoolClass ->
        AddStudentDialog(
            onDismiss = { selectedClassForStudent = null },
            onConfirm = { student ->
                viewModel.createStudent(student.firstName, student.lastName)
                selectedClassForStudent = null
            }
        )
    }
}

@Composable
private fun ClassCard(
    schoolClass: SchoolClassDto,
    students: List<de.aarondietz.lehrerlog.data.StudentDto>,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onAddStudent: () -> Unit,
    onDeleteClass: () -> Unit,
    onDeleteStudent: (de.aarondietz.lehrerlog.data.StudentDto) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = schoolClass.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    IconButton(onClick = onExpandClick) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.Add else Icons.Default.Add,
                            contentDescription = "Expand"
                        )
                    }
                }
            }

            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                students.forEach { student ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${student.firstName} ${student.lastName}")
                        IconButton(onClick = { onDeleteStudent(student) }) {
                            Icon(Icons.Default.Add, "Delete") // TODO: Use delete icon
                        }
                    }
                }

                Button(
                    onClick = onAddStudent,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Add Student")
                }

                Button(
                    onClick = onDeleteClass,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Delete Class")
                }
            }
        }
    }
}
