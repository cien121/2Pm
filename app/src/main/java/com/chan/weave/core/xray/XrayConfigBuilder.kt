package com.chan.weave.core.xray

import com.chan.weave.data.Node
import com.chan.weave.data.RoutePrefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds engine-native Xray JSON configs from a [Node].
 *
 * The template mirrors the TUN-mode config used by v2rayNG:
 * socks + tun inbounds, proxy/direct/block outbounds.
 * The TUN file descriptor is handed to the core via
 * `CoreController.startLoop(config, tunFd)` (libv2ray reads it
 * from the `xray.tun.fd` env key into the `tun` inbound).
 *
 * Outbound construction follows v2rayNG's CoreOutboundBuilder logic
 * (per-protocol settings, streamSettings, TLS/REALITY).
 */
object XrayConfigBuilder {

    /** All tunable options for a generated config. */
    data class BuildOptions(
        val routingRules: List<RoutePrefs.RoutingRule> = emptyList(),
        val domainStrategy: String = "AsIs",
        val remoteDns: String = "",
        val directDns: String = "",
        val fakeDns: Boolean = false,
        /**
         * Chain proxy: ordered list of nodes the main outbound dials through
         * in sequence (Xray `streamSettings.sockopt.dialerProxy`).
         * Empty = disabled. Example: [A, B] → main → A → B → Internet.
         */
        val chainNodes: List<Node> = emptyList(),
        /** @deprecated Use chainNodes instead. */
        val chainNode: Node? = null,
        /**
         * When true (proxychain virtual nodes), a chain member whose
         * outbound fails to build aborts the whole config with an error
         * instead of being silently dropped (which would degrade the
         * chain into a direct connection to the exit node).
         */
        val strictChain: Boolean = false,
        // Mux (multiplexing)
        val muxEnabled: Boolean = false,
        val muxConcurrency: Int = 8,
        // Fragment (TLS hello fragmentation for DPI evasion)
        val fragmentEnabled: Boolean = false,
        val fragmentPackets: String = "tlshello",
        val fragmentLength: String = "50-100",
        val fragmentInterval: String = "10-20",
        // Local DNS: route port 53 to Xray's DNS module (split horizon).
        // Requires loop-protection for the DNS servers themselves.
        val localDnsEnabled: Boolean = false
    )

    /** Full runnable config for [node], including TUN plumbing. */
    fun build(node: Node): String = build(node, BuildOptions())

    /**
     * Full runnable config for [node].
     * @param options routing + DNS tuning.
     */
    fun build(node: Node, options: BuildOptions): String {
        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning"))
        config.put("stats", JSONObject())
        config.put("policy", policyObject())
        config.put(
            "inbounds", JSONArray()
                .put(socksInbound())
                .put(tunInbound())
        )
        val mainOutbound = buildOutbound(node, "proxy")
        val outbounds = JSONArray().put(mainOutbound)
        // Chain proxy: main outbound dials through the chain in sequence.
        // A broken chain node must never break the main connection.
        // NOTE: Xray removed outbound-level "proxySettings"; use
        // "streamSettings.sockopt.dialerProxy" instead.
        // For chain [C1, C2]: main → C1 → C2 → Internet (egress = C2's IP).
        val chainList = options.chainNodes.ifEmpty {
            // Backwards compat: single chainNode
            val single = options.chainNode
            if (single != null && single.id != 0L && single.id != node.id) listOf(single) else emptyList()
        }.filter { it.id != 0L && it.id != node.id }
        if (chainList.isNotEmpty()) {
            // Build chain outbounds: chain0, chain1, ...
            val chainTags = mutableListOf<String>()
            chainList.forEachIndexed { index, chainNode ->
                val tag = "chain$index"
                val outbound = runCatching { buildOutbound(chainNode, tag) }.getOrNull()
                if (outbound != null) {
                    outbounds.put(outbound)
                    chainTags.add(tag)
                } else if (options.strictChain) {
                    throw IllegalStateException("链式成员配置生成失败：${chainNode.name}")
                }
            }
            // Link them: main → chain0 → chain1 → ... (last has no dialerProxy)
            if (chainTags.isNotEmpty()) {
                // Main dials through first chain node
                setDialerProxy(mainOutbound, chainTags[0])
                // Each chain node dials through the next
                for (i in 0 until chainTags.size - 1) {
                    val outbound = outbounds.let { arr ->
                        // Find by tag
                        (0 until arr.length()).map { arr.getJSONObject(it) }
                            .firstOrNull { it.optString("tag") == chainTags[i] }
                    }
                    if (outbound != null) {
                        setDialerProxy(outbound, chainTags[i + 1])
                    }
                }
            }
        }
        outbounds.put(directOutbound()).put(blockOutbound())
        // Local DNS: define the "dns" outbound so the port-53 routing rule
        // has a valid target. Xray's "dns" outbound type forwards queries
        // to the core DNS module.
        if (options.localDnsEnabled) {
            outbounds.put(
                JSONObject()
                    .put("tag", "dns")
                    .put("protocol", "dns")
            )
        }
        config.put("outbounds", outbounds)
        config.put("routing", routingObject(options))
        config.put("dns", dnsObject(options))
        if (options.fakeDns) {
            config.put(
                "fakedns", JSONArray().put(
                    JSONObject()
                        .put("ipPool", "198.18.0.0/15")
                        .put("poolSize", 10000)
                )
            )
        }
        return config.toString()
    }

