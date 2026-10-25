package com.printer.zpl.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class AndroidBtTransport(
    private val device: BluetoothDevice
) : IPrinterTransport {

    companion object {
        private const val TAG = "AndroidBtTransport"
        // Standard Bluetooth Serial Port Profile (SPP) UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    @get:SuppressLint("MissingPermission")
    override val endpointName: String
        get() = "${try { device.name } catch (e: SecurityException) { null } ?: "Bluetooth Printer"} (${device.address})"

    override val isOpen: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    override suspend fun open(): Boolean = withContext(Dispatchers.IO) {
        if (isOpen) return@withContext true

        try {
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            sock.connect()
            outputStream = sock.outputStream
            inputStream = sock.inputStream
            socket = sock
            Log.i(TAG, "Connected to Bluetooth printer: ${device.address}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Bluetooth printer ${device.address}", e)
            close()
            false
        }
    }

    override fun close() {
        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Bluetooth socket", e)
        } finally {
            outputStream = null
            inputStream = null
            socket = null
        }
    }

    override suspend fun write(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val out = outputStream ?: return@withContext false
        try {
            val chunkSize = 2048
            var offset = 0
            while (offset < data.size) {
                val len = Math.min(chunkSize, data.size - offset)
                out.write(data, offset, len)
                out.flush()
                offset += len
                if (offset < data.size) {
                    Thread.sleep(10) // Small delay to avoid overflowing Bluetooth UART FIFO on Rongta/Milestone
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth write failed", e)
            false
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val input = inputStream ?: return@withContext ByteArray(0)
        val buffer = ByteArray(maxBytes)
        val startTime = System.currentTimeMillis()

        try {
            while (input.available() == 0) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    return@withContext ByteArray(0)
                }
                Thread.sleep(20)
            }
            val count = input.read(buffer)
            if (count > 0) buffer.copyOf(count) else ByteArray(0)
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth read failed", e)
            ByteArray(0)
        }
    }
}

