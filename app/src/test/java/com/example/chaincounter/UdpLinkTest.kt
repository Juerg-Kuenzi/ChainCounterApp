package com.example.chaincounter

import com.example.chaincounter.net.UdpLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class UdpLinkTest {
    private val loopback: InetAddress = InetAddress.getLoopbackAddress()

    private class Recorder : UdpLink.Listener {
        val sentences = ArrayBlockingQueue<Pair<String, InetAddress>>(8)
        val sendResults = ArrayBlockingQueue<Result<String>>(8)

        override fun onSentence(text: String, sender: InetAddress) {
            sentences.add(text to sender)
        }

        override fun onReceiveError(error: IOException) = Unit

        override fun onSendResult(sentence: String, error: IOException?) {
            sendResults.add(if (error == null) Result.success(sentence) else Result.failure(error))
        }
    }

    private fun waitForPort(link: UdpLink): Int {
        repeat(100) {
            if (link.localPort > 0) return link.localPort
            Thread.sleep(20)
        }
        throw AssertionError("Socket wurde nicht geoeffnet")
    }

    @Test fun receivesSentenceAndSendsCommandWithoutLineEnd() {
        val recorder = Recorder()
        val link = UdpLink(port = 0, listener = recorder)
        link.start()
        DatagramSocket(0, loopback).use { device ->
            device.soTimeout = 3000
            val port = waitForPort(link)
            val tx = "\$ISLWND,12.4,0,24.1,17,86400,3605*07".toByteArray()
            device.send(DatagramPacket(tx, tx.size, loopback, port))

            val (text, sender) = recorder.sentences.poll(3, TimeUnit.SECONDS)!!
            assertEquals("\$ISLWND,12.4,0,24.1,17,86400,3605*07", text)

            link.send("\$ISLWNS,C*73", sender, device.localPort)
            val packet = DatagramPacket(ByteArray(64), 64)
            device.receive(packet)
            assertEquals("\$ISLWNS,C*73", String(packet.data, 0, packet.length))
            assertTrue(recorder.sendResults.poll(3, TimeUnit.SECONDS)!!.isSuccess)
        }
        link.stop()
    }

    @Test fun reportsSendAfterStopAsError() {
        val recorder = Recorder()
        val link = UdpLink(port = 0, listener = recorder)
        link.start()
        waitForPort(link)
        link.stop()
        link.send("\$ISLWNS,C*73", loopback, 1467)
        assertTrue(recorder.sendResults.poll(3, TimeUnit.SECONDS)!!.isFailure)
        assertNull(recorder.sentences.poll())
    }
}
