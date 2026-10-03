package cz.pocitas.atcollector.source

import cz.pocitas.atcollector.model.Target

/** Parses FLARM `PFLAA` sentences, as emitted by SoftRF, XCGuide and similar devices. */
object PflaaParser {
    fun parse(sourceId: String, line: String, nowMs: Long = System.currentTimeMillis()): Target? {
        val text = line.trim()
        if (!text.startsWith("\$PFLAA,")) return null
        val f = text.substringBefore('*').split(',')
        if (f.size < 11) return null
        return Target(
            sourceId = sourceId,
            id = f[6].ifBlank { return null },
            relativeNorthM = f[2].toIntOrNull() ?: return null,
            relativeEastM = f[3].toIntOrNull() ?: return null,
            relativeVerticalM = f[4].toIntOrNull() ?: return null,
            trackDeg = f[7].toIntOrNull(),
            groundSpeedMs = f[9].toIntOrNull(),
            climbRateMs = f[10].toDoubleOrNull(),
            receivedAtMs = nowMs,
        )
    }
}
