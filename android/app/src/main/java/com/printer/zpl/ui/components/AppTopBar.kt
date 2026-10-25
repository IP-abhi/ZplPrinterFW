package com.printer.zpl.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printer.zpl.ui.theme.BrandPurple
import com.printer.zpl.ui.theme.DarkBackground
import com.printer.zpl.ui.theme.DarkTextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String = "Thermal Studio",
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    showSettingsButton: Boolean = true,
    onSettingsClick: () -> Unit = {}
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DarkBackground,
            titleContentColor = DarkTextPrimary
        ),
        title = {
            if (title == "Thermal Studio") {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = DarkTextPrimary, fontWeight = FontWeight.Bold)) {
                            append("Thermal ")
                        }
                        withStyle(style = SpanStyle(color = BrandPurple, fontWeight = FontWeight.Bold)) {
                            append("Studio")
                        }
                    },
                    fontSize = 20.sp
                )
            } else {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = DarkTextPrimary
                )
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DarkTextPrimary
                    )
                }
            }
        },
        actions = {
            if (showSettingsButton) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = DarkTextPrimary
                    )
                }
            }
        }
    )
}
