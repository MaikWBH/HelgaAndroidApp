package com.helga.android

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.sync.ForegroundSyncObserver
import com.helga.android.data.sync.NetworkObserver
import com.helga.android.data.sync.NotificationScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class HelgaApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var networkObserver: NetworkObserver
    @Inject lateinit var foregroundSyncObserver: ForegroundSyncObserver
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var preferences: AppPreferences

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            enableStrictMode()
        }
        networkObserver.start()
        foregroundSyncObserver.start()
        NotificationScheduler.schedule(this)
    }

    override fun newImageLoader(): ImageLoader {
        val client = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val conn = runBlocking { preferences.currentConnection() }
                val request = if (conn.apiKey.isNotBlank()) {
                    chain.request().newBuilder()
                        .header("X-Api-Key", conn.apiKey)
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }
}