    /**
     * Minimal config for latency probing: proxy + direct outbounds,
     * no TUN/socks inbounds. Used with measureOutboundDelay.
     */
    fun buildOutboundTest(node: Node): String {
        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning"))
        config.put(
            "outbounds", JSONArray()
                .put(buildOutbound(node))
                .put(directOutbound())
        )
        return config.toString()
    }

    /**
     * Test config for a proxychain: [membersInFlowOrder] = [M1, ..., Mn],
     * Mn is the exit (main outbound), preceding members are fronts in reverse.
     */
    fun buildChainTest(membersInFlowOrder: List<Node>): String {
        if (membersInFlowOrder.isEmpty()) throw IllegalArgumentException("empty chain")
        val exit = membersInFlowOrder.last()
        val frontsReversed = membersInFlowOrder.dropLast(1).reversed()
        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning"))
        val outbounds = JSONArray()
        val mainOutbound = buildOutbound(exit, "proxy")
        outbounds.put(mainOutbound)
        val chainTags = mutableListOf<String>()
        frontsReversed.forEachIndexed { index, front ->
            val tag = "chain$index"
            outbounds.put(buildOutbound(front, tag))
            chainTags.add(tag)
        }
        if (chainTags.isNotEmpty()) {
            setDialerProxy(mainOutbound, chainTags[0])
            for (i in 0 until chainTags.size - 1) {
                val ob = (0 until outbounds.length())
                    .map { outbounds.getJSONObject(it) }
                    .firstOrNull { it.optString("tag") == chainTags[i] }
                if (ob != null) setDialerProxy(ob, chainTags[i + 1])
            }
        }
        outbounds.put(directOutbound())
        config.put("outbounds", outbounds)
        return config.toString()
    }

    /** The single "proxy" outbound for [node]. */
    fun buildOutbound(node: Node, tag: String = "proxy", opts: BuildOptions = BuildOptions()): JSONObject {
        val ob = when (node.protocol.lowercase()) {
            "vless" -> vlessOutbound(node, opts)
            "vmess" -> vmessOutbound(node, opts)
            "trojan" -> trojanOutbound(node, opts)
            "ss", "shadowsocks" -> shadowsocksOutbound(node, opts)
            "socks", "socks5" -> socksOutbound(node, opts)
            "http" -> httpOutbound(node, opts)
            "hysteria2", "hy2" -> hysteria2Outbound(node, opts)
            "mist" -> mistOutbound(node)
            "tuic" -> throw IllegalArgumentException("tuic 协议需要 sing-box 核心，当前版本暂不支持")
            else -> throw IllegalArgumentException("Xray 核心不支持协议：${node.protocol}")
        }
        ob.put("tag", tag)
        // Mux (multiplexing): reuse one connection for multiple streams.
        // Skip for hysteria2 (QUIC-based, mux not applicable).
        if (opts.muxEnabled && !node.protocol.lowercase().startsWith("hy")) {
            ob.put(
                "mux", JSONObject()
                    .put("enabled", true)
                    .put("concurrency", opts.muxConcurrency)
            )
        }
        return ob
    }

    // ── template pieces ─────────────────────────────────────────

    private fun policyObject(): JSONObject {
        val level8 = JSONObject()
            .put("handshake", 4)
            .put("connIdle", 300)
            .put("uplinkOnly", 1)
            .put("downlinkOnly", 1)
        return JSONObject()
            .put("levels", JSONObject().put("8", level8))
            .put(
                "system", JSONObject()
                    .put("statsOutboundUplink", true)
                    .put("statsOutboundDownlink", true)
            )
    }

