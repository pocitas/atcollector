package cz.pocitas.atcollector.source

import cz.pocitas.atcollector.model.SourceConfig
import cz.pocitas.atcollector.model.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Keeps running [TrafficSource]s in sync with the configured [SourceConfig]s. */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceManager(
    private val factory: SourceFactory,
    private val scope: CoroutineScope,
) {
    private val running = MutableStateFlow<Map<String, Pair<SourceConfig, TrafficSource>>>(emptyMap())

    /** Targets from all running sources. */
    val targets: Flow<Target> = running.flatMapLatest { map ->
        if (map.isEmpty()) emptyFlow() else map.values.map { it.second.targets }.merge()
    }

    val statuses: Flow<Map<String, SourceStatus>> = running.flatMapLatest { map ->
        if (map.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(map.map { (id, entry) -> entry.second.status.map { id to it } }) { it.toMap() }
        }
    }

    @Synchronized
    fun sync(configs: List<SourceConfig>) {
        val current = running.value
        val next = LinkedHashMap<String, Pair<SourceConfig, TrafficSource>>()
        for (config in configs.filter { it.enabled }) {
            val existing = current[config.id]
            next[config.id] = if (existing != null && existing.first == config) {
                existing
            } else {
                existing?.second?.stop()
                config to factory.create(config, scope).also { it.start() }
            }
        }
        current.filterKeys { it !in next }.values.forEach { it.second.stop() }
        running.value = next
    }

    /** Restarts sources that failed, e.g. after BLE permissions were granted. */
    @Synchronized
    fun restartAll() = running.value.values.forEach { it.second.stop(); it.second.start() }
}
