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
 * ChmuDataSource integration tests over MockWebServer — no real network.
 * Fixture = real CHMU 10M payload captured 2026-09-19 (same file as M1.3 tests).
 */
class ChmuDataSourceTest {

    private lateinit var server: MockWebServer
    private val requestedDates = mutableListOf<String>()

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
        javaClass.getResourceAsStream("/chmu_10m_fixture.json")!!
            .bufferedReader().use { it.readText() }

    /** Route the daily-file URL to the mock server, keep the date in the path. */
    private fun dataSource() = ChmuDataSource(
        client = OkHttpClient(),
        urlForDate = { dateCompact ->
            requestedDates.add(dateCompact)
            server.url("/10m-$dateCompact.json").toString()
        }
    )

    @Test
    fun fetch_parsesFixtureIntoUnifiedMeasurement() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))

        val m = dataSource().fetch()

        assertNotNull(m)
        assertEquals(Sources.STATION_CHMU, m!!.station)
        assertEquals(14.5f, m.temperatureC!!, 0.01f)   // values verified in M1.3 parser tests
        assertNull(m.pressureHpa)                       // 10M feed has no pressure
        assertTrue(m.windMs!! < 10f)                    // m/s, not km/h
    }

    @Test
    fun fetch_requestsTodaysDailyFile() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        dataSource().fetch()
        val date = requestedDates.single()
        assertTrue("date must be yyyyMMdd, was: $date", date.matches(Regex("\\d{8}")))
    }

    @Test
    fun fetch_http404_returnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(dataSource().fetch())
    }

    @Test
    fun fetch_malformedJson_returnsNull() = runTest {
        server.enqueue(MockResponse().setBody("<html>oops</html>"))
        assertNull(dataSource().fetch())
    }

    // --- M1.6b-3: detailed fetch outcome -----------------------------------

    @Test
    fun fetchResult_success_wrapsMeasurement() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))

        val r = dataSource().fetchResult()

        assertTrue("was $r", r is FetchResult.Success)
        assertEquals(14.5f, (r as FetchResult.Success).measurement.temperatureC!!, 0.01f)
    }

    @Test
    fun fetchResult_http404_reportsHttpErrorWithCode() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val r = dataSource().fetchResult()

        assertTrue("was $r", r is FetchResult.HttpError)
        assertEquals(404, (r as FetchResult.HttpError).code)
    }

    @Test
    fun fetchResult_malformedJson_reportsParseError() = runTest {
        server.enqueue(MockResponse().setBody("<html>oops</html>"))

        val r = dataSource().fetchResult()

        assertTrue("was $r", r is FetchResult.ParseError)
    }

    @Test
    fun fetchResult_networkFailure_reportsNetworkError() = runTest {
        // Port 1 on localhost: connection refused, no server involved.
        val unreachable = ChmuDataSource(
            client = OkHttpClient(),
            urlForDate = { dateCompact -> "http://127.0.0.1:1/10m-$dateCompact.json" }
        )

        val r = unreachable.fetchResult()

        assertTrue("was $r", r is FetchResult.NetworkError)
    }
}
