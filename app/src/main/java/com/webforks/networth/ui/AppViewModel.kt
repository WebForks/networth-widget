package com.webforks.networth.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webforks.networth.container
import com.webforks.networth.data.NetWorthRepository
import com.webforks.networth.data.Settings
import com.webforks.networth.data.db.AccountEntity
import com.webforks.networth.data.db.BalanceSnapshotEntity
import com.webforks.networth.data.db.ConnectionEntity
import com.webforks.networth.data.plaid.LinkKind
import com.webforks.networth.data.plaid.PlaidEnvironment
import com.webforks.networth.sync.RefreshScheduler
import com.webforks.networth.widget.NetWorthWidget
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A request for the Activity to open Plaid Link with a freshly created token. */
data class LinkRequest(val token: String, val kind: LinkKind, val reauthItemId: String?)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    val repo: NetWorthRepository = c.repository

    val settings: StateFlow<Settings> = c.settings.settings
    val connections: StateFlow<List<ConnectionEntity>> =
        repo.connections.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val accounts: StateFlow<List<AccountEntity>> =
        repo.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val snapshots: StateFlow<List<BalanceSnapshotEntity>> =
        repo.balanceSnapshots.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    /** Non-null while a long operation (linking) runs; the text describes it. */
    val busy = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _linkRequests = MutableSharedFlow<LinkRequest>(extraBufferCapacity = 1)
    val linkRequests: SharedFlow<LinkRequest> = _linkRequests.asSharedFlow()

    fun refresh(forceHoldings: Boolean = false) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val result = repo.refreshAll(forceHoldings)
                if (result.failures.isNotEmpty()) _messages.emit(result.failures.joinToString("\n"))
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Refresh failed")
            } finally {
                _refreshing.value = false
                NetWorthWidget.updateAll(getApplication())
            }
        }
    }

    /** Refresh on app open when the last refresh is older than [staleAfterMs]. */
    fun refreshIfStale(staleAfterMs: Long) = viewModelScope.launch {
        val last = settings.value.lastRefreshAt ?: 0L
        if (System.currentTimeMillis() - last > staleAfterMs && repo.hasConnections()) refresh()
    }

    fun startLink(kind: LinkKind) = viewModelScope.launch {
        _busy.value = "Opening Plaid…"
        try {
            _linkRequests.emit(LinkRequest(repo.createLinkToken(kind), kind, null))
        } catch (e: Exception) {
            _messages.emit(e.message ?: "Could not start Plaid Link")
        } finally {
            _busy.value = null
        }
    }

    fun startReauth(itemId: String) = viewModelScope.launch {
        _busy.value = "Opening Plaid…"
        try {
            val conn = connections.value.firstOrNull { it.itemId == itemId } ?: return@launch
            _linkRequests.emit(
                LinkRequest(repo.createUpdateLinkToken(itemId), LinkKind.valueOf(conn.kind), itemId),
            )
        } catch (e: Exception) {
            _messages.emit(e.message ?: "Could not start Plaid Link")
        } finally {
            _busy.value = null
        }
    }

    fun onLinkSuccess(
        request: LinkRequest,
        publicToken: String,
        institutionId: String?,
        institutionName: String?,
    ) = viewModelScope.launch {
        _busy.value = "Importing ${institutionName ?: "accounts"}…"
        try {
            if (request.reauthItemId != null) {
                repo.completeReauth(request.reauthItemId)
                _messages.emit("${institutionName ?: "Connection"} reconnected")
            } else {
                repo.completeLink(publicToken, request.kind, institutionId, institutionName)
                _messages.emit("${institutionName ?: "Institution"} linked")
            }
        } catch (e: Exception) {
            _messages.emit(e.message ?: "Linking failed")
        } finally {
            _busy.value = null
            NetWorthWidget.updateAll(getApplication())
        }
    }

    fun onLinkExit(message: String?) {
        if (message != null) _messages.tryEmit(message)
    }

    fun removeConnection(itemId: String) = viewModelScope.launch {
        repo.removeConnection(itemId)
        NetWorthWidget.updateAll(getApplication())
    }

    fun setIncluded(accountId: String, include: Boolean) = viewModelScope.launch {
        repo.setIncluded(accountId, include)
        NetWorthWidget.updateAll(getApplication())
    }

    fun setNickname(accountId: String, nickname: String?) = viewModelScope.launch {
        repo.setNickname(accountId, nickname)
    }

    // ---- Settings ---------------------------------------------------------------------

    fun saveCredentials(clientId: String, sandboxSecret: String, productionSecret: String) {
        repo.saveCredentials(clientId, PlaidEnvironment.SANDBOX, sandboxSecret)
        repo.saveCredentials(clientId, PlaidEnvironment.PRODUCTION, productionSecret)
        _messages.tryEmit("Plaid keys saved")
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val before = settings.value
        c.settings.update(transform)
        val after = settings.value
        if (before.refreshHours != after.refreshHours) {
            RefreshScheduler.schedule(getApplication(), after.refreshHours, replace = true)
        }
        viewModelScope.launch { NetWorthWidget.updateAll(getApplication()) }
    }
}
