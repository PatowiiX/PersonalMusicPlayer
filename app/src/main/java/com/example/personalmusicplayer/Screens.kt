@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.personalmusicplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ============================================================ NowPlaying
@Composable
fun NowPlaying(vm: PlayerViewModel) {
    val style = LocalStyle.current
    val song by vm.current.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val pos by vm.position.collectAsState()
    val art by vm.art.collectAsState()
    val accent by vm.accent.collectAsState()
    val quality by vm.quality.collectAsState()
    val bt by vm.bluetooth.device.collectAsState()
    val mode by vm.vinylMode.collectAsState()
    val sleepLeft by vm.sleepMinutesLeft.collectAsState()
    var info by remember { mutableStateOf(false) }
    var addPl by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }

    val songId = song?.id
    val isFav by remember(songId) {
        if (songId == null) kotlinx.coroutines.flow.flowOf(false)
        else vm.isFavorite(songId)
    }.collectAsState(initial = false)

    val color by animateColorAsState(accent, tween(900), label = "accent")
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                angle = (angle + (now - last) / 1e9f * 60f) % 360f
                last = now
            }
        }
    }

    val accentColor = when (style) {
        STYLE_VISTA -> VistaAccent
        STYLE_CLASSIC -> Color.White
        else -> ITunesBlue
    }
    val outerBg: Brush = when (style) {
        STYLE_XMB -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        STYLE_VISTA -> Brush.verticalGradient(listOf(color.copy(alpha = .35f), Color(0xFF07101F)))
        STYLE_CLASSIC -> Brush.verticalGradient(
            listOf(
                color.copy(alpha = 0.35f),
                Color.Black
            )
        )
        else -> Brush.verticalGradient(listOf(color.copy(alpha = .55f), Color.Black))
    }
    val textColor = Color.White
    val subColor = Color.White.copy(.75f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = outerBg)
    ) {
        val maxW = this.maxWidth.value
        val maxH = this.maxHeight.value

        val scaleW = maxW / 360f
        val scaleH = maxH / 640f
        val scale = minOf(scaleW, scaleH).coerceIn(0.72f, 1.30f)

        val coverSizeDp = minOf(maxW * 0.80f, maxH * 0.42f).coerceAtLeast(110f)
        val ctrlBig = (64f * scale).coerceIn(48f, 80f).dp
        val ctrlMid = (48f * scale).coerceIn(38f, 60f).dp
        val ctrlSm = (42f * scale).coerceIn(34f, 54f).dp
        val ctrlIconBig = (36f * scale).coerceIn(28f, 46f).dp
        val ctrlIconMid = (28f * scale).coerceIn(22f, 36f).dp
        val ctrlIconSm = (22f * scale).coerceIn(16f, 28f).dp
        val hPad = (24f * scale).coerceIn(14f, 32f).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top bar con tinte del álbum en Clásico ─────────────
            val topBrush = when (style) {
                STYLE_VISTA -> VistaGlassBrush
                STYLE_XMB -> Brush.verticalGradient(listOf(Color(0xCC000000), Color(0xCC000000)))
                STYLE_CLASSIC -> Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = 0.45f),
                        Color.Transparent
                    )
                )
                else -> ITunesMetalBrush
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = topBrush)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var dm by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { dm = true }) {
                        Icon(Icons.Default.Album, "Visualizador", tint = Color.White.copy(.85f))
                    }
                    DropdownMenu(expanded = dm, onDismissRequest = { dm = false }) {
                        DropdownMenuItem(
                            text = { Text("Vinilo de fondo") },
                            onClick = { vm.setVinyl(0); dm = false },
                            leadingIcon = if (mode == 0) ({ Icon(Icons.Default.Check, null) }) else null
                        )
                        DropdownMenuItem(
                            text = { Text("Sin visualizador") },
                            onClick = { vm.setVinyl(1); dm = false },
                            leadingIcon = if (mode != 0) ({ Icon(Icons.Default.Check, null) }) else null
                        )
                    }
                }

                if (style == STYLE_ITUNES) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(brush = ITunesLCDBrush)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = song?.title ?: "Personal Music Player",
                            color = Color(0xFF111111),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        Text(
                            text = song?.let { "${it.artist} — ${it.album}" } ?: "Biblioteca",
                            color = Color(0xFF333333),
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                var mm by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { mm = true }, enabled = song != null) {
                        Icon(Icons.Default.MoreVert, "Más", tint = Color.White.copy(.85f))
                    }
                    DropdownMenu(expanded = mm, onDismissRequest = { mm = false }) {
                        DropdownMenuItem(
                            text = { Text("Información de la canción") },
                            onClick = { mm = false; info = true },
                            leadingIcon = { Icon(Icons.Default.Info, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Agregar a playlist") },
                            onClick = { mm = false; addPl = true },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (sleepLeft > 0) "Temporizador ($sleepLeft min)"
                                    else "Temporizador de apagado"
                                )
                            },
                            onClick = { mm = false; sleepDialog = true },
                            leadingIcon = { Icon(Icons.Default.Timer, null) }
                        )
                    }
                }
            }

            // Banner de sleep timer
            if (sleepLeft > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accentColor.copy(alpha = .25f))
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Timer, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Se pausa en $sleepLeft min",
                            color = Color.White, fontSize = 11.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Cancelar",
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { vm.cancelSleepTimer() }
                        )
                    }
                }
            }

            // Banner de Bluetooth
            bt?.let { deviceName ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(.12f))
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bluetooth, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = deviceName,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height((6 * scale).dp))

            // ── Portada ────────────────────────────────────────────
            val coverShape = when (style) {
                STYLE_XMB -> RoundedCornerShape(2.dp)
                STYLE_VISTA -> RoundedCornerShape(8.dp)
                STYLE_CLASSIC -> RoundedCornerShape(20.dp)
                else -> RoundedCornerShape(8.dp)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(coverSizeDp.dp)
                    .shadow(if (style == STYLE_XMB) 8.dp else 24.dp, coverShape)
                    .clip(coverShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                color.copy(alpha = 0.55f),
                                color.copy(alpha = 0.20f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.10f), coverShape)
            ) {
                val a = art
                if (a != null) {
                    Image(a.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Album, null,
                            tint = Color.White.copy(.4f),
                            modifier = Modifier.size((coverSizeDp * 0.5f).dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height((8 * scale).dp))

            // ── Ficha de info ─────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = hPad),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = song?.title ?: "Nada reproduciéndose",
                    color = textColor,
                    fontSize = (18f * scale).coerceIn(14f, 22f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = song?.artist ?: "—",
                    color = subColor,
                    fontSize = (13f * scale).coerceIn(11f, 15f).sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
                song?.let { s ->
                    val detail = buildString {
                        if (s.album.isNotBlank() && s.album != "Álbum desconocido") append(s.album)
                        if (s.year > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(s.year)
                        }
                    }
                    if (detail.isNotBlank()) {
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = detail,
                            color = subColor.copy(.85f),
                            fontSize = (11f * scale).coerceIn(10f, 13f).sp,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                    if (quality.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = quality,
                            color = subColor.copy(.65f),
                            fontSize = (10f * scale).coerceIn(9f, 12f).sp,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Sliders ───────────────────────────────────────────
            val dur = (song?.durationMs ?: 0L).coerceAtLeast(1L)
            Column(Modifier.fillMaxWidth().padding(horizontal = hPad)) {
                ProgressSliderRow(vm, style, dur)
                ProgressTimeLabels(vm, dur, subColor)

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeDown, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    ThemedSlider(
                        progress = volume,
                        style = style,
                        modifier = Modifier.weight(1f),
                        onSeek = { volume = it; vm.setVolume(it) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height((6 * scale).dp))

            // ── Controles con requiredSize ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * scale).dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val circleBrush = when (style) {
                    STYLE_VISTA -> Brush.verticalGradient(
                        listOf(Color(0xFFE8F3FF), Color(0xFF88B8E0), Color(0xFF4C7AA8))
                    )
                    STYLE_CLASSIC -> Brush.verticalGradient(listOf(Color.White, Color(0xFFD8D8D8)))
                    else -> Brush.verticalGradient(listOf(Color.White, Color(0xFFE8E8E8)))
                }
                val iconColor = when (style) {
                    STYLE_VISTA -> Color(0xFF0A1E33)
                    STYLE_CLASSIC -> Color.Black
                    else -> Color(0xFF222222)
                }

                // Aleatorio
                Box(
                    modifier = Modifier
                        .requiredSize(ctrlSm)
                        .clip(CircleShape)
                        .then(
                            when {
                                style == STYLE_CLASSIC && shuffle ->
                                    Modifier.background(Color.White).border(2.dp, Color.Black, CircleShape)
                                style == STYLE_CLASSIC ->
                                    Modifier.background(Color.Transparent).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                shuffle ->
                                    Modifier.background(Brush.verticalGradient(listOf(accentColor, accentColor.copy(.7f))))
                                else ->
                                    Modifier.background(circleBrush)
                            }
                        )
                        .clickable { vm.toggleShuffle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Shuffle, "Aleatorio",
                        tint = when {
                            style == STYLE_CLASSIC && shuffle -> Color.Black
                            style == STYLE_CLASSIC -> Color.White
                            shuffle -> Color.White
                            else -> iconColor
                        },
                        modifier = Modifier.requiredSize(ctrlIconSm)
                    )
                }

                // Anterior
                Box(
                    modifier = Modifier
                        .requiredSize(ctrlMid)
                        .clip(CircleShape)
                        .background(brush = circleBrush)
                        .clickable { vm.prev() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SkipPrevious, "Anterior",
                        tint = iconColor,
                        modifier = Modifier.requiredSize(ctrlIconMid)
                    )
                }

                // Play / Pause
                Box(
                    modifier = Modifier
                        .requiredSize(ctrlBig)
                        .clip(CircleShape)
                        .background(brush = circleBrush)
                        .clickable { vm.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.requiredSize(ctrlIconBig)
                    )
                }

                // Siguiente
                Box(
                    modifier = Modifier
                        .requiredSize(ctrlMid)
                        .clip(CircleShape)
                        .background(brush = circleBrush)
                        .clickable { vm.next() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SkipNext, "Siguiente",
                        tint = iconColor,
                        modifier = Modifier.requiredSize(ctrlIconMid)
                    )
                }

                // Favorito
                Box(
                    modifier = Modifier
                        .requiredSize(ctrlSm)
                        .clip(CircleShape)
                        .background(brush = circleBrush)
                        .clickable(enabled = song != null) {
                            song?.let { vm.toggleFavorite(it.id) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFav) "Quitar de favoritos" else "Agregar a favoritos",
                        tint = if (isFav) Color(0xFFE24A6A) else iconColor,
                        modifier = Modifier.requiredSize(ctrlIconSm)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (info) song?.let { InfoDialog(vm, it) { info = false } }
    if (addPl) song?.let { AddToPlaylistDialog(vm, it) { addPl = false } }
    if (sleepDialog) {
        SleepTimerDialog(
            currentLeft = sleepLeft,
            onDismiss = { sleepDialog = false },
            onSet = { min -> vm.setSleepTimer(min); sleepDialog = false },
            onCancel = { vm.cancelSleepTimer(); sleepDialog = false }
        )
    }
}

// ------------------------------------------------------------ Sub-composables
@Composable
private fun ProgressSliderRow(vm: PlayerViewModel, style: String, durationMs: Long) {
    val pos by vm.position.collectAsState()
    ThemedSlider(
        progress = (pos.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f),
        style = style,
        onSeek = { vm.seekTo((it * durationMs).toLong()) }
    )
}

@Composable
private fun ProgressTimeLabels(vm: PlayerViewModel, durationMs: Long, color: Color) {
    val pos by vm.position.collectAsState()
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(fmt(pos), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(fmt(durationMs), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------ Slider responsivo
@Composable
fun ThemedSlider(
    progress: Float,
    style: String,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit
) {
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val height = when (style) {
        STYLE_ITUNES -> 36.dp
        STYLE_VISTA -> 44.dp
        STYLE_CLASSIC -> 40.dp
        STYLE_XMB -> 40.dp
        else -> 40.dp
    }
    val thumbInsetDp = when (style) {
        STYLE_VISTA -> 18.dp
        STYLE_CLASSIC -> 16.dp
        STYLE_XMB -> 16.dp
        else -> 14.dp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(style) {
                val inset = thumbInsetDp.toPx()
                val trackW = (size.width - 2 * inset).coerceAtLeast(1f)

                fun frac(x: Float) = ((x - inset) / trackW).coerceIn(0f, 1f)

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onSeek(frac(down.position.x))
                    down.consume()
                    val pointerId = down.id
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!ch.pressed) break
                        onSeek(frac(ch.position.x))
                        ch.consume()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val cy = size.height / 2f
            when (style) {
                STYLE_ITUNES -> drawITunesSlider(w, cy, progress)
                STYLE_VISTA -> drawVistaSlider(w, cy, progress)
                STYLE_CLASSIC -> drawClassicSlider(w, cy, progress)
                else -> drawXmbSlider(w, cy, progress)
            }
        }
    }
}

private fun DrawScope.drawITunesSlider(w: Float, cy: Float, progress: Float) {
    val trackH = 14f
    val top = cy - trackH / 2f
    val corner = CornerRadius(trackH / 2f)
    val thumbR = 14f
    val inset = thumbR
    val trackW = w - 2 * inset

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF3A3A3A), Color(0xFF1A1A1A), Color(0xFF2A2A2A)),
            startY = top, endY = top + trackH
        ),
        topLeft = Offset(inset, top),
        size = GSize(trackW, trackH),
        cornerRadius = corner
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.5f),
        topLeft = Offset(inset, top),
        size = GSize(trackW, trackH),
        cornerRadius = corner,
        style = Stroke(width = 1.5f)
    )
    if (progress > 0f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF9CC8F0), Color(0xFF4A90D9), Color(0xFF2C6DB5)),
                startY = top, endY = top + trackH
            ),
            topLeft = Offset(inset, top),
            size = GSize(trackW * progress, trackH),
            cornerRadius = corner
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(inset, top),
            size = GSize(trackW * progress, trackH / 2f),
            cornerRadius = CornerRadius(trackH / 4f)
        )
    }
    val cx = inset + trackW * progress
    drawCircle(Color.Black.copy(alpha = 0.4f), radius = thumbR + 3f, center = Offset(cx, cy))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFE0E0E0)),
            center = Offset(cx - 3f, cy - 3f),
            radius = thumbR * 1.6f
        ),
        radius = thumbR, center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFF4A90D9).copy(alpha = 0.6f),
        radius = thumbR, center = Offset(cx, cy), style = Stroke(2f)
    )
    drawCircle(color = Color.White, radius = thumbR * 0.4f, center = Offset(cx - 3f, cy - 3f))
}

