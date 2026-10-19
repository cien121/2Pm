package com.chan.ciancore.core.singbox

import android.util.Log
import libbox.PlatformInterface
import libbox.TunOptions

/**
 * Implements libbox.PlatformInterface.
 *
 * The critical method is openTun(): when sing-box starts a config containing
 * a "tun" inbound, it calls back here. We build the TUN via Android
 * VpnService.Builder and return the detached fd as Int.
 * Go side dups the fd and takes over the TUN device.
 */
class SingBoxPlatformInterface(
    private val tunBuilder: (TunOptions) -> Int
) : PlatformInterface {

    override fun openTun(options: TunOptions): Int {
        Log.i(TAG, "libbox openTun callback")
        return tunBuilder(options)
    }

    // ── Conservative defaults for the rest ──

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = false
    override fun autoDetectInterfaceControl(fd: Int) {}
    override fun getInterfaces(): libbox.NetworkInterfaceIterator? = null
    override fun underNetworkExtension(): Boolean = false
    override fun networkExtensionIncludeAllNetworks(): Boolean = false
    override fun clearDNSCache() {}
    override fun readWIFIState(): libbox.WIFIState? = null

    companion object {
        private const val TAG = "SingBoxPlatform"
    }
}
