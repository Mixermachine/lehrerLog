package de.aarondietz.lehrerlog.ui.screens.parent_management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import de.aarondietz.lehrerlog.data.ParentLinkDto
import de.aarondietz.lehrerlog.data.ParentLinkStatus
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ParentInviteManagementScreen(
    studentId: String,
    studentName: String,
    onBack: () -> Unit,
    viewModel: ParentInviteManagementViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(studentId) {
        viewModel.loadParentLinks(studentId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
    ) {
        // Header
        Text(
            text = stringResource(Res.string.parent_invite_management_title, studentName),
            style = MaterialTheme.typography.headlineMedium
        )

        // Generate Invite Button
        Button(
            onClick = { viewModel.generateInvite(studentId) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        ) {
            Text(stringResource(Res.string.parent_invite_generate))
        }

        // Show generated invite code
        state.inviteResponse?.let { invite ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(MaterialTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                ) {
                    Text(
                        text = stringResource(Res.string.parent_invite_code_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = invite.code,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(invite.code))
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(Res.string.parent_invite_copy)
                            )
                        }
                    }
                    Text(
                        text = stringResource(Res.string.parent_invite_expires, invite.invite.expiresAt.substringBefore("T")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { viewModel.clearInvite() }) {
                        Text(stringResource(Res.string.action_dismiss))
                    }
                }
            }
        }

        // Error display
        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Loading indicator
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        // Parent Links List
        Text(
            text = stringResource(Res.string.parent_links_title),
            style = MaterialTheme.typography.titleMedium
        )

        if (state.parentLinks.isEmpty() && !state.isLoading) {
            Text(
                text = stringResource(Res.string.parent_links_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                items(state.parentLinks) { link ->
                    ParentLinkCard(
                        link = link,
                        onRevoke = { viewModel.revokeLink(link.id, studentId) },
                        isLoading = state.isLoading
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Back button
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.action_back))
        }
    }
}

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

@Preview
@Composable
private fun ParentInviteManagementScreenPreview() {
    LehrerLogTheme {
        ParentInviteManagementScreen(
            studentId = "student-123",
            studentName = "Max Mustermann",
            onBack = {}
        )
    }
}
