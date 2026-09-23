package io.github.painter99.wswolomouc.sync

/**
 * Pure rules for the periodic background sync (M1.7, PRD G5/F2.3).
 *
 * WorkManager enforces a minimum periodic interval of 15 minutes; anything
 * shorter (or non-positive) is clamped up to the minimum so the requested
 * schedule always stays valid. The flex window stays at 5 minutes, which is
 * always valid for the supported interval range (>= 15 min).
 *
 * The user-selectable interval (F5.2, 10/15/30 min) arrives with Settings;
 * a 10-minute request is clamped to 15 until then.
 */
object SyncSchedule {

    /** WorkManager minimum for periodic work (androidx.work constant). */
    const val MIN_INTERVAL_MINUTES = 15L

    /** Default interval — 15 min satisfies G5 (10-15 min) and NF1 (<1 %/day). */
    const val DEFAULT_INTERVAL_MINUTES = 15L

    /** Flex window — how early the job may start within the interval. */
    const val FLEX_MINUTES = 5L

    /**
     * Phase offset of the SECOND periodic worker (round 4, Pavel 23. 9.).
     * WorkManager's periodic minimum is a hard 15-min floor — a 5/10-min
     * periodic is impossible. Two 15-min periodics offset by 7 min halve the
     * effective gap; the repository rate limit (10 min per source, F1.5)
     * dedupes actual fetches, so CHMU is caught at most ~10 min after its
     * hourly publication.
     */
    const val PHASE_OFFSET_MINUTES = 7L

    /** Normalized schedule parameters, ready for a PeriodicWorkRequest. */
    data class Spec(val intervalMinutes: Long, val flexMinutes: Long)

    fun spec(intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES): Spec =
        Spec(
            intervalMinutes = maxOf(MIN_INTERVAL_MINUTES, intervalMinutes),
            flexMinutes = FLEX_MINUTES
        )
}
