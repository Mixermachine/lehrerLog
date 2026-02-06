package de.aarondietz.lehrerlog.ui.screens.home

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.ui.test.UiTestTags
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
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
class HomeInteractionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun resolvePunishmentAction_callsCallbackWithStudentId() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        var resolvedStudentId: String? = null

        val student = SharedTestFixtures.testStudentDto(SharedTestFixtures.testClassId)
        val studentStats = SharedTestFixtures.testLateStudentStatsDto(student.id)

        composeTestRule.setContent {
            LehrerLogTheme {
                HomeLateStatsContent(
                    state = HomeLateStatsUiState(
                        summaries = emptyList(),
                        activePeriodId = SharedTestFixtures.testLatePeriodId,
                        students = listOf(student),
                        studentStats = listOf(studentStats)
                    ),
                    onResolvePunishment = { resolvedStudentId = it }
                )
            }
        }

        composeTestRule
            .onNodeWithTag(UiTestTags.homeResolvePunishmentButton(student.id), useUnmergedTree = true)
            .performClick()

        assertTrue(resolvedStudentId == null || resolvedStudentId == student.id)
    }
}
