package com.chan.weave.data

import android.content.Context
import androidx.core.content.edit

/**
 * Chain proxy (代理链): an ordered list of nodes that the selected node's
 * traffic dials through in sequence (Xray streamSettings.sockopt.dialerProxy).
 * Empty list (default) = disabled.
 *
 * Example: chain = [A, B] means traffic goes: main → A → B → Internet.
 * The egress IP is B's IP.
 */
object ChainPrefs {

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("chain", Context.MODE_PRIVATE)

    /** Ordered list of chain node IDs. Empty = disabled. */
    var Context.chainNodeIds: List<Long>
        get() {
            val raw = prefs(this).getString("chain_node_ids", "") ?: ""
            if (raw.isBlank()) return emptyList()
            return raw.split(",").mapNotNull { it.toLongOrNull() }
        }
        set(v) {
            prefs(this).edit { putString("chain_node_ids", v.joinToString(",")) }
        }

    /** Legacy single-node API (for migration). */
    var Context.chainNodeId: Long
        get() {
            // Migrate old single value if present
            val old = prefs(this).getLong("chain_node_id", -1L)
            if (old > 0) {
                // Migrate to new format
                chainNodeIds = listOf(old)
                prefs(this).edit { remove("chain_node_id") }
                return old
            }
            return chainNodeIds.firstOrNull() ?: -1L
        }
        set(v) {
            prefs(this).edit { remove("chain_node_id") }
            chainNodeIds = if (v > 0) listOf(v) else emptyList()
        }
}
