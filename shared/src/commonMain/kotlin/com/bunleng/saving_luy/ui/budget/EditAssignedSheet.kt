package com.bunleng.saving_luy.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bunleng.saving_luy.ui.MoneyFormatter
import com.bunleng.saving_luy.ui.components.NumericKeypad

/**
 * Bottom sheet with a numeric keypad to edit the Assigned amount for a category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAssignedSheet(
    state: EditAssignedState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit = {},
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title
            Text(
                text = "Assign to ${state.category.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(16.dp))

            // Display the amount
            Text(
                text = MoneyFormatter.format(state.inputCents),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(24.dp))

            // Numeric keypad
            NumericKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                onClear = onClear,
            )

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
