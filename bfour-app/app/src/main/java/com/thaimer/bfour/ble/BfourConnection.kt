package com.thaimer.bfour.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.util.isNotEmpty
import com.thaimer.bfour.core.Link

/**
 * Owns the GATT connection to one thermometer and keeps it alive: scans when the
 * device is unknown, reconnects with backoff when the link drops, and serialises
 * writes (Android allows only one outstanding GATT operation).
 *
 * Everything runs on the main thread; GATT callbacks are posted there.
 * Callers must hold the Bluetooth permissions before calling [start].
 */
@SuppressLint("MissingPermission")
class BfourConnection(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onLink(link: Link)
        fun onDeviceFound(address: String, deviceType: Int?)
        /** Notifications are on; the device accepts commands now. */
        fun onReady()
        fun onData(data: ByteArray)
    }

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private var running = false
    private var address: String? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var scanning = false
    private var attempt = 0

    private val queue = ArrayDeque<ByteArray>()
    private var writing = false

    fun start(savedAddress: String?) {
        if (running) return
        running = true
        address = savedAddress
        ContextCompat.registerReceiver(
            context, btStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        begin()
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { context.unregisterReceiver(btStateReceiver) }
        main.removeCallbacksAndMessages(null)
        stopScan()
        closeGatt()
        listener.onLink(Link.IDLE)
    }

    /** Drop the current link and find the thermometer again (used when data goes stale). */
    fun reconnect() {
        if (!running) return
        closeGatt()
        scheduleRetry()
    }

    /** Forget the paired thermometer and scan for any BFOUR device. */
    fun forget() {
        address = null
        if (running) {
            closeGatt()
            main.removeCallbacksAndMessages(null)
            attempt = 0
            begin()
        }
    }

    fun send(packet: ByteArray) {
        queue.addLast(packet)
        pump()
    }

    // ---- connection lifecycle -----------------------------------------------

    private fun begin() {
        if (!running || gatt != null || scanning) return
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            listener.onLink(Link.BLUETOOTH_OFF)
            return
        }
        val addr = address
        if (addr == null) startScan() else connect(addr)
    }

    private fun connect(addr: String) {
        val bt = adapter ?: return
        listener.onLink(Link.CONNECTING)
        val device = runCatching { bt.getRemoteDevice(addr) }.getOrNull()
        if (device == null) {
            address = null
            begin()
            return
        }
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
    }

    private val connectTimeout = Runnable {
        Log.w(TAG, "connect timeout")
        closeGatt()
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!running) return
        main.removeCallbacks(connectTimeout)
        listener.onLink(Link.SEARCHING)
        val delay = (RETRY_BASE_MS shl attempt.coerceAtMost(4)).coerceAtMost(RETRY_MAX_MS)
        attempt++
        main.postDelayed({ begin() }, delay)
    }

    private fun closeGatt() {
        main.removeCallbacks(connectTimeout)
        queue.clear()
        writing = false
        writeChar = null
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
    }

    // ---- scanning -----------------------------------------------------------

    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return scheduleRetry()
        if (scanning) return
        listener.onLink(Link.SEARCHING)
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BfourProtocol.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanning = runCatching { scanner.startScan(listOf(filter), settings, scanCallback) }.isSuccess
        if (!scanning) scheduleRetry()
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post { onFound(result) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed $errorCode")
            main.post {
                scanning = false
                scheduleRetry()
            }
        }
    }

    private fun onFound(result: ScanResult) {
        if (!scanning || !running) return
        val md = result.scanRecord?.manufacturerSpecificData
        val payload = if (md != null && md.isNotEmpty()) md.valueAt(0) else null
        val type = BfourProtocol.deviceTypeFromManufacturerPayload(payload)
        stopScan()
        val addr = result.device.address
        address = addr
        attempt = 0
        listener.onDeviceFound(addr, type)
        connect(addr)
    }

    // ---- GATT ---------------------------------------------------------------

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g != gatt) {
                    runCatching { g.close() }
                    return@post
                }
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    // connectTimeout keeps running until notifications are on.
                    // A short pause avoids flaky service discovery on some phones.
                    main.postDelayed({ if (g == gatt) g.discoverServices() }, 300)
                } else {
                    Log.i(TAG, "disconnected status=$status state=$newState")
                    closeGatt()
                    scheduleRetry()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g != gatt) return@post
                val service = g.getService(BfourProtocol.SERVICE)
                val write = service?.getCharacteristic(BfourProtocol.WRITE_CHAR)
                val notify = service?.getCharacteristic(BfourProtocol.NOTIFY_CHAR)
                if (status != BluetoothGatt.GATT_SUCCESS || write == null || notify == null) {
                    Log.w(TAG, "services missing, status=$status")
                    closeGatt()
                    scheduleRetry()
                    return@post
                }
                writeChar = write
                enableNotifications(g, notify)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            main.post {
                if (g != gatt || d.uuid != BfourProtocol.CCCD) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    closeGatt()
                    scheduleRetry()
                    return@post
                }
                onLinkReady()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post {
                if (g != gatt) return@post
                writing = false
                main.postDelayed({ pump() }, WRITE_GAP_MS)
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val copy = value.copyOf()
            main.post { if (g == gatt) listener.onData(copy) }
        }

        @Deprecated("Called instead of the 3-argument version before Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val copy = c.value?.copyOf() ?: return
            main.post { if (g == gatt) listener.onData(copy) }
        }
    }

    @Suppress("DEPRECATION")
    private fun enableNotifications(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(c, true)
        val cccd = c.getDescriptor(BfourProtocol.CCCD) ?: run {
            // Nothing to write; treat the link as ready.
            onLinkReady()
            return
        }
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, value)
        } else {
            cccd.value = value
            g.writeDescriptor(cccd)
        }
    }

    private fun onLinkReady() {
        main.removeCallbacks(connectTimeout)
        attempt = 0
        listener.onLink(Link.CONNECTED)
        listener.onReady()
        pump()
    }

    @Suppress("DEPRECATION")
    private fun pump() {
        if (writing) return
        val g = gatt ?: return
        val c = writeChar ?: return
        val packet = queue.removeFirstOrNull() ?: return
        writing = true
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = packet
            g.writeCharacteristic(c)
        }
        if (!ok) {
            // The stack is busy; try this packet again shortly.
            writing = false
            queue.addFirst(packet)
            main.postDelayed({ pump() }, WRITE_GAP_MS)
        }
    }

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> {
                    stopScan()
                    closeGatt()
                    main.removeCallbacksAndMessages(null)
                    listener.onLink(Link.BLUETOOTH_OFF)
                }
                BluetoothAdapter.STATE_ON -> {
                    attempt = 0
                    begin()
                }
            }
        }
    }

    private companion object {
        const val TAG = "BfourConnection"
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val RETRY_BASE_MS = 1_000L
        const val RETRY_MAX_MS = 10_000L
        const val WRITE_GAP_MS = 400L
    }
}
