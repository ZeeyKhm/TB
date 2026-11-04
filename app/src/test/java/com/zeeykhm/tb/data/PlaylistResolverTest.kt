package com.zeeykhm.tb.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistResolverTest {

    @Test
    fun `resolves relative stream paths against the playlist url`() {
        val channels = listOf(
            Channel(name = "Relative", url = "streams/one.m3u8"),
            Channel(name = "Root relative", url = "/live/two.m3u8"),
            Channel(name = "Absolute", url = "https://cdn.example.net/three.m3u8"),
        )

        val resolved = PlaylistResolver.resolve(
            channels,
            "https://example.com/lists/tb.m3u8",
        )

        assertEquals("https://example.com/lists/streams/one.m3u8", resolved[0].url)
        assertEquals("https://example.com/live/two.m3u8", resolved[1].url)
        assertEquals("https://cdn.example.net/three.m3u8", resolved[2].url)
    }

    @Test
    fun `leaves channels untouched when the base url is invalid`() {
        val channels = listOf(Channel(name = "One", url = "streams/one.m3u8"))

        val resolved = PlaylistResolver.resolve(channels, "not a url")

        assertEquals(channels, resolved)
    }
}
