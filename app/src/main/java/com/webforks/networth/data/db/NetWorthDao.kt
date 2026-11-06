package com.webforks.networth.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NetWorthDao {

    // Connections

    @Query("SELECT * FROM connections ORDER BY institutionName")
    fun observeConnections(): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections ORDER BY institutionName")
    suspend fun connections(): List<ConnectionEntity>

    @Query("SELECT * FROM connections WHERE itemId = :itemId")
    suspend fun connection(itemId: String): ConnectionEntity?

    @Upsert
    suspend fun upsertConnection(connection: ConnectionEntity)

    @Query("UPDATE connections SET status = :status, statusMessage = :message WHERE itemId = :itemId")
    suspend fun setConnectionStatus(itemId: String, status: String, message: String?)

    // Accounts

    @Query("SELECT * FROM accounts ORDER BY type, name")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun accounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE accountId = :accountId")
    fun observeAccount(accountId: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE itemId = :itemId")
    suspend fun accountsForItem(itemId: String): List<AccountEntity>

    @Upsert
    suspend fun upsertAccounts(accounts: List<AccountEntity>)

    @Query("UPDATE accounts SET includeInNetWorth = :include WHERE accountId = :accountId")
    suspend fun setIncluded(accountId: String, include: Boolean)

    @Query("UPDATE accounts SET nickname = :nickname WHERE accountId = :accountId")
    suspend fun setNickname(accountId: String, nickname: String?)

    // Holdings

    @Query("SELECT * FROM holdings WHERE accountId = :accountId ORDER BY value DESC")
    fun observeHoldings(accountId: String): Flow<List<HoldingEntity>>

    @Query("DELETE FROM holdings WHERE accountId = :accountId")
    suspend fun clearHoldings(accountId: String)

    @Upsert
    suspend fun upsertHoldings(holdings: List<HoldingEntity>)

    @Transaction
    suspend fun replaceHoldings(accountId: String, holdings: List<HoldingEntity>) {
        clearHoldings(accountId)
        upsertHoldings(holdings)
    }

    // Snapshots

    @Upsert
    suspend fun upsertBalanceSnapshots(snapshots: List<BalanceSnapshotEntity>)

    @Query("SELECT * FROM balance_snapshots ORDER BY epochDay")
    fun observeBalanceSnapshots(): Flow<List<BalanceSnapshotEntity>>

    @Query("SELECT * FROM balance_snapshots ORDER BY epochDay")
    suspend fun balanceSnapshots(): List<BalanceSnapshotEntity>

    @Upsert
    suspend fun upsertHoldingSnapshots(snapshots: List<HoldingSnapshotEntity>)

    @Query("SELECT * FROM holding_snapshots WHERE accountId = :accountId ORDER BY epochDay")
    fun observeHoldingSnapshots(accountId: String): Flow<List<HoldingSnapshotEntity>>

    // Removal

    @Query("DELETE FROM balance_snapshots WHERE accountId IN (SELECT accountId FROM accounts WHERE itemId = :itemId)")
    suspend fun deleteSnapshotsForItem(itemId: String)

    @Query("DELETE FROM holding_snapshots WHERE accountId IN (SELECT accountId FROM accounts WHERE itemId = :itemId)")
    suspend fun deleteHoldingSnapshotsForItem(itemId: String)

    @Query("DELETE FROM holdings WHERE accountId IN (SELECT accountId FROM accounts WHERE itemId = :itemId)")
    suspend fun deleteHoldingsForItem(itemId: String)

    @Query("DELETE FROM accounts WHERE itemId = :itemId")
    suspend fun deleteAccountsForItem(itemId: String)

    @Query("DELETE FROM connections WHERE itemId = :itemId")
    suspend fun deleteConnection(itemId: String)

    @Transaction
    suspend fun deleteEverythingForItem(itemId: String) {
        deleteSnapshotsForItem(itemId)
        deleteHoldingSnapshotsForItem(itemId)
        deleteHoldingsForItem(itemId)
        deleteAccountsForItem(itemId)
        deleteConnection(itemId)
    }
}
