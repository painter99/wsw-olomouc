package io.github.painter99.wswolomouc.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Idempotent scheduler for the periodic sync (M1.7).
 *
 * Called from [io.github.painter99.wswolomouc.WswApplication.onCreate]; KEEP
 * policy means repeated app starts never duplicate the periodic job.
 * Constraints: network only — no battery/idle requirements (Dozi WorkManager
 * resists on its own; the widget shows data age transparently, R5).
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun schedule(spec: SyncSchedule.Spec) {
        val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
            spec.intervalMinutes, TimeUnit.MINUTES,
            spec.flexMinutes, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val UNIQUE_NAME = "wsw-periodic-sync"
    }
}
