package com.printer.zpl.ui.screens.photo

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printer.zpl.ZplConfig
import com.printer.zpl.ui.theme.*
import com.printer.zpl.ui.viewmodel.PrinterViewModel
import com.printer.zpl.utils.BitmapUtils
import com.printer.zpl.utils.MediaLoader
import kotlinx.coroutines.launch

@Composable
fun PhotoScreen(
    viewModel: PrinterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val darkness by viewModel.darkness.collectAsState()
    val ditherMode by viewModel.ditherMode.collectAsState()
    val invertPolarity by viewModel.invertPolarity.collectAsState()
    val autoRotate by viewModel.autoRotate.collectAsState()
    val trimMargins by viewModel.trimMargins.collectAsState()
    val safeMargins by viewModel.safeMargins.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    // Launcher for selecting Photo or PDF to print
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val loaded = MediaLoader.loadFromUri(context, it)
                if (loaded != null) {
                    originalBitmap = loaded
                    displayBitmap = loaded
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image Preview Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                .clickable {
                    mediaPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = displayBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Selected Photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Choose Image",
                        tint = DarkBackground,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tap to choose a photo or PDF",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Supports JPEG, PNG, WebP, PDF",
                        color = Color.DarkGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Action Toolbar (Rotate, Fit to label, Reset)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Rotate 90 degrees
            PhotoActionButton(
                icon = Icons.Default.RotateRight,
                text = "Rotate 90°",
                modifier = Modifier.weight(1f),
                onClick = {
                    displayBitmap?.let { bmp ->
                        displayBitmap = BitmapUtils.rotateBitmap(bmp, 90f)
                    }
                }
            )

            // Fit to label
            PhotoActionButton(
                icon = Icons.Default.Fullscreen,
                text = "Fit to label",
                modifier = Modifier.weight(1f),
                onClick = {
                    displayBitmap?.let { bmp ->
                        // If landscape, rotate 90 degrees to fill standard 4x6 portrait roll
                        if (bmp.width > bmp.height) {
                            displayBitmap = BitmapUtils.rotateBitmap(bmp, 90f)
                        }
                    }
                }
            )

            // Reset
            PhotoActionButton(
                icon = Icons.Default.Refresh,
                text = "Reset",
                modifier = Modifier.weight(1f),
                onClick = {
                    displayBitmap = originalBitmap
                }
            )
        }

        // Dithering Method Section
        Column {
            Text(
                text = "Dithering Method",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = DarkTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val ditherOptions = listOf("Threshold", "Floyd-Steinberg", "Atkinson")
                    ditherOptions.forEachIndexed { index, title ->
                        val isSelected = ditherMode == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BrandPurple else Color.Transparent)
                                .clickable { viewModel.ditherMode.value = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) OnBrandPurple else DarkTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Hardware Burn Darkness (MD)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hardware Burn Darkness (MD)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = DarkTextPrimary
                )
                Text(
                    text = "${darkness.toInt()} / 30",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = DarkTextSecondary
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

        // Invert Polarity Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Invert Polarity",
                    fontSize = 14.sp,
                    color = DarkTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "White-on-black binarization",
                    fontSize = 11.sp,
                    color = DarkTextSecondary
                )
            }
            Switch(
                checked = invertPolarity,
                onCheckedChange = { viewModel.invertPolarity.value = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BrandPurple,
                    checkedTrackColor = BrandPurpleContainer,
                    uncheckedTrackColor = DarkSurfaceElevated
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Full Width Print Photo Button
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            onClick = {
                val bmp = displayBitmap ?: return@Button
                val config = ZplConfig.fromInches(4.0f, 6.0f, 203).copy(
                    darkness = darkness.toInt(),
                    speed = viewModel.printSpeed.value.toInt(),
                    ditherMode = ditherMode,
                    invert = invertPolarity
                )
                viewModel.executePrintBitmap(
                    bitmap = bmp,
                    config = config,
                    autoRotate = false,
                    trimMargins = trimMargins,
                    safeMargins = safeMargins
                )
            },
            enabled = displayBitmap != null && !isBusy && isConnected,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandPurple,
                contentColor = OnBrandPurple,
                disabledContainerColor = DarkSurfaceElevated,
                disabledContentColor = DarkTextMuted
            )
        ) {
            Icon(
                imageVector = Icons.Default.Print,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Print photo",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun PhotoActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = DarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = DarkTextPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                color = DarkTextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
