package com.webforks.networth.data

import com.webforks.networth.data.db.AccountEntity
import com.webforks.networth.data.db.BalanceSnapshotEntity
import com.webforks.networth.data.db.ConnectionEntity
import com.webforks.networth.data.db.HoldingEntity
import com.webforks.networth.data.db.HoldingSnapshotEntity
import com.webforks.networth.data.db.NetWorthDao
import com.webforks.networth.data.plaid.LinkKind
import com.webforks.networth.data.plaid.PlaidAccount
import com.webforks.networth.data.plaid.PlaidApi
import com.webforks.networth.data.plaid.PlaidApiException
import com.webforks.networth.data.plaid.PlaidCredentials
import com.webforks.networth.data.plaid.PlaidEnvironment
import com.webforks.networth.data.plaid.PlaidItem
import com.webforks.networth.data.secure.SecretStore
import com.webforks.networth.domain.History
import com.webforks.networth.domain.Observation
import com.webforks.networth.domain.Point
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class RefreshResult(val refreshed: Int, val failures: List<String>)

class NetWorthRepository(
    private val dao: NetWorthDao,
    private val secrets: SecretStore,
    private val settings: AppSettings,
    private val packageName: String,
) {
    val plaid = PlaidApi { env -> credentials(env) }
    private val refreshLock = Mutex()

    // ---- Plaid credentials ------------------------------------------------------------

    fun credentials(env: PlaidEnvironment): PlaidCredentials? {
        val id = secrets.get(SecretStore.PLAID_CLIENT_ID)?.takeIf { it.isNotBlank() } ?: return null
        val secret = secrets.get(SecretStore.plaidSecretKey(env.name))?.takeIf { it.isNotBlank() }
            ?: return null
        return PlaidCredentials(id, secret, env)
    }

    fun clientId(): String? = secrets.get(SecretStore.PLAID_CLIENT_ID)
    fun hasSecret(env: PlaidEnvironment) = !secrets.get(SecretStore.plaidSecretKey(env.name)).isNullOrBlank()

    fun saveCredentials(clientId: String, env: PlaidEnvironment, secret: String?) {
        secrets.put(SecretStore.PLAID_CLIENT_ID, clientId.trim())
        // A blank secret field means "keep the saved one".
        if (!secret.isNullOrBlank()) secrets.put(SecretStore.plaidSecretKey(env.name), secret.trim())
    }

    // ---- Linking ----------------------------------------------------------------------

    suspend fun createLinkToken(kind: LinkKind): String =
        plaid.createLinkToken(settings.current.environment, kind, packageName).linkToken

    /** Link token for re-authenticating an existing connection ("update mode"). */
    suspend fun createUpdateLinkToken(itemId: String): String {
        val conn = dao.connection(itemId) ?: error("Unknown connection")
        val token = accessToken(itemId)
        return plaid.createLinkToken(conn.env, LinkKind.valueOf(conn.kind), packageName, token).linkToken
    }

    suspend fun completeLink(
        publicToken: String,
        kind: LinkKind,
        institutionId: String?,
        institutionName: String?,
    ) {
        val env = settings.current.environment
        val exchange = plaid.exchangePublicToken(env, publicToken)
        secrets.put(SecretStore.accessTokenKey(exchange.itemId), exchange.accessToken)
        dao.upsertConnection(
            ConnectionEntity(
                itemId = exchange.itemId,
                institutionId = institutionId,
                institutionName = institutionName ?: "Linked institution",
                kind = kind.name,
                environment = env.name,
            ),
        )
        refreshConnection(exchange.itemId, forceHoldings = true)
    }

    /** Called after update-mode Link succeeds; the existing access token keeps working. */
    suspend fun completeReauth(itemId: String) {
        dao.setConnectionStatus(itemId, ConnectionEntity.STATUS_OK, null)
        refreshConnection(itemId, forceHoldings = true)
    }

    suspend fun removeConnection(itemId: String) {
        val conn = dao.connection(itemId)
        val token = secrets.get(SecretStore.accessTokenKey(itemId))
        if (conn != null && token != null) {
            // Best effort: revoke at Plaid too. Local data is removed either way.
            runCatching { plaid.removeItem(conn.env, token) }
        }
        secrets.remove(SecretStore.accessTokenKey(itemId))
        dao.deleteEverythingForItem(itemId)
    }

    // ---- Refreshing -------------------------------------------------------------------

    suspend fun refreshAll(forceHoldings: Boolean = false): RefreshResult = refreshLock.withLock {
        val failures = mutableListOf<String>()
        var ok = 0
        for (conn in dao.connections()) {
            runCatching { refreshConnectionLocked(conn, forceHoldings) }
                .onSuccess { ok++ }
                .onFailure { failures += "${conn.institutionName}: ${it.message}" }
        }
        settings.update {
            it.copy(
                lastRefreshAt = System.currentTimeMillis(),
                lastRefreshError = failures.joinToString("\n").ifBlank { null },
            )
        }
        RefreshResult(ok, failures)
    }

    suspend fun refreshConnection(itemId: String, forceHoldings: Boolean = false) =
        refreshLock.withLock {
            val conn = dao.connection(itemId) ?: return@withLock
            refreshConnectionLocked(conn, forceHoldings)
        }

    private suspend fun refreshConnectionLocked(conn: ConnectionEntity, forceHoldings: Boolean) {
        val token = accessToken(conn.itemId)
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        try {
            val holdingsDue = conn.kind == LinkKind.INVESTMENT.name &&
                (forceHoldings || conn.lastHoldingsSyncAt == null ||
                    now - conn.lastHoldingsSyncAt > HOLDINGS_MIN_INTERVAL_MS)

            val plaidAccounts: List<PlaidAccount>
            val item: PlaidItem
            if (holdingsDue) {
                // One call gives both positions and balances.
                val resp = plaid.getHoldings(conn.env, token)
                plaidAccounts = resp.accounts
                item = resp.item
                val securities = resp.securities.associateBy { it.securityId }
                val holdingsByAccount = resp.holdings.groupBy { it.accountId }
                for (acct in resp.accounts) {
                    val rows = holdingsByAccount[acct.accountId].orEmpty().map { h ->
                        val sec = securities[h.securityId]
                        HoldingEntity(
                            accountId = h.accountId,
                            securityId = h.securityId,
                            name = sec?.name ?: sec?.tickerSymbol ?: "Unknown security",
                            ticker = sec?.tickerSymbol,
                            securityType = sec?.type,
                            quantity = h.quantity,
                            price = h.institutionPrice ?: sec?.closePrice,
                            value = h.institutionValue ?: (h.quantity * (h.institutionPrice ?: 0.0)),
                            costBasis = h.costBasis,
                            priceAsOf = h.institutionPriceAsOf,
                            isCash = sec?.isCashEquivalent == true || sec?.type == "cash",
                        )
                    }
                    dao.replaceHoldings(acct.accountId, rows)
                    dao.upsertHoldingSnapshots(
                        rows.map {
                            HoldingSnapshotEntity(it.accountId, it.securityId, today, it.quantity, it.price, it.value)
                        },
                    )
                }
            } else {
                val resp = plaid.getAccounts(conn.env, token)
                plaidAccounts = resp.accounts
                item = resp.item
            }

            val existing = dao.accountsForItem(conn.itemId).associateBy { it.accountId }
            val accounts = plaidAccounts.map { a ->
                val prev = existing[a.accountId]
                AccountEntity(
                    accountId = a.accountId,
                    itemId = conn.itemId,
                    name = a.name,
                    officialName = a.officialName,
                    mask = a.mask,
                    type = a.type,
                    subtype = a.subtype,
                    // Prefer the stated balance; fall back to available if the bank omits it.
                    currentBalance = a.balances.current ?: a.balances.available ?: 0.0,
                    availableBalance = a.balances.available,
                    currency = a.balances.isoCurrencyCode,
                    includeInNetWorth = prev?.includeInNetWorth ?: true,
                    nickname = prev?.nickname,
                    updatedAt = now,
                )
            }
            dao.upsertAccounts(accounts)
            dao.upsertBalanceSnapshots(
                accounts.map { BalanceSnapshotEntity(it.accountId, today, it.signedBalance, now) },
            )
            dao.upsertConnection(
                conn.copy(
                    status = if (item.error != null) ConnectionEntity.STATUS_ERROR else ConnectionEntity.STATUS_OK,
                    statusMessage = item.error?.displayMessage ?: item.error?.errorMessage,
                    consentExpiresAt = item.consentExpirationTime,
                    lastSyncedAt = now,
                    lastHoldingsSyncAt = if (holdingsDue) now else conn.lastHoldingsSyncAt,
                ),
            )
        } catch (e: PlaidApiException) {
            dao.setConnectionStatus(
                conn.itemId,
                if (e.needsReauth) ConnectionEntity.STATUS_NEEDS_REAUTH else ConnectionEntity.STATUS_ERROR,
                e.message,
            )
            throw e
        }
    }

    private fun accessToken(itemId: String): String =
        secrets.get(SecretStore.accessTokenKey(itemId))
            ?: error("Missing access token; remove and re-link this institution.")

    // ---- Reads ------------------------------------------------------------------------

    val connections = dao.observeConnections()
    val accounts = dao.observeAccounts()
    val balanceSnapshots = dao.observeBalanceSnapshots()
    fun account(accountId: String) = dao.observeAccount(accountId)
    fun holdings(accountId: String) = dao.observeHoldings(accountId)
    fun holdingSnapshots(accountId: String) = dao.observeHoldingSnapshots(accountId)

    suspend fun hasConnections() = dao.connections().isNotEmpty()

    suspend fun setIncluded(accountId: String, include: Boolean) = dao.setIncluded(accountId, include)
    suspend fun setNickname(accountId: String, nickname: String?) =
        dao.setNickname(accountId, nickname?.trim()?.ifBlank { null })

    /** One-shot read for the widget: daily net worth series over included accounts. */
    suspend fun netWorthSeries(): List<Point> {
        val included = dao.accounts().filter { it.includeInNetWorth }.map { it.accountId }.toSet()
        return seriesFor(dao.balanceSnapshots(), included)
    }

    companion object {
        /** Investment positions only change once a day at Plaid, so don't over-fetch them. */
        private const val HOLDINGS_MIN_INTERVAL_MS = 20L * 60 * 60 * 1000

        fun seriesFor(snapshots: List<BalanceSnapshotEntity>, accountIds: Set<String>): List<Point> =
            History.dailyTotals(
                snapshots.filter { it.accountId in accountIds }
                    .map { Observation(it.accountId, it.epochDay, it.signedBalance) },
                LocalDate.now().toEpochDay(),
            )
    }
}

val ConnectionEntity.env: PlaidEnvironment
    get() = runCatching { PlaidEnvironment.valueOf(environment) }.getOrDefault(PlaidEnvironment.SANDBOX)
