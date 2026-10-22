package com.fxwidget

import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Pure conversion/input logic, independent of Android APIs. */
object Converter {
    data class Currency(val code: String, val flag: String, val symbol: String, val name: String)

    /** Picker order: the usual suspects first, then Asia, then the rest of Europe and beyond. */
    val ALL = listOf(
        Currency("THB", "🇹🇭", "฿", "Тайский бат"),
        Currency("EUR", "🇪🇺", "€", "Евро"),
        Currency("USD", "🇺🇸", "$", "Доллар США"),
        Currency("GBP", "🇬🇧", "£", "Фунт стерлингов"),
        Currency("RUB", "🇷🇺", "₽", "Российский рубль"),
        Currency("PLN", "🇵🇱", "zł", "Польский злотый"),
        Currency("CNY", "🇨🇳", "¥", "Китайский юань"),
        Currency("JPY", "🇯🇵", "¥", "Японская иена"),
        Currency("KRW", "🇰🇷", "₩", "Южнокорейская вона"),
        Currency("VND", "🇻🇳", "₫", "Вьетнамский донг"),
        Currency("MYR", "🇲🇾", "RM", "Малайзийский ринггит"),
        Currency("SGD", "🇸🇬", "S$", "Сингапурский доллар"),
        Currency("IDR", "🇮🇩", "Rp", "Индонезийская рупия"),
        Currency("INR", "🇮🇳", "₹", "Индийская рупия"),
        Currency("AED", "🇦🇪", "AED", "Дирхам ОАЭ"),
        Currency("TRY", "🇹🇷", "₺", "Турецкая лира"),
        Currency("CHF", "🇨🇭", "Fr", "Швейцарский франк"),
        Currency("CZK", "🇨🇿", "Kč", "Чешская крона"),
        Currency("SEK", "🇸🇪", "kr", "Шведская крона"),
        Currency("NOK", "🇳🇴", "kr", "Норвежская крона"),
        Currency("DKK", "🇩🇰", "kr", "Датская крона"),
        Currency("HUF", "🇭🇺", "Ft", "Венгерский форинт"),
        Currency("AUD", "🇦🇺", "A$", "Австралийский доллар"),
        Currency("CAD", "🇨🇦", "C$", "Канадский доллар"),
    )
    val CURRENCIES = ALL.map { it.code }
    private val BY_CODE = ALL.associateBy { it.code }

    fun name(currency: String): String = BY_CODE[currency]?.name ?: currency

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

    fun flag(currency: String): String = BY_CODE[currency]?.flag ?: "🏳️"

    /** Quick add-amounts under the keypad (same for every currency). */
    val QUICK_AMOUNTS = listOf(1, 10, 50, 100, 500, 1000)

    /** Typed amount with thousands grouping, keeping a trailing "." or decimals as typed. */
    fun displayInput(raw: String): String {
        val intPart = raw.substringBefore('.')
        val grouped = intPart.toLongOrNull()?.let {
            DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(it)
        } ?: intPart
        return if (raw.contains('.')) grouped + "." + raw.substringAfter('.') else grouped
    }

    /** Quick-amount tap: adds to what's there (100 + 100 + 100 = 300). */
    fun addAmount(current: String, add: Int): String =
        plain((current.toDoubleOrNull() ?: 0.0) + add)

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
        "JPY", "RUB", "INR" -> listOf(500, 1000, 2000, 5000, 10000)
        "KRW", "HUF" -> listOf(1000, 5000, 10000, 50000, 100000)
        "VND", "IDR" -> listOf(10000, 50000, 100000, 500000, 1000000)
        "CNY", "PLN", "CZK", "SEK", "NOK", "DKK", "TRY", "AED", "MYR" -> listOf(10, 50, 100, 200, 500)
        else -> listOf(5, 10, 20, 50, 100)
    }

    fun symbol(currency: String): String = BY_CODE[currency]?.symbol ?: currency

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