private fun DrawScope.drawVistaSlider(w: Float, cy: Float, progress: Float) {
    val trackH = 24f
    val top = cy - trackH / 2f
    val corner = CornerRadius(trackH / 2f)
    val thumbR = 18f
    val inset = thumbR
    val trackW = w - 2 * inset

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1A2A3D), Color(0xFF0E1D30), Color(0xFF132840)),
            startY = top, endY = top + trackH
        ),
        topLeft = Offset(inset, top),
        size = GSize(trackW, trackH),
        cornerRadius = corner
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0x88A0D8FF), Color(0x3380B8E8)),
            startY = top, endY = top + trackH
        ),
        topLeft = Offset(inset, top),
        size = GSize(trackW, trackH),
        cornerRadius = corner,
        style = Stroke(width = 2f)
    )
    if (progress > 0f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF7EC0EE), Color(0xFF3DAEE9), Color(0xFF1F6FA0)),
                startY = top, endY = top + trackH
            ),
            topLeft = Offset(inset, top),
            size = GSize(trackW * progress, trackH),
            cornerRadius = corner
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f)),
                startY = top, endY = top + trackH / 2f
            ),
            topLeft = Offset(inset, top),
            size = GSize(trackW * progress, trackH / 2f),
            cornerRadius = CornerRadius(trackH / 4f)
        )
    }
    val cx = inset + trackW * progress
    drawCircle(Color.Black.copy(alpha = 0.5f), radius = thumbR + 3f, center = Offset(cx, cy + 1f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFD8E8F8)),
            center = Offset(cx - 4f, cy - 4f),
            radius = thumbR * 1.8f
        ),
        radius = thumbR, center = Offset(cx, cy)
    )
    drawCircle(color = Color(0xFF3DAEE9), radius = thumbR, center = Offset(cx, cy), style = Stroke(3f))
    drawCircle(color = Color.White.copy(alpha = 0.8f), radius = thumbR * 0.35f, center = Offset(cx - 4f, cy - 4f))
}

