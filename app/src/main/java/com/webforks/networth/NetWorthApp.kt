package com.webforks.networth

import android.app.Application
import android.content.Context
import com.webforks.networth.data.AppSettings
import com.webforks.networth.data.NetWorthRepository
import com.webforks.networth.data.db.AppDatabase
import com.webforks.networth.data.secure.SecretStore
import com.webforks.networth.sync.RefreshScheduler

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val settings = AppSettings(context)
    val database = AppDatabase.create(context)
    val secrets = SecretStore(context)
    val repository = NetWorthRepository(database.dao(), secrets, settings, context.packageName)
}

class NetWorthApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RefreshScheduler.schedule(this, container.settings.current.refreshHours)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as NetWorthApp).container
