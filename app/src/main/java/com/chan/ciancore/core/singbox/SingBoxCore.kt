package com.chan.ciancore.core.singbox

import android.content.Context
import com.chan.ciancore.core.CoreType
import com.chan.ciancore.core.ProxyCore
import com.chan.ciancore.core.TrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libbox.CommandServer
import libbox.CommandServerHandler
import libbox.Libbox
import libbox.PlatformInterface
import libbox.SetupOptions

/**
 * sing-box adapter over the official `libbox.aar`
 * (built from SagerNet/sing-box `experimental/libbox` via gomobile).
 *
 * API surface (v1.14.3, verified from source):
 * - Libbox.setup(SetupOptions)
 * - Libbox.newCommandServer(handler, platformInterface) -> CommandServer
 * - CommandServer.start() / close()
 * - CommandServer.startOrReloadService() / closeService()
 *
 * NOTE: sing-box takes its own JSON config format (not Xray's).
 * Config translation from Node -> sing-box JSON lands with the
 * feature work; the skeleton passes through raw JSON.
 *
 * The AAR is built in CI from official source — see
 * .github/workflows/build.yml. Do NOT substitute a hand-built AAR
 * from an unverified environment (cf. 2026-10-17 Xray perf incident).
 */
class SingBoxCore(private val appContext: Context) : ProxyCore {

    override val type: CoreType = CoreType.SING_BOX
    override val version: String = "v1.14.3"

    @Volatile
    private var server: CommandServer? = null

    @Volatile
    private var initialized = false

    override fun initialize(context: Context) {
        if (initialized) return
        val opts = SetupOptions()
        opts.basePath = context.filesDir.absolutePath
        opts.workingPath = context.filesDir.absolutePath + "/singbox"
        // TODO: tempPath, writable paths per libbox docs
        Libbox.setup(opts)
        initialized = true
    }

    override fun start(configJson: String): Result<Unit> = runCatching {
        initialize(appContext)
        stop() // ensure clean state
        // TODO: write configJson to the box working dir / pass via
        // command server per libbox config flow, then:
        val handler = BoxCommandHandler()
        val platform = BoxPlatformInterface(appContext)
        val s = Libbox.newCommandServer(handler, platform)
        s.start()
        // TODO: push config via s.startOrReloadService() once the
        // config-handoff shape is confirmed against the built AAR.
        server = s
    }

    override fun stop() {
        try {
            server?.closeService()
            server?.close()
        } catch (_: Throwable) {
        } finally {
            server = null
        }
    }

    override val isRunning: Boolean
        get() = server != null

    override suspend fun measureLatency(configJson: String): Long =
        withContext(Dispatchers.IO) {
            // TODO: spin up an ephemeral sing-box instance with the
            // given config and measure against the test URL, mirroring
            // the XrayCore approach. Returns -1 until wired.
            -1L
        }

    override fun queryTrafficStats(): TrafficStats {
        // TODO: query via command client once the stats API shape is
        // confirmed against the built AAR.
        return TrafficStats()
    }

    // ── libbox plumbing ──────────────────────────────────────────

    /**
     * Minimal CommandServerHandler. Only the callbacks we need are
     * meaningfully implemented; the rest are no-ops for the skeleton.
     */
    private class BoxCommandHandler : CommandServerHandler {
        override fun writeLog(message: String?) {
            // TODO: route to Android log / in-app log viewer
        }
    }

    /**
     * Minimal PlatformInterface for TUN integration.
     * Full method set lands with the VPN plumbing work.
     */
    private class BoxPlatformInterface(
        private val context: Context
    ) : PlatformInterface {
        // TODO: implement TUN open / route / DNS per
        // experimental/libbox PlatformInterface contract.
    }
}
