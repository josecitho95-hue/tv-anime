package com.jkanimetv.app.data

import android.content.Context
import androidx.room.*

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
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    suspend fun getAll(): List<Favorite>

    @Query("SELECT COUNT(*) FROM favorites WHERE slug = :slug")
    suspend fun isFavorite(slug: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fav: Favorite)

    @Query("DELETE FROM favorites WHERE slug = :slug")
    suspend fun delete(slug: String)
}

@Database(entities = [WatchHistory::class, Favorite::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jkanime_db"
                ).build().also { INSTANCE = it }
            }
    }
}
