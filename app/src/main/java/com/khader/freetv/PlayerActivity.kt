package com.khader.freetv

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var errorBox: LinearLayout
    private var player: ExoPlayer? = null
    private var url = ""
    private var retries = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        root.addView(playerView, FrameLayout.LayoutParams(match, match))

        titleView = TextView(this).apply {
            setBackgroundColor(0x66000000)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setTextColor(Color.WHITE)
            textSize = 16f
            text = intent.getStringExtra("name") ?: ""
        }
        root.addView(titleView, FrameLayout.LayoutParams(match, wrap))

        errorBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        errorBox.addView(TextView(this).apply {
            text = "تعذّر تشغيل القناة (قد تكون متوقفة أو محجوبة في منطقتك)"
            setTextColor(Color.WHITE)
        })
        errorBox.addView(Button(this).apply {
            text = "إعادة المحاولة"
            setOnClickListener { retries = 0; start() }
        })
        root.addView(errorBox, FrameLayout.LayoutParams(wrap, wrap, Gravity.CENTER))

        setContentView(root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        url = intent.getStringExtra("url") ?: ""
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { v ->
                titleView.visibility = if (v == View.VISIBLE) View.VISIBLE else View.GONE
            }
        )
    }

    private fun start() {
        player?.release()
        errorBox.visibility = View.GONE
        val ds = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ds))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (retries < 2) { retries++; start() } else errorBox.visibility = View.VISIBLE
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { retries = 0; errorBox.visibility = View.GONE }
            }
        })
        playerView.player = p
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
        player = p
    }

    override fun onStart() { super.onStart(); start() }
    override fun onStop() { super.onStop(); player?.release(); player = null }
}
