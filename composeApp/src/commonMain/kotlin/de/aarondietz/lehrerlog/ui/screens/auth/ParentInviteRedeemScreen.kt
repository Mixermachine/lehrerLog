package de.aarondietz.lehrerlog.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.tooling.preview.Preview
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ParentInviteRedeemScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.parentInviteState.collectAsState()
    ParentInviteRedeemContent(
        state = state,
        onNavigateToLogin = onNavigateToLogin,
        onCodeChange = viewModel::updateParentInviteCode,
        onEmailChange = viewModel::updateParentInviteEmail,
        onPasswordChange = viewModel::updateParentInvitePassword,
        onFirstNameChange = viewModel::updateParentInviteFirstName,
        onLastNameChange = viewModel::updateParentInviteLastName,
        onRedeemClick = viewModel::redeemParentInvite
    )
}

@Preview
@Composable
private fun ParentInviteRedeemPreview() {
    LehrerLogTheme {
        ParentInviteRedeemContent(
            state = ParentInviteUiState(
                code = SharedTestFixtures.testParentInviteCode,
                email = SharedTestFixtures.testParentInviteEmail,
                password = SharedTestFixtures.testParentInvitePassword,
                firstName = SharedTestFixtures.testParentInviteFirstName,
                lastName = SharedTestFixtures.testParentInviteLastName
            ),
            onNavigateToLogin = {},
            onCodeChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onFirstNameChange = {},
            onLastNameChange = {},
            onRedeemClick = {}
        )
    }
}

@Preview
@Composable
private fun ParentInviteRedeemWrapperPreview() {
    LehrerLogTheme {
        ParentInviteRedeemScreen(onNavigateToLogin = {})
    }
}

@Composable
internal fun ParentInviteRedeemContent(
    state: ParentInviteUiState,
    onNavigateToLogin: () -> Unit,
    onCodeChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onRedeemClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.lg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.parent_invite_title),
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))

        Text(
            text = stringResource(Res.string.parent_invite_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.lg))

        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(Res.string.parent_invite_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))

        OutlinedTextField(
            value = state.firstName,
            onValueChange = onFirstNameChange,
            label = { Text(stringResource(Res.string.first_name)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))

        OutlinedTextField(
            value = state.lastName,
            onValueChange = onLastNameChange,
            label = { Text(stringResource(Res.string.last_name)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))

        OutlinedTextField(
            value = state.email,
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
            enabled = !state.isLoading
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(Res.string.password)) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
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
                    onRedeemClick()
                }
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        )

        state.errorResource?.let { errorResource ->
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))
            Text(
                text = stringResource(errorResource),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.lg))

        Button(
            onClick = onRedeemClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(MaterialTheme.spacing.md),
                    strokeWidth = MaterialTheme.spacing.xs
                )
            } else {
                Text(stringResource(Res.string.parent_invite_redeem))
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.sm))

        TextButton(onClick = onNavigateToLogin, enabled = !state.isLoading) {
            Text(stringResource(Res.string.parent_invite_to_login))
        }
    }
}
