package com.chan.weave.core.xray

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.chan.weave.core.CoreType
import com.chan.weave.core.ProxyCore
import com.chan.weave.core.TrafficStats
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.security.MessageDigest

/**
 * Xray-core adapter over the official `libv2ray.aar`
 * (2dust/AndroidLibXrayLite).
 *
 * API surface verified against the v26.10.10 AAR (javap on
 * classes.jar) and 2dust/AndroidLibXrayLite Go source:
 * - Libv2ray.initCoreEnv(assetPath, deviceId)
 * - Libv2ray.newCoreController(handler) -> CoreController
 * - Libv2ray.measureOutboundDelay(configJson, testUrl) -> Long
 * - CoreController.startLoop(configJson, tunFd)
 * - CoreController.stopLoop()
 * - CoreController.measureDelay(url) -> Long (via the RUNNING core)
 * - CoreController.queryAllOutboundTrafficStats() -> String
 *   "tag,direction,value;..." (query-AND-RESET semantics)
 * - CoreController.getIsRunning() -> Boolean
 */
class XrayCore(private val appContext: Context) : ProxyCore {

    override val type: CoreType = CoreType.XRAY
    override val version: String = "v26.10.10"

    @Volatile
    private var controller: CoreController? = null

    @Volatile
    private var initialized = false

    /** TUN fd handed to startLoop; set by the VPN service before start(). */
    @Volatile
    private var tunFd: Int = 0

    private val statsLock = Any()
    private var txTotal: Long = 0
    private var rxTotal: Long = 0

    fun setTunFd(fd: Int) {
        tunFd = fd
    }

    override fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            Seq.setContext(context.applicationContext)
            val assetDir = File(context.filesDir, "xray").apply { mkdirs() }
            val deviceId = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ANDROID_ID
            ).orEmpty()
            // The AAR's initCoreEnv sets xray.xudp.basekey from this key.
            // Xray requires it to be base64url-encoded 32 bytes, so derive a
            // stable per-device key: base64url(SHA-256(ANDROID_ID)).
            // Passing the raw ANDROID_ID breaks core init
            // ("BaseKey must be 32 bytes").
            val xudpKey = try {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(deviceId.toByteArray(Charsets.UTF_8))
                Base64.encodeToString(
                    digest,
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
            } catch (_: Throwable) {
                ""
            }
            Libv2ray.initCoreEnv(assetDir.absolutePath, xudpKey)
            initialized = true
        }
    }

    override fun start(configJson: String): Result<Unit> = runCatching {
        initialize(appContext)
        stop() // ensure clean state
        val c = Libv2ray.newCoreController(CoreCallback())
        c.startLoop(configJson, tunFd)
        if (c.getIsRunning() != true) {
            throw IllegalStateException("核心启动后未能进入运行状态")
        }
        controller = c
        synchronized(statsLock) {
            txTotal = 0
            rxTotal = 0
        }
    }

    override fun stop() {
        try {
            val c = controller
            c?.stopLoop()
            // Wait for native Xray to actually stop (stopLoop is async).
            // If we start a new instance while the old one still holds
            // 127.0.0.1:10808, the new SOCKS inbound fails to bind and
            // traffic keeps going through the OLD node (IP doesn't change).
            var waited = 0
            while (waited < 3000) {
                try {
                    if (c?.getIsRunning() != true) break
                } catch (_: Throwable) {
                    break
                }
                Thread.sleep(100)
                waited += 100
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stopLoop failed", t)
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
        measureLatency(configJson, DELAY_TEST_URL)

    suspend fun measureLatency(configJson: String, testUrl: String): Long =
        withContext(Dispatchers.IO) {
            try {
                initialize(appContext)
                // Ephemeral instance; does not disturb the running controller.
                Libv2ray.measureOutboundDelay(configJson, testUrl)
            } catch (t: Throwable) {
                Log.w(TAG, "measureLatency failed", t)
                -1L
            }
        }

    override fun queryTrafficStats(): TrafficStats {
        val c = controller ?: return TrafficStats(txTotal, rxTotal)
        return try {
            // Go side does query-and-reset; accumulate into session totals.
            val payload = c.queryAllOutboundTrafficStats().orEmpty()
            var tx = 0L
            var rx = 0L
            payload.split(';').forEach { entry ->
                if (entry.isBlank()) return@forEach
                val parts = entry.split(',', limit = 3)
                if (parts.size != 3) return@forEach
                if (parts[0] != "proxy") return@forEach
                val value = parts[2].toLongOrNull() ?: return@forEach
                when (parts[1]) {
                    "uplink" -> tx += value
                    "downlink" -> rx += value
                }
            }
            synchronized(statsLock) {
                txTotal += tx
                rxTotal += rx
                TrafficStats(txTotal, rxTotal)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "queryTrafficStats failed", t)
            synchronized(statsLock) { TrafficStats(txTotal, rxTotal) }
        }
    }

    private class CoreCallback : CoreCallbackHandler {
        override fun startup(): Long {
            Log.i(TAG, "core startup")
            return 0
        }

        override fun shutdown(): Long {
            Log.i(TAG, "core shutdown")
            return 0
        }

        override fun onEmitStatus(l: Long, s: String?): Long {
            Log.i(TAG, "core status: $s")
            return 0
        }
    }

    companion object {
        private const val TAG = "XrayCore"
        const val DELAY_TEST_URL = "https://www.gstatic.com/generate_204"
    }
}
