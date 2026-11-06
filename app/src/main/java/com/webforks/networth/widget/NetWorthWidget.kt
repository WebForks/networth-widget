package com.webforks.networth.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.webforks.networth.MainActivity
import com.webforks.networth.container
import com.webforks.networth.data.NetWorthRepository
import com.webforks.networth.data.Settings
import com.webforks.networth.domain.Format
import com.webforks.networth.domain.History
import com.webforks.networth.domain.Period
import com.webforks.networth.domain.PeriodChange
import com.webforks.networth.sync.RefreshScheduler
import com.webforks.networth.ui.theme.TrendColors
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** Everything the widget draws, derived from the database. */
data class WidgetData(
    val hasAccounts: Boolean,
    val total: Double,
    val day: PeriodChange?,
    val trend: PeriodChange?,
    val extra: List<PeriodChange>,
    val settings: Settings,
    val needsAttention: Int,
)

class NetWorthWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        // Room flows re-emit whenever a refresh writes new balances, so the widget redraws
        // on its own while its session is alive; updateAll() covers the rest.
        val data = combine(
            c.repository.accounts,
            c.repository.balanceSnapshots,
            c.repository.connections,
            c.settings.settings,
        ) { accounts, snapshots, connections, settings ->
            val included = accounts.filter { it.includeInNetWorth }.map { it.accountId }.toSet()
            val series = NetWorthRepository.seriesFor(snapshots, included)
            val today = LocalDate.now()
            WidgetData(
                hasAccounts = accounts.isNotEmpty(),
                total = series.lastOrNull()?.value ?: 0.0,
                day = History.dayChange(series),
                trend = History.change(series, settings.widgetPeriod, today),
                extra = listOf(Period.M1, Period.YTD, Period.Y1).mapNotNull { History.change(series, it, today) },
                settings = settings,
                needsAttention = connections.count { it.status != "OK" },
            )
        }
        provideContent {
            val state by remember { data }.collectAsState(initial = null)
            GlanceTheme {
                WidgetContent(state)
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 50.dp)
        val MEDIUM = DpSize(180.dp, 110.dp)
        val LARGE = DpSize(250.dp, 180.dp)

        suspend fun updateAll(context: Context) = NetWorthWidget().updateAll(context)
    }
}

class NetWorthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetWorthWidget()
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshScheduler.refreshNow(context)
    }
}

private val upColor = ColorProvider(day = TrendColors.UpLight, night = TrendColors.UpDark)
private val downColor = ColorProvider(day = TrendColors.DownLight, night = TrendColors.DownDark)

@Composable
private fun WidgetContent(data: WidgetData?) {
    val size = LocalSize.current
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        when {
            data == null -> Unit
            !data.hasAccounts -> Column {
                Label("Net worth")
                Text(
                    "Tap to link accounts",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                )
            }
            else -> Column(modifier = GlanceModifier.fillMaxSize()) {
                val hide = data.settings.hideAmountsInWidget
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(if (data.needsAttention > 0) "Net worth  ⚠" else "Net worth")
                    Spacer(GlanceModifier.defaultWeight())
                    if (size.height >= NetWorthWidget.LARGE.height) {
                        Text(
                            "↻ " + Format.ago(data.settings.lastRefreshAt),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                            modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                        )
                    }
                }
                Text(
                    if (hide) Format.HIDDEN else Format.money(data.total, whole = size.width < NetWorthWidget.MEDIUM.width),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = if (size.height < NetWorthWidget.MEDIUM.height) 20.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                data.day?.let { ChangeLine(it, hide, prefix = "Today") }

                if (size.height >= NetWorthWidget.MEDIUM.height) {
                    data.trend?.let { trend ->
                        Spacer(GlanceModifier.height(6.dp))
                        val chartHeight = if (size.height >= NetWorthWidget.LARGE.height) {
                            (size.height.value - 130f).coerceAtLeast(36f)
                        } else {
                            (size.height.value - 82f).coerceAtLeast(24f)
                        }
                        val chartWidth = size.width.value - 28f
                        val density = context.resources.displayMetrics.density
                        val dark = (context.resources.configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                        val bitmap = Sparkline.render(
                            trend.points,
                            (chartWidth * density).toInt(),
                            (chartHeight * density).toInt(),
                            TrendColors.argb(trend.isUp, dark),
                        )
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = "${trend.period.label} trend " + if (trend.isUp) "up" else "down",
                            modifier = GlanceModifier.fillMaxWidth().height(chartHeight.dp),
                        )
                    }
                }

                if (size.height >= NetWorthWidget.LARGE.height && data.extra.isNotEmpty()) {
                    Spacer(GlanceModifier.height(6.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        data.extra.forEachIndexed { i, change ->
                            if (i > 0) Spacer(GlanceModifier.width(10.dp))
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Label(change.period.label)
                                Text(
                                    if (hide) Format.percent(change.percent)
                                    else Format.signedMoney(change.change, whole = true),
                                    style = TextStyle(
                                        color = if (change.isUp) upColor else downColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        maxLines = 1,
    )
}

@Composable
private fun ChangeLine(change: PeriodChange, hide: Boolean, prefix: String) {
    val arrow = if (change.isUp) "▲" else "▼"
    val amount = if (hide) "" else Format.signedMoney(change.change) + "  "
    Text(
        "$arrow $amount${Format.percent(change.percent)}  $prefix",
        style = TextStyle(
            color = if (change.isUp) upColor else downColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
    )
}
