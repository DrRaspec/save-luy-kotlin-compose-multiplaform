package com.bunleng.saving_luy

import com.bunleng.saving_luy.data.AppContainer
import com.bunleng.saving_luy.domain.AccountType
import com.bunleng.saving_luy.domain.BudgetMath
import com.bunleng.saving_luy.domain.BudgetMonth
import com.bunleng.saving_luy.domain.CategoryBudgetEntry
import com.bunleng.saving_luy.domain.CategoryMonthAmounts
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.ui.MoneyFormatter
import com.bunleng.saving_luy.ui.budget.BudgetViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetMathTest {

    private val oct = BudgetMonth(2026, 10)
    private val nov = BudgetMonth(2026, 11)
    private val dec = BudgetMonth(2026, 12)

    @Test
    fun readyToAssign_noEntries_equalsInflow() {
        val result = BudgetMath.readyToAssign(500_00L, emptyList())
        assertEquals(500_00L, result)
    }

    @Test
    fun readyToAssign_withAssignments_subtractsCorrectly() {
        val amounts = listOf(
            CategoryMonthAmounts("a", nov, assigned = 100_00L, activity = 0L),
            CategoryMonthAmounts("b", nov, assigned = 200_00L, activity = 0L),
        )
        val result = BudgetMath.readyToAssign(500_00L, amounts)
        assertEquals(200_00L, result)
    }

    @Test
    fun readyToAssign_overAssigned_returnsNegative() {
        val amounts = listOf(
            CategoryMonthAmounts("a", nov, assigned = 600_00L, activity = 0L),
        )
        val result = BudgetMath.readyToAssign(500_00L, amounts)
        assertEquals(-100_00L, result)
    }

    @Test
    fun readyToAssign_sumsAssignedAcrossAllMonths() {
        val amounts = listOf(
            CategoryMonthAmounts("a", oct, assigned = 100_00L, activity = 0L),
            CategoryMonthAmounts("a", nov, assigned = 150_00L, activity = 0L),
        )
        assertEquals(250_00L, BudgetMath.readyToAssign(500_00L, amounts))
    }

    @Test
    fun carryover_noHistory_isZero() {
        assertEquals(0L, BudgetMath.carryover("a", nov, emptyList()))
    }

    @Test
    fun carryover_sumsEarlierMonthsOnly() {
        val amounts = listOf(
            CategoryMonthAmounts("a", oct, assigned = 100_00L, activity = -40_00L),
            CategoryMonthAmounts("a", nov, assigned = 50_00L, activity = -10_00L),
            CategoryMonthAmounts("a", dec, assigned = 999_00L, activity = 0L),
            CategoryMonthAmounts("b", oct, assigned = 300_00L, activity = 0L),
        )
        assertEquals(100_00L, BudgetMath.carryover("a", dec, amounts))
    }

    @Test
    fun carryover_overspendingRollsOverAsNegative() {
        val amounts = listOf(
            CategoryMonthAmounts("a", oct, assigned = 20_00L, activity = -50_00L),
        )
        assertEquals(-30_00L, BudgetMath.carryover("a", nov, amounts))
    }

    @Test
    fun entryFor_combinesCurrentMonthAndCarryover() {
        val amounts = listOf(
            CategoryMonthAmounts("a", oct, assigned = 100_00L, activity = -30_00L),
            CategoryMonthAmounts("a", nov, assigned = 20_00L, activity = -5_00L),
        )
        val entry = BudgetMath.entryFor("a", nov, amounts)
        assertEquals(CategoryBudgetEntry("a", assigned = 20_00L, activity = -5_00L, carryover = 70_00L), entry)
        assertEquals(85_00L, entry.available)
    }

    @Test
    fun entryFor_monthWithoutData_onlyCarryover() {
        val amounts = listOf(
            CategoryMonthAmounts("a", oct, assigned = 100_00L, activity = 0L),
        )
        val entry = BudgetMath.entryFor("a", dec, amounts)
        assertEquals(0L, entry.assigned)
        assertEquals(100_00L, entry.available)
    }

    @Test
    fun categoryAvailable_basic() {
        val entry = CategoryBudgetEntry("a", assigned = 100_00L, activity = -30_00L, carryover = 50_00L)
        assertEquals(120_00L, BudgetMath.categoryAvailable(entry))
    }

    @Test
    fun categoryAvailable_overspent() {
        val entry = CategoryBudgetEntry("a", assigned = 50_00L, activity = -80_00L, carryover = 0L)
        assertEquals(-30_00L, BudgetMath.categoryAvailable(entry))
    }

    @Test
    fun categoryAvailable_zeroEverything() {
        val entry = CategoryBudgetEntry("a", assigned = 0L, activity = 0L, carryover = 0L)
        assertEquals(0L, BudgetMath.categoryAvailable(entry))
    }
}

