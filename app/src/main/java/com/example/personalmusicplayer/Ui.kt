@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.personalmusicplayer

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

// ============================================================ Paletas de estilo
const val STYLE_CLASSIC = "classic"
const val STYLE_XMB = "xmb"
const val STYLE_ITUNES = "itunes"
const val STYLE_VISTA = "vista"

val LocalStyle = compositionLocalOf { STYLE_CLASSIC }

// ⭐ NUEVO: acento global para que Clásico pueda teñir su fondo
val LocalAccent = compositionLocalOf { Color(0xFF3A3A3A) }

// Preferencias de apariencia (Configuración)
const val COVER_AUTO = "auto"       // como estaba: cada tema usa su propia forma
const val COVER_ROUND = "round"     // carátulas redondeadas en todos los temas
const val COVER_SQUARE = "square"   // carátulas cuadradas en todos los temas
const val COLOR_DYNAMIC = "dynamic" // el color cambia según la carátula
const val COLOR_BLACK = "black"     // negro total (tema Clásico/minimalista)

val LocalCoverShape = compositionLocalOf { COVER_AUTO }

/** Forma final de una carátula según la preferencia del usuario. [base] es la forma original del tema. */
@Composable
fun coverShape(base: Shape, sizeDp: Float): Shape = when (LocalCoverShape.current) {
    COVER_SQUARE -> RectangleShape
    COVER_ROUND -> if (base === CircleShape) CircleShape else RoundedCornerShape((sizeDp * 0.22f).dp)
    else -> base
}

/** Fondo de las tarjetas del tema Clásico: toma un toque del color del álbum (negro total = sin tinte). */
@Composable
fun classicCardColor(): Color {
    val a = LocalAccent.current
    val base = Color(0xFF141418)
    return if (a == Color.Black) base else lerp(base, a, 0.30f)
}

// iTunes 2007
val ITunesMetalBrush = Brush.verticalGradient(listOf(Color(0xFF5C5C5C), Color(0xFF343434), Color(0xFF1A1A1A)))
val ITunesColumnBrush = Brush.verticalGradient(listOf(Color(0xFF3E3E3E), Color(0xFF232323), Color(0xFF151515)))
val ITunesLCDBrush = Brush.verticalGradient(listOf(Color(0xFFEAEAEA), Color(0xFFC6C6C6), Color(0xFFAFAFAF)))
val ITunesBlue = Color(0xFF4A90D9)
val ITunesRowOdd = Color(0xFF161616)
val ITunesRowEven = Color(0xFF0C0C0C)

// Windows Vista / WMP
val VistaBgBrush = Brush.verticalGradient(
    listOf(Color(0xFF0C1A2E), Color(0xFF0F2540), Color(0xFF07101F))
)
val VistaGlassBrush = Brush.verticalGradient(
    listOf(Color(0xCC3B5C82), Color(0xAA1D3B5C), Color(0x99122440))
)
val VistaPanelBrush = Brush.verticalGradient(
    listOf(Color(0x99D8E6F5), Color(0x997A9CC0), Color(0x99526E8F))
)
val VistaAccent = Color(0xFF3DAEE9)
val VistaRowOdd = Color(0x1AFFFFFF)
val VistaRowEven = Color(0x0DFFFFFF)

// Clásico
val ClassicBgTop = Color(0xFF0A0A0A)
val ClassicBgBottom = Color(0xFF000000)
val ClassicCard = Color(0xFF151515)
val ClassicText = Color(0xFFFFFFFF)
val ClassicTextDim = Color(0xFF9A9A9A)
val ClassicAccent = Color(0xFFFFFFFF)

// ============================================================ Utilidades
fun fmt(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

private val ACCENTS = Regex("\\p{Mn}+")
fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(ACCENTS, "")
fun letterOf(name: String): String {
    val c = norm(name.trim()).firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

// ============================================================ Fondo de olas XMB
@Composable
fun WaveBackground(modifier: Modifier = Modifier, alpha: Float = 1f) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
    Canvas(modifier) {
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
            drawPath(path, Color.White.copy(alpha = (.035f + .02f * k) * alpha))
        }
    }
}

