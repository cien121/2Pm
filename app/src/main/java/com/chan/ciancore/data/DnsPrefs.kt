package com.chan.ciancore.data

import android.content.Context
import androidx.core.content.edit

/**
 * DNS and test preferences (mirrors the NekoBox-style DNS suite).
 */
object DnsPrefs {

    private const val FILE = "dns"

    private const val KEY_LOCAL_DNS = "local_dns"
    private const val KEY_FAKE_DNS = "fake_dns"
    private const val KEY_VPN_DNS = "vpn_dns"
    private const val KEY_TEST_URL = "test_url"
    private const val KEY_CONCURRENT = "concurrent_tests"
    private const val KEY_IP_INFO_URL = "ip_info_url"
    private const val KEY_REMOTE_DNS = "remote_dns"
    private const val KEY_DIRECT_DNS = "direct_dns"
    private const val KEY_BYPASS_LAN = "bypass_lan"
    private const val KEY_VPN_ADDRESS = "vpn_address"
    private const val KEY_VPN_MTU = "vpn_mtu"
    private const val KEY_IPV6 = "ipv6_enabled"

    const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
    const val DEFAULT_IP_INFO_URL = "https://api.ip.sb/geoip"
    const val DEFAULT_REMOTE_DNS = "https://cloudflare-dns.com/dns-query"
    const val DEFAULT_DIRECT_DNS = "223.5.5.5"
    const val DEFAULT_VPN_DNS = "1.1.1.1,8.8.8.8"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** DNS handled by the core's DNS module. */
    var Context.localDns: Boolean
        get() = prefs(this).getBoolean(KEY_LOCAL_DNS, false)
        set(v) = prefs(this).edit { putBoolean(KEY_LOCAL_DNS, v) }

    /** FakeDNS: answer DNS with fake IPs (faster, may break some apps). */
    var Context.fakeDns: Boolean
        get() = prefs(this).getBoolean(KEY_FAKE_DNS, false)
        set(v) = prefs(this).edit { putBoolean(KEY_FAKE_DNS, v) }

    /** Comma-separated DNS servers for the VPN interface. */
    var Context.vpnDns: String
        get() = prefs(this).getString(KEY_VPN_DNS, DEFAULT_VPN_DNS) ?: DEFAULT_VPN_DNS
        set(v) = prefs(this).edit { putString(KEY_VPN_DNS, v) }

    /** Real-connection latency test URL. */
    var Context.testUrl: String
        get() = prefs(this).getString(KEY_TEST_URL, DEFAULT_TEST_URL) ?: DEFAULT_TEST_URL
        set(v) = prefs(this).edit { putString(KEY_TEST_URL, v) }

    /** Max concurrent latency tests. */
    var Context.concurrentTests: Int
        get() = prefs(this).getInt(KEY_CONCURRENT, 8).coerceIn(1, 32)
        set(v) = prefs(this).edit { putInt(KEY_CONCURRENT, v.coerceIn(1, 32)) }

    /** URL used to detect the egress IP. */
    var Context.ipInfoUrl: String
        get() = prefs(this).getString(KEY_IP_INFO_URL, DEFAULT_IP_INFO_URL) ?: DEFAULT_IP_INFO_URL
        set(v) = prefs(this).edit { putString(KEY_IP_INFO_URL, v) }

    /** Remote DNS (UDP/TCP/HTTPS/QUIC) for proxied traffic. */
    var Context.remoteDns: String
        get() = prefs(this).getString(KEY_REMOTE_DNS, DEFAULT_REMOTE_DNS) ?: DEFAULT_REMOTE_DNS
        set(v) = prefs(this).edit { putString(KEY_REMOTE_DNS, v) }

    /** Direct DNS for bypassed traffic. */
    var Context.directDns: String
        get() = prefs(this).getString(KEY_DIRECT_DNS, DEFAULT_DIRECT_DNS) ?: DEFAULT_DIRECT_DNS
        set(v) = prefs(this).edit { putString(KEY_DIRECT_DNS, v) }

    /** Whether LAN traffic bypasses the VPN. */
    var Context.bypassLan: Boolean
        get() = prefs(this).getBoolean(KEY_BYPASS_LAN, true)
        set(v) = prefs(this).edit { putBoolean(KEY_BYPASS_LAN, v) }

    /** VPN interface address (IPv4). */
    var Context.vpnAddress: String
        get() = prefs(this).getString(KEY_VPN_ADDRESS, "10.8.0.2") ?: "10.8.0.2"
        set(v) = prefs(this).edit { putString(KEY_VPN_ADDRESS, v) }

    /** VPN interface MTU. */
    var Context.vpnMtu: Int
        get() = prefs(this).getInt(KEY_VPN_MTU, 1500).coerceIn(1280, 9000)
        set(v) = prefs(this).edit { putInt(KEY_VPN_MTU, v.coerceIn(1280, 9000)) }

    /** Enable IPv6 on the VPN interface. */
    var Context.ipv6Enabled: Boolean
        get() = prefs(this).getBoolean(KEY_IPV6, true)
        set(v) = prefs(this).edit { putBoolean(KEY_IPV6, v) }
}
