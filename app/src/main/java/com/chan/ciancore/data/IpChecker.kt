package com.chan.ciancore.data

import android.content.Context
import com.chan.ciancore.data.DnsPrefs.ipInfoUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * Egress IP check: what the internet sees through the tunnel.
 *
 * IMPORTANT: The request goes through the local SOCKS inbound (127.0.0.1:10808)
 * so it traverses the tunnel. A direct connection would bypass the VPN
 * (the app's own package is excluded from the TUN to avoid loops) and
 * return the real IP instead of the egress IP.
 *
 * Design notes (per expert review):
 * - Every check uses a FRESH connection (Connection: close, no keep-alive reuse).
 *   Reusing a pooled connection could silently go through the OLD tunnel.
 * - v4 and v6 are checked SEPARATELY. A node may egress v6 only (VLESS) or
 *   v4 only (Hysteria); testing just one would miss the other.
 * - Retries with backoff: right after a core restart the SOCKS inbound may
 *   need 1-2s before it accepts connections. Don't give up on first timeout.
 */
object IpChecker {

    data class IpInfo(val ip: String, val isV6: Boolean)

    /** Both stacks, either may be null if that stack has no egress. */
    data class EgressInfo(val v4: String?, val v6: String?)

    /** Returns egress IPs for both stacks. Runs off the main thread. */
    suspend fun checkBoth(context: Context): EgressInfo = withContext(Dispatchers.IO) {
        val v6 = checkWithRetry("https://api64.ipify.org")
        val v4 = checkWithRetry("https://api.ipify.org")
        EgressInfo(v4 = v4, v6 = v6)
    }

    /** Returns the egress IP, preferring v6 or v4 based on protocol. Runs off the main thread. */
    suspend fun check(context: Context, preferV6: Boolean = true): IpInfo? = withContext(Dispatchers.IO) {
        val configured = context.ipInfoUrl.trim()
        if (configured.isNotEmpty()) {
            checkWithRetry(configured)?.let { ip ->
                return@withContext IpInfo(ip, ip.contains(":"))
            }
        }
        val both = checkBoth(context)
        // Protocol-aware: try preferred stack first, fall back to the other.
        val ip = if (preferV6) {
            both.v6 ?: both.v4
        } else {
            both.v4 ?: both.v6
        }
        ip?.let { IpInfo(it, it.contains(":")) }
    }

    /** Legacy single-IP check: prefers v6, falls back to v4. */
    suspend fun checkLegacy(context: Context): IpInfo? = check(context, preferV6 = true)

    /**
     * Check one URL with retries. New core restarts need a moment before
     * the SOCKS inbound accepts connections, so retry a few times.
     */
    private suspend fun checkWithRetry(url: String, maxAttempts: Int = 4): String? {
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                checkUrlOnce(url)?.let { return it }
            } catch (t: Throwable) {
                lastError = t
            }
            // Backoff: 500ms, 1000ms, 1500ms
            if (attempt < maxAttempts - 1) delay(500L * (attempt + 1))
        }
        return null
    }

    /** Single attempt with a completely fresh connection (no keep-alive reuse). */
    private fun checkUrlOnce(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            // Route through Xray's SOCKS inbound so the check exits via the tunnel.
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
            conn = (URL(url).openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                // CRITICAL: never reuse a pooled keep-alive connection — it may
                // still be bound to the OLD tunnel after a node switch.
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "CianCore/1.0")
                // Disable transparent response caching for this call.
                useCaches = false
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().readText().trim()
            extractIp(body)
        } catch (_: Throwable) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) { }
        }
    }

    /** Accepts plain-text IP or JSON like {"ip":"1.2.3.4",...}. */
    private fun extractIp(body: String): String? {
        if (body.isEmpty() || body.length > 4096) return null
        if (!body.startsWith("{")) {
            val ip = body.lineSequence().firstOrNull()?.trim().orEmpty()
            return ip.takeIf { it.isNotEmpty() && it.length <= 45 && '/' !in it }
        }
        return try {
            JSONObject(body).optString("ip").takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }
}
