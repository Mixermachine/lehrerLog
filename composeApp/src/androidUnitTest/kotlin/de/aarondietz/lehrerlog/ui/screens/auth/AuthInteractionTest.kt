package de.aarondietz.lehrerlog.ui.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.ui.test.UiTestTags
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AuthInteractionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun loginFlow_updatesState_andTriggersActions() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        var state by mutableStateOf(LoginUiState())
        var loginClicks = 0
        var registerClicks = 0
        var parentInviteClicks = 0

        composeTestRule.setContent {
            LehrerLogTheme {
                LoginScreenContent(
                    loginState = state,
                    onNavigateToRegister = { registerClicks++ },
                    onNavigateToParentInvite = { parentInviteClicks++ },
                    onEmailChange = { state = state.copy(email = it) },
                    onPasswordChange = { state = state.copy(password = it) },
                    onLoginClick = { loginClicks++ }
                )
            }
        }

        composeTestRule.onNodeWithTag(UiTestTags.loginEmailField)
            .performTextInput(SharedTestFixtures.testLoginEmail)
        composeTestRule.onNodeWithTag(UiTestTags.loginPasswordField)
            .performTextInput(SharedTestFixtures.testLoginPassword)
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(UiTestTags.loginSubmitButton).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.loginRegisterButton).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.loginParentInviteButton).performClick()

        assertEquals(1, loginClicks)
        assertEquals(1, registerClicks)
        assertEquals(1, parentInviteClicks)
        assertEquals(SharedTestFixtures.testLoginEmail, state.email)
        assertEquals(SharedTestFixtures.testLoginPassword, state.password)
    }

    @Test
    fun registerFlow_updatesState_andTriggersActions() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        var state by mutableStateOf(
            RegisterUiState(
                schoolQuery = SharedTestFixtures.testSchoolSearchName,
                selectedSchool = SharedTestFixtures.testSchoolSearchResultDto()
            )
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                RegisterScreenContent(
                    registerState = state,
                    onNavigateToLogin = {},
                    onFirstNameChange = { state = state.copy(firstName = it) },
                    onLastNameChange = { state = state.copy(lastName = it) },
                    onEmailChange = { state = state.copy(email = it) },
                    onPasswordChange = { state = state.copy(password = it) },
                    onConfirmPasswordChange = { state = state.copy(confirmPassword = it) },
                    onSchoolQueryChange = { state = state.copy(schoolQuery = it) },
                    onSchoolSelected = { state = state.copy(selectedSchool = it) },
                    onRegisterClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(UiTestTags.registerFirstNameField)
            .performTextInput(SharedTestFixtures.testParentInviteFirstName)
        composeTestRule.onNodeWithTag(UiTestTags.registerLastNameField)
            .performTextInput(SharedTestFixtures.testParentInviteLastName)
        composeTestRule.onNodeWithTag(UiTestTags.registerEmailField)
            .performTextInput(SharedTestFixtures.testLoginEmail)
        composeTestRule.onNodeWithTag(UiTestTags.registerPasswordField)
            .performTextInput(SharedTestFixtures.testLoginPassword)
        composeTestRule.onNodeWithTag(UiTestTags.registerConfirmPasswordField)
            .performTextInput(SharedTestFixtures.testLoginPassword)
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(UiTestTags.registerSubmitButton, useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.registerLoginButton, useUnmergedTree = true).performClick()

        assertEquals(SharedTestFixtures.testParentInviteFirstName, state.firstName)
        assertEquals(SharedTestFixtures.testParentInviteLastName, state.lastName)
        assertEquals(SharedTestFixtures.testLoginEmail, state.email)
        assertEquals(SharedTestFixtures.testLoginPassword, state.password)
        assertEquals(SharedTestFixtures.testLoginPassword, state.confirmPassword)
    }
}
