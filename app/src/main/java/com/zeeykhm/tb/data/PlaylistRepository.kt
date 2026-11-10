package com.zeeykhm.tb.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class PlaylistResult(
    val channels: List<Channel>,
    val offline: Boolean,
)

/**
 * Downloads the playlist over HTTP(S), parses it, and keeps the last good copy
 * on disk so the app still has channels when GitHub is unreachable.
 */
class PlaylistRepository(context: Context) {

    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, "last_playlist.m3u8")
    private val hiddenCacheFile = File(appContext.filesDir, "hidden_playlists.m3u8")

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun load(url: String, forceRefresh: Boolean): PlaylistResult =
        withContext(Dispatchers.IO) {
            try {
                val (body, finalUrl) = download(url, forceRefresh)
                if (!looksLikePlaylist(body)) {
                    throw IOException("That URL doesn't look like an M3U playlist.")
                }

                val channels = parse(body, finalUrl)
                if (channels.isEmpty()) {
                    throw IOException("This playlist has no channels.")
                }

                runCatching { cacheFile.writeText(body) }
                PlaylistResult(channels, offline = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val cached = readCache()?.let { parse(it, url) }
                if (!cached.isNullOrEmpty()) {
                    PlaylistResult(cached, offline = true)
                } else {
                    throw error
                }
            }
        }

    /** The last good playlist on disk, for an instant start while the network refreshes. */
    suspend fun cached(url: String): PlaylistResult? = withContext(Dispatchers.IO) {
        val body = readCache() ?: return@withContext null
        val channels = runCatching { parse(body, url) }.getOrNull()
        if (channels.isNullOrEmpty()) null else PlaylistResult(channels, offline = true)
    }

    /**
     * Downloads several playlists (the PIN-protected hidden channels), parses
     * each against its own URL and merges them by stream URL. A playlist that
     * fails to download is skipped so the others still load.
     */
    suspend fun loadAll(urls: List<String>, forceRefresh: Boolean): PlaylistResult =
        withContext(Dispatchers.IO) {
            val downloaded = coroutineScope {
                urls.map { url ->
                    async { runCatching { download(url, forceRefresh) }.getOrNull() }
                }.awaitAll()
            }.filterNotNull()

            if (downloaded.isEmpty()) {
                val cached = readHiddenCache()
                    ?.let { body ->
                        runCatching { parse(body, urls.firstOrNull().orEmpty()) }.getOrNull()
                    }
                if (!cached.isNullOrEmpty()) {
                    return@withContext PlaylistResult(cached, offline = true)
                }
                throw IOException("Couldn't load the hidden playlists.")
            }

            val channels = downloaded
                .filter { (body, _) -> looksLikePlaylist(body) }
                .flatMap { (body, finalUrl) -> parse(body, finalUrl) }
                .distinctBy { it.url }
            if (channels.isEmpty()) {
                throw IOException("The hidden playlists have no channels.")
            }

            runCatching {
                hiddenCacheFile.writeText(downloaded.joinToString("\n") { it.first })
            }
            PlaylistResult(channels, offline = downloaded.size < urls.size)
        }

    /**
     * Downloads [url]. If a `.m3u8` playlist is not there yet (404), retries the
     * same name with the older `.m3u` extension so renaming the file on GitHub
     * does not break existing installs.
     */
    private fun download(url: String, forceRefresh: Boolean): Pair<String, String> =
        try {
            downloadOnce(url, forceRefresh)
        } catch (error: HttpStatusException) {
            val fallback = legacyExtensionUrl(url)
            if (error.code == 404 && fallback != null) {
                downloadOnce(fallback, forceRefresh)
            } else {
                throw error
            }
        }

    private fun downloadOnce(url: String, forceRefresh: Boolean): Pair<String, String> {
        val requestUrl = if (forceRefresh) bypassCdn(url) else url
        val request = Request.Builder()
            .url(requestUrl)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "TB/1.0 (Android)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code)
            }

            val body = response.body?.string()
                ?: throw IOException("The playlist URL returned an empty response.")
            return body to response.request.url.toString()
        }
    }

    /** `…/tb.m3u8?x=1` -> `…/tb.m3u?x=1`; null for any other URL. */
    private fun legacyExtensionUrl(url: String): String? {
        val pathEnd = url.indexOfFirst { it == '?' || it == '#' }
            .let { if (it < 0) url.length else it }
        val path = url.substring(0, pathEnd)
        if (!path.endsWith(".m3u8", ignoreCase = true)) return null
        return path.dropLast(1) + url.substring(pathEnd)
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private fun parse(text: String, baseUrl: String): List<Channel> =
        PlaylistResolver.resolve(M3uParser.parse(text), baseUrl)

    private fun readCache(): String? =
        runCatching { cacheFile.takeIf { it.isFile }?.readText() }.getOrNull()

    private fun readHiddenCache(): String? =
        runCatching { hiddenCacheFile.takeIf { it.isFile }?.readText() }.getOrNull()

    private fun looksLikePlaylist(text: String): Boolean {
        if (text.isBlank()) return false
        val head = text.take(8_192).uppercase()
        if (head.contains("#EXTM3U") || head.contains("#EXTINF")) return true

        // Bare URL lists are valid too.
        return text.lineSequence().any { line ->
            val trimmed = line.trim()
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
        }
    }

    /**
     * raw.githubusercontent.com sits behind a CDN that can serve an old copy for
     * a few minutes. A unique query string makes a manual refresh actually fresh.
     * Other hosts are left untouched.
     */
    private fun bypassCdn(url: String): String {
        if (!url.contains("raw.githubusercontent.com", ignoreCase = true)) return url
        val separator = if (url.contains('?')) '&' else '?'
        return "$url${separator}refresh=${System.currentTimeMillis()}"
    }
}
