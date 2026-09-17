package com.helga.android.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/**
 * Rendert [content] als QR-Code.
 *
 * Bewusst immer schwarz auf weiß statt in Theme-Farben: Ein im Dark Mode invertierter oder
 * eingefärbter Code wird von vielen Scannern nicht erkannt.
 */
@Composable
fun QrCodeImage(
    content: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    sizePx: Int = 640,
) {
    val bitmap = remember(content, sizePx) { encodeQrBitmap(content, sizePx) } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

private fun encodeQrBitmap(content: String, sizePx: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
            pixels[offset + x] = if (matrix.get(x, y)) BLACK else WHITE
        }
    }
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}.getOrNull()
