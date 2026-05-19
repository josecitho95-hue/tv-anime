package com.jkanimetv.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class Anime(
    val slug: String,
    val title: String,
    val coverUrl: String,
    val synopsis: String = "",
    val genre: String = "",
    val status: String = "",
    val type: String = "",
    val rating: String = "",
    val year: String = ""
)

data class Episode(
    val animeSlug: String,
    val number: Int,
    val title: String = "",
    val thumbnailUrl: String = ""
)

data class VideoServer(
    val name: String,
    val embedUrl: String
)

data class VideoLink(
    val serverName: String,
    val url: String,
    val isHls: Boolean = false
)

data class Schedule(
    val dayName: String,
    val animes: List<Anime>
)

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey val key: String,
    val animeSlug: String,
    val animeTitle: String,
    val animeCover: String,
    val episodeNumber: Int,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

// Favorite status — string-backed enum so Room can persist it without a
// TypeConverter and the migration is a plain ADD COLUMN with a default.
object FavoriteStatus {
    const val WATCHING = "WATCHING"
    const val COMPLETED = "COMPLETED"
    const val ON_HOLD = "ON_HOLD"
    const val PLAN = "PLAN"

    val ALL = listOf(WATCHING, COMPLETED, ON_HOLD, PLAN)

    fun label(value: String): String = when (value) {
        WATCHING -> "Viendo"
        COMPLETED -> "Completado"
        ON_HOLD -> "En pausa"
        PLAN -> "Pendiente"
        else -> value
    }
}

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val slug: String,
    val title: String,
    val coverUrl: String,
    val timestamp: Long = System.currentTimeMillis(),
    // C2: highest episode number we've ever seen for this favorite. Compared
    // against fresh /ajax/episodes/ counts to surface "new episode" notifications.
    val lastSeenEpisode: Int = 0,
    // D1: user-chosen collection. Defaults to WATCHING so existing rows after
    // the v1→v2 migration land in the default tab.
    val status: String = FavoriteStatus.WATCHING
)

// C3: recent searches dropdown. Query is the PK so re-issuing the same search
// just bumps the timestamp instead of duplicating.
@Entity(tableName = "search_history")
data class SearchHistoryItem(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
