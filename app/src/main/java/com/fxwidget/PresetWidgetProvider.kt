package com.fxwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Compact widget: rate plus five ready-made amounts (50 / 100 / 300 / 500 / 1000 ฿ → €). */
class PresetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        Widgets.refresh(context, this)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        intent.action?.let { Widgets.handle(context, it, this) }
    }

    companion object {
        private val CELLS = listOf(
            R.id.p0_in to R.id.p0_out, R.id.p1_in to R.id.p1_out, R.id.p2_in to R.id.p2_out,
            R.id.p3_in to R.id.p3_out, R.id.p4_in to R.id.p4_out,
        )

        fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val repo = RateRepository(context)
            val rates = repo.cachedRates()
            val cls = PresetWidgetProvider::class.java
            val fromSym = Converter.symbol(repo.from)
            val toSym = Converter.symbol(repo.to)

            val v = RemoteViews(context.packageName, R.layout.preset_widget)
            v.setTextViewText(R.id.from_btn, "${Converter.flag(repo.from)}  ${repo.from}")
            v.setTextViewText(R.id.to_btn, "${Converter.flag(repo.to)}  ${repo.to}")
            v.setTextViewText(R.id.status, Widgets.modeLabel(repo) + " · " + Widgets.rateLine(repo))

            Converter.presets(repo.from).zip(CELLS).forEach { (amount, cell) ->
                val out = repo.convert(amount.toDouble())
                v.setTextViewText(cell.first, "${Converter.displayInput(amount.toString())} $fromSym")
                v.setTextViewText(cell.second, out?.let { "${Converter.formatShort(it)} $toSym" } ?: "—")
            }

            v.setOnClickPendingIntent(R.id.swap_btn, Widgets.broadcast(context, cls, Widgets.ACTION_SWAP))
            v.setOnClickPendingIntent(R.id.from_btn, Widgets.picker(context, CurrencyPickerActivity.SIDE_FROM))
            v.setOnClickPendingIntent(R.id.to_btn, Widgets.picker(context, CurrencyPickerActivity.SIDE_TO))
            v.setOnClickPendingIntent(R.id.refresh_btn, Widgets.broadcast(context, cls, Widgets.ACTION_REFRESH))
            v.setOnClickPendingIntent(R.id.presets, Widgets.openApp(context))
            v.setOnClickPendingIntent(R.id.status, Widgets.openApp(context))
            val t = Themes.byId(repo.themeId)
            v.setInt(android.R.id.background, "setBackgroundResource", t.bg)
            v.setImageViewResource(R.id.art, t.art)
            listOf(R.id.from_btn, R.id.to_btn).forEach {
                v.setInt(it, "setBackgroundResource", t.row); v.setTextColor(it, t.text)
            }
            v.setInt(R.id.swap_btn, "setBackgroundResource", t.pill)
            v.setInt(R.id.swap_btn, "setColorFilter", t.swapIcon)
            v.setInt(R.id.refresh_btn, "setColorFilter", t.secondary)
            v.setTextColor(R.id.status, t.secondary)
            listOf(R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4).forEach { v.setInt(it, "setBackgroundResource", t.key) }
            CELLS.forEach { (inId, outId) -> v.setTextColor(inId, t.secondary); v.setTextColor(outId, t.result) }
            manager.updateAppWidget(ids, v)
        }
    }
}
