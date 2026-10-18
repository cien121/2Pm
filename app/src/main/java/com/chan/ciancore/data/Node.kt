package com.chan.ciancore.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chan.ciancore.core.CoreType

/**
 * Proxy node entity.
 *
 * Protocol-specific fields cover the URI schemes parsed by
 * [NodeLinkParser]: vless, vmess, trojan, ss (shadowsocks),
 * hysteria2/hy2 and tuic. Fields that don't apply to a protocol
 * stay at their defaults.
 *
 * Identity mapping:
 * - vless / vmess: [uuid] = user id
 * - trojan / ss / hysteria2: [uuid] = password / auth string
 * - tuic: [uuid] + [password]
 */
@Entity(tableName = "nodes")
data class Node(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Display name */
    val name: String,
    /** Raw subscription / share link text */
    val rawLink: String,
    /** Protocol: vless, vmess, trojan, ss, hysteria2, tuic */
    val protocol: String = "",
    /** Server host */
    val host: String = "",
    /** Server port */
    val port: Int = 0,
    /** User id (vless/vmess), password (trojan/ss/hysteria2), uuid (tuic) */
    val uuid: String = "",
    /** Second credential (tuic password) */
    val password: String = "",
    /** Cipher: ss method, vmess security, vless encryption */
    val method: String = "",
    /** XTLS flow (vless/trojan), e.g. xtls-rprx-vision */
    val flow: String = "",
    /** Transport: tcp, ws, grpc, h2, httpupgrade, xhttp, kcp */
    val network: String = "tcp",
    /** Stream security: "", "tls", "reality" */
    val security: String = "",
    /** TLS/Reality server name */
    val sni: String = "",
    /** TLS ALPN, comma separated */
    val alpn: String = "",
    /** uTLS fingerprint */
    val fp: String = "",
    /** Reality public key */
    val pbk: String = "",
    /** Reality short id */
    val sid: String = "",
    /** Reality spiderX */
    val spx: String = "",
    /** Allow insecure TLS */
    val insecure: Boolean = false,
    /** WS / httpupgrade / xhttp host header */
    val wsHost: String = "",
    /** WS / httpupgrade / xhttp path */
    val wsPath: String = "/",
    /** gRPC service name */
    val serviceName: String = "",
    /** gRPC authority */
    val authority: String = "",
    /** gRPC mode (gun/multi) */
    val grpcMode: String = "",
    /** TCP/KCP header type: none, http, ... */
    val headerType: String = "none",
    /** KCP seed */
    val seed: String = "",
    /** Hysteria2 upload bandwidth, e.g. "50 mbps" */
    val bandwidthUp: String = "",
    /** Hysteria2 download bandwidth */
    val bandwidthDown: String = "",
    /** Hysteria2 salamander obfs password */
    val obfsPassword: String = "",
    /** Hysteria2 port hopping range (mport) */
    val portHopping: String = "",
    /** TUIC congestion control */
    val congestionControl: String = "bbr",
    /** TUIC UDP relay mode */
    val udpRelayMode: String = "native",
    /** Which core engine runs this node */
    val coreType: CoreType = CoreType.XRAY,
    /** Last measured latency in ms, -1 = untested */
    val latencyMs: Long = -1,
    /** Whether this node is currently selected */
    val isSelected: Boolean = false,
    /** Creation timestamp */
    val createdAt: Long = System.currentTimeMillis()
)
