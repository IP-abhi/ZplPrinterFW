package com.printer.zpl.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printer.zpl.ui.components.DetailedPrinterCard
import com.printer.zpl.ui.theme.*
import com.printer.zpl.ui.viewmodel.PrinterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PrinterViewModel,
    onConnectBluetooth: () -> Unit,
    onConnectUsb: () -> Unit,
    onChoosePrinter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()

    val darkness by viewModel.darkness.collectAsState()
    val printSpeed by viewModel.printSpeed.collectAsState()
    val mediaType by viewModel.mediaType.collectAsState()

    var mediaTypeDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Section: Printer & Settings (Card)
        DetailedPrinterCard(
            isConnected = isConnected,
            isBusy = isBusy,
            deviceInfo = deviceInfo,
            onConnectBluetooth = onConnectBluetooth,
            onConnectUsb = onConnectUsb,
            onChoosePrinter = onChoosePrinter,
            onDisconnect = { viewModel.disconnect() }
        )

        // Section: Diagnostics & Calibration
        Column {
            Text(
                text = "Diagnostics & Calibration",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = DarkTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column {
                    DiagnosticRow(
                        icon = Icons.Default.Info,
                        iconBackground = Color(0xFF6B4EE8),
                        title = "Query printer identity (~HI)",
                        subtitle = "Get printer model, firmware and status",
                        enabled = isConnected && !isBusy,
                        onClick = { viewModel.executeDiagnosticAction("query") }
                    )
                    HorizontalDivider(color = DarkDivider, thickness = 1.dp)

                    DiagnosticRow(
                        icon = Icons.Default.GpsFixed,
                        iconBackground = Color(0xFF2575FC),
                        title = "Calibrate media sensor (~JC)",
                        subtitle = "Auto detect gap / black mark / continuous",
                        enabled = isConnected && !isBusy,
                        onClick = { viewModel.executeDiagnosticAction("calibrate") }
                    )
                    HorizontalDivider(color = DarkDivider, thickness = 1.dp)

                    DiagnosticRow(
                        icon = Icons.Default.Straighten,
                        iconBackground = Color(0xFF10B981),
                        title = "Print alignment ruler",
                        subtitle = "Verify print quality and dimensions",
                        enabled = isConnected && !isBusy,
                        onClick = { viewModel.executeDiagnosticAction("ruler") }
                    )
                    HorizontalDivider(color = DarkDivider, thickness = 1.dp)

                    DiagnosticRow(
                        icon = Icons.Default.QrCode,
                        iconBackground = Color(0xFFF59E0B),
                        title = "Barcode test",
                        subtitle = "Print sample barcodes (1D / 2D)",
                        enabled = isConnected && !isBusy,
                        onClick = { viewModel.executeDiagnosticAction("test") }
                    )
                    HorizontalDivider(color = DarkDivider, thickness = 1.dp)

                    DiagnosticRow(
                        icon = Icons.Default.GridView,
                        iconBackground = Color(0xFFEF4444),
                        title = "Banding test",
                        subtitle = "Check print head and detect banding issues",
                        enabled = isConnected && !isBusy,
                        onClick = { viewModel.executeDiagnosticAction("bands") }
                    )
                }
            }
        }

        // Section: Printing Defaults
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Printing Defaults",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = DarkTextPrimary
                )

                TextButton(
                    onClick = { viewModel.resetPrintingDefaults() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Defaults",
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset",
                        color = DarkTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Darkness Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = DarkTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Darkness (MD)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = DarkTextPrimary
                                )
                                Text(
                                    text = "Print heat level (1 - 30)",
                                    fontSize = 11.sp,
                                    color = DarkTextSecondary
                                )
                            }
                            Text(
                                text = "${darkness.toInt()}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DarkTextPrimary
                            )
                        }

                        Slider(
                            value = darkness,
                            onValueChange = { viewModel.darkness.value = it },
                            valueRange = 1f..30f,
                            steps = 29,
                            colors = SliderDefaults.colors(
                                thumbColor = BrandPurpleLight,
                                activeTrackColor = BrandPurple,
                                inactiveTrackColor = DarkSurfaceElevated
                            )
                        )
                    }

                    HorizontalDivider(color = DarkDivider, thickness = 1.dp)

                    // Speed Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = DarkTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Print Speed (PR)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = DarkTextPrimary
                                )
                                Text(
                                    text = "Inches per second (2 - 6)",
                                    fontSize = 11.sp,
                                    color = DarkTextSecondary
                                )
                            }
                            Text(
                                text = "${printSpeed.toInt()}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DarkTextPrimary
                            )
                        }

                        Slider(
                            value = printSpeed,
                            onValueChange = { viewModel.printSpeed.value = it },
                            valueRange = 2f..6f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = BrandPurpleLight,
                                activeTrackColor = BrandPurple,
                                inactiveTrackColor = DarkSurfaceElevated
                            )
                        )
                    }

                    HorizontalDivider(color = DarkDivider, thickness = 1.dp)

                    // Media Type Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = DarkTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Media Type (MT)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = DarkTextPrimary
                            )
                            Text(
                                text = "Direct thermal or thermal transfer",
                                fontSize = 11.sp,
                                color = DarkTextSecondary
                            )
                        }

                        Box {
                            OutlinedButton(
                                onClick = { mediaTypeDropdownExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkSurfaceElevated,
                                    contentColor = DarkTextPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (mediaType == 0) "Direct Thermal" else "Thermal Transfer",
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = mediaTypeDropdownExpanded,
                                onDismissRequest = { mediaTypeDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Direct Thermal") },
                                    onClick = {
                                        viewModel.mediaType.value = 0
                                        mediaTypeDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Thermal Transfer") },
                                    onClick = {
                                        viewModel.mediaType.value = 1
                                        mediaTypeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    icon: ImageVector,
    iconBackground: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (enabled) iconBackground else DarkSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Color.White else DarkTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (enabled) DarkTextPrimary else DarkTextMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = DarkTextSecondary
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) DarkTextSecondary else DarkTextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}
