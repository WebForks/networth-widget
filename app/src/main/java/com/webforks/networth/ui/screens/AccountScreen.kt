package com.webforks.networth.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webforks.networth.data.NetWorthRepository
import com.webforks.networth.data.db.HoldingEntity
import com.webforks.networth.data.db.HoldingSnapshotEntity
import com.webforks.networth.domain.Format
import com.webforks.networth.domain.History
import com.webforks.networth.domain.Period
import com.webforks.networth.domain.kindLabel
import com.webforks.networth.ui.AppViewModel
import com.webforks.networth.ui.components.PerformanceSection
import com.webforks.networth.ui.theme.trendColor
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(vm: AppViewModel, accountId: String, onBack: () -> Unit) {
    val account by remember(accountId) { vm.repo.account(accountId) }.collectAsState(initial = null)
    val holdings by remember(accountId) { vm.repo.holdings(accountId) }.collectAsState(initial = emptyList())
    val holdingHistory by remember(accountId) { vm.repo.holdingSnapshots(accountId) }.collectAsState(initial = emptyList())
    val snapshots by vm.snapshots.collectAsState()
    var period by rememberSaveable { mutableStateOf(Period.M1) }
    var renaming by remember { mutableStateOf(false) }

    val series = remember(snapshots, accountId) { NetWorthRepository.seriesFor(snapshots, setOf(accountId)) }
    val today = LocalDate.now()
    val acct = account

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(acct?.displayName ?: "Account", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
                },
            )
        },
    ) { padding ->
        if (acct == null) return@Scaffold
        val historyBySecurity = remember(holdingHistory) { holdingHistory.groupBy { it.securityId } }
        val holdingsTotal = holdings.sumOf { it.value }.takeIf { it > 0 } ?: 1.0

        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                PerformanceSection(
                    title = listOfNotNull(acct.kindLabel, acct.mask?.let { "••$it" }).joinToString(" · "),
                    series = series,
                    changeFor = { History.change(series, it, today) },
                    period = period,
                    onPeriod = { period = it },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Include in net worth") },
                    supportingContent = {
                        Text(if (acct.isLiability) "Debt: subtracted from your total" else "Counted in your total and widget")
                    },
                    trailingContent = {
                        Switch(checked = acct.includeInNetWorth, onCheckedChange = { vm.setIncluded(accountId, it) })
                    },
                )
                acct.availableBalance?.takeIf { acct.type == "depository" || acct.type == "credit" }?.let {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        InfoLine(if (acct.type == "credit") "Available credit" else "Available", Format.money(it))
                    }
                }
            }

            if (holdings.isNotEmpty()) {
                item {
                    SectionTitle("Holdings")
                    Text(
                        "Period return uses prices recorded since this app started tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(holdings, key = { it.securityId }) { h ->
                    HoldingRow(
                        h = h,
                        share = h.value / holdingsTotal,
                        history = historyBySecurity[h.securityId].orEmpty(),
                        period = period,
                        today = today,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (renaming && acct != null) {
        var text by remember { mutableStateOf(acct.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename account") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Nickname") },
                    placeholder = { Text(acct.name) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setNickname(accountId, text); renaming = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HoldingRow(
    h: HoldingEntity,
    share: Double,
    history: List<HoldingSnapshotEntity>,
    period: Period,
    today: LocalDate,
) {
    // Price-based return so new contributions don't look like gains.
    val priced = history.filter { it.price != null && it.price > 0 }
    val startDay = period.startDay(today).let { if (it == LocalDate.MIN) Long.MIN_VALUE else it.toEpochDay() }
    val startPrice = if (period == Period.D1) {
        // Last price that differs from the current one (skips weekends).
        priced.dropLast(1).lastOrNull { it.price != h.price }?.price
    } else {
        (priced.lastOrNull { it.epochDay <= startDay } ?: priced.firstOrNull())?.price
    }
    val periodReturn = if (startPrice != null && h.price != null && startPrice > 0) h.price / startPrice - 1 else null
    val gain = h.costBasis?.takeIf { it > 0 }?.let { h.value - it }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        h.ticker,
                        h.securityType?.replace('_', ' '),
                        if (!h.isCash) "${Format.shares(h.quantity)} @ ${h.price?.let { Format.money(it) } ?: "—"}" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.money(h.value), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    String.format(java.util.Locale.US, "%.1f%% of account", share * 100),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.padding(top = 4.dp)) {
            if (periodReturn != null) {
                Text(
                    "${period.label} ${Format.percent(periodReturn)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = trendColor(periodReturn >= 0),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (gain != null) {
                Text(
                    "Total ${Format.signedMoney(gain)} (${Format.percent(gain / h.costBasis)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = trendColor(gain >= 0),
                )
            }
        }
    }
}
