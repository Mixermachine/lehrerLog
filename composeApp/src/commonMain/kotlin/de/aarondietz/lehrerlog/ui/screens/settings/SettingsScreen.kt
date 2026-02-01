package de.aarondietz.lehrerlog.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.Res
import lehrerlog.composeapp.generated.resources.logout
import lehrerlog.composeapp.generated.resources.storage_quota_title
import lehrerlog.composeapp.generated.resources.storage_quota_used
import lehrerlog.composeapp.generated.resources.storage_quota_remaining
import lehrerlog.composeapp.generated.resources.storage_quota_warning
import lehrerlog.composeapp.generated.resources.storage_quota_exceeded
import lehrerlog.composeapp.generated.resources.storage_quota_refresh
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val quotaState by viewModel.quotaState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.md),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Settings content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )

            // Storage Quota Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.storage_quota_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { viewModel.loadQuota() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(Res.string.storage_quota_refresh)
                            )
                        }
                    }

                    when {
                        quotaState.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        quotaState.error != null -> {
                            Text(
                                text = quotaState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        quotaState.quota != null -> {
                            val quota = quotaState.quota!!
                            val usedPercent = if (quota.maxTotalBytes > 0) {
                                (quota.usedTotalBytes.toFloat() / quota.maxTotalBytes * 100).roundToInt()
                            } else 0
                            val usedMB = quota.usedTotalBytes / (1024 * 1024)
                            val maxMB = quota.maxTotalBytes / (1024 * 1024)
                            val remainingMB = quota.remainingBytes / (1024 * 1024)

                            LinearProgressIndicator(
                                progress = { usedPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp),
                                color = when {
                                    usedPercent >= 100 -> MaterialTheme.colorScheme.error
                                    usedPercent >= 90 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )

                            Text(
                                text = stringResource(Res.string.storage_quota_used, usedMB, maxMB, usedPercent),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(Res.string.storage_quota_remaining, remainingMB),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (usedPercent >= 100) {
                                Text(
                                    text = stringResource(Res.string.storage_quota_exceeded),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (usedPercent >= 90) {
                                Text(
                                    text = stringResource(Res.string.storage_quota_warning),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Logout button at the bottom
        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MaterialTheme.spacing.md),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(stringResource(Res.string.logout))
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    LehrerLogTheme {
        SettingsScreen(onLogout = {})
    }
}
