package com.webforks.networth.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.webforks.networth.data.db.AccountEntity
import com.webforks.networth.data.db.ConnectionEntity
import com.webforks.networth.domain.Format
import com.webforks.networth.domain.History
import com.webforks.networth.domain.Period
import com.webforks.networth.domain.PeriodChange
import com.webforks.networth.domain.kindLabel
import com.webforks.networth.ui.AppViewModel
import com.webforks.networth.ui.components.PerformanceSection
import com.webforks.networth.ui.components.SmallChange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionScreen(
    vm: AppViewModel,
    itemId: String,
    onBack: () -> Unit,
    onOpenAccount: (String) -> Unit,
) {
    val connections by vm.connections.collectAsState()
    val allAccounts by vm.accounts.collectAsState()
    val snapshots by vm.snapshots.collectAsState()
    val conn = connections.firstOrNull { it.itemId == itemId }
    val accounts = remember(allAccounts, itemId) { allAccounts.filter { it.itemId == itemId } }
    var period by rememberSaveable { mutableStateOf(Period.M1) }
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    val includedIds = remember(accounts) { accounts.filter { it.includeInNetWorth }.map { it.accountId }.toSet() }
    val series = remember(snapshots, includedIds) { NetWorthRepository.seriesFor(snapshots, includedIds) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conn?.institutionName ?: "Institution", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Reconnect / update login") },
                            onClick = { menu = false; vm.startReauth(itemId) },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove institution") },
                            onClick = { menu = false; confirmRemove = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        // Null briefly while loading, or after removal (which navigates back itself).
        if (conn == null) return@Scaffold
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                PerformanceSection(
                    title = conn.institutionName,
                    series = series,
                    changeFor = { History.change(series, it, today) },
                    period = period,
                    onPeriod = { period = it },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (conn.status != ConnectionEntity.STATUS_OK) {
                item {
                    NoticeCard(
                        title = "Connection needs attention",
                        body = conn.statusMessage ?: "Sign in again to resume updates.",
                        action = "Reconnect",
                        onAction = { vm.startReauth(itemId) },
                        warning = true,
                    )
                }
            }
            item { SectionTitle("Accounts") }
            items(accounts, key = { it.accountId }) { acct ->
                val acctSeries = remember(snapshots, acct.accountId) {
                    NetWorthRepository.seriesFor(snapshots, setOf(acct.accountId))
                }
                AccountRow(acct, History.change(acctSeries, period, today), onClick = { onOpenAccount(acct.accountId) })
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    InfoLine("Last synced", Format.ago(conn.lastSyncedAt))
                    if (conn.kind == "INVESTMENT") InfoLine("Holdings synced", Format.ago(conn.lastHoldingsSyncAt))
                    conn.consentExpiresAt?.let { InfoLine("Bank consent expires", it.take(10)) }
                    InfoLine("Plaid environment", conn.environment.lowercase().replaceFirstChar { it.uppercase() })
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (confirmRemove && conn != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${conn.institutionName}?") },
            text = {
                Text(
                    "This disconnects it at Plaid and deletes its accounts and history from this phone. " +
                        "On Plaid's free Trial plan, removing does not give back one of your 10 connection slots.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    vm.removeConnection(itemId)
                    onBack()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AccountRow(acct: AccountEntity, change: PeriodChange?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(acct.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(acct.kindLabel, acct.mask?.let { "••$it" }, if (!acct.includeInNetWorth) "excluded" else null)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Format.money(if (acct.isLiability) -acct.currentBalance else acct.currentBalance),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            SmallChange(change)
        }
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
