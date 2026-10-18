package com.chan.ciancore.core

import android.content.Context
import com.chan.ciancore.core.xray.XrayConfigBuilder
import com.chan.ciancore.core.xray.XrayCore
import com.chan.ciancore.data.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Latency testing: TCP ping + real-connection (Xray handshake) test.
 *
 * - tcpPing: raw TCP connect timing to host:port.
 * - realTest: full Xray outbound dial + HTTP 204 round trip.
 * Returns milliseconds, or -1 on failure.
 */
object LatencyTester {

    /** TCP connect latency in ms, -1 on failure/timeout. */
    suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = 5000): Long =
        withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                System.currentTimeMillis() - start
            } catch (_: Throwable) {
                -1L
            }
        }

    /**
     * Real connection test: builds the node's Xray config and measures
     * a full handshake + HTTP round trip through the proxy.
     * Returns milliseconds, or -1 on failure.
     */
    suspend fun realTest(context: Context, node: Node): Long =
        withContext(Dispatchers.IO) {
            try {
                if (node.coreType != CoreType.XRAY) return@withContext -1L
                val config = XrayConfigBuilder.buildOutboundTest(node)
                XrayCore(context.applicationContext).measureLatency(config)
            } catch (_: Throwable) {
                -1L
            }
        }
}
