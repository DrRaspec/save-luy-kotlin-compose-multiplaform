package com.bunleng.saving_luy

import com.bunleng.saving_luy.data.AppContainer
import com.bunleng.saving_luy.domain.AccountType
import com.bunleng.saving_luy.domain.BudgetHint
import com.bunleng.saving_luy.domain.BudgetMath
import com.bunleng.saving_luy.domain.BudgetMonth
import com.bunleng.saving_luy.domain.CategoryRules
import com.bunleng.saving_luy.domain.NameError
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.domain.Transaction
import com.bunleng.saving_luy.ui.budget.BudgetViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryRulesTest {

    @Test
    fun validateName_reportsProblems() {
        assertEquals(NameError.Blank, CategoryRules.validateName("   ", emptyList()))
        assertEquals(NameError.TooLong, CategoryRules.validateName("x".repeat(41), emptyList()))
        assertEquals(NameError.Duplicate, CategoryRules.validateName(" food ", listOf("Food")))
        assertNull(CategoryRules.validateName("x".repeat(40), emptyList()))
        assertNull(CategoryRules.validateName("Gym", listOf("Food")))
    }

    @Test
    fun mergeAssigned_addsMonthByMonth() {
        val merged = CategoryRules.mergeAssigned(
            from = mapOf(202610L to 10_00L, 202611L to 5_00L),
            into = mapOf(202611L to 20_00L, 202612L to 1_00L),
        )
        assertEquals(mapOf(202610L to 10_00L, 202611L to 25_00L, 202612L to 1_00L), merged)
    }

    @Test
    fun hint_overspentWinsOverReadyToAssign() {
        assertEquals(BudgetHint.Overspent(2, "Food"), BudgetHint.of(30_00L, listOf("Food", "Fun")))
        assertEquals(BudgetHint.Unassigned, BudgetHint.of(30_00L, emptyList()))
        assertEquals(BudgetHint.OverAssigned, BudgetHint.of(-1L, emptyList()))
        assertNull(BudgetHint.of(0L, emptyList()))
    }

    @Test
    fun onBudgetBalance_countsEveryTransactionOnBudgetAccountsOnly() {
        val date = SimpleDate(2026, 11, 1)
        val txns = listOf(
            Transaction(1, accountId = 1, categoryId = null, payee = "", amount = 30_00L, date = date, memo = ""),
            Transaction(2, accountId = 1, categoryId = "cat_food", payee = "", amount = -1_00L, date = date, memo = ""),
            Transaction(3, accountId = 2, categoryId = null, payee = "", amount = 500_00L, date = date, memo = ""),
        )
        assertEquals(29_00L, BudgetMath.onBudgetBalance(txns, onBudgetAccountIds = setOf(1L)))
    }
}

class CategoryManagementRepositoryTest {

    private val nov = BudgetMonth(2026, 11)

    @Test
    fun addCategory_goesToEndOfGroup() {
        val container = testContainer()
        val id = container.budgetRepository.addCategory("grp_essentials", "Phone")
        val names = container.budgetRepository.getCategoriesForGroup("grp_essentials").map { it.name }
        assertEquals(listOf("Rent", "Food", "Transport", "Phone"), names)
        assertTrue(id.startsWith("cat_"))
    }

    @Test
    fun addGroup_goesToEnd_andRenameWorks() {
        val container = testContainer()
        val id = container.budgetRepository.addGroup("Goals")
        container.budgetRepository.renameGroup(id, "Big goals")
        assertEquals(listOf("Essentials", "Lifestyle", "Big goals"), container.budgetRepository.getGroups().map { it.name })
    }

    @Test
    fun deleteGroup_refusedWhileItHasCategories() {
        val container = testContainer()
        assertFalse(container.budgetRepository.deleteGroup("grp_lifestyle"))
        val empty = container.budgetRepository.addGroup("Empty")
        assertTrue(container.budgetRepository.deleteGroup(empty))
        assertEquals(2, container.budgetRepository.getGroups().size)
    }

