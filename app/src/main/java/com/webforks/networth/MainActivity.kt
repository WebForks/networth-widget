package com.webforks.networth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.plaid.link.OpenPlaidLink
import com.plaid.link.Plaid
import com.plaid.link.PlaidSession
import com.plaid.link.configuration.LinkTokenConfiguration
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkResult
import com.plaid.link.result.LinkSuccess
import com.webforks.networth.ui.AppViewModel
import com.webforks.networth.ui.LinkRequest
import com.webforks.networth.ui.screens.AccountScreen
import com.webforks.networth.ui.screens.HomeScreen
import com.webforks.networth.ui.screens.InstitutionScreen
import com.webforks.networth.ui.screens.SettingsScreen
import com.webforks.networth.ui.theme.NetWorthTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    /** The Link session in flight; Plaid's result doesn't say whether it was a new link or a re-login. */
    private var pendingLink: LinkRequest? = null

    private val plaidLauncher: ActivityResultLauncher<PlaidSession> =
        registerForActivityResult(OpenPlaidLink()) { result: LinkResult ->
            val request = pendingLink
            pendingLink = null
            when (result) {
                is LinkSuccess -> if (request != null) {
                    vm.onLinkSuccess(
                        request,
                        result.publicToken,
                        result.metadata.institution?.id,
                        result.metadata.institution?.name,
                    )
                }
                is LinkExit -> vm.onLinkExit(result.error?.displayMessage ?: result.error?.errorMessage)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            vm.linkRequests.collect { request ->
                pendingLink = request
                val session = Plaid.createPlaidLinkSession(
                    application,
                    LinkTokenConfiguration.Builder().token(request.token).build(),
                )
                plaidLauncher.launch(session)
            }
        }

        setContent {
            NetWorthTheme {
                val snackbar = remember { SnackbarHostState() }
                val busy by vm.busy.collectAsState()
                LaunchedEffect(Unit) {
                    vm.messages.collect { snackbar.showSnackbar(it) }
                }

                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            vm = vm,
                            snackbar = snackbar,
                            onOpenConnection = { nav.navigate("connection/$it") },
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("connection/{itemId}") { entry ->
                        InstitutionScreen(
                            vm = vm,
                            itemId = entry.arguments?.getString("itemId").orEmpty(),
                            onBack = { nav.popBackStack() },
                            onOpenAccount = { nav.navigate("account/$it") },
                        )
                    }
                    composable("account/{accountId}") { entry ->
                        AccountScreen(
                            vm = vm,
                            accountId = entry.arguments?.getString("accountId").orEmpty(),
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
                    }
                }

                busy?.let { text ->
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(4.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(text)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app (e.g. tapping the widget) pulls fresh balances if they're stale.
        vm.refreshIfStale(STALE_AFTER_MS)
    }

    private companion object {
        const val STALE_AFTER_MS = 30L * 60 * 1000
    }
}
