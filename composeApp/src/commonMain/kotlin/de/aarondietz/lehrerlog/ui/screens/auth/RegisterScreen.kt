package de.aarondietz.lehrerlog.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.confirm_password
import lehrerlog.composeapp.generated.resources.create_account
import lehrerlog.composeapp.generated.resources.email
import lehrerlog.composeapp.generated.resources.first_name
import lehrerlog.composeapp.generated.resources.have_account
import lehrerlog.composeapp.generated.resources.last_name
import lehrerlog.composeapp.generated.resources.login
import lehrerlog.composeapp.generated.resources.password
import lehrerlog.composeapp.generated.resources.register
import lehrerlog.composeapp.generated.resources.register_subtitle
import lehrerlog.composeapp.generated.resources.school_search
import lehrerlog.composeapp.generated.resources.school_search_hint
import lehrerlog.composeapp.generated.resources.school_search_min_chars
import lehrerlog.composeapp.generated.resources.school_search_no_results
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val registerState by viewModel.registerState.collectAsState()
    RegisterScreenContent(
        registerState = registerState,
        onNavigateToLogin = onNavigateToLogin,
        onFirstNameChange = viewModel::updateRegisterFirstName,
        onLastNameChange = viewModel::updateRegisterLastName,
        onEmailChange = viewModel::updateRegisterEmail,
        onPasswordChange = viewModel::updateRegisterPassword,
        onConfirmPasswordChange = viewModel::updateRegisterConfirmPassword,
        onSchoolQueryChange = viewModel::updateRegisterSchoolQuery,
        onSchoolSelected = viewModel::selectRegisterSchool,
        onRegisterClick = viewModel::register
    )
}

@Composable
private fun RegisterScreenContent(
    registerState: RegisterUiState,
    onNavigateToLogin: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSchoolQueryChange: (String) -> Unit,
    onSchoolSelected: (de.aarondietz.lehrerlog.data.SchoolSearchResultDto) -> Unit,
    onRegisterClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val suggestionScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.create_account),
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.register_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = registerState.schoolQuery,
            onValueChange = {
                onSchoolQueryChange(it)
            },
            label = { Text(stringResource(Res.string.school_search)) },
            leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
            trailingIcon = {
                if (registerState.isSchoolLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !registerState.isLoading
        )

        if (registerState.schoolSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(suggestionScrollState)
            ) {
                Column {
                    registerState.schoolSuggestions.forEachIndexed { index, school ->
                        val label = listOfNotNull(
                            school.name,
                            school.city,
                            school.postcode
                        ).filter { it.isNotBlank() }
                        val displayLabel = if (label.size > 1) {
                            "${label[0]} (${label.drop(1).joinToString(", ")})"
                        } else {
                            school.name
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = !registerState.isLoading
                                ) {
                                    onSchoolSelected(school)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(text = displayLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (index < registerState.schoolSuggestions.lastIndex) {
                            Divider()
                        }
                    }
                }
            }
        }

        val schoolHelperText = when {
            registerState.schoolQuery.isBlank() ->
                stringResource(Res.string.school_search_hint)
            registerState.schoolQuery.trim().length < 2 ->
                stringResource(Res.string.school_search_min_chars)
            !registerState.isSchoolLoading &&
                registerState.schoolSuggestions.isEmpty() &&
                registerState.selectedSchool == null ->
                stringResource(Res.string.school_search_no_results)
            else -> null
        }

        schoolHelperText?.let { helperText ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = registerState.firstName,
            onValueChange = onFirstNameChange,
            label = { Text(stringResource(Res.string.first_name)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !registerState.isLoading
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = registerState.lastName,
            onValueChange = onLastNameChange,
            label = { Text(stringResource(Res.string.last_name)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !registerState.isLoading
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = registerState.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(Res.string.email)) },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !registerState.isLoading
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = registerState.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(Res.string.password)) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !registerState.isLoading
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = registerState.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text(stringResource(Res.string.confirm_password)) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(
                        if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !registerState.isLoading
        )

        Spacer(modifier = Modifier.height(12.dp))

        registerState.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !registerState.isLoading
        ) {
            if (registerState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(Res.string.register))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text(stringResource(Res.string.have_account) + " ")
            Text(
                text = stringResource(Res.string.login),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview
@Composable
private fun RegisterScreenPreview() {
    MaterialTheme {
        RegisterScreenContent(
            registerState = RegisterUiState(
                firstName = "John",
                lastName = "Doe",
                email = "john.doe@example.com",
                password = "password123",
                confirmPassword = "password123",
                schoolQuery = "Gymnasium Musterstadt"
            ),
            onNavigateToLogin = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onSchoolQueryChange = {},
            onSchoolSelected = {},
            onRegisterClick = {}
        )
    }
}

@Preview
@Composable
private fun RegisterScreenLoadingPreview() {
    MaterialTheme {
        RegisterScreenContent(
            registerState = RegisterUiState(
                firstName = "John",
                lastName = "Doe",
                email = "john.doe@example.com",
                password = "password123",
                confirmPassword = "password123",
                isLoading = true
            ),
            onNavigateToLogin = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onSchoolQueryChange = {},
            onSchoolSelected = {},
            onRegisterClick = {}
        )
    }
}

@Preview
@Composable
private fun RegisterScreenErrorPreview() {
    MaterialTheme {
        RegisterScreenContent(
            registerState = RegisterUiState(
                firstName = "John",
                lastName = "Doe",
                email = "john.doe@example.com",
                password = "pass",
                confirmPassword = "different",
                error = "Passwords do not match"
            ),
            onNavigateToLogin = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onSchoolQueryChange = {},
            onSchoolSelected = {},
            onRegisterClick = {}
        )
    }
}
