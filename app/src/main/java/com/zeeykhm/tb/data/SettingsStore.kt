package com.zeeykhm.tb.data

import android.content.Context
import com.zeeykhm.tb.BuildConfig

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The playlist URL the app should use. Falls back to the URL compiled into
     * the build, so a fresh install works without any setup.
     */
    var playlistUrl: String
        get() = prefs.getString(KEY_PLAYLIST_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.PLAYLIST_URL
        set(value) {
            val trimmed = value.trim()
            if (trimmed == BuildConfig.PLAYLIST_URL) {
                prefs.edit().remove(KEY_PLAYLIST_URL).apply()
            } else {
                prefs.edit().putString(KEY_PLAYLIST_URL, trimmed).apply()
            }
        }

    fun resetSource() {
        prefs.edit().remove(KEY_PLAYLIST_URL).apply()
    }

    private companion object {
        const val PREFS_NAME = "tb_settings"
        const val KEY_PLAYLIST_URL = "playlist_url"
    }
}
