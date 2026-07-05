package io.github.krausstt.openbrowsertabs.enrich

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.krausstt.openbrowsertabs.MainActivity
import io.github.krausstt.openbrowsertabs.R
import io.github.krausstt.openbrowsertabs.core.Enrichment
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.data.LinkStore
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Drains the pending-enrichment queue: fetch, extract, persist, notify.
 * Human pacing between requests; batches are capped so a single run stays
 * well under WorkManager's 10-minute budget — leftover work re-enqueues
 * itself.
 */
class EnrichmentWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = LinkStore(applicationContext)
        val notifyLinkId = inputData.getLong(KEY_LINK_ID, -1L)
        val batch = if (notifyLinkId > 0) {
            listOfNotNull(store.byId(notifyLinkId)).filter { it.enrichmentState == "pending" }
        } else {
            store.pendingEnrichment(BATCH_SIZE)
        }

        var sawTransient = false
        batch.forEachIndexed { index, link ->
            if (index > 0) delay(Random.nextLong(2_000, 4_500))
            sawTransient = enrichOne(store, link, notify = link.id == notifyLinkId) || sawTransient
        }

        // keep draining bulk imports in follow-up runs
        if (notifyLinkId <= 0 && store.pendingEnrichmentCount() > 0 && !sawTransient) {
            enqueueDrain(applicationContext)
        }
        return if (sawTransient) Result.retry() else Result.success()
    }

    /** @return true if the outcome was transient (leave pending, retry run) */
    private fun enrichOne(store: LinkStore, link: LinkEntity, notify: Boolean): Boolean {
        // search queries have no fetchable article — label already says it all
        if (link.category == "search_query") {
            store.applyEnrichment(link.id, state = "done", title = link.label)
            return false
        }
        return when (val outcome = Enrichment.enrich(link.canonicalUrl)) {
            is Enrichment.Outcome.Enriched -> {
                val a = outcome.article
                store.applyEnrichment(
                    link.id, state = "done",
                    title = a.title, description = a.description, content = a.text,
                    siteName = a.siteName, publishedAt = a.publishedAt,
                )
                if (notify) {
                    postNotification(
                        link.id,
                        title = a.title ?: link.host,
                        text = a.description ?: "Gespeichert & angereichert · ${link.host}",
                    )
                }
                false
            }
            is Enrichment.Outcome.Unfetchable -> {
                store.applyEnrichment(link.id, state = "unfetchable")
                if (notify) {
                    postNotification(
                        link.id,
                        title = link.title ?: link.host,
                        text = "Gespeichert — Seite nicht abrufbar (${outcome.reason}), " +
                            "Anreicherung folgt später",
                    )
                }
                false // terminal state, no retry
            }
            is Enrichment.Outcome.Transient -> true
        }
    }

    private fun postNotification(linkId: Long, title: String, text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(ctx)
        val tap = PendingIntent.getActivity(
            ctx, linkId.toInt(),
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(linkId.toInt(), notification)
    }

    companion object {
        private const val KEY_LINK_ID = "link_id"
        private const val BATCH_SIZE = 15
        private const val CHANNEL_ID = "enrichment"
        private const val DRAIN_WORK = "enrichment-drain"

        /** Enrich one just-shared link ASAP and notify about the result. */
        fun enqueueForLink(context: Context, linkId: Long) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<EnrichmentWorker>()
                    .setInputData(workDataOf(KEY_LINK_ID to linkId))
                    .build(),
            )
        }

        /** Drain the whole pending queue (bulk imports); chains itself. */
        fun enqueueDrain(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                DRAIN_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<EnrichmentWorker>().build(),
            )
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Anreicherung",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Ergebnis der Link-Anreicherung nach dem Teilen"
                },
            )
        }
    }
}
