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
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.due_label
import lehrerlog.composeapp.generated.resources.nav_parent_assignments
import lehrerlog.composeapp.generated.resources.parent_no_assignments
import lehrerlog.composeapp.generated.resources.parent_select_student
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ParentAssignmentsScreen(
    viewModel: ParentAssignmentsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    ParentAssignmentsScreenContent(state = state)
}

@Composable
fun ParentAssignmentsScreenContent(
    state: ParentAssignmentsUiState
) {
    val spacing = MaterialTheme.spacing
    val title = stringResource(Res.string.nav_parent_assignments)

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
            state.assignments.isEmpty() -> {
                Text(
                    text = stringResource(Res.string.parent_no_assignments),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    items(state.assignments, key = { it.id }) { task ->
                        ParentAssignmentItem(task = task)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentAssignmentItem(task: TaskDto) {
    val spacing = MaterialTheme.spacing
    ListItem(
        headlineContent = {
            Text(text = task.title, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Text(
                text = stringResource(Res.string.due_label, task.dueAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.sm)
    )
}

@Preview
@Composable
private fun ParentAssignmentsContentPreview() {
    LehrerLogTheme {
        ParentAssignmentsScreenContent(
            state = ParentAssignmentsUiState(
                selectedStudentId = SharedTestFixtures.testStudentId,
                assignments = listOf(SharedTestFixtures.testTaskDto(SharedTestFixtures.testClassId))
            )
        )
    }
}

@Preview
@Composable
private fun ParentAssignmentItemPreview() {
    LehrerLogTheme {
        ParentAssignmentItem(task = SharedTestFixtures.testTaskDto(SharedTestFixtures.testClassId))
    }
}

@Preview
@Composable
private fun ParentAssignmentsScreenWrapperPreview() {
    LehrerLogTheme {
        ParentAssignmentsScreen()
    }
}
