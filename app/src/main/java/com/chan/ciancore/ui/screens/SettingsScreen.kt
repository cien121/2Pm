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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chan.ciancore.ui.components.AuroraBackground
import com.chan.ciancore.ui.theme.AuroraCyan
import com.chan.ciancore.ui.theme.InkPrimary
import com.chan.ciancore.ui.theme.InkSecondary
import com.chan.ciancore.ui.theme.NightOutline
import com.chan.ciancore.ui.theme.NightSurface
import com.chan.ciancore.core.CoreRegistry
import com.chan.ciancore.core.CoreType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings — quiet control room.
 *
 * Grouped glass sections, minimal chrome. Skeleton toggles only;
 * real preferences land with the feature work.
 */
@Composable
fun SettingsScreen(onNavigate: (String) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ipv6 by remember { mutableStateOf(com.chan.ciancore.data.DnsPrefs.run { context.ipv6Enabled }) }
    var autoConnect by remember { mutableStateOf(false) }
    var geoStatus by remember { mutableStateOf<String?>(null) }
    var geoBusy by remember { mutableStateOf(false) }
    var geoProgress by remember { mutableStateOf<String?>(null) }
    // Stream tuning (Mux / Fragment / Local DNS)
    var muxEnabled by remember { mutableStateOf(com.chan.ciancore.data.StreamPrefs.isMuxEnabled(context)) }
    var fragmentEnabled by remember { mutableStateOf(com.chan.ciancore.data.StreamPrefs.isFragmentEnabled(context)) }

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
                text = "设置",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = InkPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "网络") {
                SettingsToggle(
                    title = "IPv6",
                    subtitle = "为 VPN 接口启用 IPv6",
                    checked = ipv6,
                    onCheckedChange = {
                        ipv6 = it
                        com.chan.ciancore.data.DnsPrefs.run { context.ipv6Enabled = it }
                    }
                )
                SettingsToggle(
                    title = "开机自动连接",
                    subtitle = "设备启动后自动建立隧道",
                    checked = autoConnect,
                    onCheckedChange = { autoConnect = it }
                )
            }

            SettingsGroup(title = "传输优化") {
                SettingsToggle(
                    title = "Mux 多路复用",
                    subtitle = "单连接复用多路流，降低延迟（默认并发 8）",
                    checked = muxEnabled,
                    onCheckedChange = {
                        muxEnabled = it
                        com.chan.ciancore.data.StreamPrefs.setMuxEnabled(context, it)
                    }
                )
                SettingsToggle(
                    title = "分片 (Fragment)",
                    subtitle = "切分 TLS 握手，规避 SNI 探测",
                    checked = fragmentEnabled,
                    onCheckedChange = {
                        fragmentEnabled = it
                        com.chan.ciancore.data.StreamPrefs.setFragmentEnabled(context, it)
                    }
                )
            }

            SettingsGroup(title = "分流") {
                SettingsRow(
                    title = "分应用",
                    value = "按应用控制代理",
                    onClick = { onNavigate("apps") }
                )
                SettingsRow(
                    title = "路由规则",
                    value = "${com.chan.ciancore.data.RoutePrefs.getRules(context).count { it.enabled }} 条启用中",
                    onClick = { onNavigate("routing") }
                )
                SettingsRow(
                    title = "DNS 与网络",
                    value = "DNS / 测速 / VPN 参数",
                    onClick = { onNavigate("dns") }
                )
                SettingsRow(
                    title = "Geo 资源",
                    value = if (geoBusy) (geoProgress ?: "更新中…") else (geoStatus ?: "已内置，点击更新"),
                    onClick = {
                        if (geoBusy) return@SettingsRow
                        geoBusy = true
                        geoStatus = null
                        geoProgress = "准备下载…"
                        CoroutineScope(Dispatchers.Main).launch {
                            val err = withContext(Dispatchers.IO) {
                                com.chan.ciancore.data.GeoManager.update(context) { label, done, total ->
                                    CoroutineScope(Dispatchers.Main).launch {
                                        geoProgress = if (total > 0) {
                                            "$label ${(done * 100 / total).toInt()}%"
                                        } else {
                                            "$label ${done / 1024}KB"
                                        }
                                    }
                                }
                            }
                            geoBusy = false
                            geoProgress = null
                            geoStatus = err ?: "已更新到最新"
                        }
                    }
                )
            }

            SettingsGroup(title = "核心") {
                SettingsRow(
                    title = "Xray-core 版本",
                    value = "v26.10.10",
                    onClick = {}
                )
                SettingsRow(
                    title = "sing-box 版本",
                    value = "v1.14.3",
                    onClick = {}
                )
                CoreSelectorRow()
            }

            SettingsGroup(title = "关于") {
                SettingsRow(title = "版本", value = "0.3.0", onClick = {})
                SettingsRow(title = "开源许可", value = "GPL-3.0", onClick = {})
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
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
private fun SettingsToggle(
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
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    icon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = InkPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, fontSize = 13.sp, color = InkSecondary)
        Spacer(Modifier.width(4.dp))
        Icon(icon, contentDescription = null, tint = InkSecondary)
    }
}

/**
 * Core engine selector — Xray / sing-box segmented control.
 * Sets the default core for new connections; per-node override
 * lives on the Node itself.
 */
@Composable
private fun CoreSelectorRow() {
    val active by CoreRegistry.activeType.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "默认核心",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = InkPrimary
        )
        Text(
            text = "新连接使用的代理引擎",
            fontSize = 12.sp,
            color = InkSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NightOutline.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CoreType.entries.forEach { type ->
                val selected = type == active
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) AuroraCyan.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (selected) AuroraCyan.copy(alpha = 0.6f)
                            else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { CoreRegistry.setActive(type) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = type.displayName,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) AuroraCyan else InkSecondary
                    )
                }
            }
        }
    }
}
