/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.utils

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import android.view.View
import android.view.PixelCopy
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.drawToBitmap
import com.arturo254.opentune.R
import com.arturo254.opentune.ui.component.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

object ComposeToImage {

    // ── Utilidades internas ──

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity      -> this
        is ContextWrapper -> baseContext.findActivity()
        else             -> null
    }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        val config = bitmap.config
        if (config != Bitmap.Config.HARDWARE && config != null) return bitmap
        return runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: bitmap
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun pixelCopyViewBitmap(view: View): Bitmap? {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return null
        val activity = view.context.findActivity() ?: return null
        val window = activity.window ?: return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rect = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val result = suspendCancellableCoroutine { cont ->
            PixelCopy.request(window, rect, bitmap, { cont.resume(it) }, Handler(Looper.getMainLooper()))
        }
        return if (result == PixelCopy.SUCCESS) bitmap else null
    }

    // ── API pública de captura de View ──

    suspend fun captureViewBitmap(
        view: View,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        backgroundColor: Int? = null,
    ): Bitmap {
        val fallbackBitmap = runCatching { view.drawToBitmap() }.getOrElse {
            val w = view.width.coerceAtLeast(1)
            val h = view.height.coerceAtLeast(1)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                backgroundColor?.let { Canvas(bmp).drawColor(it) }
            }
        }
        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pixelCopyViewBitmap(view) ?: fallbackBitmap
        } else fallbackBitmap

        val needsScale = (targetWidth != null && targetWidth > 0 && targetWidth != original.width) ||
                (targetHeight != null && targetHeight > 0 && targetHeight != original.height)
        val base = if (needsScale) {
            val safeOriginal = ensureSoftwareBitmap(original)
            val tw = targetWidth ?: original.width
            val th = targetHeight ?: (original.height * tw / original.width)
            ensureSoftwareBitmap(Bitmap.createScaledBitmap(safeOriginal, tw, th, true))
        } else ensureSoftwareBitmap(original)

        if (backgroundColor != null) {
            val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
            Canvas(out).apply { drawColor(backgroundColor); drawBitmap(base, 0f, 0f, null) }
            return out
        }
        return base
    }

    fun cropBitmap(source: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
        val safeSource = ensureSoftwareBitmap(source)
        val safeLeft = left.coerceIn(0, safeSource.width.coerceAtLeast(1) - 1)
        val safeTop = top.coerceIn(0, safeSource.height.coerceAtLeast(1) - 1)
        val safeWidth = width.coerceIn(1, safeSource.width - safeLeft)
        val safeHeight = height.coerceIn(1, safeSource.height - safeTop)
        return ensureSoftwareBitmap(Bitmap.createBitmap(safeSource, safeLeft, safeTop, safeWidth, safeHeight))
    }

    fun fitBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int, backgroundColor: Int): Bitmap {
        val safeSource = ensureSoftwareBitmap(source)
        val out = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(backgroundColor)
        val scale = minOf(targetWidth.toFloat() / safeSource.width.coerceAtLeast(1),
            targetHeight.toFloat() / safeSource.height.coerceAtLeast(1))
        val scaledW = (safeSource.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (safeSource.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scaledW != safeSource.width || scaledH != safeSource.height) {
            ensureSoftwareBitmap(Bitmap.createScaledBitmap(safeSource, scaledW, scaledH, true))
        } else safeSource
        canvas.drawBitmap(scaled, ((targetWidth - scaled.width) / 2f), ((targetHeight - scaled.height) / 2f), null)
        return out
    }

    // ── Carga de carátula ──

    private suspend fun loadCoverArt(context: Context, url: String?): Bitmap? {
        if (url == null) return null
        return runCatching {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context).data(url).size(512).allowHardware(false).build()
            loader.execute(request).image?.toBitmap()
        }.getOrNull()
    }

    // ── Conversiones de tipo ──

    private fun Color.toArgb(): Int = android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )

    private fun Color.toArgb(alphaOverride: Float): Int = android.graphics.Color.argb(
        (alphaOverride * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )

    private fun TextAlign.toLayoutAlignment(): Layout.Alignment = when (this) {
        TextAlign.Start,
        TextAlign.Left -> Layout.Alignment.ALIGN_NORMAL
        TextAlign.End,
        TextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_CENTER
    }

    // ── Conversión de Dp a píxeles proporcional al tamaño de la tarjeta ──

    private fun Dp.toPx(cardSize: Float): Float {
        // 340.dp es el tamaño base definido en LyricsCardBaseSize
        return (this.value / 340f) * cardSize
    }

    // ── Helpers de dibujo ──

    /** Fondo con blur de carátula y scrim encima. */
    private fun drawAlbumBackground(
        canvas: Canvas,
        width: Int,
        height: Int,
        coverArt: Bitmap?,
        dimAlpha: Float,
        cornerRadiusTL: Float,
        cornerRadiusTR: Float,
        cornerRadiusBR: Float,
        cornerRadiusBL: Float,
    ) {
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val path = Path().apply {
            addRoundRect(rect, floatArrayOf(
                cornerRadiusTL, cornerRadiusTL,
                cornerRadiusTR, cornerRadiusTR,
                cornerRadiusBR, cornerRadiusBR,
                cornerRadiusBL, cornerRadiusBL
            ), Path.Direction.CW)
        }

        if (coverArt != null) {
            val scaled = ensureSoftwareBitmap(Bitmap.createScaledBitmap(coverArt, width, height, true))
            val blurred = scaleBlurQuality(scaled, 12)
            canvas.withClip(path) { drawBitmap(blurred, 0f, 0f, null) }
            scaled.recycle()
            blurred.recycle()
        } else {
            canvas.drawPath(path, Paint().apply {
                color = 0xFF0F0F0F.toInt(); isAntiAlias = true
            })
        }

        canvas.drawPath(path, Paint().apply {
            color = android.graphics.Color.argb((dimAlpha * 255).toInt(), 0, 0, 0)
            isAntiAlias = true
        })
    }

    /** Panel frosted para el estilo Glass (efecto liquidGlass de Compose). */
    private fun drawFrostedPanel(
        canvas: Canvas,
        width: Int,
        height: Int,
        coverArt: Bitmap?,
        seedColor: Color,
        intensity: LyricsGlassIntensity,
        panelRect: RectF,
        cornerRadius: Float,
    ) {
        val path = Path().apply { addRoundRect(panelRect, cornerRadius, cornerRadius, Path.Direction.CW) }

        if (coverArt != null) {
            val left = panelRect.left.toInt().coerceIn(0, width - 1)
            val top = panelRect.top.toInt().coerceIn(0, height - 1)
            val pWidth = panelRect.width().toInt().coerceIn(1, width - left)
            val pHeight = panelRect.height().toInt().coerceIn(1, height - top)
            val scaled = ensureSoftwareBitmap(Bitmap.createScaledBitmap(coverArt, width, height, true))
            val crop = Bitmap.createBitmap(scaled, left, top, pWidth, pHeight)
            val frosted = scaleBlur(crop, intensity.cloudyRadius)
            canvas.withClip(path) { drawBitmap(frosted, panelRect.left, panelRect.top, null) }
            scaled.recycle()
            crop.recycle()
            frosted.recycle()
        }

        // Tint del panel (seed + negro)
        canvas.drawPath(path, Paint().apply {
            color = seedColor.copy(alpha = intensity.tintAlpha * 0.55f).toArgb()
            isAntiAlias = true
        })
        canvas.drawPath(path, Paint().apply {
            color = Color.Black.copy(alpha = intensity.tintAlpha * 0.5f).toArgb()
            isAntiAlias = true
        })
        // Borde sutil
        canvas.drawPath(path, Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f
            color = android.graphics.Color.argb(25, 255, 255, 255)
            isAntiAlias = true
        })
    }

    /** Fila de metadatos (carátula + título + artista). */
    private fun drawMetadataRow(
        canvas: Canvas,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        cardWidth: Int,
        contentLeft: Float,
        contentTop: Float,
        contentRight: Float,
        showCover: Boolean,
        showTitle: Boolean,
        showArtist: Boolean,
        mainTextColor: Int,
        secondaryTextColor: Int,
        coverArtSize: Float,
        coverArtCorner: Float,
    ) {
        var textStartX = contentLeft

        if (showCover && coverArt != null) {
            val rect = RectF(contentLeft, contentTop, contentLeft + coverArtSize, contentTop + coverArtSize)
            val path = Path().apply {
                addRoundRect(rect, coverArtCorner, coverArtCorner, Path.Direction.CW)
            }
            canvas.withClip(path) { drawBitmap(coverArt, null, rect, null) }
            canvas.drawRoundRect(rect, coverArtCorner, coverArtCorner, Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 1f
                color = android.graphics.Color.argb(38, 255, 255, 255)
                isAntiAlias = true
            })
            textStartX = contentLeft + coverArtSize + cardWidth * 0.035f
        }

        val titlePaint = TextPaint().apply {
            color = mainTextColor
            textSize = cardWidth * 0.038f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            letterSpacing = -0.02f
        }
        val artistPaint = TextPaint().apply {
            color = secondaryTextColor
            textSize = cardWidth * 0.028f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val textMaxWidth = (contentRight - textStartX).toInt().coerceAtLeast(1)

        if (showTitle) {
            val titleLayout = StaticLayout.Builder.obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(1).build()
            val artistLayout = StaticLayout.Builder.obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(1).build()

            val imageCenter = contentTop + (if (showCover && coverArt != null) coverArtSize / 2f else titleLayout.height / 2f)
            val textBlockHeight = titleLayout.height + (if (showArtist) artistLayout.height + 6f else 0f)
            val textBlockY = imageCenter - textBlockHeight / 2f

            canvas.withTranslation(textStartX, textBlockY) {
                titleLayout.draw(this)
                if (showArtist) {
                    translate(0f, titleLayout.height.toFloat() + 6f)
                    artistLayout.draw(this)
                }
            }
        }
    }

    // ─── BRANDING CORREGIDO ──────────────────────────────────────────────
    // Ahora 'y' es el centro vertical del círculo y el texto se centra con FontMetrics.
    private fun drawBranding(
        context: Context,
        canvas: Canvas,
        cardWidth: Int,
        x: Float,
        y: Float,        // <-- centro del círculo
        circleColor: Int,
        logoTint: Int,
        textColor: Int,
    ) {
        val baseSize = cardWidth.toFloat()
        val logoSize = (baseSize * 0.045f).toInt()
        val rawLogo = context.getDrawable(R.drawable.opentune)?.toBitmap(logoSize, logoSize)
        val logo = rawLogo?.let { source ->
            val colored = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Canvas(colored).drawBitmap(source, 0f, 0f, Paint().apply {
                colorFilter = PorterDuffColorFilter(logoTint, PorterDuff.Mode.SRC_IN)
                isAntiAlias = true
            })
            colored
        }

        val appNamePaint = TextPaint().apply {
            color = textColor
            textSize = baseSize * 0.028f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAntiAlias = true
            letterSpacing = 0.02f
        }

        val circleRadius = logoSize * 0.55f
        val circleX = x + circleRadius
        val circleY = y  // y es el centro

        // Dibujar círculo
        canvas.drawCircle(circleX, circleY, circleRadius, Paint().apply {
            color = circleColor
            isAntiAlias = true
            style = Paint.Style.FILL
        })

        // Dibujar logo centrado
        logo?.let {
            canvas.drawBitmap(it, circleX - logoSize / 2f, circleY - logoSize / 2f, null)
        }

        // Dibujar texto centrado verticalmente respecto al círculo
        val text = context.getString(R.string.app_name)
        val fm = appNamePaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val textBaseline = circleY + (textHeight / 2f) - fm.descent

        canvas.drawText(
            text,
            circleX + circleRadius + 10f,
            textBaseline,
            appNamePaint,
        )
    }

    /** Comilla decorativa (usada en CoverBloom y Quote). */
    private fun drawQuoteMark(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        text: String = "\u275D",
    ) {
        val paint = TextPaint().apply {
            this.color = color
            textSize = size * 0.44f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val fm = paint.fontMetrics
        val textY = y + (size / 2f) - (fm.ascent + fm.descent) / 2f

        canvas.drawCircle(x, y, size / 2f, Paint().apply {
            this.color = color          // Usamos 'this' para asignar la propiedad del Paint
            isAntiAlias = true
            style = Paint.Style.FILL
        })
        canvas.drawText(text, x, textY, paint)
    }
    /** Construye un layout de texto con auto‑fit (búsqueda binaria). */
    private fun buildFittedLyricsLayout(
        lyrics: String,
        paint: TextPaint,
        maxWidth: Int,
        availableHeight: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
        initialTextSize: Float,
        minTextSize: Float = 22f,
        lineHeightMultiplier: Float = 1.35f,
    ): StaticLayout {
        var textSize = initialTextSize
        var layout: StaticLayout
        do {
            paint.textSize = textSize
            layout = StaticLayout.Builder.obtain(lyrics, 0, lyrics.length, paint, maxWidth)
                .setAlignment(alignment)
                .setIncludePad(false)
                .setLineSpacing(8f, lineHeightMultiplier)
                .setMaxLines(12)
                .build()
            if (layout.height > availableHeight) textSize -= 2f else break
        } while (textSize > minTextSize)
        return layout
    }

    // ── Funciones de mezcla de color (copiadas de LyricsGlassStyle.kt) ──

    private fun Color.mixWith(other: Color, fraction: Float): Color {
        val t = fraction.coerceIn(0f, 1f)
        return Color(
            red = red + (other.red - red) * t,
            green = green + (other.green - green) * t,
            blue = blue + (other.blue - blue) * t,
            alpha = 1f,
        )
    }

    private fun Color.shiftHue(degrees: Float): Color {
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

    private fun Color.isPerceptuallyLight(): Boolean =
        (0.299f * red + 0.587f * green + 0.114f * blue) > 0.62f

    private fun Color.tonalContainer(amount: Float = 0.88f): Color = mixWith(Color.White, amount)

    private fun Color.onTonalContainer(amount: Float = 0.55f): Color = mixWith(Color.Black, amount)

    // ── Blur ──

    private fun scaleBlurQuality(source: Bitmap, strength: Int): Bitmap {
        val safe = ensureSoftwareBitmap(source)
        if (safe.width <= 400 || safe.height <= 400) return safe
        val targetSize = max(safe.width / 4, 200)
        val smallW = targetSize
        val smallH = (safe.height * targetSize / safe.width).coerceAtLeast(100)
        val small = Bitmap.createScaledBitmap(safe, smallW, smallH, true)
        val result = Bitmap.createScaledBitmap(small, safe.width, safe.height, true)
        small.recycle()
        return result
    }

    private fun scaleBlur(source: Bitmap, strength: Int): Bitmap {
        val safe = ensureSoftwareBitmap(source)
        val factor = (1f / (strength / 2f).coerceAtLeast(1F)).coerceIn(0.3f, 1f)
        val smallW = (safe.width * factor).toInt().coerceAtLeast(100)
        val smallH = (safe.height * factor).toInt().coerceAtLeast(100)
        val small = Bitmap.createScaledBitmap(safe, smallW, smallH, true)
        return Bitmap.createScaledBitmap(small, safe.width, safe.height, true)
    }

    // ── Esquinas asimétricas (expressiveShape) ──

    private fun expressiveCorners(scale: LyricsShapeScale, size: Float): FloatArray {
        val corner = when (scale) {
            LyricsShapeScale.Soft -> 14f
            LyricsShapeScale.Rounded -> 26f
            LyricsShapeScale.Bold -> 38f
            LyricsShapeScale.Full -> 54f
        }
        val scaled = corner * (size / 340f)
        return floatArrayOf(
            scaled, scaled,                // topStart
            scaled * 0.42f, scaled * 0.42f, // topEnd
            scaled, scaled,                // bottomEnd
            scaled * 0.42f, scaled * 0.42f  // bottomStart
        )
    }

    // ── Tamaño de la tarjeta ──

    private data class Size(val width: Int, val height: Int)

    private fun computeCardSize(aspectRatio: LyricsAspectRatio, outputSize: Int): Size {
        val short = outputSize
        val long = (outputSize * (maxOf(aspectRatio.widthRatio, aspectRatio.heightRatio) /
                minOf(aspectRatio.widthRatio, aspectRatio.heightRatio))).toInt()
        return if (aspectRatio.widthRatio <= aspectRatio.heightRatio) {
            Size(short, long)
        } else {
            Size(long, short)
        }
    }

    // ── API pública ──

    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        outputSize: Int = 1080,
    ): Bitmap = withContext(Dispatchers.Default) {
        val coverArt = loadCoverArt(context, coverArtUrl)
        val cardSize = computeCardSize(config.aspectRatio, outputSize)

        when (config.style) {
            LyricsCardStyle.Tonal -> renderTonal(context, coverArt, songTitle, artistName, lyrics, config, cardSize)
            LyricsCardStyle.Glass -> renderGlass(context, coverArt, songTitle, artistName, lyrics, config, cardSize)
            LyricsCardStyle.CoverBloom -> renderCoverBloom(context, coverArt, songTitle, artistName, lyrics, config, cardSize)
            LyricsCardStyle.Quote -> renderQuote(context, coverArt, songTitle, artistName, lyrics, config, cardSize)
            LyricsCardStyle.Aurora -> renderAurora(context, coverArt, songTitle, artistName, lyrics, config, cardSize)
        }
    }

    // ── API legada (compatibilidad) ──

    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        backgroundColor: Int? = null,
        textColor: Int? = null,
        secondaryTextColor: Int? = null,
        glassIntensity: LyricsGlassIntensity? = null,
    ): Bitmap {
        val aspectRatio = when {
            width == height -> LyricsAspectRatio.Square
            width > height -> {
                val ratio = width.toFloat() / height
                when {
                    ratio >= 1.7f -> LyricsAspectRatio.Wide
                    else -> LyricsAspectRatio.Square
                }
            }
            else -> {
                val ratio = height.toFloat() / width
                when {
                    ratio >= 1.7f -> LyricsAspectRatio.Story
                    ratio >= 1.2f -> LyricsAspectRatio.Portrait
                    else -> LyricsAspectRatio.Square
                }
            }
        }
        val config = LyricsCardConfig(
            style = if (glassIntensity != null) LyricsCardStyle.Glass else LyricsCardStyle.Tonal,
            aspectRatio = aspectRatio,
            glassIntensity = glassIntensity ?: LyricsGlassIntensity.Medium,
        )
        val outputSize = minOf(width, height)
        return createLyricsImage(context, coverArtUrl, songTitle, artistName, lyrics, config, outputSize)
    }

    // ─────────────────────────────────────────────────────────────
    // RENDERER 1 — Tonal
    // ─────────────────────────────────────────────────────────────

    private fun renderTonal(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Size,
    ): Bitmap {
        val seed = config.accent.seed
        val bgColor = seed.tonalContainer(0.90f)
        val mainText = seed.onTonalContainer(0.62f)
        val secondaryText = mainText.copy(alpha = 0.64f)

        val bitmap = createBitmap(cardSize.width, cardSize.height)
        val canvas = Canvas(bitmap)
        val size = cardSize.width.toFloat()
        val height = cardSize.height.toFloat()
        val corners = expressiveCorners(config.shapeScale, size)
        val padding = config.cardPadding.toPx(size)

        // Fondo
        val bgPath = Path().apply {
            addRoundRect(RectF(0f, 0f, size, height), corners, Path.Direction.CW)
        }
        canvas.drawPath(bgPath, Paint().apply {
            color = bgColor.toArgb()
            isAntiAlias = true
        })

        // Blob decorativo (esquina superior derecha)
        canvas.drawCircle(
            size * 1.2f,
            -size * 0.1f,
            size * 0.5f,
            Paint().apply {
                color = seed.copy(alpha = 0.16f).toArgb()
                isAntiAlias = true
            }
        )

        val contentLeft = padding
        val contentTop = padding
        val contentRight = size - padding

        // Metadata
        val coverArtSize = size * 0.16f
        val coverArtCorner = corners[0] * 0.42f
        if (config.showTitle || config.showCoverArt || config.showArtist) {
            drawMetadataRow(
                canvas, coverArt, songTitle, artistName, cardSize.width,
                contentLeft, contentTop, contentRight,
                config.showCoverArt, config.showTitle, config.showArtist,
                mainText.toArgb(), secondaryText.toArgb(),
                coverArtSize, coverArtCorner
            )
        }

        // Letras
        val metadataHeight = if (config.showTitle || config.showCoverArt || config.showArtist) size * 0.20f else 0f
        val lyricsPaint = TextPaint().apply {
            color = mainText.toArgb()
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            letterSpacing = -0.02f
        }
        val lyricsMaxWidth = (size * 0.85f).toInt()
        val logoH = if (config.showBranding) size * 0.09f else 0f
        val lyricsTop = contentTop + metadataHeight + padding
        val lyricsBottom = height - logoH - padding

        val lyricsLayout = buildFittedLyricsLayout(
            lyrics = lyrics,
            paint = lyricsPaint,
            maxWidth = lyricsMaxWidth,
            availableHeight = lyricsBottom - lyricsTop,
            alignment = config.textAlign.toLayoutAlignment(),
            initialTextSize = size * 0.055f * config.textSizeMultiplier,
            lineHeightMultiplier = 1.28f,
        )
        val lyricsY = lyricsTop + ((lyricsBottom - lyricsTop) - lyricsLayout.height) / 2f
        canvas.withTranslation((size - lyricsMaxWidth) / 2f, lyricsY) {
            lyricsLayout.draw(this)
        }

        // Branding (chip tonal) con y como centro
        if (config.showBranding) {
            val brandY = height - padding * 0.4f // centro del chip
            val chipPaint = Paint().apply {
                color = seed.copy(alpha = 0.14f).toArgb()
                isAntiAlias = true
            }
            val chipRect = RectF(
                contentLeft,
                brandY - size * 0.04f,
                contentLeft + size * 0.30f,
                brandY + size * 0.04f
            )
            canvas.drawRoundRect(chipRect, size * 0.08f, size * 0.08f, chipPaint)
            drawBranding(
                context, canvas, cardSize.width,
                x = contentLeft + size * 0.02f,
                y = brandY,   // centro
                circleColor = secondaryText.toArgb(),
                logoTint = 0xDD000000.toInt(),
                textColor = secondaryText.toArgb()
            )
        }

        return bitmap
    }

    // ─────────────────────────────────────────────────────────────
    // RENDERER 2 — Glass
    // ─────────────────────────────────────────────────────────────

    private fun renderGlass(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Size,
    ): Bitmap {
        val intensity = config.glassIntensity
        val seed = config.accent.seed
        val mainTextColor = Color.White
        val secondaryColor = Color.White.copy(alpha = 0.72f)

        val bitmap = createBitmap(cardSize.width, cardSize.height)
        val canvas = Canvas(bitmap)
        val size = cardSize.width.toFloat()
        val height = cardSize.height.toFloat()
        val corners = expressiveCorners(config.shapeScale, size)
        val padding = config.cardPadding.toPx(size)
        val panelPadding = 14f

        // Fondo desenfocado
        val bgPath = Path().apply {
            addRoundRect(RectF(0f, 0f, size, height), corners, Path.Direction.CW)
        }
        if (coverArt != null) {
            val scaled = Bitmap.createScaledBitmap(coverArt, cardSize.width, cardSize.height, true)
            val blurred = scaleBlur(scaled, intensity.cloudyRadius)
            canvas.withClip(bgPath) { drawBitmap(blurred, 0f, 0f, null) }
            scaled.recycle()
            blurred.recycle()
        } else {
            canvas.drawPath(bgPath, Paint().apply {
                color = 0xFF0F0F0F.toInt()
                isAntiAlias = true
            })
        }

        // Scrim degradado
        val scrimPaint = Paint().apply { isAntiAlias = true }
        val scrimShader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(
                Color.Black.copy(alpha = intensity.dimAlpha * 0.8f).toArgb(),
                Color.Black.copy(alpha = intensity.dimAlpha).toArgb(),
                Color.Black.copy(alpha = intensity.dimAlpha * 1.25f).toArgb(),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        scrimPaint.shader = scrimShader
        canvas.drawPath(bgPath, scrimPaint)

        // Panel de vidrio
        val panelRect = RectF(panelPadding, panelPadding, size - panelPadding, height - panelPadding)
        val panelCorner = corners[0] * 0.6f

        drawFrostedPanel(
            canvas, cardSize.width, cardSize.height,
            coverArt, seed, intensity, panelRect, panelCorner
        )

        val contentLeft = panelRect.left + padding
        val contentRight = panelRect.right - padding
        val contentTop = panelRect.top + padding

        // Metadata
        val coverArtSize = size * 0.16f
        val coverArtCorner = corners[0] * 0.42f
        if (config.showTitle || config.showCoverArt || config.showArtist) {
            drawMetadataRow(
                canvas, coverArt, songTitle, artistName, cardSize.width,
                contentLeft, contentTop, contentRight,
                config.showCoverArt, config.showTitle, config.showArtist,
                mainTextColor.toArgb(), secondaryColor.toArgb(),
                coverArtSize, coverArtCorner
            )
        }

        // Letras
        val metadataHeight = if (config.showTitle || config.showCoverArt || config.showArtist) size * 0.20f else 0f
        val lyricsPaint = TextPaint().apply {
            color = mainTextColor.toArgb()
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            letterSpacing = -0.01f
        }
        val lyricsMaxWidth = (panelRect.width() * 0.85f).toInt()
        val logoH = if (config.showBranding) size * 0.09f else 0f
        val lyricsTop = contentTop + metadataHeight + padding
        val lyricsBottom = panelRect.bottom - logoH - padding

        val lyricsLayout = buildFittedLyricsLayout(
            lyrics = lyrics,
            paint = lyricsPaint,
            maxWidth = lyricsMaxWidth,
            availableHeight = lyricsBottom - lyricsTop,
            alignment = config.textAlign.toLayoutAlignment(),
            initialTextSize = size * 0.05f * config.textSizeMultiplier,
            lineHeightMultiplier = 1.35f,
        )
        val lyricsY = lyricsTop + ((lyricsBottom - lyricsTop) - lyricsLayout.height) / 2f
        canvas.withTranslation(panelRect.left + (panelRect.width() - lyricsMaxWidth) / 2f, lyricsY) {
            lyricsLayout.draw(this)
        }

        // Branding
        if (config.showBranding) {
            val brandY = panelRect.bottom - padding * 0.4f
            drawBranding(
                context, canvas, cardSize.width,
                x = contentLeft,
                y = brandY,   // centro
                circleColor = secondaryColor.toArgb(),
                logoTint = 0xDD000000.toInt(),
                textColor = secondaryColor.toArgb()
            )
        }

        return bitmap
    }

    // ─────────────────────────────────────────────────────────────
    // RENDERER 3 — CoverBloom
    // ─────────────────────────────────────────────────────────────

    private fun renderCoverBloom(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Size,
    ): Bitmap {
        val seed = config.accent.seed
        val scrimBase = seed.mixWith(Color.Black, 0.62f)

        val bitmap = createBitmap(cardSize.width, cardSize.height)
        val canvas = Canvas(bitmap)
        val size = cardSize.width.toFloat()
        val height = cardSize.height.toFloat()
        val corners = expressiveCorners(config.shapeScale, size)
        val padding = config.cardPadding.toPx(size)

        val bgPath = Path().apply {
            addRoundRect(RectF(0f, 0f, size, height), corners, Path.Direction.CW)
        }

        // Carátula de fondo
        if (config.showCoverArt && coverArt != null) {
            val scaled = Bitmap.createScaledBitmap(coverArt, cardSize.width, cardSize.height, true)
            canvas.withClip(bgPath) { drawBitmap(scaled, 0f, 0f, null) }
            scaled.recycle()
        } else {
            canvas.drawPath(bgPath, Paint().apply {
                color = scrimBase.toArgb()
                isAntiAlias = true
            })
        }

        // Scrim degradado
        val scrimPaint = Paint().apply { isAntiAlias = true }
        val scrimShader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(
                Color.Transparent.toArgb(),
                scrimBase.copy(alpha = 0.35f).toArgb(),
                scrimBase.copy(alpha = 0.82f).toArgb(),
                scrimBase.copy(alpha = 0.95f).toArgb(),
            ),
            floatArrayOf(0f, 0.45f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.withClip(bgPath) {
            scrimPaint.shader = scrimShader
            drawRect(0f, 0f, size, height, scrimPaint)
        }

        // Comilla insignia (esquina superior)
        val quoteSize = size * 0.10f
        val quoteX = padding + quoteSize / 2f
        val quoteY = padding + quoteSize / 2f
        drawQuoteMark(
            canvas = canvas,
            x = quoteX,
            y = quoteY,
            size = quoteSize,
            color = seed.copy(alpha = 0.9f).toArgb(),
            text = "\u275D"
        )

        // Letras
        val lyricsPaint = TextPaint().apply {
            color = Color.White.toArgb()
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            letterSpacing = -0.02f
        }
        val lyricsMaxWidth = (size - padding * 2).toInt()
        val lyricsAreaTop = height * 0.45f
        val metadataHeight = if (config.showTitle || config.showArtist) 60f + padding else 0f
        val logoH = if (config.showBranding) size * 0.09f else 0f
        val lyricsAreaBottom = height - padding - metadataHeight - logoH

        val lyricsLayout = buildFittedLyricsLayout(
            lyrics = lyrics,
            paint = lyricsPaint,
            maxWidth = lyricsMaxWidth,
            availableHeight = lyricsAreaBottom - lyricsAreaTop,
            alignment = config.textAlign.toLayoutAlignment(),
            initialTextSize = size * 0.055f * config.textSizeMultiplier,
            lineHeightMultiplier = 1.3f,
        )
        val lyricsY = lyricsAreaTop + ((lyricsAreaBottom - lyricsAreaTop) - lyricsLayout.height) / 2f
        canvas.withTranslation(padding, lyricsY) {
            lyricsLayout.draw(this)
        }

        // Metadatos (título y artista)
        val metaY = lyricsY + lyricsLayout.height + padding
        val titlePaint = TextPaint().apply {
            color = Color.White.toArgb()
            textSize = size * 0.041f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val artistPaint = TextPaint().apply {
            color = Color.White.copy(alpha = 0.7f).toArgb()
            textSize = size * 0.035f
            isAntiAlias = true
        }
        if (config.showTitle) {
            canvas.drawText(songTitle, padding, metaY, titlePaint)
        }
        if (config.showArtist) {
            canvas.drawText(artistName, padding, metaY + size * 0.053f, artistPaint)
        }

        // Branding
        if (config.showBranding) {
            val brandY = height - padding * 0.4f
            drawBranding(
                context, canvas, cardSize.width,
                x = padding,
                y = brandY,   // centro
                circleColor = Color.White.copy(alpha = 0.85f).toArgb(),
                logoTint = 0xDD000000.toInt(),
                textColor = Color.White.copy(alpha = 0.85f).toArgb()
            )
        }

        return bitmap
    }

    // ─────────────────────────────────────────────────────────────
    // RENDERER 4 — Quote
    // ─────────────────────────────────────────────────────────────

    private fun renderQuote(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Size,
    ): Bitmap {
        val seed = config.accent.seed
        val bgColor = seed.tonalContainer(0.95f)
        val mainText = seed.onTonalContainer(0.74f)
        val secondaryText = mainText.copy(alpha = 0.55f)

        val bitmap = createBitmap(cardSize.width, cardSize.height)
        val canvas = Canvas(bitmap)
        val size = cardSize.width.toFloat()
        val height = cardSize.height.toFloat()
        val corners = expressiveCorners(config.shapeScale, size)
        val padding = config.cardPadding.toPx(size)

        // Fondo
        val bgPath = Path().apply {
            addRoundRect(RectF(0f, 0f, size, height), corners, Path.Direction.CW)
        }
        canvas.drawPath(bgPath, Paint().apply {
            color = bgColor.toArgb()
            isAntiAlias = true
        })

        // Comilla grande
        val quotePaint = TextPaint().apply {
            color = seed.copy(alpha = 0.30f).toArgb()
            textSize = size * 0.22f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText("\u201C", padding, padding + quotePaint.textSize * 0.82f, quotePaint)

        // Letras
        val lyricsPaint = TextPaint().apply {
            color = mainText.toArgb()
            typeface = Typeface.DEFAULT
            isAntiAlias = true
            letterSpacing = -0.01f
        }
        val lyricsMaxWidth = (size - padding * 2).toInt()
        val lyricsTop = padding + quotePaint.textSize * 0.9f
        val metadataHeight = if (config.showTitle || config.showArtist) 70f + padding else 0f
        val logoH = if (config.showBranding) size * 0.09f else 0f
        val lyricsBottom = height - padding - metadataHeight - logoH

        val lyricsLayout = buildFittedLyricsLayout(
            lyrics = lyrics,
            paint = lyricsPaint,
            maxWidth = lyricsMaxWidth,
            availableHeight = lyricsBottom - lyricsTop,
            alignment = config.textAlign.toLayoutAlignment(),
            initialTextSize = size * 0.055f * config.textSizeMultiplier,
            lineHeightMultiplier = 1.42f,
        )
        val lyricsY = lyricsTop + ((lyricsBottom - lyricsTop) - lyricsLayout.height) / 2f
        canvas.withTranslation(padding, lyricsY) {
            lyricsLayout.draw(this)
        }

        // Separador
        val metaY = lyricsY + lyricsLayout.height + padding
        canvas.drawLine(padding, metaY, size - padding, metaY, Paint().apply {
            color = mainText.copy(alpha = 0.12f).toArgb()
            strokeWidth = 1f
            isAntiAlias = true
        })

        // Metadatos (con carátula pequeña)
        val infoY = metaY + padding
        val coverSize = size * 0.082f
        var textStartX = padding
        if (config.showCoverArt && coverArt != null) {
            val rect = RectF(padding, infoY, padding + coverSize, infoY + coverSize)
            val path = Path().apply {
                addRoundRect(rect, coverSize / 4f, coverSize / 4f, Path.Direction.CW)
            }
            canvas.withClip(path) { drawBitmap(coverArt, null, rect, null) }
            textStartX = padding + coverSize + size * 0.03f
        }

        val titlePaint = TextPaint().apply {
            color = mainText.toArgb()
            textSize = size * 0.041f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val artistPaint = TextPaint().apply {
            color = secondaryText.toArgb()
            textSize = size * 0.035f
            isAntiAlias = true
        }
        if (config.showTitle) {
            canvas.drawText(songTitle, textStartX, infoY + size * 0.012f, titlePaint)
        }
        if (config.showArtist) {
            canvas.drawText(artistName, textStartX, infoY + size * 0.05f, artistPaint)
        }

        // Branding
        if (config.showBranding) {
            val brandY = height - padding * 0.2f
            drawBranding(
                context, canvas, cardSize.width,
                x = padding,
                y = brandY,   // centro
                circleColor = secondaryText.toArgb(),
                logoTint = if (bgColor.isPerceptuallyLight()) 0xDD000000.toInt() else 0xE6FFFFFF.toInt(),
                textColor = secondaryText.toArgb()
            )
        }

        return bitmap
    }

    // ─────────────────────────────────────────────────────────────
    // RENDERER 5 — Aurora
    // ─────────────────────────────────────────────────────────────

    private fun renderAurora(
        context: Context,
        coverArt: Bitmap?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        config: LyricsCardConfig,
        cardSize: Size,
    ): Bitmap {
        val seed = config.accent.seed
        val accentB = seed.shiftHue(48f)
        val accentC = seed.shiftHue(-56f)
        val base = Color(0xFF0B0B14)

        val bitmap = createBitmap(cardSize.width, cardSize.height)
        val canvas = Canvas(bitmap)
        val size = cardSize.width.toFloat()
        val height = cardSize.height.toFloat()
        val corners = expressiveCorners(config.shapeScale, size)
        val padding = config.cardPadding.toPx(size)

        // Fondo base
        val bgPath = Path().apply {
            addRoundRect(RectF(0f, 0f, size, height), corners, Path.Direction.CW)
        }
        canvas.drawPath(bgPath, Paint().apply {
            color = base.toArgb()
            isAntiAlias = true
        })

        // Resplandores radiales
        val glowPaint = Paint().apply { isAntiAlias = true }

        // 1
        glowPaint.shader = RadialGradient(
            -size * 0.25f, -size * 0.2f, size * 0.45f,
            seed.copy(alpha = 0.55f).toArgb(),
            Color.Transparent.toArgb(),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(-size * 0.25f, -size * 0.2f, size * 0.45f, glowPaint)

        // 2
        glowPaint.shader = RadialGradient(
            size * 1.3f, -size * 0.15f, size * 0.475f,
            accentB.copy(alpha = 0.40f).toArgb(),
            Color.Transparent.toArgb(),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(size * 1.3f, -size * 0.15f, size * 0.475f, glowPaint)

        // 3
        glowPaint.shader = RadialGradient(
            size * 0.5f, height * 1.28f, size * 0.425f,
            accentC.copy(alpha = 0.38f).toArgb(),
            Color.Transparent.toArgb(),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(size * 0.5f, height * 1.28f, size * 0.425f, glowPaint)

        // Header (carátula + título + artista)
        var headerY = padding
        val coverSize = size * 0.13f
        if (config.showCoverArt && coverArt != null) {
            val rect = RectF(padding, padding, padding + coverSize, padding + coverSize)
            val corner = size * 0.03f
            val path = Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) }
            canvas.withClip(path) { drawBitmap(coverArt, null, rect, null) }
            canvas.drawRoundRect(rect, corner, corner, Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = seed.copy(alpha = 0.5f).toArgb()
                isAntiAlias = true
            })
            headerY = padding + coverSize + padding * 0.5f
        }

        val titlePaint = TextPaint().apply {
            color = Color.White.toArgb()
            textSize = size * 0.044f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            letterSpacing = -0.02f
        }
        val artistPaint = TextPaint().apply {
            color = Color.White.copy(alpha = 0.6f).toArgb()
            textSize = size * 0.035f
            isAntiAlias = true
        }
        if (config.showTitle) {
            canvas.drawText(songTitle, padding + coverSize + padding * 0.3f, headerY, titlePaint)
            headerY += size * 0.06f
        }
        if (config.showArtist) {
            canvas.drawText(artistName, padding + coverSize + padding * 0.3f, headerY, artistPaint)
            headerY += size * 0.06f
        }

        // Letras
        val lyricsPaint = TextPaint().apply {
            color = Color.White.toArgb()
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            letterSpacing = -0.02f
        }
        val lyricsMaxWidth = (size - padding * 2).toInt()
        val logoH = if (config.showBranding) size * 0.09f else 0f
        val lyricsTop = headerY + padding * 0.5f
        val lyricsBottom = height - logoH - padding

        val lyricsLayout = buildFittedLyricsLayout(
            lyrics = lyrics,
            paint = lyricsPaint,
            maxWidth = lyricsMaxWidth,
            availableHeight = lyricsBottom - lyricsTop,
            alignment = config.textAlign.toLayoutAlignment(),
            initialTextSize = size * 0.065f * config.textSizeMultiplier,
            lineHeightMultiplier = 1.24f,
        )
        val lyricsY = lyricsTop + ((lyricsBottom - lyricsTop) - lyricsLayout.height) / 2f
        canvas.withTranslation(padding, lyricsY) {
            lyricsLayout.draw(this)
        }

        // Branding
        if (config.showBranding) {
            val brandY = height - padding * 0.2f
            drawBranding(
                context, canvas, cardSize.width,
                x = padding,
                y = brandY,   // centro
                circleColor = Color.White.copy(alpha = 0.65f).toArgb(),
                logoTint = 0xDD000000.toInt(),
                textColor = Color.White.copy(alpha = 0.65f).toArgb()
            )
        }

        return bitmap
    }

    // ── Guardado ──

    fun saveBitmapAsFile(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val safeBitmap = ensureSoftwareBitmap(bitmap)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OpenTune")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            ) ?: throw IllegalStateException("Failed to create new MediaStore record")
            context.contentResolver.openOutputStream(uri)?.use { safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            uri
        } else {
            val cachePath = File(context.cacheDir, "images").also { it.mkdirs() }
            val imageFile = File(cachePath, "$fileName.png")
            FileOutputStream(imageFile).use { safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", imageFile)
        }
    }
}