    @Test
    fun deleteCategory_withoutHistory_needsNoTarget() {
        val container = testContainer()
        assertFalse(container.budgetRepository.categoryHasHistory("cat_fun"))
        assertTrue(container.budgetRepository.deleteCategory("cat_fun", moveToCategoryId = null))
        assertTrue(container.budgetRepository.getCategories().none { it.id == "cat_fun" })
    }

    @Test
    fun deleteCategory_withHistory_refusedWithoutTarget() {
        val container = testContainer()
        container.addTransaction(-1_00L, SimpleDate(2026, 11, 2), "cat_food")
        assertTrue(container.budgetRepository.categoryHasHistory("cat_food"))
        assertFalse(container.budgetRepository.deleteCategory("cat_food", moveToCategoryId = null))
        assertFalse(container.budgetRepository.deleteCategory("cat_food", moveToCategoryId = "cat_food"))
        assertTrue(container.budgetRepository.getCategories().any { it.id == "cat_food" })
    }

    @Test
    fun deleteCategory_movesMoneySoTotalsDoNotChange() {
        val container = testContainer()
        val repo = container.budgetRepository
        container.addTransaction(100_00L, SimpleDate(2026, 10, 1))
        repo.updateAssigned(BudgetMonth(2026, 10), "cat_food", 20_00L)
        repo.updateAssigned(nov, "cat_food", 10_00L)
        repo.updateAssigned(nov, "cat_fun", 5_00L)
        container.addTransaction(-7_00L, SimpleDate(2026, 11, 3), "cat_food")
        val readyBefore = BudgetMath.readyToAssign(repo.getTotalInflow(), repo.getAllAmounts())

        assertTrue(repo.deleteCategory("cat_food", moveToCategoryId = "cat_fun"))

        val amounts = repo.getAllAmounts()
        assertEquals(readyBefore, BudgetMath.readyToAssign(repo.getTotalInflow(), amounts))
        assertTrue(amounts.none { it.categoryId == "cat_food" })
        assertEquals(15_00L, repo.getAssigned(nov, "cat_fun"))
        assertEquals(20_00L, repo.getAssigned(BudgetMonth(2026, 10), "cat_fun"))
        // Fun now holds Food's 30 assigned minus 7 spent, plus its own 5.
        assertEquals(28_00L, BudgetMath.entryFor("cat_fun", nov, amounts).available)
        assertEquals("cat_fun", container.accountRepository.getTransactions().single { it.amount == -7_00L }.categoryId)
    }

    @Test
    fun onBudgetBalance_ignoresOffBudgetAccounts() {
        val container = testContainer()
        container.addTransaction(30_00L, SimpleDate(2026, 11, 1))
        container.addTransaction(-1_00L, SimpleDate(2026, 11, 2), "cat_food")
        container.accountRepository.addAccount(
            "Loan", AccountType.Bank, onBudget = false, startingBalance = 500_00L, date = SimpleDate(2026, 11, 1),
        )
        assertEquals(29_00L, container.budgetRepository.getOnBudgetBalance())
    }
}

class CategoryManagementViewModelTest {

    private val nov = BudgetMonth(2026, 11)

    private fun newViewModel(container: AppContainer = testContainer()): BudgetViewModel {
        container.addTransaction(30_00L, SimpleDate(2026, 11, 1), payee = "Paycheck")
        return BudgetViewModel(container.budgetRepository, container.changes, initialMonth = nov)
    }

    @Test
    fun addCategory_showsInGroup() {
        val vm = newViewModel()
        vm.startAddCategory("grp_lifestyle")
        vm.onNameChange("  Gym ")
        assertTrue(vm.confirmNameDialog())

        val state = vm.uiState.value
        assertNull(state.nameDialog)
        val lifestyle = state.groupRows.first { it.group.id == "grp_lifestyle" }
        assertEquals("Gym", lifestyle.categoryRows.last().category.name)
    }

