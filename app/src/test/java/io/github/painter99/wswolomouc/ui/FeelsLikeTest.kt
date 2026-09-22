package io.github.painter99.wswolomouc.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Feels-like temperature tests (M1.6b-1, PRD F3.6).
 *
 * Reference values:
 * - Wind Chill: NWS/EC 2001 metric formula
 *   WC = 13.12 + 0.6215*T - 11.37*v^0.16 + 0.3965*T*v^0.16 (T °C, v km/h),
 *   valid for T <= 10 °C and v > 4.8 km/h (NWS wind chill chart cross-checked).
 * - Heat Index: NWS Rothfusz regression (°F, RH%) with standard adjustments,
 *   HI = T when the regression result is below 80 °F (NWS rule).
 *   Chart cross-check: 96 °F / 65 % -> 121 °F (NWS heat safety page).
 *
 * Domain (PRD F3.6): applies Wind Chill for T <= 10 °C and v > 4.8 km/h,
 * Heat Index for T >= 27 °C (regression >= 80 °F), otherwise feels-like = T.
 * Outside the HI temperature domain the result must equal T even for high RH
 * (the Rothfusz regression is only meaningful in the NWS chart domain).
 */
class FeelsLikeTest {

    // ---- Wind Chill (T <= 10 °C, v > 4.8 km/h) ----

    @Test
    fun windChill_nwsChartValues() {
        // Formula cross-check (rounded to 0.1 °C), consistent with the NWS chart:
        assertEquals(-13.0f, FeelsLike.calculate(temperatureC = -5f, windKmh = 30f, humidityPct = null)!!, 0.1f)
        assertEquals(-5.2f, FeelsLike.calculate(temperatureC = 0f, windKmh = 20f, humidityPct = null)!!, 0.1f)
        assertEquals(1.7f, FeelsLike.calculate(temperatureC = 5f, windKmh = 15f, humidityPct = null)!!, 0.1f)
        assertEquals(-21.8f, FeelsLike.calculate(temperatureC = -10f, windKmh = 50f, humidityPct = null)!!, 0.1f)
    }

    @Test
    fun windChill_coldCalmWind_returnsTemperature() {
        // v <= 4.8 km/h -> wind chill not applicable
        assertEquals(5f, FeelsLike.calculate(5f, windKmh = 4.8f, humidityPct = null))
        assertEquals(2f, FeelsLike.calculate(2f, windKmh = 3f, humidityPct = null))
    }

    @Test
    fun windChill_boundaryTenDegrees() {
        // T <= 10 °C applies; above 10 °C it must not
        val at10 = FeelsLike.calculate(10f, windKmh = 20f, humidityPct = null)!!
        assertEquals(9.8f, at10, 0.05f)
        assertEquals(15f, FeelsLike.calculate(15f, windKmh = 20f, humidityPct = null))
    }

    // ---- Heat Index (T >= 27 °C) ----

    @Test
    fun heatIndex_nwsChartValues() {
        // NWS heat safety page example: 96 °F (35.6 °C) / 65 % -> 121 °F (49.4 °C)
        assertEquals(49.4f, FeelsLike.calculate(temperatureC = 35.5556f, windKmh = 5f, humidityPct = 65)!!, 0.3f)
        // 30 °C (86 °F) / 85 % -> ~39 °C (chart region)
        assertEquals(39.1f, FeelsLike.calculate(temperatureC = 30f, windKmh = 5f, humidityPct = 85)!!, 0.3f)
    }

    @Test
    fun heatIndex_belowRegressionThreshold_returnsTemperature() {
        // Regression result < 80 °F -> HI = T (NWS rule), even at high humidity
        assertEquals(27f, FeelsLike.calculate(27f, windKmh = 5f, humidityPct = 70))
        assertEquals(26f, FeelsLike.calculate(26f, windKmh = 5f, humidityPct = 90))
    }

    @Test
    fun heatIndex_moderateTemperatureHighHumidity() {
        // 29 °C (84.2 °F) / 70 % -> ~32.7 °C (90.9 °F)
        assertEquals(32.7f, FeelsLike.calculate(temperatureC = 29f, windKmh = 5f, humidityPct = 70)!!, 0.3f)
    }

    // ---- Neutral zone / nulls ----

    @Test
    fun neutralZone_returnsTemperature() {
        // Between the two domains: 10 < T < 27 -> feels-like = T regardless of wind/RH
        assertEquals(18f, FeelsLike.calculate(18f, windKmh = 30f, humidityPct = 50))
        assertEquals(12f, FeelsLike.calculate(12f, windKmh = 40f, humidityPct = 80))
    }

    @Test
    fun nullWindOrHumidity_fallsBackToTemperature() {
        // Missing wind -> no wind chill; missing humidity -> no heat index
        assertEquals(5f, FeelsLike.calculate(5f, windKmh = null, humidityPct = 60))
        assertEquals(30f, FeelsLike.calculate(30f, windKmh = 20f, humidityPct = null))
        assertEquals(-3f, FeelsLike.calculate(-3f, windKmh = null, humidityPct = null))
    }
}