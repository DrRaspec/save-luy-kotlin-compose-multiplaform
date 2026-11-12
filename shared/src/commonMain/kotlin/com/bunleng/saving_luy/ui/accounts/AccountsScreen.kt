package com.bunleng.saving_luy.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bunleng.saving_luy.domain.AccountType
import com.bunleng.saving_luy.ui.MoneyFormatter
import com.bunleng.saving_luy.ui.components.SystemBackHandler
import org.jetbrains.compose.resources.painterResource
import savingluy.shared.generated.resources.Res
import savingluy.shared.generated.resources.ic_arrow_back

/**
 * Accounts tab: list of accounts with a total, or one account's transactions.
 */
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onAddTransaction: (accountId: Long?) -> Unit,
    onEditTransaction: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val selected = state.selectedAccount
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.recentlyDeleted) {
        if (state.recentlyDeleted == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Transaction deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
    }

    if (selected == null) {
        AccountList(
            state = state,
            snackbarHostState = snackbarHostState,
            onAccountClick = viewModel::selectAccount,
            onAddAccount = viewModel::startAddAccount,
            onAddTransaction = { onAddTransaction(null) },
        )
    } else {
        SystemBackHandler(onBack = viewModel::closeAccount)
        AccountDetail(
            account = selected,
            transactions = state.transactions,
            snackbarHostState = snackbarHostState,
            onAddTransaction = { onAddTransaction(selected.account.id) },
            onBack = viewModel::closeAccount,
            onRename = viewModel::startEditAccount,
            onDelete = viewModel::requestDeleteAccount,
            onTransactionClick = onEditTransaction,
            onTransactionDelete = viewModel::deleteTransaction,
        )
    }

    state.accountDialog?.let { dialog ->
        AccountDialog(
            state = dialog,
            onNameChange = viewModel::onDialogNameChange,
            onTypeChange = viewModel::onDialogTypeChange,
            onStartingBalanceChange = viewModel::onDialogStartingBalanceChange,
            onOnBudgetChange = viewModel::onDialogOnBudgetChange,
            onConfirm = viewModel::confirmAccountDialog,
            onDismiss = viewModel::dismissAccountDialog,
        )
    }

    if (state.confirmingDelete && selected != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteAccount,
            title = { Text("Delete ${selected.account.name}?") },
            text = { Text("This also deletes all ${state.transactions.size} of its transactions.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteAccount) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDeleteAccount) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AccountList(
    state: AccountsUiState,
    snackbarHostState: SnackbarHostState,
    onAccountClick: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onAddTransaction: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { AddTransactionFab(onClick = onAddTransaction) },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item(key = "total") {
                TotalHeader(total = state.total)
            }
            items(items = state.accounts, key = { "account_${it.account.id}" }) { row ->
                AccountRow(row = row, onClick = { onAccountClick(row.account.id) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
            item(key = "add") {
                OutlinedButton(
                    onClick = onAddAccount,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text("+ Add account")
                }
            }
        }
    }
}

@Composable
private fun TotalHeader(total: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = MoneyFormatter.format(total),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = "Total across accounts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun AccountRow(row: AccountRowUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.account.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(row.account.type.label, "Off budget".takeIf { !row.account.onBudget })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = MoneyFormatter.format(row.balance, row.account.currencyCode),
            style = MaterialTheme.typography.titleMedium,
            color = amountColor(row.balance),
        )
    }
}

@Composable
private fun AddTransactionFab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Text(text = "+", style = MaterialTheme.typography.headlineMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDetail(
    account: AccountRowUiState,
    transactions: List<TransactionRowUiState>,
    snackbarHostState: SnackbarHostState,
    onAddTransaction: () -> Unit,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onTransactionDelete: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account.account.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onRename) { Text("Edit") }
                    TextButton(onClick = onDelete) { Text("Delete") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { AddTransactionFab(onClick = onAddTransaction) },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item(key = "balance") {
                Text(
                    text = "Balance ${MoneyFormatter.format(account.balance, account.account.currencyCode)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (transactions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No transactions yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(items = transactions, key = { "txn_${it.transaction.id}" }) { row ->
                SwipeToDeleteTransactionRow(
                    row = row,
                    currencyCode = account.account.currencyCode,
                    onClick = { onTransactionClick(row.transaction.id) },
                    onDelete = { onTransactionDelete(row.transaction.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun SwipeToDeleteTransactionRow(
    row: TransactionRowUiState,
    currencyCode: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        TransactionRow(row = row, currencyCode = currencyCode, onClick = onClick)
    }
}

@Composable
private fun TransactionRow(row: TransactionRowUiState, currencyCode: String, onClick: () -> Unit) {
    val txn = row.transaction
    val details = listOf(
        txn.date.displayName(),
        row.categoryName ?: if (txn.categoryId == null) "Ready to Assign" else "Unknown category",
        txn.memo,
    ).filter { it.isNotBlank() }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = txn.payee.ifBlank { "No payee" },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = MoneyFormatter.format(txn.amount, currencyCode),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = amountColor(txn.amount),
        )
    }
}

@Composable
private fun AccountDialog(
    state: AccountDialogState,
    onNameChange: (String) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onStartingBalanceChange: (String) -> Unit,
    onOnBudgetChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.accountId == null) "Add account" else "Edit account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                )
                AccountTypeSelector(selected = state.type, onSelect = onTypeChange)
                if (state.isNew) {
                    OutlinedTextField(
                        value = state.startingBalanceText,
                        onValueChange = onStartingBalanceChange,
                        label = { Text("Starting balance") },
                        placeholder = { Text("0.00") },
                        prefix = { Text("$") },
                        isError = state.startingBalanceCents == null,
                        supportingText = {
                            Text(if (state.startingBalanceCents == null) "Enter an amount like 120.50" else "Added as income")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("On budget", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Income here goes to Ready to Assign",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.onBudget, onCheckedChange = onOnBudgetChange)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.canSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AccountTypeSelector(selected: AccountType, onSelect: (AccountType) -> Unit) {
    val types = AccountType.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
            ) {
                Text(type.label)
            }
        }
    }
}

/** Negative amounts use the error color; the minus sign keeps it readable without color. */
@Composable
private fun amountColor(amount: Long) =
    if (amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
