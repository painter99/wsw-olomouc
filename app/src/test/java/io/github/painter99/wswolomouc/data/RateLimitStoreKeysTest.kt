package io.github.painter99.wswolomouc.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prove-It test for the M1.7b persistent rate-limit bug (Pavel 24. 9. 2026,
 * live: the widget refresh button could bypass the 10-min limit right after
 * the app process was killed and reopened).
 *
 * Root cause: [DataStoreRateLimitStore.put] writes keys WITH the "rl_" prefix
 * while the repository looks sources up WITHOUT it, so a fresh process loaded
 * an empty-looking map and the limit silently reset on every process death.
 *
 * RED: the mapping function under test does not exist yet.
 */
class RateLimitStoreKeysTest {

    @Test
    fun storeKeyWithPrefix_mapsToSourceId() {
        assertEquals(
            "INFOPOCASI",
            DataStoreRateLimitStore.storeKeyToSource("rl_INFOPOCASI")
        )
    }

    @Test
    fun storeKeyWithoutPrefix_mapsUnchanged() {
        assertEquals(
            "CHMU_HOLICE",
            DataStoreRateLimitStore.storeKeyToSource("CHMU_HOLICE")
        )
    }
}
