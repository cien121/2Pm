package com.chan.ciancore.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chan.ciancore.CianCoreApp
import com.chan.ciancore.core.LatencyTester
import com.chan.ciancore.data.DnsPrefs.concurrentTests
import com.chan.ciancore.data.IpChecker
import com.chan.ciancore.data.Node
import com.chan.ciancore.data.NodeDao
import com.chan.ciancore.data.NodeLinkParser
import com.chan.ciancore.data.SubscriptionManager
import com.chan.ciancore.service.CianCoreVpnService
import com.chan.ciancore.service.CianCoreVpnService.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    init {
        viewModelScope.launch {
            _selectedId.value = withContext(Dispatchers.IO) { dao.getSelected()?.id }
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
            val ms = LatencyTester.realTest(getApplication(), node)
            // Fall back to TCP ping if the real test fails outright.
            val final = if (ms < 0) LatencyTester.tcpPing(node.host, node.port) else ms
            withContext(Dispatchers.IO) { dao.updateLatency(node.id, final) }
            _testingIds.value = _testingIds.value - node.id
        }
    }

    /** Persist edited node fields. */
    fun updateNode(node: Node) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.update(node) }
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
