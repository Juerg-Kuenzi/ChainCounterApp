package com.example.chaincounter.net

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Empfaengt UDP-Pakete auf einem Port und sendet einzelne Saetze.
 * Enthaelt bewusst keinen Android-Code, damit sie auf dem PC getestet werden kann.
 * Die Listener-Methoden laufen auf Hintergrund-Threads.
 */
class UdpLink(
    private val port: Int,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    private val configureSocket: (DatagramSocket) -> Unit = {},
    private val listener: Listener,
) {
    interface Listener {
        fun onSentence(text: String, sender: InetAddress)
        fun onReceiveError(error: IOException)
        fun onSendResult(sentence: String, error: IOException?)
    }

    @Volatile private var running = false
    @Volatile private var socket: DatagramSocket? = null
    private var receiver: Thread? = null
    private var sender: ExecutorService? = null

    /** Lokal gebundener Port, nuetzlich wenn mit Port 0 gestartet wurde. */
    val localPort: Int get() = socket?.localPort ?: -1

    fun start() {
        check(!running) { "UdpLink laeuft bereits" }
        running = true
        sender = Executors.newSingleThreadExecutor()
        receiver = Thread(::receiveLoop, "udp-receive").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        socket?.close()
        receiver?.interrupt()
        receiver = null
        sender?.shutdown()
        sender = null
    }

    fun send(sentence: String, target: InetAddress, targetPort: Int) {
        val executor = sender ?: run {
            listener.onSendResult(sentence, IOException("Verbindung ist nicht aktiv"))
            return
        }
        try {
            executor.execute {
                val error = try {
                    val current = socket ?: throw IOException("Socket ist nicht offen")
                    val bytes = sentence.toByteArray(Charsets.US_ASCII)
                    current.send(DatagramPacket(bytes, bytes.size, target, targetPort))
                    null
                } catch (e: IOException) {
                    e
                }
                listener.onSendResult(sentence, error)
            }
        } catch (e: RejectedExecutionException) {
            listener.onSendResult(sentence, IOException("Verbindung wurde beendet", e))
        }
    }

    private fun receiveLoop() {
        val buffer = ByteArray(bufferSize)
        while (running) {
            try {
                val open = openSocket()
                socket = open
                open.use {
                    while (running) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            it.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        val text = String(packet.data, packet.offset, packet.length, Charsets.US_ASCII)
                        listener.onSentence(text, packet.address)
                    }
                }
            } catch (e: IOException) {
                if (!running) return
                listener.onReceiveError(e)
                try {
                    Thread.sleep(RETRY_DELAY_MS)
                } catch (_: InterruptedException) {
                    return
                }
            } finally {
                socket = null
            }
        }
    }

    private fun openSocket(): DatagramSocket {
        val created = DatagramSocket(null as SocketAddress?)
        try {
            created.reuseAddress = true
            created.broadcast = true
            created.soTimeout = RECEIVE_TIMEOUT_MS
            configureSocket(created)
            created.bind(InetSocketAddress(port))
            return created
        } catch (e: IOException) {
            created.close()
            throw e
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 512
        const val RECEIVE_TIMEOUT_MS = 1000
        const val RETRY_DELAY_MS = 2000L
    }
}
