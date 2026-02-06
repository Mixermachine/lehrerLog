package de.aarondietz.lehrerlog.ui.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
class ParentInviteRedeemInteractionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun redeemFlow_updatesState_andCallsActions() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        var state by mutableStateOf(ParentInviteUiState())

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentInviteRedeemContent(
                    state = state,
                    onNavigateToLogin = {},
                    onCodeChange = { state = state.copy(code = it) },
                    onEmailChange = { state = state.copy(email = it) },
                    onPasswordChange = { state = state.copy(password = it) },
                    onFirstNameChange = { state = state.copy(firstName = it) },
                    onLastNameChange = { state = state.copy(lastName = it) },
                    onRedeemClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(UiTestTags.parentInviteCodeField)
            .performTextInput(SharedTestFixtures.testParentInviteCode)
        composeTestRule.onNodeWithTag(UiTestTags.parentInviteFirstNameField)
            .performTextInput(SharedTestFixtures.testParentInviteFirstName)
        composeTestRule.onNodeWithTag(UiTestTags.parentInviteLastNameField)
            .performTextInput(SharedTestFixtures.testParentInviteLastName)
        composeTestRule.onNodeWithTag(UiTestTags.parentInviteEmailField)
            .performTextInput(SharedTestFixtures.testParentInviteEmail)
        composeTestRule.onNodeWithTag(UiTestTags.parentInvitePasswordField)
            .performTextInput(SharedTestFixtures.testParentInvitePassword)
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(UiTestTags.parentInviteSubmitButton, useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.parentInvitePasswordField).performImeAction()
        composeTestRule.onNodeWithTag(UiTestTags.parentInviteBackButton, useUnmergedTree = true).performClick()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        assertEquals(SharedTestFixtures.testParentInviteCode, state.code)
        assertEquals(SharedTestFixtures.testParentInviteEmail, state.email)
        assertEquals(SharedTestFixtures.testParentInvitePassword, state.password)
        assertEquals(SharedTestFixtures.testParentInviteFirstName, state.firstName)
        assertEquals(SharedTestFixtures.testParentInviteLastName, state.lastName)
    }
}
