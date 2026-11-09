package ch.sy_isme.app.protocol

data class Temperature(val instance: Int, val source: Int, val celsius: Double)

object N2kTemperature {
    const val PGN_TEMPERATURE = 130312
    const val PGN_TEMPERATURE_EXTENDED = 130316
    const val SOURCE_EXHAUST_GAS = 14
    private const val KELVIN = 273.15

    /** PGN 130312: Messbereich bis etwa 382 °C, Aufloesung 0,01 K. */
    fun decodeStandard(data: ByteArray): Temperature? {
        if (data.size < 5) return null
        val raw = u(data, 3, 2)
        if (raw >= 0xFFFD) return null
        return Temperature(u(data, 1, 1).toInt(), u(data, 2, 1).toInt(), raw * 0.01 - KELVIN)
    }

    /** PGN 130316: Messbereich bis etwa 16500 °C, Aufloesung 0,001 K. */
    fun decodeExtended(data: ByteArray): Temperature? {
        if (data.size < 6) return null
        val raw = u(data, 3, 3)
        if (raw >= 0xFFFFFD) return null
        return Temperature(u(data, 1, 1).toInt(), u(data, 2, 1).toInt(), raw * 0.001 - KELVIN)
    }

    private fun u(data: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        for (i in length - 1 downTo 0) value = (value shl 8) or (data[offset + i].toLong() and 0xFF)
        return value
    }
}

/**
 * Waehlt die Temperatur einer Quelle aus dem Datenstrom. Der Sensor wird an Quelle und Instanz erkannt,
 * nicht an der Busadresse, weil diese sich aendern kann. 130316 hat den groesseren Messbereich und
 * hat Vorrang; 130312 zaehlt nur, solange keine 130316 kommt.
 */
class TemperatureSelector(
    private val source: Int,
    private val instance: Int = 0,
    private val preferExtendedForMs: Long = 6000,
) {
    private var lastExtendedAt: Long? = null

    fun onFrame(frame: CanFrame, now: Long): Double? {
        val reading = when (frame.pgn) {
            N2kTemperature.PGN_TEMPERATURE_EXTENDED -> N2kTemperature.decodeExtended(frame.data)
            N2kTemperature.PGN_TEMPERATURE -> N2kTemperature.decodeStandard(frame.data)
            else -> return null
        } ?: return null
        if (reading.source != source || reading.instance != instance) return null
        if (frame.pgn == N2kTemperature.PGN_TEMPERATURE_EXTENDED) {
            lastExtendedAt = now
        } else {
            val extendedAt = lastExtendedAt
            if (extendedAt != null && now - extendedAt < preferExtendedForMs) return null
        }
        return reading.celsius
    }
}

/** Verwirft ein Paket, das kurz nacheinander identisch ein zweites Mal ankommt. */
class DuplicateFilter(private val windowMs: Long = 50) {
    private var lastText: String? = null
    private var lastAt = 0L

    fun isDuplicate(text: String, now: Long): Boolean {
        val duplicate = text == lastText && now - lastAt <= windowMs
        lastText = text
        lastAt = now
        return duplicate
    }
}
