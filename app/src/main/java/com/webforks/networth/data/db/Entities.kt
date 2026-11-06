package com.webforks.networth.data.db

import androidx.room.Entity
import androidx.room.Index

/** One login at one institution (a Plaid "Item"). Its access token lives in SecretStore. */
@Entity(tableName = "connections", primaryKeys = ["itemId"])
data class ConnectionEntity(
    val itemId: String,
    val institutionId: String?,
    val institutionName: String,
    /** [com.webforks.networth.data.plaid.LinkKind] name. */
    val kind: String,
    /** [com.webforks.networth.data.plaid.PlaidEnvironment] name; tokens only work in their own. */
    val environment: String,
    val status: String = STATUS_OK,
    val statusMessage: String? = null,
    val consentExpiresAt: String? = null,
    val lastSyncedAt: Long? = null,
    val lastHoldingsSyncAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_OK = "OK"
        const val STATUS_NEEDS_REAUTH = "NEEDS_REAUTH"
        const val STATUS_ERROR = "ERROR"
    }
}

@Entity(
    tableName = "accounts",
    primaryKeys = ["accountId"],
    indices = [Index("itemId")],
)
data class AccountEntity(
    val accountId: String,
    val itemId: String,
    val name: String,
    val officialName: String?,
    val mask: String?,
    val type: String,
    val subtype: String?,
    val currentBalance: Double,
    val availableBalance: Double?,
    val currency: String?,
    val includeInNetWorth: Boolean = true,
    val nickname: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Credit cards and loans are money owed, so they subtract from net worth. */
    val isLiability: Boolean get() = type == "credit" || type == "loan"

    /** Contribution to net worth: positive for assets, negative for debts. */
    val signedBalance: Double get() = if (isLiability) -currentBalance else currentBalance

    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: name
}

@Entity(
    tableName = "holdings",
    primaryKeys = ["accountId", "securityId"],
)
data class HoldingEntity(
    val accountId: String,
    val securityId: String,
    val name: String,
    val ticker: String?,
    val securityType: String?,
    val quantity: Double,
    val price: Double?,
    val value: Double,
    val costBasis: Double?,
    val priceAsOf: String?,
    val isCash: Boolean,
)

/** End-of-day balance per account; today's row is overwritten on every refresh. */
@Entity(
    tableName = "balance_snapshots",
    primaryKeys = ["accountId", "epochDay"],
    indices = [Index("epochDay")],
)
data class BalanceSnapshotEntity(
    val accountId: String,
    val epochDay: Long,
    /** Signed: debts are stored negative so sums are net worth directly. */
    val signedBalance: Double,
    val recordedAt: Long = System.currentTimeMillis(),
)

/** End-of-day value per fund/stock, used for per-holding performance. */
@Entity(
    tableName = "holding_snapshots",
    primaryKeys = ["accountId", "securityId", "epochDay"],
)
data class HoldingSnapshotEntity(
    val accountId: String,
    val securityId: String,
    val epochDay: Long,
    val quantity: Double,
    val price: Double?,
    val value: Double,
)
