package cz.pocitas.atcollector

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.pocitas.atcollector.data.SourceRepository
import cz.pocitas.atcollector.model.SourceConfig
import cz.pocitas.atcollector.source.BlePermissions
import cz.pocitas.atcollector.source.SourceFactory
import cz.pocitas.atcollector.source.SourceManager
import cz.pocitas.atcollector.source.SourceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** A BLE device found while scanning. */
data class BleDevice(val address: String, val name: String)

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

    private var scanJob: Job? = null

    init {
        repository.sources.onEach(manager::sync).launchIn(viewModelScope)
    }

    fun save(config: SourceConfig) = repository.upsert(config)

    fun delete(id: String) = repository.remove(id)

    fun restore(config: SourceConfig, index: Int) = repository.restore(config, index)

    /** Call after BLE permissions were granted so that BLE sources retry immediately. */
    fun onBlePermissionsGranted() = manager.restartAll()

    fun startScan() {
        if (scanJob?.isActive == true || !BlePermissions.granted(context)) return
        _devices.value = emptyList()
        scanJob = centralManager.scan(SCAN_DURATION)
            .onStart { _isScanning.value = true }
            .onEach { result ->
                val peripheral = result.peripheral
                val name = peripheral.name?.takeIf { it.isNotBlank() } ?: return@onEach
                if (result.isConnectable != true) return@onEach
                _devices.update { list ->
                    if (list.any { it.address == peripheral.address }) list
                    else (list + BleDevice(peripheral.address, name)).sortedBy { it.name.lowercase() }
                }
            }
            .catch { }
            .onCompletion { _isScanning.value = false }
            .launchIn(viewModelScope)
    }

    fun stopScan() {
        scanJob?.cancel()
    }

    private companion object {
        val SCAN_DURATION = 10.seconds
    }
}