    private fun sniffingObject(): JSONObject = JSONObject()
        .put("enabled", true)
        .put("destOverride", JSONArray().put("http").put("tls").put("quic"))

    private fun socksInbound(): JSONObject = JSONObject()
        .put("tag", "socks")
        .put("listen", "127.0.0.1")
        .put("port", 10808)
        .put("protocol", "socks")
        .put(
            "settings", JSONObject()
                .put("auth", "noauth")
                .put("udp", true)
                .put("userLevel", 8)
        )
        .put("sniffing", sniffingObject())

    private fun tunInbound(): JSONObject = JSONObject()
        .put("tag", "tun")
        .put("protocol", "tun")
        .put(
            "settings", JSONObject()
                .put("name", "xray0")
                .put("MTU", 1500)
                .put("userLevel", 8)
        )
        .put("sniffing", sniffingObject())

    private fun directOutbound(): JSONObject = JSONObject()
        .put("tag", "direct")
        .put("protocol", "freedom")
        .put(
            "streamSettings", JSONObject()
                .put(
                    "sockopt", JSONObject()
                        .put("domainStrategy", "UseIP")
                )
        )

    private fun blockOutbound(): JSONObject = JSONObject()
        .put("tag", "block")
        .put("protocol", "blackhole")
        .put(
            "settings", JSONObject()
                .put("response", JSONObject().put("type", "http"))
        )

