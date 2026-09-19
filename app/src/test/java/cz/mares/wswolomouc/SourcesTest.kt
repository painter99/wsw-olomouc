package cz.mares.wswolomouc

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourcesTest {

    @Test
    fun chmuDailyUrl_containsStationCodeAndDate() {
        val url = Sources.chmuDailyUrl("20260919")
        assertTrue(url.startsWith(Sources.CHMU_BASE))
        assertTrue(url.contains("10m-0-203-0-11742-20260919.json"))
    }

    @Test
    fun staleThresholdIs30Minutes() {
        assertEquals(30L, Sources.STALE_THRESHOLD_MIN)
    }
}
