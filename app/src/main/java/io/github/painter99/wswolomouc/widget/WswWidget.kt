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
import io.github.painter99.wswolomouc.ui.RelativeTimeFormatter

/**
 * Home-screen widget 4×2/5×2 (M1.6a + M1.6b-2, PRD F2.1/F2.2/F2.4/F2.6).
 *
 * Cache-first (no network fetch from the widget — F1.5 rate limit and
 * periodic sync live in M1.7 WorkManager). Layout fills the whole cell area:
 * LEFT half = synthesis (F2.5) + secondary row (feels-like / wind / rain,
 * max 3 items, NF8) + data age; RIGHT half = the other station, compact.
 * 5×2 wide mode additionally shows the right station's humidity
 * (SizeMode.Responsive; sizes chosen far apart so 4×2 stays compact).
 * Background AMOLED black (F2.6); palette per M1.6b-2 contrast rule — every
 * text >= 7:1 vs black, dots >= 4.5:1 (WidgetPaletteTest).
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

        provideContent {
            val wide = LocalSize.current.width >= 400.dp
            val layout = WidgetLayout.build(snapshot, System.currentTimeMillis(), wide)
            WidgetContent(layout, nowMs = System.currentTimeMillis())
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

private fun dotColor(status: WidgetStatus): Long = when (status) {
    WidgetStatus.OK -> WidgetPalette.DOT_OK
    WidgetStatus.STALE -> WidgetPalette.DOT_STALE
    WidgetStatus.OFFLINE -> WidgetPalette.DOT_OFFLINE
}

private fun textStyle(argb: Long, size: Int, bold: Boolean = false) = TextStyle(
    color = ColorProvider(Color(argb)),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
)

@Composable
fun WidgetContent(layout: WidgetLayoutState, nowMs: Long) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.Black))
            .padding(12.dp),
        horizontalAlignment = Alignment.Start,
        verticalAlignment = Alignment.Top
    ) {
        // LEFT half: synthesis + secondary row + age (F2.5/F2.6/NF8).
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "●",
                style = textStyle(dotColor(layout.left.status), 14, bold = false)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = Format.temperature(layout.left.temperatureC),
                style = textStyle(WidgetPalette.TEXT_PRIMARY, 44, bold = true)
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = layout.left.badge,
                style = textStyle(WidgetPalette.TEXT_SECONDARY, 14, bold = false)
            )
            if (layout.secondary.isNotEmpty()) {
                Text(
                    text = layout.secondary.joinToString(" · ") { "${it.label} ${it.text}" },
                    style = textStyle(WidgetPalette.TEXT_TERTIARY, 14, bold = false)
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = layout.left.measuredAtMs?.let {
                    "Měření " + RelativeTimeFormatter.format(it, nowMs)
                } ?: "Bez dat",
                style = textStyle(WidgetPalette.TEXT_SECONDARY, 14, bold = false)
            )
        }
        Spacer(modifier = GlanceModifier.width(12.dp))
        // RIGHT half: the other station, compact (Pavel's proposal d, 21. 9.).
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "●",
                style = textStyle(
                    when {
                        layout.right.hasData && !layout.right.isStale -> WidgetPalette.DOT_OK
                        layout.right.isStale -> WidgetPalette.DOT_STALE
                        else -> WidgetPalette.DOT_OFFLINE
                    },
                    14, bold = false
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = layout.right.name,
                style = textStyle(WidgetPalette.TEXT_SECONDARY, 14, bold = false)
            )
            Text(
                text = Format.temperature(layout.right.temperatureC),
                style = textStyle(WidgetPalette.TEXT_PRIMARY, 24, bold = true)
            )
            if (layout.showRightHumidity && layout.right.humidityPct != null) {
                Text(
                    text = Format.humidity(layout.right.humidityPct),
                    style = textStyle(WidgetPalette.TEXT_TERTIARY, 14, bold = false)
                )
            }
            Text(
                text = layout.right.measuredAtMs?.let {
                    "Měření " + RelativeTimeFormatter.format(it, nowMs)
                } ?: "Bez dat",
                style = textStyle(WidgetPalette.TEXT_TERTIARY, 14, bold = false)
            )
        }
    }
}