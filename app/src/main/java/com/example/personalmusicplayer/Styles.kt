@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.personalmusicplayer

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

// ============================================================ XMB (estilo PS3, negro)
private class XItem(val label: String, val icon: ImageVector, val key: String = label, val onClick: () -> Unit)
private class XCat(val label: String, val icon: ImageVector, val items: List<XItem>)

@Composable
fun XmbHome(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit) {
    val pls by vm.playlists.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val cats = remember(pls, nav) {
        listOf(
            XCat("Música", Icons.Default.MusicNote, listOf(
                XItem("Canciones", Icons.Default.MusicNote) { nav.push(Route("songs")) },
                XItem("Favoritos", Icons.Default.Favorite) { nav.push(Route("favorites")) },
                XItem("Álbumes", Icons.Default.Album) { nav.push(Route("albums")) },
                XItem("Artistas", Icons.Default.Mic) { nav.push(Route("artists")) },
                XItem("Géneros", Icons.Default.Category) { nav.push(Route("genres")) },
                XItem("Carpetas", Icons.Default.Folder) { nav.push(Route("folders")) }
            )),
            XCat("Playlists", Icons.Default.QueueMusic,
                if (pls.isEmpty()) listOf(XItem("Aún no tienes playlists", Icons.Default.Info) {})
                else pls.map { p -> XItem(p.name, Icons.Default.QueueMusic, key = "pl${p.id}") { nav.push(Route("playlist", p.id.toString())) } }),
            XCat("Buscar", Icons.Default.Search, listOf(XItem("Buscar en tu biblioteca", Icons.Default.Search) { nav.push(Route("search")) })),
            XCat("Estadísticas", Icons.Default.BarChart, listOf(XItem("Ver tus estadísticas", Icons.Default.BarChart) { nav.push(Route("stats")) })),
            XCat("Ajustes", Icons.Default.Settings, listOf(
                XItem("Configuración", Icons.Default.Settings) { nav.push(Route("settings")) },
                XItem("Perfil", Icons.Default.Person) { nav.push(Route("profile")) }
            ))
        )
    }
    val pager = rememberPagerState(pageCount = { cats.size })
    val screenW = LocalConfiguration.current.screenWidthDp
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "p"
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            for (k in 0..2) {
                val base = size.height * (0.38f + 0.13f * k)
                val path = Path().apply {
                    moveTo(0f, size.height); lineTo(0f, base)
                    var x = 0f
                    while (x <= size.width) {
                        lineTo(x, base + sin(x / size.width * 2f * PI.toFloat() * (1f + k * 0.3f) + phase + k) * size.height * 0.035f)
                        x += 14f
                    }
                    lineTo(size.width, size.height); close()
                }
                drawPath(path, Color.White.copy(alpha = .035f + .02f * k))
            }
        }
        Column(Modifier.fillMaxSize()) {
            Header(vm, nav, "Personal Music Player", null, onMenu)
            Spacer(Modifier.height(18.dp))
            HorizontalPager(pager, Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = ((screenW - 96) / 2).dp),
                pageSize = PageSize.Fixed(96.dp)) { page ->
                val c = cats[page]
                Column(Modifier.fillMaxWidth().graphicsLayer {
                    val f = min(abs((pager.currentPage - page) + pager.currentPageOffsetFraction), 1f)
                    val s = 1.2f - .5f * f; scaleX = s; scaleY = s; alpha = 1f - .55f * f
                }.clickable { scope.launch { pager.animateScrollToPage(page) } },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color.White.copy(.3f), Color.White.copy(.05f)))),
                        contentAlignment = Alignment.Center) {
                        Icon(c.icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(c.label, color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(26.dp))
            Crossfade(pager.currentPage, label = "xmbItems") { idx ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 28.dp)) {
                    items(cats[idx.coerceIn(0, cats.lastIndex)].items, key = { it.key }) { it ->
                        Row(Modifier.fillMaxWidth().clickable { it.onClick() }.padding(vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(.1f)),
                                contentAlignment = Alignment.Center) {
                                Icon(it.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(it.label, color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ iTunes 2007 (Cover Flow cuadrado)
@Composable
private fun CoverWithReflection(a: AlbumItem, size: Dp, onClick: () -> Unit) {
    val b by rememberThumb(a.first, 500)
    Column(
        Modifier
            .width(size)
            .clickable { onClick() }
    ) {
        // ⭐ Cuadrado perfecto
        Box(
            Modifier
                .size(size)
                .background(Color(0xFF222222))
                .border(1.dp, Color.White.copy(.15f))
        ) {
            b?.let {
                Image(
                    it.asImageBitmap(), null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        // Reflejo
        Box(
            Modifier
                .size(size, size * 0.4f)
                .clipToBounds()
        ) {
            b?.let {
                Image(
                    it.asImageBitmap(), null,
                    Modifier
                        .graphicsLayer { scaleY = -1f; alpha = .4f }
                        .size(size),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            )
        }
    }
}

@Composable
fun ITunesHome(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit) {
    val lib by vm.library.collectAsState()
    val current by vm.current.collectAsState()
    val photo by vm.profile.photo.collectAsState(initial = null)
    val albums = remember(lib) { albumsOf(lib) }
    val byAlbum = remember(lib) { lib.groupBy { it.albumId } }
    val pager = rememberPagerState(pageCount = { albums.size })
    val scope = rememberCoroutineScope()
    val screenW = LocalConfiguration.current.screenWidthDp
    val listState = nav.listState("itunes:${pager.currentPage}")

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(ITunesMetalBrush).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onMenu) { Icon(Icons.Default.Menu, "Menú", tint = Color.White) }
            Box(Modifier.clip(CircleShape).clickable { nav.push(Route("profile")) }) { Avatar(photo, 36) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp).clip(RoundedCornerShape(6.dp))
                .background(ITunesLCDBrush).padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current?.title ?: "Personal Music Player", color = Color(0xFF111111), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
                Text(current?.let { "${it.artist} — ${it.album}" } ?: "Biblioteca", color = Color(0xFF333333),
                    fontSize = 10.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
                MiniProgressBar(vm, current?.durationMs, Modifier.fillMaxWidth().padding(top = 3.dp).height(2.dp))
            }
            IconButton({ nav.push(Route("search")) }) { Icon(Icons.Default.Search, "Buscar", tint = Color.White) }
        }

        if (albums.isEmpty()) {
            Text("Aún no hay música. Revisa permisos o usa Configuración > Actualizar lista.",
                color = Color.White.copy(.7f), modifier = Modifier.padding(20.dp))
            return@Column
        }

        // ⭐ Cover Flow cuadrado, 150x150, página de 150dp para que quepa entero
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(top = 14.dp),
            contentPadding = PaddingValues(horizontal = ((screenW - 150) / 2).dp),
            pageSize = PageSize.Fixed(150.dp),
            key = { albums[it].id }
        ) { page ->
            val a = albums[page]
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(-abs(page - pager.currentPage).toFloat())
                    .graphicsLayer {
                        val f = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                        rotationY = -f * 55f
                        val s = 1f - .22f * abs(f)
                        scaleX = s
                        scaleY = s
                        cameraDistance = 14f * density
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                CoverWithReflection(a, 150.dp) {
                    if (pager.currentPage == page) {
                        nav.push(Route("album", a.id.toString()))
                    } else {
                        scope.launch { pager.animateScrollToPage(page) }
                    }
                }
            }
        }

        val sel = albums.getOrNull(pager.currentPage)
        if (sel != null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(sel.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).basicMarquee(iterations = Int.MAX_VALUE))
                Text(sel.artist, color = Color.White.copy(.6f), fontSize = 13.sp, maxLines = 1)
            }
            val tracks = remember(sel.id, lib) {
                byAlbum[sel.id].orEmpty().sortedWith(compareBy({ it.track }, { norm(it.title) }))
            }
            Row(Modifier.fillMaxWidth().background(ITunesColumnBrush).padding(horizontal = 16.dp, vertical = 5.dp)) {
                Text("#", color = Color.White.copy(.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                Text("Nombre", color = Color.White.copy(.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Tiempo", color = Color.White.copy(.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.weight(1f), state = listState) {
                itemsIndexed(tracks, key = { _, s -> s.id }) { i, s ->
                    Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) ITunesRowOdd else ITunesRowEven)
                        .clickable { vm.play(tracks, i) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = Color.White.copy(.45f), fontSize = 13.sp, modifier = Modifier.width(30.dp))
                        Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(fmt(s.durationMs), color = Color.White.copy(.5f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniProgressBar(vm: PlayerViewModel, fallbackDurationMs: Long?, modifier: Modifier) {
    val pos by vm.position.collectAsState()
    val dur by vm.duration.collectAsState()
    LinearProgressIndicator(
        progress = {
            if (fallbackDurationMs == null) 0f else {
                val d = if (dur > 0) dur else fallbackDurationMs
                if (d <= 0L) 0f else (pos.toFloat() / d).coerceIn(0f, 1f)
            }
        },
        modifier = modifier, color = ITunesBlue, trackColor = Color(0x33000000)
    )
}

private val VistaLcdBrush = Brush.verticalGradient(listOf(Color(0xCC0A1E33), Color(0xCC071523)))

private data class VistaCard(val label: String, val icon: ImageVector, val count: String, val route: Route)

// ============================================================ Windows Vista / WMP
@Composable
fun VistaHome(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit) {
    val lib by vm.library.collectAsState()
    val current by vm.current.collectAsState()
    val photo by vm.profile.photo.collectAsState(initial = null)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(VistaGlassBrush)
                .border(1.dp, Color.White.copy(.15f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onMenu) { Icon(Icons.Default.Menu, "Menú", tint = Color.White) }
            Box(Modifier.clip(CircleShape).clickable { nav.push(Route("profile")) }) {
                Avatar(photo, 36)
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(VistaLcdBrush)
                    .border(1.dp, Color.White.copy(.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(current?.title ?: "Personal Music Player", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
                Text(current?.let { "${it.artist} — ${it.album}" } ?: "Biblioteca",
                    color = Color.White.copy(.7f), fontSize = 10.sp, maxLines = 1, softWrap = false,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
                MiniProgressBarVista(vm, current?.durationMs, Modifier.fillMaxWidth().padding(top = 3.dp).height(2.dp))
            }
            IconButton({ nav.push(Route("search")) }) {
                Icon(Icons.Default.Search, "Buscar", tint = Color.White)
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(6.dp)).background(VistaGlassBrush)
                .border(1.dp, Color.White.copy(.2f), RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("Biblioteca multimedia", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        val nSongs = lib.size
        val nAlbums = remember(lib) { lib.map { it.albumId }.distinct().size }
        val nArtists = remember(lib) { lib.map { it.artist }.distinct().size }
        val nGenres = remember(lib) { lib.map { it.genre }.distinct().size }
        val nFolders = remember(lib) { lib.map { it.folder }.distinct().size }

        val cards = remember(nSongs, nAlbums, nArtists, nGenres, nFolders) {
            listOf(
                VistaCard("Canciones", Icons.Default.MusicNote, "$nSongs", Route("songs")),
                VistaCard("Álbumes", Icons.Default.Album, "$nAlbums", Route("albums")),
                VistaCard("Artistas", Icons.Default.Mic, "$nArtists", Route("artists")),
                VistaCard("Géneros", Icons.Default.Category, "$nGenres", Route("genres")),
                VistaCard("Carpetas", Icons.Default.Folder, "$nFolders", Route("folders")),
                VistaCard("Favoritos", Icons.Default.Favorite, "", Route("favorites")),
                VistaCard("Buscar", Icons.Default.Search, "", Route("search"))
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(cards.size) { i ->
                val c = cards[i]
                Column(
                    Modifier.fillMaxWidth().height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VistaPanelBrush)
                        .border(1.dp, Color.White.copy(.25f), RoundedCornerShape(8.dp))
                        .clickable { nav.push(c.route) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(c.icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    Column {
                        Text(c.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (c.count.isNotBlank())
                            Text(c.count, color = Color.White.copy(.75f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniProgressBarVista(vm: PlayerViewModel, fallbackDurationMs: Long?, modifier: Modifier) {
    val pos by vm.position.collectAsState()
    val dur by vm.duration.collectAsState()
    LinearProgressIndicator(
        progress = {
            if (fallbackDurationMs == null) 0f else {
                val d = if (dur > 0) dur else fallbackDurationMs
                if (d <= 0L) 0f else (pos.toFloat() / d).coerceIn(0f, 1f)
            }
        },
        modifier = modifier, color = VistaAccent, trackColor = Color(0x33000000)
    )
}

// ============================================================ Configuración
private data class StyleOpt(val id: String, val title: String, val desc: String, val icon: ImageVector)

private val STYLE_OPTS = listOf(
    StyleOpt(STYLE_CLASSIC, "Clásico", "Minimalista redondeado", Icons.Default.LibraryMusic),
    StyleOpt(STYLE_XMB, "XMB (PS3 negra)", "Categorías en horizontal", Icons.Default.Apps),
    StyleOpt(STYLE_ITUNES, "iTunes 2007", "Cover Flow de álbumes", Icons.Default.ViewCarousel),
    StyleOpt(STYLE_VISTA, "Windows Vista (WMP)", "Panel Aero con rejilla", Icons.Default.GridView)
)

@Composable
fun SettingsScreen(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit) {
    val style = LocalStyle.current
    val status by vm.scanStatus.collectAsState()
    val all by vm.allSongs.collectAsState()
    val pick = { id: String -> vm.setStyle(id) }
    val scan = { vm.rescan() }

    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, "Configuración", null, onMenu, search = false)
            when (style) {
                STYLE_XMB -> XmbSettings(style, all.size, status, pick, scan)
                STYLE_VISTA -> VistaSettings(style, all.size, status, pick, scan)
                STYLE_ITUNES -> ITunesSettings(style, all.size, status, pick, scan)
                else -> ClassicSettings(style, all.size, status, pick, scan)
            }
        }
    }
}

@Composable
private fun ClassicSettings(current: String, count: Int, status: String, onPick: (String) -> Unit, onScan: () -> Unit) {
    val cardShape = RoundedCornerShape(20.dp)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(cardShape).background(Color(0xFF141418))
                .border(1.dp, Color.White.copy(.07f), cardShape).padding(vertical = 6.dp)) {
                Text("Estilo de la app", color = Color.White.copy(.6f), fontSize = 12.sp,
                    modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 6.dp))
                STYLE_OPTS.forEach { o ->
                    val sel = current == o.id
                    Row(Modifier.fillMaxWidth().clickable { onPick(o.id) }.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(if (sel) .22f else .08f)),
                            contentAlignment = Alignment.Center) {
                            Icon(o.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(o.desc, color = Color.White.copy(.55f), fontSize = 12.sp)
                        }
                        if (sel) Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(cardShape).background(Color(0xFF141418))
                .border(1.dp, Color.White.copy(.07f), cardShape).padding(18.dp)) {
                Text("Biblioteca", color = Color.White.copy(.6f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text("$count canciones en la lista", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                Button(onScan, shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Actualizar lista de canciones")
                }
                if (status.isNotBlank()) Text(status, color = Color.White.copy(.6f), fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp))
                Text("Úsalo después de agregar música nueva al teléfono.",
                    color = Color.White.copy(.4f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun XmbSettings(current: String, count: Int, status: String, onPick: (String) -> Unit, onScan: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item {
            Text("Estilo de la app", color = Color.White.copy(.6f), fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        }
        items(STYLE_OPTS.size) { i ->
            val o = STYLE_OPTS[i]
            XmbSettingRow(o.icon, o.title, o.desc, if (current == o.id) "✓ activo" else null) { onPick(o.id) }
        }
        item {
            Text("Biblioteca", color = Color.White.copy(.6f), fontSize = 13.sp,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 8.dp))
        }
        item {
            XmbSettingRow(Icons.Default.Refresh, "Actualizar lista de canciones",
                status.ifBlank { "$count canciones en la lista" }) { onScan() }
        }
    }
}

@Composable
private fun XmbSettingRow(icon: ImageVector, title: String, subtitle: String, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f))
            .clickable { onClick() }.padding(horizontal = 28.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color.White.copy(.28f), Color.White.copy(.05f)))),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Text(it, color = Color.White.copy(.45f), fontSize = 11.sp) }
    }
}

@Composable
private fun ITunesSettings(current: String, count: Int, status: String, onPick: (String) -> Unit, onScan: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF141414))
                .border(1.dp, Color.White.copy(.1f), RoundedCornerShape(6.dp))) {
                Row(Modifier.fillMaxWidth().background(ITunesColumnBrush).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Estilo de la biblioteca", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                STYLE_OPTS.forEachIndexed { i, o ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = Color.White.copy(.08f))
                    val sel = current == o.id
                    Row(Modifier.fillMaxWidth().background(if (sel) ITunesBlue.copy(.28f) else Color.Transparent)
                        .clickable { onPick(o.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (sel) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null,
                            tint = if (sel) ITunesBlue else Color.White.copy(.6f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Icon(o.icon, null, tint = Color.White.copy(.9f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(o.desc, color = Color.White.copy(.6f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF141414))
                .border(1.dp, Color.White.copy(.1f), RoundedCornerShape(6.dp))) {
                Row(Modifier.fillMaxWidth().background(ITunesColumnBrush).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Biblioteca", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column(Modifier.padding(14.dp)) {
                    Text("$count canciones en la lista", color = Color.White.copy(.8f), fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onScan, shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ITunesBlue)) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Actualizar lista de canciones")
                    }
                    if (status.isNotBlank()) Text(status, color = Color.White.copy(.6f), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    Text("Úsalo después de agregar música nueva al teléfono.",
                        color = Color.White.copy(.4f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun VistaSettings(current: String, count: Int, status: String, onPick: (String) -> Unit, onScan: () -> Unit) {
    val panel = RoundedCornerShape(8.dp)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(panel).background(VistaGlassBrush)
                .border(1.dp, Color.White.copy(.25f), panel)) {
                Text("ESTILO DE LA APP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                STYLE_OPTS.forEachIndexed { i, o ->
                    val sel = current == o.id
                    Row(Modifier.fillMaxWidth()
                        .background(if (sel) VistaAccent.copy(.35f) else if (i % 2 == 0) VistaRowOdd else VistaRowEven)
                        .clickable { onPick(o.id) }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(o.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(o.desc, color = Color.White.copy(.7f), fontSize = 12.sp)
                        }
                        if (sel) Icon(Icons.Default.CheckCircle, null, tint = VistaAccent)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(panel).background(VistaGlassBrush)
                .border(1.dp, Color.White.copy(.25f), panel).padding(14.dp)) {
                Text("BIBLIOTECA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text("$count canciones en la lista", color = Color.White.copy(.85f), fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Button(onScan, shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VistaAccent)) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Actualizar lista de canciones")
                }
                if (status.isNotBlank()) Text(status, color = Color.White.copy(.75f), fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp))
                Text("Úsalo después de agregar música nueva al teléfono.",
                    color = Color.White.copy(.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}