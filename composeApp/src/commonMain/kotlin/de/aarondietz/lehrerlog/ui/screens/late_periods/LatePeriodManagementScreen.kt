package de.aarondietz.lehrerlog.ui.screens.late_periods

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.aarondietz.lehrerlog.data.LatePeriodDto
import de.aarondietz.lehrerlog.ui.theme.LehrerLogTheme
import de.aarondietz.lehrerlog.ui.theme.spacing
import lehrerlog.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LatePeriodManagementScreen(
    viewModel: LatePeriodManagementViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPeriod by remember { mutableStateOf<LatePeriodDto?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.late_periods_title),
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.late_period_create))
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

        // Periods List
        if (state.periods.isEmpty() && !state.isLoading) {
            Text(
                text = stringResource(Res.string.late_periods_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                items(state.periods) { period ->
                    LatePeriodCard(
                        period = period,
                        onEdit = { editingPeriod = it },
                        onActivate = { viewModel.activatePeriod(it.id) },
                        onRecalculate = { viewModel.recalculatePeriod(it.id) },
                        isLoading = state.isLoading
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateLatePeriodDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, startsAt, endsAt ->
                viewModel.createPeriod(name, startsAt, endsAt)
                showCreateDialog = false
            }
        )
    }

    editingPeriod?.let { period ->
        EditLatePeriodDialog(
            period = period,
            onDismiss = { editingPeriod = null },
            onUpdate = { name, startsAt, endsAt ->
                viewModel.updatePeriod(period.id, name, startsAt, endsAt)
                editingPeriod = null
            }
        )
    }
}

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

@Preview
@Composable
private fun LatePeriodManagementScreenPreview() {
    LehrerLogTheme {
        LatePeriodManagementScreen()
    }
}
