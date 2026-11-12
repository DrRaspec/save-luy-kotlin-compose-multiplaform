package com.bunleng.saving_luy.data

import com.bunleng.saving_luy.data.db.MoneyDatabase
import com.bunleng.saving_luy.domain.BudgetCategory
import com.bunleng.saving_luy.domain.BudgetMath
import com.bunleng.saving_luy.domain.BudgetMonth
import com.bunleng.saving_luy.domain.CategoryGroup
import com.bunleng.saving_luy.domain.CategoryMonthAmounts
import com.bunleng.saving_luy.domain.CategoryRules
import kotlin.random.Random

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

    /** Cash currently in on-budget accounts. */
    fun getOnBudgetBalance(): Long =
        BudgetMath.onBudgetBalance(accountRepository.getTransactions(), onBudgetAccountIds())

    // ---- category and group management ----

    fun addGroup(name: String): String {
        val id = newId(GROUP_PREFIX) { id -> getGroups().any { it.id == id } }
        database.categoryGroupQueries.insert(id, name, database.categoryGroupQueries.nextSortOrder().executeAsOne())
        changes.notifyChanged()
        return id
    }

    fun renameGroup(groupId: String, name: String) {
        database.categoryGroupQueries.rename(name, groupId)
        changes.notifyChanged()
    }

    /** Only empty groups can be deleted, so no category or money is lost. @return true if deleted. */
    fun deleteGroup(groupId: String): Boolean {
        val deleted = database.transactionWithResult {
            if (database.categoryQueries.countByGroup(groupId).executeAsOne() > 0) return@transactionWithResult false
            database.categoryGroupQueries.delete(groupId)
            true
        }
        if (deleted) changes.notifyChanged()
        return deleted
    }

    fun addCategory(groupId: String, name: String): String {
        val id = newId(CATEGORY_PREFIX) { id -> getCategories().any { it.id == id } }
        database.categoryQueries.insert(id, groupId, name, database.categoryQueries.nextSortOrder(groupId).executeAsOne())
        changes.notifyChanged()
        return id
    }

    fun renameCategory(categoryId: String, name: String) {
        database.categoryQueries.rename(name, categoryId)
        changes.notifyChanged()
    }

    /** True if the category has transactions or Assigned amounts that must go somewhere on delete. */
    fun categoryHasHistory(categoryId: String): Boolean =
        database.txnQueries.countByCategory(categoryId).executeAsOne() > 0 ||
            database.assignedQueries.selectByCategory(categoryId).executeAsList().isNotEmpty()

    /**
     * Delete a category in one DB transaction. Its transactions and Assigned amounts move to
     * [moveToCategoryId], so balances, Ready to Assign and total Available do not change.
     * [moveToCategoryId] may be null only when the category has no history.
     * @return true if deleted.
     */
    fun deleteCategory(categoryId: String, moveToCategoryId: String?): Boolean {
        if (categoryId == moveToCategoryId) return false
        val deleted = database.transactionWithResult {
            if (moveToCategoryId == null) {
                if (categoryHasHistory(categoryId)) return@transactionWithResult false
            } else {
                database.txnQueries.moveCategory(moveToCategoryId, categoryId)
                val merged = CategoryRules.mergeAssigned(
                    from = assignedByMonth(categoryId),
                    into = assignedByMonth(moveToCategoryId),
                )
                merged.forEach { (month, amount) -> database.assignedQueries.upsert(moveToCategoryId, month, amount) }
            }
            database.assignedQueries.deleteByCategory(categoryId)
            database.categoryQueries.delete(categoryId)
            true
        }
        if (deleted) changes.notifyChanged()
        return deleted
    }

    /** Total inflow to on-budget accounts across all months. */
    fun getTotalInflow(): Long =
        BudgetMath.totalInflow(accountRepository.getTransactions(), onBudgetAccountIds())

    // ---- private ----

    private fun assignedByMonth(categoryId: String): Map<Long, Long> =
        database.assignedQueries.selectByCategory(categoryId) { month, amount -> month to amount }
            .executeAsList().toMap()

    /** Random id like "cat_3f9a1c2b", retried until [isTaken] says it is free. */
    private fun newId(prefix: String, isTaken: (String) -> Boolean): String {
        while (true) {
            val id = prefix + Random.nextInt().toUInt().toString(16)
            if (!isTaken(id)) return id
        }
    }

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

    private companion object {
        const val GROUP_PREFIX = "grp_"
        const val CATEGORY_PREFIX = "cat_"
    }
}
