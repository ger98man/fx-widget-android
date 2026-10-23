package com.fxwidget

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** App settings: widget theme and which currencies the currency picker offers. */
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

        buildThemeStrip()

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

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    /** One preview card per theme: real background + art + sample numbers in the theme's colours. */
    private fun buildThemeStrip() {
        val strip = findViewById<LinearLayout>(R.id.theme_strip)
        strip.removeAllViews()
        val selected = repo.themeId
        for (t in Themes.ALL) {
            val card = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(128), LinearLayout.LayoutParams.MATCH_PARENT)
                    .apply { marginStart = dp(4); marginEnd = dp(4) }
                setBackgroundResource(t.bg)
                clipToOutline = true
                setOnClickListener {
                    repo.themeId = t.id
                    Widgets.updateAll(this@SettingsActivity)
                    buildThemeStrip()
                    Toast.makeText(this@SettingsActivity, "Тема «${t.name}» применена", Toast.LENGTH_SHORT).show()
                }
            }
            card.addView(ImageView(this).apply {
                setImageResource(t.art)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(-1, -1))
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                addView(TextView(context).apply {
                    text = "1,250 ฿"; setTextColor(t.text); textSize = 18f; setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.END
                }, LinearLayout.LayoutParams(-1, -2))
                addView(TextView(context).apply {
                    text = "33.13 €"; setTextColor(t.result); textSize = 18f; setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.END
                }, LinearLayout.LayoutParams(-1, -2))
                addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
                addView(TextView(context).apply {
                    text = (if (t.id == selected) "✓ " else "") + t.name
                    setTextColor(t.text); textSize = 13f; setTypeface(typeface, Typeface.BOLD)
                }, LinearLayout.LayoutParams(-1, -2))
                addView(TextView(context).apply {
                    text = t.subtitle; setTextColor(t.secondary); textSize = 10f; maxLines = 2
                }, LinearLayout.LayoutParams(-1, -2))
            }, FrameLayout.LayoutParams(-1, -1))
            if (t.id == selected) {
                card.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(28).toFloat()
                        setStroke(dp(3), t.accent)
                    }
                }, FrameLayout.LayoutParams(-1, -1))
            }
            strip.addView(card)
        }
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
