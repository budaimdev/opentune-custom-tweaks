/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Rediseño 2026 — Material 3 Expressive
//
// Filosofía: un único color de acento alimenta los 5 estilos (en vez de presets
// desconectados por layout). Menos superficie, más variedad real: cada estilo
// se ve genuinamente distinto porque cambia composición y forma, no solo color.
//
// Para agregar un estilo nuevo: 1) entry aquí en LyricsCardStyle, 2) composable
// en LyricsCardLayouts.kt, 3) branch en el dispatcher LyricsCardByLayout.
// ─────────────────────────────────────────────────────────────────────────────

enum class LyricsCardStyle(
    val displayName: String,
    val description: String,
) {
    Tonal(
        displayName = "Tonal",
        description = "Superficie M3 tonal, cálida y sólida",
    ),
    Glass(
        displayName = "Glass",
        description = "Vidrio líquido esmerilado",
    ),
    CoverBloom(
        displayName = "Cover Bloom",
        description = "La carátula protagoniza",
    ),
    Quote(
        displayName = "Quote",
        description = "Tipografía pura, minimal",
    ),
    Aurora(
        displayName = "Aurora",
        description = "Degradado expresivo en movimiento",
    ),
}

/**
 * Proporciones pensadas para compartir en redes.
 * El lado corto siempre mide [LyricsCardBaseSize]; el otro se deriva del ratio,
 * así todos los estilos escalan de forma consistente sin recalcular texto a mano.
 */
enum class LyricsAspectRatio(
    val displayName: String,
    val subtitle: String,
    val widthRatio: Float,
    val heightRatio: Float,
) {
    Square("1:1", "Feed / general", 1f, 1f),
    Portrait("4:5", "Post vertical", 4f, 5f),
    Story("9:16", "Historia / Reel", 9f, 16f),
    Wide("16:9", "Banner / X", 16f, 9f),
}

/**
 * Escala de forma expresiva M3: controla cuán "atrevidas" son las esquinas
 * en toda la tarjeta (contenedor, carátula, chips).
 */
enum class LyricsShapeScale(
    val displayName: String,
    val corner: Dp,
) {
    Soft("Soft", 14.dp),
    Rounded("Rounded", 26.dp),
    Bold("Bold", 38.dp),
    Full("Full", 54.dp),
}

/** Solo lo consume el estilo [LyricsCardStyle.Glass]. */
enum class LyricsGlassIntensity(
    val displayName: String,
    val cloudyRadius: Int,
    val refraction: Float,
    val curve: Float,
    val tintAlpha: Float,
    val dimAlpha: Float,
) {
    Soft("Soft", 10, 0.14f, 0.14f, 0.20f, 0.22f),
    Medium("Medium", 16, 0.20f, 0.20f, 0.32f, 0.32f),
    Deep("Deep", 24, 0.25f, 0.25f, 0.46f, 0.44f),
}

internal val LyricsCardBaseSize = 340.dp

/** Tamaño final de la tarjeta (lado corto fijo, lado largo derivado del ratio). */
fun lyricsCardSize(ratio: LyricsAspectRatio): DpSize {
    val short = LyricsCardBaseSize
    return if (ratio.widthRatio <= ratio.heightRatio) {
        DpSize(short, short * (ratio.heightRatio / ratio.widthRatio))
    } else {
        DpSize(short * (ratio.widthRatio / ratio.heightRatio), short)
    }
}

/**
 * Forma "expresiva" asimétrica: dos esquinas grandes, dos más contenidas.
 * Da personalidad sin necesitar geometría de blob compleja.
 */
fun expressiveShape(scale: LyricsShapeScale): Shape = RoundedCornerShape(
    topStart = scale.corner,
    topEnd = scale.corner * 0.42f,
    bottomEnd = scale.corner,
    bottomStart = scale.corner * 0.42f,
)

// ─────────────────────────────────────────────────────────────────────────────
// LyricsCardConfig — estado inmutable del usuario.
// Modifica con .copy(...) para aplicar cambios sin mutación.
// ─────────────────────────────────────────────────────────────────────────────

data class LyricsCardConfig(

    /** Qué template visual se renderiza */
    val style: LyricsCardStyle = LyricsCardStyle.Tonal,

    /** Color de acento único; alimenta los 5 estilos */
    val accent: LyricsAccent = LyricsAccents.Indigo,

    /** Proporción de salida, pensada para compartir */
    val aspectRatio: LyricsAspectRatio = LyricsAspectRatio.Square,

    /** Cuán atrevidas son las esquinas en toda la tarjeta */
    val shapeScale: LyricsShapeScale = LyricsShapeScale.Bold,

    /** Solo aplica cuando style == Glass */
    val glassIntensity: LyricsGlassIntensity = LyricsGlassIntensity.Medium,

    /**
     * Multiplicador sobre el tamaño de fuente calculado automáticamente.
     * Rango recomendado: 0.6f – 1.5f
     */
    val textSizeMultiplier: Float = 1f,

    /** Alineación del bloque de letra */
    val textAlign: TextAlign = TextAlign.Start,

    /** Visibilidad de elementos dentro de la tarjeta */
    val showTitle: Boolean = true,
    val showArtist: Boolean = true,
    val showCoverArt: Boolean = true,
    val showBranding: Boolean = true,

    /**
     * Padding interno de la tarjeta.
     * Rango recomendado: 12.dp – 36.dp
     */
    val cardPadding: Dp = 24.dp,
)
