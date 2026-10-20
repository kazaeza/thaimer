package com.thaimer.bfour.ble

import java.util.Locale
import java.util.UUID

/**
 * Wire format of BFOUR grill thermometers (BF-20/30/40/60). See PROTOCOL.md.
 * Pure Kotlin so it can be unit-tested without a device.
 */
object BfourProtocol {
    val SERVICE: UUID = uuid16(0xFFB0)
    val WRITE_CHAR: UUID = uuid16(0xFFB1)
    val NOTIFY_CHAR: UUID = uuid16(0xFFB2)
    val CCCD: UUID = uuid16(0x2902)

    const val HEADER = 0x55

    const val CMD_TEMPS = 0x00
    const val CMD_UNIT = 0x01
    const val CMD_TARGET = 0x02
    const val CMD_ALARM = 0x07
    const val CMD_ALARM_INTERVAL = 0x0F

    const val ALL_PROBES_MASK = 0x07
    const val DEFAULT_ALARM_INTERVAL = 5

    /** First byte of the manufacturer data payload (after the 2-byte company id). */
    const val TYPE_BF20 = 1
    const val TYPE_BF40 = 3
    const val TYPE_BF30 = 4
    const val TYPE_BF60 = 5

    private const val NO_PROBE = 0xFFFF

    fun probeCount(deviceType: Int?): Int = when (deviceType) {
        TYPE_BF30 -> 1
        TYPE_BF20, TYPE_BF40, TYPE_BF60, null -> 2
        else -> 6
    }

    fun modelName(deviceType: Int?): String = when (deviceType) {
        TYPE_BF20 -> "BF-20"
        TYPE_BF30 -> "BF-30"
        TYPE_BF40 -> "BF-40"
        TYPE_BF60 -> "BF-60"
        else -> "BFOUR"
    }

    // ---- App -> thermometer -------------------------------------------------

    fun setDisplayUnit(fahrenheit: Boolean): ByteArray =
        bytes(HEADER, CMD_UNIT, if (fahrenheit) 2 else 1, 0)

    /**
     * Target for one probe, in whole degrees of the unit currently shown on the
     * thermometer's display. 0 disables the target.
     */
    fun setTarget(probe: Int, degrees: Int): ByteArray {
        val t = degrees.coerceIn(0, 0xFFFF)
        // The official app sends the same value as upper and lower bound when
        // no range is configured.
        return bytes(HEADER, CMD_TARGET, probe, t shr 8, t and 0xFF, t shr 8, t and 0xFF, 0)
    }

    fun stopAlarm(): ByteArray = bytes(HEADER, CMD_ALARM, ALL_PROBES_MASK, 0)

    fun setAlarmInterval(interval: Int = DEFAULT_ALARM_INTERVAL): ByteArray =
        bytes(HEADER, CMD_ALARM_INTERVAL, interval.coerceIn(0, 0xFF), 0)

    // ---- Thermometer -> app -------------------------------------------------

    sealed interface Message {
        /** Index 0 is probe 1. `null` means no probe connected. */
        data class Temperatures(val celsius: List<Double?>) : Message
        data object AlarmDismissedOnDevice : Message
        data class Ack(val command: Int) : Message
        data class Unknown(val raw: ByteArray) : Message
    }

    fun parse(data: ByteArray, probeCount: Int): Message? {
        if (data.size < 2 || data.u(0) != HEADER) return null
        return when (val cmd = data.u(1)) {
            CMD_TEMPS -> Message.Temperatures(
                (0 until probeCount).map { i -> decodeTemp(data, 2 + i * 2) }
            )
            CMD_ALARM -> if (data.size > 2 && data.u(2) == 1) {
                Message.AlarmDismissedOnDevice
            } else {
                Message.Ack(cmd)
            }
            CMD_UNIT, CMD_TARGET, CMD_ALARM_INTERVAL -> Message.Ack(cmd)
            else -> Message.Unknown(data)
        }
    }

    private fun decodeTemp(data: ByteArray, offset: Int): Double? {
        if (offset + 1 >= data.size) return null
        val hi = data.u(offset)
        val lo = data.u(offset + 1)
        val raw = (hi shl 8) or lo
        if (raw == NO_PROBE) return null
        // Same as the official app: a set sign bit is reported as 0 °C.
        if (hi and 0x80 != 0) return 0.0
        return raw / 10.0
    }

    /** Device type from manufacturer data payload (company id already stripped). */
    fun deviceTypeFromManufacturerPayload(payload: ByteArray?): Int? =
        payload?.takeIf { it.isNotEmpty() }?.u(0)

    private fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun uuid16(short: Int): UUID =
        UUID.fromString(String.format(Locale.US, "0000%04X-0000-1000-8000-00805F9B34FB", short))
}
