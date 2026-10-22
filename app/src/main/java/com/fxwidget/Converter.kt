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
        "B" -> current.dropLast(1).ifEmpty { "0" }
        "." -> if (current.contains('.')) current else "$current."
        else -> when {
            current == "0" -> key
            current.length >= 12 -> current
            current.substringAfter('.', "").length >= 2 && current.contains('.') -> current
            else -> current + key
        }
    }

    fun flag(currency: String): String = when (currency) {
        "THB" -> "🇹🇭"; "EUR" -> "🇪🇺"; "USD" -> "🇺🇸"; "GBP" -> "🇬🇧"; "CHF" -> "🇨🇭"
        "JPY" -> "🇯🇵"; "RUB" -> "🇷🇺"; "CNY" -> "🇨🇳"; "PLN" -> "🇵🇱"; "CZK" -> "🇨🇿"
        else -> "🏳️"
    }

    /** Four quick amounts under the keypad. */
    fun quickAmounts(currency: String): List<Int> = when (currency) {
        "THB" -> listOf(100, 500, 1000, 5000)
        "JPY", "RUB" -> listOf(1000, 5000, 10000, 50000)
        "CNY", "PLN", "CZK" -> listOf(50, 100, 500, 1000)
        else -> listOf(10, 20, 50, 100)
    }

    /** Typed amount with thousands grouping, keeping a trailing "." or decimals as typed. */
    fun displayInput(raw: String): String {
        val intPart = raw.substringBefore('.')
        val grouped = intPart.toLongOrNull()?.let {
            DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(it)
        } ?: intPart
        return if (raw.contains('.')) grouped + "." + raw.substringAfter('.') else grouped
    }

    /** Machine amount for re-entry: up to 2 decimals, no trailing zeros, no grouping. */
    fun plain(value: Double): String =
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    /** Big result number: 2 decimals, whole numbers from 10 000 up. */
    fun formatResult(value: Double): String {
        val pattern = if (value >= 10000) "#,##0" else "#,##0.00"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value)
    }

    /** Quick amounts shown on the compact widget, sized to the currency. */
    fun presets(currency: String): List<Int> = when (currency) {
        "THB" -> listOf(50, 100, 300, 500, 1000)
        "JPY", "RUB" -> listOf(500, 1000, 2000, 5000, 10000)
        "CNY", "PLN", "CZK" -> listOf(10, 50, 100, 200, 500)
        else -> listOf(5, 10, 20, 50, 100)
    }

    fun symbol(currency: String): String = when (currency) {
        "THB" -> "฿"; "EUR" -> "€"; "USD" -> "$"; "GBP" -> "£"; "JPY", "CNY" -> "¥"
        "RUB" -> "₽"; "PLN" -> "zł"; "CZK" -> "Kč"; "CHF" -> "Fr"
        else -> currency
    }

    /** Compact money format: whole numbers from 100 up, two decimals below. */
    fun formatShort(value: Double): String {
        val pattern = if (value >= 100) "#,##0" else "#,##0.00"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value)
    }

    fun format(value: Double): String {
        val pattern = if (value >= 1000) "#,##0.00" else "#,##0.00##"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value)
    }
}
