package com.chan.ciancore.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chan.ciancore.data.Node
import com.chan.ciancore.data.SubscriptionManager
import com.chan.ciancore.ui.MainViewModel
import com.chan.ciancore.ui.components.AuroraBackground
import com.chan.ciancore.ui.components.LatencyBar
import com.chan.ciancore.ui.theme.AuroraCyan
import com.chan.ciancore.ui.theme.AuroraGradient
import com.chan.ciancore.ui.theme.InkPrimary
import com.chan.ciancore.ui.theme.InkSecondary
import com.chan.ciancore.ui.theme.NightOutline
import com.chan.ciancore.ui.theme.NightSurface
import com.chan.ciancore.ui.theme.SignalError
import com.chan.ciancore.ui.theme.latencyColor

/**
 * Nodes — signal grid.
 *
 * Two-column mosaic of node tiles with latency glow instead of a
 * spreadsheet. Tap selects, long-press deletes, FAB pastes a share link.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NodesScreen(vm: MainViewModel) {
    val nodeList by vm.nodes.collectAsState()
    val testingIds by vm.testingIds.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Node?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 96.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "节点",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = InkPrimary
                )
                if (nodeList.isNotEmpty()) {
                    val testing = testingIds.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, AuroraCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { vm.testAllNodes() })
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (testing) "测速中…" else "⚡ 全部测速",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AuroraCyan
                        )
                    }
                }
            }
            Text(
                text = "${nodeList.size} 个信号源 · 长按删除 · 点延迟测速",
                fontSize = 13.sp,
                color = InkSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            if (nodeList.isEmpty()) {
                EmptyNodesArt(Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(nodeList, key = { it.id }) { node ->
                        NodeTile(
                            node = node,
                            testing = testingIds.contains(node.id),
                            onClick = { vm.selectNode(node) },
                            onLongClick = { pendingDelete = node },
                            onTest = { vm.testRealConnection(node) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 112.dp),
            containerColor = AuroraCyan,
            contentColor = Color(0xFF06231F)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加节点")
        }
    }

    if (showAdd) {
        AddNodeDialog(
            onDismiss = { showAdd = false },
            onAdd = { link, onResult ->
                vm.addNodeFromLink(link) { err ->
                    onResult(err)
                    if (err == null) showAdd = false
                }
            },
            onAddBatch = { text, onResult -> vm.addNodesFromText(text, onResult) },
            onAddSubscription = { url, onResult -> vm.addNodesFromSubscription(url, onResult) }
        )
    }

    pendingDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除节点", color = InkPrimary) },
            text = { Text("确定删除「${node.name}」吗？", color = InkSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteNode(node)
                    pendingDelete = null
                }) { Text("删除", color = SignalError) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = InkSecondary)
                }
            },
            containerColor = NightSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeTile(
    node: Node,
    testing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTest: () -> Unit
) {
    val accent = latencyColor(node.latencyMs)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NightSurface.copy(alpha = 0.85f))
            .border(1.dp, NightOutline, RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .combinedClickable(onClick = onTest)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when {
                            testing -> "…"
                            node.latencyMs < 0 -> "--"
                            else -> "${node.latencyMs}ms"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
                if (node.isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AuroraGradient)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "使用中",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = node.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = node.protocol.uppercase().ifBlank { "未知协议" },
                fontSize = 11.sp,
                color = InkSecondary
            )
            Spacer(Modifier.height(10.dp))
            LatencyBar(latencyMs = node.latencyMs)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddNodeDialog(
    onDismiss: () -> Unit,
    onAdd: (String, (String?) -> Unit) -> Unit,
    onAddBatch: (String, (SubscriptionManager.ImportResult) -> Unit) -> Unit,
    onAddSubscription: (String, (Result<SubscriptionManager.ImportResult>) -> Unit) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var link by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var doneMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("添加节点", color = InkPrimary) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("链接", "订阅").forEachIndexed { i, label ->
                        val selected = tab == i
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) AuroraCyan.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (selected) AuroraCyan else NightOutline,
                                    RoundedCornerShape(10.dp)
                                )
                                .combinedClickable(onClick = {
                                    tab = i; error = null; doneMsg = null
                                })
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = if (selected) AuroraCyan else InkSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (tab == 0) {
                    Text(
                        text = "粘贴分享链接，每行一个，支持批量",
                        fontSize = 12.sp,
                        color = InkSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it; error = null; doneMsg = null },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("vless://…\nvmess://…", color = InkSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        minLines = 4
                    )
                } else {
                    Text(
                        text = "输入订阅链接，自动拉取全部节点",
                        fontSize = 12.sp,
                        color = InkSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subUrl,
                        onValueChange = { subUrl = it; error = null; doneMsg = null },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://…", color = InkSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        minLines = 2
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = error!!, fontSize = 12.sp, color = SignalError)
                }
                if (doneMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = doneMsg!!, fontSize = 12.sp, color = AuroraCyan)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    doneMsg = null
                    if (tab == 0) {
                        if (link.lines().count { it.trim().contains("://") } <= 1 &&
                            !link.trim().contains("\n")
                        ) {
                            onAdd(link) { err ->
                                busy = false
                                error = err
                                if (err == null) onDismiss()
                            }
                        } else {
                            onAddBatch(link) { res ->
                                busy = false
                                doneMsg = "成功导入 ${res.added} 个" +
                                        if (res.failed > 0) "，${res.failed} 个失败" else ""
                                if (res.errors.isNotEmpty()) error = res.errors.first()
                                if (res.added > 0 && res.failed == 0) onDismiss()
                            }
                        }
                    } else {
                        onAddSubscription(subUrl) { result ->
                            busy = false
                            result.fold(
                                onSuccess = { res ->
                                    doneMsg = "成功导入 ${res.added} 个" +
                                            if (res.failed > 0) "，${res.failed} 个失败" else ""
                                    if (res.errors.isNotEmpty()) error = res.errors.first()
                                    if (res.added > 0 && res.failed == 0) onDismiss()
                                },
                                onFailure = { t -> error = t.message ?: "订阅失败" }
                            )
                        }
                    }
                }
            ) { Text(if (tab == 1) "拉取" else "添加", color = AuroraCyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = InkSecondary) }
        },
        containerColor = NightSurface
    )
}

@Composable
private fun EmptyNodesArt(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "◌", fontSize = 64.sp, color = InkSecondary)
            Text(
                text = "空无一物",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary
            )
            Text(
                text = "点右下角 + 添加第一个节点",
                fontSize = 13.sp,
                color = InkSecondary
            )
        }
    }
}
