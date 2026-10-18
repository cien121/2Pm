package com.chan.ciancore.core.xray

import com.chan.ciancore.data.Node
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

    /** Full runnable config for [node], including TUN plumbing. */
    fun build(node: Node): String {
        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning"))
        config.put("stats", JSONObject())
        config.put("policy", policyObject())
        config.put(
            "inbounds", JSONArray()
                .put(socksInbound())
                .put(tunInbound())
        )
        config.put(
            "outbounds", JSONArray()
                .put(buildOutbound(node))
                .put(directOutbound())
                .put(blockOutbound())
        )
        config.put(
            "routing", JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", JSONArray())
        )
        config.put(
            "dns", JSONObject()
                .put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8"))
        )
        return config.toString()
    }

    /** The single "proxy" outbound for [node]. */
    fun buildOutbound(node: Node): JSONObject {
        return when (node.protocol.lowercase()) {
            "vless" -> vlessOutbound(node)
            "vmess" -> vmessOutbound(node)
            "trojan" -> trojanOutbound(node)
            "ss", "shadowsocks" -> shadowsocksOutbound(node)
            "hysteria2", "hy2" -> hysteria2Outbound(node)
            "tuic" -> throw IllegalArgumentException("tuic 协议需要 sing-box 核心，当前版本暂不支持")
            else -> throw IllegalArgumentException("Xray 核心不支持协议：${node.protocol}")
        }
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

    // ── per-protocol outbounds ──────────────────────────────────

    private fun vlessOutbound(n: Node): JSONObject {
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
            .put("streamSettings", streamSettings(n))
            .put("mux", JSONObject().put("enabled", false))
    }

    private fun vmessOutbound(n: Node): JSONObject {
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
            .put("streamSettings", streamSettings(n))
            .put("mux", JSONObject().put("enabled", false))
    }

    private fun trojanOutbound(n: Node): JSONObject {
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
            .put("streamSettings", streamSettings(n))
    }

    private fun shadowsocksOutbound(n: Node): JSONObject {
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
            .put("streamSettings", streamSettings(n))
    }

    private fun hysteria2Outbound(n: Node): JSONObject {
        val server = JSONObject()
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
        if (n.bandwidthUp.isNotBlank()) {
            quicParams.put("brutalUp", n.bandwidthUp)
            quicParams.put("congestion", "brutal")
        }
        if (n.bandwidthDown.isNotBlank()) {
            quicParams.put("brutalDown", n.bandwidthDown)
            quicParams.put("congestion", "brutal")
        }
        if (n.portHopping.isNotBlank()) {
            quicParams.put(
                "udpHop", JSONObject()
                    .put("ports", n.portHopping)
                    .put("interval", "30")
            )
        }
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
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "hysteria")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", stream)
    }

    // ── stream settings ─────────────────────────────────────────

    private fun streamSettings(n: Node): JSONObject {
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
                ss.put(
                    "xhttpSettings", JSONObject()
                        .put("path", n.wsPath.ifEmpty { "/" })
                        .put("host", n.wsHost)
                )
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
        return tls
    }
}
