package io.github.painter99.wswolomouc

import android.app.Application
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.painter99.wswolomouc.sync.SyncSchedule
import io.github.painter99.wswolomouc.sync.SyncScheduler

/**
 * Application entry — schedules the periodic background sync (M1.7, G5).
 * Idempotent: ExistingPeriodicWorkPolicy.KEEP, so repeated app starts do not
 * duplicate the job.
 */
@HiltAndroidApp
class WswApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val scheduler = EntryPointAccessors
            .fromApplication(this, SyncEntryPoint::class.java)
            .syncScheduler()
        scheduler.schedule(SyncSchedule.spec())
    }
}

/** Hilt entry point for non-injectable components (same pattern as the widget). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun syncScheduler(): SyncScheduler
}
