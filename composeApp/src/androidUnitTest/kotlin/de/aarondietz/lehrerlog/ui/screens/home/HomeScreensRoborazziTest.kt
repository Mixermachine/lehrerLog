package de.aarondietz.lehrerlog.ui.screens.home

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
@Config(sdk = [33])
class HomeScreensRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun captureHomeLateOverview() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)
        val summary = SharedTestFixtures.testLatePeriodSummaryDto()
        val stats = SharedTestFixtures.testLateStudentStatsDto(student.id)

        composeTestRule.setContent {
            LehrerLogTheme {
                HomeLateStatsContent(
                    state = HomeLateStatsUiState(
                        isLoading = false,
                        summaries = listOf(summary),
                        activePeriodId = SharedTestFixtures.testLatePeriodId,
                        studentStats = listOf(stats),
                        students = listOf(student)
                    ),
                    onResolvePunishment = {}
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioLateOverview
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
