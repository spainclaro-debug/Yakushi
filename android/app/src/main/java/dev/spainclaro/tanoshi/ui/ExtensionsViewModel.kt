package dev.spainclaro.tanoshi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.spainclaro.tanoshi.R
import dev.spainclaro.tanoshi.extensions.CatalogExtension
import dev.spainclaro.tanoshi.extensions.CatalogFetchResult
import dev.spainclaro.tanoshi.extensions.ExtensionManager
import dev.spainclaro.tanoshi.extensions.ExtensionRepository
import dev.spainclaro.tanoshi.extensions.InstalledExtension
import dev.spainclaro.tanoshi.extensions.RepositoryFailure
import dev.spainclaro.tanoshi.server.RunState
import dev.spainclaro.tanoshi.server.ServerStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Snapshot of the Sources/Extensions screen: configured repositories, the
 * merged catalog (with per-repository failures), what is installed on the
 * device, the enable/disable markers and any in-flight operation.
 *
 * [restartRequired] becomes true after any mutation (install/remove/enable/
 * disable); the server only picks the change up on its next start.
 */
data class ExtensionsUiState(
    val repositories: List<ExtensionRepository> = emptyList(),
    val repositoriesLoaded: Boolean = false,
    val catalog: List<CatalogExtension> = emptyList(),
    val repositoryFailures: List<RepositoryFailure> = emptyList(),
    val installed: List<InstalledExtension> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogLoaded: Boolean = false,
    val catalogError: String? = null,
    val installingName: String? = null,
    val installProgress: Float? = null,
    val removingName: String? = null,
    val togglingName: String? = null,
    val installError: String? = null,
    val restartRequired: Boolean = false,
) {
    val busy: Boolean
        get() = installingName != null || removingName != null || togglingName != null
}

/** One-shot events the app shell turns into snackbars. */
sealed interface ExtensionsEvent {
    /** The on-disk extensions changed; offer a Restart server / Start server action. */
    data object RestartRequired : ExtensionsEvent

    /** Plain message snackbar (repository added/removed, failures). */
    data class ShowMessage(val text: String) : ExtensionsEvent
}

/**
 * All extension work happens here so it survives tab switches: repositories are
 * loaded from [ExtensionManager], the merged catalog is fetched once
 * (retryable) and every mutation (install/update/remove/enable/disable) runs
 * off the main thread. UI state is exposed as an immutable snapshot; one-shot
 * messages go through [events].
 */
class ExtensionsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext get() = getApplication<Application>().applicationContext

    // Constructed lazily so the manager is first touched on Dispatchers.IO.
    private val manager: ExtensionManager by lazy { ExtensionManager(appContext) }

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExtensionsEvent>(Channel.BUFFERED)
    val events: Flow<ExtensionsEvent> = _events.receiveAsFlow()

    init {
        refreshRepositories()
        refreshInstalled()
        // A (re)started server reloads the plugins, so the on-disk state matches
        // the running process again and the restart banner can go away.
        viewModelScope.launch {
            ServerStateRepository.state.collect { snapshot ->
                if (snapshot.state == RunState.STARTING) {
                    _uiState.update { state ->
                        if (state.restartRequired) state.copy(restartRequired = false) else state
                    }
                }
            }
        }
    }

    /** Called whenever the Sources screen becomes visible. */
    fun onScreenVisible() {
        refreshRepositories()
        refreshInstalled()
        loadCatalog()
    }

    /** Pull-to-refresh equivalent for the header button: re-reads everything. */
    fun refreshAll() {
        viewModelScope.launch {
            refreshRepositoriesSuspend()
            loadCatalogSuspend(force = true)
        }
    }

    /** Loads the merged catalog unless already present; [force] retries failures. */
    fun loadCatalog(force: Boolean = false) {
        viewModelScope.launch { loadCatalogSuspend(force) }
    }

    fun refreshInstalled() {
        viewModelScope.launch { refreshInstalledSuspend() }
    }

    fun refreshRepositories() {
        viewModelScope.launch { refreshRepositoriesSuspend() }
    }

    /**
     * Adds a custom repository from a direct HTTPS `index.json` URL or a GitHub
     * repository link. The backend resolves/normalises it and persists it.
     * Failures are returned so the dialog can show them inline.
     */
    suspend fun addRepository(input: String): Result<Unit> {
        val result: Result<Unit> = withContext(Dispatchers.IO) {
            try {
                manager.addRepository(input.trim())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
        }.map { }
        result.fold(
            onSuccess = {
                refreshRepositoriesSuspend()
                loadCatalogSuspend(force = true)
                _events.trySend(ExtensionsEvent.ShowMessage(string(R.string.extensions_repositories_added)))
            },
            onFailure = {
                // Rendered inline in the "Add repository" dialog.
            },
        )
        return result
    }

    /** Removes a custom repository and refreshes the catalog around it. */
    fun removeRepository(repository: ExtensionRepository) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    manager.removeRepository(repository.catalogUrl)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Result.failure(throwable)
                }
            }
            result.fold(
                onSuccess = {
                    refreshRepositoriesSuspend()
                    loadCatalogSuspend(force = true)
                    _events.trySend(
                        ExtensionsEvent.ShowMessage(string(R.string.extensions_repositories_removed)),
                    )
                },
                onFailure = { error ->
                    _events.trySend(
                        ExtensionsEvent.ShowMessage(
                            string(
                                R.string.extensions_repositories_remove_failed,
                                error.message ?: string(R.string.extensions_error_generic),
                            ),
                        ),
                    )
                },
            )
        }
    }

    /** Downloads and installs (or replaces) the catalog entry; clears disabled. */
    fun install(entry: CatalogExtension) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    installingName = entry.name,
                    installProgress = 0f,
                    installError = null,
                )
            }
            val result = withContext(Dispatchers.IO) {
                try {
                    manager.install(entry) { downloaded, total ->
                        val progress = if (total > 0L) {
                            (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        _uiState.update { current -> current.copy(installProgress = progress) }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Result.failure(throwable)
                }
            }
            result.fold(
                onSuccess = {
                    refreshInstalledSuspend()
                    _uiState.update {
                        it.copy(
                            installingName = null,
                            installProgress = null,
                            restartRequired = true,
                        )
                    }
                    _events.trySend(ExtensionsEvent.RestartRequired)
                },
                onFailure = { error ->
                    val message = string(
                        R.string.extensions_install_failed,
                        entry.displayName,
                        error.message ?: string(R.string.extensions_error_generic),
                    )
                    _uiState.update {
                        it.copy(
                            installingName = null,
                            installProgress = null,
                            installError = message,
                        )
                    }
                },
            )
        }
    }

    /** Removes the user-installed copy; bundled copies are only disabled. */
    fun remove(extension: InstalledExtension) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(removingName = extension.name, installError = null) }
            val result = withContext(Dispatchers.IO) {
                try {
                    manager.remove(extension.name)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Result.failure(throwable)
                }
            }
            result.fold(
                onSuccess = {
                    refreshInstalledSuspend()
                    _uiState.update {
                        it.copy(removingName = null, restartRequired = true)
                    }
                    _events.trySend(ExtensionsEvent.RestartRequired)
                },
                onFailure = { error ->
                    val message = string(
                        R.string.extensions_remove_failed,
                        extension.displayName,
                        error.message ?: string(R.string.extensions_error_generic),
                    )
                    _uiState.update {
                        it.copy(removingName = null, installError = message)
                    }
                },
            )
        }
    }

    /**
     * Enables/disables an extension without deleting it (the backend writes or
     * removes a marker for bundled and user copies alike).
     */
    fun setExtensionEnabled(extension: InstalledExtension, enabled: Boolean) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(togglingName = extension.name, installError = null) }
            val result = withContext(Dispatchers.IO) {
                try {
                    manager.setExtensionEnabled(extension.name, enabled)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Result.failure(throwable)
                }
            }
            result.fold(
                onSuccess = {
                    refreshInstalledSuspend()
                    _uiState.update {
                        it.copy(togglingName = null, restartRequired = true)
                    }
                    _events.trySend(ExtensionsEvent.RestartRequired)
                },
                onFailure = { error ->
                    val message = string(
                        R.string.extensions_toggle_failed,
                        extension.displayName,
                        error.message ?: string(R.string.extensions_error_generic),
                    )
                    _uiState.update {
                        it.copy(togglingName = null, installError = message)
                    }
                },
            )
        }
    }

    fun dismissInstallError() {
        _uiState.update { it.copy(installError = null) }
    }

    // --- Internals ---------------------------------------------------------

    private suspend fun refreshRepositoriesSuspend() {
        val repositories = withContext(Dispatchers.IO) {
            try {
                manager.repositories()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _uiState.value.repositories
            }
        }
        _uiState.update { it.copy(repositories = repositories, repositoriesLoaded = true) }
    }

    private suspend fun refreshInstalledSuspend() {
        val catalog = _uiState.value.catalog
        val installed = withContext(Dispatchers.IO) {
            try {
                manager.listInstalled(catalog)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _uiState.value.installed
            }
        }
        _uiState.update { it.copy(installed = installed) }
    }

    private suspend fun loadCatalogSuspend(force: Boolean) {
        val snapshot = _uiState.value
        if (snapshot.catalogLoading) return
        if (!force && snapshot.catalogLoaded) return
        _uiState.update { it.copy(catalogLoading = true, catalogError = null) }
        val fetched: CatalogFetchResult? = withContext(Dispatchers.IO) {
            try {
                manager.fetchCatalogs()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                null
            }
        }
        if (fetched == null) {
            _uiState.update {
                it.copy(catalogLoading = false, catalogError = string(R.string.extensions_error_generic))
            }
            return
        }
        val installed = withContext(Dispatchers.IO) {
            try {
                manager.listInstalled(fetched.entries)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _uiState.value.installed
            }
        }
        _uiState.update {
            it.copy(
                catalog = fetched.entries,
                repositoryFailures = fetched.failures,
                installed = installed,
                catalogLoading = false,
                catalogLoaded = true,
                catalogError = null,
            )
        }
    }

    private fun string(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}
