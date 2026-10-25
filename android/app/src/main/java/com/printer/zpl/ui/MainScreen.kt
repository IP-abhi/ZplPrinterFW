package com.printer.zpl.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printer.zpl.ui.components.AppTopBar
import com.printer.zpl.ui.components.CompactPrinterCard
import com.printer.zpl.ui.screens.label.LabelScreen
import com.printer.zpl.ui.screens.photo.PhotoScreen
import com.printer.zpl.ui.screens.settings.SettingsScreen
import com.printer.zpl.ui.theme.*
import com.printer.zpl.ui.viewmodel.PrinterViewModel

enum class StudioTab {
    LABEL, PHOTO
}

@Composable
fun MainScreen(
    viewModel: PrinterViewModel,
    onConnectUsb: () -> Unit,
    onRequestBluetoothScan: () -> Unit,
    pairedDevices: List<BluetoothDevice>,
    showBtDialog: Boolean,
    onDismissBtDialog: () -> Unit,
    onSelectBluetoothDevice: (BluetoothDevice) -> Unit
) {
    var inSettingsScreen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(StudioTab.PHOTO) }

    val isConnected by viewModel.isConnected.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val dialogMessage by viewModel.dialogMessage.collectAsState()

    // Handle system back gesture / back button to pop from SettingsScreen to MainScreen
    BackHandler(enabled = inSettingsScreen) {
        inSettingsScreen = false
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            if (inSettingsScreen) {
                AppTopBar(
                    title = "Printer & Settings",
                    showBackButton = true,
                    onBackClick = { inSettingsScreen = false },
                    showSettingsButton = false
                )
            } else {
                AppTopBar(
                    title = "Thermal Studio",
                    showBackButton = false,
                    showSettingsButton = true,
                    onSettingsClick = { inSettingsScreen = true }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (inSettingsScreen) {
                SettingsScreen(
                    viewModel = viewModel,
                    onConnectBluetooth = onRequestBluetoothScan,
                    onConnectUsb = onConnectUsb,
                    onChoosePrinter = onRequestBluetoothScan
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Header Card (Compact)
                    CompactPrinterCard(
                        isConnected = isConnected,
                        deviceInfo = deviceInfo,
                        onClick = { inSettingsScreen = true }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tab Selector (Segmented Pill)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkSurface,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Label / Receipt Tab
                            TabButton(
                                text = "Label / Receipt",
                                icon = Icons.Default.Description,
                                isSelected = selectedTab == StudioTab.LABEL,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTab = StudioTab.LABEL }
                            )

                            // Photo Tab
                            TabButton(
                                text = "Photo",
                                icon = Icons.Default.Image,
                                isSelected = selectedTab == StudioTab.PHOTO,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTab = StudioTab.PHOTO }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Selected Tab Content
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            StudioTab.LABEL -> LabelScreen(viewModel = viewModel)
                            StudioTab.PHOTO -> PhotoScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    // Bluetooth Device Selection Dialog
    if (showBtDialog) {
        AlertDialog(
            onDismissRequest = onDismissBtDialog,
            containerColor = DarkSurface,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = {
                Text(
                    text = "Select Paired Printer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                if (pairedDevices.isEmpty()) {
                    Text(
                        "No paired Bluetooth devices found. Please pair your MHT-L1091 / Rongta thermal printer in Android Settings first.",
                        fontSize = 13.sp
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pairedDevices.forEach { dev ->
                            @SuppressLint("MissingPermission")
                            val name = dev.name ?: "Unknown Device"
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onDismissBtDialog()
                                        onSelectBluetoothDevice(dev)
                                    },
                                color = DarkSurfaceElevated,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = DarkTextPrimary
                                    )
                                    Text(
                                        text = dev.address,
                                        fontSize = 12.sp,
                                        color = DarkTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissBtDialog) {
                    Text("Cancel", color = BrandPurpleLight)
                }
            }
        )
    }

    // General Dialog (~HI query or informational)
    dialogMessage?.let { (title, msg) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            containerColor = DarkSurface,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    color = DarkTextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDialog() }) {
                    Text("OK", color = BrandPurpleLight)
                }
            }
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) BrandPurple else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) OnBrandPurple else DarkTextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) OnBrandPurple else DarkTextSecondary
            )
        }
    }
}
