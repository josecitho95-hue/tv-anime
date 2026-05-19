package com.jkanimetv.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jkanimetv.app.MainActivity
import com.jkanimetv.app.R
import com.jkanimetv.app.data.AppDatabase
import com.jkanimetv.app.data.JKAnimeRepository

// Periodic background check: for each favorite, compare the latest episode
// number against `lastSeenEpisode`. If new episodes are out, post a
// notification and update the watermark. Scheduled from MainActivity as a
// 6h unique periodic work — WorkManager guarantees only one instance.
class EpisodeCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = JKAnimeRepository(ctx)
        val favDao = AppDatabase.getInstance(ctx).favoriteDao()

        val favorites = favDao.getAll()
        if (favorites.isEmpty()) return Result.success()

        ensureChannel(ctx)
        var posted = 0
        for (fav in favorites) {
            // Skip collections the user has explicitly marked as not-following.
            if (fav.status == "COMPLETED" || fav.status == "ON_HOLD") continue
            runCatching {
                val episodes = repo.getEpisodeList(fav.slug)
                if (episodes.isEmpty()) return@runCatching
                val latest = episodes.maxOf { it.number }
                if (latest > fav.lastSeenEpisode) {
                    notifyNewEpisode(ctx, fav.slug, fav.title, latest, fav.lastSeenEpisode)
                    favDao.updateLastSeenEpisode(fav.slug, latest)
                    posted++
                }
            }.onFailure {
                Log.w(TAG, "Check failed for ${fav.slug}", it)
            }
        }
        Log.d(TAG, "EpisodeCheckWorker done — posted=$posted, checked=${favorites.size}")
        return Result.success()
    }

    private fun notifyNewEpisode(
        ctx: Context, slug: String, title: String, latest: Int, previous: Int
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return  // user denied notifications — silent no-op.

        val body = if (previous == 0) "Episodio $latest disponible"
        else if (latest - previous == 1) "Episodio $latest disponible"
        else "Eps ${previous + 1}–$latest disponibles"

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ANIME_SLUG, slug)
        }
        val pi = PendingIntent.getActivity(
            ctx, slug.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(ctx).notify(slug.hashCode(), notif)
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Nuevos episodios",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos cuando un anime favorito tiene episodios nuevos"
            }
        )
    }

    companion object {
        private const val TAG = "EpisodeCheckWorker"
        const val CHANNEL_ID = "new_episodes"
        const val EXTRA_OPEN_ANIME_SLUG = "open_anime_slug"
        const val UNIQUE_NAME = "episode_check"
    }
}
