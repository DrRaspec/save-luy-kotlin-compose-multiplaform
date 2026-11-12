package com.bunleng.saving_luy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bunleng.saving_luy.data.AppContainer
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import savingluy.shared.generated.resources.Res
import savingluy.shared.generated.resources.ic_tab_accounts
import savingluy.shared.generated.resources.ic_tab_budget
import savingluy.shared.generated.resources.ic_tab_settings
import com.bunleng.saving_luy.ui.accounts.AccountsScreen
import com.bunleng.saving_luy.ui.accounts.AccountsViewModel
import com.bunleng.saving_luy.ui.budget.BudgetScreen
import com.bunleng.saving_luy.ui.budget.BudgetViewModel
import com.bunleng.saving_luy.ui.settings.SettingsScreen
import com.bunleng.saving_luy.ui.theme.MoneyAppTheme
import com.bunleng.saving_luy.ui.transaction.AddTransactionScreen
import com.bunleng.saving_luy.ui.transaction.AddTransactionViewModel

private enum class MainTab(val label: String, val icon: DrawableResource) {
    Budget("Budget", Res.drawable.ic_tab_budget),
    Accounts("Accounts", Res.drawable.ic_tab_accounts),
    Settings("Settings", Res.drawable.ic_tab_settings),
}

/**
 * @param addTransactionRequest increase this to open Add Transaction (e.g. after a widget tap).
 * 0 means no request.
 */
@Composable
fun App(container: AppContainer, addTransactionRequest: Int = 0) {
    MoneyAppTheme {
        val budgetViewModel = viewModel { BudgetViewModel(container.budgetRepository, container.changes) }
        val accountsViewModel = viewModel {
            AccountsViewModel(container.accountRepository, container.budgetRepository, container.changes)
        }
        val transactionViewModel = viewModel {
            AddTransactionViewModel(container.accountRepository, container.budgetRepository)
        }
        val transactionForm by transactionViewModel.uiState.collectAsState()

        LaunchedEffect(addTransactionRequest) {
            if (addTransactionRequest > 0) transactionViewModel.startNew()
        }
        var tab by rememberSaveable { mutableStateOf(MainTab.Budget) }

        val form = transactionForm
        if (form != null) {
            AddTransactionScreen(state = form, viewModel = transactionViewModel)
        } else {
            MainTabs(
                tab = tab,
                onTabChange = { tab = it },
            ) { selected ->
                when (selected) {
                    MainTab.Budget -> BudgetScreen(
                        viewModel = budgetViewModel,
                        onAddTransaction = { transactionViewModel.startNew() },
                    )
                    MainTab.Accounts -> AccountsScreen(
                        viewModel = accountsViewModel,
                        onAddTransaction = transactionViewModel::startNew,
                        onEditTransaction = transactionViewModel::startEdit,
                    )
                    MainTab.Settings -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun MainTabs(
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
    content: @Composable (MainTab) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                for (item in MainTab.entries) {
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { onTabChange(item) },
                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        Box(modifier = contentModifier) {
            content(tab)
        }
    }
}
