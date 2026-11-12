package com.helga.android.data.receipts

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.helga.android.data.util.ReceiptImagePreprocessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Verkleinertes Bon-Foto: die Datei (zum Hochladen) und die Bitmap (für die lokale ML-Kit-Erkennung). */
class PreparedImage(val file: File, val bitmap: Bitmap)

/**
 * Lokale Foto-Ablage für Bons. Das Handy hält ein Foto nur, bis es hochgeladen ist (Server = Archiv):
 * verkleinert (längste Kante 2000 px, JPEG 85) unter `filesDir/receipts/<bonId>.jpg`; die Kamera-Temp-Datei
 * unter `cacheDir/receipts/` wird nach dem Scan gelöscht.
 */
@Singleton
class ReceiptImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val photoDir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }
    private val cameraDir: File get() = File(context.cacheDir, "receipts")

    /** Lädt das Foto aufrecht und verkleinert, speichert es als JPEG und gibt Datei und Bitmap zurück. */
    suspend fun prepare(uri: Uri, receiptId: String): PreparedImage? = withContext(Dispatchers.IO) {
        val bitmap = ReceiptImagePreprocessor.loadUprightBitmap(context, uri, maxDim = MAX_DIM) ?: return@withContext null
        val file = File(photoDir, "$receiptId.jpg")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        PreparedImage(file, bitmap)
    }

    /** Löscht die Foto-Datei (Pfad oder `file://`-URI); fehlende Dateien sind kein Fehler. */
    fun delete(path: String) {
        if (path.isBlank()) return
        val file = File(Uri.parse(path).path ?: path)
        if (file.exists() && !file.delete()) Timber.w("Bon-Foto konnte nicht gelöscht werden: %s", file.name)
    }

    /**
     * Löscht die Kamera-Temp-Datei — und nur Dateien unter `cacheDir/receipts/` (nie Galerie-Bilder).
     * Die Kamera liefert eine FileProvider-URI (`content://<paket>.fileprovider/receipt_captures/<datei>`,
     * siehe `res/xml/file_paths.xml`); `file://`-Pfade unter dem Kamera-Ordner werden ebenfalls akzeptiert.
     */
    fun deleteCameraTemp(uri: Uri?) {
        uri ?: return
        val file = cameraTempFile(uri.scheme, uri.authority, uri.pathSegments, uri.path, context.packageName)
            ?: return
        val inCameraDir = runCatching { file.canonicalPath.startsWith(cameraDir.canonicalPath + File.separator) }
            .getOrDefault(false)
        if (inCameraDir) file.delete()
    }

    private fun cameraTempFile(
        scheme: String?, authority: String?, segments: List<String>, path: String?, packageName: String,
    ): File? = when {
        scheme == "content" && authority == "$packageName.fileprovider" &&
            segments.size == 2 && segments[0] == CAMERA_PATH_NAME -> File(cameraDir, segments[1])
        scheme == "file" && path != null -> File(path)
        else -> null
    }

    /** Entfernt Fotos in `filesDir/receipts/`, auf die kein Bon mehr verweist (verwaiste Altlasten). */
    fun cleanupOrphans(referenced: Set<String>) {
        val existing = photoDir.listFiles()?.map { it.absolutePath }.orEmpty()
        orphanFiles(existing, referenced.map { Uri.parse(it).path ?: it }.toSet())
            .forEach { File(it).delete() }
    }

    private companion object {
        const val MAX_DIM = 2000
        const val JPEG_QUALITY = 85
        // `name` des <cache-path> in res/xml/file_paths.xml
        const val CAMERA_PATH_NAME = "receipt_captures"
    }
}

/** Dateien, die vorhanden, aber nicht (mehr) von einem Bon referenziert sind. */
fun orphanFiles(existing: List<String>, referenced: Set<String>): List<String> =
    existing.filter { it !in referenced }
