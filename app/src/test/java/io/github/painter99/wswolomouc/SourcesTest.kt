package io.github.painter99.wswolomouc

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

    /**
     * M1.7-trend (Pavel 23. 9.): the app must link the OFFICIAL pages of both
     * data sources, with licenses visible "podle standardů" (F5.4 extended).
     */
    @Test
    fun sourceWebLinks_pointToOfficialPages() {
        assertEquals("https://infopocasi-olomouc.cz/meridla", Sources.INFOPOCASI_WEB)
        assertEquals(
            "https://www.chmi.cz/namerena-data/merici-stanice/meteorologicke/o2olom01-olomouc-holice",
            Sources.CHMU_WEB
        )
        assertEquals("https://creativecommons.org/licenses/by/4.0/", Sources.CHMU_LICENSE_URL)
    }
}
