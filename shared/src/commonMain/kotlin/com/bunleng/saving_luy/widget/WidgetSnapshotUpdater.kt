package com.bunleng.saving_luy.widget

import com.bunleng.saving_luy.data.AccountRepository
import com.bunleng.saving_luy.data.BudgetRepository
import com.bunleng.saving_luy.domain.BudgetMath
import com.bunleng.saving_luy.domain.BudgetMonth
import com.bunleng.saving_luy.domain.currentBudgetMonth

/**
 * Recomputes the widget summary for the current month and hands it to [writer].
 */
class WidgetSnapshotUpdater(
    private val budgetRepository: BudgetRepository,
    private val writer: WidgetSnapshotWriter,
    private val currentMonth: () -> BudgetMonth = ::currentBudgetMonth,
) {

    fun update() {
        val month = currentMonth()
        val allAmounts = budgetRepository.getAllAmounts()
        val categories = budgetRepository.getCategories().map { category ->
            category to BudgetMath.entryFor(category.id, month, allAmounts)
        }
        writer.save(
            WidgetSnapshot.from(
                readyToAssign = BudgetMath.readyToAssign(budgetRepository.getTotalInflow(), allAmounts),
                categories = categories,
                currencyCode = AccountRepository.DEFAULT_CURRENCY,
            )
        )
    }
}
