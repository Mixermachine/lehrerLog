package de.aarondietz.lehrerlog.ui.screens.parent_management

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import de.aarondietz.lehrerlog.RoborazziTestActivity
import de.aarondietz.lehrerlog.RoborazziTestUtils
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.data.ParentLinkDto
import de.aarondietz.lehrerlog.data.ParentLinkStatus
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.parent_link_created
import lehrerlog.composeapp.generated.resources.parent_link_id
import lehrerlog.composeapp.generated.resources.parent_link_revoke
import lehrerlog.composeapp.generated.resources.parent_link_status_active
import lehrerlog.composeapp.generated.resources.parent_link_status_pending
import lehrerlog.composeapp.generated.resources.parent_link_status_revoked
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class ParentInviteComponentsRoborazziTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RoborazziTestActivity>()

    @Composable
    private fun ParentLinkCard(
        link: ParentLinkDto,
        onRevoke: () -> Unit,
        isLoading: Boolean
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.parent_link_id, link.id.take(8)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(Res.string.parent_link_created, link.createdAt.substringBefore("T")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when (link.status) {
                            ParentLinkStatus.ACTIVE -> stringResource(Res.string.parent_link_status_active)
                            ParentLinkStatus.REVOKED -> stringResource(Res.string.parent_link_status_revoked)
                            ParentLinkStatus.PENDING -> stringResource(Res.string.parent_link_status_pending)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (link.status) {
                            ParentLinkStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                            ParentLinkStatus.REVOKED -> MaterialTheme.colorScheme.error
                            ParentLinkStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
                if (link.status == ParentLinkStatus.ACTIVE) {
                    TextButton(
                        onClick = onRevoke,
                        enabled = !isLoading
                    ) {
                        Text(stringResource(Res.string.parent_link_revoke))
                    }
                }
            }
        }
    }

    @Test
    fun captureParentLinkCardActive() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val link = ParentLinkDto(
            id = "link-12345678",
            parentUserId = "parent-123",
            studentId = "student-456",
            status = ParentLinkStatus.ACTIVE,
            createdAt = "2026-01-15T10:00:00Z",
            revokedAt = null
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentLinkCard(
                    link = link,
                    onRevoke = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "ParentLinkCardActive"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureParentLinkCardRevoked() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val link = ParentLinkDto(
            id = "link-87654321",
            parentUserId = "parent-123",
            studentId = "student-456",
            status = ParentLinkStatus.REVOKED,
            createdAt = "2026-01-10T10:00:00Z",
            revokedAt = "2026-01-20T15:30:00Z"
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentLinkCard(
                    link = link,
                    onRevoke = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "ParentLinkCardRevoked"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }

    @Test
    fun captureParentLinkCardPending() {
        RoborazziTestUtils.prepareAnimationsOff(composeTestRule)
        val link = ParentLinkDto(
            id = "link-11223344",
            parentUserId = "parent-123",
            studentId = "student-456",
            status = ParentLinkStatus.PENDING,
            createdAt = "2026-02-01T08:00:00Z",
            revokedAt = null
        )

        composeTestRule.setContent {
            LehrerLogTheme {
                ParentLinkCard(
                    link = link,
                    onRevoke = {},
                    isLoading = false
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(0)
        val snapshotPath = SharedTestFixtures.snapshotPath(
            SharedTestFixtures.roborazziSmokeTest,
            "ParentLinkCardPending"
        )
        RoborazziTestUtils.captureSnapshot(composeTestRule.onRoot(), snapshotPath)
    }
}
