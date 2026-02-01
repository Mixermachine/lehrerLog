package de.aarondietz.lehrerlog.ui.composables

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
class EditTaskDialogRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun captureEditTaskDialog() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val task = SharedTestFixtures.testTaskDto(SharedTestFixtures.testClassId)

        composeTestRule.setContent {
            LehrerLogTheme {
                EditTaskDialog(
                    task = task,
                    onDismiss = {},
                    onConfirm = { _, _, _ -> }
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "EditTaskDialog"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
