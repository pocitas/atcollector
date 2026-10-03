package cz.pocitas.atcollector.model

enum class SourceType(val label: String, val implemented: Boolean = true) {
    TCP("TCP client"),
    HTTPS("HTTPS polling"),
    BLE("Bluetooth LE device"),
    WIFI("Wi-Fi device", implemented = false),
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
