package cz.pocitas.atcollector.data

import android.content.Context
import cz.pocitas.atcollector.model.BleSourceConfig
import cz.pocitas.atcollector.model.HttpsSourceConfig
import cz.pocitas.atcollector.model.SourceConfig
import cz.pocitas.atcollector.model.TcpSourceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the configured sources in `filesDir/sources.json`. The file is listed in the backup
 * rules (`backup_rules.xml`, `data_extraction_rules.xml`) so it's restored on a new phone.
 */
@Singleton
class SourceRepository @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val _sources = MutableStateFlow(load())
    val sources: StateFlow<List<SourceConfig>> = _sources

    @Synchronized
    fun upsert(config: SourceConfig) {
        val current = _sources.value
        val updated = if (current.any { it.id == config.id }) {
            current.map { if (it.id == config.id) config else it }
        } else {
            current + config
        }
        save(updated)
    }

    /** Re-adds a previously removed [config] at its original [index]. */
    @Synchronized
    fun restore(config: SourceConfig, index: Int) {
        val current = _sources.value.filterNot { it.id == config.id }
        save(current.toMutableList().apply { add(index.coerceIn(0, size), config) })
    }

    /** Reorders sources to match [ids]; sources not listed keep their relative order at the end. */
    @Synchronized
    fun reorder(ids: List<String>) {
        val current = _sources.value
        val byId = current.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        save(ordered + current.filterNot { it.id in ids })
    }

    @Synchronized
    fun remove(id: String) = save(_sources.value.filterNot { it.id == id })

    private fun save(list: List<SourceConfig>) {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(JSONArray(list.map(::toJson)).toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        _sources.value = list
    }

    private fun load(): List<SourceConfig> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).mapNotNull { runCatching { fromJson(array.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun toJson(c: SourceConfig): JSONObject = JSONObject()
        .put("id", c.id)
        .put("name", c.name)
        .put("type", c.type.name)
        .put("enabled", c.enabled)
        .apply {
            when (c) {
                is TcpSourceConfig -> put("host", c.host).put("port", c.port)
                is HttpsSourceConfig -> put("url", c.url).put("pollSeconds", c.pollSeconds)
                is BleSourceConfig -> put("address", c.address).put("deviceName", c.deviceName)
            }
        }

    private fun fromJson(o: JSONObject): SourceConfig {
        val id = o.getString("id")
        val name = o.getString("name")
        val enabled = o.optBoolean("enabled", true)
        return when (o.getString("type")) {
            "TCP" -> TcpSourceConfig(id, name, o.getString("host"), o.getInt("port"), enabled)
            "HTTPS" -> HttpsSourceConfig(id, name, o.getString("url"), o.getInt("pollSeconds"), enabled)
            "BLE" -> BleSourceConfig(id, name, o.getString("address"), o.getString("deviceName"), enabled)
            else -> throw IllegalArgumentException("Unknown source type")
        }
    }

    private companion object {
        const val FILE_NAME = "sources.json"
    }
}
