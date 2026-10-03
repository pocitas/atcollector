package cz.pocitas.atcollector.source

import cz.pocitas.atcollector.model.Target
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Shared start/stop, retry and line-parsing logic for sources. */
abstract class BaseTrafficSource(
    protected val sourceId: String,
    private val scope: CoroutineScope,
    private val retryDelayMs: Long = 5_000,
) : TrafficSource {

    private val _targets = MutableSharedFlow<Target>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val targets: SharedFlow<Target> = _targets

    private val _status = MutableStateFlow(SourceStatus(SourceState.STOPPED))
    override val status: StateFlow<SourceStatus> = _status

    private var job: Job? = null

    /** Connects and processes data until the connection ends. Throwing triggers a retry. */
    protected abstract suspend fun run()

    @Synchronized
    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    setStatus(SourceState.CONNECTING)
                    run()
                    setStatus(SourceState.DISCONNECTED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PermissionMissingException) {
                    setStatus(SourceState.PERMISSION_REQUIRED)
                } catch (e: Exception) {
                    setStatus(SourceState.ERROR, e.message ?: e.javaClass.simpleName)
                }
                delay(retryDelayMs.milliseconds)
            }
        }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        job = null
        setStatus(SourceState.STOPPED)
    }

    protected fun setStatus(state: SourceState, detail: String? = null) {
        _status.value = SourceStatus(state, detail)
    }

    protected fun onLine(line: String) {
        PflaaParser.parse(sourceId, line)?.let { _targets.tryEmit(it) }
    }
}

class PermissionMissingException : Exception("Permission missing")

/** Reassembles newline-terminated text from arbitrary byte chunks (e.g. BLE notifications). */
class LineAssembler(private val onLine: (String) -> Unit) {
    private val buffer = StringBuilder()

    fun append(bytes: ByteArray) {
        buffer.append(String(bytes, Charsets.US_ASCII))
        var index = buffer.indexOf("\n")
        while (index >= 0) {
            onLine(buffer.substring(0, index))
            buffer.delete(0, index + 1)
            index = buffer.indexOf("\n")
        }
        if (buffer.length > 4096) buffer.clear()
    }
}
