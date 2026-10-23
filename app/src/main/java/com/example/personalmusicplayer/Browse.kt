@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.personalmusicplayer

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import android.util.Size
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AlbumItem(val id: Long, val name: String, val artist: String, val count: Int, val first: Song)
data class ArtistItem(val name: String, val songs: Int, val albums: Int, val first: Song)
data class NameCount(val name: String, val count: Int)

fun albumsOf(l: List<Song>): List<AlbumItem> = l.groupBy { it.albumId }.map { (id, ss) ->
    AlbumItem(id, ss.first().album, ss.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "", ss.size, ss.first())
}.sortedBy { norm(it.name) }

fun artistsOf(l: List<Song>): List<ArtistItem> = l.groupBy { it.artist }.map { (n, ss) ->
    ArtistItem(n, ss.size, ss.map { it.albumId }.distinct().size, ss.first())
}.sortedBy { norm(it.name) }

fun fmtLong(ms: Long): String { val m = ms / 60000; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }

// ------------------------------------------------------------ Host
@Composable
fun BrowseHost(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit) {
    val lib by vm.library.collectAsState()
    val all by vm.allSongs.collectAsState()
    val ent by vm.entries.collectAsState(emptyList())
    val pls by vm.playlists.collectAsState(emptyList())
    val r = nav.current

    when (r.type) {
        "songs" -> SongsScreen(vm, nav, onMenu, "Canciones", remember(lib) { lib.sortedBy { norm(it.title) } })
        "albums" -> AlbumsScreen(vm, nav, onMenu, lib)
        "artists" -> ArtistsScreen(vm, nav, onMenu, lib)
        "genres" -> GenresScreen(vm, nav, onMenu, lib)
        "folders" -> FoldersScreen(vm, nav, onMenu, all)
        "xmb" -> XmbHome(vm, nav, onMenu)
        "itunes" -> ITunesHome(vm, nav, onMenu)
        "vista" -> VistaHome(vm, nav, onMenu)
        "settings" -> SettingsScreen(vm, nav, onMenu)
        "favorites" -> {
            val favs by vm.favorites.collectAsState()
            SongsScreen(vm, nav, onMenu, "Favoritos", lib.filter { it.id in favs }.sortedBy { norm(it.title) })
        }
        "profile" -> {
            StyleScreenBackground(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    StyleHeader(vm, nav, "Mi cuenta", null, onMenu, search = false)
                    AccountScreen(vm)
                }
            }
        }
        "history" -> HistoryScreen(vm, nav, onMenu)
        "search" -> SearchScreen(vm, nav, onMenu, lib)
        "album" -> AlbumDetail(vm, nav, onMenu, lib.filter { it.albumId.toString() == r.value })
        "artist" -> ArtistDetail(vm, nav, onMenu, r.value, lib.filter { it.artist == r.value })
        "genre" -> SongsScreen(vm, nav, onMenu, r.value, lib.filter { it.genre == r.value }.sortedBy { norm(it.title) })
        "folder" -> SongsScreen(vm, nav, onMenu, r.value, all.filter { it.folder == r.value }.sortedBy { norm(it.title) })
        "playlist" -> {
            val p = pls.firstOrNull { it.id.toString() == r.value }
            val m = all.associateBy { it.id }
            val songs = ent.filter { it.playlistId.toString() == r.value }.mapNotNull { m[it.songId] }
            SongsScreen(vm, nav, onMenu, p?.name ?: "Playlist", songs, alpha = false, playlistId = p?.id) {
                if (p != null) TextButton({ vm.deletePlaylist(p.id); nav.back() }) { Text("Eliminar", color = ITunesBlue) }
            }
        }
    }
}

