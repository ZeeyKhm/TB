package com.zeeykhm.tb.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.zeeykhm.tb.data.Channel
import com.zeeykhm.tb.data.PlaylistRepository
import com.zeeykhm.tb.data.SettingsStore
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TbUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val currentIndex: Int = 0,
    val sourceUrl: String = "",
    val offline: Boolean = false,
    val error: String? = null,
    val playbackError: String? = null,
    val buffering: Boolean = false,
    val isPlaying: Boolean = false,
) {
    val currentChannel: Channel? get() = channels.getOrNull(currentIndex)
}

/**
 * Owns the playlist and the single ExoPlayer instance. Keeping the player in the
 * ViewModel means rotating the phone does not interrupt playback.
 */
class TbViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistRepository(application)
    private val settings = SettingsStore(application)

    private val _state = MutableStateFlow(
        TbUiState(sourceUrl = settings.playlistUrl),
    )
    val state: StateFlow<TbUiState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(application)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var loadJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _state.update {
                    it.copy(
                        playbackError = playbackMessage(error),
                        buffering = false,
                        isPlaying = false,
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(
                        buffering = playbackState == Player.STATE_BUFFERING,
                        isPlaying = player.isPlaying,
                    )
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }
        })

        load(resetToFirst = true)
    }

    fun refresh() {
        load(resetToFirst = false)
    }

    fun setSource(url: String) {
        settings.playlistUrl = url
        player.stop()
        player.clearMediaItems()
        _state.update {
            it.copy(
                channels = emptyList(),
                currentIndex = 0,
                sourceUrl = settings.playlistUrl,
                offline = false,
                error = null,
                playbackError = null,
                isPlaying = false,
            )
        }
        load(resetToFirst = true)
    }

    fun resetSource() {
        settings.resetSource()
        setSource(settings.playlistUrl)
    }

    fun play(index: Int) {
        val channel = _state.value.channels.getOrNull(index) ?: return

        _state.update {
            it.copy(
                currentIndex = index,
                playbackError = null,
                buffering = true,
            )
        }

        val mediaItem = MediaItem.Builder()
            .setUri(channel.url)
            .apply {
                if (channel.url.contains(".m3u8", ignoreCase = true) ||
                    channel.url.contains(".m3u", ignoreCase = true)
                ) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun retryPlayback() {
        play(_state.value.currentIndex)
    }

    fun togglePlayPause() {
        when {
            player.playbackState == Player.STATE_IDLE -> play(_state.value.currentIndex)
            player.playbackState == Player.STATE_ENDED -> {
                player.seekToDefaultPosition()
                player.play()
            }
            player.isPlaying -> player.pause()
            else -> player.play()
        }
    }

    private fun load(resetToFirst: Boolean) {
        val url = settings.playlistUrl
        _state.update {
            it.copy(
                sourceUrl = url,
                loading = it.channels.isEmpty(),
                refreshing = it.channels.isNotEmpty(),
                error = null,
            )
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val result = repository.load(url, forceRefresh = true)
                if (!isActive) return@launch

                val previousUrl = _state.value.currentChannel?.url
                val index = if (resetToFirst) {
                    0
                } else {
                    result.channels.indexOfFirst { it.url == previousUrl }
                        .takeIf { it >= 0 }
                        ?: 0
                }

                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        channels = result.channels,
                        currentIndex = index,
                        offline = result.offline,
                        error = null,
                    )
                }
                play(index)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    if (it.channels.isEmpty()) {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = loadMessage(error),
                        )
                    } else {
                        // Keep playing the list we already have.
                        it.copy(
                            loading = false,
                            refreshing = false,
                            offline = true,
                        )
                    }
                }
            }
        }
    }

    private fun loadMessage(error: Throwable): String {
        val raw = error.message?.takeIf { it.isNotBlank() }
        return when {
            error is UnknownHostException ->
                "Can’t reach the playlist host. Check your internet connection."
            error is SocketTimeoutException ->
                "The playlist took too long to load. Try again."
            raw != null && raw.startsWith("HTTP ") ->
                "The playlist URL returned $raw. Check that the repo is public and the path is correct."
            raw != null -> raw
            else -> "Couldn’t download the playlist."
        }
    }

    private fun playbackMessage(error: PlaybackException): String =
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> "The stream is unreachable. Check the channel URL or your connection."

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            -> "The stream server returned an error."

            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            -> "This stream uses a format this device can’t decode."

            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ->
                "The live stream fell behind. Retry usually fixes it."

            else -> "Playback failed (${error.errorCodeName})."
        }

    override fun onCleared() {
        loadJob?.cancel()
        player.release()
        super.onCleared()
    }
}
