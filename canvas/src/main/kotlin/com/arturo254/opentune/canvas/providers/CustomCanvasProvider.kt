package com.arturo254.opentune.canvas.providers

import android.content.Context
import android.util.Log
import com.arturo254.opentune.canvas.CanvasCacheManager
import com.arturo254.opentune.canvas.models.CanvasArtwork
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import timber.log.Timber
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.io.File

object CustomCanvasProvider {

    private const val API_BASE_URL =
        "https://opentune-canvas-api.cervantesarturo777.workers.dev"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(jsonParser) }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 25_000
                socketTimeoutMillis = 25_000
            }
            install(ContentEncoding) { gzip(); deflate() }
            install(HttpCache)
            expectSuccess = false
        }
    }

    private data class CanvasEntry(
        val artist: String,
        val album: String,
        val song: String,
        val url: String,
    )

    private data class ListCacheEntry(val entries: List<CanvasEntry>, val expiresAtMs: Long)

    private var listCache: ListCacheEntry? = null
    private val listCacheLock = Any()
    private const val LIST_CACHE_TTL_MS = 1000L * 60 * 60 * 24

    private data class CacheEntry(val value: CanvasArtwork?, val expiresAtMs: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24

    private var appContext: Context? = null
    private const val CATALOG_FILE = "canvas_catalog.json"

    // ===================== INICIALIZACIÓN =====================

    fun init(context: Context) {
        appContext = context.applicationContext
        loadCatalogFromDisk()
        GlobalLog.append(Log.INFO, "CustomCanvas", "🎵 CustomCanvasProvider inicializado")
    }

    // ===================== MÉTODOS PÚBLICOS =====================

    suspend fun getBySongArtist(
        song: String? = null,
        artist: String,
        album: String,
    ): CanvasArtwork? {
        val key = cacheKey(artist, album, song)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let {
            val msg = "🎵 CustomCanvas - Cache hit (memoria)"
            Timber.d(msg)
            GlobalLog.append(Log.DEBUG, "CustomCanvas", msg)
            return it.value
        }

        val msg = "🎵 CustomCanvas - Buscando: artist=$artist, album=$album, song=$song"
        Timber.d(msg)
        GlobalLog.append(Log.DEBUG, "CustomCanvas", msg)

        val result = searchCanvas(artist, album, song)
        cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        return result
    }

    suspend fun getByAlbumArtist(
        album: String,
        artist: String,
    ): CanvasArtwork? {
        return getBySongArtist(
            song = null,
            artist = artist,
            album = album
        )
    }

    // ===================== MÉTODOS DE DEPURACIÓN =====================

    fun isCatalogLoaded(): Boolean {
        synchronized(listCacheLock) {
            return listCache != null && listCache?.entries?.isNotEmpty() == true
        }
    }

    suspend fun forceRefreshCatalog() {
        val msg = "🎵 CustomCanvas - Forzando recarga de catálogo..."
        Timber.d(msg)
        GlobalLog.append(Log.INFO, "CustomCanvas", msg)

        synchronized(listCacheLock) {
            listCache = null
        }
        // Forzar descarga
        getCatalog()

        val msg2 = "🎵 CustomCanvas - Catálogo recargado"
        Timber.d(msg2)
        GlobalLog.append(Log.INFO, "CustomCanvas", msg2)
    }

    // ===================== MÉTODOS PRIVADOS =====================

    private suspend fun searchCanvas(
        artist: String,
        album: String,
        song: String? = null,
    ): CanvasArtwork? {
        val entries = getCatalog() ?: return null
        if (entries.isEmpty()) return null

        val normArtist = normalize(artist)
        val normAlbum = normalize(album)
        val normSong = song?.let { normalize(it) }

        val candidates = entries.filter { entry ->
            artistMatches(normArtist, normalize(entry.artist))
        }

        if (candidates.isEmpty()) {
            val msg = "🎵 CustomCanvas - Sin coincidencias de artista"
            Timber.d(msg)
            GlobalLog.append(Log.WARN, "CustomCanvas", msg)
            return null
        }

        val albumCandidates = if (normAlbum.isNotBlank()) {
            candidates.filter { entry ->
                albumMatches(normAlbum, normalize(entry.album))
            }
        } else {
            if (!normSong.isNullOrBlank()) {
                val songMatches = candidates.filter { entry ->
                    entry.song.isNotBlank() &&
                            (normalize(entry.song) == normSong ||
                                    normalize(entry.song).contains(normSong) ||
                                    normSong.contains(normalize(entry.song)))
                }
                if (songMatches.isNotEmpty()) {
                    return createArtworkWithCache(songMatches.first(), "canción (sin álbum)")
                }
            }
            candidates
        }

        if (albumCandidates.isEmpty()) {
            val msg = "🎵 CustomCanvas - Sin coincidencias de álbum"
            Timber.d(msg)
            GlobalLog.append(Log.WARN, "CustomCanvas", msg)
            return null
        }

        val best = if (!normSong.isNullOrBlank()) {
            albumCandidates.firstOrNull { entry ->
                entry.song.isNotBlank() &&
                        (normalize(entry.song) == normSong ||
                                normalize(entry.song).contains(normSong) ||
                                normSong.contains(normalize(entry.song)))
            } ?: albumCandidates.first()
        } else {
            albumCandidates.first()
        }

        val matchType = when {
            best.song.isBlank() -> "álbum (sin canción específica)"
            !normSong.isNullOrBlank() && normalize(best.song) == normSong -> "canción exacta"
            else -> "álbum (con canción: ${best.song})"
        }

        return createArtworkWithCache(best, matchType)
    }

    private suspend fun createArtworkWithCache(
        entry: CanvasEntry,
        matchType: String
    ): CanvasArtwork? {
        val cacheKey = "${entry.artist}|${entry.album}|${entry.song}"

        val cached = CanvasCacheManager.getCachedCanvas(cacheKey)
        if (cached != null) {
            val msg = "🎵 CustomCanvas - ✅ Cache hit (disco): ${cached.url}"
            Timber.d(msg)
            GlobalLog.append(Log.INFO, "CustomCanvas", msg)
            return CanvasArtwork(
                name = entry.song.takeIf { it.isNotBlank() },
                artist = entry.artist,
                albumName = entry.album,
                animated = cached.url,
                videoUrl = cached.url,
            )
        }

        val msg = "🎵 CustomCanvas - ✅ Encontrado por $matchType: ${entry.url} (artista=${entry.artist}, album=${entry.album})"
        Timber.d(msg)
        GlobalLog.append(Log.INFO, "CustomCanvas", msg)

        val videoData = downloadVideo(entry.url)
        if (videoData != null) {
            CanvasCacheManager.cacheCanvas(
                id = cacheKey,
                artist = entry.artist,
                album = entry.album,
                song = entry.song,
                url = entry.url,
                videoData = videoData
            )
            val msg2 = "🎵 CustomCanvas - ✅ Video cacheado: ${entry.url}"
            Timber.d(msg2)
            GlobalLog.append(Log.INFO, "CustomCanvas", msg2)
        } else {
            val msg2 = "🎵 CustomCanvas - ⚠️ No se pudo descargar/cachear video: ${entry.url}"
            Timber.d(msg2)
            GlobalLog.append(Log.WARN, "CustomCanvas", msg2)
        }

        return CanvasArtwork(
            name = entry.song.takeIf { it.isNotBlank() },
            artist = entry.artist,
            albumName = entry.album,
            animated = entry.url,
            videoUrl = entry.url,
        )
    }

    private suspend fun downloadVideo(url: String): ByteArray? {
        return runCatching {
            val msg = "🎵 CustomCanvas - Descargando video: $url"
            Timber.d(msg)
            GlobalLog.append(Log.DEBUG, "CustomCanvas", msg)

            val response = client.get(url)
            if (response.status == HttpStatusCode.OK) {
                response.bodyAsBytes()
            } else {
                val msg2 = "🎵 CustomCanvas - Error descargando video: ${response.status}"
                Timber.d(msg2)
                GlobalLog.append(Log.ERROR, "CustomCanvas", msg2)
                null
            }
        }.getOrNull()
    }

    private suspend fun getCatalog(): List<CanvasEntry>? {
        synchronized(listCacheLock) {
            listCache?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let {
                val msg = "🎵 CustomCanvas - Catálogo en caché de memoria (${it.entries.size} entradas)"
                Timber.d(msg)
                GlobalLog.append(Log.DEBUG, "CustomCanvas", msg)
                return it.entries
            }
        }

        val diskEntries = loadCatalogFromDisk()
        if (diskEntries != null) {
            val msg = "🎵 CustomCanvas - Catálogo cargado desde disco (${diskEntries.size} entradas)"
            Timber.d(msg)
            GlobalLog.append(Log.INFO, "CustomCanvas", msg)
            synchronized(listCacheLock) {
                listCache = ListCacheEntry(diskEntries, System.currentTimeMillis() + LIST_CACHE_TTL_MS)
            }
            return diskEntries
        }

        val msg = "🎵 CustomCanvas - Descargando catálogo desde Cloudflare Workers..."
        Timber.d(msg)
        GlobalLog.append(Log.INFO, "CustomCanvas", msg)

        val fetched = fetchCatalogFromNetwork()

        if (fetched != null) {
            saveCatalogToDisk(fetched)
            synchronized(listCacheLock) {
                listCache = ListCacheEntry(fetched, System.currentTimeMillis() + LIST_CACHE_TTL_MS)
            }
            val msg2 = "🎵 CustomCanvas - Catálogo descargado (${fetched.size} entradas)"
            Timber.d(msg2)
            GlobalLog.append(Log.INFO, "CustomCanvas", msg2)
        } else {
            val msg2 = "🎵 CustomCanvas - No se pudo descargar el catálogo"
            Timber.d(msg2)
            GlobalLog.append(Log.ERROR, "CustomCanvas", msg2)
        }

        return fetched
    }

    private suspend fun fetchCatalogFromNetwork(): List<CanvasEntry>? {
        return try {
            val response = client.get(API_BASE_URL) {
                parameter("action", "list")
            }

            if (response.status != HttpStatusCode.OK) {
                val msg = "🎵 CustomCanvas - Error HTTP: ${response.status}"
                Timber.d(msg)
                GlobalLog.append(Log.ERROR, "CustomCanvas", msg)
                return null
            }

            val rawBody = response.bodyAsText()
            val trimmed = rawBody.trimStart()

            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                val msg = "🎵 CustomCanvas - ⚠️ Respuesta no es JSON. Primeros 200 chars: ${rawBody.take(200)}"
                Timber.d(msg)
                GlobalLog.append(Log.ERROR, "CustomCanvas", msg)
                return null
            }

            val root = jsonParser.parseToJsonElement(rawBody).jsonObject
            val success = root["success"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false

            if (!success) {
                val msg = "🎵 CustomCanvas - success=false en la respuesta"
                Timber.d(msg)
                GlobalLog.append(Log.ERROR, "CustomCanvas", msg)
                return null
            }

            val dataWrapper = root["data"]?.jsonObject
            val canvasesArray = dataWrapper?.get("canvases")?.jsonArray

            if (canvasesArray == null) {
                val msg = "🎵 CustomCanvas - ⚠️ No existe 'data.canvases'. Keys: ${dataWrapper?.keys}"
                Timber.d(msg)
                GlobalLog.append(Log.ERROR, "CustomCanvas", msg)
                return null
            }

            canvasesArray.mapNotNull { item ->
                val obj = item.jsonObject
                val url = obj["url"]?.jsonPrimitive?.contentOrNull
                val entryArtist = obj["artist"]?.jsonPrimitive?.contentOrNull
                if (url == null || entryArtist == null) {
                    val msg = "🎵 CustomCanvas - ⚠️ Entrada sin url/artist descartada: $obj"
                    Timber.d(msg)
                    GlobalLog.append(Log.WARN, "CustomCanvas", msg)
                    null
                } else {
                    CanvasEntry(
                        artist = entryArtist,
                        album = obj["album"]?.jsonPrimitive?.contentOrNull ?: "",
                        song = obj["song"]?.jsonPrimitive?.contentOrNull ?: "",
                        url = url,
                    )
                }
            }
        } catch (e: Exception) {
            val msg = "🎵 CustomCanvas - Excepción consultando catálogo: ${e.message}"
            Timber.e(e, msg)
            GlobalLog.append(Log.ERROR, "CustomCanvas", msg)
            null
        }
    }

    private fun saveCatalogToDisk(entries: List<CanvasEntry>) {
        try {
            val context = appContext ?: return
            val file = File(context.filesDir, CATALOG_FILE)
            val jsonString = jsonParser.encodeToString(entries)
            file.writeText(jsonString)
            val msg = "🎵 CustomCanvas - Catálogo guardado en disco (${entries.size} entradas)"
            Timber.d(msg)
            GlobalLog.append(Log.DEBUG, "CustomCanvas", msg)
        } catch (e: Exception) {
            val msg = "🎵 CustomCanvas - Error guardando catálogo en disco: ${e.message}"
            Timber.e(e, msg)
            GlobalLog.append(Log.ERROR, "CustomCanvas", msg)
        }
    }

    private fun loadCatalogFromDisk(): List<CanvasEntry>? {
        try {
            val context = appContext ?: return null
            val file = File(context.filesDir, CATALOG_FILE)
            if (!file.exists()) return null

            val jsonString = file.readText()
            val entries = jsonParser.decodeFromString<List<CanvasEntry>>(jsonString)
            val msg = "🎵 CustomCanvas - Catálogo cargado desde disco (${entries.size} entradas)"
            Timber.d(msg)
            GlobalLog.append(Log.DEBUG, "CustomCanvas", msg)
            return entries
        } catch (e: Exception) {
            val msg = "🎵 CustomCanvas - Error cargando catálogo desde disco: ${e.message}"
            Timber.e(e, msg)
            GlobalLog.append(Log.ERROR, "CustomCanvas", msg)
            return null
        }
    }

    private fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    }

    private val artistSplitRegex = Regex(
        "(?:\\s*,\\s*|\\s*&\\s*|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
        RegexOption.IGNORE_CASE,
    )

    private fun artistMatches(normRequested: String, normEntry: String): Boolean {
        if (normRequested == normEntry) return true
        val requestedTokens = normRequested.split(artistSplitRegex).map { it.trim() }.filter { it.isNotBlank() }
        val entryTokens = normEntry.split(artistSplitRegex).map { it.trim() }.filter { it.isNotBlank() }
        return requestedTokens.any { req ->
            entryTokens.any { ent -> ent.contains(req) || req.contains(ent) }
        }
    }

    private fun albumMatches(normRequested: String, normEntry: String): Boolean {
        if (normEntry.isBlank()) return normRequested.isBlank()
        if (normRequested.isBlank()) return true
        return normEntry == normRequested ||
                normEntry.contains(normRequested) ||
                normRequested.contains(normEntry)
    }

    private fun cacheKey(artist: String, album: String, song: String?): String =
        "custom|${artist.lowercase()}|${album.lowercase()}|${song?.lowercase() ?: ""}"
}