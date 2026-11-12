package com.bunleng.saving_luy.ui.transaction

import androidx.lifecycle.ViewModel
import com.bunleng.saving_luy.data.AccountRepository
import com.bunleng.saving_luy.data.BudgetRepository
import com.bunleng.saving_luy.domain.Account
import com.bunleng.saving_luy.domain.BudgetCategory
import com.bunleng.saving_luy.domain.CategoryGroup
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.domain.Transaction
import com.bunleng.saving_luy.domain.TransactionError
import com.bunleng.saving_luy.domain.TransactionRules
import com.bunleng.saving_luy.domain.currentDate
import com.bunleng.saving_luy.ui.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

data class CategoryPickerGroup(
    val group: CategoryGroup,
    val categories: List<BudgetCategory>,
)

/**
 * The transaction being entered or edited.
 */
data class TransactionFormState(
    /** [Transaction.NEW_ID] when adding. */
    val transactionId: Long = Transaction.NEW_ID,
    val isExpense: Boolean = true,
    /** The amount as typed on the keyboard, e.g. "12.5". Always parseable by [MoneyFormatter.parseCents]. */
    val amountText: String = "",
    val payee: String = "",
    val categoryId: String? = null,
    val accountId: Long? = null,
    val date: SimpleDate,
    val memo: String = "",
    val accounts: List<Account> = emptyList(),
    val categoryGroups: List<CategoryPickerGroup> = emptyList(),
    val error: TransactionError? = null,
) {
    val isEditing: Boolean get() = transactionId != Transaction.NEW_ID
    val amountCents: Long get() = MoneyFormatter.parseCents(amountText) ?: 0L
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == accountId }
    val selectedCategory: BudgetCategory? get() =
        categoryGroups.flatMap { it.categories }.firstOrNull { it.id == categoryId }

    /** Category only applies to spending from an on-budget account. */
    val showsCategory: Boolean get() =
        TransactionRules.categoryRequired(isExpense, selectedAccount?.onBudget ?: true)
}

/**
 * Add / edit transaction screen. [uiState] is null while the screen is closed.
 */
class AddTransactionViewModel(
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val today: () -> SimpleDate = ::currentDate,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransactionFormState?>(null)
    val uiState: StateFlow<TransactionFormState?> = _uiState.asStateFlow()

    /**
     * Open the screen with an empty expense dated today, on [accountId] if given,
     * otherwise on the last used account.
     */
    fun startNew(accountId: Long? = null) {
        val accounts = accountRepository.getAccounts()
        _uiState.value = TransactionFormState(
            date = today(),
            accountId = TransactionRules.defaultAccountId(accounts, accountId, accountRepository.getLastUsedAccountId()),
            accounts = accounts,
            categoryGroups = loadCategoryGroups(),
        )
    }

    /** Open the screen to edit a saved transaction. */
    fun startEdit(transactionId: Long) {
        val transaction = accountRepository.getTransaction(transactionId) ?: return
        _uiState.value = TransactionFormState(
            transactionId = transaction.id,
            isExpense = transaction.amount < 0,
            amountText = MoneyFormatter.toEditableText(abs(transaction.amount)),
            payee = transaction.payee,
            categoryId = transaction.categoryId,
            accountId = transaction.accountId,
            date = transaction.date,
            memo = transaction.memo,
            accounts = accountRepository.getAccounts(),
            categoryGroups = loadCategoryGroups(),
        )
    }

    fun close() {
        _uiState.value = null
    }

    /**
     * Keyboard input for the amount. Text that is not a valid amount is ignored.
     * @return true if the text was accepted.
     */
    fun onAmountChange(text: String): Boolean {
        if (MoneyFormatter.parseCents(text) == null) return false
        updateForm { it.copy(amountText = text) }
        return true
    }

    fun setExpense(isExpense: Boolean) = updateForm { it.copy(isExpense = isExpense) }

    fun onPayeeChange(payee: String) = updateForm { it.copy(payee = payee) }

    fun selectCategory(categoryId: String) = updateForm { it.copy(categoryId = categoryId) }

    fun selectAccount(accountId: Long) = updateForm { it.copy(accountId = accountId) }

    fun selectDate(date: SimpleDate) = updateForm { it.copy(date = date) }

    fun onMemoChange(memo: String) = updateForm { it.copy(memo = memo) }

    /**
     * Validate and save. Closes the screen and returns true on success;
     * otherwise shows the error and returns false.
     */
    fun save(): Boolean {
        val form = _uiState.value ?: return false
        val error = TransactionRules.validate(form.amountCents, form.selectedAccount, form.categoryId, form.isExpense)
        if (error != null) {
            _uiState.update { it?.copy(error = error) }
            return false
        }
        accountRepository.saveTransaction(
            Transaction(
                id = form.transactionId,
                accountId = form.accountId!!,
                categoryId = if (form.showsCategory) form.categoryId else null,
                payee = form.payee.trim(),
                amount = TransactionRules.signedAmount(form.amountCents, form.isExpense),
                date = form.date,
                memo = form.memo.trim(),
            )
        )
        close()
        return true
    }

    /** Apply a change to the open form and clear any previous error. */
    private fun updateForm(change: (TransactionFormState) -> TransactionFormState) {
        _uiState.update { form -> form?.let { change(it).copy(error = null) } }
    }

    private fun loadCategoryGroups(): List<CategoryPickerGroup> =
        budgetRepository.getGroups().map { group ->
            CategoryPickerGroup(group, budgetRepository.getCategoriesForGroup(group.id))
        }
}
