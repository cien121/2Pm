package com.chan.weave

import android.app.Application
import com.chan.weave.data.AppDatabase
import com.chan.weave.data.GeoManager
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
        // Ensure geo dat files are available (download if missing).
        appScope.launch { GeoManager.ensureAvailable(this@CianCoreApp) }
    }

    companion object {
        lateinit var instance: CianCoreApp
            private set
    }
}
