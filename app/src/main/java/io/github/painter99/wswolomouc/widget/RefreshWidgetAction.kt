package io.github.painter99.wswolomouc.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.EntryPointAccessors

/**
 * Manual widget refresh (round 2, Pavel 23. 9. 2026): runs the repository
 * refresh — the 10-min per-source rate limit (F1.5) is enforced inside, so
 * within the limit this just re-renders the cache — and updates the widget.
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .weatherRepository()
            .refresh()
        WswWidget().updateAll(context)
    }
}