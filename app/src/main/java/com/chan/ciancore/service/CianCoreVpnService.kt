package com.chan.ciancore.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.chan.ciancore.CianCoreApp
import com.chan.ciancore.R
import com.chan.ciancore.core.CoreRegistry
import com.chan.ciancore.core.CoreType
import com.chan.ciancore.core.xray.XrayConfigBuilder
import com.chan.ciancore.core.xray.XrayCore
import com.chan.ciancore.data.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * VPN service: owns the TUN interface and the active proxy core.
 *
 * Flow (Xray path, mirroring v2rayNG):
 * 1. ACTION_CONNECT(nodeId) → foreground + CONNECTING
 * 2. Load node → [XrayConfigBuilder.build] → full config JSON
 * 3. Builder().establish() → TUN fd → XrayCore.setTunFd(fd)
 * 4. core.start(config) → libv2ray startLoop(config, tunFd);
 *    the `tun` inbound pulls packets straight from our fd
 * 5. CONNECTED (or ERROR with a readable message)
 *
 * Our own package is disallowed from the tunnel so the core's
 * outbound sockets can't loop back into the TUN.
 */
class CianCoreVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunFd: ParcelFileDescriptor? = null
    private var activeNode: Node? = null

    override fun onCreate() {
        super.onCreate()
        CoreRegistry.initialize(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val nodeId = intent.getLongExtra(EXTRA_NODE_ID, -1)
                scope.launch { connect(nodeId) }
            }
            ACTION_DISCONNECT -> scope.launch { disconnect() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        scope.launch { disconnect() }
    }

    private suspend fun connect(nodeId: Long) {
        if (_state.value == ConnectionState.CONNECTING ||
            _state.value == ConnectionState.CONNECTED
        ) return
        _state.value = ConnectionState.CONNECTING
        _errorMessage.value = null

        goForeground("正在建立隧道…", null)
        try {
            val node = loadNode(nodeId)
            activeNode = node
            goForeground("正在连接 ${node.name}", node.name)

            if (node.coreType != CoreType.XRAY) {
                throw IllegalStateException("${node.coreType.displayName} 核心下一阶段支持，请先用 Xray 节点")
            }

            val configJson = XrayConfigBuilder.build(
                node,
                routeMode = com.chan.ciancore.data.RoutePrefs.getMode(this).name,
                customRules = com.chan.ciancore.data.RoutePrefs.getCustomRules(this)
                    .map { Triple(it.kind, it.value, it.action) }
            )

            val builder = Builder()
                .setSession("CianCore")
                .setMtu(VPN_MTU)
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication(packageName)
            // Per-app routing.
            applyPerAppRouting(builder)
            // Close any stale interface before establishing a new one.
            try {
                tunFd?.close()
            } catch (_: Throwable) {
            }
            tunFd = builder.establish()
                ?: throw IllegalStateException("VPN 接口创建失败")

            val core = CoreRegistry.get(CoreType.XRAY) as? XrayCore
                ?: throw IllegalStateException("Xray 核心未注册")
            core.setTunFd(tunFd!!.fd)
            core.start(configJson).getOrThrow()

            _activeNodeId.value = node.id
            _state.value = ConnectionState.CONNECTED
            goForeground("已连接 · ${node.name}", node.name)
            Log.i(TAG, "connected: ${node.name}")
        } catch (t: Throwable) {
            val msg = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            Log.e(TAG, "connect failed", t)
            _errorMessage.value = msg
            _state.value = ConnectionState.ERROR
            cleanupLocked()
            stopSelf()
        }
    }

    /** Applies per-app allow/block lists to the VPN builder. */
    private fun applyPerAppRouting(builder: Builder) {
        val mode = com.chan.ciancore.data.PerAppPrefs.getMode(this)
        if (mode == com.chan.ciancore.data.PerAppPrefs.Mode.ALL) return
        val packages = com.chan.ciancore.data.PerAppPrefs.getPackages(this)
        for (pkg in packages) {
            if (pkg == packageName) continue
            try {
                when (mode) {
                    com.chan.ciancore.data.PerAppPrefs.Mode.BLOCKLIST ->
                        builder.addDisallowedApplication(pkg)
                    com.chan.ciancore.data.PerAppPrefs.Mode.ALLOWLIST ->
                        builder.addAllowedApplication(pkg)
                    else -> {}
                }
            } catch (_: Throwable) {
                // Package may be uninstalled; skip.
            }
        }
    }

    private suspend fun disconnect() {
        try {
            cleanupLocked()
        } finally {
            _state.value = ConnectionState.IDLE
            _activeNodeId.value = null
            activeNode = null
            _errorMessage.value = null
            stopSelf()
        }
    }

    /** Stops the core and closes the TUN fd. Call from the IO scope. */
    private fun cleanupLocked() {
        try {
            (CoreRegistry.getOrNull(CoreType.XRAY) as? XrayCore)?.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "core stop failed", t)
        }
        try {
            tunFd?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "tun close failed", t)
        } finally {
            tunFd = null
        }
    }

    private suspend fun loadNode(nodeId: Long): Node {
        if (nodeId <= 0) throw IllegalArgumentException("没有选中节点")
        val dao = CianCoreApp.instance.database.nodeDao()
        return dao.getById(nodeId)
            ?: throw IllegalArgumentException("节点不存在（可能已被删除）")
    }

    override fun onDestroy() {
        scope.cancel()
        try {
            cleanupLocked()
        } catch (_: Throwable) {
        }
        // Preserve ERROR so the message stays on screen until the user retries;
        // stopSelf() after a failed connect would otherwise wipe it instantly.
        if (_state.value != ConnectionState.ERROR) {
            _state.value = ConnectionState.IDLE
        }
        super.onDestroy()
    }

    // ── foreground notification ───────────────────────────────

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "CianCore 隧道", NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification(title: String, nodeName: String?): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle("CianCore")
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun goForeground(title: String, nodeName: String?) {
        val notification = buildNotification(title, nodeName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, 0)
        }
    }

    enum class ConnectionState { IDLE, CONNECTING, CONNECTED, ERROR }

    companion object {
        private const val TAG = "CianCoreVpn"
        private const val CHANNEL_ID = "ciancore_vpn"
        private const val NOTIF_ID = 1001
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.8.0.2"

        const val ACTION_CONNECT = "com.chan.ciancore.action.CONNECT"
        const val ACTION_DISCONNECT = "com.chan.ciancore.action.DISCONNECT"
        const val EXTRA_NODE_ID = "extra_node_id"

        private val _state = MutableStateFlow(ConnectionState.IDLE)
        val state: StateFlow<ConnectionState> = _state.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val _activeNodeId = MutableStateFlow<Long?>(null)
        val activeNodeId: StateFlow<Long?> = _activeNodeId.asStateFlow()

        /** Start a connection to [nodeId]. Caller must hold VPN permission. */
        fun startConnect(context: Context, nodeId: Long) {
            val intent = Intent(context, CianCoreVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_NODE_ID, nodeId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startDisconnect(context: Context) {
            val intent = Intent(context, CianCoreVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }

        /** Returns the VPN permission intent, or null if already granted. */
        fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)
    }
}
