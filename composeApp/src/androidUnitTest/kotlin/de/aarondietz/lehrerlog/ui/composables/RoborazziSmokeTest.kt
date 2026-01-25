package de.aarondietz.lehrerlog.ui.composables

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import de.aarondietz.lehrerlog.SharedTestFixtures
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
    val composeTestRule = createComposeRule()

    @Test
    fun captureEmptyDialog() {
        prepareAnimationsOff()

        composeTestRule.setContent {
            MaterialTheme {
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
        prepareAnimationsOff()
        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val students = listOf(SharedTestFixtures.testStudentDto(schoolClass.id))

        composeTestRule.setContent {
            MaterialTheme {
                de.aarondietz.lehrerlog.ui.screens.students.ClassCard(
                    schoolClass = schoolClass,
                    students = students,
                    isExpanded = true,
                    onExpandClick = {},
                    onAddStudent = {},
                    onDeleteClass = {},
                    onDeleteStudent = {}
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

    private fun prepareAnimationsOff() {
        composeTestRule.mainClock.autoAdvance = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolver = context.contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        Settings.Global.putFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, 0f)
    }
}
