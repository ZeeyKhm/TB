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
    /** Audio-only station, shown in the Radio section instead of TV. */
    val isRadio: Boolean = false,
    /** `dash`, `hls`, … from `#KODIPROP:inputstream.adaptive.manifest_type`. */
    val manifestType: String? = null,
    val drm: DrmInfo? = null,
    /** Extra HTTP headers from `#EXTVLCOPT` or a `|Header=value` URL suffix. */
    val httpHeaders: Map<String, String> = emptyMap(),
) {
    val key: String get() = url
}
