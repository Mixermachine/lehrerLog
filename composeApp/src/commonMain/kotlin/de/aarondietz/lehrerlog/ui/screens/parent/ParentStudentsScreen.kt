package de.aarondietz.lehrerlog.ui.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.nav_parent_students
import lehrerlog.composeapp.generated.resources.parent_no_students
import lehrerlog.composeapp.generated.resources.parent_select_student
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ParentStudentsScreen(
    viewModel: ParentStudentsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    ParentStudentsScreenContent(
        state = state,
        onSelectStudent = viewModel::selectStudent,
        onRefresh = viewModel::refresh
    )
}

@Preview
@Composable
private fun ParentStudentsContentPreview() {
    LehrerLogTheme {
        ParentStudentsScreenContent(
            state = ParentStudentsUiState(
                students = listOf(SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)),
                selectedStudentId = SharedTestFixtures.testStudentId
            ),
            onSelectStudent = {},
            onRefresh = {}
        )
    }
}

@Preview
@Composable
private fun ParentStudentsScreenWrapperPreview() {
    LehrerLogTheme {
        ParentStudentsScreen()
    }
}

@Composable
fun ParentStudentsScreenContent(
    state: ParentStudentsUiState,
    onSelectStudent: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val title = stringResource(Res.string.nav_parent_students)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)

        when {
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
            state.students.isEmpty() -> {
                Text(
                    text = stringResource(Res.string.parent_no_students),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {
                Text(
                    text = stringResource(Res.string.parent_select_student),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    contentPadding = PaddingValues(vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    items(state.students, key = { it.id }) { student ->
                        val isSelected = student.id == state.selectedStudentId
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "${student.firstName} ${student.lastName}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                                .clickable { onSelectStudent(student.id) }
                                .padding(spacing.sm)
                        )
                    }
                }
            }
        }
    }
}
