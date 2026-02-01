package de.aarondietz.lehrerlog.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.ui.theme.spacing
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToParentInvite: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val loginState by viewModel.loginState.collectAsState()
    LoginScreenContent(
        loginState = loginState,
        onNavigateToRegister = onNavigateToRegister,
        onNavigateToParentInvite = onNavigateToParentInvite,
        onEmailChange = viewModel::updateLoginEmail,
        onPasswordChange = viewModel::updateLoginPassword,
        onLoginClick = viewModel::login
    )
}

@Composable
private fun LoginScreenContent(
    loginState: LoginUiState,
    onNavigateToRegister: () -> Unit,
    onNavigateToParentInvite: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.welcome_back),
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = loginState.email,
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
            enabled = !loginState.isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = loginState.password,
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
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onLoginClick()
                }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !loginState.isLoading
        )

        loginState.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loginState.isLoading
        ) {
            if (loginState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(Res.string.login))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToRegister) {
            Text(stringResource(Res.string.no_account) + " ")
            Text(
                text = stringResource(Res.string.register),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))

        TextButton(onClick = onNavigateToParentInvite) {
            Text(stringResource(Res.string.parent_invite_redeem))
        }
    }
}

@Preview
@Composable
private fun LoginScreenPreview() {
    LehrerLogTheme {
        LoginScreenContent(
            loginState = LoginUiState(
                email = SharedTestFixtures.testLoginEmail,
                password = SharedTestFixtures.testLoginPassword
            ),
            onNavigateToRegister = {},
            onNavigateToParentInvite = {},
            onEmailChange = {},
            onPasswordChange = {},
            onLoginClick = {}
        )
    }
}

@Preview
@Composable
private fun LoginScreenLoadingPreview() {
    LehrerLogTheme {
        LoginScreenContent(
            loginState = LoginUiState(
                email = SharedTestFixtures.testLoginEmail,
                password = SharedTestFixtures.testLoginPassword,
                isLoading = true
            ),
            onNavigateToRegister = {},
            onNavigateToParentInvite = {},
            onEmailChange = {},
            onPasswordChange = {},
            onLoginClick = {}
        )
    }
}

@Preview
@Composable
private fun LoginScreenErrorPreview() {
    LehrerLogTheme {
        LoginScreenContent(
            loginState = LoginUiState(
                email = SharedTestFixtures.testLoginEmail,
                password = SharedTestFixtures.testLoginPassword,
                error = SharedTestFixtures.testLoginError
            ),
            onNavigateToRegister = {},
            onNavigateToParentInvite = {},
            onEmailChange = {},
            onPasswordChange = {},
            onLoginClick = {}
        )
    }
}

@Preview
@Composable
private fun LoginScreenWrapperPreview() {
    LehrerLogTheme {
        LoginScreen(
            onNavigateToRegister = {},
            onNavigateToParentInvite = {}
        )
    }
}
