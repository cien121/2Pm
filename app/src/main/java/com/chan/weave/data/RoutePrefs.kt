package com.chan.weave.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Routing rules, modeled on v2rayNG's routing settings.
 *
 * Each rule = remarks + match criteria (domains / ips / port / network)
 * + outboundTag (proxy / direct / block) + enable switch.
 *
 * DNS anti-leak: the TUN DNS server is reached through the tunnel via
 * normal routing (default outbound = proxy); no rule ever sends DNS
 * direct, so queries cannot leak outside the tunnel.
 */
object RoutePrefs {

    private const val FILE = "routing"
    private const val KEY_RULES = "rules_v2"
    private const val KEY_DOMAIN_STRATEGY = "domain_strategy"
    private const val KEY_SEEDED = "seeded_v2"

    /** Xray domainStrategy. */
    enum class DomainStrategy(val value: String) {
        AS_IS("AsIs"),
        IP_IF_NON_MATCH("IPIfNonMatch"),
        IP_ON_DEMAND("IPOnDemand")
    }

    data class RoutingRule(
        val id: String = UUID.randomUUID().toString(),
        val enabled: Boolean = true,
        val locked: Boolean = false,
        val remarks: String = "",
        val domains: List<String> = emptyList(),
        val ips: List<String> = emptyList(),
        val port: String = "",
        val network: String = "", // "tcp" / "udp" / "" (both)
        val outboundTag: String = "proxy" // "proxy" / "direct" / "block"
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("enabled", enabled)
            .put("locked", locked)
            .put("remarks", remarks)
            .put("domain", JSONArray(domains))
            .put("ip", JSONArray(ips))
            .put("port", port)
            .put("network", network)
            .put("outboundTag", outboundTag)

        /** One-line match summary, e.g. "[geosite:google]" or "443/udp". */
        fun matchSummary(): String {
            val parts = mutableListOf<String>()
            if (domains.isNotEmpty()) {
                val d = domains.joinToString(", ")
                parts.add(if (d.length > 42) "[${d.take(42)}…]" else "[$d]")
            }
            if (ips.isNotEmpty()) {
                val i = ips.joinToString(", ")
                parts.add(if (i.length > 42) "[${i.take(42)}…]" else "[$i]")
            }
            if (port.isNotBlank()) {
                parts.add(if (network.isNotBlank()) "$port/$network" else port)
            }
            return parts.joinToString(" ").ifBlank { "全部流量" }
        }

        fun outboundName(): String = when (outboundTag) {
            "direct" -> "direct"
            "block" -> "block"
            else -> "proxy"
        }

        companion object {
            fun fromJson(o: JSONObject): RoutingRule {
                fun strList(key: String): List<String> {
                    val arr = o.optJSONArray(key) ?: return emptyList()
                    return List(arr.length()) { i -> arr.optString(i) }
                        .filter { it.isNotBlank() }
                }
                return RoutingRule(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    enabled = o.optBoolean("enabled", true),
                    locked = o.optBoolean("locked", false),
                    remarks = o.optString("remarks"),
                    domains = strList("domain"),
                    ips = strList("ip"),
                    port = o.optString("port"),
                    network = o.optString("network"),
                    outboundTag = o.optString("outboundTag", "proxy")
                        .ifBlank { "proxy" }
                )
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getDomainStrategy(context: Context): DomainStrategy =
        runCatching {
            DomainStrategy.valueOf(
                prefs(context).getString(KEY_DOMAIN_STRATEGY, DomainStrategy.AS_IS.name)
                    ?: DomainStrategy.AS_IS.name
            )
        }.getOrDefault(DomainStrategy.AS_IS)

    fun setDomainStrategy(context: Context, s: DomainStrategy) {
        prefs(context).edit { putString(KEY_DOMAIN_STRATEGY, s.name) }
    }

    fun getRules(context: Context): List<RoutingRule> {
        ensureSeeded(context)
        val raw = prefs(context).getString(KEY_RULES, "[]").orEmpty()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> RoutingRule.fromJson(arr.getJSONObject(i)) }
        } catch (_: Throwable) {
            defaultRules()
        }
    }

    fun setRules(context: Context, rules: List<RoutingRule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        prefs(context).edit {
            putString(KEY_RULES, arr.toString())
            putBoolean(KEY_SEEDED, true)
        }
    }

    private fun ensureSeeded(context: Context) {
        if (prefs(context).getBoolean(KEY_SEEDED, false)) return
        setRules(context, defaultRules())
    }

    /** v2rayNG's built-in rule set. */
    fun defaultRules(): List<RoutingRule> = listOf(
        RoutingRule(
            remarks = "阻断udp443",
            network = "udp",
            port = "443",
            outboundTag = "block"
        ),
        RoutingRule(
            remarks = "代理Google",
            domains = listOf("geosite:google"),
            outboundTag = "proxy"
        ),
        RoutingRule(
            remarks = "绕过局域网IP",
            ips = listOf("geoip:private"),
            outboundTag = "direct"
        ),
        RoutingRule(
            remarks = "绕过局域网域名",
            domains = listOf("geosite:private"),
            outboundTag = "direct"
        ),
        RoutingRule(
            remarks = "绕过中国公共DNSIP",
            ips = listOf(
                "223.5.5.5", "223.6.6.6", "2400:3200::1", "2400:3200:baba::1",
                "119.29.29.29", "1.12.12.12", "120.53.53.53", "2402:4e00::",
                "2402:4e00:1::", "180.76.76.76", "2400:da00::6666",
                "114.114.114.114", "114.114.115.115", "114.114.114.119",
                "114.114.115.119", "114.114.114.110", "114.114.115.110",
                "180.184.1.1", "180.184.2.2", "101.226.4.6", "218.30.118.6",
                "123.125.81.6", "140.207.198.6", "1.2.4.8", "210.2.4.8",
                "52.80.66.66", "117.50.22.22", "2400:7fc0:849e:200::4",
                "2404:c2c0:85d8:901::4", "117.50.10.10", "52.80.52.52",
                "2400:7fc0:849e:200::8", "2404:c2c0:85d8:901::8",
                "117.50.60.30", "52.80.60.30"
            ),
            outboundTag = "direct"
        ),
        RoutingRule(
            remarks = "绕过中国公共DNS域名",
            domains = listOf(
                "domain:alidns.com", "domain:doh.pub", "domain:dot.pub",
                "domain:360.cn", "domain:onedns.net"
            ),
            outboundTag = "direct"
        ),
        RoutingRule(
            remarks = "绕过中国IP",
            ips = listOf("geoip:cn"),
            outboundTag = "direct"
        ),
        RoutingRule(
            remarks = "绕过中国域名",
            domains = listOf("geosite:cn"),
            outboundTag = "direct"
        )
    )

    // ── legacy (v0.6.x) migration ────────────────────────────────
    // Old model: Mode { GLOBAL, BYPASS_CN, CUSTOM } + CustomRule(kind,value,action).
    // Kept only to migrate once; new code must use RoutingRule.

    @Deprecated("migrated to RoutingRule")
    enum class Mode(val displayName: String) {
        GLOBAL("全局代理"),
        BYPASS_CN("绕过大陆"),
        CUSTOM("自定义规则")
    }

    @Deprecated("migrated to RoutingRule")
    data class CustomRule(val kind: String, val value: String, val action: String)
}
