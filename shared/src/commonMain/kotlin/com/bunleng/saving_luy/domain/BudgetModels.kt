package com.bunleng.saving_luy.domain

/**
 * A category group (e.g. Bills, Needs, Wants, Savings).
 * Groups are collapsible in the UI.
 */
data class CategoryGroup(
    val id: String,
    val name: String,
    val sortOrder: Int,
)

/**
 * A single budget category within a group.
 * All monetary values are Long in minor units (cents).
 */
data class BudgetCategory(
    val id: String,
    val groupId: String,
    val name: String,
    val sortOrder: Int,
)

/**
 * The per-month budget data for one category.
 * Amounts are Long in minor units (cents). Outflow = negative, inflow = positive.
 */
data class CategoryBudgetEntry(
    val categoryId: String,
    /** Amount the user assigned this month (always >= 0 in normal use). */
    val assigned: Long,
    /** Sum of transactions for this category in this month. */
    val activity: Long,
    /** Previous month's Available that rolled over. */
    val carryover: Long,
) {
    /**
     * Available = carryover + assigned + activity.
     * Positive = funded, zero = zero, negative = overspent.
     */
    val available: Long get() = carryover + assigned + activity
}

/**
 * Raw stored amounts for one category in one month (carryover is derived, not stored).
 * Amounts are Long in minor units (cents).
 */
data class CategoryMonthAmounts(
    val categoryId: String,
    val month: BudgetMonth,
    val assigned: Long,
    val activity: Long,
)

/**
 * Represents a year-month pair for the budget.
 */
data class BudgetMonth(
    val year: Int,
    val month: Int, // 1-12
) : Comparable<BudgetMonth> {
    override fun compareTo(other: BudgetMonth): Int {
        val yearCmp = year.compareTo(other.year)
        return if (yearCmp != 0) yearCmp else month.compareTo(other.month)
    }

    fun next(): BudgetMonth =
        if (month == 12) BudgetMonth(year + 1, 1) else BudgetMonth(year, month + 1)

    fun previous(): BudgetMonth =
        if (month == 1) BudgetMonth(year - 1, 12) else BudgetMonth(year, month - 1)

    /** Stable numeric key used in the DB, e.g. 202611. */
    val key: Long get() = year * 100L + month

    /** Short display name, e.g. "Nov 2026". */
    fun displayName(): String = "${monthName()} $year"

    /** Short month name, e.g. "Nov". */
    fun monthName(): String = MONTH_NAMES[month - 1]

    companion object {
        fun fromKey(key: Long): BudgetMonth = BudgetMonth((key / 100).toInt(), (key % 100).toInt())

        private val MONTH_NAMES = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
    }
}
