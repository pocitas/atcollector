package cz.pocitas.atcollector.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import cz.pocitas.atcollector.R

enum class SourceType(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val implemented: Boolean = true,
) {
    TCP(R.string.source_type_tcp, R.drawable.ic_mobile_share),
    HTTPS(R.string.source_type_https, R.drawable.ic_public),
    BLE(R.string.source_type_ble, R.drawable.ic_bluetooth),
    WIFI(R.string.source_type_wifi, R.drawable.ic_wifi, implemented = false),
}

/** User-configured traffic source. Persisted (and backed up) by [cz.pocitas.atcollector.data.SourceRepository]. */
sealed interface SourceConfig {
    val id: String
    val name: String
    val type: SourceType
}

data class TcpSourceConfig(
    override val id: String,
    override val name: String,
    val host: String = DEFAULT_HOST,
    val port: Int,
) : SourceConfig {
    override val type get() = SourceType.TCP

    companion object {
        const val DEFAULT_HOST = "localhost"
    }
}

data class HttpsSourceConfig(
    override val id: String,
    override val name: String,
    val url: String,
    val pollSeconds: Int,
) : SourceConfig {
    override val type get() = SourceType.HTTPS
}

data class BleSourceConfig(
    override val id: String,
    override val name: String,
    val address: String,
    val deviceName: String,
) : SourceConfig {
    override val type get() = SourceType.BLE
}
