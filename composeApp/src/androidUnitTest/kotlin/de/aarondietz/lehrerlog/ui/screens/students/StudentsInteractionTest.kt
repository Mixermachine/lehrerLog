package de.aarondietz.lehrerlog.ui.screens.students

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.ui.test.UiTestTags
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StudentsInteractionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Test
    fun classCard_actionsAreReachable() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)

        val schoolClass = SharedTestFixtures.testSchoolClassDto()
        val student = SharedTestFixtures.testStudentDto(schoolClass.id)
        var expandClicks = 0
        var addStudentClicks = 0
        var deleteClassClicks = 0
        var deletedStudentId: String? = null
        var invitedStudentId: String? = null
        var parentLinksStudentId: String? = null

        composeTestRule.setContent {
            LehrerLogTheme {
                ClassCard(
                    schoolClass = schoolClass,
                    students = listOf(student),
                    isExpanded = true,
                    onExpandClick = { expandClicks++ },
                    onAddStudent = { addStudentClicks++ },
                    onDeleteClass = { deleteClassClicks++ },
                    onDeleteStudent = { deletedStudentId = it.id },
                    onInviteParent = { invitedStudentId = it.id },
                    onViewParentLinks = { parentLinksStudentId = it.id }
                )
            }
        }

        composeTestRule.onNodeWithTag(UiTestTags.studentsClassExpandButton(schoolClass.id)).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.studentsInviteParentButton(student.id)).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.studentsParentLinksButton(student.id)).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.studentsDeleteStudentButton(student.id)).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.studentsAddStudentButton(schoolClass.id)).performClick()
        composeTestRule.onNodeWithTag(UiTestTags.studentsDeleteClassButton(schoolClass.id)).performClick()

        assertEquals(1, expandClicks)
        assertEquals(1, addStudentClicks)
        assertEquals(1, deleteClassClicks)
        assertEquals(student.id, invitedStudentId)
        assertEquals(student.id, parentLinksStudentId)
        assertEquals(student.id, deletedStudentId)
    }
}
