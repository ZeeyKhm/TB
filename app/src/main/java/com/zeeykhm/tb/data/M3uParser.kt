package com.zeeykhm.tb.data

import java.util.Locale

/**
 * Tolerant parser for the M3U / M3U8 playlists used by IPTV providers and
 * public channel indexes (the same dialect VLC accepts).
 *
 * Handles:
 *  - `#EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Name`
 *  - escaped/commas inside attribute values
 *  - single- or double-quoted attribute values
 *  - `#EXTGRP:Group` lines
 *  - `#EXTVLCOPT` and unknown `#` directives (ignored)
 *  - bare URL lists with no `#EXTINF` at all
 *  - BOM, CRLF and CR line endings
 *
 * Duplicate URLs are dropped, keeping the first occurrence.
 */
object M3uParser {

    private val ATTR_REGEX =
        Regex("""([A-Za-z0-9_.:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s]+))""")

    private val DURATION_REGEX = Regex("""^[-+]?\d+(?:\.\d+)?\s*""")

    fun parse(text: String): List<Channel> {
        val channels = LinkedHashMap<String, Channel>()
        val usesExtInf = text.contains("#EXTINF", ignoreCase = true)

        var attributes: Map<String, String> = emptyMap()
        var pendingName: String? = null
        var pendingGroup: String? = null

        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val info = parseExtInf(line)
                    attributes = info.attributes
                    pendingName = info.title.ifBlank {
                        info.attributes["tvg-name"].orEmpty()
                    }
                    pendingGroup = info.attributes["group-title"]
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':').trim().ifBlank { pendingGroup }
                }

                // #EXTM3U, #EXTVLCOPT, #EXT-X-*, anything else commented out.
                line.startsWith("#") -> Unit

                else -> {
                    // Kodi-style lists sometimes put extra/alternate URLs after
                    // a channel (e.g. a second URL or a DASH manifest following
                    // #KODIPROP). Those have no #EXTINF of their own, so skip
                    // them instead of showing a duplicate hostname channel.
                    if (usesExtInf && pendingName == null) continue

                    val name = pendingName?.ifBlank { null }
                        ?: attributes["tvg-name"]?.ifBlank { null }
                        ?: deriveName(line)

                    channels.putIfAbsent(
                        line,
                        Channel(
                            name = name,
                            url = line,
                            group = pendingGroup?.ifBlank { null },
                            logoUrl = attributes["tvg-logo"]?.ifBlank { null },
                            tvgId = attributes["tvg-id"]?.ifBlank { null },
                        ),
                    )

                    attributes = emptyMap()
                    pendingName = null
                    pendingGroup = null
                }
            }
        }

        return channels.values.toList()
    }

    private data class ExtInf(
        val title: String,
        val attributes: Map<String, String>,
    )

    private fun parseExtInf(line: String): ExtInf {
        // "#EXTINF:-1 tvg-id="x",Name" -> "-1 tvg-id="x",Name"
        val body = line.substring("#EXTINF".length)
            .trimStart()
            .removePrefix(":")
            .trimStart()

        val comma = indexOfUnquoted(body, ',')
        val head = if (comma >= 0) body.substring(0, comma) else body
        val title = if (comma >= 0) body.substring(comma + 1).trim() else ""

        // The first token is the duration (-1 for live streams).
        val attributePart = head.trim().replaceFirst(DURATION_REGEX, "")

        return ExtInf(title, parseAttributes(attributePart))
    }

    private fun parseAttributes(input: String): Map<String, String> =
        ATTR_REGEX.findAll(input).associate { match ->
            val value = match.groupValues[2]
                .ifEmpty { match.groupValues[3] }
                .ifEmpty { match.groupValues[4] }
            match.groupValues[1].lowercase(Locale.ROOT) to value.trim()
        }

    /** Finds [char] that is not inside a quoted attribute value. */
    private fun indexOfUnquoted(input: String, char: Char): Int {
        var quote: Char? = null
        for (index in input.indices) {
            val current = input[index]
            when {
                quote != null -> if (current == quote) quote = null
                current == '"' || current == '\'' -> quote = current
                current == char -> return index
            }
        }
        return -1
    }

    private fun deriveName(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val host = withoutScheme.substringBefore('/').substringBefore('?')
        return host.ifBlank { url }
    }
}
