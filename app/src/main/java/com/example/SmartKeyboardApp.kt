package com.example

import android.app.Application
import com.example.data.repository.KeyboardRepository

class SmartKeyboardApp : Application() {
    lateinit var repository: KeyboardRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = KeyboardRepository(this)
    }

    companion object {
        lateinit var instance: SmartKeyboardApp
            private set
    }
}
