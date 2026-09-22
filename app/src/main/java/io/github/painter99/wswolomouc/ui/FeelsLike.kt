package io.github.painter99.wswolomouc.ui

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Feels-like temperature (PRD F3.6) — pure function over measured values.
 *
 * Wind Chill: NWS/EC 2001 metric formula
 *   WC = 13.12 + 0.6215*T - 11.37*v^0.16 + 0.3965*T*v^0.16 (T °C, v km/h),
 *   applied for T <= 10 °C and v > 4.8 km/h.
 *
 * Heat Index: NWS Rothfusz regression (°F, RH%) with standard adjustments,
 * applied for T >= 27 °C; per the NWS rule, HI = T when the regression
 * result is below 80 °F.
 *
 * Outside these domains feels-like = measured temperature.
 *
 * Note (PRD F3.6): station wind may not be at 10 m height (Davis anemometer
 * typically 2-10 m) — the UI must label this "Pocitová", not an official
 * wind chill.
 */
object FeelsLike {

    fun calculate(temperatureC: Float, windKmh: Float?, humidityPct: Int?): Float {
        // Wind chill domain: T <= 10 °C, v > 4.8 km/h
        if (temperatureC <= 10f && windKmh != null && windKmh > 4.8f) {
            val v = windKmh.toDouble()
            val wc = 13.12 + 0.6215 * temperatureC -
                11.37 * v.pow(0.16) + 0.3965 * temperatureC * v.pow(0.16)
            return wc.toFloat()
        }
        // Heat index domain: T >= 27 °C (NWS chart domain starts at 80 °F)
        if (temperatureC >= 27f && humidityPct != null) {
            val tF = temperatureC * 9f / 5f + 32f
            val rh = humidityPct.toFloat()
            var hi = -42.379f +
                2.04901523f * tF +
                10.14333127f * rh -
                0.22475541f * tF * rh -
                0.00683783f * tF * tF -
                0.05481717f * rh * rh +
                0.00122874f * tF * tF * rh +
                0.00085282f * tF * rh * rh -
                0.00000199f * tF * tF * rh * rh
            if (rh < 13f && tF in 80f..112f) {
                hi -= ((13f - rh) / 4f) * sqrt(((17f - abs(tF - 95f)) / 17f))
            }
            if (rh > 85f && tF in 80f..87f) {
                hi += ((rh - 85f) / 10f) * ((87f - tF) / 5f)
            }
            if (hi < 80f) return temperatureC
            return (hi - 32f) * 5f / 9f
        }
        return temperatureC
    }
}