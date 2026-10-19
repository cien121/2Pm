package com.chan.ciancore.core.singbox

import android.content.Context
import android.util.Log
import com.chan.ciancore.core.CoreType
import com.chan.ciancore.core.ProxyCore
import com.chan.ciancore.core.TrafficStats
import go.Seq
import java.io.File
import libbox.Libbox
import libbox.SetupOptions

/**
 * sing-box core via official libbox (v1.14.3).
 *
 * Real API (verified against sing-box v1.14.3 source):
 * - Libbox.setup(SetupOptions)
 * - Libbox.newCommandServer(handler, platformInterface) -> CommandServer
 * - server.start() -> server.startOrReloadService(configJson, null)
 * - server.closeService() + server.close() to stop
 * - TUN is created via PlatformInterface.openTun() callback (libbox -> app direction)
 */
class SingBoxCore(
    private val appContext: Context,
    private val platformInterface: SingBoxPlatformInterface? = null
) : ProxyCore {

    override val type: CoreType = CoreType.SING_BOX
    override val version: String = "v1.14.3"

    @Volatile
    private var commandServer: Any? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var running = false

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
            // Libbox.setup with SetupOptions
            val filesDir = context.filesDir
            val baseDir = File(filesDir, "singbox").apply { mkdirs() }.absolutePath
            val options = SetupOptions()
            options.basePath = baseDir
            options.workingPath = baseDir
            options.tempPath = File(filesDir, "singbox-tmp").apply { mkdirs() }.absolutePath
            Libbox.setup(options)
            initialized = true
            Log.i(TAG, "libbox setup done: $baseDir")
        }
    }

    override fun start(configJson: String): Result<Unit> {
        return try {
            initialize(appContext)
            // Validate config before starting (avoid native crash on bad JSON)
            try {
                Libbox.checkConfig(configJson)
            } catch (e: Throwable) {
                Log.e(TAG, "config validation failed", e)
                return Result.failure(IllegalArgumentException("配置校验失败: ${e.message}"))
            }
            val handler = SingBoxCommandHandler()
            val platform = platformInterface
                ?: throw IllegalStateException("PlatformInterface not set for sing-box")
            // newCommandServer(handler, platformInterface)
            val server = Libbox.newCommandServer(handler, platform)
            server.start()
            // startOrReloadService(configContent, options): options must be a real
            // OverrideOptions — Go dereferences it directly, null would panic.
            server.startOrReloadService(configJson, libbox.OverrideOptions())
            commandServer = server
            running = true
            Log.i(TAG, "sing-box service started")
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "sing-box start failed", e)
            Result.failure(e)
        }
    }

    override fun stop() {
        val server = synchronized(this) {
            val s = commandServer as? libbox.CommandServer
            commandServer = null
            running = false
            s
        } ?: return
        // Close outside the lock; each step guarded so a native crash
        // in one doesn't skip the other.
        try {
            server.closeService()
        } catch (e: Throwable) {
            Log.w(TAG, "closeService failed", e)
        }
        try {
            server.close()
        } catch (e: Throwable) {
            Log.w(TAG, "server close failed", e)
        }
    }

    override val isRunning: Boolean get() = running

    override suspend fun measureLatency(configJson: String): Long = -1L

    override fun queryTrafficStats(): TrafficStats = TrafficStats()

    companion object {
        private const val TAG = "SingBoxCore"
    }
}
