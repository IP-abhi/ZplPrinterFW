package com.printer.zpl.ui.screens.label

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printer.zpl.ui.theme.*
import com.printer.zpl.ui.viewmodel.PrinterViewModel

@Composable
fun LabelScreen(
    viewModel: PrinterViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = BrandPurpleLight,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Label & Receipt Designer",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DarkTextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "The interactive label visual editor (barcodes, QR codes, custom text & dynamic fields) will be implemented in the next phase.",
            fontSize = 13.sp,
            color = DarkTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Text(
                text = "Use the Photo tab to print images, label PDFs, and barcodes directly.",
                color = BrandPurpleLight,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}
