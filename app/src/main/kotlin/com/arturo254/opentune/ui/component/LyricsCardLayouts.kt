/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.liquidGlass
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.UseSystemFontKey
import com.arturo254.opentune.models.MediaMetadata
import com.arturo254.opentune.utils.rememberPreference

// ─────────────────────────────────────────────────────────────────────────────
// Helpers internos — reutilizados por todos los estilos
// ─────────────────────────────────────────────────────────────────────────────

/** Fuente de letras respetando la preferencia del usuario */
@Composable
internal fun rememberLyricsFontFamily(): FontFamily? {
    val (useSystemFont) = rememberPreference(UseSystemFontKey, defaultValue = false)
    return remember(useSystemFont) {
        if (useSystemFont) null else FontFamily(Font(R.font.sfprodisplaybold))
    }
}

/** Painter de la carátula con crossfade */
@Composable
internal fun rememberArtworkPainter(thumbnailUrl: String?): Painter =
    rememberAsyncImagePainter(
        ImageRequest.Builder(LocalContext.current)
            .data(thumbnailUrl)
            .crossfade(true)
            .build()
    )

/** Fila de marca OpenTune — aparece en el pie si showBranding=true */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
internal fun LyricsBrandingRow(
    color: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.opentune),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                colorFilter = ColorFilter.tint(
                    if (isDark) Color.Black.copy(alpha = 0.85f)
                    else Color.White.copy(alpha = 0.9f)
                ),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = context.getString(R.string.app_name),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.02.em,
        )
    }
}

/**
 * Fila de metadatos (carátula + título + artista).
 * Respeta los flags showCoverArt / showTitle / showArtist del config.
 */
