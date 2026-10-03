package cz.pocitas.atcollector.source

import cz.pocitas.atcollector.model.Target
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SourceState { STOPPED, CONNECTING, CONNECTED, ERROR, PERMISSION_REQUIRED }

data class SourceStatus(val state: SourceState, val detail: String? = null) {
    val label: String
        get() = when (state) {
            SourceState.STOPPED -> "Stopped"
            SourceState.CONNECTING -> "Connecting…"
            SourceState.CONNECTED -> "Connected"
            SourceState.ERROR -> "Error" + (detail?.let { ": $it" } ?: "")
            SourceState.PERMISSION_REQUIRED -> "Permission required"
        }
}

/** A producer of traffic [Target]s. */
interface TrafficSource {
    val targets: Flow<Target>
    val status: StateFlow<SourceStatus>

    fun start()
    fun stop()
}
