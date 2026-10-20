package com.chan.weave.data

import android.net.Uri
import android.util.Base64
import org.json.JSONObject

/**
 * Generates share links from [Node] objects (reverse of [NodeLinkParser]).
 * Used for "export to clipboard" — copy all nodes as importable links.
 */
object NodeLinkGenerator {

    /** Generate a share link for [node], or null if the protocol is unsupported. */
    fun generate(node: Node): String? = runCatching {
        when (node.protocol.lowercase()) {
            "vless" -> genVless(node)
            "vmess" -> genVmess(node)
            "trojan" -> genTrojan(node)
            "ss", "shadowsocks" -> genSs(node)
            "hysteria2", "hy2" -> genHysteria2(node)
            "socks", "socks5" -> genSocks(node)
            "http" -> genHttp(node)
            "mist" -> genMist(node)
            else -> null
        }
    }.getOrNull()

    /** Export all [nodes] as newline-separated links. Nodes without a generatable link are skipped. */
    fun exportAll(nodes: List<Node>): String =
        nodes.mapNotNull { generate(it) }.joinToString("\n")

    private fun enc(s: String): String = Uri.encode(s)

    private fun transportQuery(n: Node): String {
        val q = mutableListOf<String>()
        // security
        if (n.security.isNotBlank()) {
            q.add("security=${enc(n.security)}")
            if (n.security == "reality") {
                if (n.pbk.isNotBlank()) q.add("pbk=${enc(n.pbk)}")
                if (n.sid.isNotBlank()) q.add("sid=${enc(n.sid)}")
                if (n.spx.isNotBlank()) q.add("spx=${enc(n.spx)}")
            }
        } else {
            q.add("security=none")
        }
        if (n.sni.isNotBlank()) q.add("sni=${enc(n.sni)}")
        if (n.alpn.isNotBlank()) q.add("alpn=${enc(n.alpn)}")
        if (n.fp.isNotBlank()) q.add("fp=${enc(n.fp)}")
        if (n.flow.isNotBlank()) q.add("flow=${enc(n.flow)}")
        // transport
        val net = n.network.ifBlank { "tcp" }
        q.add("type=${enc(net)}")
        when (net) {
            "ws", "httpupgrade" -> {
                if (n.wsPath.isNotBlank() && n.wsPath != "/") q.add("path=${enc(n.wsPath)}")
                if (n.wsHost.isNotBlank()) q.add("host=${enc(n.wsHost)}")
            }
            "xhttp" -> {
                if (n.wsPath.isNotBlank() && n.wsPath != "/") q.add("path=${enc(n.wsPath)}")
                if (n.wsHost.isNotBlank()) q.add("host=${enc(n.wsHost)}")
                if (n.xhttpMode.isNotBlank()) q.add("mode=${enc(n.xhttpMode)}")
            }
            "grpc" -> {
                if (n.serviceName.isNotBlank()) q.add("serviceName=${enc(n.serviceName)}")
                if (n.authority.isNotBlank()) q.add("authority=${enc(n.authority)}")
                if (n.grpcMode.isNotBlank()) q.add("mode=${enc(n.grpcMode)}")
            }
            "tcp", "kcp" -> {
                if (n.headerType.isNotBlank() && n.headerType != "none")
                    q.add("headerType=${enc(n.headerType)}")
                if (n.seed.isNotBlank()) q.add("seed=${enc(n.seed)}")
            }
        }
        if (n.echConfigList.isNotBlank()) q.add("ech=${enc(n.echConfigList)}")
        return q.joinToString("&")
    }

    private fun genVless(n: Node): String {
        val encVal = n.method.ifBlank { "none" }
        val query = "encryption=${enc(encVal)}&${transportQuery(n)}"
        return "vless://${enc(n.uuid)}@${n.host}:${n.port}?$query#${enc(n.name)}"
    }

    private fun genVmess(n: Node): String {
        // Standard vmess:// base64(json) format
        val json = JSONObject()
            .put("v", "2")
            .put("ps", n.name)
            .put("add", n.host)
            .put("port", n.port.toString())
            .put("id", n.uuid)
            .put("aid", "0")
            .put("scy", n.method.ifBlank { "auto" })
            .put("net", n.network.ifBlank { "tcp" })
            .put("type", n.headerType.ifBlank { "none" })
            .put("host", n.wsHost)
            .put("path", n.wsPath.ifBlank { "/" })
            .put("tls", if (n.security == "tls") "tls" else "")
            .put("sni", n.sni)
            .put("alpn", n.alpn)
            .put("fp", n.fp)
        val b64 = Base64.encodeToString(
            json.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "vmess://$b64"
    }

    private fun genTrojan(n: Node): String {
        val query = transportQuery(n)
        return "trojan://${enc(n.uuid)}@${n.host}:${n.port}?$query#${enc(n.name)}"
    }

    private fun genSs(n: Node): String {
        val method = n.method.ifBlank { "aes-256-gcm" }
        val userInfo = Base64.encodeToString(
            "$method:${n.uuid}".toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "ss://$userInfo@${n.host}:${n.port}#${enc(n.name)}"
    }

    private fun genHysteria2(n: Node): String {
        val q = mutableListOf<String>()
        if (n.insecure) q.add("insecure=1")
        if (n.sni.isNotBlank()) q.add("sni=${enc(n.sni)}")
        if (n.obfsPassword.isNotBlank()) {
            q.add("obfs=salamander")
            q.add("obfs-password=${enc(n.obfsPassword)}")
        }
        // bandwidth: "50 mbps" -> upmbps=50
        n.bandwidthUp.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() }?.let {
            q.add("upmbps=$it")
        }
        n.bandwidthDown.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() }?.let {
            q.add("downmbps=$it")
        }
        if (n.portHopping.isNotBlank()) q.add("mport=${enc(n.portHopping)}")
        val query = if (q.isNotEmpty()) "?${q.joinToString("&")}" else ""
        return "hysteria2://${enc(n.uuid)}@${n.host}:${n.port}$query#${enc(n.name)}"
    }

    private fun genSocks(n: Node): String {
        val auth = if (n.uuid.isNotBlank()) "${enc(n.uuid)}@" else ""
        return "socks://${auth}${n.host}:${n.port}#${enc(n.name)}"
    }

    private fun genHttp(n: Node): String {
        val auth = if (n.uuid.isNotBlank()) {
            val pass = if (n.password.isNotBlank()) ":${enc(n.password)}" else ""
            "${enc(n.uuid)}$pass@"
        } else ""
        return "http://${auth}${n.host}:${n.port}#${enc(n.name)}"
    }

    /**
     * mist:// 分享链接（v0.1）：
     *   mist://<clientIdHex>@<address>:<port>?key=<base64url(authKey)>&sni=<雾域名>#<备注>
     * Node.password 里存的是标准 base64，这里转成 base64url（无补齐）。
     */
    private fun genMist(n: Node): String {
        val keyB64Url = runCatching {
            val bytes = Base64.decode(n.password.trim(), Base64.DEFAULT)
            require(bytes.size == 32)
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }.getOrElse { return@getOrElse "" }
        require(keyB64Url.isNotEmpty()) { "mist 节点 Auth Key 无效，无法生成分享链接" }
        val sni = n.sni.ifBlank { n.host }
        return "mist://${n.uuid}@${n.host}:${n.port}" +
                "?key=$keyB64Url&sni=${enc(sni)}#${enc(n.name)}"
    }
}
