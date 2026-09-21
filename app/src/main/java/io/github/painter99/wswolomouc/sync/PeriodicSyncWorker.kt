package io.github.painter99.wswolomouc.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import io.github.painter99.wswolomouc.data.WeatherRepository
import io.github.painter99.wswolomouc.widget.WswWidget
import io.github.painter99.wswolomouc.widget.WidgetEntryPoint

/**
 * Periodic background sync (M1.7, PRD F2.3/G5/F1.5).
 *
 * Fetches both stations through the repository (the 10 min per-source rate
 * limit in [WeatherRepository] guards the operators' servers, F1.5) and then
 * updates the widget — the widget is refreshed from WorkManager, not from a
 * UI process (F2.3).
 *
 * Failures return success: periodic work repeats anyway, and the widget
 * shows data age transparently (R5 mitigation, G6). A retry result would
 * only add pressure on sources that are already failing.
 */
class PeriodicSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, WidgetEntryPoint::class.java)
            .weatherRepository()

        return try {
            repository.refresh()
            WswWidget().updateAll(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Result.success()
        }
    }
}
