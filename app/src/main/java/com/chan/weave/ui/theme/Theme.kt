package com.chan.weave.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Weave design system — "Deep Sea".
 *
 * Deep ocean-blue canvas, sea-blue accents. Dark-first.
 */

// ── Palette ──────────────────────────────────────────────────────────
val NightCanvas = Color(0xFF071120)      // app background (deep sea)
val NightSurface = Color(0xFF0E1E33)    // cards
val NightSurfaceHigh = Color(0xFF15294A)
val NightOutline = Color(0x26FFFFFF)    // 15% white hairlines

val AuroraCyan = Color(0xFF38BDF8)      // primary: sky blue
val AuroraViolet = Color(0xFF0EA5E9)    // secondary: ocean blue
val AuroraMagenta = Color(0xFF22D3EE)   // tertiary: aqua

val InkPrimary = Color(0xFFE8F1FA)
val InkSecondary = Color(0xFF8BA3BD)
val InkTertiary = Color(0xFF5A6F8A)

val SignalUp = Color(0xFF34D399)
val SignalDown = Color(0xFF60A5FA)
val SignalWarn = Color(0xFFFFB545)
val SignalError = Color(0xFFFF6B6B)

// ── Gradients ────────────────────────────────────────────────────────
val AuroraGradient = Brush.linearGradient(
    listOf(AuroraCyan, AuroraViolet, AuroraMagenta)
)
val AuroraGradientVertical = Brush.verticalGradient(
    listOf(AuroraCyan.copy(alpha = 0.9f), AuroraViolet.copy(alpha = 0.9f))
)
val HeroGlow = Brush.radialGradient(
    listOf(
        AuroraViolet.copy(alpha = 0.35f),
        AuroraCyan.copy(alpha = 0.12f),
        Color.Transparent
    )
)

// ── Latency color ramp (shared) ──────────────────────────────────────
fun latencyColor(ms: Long): Color = when {
    ms < 0 -> InkTertiary
    ms < 300 -> SignalUp
    ms < 800 -> SignalWarn
    else -> SignalError
}

// ── Material3 scheme ─────────────────────────────────────────────────
private val CianCoreDarkScheme = darkColorScheme(
    primary = AuroraCyan,
    onPrimary = Color(0xFF06231F),
    primaryContainer = AuroraViolet,
    onPrimaryContainer = Color.White,
    secondary = AuroraViolet,
    tertiary = AuroraMagenta,
    background = NightCanvas,
    onBackground = InkPrimary,
    surface = NightSurface,
    onSurface = InkPrimary,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = InkSecondary,
    outline = NightOutline,
    outlineVariant = Color(0x14FFFFFF),
    error = SignalError
)

@Composable
fun CianCoreTheme(content: @Composable () -> Unit) {
    // Force dark — the design system is dark-first.
    MaterialTheme(
        colorScheme = CianCoreDarkScheme,
        content = content
    )
    // Suppress unused-parameter warning for future light-theme support.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
}
