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

    // ---- fixed (exchange-office) rates -------------------------------------------
    // Stored once per pair as "A/B" -> units of B per 1 A, with A < B alphabetically,
    // so "1 € = 36.50 ฿" and "1 ฿ = 0.0274 €" are the same entry.

    fun pairKey(a: String, b: String): String = if (a < b) "$a/$b" else "$b/$a"

    /** Units of [to] per 1 [from] from the fixed table, or null if this pair has none. */
    fun fixedRate(from: String, to: String, fixed: Map<String, Double>): Double? {
        if (from == to) return 1.0
        val v = fixed[pairKey(from, to)]?.takeIf { it > 0 } ?: return null
        return if (from < to) v else 1 / v
    }

    /** Stores "1 [from] = [perFrom] [to]". */
    fun withFixed(fixed: Map<String, Double>, from: String, to: String, perFrom: Double): Map<String, Double> =
        fixed + (pairKey(from, to) to if (from < to) perFrom else 1 / perFrom)

    /** Rate for editing: up to 4 decimals, no trailing zeros (36.5, 0.0274, 25800). */
    fun plainRate(value: Double): String =
        String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')

    /** Readable direction for a rate (units of [to] per 1 [from]): the side where 1 unit is worth ≥ 1 of the other. */
    fun bigSide(from: String, to: String, rate: Double?): Pair<String, String> =
        if ((rate ?: 1.0) >= 1) from to to else to to from

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

    /**
     * Quick add-amounts under the keypad, scaled to how "heavy" the currency is:
     * picked by units per 1 USD from the live rates (fallback table if none cached yet).
     */
    fun quickAmounts(currency: String, rates: Map<String, Double>?): List<Int> {
        val perUsd = rates?.get(currency) ?: FALLBACK_PER_USD[currency] ?: 1.0
        return when {
            perUsd < 2 -> listOf(1, 2, 5, 10, 20, 50)                        // EUR, USD, GBP, CHF…
            perUsd < 20 -> listOf(1, 5, 10, 50, 100, 500)                    // PLN, CNY, MYR, AED, kroner
            perUsd < 100 -> listOf(10, 50, 100, 500, 1000, 5000)             // THB, RUB, INR, TRY, CZK
            perUsd < 500 -> listOf(100, 500, 1000, 5000, 10000, 50000)       // JPY, HUF
            perUsd < 5000 -> listOf(1000, 5000, 10000, 50000, 100000, 500000) // KRW
            else -> listOf(10000, 50000, 100000, 200000, 500000, 1000000)    // VND, IDR
        }
    }

    /** Rough units per USD, only used before the first rate download. */
    private val FALLBACK_PER_USD = mapOf(
        "THB" to 34.0, "EUR" to 0.9, "USD" to 1.0, "GBP" to 0.78, "RUB" to 80.0, "PLN" to 3.9,
        "CNY" to 7.2, "JPY" to 150.0, "KRW" to 1400.0, "VND" to 25000.0, "MYR" to 4.5,
        "SGD" to 1.3, "IDR" to 16000.0, "INR" to 85.0, "AED" to 3.67, "TRY" to 40.0,
        "CHF" to 0.85, "CZK" to 22.0, "SEK" to 10.5, "NOK" to 10.5, "DKK" to 6.7,
        "HUF" to 360.0, "AUD" to 1.5, "CAD" to 1.4,
    )

    /** Chip label: "+1,000" style grouping, short "k" form for very large steps. */
    fun quickLabel(value: Int): String = when {
        value >= 1_000_000 -> "+${value / 1_000_000}M"
        value >= 10_000 -> "+${value / 1000}k"
        else -> "+" + displayInput(value.toString())
    }

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
