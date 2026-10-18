package com.chan.ciancore.data

import android.content.Context
import com.chan.ciancore.data.DnsPrefs.ipInfoUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Egress IP check: what the internet sees through the tunnel.
 * Uses the user-configured info URL (default api.ip.sb/geoip),
 * falls back to plain-text ipify endpoints. Auto-detects IPv4/IPv6.
 */
object IpChecker {

    data class IpInfo(val ip: String, val isV6: Boolean)

    /** Returns the egress IP, or null on failure. Runs off the main thread. */
    suspend fun check(context: Context): IpInfo? = withContext(Dispatchers.IO) {
        val configured = context.ipInfoUrl.trim()
        (if (configured.isNotEmpty()) checkUrl(configured) else null)
            ?: checkUrl("https://api64.ipify.org")
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
                val body = conn.inputStream.bufferedReader().readText().trim()
                val ip = extractIp(body) ?: return null
                IpInfo(ip, ip.contains(":"))
            } finally {
                conn.disconnect()
            }
        } catch (_: Throwable) {
            null
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
