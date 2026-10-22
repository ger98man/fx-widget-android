package com.fxwidget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
    private lateinit var banner: TextView
    private var pendingRelease: Updater.Release? = null
    private var installing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = RateRepository(this)

        input = findViewById(R.id.input)
        result = findViewById(R.id.result)
        status = findViewById(R.id.status)
        fromBtn = findViewById(R.id.from_btn)
        toBtn = findViewById(R.id.to_btn)
        presetViews = listOf(R.id.a0, R.id.a1, R.id.a2, R.id.a3, R.id.a4, R.id.a5).map { findViewById<TextView>(it) }

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
        fromBtn.setOnClickListener { startActivity(CurrencyPickerActivity.intent(this, CurrencyPickerActivity.SIDE_FROM)) }
        toBtn.setOnClickListener { startActivity(CurrencyPickerActivity.intent(this, CurrencyPickerActivity.SIDE_TO)) }
        findViewById<TextView>(R.id.refresh_btn).setOnClickListener { refresh() }

        banner = findViewById(R.id.update_banner)
        banner.setOnClickListener { pendingRelease?.let { startUpdate(it) } }
        findViewById<TextView>(R.id.version).text = "v" + Updater.installedVersion(this)

        RefreshWorker.schedulePeriodic(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        checkUpdate()
    }

    private fun checkUpdate() {
        if (installing) return
        thread {
            val release = Updater.checkForUpdate(this)
            runOnUiThread {
                pendingRelease = release
                if (release == null) {
                    banner.visibility = View.GONE
                } else {
                    banner.text = "Доступна версия v${release.version} — нажми, чтобы обновить"
                    banner.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startUpdate(release: Updater.Release) {
        // One-time: Android must allow this app to install APKs
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            banner.text = "Разреши установку из этого приложения и вернись сюда"
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
            )
            return
        }
        installing = true
        banner.isEnabled = false
        banner.text = "Скачиваю v${release.version}…"
        thread {
            try {
                Updater.downloadAndInstall(this, release) { pct ->
                    if (pct >= 0) runOnUiThread { banner.text = "Скачиваю v${release.version}… $pct%" }
                }
                runOnUiThread { banner.text = "Подтверди установку в окне Android" }
            } catch (e: Exception) {
                runOnUiThread {
                    banner.text = "Не удалось скачать (${e.javaClass.simpleName}) — нажми, чтобы повторить"
                }
            } finally {
                runOnUiThread { installing = false; banner.isEnabled = true }
            }
        }
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
        fromBtn.text = "${Converter.flag(repo.from)}  ${repo.from}"
        toBtn.text = "${Converter.flag(repo.to)}  ${repo.to}"
        input.hint = "0 ${Converter.symbol(repo.from)}"
        status.text = Widgets.rateLine(repo, rates)

        val amount = repo.amount.toDoubleOrNull() ?: 0.0
        val out = rates?.let { Converter.convert(amount, repo.from, repo.to, it) }
        result.text = out?.let { "${Converter.formatResult(it)} ${Converter.symbol(repo.to)}" } ?: "—"

        Converter.QUICK_AMOUNTS.zip(presetViews).forEach { (value, view) ->
            view.text = "+$value"
            view.setOnClickListener {
                input.setText(Converter.addAmount(repo.amount, value))
                input.setSelection(input.text.length)
            }
        }
    }
}
