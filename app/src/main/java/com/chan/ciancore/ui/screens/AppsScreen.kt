package com.chan.ciancore.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.chan.ciancore.data.PerAppPrefs
import com.chan.ciancore.ui.components.AuroraBackground
import com.chan.ciancore.ui.theme.AuroraCyan
import com.chan.ciancore.ui.theme.InkPrimary
import com.chan.ciancore.ui.theme.InkSecondary
import com.chan.ciancore.ui.theme.NightOutline
import com.chan.ciancore.ui.theme.NightSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

/** Apps that typically need the proxy (allowlist auto-select). */
private val PROXY_PRESET = setOf(
    "com.google.android.youtube",
    "com.android.chrome",
    "org.telegram.messenger",
    "com.twitter.android",
    "com.facebook.katana",
    "com.instagram.android",
    "com.google.android.gm",
    "com.google.android.apps.maps",
    "com.android.vending",
    "com.netflix.mediaclient",
    "com.spotify.music",
    "com.discord",
    "com.reddit.frontpage",
    "com.zhiliaoapp.musically",
    "com.google.android.apps.docs",
    "com.microsoft.bing",
    "com.openai.chatgpt"
)

/** Apps that typically must bypass the proxy (blocklist auto-select). */
private val BYPASS_PRESET = setOf(
    "com.eg.android.AlipayGphone", // 支付宝
    "com.tencent.mm", // 微信
    "com.unionpay", // 云闪付
    "com.icbc", // 工商银行
    "com.chinamworld.main", // 建设银行
    "com.abchina", // 农业银行
    "com.chinamworld.bocmbci", // 中国银行
    "cmbandroid", // 招商银行
    "com.bankcomm.Bankcomm", // 交通银行
    "com.psbc", // 邮储银行
    "com.cmbc.ccd.plat", // 民生银行
    "com.pingan.pabank", // 平安银行
    "com.cib.cibmb", // 兴业银行
    "com.spdbbank", // 浦发银行
    "com.bankofbeijing", // 北京银行
    "com.shbank", // 上海银行
    "com.gdbank", // 广发银行
    "com.cebbank", // 光大银行
    "com.citicbank", // 中信银行
    "com.hxb", // 华夏银行
    "com.cmbchina.ccd.plat.cmbApp", // 招商银行新版
    "com.icbc.activity" // 工商银行备用
)

/**
 * Per-app VPN routing, v2rayNG-style list.
 *
 * - 启用分应用 off → ALL (everything through the tunnel).
 * - on + 绕行模式 on → BLOCKLIST (checked apps bypass).
 * - on + 绕行模式 off → ALLOWLIST (only checked apps proxied).
 * - 自动选择： one-tap preset selection based on the current mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember {
        mutableStateOf(PerAppPrefs.getMode(context) != PerAppPrefs.Mode.ALL)
    }
    var bypass by remember {
        mutableStateOf(PerAppPrefs.getMode(context) == PerAppPrefs.Mode.BLOCKLIST)
    }
    var selected by remember { mutableStateOf(PerAppPrefs.getPackages(context)) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
        loading = false
    }

    fun persistMode() {
        val m = if (!enabled) PerAppPrefs.Mode.ALL
        else if (bypass) PerAppPrefs.Mode.BLOCKLIST
        else PerAppPrefs.Mode.ALLOWLIST
        PerAppPrefs.setMode(context, m)
    }

    fun toggle(pkg: String, on: Boolean) {
        val next = selected.toMutableSet()
        if (on) next.add(pkg) else next.remove(pkg)
        selected = next
        PerAppPrefs.togglePackage(context, pkg, on)
    }

    fun autoSelect() {
        val preset = if (bypass) BYPASS_PRESET else PROXY_PRESET
        val installed = apps.map { it.packageName }.toSet()
        val matched = preset.intersect(installed) - selected
        if (matched.isEmpty()) {
            Toast.makeText(context, "没有发现可自动选择的应用", Toast.LENGTH_SHORT).show()
        } else {
            val next = selected + matched
            selected = next
            PerAppPrefs.setPackages(context, next)
            Toast.makeText(context, "已自动勾选 ${matched.size} 个应用", Toast.LENGTH_SHORT).show()
        }
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = InkPrimary
                    )
                }
                Text(
                    text = "分应用设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) query = ""
                }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索", tint = InkPrimary)
                }
            }

            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("搜索应用名或包名", color = InkSecondary) },
                    singleLine = true
                )
            }

            // Control row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "启用分应用", fontSize = 14.sp, color = InkPrimary)
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        persistMode()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = AuroraCyan)
                )
                Spacer(Modifier.weight(1f))
                Text(text = "绕行模式", fontSize = 14.sp, color = InkPrimary)
                Switch(
                    checked = bypass,
                    onCheckedChange = {
                        bypass = it
                        persistMode()
                    },
                    enabled = enabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = AuroraCyan)
                )
                IconButton(
                    onClick = { showInfo = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = "说明", tint = InkSecondary)
                }
            }

            // Auto-select
            if (enabled) {
                OutlinedButton(
                    onClick = { autoSelect() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (bypass) "✨ 自动勾选需绕行的应用（银行/支付类）"
                        else "✨ 自动勾选需代理的应用",
                        fontSize = 13.sp,
                        color = AuroraCyan
                    )
                }
            }
            HorizontalDivider(color = NightOutline)

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("加载中…", color = InkSecondary, fontSize = 13.sp)
                    }
                }
                filtered.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (query.isBlank()) "没有找到应用" else "没有匹配「$query」的应用",
                            color = InkSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.packageName }) { app ->
                            val on = selected.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(onClick = { toggle(app.packageName, !on) })
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(drawable = app.icon)
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 12.sp,
                                        color = InkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Checkbox(
                                    checked = on,
                                    onCheckedChange = { toggle(app.packageName, it) },
                                    colors = CheckboxDefaults.colors(checkedColor = AuroraCyan)
                                )
                            }
                            HorizontalDivider(
                                color = NightOutline.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 72.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("分应用说明", color = InkPrimary) },
            text = {
                Text(
                    text = "绕行模式开：勾选的应用不走代理（黑名单），适合银行、支付类应用。\n\n绕行模式关：仅勾选的应用走代理（白名单）。\n\n关闭启用分应用：全部应用走代理。",
                    color = InkSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("知道了", color = AuroraCyan)
                }
            },
            containerColor = NightSurface
        )
    }
}

@Composable
private fun AppIcon(drawable: Drawable?) {
    val bitmap = remember(drawable) {
        try {
            drawable?.toBitmap(96, 96)?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NightOutline)
        )
    }
}

private fun loadApps(context: Context): List<AppEntry> {
    val pm: PackageManager = context.packageManager
    return try {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                // Skip self; keep user apps + interesting system apps.
                app.packageName != context.packageName
            }
            .map { app ->
                AppEntry(
                    packageName = app.packageName,
                    label = app.loadLabel(pm)?.toString() ?: app.packageName,
                    icon = try { app.loadIcon(pm) } catch (_: Throwable) { null },
                    isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedWith(
                compareBy({ it.isSystem }, { it.label.lowercase() })
            )
    } catch (_: Throwable) {
        emptyList()
    }
}
