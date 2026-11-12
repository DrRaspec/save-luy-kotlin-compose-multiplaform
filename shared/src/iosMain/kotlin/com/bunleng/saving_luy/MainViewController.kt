package com.bunleng.saving_luy

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.window.ComposeUIViewController
import com.bunleng.saving_luy.data.AppContainer
import com.bunleng.saving_luy.data.createDatabaseDriver
import com.bunleng.saving_luy.widget.WidgetSnapshotStore
import platform.UIKit.UIViewController

private var container: AppContainer? = null
private val addTransactionRequests = mutableIntStateOf(0)

/**
 * Entry point for the iOS app.
 * @param reloadWidgets Swift passes `{ WidgetCenter.shared.reloadAllTimelines() }`.
 */
fun MainViewController(reloadWidgets: () -> Unit): UIViewController {
    val appContainer = container
        ?: AppContainer(createDatabaseDriver(), WidgetSnapshotStore(reloadWidgets)).also { container = it }
    return ComposeUIViewController { App(appContainer, addTransactionRequests.intValue) }
}

/** Open Add Transaction, e.g. when the app is opened from the widget. */
fun requestAddTransaction() {
    addTransactionRequests.intValue++
}
