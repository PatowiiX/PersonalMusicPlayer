package com.example.personalmusicplayer

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.resume
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.random.Random

val DefaultAccent = Color(0xFF3A3A3A)

/**
 * Elige el color más llamativo de la carátula y lo hace vivo: sube la saturación y mantiene un brillo
 * medio, para que el tinte se note aunque el álbum sea apagado u oscuro.
 */
private fun vividAccent(p: Palette): Color? {
    val sw = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch
        ?: p.mutedSwatch ?: p.dominantSwatch ?: return null
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(sw.rgb, hsv)
    // Gris casi sin color: se respeta (no inventar un tono), solo se aclara un poco
    if (hsv[1] < 0.08f) { hsv[2] = hsv[2].coerceIn(0.35f, 0.75f); return Color(android.graphics.Color.HSVToColor(hsv)) }
    hsv[1] = hsv[1].coerceIn(0.55f, 1f)
    hsv[2] = hsv[2].coerceIn(0.55f, 0.95f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Función suelta para poder armar los MediaItem fuera del hilo principal (listas grandes). */
private fun mediaItemOf(s: Song): MediaItem =
    MediaItem.Builder().setMediaId(s.id.toString()).setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
                .setExtras(Bundle().apply { putString("album", s.album); putInt("year", s.year) })
                .build()
        ).build()

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val dao = AppDb.get(app).dao()
    val profile = ProfileStore(app)
    val bluetooth = BluetoothMonitor(app)

    val allSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlists = dao.playlists()
    val entries = dao.entries()
    val excluded = profile.excluded.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val vinylMode = profile.vinylMode.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val coverShape = profile.coverShape.stateIn(viewModelScope, SharingStarted.Eagerly, "auto")
    val colorMode = profile.colorMode.stateIn(viewModelScope, SharingStarted.Eagerly, "dynamic")

    val library: StateFlow<List<Song>> = combine(allSongs, excluded) { all, ex -> all.filter { it.folder !in ex } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val current = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    private val _shuffle = MutableStateFlow(false)
    /** Refleja el estado real del reproductor: la UI cambia al instante al pulsar aleatorio. */
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()
    val uiStyle = profile.style.stateIn(viewModelScope, SharingStarted.Eagerly, "classic")
    val favorites = profile.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet<Long>())
    val scanStatus = MutableStateFlow("")
    val position = MutableStateFlow(0L)
    val art = MutableStateFlow<Bitmap?>(null)
    val accent = MutableStateFlow(DefaultAccent)
    val quality = MutableStateFlow("")
    val requestPlayerTab = MutableStateFlow(false)
    /** Duración total de la pista actual (para el slider cuando no hay metadatos). */
    val duration = MutableStateFlow(0L)

    val topSongs = dao.topSongs()
    val topArtists = dao.topArtists()
    val topGenres = dao.topGenres()
    val totalMs = dao.totalMs()
    val history: Flow<List<PlayEvent>> = dao.history()

    val sleepMinutesLeft = MutableStateFlow(0)
    private var sleepTimerJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            sleepMinutesLeft.value = 0
            return
        }
        sleepMinutesLeft.value = minutes
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000L)
                remaining--
                sleepMinutesLeft.value = remaining
            }
            controller?.pause()
            isPlaying.value = false
            sleepMinutesLeft.value = 0
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepMinutesLeft.value = 0
    }

    fun isFavorite(id: Long): Flow<Boolean> = profile.favorites.map { id in it }

    private var controller: MediaController? = null
    private var byId = mapOf<Long, Song>()
    private var pendingMs = 0L
    private var restored = false
    private var ffJob: Job? = null
    private var playJob: Job? = null
    private var artJob: Job? = null
    /** Scope que sobrevive a onCleared() para guardar el estado final. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            flushTime()
            setCurrent(item?.mediaId?.toLongOrNull(), count = true)
        }
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying.value = playing
            if (!playing) flushTime()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (_shuffle.value != shuffleModeEnabled) {
                _shuffle.value = shuffleModeEnabled
                viewModelScope.launch { profile.saveShuffle(shuffleModeEnabled) }
            }
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) controller?.duration?.takeIf { it > 0 }?.let { duration.value = it }
        }
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (ffJob?.isActive != true) position.value = newPosition.positionMs
        }
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            controller = future.get().also {
                it.addListener(listener)
                isPlaying.value = it.isPlaying
                viewModelScope.launch {
                    val saved = profile.shuffle.first()
                    _shuffle.value = saved
                    it.shuffleModeEnabled = saved
                }
                setCurrent(it.currentMediaItem?.mediaId?.toLongOrNull(), count = false)
                tryRestoreLast()
            }
        }, ContextCompat.getMainExecutor(ctx))

        // Sondea posición/duración solo mientras suena: en pausa no despierta la CPU.
        viewModelScope.launch {
            isPlaying.collectLatest { playing ->
                if (!playing) return@collectLatest
                var last = SystemClock.elapsedRealtime()
                var sinceSave = 0L
                while (true) {
                    delay(250)
                    val c = controller ?: continue
                    val now = SystemClock.elapsedRealtime()
                    val dt = now - last
                    last = now
                    if (ffJob?.isActive != true) position.value = c.currentPosition
                    c.duration.takeIf { it > 0 }?.let { if (duration.value != it) duration.value = it }
                    pendingMs += dt
                    if (pendingMs >= 10_000) flushTime()
                    sinceSave += dt
                    if (sinceSave >= 5_000) {
                        sinceSave = 0
                        current.value?.let { s -> profile.saveLastSong(s.id, c.currentPosition) }
                    }
                }
            }
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            val l = withContext(Dispatchers.IO) { MediaRepo.loadSongs(ctx) }
            byId = l.associateBy { it.id }
            allSongs.value = l
            controller?.currentMediaItem?.mediaId?.toLongOrNull()?.let { setCurrent(it, count = false) }
            tryRestoreLast()
        }
    }

    /** Si el usuario cerró la app con una canción cargada, la restaura al estado en que la dejó. */
    private fun tryRestoreLast() {
        if (restored) return
        if (byId.isEmpty()) return
        if (controller == null) return
        restored = true
        viewModelScope.launch {
            val id = profile.lastSongId.first()
            val pos = profile.lastPosition.first()
            if (id <= 0) return@launch
            val c = controller ?: return@launch
            if (c.currentMediaItem != null) return@launch    // ya hay algo cargado
            val s = byId[id] ?: return@launch
            // Carga sin reproducir, en la posición guardada
            val start = pos.coerceAtLeast(0L)
            c.setMediaItem(mediaItemOf(s), start)
            c.prepare()
            current.value = s
            position.value = start
            duration.value = s.durationMs
        }
    }

    fun play(list: List<Song>, index: Int, shuffleOn: Boolean? = null) {
        val c = controller ?: return
        if (list.isEmpty()) return
        shuffleOn?.let { v ->
            _shuffle.value = v
            viewModelScope.launch { profile.saveShuffle(v) }
        }
        c.shuffleModeEnabled = shuffleOn ?: _shuffle.value
        val start = index.coerceIn(0, list.lastIndex)
        // Armar miles de MediaItem en el hilo principal congelaba la UI: se hace en segundo plano.
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val items = withContext(Dispatchers.Default) { list.map { mediaItemOf(it) } }
            c.setMediaItems(items, start, 0)
            c.prepare(); c.play()
        }
    }
    fun playShuffled(list: List<Song>) { if (list.isNotEmpty()) play(list, Random.nextInt(list.size), true) }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { stopFastForward(); controller?.seekToNextMediaItem() }
    fun prev() { stopFastForward(); controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms); position.value = ms }

    /**
     * Mantener presionado "siguiente": avanza dentro de la canción y va acelerando.
     * Se detiene a 1 s del final para que la canción termine sola y pase a la siguiente.
     */
    fun startFastForward() {
        val c = controller ?: return
        ffJob?.cancel()
        ffJob = viewModelScope.launch {
            var p = c.currentPosition
            var step = 1_500L
            while (true) {
                val dur = c.duration.takeIf { it > 0 } ?: duration.value
                val limit = if (dur > 0) dur - 1_000 else Long.MAX_VALUE
                val target = (p + step).coerceAtMost(limit)
                if (target <= p) break
                p = target
                c.seekTo(p)
                position.value = p
                if (p >= limit) break
                step = (step + 250).coerceAtMost(6_000L)
                delay(150)
            }
        }
    }
    fun stopFastForward() { ffJob?.cancel(); ffJob = null }
    fun setVolume(v: Float) { controller?.volume = v.coerceIn(0f, 1f) }
    val volume: StateFlow<Float> = object : StateFlow<Float> {
        override val value: Float get() = controller?.volume ?: 1f
        override val replayCache: List<Float> get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<Float>): Nothing = throw UnsupportedOperationException()
    }.let { MutableStateFlow(1f) }

    fun toggleShuffle() {
        val v = !_shuffle.value
        _shuffle.value = v                       // la UI cambia de color al instante
        controller?.shuffleModeEnabled = v
        viewModelScope.launch { profile.saveShuffle(v) }
    }
    fun toggleFavorite(id: Long) { viewModelScope.launch { profile.toggleFavorite(id) } }
    fun setStyle(s: String) { viewModelScope.launch { profile.saveStyle(s) } }
    fun setVinyl(i: Int) { viewModelScope.launch { profile.saveVinyl(i) } }
    fun setCoverShape(s: String) { viewModelScope.launch { profile.saveCoverShape(s) } }
    fun setColorMode(s: String) { viewModelScope.launch { profile.saveColorMode(s) } }
    fun saveAlias(n: String) { viewModelScope.launch { profile.saveName(n) } }
    fun savePhoto(uri: android.net.Uri) { viewModelScope.launch { profile.savePhoto(uri) } }
    fun removePhoto() { viewModelScope.launch { profile.removePhoto() } }
    fun saveLastRoute(type: String, value: String) {
        viewModelScope.launch { profile.saveLastRoute(type, value) }
    }

    fun rescan() {
        viewModelScope.launch {
            val before = allSongs.value.size
            scanStatus.value = "Buscando música nueva…"
            val paths = withContext(Dispatchers.IO) {
                runCatching {
                    val exts = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma")
                    Environment.getExternalStorageDirectory().walkTopDown().maxDepth(8)
                        .onEnter { !it.name.startsWith(".") && it.name != "Android" }
                        .filter { it.isFile && it.extension.lowercase() in exts }.map { it.path }.take(30000).toList()
                }.getOrDefault(emptyList())
            }
            if (paths.isNotEmpty()) withTimeoutOrNull(20000) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val left = AtomicInteger(paths.size)
                    MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null) { _, _ ->
                        if (left.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
                    }
                }
            }
            val l = withContext(Dispatchers.IO) { MediaRepo.loadSongs(ctx) }
            byId = l.associateBy { it.id }
            allSongs.value = l
            val diff = l.size - before
            scanStatus.value = "Lista actualizada: ${l.size} canciones" + if (diff > 0) " (+$diff nuevas)" else ""
        }
    }

    fun toggleFolder(f: String) { viewModelScope.launch { profile.toggleFolder(f) } }
    fun createPlaylist(name: String, addSongId: Long? = null) {
        viewModelScope.launch {
            val id = dao.createPlaylist(Playlist(name = name.trim()))
            addSongId?.let { dao.addToPlaylist(PlaylistSong(id, it)) }
        }
    }
    fun deletePlaylist(id: Long) {
        viewModelScope.launch { dao.clearPlaylist(id); dao.deletePlaylist(id) }
    }
    fun addToPlaylist(pid: Long, sid: Long) { viewModelScope.launch { dao.addToPlaylist(PlaylistSong(pid, sid)) } }
    fun removeFromPlaylist(pid: Long, sid: Long) { viewModelScope.launch { dao.removeFromPlaylist(pid, sid) } }

    fun moveSongInPlaylist(pid: Long, sid: Long, up: Boolean) {
        viewModelScope.launch {
            val list = dao.entriesFor(pid).toMutableList()
            val i = list.indexOfFirst { it.songId == sid }
            if (i < 0) return@launch
            val j = if (up) i - 1 else i + 1
            if (j < 0 || j >= list.size) return@launch
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
            dao.clearOne(pid)
            val base = System.currentTimeMillis()
            dao.insertAll(list.mapIndexed { idx, ps -> ps.copy(addedAt = base + idx) })
        }
    }
    fun moveSongToPlaylist(fromPid: Long?, toPid: Long, sid: Long) {
        viewModelScope.launch {
            fromPid?.let { dao.removeFromPlaylist(it, sid) }
            dao.addToPlaylist(PlaylistSong(toPid, sid))
        }
    }
    fun moveSongToNewPlaylist(fromPid: Long?, name: String, sid: Long) {
        viewModelScope.launch {
            fromPid?.let { dao.removeFromPlaylist(it, sid) }
            val id = dao.createPlaylist(Playlist(name = name.trim()))
            dao.addToPlaylist(PlaylistSong(id, sid))
        }
    }
    fun deleteSong(sid: Long) {
        // Usa solo métodos que ya existen en el DAO: la quita de cada playlist
        viewModelScope.launch { dao.playlists().first().forEach { dao.removeFromPlaylist(it.id, sid) } }
    }

    private fun flushTime() {
        val s = current.value; val ms = pendingMs; pendingMs = 0
        if (s != null && ms > 0) viewModelScope.launch { dao.addTime(s.id, ms) }
    }

    private fun setCurrent(id: Long?, count: Boolean) {
        val s = id?.let { byId[it] }
        current.value = s
        if (s == null) return
        duration.value = s.durationMs
        position.value = controller?.currentPosition ?: 0L
        quality.value = ""
        if (count) viewModelScope.launch {
            dao.ensure(Stat(s.id, s.title, s.artist, s.genre))
            dao.addPlay(s.id)
            dao.logPlay(PlayEvent(songId = s.id))
        }
        // Si se saltan varias canciones seguidas solo se procesa la última (carátula y paleta son caras).
        artJob?.cancel()
        artJob = viewModelScope.launch {
            delay(120)
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.loadThumbnail(s.uri, Size(768, 768), null)
                        .let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
                }.getOrNull()
            }
            art.value = bmp
            accent.value = bmp?.let {
                withContext(Dispatchers.Default) {
                    vividAccent(Palette.from(it).maximumColorCount(24).resizeBitmapArea(100 * 100).generate())
                }
            } ?: DefaultAccent
            quality.value = qualityFor(s)
            ctx.getSharedPreferences("widget", 0).edit()
                .putString("album", s.album).putInt("year", s.year).apply()
            profile.saveLastSong(s.id, controller?.currentPosition ?: 0L)
        }
    }

    suspend fun qualityFor(s: Song): String = withContext(Dispatchers.IO) {
        runCatching {
            val ex = MediaExtractor()
            ex.setDataSource(ctx, s.uri, null)
            val f = ex.getTrackFormat(0)
            ex.release()
            val mime = f.getString(MediaFormat.KEY_MIME) ?: "audio/unknown"
            val sr = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
            val kbps = if (f.containsKey(MediaFormat.KEY_BIT_RATE)) f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
            else if (s.durationMs > 0) (s.size * 8 / s.durationMs).toInt() else 0
            val lossless = listOf("flac", "alac", "wav", "raw", "pcm").any { mime.contains(it) }
            val codec = when {
                mime.contains("mpeg") -> "MP3"
                mime.contains("mp4a") || mime.contains("aac") -> "AAC"
                else -> mime.substringAfter("/").removePrefix("x-").uppercase()
            }
            val khz = String.format(Locale.US, "%.1f kHz", sr / 1000f)
            val tag = when {
                lossless && sr >= 96000 -> "Hi-Res Lossless"
                lossless -> "Lossless"
                else -> "$kbps kbps"
            }
            "$codec · $tag · $khz"
        }.getOrDefault("")
    }

    suspend fun lookupOnline(s: Song, force: Boolean = false): AlbumMeta? = withContext(Dispatchers.IO) {
        val albumKnown = s.album != "Álbum desconocido"
        val key = if (albumKnown) "${s.artist.lowercase()}|${s.album.lowercase()}" else "${s.artist.lowercase()}|t:${s.title.lowercase()}"
        if (!force) dao.meta(key)?.let { return@withContext it }
        runCatching {
            val query = if (albumKnown) "releasegroup:\"${s.album}\" AND artist:\"${s.artist}\""
            else "recording:\"${s.title}\" AND artist:\"${s.artist}\""
            val path = if (albumKnown) "release-group" else "recording"
            val url = URL("https://musicbrainz.org/ws/2/$path/?query=${URLEncoder.encode(query, "UTF-8")}&fmt=json&limit=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "PersonalMusicPlayer/1.0 (uso personal)")
                connectTimeout = 8000; readTimeout = 8000
            }
            val arr = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                .getJSONArray(if (albumKnown) "release-groups" else "recordings")
            if (arr.length() == 0) null else {
                val o = arr.getJSONObject(0)
                val m = AlbumMeta(
                    key, o.optString("title"),
                    o.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name") ?: s.artist,
                    o.optString("first-release-date")
                )
                dao.saveMeta(m); m
            }
        }.getOrNull()
    }

    override fun onCleared() {
        // viewModelScope ya está cancelado aquí: el estado final se guarda con un scope propio.
        val s = current.value
        val pos = controller?.currentPosition ?: 0L
        val ms = pendingMs; pendingMs = 0
        if (s != null) appScope.launch {
            profile.saveLastSong(s.id, pos)
            if (ms > 0) dao.addTime(s.id, ms)
        }
        ffJob?.cancel()
        controller?.release()
        super.onCleared()
    }
}