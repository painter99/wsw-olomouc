package io.github.painter99.wswolomouc.data

import io.github.painter99.wswolomouc.Sources
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * InfopocasiDataSource integration tests over MockWebServer — no real network.
 * Fixture = real station data captured 2026-09-19 (same file as parser tests).
 */
class InfopocasiDataSourceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(): String =
        javaClass.getResourceAsStream("/clientraw_fixture.txt")!!
            .bufferedReader().use { it.readText() }

    private fun dataSource(): InfopocasiDataSource =
        InfopocasiDataSource(OkHttpClient(), url = server.url("/clientraw.txt").toString())

    @Test
    fun fetch_parsesFixtureIntoUnifiedMeasurement() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))

        val m = dataSource().fetch()

        assertNotNull(m)
        assertEquals(Sources.STATION_INFOPOCASI, m!!.station)
        assertEquals(2.7f, m.temperatureC!!, 0.01f)    // values verified in M1.2 parser tests
        assertEquals(1022.8f, m.pressureHpa!!, 0.01f)
        assertTrue(m.measuredAtMs > 0)
        assertTrue(m.fetchedAtMs > 0)
    }

    @Test
    fun fetch_sendsUserAgentHeader() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        dataSource().fetch()
        val recorded = server.takeRequest()
        assertTrue(recorded.getHeader("User-Agent")!!.startsWith("WSW-Olomouc"))
    }

    @Test
    fun fetch_http500_returnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(dataSource().fetch())
    }

    @Test
    fun fetch_garbageBody_returnsNull() = runTest {
        server.enqueue(MockResponse().setBody("not a clientraw file"))
        assertNull(dataSource().fetch())
    }
}
