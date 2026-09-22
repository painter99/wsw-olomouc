package io.github.painter99.wswolomouc.data

/**
 * A single weather data source (M1.4). Implementations perform the network
 * call, parse the payload and map it to [StationMeasurement].
 *
 * Contract: returns null on any failure (HTTP error, malformed data, IO
 * exception). Implementations must not throw — the repository still guards
 * with try/catch as defense in depth (F1.4).
 */
interface StationDataSource {
    /** Stable station identifier, e.g. [Sources.STATION_INFOPOCASI]. */
    val id: String

    suspend fun fetch(): StationMeasurement?

    /**
     * Detailed fetch outcome (M1.6b-3). Default implementation wraps the
     * legacy [fetch] result; real sources override it to report the actual
     * failure reason (HTTP code vs. network vs. parse).
     */
    suspend fun fetchResult(): FetchResult =
        fetch()?.let { FetchResult.Success(it) }
            ?: FetchResult.ParseError("unspecified failure")
}
