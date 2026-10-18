package com.chan.ciancore.core.singbox

import android.content.Context
import com.chan.ciancore.core.CoreType
import com.chan.ciancore.core.ProxyCore
import com.chan.ciancore.core.TrafficStats

/**
 * sing-box adapter — PLACEHOLDER for v0.2.0.
 *
 * The real implementation targets the official `libbox.aar` built from
 * SagerNet/sing-box `experimental/libbox` via gomobile, but it is
 * deliberately NOT packaged yet:
 *
 * 1. Both libbox.aar and libv2ray.aar ship `jni/arm64-v8a/libgojni.so`
 *    under the same name -> mergeDebugNativeLibs fails.
 * 2. Even renamed, two gomobile .so files mean two Go runtimes
 *    (signal handlers, schedulers) in one process — not something to
 *    validate blindly without a test device.
 *
 * The dual-.so problem gets its own design pass in the sing-box phase.
 * Until then every op reports "not implemented" and the UI keeps the
 * core switcher (Xray works fully).
 */
class SingBoxCore(private val appContext: Context) : ProxyCore {

    override val type: CoreType = CoreType.SING_BOX
    override val version: String = "v1.14.3 (not packaged)"

    override fun initialize(context: Context) = Unit

    override fun start(configJson: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("sing-box 核心尚未接入，当前版本仅支持 Xray"))

    override fun stop() = Unit

    override val isRunning: Boolean = false

    override suspend fun measureLatency(configJson: String): Long = -1L

    override fun queryTrafficStats(): TrafficStats = TrafficStats()
}
