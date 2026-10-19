package com.chan.weave.data

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.chan.weave.MainActivity
import com.chan.weave.R

/**
 * Home screen shortcuts for nodes: tap to launch and connect directly.
 */
object NodeShortcutManager {

    const val EXTRA_CONNECT_NODE_ID = "extra_connect_node_id"

    /**
     * Pin a shortcut for [node] to the home screen.
     * Returns true if the pin request was sent.
     */
    fun pinNodeShortcut(context: Context, node: Node): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!sm.isRequestPinShortcutSupported) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_CONNECT_NODE_ID, node.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val shortcut = ShortcutInfo.Builder(context, "node_${node.id}")
            .setShortLabel(node.name.take(12))
            .setLongLabel("连接 ${node.name}")
            .setIcon(Icon.createWithResource(context, R.drawable.ic_tile_vpn))
            .setIntent(intent)
            .build()

        return try {
            sm.requestPinShortcut(shortcut, null)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Update dynamic shortcuts (long-press app icon) with recent nodes.
     */
    fun updateDynamicShortcuts(context: Context, nodes: List<Node>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return
        try {
            val shortcuts = nodes.take(4).map { node ->
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(EXTRA_CONNECT_NODE_ID, node.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                ShortcutInfo.Builder(context, "node_${node.id}")
                    .setShortLabel(node.name.take(12))
                    .setLongLabel("连接 ${node.name}")
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_tile_vpn))
                    .setIntent(intent)
                    .build()
            }
            sm.dynamicShortcuts = shortcuts
        } catch (_: Throwable) {
        }
    }
}
