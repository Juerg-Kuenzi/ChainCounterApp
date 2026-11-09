package ch.sy_isme.app.protocol

import java.util.Locale

object Format {
    fun oneDecimal(value: Double): String = "%.1f".format(Locale.ROOT, value)

    /** Beispiele: 01:00:05, 1 d 00:00:00 */
    fun duration(seconds: Long): String {
        val days = seconds / 86_400
        val time = "%02d:%02d:%02d".format(
            Locale.ROOT,
            seconds % 86_400 / 3_600,
            seconds % 3_600 / 60,
            seconds % 60,
        )
        return if (days > 0) "$days d $time" else time
    }
}
