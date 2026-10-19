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
        // :singbox process only needs minimal init (no Xray geo files, no DB)
        if (isSingBoxProcess()) return
        // Ensure geo dat files are in the Xray asset dir.
        appScope.launch { GeoManager.ensureBundled(this@CianCoreApp) }
    }

    private fun isSingBoxProcess(): Boolean {
        return try {
            // API 28+: Application.getProcessName()
            val processName = if (android.os.Build.VERSION.SDK_INT >= 28) {
                Application.getProcessName()
            } else {
                // Fallback: read from ActivityManager
                val am = getSystemService(android.app.ActivityManager::class.java)
                val pid = android.os.Process.myPid()
                am?.runningAppProcesses?.find { it.pid == pid }?.processName
            }
            processName?.endsWith(":singbox") == true
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        lateinit var instance: CianCoreApp
            private set
    }
}
