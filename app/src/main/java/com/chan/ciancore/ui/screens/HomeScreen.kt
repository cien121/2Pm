package com.chan.ciancore.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chan.ciancore.data.Node
import com.chan.ciancore.service.CianCoreVpnService.ConnectionState
import com.chan.ciancore.ui.components.AuroraBackground
import com.chan.ciancore.ui.components.ConnectionOrb
import com.chan.ciancore.ui.components.HudStats
import com.chan.ciancore.ui.components.NodeDeck
import com.chan.ciancore.ui.theme.AuroraCyan
import com.chan.ciancore.ui.theme.InkPrimary
import com.chan.ciancore.ui.theme.InkSecondary
import com.chan.ciancore.ui.theme.SignalUp
import com.chan.ciancore.ui.theme.SignalWarn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Home — the cockpit.
 *
 * Aurora sky, living connection orb in the center, HUD stats floating
 * beneath, and the node deck at the bottom. One tap on the orb is the
 * entire connection UX.
 *
 * NOTE (skeleton): state flows are placeholders; the ViewModel wiring
 * lands with the Xray-core integration.
 */
@Composable
fun HomeScreen(
    onToggleConnection: () -> Unit = {},
    onSelectNode: (Node) -> Unit = {}
) {
    // --- Skeleton state (replace with ViewModel) ---
    val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.IDLE)
    val nodes: StateFlow<List<Node>> = MutableStateFlow(emptyList())
    val selectedId: StateFlow<Long?> = MutableStateFlow(null)
    // ------------------------------------------------

    val state by connectionState.collectAsState()
    val nodeList by nodes.collectAsState()
    val currentSelectedId by selectedId.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand + status line
            Text(
                text = "CianCore",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = InkPrimary,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            StatusLine(state = state)
            Spacer(Modifier.height(28.dp))

            // The orb
            ConnectionOrb(
                state = state,
                onToggle = onToggleConnection
            )
            Spacer(Modifier.height(24.dp))

            // HUD stats
            HudStats(
                latencyMs = -1,
                downBps = 0,
                upBps = 0,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(28.dp))

            // Node deck
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "信号源",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                NodeDeck(
                    nodes = nodeList,
                    selectedId = currentSelectedId,
                    onSelect = onSelectNode
                )
            }
        }
    }
}

@Composable
private fun StatusLine(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.CONNECTED -> "已连接 · 隧道开启" to SignalUp
        ConnectionState.CONNECTING -> "正在建立隧道…" to SignalWarn
        ConnectionState.IDLE -> "轻触核心开始连接" to InkSecondary
    }
    Text(
        text = text,
        fontSize = 13.sp,
        color = color,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Medium
    )
}
