package com.fxwidget

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** App settings: which currencies the widget's currency picker offers. */
class SettingsActivity : Activity() {

    private lateinit var repo: RateRepository
    private lateinit var enabled: MutableSet<String>
    private lateinit var adapter: Adapter
    private lateinit var counter: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        repo = RateRepository(this)
        enabled = repo.enabledCurrencies.toMutableSet()
        counter = findViewById(R.id.settings_counter)

        findViewById<TextView>(R.id.settings_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.settings_all).setOnClickListener {
            enabled = Converter.CURRENCIES.toMutableSet(); save()
        }
        findViewById<TextView>(R.id.settings_none).setOnClickListener {
            // Keep what's selected right now so the widget always has its two currencies
            enabled = mutableSetOf(repo.from, repo.to); save()
        }

        adapter = Adapter()
        val list = findViewById<ListView>(R.id.settings_list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val code = Converter.ALL[pos].code
            when {
                code == repo.from || code == repo.to ->
                    Toast.makeText(this, "$code сейчас выбрана на виджете", Toast.LENGTH_SHORT).show()
                code in enabled -> { enabled.remove(code); save() }
                else -> { enabled.add(code); save() }
            }
        }
        updateCounter()
    }

    private fun save() {
        repo.enabledCurrencies = enabled.toSet()
        adapter.notifyDataSetChanged()
        updateCounter()
    }

    private fun updateCounter() {
        val shown = repo.pickerCurrencies().size
        counter.text = "В списке $shown из ${Converter.ALL.size}"
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = Converter.ALL.size
        override fun getItem(position: Int) = Converter.ALL[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: View.inflate(this@SettingsActivity, R.layout.currency_row, null)
            val c = Converter.ALL[position]
            val locked = c.code == repo.from || c.code == repo.to
            val on = locked || c.code in enabled
            v.findViewById<TextView>(R.id.row_flag).text = c.flag
            v.findViewById<TextView>(R.id.row_code).text = c.code
            v.findViewById<TextView>(R.id.row_name).text = if (locked) "${c.name} · на виджете" else c.name
            v.findViewById<TextView>(R.id.row_check).visibility = if (on) View.VISIBLE else View.INVISIBLE
            v.alpha = if (on) 1f else 0.45f
            v.setBackgroundResource(if (on) R.drawable.row_active else 0)
            return v
        }
    }
}
