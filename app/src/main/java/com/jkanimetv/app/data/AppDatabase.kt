package com.jkanimetv.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT 50")
    suspend fun getAll(): List<WatchHistory>

    @Query("SELECT * FROM watch_history WHERE key = :key")
    suspend fun get(key: String): WatchHistory?

    @Query("SELECT * FROM watch_history WHERE animeSlug = :slug")
    suspend fun getForAnime(slug: String): List<WatchHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchHistory)

    @Query("DELETE FROM watch_history WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    suspend fun getAll(): List<Favorite>

    @Query("SELECT * FROM favorites WHERE slug = :slug")
    suspend fun get(slug: String): Favorite?

    @Query("SELECT COUNT(*) FROM favorites WHERE slug = :slug")
    suspend fun isFavorite(slug: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fav: Favorite)

    @Query("DELETE FROM favorites WHERE slug = :slug")
    suspend fun delete(slug: String)

    @Query("UPDATE favorites SET status = :status WHERE slug = :slug")
    suspend fun updateStatus(slug: String, status: String)

    @Query("UPDATE favorites SET lastSeenEpisode = :ep WHERE slug = :slug")
    suspend fun updateLastSeenEpisode(slug: String, ep: Int)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 10")
    suspend fun recent(): List<SearchHistoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SearchHistoryItem)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

// v1 → v2: additive only.
//   - favorites.lastSeenEpisode (INTEGER NOT NULL DEFAULT 0)
//   - favorites.status (TEXT NOT NULL DEFAULT 'WATCHING')
//   - search_history table
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorites ADD COLUMN lastSeenEpisode INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE favorites ADD COLUMN status TEXT NOT NULL DEFAULT 'WATCHING'")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS search_history (
                query TEXT NOT NULL PRIMARY KEY,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

@Database(
    entities = [WatchHistory::class, Favorite::class, SearchHistoryItem::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jkanime_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
