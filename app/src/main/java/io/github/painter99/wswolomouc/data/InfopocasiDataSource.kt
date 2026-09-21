package io.github.painter99.wswolomouc.data

import io.github.painter99.wswolomouc.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches and parses clientraw.txt from infopocasi-olomouc.cz (PRD F1.1).
 *
 * The server returns HTTP 401 without a User-Agent header (verified
 * 2026-09-19), so an identifying UA is always sent.
 */
class InfopocasiDataSource(
    private val client: OkHttpClient,
    private val url: String = Sources.INFOPOCASI_URL,
    private val clock: () -> Long = System::currentTimeMillis
) : StationDataSource {

    override val id: String = Sources.STATION_INFOPOCASI

    override suspend fun fetch(): StationMeasurement? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "WSW-Olomouc/0.1 (personal; +https://github.com/painter99/wsw-olomouc)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                ClientrawParser.parse(body)?.let {
                    StationMeasurementMapper.fromClientraw(it, fetchedAtMs = clock())
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
