package com.fxwidget

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/** Fetches rates online and falls back to the last cached copy when offline. */
class RateRepository(context: Context) {
    private val prefs = context.getSharedPreferences("fx", Context.MODE_PRIVATE)

    fun cachedRates(): Map<String, Double>? =
        prefs.getString("rates", null)?.let { Converter.parseRates(it) }

    fun cachedAt(): Long = prefs.getLong("rates_at", 0L)

    /** Blocking network call; returns true if the cache was refreshed. */
    fun refresh(): Boolean = try {
        val conn = URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        if (Converter.parseRates(body) != null) {
            prefs.edit().putString("rates", body).putLong("rates_at", System.currentTimeMillis()).apply()
            true
        } else false
    } catch (e: Exception) {
        false
    }

    var amount: String
        get() = prefs.getString("amount", "100") ?: "100"
        set(v) = prefs.edit().putString("amount", v).apply()

    var from: String
        get() = prefs.getString("from", "THB") ?: "THB"
        set(v) = prefs.edit().putString("from", v).apply()

    var to: String
        get() = prefs.getString("to", "EUR") ?: "EUR"
        set(v) = prefs.edit().putString("to", v).apply()
}
