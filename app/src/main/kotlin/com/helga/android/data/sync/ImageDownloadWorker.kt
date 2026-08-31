package com.helga.android.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.util.ImageUrls
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Download-Pendant zu [ImageUploadWorker] (sync A4): lädt Bilder, die bereits auf dem Server
 * liegen (`imagePath` gesetzt) aber auf diesem Gerät noch nicht lokal gecacht sind
 * (`localImageUri` leer), proaktiv in Coils Disk-Cache — sonst wären sie auf einem neuen/zweiten
 * Gerät erst offline sichtbar, nachdem die jeweilige Detailansicht schon einmal online geöffnet
 * wurde. Schreibt bewusst nicht in `localImageUri` zurück: dieses Feld markiert für
 * [ImageUploadWorker], dass ein Bild noch hochgeladen werden muss — ein heruntergeladenes Bild,
 * das schon auf dem Server liegt, darf dort nicht erneut landen.
 */
@HiltWorker
class ImageDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recipeDao: RecipeDao,
    private val receiptDao: ReceiptDao,
    private val preferences: AppPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val conn = preferences.currentConnection()
        if (!conn.isConfigured) return Result.success()

        val recipeImagePaths = recipeDao.recipesNeedingImageDownload().map { it.imagePath }
        val receiptImagePaths = receiptDao.receiptsNeedingImageDownload().map { it.imagePath }
        val imagePaths = (recipeImagePaths + receiptImagePaths).distinct()
        if (imagePaths.isEmpty()) return Result.success()

        val loader = applicationContext.imageLoader
        var allOk = true
        imagePaths.forEach { imagePath ->
            val url = ImageUrls.serverImageUrl(conn.serverUrl, imagePath)
            try {
                val request = ImageRequest.Builder(applicationContext).data(url).build()
                val result = loader.execute(request)
                if (result !is SuccessResult) allOk = false
            } catch (e: Exception) {
                Timber.w(e, "ImageDownload fehlgeschlagen: $imagePath")
                allOk = false
            }
        }
        return if (allOk) Result.success() else Result.retry()
    }
}
