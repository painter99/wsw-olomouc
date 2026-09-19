package io.github.painter99.wswolomouc

/**
 * Verified data source endpoints (PRD F1.1, F1.2).
 * Live-verified 2026-09-19.
 */
object Sources {
    const val INFOPOCASI_URL = "https://infopocasi-olomouc.cz/clientraw.txt"

    const val CHMU_BASE = "https://opendata.chmi.cz/meteorology/climate/now/data/"
    const val CHMU_STATION_CODE = "0-203-0-11742" // Olomouc, Holice (GH_ID O2OLOM01)

    /** A station measurement older than this is not used for widget synthesis (F2.5). */
    const val STALE_THRESHOLD_MIN = 30L

    /** Daily CHMU data file URL for the given date (yyyyMMdd). */
    fun chmuDailyUrl(dateCompact: String): String =
        CHMU_BASE + "10m-" + CHMU_STATION_CODE + "-" + dateCompact + ".json"
}
