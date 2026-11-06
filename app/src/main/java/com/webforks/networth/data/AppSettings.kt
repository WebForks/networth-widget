package com.webforks.networth.data

import android.content.Context
import com.webforks.networth.data.plaid.PlaidEnvironment
import com.webforks.networth.domain.Period
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val environment: PlaidEnvironment = PlaidEnvironment.SANDBOX,
    val refreshHours: Int = 6,
    val hideAmountsInWidget: Boolean = false,
    /** Window for the widget's trend line; its color follows this window's change. */
    val widgetPeriod: Period = Period.M1,
    val lastRefreshAt: Long? = null,
    val lastRefreshError: String? = null,
)

/** Non-secret preferences. Secrets go in [com.webforks.networth.data.secure.SecretStore]. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state.asStateFlow()
    val current: Settings get() = state.value

    fun update(transform: (Settings) -> Settings) {
        val next = transform(state.value)
        prefs.edit()
            .putString("environment", next.environment.name)
            .putInt("refresh_hours", next.refreshHours)
            .putBoolean("hide_amounts_widget", next.hideAmountsInWidget)
            .putString("widget_period", next.widgetPeriod.name)
            .putLong("last_refresh_at", next.lastRefreshAt ?: 0L)
            .putString("last_refresh_error", next.lastRefreshError)
            .apply()
        state.value = next
    }

    private fun read() = Settings(
        environment = prefs.getString("environment", null)
            ?.let { runCatching { PlaidEnvironment.valueOf(it) }.getOrNull() }
            ?: PlaidEnvironment.SANDBOX,
        refreshHours = prefs.getInt("refresh_hours", 6),
        hideAmountsInWidget = prefs.getBoolean("hide_amounts_widget", false),
        widgetPeriod = prefs.getString("widget_period", null)
            ?.let { runCatching { Period.valueOf(it) }.getOrNull() }
            ?: Period.M1,
        lastRefreshAt = prefs.getLong("last_refresh_at", 0L).takeIf { it > 0 },
        lastRefreshError = prefs.getString("last_refresh_error", null),
    )

    companion object {
        val REFRESH_CHOICES = listOf(2, 4, 6, 12, 24)
        val WIDGET_PERIOD_CHOICES = listOf(Period.W1, Period.M1, Period.M3, Period.YTD, Period.Y1)
    }
}
