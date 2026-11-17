package dev.spainclaro.tanoshi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.spainclaro.tanoshi.R
import dev.spainclaro.tanoshi.extensions.CatalogExtension
import dev.spainclaro.tanoshi.extensions.InstalledExtension
import dev.spainclaro.tanoshi.ui.ExtensionsUiState
import dev.spainclaro.tanoshi.ui.ExtensionsViewModel
import java.util.Locale

/**
 * Sources / Extensions screen (Suwayomi-style "Add extension" flow).
 *
 * Shows what is installed (bundled + user copies), lets the user install or
 * update extensions from the repository catalog and remove user copies, and
 * makes it explicit that the server has to be restarted before Tanoshi sees the
 * new sources. All network/disk work is done by [ExtensionsViewModel] on
 * [kotlinx.coroutines.Dispatchers.IO]; this file is UI only.
 */
@Composable
fun ExtensionsScreen(
    viewModel: ExtensionsViewModel,
    serverRunning: Boolean,
    onStartServer: () -> Unit,
    onRestartServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addDialogVisible by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<InstalledExtension?>(null) }

    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.extensions_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.extensions_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.extensions_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.restartRequired) {
            item {
                RestartRequiredBanner(
                    serverRunning = serverRunning,
                    onStartServer = onStartServer,
                    onRestartServer = onRestartServer,
                )
            }
        }

        val installError = state.installError
        if (installError != null) {
            item {
                ErrorBanner(
                    message = installError,
                    onDismiss = viewModel::dismissInstallError,
                )
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.extensions_section_installed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.extensions_installed_count,
                        state.installed.size,
                        state.installed.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.installed.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.extensions_installed_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        } else {
            items(items = state.installed, key = { extension -> extension.name }) { extension ->
                InstalledExtensionCard(
                    extension = extension,
                    busy = state.busy,
                    installing = state.installingName == extension.name,
                    installProgress = if (state.installingName == extension.name) {
                        state.installProgress
                    } else {
                        null
                    },
                    removing = state.removingName == extension.name,
                    onInstall = viewModel::install,
                    onRemove = { pendingRemoval = extension },
                )
            }
        }

        item {
            OutlinedButton(
                onClick = { addDialogVisible = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.extensions_add))
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
        }
    }

    if (addDialogVisible) {
        AddExtensionDialog(
            state = state,
            onDismiss = { addDialogVisible = false },
            onRetry = { viewModel.loadCatalog(force = true) },
            onInstall = viewModel::install,
        )
    }

    val removal = pendingRemoval
    if (removal != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.extensions_remove_title)) },
            text = { Text(stringResource(R.string.extensions_remove_body, removal.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.remove(removal)
                        pendingRemoval = null
                    },
                    enabled = !state.busy,
                ) {
                    Text(
                        text = stringResource(R.string.extensions_remove_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.extensions_remove_cancel))
                }
            },
        )
    }
}

