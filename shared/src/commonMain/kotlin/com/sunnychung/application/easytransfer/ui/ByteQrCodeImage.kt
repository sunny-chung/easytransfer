package com.sunnychung.application.easytransfer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.sunnychung.application.easytransfer.optical.OpticalErrorCorrection

@Composable
internal expect fun ByteQrCodeImage(
    frame: ByteArray,
    errorCorrection: OpticalErrorCorrection,
    contentDescription: String?,
    modifier: Modifier = Modifier,
)

@Composable
internal fun QrCodeMatrixImage(
    matrix: List<BooleanArray>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val matrixSize = matrix.size
        if (matrixSize == 0) return@Canvas
        drawRect(Color.White)
        val totalModules = matrixSize + QR_QUIET_ZONE_MODULES * 2
        val moduleSize = minOf(size.width, size.height) / totalModules
        val offsetX = (size.width - moduleSize * totalModules) / 2f
        val offsetY = (size.height - moduleSize * totalModules) / 2f
        matrix.forEachIndexed { y, row ->
            row.forEachIndexed { x, isDark ->
                if (isDark) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(
                            x = offsetX + (x + QR_QUIET_ZONE_MODULES) * moduleSize,
                            y = offsetY + (y + QR_QUIET_ZONE_MODULES) * moduleSize,
                        ),
                        size = Size(moduleSize, moduleSize),
                    )
                }
            }
        }
    }
}

private const val QR_QUIET_ZONE_MODULES = 4