class BudgetMonthTest {

    @Test
    fun next_normalMonth() {
        assertEquals(BudgetMonth(2026, 12), BudgetMonth(2026, 11).next())
    }

    @Test
    fun next_december_wrapsToJanuary() {
        assertEquals(BudgetMonth(2027, 1), BudgetMonth(2026, 12).next())
    }

    @Test
    fun previous_normalMonth() {
        assertEquals(BudgetMonth(2026, 10), BudgetMonth(2026, 11).previous())
    }

    @Test
    fun previous_january_wrapsToDecember() {
        assertEquals(BudgetMonth(2025, 12), BudgetMonth(2026, 1).previous())
    }

    @Test
    fun displayName() {
        assertEquals("Nov 2026", BudgetMonth(2026, 11).displayName())
    }

    @Test
    fun key_roundTrips() {
        assertEquals(202611L, BudgetMonth(2026, 11).key)
        assertEquals(BudgetMonth(2026, 1), BudgetMonth.fromKey(202601L))
    }

    @Test
    fun compareTo_sameMonth() {
        assertEquals(0, BudgetMonth(2026, 11).compareTo(BudgetMonth(2026, 11)))
    }

    @Test
    fun compareTo_differentMonths() {
        assertTrue(BudgetMonth(2026, 10) < BudgetMonth(2026, 11))
        assertTrue(BudgetMonth(2027, 1) > BudgetMonth(2026, 12))
    }
}

class MoneyFormatterTest {

    @Test
    fun format_positive() {
        assertEquals("$12.34", MoneyFormatter.format(1234L))
    }

    @Test
    fun format_negative() {
        assertEquals("-$5.00", MoneyFormatter.format(-500L))
    }

    @Test
    fun format_zero() {
        assertEquals("$0.00", MoneyFormatter.format(0L))
    }

    @Test
    fun format_largeAmount() {
        assertEquals("$1,234.56", MoneyFormatter.format(123456L))
    }

    @Test
    fun format_oneCent() {
        assertEquals("$0.01", MoneyFormatter.format(1L))
    }

    @Test
    fun parseCents_acceptsTypedAmounts() {
        assertEquals(0L, MoneyFormatter.parseCents(""))
        assertEquals(12_00L, MoneyFormatter.parseCents("12"))
        assertEquals(12_50L, MoneyFormatter.parseCents("12.5"))
        assertEquals(1234_56L, MoneyFormatter.parseCents("1,234.56"))
        assertEquals(50L, MoneyFormatter.parseCents(".50"))
    }

    @Test
    fun parseCents_rejectsInvalid() {
        assertNull(MoneyFormatter.parseCents("-5"))
        assertNull(MoneyFormatter.parseCents("1.234"))
        assertNull(MoneyFormatter.parseCents("abc"))
    }

    @Test
    fun toEditableText() {
        assertEquals("12.50", MoneyFormatter.toEditableText(1250L))
        assertEquals("0.05", MoneyFormatter.toEditableText(5L))
        assertEquals("", MoneyFormatter.toEditableText(0L))
        assertEquals(1250L, MoneyFormatter.parseCents(MoneyFormatter.toEditableText(1250L)))
    }

    @Test
    fun parseCentsFromDigits() {
        assertEquals(1234L, MoneyFormatter.parseCentsFromDigits("1234"))
        assertEquals(0L, MoneyFormatter.parseCentsFromDigits(""))
        assertEquals(0L, MoneyFormatter.parseCentsFromDigits("invalid"))
    }
}

class BudgetRepositoryTest {

    private val nov = BudgetMonth(2026, 11)

