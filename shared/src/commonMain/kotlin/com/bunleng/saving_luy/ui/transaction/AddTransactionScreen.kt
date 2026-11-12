package com.bunleng.saving_luy.ui.transaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.domain.TransactionError
import com.bunleng.saving_luy.ui.components.SystemBackHandler
import com.bunleng.saving_luy.ui.theme.LocalBudgetStatusColors
import org.jetbrains.compose.resources.painterResource
import savingluy.shared.generated.resources.Res
import savingluy.shared.generated.resources.ic_arrow_back

/**
 * Full-screen form to add or edit a transaction. The amount field gets focus first,
 * so the system number keyboard is ready as soon as the screen opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    state: TransactionFormState,
    viewModel: AddTransactionViewModel,
) {
    SystemBackHandler(onBack = viewModel::close)
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit Transaction" else "New Transaction") },
                navigationIcon = {
                    IconButton(onClick = viewModel::close) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.save() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp),
            ) {
                Text("Save", style = MaterialTheme.typography.titleMedium)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Tapping empty space closes the keyboard.
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExpenseIncomeToggle(isExpense = state.isExpense, onChange = viewModel::setExpense)

            AmountField(
                text = state.amountText,
                isExpense = state.isExpense,
                isError = state.error == TransactionError.MissingAmount,
                autoFocus = !state.isEditing,
                onChange = viewModel::onAmountChange,
            )

            Text(
                text = state.error?.let { "⚠ ${errorMessage(it)}" } ?: hint(state),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )

            DetailsCard(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun ExpenseIncomeToggle(isExpense: Boolean, onChange: (Boolean) -> Unit) {
    val options = listOf(true to "Expense", false to "Income")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (expense, label) ->
            SegmentedButton(
                selected = isExpense == expense,
                onClick = { onChange(expense) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

/**
 * Big centered amount like "−$12.50", typed with the system decimal keyboard.
 * The field is sized to its text and the font shrinks so long amounts still fit on one line.
 */
@Composable
private fun AmountField(
    text: String,
    isExpense: Boolean,
    isError: Boolean,
    autoFocus: Boolean,
    onChange: (String) -> Boolean,
) {
    // Local state keeps typing in sync even when the view model's flow is a frame behind.
    var value by remember { mutableStateOf(text) }
    val statusColors = LocalBudgetStatusColors.current
    val color = when {
        isError -> MaterialTheme.colorScheme.error
        isExpense -> statusColors.negative.content
        else -> statusColors.positive.content
    }
    val dimColor = color.copy(alpha = 0.35f)
    val prefix = if (isExpense) "−$" else "+$"
    val typography = MaterialTheme.typography
    val sizes = listOf(typography.displayMedium, typography.displaySmall, typography.headlineMedium)
        .map { it.copy(fontWeight = FontWeight.Bold) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (autoFocus) focusRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shown = value.ifEmpty { AMOUNT_PLACEHOLDER }
        val cursorPx = with(density) { CURSOR_SPACE.roundToPx() }
        val style = sizes.firstOrNull { measurer.measure(prefix + shown, it).size.width + cursorPx <= constraints.maxWidth }
            ?: sizes.last()
        val fieldWidth = with(density) { measurer.measure(shown, style).size.width.toDp() } + CURSOR_SPACE

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = prefix, style = style, color = if (value.isEmpty()) dimColor else color)
            BasicTextField(
                value = value,
                onValueChange = { if (onChange(it)) value = it },
                textStyle = style.copy(color = color),
                singleLine = true,
                cursorBrush = SolidColor(color),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .width(fieldWidth)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(AMOUNT_PLACEHOLDER, style = style, color = dimColor)
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

private const val AMOUNT_PLACEHOLDER = "0.00"
private val CURSOR_SPACE = 4.dp

/** Payee, Category, Account, Date and Memo grouped in one rounded card. */
@Composable
private fun DetailsCard(state: TransactionFormState, viewModel: AddTransactionViewModel) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            TextRow(
                label = "Payee",
                value = state.payee,
                placeholder = "Who was it?",
                capitalization = KeyboardCapitalization.Words,
                onChange = viewModel::onPayeeChange,
            )
            RowDivider()
            if (state.showsCategory) {
                CategoryRow(state = state, onSelect = viewModel::selectCategory)
                RowDivider()
            }
            AccountRow(state = state, onSelect = viewModel::selectAccount)
            RowDivider()
            DateRow(date = state.date, onSelect = viewModel::selectDate)
            RowDivider()
            TextRow(
                label = "Memo",
                value = state.memo,
                placeholder = "Optional",
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
                onChange = viewModel::onMemoChange,
            )
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** Label on the left, editable text on the right. */
@Composable
private fun TextRow(
    label: String,
    value: String,
    placeholder: String,
    capitalization: KeyboardCapitalization,
    onChange: (String) -> Unit,
    imeAction: ImeAction = ImeAction.Next,
) {
    val focusManager = LocalFocusManager.current
    // Local state keeps typing in sync even when the view model's flow is a frame behind.
    var text by remember { mutableStateOf(value) }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
    )
    RowLayout(label = label) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { focusManager.clearFocus() },
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (text.isEmpty()) {
                        Text(placeholder, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                    innerTextField()
                }
            },
        )
    }
}

/** Label on the left, current choice on the right; tapping opens a menu or dialog. */
@Composable
private fun PickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    isError: Boolean = false,
    menu: @Composable () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val openPicker = {
        focusManager.clearFocus()
        onClick()
    }
    RowLayout(label = label, isError = isError, modifier = Modifier.clickable(onClick = openPicker)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = "$value  ›",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
            menu()
        }
    }
}

@Composable
private fun RowLayout(
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun CategoryRow(state: TransactionFormState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    PickerRow(
        label = "Category",
        value = state.selectedCategory?.name ?: "Choose",
        onClick = { expanded = true },
        isError = state.error == TransactionError.MissingCategory,
    ) {
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (group in state.categoryGroups) {
                Text(
                    text = group.group.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                for (category in group.categories) {
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onSelect(category.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(state: TransactionFormState, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    PickerRow(
        label = "Account",
        value = state.selectedAccount?.name ?: "Choose",
        onClick = { expanded = true },
        isError = state.error == TransactionError.MissingAccount,
    ) {
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (account in state.accounts) {
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onSelect(account.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: SimpleDate, onSelect: (SimpleDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    PickerRow(label = "Date", value = date.displayName(), onClick = { showPicker = true })

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * SimpleDate.MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onSelect(SimpleDate.fromUtcMillis(it)) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun hint(state: TransactionFormState): String = when {
    !state.isExpense -> "Income goes to Ready to Assign"
    !state.showsCategory -> "Off-budget account: no category needed"
    else -> "Comes out of the category's Available"
}

private fun errorMessage(error: TransactionError): String = when (error) {
    TransactionError.MissingAmount -> "Enter an amount"
    TransactionError.MissingAccount -> "Choose an account"
    TransactionError.MissingCategory -> "Choose a category for this expense"
}
