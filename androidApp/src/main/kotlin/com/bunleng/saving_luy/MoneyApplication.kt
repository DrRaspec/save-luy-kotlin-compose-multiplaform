package com.bunleng.saving_luy

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.bunleng.saving_luy.data.AppContainer
import com.bunleng.saving_luy.data.createDatabaseDriver
import com.bunleng.saving_luy.widget.MoneyWidget
import com.bunleng.saving_luy.widget.WidgetSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the single [AppContainer] so the whole app shares one database and widget snapshot writer. */
class MoneyApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob())

    val container: AppContainer by lazy {
        val widgetStore = WidgetSnapshotStore(this, refreshWidgets = ::refreshWidgets)
        AppContainer(createDatabaseDriver(this), widgetStore)
    }

    private fun refreshWidgets() {
        appScope.launch { MoneyWidget().updateAll(this@MoneyApplication) }
    }
}
