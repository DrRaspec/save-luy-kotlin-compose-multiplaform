package com.bunleng.saving_luy.ui.accounts

import androidx.lifecycle.ViewModel
import com.bunleng.saving_luy.data.AccountRepository
import com.bunleng.saving_luy.data.BudgetRepository
import com.bunleng.saving_luy.data.DataChangeNotifier
import com.bunleng.saving_luy.domain.Account
import com.bunleng.saving_luy.domain.AccountType
import com.bunleng.saving_luy.domain.BudgetMath
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.domain.Transaction
import com.bunleng.saving_luy.domain.currentDate
import com.bunleng.saving_luy.ui.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state for the Accounts tab: the account list, or one account's transactions.
 */
data class AccountsUiState(
    val total: Long = 0L,
    val accounts: List<AccountRowUiState> = emptyList(),
    /** The account whose transactions are shown, or null for the list. */
    val selectedAccountId: Long? = null,
    val transactions: List<TransactionRowUiState> = emptyList(),
    /** Add / rename dialog, or null if hidden. */
    val accountDialog: AccountDialogState? = null,
    val confirmingDelete: Boolean = false,
    /** Last swiped-away transaction, kept so it can be undone from the snackbar. */
    val recentlyDeleted: Transaction? = null,
) {
    val selectedAccount: AccountRowUiState? get() = accounts.firstOrNull { it.account.id == selectedAccountId }
}

data class AccountRowUiState(
    val account: Account,
    val balance: Long,
)

data class TransactionRowUiState(
    val transaction: Transaction,
    /** Category name, or null when the money went to Ready to Assign. */
    val categoryName: String?,
)

data class AccountDialogState(
    /** Null when adding a new account. */
    val accountId: Long?,
    val name: String,
    val type: AccountType,
    val onBudget: Boolean,
    /** Typed starting balance, e.g. "120.50". Only used when adding. */
    val startingBalanceText: String = "",
) {
    val isNew: Boolean get() = accountId == null

    /** Null when the typed text is not a valid amount. */
    val startingBalanceCents: Long? get() = MoneyFormatter.parseCents(startingBalanceText)

    val canSave: Boolean get() = name.isNotBlank() && (!isNew || startingBalanceCents != null)
}

class AccountsViewModel(
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val changes: DataChangeNotifier,
    private val today: () -> SimpleDate = ::currentDate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val onDataChanged: () -> Unit = ::refreshState

    init {
        changes.addListener(onDataChanged)
        refreshState()
    }

    override fun onCleared() {
        changes.removeListener(onDataChanged)
    }

    fun selectAccount(accountId: Long) {
        _uiState.update { it.copy(selectedAccountId = accountId) }
        refreshState()
    }

    fun closeAccount() {
        _uiState.update { it.copy(selectedAccountId = null, transactions = emptyList()) }
    }

    /** Delete right away; [undoDelete] can put it back while the snackbar shows. */
    fun deleteTransaction(transactionId: Long) {
        val transaction = accountRepository.getTransaction(transactionId) ?: return
        _uiState.update { it.copy(recentlyDeleted = transaction) }
        accountRepository.deleteTransaction(transactionId)
    }

    fun undoDelete() {
        val transaction = _uiState.value.recentlyDeleted ?: return
        _uiState.update { it.copy(recentlyDeleted = null) }
        accountRepository.restoreTransaction(transaction)
    }

    /** The snackbar went away without undo. */
    fun clearUndo() {
        _uiState.update { it.copy(recentlyDeleted = null) }
    }

    // ---- account dialog ----

    fun startAddAccount() {
        _uiState.update { it.copy(accountDialog = AccountDialogState(accountId = null, name = "", type = AccountType.Bank, onBudget = true)) }
    }

    fun startEditAccount() {
        val account = _uiState.value.selectedAccount?.account ?: return
        _uiState.update {
            it.copy(accountDialog = AccountDialogState(account.id, account.name, account.type, account.onBudget))
        }
    }

    fun onDialogNameChange(name: String) {
        _uiState.update { state -> state.copy(accountDialog = state.accountDialog?.copy(name = name)) }
    }

    fun onDialogTypeChange(type: AccountType) {
        _uiState.update { state -> state.copy(accountDialog = state.accountDialog?.copy(type = type)) }
    }

    fun onDialogStartingBalanceChange(text: String) {
        _uiState.update { state -> state.copy(accountDialog = state.accountDialog?.copy(startingBalanceText = text)) }
    }

    fun onDialogOnBudgetChange(onBudget: Boolean) {
        _uiState.update { state -> state.copy(accountDialog = state.accountDialog?.copy(onBudget = onBudget)) }
    }

    fun confirmAccountDialog() {
        val dialog = _uiState.value.accountDialog ?: return
        if (!dialog.canSave) return
        _uiState.update { it.copy(accountDialog = null) }
        val name = dialog.name.trim()
        if (dialog.accountId == null) {
            accountRepository.addAccount(
                name = name,
                type = dialog.type,
                onBudget = dialog.onBudget,
                startingBalance = dialog.startingBalanceCents ?: 0L,
                date = today(),
            )
        } else {
            accountRepository.updateAccount(dialog.accountId, name, dialog.type, dialog.onBudget)
        }
    }

    fun dismissAccountDialog() {
        _uiState.update { it.copy(accountDialog = null) }
    }

    // ---- delete account ----

    fun requestDeleteAccount() {
        _uiState.update { it.copy(confirmingDelete = true) }
    }

    fun cancelDeleteAccount() {
        _uiState.update { it.copy(confirmingDelete = false) }
    }

    /** Deletes the selected account and all its transactions. */
    fun confirmDeleteAccount() {
        val accountId = _uiState.value.selectedAccountId ?: return
        _uiState.update { it.copy(confirmingDelete = false, selectedAccountId = null, transactions = emptyList()) }
        accountRepository.deleteAccount(accountId)
    }

    private fun refreshState() {
        val balances = BudgetMath.accountBalances(accountRepository.getTransactions())
        val rows = accountRepository.getAccounts().map { AccountRowUiState(it, balances[it.id] ?: 0L) }
        val categoryNames = budgetRepository.getCategories().associate { it.id to it.name }
        // Drop the selection if that account no longer exists.
        val selectedId = _uiState.value.selectedAccountId?.takeIf { id -> rows.any { it.account.id == id } }
        val transactions = selectedId?.let { id ->
            accountRepository.getTransactionsForAccount(id).map {
                TransactionRowUiState(it, it.categoryId?.let(categoryNames::get))
            }
        }.orEmpty()

        _uiState.update {
            it.copy(
                total = rows.sumOf { row -> row.balance },
                accounts = rows,
                selectedAccountId = selectedId,
                transactions = transactions,
            )
        }
    }
}
