package de.aarondietz.lehrerlog.ui.screens.auth

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ParentInviteRedeemRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun captureParentInviteRedeem() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val state = ParentInviteUiState(
            code = SharedTestFixtures.testParentInviteCode,
            email = SharedTestFixtures.testParentInviteEmail,
            password = SharedTestFixtures.testParentInvitePassword,
            firstName = SharedTestFixtures.testParentInviteFirstName,
            lastName = SharedTestFixtures.testParentInviteLastName,
            isLoading = false,
            errorResource = null
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentInviteRedeemContent(
                    state = state,
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

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioParentInviteRedeem
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
