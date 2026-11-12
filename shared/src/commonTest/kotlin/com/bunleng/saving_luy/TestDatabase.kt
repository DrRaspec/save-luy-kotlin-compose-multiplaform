package com.bunleng.saving_luy

import app.cash.sqldelight.db.SqlDriver
import com.bunleng.saving_luy.data.AppContainer
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.domain.Transaction
import com.bunleng.saving_luy.widget.WidgetSnapshot
import com.bunleng.saving_luy.widget.WidgetSnapshotWriter

/** A fresh in-memory database for each call. */
expect fun createTestDriver(): SqlDriver

/** Keeps every snapshot the app writes, newest last. */
class RecordingSnapshotWriter : WidgetSnapshotWriter {
    val snapshots = mutableListOf<WidgetSnapshot>()
    override fun save(snapshot: WidgetSnapshot) {
        snapshots += snapshot
    }
}

fun testContainer(widgetWriter: WidgetSnapshotWriter = RecordingSnapshotWriter()): AppContainer =
    AppContainer(createTestDriver(), widgetWriter)

/** Id of the seeded "Cash" account. */
fun AppContainer.cashAccountId(): Long = accountRepository.getAccounts().first { it.name == "Cash" }.id

fun AppContainer.addTransaction(
    amount: Long,
    date: SimpleDate,
    categoryId: String? = null,
    accountId: Long = cashAccountId(),
    payee: String = "",
) {
    accountRepository.saveTransaction(
        Transaction(Transaction.NEW_ID, accountId, categoryId, payee, amount, date, memo = "")
    )
}
