package com.example.chaincounter

import com.example.chaincounter.protocol.ChainDirection
import com.example.chaincounter.protocol.Command
import com.example.chaincounter.protocol.Format
import com.example.chaincounter.protocol.Inputs
import com.example.chaincounter.protocol.Nmea
import com.example.chaincounter.protocol.ParseResult
import com.example.chaincounter.protocol.WindlassData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class NmeaTest {
    private fun parsed(raw: String): WindlassData = (Nmea.parse(raw) as ParseResult.Data).data

    @Test fun parsesFirmwareExample() {
        val d = parsed("\$ISLWND,12.4,0,24.1,17,86400,3605*07")
        assertEquals(WindlassData(12.4, ChainDirection.DOWN, 24.1, 17, 86400, 3605), d)
    }

    @Test fun parsesDirectionUpNegativeLengthAndLineEnd() {
        val d = parsed(Nmea.sentence("ISLWND,-1.2,1,12.0,1,0,0") + "\r\n")
        assertEquals(-1.2, d.chainLengthM, 0.0)
        assertEquals(ChainDirection.UP, d.direction)
    }

    @Test fun acceptsLowercaseChecksum() {
        val body = "ISLWND,1.0,0,12.0,1,0,0"
        val text = "%02x".format(Nmea.checksum(body))
        assertTrue(Nmea.parse("$$body*$text") is ParseResult.Data)
    }

    @Test fun readsU32PrintedWithPercentD() {
        assertEquals(4294967295L, parsed(Nmea.sentence("ISLWND,1.0,0,12.0,1,-1,0")).totalRuntimeS)
        assertEquals(4294967295L, parsed(Nmea.sentence("ISLWND,1.0,0,12.0,1,4294967295,0")).totalRuntimeS)
    }

    @Test fun rejectsBadSentences() {
        val bad = listOf(
            "ISLWND,12.4,0,24.1,17,86400,3605*07",
            "\$ISLWND,12.4,0,24.1,17,86400,3605",
            "\$ISLWND,12.4,0,24.1,17,86400,3605*08",
            "\$ISLWND,12.4,0,24.1,17,86400,3605*7",
            Nmea.sentence("ISLWND,12.4,0,24.1,17,86400"),
            Nmea.sentence("ISLWND,12.4,0,24.1,17,86400,3605,1"),
            Nmea.sentence("ISLWND,12.4,2,24.1,17,86400,3605"),
            Nmea.sentence("ISLWND,NaN,0,24.1,17,86400,3605"),
            Nmea.sentence("ISLWND,1e3,0,24.1,17,86400,3605"),
            Nmea.sentence("ISLWND,12.4,0,24.1,1.5,86400,3605"),
            Nmea.sentence("ISLWND,12.4,0,24.1,17,4294967296,3605"),
            Nmea.sentence("ISLWND,,0,24.1,17,86400,3605"),
        )
        bad.forEach { assertTrue(it, Nmea.parse(it) is ParseResult.Invalid) }
    }

    @Test fun ignoresOtherAddress() {
        assertEquals(ParseResult.OtherSentence("ISLWNS"), Nmea.parse(Command.ZeroCounter.sentence))
    }

    @Test fun buildsCommandsLikeFirmwareDocumentation() {
        assertEquals("\$ISLWNS,K,0.42*4F", Command.SetFactor(BigDecimal("0.42")).sentence)
        assertEquals("\$ISLWNS,C*73", Command.ZeroCounter.sentence)
        assertEquals(Nmea.sentence("ISLWNS,C,25"), Command.SetLength(25).sentence)
        assertEquals(Nmea.sentence("ISLWNS,S,10"), Command.SetDebounce(10).sentence)
    }

    @Test fun commandsAndNumbersIgnoreDeviceLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("\$ISLWNS,K,0.42*4F", Command.SetFactor(Inputs.factor("0,42")!!).sentence)
            assertEquals("12.4", Format.oneDecimal(12.4))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun validatesRanges() {
        assertEquals(0, Inputs.length("0"))
        assertEquals(25, Inputs.length(" 25 "))
        assertNull(Inputs.length("-1"))
        assertNull(Inputs.length("10000"))
        assertNull(Inputs.length("1.5"))
        assertEquals(1, Inputs.debounce("1"))
        assertEquals(1000, Inputs.debounce("1000"))
        assertNull(Inputs.debounce("0"))
        assertNull(Inputs.debounce("1001"))
        assertEquals(1467, Inputs.port("1467"))
        assertNull(Inputs.port("0"))
        assertNull(Inputs.port("65536"))
    }

    @Test fun validatesFactor() {
        assertEquals("0.4", Inputs.factor("0.400")!!.toPlainString())
        assertEquals("0.001", Inputs.factor("0,001")!!.toPlainString())
        assertEquals("100", Inputs.factor("100")!!.toPlainString())
        assertEquals("0.123456", Inputs.factor("0.123456")!!.toPlainString())
        assertNull(Inputs.factor("0.0009"))
        assertNull(Inputs.factor("100.1"))
        assertNull(Inputs.factor("0"))
        assertNull(Inputs.factor("0.1234567"))
        assertNull(Inputs.factor("1e-1"))
        assertNull(Inputs.factor(""))
    }

    @Test fun formatsDuration() {
        assertEquals("00:00:00", Format.duration(0))
        assertEquals("01:00:05", Format.duration(3605))
        assertEquals("1 d 00:00:00", Format.duration(86400))
    }
}
