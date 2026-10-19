package com.chan.ciancore.core.singbox

import android.util.Log
import libbox.PlatformInterface
import libbox.TunOptions

/**
 * Implements libbox.PlatformInterface (v1.14.3).
 *
 * The critical method is openTun(): when sing-box starts a config containing
 * a "tun" inbound, it calls back here. We build the TUN via Android
 * VpnService.Builder and return the detached fd as Int.
 * Go side dups the fd and takes over the TUN device.
 */
class SingBoxPlatformInterface(
    private val tunBuilder: (TunOptions) -> Int,
    private val protectSocket: (Int) -> Boolean = { false }
) : PlatformInterface {

    override fun openTun(options: TunOptions): Int {
        Log.i(TAG, "libbox openTun callback")
        return try {
            tunBuilder(options)
        } catch (t: Throwable) {
            Log.e(TAG, "openTun failed", t)
            // Return -1 to signal failure to Go side instead of crashing
            -1
        }
    }

    // ── Interface control: MUST protect outbound sockets from TUN loop ──

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun autoDetectInterfaceControl(fd: Int) {
        try {
            protectSocket(fd)
        } catch (t: Throwable) {
            Log.w(TAG, "protect($fd) failed", t)
        }
    }
    override fun useProcFS(): Boolean = false
    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String, sourcePort: Int,
        destinationAddress: String, destinationPort: Int
    ): libbox.ConnectionOwner? = null
    override fun startDefaultInterfaceMonitor(listener: libbox.InterfaceUpdateListener) {}
    override fun closeDefaultInterfaceMonitor(listener: libbox.InterfaceUpdateListener) {}
    override fun getInterfaces(): libbox.NetworkInterfaceIterator? = null
    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun readWIFIState(): libbox.WIFIState? = null
    override fun clearDNSCache() {}
    override fun sendNotification(notification: libbox.Notification) {}
    override fun cancelNotification(identifier: String, typeID: Int) {}
    override fun startNeighborMonitor(listener: libbox.NeighborUpdateListener) {}
    override fun closeNeighborMonitor(listener: libbox.NeighborUpdateListener) {}
    override fun registerMyInterface(name: String) {
        Log.i(TAG, "registerMyInterface: $name")
    }
    override fun usePlatformShell(): Boolean = false
    override fun checkPlatformShell() {}
    override fun openShellSession(
        user: libbox.PlatformUser, command: String,
        environ: libbox.StringIterator, term: String, rows: Int, cols: Int
    ): libbox.ShellSession? = null
    override fun lookupUser(username: String): libbox.PlatformUser? = null
    override fun lookupSFTPServer(): String = ""
    override fun readSystemSSHHostKey(): String = ""
    override fun tailscaleHostname(): String = ""
    override fun usePlatformBridge(): Boolean = false
    override fun createBridge(options: libbox.BridgeOptions): libbox.BridgeSession? = null

    companion object {
        private const val TAG = "SingBoxPlatform"
    }
}
