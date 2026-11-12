package com.helga.android.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.SyncApiFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File

@HiltWorker
class ImageUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recipeDao: RecipeDao,
    private val apiFactory: SyncApiFactory,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pendingRecipes = recipeDao.recipesWithLocalImage()
        if (pendingRecipes.isEmpty()) return Result.success()

        val api = try {
            apiFactory.api()
        } catch (e: Exception) {
            Timber.w(e, "ImageUploadWorker: Server nicht konfiguriert")
            return Result.retry()
        }

        val allOk = uploadRecipeImages(api, pendingRecipes)
        return if (allOk) Result.success() else Result.retry()
    }

    private suspend fun uploadRecipeImages(
        api: SyncApi,
        pending: List<RecipeEntity>,
    ): Boolean {
        var ok = true
        for (recipe in pending) {
            val file = File(recipe.localImageUri)
            if (!file.exists()) {
                recipeDao.setImagePathAndClearLocal(recipe.id, recipe.imagePath, System.currentTimeMillis())
                continue
            }
            try {
                val part = filePart(file)
                val response = api.uploadImage(part)
                recipeDao.setImagePathAndClearLocal(recipe.id, response.filename, System.currentTimeMillis())
                Timber.d("ImageUpload Rezept ok: ${recipe.id} → ${response.filename}")
            } catch (e: Exception) {
                Timber.w(e, "ImageUpload Rezept fehlgeschlagen: ${recipe.id}")
                ok = false
            }
        }
        return ok
    }

    private fun filePart(file: File) = MultipartBody.Part.createFormData(
        name = "file",
        filename = file.name,
        body = file.asRequestBody("image/*".toMediaType()),
    )
}
