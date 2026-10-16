package com.kazaeza.thaimer.ui

import kotlin.math.ceil

/** "03:07" from whole seconds. */
fun mmss(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

/** Time already done: counts up, floors. */
fun elapsedClock(ms: Long): String = mmss(ms.coerceAtLeast(0) / 1000)

/** Time to go: counts down, rounds up so "00:00" only shows at the very end. */
fun remainingClock(ms: Long): String = mmss(ceil(ms.coerceAtLeast(0) / 1000.0).toLong())

/** "1:30", "0:20", "3:00" for plan rows. */
fun shortDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$n $word"
}

fun minutes(seconds: Int): String = "${(seconds + 30) / 60} мин"

fun rounds(n: Int): String = plural(n, "раунд", "раунда", "раундов")

/** "6 ч 20 мин" / "45 мин". */
fun hoursMinutes(seconds: Long): String {
    val totalMinutes = (seconds + 30) / 60
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "$h ч $m мин" else "$m мин"
}

/** "сегодня", "вчера", "3 дня назад". */
fun daysAgo(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val day = 24 * 60 * 60 * 1000L
    val zone = java.util.TimeZone.getDefault()
    val today = (now + zone.getOffset(now)) / day
    val then = (timestamp + zone.getOffset(timestamp)) / day
    return when (val d = (today - then).toInt()) {
        0 -> "сегодня"
        1 -> "вчера"
        else -> plural(d, "день", "дня", "дней") + " назад"
    }
}
