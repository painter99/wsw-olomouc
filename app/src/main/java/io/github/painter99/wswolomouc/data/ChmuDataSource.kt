package io.github.painter99.wswolomouc.data

import io.github.painter99.wswolomouc.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fetches and parses the CHMU OpenData 10M daily JSON file (PRD F1.2).
 *
 * The file name contains the day (Europe/Prague); the URL is built through
 * [urlForDate] so tests can point it at a mock server.
 */
class ChmuDataSource(
    private val client: OkHttpClient,
    private val urlForDate: (String) -> String = Sources::chmuDailyUrl,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.of("Europe/Prague")
) : StationDataSource {

    override val id: String = Sources.STATION_CHMU

    override suspend fun fetch(): StationMeasurement? = withContext(Dispatchers.IO) {
        try {
            val dateCompact = LocalDate.now(zone).format(DateTimeFormatter.BASIC_ISO_DATE)
            val request = Request.Builder().url(urlForDate(dateCompact)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                ChmuJsonParser.parse(body, Sources.CHMU_STATION_CODE)?.let {
                    StationMeasurementMapper.fromChmu(it, fetchedAtMs = clock())
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
