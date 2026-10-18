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
@Composable
fun NodesScreen(vm: MainViewModel) {
    val nodeList by vm.nodes.collectAsState()
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
            Text(
                text = "节点",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = InkPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "${nodeList.size} 个信号源 · 长按删除",
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
                            onClick = { vm.selectNode(node) },
                            onLongClick = { pendingDelete = node }
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
            }
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (node.latencyMs < 0) "--" else "${node.latencyMs}ms",
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

@Composable
private fun AddNodeDialog(
    onDismiss: () -> Unit,
    onAdd: (String, (String?) -> Unit) -> Unit
) {
    var link by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("添加节点", color = InkPrimary) },
        text = {
            Column {
                Text(
                    text = "粘贴分享链接（vless / vmess / trojan / ss / hysteria2 / tuic）",
                    fontSize = 12.sp,
                    color = InkSecondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("vless://…", color = InkSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    minLines = 3
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = error!!, fontSize = 12.sp, color = SignalError)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    onAdd(link) { err ->
                        busy = false
                        error = err
                    }
                }
            ) { Text("添加", color = AuroraCyan) }
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
