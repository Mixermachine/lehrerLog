package de.aarondietz.lehrerlog.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DesktopComposeUiScaffoldTest {

    @Test
    fun desktopScaffold_interactionsRunViaComposeUiTest() = runComposeUiTest {
        var clicks by mutableStateOf(0)

        setContent {
            LehrerLogTheme {
                Button(onClick = { clicks += 1 }) {
                    val label = if (clicks == 0) {
                        "Desktop UI Scaffold"
                    } else {
                        "Desktop UI Clicked $clicks"
                    }
                    Text(label)
                }
            }
        }

        onNodeWithText("Desktop UI Scaffold").assertIsDisplayed()
        onNodeWithText("Desktop UI Scaffold").performClick()
        onNodeWithText("Desktop UI Clicked 1").assertIsDisplayed()
    }
}
