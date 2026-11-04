package com.zeeykhm.tb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun `parses standard extinf entries`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news.en" tvg-name="News 24" tvg-logo="https://example.com/news.png" group-title="News",News 24 HD
            https://example.com/news/index.m3u8
            #EXTINF:-1 group-title="Sports",Sports One
            http://example.com/sports.ts
        """.trimIndent()

        val channels = M3uParser.parse(playlist)

        assertEquals(2, channels.size)
        assertEquals("News 24 HD", channels[0].name)
        assertEquals("https://example.com/news/index.m3u8", channels[0].url)
        assertEquals("News", channels[0].group)
        assertEquals("news.en", channels[0].tvgId)
        assertEquals("https://example.com/news.png", channels[0].logoUrl)
        assertEquals("Sports One", channels[1].name)
        assertEquals("Sports", channels[1].group)
        assertNull(channels[1].logoUrl)
    }

    @Test
    fun `commas inside quoted attributes are not treated as the title separator`() {
        val playlist = """
            #EXTINF:-1 tvg-name="A, B" group-title="Docs, Science",Real Name
            https://example.com/a.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(playlist)

        assertEquals(1, channels.size)
        assertEquals("Real Name", channels[0].name)
        assertEquals("Docs, Science", channels[0].group)
    }

    @Test
    fun `title may itself contain commas`() {
        val playlist = """
            #EXTINF:-1 group-title="Kids",Cartoons, Volume 2
            https://example.com/kids.m3u8
        """.trimIndent()

        assertEquals("Cartoons, Volume 2", M3uParser.parse(playlist).single().name)
    }

    @Test
    fun `supports single quoted attributes`() {
        val playlist = """
            #EXTINF:-1 tvg-id='abc' group-title='Radio',Radio One
            https://example.com/radio
        """.trimIndent()

        val channel = M3uParser.parse(playlist).single()
        assertEquals("Radio One", channel.name)
        assertEquals("Radio", channel.group)
        assertEquals("abc", channel.tvgId)
    }

    @Test
    fun `handles BOM and CRLF line endings`() {
        val playlist = "\uFEFF#EXTM3U\r\n#EXTINF:-1 group-title=\"A\",One\r\nhttps://example.com/one\r\n"

        val channel = M3uParser.parse(playlist).single()
        assertEquals("One", channel.name)
        assertEquals("https://example.com/one", channel.url)
    }

    @Test
    fun `falls back to tvg-name then to the host`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-name="From Attribute"
            https://example.com/a
            #EXTINF:-1
            https://other.example.org/live/stream
        """.trimIndent()

        val channels = M3uParser.parse(playlist)
        assertEquals("From Attribute", channels[0].name)
        assertEquals("other.example.org", channels[1].name)
    }

    @Test
    fun `applies EXTGRP groups`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,One
            #EXTGRP:Movies
            https://example.com/one
        """.trimIndent()

        assertEquals("Movies", M3uParser.parse(playlist).single().group)
    }

    @Test
    fun `ignores blank lines comments and vlcl opt lines`() {
        val playlist = """
            #EXTM3U

            #EXTVLCOPT:network-caching=3000
            #EXTINF:-1,One
            #EXTVLCOPT:http-user-agent=Custom
            https://example.com/one
        """.trimIndent()

        val channel = M3uParser.parse(playlist).single()
        assertEquals("One", channel.name)
        assertEquals("https://example.com/one", channel.url)
    }

    @Test
    fun `accepts a bare list of urls`() {
        val playlist = """
            https://example.com/a.m3u8
            https://example.com/b.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(playlist)
        assertEquals(2, channels.size)
        assertEquals("example.com", channels[0].name)
        assertEquals("https://example.com/b.m3u8", channels[1].url)
    }

    @Test
    fun `ignores extra urls that have no extinf of their own`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,Channel One
            https://example.com/one.m3u8
            #EXTINF:-1,Channel Two
            https://example.com/two.m3u8
            https://example.com/two-backup.m3u8
            #KODIPROP:inputstream.adaptive.manifest_type=dash
            https://example.com/two/manifest.mpd
        """.trimIndent()

        val channels = M3uParser.parse(playlist)
        assertEquals(listOf("Channel One", "Channel Two"), channels.map { it.name })
    }

    @Test
    fun `keeps only the first occurrence of a duplicated url`() {
        val playlist = """
            #EXTINF:-1,First
            https://example.com/same
            #EXTINF:-1,Second
            https://example.com/same
        """.trimIndent()

        val channels = M3uParser.parse(playlist)
        assertEquals(1, channels.size)
        assertEquals("First", channels[0].name)
    }

    @Test
    fun `preserves playlist order`() {
        val playlist = """
            #EXTINF:-1,Three
            https://example.com/3
            #EXTINF:-1,One
            https://example.com/1
            #EXTINF:-1,Two
            https://example.com/2
        """.trimIndent()

        assertEquals(
            listOf("Three", "One", "Two"),
            M3uParser.parse(playlist).map { it.name },
        )
    }

    @Test
    fun `empty and header-only playlists yield no channels`() {
        assertTrue(M3uParser.parse("").isEmpty())
        assertTrue(M3uParser.parse("#EXTM3U\n").isEmpty())
    }
}
