package com.example.chaincounter

/** Abgastemperatur, Datenausfall und Alarm. Enthaelt keinen Android-Code. */
class ExhaustMonitor {
    var celsius: Double? = null
        private set
    var alarmActive = false
        private set
    var muted = false
        private set

    private var lastAt = 0L

    fun onReading(value: Double, now: Long) {
        celsius = value
        lastAt = now
    }

    fun isFresh(now: Long): Boolean = celsius != null && now - lastAt < STALE_AFTER_MS

    fun secondsSinceData(now: Long): Long = (now - lastAt) / 1000

    /**
     * Alarm, sobald die Temperatur ueber dem Alarmwert liegt. Er endet erst 1 °C darunter,
     * damit er um den Grenzwert herum nicht dauernd ein- und ausschaltet.
     */
    fun update(now: Long, limit: Double) {
        val value = celsius
        val active = value != null && isFresh(now) &&
            (value > limit || (alarmActive && value > limit - HYSTERESIS))
        if (!active) muted = false
        alarmActive = active
    }

    /** Schaltet den Ton bis zum naechsten Alarm aus. Die Anzeige bleibt. */
    fun mute() {
        if (alarmActive) muted = true
    }

    val toneWanted: Boolean get() = alarmActive && !muted

    fun forget() {
        celsius = null
        alarmActive = false
        muted = false
    }

    companion object {
        /** Der Sensor sendet alle 2 s. Nach drei verpassten Werten gilt der Wert als veraltet. */
        const val STALE_AFTER_MS = 6000L
        const val HYSTERESIS = 1.0
    }
}
