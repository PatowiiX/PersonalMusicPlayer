package com.example.personalmusicplayer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_ALIAS = 24

/**
 * Cuenta de usuario: al tocar tu foto (en cualquier encabezado o en el menú lateral) llegas aquí.
 * Permite cambiar el alias y la imagen, y muestra un resumen de tu actividad.
 */
@Composable
fun AccountScreen(vm: PlayerViewModel, nav: Nav) {
    val style = LocalStyle.current
    val alias by vm.profile.name.collectAsState(initial = "")
    val photo by vm.profile.photo.collectAsState(initial = null)
    val totalMs by vm.totalMs.collectAsState(initial = 0L)
    val lib by vm.library.collectAsState()
    val favs by vm.favorites.collectAsState()
    val pls by vm.playlists.collectAsState(emptyList())
    val statsInDock by vm.statsInDock.collectAsState()

    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.savePhoto(it) }
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val accent = when (style) { STYLE_VISTA -> VistaAccent; STYLE_CLASSIC -> Color.White; else -> ITunesBlue }
    val onAccent = if (style == STYLE_CLASSIC) Color.Black else Color.White
    val shape = when (style) { STYLE_CLASSIC -> RoundedCornerShape(20.dp); STYLE_XMB -> RoundedCornerShape(0.dp); else -> RoundedCornerShape(8.dp) }
    val container = when (style) {
        STYLE_VISTA -> Modifier.background(VistaGlassBrush)
        STYLE_XMB -> Modifier.background(Color.Black.copy(.35f))
        STYLE_ITUNES -> Modifier.background(Color(0xFF141414))
        else -> Modifier.background(classicCardColor())
    }
    val cardMod = Modifier.fillMaxWidth().clip(shape).then(container).border(1.dp, Color.White.copy(.12f), shape)

    fun save() {
        val n = draft.trim().take(MAX_ALIAS)
        if (n.isNotBlank()) { vm.saveAlias(n); editing = false }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Foto ─────────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        Box(Modifier.clip(CircleShape).clickable { pickPhoto() }) {
            Avatar(photo, 132)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).size(38.dp).clip(CircleShape)
                    .background(accent).border(2.dp, Color.Black, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PhotoCamera, "Cambiar foto", tint = onAccent, modifier = Modifier.size(20.dp)) }
        }

        // ── Alias ────────────────────────────────────────────
        if (!editing) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { draft = alias; editing = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    alias.ifBlank { "Sin alias" }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Edit, "Cambiar alias", tint = Color.White.copy(.7f), modifier = Modifier.size(18.dp))
            }
        } else {
            Column(cardMod.padding(16.dp)) {
                Text("Alias", color = Color.White.copy(.65f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= MAX_ALIAS) draft = it },
                    singleLine = true,
                    label = { Text("Nombre de usuario") },
                    supportingText = { Text("${draft.length}/$MAX_ALIAS") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ editing = false }) { Text("Cancelar", color = Color.White.copy(.8f)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { save() }, enabled = draft.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent)
                    ) { Text("Guardar") }
                }
            }
        }

        // ── Acciones de imagen ───────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            Button(
                onClick = pickPhoto,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent)
            ) {
                Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Cambiar imagen")
            }
            if (photo != null) OutlinedButton(
                onClick = { vm.removePhoto() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Quitar")
            }
        }

        // ── Resumen de actividad ─────────────────────────────
        Column(cardMod.padding(vertical = 8.dp)) {
            Text("Tu actividad", color = Color.White.copy(.65f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp))
            AccountStat(Icons.Default.Timer, "Tiempo escuchado", fmtLong(totalMs))
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Color.White.copy(.08f))
            AccountStat(Icons.Default.MusicNote, "Canciones en tu biblioteca", "${lib.size}")
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Color.White.copy(.08f))
            AccountStat(Icons.Default.Album, "Álbumes", "${lib.map { it.albumId }.distinct().size}")
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Color.White.copy(.08f))
            AccountStat(Icons.Default.Favorite, "Favoritos", "${favs.size}")
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Color.White.copy(.08f))
            AccountStat(Icons.Default.QueueMusic, "Playlists", "${pls.size}")
        }

        // ── Estadísticas (dentro de tu cuenta) ───────────────
        Column(cardMod.padding(vertical = 8.dp)) {
            Text("Estadísticas", color = Color.White.copy(.65f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp))
            Row(
                Modifier.fillMaxWidth().clickable { nav.push(Route("stats")) }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BarChart, null, tint = Color.White.copy(.8f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ver mis estadísticas", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Canciones, artistas y géneros más escuchados", color = Color.White.copy(.55f), fontSize = 12.sp)
                }
                Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White.copy(.5f))
            }
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Color.White.copy(.08f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar en la barra inferior", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Junto a Reproduciendo y Biblioteca", color = Color.White.copy(.55f), fontSize = 12.sp)
                }
                Switch(
                    checked = statsInDock, onCheckedChange = { vm.setStatsInDock(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = onAccent)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountStat(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(.8f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White.copy(.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
