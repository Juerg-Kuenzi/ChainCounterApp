package ch.sy_isme.app

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.media.AudioManager
import android.media.ToneGenerator
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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Switch
import android.widget.TextView
import android.widget.ViewFlipper
import ch.sy_isme.app.net.UdpLink
import ch.sy_isme.app.protocol.ChainDirection
import ch.sy_isme.app.protocol.Command
import ch.sy_isme.app.protocol.DuplicateFilter
import ch.sy_isme.app.protocol.Format
import ch.sy_isme.app.protocol.Inputs
import ch.sy_isme.app.protocol.N2kTemperature
import ch.sy_isme.app.protocol.Nmea
import ch.sy_isme.app.protocol.ParseResult
import ch.sy_isme.app.protocol.TemperatureSelector
import ch.sy_isme.app.protocol.YdRaw
import java.io.IOException
import java.net.InetAddress
import kotlin.math.abs

class MainActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var connectivity: ConnectivityManager
    private lateinit var multicastLock: WifiManager.MulticastLock
    private lateinit var session: SessionState
    private lateinit var gestures: GestureDetector

    private lateinit var screens: Map<Screen, View>
    private lateinit var pager: ViewFlipper
    private lateinit var pageTitle: TextView
    private lateinit var pageDots: TextView
    private lateinit var lengthValue: TextView
    private lateinit var directionValue: TextView
    private lateinit var vccValue: TextView
    private lateinit var mainWarning: TextView
    private lateinit var chainAlarmBanner: TextView
    private lateinit var exhaustValue: TextView
    private lateinit var alarmLimitValue: TextView
    private lateinit var exhaustAlarm: View
    private lateinit var exhaustAlarmText: TextView
    private lateinit var muteButton: Button
    private lateinit var n2kWarning: TextView
    private lateinit var startsValue: TextView
    private lateinit var actualRuntimeValue: TextView
    private lateinit var totalRuntimeValue: TextView
    private lateinit var deviceValue: TextView
    private lateinit var bridgeValue: TextView
    private lateinit var versionValue: TextView
    private lateinit var invalidRow: View
    private lateinit var invalidValue: TextView
    private lateinit var detailTitle: TextView
    private lateinit var detailDescription: TextView
    private lateinit var detailCurrent: TextView
    private lateinit var detailInput: EditText
    private lateinit var detailSend: Button
    private lateinit var detailMessage: TextView
    private lateinit var detailConnection: TextView

    private var port = DEFAULT_PORT
    private var n2kPort = DEFAULT_N2K_PORT
    private var alarmLimit = DEFAULT_ALARM_LIMIT
    private var network: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var link: UdpLink? = null
    private var n2kLink: UdpLink? = null
    private var tone: ToneGenerator? = null
    private var lastToneAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            render()
            main.postDelayed(this, TICK_MS)
        }
    }

    /** Wendet die gespeicherte Wahl Hell oder Dunkel an. Ohne Wahl gilt die Android-Einstellung. */
    override fun attachBaseContext(newBase: Context) {
        val stored = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!stored.contains(PREF_DARK)) {
            super.attachBaseContext(newBase)
            return
        }
        val nightMode = if (stored.getBoolean(PREF_DARK, false)) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        val type = newBase.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        // Nur uiMode ueberschreiben, damit Drehen des Bildschirms weiter korrekt wirkt.
        val override = Configuration().apply { uiMode = type or nightMode }
        super.attachBaseContext(newBase.createConfigurationContext(override))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        session = lastNonConfigurationInstance as? SessionState ?: SessionState()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        port = prefs.getInt(PREF_PORT, DEFAULT_PORT)
        n2kPort = prefs.getInt(PREF_N2K_PORT, DEFAULT_N2K_PORT)
        alarmLimit = prefs.getInt(PREF_ALARM_LIMIT, DEFAULT_ALARM_LIMIT)
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("chaincounter").apply {
            setReferenceCounted(false)
        }

        bindViews()
        setUpNavigation()
        setUpPages()
        setUpDarkSwitch()
        versionValue.text = packageManager.getPackageInfo(packageName, 0).versionName
        showScreen(session.screen)
    }

    override fun onRetainNonConfigurationInstance(): Any = session

    private fun bindViews() {
        val detail = findViewById<View>(R.id.detail_screen)
        screens = mapOf(
            Screen.MAIN to findViewById(R.id.main_screen),
            Screen.SETTINGS to findViewById(R.id.settings_screen),
            Screen.INFO to findViewById(R.id.info_screen),
            Screen.ZERO to detail,
            Screen.LENGTH to detail,
            Screen.PORT to detail,
            Screen.DEBOUNCE to detail,
            Screen.FACTOR to detail,
            Screen.RESTART to detail,
            Screen.N2K_PORT to detail,
            Screen.EXHAUST_ALARM to detail,
        )
        pager = findViewById(R.id.pager)
        pageTitle = findViewById(R.id.page_title)
        pageDots = findViewById(R.id.page_dots)
        lengthValue = findViewById(R.id.length_value)
        directionValue = findViewById(R.id.direction_value)
        vccValue = findViewById(R.id.vcc_value)
        mainWarning = findViewById(R.id.main_warning)
        chainAlarmBanner = findViewById(R.id.chain_alarm_banner)
        exhaustValue = findViewById(R.id.exhaust_value)
        alarmLimitValue = findViewById(R.id.alarm_limit_value)
        exhaustAlarm = findViewById(R.id.exhaust_alarm)
        exhaustAlarmText = findViewById(R.id.exhaust_alarm_text)
        muteButton = findViewById(R.id.mute_button)
        n2kWarning = findViewById(R.id.n2k_warning)
        startsValue = findViewById(R.id.starts_value)
        actualRuntimeValue = findViewById(R.id.actual_runtime_value)
        totalRuntimeValue = findViewById(R.id.total_runtime_value)
        deviceValue = findViewById(R.id.device_value)
        bridgeValue = findViewById(R.id.bridge_value)
        versionValue = findViewById(R.id.version_value)
        invalidRow = findViewById(R.id.invalid_row)
        invalidValue = findViewById(R.id.invalid_value)
        detailTitle = findViewById(R.id.detail_title)
        detailDescription = findViewById(R.id.detail_description)
        detailCurrent = findViewById(R.id.detail_current)
        detailInput = findViewById(R.id.detail_input)
        detailSend = findViewById(R.id.detail_send)
        detailMessage = findViewById(R.id.detail_message)
        detailConnection = findViewById(R.id.detail_connection)
    }

    private fun setUpNavigation() {
        findViewById<ImageButton>(R.id.menu_button).setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add(0, MENU_SETTINGS, 0, R.string.settings_title)
                menu.add(0, MENU_INFO, 1, R.string.info_title)
                setOnMenuItemClickListener { item ->
                    showScreen(if (item.itemId == MENU_SETTINGS) Screen.SETTINGS else Screen.INFO)
                    true
                }
                show()
            }
        }
        findViewById<Button>(R.id.settings_back).setOnClickListener { goBack() }
        findViewById<Button>(R.id.info_back).setOnClickListener { goBack() }
        findViewById<Button>(R.id.detail_back).setOnClickListener { goBack() }
        val rows = mapOf(
            R.id.row_zero to Screen.ZERO,
            R.id.row_length to Screen.LENGTH,
            R.id.row_port to Screen.PORT,
            R.id.row_debounce to Screen.DEBOUNCE,
            R.id.row_factor to Screen.FACTOR,
            R.id.row_restart to Screen.RESTART,
            R.id.row_n2k_port to Screen.N2K_PORT,
            R.id.row_alarm to Screen.EXHAUST_ALARM,
        )
        rows.forEach { (id, screen) ->
            findViewById<Button>(id).setOnClickListener { showScreen(screen) }
        }
        detailSend.setOnClickListener { submitDetail() }
        muteButton.setOnClickListener {
            session.exhaust.mute()
            tone?.stopTone()
            render()
        }
        chainAlarmBanner.setOnClickListener { showPage(PAGE_N2K) }
    }

    /** Zwei Seiten, gewechselt durch seitliches Wischen oder Tippen auf die Punkte. */
    private fun setUpPages() {
        pager.displayedChild = session.page
        val threshold = SWIPE_MIN_DP * resources.displayMetrics.density
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) < threshold || abs(dx) < 2 * abs(dy)) return false
                showPage(if (dx < 0) session.page + 1 else session.page - 1)
                return true
            }
        })
        pageDots.setOnClickListener { showPage((session.page + 1) % PAGE_COUNT) }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (session.screen == Screen.MAIN) gestures.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    private fun showPage(page: Int) {
        if (page < 0 || page >= PAGE_COUNT || page == session.page) return
        if (ValueAnimator.areAnimatorsEnabled()) {
            val forward = page > session.page
            pager.setInAnimation(this, if (forward) R.anim.slide_in_right else R.anim.slide_in_left)
            pager.setOutAnimation(this, if (forward) R.anim.slide_out_left else R.anim.slide_out_right)
        } else {
            pager.inAnimation = null
            pager.outAnimation = null
        }
        session.page = page
        pager.displayedChild = page
        render()
    }

    private fun setUpDarkSwitch() {
        val switch = findViewById<Switch>(R.id.dark_switch)
        val current = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val dark = current == Configuration.UI_MODE_NIGHT_YES
        switch.isChecked = dark
        switch.setText(if (dark) R.string.dark_on else R.string.dark_off)
        switch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_DARK, checked).apply()
            recreate()
        }
    }

    @Deprecated("Bis targetSdk 34 der einfachste Weg fuer die Zurueck-Taste des Systems")
    override fun onBackPressed() {
        if (session.screen == Screen.MAIN) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        } else {
            goBack()
        }
    }

    private fun goBack() {
        showScreen(if (session.screen.isDetail) Screen.SETTINGS else Screen.MAIN)
    }

    private fun showScreen(screen: Screen) {
        val visible = screens.getValue(screen)
        screens.values.toSet().forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
        hideKeyboard()
        if (screen.isDetail && screen != session.screen) session.clearRestoredNotice()
        session.screen = screen
        if (screen.isDetail) configureDetail(screen)
        render()
    }

    private fun configureDetail(screen: Screen) {
        val number = InputType.TYPE_CLASS_NUMBER
        when (screen) {
            Screen.ZERO -> setDetail(R.string.zero_title, R.string.counter_storage_hint, R.string.zero_title)
            Screen.LENGTH -> setDetail(
                R.string.length_title, R.string.counter_storage_hint, R.string.length_title,
                number, getString(R.string.length_hint, Inputs.LENGTH_MAX),
            )
            Screen.PORT -> setDetail(
                R.string.port_title, R.string.port_message, R.string.port_confirm,
                number, getString(R.string.port_hint, Inputs.PORT_MIN, Inputs.PORT_MAX), port.toString(),
            )
            Screen.DEBOUNCE -> setDetail(
                R.string.debounce_title, R.string.no_readback_hint, R.string.send,
                number, getString(R.string.debounce_hint, Inputs.DEBOUNCE_MIN, Inputs.DEBOUNCE_MAX),
            )
            Screen.FACTOR -> setDetail(
                R.string.factor_title, R.string.no_readback_hint, R.string.send,
                number or InputType.TYPE_NUMBER_FLAG_DECIMAL, getString(R.string.factor_hint),
            )
            Screen.RESTART -> setDetail(R.string.restart_title, R.string.restart_message, R.string.restart_title)
            Screen.N2K_PORT -> setDetail(
                R.string.n2k_port_title, R.string.n2k_port_message, R.string.port_confirm,
                number, getString(R.string.port_hint, Inputs.PORT_MIN, Inputs.PORT_MAX), n2kPort.toString(),
            )
            Screen.EXHAUST_ALARM -> setDetail(
                R.string.alarm_title, R.string.alarm_message, R.string.save,
                number, getString(R.string.alarm_hint, Inputs.ALARM_MIN, Inputs.ALARM_MAX), alarmLimit.toString(),
            )
            else -> Unit
        }
    }

    private fun setDetail(
        title: Int,
        description: Int,
        action: Int,
        inputType: Int = 0,
        hint: String? = null,
        initial: String = "",
    ) {
        detailTitle.setText(title)
        detailDescription.setText(description)
        detailSend.setText(action)
        detailInput.error = null
        detailInput.setText(initial)
        if (hint == null) {
            detailInput.visibility = View.GONE
        } else {
            detailInput.visibility = View.VISIBLE
            detailInput.inputType = inputType
            detailInput.hint = hint
        }
    }

    private fun submitDetail() {
        val text = detailInput.text.toString()
        when (session.screen) {
            Screen.ZERO -> send(Command.ZeroCounter, getString(R.string.zero_sent))
            Screen.RESTART -> send(Command.Restart, getString(R.string.restart_sent))
            Screen.LENGTH -> Inputs.length(text).orInputError { meters ->
                send(Command.SetLength(meters), getString(R.string.length_sent, meters))
            }
            Screen.DEBOUNCE -> Inputs.debounce(text).orInputError { millis ->
                send(Command.SetDebounce(millis), getString(R.string.debounce_sent, millis))
            }
            Screen.FACTOR -> Inputs.factor(text).orInputError { factor ->
                send(Command.SetFactor(factor), getString(R.string.factor_sent, factor.toPlainString()))
            }
            Screen.PORT -> Inputs.port(text).orInputError { newPort -> changePort(newPort) }
            Screen.N2K_PORT -> Inputs.port(text).orInputError { newPort -> changeN2kPort(newPort) }
            Screen.EXHAUST_ALARM -> Inputs.alarm(text).orInputError { limit ->
                alarmLimit = limit
                prefs.edit().putInt(PREF_ALARM_LIMIT, limit).apply()
                session.messages[Screen.EXHAUST_ALARM] = getString(R.string.alarm_saved, limit)
                render()
            }
            else -> Unit
        }
    }

    private inline fun <T : Any> T?.orInputError(onValid: (T) -> Unit) {
        if (this == null) {
            detailInput.error = detailInput.hint
        } else {
            hideKeyboard()
            onValid(this)
        }
    }

    private fun send(command: Command, message: String) {
        val now = SystemClock.elapsedRealtime()
        val target = session.deviceAddress
        val active = link
        val screen = session.screen
        if (target == null || active == null || !session.isFresh(now)) {
            session.messages[screen] = getString(R.string.command_no_device)
        } else {
            session.messages[screen] = message
            session.lastSendScreen = screen
            session.clearRestoredNotice()
            active.send(command.sentence, target, port)
        }
        render()
    }

    private fun changePort(newPort: Int) {
        if (newPort == n2kPort) {
            detailInput.error = getString(R.string.port_in_use)
            return
        }
        port = newPort
        prefs.edit().putInt(PREF_PORT, newPort).apply()
        session.forgetDevice()
        session.messages[Screen.PORT] = getString(R.string.port_changed, newPort)
        startLinks()
        render()
    }

    private fun changeN2kPort(newPort: Int) {
        if (newPort == port) {
            detailInput.error = getString(R.string.port_in_use)
            return
        }
        n2kPort = newPort
        prefs.edit().putInt(PREF_N2K_PORT, newPort).apply()
        session.exhaust.forget()
        session.bridgeAddress = null
        session.n2kReceiveError = null
        session.messages[Screen.N2K_PORT] = getString(R.string.port_changed, newPort)
        startLinks()
        render()
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(findViewById<View>(R.id.root).windowToken, 0)
    }

    override fun onStart() {
        super.onStart()
        multicastLock.acquire()
        tone = try {
            ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
        } catch (_: RuntimeException) {
            null
        }
        // WLAN ohne Internet ist an Bord der Normalfall. Android wuerde sonst auf Mobilfunk routen.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(available: Network) {
                if (available != network) {
                    network = available
                    startLinks()
                }
            }

            override fun onLost(lost: Network) {
                if (lost == network) {
                    network = null
                    stopLinks()
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
        networkCallback?.let(connectivity::unregisterNetworkCallback)
        networkCallback = null
        network = null
        stopLinks()
        tone?.release()
        tone = null
        if (multicastLock.isHeld) multicastLock.release()
        super.onStop()
    }

    private fun startLinks() {
        stopLinks()
        val active = network ?: return
        link = UdpLink(
            port = port,
            configureSocket = { active.bindSocket(it) },
            listener = object : UdpLink.Listener {
                override fun onSentence(text: String, sender: InetAddress) {
                    main.post { handleSentence(text, sender) }
                }

                override fun onReceiveError(error: IOException) {
                    main.post { session.receiveError = error.message ?: error.toString() }
                }

                override fun onSendResult(sentence: String, error: IOException?) {
                    if (error != null) {
                        main.post {
                            session.lastSendScreen?.let { screen ->
                                session.messages[screen] = getString(
                                    R.string.command_send_failed,
                                    error.message ?: error.toString(),
                                )
                            }
                            render()
                        }
                    }
                }
            },
        ).also { it.start() }

        // Auswertung auf dem Empfangs-Thread: Die Bridge sendet einige hundert Frames pro Sekunde.
        val duplicates = DuplicateFilter()
        val exhaust = TemperatureSelector(source = N2kTemperature.SOURCE_EXHAUST_GAS)
        n2kLink = UdpLink(
            port = n2kPort,
            bufferSize = N2K_BUFFER_SIZE,
            configureSocket = { active.bindSocket(it) },
            listener = object : UdpLink.Listener {
                override fun onSentence(text: String, sender: InetAddress) {
                    val now = SystemClock.elapsedRealtime()
                    if (duplicates.isDuplicate(text, now)) return
                    var latest: Double? = null
                    for (frame in YdRaw.parsePacket(text)) {
                        exhaust.onFrame(frame, now)?.let { latest = it }
                    }
                    val value = latest
                    main.post {
                        session.bridgeAddress = sender
                        session.n2kReceiveError = null
                        if (value != null) {
                            session.exhaust.onReading(value, SystemClock.elapsedRealtime())
                            render()
                        }
                    }
                }

                override fun onReceiveError(error: IOException) {
                    main.post { session.n2kReceiveError = error.message ?: error.toString() }
                }

                override fun onSendResult(sentence: String, error: IOException?) = Unit
            },
        ).also { it.start() }
    }

    private fun stopLinks() {
        link?.stop()
        link = null
        n2kLink?.stop()
        n2kLink = null
    }

    private fun handleSentence(text: String, sender: InetAddress) {
        when (val result = Nmea.parse(text)) {
            is ParseResult.Data -> {
                session.onData(result.data, sender, SystemClock.elapsedRealtime())
                render()
            }
            is ParseResult.OtherSentence -> Unit
            is ParseResult.Invalid -> session.invalidSentences++
        }
    }

    private fun render() {
        val now = SystemClock.elapsedRealtime()
        session.onTick(now)
        session.exhaust.update(now, alarmLimit.toDouble())
        renderPageHeader()
        renderMain(now)
        renderN2k(now)
        renderInfo()
        if (session.screen.isDetail) renderDetail(now)
        soundAlarm(now)
    }

    private fun renderPageHeader() {
        pageTitle.setText(if (session.page == PAGE_CHAIN) R.string.page_chain else R.string.page_n2k)
        pageDots.text = if (session.page == PAGE_CHAIN) "● ○" else "○ ●"
        pageDots.contentDescription = getString(R.string.page_position, session.page + 1, PAGE_COUNT)
    }

    private fun renderMain(now: Long) {
        val data = session.lastData
        val fresh = session.isFresh(now)
        lengthValue.setTextColor(getColor(if (fresh) R.color.ink else R.color.muted))
        if (data == null) {
            val empty = getString(R.string.no_value)
            lengthValue.text = empty
            directionValue.text = empty
            vccValue.text = empty
        } else {
            lengthValue.text = Format.oneDecimal(data.chainLengthM)
            directionValue.setText(
                if (data.direction == ChainDirection.DOWN) R.string.direction_down else R.string.direction_up,
            )
            vccValue.text = getString(R.string.vcc_value, Format.oneDecimal(data.vccVolt))
        }
        val warning = connectionWarning(now)
        mainWarning.text = warning
        mainWarning.visibility = if (warning == null) View.GONE else View.VISIBLE

        // Der Alarm muss auch auf der Seite der Ankerkette sichtbar sein.
        val alarm = session.exhaust.alarmActive
        chainAlarmBanner.visibility = if (alarm) View.VISIBLE else View.GONE
        if (alarm) chainAlarmBanner.text = alarmText()
    }

    private fun alarmText(): String = getString(
        R.string.alarm_banner,
        Format.oneDecimal(session.exhaust.celsius ?: 0.0),
        alarmLimit,
    )

    private fun renderN2k(now: Long) {
        val exhaust = session.exhaust
        val fresh = exhaust.isFresh(now)
        val value = exhaust.celsius
        exhaustValue.text = if (value == null) {
            getString(R.string.no_value)
        } else {
            getString(R.string.celsius_value, Format.oneDecimal(value))
        }
        exhaustValue.setTextColor(getColor(if (fresh) R.color.ink else R.color.muted))
        alarmLimitValue.text = getString(R.string.celsius_whole, alarmLimit)

        exhaustAlarm.visibility = if (exhaust.alarmActive) View.VISIBLE else View.GONE
        if (exhaust.alarmActive) {
            exhaustAlarmText.text = alarmText()
            muteButton.visibility = if (exhaust.muted) View.GONE else View.VISIBLE
        }

        val warning = when {
            fresh -> null
            network == null -> getString(R.string.status_no_wifi)
            value != null -> getString(R.string.exhaust_stale, exhaust.secondsSinceData(now))
            session.n2kReceiveError != null ->
                getString(R.string.status_port_error, n2kPort, session.n2kReceiveError)
            session.bridgeAddress != null -> getString(R.string.exhaust_waiting_sensor)
            else -> getString(R.string.status_waiting, n2kPort)
        }
        n2kWarning.text = warning
        n2kWarning.visibility = if (warning == null) View.GONE else View.VISIBLE
    }

    /** Wiederholter Ton, solange der Alarm aktiv und nicht stummgeschaltet ist. */
    private fun soundAlarm(now: Long) {
        if (!session.exhaust.toneWanted) return
        if (now - lastToneAt < TONE_INTERVAL_MS) return
        lastToneAt = now
        tone?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, TONE_DURATION_MS)
    }

    /** Null bedeutet: Die angezeigten Daten sind aktuell. */
    private fun connectionWarning(now: Long): String? {
        if (session.isFresh(now)) return null
        val parts = mutableListOf<String>()
        if (network == null) parts += getString(R.string.status_no_wifi)
        if (session.lastData != null) {
            parts += getString(R.string.stale_warning, session.secondsSinceData(now))
        } else if (network != null) {
            val error = session.receiveError
            parts += if (error != null) {
                getString(R.string.status_port_error, port, error)
            } else {
                getString(R.string.status_waiting, port)
            }
        }
        return parts.joinToString("\n")
    }

    private fun renderInfo() {
        val data = session.lastData
        val empty = getString(R.string.no_value)
        startsValue.text = data?.systemStarts?.toString() ?: empty
        actualRuntimeValue.text = data?.let { Format.duration(it.actualRuntimeS) } ?: empty
        totalRuntimeValue.text = data?.let { Format.duration(it.totalRuntimeS) } ?: empty
        deviceValue.text = session.deviceAddress?.hostAddress ?: empty
        bridgeValue.text = session.bridgeAddress?.hostAddress ?: empty
        invalidValue.text = session.invalidSentences.toString()
        invalidRow.visibility = if (session.invalidSentences > 0) View.VISIBLE else View.GONE
    }

    private fun renderDetail(now: Long) {
        val screen = session.screen
        val fresh = session.isFresh(now)
        val local = screen == Screen.PORT || screen == Screen.N2K_PORT || screen == Screen.EXHAUST_ALARM

        val showsLength = screen == Screen.ZERO || screen == Screen.LENGTH
        detailCurrent.visibility = if (showsLength) View.VISIBLE else View.GONE
        if (showsLength) {
            val length = session.lastData?.let { Format.oneDecimal(it.chainLengthM) }
            detailCurrent.text = getString(R.string.current_length, length ?: getString(R.string.no_value))
        }

        detailSend.isEnabled = local || fresh
        val message = session.messages[screen]
        detailMessage.text = message
        detailMessage.visibility = if (message == null) View.GONE else View.VISIBLE

        // Die ChainCounter-Verbindung ist nur fuer Einstellungen wichtig, die ans Geraet gehen.
        val warning = if (local) null else connectionWarning(now)
        when {
            warning != null -> {
                detailConnection.text = warning
                detailConnection.setBackgroundColor(getColor(R.color.warning))
                detailConnection.setTextColor(getColor(R.color.on_warning))
                detailConnection.visibility = View.VISIBLE
            }
            !local && session.receptionRestored -> {
                detailConnection.setText(R.string.reception_restored)
                detailConnection.setBackgroundColor(getColor(R.color.rule))
                detailConnection.setTextColor(getColor(R.color.ink))
                detailConnection.visibility = View.VISIBLE
            }
            else -> detailConnection.visibility = View.GONE
        }
    }

    private companion object {
        const val DEFAULT_PORT = 1467
        const val DEFAULT_N2K_PORT = 1457
        const val DEFAULT_ALARM_LIMIT = 50
        const val N2K_BUFFER_SIZE = 4096
        const val PREFS_NAME = "chaincounter"
        const val PREF_PORT = "udp_port"
        const val PREF_N2K_PORT = "n2k_port"
        const val PREF_ALARM_LIMIT = "exhaust_alarm_limit"
        const val PREF_DARK = "dark_mode"
        const val TICK_MS = 500L
        const val TONE_INTERVAL_MS = 2000L
        const val TONE_DURATION_MS = 1000
        const val SWIPE_MIN_DP = 80
        const val PAGE_CHAIN = 0
        const val PAGE_N2K = 1
        const val PAGE_COUNT = 2
        const val MENU_SETTINGS = 1
        const val MENU_INFO = 2
    }
}