// ============================================================ Navegación
data class Route(val type: String, val value: String = "")

class Nav(private val goStats: () -> Unit) {
    val stack = mutableStateListOf(Route("songs"))
    val listStates = mutableMapOf<String, LazyListState>()
    val current: Route get() = stack.last()
    fun push(r: Route) { if (r.type == "stats") goStats() else stack.add(r) }
    fun back(): Boolean { if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }; return false }
    fun reset(r: Route) { stack.clear(); stack.add(r) }
    fun listState(key: String): LazyListState = listStates.getOrPut(key) { LazyListState() }
}

@Composable
fun AppRoot(vm: PlayerViewModel) {
    val perms = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 33) { add(Manifest.permission.READ_MEDIA_AUDIO); add(Manifest.permission.POST_NOTIFICATIONS) }
            else add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.loadSongs() }
    LaunchedEffect(Unit) { launcher.launch(perms) }

    val name by vm.profile.name.collectAsState(initial = null)
    var welcomed by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            name == null -> {}
            name == "" -> Box(Modifier.statusBarsPadding()) { ProfileScreen(vm, first = true) }
            !welcomed -> Welcome(vm, name!!) { welcomed = true }
            else -> MainScaffold(vm)
        }
    }
}

@Composable
fun Welcome(vm: PlayerViewModel, name: String, onDone: () -> Unit) {
    val photo by vm.profile.photo.collectAsState(initial = null)
    LaunchedEffect(Unit) { delay(2200); onDone() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(photo, 120)
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Bienvenido, $name",
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MainScaffold(vm: PlayerViewModel) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val pager = rememberPagerState(initialPage = 2, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val nav = remember { Nav { scope.launch { pager.animateScrollToPage(0) } } }
    val req by vm.requestPlayerTab.collectAsState()
    val current by vm.current.collectAsState()
    val style by vm.uiStyle.collectAsState()
    val accent by vm.accent.collectAsState()
    val coverMode by vm.coverShape.collectAsState()
    val colorMode by vm.colorMode.collectAsState()
    // Negro total solo aplica al tema minimalista (Clásico); los demás temas conservan su color.
    val effAccent = if (style == STYLE_CLASSIC && colorMode == COLOR_BLACK) Color.Black else accent
    val styleRoot = Route(when (style) {
        STYLE_XMB -> STYLE_XMB; STYLE_ITUNES -> STYLE_ITUNES; STYLE_VISTA -> STYLE_VISTA; else -> "songs"
    })

    var bootstrapped by remember { mutableStateOf(false) }
    var baselineStyle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val saved = vm.profile.lastRoute.first()
        val s = vm.uiStyle.first()
        baselineStyle = s
        val sr = Route(when (s) { STYLE_XMB -> STYLE_XMB; STYLE_ITUNES -> STYLE_ITUNES; STYLE_VISTA -> STYLE_VISTA; else -> "songs" })
        val r = if (saved != null && saved.first !in listOf(STYLE_XMB, STYLE_ITUNES, STYLE_VISTA))
            Route(saved.first, saved.second) else sr
        nav.stack.clear(); nav.stack.add(r)
        bootstrapped = true
    }
    LaunchedEffect(style, bootstrapped) {
        if (bootstrapped && baselineStyle != null && baselineStyle != style) {
            nav.reset(styleRoot); baselineStyle = style
        }
    }
    LaunchedEffect(nav.current) {
        val c = nav.current
        vm.saveLastRoute(c.type, c.value)
    }
    LaunchedEffect(req) { if (req) { pager.animateScrollToPage(1); vm.requestPlayerTab.value = false } }

    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(!drawer.isOpen && pager.currentPage == 2 && nav.stack.size > 1) { nav.back() }

    val bgMod = Modifier.fillMaxSize()
    val isVista = style == STYLE_VISTA
    val isXmb = style == STYLE_XMB

    // ⭐ NUEVO: LocalAccent provisto junto con LocalStyle
    CompositionLocalProvider(
        LocalStyle provides style,
        LocalAccent provides effAccent,
        LocalCoverShape provides coverMode
    ) {
        Box(if (isVista) bgMod.background(VistaBgBrush) else bgMod.background(Color.Black)) {
            if (isXmb) WaveBackground(Modifier.fillMaxSize())
            if (isVista) {
                Box(Modifier.fillMaxWidth().height(120.dp).background(
                    Brush.verticalGradient(listOf(Color(0x33A0D8FF), Color.Transparent))
                ))
            }

            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = drawer.isOpen,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = when (style) {
                            STYLE_XMB -> Color(0xE6000000)
                            STYLE_VISTA -> Color(0xEE0F2540)
                            STYLE_CLASSIC -> Color(0xFF000000)
                            else -> Color(0xFF101010)
                        },
                        modifier = Modifier.width(300.dp)
                    ) { DrawerContent(vm) { r ->
                        scope.launch {
                            if (r.type == "stats") { pager.scrollToPage(0) }
                            else { nav.reset(styleRoot); if (r != styleRoot) nav.push(r); pager.scrollToPage(2) }
                            drawer.close()
                        }
                    } }
                }
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        Column {
                            if (pager.currentPage != 1 && current != null) MiniPlayer(vm) { scope.launch { pager.animateScrollToPage(1) } }
                            StyleDock(style, pager.currentPage) { i ->
                                scope.launch {
                                    when (i) {
                                        0 -> pager.animateScrollToPage(0)
                                        1 -> pager.animateScrollToPage(1)
                                        else -> pager.animateScrollToPage(2)
                                    }
                                }
                            }
                        }
                    }
                ) { pad ->
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())
                    ) { page ->
                        when (page) {
                            0 -> StatsPage(vm)
                            1 -> NowPlaying(vm)
                            else -> XmbStyledBrowse(vm, nav) { scope.launch { drawer.open() } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XmbStyledBrowse(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit) {
    val style = LocalStyle.current
    val route = nav.current
    if (style == STYLE_XMB) {
        val key = "${nav.stack.size}:${route.type}:${route.value}"
        AnimatedContent(
            targetState = key,
            transitionSpec = {
                (slideInHorizontally(animationSpec = tween(340, easing = FastOutSlowInEasing)) { it } +
                        fadeIn(animationSpec = tween(260)))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = tween(340, easing = FastOutSlowInEasing)) { -it / 2 } +
                                fadeOut(animationSpec = tween(200))
                    )
            },
            label = "xmbNav"
        ) { _ ->
            BrowseHost(vm, nav, onMenu)
        }
    } else {
        BrowseHost(vm, nav, onMenu)
    }
}

// ------------------------------------------------------------ Dock
@Composable
private fun StyleDock(style: String, selected: Int, onClick: (Int) -> Unit) {
    when (style) {
        STYLE_CLASSIC -> {
            Row(
                Modifier.fillMaxWidth().background(color = Color.Black)
                    .border(width = 1.dp, color = LocalAccent.current.let { if (it == Color.Black) Color.White.copy(alpha = 0.08f) else it.copy(alpha = 0.45f) })
                    .navigationBarsPadding().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClassicDockItem(Icons.Default.BarChart, "Estadísticas", selected == 0) { onClick(0) }
                ClassicDockItem(Icons.Default.PlayCircleFilled, "Reproduciendo", selected == 1) { onClick(1) }
                ClassicDockItem(Icons.Default.LibraryMusic, "Biblioteca", selected == 2) { onClick(2) }
            }
        }
        else -> {
            val brush = when (style) {
                STYLE_VISTA -> VistaGlassBrush
                STYLE_XMB -> Brush.verticalGradient(listOf(Color(0xE6000000), Color(0xE6000000)))
                else -> ITunesMetalBrush
            }
            Row(
                Modifier.fillMaxWidth().background(brush).border(1.dp, Color.Black.copy(.5f))
                    .navigationBarsPadding().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockItem(Icons.Default.BarChart, "Estadísticas", selected == 0, Color.White) { onClick(0) }
                DockItem(Icons.Default.PlayCircleFilled, "Reproduciendo", selected == 1, Color.White) { onClick(1) }
                DockItem(Icons.Default.LibraryMusic, "Biblioteca", selected == 2, Color.White) { onClick(2) }
            }
        }
    }
}

@Composable
private fun ClassicDockItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable { onClick() }.padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (active) Color.White else Color.White.copy(.35f), modifier = Modifier.size(22.dp))
        Text(label,
            color = if (active) Color.White else Color.White.copy(.35f),
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1)
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: String, active: Boolean, labelColor: Color, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }.padding(horizontal = 16.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (active) labelColor else labelColor.copy(.55f), modifier = Modifier.size(22.dp))
        Text(label, color = if (active) labelColor else labelColor.copy(.55f), fontSize = 10.sp, maxLines = 1)
    }
}

