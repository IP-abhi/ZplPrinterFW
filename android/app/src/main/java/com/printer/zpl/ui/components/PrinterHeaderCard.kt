package com.printer.zpl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printer.zpl.ui.theme.*
import com.printer.zpl.ui.viewmodel.ConnectedDeviceInfo
import com.printer.zpl.ui.viewmodel.ConnectionType

@Composable
fun CompactPrinterCard(
    isConnected: Boolean,
    deviceInfo: ConnectedDeviceInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Printer Icon in container
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = if (isConnected) BrandPurpleLight else DarkTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) StatusGreen else DarkTextMuted)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) deviceInfo.name else "No printer connected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = DarkTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                val subtext = if (isConnected) {
                    val connType = if (deviceInfo.type == ConnectionType.BLUETOOTH) "Bluetooth connected" else "USB connected"
                    "$connType • ${deviceInfo.addressOrEndpoint}"
                } else {
                    "Tap to connect via Bluetooth or USB"
                }

                Text(
                    text = subtext,
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Details",
                tint = DarkTextMuted
            )
        }
    }
}

@Composable
fun DetailedPrinterCard(
    isConnected: Boolean,
    isBusy: Boolean,
    deviceInfo: ConnectedDeviceInfo,
    onConnectBluetooth: () -> Unit,
    onConnectUsb: () -> Unit,
    onChoosePrinter: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large Printer Icon Card
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = if (isConnected) BrandPurpleLight else DarkTextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isConnected) deviceInfo.name else "Printer Offline",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = DarkTextPrimary
                    )
                    if (isConnected && deviceInfo.model.isNotEmpty()) {
                        Text(
                            text = deviceInfo.model,
                            fontSize = 12.sp,
                            color = DarkTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) StatusGreen else DarkTextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) {
                                if (deviceInfo.type == ConnectionType.BLUETOOTH) "Connected via Bluetooth" else "Connected via USB"
                            } else "Not connected",
                            fontSize = 12.sp,
                            color = if (isConnected) StatusGreen else DarkTextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isConnected && deviceInfo.addressOrEndpoint.isNotEmpty()) {
                        Text(
                            text = deviceInfo.addressOrEndpoint,
                            fontSize = 11.sp,
                            color = DarkTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Connect / Disconnect Bluetooth
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isConnected) onDisconnect() else onConnectBluetooth()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) DarkSurfaceElevated else BrandPurple,
                        contentColor = if (isConnected) DarkTextPrimary else OnBrandPurple
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                    enabled = !isBusy
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isConnected) "Disconnect" else "Connect",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // USB Printer Button
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onConnectUsb,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkSurfaceElevated,
                        contentColor = DarkTextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                    enabled = !isBusy
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = DarkTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "USB printer",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Choose printer Button
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onChoosePrinter,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkSurfaceElevated,
                        contentColor = DarkTextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                    enabled = !isBusy
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = DarkTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
