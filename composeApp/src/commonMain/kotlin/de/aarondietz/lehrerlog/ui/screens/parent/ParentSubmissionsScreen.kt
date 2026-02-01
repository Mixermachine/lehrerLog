package de.aarondietz.lehrerlog.ui.screens.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.nav_parent_submissions
import lehrerlog.composeapp.generated.resources.parent_no_submissions
import lehrerlog.composeapp.generated.resources.parent_submission_grade
import lehrerlog.composeapp.generated.resources.parent_submission_note
import lehrerlog.composeapp.generated.resources.parent_select_student
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ParentSubmissionsScreen(
    viewModel: ParentSubmissionsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    ParentSubmissionsScreenContent(state = state)
}

@Composable
fun ParentSubmissionsScreenContent(
    state: ParentSubmissionsUiState
) {
    val spacing = MaterialTheme.spacing
    val title = stringResource(Res.string.nav_parent_submissions)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)

        when {
            state.selectedStudentId == null -> {
                Text(
                    text = stringResource(Res.string.parent_select_student),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.errorMessage != null -> {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            state.submissions.isEmpty() -> {
                Text(
                    text = stringResource(Res.string.parent_no_submissions),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    items(state.submissions, key = { it.id }) { submission ->
                        ParentSubmissionItem(submission = submission)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentSubmissionItem(submission: TaskSubmissionDto) {
    val spacing = MaterialTheme.spacing
    ListItem(
        headlineContent = {
            Text(
                text = submission.submissionType.name,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "${submission.lateStatus.name} � ${submission.submittedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                submission.grade?.let { grade ->
                    Text(
                        text = stringResource(Res.string.parent_submission_grade, grade),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                submission.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = stringResource(Res.string.parent_submission_note, note),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.sm)
    )
}

@Preview
@Composable
private fun ParentSubmissionsContentPreview() {
    LehrerLogTheme {
        ParentSubmissionsScreenContent(
            state = ParentSubmissionsUiState(
                selectedStudentId = SharedTestFixtures.testStudentId,
                submissions = listOf(
                    SharedTestFixtures.testSubmissionDto(
                        SharedTestFixtures.testTaskId,
                        SharedTestFixtures.testStudentId
                    )
                )
            )
        )
    }
}

@Preview
@Composable
private fun ParentSubmissionItemPreview() {
    LehrerLogTheme {
        ParentSubmissionItem(
            submission = SharedTestFixtures.testSubmissionDto(
                SharedTestFixtures.testTaskId,
                SharedTestFixtures.testStudentId
            )
        )
    }
}

@Preview
@Composable
private fun ParentSubmissionsScreenWrapperPreview() {
    LehrerLogTheme {
        ParentSubmissionsScreen()
    }
}
