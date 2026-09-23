package io.github.painter99.wswolomouc.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
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
import io.github.painter99.wswolomouc.ui.Trend
import io.github.painter99.wswolomouc.ui.TrendDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget 4×2/5×2 (M1.6a + M1.6b-2 v2, PRD F2.1/F2.2/F2.4/F2.6).
 *
 * Cache-first (no network fetch from the widget — periodic sync lives in
 * M1.7 WorkManager, so data are at most ~15 min old when infopocasi works).
 *
 * Layout v2 (Pavel 22. 9.):
 *  - LEFT = synthesis value (F2.5 fallback — always some value) + secondary
 *    row (feels-like / wind / rain, precise temperatures) + age.
 *  - RIGHT = BOTH stations, each with its own last-measurement time.
 *  - ALL text pure white (no gray on black — sunlight legibility); only the
 *    status dots are colored.
 *  - 5×2 wide mode adds per-station humidity (SizeMode.Responsive; sizes far
 *    apart so 4×2 stays compact).
 */
class WswWidget : GlanceAppWidget() {

    // Compact 4×2 and wide 5×2 (F2.6). The two defined sizes are far apart
    // so the "closest size" selection keeps 4×2 compact and 5×2 wide.
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(240.dp, 110.dp), DpSize(400.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .weatherRepository()

        // Cache-first read only — no network from the widget (M1.6a).
        val snapshot = repository.latestFromCache()
        val nowMs = System.currentTimeMillis()

        // ONE trend arrow (3 h window) for the station behind the badge
        // (Pavel 23. 9.). Computed here because it needs Room history; the
        // layout itself stays a pure function.
        val synth = WidgetSynthesis.synthesize(snapshot, nowMs)
        val trend = synth.sourceStation?.let {
            Trend.compute(
                synth.temperatureC,
                repository.pastTemperature(it, nowMs - Trend.WIDGET_WINDOW_MS)
            )
        }

        provideContent {
            val wide = LocalSize.current.width >= 400.dp
            val layout = WidgetLayout.build(snapshot, nowMs, wide, trend)
            WidgetContent(layout, nowMs = nowMs)
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

private fun dotColor(hasData: Boolean, isStale: Boolean): Long = when {
    hasData && !isStale -> WidgetPalette.DOT_OK
    isStale -> WidgetPalette.DOT_STALE
    else -> WidgetPalette.DOT_OFFLINE
}

private fun textStyle(size: Int, bold: Boolean = false) = TextStyle(
    color = ColorProvider(Color(WidgetPalette.TEXT_PRIMARY)),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
)

private fun dotStyle(color: Long) = TextStyle(
    color = ColorProvider(Color(color)),
    fontSize = 12.sp
)

/**
 * Absolute clock time (round 2, Pavel 23. 9.): the widget does NOT re-render
 * between updates, so a relative countdown ("před X min") would freeze and
 * lie — show the clock time of the last update instead.
 */
private fun clockTime(measuredAtMs: Long?): String =
    measuredAtMs
        ?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) }
        ?: "bez dat"

@Composable
fun WidgetContent(layout: WidgetLayoutState, nowMs: Long) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.Black))
            .padding(10.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity<MainActivity>()),
            horizontalAlignment = Alignment.Start,
            verticalAlignment = Alignment.Top
        ) {
            // LEFT half: synthesis + secondary quantities (one per line) + time.
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "●",
                    style = dotStyle(
                        dotColor(layout.left.temperatureC != null, layout.left.status == WidgetStatus.STALE)
                    )
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = Format.temperaturePrecise(layout.left.temperatureC) +
                        Trend.arrow(layout.left.trend),
                    style = textStyle(40, bold = true)
                )
                Text(
                    text = layout.left.badge,
                    style = textStyle(14)
                )
                for (item in layout.secondary) {
                    Text(
                        text = "${item.label} ${item.text}",
                        style = textStyle(14)
                    )
                }
                Text(
                    text = layout.left.measuredAtMs?.let { "Měření " + clockTime(it) } ?: "Bez dat",
                    style = textStyle(14)
                )
            }
            Spacer(modifier = GlanceModifier.width(10.dp))
            // RIGHT half: BOTH stations, each with its own last-update time.
            Column(horizontalAlignment = Alignment.End) {
                for ((index, station) in layout.stations.withIndex()) {
                    if (index > 0) Spacer(modifier = GlanceModifier.height(8.dp))
                    Row {
                        Text(
                            text = "●",
                            style = dotStyle(dotColor(station.hasData, station.isStale))
                        )
                        Text(
                            text = " " + station.name,
                            style = textStyle(14)
                        )
                    }
                    Text(
                        text = Format.temperaturePrecise(station.temperatureC),
                        style = textStyle(20, bold = true)
                    )
                    Text(
                        text = clockTime(station.measuredAtMs),
                        style = textStyle(14)
                    )
                    if (layout.showStationHumidity && station.humidityPct != null) {
                        Text(
                            text = Format.humidity(station.humidityPct),
                            style = textStyle(14)
                        )
                    }
                }
            }
        }
        // Manual refresh, bottom right (round 2, Pavel 23. 9.). The 10-min
        // per-source rate limit (F1.5) is enforced inside the repository —
        // within the limit this just re-renders the cache.
        Text(
            text = "⟳",
            style = textStyle(18),
            modifier = GlanceModifier
                .padding(4.dp)
                .clickable(actionRunCallback<RefreshWidgetAction>())
        )
    }
}