// ------------------------------------------------------------ MiniPlayer
@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val song by vm.current.collectAsState()
    val art by vm.art.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val pos by vm.position.collectAsState()
    val s = song ?: return
    val style = LocalStyle.current

    val outerBrush = when (style) {
        STYLE_VISTA -> VistaGlassBrush
        STYLE_XMB -> Brush.verticalGradient(listOf(Color(0xCC000000), Color(0xCC000000)))
        STYLE_ITUNES -> ITunesMetalBrush
        else -> Brush.verticalGradient(listOf(Color(0xFF0A0A0A), Color(0xFF0A0A0A)))
    }
    val innerBrush = when (style) {
        STYLE_VISTA -> Brush.verticalGradient(listOf(Color(0xCC0A1E33), Color(0xCC071523)))
        STYLE_XMB -> Brush.verticalGradient(listOf(Color(0x99000000), Color(0x99000000)))
        STYLE_ITUNES -> ITunesLCDBrush
        else -> classicCardColor().let { Brush.verticalGradient(listOf(it, it)) }
    }
    val textColor = if (style == STYLE_ITUNES) Color(0xFF111111) else Color.White
    val subColor = if (style == STYLE_ITUNES) Color(0xFF333333) else Color.White.copy(.7f)
    val accent = when (style) {
        STYLE_VISTA -> VistaAccent
        STYLE_CLASSIC -> Color.White
        else -> ITunesBlue
    }
    val cornerRadius = if (style == STYLE_CLASSIC) 18.dp else 6.dp

    Box(
        Modifier.fillMaxWidth().background(outerBrush)
            .border(1.dp, Color.Black.copy(.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .background(innerBrush)
                .clickable { onOpen() }
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp)
                    .clip(coverShape(RoundedCornerShape(if (style == STYLE_CLASSIC) 12.dp else 4.dp), 40f))
                    .background(Color(0xFF222222))
            ) {
                art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.artist} — ${s.album}", color = subColor, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = { (pos.toFloat() / s.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(3.dp),
                    color = accent, trackColor = Color(0x33000000)
                )
            }
            IconButton({ vm.prev() }, Modifier.size(30.dp)) {
                Icon(Icons.Default.SkipPrevious, "Anterior", tint = textColor, modifier = Modifier.size(20.dp))
            }
            IconButton({ vm.toggle() }, Modifier.size(30.dp)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = textColor, modifier = Modifier.size(24.dp))
            }
            IconButton({ vm.next() }, Modifier.size(30.dp)) {
                Icon(Icons.Default.SkipNext, "Siguiente", tint = textColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ------------------------------------------------------------ Drawer
@Composable
private fun DrawerItem(icon: ImageVector, label: String, count: Int? = null, trailing: ImageVector? = null,
                       active: Boolean = false, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()
        .background(if (active) accent else Color.Transparent)
        .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(.85f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
        count?.let { Text("$it", color = Color.White.copy(.55f), fontSize = 12.sp) }
        trailing?.let { Icon(it, null, tint = Color.White.copy(.6f), modifier = Modifier.size(18.dp)) }
    }
}

@Composable
fun DrawerContent(vm: PlayerViewModel, onGo: (Route) -> Unit) {
    val lib by vm.library.collectAsState()
    val all by vm.allSongs.collectAsState()
    val playlists by vm.playlists.collectAsState(emptyList())
    val ent by vm.entries.collectAsState(emptyList())
    var plOpen by remember { mutableStateOf(false) }
    var newDialog by remember { mutableStateOf(false) }
    val style = LocalStyle.current
    val accent = when (style) {
        STYLE_VISTA -> VistaAccent
        STYLE_CLASSIC -> Color.White
        else -> ITunesBlue
    }
    val headerBrush = when (style) {
        STYLE_VISTA -> VistaGlassBrush
        STYLE_XMB -> Brush.verticalGradient(listOf(Color(0xE6000000), Color(0xE6000000)))
        STYLE_CLASSIC -> Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF000000)))
        else -> ITunesMetalBrush
    }
    val nAlbums = remember(lib) { lib.map { it.albumId }.distinct().size }
    val nArtists = remember(lib) { lib.map { it.artist }.distinct().size }
    val nGenres = remember(lib) { lib.map { it.genre }.distinct().size }
    val nFolders = remember(all) { all.map { it.folder }.distinct().size }

    Column(Modifier.fillMaxHeight()) {
        val photo by vm.profile.photo.collectAsState(initial = null)
        val alias by vm.profile.name.collectAsState(initial = "")
        Row(Modifier.fillMaxWidth().background(headerBrush).statusBarsPadding()
            .clickable { onGo(Route("profile")) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Avatar(photo, 52)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(alias.ifBlank { "Personal Music Player" }, color = Color.White, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Mi cuenta", color = Color.White.copy(.65f), fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            item { DrawerItem(Icons.Default.MusicNote, "Canciones", lib.size, accent = accent) { onGo(Route("songs")) } }
            item { DrawerItem(Icons.Default.Album, "Álbumes", nAlbums, accent = accent) { onGo(Route("albums")) } }
            item { DrawerItem(Icons.Default.Mic, "Artistas", nArtists, accent = accent) { onGo(Route("artists")) } }
            item { DrawerItem(Icons.Default.Category, "Géneros", nGenres, accent = accent) { onGo(Route("genres")) } }
            item { DrawerItem(Icons.Default.Folder, "Carpetas", nFolders, accent = accent) { onGo(Route("folders")) } }
            item { DrawerItem(Icons.Default.BarChart, "Estadísticas", accent = accent) { onGo(Route("stats")) } }
            item { HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color.White.copy(.12f)) }
            item {
                DrawerItem(Icons.Default.Favorite, "Favoritos", accent = accent) {
                    onGo(Route("favorites"))
                }
            }
            item {
                DrawerItem(Icons.Default.History, "Reproducido recientemente", accent = accent) {
                    onGo(Route("history"))
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color.White.copy(.12f)) }
            item {
                DrawerItem(Icons.Default.QueueMusic, "Playlists", playlists.size,
                    trailing = if (plOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, accent = accent) { plOpen = !plOpen }
            }
            if (plOpen) {
                items(playlists, key = { "pl${it.id}" }) { p ->
                    Row(Modifier.fillMaxWidth().clickable { onGo(Route("playlist", p.id.toString())) }
                        .padding(start = 50.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)) {
                        Text(p.name, color = Color.White.copy(.9f), fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${ent.count { e -> e.playlistId == p.id }}", color = Color.White.copy(.5f), fontSize = 12.sp)
                    }
                }
                item { TextButton({ newDialog = true }, Modifier.padding(start = 40.dp)) { Text("+ Nueva playlist", color = accent) } }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color.White.copy(.12f)) }
            item { DrawerItem(Icons.Default.Settings, "Configuración", accent = accent) { onGo(Route("settings")) } }
            item { DrawerItem(Icons.Default.Person, "Mi cuenta", accent = accent) { onGo(Route("profile")) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (newDialog) {
        var n by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { newDialog = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Nueva playlist", color = Color.White) },
            text = { OutlinedTextField(n, { n = it }, singleLine = true, label = { Text("Nombre") }) },
            confirmButton = { TextButton({ if (n.isNotBlank()) vm.createPlaylist(n); newDialog = false }) { Text("Crear", color = accent) } },
            dismissButton = { TextButton({ newDialog = false }) { Text("Cancelar", color = accent) } })
    }
}

// ============================================================ Fondo por estilo
@Composable
fun StyleScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val style = LocalStyle.current
    val accent = LocalAccent.current   // ⭐ NUEVO: lee el acento global

    when (style) {
        STYLE_XMB -> Box(modifier.background(Color.Black)) {
            WaveBackground(Modifier.fillMaxSize(), alpha = 0.75f)
            content()
        }
        STYLE_VISTA -> Box(modifier.background(VistaBgBrush)) {
            Box(
                Modifier.fillMaxWidth().height(140.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x33A0D8FF), Color.Transparent)))
            )
            content()
        }
        STYLE_ITUNES -> Box(modifier.background(Color.Black), content = content)
        else -> {
            val anim = animateColorAsState(accent, tween(700), label = "classicBg")
            Box(
                modifier.drawBehind {
                    val a = anim.value
                    drawRect(Brush.verticalGradient(listOf(a.copy(alpha = 0.60f), a.copy(alpha = 0.22f), ClassicBgBottom)))
                },
                content = content
            )
        }
    }
}

// ============================================================ Header adaptativo
@Composable
fun StyleHeader(
    vm: PlayerViewModel, nav: Nav, title: String, subtitle: String?,
    onMenu: () -> Unit, search: Boolean = true
) {
    val style = LocalStyle.current

    if (style == STYLE_CLASSIC) {
        val photo by vm.profile.photo.collectAsState(initial = null)
        val accent = LocalAccent.current
        Row(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.55f),
                            accent.copy(alpha = 0.18f)
                        )
                    )
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onMenu) { Icon(Icons.Default.Menu, "Menú", tint = Color.White) }
            if (nav.stack.size > 1) {
                IconButton({ nav.back() }) { Icon(Icons.Default.ArrowBack, "Atrás", tint = Color.White) }
            } else {
                Box(Modifier.padding(4.dp).clip(CircleShape).clickable { nav.push(Route("profile")) }) {
                    Avatar(photo, 32)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, color = Color.White.copy(.7f), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (search && nav.current.type != "search")
                IconButton({ nav.push(Route("search")) }) { Icon(Icons.Default.Search, "Buscar", tint = Color.White) }
        }
    } else {
        Header(vm, nav, title, subtitle, onMenu, search)
    }
}

