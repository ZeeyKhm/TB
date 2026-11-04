package com.zeeykhm.tb.data

import java.net.URI

/**
 * Some playlists use relative stream paths (for example `channels/one.m3u8`).
 * Resolve them against the URL the playlist itself was downloaded from.
 */
object PlaylistResolver {

    fun resolve(channels: List<Channel>, baseUrl: String): List<Channel> {
        val base = runCatching { URI(baseUrl) }.getOrNull() ?: return channels

        return channels.map { channel ->
            val raw = channel.url.trim()
            if (raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true)
            ) {
                channel
            } else {
                runCatching { base.resolve(raw).toString() }
                    .getOrNull()
                    ?.let { channel.copy(url = it) }
                    ?: channel
            }
        }
    }
}
