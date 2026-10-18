package com.chan.ciancore.core.singbox

import android.content.Context
import android.util.Log
import com.chan.ciancore.core.CoreType
import com.chan.ciancore.core.ProxyCore
import com.chan.ciancore.core.TrafficStats
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * sing-box adapter over the official `libbox.aar`
 * built from SagerNet/sing-box `experimental/libbox` via gomobile.
 *
 * Dual-library notes:
 * - libbox.aar's `jni/arm64-v8a/libgojni.so` is renamed to `libboxjni.so`
 *   in CI to avoid merge conflicts with libv2ray.aar's copy.
 * - libbox.aar's `go/*` Java classes are stripped; the identical classes
 *   from libv2ray.aar are used at runtime (same gomobile version).
 * - This class loads `libboxjni.so` explicitly before touching libbox APIs.
 *
 * API surface: libbox.Libbox / libbox.SingBoxInstance from
 * sing-box v1.14.3 experimental/libbox.
 */
class SingBoxCore(private val appContext: Context) : ProxyCore {

    override val type: CoreType = CoreType.SING_BOX
    override val version: String = "v1.14.3"

    @Volatile
    private var instance: Any? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var running = false

    /** TUN fd handed to the VPN service; sing-box reads it via platform hooks. */
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
                // Load our renamed sing-box native library.
                // libv2ray's gojni is loaded separately by XrayCore.
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
            // libbox API via reflection to avoid hard compile dependency
            // on generated bindings (they're in the AAR).
            val libboxClass = Class.forName("libbox.Libbox")
            // Setup directories
            val filesDir = appContext.filesDir
            val setupMethod = libboxClass.getMethod(
                "setup",
                String::class.java, String::class.java, String::class.java
            )
            val workDir = File(filesDir, "singbox").apply { mkdirs() }.absolutePath
            setupMethod.invoke(null, workDir, workDir, workDir)

            // Create instance with config
            val newMethod = libboxClass.getMethod(
                "newSingBox",
                Class.forName("libbox.PlatformOptions")
            )
            // Build PlatformOptions with config content
            val optionsClass = Class.forName("libbox.PlatformOptions")
            val options = optionsClass.newInstance()
            // Set config content via reflection
            try {
                val configField = optionsClass.getField("configContent")
                configField.set(options, configJson)
            } catch (_: Throwable) {
                // Try setter
                val setter = optionsClass.methods.firstOrNull {
                    it.name.equals("setConfigContent", ignoreCase = true)
                }
                setter?.invoke(options, configJson)
            }

            val boxInstance = newMethod.invoke(null, options)
            instance = boxInstance

            // Start
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

    override suspend fun measureLatency(configJson: String): Long =
        withContext(Dispatchers.IO) {
            // TODO: implement via libbox URL test
            -1L
        }

    override fun queryTrafficStats(): TrafficStats = TrafficStats()

    companion object {
        private const val TAG = "SingBoxCore"
    }
}
