package io.github.painter99.wswolomouc.data

/**
 * Outcome of a single source fetch (M1.6b-3).
 *
 * Replaces the silent null-on-failure contract so the UI can show WHY a
 * source is offline (per-source status) instead of a global "Offline"
 * guess. The reason is the diagnostic for the CHMU fetch problem observed
 * on 2026-09-22 (server verified healthy; the failure was invisible).
 */
sealed interface FetchResult {

    /** Fetch + parse succeeded; the repository persists [measurement]. */
    data class Success(val measurement: StationMeasurement) : FetchResult

    /** Server answered with a non-2xx code. */
    data class HttpError(val code: Int) : FetchResult

    /** Request could not be completed (IO/DNS/timeout). */
    data class NetworkError(val message: String) : FetchResult

    /** Server answered 2xx but the payload was unusable. */
    data class ParseError(val detail: String) : FetchResult
}
