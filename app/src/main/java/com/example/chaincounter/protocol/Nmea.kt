package com.example.chaincounter.protocol

import java.util.Locale

enum class ChainDirection { DOWN, UP }

/** Inhalt eines $ISLWND-Satzes gemaess Spezifikation V1.5, Kapitel Tx. */
data class WindlassData(
    val chainLengthM: Double,
    val direction: ChainDirection,
    val vccVolt: Double,
    val systemStarts: Long,
    val totalRuntimeS: Long,
    val actualRuntimeS: Long,
)

sealed interface ParseResult {
    data class Data(val data: WindlassData) : ParseResult

    /** Gueltiger Satz mit anderem Adressfeld, z. B. das eigene $ISLWNS. */
    data class OtherSentence(val address: String) : ParseResult

    data class Invalid(val reason: String) : ParseResult
}

object Nmea {
    private const val MAX_U32 = 0xFFFFFFFFL
    private val DECIMAL = Regex("""-?\d+(\.\d+)?""")
    private val INTEGER = Regex("""-?\d+""")
    private val HEX_PAIR = Regex("""[0-9A-Fa-f]{2}""")

    fun checksum(body: String): Int = body.fold(0) { acc, c -> acc xor c.code }

    /** Baut den Satz ohne Zeilenende, wie ihn das Geraet per UDP erwartet. */
    fun sentence(body: String): String =
        "$" + body + "*" + "%02X".format(Locale.ROOT, checksum(body))

    fun parse(raw: String): ParseResult {
        val line = raw.trim()
        if (!line.startsWith("$")) return ParseResult.Invalid("kein \$ am Anfang")
        val star = line.lastIndexOf('*')
        if (star < 0) return ParseResult.Invalid("keine Checksumme")
        val given = line.substring(star + 1)
        if (!HEX_PAIR.matches(given)) return ParseResult.Invalid("Checksumme falsch formatiert")
        val body = line.substring(1, star)
        if (checksum(body) != given.toInt(16)) return ParseResult.Invalid("Checksumme falsch")

        val fields = body.split(',')
        if (fields[0] != "ISLWND") return ParseResult.OtherSentence(fields[0])
        if (fields.size != 7) return ParseResult.Invalid("${fields.size - 1} statt 6 Felder")

        val length = decimal(fields[1]) ?: return ParseResult.Invalid("Kettenlaenge")
        val direction = when (fields[2]) {
            "0" -> ChainDirection.DOWN
            "1" -> ChainDirection.UP
            else -> return ParseResult.Invalid("Richtung")
        }
        val vcc = decimal(fields[3]) ?: return ParseResult.Invalid("VCC")
        val starts = unsigned32(fields[4]) ?: return ParseResult.Invalid("Systemstarts")
        val total = unsigned32(fields[5]) ?: return ParseResult.Invalid("TotalRuntime")
        val actual = unsigned32(fields[6]) ?: return ParseResult.Invalid("ActualRuntime")
        return ParseResult.Data(WindlassData(length, direction, vcc, starts, total, actual))
    }

    private fun decimal(text: String): Double? =
        if (DECIMAL.matches(text)) text.toDoubleOrNull() else null

    /** Die Spezifikation nennt %d fuer u32. Negative Werte werden deshalb zurueckgerechnet. */
    private fun unsigned32(text: String): Long? {
        if (!INTEGER.matches(text)) return null
        val value = text.toLongOrNull() ?: return null
        if (value < Int.MIN_VALUE || value > MAX_U32) return null
        return value and MAX_U32
    }
}
