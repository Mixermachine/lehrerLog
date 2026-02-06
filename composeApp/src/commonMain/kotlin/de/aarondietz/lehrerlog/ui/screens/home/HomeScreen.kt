package de.aarondietz.lehrerlog.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.ui.components.StudentLateStatsChart
import de.aarondietz.lehrerlog.ui.test.UiTestTags
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    schoolId: String?,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.lateStats.collectAsState()

    LaunchedEffect(schoolId) {
        viewModel.load(schoolId)
    }

    HomeLateStatsContent(
        state = state,
        onResolvePunishment = viewModel::resolvePunishment
    )
}

@Composable
internal fun HomeLateStatsContent(
    state: HomeLateStatsUiState,
    onResolvePunishment: (String) -> Unit
) {
    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
    ) {
        if (state.missingSchool) {
            item {
                Text(stringResource(Res.string.late_overview_missing_school))
            }
        } else if (state.errorMessage != null) {
            item {
                Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }

        if (state.summaries.isEmpty()) {
            item {
                Text(stringResource(Res.string.late_overview_empty))
            }
        } else {
            item {
                Text(
                    stringResource(Res.string.late_overview_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            items(state.summaries) { summary ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(summary.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        Text(stringResource(Res.string.late_overview_missed, summary.totalMissed))
                        Text(stringResource(Res.string.late_overview_punishments, summary.totalPunishments))
                    }
                }
            }
        }

        if (state.activePeriodId != null && state.studentStats.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
                Text(
                    stringResource(Res.string.late_overview_active_period),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Chart visualization
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.sm)
                ) {
                    Column(modifier = Modifier.padding(MaterialTheme.spacing.md)) {
                        Text(
                            text = stringResource(Res.string.late_stats_chart_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
                        StudentLateStatsChart(
                            studentNames = state.studentStats.mapNotNull { stats ->
                                state.students.firstOrNull { it.id == stats.studentId }?.let {
                                    "${it.firstName} ${it.lastName}"
                                }
                            },
                            lateCounts = state.studentStats.map { it.totalMissed }
                        )
                    }
                }
            }

            items(state.studentStats) { stats ->
                val student = state.students.firstOrNull { it.id == stats.studentId }
                StudentLateStatsRow(
                    student = student,
                    stats = stats,
                    onResolvePunishment = {
                        onResolvePunishment(stats.studentId)
                    }
                )
            }
        }
    }
}

@Composable
internal fun StudentLateStatsRow(
    student: StudentDto?,
    stats: de.aarondietz.lehrerlog.data.LateStudentStatsDto,
    onResolvePunishment: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = student?.let { "${it.firstName} ${it.lastName}" } ?: stats.studentId,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Text(stringResource(Res.string.late_overview_missed, stats.totalMissed))
            Text(stringResource(Res.string.late_overview_since, stats.missedSincePunishment))
            if (stats.punishmentRequired) {
                Text(
                    stringResource(Res.string.late_overview_punishment_required),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (stats.punishmentRequired) {
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.xs))
            TextButton(
                onClick = onResolvePunishment,
                modifier = Modifier.testTag(UiTestTags.homeResolvePunishmentButton(stats.studentId))
            ) {
                Text(stringResource(Res.string.punishment_resolve))
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    LehrerLogTheme {
        HomeLateStatsContent(
            state = HomeLateStatsUiState(
                summaries = listOf(SharedTestFixtures.testLatePeriodSummaryDto()),
                activePeriodId = SharedTestFixtures.testLatePeriodId,
                students = listOf(SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)),
                studentStats = listOf(SharedTestFixtures.testLateStudentStatsDto(SharedTestFixtures.testStudentId))
            ),
            onResolvePunishment = {}
        )
    }
}

@Preview
@Composable
private fun StudentLateStatsRowPreview() {
    LehrerLogTheme {
        StudentLateStatsRow(
            student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId),
            stats = SharedTestFixtures.testLateStudentStatsDto(SharedTestFixtures.testStudentId),
            onResolvePunishment = {}
        )
    }
}

@Preview
@Composable
private fun HomeScreenWrapperPreview() {
    LehrerLogTheme {
        HomeScreen(schoolId = SharedTestFixtures.testSchoolId)
    }
}