    @Test
    fun seedDefaults_seedsExpectedCategories() {
        val repo = testContainer().budgetRepository

        val groups = repo.getGroups()
        assertEquals(2, groups.size)
        assertEquals("Essentials", groups[0].name)
        assertEquals("Lifestyle", groups[1].name)

        val categories = repo.getCategories()
        assertEquals(5, categories.size)
        val names = categories.map { it.name }
        assertTrue(names.contains("Rent"))
        assertTrue(names.contains("Food"))
        assertTrue(names.contains("Transport"))
        assertTrue(names.contains("Savings"))
        assertTrue(names.contains("Fun"))
    }

    @Test
    fun getCategories_ordersByGroupThenCategory() {
        val names = testContainer().budgetRepository.getCategories().map { it.name }
        assertEquals(listOf("Rent", "Food", "Transport", "Savings", "Fun"), names)
    }

    @Test
    fun ensureSeeded_isIdempotent() {
        val repo = testContainer().budgetRepository
        repo.ensureSeeded()
        assertEquals(5, repo.getCategories().size)
    }

    @Test
    fun updateAssigned_persistsValue() {
        val repo = testContainer().budgetRepository

        repo.updateAssigned(nov, "cat_rent", 1200_00L)
        assertEquals(1200_00L, repo.getAssigned(nov, "cat_rent"))
        assertEquals(1, repo.getAllAmounts().size)

        repo.updateAssigned(nov, "cat_rent", 900_00L)
        assertEquals(900_00L, repo.getAssigned(nov, "cat_rent"))
        assertEquals(1, repo.getAllAmounts().size)
    }

    @Test
    fun getAssigned_doesNotCreateEntries() {
        val repo = testContainer().budgetRepository

        assertEquals(0L, repo.getAssigned(BudgetMonth(2026, 12), "cat_rent"))
        assertTrue(repo.getAllAmounts().isEmpty())
    }

    @Test
    fun totalInflow_sumsIncomeOnBudgetAccountsOnly() {
        val container = testContainer()
        container.accountRepository.addAccount("Brokerage", AccountType.Bank, onBudget = false)
        val offBudgetId = container.accountRepository.getAccounts().first { !it.onBudget }.id

        container.addTransaction(100_00L, SimpleDate(2026, 10, 3))
        container.addTransaction(250_00L, SimpleDate(2026, 11, 1))
        container.addTransaction(-40_00L, SimpleDate(2026, 11, 2), categoryId = "cat_food")
        container.addTransaction(999_00L, SimpleDate(2026, 11, 2), accountId = offBudgetId)

        assertEquals(350_00L, container.budgetRepository.getTotalInflow())
    }

    @Test
    fun getAllAmounts_includesActivityFromTransactions() {
        val container = testContainer()
        container.budgetRepository.updateAssigned(nov, "cat_food", 300_00L)
        container.addTransaction(-40_00L, SimpleDate(2026, 11, 2), categoryId = "cat_food")

        val food = container.budgetRepository.getAllAmounts().single()
        assertEquals(300_00L, food.assigned)
        assertEquals(-40_00L, food.activity)
    }
}

class BudgetViewModelTest {

    private val nov = BudgetMonth(2026, 11)

    /** View model over a fresh database with $2,000 of income in November. */
    private fun newViewModel(container: AppContainer = testContainer()): BudgetViewModel {
        container.addTransaction(200_000L, SimpleDate(2026, 11, 1), payee = "Paycheck")
        return BudgetViewModel(container.budgetRepository, container.changes, initialMonth = nov)
    }

    @Test
    fun initialState_hasSeededCategoriesAndReadyToAssign() {
        val vm = newViewModel()
        val state = vm.uiState.value

        assertEquals(BudgetMonth(2026, 11), state.currentMonth)
        assertEquals(2, state.groupRows.size)
        val totalCategories = state.groupRows.sumOf { it.categoryRows.size }
        assertEquals(5, totalCategories)
        assertEquals(200_000L, state.readyToAssign)
    }

    @Test
    fun monthNavigation_updatesMonth() {
        val vm = newViewModel()
        vm.goToNextMonth()
        assertEquals(BudgetMonth(2026, 12), vm.uiState.value.currentMonth)

        vm.goToPreviousMonth()
        assertEquals(BudgetMonth(2026, 11), vm.uiState.value.currentMonth)
    }

