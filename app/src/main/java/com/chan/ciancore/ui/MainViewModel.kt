package com.chan.ciancore.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chan.ciancore.CianCoreApp
import com.chan.ciancore.data.Node
import com.chan.ciancore.data.NodeDao
import com.chan.ciancore.data.NodeLinkParser
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

    init {
        viewModelScope.launch {
            _selectedId.value = withContext(Dispatchers.IO) { dao.getSelected()?.id }
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

    fun consumeNotice() {
        _notice.value = null
    }
}