// ============================================================ Filas adaptativas
@Composable
fun StyleSongRow(
    vm: PlayerViewModel, s: Song, queue: List<Song>, index: Int,
    number: Int? = null, playlistId: Long? = null, zebra: Boolean = false
) {
    val style = LocalStyle.current
    when (style) {
        STYLE_XMB -> XmbSongRow(vm, s, queue, index, number, playlistId)
        STYLE_VISTA -> VistaSongRow(vm, s, queue, index, number, zebra, playlistId)
        STYLE_ITUNES -> SongRow(vm, s, queue, index, number, playlistId, zebra)
        else -> ClassicSongRow(vm, s, queue, index, number, playlistId)
    }
}

@Composable
fun StyleAlbumRow(nav: Nav, a: AlbumItem, zebra: Boolean = false) {
    val style = LocalStyle.current
    when (style) {
        STYLE_XMB -> XmbAlbumRow(nav, a)
        STYLE_VISTA -> VistaAlbumRow(nav, a, zebra)
        else -> AlbumRow(nav, a, zebra)
    }
}

@Composable
fun StyleArtistRow(nav: Nav, a: ArtistItem, zebra: Boolean = false) {
    val style = LocalStyle.current
    when (style) {
        STYLE_XMB -> XmbArtistRow(nav, a)
        STYLE_VISTA -> VistaArtistRow(nav, a, zebra)
        else -> ArtistRow(nav, a, zebra)
    }
}

