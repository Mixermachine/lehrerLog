package de.aarondietz.lehrerlog.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.aarondietz.lehrerlog.SharedTestFixtures
import de.aarondietz.lehrerlog.logging.LogOverview
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

private const val LOG_PREVIEW_MAX_LINES = 8

@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val quotaState by viewModel.quotaState.collectAsState()
    val logState by viewModel.logState.collectAsState()

    SettingsContent(
        quotaState = quotaState,
        logState = logState,
        onRefreshQuota = viewModel::loadQuota,
        onRefreshLogs = viewModel::loadLogs,
        onShareLogs = viewModel::shareLogs,
        onClearLogs = viewModel::clearLogs,
        onLogout = onLogout
    )
}

@Composable
internal fun SettingsContent(
    quotaState: StorageQuotaState,
    logState: LogUiState,
    onRefreshQuota: () -> Unit,
    onRefreshLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.md),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
    ) {
        Text(
            text = stringResource(Res.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium
        )

        StorageQuotaCard(
            quotaState = quotaState,
            onRefreshQuota = onRefreshQuota
        )

        LogsCard(
            logState = logState,
            onRefreshLogs = onRefreshLogs,
            onShareLogs = onShareLogs,
            onClearLogs = onClearLogs
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))

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
                modifier = Modifier.padding(end = MaterialTheme.spacing.sm)
            )
            Text(stringResource(Res.string.logout))
        }
    }
}

@Composable
private fun StorageQuotaCard(
    quotaState: StorageQuotaState,
    onRefreshQuota: () -> Unit
) {
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
                IconButton(onClick = onRefreshQuota) {
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
                        text = quotaState.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                quotaState.quota != null -> {
                    val quota = quotaState.quota
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

@Composable
private fun LogsCard(
    logState: LogUiState,
    onRefreshLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = MaterialTheme.spacing.xs)
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
                    text = stringResource(Res.string.logs_title),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onRefreshLogs) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.logs_refresh)
                    )
                }
            }

            Text(
                text = stringResource(Res.string.logs_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                logState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                logState.error != null -> {
                    Text(
                        text = logState.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                else -> {
                    LogsSummary(logState.overview)
                    LogsPreview(logState.overview)
                    LogsActions(
                        logState = logState,
                        onShareLogs = onShareLogs,
                        onClearLogs = onClearLogs
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsSummary(overview: LogOverview?) {
    val filesCount = overview?.files?.size ?: 0
    val totalSize = formatBytes(overview?.totalSizeBytes ?: 0L)
    val currentSize = formatBytes(overview?.currentFileSizeBytes ?: 0L)

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Text(
            text = stringResource(Res.string.logs_files_count, filesCount),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(Res.string.logs_total_size, totalSize),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(Res.string.logs_current_size, currentSize),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LogsPreview(overview: LogOverview?) {
    val previewLines = overview?.previewLines?.takeLast(LOG_PREVIEW_MAX_LINES).orEmpty()

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Text(
            text = stringResource(Res.string.logs_preview_title),
            style = MaterialTheme.typography.labelLarge
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
            ) {
                if (previewLines.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.logs_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    previewLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsActions(
    logState: LogUiState,
    onShareLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    val hasLogs = logState.overview?.files?.isNotEmpty() == true
    val actionsEnabled = !logState.isLoading && !logState.isSharing

    if (logState.isSharing) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        Button(
            onClick = onShareLogs,
            enabled = actionsEnabled,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = MaterialTheme.spacing.xs)
                    .size(MaterialTheme.spacing.md)
            )
            Text(stringResource(Res.string.logs_share))
        }
        OutlinedButton(
            onClick = onClearLogs,
            enabled = actionsEnabled && hasLogs,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = MaterialTheme.spacing.xs)
                    .size(MaterialTheme.spacing.md)
            )
            Text(stringResource(Res.string.logs_clear))
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
        bytes >= mb -> "${(bytes / mb * 10).roundToInt() / 10.0} MB"
        bytes >= kb -> "${(bytes / kb * 10).roundToInt() / 10.0} KB"
        else -> "$bytes B"
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    LehrerLogTheme {
        SettingsContent(
            quotaState = StorageQuotaState(quota = SharedTestFixtures.testStorageQuotaDto()),
            logState = LogUiState(overview = SharedTestFixtures.testLogOverview()),
            onRefreshQuota = {},
            onRefreshLogs = {},
            onShareLogs = {},
            onClearLogs = {},
            onLogout = {}
        )
    }
}
