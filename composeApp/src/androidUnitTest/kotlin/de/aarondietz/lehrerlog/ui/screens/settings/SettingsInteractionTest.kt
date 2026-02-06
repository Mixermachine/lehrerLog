package de.aarondietz.lehrerlog.ui.screens.settings

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.ui.test.UiTestTags
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SettingsInteractionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun settingsActions_triggerAllCallbacks() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)

        var refreshQuotaClicks = 0
        var refreshLogsClicks = 0

        composeTestRule.setContent {
            LehrerLogTheme {
                SettingsContent(
                    quotaState = StorageQuotaState(quota = SharedTestFixtures.testStorageQuotaDto()),
                    logState = LogUiState(overview = SharedTestFixtures.testLogOverview()),
                    onRefreshQuota = { refreshQuotaClicks++ },
                    onRefreshLogs = { refreshLogsClicks++ },
                    onShareLogs = {},
                    onClearLogs = {},
                    onLogout = {}
                )
            }
        }

        assertTrue(
            runCatching {
                composeTestRule.onNodeWithTag(UiTestTags.settingsShareLogsButton).fetchSemanticsNode()
            }.isSuccess
        )
        assertTrue(
            runCatching {
                composeTestRule.onNodeWithTag(UiTestTags.settingsClearLogsButton).fetchSemanticsNode()
            }.isSuccess
        )
        composeTestRule.onNodeWithTag(UiTestTags.settingsRefreshQuotaButton).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.settingsRefreshLogsButton).performClick()

        assertEquals(1, refreshQuotaClicks)
        assertEquals(1, refreshLogsClicks)
    }
}
