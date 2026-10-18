package com.chan.ciancore.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Mux (multiplexing) and Fragment settings, modeled on v2rayNG.
 *
 * Mux reuses a single connection for multiple streams, cutting latency.
 * Fragment splits TLS handshakes to evade DPI/SNI-based blocking.
 */
object StreamPrefs {

    private const val FILE = "stream"
    private const val K_MUX_ENABLED = "mux_enabled"
    private const val K_MUX_CONCURRENCY = "mux_concurrency"
    private const val K_FRAGMENT_ENABLED = "fragment_enabled"
    private const val K_FRAGMENT_PACKETS = "fragment_packets"
    private const val K_FRAGMENT_LENGTH = "fragment_length"
    private const val K_FRAGMENT_INTERVAL = "fragment_interval"
    private const val K_LOCAL_DNS_ENABLED = "local_dns_enabled"

    // v2rayNG defaults
    const val DEFAULT_CONCURRENCY = 8
    const val DEFAULT_PACKETS = "tlshello"
    const val DEFAULT_LENGTH = "50-100"
    const val DEFAULT_INTERVAL = "10-20"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ── Mux ──────────────────────────────────────────────

    fun isMuxEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(K_MUX_ENABLED, false)

    fun setMuxEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit { putBoolean(K_MUX_ENABLED, enabled) }

    fun getMuxConcurrency(ctx: Context): Int =
        prefs(ctx).getInt(K_MUX_CONCURRENCY, DEFAULT_CONCURRENCY).coerceIn(1, 1024)

    fun setMuxConcurrency(ctx: Context, value: Int) =
        prefs(ctx).edit { putInt(K_MUX_CONCURRENCY, value.coerceIn(1, 1024)) }

    // ── Fragment ─────────────────────────────────────────

    fun isFragmentEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(K_FRAGMENT_ENABLED, false)

    fun setFragmentEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit { putBoolean(K_FRAGMENT_ENABLED, enabled) }

    fun getFragmentPackets(ctx: Context): String =
        prefs(ctx).getString(K_FRAGMENT_PACKETS, DEFAULT_PACKETS).orEmpty()
            .ifBlank { DEFAULT_PACKETS }

    fun setFragmentPackets(ctx: Context, value: String) =
        prefs(ctx).edit { putString(K_FRAGMENT_PACKETS, value.ifBlank { DEFAULT_PACKETS }) }

    fun getFragmentLength(ctx: Context): String =
        prefs(ctx).getString(K_FRAGMENT_LENGTH, DEFAULT_LENGTH).orEmpty()
            .ifBlank { DEFAULT_LENGTH }

    fun setFragmentLength(ctx: Context, value: String) =
        prefs(ctx).edit { putString(K_FRAGMENT_LENGTH, value.ifBlank { DEFAULT_LENGTH }) }

    fun getFragmentInterval(ctx: Context): String =
        prefs(ctx).getString(K_FRAGMENT_INTERVAL, DEFAULT_INTERVAL).orEmpty()
            .ifBlank { DEFAULT_INTERVAL }

    fun setFragmentInterval(ctx: Context, value: String) =
        prefs(ctx).edit { putString(K_FRAGMENT_INTERVAL, value.ifBlank { DEFAULT_INTERVAL }) }

    // ── Local DNS ────────────────────────────────────────
    // When ON, DNS queries are handled by Xray's DNS module (split horizon:
    // CN domains -> direct DNS, others -> remote DNS via tunnel).
    // When OFF, DNS goes to the TUN DNS server through the tunnel.

    fun isLocalDnsEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(K_LOCAL_DNS_ENABLED, false)

    fun setLocalDnsEnabled(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit { putBoolean(K_LOCAL_DNS_ENABLED, enabled) }
}