@Composable
private fun InstalledExtensionCard(
    extension: InstalledExtension,
    busy: Boolean,
    installing: Boolean,
    installProgress: Float?,
    removing: Boolean,
    onInstall: (CatalogExtension) -> Unit,
    onRemove: () -> Unit,
) {
    val catalog = extension.catalog
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = extension.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val description = catalog?.description
                    if (description != null && description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (catalog != null) {
                    Text(
                        text = formatSize(catalog.sizeBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExtensionBadge(label = installationBadge(extension))
                if (catalog?.ported == true) {
                    ExtensionBadge(label = stringResource(R.string.extensions_badge_ported))
                }
                if (extension.updateAvailable) {
                    ExtensionBadge(
                        label = stringResource(R.string.extensions_badge_update),
                        highlighted = true,
                    )
                }
            }

            if (extension.bundled && !extension.userInstalled) {
                Text(
                    text = stringResource(R.string.extensions_bundled_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (installing) {
                InstallProgress(progress = installProgress)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (catalog != null) {
                        OutlinedButton(
                            onClick = { onInstall(catalog) },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (extension.updateAvailable) {
                                        R.string.extensions_action_update
                                    } else {
                                        R.string.extensions_action_reinstall
                                    },
                                ),
                            )
                        }
                    }
                    if (extension.userInstalled) {
                        TextButton(onClick = onRemove, enabled = !busy) {
                            Text(
                                text = stringResource(R.string.extensions_action_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (removing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddExtensionDialog(
    state: ExtensionsUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onInstall: (CatalogExtension) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extensions_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.extensions_add_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    state.catalogLoading && state.catalog.isEmpty() -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.extensions_add_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    state.catalog.isEmpty() -> {
                        val error = state.catalogError
                        if (error != null) {
                            Text(
                                text = stringResource(R.string.extensions_add_error, error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(
                                onClick = onRetry,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.extensions_add_retry))
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.extensions_add_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items = state.catalog, key = { entry -> entry.name }) { entry ->
                            val installed = state.installed.firstOrNull { it.name == entry.name }
                            CatalogExtensionRow(
                                entry = entry,
                                installed = installed,
                                installing = state.installingName == entry.name,
                                installProgress = if (state.installingName == entry.name) {
                                    state.installProgress
                                } else {
                                    null
                                },
                                busy = state.busy,
                                onInstall = { onInstall(entry) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.extensions_add_close))
            }
        },
    )
}

@Composable
private fun CatalogExtensionRow(
    entry: CatalogExtension,
    installed: InstalledExtension?,
    installing: Boolean,
    installProgress: Float?,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val description = entry.description
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatSize(entry.sizeBytes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.ported) {
                ExtensionBadge(label = stringResource(R.string.extensions_badge_ported))
            }
            if (installed?.updateAvailable == true) {
                ExtensionBadge(
                    label = stringResource(R.string.extensions_badge_update),
                    highlighted = true,
                )
            }
            Spacer(Modifier.weight(1f))
            when {
                installing -> InstallProgress(progress = installProgress, compact = true)
                installed != null && installed.userInstalled && !installed.updateAvailable -> Text(
                    text = stringResource(R.string.extensions_action_installed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Button(
                    onClick = onInstall,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(catalogActionLabel(installed)))
                }
            }
        }
        if (installing) {
            InstallProgressBar(progress = installProgress)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun InstallProgress(progress: Float?, compact: Boolean = false) {
    if (compact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = progressLabel(progress),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InstallProgressBar(progress = progress)
            Text(
                text = progressLabel(progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InstallProgressBar(progress: Float?) {
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun progressLabel(progress: Float?): String = if (progress != null) {
    stringResource(R.string.extensions_progress, (progress * 100f).toInt())
} else {
    stringResource(R.string.extensions_installing)
}

/** Label of the install button in the catalog list, based on the local state. */
private fun catalogActionLabel(installed: InstalledExtension?): Int = when {
    installed == null -> R.string.extensions_action_install
    installed.updateAvailable -> R.string.extensions_action_update
    else -> R.string.extensions_action_reinstall
}

/** Badge that states where the extension currently comes from. */
@Composable
private fun installationBadge(extension: InstalledExtension): String = when {
    extension.bundled && extension.userInstalled -> stringResource(R.string.extensions_badge_override)
    extension.userInstalled -> stringResource(R.string.extensions_badge_user)
    else -> stringResource(R.string.extensions_badge_bundled)
}

@Composable
private fun ExtensionBadge(
    label: String,
    highlighted: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun RestartRequiredBanner(
    serverRunning: Boolean,
    onStartServer: () -> Unit,
    onRestartServer: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.extensions_restart_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.extensions_restart_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (serverRunning) {
                    Button(onClick = onRestartServer, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.extensions_restart_now))
                    }
                } else {
                    Button(onClick = onStartServer, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.extensions_start_now))
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.extensions_dismiss),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "?"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb.toLong() -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb.toLong() -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb.toLong() -> String.format(Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
