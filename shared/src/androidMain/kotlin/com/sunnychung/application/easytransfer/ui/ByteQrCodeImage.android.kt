package com.sunnychung.application.easytransfer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.sunnychung.application.easytransfer.optical.OpticalErrorCorrection
import java.nio.charset.StandardCharsets
import java.util.EnumMap

@Composable
internal actual fun ByteQrCodeImage(
    frame: ByteArray,
    errorCorrection: OpticalErrorCorrection,
    contentDescription: String?,
    modifier: Modifier,
) {
    val matrix = remember(frame, errorCorrection) { frame.toQrMatrix(errorCorrection) }
    QrCodeMatrixImage(matrix = matrix, modifier = modifier)
}

private fun ByteArray.toQrMatrix(errorCorrection: OpticalErrorCorrection): List<BooleanArray> {
    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.ERROR_CORRECTION, errorCorrection.zxingErrorCorrectionLevel())
        put(EncodeHintType.MARGIN, 0)
        put(EncodeHintType.QR_MASK_PATTERN, 4)
    }
    // ZXing's byte-mode default is ISO-8859-1. Do not pass CHARACTER_SET here:
    // the hint adds an ECI segment, and the upstream 2953-byte V40/L frame has
    // no room for those extra bits.
    val content = String(this, StandardCharsets.ISO_8859_1)
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 1, 1, hints)
    return List(bitMatrix.height) { y ->
        BooleanArray(bitMatrix.width) { x -> bitMatrix[x, y] }
    }
}

private fun OpticalErrorCorrection.zxingErrorCorrectionLevel(): ErrorCorrectionLevel = when (this) {
    OpticalErrorCorrection.Low -> ErrorCorrectionLevel.L
    OpticalErrorCorrection.Medium -> ErrorCorrectionLevel.M
    OpticalErrorCorrection.Quartile -> ErrorCorrectionLevel.Q
    OpticalErrorCorrection.High -> ErrorCorrectionLevel.H
}
