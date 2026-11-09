package com.example.chaincounter.protocol

/** Ein CAN-Frame vom NMEA2000-Bus. */
class CanFrame(val pgn: Int, val sourceAddress: Int, val priority: Int, val data: ByteArray)

/**
 * Yacht-Devices-RAW-Format, wie es die YDWG-02 per UDP sendet. Pro Zeile ein Frame:
 * `01:36:48.770 R 15FD0C74 FF 00 0E 7A BD 04 FF FF`
 * Ein UDP-Paket enthaelt mehrere Zeilen, getrennt durch CR LF.
 */
object YdRaw {
    private val LINE = Regex("""\d\d:\d\d:\d\d\.\d{3} [RT] ([0-9A-Fa-f]{8})((?: [0-9A-Fa-f]{2}){0,8})""")

    fun parseLine(raw: String): CanFrame? {
        val match = LINE.matchEntire(raw.trim()) ?: return null
        val id = match.groupValues[1].toLong(16).toInt()
        val bytes = match.groupValues[2].trim()
        val data = if (bytes.isEmpty()) ByteArray(0) else
            bytes.split(' ').map { it.toInt(16).toByte() }.toByteArray()
        return CanFrame(pgnOf(id), id and 0xFF, (id ushr 26) and 0x7, data)
    }

    fun parsePacket(text: String): List<CanFrame> = text.lineSequence().mapNotNull(::parseLine).toList()

    /** PDU1 (PF < 240) ist adressiert, dann gehoert PS nicht zur PGN. */
    fun pgnOf(canId: Int): Int {
        val pf = (canId ushr 16) and 0xFF
        val pgn = (canId ushr 8) and 0x3FFFF
        return if (pf < 240) pgn and 0x3FF00 else pgn
    }
}
