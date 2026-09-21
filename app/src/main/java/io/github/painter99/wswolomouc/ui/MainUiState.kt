package io.github.painter99.wswolomouc.ui

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.StationMeasurement
import io.github.painter99.wswolomouc.data.WeatherRepository

/**
 * UI model of one station card (M1.5, PRD F3.1/F3.2).
 *
 * Rain semantics differ per source (M1.4 decision) and are carried as
 * [rainLabel] so the UI can show them honestly:
 *  - INFOPOCASI: daily total
 *  - CHMU_HOLICE: accumulation of the last 10 minutes
 *
 * Pressure exists only for INFOPOCASI (CHMU 10M feed has no P element);
 * null is rendered as "—", never as 0.
 */
data class StationUi(
    val station: String,
    val displayName: String,
    val temperatureC: Float?,
    val humidityPct: Int?,
    val pressureHpa: Float?,
    val windMs: Float?,
    val windGustMs: Float?,
    val windDirDeg: Int?,
    val rainMm: Float?,
    val rainLabel: String,
    val measuredAtMs: Long,
    val isStale: Boolean
) {
    /** PRD F1.2: the UI converts wind m/s -> km/h (x3.6). */
    val windKmh: Float? get() = windMs?.times(3.6f)
    val gustKmh: Float? get() = windGustMs?.times(3.6f)

    companion object {
        fun displayNameFor(station: String): String = when (station) {
            Sources.STATION_INFOPOCASI -> "Infopocasi Olomouc"
            Sources.STATION_CHMU -> "ČHMÚ Olomouc–Holice"
            else -> station
        }

        fun rainLabelFor(station: String): String = when (station) {
            Sources.STATION_INFOPOCASI -> "úhrn za den"
            Sources.STATION_CHMU -> "úhrn za 10 min"
            else -> "—"
        }
    }
}

/**
 * Whole main screen state (M1.5). [primary] = hardcoded primary station
 * INFOPOCASI (Q5 recommendation, real choice only at F5.1).
 */
data class MainUiState(
    val isLoading: Boolean = true,
    val freshness: WeatherRepository.Freshness? = null,
    val primary: StationUi? = null,
    val secondary: StationUi? = null
)

object MainUiStateMapper {

    /** Per-station staleness threshold (same constant as widget synthesis, F2.5). */
    private val STALE_MS = Sources.STALE_THRESHOLD_MIN * 60_000L

    fun from(snapshot: WeatherRepository.Snapshot, nowMs: Long): MainUiState = MainUiState(
        isLoading = false,
        freshness = snapshot.freshness,
        primary = snapshot.measurements[Sources.STATION_INFOPOCASI]?.let { stationUi(it, nowMs) },
        secondary = snapshot.measurements[Sources.STATION_CHMU]?.let { stationUi(it, nowMs) }
    )

    fun stationUi(m: StationMeasurement, nowMs: Long): StationUi = StationUi(
        station = m.station,
        displayName = StationUi.displayNameFor(m.station),
        temperatureC = m.temperatureC,
        humidityPct = m.humidityPct,
        pressureHpa = m.pressureHpa,
        windMs = m.windMs,
        windGustMs = m.windGustMs,
        windDirDeg = m.windDirDeg,
        rainMm = m.rainMm,
        rainLabel = StationUi.rainLabelFor(m.station),
        measuredAtMs = m.measuredAtMs,
        isStale = nowMs - m.measuredAtMs > STALE_MS
    )
}
