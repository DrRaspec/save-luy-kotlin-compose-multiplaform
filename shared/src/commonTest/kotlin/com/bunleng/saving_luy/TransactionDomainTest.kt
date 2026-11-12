package com.bunleng.saving_luy

import com.bunleng.saving_luy.domain.Account
import com.bunleng.saving_luy.domain.AccountType
import com.bunleng.saving_luy.domain.BudgetMath
import com.bunleng.saving_luy.domain.BudgetMonth
import com.bunleng.saving_luy.domain.CategoryMonthAmounts
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.domain.Transaction
import com.bunleng.saving_luy.domain.TransactionError
import com.bunleng.saving_luy.domain.TransactionRules
import com.bunleng.saving_luy.ui.components.AmountInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimpleDateTest {

    @Test
    fun epochDay_knownDates() {
        assertEquals(0L, SimpleDate(1970, 1, 1).toEpochDay())
        assertEquals(20_404L, SimpleDate(2025, 11, 12).toEpochDay())
        assertEquals(-1L, SimpleDate(1969, 12, 31).toEpochDay())
    }

    @Test
    fun epochDay_roundTripsAcrossLeapYears() {
        val dates = listOf(
            SimpleDate(2024, 2, 29),
            SimpleDate(2000, 2, 29),
            SimpleDate(2026, 12, 31),
            SimpleDate(2027, 1, 1),
            SimpleDate(1900, 3, 1),
        )
        for (date in dates) {
            assertEquals(date, SimpleDate.fromEpochDay(date.toEpochDay()))
        }
    }

    @Test
    fun epochDay_consecutiveDaysDifferByOne() {
        assertEquals(1L, SimpleDate(2024, 3, 1).toEpochDay() - SimpleDate(2024, 2, 29).toEpochDay())
        assertEquals(1L, SimpleDate(2023, 3, 1).toEpochDay() - SimpleDate(2023, 2, 28).toEpochDay())
    }

    @Test
    fun fromUtcMillis_usesDatePart() {
        val millis = SimpleDate(2026, 11, 12).toEpochDay() * SimpleDate.MILLIS_PER_DAY
        assertEquals(SimpleDate(2026, 11, 12), SimpleDate.fromUtcMillis(millis))
        assertEquals(SimpleDate(2026, 11, 12), SimpleDate.fromUtcMillis(millis + 23 * 3_600_000L))
    }

    @Test
    fun budgetMonthAndDisplay() {
        val date = SimpleDate(2026, 11, 12)
        assertEquals(BudgetMonth(2026, 11), date.budgetMonth)
        assertEquals("Nov 12, 2026", date.displayName())
    }

    @Test
    fun compareTo_ordersByDate() {
        assertTrue(SimpleDate(2026, 1, 31) < SimpleDate(2026, 2, 1))
    }
}

class TransactionRulesTest {

    private val onBudget = Account(1, "Cash", AccountType.Cash, "USD", onBudget = true)
    private val offBudget = Account(2, "Loan", AccountType.Bank, "USD", onBudget = false)

    @Test
    fun signedAmount_expenseIsNegative() {
        assertEquals(-12_34L, TransactionRules.signedAmount(12_34L, isExpense = true))
        assertEquals(12_34L, TransactionRules.signedAmount(12_34L, isExpense = false))
    }

    @Test
    fun categoryRequired_onlyForOnBudgetExpenses() {
        assertTrue(TransactionRules.categoryRequired(isExpense = true, accountOnBudget = true))
        assertFalse(TransactionRules.categoryRequired(isExpense = false, accountOnBudget = true))
        assertFalse(TransactionRules.categoryRequired(isExpense = true, accountOnBudget = false))
    }

    @Test
    fun defaultAccountId_prefersRequestedThenLastUsedThenOnBudget() {
        val accounts = listOf(offBudget, onBudget)
        assertEquals(2L, TransactionRules.defaultAccountId(accounts, requestedId = 2L, lastUsedId = 1L))
        assertEquals(2L, TransactionRules.defaultAccountId(accounts, requestedId = null, lastUsedId = 2L))
        assertEquals(1L, TransactionRules.defaultAccountId(accounts, requestedId = null, lastUsedId = 99L))
        assertEquals(2L, TransactionRules.defaultAccountId(listOf(offBudget), null, null))
        assertNull(TransactionRules.defaultAccountId(emptyList(), null, null))
    }

    @Test
    fun validate_reportsFirstProblem() {
        assertEquals(TransactionError.MissingAmount, TransactionRules.validate(0L, onBudget, "cat", true))
        assertEquals(TransactionError.MissingAccount, TransactionRules.validate(100L, null, "cat", true))
        assertEquals(TransactionError.MissingCategory, TransactionRules.validate(100L, onBudget, null, true))
    }

