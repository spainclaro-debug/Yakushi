package dev.spainclaro.yashi

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    store: AddressStore,
    theme: YashiThemeMode,
    onThemeChange: (YashiThemeMode) -> Unit,
    onOpenServer: (ServerAddress) -> Unit,
) {
    val context = LocalContext.current
    val history by store.history.collectAsStateWithLifecycle()
    val saved by store.saved.collectAsStateWithLifecycle()

    var addressInput by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<ServerAddress?>(null) }
    var editingText by remember(editing) { mutableStateOf(editing?.url ?: "") }
    var editingError by remember(editing) { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var lanOpen by remember { mutableStateOf(false) }
    var lanScanning by remember { mutableStateOf(false) }
    var lanScanResult by remember { mutableStateOf<LanScanResult?>(null) }
    var lanScanRequest by remember { mutableIntStateOf(0) }
    var pendingQr by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun connect(address: ServerAddress) {
        onOpenServer(address)
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            val parsed = AddressParser.parse(contents)
            if (parsed != null) {
                connect(parsed)
            } else {
                toast("That QR code does not contain a valid server address")
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        store.cameraAsked = true
        if (granted && pendingQr) {
            pendingQr = false
            launchQrScanner(scanLauncher)
        } else if (!granted) {
            pendingQr = false
            toast("Camera permission is needed to scan the server QR code")
        }
    }

    fun openQrScanner() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchQrScanner(scanLauncher)
        } else {
            pendingQr = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Camera permission is requested on first launch (the QR scanner needs it).
    LaunchedEffect(Unit) {
        if (!store.cameraAsked) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                store.cameraAsked = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    LaunchedEffect(lanOpen, lanScanRequest) {
        if (lanOpen) {
            lanScanning = true
            lanScanResult = null
            lanScanResult = LanScanner.scan(
                context = context,
                preferredPorts = (saved + history).map { it.port },
            )
            lanScanning = false
        }
    }

    fun goToTypedAddress() {
        val parsed = AddressParser.parse(addressInput)
        if (parsed == null) {
            toast("Enter a server address like 192.168.1.20:8080")
        } else {
            connect(parsed)
        }
    }

    Scaffold(
        // Insets are handled explicitly below: the content gets safe-drawing
        // top/horizontal padding, and the bottom bar handles navigation-bar
        // padding. Zeroing Scaffold's own insets avoids double spacing.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::openQrScanner) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan QR code")
                    }
                    IconButton(onClick = { lanOpen = true }) {
                        Icon(Icons.Outlined.WifiFind, contentDescription = "Scan local network")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Only reserve the system status-bar height when it is
                // actually shown. When the phone's fullscreen/cutout handling
                // hides the status bar (e.g. "Show cutout"), this inset is
                // zero and the content uses the full display.
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Yashi",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Connect to your Yakushi server",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Server address") },
                    placeholder = { Text("192.168.1.20:8080") },
                    leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { goToTypedAddress() }),
                )
                Button(onClick = { goToTypedAddress() }) {
                    Text("Go")
                }
            }

            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                item { SectionTitle("History") }
                if (history.isEmpty()) {
                    item {
                        EmptyHint("No connections yet. Enter an address, scan the server QR code, or scan your network.")
                    }
                } else {
                    items(history, key = { it.url }) { address ->
                        HistoryRow(
                            address = address,
                            isSaved = store.isSaved(address),
                            onOpen = { connect(address) },
                            onSave = {
                                if (store.save(address)) {
                                    toast("Saved ${address.display}")
                                } else {
                                    toast("Already saved")
                                }
                            },
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("Saved servers")
                }
                if (saved.isEmpty()) {
                    item {
                        EmptyHint("No saved servers. Save one from the history list above.")
                    }
                } else {
                    items(saved, key = { it.url }) { address ->
                        SavedRow(
                            address = address,
                            onOpen = { connect(address) },
                            onEdit = { editing = address },
                            onDelete = {
                                store.removeSaved(address)
                                toast("Removed ${address.display}")
                            },
                        )
                    }
                }
            }
        }
    }

    if (editing != null) {
        val current = editing
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit saved server") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = {
                            editingText = it
                            editingError = false
                        },
                        singleLine = true,
                        isError = editingError,
                        label = { Text("Address") },
                        placeholder = { Text("192.168.1.20:8080") },
                    )
                    if (editingError) {
                        Text(
                            text = "Enter a valid address, e.g. 192.168.1.20:8080",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = AddressParser.parse(editingText)
                    if (parsed == null) {
                        editingError = true
                    } else if (current != null && store.updateSaved(current, parsed)) {
                        editing = null
                        toast("Saved ${parsed.display}")
                    } else {
                        toast("That server is already saved")
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Cancel") }
            },
        )
    }

    if (settingsOpen) {
        SettingsSheet(
            theme = theme,
            onThemeChange = onThemeChange,
            onDismiss = { settingsOpen = false },
            onClearData = {
                settingsOpen = false
                clearConfirm = true
            },
        )
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("Clear data?") },
            text = {
                Text("This removes all saved servers and the connection history from this device. The theme setting is kept.")
            },
            confirmButton = {
                TextButton(onClick = {
                    store.clearConnectionData()
                    clearConfirm = false
                    toast("Saved servers and history cleared")
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (lanOpen) {
        AlertDialog(
            onDismissRequest = { lanOpen = false },
            title = { Text("Local network") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (lanScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Scanning your network…")
                        }
                    } else if (lanScanResult == null || lanScanResult?.servers.isNullOrEmpty()) {
                        val result = lanScanResult
                        Text(
                            "No Yakushi servers found" +
                                (result?.localIp?.let { " on $it" } ?: "") +
                                (result?.ports?.takeIf { it.isNotEmpty() }?.let { " (ports ${it.joinToString()})" } ?: "") +
                                ". Make sure Yakushi is running, LAN mode is enabled, and both devices are on the same network.",
                        )
                    } else {
                        val result = lanScanResult
                        val servers = result?.servers.orEmpty()
                        Text("Found ${servers.size} server(s). Tap one to connect.")
                        servers.forEach { address ->
                            Surface(
                                onClick = {
                                    lanOpen = false
                                    connect(address)
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Dns, contentDescription = null)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = address.display,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { lanOpen = false }) { Text("Close") }
            },
            dismissButton = {
                if (!lanScanning) {
                    TextButton(onClick = { lanScanRequest++ }) { Text("Scan again") }
                }
            },
        )
    }
}

private fun launchQrScanner(launcher: androidx.activity.result.ActivityResultLauncher<ScanOptions>) {
    launcher.launch(
        ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan the Yakushi server QR code")
            setBeepEnabled(false)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun HistoryRow(
    address: ServerAddress,
    isSaved: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    AddressRow(
        address = address,
        onOpen = onOpen,
        trailing = {
            IconButton(onClick = onSave, enabled = !isSaved) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (isSaved) "Already saved" else "Save server",
                )
            }
        },
    )
}

@Composable
private fun SavedRow(
    address: ServerAddress,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AddressRow(
        address = address,
        onOpen = onOpen,
        trailing = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit saved server")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete saved server")
                }
            }
        },
    )
}

@Composable
private fun AddressRow(
    address: ServerAddress,
    onOpen: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Dns, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = address.display,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = address.scheme,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    theme: YashiThemeMode,
    onThemeChange: (YashiThemeMode) -> Unit,
    onDismiss: () -> Unit,
    onClearData: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            YashiThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = theme == mode,
                        onClick = { onThemeChange(mode) },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when (mode) {
                            YashiThemeMode.SYSTEM -> "System"
                            YashiThemeMode.DARK -> "Dark"
                            YashiThemeMode.LIGHT -> "Light"
                        },
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = "Data",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(
                onClick = onClearData,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Clear saved servers & history")
            }
            Text(
                text = "Removes all saved IP addresses/ports and the connection history from this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
