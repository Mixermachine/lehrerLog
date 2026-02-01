package de.aarondietz.lehrerlog.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.data.LateStatus
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import de.aarondietz.lehrerlog.data.TaskSubmissionType
import de.aarondietz.lehrerlog.ui.composables.EditTaskDialog
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import de.aarondietz.lehrerlog.ui.util.PickedFile
import de.aarondietz.lehrerlog.ui.util.rememberFilePickerLauncher
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TaskDetailDialog(
    state: TaskDetailState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onEditTask: (String, String, String?, String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onMarkInPerson: (String) -> Unit,
    onUpdateSubmission: (String, Double?, String?) -> Unit,
    onUploadAssignmentFile: (PickedFile) -> Unit,
    onUploadSubmissionFile: (String, String?, PickedFile) -> Unit
) {
    val task = state.task ?: return
    var isEditingTask by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var editingSubmission by remember { mutableStateOf<TaskSubmissionDto?>(null) }
    var pendingSubmissionUpload by remember { mutableStateOf<SubmissionUploadTarget?>(null) }

    val assignmentPicker = rememberFilePickerLauncher(
        mimeTypes = listOf("application/pdf"),
        onFilePicked = onUploadAssignmentFile,
        onCanceled = {}
    )
    val submissionPicker = rememberFilePickerLauncher(
        mimeTypes = listOf("*/*"),
        onFilePicked = { file ->
            val target = pendingSubmissionUpload
            if (target != null) {
                onUploadSubmissionFile(target.studentId, target.submissionId, file)
            }
            pendingSubmissionUpload = null
        },
        onCanceled = { pendingSubmissionUpload = null }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.task_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium)
                        task.description?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = task.dueAt.substringBefore("T"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                        TextButton(onClick = { isEditingTask = true }) {
                            Text(stringResource(Res.string.task_edit))
                        }
                        TextButton(onClick = { showDeleteConfirmation = true }) {
                            Text(
                                text = stringResource(Res.string.task_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (state.isLoading || state.isUploading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.sm))
                        Text(stringResource(Res.string.task_loading))
                    }
                } else {
                    TextButton(onClick = onRefresh) {
                        Text(stringResource(Res.string.task_refresh))
                    }
                }

                if (state.error != null) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.task_upload_assignment),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { assignmentPicker() }) {
                        Text(stringResource(Res.string.submission_upload_file))
                    }
                }

                if (state.students.isEmpty()) {
                    Text(stringResource(Res.string.task_students_empty))
                } else {
                    state.students.forEach { student ->
                        val submission = state.submissions.firstOrNull { it.studentId == student.id }
                        SubmissionRow(
                            student = student,
                            submission = submission,
                            onMarkInPerson = onMarkInPerson,
                            onEdit = { editingSubmission = it },
                            onUploadFile = { studentId, submissionId ->
                                pendingSubmissionUpload = SubmissionUploadTarget(studentId, submissionId)
                                submissionPicker()
                            }
                        )
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

    editingSubmission?.let { submission ->
        SubmissionEditDialog(
            submission = submission,
            onDismiss = { editingSubmission = null },
            onSave = { grade, note ->
                onUpdateSubmission(submission.id, grade, note)
                editingSubmission = null
            }
        )
    }

    if (isEditingTask) {
        EditTaskDialog(
            task = task,
            onDismiss = { isEditingTask = false },
            onConfirm = { title, description, dueAt ->
                onEditTask(task.id, title, description, dueAt)
                isEditingTask = false
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(Res.string.task_delete)) },
            text = { Text(stringResource(Res.string.task_delete_confirm, task.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTask(task.id)
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(
                        text = stringResource(Res.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SubmissionRow(
    student: StudentDto,
    submission: TaskSubmissionDto?,
    onMarkInPerson: (String) -> Unit,
    onEdit: (TaskSubmissionDto) -> Unit,
    onUploadFile: (String, String?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
    ) {
        Text(
            text = "${student.firstName} ${student.lastName}",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (submission == null) {
                Text(
                    text = stringResource(Res.string.submission_status_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    TextButton(onClick = { onUploadFile(student.id, null) }) {
                        Text(stringResource(Res.string.submission_upload_file))
                    }
                    TextButton(onClick = { onMarkInPerson(student.id) }) {
                        Text(stringResource(Res.string.submission_mark_in_person))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                    Text(
                        text = stringResource(Res.string.submission_status_submitted),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = submissionTypeLabel(submission.submissionType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = lateStatusLabel(submission.lateStatus),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    if (submission.submissionType == TaskSubmissionType.FILE) {
                        TextButton(onClick = { onUploadFile(student.id, submission.id) }) {
                            Text(stringResource(Res.string.submission_upload_file))
                        }
                    }
                    TextButton(onClick = { onEdit(submission) }) {
                        Text(stringResource(Res.string.submission_edit))
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun SubmissionEditDialog(
    submission: TaskSubmissionDto,
    onDismiss: () -> Unit,
    onSave: (Double?, String?) -> Unit
) {
    var gradeText by remember { mutableStateOf(submission.grade?.toString() ?: "") }
    var noteText by remember { mutableStateOf(submission.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.submission_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                OutlinedTextField(
                    value = gradeText,
                    onValueChange = { gradeText = it },
                    label = { Text(stringResource(Res.string.submission_grade)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(Res.string.submission_note)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val grade = gradeText.trim().ifBlank { null }?.toDoubleOrNull()
                val note = noteText.trim().ifBlank { null }
                onSave(grade, note)
            }) {
                Text(stringResource(Res.string.submission_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Composable
private fun submissionTypeLabel(type: TaskSubmissionType): String {
    return when (type) {
        TaskSubmissionType.FILE -> stringResource(Res.string.submission_type_file)
        TaskSubmissionType.IN_PERSON -> stringResource(Res.string.submission_type_in_person)
    }
}

@Composable
private fun lateStatusLabel(status: LateStatus): String {
    return when (status) {
        LateStatus.ON_TIME -> stringResource(Res.string.late_status_on_time)
        LateStatus.LATE_UNDECIDED -> stringResource(Res.string.late_status_undecided)
        LateStatus.LATE_FORGIVEN -> stringResource(Res.string.late_status_forgiven)
        LateStatus.LATE_PUNISH -> stringResource(Res.string.late_status_punish)
    }
}

private data class SubmissionUploadTarget(
    val studentId: String,
    val submissionId: String?
)

@Preview
@Composable
private fun TaskDetailDialogPreview() {
    LehrerLogTheme {
        TaskDetailDialog(
            state = TaskDetailState(
                task = SharedTestFixtures.testTaskDto(SharedTestFixtures.testClassId),
                students = listOf(SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)),
                submissions = listOf(
                    SharedTestFixtures.testSubmissionDto(
                        SharedTestFixtures.testTaskId,
                        SharedTestFixtures.testStudentId
                    )
                )
            ),
            onDismiss = {},
            onRefresh = {},
            onEditTask = { _, _, _, _ -> },
            onDeleteTask = {},
            onMarkInPerson = {},
            onUpdateSubmission = { _, _, _ -> },
            onUploadAssignmentFile = {},
            onUploadSubmissionFile = { _, _, _ -> }
        )
    }
}

@Preview
@Composable
private fun SubmissionRowPreview() {
    LehrerLogTheme {
        SubmissionRow(
            student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId),
            submission = SharedTestFixtures.testSubmissionDto(
                SharedTestFixtures.testTaskId,
                SharedTestFixtures.testStudentId
            ),
            onMarkInPerson = {},
            onEdit = {},
            onUploadFile = { _, _ -> }
        )
    }
}

@Preview
@Composable
private fun SubmissionEditDialogPreview() {
    LehrerLogTheme {
        SubmissionEditDialog(
            submission = SharedTestFixtures.testSubmissionDto(
                SharedTestFixtures.testTaskId,
                SharedTestFixtures.testStudentId
            ),
            onDismiss = {},
            onSave = { _, _ -> }
        )
    }
}

@Preview
@Composable
private fun SubmissionTypeLabelPreview() {
    LehrerLogTheme {
        Text(submissionTypeLabel(TaskSubmissionType.FILE))
    }
}

@Preview
@Composable
private fun LateStatusLabelPreview() {
    LehrerLogTheme {
        Text(lateStatusLabel(LateStatus.LATE_UNDECIDED))
    }
}
