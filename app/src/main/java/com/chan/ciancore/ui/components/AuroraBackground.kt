package com.chan.ciancore.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chan.ciancore.ui.theme.AuroraCyan
import com.chan.ciancore.ui.theme.AuroraMagenta
import com.chan.ciancore.ui.theme.AuroraViolet
import com.chan.ciancore.ui.theme.NightCanvas

/**
 * Living aurora background — slow-drifting color nebulae over the
 * night canvas. Gives every screen a sense of depth and motion.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "aurora-drift")
    val offsetX by drift.animateFloat(
        initialValue = -0.15f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftX"
    )
    val offsetY by drift.animateFloat(
        initialValue = 0.1f, targetValue = -0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftY"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NightCanvas)
    ) {
        // Violet nebula, top-left
        Box(
            modifier = Modifier
                .fillMaxSize(0.7f)
                .blur(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AuroraViolet.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = Offset(0.5f + offsetX, 0.28f + offsetY)
                    )
                )
        )
        // Cyan nebula, bottom-right
        Box(
            modifier = Modifier
                .fillMaxSize(0.65f)
                .blur(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AuroraCyan.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(0.75f - offsetX, 0.8f - offsetY)
                    )
                )
        )
        // Magenta whisper, center
        Box(
            modifier = Modifier
                .fillMaxSize(0.5f)
                .blur(140.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AuroraMagenta.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
