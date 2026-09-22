package io.github.painter99.wswolomouc.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One persisted station measurement (PRD F4.1, data model from PRD §6).
 * Wind is stored in m/s; rain semantics per station are documented on
 * [io.github.painter99.wswolomouc.data.StationMeasurement].
 */
@Entity(tableName = "measurement")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val station: String,          // "INFOPOCASI" | "CHMU_HOLICE"
    val temperatureC: Float?,
    val humidityPct: Int?,
    val pressureHpa: Float?,
    val windMs: Float?,
    val windGustMs: Float?,
    val windDirDeg: Int?,
    val rainMm: Float?,
    /** Unified daily total for BOTH stations (M1.6b-2; see StationMeasurement). */
    val rainDailyMm: Float? = null,
    val measuredAt: Long,         // measurement time from the source (epoch ms)
    val fetchedAt: Long           // fetch time (epoch ms)
)
