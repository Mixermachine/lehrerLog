package de.aarondietz.lehrerlog.ui.screens.home

import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.data.repository.LateStatsRepository
import de.aarondietz.lehrerlog.data.repository.PunishmentRepository
import de.aarondietz.lehrerlog.data.repository.StudentRepository
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loadsLateStatsAndResolvesPunishment() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val period = SharedTestFixtures.testLatePeriodDto()
        val summary = SharedTestFixtures.testLatePeriodSummaryDto()
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)
        val stats = SharedTestFixtures.testLateStudentStatsDto(student.id)
        val record = SharedTestFixtures.testPunishmentRecordDto(student.id, period.id)

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/late-periods" -> respondJson(json.encodeToString(listOf(period)))
                "/api/late-stats/periods" -> respondJson(json.encodeToString(listOf(summary)))
                "/api/late-stats/students" -> respondJson(json.encodeToString(listOf(stats)))
                "/api/students" -> respondJson(json.encodeToString(listOf(student)))
                "/api/punishments/resolve" -> respondJson(json.encodeToString(record))
                else -> respondJson(json.encodeToString(emptyList<String>()))
            }
        }

        val lateRepo = LateStatsRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val studentRepo = StudentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        val periodsResult = lateRepo.getPeriods()
        assertTrue(periodsResult.isSuccess, "Periods failed: ${periodsResult.exceptionOrNull()}")
        val summariesResult = lateRepo.getPeriodSummaries()
        assertTrue(summariesResult.isSuccess, "Summaries failed: ${summariesResult.exceptionOrNull()}")
        val studentsResult = studentRepo.refreshStudents(SharedTestFixtures.testSchoolId)
        assertTrue(studentsResult.isSuccess, "Students failed: ${studentsResult.exceptionOrNull()}")

        val viewModel = HomeViewModel(
            lateStatsRepository = lateRepo,
            punishmentRepository = PunishmentRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            studentRepository = studentRepo
        )

        try {
            viewModel.load(SharedTestFixtures.testSchoolId)
            awaitUntil { !viewModel.lateStats.value.isLoading }

            val state = viewModel.lateStats.value
            assertFalse(state.isLoading)
            assertTrue(state.periods.isNotEmpty())

            viewModel.resolvePunishment(student.id)
            awaitUntil { viewModel.lateStats.value.errorMessage == null }

            assertTrue(viewModel.lateStats.value.errorMessage == null)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
