package com.bunleng.saving_luy.ui.budget

import androidx.lifecycle.ViewModel
import com.bunleng.saving_luy.data.BudgetRepository
import com.bunleng.saving_luy.data.DataChangeNotifier
import com.bunleng.saving_luy.domain.BudgetCategory
import com.bunleng.saving_luy.domain.BudgetHint
import com.bunleng.saving_luy.domain.BudgetMath
import com.bunleng.saving_luy.domain.BudgetMonth
import com.bunleng.saving_luy.domain.CategoryBudgetEntry
import com.bunleng.saving_luy.domain.CategoryGroup
import com.bunleng.saving_luy.domain.CategoryRules
import com.bunleng.saving_luy.domain.MoveMoneyError
import com.bunleng.saving_luy.domain.MoveMoneyRules
import com.bunleng.saving_luy.domain.NameError
import com.bunleng.saving_luy.domain.currentBudgetMonth
import com.bunleng.saving_luy.ui.MoneyFormatter
import com.bunleng.saving_luy.ui.components.AmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * UI state for the Budget screen.
 */
data class BudgetUiState(
    val currentMonth: BudgetMonth,
    val readyToAssign: Long = 0L,
    val totalInflow: Long = 0L,
    /** Cash in on-budget accounts, shown under the banner. */
    val onBudgetBalance: Long = 0L,
    /** Nudge under the banner, or null when there is nothing to fix. */
    val hint: BudgetHint? = null,
    val groupRows: List<GroupUiState> = emptyList(),
    /** Which category is being edited in the bottom sheet, or null if hidden. */
    val editingCategory: EditAssignedState? = null,
    /** The Move Money sheet, or null if hidden. */
    val moveMoney: MoveMoneyState? = null,
    /** Add or rename dialog for a category or group, or null if hidden. */
    val nameDialog: NameDialogState? = null,
    /** Delete-category confirmation, or null if hidden. */
    val deleteCategory: DeleteCategoryState? = null,
    /** Delete-group confirmation, or null if hidden. */
    val deleteGroup: DeleteGroupState? = null,
) {
    /** Every category with its current-month entry, in Budget screen order. */
    val allCategoryRows: List<CategoryRowUiState> get() = groupRows.flatMap { it.categoryRows }
}

data class GroupUiState(
    val group: CategoryGroup,
    val isExpanded: Boolean = true,
    val categoryRows: List<CategoryRowUiState> = emptyList(),
)

data class CategoryRowUiState(
    val category: BudgetCategory,
    val entry: CategoryBudgetEntry,
)

data class EditAssignedState(
    val category: BudgetCategory,
    val currentAssigned: Long,
    /** The digits the user has typed so far (in cents). */
    val inputDigits: String,
) {
    val inputCents: Long get() = MoneyFormatter.parseCentsFromDigits(inputDigits)
}

data class MoveMoneyState(
    val fromCategoryId: String? = null,
    val toCategoryId: String? = null,
    /** Typed amount, e.g. "25.50". Always parseable by [MoneyFormatter.parseCents]. */
    val amountText: String = "",
    val error: MoveMoneyError? = null,
) {
    val amountCents: Long get() = MoneyFormatter.parseCents(amountText) ?: 0L
}

/** What the name dialog creates or renames. */
sealed interface NameTarget {
    data object NewGroup : NameTarget
    data class RenameGroup(val groupId: String) : NameTarget
    data class NewCategory(val groupId: String) : NameTarget
    data class RenameCategory(val categoryId: String) : NameTarget
}

data class NameDialogState(
    val target: NameTarget,
    val name: String,
    val error: NameError? = null,
) {
    val isCategory: Boolean get() = target is NameTarget.NewCategory || target is NameTarget.RenameCategory
    val isNew: Boolean get() = target is NameTarget.NewGroup || target is NameTarget.NewCategory
}

data class DeleteCategoryState(
    val category: BudgetCategory,
    /** True when transactions or Assigned amounts must move to another category. */
    val hasHistory: Boolean,
    val moveToCategoryId: String? = null,
) {
    val canDelete: Boolean get() = !hasHistory || moveToCategoryId != null
}

data class DeleteGroupState(
    val group: CategoryGroup,
    /** Only empty groups can be deleted. */
    val isEmpty: Boolean,
)

