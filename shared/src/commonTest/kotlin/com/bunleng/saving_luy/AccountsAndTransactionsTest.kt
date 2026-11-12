package com.bunleng.saving_luy

import com.bunleng.saving_luy.data.AppContainer
import com.bunleng.saving_luy.domain.AccountType
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.domain.TransactionError
import com.bunleng.saving_luy.ui.accounts.AccountsViewModel
import com.bunleng.saving_luy.ui.transaction.AddTransactionViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountRepositoryTest {

    @Test
    fun seedsOneEmptyCashAccount() {
        val container = testContainer()
        val accounts = container.accountRepository.getAccounts()
        assertEquals(1, accounts.size)
        assertEquals("Cash", accounts[0].name)
        assertEquals("USD", accounts[0].currencyCode)
        assertTrue(accounts[0].onBudget)
        assertTrue(container.accountRepository.getTransactions().isEmpty())
    }

    @Test
    fun transactionsForAccount_newestFirst() {
        val container = testContainer()
        container.addTransaction(-1_00L, SimpleDate(2026, 11, 1), "cat_food", payee = "old")
        container.addTransaction(-2_00L, SimpleDate(2026, 11, 10), "cat_food", payee = "new")
        container.addTransaction(-3_00L, SimpleDate(2026, 11, 5), "cat_food", payee = "middle")

        val payees = container.accountRepository.getTransactionsForAccount(container.cashAccountId()).map { it.payee }
        assertEquals(listOf("new", "middle", "old"), payees)
    }

    @Test
    fun saveTransaction_updatesExisting() {
        val container = testContainer()
        container.addTransaction(-1_00L, SimpleDate(2026, 11, 1), "cat_food")
        val saved = container.accountRepository.getTransactions().single()

        container.accountRepository.saveTransaction(saved.copy(amount = -9_00L, memo = "fixed"))

        val updated = container.accountRepository.getTransactions().single()
        assertEquals(-9_00L, updated.amount)
        assertEquals("fixed", updated.memo)
        assertEquals(SimpleDate(2026, 11, 1), updated.date)
    }

    @Test
    fun addAccount_startingBalanceIsInflowToReadyToAssign() {
        val container = testContainer()
        val id = container.accountRepository.addAccount(
            "ABA", AccountType.Bank, onBudget = true, startingBalance = 750_00L, date = SimpleDate(2026, 11, 12),
        )

        val txn = container.accountRepository.getTransactionsForAccount(id).single()
        assertEquals(750_00L, txn.amount)
        assertNull(txn.categoryId)
        assertEquals("Starting balance", txn.payee)
        assertEquals(750_00L, container.budgetRepository.getTotalInflow())
        assertEquals(AccountType.Bank, container.accountRepository.getAccounts().first { it.id == id }.type)
    }

    @Test
    fun addAccount_zeroStartingBalance_addsNoTransaction() {
        val container = testContainer()
        val id = container.accountRepository.addAccount("Wing", AccountType.EWallet, onBudget = true)
        assertTrue(container.accountRepository.getTransactionsForAccount(id).isEmpty())
    }

    @Test
    fun saveTransaction_remembersLastUsedAccount() {
        val container = testContainer()
        assertNull(container.accountRepository.getLastUsedAccountId())
        val bankId = container.accountRepository.addAccount("Bank", AccountType.Bank, onBudget = true)
        container.addTransaction(-1_00L, SimpleDate(2026, 11, 1), "cat_food", accountId = bankId)
        assertEquals(bankId, container.accountRepository.getLastUsedAccountId())
    }

    @Test
    fun deleteTransaction_removesItAndUpdatesBalance() {
        val container = testContainer()
        container.addTransaction(100_00L, SimpleDate(2026, 11, 1))
        container.addTransaction(-30_00L, SimpleDate(2026, 11, 2), "cat_food")
        val expense = container.accountRepository.getTransactions().first { it.amount < 0 }

        container.accountRepository.deleteTransaction(expense.id)

        assertEquals(listOf(100_00L), container.accountRepository.getTransactions().map { it.amount })
        assertEquals(100_00L, container.budgetRepository.getTotalInflow())
    }

    @Test
    fun deleteAccount_removesItsTransactions() {
        val container = testContainer()
        val repo = container.accountRepository
        val bankId = repo.addAccount("Bank", AccountType.Bank, onBudget = true)
        container.addTransaction(100_00L, SimpleDate(2026, 11, 1), accountId = bankId)
        container.addTransaction(50_00L, SimpleDate(2026, 11, 1))

        repo.deleteAccount(bankId)

        assertEquals(listOf("Cash"), repo.getAccounts().map { it.name })
        assertEquals(listOf(50_00L), repo.getTransactions().map { it.amount })
    }
}

class AddTransactionViewModelTest {

    private val today = SimpleDate(2026, 11, 12)

    private fun setup(): Pair<AppContainer, AddTransactionViewModel> {
        val container = testContainer()
        val vm = AddTransactionViewModel(container.accountRepository, container.budgetRepository, today = { today })
        return container to vm
    }

