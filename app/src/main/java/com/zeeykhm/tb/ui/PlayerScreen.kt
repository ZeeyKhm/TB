package com.zeeykhm.tb.ui

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    state: TbUiState,
    player: Player,
    onSelectChannel: (Int) -> Unit,
    onRefresh: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSelectMode: (Boolean) -> Unit,
    onTogglePanel: () -> Unit,
    onClosePanel: () -> Unit,
    onRetryPlayback: () -> Unit,
) {
    var showNameBadge by remember { mutableStateOf(false) }
    var resumeOnStart by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(enabled = state.panelOpen) { onClosePanel() }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeOnStart = player.playWhenReady
                    player.pause()
                }

                Lifecycle.Event.ON_START -> if (resumeOnStart) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.currentChannel?.url) {
        if (state.currentChannel != null) {
            showNameBadge = true
            delay(2_600)
            showNameBadge = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val landscape = maxWidth > maxHeight

        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    setPlayer(player)
                }
            },
            update = { view -> view.player = player },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // A single tap anywhere on the video toggles the channel list.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTogglePanel() })
                },
        )

        if (state.buffering && state.playbackError == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = TbAccent,
                    strokeWidth = 2.5.dp,
                )
            }
        }

        AnimatedVisibility(
            visible = showNameBadge && !state.panelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp),
        ) {
            ChannelBadge(
                name = state.currentChannel?.name.orEmpty(),
                group = state.currentChannel?.group,
            )
        }

        if (state.offline && !state.panelOpen) {
            OfflineBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp),
            )
        }

        state.playbackError?.let { message ->
            PlaybackErrorCard(
                message = message,
                onRetry = onRetryPlayback,
                onNext = {
                    if (state.channels.isNotEmpty()) {
                        onSelectChannel((state.currentIndex + 1) % state.channels.size)
                    }
                },
                onOpenChannels = onTogglePanel,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        }

        AnimatedVisibility(
            visible = state.panelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClosePanel() },
            )
        }

        AnimatedVisibility(
            visible = state.panelOpen,
            enter = if (landscape) {
                slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
            } else {
                slideInVertically(initialOffsetY = { it }) + fadeIn()
            },
            exit = if (landscape) {
                slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            } else {
                slideOutVertically(targetOffsetY = { it }) + fadeOut()
            },
            modifier = Modifier.align(
                if (landscape) Alignment.CenterStart else Alignment.BottomCenter,
            ),
        ) {
            ChannelPanel(
                state = state,
                onSelect = { index ->
                    onSelectChannel(index)
                    onClosePanel()
                },
                onClose = onClosePanel,
                onRefresh = onRefresh,
                onTogglePlayPause = onTogglePlayPause,
                onSelectMode = onSelectMode,
                landscape = landscape,
            )
        }
    }
}
