package com.printer.zpl.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.printer.zpl.transport.AndroidUsbTransport
import com.printer.zpl.ui.theme.ZplPrinterTheme
import com.printer.zpl.ui.viewmodel.PrinterViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val ACTION_USB_PERMISSION = "com.printer.zpl.USB_PERMISSION"
    private var usbManager: UsbManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val printerViewModel: PrinterViewModel by viewModels()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { dev ->
                            usbManager?.let { mgr -> printerViewModel.connectUsb(mgr, dev) }
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "USB Permission denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = btManager?.adapter

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Handle auto-connection if launched via USB attach
        intent?.let { checkUsbIntent(it) }

        // Observe toast notifications from ViewModel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                printerViewModel.toastEvent.collect { message ->
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            ZplPrinterTheme {
                var showBtDialog by remember { mutableStateOf(false) }
                var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

                val btPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { perms ->
                    val granted = perms.values.all { it }
                    if (granted) {
                        pairedDevices = getPairedBluetoothDevices()
                        showBtDialog = true
                    } else {
                        Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
                    }
                }

                MainScreen(
                    viewModel = printerViewModel,
                    onConnectUsb = { detectAndConnectUsb() },
                    onRequestBluetoothScan = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            btPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN
                                )
                            )
                        } else {
                            pairedDevices = getPairedBluetoothDevices()
                            showBtDialog = true
                        }
                    },
                    pairedDevices = pairedDevices,
                    showBtDialog = showBtDialog,
                    onDismissBtDialog = { showBtDialog = false },
                    onSelectBluetoothDevice = { dev ->
                        printerViewModel.connectBluetooth(dev)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkUsbIntent(intent)
    }

    private fun checkUsbIntent(intent: Intent) {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            device?.let { dev ->
                usbManager?.let { mgr -> printerViewModel.connectUsb(mgr, dev) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }

    private fun detectAndConnectUsb() {
        val manager = usbManager ?: return
        val deviceList = manager.deviceList

        if (deviceList.isEmpty()) {
            Toast.makeText(this, "No USB devices found. Connect printer via OTG adapter.", Toast.LENGTH_LONG).show()
            return
        }

        // Prefer Rongta / Milestone (0fe6:8800) or any USB printer (deviceClass 7)
        var target: UsbDevice? = null
        for ((_, dev) in deviceList) {
            if (dev.vendorId == AndroidUsbTransport.RONGTA_VENDOR_ID &&
                dev.productId == AndroidUsbTransport.RONGTA_PRODUCT_ID) {
                target = dev
                break
            }
            if (dev.deviceClass == 7) {
                target = dev
            }
        }

        val targetDevice = target ?: deviceList.values.firstOrNull() ?: run {
            Toast.makeText(this, "No USB device found", Toast.LENGTH_SHORT).show()
            return
        }

        if (!manager.hasPermission(targetDevice)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val permissionIntent = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_USB_PERMISSION), flags
            )
            manager.requestPermission(targetDevice, permissionIntent)
        } else {
            printerViewModel.connectUsb(manager, targetDevice)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getPairedBluetoothDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