    @Test
    fun startNew_defaultsToExpenseTodayAndCashAccount() {
        val (container, vm) = setup()
        assertNull(vm.uiState.value)

        vm.startNew()
        val form = assertNotNull(vm.uiState.value)
        assertTrue(form.isExpense)
        assertEquals(today, form.date)
        assertEquals(container.cashAccountId(), form.accountId)
        assertEquals(5, form.categoryGroups.sumOf { it.categories.size })
    }

    @Test
    fun saveExpense_storesNegativeAmountAndCloses() {
        val (container, vm) = setup()
        vm.startNew()
        vm.onAmountChange("12.50")
        vm.onPayeeChange("  Market ")
        vm.selectCategory("cat_food")

        assertTrue(vm.save())
        assertNull(vm.uiState.value)
        val saved = container.accountRepository.getTransactions().single()
        assertEquals(-12_50L, saved.amount)
        assertEquals("Market", saved.payee)
        assertEquals("cat_food", saved.categoryId)
        assertEquals(today, saved.date)
    }

    @Test
    fun startNew_usesLastUsedOrRequestedAccount() {
        val (container, vm) = setup()
        val bankId = container.accountRepository.addAccount("Bank", AccountType.Bank, onBudget = true)
        container.addTransaction(-1_00L, SimpleDate(2026, 11, 1), "cat_food", accountId = bankId)

        vm.startNew()
        assertEquals(bankId, vm.uiState.value?.accountId)

        vm.startNew(accountId = container.cashAccountId())
        assertEquals(container.cashAccountId(), vm.uiState.value?.accountId)
    }

    @Test
    fun saveIncome_goesToReadyToAssign() {
        val (container, vm) = setup()
        vm.startNew()
        vm.setExpense(false)
        vm.selectCategory("cat_food") // ignored for income
        vm.onAmountChange("1000")

        assertTrue(vm.save())
        val saved = container.accountRepository.getTransactions().single()
        assertEquals(1000_00L, saved.amount)
        assertNull(saved.categoryId)
        assertEquals(1000_00L, container.budgetRepository.getTotalInflow())
    }

    @Test
    fun save_withoutCategory_showsErrorAndKeepsForm() {
        val (container, vm) = setup()
        vm.startNew()
        vm.onAmountChange("5")

        assertFalse(vm.save())
        assertEquals(TransactionError.MissingCategory, vm.uiState.value?.error)
        assertTrue(container.accountRepository.getTransactions().isEmpty())

        vm.selectCategory("cat_fun")
        assertNull(vm.uiState.value?.error)
    }

    @Test
    fun save_withoutAmount_showsError() {
        val (_, vm) = setup()
        vm.startNew()
        vm.selectCategory("cat_fun")
        assertFalse(vm.save())
        assertEquals(TransactionError.MissingAmount, vm.uiState.value?.error)
    }

    @Test
    fun startEdit_loadsAndUpdatesTransaction() {
        val (container, vm) = setup()
        container.addTransaction(-7_25L, SimpleDate(2026, 11, 3), "cat_transport", payee = "Bus")
        val id = container.accountRepository.getTransactions().single().id

        vm.startEdit(id)
        val form = assertNotNull(vm.uiState.value)
        assertTrue(form.isEditing)
        assertTrue(form.isExpense)
        assertEquals("7.25", form.amountText)
        assertEquals("Bus", form.payee)
        assertEquals(SimpleDate(2026, 11, 3), form.date)

        vm.onAmountChange("72.50")
        vm.selectDate(SimpleDate(2026, 11, 4))
        assertTrue(vm.save())

        val updated = container.accountRepository.getTransactions().single()
        assertEquals(id, updated.id)
        assertEquals(-72_50L, updated.amount)
        assertEquals(SimpleDate(2026, 11, 4), updated.date)
    }

    @Test
    fun onAmountChange_ignoresInvalidText() {
        val (_, vm) = setup()
        vm.startNew()
        assertTrue(vm.onAmountChange("12.3"))
        assertFalse(vm.onAmountChange("12.345"))
        assertFalse(vm.onAmountChange("12a"))
        assertFalse(vm.onAmountChange("-1"))
        assertEquals("12.3", vm.uiState.value?.amountText)
        assertEquals(12_30L, vm.uiState.value?.amountCents)
    }

    @Test
    fun close_discardsForm() {
        val (container, vm) = setup()
        vm.startNew()
        vm.onAmountChange("1")
        vm.close()
        assertNull(vm.uiState.value)
        assertTrue(container.accountRepository.getTransactions().isEmpty())
    }
}

class AccountsViewModelTest {

    private val today = SimpleDate(2026, 11, 12)

    private fun setup(): Pair<AppContainer, AccountsViewModel> {
        val container = testContainer()
        val vm = AccountsViewModel(container.accountRepository, container.budgetRepository, container.changes, today = { today })
        return container to vm
    }

