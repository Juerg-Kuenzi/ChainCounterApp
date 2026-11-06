package com.example.chaincounter

import com.example.chaincounter.protocol.WindlassData
import java.net.InetAddress

enum class Screen {
    MAIN, SETTINGS, INFO, ZERO, LENGTH, PORT, DEBOUNCE, FACTOR, RESTART;

    val isDetail: Boolean get() = this != MAIN && this != SETTINGS && this != INFO
}

/** Zustand, der den Wechsel zwischen Hell und Dunkel ueberlebt. Enthaelt keinen Android-Code. */
class SessionState {
    var screen = Screen.MAIN
    var lastData: WindlassData? = null
        private set
    var deviceAddress: InetAddress? = null
        private set
    var invalidSentences = 0
    var receiveError: String? = null
    var receptionRestored = false
        private set

    /** Zuletzt gemeldeter Text je Einstellung. Er bleibt stehen, bis etwas Neues gesendet wird. */
    val messages = mutableMapOf<Screen, String>()
    var lastSendScreen: Screen? = null

    private var lastReceivedAt = 0L
    private var receptionLost = false

    fun onData(data: WindlassData, sender: InetAddress, now: Long) {
        lastData = data
        deviceAddress = sender
        lastReceivedAt = now
        receiveError = null
        if (receptionLost) {
            receptionLost = false
            receptionRestored = true
        }
    }

    fun isFresh(now: Long): Boolean =
        lastData != null && now - lastReceivedAt < STALE_AFTER_MS

    fun secondsSinceData(now: Long): Long = (now - lastReceivedAt) / 1000

    /** Regelmaessig aufrufen, damit ein Ausfall auch ohne eingehende Daten erkannt wird. */
    fun onTick(now: Long) {
        if (lastData != null && !isFresh(now)) {
            receptionLost = true
            receptionRestored = false
        }
    }

    fun clearRestoredNotice() {
        receptionRestored = false
    }

    fun forgetDevice() {
        lastData = null
        deviceAddress = null
        invalidSentences = 0
        receiveError = null
        receptionLost = false
        receptionRestored = false
    }

    companion object {
        const val STALE_AFTER_MS = 3000L
    }
}
