package de.aarondietz.lehrerlog

import android.provider.Settings
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider

/**
 * Shared utilities for Roborazzi snapshot tests.
 */
object RoborazziTestUtils {

    /**
     * Disables all animations to ensure deterministic snapshot captures.
     * - Sets animation scales to 0 (animator, transition, window)
     * - Disables Compose test rule auto-advance
     *
     * Call this at the beginning of each Roborazzi test.
     */
    fun prepareAnimationsOff(composeTestRule: ComposeContentTestRule) {
        composeTestRule.mainClock.autoAdvance = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolver = context.contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        Settings.Global.putFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, 0f)
    }
}
