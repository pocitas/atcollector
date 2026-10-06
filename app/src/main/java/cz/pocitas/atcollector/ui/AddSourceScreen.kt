package cz.pocitas.atcollector.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.pocitas.atcollector.BleDevice
import cz.pocitas.atcollector.BleBluetoothState
import cz.pocitas.atcollector.R
import cz.pocitas.atcollector.model.BleSourceConfig
import cz.pocitas.atcollector.model.HttpsSourceConfig
import cz.pocitas.atcollector.model.SourceConfig
import cz.pocitas.atcollector.model.SourceType
import cz.pocitas.atcollector.model.TcpSourceConfig
import cz.pocitas.atcollector.source.BlePermissions
import cz.pocitas.atcollector.ui.theme.AtcollectorTheme
import java.util.UUID

/** Adds a new source, or edits [initial] when given. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceScreen(
    initial: SourceConfig?,
    devices: List<BleDevice>,
    isScanning: Boolean,
    bluetoothState: BleBluetoothState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onBlePermissionsGranted: () -> Unit,
    onBluetoothEnableResult: () -> Unit,
    onSave: (SourceConfig) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf(initial?.type ?: SourceType.TCP) }
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var host by rememberSaveable {
        mutableStateOf((initial as? TcpSourceConfig)?.host ?: TcpSourceConfig.DEFAULT_HOST)
    }
    var port by rememberSaveable { mutableStateOf((initial as? TcpSourceConfig)?.port?.toString() ?: "") }
    var url by rememberSaveable { mutableStateOf((initial as? HttpsSourceConfig)?.url ?: "https://") }
    var pollSeconds by rememberSaveable {
        mutableStateOf((initial as? HttpsSourceConfig)?.pollSeconds?.toString() ?: "5")
    }
    var bleAddress by rememberSaveable { mutableStateOf((initial as? BleSourceConfig)?.address) }
    var bleName by rememberSaveable { mutableStateOf((initial as? BleSourceConfig)?.deviceName) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            onBlePermissionsGranted()
            onStartScan()
        }
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        onBluetoothEnableResult()
    }

    fun scan() {
        if (BlePermissions.granted(context)) onStartScan()
        else permissionLauncher.launch(BlePermissions.required())
    }

    val scanWanted = type == SourceType.BLE && initial == null
    LaunchedEffect(scanWanted) {
        if (scanWanted) scan() else onStopScan()
    }
    DisposableEffect(Unit) { onDispose(onStopScan) }
    BackHandler(onBack = onCancel)

    val portValue = port.toIntOrNull()?.takeIf { it in 1..65535 }
    val pollValue = pollSeconds.toIntOrNull()?.takeIf { it >= 1 }
    val urlValid = url.trim().startsWith("https://") && url.trim().length > "https://".length
    val valid = when (type) {
        SourceType.TCP -> host.isNotBlank() && portValue != null
        SourceType.HTTPS -> urlValid && pollValue != null
        SourceType.BLE -> bleAddress != null
        SourceType.WIFI -> false
    }

    fun defaultName() = when (type) {
        SourceType.TCP -> "${host.trim()}:$port"
        SourceType.HTTPS -> url.trim().removePrefix("https://").substringBefore('/')
        SourceType.BLE -> bleName.orEmpty()
        SourceType.WIFI -> ""
    }

    fun build(): SourceConfig {
        val id = initial?.id ?: UUID.randomUUID().toString()
        val finalName = name.trim().ifEmpty { defaultName() }
        return when (type) {
            SourceType.TCP -> TcpSourceConfig(id, finalName, host.trim(), portValue!!)
            SourceType.HTTPS -> HttpsSourceConfig(id, finalName, url.trim(), pollValue!!)
            SourceType.BLE -> BleSourceConfig(id, finalName, bleAddress!!, bleName.orEmpty())
            SourceType.WIFI -> error("Not implemented")
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (initial == null) R.string.add_source else R.string.edit_source)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    Button(onClick = { onSave(build()) }, enabled = valid) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.field_type), style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SourceType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        enabled = option.implemented && (initial == null || initial.type == option),
                        onClick = { type = option },
                        leadingIcon = {
                            Icon(painterResource(option.iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = {
                            val label = stringResource(option.labelRes)
                            Text(if (option.implemented) label else stringResource(R.string.coming_later_suffix, label))
                        },
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            when (type) {
                SourceType.TCP -> {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.field_host)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.field_port)) },
                        singleLine = true,
                        isError = port.isNotEmpty() && portValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SourceType.HTTPS -> {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.field_url)) },
                        placeholder = { Text("https://") },
                        singleLine = true,
                        isError = !urlValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pollSeconds,
                        onValueChange = { pollSeconds = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.field_poll_interval)) },
                        singleLine = true,
                        isError = pollValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SourceType.BLE -> {
                    bleName?.let { Text(stringResource(R.string.ble_selected, it), style = MaterialTheme.typography.bodyLarge) }
                    if (scanWanted && isScanning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                            Text(stringResource(R.string.ble_scanning))
                        }
                    }
                    if (scanWanted && bluetoothState == BleBluetoothState.DISABLED) {
                        Text(stringResource(R.string.ble_bluetooth_disabled))
                        Button(
                            onClick = {
                                enableBluetoothLauncher.launch(
                                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.ble_enable_bluetooth))
                        }
                    } else if (scanWanted && bluetoothState == BleBluetoothState.UNAVAILABLE) {
                        Text(stringResource(R.string.ble_bluetooth_unavailable))
                    }
                    devices.forEach { device ->
                        ListItem(
                            headlineContent = { Text(device.name) },
                            leadingContent = {
                                RadioButton(selected = bleAddress == device.address, onClick = null)
                            },
                            modifier = Modifier.selectable(
                                selected = bleAddress == device.address,
                                role = Role.RadioButton,
                                onClick = {
                                    bleAddress = device.address
                                    bleName = device.name
                                    if (name.isBlank()) name = device.name
                                },
                            ),
                        )
                    }
                }
                SourceType.WIFI -> Text(stringResource(R.string.wifi_later))
            }
        }
    }
}

@Preview(name = "Phone - Portrait", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AddSourcePhonePortraitPreview() {
    AddSourceScreenPreview()
}

@Preview(name = "Phone - Landscape", showBackground = true, widthDp = 852, heightDp = 393)
@Composable
private fun AddSourcePhoneLandscapePreview() {
    AddSourceScreenPreview()
}

@Preview(name = "Tablet - Portrait", showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
private fun AddSourceTabletPortraitPreview() {
    AddSourceScreenPreview()
}

@Preview(name = "Tablet - Landscape", showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun AddSourceTabletLandscapePreview() {
    AddSourceScreenPreview()
}

@Composable
private fun AddSourceScreenPreview() {
    AtcollectorTheme {
        AddSourceScreen(
            initial = null,
            devices = emptyList(),
            isScanning = false,
            bluetoothState = BleBluetoothState.UNKNOWN,
            onStartScan = {},
            onStopScan = {},
            onBlePermissionsGranted = {},
            onBluetoothEnableResult = {},
            onSave = {},
            onCancel = {},
        )
    }
}
