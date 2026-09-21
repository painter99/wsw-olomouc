package io.github.painter99.wswolomouc.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.painter99.wswolomouc.MainActivity
import io.github.painter99.wswolomouc.data.WeatherRepository
import io.github.painter99.wswolomouc.ui.Format
import io.github.painter99.wswolomouc.ui.RelativeTimeFormatter

/**
 * Home-screen widget 4×2 (M1.6a, PRD F2.1/F2.2/F2.4 + synthesis F2.5).
 *
 * M1.6a scope: cache-first (no network fetch from the widget — F1.5 rate
 * limit and periodic sync arrive with M1.7 WorkManager). The widget is
 * refreshed when the app itself refreshes (MainActivity hook) and on system
 * update requests. Background is AMOLED black per F2.6 default; typography
 * per NF8 (temperature 44sp, secondary row >= 14sp).
 */
class WswWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .weatherRepository()

        // Cache-first read only — no network from the widget in M1.6a.
        val snapshot = repository.latestFromCache()
        val state = WidgetSynthesis.synthesize(snapshot, System.currentTimeMillis())

        provideContent {
            WidgetContent(state, nowMs = System.currentTimeMillis())
        }
    }
}

/** Hilt entry point — Glance widgets are not injectable components. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
}

/** Standard receiver wiring declared in AndroidManifest.xml. */
class WswWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WswWidget()
}

private val DotGreen = Color(0xFF2E7D32)
private val DotOrange = Color(0xFFEF6C00)
private val DotGray = Color(0xFF9E9E9E)

@Composable
fun WidgetContent(state: WidgetState, nowMs: Long) {
    val dotColor = when (state.status) {
        WidgetStatus.OK -> DotGreen
        WidgetStatus.STALE -> DotOrange
        WidgetStatus.OFFLINE -> DotGray
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.Black))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = "●",
                style = TextStyle(color = ColorProvider(dotColor), fontSize = 14.sp)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = Format.temperature(state.temperatureC),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = state.badge,
                style = TextStyle(color = ColorProvider(Color(0xFFBDBDBD)), fontSize = 14.sp)
            )
            Text(
                text = state.measuredAtMs?.let {
                    "Měření " + RelativeTimeFormatter.format(it, nowMs)
                } ?: "Bez dat",
                style = TextStyle(color = ColorProvider(Color(0xFFBDBDBD)), fontSize = 14.sp)
            )
        }
    }
}
