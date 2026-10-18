package com.chan.ciancore.data

import android.content.Context
import androidx.core.content.edit

/**
 * Chain proxy (前置代理): an optional node that the selected node's
 * traffic dials through (Xray proxySettings.tag = "front").
 * -1 (default) = disabled.
 */
object ChainPrefs {

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("chain", Context.MODE_PRIVATE)

    var Context.chainNodeId: Long
        get() = prefs(this).getLong("chain_node_id", -1L)
        set(v) = prefs(this).edit { putLong("chain_node_id", v) }
}
