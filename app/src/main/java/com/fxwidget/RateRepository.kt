package com.fxwidget

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Fetches rates online (two sources) and falls back to the last cached copy when offline. */
class RateRepository(context: Context) {
    private val prefs = context.getSharedPreferences("fx", Context.MODE_PRIVATE)

    fun cachedRates(): Map<String, Double>? =
        prefs.getString("rates", null)?.let { Converter.parseRates(it) }

    fun cachedAt(): Long = prefs.getLong("rates_at", 0L)

    /** Short reason of the last failed refresh, null if the last refresh succeeded. */
    val lastError: String? get() = prefs.getString("last_error", null)

    /** Blocking network call; returns true if the cache was refreshed. */
    fun refresh(): Boolean {
        val errors = mutableListOf<String>()
        for (source in listOf(::fromErApi, ::fromFrankfurter)) {
            try {
                val json = source()
                if (Converter.parseRates(json) != null) {
                    prefs.edit()
                        .putString("rates", json)
                        .putLong("rates_at", System.currentTimeMillis())
                        .remove("last_error")
                        .apply()
                    return true
                }
                errors += "bad data"
            } catch (e: Exception) {
                errors += e.javaClass.simpleName
            }
        }
        prefs.edit().putString("last_error", errors.distinct().joinToString(", ")).apply()
        return false
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun fromErApi(): String = get("https://open.er-api.com/v6/latest/USD")

    /** ECB data via Frankfurter, normalised to the er-api shape the cache expects. */
    private fun fromFrankfurter(): String {
        val src = JSONObject(get("https://api.frankfurter.dev/v1/latest?base=USD")).getJSONObject("rates")
        src.put("USD", 1.0)
        return JSONObject().put("result", "success").put("rates", src).toString()
    }

    var amount: String
        get() = prefs.getString("amount", "100") ?: "100"
        set(v) = prefs.edit().putString("amount", v).apply()

    // ---- bank vs fixed (exchange office) rate --------------------------------------

    /** "bank" = live rate, "fix" = the user's exchange-office rate where one is set. */
    var rateMode: String
        get() = prefs.getString("rate_mode", "bank") ?: "bank"
        set(v) = prefs.edit().putString("rate_mode", v).apply()

    var fixedRates: Map<String, Double>
        get() = prefs.getString("fixed_rates", null)?.let { json ->
            try {
                val o = JSONObject(json)
                o.keys().asSequence().associateWith { o.getDouble(it) }
            } catch (e: Exception) { emptyMap() }
        } ?: emptyMap()
        set(v) = prefs.edit().putString("fixed_rates", JSONObject(v).toString()).apply()

    fun setFixed(from: String, to: String, perFrom: Double) {
        fixedRates = Converter.withFixed(fixedRates, from, to, perFrom)
    }

    fun clearFixed(a: String, b: String) {
        fixedRates = fixedRates - Converter.pairKey(a, b)
    }

    fun bankRate(from: String = this.from, to: String = this.to): Double? =
        cachedRates()?.let { Converter.convert(1.0, from, to, it) }

    fun fixedRate(from: String = this.from, to: String = this.to): Double? =
        Converter.fixedRate(from, to, fixedRates)

    /** True when the fixed rate is actually being used for this pair. */
    fun usingFixed(from: String = this.from, to: String = this.to): Boolean =
        rateMode == "fix" && fixedRate(from, to) != null

    /** Effective rate (units of [to] per 1 [from]) honouring the bank/fixed mode. */
    fun rate(from: String = this.from, to: String = this.to): Double? =
        if (usingFixed(from, to)) fixedRate(from, to) else bankRate(from, to)

    fun convert(amount: Double, from: String = this.from, to: String = this.to): Double? =
        rate(from, to)?.let { amount * it }

    /** Selected widget theme id (see Themes). */
    var themeId: String
        get() = prefs.getString("theme", "base") ?: "base"
        set(v) = prefs.edit().putString("theme", v).apply()

    /** Currencies offered in the picker; set in the app's settings. Default: all. */
    var enabledCurrencies: Set<String>
        get() = prefs.getStringSet("enabled_currencies", null)?.toSet() ?: Converter.CURRENCIES.toSet()
        set(v) = prefs.edit().putStringSet("enabled_currencies", v).apply()

    /** Picker list in canonical order: enabled ones plus whatever is selected right now. */
    fun pickerCurrencies(): List<Converter.Currency> {
        val keep = enabledCurrencies + from + to
        return Converter.ALL.filter { it.code in keep }
    }

    var from: String
        get() = prefs.getString("from", "THB") ?: "THB"
        set(v) = prefs.edit().putString("from", v).apply()

    var to: String
        get() = prefs.getString("to", "EUR") ?: "EUR"
        set(v) = prefs.edit().putString("to", v).apply()
}
