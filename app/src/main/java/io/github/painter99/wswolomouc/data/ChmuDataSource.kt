package io.github.painter99.wswolomouc.data

import io.github.painter99.wswolomouc.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Fetches and parses the CHMU OpenData 10M daily JSON file (PRD F1.2).
 *
 * CHMU names the daily files by the UTC date (rows carry UTC "Z" stamps,
 * live-verified 24. 9. 2026) — the file name is built from
 * [todayUtc], never from a local zone (the old Europe/Prague name caused a
 * guaranteed nightly 404 between local midnight and ~02:00).
 *
 * 404 fallback (M1.7b, Pavel 24. 9. 2026): the new UTC day's 10m file is
 * published only hours into the UTC day (live-verified — the 1h files
 * appear ~01:20 UTC, the 10m file later). When today's file returns 404,
 * yesterday's UTC file is fetched instead — it still holds the newest rows
 * and the parser picks the latest value per element.
 *
 * The URL is built through [urlForDate] so tests can point it at a mock
 * server.
 */
class ChmuDataSource(
    private val client: OkHttpClient,
    private val urlForDate: (String) -> String = Sources::chmuDailyUrl,
    private val clock: () -> Long = System::currentTimeMillis,
    private val todayUtc: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) }
) : StationDataSource {

    override val id: String = Sources.STATION_CHMU

    override suspend fun fetch(): StationMeasurement? =
        (fetchResult() as? FetchResult.Success)?.measurement

    override suspend fun fetchResult(): FetchResult = withContext(Dispatchers.IO) {
        try {
            val today = todayUtc()
            val first = fetchDate(today)
            if (first !is FetchResult.HttpError || first.code != 404) {
                return@withContext first
            }
            val yesterday = fetchDate(today.minusDays(1))
            if (yesterday is FetchResult.Success) yesterday else first
        } catch (e: Exception) {
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun fetchDate(date: LocalDate): FetchResult = withContext(Dispatchers.IO) {
        try {
            val dateCompact = date.format(DateTimeFormatter.BASIC_ISO_DATE)
            val request = Request.Builder().url(urlForDate(dateCompact)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext FetchResult.HttpError(response.code)
                val body = response.body?.string()
                    ?: return@withContext FetchResult.NetworkError("empty response body")
                val parsed = ChmuJsonParser.parse(body, Sources.CHMU_STATION_CODE)
                    ?: return@withContext FetchResult.ParseError(
                        "no usable rows in daily 10M JSON"
                    )
                FetchResult.Success(
                    StationMeasurementMapper.fromChmu(parsed, fetchedAtMs = clock())
                )
            }
        } catch (e: Exception) {
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }
}