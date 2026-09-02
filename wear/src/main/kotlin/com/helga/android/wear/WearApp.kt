package com.helga.android.wear

import android.app.Application

/** Kein Hilt/Room auf der Uhr — die eine Repository-Instanz reicht als App-weiter Singleton. */
class WearApp : Application() {

    lateinit var shoppingRepository: WearShoppingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        shoppingRepository = WearShoppingRepository(this)
    }
}
