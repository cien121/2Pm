package com.chan.ciancore.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.chan.ciancore.CianCoreApp
import com.chan.ciancore.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile: tap to toggle the VPN tunnel.
 * Shows active state when connected.
 */
class CianCoreTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val state = CianCoreVpnService.state.first()
            val connected = state == CianCoreVpnService.ConnectionState.CONNECTED
            // Show the selected node name on the tile, like v2rayNG does.
            val nodeName = try {
                val dao = CianCoreApp.instance.database.nodeDao()
                withContext(Dispatchers.IO) {
                    dao.getSelected()?.name
                }
            } catch (_: Throwable) { null }
            updateTile(connected, nodeName)
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val state = CianCoreVpnService.state.first()
            val connected = state == CianCoreVpnService.ConnectionState.CONNECTED
            val intent = Intent(this@CianCoreTileService, CianCoreVpnService::class.java).apply {
                action = if (connected) CianCoreVpnService.ACTION_DISCONNECT
                else CianCoreVpnService.ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            // Optimistically flip the tile; the service will correct it.
            updateTile(!connected)
        }
        // For Android 13+, request the system to keep the tile updated.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // no-op: onStartListening covers refreshes
        }
    }

    private fun updateTile(connected: Boolean, nodeName: String? = null) {
        qsTile?.let { tile ->
            tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            // Show node name like v2rayNG: tap toggles that node.
            tile.label = nodeName ?: getString(R.string.app_name)
            tile.subtitle = if (connected) "已连接" else "未连接"
            try {
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_vpn)
            } catch (_: Throwable) {
            }
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
