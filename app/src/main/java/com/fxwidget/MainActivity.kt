package com.fxwidget

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Full-screen converter. Opening it also refreshes rates in the foreground, where
 * the system never blocks network — this seeds the cache the widgets read from.
 */
class MainActivity : Activity() {

    private lateinit var repo: RateRepository
    private lateinit var input: EditText
    private lateinit var result: TextView
    private lateinit var status: TextView
    private lateinit var fromBtn: TextView
    private lateinit var toBtn: TextView
    private lateinit var presetViews: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = RateRepository(this)

        input = findViewById(R.id.input)
        result = findViewById(R.id.result)
        status = findViewById(R.id.status)
        fromBtn = findViewById(R.id.from_btn)
        toBtn = findViewById(R.id.to_btn)
        presetViews = listOf(R.id.a0, R.id.a1, R.id.a2, R.id.a3, R.id.a4).map { findViewById<TextView>(it) }

        input.setText(repo.amount.takeIf { it != "0" } ?: "")
        input.setSelection(input.text.length)
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                repo.amount = s.toString().replace(',', '.').ifBlank { "0" }
                render()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        findViewById<TextView>(R.id.swap_btn).setOnClickListener {
            val f = repo.from; repo.from = repo.to; repo.to = f
            render()
        }
        fromBtn.setOnClickListener { repo.from = next(repo.from, repo.to); render() }
        toBtn.setOnClickListener { repo.to = next(repo.to, repo.from); render() }
        findViewById<TextView>(R.id.refresh_btn).setOnClickListener { refresh() }

        RefreshWorker.schedulePeriodic(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        status.text = "Обновляю курс…"
        thread {
            repo.refresh()
            runOnUiThread {
                render()
                Widgets.updateAll(this)
            }
        }
    }

    private fun next(cur: String, skip: String): String {
        val l = Converter.CURRENCIES
        var i = l.indexOf(cur)
        do i = (i + 1) % l.size while (l[i] == skip)
        return l[i]
    }

    private fun render() {
        val rates = repo.cachedRates()
        fromBtn.text = repo.from
        toBtn.text = repo.to
        input.hint = "0 ${Converter.symbol(repo.from)}"
        status.text = Widgets.rateLine(repo, rates)

        val amount = repo.amount.toDoubleOrNull() ?: 0.0
        val out = rates?.let { Converter.convert(amount, repo.from, repo.to, it) }
        result.text = out?.let { "${Converter.format(it)} ${Converter.symbol(repo.to)}" } ?: "—"

        Converter.presets(repo.from).zip(presetViews).forEach { (value, view) ->
            view.text = "%,d".format(value)
            view.setOnClickListener {
                input.setText(value.toString())
                input.setSelection(input.text.length)
            }
        }
    }
}
