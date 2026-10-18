package com.chan.ciancore.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.chan.ciancore.core.CoreRegistry
import com.chan.ciancore.core.CoreType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * VPN service skeleton.
 *
 * Establishes the TUN interface and drives the active [ProxyCore]
 * from [CoreRegistry]. The core used per-connection comes from the
 * selected node's [CoreType] (next iteration); for now the registry's
 * active core is used.
 *
 * Xray-core start/stop and traffic plumbing land in the next iteration.
 */
class CianCoreVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunFd: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        CoreRegistry.initialize(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val coreType = intent.getStringExtra(EXTRA_CORE)
                    ?.let { CoreType.fromName(it) }
                    ?: CoreRegistry.activeType.value
                scope.launch { connect(coreType) }
            }
            ACTION_DISCONNECT -> scope.launch { disconnect() }
        }
        return START_STICKY
    }

    private suspend fun connect(coreType: CoreType) {
        if (_state.value == ConnectionState.CONNECTED ||
            _state.value == ConnectionState.CONNECTING
        ) return
        _state.value = ConnectionState.CONNECTING
        try {
            val builder = Builder()
                .setSession("CianCore")
                .addAddress("10.8.0.2", 32)
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
            // TODO: per-app rules, IPv6
            tunFd = builder.establish()

            // TODO: build engine-native config JSON from the selected node,
            // then: CoreRegistry.get(coreType).start(configJson)
            val core = CoreRegistry.get(coreType)
            // val configJson = buildConfig(selectedNode, core.type)
            // core.start(configJson).getOrThrow()

            _state.value = ConnectionState.CONNECTED
            _activeCore.value = core.type
        } catch (t: Throwable) {
            _state.value = ConnectionState.IDLE
            stopSelf()
        }
    }

    private suspend fun disconnect() {
        try {
            CoreRegistry.getOrNull(_activeCore.value)?.stop()
            tunFd?.close()
        } catch (_: Throwable) {
        } finally {
            tunFd = null
            _state.value = ConnectionState.IDLE
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        try {
            CoreRegistry.getOrNull(_activeCore.value)?.stop()
            tunFd?.close()
        } catch (_: Throwable) {
        }
        _state.value = ConnectionState.IDLE
        super.onDestroy()
    }

    enum class ConnectionState { IDLE, CONNECTING, CONNECTED }

    companion object {
        const val ACTION_CONNECT = "com.chan.ciancore.action.CONNECT"
        const val ACTION_DISCONNECT = "com.chan.ciancore.action.DISCONNECT"
        const val EXTRA_CORE = "extra_core_type"

        private val _state = MutableStateFlow(ConnectionState.IDLE)
        val state: StateFlow<ConnectionState> = _state.asStateFlow()

        private val _activeCore = MutableStateFlow(CoreType.XRAY)
        val activeCore: StateFlow<CoreType> = _activeCore.asStateFlow()

        /** Ask the system for VPN permission, then start the service. */
        fun prepareIntent(service: VpnService): Intent? = VpnService.prepare(service)
    }
}