private fun DrawScope.drawClassicSlider(w: Float, cy: Float, progress: Float) {
    val trackH = 16f
    val top = cy - trackH / 2f
    val corner = CornerRadius(trackH / 2f)
    val thumbR = 16f
    val inset = thumbR
    val trackW = w - 2 * inset

    drawRoundRect(
        color = Color(0xFF1E1E1E),
        topLeft = Offset(inset, top),
        size = GSize(trackW, trackH),
        cornerRadius = corner
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.08f),
        topLeft = Offset(inset, top),
        size = GSize(trackW, trackH),
        cornerRadius = corner,
        style = Stroke(width = 1.5f)
    )
    if (progress > 0f) {
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(inset, top),
            size = GSize(trackW * progress, trackH),
            cornerRadius = corner
        )
    }
    val cx = inset + trackW * progress
    drawCircle(Color.Black.copy(alpha = 0.5f), radius = thumbR + 3f, center = Offset(cx, cy))
    drawCircle(Color.White, radius = thumbR, center = Offset(cx, cy))
    drawCircle(Color.Black.copy(alpha = 0.15f), radius = thumbR - 6f, center = Offset(cx, cy))
}

private fun DrawScope.drawXmbSlider(w: Float, cy: Float, progress: Float) {
    val trackH = 16f
    val top = cy - trackH / 2f
    val corner = CornerRadius(trackH / 2f)
    val thumbR = 16f
    val inset = thumbR
    val trackW = w - 2 * inset

    drawRoundRect(
        color = Color(0xFF0A0A0A),
        topLeft = Offset(inset, top),
        size = GSize(trackW, trackH),
        cornerRadius = corner
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.15f),
        topLeft = Offset(inset - 1.5f, top - 1.5f),
        size = GSize(trackW + 3f, trackH + 3f),
        cornerRadius = CornerRadius(trackH / 2f + 1.5f),
        style = Stroke(width = 1.5f)
    )
    if (progress > 0f) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, Color(0xFFE0E8F0)),
                startY = top, endY = top + trackH
            ),
            topLeft = Offset(inset, top),
            size = GSize(trackW * progress, trackH),
            cornerRadius = corner
        )
    }
    val cx = inset + trackW * progress
    drawCircle(Color.White.copy(alpha = 0.25f), radius = thumbR + 8f, center = Offset(cx, cy))
    drawCircle(Color.Black.copy(alpha = 0.6f), radius = thumbR + 3f, center = Offset(cx, cy))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFE8F0F8)),
            center = Offset(cx - 3f, cy - 3f),
            radius = thumbR * 1.5f
        ),
        radius = thumbR, center = Offset(cx, cy)
    )
    drawCircle(Color.White, radius = thumbR * 0.4f, center = Offset(cx - 4f, cy - 4f))
}

