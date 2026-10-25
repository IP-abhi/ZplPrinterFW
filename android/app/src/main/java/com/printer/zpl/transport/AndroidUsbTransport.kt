package com.printer.zpl.transport

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidUsbTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) : IPrinterTransport {

    companion object {
        private const val TAG = "AndroidUsbTransport"
        const val RONGTA_VENDOR_ID = 0x0fe6
        const val RONGTA_PRODUCT_ID = 0x8800
    }

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointOut: UsbEndpoint? = null
    private var endpointIn: UsbEndpoint? = null

    override val endpointName: String
        get() = "${device.deviceName} (${device.manufacturerName ?: "Rongta"} - ${device.productName ?: "LabelPrinter"})"

    override val isOpen: Boolean
        get() = connection != null && endpointOut != null

    override suspend fun open(): Boolean = withContext(Dispatchers.IO) {
        if (isOpen) return@withContext true

        if (!usbManager.hasPermission(device)) {
            Log.e(TAG, "Missing USB permission for device ${device.deviceName}")
            return@withContext false
        }

        val conn = usbManager.openDevice(device) ?: run {
            Log.e(TAG, "Failed to open USB device connection")
            return@withContext false
        }

        // Find printer interface (or interface 0)
        var targetInterface: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER || targetInterface == null) {
                targetInterface = iface
            }
        }

        if (targetInterface == null) {
            Log.e(TAG, "No suitable USB interface found")
            conn.close()
            return@withContext false
        }

        if (!conn.claimInterface(targetInterface, true)) {
            Log.e(TAG, "Failed to claim USB interface")
            conn.close()
            return@withContext false
        }

        // Locate bulk OUT and IN endpoints
        var outEp: UsbEndpoint? = null
        var inEp: UsbEndpoint? = null

        for (i in 0 until targetInterface.endpointCount) {
            val ep = targetInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_OUT) {
                    outEp = ep
                } else if (ep.direction == UsbConstants.USB_DIR_IN) {
                    inEp = ep
                }
            }
        }

        if (outEp == null) {
            Log.e(TAG, "No bulk OUT endpoint found on interface")
            conn.releaseInterface(targetInterface)
            conn.close()
            return@withContext false
        }

        connection = conn
        usbInterface = targetInterface
        endpointOut = outEp
        endpointIn = inEp

        Log.i(TAG, "Successfully opened USB printer endpoint: ${device.deviceName}")
        true
    }

    override fun close() {
        try {
            usbInterface?.let { connection?.releaseInterface(it) }
            connection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing USB connection", e)
        } finally {
            connection = null
            usbInterface = null
            endpointOut = null
            endpointIn = null
        }
    }

    override suspend fun write(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext false
        val ep = endpointOut ?: return@withContext false

        var bytesWritten = 0
        val total = data.size
        val chunkSize = 4096

        while (bytesWritten < total) {
            val count = Math.min(chunkSize, total - bytesWritten)
            val written = conn.bulkTransfer(ep, data, bytesWritten, count, 5000)
            if (written < 0) {
                Log.e(TAG, "USB bulkTransfer write failed with code: $written")
                return@withContext false
            }
            bytesWritten += written
            if (bytesWritten < total) {
                Thread.sleep(5) // Prevent USB bus saturation on printer's USB controller
            }
        }

        true
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext ByteArray(0)
        val ep = endpointIn ?: return@withContext ByteArray(0)

        val buffer = ByteArray(maxBytes)
        val bytesRead = conn.bulkTransfer(ep, buffer, maxBytes, timeoutMs)

        if (bytesRead > 0) {
            buffer.copyOf(bytesRead)
        } else {
            ByteArray(0)
        }
    }
}

