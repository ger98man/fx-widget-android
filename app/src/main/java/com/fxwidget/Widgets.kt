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
    const val ACTION_MODE = "com.fxwidget.MODE"

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

    /** Short badge text for the current mode. */
    fun modeLabel(repo: RateRepository): String = if (repo.usingFixed()) "ФИКС" else "БАНК"

    fun rateLine(repo: RateRepository): String {
        val r = repo.rate()
        if (r == null) {
            val err = repo.lastError?.let { " ($it)" } ?: ""
            return "Нет курса — нажми ⟳ или открой приложение$err"
        }
        // Show the "big" side: 1 € = 37.74 ฿ reads better than 1 ฿ = 0.0265 €
        val (a, b) = Converter.bigSide(repo.from, repo.to, r)
        val v = if (a == repo.from) r else 1 / r
        val base = "1 ${Converter.symbol(a)} = ${Converter.formatShort(v)} ${Converter.symbol(b)}"
        if (repo.usingFixed()) {
            // how the office rate compares with the bank one, from the "big" side
            val bank = repo.bankRate(a, b) ?: return base
            val pct = (v / bank - 1) * 100
            return base + " · банк " + Converter.formatShort(bank) + " (" + String.format(java.util.Locale.US, "%+.1f%%", pct) + ")"
        }
        val age = DateUtils.getRelativeTimeSpanString(
            repo.cachedAt(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        )
        val stale = if (repo.lastError != null) " · офлайн" else ""
        val noFix = if (repo.rateMode == "fix") " · фикс для ${repo.from}/${repo.to} не задан" else ""
        return "$base · $age$stale$noFix"
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
                val out = repo.convert(repo.amount.toDoubleOrNull() ?: 0.0)
                if (out != null && out > 0) repo.amount = Converter.plain(out)
                val f = repo.from; repo.from = repo.to; repo.to = f
                updateAll(ctx, swapped = true)
                return true
            }
            action == ACTION_FROM -> repo.from = next(repo.from, skip = repo.to)
            action == ACTION_TO -> repo.to = next(repo.to, skip = repo.from)
            action == ACTION_REFRESH -> refresh(ctx, pending)
            action == ACTION_MODE -> repo.rateMode = if (repo.rateMode == "fix") "bank" else "fix"
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
