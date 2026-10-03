package cz.pocitas.atcollector.source

import cz.pocitas.atcollector.model.HttpsSourceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

class HttpsSource(
    private val config: HttpsSourceConfig,
    private val client: OkHttpClient,
    scope: CoroutineScope,
) : BaseTrafficSource(config.id, scope) {

    override suspend fun run() {
        val request = Request.Builder().url(config.url).build()
        while (true) {
            try {
                val body = client.newCall(request).await()
                body.lineSequence().forEach(::onLine)
                setStatus(SourceState.CONNECTED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus(SourceState.ERROR, e.message ?: e.javaClass.simpleName)
            }
            delay(config.pollSeconds.seconds)
        }
    }

    private suspend fun Call.await(): String = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        cont.resumeWithException(IOException("HTTP ${it.code}"))
                    } else {
                        try {
                            cont.resume(it.body.string())
                        } catch (e: IOException) {
                            cont.resumeWithException(e)
                        }
                    }
                }
            }
        })
    }
}
