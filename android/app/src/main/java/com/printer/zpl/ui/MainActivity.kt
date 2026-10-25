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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.printer.zpl.ZplConfig
import com.printer.zpl.ZplNative
import com.printer.zpl.transport.AndroidBtTransport
import com.printer.zpl.transport.AndroidUsbTransport
import com.printer.zpl.transport.IPrinterTransport
import com.printer.zpl.ui.theme.ZplPrinterTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val ACTION_USB_PERMISSION = "com.printer.zpl.USB_PERMISSION"
    private var usbManager: UsbManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var activeTransport = mutableStateOf<IPrinterTransport?>(null)
    private var statusMessage = mutableStateOf("Disconnected")
    private var isConnected = mutableStateOf(false)
    private var isBusy = mutableStateOf(false)

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
                        device?.let { connectUsbDevice(it) }
                    } else {
                        statusMessage.value = "USB Permission denied for device"
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

        // Check if intent was launched from USB device attached
        intent?.let { checkUsbIntent(it) }

        setContent {
            ZplPrinterTheme {
                MainScreen(
                    status = statusMessage.value,
                    isConnected = isConnected.value,
                    isBusy = isBusy.value,
                    onConnectUsb = { detectAndConnectUsb() },
                    onSelectBluetooth = { device -> connectBluetoothDevice(device) },
                    onDisconnect = { disconnectPrinter() },
                    onPrintAction = { action -> executePrintAction(action) },
                    onPrintBitmap = { bmp, cfg, rot, trim, safe ->
                        executePrintBitmap(bmp, cfg, rot, trim, safe)
                    },
                    getPairedBtDevices = { getPairedBluetoothDevices() }
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
            device?.let { connectUsbDevice(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        activeTransport.value?.close()
    }

    fun detectAndConnectUsb() {
        val manager = usbManager ?: return
        val deviceList = manager.deviceList

        if (deviceList.isEmpty()) {
            Toast.makeText(this, "No USB devices found. Connect printer via OTG adapter.", Toast.LENGTH_LONG).show()
            return
        }

        // Prefer Rongta / Milestone (0fe6:8800) or any USB printer
        var target: UsbDevice? = null
        for ((_, dev) in deviceList) {
            if (dev.vendorId == AndroidUsbTransport.RONGTA_VENDOR_ID &&
                dev.productId == AndroidUsbTransport.RONGTA_PRODUCT_ID) {
                target = dev
                break
            }
            if (dev.deviceClass == 7) { // Printer class
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
            connectUsbDevice(targetDevice)
        }
    }

    private fun connectUsbDevice(device: UsbDevice) {
        val manager = usbManager ?: return
        isBusy.value = true
        statusMessage.value = "Connecting to USB: ${device.deviceName}..."

        CoroutineScope(Dispatchers.Main).launch {
            val transport = AndroidUsbTransport(manager, device)
            val success = transport.open()
            isBusy.value = false
            if (success) {
                activeTransport.value?.close()
                activeTransport.value = transport
                isConnected.value = true
                statusMessage.value = "Connected (USB): ${transport.endpointName}"
                Toast.makeText(this@MainActivity, "Connected to USB printer", Toast.LENGTH_SHORT).show()
            } else {
                statusMessage.value = "Failed to connect to USB device"
                Toast.makeText(this@MainActivity, "Failed to open USB endpoint", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectBluetoothDevice(device: BluetoothDevice) {
        isBusy.value = true
        statusMessage.value = "Connecting to Bluetooth: ${device.name ?: device.address}..."

        CoroutineScope(Dispatchers.Main).launch {
            val transport = AndroidBtTransport(device)
            val success = transport.open()
            isBusy.value = false
            if (success) {
                activeTransport.value?.close()
                activeTransport.value = transport
                isConnected.value = true
                statusMessage.value = "Connected (BT): ${transport.endpointName}"
                Toast.makeText(this@MainActivity, "Connected to Bluetooth printer", Toast.LENGTH_SHORT).show()
            } else {
                statusMessage.value = "Failed to connect to Bluetooth printer"
                Toast.makeText(this@MainActivity, "Bluetooth connection failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun disconnectPrinter() {
        activeTransport.value?.close()
        activeTransport.value = null
        isConnected.value = false
        statusMessage.value = "Disconnected"
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

    private fun executePrintAction(action: String) {
        val transport = activeTransport.value ?: run {
            Toast.makeText(this, "Connect a printer first!", Toast.LENGTH_SHORT).show()
            return
        }

        isBusy.value = true
        CoroutineScope(Dispatchers.Main).launch {
            val config = ZplConfig.fromInches(4.0f, 6.0f, 203)
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
                    val replyStr = if (reply.isNotEmpty()) String(reply) else "No response"
                    showDialog("Printer Query (~HI)", "Reply from printer:\n$replyStr")
                } else if (ok) {
                    Toast.makeText(this@MainActivity, "Sent to printer successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to write to printer", Toast.LENGTH_LONG).show()
                }
            }
            isBusy.value = false
        }
    }

    companion object {
        fun trimWhitespace(source: Bitmap, threshold: Int = 245): Bitmap {
            val width = source.width
            val height = source.height
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1

            val pixels = IntArray(width)
            for (y in 0 until height) {
                source.getPixels(pixels, 0, width, 0, y, width, 1)
                for (x in 0 until width) {
                    val pixel = pixels[x]
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (r < threshold || g < threshold || b < threshold) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            if (maxX < minX || maxY < minY) {
                return source // Empty / solid white
            }

            val pad = 8
            val cropX = (minX - pad).coerceAtLeast(0)
            val cropY = (minY - pad).coerceAtLeast(0)
            val cropW = (maxX - minX + 1 + 2 * pad).coerceAtMost(width - cropX)
            val cropH = (maxY - minY + 1 + 2 * pad).coerceAtMost(height - cropY)

            return Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
        }

        fun prepareBitmapForPrint(
            source: Bitmap,
            targetWidth: Int = 812,
            targetHeight: Int = 1218,
            autoRotate: Boolean = true,
            marginDots: Int = 0,
            trimMargins: Boolean = false
        ): Bitmap {
            var src = source
            // Convert to ARGB_8888 if needed
            if (src.config != Bitmap.Config.ARGB_8888) {
                src = src.copy(Bitmap.Config.ARGB_8888, true) ?: src
            }

            // Trim surrounding whitespace for shipping labels if enabled
            if (trimMargins) {
                src = trimWhitespace(src)
            }

            // Auto-rotate landscape content to fit portrait 4x6 label
            if (autoRotate && src.width > src.height && targetWidth < targetHeight) {
                val matrix = Matrix().apply { postRotate(90f) }
                src = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            }

            val availableW = (targetWidth - 2 * marginDots).coerceAtLeast(100)
            val availableH = (targetHeight - 2 * marginDots).coerceAtLeast(100)

            val scale = minOf(
                availableW.toFloat() / src.width.toFloat(),
                availableH.toFloat() / src.height.toFloat()
            )

            val scaledW = (src.width * scale).let { Math.round(it) }.coerceAtLeast(1)
            val scaledH = (src.height * scale).let { Math.round(it) }.coerceAtLeast(1)

            val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)

            val canvasBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(Color.WHITE)

            val left = (targetWidth - scaledW) / 2f
            val top = (targetHeight - scaledH) / 2f
            canvas.drawBitmap(scaled, left, top, null)

            return canvasBitmap
        }

        fun loadMediaFromUri(context: Context, uri: Uri): Bitmap? {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val isPdf = mimeType.contains("pdf", ignoreCase = true) ||
                    uri.path?.endsWith(".pdf", ignoreCase = true) == true

            if (isPdf) {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
                return pfd.use { descriptor ->
                    val renderer = PdfRenderer(descriptor)
                    renderer.use { pdf ->
                        if (pdf.pageCount == 0) return null
                        val page = pdf.openPage(0)
                        page.use { pg ->
                            // Render page 1 at high resolution (203 DPI) for crisp barcodes
                            val scale = 203f / 72f
                            val w = (pg.width * scale).toInt().coerceAtLeast(1)
                            val h = (pg.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            bmp
                        }
                    }
                }
            } else {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }
        }
    }

    private fun executePrintBitmap(
        bitmap: Bitmap,
        config: ZplConfig,
        autoRotate: Boolean = true,
        trimMargins: Boolean = false,
        safeMargins: Boolean = false
    ) {
        val transport = activeTransport.value ?: run {
            Toast.makeText(this, "Connect a printer first!", Toast.LENGTH_SHORT).show()
            return
        }

        isBusy.value = true
        CoroutineScope(Dispatchers.Main).launch {
            val zplBytes = withContext(Dispatchers.Default) {
                val prepared = prepareBitmapForPrint(
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
                    Toast.makeText(this@MainActivity, "Label printed successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to write label data to printer", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this@MainActivity, "Native encoding failed", Toast.LENGTH_LONG).show()
            }
            isBusy.value = false
        }
    }

    private fun showDialog(title: String, msg: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    status: String,
    isConnected: Boolean,
    isBusy: Boolean,
    onConnectUsb: () -> Unit,
    onSelectBluetooth: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onPrintAction: (String) -> Unit,
    onPrintBitmap: (Bitmap, ZplConfig, Boolean, Boolean, Boolean) -> Unit,
    getPairedBtDevices: () -> List<BluetoothDevice>
) {
    val context = LocalContext.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var autoRotate by remember { mutableStateOf(true) }
    var trimMargins by remember { mutableStateOf(false) }
    var safeMargins by remember { mutableStateOf(false) }
    var darkness by remember { mutableFloatStateOf(30f) }
    var ditherMode by remember { mutableIntStateOf(0) } // 0 = Threshold (Best for crisp shipping labels and barcodes)
    var invertPolarity by remember { mutableStateOf(false) }

    var showBtDialog by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it)) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                selectedBitmap = bmp
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val pfd = context.contentResolver.openFileDescriptor(it, "r")
                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val scale = 203f / 72f
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()
                        renderer.close()
                        pfd.close()
                        selectedBitmap = bmp
                    } else {
                        renderer.close()
                        pfd.close()
                        Toast.makeText(context, "PDF file has no pages", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        if (granted) {
            pairedDevices = getPairedBtDevices()
            showBtDialog = true
        } else {
            Toast.makeText(context, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZPL Direct Thermal Printer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isConnected) "Printer Online" else "Printer Disconnected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = status,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isConnected) {
                        IconButton(onClick = onDisconnect) {
                            Icon(Icons.Default.Close, contentDescription = "Disconnect")
                        }
                    }
                }
            }

            // Connection Actions
            if (!isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onConnectUsb,
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect USB")
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                btPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                        Manifest.permission.BLUETOOTH_SCAN
                                    )
                                )
                            } else {
                                pairedDevices = getPairedBtDevices()
                                showBtDialog = true
                            }
                        },
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bluetooth")
                    }
                }
            }

            // Diagnostic Printing Actions
            Text(
                text = "Diagnostics & Calibration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onPrintAction("ruler") },
                    enabled = isConnected && !isBusy
                ) {
                    Text("Ruler Test")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onPrintAction("test") },
                    enabled = isConnected && !isBusy
                ) {
                    Text("Barcode Test")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onPrintAction("bands") },
                    enabled = isConnected && !isBusy
                ) {
                    Text("Bands Test")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onPrintAction("query") },
                    enabled = isConnected && !isBusy
                ) {
                    Text("Query (~HI)")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onPrintAction("calibrate") },
                    enabled = isConnected && !isBusy
                ) {
                    Text("Calibrate (~JC)")
                }
            }

            HorizontalDivider()

            // Image & Document Printing
            Text(
                text = "Image / Document Printing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                    enabled = !isBusy
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF Label")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { photoPickerLauncher.launch("image/*") },
                    enabled = !isBusy
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Image")
                }

                Button(
                    modifier = Modifier.weight(1.1f),
                    onClick = {
                        selectedBitmap?.let { bmp ->
                            val cfg = ZplConfig.fromInches(4.0f, 6.0f, 203).copy(
                                darkness = darkness.toInt(),
                                ditherMode = ditherMode,
                                invert = invertPolarity
                            )
                            onPrintBitmap(bmp, cfg, autoRotate, trimMargins, safeMargins)
                        }
                    },
                    enabled = selectedBitmap != null && !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Print")
                }
            }

            // Media Preview
            selectedBitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected Label Media",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Tuning Sliders
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Hardware Burn Darkness: ${darkness.toInt()} (^MD)",
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = darkness,
                        onValueChange = { darkness = it },
                        valueRange = 1f..30f,
                        steps = 29
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Binarization Mode", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = ditherMode == 0,
                            onClick = { ditherMode = 0 },
                            label = { Text("Threshold (Barcodes)") }
                        )
                        FilterChip(
                            selected = ditherMode == 1,
                            onClick = { ditherMode = 1 },
                            label = { Text("Floyd-Steinberg") }
                        )
                        FilterChip(
                            selected = ditherMode == 2,
                            onClick = { ditherMode = 2 },
                            label = { Text("Atkinson") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = autoRotate,
                            onCheckedChange = { autoRotate = it }
                        )
                        Text("Auto-rotate landscape to portrait")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = trimMargins,
                            onCheckedChange = { trimMargins = it }
                        )
                        Text("Auto-trim white margins (for carrier PDFs)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = safeMargins,
                            onCheckedChange = { safeMargins = it }
                        )
                        Text("Safe margins (16 dots / 2mm border)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = invertPolarity,
                            onCheckedChange = { invertPolarity = it }
                        )
                        Text("Invert Polarity (white-on-black)")
                    }
                }
            }
        }
    }

    // Bluetooth Device Selection Dialog
    if (showBtDialog) {
        AlertDialog(
            onDismissRequest = { showBtDialog = false },
            title = { Text("Select Paired Bluetooth Printer") },
            text = {
                if (pairedDevices.isEmpty()) {
                    Text("No paired Bluetooth devices found. Pair the MHT-L1091 in Android Bluetooth Settings first.")
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        pairedDevices.forEach { dev ->
                            @SuppressLint("MissingPermission")
                            val name = dev.name ?: "Unknown Device"
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    showBtDialog = false
                                    onSelectBluetooth(dev)
                                }
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(name, fontWeight = FontWeight.Bold)
                                    Text(dev.address, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBtDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

