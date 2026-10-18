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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

/**
 * Routing rules: presets + custom rules.
 *
 * - 全局代理： everything through the tunnel.
 * - 绕过大陆： geosite:cn / geoip:cn go direct (needs geo dat files).
 * - 自定义规则： user domain/ip rules with proxy/direct/block actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoutingScreen() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(RoutePrefs.getMode(context)) }
    var rules by remember { mutableStateOf(RoutePrefs.getCustomRules(context)) }
    var showAdd by remember { mutableStateOf(false) }

    fun saveRules(next: List<RoutePrefs.CustomRule>) {
        rules = next
        RoutePrefs.setCustomRules(context, next)
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
                text = "路由规则",
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

            // Preset selector
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoutePrefs.Mode.entries.forEach { m ->
                    val active = mode == m
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (active) AuroraCyan.copy(alpha = 0.12f)
                                else NightSurface.copy(alpha = 0.85f)
                            )
                            .border(
                                1.dp,
                                if (active) AuroraCyan.copy(alpha = 0.6f) else NightOutline,
                                RoundedCornerShape(16.dp)
                            )
                            .combinedClickable(onClick = {
                                mode = m
                                RoutePrefs.setMode(context, m)
                            })
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = m.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (active) AuroraCyan else InkPrimary
                                )
                                Text(
                                    text = when (m) {
                                        RoutePrefs.Mode.GLOBAL -> "全部流量走代理"
                                        RoutePrefs.Mode.BYPASS_CN -> "大陆域名/IP 直连，其余走代理"
                                        RoutePrefs.Mode.CUSTOM -> "按下方自定义规则分流"
                                    },
                                    fontSize = 12.sp,
                                    color = InkSecondary
                                )
                            }
                            if (active) {
                                Text(text = "●", color = AuroraCyan, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (mode == RoutePrefs.Mode.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "自定义规则（${rules.size}）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp, vertical = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rules, key = { it.kind + it.value + it.action }) { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NightSurface.copy(alpha = 0.85f))
                                .border(1.dp, NightOutline, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AuroraCyan.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = when (rule.action) {
                                        "direct" -> "直连"
                                        "block" -> "拦截"
                                        else -> "代理"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AuroraCyan
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.value,
                                    fontSize = 14.sp,
                                    color = InkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (rule.kind == "ip") "IP / CIDR" else "域名",
                                    fontSize = 11.sp,
                                    color = InkSecondary
                                )
                            }
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = SignalError,
                                modifier = Modifier.combinedClickable(onClick = {
                                    saveRules(rules - rule)
                                })
                            )
                        }
                    }
                }
            }
        }

        if (mode == RoutePrefs.Mode.CUSTOM) {
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
    }

    if (showAdd) {
        AddRuleDialog(
            onDismiss = { showAdd = false },
            onAdd = { rule ->
                saveRules(rules + rule)
                showAdd = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (RoutePrefs.CustomRule) -> Unit
) {
    var kind by remember { mutableStateOf("domain") }
    var value by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("proxy") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加规则", color = InkPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // kind
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("domain" to "域名", "ip" to "IP/CIDR").forEach { (k, label) ->
                        ChipOption(
                            label = label,
                            selected = kind == k,
                            onClick = { kind = k }
                        )
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (kind == "domain") "example.com" else "1.2.3.0/24",
                            color = InkSecondary
                        )
                    },
                    singleLine = true
                )
                // action
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "proxy" to "走代理",
                        "direct" to "直连",
                        "block" to "拦截"
                    ).forEach { (a, label) ->
                        ChipOption(
                            label = label,
                            selected = action == a,
                            onClick = { action = a }
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
                val v = value.trim()
                if (v.isEmpty()) {
                    error = "请输入域名或 IP"
                } else {
                    onAdd(RoutePrefs.CustomRule(kind, v, action))
                }
            }) { Text("添加", color = AuroraCyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = InkSecondary) }
        },
        containerColor = NightSurface
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChipOption(label: String, selected: Boolean, onClick: () -> Unit) {
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
            fontWeight = FontWeight.Medium
        )
    }
}
