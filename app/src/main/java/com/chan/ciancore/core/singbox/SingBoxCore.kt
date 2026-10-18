package com.chan.ciancore.core.singbox

import android.content.Context
import android.util.Log
import com.chan.ciancore.core.CoreType
import com.chan.ciancore.core.ProxyCore
import com.chan.ciancore.core.TrafficStats
import go.Seq
import java.io.File

class SingBoxCore(private val appContext: Context) : ProxyCore {

    override val type: CoreType = CoreType.SING_BOX
    override val version: String = "v1.14.3"

    @Volatile
    private var instance: Any? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var running = false

    @Volatile
    private var tunFd: Int = 0

    fun setTunFd(fd: Int) {
        tunFd = fd
    }

    override fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                System.loadLibrary("boxjni")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load boxjni", e)
                throw e
            }
            Seq.setContext(context.applicationContext)
            initialized = true
        }
    }

    override fun start(configJson: String): Result<Unit> {
        return try {
            initialize(appContext)
            val libboxClass = Class.forName("libbox.Libbox")
            val filesDir = appContext.filesDir
            val workDir = File(filesDir, "singbox").apply { mkdirs() }.absolutePath
            val setupMethod = libboxClass.getMethod(
                "setup",
                String::class.java, String::class.java, String::class.java
            )
            setupMethod.invoke(null, workDir, workDir, workDir)
            val optionsClass = Class.forName("libbox.PlatformOptions")
            val options = optionsClass.newInstance()
            try {
                val configField = optionsClass.getField("configContent")
                configField.set(options, configJson)
            } catch (_: Throwable) {
                val setter = optionsClass.methods.firstOrNull {
                    it.name.equals("setConfigContent", ignoreCase = true)
                }
                setter?.invoke(options, configJson)
            }
            val newMethod = libboxClass.getMethod("newSingBox", optionsClass)
            val boxInstance = newMethod.invoke(null, options)
            instance = boxInstance
            val startMethod = boxInstance.javaClass.getMethod("start")
            startMethod.invoke(boxInstance)
            running = true
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "sing-box start failed", e)
            Result.failure(e)
        }
    }

    override fun stop() {
        try {
            val boxInstance = instance ?: return
            val closeMethod = boxInstance.javaClass.getMethod("close")
            closeMethod.invoke(boxInstance)
        } catch (e: Throwable) {
            Log.e(TAG, "sing-box stop failed", e)
        } finally {
            instance = null
            running = false
        }
    }

    override val isRunning: Boolean get() = running

    override suspend fun measureLatency(configJson: String): Long = -1L

    override fun queryTrafficStats(): TrafficStats = TrafficStats()

    companion object {
        private const val TAG = "SingBoxCore"
    }
}