@Composable
internal fun LyricsMetadataRow(
    mediaMetadata: MediaMetadata,
    artworkPainter: Painter,
    config: LyricsCardConfig,
    mainTextColor: Color,
    secondaryColor: Color,
    coverArtSize: Dp = 56.dp,
    coverArtShape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        if (config.showCoverArt) {
            Image(
                painter = artworkPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(coverArtSize)
                    .clip(coverArtShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), coverArtShape),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            if (config.showTitle) {
                Text(
                    text = mediaMetadata.title,
                    color = mainTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp),
                    style = TextStyle(letterSpacing = (-0.02).em),
                )
            }
            if (config.showArtist) {
                Text(
                    text = mediaMetadata.artists.joinToString { it.name },
                    color = secondaryColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Tamaño de letra inicial heurístico compartido entre estilos, según longitud del texto. */
private fun heuristicInitialSize(length: Int, big: Float, mid: Float, small: Float, tiny: Float): TextUnit =
    when {
        length < 50 -> big.sp
        length < 100 -> mid.sp
        length < 200 -> small.sp
        else -> tiny.sp
    }

// ─────────────────────────────────────────────────────────────────────────────
// Dispatcher — punto de entrada único para cualquier caller
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LyricsCardByLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    when (config.style) {
        LyricsCardStyle.Tonal -> TonalLayout(lyricText, mediaMetadata, config, modifier)
        LyricsCardStyle.Glass -> GlassLayout(lyricText, mediaMetadata, config, modifier)
        LyricsCardStyle.CoverBloom -> CoverBloomLayout(lyricText, mediaMetadata, config, modifier)
        LyricsCardStyle.Quote -> QuoteLayout(lyricText, mediaMetadata, config, modifier)
        LyricsCardStyle.Aurora -> AuroraLayout(lyricText, mediaMetadata, config, modifier)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Estilo 1 — Tonal
// Superficie M3 tonal derivada del acento, un blob decorativo suave, tipografía
// extra bold. El estilo "por defecto": sólido, legible, sin efectos costosos.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun TonalLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontFamily = rememberLyricsFontFamily()
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)
    val cardSize = lyricsCardSize(config.aspectRatio)
    val shape = expressiveShape(config.shapeScale)

    val seed = config.accent.seed
    val bgColor = seed.tonalContainer(0.90f)
    val mainText = seed.onTonalContainer(0.62f)
    val secondaryText = mainText.copy(alpha = 0.64f)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(cardSize.width, cardSize.height)
                .clip(shape)
                .background(bgColor),
        ) {
            // Blob decorativo — esquina superior derecha
            Box(
                modifier = Modifier
                    .size(cardSize.width * 0.7f)
                    .align(Alignment.TopEnd)
                    .offset(x = cardSize.width * 0.28f, y = -cardSize.width * 0.22f)
                    .clip(CircleShape)
                    .background(seed.copy(alpha = 0.16f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(config.cardPadding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                LyricsMetadataRow(
                    mediaMetadata = mediaMetadata,
                    artworkPainter = artworkPainter,
                    config = config,
                    mainTextColor = mainText,
                    secondaryColor = secondaryText,
                    coverArtShape = RoundedCornerShape(config.shapeScale.corner * 0.42f),
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val textStyle = TextStyle(
                        color = mainText,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = config.textAlign,
                        letterSpacing = (-0.02).em,
                        fontFamily = fontFamily,
                    )
                    val initialSize = heuristicInitialSize(lyricText.length, 24f, 20f, 17f, 13f)
                    val dynamicFontSize = rememberAdjustedFontSize(
                        text = lyricText,
                        maxWidth = maxWidth - 6.dp,
                        maxHeight = maxHeight - 6.dp,
                        density = density,
                        initialFontSize = (initialSize.value * config.textSizeMultiplier).sp,
                        minFontSize = 11.sp,
                        style = textStyle,
                        textMeasurer = rememberTextMeasurer(),
                    )
                    Text(
                        text = lyricText,
                        style = textStyle.copy(
                            fontSize = dynamicFontSize,
                            lineHeight = dynamicFontSize.value.sp * 1.28f,
                        ),
                        overflow = TextOverflow.Ellipsis,
                        textAlign = config.textAlign,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (config.showBranding) {
                    // Chip tonal — no una fila plana, un pill con superficie propia
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(seed.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        LyricsBrandingRow(color = mainText, isDark = false)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Estilo 2 — Glass
// Vidrio líquido esmerilado sobre la carátula desenfocada. El acento tiñe el
// panel; la intensidad (Soft/Medium/Deep) controla blur y opacidad.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun GlassLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontFamily = rememberLyricsFontFamily()
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)
    val cardSize = lyricsCardSize(config.aspectRatio)
    val shape = expressiveShape(config.shapeScale)
    val intensity = config.glassIntensity
    val seed = config.accent.seed

    val mainTextColor = Color.White
    val secondaryColor = Color.White.copy(alpha = 0.72f)

    var glassComponentSize by remember { mutableStateOf(Size.Zero) }
    val lensCenter = remember(glassComponentSize) {
        Offset(glassComponentSize.width / 2f, glassComponentSize.height / 2f)
    }
    val lensSize = remember(glassComponentSize) {
        Size(glassComponentSize.width, glassComponentSize.height)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(cardSize.width, cardSize.height)
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            // Fondo desenfocado
            Image(
                painter = artworkPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().cloudy(radius = intensity.cloudyRadius),
            )
            // Gradiente oscurecedor
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = intensity.dimAlpha * 0.8f),
                            Color.Black.copy(alpha = intensity.dimAlpha),
                            Color.Black.copy(alpha = intensity.dimAlpha * 1.25f),
                        )
                    )
                )
            )

            // Panel de vidrio líquido
            Box(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxSize()
                    .onSizeChanged { glassComponentSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .clip(RoundedCornerShape(config.shapeScale.corner * 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .cloudy(radius = intensity.cloudyRadius)
                        .then(
                            if (glassComponentSize.width > 0f && glassComponentSize.height > 0f) {
                                Modifier.liquidGlass(
                                    lensCenter = lensCenter,
                                    lensSize = lensSize,
                                    cornerRadius = config.shapeScale.corner.value * 0.6f,
                                    refraction = intensity.refraction,
                                    curve = intensity.curve,
                                    dispersion = 0f,
                                    saturation = 1.10f,
                                    contrast = 1.0f,
                                    tint = seed.copy(alpha = 0.16f),
                                    edge = 0.2f,
                                )
                            } else Modifier
                        )
                        .drawWithContent {
                            drawContent()
                            drawRect(seed.copy(alpha = intensity.tintAlpha * 0.55f))
                            drawRect(Color.Black.copy(alpha = intensity.tintAlpha * 0.5f))
                        }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(config.cardPadding),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    LyricsMetadataRow(mediaMetadata, artworkPainter, config, mainTextColor, secondaryColor)

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val textStyle = TextStyle(
                            color = mainTextColor,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = config.textAlign,
                            letterSpacing = (-0.01).em,
                            fontFamily = fontFamily,
                        )
                        val initialSize = heuristicInitialSize(lyricText.length, 22f, 19f, 16f, 13f)
                        val dynamicFontSize = rememberAdjustedFontSize(
                            text = lyricText,
                            maxWidth = maxWidth - 6.dp,
                            maxHeight = maxHeight - 6.dp,
                            density = density,
                            initialFontSize = (initialSize.value * config.textSizeMultiplier).sp,
                            minFontSize = 11.sp,
                            style = textStyle,
                            textMeasurer = rememberTextMeasurer(),
                        )
                        Text(
                            text = lyricText,
                            style = textStyle.copy(
                                fontSize = dynamicFontSize,
                                lineHeight = dynamicFontSize.value.sp * 1.35f,
                            ),
                            overflow = TextOverflow.Ellipsis,
                            textAlign = config.textAlign,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (config.showBranding) {
                        LyricsBrandingRow(secondaryColor, isDark = true)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Estilo 3 — Cover Bloom
// La carátula ocupa toda la tarjeta; un scrim degradado con tinte de acento
// sostiene la letra en el tercio inferior. El más "editorial" de los cinco.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CoverBloomLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontFamily = rememberLyricsFontFamily()
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)
    val cardSize = lyricsCardSize(config.aspectRatio)
    val shape = expressiveShape(config.shapeScale)
    val seed = config.accent.seed
    val scrimBase = seed.mixWith(Color.Black, 0.62f)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(cardSize.width, cardSize.height)
                .clip(shape),
        ) {
            if (config.showCoverArt) {
                Image(
                    painter = artworkPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(scrimBase))
            }

            // Scrim degradado — más denso hacia abajo, teñido con el acento
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to scrimBase.copy(alpha = 0.35f),
                        0.72f to scrimBase.copy(alpha = 0.82f),
                        1.0f to scrimBase.copy(alpha = 0.95f),
                    )
                )
            )

            // Comilla insignia — esquina superior
            Box(
                modifier = Modifier
                    .padding(config.cardPadding)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(seed.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u275D", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(config.cardPadding),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = cardSize.height * 0.5f),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    val textStyle = TextStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = config.textAlign,
                        letterSpacing = (-0.02).em,
                        fontFamily = fontFamily,
                    )
                    val initialSize = heuristicInitialSize(lyricText.length, 22f, 18f, 15f, 12f)
                    val dynamicFontSize = rememberAdjustedFontSize(
                        text = lyricText,
                        maxWidth = maxWidth - 6.dp,
                        maxHeight = maxHeight - 6.dp,
                        density = density,
                        initialFontSize = (initialSize.value * config.textSizeMultiplier).sp,
                        minFontSize = 11.sp,
                        style = textStyle,
                        textMeasurer = rememberTextMeasurer(),
                    )
                    Text(
                        text = lyricText,
                        style = textStyle.copy(
                            fontSize = dynamicFontSize,
                            lineHeight = dynamicFontSize.value.sp * 1.3f,
                        ),
                        overflow = TextOverflow.Ellipsis,
                        textAlign = config.textAlign,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (config.showTitle || config.showArtist) {
                    Column {
                        if (config.showTitle) {
                            Text(
                                text = mediaMetadata.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (config.showArtist) {
                            Text(
                                text = mediaMetadata.artists.joinToString { it.name },
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (config.showBranding) {
                    LyricsBrandingRow(color = Color.White.copy(alpha = 0.85f), isDark = false)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Estilo 4 — Quote
// Tipografía pura: fondo tonal muy claro, una comilla enorme, mucho espacio
// en blanco. Sin carátula grande, sin efectos — el más "editorial impreso".
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun QuoteLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontFamily = rememberLyricsFontFamily()
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)
    val cardSize = lyricsCardSize(config.aspectRatio)
    val shape = expressiveShape(config.shapeScale)

    val seed = config.accent.seed
    val bgColor = seed.tonalContainer(0.95f)
    val mainText = seed.onTonalContainer(0.74f)
    val secondaryText = mainText.copy(alpha = 0.55f)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(cardSize.width, cardSize.height)
                .clip(shape)
                .background(bgColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(config.cardPadding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "\u201C",
                    color = seed.copy(alpha = 0.30f),
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 62.sp,
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val textStyle = TextStyle(
                        color = mainText,
                        fontWeight = FontWeight.Medium,
                        textAlign = config.textAlign,
                        fontFamily = fontFamily,
                        letterSpacing = (-0.01).em,
                    )
                    val initialSize = heuristicInitialSize(lyricText.length, 24f, 20f, 17f, 13f)
                    val dynamicFontSize = rememberAdjustedFontSize(
                        text = lyricText,
                        maxWidth = maxWidth - 6.dp,
                        maxHeight = maxHeight - 6.dp,
                        density = density,
                        initialFontSize = (initialSize.value * config.textSizeMultiplier).sp,
                        minFontSize = 11.sp,
                        style = textStyle,
                        textMeasurer = rememberTextMeasurer(),
                    )
                    Text(
                        text = lyricText,
                        style = textStyle.copy(
                            fontSize = dynamicFontSize,
                            lineHeight = dynamicFontSize.value.sp * 1.42f,
                        ),
                        overflow = TextOverflow.Ellipsis,
                        textAlign = config.textAlign,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column {
                    if (config.showTitle || config.showArtist) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(mainText.copy(alpha = 0.12f))
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (config.showCoverArt) {
                            Image(
                                painter = artworkPainter,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column {
                            if (config.showTitle) {
                                Text(
                                    text = mediaMetadata.title,
                                    color = mainText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (config.showArtist) {
                                Text(
                                    text = mediaMetadata.artists.joinToString { it.name },
                                    color = secondaryText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (config.showBranding) {
                        Spacer(Modifier.height(10.dp))
                        LyricsBrandingRow(secondaryText, isDark = bgColor.isPerceptuallyLight())
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Estilo 5 — Aurora
// Malla de degradados suaves (acento + dos matices derivados) sobre base
// oscura. El más juguetón: tipografía extra grande, sin caja de texto.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AuroraLayout(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontFamily = rememberLyricsFontFamily()
    val artworkPainter = rememberArtworkPainter(mediaMetadata.thumbnailUrl)
    val cardSize = lyricsCardSize(config.aspectRatio)
    val shape = expressiveShape(config.shapeScale)

    val seed = config.accent.seed
    val accentB = seed.shiftHue(48f)
    val accentC = seed.shiftHue(-56f)
    val base = Color(0xFF0B0B14)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(cardSize.width, cardSize.height)
                .clip(shape)
                .background(base),
        ) {
            // Malla de degradados — tres resplandores radiales superpuestos
            Box(
                modifier = Modifier
                    .size(cardSize.width * 0.9f)
                    .align(Alignment.TopStart)
                    .offset(x = -cardSize.width * 0.25f, y = -cardSize.width * 0.2f)
                    .background(Brush.radialGradient(listOf(seed.copy(alpha = 0.55f), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(cardSize.width * 0.95f)
                    .align(Alignment.TopEnd)
                    .offset(x = cardSize.width * 0.3f, y = -cardSize.width * 0.15f)
                    .background(Brush.radialGradient(listOf(accentB.copy(alpha = 0.40f), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .size(cardSize.width * 0.85f)
                    .align(Alignment.BottomCenter)
                    .offset(y = cardSize.width * 0.28f)
                    .background(Brush.radialGradient(listOf(accentC.copy(alpha = 0.38f), Color.Transparent)))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(config.cardPadding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (config.showCoverArt) {
                        Image(
                            painter = artworkPainter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(config.shapeScale.corner * 0.3f))
                                .border(1.dp, seed.copy(alpha = 0.5f), RoundedCornerShape(config.shapeScale.corner * 0.3f)),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (config.showTitle) {
                            Text(
                                text = mediaMetadata.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                letterSpacing = (-0.02).em,
                            )
                        }
                        if (config.showArtist) {
                            Text(
                                text = mediaMetadata.artists.joinToString { it.name },
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val textStyle = TextStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        textAlign = config.textAlign,
                        fontFamily = fontFamily,
                        letterSpacing = (-0.02).em,
                    )
                    val initialSize = heuristicInitialSize(lyricText.length, 26f, 21f, 18f, 14f)
                    val dynamicFontSize = rememberAdjustedFontSize(
                        text = lyricText,
                        maxWidth = maxWidth - 6.dp,
                        maxHeight = maxHeight - 6.dp,
                        density = density,
                        initialFontSize = (initialSize.value * config.textSizeMultiplier).sp,
                        minFontSize = 11.sp,
                        style = textStyle,
                        textMeasurer = rememberTextMeasurer(),
                    )
                    Text(
                        text = lyricText,
                        style = textStyle.copy(
                            fontSize = dynamicFontSize,
                            lineHeight = dynamicFontSize.value.sp * 1.24f,
                        ),
                        overflow = TextOverflow.Ellipsis,
                        textAlign = config.textAlign,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (config.showBranding) {
                    LyricsBrandingRow(Color.White.copy(alpha = 0.65f), isDark = false)
                }
            }
        }
    }
}
