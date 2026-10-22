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

    /** Bumped on every swap; its parity picks the flipper child so the button animates. */
    var swapCount: Int
        get() = prefs.getInt("swap_count", 0)
        set(v) = prefs.edit().putInt("swap_count", v).apply()

    var from: String
        get() = prefs.getString("from", "THB") ?: "THB"
        set(v) = prefs.edit().putString("from", v).apply()

    var to: String
        get() = prefs.getString("to", "EUR") ?: "EUR"
        set(v) = prefs.edit().putString("to", v).apply()
}
