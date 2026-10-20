package com.thaimer.bfour.core

enum class Link {
    /** Monitoring is not running (permissions missing or user stopped it). */
    IDLE,
    BLUETOOTH_OFF,
    SEARCHING,
    CONNECTING,
    CONNECTED,
}

data class ProbeState(
    /** 1-based, as the thermometer numbers them. */
    val number: Int,
    val celsius: Double?,
    val target: Target?,
    val ringing: Boolean,
)

data class ThermoState(
    val link: Link = Link.IDLE,
    val model: String = "BF-40",
    val unit: TempUnit = TempUnit.C,
    val probes: List<ProbeState> = emptyList(),
    /** Connection dropped while something was cooking. */
    val lostWhileCooking: Boolean = false,
) {
    val ringingProbes: List<ProbeState> get() = probes.filter { it.ringing }
}