// ============================================================ Filas XMB (con portada)
@Composable
private fun XmbRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        } else {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(.28f), Color.White.copy(.05f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Text(it, color = Color.White.copy(.45f), fontSize = 11.sp) }
    }
}

@Composable
private fun XmbSongRow(
    vm: PlayerViewModel, s: Song, queue: List<Song>, index: Int, number: Int?, playlistId: Long?
) {
    var sheet by remember { mutableStateOf(false) }
    XmbRow(
        icon = Icons.Default.MusicNote,
        title = s.title,
        subtitle = if (number != null) s.artist else "${s.artist} · ${s.album}",
        trailing = fmt(s.durationMs),
        onLongClick = { sheet = true },
        onClick = { vm.play(queue, index) }
    )
    SongActions(vm, s, playlistId, sheet) { sheet = false }
}

@Composable
private fun XmbAlbumRow(nav: Nav, a: AlbumItem) =
    XmbRow(
        icon = Icons.Default.Album,
        title = a.name,
        subtitle = "${a.artist} · ${a.count} canciones",
        onClick = { nav.push(Route("album", a.id.toString())) },
        leading = { Thumb(a.first, 56, RoundedCornerShape(4.dp), px = 220) }
    )

@Composable
private fun XmbArtistRow(nav: Nav, a: ArtistItem) =
    XmbRow(
        icon = Icons.Default.Person,
        title = a.name,
        subtitle = "${a.albums} álbumes · ${a.songs} canciones",
        onClick = { nav.push(Route("artist", a.name)) },
        leading = { Thumb(a.first, 56, CircleShape, px = 220) }
    )

