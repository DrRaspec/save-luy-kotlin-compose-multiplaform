package com.bunleng.saving_luy

import com.bunleng.saving_luy.data.AppContainer
import com.bunleng.saving_luy.domain.BudgetMath
import com.bunleng.saving_luy.domain.BudgetMonth
import com.bunleng.saving_luy.domain.MoveMoneyError
import com.bunleng.saving_luy.domain.MoveMoneyRules
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.ui.budget.BudgetViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveMoneyRulesTest {

    @Test
    fun moveAssigned_shiftsAmount() {
        assertEquals(70_00L to 30_00L, BudgetMath.moveAssigned(100_00L, 0L, 30_00L))
    }

    @Test
    fun moveAssigned_fromCanGoNegative() {
        assertEquals(-10_00L to 10_00L, BudgetMath.moveAssigned(0L, 0L, 10_00L))
    }

    @Test
    fun validate_reportsProblems() {
        assertEquals(MoveMoneyError.MissingFrom, MoveMoneyRules.validate(null, "b", 100L))
        assertEquals(MoveMoneyError.MissingTo, MoveMoneyRules.validate("a", null, 100L))
        assertEquals(MoveMoneyError.SameCategory, MoveMoneyRules.validate("a", "a", 100L))
        assertEquals(MoveMoneyError.MissingAmount, MoveMoneyRules.validate("a", "b", 0L))
        assertNull(MoveMoneyRules.validate("a", "b", 100L))
    }

    @Test
    fun suggestedAmount_coversOverspending() {
        assertEquals(25_00L, MoveMoneyRules.suggestedAmount(-25_00L))
        assertEquals(0L, MoveMoneyRules.suggestedAmount(0L))
        assertEquals(0L, MoveMoneyRules.suggestedAmount(40_00L))
    }
}

class MoveMoneyFlowTest {

    private val nov = BudgetMonth(2026, 11)

    private fun setup(): Pair<AppContainer, BudgetViewModel> {
        val container = testContainer()
        container.addTransaction(1000_00L, SimpleDate(2026, 11, 1))
        container.budgetRepository.updateAssigned(nov, "cat_rent", 600_00L)
        container.budgetRepository.updateAssigned(nov, "cat_food", 100_00L)
        container.addTransaction(-130_00L, SimpleDate(2026, 11, 3), "cat_food") // Food overspent by $30
        return container to BudgetViewModel(container.budgetRepository, container.changes, initialMonth = nov)
    }

    private fun BudgetViewModel.available(id: String) =
        uiState.value.allCategoryRows.first { it.category.id == id }.entry.available

    @Test
    fun repository_moveAssigned_updatesBothCategoriesAndKeepsReadyToAssign() {
        val (container, vm) = setup()
        val before = vm.uiState.value.readyToAssign

        container.budgetRepository.moveAssigned(nov, "cat_rent", "cat_food", 30_00L)

        assertEquals(570_00L, container.budgetRepository.getAssigned(nov, "cat_rent"))
        assertEquals(130_00L, container.budgetRepository.getAssigned(nov, "cat_food"))
        assertEquals(before, vm.uiState.value.readyToAssign)
        assertEquals(0L, vm.available("cat_food"))
    }

    @Test
    fun startMoveMoney_onOverspent_prefillsDestinationAndAmount() {
        val (_, vm) = setup()
        assertEquals(-30_00L, vm.available("cat_food"))

        vm.startMoveMoney("cat_food")

        val move = assertNotNull(vm.uiState.value.moveMoney)
        assertEquals("cat_food", move.toCategoryId)
        assertNull(move.fromCategoryId)
        assertEquals("30.00", move.amountText)
    }

    @Test
    fun startMoveMoney_onFundedCategory_setsSource() {
        val (_, vm) = setup()
        vm.startMoveMoney("cat_rent")
        assertEquals("cat_rent", vm.uiState.value.moveMoney?.fromCategoryId)
        assertNull(vm.uiState.value.moveMoney?.toCategoryId)
    }

    @Test
    fun fullFlow_fixesOverspending() {
        val (_, vm) = setup()
        vm.startMoveMoney("cat_food")
        vm.selectMoveFrom("cat_rent")

        assertTrue(vm.confirmMoveMoney())

        assertNull(vm.uiState.value.moveMoney)
        assertEquals(0L, vm.available("cat_food"))
        assertEquals(570_00L, vm.available("cat_rent"))
    }

    @Test
    fun selectMoveTo_overspent_fillsAmountOnlyWhenEmpty() {
        val (_, vm) = setup()
        vm.startMoveMoney()
        vm.selectMoveTo("cat_food")
        assertEquals("30.00", vm.uiState.value.moveMoney?.amountText)

        vm.startMoveMoney()
        vm.onMoveAmountChange("5")
        vm.selectMoveTo("cat_food")
        assertEquals("5", vm.uiState.value.moveMoney?.amountText)
    }

    @Test
    fun confirm_withSameCategory_showsErrorAndKeepsSheet() {
        val (container, vm) = setup()
        vm.startMoveMoney("cat_rent")
        vm.selectMoveTo("cat_rent")
        vm.onMoveAmountChange("10")

        assertFalse(vm.confirmMoveMoney())
        assertEquals(MoveMoneyError.SameCategory, vm.uiState.value.moveMoney?.error)
        assertEquals(600_00L, container.budgetRepository.getAssigned(nov, "cat_rent"))

        vm.selectMoveTo("cat_fun")
        assertNull(vm.uiState.value.moveMoney?.error)
    }

    @Test
    fun onMoveAmountChange_rejectsInvalid() {
        val (_, vm) = setup()
        vm.startMoveMoney()
        assertTrue(vm.onMoveAmountChange("12.5"))
        assertFalse(vm.onMoveAmountChange("12.555"))
        assertEquals("12.5", vm.uiState.value.moveMoney?.amountText)
    }

    @Test
    fun cancel_closesWithoutChanges() {
        val (container, vm) = setup()
        vm.startMoveMoney("cat_food")
        vm.selectMoveFrom("cat_rent")
        vm.cancelMoveMoney()
        assertNull(vm.uiState.value.moveMoney)
        assertEquals(600_00L, container.budgetRepository.getAssigned(nov, "cat_rent"))
    }
}