// ------------------------------------------------------------ Header (base para iTunes/Vista/XMB)
@Composable
fun Header(vm: PlayerViewModel, nav: Nav, title: String, subtitle: String?, onMenu: () -> Unit, search: Boolean = true) {
    val photo by vm.profile.photo.collectAsState(initial = null)
    val style = LocalStyle.current
    val brush = when (style) {
        STYLE_VISTA -> VistaGlassBrush
        STYLE_XMB -> Brush.verticalGradient(listOf(Color(0xCC000000), Color(0xCC000000)))
        else -> ITunesMetalBrush
    }
    val inner = when (style) {
        STYLE_ITUNES -> ITunesLCDBrush
        STYLE_VISTA -> Brush.verticalGradient(listOf(Color(0xCC0A1E33), Color(0xCC071523)))
        else -> Brush.verticalGradient(listOf(Color(0x66000000), Color(0x66000000)))
    }
    val onLcd = style == STYLE_ITUNES
    Row(Modifier.fillMaxWidth().background(brush).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onMenu) { Icon(Icons.Default.Menu, "Menú", tint = Color.White) }
        if (nav.stack.size > 1) {
            IconButton({ nav.back() }) { Icon(Icons.Default.ArrowBack, "Atrás", tint = Color.White) }
        } else {
            Box(Modifier.padding(4.dp).clip(CircleShape).clickable { nav.push(Route("profile")) }) { Avatar(photo, 32) }
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).background(inner).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(title, color = if (onLcd) Color(0xFF111111) else Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, color = if (onLcd) Color(0xFF444444) else Color.White.copy(.7f),
                    fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (search && nav.current.type != "search") IconButton({ nav.push(Route("search")) }) {
            Icon(Icons.Default.Search, "Buscar", tint = Color.White)
        }
    }
}

/** Caché en memoria (24 MB): al hacer scroll las carátulas ya vistas no se vuelven a decodificar. */
private object ThumbCache {
    private val cache = object : android.util.LruCache<String, Bitmap>(maxOf(24 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bmp: Bitmap) { cache.put(key, bmp) }
}

@Composable
fun rememberThumb(song: Song?, px: Int): State<Bitmap?> {
    val ctx = LocalContext.current.applicationContext
    val key = song?.let { "${it.id}:$px" }
    return produceState<Bitmap?>(key?.let { ThumbCache.get(it) }, key) {
        if (song == null || key == null) { value = null; return@produceState }
        ThumbCache.get(key)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(song.uri, Size(px, px), null) }.getOrNull()
        }?.also { ThumbCache.put(key, it) }
    }
}

@Composable
fun Thumb(song: Song?, dp: Int, shape: Shape = RoundedCornerShape(3.dp), px: Int = 160) {
    val b by rememberThumb(song, px)
    val shape = coverShape(shape, dp.toFloat())
    Box(Modifier.size(dp.dp).clip(shape).background(Color(0xFF222222)).border(1.dp, Color.Black.copy(.6f), shape),
        contentAlignment = Alignment.Center) {
        val bm = b
        if (bm != null) Image(bm.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(.3f))
    }
}

@Composable
fun PlayButtons(vm: PlayerViewModel, songs: List<Song>, extra: @Composable () -> Unit = {}) {
    val style = LocalStyle.current
    val isClassic = style == STYLE_CLASSIC
    val accent = when (style) {
        STYLE_VISTA -> VistaAccent
        STYLE_CLASSIC -> Color.White
        else -> ITunesBlue
    }
    val onAccent = if (isClassic) Color.Black else Color.White
    val bg = when (style) {
        STYLE_VISTA -> VistaGlassBrush
        STYLE_XMB -> Brush.verticalGradient(listOf(Color(0xCC000000), Color(0xCC000000)))
        STYLE_CLASSIC -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))  // ⬅️ SIN fondo
        else -> Brush.verticalGradient(listOf(Color(0xFF1C1C1C), Color(0xFF0E0E0E)))
    }
    val shape = if (isClassic) RoundedCornerShape(50) else RoundedCornerShape(4.dp)

    Row(
        Modifier.fillMaxWidth().background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { vm.play(songs, 0) },
            enabled = songs.isNotEmpty(),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = onAccent
            )
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(4.dp))
            Text("Reproducir")
        }
        OutlinedButton(
            onClick = { vm.playShuffled(songs) },
            enabled = songs.isNotEmpty(),
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = if (isClassic) BorderStroke(1.dp, Color.White) else null
        ) {
            Icon(Icons.Default.Shuffle, null)
            Spacer(Modifier.width(4.dp))
            Text("Aleatorio")
        }
        extra()
    }
}

