package com.webforks.networth.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConnectionEntity::class,
        AccountEntity::class,
        HoldingEntity::class,
        BalanceSnapshotEntity::class,
        HoldingSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): NetWorthDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "networth.db").build()
    }
}
