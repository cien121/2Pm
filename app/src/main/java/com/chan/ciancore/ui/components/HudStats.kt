package com.chan.ciancore.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chan.ciancore.ui.theme.AuroraCyan
import com.chan.ciancore.ui.theme.InkPrimary
import com.chan.ciancore.ui.theme.InkSecondary
import com.chan.ciancore.ui.theme.NightOutline
import com.chan.ciancore.ui.theme.NightSurface
import com.chan.ciancore.ui.theme.SignalDown
import com.chan.ciancore.ui.theme.SignalUp
import com.chan.ciancore.ui.theme.latencyColor

/**
 * Floating HUD stat cluster — latency / down / up as glass pills.
 * Numbers animate on change for a "live instrument" feel.
 */
@Composable
fun HudStats(
    latencyMs: Long,
    downBps: Long,
    upBps: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HudPill(
            label = "延迟",
            value = if (latencyMs < 0) "--" else "$latencyMs",
            unit = "ms",
            accent = latencyColor(latencyMs)
        )
        HudPill(
            label = "下行",
            value = formatSpeed(downBps).first,
            unit = formatSpeed(downBps).second,
            accent = SignalDown
        )
        HudPill(
            label = "上行",
            value = formatSpeed(upBps).first,
            unit = formatSpeed(upBps).second,
            accent = SignalUp
        )
    }
}

@Composable
private fun HudPill(
    label: String,
    value: String,
    unit: String,
    accent: Color
) {
    val glow by animateFloatAsState(
        targetValue = 1f, animationSpec = tween(400), label = "pill"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NightSurface.copy(alpha = 0.72f * glow))
            .border(1.dp, NightOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = InkSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Baseline) {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkPrimary
                )
                Spacer(Modifier.width(3.dp))
                Text(text = unit, fontSize = 11.sp, color = accent)
            }
            // accent underline
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accent)
            )
        }
    }
}

private fun formatSpeed(bps: Long): Pair<String, String> {
    if (bps <= 0) return "--" to "B/s"
    val kb = bps / 1024.0
    if (kb < 1024) return String.format("%.1f", kb) to "KB/s"
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.2f", mb) to "MB/s"
    return String.format("%.2f", mb / 1024.0) to "GB/s"
}

/** Tiny latency bar used inside node cards. */
@Composable
fun LatencyBar(latencyMs: Long, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState(
        targetValue = when {
            latencyMs < 0 -> 0f
            latencyMs > 1500 -> 1f
            else -> latencyMs / 1500f
        },
        animationSpec = tween(500),
        label = "latencyFrac"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(frac)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(latencyColor(latencyMs))
        )
    }
}
