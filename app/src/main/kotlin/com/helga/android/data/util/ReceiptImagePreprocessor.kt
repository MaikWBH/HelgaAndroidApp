package com.helga.android.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import timber.log.Timber
import kotlin.math.max

/**
 * Lädt und bereinigt Bon-Fotos vor der Erkennung.
 *
 * Hintergrund (Recherche zu Receipt-OCR): Bildqualität ist der größte Hebel für
 * die Erkennungsrate (Studien: Vorverarbeitung hebt die OCR-Genauigkeit von
 * ~70 % auf ~92 %). Hier werden die zwei häufigsten Fehlerquellen behoben:
 *  1. EXIF-Rotation: Handy-Fotos werden oft um 90/180° gedreht gespeichert. Ohne
 *     Korrektur steht der Bon-Text quer → OCR/Vision erkennt kaum etwas.
 *  2. Niedriger Kontrast / starke Verkleinerung: kleiner Bon-Druck verschwimmt.
 */
object ReceiptImagePreprocessor {

    /**
     * Dekodiert das Bild aus [uri] speicherschonend und richtet es anhand der
     * EXIF-Orientierung aufrecht aus. [maxDim] begrenzt die längste Kante, ohne den
     * Text unleserlich zu machen (Default bewusst hoch für kleinen Bon-Druck).
     */
    fun loadUprightBitmap(context: Context, uri: Uri, maxDim: Int = 2400): Bitmap? {
        val resolver = context.contentResolver

        // 1) Maße lesen, ohne das ganze Bild zu dekodieren (OOM-Schutz).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2) inSampleSize so wählen, dass die längste Kante grob beim 2× von maxDim
        //    landet → danach sauber herunterskalieren (bessere Kantenqualität).
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim * 2)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        // 3) Auf maxDim skalieren.
        val scaled = scaleToMaxDim(decoded, maxDim)
        if (scaled !== decoded) decoded.recycle()

        // 4) EXIF-Orientierung anwenden.
        val rotation = readExifRotation(context, uri)
        if (rotation == 0) return scaled
        val rotated = rotate(scaled, rotation)
        if (rotated !== scaled) scaled.recycle()
        return rotated
    }

    /**
     * Erhöht den Kontrast und entsättigt das Bild (Graustufen). Für die
     * On-Device-OCR (ML Kit) verbessert das die Trennung von Text und Hintergrund
     * spürbar. Das Original wird nicht verändert (neue Bitmap).
     */
    fun enhanceForOcr(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val contrast = 1.5f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val matrix = ColorMatrix().apply {
            setSaturation(0f) // entsättigen → Graustufen
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun computeInSampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (target > 0 && longest / 2 >= target) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMaxDim(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun readExifRotation(context: Context, uri: Uri): Int =
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            Timber.w(e, "EXIF-Orientierung konnte nicht gelesen werden")
            0
        }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
