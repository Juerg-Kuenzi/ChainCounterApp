package ch.sy_isme.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExhaustMonitorTest {
    @Test fun alarmAboveLimitWithHysteresis() {
        val m = ExhaustMonitor()
        m.onReading(50.0, 0); m.update(0, 50.0)
        assertEquals(false, m.alarmActive)
        m.onReading(50.1, 100); m.update(100, 50.0)
        assertTrue(m.alarmActive)
        assertTrue(m.toneWanted)
        m.onReading(49.5, 200); m.update(200, 50.0)
        assertTrue(m.alarmActive)
        m.onReading(49.0, 300); m.update(300, 50.0)
        assertEquals(false, m.alarmActive)
    }

    @Test fun muteLastsUntilNextAlarm() {
        val m = ExhaustMonitor()
        m.mute()
        m.onReading(60.0, 0); m.update(0, 50.0)
        assertEquals(false, m.muted)
        m.mute()
        assertEquals(false, m.toneWanted)
        assertTrue(m.alarmActive)
        m.onReading(40.0, 100); m.update(100, 50.0)
        m.onReading(60.0, 200); m.update(200, 50.0)
        assertTrue(m.toneWanted)
    }

    @Test fun staleDataEndsAlarmAndTone() {
        val m = ExhaustMonitor()
        m.onReading(60.0, 0); m.update(0, 50.0)
        assertTrue(m.isFresh(5999))
        m.update(6000, 50.0)
        assertEquals(false, m.isFresh(6000))
        assertEquals(false, m.alarmActive)
        assertEquals(6L, m.secondsSinceData(6000))
    }

    @Test fun lowerLimitTriggersImmediately() {
        val m = ExhaustMonitor()
        m.onReading(37.5, 0); m.update(0, 50.0)
        m.update(10, 30.0)
        assertTrue(m.alarmActive)
    }
}
