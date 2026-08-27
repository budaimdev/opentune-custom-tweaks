/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.component

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Layout styles
// ─────────────────────────────────────────────────────────────────────────────

enum class LyricsLayoutStyle(
    val displayName: String,
    val description: String,
) {
    GlassCard("Glass Card", "Modern translucent design"),
    Minimal("Minimal", "Focus on simplicity"),
    Poster("Poster", "Bold typography focus"),
    Retro("Retro", "Vintage vinyl aesthetic"),
    Mesh("Mesh Gradient", "Vibrant color mesh"),
    Streaming("Streaming", "Modern music app style"),
    Brutalist("Brutalist", "High contrast and raw"),
    Classic("Classic", "Standard card layout")
}

// ─────────────────────────────────────────────────────────────────────────────
// Aspect Ratio
// ─────────────────────────────────────────────────────────────────────────────

enum class LyricsAspectRatio(val ratio: Float, val displayName: String) {
    Square(1f, "1:1"),
    Portrait(9f / 16f, "9:16"),
    Social(4f / 5f, "4:5"),
    Wide(16f / 9f, "16:9")
}

// ─────────────────────────────────────────────────────────────────────────────
// LyricsCardConfig
// ─────────────────────────────────────────────────────────────────────────────

data class LyricsCardConfig(
    val layoutStyle: LyricsLayoutStyle = LyricsLayoutStyle.GlassCard,
    val glassStyle: LyricsGlassStyle = LyricsGlassStyle.FrostedDark,
    val aspectRatio: LyricsAspectRatio = LyricsAspectRatio.Square,
    val textSizeMultiplier: Float = 1f,
    val textAlign: TextAlign = TextAlign.Center,
    val showTitle: Boolean = true,
    val showArtist: Boolean = true,
    val showCoverArt: Boolean = true,
    val showBranding: Boolean = true,
    val cardPadding: Dp = 24.dp,
)
