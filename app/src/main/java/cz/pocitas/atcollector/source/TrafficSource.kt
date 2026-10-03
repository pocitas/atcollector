package cz.pocitas.atcollector.source

import android.content.Context
import cz.pocitas.atcollector.R
import cz.pocitas.atcollector.model.Target
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SourceState { STOPPED, CONNECTING, CONNECTED, DISCONNECTED, ERROR, PERMISSION_REQUIRED }

enum class SourceHealth { HEALTHY, WARNING, ERROR }

data class SourceStatus(val state: SourceState, val detail: String? = null) {
    val health: SourceHealth
        get() = when (state) {
            SourceState.CONNECTED -> SourceHealth.HEALTHY
            SourceState.ERROR, SourceState.DISCONNECTED, SourceState.PERMISSION_REQUIRED -> SourceHealth.ERROR
            SourceState.STOPPED, SourceState.CONNECTING -> SourceHealth.WARNING
        }

    fun label(context: Context): String = when (state) {
        SourceState.STOPPED -> context.getString(R.string.status_stopped)
        SourceState.CONNECTING -> context.getString(R.string.status_connecting)
        SourceState.CONNECTED -> context.getString(R.string.status_connected)
        SourceState.DISCONNECTED -> context.getString(R.string.status_disconnected)
        SourceState.ERROR ->
            if (detail != null) context.getString(R.string.status_error_detail, detail)
            else context.getString(R.string.status_error)
        SourceState.PERMISSION_REQUIRED -> context.getString(R.string.status_permission_required)
    }
}

/** A producer of traffic [Target]s. */
interface TrafficSource {
    val targets: Flow<Target>
    val status: StateFlow<SourceStatus>

    fun start()
    fun stop()
}
