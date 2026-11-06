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
 *  - `#EXTVLCOPT` user-agent/referrer headers
 *  - `#KODIPROP` manifest type and Widevine/ClearKey DRM details
 *  - `url|Header=value&Header=value` suffixes
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
        var pendingLicenseType: String? = null
        var pendingLicenseKey: String? = null
        var pendingManifestType: String? = null
        val pendingHeaders = LinkedHashMap<String, String>()

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
                    pendingLicenseType = null
                    pendingLicenseKey = null
                    pendingManifestType = null
                    pendingHeaders.clear()
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':').trim().ifBlank { pendingGroup }
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = line.substringAfter(':')
                    val key = option.substringBefore('=').trim().lowercase(Locale.ROOT)
                    val value = option.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" ->
                            if (value.isNotEmpty()) pendingHeaders["User-Agent"] = value
                        "http-referrer", "http-referer" ->
                            if (value.isNotEmpty()) pendingHeaders["Referer"] = value
                    }
                }

                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    val prop = line.substringAfter(':')
                    when (prop.substringBefore('=').trim().lowercase(Locale.ROOT)) {
                        "inputstream.adaptive.license_type" ->
                            pendingLicenseType = prop.substringAfter('=', "").trim()
                        "inputstream.adaptive.license_key" ->
                            pendingLicenseKey = prop.substringAfter('=', "").trim()
                        "inputstream.adaptive.manifest_type" ->
                            pendingManifestType = prop.substringAfter('=', "").trim()
                    }
                }

                // #EXTM3U, #EXT-X-*, anything else commented out.
                line.startsWith("#") -> Unit

                else -> {
                    // Kodi-style lists sometimes put extra/alternate URLs after
                    // a channel (e.g. a second URL or a DASH manifest following
                    // #KODIPROP). Those have no #EXTINF of their own, so skip
                    // them instead of showing a duplicate hostname channel.
                    if (usesExtInf && pendingName == null) {
                        pendingLicenseType = null
                        pendingLicenseKey = null
                        pendingManifestType = null
                        pendingHeaders.clear()
                        continue
                    }

                    val (url, urlHeaders) = splitUrlHeaders(line)

                    val name = pendingName?.ifBlank { null }
                        ?: attributes["tvg-name"]?.ifBlank { null }
                        ?: deriveName(url)

                    channels.putIfAbsent(
                        url,
                        Channel(
                            name = name,
                            url = url,
                            group = pendingGroup?.ifBlank { null },
                            logoUrl = attributes["tvg-logo"]?.ifBlank { null },
                            tvgId = attributes["tvg-id"]?.ifBlank { null },
                            manifestType = pendingManifestType?.ifBlank { null },
                            drm = buildDrm(pendingLicenseType, pendingLicenseKey),
                            httpHeaders = pendingHeaders + urlHeaders,
                        ),
                    )

                    attributes = emptyMap()
                    pendingName = null
                    pendingGroup = null
                    pendingLicenseType = null
                    pendingLicenseKey = null
                    pendingManifestType = null
                    pendingHeaders.clear()
                }
            }
        }

        return channels.values.toList()
    }

    /**
     * Kodi streams can carry request headers after a pipe:
     * `https://host/live.m3u8|User-Agent=Mozilla/5.0&Referer=https://example.com/`.
     */
    private fun splitUrlHeaders(raw: String): Pair<String, Map<String, String>> {
        val pipe = raw.indexOf('|')
        if (pipe < 0) return raw to emptyMap()

        val url = raw.substring(0, pipe).trim()
        val headerPart = raw.substring(pipe + 1)
        if (!headerPart.contains('=')) return raw to emptyMap()

        val headers = LinkedHashMap<String, String>()
        for (pair in headerPart.split('&')) {
            if (!pair.contains('=')) continue
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=', "").trim()
            if (name.isEmpty()) continue
            val canonical = when (name.lowercase(Locale.ROOT)) {
                "user-agent" -> "User-Agent"
                "referer", "referrer" -> "Referer"
                else -> name
            }
            headers[canonical] = value
        }
        return url to headers
    }

    private fun buildDrm(licenseType: String?, licenseKey: String?): DrmInfo? {
        val type = licenseType?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        val key = licenseKey?.trim().orEmpty()

        return when {
            "widevine" in type -> {
                val (url, headers) = parseWidevineKey(key)
                url?.let { DrmInfo(DrmScheme.WIDEVINE, licenseUrl = it, licenseHeaders = headers) }
            }

            "clearkey" in type && key.startsWith("{") ->
                DrmInfo(DrmScheme.CLEARKEY, clearKeyJson = key)

            else -> null
        }
    }

    /**
     * Kodi licence keys look like `https://server|Header=value&Other=value|`.
     * The optional `R{SSM}` part is the DRM challenge placeholder, not a header.
     */
    private fun parseWidevineKey(raw: String): Pair<String?, Map<String, String>> {
        if (raw.isBlank()) return null to emptyMap()

        val parts = raw.split('|')
        val url = parts.first().trim().takeIf {
            it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true)
        }

        val headers = LinkedHashMap<String, String>()
        for (part in parts.drop(1)) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.equals("R{SSM}", ignoreCase = true) ||
                trimmed.equals("{SSM}", ignoreCase = true)
            ) {
                continue
            }

            for (pair in trimmed.split('&')) {
                if (!pair.contains('=')) continue
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isNotEmpty() && !name.contains('{')) {
                    headers[name] = value
                }
            }
        }
        return url to headers
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
