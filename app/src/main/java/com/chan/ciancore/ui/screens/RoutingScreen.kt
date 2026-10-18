package com.chan.ciancore.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chan.ciancore.data.RoutePrefs
import com.chan.ciancore.ui.components.AuroraBackground
import com.chan.ciancore.ui.theme.AuroraCyan
import com.chan.ciancore.ui.theme.InkPrimary
import com.chan.ciancore.ui.theme.InkSecondary
import com.chan.ciancore.ui.theme.NightOutline
import com.chan.ciancore.ui.theme.NightSurface
import com.chan.ciancore.ui.theme.SignalError
import com.chan.ciancore.ui.theme.SignalWarn

/**
 * Routing settings, modeled on v2rayNG's routing page.
 *
 * - 域名策略 (domain strategy)
 * - DNS anti-leak system rule (locked, always on)
 * - Rule list: remarks / match summary / outbound tag / edit / switch
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoutingScreen(vm: com.chan.ciancore.ui.MainViewModel) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(RoutePrefs.getRules(context)) }
    var domainStrategy by remember { mutableStateOf(RoutePrefs.getDomainStrategy(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RoutePrefs.RoutingRule?>(null) }
    var showDomainStrategy by remember { mutableStateOf(false) }
    val nodeList by vm.nodes.collectAsState()

    fun saveRules(next: List<RoutePrefs.RoutingRule>) {
        rules = next
        RoutePrefs.setRules(context, next)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 96.dp)
        ) {
            Text(
                text = "路由设置",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = InkPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "决定哪些流量走代理",
                fontSize = 13.sp,
                color = InkSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(12.dp))

            // Domain strategy row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NightSurface.copy(alpha = 0.85f))
                    .border(1.dp, NightOutline, RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = { showDomainStrategy = true })
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "域名策略",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkPrimary
                        )
                        Text(
                            text = domainStrategy.value,
                            fontSize = 12.sp,
                            color = InkSecondary
                        )
                    }
                    Text(text = "›", color = InkSecondary, fontSize = 20.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // DNS anti-leak indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A2B1E).copy(alpha = 0.9f))
                    .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DNS 防泄露 · 始终启用",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF81C784)
                        )
                        Text(
                            text = "全部 DNS 查询经隧道解析，不出本地网络",
                            fontSize = 11.sp,
                            color = InkSecondary
                        )
                    }
                    Text(text = "🔒", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "路由规则设置（${rules.size}）",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleRow(
                        rule = rule,
                        onToggle = { enabled ->
                            saveRules(rules.map {
                                if (it.id == rule.id) it.copy(enabled = enabled) else it
                            })
                        },
                        onEdit = { editing = rule },
                        onDelete = { saveRules(rules - rule) }
                    )
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
            Icon(Icons.Filled.Add, contentDescription = "添加规则")
        }
    }

    if (showDomainStrategy) {
        AlertDialog(
            onDismissRequest = { showDomainStrategy = false },
            title = { Text("域名策略", color = InkPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RoutePrefs.DomainStrategy.entries.forEach { s ->
                        val selected = s == domainStrategy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) AuroraCyan.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .combinedClickable(onClick = {
                                    domainStrategy = s
                                    RoutePrefs.setDomainStrategy(context, s)
                                    showDomainStrategy = false
                                })
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = s.value,
                                fontSize = 14.sp,
                                color = if (selected) AuroraCyan else InkPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) Text(text = "●", color = AuroraCyan, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDomainStrategy = false }) {
                    Text("取消", color = InkSecondary)
                }
            },
            containerColor = NightSurface
        )
    }

    if (showAdd) {
        RuleEditDialog(
            rule = null,
            onDismiss = { showAdd = false },
            onSave = { rule ->
                saveRules(rules + rule)
                showAdd = false
            }
        )
    }

    editing?.let { rule ->
        RuleEditDialog(
            rule = rule,
            onDismiss = { editing = null },
            onSave = { updated ->
                saveRules(rules.map { if (it.id == rule.id) updated else it })
                editing = null
            },
            onDelete = {
                saveRules(rules.filter { it.id != rule.id })
                editing = null
            }
        )
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleRow(
    rule: RoutePrefs.RoutingRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.remarks.ifBlank { "未命名规则" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (rule.enabled) InkPrimary else InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rule.matchSummary(),
                    fontSize = 12.sp,
                    color = InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rule.outboundName(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SignalWarn
                )
            }
            if (!rule.locked) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑",
                    tint = InkSecondary,
                    modifier = Modifier
                        .combinedClickable(onClick = onEdit)
                        .padding(8.dp)
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = if (rule.locked) null else onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFE8DEF8),
                    checkedTrackColor = Color(0xFF7B6D9B)
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NightOutline.copy(alpha = 0.5f))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleEditDialog(
    rule: RoutePrefs.RoutingRule?,
    onDismiss: () -> Unit,
    onSave: (RoutePrefs.RoutingRule) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isNew = rule == null
    var remarks by remember { mutableStateOf(rule?.remarks ?: "") }
    var outboundTag by remember { mutableStateOf(rule?.outboundTag ?: "proxy") }
    var domains by remember { mutableStateOf(rule?.domains?.joinToString("\n") ?: "") }
    var ips by remember { mutableStateOf(rule?.ips?.joinToString("\n") ?: "") }
    var port by remember { mutableStateOf(rule?.port ?: "") }
    var network by remember { mutableStateOf(rule?.network ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加规则" else "编辑规则", color = InkPrimary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it; error = null },
                    label = { Text("备注", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "出站", fontSize = 12.sp, color = InkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("proxy" to "proxy", "direct" to "direct", "block" to "block")
                        .forEach { (tag, label) ->
                            ChipOption(
                                label = label,
                                selected = outboundTag == tag,
                                onClick = { outboundTag = tag }
                            )
                        }
                }
                OutlinedTextField(
                    value = domains,
                    onValueChange = { domains = it },
                    label = { Text("域名（每行一个，支持 geosite:）", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                OutlinedTextField(
                    value = ips,
                    onValueChange = { ips = it },
                    label = { Text("IP（每行一个，支持 geoip: / CIDR）", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() || c == ',' || c == '-' } },
                        label = { Text("端口（可空）", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(text = "网络", fontSize = 12.sp, color = InkSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to "全部", "tcp" to "TCP", "udp" to "UDP").forEach { (n, label) ->
                        ChipOption(
                            label = label,
                            selected = network == n,
                            onClick = { network = n }
                        )
                    }
                }
                if (error != null) {
                    Text(text = error!!, fontSize = 12.sp, color = SignalError)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val domainList = domains.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                val ipList = ips.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                when {
                    remarks.isBlank() -> error = "备注不能为空"
                    domainList.isEmpty() && ipList.isEmpty() && port.isBlank() ->
                        error = "至少填写一个匹配条件"
                    else -> {
                        val base = rule ?: RoutePrefs.RoutingRule()
                        onSave(
                            base.copy(
                                remarks = remarks.trim(),
                                domains = domainList,
                                ips = ipList,
                                port = port.trim(),
                                network = network,
                                outboundTag = outboundTag,
                                enabled = rule?.enabled ?: true
                            )
                        )
                    }
                }
            }) { Text(if (isNew) "添加" else "保存", color = AuroraCyan) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isNew && rule?.locked == false && onDelete != null) {
                    TextButton(onClick = {
                        if (confirmDelete) {
                            onDelete()
                        } else {
                            confirmDelete = true
                        }
                    }) {
                        Text(
                            if (confirmDelete) "确认删除？" else "删除",
                            color = SignalError
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消", color = InkSecondary) }
            }
        },
        containerColor = NightSurface
    )
}

@Composable
private fun ChipOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AuroraCyan.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) AuroraCyan else NightOutline,
                RoundedCornerShape(10.dp)
            )
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) AuroraCyan else InkSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}




