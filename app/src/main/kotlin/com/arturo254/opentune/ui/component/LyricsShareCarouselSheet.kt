/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arturo254.opentune.R
import com.arturo254.opentune.models.MediaMetadata

// ─────────────────────────────────────────────────────────────────────────────
// API pública
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom sheet que permite al usuario:
 *  1. Previsualizar en tiempo real la tarjeta de letras.
 *  2. Elegir estilo (Tonal / Glass / Cover Bloom / Quote / Aurora).
 *  3. Elegir un único color de acento — alimenta los 5 estilos.
 *  4. Elegir proporción de salida pensada para redes (1:1, 4:5, 9:16, 16:9).
 *  5. Ajustar forma, tamaño de texto, alineación, visibilidad y padding.
 *  6. Compartir o guardar la configuración resultante.
 *
 * @param onShare  Se llama con el [LyricsCardConfig] final cuando el usuario pulsa "Compartir".
 *                 El caller es responsable de capturar el composable como bitmap y lanzar el Intent.
 * @param onSave   Opcional. Si se provee, aparece el botón "Guardar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsShareCarouselSheet(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    initialConfig: LyricsCardConfig = LyricsCardConfig(),
    onDismiss: () -> Unit,
    onShare: (config: LyricsCardConfig) -> Unit,
    onSave: ((config: LyricsCardConfig) -> Unit)? = null,
) {
    var config by remember { mutableStateOf(initialConfig) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Título del sheet ──────────────────────────────────────────
            Text(
                text = "Compartir letra",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(18.dp))

            // ── Preview principal ─────────────────────────────────────────
            // AnimatedContent hace fade entre cambios de config
            AnimatedContent(
                targetState = config,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
                label = "lyrics_card_preview",
            ) { previewConfig ->
                CardPreviewBox(
                    lyricText = lyricText,
                    mediaMetadata = mediaMetadata,
                    config = previewConfig,
                    maxPreviewDp = 260,
                )
            }

            Spacer(Modifier.height(22.dp))

            // ── Carrusel de estilos ────────────────────────────────────────
            SheetSectionLabel("Estilo")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LyricsCardStyle.entries.forEach { style ->
                    StyleThumbnail(
                        style = style,
                        lyricText = lyricText,
                        mediaMetadata = mediaMetadata,
                        currentConfig = config,
                        isSelected = config.style == style,
                        onSelect = { config = config.copy(style = style) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Acento — un único color que alimenta los 5 estilos ─────────
            SheetSectionLabel("Acento")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LyricsAccents.all.forEach { accent ->
                    AccentChip(
                        accent = accent,
                        isSelected = config.accent == accent,
                        onSelect = { config = config.copy(accent = accent) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Proporción — pensada para compartir en redes ───────────────
            SheetSectionLabel("Proporción")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LyricsAspectRatio.entries.forEach { ratio ->
                    AspectRatioChip(
                        ratio = ratio,
                        isSelected = config.aspectRatio == ratio,
                        onSelect = { config = config.copy(aspectRatio = ratio) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Panel de personalización (expandible) ─────────────────────
            CustomizationPanel(
                config = config,
                onConfigChange = { config = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(26.dp))

            // ── Botones de acción ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onSave != null) {
                    OutlinedButton(
                        onClick = { onSave(config) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                Button(
                    onClick = { onShare(config) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview principal — box con la tarjeta escalada, respeta la proporción
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardPreviewBox(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    maxPreviewDp: Int,
) {
    val cardSize = lyricsCardSize(config.aspectRatio)
    val maxDim = maxOf(cardSize.width.value, cardSize.height.value)
    val scale = maxPreviewDp / maxDim
    val previewW = (cardSize.width.value * scale).dp
    val previewH = (cardSize.height.value * scale).dp

    Box(
        modifier = Modifier
            .size(previewW, previewH)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // La tarjeta siempre se renderiza a su tamaño real y se escala visualmente
        Box(
            modifier = Modifier
                .requiredSize(cardSize.width, cardSize.height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
        ) {
            LyricsCardByLayout(
                lyricText = lyricText,
                mediaMetadata = mediaMetadata,
                config = config,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Thumbnail de estilo en el carrusel — slot cuadrado, contenido centrado
// y escalado sin importar la proporción activa.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StyleThumbnail(
    style: LyricsCardStyle,
    lyricText: String,
    mediaMetadata: MediaMetadata,
    currentConfig: LyricsCardConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val thumbnailDp = 96
    val previewConfig = currentConfig.copy(style = style)
    val cardSize = lyricsCardSize(previewConfig.aspectRatio)
    val maxDim = maxOf(cardSize.width.value, cardSize.height.value)
    val scale = thumbnailDp / maxDim

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(thumbnailDp.dp),
    ) {
        Box(
            modifier = Modifier
                .size(thumbnailDp.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onSelect),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(cardSize.width, cardSize.height)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) {
                LyricsCardByLayout(
                    lyricText = lyricText,
                    mediaMetadata = mediaMetadata,
                    config = previewConfig,
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))
        Text(
            text = style.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chip de acento
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccentChip(
    accent: LyricsAccent,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(50),
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else Color.Transparent
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(accent.seed)
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
        )
        Text(
            text = accent.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chip de proporción — muestra el ratio como una miniatura de forma real
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AspectRatioChip(
    ratio: LyricsAspectRatio,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val short = 14.dp
    val swatchW = if (ratio.widthRatio <= ratio.heightRatio) short else short * (ratio.widthRatio / ratio.heightRatio)
    val swatchH = if (ratio.widthRatio <= ratio.heightRatio) short * (ratio.heightRatio / ratio.widthRatio) else short

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(14.dp),
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else Color.Transparent
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(swatchW.coerceAtMost(22.dp), swatchH.coerceAtMost(22.dp))
                .clip(RoundedCornerShape(3.dp))
                .border(
                    1.5.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    RoundedCornerShape(3.dp),
                )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = ratio.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = ratio.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Panel de personalización (expandible)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomizationPanel(
    config: LyricsCardConfig,
    onConfigChange: (LyricsCardConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )

    Column(modifier = modifier) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.lyrics_share_customization),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = null,
                modifier = Modifier.rotate(chevronAngle),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {

                // ── Forma — escala expresiva de esquinas ───────────────────
                Column {
                    Text(
                        text = "Forma",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LyricsShapeScale.entries.forEach { scale ->
                            ShapeScaleButton(
                                scale = scale,
                                isSelected = config.shapeScale == scale,
                                onClick = { onConfigChange(config.copy(shapeScale = scale)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // ── Intensidad de vidrio — solo aplica al estilo Glass ──────
                AnimatedVisibility(visible = config.style == LyricsCardStyle.Glass) {
                    Column {
                        Text(
                            text = "Intensidad de vidrio",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            LyricsGlassIntensity.entries.forEach { intensity ->
                                ShapeScaleLikeButton(
                                    label = intensity.displayName,
                                    isSelected = config.glassIntensity == intensity,
                                    onClick = { onConfigChange(config.copy(glassIntensity = intensity)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

                // ── Tamaño de texto ───────────────────────────────────────
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.lyrics_share_text_size),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${(config.textSizeMultiplier * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = config.textSizeMultiplier,
                        onValueChange = { onConfigChange(config.copy(textSizeMultiplier = it)) },
                        valueRange = 0.6f..1.5f,
                        steps = 17,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Alineación del texto ──────────────────────────────────
                Column {
                    Text(
                        text = stringResource(R.string.lyrics_share_alignment),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AlignButton(
                            painter = painterResource(R.drawable.format_align_left),
                            label = stringResource(R.string.lyrics_share_align_left),
                            isSelected = config.textAlign == TextAlign.Start,
                            onClick = { onConfigChange(config.copy(textAlign = TextAlign.Start)) },
                        )
                        AlignButton(
                            painter = painterResource(R.drawable.format_align_center),
                            label = stringResource(R.string.lyrics_share_align_center),
                            isSelected = config.textAlign == TextAlign.Center,
                            onClick = { onConfigChange(config.copy(textAlign = TextAlign.Center)) },
                        )
                        AlignButton(
                            painter = painterResource(R.drawable.format_align_right),
                            label = stringResource(R.string.lyrics_share_align_right),
                            isSelected = config.textAlign == TextAlign.End,
                            onClick = { onConfigChange(config.copy(textAlign = TextAlign.End)) },
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

                // ── Visibilidad de elementos ──────────────────────────────
                Text(
                    text = stringResource(R.string.lyrics_share_visibility),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ToggleRow(stringResource(R.string.lyrics_share_show_title), config.showTitle) { onConfigChange(config.copy(showTitle = it)) }
                    ToggleRow(stringResource(R.string.lyrics_share_show_artist), config.showArtist) { onConfigChange(config.copy(showArtist = it)) }
                    ToggleRow(stringResource(R.string.lyrics_share_show_cover), config.showCoverArt) { onConfigChange(config.copy(showCoverArt = it)) }
                    ToggleRow(stringResource(R.string.lyrics_share_show_branding), config.showBranding) { onConfigChange(config.copy(showBranding = it)) }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

                // ── Padding ───────────────────────────────────────────────
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.lyrics_share_spacing),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${config.cardPadding.value.toInt()} dp",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = config.cardPadding.value,
                        onValueChange = { onConfigChange(config.copy(cardPadding = it.dp)) },
                        valueRange = 12f..36f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Componentes internos auxiliares
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShapeScaleButton(
    scale: LyricsShapeScale,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(10.dp),
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(18.dp)
                .clip(RoundedCornerShape(scale.corner / 4.5f))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = scale.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
    }
}

@Composable
private fun ShapeScaleLikeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(10.dp),
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AlignButton(
    painter: Painter,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color =
                    if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(10.dp),
            )
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint =
                if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, bottom = 8.dp),
    )
}