    @Test
    fun balancesAndTotal_updateWhenTransactionsChange() {
        val (container, vm) = setup()
        assertEquals(0L, vm.uiState.value.total)

        container.addTransaction(500_00L, SimpleDate(2026, 11, 1))
        container.addTransaction(-20_00L, SimpleDate(2026, 11, 2), "cat_food")

        assertEquals(480_00L, vm.uiState.value.total)
        assertEquals(480_00L, vm.uiState.value.accounts.single().balance)
    }

    @Test
    fun selectAccount_listsTransactionsWithCategoryNames() {
        val (container, vm) = setup()
        container.addTransaction(500_00L, SimpleDate(2026, 11, 1))
        container.addTransaction(-20_00L, SimpleDate(2026, 11, 2), "cat_food")

        vm.selectAccount(container.cashAccountId())
        val rows = vm.uiState.value.transactions
        assertEquals(listOf("Food", null), rows.map { it.categoryName })

        vm.closeAccount()
        assertNull(vm.uiState.value.selectedAccount)
    }

    @Test
    fun deleteTransaction_updatesListAndBalance() {
        val (container, vm) = setup()
        container.addTransaction(500_00L, SimpleDate(2026, 11, 1))
        vm.selectAccount(container.cashAccountId())

        vm.deleteTransaction(vm.uiState.value.transactions.single().transaction.id)

        assertTrue(vm.uiState.value.transactions.isEmpty())
        assertEquals(0L, vm.uiState.value.total)
        assertNotNull(vm.uiState.value.recentlyDeleted)
    }

    @Test
    fun undoDelete_restoresTransaction() {
        val (container, vm) = setup()
        container.addTransaction(-12_00L, SimpleDate(2026, 11, 3), "cat_food", payee = "Cafe")
        vm.selectAccount(container.cashAccountId())
        vm.deleteTransaction(vm.uiState.value.transactions.single().transaction.id)

        vm.undoDelete()

        val restored = vm.uiState.value.transactions.single().transaction
        assertEquals("Cafe", restored.payee)
        assertEquals(-12_00L, restored.amount)
        assertEquals(SimpleDate(2026, 11, 3), restored.date)
        assertNull(vm.uiState.value.recentlyDeleted)
        assertEquals(-12_00L, vm.uiState.value.total)
    }

    @Test
    fun addAccount_viaDialog() {
        val (_, vm) = setup()
        vm.startAddAccount()
        vm.confirmAccountDialog() // blank name is ignored
        assertNotNull(vm.uiState.value.accountDialog)

        vm.onDialogNameChange(" Savings ")
        vm.onDialogTypeChange(AccountType.EWallet)
        vm.onDialogOnBudgetChange(false)
        vm.confirmAccountDialog()

        assertNull(vm.uiState.value.accountDialog)
        val added = vm.uiState.value.accounts.map { it.account }.first { it.name == "Savings" }
        assertFalse(added.onBudget)
        assertEquals(AccountType.EWallet, added.type)
    }

    @Test
    fun addAccount_withStartingBalance_updatesTotal() {
        val (container, vm) = setup()
        vm.startAddAccount()
        vm.onDialogNameChange("Bank")
        vm.onDialogStartingBalanceChange("1,200.50")
        vm.confirmAccountDialog()

        assertEquals(1200_50L, vm.uiState.value.total)
        assertEquals(1200_50L, container.budgetRepository.getTotalInflow())
        val bank = vm.uiState.value.accounts.first { it.account.name == "Bank" }
        assertEquals(1200_50L, bank.balance)
        assertEquals(today, container.accountRepository.getTransactionsForAccount(bank.account.id).single().date)
    }

    @Test
    fun addAccount_invalidStartingBalance_cannotSave() {
        val (_, vm) = setup()
        vm.startAddAccount()
        vm.onDialogNameChange("Bank")
        vm.onDialogStartingBalanceChange("12.345")
        vm.confirmAccountDialog()

        assertNotNull(vm.uiState.value.accountDialog)
        assertEquals(1, vm.uiState.value.accounts.size)
    }

    @Test
    fun renameAccount_viaDialog() {
        val (container, vm) = setup()
        vm.selectAccount(container.cashAccountId())
        vm.startEditAccount()
        assertEquals("Cash", vm.uiState.value.accountDialog?.name)

        vm.onDialogNameChange("Wallet")
        vm.confirmAccountDialog()

        assertEquals("Wallet", vm.uiState.value.selectedAccount?.account?.name)
    }

    @Test
    fun deleteAccount_afterConfirmation() {
        val (container, vm) = setup()
        vm.selectAccount(container.cashAccountId())
        vm.requestDeleteAccount()
        assertTrue(vm.uiState.value.confirmingDelete)

        vm.confirmDeleteAccount()

        assertFalse(vm.uiState.value.confirmingDelete)
        assertNull(vm.uiState.value.selectedAccountId)
        assertTrue(vm.uiState.value.accounts.isEmpty())
    }
}