@Composable
fun SectionLabel(t: String) {
    val style = LocalStyle.current
    val bg = when (style) { STYLE_VISTA -> VistaGlassBrush; else -> ITunesColumnBrush }
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(t.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

private class Entry<T>(val letter: String?, val index: Int, val item: T?)

@Composable
fun <T> AlphaList(
    items: List<T>, nameOf: (T) -> String, keyOf: (T) -> Any, modifier: Modifier = Modifier,
    alpha: Boolean = true, listState: LazyListState? = null, content: @Composable (Int, T) -> Unit
) {
    val style = LocalStyle.current
    val flat = remember(items, alpha) {
        val out = ArrayList<Entry<T>>(); var last = ""
        items.forEachIndexed { i, it ->
            if (alpha) { val l = letterOf(nameOf(it)); if (l != last) { out.add(Entry(l, -1, null)); last = l } }
            out.add(Entry(null, i, it))
        }
        out
    }
    val letters = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    val available = remember(flat) { flat.mapNotNull { it.letter }.toSet() }
    val firstIndex = remember(flat) {
        val m = HashMap<String, Int>(); flat.forEachIndexed { i, e -> e.letter?.let { if (it !in m) m[it] = i } }; m
    }
    val state = listState ?: rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var bubble by remember { mutableStateOf<String?>(null) }
    var lastTarget by remember { mutableIntStateOf(-1) }

    fun jump(y: Float, h: Float) {
        if (h <= 0f) return
        val idx = ((y / h) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val l = (idx until letters.size).map { letters[it] }.firstOrNull { it in available }
            ?: (idx downTo 0).map { letters[it] }.firstOrNull { it in available } ?: return
        bubble = l
        val target = firstIndex[l] ?: return
        if (target != lastTarget) {
            lastTarget = target
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { state.scrollToItem(target) }
        }
    }

    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(end = if (alpha) 34.dp else 0.dp)) {
            itemsIndexed(flat, key = { _, e -> e.letter?.let { "#h$it" } ?: keyOf(e.item!!) }) { _, e ->
                if (e.letter != null) {
                    LetterHeader(style, e.letter!!)
                } else content(e.index, e.item!!)
            }
        }
        if (alpha && items.size > 15) {
            Column(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(34.dp).padding(vertical = 8.dp)
                    .pointerInput(flat) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            lastTarget = -1
                            jump(down.position.y, size.height.toFloat())
                            do {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull()
                                if (ch != null && ch.pressed) { ch.consume(); jump(ch.position.y, size.height.toFloat()) }
                            } while (ev.changes.any { it.pressed })
                            bubble = null
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                letters.forEach { l ->
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(l, fontSize = 10.sp, color = Color.White.copy(if (l in available) .9f else .25f))
                    }
                }
            }
            bubble?.let {
                Box(Modifier.align(Alignment.Center).size(72.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xEE000000)),
                    contentAlignment = Alignment.Center) {
                    Text(it, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ------------------------------------------------------------ Separador de letra por estilo
@Composable
private fun LetterHeader(style: String, letter: String) {
    when (style) {
        STYLE_ITUNES -> {
            Row(
                Modifier.fillMaxWidth()
                    .background(ITunesColumnBrush)
                    .padding(start = 14.dp, top = 6.dp, bottom = 4.dp)
            ) {
                Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        STYLE_XMB -> {
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, top = 18.dp, end = 28.dp, bottom = 6.dp)) {
                Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }
        STYLE_VISTA -> {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.03f)
                    )))
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        else -> {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp)) {
                Text(letter, color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ------------------------------------------------------------ SongRow original (iTunes)
@Composable
fun SongRow(vm: PlayerViewModel, s: Song, queue: List<Song>, index: Int, number: Int? = null,
            playlistId: Long? = null, zebra: Boolean = false) {
    var sheet by remember { mutableStateOf(false) }
    val style = LocalStyle.current
    val rowBg = when (style) {
        STYLE_VISTA -> if (zebra) VistaRowOdd else VistaRowEven
        else -> if (zebra) ITunesRowOdd else ITunesRowEven
    }

    Row(
        Modifier.fillMaxWidth()
            .background(rowBg)
            .combinedClickable(onClick = { vm.play(queue, index) }, onLongClick = { sheet = true })
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) Text("$number", color = Color.White.copy(.5f), modifier = Modifier.width(28.dp), fontSize = 13.sp)
        else { Thumb(s, 48); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (number != null) s.artist else "${s.artist} · ${s.album}",
                color = Color.White.copy(.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FavMark(vm, s.id)
        Text(fmt(s.durationMs), color = Color.White.copy(.45f), fontSize = 11.sp)
        IconButton({ sheet = true }, Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(.6f), modifier = Modifier.size(18.dp))
        }
    }

    SongActions(vm, s, playlistId, sheet) { sheet = false }
}

/** Hoja de acciones (pulsación larga / ⋮) compartida por TODOS los estilos. */
@Composable
fun SongActions(vm: PlayerViewModel, s: Song, playlistId: Long?, show: Boolean, onClose: () -> Unit) {
    var info by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }

    if (show) ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF1B1B1B)) {
        val favs by vm.favorites.collectAsState()
        val isFav = s.id in favs
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(s.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Text(s.artist, color = Color.White.copy(.6f), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp))
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(.1f))
            SheetItem(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (isFav) "Quitar de favoritos" else "Añadir a favoritos") { vm.toggleFavorite(s.id); onClose() }
            SheetItem(Icons.Default.PlaylistAdd, "Agregar a playlist") { onClose(); add = true }
            SheetItem(Icons.Default.DriveFileMove, "Mover a…") { onClose(); move = true }
            if (playlistId != null) {
                SheetItem(Icons.Default.ArrowUpward, "Mover arriba") { vm.moveSongInPlaylist(playlistId, s.id, true); onClose() }
                SheetItem(Icons.Default.ArrowDownward, "Mover abajo") { vm.moveSongInPlaylist(playlistId, s.id, false); onClose() }
            }
            SheetItem(Icons.Default.Delete, "Eliminar") { onClose(); del = true }
            SheetItem(Icons.Default.Info, "Información") { onClose(); info = true }
        }
    }
    if (info) InfoDialog(vm, s) { info = false }
    if (add) AddToPlaylistDialog(vm, s) { add = false }
    if (move) MoveToPlaylistDialog(vm, s, playlistId) { move = false }
    if (del) ConfirmDeleteDialog(vm, s, playlistId) { del = false }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(.85f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun AlbumRow(nav: Nav, a: AlbumItem, zebra: Boolean = false) {
    val style = LocalStyle.current
    val bg = when (style) { STYLE_VISTA -> if (zebra) VistaRowOdd else VistaRowEven; else -> if (zebra) ITunesRowOdd else ITunesRowEven }
    Row(Modifier.fillMaxWidth().background(bg)
        .clickable { nav.push(Route("album", a.id.toString())) }.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Thumb(a.first, 52); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, color = Color.White, fontSize = 14.sp, maxLines = 1, softWrap = false,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
            Text("${a.artist} · ${a.count} canciones", color = Color.White.copy(.55f), fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ArtistRow(nav: Nav, a: ArtistItem, zebra: Boolean = false) {
    val style = LocalStyle.current
    val bg = when (style) { STYLE_VISTA -> if (zebra) VistaRowOdd else VistaRowEven; else -> if (zebra) ITunesRowOdd else ITunesRowEven }
    Row(Modifier.fillMaxWidth().background(bg)
        .clickable { nav.push(Route("artist", a.name)) }.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Thumb(a.first, 52, CircleShape); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${a.albums} álbumes · ${a.songs} canciones", color = Color.White.copy(.55f), fontSize = 11.sp)
        }
    }
}

// ------------------------------------------------------------ Secciones
@Composable
fun SongsScreen(
    vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit, title: String, songs: List<Song>,
    alpha: Boolean = true, playlistId: Long? = null, extra: @Composable () -> Unit = {}
) {
    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, title, "${songs.size} canciones", onMenu)
            PlayButtons(vm, songs, extra)
            val ls = nav.listState("songs:${nav.current.type}:${nav.current.value}")
            if (songs.isEmpty()) {
                Text("No hay canciones aquí. Revisa el permiso de audio o las carpetas ocultas.",
                    color = Color.White.copy(.7f), modifier = Modifier.padding(16.dp))
            } else {
                AlphaList(songs, { it.title }, { it.id }, Modifier.fillMaxSize(), alpha, ls) { i, s ->
                    StyleSongRow(vm, s, songs, i, playlistId = playlistId, zebra = i % 2 == 0)
                }
            }
        }
    }
}

@Composable
fun AlbumsScreen(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit, lib: List<Song>) {
    val albums = remember(lib) { albumsOf(lib) }
    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, "Álbumes", "${albums.size} álbumes", onMenu)
            val ls = nav.listState("albums")
            AlphaList(albums, { it.name }, { it.id }, Modifier.fillMaxSize(), listState = ls) { i, a ->
                StyleAlbumRow(nav, a, i % 2 == 0)
            }
        }
    }
}

