package com.thaimer.bfour.core

import java.util.Locale
import kotlin.math.roundToInt

enum class TempUnit(val symbol: String) {
    C("°C"),
    F("°F");

    fun fromCelsius(c: Double): Double = if (this == C) c else c * 9.0 / 5.0 + 32.0

    fun toCelsius(v: Double): Double = if (this == C) v else (v - 32.0) * 5.0 / 9.0

    /** Whole degrees in this unit, as the thermometer expects for targets. */
    fun wholeDegrees(celsius: Double): Int = fromCelsius(celsius).roundToInt()

    /** "54.3°" — one decimal, which is the sensor resolution. */
    fun format(celsius: Double): String =
        String.format(Locale.getDefault(), "%.1f°", fromCelsius(celsius))

    fun formatWhole(celsius: Double): String = "${wholeDegrees(celsius)}°"
}
