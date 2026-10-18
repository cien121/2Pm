package com.chan.ciancore.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chan.ciancore.core.CoreType

/**
 * Proxy node entity. First version stores the essentials;
 * protocol-specific fields will grow as features land.
 */
@Entity(tableName = "nodes")
data class Node(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Display name */
    val name: String,
    /** Raw subscription / share link text */
    val rawLink: String,
    /** Protocol, e.g. vless, vmess, trojan, hysteria2, shadowsocks */
    val protocol: String = "",
    /** Server host */
    val host: String = "",
    /** Server port */
    val port: Int = 0,
    /** Which core engine runs this node */
    val coreType: CoreType = CoreType.XRAY,
    /** Last measured latency in ms, -1 = untested */
    val latencyMs: Long = -1,
    /** Whether this node is currently selected */
    val isSelected: Boolean = false,
    /** Creation timestamp */
    val createdAt: Long = System.currentTimeMillis()
)
