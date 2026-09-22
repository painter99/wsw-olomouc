package io.github.painter99.wswolomouc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ChmuJsonParserTest {

    private val fixture = javaClass.classLoader!!
        .getResourceAsStream("chmu_10m_fixture.json")!!
        .bufferedReader().use { it.readText() }

    private val station = "0-203-0-11742"

    @Test
    fun `parses latest values at 20_00Z`() {
        val m = ChmuJsonParser.parse(fixture, station)
        assertNotNull(m)
        assertEquals(14.5f, m!!.temperatureC)
        assertEquals(72, m.humidityPct)
        assertEquals(4.1f, m.windSpeedMs)
        assertEquals(9.3f, m.windGustMs)
        assertEquals(57, m.windDirDeg)
        assertEquals(0.4f, m.rain10mMm)
    }

    @Test
    fun `daily rain is exact sum of all SRA10M rows of the day`() {
        // Fixture rows: 0.2 + 0.0 + 0.4 (Pavel 22. 9.: unified daily totals)
        val m = ChmuJsonParser.parse(fixture, station)!!
        assertEquals(0.6f, m.rainDailyMm!!, 0.001f)
        assertEquals(0.4f, m.rain10mMm) // latest 10-min value unchanged
    }

    @Test
    fun `daily rain skips empty VAL rows`() {
        val json = """
            {"data":{"data":{"values":[
              ["0-203-0-11742","SRA10M","2026-09-19T19:40:00Z","","",5.0],
              ["0-203-0-11742","SRA10M","2026-09-19T19:50:00Z",0.3,"",5.0],
              ["0-203-0-11742","SRA10M","2026-09-19T20:00:00Z",0.2,"",5.0]
            ]}}}
        """.trimIndent()
        val m = ChmuJsonParser.parse(json, station)!!
        assertEquals(0.5f, m.rainDailyMm!!, 0.001f)
        assertEquals(0.2f, m.rain10mMm)
    }

    @Test
    fun `no SRA10M rows means null daily rain`() {
        val json = """
            {"data":{"data":{"values":[
              ["0-203-0-11742","T","2026-09-19T20:00:00Z",14.5,"",5.0]
            ]}}}
        """.trimIndent()
        val m = ChmuJsonParser.parse(json, station)!!
        assertNull(m.rainDailyMm)
    }

    @Test
    fun `measuredAt matches DT of latest record`() {
        val m = ChmuJsonParser.parse(fixture, station)!!
        assertEquals(Instant.parse("2026-09-19T20:00:00Z").toEpochMilli(), m.measuredAtEpochMs)
    }

    @Test
    fun `ignores rows of other stations`() {
        val m = ChmuJsonParser.parse(fixture, station)!!
        // Decoy station 0-20000-0-11514 has T=21.3 — must not leak in.
        assertEquals(14.5f, m.temperatureC)
    }



    @Test
    fun `unknown station returns null`() {
        assertNull(ChmuJsonParser.parse(fixture, "0-203-0-99999"))
    }

    @Test
    fun `invalid json returns null`() {
        assertNull(ChmuJsonParser.parse("not json at all", station))
    }

    @Test
    fun `missing data node returns null`() {
        assertNull(ChmuJsonParser.parse("""{"data":{"other":1}}""", station))
    }

    @Test
    fun `row with malformed DT is skipped but others used`() {
        val json = """
            {"data":{"data":{"values":[
              ["0-203-0-11742","T","garbage",13.0,"",5.0],
              ["0-203-0-11742","T","2026-09-19T10:00:00Z",12.0,"",5.0]
            ]}}}
        """.trimIndent()
        val m = ChmuJsonParser.parse(json, station)
        assertNotNull(m)
        assertEquals(12.0f, m!!.temperatureC)
        assertEquals(Instant.parse("2026-09-19T10:00:00Z").toEpochMilli(), m.measuredAtEpochMs)
    }

    @Test
    fun `empty values array returns null`() {
        val json = """{"data":{"data":{"values":[]}}}"""
        assertNull(ChmuJsonParser.parse(json, station))
    }

    @Test
    fun `unsorted rows still pick latest per element`() {
        val json = """
            {"data":{"data":{"values":[
              ["0-203-0-11742","T","2026-09-19T18:00:00Z",11.0,"",5.0],
              ["0-203-0-11742","T","2026-09-19T16:00:00Z",10.0,"",5.0]
            ]}}}
        """.trimIndent()
        val m = ChmuJsonParser.parse(json, station)!!
        assertEquals(11.0f, m.temperatureC)
        assertEquals(Instant.parse("2026-09-19T18:00:00Z").toEpochMilli(), m.measuredAtEpochMs)
    }
}


