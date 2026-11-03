package it.jarvis.discover.data.creators

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.jarvis.discover.R
import it.jarvis.discover.data.backup.SecretNames
import it.jarvis.discover.data.youtube.ApiResult
import it.jarvis.discover.data.youtube.SecureStore
import it.jarvis.discover.data.youtube.YouTubeClient
import it.jarvis.discover.data.youtube.YtErrorKind
import java.util.concurrent.TimeUnit

/**
 * Notifiche facoltative per i nuovi video dei creator seguiti. Ogni 6 ore (circa: Android decide
 * l'orario esatto) il lavoro in background guarda i caricamenti dei creator con la campanella attiva
 * e, se c'è un video più recente dell'ultimo già visto, manda UNA notifica per creator.
 * Il primo controllo di un creator non notifica: segna solo il punto di partenza.
 */
object CreatorUpdates {
    private const val WORK_NAME = "creator_updates"
    const val CHANNEL_ID = "creator_updates"

    fun schedule(context: Context, enabled: Boolean) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<CreatorUpdatesWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nuovi video dei creator", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Un avviso quando un creator che segui pubblica qualcosa di nuovo."
                },
            )
        }
    }
}

class CreatorUpdatesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(CreatorStore.PREFS, Context.MODE_PRIVATE)
        val watched = CreatorParser.followsFromJson(prefs.getString(CreatorStore.K_FOLLOWS, null)).filter { it.notify }
        if (watched.isEmpty()) return Result.success()
        val key = SecureStore(ctx).get(SecretNames.YOUTUBE) ?: return Result.success()

        val client = CreatorClient(YouTubeClient())
        val seen = JsonSeen.read(prefs.getString(CreatorStore.K_SEEN, null)).toMutableMap()
        var changed = false
        for (f in watched) {
            val page = when (val r = client.latestUploads(key, f.creator, max = 5)) {
                is ApiResult.Ok -> r.value
                is ApiResult.Err -> { if (r.error.isKeyProblem || r.error.kind == YtErrorKind.QuotaExceeded) break else continue }
            }
            val newest = page.publishedAt.values.maxOrNull() ?: continue
            val previous = seen[f.creator.id]
            if (previous == null) {
                seen[f.creator.id] = newest // primo controllo: nessuna notifica
                changed = true
            } else if (newest > previous) {
                seen[f.creator.id] = newest
                changed = true
                notifyNew(ctx, f.creator)
            }
        }
        if (changed) prefs.edit().putString(CreatorStore.K_SEEN, JsonSeen.write(seen)).apply()
        return Result.success()
    }

    private fun notifyNew(ctx: Context, creator: Creator) {
        if (!CreatorUpdates.canNotify(ctx)) return
        CreatorUpdates.ensureChannel(ctx)
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        val pending = PendingIntent.getActivity(ctx, creator.id.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(ctx, CreatorUpdates.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Novità da ${creator.title}")
            .setContentText("Ha pubblicato un nuovo video. Aprilo da Discover.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(creator.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // permesso revocato nel frattempo: nessuna notifica
        }
    }
}
