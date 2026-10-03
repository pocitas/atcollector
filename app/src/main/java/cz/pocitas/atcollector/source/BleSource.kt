package cz.pocitas.atcollector.source

import android.content.Context
import cz.pocitas.atcollector.model.BleSourceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import no.nordicsemi.kotlin.ble.client.RemoteServices
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.core.ConnectionState
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Connects to a previously selected BLE device without user interaction and reads FLARM-style
 * text from the Nordic UART Service TX characteristic (used e.g. by SoftRF).
 */
class BleSource(
    private val config: BleSourceConfig,
    private val context: Context,
    private val centralManager: CentralManager,
    scope: CoroutineScope,
) : BaseTrafficSource(config.id, scope) {

    override suspend fun run() {
        if (!BlePermissions.granted(context)) throw PermissionMissingException()
        val peripheral = centralManager.getPeripheralById(config.address)
            ?: throw IllegalStateException("Device not available")
        try {
            centralManager.connect(peripheral, CentralManager.ConnectionOptions.Direct(timeout = 15.seconds))
            coroutineScope {
                val assembler = LineAssembler(::onLine)
                val subscription = peripheral.services(listOf(NUS_SERVICE))
                    .filterIsInstance<RemoteServices.Discovered>()
                    .first()
                    .services
                    .first { it.uuid == NUS_SERVICE }
                    .characteristics
                    .first { it.uuid == NUS_TX }
                    .subscribe()
                    .onEach { assembler.append(it) }
                    .launchIn(this)
                setStatus(SourceState.CONNECTED)
                peripheral.state.first { it is ConnectionState.Disconnected }
                subscription.cancel()
            }
        } finally {
            withContext(NonCancellable) { runCatching { peripheral.disconnect() } }
        }
    }

    private companion object {
        val NUS_SERVICE = Uuid.parse("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NUS_TX = Uuid.parse("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }
}
