package com.bunleng.saving_luy.data

import com.bunleng.saving_luy.data.db.MoneyDatabase
import com.bunleng.saving_luy.domain.BudgetCategory
import com.bunleng.saving_luy.domain.BudgetMath
import com.bunleng.saving_luy.domain.BudgetMonth
import com.bunleng.saving_luy.domain.CategoryGroup
import com.bunleng.saving_luy.domain.CategoryMonthAmounts

/**
 * Categories and Assigned amounts, stored in SQLite via SQLDelight.
 * Activity and inflow are derived from transactions in [AccountRepository].
 */
class BudgetRepository(
    private val database: MoneyDatabase,
    private val accountRepository: AccountRepository,
    private val changes: DataChangeNotifier,
) {

    /** Ensure default categories exist. Idempotent. */
    fun ensureSeeded() {
        if (database.categoryGroupQueries.count().executeAsOne() > 0) return
        seedDefaults()
    }

    fun getGroups(): List<CategoryGroup> =
        database.categoryGroupQueries.selectAll { id, name, sortOrder ->
            CategoryGroup(id, name, sortOrder.toInt())
        }.executeAsList()

    fun getCategories(): List<BudgetCategory> =
        database.categoryQueries.selectAll { id, groupId, name, sortOrder ->
            BudgetCategory(id, groupId, name, sortOrder.toInt())
        }.executeAsList()

    fun getCategoriesForGroup(groupId: String): List<BudgetCategory> =
        getCategories().filter { it.groupId == groupId }

    /** Assigned and Activity for every category/month that has either. */
    fun getAllAmounts(): List<CategoryMonthAmounts> {
        val assigned = database.assignedQueries.selectAll { categoryId, month, amount ->
            (categoryId to BudgetMonth.fromKey(month)) to amount
        }.executeAsList().toMap()
        return BudgetMath.monthAmounts(assigned, accountRepository.getTransactions(), onBudgetAccountIds())
    }

    fun getAssigned(month: BudgetMonth, categoryId: String): Long =
        database.assignedQueries.selectAmount(categoryId, month.key).executeAsOneOrNull() ?: 0L

    fun updateAssigned(month: BudgetMonth, categoryId: String, newAssigned: Long) {
        database.assignedQueries.upsert(categoryId, month.key, newAssigned)
        changes.notifyChanged()
    }

    /** Move Money: decrease one category's Assigned and increase another's, in one DB transaction. */
    fun moveAssigned(month: BudgetMonth, fromCategoryId: String, toCategoryId: String, amount: Long) {
        database.transaction {
            val (newFrom, newTo) = BudgetMath.moveAssigned(
                fromAssigned = getAssigned(month, fromCategoryId),
                toAssigned = getAssigned(month, toCategoryId),
                amount = amount,
            )
            database.assignedQueries.upsert(fromCategoryId, month.key, newFrom)
            database.assignedQueries.upsert(toCategoryId, month.key, newTo)
        }
        changes.notifyChanged()
    }

    /** Total inflow to on-budget accounts across all months. */
    fun getTotalInflow(): Long =
        BudgetMath.totalInflow(accountRepository.getTransactions(), onBudgetAccountIds())

    // ---- private ----

    private fun onBudgetAccountIds(): Set<Long> =
        accountRepository.getAccounts().filter { it.onBudget }.map { it.id }.toSet()

    /**
     * Seed the five default categories into two groups.
     * Groups: "Essentials" (Rent, Food, Transport) and "Lifestyle" (Savings, Fun).
     */
    private fun seedDefaults() {
        val groups = database.categoryGroupQueries
        val categories = database.categoryQueries
        database.transaction {
            groups.insert("grp_essentials", "Essentials", 0)
            groups.insert("grp_lifestyle", "Lifestyle", 1)
            categories.insert("cat_rent", "grp_essentials", "Rent", 0)
            categories.insert("cat_food", "grp_essentials", "Food", 1)
            categories.insert("cat_transport", "grp_essentials", "Transport", 2)
            categories.insert("cat_savings", "grp_lifestyle", "Savings", 0)
            categories.insert("cat_fun", "grp_lifestyle", "Fun", 1)
        }
    }
}
