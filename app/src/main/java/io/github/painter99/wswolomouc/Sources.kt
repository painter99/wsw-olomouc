package io.github.painter99.wswolomouc

/**
 * Verified data source endpoints (PRD F1.1, F1.2).
 * Live-verified 2026-09-19.
 */
object Sources {
    /**
     * Infopocasi customclientraw.txt — LABELED WeatherDisplay JSON with
     * declared units (M1.6b-2 data fix, 22. 9. 2026). The old clientraw.txt
     * field indices turned out to be non-standard on this station
     * (clientraw[1] = 7.3 while the real temperature was 13.6 °C,
     * cross-checked against CHMU; clientraw[12] = indoor temp).
     */
    const val INFOPOCASI_URL = "https://infopocasi-olomouc.cz/customclientraw.txt"

    const val CHMU_BASE = "https://opendata.chmi.cz/meteorology/climate/now/data/"
    const val CHMU_STATION_CODE = "0-203-0-11742" // Olomouc, Holice (GH_ID O2OLOM01)

    /** A station measurement older than this is not used for widget synthesis (F2.5). */
    const val STALE_THRESHOLD_MIN = 30L

    /** Station identifiers stored in Room and used in UI badges (PRD data model). */
    const val STATION_INFOPOCASI = "INFOPOCASI"
    const val STATION_CHMU = "CHMU_HOLICE"

    /** Rate limiting: max 1 request per source per 10 min (F1.5). */
    const val FETCH_RATE_LIMIT_MS = 10L * 60 * 1000

    /** Room retention: keep only the last 72 h of fetched rows (Pavel 25. 9. 2026; enough for the 24/48/72 h graphs). */
    const val RETENTION_MS = 72L * 60 * 60 * 1000

    /** Daily CHMU data file URL for the given date (yyyyMMdd). */
    fun chmuDailyUrl(dateCompact: String): String =
        CHMU_BASE + "10m-" + CHMU_STATION_CODE + "-" + dateCompact + ".json"

    // --- Official web pages + licenses (M1.7-trend, Pavel 23. 9., F5.4) ------
    // The app must link the OFFICIAL pages of both sources, licenses visible
    // "podle standardů".

    /** Infopocasi station page (operator consent 23. 9. 2026; the operator's personal name is intentionally not shown — operator's request 25. 9. 2026). */
    const val INFOPOCASI_WEB = "https://infopocasi-olomouc.cz/meridla"

    /** Official CHMU station page for Olomouc-Holice (O2OLOM01). */
    const val CHMU_WEB =
        "https://www.chmi.cz/namerena-data/merici-stanice/meteorologicke/o2olom01-olomouc-holice"

    /** CHMU open data license (CC BY 4.0) — attribution must be visible. */
    const val CHMU_LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"
}