    @Test
    fun validate_acceptsValidEntries() {
        assertNull(TransactionRules.validate(100L, onBudget, "cat", isExpense = true))
        assertNull(TransactionRules.validate(100L, onBudget, null, isExpense = false))
        assertNull(TransactionRules.validate(100L, offBudget, null, isExpense = true))
    }
}

class TransactionBudgetMathTest {

    private val oct = BudgetMonth(2026, 10)
    private val nov = BudgetMonth(2026, 11)
    private val onBudgetIds = setOf(1L)

    private fun txn(amount: Long, date: SimpleDate, categoryId: String? = null, accountId: Long = 1L) =
        Transaction(Transaction.NEW_ID, accountId, categoryId, "", amount, date, "")

    @Test
    fun totalInflow_countsUncategorizedOnBudgetOnly() {
        val transactions = listOf(
            txn(1000_00L, SimpleDate(2026, 11, 1)),
            txn(-50_00L, SimpleDate(2026, 11, 2), categoryId = "food"),
            txn(500_00L, SimpleDate(2026, 11, 3), accountId = 2L),
        )
        assertEquals(1000_00L, BudgetMath.totalInflow(transactions, onBudgetIds))
    }

    @Test
    fun totalInflow_noTransactions_isZero() {
        assertEquals(0L, BudgetMath.totalInflow(emptyList(), onBudgetIds))
    }

    @Test
    fun monthAmounts_groupsActivityByCategoryAndMonth() {
        val transactions = listOf(
            txn(-10_00L, SimpleDate(2026, 10, 31), categoryId = "food"),
            txn(-20_00L, SimpleDate(2026, 11, 1), categoryId = "food"),
            txn(-5_00L, SimpleDate(2026, 11, 20), categoryId = "food"),
            txn(-99_00L, SimpleDate(2026, 11, 20), categoryId = "food", accountId = 2L),
        )
        val result = BudgetMath.monthAmounts(emptyMap(), transactions, onBudgetIds).toSet()
        assertEquals(
            setOf(
                CategoryMonthAmounts("food", oct, assigned = 0L, activity = -10_00L),
                CategoryMonthAmounts("food", nov, assigned = 0L, activity = -25_00L),
            ),
            result,
        )
    }

    @Test
    fun monthAmounts_mergesAssignedWithActivity() {
        val assigned = mapOf(("food" to nov) to 300_00L, ("rent" to nov) to 1000_00L)
        val transactions = listOf(txn(-40_00L, SimpleDate(2026, 11, 5), categoryId = "food"))
        val result = BudgetMath.monthAmounts(assigned, transactions, onBudgetIds).associateBy { it.categoryId }
        assertEquals(CategoryMonthAmounts("food", nov, 300_00L, -40_00L), result["food"])
        assertEquals(CategoryMonthAmounts("rent", nov, 1000_00L, 0L), result["rent"])
    }

    @Test
    fun monthAmounts_feedsAvailableWithRollover() {
        val assigned = mapOf(("food" to oct) to 100_00L)
        val transactions = listOf(
            txn(-30_00L, SimpleDate(2026, 10, 5), categoryId = "food"),
            txn(-20_00L, SimpleDate(2026, 11, 5), categoryId = "food"),
        )
        val amounts = BudgetMath.monthAmounts(assigned, transactions, onBudgetIds)
        assertEquals(50_00L, BudgetMath.entryFor("food", nov, amounts).available)
    }

    @Test
    fun accountBalances_sumsPerAccount() {
        val transactions = listOf(
            txn(1000_00L, SimpleDate(2026, 11, 1)),
            txn(-50_00L, SimpleDate(2026, 11, 2), categoryId = "food"),
            txn(200_00L, SimpleDate(2026, 11, 3), accountId = 2L),
        )
        assertEquals(mapOf(1L to 950_00L, 2L to 200_00L), BudgetMath.accountBalances(transactions))
    }
}

class AmountInputTest {

    @Test
    fun appendDigit_ignoresLeadingZero() {
        assertEquals("", AmountInput.appendDigit("", '0'))
        assertEquals("50", AmountInput.appendDigit("5", '0'))
    }

    @Test
    fun appendDigit_capsLength() {
        assertEquals("123456789", AmountInput.appendDigit("123456789", '1'))
    }

    @Test
    fun backspaceAndFromCents() {
        assertEquals("12", AmountInput.backspace("123"))
        assertEquals("", AmountInput.backspace(""))
        assertEquals("", AmountInput.fromCents(0L))
        assertEquals("1234", AmountInput.fromCents(1234L))
    }
}
