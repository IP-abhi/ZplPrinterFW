package com.printer.zpl.ui.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.printer.zpl.ZplConfig
import com.printer.zpl.ZplNative
import com.printer.zpl.transport.AndroidBtTransport
import com.printer.zpl.transport.AndroidUsbTransport
import com.printer.zpl.transport.IPrinterTransport
import com.printer.zpl.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConnectionType {
    NONE, BLUETOOTH, USB
}

data class ConnectedDeviceInfo(
    val name: String = "No printer connected",
    val model: String = "",
    val addressOrEndpoint: String = "",
    val type: ConnectionType = ConnectionType.NONE
)

class PrinterViewModel : ViewModel() {

    private val _activeTransport = MutableStateFlow<IPrinterTransport?>(null)
    val activeTransport: StateFlow<IPrinterTransport?> = _activeTransport.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _statusMessage = MutableStateFlow("Disconnected")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _deviceInfo = MutableStateFlow(ConnectedDeviceInfo())
    val deviceInfo: StateFlow<ConnectedDeviceInfo> = _deviceInfo.asStateFlow()

    // Dialogs / Notifications
    private val _dialogMessage = MutableStateFlow<Pair<String, String>?>(null)
    val dialogMessage: StateFlow<Pair<String, String>?> = _dialogMessage.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // Printing Defaults & Tuning
    var darkness = MutableStateFlow(18f)
    var printSpeed = MutableStateFlow(4f)
    var mediaType = MutableStateFlow(0) // 0: Direct Thermal, 1: Thermal Transfer
    var ditherMode = MutableStateFlow(1) // 0: Threshold, 1: Floyd-Steinberg, 2: Atkinson
    var invertPolarity = MutableStateFlow(false)
    var autoRotate = MutableStateFlow(false)
    var trimMargins = MutableStateFlow(false)
    var safeMargins = MutableStateFlow(false)

    fun resetPrintingDefaults() {
        darkness.value = 18f
        printSpeed.value = 4f
        mediaType.value = 0
    }

    fun dismissDialog() {
        _dialogMessage.value = null
    }

    fun connectUsb(manager: UsbManager, device: UsbDevice) {
        _isBusy.value = true
        _statusMessage.value = "Connecting to USB: ${device.deviceName}..."

        viewModelScope.launch {
            val transport = AndroidUsbTransport(manager, device)
            val success = transport.open()
            _isBusy.value = false

            if (success) {
                _activeTransport.value?.close()
                _activeTransport.value = transport
                _isConnected.value = true
                val endpoint = transport.endpointName
                _statusMessage.value = "Connected (USB): $endpoint"
                _deviceInfo.value = ConnectedDeviceInfo(
                    name = device.productName ?: device.deviceName ?: "USB Printer",
                    model = "USB Vendor: 0x${device.vendorId.toString(16)}:0x${device.productId.toString(16)}",
                    addressOrEndpoint = endpoint,
                    type = ConnectionType.USB
                )
                _toastEvent.tryEmit("Connected to USB printer")
            } else {
                _statusMessage.value = "Failed to connect to USB device"
                _toastEvent.tryEmit("Failed to open USB endpoint")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectBluetooth(device: BluetoothDevice) {
        _isBusy.value = true
        val devName = device.name ?: device.address
        _statusMessage.value = "Connecting to Bluetooth: $devName..."

        viewModelScope.launch {
            val transport = AndroidBtTransport(device)
            val success = transport.open()
            _isBusy.value = false

            if (success) {
                _activeTransport.value?.close()
                _activeTransport.value = transport
                _isConnected.value = true
                val endpoint = transport.endpointName
                _statusMessage.value = "Connected (BT): $endpoint"
                _deviceInfo.value = ConnectedDeviceInfo(
                    name = device.name ?: "Bluetooth Printer",
                    model = "Thermal Printer (ESC/POS / ZPL)",
                    addressOrEndpoint = device.address,
                    type = ConnectionType.BLUETOOTH
                )
                _toastEvent.tryEmit("Connected to Bluetooth printer")
            } else {
                _statusMessage.value = "Failed to connect to Bluetooth printer"
                _toastEvent.tryEmit("Bluetooth connection failed")
            }
        }
    }

    fun disconnect() {
        _activeTransport.value?.close()
        _activeTransport.value = null
        _isConnected.value = false
        _statusMessage.value = "Disconnected"
        _deviceInfo.value = ConnectedDeviceInfo()
        _toastEvent.tryEmit("Printer disconnected")
    }

    fun executeDiagnosticAction(action: String) {
        val transport = _activeTransport.value ?: run {
            _toastEvent.tryEmit("Connect a printer first!")
            return
        }

        _isBusy.value = true
        viewModelScope.launch {
            val config = ZplConfig.fromInches(4.0f, 6.0f, 203).copy(
                darkness = darkness.value.toInt(),
                speed = printSpeed.value.toInt(),
                mediaType = mediaType.value
            )

            val zplBytes: ByteArray = when (action) {
                "ruler" -> ZplNative.generateRulerNative(config)
                "test" -> ZplNative.generateTestLabelNative(config)
                "bands" -> ZplNative.generateBandsNative(config)
                "calibrate" -> ZplNative.calibrateSensorNative()
                "query" -> ZplNative.queryIdentityNative()
                else -> ByteArray(0)
            }

            if (zplBytes.isNotEmpty()) {
                val ok = transport.write(zplBytes)
                if (action == "query" && ok) {
                    val reply = transport.read(256, 1500)
                    val replyStr = if (reply.isNotEmpty()) String(reply) else "No response from printer"
                    _dialogMessage.value = Pair("Printer Identity (~HI)", replyStr)
                } else if (ok) {
                    _toastEvent.tryEmit("Diagnostic sent to printer successfully")
                } else {
                    _toastEvent.tryEmit("Failed to write to printer")
                }
            }
            _isBusy.value = false
        }
    }

    fun executePrintBitmap(
        bitmap: android.graphics.Bitmap,
        config: ZplConfig,
        autoRotate: Boolean,
        trimMargins: Boolean,
        safeMargins: Boolean
    ) {
        val transport = _activeTransport.value ?: run {
            _toastEvent.tryEmit("Connect a printer first!")
            return
        }

        _isBusy.value = true
        viewModelScope.launch {
            val zplBytes = withContext(Dispatchers.Default) {
                val prepared = BitmapUtils.prepareBitmapForPrint(
                    source = bitmap,
                    targetWidth = config.widthDots,
                    targetHeight = config.lengthDots,
                    autoRotate = autoRotate,
                    marginDots = if (safeMargins) 16 else 0,
                    trimMargins = trimMargins
                )
                ZplNative.encodeBitmapNative(prepared, config)
            }

            if (zplBytes != null && zplBytes.isNotEmpty()) {
                val ok = transport.write(zplBytes)
                if (ok) {
                    _toastEvent.tryEmit("Label printed successfully!")
                } else {
                    _toastEvent.tryEmit("Failed to write data to printer")
                }
            } else {
                _toastEvent.tryEmit("Native encoding failed")
            }
            _isBusy.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        _activeTransport.value?.close()
    }
}
