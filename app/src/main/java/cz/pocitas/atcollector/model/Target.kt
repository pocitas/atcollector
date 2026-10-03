package cz.pocitas.atcollector.model

/** A traffic target (e.g. an aircraft) reported by a source. Positions are relative to the receiver. */
data class Target(
    val sourceId: String,
    val id: String,
    val relativeNorthM: Int,
    val relativeEastM: Int,
    val relativeVerticalM: Int,
    val trackDeg: Int?,
    val groundSpeedMs: Int?,
    val climbRateMs: Double?,
    val receivedAtMs: Long = System.currentTimeMillis(),
)
