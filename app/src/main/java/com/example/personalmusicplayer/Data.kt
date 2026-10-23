package com.example.personalmusicplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class Song(
    val id: Long, val title: String, val artist: String, val album: String, val albumId: Long, val genre: String,
    val year: Int, val track: Int, val folder: String, val fileName: String, val durationMs: Long, val size: Long
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

object MediaRepo {
    fun loadSongs(ctx: Context): List<Song> {
        val list = mutableListOf<Song>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        runCatching {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                proj,
                sel,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
            )?.use { c ->
                while (c.moveToNext()) {
                    fun s(i: Int, d: String) = c.getString(i)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: d
                    list += Song(
                        c.getLong(0), s(1, "Sin título"), s(2, "Artista desconocido"), s(3, "Álbum desconocido"), c.getLong(4),
                        s(5, "Sin género"), c.getInt(6), c.getInt(7), s(8, "Otros"), s(9, ""), c.getLong(10), c.getLong(11)
                    )
                }
            }
        }
        return list
    }
}

@Entity data class Stat(
    @PrimaryKey val songId: Long, val title: String, val artist: String,
    val genre: String, val plays: Int = 0, val totalMs: Long = 0
)
data class Agg(val name: String, val plays: Int, val totalMs: Long)

@Entity data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(primaryKeys = ["playlistId", "songId"])
data class PlaylistSong(val playlistId: Long, val songId: Long, val addedAt: Long = System.currentTimeMillis())

@Entity data class AlbumMeta(@PrimaryKey val metaKey: String, val title: String, val artist: String, val year: String)

@Entity data class Favorite(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis()
)

@Dao
interface AppDao {
    // estadísticas
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun ensure(s: Stat)
    @Query("UPDATE Stat SET plays = plays + 1 WHERE songId = :id") suspend fun addPlay(id: Long)
    @Query("UPDATE Stat SET totalMs = totalMs + :ms WHERE songId = :id") suspend fun addTime(id: Long, ms: Long)
    @Query("SELECT * FROM Stat WHERE plays > 0 ORDER BY plays DESC, totalMs DESC LIMIT 8") fun topSongs(): Flow<List<Stat>>
    @Query("SELECT artist AS name, SUM(plays) AS plays, SUM(totalMs) AS totalMs FROM Stat GROUP BY artist HAVING SUM(totalMs) > 0 ORDER BY totalMs DESC LIMIT 8")
    fun topArtists(): Flow<List<Agg>>
    @Query("SELECT genre AS name, SUM(plays) AS plays, SUM(totalMs) AS totalMs FROM Stat GROUP BY genre HAVING SUM(totalMs) > 0 ORDER BY totalMs DESC LIMIT 8")
    fun topGenres(): Flow<List<Agg>>
    @Query("SELECT COALESCE(SUM(totalMs), 0) FROM Stat") fun totalMs(): Flow<Long>

    // playlists
    @Insert suspend fun createPlaylist(p: Playlist): Long
    @Query("SELECT * FROM Playlist ORDER BY name COLLATE NOCASE") fun playlists(): Flow<List<Playlist>>
    @Query("DELETE FROM Playlist WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM PlaylistSong WHERE playlistId = :id") suspend fun clearPlaylist(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addToPlaylist(ps: PlaylistSong)
    @Query("DELETE FROM PlaylistSong WHERE playlistId = :pid AND songId = :sid") suspend fun removeFromPlaylist(pid: Long, sid: Long)
    @Query("SELECT * FROM PlaylistSong ORDER BY addedAt") fun entries(): Flow<List<PlaylistSong>>
    @Query("DELETE FROM PlaylistSong WHERE songId = :sid") suspend fun removeSongFromAllPlaylists(sid: Long)

    @Query("SELECT * FROM PlaylistSong WHERE playlistId = :pid ORDER BY addedAt")
    suspend fun entriesFor(pid: Long): List<PlaylistSong>
    @Query("DELETE FROM PlaylistSong WHERE playlistId = :pid")
    suspend fun clearOne(pid: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlaylistSong>)

    // base de datos musical
    @Query("SELECT * FROM AlbumMeta WHERE metaKey = :k") suspend fun meta(k: String): AlbumMeta?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveMeta(m: AlbumMeta)

    // Favoritos
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(f: Favorite)
    @Query("DELETE FROM Favorite WHERE songId = :sid") suspend fun removeFavorite(sid: Long)
    @Query("SELECT * FROM Favorite ORDER BY addedAt DESC") fun favorites(): Flow<List<Favorite>>
    @Query("SELECT EXISTS(SELECT 1 FROM Favorite WHERE songId = :sid)") fun isFavorite(sid: Long): Flow<Boolean>

    // Historial
    @Insert suspend fun logPlay(e: PlayEvent)
    @Query("SELECT * FROM PlayEvent ORDER BY playedAt DESC LIMIT 200") fun history(): Flow<List<PlayEvent>>
    @Query("DELETE FROM PlayEvent WHERE playedAt < :before") suspend fun trimHistory(before: Long)
}

@Database(
    entities = [
        Stat::class, Playlist::class, PlaylistSong::class, AlbumMeta::class,
        Favorite::class, PlayEvent::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "pmp.db")
                .fallbackToDestructiveMigration()
                .build().also { inst = it }
        }
    }
}