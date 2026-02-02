package de.aarondietz.lehrerlog.ui.screens.late_periods

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.data.LatePeriodDto
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class LatePeriodComponentsRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Composable
    private fun LatePeriodCard(
        period: LatePeriodDto,
        onEdit: (LatePeriodDto) -> Unit,
        onActivate: (LatePeriodDto) -> Unit,
        onRecalculate: (LatePeriodDto) -> Unit,
        isLoading: Boolean
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = if (period.isActive) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                CardDefaults.cardColors()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = period.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (period.isActive) {
                        Text(
                            text = stringResource(Res.string.late_period_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = stringResource(
                        Res.string.late_period_dates,
                        period.startsAt.substringBefore("T"),
                        period.endsAt?.substringBefore("T") ?: stringResource(Res.string.late_period_ongoing)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                ) {
                    TextButton(onClick = { onEdit(period) }, enabled = !isLoading) {
                        Text(stringResource(Res.string.action_edit))
                    }
                    if (!period.isActive) {
                        TextButton(onClick = { onActivate(period) }, enabled = !isLoading) {
                            Text(stringResource(Res.string.late_period_activate))
                        }
                    }
                    TextButton(onClick = { onRecalculate(period) }, enabled = !isLoading) {
                        Text(stringResource(Res.string.late_period_recalculate))
                    }
                }
            }
        }
    }

    @Composable
    private fun CreateLatePeriodDialog(
        onDismiss: () -> Unit,
        onCreate: (String, String, String?) -> Unit
    ) {
        var name by remember { mutableStateOf("") }
        var startsAt by remember { mutableStateOf("") }
        var endsAt by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.late_period_create)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.late_period_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = startsAt,
                        onValueChange = { startsAt = it },
                        label = { Text(stringResource(Res.string.late_period_starts)) },
                        placeholder = { Text(stringResource(Res.string.due_date_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endsAt,
                        onValueChange = { endsAt = it },
                        label = { Text(stringResource(Res.string.late_period_ends)) },
                        placeholder = { Text(stringResource(Res.string.late_period_ends_optional)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreate(name.trim(), startsAt.trim(), endsAt.trim().ifBlank { null })
                    },
                    enabled = name.isNotBlank() && startsAt.isNotBlank()
                ) {
                    Text(stringResource(Res.string.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    @Composable
    private fun EditLatePeriodDialog(
        period: LatePeriodDto,
        onDismiss: () -> Unit,
        onUpdate: (String?, String?, String?) -> Unit
    ) {
        var name by remember { mutableStateOf(period.name) }
        var startsAt by remember { mutableStateOf(period.startsAt.substringBefore("T")) }
        var endsAt by remember { mutableStateOf(period.endsAt?.substringBefore("T") ?: "") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.late_period_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.late_period_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = startsAt,
                        onValueChange = { startsAt = it },
                        label = { Text(stringResource(Res.string.late_period_starts)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endsAt,
                        onValueChange = { endsAt = it },
                        label = { Text(stringResource(Res.string.late_period_ends)) },
                        placeholder = { Text(stringResource(Res.string.late_period_ends_optional)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate(
                            name.trim().takeIf { it.isNotBlank() },
                            startsAt.trim().takeIf { it.isNotBlank() },
                            endsAt.trim().ifBlank { null }
                        )
                    },
                    enabled = name.isNotBlank() && startsAt.isNotBlank()
                ) {
                    Text(stringResource(Res.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    @Test
    fun captureLatePeriodCardActive() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val period = LatePeriodDto(
            id = "period-123",
            name = "Semester 1 2026",
            startsAt = "2026-01-01T00:00:00Z",
            endsAt = "2026-06-30T23:59:59Z",
            isActive = true,
            createdAt = "2026-01-01T00:00:00Z"
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                LatePeriodCard(
                    period = period,
                    onEdit = {},
                    onActivate = {},
                    onRecalculate = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "LatePeriodCardActive"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureLatePeriodCardInactive() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val period = LatePeriodDto(
            id = "period-789",
            name = "Semester 2 2025",
            startsAt = "2025-07-01T00:00:00Z",
            endsAt = "2025-12-31T23:59:59Z",
            isActive = false,
            createdAt = "2025-07-01T00:00:00Z"
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                LatePeriodCard(
                    period = period,
                    onEdit = {},
                    onActivate = {},
                    onRecalculate = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "LatePeriodCardInactive"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureLatePeriodCardOngoing() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val period = LatePeriodDto(
            id = "period-ongoing",
            name = "Current Semester",
            startsAt = "2026-02-01T00:00:00Z",
            endsAt = null,
            isActive = true,
            createdAt = "2026-02-01T00:00:00Z"
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                LatePeriodCard(
                    period = period,
                    onEdit = {},
                    onActivate = {},
                    onRecalculate = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "LatePeriodCardOngoing"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureCreateLatePeriodDialog() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)

        composeTestRule.setContent {
            LehrerLogTheme {
                CreateLatePeriodDialog(
                    onDismiss = {},
                    onCreate = { _, _, _ -> }
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "CreateLatePeriodDialog"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureEditLatePeriodDialog() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val period = LatePeriodDto(
            id = "period-edit",
            name = "Old Semester Name",
            startsAt = "2026-01-15T00:00:00Z",
            endsAt = "2026-07-15T23:59:59Z",
            isActive = false,
            createdAt = "2026-01-15T00:00:00Z"
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                EditLatePeriodDialog(
                    period = period,
                    onDismiss = {},
                    onUpdate = { _, _, _ -> }
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "EditLatePeriodDialog"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
