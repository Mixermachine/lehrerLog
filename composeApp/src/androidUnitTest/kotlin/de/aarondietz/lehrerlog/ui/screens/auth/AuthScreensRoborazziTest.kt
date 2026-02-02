package de.aarondietz.lehrerlog.ui.screens.auth

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.data.repository.SchoolRepository
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AuthScreensRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    private val json = Json { ignoreUnknownKeys = true }

    private fun createViewModel(): AuthViewModel {
        val tokenStorage = InMemoryTokenStorage()
        val authResponse = SharedTestFixtures.testAuthResponse()
        val schoolResult = SharedTestFixtures.testSchoolSearchResultDto()

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/auth/login" -> respondJson(json.encodeToString(authResponse))
                "/auth/register" -> respondJson(json.encodeToString(authResponse))
                "/api/parent-invites/redeem" -> respondJson(json.encodeToString(authResponse))
                "/schools/search" -> respondJson(json.encodeToString(listOf(schoolResult)))
                else -> respondJson(json.encodeToString(authResponse))
            }
        }

        return AuthViewModel(
            authRepository = AuthRepository(httpClient, tokenStorage),
            schoolRepository = SchoolRepository(httpClient)
        )
    }

    @Test
    fun captureLoginScreen() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val viewModel = createViewModel()

        composeTestRule.setContent {
            LehrerLogTheme {
                LoginScreen(
                    onNavigateToRegister = {},
                    onNavigateToParentInvite = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.runOnIdle {
            viewModel.updateLoginEmail(SharedTestFixtures.testLoginEmail)
            viewModel.updateLoginPassword(SharedTestFixtures.testLoginPassword)
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioLogin
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureLoginError() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val viewModel = createViewModel()

        composeTestRule.setContent {
            LehrerLogTheme {
                LoginScreen(
                    onNavigateToRegister = {},
                    onNavigateToParentInvite = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.runOnIdle {
            viewModel.login()
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioLoginError
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureRegisterScreen() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val viewModel = createViewModel()
        val schoolResult = SharedTestFixtures.testSchoolSearchResultDto()

        composeTestRule.setContent {
            LehrerLogTheme {
                RegisterScreen(
                    onNavigateToLogin = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.runOnIdle {
            viewModel.updateRegisterEmail(SharedTestFixtures.testLoginEmail)
            viewModel.updateRegisterPassword(SharedTestFixtures.testLoginPassword)
            viewModel.updateRegisterConfirmPassword(SharedTestFixtures.testLoginPassword)
            viewModel.updateRegisterFirstName(SharedTestFixtures.testAuthFirstName)
            viewModel.updateRegisterLastName(SharedTestFixtures.testAuthLastName)
            viewModel.selectRegisterSchool(schoolResult)
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioRegister
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureRegisterError() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val viewModel = createViewModel()

        composeTestRule.setContent {
            LehrerLogTheme {
                RegisterScreen(
                    onNavigateToLogin = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.runOnIdle {
            viewModel.register()
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioRegisterError
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
