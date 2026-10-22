package com.fxwidget

import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Pure conversion/input logic, independent of Android APIs. */
object Converter {
    val CURRENCIES = listOf("THB", "EUR", "USD", "GBP", "CHF", "JPY", "RUB", "CNY", "PLN", "CZK")

    /** Rates are quoted against USD (units of currency per 1 USD). */
    fun convert(amount: Double, from: String, to: String, rates: Map<String, Double>): Double? {
        val f = rates[from] ?: return null
        val t = rates[to] ?: return null
        if (f == 0.0) return null
        return amount / f * t
    }

    fun parseRates(json: String): Map<String, Double>? = try {
        val obj = JSONObject(json)
        if (obj.optString("result") != "success") null
        else {
            val r = obj.getJSONObject("rates")
            val map = r.keys().asSequence().associateWith { r.getDouble(it) }
            map.ifEmpty { null }
        }
    } catch (e: Exception) {
        null
    }

    /** Applies a keypad key ("0".."9", ".", "C") to the current amount text. */
    fun applyKey(current: String, key: String): String = when (key) {
        "C" -> "0"
        "." -> if (current.contains('.')) current else "$current."
        else -> when {
            current == "0" -> key
            current.length >= 12 -> current
            current.substringAfter('.', "").length >= 2 && current.contains('.') -> current
            else -> current + key
        }
    }

    fun format(value: Double): String {
        val pattern = if (value >= 1000) "#,##0.00" else "#,##0.00##"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value)
    }
}
