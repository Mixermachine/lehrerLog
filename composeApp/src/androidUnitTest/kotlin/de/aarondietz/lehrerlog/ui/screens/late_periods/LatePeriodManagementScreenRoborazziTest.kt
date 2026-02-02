package de.aarondietz.lehrerlog.ui.screens.late_periods

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.data.repository.LatePeriodRepository
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
class LatePeriodManagementScreenRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    private val json = Json { ignoreUnknownKeys = true }

    private fun createViewModel(): LatePeriodManagementViewModel {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val period = SharedTestFixtures.testLatePeriodDto()

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/late-periods" -> respondJson(json.encodeToString(listOf(period)))
                else -> respondJson(json.encodeToString(period))
            }
        }

        return LatePeriodManagementViewModel(
            latePeriodRepository = LatePeriodRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl)
        )
    }

    @Test
    fun captureLatePeriodManagementScreen() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val viewModel = createViewModel()

        composeTestRule.setContent {
            LehrerLogTheme {
                LatePeriodManagementScreen(viewModel = viewModel)
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioLatePeriodManagement
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
