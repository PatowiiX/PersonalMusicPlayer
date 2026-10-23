package com.example.personalmusicplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.session.MediaButtonReceiver
import java.io.File

class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, WidgetUi.build(ctx)) }
    }
}

object WidgetUi {
    private fun artFile(ctx: Context) = File(ctx.filesDir, "widget_art.png")

    fun save(ctx: Context, title: String, artist: String, playing: Boolean, art: Bitmap?) {
        ctx.getSharedPreferences("widget", 0).edit()
            .putString("title", title).putString("artist", artist).putBoolean("playing", playing).apply()
        val f = artFile(ctx)
        if (art != null) f.outputStream().use { art.compress(Bitmap.CompressFormat.PNG, 90, it) } else f.delete()
    }

    private fun mediaPi(ctx: Context, key: Int): PendingIntent {
        val i = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(ctx, MediaButtonReceiver::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, key))
        return PendingIntent.getBroadcast(ctx, key, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun build(ctx: Context): RemoteViews {
        val p = ctx.getSharedPreferences("widget", 0)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_player)

        // Ahora estos IDs existen en el XML de arriba
        rv.setTextViewText(R.id.w_title, p.getString("title", "Personal Music Player"))
        rv.setTextViewText(R.id.w_artist, p.getString("artist", "Toca para abrir"))
        rv.setImageViewResource(R.id.w_play, if (p.getBoolean("playing", false)) R.drawable.ic_pause else R.drawable.ic_play)

        val f = artFile(ctx)
        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        if (bmp != null) rv.setImageViewBitmap(R.id.w_art, bmp) else rv.setImageViewResource(R.id.w_art, R.drawable.ic_music)

        rv.setOnClickPendingIntent(R.id.w_prev, mediaPi(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        rv.setOnClickPendingIntent(R.id.w_play, mediaPi(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        rv.setOnClickPendingIntent(R.id.w_next, mediaPi(ctx, KeyEvent.KEYCODE_MEDIA_NEXT))

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).putExtra("open_player", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        rv.setOnClickPendingIntent(R.id.w_root, open)
        rv.setOnClickPendingIntent(R.id.w_art, open)

        return rv
    }

    fun refresh(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        mgr.getAppWidgetIds(ComponentName(ctx, PlayerWidget::class.java)).forEach { mgr.updateAppWidget(it, build(ctx)) }
    }
}