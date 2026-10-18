package com.chan.ciancore.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chan.ciancore.data.DnsPrefs
import com.chan.ciancore.data.DnsPrefs.bypassLan
import com.chan.ciancore.data.DnsPrefs.concurrentTests
import com.chan.ciancore.data.DnsPrefs.directDns
import com.chan.ciancore.data.DnsPrefs.fakeDns
import com.chan.ciancore.data.DnsPrefs.ipInfoUrl
import com.chan.ciancore.data.DnsPrefs.localDns
import com.chan.ciancore.data.DnsPrefs.remoteDns
import com.chan.ciancore.data.DnsPrefs.testUrl
import com.chan.ciancore.data.DnsPrefs.vpnAddress
import com.chan.ciancore.data.DnsPrefs.vpnDns
import com.chan.ciancore.data.DnsPrefs.vpnMtu
import com.chan.ciancore.ui.components.AuroraBackground
import com.chan.ciancore.ui.theme.AuroraCyan
import com.chan.ciancore.ui.theme.InkPrimary
import com.chan.ciancore.ui.theme.InkSecondary
import com.chan.ciancore.ui.theme.NightOutline
import com.chan.ciancore.ui.theme.NightSurface

/**
 * DNS & network tuning: local DNS, FakeDNS, VPN DNS servers,
 * latency test URL/concurrency, IP info URL, remote/direct DNS,
 * LAN bypass, interface address and MTU.
 */
@Composable
fun DnsScreen() {
    val context = LocalContext.current
    // Snapshot prefs into state; write back on change.
    var localDns by remember { mutableStateOf(context.localDns) }
    var fakeDns by remember { mutableStateOf(context.fakeDns) }
    var bypassLan by remember { mutableStateOf(context.bypassLan) }
    var vpnDns by remember { mutableStateOf(context.vpnDns) }
    var testUrl by remember { mutableStateOf(context.testUrl) }
    var concurrent by remember { mutableStateOf(context.concurrentTests.toString()) }
    var ipInfoUrl by remember { mutableStateOf(context.ipInfoUrl) }
    var remoteDns by remember { mutableStateOf(context.remoteDns) }
    var directDns by remember { mutableStateOf(context.directDns) }
    var vpnAddress by remember { mutableStateOf(context.vpnAddress) }
    var vpnMtu by remember { mutableStateOf(context.vpnMtu.toString()) }
    var editing by remember { mutableStateOf<TextFieldTarget?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 112.dp)
        ) {
            Text(
                text = "DNS 与网络",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = InkPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "修改后下次连接生效",
                fontSize = 13.sp,
                color = InkSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(12.dp))

            DnsGroup(title = "DNS") {
                DnsToggle(
                    title = "启用本地 DNS",
                    subtitle = "DNS 由核心的 DNS 模块处理（绕过局域网/大陆路由时建议启用）",
                    checked = localDns,
                    onCheckedChange = { localDns = it; context.localDns = it }
                )
                DnsToggle(
                    title = "启用伪造 DNS（FakeDNS）",
                    subtitle = "解析域名时返回虚构 IP，速度更快，但可能不适用于某些应用",
                    checked = fakeDns,
                    onCheckedChange = { fakeDns = it; context.fakeDns = it }
                )
                DnsRow(
                    title = "VPN DNS",
                    subtitle = "仅支持 IPv4/IPv6，逗号分隔",
                    value = vpnDns,
                    onClick = {
                        editing = TextFieldTarget(
                            "VPN DNS", vpnDns, "1.1.1.1,8.8.8.8"
                        ) { vpnDns = it; context.vpnDns = it }
                    }
                )
                DnsRow(
                    title = "远程 DNS",
                    subtitle = "UDP/TCP/HTTPS/QUIC，可选",
                    value = remoteDns,
                    onClick = {
                        editing = TextFieldTarget(
                            "远程 DNS", remoteDns, DnsPrefs.DEFAULT_REMOTE_DNS
                        ) { remoteDns = it; context.remoteDns = it }
                    }
                )
                DnsRow(
                    title = "直连 DNS",
                    subtitle = "直连流量使用，可选",
                    value = directDns,
                    onClick = {
                        editing = TextFieldTarget(
                            "直连 DNS", directDns, DnsPrefs.DEFAULT_DIRECT_DNS
                        ) { directDns = it; context.directDns = it }
                    }
                )
            }

            DnsGroup(title = "延迟测试") {
                DnsRow(
                    title = "真连接延迟测试 URL",
                    value = testUrl,
                    onClick = {
                        editing = TextFieldTarget(
                            "真连接延迟测试 URL", testUrl, DnsPrefs.DEFAULT_TEST_URL
                        ) { testUrl = it; context.testUrl = it }
                    }
                )
                DnsRow(
                    title = "真连接延迟并发测试数",
                    value = concurrent,
                    onClick = {
                        editing = TextFieldTarget(
                            "并发测试数", concurrent, "8", numeric = true
                        ) {
                            concurrent = it
                            it.toIntOrNull()?.let { n -> context.concurrentTests = n }
                        }
                    }
                )
                DnsRow(
                    title = "当前连接信息测试网址",
                    subtitle = "用于显示出口 IP",
                    value = ipInfoUrl,
                    onClick = {
                        editing = TextFieldTarget(
                            "连接信息测试网址", ipInfoUrl, DnsPrefs.DEFAULT_IP_INFO_URL
                        ) { ipInfoUrl = it; context.ipInfoUrl = it }
                    }
                )
            }

            DnsGroup(title = "VPN") {
                DnsToggle(
                    title = "VPN 是否绕过局域网",
                    subtitle = "局域网流量不走代理",
                    checked = bypassLan,
                    onCheckedChange = { bypassLan = it; context.bypassLan = it }
                )
                DnsRow(
                    title = "VPN 接口地址",
                    value = vpnAddress,
                    onClick = {
                        editing = TextFieldTarget(
                            "VPN 接口地址", vpnAddress, "10.8.0.2"
                        ) { vpnAddress = it; context.vpnAddress = it }
                    }
                )
                DnsRow(
                    title = "VPN MTU",
                    subtitle = "默认 1500",
                    value = vpnMtu,
                    onClick = {
                        editing = TextFieldTarget(
                            "VPN MTU", vpnMtu, "1500", numeric = true
                        ) {
                            vpnMtu = it
                            it.toIntOrNull()?.let { n -> context.vpnMtu = n }
                        }
                    }
                )
            }
        }
    }

    editing?.let { target ->
        var text by remember(target) { mutableStateOf(target.current) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(target.title, color = InkPrimary) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(target.hint, color = InkSecondary) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    target.onSave(text.trim())
                    editing = null
                }) { Text("保存", color = AuroraCyan) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text("取消", color = InkSecondary)
                }
            },
            containerColor = NightSurface
        )
    }
}

private data class TextFieldTarget(
    val title: String,
    val current: String,
    val hint: String,
    val numeric: Boolean = false,
    val onSave: (String) -> Unit
)

@Composable
private fun DnsGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = InkSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(NightSurface.copy(alpha = 0.85f))
                .border(1.dp, NightOutline, RoundedCornerShape(20.dp))
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun DnsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = InkPrimary)
            Text(text = subtitle, fontSize = 12.sp, color = InkSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AuroraCyan)
        )
    }
}

@Composable
private fun DnsRow(
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = InkPrimary
        )
        if (subtitle != null) {
            Text(text = subtitle, fontSize = 12.sp, color = InkSecondary)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = value.ifBlank { "未设置" },
            fontSize = 13.sp,
            color = AuroraCyan
        )
    }
}
