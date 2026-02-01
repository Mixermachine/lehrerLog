package de.aarondietz.lehrerlog.ui.screens.students

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.ui.composables.AddClassDialog
import de.aarondietz.lehrerlog.ui.composables.AddStudentDialog
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.layoutMetrics
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.*
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
    val parentInviteState by viewModel.parentInvite.collectAsState()
    val parentLinksState by viewModel.parentLinks.collectAsState()
    val expandedClasses = remember { mutableStateMapOf<String, Boolean>() }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var selectedClassForStudent by remember { mutableStateOf<SchoolClassDto?>(null) }
    var confirmDeleteStudent by remember { mutableStateOf<de.aarondietz.lehrerlog.data.StudentDto?>(null) }
    var selectedStudentForInvite by remember { mutableStateOf<de.aarondietz.lehrerlog.data.StudentDto?>(null) }
    var selectedStudentForLinks by remember { mutableStateOf<de.aarondietz.lehrerlog.data.StudentDto?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showAddClassDialog = true
                }
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
                    contentPadding = PaddingValues(MaterialTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                ) {
                    if (classes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.xl),
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
                                    confirmDeleteStudent = student
                                },
                                onInviteParent = { student ->
                                    selectedStudentForInvite = student
                                    viewModel.createParentInvite(student.id)
                                },
                                onViewParentLinks = { student ->
                                    selectedStudentForLinks = student
                                    viewModel.loadParentLinks(student.id)
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
            snackbarHostState.showSnackbar(errorMessage)
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
                viewModel.createStudent(schoolClass.id, student.firstName, student.lastName)
                selectedClassForStudent = null
            }
        )
    }

    confirmDeleteStudent?.let { student ->
        AlertDialog(
            onDismissRequest = { confirmDeleteStudent = null },
            title = { Text(stringResource(Res.string.delete_student)) },
            text = {
                Text(
                    stringResource(
                        Res.string.delete_student_confirm,
                        "${student.firstName} ${student.lastName}"
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStudent(student.id)
                        confirmDeleteStudent = null
                    }
                ) {
                    Text(stringResource(Res.string.delete_student))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteStudent = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    selectedStudentForInvite?.let { student ->
        ParentInviteDialog(
            state = parentInviteState,
            onDismiss = {
                selectedStudentForInvite = null
                viewModel.clearParentInvite()
            }
        )
    }

    selectedStudentForLinks?.let {
        ParentLinksDialog(
            state = parentLinksState,
            onDismiss = {
                selectedStudentForLinks = null
                viewModel.clearParentLinks()
            },
            onRevoke = { linkId ->
                viewModel.revokeParentLink(linkId)
            }
        )
    }
}

@Composable
internal fun ClassCard(
    schoolClass: SchoolClassDto,
    students: List<de.aarondietz.lehrerlog.data.StudentDto>,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onAddStudent: () -> Unit,
    onDeleteClass: () -> Unit,
    onDeleteStudent: (de.aarondietz.lehrerlog.data.StudentDto) -> Unit,
    onInviteParent: (de.aarondietz.lehrerlog.data.StudentDto) -> Unit,
    onViewParentLinks: (de.aarondietz.lehrerlog.data.StudentDto) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.md)
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
                IconButton(onClick = onExpandClick) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) {
                            stringResource(Res.string.collapse)
                        } else {
                            stringResource(Res.string.expand)
                        }
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.spacing.sm))

                students.forEach { student ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MaterialTheme.spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${student.firstName} ${student.lastName}")
                        Row {
                            IconButton(onClick = { onInviteParent(student) }) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = stringResource(Res.string.invite_parent)
                                )
                            }
                            IconButton(onClick = { onViewParentLinks(student) }) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = stringResource(Res.string.parent_links_title)
                                )
                            }
                            IconButton(onClick = { onDeleteStudent(student) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(Res.string.delete_student)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < MaterialTheme.layoutMetrics.compactWidth) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onAddStudent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(Res.string.add_student))
                            }

                            Button(
                                onClick = onDeleteClass,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(Res.string.delete_class))
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onAddStudent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(Res.string.add_student))
                            }

                            Button(
                                onClick = onDeleteClass,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(Res.string.delete_class))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ParentLinksDialog(
    state: ParentLinksUiState,
    onDismiss: () -> Unit,
    onRevoke: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.parent_links_title)) },
        text = {
            when {
                state.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.md))
                        Text(stringResource(Res.string.parent_links_title))
                    }
                }

                state.error != null -> {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }

                state.links.isEmpty() -> {
                    Text(stringResource(Res.string.parent_links_empty))
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        state.links.forEach { link ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(link.parentUserId, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { onRevoke(link.id) }) {
                                    Text(stringResource(Res.string.parent_link_revoke))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Composable
internal fun ParentInviteDialog(
    state: ParentInviteUiState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.invite_parent_title)) },
        text = {
            when {
                state.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.md))
                        Text(stringResource(Res.string.invite_parent_message))
                    }
                }

                state.invite != null -> {
                    Column {
                        Text(stringResource(Res.string.invite_parent_message))
                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
                        Text(state.invite.code, style = MaterialTheme.typography.titleMedium)
                    }
                }

                state.error != null -> {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Preview
@Composable
private fun StudentsScreenPreview() {
    LehrerLogTheme {
        StudentsScreen()
    }
}

@Preview
@Composable
private fun ClassCardPreview() {
    LehrerLogTheme {
        ClassCard(
            schoolClass = SharedTestFixtures.testSchoolClassDto(),
            students = listOf(SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)),
            isExpanded = true,
            onExpandClick = {},
            onAddStudent = {},
            onDeleteClass = {},
            onDeleteStudent = {},
            onInviteParent = {},
            onViewParentLinks = {}
        )
    }
}

@Preview
@Composable
private fun ParentLinksDialogPreview() {
    LehrerLogTheme {
        ParentLinksDialog(
            state = ParentLinksUiState(
                links = listOf(SharedTestFixtures.testParentLinkDto())
            ),
            onDismiss = {},
            onRevoke = {}
        )
    }
}

@Preview
@Composable
private fun ParentInviteDialogPreview() {
    LehrerLogTheme {
        ParentInviteDialog(
            state = ParentInviteUiState(
                invite = SharedTestFixtures.testParentInviteCreateResponse()
            ),
            onDismiss = {}
        )
    }
}
