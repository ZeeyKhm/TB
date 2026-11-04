package com.zeeykhm.tb.data

import com.zeeykhm.tb.BuildConfig

/**
 * Holds the playlist URL for the current session only.
 *
 * Every cold start begins at [BuildConfig.PLAYLIST_URL], so opening the app
 * always goes straight to the channels in the GitHub playlist with no setup.
 * The source dialog can still point at another list until the app is closed.
 */
class SettingsStore {

    var playlistUrl: String = BuildConfig.PLAYLIST_URL
        set(value) {
            field = value.trim().ifBlank { BuildConfig.PLAYLIST_URL }
        }

    fun resetSource() {
        playlistUrl = BuildConfig.PLAYLIST_URL
    }
}