    @Test
    fun nextMonth_keepsReadyToAssignAndRollsOverAvailable() {
        val vm = newViewModel()
        vm.startEditAssigned("cat_rent")
        "50000".forEach(vm::onEditDigit)
        vm.confirmEdit()

        vm.goToNextMonth()
        val state = vm.uiState.value
        assertEquals(150_000L, state.readyToAssign)
        val rent = state.groupRows.flatMap { it.categoryRows }.first { it.category.id == "cat_rent" }
        assertEquals(0L, rent.entry.assigned)
        assertEquals(500_00L, rent.entry.available)
    }

    @Test
    fun editDigit_ignoresLeadingZero() {
        val vm = newViewModel()
        vm.startEditAssigned("cat_food")
        vm.onEditDigit('0')
        vm.onEditDigit('5')
        assertEquals("5", vm.uiState.value.editingCategory?.inputDigits)
    }

    @Test
    fun startEditAssigned_unknownCategory_doesNothing() {
        val vm = newViewModel()
        vm.startEditAssigned("missing")
        assertNull(vm.uiState.value.editingCategory)
    }

    @Test
    fun editAssignedFlow_updatesAssignedAndReadyToAssign() {
        val vm = newViewModel()
        vm.startEditAssigned("cat_rent")

        assertNotNull(vm.uiState.value.editingCategory)
        assertEquals("Rent", vm.uiState.value.editingCategory?.category?.name)

        // Type 5, 0, 0, 0, 0 -> 50000 cents ($500.00)
        vm.onEditClear()
        vm.onEditDigit('5')
        vm.onEditDigit('0')
        vm.onEditDigit('0')
        vm.onEditDigit('0')
        vm.onEditDigit('0')
        assertEquals("50000", vm.uiState.value.editingCategory?.inputDigits)

        // Backspace once -> "5000" ($50.00)
        vm.onEditBackspace()
        assertEquals("5000", vm.uiState.value.editingCategory?.inputDigits)

        // Add '0' back -> "50000" ($500.00)
        vm.onEditDigit('0')

        // Confirm
        vm.confirmEdit()
        assertNull(vm.uiState.value.editingCategory)

        // Ready to assign was 200_000, now should be 150_000
        assertEquals(150_000L, vm.uiState.value.readyToAssign)

        val rentRow = vm.uiState.value.groupRows
            .flatMap { it.categoryRows }
            .first { it.category.id == "cat_rent" }
        assertEquals(500_00L, rentRow.entry.assigned)
        assertEquals(500_00L, rentRow.entry.available)
    }

    @Test
    fun initialState_withoutIncome_readyToAssignIsZero() {
        val container = testContainer()
        val vm = BudgetViewModel(container.budgetRepository, container.changes, initialMonth = nov)
        assertEquals(0L, vm.uiState.value.readyToAssign)
    }

    @Test
    fun newTransaction_refreshesActivityAndAvailable() {
        val container = testContainer()
        val vm = newViewModel(container)
        vm.startEditAssigned("cat_food")
        "30000".forEach(vm::onEditDigit)
        vm.confirmEdit()

        container.addTransaction(-45_00L, SimpleDate(2026, 11, 5), categoryId = "cat_food")

        val food = vm.uiState.value.groupRows.flatMap { it.categoryRows }.first { it.category.id == "cat_food" }
        assertEquals(-45_00L, food.entry.activity)
        assertEquals(255_00L, food.entry.available)
        assertEquals(170_000L, vm.uiState.value.readyToAssign)
    }

    @Test
    fun overspending_showsNegativeAvailable() {
        val container = testContainer()
        val vm = newViewModel(container)
        container.addTransaction(-20_00L, SimpleDate(2026, 11, 5), categoryId = "cat_fun")

        val funRow = vm.uiState.value.groupRows.flatMap { it.categoryRows }.first { it.category.id == "cat_fun" }
        assertEquals(-20_00L, funRow.entry.available)
    }

    @Test
    fun toggleGroupExpanded_togglesState() {
        val vm = newViewModel()
        val groupId = vm.uiState.value.groupRows.first().group.id

        assertTrue(vm.uiState.value.groupRows.first().isExpanded)
        vm.toggleGroupExpanded(groupId)
        assertEquals(false, vm.uiState.value.groupRows.first { it.group.id == groupId }.isExpanded)
        vm.toggleGroupExpanded(groupId)
        assertEquals(true, vm.uiState.value.groupRows.first { it.group.id == groupId }.isExpanded)
    }
}
