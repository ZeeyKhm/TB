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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.zeeykhm.tb.data.Channel
import com.zeeykhm.tb.data.DrmInfo
import com.zeeykhm.tb.data.DrmScheme
import com.zeeykhm.tb.data.PlaylistRepository
import com.zeeykhm.tb.data.SettingsStore
import org.json.JSONObject
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
    val panelOpen: Boolean = false,
) {
    val currentChannel: Channel? get() = channels.getOrNull(currentIndex)
}

/**
 * Owns the playlist and the single ExoPlayer instance. Keeping the player in the
 * ViewModel means rotating the phone does not interrupt playback.
 */
class TbViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistRepository(application)
    private val settings = SettingsStore()

    private val _state = MutableStateFlow(
        TbUiState(sourceUrl = settings.playlistUrl),
    )
    val state: StateFlow<TbUiState> = _state.asStateFlow()

    /**
     * Tuned for live TV: start after a short buffer, keep the read-ahead small
     * and let the player nudge playback speed so it stays near the live edge.
     */
    val player: ExoPlayer = ExoPlayer.Builder(
        application,
        DefaultRenderersFactory(application).setEnableDecoderFallback(true),
    )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 2_000,
                    /* maxBufferMs = */ 12_000,
                    /* bufferForPlaybackMs = */ 700,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_200,
                )
                .setBackBuffer(0, false)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .setLivePlaybackSpeedControl(
            DefaultLivePlaybackSpeedControl.Builder()
                .setFallbackMinPlaybackSpeed(0.95f)
                .setFallbackMaxPlaybackSpeed(1.08f)
                .setMinUpdateIntervalMs(1_000)
                .build(),
        )
        .setWakeMode(C.WAKE_MODE_NETWORK)
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

    /** ClearKey licences are local, so they need a hand-built DRM session manager. */
    private val drmManagers = mutableMapOf<DrmInfo, DrmSessionManager>()

    init {
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)

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

        load(resetToFirst = true, allowCacheFirst = true)
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
                panelOpen = false,
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

        val mediaItem = buildMediaItem(channel)
        val factory = createMediaSourceFactory(channel)

        val clearKey = channel.drm?.takeIf { it.scheme == DrmScheme.CLEARKEY }
        if (clearKey != null) {
            val manager = drmManagers.getOrPut(clearKey) { createClearKeyManager(clearKey) }
            factory.setDrmSessionManagerProvider(DrmSessionManagerProvider { manager })
        }

        player.setMediaSource(factory.createMediaSource(mediaItem))
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Per-channel HTTP headers: some CDNs reject the default player agent or
     * require a Referer, so `#EXTVLCOPT` values are applied to every request
     * for that channel (manifest and segments alike).
     */
    private fun createMediaSourceFactory(channel: Channel): DefaultMediaSourceFactory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(5_000)
            .setReadTimeoutMs(10_000)
            .setAllowCrossProtocolRedirects(true)

        channel.httpHeaders["User-Agent"]
            ?.takeIf { it.isNotBlank() }
            ?.let { httpFactory.setUserAgent(it) }

        val requestHeaders = channel.httpHeaders
            .filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        if (requestHeaders.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(requestHeaders)
        }

        return DefaultMediaSourceFactory(
            DefaultDataSource.Factory(getApplication<Application>(), httpFactory),
        )
            // Stay close to the live edge; speed control catches up after a hiccup.
            .setLiveTargetOffsetMs(2_500)
            .setLiveMinOffsetMs(1_500)
            .setLiveMaxOffsetMs(12_000)
            .setLiveMinSpeed(0.95f)
            .setLiveMaxSpeed(1.08f)
    }

    /**
     * Widevine is handled by Media3's default DRM provider through the media
     * item's [MediaItem.DrmConfiguration]; the playlist supplies the licence URL.
     */
    private fun buildMediaItem(channel: Channel): MediaItem {
        val builder = MediaItem.Builder().setUri(channel.url)

        mimeTypeFor(channel)?.let { mime -> builder.setMimeType(mime) }

        channel.drm?.takeIf { it.scheme == DrmScheme.WIDEVINE }?.let { drm ->
            val configuration = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .apply {
                    drm.licenseUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        setLicenseUri(url)
                    }
                    if (drm.licenseHeaders.isNotEmpty()) {
                        setLicenseRequestHeaders(drm.licenseHeaders)
                    }
                }
                .build()
            builder.setDrmConfiguration(configuration)
        }

        return builder.build()
    }

    private fun mimeTypeFor(channel: Channel): String? {
        val declared = channel.manifestType?.lowercase()
        return when {
            declared == "dash" || declared == "mpd" -> MimeTypes.APPLICATION_MPD
            declared == "hls" || declared == "m3u8" -> MimeTypes.APPLICATION_M3U8
            channel.url.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            channel.url.contains(".m3u8", ignoreCase = true) ||
                channel.url.contains(".m3u", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            else -> null
        }
    }

    private fun createClearKeyManager(drm: DrmInfo): DrmSessionManager {
        val callback = LocalMediaDrmCallback(clearKeyResponse(drm.clearKeyJson.orEmpty()))
        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .build(callback)
    }

    /** ClearKey requires base64url values, while Kodi playlists often use base64. */
    private fun clearKeyResponse(json: String): ByteArray =
        runCatching {
            val root = JSONObject(json)
            val keys = root.optJSONArray("keys")
            if (keys != null) {
                for (index in 0 until keys.length()) {
                    val key = keys.optJSONObject(index) ?: continue
                    for (field in listOf("k", "kid")) {
                        val value = key.optString(field)
                        if (value.isNotEmpty()) {
                            key.put(field, base64Url(value))
                        }
                    }
                }
            }
            root.toString().toByteArray(Charsets.UTF_8)
        }.getOrElse { json.toByteArray(Charsets.UTF_8) }

    private fun base64Url(value: String): String =
        value.replace('+', '-').replace('/', '_').trimEnd('=')

    fun retryPlayback() {
        play(_state.value.currentIndex)
    }

    /** Left/right on the remote zaps channels, wrapping at either end. */
    fun nextChannel() = stepChannel(1)

    fun previousChannel() = stepChannel(-1)

    private fun stepChannel(delta: Int) {
        val current = _state.value
        if (current.channels.isEmpty()) return
        val next = (current.currentIndex + delta + current.channels.size) % current.channels.size
        if (next != current.currentIndex) {
            closePanel()
            play(next)
        }
    }

    fun openPanel() {
        _state.update { it.copy(panelOpen = true) }
    }

    fun closePanel() {
        _state.update { it.copy(panelOpen = false) }
    }

    fun togglePanel() {
        _state.update { it.copy(panelOpen = !it.panelOpen) }
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

    private fun load(resetToFirst: Boolean, allowCacheFirst: Boolean = false) {
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
            // Start from the last good playlist immediately, then refresh it.
            if (allowCacheFirst && _state.value.channels.isEmpty()) {
                repository.cached(url)?.let { cached ->
                    if (!isActive) return@launch
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = true,
                            channels = cached.channels,
                            currentIndex = 0,
                            offline = true,
                            error = null,
                        )
                    }
                    play(0)
                }
            }

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

                // Don't restart a stream that is already playing the same channel.
                val nextUrl = result.channels.getOrNull(index)?.url
                val needsPlay = nextUrl != previousUrl ||
                    player.playbackState == Player.STATE_IDLE ||
                    _state.value.playbackError != null
                if (needsPlay) play(index)
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

            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            -> "The channel’s DRM licence couldn’t be loaded."

            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED ->
                "This device doesn’t support the DRM this channel uses."

            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED ->
                "The DRM licence for this channel has expired."

            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
            -> "DRM playback is not allowed on this device."

            else -> "Playback failed (${error.errorCodeName})."
        }

    override fun onCleared() {
        loadJob?.cancel()
        drmManagers.values.forEach { runCatching { it.release() } }
        drmManagers.clear()
        player.release()
        super.onCleared()
    }
}
