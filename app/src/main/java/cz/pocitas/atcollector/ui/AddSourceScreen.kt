package cz.pocitas.atcollector.ui

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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.pocitas.atcollector.BleDevice
import cz.pocitas.atcollector.model.BleSourceConfig
import cz.pocitas.atcollector.model.HttpsSourceConfig
import cz.pocitas.atcollector.model.SourceConfig
import cz.pocitas.atcollector.model.SourceType
import cz.pocitas.atcollector.model.TcpSourceConfig
import cz.pocitas.atcollector.source.BlePermissions
import java.util.UUID

/** Adds a new source, or edits [initial] when given. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceScreen(
    initial: SourceConfig?,
    devices: List<BleDevice>,
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onBlePermissionsGranted: () -> Unit,
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
    fun scan() {
        if (BlePermissions.granted(context)) onStartScan()
        else permissionLauncher.launch(BlePermissions.required())
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
                title = { Text(if (initial == null) "Add source" else "Edit source") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
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
            Text("Type", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SourceType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        enabled = option.implemented && (initial == null || initial.type == option),
                        onClick = { type = option },
                        label = {
                            Text(option.label + if (option.implemented) "" else " (coming later)")
                        },
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            when (type) {
                SourceType.TCP -> {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text("Port") },
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
                        label = { Text("URL") },
                        singleLine = true,
                        isError = !urlValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pollSeconds,
                        onValueChange = { pollSeconds = it.filter(Char::isDigit).take(5) },
                        label = { Text("Polling interval (seconds)") },
                        singleLine = true,
                        isError = pollValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SourceType.BLE -> {
                    bleName?.let { Text("Selected: $it", style = MaterialTheme.typography.bodyLarge) }
                    OutlinedButton(onClick = ::scan, enabled = !isScanning) {
                        Text(if (isScanning) "Scanning…" else "Scan for devices")
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
                                },
                            ),
                        )
                    }
                }

                SourceType.WIFI -> Text("Wi-Fi devices will be supported in a later version.")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = { onSave(build()) }, enabled = valid) { Text("Save") }
            }
        }
    }
}
