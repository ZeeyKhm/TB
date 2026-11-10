package com.zeeykhm.tb

import android.annotation.SuppressLint
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.zeeykhm.tb.ui.TbApp
import com.zeeykhm.tb.ui.TbTheme
import com.zeeykhm.tb.ui.TbViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: TbViewModel
    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        viewModel = ViewModelProvider(this)[TbViewModel::class.java]
        audioManager = getSystemService(AudioManager::class.java)

        setContent {
            TbTheme {
                TbApp(viewModel)
            }
        }
    }

    /**
     * TV remote mapping:
     *   left/right -> previous/next channel (also closes the channel list)
     *   up/down    -> volume while watching; list navigation while the list is open
     *   OK         -> open the channel list, or activate the focused row when it is open
     *
     * Error cards keep the normal D-pad so their buttons stay reachable.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val state = viewModel.state.value
        val ready = state.playbackError == null && state.channels.isNotEmpty()

        if (ready && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_CHANNEL_DOWN,
                -> {
                    if (event.repeatCount == 0) viewModel.previousChannel()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_CHANNEL_UP,
                -> {
                    if (event.repeatCount == 0) viewModel.nextChannel()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!state.panelOpen) {
                        adjustVolume(AudioManager.ADJUST_RAISE)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!state.panelOpen) {
                        adjustVolume(AudioManager.ADJUST_LOWER)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                -> {
                    if (!state.panelOpen) {
                        if (event.repeatCount == 0) viewModel.openPanel()
                        return true
                    }
                }

                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (event.repeatCount == 0) viewModel.togglePlayPause()
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun adjustVolume(direction: Int) {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI,
        )
    }
}
