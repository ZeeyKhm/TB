package com.zeeykhm.tb.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    private val cacheFile = File(appContext.filesDir, "last_playlist.m3u")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun load(url: String, forceRefresh: Boolean): PlaylistResult =
        withContext(Dispatchers.IO) {
            try {
                val requestUrl = if (forceRefresh) bypassCdn(url) else url
                val request = Request.Builder()
                    .url(requestUrl)
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", "TB/1.0 (Android)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }

                    val body = response.body?.string()
                        ?: throw IOException("The playlist URL returned an empty response.")
                    if (!looksLikePlaylist(body)) {
                        throw IOException("That URL doesn't look like an M3U playlist.")
                    }

                    val channels = parse(body, response.request.url.toString())
                    if (channels.isEmpty()) {
                        throw IOException("This playlist has no channels.")
                    }

                    runCatching { cacheFile.writeText(body) }
                    PlaylistResult(channels, offline = false)
                }
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

    private fun parse(text: String, baseUrl: String): List<Channel> =
        PlaylistResolver.resolve(M3uParser.parse(text), baseUrl)

    private fun readCache(): String? =
        runCatching { cacheFile.takeIf { it.isFile }?.readText() }.getOrNull()

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