@Composable
fun StyleSlider(progress: Float, accent: Color, modifier: Modifier = Modifier, onSeek: (Float) -> Unit) {
    val style = LocalStyle.current
    ThemedSlider(progress, style, modifier, onSeek)
}

@Composable
fun ITunesSlider(progress: Float, onSeek: (Float) -> Unit) =
    ThemedSlider(progress, STYLE_ITUNES, onSeek = onSeek)

// ------------------------------------------------------------ Vinilo
@Composable
fun VinylBackground(angle: Float, accent: Color, art: Bitmap?, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height * 0.62f)
        val r = size.minDimension * 0.75f
        rotate(angle, c) {
            drawCircle(Color(0xFF101010), r, c)
            var rr = r * 0.97f
            while (rr > r * 0.4f) {
                drawCircle(Color(0x33FFFFFF), rr, c, style = Stroke(1.2f))
                rr -= r * 0.03f
            }
            drawCircle(
                Brush.sweepGradient(
                    listOf(
                        Color.Transparent, Color.White.copy(.18f), Color.Transparent,
                        Color.White.copy(.18f), Color.Transparent
                    ), c
                ), r, c
            )
            drawCircle(accent, r * 0.33f, c)
            art?.let {
                val rad = r * 0.33f
                val d = (rad * 2f).toInt()
                val img = it.asImageBitmap()
                clipPath(Path().apply { addOval(Rect(c, rad)) }) {
                    drawImage(
                        image = img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset((c.x - rad).toInt(), (c.y - rad).toInt()),
                        dstSize = IntSize(d, d)
                    )
                }
            }
            drawCircle(Color.Black, r * 0.04f, c)
        }
    }
}

