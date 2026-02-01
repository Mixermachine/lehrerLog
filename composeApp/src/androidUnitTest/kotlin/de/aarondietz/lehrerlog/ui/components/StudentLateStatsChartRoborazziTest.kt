package de.aarondietz.lehrerlog.ui.components

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
class StudentLateStatsChartRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun captureStudentLateStatsChart() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)

        composeTestRule.setContent {
            LehrerLogTheme {
                StudentLateStatsChart(
                    studentNames = listOf("Max Mustermann", "Anna Schmidt", "Tom Weber"),
                    lateCounts = listOf(0, 2, 5)
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "StudentLateStatsChart"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureStudentLateStatsChartEmpty() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)

        composeTestRule.setContent {
            LehrerLogTheme {
                StudentLateStatsChart(
                    studentNames = emptyList(),
                    lateCounts = emptyList()
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "StudentLateStatsChartEmpty"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureStudentLateStatsChartAllGreen() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)

        composeTestRule.setContent {
            LehrerLogTheme {
                StudentLateStatsChart(
                    studentNames = listOf("Lisa Klein", "Paul Gross", "Emma Jung"),
                    lateCounts = listOf(0, 0, 0)
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "StudentLateStatsChartAllGreen"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
