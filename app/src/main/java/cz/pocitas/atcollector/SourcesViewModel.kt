package cz.pocitas.atcollector

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import cz.pocitas.atcollector.data.SourceRepository
import cz.pocitas.atcollector.model.SourceConfig
import cz.pocitas.atcollector.source.BlePermissions
import cz.pocitas.atcollector.source.SourceFactory
import cz.pocitas.atcollector.source.SourceManager
import cz.pocitas.atcollector.source.SourceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** A BLE device found while scanning. */
data class BleDevice(val address: String, val name: String)

enum class BleBluetoothState {
    UNKNOWN,
    ENABLED,
    DISABLED,
    UNAVAILABLE,
}

@HiltViewModel
class SourcesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SourceRepository,
    private val centralManager: CentralManager,
    factory: SourceFactory,
) : ViewModel() {

    private val manager = SourceManager(factory, viewModelScope)

    val sources: StateFlow<List<SourceConfig>> = repository.sources

    val statuses: StateFlow<Map<String, SourceStatus>> = manager.statuses
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _bluetoothState = MutableStateFlow(BleBluetoothState.UNKNOWN)
    val bluetoothState: StateFlow<BleBluetoothState> = _bluetoothState

    private var scanJob: Job? = null

    /** True while the UI wants scanning; lets Bluetooth state changes pause and resume the scan. */
    private var scanRequested = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            refreshBluetoothState()
            if (!scanRequested) return
            if (_bluetoothState.value == BleBluetoothState.ENABLED) startScan() else scanJob?.cancel()
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        repository.sources.onEach(manager::sync).launchIn(viewModelScope)
    }

    fun save(config: SourceConfig) = repository.upsert(config)

    fun delete(id: String) = repository.remove(id)

    fun restore(config: SourceConfig, index: Int) = repository.restore(config, index)

    /** Call after BLE permissions were granted so that BLE sources retry immediately. */
    fun onBlePermissionsGranted() = manager.restartAll()

    fun startScan() {
        if (!scanRequested) _devices.value = emptyList()
        scanRequested = true
        if (scanJob?.isActive == true || !BlePermissions.granted(context)) return
        refreshBluetoothState()
        if (_bluetoothState.value != BleBluetoothState.ENABLED) return
        _devices.value = emptyList()
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            try {
                while (isActive) {
                    try {
                        centralManager.scan(SCAN_DURATION).collect { result ->
                            val peripheral = result.peripheral
                            val name = peripheral.name?.takeIf { it.isNotBlank() } ?: return@collect
                            if (result.isConnectable != true) return@collect
                            _devices.update { list ->
                                if (list.any { it.address == peripheral.address }) list
                                else (list + BleDevice(peripheral.address, name))
                                    .sortedBy { it.name.lowercase() }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "BLE scan failed; retrying", e)
                        delay(SCAN_RETRY_DELAY)
                    }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun onBluetoothEnableResult() {
        refreshBluetoothState()
        if (_bluetoothState.value == BleBluetoothState.ENABLED) startScan()
    }

    fun stopScan() {
        scanRequested = false
        scanJob?.cancel()
    }

    private fun refreshBluetoothState() {
        if (!BlePermissions.granted(context)) {
            _bluetoothState.value = BleBluetoothState.UNKNOWN
            return
        }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        _bluetoothState.value = when {
            adapter == null -> BleBluetoothState.UNAVAILABLE
            adapter.isEnabled -> BleBluetoothState.ENABLED
            else -> BleBluetoothState.DISABLED
        }
    }

    override fun onCleared() {
        context.unregisterReceiver(bluetoothStateReceiver)
    }

    private companion object {
        const val TAG = "SourcesViewModel"
        val SCAN_DURATION = 10.seconds
        // Android allows at most 5 scan starts per 30 s; retrying faster makes failures permanent.
        val SCAN_RETRY_DELAY = 10.seconds
    }
}
