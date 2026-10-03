package cz.pocitas.atcollector.source

import cz.pocitas.atcollector.model.TcpSourceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class TcpSource(
    private val config: TcpSourceConfig,
    scope: CoroutineScope,
) : BaseTrafficSource(config.id, scope) {

    override suspend fun run() = withContext(Dispatchers.IO) {
        Socket().use { socket ->
            // Blocking socket reads don't react to cancellation, so close the socket instead.
            val handle = currentCoroutineContext().job.invokeOnCompletion { runCatching { socket.close() } }
            try {
                socket.connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)
                setStatus(SourceState.CONNECTED)
                socket.getInputStream().bufferedReader().forEachLine(::onLine)
            } finally {
                handle.dispose()
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
