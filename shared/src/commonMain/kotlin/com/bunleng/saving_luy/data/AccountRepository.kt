package com.bunleng.saving_luy.data

import com.bunleng.saving_luy.data.db.MoneyDatabase
import com.bunleng.saving_luy.domain.Account
import com.bunleng.saving_luy.domain.AccountType
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.domain.Transaction
import com.bunleng.saving_luy.domain.TransactionRules

/**
 * Accounts and their transactions, stored in SQLite via SQLDelight.
 */
class AccountRepository(
    private val database: MoneyDatabase,
    private val changes: DataChangeNotifier,
) {

    private val accountQueries get() = database.accountQueries
    private val txnQueries get() = database.txnQueries

    /** Seed one empty on-budget cash account on first launch. Idempotent. */
    fun ensureSeeded() {
        if (accountQueries.count().executeAsOne() > 0) return
        accountQueries.insert(name = "Cash", type = AccountType.Cash, currencyCode = DEFAULT_CURRENCY, onBudget = true)
    }

    fun getAccounts(): List<Account> = accountQueries.selectAll(::Account).executeAsList()

    /**
     * Add an account. A positive [startingBalance] is recorded as an uncategorized inflow
     * on [date], so it shows up in the balance and in Ready to Assign (if on budget).
     */
    fun addAccount(
        name: String,
        type: AccountType,
        onBudget: Boolean,
        startingBalance: Long = 0L,
        date: SimpleDate? = null,
    ): Long {
        val id = database.transactionWithResult {
            accountQueries.insert(name = name, type = type, currencyCode = DEFAULT_CURRENCY, onBudget = onBudget)
            val accountId = accountQueries.lastInsertRowId().executeAsOne()
            if (startingBalance != 0L && date != null) {
                txnQueries.insert(
                    accountId, null, TransactionRules.STARTING_BALANCE_PAYEE, startingBalance, date.toEpochDay(), "",
                )
            }
            accountId
        }
        changes.notifyChanged()
        return id
    }

    fun updateAccount(id: Long, name: String, type: AccountType, onBudget: Boolean) {
        accountQueries.update(name = name, type = type, onBudget = onBudget, id = id)
        changes.notifyChanged()
    }

    /** Account of the most recently saved transaction, if any. */
    fun getLastUsedAccountId(): Long? =
        database.appSettingQueries.select(KEY_LAST_ACCOUNT).executeAsOneOrNull()?.toLongOrNull()

    /** Delete an account together with all of its transactions. */
    fun deleteAccount(id: Long) {
        database.transaction {
            txnQueries.deleteByAccount(id)
            accountQueries.delete(id)
        }
        changes.notifyChanged()
    }

    fun getTransactions(): List<Transaction> = txnQueries.selectAll(::toTransaction).executeAsList()

    /** Transactions for one account, newest first. */
    fun getTransactionsForAccount(accountId: Long): List<Transaction> =
        txnQueries.selectByAccount(accountId, ::toTransaction).executeAsList()

    fun getTransaction(id: Long): Transaction? =
        txnQueries.selectById(id, ::toTransaction).executeAsOneOrNull()

    /**
     * Insert when [Transaction.id] is [Transaction.NEW_ID], otherwise update.
     * Also remembers the account as the default for the next transaction.
     */
    fun saveTransaction(transaction: Transaction) {
        database.transaction {
            with(transaction) {
                if (id == Transaction.NEW_ID) {
                    txnQueries.insert(accountId, categoryId, payee, amount, date.toEpochDay(), memo)
                } else {
                    txnQueries.update(accountId, categoryId, payee, amount, date.toEpochDay(), memo, id)
                }
            }
            database.appSettingQueries.upsert(KEY_LAST_ACCOUNT, transaction.accountId.toString())
        }
        changes.notifyChanged()
    }

    /** Put back a deleted transaction (used by undo). It gets a new id. */
    fun restoreTransaction(transaction: Transaction) {
        with(transaction) {
            txnQueries.insert(accountId, categoryId, payee, amount, date.toEpochDay(), memo)
        }
        changes.notifyChanged()
    }

    fun deleteTransaction(id: Long) {
        txnQueries.delete(id)
        changes.notifyChanged()
    }

    private fun toTransaction(
        id: Long,
        accountId: Long,
        categoryId: String?,
        payee: String,
        amount: Long,
        date: Long,
        memo: String,
    ) = Transaction(id, accountId, categoryId, payee, amount, SimpleDate.fromEpochDay(date), memo)

    companion object {
        const val DEFAULT_CURRENCY = "USD"
        private const val KEY_LAST_ACCOUNT = "last_account_id"
    }
}