// ============================================================ Filas Vista
@Composable
private fun VistaRow(
    zebra: Boolean, title: String, subtitle: String, trailing: String? = null,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit, leading: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (zebra) VistaRowOdd else VistaRowEven)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Text(it, color = Color.White.copy(.5f), fontSize = 11.sp) }
    }
}

@Composable
private fun VistaSongRow(
    vm: PlayerViewModel, s: Song, queue: List<Song>, index: Int, number: Int?, zebra: Boolean, playlistId: Long?
) {
    var sheet by remember { mutableStateOf(false) }
    VistaRow(
        zebra, s.title,
        if (number != null) s.artist else "${s.artist} · ${s.album}",
        fmt(s.durationMs), onLongClick = { sheet = true }, onClick = { vm.play(queue, index) }
    ) {
        if (number != null) Text("$number", color = Color.White.copy(.55f), modifier = Modifier.width(28.dp), fontSize = 13.sp)
        else { Thumb(s, 44, RoundedCornerShape(6.dp)); Spacer(Modifier.width(10.dp)) }
    }
    SongActions(vm, s, playlistId, sheet) { sheet = false }
}

@Composable
private fun VistaAlbumRow(nav: Nav, a: AlbumItem, zebra: Boolean) =
    VistaRow(zebra, a.name, "${a.artist} · ${a.count} canciones",
        onClick = { nav.push(Route("album", a.id.toString())) }) {
        Thumb(a.first, 48, RoundedCornerShape(6.dp)); Spacer(Modifier.width(12.dp))
    }

