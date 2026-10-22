package com.fxwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.RemoteViews
import kotlin.concurrent.thread

class FxWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val repo = RateRepository(context)
        when {
            action.startsWith(ACTION_KEY) -> repo.amount = Converter.applyKey(repo.amount, action.removePrefix(ACTION_KEY))
            action == ACTION_SWAP -> {
                val f = repo.from
                repo.from = repo.to
                repo.to = f
            }
            action == ACTION_FROM -> repo.from = next(repo.from, skip = repo.to)
            action == ACTION_TO -> repo.to = next(repo.to, skip = repo.from)
            action == ACTION_REFRESH -> refreshAsync(context)
            else -> return
        }
        updateAll(context)
    }

    private fun next(cur: String, skip: String): String {
        val l = Converter.CURRENCIES
        var i = l.indexOf(cur)
        do i = (i + 1) % l.size while (l[i] == skip)
        return l[i]
    }

    private fun refreshAsync(context: Context) {
        val app = context.applicationContext
        val pending = goAsync()
        thread {
            try {
                RateRepository(app).refresh()
                updateAll(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_KEY = "com.fxwidget.KEY_"
        private const val ACTION_SWAP = "com.fxwidget.SWAP"
        private const val ACTION_FROM = "com.fxwidget.FROM"
        private const val ACTION_TO = "com.fxwidget.TO"
        private const val ACTION_REFRESH = "com.fxwidget.REFRESH"

        private val KEYS = mapOf(
            R.id.k0 to "0", R.id.k1 to "1", R.id.k2 to "2", R.id.k3 to "3", R.id.k4 to "4",
            R.id.k5 to "5", R.id.k6 to "6", R.id.k7 to "7", R.id.k8 to "8", R.id.k9 to "9",
            R.id.kdot to ".", R.id.kclr to "C",
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FxWidgetProvider::class.java))
            render(context, manager, ids)
        }

        private fun pi(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, FxWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val repo = RateRepository(context)
            val rates = repo.cachedRates()
            val amount = repo.amount
            val result = rates?.let { Converter.convert(amount.toDoubleOrNull() ?: 0.0, repo.from, repo.to, it) }

            val views = RemoteViews(context.packageName, R.layout.fx_widget)
            views.setTextViewText(R.id.from_btn, repo.from)
            views.setTextViewText(R.id.to_btn, repo.to)
            views.setTextViewText(R.id.amount, amount)
            views.setTextViewText(R.id.result, result?.let { Converter.format(it) } ?: "—")
            views.setTextViewText(
                R.id.status,
                if (rates == null) "No rates yet – connect to internet, tap to retry"
                else "1 ${repo.from} = ${Converter.format(Converter.convert(1.0, repo.from, repo.to, rates) ?: 0.0)} ${repo.to} · " +
                    DateUtils.getRelativeTimeSpanString(repo.cachedAt(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
            )
            KEYS.forEach { (id, key) -> views.setOnClickPendingIntent(id, pi(context, ACTION_KEY + key)) }
            views.setOnClickPendingIntent(R.id.swap_btn, pi(context, ACTION_SWAP))
            views.setOnClickPendingIntent(R.id.from_btn, pi(context, ACTION_FROM))
            views.setOnClickPendingIntent(R.id.to_btn, pi(context, ACTION_TO))
            views.setOnClickPendingIntent(R.id.status, pi(context, ACTION_REFRESH))
            manager.updateAppWidget(ids, views)
        }
    }
}
