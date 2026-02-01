package de.aarondietz.lehrerlog

import android.provider.Settings
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import com.dropbox.differ.ImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.Locale

/**
 * Shared utilities for Roborazzi snapshot tests.
 */
object RoborazziTestUtils {
    private const val SNAPSHOT_DIFF_THRESHOLD = 0.01f

    private val roborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = RoborazziOptions.CompareOptions.DefaultImageComparator,
            resultValidator = ::validateSnapshotDiff
        )
    )

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

    fun captureSnapshot(target: SemanticsNodeInteraction, snapshotPath: String) {
        File(snapshotPath).parentFile?.mkdirs()
        target.captureRoboImage(snapshotPath, roborazziOptions)
    }

    private fun validateSnapshotDiff(result: ImageComparator.ComparisonResult): Boolean {
        val diffRatio = result.pixelDifferences.toFloat() / result.pixelCount
        if (diffRatio > SNAPSHOT_DIFF_THRESHOLD) {
            val diffPercent = diffRatio * 100f
            val thresholdPercent = SNAPSHOT_DIFF_THRESHOLD * 100f
            val message = String.format(
                Locale.US,
                "Roborazzi diff %.2f%% exceeds %.2f%% threshold.",
                diffPercent,
                thresholdPercent
            )
            throw AssertionError(message)
        }
        return true
    }
}
