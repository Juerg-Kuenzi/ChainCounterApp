package com.example.chaincounter

import com.example.chaincounter.protocol.DuplicateFilter
import com.example.chaincounter.protocol.N2kTemperature
import com.example.chaincounter.protocol.TemperatureSelector
import com.example.chaincounter.protocol.YdRaw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Zeilen stammen aus einem Mitschnitt der YDWG-02 mit dem Abgassensor (Quelle 14, Adresse 0x74). */
class N2kTest {
    private val extended = "01:36:48.770 R 15FD0C74 FF 00 0E 7A BD 04 FF FF"
    private val standard = "01:36:48.769 R 15FD0874 FF 00 0E 59 79 FF FF FF"
    private val sea = "01:36:47.650 R 15FD086E 00 00 00 F7 76 FF FF FF"

    @Test fun parsesYdRawLine() {
        val f = YdRaw.parseLine(extended)!!
        assertEquals(130316, f.pgn)
        assertEquals(0x74, f.sourceAddress)
        assertEquals(5, f.priority)
        assertEquals(8, f.data.size)
        assertEquals(0x0E, f.data[2].toInt())
    }

    @Test fun parsesPacketWithHeaderlessLinesAndCrLf() {
        val packet = "$standard\r\n$extended\r\n01:36:47.351 R 1DEFFF01 41 03 FF FF FF FF FF FF\r\n"
        val frames = YdRaw.parsePacket(packet)
        assertEquals(listOf(130312, 130316, 126720), frames.map { it.pgn })
    }

    @Test fun pdu1PgnIgnoresDestination() {
        // 0x1DEFFF01: PF 0xEF < 240, PGN 0x1EF00 = 126720
        assertEquals(126720, YdRaw.pgnOf(0x1DEFFF01))
        assertEquals(130316, YdRaw.pgnOf(0x15FD0C74))
    }

    @Test fun rejectsBadLines() {
        listOf(
            "",
            "garbage",
            "01:36:48.770 R 15FD0C7 FF",
            "01:36:48.770 X 15FD0C74 FF",
            "01:36:48.770 R 15FD0C74 FF 00 0E 7A BD 04 FF FF 00",
        ).forEach { assertNull(it, YdRaw.parseLine(it)) }
    }

    @Test fun decodesBothTemperaturePgns() {
        val e = N2kTemperature.decodeExtended(YdRaw.parseLine(extended)!!.data)!!
        assertEquals(14, e.source)
        assertEquals(0, e.instance)
        assertEquals(37.5, e.celsius, 0.0005)
        val s = N2kTemperature.decodeStandard(YdRaw.parseLine(standard)!!.data)!!
        assertEquals(37.5, s.celsius, 0.005)
        assertEquals(31.4, N2kTemperature.decodeStandard(YdRaw.parseLine(sea)!!.data)!!.celsius, 0.005)
    }

    @Test fun unavailableValueIsNull() {
        assertNull(N2kTemperature.decodeExtended(byteArrayOf(-1, 0, 14, -1, -1, -1, -1, -1)))
        assertNull(N2kTemperature.decodeStandard(byteArrayOf(-1, 0, 14, -1, -1)))
    }

    @Test fun selectorFiltersSourceAndPrefersExtended() {
        val sel = TemperatureSelector(source = 14)
        assertNull(sel.onFrame(YdRaw.parseLine(sea)!!, 0))
        assertEquals(37.5, sel.onFrame(YdRaw.parseLine(standard)!!, 0)!!, 0.005)
        assertEquals(37.5, sel.onFrame(YdRaw.parseLine(extended)!!, 1)!!, 0.0005)
        assertNull(sel.onFrame(YdRaw.parseLine(standard)!!, 2000))
        assertEquals(37.5, sel.onFrame(YdRaw.parseLine(standard)!!, 7001)!!, 0.005)
    }

    @Test fun duplicateFilter() {
        val f = DuplicateFilter()
        assertEquals(false, f.isDuplicate("a", 0))
        assertTrue(f.isDuplicate("a", 1))
        assertEquals(false, f.isDuplicate("a", 200))
        assertEquals(false, f.isDuplicate("b", 201))
    }
}