    /**
     * Routing section.
     *
     * Rule order matters (first match wins):
     * 1. User rules (enabled only), in list order.
     *
     * DNS goes to the TUN DNS server (1.1.1.1) through the tunnel via
     * normal routing (default outbound = proxy), exactly like v2rayNG.
     * No rule sends DNS direct, so it cannot leak outside the tunnel.
     */
    private fun routingObject(options: BuildOptions): JSONObject {
        val rules = JSONArray()
        // Local DNS: when enabled, DNS queries go to Xray's DNS module
        // (split horizon) instead of straight through the tunnel.
        // Loop protection: the DNS servers themselves must NOT match the
        // port-53 rule, or Xray's upstream queries would loop forever.
        if (options.localDnsEnabled) {
            // Only literal IPs/CIDRs may go into an "ip" rule — the user
            // may have configured DoH/DoT URLs (e.g. https://.../dns-query),
            // which Xray rejects with "illegal ip rule".
            val ipLike = Regex("^[0-9a-fA-F.:/]+$")
            val dnsServers = listOfNotNull(
                options.remoteDns.ifBlank { null }?.split(",")?.map { it.trim() },
                options.directDns.ifBlank { null }?.split(",")?.map { it.trim() },
                listOf("1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4", "223.5.5.5", "223.6.6.6")
            ).flatten().distinct()
                .filter { it.isNotBlank() && ipLike.matches(it) }
            if (dnsServers.isNotEmpty()) {
                rules.put(
                    JSONObject()
                        .put("type", "field")
                        .put("ip", JSONArray(dnsServers))
                        .put("outboundTag", "proxy")
                )
            }
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("port", 53)
                    .put("network", "udp")
                    .put("outboundTag", "dns")
            )
        }
        // User rules.
        for (rule in options.routingRules) {
            if (!rule.enabled) continue
            val field = JSONObject().put("type", "field")
            if (rule.domains.isNotEmpty()) {
                field.put("domain", JSONArray(rule.domains))
            }
            if (rule.ips.isNotEmpty()) {
                field.put("ip", JSONArray(rule.ips))
            }
            if (rule.port.isNotBlank()) {
                // Xray accepts "443" or "443,8443" or "1000-2000".
                field.put("port", rule.port)
            }
            if (rule.network.isNotBlank()) {
                field.put("network", rule.network)
            }
            val tag = when (rule.outboundTag) {
                "direct" -> "direct"
                "block" -> "block"
                else -> "proxy"
            }
            field.put("outboundTag", tag)
            // Skip empty rules (no match criteria at all).
            if (!field.has("domain") && !field.has("ip") &&
                !field.has("port")
            ) continue
            rules.put(field)
        }
        return JSONObject()
            .put("domainStrategy", options.domainStrategy.ifBlank { "AsIs" })
            .put("rules", rules)
    }

    /**
     * DNS section: split-horizon remote/direct DNS, optional FakeDNS.
     */
    private fun dnsObject(options: BuildOptions): JSONObject {
        val servers = JSONArray()
        if (options.fakeDns) {
            servers.put("fakedns")
        }
        val remote = options.remoteDns.ifBlank { "1.1.1.1" }
        val direct = options.directDns.ifBlank { "223.5.5.5" }
        // Mainland domains resolve via direct DNS (no poisoning).
        // Foreign domains go through the tunnel via the remote resolver.
        servers.put(
            JSONObject()
                .put("address", direct)
                .put("domains", JSONArray().put("geosite:cn"))
        )
        servers.put(remote)
        // Final fallback.
        if (remote != "1.1.1.1") servers.put("1.1.1.1")
        return JSONObject().put("servers", servers)
    }

    // ── per-protocol outbounds ──────────────────────────────────

    private fun vlessOutbound(n: Node, opts: BuildOptions = BuildOptions()): JSONObject {
        val user = JSONObject()
            .put("id", n.uuid)
            .put("encryption", n.method.ifEmpty { "none" })
            .put("level", 8)
        // XTLS flow only makes sense with TLS/Reality.
        if (n.flow.isNotBlank() && (n.security == "tls" || n.security == "reality")) {
            user.put("flow", n.flow)
        }
        val vnext = JSONObject()
            .put("address", n.host)
            .put("port", n.port)
            .put("users", JSONArray().put(user))
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", streamSettings(n, opts))
            .put("mux", JSONObject().put("enabled", false))
    }

    private fun vmessOutbound(n: Node, opts: BuildOptions = BuildOptions()): JSONObject {
        val user = JSONObject()
            .put("id", n.uuid)
            .put("alterId", 0)
            .put("security", n.method.ifEmpty { "auto" })
            .put("level", 8)
        val vnext = JSONObject()
            .put("address", n.host)
            .put("port", n.port)
            .put("users", JSONArray().put(user))
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", streamSettings(n, opts))
            .put("mux", JSONObject().put("enabled", false))
    }

    private fun trojanOutbound(n: Node, opts: BuildOptions = BuildOptions()): JSONObject {
        val server = JSONObject()
            .put("address", n.host)
            .put("port", n.port)
            .put("password", n.uuid)
            .put("level", 8)
        if (n.flow.isNotBlank() && (n.security == "tls" || n.security == "reality")) {
            server.put("flow", n.flow)
        }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", streamSettings(n, opts))
    }

    private fun shadowsocksOutbound(n: Node, opts: BuildOptions = BuildOptions()): JSONObject {
        val server = JSONObject()
            .put("address", n.host)
            .put("port", n.port)
            .put("method", n.method)
            .put("password", n.uuid)
            .put("level", 8)
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", streamSettings(n, opts))
    }

    private fun socksOutbound(n: Node, opts: BuildOptions = BuildOptions()): JSONObject {
        val server = JSONObject()
            .put("address", n.host)
            .put("port", n.port)
        if (n.uuid.isNotBlank()) {
            server.put(
                "users", JSONArray().put(
                    JSONObject()
                        .put("user", n.uuid)
                        .put("pass", n.password)
                )
            )
        }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "socks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
    }

    private fun httpOutbound(n: Node, opts: BuildOptions = BuildOptions()): JSONObject {
        val server = JSONObject()
            .put("address", n.host)
            .put("port", n.port)
        if (n.uuid.isNotBlank()) {
            server.put(
                "users", JSONArray().put(
                    JSONObject()
                        .put("user", n.uuid)
                        .put("pass", n.password)
                )
            )
        }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "http")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", streamSettings(n, opts))
    }

    private fun hysteria2Outbound(n: Node, opts: BuildOptions = BuildOptions()): JSONObject {
        // Xray's hysteria outbound settings are FLAT (HysteriaClientConfig):
        // {"version": 2, "address": ..., "port": ...} — NOT servers[].
        val settings = JSONObject()
            .put("version", 2)
            .put("address", n.host)
            .put("port", n.port)
        val stream = JSONObject()
            .put("network", "hysteria")
            .put("security", "tls")
            .put("tlsSettings", tlsSettings(n))
            .put(
                "hysteriaSettings", JSONObject()
                    .put("version", 2)
                    .put("auth", n.uuid)
            )
        val quicParams = JSONObject()
        val upBw = normalizeBandwidth(n.bandwidthUp)
        if (upBw.isNotBlank()) {
            quicParams.put("brutalUp", upBw)
            quicParams.put("congestion", "brutal")
        }
        val downBw = normalizeBandwidth(n.bandwidthDown)
        if (downBw.isNotBlank()) {
            quicParams.put("brutalDown", downBw)
            quicParams.put("congestion", "brutal")
        }
        if (n.portHopping.isNotBlank()) {
            quicParams.put(
                "udpHop", JSONObject()
                    .put("ports", n.portHopping)
                    .put("interval", n.hopInterval.ifBlank { "30" })
            )
        }
        // Raw FinalMask JSON overrides the structured builder output.
        val rawFinalMask = n.finalMaskJson.trim().takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (rawFinalMask != null) {
            stream.put("finalmask", rawFinalMask)
        } else {
            val finalmask = JSONObject().put("quicParams", quicParams)
            if (n.obfsPassword.isNotBlank()) {
                finalmask.put(
                    "udp", JSONArray().put(
                        JSONObject()
                            .put("type", "salamander")
                            .put(
                                "settings", JSONObject()
                                    .put("password", n.obfsPassword)
                            )
                    )
                )
            }
            stream.put("finalmask", finalmask)
        }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "hysteria")
            .put("settings", settings)
            .put("streamSettings", stream)
    }

    /**
     * Mist 自研协议原生出站（proxy/mist，Xray mist 分支）。
     * 严格按 XRAY_INTEGRATION.md（~/workspace/xray-core/XRAY_INTEGRATION.md）生成：
     * - Node.uuid = clientId（hex 32 字符），Node.password = authKey（标准 base64）
     * - 传输固定：network tcp / security tls / fingerprint hellochrome_155 /
     *   alpn h2+http/1.1 / allowInsecure=false（证书链必须校验）
     * - SNI：Node.sni，默认 = host（雾域名）
     */
    private fun mistOutbound(n: Node): JSONObject {
        require(n.uuid.trim().matches(Regex("[0-9a-fA-F]{32}"))) {
            "mist 节点 Client ID 无效（应为 32 个 hex 字符）"
        }
        require(n.password.isNotBlank()) { "mist 节点缺少 Auth Key" }
        val sni = n.sni.ifBlank { n.host }
        require(sni.isNotBlank()) { "mist 节点缺少服务器地址" }
        val settings = JSONObject()
            .put("address", n.host)
            .put("port", n.port)
            .put("clientId", n.uuid.trim().lowercase())
            .put("authKey", n.password.trim())
            .put("heartbeatInterval", 30)
            .put("shapingHeavyPackets", 32)
        val tlsSettings = JSONObject()
            .put("serverName", sni)
            .put("fingerprint", "hellochrome_155")
            .put("alpn", JSONArray().put("h2").put("http/1.1"))
            .put("allowInsecure", false)
        if (n.echConfigList.isNotBlank()) {
            tlsSettings.put("echConfigList", n.echConfigList.trim())
        }
        val stream = JSONObject()
            .put("network", "tcp")
            .put("security", "tls")
            .put("tlsSettings", tlsSettings)
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "mist")
            .put("settings", settings)
            .put("streamSettings", stream)
    }

    /**
     * Normalize a user-entered bandwidth string for Xray.
     * Xray parses "50 mbps" / "50mbps" / "50m" etc.; a bare number
     * would mean bytes/sec and trip the 65536 Bps minimum, so treat
     * bare numbers as mbps (matches v2rayNG's upmbps/downmbps).
     */
    private fun normalizeBandwidth(raw: String): String {
        val t = raw.trim().lowercase()
        if (t.isEmpty()) return ""
        if (t.matches(Regex("[0-9.]+"))) return "$t mbps"
        return t
    }

    // ── stream settings ─────────────────────────────────────────

    private fun streamSettings(n: Node, opts: BuildOptions = BuildOptions()): JSONObject {
        val ss = JSONObject()
        val network = n.network.ifEmpty { "tcp" }
        ss.put("network", network)
        when (network) {
            "tcp" -> {
                val header = JSONObject().put("type", n.headerType.ifEmpty { "none" })
                if (n.headerType == "http") {
                    header.put(
                        "request", JSONObject()
                            .put("path", JSONArray(n.wsPath.split(",").map { it.trim() }))
                            .put(
                                "headers", JSONObject()
                                    .put("Host", JSONArray(n.wsHost.split(",").map { it.trim() }))
                            )
                    )
                }
                ss.put("tcpSettings", JSONObject().put("header", header))
            }
            "ws" -> {
                ss.put(
                    "wsSettings", JSONObject()
                        .put("path", n.wsPath.ifEmpty { "/" })
                        .put("host", n.wsHost)
                )
            }
            "httpupgrade" -> {
                ss.put(
                    "httpupgradeSettings", JSONObject()
                        .put("path", n.wsPath.ifEmpty { "/" })
                        .put("host", n.wsHost)
                )
            }
            "xhttp" -> {
                val xhttp = JSONObject()
                    .put("path", n.wsPath.ifEmpty { "/" })
                    .put("host", n.wsHost)
                if (n.xhttpMode.isNotBlank()) xhttp.put("mode", n.xhttpMode)
                if (n.xhttpExtra.isNotBlank()) {
                    runCatching { xhttp.put("extra", JSONObject(n.xhttpExtra)) }
                }
                ss.put("xhttpSettings", xhttp)
            }
            "h2", "http" -> {
                ss.put("network", "h2")
                ss.put(
                    "httpSettings", JSONObject()
                        .put("path", n.wsPath.ifEmpty { "/" })
                        .put("host", JSONArray(n.wsHost.split(",").map { it.trim() }))
                )
            }
            "grpc" -> {
                ss.put(
                    "grpcSettings", JSONObject()
                        .put("serviceName", n.serviceName)
                        .put("authority", n.authority)
                        .put("multiMode", n.grpcMode == "multi")
                        .put("idle_timeout", 60)
                        .put("health_check_timeout", 20)
                )
            }
            "kcp" -> {
                val kcp = JSONObject()
                    .put("mtu", 1350)
                    .put("tti", 50)
                    .put("header", JSONObject().put("type", n.headerType.ifEmpty { "none" }))
                if (n.seed.isNotBlank()) kcp.put("seed", n.seed)
                ss.put("kcpSettings", kcp)
            }
        }
        if (n.security == "tls" || n.security == "reality") {
            ss.put("security", n.security)
            val key = if (n.security == "tls") "tlsSettings" else "realitySettings"
            ss.put(key, tlsSettings(n))
        }
        // Raw FinalMask JSON (advanced override).
        n.finalMaskJson.trim().takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { ss.put("finalmask", JSONObject(raw)) }
        }
        // Fragment: split TLS handshakes to evade DPI/SNI detection.
        // Only applies to TCP-based transports with TLS.
        if (opts.fragmentEnabled && (n.security == "tls" || n.security == "reality")) {
            val network = n.network.ifEmpty { "tcp" }
            if (network == "tcp" || network == "ws" || network == "httpupgrade" || network == "xhttp") {
                ss.put(
                    "sockopt", JSONObject()
                        .put(
                            "fragment", JSONObject()
                                .put("packets", opts.fragmentPackets.ifBlank { "tlshello" })
                                .put("length", opts.fragmentLength.ifBlank { "50-100" })
                                .put("interval", opts.fragmentInterval.ifBlank { "10-20" })
                        )
                )
            }
        }
        return ss
    }

    private fun tlsSettings(n: Node): JSONObject {
        val tls = JSONObject()
        tls.put("allowInsecure", n.insecure)
        // SNI: explicit sni wins, else the host if it's a domain.
        val sni = n.sni.ifBlank {
            if (n.host.any { it.isLetter() }) n.host else ""
        }
        if (sni.isNotBlank()) tls.put("serverName", sni)
        if (n.fp.isNotBlank()) tls.put("fingerprint", n.fp)
        if (n.alpn.isNotBlank()) {
            tls.put("alpn", JSONArray(n.alpn.split(",").map { it.trim() }))
        }
        if (n.pbk.isNotBlank()) tls.put("publicKey", n.pbk)
        if (n.sid.isNotBlank()) tls.put("shortId", n.sid)
        if (n.spx.isNotBlank()) tls.put("spiderX", n.spx)
        if (n.echConfigList.isNotBlank()) tls.put("echConfigList", n.echConfigList)
        if (n.peerCertSha256.isNotBlank()) tls.put("pinnedPeerCertSha256", n.peerCertSha256)
        if (n.verifyPeerCertByName.isNotBlank()) {
            tls.put("verifyPeerCertByName", n.verifyPeerCertByName)
        }
        return tls
    }

    /** Sets streamSettings.sockopt.dialerProxy on an outbound (Xray chain). */
    private fun setDialerProxy(outbound: JSONObject, proxyTag: String) {
        val streamSettings = outbound.optJSONObject("streamSettings")
            ?: JSONObject().also { outbound.put("streamSettings", it) }
        val sockopt = streamSettings.optJSONObject("sockopt")
            ?: JSONObject().also { streamSettings.put("sockopt", it) }
        sockopt.put("dialerProxy", proxyTag)
    }
}
