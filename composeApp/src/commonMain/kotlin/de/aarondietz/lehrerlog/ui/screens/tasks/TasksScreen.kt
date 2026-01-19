package de.aarondietz.lehrerlog.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.ui.composables.AddTaskDialog
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TasksScreen(
    viewModel: TasksViewModel = koinViewModel()
) {
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var classMenuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val classes by viewModel.classes.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val selectedClassId by viewModel.selectedClassId.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(classes) {
        if (selectedClassId == null && classes.isNotEmpty()) {
            viewModel.selectClass(classes.first().id)
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val canAddTask = selectedClassId != null
            FloatingActionButton(
                onClick = { if (canAddTask) showAddTaskDialog = true },
                containerColor = if (canAddTask) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (canAddTask) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_task))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = classes.firstOrNull { it.id == selectedClassId }?.name ?: "",
                    onValueChange = { },
                    label = { Text(stringResource(Res.string.select_class)) },
                    trailingIcon = {
                        IconButton(onClick = { classMenuExpanded = !classMenuExpanded }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownMenu(
                    expanded = classMenuExpanded,
                    onDismissRequest = { classMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    classes.forEach { schoolClass ->
                        DropdownMenuItem(
                            text = { Text(schoolClass.name) },
                            onClick = {
                                classMenuExpanded = false
                                viewModel.selectClass(schoolClass.id)
                            }
                        )
                    }
                }
            }

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.no_tasks),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(task = task, summary = summaries[task.id])
                    }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        val classId = selectedClassId
        if (classId != null) {
            AddTaskDialog(
                onDismiss = { showAddTaskDialog = false },
                onConfirm = { title, description, dueAt ->
                    viewModel.createTask(classId, title, description, dueAt)
                    showAddTaskDialog = false
                }
            )
        } else {
            showAddTaskDialog = false
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskDto,
    summary: de.aarondietz.lehrerlog.data.TaskSubmissionSummaryDto?
) {
    val dueDate = task.dueAt.substringBefore("T")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = task.title, style = MaterialTheme.typography.titleMedium)
            task.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(Res.string.due_label, dueDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            summary?.let {
                Text(
                    text = stringResource(
                        Res.string.submissions_status,
                        it.submittedStudents,
                        it.totalStudents
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
