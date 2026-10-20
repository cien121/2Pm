package com.chan.weave.core

import android.content.Context
import com.chan.weave.core.xray.XrayConfigBuilder
import com.chan.weave.core.xray.XrayCore
import com.chan.weave.data.DnsPrefs.testUrl
import com.chan.weave.data.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Latency testing: TCP ping + real-connection (Xray handshake) test.
 *
 * - tcpPing: raw TCP connect timing to host:port.
 * - realTest: full Xray outbound dial + HTTP round trip to the
 *   user-configured test URL.
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
                val config = if (node.protocol == "proxychain") {
                    // Resolve chain members from the DB and build a chain test config.
                    val ids = node.rawLink.removePrefix("proxychain:").trim()
                        .split(",").mapNotNull { it.toLongOrNull() }
                    val dao = com.chan.weave.CianCoreApp.instance.database.nodeDao()
                    val members = ids.mapNotNull { runCatching { dao.getById(it) }.getOrNull() }
                        .filter { it.protocol != "proxychain" }
                    if (members.isEmpty()) return@withContext -1L
                    XrayConfigBuilder.buildChainTest(members)
                } else {
                    XrayConfigBuilder.buildOutboundTest(node)
                }
                val url = context.testUrl.ifBlank { XrayCore.DELAY_TEST_URL }
                XrayCore(context.applicationContext).measureLatency(config, url)
            } catch (_: Throwable) {
                -1L
            }
        }

    /**
     * Test many nodes with bounded concurrency.
     * Calls [onResult] for each finished node.
     */
    suspend fun testAll(
        context: Context,
        nodes: List<Node>,
        maxConcurrent: Int,
        onResult: suspend (Node, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(maxConcurrent.coerceIn(1, 32))
        nodes.map { node ->
            async {
                semaphore.withPermit {
                    val ms = realTest(context, node)
                    val final = if (ms < 0) tcpPing(node.host, node.port) else ms
                    onResult(node, final)
                }
            }
        }.awaitAll()
    }
}
