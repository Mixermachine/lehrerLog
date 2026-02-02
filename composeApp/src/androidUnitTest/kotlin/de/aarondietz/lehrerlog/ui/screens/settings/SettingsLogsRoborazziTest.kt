package de.aarondietz.lehrerlog.ui.screens.settings

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
class SettingsLogsRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun captureSettingsLogs() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)

        composeTestRule.setContent {
            LehrerLogTheme {
                SettingsContent(
                    quotaState = StorageQuotaState(quota = SharedTestFixtures.testStorageQuotaDto()),
                    logState = LogUiState(overview = SharedTestFixtures.testLogOverview()),
                    onRefreshQuota = {},
                    onRefreshLogs = {},
                    onShareLogs = {},
                    onClearLogs = {},
                    onLogout = {}
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioSettingsLogs
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
