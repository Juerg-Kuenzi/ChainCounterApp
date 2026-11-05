package com.example.chaincounter

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.example.chaincounter.net.UdpLink
import com.example.chaincounter.protocol.ChainDirection
import com.example.chaincounter.protocol.Command
import com.example.chaincounter.protocol.Format
import com.example.chaincounter.protocol.Inputs
import com.example.chaincounter.protocol.Nmea
import com.example.chaincounter.protocol.ParseResult
import com.example.chaincounter.protocol.WindlassData
import java.io.IOException
import java.net.InetAddress

class MainActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var connectivity: ConnectivityManager
    private lateinit var multicastLock: WifiManager.MulticastLock

    private lateinit var statusText: TextView
    private lateinit var staleBanner: TextView
    private lateinit var lengthValue: TextView
    private lateinit var directionValue: TextView
    private lateinit var vccValue: TextView
    private lateinit var startsValue: TextView
    private lateinit var actualRuntimeValue: TextView
    private lateinit var totalRuntimeValue: TextView
    private lateinit var commandStatus: TextView
    private lateinit var portButton: Button
    private lateinit var commandButtons: List<Button>

    private var port = DEFAULT_PORT
    private var network: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var link: UdpLink? = null

    private var lastData: WindlassData? = null
    private var lastReceivedAt = 0L
    private var deviceAddress: InetAddress? = null
    private var invalidSentences = 0
    private var receiveError: String? = null
    private var pendingLengthCheck: Runnable? = null

    private val ticker = object : Runnable {
        override fun run() {
            render()
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        port = prefs.getInt(PREF_PORT, DEFAULT_PORT)
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("chaincounter").apply {
            setReferenceCounted(false)
        }

        statusText = findViewById(R.id.status_text)
        staleBanner = findViewById(R.id.stale_banner)
        lengthValue = findViewById(R.id.length_value)
        directionValue = findViewById(R.id.direction_value)
        vccValue = findViewById(R.id.vcc_value)
        startsValue = findViewById(R.id.starts_value)
        actualRuntimeValue = findViewById(R.id.actual_runtime_value)
        totalRuntimeValue = findViewById(R.id.total_runtime_value)
        commandStatus = findViewById(R.id.command_status)
        portButton = findViewById(R.id.port_button)

        val zero = findViewById<Button>(R.id.zero_button)
        val setLength = findViewById<Button>(R.id.set_length_button)
        val debounce = findViewById<Button>(R.id.debounce_button)
        val factor = findViewById<Button>(R.id.factor_button)
        commandButtons = listOf(zero, setLength, debounce, factor)

        zero.setOnClickListener { confirmZero() }
        setLength.setOnClickListener { askLength() }
        debounce.setOnClickListener { askDebounce() }
        factor.setOnClickListener { askFactor() }
        portButton.setOnClickListener { askPort() }
    }

    override fun onStart() {
        super.onStart()
        multicastLock.acquire()
        // WLAN ohne Internet ist an Bord der Normalfall. Android wuerde sonst auf Mobilfunk routen.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(available: Network) {
                if (available != network) {
                    network = available
                    startLink()
                }
            }

            override fun onLost(lost: Network) {
                if (lost == network) {
                    network = null
                    stopLink()
                    render()
                }
            }
        }
        networkCallback = callback
        connectivity.requestNetwork(request, callback, main)
        main.post(ticker)
    }

    override fun onStop() {
        main.removeCallbacks(ticker)
        pendingLengthCheck?.let(main::removeCallbacks)
        pendingLengthCheck = null
        networkCallback?.let(connectivity::unregisterNetworkCallback)
        networkCallback = null
        network = null
        stopLink()
        if (multicastLock.isHeld) multicastLock.release()
        super.onStop()
    }

    private fun startLink() {
        stopLink()
        val active = network ?: return
        receiveError = null
        link = UdpLink(
            port = port,
            configureSocket = { active.bindSocket(it) },
            listener = object : UdpLink.Listener {
                override fun onSentence(text: String, sender: InetAddress) {
                    main.post { handleSentence(text, sender) }
                }

                override fun onReceiveError(error: IOException) {
                    main.post { receiveError = error.message ?: error.toString() }
                }

                override fun onSendResult(sentence: String, error: IOException?) {
                    if (error != null) {
                        main.post {
                            commandStatus.text = getString(
                                R.string.command_send_failed,
                                error.message ?: error.toString(),
                            )
                        }
                    }
                }
            },
        ).also { it.start() }
    }

    private fun stopLink() {
        link?.stop()
        link = null
    }

    private fun handleSentence(text: String, sender: InetAddress) {
        when (val result = Nmea.parse(text)) {
            is ParseResult.Data -> {
                lastData = result.data
                lastReceivedAt = SystemClock.elapsedRealtime()
                deviceAddress = sender
                receiveError = null
                render()
            }
            is ParseResult.OtherSentence -> Unit
            is ParseResult.Invalid -> invalidSentences++
        }
    }

    private fun isFresh(): Boolean =
        lastData != null && SystemClock.elapsedRealtime() - lastReceivedAt < STALE_AFTER_MS

    private fun render() {
        val data = lastData
        val fresh = isFresh()

        statusText.text = buildString {
            append(
                when {
                    network == null -> getString(R.string.status_no_wifi)
                    fresh -> getString(R.string.status_receiving, deviceAddress?.hostAddress ?: "")
                    receiveError != null -> getString(R.string.status_port_error, port, receiveError)
                    else -> getString(R.string.status_waiting, port)
                },
            )
            if (invalidSentences > 0) {
                append('\n').append(getString(R.string.status_invalid, invalidSentences))
            }
        }
        portButton.text = getString(R.string.port_button, port)

        if (data != null && !fresh) {
            val seconds = (SystemClock.elapsedRealtime() - lastReceivedAt) / 1000
            staleBanner.text = getString(R.string.stale_banner, seconds)
            staleBanner.visibility = View.VISIBLE
        } else {
            staleBanner.visibility = View.GONE
        }

        lengthValue.setTextColor(getColor(if (fresh) R.color.ink else R.color.muted))
        commandButtons.forEach { it.isEnabled = fresh }

        if (data == null) {
            val empty = getString(R.string.no_value)
            lengthValue.text = empty
            directionValue.text = empty
            vccValue.text = empty
            startsValue.text = empty
            actualRuntimeValue.text = empty
            totalRuntimeValue.text = empty
            return
        }
        lengthValue.text = Format.oneDecimal(data.chainLengthM)
        directionValue.setText(
            if (data.direction == ChainDirection.DOWN) R.string.direction_down else R.string.direction_up,
        )
        vccValue.text = getString(R.string.vcc_value, Format.oneDecimal(data.vccVolt))
        startsValue.text = data.systemStarts.toString()
        actualRuntimeValue.text = Format.duration(data.actualRuntimeS)
        totalRuntimeValue.text = Format.duration(data.totalRuntimeS)
    }

    private fun send(command: Command, sentMessage: String) {
        val target = deviceAddress
        val active = link
        if (target == null || active == null || !isFresh()) {
            commandStatus.setText(R.string.command_no_device)
            return
        }
        commandStatus.text = sentMessage
        active.send(command.sentence, target, port)
    }

    /** Das Geraet quittiert nicht. Der naechste $ISLWND-Satz zeigt aber, was es jetzt meldet. */
    private fun reportLengthAfterSend(label: String) {
        pendingLengthCheck?.let(main::removeCallbacks)
        val check = Runnable {
            pendingLengthCheck = null
            val data = lastData
            commandStatus.text = if (data != null && isFresh()) {
                getString(R.string.command_length_result, label, Format.oneDecimal(data.chainLengthM))
            } else {
                getString(R.string.command_length_no_data, label)
            }
        }
        pendingLengthCheck = check
        main.postDelayed(check, LENGTH_CHECK_DELAY_MS)
    }

    private fun confirmZero() {
        AlertDialog.Builder(this)
            .setTitle(R.string.zero_title)
            .setMessage(R.string.counter_storage_hint)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.zero_confirm) { _, _ ->
                val label = getString(R.string.zero_sent)
                send(Command.ZeroCounter, label)
                reportLengthAfterSend(label)
            }
            .show()
    }

    private fun askLength() {
        showInputDialog(
            title = getString(R.string.length_title),
            message = getString(R.string.counter_storage_hint),
            hint = getString(R.string.length_hint, Inputs.LENGTH_MAX),
            inputType = InputType.TYPE_CLASS_NUMBER,
            confirm = getString(R.string.length_confirm),
            parse = Inputs::length,
        ) { meters ->
            val label = getString(R.string.length_sent, meters)
            send(Command.SetLength(meters), label)
            reportLengthAfterSend(label)
        }
    }

    private fun askDebounce() {
        showInputDialog(
            title = getString(R.string.debounce_title),
            message = getString(R.string.no_readback_hint),
            hint = getString(R.string.debounce_hint, Inputs.DEBOUNCE_MIN, Inputs.DEBOUNCE_MAX),
            inputType = InputType.TYPE_CLASS_NUMBER,
            confirm = getString(R.string.debounce_confirm),
            parse = Inputs::debounce,
        ) { millis ->
            send(Command.SetDebounce(millis), getString(R.string.debounce_sent, millis))
        }
    }

    private fun askFactor() {
        showInputDialog(
            title = getString(R.string.factor_title),
            message = getString(R.string.no_readback_hint),
            hint = getString(R.string.factor_hint),
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            confirm = getString(R.string.factor_confirm),
            parse = Inputs::factor,
        ) { factor ->
            send(Command.SetFactor(factor), getString(R.string.factor_sent, factor.toPlainString()))
        }
    }

    private fun askPort() {
        showInputDialog(
            title = getString(R.string.port_title),
            message = getString(R.string.port_message),
            hint = getString(R.string.port_hint, Inputs.PORT_MIN, Inputs.PORT_MAX),
            inputType = InputType.TYPE_CLASS_NUMBER,
            confirm = getString(R.string.port_confirm),
            initial = port.toString(),
            parse = Inputs::port,
        ) { newPort ->
            port = newPort
            prefs.edit().putInt(PREF_PORT, newPort).apply()
            lastData = null
            deviceAddress = null
            invalidSentences = 0
            startLink()
            render()
        }
    }

    private fun <T : Any> showInputDialog(
        title: String,
        message: String,
        hint: String,
        inputType: Int,
        confirm: String,
        initial: String = "",
        parse: (String) -> T?,
        onValid: (T) -> Unit,
    ) {
        val input = EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            isSingleLine = true
            setText(initial)
        }
        val padding = (DIALOG_PADDING_DP * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(confirm, null)
            .create()
        dialog.setOnShowListener {
            // Eigener Listener, damit der Dialog bei falscher Eingabe offen bleibt.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = parse(input.text.toString())
                if (value == null) {
                    input.error = hint
                } else {
                    dialog.dismiss()
                    onValid(value)
                }
            }
        }
        dialog.show()
    }

    private companion object {
        const val DEFAULT_PORT = 1467
        const val PREFS_NAME = "chaincounter"
        const val PREF_PORT = "udp_port"
        const val TICK_MS = 500L
        const val STALE_AFTER_MS = 3000L
        const val LENGTH_CHECK_DELAY_MS = 2500L
        const val DIALOG_PADDING_DP = 24
    }
}
