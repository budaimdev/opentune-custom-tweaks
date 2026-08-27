/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.component

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

// ─────────────────────────────────────────────────────────────────────────────
// LyricsAccent — un único color "semilla" que todos los estilos derivan.
//
// En vez de mantener 5 presets desconectados por layout (como antes), cada
// estilo calcula sus propios tonos (contenedor, texto, overlay) a partir de
// este color, usando las funciones de mezcla más abajo. Resultado: el mismo
// acento se ve coherente sin importar qué LyricsCardStyle esté activo.
// ─────────────────────────────────────────────────────────────────────────────

data class LyricsAccent(
    val name: String,
    val seed: Color,
)

object LyricsAccents {

    /** Se reemplaza en runtime por [fromPalette] usando el color dominante de la carátula. */
    val Auto = LyricsAccent("Auto", Color(0xFF6750A4))

    val Indigo = LyricsAccent("Indigo", Color(0xFF6750A4))
    val Ocean = LyricsAccent("Ocean", Color(0xFF2F6FED))
    val Emerald = LyricsAccent("Emerald", Color(0xFF1E8E5A))
    val Coral = LyricsAccent("Coral", Color(0xFFFF6656))
    val Sunset = LyricsAccent("Sunset", Color(0xFFFF9457))
    val Bubblegum = LyricsAccent("Bubblegum", Color(0xFFFF4FA0))
    val Ink = LyricsAccent("Ink", Color(0xFF2B2B33))
    val Paper = LyricsAccent("Paper", Color(0xFFD8CBB0))

    val all = listOf(Auto, Indigo, Ocean, Emerald, Coral, Sunset, Bubblegum, Ink, Paper)

    /** Extrae un acento a partir de la carátula del track (Palette de androidx.palette). */
    fun fromPalette(palette: Palette): LyricsAccent {
        val swatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.mutedSwatch
        val color = swatch?.let { Color(it.rgb) } ?: Indigo.seed
        return LyricsAccent("Auto", color)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilidades tonales — mezclas simples, sin dependencias extra.
// Cada estilo las usa para construir su propia paleta a partir de accent.seed.
// ─────────────────────────────────────────────────────────────────────────────

/** Interpola linealmente hacia [other]; útil para construir tonos contenedor. */
fun Color.mixWith(other: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * t,
        green = green + (other.green - green) * t,
        blue = blue + (other.blue - blue) * t,
        alpha = 1f,
    )
}

/** Rota el matiz N grados mantenimiento saturación/brillo — genera acentos secundarios. */
fun Color.shiftHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255f).toInt().coerceIn(0, 255),
        (green * 255f).toInt().coerceIn(0, 255),
        (blue * 255f).toInt().coerceIn(0, 255),
        hsv,
    )
    hsv[0] = (hsv[0] + degrees).mod(360f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Luminancia perceptual aproximada — decide si el texto encima debe ser claro u oscuro. */
fun Color.isPerceptuallyLight(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) > 0.62f

/** Color de texto legible sobre este color (blanco u oscuro casi negro). */
fun Color.readableTextColor(): Color =
    if (isPerceptuallyLight()) Color(0xFF201A14) else Color.White

/** Tono "contenedor" M3-like: pastel claro (mezcla con blanco) a partir de la semilla. */
fun Color.tonalContainer(amount: Float = 0.88f): Color = mixWith(Color.White, amount)

/** Tono "sobre-contenedor": versión oscura y saturada de la semilla, para texto/íconos. */
fun Color.onTonalContainer(amount: Float = 0.55f): Color = mixWith(Color.Black, amount)
