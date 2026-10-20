package com.thaimer.bfour.ble

import com.thaimer.bfour.ble.BfourProtocol.Message
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BfourProtocolTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `temperatures are big-endian tenths of a degree celsius`() {
        // 0x0219 = 537 -> 53.7 °C, 0x00FA = 250 -> 25.0 °C
        val msg = BfourProtocol.parse(bytes(0x55, 0x00, 0x02, 0x19, 0x00, 0xFA), probeCount = 2)
        assertEquals(Message.Temperatures(listOf(53.7, 25.0)), msg)
    }

    @Test
    fun `FFFF means the probe is not connected`() {
        val msg = BfourProtocol.parse(bytes(0x55, 0x00, 0xFF, 0xFF, 0x01, 0x2C), probeCount = 2)
        assertEquals(Message.Temperatures(listOf(null, 30.0)), msg)
    }

    @Test
    fun `sign bit reads as zero like the official app`() {
        val msg = BfourProtocol.parse(bytes(0x55, 0x00, 0xFF, 0xCE, 0x00, 0x00), probeCount = 2)
        assertEquals(Message.Temperatures(listOf(0.0, 0.0)), msg)
    }

    @Test
    fun `short packet leaves missing probes empty`() {
        val msg = BfourProtocol.parse(bytes(0x55, 0x00, 0x01, 0x00), probeCount = 2)
        assertEquals(Message.Temperatures(listOf(25.6, null)), msg)
    }

    @Test
    fun `packets without the 0x55 header are ignored`() {
        assertNull(BfourProtocol.parse(bytes(0xAA, 0x00, 0x01, 0x00), probeCount = 2))
        assertNull(BfourProtocol.parse(bytes(0x55), probeCount = 2))
    }

    @Test
    fun `button on the thermometer dismisses the alarm`() {
        assertEquals(Message.AlarmDismissedOnDevice, BfourProtocol.parse(bytes(0x55, 0x07, 0x01, 0x00), 2))
        assertEquals(Message.Ack(0x07), BfourProtocol.parse(bytes(0x55, 0x07, 0x02, 0x03), 2))
    }

    @Test
    fun `acks for commands`() {
        assertEquals(Message.Ack(0x01), BfourProtocol.parse(bytes(0x55, 0x01, 0x00), 2))
        assertEquals(Message.Ack(0x02), BfourProtocol.parse(bytes(0x55, 0x02, 0x01), 2))
    }

    @Test
    fun `display unit command`() {
        assertArrayEquals(bytes(0x55, 0x01, 0x01, 0x00), BfourProtocol.setDisplayUnit(fahrenheit = false))
        assertArrayEquals(bytes(0x55, 0x01, 0x02, 0x00), BfourProtocol.setDisplayUnit(fahrenheit = true))
    }

    @Test
    fun `target command repeats the value as upper and lower bound`() {
        assertArrayEquals(
            bytes(0x55, 0x02, 0x01, 0x00, 0x3F, 0x00, 0x3F, 0x00),
            BfourProtocol.setTarget(probe = 1, degrees = 63),
        )
        // Values above 255 use both bytes: 0x0123 = 291
        assertArrayEquals(
            bytes(0x55, 0x02, 0x02, 0x01, 0x23, 0x01, 0x23, 0x00),
            BfourProtocol.setTarget(probe = 2, degrees = 291),
        )
        assertArrayEquals(
            bytes(0x55, 0x02, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00),
            BfourProtocol.setTarget(probe = 2, degrees = 0),
        )
    }

    @Test
    fun `stop alarm and interval commands`() {
        assertArrayEquals(bytes(0x55, 0x07, 0x07, 0x00), BfourProtocol.stopAlarm())
        assertArrayEquals(bytes(0x55, 0x0F, 0x05, 0x00), BfourProtocol.setAlarmInterval())
    }

    @Test
    fun `model detection from advertising`() {
        val type = BfourProtocol.deviceTypeFromManufacturerPayload(bytes(0x03, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF))
        assertEquals(BfourProtocol.TYPE_BF40, type)
        assertEquals("BF-40", BfourProtocol.modelName(type))
        assertEquals(2, BfourProtocol.probeCount(type))
        assertEquals(1, BfourProtocol.probeCount(BfourProtocol.TYPE_BF30))
        assertNull(BfourProtocol.deviceTypeFromManufacturerPayload(ByteArray(0)))
    }

    @Test
    fun `service uuids`() {
        assertEquals("0000ffb0-0000-1000-8000-00805f9b34fb", BfourProtocol.SERVICE.toString())
        assertEquals("0000ffb1-0000-1000-8000-00805f9b34fb", BfourProtocol.WRITE_CHAR.toString())
        assertEquals("0000ffb2-0000-1000-8000-00805f9b34fb", BfourProtocol.NOTIFY_CHAR.toString())
    }
}
