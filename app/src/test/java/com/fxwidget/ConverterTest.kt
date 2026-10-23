package com.fxwidget

import org.junit.Assert.*
import org.junit.Test

class ConverterTest {
    private val rates = mapOf("USD" to 1.0, "THB" to 35.0, "EUR" to 0.9)

    @Test fun convertsViaUsd() = assertEquals(2.5714, Converter.convert(100.0, "THB", "EUR", rates)!!, 1e-3)

    @Test fun missingCurrencyIsNull() = assertNull(Converter.convert(1.0, "THB", "XXX", rates))

    @Test fun parsesRates() {
        val r = Converter.parseRates("""{"result":"success","rates":{"USD":1,"THB":35.5}}""")
        assertEquals(35.5, r!!["THB"]!!, 0.0)
        assertNull(Converter.parseRates("""{"result":"error"}"""))
        assertNull(Converter.parseRates("garbage"))
    }

    @Test fun presetsAndFormat() {
        assertEquals(listOf(50, 100, 300, 500, 1000), Converter.presets("THB"))
        assertEquals(listOf(5, 10, 20, 50, 100), Converter.presets("EUR"))
        assertEquals("1.33", Converter.formatShort(1.3315))
        assertEquals("3,774", Converter.formatShort(3774.4))
        assertEquals("฿", Converter.symbol("THB"))
    }

    @Test fun displayAndBackspace() {
        assertEquals("1,250", Converter.displayInput("1250"))
        assertEquals("12,500.5", Converter.displayInput("12500.5"))
        assertEquals("3.", Converter.displayInput("3."))
        assertEquals("12", Converter.applyKey("125", "B"))
        assertEquals("0", Converter.applyKey("5", "B"))
        assertEquals("33.13", Converter.formatResult(33.1312))
        assertEquals("47,163", Converter.formatResult(47162.9))
        assertEquals("🇹🇭", Converter.flag("THB"))
        assertEquals("33.13", Converter.plain(33.1312))
        assertEquals("1250", Converter.plain(1250.0))
        assertEquals("33.1", Converter.plain(33.1))
        assertEquals("300", Converter.addAmount(Converter.addAmount(Converter.addAmount("0", 100), 100), 100))
        assertEquals("112.5", Converter.addAmount("12.5", 100))
    }

    @Test fun quickAmountsScaleWithCurrency() {
        val rates = mapOf("USD" to 1.0, "EUR" to 0.89, "THB" to 33.6, "PLN" to 3.9, "JPY" to 151.0,
            "KRW" to 1390.0, "VND" to 25400.0)
        assertEquals(listOf(1, 2, 5, 10, 20, 50), Converter.quickAmounts("EUR", rates))
        assertEquals(listOf(1, 5, 10, 50, 100, 500), Converter.quickAmounts("PLN", rates))
        assertEquals(listOf(10, 50, 100, 500, 1000, 5000), Converter.quickAmounts("THB", rates))
        assertEquals(listOf(100, 500, 1000, 5000, 10000, 50000), Converter.quickAmounts("JPY", rates))
        assertEquals(500_000, Converter.quickAmounts("KRW", rates).last())
        assertEquals(1_000_000, Converter.quickAmounts("VND", rates).last())
        assertEquals(listOf(10, 50, 100, 500, 1000, 5000), Converter.quickAmounts("THB", null))
        assertEquals("+5,000", Converter.quickLabel(5000))
        assertEquals("+10k", Converter.quickLabel(10000))
        assertEquals("+500k", Converter.quickLabel(500000))
        assertEquals("+1M", Converter.quickLabel(1000000))
    }

    @Test fun fixedRatesWorkBothWays() {
        var fixed = Converter.withFixed(emptyMap(), "EUR", "THB", 36.5)
        assertEquals(36.5, Converter.fixedRate("EUR", "THB", fixed)!!, 1e-9)
        assertEquals(1 / 36.5, Converter.fixedRate("THB", "EUR", fixed)!!, 1e-9)
        assertNull(Converter.fixedRate("USD", "THB", fixed))
        // entered from the other side: "1 $ = 33 ฿" (THB < USD alphabetically)
        fixed = Converter.withFixed(fixed, "USD", "THB", 33.0)
        assertEquals(33.0, Converter.fixedRate("USD", "THB", fixed)!!, 1e-9)
        assertEquals(1 / 33.0, Converter.fixedRate("THB", "USD", fixed)!!, 1e-9)
        assertEquals("EUR" to "THB", Converter.bigSide("THB", "EUR", 1 / 36.5))
        assertEquals("EUR" to "THB", Converter.bigSide("EUR", "THB", 36.5))
        assertEquals("36.5", Converter.plainRate(36.5))
        assertEquals("0.0274", Converter.plainRate(0.02739726))
    }

    @Test fun keypad() {
        assertEquals("5", Converter.applyKey("0", "5"))
        assertEquals("15", Converter.applyKey("1", "5"))
        assertEquals("1.", Converter.applyKey("1", "."))
        assertEquals("1.5", Converter.applyKey("1.", "5"))
        assertEquals("1.50", Converter.applyKey("1.5", "0"))
        assertEquals("1.50", Converter.applyKey("1.50", "7"))
        assertEquals("0", Converter.applyKey("123", "C"))
    }
}