@Composable
private fun VistaArtistRow(nav: Nav, a: ArtistItem, zebra: Boolean) =
    VistaRow(zebra, a.name, "${a.albums} álbumes · ${a.songs} canciones",
        onClick = { nav.push(Route("artist", a.name)) }) {
        Thumb(a.first, 48, CircleShape); Spacer(Modifier.width(12.dp))
    }

// ============================================================ Filas Clásico
@Composable
private fun ClassicSongRow(
    vm: PlayerViewModel, s: Song, queue: List<Song>, index: Int, number: Int?, playlistId: Long?
) {
    var sheet by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(classicCardColor())
            .combinedClickable(onClick = { vm.play(queue, index) }, onLongClick = { sheet = true })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null)
            Text("$number", color = Color.White.copy(.5f), modifier = Modifier.width(28.dp), fontSize = 13.sp)
        else {
            Thumb(s, 46, RoundedCornerShape(12.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(s.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (number != null) s.artist else "${s.artist} · ${s.album}",
                color = Color.White.copy(.55f), fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        FavMark(vm, s.id)
        Text(fmt(s.durationMs), color = Color.White.copy(.5f), fontSize = 11.sp)
    }
    SongActions(vm, s, playlistId, sheet) { sheet = false }
}

// ============================================================ FavMark (corazón en filas)
@Composable
fun FavMark(vm: PlayerViewModel, id: Long) {
    val favs by vm.favorites.collectAsState()
    if (id in favs) {
        Icon(
            Icons.Default.Favorite, "Favorita",
            tint = Color.White.copy(.85f),
            modifier = Modifier.padding(end = 6.dp).size(14.dp)
        )
    }
}