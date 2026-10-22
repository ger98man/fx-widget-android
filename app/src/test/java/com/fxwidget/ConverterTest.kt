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
