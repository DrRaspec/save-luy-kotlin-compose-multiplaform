package com.bunleng.saving_luy.domain

/**
 * Pure budget math functions. No side effects, no UI, no IO.
 * Every function here should have a unit test.
 */
object BudgetMath {

    /**
     * Ready to Assign = total inflow − sum of all assigned amounts (across all months).
     * @param totalInflow total inflow to on-budget accounts in minor units.
     * @param allAmounts every stored category/month amount.
     * @return the amount still available to assign (can be negative if over-assigned).
     */
    fun readyToAssign(totalInflow: Long, allAmounts: List<CategoryMonthAmounts>): Long {
        val totalAssigned = allAmounts.sumOf { it.assigned }
        return totalInflow - totalAssigned
    }

    /**
     * Previous month's Available for a category. Since Available rolls over in full
     * (including negative), this is the sum of assigned + activity of all earlier months.
     */
    fun carryover(categoryId: String, month: BudgetMonth, allAmounts: List<CategoryMonthAmounts>): Long =
        allAmounts
            .filter { it.categoryId == categoryId && it.month < month }
            .sumOf { it.assigned + it.activity }

    /** Build the month entry for a category, deriving carryover from history. */
    fun entryFor(categoryId: String, month: BudgetMonth, allAmounts: List<CategoryMonthAmounts>): CategoryBudgetEntry {
        val current = allAmounts.firstOrNull { it.categoryId == categoryId && it.month == month }
        return CategoryBudgetEntry(
            categoryId = categoryId,
            assigned = current?.assigned ?: 0L,
            activity = current?.activity ?: 0L,
            carryover = carryover(categoryId, month, allAmounts),
        )
    }

    /**
     * Total inflow to on-budget accounts: transactions without a category on those accounts.
     * Categorized transactions are spending (or refunds) and count as category Activity instead.
     */
    fun totalInflow(transactions: List<Transaction>, onBudgetAccountIds: Set<Long>): Long =
        transactions
            .filter { it.categoryId == null && it.accountId in onBudgetAccountIds }
            .sumOf { it.amount }

    /**
     * Combine stored Assigned amounts with Activity derived from transactions on on-budget
     * accounts, producing one entry per category and month that has either.
     */
    fun monthAmounts(
        assigned: Map<Pair<String, BudgetMonth>, Long>,
        transactions: List<Transaction>,
        onBudgetAccountIds: Set<Long>,
    ): List<CategoryMonthAmounts> {
        val activity = transactions
            .filter { it.categoryId != null && it.accountId in onBudgetAccountIds }
            .groupBy { it.categoryId!! to it.date.budgetMonth }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
        return (assigned.keys + activity.keys).map { key ->
            CategoryMonthAmounts(
                categoryId = key.first,
                month = key.second,
                assigned = assigned[key] ?: 0L,
                activity = activity[key] ?: 0L,
            )
        }
    }

    /** Balance of every account that has transactions, keyed by account id. */
    fun accountBalances(transactions: List<Transaction>): Map<Long, Long> =
        transactions.groupBy { it.accountId }.mapValues { (_, txns) -> txns.sumOf { it.amount } }

    /**
     * Move Money: take [amount] from one category's Assigned and add it to another's.
     * @return the new (from, to) Assigned amounts. From may go negative (moving rolled-over money).
     */
    fun moveAssigned(fromAssigned: Long, toAssigned: Long, amount: Long): Pair<Long, Long> =
        (fromAssigned - amount) to (toAssigned + amount)

    /**
     * Compute available for a single category.
     * Available = carryover + assigned + activity.
     */
    fun categoryAvailable(entry: CategoryBudgetEntry): Long = entry.available
}
