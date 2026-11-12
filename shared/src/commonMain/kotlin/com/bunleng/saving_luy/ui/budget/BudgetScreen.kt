package com.bunleng.saving_luy.ui.budget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The main Budget screen – assembles month selector, ready-to-assign banner,
 * collapsible category groups, and the edit-assigned bottom sheet.
 */
@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel,
    onAddTransaction: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Month selector
            MonthSelector(
                month = state.currentMonth,
                onPrevious = viewModel::goToPreviousMonth,
                onNext = viewModel::goToNextMonth,
            )

            // Ready to Assign banner
            ReadyToAssignBanner(
                amount = state.readyToAssign,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            BudgetSummary(hint = state.hint, onBudgetBalance = state.onBudgetBalance)

            TextButton(
                onClick = { viewModel.startMoveMoney() },
                modifier = Modifier.align(Alignment.End).padding(end = 8.dp),
            ) {
                Text("⇄ Move money")
            }

            // Category groups list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                for (group in state.groupRows) {
                    // Group header
                    item(key = "group_${group.group.id}") {
                        CategoryGroupHeader(
                            name = group.group.name,
                            isExpanded = group.isExpanded,
                            onClick = { viewModel.toggleGroupExpanded(group.group.id) },
                            onAddCategory = { viewModel.startAddCategory(group.group.id) },
                            onRename = { viewModel.startRenameGroup(group.group.id) },
                            onDelete = { viewModel.requestDeleteGroup(group.group.id) },
                        )
                    }

                    // Category rows (animated visibility for collapse)
                    if (group.isExpanded) {
                        items(
                            items = group.categoryRows,
                            key = { "cat_${it.category.id}" },
                        ) { row ->
                            CategoryRow(
                                name = row.category.name,
                                assigned = row.entry.assigned,
                                available = row.entry.available,
                                onClick = { viewModel.startEditAssigned(row.category.id) },
                                onMoveMoney = { viewModel.startMoveMoney(row.category.id) },
                                onRename = { viewModel.startRenameCategory(row.category.id) },
                                onDelete = { viewModel.requestDeleteCategory(row.category.id) },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                        item(key = "add_cat_${group.group.id}") {
                            AddRow(label = "Add category", onClick = { viewModel.startAddCategory(group.group.id) })
                        }
                    }
                }
                item(key = "add_group") {
                    AddRow(label = "Add group", onClick = viewModel::startAddGroup)
                }
                // Room so the last rows can scroll above the floating action button.
                item(key = "fab_space") { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    state.moveMoney?.let { move ->
        MoveMoneySheet(
            state = move,
            categories = state.allCategoryRows,
            onSelectFrom = viewModel::selectMoveFrom,
            onSelectTo = viewModel::selectMoveTo,
            onAmountChange = viewModel::onMoveAmountChange,
            onConfirm = { viewModel.confirmMoveMoney() },
            onDismiss = viewModel::cancelMoveMoney,
        )
    }

    state.nameDialog?.let { dialog ->
        NameDialog(
            state = dialog,
            onNameChange = viewModel::onNameChange,
            onConfirm = { viewModel.confirmNameDialog() },
            onDismiss = viewModel::dismissNameDialog,
        )
    }

    state.deleteCategory?.let { delete ->
        DeleteCategoryDialog(
            state = delete,
            otherCategories = state.allCategoryRows.filter { it.category.id != delete.category.id },
            onSelectMoveTo = viewModel::selectDeleteMoveTo,
            onConfirm = { viewModel.confirmDeleteCategory() },
            onDismiss = viewModel::cancelDeleteCategory,
        )
    }

    state.deleteGroup?.let { delete ->
        DeleteGroupDialog(
            state = delete,
            onConfirm = { viewModel.confirmDeleteGroup() },
            onDismiss = viewModel::cancelDeleteGroup,
        )
    }

    // Edit Assigned bottom sheet
    state.editingCategory?.let { editing ->
        EditAssignedSheet(
            state = editing,
            onDigit = viewModel::onEditDigit,
            onBackspace = viewModel::onEditBackspace,
            onClear = viewModel::onEditClear,
            onConfirm = viewModel::confirmEdit,
            onDismiss = viewModel::cancelEdit,
        )
    }
}
