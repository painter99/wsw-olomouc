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
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
            spec.intervalMinutes, TimeUnit.MINUTES,
            spec.flexMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        // Round 4 (Pavel 23. 9.): a second periodic, phase-offset by 7 min,
        // halves the effective gap (CHMU caught <= ~10 min after its hourly
        // publication). The repository rate limit (10 min/source, F1.5)
        // dedupes actual fetches — no extra load on the operators' servers.
        val offsetRequest = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
            spec.intervalMinutes, TimeUnit.MINUTES,
            spec.flexMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(SyncSchedule.PHASE_OFFSET_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME_SECOND,
            ExistingPeriodicWorkPolicy.KEEP,
            offsetRequest
        )
    }

    companion object {
        const val UNIQUE_NAME = "wsw-periodic-sync"
        const val UNIQUE_NAME_SECOND = "wsw-periodic-sync-offset"
    }
}