    @Test
    fun duplicateName_keepsDialogOpenWithError() {
        val vm = newViewModel()
        vm.startAddCategory("grp_lifestyle")
        vm.onNameChange("food")
        assertFalse(vm.confirmNameDialog())
        assertEquals(NameError.Duplicate, vm.uiState.value.nameDialog?.error)

        vm.onNameChange("Snacks")
        assertNull(vm.uiState.value.nameDialog?.error)
    }

    @Test
    fun renameCategory_toSameName_isAllowed() {
        val vm = newViewModel()
        vm.startRenameCategory("cat_food")
        assertEquals("Food", vm.uiState.value.nameDialog?.name)
        vm.onNameChange("Groceries")
        assertTrue(vm.confirmNameDialog())
        assertEquals("Groceries", vm.uiState.value.allCategoryRows.first { it.category.id == "cat_food" }.category.name)

        vm.startRenameCategory("cat_food")
        assertTrue(vm.confirmNameDialog())
    }

    @Test
    fun addAndRenameGroup() {
        val vm = newViewModel()
        vm.startAddGroup()
        vm.onNameChange("Goals")
        assertTrue(vm.confirmNameDialog())
        val goals = vm.uiState.value.groupRows.last().group
        assertEquals("Goals", goals.name)

        vm.startRenameGroup(goals.id)
        vm.onNameChange("essentials")
        assertFalse(vm.confirmNameDialog())
        assertEquals(NameError.Duplicate, vm.uiState.value.nameDialog?.error)
    }

    @Test
    fun deleteCategoryWithHistory_requiresTarget() {
        val container = testContainer()
        val vm = newViewModel(container)
        container.addTransaction(-1_00L, SimpleDate(2026, 11, 2), "cat_food")

        vm.requestDeleteCategory("cat_food")
        val delete = assertNotNull(vm.uiState.value.deleteCategory)
        assertTrue(delete.hasHistory)
        assertFalse(delete.canDelete)
        assertFalse(vm.confirmDeleteCategory())

        vm.selectDeleteMoveTo("cat_fun")
        assertTrue(vm.confirmDeleteCategory())
        val state = vm.uiState.value
        assertNull(state.deleteCategory)
        assertTrue(state.allCategoryRows.none { it.category.id == "cat_food" })
        assertEquals(-1_00L, state.allCategoryRows.first { it.category.id == "cat_fun" }.entry.available)
    }

    @Test
    fun deleteGroup_onlyWhenEmpty() {
        val vm = newViewModel()
        vm.requestDeleteGroup("grp_lifestyle")
        assertFalse(assertNotNull(vm.uiState.value.deleteGroup).isEmpty)
        assertFalse(vm.confirmDeleteGroup())
        assertEquals(2, vm.uiState.value.groupRows.size)

        vm.startAddGroup()
        vm.onNameChange("Empty")
        vm.confirmNameDialog()
        val empty = vm.uiState.value.groupRows.last().group.id
        vm.requestDeleteGroup(empty)
        assertTrue(vm.confirmDeleteGroup())
        assertEquals(2, vm.uiState.value.groupRows.size)
    }

    @Test
    fun hintAndBalance_followSpending() {
        val container = testContainer()
        val vm = newViewModel(container)
        assertEquals(BudgetHint.Unassigned, vm.uiState.value.hint)
        assertEquals(30_00L, vm.uiState.value.onBudgetBalance)

        container.addTransaction(-1_00L, SimpleDate(2026, 11, 2), "cat_food")
        assertEquals(BudgetHint.Overspent(1, "Food"), vm.uiState.value.hint)
        assertEquals(29_00L, vm.uiState.value.onBudgetBalance)
        assertEquals(30_00L, vm.uiState.value.readyToAssign)

        container.budgetRepository.updateAssigned(nov, "cat_food", 30_00L)
        assertNull(vm.uiState.value.hint)
    }
}
