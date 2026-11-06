package com.example.chaincounter.protocol

import java.math.BigDecimal

/** Kommandos $ISLWNS. Factory Reset (F) ist in dieser App bewusst nicht enthalten. */
sealed class Command(private val parameters: String) {
    data object ZeroCounter : Command("C")
    data object Restart : Command("R")
    data class SetLength(val meters: Int) : Command("C,$meters")
    data class SetDebounce(val millis: Int) : Command("S,$millis")
    data class SetFactor(val metersPerTurn: BigDecimal) :
        Command("K," + metersPerTurn.toPlainString())

    val sentence: String get() = Nmea.sentence("ISLWNS,$parameters")
}

/** Prueft Benutzereingaben. Null bedeutet: ausserhalb des erlaubten Bereichs oder falsches Format. */
object Inputs {
    const val LENGTH_MAX = 9999
    const val DEBOUNCE_MIN = 1
    const val DEBOUNCE_MAX = 1000
    const val PORT_MIN = 1
    const val PORT_MAX = 65535
    private const val FACTOR_MAX_DECIMALS = 6

    private val FACTOR_MIN = BigDecimal("0.001")
    private val FACTOR_MAX = BigDecimal("100")
    private val DIGITS = Regex("""\d{1,6}""")
    private val FACTOR = Regex("""\d{1,3}([.,]\d+)?""")

    fun length(text: String): Int? = bounded(text, 0, LENGTH_MAX)
    fun debounce(text: String): Int? = bounded(text, DEBOUNCE_MIN, DEBOUNCE_MAX)
    fun port(text: String): Int? = bounded(text, PORT_MIN, PORT_MAX)

    /** Nimmt Punkt oder Komma an und liefert immer einen Wert, der mit Punkt gesendet wird. */
    fun factor(text: String): BigDecimal? {
        val trimmed = text.trim()
        if (!FACTOR.matches(trimmed)) return null
        val value = BigDecimal(trimmed.replace(',', '.')).stripTrailingZeros()
        if (value.scale() > FACTOR_MAX_DECIMALS) return null
        return value.takeIf { it >= FACTOR_MIN && it <= FACTOR_MAX }
    }

    private fun bounded(text: String, min: Int, max: Int): Int? {
        val trimmed = text.trim()
        if (!DIGITS.matches(trimmed)) return null
        return trimmed.toInt().takeIf { it in min..max }
    }
}
