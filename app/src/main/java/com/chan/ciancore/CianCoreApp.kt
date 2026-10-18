package com.chan.ciancore

import android.app.Application
import com.chan.ciancore.data.AppDatabase
import com.chan.ciancore.data.GeoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * CianCore application entry point.
 * Owns the Room database singleton.
 */
class CianCoreApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Ensure geo dat files are in the Xray asset dir.
        appScope.launch { GeoManager.ensureBundled(this@CianCoreApp) }
    }

    companion object {
        lateinit var instance: CianCoreApp
            private set
    }
}
