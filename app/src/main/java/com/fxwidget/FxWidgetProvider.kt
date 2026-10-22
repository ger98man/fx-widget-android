package com.fxwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Keypad widget: two currency rows with flags, compact keypad, quick amounts underneath. */
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
            R.id.kdot to ".", R.id.kclr to "C", R.id.kback to "B",
        )
        private val QUICK = listOf(R.id.q0, R.id.q1, R.id.q2, R.id.q3)

        fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val repo = RateRepository(context)
            val rates = repo.cachedRates()
            val amount = repo.amount
            val result = rates?.let { Converter.convert(amount.toDoubleOrNull() ?: 0.0, repo.from, repo.to, it) }
            val cls = FxWidgetProvider::class.java
            val fromSym = Converter.symbol(repo.from)

            val v = RemoteViews(context.packageName, R.layout.fx_widget)
            v.setTextViewText(R.id.from_flag, Converter.flag(repo.from))
            v.setTextViewText(R.id.to_flag, Converter.flag(repo.to))
            v.setTextViewText(R.id.from_btn, repo.from)
            v.setTextViewText(R.id.to_btn, repo.to)
            v.setTextViewText(R.id.amount, "${Converter.displayInput(amount)} $fromSym")
            v.setTextViewText(
                R.id.result,
                result?.let { "${Converter.formatResult(it)} ${Converter.symbol(repo.to)}" } ?: "—",
            )
            v.setTextViewText(R.id.status, Widgets.rateLine(repo, rates))

            Converter.quickAmounts(repo.from).zip(QUICK).forEach { (value, id) ->
                // "+" says it adds; drop the symbol on 4+ digits so the chip stays readable
                val label = "+" + Converter.displayInput(value.toString()) + if (value < 1000) " $fromSym" else ""
                v.setTextViewText(id, label)
                v.setOnClickPendingIntent(id, Widgets.broadcast(context, cls, Widgets.ACTION_SET + value))
            }
            KEYS.forEach { (id, key) -> v.setOnClickPendingIntent(id, Widgets.broadcast(context, cls, Widgets.ACTION_KEY + key)) }
            v.setOnClickPendingIntent(R.id.swap_btn, Widgets.broadcast(context, cls, Widgets.ACTION_SWAP))
            v.setOnClickPendingIntent(R.id.from_row, Widgets.broadcast(context, cls, Widgets.ACTION_FROM))
            v.setOnClickPendingIntent(R.id.to_row, Widgets.broadcast(context, cls, Widgets.ACTION_TO))
            v.setOnClickPendingIntent(R.id.refresh_btn, Widgets.broadcast(context, cls, Widgets.ACTION_REFRESH))
            v.setOnClickPendingIntent(R.id.status, Widgets.openApp(context))
            manager.updateAppWidget(ids, v)
        }
    }
}
