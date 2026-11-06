package com.webforks.networth.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.webforks.networth.data.AppSettings
import com.webforks.networth.data.plaid.PlaidEnvironment
import com.webforks.networth.domain.Format
import com.webforks.networth.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var clientId by remember { mutableStateOf(vm.repo.clientId().orEmpty()) }
    var sandboxSecret by remember { mutableStateOf("") }
    var productionSecret by remember { mutableStateOf("") }
    // Re-read after saving so the "saved" hints update.
    var keysVersion by remember { mutableStateOf(0) }
    val hasSandbox = remember(keysVersion) { vm.repo.hasSecret(PlaidEnvironment.SANDBOX) }
    val hasProduction = remember(keysVersion) { vm.repo.hasSecret(PlaidEnvironment.PRODUCTION) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            SectionTitle("Plaid keys")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "From dashboard.plaid.com → Developers → Keys. Stored encrypted on this phone only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("client_id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretField("Sandbox secret", sandboxSecret, hasSandbox) { sandboxSecret = it }
                SecretField("Production secret", productionSecret, hasProduction) { productionSecret = it }
                Button(
                    onClick = {
                        vm.saveCredentials(clientId, sandboxSecret, productionSecret)
                        sandboxSecret = ""
                        productionSecret = ""
                        keysVersion++
                    },
                    enabled = clientId.isNotBlank(),
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Save keys") }
            }

            SectionTitle("New connections use")
            PlaidEnvironment.entries.forEach { env ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(
                            selected = settings.environment == env,
                            onClick = { vm.updateSettings { it.copy(environment = env) } },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.environment == env, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(env.label)
                        Text(
                            if (env == PlaidEnvironment.SANDBOX) "Fake bank; sign in with user_good / pass_good"
                            else "Your real accounts (free Trial plan, up to 10 connections)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Background refresh")
            ChipRow(
                options = AppSettings.REFRESH_CHOICES,
                selected = settings.refreshHours,
                label = { "${it}h" },
                onSelect = { h -> vm.updateSettings { it.copy(refreshHours = h) } },
            )
            Text(
                "Banks update a few times a day and 401(k)/IRA balances update once a day after market close, " +
                    "so refreshing more often rarely shows anything new. Fund holdings are fetched at most once a day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                InfoLine("Last refresh", Format.ago(settings.lastRefreshAt))
                settings.lastRefreshError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            SectionTitle("Widget")
            Text(
                "Trend line period",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ChipRow(
                options = AppSettings.WIDGET_PERIOD_CHOICES,
                selected = settings.widgetPeriod,
                label = { it.label },
                onSelect = { p -> vm.updateSettings { it.copy(widgetPeriod = p) } },
            )
            ListItem(
                headlineContent = { Text("Hide dollar amounts on widget") },
                supportingContent = { Text("Shows only percentages, for privacy on the home or lock screen") },
                trailingContent = {
                    Switch(
                        checked = settings.hideAmountsInWidget,
                        onCheckedChange = { v -> vm.updateSettings { it.copy(hideAmountsInWidget = v) } },
                    )
                },
            )
            Text(
                "Resize the widget to see more: small shows the total and today's change, " +
                    "medium adds the trend line, and large adds 1M / YTD / 1Y.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, saved: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(if (saved) "Saved — leave blank to keep" else "Not set") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}
