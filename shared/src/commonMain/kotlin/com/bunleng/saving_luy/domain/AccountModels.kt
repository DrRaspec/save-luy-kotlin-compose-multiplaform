package com.bunleng.saving_luy.domain

/**
 * A place money lives (cash, bank account). Only on-budget accounts feed Ready to Assign.
 */
data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    val onBudget: Boolean,
)

enum class AccountType(val label: String) {
    Cash("Cash"),
    Bank("Bank"),
    EWallet("E-wallet"),
}

/**
 * A single money movement. Amount is in minor units: outflow negative, inflow positive.
 * [categoryId] is null for money that goes to Ready to Assign (income).
 */
data class Transaction(
    val id: Long,
    val accountId: Long,
    val categoryId: String?,
    val payee: String,
    val amount: Long,
    val date: SimpleDate,
    val memo: String,
) {
    companion object {
        /** Id used for a transaction that has not been saved yet. */
        const val NEW_ID = 0L
    }
}

enum class TransactionError { MissingAmount, MissingAccount, MissingCategory }

/** Rules for entering a transaction. Pure functions, unit tested. */
object TransactionRules {

    /** Payee used for the inflow that records an account's starting balance. */
    const val STARTING_BALANCE_PAYEE = "Starting balance"

    /** Expenses are stored as negative amounts, income as positive. */
    fun signedAmount(cents: Long, isExpense: Boolean): Long = if (isExpense) -cents else cents

    /** Expenses on on-budget accounts must come out of a category. */
    fun categoryRequired(isExpense: Boolean, accountOnBudget: Boolean): Boolean =
        isExpense && accountOnBudget

    /**
     * Account to preselect for a new transaction: the requested one, else the last used one,
     * else the first on-budget account, else any account.
     */
    fun defaultAccountId(accounts: List<Account>, requestedId: Long?, lastUsedId: Long?): Long? =
        listOf(requestedId, lastUsedId).firstOrNull { id -> id != null && accounts.any { it.id == id } }
            ?: accounts.firstOrNull { it.onBudget }?.id
            ?: accounts.firstOrNull()?.id

    /** Returns the first problem with the entered values, or null if it can be saved. */
    fun validate(
        amountCents: Long,
        account: Account?,
        categoryId: String?,
        isExpense: Boolean,
    ): TransactionError? = when {
        amountCents <= 0 -> TransactionError.MissingAmount
        account == null -> TransactionError.MissingAccount
        categoryRequired(isExpense, account.onBudget) && categoryId == null -> TransactionError.MissingCategory
        else -> null
    }
}