@Composable
fun ArtistsScreen(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit, lib: List<Song>) {
    val artists = remember(lib) { artistsOf(lib) }
    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, "Artistas", "${artists.size} artistas", onMenu)
            val ls = nav.listState("artists")
            AlphaList(artists, { it.name }, { "ar" + it.name }, Modifier.fillMaxSize(), listState = ls) { i, a ->
                StyleArtistRow(nav, a, i % 2 == 0)
            }
        }
    }
}

@Composable
fun GenresScreen(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit, lib: List<Song>) {
    val genres = remember(lib) { lib.groupingBy { it.genre }.eachCount().map { NameCount(it.key, it.value) }.sortedBy { norm(it.name) } }
    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, "Géneros", "${genres.size} géneros", onMenu)
            val ls = nav.listState("genres")
            val style = LocalStyle.current
            AlphaList(genres, { it.name }, { "g" + it.name }, Modifier.fillMaxSize(), listState = ls) { i, g ->
                val bg = when (style) {
                    STYLE_VISTA -> if (i % 2 == 0) VistaRowOdd else VistaRowEven
                    STYLE_XMB -> Color.Black.copy(alpha = 0.35f)
                    else -> if (i % 2 == 0) ITunesRowOdd else ITunesRowEven
                }
                Row(Modifier.fillMaxWidth().background(bg)
                    .clickable { nav.push(Route("genre", g.name)) }
                    .padding(horizontal = if (style == STYLE_XMB) 28.dp else 14.dp, vertical = 14.dp)) {
                    Text(g.name, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Text("${g.count}", color = Color.White.copy(.5f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun FoldersScreen(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit, all: List<Song>) {
    val excluded by vm.excluded.collectAsState()
    val folders = remember(all) { all.groupingBy { it.folder }.eachCount().map { NameCount(it.key, it.value) }.sortedBy { norm(it.name) } }
    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, "Carpetas", "Marca las que quieres ver", onMenu)
            val ls = nav.listState("folders")
            val style = LocalStyle.current
            LazyColumn(Modifier.fillMaxSize(), state = ls) {
                itemsIndexed(folders, key = { _, f -> "f" + f.name }) { i, f ->
                    val shown = f.name !in excluded
                    val bg = when (style) {
                        STYLE_VISTA -> if (i % 2 == 0) VistaRowOdd else VistaRowEven
                        STYLE_XMB -> Color.Black.copy(alpha = 0.35f)
                        else -> if (i % 2 == 0) ITunesRowOdd else ITunesRowEven
                    }
                    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = if (style == STYLE_XMB) 16.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shown, onCheckedChange = { vm.toggleFolder(f.name) })
                        Text(f.name, color = Color.White.copy(if (shown) .9f else .4f), fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable { nav.push(Route("folder", f.name)) }.padding(vertical = 12.dp))
                        Text("${f.count}", color = Color.White.copy(.5f), fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit, lib: List<Song>) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, "Buscar", null, onMenu, search = false)
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Canción, álbum o artista") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Default.Close, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).focusRequester(focus))

            val n = norm(q.trim())
            val artists = remember(lib, n) { if (n.isBlank()) emptyList() else artistsOf(lib).filter { norm(it.name).contains(n) }.take(20) }
            val albums = remember(lib, n) { if (n.isBlank()) emptyList() else albumsOf(lib).filter { norm(it.name).contains(n) || norm(it.artist).contains(n) }.take(30) }
            val songs = remember(lib, n) { if (n.isBlank()) emptyList() else lib.filter { norm(it.title).contains(n) }.sortedBy { norm(it.title) }.take(100) }

            if (n.isBlank()) { Text("Escribe para buscar en tu biblioteca.", color = Color.White.copy(.6f), modifier = Modifier.padding(16.dp)); return@Column }
            if (artists.isEmpty() && albums.isEmpty() && songs.isEmpty()) { Text("Sin resultados.", color = Color.White.copy(.6f), modifier = Modifier.padding(16.dp)); return@Column }
            LazyColumn(Modifier.fillMaxSize()) {
                if (artists.isNotEmpty()) {
                    item { SectionLabel("Artistas") }
                    itemsIndexed(artists, key = { _, it -> "ar" + it.name }) { i, a -> StyleArtistRow(nav, a, i % 2 == 0) }
                }
                if (albums.isNotEmpty()) {
                    item { SectionLabel("Álbumes") }
                    itemsIndexed(albums, key = { _, it -> "al${it.id}" }) { i, a -> StyleAlbumRow(nav, a, i % 2 == 0) }
                }
                if (songs.isNotEmpty()) {
                    item { SectionLabel("Canciones") }
                    itemsIndexed(songs, key = { _, s -> "s${s.id}" }) { i, s -> StyleSongRow(vm, s, songs, i, zebra = i % 2 == 0) }
                }
            }
        }
    }
}

@Composable
fun ArtistDetail(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit, name: String, raw: List<Song>) {
    val songs = remember(raw) { raw.sortedBy { norm(it.title) } }
    val albums = remember(raw) { albumsOf(raw) }
    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, name, "${albums.size} álbumes · ${songs.size} canciones", onMenu)
            LazyColumn(Modifier.fillMaxSize()) {
                item { PlayButtons(vm, songs) }
                if (albums.isNotEmpty()) {
                    item { SectionLabel("Álbumes") }
                    itemsIndexed(albums, key = { _, it -> "al${it.id}" }) { i, a -> StyleAlbumRow(nav, a, i % 2 == 0) }
                }
                item { SectionLabel("Canciones") }
                itemsIndexed(songs, key = { _, s -> "s${s.id}" }) { i, s -> StyleSongRow(vm, s, songs, i, zebra = i % 2 == 0) }
            }
        }
    }
}

