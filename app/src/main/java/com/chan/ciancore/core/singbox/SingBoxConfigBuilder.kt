package com.chan.ciancore.core.singbox

import com.chan.ciancore.data.Node
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds sing-box JSON config from a [Node].
 *
 * Structure:
 * - inbounds: single "tun" inbound (libbox will callback openTun to create it)
 * - outbounds: protocol-specific outbound + direct + block
 * - route: basic rules
 */
object SingBoxConfigBuilder {

    fun build(node: Node, tunAddress: String = "10.8.0.2"): String {
        val config = JSONObject()

        // ── TUN inbound ──
        // libbox calls PlatformInterface.openTun() when this inbound starts.
        // Keep it minimal: App builds the TUN via VpnService.Builder in openTun().
        // The address MUST match what the App assigns via VpnService.Builder.
        val tunInbound = JSONObject().apply {
            put("type", "tun")
            put("tag", "tun-in")
            put("interface_name", "tun0")
            put("mtu", 9000)
            // Must match the address assigned by VpnService.Builder in openTun()
            // System stack needs a prefix with at least 2 addresses; use host address with /30
            put("address", JSONArray().put("$tunAddress/30"))
            put("stack", "system")
            put("auto_route", false)
            put("strict_route", false)
            // sniff moved to route rule action (legacy inbound sniff removed in 1.13)
        }
        config.put("inbounds", JSONArray().put(tunInbound))

        // ── Outbounds ──
        val outbounds = JSONArray()
        outbounds.put(buildOutbound(node))
        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct")
        })
        outbounds.put(JSONObject().apply {
            put("type", "block")
            put("tag", "block")
        })
        config.put("outbounds", outbounds)

        // ── Route ──
        val route = JSONObject().apply {
            val rules = JSONArray()
            // DNS traffic -> direct (avoid loop)
            rules.put(JSONObject().apply {
                put("protocol", "dns")
                put("outbound", "direct")
            })
            // Sniff (migrated from legacy inbound sniff field)
            rules.put(JSONObject().apply {
                put("action", "sniff")
            })
            put("rules", rules)
            put("final", "proxy")
            put("auto_detect_interface", true)
        }
        config.put("route", route)

        // ── DNS (sing-box >= 1.14 new format) ──
        val dns = JSONObject().apply {
            val servers = JSONArray()
            servers.put(JSONObject().apply {
                put("tag", "remote")
                put("type", "https")
                put("server", "1.1.1.1")
                put("server_port", 443)
                put("path", "/dns-query")
                put("detour", "proxy")
            })
            servers.put(JSONObject().apply {
                put("tag", "local")
                put("type", "udp")
                put("server", "223.5.5.5")
                put("server_port", 53)
            })
            put("servers", servers)
            val dnsRules = JSONArray()
            dnsRules.put(JSONObject().apply {
                put("outbound", "any")
                put("server", "local")
            })
            put("rules", dnsRules)
            put("final", "remote")
        }
        config.put("dns", dns)

        return config.toString(2)
    }

    private fun buildOutbound(node: Node): JSONObject {
        return when (node.protocol.lowercase()) {
            "anytls" -> buildAnytls(node)
            "tuic" -> buildTuic(node)
            "wireguard" -> buildWireguard(node)
            "vless" -> buildVless(node)
            "vmess" -> buildVmess(node)
            "trojan" -> buildTrojan(node)
            "ss", "shadowsocks" -> buildShadowsocks(node)
            "hysteria2", "hy2" -> buildHysteria2(node)
            "socks", "socks5" -> buildSocks(node)
            "http" -> buildHttp(node)
            else -> throw IllegalArgumentException("sing-box 不支持的协议: ${node.protocol}")
        }
    }

    private fun buildAnytls(node: Node): JSONObject {
        return JSONObject().apply {
            put("type", "anytls")
            put("tag", "proxy")
            put("server", node.host)
            put("server_port", node.port)
            put("password", node.password)
            // Session pool defaults (avoid zero-value issues)
            put("idle_session_check_interval", "30s")
            put("idle_session_timeout", "30s")
            put("min_idle_session", 0)
            put("tls", JSONObject().apply {
                put("enabled", true)
                put("server_name", node.sni.ifBlank { node.host })
                put("insecure", node.insecure)
                if (node.alpn.isNotBlank()) {
                    put("alpn", JSONArray(node.alpn.split(",").map { it.trim() }))
                }
                if (node.fp.isNotBlank() && node.fp != "none") {
                    put("utls", JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", node.fp)
                    })
                }
            })
        }
    }

    private fun buildTuic(node: Node): JSONObject {
        return JSONObject().apply {
            put("type", "tuic")
            put("tag", "proxy")
            put("server", node.host)
            put("server_port", node.port)
            put("uuid", node.uuid)
            put("password", node.password)
            put("congestion_control", node.congestionControl.ifBlank { "bbr" })
            put("udp_relay_mode", node.udpRelayMode.ifBlank { "native" })
            put("tls", JSONObject().apply {
                put("enabled", true)
                put("server_name", node.sni.ifBlank { node.host })
                put("insecure", node.insecure)
                put("alpn", JSONArray().put(node.alpn.ifBlank { "h3" }))
            })
        }
    }

    private fun buildWireguard(node: Node): JSONObject {
        return JSONObject().apply {
            put("type", "wireguard")
            put("tag", "proxy")
            put("server", node.host)
            put("server_port", node.port)
            put("private_key", node.uuid)  // uuid field stores private key
            put("peer_public_key", node.password)  // password field stores public key
            put("local_address", JSONArray().put("172.19.0.2/32"))
            put("mtu", 1280)
        }
    }

    private fun buildVless(node: Node): JSONObject {
        // Basic VLESS for sing-box (XRAY is preferred for vless, but support it here too)
        return JSONObject().apply {
            put("type", "vless")
            put("tag", "proxy")
            put("server", node.host)
            put("server_port", node.port)
            put("uuid", node.uuid)
            if (node.flow.isNotBlank()) put("flow", node.flow)
            if (node.security == "tls" || node.security == "reality") {
                put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", node.sni.ifBlank { node.host })
                    put("insecure", node.insecure)
                })
            }
            // Transport
            when (node.network.lowercase()) {
                "ws" -> put("transport", JSONObject().apply {
                    put("type", "ws")
                    put("path", node.wsPath.ifBlank { "/" })
                    if (node.wsHost.isNotBlank()) put("headers", JSONObject().put("Host", node.wsHost))
                })
                "grpc" -> put("transport", JSONObject().apply {
                    put("type", "grpc")
                    put("service_name", node.serviceName)
                })
            }
        }
    }

    private fun buildVmess(node: Node): JSONObject {
        return JSONObject().apply {
            put("type", "vmess")
            put("tag", "proxy")
            put("server", node.host)
            put("server_port", node.port)
            put("uuid", node.uuid)
            put("security", node.method.ifBlank { "auto" })
            if (node.security == "tls") {
                put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", node.sni.ifBlank { node.host })
                    put("insecure", node.insecure)
                })
            }
        }
    }

    private fun buildTrojan(node: Node): JSONObject {
        return JSONObject().apply {
            put("type", "trojan")
            put("tag", "proxy")
            put("server", node.host)
            put("server_port", node.port)
            put("password", node.uuid)
            put("tls", JSONObject().apply {
                put("enabled", true)
                put("server_name", node.sni.ifBlank { node.host })
                put("insecure", node.insecure)
            })
        }
    }

    private fun buildShadowsocks(node: Node): JSONObject {
        return JSONObject().apply {
            put("type", "shadowsocks")
            put("tag", "proxy")
            put("server", node.host)
            put("server_port", node.port)
            put("method", node.method.ifBlank { "aes-128-gcm" })
            put("password", node.uuid)
        }
    }

    private fun buildHysteria2(node: Node): JSONObject {
        return JSONObject().apply {
            put("type", "hysteria2")
            put("tag", "proxy")
            put("server", node.host)
            put("server_port", node.port)
            put("password", node.uuid)
            put("tls", JSONObject().apply {
                put("enabled", true)
                put("server_name", node.sni.ifBlank { node.host })
                put("insecure", node.insecure)
                put("alpn", JSONArray().put("h3"))
            })
            if (node.obfsPassword.isNotBlank()) {
                put("obfs", JSONObject().apply {
                    put("type", "salamander")
                    put("password", node.obfsPassword)
                })
            }
        }
    }

    private fun buildSocks(node: Node): JSONObject {
        return JSONObject().apply {
            put("type", "socks")
            put("tag", "proxy")
            put("server", node.host)
            put("server_port", node.port)
            if (node.uuid.isNotBlank()) {
                put("username", node.uuid)
                put("password", node.password)
            }
        }
    }

    private fun buildHttp(node: Node): JSONObject {
        return JSONObject().apply {
            put("type", "http")
            put("tag", "proxy")
            put("server", node.host)
            put("server_port", node.port)
            if (node.uuid.isNotBlank()) {
                put("username", node.uuid)
                put("password", node.password)
            }
            if (node.security == "tls") {
                put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", node.sni.ifBlank { node.host })
                    put("insecure", node.insecure)
                })
            }
        }
    }
}
