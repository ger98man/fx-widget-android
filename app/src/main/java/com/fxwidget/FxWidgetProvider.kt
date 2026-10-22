package com.fxwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Keypad widget: type any amount right on the home screen. */
class FxWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        Widgets.refresh(context, this)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        intent.action?.let { Widgets.handle(context, it, this) }
    }

    companion object {
        private val KEYS = mapOf(
            R.id.k0 to "0", R.id.k1 to "1", R.id.k2 to "2", R.id.k3 to "3", R.id.k4 to "4",
            R.id.k5 to "5", R.id.k6 to "6", R.id.k7 to "7", R.id.k8 to "8", R.id.k9 to "9",
            R.id.kdot to ".", R.id.kclr to "C",
        )

        fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val repo = RateRepository(context)
            val rates = repo.cachedRates()
            val amount = repo.amount
            val result = rates?.let { Converter.convert(amount.toDoubleOrNull() ?: 0.0, repo.from, repo.to, it) }
            val cls = FxWidgetProvider::class.java

            val v = RemoteViews(context.packageName, R.layout.fx_widget)
            v.setTextViewText(R.id.from_btn, repo.from)
            v.setTextViewText(R.id.to_btn, repo.to)
            v.setTextViewText(R.id.amount, "$amount ${Converter.symbol(repo.from)}")
            v.setTextViewText(
                R.id.result,
                result?.let { "${Converter.format(it)} ${Converter.symbol(repo.to)}" } ?: "—",
            )
            v.setTextViewText(R.id.status, Widgets.rateLine(repo, rates))
            KEYS.forEach { (id, key) -> v.setOnClickPendingIntent(id, Widgets.broadcast(context, cls, Widgets.ACTION_KEY + key)) }
            v.setOnClickPendingIntent(R.id.swap_btn, Widgets.broadcast(context, cls, Widgets.ACTION_SWAP))
            v.setOnClickPendingIntent(R.id.from_btn, Widgets.broadcast(context, cls, Widgets.ACTION_FROM))
            v.setOnClickPendingIntent(R.id.to_btn, Widgets.broadcast(context, cls, Widgets.ACTION_TO))
            v.setOnClickPendingIntent(R.id.refresh_btn, Widgets.broadcast(context, cls, Widgets.ACTION_REFRESH))
            v.setOnClickPendingIntent(R.id.status, Widgets.openApp(context))
            manager.updateAppWidget(ids, v)
        }
    }
}
