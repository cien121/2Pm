package com.chan.ciancore.core.xray

import android.content.Context
import com.chan.ciancore.core.CoreType
import com.chan.ciancore.core.ProxyCore
import com.chan.ciancore.core.TrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Xray-core adapter over the official `libv2ray.aar`
 * (2dust/AndroidLibXrayLite).
 *
 * Verified API surface (v26.10.10):
 * - Libv2ray.initCoreEnv(context, userAgent)
 * - Libv2ray.newCoreController() -> CoreController
 * - CoreController.startLoop(configJson)
 * - CoreController.stopLoop()
 * - CoreController.measureDelay(configJson) -> Long (ms)
 * - CoreController.queryAllOutboundTrafficStats() -> LongArray [tx, rx]
 * - CoreController.getIsRunning() -> Boolean
 */
class XrayCore(private val appContext: Context) : ProxyCore {

    override val type: CoreType = CoreType.XRAY
    override val version: String = "v26.10.10"

    @Volatile
    private var controller: CoreController? = null

    @Volatile
    private var initialized = false

    override fun initialize(context: Context) {
        if (initialized) return
        // Package name as user agent, matching upstream convention.
        Libv2ray.initCoreEnv(context.applicationContext, "com.chan.ciancore")
        initialized = true
    }

    override fun start(configJson: String): Result<Unit> = runCatching {
        initialize(appContext)
        stop() // ensure clean state
        val c = Libv2ray.newCoreController()
        // startLoop throws / returns error string on failure depending on build;
        // treat any exception as failure.
        c.startLoop(configJson)
        controller = c
    }

    override fun stop() {
        try {
            controller?.stopLoop()
        } catch (_: Throwable) {
        } finally {
            controller = null
        }
    }

    override val isRunning: Boolean
        get() = try {
            controller?.getIsRunning() == true
        } catch (_: Throwable) {
            false
        }

    override suspend fun measureLatency(configJson: String): Long =
        withContext(Dispatchers.IO) {
            try {
                initialize(appContext)
                // measureDelay spins up an ephemeral instance; does not
                // disturb the running controller.
                val c = Libv2ray.newCoreController()
                try {
                    c.measureDelay(configJson)
                } finally {
                    try {
                        c.stopLoop()
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
                -1L
            }
        }

    override fun queryTrafficStats(): TrafficStats {
        return try {
            // Returns LongArray [txBytes, rxBytes] per upstream convention.
            val arr = controller?.queryAllOutboundTrafficStats()
            if (arr != null && arr.size >= 2) {
                TrafficStats(txBytes = arr[0], rxBytes = arr[1])
            } else {
                TrafficStats()
            }
        } catch (_: Throwable) {
            TrafficStats()
        }
    }
}
