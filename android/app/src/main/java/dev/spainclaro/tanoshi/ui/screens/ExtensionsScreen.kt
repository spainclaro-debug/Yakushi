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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.spainclaro.tanoshi.R
import dev.spainclaro.tanoshi.extensions.CatalogExtension
import dev.spainclaro.tanoshi.extensions.ExtensionRepository
import dev.spainclaro.tanoshi.extensions.InstalledExtension
import dev.spainclaro.tanoshi.extensions.RepositoryFailure
import dev.spainclaro.tanoshi.ui.ExtensionsUiState
import dev.spainclaro.tanoshi.ui.ExtensionsViewModel
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Sources / Extensions screen (Suwayomi-style extension manager).
 *
 * It manages extension repositories (default + custom), lists the merged
 * catalog from all of them with per-repository failure reporting, and lets the
 * user install/update/remove/enable/disable extensions. Every mutation only
 * takes effect after a server restart, so all of them raise the shared restart
 * banner/snackbar. Network/disk work is done by [ExtensionsViewModel] off the
 * main thread; this file is UI only.
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
    var addRepositoryVisible by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<InstalledExtension?>(null) }
    var pendingRepositoryRemoval by remember { mutableStateOf<ExtensionRepository?>(null) }

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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
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
                IconButton(
                    onClick = { viewModel.refreshAll() },
                    enabled = !state.catalogLoading,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.extensions_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

        if (state.catalogLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
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
                    text = stringResource(R.string.extensions_section_repositories),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { addRepositoryVisible = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.extensions_repositories_add))
                }
            }
        }

        item {
            RepositoriesCard(
                repositories = state.repositories,
                loaded = state.repositoriesLoaded,
                onRemove = { repository -> pendingRepositoryRemoval = repository },
            )
        }

        if (state.repositoryFailures.isNotEmpty()) {
            item {
                RepositoryFailuresCard(
                    failures = state.repositoryFailures,
                    onRetry = { viewModel.loadCatalog(force = true) },
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
                    toggling = state.togglingName == extension.name,
                    onInstall = viewModel::install,
                    onRemove = { pendingRemoval = extension },
                    onSetEnabled = { enabled ->
                        viewModel.setExtensionEnabled(extension, enabled)
                    },
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

    if (addRepositoryVisible) {
        AddRepositoryDialog(
            viewModel = viewModel,
            onDismiss = { addRepositoryVisible = false },
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

    val repositoryRemoval = pendingRepositoryRemoval
    if (repositoryRemoval != null) {
        AlertDialog(
            onDismissRequest = { pendingRepositoryRemoval = null },
            title = { Text(stringResource(R.string.extensions_repositories_remove_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.extensions_repositories_remove_body,
                        repositoryRemoval.label,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeRepository(repositoryRemoval)
                        pendingRepositoryRemoval = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.extensions_repositories_remove_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRepositoryRemoval = null }) {
                    Text(stringResource(R.string.extensions_repositories_remove_cancel))
                }
            },
        )
    }
}

@Composable
private fun RepositoriesCard(
    repositories: List<ExtensionRepository>,
    loaded: Boolean,
    onRemove: (ExtensionRepository) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        if (repositories.isEmpty()) {
            Text(
                text = stringResource(
                    if (loaded) {
                        R.string.extensions_repositories_empty
                    } else {
                        R.string.extensions_repositories_loading
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                repositories.forEachIndexed { index, repository ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = repository.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (repository.isDefault) {
                                    Spacer(Modifier.width(8.dp))
                                    ExtensionBadge(
                                        label = stringResource(
                                            R.string.extensions_repositories_default,
                                        ),
                                    )
                                }
                            }
                            Text(
                                text = repository.catalogUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!repository.isDefault) {
                            TextButton(onClick = { onRemove(repository) }) {
                                Text(
                                    text = stringResource(
                                        R.string.extensions_repositories_remove,
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (index != repositories.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepositoryFailuresCard(
    failures: List<RepositoryFailure>,
    onRetry: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.extensions_failures_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            failures.forEach { failure ->
                Text(
                    text = "${failure.repository.label}: ${failure.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.extensions_failures_retry),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun InstalledExtensionCard(
    extension: InstalledExtension,
    busy: Boolean,
    installing: Boolean,
    installProgress: Float?,
    removing: Boolean,
    toggling: Boolean,
    onInstall: (CatalogExtension) -> Unit,
    onRemove: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
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
                if (catalog?.repoLabel?.isNotBlank() == true) {
                    ExtensionBadge(label = catalog.repoLabel)
                }
                if (catalog?.ported == true) {
                    ExtensionBadge(label = stringResource(R.string.extensions_badge_ported))
                }
                if (extension.updateAvailable) {
                    ExtensionBadge(
                        label = stringResource(R.string.extensions_badge_update),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (extension.disabled) {
                    ExtensionBadge(
                        label = stringResource(R.string.extensions_badge_disabled),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
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
                    TextButton(
                        onClick = { onSetEnabled(extension.disabled) },
                        enabled = !busy,
                    ) {
                        Text(
                            stringResource(
                                if (extension.disabled) {
                                    R.string.extensions_action_enable
                                } else {
                                    R.string.extensions_action_disable
                                },
                            ),
                        )
                    }
                    if (extension.userInstalled) {
                        TextButton(onClick = onRemove, enabled = !busy) {
                            Text(
                                text = stringResource(R.string.extensions_action_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (removing || toggling) {
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
                        if (state.repositoryFailures.isNotEmpty()) {
                            RepositoryFailuresCard(
                                failures = state.repositoryFailures,
                                onRetry = onRetry,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
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
                        if (state.repositoryFailures.isNotEmpty()) {
                            item {
                                RepositoryFailuresCard(
                                    failures = state.repositoryFailures,
                                    onRetry = onRetry,
                                )
                            }
                        }
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
            // Badges are stacked vertically so the long repository label and the
            // "Ported to Rust" badge do not squeeze the Install/Update button on
            // narrow dialogs.
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (entry.repoLabel.isNotBlank()) {
                    ExtensionBadge(label = entry.repoLabel)
                }
                if (entry.ported) {
                    ExtensionBadge(label = stringResource(R.string.extensions_badge_ported))
                }
                if (installed?.disabled == true) {
                    ExtensionBadge(
                        label = stringResource(R.string.extensions_badge_disabled),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (installed?.updateAvailable == true) {
                    ExtensionBadge(
                        label = stringResource(R.string.extensions_badge_update),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            when {
                installing -> InstallProgress(progress = installProgress, compact = true)

                installed != null && installed.userInstalled &&
                    !installed.updateAvailable && !installed.disabled -> Text(
                    text = stringResource(R.string.extensions_action_installed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )

                else -> Button(
                    onClick = onInstall,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.widthIn(min = 96.dp),
                ) {
                    Text(
                        text = stringResource(catalogActionLabel(installed)),
                        maxLines = 1,
                        softWrap = false,
                    )
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
private fun AddRepositoryDialog(
    viewModel: ExtensionsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val trimmed = input.trim()
    val looksLikeUrl = trimmed.isEmpty() || trimmed.startsWith("https://", ignoreCase = true)

    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text(stringResource(R.string.extensions_repositories_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value
                        error = null
                    },
                    label = { Text(stringResource(R.string.extensions_repositories_add_input)) },
                    placeholder = { Text("https://github.com/owner/repo") },
                    singleLine = true,
                    enabled = !adding,
                    isError = !looksLikeUrl,
                    supportingText = {
                        Text(stringResource(R.string.extensions_repositories_add_helper))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!looksLikeUrl) {
                    Text(
                        text = stringResource(R.string.extensions_repositories_add_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val currentError = error
                if (currentError != null) {
                    Text(
                        text = currentError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (adding) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.extensions_repositories_adding),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (trimmed.isEmpty()) {
                        error = context.getString(R.string.extensions_repositories_add_empty)
                        return@TextButton
                    }
                    adding = true
                    error = null
                    scope.launch {
                        viewModel.addRepository(trimmed).fold(
                            onSuccess = {
                                adding = false
                                onDismiss()
                            },
                            onFailure = { failure ->
                                adding = false
                                error = failure.message
                                    ?: context.getString(R.string.extensions_error_generic)
                            },
                        )
                    }
                },
                enabled = !adding && trimmed.isNotEmpty() && looksLikeUrl,
            ) {
                Text(stringResource(R.string.extensions_repositories_add_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !adding) {
                Text(stringResource(R.string.extensions_repositories_add_cancel))
            }
        },
    )
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
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
