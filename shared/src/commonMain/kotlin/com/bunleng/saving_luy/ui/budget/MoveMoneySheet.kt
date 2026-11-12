package com.bunleng.saving_luy.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bunleng.saving_luy.domain.MoveMoneyError

/**
 * Bottom sheet to move money from one category to another. Each choice shows its
 * Available so overspending is easy to spot and fix.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveMoneySheet(
    state: MoveMoneyState,
    categories: List<CategoryRowUiState>,
    onSelectFrom: (String) -> Unit,
    onSelectTo: (String) -> Unit,
    onAmountChange: (String) -> Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Local state keeps typing in sync even when the view model's flow is a frame behind.
    // Re-read it when the destination changes, since that can fill in a suggested amount.
    var amountText by remember(state.toCategoryId) { mutableStateOf(state.amountText) }

    // Open fully so the action buttons below the content are always on screen.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Move Money",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    CategoryPickerRow(
                        label = "From",
                        selectedId = state.fromCategoryId,
                        categories = categories,
                        isError = state.error == MoveMoneyError.MissingFrom || state.error == MoveMoneyError.SameCategory,
                        onSelect = onSelectFrom,
                    )
                    CategoryPickerRow(
                        label = "To",
                        selectedId = state.toCategoryId,
                        categories = categories,
                        isError = state.error == MoveMoneyError.MissingTo || state.error == MoveMoneyError.SameCategory,
                        onSelect = onSelectTo,
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { if (onAmountChange(it)) amountText = it },
                label = { Text("Amount") },
                prefix = { Text("$") },
                placeholder = { Text("0.00") },
                isError = state.error == MoveMoneyError.MissingAmount,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let {
                Text(
                    text = "⚠ ${errorMessage(it)}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Move") }
            }
        }
    }
}

@Composable
private fun CategoryPickerRow(
    label: String,
    selectedId: String?,
    categories: List<CategoryRowUiState>,
    isError: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val selected = categories.firstOrNull { it.category.id == selectedId }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable {
                    focusManager.clearFocus()
                    expanded = true
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
            )
            Text(
                text = selected?.category?.name ?: "Choose  ›",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            selected?.let { AvailablePill(available = it.entry.available) }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (row in categories) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.category.name, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(16.dp))
                            AvailablePill(available = row.entry.available)
                        }
                    },
                    onClick = {
                        onSelect(row.category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun errorMessage(error: MoveMoneyError): String = when (error) {
    MoveMoneyError.MissingFrom -> "Choose where the money comes from"
    MoveMoneyError.MissingTo -> "Choose where the money goes"
    MoveMoneyError.SameCategory -> "Choose two different categories"
    MoveMoneyError.MissingAmount -> "Enter an amount"
}
