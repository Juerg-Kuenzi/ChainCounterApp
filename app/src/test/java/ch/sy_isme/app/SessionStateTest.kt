package ch.sy_isme.app

import ch.sy_isme.app.protocol.ChainDirection
import ch.sy_isme.app.protocol.WindlassData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class SessionStateTest {
    private val data = WindlassData(1.0, ChainDirection.DOWN, 24.0, 1, 1, 1)
    private val device: InetAddress = InetAddress.getLoopbackAddress()

    @Test fun becomesStaleAfterThreeSeconds() {
        val s = SessionState()
        assertEquals(false, s.isFresh(0))
        s.onData(data, device, 1000)
        assertTrue(s.isFresh(3999))
        assertEquals(false, s.isFresh(4000))
        assertEquals(4L, s.secondsSinceData(5000))
    }

    @Test fun reportsRestoredOnlyAfterOutage() {
        val s = SessionState()
        s.onData(data, device, 0)
        s.onTick(1000)
        s.onData(data, device, 1000)
        assertEquals(false, s.receptionRestored)

        s.onTick(5000)
        assertEquals(false, s.receptionRestored)
        s.onData(data, device, 9000)
        assertTrue(s.receptionRestored)

        s.onTick(9500)
        assertTrue(s.receptionRestored)
        s.clearRestoredNotice()
        assertEquals(false, s.receptionRestored)
    }

    @Test fun newOutageClearsRestoredNotice() {
        val s = SessionState()
        s.onData(data, device, 0)
        s.onTick(5000)
        s.onData(data, device, 6000)
        s.onTick(10000)
        assertEquals(false, s.receptionRestored)
    }

    @Test fun forgetDeviceDoesNotReportRestored() {
        val s = SessionState()
        s.onData(data, device, 0)
        s.onTick(5000)
        s.forgetDevice()
        s.onData(data, device, 6000)
        assertEquals(false, s.receptionRestored)
    }

    @Test fun detailScreens() {
        assertEquals(false, Screen.MAIN.isDetail)
        assertEquals(false, Screen.SETTINGS.isDetail)
        assertEquals(false, Screen.INFO.isDetail)
        assertTrue(Screen.RESTART.isDetail)
    }
}
