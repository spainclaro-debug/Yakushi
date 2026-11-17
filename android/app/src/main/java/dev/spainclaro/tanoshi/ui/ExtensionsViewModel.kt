package dev.spainclaro.tanoshi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.spainclaro.tanoshi.R
import dev.spainclaro.tanoshi.extensions.CatalogExtension
import dev.spainclaro.tanoshi.extensions.ExtensionManager
import dev.spainclaro.tanoshi.extensions.InstalledExtension
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
 * Snapshot of the Sources/Extensions screen: the remote catalog, the extensions
 * currently on the device, per-operation progress and the "restart required"
 * flag. [installed] is the list Tanoshi will see after the next server start;
 * [restartRequired] becomes true when the on-disk state no longer matches the
 * running server process.
 */
data class ExtensionsUiState(
    val catalog: List<CatalogExtension> = emptyList(),
    val installed: List<InstalledExtension> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogLoaded: Boolean = false,
    val catalogError: String? = null,
    val installingName: String? = null,
    val installProgress: Float? = null,
    val removingName: String? = null,
    val installError: String? = null,
    val restartRequired: Boolean = false,
) {
    val busy: Boolean
        get() = installingName != null || removingName != null
}

/** One-shot events the app shell turns into snackbars. */
sealed interface ExtensionsEvent {
    /** An install/remove succeeded; offer a Restart server / Start server action. */
    data object RestartRequired : ExtensionsEvent

    /** Plain message snackbar (used for non-banner failures). */
    data class ShowMessage(val text: String) : ExtensionsEvent
}

/**
 * All extension work happens here so it survives tab switches: the catalog is
 * fetched once (retryable) and install/remove download from the catalog through
 * [ExtensionManager] off the main thread. The ViewModel only exposes immutable
 * UI state; the screen renders it.
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
        refreshInstalled()
        // A (re)started server reloads the plugin directory, so the on-disk list
        // matches the running process again.
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
        refreshInstalled()
        loadCatalog()
    }

    /** Loads the catalog unless it is already present; [force] retries after failure. */
    fun loadCatalog(force: Boolean = false) {
        val state = _uiState.value
        if (state.catalogLoading) return
        if (!force && state.catalogLoaded && state.catalogError == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(catalogLoading = true, catalogError = null) }
            val result = withContext(Dispatchers.IO) {
                try {
                    manager.fetchCatalog()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Result.failure(throwable)
                }
            }
            result.fold(
                onSuccess = { catalog ->
                    val installed = withContext(Dispatchers.IO) {
                        try {
                            manager.listInstalled(catalog)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Throwable) {
                            _uiState.value.installed
                        }
                    }
                    _uiState.update {
                        it.copy(
                            catalog = catalog,
                            installed = installed,
                            catalogLoading = false,
                            catalogLoaded = true,
                            catalogError = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            catalogLoading = false,
                            catalogError = error.message?.takeIf { message ->
                                message.isNotBlank()
                            } ?: string(R.string.extensions_error_generic),
                        )
                    }
                },
            )
        }
    }

    /** Re-reads the plugin directory without touching the network. */
    fun refreshInstalled() {
        viewModelScope.launch {
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
    }

    /** Downloads and installs (or replaces) the catalog entry. */
    fun install(entry: CatalogExtension) {
        val state = _uiState.value
        if (state.busy) return
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
                    _uiState.update {
                        it.copy(
                            installed = installed,
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

    /** Removes the user-installed copy; the bundled copy (if any) stays. */
    fun remove(extension: InstalledExtension) {
        val state = _uiState.value
        if (state.busy) return
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
                    _uiState.update {
                        it.copy(
                            installed = installed,
                            removingName = null,
                            restartRequired = true,
                        )
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

    fun dismissInstallError() {
        _uiState.update { it.copy(installError = null) }
    }

    private fun string(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}
