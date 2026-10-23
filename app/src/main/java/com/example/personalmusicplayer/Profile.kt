package com.example.personalmusicplayer

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.ds by preferencesDataStore("profile")

class ProfileStore(private val ctx: Context) {
    private val NAME = stringPreferencesKey("name")
    private val PHOTO = stringPreferencesKey("photo")
    private val EXC = stringSetPreferencesKey("excluded_folders")
    private val STYLE = stringPreferencesKey("ui_style")
    private val SHUF = booleanPreferencesKey("shuffle")
    private val VINYL = intPreferencesKey("vinyl_mode")
    private val LAST_TYPE = stringPreferencesKey("last_route_type")
    private val LAST_VALUE = stringPreferencesKey("last_route_value")
    private val LAST_SONG = longPreferencesKey("last_song_id")
    private val LAST_POS = longPreferencesKey("last_position_ms")
    private val FAV = stringSetPreferencesKey("favorites")

    val name: Flow<String> = ctx.ds.data.map { it[NAME] ?: "" }
    val photo: Flow<String?> = ctx.ds.data.map { it[PHOTO] }
    val excluded: Flow<Set<String>> = ctx.ds.data.map { it[EXC] ?: emptySet() }
    val vinylMode: Flow<Int> = ctx.ds.data.map { it[VINYL] ?: 0 }
    val style: Flow<String> = ctx.ds.data.map { it[STYLE] ?: "classic" }
    val shuffle: Flow<Boolean> = ctx.ds.data.map { it[SHUF] ?: false }
    val lastRoute: Flow<Pair<String, String>?> = ctx.ds.data.map { p ->
        val t = p[LAST_TYPE] ?: return@map null
        t to (p[LAST_VALUE] ?: "")
    }
    val lastSongId: Flow<Long> = ctx.ds.data.map { it[LAST_SONG] ?: -1L }
    val lastPosition: Flow<Long> = ctx.ds.data.map { it[LAST_POS] ?: 0L }
    val favorites: Flow<Set<Long>> = ctx.ds.data.map { p -> (p[FAV] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet() }

    suspend fun saveName(n: String) { ctx.ds.edit { it[NAME] = n.trim() } }
    suspend fun saveVinyl(i: Int) { ctx.ds.edit { it[VINYL] = i } }
    suspend fun saveStyle(s: String) { ctx.ds.edit { it[STYLE] = s } }
    suspend fun saveShuffle(b: Boolean) { ctx.ds.edit { it[SHUF] = b } }
    suspend fun saveLastRoute(type: String, value: String) {
        ctx.ds.edit { it[LAST_TYPE] = type; it[LAST_VALUE] = value }
    }
    suspend fun saveLastSong(id: Long, posMs: Long) {
        ctx.ds.edit { it[LAST_SONG] = id; it[LAST_POS] = posMs }
    }

    suspend fun toggleFavorite(id: Long) {
        ctx.ds.edit {
            val cur = (it[FAV] ?: emptySet()).toMutableSet()
            if (!cur.remove(id.toString())) cur.add(id.toString())
            it[FAV] = cur
        }
    }

    suspend fun toggleFolder(f: String) {
        ctx.ds.edit {
            val cur = (it[EXC] ?: emptySet()).toMutableSet()
            if (!cur.remove(f)) cur.add(f)
            it[EXC] = cur
        }
    }

    suspend fun savePhoto(uri: Uri) {
        val f = File(ctx.filesDir, "profile_${System.currentTimeMillis()}.jpg")
        withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
        ctx.ds.edit { it[PHOTO] = f.absolutePath }
    }
}