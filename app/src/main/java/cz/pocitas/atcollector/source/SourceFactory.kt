package cz.pocitas.atcollector.source

import android.content.Context
import cz.pocitas.atcollector.model.BleSourceConfig
import cz.pocitas.atcollector.model.HttpsSourceConfig
import cz.pocitas.atcollector.model.SourceConfig
import cz.pocitas.atcollector.model.TcpSourceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import okhttp3.OkHttpClient
import javax.inject.Inject

/** Creates [TrafficSource]s with their shared, injected dependencies. */
class SourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val centralManager: CentralManager,
    private val httpClient: OkHttpClient,
) {
    fun create(config: SourceConfig, scope: CoroutineScope): TrafficSource = when (config) {
        is TcpSourceConfig -> TcpSource(config, scope)
        is HttpsSourceConfig -> HttpsSource(config, httpClient, scope)
        is BleSourceConfig -> BleSource(config, context, centralManager, scope)
    }
}
