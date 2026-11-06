package com.webforks.networth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webforks.networth.data.NetWorthRepository
import com.webforks.networth.data.db.ConnectionEntity
import com.webforks.networth.data.plaid.LinkKind
import com.webforks.networth.domain.Category
import com.webforks.networth.domain.Format
import com.webforks.networth.domain.History
import com.webforks.networth.domain.Period
import com.webforks.networth.domain.category
import com.webforks.networth.ui.AppViewModel
import com.webforks.networth.ui.components.PerformanceSection
import com.webforks.networth.ui.components.SmallChange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    onOpenConnection: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val accounts by vm.accounts.collectAsState()
    val connections by vm.connections.collectAsState()
    val snapshots by vm.snapshots.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val settings by vm.settings.collectAsState()
    var period by rememberSaveable { mutableStateOf(Period.M1) }

    val included = remember(accounts) { accounts.filter { it.includeInNetWorth } }
    val series = remember(snapshots, included) {
        NetWorthRepository.seriesFor(snapshots, included.map { it.accountId }.toSet())
    }
    val today = LocalDate.now()
    val hasKeys = remember(settings) { vm.repo.credentials(settings.environment) != null }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Net Worth") },
                actions = {
                    IconButton(onClick = { vm.refresh(forceHoldings = true) }, enabled = !refreshing) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh(forceHoldings = true) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (!hasKeys) {
                    item {
                        NoticeCard(
                            title = "Connect Plaid to get started",
                            body = "Add your free Plaid client ID and secret, then link your banks " +
                                "and retirement accounts. Everything stays on this phone.",
                            action = "Open settings",
                            onAction = onOpenSettings,
                        )
                    }
                }

                if (accounts.isNotEmpty()) {
                    item {
                        PerformanceSection(
                            title = "Net worth",
                            series = series,
                            changeFor = { History.change(series, it, today) },
                            period = period,
                            onPeriod = { period = it },
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        )
                    }
                    item {
                        Text(
                            "Updated ${Format.ago(settings.lastRefreshAt)} · refreshes every ${settings.refreshHours}h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                val broken = connections.filter { it.status != ConnectionEntity.STATUS_OK }
                items(broken, key = { "warn-" + it.itemId }) { conn ->
                    NoticeCard(
                        title = "${conn.institutionName} needs attention",
                        body = conn.statusMessage ?: "Sign in again to keep this account updating.",
                        action = "Reconnect",
                        onAction = { vm.startReauth(conn.itemId) },
                        warning = true,
                    )
                }

                if (included.isNotEmpty()) {
                    item { SectionTitle("Breakdown") }
                    item { Breakdown(included.groupBy { it.category }.mapValues { (_, a) -> a.sumOf { it.signedBalance } }) }
                }

                if (connections.isNotEmpty()) item { SectionTitle("Institutions") }
                items(connections, key = { it.itemId }) { conn ->
                    val connAccounts = accounts.filter { it.itemId == conn.itemId }
                    val connIncluded = connAccounts.filter { it.includeInNetWorth }.map { it.accountId }.toSet()
                    val connSeries = remember(snapshots, connIncluded) {
                        NetWorthRepository.seriesFor(snapshots, connIncluded)
                    }
                    InstitutionRow(
                        conn = conn,
                        total = connSeries.lastOrNull()?.value ?: 0.0,
                        accountCount = connAccounts.size,
                        dayChange = History.dayChange(connSeries),
                        onClick = { onOpenConnection(conn.itemId) },
                    )
                }

                item {
                    SectionTitle("Add an institution")
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LinkButton(
                            Icons.Default.AccountBalance,
                            "Bank or credit card",
                            "Checking, savings, credit cards",
                        ) { vm.startLink(LinkKind.BANK) }
                        LinkButton(
                            Icons.AutoMirrored.Filled.ShowChart,
                            "Investment or retirement",
                            "401(k), IRA, brokerage",
                        ) { vm.startLink(LinkKind.INVESTMENT) }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun NoticeCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    warning: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (warning) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

private val categoryColors = mapOf(
    Category.CASH to Color(0xFF0284C7),
    Category.INVESTMENTS to Color(0xFF7C3AED),
    Category.RETIREMENT to Color(0xFF0D9488),
    Category.OTHER to Color(0xFF64748B),
    Category.CREDIT to Color(0xFFDC2626),
    Category.LOANS to Color(0xFFEA580C),
)

/** Stacked bar of assets by category, with debts listed underneath. */
@Composable
private fun Breakdown(byCategory: Map<Category, Double>) {
    val assets = byCategory.filterValues { it > 0 }.toList().sortedByDescending { it.second }
    val totalAssets = assets.sumOf { it.second }.takeIf { it > 0 } ?: 1.0
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().height(12.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
        ) {
            assets.forEach { (cat, v) ->
                Box(
                    Modifier.weight((v / totalAssets).toFloat().coerceAtLeast(0.01f)).fillMaxHeight()
                        .background(categoryColors.getValue(cat)),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Category.entries.forEach { cat ->
            val v = byCategory[cat] ?: return@forEach
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(categoryColors.getValue(cat), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(cat.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (v > 0) {
                    Text(
                        String.format(java.util.Locale.US, "%.0f%%", v / totalAssets * 100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(Format.money(v, whole = true), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InstitutionRow(
    conn: ConnectionEntity,
    total: Double,
    accountCount: Int,
    dayChange: com.webforks.networth.domain.PeriodChange?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                conn.institutionName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                conn.institutionName + if (conn.environment == "SANDBOX") " (sandbox)" else "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$accountCount account${if (accountCount == 1) "" else "s"} · ${Format.ago(conn.lastSyncedAt)}" +
                    if (conn.status != ConnectionEntity.STATUS_OK) " · ⚠" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.money(total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            SmallChange(dayChange)
        }
    }
}

@Composable
private fun LinkButton(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
