package de.aarondietz.lehrerlog.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.data.LatePeriodDto
import de.aarondietz.lehrerlog.data.LatePeriodSummaryDto
import de.aarondietz.lehrerlog.data.LateStudentStatsDto
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.repository.LateStatsRepository
import de.aarondietz.lehrerlog.data.repository.PunishmentRepository
import de.aarondietz.lehrerlog.data.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeLateStatsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val missingSchool: Boolean = false,
    val periods: List<LatePeriodDto> = emptyList(),
    val summaries: List<LatePeriodSummaryDto> = emptyList(),
    val activePeriodId: String? = null,
    val studentStats: List<LateStudentStatsDto> = emptyList(),
    val students: List<StudentDto> = emptyList()
)

class HomeViewModel(
    private val lateStatsRepository: LateStatsRepository,
    private val punishmentRepository: PunishmentRepository,
    private val studentRepository: StudentRepository
) : ViewModel() {

    private val _lateStats = MutableStateFlow(HomeLateStatsUiState(isLoading = true))
    val lateStats: StateFlow<HomeLateStatsUiState> = _lateStats.asStateFlow()
    private var currentSchoolId: String? = null

    fun load(schoolId: String?) {
        if (schoolId.isNullOrBlank()) {
            _lateStats.value = HomeLateStatsUiState(missingSchool = true)
            return
        }
        currentSchoolId = schoolId

        viewModelScope.launch {
            _lateStats.value = _lateStats.value.copy(isLoading = true, errorMessage = null)

            val periodsResult = lateStatsRepository.getPeriods()
            val summariesResult = lateStatsRepository.getPeriodSummaries()
            val studentsResult = studentRepository.refreshStudents(schoolId)

            val periods = periodsResult.getOrElse { emptyList() }
            val summaries = summariesResult.getOrElse { emptyList() }
            val students = studentsResult.getOrElse { emptyList() }
            val activePeriod = periods.firstOrNull { it.isActive } ?: periods.firstOrNull()

            val stats = if (activePeriod != null) {
                lateStatsRepository.getStatsForPeriod(activePeriod.id).getOrElse { emptyList() }
            } else {
                emptyList()
            }

            val errorMessage = periodsResult.exceptionOrNull()?.message
                ?: summariesResult.exceptionOrNull()?.message
                ?: studentsResult.exceptionOrNull()?.message

            _lateStats.value = HomeLateStatsUiState(
                isLoading = false,
                errorMessage = errorMessage,
                periods = periods,
                summaries = summaries,
                activePeriodId = activePeriod?.id,
                studentStats = stats,
                students = students
            )
        }
    }

    fun resolvePunishment(studentId: String) {
        val periodId = _lateStats.value.activePeriodId ?: return
        val schoolId = currentSchoolId ?: return

        viewModelScope.launch {
            _lateStats.value = _lateStats.value.copy(isLoading = true, errorMessage = null)
            val result = punishmentRepository.resolvePunishment(studentId, periodId)
            if (result.isFailure) {
                _lateStats.value = _lateStats.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            } else {
                load(schoolId)
            }
        }
    }
}
