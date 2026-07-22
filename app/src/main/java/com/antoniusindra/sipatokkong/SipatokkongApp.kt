package com.antoniusindra.sipatokkong

import android.app.Application
import com.antoniusindra.sipatokkong.core.di.ServiceLocator

/**
 * Application entry point. Initializes the manual DI [ServiceLocator]
 * (no Hilt for the pilot — kept intentionally lightweight).
 */
class SipatokkongApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
