package com.fxwidget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView

/**
 * Small dialog over the home screen with every currency (flag, code, name).
 * Opened from a widget's currency row or the app's currency buttons.
 */
class CurrencyPickerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.currency_picker)

        val side = intent.getStringExtra(EXTRA_SIDE) ?: SIDE_FROM
        val repo = RateRepository(this)
        val current = if (side == SIDE_FROM) repo.from else repo.to
        val other = if (side == SIDE_FROM) repo.to else repo.from

        findViewById<TextView>(R.id.picker_title).text =
            if (side == SIDE_FROM) "Из какой валюты" else "В какую валюту"

        val list = findViewById<ListView>(R.id.picker_list)
        list.adapter = Adapter(this, current, other)
        list.setSelection((Converter.CURRENCIES.indexOf(current) - 2).coerceAtLeast(0))
        list.setOnItemClickListener { _, _, pos, _ ->
            pick(this, side, Converter.CURRENCIES[pos])
            finish()
        }
    }

    private class Adapter(
        private val ctx: Context,
        private val current: String,
        private val other: String,
    ) : BaseAdapter() {
        override fun getCount() = Converter.ALL.size
        override fun getItem(position: Int) = Converter.ALL[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: View.inflate(ctx, R.layout.currency_row, null)
            val c = Converter.ALL[position]
            v.findViewById<TextView>(R.id.row_flag).text = c.flag
            v.findViewById<TextView>(R.id.row_code).text = c.code
            v.findViewById<TextView>(R.id.row_name).text = when (c.code) {
                other -> "${c.name} · поменять местами"
                else -> c.name
            }
            v.findViewById<TextView>(R.id.row_check).visibility =
                if (c.code == current) View.VISIBLE else View.INVISIBLE
            v.setBackgroundResource(if (c.code == current) R.drawable.row_active else 0)
            return v
        }
    }

    companion object {
        const val EXTRA_SIDE = "side"
        const val SIDE_FROM = "from"
        const val SIDE_TO = "to"

        fun intent(ctx: Context, side: String): Intent =
            Intent(ctx, CurrencyPickerActivity::class.java).putExtra(EXTRA_SIDE, side)

        /** Applies a choice; picking the other side's currency swaps the two. */
        fun pick(ctx: Context, side: String, code: String) {
            val repo = RateRepository(ctx)
            if (side == SIDE_FROM) {
                if (code == repo.to) repo.to = repo.from
                repo.from = code
            } else {
                if (code == repo.from) repo.from = repo.to
                repo.to = code
            }
            Widgets.updateAll(ctx)
        }
    }
}
