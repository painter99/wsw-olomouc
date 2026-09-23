package io.github.painter99.wswolomouc.ui

import io.github.painter99.wswolomouc.Sources
import io.github.painter99.wswolomouc.data.FetchResult
import io.github.painter99.wswolomouc.data.StationMeasurement
import io.github.painter99.wswolomouc.data.WeatherRepository

/**
 * UI model of one station card (M1.5, PRD F3.1/F3.2; placeholders M1.6a.1).
 *
 * Rain semantics differ per source (M1.4 decision) and are carried as
 * [rainLabel] so the UI can show them honestly:
 *  - INFOPOCASI: daily total
 *  - CHMU_HOLICE: accumulation of the last 10 minutes
 *
 * Pressure exists only for INFOPOCASI (CHMU 10M feed has no P element);
 * null is rendered as "—", never as 0.
 *
 * Wind values are m/s (PRD data model). The UI converts to km/h exactly
 * once, via [Format.wind] — do NOT pre-convert (M1.6a.1: the card showed
 * 80 km/h for a 6.2 m/s day because windKmh was fed into Format.wind).
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
    /**
     * DAILY precipitation total for BOTH stations (M1.6b-2 unification,
     * Pavel 22. 9.) — labeled "úhrn za den". The per-source raw semantics
     * (INFOPOCASI daily / CHMU 10-min) stay in the DB ([rainMm] history).
     */
    val rainDailyMm: Float?,
    val rainLabel: String,
    /** Measurement time from the source; null = the station has no data. */
    val measuredAtMs: Long?,
    val isStale: Boolean,
    /** false = placeholder card ("bez dat"), true = real measurement. */
    val hasData: Boolean,
    /** Per-source fetch outcome note (M1.6b-3), e.g. "HTTP 404"; null = OK/no attempt. */
    val statusNote: String? = null
) {
    companion object {
        fun displayNameFor(station: String): String = when (station) {
            Sources.STATION_INFOPOCASI -> "Infopocasi Olomouc"
            Sources.STATION_CHMU -> "ČHMÚ Olomouc–Holice"
            else -> station
        }

        /** Unified since M1.6b-2: both sources show the daily total. */
        fun rainLabelFor(station: String): String = "úhrn za den"
    }
}

/**
 * Whole main screen state (M1.5). [primary] = hardcoded primary station
 * INFOPOCASI (Q5 recommendation, real choice only at F5.1).
 *
 * Since M1.6a.1 both stations are always present — a station without data
 * renders as a placeholder card instead of disappearing silently (G3, US-002).
 */
data class MainUiState(
    val isLoading: Boolean = true,
    val freshness: WeatherRepository.Freshness? = null,
    val primary: StationUi? = null,
    val secondary: StationUi? = null,
    /**
     * Per-source fetch outcome label (M1.6b-3): station id -> "OK" /
     * "HTTP 404" / "síť" / "data". Shown in the status row so a failing
     * source is visible instead of a global "Offline" guess.
     */
    val sourceStatus: Map<String, String> = emptyMap(),
    /** True while a refresh is running (M1.6b-3 refresh feedback). */
    val isRefreshing: Boolean = false,
    /** Non-null = refresh was skipped by the rate limit; text says when to retry. */
    val rateLimitMessage: String? = null,
    /**
     * Temperature trend of the PRIMARY station for 1 h / 3 h / 6 h windows
     * (M1.7-trend, Pavel 23. 9.); empty when the primary has no data.
     */
    val trends: List<TrendItem> = emptyList()
)

object MainUiStateMapper {

    /** Per-station staleness threshold (same constant as widget synthesis, F2.5). */
    private val STALE_MS = Sources.STALE_THRESHOLD_MIN * 60_000L

    fun from(snapshot: WeatherRepository.Snapshot, nowMs: Long): MainUiState = MainUiState(
        isLoading = false,
        freshness = snapshot.freshness,
        primary = snapshot.measurements[Sources.STATION_INFOPOCASI]
            ?.let { stationUi(it, nowMs) }
            ?: placeholder(
                Sources.STATION_INFOPOCASI,
                noteFor(snapshot, Sources.STATION_INFOPOCASI)
            ),
        secondary = snapshot.measurements[Sources.STATION_CHMU]
            ?.let { stationUi(it, nowMs) }
            ?: placeholder(
                Sources.STATION_CHMU,
                noteFor(snapshot, Sources.STATION_CHMU)
            ),
        sourceStatus = snapshot.sourceResults.mapValues { statusLabel(it.value) }
    )

    /** Failure note for a station card; null for Success / no attempt. */
    private fun noteFor(snapshot: WeatherRepository.Snapshot, station: String): String? =
        snapshot.sourceResults[station]?.let { r ->
            if (r is FetchResult.Success) null else statusLabel(r)
        }

    /** Short per-source outcome label for the UI (M1.6b-3). */
    fun statusLabel(result: FetchResult): String = when (result) {
        is FetchResult.Success -> "OK"
        is FetchResult.HttpError -> "HTTP ${result.code}"
        is FetchResult.NetworkError -> "síť"
        is FetchResult.ParseError -> "data"
    }

    fun stationUi(m: StationMeasurement, nowMs: Long): StationUi = StationUi(
        station = m.station,
        displayName = StationUi.displayNameFor(m.station),
        temperatureC = m.temperatureC,
        humidityPct = m.humidityPct,
        pressureHpa = m.pressureHpa,
        windMs = m.windMs,
        windGustMs = m.windGustMs,
        windDirDeg = m.windDirDeg,
        rainDailyMm = m.rainDailyMm,
        rainLabel = StationUi.rainLabelFor(m.station),
        measuredAtMs = m.measuredAtMs,
        isStale = nowMs - m.measuredAtMs > STALE_MS,
        hasData = true
    )

    /** Card for a station with no data at all (M1.6a.1). */
    fun placeholder(station: String, statusNote: String? = null): StationUi = StationUi(
        station = station,
        displayName = StationUi.displayNameFor(station),
        temperatureC = null,
        humidityPct = null,
        pressureHpa = null,
        windMs = null,
        windGustMs = null,
        windDirDeg = null,
        rainDailyMm = null,
        rainLabel = StationUi.rainLabelFor(station),
        measuredAtMs = null,
        isStale = false,
        hasData = false,
        statusNote = statusNote
    )
}
