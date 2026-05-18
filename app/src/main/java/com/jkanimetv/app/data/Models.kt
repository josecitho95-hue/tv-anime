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

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val slug: String,
    val title: String,
    val coverUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)
