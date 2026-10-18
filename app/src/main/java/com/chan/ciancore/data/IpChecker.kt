package com.chan.ciancore.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Egress IP check: what the internet sees through the tunnel.
 * Fetches from ipify (plain-text IP), auto-detects IPv4/IPv6.
 */
object IpChecker {

    data class IpInfo(val ip: String, val isV6: Boolean)

    /** Returns the egress IP, or null on failure. Runs off the main thread. */
    suspend fun check(): IpInfo? = withContext(Dispatchers.IO) {
        // Try IPv6-capable endpoint first, then plain IPv4.
        checkUrl("https://api64.ipify.org")
            ?: checkUrl("https://api.ipify.org")
    }

    private fun checkUrl(url: String): IpInfo? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "CianCore/1.0")
            }
            try {
                if (conn.responseCode !in 200..299) return null
                val ip = conn.inputStream.bufferedReader().readText().trim()
                if (ip.isEmpty() || ip.length > 45) return null
                IpInfo(ip, ip.contains(":"))
            } finally {
                conn.disconnect()
            }
        } catch (_: Throwable) {
            null
        }
    }
}
