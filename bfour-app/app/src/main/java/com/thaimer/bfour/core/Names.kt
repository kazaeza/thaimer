package com.thaimer.bfour.core

import android.content.Context
import com.thaimer.bfour.R

/** BF-40 probes are black (1) and white (2), the same as on the thermometer's screen. */
fun probeName(context: Context, number: Int, probeCount: Int): String = when {
    probeCount == 2 && number == 1 -> context.getString(R.string.probe_black)
    probeCount == 2 && number == 2 -> context.getString(R.string.probe_white)
    else -> context.getString(R.string.probe_n, number)
}

fun linkText(context: Context, link: Link): String = context.getString(
    when (link) {
        Link.IDLE -> R.string.link_idle
        Link.BLUETOOTH_OFF -> R.string.link_bt_off
        Link.SEARCHING -> R.string.link_searching
        Link.CONNECTING -> R.string.link_connecting
        Link.CONNECTED -> R.string.link_connected
    }
)