class BudgetViewModel(
    private val repository: BudgetRepository,
    private val changes: DataChangeNotifier,
    initialMonth: BudgetMonth = currentBudgetMonth(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState(currentMonth = initialMonth))
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val onDataChanged: () -> Unit = ::refreshState

    init {
        changes.addListener(onDataChanged)
        refreshState()
    }

    override fun onCleared() {
        changes.removeListener(onDataChanged)
    }

    fun goToPreviousMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.previous()) }
        refreshState()
    }

    fun goToNextMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.next()) }
        refreshState()
    }

    fun toggleGroupExpanded(groupId: String) {
        _uiState.update { state ->
            state.copy(
                groupRows = state.groupRows.map { g ->
                    if (g.group.id == groupId) g.copy(isExpanded = !g.isExpanded) else g
                }
            )
        }
    }

    /** Open the edit-assigned bottom sheet for a category. */
    fun startEditAssigned(categoryId: String) {
        val month = _uiState.value.currentMonth
        val category = repository.getCategories().firstOrNull { it.id == categoryId } ?: return
        val assigned = repository.getAssigned(month, categoryId)
        _uiState.update {
            it.copy(
                editingCategory = EditAssignedState(
                    category = category,
                    currentAssigned = assigned,
                    inputDigits = AmountInput.fromCents(assigned),
                )
            )
        }
    }

    /** User typed a digit on the keypad. */
    fun onEditDigit(digit: Char) {
        _uiState.update { state ->
            val editing = state.editingCategory ?: return@update state
            state.copy(
                editingCategory = editing.copy(
                    inputDigits = AmountInput.appendDigit(editing.inputDigits, digit)
                )
            )
        }
    }

    /** User pressed backspace on the keypad. */
    fun onEditBackspace() {
        _uiState.update { state ->
            val editing = state.editingCategory ?: return@update state
            state.copy(
                editingCategory = editing.copy(
                    inputDigits = AmountInput.backspace(editing.inputDigits)
                )
            )
        }
    }

    /** User pressed clear on the keypad. */
    fun onEditClear() {
        _uiState.update { state ->
            val editing = state.editingCategory ?: return@update state
            state.copy(
                editingCategory = editing.copy(
                    inputDigits = ""
                )
            )
        }
    }

    /** Confirm the edit and save. */
    fun confirmEdit() {
        val state = _uiState.value
        val editing = state.editingCategory ?: return
        _uiState.update { it.copy(editingCategory = null) }
        repository.updateAssigned(state.currentMonth, editing.category.id, editing.inputCents)
    }

    /** Dismiss the bottom sheet without saving. */
    fun cancelEdit() {
        _uiState.update { it.copy(editingCategory = null) }
    }

    // ---- Move Money ----

    /**
     * Open Move Money. An overspent category becomes the destination with the overspent
     * amount filled in; any other category becomes the source.
     */
    fun startMoveMoney(categoryId: String? = null) {
        val row = _uiState.value.allCategoryRows.firstOrNull { it.category.id == categoryId }
        val sheet = when {
            row == null -> MoveMoneyState()
            row.entry.available < 0 -> MoveMoneyState(
                toCategoryId = row.category.id,
                amountText = MoneyFormatter.toEditableText(MoveMoneyRules.suggestedAmount(row.entry.available)),
            )
            else -> MoveMoneyState(fromCategoryId = row.category.id)
        }
        _uiState.update { it.copy(moveMoney = sheet) }
    }

    fun selectMoveFrom(categoryId: String) = updateMove { it.copy(fromCategoryId = categoryId) }

    /** Picking an overspent destination fills in the amount needed to cover it, if none was typed. */
    fun selectMoveTo(categoryId: String) {
        val available = _uiState.value.allCategoryRows.firstOrNull { it.category.id == categoryId }?.entry?.available ?: 0L
        updateMove { move ->
            val suggested = MoveMoneyRules.suggestedAmount(available)
            val amountText = if (move.amountText.isEmpty() && suggested > 0) {
                MoneyFormatter.toEditableText(suggested)
            } else {
                move.amountText
            }
            move.copy(toCategoryId = categoryId, amountText = amountText)
        }
    }

    /** @return true if the text was accepted as an amount. */
    fun onMoveAmountChange(text: String): Boolean {
        if (MoneyFormatter.parseCents(text) == null) return false
        updateMove { it.copy(amountText = text) }
        return true
    }

    /** Validate and move. Closes the sheet on success, otherwise shows the error. */
    fun confirmMoveMoney(): Boolean {
        val move = _uiState.value.moveMoney ?: return false
        val error = MoveMoneyRules.validate(move.fromCategoryId, move.toCategoryId, move.amountCents)
        if (error != null) {
            _uiState.update { it.copy(moveMoney = move.copy(error = error)) }
            return false
        }
        _uiState.update { it.copy(moveMoney = null) }
        repository.moveAssigned(_uiState.value.currentMonth, move.fromCategoryId!!, move.toCategoryId!!, move.amountCents)
        return true
    }

    fun cancelMoveMoney() {
        _uiState.update { it.copy(moveMoney = null) }
    }

    // ---- category and group management ----

    fun startAddGroup() = openNameDialog(NameTarget.NewGroup, "")

    fun startRenameGroup(groupId: String) {
        val group = _uiState.value.groupRows.firstOrNull { it.group.id == groupId }?.group ?: return
        openNameDialog(NameTarget.RenameGroup(groupId), group.name)
    }

    fun startAddCategory(groupId: String) = openNameDialog(NameTarget.NewCategory(groupId), "")

    fun startRenameCategory(categoryId: String) {
        val category = categoryById(categoryId) ?: return
        openNameDialog(NameTarget.RenameCategory(categoryId), category.name)
    }

    fun onNameChange(name: String) {
        _uiState.update { state -> state.copy(nameDialog = state.nameDialog?.copy(name = name, error = null)) }
    }

    /** Validate and save. Closes the dialog on success, otherwise shows the error. */
    fun confirmNameDialog(): Boolean {
        val dialog = _uiState.value.nameDialog ?: return false
        val error = CategoryRules.validateName(dialog.name, otherNames(dialog.target))
        if (error != null) {
            _uiState.update { it.copy(nameDialog = dialog.copy(error = error)) }
            return false
        }
        _uiState.update { it.copy(nameDialog = null) }
        val name = dialog.name.trim()
        when (val target = dialog.target) {
            NameTarget.NewGroup -> repository.addGroup(name)
            is NameTarget.RenameGroup -> repository.renameGroup(target.groupId, name)
            is NameTarget.NewCategory -> repository.addCategory(target.groupId, name)
            is NameTarget.RenameCategory -> repository.renameCategory(target.categoryId, name)
        }
        return true
    }

    fun dismissNameDialog() {
        _uiState.update { it.copy(nameDialog = null) }
    }

    fun requestDeleteCategory(categoryId: String) {
        val category = categoryById(categoryId) ?: return
        _uiState.update {
            it.copy(deleteCategory = DeleteCategoryState(category, repository.categoryHasHistory(categoryId)))
        }
    }

    fun selectDeleteMoveTo(categoryId: String) {
        _uiState.update { state ->
            state.copy(deleteCategory = state.deleteCategory?.copy(moveToCategoryId = categoryId))
        }
    }

    fun confirmDeleteCategory(): Boolean {
        val delete = _uiState.value.deleteCategory ?: return false
        if (!delete.canDelete) return false
        _uiState.update { it.copy(deleteCategory = null) }
        return repository.deleteCategory(delete.category.id, delete.moveToCategoryId)
    }

    fun cancelDeleteCategory() {
        _uiState.update { it.copy(deleteCategory = null) }
    }

    fun requestDeleteGroup(groupId: String) {
        val row = _uiState.value.groupRows.firstOrNull { it.group.id == groupId } ?: return
        _uiState.update { it.copy(deleteGroup = DeleteGroupState(row.group, row.categoryRows.isEmpty())) }
    }

    fun confirmDeleteGroup(): Boolean {
        val delete = _uiState.value.deleteGroup ?: return false
        _uiState.update { it.copy(deleteGroup = null) }
        return delete.isEmpty && repository.deleteGroup(delete.group.id)
    }

    fun cancelDeleteGroup() {
        _uiState.update { it.copy(deleteGroup = null) }
    }

    private fun openNameDialog(target: NameTarget, name: String) {
        _uiState.update { it.copy(nameDialog = NameDialogState(target, name)) }
    }

    private fun categoryById(categoryId: String): BudgetCategory? =
        _uiState.value.allCategoryRows.firstOrNull { it.category.id == categoryId }?.category

    /** Names the new or renamed item must not clash with. Category names are unique across groups. */
    private fun otherNames(target: NameTarget): List<String> {
        val state = _uiState.value
        return when (target) {
            NameTarget.NewGroup -> state.groupRows.map { it.group.name }
            is NameTarget.RenameGroup -> state.groupRows.filter { it.group.id != target.groupId }.map { it.group.name }
            is NameTarget.NewCategory -> state.allCategoryRows.map { it.category.name }
            is NameTarget.RenameCategory ->
                state.allCategoryRows.filter { it.category.id != target.categoryId }.map { it.category.name }
        }
    }

    /** Apply a change to the open Move Money sheet and clear any previous error. */
    private fun updateMove(change: (MoveMoneyState) -> MoveMoneyState) {
        _uiState.update { state -> state.copy(moveMoney = state.moveMoney?.let { change(it).copy(error = null) }) }
    }

    private fun refreshState() {
        val month = _uiState.value.currentMonth
        val groups = repository.getGroups().sortedBy { it.sortOrder }
        val allAmounts = repository.getAllAmounts()
        val totalInflow = repository.getTotalInflow()
        val readyToAssign = BudgetMath.readyToAssign(totalInflow, allAmounts)

        // Preserve expansion state from current UI state
        val currentGroupStates = _uiState.value.groupRows.associateBy { it.group.id }

        val groupRows = groups.map { group ->
            val cats = repository.getCategoriesForGroup(group.id)
            val isExpanded = currentGroupStates[group.id]?.isExpanded ?: true
            GroupUiState(
                group = group,
                isExpanded = isExpanded,
                categoryRows = cats.map { cat ->
                    CategoryRowUiState(
                        category = cat,
                        entry = BudgetMath.entryFor(cat.id, month, allAmounts),
                    )
                }
            )
        }

        val overspentNames = groupRows.flatMap { it.categoryRows }
            .filter { it.entry.available < 0 }
            .map { it.category.name }

        _uiState.update {
            it.copy(
                readyToAssign = readyToAssign,
                totalInflow = totalInflow,
                onBudgetBalance = repository.getOnBudgetBalance(),
                hint = BudgetHint.of(readyToAssign, overspentNames),
                groupRows = groupRows,
            )
        }
    }
}
