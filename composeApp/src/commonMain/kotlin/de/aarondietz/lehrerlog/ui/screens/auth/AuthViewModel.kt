package de.aarondietz.lehrerlog.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.AuthResult
import de.aarondietz.lehrerlog.auth.UserDto
import de.aarondietz.lehrerlog.data.SchoolSearchResultDto
import de.aarondietz.lehrerlog.data.repository.SchoolRepository
import de.aarondietz.lehrerlog.ui.util.toStringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

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
    val errorResource: StringResource? = null
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
    val errorResource: StringResource? = null
)

data class ParentInviteUiState(
    val code: String = "",
    val email: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val isLoading: Boolean = false,
    val errorResource: StringResource? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val schoolRepository: SchoolRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow(RegisterUiState())
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    private val _parentInviteState = MutableStateFlow(ParentInviteUiState())
    val parentInviteState: StateFlow<ParentInviteUiState> = _parentInviteState.asStateFlow()
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
        _loginState.value = _loginState.value.copy(email = email, errorResource = null)
    }

    fun updateLoginPassword(password: String) {
        _loginState.value = _loginState.value.copy(password = password, errorResource = null)
    }

    fun login() {
        val state = _loginState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _loginState.value = state.copy(errorResource = Res.string.error_auth_email_required)
            return
        }

        viewModelScope.launch {
            _loginState.value = _loginState.value.copy(isLoading = true, errorResource = null)
            when (val result = authRepository.login(state.email, state.password)) {
                is AuthResult.Success -> {
                    _loginState.value = LoginUiState()
                    _authState.value = AuthState.Authenticated(result.data.user)
                }

                is AuthResult.Error -> {
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        errorResource = result.toStringResource()
                    )
                }
            }
        }
    }

    // Register functions
    fun updateRegisterEmail(email: String) {
        _registerState.value = _registerState.value.copy(email = email, errorResource = null)
    }

    fun updateRegisterPassword(password: String) {
        _registerState.value = _registerState.value.copy(password = password, errorResource = null)
    }

    fun updateRegisterConfirmPassword(confirmPassword: String) {
        _registerState.value = _registerState.value.copy(confirmPassword = confirmPassword, errorResource = null)
    }

    fun updateRegisterFirstName(firstName: String) {
        _registerState.value = _registerState.value.copy(firstName = firstName, errorResource = null)
    }

    fun updateRegisterLastName(lastName: String) {
        _registerState.value = _registerState.value.copy(lastName = lastName, errorResource = null)
    }

    fun updateRegisterSchoolQuery(query: String) {
        _registerState.value = _registerState.value.copy(
            schoolQuery = query,
            selectedSchool = null,
            schoolSuggestions = emptyList(),
            isSchoolLoading = query.isNotBlank(),
            errorResource = null
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
                    errorResource = Res.string.error_network_general
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
            errorResource = null
        )
    }

    fun register() {
        val state = _registerState.value

        // Validation
        when {
            state.email.isBlank() -> {
                _registerState.value = state.copy(errorResource = Res.string.error_validation_required_field)
                return
            }

            state.password.isBlank() -> {
                _registerState.value = state.copy(errorResource = Res.string.error_validation_required_field)
                return
            }

            state.password.length < 8 -> {
                _registerState.value = state.copy(errorResource = Res.string.error_validation_password_length)
                return
            }

            state.password != state.confirmPassword -> {
                _registerState.value = state.copy(errorResource = Res.string.error_validation_passwords_mismatch)
                return
            }

            state.firstName.isBlank() -> {
                _registerState.value = state.copy(errorResource = Res.string.error_validation_required_field)
                return
            }

            state.lastName.isBlank() -> {
                _registerState.value = state.copy(errorResource = Res.string.error_validation_required_field)
                return
            }

            state.selectedSchool == null -> {
                _registerState.value = state.copy(errorResource = Res.string.error_auth_school_required)
                return
            }
        }

        viewModelScope.launch {
            _registerState.value = _registerState.value.copy(isLoading = true, errorResource = null)
            val schoolCode = state.selectedSchool.code

            when (val result = authRepository.register(
                email = state.email,
                password = state.password,
                firstName = state.firstName,
                lastName = state.lastName,
                schoolCode = schoolCode
            )) {
                is AuthResult.Success -> {
                    _registerState.value = RegisterUiState()
                    _authState.value = AuthState.Authenticated(result.data.user)
                }

                is AuthResult.Error -> {
                    _registerState.value = _registerState.value.copy(
                        isLoading = false,
                        errorResource = result.toStringResource()
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authRepository.logout()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun clearLoginError() {
        _loginState.value = _loginState.value.copy(errorResource = null)
    }

    fun clearRegisterError() {
        _registerState.value = _registerState.value.copy(errorResource = null)
    }

    fun updateParentInviteCode(code: String) {
        _parentInviteState.value = _parentInviteState.value.copy(code = code, errorResource = null)
    }

    fun updateParentInviteEmail(email: String) {
        _parentInviteState.value = _parentInviteState.value.copy(email = email, errorResource = null)
    }

    fun updateParentInvitePassword(password: String) {
        _parentInviteState.value = _parentInviteState.value.copy(password = password, errorResource = null)
    }

    fun updateParentInviteFirstName(firstName: String) {
        _parentInviteState.value = _parentInviteState.value.copy(firstName = firstName, errorResource = null)
    }

    fun updateParentInviteLastName(lastName: String) {
        _parentInviteState.value = _parentInviteState.value.copy(lastName = lastName, errorResource = null)
    }

    fun redeemParentInvite() {
        val state = _parentInviteState.value
        if (state.code.isBlank()) {
            _parentInviteState.value = state.copy(errorResource = Res.string.error_validation_required_field)
            return
        }
        if (state.email.isBlank() || state.password.isBlank() || state.firstName.isBlank() || state.lastName.isBlank()) {
            _parentInviteState.value = state.copy(errorResource = Res.string.error_validation_required_field)
            return
        }

        viewModelScope.launch {
            _parentInviteState.value = state.copy(isLoading = true, errorResource = null)
            when (val result = authRepository.redeemParentInvite(
                code = state.code,
                email = state.email,
                password = state.password,
                firstName = state.firstName,
                lastName = state.lastName
            )) {
                is AuthResult.Success -> {
                    _parentInviteState.value = ParentInviteUiState()
                    _authState.value = AuthState.Authenticated(result.data.user)
                }

                is AuthResult.Error -> {
                    _parentInviteState.value = state.copy(isLoading = false, errorResource = result.toStringResource())
                }
            }
        }
    }

    fun clearParentInviteError() {
        _parentInviteState.value = _parentInviteState.value.copy(errorResource = null)
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
