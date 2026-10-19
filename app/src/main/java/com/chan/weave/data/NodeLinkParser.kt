package com.chan.weave.data

import android.net.Uri
import android.util.Base64
import com.chan.weave.core.CoreType
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Share-link → [Node] parser.
 *
 * Supported schemes: vless://, vmess://, trojan://, ss://,
 * socks:// (socks5://), hysteria2:// (hy2://), tuic://.
 *
 * Query-parameter mapping follows the v2rayNG convention
 * (type/security/sni/fp/alpn/pbk/sid/spx/flow/insecure/...).
 * Every failure returns a human-readable Chinese error message.
 */
object NodeLinkParser {

    fun parse(rawLink: String): Result<Node> = runCatching {
        val link = rawLink.trim()
        require(link.isNotEmpty()) { "链接为空，请粘贴节点链接" }
        val scheme = link.substringBefore("://", "").lowercase()
        require(scheme.isNotEmpty() && link.contains("://")) { "不是有效的节点链接：缺少协议头（如 vless://）" }
        when (scheme) {
            "vless" -> parseVless(link)
            "vmess" -> parseVmess(link)
            "trojan" -> parseTrojan(link)
            "ss", "shadowsocks" -> parseSs(link)
            "hysteria2", "hy2" -> parseHysteria2(link)
            "tuic" -> parseTuic(link)
            "socks", "socks5" -> parseSocks(link)
            "http" -> parseHttp(link)
            "anytls" -> parseAnytls(link)
            "wireguard" -> parseWireguard(link)
            else -> throw IllegalArgumentException("不支持的协议：$scheme")
        }
    }

