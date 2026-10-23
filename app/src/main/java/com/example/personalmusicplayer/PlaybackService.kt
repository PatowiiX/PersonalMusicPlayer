package com.example.personalmusicplayer

import android.app.PendingIntent
import android.content.Intent
import android.util.Size
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlin.concurrent.thread

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Al tocar la notificación se abre la app directo en "Reproduciendo"
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra("open_player", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) = push(player)
            override fun onIsPlayingChanged(isPlaying: Boolean) = push(player)
        })
    }

    private fun push(p: Player) {
        val item = p.currentMediaItem
        val title = item?.mediaMetadata?.title?.toString() ?: "Personal Music Player"
        val artist = item?.mediaMetadata?.artist?.toString() ?: ""
        val playing = p.isPlaying
        val uri = item?.localConfiguration?.uri
        thread {
            val bmp = uri?.let { runCatching { contentResolver.loadThumbnail(it, Size(160, 160), null) }.getOrNull() }
            WidgetUi.save(this, title, artist, playing, bmp)
            WidgetUi.refresh(this)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
