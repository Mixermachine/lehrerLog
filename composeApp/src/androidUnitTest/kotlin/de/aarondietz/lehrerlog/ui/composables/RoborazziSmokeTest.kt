package de.aarondietz.lehrerlog.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class RoborazziSmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun captureEmptyDialog() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)

        composeTestRule.setContent {
            LehrerLogTheme {
                Column {
                    Text(SharedTestFixtures.roborazziTitle)
                    OutlinedTextField(
                        value = SharedTestFixtures.roborazziFieldValue,
                        onValueChange = {},
                        label = { Text(SharedTestFixtures.roborazziFieldLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioEmpty
        )
        File(snapshotPath).parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(snapshotPath)
    }

    @Test
    fun captureClassCardExpanded() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val students = listOf(SharedTestFixtures.testStudentDto(schoolClass.id))

        composeTestRule.setContent {
            LehrerLogTheme {
                de.aarondietz.lehrerlog.ui.screens.students.ClassCard(
                    schoolClass = schoolClass,
                    students = students,
                    isExpanded = true,
                    onExpandClick = {},
                    onAddStudent = {},
                    onDeleteClass = {},
                    onDeleteStudent = {},
                    onInviteParent = {},
                    onViewParentLinks = {}
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            SharedTestFixtures.scenarioClassCardExpanded
        )
        File(snapshotPath).parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(snapshotPath)
    }
}
