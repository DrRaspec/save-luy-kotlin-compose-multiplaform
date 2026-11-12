package com.bunleng.saving_luy.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.EnumColumnAdapter
import com.bunleng.saving_luy.data.db.Account
import com.bunleng.saving_luy.data.db.MoneyDatabase
import com.bunleng.saving_luy.widget.WidgetSnapshotUpdater
import com.bunleng.saving_luy.widget.WidgetSnapshotWriter

/**
 * Owns the database and repositories. Create one per process and share it.
 */
class AppContainer(driver: SqlDriver, widgetSnapshotWriter: WidgetSnapshotWriter) {

    private val database = MoneyDatabase(driver, Account.Adapter(typeAdapter = EnumColumnAdapter()))

    val changes = DataChangeNotifier()
    val accountRepository = AccountRepository(database, changes)
    val budgetRepository = BudgetRepository(database, accountRepository, changes)
    private val widgetSnapshotUpdater = WidgetSnapshotUpdater(budgetRepository, widgetSnapshotWriter)

    init {
        budgetRepository.ensureSeeded()
        accountRepository.ensureSeeded()
        // Keep the widget summary current: once at startup, then after every write.
        widgetSnapshotUpdater.update()
        changes.addListener(widgetSnapshotUpdater::update)
    }
}
