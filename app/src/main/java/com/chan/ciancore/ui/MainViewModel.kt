package com.chan.ciancore.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chan.ciancore.CianCoreApp
import com.chan.ciancore.core.CoreRegistry
import com.chan.ciancore.core.LatencyTester
import com.chan.ciancore.data.DnsPrefs.concurrentTests
import com.chan.ciancore.data.IpChecker
import com.chan.ciancore.data.Node
import com.chan.ciancore.data.NodeDao
import com.chan.ciancore.data.NodeLinkParser
import com.chan.ciancore.data.NodeShortcutManager
import com.chan.ciancore.data.SubscriptionManager
import com.chan.ciancore.service.CianCoreVpnService
import com.chan.ciancore.service.CianCoreVpnService.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home/Nodes shared view model.
 *
 * - Connection state comes straight from [CianCoreVpnService.state],
 *   so the orb always reflects the real tunnel state.
 * - Nodes come from Room; the selected node drives connect().
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao: NodeDao = CianCoreApp.instance.database.nodeDao()

    val connectionState: StateFlow<ConnectionState> = CianCoreVpnService.state
    val connectionError: StateFlow<String?> = CianCoreVpnService.errorMessage
    val activeNodeId: StateFlow<Long?> = CianCoreVpnService.activeNodeId

    val nodes: StateFlow<List<Node>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = _selectedId.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _egressIp = MutableStateFlow<IpChecker.IpInfo?>(null)
    val egressIp: StateFlow<IpChecker.IpInfo?> = _egressIp.asStateFlow()

    /** Live traffic speed in bytes/sec, polled while connected. */
    data class TrafficSpeed(val downBps: Long = 0, val upBps: Long = 0)
    private val _trafficSpeed = MutableStateFlow(TrafficSpeed())
    val trafficSpeed: StateFlow<TrafficSpeed> = _trafficSpeed.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedId.value = withContext(Dispatchers.IO) { dao.getSelected()?.id }
        }
        // Update home-screen dynamic shortcuts when nodes change.
        viewModelScope.launch {
            nodes.collect { list ->
                try {
                    NodeShortcutManager.updateDynamicShortcuts(getApplication(), list)
                } catch (_: Throwable) {
                }
            }
        }
        // Refresh egress IP whenever the tunnel connects.
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    _egressIp.value = IpChecker.check(getApplication())
                } else if (state == ConnectionState.IDLE) {
                    _egressIp.value = null
                }
            }
        }
        // Also refresh egress when the active node changes (e.g. after SWITCH).
        // The state may stay CONNECTED across a switch, so the above alone
        // would not re-check. Wait a moment for the new core's SOCKS to be ready.
        viewModelScope.launch {
            activeNodeId.collect { nodeId ->
                if (nodeId != null && connectionState.value == ConnectionState.CONNECTED) {
                    // Clear old IP immediately so the UI doesn't show stale data.
                    _egressIp.value = null
                    // Give the new Xray instance time to bind its SOCKS inbound.
                    kotlinx.coroutines.delay(1500)
                    // Only update if we're still on the same node (avoid race).
                    if (activeNodeId.value == nodeId &&
                        connectionState.value == ConnectionState.CONNECTED) {
                        _egressIp.value = IpChecker.check(getApplication())
                    }
                }
            }
        }
        // Poll traffic stats while connected (2s cadence).
        viewModelScope.launch {
            var lastTx = 0L
            var lastRx = 0L
            var lastAt = 0L
            while (isActive) {
                if (connectionState.value == ConnectionState.CONNECTED) {
                    try {
                        val stats = withContext(Dispatchers.IO) {
                            CoreRegistry.getOrNull(
                                com.chan.ciancore.core.CoreType.XRAY
                            )?.queryTrafficStats()
                        }
                        val now = System.currentTimeMillis()
                        if (stats != null && lastAt > 0) {
                            val dt = (now - lastAt).coerceAtLeast(1) / 1000.0
                            val down = ((stats.rxBytes - lastRx).coerceAtLeast(0) / dt).toLong()
                            val up = ((stats.txBytes - lastTx).coerceAtLeast(0) / dt).toLong()
                            _trafficSpeed.value = TrafficSpeed(down, up)
                        }
                        if (stats != null) {
                            lastTx = stats.txBytes
                            lastRx = stats.rxBytes
                            lastAt = now
                        }
                    } catch (_: Throwable) {
                        // Keep last known speed.
                    }
                } else {
                    lastAt = 0L
                    if (_trafficSpeed.value != TrafficSpeed()) {
                        _trafficSpeed.value = TrafficSpeed()
                    }
                }
                delay(2000)
            }
        }
    }

    /**
     * Orb tap. When disconnected: connect the selected node.
     * @param requestVpnPermission invoked with the system VPN permission
     * intent when the user hasn't granted it yet.
     */
    fun onOrbClick(requestVpnPermission: (Intent) -> Unit) {
        when (connectionState.value) {
            ConnectionState.IDLE, ConnectionState.ERROR -> {
                val nodeId = _selectedId.value
                if (nodeId == null) {
                    _notice.value = "先添加并选择一个节点"
                    return
                }
                val ctx = getApplication<Application>()
                val permissionIntent = CianCoreVpnService.prepareIntent(ctx)
                if (permissionIntent != null) {
                    requestVpnPermission(permissionIntent)
                } else {
                    CianCoreVpnService.startConnect(ctx, nodeId)
                }
            }
            ConnectionState.CONNECTING -> { /* tap does nothing mid-flight */ }
            ConnectionState.CONNECTED -> {
                CianCoreVpnService.startDisconnect(getApplication())
            }
        }
    }

    /** Called after the user grants VPN permission from the system dialog. */
    fun onVpnPermissionGranted() {
        val nodeId = _selectedId.value ?: return
        if (connectionState.value == ConnectionState.IDLE ||
            connectionState.value == ConnectionState.ERROR
        ) {
            CianCoreVpnService.startConnect(getApplication(), nodeId)
        }
    }

    fun selectNode(node: Node) {
        _selectedId.value = node.id
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearSelection()
            dao.selectById(node.id)
        }
    }

    /**
     * Connect directly to a node by ID (from home screen shortcut).
     * Selects the node, then connects if VPN permission is granted.
     * @param requestVpnPermission invoked with the system VPN permission
     * intent when the user hasn't granted it yet.
     */
    fun connectToNode(nodeId: Long, requestVpnPermission: (Intent) -> Unit) {
        viewModelScope.launch {
            val node = withContext(Dispatchers.IO) { dao.getById(nodeId) } ?: return@launch
            // Select first
            _selectedId.value = node.id
            withContext(Dispatchers.IO) {
                dao.clearSelection()
                dao.selectById(node.id)
            }
            // Then connect (or request permission)
            val ctx = getApplication<Application>()
            when (connectionState.value) {
                ConnectionState.IDLE, ConnectionState.ERROR -> {
                    val permissionIntent = CianCoreVpnService.prepareIntent(ctx)
                    if (permissionIntent != null) {
                        // Need permission: store pending node and request
                        _pendingShortcutNodeId.value = node.id
                        requestVpnPermission(permissionIntent)
                    } else {
                        CianCoreVpnService.startConnect(ctx, node.id)
                    }
                }
                ConnectionState.CONNECTED -> {
                    // Already connected: switch immediately to this node.
                    CianCoreVpnService.startSwitch(ctx, node.id)
                }
                else -> { /* connecting, ignore */ }
            }
        }
    }

    /** Pending node ID from shortcut when VPN permission was requested. */
    private val _pendingShortcutNodeId = MutableStateFlow<Long?>(null)

    /** Called after VPN permission granted for a shortcut-initiated connection. */
    fun onShortcutVpnPermissionGranted() {
        val nodeId = _pendingShortcutNodeId.value ?: return
        _pendingShortcutNodeId.value = null
        val ctx = getApplication<Application>()
        CianCoreVpnService.startConnect(ctx, nodeId)
    }

    /** Parse a share link and store it. Returns the error message, or null on success. */
    fun addNodeFromLink(link: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { NodeLinkParser.parse(link) }
            result.fold(
                onSuccess = { node ->
                    val id = withContext(Dispatchers.IO) { dao.insert(node) }
                    // Auto-select the first node ever added.
                    val select = withContext(Dispatchers.IO) { dao.getAll() }.size == 1
                    if (select) {
                        _selectedId.value = id
                        withContext(Dispatchers.IO) {
                            dao.clearSelection()
                            dao.selectById(id)
                        }
                    }
                    onDone(null)
                },
                onFailure = { t ->
                    onDone(t.message ?: "链接解析失败")
                }
            )
        }
    }

    fun deleteNode(node: Node) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_selectedId.value == node.id) _selectedId.value = null
            dao.delete(node)
        }
    }

    /** Test state per node id: null = idle, -2 = testing. */
    private val _testingIds = MutableStateFlow<Set<Long>>(emptySet())
    val testingIds: StateFlow<Set<Long>> = _testingIds.asStateFlow()

    /** TCP ping test for one node; stores result in DB. */
    fun testTcpPing(node: Node) {
        if (_testingIds.value.contains(node.id)) return
        _testingIds.value = _testingIds.value + node.id
        viewModelScope.launch {
            val ms = LatencyTester.tcpPing(node.host, node.port)
            withContext(Dispatchers.IO) { dao.updateLatency(node.id, ms) }
            _testingIds.value = _testingIds.value - node.id
        }
    }

    /** Real-connection test (full Xray handshake) for one node. */
    fun testRealConnection(node: Node) {
        if (_testingIds.value.contains(node.id)) return
        _testingIds.value = _testingIds.value + node.id
        viewModelScope.launch {
            try {
                val ms = LatencyTester.realTest(getApplication(), node)
                // Fall back to TCP ping if the real test fails outright.
                val final = if (ms < 0) {
                    try {
                        LatencyTester.tcpPing(node.host, node.port)
                    } catch (_: Throwable) {
                        -1L
                    }
                } else ms
                withContext(Dispatchers.IO) { dao.updateLatency(node.id, final) }
            } catch (_: Throwable) {
                // Never crash the app from a latency test
            } finally {
                _testingIds.value = _testingIds.value - node.id
            }
        }
    }

    /** Persist edited node fields. */
    fun updateNode(node: Node) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.update(node) }
        }
    }

    /** Create a blank node for manual editing. Returns the inserted node. */
    fun createManualNode(protocol: String, onDone: (Node) -> Unit) {
        viewModelScope.launch {
            // Single-core (Xray only); sing-box removed.
            val coreType = com.chan.ciancore.core.CoreType.XRAY
            val node = Node(
                name = "新节点",
                rawLink = "",
                protocol = protocol,
                network = "tcp",
                coreType = coreType
            )
            val id = withContext(Dispatchers.IO) { dao.insert(node) }
            onDone(node.copy(id = id))
        }
    }

    /** Test all nodes with the configured concurrency. */
    fun testAllNodes() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val list = withContext(Dispatchers.IO) { dao.getAll() }
            val pending = list.filter { !_testingIds.value.contains(it.id) }
            _testingIds.value = _testingIds.value + pending.map { it.id }
            try {
                LatencyTester.testAll(
                    ctx,
                    pending,
                    ctx.concurrentTests
                ) { node, ms ->
                    withContext(Dispatchers.IO) { dao.updateLatency(node.id, ms) }
                    _testingIds.value = _testingIds.value - node.id
                }
            } finally {
                _testingIds.value = emptySet()
            }
        }
    }

    /** Test all nodes, then auto-select the fastest reachable one. */
    fun autoSelectFastest(onDone: (Node?) -> Unit = {}) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val list = withContext(Dispatchers.IO) { dao.getAll() }
            if (list.isEmpty()) {
                onDone(null)
                return@launch
            }
            val pending = list.filter { !_testingIds.value.contains(it.id) }
            _testingIds.value = _testingIds.value + pending.map { it.id }
            try {
                LatencyTester.testAll(
                    ctx,
                    pending,
                    ctx.concurrentTests
                ) { node, ms ->
                    withContext(Dispatchers.IO) { dao.updateLatency(node.id, ms) }
                    _testingIds.value = _testingIds.value - node.id
                }
            } finally {
                _testingIds.value = emptySet()
            }
            // Pick the fastest node with a valid measurement.
            val fastest = withContext(Dispatchers.IO) { dao.getAll() }
                .filter { it.latencyMs >= 0 }
                .minByOrNull { it.latencyMs }
            if (fastest != null) selectNode(fastest)
            onDone(fastest)
        }
    }

    /**
     * Batch import from pasted text: one share link per line,
     * or a base64 subscription blob. Returns (added, failed, errors).
     */
    fun addNodesFromText(rawText: String, onDone: (SubscriptionManager.ImportResult) -> Unit) {
        viewModelScope.launch {
            val (nodes, errors) = withContext(Dispatchers.Default) {
                SubscriptionManager.parseBatch(rawText)
            }
            var added = 0
            withContext(Dispatchers.IO) {
                val hadAny = dao.getAll().isNotEmpty()
                for (node in nodes) {
                    dao.insert(node)
                    added++
                }
                // Auto-select the first node if the library was empty.
                if (!hadAny && added > 0) {
                    dao.getAll().firstOrNull()?.let {
                        _selectedId.value = it.id
                        dao.clearSelection()
                        dao.selectById(it.id)
                    }
                }
            }
            onDone(
                SubscriptionManager.ImportResult(
                    added = added,
                    failed = errors.size,
                    errors = errors.take(5)
                )
            )
        }
    }

    /** Fetch a subscription URL and import all nodes found. */
    fun addNodesFromSubscription(subUrl: String, onDone: (Result<SubscriptionManager.ImportResult>) -> Unit) {
        viewModelScope.launch {
            val fetchResult = SubscriptionManager.fetchLinks(subUrl)
            fetchResult.fold(
                onSuccess = { links ->
                    if (links.isEmpty()) {
                        onDone(Result.success(SubscriptionManager.ImportResult(0, 0, listOf("订阅中没有节点"))))
                    } else {
                        addNodesFromText(links.joinToString("\n"), { onDone(Result.success(it)) })
                    }
                },
                onFailure = { t -> onDone(Result.failure(t)) }
            )
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }
}