@Composable
fun AlbumDetail(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit, raw: List<Song>) {
    val songs = remember(raw) { raw.sortedWith(compareBy({ it.track }, { norm(it.title) })) }
    val first = songs.firstOrNull()
    val style = LocalStyle.current
    if (first == null) {
        StyleScreenBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                StyleHeader(vm, nav, "Álbum", null, onMenu)
                Text("Álbum no encontrado.", color = Color.White.copy(.7f), modifier = Modifier.padding(16.dp))
            }
        }
        return
    }

    var meta by remember(first.albumId) { mutableStateOf<AlbumMeta?>(null) }
    LaunchedEffect(first.albumId) { meta = vm.lookupOnline(first) }
    val cover by rememberThumb(first, 640)
    val year = songs.maxOf { it.year }.takeIf { it > 0 }?.toString() ?: meta?.year?.take(4)?.takeIf { it.isNotBlank() }
    val artist = songs.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: first.artist
    val accent = when (style) { STYLE_VISTA -> VistaAccent; else -> ITunesBlue }

    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, first.album, null, onMenu)
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
                        val coverSh = coverShape(if (style == STYLE_CLASSIC) RoundedCornerShape(20.dp) else RoundedCornerShape(6.dp), 160f)
                        Box(Modifier.size(160.dp)
                            .clip(coverSh)
                            .background(Color(0xFF222222))
                            .border(1.dp, Color.Black.copy(.7f), coverSh)) {
                            cover?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ vm.play(songs, 0) }, shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Reproducir")
                            }
                            OutlinedButton({ vm.playShuffled(songs) }, shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                                Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(4.dp)); Text("Aleatorio")
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(first.album, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            softWrap = false, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
                        Text(artist, color = Color.White.copy(.85f), fontSize = 14.sp,
                            modifier = Modifier.clickable { nav.push(Route("artist", artist)) })
                        Text(buildString {
                            append("Álbum"); year?.let { append(" · $it") }
                            append(" · ${songs.size} canciones · ${fmtLong(songs.sumOf { it.durationMs })}")
                        }, color = Color.White.copy(.6f), fontSize = 12.sp)
                        meta?.takeIf { it.year.isNotBlank() }?.let {
                            Text("Publicado: ${it.year} · datos de MusicBrainz", color = Color.White.copy(.45f), fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item { SectionLabel("Pistas") }
                itemsIndexed(songs, key = { _, s -> "s${s.id}" }) { i, s ->
                    StyleSongRow(vm, s, songs, i,
                        number = if (s.track % 1000 > 0) s.track % 1000 else i + 1,
                        zebra = i % 2 == 0)
                }
            }
        }
    }
}