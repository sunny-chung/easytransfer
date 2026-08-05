package com.sunnychung.application.easytransfer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Ink = Color(0xFF18212F)
private val Canvas = Color(0xFFF7F8FA)
private val Blue = Color(0xFF2764E7)
private val PaleBlue = Color(0xFFE9F0FF)
private val Mint = Color(0xFF0B755F)
private val PaleMint = Color(0xFFDAF4EB)
private val Border = Color(0xFFDDE2EA)

private val EasyTransferColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = PaleBlue,
    onPrimaryContainer = Color(0xFF153A86),
    secondary = Mint,
    onSecondary = Color.White,
    secondaryContainer = PaleMint,
    onSecondaryContainer = Color(0xFF075040),
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF5A6473),
    outline = Border,
)

private val EasyTransferShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun EasyTransferTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = EasyTransferColors,
        shapes = EasyTransferShapes,
        content = content,
    )
}
