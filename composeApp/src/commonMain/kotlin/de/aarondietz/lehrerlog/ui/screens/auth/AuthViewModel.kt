package de.aarondietz.lehrerlog.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.AuthResult
import de.aarondietz.lehrerlog.auth.UserDto
import de.aarondietz.lehrerlog.data.SchoolSearchResultDto
import de.aarondietz.lehrerlog.data.repository.SchoolRepository
import de.aarondietz.lehrerlog.database.DatabaseManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: UserDto) : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val schoolQuery: String = "",
    val selectedSchool: SchoolSearchResultDto? = null,
    val schoolSuggestions: List<SchoolSearchResultDto> = emptyList(),
    val isSchoolLoading: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val schoolRepository: SchoolRepository,
    private val databaseManager: DatabaseManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow(RegisterUiState())
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()
    private var schoolSearchJob: Job? = null

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) {
                _authState.value = AuthState.Loading
                when (val result = authRepository.getCurrentUser()) {
                    is AuthResult.Success -> {
                        _authState.value = AuthState.Authenticated(result.data)
                    }
                    is AuthResult.Error -> {
                        _authState.value = AuthState.Unauthenticated
                    }
                }
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    // Login functions
    fun updateLoginEmail(email: String) {
        _loginState.value = _loginState.value.copy(email = email, error = null)
    }

    fun updateLoginPassword(password: String) {
        _loginState.value = _loginState.value.copy(password = password, error = null)
    }

    fun login() {
        val state = _loginState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _loginState.value = state.copy(error = "Email and password are required")
            return
        }

        viewModelScope.launch {
            _loginState.value = _loginState.value.copy(isLoading = true, error = null)
            when (val result = authRepository.login(state.email, state.password)) {
                is AuthResult.Success -> {
                    _loginState.value = LoginUiState()
                    databaseManager.getDatabase()
                    _authState.value = AuthState.Authenticated(result.data.user)
                }
                is AuthResult.Error -> {
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    // Register functions
    fun updateRegisterEmail(email: String) {
        _registerState.value = _registerState.value.copy(email = email, error = null)
    }

    fun updateRegisterPassword(password: String) {
        _registerState.value = _registerState.value.copy(password = password, error = null)
    }

    fun updateRegisterConfirmPassword(confirmPassword: String) {
        _registerState.value = _registerState.value.copy(confirmPassword = confirmPassword, error = null)
    }

    fun updateRegisterFirstName(firstName: String) {
        _registerState.value = _registerState.value.copy(firstName = firstName, error = null)
    }

    fun updateRegisterLastName(lastName: String) {
        _registerState.value = _registerState.value.copy(lastName = lastName, error = null)
    }

    fun updateRegisterSchoolQuery(query: String) {
        _registerState.value = _registerState.value.copy(
            schoolQuery = query,
            selectedSchool = null,
            schoolSuggestions = emptyList(),
            isSchoolLoading = query.isNotBlank(),
            error = null
        )

        schoolSearchJob?.cancel()
        if (query.trim().length < 2) {
            _registerState.value = _registerState.value.copy(isSchoolLoading = false)
            return
        }

        schoolSearchJob = viewModelScope.launch {
            delay(250)
            val result = schoolRepository.searchSchools(query.trim())
            if (result.isSuccess) {
                _registerState.value = _registerState.value.copy(
                    schoolSuggestions = result.getOrNull().orEmpty(),
                    isSchoolLoading = false
                )
            } else {
                _registerState.value = _registerState.value.copy(
                    isSchoolLoading = false,
                    error = "Failed to load schools"
                )
            }
        }
    }

    fun selectRegisterSchool(school: SchoolSearchResultDto) {
        _registerState.value = _registerState.value.copy(
            schoolQuery = schoolDisplayName(school),
            selectedSchool = school,
            schoolSuggestions = emptyList(),
            isSchoolLoading = false,
            error = null
        )
    }

    fun register() {
        val state = _registerState.value

        // Validation
        when {
            state.email.isBlank() -> {
                _registerState.value = state.copy(error = "Email is required")
                return
            }
            state.password.isBlank() -> {
                _registerState.value = state.copy(error = "Password is required")
                return
            }
            state.password.length < 8 -> {
                _registerState.value = state.copy(error = "Password must be at least 8 characters")
                return
            }
            state.password != state.confirmPassword -> {
                _registerState.value = state.copy(error = "Passwords do not match")
                return
            }
            state.firstName.isBlank() -> {
                _registerState.value = state.copy(error = "First name is required")
                return
            }
            state.lastName.isBlank() -> {
                _registerState.value = state.copy(error = "Last name is required")
                return
            }
            state.selectedSchool == null -> {
                _registerState.value = state.copy(error = "School is required")
                return
            }
        }

        viewModelScope.launch {
            _registerState.value = _registerState.value.copy(isLoading = true, error = null)
            val schoolCode = state.selectedSchool?.code

            when (val result = authRepository.register(
                email = state.email,
                password = state.password,
                firstName = state.firstName,
                lastName = state.lastName,
                schoolCode = schoolCode
            )) {
                is AuthResult.Success -> {
                    _registerState.value = RegisterUiState()
                    databaseManager.getDatabase()
                    _authState.value = AuthState.Authenticated(result.data.user)
                }
                is AuthResult.Error -> {
                    _registerState.value = _registerState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.logout()
            databaseManager.reset()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun clearLoginError() {
        _loginState.value = _loginState.value.copy(error = null)
    }

    fun clearRegisterError() {
        _registerState.value = _registerState.value.copy(error = null)
    }

    private fun schoolDisplayName(school: SchoolSearchResultDto): String {
        val parts = listOfNotNull(school.name, school.city, school.postcode).filter { it.isNotBlank() }
        return if (parts.size > 1) {
            "${parts[0]} (${parts.drop(1).joinToString(", ")})"
        } else {
            school.name
        }
    }
}