    // ── common helpers ──────────────────────────────────────────

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        s
    }

    private fun decodeBase64(s: String): String {
        var t = s.trim().replace('-', '+').replace('_', '/')
        val pad = t.length % 4
        if (pad > 0) t += "=".repeat(4 - pad)
        return String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun checkHostPort(host: String?, port: Int, scheme: String) {
        require(!host.isNullOrBlank()) { "$scheme 链接缺少服务器地址" }
        require(port in 1..65535) { "$scheme 链接端口无效：$port" }
    }

    private fun nodeName(uri: Uri, fallback: String): String {
        val frag = uri.fragment?.let { decode(it) }.orEmpty().trim()
        return frag.ifEmpty { fallback }
    }

    /** Shared transport/security query params (v2rayNG FmtBase mapping). */
    private fun applyTransportQuery(n: Node, uri: Uri): Node {
        fun q(name: String): String? = uri.getQueryParameter(name)?.takeIf { it.isNotEmpty() }
        val security = q("security")?.lowercase().takeIf { it == "tls" || it == "reality" }.orEmpty()
        val insecure = listOf("insecure", "allowInsecure", "allow_insecure")
            .any { uri.getQueryParameter(it) == "1" }
        val net = q("type")?.lowercase()?.ifEmpty { "tcp" } ?: "tcp"
        // "mode" means XHTTP mode for xhttp, gRPC mode for grpc.
        val mode = q("mode").orEmpty()
        return n.copy(
            network = net,
            headerType = q("headerType")?.ifEmpty { "none" } ?: "none",
            wsHost = q("host").orEmpty(),
            wsPath = q("path")?.ifEmpty { "/" } ?: "/",
            seed = q("seed").orEmpty(),
            serviceName = q("serviceName").orEmpty(),
            authority = q("authority").orEmpty(),
            grpcMode = if (net == "grpc") mode else "",
            xhttpMode = if (net == "xhttp") mode else "",
            security = security,
            insecure = insecure,
            sni = q("sni").orEmpty(),
            fp = q("fp").orEmpty(),
            alpn = q("alpn").orEmpty(),
            pbk = q("pbk").orEmpty(),
            sid = q("sid").orEmpty(),
            spx = q("spx").orEmpty(),
            flow = q("flow").orEmpty(),
            echConfigList = q("ech").orEmpty(),
            peerCertSha256 = q("peerCertSha256").orEmpty()
        )
    }

    // ── vless ───────────────────────────────────────────────────

    private fun parseVless(link: String): Node {
        val uri = Uri.parse(link)
        val uuid = decode(uri.encodedUserInfo.orEmpty())
        require(uuid.isNotBlank()) { "vless 链接缺少 UUID（@ 前面的部分）" }
        checkHostPort(uri.host, uri.port, "vless")
        var n = Node(
            name = nodeName(uri, "${uri.host}:${uri.port}"),
            rawLink = link,
            protocol = "vless",
            host = uri.host!!,
            port = uri.port,
            uuid = uuid,
            method = uri.getQueryParameter("encryption")?.ifEmpty { "none" } ?: "none",
            coreType = CoreType.XRAY
        )
        return applyTransportQuery(n, uri)
    }

    // ── vmess ───────────────────────────────────────────────────

    private fun parseVmess(link: String): Node {
        // Standard URI form: vmess://uuid@host:port?security=auto&...
        val afterScheme = link.substringAfter("://")
        if (afterScheme.contains("?") && afterScheme.contains("&")) {
            return parseVmessStd(link)
        }
        val json = try {
            decodeBase64(afterScheme.substringBefore("#").substringBefore("?"))
        } catch (_: Exception) {
            throw IllegalArgumentException("vmess 链接 Base64 解码失败")
        }
        val o = try {
            JSONObject(json)
        } catch (_: Exception) {
            throw IllegalArgumentException("vmess 链接内容不是有效的 JSON")
        }
        val host = o.optString("add").trim()
        val port = o.optString("port").trim().toIntOrNull() ?: -1
        val id = o.optString("id").trim()
        require(host.isNotEmpty()) { "vmess 链接缺少服务器地址（add 字段）" }
        require(port in 1..65535) { "vmess 链接端口无效" }
        require(id.isNotEmpty()) { "vmess 链接缺少 UUID（id 字段）" }
        val net = o.optString("net", "tcp").ifEmpty { "tcp" }
        return Node(
            name = o.optString("ps", "").ifEmpty { "$host:$port" },
            rawLink = link,
            protocol = "vmess",
            host = host,
            port = port,
            uuid = id,
            method = o.optString("scy", "auto").ifEmpty { "auto" },
            network = net,
            headerType = o.optString("type", "none").ifEmpty { "none" },
            wsHost = o.optString("host"),
            wsPath = o.optString("path").ifEmpty { "/" },
            seed = if (net == "kcp") o.optString("path") else "",
            serviceName = if (net == "grpc") o.optString("path") else "",
            authority = if (net == "grpc") o.optString("host") else "",
            grpcMode = if (net == "grpc") o.optString("type") else "",
            security = o.optString("tls").lowercase().takeIf { it == "tls" || it == "reality" }.orEmpty(),
            sni = o.optString("sni"),
            fp = o.optString("fp"),
            alpn = o.optString("alpn"),
            insecure = o.optString("insecure") == "1",
            coreType = CoreType.XRAY
        )
    }

    private fun parseVmessStd(link: String): Node {
        val uri = Uri.parse(link)
        val uuid = decode(uri.encodedUserInfo.orEmpty())
        require(uuid.isNotBlank()) { "vmess 链接缺少 UUID" }
        checkHostPort(uri.host, uri.port, "vmess")
        var n = Node(
            name = nodeName(uri, "${uri.host}:${uri.port}"),
            rawLink = link,
            protocol = "vmess",
            host = uri.host!!,
            port = uri.port,
            uuid = uuid,
            method = "auto",
            coreType = CoreType.XRAY
        )
        return applyTransportQuery(n, uri)
    }

    // ── trojan ──────────────────────────────────────────────────

    private fun parseTrojan(link: String): Node {
        val uri = Uri.parse(link)
        val password = decode(uri.encodedUserInfo.orEmpty())
        require(password.isNotBlank()) { "trojan 链接缺少密码（@ 前面的部分）" }
        checkHostPort(uri.host, uri.port, "trojan")
        var n = Node(
            name = nodeName(uri, "${uri.host}:${uri.port}"),
            rawLink = link,
            protocol = "trojan",
            host = uri.host!!,
            port = uri.port,
            uuid = password,
            coreType = CoreType.XRAY
        )
        n = applyTransportQuery(n, uri)
        // Trojan is TLS by nature; default to tls when the link says nothing.
        if (n.security.isEmpty()) n = n.copy(security = "tls", network = n.network.ifEmpty { "tcp" })
        return n
    }

    // ── shadowsocks ─────────────────────────────────────────────

    private fun parseSs(link: String): Node {
        // Split off fragment first.
        val hashIdx = link.indexOf('#')
        val remark = if (hashIdx >= 0) decode(link.substring(hashIdx + 1)) else ""
        var body = if (hashIdx >= 0) link.substring(0, hashIdx) else link
        body = body.substringAfter("://")
        // Split query (plugin=...) if present.
        val queryIdx = body.indexOf('?')
        val query = if (queryIdx >= 0) body.substring(queryIdx + 1) else ""
        if (queryIdx >= 0) body = body.substring(0, queryIdx)
        if (query.contains("plugin=")) {
            throw IllegalArgumentException("ss 链接带 obfs 插件，当前版本暂不支持")
        }

        var method = ""
        var password = ""
        var host: String? = null
        var port = -1

        if (body.contains("@")) {
            val userInfo = body.substringBeforeLast("@")
            val hostPort = body.substringAfterLast("@")
            port = hostPort.substringAfterLast(":").substringBefore("/").toIntOrNull() ?: -1
            host = hostPort.substringBeforeLast(":").removeSurrounding("[", "]").ifEmpty { null }
            val plain = if (userInfo.contains(":")) {
                userInfo
            } else try {
                decodeBase64(userInfo)
            } catch (_: Exception) {
                throw IllegalArgumentException("ss 链接用户信息部分无法解析")
            }
            method = plain.substringBefore(":").lowercase()
            password = decode(plain.substringAfter(":"))
        } else {
            // Fully-base64 legacy form: base64(method:password@host:port)
            val decoded = try {
                decodeBase64(body.trimEnd('/'))
            } catch (_: Exception) {
                throw IllegalArgumentException("ss 链接 Base64 解码失败")
            }
            val m = Regex("^(.+?):(.*)@(.+?):(\\d+)/?$").matchEntire(decoded)
                ?: throw IllegalArgumentException("ss 链接格式无法识别")
            method = m.groupValues[1].lowercase()
            password = m.groupValues[2]
            host = m.groupValues[3].removeSurrounding("[", "]")
            port = m.groupValues[4].toIntOrNull() ?: -1
        }

        require(method.isNotEmpty()) { "ss 链接缺少加密方式" }
        require(password.isNotEmpty()) { "ss 链接缺少密码" }
        checkHostPort(host, port, "ss")
        return Node(
            name = remark.ifEmpty { "$host:$port" },
            rawLink = link,
            protocol = "ss",
            host = host!!,
            port = port,
            uuid = password,
            method = method,
            network = "tcp",
            coreType = CoreType.XRAY
        )
    }

    // ── hysteria2 ───────────────────────────────────────────────

    private fun parseHysteria2(link: String): Node {
        val uri = Uri.parse(link)
        val auth = decode(uri.encodedUserInfo.orEmpty())
        require(auth.isNotBlank()) { "hysteria2 链接缺少认证密码（@ 前面的部分）" }
        checkHostPort(uri.host, uri.port, "hysteria2")
        fun q(name: String): String? = uri.getQueryParameter(name)?.takeIf { it.isNotEmpty() }
        val insecure = listOf("insecure", "allowInsecure", "allow_insecure")
            .any { uri.getQueryParameter(it) == "1" }
        return Node(
            name = nodeName(uri, "${uri.host}:${uri.port}"),
            rawLink = link,
            protocol = "hysteria2",
            host = uri.host!!,
            port = uri.port,
            uuid = auth,
            security = "tls",
            sni = q("sni").orEmpty(),
            alpn = q("alpn") ?: "h3",
            insecure = insecure,
            bandwidthUp = q("upmbps")?.let { "$it mbps" }.orEmpty(),
            bandwidthDown = q("downmbps")?.let { "$it mbps" }.orEmpty(),
            obfsPassword = q("obfs-password").orEmpty(),
            portHopping = q("mport").orEmpty(),
            hopInterval = q("hop-interval").orEmpty(),
            echConfigList = q("ech").orEmpty(),
            coreType = CoreType.XRAY
        )
    }

    // ── socks5 ────────────────────────────────────────────────────

    private fun parseSocks(link: String): Node {
        val uri = Uri.parse(link)
        var userInfo = decode(uri.encodedUserInfo.orEmpty())
        // userinfo is often base64("user:pass")
        if (userInfo.isNotEmpty() && ":" !in userInfo) {
            try {
                val decoded = decodeBase64(userInfo)
                if (Regex("^[\\x20-\\x7E]+:[\\x20-\\x7E]*$").matches(decoded)) {
                    userInfo = decoded
                }
            } catch (_: Exception) { /* keep raw userinfo */ }
        }
        val user = userInfo.substringBefore(":").trim()
        val pass = if (":" in userInfo) userInfo.substringAfter(":") else ""
        checkHostPort(uri.host, uri.port, "socks5")
        return Node(
            name = nodeName(uri, "${uri.host}:${uri.port}"),
            rawLink = link,
            protocol = "socks",
            host = uri.host!!,
            port = uri.port,
            uuid = user,
            password = pass,
            network = "tcp",
            coreType = CoreType.XRAY
        )
    }

    private fun parseHttp(link: String): Node {
        val uri = Uri.parse(link)
        val userInfo = decode(uri.encodedUserInfo.orEmpty())
        val user = userInfo.substringBefore(":").trim()
        val pass = if (":" in userInfo) userInfo.substringAfter(":") else ""
        checkHostPort(uri.host, uri.port, "http")
        return Node(
            name = nodeName(uri, "${uri.host}:${uri.port}"),
            rawLink = link,
            protocol = "http",
            host = uri.host!!,
            port = uri.port,
            uuid = user,
            password = pass,
            network = "tcp",
            coreType = CoreType.XRAY
        )
    }

    // ── tuic ────────────────────────────────────────────────────

    private fun parseTuic(link: String): Node {
        throw IllegalArgumentException("TUIC 协议暂不支持（仅 Xray 内核）")
        val uri = Uri.parse(link)
        val userInfo = decode(uri.encodedUserInfo.orEmpty())
        val uuid = userInfo.substringBefore(":")
        val password = userInfo.substringAfter(":", "")
        require(uuid.isNotBlank()) { "tuic 链接缺少 UUID（uuid:password@...）" }
        require(password.isNotEmpty()) { "tuic 链接缺少密码（uuid:password@...）" }
        checkHostPort(uri.host, uri.port, "tuic")
        fun q(name: String): String? = uri.getQueryParameter(name)?.takeIf { it.isNotEmpty() }
        val insecure = listOf("insecure", "allowInsecure", "allow_insecure")
            .any { uri.getQueryParameter(it) == "1" }
        return Node(
            name = nodeName(uri, "${uri.host}:${uri.port}"),
            rawLink = link,
            protocol = "tuic",
            host = uri.host!!,
            port = uri.port,
            uuid = uuid,
            password = password,
            security = "tls",
            sni = q("sni").orEmpty(),
            alpn = q("alpn") ?: "h3",
            insecure = insecure,
            congestionControl = q("congestion_control") ?: "bbr",
            udpRelayMode = q("udp_relay_mode") ?: "native",
            // TUIC is sing-box only; parsed fine, connection gated later.
            coreType = CoreType.SING_BOX
        )
    }

    private fun parseAnytls(link: String): Node {
        throw IllegalArgumentException("AnyTLS 协议暂不支持（仅 Xray 内核）")
        val uri = Uri.parse(link)
        val password = decode(uri.encodedUserInfo.orEmpty())
        require(password.isNotBlank()) { "anytls 链接缺少密码（password@...）" }
        checkHostPort(uri.host, uri.port, "anytls")
        fun q(name: String): String? = uri.getQueryParameter(name)?.takeIf { it.isNotEmpty() }
        val insecure = listOf("insecure", "allowInsecure", "allow_insecure")
            .any { uri.getQueryParameter(it) == "1" }
        return Node(
            name = nodeName(uri, "${uri.host}:${uri.port}"),
            rawLink = link,
            protocol = "anytls",
            host = uri.host!!,
            port = uri.port,
            password = password,
            security = "tls",
            sni = q("sni").orEmpty(),
            insecure = insecure,
            coreType = CoreType.SING_BOX
        )
    }

    private fun parseWireguard(link: String): Node {
        throw IllegalArgumentException("WireGuard 协议暂不支持（仅 Xray 内核）")
        val uri = Uri.parse(link)
        val privateKey = decode(uri.encodedUserInfo.orEmpty())
        require(privateKey.isNotBlank()) { "wireguard 链接缺少私钥（privateKey@...）" }
        checkHostPort(uri.host, uri.port, "wireguard")
        fun q(name: String): String? = uri.getQueryParameter(name)?.takeIf { it.isNotEmpty() }
        val publicKey = q("publickey") ?: q("publicKey") ?: q("peer")
        require(!publicKey.isNullOrBlank()) { "wireguard 链接缺少对端公钥（?publickey=...）" }
        return Node(
            name = nodeName(uri, "${uri.host}:${uri.port}"),
            rawLink = link,
            protocol = "wireguard",
            host = uri.host!!,
            port = uri.port,
            uuid = privateKey,
            password = publicKey,
            coreType = CoreType.SING_BOX
        )
    }
}
