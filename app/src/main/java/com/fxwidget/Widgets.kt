package com.fxwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import kotlin.concurrent.thread

/** Shared helpers for both widgets: actions, status line, refreshing every widget at once. */
object Widgets {
    const val ACTION_KEY = "com.fxwidget.KEY_"
    const val ACTION_SET = "com.fxwidget.SET_"
    const val ACTION_SWAP = "com.fxwidget.SWAP"
    const val ACTION_FROM = "com.fxwidget.FROM"
    const val ACTION_TO = "com.fxwidget.TO"
    const val ACTION_REFRESH = "com.fxwidget.REFRESH"

    fun updateAll(ctx: Context, swapped: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(ctx)
        mgr.getAppWidgetIds(ComponentName(ctx, FxWidgetProvider::class.java))
            .takeIf { it.isNotEmpty() }?.let { FxWidgetProvider.render(ctx, mgr, it, swapped) }
        mgr.getAppWidgetIds(ComponentName(ctx, PresetWidgetProvider::class.java))
            .takeIf { it.isNotEmpty() }?.let { PresetWidgetProvider.render(ctx, mgr, it) }
    }

    fun broadcast(ctx: Context, cls: Class<*>, action: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, (cls.name + action).hashCode(),
        Intent(ctx, cls).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun picker(ctx: Context, side: String): PendingIntent = PendingIntent.getActivity(
        ctx, side.hashCode(),
        CurrencyPickerActivity.intent(ctx, side).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun rateLine(repo: RateRepository, rates: Map<String, Double>?): String {
        if (rates == null) {
            val err = repo.lastError?.let { " ($it)" } ?: ""
            return "Нет курса — нажми ⟳ или открой приложение$err"
        }
        // Show the "big" side: 1 € = 37.74 ฿ reads better than 1 ฿ = 0.0265 €
        val (a, b) = if ((Converter.convert(1.0, repo.from, repo.to, rates) ?: 0.0) >= 1)
            repo.from to repo.to else repo.to to repo.from
        val v = Converter.convert(1.0, a, b, rates) ?: 0.0
        val age = DateUtils.getRelativeTimeSpanString(
            repo.cachedAt(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        )
        val stale = if (repo.lastError != null) " · офлайн" else ""
        return "1 ${Converter.symbol(a)} = ${Converter.formatShort(v)} ${Converter.symbol(b)} · $age$stale"
    }

    private fun next(cur: String, skip: String): String {
        val l = Converter.CURRENCIES
        var i = l.indexOf(cur)
        do i = (i + 1) % l.size while (l[i] == skip)
        return l[i]
    }

    /** Handles a widget action; returns false if the action isn't ours. */
    fun handle(ctx: Context, action: String, pending: AppWidgetProvider): Boolean {
        val repo = RateRepository(ctx)
        when {
            action.startsWith(ACTION_SET) ->
                repo.amount = Converter.addAmount(repo.amount, action.removePrefix(ACTION_SET).toInt())
            action.startsWith(ACTION_KEY) ->
                repo.amount = Converter.applyKey(repo.amount, action.removePrefix(ACTION_KEY))
            action == ACTION_SWAP -> {
                // Like Wise/Revolut: the result becomes the new amount, so 1,250 ฿ → 33.13 € flips to 33.13 € → 1,250 ฿
                val rates = repo.cachedRates()
                val out = rates?.let { Converter.convert(repo.amount.toDoubleOrNull() ?: 0.0, repo.from, repo.to, it) }
                if (out != null && out > 0) repo.amount = Converter.plain(out)
                val f = repo.from; repo.from = repo.to; repo.to = f
                updateAll(ctx, swapped = true)
                return true
            }
            action == ACTION_FROM -> repo.from = next(repo.from, skip = repo.to)
            action == ACTION_TO -> repo.to = next(repo.to, skip = repo.from)
            action == ACTION_REFRESH -> refresh(ctx, pending)
            else -> return false
        }
        updateAll(ctx)
        return true
    }

    /** Try right away (works when the app isn't restricted) and also queue a network-aware job. */
    fun refresh(ctx: Context, provider: AppWidgetProvider) {
        val app = ctx.applicationContext
        RefreshWorker.now(app)
        RefreshWorker.schedulePeriodic(app)
        val result = provider.goAsync()
        thread {
            try {
                RateRepository(app).refresh()
                updateAll(app)
            } finally {
                result.finish()
            }
        }
    }
}