// ------------------------------------------------------------ Sleep timer
@Composable
fun SleepTimerDialog(
    currentLeft: Int,
    onDismiss: () -> Unit,
    onSet: (Int) -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Temporizador de apagado", color = Color.White) },
        confirmButton = {
            TextButton(onDismiss) { Text("Cerrar", color = ITunesBlue) }
        },
        text = {
            Column {
                Text(
                    if (currentLeft > 0) "Activo: se pausa en $currentLeft min"
                    else "La música se pausará automáticamente.",
                    color = Color.White.copy(.75f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                listOf(15, 30, 45, 60, 90).forEach { min ->
                    TextButton(
                        onClick = { onSet(min) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$min minutos", color = ITunesBlue, fontSize = 15.sp)
                    }
                }
                if (currentLeft > 0) {
                    HorizontalDivider(
                        Modifier.padding(vertical = 6.dp),
                        color = Color.White.copy(.1f)
                    )
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancelar temporizador", color = Color(0xFFE24A4A))
                    }
                }
            }
        }
    )
}

// ------------------------------------------------------------ Historial
@Composable
fun HistoryScreen(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit) {
    val history by vm.history.collectAsState(emptyList())
    val all by vm.allSongs.collectAsState()
    val byId = remember(all) { all.associateBy { it.id } }
    val items = remember(history, all) {
        history.mapNotNull { ev -> byId[ev.songId]?.let { it to ev.playedAt } }
    }

    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, "Reproducido recientemente", "${items.size} pistas", onMenu)
            if (items.isEmpty()) {
                Text(
                    "Aún no has reproducido nada.",
                    color = Color.White.copy(.6f),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(items, key = { i, pair -> "h${pair.first.id}_$i" }) { i, pair ->
                        val (s, ts) = pair
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (i % 2 == 0) ITunesRowOdd else ITunesRowEven)
                                .clickable { vm.play(items.map { it.first }, i) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Thumb(s, 44)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.title, color = Color.White, fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${s.artist} · ${timeAgo(ts)}",
                                    color = Color.White.copy(.55f), fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                fmt(s.durationMs),
                                color = Color.White.copy(.45f), fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun timeAgo(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "ahora"
        diff < 3_600_000 -> "${diff / 60_000} min"
        diff < 86_400_000 -> "${diff / 3_600_000} h"
        diff < 604_800_000 -> "${diff / 86_400_000} d"
        else -> "hace mucho"
    }
}

// ------------------------------------------------------------ Favoritos
@Composable
fun FavoritesScreen(vm: PlayerViewModel, nav: Nav, onMenu: () -> Unit) {
    val favorites by vm.favorites.collectAsState(emptySet())
    val all by vm.allSongs.collectAsState()
    val byId = remember(all) { all.associateBy { it.id } }
    val songs = remember(favorites, all) {
        favorites.mapNotNull { byId[it] }
    }

    StyleScreenBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            StyleHeader(vm, nav, "Favoritos", "${songs.size} canciones", onMenu)
            if (songs.isEmpty()) {
                Text(
                    "Toca el corazón en el reproductor para guardar tus canciones favoritas.",
                    color = Color.White.copy(.6f),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                PlayButtons(vm, songs)
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(songs, key = { _, s -> "fav${s.id}" }) { i, s ->
                        StyleSongRow(vm, s, songs, i, zebra = i % 2 == 0)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ Diálogos
@Composable
fun InfoDialog(vm: PlayerViewModel, s: Song, onDismiss: () -> Unit) {
    var online by remember(s.id) { mutableStateOf<AlbumMeta?>(null) }
    var loading by remember(s.id) { mutableStateOf(true) }
    var tick by remember(s.id) { mutableIntStateOf(0) }
    val q by produceState("", s.id) { value = vm.qualityFor(s) }
    LaunchedEffect(s.id, tick) {
        loading = true
        online = vm.lookupOnline(s, force = tick > 0)
        loading = false
    }

    @Composable fun line(k: String, v: String) {
        if (v.isNotBlank()) Column(Modifier.padding(vertical = 3.dp)) {
            Text(k, fontSize = 10.sp, color = Color(0xFF555555), fontWeight = FontWeight.Bold)
            Text(v, fontSize = 14.sp, color = Color(0xFF111111))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(s.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar", color = ITunesBlue) } },
        dismissButton = { TextButton({ tick++ }) { Text("Buscar en línea", color = ITunesBlue) } },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush = ITunesLCDBrush)
                    .padding(12.dp)
            ) {
                line("Artista", s.artist)
                line("Álbum", s.album)
                line("Año (archivo)", if (s.year > 0) s.year.toString() else "")
                line("Género", s.genre)
                line("Duración", fmt(s.durationMs))
                line("Calidad", q)
                line("Tamaño", String.format(Locale.US, "%.1f MB", s.size / 1048576f))
                line("Carpeta", s.folder)
                line("Archivo", s.fileName)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0x33000000))
                Text(
                    "Base de datos musical (MusicBrainz)",
                    color = Color(0xFF111111),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                when {
                    loading -> Text("Buscando…", fontSize = 13.sp, color = Color(0xFF333333))
                    online == null -> Text("Sin resultados o sin conexión.", fontSize = 13.sp, color = Color(0xFF333333))
                    else -> {
                        line("Título", online!!.title)
                        line("Artista", online!!.artist)
                        line("Fecha de publicación", online!!.year)
                    }
                }
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(vm: PlayerViewModel, s: Song, onDismiss: () -> Unit) {
    val pls by vm.playlists.collectAsState(emptyList())
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Agregar a playlist", color = Color.White) },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar", color = ITunesBlue) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                pls.forEach { p ->
                    TextButton({ vm.addToPlaylist(p.id, s.id); onDismiss() }) {
                        Text(p.name, color = ITunesBlue)
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nueva playlist") },
                    singleLine = true
                )
                TextButton({
                    if (name.isNotBlank()) {
                        vm.createPlaylist(name, s.id)
                        onDismiss()
                    }
                }) {
                    Text("Crear y agregar", color = ITunesBlue)
                }
            }
        }
    )
}

@Composable
fun MoveToPlaylistDialog(vm: PlayerViewModel, s: Song, fromPlaylistId: Long?, onDismiss: () -> Unit) {
    val pls by vm.playlists.collectAsState(emptyList())
    var name by remember { mutableStateOf("") }
    val targets = pls.filter { it.id != fromPlaylistId }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Mover a…", color = Color.White) },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar", color = ITunesBlue) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (targets.isEmpty()) {
                    Text("No hay otras playlists.", color = Color.White.copy(.7f), fontSize = 12.sp)
                } else {
                    targets.forEach { p ->
                        TextButton({ vm.moveSongToPlaylist(fromPlaylistId, p.id, s.id); onDismiss() }) {
                            Text(p.name, color = ITunesBlue)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nueva playlist") },
                    singleLine = true
                )
                TextButton({
                    if (name.isNotBlank()) {
                        vm.moveSongToNewPlaylist(fromPlaylistId, name, s.id)
                        onDismiss()
                    }
                }) {
                    Text("Crear y mover", color = ITunesBlue)
                }
            }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(vm: PlayerViewModel, s: Song, playlistId: Long?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Eliminar", color = Color.White) },
        text = {
            Text(
                if (playlistId != null) "¿Quitar \"${s.title}\" de esta playlist?"
                else "¿Eliminar \"${s.title}\" de todas las playlists? El archivo no se borra.",
                color = Color.White.copy(.85f)
            )
        },
        confirmButton = {
            TextButton({
                if (playlistId != null) vm.removeFromPlaylist(playlistId, s.id) else vm.deleteSong(s.id)
                onDismiss()
            }) { Text("Eliminar", color = Color(0xFFE24A4A)) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar", color = ITunesBlue) } }
    )
}

// ------------------------------------------------------------ Estadísticas
private val CardShape = RoundedCornerShape(6.dp)

@Composable
private fun RankRow(rank: Int, name: String, frac: Float, value: String) {
    val f by animateFloatAsState(frac.coerceIn(.04f, 1f), tween(900), label = "bar")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$rank", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(
                    name, color = Color.White, fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(value, color = Color.White.copy(.6f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(.09f))
            ) {
                Box(
                    Modifier.fillMaxWidth(f).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(ITunesBlue, Color(0xFF2A5FA0))))
                )
            }
        }
    }
}

@Composable
fun BarCard(title: String, icon: ImageVector, data: List<Pair<String, Float>>, valueText: (Float) -> String) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Color(0x66000000))
            .border(1.dp, Color.White.copy(.08f), CardShape)
    ) {
        Row(
            Modifier.fillMaxWidth().background(brush = ITunesColumnBrush)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Column(Modifier.padding(14.dp)) {
            if (data.isEmpty()) {
                Text("Aún no hay datos. ¡Reproduce algo!", color = Color.White.copy(.55f), fontSize = 13.sp)
            }
            val max = data.maxOfOrNull { it.second }?.takeIf { it > 0f } ?: 1f
            data.forEachIndexed { i, pair -> RankRow(i + 1, pair.first, pair.second / max, valueText(pair.second)) }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Column(
        modifier.clip(CardShape).background(Color(0x66000000))
            .border(1.dp, Color.White.copy(.08f), CardShape).padding(14.dp)
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = Color.White.copy(.55f), fontSize = 11.sp)
        Text(
            value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatsPage(vm: PlayerViewModel) {
    val style = LocalStyle.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val brush = when (style) {
            STYLE_VISTA -> VistaGlassBrush
            STYLE_XMB -> Brush.verticalGradient(listOf(Color(0xCC000000), Color(0xCC000000)))
            STYLE_CLASSIC -> Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF000000)))
            else -> ITunesMetalBrush
        }
        Box(
            Modifier.fillMaxWidth().background(brush = brush)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("Tus estadísticas", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Stats(vm)
    }
}

@Composable
fun Stats(vm: PlayerViewModel) {
    val artists by vm.topArtists.collectAsState(emptyList())
    val genres by vm.topGenres.collectAsState(emptyList())
    val songs by vm.topSongs.collectAsState(emptyList())
    val total by vm.totalMs.collectAsState(0L)
    val hours = { v: Float -> String.format(Locale.US, "%.1f h", v) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(CardShape).background(brush = ITunesLCDBrush)
                    .border(1.dp, Color.Black.copy(.7f), CardShape).padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Tiempo de escucha",
                            color = Color(0xFF333333), fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                String.format(Locale.US, "%.1f", total / 3_600_000f),
                                color = Color(0xFF111111), fontSize = 48.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                " h", color = Color(0xFF333333), fontSize = 20.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Headphones, null,
                            tint = Color(0xFF111111), modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Artista top", artists.firstOrNull()?.name ?: "—", Icons.Default.Mic, Modifier.weight(1f))
                MiniStat("Género favorito", genres.firstOrNull()?.name ?: "—", Icons.Default.Category, Modifier.weight(1f))
            }
        }
        item {
            BarCard(
                "Artistas", Icons.Default.Mic,
                artists.map { it.name to it.totalMs / 3_600_000f }, hours
            )
        }
        item {
            BarCard(
                "Géneros", Icons.Default.Category,
                genres.map { it.name to it.totalMs / 3_600_000f }, hours
            )
        }
        item {
            BarCard(
                "Canciones más pedidas", Icons.Default.MusicNote,
                songs.map { "${it.title} — ${it.artist}" to it.plays.toFloat() }
            ) { "${it.toInt()}×" }
        }
    }
}

// ------------------------------------------------------------ Perfil
@Composable
fun Avatar(path: String?, size: Int) {
    val bmp by produceState<Bitmap?>(null, path) {
        value = path?.let { p ->
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(p, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
        }
    }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF2A2A38))
            .border(2.dp, Color.Black.copy(.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        bmp?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } ?: Icon(
            Icons.Default.Person, null,
            tint = Color.White.copy(.6f),
            modifier = Modifier.size((size / 2).dp)
        )
    }
}

@Composable
fun ProfileScreen(vm: PlayerViewModel, first: Boolean) {
    val savedName by vm.profile.name.collectAsState(initial = "")
    val photo by vm.profile.photo.collectAsState(initial = null)
    var text by remember(savedName) { mutableStateOf(savedName) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch { vm.profile.savePhoto(it) } }
    }
    val pick = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(Modifier.fillMaxSize()) {
        if (first) {
            Box(
                Modifier.fillMaxWidth().background(brush = ITunesMetalBrush)
                    .statusBarsPadding().padding(16.dp)
            ) {
                Text("Configurar perfil", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.clickable { pick() }) { Avatar(photo, 140) }
            TextButton({ pick() }) { Text("Cambiar foto", color = ITunesBlue) }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Nombre de usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (text.isNotBlank()) scope.launch { vm.profile.saveName(text) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ITunesBlue)
            ) {
                Text(if (first) "Continuar" else "Guardar")
            }
        }
    }
}