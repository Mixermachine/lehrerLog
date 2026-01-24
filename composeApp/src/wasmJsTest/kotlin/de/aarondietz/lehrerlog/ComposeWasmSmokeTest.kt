package de.aarondietz.lehrerlog

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ComposeWasmSmokeTest {
    @Test
    fun rendersText() = runComposeUiTest {
        setContent { Text("Wasm UI Test") }
        onNodeWithText("Wasm UI Test").assertIsDisplayed()
    }
}
