package com.zeeykhm.tb.data

/**
 * One playable entry from an M3U playlist.
 *
 * [url] is the stable identity of a channel: playlists sometimes repeat display
 * names, but never (meaningfully) repeat the same stream URL.
 */
data class Channel(
    val name: String,
    val url: String,
    val group: String? = null,
    val logoUrl: String? = null,
    val tvgId: String? = null,
) {
    val key: String get() = url
}
